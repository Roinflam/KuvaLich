package pers.roinflam.kuvalich.network.packet;

import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraSync;
import pers.roinflam.kuvalich.world.inventory.RequiemWarframeTableMenu;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 军械库调整面板改幻纹外观（客户端 → 服务器）：两色、渐变、大小、高度，或者恢复默认。
 *
 * <p>只改发送者自己开着的那个军械库菜单幻纹格里的那件；菜单编号对不上、格里不是幻纹就丢掉。
 * 包是定长的几个数，键名由服务端写死，伪造不出别的 NBT。每人每秒最多收 {@link #MAX_PER_SECOND} 个。</p>
 */
public class EphemeraTunePacket {

    public static final int SET = 0;
    public static final int RESET = 1;
    private static final int INVALID = -1;

    public static final int MAX_PER_SECOND = 4;
    private static final long WINDOW_MS = 1000L;
    private static final int PRUNE_SIZE = 256;
    /** 动作后面的定长部分：两色各 4、渐变 1、大小和高度各 4 */
    private static final int BODY_BYTES = 4 + 4 + 1 + 4 + 4;

    /** 玩家 → {窗口开始的时刻, 窗口里收了几个}。只在服务端主线程读写 */
    private static final Map<UUID, long[]> WINDOWS = new HashMap<>();

    public enum Result {
        OK,
        UNCHANGED,
        RATE_LIMITED,
        NO_MENU,
        NO_EPHEMERA,
        BAD_ACTION
    }

    public final int containerId;
    public final int action;
    public final int color1;
    public final int color2;
    public final int gradient;
    public final float scale;
    public final float yOffset;

    public EphemeraTunePacket(int containerId, int action, int color1, int color2, int gradient, float scale,
                              float yOffset) {
        this.containerId = containerId;
        this.action = action;
        this.color1 = color1;
        this.color2 = color2;
        this.gradient = gradient;
        this.scale = scale;
        this.yOffset = yOffset;
    }

    public static EphemeraTunePacket reset(int containerId) {
        return new EphemeraTunePacket(containerId, RESET, 0, 0, EphemeraItem.GRADIENT_OFF, 1.0F, 0.0F);
    }

    public static void encode(EphemeraTunePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeByte(msg.action);
        buf.writeInt(msg.color1);
        buf.writeInt(msg.color2);
        buf.writeByte(msg.gradient);
        buf.writeFloat(msg.scale);
        buf.writeFloat(msg.yOffset);
    }

    /** 截断、乱码的包不抛，读成一个会被丢掉的动作；多出来的字节不读 */
    public static EphemeraTunePacket decode(FriendlyByteBuf buf) {
        try {
            int id = buf.readVarInt();
            int action = buf.readByte();
            if (buf.readableBytes() < BODY_BYTES) {
                return new EphemeraTunePacket(id, INVALID, 0, 0, 0, 1.0F, 0.0F);
            }
            return new EphemeraTunePacket(id, action, buf.readInt(), buf.readInt(), buf.readByte(), buf.readFloat(),
                    buf.readFloat());
        } catch (RuntimeException e) {
            return new EphemeraTunePacket(-1, INVALID, 0, 0, 0, 1.0F, 0.0F);
        }
    }

    public static void handle(EphemeraTunePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (sender != null) {
                receive(sender, msg, Util.getMillis());
            }
        });
        ctx.setPacketHandled(true);
    }

    public static Result receive(ServerPlayer player, EphemeraTunePacket msg, long nowMs) {
        if (!allow(player.getUUID(), nowMs)) {
            return Result.RATE_LIMITED;
        }
        return apply(player, msg);
    }

    public static Result apply(ServerPlayer player, EphemeraTunePacket msg) {
        if (msg.action != SET && msg.action != RESET) {
            return Result.BAD_ACTION;
        }
        if (!(player.containerMenu instanceof RequiemWarframeTableMenu menu) || menu.containerId != msg.containerId) {
            return Result.NO_MENU;
        }
        ItemStack cur = menu.ephemeraHandler.getStackInSlot(0);
        if (!(cur.getItem() instanceof EphemeraItem) || EphemeraItem.getType(cur) == null) {
            return Result.NO_EPHEMERA;
        }
        // 改拷贝再整件换进去：原地改 NBT 不会让能力的版本号变，本人的同步包就不发
        ItemStack next = cur.copy();
        if (msg.action == RESET) {
            EphemeraItem.resetLook(next);
        } else {
            EphemeraItem.applyLook(next, msg.color1, msg.color2, msg.gradient, msg.scale, msg.yOffset);
        }
        if (ItemStack.matches(cur, next)) {
            return Result.UNCHANGED;
        }
        menu.replaceEphemera(next);
        // 不等每 5 tick 的轮询，周围的人马上看到新颜色
        EphemeraSync.poll(player);
        return Result.OK;
    }

    static boolean allow(UUID id, long now) {
        if (WINDOWS.size() > PRUNE_SIZE) {
            WINDOWS.values().removeIf(w -> now - w[0] >= WINDOW_MS);
        }
        long[] w = WINDOWS.get(id);
        if (w == null || now - w[0] >= WINDOW_MS || now < w[0]) {
            WINDOWS.put(id, new long[]{now, 1});
            return true;
        }
        if (w[1] >= MAX_PER_SECOND) {
            return false;
        }
        w[1]++;
        return true;
    }

    /** 测试用：清掉限频记录 */
    public static void resetRateLimit() {
        WINDOWS.clear();
    }

    public static void send(EphemeraTunePacket msg) {
        if (KuvaLich.network != null) {
            KuvaLich.network.sendToServer(msg);
        }
    }
}
