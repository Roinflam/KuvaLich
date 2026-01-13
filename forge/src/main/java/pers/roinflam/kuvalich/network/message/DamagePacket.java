// 文件：DamagePacket.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/network/message/DamagePacket.java

package pers.roinflam.kuvalich.network.message;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.render.damagedisplay.DamageRenderer;

import java.util.function.Supplier;

/**
 * 伤害数字显示网络包（1.20.1完整版）
 * Damage display network packet (1.20.1 complete version)
 */
public class DamagePacket {

    private final float damage;
    private final int color;
    private final double x;
    private final double y;
    private final double z;
    private final long displayDuration;

    /**
     * 便捷构造函数（使用Vec3）
     * Convenience constructor (using Vec3)
     */
    public DamagePacket(float damage, Vec3 position, int color) {
        this(damage, color, position.x, position.y, position.z, 2000L);
    }

    /**
     * 完整构造函数
     * Full constructor
     */
    public DamagePacket(float damage, int color, double x, double y, double z, long displayDuration) {
        this.damage = damage;
        this.color = color;
        this.x = x;
        this.y = y;
        this.z = z;
        this.displayDuration = displayDuration;
    }

    /**
     * 编码到字节缓冲
     * Encode to byte buffer
     */
    public static void encode(DamagePacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.damage);
        buffer.writeInt(packet.color);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeLong(packet.displayDuration);
    }

    /**
     * 从字节缓冲解码
     * Decode from byte buffer
     */
    public static DamagePacket decode(FriendlyByteBuf buffer) {
        float damage = buffer.readFloat();
        int color = buffer.readInt();
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        long displayDuration = buffer.readLong();

        return new DamagePacket(damage, color, x, y, z, displayDuration);
    }

    /**
     * 处理网络包
     * Handle packet
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
     * Client-side handling logic
     */
    private static void handleClient(DamagePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        // ✅ 关键修复：使用渲染时间作为起始时间，确保初始位置准确
        // 这样第一次渲染时 riseOffset 必定为 0
        long startTime = System.currentTimeMillis();
        long endTime = startTime + packet.displayDuration;

        Vec3 position = new Vec3(packet.x, packet.y, packet.z);
        DamageInfo info = new DamageInfo(
                packet.damage,
                packet.color,
                position,
                endTime,
                startTime  // ✅ 使用接收时的时间作为起始点
        );

        DamageRenderer.getInstance().addDamageInfo(info);
    }
}