package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.comm.LichLook;

import java.util.function.Supplier;

/**
 * 通讯窗的一句台词（服务器 → 客户端，只发给一个玩家）
 *
 * <p>台词以 {@link Component} 的形式传，服务端用 {@code Component.translatable(键, 参数…)} 组好，
 * 客户端按自己的语言解析。字节格式：</p>
 * <pre>
 *  byte      speaker      发言人序号（{@link CommSpeaker}）
 *  VarInt    flags        URGENT=1 / ARRIVAL=2 / AMBIENT=4 / CHAT_COPY=8 / NO_MUTTER=16
 *  Component line         台词
 *  boolean   hasName      后面是否跟名牌
 *  [Component name]
 *  [LichLook]             只在发言人是玄骸时有：seed(int) / weapon+1(VarInt) / element(byte) / entityId+1(VarInt)
 *  VarInt    holdMs       字幕至少停留多久，0 = 客户端自己算
 * </pre>
 * <p>客户端处理隔离在 {@link ClientPacketHandlers}，只经 DistExecutor 进，专用服不加载客户端类。</p>
 */
public class KuvaCommPacket {

    public final CommSpeaker speaker;
    public final int flags;
    @Nullable
    public final Component name;
    public final Component line;
    /** 只在 {@code speaker == LICH} 时有值 */
    @Nullable
    public final LichLook look;
    public final int holdMs;

    public KuvaCommPacket(CommSpeaker speaker, int flags, @Nullable Component name, Component line,
                          @Nullable LichLook look, int holdMs) {
        this.speaker = speaker;
        this.flags = flags;
        this.name = name;
        this.line = line;
        this.look = look;
        this.holdMs = holdMs;
    }

    public static void encode(KuvaCommPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.speaker.ordinal());
        buf.writeVarInt(msg.flags);
        buf.writeComponent(msg.line);
        buf.writeBoolean(msg.name != null);
        if (msg.name != null) {
            buf.writeComponent(msg.name);
        }
        if (msg.speaker == CommSpeaker.LICH) {
            (msg.look != null ? msg.look : LichLook.NONE).write(buf);
        }
        buf.writeVarInt(msg.holdMs);
    }

    public static KuvaCommPacket decode(FriendlyByteBuf buf) {
        CommSpeaker speaker = CommSpeaker.byOrdinal(buf.readByte());
        int flags = buf.readVarInt();
        Component line = buf.readComponent();
        Component name = buf.readBoolean() ? buf.readComponent() : null;
        LichLook look = speaker == CommSpeaker.LICH ? LichLook.read(buf) : null;
        int holdMs = buf.readVarInt();
        return new KuvaCommPacket(speaker, flags, name, line, look, holdMs);
    }

    public static void handle(KuvaCommPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    /** 客户端处理（从主包隔离，避免专用服务端加载客户端类） */
    private static void handleClient(KuvaCommPacket msg) {
        ClientPacketHandlers.onComm(msg);
    }
}
