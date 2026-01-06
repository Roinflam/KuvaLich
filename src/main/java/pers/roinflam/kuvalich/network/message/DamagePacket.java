package pers.roinflam.kuvalich.network.message;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.render.damagedisplay.DamageRenderer;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 伤害显示网络数据包（批量版）
 * 支持：客户端显示，服务器发送
 */
public class DamagePacket implements IMessage {
    private static final int MAX_BATCH_SIZE = 10;
    private static final int DISPLAY_DURATION_MS = 2000;

    private List<DamageData> damages;

    private static class DamageData {
        float damage;
        Vec3d position;
        int color;

        DamageData(float damage, Vec3d position, int color) {
            this.damage = damage;
            this.position = position;
            this.color = color;
        }
    }

    public DamagePacket() {
        this.damages = new ArrayList<>();
    }

    public DamagePacket(float damage, Vec3d position, int color) {
        this();
        addDamage(damage, position, color);
    }

    public void addDamage(float damage, Vec3d position, int color) {
        if (damages.size() >= MAX_BATCH_SIZE) {
            LogUtil.warn("伤害包已达到最大批量大小限制: " + MAX_BATCH_SIZE);
            return;
        }
        damages.add(new DamageData(damage, position, color));
    }

    public boolean isEmpty() {
        return damages.isEmpty();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(damages.size(), MAX_BATCH_SIZE);
        buf.writeInt(count);

        for (int i = 0; i < count; i++) {
            DamageData data = damages.get(i);
            buf.writeFloat(data.damage);
            buf.writeDouble(data.position.x);
            buf.writeDouble(data.position.y);
            buf.writeDouble(data.position.z);
            buf.writeInt(data.color);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            int count = buf.readInt();

            if (count < 0 || count > MAX_BATCH_SIZE) {
                LogUtil.error("接收到非法的伤害数量: " + count);
                return;
            }

            damages = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                float damage = buf.readFloat();
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                int color = buf.readInt();

                damages.add(new DamageData(damage, new Vec3d(x, y, z), color));
            }
        } catch (Exception e) {
            LogUtil.error("解析伤害数据包时发生错误", e);
            damages = new ArrayList<>();
        }
    }

    public static class Handler implements IMessageHandler<DamagePacket, IMessage> {
        @Override
        public IMessage onMessage(DamagePacket message, MessageContext ctx) {
            // 服务端不处理此包
            if (ctx.side != Side.CLIENT) {
                return null;
            }

            if (message.damages == null || message.damages.isEmpty()) {
                return null;
            }

            handleClientSide(message);
            return null;
        }

        @SideOnly(Side.CLIENT)
        private void handleClientSide(DamagePacket message) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                try {
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc.world == null || !mc.world.isRemote) {
                        return;
                    }

                    long currentTime = System.currentTimeMillis();
                    long endTime = currentTime + DISPLAY_DURATION_MS;

                    for (DamageData data : message.damages) {
                        DamageInfo damageInfo = new DamageInfo(
                                data.damage,
                                data.color,
                                data.position,
                                endTime,
                                currentTime
                        );
                        DamageRenderer.getInstance().addDamageInfo(damageInfo);
                    }

                    LogUtil.debug(String.format("批量添加伤害显示成功 - 数量: %d", message.damages.size()));
                } catch (Exception e) {
                    LogUtil.error("处理批量伤害数据包时发生错误", e);
                }
            });
        }
    }
}