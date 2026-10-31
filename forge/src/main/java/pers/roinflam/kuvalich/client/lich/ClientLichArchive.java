package pers.roinflam.kuvalich.client.lich;

import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichArchiveRequestPacket;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 客户端缓存的玄骸档案：打开档案时向服务端要，包回来之前先用上次的，从没收到过就是空
 *
 * <p>时间用真实时钟（{@link Util#getMillis()}），不用演出钟：演出钟在游戏暂停时不走，
 * 而请求限流和「每 5 秒刷新一次」都是按真实时间算的。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ClientLichArchive {

    /** 两次请求之间的最短间隔，和服务端的限流一致 */
    private static final long MIN_REQUEST_INTERVAL_MS = 500L;

    @Nullable
    private static volatile LichArchivePacket.Data data;
    private static volatile long receivedAtMs;
    private static long lastRequestMs = Long.MIN_VALUE / 2;

    private ClientLichArchive() {
    }

    /** 向服务端要一份最新的档案；距上次请求不到 500 ms 时什么都不做。返回有没有真的发出请求 */
    public static boolean request() {
        long now = Util.getMillis();
        if (now - lastRequestMs < MIN_REQUEST_INTERVAL_MS || KuvaLich.network == null) {
            return false;
        }
        lastRequestMs = now;
        KuvaLich.network.sendToServer(new LichArchiveRequestPacket());
        return true;
    }

    /** 最近一次收到的档案；从没收到过返回 {@code null} */
    @Nullable
    public static LichArchivePacket.Data get() {
        return data;
    }

    /** 最近一次收到档案的时刻（{@link Util#getMillis()}）；从没收到过为 0 */
    public static long receivedAtMs() {
        return receivedAtMs;
    }

    /** 档案包到了 */
    public static void accept(LichArchivePacket.Data archive) {
        data = archive;
        receivedAtMs = Util.getMillis();
    }

    /** 离开世界时清空 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        data = null;
        receivedAtMs = 0L;
    }
}
