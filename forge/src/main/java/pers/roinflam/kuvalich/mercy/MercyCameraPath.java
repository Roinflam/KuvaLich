package pers.roinflam.kuvalich.mercy;

import net.minecraft.world.phys.Vec3;

/**
 * 处决机位上随时间走的那一层：在本套机位（遮挡检测过的静态位置）上叠推拉、绕、升降、滚转和跟人的比例。
 * 纯数学，客户端的 {@code MercyCamera} 用，GameTest 也能直接测。
 *
 * <p>一条轨迹分几段，段和段之间要么硬切（落在刀点上），要么是刺入那一下的猛推：按真实钟在 {@link #PUNCH_MS}
 * 里从上一段的末值冲到这一段，顿帧时照样推过去。段内各通道按 {@link MercyCurve.Keys} 的单调三次走，
 * 每段最后一帧放在切点之后，切的那一刻镜头还在动。</p>
 *
 * <p>只有排过轨迹的套路有这一层（现在是正面直刺、背后锁颈、贴身上挑、翻滚绕背），别的套路照旧是固定机位。</p>
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
    /** 排过轨迹的套路进机位走弧线，转角大（侧面机位要转将近 90°），给长一点 */
    public static final int ENTRY_ARC_MS = 300;
    /** 弧线上查遮挡、查身体间隙的点数 */
    public static final int ENTRY_SAMPLES = 12;
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
        PATHS[MercyVariant.LICH_B.ordinal()] = lichB();
        PATHS[MercyVariant.LICH_C.ordinal()] = lichC();
        PATHS[MercyVariant.LICH_D.ordinal()] = lichD();
    }

    private MercyCameraPath() {
    }

    /**
     * 正面直刺：第一刀侧面中近景、慢慢往里蹭；第二刀切到同一侧的斜后方低机位往上拍，看刀尖从玄骸背后透出来，画面歪 1.5°；
     * 拔刀之后边绕回侧面边拉开升高，把举过头顶的刀和抬头看刀的玄骸都放进来；第三刀刺入猛推，之后随处决者蹬开慢慢退出来。
     * 第二刀不能从侧面拍：两人贴在一起，刀口在两个身子中间，玄骸往前蜷时它的左肩甲和左臂正好挡在前面，往处决者那边绕多少都挡
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
                        k(s2, 0.20F, -25.0F, -0.65F, -1.5F, 0.45F),
                        k(s2 + 400, 0.23F, -23.0F, -0.62F, -1.5F, 0.45F),
                        k(s3 - 240, 0.02F, 2.0F, 0.10F, -0.3F, 0.45F),
                        k(s3 - 40, -0.04F, 7.0F, 0.16F, 0.0F, 0.45F),
                        k(s3 + 200, -0.06F, 6.8F, 0.17F, 0.0F, 0.45F)),
                seg(s3, true,
                        k(s3, 0.22F, 6.0F, 0.02F, 0.0F, 0.45F),
                        k(s3 + 250, 0.20F, 5.8F, 0.03F, 0.0F, 0.45F),
                        k(s3 + 1000, -0.02F, 4.0F, 0.08F, 0.0F, 0.45F),
                        k(end + 300, -0.07F, 3.0F, 0.09F, 0.0F, 0.45F))
        };
    }

    /**
     * 背后锁颈，本套机位是处决者右肩后上方的过肩镜头。原样拍第一刀，刀口在处决者的头后面，所以整段先往侧面绕 20 多度、
     * 升高 0.2 格，从他头的右上方看下去；勒着脖子憋那一下慢慢往里蹭。第二刀硬切到侧面压低，看它被扳到后面的头和背心那一刀，
     * 画面歪 2°；拔刀转圈时边拉开边升回去，让整圈都在画里；第三刀刺入猛推，之后跟着它往前扑倒慢慢退出来。
     * 绕的方向是负的：过肩机位在处决者身后，往处决者那一侧绕就绕到他背后被他自己挡住
     */
    private static Seg[] lichB() {
        MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_B);
        int in = b.cutIn;
        int s1 = b.stab(0);
        int s2 = b.stab(1);
        int s3 = b.stab(2);
        int end = b.closeup;
        return new Seg[]{
                seg(in, false,
                        k(in, -0.04F, -22.0F, 0.22F, 0.0F, 0.45F),
                        k(s1 + 300, 0.03F, -25.0F, 0.25F, 0.0F, 0.45F),
                        k(s2 + 200, 0.13F, -31.0F, 0.20F, 0.0F, 0.45F)),
                seg(s2, false,
                        k(s2, 0.14F, -56.0F, -0.50F, -2.0F, 0.45F),
                        k(s2 + 300, 0.18F, -55.0F, -0.47F, -2.0F, 0.45F),
                        k(s3 - 60, -0.05F, -32.0F, 0.22F, 0.0F, 0.45F),
                        k(s3 + 200, -0.07F, -31.0F, 0.23F, 0.0F, 0.45F)),
                seg(s3, true,
                        k(s3, 0.20F, -30.0F, 0.18F, 0.0F, 0.45F),
                        k(s3 + 250, 0.18F, -29.5F, 0.20F, 0.0F, 0.45F),
                        k(s3 + 1000, -0.04F, -24.0F, 0.30F, 0.0F, 0.45F),
                        k(end + 300, -0.08F, -22.0F, 0.32F, 0.0F, 0.45F))
        };
    }

    /**
     * 贴身上挑，本套机位是斜前方低机位往上看。原机位的注视点在站直的玄骸头顶，这一套它半蹲着、刀口在肚子上，
     * 刀口落在画面下沿被提示条盖住，所以排轨迹时注视点往下挪（挪到正中又被处决准星盖住） {@link #LOOK_DROP_C}。第一刀往侧面绕 20 多度（不绕的话
     * 注视点挪低以后视线擦着处决者的头）、慢慢往里蹭；第二刀硬切到正侧面、稍抬一点，从它垂下来的胳膊底下看刀从两个身子中间
     * 挑进去，画面歪 2°；拔刀以后边退边压低，第三刀前它趴在处决者肩上、处决者压到最低那一下从下往上看；第三刀刺入猛推，
     * 之后跟着处决者往上一窜、后空翻，边拉开边升高
     */
    private static Seg[] lichC() {
        MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_C);
        int in = b.cutIn;
        int s1 = b.stab(0);
        int s2 = b.stab(1);
        int s3 = b.stab(2);
        int end = b.closeup;
        return new Seg[]{
                seg(in, false,
                        k(in, -0.02F, -20.0F, 0.0F, 0.0F, 0.45F),
                        k(s1 + 300, 0.04F, -22.0F, 0.05F, 0.0F, 0.45F),
                        k(s2 + 200, 0.09F, -24.0F, 0.08F, 0.0F, 0.45F)),
                seg(s2, false,
                        k(s2, 0.14F, -46.0F, 0.12F, 2.0F, 0.45F),
                        k(s2 + 300, 0.17F, -45.0F, 0.10F, 2.0F, 0.45F),
                        k(s3 - 80, -0.05F, -32.0F, -0.10F, 0.0F, 0.45F),
                        k(s3 + 200, -0.06F, -31.5F, -0.11F, 0.0F, 0.45F)),
                seg(s3, true,
                        k(s3, 0.22F, -24.0F, -0.15F, 0.0F, 0.45F),
                        k(s3 + 200, 0.20F, -23.5F, -0.12F, 0.0F, 0.45F),
                        k(s3 + 700, -0.10F, -22.0F, 0.25F, 0.0F, 0.45F),
                        k(end + 300, -0.15F, -22.0F, 0.30F, 0.0F, 0.45F))
        };
    }

    /**
     * 翻滚绕背，本套机位在绕背那一侧、离得远，好把整段翻滚放进画里。前两刀在正面：正侧面看刀口被它的左肩甲和垂着的左臂挡住，
     * 所以往处决者那一侧绕 15° 上下、压低 0.3 格从胳膊底下看，推近两成拍成中近景；第二刀同正面直刺，切到同一侧的
     * 斜后方低机位往上拍，画面歪 1.5°；拔刀翻滚时边拉开边升高，处决者从镜头和它之间滚过去，落到它身后时整个人都在画里；第三刀刺入猛推，再往它背后绕一点、
     * 稍抬高，越过处决者的左肩看刀扎进后背（处决者背对着画面那一侧握刀，侧面拍不到刀口），之后跟着它往前扑倒退出来
     */
    private static Seg[] lichD() {
        MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_D);
        int in = b.cutIn;
        int s1 = b.stab(0);
        int s2 = b.stab(1);
        int s3 = b.stab(2);
        int end = b.closeup;
        return new Seg[]{
                seg(in, false,
                        k(in, 0.02F, 14.0F, -0.10F, 0.0F, 0.45F),
                        k(s1 - 120, 0.18F, 16.0F, -0.28F, 0.0F, 0.45F),
                        k(s1 + 300, 0.21F, 17.0F, -0.30F, 0.0F, 0.45F),
                        k(s2 + 200, 0.26F, 19.0F, -0.32F, 0.0F, 0.45F)),
                seg(s2, false,
                        k(s2, 0.26F, -24.0F, -0.55F, -1.5F, 0.3F),
                        k(s2 + 300, 0.27F, -22.0F, -0.52F, -1.5F, 0.3F),
                        k(s2 + 560, 0.10F, -12.0F, -0.05F, -0.5F, 0.3F),
                        k(s2 + 900, -0.04F, -8.0F, 0.28F, 0.0F, 0.3F),
                        k(s3 - 120, -0.02F, -14.0F, 0.26F, 0.0F, 0.3F),
                        k(s3 + 200, -0.03F, -15.0F, 0.26F, 0.0F, 0.3F)),
                seg(s3, true,
                        k(s3, 0.22F, -28.0F, 0.20F, 1.5F, 0.3F),
                        k(s3 + 250, 0.20F, -27.5F, 0.22F, 1.5F, 0.3F),
                        k(s3 + 1000, 0.0F, -22.0F, 0.30F, 0.0F, 0.3F),
                        k(end + 300, -0.05F, -20.0F, 0.32F, 0.0F, 0.3F))
        };
    }

    /** 贴身上挑的注视点往下挪多少（格，按目标身高缩放） */
    public static final float LOOK_DROP_C = 0.45F;

    /** 注视点往下挪多少；只在走轨迹时用，静态档和没排轨迹的套路照旧 */
    public static float lookDrop(MercyVariant v) {
        return v == MercyVariant.LICH_C ? LOOK_DROP_C : 0.0F;
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

    /** 进机位的缓动：两头都慢。起步不能快，注视点离眼睛不到一格，镜头稍一横移朝向就转一大截 */
    public static float entryEase(float u) {
        float x = Math.max(0.0F, Math.min(1.0F, u));
        return (float) (0.5D - 0.5D * Math.cos(Math.PI * x));
    }

    /**
     * 进机位弧线上 {@code x}（0~1，已缓动）处的镜头位置：注视点从 {@code fromLook} 直线移到 {@code toLook}，
     * 镜头相对注视点的偏移按水平角、水平半径、高差分别插值，绕着注视点转过去。
     * 走直线的话从处决者眼睛到侧面机位正好横穿目标的肩甲和头冠
     */
    public static Vec3 entryArc(Vec3 fromPos, Vec3 fromLook, Vec3 toPos, Vec3 toLook, float x) {
        Vec3 a = fromPos.subtract(fromLook);
        Vec3 b = toPos.subtract(toLook);
        double ra = Math.sqrt(a.x * a.x + a.z * a.z);
        double rb = Math.sqrt(b.x * b.x + b.z * b.z);
        double ab = Math.atan2(b.z, b.x);
        double aa = ra < 1.0E-3D ? ab : Math.atan2(a.z, a.x);
        double d = ab - aa;
        while (d > Math.PI) {
            d -= 2.0D * Math.PI;
        }
        while (d < -Math.PI) {
            d += 2.0D * Math.PI;
        }
        double ang = aa + d * x;
        double r = ra + (rb - ra) * x;
        Vec3 look = fromLook.lerp(toLook, x);
        return new Vec3(look.x + Math.cos(ang) * r, look.y + a.y + (b.y - a.y) * x, look.z + Math.sin(ang) * r);
    }

    /** 点在竖直圆柱（轴过 {@code foot}、半径 {@code r}、顶到 {@code top}，底不限）外面 */
    public static boolean outsideColumn(Vec3 p, Vec3 foot, double r, double top) {
        double dx = p.x - foot.x;
        double dz = p.z - foot.z;
        return dx * dx + dz * dz >= r * r || p.y >= top;
    }

    /**
     * 进机位弧线上的各点：离开处决者的头（球心 {@code head}、半径 {@code headR}）以后都要在目标的身体圆柱外面。
     * 起步那几点本来就在处决者眼睛里，离目标不到一格半，不算
     */
    public static boolean entryClear(Vec3 fromPos, Vec3 fromLook, Vec3 toPos, Vec3 toLook, Vec3 head, double headR,
                                     Vec3 foot, double r, double top) {
        for (int i = 1; i <= ENTRY_SAMPLES; i++) {
            Vec3 p = entryArc(fromPos, fromLook, toPos, toLook, i / (float) ENTRY_SAMPLES);
            if (p.distanceTo(head) > headR && !outsideColumn(p, foot, r, top)) {
                return false;
            }
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
