package pers.roinflam.kuvalich.client.gui.codex;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.utils.KuvaPalette;

/**
 * 图鉴界面的设计令牌与绘制原语
 *
 * <p>整套界面是<b>全自绘</b>的：没有任何贴图，所有质感都由若干次
 * {@code GuiGraphics.fill} 叠出来。这样做的好处是资源包换皮不会把界面画坏，
 * 也不需要为不同分辨率准备九宫格贴图。</p>
 *
 * <h3>配色为什么不照搬参考实现</h3>
 * <p>结构与绘制技法参考了 CarianStyle 的附魔百科（面板描边 + 四角装饰 + 分隔线 + 暗角）
 * 与 AlbionMastery 的专精页（四态只靠背景透明度 + 边框色 + 左侧竖条表达），
 * 但<b>配色没有照抄</b>：那两套都是金色系，那是它们自己的身份。
 * 这里的强调色取 {@link KuvaPalette#LICH}（赤毒的品红），
 * 与本模组 tooltip、聊天提示用的是同一个色，整体才是一套东西。</p>
 *
 * <h3>为什么全是直角</h3>
 * <p>原版字体是像素字体，圆角在小尺寸下只会糊成毛边。参考的两套实现也都刻意保持
 * 全直角 1px 描边，这里沿用。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class CodexTheme {

    private CodexTheme() {}

    // ==================== 配色 ====================

    /** 整屏遮罩：几乎不透明，把背后的游戏画面压下去，但不是纯黑，留一点暖调 */
    public static final int SCRIM = 0xE00A0708;

    /** 主面板底色 */
    public static final int PANEL = 0xFF14100F;

    /** 次级底色：顶栏、底栏、搜索框、分组标题行 */
    public static final int PANEL_ALT = 0xFF1C1618;

    /** 悬停态填充 */
    public static final int PANEL_HOVER = 0xFF2A2024;

    /** 默认描边与分隔线 */
    public static final int BORDER = 0xFF3A2C30;

    /** 强调色：赤毒品红，与 {@link KuvaPalette#LICH} 同源 */
    public static final int ACCENT = 0xFF000000 | KuvaPalette.LICH;

    /** 强调色半透明填充：选中底色、徽章底 */
    public static final int ACCENT_SOFT = 0x55000000 | KuvaPalette.LICH;

    /** 主文本 */
    public static final int TEXT = 0xFF000000 | KuvaPalette.VALUE;

    /** 次要文本 */
    public static final int TEXT_DIM = 0xFF000000 | KuvaPalette.MUTED;

    /** 更次要：底部提示、空态 */
    public static final int TEXT_FAINT = 0xFF6B6268;

    // ==================== 尺寸 ====================

    /** 面板四周留白 */
    public static final int MARGIN = 14;
    /** 顶栏高度（标题行 + 搜索行） */
    public static final int HEADER_H = 44;
    /** 底栏高度 */
    public static final int FOOTER_H = 24;
    /** 面板内容内边距 */
    public static final int PAD = 10;
    /** 四角装饰的臂长 */
    public static final int CORNER = 8;
    /** 滚动条宽度 */
    public static final int SCROLLBAR_W = 3;
    /** 滚动滑块最小高度 */
    public static final int THUMB_MIN_H = 16;

    // ==================== 原语 ====================

    /**
     * 把 ARGB 的 alpha 换成指定值。
     *
     * @param argb  原色
     * @param alpha 新的 alpha（0~255）
     * @return 换过 alpha 的颜色
     */
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * 按比例缩放 alpha，用于开场淡入。
     *
     * @param argb     原色
     * @param progress 0~1
     * @return 缩放后的颜色
     */
    public static int fade(int argb, float progress) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, progress)));
        return withAlpha(argb, a);
    }

    /**
     * 整屏遮罩。
     *
     * @param g        画布
     * @param w        屏宽
     * @param h        屏高
     * @param progress 开场进度 0~1
     */
    public static void scrim(GuiGraphics g, int w, int h, float progress) {
        g.fill(0, 0, w, h, fade(SCRIM, progress));
    }

    /**
     * 1px 直角描边。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param color 颜色
     */
    public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * 面板：底色 + 描边。
     *
     * @param g 画布
     * @param x 左
     * @param y 上
     * @param w 宽
     * @param h 高
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        border(g, x, y, w, h, BORDER);
    }

    /**
     * 四角 L 形装饰
     *
     * <p>比整圈亮边框克制：只在四角勾一下，既点明面板边界又不抢内容。
     * {@code reveal} 从 0 到 1 时臂长由 0 生长到满，用作开场动画。</p>
     *
     * @param g      画布
     * @param x      左
     * @param y      上
     * @param w      宽
     * @param h      高
     * @param color  颜色
     * @param reveal 生长进度 0~1
     */
    public static void corners(GuiGraphics g, int x, int y, int w, int h, int color, float reveal) {
        int len = (int) (CORNER * Math.max(0f, Math.min(1f, reveal)));
        if (len <= 0) return;
        int r = x + w, b = y + h;
        // 左上
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        // 右上
        g.fill(r - len, y, r, y + 1, color);
        g.fill(r - 1, y, r, y + len, color);
        // 左下
        g.fill(x, b - 1, x + len, b, color);
        g.fill(x, b - len, x + 1, b, color);
        // 右下
        g.fill(r - len, b - 1, r, b, color);
        g.fill(r - 1, b - len, r, b, color);
    }

    /**
     * 水平分隔线。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param color 颜色
     */
    public static void divider(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    /**
     * 暗角
     *
     * <p>用若干层同心矩形近似径向渐变，让面板从背景里「浮」出来。
     * 层数刻意压到 5 —— 逐像素做真渐变在这种尺寸上看不出差别，却要多几百次 fill。</p>
     *
     * @param g 画布
     * @param x 左
     * @param y 上
     * @param w 宽
     * @param h 高
     */
    public static void vignette(GuiGraphics g, int x, int y, int w, int h) {
        for (int i = 0; i < 5; i++) {
            int a = 22 - i * 4;
            if (a <= 0) break;
            int c = withAlpha(0xFF000000, a);
            g.fill(x + i, y + i, x + w - i, y + i + 1, c);
            g.fill(x + i, y + h - i - 1, x + w - i, y + h - i, c);
            g.fill(x + i, y + i + 1, x + i + 1, y + h - i - 1, c);
            g.fill(x + w - i - 1, y + i + 1, x + w - i, y + h - i - 1, c);
        }
    }

    /**
     * 小徽章：一段带底色的短文本，用于「已发现/总数」这类计数。
     *
     * @param g    画布
     * @param font 字体
     * @param text 文本
     * @param x    左
     * @param y    上
     * @param bg   底色
     * @param fg   字色
     * @return 徽章总宽度，方便调用方继续排版
     */
    public static int badge(GuiGraphics g, Font font, String text, int x, int y, int bg, int fg) {
        int w = font.width(text) + 8;
        int h = font.lineHeight + 4;
        g.fill(x, y, x + w, y + h, bg);
        border(g, x, y, w, h, withAlpha(fg, 0x40));
        g.drawString(font, text, x + 4, y + 3, fg, false);
        return w;
    }

    /**
     * 滚动条：轨道 + 滑块。
     *
     * @param g         画布
     * @param x         左
     * @param y         上
     * @param trackH    轨道高
     * @param scroll    当前滚动量
     * @param maxScroll 最大滚动量（&lt;=0 时不画）
     */
    public static void scrollbar(GuiGraphics g, int x, int y, int trackH, double scroll, double maxScroll) {
        if (maxScroll <= 0) return;
        g.fill(x, y, x + SCROLLBAR_W, y + trackH, withAlpha(BORDER, 0x80));
        double visibleRatio = trackH / (trackH + maxScroll);
        int thumbH = Math.max(THUMB_MIN_H, (int) (trackH * visibleRatio));
        int thumbY = y + (int) ((trackH - thumbH) * (scroll / maxScroll));
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, withAlpha(ACCENT, 0xC0));
    }

    /**
     * 缓出三次方，用于开场动画。
     *
     * @param t 0~1
     * @return 缓动后的 0~1
     */
    public static float easeOutCubic(float t) {
        float c = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - c * c * c;
    }
}
