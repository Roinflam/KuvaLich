package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.client.fx.KuvaFxClient;

import java.util.function.Supplier;

/**
 * 音画效果包（服务器 → 客户端，主通道）
 *
 * <p>服务端只说「发生了什么」：效果编号 {@link pers.roinflam.kuvalich.fx.FxId}、相关实体、演出原点和两个参数。
 * 声音、粒子、几何、屏幕效果全由客户端按自己的时间轴放，所以声画同帧、客户端的音量和强度选项管得到。
 * 约 20 字节，比原来一次元素触发发二十多个粒子包少一个数量级。</p>
 *
 * <p>字段含义见 {@link pers.roinflam.kuvalich.fx.FxId}。客户端处理隔离在 {@link #handleClient} 里，
 * 只经 DistExecutor 调用，专用服务端不会加载客户端类。</p>
 */
public class KuvaFxPacket {

    /** {@link pers.roinflam.kuvalich.fx.FxId#id} */
    public final int fx;
    /** 实体 id + 1；0 = 没有 */
    public final int entity;
    /** 演出原点（实体可能已被移除，客户端以坐标为准） */
    public final float x, y, z;
    /** 参数，含义随 fx 而定 */
    public final int a, b;

    public KuvaFxPacket(int fx, int entity, float x, float y, float z, int a, int b) {
        this.fx = fx;
        this.entity = entity;
        this.x = x;
        this.y = y;
        this.z = z;
        this.a = a;
        this.b = b;
    }

    /** 带的实体的原始 id；没有则返回 -1 */
    public int entityId() {
        return entity - 1;
    }

    /** 演出原点 */
    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    public static void encode(KuvaFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.fx);
        buf.writeVarInt(msg.entity);
        buf.writeFloat(msg.x);
        buf.writeFloat(msg.y);
        buf.writeFloat(msg.z);
        buf.writeVarInt(msg.a);
        buf.writeVarInt(msg.b);
    }

    public static KuvaFxPacket decode(FriendlyByteBuf buf) {
        int fx = buf.readVarInt();
        int entity = buf.readVarInt();
        float x = buf.readFloat();
        float y = buf.readFloat();
        float z = buf.readFloat();
        int a = buf.readVarInt();
        int b = buf.readVarInt();
        return new KuvaFxPacket(fx, entity, x, y, z, a, b);
    }

    public static void handle(KuvaFxPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    /** 客户端处理（从主包隔离，避免专用服务端加载客户端类） */
    private static void handleClient(KuvaFxPacket msg) {
        KuvaFxClient.handle(msg);
    }
}
