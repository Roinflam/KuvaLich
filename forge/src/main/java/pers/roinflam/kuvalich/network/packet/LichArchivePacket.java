package pers.roinflam.kuvalich.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * 玄骸档案的完整数据（服务器 → 客户端）
 *
 * <p>只在玩家打开档案时按请求发（见 {@link LichArchiveRequestPacket}），只含请求者自己的数据。
 * 内容分三块：和状态包一样的那一套（名字、等级、怒气、状态行）、玄骸本身的属性、玩家这边的解密进度和处决记录。</p>
 *
 * <p>没收物品整份都带（档案里「查看全部」要列出每一件），个数上限 {@link #MAX_CONFISCATED} 和配置
 * {@code maxConfiscatedItems} 能填的最大值一致。单个物品的 NBT 超过 {@link #MAX_STACK_NBT} 字节时编码时去掉 NBT
 * 只留物品本身，免得一件塞满数据的物品把整包撑过上限；所有物品合起来要超过 {@link #MAX_CONFISCATED_BYTES} 字节时，
 * 放不下的那几件也去掉 NBT（见 {@link #writeConfiscated}），连光秃秃的物品都放不下才不再往下写。
 * 去掉过 NBT 的是哪几件随包带一个位图（{@link Data#confiscatedSlim()}），档案里悬停时说明详情没能显示；
 * 没写进包的件数由客户端用总数减去列表长度得出。解码时所有数量都按上限截断。</p>
 */
public class LichArchivePacket {

    /** 没收物品最多带几件，和配置 {@code maxConfiscatedItems} 的上限一致 */
    public static final int MAX_CONFISCATED = 1024;
    public static final int MAX_STACK_NBT = 2048;
    /**
     * 没收物品一段最多占多少字节。原版自定义数据包上限 1 MiB，这里只用一小部分：塞满 1024 件、每件 2 KB 的 NBT
     * 也不会把包撑破，开着档案每 5 秒刷新一次也不至于太重
     */
    public static final int MAX_CONFISCATED_BYTES = 192 * 1024;
    /** 一件不带 NBT 的物品编码后最多几个字节：有无物品 1、注册号变长整数至多 5、数量 1、空 NBT 1 */
    private static final int BARE_ITEM_BYTES = 8;
    /** 处决记录最多留几条（卡里最多存 5 条，这里留余量） */
    public static final int MAX_ATTEMPTS = 16;
    /** 名册上限配置最大 8；读档、改配置不截，留点余量 */
    public static final int MAX_ALLIES = 16;
    private static final int MAX_ID_LEN = 128;
    private static final int MAX_TYPE_LEN = 32;

    /**
     * 名册里的一只盟友
     *
     * @param seed    种子低 32 位
     * @param weapon  武器下标，-1 = 没有
     * @param element 武器元素，空 = 没定
     * @param active  下次前来助战的就是它
     * @param present 此刻正在场上
     */
    public record Ally(String name, int generation, int rank, int seed, int weapon, String element, String ephemera,
                       boolean active, boolean present) {
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(cut(name, LichStatePacket.MAX_NAME), LichStatePacket.MAX_NAME);
            buf.writeVarInt(Math.max(0, generation));
            buf.writeVarInt(Math.max(0, rank));
            buf.writeInt(seed);
            buf.writeVarInt(Math.max(0, weapon + 1));
            buf.writeUtf(cut(element, MAX_TYPE_LEN), MAX_TYPE_LEN);
            buf.writeUtf(cut(ephemera, MAX_TYPE_LEN), MAX_TYPE_LEN);
            buf.writeByte((active ? 1 : 0) | (present ? 2 : 0));
        }

        static Ally read(FriendlyByteBuf buf) {
            String name = buf.readUtf(LichStatePacket.MAX_NAME);
            int gen = buf.readVarInt();
            int rank = buf.readVarInt();
            int seed = buf.readInt();
            int weapon = buf.readVarInt() - 1;
            String element = buf.readUtf(MAX_TYPE_LEN);
            String ephemera = buf.readUtf(MAX_TYPE_LEN);
            int flags = buf.readByte();
            return new Ally(name, gen, rank, seed, weapon, element, ephemera, (flags & 1) != 0, (flags & 2) != 0);
        }
    }

    /** 一张安魂卡：卡 id（-1 = 没放）、已用次数、总次数 */
    public record Card(int id, int damage, int maxDamage) {
        public static final Card EMPTY = new Card(-1, 0, 0);
    }

    /**
     * 这只玄骸自己的战斗面板（服务端按它当前的等级、主人所在世界的难度算好）
     *
     * @param affinityPct  四类伤害打它的倍率百分数，按近战、弓箭、其他弹射物、魔法排（100 = 不变）
     * @param adaptPct     适应每层减多少百分比
     * @param adaptStacks  适应至多几层
     * @param furyPermille 交战中每秒增幅的千分比
     * @param health       最大生命（取整）
     * @param armorTenths  护甲 × 10
     * @param hitTenths    一刀打主人多少 × 10（含难度和主人身上的等级加成，不含交战增幅；和平难度为 0）
     * @param skills       五项技能的档位，压法见 {@code LichTraits#packedTiers}
     * @param rankStepPct  每级生命、护甲变化的百分比，0 = 不随等级变
     * @param takenStepPct 每级主人多受赤毒伤害的百分比
     */
    public record Stats(int[] affinityPct, int adaptPct, int adaptStacks, int furyPermille, int health,
                        int armorTenths, int hitTenths, int skills, int rankStepPct, int takenStepPct) {

        public static final Stats NONE = new Stats(new int[]{100, 100, 100, 100}, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        private void write(FriendlyByteBuf buf) {
            for (int i = 0; i < 4; i++) {
                buf.writeVarInt(Math.max(0, i < affinityPct.length ? affinityPct[i] : 100));
            }
            buf.writeVarInt(Math.max(0, adaptPct));
            buf.writeVarInt(Math.max(0, adaptStacks));
            buf.writeVarInt(Math.max(0, furyPermille));
            buf.writeVarInt(Math.max(0, health));
            buf.writeVarInt(Math.max(0, armorTenths));
            buf.writeVarInt(Math.max(0, hitTenths));
            buf.writeVarInt(Math.max(0, skills));
            buf.writeVarInt(Math.max(0, rankStepPct));
            buf.writeVarInt(Math.max(0, takenStepPct));
        }

        private static Stats read(FriendlyByteBuf buf) {
            int[] affinity = new int[4];
            for (int i = 0; i < 4; i++) {
                affinity[i] = buf.readVarInt();
            }
            return new Stats(affinity, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
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
     * @param stats            这只玄骸自己的战斗面板；没有玄骸时是 {@link Stats#NONE}
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
     * @param confiscated      被没收的物品，按没收先后（编码时按字节预算可能少写后面几件，见类注释）
     * @param confiscatedSlim  {@code confiscated} 里哪几件（按下标）去掉过 NBT，只剩物品种类和数量
     * @param lastName         上一只玄骸的名字，没有为空
     * @param lastFate         上一只的结局：0 无 / 1 终结 / 2 管理命令解除 / 3 感化
     * @param roster           盟友名册，最早感化的在前
     * @param rosterCap        名册上限（配置）
     * @param inboxCount       待领取的物品件数
     */
    public record Data(LichStatePacket state, String birthBiome, String birthDim,
                       ItemStack weaponStack, int weaponLvMin, int weaponLvMax,
                       Stats stats, int dealtPct, int takenPct,
                       int rageSources, int encounters,
                       int stage, int progress, int threshold,
                       int[] riddles, Card[] cards, int[] attempts,
                       int confiscatedCount, List<ItemStack> confiscated, BitSet confiscatedSlim,
                       String lastName, int lastFate,
                       List<Ally> roster, int rosterCap, int inboxCount) {

        /** 服务端组数据时用：哪几件要去掉 NBT 由编码时决定 */
        public Data(LichStatePacket state, String birthBiome, String birthDim,
                    ItemStack weaponStack, int weaponLvMin, int weaponLvMax,
                    Stats stats, int dealtPct, int takenPct,
                    int rageSources, int encounters,
                    int stage, int progress, int threshold,
                    int[] riddles, Card[] cards, int[] attempts,
                    int confiscatedCount, List<ItemStack> confiscated,
                    String lastName, int lastFate,
                    List<Ally> roster, int rosterCap, int inboxCount) {
            this(state, birthBiome, birthDim, weaponStack, weaponLvMin, weaponLvMax, stats, dealtPct, takenPct,
                    rageSources, encounters, stage, progress, threshold, riddles, cards, attempts,
                    confiscatedCount, confiscated, new BitSet(), lastName, lastFate, roster, rosterCap, inboxCount);
        }

        /** 同上，名册空、上限 3、没有待领取（开发命令和测试用） */
        public Data(LichStatePacket state, String birthBiome, String birthDim,
                    ItemStack weaponStack, int weaponLvMin, int weaponLvMax,
                    Stats stats, int dealtPct, int takenPct,
                    int rageSources, int encounters,
                    int stage, int progress, int threshold,
                    int[] riddles, Card[] cards, int[] attempts,
                    int confiscatedCount, List<ItemStack> confiscated,
                    String lastName, int lastFate) {
            this(state, birthBiome, birthDim, weaponStack, weaponLvMin, weaponLvMax, stats, dealtPct, takenPct,
                    rageSources, encounters, stage, progress, threshold, riddles, cards, attempts,
                    confiscatedCount, confiscated, new BitSet(), lastName, lastFate, List.of(), 3, 0);
        }

        public Data withRoster(List<Ally> roster, int rosterCap, int inboxCount) {
            return new Data(state, birthBiome, birthDim, weaponStack, weaponLvMin, weaponLvMax, stats, dealtPct,
                    takenPct, rageSources, encounters, stage, progress, threshold, riddles, cards, attempts,
                    confiscatedCount, confiscated, confiscatedSlim, lastName, lastFate, roster, rosterCap, inboxCount);
        }

        public Data withHistory(String lastName, int lastFate) {
            return new Data(state, birthBiome, birthDim, weaponStack, weaponLvMin, weaponLvMax, stats, dealtPct,
                    takenPct, rageSources, encounters, stage, progress, threshold, riddles, cards, attempts,
                    confiscatedCount, confiscated, confiscatedSlim, lastName, lastFate, roster, rosterCap, inboxCount);
        }
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
        (d.stats() == null ? Stats.NONE : d.stats()).write(buf);
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
        writeConfiscated(d.confiscated(), d.confiscatedSlim(), buf);
        buf.writeUtf(cut(d.lastName(), LichStatePacket.MAX_NAME), LichStatePacket.MAX_NAME);
        buf.writeByte(d.lastFate());
        List<Ally> roster = d.roster() == null ? List.of() : d.roster();
        int allyCount = Math.min(MAX_ALLIES, roster.size());
        buf.writeVarInt(allyCount);
        for (int i = 0; i < allyCount; i++) {
            roster.get(i).write(buf);
        }
        buf.writeVarInt(Math.max(0, d.rosterCap()));
        buf.writeVarInt(Math.max(0, d.inboxCount()));
    }

    public static LichArchivePacket decode(FriendlyByteBuf buf) {
        LichStatePacket state = LichStatePacket.decode(buf);
        String birthBiome = buf.readUtf(MAX_ID_LEN);
        String birthDim = buf.readUtf(MAX_ID_LEN);
        ItemStack weaponStack = buf.readItem();
        int weaponLvMin = buf.readVarInt();
        int weaponLvMax = buf.readVarInt();
        Stats stats = Stats.read(buf);
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
        BitSet slim = buf.readBitSet();
        if (slim.length() > stackCount) {
            slim.clear(stackCount, slim.length());
        }
        String lastName = buf.readUtf(LichStatePacket.MAX_NAME);
        int lastFate = buf.readByte();
        int allyCount = Math.min(MAX_ALLIES, Math.max(0, buf.readVarInt()));
        List<Ally> roster = new ArrayList<>(allyCount);
        for (int i = 0; i < allyCount; i++) {
            roster.add(Ally.read(buf));
        }
        int rosterCap = buf.readVarInt();
        int inboxCount = buf.readVarInt();
        return new LichArchivePacket(new Data(state, birthBiome, birthDim, weaponStack, weaponLvMin, weaponLvMax,
                stats, dealtPct, takenPct, rageSources, encounters,
                stage, progress, threshold, riddles, cards, attempts, confiscatedCount, confiscated, slim,
                lastName, lastFate, List.copyOf(roster), rosterCap, inboxCount));
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

    /**
     * 写没收物品：先写进一块临时缓冲，按字节预算挑出能放下的，再连同件数、去掉过 NBT 的位图一起抄进包里。
     * 每写一件都给后面的每一件留出一件光秃秃物品的位置（{@link #BARE_ITEM_BYTES}），按实际写进去的字节数判断，
     * 超了就去掉这件的 NBT 再放，所以前面带 NBT 的物品不会把预算吃光、让后面的物品一件都列不出来。
     * 后面的预留随件数减少，中途去掉过 NBT 之后，再往后的小件仍可能带着 NBT 放进去。
     * 去掉 NBT 以后还超出总预算（有的物品没有 NBT 也要写一份共享数据）才到此为止
     *
     * @param prevSlim 列表里已经去掉过 NBT 的下标（档案包解码后再编码时原样带上）
     */
    private static void writeConfiscated(List<ItemStack> items, @Nullable BitSet prevSlim, FriendlyByteBuf buf) {
        int limit = Math.min(MAX_CONFISCATED, items.size());
        FriendlyByteBuf tmp = new FriendlyByteBuf(Unpooled.buffer());
        BitSet slimmed = new BitSet();
        try {
            int written = 0;
            for (int i = 0; i < limit; i++) {
                ItemStack original = items.get(i);
                ItemStack stack = slim(original);
                boolean stripped = (stack != original && !stack.isEmpty()) || (prevSlim != null && prevSlim.get(i));
                int budget = MAX_CONFISCATED_BYTES - (limit - i - 1) * BARE_ITEM_BYTES;
                int mark = tmp.writerIndex();
                tmp.writeItem(stack);
                if (tmp.writerIndex() > budget && stack.getTag() != null) {
                    tmp.writerIndex(mark);
                    stack = bare(stack);
                    stripped = true;
                    tmp.writeItem(stack);
                }
                if (tmp.writerIndex() > MAX_CONFISCATED_BYTES) {
                    tmp.writerIndex(mark);
                    break;
                }
                if (stripped) {
                    slimmed.set(written);
                }
                written++;
            }
            buf.writeVarInt(written);
            buf.writeBytes(tmp);
            buf.writeBitSet(slimmed);
        } finally {
            tmp.release();
        }
    }

    private static ItemStack bare(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setTag(null);
        return copy;
    }

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
        return bare(stack);
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
