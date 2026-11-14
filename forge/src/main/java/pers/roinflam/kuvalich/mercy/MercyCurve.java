package pers.roinflam.kuvalich.mercy;

/**
 * 处决者姿态的插值曲线。纯数学，客户端的 {@code MercyPose} 用，GameTest 也能直接测。
 *
 * <p>旧的几套每一段都是两头速度为 0 的缓动，过一个关键帧就停一下。新写法（{@link Keys}）里平常的帧按单调三次
 * 曲线走：切线取前后两段斜率的加权调和平均，同号才有，反向的那一帧速度自然是 0，不会冲过关键帧；要停的帧标
 * {@link #HOLD}。另有 {@link #ANTIC}（先往反方向收 10% 再出去，出刀用）、{@link #OVER}（冲过 8% 再落回，
 * 临界阻尼弹簧带初速度的解）、{@link #DECEL}（先快后慢，落脚用）。</p>
 */
public final class MercyCurve {

    public static final int SINE = 0, OUT = 1, SNAP = 2, IN = 3, BACK = 4, CURVE = 5, ANTIC = 6, OVER = 7, DECEL = 8;
    /** 标在模式上：这一帧速度为 0 */
    public static final int HOLD = 16;
    /** 旧写法的出刀帧从上一帧起多久到位；新写法里 OUT 也按这个窗口，只是换成会过冲的曲线 */
    public static final int OUT_MS = 110;

    // easeInBack 的 s：最低点正好 −10%
    private static final float ANTIC_S = 1.70158F;
    // 弹簧 e(t) = (−1 + a·t)·e^(−ω·t)：ω = 5、a = 4.1 时在段内四成多处冲过 8%，到段尾剩 2%（线性扣掉）。
    // ω 再大会在头两成就到位，看着像跳过去
    private static final double OVER_W = 5.0D, OVER_A = 4.1D;
    private static final double OVER_END = (-1.0D + OVER_A) * Math.exp(-OVER_W);

    private MercyCurve() {
    }

    public static int base(int mode) {
        return mode & 15;
    }

    /** 旧写法用的四种缓动和新加的三种，{@code x} 是这一段走了几成 */
    public static float ease(int mode, float x) {
        return switch (base(mode)) {
            case OUT, DECEL -> {
                float k = 1.0F - x;
                yield 1.0F - k * k * k;
            }
            case IN -> x * x;
            case BACK -> {
                float k = x - 1.0F;
                yield 1.0F + 2.70158F * k * k * k + 1.70158F * k * k;
            }
            case ANTIC -> x * x * ((ANTIC_S + 1.0F) * x - ANTIC_S);
            case OVER -> over(x);
            default -> (float) (0.5D - 0.5D * Math.cos(Math.PI * x));
        };
    }

    public static float over(float x) {
        double e = (-1.0D + OVER_A * x) * Math.exp(-OVER_W * x);
        return (float) (1.0D + e - OVER_END * x);
    }

    /** 三次 Hermite：{@code m0}、{@code m1} 是两头每毫秒的变化量，{@code len} 是这一段的毫秒数 */
    public static float hermite(float p0, float p1, float m0, float m1, float len, float x) {
        float x2 = x * x;
        float x3 = x2 * x;
        return (2 * x3 - 3 * x2 + 1) * p0 + (x3 - 2 * x2 + x) * len * m0 + (-2 * x3 + 3 * x2) * p1
                + (x3 - x2) * len * m1;
    }

    /** 单调三次的切线：两段斜率同号才给，取加权调和平均（PCHIP），不会冲过两头的值 */
    public static float tangent(float d0, float h0, float d1, float h1) {
        if (d0 == 0.0F || d1 == 0.0F || d0 > 0.0F != d1 > 0.0F) {
            return 0.0F;
        }
        float w1 = 2.0F * h1 + h0;
        float w2 = h1 + 2.0F * h0;
        return (w1 + w2) / (w1 / d0 + w2 / d1);
    }

