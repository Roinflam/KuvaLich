package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.network.packet.MercyChoiceOpenPacket;
import pers.roinflam.kuvalich.network.packet.MercyChoicePacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;

/**
 * 终结玄骸时「感化 / 消灭」的选择牌：两块牌左右并排、中线对齐屏幕中线，顶上一条倒计时细线。
 * 按住「向左移动」或鼠标左键选感化，「向右移动」或右键选消灭，按满 {@value #HOLD_MS} ms 才发包。
 *
 * <p>整块在物理像素里排（和倒地小牌一样），GUI 缩放只影响不到它。只在本地处决者停在选择那一拍、
 * 目标的选择状态还是「等待中」时画；结果以服务端写回目标的同步数据为准，这里不自己判。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MercyChoiceHud {

    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(Reference.MOD_ID, "mercy_choice");

    static final int HOLD_MS = 600;
    private static final int FADE_IN_MS = 150;
    /** 和处决界面一样抬到聊天行上面 */
    private static final float ABOVE_CHAT_Z = 200.0F;

    // 布局，都按窗口物理高 P 的比例
    private static final float CARD_TOP = 0.585F;
    private static final float CARD_W = 0.25F;
    private static final float GAP_HALF = 0.018F;
    private static final float PAD_V = 0.012F;
    private static final float TIMER_ABOVE = 0.030F;
    private static final float DESC_BELOW = 0.012F;

    private static final int NONE = -1, VANQUISH = 0, CONVERT = 1;

    /** 正在按住哪一边、从什么时候（演出钟）开始 */
    private static int pressing = NONE;
    private static long pressStart;
    /** 已经发出去的选择；结果回来之前牌一直亮着 */
    private static int sent = NONE;
    private static int forTarget = Integer.MIN_VALUE;
    private static long shownSince = -1L;

    @Nullable
    private static Dev dev;

    /** 开发命令摆的假牌：{@code judging} = 倒计时已走完 */
    private record Dev(boolean allowConvert, String releaseName, boolean judging, long startMs, int totalTicks,
                       @Nullable EphemeraType ephemera) {
    }

    private MercyChoiceHud() {
    }

    static void setDev(boolean allowConvert, String releaseName, boolean judging, @Nullable EphemeraType ephemera) {
        dev = new Dev(allowConvert, releaseName, judging, KuvaFxDirector.clockMs(), 200, ephemera);
        reset(Integer.MIN_VALUE);
    }

    static void clearDev() {
        dev = null;
    }

    /** 这一帧要画的东西；不画返回 null */
    private record View(int targetId, boolean allowConvert, String releaseName, float remaining, boolean timed,
                        @Nullable EphemeraType ephemera) {
    }

    @Nullable
    private static View view(float partialTick) {
        Dev d = dev;
        if (d != null) {
            float total = d.totalTicks;
            float left = d.judging ? 0.0F : total - (KuvaFxDirector.clockMs() - d.startMs) / 50.0F;
            if (left < -60.0F) {
                // 截止后三秒自己撤掉，免得一直挂着
                dev = null;
                return null;
            }
            return new View(Integer.MIN_VALUE, d.allowConvert, d.releaseName, Math.max(0.0F, left) / total, true,
                    d.ephemera);
        }
        int id = MercyClient.choiceTargetId();
        if (id < 0) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        EphemeraType ephemera = ephemeraOf(mc, id);
        MercyChoiceOpenPacket open = MercyChoiceClient.forEntity(id);
        if (open == null) {
            return new View(id, true, "", 1.0F, false, ephemera);
        }
        float passed = 0.0F;
        if (mc.level != null) {
            passed = mc.level.getGameTime() - MercyChoiceClient.receivedAtTick() + (mc.isPaused() ? 0.0F : partialTick);
        }
        float total = Math.max(1, open.remainingTicks);
        float left = Math.max(0.0F, total - passed) / total;
        return new View(id, open.allowConvert, open.releaseName, left, true, ephemera);
    }

    /** 感化、消灭都发卡上那件幻纹，玄骸身上同步的外观就是它，不用另外下发 */
    @Nullable
    private static EphemeraType ephemeraOf(Minecraft mc, int id) {
        if (mc.level == null || !(mc.level.getEntity(id) instanceof KuvaMasterEntity lich)) {
            return null;
        }
        EphemeraLook look = lich.getEphemeraLook();
        return look.isNone() ? null : look.styleType();
    }

    private static void reset(int target) {
        pressing = NONE;
        sent = NONE;
        forTarget = target;
        shownSince = -1L;
    }

    // ==================== 读键 ====================

    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        View v = view(0.0F);
        if (v == null) {
            if (forTarget != Integer.MIN_VALUE || pressing != NONE) {
                reset(Integer.MIN_VALUE);
            }
            return;
        }
        if (v.targetId != forTarget) {
            reset(v.targetId);
        }
        Minecraft mc = Minecraft.getInstance();
        if (sent != NONE) {
            return;
        }
        if (mc.screen != null) {
            pressing = NONE;
            return;
        }
        long window = mc.getWindow().getWindow();
        boolean left = down(window, mc.options.keyLeft) || mouseDown(window, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        boolean right = down(window, mc.options.keyRight) || mouseDown(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        int want = left == right ? NONE : left ? CONVERT : VANQUISH;
        if (want == CONVERT && !v.allowConvert) {
            want = NONE;
        }
        long now = KuvaFxDirector.clockMs();
        if (want != pressing) {
            pressing = want;
            pressStart = now;
        }
        if (pressing != NONE && now - pressStart >= HOLD_MS) {
            sent = pressing;
            if (dev == null) {
                MercyChoicePacket.send(sent == CONVERT);
            }
        }
    }

    /** 绑定自己的按下状态（处决中移动键没被清）再加原始状态：别的模组改了按键分发也读得到 */
    private static boolean down(long window, KeyMapping k) {
        if (k.isDown()) {
            return true;
        }
        InputConstants.Key key = k.getKey();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> mouseDown(window, key.getValue());
            case SCANCODE -> false;
        };
    }

    private static boolean mouseDown(long window, int button) {
        return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
    }

    // ==================== 画 ====================

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        View v = view(partialTick);
        if (v == null) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        if (shownSince < 0L) {
            shownSince = now;
        }
        float alpha = FxCurve.clamp01((now - shownSince) / (float) FADE_IN_MS);
        float progress = pressing == NONE ? 0.0F : FxCurve.clamp01((now - pressStart) / (float) HOLD_MS);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, ABOVE_CHAT_Z);
        draw(g, mc.font, mc.getWindow(), v, sent != NONE ? sent : pressing, sent != NONE ? 1.0F : progress, alpha);
        g.pose().popPose();
    }

    /**
     * 按物理像素画一帧
     *
     * @param active   正在按住或已经选定的一边
     * @param progress 按住的进度 0~1（已选定恒为 1）
     */
    static void draw(GuiGraphics g, Font font, Window win, View v, int active, float progress, float alpha) {
        float gs = (float) win.getGuiScale();
        int pw = win.getWidth(), ph = win.getHeight();
        Component convertTitle = Component.translatable("gui.kuvalich.mercy_choice.convert");
        Component vanquishTitle = Component.translatable("gui.kuvalich.mercy_choice.vanquish");
        // 汉字位图 16 像素高，按 1 倍画糊成一团：带宽字符时至少 2 倍
        boolean wide = MercyPrompt.hasWide(convertTitle.getString());
        int p = Math.max(wide ? 2 : 1, Math.round(ph / 540.0F));
        int pt = p + Math.max(1, p / 2);
        int lw = GuiShapes.lineWidthPx();

        int mid = pw / 2;
        int gapHalf = Math.max(2, Math.round(ph * GAP_HALF));
        int cw = Math.round(ph * CARD_W);
        int padV = Math.max(2, Math.round(ph * PAD_V));
        int titleH = 8 * pt;
        int hintH = 8 * p;
        int innerGap = Math.max(2, p * 3);
        int ch = padV + titleH + innerGap + hintH + padV;
        int y0 = Math.round(ph * CARD_TOP);

        String keyL = mc().options.keyLeft.getTranslatedKeyMessage().getString();
        String keyR = mc().options.keyRight.getTranslatedKeyMessage().getString();
        String mouseL = Component.translatable("key.mouse.left").getString();
        String mouseR = Component.translatable("key.mouse.right").getString();
        Component hintL = Component.translatable("gui.kuvalich.mercy_choice.hold", keyL + " / " + mouseL);
        Component hintR = Component.translatable("gui.kuvalich.mercy_choice.hold", keyR + " / " + mouseR);

        // 窗口矮时字号不跟着缩（汉字至少 2 倍），按窗高算的牌宽会装不下按键提示，说明里的词也会被拆到下一行。
        // 牌宽至少放下标题、按键提示和说明最长的一行，两边各留 4p，最宽到屏宽的 3 成
        int need = Math.max(font.width(convertTitle), font.width(vanquishTitle)) * pt;
        need = Math.max(need, Math.max(font.width(hintL), font.width(hintR)) * p);
        String descL = Component.translatable(v.allowConvert
                ? "gui.kuvalich.mercy_choice.convert.desc" : "gui.kuvalich.mercy_choice.disabled").getString();
        String descR = Component.translatable("gui.kuvalich.mercy_choice.vanquish.desc").getString();
        for (String line : (descL + "\n" + descR).split("\n")) {
            need = Math.max(need, font.width(line) * p);
        }
        cw = Math.max(cw, Math.min(need + 8 * p, Math.round(pw * 0.3F)));
        int lx0 = mid - gapHalf - cw, lx1 = mid - gapHalf;
        int rx0 = mid + gapHalf, rx1 = mid + gapHalf + cw;

        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);

        // 倒计时：从中线往两边对称收
        int timerY = y0 - Math.max(4, Math.round(ph * TIMER_ABOVE));
        int half = (rx1 - lx0) / 2;
        GuiShapes.begin(g);
        card(g, lx0, y0, lx1, y0 + ch, lw, v.allowConvert, active == CONVERT, active == CONVERT ? progress : 0.0F, alpha);
        card(g, rx0, y0, rx1, y0 + ch, lw, true, active == VANQUISH, active == VANQUISH ? progress : 0.0F, alpha);
        if (v.timed && v.remaining > 0.0F) {
            int len = Math.round(half * v.remaining);
            GuiShapes.rect(g, mid - half, timerY, mid + half, timerY + lw, GuiShapes.argb(FxPalette.ASH, 0.25F * alpha));
            GuiShapes.rect(g, mid - len, timerY, mid + len, timerY + lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.85F * alpha));
        }
        GuiShapes.end();

        if (alpha >= 0.02F) {
            int titleY = y0 + padV;
            int hintY = titleY + titleH + innerGap;
            int dim = GuiShapes.argb(FxPalette.ASH, alpha);
            centered(g, font, convertTitle.getVisualOrderText(), lx0, lx1, titleY, pt,
                    GuiShapes.argb(v.allowConvert ? FxPalette.BONE : FxPalette.ASH, alpha));
            centered(g, font, vanquishTitle.getVisualOrderText(), rx0, rx1, titleY, pt, GuiShapes.argb(FxPalette.BONE, alpha));
            centered(g, font, hintL.getVisualOrderText(), lx0, lx1, hintY, p,
                    v.allowConvert ? GuiShapes.argb(FxPalette.TEXT_MID, alpha) : dim);
            centered(g, font, hintR.getVisualOrderText(), rx0, rx1, hintY, p, GuiShapes.argb(FxPalette.TEXT_MID, alpha));

            int descY = y0 + ch + Math.max(2, Math.round(ph * DESC_BELOW));
            int lineH = 10 * p;
            int wrap = Math.max(1, cw / p);
            int y = descY;
            Component convertDesc = Component.translatable(v.allowConvert
                    ? "gui.kuvalich.mercy_choice.convert.desc" : "gui.kuvalich.mercy_choice.disabled");
            for (FormattedCharSequence line : font.split(convertDesc, wrap)) {
                centered(g, font, line, lx0, lx1, y, p, v.allowConvert ? GuiShapes.argb(FxPalette.BONE, 0.9F * alpha) : dim);
                y += lineH;
            }
            if (v.allowConvert && !v.releaseName.isEmpty()) {
                Component rel = Component.translatable("gui.kuvalich.mercy_choice.release", v.releaseName);
                for (FormattedCharSequence line : font.split(rel, wrap)) {
                    centered(g, font, line, lx0, lx1, y, p, GuiShapes.argb(FxPalette.KUVA_HOT, alpha));
                    y += lineH;
                }
            }
            y = descY;
            for (FormattedCharSequence line : font.split(Component.translatable("gui.kuvalich.mercy_choice.vanquish.desc"), wrap)) {
                centered(g, font, line, rx0, rx1, y, p, GuiShapes.argb(FxPalette.BONE, 0.9F * alpha));
                y += lineH;
            }
            // 「等待裁决」的底块上沿是 jy - 2p，幻纹那行再往上空 3p，两样同时出现也不叠
            int jy = timerY + lw / 2 - 4 * p;
            if (v.ephemera != null) {
                Component name = Component.translatable(v.ephemera.nameKey())
                        .withStyle(Style.EMPTY.withColor(FxPalette.ACCENT));
                List<FormattedCharSequence> lines = font.split(
                        Component.translatable("gui.kuvalich.mercy_choice.ephemera", name), Math.max(1, (rx1 - lx0) / p));
                int ey = jy - 2 * p - 3 * p - lines.size() * 10 * p + 2 * p;
                for (FormattedCharSequence line : lines) {
                    centered(g, font, line, lx0, rx1, ey, p, GuiShapes.argb(FxPalette.TEXT_MID, alpha));
                    ey += 10 * p;
                }
            }
            if (v.timed && v.remaining <= 0.0F) {
                // 倒计时走完：等服务端的结果，字的竖直中线压在细线上
                FormattedCharSequence judging = Component.translatable("gui.kuvalich.mercy_choice.judging").getVisualOrderText();
                int jw = font.width(judging) * p, jpad = 3 * p;
                int jx0 = mid - jw / 2 - jpad, jx1 = jx0 + jw + jpad * 2;
                GuiShapes.begin(g);
                GuiShapes.rect(g, jx0, jy - p * 2, jx1, jy + 8 * p + p * 2, GuiShapes.argb(FxPalette.INK, 0.62F * alpha));
                GuiShapes.end();
                centered(g, font, judging, jx0, jx1, jy, p, GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
            }
        }
        g.pose().popPose();
    }

    /** 一块牌：暗底、细框；按住时框变亮、底边一条进度从左往右走 */
    private static void card(GuiGraphics g, int x0, int y0, int x1, int y1, int lw, boolean enabled, boolean active,
                             float progress, float alpha) {
        int frameRgb = !enabled ? FxPalette.ASH : active ? FxPalette.KUVA : FxPalette.KUVA_PALE;
        float frameA = !enabled ? 0.35F : active ? 1.0F : 0.55F;
        GuiShapes.rect(g, x0, y0, x1, y1, GuiShapes.argb(FxPalette.INK, (enabled ? 0.62F : 0.45F) * alpha));
        int frame = GuiShapes.argb(frameRgb, frameA * alpha);
        GuiShapes.rect(g, x0, y0, x1, y0 + lw, frame);
        GuiShapes.rect(g, x0, y1 - lw, x1, y1, frame);
        GuiShapes.rect(g, x0, y0 + lw, x0 + lw, y1 - lw, frame);
        GuiShapes.rect(g, x1 - lw, y0 + lw, x1, y1 - lw, frame);
        if (progress > 0.0F) {
            int bar = lw * 2;
            int px1 = x0 + lw + Math.round((x1 - x0 - lw * 2) * progress);
            GuiShapes.rect(g, x0 + lw, y1 - lw - bar, px1, y1 - lw, GuiShapes.argb(FxPalette.KUVA, 0.9F * alpha));
        }
    }

    /** 在 [x0, x1) 里水平居中画一行字，奇数差往左偏 */
    private static void centered(GuiGraphics g, Font font, FormattedCharSequence s, int x0, int x1, int y, int p, int argb) {
        int w = font.width(s) * p;
        int x = x0 + (x1 - x0 - w) / 2;
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(p, p, 1.0F);
        g.drawString(font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(OVERLAY_ID.getPath(), MercyChoiceHud::render);
    }

    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
    public static final class Ticker {
        private Ticker() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            tick(event);
        }
    }
}
