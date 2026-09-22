package pers.roinflam.kuvalich.client.gui.codex;

import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * 图鉴格子上的一次性反馈动画（客户端）
 *
 * <p>三种反馈共用一张表，按 {@code discoveryKey} 索引：</p>
 * <ul>
 *   <li>{@link Kind#INSTALLED} —— 一键装配成功</li>
 *   <li>{@link Kind#REJECTED} —— 装不上（没空位 / 冲突 / 军械库里没放武器）</li>
 *   <li>{@link Kind#UNLOCKED} —— 这个模组刚刚被首次发现</li>
 * </ul>
 *
 * <p><b>为什么需要 REJECTED。</b>改造前一键装配失败是完全静默的：服务端判定装不上就
 * 直接 return，客户端连「请求被拒绝了」都不知道，玩家只会觉得「点了没反应」。
 * 现在服务端无论成功失败都回一个结果包，界面上才有得画。</p>
 *
 * <p><b>为什么用静态表而不是塞进 Screen。</b>反馈可能在图鉴关着的时候产生
 * （比如捡起一张没见过的卡），也可能在图鉴重新打开后才被看到；而 Screen 实例
 * 每次打开都是新的。放在静态表里，谁打开谁就能画。</p>
 *
 * <p>表是自清理的：{@link #peek} 每次访问都会顺手丢掉过期项，条目数也有上限兜底，
 * 所以不会随着游戏时长无限增长。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class CodexFeedback {

    private CodexFeedback() {}

    /** 反馈类型 */
    public enum Kind {
        /** 装配成功 */
        INSTALLED,
        /** 装不上 */
        REJECTED,
        /** 首次发现 */
        UNLOCKED
    }

    /** 每种反馈的动画时长（毫秒） */
    private static final long INSTALLED_MS = 520L;
    private static final long REJECTED_MS = 420L;
    private static final long UNLOCKED_MS = 900L;

    /**
     * 表的容量上限
     *
     * <p>正常情况下同时活跃的反馈只有个位数，但「给玩家一次性塞一整套模组」这类操作
     * 可能一瞬间推进来几百条。超过上限就整表清空 —— 反馈是纯观感，丢掉不影响任何逻辑，
     * 而让一张只读不写的表慢慢涨到几千条不值得。</p>
     */
    private static final int MAX_ENTRIES = 256;

    /** 一条活跃的反馈 */
    public static final class Entry {
        /** 类型 */
        public final Kind kind;
        /** 起始时间 */
        public final long startedAt;

        Entry(Kind kind, long startedAt) {
            this.kind = kind;
            this.startedAt = startedAt;
        }

        /**
         * 这条反馈的总时长。
         *
         * @return 毫秒
         */
        public long duration() {
            switch (kind) {
                case INSTALLED: return INSTALLED_MS;
                case REJECTED: return REJECTED_MS;
                default: return UNLOCKED_MS;
            }
        }

        /**
         * 动画进度。
         *
         * @param now 当前时间
         * @return 0~1
         */
        public float progress(long now) {
            long dt = now - startedAt;
            if (dt <= 0) return 0f;
            long dur = duration();
            return dt >= dur ? 1f : (float) dt / dur;
        }
    }

    private static final Map<String, Entry> ACTIVE = new HashMap<>();

    /**
     * 推入一条反馈。同一个 key 的旧反馈会被覆盖 —— 玩家连点时只看最后一次。
     *
     * @param discoveryKey 模组的发现键（{@code type:rarityOrder}）
     * @param kind         反馈类型
     */
    public static void push(String discoveryKey, Kind kind) {
        if (discoveryKey == null || discoveryKey.isEmpty()) return;
        if (ACTIVE.size() >= MAX_ENTRIES) {
            ACTIVE.clear();
        }
        ACTIVE.put(discoveryKey, new Entry(kind, Util.getMillis()));
    }

    /**
     * 取一条仍在播放中的反馈；顺手清掉过期项。
     *
     * @param discoveryKey 模组的发现键
     * @param now          当前时间
     * @return 活跃的反馈；没有或已播完返回 null
     */
    public static Entry peek(String discoveryKey, long now) {
        if (ACTIVE.isEmpty() || discoveryKey == null) return null;
        Entry e = ACTIVE.get(discoveryKey);
        if (e == null) return null;
        if (now - e.startedAt >= e.duration()) {
            ACTIVE.remove(discoveryKey);
            return null;
        }
        return e;
    }

    /**
     * 表里还有没有活跃项
     *
     * <p>给渲染端做快速短路用：绝大多数帧这里是空的，空表时连 key 都不必去查。</p>
     *
     * @return 是否为空
     */
    public static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    /** 退出世界时清空，避免跨存档残留。 */
    public static void clear() {
        ACTIVE.clear();
    }
}
