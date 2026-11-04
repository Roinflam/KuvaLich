package pers.roinflam.kuvalich.client.lich;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.BufferBuilder;
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
 * 专属玄骸的 Boss 条，和升级横幅一套画法（细线框、尖头玻璃板、双线菱框装等级）：
 * 左头一颗大菱框装等级，右边拉出分开的三段血条，正下方一条更细的科技青护盾线；血条上方左边是名字和三颗密语小晶，
 * 右边是交战增幅；底板下面靠右一行适应层数，再往下是元素状态
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
 * <p>三段对应三次处决：血按整条的比例从右往左掉，每段有自己的暗底、填充和残影，打空的段只剩暗底。
 * 长度从菱框右尖量到血条尾巴，扣掉两道空隙三等分，除不尽的 1~2 像素给第 0 段（它左头本来就插在菱框底下）。
 * 护盾线和血条一样从菱框底下伸到尾巴，按护盾 / 上限缩短，空的部分不画；涨回来时从左往右匀速长
 * （服务端一秒回一跳，每跳摊开到一秒里），掉了直接缩。底板往下包住护盾线，尖头斜度不变。</p>
 *
 * <p>入场：这条第一次出现（或者 5 秒没画过又出现）时，0–280 ms 菱框从上顶点描一圈，80 起菱框里填色、数字淡入，
 * 140–440 血条、护盾线和底板从菱框往右展开，300–520 字和小晶淡入。掉血后一段淡色残影停 250 ms 再追上。
 * 倒地（被处决时也算）：底板、菱框和满着的段变灰，正在回满的那段换成暗赤毒红、前头一道骨白，跟着倒地计时从空涨到满；
 * 起身时血已经回到段顶，照常画。别人的玄骸（客户端比对第 4 个参数和本地玩家 UUID）名字后面接「· 某某的玄骸」，小晶全暗。
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
    /** 三段之间的空隙（至少两条线宽） */
    private static final float SEG_GAP = 0.005F;
    /** 护盾线和血条之间的缝、护盾线的粗细（都至少一条线宽） */
    private static final float SHIELD_GAP = 0.0025F;
    private static final float SHIELD_H = 0.0035F;
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

    private static final int SEGMENTS = KuvaMasterEntity.SEGMENTS;
    /** 一段装了多满，离 0、1 这么近就当 0、1（血正好压在段边上时浮点会差一丁点，别在空段上画出亮边） */
    private static final float FILL_EPS = 0.002F;
    /** 护盾涨一跳摊开的时长：服务端一秒回一跳，连起来就是匀速；一下回满的也按这个从左往右长 */
    private static final int SHIELD_GROW_MS = 1000;

    /** 每一条 Boss 条自己的状态：解析好的参数、对应实体、残影、护盾显示值、入场时刻 */
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

        /** 护盾线现在画多长（比例），小于 0 是还没有值 */
        float shieldShown = -1.0F;
        float shieldFrom;
        float shieldTo;
        long shieldAtMs;
    }

    /** 开发用：增幅、适应层数换成演示值（+465%，近战 2 / 弓箭 1 / 魔法 3），截图对齐用 */
    static boolean demoReadout;
    /** 开发用：生命按这个比例画（0~1），小于 0 不替换 */
    static float demoHealth = -1.0F;
    /** 开发用：护盾按这个比例画（0~1），小于 0 不替换 */
    static float demoShield = -1.0F;
    /** 开发用：按倒地画，打空了几段（0~2）；小于 0 不演 */
    private static int demoDownPhase = -1;
    private static float demoDownFrom;
    /** 回满一整段要多少毫秒，0 = 停在起点不动 */
    private static int demoDownMs;
    private static long demoDownAtMs;

    private static final Map<UUID, Bar> BARS = new HashMap<>();
    private static long lastPruneMs;

    /** 0~7 的层数字串，免得每帧拼字串 */
    private static final String[] NUM = new String[8];
    private static final String[] PLUS_PCT = new String[1000];
    private static final float[] POLY = new float[12];
    private static final float[] POLY_A = new float[6];
    private static final float[] PATH = new float[8];
    /** 这一帧三段各自的长度区间（第 0 段左头往菱框底下伸的那截不算） */
    private static final float[] SX0 = new float[SEGMENTS];
    private static final float[] SX1 = new float[SEGMENTS];

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

        KuvaMasterEntity entity = entityOf(bar, now);
        MercyState state = entity != null ? entity.getMercyState() : MercyState.NONE;
        // 倒地、被处决时按倒地画；起身时血已经回到段顶，照常画
        boolean down = state == MercyState.DOWNED || state == MercyState.EXECUTING;
        int phase = entity != null ? entity.getLichPhase() : 0;
        float progress = Math.max(0.0F, Math.min(1.0F, boss.getProgress()));
        float shieldTarget = entity != null && entity.getShieldMax() > 0.0F
                ? FxCurve.clamp01(entity.getShield() / entity.getShieldMax()) : -1.0F;
        if (demoHealth >= 0.0F) {
            progress = demoHealth;
        }
        if (demoShield >= 0.0F) {
            shieldTarget = demoShield;
        }
        if (demoDownPhase >= 0) {
            int seg = SEGMENTS - 1 - demoDownPhase;
            float r = demoDownFrom + (demoDownMs > 0 ? (now - demoDownAtMs) / (float) demoDownMs : 0.0F);
            if (r >= 1.0F && demoDownMs > 0) {
                // 回满了就站起来：这段满着回到常态
                demoDownPhase = -1;
                demoHealth = (seg + 1) / (float) SEGMENTS;
                progress = demoHealth;
            } else {
                down = true;
                phase = demoDownPhase;
                progress = (seg + FxCurve.clamp01(r)) / SEGMENTS;
            }
        }
        updateTrail(bar, progress, now);
        float shield = updateShield(bar, shieldTarget, now);
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
        // 血条左头插在菱框底下
        float bx0 = Math.round(dcx + dw * 0.55F);
        float bx1 = Math.round(bx0 + (x1 - bx0) * barK);
        // 三段：从菱框右尖量到血条尾巴，扣掉两道空隙三等分，零头给第 0 段
        float vL = dcx + dw;
        int sg = Math.max(2 * lw, Math.round(SEG_GAP * H));
        int span = Math.round(x1 - vL) - 2 * sg;
        int sw = span / SEGMENTS;
        float sx = vL;
        for (int i = 0; i < SEGMENTS; i++) {
            SX0[i] = sx;
            sx += i == 0 ? span - sw * (SEGMENTS - 1) : sw;
            SX1[i] = sx;
            sx += sg;
        }
        // 护盾线：血条正下方，同样从菱框底下伸到尾巴；长度从它在菱框斜边上露头的地方量起
        float st = bb + Math.max(lw, Math.round(SHIELD_GAP * H));
        float sb = st + Math.max(lw, Math.round(SHIELD_H * H));
        float sl = Math.round(dcx + dw * (1.0F - ((st + sb) * 0.5F - ay) / dh));
        // 底板包住血条和护盾线；左下角收进菱框斜边里至少一条线宽，不从菱框底下露出来
        float pt = bt - pad, pb = sb + pad;
        float plateL = Math.min(bx0, (float) Math.floor(dcx + dw * (1.0F - (pb - ay) / dh)) - lw);

        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);
        m = g.pose().last().pose();

        // ---- 血条底板、三段血条、护盾线 ----
        if (barK > 0.0F) {
            float pcy = (pt + pb) * 0.5F, phh = (pb - pt) * 0.5F;
            // 尖头斜度和只包血条时一样
            float ch = Math.round(chev * phh / ((bb - bt) * 0.5F + pad));
            HoloShapes.hexagon(POLY, plateL - ch, bx1 + ch, pcy, phh, ch);
            HoloShapes.softPolygon(g, m, POLY, 6, Math.min(0.01F * H, 0.5F * phh), 0.02F * H,
                    GuiShapes.argb(FxPalette.INK, 0.45F * barK));
            for (int i = 0; i < 6; i++) {
                POLY_A[i] = 0.78F;
            }
            HoloShapes.glass(g, m, POLY, POLY_A, (plateL + bx1) * 0.5F, pcy, phh, 0.78F,
                    down ? DOWNED_GLASS : HoloShapes.GLASS);
            bars(g, bx0, bx1, bt, bb, progress, bar.trail, down, SEGMENTS - 1 - phase, shield, sl, x1, st, sb);

            GuiShapes.begin(g);
            int red = GuiShapes.argb(down ? FxPalette.ASH : FxPalette.KUVA, 0.9F);
            // 底板上沿一道红线，过了尾巴再拖一截渐隐
            float ly = crisp(pt);
            GuiShapes.hairline(g, bx0, ly, bx1, ly, lw, red);
            if (barK >= 1.0F) {
                GuiShapes.hairline(g, bx1, ly, bx1 + Math.round(TAIL * W), ly, lw, red,
                        GuiShapes.argb(FxPalette.KUVA, 0.0F));
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

        // ---- 适应层数：右边对齐血条尾巴，在菱框和底板下面 ----
        float bottom = Math.max(ay + dh, pb);
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

    /**
     * 三段血条和护盾线，一批提交。段 i 的长度区间是 {@link #SX0}~{@link #SX1}；第 0 段和护盾线左头都往左伸进菱框底下
     * （到 {@code left}），那截不算长度、有就涂满。每段：暗底、残影、填充、上半截高光，最前面那段的填充头一道亮边
     *
     * @param clip   入场展开到的位置
     * @param p      血占整条的比例
     * @param refill 倒地时正在回满的那段
     * @param shield 护盾比例，小于 0 不画（实体没加载、没有护盾）
     * @param sl     护盾线长度的起点（在菱框斜边上露头的地方），满了到 {@code right}
     */
    private static void bars(GuiGraphics g, float left, float clip, float bt, float bb, float p, float trail,
                             boolean down, int refill, float shield, float sl, float right, float st, float sb) {
        if (clip <= left) {
            return;
        }
        int front = -1;
        for (int i = 0; i < SEGMENTS; i++) {
            if (segFill(p, i) > 0.0F) {
                front = i;
            }
        }
        BufferBuilder buf = HoloShapes.open(g, false);
        for (int i = 0; i < SEGMENTS; i++) {
            float a = i == 0 ? left : SX0[i];
            float r = Math.min(SX1[i], clip);
            if (r <= a) {
                break;
            }
            rect(buf, a, bt, r, bb, FxPalette.KUVA_MIST, 0.85F, 0.85F);
            float f = segFill(p, i), tr = segFill(trail, i);
            float fx = f > 0.0F ? Math.min(r, segX(i, f)) : a;
            if (tr > f) {
                rect(buf, fx, bt, Math.min(r, segX(i, tr)), bb, FxPalette.KUVA_PALE, 0.6F, 0.6F);
            }
            if (down && i == refill) {
                // 倒地回血的那段：暗红跟着倒地计时涨回去，前头一道骨白（空着时贴在段头），当倒计时看
                rect(buf, a, bt, fx, bb, FxPalette.KUVA_DEEP, 1.0F, 1.0F);
                head(buf, i, f, r, bt, bb, FxPalette.BONE, 0.9F);
            } else if (down) {
                rect(buf, a, bt, fx, bb, FxPalette.ASH, 0.7F, 0.7F);
            } else if (f > 0.0F) {
                rect(buf, a, bt, fx, bb, FxPalette.KUVA, 1.0F, 1.0F);
                rect(buf, a, bt, fx, bb, FxPalette.KUVA_HOT, 0.45F, 0.0F);
                if (i == front) {
                    head(buf, i, f, r, bt, bb, FxPalette.KUVA_PALE, 0.9F);
                }
            }
        }
        if (shield > 0.0F) {
            float sx = Math.min(clip, Math.round(sl + (right - sl) * shield));
            if (down) {
                rect(buf, left, st, sx, sb, FxPalette.ASH, 0.7F, 0.7F);
            } else {
                rect(buf, left, st, sx, sb, FxPalette.TECH, 0.9F, 0.9F);
                rect(buf, Math.max(left, sx - lw), st, sx, sb, FxPalette.TECH_PALE, 0.95F, 0.95F);
            }
        }
        HoloShapes.close(buf);
    }

    /** 第 i 段装了多满（0~1）；{@code p} 是整条的比例 */
    private static float segFill(float p, int i) {
        float f = p * SEGMENTS - i;
        return f < FILL_EPS ? 0.0F : f > 1.0F - FILL_EPS ? 1.0F : f;
    }

    /** 第 i 段装到 {@code f} 时填充的右边，取整到像素 */
    private static float segX(int i, float f) {
        return Math.round(SX0[i] + (SX1[i] - SX0[i]) * f);
    }

    /** 填充头一道一条线宽的亮边，贴在填充里面；空段贴在段头 */
    private static void head(BufferBuilder buf, int i, float f, float clip, float y0, float y1, int rgb, float a) {
        float hx1 = Math.min(clip, Math.max(segX(i, f), SX0[i] + lw));
        rect(buf, hx1 - lw, y0, hx1, y1, rgb, a, a);
    }

    /** 轴对齐矩形（坐标已取整），上沿透明度 {@code aTop}、下沿 {@code aBottom} */
    private static void rect(BufferBuilder buf, float x0, float y0, float x1, float y1, int rgb, float aTop,
                             float aBottom) {
        if (x1 > x0 && y1 > y0) {
            HoloShapes.quad(buf, m, x0, y0, x1, y1, rgb, aTop, aTop, aBottom, aBottom);
        }
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
    private static KuvaMasterEntity entityOf(Bar bar, long now) {
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

    /**
     * 护盾线画多长：掉了直接缩；涨了从现在的长度匀速长到新值，用 {@link #SHIELD_GROW_MS}。
     * {@code target} 小于 0（没有护盾、实体没加载）时不画，下次有值直接取那个值
     */
    private static float updateShield(Bar bar, float target, long now) {
        if (target < 0.0F) {
            bar.shieldShown = -1.0F;
            return -1.0F;
        }
        if (bar.shieldShown < 0.0F || target < bar.shieldShown) {
            bar.shieldShown = target;
            bar.shieldFrom = target;
            bar.shieldTo = target;
            return target;
        }
        if (Math.abs(target - bar.shieldTo) > 1.0E-4F) {
            bar.shieldFrom = bar.shieldShown;
            bar.shieldTo = target;
            bar.shieldAtMs = now;
        }
        float k = FxCurve.clamp01((now - bar.shieldAtMs) / (float) SHIELD_GROW_MS);
        bar.shieldShown = bar.shieldFrom + (bar.shieldTo - bar.shieldFrom) * k;
        return bar.shieldShown;
    }

    // ==================== 开发用演示 ====================

    /** 生命、护盾、倒地的演示值全部清掉 */
    static void clearDemo() {
        demoHealth = -1.0F;
        demoShield = -1.0F;
        demoDownPhase = -1;
    }

    /**
     * 按倒地画：打空了 {@code phase} 段，正在回满的那段从 {@code from}（0~1）起、按 {@code seconds} 秒回满一整段的速度涨，
     * 满了站起来（恢复常态，生命停在这段顶上）；{@code seconds} 为 0 时停在起点不动
     */
    static void demoDown(int phase, float seconds, float from) {
        demoDownPhase = Math.max(0, Math.min(SEGMENTS - 1, phase));
        demoDownFrom = FxCurve.clamp01(from);
        demoDownMs = Math.round(Math.max(0.0F, seconds) * 1000.0F);
        demoDownAtMs = KuvaFxDirector.clockMs();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BARS.clear();
    }
}
