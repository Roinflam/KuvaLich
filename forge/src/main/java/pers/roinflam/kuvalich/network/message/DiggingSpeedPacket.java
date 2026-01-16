package pers.roinflam.kuvalich.network.message;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 挖掘速度同步网络包
 * Digging speed synchronization packet
 *
 * 服务器 → 客户端
 * Server → Client
 *
 * 用于同步玩家的挖掘速度修改器
 * Used to synchronize player's digging speed modifier
 */
public class DiggingSpeedPacket {

    /**
     * 速度增量（0.9 表示增加 90% 速度）
     * Speed increment (0.9 means 90% speed increase)
     */
    private final float speedIncrement;

    /**
     * 客户端缓存（UUID → 挖掘速度增量）
     * Client cache (UUID → digging speed increment)
     */
    private static final Map<UUID, Float> CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * 构造挖掘速度包
     * Construct digging speed packet
     *
     * @param speedIncrement 速度增量（0.9 = +90%）/ speed increment (0.9 = +90%)
     */
    public DiggingSpeedPacket(float speedIncrement) {
        this.speedIncrement = speedIncrement;
    }

    /**
     * 编码到字节缓冲
     * Encode to byte buffer
     */
    public static void encode(DiggingSpeedPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.speedIncrement);
    }

    /**
     * 从字节缓冲解码
     * Decode from byte buffer
     */
    public static DiggingSpeedPacket decode(FriendlyByteBuf buffer) {
        float speedIncrement = buffer.readFloat();
        return new DiggingSpeedPacket(speedIncrement);
    }

    /**
     * 处理网络包
     * Handle packet
     */
    public static void handle(DiggingSpeedPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        // 确保在主线程执行
        // Ensure execution on main thread
        context.enqueueWork(() -> {
            // 仅在客户端执行
            // Execute only on client
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });

        context.setPacketHandled(true);
    }

    /**
     * 客户端处理逻辑
     * Client-side handling logic
     */
    private static void handleClient(DiggingSpeedPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 更新客户端缓存（存储增量值，如 0.9 表示 +90%）
        // Update client cache (store increment value, e.g. 0.9 means +90%)
        UUID playerUUID = mc.player.getUUID();
        CLIENT_CACHE.put(playerUUID, packet.speedIncrement);
    }

    /**
     * 获取玩家的挖掘速度增量
     * Get player's digging speed increment
     *
     * @param playerUUID 玩家UUID / player UUID
     * @return 速度增量，默认0.0（无加成）/ speed increment, default 0.0 (no bonus)
     */
    public static float getDiggingSpeedIncrement(UUID playerUUID) {
        return CLIENT_CACHE.getOrDefault(playerUUID, 0.0f);
    }

    /**
     * 清理缓存（退出世界时调用）
     * Clean cache (called when leaving world)
     */
    public static void cleanupCache() {
        CLIENT_CACHE.clear();
    }
}