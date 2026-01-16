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

        String text = colorCode + DamageRenderer.formatDamage(damage);
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