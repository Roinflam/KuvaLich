package pers.roinflam.kuvalich.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 屏幕效果：暗角、闪白、四角括号。颜色都是 0xRRGGBB，只在本人的客户端画（由调用方用 {@code onlyFor} 判定）
 *
 * <p>画在世界之上、所有 HUD 之下。时间走导演的单调时钟，暂停时冻结。</p>
 *
 * <p><b>光敏</b>：每种效果有自己的冷却——暗角 1.5 s、闪白 3 s、四角括号 1.5 s。冷却内同档或更低档的请求直接丢，
 * 更高档的照常覆盖（红暴脉冲刚放过，紧接着的安魂成功暗角照放）。这样本模组的屏幕脉冲不会超过 1 Hz。
 * 同时最多一个暗角、一个闪白，新的覆盖旧的、取较大的峰值。</p>
 *
 * <p>选项：{@code screen_effects} 关掉所有暗角和括号；{@code screen_flash} 关掉时闪白改成同色暗角。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KuvaScreenFx {

    private static final ResourceLocation VIGNETTE = new ResourceLocation(Reference.MOD_ID, "textures/gui/fx/vignette.png");
    /** 暗角、闪白的不透明度上限 */
    private static final float VIGNETTE_MAX = 0.35F;
    private static final float FLASH_MAX = 0.25F;
    private static final int VIGNETTE_COOLDOWN = 1500;
    private static final int FLASH_COOLDOWN = 3000;
    private static final int BRACKET_COOLDOWN = 1500;

    private static final Effect VIG = new Effect();
    private static final Effect FLASH = new Effect();
    private static final Effect BRACKETS = new Effect();
    /** 处决刺入的边缘脉冲：和暗角分开，三刀间隔不到暗角的冷却 */
    private static final Effect PULSE = new Effect();

    private KuvaScreenFx() {
    }

    /** 一种屏幕效果的当前状态 */
    private static final class Effect {
        long start = Long.MIN_VALUE / 2;
        int rgb;
        float peak;
        int inMs, holdMs, outMs;
        FxTier tier = FxTier.C;
        long lastFire = Long.MIN_VALUE / 2;

        int total() {
            return inMs + holdMs + outMs;
        }

        boolean active(long now) {
            return now - start < total();
        }

        /** 冷却内同档或更低档的请求不放；还亮着的时候接上去不算新的一次脉冲（闪白后紧接暗角就是这样） */
        boolean accept(long now, FxTier t, int cooldown) {
            return active(now) || now - lastFire >= cooldown || t.ordinal() < tier.ordinal();
        }

        void fire(long now, int rgb, float peak, int in, int hold, int out, FxTier t) {
            boolean wasActive = active(now);
            if (wasActive) {
                // 接着还亮着的那次：已经够亮就不再渐入，免得先暗下去再亮一次
                float cur = value(now);
                if (cur >= peak * 0.5F) {
                    in = 0;
                }
                peak = Math.max(peak, cur);
                if (t.ordinal() < tier.ordinal()) {
                    tier = t;
                }
            } else {
                this.lastFire = now;
                this.tier = t;
            }
            this.start = now;
            this.rgb = rgb;
            this.peak = peak;
            this.inMs = in;
            this.holdMs = hold;
            this.outMs = out;
        }

        float value(long now) {
            float t = now - start;
            if (t < 0 || t >= total()) {
                return 0.0F;
            }
            if (t < inMs) {
                return peak * FxCurve.easeOutCubic(t / Math.max(1.0F, inMs));
            }
            if (t < inMs + holdMs) {
                return peak;
            }
            return peak * FxCurve.fadeOut((t - inMs - holdMs) / Math.max(1.0F, outMs));
        }
    }

    /** 边缘暗角：{@code inMs} 渐入、{@code holdMs} 保持、{@code outMs} 渐出，{@code peak} 是最大不透明度 */
    public static void vignette(int rgb, float peak, int inMs, int holdMs, int outMs, FxTier tier) {
        if (!EffectsDisplayConfig.screenEffects()) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (!VIG.accept(now, tier, VIGNETTE_COOLDOWN)) {
            return;
        }
        VIG.fire(now, rgb, Math.min(VIGNETTE_MAX, peak), inMs, holdMs, outMs, tier);
    }

    /** 边缘一圈脉冲，没有冷却，后一次直接盖掉前一次；只给自己的处决演出用 */
    public static void edgePulse(int rgb, float peak, int inMs, int outMs) {
        if (!EffectsDisplayConfig.screenEffects()) {
            return;
        }
        PULSE.fire(KuvaFxDirector.clockMs(), rgb, Math.min(VIGNETTE_MAX, peak), inMs, 0, outMs, FxTier.S);
    }

    /** 整屏闪一下，{@code peak} 是起始不透明度，随后按淡出曲线消失；关掉闪白时改成同色暗角 */
    public static void flash(int rgb, float peak, int ms, FxTier tier) {
        if (!EffectsDisplayConfig.screenFlash()) {
            vignette(rgb, Math.min(VIGNETTE_MAX, peak * 1.2F), 0, ms / 2, ms * 2, tier);
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (!FLASH.accept(now, tier, FLASH_COOLDOWN)) {
            return;
        }
        FLASH.fire(now, rgb, Math.min(FLASH_MAX, peak), 0, 0, ms, tier);
    }

    /** 四角 L 形括号闪两下（护盾破碎、信道污染用） */
    public static void edgeBrackets(int rgb, int ms, FxTier tier) {
        if (!EffectsDisplayConfig.screenEffects()) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (!BRACKETS.accept(now, tier, BRACKET_COOLDOWN)) {
            return;
        }
        BRACKETS.fire(now, rgb, 1.0F, 0, 0, ms, tier);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("kuva_screen_fx", KuvaScreenFx::render);
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        long now = KuvaFxDirector.clockMs();
        drawVignette(g, VIG, VIG.value(now), width, height);
        drawVignette(g, PULSE, PULSE.value(now), width, height);
        float f = FLASH.value(now);
        if (f > 0.002F) {
            int a = Math.round(f * 255.0F);
            g.fill(0, 0, width, height, (a << 24) | FLASH.rgb);
        }
        if (BRACKETS.active(now)) {
            float t = now - BRACKETS.start;
            // 38 ms 一格：亮、灭、亮、灭，之后保持暗淡淡出
            int step = (int) (t / FxCurve.FLICKER_STEP_MS);
            float b = step < 4 ? (step % 2 == 0 ? 1.0F : 0.0F) : 0.6F * FxCurve.fadeOut(t / Math.max(1.0F, BRACKETS.total()));
            if (b > 0.01F) {
                drawBrackets(g, width, height, ((int) (b * 220.0F) << 24) | BRACKETS.rgb);
            }
        }
    }

    private static void drawVignette(GuiGraphics g, Effect fx, float v, int width, int height) {
        if (v <= 0.002F) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((fx.rgb >> 16) & 0xFF) / 255.0F, ((fx.rgb >> 8) & 0xFF) / 255.0F, (fx.rgb & 0xFF) / 255.0F, v);
        try {
            g.blit(VIGNETTE, 0, 0, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
        } finally {
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    private static void drawBrackets(GuiGraphics g, int w, int h, int argb) {
        int len = 24, th = 2, m = 6;
        g.fill(m, m, m + len, m + th, argb);
        g.fill(m, m, m + th, m + len, argb);
        g.fill(w - m - len, m, w - m, m + th, argb);
        g.fill(w - m - th, m, w - m, m + len, argb);
        g.fill(m, h - m - th, m + len, h - m, argb);
        g.fill(m, h - m - len, m + th, h - m, argb);
        g.fill(w - m - len, h - m - th, w - m, h - m, argb);
        g.fill(w - m - th, h - m - len, w - m, h - m, argb);
    }
}
