package pers.roinflam.kuvalich.network.message;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.client.render.ClientElementDebuffTracker;

import java.util.function.Supplier;

/**
 * 元素 debuff 视觉同步 Packet（S2C）
 * Element Debuff Visual Sync Packet (Server → Client)
 *
 * <p>服务端在 {@code DynamicAttributeManager.apply} 的同时立即发送本包，
 * 携带目标实体 ID、元素类型、持续 tick 数和等级。客户端收到后立即写入
 * {@link ClientElementDebuffTracker}，实现无延迟的染色和装饰渲染。</p>
 *
 * <p>与依赖 {@code AttributeModifier} 自动同步相比，本包的优势：
 * <ol>
 *     <li>延迟不超过 1 tick（仅网络 RTT），AttributeModifier 同步通常 2-5 tick。</li>
 *     <li>不需要在 DynamicAttribute 上添加哑 MAX_HEALTH(0) 修改器作标记，
 *         架构更干净，不会出现在任何 tooltip 或调试面板。</li>
 *     <li>可携带任意元信息（比如剩余持续时间），便于未来扩展。</li>
 * </ol></p>
 *
 * @author RoinFlam
 */
public class ElementDebuffPacket {

    /** 目标实体 ID / Target entity ID */
    private final int entityId;

    /** 元素类型名（ice/fire/poison/...）/ Element type name */
    private final String element;

    /** 剩余持续时间（tick 数）/ Remaining duration in ticks */
    private final int durationTicks;

    /** 元素等级（amplifier）/ Element amplifier */
    private final int amplifier;

    /**
     * 构造函数
     *
     * @param entityId      目标实体 ID
     * @param element       元素类型名
     * @param durationTicks 持续 tick 数
     * @param amplifier     元素等级
     */
    public ElementDebuffPacket(int entityId, String element, int durationTicks, int amplifier) {
        this.entityId = entityId;
        this.element = element;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
    }

    /**
     * 将 packet 编码到字节缓冲区
     *
     * @param msg 要编码的 packet 实例
     * @param buf 字节缓冲区
     */
    public static void encode(ElementDebuffPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.element, 32);
        buf.writeVarInt(msg.durationTicks);
        buf.writeVarInt(msg.amplifier);
    }

    /**
     * 从字节缓冲区解码 packet
     *
     * @param buf 字节缓冲区
     * @return 解码得到的 packet 实例
     */
    public static ElementDebuffPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String element = buf.readUtf(32);
        int durationTicks = buf.readVarInt();
        int amplifier = buf.readVarInt();
        return new ElementDebuffPacket(entityId, element, durationTicks, amplifier);
    }

    /**
     * 处理收到的 packet（仅在客户端执行）
     *
     * @param msg         收到的 packet
     * @param ctxSupplier 网络事件上下文
     */
    public static void handle(ElementDebuffPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.setPacketHandled(true);
    }

    /**
     * 客户端实际处理逻辑（从主包隔离，避免专用服务端加载客户端类）
     *
     * @param msg 收到的 packet
     */
    private static void handleClient(ElementDebuffPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(msg.entityId);
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        ClientElementDebuffTracker.apply(msg.entityId, msg.element, msg.durationTicks, msg.amplifier);
    }
}
