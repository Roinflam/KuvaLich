package pers.roinflam.kuvalich.network.packet;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.nemesis.AllyRoster;
import pers.roinflam.kuvalich.nemesis.LichArchiveService;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 档案里对盟友名册的操作（客户端 → 服务器）：设为出战、遣散，带名册里的代数。
 *
 * <p>只能动发送者自己的名册，代数不在名册里就不做事。遣散的二次确认在客户端做，服务端收到就遣散。
 * 每人每秒最多收 {@link #MAX_PER_SECOND} 个，多出来的直接丢掉；处理完回一份新的档案，界面跟着刷新。</p>
 */
public class AllyCommandPacket {

    public static final int SET_ACTIVE = 0;
    public static final int RELEASE = 1;

    public static final int MAX_PER_SECOND = 5;
    private static final long WINDOW_MS = 1000L;
    private static final int PRUNE_SIZE = 256;

    /** 玩家 → {窗口开始的时刻, 窗口里收了几个}。只在服务端主线程读写 */
    private static final Map<UUID, long[]> WINDOWS = new HashMap<>();

    public enum Result {
        OK,
        UNCHANGED,
        RATE_LIMITED,
        NOT_IN_ROSTER,
        BAD_ACTION
    }

    private final int action;
    private final int generation;

    public AllyCommandPacket(int action, int generation) {
        this.action = action;
        this.generation = generation;
    }

    public static void encode(AllyCommandPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        buf.writeVarInt(Math.max(0, msg.generation));
    }

    public static AllyCommandPacket decode(FriendlyByteBuf buf) {
        return new AllyCommandPacket(buf.readByte(), buf.readVarInt());
    }

    public static void handle(AllyCommandPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (sender != null) {
                receive(sender, msg.action, msg.generation, Util.getMillis());
            }
        });
        ctx.setPacketHandled(true);
    }

    /** 限频之后执行，执行了就回一份档案 */
    public static Result receive(ServerPlayer player, int action, int generation, long nowMs) {
        if (!allow(player.getUUID(), nowMs)) {
            return Result.RATE_LIMITED;
        }
        Result r = apply(player, action, generation);
        LichArchiveService.handleRequest(player);
        return r;
    }

    public static Result apply(ServerPlayer player, int action, int generation) {
        if (action != SET_ACTIVE && action != RELEASE) {
            return Result.BAD_ACTION;
        }
        CompoundTag entry = find(player, generation);
        if (entry == null) {
            return Result.NOT_IN_ROSTER;
        }
        String name = entry.getString(AllyRoster.NAME);
        if (action == RELEASE) {
            if (!AllyRoster.release(player, generation)) {
                return Result.NOT_IN_ROSTER;
            }
            player.displayClientMessage(Component.translatable("message.kuvalich.ally.released", name), true);
            return Result.OK;
        }
        if (PlayerLedger.getActiveAlly(player) == generation) {
            return Result.UNCHANGED;
        }
        PlayerLedger.setActiveAlly(player, generation);
        player.displayClientMessage(Component.translatable("message.kuvalich.ally.active", name), true);
        return Result.OK;
    }

    private static CompoundTag find(ServerPlayer player, int generation) {
        if (generation < 0) {
            return null;
        }
        for (CompoundTag a : PlayerLedger.getAllies(player)) {
            if (a.getInt(AllyRoster.GENERATION) == generation) {
                return a;
            }
        }
        return null;
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

    public static void send(int action, int generation) {
        if (KuvaLich.network != null) {
            KuvaLich.network.sendToServer(new AllyCommandPacket(action, generation));
        }
    }
}
