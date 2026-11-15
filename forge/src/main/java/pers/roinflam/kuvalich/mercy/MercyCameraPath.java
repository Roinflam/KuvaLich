package pers.roinflam.kuvalich.mercy;

/**
 * 处决机位上随时间走的那一层：在本套机位（遮挡检测过的静态位置）上叠推拉、绕、升降、滚转和跟人的比例。
 * 纯数学，客户端的 {@code MercyCamera} 用，GameTest 也能直接测。
 *
 * <p>一条轨迹分几段，段和段之间要么硬切（落在刀点上），要么是刺入那一下的猛推：按真实钟在 {@link #PUNCH_MS}
 * 里从上一段的末值冲到这一段，顿帧时照样推过去。段内各通道按 {@link MercyCurve.Keys} 的单调三次走，
 * 每段最后一帧放在切点之后，切的那一刻镜头还在动。</p>
 *
 * <p>只有排过轨迹的套路有这一层（现在是正面直刺），别的套路照旧是固定机位。</p>
 */
public final class MercyCameraPath {

    /** 客户端设置 {@code mercy_camera_motion} */
    public enum Motion {
        /** 轨迹、跟人、进机位和切宽镜头的过渡都有 */
        FULL,
        /** 只留推拉和过渡，不绕、不升降、不滚、不跟人，震动减半 */
        REDUCED,
        /** 固定机位、硬切，和没有这一层时一样 */
        STATIC
    }

    /** 沿视线往注视点推近的比例（0.2 = 离注视点近两成），负的是拉远 */
    public static final int DOLLY = 0;
    /** 绕注视点竖轴转的角度，正的往处决者那一侧转（看得到目标的脸） */
    public static final int ORBIT = 1;
    /** 镜头升降（格，按目标身高缩放） */
    public static final int RISE = 2;
    /** 滚转（度） */
    public static final int ROLL = 3;
    /** 注视点跟处决者位移的比例 */
    public static final int FOLLOW = 4;
    public static final int CHANNELS = 5;

    /** 从玩家视角推出去进机位 */
    public static final int ENTRY_MS = 250;
    /** 失败、挣开站起切宽镜头的过渡 */
    public static final int WIDE_BLEND_MS = 350;
    /** 刺入那一下的猛推 */
    public static final int PUNCH_MS = 80;
    /** 不到三刀就出结果的，最后一刀之后轨迹再走这么久就停：后面那几段是给没演到的那一刀的蓄力排的 */
    public static final int HOLD_AFTER_LAST_MS = 600;
    /** 减弱档的震动 */
    public static final float REDUCED_SHAKE = 0.5F;

    private record Seg(int from, boolean punch, MercyCurve.Keys keys) {
    }

    private static final Seg[][] PATHS = new Seg[MercyVariant.values().length][];

    static {
        PATHS[MercyVariant.LICH_A.ordinal()] = lichA();
    }

    private MercyCameraPath() {
    }

