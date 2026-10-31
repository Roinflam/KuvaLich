package pers.roinflam.kuvalich.client.fx.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;

/**
 * 工作台界面里的小特效：屏幕像素坐标的粒子表，外加各种工作台操作的分镜和声音
 *
 * <p>只给安魂之融、安魂之铸、武器军械库、战甲军械库四个界面用。每个界面持有一份，
 * 在 {@code render} 里 {@code super.render} 之后、{@code renderTooltip} 之前调 {@link #render}。</p>
 *
 * <p>几条规矩：</p>
 * <ul>
 *   <li>元素存在定长数组里（{@link #CAPACITY} 个），满了新的直接丢；{@link #render} 不分配对象，
 *       没有元素时什么都不做——界面静止时没有任何常驻动画；</li>
 *   <li>坐标一律相对界面左上角（{@code leftPos, topPos}），窗口缩放后正在播的特效也不会错位；</li>
 *   <li>全部是像素对齐的矩形，和界面贴图同一种像素风，斜线、六边形都用阶梯拼；</li>
 *   <li>层次放在 z = 300，盖过格子里的物品图标、低于提示框（400）；手上拿着物品时降到 200，
 *       不压到鼠标上那件东西；</li>
 *   <li>时间用 {@link Util#getMillis()}：声音按实时播放，画面的爆发点要和声音的爆发点对上，
 *       不能跟着游戏暂停或低帧率时被截断的帧间隔走。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class GuiFx {

    /** 一个界面同时最多这么多个元素 */
    public static final int CAPACITY = 80;

    private static final float Z_OVER_ITEMS = 300.0F;
    private static final float Z_UNDER_CARRIED = 200.0F;

    // ==================== 颜色（0xRRGGBB，只在工作台界面用） ====================

    /** 模组稀有度：铜 / 银 / 金 / 白金（Prime）/ 紫卡 */
    private static final int[] RARITY = {0xC08A5A, 0xC8D2DA, 0xFFC861, 0xE8ECFF, 0xC864FF};
    /** 紫卡、裂罅碎块 */
    private static final int RIVEN = 0xC864FF;
    /** 故障色差：品红 / 青，各错 1 px */
    private static final int GLITCH_MAGENTA = 0xFF4FD8;
    private static final int GLITCH_CYAN = 0x4FE8FF;
    /** 暗影塑形块的六边形（Ordis 淡紫） */
    private static final int ORDIS = 0xB9A0FF;
    /** 暗影塑形块先压暗的那一下 */
    private static final int SHADOW_DARK = 0x2A1440;
    /** 塑形块掷中锁定时夹住格子的横杠 */
    private static final int LOCK_RED = 0xFF7B7B;
    /** 内融核心的光点 */
    private static final int ENDO_MOTE = 0x9FE8FF;

    // ==================== 元素类型 ====================

    private static final byte SPARK = 1;
    private static final byte MOTE = 2;
    private static final byte SHARD = 3;
    private static final byte RING = 4;
    private static final byte HEX = 5;
    private static final byte BRACKETS = 6;
    private static final byte SCAN = 7;
    private static final byte FLASH = 8;
    private static final byte TETHER = 9;
    private static final byte FRAME = 10;
    private static final byte BARS = 11;
    private static final byte LABEL = 12;

    /** 描边的透明度走法 */
    private static final int FRAME_ENVELOPE = 0;
    private static final int FRAME_STEPS = 1;
    private static final int FRAME_STEADY = 2;

    /** 取出模组时描边按 38 ms 一格熄灭 */
    private static final float[] REMOVE_STEPS = {0.8F, 0.0F, 0.5F, 0.0F};
    private static final int STEP_MS = FxCurve.FLICKER_STEP_MS;
    /** 故障横跳一跳的长度 */
    private static final int JUMP_MS = 38;

    /** 模组升级音高表：新等级 1~10（十级以上按十级）对应的音区文件和播放音高 */
    private static final int[] RANK_GROUP = {0, 0, 0, 1, 1, 2, 2, 2, 3, 3};
    private static final float[] RANK_PITCH = {0.891F, 1.0F, 1.122F, 0.944F, 1.059F, 0.891F, 1.0F, 1.122F, 0.944F, 1.059F};
    /** 装入模组的音高：铜、银、金、Prime 逐档升一点，紫卡回到 1.0（紫卡靠画面上的故障区分） */
    private static final float[] INSTALL_PITCH = {1.0F, 1.03F, 1.06F, 1.09F, 1.0F};
    /** 三合一的音高按产出品质 */
    private static final float[] MERGE_PITCH = {0.94F, 1.0F, 1.06F, 1.12F, 1.0F};

    /** 「+N」的字符串先备好，画的时候不拼字符串 */
    private static final String[] PLUS = new String[101];

    static {
        for (int i = 0; i < PLUS.length; i++) {
            PLUS[i] = "+" + i;
        }
    }

    // ==================== 元素表 ====================

    private final byte[] kind = new byte[CAPACITY];
    private final long[] start = new long[CAPACITY];
    private final int[] dur = new int[CAPACITY];
    private final float[] ax = new float[CAPACITY];
    private final float[] ay = new float[CAPACITY];
    private final float[] bx = new float[CAPACITY];
    private final float[] by = new float[CAPACITY];
    private final float[] qx = new float[CAPACITY];
    private final float[] qy = new float[CAPACITY];
    private final float[] r0 = new float[CAPACITY];
    private final float[] r1 = new float[CAPACITY];
    private final float[] alpha = new float[CAPACITY];
    private final int[] col0 = new int[CAPACITY];
    private final int[] col1 = new int[CAPACITY];
    private final int[] arg = new int[CAPACITY];
    private final int[] arg2 = new int[CAPACITY];
    private int count;

    /** 折线路径（相对界面左上角的像素坐标，x0, y0, x1, y1, …），界面构造时登记一次 */
    private static final int MAX_PATHS = 12;
    private final int[][] paths = new int[MAX_PATHS][];
    private final int[] pathLen = new int[MAX_PATHS];
    private int pathCount;

    private final RandomSource random = RandomSource.create();

    // 画的时候用的临时状态
    private final Vector3f tmp = new Vector3f();
    private VertexConsumer buffer;
    private Matrix4f matrix;

    // ==================== 画 ====================

    /**
     * 画全部元素
     *
     * @param originX  界面左上角 x（{@code leftPos}）
     * @param originY  界面左上角 y（{@code topPos}）
     * @param carrying 鼠标上拿着物品
     */
    public void render(GuiGraphics g, int originX, int originY, boolean carrying) {
        if (count == 0) {
            return;
        }
        long now = Util.getMillis();
        compact(now);
        if (count == 0) {
            return;
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(originX, originY, carrying ? Z_UNDER_CARRIED : Z_OVER_ITEMS);
        matrix = pose.last().pose();
        buffer = g.bufferSource().getBuffer(RenderType.gui());
        boolean labels = false;
        for (int i = 0; i < count; i++) {
            long el = now - start[i];
            if (el < 0L) {
                continue;
            }
            int ms = (int) el;
            float t = Math.min(1.0F, ms / (float) dur[i]);
            switch (kind[i]) {
                case SPARK -> drawSpark(i, ms, t);
                case MOTE -> drawMote(i, t);
                case SHARD -> drawShard(i, ms, t);
                case RING -> drawRing(i, t);
                case HEX -> drawHex(i, t);
                case BRACKETS -> drawBrackets(i, ms);
                case SCAN -> drawScan(i, ms, t);
                case FLASH -> drawFlash(i, t);
                case TETHER -> drawTether(i, ms);
                case FRAME -> drawFrame(i, ms);
                case BARS -> drawBars(i, ms);
                case LABEL -> labels = true;
                default -> {
                }
            }
        }
        buffer = null;
        // 一次画完，状态（混合、深度）由 RenderType 自己设好再还原
        g.flush();
        if (labels) {
            drawLabels(g, now);
        }
        matrix = null;
        pose.popPose();
    }

    /** 去掉播完的元素，保持先后顺序（先加的先画，闪光垫在下面） */
    private void compact(long now) {
        int w = 0;
        for (int r = 0; r < count; r++) {
            if (now >= start[r] + dur[r]) {
                continue;
            }
            if (w != r) {
                copy(r, w);
            }
            w++;
        }
        count = w;
    }

    private void copy(int from, int to) {
        kind[to] = kind[from];
        start[to] = start[from];
        dur[to] = dur[from];
        ax[to] = ax[from];
        ay[to] = ay[from];
        bx[to] = bx[from];
        by[to] = by[from];
        qx[to] = qx[from];
        qy[to] = qy[from];
        r0[to] = r0[from];
        r1[to] = r1[from];
        alpha[to] = alpha[from];
        col0[to] = col0[from];
        col1[to] = col1[from];
        arg[to] = arg[from];
        arg2[to] = arg2[from];
    }

    // ---------- 各类元素 ----------

    /** 火花：1 × 3 px 的线，沿速度方向，带一点阻尼；前半程保持亮度、后半程按 1 - t² 收掉（只有一个像素宽，退得太早就看不见了） */
    private void drawSpark(int i, int ms, float t) {
        float s = ms / 1000.0F;
        float k = s * (1.0F - 0.35F * t);
        int hx = Mth.floor(ax[i] + qx[i] * k);
        int hy = Mth.floor(ay[i] + qy[i] * k);
        int dx = (arg[i] & 3) - 1;
        int dy = ((arg[i] >> 2) & 3) - 1;
        float a = alpha[i] * (1.0F - t * t);
        int c = col0[i];
        px(hx, hy, c, a);
        px(hx - dx, hy - dy, c, a * 0.7F);
        px(hx - 2 * dx, hy - 2 * dy, c, a * 0.4F);
    }

    /** 光点：2 × 2，沿二次贝塞尔从 a 经 q 飞到 b，颜色 col0 → col1，身后拖一个暗一点的像素 */
    private void drawMote(int i, float t) {
        float u = ease(arg2[i], t);
        float a = alpha[i] * (t < 0.12F ? t / 0.12F : 1.0F);
        int c = FxCurve.lerpColor(col0[i], col1[i], u);
        float tu = Math.max(0.0F, u - 0.08F);
        int tx = Mth.floor(bezier(ax[i], qx[i], bx[i], tu));
        int ty = Mth.floor(bezier(ay[i], qy[i], by[i], tu));
        px(tx, ty, col0[i], a * 0.45F);
        int x = Mth.floor(bezier(ax[i], qx[i], bx[i], u)) - 1;
        int y = Mth.floor(bezier(ay[i], qy[i], by[i], u)) - 1;
        rect(x, y, x + 2, y + 2, c, a);
    }

    /** 碎片：3 px 的小三角，抛出去受重力下落，每 60 ms 转一下 */
    private void drawShard(int i, int ms, float t) {
        float s = ms / 1000.0F;
        int x = Mth.floor(ax[i] + qx[i] * s);
        int y = Mth.floor(ay[i] + qy[i] * s + 130.0F * s * s);
        float a = alpha[i] * (1.0F - t);
        int c = col0[i];
        switch ((ms / 60 + arg[i]) & 3) {
            case 0 -> {
                px(x, y, c, a);
                px(x + 1, y, c, a);
                px(x, y + 1, c, a);
            }
            case 1 -> {
                px(x, y, c, a);
                px(x + 1, y, c, a);
                px(x + 1, y + 1, c, a);
            }
            case 2 -> {
                px(x + 1, y, c, a);
                px(x, y + 1, c, a);
                px(x + 1, y + 1, c, a);
            }
            default -> {
                px(x, y, c, a);
                px(x, y + 1, c, a);
                px(x + 1, y + 1, c, a);
            }
        }
    }

    /** 斜切方框外扩：半边长 r0 → r1（easeOutCubic），透明度线性退到 0 */
    private void drawRing(int i, float t) {
        float h = r0[i] + (r1[i] - r0[i]) * FxCurve.easeOutCubic(t);
        bevelSquare(Math.round(ax[i]), Math.round(ay[i]), Math.round(h), col0[i], alpha[i] * (1.0F - t));
    }

    /** 六边形描边外扩；arg = 1 时左右各叠一份品红 / 青的色差副本 */
    private void drawHex(int i, float t) {
        float rad = r0[i] + (r1[i] - r0[i]) * FxCurve.easeOutCubic(t);
        float a = alpha[i] * (1.0F - t);
        int cx = Math.round(ax[i]);
        int cy = Math.round(ay[i]);
        if (arg[i] == 1) {
            hexOutline(cx - 1, cy, rad, GLITCH_MAGENTA, a * 0.5F);
            hexOutline(cx + 1, cy, rad, GLITCH_CYAN, a * 0.5F);
        }
        hexOutline(cx, cy, rad, col0[i], a);
    }

    /** 四个 L 角从外面收到格框上（arg2 毫秒内收拢），之后在剩下的时间里淡出；arg = 1 收拢时带回弹 */
    private void drawBrackets(int i, int ms) {
        int conv = arg2[i];
        float h;
        float a;
        if (ms < conv) {
            float u = ms / (float) conv;
            u = arg[i] == 1 ? clampBack(u) : FxCurve.easeOutCubic(u);
            h = r0[i] + (r1[i] - r0[i]) * u;
            a = alpha[i];
        } else {
            h = r1[i];
            a = alpha[i] * FxCurve.fadeOut((ms - conv) / (float) Math.max(1, dur[i] - conv));
        }
        int cx = Math.round(ax[i]);
        int cy = Math.round(ay[i]);
        int hs = Math.round(h);
        int x0 = cx - hs;
        int y0 = cy - hs;
        int x1 = cx + hs;
        int y1 = cy + hs;
        int c = col0[i];
        int len = 5;
        rect(x0, y0, x0 + len, y0 + 1, c, a);
        rect(x0, y0 + 1, x0 + 1, y0 + len, c, a);
        rect(x1 - len, y0, x1, y0 + 1, c, a);
        rect(x1 - 1, y0 + 1, x1, y0 + len, c, a);
        rect(x0, y1 - 1, x0 + len, y1, c, a);
        rect(x0, y1 - len, x0 + 1, y1 - 1, c, a);
        rect(x1 - len, y1 - 1, x1, y1, c, a);
        rect(x1 - 1, y1 - len, x1, y1 - 1, c, a);
    }

    /**
     * 扫描线：2 px 亮线 + 上方 6 px 渐隐尾巴，自上而下扫过格子内部（16 × 16），出了格子的部分不画。
     * arg = 横跳次数（中途按 38 ms 一跳左右错 2 px）
     */
    private void drawScan(int i, int ms, float t) {
        int sx = Math.round(ax[i]);
        int sy = Math.round(ay[i]);
        int dx = 0;
        int jumps = arg[i];
        for (int k = 0; k < jumps; k++) {
            int js = Math.round(dur[i] * (0.35F + 0.3F * k));
            if (ms >= js && ms < js + JUMP_MS) {
                dx = (k & 1) == 0 ? 2 : -2;
            }
        }
        int head = -2 + Math.round(24.0F * t);
        int c = col0[i];
        float a = alpha[i];
        clippedRow(sx + dx, sy, head, 2, c, a);
        for (int k = 1; k <= 6; k++) {
            clippedRow(sx + dx, sy, head - k, 1, c, a * (1.0F - k / 7.0F) * 0.6F);
        }
    }

    /** 格子内部整块填色，按 (1 - t)² 退 */
    private void drawFlash(int i, float t) {
        int sx = Math.round(ax[i]);
        int sy = Math.round(ay[i]);
        rect(sx, sy, sx + 16, sy + 16, col0[i], alpha[i] * FxCurve.fadeOut(t));
    }

    /**
     * 两格之间的折线：线头沿路径走 r0 毫秒（easeOutCubic），线头是一个亮点，身后留下颜色从 col0 渐到 col1 的线；
     * 走到头以后在剩下的时间里整条淡出。arg = 路径号，arg2 = 线宽
     */
    private void drawTether(int i, int ms) {
        int[] pts = paths[arg[i]];
        int total = pathLen[arg[i]];
        if (pts == null || total <= 0) {
            return;
        }
        int travel = Math.max(1, Math.round(r0[i]));
        float head;
        float a = alpha[i];
        boolean moving = ms < travel;
        if (moving) {
            head = total * FxCurve.easeOutCubic(ms / (float) travel);
        } else {
            head = total;
            a *= 1.0F - (ms - travel) / (float) Math.max(1, dur[i] - travel);
        }
        int w = arg2[i];
        int c0 = col0[i];
        int c1 = col1[i];
        float lineA = a * 0.75F;
        int walked = 0;
        int headX = pts[0];
        int headY = pts[1];
        for (int p = 0; p + 3 < pts.length && walked < head; p += 2) {
            int x0 = pts[p];
            int y0 = pts[p + 1];
            int x1 = pts[p + 2];
            int y1 = pts[p + 3];
            int seg = Math.abs(x1 - x0) + Math.abs(y1 - y0);
            if (seg == 0) {
                continue;
            }
            int take = (int) Math.min(seg, head - walked);
            int sxDir = Integer.signum(x1 - x0);
            int syDir = Integer.signum(y1 - y0);
            int ex = x0 + sxDir * take;
            int ey = y0 + syDir * take;
            int ca = FxCurve.lerpColor(c0, c1, walked / (float) total);
            int cb = FxCurve.lerpColor(c0, c1, (walked + take) / (float) total);
            if (y0 == y1) {
                if (ex >= x0) {
                    rectH(x0, y0, ex + 1, y0 + w, ca, cb, lineA);
                } else {
                    rectH(ex, y0, x0 + 1, y0 + w, cb, ca, lineA);
                }
            } else {
                if (ey >= y0) {
                    rectV(x0, y0, x0 + w, ey + 1, ca, cb, lineA);
                } else {
                    rectV(x0, ey, x0 + w, y0 + 1, cb, ca, lineA);
                }
            }
            walked += take;
            headX = ex;
            headY = ey;
        }
        if (moving) {
            // 线头比线宽两边各多 1 px
            int d = w + 2;
            rect(headX - 1, headY - 1, headX - 1 + d, headY - 1 + d, FxCurve.lerpColor(c1, FxPalette.WHITE, 0.6F), a);
        }
    }

    /**
     * 格框描边（18 × 18、斜切角），arg2 = 透明度走法：
     * {@link #FRAME_ENVELOPE} r0 毫秒淡入、停 r1 毫秒、剩下的淡出；{@link #FRAME_STEPS} 按 38 ms 阶跃熄灭；
     * {@link #FRAME_STEADY} 一直亮、最后 120 ms 淡出。
     * arg 低 2 位 = 故障横跳次数，第 2 位起是每一跳往左还是往右
     */
    private void drawFrame(int i, int ms) {
        float a;
        switch (arg2[i]) {
            case FRAME_ENVELOPE -> {
                int in = Math.max(1, Math.round(r0[i]));
                int hold = Math.round(r1[i]);
                if (ms < in) {
                    a = ms / (float) in;
                } else if (ms < in + hold) {
                    a = 1.0F;
                } else {
                    a = 1.0F - (ms - in - hold) / (float) Math.max(1, dur[i] - in - hold);
                }
                a *= alpha[i];
            }
            case FRAME_STEPS -> a = REMOVE_STEPS[Math.min(REMOVE_STEPS.length - 1, ms / STEP_MS)];
            default -> {
                int tail = dur[i] - 120;
                a = alpha[i] * (ms < tail ? 1.0F : 1.0F - (ms - tail) / 120.0F);
            }
        }
        if (a <= 0.0F) {
            return;
        }
        int cx = Math.round(ax[i]);
        int cy = Math.round(ay[i]);
        int jumps = arg[i] & 3;
        int dx = 0;
        for (int k = 0; k < jumps; k++) {
            int js = dur[i] * (k + 1) / (jumps + 1) - JUMP_MS / 2;
            if (ms >= js && ms < js + JUMP_MS) {
                dx = ((arg[i] >> (2 + k)) & 1) == 0 ? -2 : 2;
            }
        }
        if (dx != 0) {
            bevelSquare(cx + dx - 1, cy, 9, GLITCH_MAGENTA, a * 0.6F);
            bevelSquare(cx + dx + 1, cy, 9, GLITCH_CYAN, a * 0.6F);
        }
        bevelSquare(cx + dx, cy, 9, col0[i], a);
    }

    /** 锁定横杠：左右两段 8 × 2 px 从格子两侧外滑进来（arg2 毫秒）在中间合上，停住，最后 200 ms 淡出 */
    private void drawBars(int i, int ms) {
        int sx = Math.round(ax[i]);
        int sy = Math.round(ay[i]);
        int slide = Math.max(1, arg2[i]);
        float u = FxCurve.easeOutCubic(Math.min(1.0F, ms / (float) slide));
        int fadeStart = dur[i] - 200;
        float a = alpha[i] * (ms < fadeStart ? 1.0F : 1.0F - (ms - fadeStart) / 200.0F);
        int left = Math.round(sx - 10 + 10 * u);
        int right = Math.round(sx + 18 - 10 * u);
        int y = sy + 7;
        rect(left, y, left + 8, y + 2, col0[i], a);
        rect(right, y, right + 8, y + 2, col0[i], a);
    }

    /** 「+N」：从 (ax, ay) 往上浮 r1 px，前一半不透明，后一半淡出 */
    private void drawLabels(GuiGraphics g, long now) {
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < count; i++) {
            if (kind[i] != LABEL) {
                continue;
            }
            long el = now - start[i];
            if (el < 0L) {
                continue;
            }
            float t = Math.min(1.0F, el / (float) dur[i]);
            float a = alpha[i] * (t < 0.45F ? 1.0F : 1.0F - (t - 0.45F) / 0.55F);
            int a8 = Math.round(a * 255.0F);
            // 字体会把 alpha 小于 4 的颜色当成不透明，快退干净时直接不画
            if (a8 < 8) {
                continue;
            }
            // 配置把满级调到 100 以上才会走到拼字符串那一支
            String s = arg[i] >= 0 && arg[i] < PLUS.length ? PLUS[arg[i]] : "+" + arg[i];
            int x = Math.round(ax[i]) - font.width(s) / 2;
            int y = Math.round(ay[i] - r1[i] * FxCurve.easeOutCubic(t));
            g.drawString(font, s, x, y, (a8 << 24) | col0[i], true);
        }
    }

    // ---------- 拼图形 ----------

    /** 斜切方框：以 (cx, cy) 为中心、半边长 h，四角各切掉一个像素的斜角。h = 9 正好压在 18 × 18 的格框上 */
    private void bevelSquare(int cx, int cy, int h, int rgb, float a) {
        if (a <= 0.0F || h < 2) {
            return;
        }
        int x0 = cx - h;
        int y0 = cy - h;
        int x1 = cx + h;
        int y1 = cy + h;
        rect(x0 + 2, y0, x1 - 2, y0 + 1, rgb, a);
        rect(x0 + 2, y1 - 1, x1 - 2, y1, rgb, a);
        rect(x0, y0 + 2, x0 + 1, y1 - 2, rgb, a);
        rect(x1 - 1, y0 + 2, x1, y1 - 2, rgb, a);
        px(x0 + 1, y0 + 1, rgb, a);
        px(x1 - 2, y0 + 1, rgb, a);
        px(x0 + 1, y1 - 2, rgb, a);
        px(x1 - 2, y1 - 2, rgb, a);
    }

    /**
     * 平顶六边形描边，外接圆半径 rad，像素阶梯拼成：两条斜边每行算一次半宽，半宽相同的连续几行合成一个竖条；
     * 顶边、底边各一条横线
     */
    private void hexOutline(int cx, int cy, float rad, int rgb, float a) {
        if (a <= 0.0F || rad < 2.0F) {
            return;
        }
        int rows = Math.max(1, Math.round(rad * 0.8660254F));
        int k = 0;
        while (k < rows - 1) {
            int w = hexHalfWidth(rad, k);
            int k2 = k;
            while (k2 + 1 < rows - 1 && hexHalfWidth(rad, k2 + 1) == w) {
                k2++;
            }
            rect(cx - w, cy - 1 - k2, cx - w + 1, cy - k, rgb, a);
            rect(cx + w - 1, cy - 1 - k2, cx + w, cy - k, rgb, a);
            rect(cx - w, cy + k, cx - w + 1, cy + k2 + 1, rgb, a);
            rect(cx + w - 1, cy + k, cx + w, cy + k2 + 1, rgb, a);
            k = k2 + 1;
        }
        int wt = hexHalfWidth(rad, rows - 1);
        rect(cx - wt, cy - rows, cx + wt, cy - rows + 1, rgb, a);
        rect(cx - wt, cy + rows - 1, cx + wt, cy + rows, rgb, a);
    }

    /** 离中线第 k 行（取行中心 k + 0.5）的半宽：r - y / √3 */
    private static int hexHalfWidth(float rad, int k) {
        return Math.max(1, Math.round(rad - (k + 0.5F) * 0.57735026F));
    }

    /** 扫描线的一行（或几行），裁到格子内部 16 × 16 */
    private void clippedRow(int sx, int sy, int row, int height, int rgb, float a) {
        int y0 = Math.max(0, row);
        int y1 = Math.min(16, row + height);
        if (y1 <= y0 || a <= 0.0F) {
            return;
        }
        rect(sx, sy + y0, sx + 16, sy + y1, rgb, a);
    }

    private void px(int x, int y, int rgb, float a) {
        rect(x, y, x + 1, y + 1, rgb, a);
    }

    // ---------- 写顶点 ----------

    private void rect(float x0, float y0, float x1, float y1, int rgb, float a) {
        int a8 = alpha8(a);
        if (a8 == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        vertex(x0, y0, r, g, b, a8);
        vertex(x0, y1, r, g, b, a8);
        vertex(x1, y1, r, g, b, a8);
        vertex(x1, y0, r, g, b, a8);
    }

    /** 横向渐变：左边 rgbL、右边 rgbR */
    private void rectH(float x0, float y0, float x1, float y1, int rgbL, int rgbR, float a) {
        int a8 = alpha8(a);
        if (a8 == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        vertex(x0, y0, (rgbL >> 16) & 0xFF, (rgbL >> 8) & 0xFF, rgbL & 0xFF, a8);
        vertex(x0, y1, (rgbL >> 16) & 0xFF, (rgbL >> 8) & 0xFF, rgbL & 0xFF, a8);
        vertex(x1, y1, (rgbR >> 16) & 0xFF, (rgbR >> 8) & 0xFF, rgbR & 0xFF, a8);
        vertex(x1, y0, (rgbR >> 16) & 0xFF, (rgbR >> 8) & 0xFF, rgbR & 0xFF, a8);
    }

    /** 竖向渐变：上边 rgbT、下边 rgbB */
    private void rectV(float x0, float y0, float x1, float y1, int rgbT, int rgbB, float a) {
        int a8 = alpha8(a);
        if (a8 == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        vertex(x0, y0, (rgbT >> 16) & 0xFF, (rgbT >> 8) & 0xFF, rgbT & 0xFF, a8);
        vertex(x0, y1, (rgbB >> 16) & 0xFF, (rgbB >> 8) & 0xFF, rgbB & 0xFF, a8);
        vertex(x1, y1, (rgbB >> 16) & 0xFF, (rgbB >> 8) & 0xFF, rgbB & 0xFF, a8);
        vertex(x1, y0, (rgbT >> 16) & 0xFF, (rgbT >> 8) & 0xFF, rgbT & 0xFF, a8);
    }

    /** 自己做矩阵变换再写顶点：带矩阵的那个重载每个顶点都要新建一个向量 */
    private void vertex(float x, float y, int r, int g, int b, int a) {
        matrix.transformPosition(x, y, 0.0F, tmp);
        buffer.vertex(tmp.x(), tmp.y(), tmp.z()).color(r, g, b, a).endVertex();
    }

    private static int alpha8(float a) {
        if (a <= 0.0F) {
            return 0;
        }
        return Math.min(255, Math.round(a * 255.0F));
    }

    // ==================== 加元素 ====================

    private int add(byte k, int delayMs, int durMs) {
        if (count >= CAPACITY) {
            return -1;
        }
        int i = count++;
        kind[i] = k;
        start[i] = Util.getMillis() + Math.max(0, delayMs);
        dur[i] = Math.max(1, durMs);
        ax[i] = 0.0F;
        ay[i] = 0.0F;
        bx[i] = 0.0F;
        by[i] = 0.0F;
        qx[i] = 0.0F;
        qy[i] = 0.0F;
        r0[i] = 0.0F;
        r1[i] = 0.0F;
        alpha[i] = 1.0F;
        col0[i] = FxPalette.WHITE;
        col1[i] = FxPalette.WHITE;
        arg[i] = 0;
        arg2[i] = 0;
        return i;
    }

    /** 火花：从 (x, y) 以 (vx, vy) px/s 飞出 */
    private void spark(int delay, float x, float y, float vx, float vy, int life, int rgb, float a) {
        int i = add(SPARK, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = x;
        ay[i] = y;
        qx[i] = vx;
        qy[i] = vy;
        col0[i] = rgb;
        alpha[i] = a;
        // 方向量化成八个方向（tan 67.5° ≈ 2.414 为界），线就是三个像素一格一格连着
        float avx = Math.abs(vx);
        float avy = Math.abs(vy);
        int dx;
        int dy;
        if (avx > 2.414F * avy) {
            dx = (int) Math.signum(vx);
            dy = 0;
        } else if (avy > 2.414F * avx) {
            dx = 0;
            dy = (int) Math.signum(vy);
        } else {
            dx = (int) Math.signum(vx);
            dy = (int) Math.signum(vy);
        }
        arg[i] = (dx + 1) | ((dy + 1) << 2);
    }

    /** 一圈放射火花，速度 vMin ~ vMax px/s */
    private void sparkBurst(int delay, float x, float y, int n, float vMin, float vMax, int life, int rgb) {
        float phase = random.nextFloat() * Mth.TWO_PI;
        for (int k = 0; k < n; k++) {
            float ang = phase + Mth.TWO_PI * k / n + (random.nextFloat() - 0.5F) * 0.4F;
            float v = vMin + random.nextFloat() * (vMax - vMin);
            spark(delay, x, y, Mth.cos(ang) * v, Mth.sin(ang) * v, life, rgb, 1.0F);
        }
    }

    /** 光点：(x0, y0) → (x1, y1)，控制点 (cx, cy) */
    private void mote(int delay, float x0, float y0, float cx, float cy, float x1, float y1, int life,
                      int rgb0, int rgb1, int easing) {
        int i = add(MOTE, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = x0;
        ay[i] = y0;
        qx[i] = cx;
        qy[i] = cy;
        bx[i] = x1;
        by[i] = y1;
        col0[i] = rgb0;
        col1[i] = rgb1;
        arg2[i] = easing;
    }

    private void shard(int delay, float x, float y, float vx, float vy, int life, int rgb) {
        int i = add(SHARD, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = x;
        ay[i] = y;
        qx[i] = vx;
        qy[i] = vy;
        col0[i] = rgb;
        arg[i] = random.nextInt(4);
    }

    private void ring(int delay, Slot s, float extraFrom, float extraTo, int life, int rgb, float a) {
        int i = add(RING, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = s.x + 8;
        ay[i] = s.y + 8;
        r0[i] = 9.0F + extraFrom;
        r1[i] = 9.0F + extraTo;
        col0[i] = rgb;
        alpha[i] = a;
    }

    private void hex(int delay, Slot s, float radFrom, float radTo, int life, int rgb, boolean glitch) {
        int i = add(HEX, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = s.x + 8;
        ay[i] = s.y + 8;
        r0[i] = radFrom;
        r1[i] = radTo;
        col0[i] = rgb;
        arg[i] = glitch ? 1 : 0;
    }

    private void brackets(int delay, Slot s, float extraFrom, int convergeMs, int life, int rgb, boolean back) {
        int i = add(BRACKETS, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = s.x + 8;
        ay[i] = s.y + 8;
        r0[i] = 9.0F + extraFrom;
        r1[i] = 9.0F;
        arg[i] = back ? 1 : 0;
        arg2[i] = Math.max(1, convergeMs);
        col0[i] = rgb;
    }

    private void scan(int delay, Slot s, int life, int rgb, int jumps) {
        int i = add(SCAN, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = s.x;
        ay[i] = s.y;
        col0[i] = rgb;
        arg[i] = jumps;
    }

    private void flash(int delay, Slot s, int life, int rgb, float a) {
        int i = add(FLASH, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = s.x;
        ay[i] = s.y;
        col0[i] = rgb;
        alpha[i] = a;
    }

    private void tether(int delay, int path, int travelMs, int fadeMs, int width, int rgb0, int rgb1) {
        if (path < 0 || path >= pathCount) {
            return;
        }
        int i = add(TETHER, delay, travelMs + fadeMs);
        if (i < 0) {
            return;
        }
        arg[i] = path;
        arg2[i] = width;
        r0[i] = travelMs;
        col0[i] = rgb0;
        col1[i] = rgb1;
    }

    private void frame(int delay, Slot s, int life, int rgb, float a, int mode, float inMs, float holdMs, int jumps) {
        int i = add(FRAME, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = s.x + 8;
        ay[i] = s.y + 8;
        col0[i] = rgb;
        alpha[i] = a;
        arg2[i] = mode;
        r0[i] = inMs;
        r1[i] = holdMs;
        arg[i] = Math.min(3, jumps) | (random.nextInt(8) << 2);
    }

    private void bars(int delay, Slot s, int slideMs, int holdMs, int rgb) {
        int i = add(BARS, delay, slideMs + holdMs + 200);
        if (i < 0) {
            return;
        }
        ax[i] = s.x;
        ay[i] = s.y;
        arg2[i] = slideMs;
        col0[i] = rgb;
    }

    private void label(int delay, Slot s, int value, int life, int rgb) {
        int i = add(LABEL, delay, life);
        if (i < 0) {
            return;
        }
        ax[i] = s.x + 8;
        ay[i] = s.y - 10;
        r1[i] = 6.0F;
        arg[i] = value;
        col0[i] = rgb;
    }

    // ==================== 路径 ====================

    /**
     * 登记一条只有横竖两种走向的折线（相对界面左上角，x0, y0, x1, y1, …），返回路径号。
     * 在界面构造时登记，坐标跟着格子位置算
     */
    public int path(int... xy) {
        if (pathCount >= MAX_PATHS || xy.length < 4 || (xy.length & 1) != 0) {
            return -1;
        }
        int len = 0;
        for (int p = 0; p + 3 < xy.length; p += 2) {
            len += Math.abs(xy[p + 2] - xy[p]) + Math.abs(xy[p + 3] - xy[p + 1]);
        }
        paths[pathCount] = xy.clone();
        pathLen[pathCount] = len;
        return pathCount++;
    }

    // ==================== 分镜（声音 + 画面） ====================

    /** 打开工作台界面：终端上电的一声 */
    public static void playOpen() {
        sound(KuvaLichSounds.BLOCK_FORGE_OPEN.get(), 0.6F, 1.0F);
    }

    /**
     * 塑形块：四个角从外面收到结果格上、扫描线扫过（0~160 ms），160 ms 闪白 + 六边形外扩 + 一圈火花；
     * 掷中锁定的话 420 ms 两道红杠从两侧夹住格子，同时放锁定声
     */
    public void forma(Slot result, boolean locked) {
        sound(KuvaLichSounds.FORGE_FORMA.get(), 1.0F, 1.0F);
        brackets(0, result, 24.0F, 160, 420, FxPalette.TECH, false);
        scan(0, result, 160, FxPalette.TECH, 0);
        flash(160, result, 220, FxPalette.WHITE, 0.8F);
        hex(160, result, 10.0F, 36.0F, 400, FxPalette.TECH, false);
        sparkBurst(160, result.x + 8, result.y + 8, scaled(10, 3), 60.0F, 120.0F, 300, FxPalette.TECH_PALE);
        if (locked) {
            bars(420, result, 120, 600, LOCK_RED);
            soundLater(KuvaLichSounds.FORGE_FORMA_LOCK.get(), 0.9F, 1.0F, 420);
        }
    }

    /**
     * 暗影塑形块：和塑形块同一套动作，闪光改成先压暗再亮边，六边形换成 Ordis 紫并带品红 / 青色差，
     * 扫描线中途横跳两下。暗影塑形块本身就是解锁，不会再掷锁定
     */
    public void shadowForma(Slot result) {
        sound(KuvaLichSounds.FORGE_FORMA_SHADOW.get(), 1.0F, 1.0F);
        brackets(0, result, 24.0F, 160, 420, FxPalette.TECH, false);
        scan(0, result, 160, FxPalette.TECH, 2);
        flash(160, result, 260, SHADOW_DARK, 0.7F);
        frame(200, result, 320, ORDIS, 1.0F, FRAME_ENVELOPE, 40.0F, 80.0F, 0);
        hex(160, result, 10.0F, 36.0F, 400, ORDIS, true);
        sparkBurst(160, result.x + 8, result.y + 8, scaled(10, 3), 60.0F, 120.0F, 300, FxPalette.TECH_PALE);
    }

    /**
     * 模组升级：一串光点从内融核心格沿弧线飞进模组格，T 时模组格外扩方框 + 闪一下 + 上浮「+N」。
     * T 是升级声的爆发点，跟着播放音高缩放；满级换金色、多一圈金火花，闪光 1.2 秒慢慢退
     *
     * @param endo     内融核心所在的格子
     * @param module   被升级的模组所在的格子
     * @param newLevel 升级后的等级
     * @param max      升到了满级
     */
    public void rankUp(Slot endo, Slot module, int newLevel, boolean max) {
        int t;
        int color;
        if (max) {
            sound(KuvaLichSounds.FORGE_MODULE_MAXRANK.get(), 1.0F, 1.0F);
            t = 280;
            color = FxPalette.ACCENT;
        } else {
            int idx = Mth.clamp(newLevel, 1, RANK_PITCH.length) - 1;
            float pitch = RANK_PITCH[idx];
            sound(KuvaLichSounds.rankup(RANK_GROUP[idx]), 0.9F, pitch);
            t = KuvaSoundPlayer.scaleT(280, pitch);
            color = FxPalette.TECH;
        }
        float x0 = endo.x + 8;
        float y0 = endo.y + 8;
        float x1 = module.x + 8;
        float y1 = module.y + 8;
        float cx = (x0 + x1) * 0.5F;
        float cy = Math.min(y0, y1) - 20.0F;
        int n = scaled(8, 3);
        int fly = Math.max(60, t - 80);
        for (int k = 0; k < n; k++) {
            mote(25 * k, x0, y0, cx + (random.nextFloat() - 0.5F) * 8.0F, cy, x1, y1, fly,
                    max ? FxPalette.ACCENT : ENDO_MOTE, FxPalette.WHITE, EASE_IN_CUBIC);
        }
        if (max) {
            flash(t, module, 1200, FxPalette.ACCENT, 0.35F);
            sparkBurst(t, x1, y1, scaled(12, 4), 50.0F, 110.0F, 360, FxPalette.ACCENT);
        } else {
            flash(t, module, 260, FxPalette.TECH_PALE, 0.5F);
        }
        ring(t, module, 0.0F, 8.0F, 300, color, 1.0F);
        label(t, module, newLevel, 700, color);
    }

    /** 融合：两个输入格各有一条线流向结果格（赤毒红渐到金），500 ms 时结果格闪金 + 外扩方框 */
    public void fusion(int pathA, int pathB, Slot result) {
        sound(KuvaLichSounds.FORGE_FUSION.get(), 1.0F, 1.0F);
        tether(0, pathA, 500, 200, 1, FxPalette.KUVA, FxPalette.ACCENT);
        tether(0, pathB, 500, 200, 1, FxPalette.KUVA, FxPalette.ACCENT);
        flash(500, result, 320, FxPalette.ACCENT, 0.55F);
        ring(500, result, 0.0F, 9.0F, 360, FxPalette.ACCENT, 1.0F);
    }

    /** 紫卡循环：结果格的紫色描边左右乱跳三下（带色差），400 ms 定格时外扩一圈紫框 */
    public void rivenCycle(Slot result) {
        sound(KuvaLichSounds.FORGE_RIVEN_CYCLE.get(), 0.9F, 1.0F);
        frame(0, result, 400, RIVEN, 0.75F, FRAME_STEADY, 0.0F, 0.0F, 3);
        ring(400, result, 0.0F, 8.0F, 320, RIVEN, 1.0F);
    }

    /**
     * 开光（裂罅碎块）和 TACZ 枪械强化（玄骸之遗）：六个紫→白光点从材料格注入武器所在的格子，
     * 300 ms 全部到齐，落点轻轻闪一下
     */
    public void imbue(Slot material, Slot weapon) {
        sound(KuvaLichSounds.FORGE_IMBUE.get(), 0.9F, 1.0F);
        float x0 = material.x + 8;
        float y0 = material.y + 8;
        float x1 = weapon.x + 8;
        float y1 = weapon.y + 8;
        float cx = (x0 + x1) * 0.5F;
        float cy = Math.min(y0, y1) - 14.0F;
        int n = scaled(6, 2);
        for (int k = 0; k < n; k++) {
            int delay = 25 * k;
            mote(delay, x0, y0, cx + (random.nextFloat() - 0.5F) * 10.0F, cy + (random.nextFloat() - 0.5F) * 6.0F,
                    x1, y1, 300 - delay, RIVEN, FxPalette.WHITE, EASE_IN_CUBIC);
        }
        flash(300, weapon, 220, FxPalette.WHITE, 0.35F);
    }

    /**
     * 三合一：三个输入格各几颗光点汇向输出格，T 时输出格外扩一圈产出稀有度色的方框。
     * T 是三合一声的爆发点，音高按产出品质
     *
     * @param rarity 产出的稀有度（0 铜 ~ 4 紫卡；安魂卡等其它东西传 -1）
     */
    public void merge(Slot a, Slot b, Slot c, Slot out, int rarity) {
        float pitch = rarity >= 0 && rarity < MERGE_PITCH.length ? MERGE_PITCH[rarity] : 1.0F;
        sound(KuvaLichSounds.FORGE_RECAST_MERGE.get(), 0.9F, pitch);
        int t = KuvaSoundPlayer.scaleT(260, pitch);
        int color = rarity >= 0 ? rarityColor(rarity) : FxPalette.KUVA;
        int per = scaled(3, 1);
        mergeFrom(a, out, per, t, color);
        mergeFrom(b, out, per, t, color);
        mergeFrom(c, out, per, t, color);
        ring(t, out, 0.0F, 9.0F, 320, color, 1.0F);
    }

    private void mergeFrom(Slot from, Slot out, int n, int t, int color) {
        float x0 = from.x + 8;
        float y0 = from.y + 8;
        float x1 = out.x + 8;
        float y1 = out.y + 8;
        for (int k = 0; k < n; k++) {
            int delay = 30 * k;
            float cx = x0 + (x1 - x0) * 0.25F + (random.nextFloat() - 0.5F) * 12.0F;
            float cy = y1 + (y0 - y1) * 0.35F;
            mote(delay, x0, y0, cx, cy, x1, y1, Math.max(60, t - delay), color, FxPalette.WHITE, EASE_IN_CUBIC);
        }
    }

    /** 分解：输入格碎成几片往下掉，同时几颗内融核心色的光点飞进输出格 */
    public void decompose(Slot card, Slot out, int rarity) {
        if (KuvaSoundPlayer.gate("kuvalich.forge.decompose", 80)) {
            sound(KuvaLichSounds.FORGE_RECAST_DECOMPOSE.get(), 0.7F, KuvaSoundPlayer.jitter(1.0F, 0.04F));
        }
        float x0 = card.x + 8;
        float y0 = card.y + 8;
        int color = rarity >= 0 ? rarityColor(rarity) : FxPalette.ASH;
        int shards = scaled(8, 3);
        for (int k = 0; k < shards; k++) {
            shard(0, x0 + (random.nextFloat() - 0.5F) * 10.0F, y0 + (random.nextFloat() - 0.5F) * 8.0F,
                    (random.nextFloat() - 0.5F) * 90.0F, -20.0F - random.nextFloat() * 50.0F,
                    380 + random.nextInt(140), color);
        }
        float x1 = out.x + 8;
        float y1 = out.y + 8;
        int motes = scaled(4, 2);
        for (int k = 0; k < motes; k++) {
            float cx = (x0 + x1) * 0.5F + (random.nextFloat() - 0.5F) * 16.0F;
            float cy = (y0 + y1) * 0.5F;
            mote(40 * k, x0, y0, cx, cy, x1, y1, 260, ENDO_MOTE, FxPalette.WHITE, EASE_IN_CUBIC);
        }
    }

    /**
     * 装入模组：格框按稀有度色淡入、停住、退掉；军械库有武器格时，再从这一格沿折线流一道线到武器格，
     * 线到了武器格闪一下。紫卡的描边另外横跳两下
     *
     * @param path   这一格到武器格的路径号，没有武器格传 -1
     * @param weapon 武器格，没有传 null
     */
    public void install(Slot slot, int rarity, int path, Slot weapon) {
        if (KuvaSoundPlayer.gate("kuvalich.forge.install", 60)) {
            float pitch = rarity >= 0 && rarity < INSTALL_PITCH.length ? INSTALL_PITCH[rarity] : 1.0F;
            sound(KuvaLichSounds.FORGE_MODULE_INSTALL.get(), 0.8F, pitch);
        }
        int color = rarity >= 0 ? rarityColor(rarity) : FxPalette.TECH;
        frame(0, slot, 380, color, 1.0F, FRAME_ENVELOPE, 60.0F, 120.0F, rarity == 4 ? 2 : 0);
        if (weapon != null && path >= 0) {
            tether(60, path, 160, 140, 2, color, color);
            flash(220, weapon, 200, color, 0.35F);
        }
    }

    /** 取出模组：这一格的描边按 38 ms 阶跃 0.8 / 0 / 0.5 / 0 熄灭 */
    public void remove(Slot slot, int rarity) {
        if (KuvaSoundPlayer.gate("kuvalich.forge.remove", 60)) {
            sound(KuvaLichSounds.FORGE_MODULE_REMOVE.get(), 0.7F, KuvaSoundPlayer.jitter(1.0F, 0.03F));
        }
        frame(0, slot, STEP_MS * REMOVE_STEPS.length, rarity >= 0 ? rarityColor(rarity) : FxPalette.TECH,
                1.0F, FRAME_STEPS, 0.0F, 0.0F, 0);
    }

    /** 武器上台：四个角夹紧武器格（easeOutBack）+ 闪一下 */
    public void mount(Slot weapon) {
        sound(KuvaLichSounds.FORGE_WEAPON_MOUNT.get(), 0.8F, 1.0F);
        flash(0, weapon, 240, FxPalette.TECH_PALE, 0.4F);
        brackets(0, weapon, 6.0F, 120, 360, FxPalette.TECH, true);
    }

    // ==================== 小工具 ====================

    /** 稀有度色：0 铜 / 1 银 / 2 金 / 3 白金 / 4 紫卡 */
    public static int rarityColor(int rarity) {
        return RARITY[Mth.clamp(rarity, 0, RARITY.length - 1)];
    }

    private static final int EASE_IN_CUBIC = 1;

    private static float ease(int id, float t) {
        return id == EASE_IN_CUBIC ? t * t * t : t;
    }

    /**
     * 夹紧用的 easeOutBack（s = 2，过冲约 13%）。通用那条只过冲 4%，收 6 px 时还不到半个像素，
     * 在像素网格上看不出「咬一下」
     */
    private static float clampBack(float t) {
        float u = t - 1.0F;
        return 1.0F + 3.0F * u * u * u + 2.0F * u * u;
    }

    private static float bezier(float p0, float p1, float p2, float u) {
        float v = 1.0F - u;
        return v * v * p0 + 2.0F * v * u * p1 + u * u * p2;
    }

    /** 粒子数按特效强度缩，最少留 {@code min} 个 */
    private static int scaled(int n, int min) {
        return Math.max(min, Math.round(n * EffectsDisplayConfig.intensity().budgetScale));
    }

    private static void sound(SoundEvent ev, float vol, float pitch) {
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(ev, vol, pitch));
    }

    /** 延后 {@code delayMs} 再放（按 tick 取整，误差不到 25 ms） */
    private static void soundLater(SoundEvent ev, float vol, float pitch, int delayMs) {
        KuvaSoundPlayer.play(KuvaSoundPlayer.ui(ev, vol, pitch), Math.round(delayMs / 50.0F));
    }
}
