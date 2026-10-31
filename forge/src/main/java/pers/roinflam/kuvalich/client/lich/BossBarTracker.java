package pers.roinflam.kuvalich.client.lich;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 记录屏幕上 Boss 条占到了哪里，别的 HUD 据此往下避让
 *
 * <p>每帧画 Boss 条那一层之前清零，之后每一条（原版的、别的模组的、本模组自绘的玄骸条）都把
 * 「这一条的 y + 占的行高」记下来，取最大的那个。监听排在最后、连被取消的也收，所以别的模组改过行高、
 * 自己接管了绘制的条也算在内。</p>
 *
 * <p>Boss 条那一层这一帧根本没画（按了 F1、别的模组把整层关了）时不会清零，所以另记一个帧号：
 * 不是这一帧记下的值一律当没有。读的一方（解密 HUD 等）画在 Boss 条之后，同一帧里读得到。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class BossBarTracker {

    private static int bottom;
    private static long bottomFrame = -1L;
    private static long frame;

    private BossBarTracker() {
    }

    /** 屏幕上所有 Boss 条最下面那条的下沿，GUI 坐标；一条都没有时为 0 */
    public static int bottomY() {
        return bottomFrame == frame ? bottom : 0;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            frame++;
        }
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) {
            bottom = 0;
            bottomFrame = frame;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        int b = event.getY() + event.getIncrement();
        if (bottomFrame != frame) {
            bottom = 0;
            bottomFrame = frame;
        }
        if (b > bottom) {
            bottom = b;
        }
    }
}
