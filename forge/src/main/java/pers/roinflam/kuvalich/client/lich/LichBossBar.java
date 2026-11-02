package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.ElementStatusRow;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.HoloShapes;
import pers.roinflam.kuvalich.client.mercy.MercyClient;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 专属玄骸的 Boss 条，和升级横幅一套画法（细线框、尖头玻璃板、青色刻度、双线菱框装等级）：
 * 左头一颗大菱框装等级，右边拉一条带十格刻度的血条；血条上方左边是名字和三颗密语小晶，右边是交战增幅；
 * 刻度下面靠右一行适应层数，再往下是元素状态
 *
 * <p>服务端发的是普通的 Boss 条，名字用 {@code kuvalich.bossbar.lich} 这个键、带四个参数（全大写名字、等级、
 * 主人名、主人 UUID），原版画法下只显示名字。这里认出这个键就取消原版绘制、自己画，并把这一条的行高改成
 * 实际用掉的高度，后面的 Boss 条照原版往下排。别的 Boss 条不动。</p>
 *
 * <p>按物理像素画（姿态缩成 1 / 界面缩放），长度都是屏高的比例，字取整数倍物理像素，线和字不糊。
 * 字用读数字体 {@link HoloShapes#HUD}，「强制使用 Unicode 字体」开着也是像素字，大写字母、数字竖直方向占 0~6 行，
 * 名字、小晶、增幅、等级数字、层数都按这个量居中；字底下不垫错开一个字像素的暗影（放大后像两层字印歪了），
 * 浮在画面上的字各自垫一块软边暗红玻璃。</p>
 *
 * <p>入场：这条第一次出现（或者 5 秒没画过又出现）时，0–280 ms 菱框从上顶点描一圈，80 起菱框里填色、数字淡入，
 * 140–440 血条和底板从菱框往右展开，300–520 字和小晶淡入。掉血后一段淡色残影停 250 ms 再追上。
 * 倒地时整条变灰。别人的玄骸（客户端比对第 4 个参数和本地玩家 UUID）名字后面接「· 某某的玄骸」，小晶全暗。
 * 处决运镜中整条不画（行高照样留着，免得别的条跳位）。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichBossBar {

    /** 服务端 Boss 条名字用的语言键 */
    public static final String KEY = "kuvalich.bossbar.lich";

    /** 掉血后残影停多久再追 */
    private static final int TRAIL_HOLD_MS = 250;
    /** 残影追上用多久 */
    private static final int TRAIL_CHASE_MS = 600;
    /** 多久重新在世界里找一次对应的实体 */
    private static final int RESCAN_MS = 500;
    /** 多久没画过的条从缓存里清掉（再出现时重放入场） */
    private static final int FORGET_MS = 5000;
    /** 交战增幅到这个千分比才显示 */
    private static final int FURY_SHOW_PERMILLE = 50;

    // 下面的长度都是屏高（物理像素）的比例，宽度类的是屏宽的比例
    /** 整条宽（屏宽比例） */
    private static final float WIDTH = 0.34F;
    private static final float DIA_HH = 0.036F;
    private static final float DIA_HW = 0.031F;
    private static final float DIA_INSET = 0.006F;
    private static final float BAR_H = 0.010F;
    private static final float GAP = 0.010F;
    private static final float CHEV = 0.008F;
    private static final float TICK_SHORT = 0.004F;
    private static final float TICK_LONG = 0.009F;
    /** 血条尾巴后面拖出去的渐隐红线（屏宽比例） */
    private static final float TAIL = 0.05F;
    private static final float PIP_HW = 0.0045F;
    private static final float PIP_HH = 0.0068F;
    /** 名字的字号：大写字母高度占屏高的比例 */
    private static final float NAME_CAP = 0.0225F * 7.0F / 8.0F;
    /** 层数小字的字号 */
    private static final float SMALL_CAP = 0.0125F * 7.0F / 8.0F;

    private static final int DOWNED_GLASS = 0x15181A;
    private static final int NUMBER_WHITE = 0xF4F7F8;

    /** 每一条 Boss 条自己的状态：解析好的参数、对应实体、残影、入场时刻 */
    private static final class Bar {
        String nameUpper = "";
        String rankText = "";
        @Nullable
        UUID owner;
        String ownerName = "";
        boolean mine;
        /** 别人看时名字后面那半句（含前面的「 · 」），按语言缓存 */
        String rest = "";
        String restLang = "";
        Component lastName;
        boolean otherView;

        int entityId = -1;
        long nextScanMs;

        float trail = -1.0F;
        float lastProgress = -1.0F;
        long holdUntilMs;
        float chaseFrom;
        long lastSeenMs;
        long bornMs;
    }

    /** 开发用：增幅、适应层数换成演示值（+465%，近战 2 / 弓箭 1 / 魔法 3），截图对齐用 */
    static boolean demoReadout;

    private static final Map<UUID, Bar> BARS = new HashMap<>();
    private static long lastPruneMs;

    /** 0~7 的层数字串，免得每帧拼字串 */
    private static final String[] NUM = new String[8];
    private static final String[] PLUS_PCT = new String[1000];
    private static final float[] POLY = new float[12];
    private static final float[] POLY_A = new float[6];
    private static final float[] PATH = new float[8];

    static {
        for (int i = 0; i < NUM.length; i++) {
            NUM[i] = Integer.toString(i);
        }
    }

    // 这一帧的公共量（只在渲染线程用）
    private static Font font;
    private static Matrix4f m;
    private static float W;
    private static float H;
    private static int lw;

    private LichBossBar() {
    }

    /** 这条 Boss 条是不是本模组的玄骸条 */
    public static boolean isLichBar(LerpingBossEvent event) {
        return event.getName().getContents() instanceof TranslatableContents tc && KEY.equals(tc.getKey());
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        LerpingBossEvent boss = event.getBossEvent();
        if (!isLichBar(boss)) {
            return;
        }
        event.setCanceled(true);
        GuiGraphics g = event.getGuiGraphics();
        int h = g.guiHeight();
        int inc = Math.max(24, Math.round(0.095F * h));
        event.setIncrement(inc);
        if (MercyClient.hidesHud()) {
            return;
        }
        long now = KuvaFxDirector.clockMs();
        Bar bar = barOf(boss, now);
        int used = draw(g, boss, bar, event.getY(), now);
        if (used > inc) {
            event.setIncrement(used);
        }
    }

    // ==================== 画 ====================

    /** @return 实际用掉的高度（GUI 像素） */
    private static int draw(GuiGraphics g, LerpingBossEvent boss, Bar bar, int y0, long now) {
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        font = mc.font;
        float gs = (float) win.getGuiScale();
        W = win.getWidth();
        H = win.getHeight();
        lw = Math.max(1, Math.round(gs) / 2);

        AbstractKuva entity = entityOf(bar, now);
        boolean down = entity != null && entity.getMercyState() != MercyState.NONE
                && entity.getMercyState() != MercyState.LEAVING;
        float progress = Math.max(0.0F, Math.min(1.0F, boss.getProgress()));
        updateTrail(bar, progress, now);
        int clues = bar.mine ? ClientLichState.get().clues() : 0;
        int adapt = entity != null ? entity.getAdaptPacked() : 0;
        int fury = entity != null ? entity.getFuryPermille() : 0;
        if (demoReadout) {
            adapt = 2 | (1 << 3) | (3 << 9);
            fury = 4650;
        }

        // 入场进度
        float t = now - bar.bornMs;
        float traceK = FxCurve.easeOutCubic(seg(t, 0, 280));
        float fillA = seg(t, 80, 280);
        float barK = FxCurve.easeOutCubic(seg(t, 140, 440));
        float textA = seg(t, 300, 520);

        // ---- 排版（物理像素，全部取整） ----
        int pn = Math.max(2, Math.round(NAME_CAP * H / 7.0F));
        int ps = Math.max(1, Math.round(SMALL_CAP * H / 7.0F));
        int dh = Math.round(DIA_HH * H), dw = Math.round(DIA_HW * H);
        int gap = Math.round(GAP * H);
        int pad = 2 * lw;
        int chev = Math.round(CHEV * H);
        float bw = Math.round(WIDTH * W);
        float cx = Math.round(W * 0.5F);
        float x0 = cx - Math.round(bw * 0.5F), x1 = x0 + bw;
        float dcx = x0 + dw;
        float top = Math.round(y0 * gs);
        // 名字一行：字顶 ny，大写字母占 7 个字像素，竖直中线 rowMid；血条在它下面空一个 GAP
        float ny = top + Math.round(0.002F * H) + pn;
        float rowMid = ny + 3.5F * pn;
        float bt = ny + 7 * pn + pn + gap + pad, bb = bt + Math.round(BAR_H * H);
        // 菱框中心对准血条中线
        float ay = Math.round((bt + bb) * 0.5F);
        float bx0 = dcx + dw * 0.55F;
        float bx1 = bx0 + (x1 - bx0) * barK;

        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);
        m = g.pose().last().pose();

        // ---- 血条底板、血条 ----
        if (barK > 0.0F) {
            float pcy = (bt + bb) * 0.5F, phh = (bb - bt) * 0.5F + pad;
            HoloShapes.hexagon(POLY, bx0 - chev, bx1 + chev, pcy, phh, chev);
            HoloShapes.softPolygon(g, m, POLY, 6, Math.min(0.01F * H, 0.5F * phh), 0.02F * H,
                    GuiShapes.argb(FxPalette.INK, 0.45F * barK));
            for (int i = 0; i < 6; i++) {
                POLY_A[i] = 0.78F;
            }
            HoloShapes.glass(g, m, POLY, POLY_A, (bx0 + bx1) * 0.5F, pcy, phh, 0.78F,
                    down ? DOWNED_GLASS : HoloShapes.GLASS);
            healthFill(g, bx0, bt, bx1, bb, x1 - bx0, progress, bar.trail, down);

            GuiShapes.begin(g);
            int red = GuiShapes.argb(down ? FxPalette.ASH : FxPalette.KUVA, 0.9F);
            int cyan = GuiShapes.argb(FxPalette.TECH, 0.75F);
            // 底板上沿一道红线，过了尾巴再拖一截渐隐
            float ly = crisp(bt - pad);
            GuiShapes.hairline(g, bx0, ly, bx1, ly, lw, red);
            if (barK >= 1.0F) {
                GuiShapes.hairline(g, bx1, ly, bx1 + Math.round(TAIL * W), ly, lw, red,
                        GuiShapes.argb(FxPalette.KUVA, 0.0F));
            }
            // 十格刻度：每 10% 短、50% 长，展开到哪画到哪
            for (int q = 1; q < 10; q++) {
                float x = crisp(bx0 + (x1 - bx0) * q / 10.0F);
                if (x > bx1) {
                    break;
                }
                float len = Math.round((q == 5 ? TICK_LONG : TICK_SHORT) * H);
                GuiShapes.hairline(g, x, bb + pad, x, bb + pad + len, lw, cyan);
            }
            GuiShapes.end();
        }

        // ---- 等级菱框 ----
        badge(g, bar.rankText, dcx, ay, dw, dh, traceK, fillA, down);

        // ---- 名字、小晶、别人看时的后半句 ----
        if (textA > 0.0F) {
            float nx = dcx + dw + gap;
            float nameW = HoloShapes.hudWidth(font, bar.nameUpper) * pn - pn;
            float pipHw = Math.round(PIP_HW * H), pipStep = Math.round(pipHw * 2.7F);
            float pipsW = 2 * pipHw + 2 * pipStep;
            float px0 = nx + nameW + gap;
            boolean showRest = !bar.mine && !bar.rest.isEmpty();
            int pr = Math.max(1, pn - 1);
            // 别人看时「· 某某的玄骸」也收在同一块底板里，不然浮在天上看不清
            float plateR = px0 + pipsW + gap + (showRest ? font.width(bar.rest) * pr + gap : 0);
            backdrop(g, dcx + dw, ny - pn, plateR, ny + 8 * pn, textA);
            HoloShapes.hudText(g, font, bar.nameUpper, nx, ny, pn, GuiShapes.argb(FxPalette.BONE, 0.95F * textA));
            pips(g, px0, rowMid, pipHw, pipStep, bar.mine ? clues : 0, bar.mine, down, textA);
            if (showRest) {
                // 汉字用原版字体：字形格 0~7 行，中线在 4
                HoloShapes.text(g, font, bar.rest, px0 + pipsW + gap, Math.round(rowMid - 4.0F * pr), pr,
                        GuiShapes.argb(FxPalette.ASH, textA));
            }

            // ---- 交战增幅：右边对齐血条尾巴 ----
            if (fury >= FURY_SHOW_PERMILLE) {
                String s = plusPct(Math.round(fury / 10.0F));
                float fw = HoloShapes.hudWidth(font, s) * pn - pn;
                float fx = x1 - fw;
                backdrop(g, fx - 6 * pn, ny - pn, x1 + gap, ny + 8 * pn, textA);
                furyArrow(g, fx - 3 * pn, rowMid, pn, textA);
                HoloShapes.hudText(g, font, s, fx, ny, pn, GuiShapes.argb(FxPalette.KUVA_PALE, textA));
            }
        }

        // ---- 适应层数：右边对齐血条尾巴，在刻度下面 ----
        float bottom = Math.max(ay + dh, bb + pad + Math.round(TICK_LONG * H));
        if (adapt != 0 && textA > 0.0F) {
            float sy = bottom + Math.round(0.005F * H);
            bottom = adaptRow(g, adapt, x1, sy, ps, textA);
        }
        bottom += Math.round(0.004F * H);
        GuiShapes.flush(g);
        g.pose().popPose();

        // ---- 元素状态：再往下一行（GUI 坐标） ----
        int used = Math.round(bottom / gs - y0) + 1;
        if (entity != null) {
            int rowY = Math.round(bottom / gs) + 1;
            int eh = ElementStatusRow.draw(g, entity, Math.round(cx / gs), rowY, 1.0F);
            if (eh > 0) {
                used = Math.max(used, rowY + eh - y0 + 1);
            }
        }
        return used;
    }

    /** 血条：底、残影、填充、上半截高光、血条头一道亮边；{@code full} 是展开完的长度，展开中按它截 */
    private static void healthFill(GuiGraphics g, float x0, float y0, float x1, float y1, float full, float p,
                                   float trail, boolean down) {
        HoloShapes.softRect(g, m, x0, y0, x1, y1, 0.0F, GuiShapes.argb(FxPalette.KUVA_MIST, 0.85F), false);
        if (trail > p + 1.0E-3F) {
            HoloShapes.softRect(g, m, x0 + full * p, y0, Math.min(x1, x0 + full * Math.min(1.0F, trail)), y1, 0.0F,
                    GuiShapes.argb(FxPalette.KUVA_PALE, 0.6F), false);
        }
        if (p <= 0.0F) {
            return;
        }
        float fx = Math.min(x1, Math.round(x0 + full * p));
        if (down) {
            HoloShapes.softRect(g, m, x0, y0, fx, y1, 0.0F, GuiShapes.argb(FxPalette.ASH, 0.7F), false);
            return;
        }
        HoloShapes.softRect(g, m, x0, y0, fx, y1, 0.0F, GuiShapes.argb(FxPalette.KUVA, 1.0F), false);
        HoloShapes.vgrad(g, m, x0, y0, fx, y1, FxPalette.KUVA_HOT, 0.45F, 0.0F, false);
        HoloShapes.softRect(g, m, fx - lw, y0, fx, y1, 0.0F, GuiShapes.argb(FxPalette.KUVA_PALE, 0.9F), false);
    }

    /** 双线菱框 + 等级数字；{@code traceK} 是外框描到哪，{@code fillA} 是填色和数字的透明度 */
    private static void badge(GuiGraphics g, String rank, float cx, float cy, float dw, float dh, float traceK,
                              float fillA, boolean down) {
        float inset = Math.round(DIA_INSET * H);
        float ihw = dw - inset, ihh = dh - inset * dh / dw;
        if (fillA > 0.0F) {
            HoloShapes.diamondFill(g, m, cx, cy, dw, dh, FxPalette.INK, 0.55F * fillA, 0.55F * fillA, 0.55F * fillA,
                    false);
            HoloShapes.diamondFill(g, m, cx, cy, ihw, ihh, down ? FxPalette.ASH : FxPalette.KUVA_DEEP, 0.85F * fillA,
                    0.7F * fillA, 0.55F * fillA, false);
        }
        GuiShapes.begin(g);
        GuiShapes.diamondTrace(g, cx, cy, dw, dh, lw, GuiShapes.argb(down ? FxPalette.ASH : FxPalette.KUVA, 0.95F),
                traceK);
        if (fillA > 0.0F) {
            outline(g, cx, cy, ihw, ihh, GuiShapes.argb(FxPalette.KUVA_PALE, 0.5F * fillA));
        }
        GuiShapes.end();
        int adv = HoloShapes.hudWidth(font, rank);
        if (adv <= 0 || fillA <= 0.0F) {
            return;
        }
        // 数字取放得进内框的最大整数倍：半宽、半高按内框的菱形斜边量
        int p = Math.max(1, Math.round(0.6F * dh / 4.0F));
        while (p > 1 && ((adv * p - p) * 0.5F + 2.0F) / ihw + (3.5F * p + 2.0F) / ihh > 0.92F) {
            p--;
        }
        float tw = adv * p - p;
        float x = Math.round(cx - tw * 0.5F), y = Math.round(cy - 3.5F * p);
        float glow = 0.006F * H;
        HoloShapes.softRect(g, m, x - glow, y - glow, x + tw + glow, y + 7 * p + glow, 2 * glow,
                GuiShapes.argb(FxPalette.KUVA, down ? 0.0F : 0.25F * fillA), false);
        HoloShapes.hudText(g, font, rank, x, y, p, GuiShapes.argb(NUMBER_WHITE, fillA));
    }

    private static void outline(GuiGraphics g, float cx, float cy, float hw, float hh, int argb) {
        PATH[0] = cx;
        PATH[1] = cy - hh;
        PATH[2] = cx + hw;
        PATH[3] = cy;
        PATH[4] = cx;
        PATH[5] = cy + hh;
        PATH[6] = cx - hw;
        PATH[7] = cy;
        GuiShapes.polyline(g, PATH, 4, true, lw, argb);
    }

    /** 三颗密语小晶，中心在 {@code cy}：揭示了的实心亮、没揭示的空心暗；别人的玄骸、倒地时全暗 */
    private static void pips(GuiGraphics g, float x, float cy, float hw, float step, int clues, boolean mine,
                             boolean down, float a) {
        float hh = Math.round(PIP_HH * H);
        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            float pcx = x + hw + i * step;
            if (i < clues && !down) {
                GuiShapes.diamondFill(g, pcx, cy, hw, hh, GuiShapes.argb(FxPalette.KUVA_PALE, 0.95F * a));
            } else {
                outline(g, pcx, cy, hw, hh, GuiShapes.argb(down || !mine ? FxPalette.ASH : FxPalette.KUVA, 0.6F * a));
            }
        }
        GuiShapes.end();
    }

    /** 增幅前面的上箭头，尖朝上、中心在 ({@code cx}, {@code cy}) */
    private static void furyArrow(GuiGraphics g, float cx, float cy, int p, float a) {
        float hw = 2.0F * p, hh = 1.0F * p;
        PATH[0] = cx - hw;
        PATH[1] = cy + hh;
        PATH[2] = cx;
        PATH[3] = cy - hh;
        PATH[4] = cx + hw;
        PATH[5] = cy + hh;
        GuiShapes.begin(g);
        GuiShapes.polyline(g, PATH, 3, false, Math.max(lw, p / 2), GuiShapes.argb(FxPalette.KUVA, 0.95F * a));
        GuiShapes.end();
    }

    /** 适应层数一行，右边对齐 {@code right}、上沿 {@code y}；图标和数字一样高（7 个字像素），返回下沿 */
    private static float adaptRow(GuiGraphics g, int adapt, float right, float y, int ps, float a) {
        float icon = 7 * ps;
        float inner = 2 * ps, between = 5 * ps;
        float w = 0.0F;
        for (int c = 0; c < 5; c++) {
            int n = (adapt >> (c * 3)) & 7;
            if (n > 0) {
                w += icon + inner + (HoloShapes.hudWidth(font, NUM[n]) * ps - ps) + between;
            }
        }
        if (w <= 0.0F) {
            return y;
        }
        w -= between;
        float x = right - w;
        backdrop(g, x - 3 * ps, y - 2 * ps, right + 3 * ps, y + icon + 2 * ps, a);
        int col = GuiShapes.argb(FxPalette.BONE, 0.9F * a);
        float xx = x;
        GuiShapes.begin(g);
        for (int c = 0; c < 5; c++) {
            int n = (adapt >> (c * 3)) & 7;
            if (n > 0) {
                drawCategoryIcon(g, c, xx + icon * 0.5F, y + icon * 0.5F, icon, lw, col);
                xx += icon + inner + (HoloShapes.hudWidth(font, NUM[n]) * ps - ps) + between;
            }
        }
        GuiShapes.end();
        xx = x;
        for (int c = 0; c < 5; c++) {
            int n = (adapt >> (c * 3)) & 7;
            if (n > 0) {
                HoloShapes.hudText(g, font, NUM[n], xx + icon + inner, y, ps, col);
                xx += icon + inner + (HoloShapes.hudWidth(font, NUM[n]) * ps - ps) + between;
            }
        }
        return y + icon + 2 * ps;
    }

    /** 浮在画面上的字底下垫一块软边暗红玻璃，亮天空下也读得清 */
    private static void backdrop(GuiGraphics g, float x0, float y0, float x1, float y1, float a) {
        float e = 0.010F * H;
        HoloShapes.softRect(g, m, x0 + e * 0.5F, y0, x1 - e * 0.5F, y1, e,
                GuiShapes.argb(HoloShapes.GLASS, 0.55F * a), false);
    }

    /**
     * 伤害类别的小线描：近战 = 一把剑、弓箭 = 一支箭、其他弹射物 = 带尾迹的小球、魔法 = 四角星、其他 = 空心小菱
     *
     * @param size 图标边长
     */
    static void drawCategoryIcon(GuiGraphics g, int category, float cx, float cy, float size, float lw, int argb) {
        float r = size * 0.5F;
        switch (category) {
            case 0 -> {
                GuiShapes.hairline(g, cx - r * 0.75F, cy + r * 0.75F, cx + r * 0.8F, cy - r * 0.8F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.75F, cy + r * 0.05F, cx - r * 0.05F, cy + r * 0.75F, lw, argb);
            }
            case 1 -> {
                GuiShapes.hairline(g, cx - r * 0.8F, cy + r * 0.8F, cx + r * 0.8F, cy - r * 0.8F, lw, argb);
                GuiShapes.hairline(g, cx + r * 0.8F, cy - r * 0.8F, cx + r * 0.15F, cy - r * 0.65F, lw, argb);
                GuiShapes.hairline(g, cx + r * 0.8F, cy - r * 0.8F, cx + r * 0.65F, cy - r * 0.15F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.8F, cy + r * 0.8F, cx - r * 0.8F, cy + r * 0.3F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.8F, cy + r * 0.8F, cx - r * 0.3F, cy + r * 0.8F, lw, argb);
            }
            case 2 -> {
                GuiShapes.ring(g, cx + r * 0.35F, cy - r * 0.35F, r * 0.42F, lw, argb, 1.0F);
                GuiShapes.hairline(g, cx - r * 0.85F, cy + r * 0.35F, cx - r * 0.1F, cy - r * 0.15F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.35F, cy + r * 0.85F, cx + r * 0.15F, cy + r * 0.1F, lw, argb);
            }
            case 3 -> {
                GuiShapes.hairline(g, cx, cy - r * 0.9F, cx, cy + r * 0.9F, lw, argb);
                GuiShapes.hairline(g, cx - r * 0.9F, cy, cx + r * 0.9F, cy, lw, argb);
                GuiShapes.diamondFill(g, cx, cy, r * 0.32F, r * 0.32F, argb);
            }
            default -> GuiShapes.diamondOutline(g, cx, cy, r * 0.5F, r * 0.75F, lw, argb);
        }
    }

    private static String plusPct(int n) {
        int i = Math.max(0, Math.min(PLUS_PCT.length - 1, n));
        String s = PLUS_PCT[i];
        if (s == null) {
            s = "+" + i + "%";
            PLUS_PCT[i] = s;
        }
        return s;
    }

    private static float seg(float t, float a, float b) {
        return FxCurve.clamp01((t - a) / (b - a));
    }

    /** 线宽是奇数个像素时落到像素中线上，偶数时落到像素边上，横线不糊成两行 */
    private static float crisp(float v) {
        return (lw & 1) == 1 ? (float) Math.floor(v) + 0.5F : Math.round(v);
    }

    // ==================== 状态 ====================

    private static Bar barOf(LerpingBossEvent boss, long now) {
        Bar bar = BARS.get(boss.getId());
        if (bar == null || now - bar.lastSeenMs > FORGET_MS) {
            // 第一次出现，或者隔了一阵（走远了、按 F1 藏了界面）又出现：重放入场
            bar = new Bar();
            bar.bornMs = now;
            BARS.put(boss.getId(), bar);
        }
        bar.lastSeenMs = now;
        Component name = boss.getName();
        Minecraft mc = Minecraft.getInstance();
        String lang = mc.options.languageCode;
        if (name != bar.lastName || !lang.equals(bar.restLang) || bar.otherView != ArchiveDevCommands.forceOtherView) {
            parse(bar, name, lang);
        }
        if (now - lastPruneMs > 1000L) {
            lastPruneMs = now;
            Iterator<Map.Entry<UUID, Bar>> it = BARS.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue().lastSeenMs > FORGET_MS) {
                    it.remove();
                }
            }
        }
        return bar;
    }

    /** 名字变了（服务端每 20 tick 重设一次，内容一样也是新对象）或者换了语言才重新解析 */
    private static void parse(Bar bar, Component name, String lang) {
        bar.lastName = name;
        bar.otherView = ArchiveDevCommands.forceOtherView;
        bar.restLang = lang;
        Object[] args = name.getContents() instanceof TranslatableContents tc ? tc.getArgs() : new Object[0];
        String upper = args.length > 0 ? argString(args[0]) : name.getString();
        String rank = args.length > 1 ? argString(args[1]) : "";
        String ownerName = args.length > 2 ? argString(args[2]) : "";
        UUID owner = null;
        if (args.length > 3) {
            try {
                owner = UUID.fromString(argString(args[3]));
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
        if (!upper.equals(bar.nameUpper) || !ownerName.equals(bar.ownerName) || bar.rest.isEmpty()) {
            String full = Component.translatable("kuvalich.nameplate.lich.other", upper, ownerName).getString();
            int at = full.indexOf(upper);
            bar.rest = at >= 0 ? full.substring(at + upper.length()) : " · " + full;
        }
        bar.nameUpper = upper;
        bar.rankText = rank;
        bar.ownerName = ownerName;
        if (owner == null || !owner.equals(bar.owner)) {
            bar.entityId = -1;
            bar.nextScanMs = 0L;
        }
        bar.owner = owner;
        Minecraft mc = Minecraft.getInstance();
        bar.mine = !ArchiveDevCommands.forceOtherView
                && (owner == null || (mc.player != null && owner.equals(mc.player.getUUID())));
    }

    private static String argString(Object arg) {
        if (arg instanceof Component c) {
            return c.getString();
        }
        return String.valueOf(arg);
    }

    /** 世界里对应的那只玄骸（按主人 UUID 找），离得太远没加载时为 null */
    @Nullable
    private static AbstractKuva entityOf(Bar bar, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || bar.owner == null) {
            return null;
        }
        if (bar.entityId >= 0) {
            Entity e = mc.level.getEntity(bar.entityId);
            if (e instanceof KuvaMasterEntity k && !k.isRemoved() && bar.owner.equals(k.getOwnerUUID())) {
                return k;
            }
            bar.entityId = -1;
        }
        if (now < bar.nextScanMs) {
            return null;
        }
        bar.nextScanMs = now + RESCAN_MS;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof KuvaMasterEntity k && e.getId() >= 0 && bar.owner.equals(k.getOwnerUUID())) {
                bar.entityId = e.getId();
                return k;
            }
        }
        return null;
    }

    private static void updateTrail(Bar bar, float progress, long now) {
        if (bar.trail < 0.0F || progress >= bar.trail) {
            bar.trail = progress;
            bar.lastProgress = progress;
            bar.holdUntilMs = 0L;
            return;
        }
        if (progress < bar.lastProgress - 1.0E-4F) {
            // 又掉了一截：残影停在原处，重新等 250 ms
            bar.holdUntilMs = now + TRAIL_HOLD_MS;
            bar.chaseFrom = bar.trail;
        }
        bar.lastProgress = progress;
        if (now >= bar.holdUntilMs) {
            float t = FxCurve.clamp01((now - bar.holdUntilMs) / (float) TRAIL_CHASE_MS);
            bar.trail = bar.chaseFrom + (progress - bar.chaseFrom) * FxCurve.easeOutCubic(t);
            if (t >= 1.0F) {
                bar.trail = progress;
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BARS.clear();
    }
}
