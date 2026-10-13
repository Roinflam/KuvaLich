package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 悬停「锁定括号」的限速：任意一秒里至多播 {@value #MAX} 次收拢动画
 *
 * <p>鼠标在首页格子上快速划过时，每压上一格就收一次括号，一秒能闪十几下。记下最近 {@value #MAX} 次的时刻，
 * 最早那次还在一秒以内就不放：这次悬停的括号直接静止在四角上，不做收拢（照样标出悬停的是哪一格，只是不动）。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideLockBudget {

    private static final long WINDOW_MS = 1000L;
    private static final int MAX = 3;

    /** 最近几次放行的时刻（环形，0 = 空位） */
    private final long[] recent = new long[MAX];
    private int next;

    /**
     * 要播一次收拢动画
     *
     * @param now {@code Util.getMillis()}
     * @return 放行（记下这一次）；false 表示一秒里已经播满，这次不动
     */
    boolean tryUse(long now) {
        long oldest = recent[next];
        if (oldest != 0L && now - oldest < WINDOW_MS) {
            return false;
        }
        recent[next] = now;
        next = (next + 1) % MAX;
        return true;
    }
}
