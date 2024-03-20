package pers.roinflam.kuvalich.network.message;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.render.damagedisplay.DamageRenderer;

public class DamagePacket implements IMessage {
    private float damage;
    private Vec3d position;
    private int color;

    public DamagePacket() {}

    public DamagePacket(float damage, Vec3d position, int color) {
        this.damage = damage;
        this.position = position;
        this.color = color;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(damage);
        buf.writeDouble(position.x);
        buf.writeDouble(position.y);
        buf.writeDouble(position.z);
        buf.writeInt(color);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        damage = buf.readFloat();
        position = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        color = buf.readInt();
    }

    public static class Handler implements IMessageHandler<DamagePacket, IMessage> {
        @Override
        public IMessage onMessage(DamagePacket message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().world.isRemote) {
                    DamageRenderer.getInstance().addDamageInfo(
                            new DamageInfo(message.damage, message.color, message.position, System.currentTimeMillis() + 2000, System.currentTimeMillis()));
                }
            });
            return null;
        }
    }
}
