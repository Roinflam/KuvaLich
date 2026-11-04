package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.client.ClientPacketHandlers;

import java.util.function.Supplier;

/**
 * 一条玩家提示（服务器 → 客户端，只发给一个玩家）：{@code PlayerFeedback} 的聊天 / 动作栏两个出口都改发它
 *
 * <p>只传语言键和参数，不传拼好的整句：客户端开着提示条时按提示条的配色自己拼（正文骨白、参数用这一类的颜色），
 * 关掉提示条时用 {@code PlayerFeedback.translate} 拼出和以前一模一样的整句，交回原版的聊天 / 动作栏。字节格式：</p>
 * <pre>
 *  byte       flags   CHAT=1（原来进聊天框的那一类：停得久一些；关掉提示条时进聊天框）
 *  int        rgb     这一类提示的颜色（{@code KuvaPalette} 里的值）
 *  String     key     语言键
 *  VarInt     n       参数个数（最多 {@link #MAX_ARGS}）
 *  Component  arg × n 参数，没有上色
 * </pre>
 * <p>客户端处理隔离在 {@link ClientPacketHandlers}，只经 DistExecutor 进，专用服不加载客户端类。</p>
 */
public class NoticePacket {

    /** 原来进聊天框的那一类（里程碑） */
    public static final int CHAT = 1;
    /** 参数个数上限：现有译文最多 3 个，多出来的是写错了，截掉免得解码端分配过多 */
    public static final int MAX_ARGS = 8;
    private static final int MAX_KEY = 256;

    public final int flags;
    public final int rgb;
    public final String key;
    public final Component[] args;

    public NoticePacket(int flags, int rgb, String key, Component[] args) {
        this.flags = flags;
        this.rgb = rgb;
        this.key = key;
        this.args = args;
    }

    /** 原来进聊天框的那一类 */
    public boolean isChat() {
        return (flags & CHAT) != 0;
    }

    public static void encode(NoticePacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.flags);
        buf.writeInt(msg.rgb);
        buf.writeUtf(msg.key, MAX_KEY);
        int n = Math.min(msg.args.length, MAX_ARGS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeComponent(msg.args[i]);
        }
    }

    public static NoticePacket decode(FriendlyByteBuf buf) {
        int flags = buf.readByte();
        int rgb = buf.readInt();
        String key = buf.readUtf(MAX_KEY);
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ARGS) {
            throw new IllegalArgumentException("提示参数个数不对：" + n);
        }
        Component[] args = new Component[n];
        for (int i = 0; i < n; i++) {
            args[i] = buf.readComponent();
        }
        return new NoticePacket(flags, rgb, key, args);
    }

    public static void handle(NoticePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    /** 客户端处理（从主包隔离，避免专用服务端加载客户端类） */
    private static void handleClient(NoticePacket msg) {
        ClientPacketHandlers.onNotice(msg);
    }
}
