package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.WaitReason;

import java.util.function.Supplier;

/**
 * 玩家自己的玄骸状态（服务器 → 客户端，小包）
 *
 * <p>解密 HUD、头顶名牌、档案的状态行、通讯肖像都读它。客户端缓存在 {@code ClientLichState}。
 * 什么时候发、每人每秒最多发几个，由服务端导演在调用侧控制，这里只管编解码和发送。</p>
 *
 * <p>字段：</p>
 * <pre>
 *  byte    state         {@link LichState} 序号
 *  byte    waitReason    {@link WaitReason} 序号，不在等就是 0
 *  String  name          首字母大写，≤ 24，没有玄骸时为空
 *  int     seed          lichSeed 的低 32 位，外观和嗓音用
 *  VarInt  weapon        lichWeapon + 1（内存里存的是下标，-1 表示没有）
 *  VarInt  rank          玄骸等级（kuvaLevel + 1）
 *  short   rage          怒气 × 1000 四舍五入
 *  byte    tier          怒气档位 0~4
 *  byte    clues         已揭示的密语条数 0~3
 *  VarInt  cooldownSec   冷却还剩几秒，0 = 没在冷却
 *  VarInt  generation    这是第几只
 *  boolean paused        被管理命令暂停
 * </pre>
 */
public class LichStatePacket {

    /** 名字的长度上限 */
    public static final int MAX_NAME = 24;

    /** 没有玄骸时的状态 */
    public static final LichStatePacket EMPTY = new LichStatePacket(LichState.UNBORN, WaitReason.NONE, "",
            0, -1, 0, 0, 0, 0, 0, 0, false);

    public final LichState state;
    public final WaitReason waitReason;
    public final String name;
    public final int seed;
    /** 武器下标，-1 = 没有（网络上按 +1 写） */
    public final int weapon;
    public final int rank;
    /** 怒气千分比，0~1000 */
    public final int ragePermille;
    public final int tier;
    public final int clues;
    public final int cooldownSec;
    public final int generation;
    public final boolean paused;

    public LichStatePacket(LichState state, WaitReason waitReason, String name, int seed, int weapon, int rank,
                           int ragePermille, int tier, int clues, int cooldownSec, int generation, boolean paused) {
        this.state = state;
        this.waitReason = waitReason;
        this.name = name;
        this.seed = seed;
        this.weapon = weapon;
        this.rank = rank;
        this.ragePermille = ragePermille;
        this.tier = tier;
        this.clues = clues;
        this.cooldownSec = cooldownSec;
        this.generation = generation;
        this.paused = paused;
    }

    /** 档案大包里内嵌的那一份状态也走同一套字节格式 */
    public static void encode(LichStatePacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.state.ordinal());
        buf.writeByte(msg.waitReason.ordinal());
        String name = msg.name == null ? "" : msg.name;
        buf.writeUtf(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name, MAX_NAME);
        buf.writeInt(msg.seed);
        buf.writeVarInt(Math.max(0, msg.weapon + 1));
        buf.writeVarInt(Math.max(0, msg.rank));
        buf.writeShort(Math.max(0, Math.min(1000, msg.ragePermille)));
        buf.writeByte(msg.tier);
        buf.writeByte(msg.clues);
        buf.writeVarInt(Math.max(0, msg.cooldownSec));
        buf.writeVarInt(Math.max(0, msg.generation));
        buf.writeBoolean(msg.paused);
    }

    public static LichStatePacket decode(FriendlyByteBuf buf) {
        LichState state = LichState.byOrdinal(buf.readByte());
        WaitReason wait = WaitReason.byOrdinal(buf.readByte());
        String name = buf.readUtf(MAX_NAME);
        int seed = buf.readInt();
        int weapon = buf.readVarInt() - 1;
        int rank = buf.readVarInt();
        int rage = buf.readShort();
        int tier = buf.readByte();
        int clues = buf.readByte();
        int cooldownSec = buf.readVarInt();
        int generation = buf.readVarInt();
        boolean paused = buf.readBoolean();
        return new LichStatePacket(state, wait, name, seed, weapon, rank, rage, tier, clues, cooldownSec,
                generation, paused);
    }

    public static void handle(LichStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    /** 客户端处理（从主包隔离，避免专用服务端加载客户端类） */
    private static void handleClient(LichStatePacket msg) {
        ClientPacketHandlers.onLichState(msg);
    }

    /** 发给一个玩家 */
    public static void send(ServerPlayer to, LichStatePacket msg) {
        if (to == null || KuvaLich.network == null) {
            return;
        }
        KuvaLich.network.send(PacketDistributor.PLAYER.with(() -> to), msg);
    }
}
