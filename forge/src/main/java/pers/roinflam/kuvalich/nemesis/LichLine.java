package pers.roinflam.kuvalich.nemesis;

/**
 * 玄骸（和 Lotus）会在什么时机说话，以及每一类台词的语言键、聊天回显级别、优先级
 *
 * <p>玄骸的台词按性格（{@link LichPersona}）分两套，键是 {@code kuvalich.comm.lich.<性格>.<场合>.<序号>}，
 * 两套每一类的条数一样多，所以抽签、「不和上一句重复」、管理命令的序号都不用管性格；Lotus 那一类不分性格，
 * 键是 {@code kuvalich.comm.lotus.<场合>.<序号>}。同一类里随机抽一条，同一个玩家连着两次不会抽到同一条。</p>
 *
 * <p>{@link #FAIL} 的第 5 条只在怜悯之击开着时进抽签池（说的是落刀），第 6 条只在关着时进（说的是被打倒又站起来）；
 * 前 4 条两种情况都能用，不提刀。这个筛选由发台词的一方做，这里列全部六个。</p>
 */
public enum LichLine {
    /** Lotus 通知玄骸诞生 */
    LOTUS_LICH_BORN(true, "lich_born", 2, false, Priority.HIGH),
    /** 玄骸诞生后的第一句 */
    BIRTH(false, "birth", 3, true, Priority.HIGH),
    /** 狩猎到场 */
    ARRIVE(false, "arrive", 5, false, Priority.HIGH),
    /** 被安魂通牒召来 */
    ULTIMATUM(false, "ultimatum", 3, false, Priority.HIGH),
    /** 被打倒 */
    DOWNED(false, "downed", 4, false, Priority.MID),
    /** 处决第一刀之后 */
    STAB(false, "stab", 3, false, Priority.MID),
    /** 处决判错 */
    FAIL(false, "fail", 6, true, Priority.HIGH),
    /** 终结时的遗言 */
    VANQUISHED(false, "vanquished", 6, true, Priority.HIGH),
    /** 倒地后没人动手，它逃走 */
    UNCLAIMED(false, "unclaimed", 3, false, Priority.HIGH),
    /** 玩家跑远、换维度、晾太久，它离开 */
    DEPART(false, "depart", 3, false, Priority.MID),
    /** 把玩家打死 */
    KILLED_YOU(false, "killed_you", 4, true, Priority.HIGH),
    /** 玩家死在别的东西手里 */
    YOU_DIED(false, "you_died", 3, true, Priority.MID),
    /** 没收物品 */
    CONFISCATE(false, "confiscate", 6, false, Priority.LOW),
    /** 狩猎中随机嘲讽 */
    IDLE(false, "idle", 8, false, Priority.LOW),
    /** 怒气很高时的随机嘲讽 */
    IDLE_FURIOUS(false, "idle_furious", 3, false, Priority.LOW),
    /** 打开档案时的一句（客户端本地抽，服务端不发） */
    ARCHIVE(false, "archive", 4, false, Priority.LOW),
    /** 被感化的那一刻，取代遗言 */
    CONVERTED(false, "converted", 5, true, Priority.HIGH),
    /** 作为盟友前来助战 */
    ALLY_ARRIVE(false, "ally_arrive", 5, false, Priority.HIGH),
    /** 盟友打死敌人后偶尔一句 */
    ALLY_KILL(false, "ally_kill", 5, false, Priority.LOW),
    /** 盟友被击倒、退去 */
    ALLY_DOWNED(false, "ally_downed", 5, false, Priority.MID),
    /** 盟友停留到时、主人脱战后退去 */
    ALLY_LEAVE(false, "ally_leave", 5, false, Priority.MID);

    /** 台词的轻重：服务端限流时，低优先级的在刚有过通讯后会被丢掉 */
    public enum Priority {
        HIGH, MID, LOW
    }

    /** 说话的是 Lotus（否则是玄骸） */
    public final boolean lotus;
    /** 按性格下标：{@code keys[性格][序号]}，Lotus 那一类两行一样 */
    private final String[][] keys;
    /** 聊天回显设为「重要」时是否也回显到聊天栏 */
    public final boolean chatEchoImportant;
    public final Priority priority;

    LichLine(boolean lotus, String name, int count, boolean chatEchoImportant, Priority priority) {
        this.lotus = lotus;
        this.chatEchoImportant = chatEchoImportant;
        this.priority = priority;
        LichPersona[] personas = LichPersona.values();
        this.keys = new String[personas.length][count];
        for (LichPersona p : personas) {
            String prefix = lotus ? "kuvalich.comm.lotus." + name : "kuvalich.comm.lich." + p.id + "." + name;
            for (int i = 0; i < count; i++) {
                keys[p.ordinal()][i] = prefix + "." + (i + 1);
            }
        }
    }

    /** 这一类一共几条（两种性格一样多） */
    public int count() {
        return keys[0].length;
    }

    /** 这种性格的第 {@code index} 条的完整语言键（{@code index} 从 0 起） */
    public String key(LichPersona persona, int index) {
        return keys[persona.ordinal()][index];
    }
}
