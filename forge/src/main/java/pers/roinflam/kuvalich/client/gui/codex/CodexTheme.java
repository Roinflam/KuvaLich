package pers.roinflam.kuvalich.client.gui.codex;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 图鉴界面的视觉语言：军械库全息终端
 *
 * <h3>为什么不是一个深色矩形加描边</h3>
 * <p>第一版就是那样，观感像配置界面。军械库那套科技感的要点不在「深色」，而在四件事：</p>
 * <ol>
 *   <li><b>斜切几何</b> —— 框体没有直角。角是斜切的，边上有平行四边形的缺口，
 *       线条终止于斜角而不是戛然而止。</li>
 *   <li><b>精准细线</b> —— 强调靠 1px 的锐线与它外溢的一点辉光，不靠大色块。
 *       界面大部分面积是空的，信息密度由线条组织，而不是靠填色划分。</li>
 *   <li><b>全息表面</b> —— 扫描线 + 极低对比的网格，让平涂的板材看起来是投影出来的，
 *       而不是一块塑料。</li>
 *   <li><b>冷底暖芯</b> —— 底色是冷的近黑带蓝，唯一的暖色是赤毒猩红，
 *       所以猩红一出现就是焦点。全屏只允许一个焦点色。</li>
 * </ol>
 *
 * <h3>实现约束</h3>
 * <p>{@code GuiGraphics.fill} 只能画轴对齐矩形，所以斜边一律用 1px 矩形堆成阶梯。
 * 在像素字体的尺度下阶梯本身就是对的观感，不需要抗锯齿。辉光用同一条线向外叠几层
 * 递减 alpha 近似，比真高斯便宜得多，暗底上肉眼分不出差别。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class CodexTheme {

    private CodexTheme() {}

    // ==================== 配色 ====================
    // 冷底暖芯：底色一律偏蓝的近黑，全屏唯一的暖色是赤毒猩红。
    // 这样猩红不需要很大面积就能成为焦点，而不是靠亮度去压别的元素。

    /** 整屏遮罩 */
    public static final int VOID = 0xF2060809;

    /** 主板材：全息投影的底 */
    public static final int PLATE = 0xFF0C1013;

    /** 高一层的板材：顶栏、底栏 */
    public static final int PLATE_HI = 0xFF121820;

    /** 凹陷：格子底，比主板材更暗，制造「嵌进去」的层次 */
    public static final int PLATE_DEEP = 0xFF070A0C;

    /** 结构线：板材之间的接缝，冷灰蓝 */
    public static final int EDGE = 0xFF2A3A44;

    /** 科技青：结构性发光线、刻度、数据元素。撑起「科技感」的主力，但绝不抢焦点。 */
    public static final int TECH = 0xFF4FD6E8;

    /** 赤毒猩红：全屏唯一焦点色，只给「选中/悬停/当前」这类真正需要被看到的东西 */
    public static final int KUVA = 0xFFFF3B52;

    /** 余烬橙：次强调，计数与警示 */
    public static final int EMBER = 0xFFFFA24D;

    /** 主文本 */
    public static final int BONE = 0xFFDCE6EA;

    /** 次要文本 */
    public static final int ASH = 0xFF8496A0;

    /** 更次要：底部提示、空态 */
    public static final int FAINT = 0xFF4E5E68;

    // ==================== 尺寸 ====================

    /** 面板四周留白 */
    public static final int MARGIN = 14;
    /**
     * 顶栏高度
     *
     * <p>从 46 降到 34：搜索框原先单独占第二行，现在与计数徽章同排靠右，
     * 顶栏只需要容下一行内容。省下的 12px 全部还给网格。</p>
     */
    public static final int HEADER_H = 34;
    /** 底栏高度 */
    public static final int FOOTER_H = 24;
    /** 内边距 */
    public static final int PAD = 12;
    /** 主框体切角边长 */
    public static final int CHAMFER = 12;
    /** 滚动条宽度 */
    public static final int SCROLLBAR_W = 3;
    /** 滚动滑块最小高度 */
    public static final int THUMB_MIN_H = 18;
    /** 数据微粒数量。再多就从「有气氛」变成「有噪点」了。 */
    public static final int MOTES = 16;

    // ==================== 颜色工具 ====================

    /**
     * 换 alpha。
     *
     * @param argb  原色
     * @param alpha 0~255
     * @return 新颜色
     */
    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha))) << 24);
    }

    /**
     * 按比例缩放 alpha。
     *
     * @param argb 原色
     * @param f    0~1
     * @return 新颜色
     */
    public static int fade(int argb, float f) {
        return withAlpha(argb, (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f))));
    }

    /**
     * 缓出三次方。
     *
     * @param t 0~1
     * @return 缓动值
     */
    public static float easeOutCubic(float t) {
        float c = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - c * c * c;
    }

    // ==================== 框体 ====================

    /**
     * 直角 1px 描边
     *
     * <p>大框体一律用斜切，但 18px 见方的槽位不能 —— 在那个尺度上切角会把四个角
     * 吃掉将近一半，凹槽会糊成一个八边形。小元件用直角，大框体用斜切，
     * 这个对比本身也是军械库那套语言的一部分。</p>
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param color 线色
     */
    public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * 斜切板材：填充一个四角被斜切掉的矩形。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param cut   切角边长
     * @param color 填充色
     */
    public static void chamferFill(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x, y + cut, x + w, y + h - cut, color);
        for (int i = 0; i < cut; i++) {
            int inset = cut - i;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /**
     * 斜切描边：沿斜切矩形的轮廓画 1px 线。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param cut   切角边长
     * @param color 线色
     */
    public static void chamferOutline(GuiGraphics g, int x, int y, int w, int h, int cut, int color) {
        g.fill(x + cut, y, x + w - cut, y + 1, color);
        g.fill(x + cut, y + h - 1, x + w - cut, y + h, color);
        g.fill(x, y + cut, x + 1, y + h - cut, color);
        g.fill(x + w - 1, y + cut, x + w, y + h - cut, color);
        for (int i = 0; i < cut; i++) {
            int inset = cut - i;
            g.fill(x + inset - 1, y + i, x + inset, y + i + 1, color);
            g.fill(x + w - inset, y + i, x + w - inset + 1, y + i + 1, color);
            g.fill(x + inset - 1, y + h - i - 1, x + inset, y + h - i, color);
            g.fill(x + w - inset, y + h - i - 1, x + w - inset + 1, y + h - i, color);
        }
    }

    /**
     * 发光的斜切描边：轮廓外再叠两层递减 alpha。
     *
     * @param g        画布
     * @param x        左
     * @param y        上
     * @param w        宽
     * @param h        高
     * @param cut      切角边长
     * @param color    线色
     * @param strength 辉光强度 0~1
     */
    public static void chamferGlow(GuiGraphics g, int x, int y, int w, int h, int cut, int color, float strength) {
        for (int layer = 2; layer >= 1; layer--) {
            int a = (int) (40 * strength / layer);
            if (a <= 0) continue;
            chamferOutline(g, x - layer, y - layer, w + layer * 2, h + layer * 2,
                    cut + layer, withAlpha(color, a));
        }
        chamferOutline(g, x, y, w, h, cut, color);
    }

    /**
     * 全息表面：扫描线 + 极低对比的竖向网格
     *
     * <p>整套观感里性价比最高的一笔 —— 它让平涂的板材看起来是投影出来的。
     * 代价只有 h/3 + w/16 次 fill。</p>
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param alpha 扫描线 alpha（建议 10~16，再高就成了条纹装饰）
     */
    public static void holoSurface(GuiGraphics g, int x, int y, int w, int h, int alpha) {
        int line = withAlpha(0xFF000000, alpha);
        for (int yy = y; yy < y + h; yy += 3) {
            g.fill(x, yy, x + w, yy + 1, line);
        }
        int grid = withAlpha(TECH, 6);
        for (int xx = x + 16; xx < x + w; xx += 16) {
            g.fill(xx, y, xx + 1, y + h, grid);
        }
    }

    /**
     * 四角瞄准括号：像 HUD 的锁定框。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param w     宽
     * @param h     高
     * @param len   臂长
     * @param color 颜色
     */
    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int color) {
        if (len <= 0) return;
        int r = x + w, b = y + h;
        g.fill(x, y, x + len, y + 1, color);
        g.fill(x, y, x + 1, y + len, color);
        g.fill(r - len, y, r, y + 1, color);
        g.fill(r - 1, y, r, y + len, color);
        g.fill(x, b - 1, x + len, b, color);
        g.fill(x, b - len, x + 1, b, color);
        g.fill(r - len, b - 1, r, b, color);
        g.fill(r - 1, b - len, r, b, color);
    }

    /**
     * 数据刻度条：一条竖线 + 疏密相间的刻度，把大片留白「仪器化」。
     *
     * @param g     画布
     * @param x     左
     * @param y     上
     * @param h     高
     * @param color 颜色
     */
    public static void tickStrip(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 1, y + h, withAlpha(color, 0x40));
        for (int yy = y + 4; yy < y + h; yy += 9) {
            int len = ((yy - y) / 9) % 4 == 0 ? 5 : 2;
            g.fill(x, yy, x + len, yy + 1, withAlpha(color, 0x90));
        }
    }

    /**
     * 数据线：中间实、两端斜向渐隐，带一道缓慢游走的扫描亮点
     *
     * <p>游走的亮点是全屏唯一的常驻动态元素 —— 有一个缓慢移动的东西，界面就是「活的」；
     * 但只能有一处，多了就成了噪音。</p>
     *
     * @param g      画布
     * @param x      左
     * @param y      上
     * @param w      宽
     * @param color  颜色
     * @param millis 当前时间
     */
    public static void dataLine(GuiGraphics g, int x, int y, int w, int color, long millis) {
        int fadeW = Math.max(1, w / 6);
        for (int i = 0; i < w; i++) {
            int a = 0x55;
            if (i < fadeW) a = (int) (0x55 * ((float) i / fadeW));
            else if (i >= w - fadeW) a = (int) (0x55 * ((float) (w - i) / fadeW));
            g.fill(x + i, y, x + i + 1, y + 1, withAlpha(color, a));
        }
        float phase = (millis % 9000L) / 9000f;
        float tri = phase < 0.5f ? phase * 2f : (1f - phase) * 2f;
        int cx = x + (int) (tri * w);
        for (int i = -7; i <= 7; i++) {
            int px = cx + i;
            if (px < x || px >= x + w) continue;
            int a = (int) (0xD0 * (1f - Math.abs(i) / 8f));
            g.fill(px, y, px + 1, y + 1, withAlpha(color, a));
        }
    }

    /**
     * 数据微粒：面板里缓慢上浮的冷色光点
     *
     * <p>位置由下标哈希决定、纵向随时间漂移，不保存任何粒子状态，每帧零分配，
     * 只有 {@link #MOTES} 次 fill。</p>
     *
     * @param g      画布
     * @param x      区域左
     * @param y      区域上
     * @param w      区域宽
     * @param h      区域高
     * @param millis 当前时间
     * @param alpha  整体不透明度系数 0~1
     */
    public static void motes(GuiGraphics g, int x, int y, int w, int h, long millis, float alpha) {
        if (w <= 4 || h <= 4) return;
        for (int i = 0; i < MOTES; i++) {
            int hash = (int) (((long) i * 2654435761L) & 0x7FFFFFFF) % 100003;
            int px = x + 2 + (hash % Math.max(1, w - 4));
            float speed = 0.006f + (hash % 5) * 0.002f;
            float raw = (millis * speed + hash % 991) % (h + 20);
            int py = y + h - (int) raw;
            if (py < y || py >= y + h) continue;
            float lifeFade = (float) (py - y) / h;
            int a = (int) (0x90 * lifeFade * alpha * (0.4f + 0.6f * (hash % 5) / 4f));
            if (a <= 4) continue;
            g.fill(px, py, px + 1, py + 1, withAlpha(TECH, a));
        }
    }

    /**
     * 角标：格子左上角的斜切三角，用来表示品质。
     *
     * @param g     画布
     * @param x     格子左
     * @param y     格子上
     * @param size  直角边长
     * @param color 颜色
     */
    public static void cornerTab(GuiGraphics g, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            g.fill(x, y + i, x + size - i, y + i + 1, color);
        }
    }

    /**
     * 徽章：斜切小块底 + 文字。
     *
     * @param g    画布
     * @param font 字体
     * @param text 文本
     * @param x    左
     * @param y    上
     * @param bg   底色
     * @param fg   字色
     * @return 徽章宽度
     */
    public static int badge(GuiGraphics g, Font font, String text, int x, int y, int bg, int fg) {
        int w = font.width(text) + 12;
        int h = font.lineHeight + 5;
        chamferFill(g, x, y, w, h, 3, bg);
        chamferOutline(g, x, y, w, h, 3, withAlpha(fg, 0x70));
        // 带阴影：徽章里是细体数字，压在扫描线上不带阴影会糊掉
        g.drawString(font, text, x + 6, y + 3, fg, true);
        return w;
    }

    /**
     * 滚动条：轨道 + 发光滑块。
     *
     * @param g         画布
     * @param x         左
     * @param y         上
     * @param trackH    轨道高
     * @param scroll    当前滚动量
     * @param maxScroll 最大滚动量
     */
    public static void scrollbar(GuiGraphics g, int x, int y, int trackH, double scroll, double maxScroll) {
        if (maxScroll <= 0) return;
        g.fill(x, y, x + SCROLLBAR_W, y + trackH, withAlpha(EDGE, 0x90));
        double visibleRatio = trackH / (trackH + maxScroll);
        int thumbH = Math.max(THUMB_MIN_H, (int) (trackH * visibleRatio));
        int thumbY = y + (int) ((trackH - thumbH) * (scroll / maxScroll));
        g.fill(x - 1, thumbY, x + SCROLLBAR_W + 1, thumbY + thumbH, withAlpha(TECH, 0x30));
        g.fill(x, thumbY, x + SCROLLBAR_W, thumbY + thumbH, TECH);
    }
}
