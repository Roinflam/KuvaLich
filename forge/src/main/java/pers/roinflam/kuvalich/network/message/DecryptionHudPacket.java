package pers.roinflam.kuvalich.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.client.gui.hud.DecryptionHudOverlay;

import java.util.function.Supplier;

/**
 * 安魂解密进度顶部HUD同步包（服务器 → 客户端）
 * Decryption progress top-HUD sync packet (Server → Client)
 *
 * <p>击杀奴仆/玄骸获得解密进度时，服务端立即下发本包，携带当前阶段（level）、
 * 当前阶段进度（progress）、当前阶段阈值（threshold，满级为 -1）、本次增加量（added）
 * 以及事件类型。客户端 {@link DecryptionHudOverlay} 收到后做填充补间动画与提示。</p>
 *
 * <p>阈值由服务端下发（而非客户端读配置），因此 HUD 比例始终与服务端真实进度一致，
 * 不受客户端 ModConfig 是否同步影响。</p>
 *
 * <p>客户端处理逻辑隔离在 {@link #handleClient} 中，仅经 DistExecutor 在客户端调用，
 * 专用服务端不会加载客户端类（参照 ElementDebuffPacket 的做法）。</p>
 */
public class DecryptionHudPacket {

    /** 事件类型：普通进度增加（无揭示） */
    public static final int EVENT_PROGRESS = 0;
    /** 事件类型：本次增加揭示了新线索（阶段推进 level++） */
    public static final int EVENT_REVEAL = 1;
    /** 事件类型：最终解密成功（击杀玄骸且卡序正确） */
    public static final int EVENT_COMPLETE = 2;

    /** 事件类型 / Event type */
    private final int eventType;
    /** 当前解密阶段（unlockedCardStatus，0~3） / Current stage */
    private final int level;
    /** 当前阶段已累积进度 / Current stage accumulated progress */
    private final int progress;
    /** 当前阶段阈值（getPointsRequired，满级为 -1） / Current stage threshold (-1 when maxed) */
    private final int threshold;
    /** 本次增加量（仅用于展示，可为0） / Amount added this time (display only) */
    private final int added;

    /**
     * 构造同步包
     *
     * @param eventType 事件类型（EVENT_PROGRESS / EVENT_REVEAL / EVENT_COMPLETE）
     * @param level     当前解密阶段
     * @param progress  当前阶段已累积进度
     * @param threshold 当前阶段阈值（满级 -1）
     * @param added     本次增加量
     */
    public DecryptionHudPacket(int eventType, int level, int progress, int threshold, int added) {
        this.eventType = eventType;
        this.level = level;
        this.progress = progress;
        this.threshold = threshold;
        this.added = added;
    }

    // ==================== 编解码 / Encode & Decode ====================

    /**
     * 编码到字节缓冲
     *
     * @param msg 待编码包
     * @param buf 字节缓冲
     */
    public static void encode(DecryptionHudPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.eventType);
        buf.writeVarInt(msg.level);
        buf.writeVarInt(Math.max(0, msg.progress));
        // threshold 可能为 -1（满级），用 writeInt 而非 writeVarInt 以正确传递负值
        buf.writeInt(msg.threshold);
        buf.writeVarInt(Math.max(0, msg.added));
    }

    /**
     * 从字节缓冲解码
     *
     * @param buf 字节缓冲
     * @return 解码得到的包
     */
    public static DecryptionHudPacket decode(FriendlyByteBuf buf) {
        int eventType = buf.readByte();
        int level = buf.readVarInt();
        int progress = buf.readVarInt();
        int threshold = buf.readInt();
        int added = buf.readVarInt();
        return new DecryptionHudPacket(eventType, level, progress, threshold, added);
    }

    /**
     * 处理网络包（仅客户端实际执行渲染层更新）
     *
     * @param msg         收到的包
     * @param ctxSupplier 网络事件上下文
     */
    public static void handle(DecryptionHudPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    /**
     * 客户端处理逻辑（从主包隔离，避免专用服务端加载客户端类）
     *
     * @param msg 收到的包
     */
    private static void handleClient(DecryptionHudPacket msg) {
        DecryptionHudOverlay.onPacket(msg.eventType, msg.level, msg.progress, msg.threshold, msg.added);
    }

    // ==================== 服务端发送接口 / Server Send API ====================

    /**
     * 发送进度更新包：根据加成前后的阶段自动判定是否为「揭示新线索」。
     *
     * @param player      目标玩家（非 ServerPlayer 时静默忽略）
     * @param requiemCard 玩家的安魂卡片能力（已应用本次加成）
     * @param added       本次增加量
     * @param levelBefore 加成前的阶段（用于判断是否揭示了新线索）
     */
    public static void sendProgress(Player player, RequiemCard requiemCard, int added, int levelBefore) {
        if (!(player instanceof ServerPlayer serverPlayer) || requiemCard == null) {
            return;
        }
        int level = requiemCard.getUnlockedCardStatus();
        int progress = requiemCard.getDecryptionProgress();
        int threshold = requiemCard.getPointsRequired();
        int eventType = (level > levelBefore) ? EVENT_REVEAL : EVENT_PROGRESS;
        send(serverPlayer, new DecryptionHudPacket(eventType, level, progress, threshold, added));
    }

    /**
     * 发送最终解密成功包（触发庆祝提示并将 HUD 进度条归零）。
     * <p>应在 RequiemCard.reset() 之后调用，此时读取到的为重置后的阶段状态。</p>
     *
     * @param player      目标玩家
     * @param requiemCard 玩家的安魂卡片能力（已 reset）
     */
    public static void sendComplete(Player player, RequiemCard requiemCard) {
        if (!(player instanceof ServerPlayer serverPlayer) || requiemCard == null) {
            return;
        }
        int level = requiemCard.getUnlockedCardStatus();
        int progress = requiemCard.getDecryptionProgress();
        int threshold = requiemCard.getPointsRequired();
        send(serverPlayer, new DecryptionHudPacket(EVENT_COMPLETE, level, progress, threshold, 0));
    }

    /**
     * 实际发送到指定玩家
     *
     * @param serverPlayer 目标玩家
     * @param packet       待发送包
     */
    private static void send(ServerPlayer serverPlayer, DecryptionHudPacket packet) {
        if (KuvaLich.network == null) {
            return;
        }
        KuvaLich.network.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
    }
}
