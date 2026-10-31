package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.mercy.MercyServer;

import java.util.function.Supplier;

/**
 * 玩家对一只倒地的玄骸或奴仆请求怜悯之击（客户端 → 服务器）
 *
 * <p>只带目标的实体 id。能不能捅、站得对不对、是不是这个人的猎物，全部由服务端
 * {@link MercyServer#handleRequest} 重新校验，包里的内容不可信。</p>
 */
public class MercyRequestPacket {

    private final int entityId;

    public MercyRequestPacket(int entityId) {
        this.entityId = entityId;
    }

    public int entityId() {
        return entityId;
    }

    public static void encode(MercyRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static MercyRequestPacket decode(FriendlyByteBuf buf) {
        return new MercyRequestPacket(buf.readVarInt());
    }

    public static void handle(MercyRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (sender != null) {
                MercyServer.handleRequest(sender, msg.entityId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
