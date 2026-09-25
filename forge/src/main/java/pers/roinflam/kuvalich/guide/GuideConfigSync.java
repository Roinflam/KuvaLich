package pers.roinflam.kuvalich.guide;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import pers.roinflam.kuvalich.network.packet.GuideConfigSyncPacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 什么时候把服务端配置快照发给客户端
 *
 * <ul>
 *   <li><b>玩家登录</b>：发给这个人</li>
 *   <li><b>配置热重载</b>（服主改了 toml，Forge 的文件监视器触发 {@link ModConfigEvent.Reloading}）：
 *       广播全服，开着指南的玩家下一帧就能看到新数值。
 *       只在启动时生效的项（矿物生成、刷怪）例外：快照里仍是本次启动实际用上的值，见
 *       {@link GuideConfigSnapshot} 的「只在启动时生效的项」</li>
 * </ul>
 *
 * @author RoinFlam
 */
public final class GuideConfigSync {

    private GuideConfigSync() {}

    /** Forge 总线：登录 */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {

        private ForgeEvents() {}

        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                GuideConfigSyncPacket.sendTo(player);
            }
        }
    }

    /** 模组总线：配置重载 */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {

        private ModEvents() {}

        /**
         * 配置文件被改动
         *
         * <p>这个事件在 night-config 的文件监视线程上触发，不能直接发包，要切回服务端主线程。
         * 纯客户端（连着远程服务器）改自己的 toml 时 {@code getCurrentServer()} 为 null，什么也不做 ——
         * 那份本地配置本来就不该影响指南显示。</p>
         *
         * @param event 重载事件
         */
        @SubscribeEvent
        public static void onConfigReloading(ModConfigEvent.Reloading event) {
            if (!Reference.MOD_ID.equals(event.getConfig().getModId())
                    || event.getConfig().getType() != net.minecraftforge.fml.config.ModConfig.Type.COMMON) {
                return;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return;
            }
            server.execute(() -> {
                GuideConfigSyncPacket.broadcast(server);
                LogUtil.debug("配置已重载，已向全服重新下发冒险指南配置快照: " + event.getConfig().getFileName());
            });
        }
    }
}
