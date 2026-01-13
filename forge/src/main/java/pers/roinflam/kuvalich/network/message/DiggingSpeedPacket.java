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

    private final float speedMultiplier;

    /**
     * 客户端缓存（UUID → 挖掘速度倍率）
     * Client cache (UUID → digging speed multiplier)
     *
     * 注意：1.20.1中需要手动管理缓存清理
     * Note: Cache cleanup must be managed manually in 1.20.1
     */
    private static final Map<UUID, Float> CLIENT_CACHE = new ConcurrentHashMap<>();

    /**
     * 构造挖掘速度包
     * Construct digging speed packet
     *
     * @param speedMultiplier 速度倍率 / speed multiplier
     */
    public DiggingSpeedPacket(float speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    /**
     * 编码到字节缓冲
     * Encode to byte buffer
     */
    public static void encode(DiggingSpeedPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.speedMultiplier);
    }

    /**
     * 从字节缓冲解码
     * Decode from byte buffer
     */
    public static DiggingSpeedPacket decode(FriendlyByteBuf buffer) {
        float speedMultiplier = buffer.readFloat();
        return new DiggingSpeedPacket(speedMultiplier);
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

        // 更新客户端缓存
        // Update client cache
        UUID playerUUID = mc.player.getUUID();
        CLIENT_CACHE.put(playerUUID, packet.speedMultiplier);
    }

    /**
     * 获取玩家的挖掘速度倍率
     * Get player's digging speed multiplier
     *
     * @param playerUUID 玩家UUID / player UUID
     * @return 速度倍率，默认1.0 / speed multiplier, default 1.0
     */
    public static float getDiggingSpeed(UUID playerUUID) {
        return CLIENT_CACHE.getOrDefault(playerUUID, 1.0f);
    }

    /**
     * 清理缓存（退出世界时调用）
     * Clean cache (called when leaving world)
     */
    public static void cleanupCache() {
        CLIENT_CACHE.clear();
    }
}