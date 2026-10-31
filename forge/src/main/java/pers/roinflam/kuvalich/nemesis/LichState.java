package pers.roinflam.kuvalich.nemesis;

/**
 * 一位玩家的玄骸现在处在什么阶段。序号会写进同步包，只追加、不改号
 *
 * <p>多数状态是从卡上的数据推出来的，只有 {@link #PRESENT} 会存一个「遭遇进行中」的记号。</p>
 */
public enum LichState {
    /** 没有玄骸（还没揭示第一条密语，或者上一只已被终结） */
    UNBORN,
    /** 有玄骸、不在场、不在冷却、怒气没满 */
    STALKING,
    /** 有玄骸、不在场、冷却还没过 */
    COOLDOWN,
    /** 怒气满了、冷却已过，等着到场（也可能条件不满足在等，原因见 {@link WaitReason}） */
    INCOMING,
    /** 通讯已经发出，等一小会儿再现身 */
    ARRIVING,
    /** 实体在世界里 */
    PRESENT,
    /** 实体倒地，等着被处决 */
    DOWNED,
    /** 离场演出中 */
    DEPARTING;

    private static final LichState[] VALUES = values();

    /** 按序号取；越界按 {@link #UNBORN} */
    public static LichState byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : UNBORN;
    }
}
