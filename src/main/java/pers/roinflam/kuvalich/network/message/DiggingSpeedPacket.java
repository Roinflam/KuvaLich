package pers.roinflam.kuvalich.network.message;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class DiggingSpeedPacket implements IMessage {
    public static double DIGGING_SPEED = 0;
    private double diggingSpeed;

    public DiggingSpeedPacket() {
    }

    public DiggingSpeedPacket(Double diggingSpeed) {
        this.diggingSpeed = diggingSpeed;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(diggingSpeed);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        diggingSpeed = buf.readDouble();
    }

    public static class Handler implements IMessageHandler<DiggingSpeedPacket, IMessage> {
        @Override
        public IMessage onMessage(DiggingSpeedPacket message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().world.isRemote) {
                    DIGGING_SPEED = message.diggingSpeed;
                }
            });
            return null;
        }
    }
}
