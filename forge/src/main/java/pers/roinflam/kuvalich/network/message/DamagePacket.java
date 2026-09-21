package pers.roinflam.kuvalich.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.render.damagedisplay.DamageRenderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 伤害数字显示网络包
 * Damage number display network packet
 *
 * <p>性能保护：按玩家、按通道限流。每个玩家每 10 tick（500ms）内，
 * 主通道与附加通道各自最多发送 {@value #MAX_PRIMARY_PER_WINDOW} / {@value #MAX_SECONDARY_PER_WINDOW} 个包，超出直接丢弃。</p>
 *
 * <p>限流分主通道（命中本体、溅射、真伤、普通白字）与附加通道（元素、DoT、武器持续伤害），
 * 附加通道满了只丢附加数字，不影响主伤害。数字格式固定用 {@link Locale#ROOT}，
 * 极小伤害显示为 {@code <0.01}。</p>
 *
 * <p>⭐ 本次改动：包里不再传一段拼好的文本，而是把「受击实体 ID、数值、颜色码、前缀、图标后缀、
 * 是否打到护盾、合并分组」拆开传。客户端拿到这些字段后才能把短时间内同一只怪身上的多条数字
 * 合并成一条（见 {@link DamageRenderer#addDamage}）；关闭合并时客户端按原顺序把它们拼回同一段文本，
 * 显示效果与改动前一致。字节流格式变了，网络协议版本已同步升到 "6"。</p>
 */
public class DamagePacket {

    // ====================== 限流配置 ======================

    /**
     * 发包通道：决定一条伤害数字占用哪一份限流额度
     */
    public enum Channel {
        /** 主伤害：命中本体、溅射、真伤、普通白字——玩家最关心的数字 */
        PRIMARY,
        /** 附加伤害：元素触发、元素 DoT、武器持续伤害——数量大，额度满了只丢这一类 */
        SECONDARY
    }

    /**
     * 限流窗口大小（毫秒）。
     * 500ms = 10tick。
     */
    private static final long THROTTLE_WINDOW_MS = 500L;

    /**
     * 主通道每个窗口内最多发送的包数量。
     * 200包/10tick = 20包/tick。
     */
    private static final int MAX_PRIMARY_PER_WINDOW = 200;

    /**
     * 附加通道每个窗口内最多发送的包数量。
     * 200包/10tick = 20包/tick，与主通道互不占用。
     */
    private static final int MAX_SECONDARY_PER_WINDOW = 200;

    /** 每个玩家的限流状态 —— [窗口ID, 主通道计数, 附加通道计数] */
    private static final Map<UUID, long[]> THROTTLE = new ConcurrentHashMap<>();

    /** 默认显示时长（毫秒） */
    public static final long DEFAULT_DISPLAY_DURATION = 2000L;

    /** 「无受击实体」的占位 ID：客户端见到负数不做合并 */
    public static final int NO_ENTITY = -1;

    /** 普通伤害的合并分组（空串）：命中本体、溅射、真伤、白字、武器持续伤害都在这一组里互相合并 */
    public static final String MERGE_GROUP_GENERAL = "";

    /** 读字符串时的长度上限：颜色码 / 前缀 / 图标串 / 分组名都很短，超出说明包不正常 */
    private static final int MAX_STRING_LENGTH = 256;

    // ====================== 包字段 ======================

    /** 受击实体 ID（合并按实体分组）；{@link #NO_ENTITY} 表示无 */
    private final int entityId;
    /** 伤害数值 */
    private final float amount;
    /** 颜色码（如 {@code "§c"}） */
    private final String colorCode;
    /** 数字前缀（宠物、女仆攻击时为🎀） */
    private final String prefix;
    /** 数字后缀（元素图标、真伤图标等，每个图标自带颜色码） */
    private final String suffix;
    /** 是否打到护盾（客户端在末尾追加护盾图标） */
    private final boolean hitShield;
    /** 合并分组：普通伤害为 {@link #MERGE_GROUP_GENERAL}，本模组元素伤害为元素名 */
    private final String mergeGroup;
    private final double x;
    private final double y;
    private final double z;
    private final long displayDuration;

    /**
     * 完整构造函数
     *
     * @param entityId        受击实体 ID；无实体时传 {@link #NO_ENTITY}
     * @param amount          伤害数值
     * @param colorCode       颜色码（如 {@code "§c"}）
     * @param prefix          数字前缀，可为空串
     * @param suffix          数字后缀（图标串），可为空串
     * @param hitShield       是否打到护盾
     * @param mergeGroup      合并分组
     * @param position        显示位置
     * @param displayDuration 显示时长（毫秒）
     */
    public DamagePacket(int entityId, float amount, @Nonnull String colorCode, @Nonnull String prefix,
                        @Nonnull String suffix, boolean hitShield, @Nonnull String mergeGroup,
                        @Nonnull Vec3 position, long displayDuration) {
        this.entityId = entityId;
        this.amount = amount;
        this.colorCode = colorCode;
        this.prefix = prefix;
        this.suffix = suffix;
        this.hitShield = hitShield;
        this.mergeGroup = mergeGroup;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.displayDuration = displayDuration;
    }

    /**
     * 便捷构造函数（使用默认显示时长 2 秒）
     *
     * @param entityId   受击实体 ID；无实体时传 {@link #NO_ENTITY}
     * @param amount     伤害数值
     * @param colorCode  颜色码
     * @param prefix     数字前缀，可为空串
     * @param suffix     数字后缀（图标串），可为空串
     * @param hitShield  是否打到护盾
     * @param mergeGroup 合并分组
     * @param position   显示位置
     */
    public DamagePacket(int entityId, float amount, @Nonnull String colorCode, @Nonnull String prefix,
                        @Nonnull String suffix, boolean hitShield, @Nonnull String mergeGroup,
                        @Nonnull Vec3 position) {
        this(entityId, amount, colorCode, prefix, suffix, hitShield, mergeGroup, position, DEFAULT_DISPLAY_DURATION);
    }

    // ====================== 限流检查 ======================

    /**
     * 检查并递增指定通道的限流计数器
     *
     * <p>仅在服务端主线程调用，数组内的读改写不需要额外同步。</p>
     *
     * @param uuid    玩家UUID
     * @param channel 发包通道
     * @return true表示允许发送，false表示该通道本窗口已超限
     */
    private static boolean checkThrottle(UUID uuid, Channel channel) {
        long window = System.currentTimeMillis() / THROTTLE_WINDOW_MS;
        long[] state = THROTTLE.computeIfAbsent(uuid, k -> new long[3]);

        if (state[0] != window) {
            state[0] = window;
            state[1] = 0;
            state[2] = 0;
        }

        int index;
        int limit;
        if (channel == Channel.SECONDARY) {
            index = 2;
            limit = MAX_SECONDARY_PER_WINDOW;
        } else {
            index = 1;
            limit = MAX_PRIMARY_PER_WINDOW;
        }

        if (state[index] >= limit) {
            return false;
        }

        state[index]++;
        return true;
    }

    // ====================== 发送接口 ======================

    /**
     * 格式化伤害数字（Warframe风格）
     * Format damage number (Warframe style)
     *
     * 规则：
     * - 千位分隔符：100,000,000（固定 {@link Locale#ROOT}，不受服务器语言环境影响）
     * - 最多2位小数：123.45
     * - 整数不显示小数点：123
     * - 小数第二位是0也不显示：123.4
     * - 大于 0 但四舍五入后为 0 的极小值显示为 {@code <0.01}
     *
     * @param damage 伤害数值
     * @return 格式化后的字符串
     */
    public static String formatDamage(float damage) {
        String formatted;

        // 判断是否为整数
        if (damage == (int) damage) {
            formatted = String.format(Locale.ROOT, "%,d", (int) damage);
        } else {
            // 保留2位小数
            formatted = String.format(Locale.ROOT, "%,.2f", damage);

            // 移除末尾的0（如 123.40 → 123.4）
            if (formatted.endsWith("0") && !formatted.endsWith(".00")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }

            // 移除 .00（如 123.00 → 123）
            if (formatted.endsWith(".00")) {
                formatted = formatted.substring(0, formatted.length() - 3);
            }
        }

        // 掉了血却被四舍五入成 0 时，显示为 <0.01
        if (damage > 0F && "0".equals(formatted)) {
            return "<0.01";
        }

        return formatted;
    }

    /**
     * 按指定通道把一条伤害数字发给玩家
     *
     * <p>先看总开关，再走限流；两关都过了才真正发包。</p>
     *
     * @param player  目标玩家；为 null 时不发送
     * @param packet  要发的包；为 null 时不发送
     * @param channel 发包通道；为 null 时按主通道处理
     */
    public static void sendToPlayer(@Nullable ServerPlayer player, @Nullable DamagePacket packet,
                                    @Nullable Channel channel) {
        if (player == null || packet == null) {
            return;
        }

        // 检查配置开关，如果禁用则不发送数据包
        if (!ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
            return;
        }

        // 限流检查（按通道分别计数）
        if (!checkThrottle(player.getUUID(), channel == null ? Channel.PRIMARY : channel)) {
            return;
        }

        KuvaLich.network.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * 清理指定玩家的限流缓存（玩家退出时由 DamageDisplayTracker 调用）
     *
     * @param playerUUID 玩家UUID
     */
    public static void cleanupPlayer(UUID playerUUID) {
        THROTTLE.remove(playerUUID);
    }

    // ====================== 网络编解码 ======================

    /**
     * 编码到字节缓冲
     */
    public static void encode(DamagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.entityId);
        buffer.writeFloat(packet.amount);
        buffer.writeUtf(packet.colorCode, MAX_STRING_LENGTH);
        buffer.writeUtf(packet.prefix, MAX_STRING_LENGTH);
        buffer.writeUtf(packet.suffix, MAX_STRING_LENGTH);
        buffer.writeBoolean(packet.hitShield);
        buffer.writeUtf(packet.mergeGroup, MAX_STRING_LENGTH);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeLong(packet.displayDuration);
    }

    /**
     * 从字节缓冲解码
     */
    public static DamagePacket decode(FriendlyByteBuf buffer) {
        int entityId = buffer.readInt();
        float amount = buffer.readFloat();
        String colorCode = buffer.readUtf(MAX_STRING_LENGTH);
        String prefix = buffer.readUtf(MAX_STRING_LENGTH);
        String suffix = buffer.readUtf(MAX_STRING_LENGTH);
        boolean hitShield = buffer.readBoolean();
        String mergeGroup = buffer.readUtf(MAX_STRING_LENGTH);
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        long displayDuration = buffer.readLong();

        Vec3 position = new Vec3(x, y, z);
        return new DamagePacket(entityId, amount, colorCode, prefix, suffix, hitShield, mergeGroup,
                position, displayDuration);
    }

    /**
     * 处理网络包
     */
    public static void handle(DamagePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });

        context.setPacketHandled(true);
    }

    /**
     * 客户端处理逻辑
     *
     * <p>数值非法（≤ 0、NaN、无穷大）或显示时长 ≤ 0 的包直接丢弃，不让一条坏包把合并槽位搞脏。</p>
     */
    private static void handleClient(DamagePacket packet) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!(packet.amount > 0F) || !Float.isFinite(packet.amount) || packet.displayDuration <= 0L) {
            return;
        }

        Vec3 position = new Vec3(packet.x, packet.y, packet.z);

        DamageRenderer.getInstance().addDamage(packet.entityId, packet.mergeGroup, packet.amount,
                packet.colorCode, packet.prefix, packet.suffix, packet.hitShield, position, packet.displayDuration);
    }
}
