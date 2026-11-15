package pers.roinflam.kuvalich.mercy;

import org.jetbrains.annotations.Nullable;

/**
 * 处决的套路：站位、目标朝向、机位
 *
 * <p>编号写进计划位的 bit 4~7，只追加不改：玄骸 0~7，奴仆 8~15。站位距离是初值，
 * 以「刀尖刚好没入胸口 / 后背、两具模型不明显穿插」为准，摆出来看过再调。</p>
 *
 * <p>刀时刻和其余节拍在 {@link MercyBeats}。玄骸的整条时间轴长短取决于结果（成功、第几刀判错），
 * 所以 {@link #totalMs} 对玄骸是 -1；奴仆固定 2200。</p>
 */
public enum MercyVariant {
    /** 玄骸：正面直刺 */
    LICH_A(0, false, "a", 1.25F, Facing.TOWARD, 0, -1),
    /** 玄骸：背后锁颈 */
    LICH_B(1, false, "b", 1.10F, Facing.AWAY, 1, -1),
    /** 玄骸：贴身上挑 */
    LICH_C(2, false, "c", 0.95F, Facing.TOWARD, 2, -1),
    /** 玄骸：翻滚绕背，前两刀在正面，翻到身后捅第三刀 */
    LICH_D(3, false, "d", 1.25F, Facing.TOWARD, 3, -1),
    /** 玄骸：踏背下刺，背后捅一刀，跳上后背往后颈扎一刀，化作一道红光闪到正面捅第三刀，蹬开后空翻 */
    LICH_E(4, false, "e", 1.10F, Facing.AWAY, 4, -1),
    /** 玄骸：折翼，攥住它挥过来的手腕荡到它右后方，拧着胳膊捅两刀，踩着后腰窜到它右侧，从腋下往上送第三刀 */
    LICH_F(5, false, "f", 1.25F, Facing.TOWARD, 5, -1, -28.0F),
    /** 奴仆：抓肩直刺 */
    THRALL_1(8, true, "t1", 1.00F, Facing.TOWARD, -1, 2200),
    /** 奴仆：锁喉背刺 */
    THRALL_2(9, true, "t2", 0.95F, Facing.AWAY, -1, 2200),
    /** 奴仆：压颈下刺 */
    THRALL_3(10, true, "t3", 1.00F, Facing.SIDE, -1, 2200),
    /** 奴仆：穿胸绕背，捅进胸口后翻滚到身后，收尾时人在它背后 */
    THRALL_4(11, true, "t4", 1.00F, Facing.TOWARD, -1, 2200),
    /** 奴仆：飞跃下刺，从它头顶翻过去、倒着往后颈扎一刀，落在身后 */
    THRALL_5(12, true, "t5", 1.25F, Facing.TOWARD, -1, 2200);

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

    private static final MercyVariant[] VALUES = values();

    /** 计划位里的编号 */
    public final int id;
    public final boolean thrall;
    /** 动画片段名里用的后缀：玄骸 a～f，奴仆 t1～t5 */
    public final String animKey;
    /** 处决者离目标多远（沿处决者到目标的水平方向，格） */
    public final float standDist;
    public final Facing facing;
    /** 三刀机位编号：0 = A 侧面中近景、1 = B 过肩近景、2 = C 贴身仰拍、3 = D 侧面远一点、4 = E 低机位仰拍、5 = F 它右前方偏低；奴仆不切镜头为 -1 */
    public final int camera;
    /** 整条时间轴的长度，玄骸取决于结果所以为 -1 */
    public final int totalMs;
    /**
     * 面向处决者的套路里，目标再偏开多少度：处决者落在它正前方往右手边（负）或左手边（正）这么多度的地方。
     * 成功切特写、挣开站起时转正
     */
    public final float faceTurn;

    MercyVariant(int id, boolean thrall, String animKey, float standDist, Facing facing, int camera, int totalMs) {
        this(id, thrall, animKey, standDist, facing, camera, totalMs, 0.0F);
    }

    MercyVariant(int id, boolean thrall, String animKey, float standDist, Facing facing, int camera, int totalMs,
                 float faceTurn) {
        this.id = id;
        this.thrall = thrall;
        this.animKey = animKey;
        this.standDist = standDist;
        this.facing = facing;
        this.camera = camera;
        this.totalMs = totalMs;
        this.faceTurn = faceTurn;
    }

    /** 每一刀刺入的时刻（副本）。玄骸三个，奴仆一个 */
    public int[] stabMs() {
        return MercyBeats.stabMs(this);
    }

    /** 第 {@code i} 刀（从 0 起）刺入的时刻；越界返回 -1 */
    public int stabMs(int i) {
        if (thrall) {
            return i == 0 ? MercyBeats.thrall(this).stab : -1;
        }
        return i >= 0 && i < 3 ? MercyBeats.lich(this).stab(i) : -1;
    }

    /** 要绕着目标走半圈（路上得有地方），绕哪一边由服务端看地形定，写进计划位 */
    public boolean orbits() {
        return this == LICH_D || this == THRALL_4;
    }

    /** 收尾时人真的换到了目标身后（玄骸的几套都回到原站位，整身动作只是模型在动） */
    public boolean relocates() {
        return this == THRALL_4 || this == THRALL_5;
    }

    /** 跳到目标上方、最后落到它正面（离站位最远的那一侧），头顶和正面都得空着 */
    public boolean vaultsOver() {
        return this == LICH_E;
    }

    /** 开场不正对处决者（背对、侧身或偏开一个角度），成功切特写、挣开站起时要转过来 */
    public boolean turnsToFace() {
        return facing == Facing.AWAY || faceTurn != 0.0F;
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
