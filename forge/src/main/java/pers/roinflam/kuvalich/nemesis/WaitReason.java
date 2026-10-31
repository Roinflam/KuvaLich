package pers.roinflam.kuvalich.nemesis;

/**
 * 怒气已满、到点了却还没来的原因。序号会写进同步包，只追加、不改号
 */
public enum WaitReason {
    /** 没在等 */
    NONE,
    /** 玩家所在维度不在它的狩猎范围里 */
    DIMENSION,
    /** 和平难度 */
    PEACEFUL,
    /** 创造或旁观模式（且没开 lichHuntCreative） */
    CREATIVE,
    /** 玩家在睡觉、骑乘、正处决或已经死亡 */
    BUSY,
    /** 刚登录，还在宽限时间里 */
    GRACE;

    private static final WaitReason[] VALUES = values();

    /** 按序号取；越界按 {@link #NONE} */
    public static WaitReason byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : NONE;
    }
}
