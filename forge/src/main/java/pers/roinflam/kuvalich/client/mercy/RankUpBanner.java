package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.lich.ClientLichState;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 玄骸升级时屏幕中下方的横幅：烟雾底带上一枚赤毒徽记，徽记里写升级后的等级，下面是玄骸的名字和「升级」
 *
 * <p>开场时刻 Tb：怜悯之击判错（包里 b 的 bit 0 = 1）是处决时间轴上的 g_k + 2900 ms，本地已经没有这一场的时间轴
 * （处决者中途被清场之类）时按包里的 k 推：收到包 + (g_k + 2900 − 850) ms（这个包在处决第 850 ms 左右定案时发出）；
 * 直接判定判错是收到包 + 600 ms。跟着处决时间轴走的时候每帧重新对一次，冻帧时横幅也停住。</p>
 *
 * <p>入场：Tb 一道光带从徽记左边扫到右边（400 ms）→ Tb + 400 名字淡入 → Tb + 500 徽记、「升级」、底下的小菱形淡入 →
 * Tb + 750 数字最后出（1.3 倍缩回原大）。停到 Tb + 6200，全部 500 ms 淡出。同时只有一条，新的直接顶掉旧的。
 * 烟雾底带是贴图（不规则烟形、四边羽化），没有方框。字按整数倍物理像素画，不糊。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RankUpBanner {

    /** 覆盖层 id */
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(Reference.MOD_ID, "rank_banner");
    /** 整个横幅往屏幕外抬多少：原版聊天行画在 z = 50，要比它高 */
    private static final float ABOVE_CHAT_Z = 200.0F;
    private static final ResourceLocation SMOKE = new ResourceLocation(Reference.MOD_ID, "textures/gui/mercy/rank_smoke.png");

    /** 怜悯之击判错：横幅在判错那一刀填满时刻之后多久出来 */
    public static final int AFTER_WRONG_MS = 2900;
    /** 定案（发包）大约在处决第几毫秒 */
    private static final int VERDICT_MS = 850;
    /** 直接判定：收到包之后多久出来 */
    public static final int DIRECT_DELAY_MS = 600;

    private static final int SWEEP_MS = 400;
    private static final int NAME_AT = 400;
    private static final int BADGE_AT = 500;
    private static final int FADE_IN = 150;
    private static final int NUMBER_AT = 750;
    private static final int NUMBER_SCALE_MS = 100;
    private static final int NUMBER_ALPHA_MS = 60;
    private static final float NUMBER_POP = 1.3F;
    private static final int SMOKE_IN = 250;
    public static final int HOLD_UNTIL = 6200;
    private static final int FADE_OUT = 500;

    /** 扫光带的另一端颜色（淡紫） */
    private static final int SWEEP_END = 0xE6DDFF;

    // 位置（屏幕高 H 的比例；横向从中线量）
    private static final float SMOKE_HW = 0.249F;
    private static final float SMOKE_TOP = 0.66F;
    private static final float SMOKE_BOTTOM = 0.95F;
    private static final float BADGE_Y = 0.700F;
    private static final float BADGE_HW = 0.115F;
    private static final float BADGE_HH = 0.080F;
    private static final float BADGE_SHOULDER = 0.045F;
    private static final float NUMBER_H = 0.041F;
    private static final float NAME_Y = 0.805F;
    private static final float NAME_H = 0.035F;
    /** 名字底下那条墨色横条中间的不透明度 */
    private static final float NAME_BAND_ALPHA = 0.8F;
    private static final float LINE_Y = 0.845F;
    private static final float LINE_H = 0.020F;
    private static final float MINI_Y = 0.910F;
    private static final float MINI_D = 0.03F;
    private static final float SWEEP_H = 0.05F;
    private static final float SWEEP_RANGE = 0.3F;
    private static final float SWEEP_W = 0.18F;

    private static final float[] BADGE = new float[12];
    private static final float[] WHISKER = new float[2 * 9];
    private static final float[] SWEEP_X = new float[4];
    private static final float[] BAND_Y = new float[4];

    private static boolean active;
    private static int rank;
    private static String rankText = "";
    private static String[] nameChars = new String[0];
    private static String[] lineChars = new String[0];
    private static boolean nameWide;
    private static boolean lineWide;
    private static boolean fromMercy;
    private static int targetId = -1;
    private static int wrongStab = 1;
    /** t = 0（Tb）那一刻的演出钟 */
    private static long zeroClock;
    /** 调试用：冻在横幅的这一毫秒；小于 0 = 照常走 */
    private static long frozenAt = -1L;
    private static boolean soundDone;

    private RankUpBanner() {
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(OVERLAY_ID.getPath(), RankUpBanner::render);
    }

    /** 升级的效果包到了：a = 新等级，b 的 bit 0 = 来自怜悯之击、bit 1~2 = 判错的是第几刀 */
    public static void onPacket(KuvaFxPacket p) {
        int id = p.entityId();
        show(p.a, (p.b & 1) != 0, (p.b >> 1) & 3, id, nameOf(id), -1L);
    }

    /**
     * 排一条横幅（新的顶掉旧的）
     *
     * @param mercy    来自怜悯之击（按处决时间轴排）；否则是直接判定
     * @param k        判错的是第几刀（1~3）
     * @param target   玄骸的实体 id（跟处决时间轴对时用），没有给 -1
     * @param frozenMs 调试用：冻在横幅的这一毫秒，小于 0 照常走
     */
    static void show(int newRank, boolean mercy, int k, int target, String name, long frozenMs) {
        long now = KuvaFxDirector.clockMs();
        active = true;
        rank = newRank;
        rankText = Integer.toString(newRank);
        nameChars = split(name);
        nameWide = MercyPrompt.hasWide(name);
        String line = I18n.get("kuvalich.mercy.banner.rank_up");
        lineChars = split(line);
        // 汉字按 1 倍（8 像素）画认不出，至少 2 倍
        lineWide = MercyPrompt.hasWide(line);
        fromMercy = mercy;
        wrongStab = Mth.clamp(k, 1, 3);
        targetId = target;
        frozenAt = frozenMs;
        soundDone = false;
        if (!mercy) {
            zeroClock = now + DIRECT_DELAY_MS;
            return;
        }
        int at = MercyHud.fillMs(wrongStab) + AFTER_WRONG_MS;
        long e = MercyHud.elapsedFor(target);
        zeroClock = e >= 0L ? now - e + at : now + at - VERDICT_MS;
    }

    /** 撤掉当前的横幅 */
    static void clear() {
        active = false;
    }

    /** 玄骸的名字：实体身上同步的优先，其次是自己那只玄骸的状态，都没有就写「赤毒玄骸」 */
    static String nameOf(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null || entityId < 0 ? null : mc.level.getEntity(entityId);
        if (e instanceof KuvaMasterEntity lich && !lich.getLichName().isEmpty()) {
            return lich.getLichName();
        }
        String own = ClientLichState.nameTitle();
        if (own != null && !own.isEmpty()) {
            return own;
        }
        return I18n.get("entity.kuvalich.kuva_master");
    }

    /** 按码点拆成一个个字（逐字画，字距才好调） */
    private static String[] split(String s) {
        int[] cps = s.codePoints().toArray();
        String[] out = new String[cps.length];
        for (int i = 0; i < cps.length; i++) {
            out[i] = new String(Character.toChars(cps[i]));
        }
        return out;
    }

    /** 横幅现在的时刻（毫秒，0 = Tb）；没有横幅返回 {@link Long#MIN_VALUE} */
    static long nowMs() {
        if (!active) {
            return Long.MIN_VALUE;
        }
        if (frozenAt >= 0L) {
            return frozenAt;
        }
        long now = KuvaFxDirector.clockMs();
        if (fromMercy) {
            long e = MercyHud.elapsedFor(targetId);
            if (e >= 0L) {
                zeroClock = now - e + MercyHud.fillMs(wrongStab) + AFTER_WRONG_MS;
            }
        }
        return now - zeroClock;
    }

    // ==================== 画 ====================

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (!active) {
            return;
        }
        if (mc.level == null) {
            active = false;
            return;
        }
        long t = nowMs();
        if (t > HOLD_UNTIL + FADE_OUT) {
            active = false;
            return;
        }
        if (t < 0L) {
            return;
        }
        if (!soundDone) {
            soundDone = true;
            if (EffectsDisplayConfig.mercyRankupSound() && frozenAt < 0L) {
                KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.UI_BANNER_RANKUP.get(), 1.0F, 1.0F));
            }
        }
        if (mc.options.hideGui) {
            return;
        }
        // 聊天栏画在 z = 50、开着深度测试，横幅画在 0 的话名字那一行会被聊天背景挡掉；整段抬到聊天之上
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, ABOVE_CHAT_Z);
        draw(g, mc.font, mc.getWindow(), t);
        g.pose().popPose();
    }

    private static float fadeIn(float t, float at, float ms) {
        return FxCurve.clamp01((t - at) / ms);
    }

    private static float easeInOutSine(float x) {
        return -(Mth.cos(Mth.PI * x) - 1.0F) * 0.5F;
    }

    /** 按横幅时刻 {@code t}（毫秒，0 = Tb）画一帧；全部按物理像素量 */
    static void draw(GuiGraphics g, Font font, Window win, float t) {
        float gs = (float) win.getGuiScale();
        float H = win.getHeight();
        float cx = win.getWidth() * 0.5F;
        float out = t <= HOLD_UNTIL ? 1.0F : 1.0F - FxCurve.clamp01((t - HOLD_UNTIL) / FADE_OUT);
        if (out <= 0.0F) {
            return;
        }
        int lw = GuiShapes.lineWidthPx();

        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);

        // 烟雾底带
        float smokeA = 0.7F * fadeIn(t, 0.0F, SMOKE_IN) * out;
        if (smokeA > 0.002F) {
            smoke(g, cx - SMOKE_HW * H, SMOKE_TOP * H, cx + SMOKE_HW * H, SMOKE_BOTTOM * H,
                    GuiShapes.argb(FxPalette.INK, smokeA));
        }

        float badgeA = fadeIn(t, BADGE_AT, FADE_IN) * out;
        float by = BADGE_Y * H;
        GuiShapes.begin(g);
        if (badgeA > 0.002F) {
            badge(g, cx, by, H, lw, badgeA);
            float d = MINI_D * H * 0.5F;
            GuiShapes.diamondOutline(g, cx, MINI_Y * H, d, d, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.6F * badgeA));
        }
        GuiShapes.end();
        // 扫光带：Tb 起 400 ms 从左扫到右；四边都是软边，颜色从粉白横向渐变到淡紫
        if (t < SWEEP_MS) {
            float x = cx - SWEEP_RANGE * H + 2.0F * SWEEP_RANGE * H * easeInOutSine(t / SWEEP_MS);
            float env = Math.min(FxCurve.clamp01(t / 50.0F), FxCurve.clamp01((SWEEP_MS - t) / 100.0F));
            sweep(g, x, by, SWEEP_W * H * 0.5F, SWEEP_H * H * 0.5F, 0.5F * env);
        }

        // 名字
        float nameA = fadeIn(t, NAME_AT, FADE_IN) * out;
        if (nameA >= 0.02F) {
            int p = Math.max(nameWide ? 2 : 1, Math.round(NAME_H * H / 8.0F));
            // 名字底下垫一条软边的墨色横条：聊天栏有字时名字压在聊天字上，没有底就读不清
            float hw = spacedWidth(font, nameChars, p, 1.0F, 0.0F) * 0.5F + 2.0F * p;
            shadeBand(g, cx, NAME_Y * H - 0.5F * p, hw, 5.0F * p, 5.0F * p, NAME_BAND_ALPHA * nameA);
            spaced(g, font, nameChars, cx, NAME_Y * H, p, 1.0F, 0.0F, GuiShapes.argb(FxPalette.KUVA, nameA));
        }
        // 第二行「升级」
        if (badgeA >= 0.02F) {
            int p = Math.max(lineWide ? 2 : 1, Math.round(LINE_H * H / 8.0F));
            spaced(g, font, lineChars, cx, LINE_Y * H, p, 0.0F, 0.5F, GuiShapes.argb(FxPalette.BONE, badgeA));
        }
        // 数字最后出：1.3 倍缩回，α 60 ms 拉满
        if (t >= NUMBER_AT) {
            float a = fadeIn(t, NUMBER_AT, NUMBER_ALPHA_MS) * out;
            float s = NUMBER_POP - (NUMBER_POP - 1.0F) * FxCurve.easeOutCubic(fadeIn(t, NUMBER_AT, NUMBER_SCALE_MS));
            if (a >= 0.02F) {
                int p = Math.max(1, Math.round(NUMBER_H * H / 8.0F));
                int argb = GuiShapes.argb(FxPalette.BONE, a);
                float w = (font.width(rankText) - 1) * p;
                g.pose().pushPose();
                g.pose().translate(cx, Math.round(by), 0.0F);
                g.pose().scale(s, s, 1.0F);
                // 粗体：同一串字错开 1 个物理像素再画一遍
                text(g, font, rankText, -w * 0.5F - 0.5F, -3.5F * p, p, argb);
                text(g, font, rankText, -w * 0.5F + 0.5F, -3.5F * p, p, argb);
                g.pose().popPose();
            }
        }
        g.pose().popPose();
    }

    /** 徽记：横向拉长的六边形（左右两尖、上下两条平边），深红填、粉白描边，左右内侧各两道弧形细须 */
    private static void badge(GuiGraphics g, float cx, float cy, float H, float lw, float a) {
        float hw = BADGE_HW * H, hh = BADGE_HH * H, sh = BADGE_SHOULDER * H;
        BADGE[0] = cx - hw;
        BADGE[1] = cy;
        BADGE[2] = cx - hw + sh;
        BADGE[3] = cy - hh;
        BADGE[4] = cx + hw - sh;
        BADGE[5] = cy - hh;
        BADGE[6] = cx + hw;
        BADGE[7] = cy;
        BADGE[8] = cx + hw - sh;
        BADGE[9] = cy + hh;
        BADGE[10] = cx - hw + sh;
        BADGE[11] = cy + hh;
        GuiShapes.convexFill(g, BADGE, 6, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.9F * a));
        GuiShapes.polyline(g, BADGE, 6, true, lw, GuiShapes.argb(FxPalette.KUVA_PALE, a));
        int whisker = GuiShapes.argb(FxPalette.ACCENT, 0.6F * a);
        for (int side = -1; side <= 1; side += 2) {
            whisker(g, cx + side * 0.045F * H, cy, 0.038F * H, 35.0F, side, lw, whisker);
            whisker(g, cx + side * 0.045F * H, cy, 0.052F * H, 25.0F, side, lw, whisker);
        }
    }

    /** 一道弧：圆心 (x, y)、半径 r，朝 {@code side}（-1 左、1 右）张开 ±{@code spanDeg} 度 */
    private static void whisker(GuiGraphics g, float x, float y, float r, float spanDeg, int side, float lw, int argb) {
        int n = WHISKER.length / 2;
        float span = spanDeg * Mth.DEG_TO_RAD;
        for (int i = 0; i < n; i++) {
            float ang = -span + 2.0F * span * i / (n - 1);
            WHISKER[i * 2] = x + side * r * Mth.cos(ang);
            WHISKER[i * 2 + 1] = y + r * Mth.sin(ang);
        }
        GuiShapes.polyline(g, WHISKER, n, false, lw, argb);
    }

    /**
     * 逐字画一行、整体以 (cx, cy) 居中
     *
     * @param spacePx   每两个字之间多加的物理像素
     * @param spaceFrac 每个字后面再多加这个字宽的几分之几
     */
    private static void spaced(GuiGraphics g, Font font, String[] chars, float cx, float cy, int p, float spacePx,
                               float spaceFrac, int argb) {
        float total = spacedWidth(font, chars, p, spacePx, spaceFrac);
        float x = Math.round(cx - total * 0.5F);
        float y = Math.round(cy - 4.0F * p);
        for (int i = 0; i < chars.length; i++) {
            float w = font.width(chars[i]) * p;
            text(g, font, chars[i], x, y, p, argb);
            x += w + spacePx + w * spaceFrac;
        }
    }

    /** 逐字画的那一行有多宽（物理像素） */
    private static float spacedWidth(Font font, String[] chars, int p, float spacePx, float spaceFrac) {
        float total = 0.0F;
        for (int i = 0; i < chars.length; i++) {
            float w = font.width(chars[i]) * p;
            total += w;
            if (i < chars.length - 1) {
                total += spacePx + w * spaceFrac;
            }
        }
        // 每个字的步进里带着字后 1 个字体像素的空隙，最后一个字的不算，看上去才居中
        return total - p;
    }

    private static void text(GuiGraphics g, Font font, String s, float x, float y, int p, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(p, p, 1.0F);
        g.drawString(font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    /**
     * 扫光带：中心 (x, y)、半宽 hw、半高 hh。横向分三段（左软边、渐变主体、右软边），竖向上下各一半从边上的 0 渐到中线，
     * 顶点色插值出来没有接缝
     */
    private static void sweep(GuiGraphics g, float x, float y, float hw, float hh, float a) {
        if (a <= 0.002F) {
            return;
        }
        GuiShapes.flush(g);
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float edge = hw * 0.4F;
        float[] xs = SWEEP_X;
        xs[0] = x - hw;
        xs[1] = x - hw + edge;
        xs[2] = x + hw - edge;
        xs[3] = x + hw;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < 3; i++) {
            int c0 = i == 0 ? GuiShapes.argb(FxPalette.KUVA_PALE, 0.0F) : i == 1 ? GuiShapes.argb(FxPalette.KUVA_PALE, a)
                    : GuiShapes.argb(SWEEP_END, a);
            int c1 = i == 0 ? GuiShapes.argb(FxPalette.KUVA_PALE, a) : i == 1 ? GuiShapes.argb(SWEEP_END, a)
                    : GuiShapes.argb(SWEEP_END, 0.0F);
            int z0 = c0 & 0x00FFFFFF, z1 = c1 & 0x00FFFFFF;
            // 上半：上沿透明 → 中线
            col(buf, m, xs[i], y - hh, z0);
            col(buf, m, xs[i], y, c0);
            col(buf, m, xs[i + 1], y, c1);
            col(buf, m, xs[i + 1], y - hh, z1);
            // 下半：中线 → 下沿透明
            col(buf, m, xs[i], y, c0);
            col(buf, m, xs[i], y + hh, z0);
            col(buf, m, xs[i + 1], y + hh, z1);
            col(buf, m, xs[i + 1], y, c1);
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /**
     * 墨色软边横条：实色部分中心 (x, y)、半宽 hw、半高 hh，四周再各有 {@code edge} 宽从实色渐到透明
     */
    private static void shadeBand(GuiGraphics g, float x, float y, float hw, float hh, float edge, float a) {
        if (a <= 0.002F) {
            return;
        }
        GuiShapes.flush(g);
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float[] xs = SWEEP_X;
        xs[0] = x - hw - edge;
        xs[1] = x - hw;
        xs[2] = x + hw;
        xs[3] = x + hw + edge;
        float[] ys = BAND_Y;
        ys[0] = y - hh - edge;
        ys[1] = y - hh;
        ys[2] = y + hh;
        ys[3] = y + hh + edge;
        int solid = GuiShapes.argb(FxPalette.INK, a);
        int clear = FxPalette.INK & 0x00FFFFFF;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // 3×3 格：只有正中那格四角都是实色，外圈的角点透明，顶点色插值出软边
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                col(buf, m, xs[i], ys[j], bandColor(i, j, solid, clear));
                col(buf, m, xs[i], ys[j + 1], bandColor(i, j + 1, solid, clear));
                col(buf, m, xs[i + 1], ys[j + 1], bandColor(i + 1, j + 1, solid, clear));
                col(buf, m, xs[i + 1], ys[j], bandColor(i + 1, j, solid, clear));
            }
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /** 横条网格第 (i, j) 个角点的颜色：里面那四个角点实色，最外一圈透明 */
    private static int bandColor(int i, int j, int solid, int clear) {
        return i == 0 || i == 3 || j == 0 || j == 3 ? clear : solid;
    }

    private static void col(BufferBuilder buf, Matrix4f m, float x, float y, int argb) {
        buf.vertex(m, x, y, 0.0F).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, argb >>> 24).endVertex();
    }

    /** 烟雾底带：白色烟形贴图乘上颜色 */
    private static void smoke(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        GuiShapes.flush(g);
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, SMOKE);
        int a = argb >>> 24, r = (argb >> 16) & 255, gr = (argb >> 8) & 255, b = argb & 255;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        buf.vertex(m, x0, y0, 0.0F).color(r, gr, b, a).uv(0.0F, 0.0F).endVertex();
        buf.vertex(m, x0, y1, 0.0F).color(r, gr, b, a).uv(0.0F, 1.0F).endVertex();
        buf.vertex(m, x1, y1, 0.0F).color(r, gr, b, a).uv(1.0F, 1.0F).endVertex();
        buf.vertex(m, x1, y0, 0.0F).color(r, gr, b, a).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.disableBlend();
    }

    /** 调试用：现在有没有横幅、等级多少 */
    @Nullable
    static String debugState() {
        return active ? "rank " + rank + (fromMercy ? " mercy k=" + wrongStab : " direct") + " t=" + nowMs() : null;
    }
}
