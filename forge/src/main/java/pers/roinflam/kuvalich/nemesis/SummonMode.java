package pers.roinflam.kuvalich.nemesis;

/**
 * 让玄骸立刻现身的几种方式
 */
public enum SummonMode {
    /** 安魂通牒：先通讯，再在玩家面前现身 */
    ULTIMATUM,
    /** 管理命令：直接落在玩家面前 */
    FRONT,
    /** 管理命令：按狩猎的落点规则找位置 */
    HUNT
}
