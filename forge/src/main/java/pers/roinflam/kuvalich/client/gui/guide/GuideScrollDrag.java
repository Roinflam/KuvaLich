package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;

/**
 * 可拖动滚动条的按下 / 拖动换算（左栏目录、首页格子区用）
 *
 * <p>同一个界面里右栏正文的滚动条能点轨道、能拖滑块，左栏和首页的看起来一模一样却按不动，
 * 玩家会以为界面卡住了。手感照 {@link GuideReader} 那条：点在轨道空白处滑块中心直接跳过去，
 * 按住滑块拖动时按轨道剩余长度等比滚动。滑块尺寸的算法必须和 {@link CodexTheme#scrollbar} 一致，
 * 否则点中的位置和画出来的滑块对不上。</p>
 *
 * <p>只管换算和「是否正在拖」，不存滚动值：滚动值留在各自的部件里，这里算出来由调用方写回。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideScrollDrag {

    /** 命中区宽度：滚动条只有 3px 宽，照原样判定太难点中，按它所在的那条留白算 */
    static final int HIT_W = 6;

    private boolean dragging;
    private double startY;
    private float startScroll;

    boolean dragging() {
        return dragging;
    }

    /**
     * 在滚动条上按下左键（调用方已判定命中）
     *
     * @param my     鼠标 y
     * @param trackY 轨道顶
     * @param trackH 轨道高
     * @param scroll 当前滚动
     * @param max    最大滚动（&gt; 0）
     * @return 按下后的滚动位置
     */
    float press(double my, int trackY, int trackH, float scroll, float max) {
        int thumbH = thumbH(trackH, max);
        int thumbY = trackY + (int) ((trackH - thumbH) * (scroll / max));
        if (my < thumbY || my >= thumbY + thumbH) {
            double t = (my - trackY - thumbH / 2.0) / Math.max(1, trackH - thumbH);
            scroll = (float) Math.max(0, Math.min(max, t * max));
        }
        dragging = true;
        startY = my;
        startScroll = scroll;
        return scroll;
    }

    /**
     * 拖动中
     *
     * @param my     鼠标 y
     * @param trackH 轨道高
     * @param max    最大滚动
     * @return 新的滚动位置
     */
    float drag(double my, int trackH, float max) {
        if (max <= 0) {
            return 0f;
        }
        float perPixel = max / Math.max(1, trackH - thumbH(trackH, max));
        return (float) Math.max(0, Math.min(max, startScroll + (my - startY) * perPixel));
    }

    void release() {
        dragging = false;
    }

    private static int thumbH(int trackH, float max) {
        return Math.max(CodexTheme.THUMB_MIN_H, (int) (trackH * (trackH / (trackH + max))));
    }
}
