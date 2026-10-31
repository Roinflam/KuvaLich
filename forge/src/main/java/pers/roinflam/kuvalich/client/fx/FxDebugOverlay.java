package pers.roinflam.kuvalich.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.client.sound.KuvaMixer;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 开发环境下 F3 左栏多一行特效计数：本模组活跃粒子 / 本 tick 新粒子、活跃几何、活跃时间轴，
 * 以及最近结束的一段演出总共生成了多少粒子 / 几何（拿来对预算表）；第二行是混音器的全局压混倍数和在压的 key。正式版里不显示
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class FxDebugOverlay {

    private FxDebugOverlay() {
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        // 这个事件不开 F3 也会触发（只是左右栏是空的），往里加了字就会一直显示，所以自己判断 F3 开没开
        if (FMLEnvironment.production || !Minecraft.getInstance().options.renderDebug) {
            return;
        }
        event.getLeft().add("KuvaFx: particles " + FxBudget.liveParticles() + " (+" + FxBudget.newThisTick() + ")"
                + " | geometry " + KuvaFxGeometry.activeCount()
                + " | timelines " + KuvaFxDirector.activeCount()
                + " | last " + KuvaFxDirector.lastSummary());
        float mix = KuvaMixer.globalGain();
        event.getLeft().add(String.format("KuvaMixer: gain %.3f (%.1f dB) keys %s", mix,
                mix > 0.0F ? 20.0D * Math.log10(mix) : -99.0D, KuvaMixer.activeKeys()));
    }
}
