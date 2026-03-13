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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 伤害文本显示网络包（简化版，只传字符串）
 * Damage text display network packet (simplified, only sends string)
 *
 * <p>性能保护：每个玩家每10tick（500ms）最多发送200个伤害数字包，超出直接丢弃。</p>
 * <p>200/10tick = 20/tick，正常战斗完全够用，极端连射场景做兜底。</p>
 */
public class DamagePacket {

    // ====================== 限流配置 ======================

    /**
     * 限流窗口大小（毫秒）。
     * 500ms = 10tick。
     */
    private static final long THROTTLE_WINDOW_MS = 500L;

    /**
     * 每个窗口内最多发送的包数量。
     * 200包/10tick = 20包/tick。
     */
    private static final int MAX_PACKETS_PER_WINDOW = 20;

    /** 每个玩家的限流状态 —— [窗口ID, 计数] */
    private static final Map<UUID, long[]> THROTTLE = new ConcurrentHashMap<>();

    // ====================== 包字段 ======================

    private final String text;
    private final double x;
    private final double y;
    private final double z;
    private final long displayDuration;

    /**
     * 完整构造函数
     *
     * @param text            显示文本（可包含颜色代码）
     * @param position        显示位置
     * @param displayDuration 显示时长（毫秒）
     */
    public DamagePacket(String text, Vec3 position, long displayDuration) {
        this.text = text;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.displayDuration = displayDuration;
    }

    /**
     * 便捷构造函数（使用默认显示时长2秒）
     *
     * @param text     显示文本
     * @param position 显示位置
     */
    public DamagePacket(String text, Vec3 position) {
        this(text, position, 2000L);
    }

    // ====================== 限流检查 ======================

    /**
     * 检查并递增限流计数器
     *
     * @param uuid 玩家UUID
     * @return true表示允许发送，false表示已超限
     */
    private static boolean checkThrottle(UUID uuid) {
        long window = System.currentTimeMillis() / THROTTLE_WINDOW_MS;
        long[] state = THROTTLE.computeIfAbsent(uuid, k -> new long[]{0L, 0L});

        if (state[0] != window) {
            state[0] = window;
            state[1] = 0;
        }

        if (state[1] >= MAX_PACKETS_PER_WINDOW) {
            return false;
        }

        state[1]++;
        return true;
    }

    // ====================== 发送接口 ======================

    /**
     * 格式化伤害数字（Warframe风格）
     * Format damage number (Warframe style)
     *
     * 规则：
     * - 千位分隔符：100,000,000
     * - 最多2位小数：123.45
     * - 整数不显示小数点：123
     * - 小数第二位是0也不显示：123.4
     *
     * @param damage 伤害数值
     * @return 格式化后的字符串
     */
    public static String formatDamage(float damage) {
        // 判断是否为整数
        if (damage == (int) damage) {
            return String.format("%,d", (int) damage);
        }

        // 保留2位小数
        String formatted = String.format("%,.2f", damage);

        // 移除末尾的0（如 123.40 → 123.4）
        if (formatted.endsWith("0") && !formatted.endsWith(".00")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }

        // 移除 .00（如 123.00 → 123）
        if (formatted.endsWith(".00")) {
            formatted = formatted.substring(0, formatted.length() - 3);
        }

        return formatted;
    }

    /**
     * 将颜色值转换为§颜色代码
     * Convert color value to § color code
     *
     * @param color ARGB颜色值
     * @return § 颜色代码字符串（如 "§f", "§e"）
     */
    public static String getColorCode(int color) {
        if (color == DamageInfo.DamageColor.WHITE.getColor()) {
            return "§f";  // 白色
        } else if (color == DamageInfo.DamageColor.YELLOW.getColor()) {
            return "§e";  // 黄色
        } else if (color == DamageInfo.DamageColor.ORANGE.getColor()) {
            return "§6";  // 橙色（金色）
        } else if (color == DamageInfo.DamageColor.RED.getColor()) {
            return "§c";  // 红色
        } else if (color == DamageInfo.DamageColor.BLUE.getColor()) {
            return "§b";  // 蓝色
        }
        return "§f";  // 默认白色
    }

    /**
     * 静态辅助方法：发送数字伤害（自动格式化）
     *
     * @param player    目标玩家
     * @param damage    伤害数值
     * @param position  显示位置
     * @param colorCode 颜色代码（如 "§c"）
     */
    public static void sendToPlayer(ServerPlayer player, float damage, Vec3 position, String colorCode) {
        // 检查配置开关，如果禁用则不发送数据包
        if (!ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
            return;
        }

        // 限流检查
        if (!checkThrottle(player.getUUID())) {
            return;
        }

        String text = colorCode + formatDamage(damage);
        KuvaLich.network.send(
                PacketDistributor.PLAYER.with(() -> player),
                new DamagePacket(text, position)
        );
    }

    /**
     * 静态辅助方法：发送文本（已格式化）
     *
     * @param player   目标玩家
     * @param text     显示文本（可包含颜色代码）
     * @param position 显示位置
     */
    public static void sendToPlayer(ServerPlayer player, String text, Vec3 position) {
        // 检查配置开关，如果禁用则不发送数据包
        if (!ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
            return;
        }

        // 限流检查
        if (!checkThrottle(player.getUUID())) {
            return;
        }

        KuvaLich.network.send(
                PacketDistributor.PLAYER.with(() -> player),
                new DamagePacket(text, position)
        );
    }

    /**
     * 清理指定玩家的限流缓存（玩家退出时调用）
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
        buffer.writeUtf(packet.text);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeLong(packet.displayDuration);
    }

    /**
     * 从字节缓冲解码
     */
    public static DamagePacket decode(FriendlyByteBuf buffer) {
        String text = buffer.readUtf();
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        long displayDuration = buffer.readLong();

        Vec3 position = new Vec3(x, y, z);
        return new DamagePacket(text, position, displayDuration);
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
     */
    private static void handleClient(DamagePacket packet) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        long startTime = System.currentTimeMillis();
        long endTime = startTime + packet.displayDuration;

        Vec3 position = new Vec3(packet.x, packet.y, packet.z);

        DamageInfo info = new DamageInfo(packet.text, position, endTime, startTime);
        DamageRenderer.getInstance().addDamageInfo(info);
    }
}