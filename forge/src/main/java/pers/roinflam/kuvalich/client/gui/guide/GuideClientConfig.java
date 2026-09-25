package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.guide.GuideConfigSnapshot;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端这边的「服务端配置」镜像，冒险指南里所有 {@code {cfg:键}} 都从这里取值
 *
 * <p>服务端在玩家登录、以及配置文件被改动（Forge 热重载）时下发 {@code GuideConfigSyncPacket}，
 * 收到后整份替换。还没收到（或已经退出世界）时回退到本机读到的配置 ——
 * 单人游戏里两者本来就是同一份，联机时这只会发生在登录包到达之前的一瞬间。</p>
 *
 * <p>{@link #revision()} 每次整份替换时 +1，界面据此判断要不要重新解析正文里的数值。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class GuideClientConfig {

    /** 服务端下发的快照；null 表示还没收到 */
    @Nullable
    private static volatile Map<String, String> synced;

    private static volatile int revision;

    private GuideClientConfig() {}

    /**
     * 收到服务端快照（主线程调用）
     *
     * @param values 键 → 字符串值
     */
    public static void accept(@Nonnull Map<String, String> values) {
        synced = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        revision++;
    }

    /**
     * 当前是否在用服务端下发的值
     *
     * @return false 表示在用本机配置
     */
    public static boolean isSynced() {
        return synced != null;
    }

    /**
     * 快照版本号，每次替换 +1
     *
     * @return 版本号
     */
    public static int revision() {
        return revision;
    }

    /**
     * 取一份完整快照
     *
     * <p>没收到服务端的值时，每次调用都会重新读一遍本机配置（约 170 项，微秒级），
     * 所以界面应当在打开时 / {@link #revision()} 变化时取一次并自己缓存，不要逐个键调用。</p>
     *
     * @return 不可变的键 → 值
     */
    @Nonnull
    public static Map<String, String> snapshot() {
        Map<String, String> s = synced;
        return s != null ? s : Collections.unmodifiableMap(GuideConfigSnapshot.capture());
    }

    /**
     * 退出世界时丢掉上一个服务器的值，免得带进下一个存档 / 服务器
     *
     * @param event 登出事件
     */
    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        synced = null;
        revision++;
    }
}
