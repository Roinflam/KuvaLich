package pers.roinflam.kuvalich.client.mercy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.comm.CommHud;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.RequiemSigils;
import pers.roinflam.kuvalich.client.gui.requiem.RequiemSigils.SigilLook;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 处决玄骸时屏幕上的界面：中央大符文、底部三格、格下小点和托架
 *
 * <p>只在本地玩家是一场玄骸三刀处决的处决者时画（单刀、奴仆都不画）。一切位置都按屏幕高 H 算、横向从屏幕中线量，
 * 超宽屏不会把三格拉散；细线按物理像素画（{@link GuiShapes#lineWidthPx()}，1080p 是 2 px），GUI 缩放大的时候线不变粗。
 * 符号统一走 {@link RequiemSigils}，每一格画的都是这一刀那张卡自己的符号。</p>
 *
 * <p>时间从处决开始那一帧算（{@link MercyClient#view()} 的已过毫秒，冻帧时停住）。第 n 格「填满发光」的时刻
 * g1 / g2 / g3 = 830 / 1680 / 2670 ms：
 * <ul>
 *   <li>400 ms 硬切进三刀机位的那一刻，托架和三个空圈淡入 100 ms；</li>
 *   <li>g − 130：中央先出这张卡的暗线外框，1.7 倍停 67 ms、再 67 ms 缩回原大；g − 100：这一格也出暗线外框（不缩放）；</li>
 *   <li>g：中央和这一格同一帧填满发光。中央满亮 400 ms 后淡出 300 ms（第三刀 550 ms）；格子保持峰值 400 ms，
 *       再 250 ms 回落到 0.88 的亮度，之后一直保持；</li>
 *   <li>判错的那一刀在本该发光的那一帧直接变成红色霓虹 + 斜杠：中央的 250 ms 后淡出 320 ms，格子里的一直留着；
 *       后面的格保持空圈，不浮现、不变灰；</li>
 *   <li>格下小点（这张卡的剩余次数，每格 3 颗）和第三格的外框同一帧出现，这次用掉的那颗 2770 ms 起画一道斜杠；
 *       第一、二刀就判错时根本不出现；</li>
 *   <li>成功 5400 ms 起整组淡出 200 ms；失败在 g_k + 2000 切宽镜头时直接不画，没开电影镜头（没有宽镜头）时改成
 *       g_k + 2750 起淡出 200 ms，免得在第三人称画面里突然消失。</li>
 * </ul>
 * 成功分支在 3670 ms 把玄骸正开着的通讯窗快速收起，4670 切跪地特写时画面上就没有窗了。</p>
 *
 * <p>画的时候不分配；用到的 GL 状态由 {@link GuiShapes} / {@link RequiemSigils} 各自还原。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MercyHud {

    /** 覆盖层 id（处决运镜时藏 HUD 的白名单里认这个） */
    public static final ResourceLocation OVERLAY_ID = new ResourceLocation(Reference.MOD_ID, "mercy_hud");
    /** 整个界面往屏幕外抬多少：原版聊天行画在 z = 50、开着深度测试，处决中台词进聊天时会挡住三格的上沿 */
    private static final float ABOVE_CHAT_Z = 200.0F;

    // ==================== 时间（毫秒，从处决开始算） ====================

    /** 硬切进三刀机位，托架和空圈出现 */
    public static final int CUT_MS = 400;
    private static final int PLATE_FADE_IN = 100;
    /** 中央外框比填满早多少 */
    public static final int CENTER_LEAD = 130;
    /** 格子外框比填满早多少 */
    public static final int CELL_LEAD = 100;
    private static final int POP_HOLD = 67;
    private static final int POP_SHRINK = 67;
    private static final float POP_SCALE = 1.7F;
    private static final int CENTER_HOLD = 400;
    private static final int CENTER_FADE = 300;
    private static final int CENTER_FADE_LAST = 550;
    private static final int WRONG_FADE_DELAY = 250;
    private static final int WRONG_FADE = 320;
    private static final int CELL_PEAK_HOLD = 400;
    private static final int CELL_SETTLE = 250;
    /** 格下小点出现（和第三格外框同一帧） */
    public static final int DOTS_MS = 2540;
    /** 这次用掉的那颗小点开始画斜杠 */
    public static final int DOT_SLASH_MS = 2770;
    private static final int DOT_SLASH_DRAW = 120;
    /** 成功：整组开始淡出 */
    public static final int SUCCESS_FADE_MS = 5400;
    private static final int GROUP_FADE = 200;
    /** 失败：从判错那一刻起多久切宽镜头 */
    public static final int FAIL_WIDE_AFTER = 2000;
    /** 失败且没开电影镜头：从判错那一刻起多久开始淡出 */
    public static final int FAIL_FADE_AFTER = 2750;
    /** 成功分支收玄骸的通讯窗 */
    public static final int DISMISS_COMM_MS = 3670;

    // ==================== 位置（屏幕高 H 的比例；横向从中线量） ====================

    public static final float CENTER_Y = 0.500F;
    public static final float CENTER_SIZE = 0.216F;
    public static final float CELL_Y = 0.835F;
    public static final float CELL_DX = 0.111F;
    public static final float CELL_SIZE = 0.070F;
    public static final float CELL_RING_D = 0.056F;
    public static final float DOT_Y = 0.887F;
    public static final float DOT_DX = 0.0222F;
    public static final float DOT_D = 0.0085F;

    private static final float PLATE_HW = 0.178F;
    private static final float PLATE_TOP = 0.795F;
    private static final float PLATE_BOTTOM = 0.915F;
    private static final float PLATE_CUT = 0.012F;
    private static final float RAIL_Y = 0.832F;
    private static final float RAIL_OUTER = 0.444F;
    private static final float RAIL_FADE = 0.06F;
    private static final float RAIL_BEND = 0.249F;
    private static final float RAIL_FOOT_Y = 0.903F;
    private static final float MID_DIAMOND = 0.012F;
    private static final float SHADOW_Y = 0.850F;
    private static final float SHADOW_OUTER = 0.40F;
    private static final float FLOOR_Y1 = 0.906F;
    private static final float FLOOR_Y2 = 0.917F;
    private static final float SIDE_Y = 0.900F;
    private static final float SIDE_OUTER = 0.39F;
    private static final float SIDE_INNER = 0.215F;

    /** 托架底板的八个顶点（每帧原地改写，不分配） */
    private static final float[] PLATE = new float[16];
    private static final float[] RAIL = new float[6];

    /** 调试命令摆出来的假处决用的目标 id（不对应任何实体） */
    public static final int DEV_TARGET = -2;
    /** 调试命令摆出来的假处决；不为 null 时优先于真的 */
    @Nullable
    private static MercyPlan.Plan devPlan;
    /** 假处决开始的演出钟时刻（实时播放用） */
    private static long devStart;
    /** 假处决冻在哪一毫秒；小于 0 = 实时播放 */
    private static long devFrozen = -1L;
    /** 实时播放多久后自己撤掉 */
    private static final int DEV_PLAY_MS = 9000;

    private MercyHud() {
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(OVERLAY_ID.getPath(), MercyHud::render);
    }

    // ==================== 当前这一场 ====================

    /**
     * 调试命令用：摆一场假处决给界面画
     *
     * @param frozenMs 冻在这一毫秒；小于 0 从 0 开始实时播放
     */
    static void setDev(MercyPlan.Plan plan, long frozenMs) {
        devPlan = plan;
        devFrozen = frozenMs;
        devStart = KuvaFxDirector.clockMs();
    }

    static void clearDev() {
        devPlan = null;
    }

    @Nullable
    static MercyPlan.Plan devPlan() {
        return devPlan;
    }

    private static long devElapsed() {
        return devFrozen >= 0L ? devFrozen : KuvaFxDirector.clockMs() - devStart;
    }

    /**
     * 这只目标正在本地玩家手上演的处决已过多少毫秒（界面上的时间，冻帧时停住）；不是本地玩家在处决它返回 -1。
     * 调试摆出来的假处决用 {@link #DEV_TARGET}
     */
    public static long elapsedFor(int targetId) {
        if (devPlan != null && targetId == DEV_TARGET) {
            return devElapsed();
        }
        MercyClient.View v = MercyClient.view();
        return v != null && v.targetId() == targetId ? v.elapsedMs() : -1L;
    }

    /** 这一场要不要画处决界面：玄骸三刀才画 */
    public static boolean drawsFor(@Nullable MercyPlan.Plan plan) {
        return plan != null && plan.stabs() == 3 && !plan.variant().thrall;
    }

    /** 判错的是第几刀（1~3）；成功返回 0 */
    public static int wrongStab(MercyPlan.Plan plan) {
        return plan.prefix() >= 3 ? 0 : plan.prefix() + 1;
    }

    /** 第 k 刀（1~3）填满发光的时刻 */
    public static int fillMs(int k) {
        return MercyVariant.LICH_FILL_MS[Math.max(1, Math.min(3, k)) - 1];
    }

    // ==================== 画 ====================

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        MercyPlan.Plan dev = devPlan;
        if (dev != null) {
            long t = devElapsed();
            if (devFrozen < 0L && t > DEV_PLAY_MS) {
                devPlan = null;
                return;
            }
            drawAboveChat(g, dev, t, width, height);
            return;
        }
        MercyClient.View v = MercyClient.view();
        if (v == null || !drawsFor(v.plan())) {
            return;
        }
        drawAboveChat(g, v.plan(), v.elapsedMs(), width, height);
    }

    private static void drawAboveChat(GuiGraphics g, MercyPlan.Plan plan, long elapsed, int w, int h) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, ABOVE_CHAT_Z);
        draw(g, plan, elapsed, w, h);
        g.pose().popPose();
    }

    /**
     * 按已过毫秒画一帧
     *
     * @param w GUI 宽
     * @param h GUI 高（= H）
     */
    static void draw(GuiGraphics g, MercyPlan.Plan plan, long elapsed, int w, int h) {
        float t = elapsed;
        if (t < CUT_MS) {
            return;
        }
        int k = wrongStab(plan);
        boolean success = k == 0;
        int judged = success ? 3 : k;
        float cx = w * 0.5F;
        float H = h;
        int lwPx = GuiShapes.lineWidthPx();
        float lw = GuiShapes.pixels(g, lwPx);

        float group = groupAlpha(t, k);
        if (group > 0.002F) {
            drawPlate(g, cx, H, lw, group, t, judged);
            drawCells(g, plan, t, k, judged, cx, H, lw, lwPx, group);
            if (t >= DOTS_MS && (success || k == 3)) {
                drawDots(g, plan, t, success, cx, H, lw, group);
            }
        }
        drawCenter(g, plan, t, k, judged, cx, H, lwPx);
    }

    /** 托架、三格、小点这一组的整体透明度 */
    static float groupAlpha(float t, int k) {
        if (t < CUT_MS) {
            return 0.0F;
        }
        float a = FxCurve.clamp01((t - CUT_MS) / PLATE_FADE_IN);
        if (k == 0) {
            if (t >= SUCCESS_FADE_MS) {
                a *= 1.0F - FxCurve.clamp01((t - SUCCESS_FADE_MS) / GROUP_FADE);
            }
            return a;
        }
        float gk = fillMs(k);
        if (EffectsDisplayConfig.mercyCamera()) {
            // 开着电影镜头时这一刻硬切宽镜头，界面跟着镜头一起切掉
            return t >= gk + FAIL_WIDE_AFTER ? 0.0F : a;
        }
        if (t >= gk + FAIL_FADE_AFTER) {
            a *= 1.0F - FxCurve.clamp01((t - gk - FAIL_FADE_AFTER) / GROUP_FADE);
        }
        return a;
    }

    /** 托架：暗底板、上沿亮线（外段淡出、内侧 45° 折下去接到底板两角）、中点菱形、淡平行线、板底细线、外侧短线 */
    private static void drawPlate(GuiGraphics g, float cx, float H, float lw, float alpha, float t, int judged) {
        float hw = PLATE_HW * H, top = PLATE_TOP * H, bot = PLATE_BOTTOM * H, c = PLATE_CUT * H;
        float x0 = cx - hw, x1 = cx + hw;
        PLATE[0] = x0 + c;
        PLATE[1] = top;
        PLATE[2] = x1 - c;
        PLATE[3] = top;
        PLATE[4] = x1;
        PLATE[5] = top + c;
        PLATE[6] = x1;
        PLATE[7] = bot - c;
        PLATE[8] = x1 - c;
        PLATE[9] = bot;
        PLATE[10] = x0 + c;
        PLATE[11] = bot;
        PLATE[12] = x0;
        PLATE[13] = bot - c;
        PLATE[14] = x0;
        PLATE[15] = top + c;

        int pale = FxPalette.KUVA_PALE;
        float railY = RAIL_Y * H;
        float outer = RAIL_OUTER * H, fadeIn = (RAIL_OUTER - RAIL_FADE) * H, bend = RAIL_BEND * H;
        float footX = PLATE_HW * H, footY = RAIL_FOOT_Y * H;

        GuiShapes.begin(g);
        GuiShapes.convexFill(g, PLATE, 8, GuiShapes.argb(FxPalette.INK, 0.55F * alpha));
        int rail = GuiShapes.argb(pale, 0.55F * alpha);
        int railClear = GuiShapes.argb(pale, 0.0F);
        for (int side = -1; side <= 1; side += 2) {
            GuiShapes.hairline(g, cx + side * outer, railY, cx + side * fadeIn, railY, lw, railClear, rail);
            RAIL[0] = cx + side * fadeIn;
            RAIL[1] = railY;
            RAIL[2] = cx + side * bend;
            RAIL[3] = railY;
            RAIL[4] = cx + side * footX;
            RAIL[5] = footY;
            GuiShapes.polyline(g, RAIL, 3, false, lw, rail);

            GuiShapes.hairline(g, cx + side * SHADOW_OUTER * H, SHADOW_Y * H, cx + side * PLATE_HW * H, SHADOW_Y * H,
                    lw, GuiShapes.argb(pale, 0.28F * alpha));
            GuiShapes.hairline(g, cx + side * SIDE_OUTER * H, SIDE_Y * H, cx + side * SIDE_INNER * H, SIDE_Y * H,
                    lw, GuiShapes.argb(pale, 0.33F * alpha));
        }
        GuiShapes.hairline(g, x0, FLOOR_Y1 * H, x1, FLOOR_Y1 * H, lw, GuiShapes.argb(pale, 0.45F * alpha));
        GuiShapes.hairline(g, x0, FLOOR_Y2 * H, x1, FLOOR_Y2 * H, lw, GuiShapes.argb(pale, 0.25F * alpha));
        // 中点菱形落在中间那一格里：格子还是空圈时它是圈心的一点，符号出来以后就不画，免得从笔画的空隙里透出来
        if (judged < 2 || t < fillMs(2) - CELL_LEAD) {
            float d = MID_DIAMOND * H * 0.5F;
            GuiShapes.diamondOutline(g, cx, RAIL_Y * H, d, d, lw, GuiShapes.argb(pale, 0.7F * alpha));
        }
        GuiShapes.end();
    }

    /** 底部三格：没轮到的是细圆圈；轮到的先出暗外框、再填满发光、再回落；判错的那格红色霓虹 + 斜杠 */
    private static void drawCells(GuiGraphics g, MercyPlan.Plan plan, float t, int k, int judged, float cx, float H,
                                  float lw, int lwPx, float alpha) {
        float y = CELL_Y * H;
        float size = CELL_SIZE * H;
        float ringR = CELL_RING_D * H * 0.5F;
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * CELL_DX * H;
            int card = plan.cardIds()[i];
            float gi = MercyVariant.LICH_FILL_MS[i];
            if (i >= judged || t < gi - CELL_LEAD) {
                GuiShapes.ring(g, x, y, ringR, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.45F * alpha), 1.0F);
            } else if (t < gi) {
                RequiemSigils.drawLook(g, card, x, y, size, SigilLook.OUTLINE_DARK, alpha, lwPx);
            } else if (i == k - 1) {
                RequiemSigils.drawLook(g, card, x, y, size, SigilLook.WRONG, alpha, lwPx);
            } else {
                float e = t < gi + CELL_PEAK_HOLD ? 0.0F
                        : FxCurve.easeOutCubic(FxCurve.clamp01((t - gi - CELL_PEAK_HOLD) / CELL_SETTLE));
                drawSettling(g, card, x, y, size, e, alpha, lwPx);
            }
        }
    }

    /**
     * 点亮之后往稳定亮度回落：{@code e = 0} 是刚点亮，{@code 1} 是回落完。两头直接用对应的画法，
     * 中间按同样的配方插颜色（线往米灰混 12%、光 α × 0.88）
     */
    private static void drawSettling(GuiGraphics g, int card, float x, float y, float size, float e, float alpha,
                                     int lwPx) {
        if (e <= 0.0F) {
            RequiemSigils.drawLook(g, card, x, y, size, SigilLook.LIT, alpha, lwPx);
        } else if (e >= 1.0F) {
            RequiemSigils.drawLook(g, card, x, y, size, SigilLook.LIT_SETTLED, alpha, lwPx);
        } else {
            int glow = GuiShapes.argb(FxPalette.KUVA_PALE, 0.3F * (1.0F - 0.12F * e) * alpha);
            int line = GuiShapes.argb(FxCurve.lerpColor(FxPalette.BONE, FxPalette.TEXT_MID, 0.12F * e), alpha);
            RequiemSigils.drawLayers(g, card, x, y, size, 0, 0, glow, line);
        }
    }

    /**
     * 格下小点（这张卡处决之前还剩几次）：还剩的亮粉 + 一圈猩红光晕；这次用掉的猩红 + 一道斜杠；早就用掉的暗灰。
     * 成功时第 r 颗（r = 处决之前的剩余次数）是这次用掉的；失败不扣耐久，全按还剩画
     */
    private static void drawDots(GuiGraphics g, MercyPlan.Plan plan, float t, boolean success, float cx, float H,
                                 float lw, float alpha) {
        float y = DOT_Y * H;
        float r = DOT_D * H * 0.5F;
        float slash = FxCurve.clamp01((t - DOT_SLASH_MS) / DOT_SLASH_DRAW);
        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            float x0 = cx + (i - 1) * CELL_DX * H;
            int left = plan.remaining()[i];
            int usedNow = success ? left - 1 : -1;
            for (int j = 0; j < 3; j++) {
                float x = x0 + (j - 1) * DOT_DX * H;
                if (j == usedNow) {
                    GuiShapes.disc(g, x, y, r, GuiShapes.argb(FxPalette.KUVA, alpha));
                    if (slash > 0.0F) {
                        float s = r * 1.7F;
                        GuiShapes.hairline(g, x - s, y + s, x - s + 2.0F * s * slash, y + s - 2.0F * s * slash, lw,
                                GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
                    }
                } else if (j < left) {
                    GuiShapes.disc(g, x, y, r + lw, GuiShapes.argb(FxPalette.KUVA, 0.45F * alpha));
                    GuiShapes.disc(g, x, y, r, GuiShapes.argb(FxPalette.KUVA_PALE, alpha));
                } else {
                    GuiShapes.disc(g, x, y, r, GuiShapes.argb(FxPalette.ASH, 0.35F * alpha));
                }
            }
        }
        GuiShapes.end();
    }

    /** 中央大符文：每一刀一枚，外框阶段放大再缩回，填满发光后停一会儿淡出；判错的是红色霓虹 + 斜杠 */
    private static void drawCenter(GuiGraphics g, MercyPlan.Plan plan, float t, int k, int judged, float cx, float H,
                                   int lwPx) {
        float y = CENTER_Y * H;
        float size = CENTER_SIZE * H;
        float lw = lwPx * 1.5F;
        for (int i = 0; i < judged; i++) {
            float gi = MercyVariant.LICH_FILL_MS[i];
            if (t < gi - CENTER_LEAD) {
                continue;
            }
            int card = plan.cardIds()[i];
            if (t < gi) {
                float dt = t - (gi - CENTER_LEAD);
                float s = dt < POP_HOLD ? POP_SCALE
                        : POP_SCALE - (POP_SCALE - 1.0F) * FxCurve.easeOutCubic(FxCurve.clamp01((dt - POP_HOLD) / POP_SHRINK));
                RequiemSigils.drawLook(g, card, cx, y, size * s, SigilLook.OUTLINE_DARK, 1.0F, lw);
            } else if (i == k - 1) {
                float a = 1.0F - FxCurve.clamp01((t - gi - WRONG_FADE_DELAY) / WRONG_FADE);
                RequiemSigils.drawLook(g, card, cx, y, size, SigilLook.WRONG, a, lw);
            } else {
                int fade = i == 2 ? CENTER_FADE_LAST : CENTER_FADE;
                float a = 1.0F - FxCurve.clamp01((t - gi - CENTER_HOLD) / fade);
                RequiemSigils.drawLook(g, card, cx, y, size, SigilLook.LIT, a, lw);
            }
        }
    }

    // ==================== 成功分支收通讯窗 ====================

    /** FORGE 总线上的逐 tick 检查（覆盖层在 F1 藏界面时不画，收窗不能挂在画里） */
    @OnlyIn(Dist.CLIENT)
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
    public static final class Ticker {

        /** 这一场已经收过窗（按目标 id 记；换了一场或者没有处决时清掉） */
        private static int dismissedFor = Integer.MIN_VALUE;

        private Ticker() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            MercyClient.View v = MercyClient.view();
            if (v == null || !drawsFor(v.plan()) || wrongStab(v.plan()) != 0) {
                if (v == null) {
                    dismissedFor = Integer.MIN_VALUE;
                }
                return;
            }
            if (v.elapsedMs() >= DISMISS_COMM_MS && dismissedFor != v.targetId()) {
                dismissedFor = v.targetId();
                CommHud.dismiss(CommSpeaker.LICH);
            }
        }
    }
}