    /**
     * 正面直刺：第一刀侧面中近景、慢慢往里蹭；第二刀切近两成、压低一点往处决者那边绕 10°、画面歪 2°；
     * 拔刀之后边拉开边升，把举过头顶的刀和抬头看刀的玄骸都放进来；第三刀刺入猛推，之后随处决者蹬开慢慢退出来
     */
    private static Seg[] lichA() {
        MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_A);
        int in = b.cutIn;
        int s2 = b.stab(1);
        int s3 = b.stab(2);
        int end = b.closeup;
        return new Seg[]{
                seg(in, false,
                        k(in, -0.03F, 0.0F, 0.0F, 0.0F, 0.45F),
                        k(s2 + 400, 0.06F, 4.0F, 0.0F, 0.0F, 0.45F)),
                seg(s2, false,
                        k(s2, 0.20F, 10.0F, -0.12F, -2.0F, 0.45F),
                        k(s2 + 580, 0.24F, 11.5F, -0.12F, -2.0F, 0.45F),
                        k(s3 - 300, 0.04F, 8.0F, 0.10F, -0.5F, 0.45F),
                        k(s3 - 40, -0.04F, 7.0F, 0.16F, 0.0F, 0.45F),
                        k(s3 + 200, -0.06F, 6.8F, 0.17F, 0.0F, 0.45F)),
                seg(s3, true,
                        k(s3, 0.22F, 6.0F, 0.02F, 0.0F, 0.45F),
                        k(s3 + 250, 0.20F, 5.8F, 0.03F, 0.0F, 0.45F),
                        k(s3 + 1000, -0.02F, 4.0F, 0.08F, 0.0F, 0.45F),
                        k(end + 300, -0.07F, 3.0F, 0.09F, 0.0F, 0.45F))
        };
    }

    private static float[] k(int t, float dolly, float orbit, float rise, float roll, float follow) {
        return new float[]{t, dolly, orbit, rise, roll, follow};
    }

    private static Seg seg(int from, boolean punch, float[]... rows) {
        int n = rows.length;
        int[] t = new int[n];
        int[] start = new int[n];
        int[] mode = new int[n];
        float[][] v = new float[n][CHANNELS];
        for (int i = 0; i < n; i++) {
            t[i] = (int) rows[i][0];
            start[i] = -1;
            mode[i] = MercyCurve.CURVE;
            System.arraycopy(rows[i], 1, v[i], 0, CHANNELS);
        }
        return new Seg(from, punch, new MercyCurve.Keys(t, start, mode, v));
    }

    /** 这一套排过轨迹没有 */
    public static boolean has(MercyVariant v) {
        return PATHS[v.ordinal()] != null;
    }

    /** 轨迹按哪一刻取：不到三刀就出结果的，最后一刀之后停住 */
    private static long clamp(MercyVariant v, int lastStab, long t) {
        if (lastStab >= 3 || lastStab <= 0) {
            return t;
        }
        return Math.min(t, MercyBeats.lich(v).stab(lastStab - 1) + HOLD_AFTER_LAST_MS);
    }

    private static int segIndex(Seg[] p, long t) {
        int k = 0;
        for (int i = 1; i < p.length; i++) {
            if (t >= p[i].from) {
                k = i;
            }
        }
        return k;
    }

    /** 此刻在第几段；切段时注视点的跟随直接对齐，不追 */
    public static int segment(MercyVariant v, int lastStab, long t) {
        Seg[] p = PATHS[v.ordinal()];
        return p == null ? 0 : segIndex(p, clamp(v, lastStab, t));
    }

    /**
     * 取此刻的五个通道到 {@code out}。{@code t} 是演出钟（顿帧扭过的），{@code raw} 是真实钟，只给刺入的猛推用。
     * 静态档、没排轨迹的套路返回 false，{@code out} 不动
     */
    public static boolean sample(MercyVariant v, int lastStab, Motion m, long t, long raw, float[] out) {
        Seg[] p = PATHS[v.ordinal()];
        if (p == null || m == Motion.STATIC) {
            return false;
        }
        long tt = clamp(v, lastStab, t);
        int k = segIndex(p, tt);
        Seg s = p[k];
        float x = 1.0F;
        if (s.punch && k > 0 && tt == t) {
            x = Math.max(0.0F, Math.min(1.0F, (raw - s.from) / (float) PUNCH_MS));
            float q = 1.0F - x;
            x = 1.0F - q * q * q;
        }
        for (int c = 0; c < CHANNELS; c++) {
            float val = s.keys.sample(c, tt);
            if (x < 1.0F) {
                float prev = p[k - 1].keys.sample(c, s.from);
                val = prev + (val - prev) * x;
            }
            out[c] = val;
        }
        if (m == Motion.REDUCED) {
            out[ORBIT] = 0.0F;
            out[RISE] = 0.0F;
            out[ROLL] = 0.0F;
            out[FOLLOW] = 0.0F;
        }
        return true;
    }

    /**
     * 遮挡检测要查的时刻：每一段的两头和中点（到 {@code windowEnd} 为止，本套机位在那一刻交给下一个镜头）。
     * 猛推的中间几帧在上一段末值和这一段起点的连线上，两头查过就够
     */
    public static int[] checkTimes(MercyVariant v, int lastStab, int windowEnd) {
        Seg[] p = PATHS[v.ordinal()];
        if (p == null) {
            return new int[0];
        }
        long limit = Math.min(windowEnd - 1L, clamp(v, lastStab, Long.MAX_VALUE / 4));
        int[] buf = new int[p.length * 3];
        int n = 0;
        for (int i = 0; i < p.length; i++) {
            int a = p[i].from;
            if (a > limit) {
                break;
            }
            long b = i + 1 < p.length ? Math.min(limit, p[i + 1].from - 1L) : limit;
            buf[n++] = a;
            buf[n++] = (int) ((a + b) / 2);
            buf[n++] = (int) b;
        }
        int[] r = new int[n];
        System.arraycopy(buf, 0, r, 0, n);
        return r;
    }
}
