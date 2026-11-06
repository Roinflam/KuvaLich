package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

import java.util.function.Supplier;

/**
 * 安魂之融裂罅循环的操作请求（客户端 → 服务器）
 *
 * <p>只带「做什么」和客户端看到的状态序号，候选卡、价格、扣多少赤毒全由服务端按菜单里的实际东西算。
 * 服务端收到后先核对：玩家当前开着的就是这个界面（窗口号对得上、还在可用距离内），序号和菜单现在的一致，
 * 再交给菜单逐项校验（见 {@link RequiemEvolveMenu#handleRivenRequest}）。序号对不上的一律作废并回一个状态包，
 * 所以连点两下、同一 tick 里收到两个挑选，第二个都不会生效。</p>
 */
public class RivenCyclePacket {

    public static final byte ROLL = 0;
    public static final byte PICK = 1;
    public static final byte DISMISS = 2;
    public static final byte SWAP = 3;

    private final int containerId;
    private final byte action;
    private final int serial;
    /** 循环时是张数，挑选时是候选下标，其余不用 */
    private final int arg;

    public RivenCyclePacket(int containerId, byte action, int serial, int arg) {
        this.containerId = containerId;
        this.action = action;
        this.serial = serial;
        this.arg = arg;
    }

    public static void encode(RivenCyclePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeByte(msg.action);
        buf.writeVarInt(msg.serial);
        buf.writeVarInt(msg.arg);
    }

    public static RivenCyclePacket decode(FriendlyByteBuf buf) {
        return new RivenCyclePacket(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(RivenCyclePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            // 死了（界面要到移出世界时才关）或旁观的不处理
            if (player == null || !player.isAlive() || player.isSpectator() || msg.action < ROLL || msg.action > SWAP) {
                return;
            }
            if (player.containerMenu instanceof RequiemEvolveMenu menu && menu.containerId == msg.containerId
                    && menu.stillValid(player)) {
                menu.handleRivenRequest(player, msg.action, msg.serial, msg.arg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
