package pers.roinflam.kuvalich.mercy;

/**
 * 处决的顿帧。只拧客户端的演出钟，服务端照真实时间走。
 *
 * <p>每一刀刺入后演出钟停 h 毫秒，再按 {@link #CATCH_UP} 倍速追回。滞后 lag(t) 刺入后线性涨到 h，
 * 再以 (CATCH_UP − 1) 的斜率降回 0；扭过的钟 warp(t) = t − lag(t)。刺入那一刻 lag 还是 0，
 * 所以挂在刺入时刻上的刀声、火花、冲击环照真实时间放，不用另记起点。</p>
 *
 * <p>两段冻结叠在一起钟会倒走，所以一刀的停 + 追必须在下一刀之前走完；服务端会动手的时刻上滞后不超过
 * {@link #MAX_SERVER_LAG_MS}。这两条在 {@link MercyBeats.Lich} 建表时按每种结果校验，超了就缩短那一刀的停顿。</p>
 */
public final class MercyHitstop {

    /** 第一刀离定案（第一格填满那一 tick）很近，停得最短 */
    public static final int FIRST_MS = 45;
    public static final int NORMAL_MS = 70;
    /** 出结果的那一刀 */
    public static final int HEAVY_MS = 110;
    /** 追回的倍速；追回段长 = h ÷ (倍速 − 1) */
    public static final double CATCH_UP = 1.4D;
    /** 服务端可见时刻上演出钟最多落后多少（容差 300 ms 的一半，另一半留给客户端卡顿） */
    public static final int MAX_SERVER_LAG_MS = 150;

    private MercyHitstop() {
    }

    /** 这一场实际捅几刀：全对是这次验的刀数，判错捅到判错那一刀为止 */
    public static int stabCount(int stabs, int prefix) {
        return prefix >= stabs ? stabs : Math.min(stabs, prefix + 1);
    }

    /** 不缩短时第 {@code i} 刀（从 0 起）该停多久 */
    public static int desired(int i, int count) {
        if (i == 0) {
            return FIRST_MS;
        }
        return i == count - 1 ? HEAVY_MS : NORMAL_MS;
    }

    /** 停 {@code hold} 毫秒再追平，一共占多长 */
    public static int span(int hold) {
        return hold + (int) Math.ceil(hold / (CATCH_UP - 1.0D));
    }

    /** 第 {@code i} 刀在真实时刻 {@code t} 造成的滞后 */
    public static double lagOf(int stab, int hold, double t) {
        double d = t - stab;
        if (d <= 0.0D || hold <= 0) {
            return 0.0D;
        }
        if (d < hold) {
            return d;
        }
        return Math.max(0.0D, hold - (d - hold) * (CATCH_UP - 1.0D));
    }

    /** 真实时刻 {@code t} 演出钟落后多少毫秒 */
    public static double lag(int[] stabs, int[] holds, double t) {
        double lag = 0.0D;
        int n = Math.min(stabs.length, holds.length);
        for (int i = 0; i < n; i++) {
            lag += lagOf(stabs[i], holds[i], t);
        }
        return lag;
    }

    /** 扭过的钟（演出钟），永远不超过 {@code t}、不倒走 */
    public static long warp(int[] stabs, int[] holds, long t) {
        return Math.round(t - lag(stabs, holds, t));
    }

    /**
     * GeckoLib 片段的速度：它把速度乘在「片段开头到现在」的总时长上，不是播放速率，直接设斜率会跳帧。
     * 所以给比值 (warp(t) − warp(t0)) ÷ (t − t0)，t0 是片段开播的真实时刻
     */
    public static double clipSpeed(int[] stabs, int[] holds, long t0, long t) {
        double dt = t - t0;
        double dLag = lag(stabs, holds, t) - lag(stabs, holds, t0);
        if (dt < 1.0D) {
            // 片段刚开播：按此刻的斜率给，停顿里就是 0
            double next = lag(stabs, holds, t + 1.0D) - lag(stabs, holds, t);
            return Math.max(0.0D, 1.0D - next);
        }
        if (dLag == 0.0D) {
            return 1.0D;
        }
        return Math.max(0.0D, (dt - dLag) / dt);
    }
}
