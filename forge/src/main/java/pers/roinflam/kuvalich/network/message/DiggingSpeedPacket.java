// DiggingSpeedPacket.java
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
     * 服务端缓存：上次发送给每个玩家的值，用于去重
     * Server cache: last sent value per player, used for deduplication
     *
     * 只在服务端使用，玩家退出时需清理
     * Only used on server side, must be cleaned up when player leaves
     */
    private static final Map<UUID, Float> SERVER_LAST_SENT = new ConcurrentHashMap<>();

    /**
     * 值比较的容差（避免浮点精度问题导致无意义的重复发包）
     * Tolerance for value comparison (avoid meaningless repeated packets due to float precision)
     */
    private static final float EPSILON = 0.0001f;

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
     * 检查值是否发生变化，如果变化则更新缓存并返回true
     * Check if value has changed, update cache and return true if changed
     *
     * 调用方应先调用此方法，只在返回true时才构造并发送包
     * Caller should invoke this first, only construct and send packet when true is returned
     *
     * @param playerUUID 玩家UUID / player UUID
     * @param newValue   新的速度增量值 / new speed increment value
     * @return true表示值已变化需要发包，false表示值未变无需发包
     *         true = value changed, need to send; false = unchanged, skip
     */
    public static boolean shouldSend(UUID playerUUID, float newValue) {
        Float lastSent = SERVER_LAST_SENT.get(playerUUID);

        // 首次发送，或值发生了超过容差的变化
        // First send, or value changed beyond tolerance
        if (lastSent == null || Math.abs(lastSent - newValue) > EPSILON) {
            SERVER_LAST_SENT.put(playerUUID, newValue);
            return true;
        }

        return false;
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
     *
     * 同时清理客户端缓存和服务端发送记录
     * Clean both client cache and server send records
     */
    public static void cleanupCache() {
        CLIENT_CACHE.clear();
        SERVER_LAST_SENT.clear();
    }

    /**
     * 清理指定玩家的服务端缓存（玩家退出时调用）
     * Clean server cache for specific player (called when player leaves)
     *
     * @param playerUUID 玩家UUID / player UUID
     */
    public static void cleanupPlayer(UUID playerUUID) {
        SERVER_LAST_SENT.remove(playerUUID);
    }
}