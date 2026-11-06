package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 安魂之融裂罅循环的状态（服务器 → 客户端）：每处理完一个 {@link RivenCyclePacket} 都回一个，被拒的也回
 *
 * <p>候选卡只发给客户端画出来，挑哪张、换不换都还要客户端发请求、服务端按自己菜单里的那份办。
 * {@code paid} 是这一次实际扣了多少赤毒（没扣就是 0），{@code rolled} 是这一次洗了几张，
 * {@code reason} 是 {@link RequiemEvolveMenu} 里的 {@code RIVEN_*} 结果码。</p>
 */
public class RivenCycleStatePacket {

    private final int containerId;
    private final int serial;
    private final List<ItemStack> candidates;
    private final int paid;
    private final int rolled;
    private final byte reason;

    public RivenCycleStatePacket(int containerId, int serial, List<ItemStack> candidates, int paid, int rolled,
                                 byte reason) {
        this.containerId = containerId;
        this.serial = serial;
        this.candidates = candidates;
        this.paid = paid;
        this.rolled = rolled;
        this.reason = reason;
    }

    public static void encode(RivenCycleStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeVarInt(msg.serial);
        buf.writeVarInt(msg.paid);
        buf.writeVarInt(msg.rolled);
        buf.writeByte(msg.reason);
        buf.writeVarInt(msg.candidates.size());
        for (ItemStack s : msg.candidates) {
            buf.writeItem(s);
        }
    }

    public static RivenCycleStatePacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int serial = buf.readVarInt();
        int paid = buf.readVarInt();
        int rolled = buf.readVarInt();
        byte reason = buf.readByte();
        int n = buf.readVarInt();
        if (n < 0 || n > RequiemEvolveMenu.RIVEN_MAX_ROLL) {
            throw new IllegalArgumentException("裂罅候选数量不对：" + n);
        }
        List<ItemStack> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readItem());
        }
        return new RivenCycleStatePacket(id, serial, list, paid, rolled, reason);
    }

    public static void handle(RivenCycleStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                // 只在客户端有界面，用 DistExecutor 隔开，专用服务端不加载客户端类
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        pers.roinflam.kuvalich.client.RivenCycleClient.accept(msg.containerId, msg.serial,
                                msg.candidates, msg.paid, msg.rolled, msg.reason)));
        ctx.get().setPacketHandled(true);
    }
}
