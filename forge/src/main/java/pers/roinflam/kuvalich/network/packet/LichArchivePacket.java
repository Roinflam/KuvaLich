package pers.roinflam.kuvalich.network.packet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 玄骸档案的完整数据（服务器 → 客户端）
 *
 * <p>只在玩家打开档案时按请求发（见 {@link LichArchiveRequestPacket}），只含请求者自己的数据。
 * 内容分三块：和状态包一样的那一套（名字、等级、怒气、状态行）、玄骸本身的属性、玩家这边的解密进度和处决记录。</p>
 *
 * <p>没收物品最多带 {@link #MAX_CONFISCATED} 个，单个物品的 NBT 超过 {@link #MAX_STACK_NBT} 字节时
 * 编码时去掉 NBT 只留物品本身，免得一件塞满数据的物品把整包撑过上限。解码时所有数量都按上限截断。</p>
 */
public class LichArchivePacket {

    public static final int MAX_CONFISCATED = 54;
    public static final int MAX_STACK_NBT = 2048;
    /** 处决记录最多留几条（卡里最多存 5 条，这里留余量） */
    public static final int MAX_ATTEMPTS = 16;
    private static final int MAX_ID_LEN = 128;

    /** 一张安魂卡：卡 id（-1 = 没放）、已用次数、总次数 */
    public record Card(int id, int damage, int maxDamage) {
        public static final Card EMPTY = new Card(-1, 0, 0);
    }

    /**
     * 档案内容。{@code riddles}、{@code cards} 的长度恒为 3。
     *
     * @param state            状态包那一套
     * @param birthBiome       诞生地生物群系 id
     * @param birthDim         诞生地维度 id
     * @param weaponStack      手里那把赤毒武器（不带 NBT）
     * @param weaponLvMin      武器等级下限
     * @param weaponLvMax      武器等级上限
     * @param affinity         低 4 位弱点类别位掩码、4~7 位抗性位掩码
     * @param adaptPct         适应抗性已叠到百分之几
     * @param adaptMax         适应抗性上限百分比
     * @param furyPermille     交战增幅千分比
     * @param dealtPct         玩家对玄骸的输出削减百分比
     * @param takenPct         玄骸对玩家的伤害增幅百分比
     * @param rageSources      怒气来源位掩码：1 奴仆 / 2 进度 / 4 揭示 / 8 时间
     * @param encounters       这一轮交手次数
     * @param stage            已揭示的密语条数
     * @param progress         当前阶段进度
     * @param threshold        当前阶段阈值，-1 = 已满
     * @param riddles          三条已揭示的密语（卡 id，-1 = 未揭示）
     * @param cards            当前放着的三张安魂卡
     * @param attempts         处决记录（打包过的 int，最新在前）
     * @param confiscatedCount 被没收的物品总数
     * @param confiscated      被没收物品的图标（最多前 54 个）
     * @param lastName         上一只玄骸的名字，没有为空
     * @param lastFate         上一只的结局：0 无 / 1 终结 / 2 管理命令解除
     */
    public record Data(LichStatePacket state, String birthBiome, String birthDim,
                       ItemStack weaponStack, int weaponLvMin, int weaponLvMax,
                       int affinity, int adaptPct, int adaptMax, int furyPermille, int dealtPct, int takenPct,
                       int rageSources, int encounters,
                       int stage, int progress, int threshold,
                       int[] riddles, Card[] cards, int[] attempts,
                       int confiscatedCount, List<ItemStack> confiscated,
                       String lastName, int lastFate) {
    }

    private final Data data;

    public LichArchivePacket(Data data) {
        this.data = data;
    }

    public Data data() {
        return data;
    }

    // ==================== 编解码 ====================

    public static void encode(LichArchivePacket msg, FriendlyByteBuf buf) {
        Data d = msg.data;
        LichStatePacket.encode(d.state(), buf);
        buf.writeUtf(cut(d.birthBiome(), MAX_ID_LEN), MAX_ID_LEN);
        buf.writeUtf(cut(d.birthDim(), MAX_ID_LEN), MAX_ID_LEN);
        buf.writeItem(d.weaponStack() == null ? ItemStack.EMPTY : d.weaponStack());
        buf.writeVarInt(Math.max(0, d.weaponLvMin()));
        buf.writeVarInt(Math.max(0, d.weaponLvMax()));
        buf.writeInt(d.affinity());
        buf.writeVarInt(Math.max(0, d.adaptPct()));
        buf.writeVarInt(Math.max(0, d.adaptMax()));
        buf.writeVarInt(Math.max(0, d.furyPermille()));
        buf.writeVarInt(Math.max(0, d.dealtPct()));
        buf.writeVarInt(Math.max(0, d.takenPct()));
        buf.writeByte(d.rageSources());
        buf.writeVarInt(Math.max(0, d.encounters()));
        buf.writeVarInt(Math.max(0, d.stage()));
        buf.writeVarInt(Math.max(0, d.progress()));
        // 阈值 -1 表示已满，按带符号的整数写
        buf.writeInt(d.threshold());
        for (int i = 0; i < 3; i++) {
            buf.writeByte(i < d.riddles().length ? d.riddles()[i] : -1);
        }
        for (int i = 0; i < 3; i++) {
            Card c = i < d.cards().length && d.cards()[i] != null ? d.cards()[i] : Card.EMPTY;
            buf.writeByte(c.id());
            buf.writeVarInt(Math.max(0, c.damage()));
            buf.writeVarInt(Math.max(0, c.maxDamage()));
        }
        int attemptCount = Math.min(MAX_ATTEMPTS, d.attempts().length);
        buf.writeVarInt(attemptCount);
        for (int i = 0; i < attemptCount; i++) {
            buf.writeInt(d.attempts()[i]);
        }
        buf.writeVarInt(Math.max(0, d.confiscatedCount()));
        int stackCount = Math.min(MAX_CONFISCATED, d.confiscated().size());
        buf.writeVarInt(stackCount);
        for (int i = 0; i < stackCount; i++) {
            buf.writeItem(slim(d.confiscated().get(i)));
        }
        buf.writeUtf(cut(d.lastName(), LichStatePacket.MAX_NAME), LichStatePacket.MAX_NAME);
        buf.writeByte(d.lastFate());
    }

    public static LichArchivePacket decode(FriendlyByteBuf buf) {
        LichStatePacket state = LichStatePacket.decode(buf);
        String birthBiome = buf.readUtf(MAX_ID_LEN);
        String birthDim = buf.readUtf(MAX_ID_LEN);
        ItemStack weaponStack = buf.readItem();
        int weaponLvMin = buf.readVarInt();
        int weaponLvMax = buf.readVarInt();
        int affinity = buf.readInt();
        int adaptPct = buf.readVarInt();
        int adaptMax = buf.readVarInt();
        int furyPermille = buf.readVarInt();
        int dealtPct = buf.readVarInt();
        int takenPct = buf.readVarInt();
        int rageSources = buf.readByte();
        int encounters = buf.readVarInt();
        int stage = buf.readVarInt();
        int progress = buf.readVarInt();
        int threshold = buf.readInt();
        int[] riddles = new int[3];
        for (int i = 0; i < 3; i++) {
            riddles[i] = buf.readByte();
        }
        Card[] cards = new Card[3];
        for (int i = 0; i < 3; i++) {
            cards[i] = new Card(buf.readByte(), buf.readVarInt(), buf.readVarInt());
        }
        int attemptCount = Math.min(MAX_ATTEMPTS, Math.max(0, buf.readVarInt()));
        int[] attempts = new int[attemptCount];
        for (int i = 0; i < attemptCount; i++) {
            attempts[i] = buf.readInt();
        }
        int confiscatedCount = buf.readVarInt();
        int stackCount = Math.min(MAX_CONFISCATED, Math.max(0, buf.readVarInt()));
        List<ItemStack> confiscated = new ArrayList<>(stackCount);
        for (int i = 0; i < stackCount; i++) {
            confiscated.add(buf.readItem());
        }
        String lastName = buf.readUtf(LichStatePacket.MAX_NAME);
        int lastFate = buf.readByte();
        return new LichArchivePacket(new Data(state, birthBiome, birthDim, weaponStack, weaponLvMin, weaponLvMax,
                affinity, adaptPct, adaptMax, furyPermille, dealtPct, takenPct, rageSources, encounters,
                stage, progress, threshold, riddles, cards, attempts, confiscatedCount, confiscated,
                lastName, lastFate));
    }

    public static void handle(LichArchivePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    /** 客户端处理（从主包隔离，避免专用服务端加载客户端类） */
    private static void handleClient(LichArchivePacket msg) {
        ClientPacketHandlers.onArchive(msg);
    }

    /** 发给一个玩家 */
    public static void send(ServerPlayer to, LichArchivePacket msg) {
        if (to == null || KuvaLich.network == null) {
            return;
        }
        KuvaLich.network.send(PacketDistributor.PLAYER.with(() -> to), msg);
    }

    // ==================== 小工具 ====================

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 图标只需要物品本身：NBT 超过上限的去掉，免得单件物品撑大整包 */
    private static ItemStack slim(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || nbtSize(tag) <= MAX_STACK_NBT) {
            return stack;
        }
        ItemStack copy = stack.copy();
        copy.setTag(null);
        return copy;
    }

    private static int nbtSize(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            tag.write(new DataOutputStream(bytes));
            return bytes.size();
        } catch (IOException e) {
            return Integer.MAX_VALUE;
        }
    }
}