    private static boolean smooth(int mode) {
        int b = base(mode);
        return b == CURVE || b == SINE;
    }

    /**
     * 新写法的一条关键帧轨道。每一帧：时刻、这一段从哪一刻开始动（小于 0 = 从上一帧起）、模式、各通道的值。
     * 平常的段（{@link #CURVE}，新写法里 {@link #SINE} 也按它）两头的切线开场时算好；挨着别的模式、延后起步、
     * 标了 {@link #HOLD} 的帧切线是 0。
     */
    public static final class Keys {
        private final int[] t;
        private final int[] start;
        private final int[] mode;
        private final float[][] v;
        private final float[][] tan;
        private final int channels;

        public Keys(int[] t, int[] start, int[] mode, float[][] v) {
            this.t = t;
            this.start = start;
            this.mode = mode;
            this.v = v;
            this.channels = v.length == 0 ? 0 : v[0].length;
            this.tan = new float[t.length][channels];
            for (int i = 1; i + 1 < t.length; i++) {
                if ((mode[i] & HOLD) != 0 || !smooth(mode[i]) || !smooth(mode[i + 1]) || delayed(i) || delayed(i + 1)) {
                    continue;
                }
                float h0 = Math.max(1, t[i] - t[i - 1]);
                float h1 = Math.max(1, t[i + 1] - t[i]);
                for (int c = 0; c < channels; c++) {
                    tan[i][c] = tangent((v[i][c] - v[i - 1][c]) / h0, h0, (v[i + 1][c] - v[i][c]) / h1, h1);
                }
            }
        }

        private boolean delayed(int i) {
            return start[i] >= 0 && start[i] > t[i - 1];
        }

        public int size() {
            return t.length;
        }

        public int lastTime() {
            return t[t.length - 1];
        }

        /** 第 {@code c} 个通道在 {@code time} 毫秒的值 */
        public float sample(int c, double time) {
            if (time <= t[0]) {
                return v[0][c];
            }
            int n = t.length;
            for (int i = 1; i < n; i++) {
                if (time > t[i]) {
                    continue;
                }
                int b = base(mode[i]);
                float p0 = v[i - 1][c];
                float p1 = v[i][c];
                if (b == SNAP) {
                    return time >= t[i] ? p1 : p0;
                }
                int s;
                if (start[i] >= 0) {
                    s = start[i];
                } else if (b == OUT) {
                    s = Math.max(t[i - 1], t[i] - OUT_MS);
                } else {
                    s = t[i - 1];
                }
                if (time <= s) {
                    return p0;
                }
                float len = Math.max(1, t[i] - s);
                float x = (float) Math.min(1.0D, (time - s) / len);
                if (smooth(b)) {
                    return hermite(p0, p1, tan[i - 1][c], tan[i][c], len, x);
                }
                int eased = b == OUT ? OVER : b;
                return p0 + (p1 - p0) * ease(eased, x);
            }
            return v[n - 1][c];
        }

        /**
         * 各通道按自己的起步延迟取值：通道 c 取 {@code time − offset[c]}。切镜（{@link #SNAP}）那一帧所有通道一起跳：
         * 走在前面的不提前跳过去，落在后面的跳过去以后不再回到切镜之前
         */
        public void sampleStaggered(long time, int[] offset, float[] dst) {
            for (int c = 0; c < channels; c++) {
                long tc = time - offset[c];
                if (tc != time) {
                    for (int i = 1; i < t.length; i++) {
                        if (base(mode[i]) != SNAP) {
                            continue;
                        }
                        int cut = t[i];
                        if (tc < cut && time >= cut) {
                            tc = cut;
                        } else if (tc >= cut && time < cut) {
                            tc = cut - 1;
                        }
                    }
                }
                dst[c] = sample(c, tc);
            }
        }
    }
}
