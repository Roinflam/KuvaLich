package pers.roinflam.kuvalich.network.message;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 挖掘速度同步数据包
 *
 * 修复说明：
 * 1. 改为每个玩家独立存储挖掘速度（使用UUID作为key）
 * 2. 移除AtomicReference，在单线程环境下不需要
 * 3. 添加客户端缓存清理机制
 */
public class DiggingSpeedPacket implements IMessage {
    // 🔧 修复：每个玩家独立存储挖掘速度
    private static final Map<UUID, Double> DIGGING_SPEED_MAP = new ConcurrentHashMap<>();

    private UUID playerUUID;
    private double diggingSpeed;

    public DiggingSpeedPacket() {
    }

    public DiggingSpeedPacket(UUID playerUUID, Double diggingSpeed) {
        this.playerUUID = playerUUID;
        this.diggingSpeed = diggingSpeed != null ? diggingSpeed : 0.0;
    }

    /**
     * 获取指定玩家的挖掘速度
     */
    public static double getDiggingSpeed(EntityPlayer player) {
        if (player == null) return 0.0;
        return DIGGING_SPEED_MAP.getOrDefault(player.getUniqueID(), 0.0);
    }

    /**
     * 设置指定玩家的挖掘速度
     */
    private static void setDiggingSpeed(UUID playerUUID, double speed) {
        DIGGING_SPEED_MAP.put(playerUUID, speed);
    }

    /**
     * 清理不在线玩家的缓存（防止内存泄漏）
     */
    @SideOnly(Side.CLIENT)
    public static void cleanupCache() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            DIGGING_SPEED_MAP.clear();
            return;
        }

        // 只保留在线玩家的数据
        DIGGING_SPEED_MAP.keySet().removeIf(uuid ->
                mc.world.getPlayerEntityByUUID(uuid) == null
        );
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(playerUUID.getMostSignificantBits());
        buf.writeLong(playerUUID.getLeastSignificantBits());
        buf.writeDouble(diggingSpeed);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        long mostSigBits = buf.readLong();
        long leastSigBits = buf.readLong();
        playerUUID = new UUID(mostSigBits, leastSigBits);
        diggingSpeed = buf.readDouble();
    }

    public static class Handler implements IMessageHandler<DiggingSpeedPacket, IMessage> {
        @Override
        public IMessage onMessage(DiggingSpeedPacket message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }

            handleClientSide(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClientSide(DiggingSpeedPacket message) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc.world == null || !mc.world.isRemote) {
                        return;
                    }

                    double oldSpeed = DIGGING_SPEED_MAP.getOrDefault(message.playerUUID, 0.0);
                    setDiggingSpeed(message.playerUUID, message.diggingSpeed);
                } catch (Exception e) {
                    LogUtil.error("处理挖掘速度数据包时发生错误", e);
                }
            });
        }
    }
}