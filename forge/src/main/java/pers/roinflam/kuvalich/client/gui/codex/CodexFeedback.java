package pers.roinflam.kuvalich.client.gui.codex;

import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * 图鉴格子上的一次性反馈动画（客户端）
 *
 * <p>五种反馈共用一张表，按 {@code discoveryKey} 索引：</p>
 * <ul>
 *   <li>{@link Kind#INSTALLED} —— Shift+点击一键装配成功</li>
 *   <li>{@link Kind#REJECTED} —— 装不上（没空位 / 冲突 / 军械库里没放武器）</li>
 *   <li>{@link Kind#TAKEN} —— 左键点击，已放进背包</li>
 *   <li>{@link Kind#DROPPED} —— 左键点击但背包满了，掉在脚下</li>
 *   <li>{@link Kind#UNLOCKED} —— 这个模组刚刚被首次发现</li>
 * </ul>
 *
 * <p><b>为什么这些都需要反馈。</b>改造前这几条路径在服务端都是「做完就 return」：
 * 装不上是完全静默的，背包满了掉在脚下也完全看不出发生了什么 ——
 * 玩家看到的都是「点了没反应」。现在服务端每条路径都回一个结果包，界面上才有得画。</p>
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
        /** 已放进背包 */
        TAKEN,
        /** 背包满了，掉在脚下 */
        DROPPED,
        /** 首次发现 */
        UNLOCKED
    }

    /** 每种反馈的动画时长（毫秒） */
    private static final long INSTALLED_MS = 520L;
    private static final long REJECTED_MS = 420L;
    private static final long TAKEN_MS = 340L;
    private static final long DROPPED_MS = 520L;
    private static final long UNLOCKED_MS = 900L;

    /**
     * 表的容量上限
     *
     * <p>正常情况下同时活跃的反馈只有个位数，但「给玩家一次性塞一整套模组」这类操作
     * 可能一瞬间推进来几百条。超过上限就整表清空 —— 反馈是纯观感，丢掉不影响任何逻辑，
     * 而让一张只读不写的表慢慢涨到几千条不值得。</p>
     */
    private static final int MAX_ENTRIES = 256;

    /**
     * 反馈被推入后、还没被看到之前能等多久
     *
     * <p>计时是「第一次真正画出来」才开始的，所以一条始终没被看到的反馈本身不会过期。
     * 给它一个兜底上限，免得玩家装配完就关掉图鉴、这条反馈一直挂在表里，
     * 过很久之后再打开图鉴时莫名其妙地播一下。</p>
     */
    private static final long UNSEEN_TTL_MS = 30_000L;

    /** 一条活跃的反馈 */
    public static final class Entry {
        /** 类型 */
        public final Kind kind;
        /** 推入时间，用于给「一直没被看到」的反馈兜底过期 */
        final long pushedAt;
        /**
         * 动画开始时间；0 表示还没被真正画出来过
         *
         * <p>⭐ 不是推入时间。反馈动画只在格子<b>可见</b>时才画，而玩家完全可能
         * 装配完立刻滚走、或者改搜索词把这个条目筛掉 —— 按推入时间计时的话，
         * 动画会在玩家看不到的地方悄悄播完然后彻底消失，等滚回来什么都没有了。
         * 首次解锁尤其可惜，那是图鉴里最值得被看到的一刻。</p>
         */
        long startedAt;

        Entry(Kind kind, long pushedAt) {
            this.kind = kind;
            this.pushedAt = pushedAt;
            this.startedAt = 0L;
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
                case TAKEN: return TAKEN_MS;
                case DROPPED: return DROPPED_MS;
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
     * 推入一条反馈
     *
     * <p>同一个 key 的旧反馈一般会被覆盖（玩家连点时只看最后一次），
     * 唯一的例外是正在播放的解锁动画 —— 见方法体里的说明。</p>
     *
     * @param discoveryKey 模组的发现键（{@code type:rarityOrder}）
     * @param kind         反馈类型
     */
    public static void push(String discoveryKey, Kind kind) {
        if (discoveryKey == null || discoveryKey.isEmpty()) return;
        if (ACTIVE.size() >= MAX_ENTRIES) {
            ACTIVE.clear();
        }
        long now = Util.getMillis();

        // ⭐ 解锁优先：第一次拿到某个模组时，「拿到了」和「解锁了」会几乎同时到达
        //    （服务端先给物品再记发现，两个包紧挨着发）。两者都往同一个 key 上写，
        //    后到的会盖掉先到的 —— 而「首次解锁」显然是更值得看的那一个。
        //    所以只要解锁动画还在播，就不让别的类型把它顶掉。
        Entry existing = ACTIVE.get(discoveryKey);
        if (kind != Kind.UNLOCKED && existing != null && existing.kind == Kind.UNLOCKED
                && (existing.startedAt == 0L || now - existing.startedAt < existing.duration())) {
            return;
        }
        ACTIVE.put(discoveryKey, new Entry(kind, now));
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

        if (e.startedAt == 0L) {
            // 这一帧是它第一次真正被画出来 —— 现在才开始计时，保证至少完整播一遍
            if (now - e.pushedAt >= UNSEEN_TTL_MS) {
                ACTIVE.remove(discoveryKey);
                return null;
            }
            e.startedAt = now;
            return e;
        }
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
