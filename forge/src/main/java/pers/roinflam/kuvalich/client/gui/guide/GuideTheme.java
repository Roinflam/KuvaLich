package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;

/**
 * 冒险指南的配色、尺寸与绘制原语
 *
 * <h3>和模组图鉴是同一套语言</h3>
 * <p>底色、结构线、科技青、赤毒猩红直接取 {@link CodexTheme} 的常量 —— 指南和图鉴是同一个模组里
 * 挨着的两块界面，玩家从一个切到另一个时不该觉得换了一个产品。规则也照搬：冷底暖芯、斜切几何、
 * 1px 锐线 + 微辉光、全息扫描线，<b>全屏只有一个暖色焦点（赤毒猩红）</b>，只给「当前 / 悬停」用。</p>
 *
 * <h3>正文配色为什么单列</h3>
 * <p>图鉴几乎没有成段的文字，而指南是拿来读的。正文色比 {@link CodexTheme#BONE} 暗半档：
 * 满屏纯亮白读久了刺眼，也会把 {@code <em>} 的「亮白加粗」衬得不够亮。
 * 行内标签的颜色与武器 tooltip（{@code client.tooltip.PanelPalette}）逐一对齐 ——
 * 书里写「<g>增益</g>」「<b>减益</b>」时，颜色和玩家在面板上看到的一模一样。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class GuideTheme {

    private GuideTheme() {}

    // ==================== 结构色（沿用图鉴） ====================

    /** 整屏遮罩：比图鉴略透一点，背后的世界隐约可见，但不干扰阅读 */
    public static final int SCRIM = 0xE805080A;
    public static final int PLATE = CodexTheme.PLATE;
    public static final int PLATE_HI = CodexTheme.PLATE_HI;
    public static final int PLATE_DEEP = CodexTheme.PLATE_DEEP;
    public static final int EDGE = CodexTheme.EDGE;
    public static final int TECH = CodexTheme.TECH;
    /** 全屏唯一的暖色焦点：当前条目、悬停中的格子 / 链接 */
    public static final int KUVA = CodexTheme.KUVA;
    public static final int EMBER = CodexTheme.EMBER;
    public static final int BONE = CodexTheme.BONE;
    public static final int ASH = CodexTheme.ASH;
    public static final int FAINT = CodexTheme.FAINT;

    // ==================== 正文与行内标签 ====================

    /** 正文：比 BONE 暗半档，长时间阅读不刺眼 */
    public static final int BODY = 0xFFC6D0D6;
    /** {@code <hl>}：关键术语，赤毒猩红（全书唯一强焦点色） */
    public static final int TAG_HL = KUVA;
    /** {@code <t>}：科技青，取 tooltip 的叠层值色 {@code PanelPalette.STACKED} */
    public static final int TAG_T = 0xFF6FD8E8;
    /** {@code <w>}：余烬橙，取 tooltip 的警告色 {@code PanelPalette.WARN} */
    public static final int TAG_W = 0xFFFFB066;
    /** {@code <g>}：增益，取 {@code PanelPalette.BONUS} */
    public static final int TAG_G = 0xFF7FE08A;
    /** {@code <b>}：减益 / 危险，取 {@code PanelPalette.PENALTY} */
    public static final int TAG_B = 0xFFFF7B7B;
    /** {@code <dim>}：次要说明，取 {@code PanelPalette.MUTED} */
    public static final int TAG_DIM = 0xFF8A93A3;
    /** {@code <em>}：亮白加粗，取 {@code PanelPalette.VALUE} */
    public static final int TAG_EM = 0xFFF2F4F8;

    /**
     * 链接色
     *
     * <p>刻意避开科技青：{@code <t>} 已经用青色标数值和名词，链接再用青色就分不清「这是个数」还是「能点」。
     * 淡蓝紫加一条虚线下划线，和元素色里饱和度更高的电击 / 磁力也拉得开。悬停时转猩红 + 实线。</p>
     */
    public static final int LINK = 0xFF9AB6FF;
    /** 配置里缺这个键时显示的「?」 */
    public static final int MISSING = TAG_W;

    /** 表头 / 小标题 */
    public static final int HEADING = BONE;
    /**
     * 键值表的左列标签：比正文暗一档、比 ASH 亮一档。
     * 用 ASH 的话「数值速查」这种满屏标签的表读起来太吃力，和值同色又分不出哪边是键。
     */
    public static final int LABEL = 0xFFA3B1B9;
    /** 表头文字：科技青，和表头底色一起把表头与数据行分开 */
    public static final int TABLE_HEAD = TECH;

    // ==================== 提示框 ====================

    public static final int TIP_INFO = TECH;
    public static final int TIP_WARN = 0xFFFFA24D;
    public static final int TIP_GOOD = TAG_G;
    public static final int TIP_BAD = 0xFFFF6B6B;
    /** 星际战甲小知识：淡紫，和功能性提示（青/橙/绿/红）一眼分开，表示「这是背景知识，可以跳过」 */
    public static final int TIP_WF = 0xFFB9A0FF;

    // ==================== 尺寸 ====================

    /** 屏幕四周留白（大屏）；小于 560 宽时降到 {@link #MARGIN_SMALL} */
    public static final int MARGIN = 12;
    public static final int MARGIN_SMALL = 6;
    public static final int HEADER_H = 34;
    public static final int FOOTER_H = 18;
    public static final int PAD = 10;
    /** 主框体切角 */
    public static final int CHAMFER = 10;
    /** 正文行高（字高 9 + 行距 2） */
    public static final int LINE_H = 12;

    // ==================== 像素点阵数字 ====================

    /**
     * 0~9 的 5×7 点阵（每行 5 位，最高位是最左一列）
     *
     * <h3>为什么不用字体画</h3>
     * <p>步骤徽记只有 13 像素宽，数字要严格居中。用字体画时，居中靠的是字宽（含字间距），
     * 而字形在字宽里的位置取决于字体：原版 ascii 字体刚好居中，整合包换了像素字体后有的数字偏半格到一格，
     * 徽记里一眼就能看出歪。自己画点阵与字体无关：5 像素宽的数字在 13 像素的框里左右各留 4 格，永远正中。</p>
     */
    private static final int[][] DIGITS = {
            {14, 17, 19, 21, 25, 17, 14},
            {4, 12, 4, 4, 4, 4, 31},
            {14, 17, 1, 6, 8, 17, 31},
            {14, 17, 1, 6, 1, 17, 14},
            {3, 5, 9, 17, 31, 1, 1},
            {31, 16, 30, 1, 1, 17, 14},
            {6, 8, 16, 30, 17, 17, 14},
            {31, 17, 1, 2, 4, 4, 4},
            {14, 17, 17, 14, 17, 17, 14},
            {14, 17, 17, 15, 1, 2, 12}
    };

    /** 点阵数字的宽度（含数字间 1 像素间隔） */
    public static int digitsWidth(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                n++;
            }
        }
        return n == 0 ? 0 : n * 6 - 1;
    }

    /**
     * 以 {@code centerX} 列为中心画一串点阵数字（非数字字符跳过）
     *
     * @param g       画布
     * @param s       数字串
     * @param centerX 中心列（奇数宽度的框传中间那一列）
     * @param top     顶行
     * @param color   颜色
     */
    public static void digits(GuiGraphics g, String s, int centerX, int top, int color) {
        int x = centerX - (digitsWidth(s) - 1) / 2;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') {
                continue;
            }
            int[] glyph = DIGITS[ch - '0'];
            for (int row = 0; row < 7; row++) {
                int bits = glyph[row];
                int col = 0;
                while (col < 5) {
                    if ((bits & (1 << (4 - col))) == 0) {
                        col++;
                        continue;
                    }
                    // 同一行连续的点合成一次 fill
                    int start = col;
                    while (col < 5 && (bits & (1 << (4 - col))) != 0) {
                        col++;
                    }
                    g.fill(x + start, top + row, x + col, top + row + 1, color);
                }
            }
            x += 6;
        }
    }
    /** 面板最宽多少：再宽阅读栏也不会跟着变宽，只会让两侧空得更多 */
    public static final int PANEL_MAX_W = 760;
    /** 阅读栏最大宽度：一行超过约 50 个汉字，眼睛换行时就容易找错下一行 */
    public static final int READ_MAX_W = 460;

    // ==================== 颜色工具 ====================

    public static int withAlpha(int argb, int alpha) {
        return CodexTheme.withAlpha(argb, alpha);
    }

    /**
     * 按比例缩放 alpha。
     *
     * @param argb 原色
     * @param f    0~1
     * @return 新颜色
     */
    public static int fade(int argb, float f) {
        return CodexTheme.fade(argb, f);
    }

    /**
     * 文字颜色乘透明度
     *
     * <p>原版字体渲染把 alpha 低于 4 的颜色当成「没写 alpha」按不透明画，淡出到最后会突然闪一下全亮，
     * 所以最低保留 5。几乎透明的文字调用方应该直接不画。</p>
     *
     * @param argb  原色
     * @param scale 0~1
     * @return 新颜色
     */
    public static int textAlpha(int argb, float scale) {
        int alpha = Math.max(5, (int) (((argb >>> 24) & 0xFF) * Mth.clamp(scale, 0f, 1f)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerpColor(int from, int to, float t) {
        float k = Mth.clamp(t, 0f, 1f);
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * k);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ==================== 时间与曲线 ====================

    /**
     * 常驻动画共用的时间源（秒）。取模 12 分钟，免得游戏开久了浮点精度变差。
     *
     * <p>回绕那一刻时间从 720 跳回 0，只有周期能整除 720 秒的动画才看不出接缝：
     * 现有的流光 {@link #sweep} 9 秒（80 圈）、选中行呼吸 2.4 秒（300 圈）、实体预览环顾 8 秒（90 圈）。
     * <b>以后新加常驻动画，周期也必须能整除 720 秒</b>，否则每 12 分钟会瞬移一次
     * （原先取模 10 分钟，9 秒的流光就是这样每 10 分钟跳一下）。</p>
     *
     * @return 秒
     */
    public static float time() {
        return (System.currentTimeMillis() % 720_000L) / 1000f;
    }

    /** 缓出：开头快、结尾慢。用于出现 —— 滑进来后稳稳停住。 */
    public static float easeOutCubic(float t) {
        float inv = 1f - Mth.clamp(t, 0f, 1f);
        return 1f - inv * inv * inv;
    }

    /** 缓入：与缓出互为镜像，用于消失。反过来用会在按下的一瞬间猛弹一截。 */
    public static float easeInCubic(float t) {
        float c = Mth.clamp(t, 0f, 1f);
        return c * c * c;
    }

    /** 带一点过冲再回弹的缓出：格子浮现用，和御炎指南的菜单同一条曲线。 */
    public static float easeOutBack(float t) {
        float inv = Mth.clamp(t, 0f, 1f) - 1f;
        return 1f + inv * inv * (2.70158f * inv + 1.70158f);
    }

    /**
     * 指数趋近：每帧向目标靠近一截，与帧率无关。悬停高亮、平滑滚动都用它。
     *
     * @param current      当前值
     * @param target       目标值
     * @param deltaSeconds 距上一帧的秒数
     * @param speed        越大越快，16 约等于 0.1 秒走完大半
     * @return 新值
     */
    public static float approach(float current, float target, float deltaSeconds, float speed) {
        float next = current + (target - current) * (1f - (float) Math.exp(-speed * deltaSeconds));
        // 足够近就直接贴上：指数曲线永远到不了终点，残留的零点几像素会让文字在两个像素之间来回跳
        return Math.abs(target - next) < 0.02f ? target : next;
    }

    /** 呼吸系数：在 min 与 1 之间以 period 秒为周期平滑往复。 */
    public static float breathe(float time, float period, float min) {
        float wave = (float) ((Math.sin(time / period * Math.PI * 2.0) + 1.0) * 0.5);
        return min + (1f - min) * wave;
    }

    // ==================== 绘制原语 ====================

    public static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * 四周压暗的暗角：遮罩均匀的话视线没有着落点，四周再压一档，中间的面板自然浮出来。
     *
     * @param g      画布
     * @param width  屏宽
     * @param height 屏高
     * @param scale  强度 0~1（跟着遮罩淡入淡出）
     */
    public static void vignette(GuiGraphics g, int width, int height, float scale) {
        if (scale <= 0f) {
            return;
        }
        int rings = 6;
        int stepX = Math.max(1, width / (rings * 4));
        int stepY = Math.max(1, height / (rings * 4));
        int color = ((int) (24 * scale)) << 24;
        for (int i = 0; i < rings; i++) {
            int left = i * stepX;
            int top = i * stepY;
            g.fill(0, top, left + stepX, height - top, color);
            g.fill(width - left - stepX, top, width, height - top, color);
            g.fill(left + stepX, 0, width - left - stepX, top + stepY, color);
            g.fill(left + stepX, height - top - stepY, width - left - stepX, height, color);
        }
    }

    /**
     * 水平线上一段缓慢往复的流光（三角波来回扫，不会在回到起点时瞬移）。固定分段画，宽屏上也只是几十次 fill。
     *
     * @param g        画布
     * @param x        左
     * @param y        线所在行
     * @param width    宽
     * @param time     {@link #time()}
     * @param phase    相位偏移：不同的线错开，免得整屏高光同步扫动像跑马灯
     * @param color    颜色
     * @param strength 0~1
     */
    public static void sweep(GuiGraphics g, int x, int y, int width, float time, float phase, int color, float strength) {
        if (width <= 0 || strength <= 0f) {
            return;
        }
        final int segments = 24;
        float t = ((time / 9f) + phase) % 1f;
        float tri = t < 0.5f ? t * 2f : (1f - t) * 2f;
        int center = x + (int) (tri * width);
        int half = Math.max(16, width / 6);
        int step = Math.max(1, half * 2 / segments);
        for (int i = 0; i < segments; i++) {
            int sx = center - half + i * step;
            int ex = Math.min(sx + step, x + width);
            if (ex <= x || sx >= x + width) {
                continue;
            }
            float falloff = 1f - Math.min(1f, Math.abs((sx + ex) * 0.5f - center) / half);
            int alpha = (int) (170 * falloff * falloff * strength);
            if (alpha > 2) {
                g.fill(Math.max(x, sx), y, ex, y + 1, withAlpha(color, alpha));
            }
        }
    }

    /**
     * 从左往右由浓到淡的渐变条。悬停行用它：纯色块会把整行盖住，渐变像一道光扫过去，不抢文字。
     */
    public static void gradientRow(GuiGraphics g, int x, int y, int width, int height, int color) {
        if (width <= 0) {
            return;
        }
        int steps = 12;
        int step = Math.max(1, width / steps);
        for (int i = 0; i < steps; i++) {
            int sx = x + i * step;
            int ex = i == steps - 1 ? x + width : Math.min(x + width, sx + step);
            if (sx >= ex) {
                break;
            }
            float k = 1f - (i / (float) steps);
            g.fill(sx, y, ex, y + height, fade(color, k * k));
        }
    }

    /**
     * 斜切徽框：图标嵌在里面的小八边形
     *
     * <p>和图鉴的大框体同一种切角语言；切角按尺寸取 1/4，小到 12px 时也还看得出是「切过的」。</p>
     *
     * @param g       画布
     * @param x       左
     * @param y       上
     * @param size    边长
     * @param fill    底色
     * @param outline 描边色
     */
    public static void emblem(GuiGraphics g, int x, int y, int size, int fill, int outline) {
        int cut = Math.max(2, size / 4);
        CodexTheme.chamferFill(g, x, y, size, size, cut, fill);
        CodexTheme.chamferOutline(g, x, y, size, size, cut, outline);
    }

    /**
     * 小徽章：斜切底 + 文字，高 11px（比图鉴的徽章矮 3px，塞得进 34px 的顶栏第一行）
     *
     * @return 宽度
     */
    public static int chip(GuiGraphics g, Font font, String text, int x, int y, int color, float alpha) {
        int w = font.width(text) + 8;
        int h = 11;
        CodexTheme.chamferFill(g, x, y, w, h, 2, fade(withAlpha(color, 0x22), alpha));
        CodexTheme.chamferOutline(g, x, y, w, h, 2, fade(withAlpha(color, 0x90), alpha));
        g.drawString(font, text, x + 4, y + 2, textAlpha(color, alpha), false);
        return w;
    }

    public static int chipWidth(Font font, String text) {
        return font.width(text) + 8;
    }

    /**
     * 中间实、两端渐隐的细线（分隔线、实体预览的地台）。分 16 段画，不逐像素。
     *
     * @param g     画布
     * @param x     左
     * @param y     行
     * @param w     宽
     * @param color 颜色（中段的颜色）
     */
    public static void hairline(GuiGraphics g, int x, int y, int w, int color) {
        if (w <= 0) {
            return;
        }
        final int segments = 16;
        for (int i = 0; i < segments; i++) {
            int sx = x + w * i / segments;
            int ex = x + w * (i + 1) / segments;
            if (ex <= sx) {
                continue;
            }
            float mid = (i + 0.5f) / segments;
            float k = 1f - Math.abs(mid - 0.5f) * 2f;
            g.fill(sx, y, ex, y + 1, fade(color, (float) Math.sqrt(k)));
        }
    }

    /**
     * 虚线：链接的常态下划线。两像素一点，比实线轻，一眼能看出「这里能点」又不抢字。
     */
    public static void dotted(GuiGraphics g, int x, int y, int w, int color) {
        for (int i = 0; i < w; i += 2) {
            g.fill(x + i, y, x + i + 1, y + 1, color);
        }
    }

    /**
     * 像素箭头（朝左或朝右的 V 形），画在导航按钮里。字体里的箭头字符在不同字体包里宽窄不一，自己画最稳。
     *
     * @param g     画布
     * @param cx    中心 x
     * @param cy    中心 y
     * @param left  是否朝左
     * @param color 颜色
     */
    public static void chevron(GuiGraphics g, int cx, int cy, boolean left, int color) {
        for (int i = 0; i < 4; i++) {
            int dx = left ? (i - 2) : (1 - i);
            g.fill(cx + dx, cy - i, cx + dx + 2, cy - i + 1, color);
            if (i > 0) {
                g.fill(cx + dx, cy + i, cx + dx + 2, cy + i + 1, color);
            }
        }
    }

    /**
     * 像素房子：主页按钮。
     */
    public static void house(GuiGraphics g, int cx, int cy, int color) {
        // 屋顶：三层阶梯
        g.fill(cx - 1, cy - 4, cx + 1, cy - 3, color);
        g.fill(cx - 2, cy - 3, cx + 2, cy - 2, color);
        g.fill(cx - 4, cy - 2, cx + 4, cy - 1, color);
        // 墙与门洞
        g.fill(cx - 3, cy - 1, cx - 2, cy + 4, color);
        g.fill(cx + 2, cy - 1, cx + 3, cy + 4, color);
        g.fill(cx - 3, cy + 3, cx + 3, cy + 4, color);
        g.fill(cx - 1, cy + 1, cx + 1, cy + 3, color);
    }

    /**
     * 放大镜：搜索框的前导图标。
     */
    public static void magnifier(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 4, y + 1, color);
        g.fill(x, y + 1, x + 1, y + 4, color);
        g.fill(x + 4, y + 1, x + 5, y + 4, color);
        g.fill(x + 1, y + 4, x + 4, y + 5, color);
        g.fill(x + 4, y + 5, x + 5, y + 6, color);
        g.fill(x + 5, y + 6, x + 6, y + 7, color);
    }

    /**
     * 按宽度截断并补省略号。
     *
     * @param font 字体
     * @param text 原文
     * @param maxW 最大宽度
     * @return 放得下的文本
     */
    public static String ellipsize(Font font, String text, int maxW) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.width(text) <= maxW) {
            return text;
        }
        int ell = font.width("…");
        if (maxW <= ell) {
            return "";
        }
        return font.plainSubstrByWidth(text, maxW - ell) + "…";
    }
}
