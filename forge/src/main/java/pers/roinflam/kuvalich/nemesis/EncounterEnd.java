package pers.roinflam.kuvalich.nemesis;

/**
 * 一次遭遇是怎么结束的。序号会写进离场效果包，只追加、不改号
 *
 * <p>离场效果包里另有一个特殊值 99：玄骸卡住后借赤毒雾换位置，不撤 Boss 条、不结算。它不是这里的成员。</p>
 */
public enum EncounterEnd {
    /** 玩家下线 */
    OWNER_LOGOUT,
    /** 玩家换了维度 */
    OWNER_DIMENSION,
    /** 玩家离它太远 */
    OWNER_FAR,
    /** 玩家死了，凶手是它 */
    OWNER_DIED,
    /** 玩家死了，凶手是别的东西 */
    OWNER_DIED_OTHER,
    /** 很久没有任何交手 */
    IDLE,
    /** 难度切到了和平 */
    PEACEFUL,
    /** 倒地后一直没人动手，它逃走了 */
    UNCLAIMED,
    /** 处决时判错，它挣脱逃走 */
    MERCY_FAILED,
    /** 绕过倒地的直接死亡（命令、虚空、别的模组秒杀），或没开怜悯之击时死了 */
    KILLED_OTHER,
    /** 区块卸载、关服 */
    UNLOADED,
    /** 管理命令让它离开（不进冷却） */
    ADMIN,
    /** 三张卡全对，被终结（整轮结束） */
    VANQUISHED;

    private static final EncounterEnd[] VALUES = values();

    /** 按序号取；越界返回 null（重定位的 99 也走这条） */
    public static EncounterEnd byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : null;
    }
}
