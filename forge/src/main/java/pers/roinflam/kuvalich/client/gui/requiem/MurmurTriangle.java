package pers.roinflam.kuvalich.client.gui.requiem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.FxPalette;

/**
 * 三枚安魂密语排成的三角：灭骸之扉（小号）和玄骸档案（大号）共用
 *
 * <p>以三枚的平均点为原点，左 (−d, +d/6)、中 (0, −d/3)、右 (+d, +d/6)，{@code d = 1.39 × 符号尺寸}。
 * 已揭示的那一枚画这张卡自己的符号（浅粉白线描，不填、不发光）；没揭示的画一枚赤毒菱晶（和解密 HUD 同一个形），
 * 正在解的那一枚外圈和液面跟着这一阶段的进度涨，后面还没轮到的是空的。</p>
 *
 * <p>三枚每 3 秒换一次位：周期起点对齐 3 秒网格，每个周期随机挑两枚互换（两成的周期跳过），
 * 移动 970 ms，前 730 ms 走完 95%、后 240 ms 缓停；两枚沿弧线擦肩而过，一个往上、一个往下偏 0.25 个符号尺寸。
 * 换位不出声，提示的是「这里的排列不代表正确顺序」。</p>
 *
 * <p>某一格刚揭示时：菱晶 300 ms 淡出，符号接着 750 ms 淡入，不闪、不发金光。</p>
 *
 * <p>时间一律由调用方传入（演出时钟 {@code KuvaFxDirector.clockMs()}）。画的时候不分配对象，只在渲染线程用。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MurmurTriangle {

    /** 三角半宽相对符号尺寸的倍数 */
    public static final float SPREAD = 1.39F;

    /** 换位周期 */
    private static final long CYCLE_MS = 3000L;
    /** 一次换位的移动时长、主运动段时长 */
    private static final float MOVE_MS = 970.0F;
    private static final float MAIN_MS = 730.0F;
    /** 主运动段走完的比例 */
    private static final float MAIN_SHARE = 0.95F;
    /** 跳过换位的周期占比 */
    private static final float SKIP_CHANCE = 0.2F;
    /** 弧线偏移（× 符号尺寸） */
    private static final float ARC = 0.25F;
    /** 揭示：菱晶淡出、符号淡入 */
    private static final float REVEAL_OUT_MS = 300.0F;
    private static final float REVEAL_IN_MS = 750.0F;
    /** 进度变化的补间 */
    private static final float PROGRESS_MS = 600.0F;

    /** 三个位置相对原点的偏移（× d）：左、中、右 */
    private static final float[] POS_X = {-1.0F, 0.0F, 1.0F};
    private static final float[] POS_Y = {1.0F / 6.0F, -1.0F / 3.0F, 1.0F / 6.0F};
    /** 一次换位挑哪两个位置 */
    private static final int[][] PAIRS = {{0, 1}, {0, 2}, {1, 2}};

    /** 主运动段的出口速度（× 每毫秒），和缓停段的入口速度一致，衔接处不顿 */
    private static final float SETTLE_V = 2.0F * (1.0F - MAIN_SHARE) / (MOVE_MS - MAIN_MS);

    private final float size;
    private final float d;
    private final long seed;

    private final int[] riddles = {-1, -1, -1};
    /** 第 i 格现在在哪个位置（0 左、1 中、2 右） */
    private final int[] slotPos = {0, 1, 2};
    /** 某一格揭示的时刻，没有为 −1 */
    private final long[] revealAt = {-1L, -1L, -1L};
    /** 上一帧画在哪（悬停判定用） */
    private final float[] lastX = new float[3];
    private final float[] lastY = new float[3];
    private boolean rendered;

    private boolean hasData;
    private float progressFrom;
    private float progressTo;
    private long progressAt = Long.MIN_VALUE;
    private int activeSlot = -1;

    /** 正在走的周期，以及这个周期换哪两个位置（−1 = 不换） */
    private long cycle = Long.MIN_VALUE;
    private int swapA = -1;
    private int swapB = -1;

    /**
     * @param symbolSize 一枚符号的尺寸（高）
     */
    public MurmurTriangle(float symbolSize) {
        this.size = symbolSize;
        this.d = SPREAD * symbolSize;
        this.seed = System.identityHashCode(this) * 0x9E3779B97F4A7C15L;
    }

    /** 三角的半宽 */
    public float spread() {
        return d;
    }

    /**
     * 每帧（或数据变了时）调一次
     *
     * @param riddles       三格：卡 id，没揭示为 −1（只读前三个）
     * @param stageProgress 这一阶段的进度 0~1，画在正在解的那一枚上
     * @param nowMs         演出时钟
     */
    public void update(int[] riddles, float stageProgress, long nowMs) {
        float p = Mth.clamp(stageProgress, 0.0F, 1.0F);
        for (int i = 0; i < 3; i++) {
            int id = i < riddles.length ? riddles[i] : -1;
            if (id < 0) {
                id = -1;
            }
            if (hasData && this.riddles[i] < 0 && id >= 0) {
                revealAt[i] = nowMs;
            } else if (id < 0) {
                revealAt[i] = -1L;
            }
            this.riddles[i] = id;
        }
        int active = firstUnrevealed();
        if (!hasData) {
            // 第一次拿到数据：从空的涨上去
            hasData = true;
            activeSlot = active;
            progressFrom = 0.0F;
            progressTo = p;
            progressAt = nowMs;
            return;
        }
        if (active != activeSlot) {
            // 换了一枚在解：新的那枚从空的开始
            activeSlot = active;
            progressFrom = 0.0F;
            progressTo = p;
            progressAt = nowMs;
        } else if (Math.abs(p - progressTo) > 1.0E-4F) {
            progressFrom = shownProgress(nowMs);
            progressTo = p;
            progressAt = nowMs;
        }
    }

    /**
     * 画三角
     *
     * @param cx    三枚的平均点 x
     * @param cy    三枚的平均点 y
     * @param nowMs 演出时钟
     */
    public void render(GuiGraphics g, float cx, float cy, long nowMs) {
        advanceCycle(nowMs);
        float move = moveFraction(nowMs - cycle * CYCLE_MS);
        float lw = GuiShapes.pixels(g, GuiShapes.lineWidthPx());
        float progress = shownProgress(nowMs);
        int active = firstUnrevealed();

        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            int from = slotPos[i];
            int to = from;
            float arc = 0.0F;
            if (swapA >= 0 && (from == swapA || from == swapB)) {
                to = from == swapA ? swapB : swapA;
                arc = from == swapA ? -1.0F : 1.0F;
            }
            float x = cx + d * Mth.lerp(move, POS_X[from], POS_X[to]);
            float y = cy + d * Mth.lerp(move, POS_Y[from], POS_Y[to]);
            if (arc != 0.0F) {
                y += arc * ARC * size * Mth.sin(move * Mth.PI);
            }
            lastX[i] = x;
            lastY[i] = y;

            int id = riddles[i];
            float crystalA = 1.0F;
            float sigilA = 0.0F;
            if (id >= 0) {
                crystalA = 0.0F;
                sigilA = 1.0F;
                if (revealAt[i] >= 0L) {
                    float t = nowMs - revealAt[i];
                    if (t < REVEAL_OUT_MS) {
                        crystalA = 1.0F - t / REVEAL_OUT_MS;
                        sigilA = 0.0F;
                    } else if (t < REVEAL_OUT_MS + REVEAL_IN_MS) {
                        sigilA = easeInOutSine((t - REVEAL_OUT_MS) / REVEAL_IN_MS);
                    } else {
                        revealAt[i] = -1L;
                    }
                }
            }
            if (crystalA > 0.0F) {
                // 刚揭示的那一枚淡出时是满的（它这一阶段刚走完）
                float p = id >= 0 ? 1.0F : (i == active ? progress : 0.0F);
                drawCrystal(g, x, y, size * 0.5F, p, crystalA, 0.0F, lw, nowMs);
            }
            if (sigilA > 0.0F) {
                RequiemSigils.draw(g, id, x, y, size, GuiShapes.argb(FxPalette.KUVA_PALE, sigilA), 0);
            }
        }
        GuiShapes.end();
        rendered = true;
    }

    /**
     * 鼠标在哪一格上（按上一帧画的位置算）
     *
     * @return 格号 0~2，都不在为 {@code null}
     */
    @Nullable
    public Integer hoveredSlot(double mx, double my) {
        if (!rendered) {
            return null;
        }
        float hh = size * 0.5F;
        for (int i = 0; i < 3; i++) {
            float hw = riddles[i] >= 0 ? RequiemSigils.width(size) * 0.5F : hh * GuiShapes.DIAMOND_ASPECT;
            if (Math.abs(mx - lastX[i]) <= hw && Math.abs(my - lastY[i]) <= hh) {
                return i;
            }
        }
        return null;
    }

    // ==================== 赤毒菱晶（解密 HUD 同一个画法） ====================

    /**
     * 画一枚竖长的赤毒菱晶，从下往上：菱内未填部分、赤毒液面、液面线和高光、内切面、中缝、外圈轨道、外圈进度
     *
     * <p>外圈从上顶点开始顺时针（上 → 右 → 下 → 左）点亮 {@code progress} 比例的周长，液面同步升到
     * {@code progress} 的高度；两样都是这一阶段的密语进度。半宽 = 半高 × 0.6。尺寸按半高 8 的那一版定，
     * 别的尺寸等比缩放。</p>
     *
     * @param hh           半高
     * @param progress     进度 0~1
     * @param bodyAlpha    整枚菱晶（液面、刻面、轨道、进度）的透明度；揭示时它先淡掉
     * @param outlineAlpha 另外叠一圈完整外框的透明度（揭示时菱晶淡掉、外框留着用），0 = 不画
     * @param lw           线宽（当前坐标下）
     * @param nowMs        演出时钟（液面高光的慢呼吸用）
     */
    public static void drawCrystal(GuiGraphics g, float cx, float cy, float hh, float progress,
                                   float bodyAlpha, float outlineAlpha, float lw, long nowMs) {
        float hw = hh * GuiShapes.DIAMOND_ASPECT;
        float k = hh / 8.0F;
        float p = Mth.clamp(progress, 0.0F, 1.0F);
        GuiShapes.begin(g);
        if (bodyAlpha > 0.002F) {
            GuiShapes.diamondFill(g, cx, cy, hw, hh, GuiShapes.argb(FxPalette.KUVA_MIST, 0.45F * bodyAlpha));
            if (p > 0.0F) {
                float yCut = cy + hh - 2.0F * hh * p;
                GuiShapes.diamondFillBelow(g, cx, cy, hw, hh, yCut, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.95F * bodyAlpha));
                float w = GuiShapes.diamondHalfWidthAt(hw, hh, yCut - cy);
                if (w > lw) {
                    // 液面那一行，再叠一道 2.4 s 慢呼吸的高光（只变亮暗，不晃）
                    float yl = yCut + lw * 0.5F;
                    GuiShapes.hairline(g, cx - w, yl, cx + w, yl, lw, GuiShapes.argb(FxPalette.KUVA, 0.8F * bodyAlpha));
                    float breath = 0.5F - 0.5F * Mth.cos((nowMs % 2400L) / 2400.0F * Mth.TWO_PI);
                    float hl = (0.45F + 0.25F * breath) * bodyAlpha;
                    GuiShapes.hairline(g, cx - w * 0.7F, yl, cx + w * 0.7F, yl, lw, GuiShapes.argb(FxPalette.KUVA_HOT, hl));
                }
            }
            // 内切面：往里缩一圈，给一点晶体的厚度感
            float ihh = hh - 2.0F * k;
            float ihw = hw - 1.2F * k;
            if (ihh > lw && ihw > lw) {
                GuiShapes.diamondOutline(g, cx, cy, ihw, ihh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.2F * bodyAlpha));
            }
            GuiShapes.hairline(g, cx, cy - hh, cx, cy + hh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.18F * bodyAlpha));
            GuiShapes.diamondOutline(g, cx, cy, hw, hh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.2F * bodyAlpha));
            if (p > 0.0F) {
                GuiShapes.diamondTrace(g, cx, cy, hw, hh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, 0.95F * bodyAlpha), p);
            }
        }
        if (outlineAlpha > 0.002F) {
            GuiShapes.diamondOutline(g, cx, cy, hw, hh, lw, GuiShapes.argb(FxPalette.KUVA_PALE, outlineAlpha));
        }
        GuiShapes.end();
    }

    /** 缓入缓出（正弦） */
    public static float easeInOutSine(float t) {
        float u = Mth.clamp(t, 0.0F, 1.0F);
        return 0.5F - 0.5F * Mth.cos(u * Mth.PI);
    }

    // ==================== 内部 ====================

    private int firstUnrevealed() {
        for (int i = 0; i < 3; i++) {
            if (riddles[i] < 0) {
                return i;
            }
        }
        return -1;
    }

    private float shownProgress(long nowMs) {
        if (progressAt == Long.MIN_VALUE) {
            return progressTo;
        }
        float t = Mth.clamp((nowMs - progressAt) / PROGRESS_MS, 0.0F, 1.0F);
        float u = 1.0F - t;
        return progressFrom + (progressTo - progressFrom) * (1.0F - u * u * u);
    }

    /** 走到这一帧所在的周期：上一个周期的换位落定，再给这个周期挑一对 */
    private void advanceCycle(long nowMs) {
        long c = Math.floorDiv(nowMs, CYCLE_MS);
        if (c == cycle) {
            return;
        }
        boolean first = cycle == Long.MIN_VALUE;
        if (!first && swapA >= 0) {
            for (int i = 0; i < 3; i++) {
                if (slotPos[i] == swapA) {
                    slotPos[i] = swapB;
                } else if (slotPos[i] == swapB) {
                    slotPos[i] = swapA;
                }
            }
        }
        cycle = c;
        swapA = -1;
        swapB = -1;
        // 刚开始画、或者中间有一阵没画（界面关过）时，这个周期已经走了一截，就不从半路开始动
        if (first || nowMs - c * CYCLE_MS > 100L) {
            return;
        }
        long h = mix(c * 0x9E3779B97F4A7C15L ^ seed);
        float u = (h >>> 40) / (float) (1 << 24);
        if (u < SKIP_CHANCE) {
            return;
        }
        int[] pair = PAIRS[(int) Long.remainderUnsigned(h >>> 8, PAIRS.length)];
        swapA = pair[0];
        swapB = pair[1];
    }

    /**
     * 周期里第 {@code t} 毫秒走到了全程的几成：前 730 ms 从静止加速再减速，走完 95% 时还留一点速度，
     * 后 240 ms 用同样的速度接上、匀减速停住，中间不顿
     */
    private static float moveFraction(long t) {
        if (t <= 0L) {
            return 0.0F;
        }
        if (t >= MOVE_MS) {
            return 1.0F;
        }
        if (t < MAIN_MS) {
            float u = t / MAIN_MS;
            float m1 = SETTLE_V * MAIN_MS;
            return (-2.0F * u * u * u + 3.0F * u * u) * MAIN_SHARE + (u * u * u - u * u) * m1;
        }
        float tau = (t - MAIN_MS) / (MOVE_MS - MAIN_MS);
        float rest = 1.0F - MAIN_SHARE;
        return MAIN_SHARE + rest * (1.0F - (1.0F - tau) * (1.0F - tau));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
