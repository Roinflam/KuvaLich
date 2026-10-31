package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;
import pers.roinflam.kuvalich.config.ModConfig;

import java.util.function.Supplier;

/**
 * 安魂解密进度顶部HUD同步包（服务器 → 客户端）
 * Decryption progress top-HUD sync packet (Server → Client)
 *
 * <p>击杀奴仆/玄骸获得解密进度时，服务端立即下发本包，携带当前阶段（level）、
 * 当前阶段进度（progress）、当前阶段阈值（threshold，满级为 -1）、本次增加量（added）
 * 以及事件类型。客户端 {@code DecryptionHudOverlay} 收到后做填充补间动画与提示。</p>
 *
 * <p>阈值由服务端下发（而非客户端读配置），因此 HUD 比例始终与服务端真实进度一致，
 * 不受客户端 ModConfig 是否同步影响。</p>
 *
 * <p>客户端处理逻辑隔离在 {@link #handleClient} 中，仅经 DistExecutor 在客户端调用，
 * 专用服务端不会加载客户端类（参照 ElementDebuffPacket 的做法）。</p>
 *
 * <p>协议 10 起末尾追加三项：{@code revealedCard}（这次新揭示的是哪张卡，没有为 -1）、
 * {@code riddles}（三枚已揭示的密语，按显示顺序，-1 为未揭示）、{@code lichName}（只在
 * {@link #EVENT_COMPLETE} 里带：刚终结的那只玄骸的名字）。包到达时客户端缓存的玄骸状态已经清空，
 * 所以破解完成的名字行读这里的 {@code lichName}，不读客户端缓存。</p>
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
    /** 本次新揭示的卡 id（0~7），没有揭示为 -1 */
    private final int revealedCard;
    /** 三枚已揭示的密语（卡 id，按显示顺序，-1 为未揭示），长度恒为 3 */
    private final int[] riddles;
    /** 刚终结的玄骸的名字（首字母大写）；只在 EVENT_COMPLETE 里有，其余为空串 */
    private final String lichName;

    /** 名字的长度上限 */
    public static final int MAX_LICH_NAME = 32;

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
        this(eventType, level, progress, threshold, added, -1, new int[]{-1, -1, -1}, "");
    }

    /**
     * 构造同步包（带揭示信息）
     *
     * @param revealedCard 本次新揭示的卡 id，没有为 -1
     * @param riddles      三枚已揭示的密语，长度不足 3 的补 -1
     * @param lichName     刚终结的玄骸名字，只在 EVENT_COMPLETE 里填
     */
    public DecryptionHudPacket(int eventType, int level, int progress, int threshold, int added,
                               int revealedCard, int[] riddles, String lichName) {
        this.eventType = eventType;
        this.level = level;
        this.progress = progress;
        this.threshold = threshold;
        this.added = added;
        this.revealedCard = revealedCard;
        int[] three = {-1, -1, -1};
        if (riddles != null) {
            System.arraycopy(riddles, 0, three, 0, Math.min(3, riddles.length));
        }
        this.riddles = three;
        this.lichName = lichName == null ? "" : lichName;
    }

    // ==================== 只读访问 / Accessors ====================

    public int eventType() {
        return eventType;
    }

    public int level() {
        return level;
    }

    public int progress() {
        return progress;
    }

    public int threshold() {
        return threshold;
    }

    public int added() {
        return added;
    }

    public int revealedCard() {
        return revealedCard;
    }

    /** 副本 */
    public int[] riddles() {
        return riddles.clone();
    }

    public String lichName() {
        return lichName;
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
        // 协议 10 追加的三项，顺序不能动
        buf.writeByte(msg.revealedCard);
        for (int i = 0; i < 3; i++) {
            buf.writeByte(msg.riddles[i]);
        }
        if (msg.eventType == EVENT_COMPLETE) {
            String name = msg.lichName.length() > MAX_LICH_NAME ? msg.lichName.substring(0, MAX_LICH_NAME) : msg.lichName;
            buf.writeUtf(name, MAX_LICH_NAME);
        }
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
        int revealedCard = buf.readByte();
        int[] riddles = new int[3];
        for (int i = 0; i < 3; i++) {
            riddles[i] = buf.readByte();
        }
        String lichName = eventType == EVENT_COMPLETE ? buf.readUtf(MAX_LICH_NAME) : "";
        return new DecryptionHudPacket(eventType, level, progress, threshold, added, revealedCard, riddles, lichName);
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
        ClientPacketHandlers.onDecryptionHud(msg);
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
        // 揭示了新线索时，新填上的那一格就是当前阶段对应的答案卡
        int revealed = (level > levelBefore) ? requiemCard.getLockCard(level) : -1;
        int[] riddles = {requiemCard.getOneRiddle(), requiemCard.getTwoRiddle(), requiemCard.getThreeRiddle()};
        send(serverPlayer, new DecryptionHudPacket(eventType, level, progress, threshold, added,
                revealed, riddles, ""));
    }

    /**
     * 发送最终解密成功包，带上终结那一刻的快照。
     *
     * <p>调用方在 {@code reset()} 之前取好快照：{@code riddles} 是卡上三枚密语的显示顺序，{@code lichName}
     * 是刚终结的那只玄骸的名字。包在延时任务里发出时卡已经重置，所以阶段和进度按重置后的初始值写。</p>
     *
     * @param player   目标玩家
     * @param riddles  三枚密语（卡 id，-1 为没有），长度不足 3 的补 -1
     * @param lichName 刚终结的玄骸名字
     */
    public static void sendComplete(ServerPlayer player, byte[] riddles, String lichName) {
        if (player == null) {
            return;
        }
        int[] ids = {-1, -1, -1};
        if (riddles != null) {
            for (int i = 0; i < 3 && i < riddles.length; i++) {
                ids[i] = riddles[i];
            }
        }
        // 重置之后回到第 0 阶段，阈值就是第一阶段的配置值
        int threshold = ModConfig.KUVA_LICH.firstStage.get();
        send(player, new DecryptionHudPacket(EVENT_COMPLETE, 0, 0, threshold, 0, -1, ids, lichName));
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
