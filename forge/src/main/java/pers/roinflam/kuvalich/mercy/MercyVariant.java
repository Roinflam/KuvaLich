package pers.roinflam.kuvalich.mercy;

import org.jetbrains.annotations.Nullable;

/**
 * 处决的套路：站位、目标朝向、机位和每一刀刺入的时刻
 *
 * <p>编号写进计划位的 bit 4~7，只追加不改：玄骸 0~2，奴仆 8~10。站位距离是初值，
 * 以「刀尖刚好没入胸口 / 后背、两具模型不明显穿插」为准，摆出来看过再调。</p>
 *
 * <p>时间都是毫秒，从处决开始那一帧算。玄骸的整条时间轴长短取决于结果（成功、判错、单刀），
 * 所以 {@link #totalMs} 对玄骸是 -1；奴仆固定 2200。</p>
 */
public enum MercyVariant {
    /** 玄骸：正面直刺 */
    LICH_A(0, false, "a", 1.25F, Facing.TOWARD, 0, new int[]{790, 1680, 2670}, -1),
    /** 玄骸：背后锁颈 */
    LICH_B(1, false, "b", 1.10F, Facing.AWAY, 1, new int[]{790, 1680, 2670}, -1),
    /** 玄骸：贴身上挑 */
    LICH_C(2, false, "c", 0.95F, Facing.TOWARD, 2, new int[]{790, 1680, 2670}, -1),
    /** 奴仆：抓肩直刺 */
    THRALL_1(8, true, "t1", 1.00F, Facing.TOWARD, -1, new int[]{900}, 2200),
    /** 奴仆：锁喉背刺 */
    THRALL_2(9, true, "t2", 0.95F, Facing.AWAY, -1, new int[]{950}, 2200),
    /** 奴仆：压颈下刺 */
    THRALL_3(10, true, "t3", 1.00F, Facing.SIDE, -1, new int[]{1000}, 2200);

    /** 目标在处决开始那一刻被转向哪里（服务端和客户端同时设） */
    public enum Facing {
        /** 面向处决者 */
        TOWARD,
        /** 背对处决者 */
        AWAY,
        /**
         * 侧身，处决者在它左手边：朝向 −S（S = Y × F，F 是目标指向处决者的水平方向）。
         * 处决者往前一推，它往自己右手边侧翻、倒向离开处决者的一侧，和压颈下刺的倒地动画同向
         */
        SIDE
    }

    /** 玄骸三刀里每一格「填满发光」的时刻（比刺入晚一点）：第 1、2、3 格 */
    public static final int[] LICH_FILL_MS = {830, 1680, 2670};

    private static final MercyVariant[] VALUES = values();

    /** 计划位里的编号 */
    public final int id;
    public final boolean thrall;
    /** 动画片段名里用的后缀：玄骸 a / b / c，奴仆 t1 / t2 / t3 */
    public final String animKey;
    /** 处决者离目标多远（沿处决者到目标的水平方向，格） */
    public final float standDist;
    public final Facing facing;
    /** 三刀机位编号：0 = A 侧面中近景、1 = B 过肩近景、2 = C 贴身仰拍；奴仆不切镜头为 -1 */
    public final int camera;
    private final int[] stabMs;
    /** 整条时间轴的长度，玄骸取决于结果所以为 -1 */
    public final int totalMs;

    MercyVariant(int id, boolean thrall, String animKey, float standDist, Facing facing, int camera,
                 int[] stabMs, int totalMs) {
        this.id = id;
        this.thrall = thrall;
        this.animKey = animKey;
        this.standDist = standDist;
        this.facing = facing;
        this.camera = camera;
        this.stabMs = stabMs;
        this.totalMs = totalMs;
    }

    /** 每一刀刺入的时刻（副本）。玄骸三个，奴仆一个 */
    public int[] stabMs() {
        return stabMs.clone();
    }

    /** 第 {@code i} 刀（从 0 起）刺入的时刻；越界返回 -1 */
    public int stabMs(int i) {
        return i >= 0 && i < stabMs.length ? stabMs[i] : -1;
    }

    /** 按编号取；不认识的编号返回 {@code null} */
    @Nullable
    public static MercyVariant byId(int id) {
        for (MercyVariant v : VALUES) {
            if (v.id == id) {
                return v;
            }
        }
        return null;
    }
}
