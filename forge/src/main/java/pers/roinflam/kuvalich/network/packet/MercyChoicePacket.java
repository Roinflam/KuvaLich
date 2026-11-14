package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.mercy.MercyServer;

import java.util.function.Supplier;

/**
 * 终结的选择：1 感化 / 0 消灭（客户端 → 服务器）
 *
 * <p>只带这一个字节，不带实体 id 和任何结果。是不是在窗口里、是不是第一次、这一场允不允许感化，
 * 全由 {@link MercyServer#handleChoice} 在主线程判。</p>
 */
public class MercyChoicePacket {

    public static final byte VANQUISH = 0;
    public static final byte CONVERT = 1;

    private final byte value;

    public MercyChoicePacket(byte value) {
        this.value = value;
    }

    public static void encode(MercyChoicePacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.value);
    }

    public static MercyChoicePacket decode(FriendlyByteBuf buf) {
        return new MercyChoicePacket(buf.readByte());
    }

    public static void handle(MercyChoicePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (sender != null) {
                MercyServer.handleChoice(sender, msg.value);
            }
        });
        ctx.setPacketHandled(true);
    }

    public static void send(boolean convert) {
        if (KuvaLich.network != null) {
            KuvaLich.network.sendToServer(new MercyChoicePacket(convert ? CONVERT : VANQUISH));
        }
    }
}
