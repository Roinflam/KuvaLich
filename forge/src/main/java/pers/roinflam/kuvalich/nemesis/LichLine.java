package pers.roinflam.kuvalich.nemesis;

/**
 * 玄骸（和 Lotus）会在什么时机说话，以及每一类台词的语言键、聊天回显级别、优先级
 *
 * <p>同一类里随机抽一条，同一个玩家连着两次不会抽到同一条。键有两种写法：带数字后缀的
 * （{@code 前缀.1} ~ {@code 前缀.N}），和各自起名的（没收、遗言）。</p>
 *
 * <p>{@link #FAIL} 的 {@code fail.5} 只在怜悯之击开着时进抽签池，{@code fail.6} 只在关着时进；
 * 这个筛选由发台词的一方做，这里列全部六个。</p>
 */
public enum LichLine {
    /** Lotus 通知玄骸诞生 */
    LOTUS_LICH_BORN(true, numbered("kuvalich.comm.lotus.lich_born", 2), false, Priority.HIGH),
    /** 玄骸诞生后的第一句 */
    BIRTH(false, numbered("kuvalich.comm.lich.birth", 3), true, Priority.HIGH),
    /** 狩猎到场 */
    ARRIVE(false, numbered("kuvalich.comm.lich.arrive", 5), false, Priority.HIGH),
    /** 被安魂通牒召来 */
    ULTIMATUM(false, numbered("kuvalich.comm.lich.ultimatum", 3), false, Priority.HIGH),
    /** 被打倒 */
    DOWNED(false, numbered("kuvalich.comm.lich.downed", 4), false, Priority.MID),
    /** 处决第一刀之后 */
    STAB(false, numbered("kuvalich.comm.lich.stab", 3), false, Priority.MID),
    /** 处决判错 */
    FAIL(false, numbered("kuvalich.comm.lich.fail", 6), true, Priority.HIGH),
    /** 终结时的遗言 */
    VANQUISHED(false, named("message.kuvalich.death.",
            "hurts", "fair", "nexttime", "winsome", "comeon", "failed", "unacceptable", "forgive", "next"),
            true, Priority.HIGH),
    /** 倒地后没人动手，它逃走 */
    UNCLAIMED(false, numbered("kuvalich.comm.lich.unclaimed", 3), false, Priority.HIGH),
    /** 玩家跑远、换维度、晾太久，它离开 */
    DEPART(false, numbered("kuvalich.comm.lich.depart", 3), false, Priority.MID),
    /** 把玩家打死 */
    KILLED_YOU(false, numbered("kuvalich.comm.lich.killed_you", 4), true, Priority.HIGH),
    /** 玩家死在别的东西手里 */
    YOU_DIED(false, numbered("kuvalich.comm.lich.you_died", 2), true, Priority.MID),
    /** 没收物品 */
    CONFISCATE(false, named("message.kuvalich.confiscation.",
            "wallet", "tax", "something", "closer", "yoink", "sector", "tithe", "coin", "accepted", "toll"),
            false, Priority.LOW),
    /** 狩猎中随机嘲讽 */
    IDLE(false, numbered("kuvalich.comm.lich.idle", 6), false, Priority.LOW),
    /** 怒气很高时的随机嘲讽 */
    IDLE_FURIOUS(false, numbered("kuvalich.comm.lich.idle_furious", 2), false, Priority.LOW),
    /** 打开档案时的一句（客户端本地抽，服务端不发） */
    ARCHIVE(false, numbered("kuvalich.comm.lich.archive", 4), false, Priority.LOW);

    /** 台词的轻重：服务端限流时，低优先级的在刚有过通讯后会被丢掉 */
    public enum Priority {
        HIGH, MID, LOW
    }

    /** 说话的是 Lotus（否则是玄骸） */
    public final boolean lotus;
    private final String[] keys;
    /** 聊天回显设为「重要」时是否也回显到聊天栏 */
    public final boolean chatEchoImportant;
    public final Priority priority;

    LichLine(boolean lotus, String[] keys, boolean chatEchoImportant, Priority priority) {
        this.lotus = lotus;
        this.keys = keys;
        this.chatEchoImportant = chatEchoImportant;
        this.priority = priority;
    }

    /** 这一类一共几条 */
    public int count() {
        return keys.length;
    }

    /** 第 {@code index} 条的完整语言键（{@code index} 从 0 起） */
    public String key(int index) {
        return keys[index];
    }

    private static String[] numbered(String prefix, int n) {
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = prefix + "." + (i + 1);
        }
        return out;
    }

    private static String[] named(String prefix, String... names) {
        String[] out = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = prefix + names[i];
        }
        return out;
    }
}
