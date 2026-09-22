package pers.roinflam.kuvalich.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.gui.codex.ModuleCodexData;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 进入世界时预热图鉴数据，把打开图鉴时的卡顿挪到看不见的时刻
 *
 * <p>图鉴第一次打开会同步走完：八个模组原型池建表（每个几十到上百个 register，
 * 每个都在 new ItemStack 并写 NBT）+ 两遍 CodexEntry 列表构建。这条链路挂在
 * {@code Minecraft.setScreen} 的同步调用上，也就是在渲染线程上，实测量级 20~35ms ——
 * 表现为「按键打开图鉴，画面顿一下才出来」。</p>
 *
 * <p>在登入世界时先跑一遍，那会儿玩家正在看加载画面，同样的耗时完全感知不到。</p>
 *
 * <p>为什么挂在 {@code LoggingIn} 而不是 {@code FMLClientSetupEvent}：
 * 建表要读配置（{@code ModuleConfig.isTypeDisabled}、模组属性倍率）并生成 ItemStack，
 * 放在客户端 setup 阶段太早。登入世界这个时机则一定在配置加载与注册表冻结之后。</p>
 *
 * <p>配置热重载会把原型池和图鉴缓存一起拆掉（见 {@code ModConfig.onConfigReload} 与
 * {@code ClientSetup.onConfigReload}），那之后第一次打开图鉴仍然会重建一次。
 * 这是有意的取舍：为一次配置改动去抢在玩家之前重建，不值得，
 * 而改配置本身就是个低频动作。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public class CodexPrewarmHandler {

    /**
     * 退出世界时清空图鉴的一次性反馈表，避免跨存档残留。
     *
     * @param event 客户端玩家登出事件
     */
    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pers.roinflam.kuvalich.client.gui.codex.CodexFeedback.clear();
    }

    /**
     * 登入世界后预热一次。
     *
     * @param event 客户端玩家登入事件
     */
    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            long start = System.nanoTime();
            ModuleCodexData.prewarm();
            LogUtil.debug("图鉴数据预热完成，耗时 " + (System.nanoTime() - start) / 1_000_000 + "ms");
        } catch (Exception e) {
            // 预热纯属提速，失败不能影响进入世界 —— 打开图鉴时还会按原路径再建一次
            LogUtil.error("图鉴数据预热失败（不影响功能，打开图鉴时会重建）", e);
        }
    }
}
