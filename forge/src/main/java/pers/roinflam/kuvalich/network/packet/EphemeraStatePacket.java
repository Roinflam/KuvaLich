package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;

import java.util.function.Supplier;

/**
 * 某个玩家带着什么幻纹（服务器 → 本人和追踪他的人）。内容由服务端从幻纹格读，客户端声明不了。
 *
 * <pre>
 *  VarInt  entityId
 *  String  type        空 = 卸下，后面没有了
 *  String  element
 *  int     color1, color2
 *  byte    gradient
 *  float   scale, yOffset
 * </pre>
 */
public class EphemeraStatePacket {

    public final int entityId;
    public final EphemeraLook look;

    public EphemeraStatePacket(int entityId, EphemeraLook look) {
        this.entityId = entityId;
        this.look = look == null ? EphemeraLook.NONE : look;
    }

    public static void encode(EphemeraStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        msg.look.write(buf);
    }

    public static EphemeraStatePacket decode(FriendlyByteBuf buf) {
        return new EphemeraStatePacket(buf.readVarInt(), EphemeraLook.read(buf));
    }

    public static void handle(EphemeraStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.onEphemeraState(msg)));
        ctx.setPacketHandled(true);
    }
}
