// 文件：DamagePacket.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/network/message/DamagePacket.java
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

import java.util.function.Supplier;

/**
 * 伤害文本显示网络包（简化版，只传字符串）
 * Damage text display network packet (simplified, only sends string)
 */
public class DamagePacket {

    private final String text;
    private final double x;
    private final double y;
    private final double z;
    private final long displayDuration;

    /**
     * 完整构造函数
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
     */
    public DamagePacket(String text, Vec3 position) {
        this(text, position, 2000L);
    }

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
     * @param player 目标玩家
     * @param damage 伤害数值
     * @param position 显示位置
     * @param colorCode 颜色代码（如 "§c"）
     */
    public static void sendToPlayer(ServerPlayer player, float damage, Vec3 position, String colorCode) {
        // 检查配置开关，如果禁用则不发送数据包
        if (!ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
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
     * @param player 目标玩家
     * @param text 显示文本（可包含颜色代码）
     * @param position 显示位置
     */
    public static void sendToPlayer(ServerPlayer player, String text, Vec3 position) {
        // 检查配置开关，如果禁用则不发送数据包
        if (!ModConfig.KUVA_LICH.enableDamageNumbers.get()) {
            return;
        }

        KuvaLich.network.send(
                PacketDistributor.PLAYER.with(() -> player),
                new DamagePacket(text, position)
        );
    }

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