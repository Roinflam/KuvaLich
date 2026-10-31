package pers.roinflam.kuvalich.network.packet;

import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.kuvalich.nemesis.LichArchiveService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 打开玄骸档案时向服务端要数据（客户端 → 服务器，空包）
 *
 * <p>服务端每个玩家 500 ms 内只回一次，过了才交给 {@link LichArchiveService#handleRequest}；
 * 回的是请求者自己的数据，包里没有任何可以指定别人的字段。</p>
 */
public class LichArchiveRequestPacket {

    /** 同一个玩家两次请求之间的最短间隔 */
    private static final long MIN_INTERVAL_MS = 500L;
    /** 记录表超过这个数就顺手清掉过期的，免得长期运行越攒越多 */
    private static final int PRUNE_SIZE = 256;

    /** 玩家 → 上次放行的时刻。只在服务端主线程读写 */
    private static final Map<UUID, Long> LAST_SERVED = new HashMap<>();

    public static void encode(LichArchiveRequestPacket msg, FriendlyByteBuf buf) {
    }

    public static LichArchiveRequestPacket decode(FriendlyByteBuf buf) {
        return new LichArchiveRequestPacket();
    }

    public static void handle(LichArchiveRequestPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer sender = ctx.getSender();
        ctx.enqueueWork(() -> {
            if (sender != null && allow(sender.getUUID())) {
                LichArchiveService.handleRequest(sender);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static boolean allow(UUID id) {
        long now = Util.getMillis();
        if (LAST_SERVED.size() > PRUNE_SIZE) {
            LAST_SERVED.values().removeIf(t -> now - t > MIN_INTERVAL_MS);
        }
        Long last = LAST_SERVED.get(id);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return false;
        }
        LAST_SERVED.put(id, now);
        return true;
    }
}
