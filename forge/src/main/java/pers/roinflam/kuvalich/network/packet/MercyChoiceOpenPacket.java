package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;

import java.util.function.Supplier;

/**
 * 选择窗口开了（服务器 → 处决者本人），开窗那一刻发一次。窗口开没开、结果是什么看目标身上的同步数据，
 * 这里只带选择牌要画的东西。
 *
 * <pre>
 *  VarInt  entityId        被处决的那只
 *  boolean allowConvert    这一场能不能选感化（开窗那一刻的配置）
 *  VarInt  remainingTicks  到截止还有几 tick（不含收包宽限）
 *  String  releaseName     名册已满时感化会挤掉的那只，≤ 24，没有为空
 * </pre>
 */
public class MercyChoiceOpenPacket {

    public final int entityId;
    public final boolean allowConvert;
    public final int remainingTicks;
    public final String releaseName;

    public MercyChoiceOpenPacket(int entityId, boolean allowConvert, int remainingTicks, String releaseName) {
        this.entityId = entityId;
        this.allowConvert = allowConvert;
        this.remainingTicks = remainingTicks;
        this.releaseName = releaseName == null ? "" : releaseName;
    }

    public static void encode(MercyChoiceOpenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.allowConvert);
        buf.writeVarInt(Math.max(0, msg.remainingTicks));
        String name = msg.releaseName;
        buf.writeUtf(name.length() > LichStatePacket.MAX_NAME ? name.substring(0, LichStatePacket.MAX_NAME) : name,
                LichStatePacket.MAX_NAME);
    }

    public static MercyChoiceOpenPacket decode(FriendlyByteBuf buf) {
        return new MercyChoiceOpenPacket(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                buf.readUtf(LichStatePacket.MAX_NAME));
    }

    public static void handle(MercyChoiceOpenPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandlers.onMercyChoiceOpen(msg)));
        ctx.setPacketHandled(true);
    }

    public static void send(ServerPlayer to, MercyChoiceOpenPacket msg) {
        if (to == null || KuvaLich.network == null || to.hasDisconnected()) {
            return;
        }
        KuvaLich.network.send(PacketDistributor.PLAYER.with(() -> to), msg);
    }
}
