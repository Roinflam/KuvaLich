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
 * <h3>读的地方不动（2026-10-13 起）</h3>
 * <p>阅读栏、左栏、首页格子区里<b>没有常驻动画</b>：条目头的流光、左栏选中竖条的呼吸、实体预览自己环顾、
 * 首页上浮的数据微粒都改成了静止 —— 读久了，视野边上一直有东西在动会累眼睛。全屏只留顶栏分隔线上那一道
 * 往复的扫描亮点（{@link CodexTheme#dataLine}，顶栏不是读字的地方）。悬停、按下、换页、开场收场是事件，
 * 不算常驻。配方里的候选材料只在鼠标压着那张配方时轮换。</p>
 *
 * <h3>对比度（WCAG 相对亮度；半透明的底按合成后的不透明色算）</h3>
 * <p>阅读栏底 {@link #PLATE} #0C1013。<b>正文</b> {@link #BODY} 对它 11.0:1 —— 落在 9~12:1 里
 * （原先 12.2:1，近黑底上字边发晕）；顶栏 / 底栏 {@link #PLATE_HI} 上 10.3:1、提示框 {@link #TIP_BG} 上 10.1:1、
 * 按下的底 {@link #PRESS_BG} 上 10.3:1。标题和悬停字 {@link #BONE} 15.1:1（只用在短字上）。
 * <b>次要字</b> {@link #ASH} 6.2:1（最低 5.7:1，提示框上）；<b>最次要的字</b> {@link #FAINT_TEXT} 4.9:1
 * （{@link #PLATE_HI} 上 4.5:1、左栏底 #090C0E 上 5.0:1；提示框底上只有 4.47:1，所以提示框里不用它）。
 * 它只放在不变色的底上：首页格子、继续阅读条、左栏的行一悬停 / 选中底色就变亮（首页悬停底 4.4:1，左栏键盘选中的结果行约 3:1），
 * 这些地方的 FAINT_TEXT 随悬停 / 选中换成 {@link #ASH} 或更亮的字。{@link #FAINT} 只有 2.8:1，只当线条、图标、禁用态用，不当字用。
 * 键值表标签 {@link #LABEL} 8.7:1、链接 {@link #LINK} 9.6:1、物品链接 {@link #ITEM_LINK} 11.4:1、
 * 科技青 {@link #TECH} 11.0:1；赤毒猩红 {@link #KUVA} 5.5:1，当字只用在悬停的链接上。
 * 提示框上 {@link #KUVA} 5.0:1、{@link #TIP_BAD} 6.3:1、{@link #TIP_WF} 8.0:1。
 * 改色时按同样的算法复核：正文 9~12:1，次要字不低于 4.5:1。</p>
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
    /** 最淡的结构色：线条、图标、禁用态。<b>不当字用</b>（对 {@link #PLATE} 只有 2.8:1），当字用 {@link #FAINT_TEXT} */
    public static final int FAINT = CodexTheme.FAINT;
    /**
     * 最次要的字：底栏提示、搜索框占位、左栏计数与副标题、空状态、首页序号、继续阅读条的分类名。
     * 对 {@link #PLATE} 4.9:1、顶栏 / 底栏 4.5:1；提示框底上 4.47:1、悬停的格子底上 4.4:1，这两处不用它（见类注释）
     */
    public static final int FAINT_TEXT = 0xFF72838D;
    /** 按下时的底：赤毒暗面，只在按住的那一下出现（{@link #BONE} 14.1:1、正文 10.3:1、{@link #ASH} 5.8:1） */
    public static final int PRESS_BG = 0xFF24121A;

    // ==================== 正文与行内标签 ====================

    /** 正文：比 BONE 暗一档，对阅读栏底 11.0:1，长时间阅读不刺眼、字边不发晕 */
    public static final int BODY = 0xFFBCC6CC;
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
    /**
     * 物品链接色（正文里的 {@code {item:…}}）：偏青的浅灰蓝，和条目链接的 {@link #LINK} 分开 ——
     * 一个是「点了翻到别的篇」，一个是「指上去看物品提示」。同样带点线下划线，悬停转猩红。对阅读栏底 11.4:1
     */
    public static final int ITEM_LINK = 0xFF9FD0DC;
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
    /**
     * 提示框的底：所有样式同一个底（比 {@link #PLATE} 亮半档的冷灰），颜色只留在左色条和标题上。
     * 原先叠一层半透明的同色底，青、猩红叠在近黑上会混出发灰的脏色
     */
    public static final int TIP_BG = 0xFF141A1F;

    // ==================== 尺寸 ====================

    /** 屏幕四周留白（大屏）；小于 560 宽时降到 {@link #MARGIN_SMALL} */
    public static final int MARGIN = 12;
    public static final int MARGIN_SMALL = 6;
    public static final int HEADER_H = 34;
    public static final int FOOTER_H = 18;
    public static final int PAD = 10;
    /** 主框体切角 */
    public static final int CHAMFER = 10;
    /**
     * 正文行高（字高 9 + 阴影 1 + 行距 3）。中文一行 9 像素的字排得密，多空一像素换行时不容易跳错行。
     * 所有块的高度、链接热区、滚动步长都按它算；块高用 {@code 行数 × LINE_H − }{@link #LINE_TAIL}（最后一行不留行距）
     */
    public static final int LINE_H = 13;
    /** 一段文字最后一行下面不留的那截行距：{@code LINE_H − 10}（字 9 像素 + 阴影 1 像素） */
    public static final int LINE_TAIL = LINE_H - 10;

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

    // ==================== 曲线 ====================

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
     * 像素「Aa」：顶栏调界面大小的按钮（大 A 7 行高、小 a 5 行高，底边对齐）。和放大镜、房子同一类，自己画不随字体包变
     *
     * @param cx 中心 x
     * @param cy 中心 y
     */
    public static void scaleGlyph(GuiGraphics g, int cx, int cy, int color) {
        int x = cx - 5;
        int top = cy - 3;
        // 大 A（5 宽）：尖顶、两条斜腿、第 5 行横梁
        g.fill(x + 2, top, x + 3, top + 1, color);
        g.fill(x + 1, top + 1, x + 2, top + 3, color);
        g.fill(x + 3, top + 1, x + 4, top + 3, color);
        g.fill(x, top + 3, x + 1, top + 7, color);
        g.fill(x + 4, top + 3, x + 5, top + 7, color);
        g.fill(x + 1, top + 4, x + 4, top + 5, color);
        // 小 a（4 宽，后 5 行）：顶弧、右竖、中横、左下、底横
        int ax = x + 6;
        int at = top + 2;
        g.fill(ax + 1, at, ax + 3, at + 1, color);
        g.fill(ax + 3, at + 1, ax + 4, at + 5, color);
        g.fill(ax + 1, at + 2, ax + 3, at + 3, color);
        g.fill(ax, at + 3, ax + 1, at + 4, color);
        g.fill(ax + 1, at + 4, ax + 3, at + 5, color);
    }

    /**
     * 像素四芒星（9×9）：顶栏「开场动画」开关。开着是实心的星，关着改画暗色的星并加一道斜杠
     *
     * @param cx    中心 x
     * @param cy    中心 y
     * @param slash 关着时斜杠的颜色
     */
    public static void animGlyph(GuiGraphics g, int cx, int cy, int color, int slash, boolean on) {
        // 竖、横两条主轴（长 9、粗 1），中间一格四周补成菱形的饱满感
        g.fill(cx, cy - 4, cx + 1, cy + 5, color);
        g.fill(cx - 4, cy, cx + 5, cy + 1, color);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
        g.fill(cx - 2, cy - 2, cx - 1, cy - 1, color);
        g.fill(cx + 2, cy - 2, cx + 3, cy - 1, color);
        g.fill(cx - 2, cy + 2, cx - 1, cy + 3, color);
        g.fill(cx + 2, cy + 2, cx + 3, cy + 3, color);
        if (!on) {
            // 斜杠：左下到右上
            for (int i = -4; i <= 4; i++) {
                g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, slash);
            }
        }
    }

    /** 悬停「锁定括号」从块外收到四角上的时长 */
    public static final float LOCK_MS = 120f;

    /**
     * 悬停的「锁定括号」：鼠标刚压上首页格子 / 顶栏按钮 / 继续阅读条时，四个角括号从块外 4px 收到块的四角上
     * （easeOutBack，带一点卡入的回弹），到位后静止，移开就消失 —— 和开场的锁定框是同一个意象
     *
     * @param p     收拢进度 0~1，≥ 1 时静止在四角上
     * @param color 颜色（赤毒猩红）
     */
    public static void lockBrackets(GuiGraphics g, int x, int y, int w, int h, float p, int color) {
        int off = Math.max(0, Math.round((1f - easeOutBack(p)) * 4f));
        int len = Mth.clamp(Math.min(w, h) / 4, 3, 6);
        CodexTheme.brackets(g, x - off, y - off, w + off * 2, h + off * 2, len, color);
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
