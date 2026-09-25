package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;

/**
 * 冒险指南的开场 / 收场 / 翻页动效：军械库终端开机
 *
 * <p>面板本身从头到尾<b>不平移</b>，只改变「哪一部分被画出来」（裁剪）和叠加在上面的光效 ——
 * 所以任何时刻控件都在它最终的位置上，点击的命中区和看到的东西不会对不上。
 * 所有时间都按 {@code Util.getMillis()} 的墙钟算，与帧率无关；窗口最小化回来直接是终态。</p>
 *
 * <h3>开场分镜（毫秒，从界面打开算起）</h3>
 * <pre>
 *   0 ─ 160  遮罩线性淡入
 *   0 ─ 120  屏幕正中一个亮点炸开成一条科技青扫描线，从中心向两侧展开（缓出）            —— CRT 开机
 *  90 ─ 300  面板以这条线为轴上下展开（裁剪高度增长，缓出）；线分成上下两道亮边，
 *            随展开外推并渐隐
 *  90 ─ 290  斜切外框「描线」：从左右两条侧边的中点出发，四个亮白笔头沿侧边上下走、
 *            拐过切角、在顶边 / 底边中点合拢（缓出）
 * 290 ─ 490  外框通电：辉光先冲到 2.5 倍、描边偏白，再回落到常态
 * 200 ─ 330  四角括号从外侧 10px 带回弹卡入到位（easeOutBack），闪一下后留一层淡青
 * 200 ─ 430  扫描光束自上而下匀速扫过面板：光束之上的内容显现（裁剪），光束拖一段余辉，
 *            光束后方有几条跳动的信号干扰条（青 / 猩红错位 = 色偏）
 * 200 ─ 560  全息闪烁：面板整体随机暗跳，幅度线性衰减到 0
 * 230 ─ 560  顶栏书名解密（呼应安魂解密）：230~320 乱码从左到右逐字铺开，每 40ms 换一次字形；
 *            320~490 从左到右逐字收束成真名，收束那一下闪白、70ms 内褪成常态色；
 *            猩红下划线标出正在解密的那个字
 * 380 ─ 560  副标题从左往右打字式展开，前沿带一个青色光标块
 * 200 ─ ~630 首页格子跟着光束逐个「全息成形」（描边先到、内容后到，见 {@link GuideHome}）
 * 640       开场结束：悬停、tooltip 生效
 * </pre>
 * <p>开场期间任何点击 / 按键（Esc 除外）都会让动画直接跳到终态，这次输入照常处理，见
 * {@code GuideScreen#skipIntro}。</p>
 *
 * <h3>收场分镜（毫秒，从按下关闭算起，共 {@value #CLOSE_MS}）</h3>
 * <pre>
 *   0 ─  80  内容被板材盖住（淡出），四角括号一起淡掉
 *  60 ─ 150  面板收成一条线（裁剪高度缩小，缓入）；上下两道亮边相向合拢
 * 140 ─ 220  这条线向中心收缩消失（缓入），最后 40ms 中心亮点闪一下
 * 100 ─ 220  遮罩淡出（缓入）
 * </pre>
 * <p>开场没走完就关：开场时间轴冻结在按下关闭的那一刻，收场从那个状态接着走。</p>
 *
 * <h3>翻页</h3>
 * <p>首页 ↔ 阅读页：正文区横向擦除 {@value #WIPE_MS}ms（缓出）。去阅读页从左往右擦，回首页从右往左；
 * 擦除线两侧分别是新页和旧页，前沿一道竖直亮线拖着余辉。</p>
 *
 * <h3>开销</h3>
 * <p>乱码字形是静态预分配的字符串表，字宽在排版时量好；每帧零分配、零测宽。
 * 开场结束后除了四角括号（8 次 fill）和外框，这里什么都不画。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideIntro {

    // ==================== 开场时间轴（毫秒） ====================

    static final float SCRIM_MS = 160f;
    static final float LINE_MS = 120f;
    /** 开场头几十毫秒的中心亮点 */
    static final float SPARK_MS = 70f;
    static final float UNFOLD_START = 90f;
    static final float UNFOLD_MS = 210f;
    static final float TRACE_START = 90f;
    static final float TRACE_MS = 200f;
    /** 外框描完后的通电辉光回落 */
    static final float FLASH_MS = 200f;
    static final float BRACKET_START = 200f;
    static final float BRACKET_MS = 130f;
    /** 括号卡到位后那一下闪光的衰减 */
    static final float BRACKET_FLASH_MS = 180f;
    /** 光束匀速扫过整块面板；首页格子按光束到达自己顶边的时刻开始成形 */
    static final float BEAM_START = 200f;
    static final float BEAM_MS = 230f;
    static final float FLICKER_END = 560f;
    static final float FLICKER_STEP_MS = 38f;
    static final float GLITCH_STEP_MS = 32f;
    static final float DECRYPT_START = 230f;
    static final float DECRYPT_END = 560f;
    /** 乱码从左到右铺满一行用的时间 */
    static final float SCRAMBLE_LEAD = 90f;
    /** 乱码换字形的间隔 */
    static final float SCRAMBLE_STEP_MS = 40f;
    /** 每个字收束时闪白、褪回常态色的时长 */
    static final float SETTLE_FLASH_MS = 70f;
    static final float SUB_START = 380f;
    static final float SUB_MS = 180f;
    /** 开场结束：之后悬停、tooltip 才生效 */
    static final float OPEN_MS = 640f;

    // ==================== 收场时间轴（毫秒） ====================

    static final float FADE_MS = 80f;
    static final float COLLAPSE_START = 60f;
    static final float COLLAPSE_MS = 90f;
    static final float SHRINK_START = 140f;
    static final float SHRINK_MS = 80f;
    static final float SCRIM_HOLD = 100f;
    static final float CLOSE_MS = 220f;
    /** 收场最后中心亮点闪一下的时长 */
    static final float DOT_MS = 40f;

    // ==================== 翻页 ====================

    static final float WIPE_MS = 140f;

    /** 笔头、光束芯、亮点：带一点青的白 */
    static final int HOT = 0xFFEFFFFF;

    /**
     * 光束、闪烁、收场遮盖、中线亮边这几层叠在内容之上时抬到的 z：物品图标画在 z≈150、数量字在 200、
     * 实体预览更低，留在 z=0 会过不了深度测试 —— 收场「盖住内容」时图标会实心地浮在空白板材上，
     * 光束扫过图标时会从它背后穿过去。300 仍低于 tooltip 的 400（与正文上下渐隐带同一个值）
     */
    private static final float OVERLAY_Z = 300f;

    // ==================== 乱码字形（静态预分配） ====================

    /** 窄字位（英文标题）：终端风格的 ASCII */
    private static final char[] NOISE = "0123456789ABCDEF#$%&*+=<>/\\|?!".toCharArray();
    /** 宽字位（中文标题）：方块与斜块，占位感更强；和窄字形按一半概率混用 */
    private static final char[] NOISE_WIDE = "▓▒░█◢◣◤◥".toCharArray();
    private static final String[] NOISE_STR = strings(NOISE);
    private static final String[] NOISE_WIDE_STR = strings(NOISE_WIDE);

    private static String[] strings(char[] chars) {
        String[] out = new String[chars.length];
        for (int i = 0; i < chars.length; i++) {
            out[i] = String.valueOf(chars[i]);
        }
        return out;
    }

    // ==================== 本帧状态（update 算好，绘制时只读） ====================

    /** 开场时间轴上的位置（毫秒，封顶 {@link #OPEN_MS}）；收场时冻结在按下关闭的那一刻 */
    float openT;
    /** 收场开始后的毫秒数；没在收场为 -1 */
    float closeT = -1f;
    float scrim;
    /** 中线宽度占面板宽的比例 */
    float line;
    /** 面板展开高度占面板高的比例 */
    float unfold;
    /** 外框描线进度 */
    float trace;
    /** 扫描光束位置占面板高的比例；1 = 扫完 */
    float beam;
    /** 收场时内容被板材盖住的程度 */
    float fade;

    // ==================== 几何与书名排版 ====================

    private int px;
    private int py;
    private int pw;
    private int ph;

    private String title = "";
    private String subtitle = "";
    private int subtitleW;
    private String[] glyphs = new String[0];
    private int[] glyphX = new int[0];
    private int[] glyphW = new int[0];
    private boolean[] blank = new boolean[0];
    private final int[] noiseW = new int[NOISE.length];
    private final int[] noiseWideW = new int[NOISE_WIDE.length];

    void setPanel(int x, int y, int w, int h) {
        px = x;
        py = y;
        pw = w;
        ph = h;
    }

    /**
     * 书名按字拆开、量好每个字的位置与宽度（只在排版时做一次）
     *
     * <p>解密时每个字位按<b>真名那个字的宽度</b>占位，乱码字形在字位里居中 ——
     * 乱码和真名宽窄不一，整串一起画的话后面的字会跟着左右抖。</p>
     */
    void prepareTitle(Font font, String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
        this.subtitleW = font.width(subtitle);
        int n = title.codePointCount(0, title.length());
        glyphs = new String[n];
        glyphX = new int[n];
        glyphW = new int[n];
        blank = new boolean[n];
        int x = 0;
        int i = 0;
        for (int off = 0; off < title.length() && i < n; i++) {
            int cp = title.codePointAt(off);
            int len = Character.charCount(cp);
            String s = title.substring(off, off + len);
            glyphs[i] = s;
            glyphX[i] = x;
            glyphW[i] = font.width(s);
            blank[i] = Character.isWhitespace(cp);
            x += glyphW[i];
            off += len;
        }
        for (int k = 0; k < NOISE_STR.length; k++) {
            noiseW[k] = font.width(NOISE_STR[k]);
        }
        for (int k = 0; k < NOISE_WIDE_STR.length; k++) {
            noiseWideW[k] = font.width(NOISE_WIDE_STR[k]);
        }
    }

    // ==================== 时间轴 ====================

    /**
     * 按当前时间算出本帧各段的进度
     *
     * @param now       {@code Util.getMillis()}
     * @param openedAt  打开时刻
     * @param closingAt 开始收场的时刻，没在收场为 0
     */
    void update(long now, long openedAt, long closingAt) {
        closeT = closingAt != 0L ? now - closingAt : -1f;
        long frozen = closingAt != 0L ? closingAt : now;
        openT = Mth.clamp((float) (frozen - openedAt), 0f, OPEN_MS);
        scrim = Math.min(1f, openT / SCRIM_MS);
        line = GuideTheme.easeOutCubic(openT / LINE_MS);
        unfold = GuideTheme.easeOutCubic((openT - UNFOLD_START) / UNFOLD_MS);
        trace = GuideTheme.easeOutCubic((openT - TRACE_START) / TRACE_MS);
        beam = Mth.clamp((openT - BEAM_START) / BEAM_MS, 0f, 1f);
        fade = 0f;
        if (closeT >= 0f) {
            fade = GuideTheme.easeOutCubic(closeT / FADE_MS);
            unfold *= 1f - GuideTheme.easeInCubic((closeT - COLLAPSE_START) / COLLAPSE_MS);
            line *= 1f - GuideTheme.easeInCubic((closeT - SHRINK_START) / SHRINK_MS);
            if (closeT > SCRIM_HOLD) {
                scrim *= 1f - GuideTheme.easeInCubic((closeT - SCRIM_HOLD) / (CLOSE_MS - SCRIM_HOLD));
            }
        }
    }

    /** 收场走完：可以真正关掉界面了 */
    boolean closed() {
        return closeT >= CLOSE_MS;
    }

    /** 开场走完且没在收场：悬停、tooltip 生效 */
    boolean settled() {
        return closeT < 0f && openT >= OPEN_MS;
    }

    /** 面板当前露出来的高度（以面板中线为轴上下展开） */
    int bandHeight() {
        return unfold >= 1f ? ph : Math.round(ph * unfold);
    }

    int bandTop() {
        return py + (ph - bandHeight()) / 2;
    }

    /** 光束所在行：它上面的内容已经显现 */
    int revealBottom() {
        return beam >= 1f ? py + ph : py + Math.round(ph * beam);
    }

    boolean traceDone() {
        return trace >= 1f;
    }

    private float outlineFlash() {
        float since = openT - TRACE_START - TRACE_MS;
        return since < 0f ? 1f : Math.max(0f, 1f - since / FLASH_MS);
    }

    /** 外框辉光强度：描完那一刻冲到 2.5 倍，{@value #FLASH_MS}ms 内回落到常态的 1 */
    float glowStrength() {
        return 1f + 1.5f * outlineFlash();
    }

    /** 外框线色：通电时偏白，回落到常态的科技青 */
    int outlineColor() {
        return GuideTheme.lerpColor(CodexTheme.withAlpha(GuideTheme.TECH, 0xB0), HOT, outlineFlash() * 0.6f);
    }

    // ==================== 绘制 ====================

    /**
     * 中线 / 上下两道亮边 + 中心亮点
     *
     * <p>面板还没展开时是屏幕正中的一条线；展开中分成两道，贴着裁剪带的上下沿外推、渐隐；
     * 收场时反过来相向合拢，再向中心缩成一个点。</p>
     *
     * <p>和盖层一样抬到 {@link #OVERLAY_Z}：两道亮边落在展开带<b>里面</b>，收场时盖住内容的板材
     * （开场时的全息闪烁）已经在这些像素上写了 z=300 的深度，留在 z=0 画会整条过不了深度测试 ——
     * 「上下两道亮边相向合拢」就只剩带外那两行淡辉光。</p>
     */
    void drawSeam(GuiGraphics g) {
        float spark = 0f;
        if (closeT < 0f) {
            spark = openT < SPARK_MS ? 1f - openT / SPARK_MS : 0f;
        } else if (closeT > CLOSE_MS - DOT_MS) {
            float p = (closeT - (CLOSE_MS - DOT_MS)) / DOT_MS;
            spark = p < 0.4f ? p / 0.4f : Math.max(0f, (1f - p) / 0.6f);
        }
        float k = 1f - unfold;
        boolean lines = k > 0.002f && line > 0.002f;
        if (spark <= 0.01f && !lines) {
            // 开场走完后每帧都走到这里：先判空再 pushPose（它每次都新建矩阵），常态下零分配
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        seamLayer(g, spark, lines ? k : 0f);
        g.pose().popPose();
    }

    /**
     * @param spark 中心亮点强度（≤ 0.01 不画）
     * @param k     亮边强度（1 − 展开量；0 不画）
     */
    private void seamLayer(GuiGraphics g, float spark, float k) {
        int cx = px + pw / 2;
        int cy = py + ph / 2;
        if (spark > 0.01f) {
            g.fill(cx - 4, cy - 1, cx + 4, cy + 2, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x60 * spark)));
            g.fill(cx - 1, cy - 2, cx + 2, cy + 3, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x60 * spark)));
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, CodexTheme.withAlpha(HOT, (int) (0xFF * spark)));
        }
        if (k <= 0f) {
            return;
        }
        float a = (float) Math.sqrt(k);
        int lw = Math.max(1, Math.round(pw * line));
        int lx = px + (pw - lw) / 2;
        int band = bandHeight();
        if (band <= 1) {
            glowLine(g, lx, cy, lw, a);
            return;
        }
        int top = bandTop();
        glowLine(g, lx, top, lw, a);
        glowLine(g, lx, top + band - 1, lw, a);
    }

    /** 一道带上下辉光的亮线（中段实、两端渐隐） */
    private static void glowLine(GuiGraphics g, int x, int y, int w, float a) {
        int outer = CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x24 * a));
        int inner = CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x70 * a));
        GuideTheme.hairline(g, x, y - 2, w, outer);
        GuideTheme.hairline(g, x, y - 1, w, inner);
        GuideTheme.hairline(g, x, y, w, CodexTheme.withAlpha(HOT, (int) (0xF0 * a)));
        GuideTheme.hairline(g, x, y + 1, w, inner);
        GuideTheme.hairline(g, x, y + 2, w, outer);
    }

    /**
     * 外框描线（描完之后由界面画常态外框，这里不再画）
     *
     * <p>从左右侧边中点（弧长 1/4、3/4 周处）出发，各自向上下两个方向走，
     * 走满 1/4 周时四个笔头在顶边、底边中点合拢。外面同步描一圈淡辉光，笔头 6px 亮白。</p>
     */
    void drawTrace(GuiGraphics g, int cut) {
        if (trace <= 0f || trace >= 1f) {
            return;
        }
        // 开场没描完就关：描了一半的外框不受展开带裁剪，得跟着内容一起淡掉，不能挂到收场结束
        float a = 1f - fade;
        if (a <= 0.01f) {
            return;
        }
        int per = CodexTheme.chamferPerimeter(pw, ph, cut);
        int perGlow = CodexTheme.chamferPerimeter(pw + 2, ph + 2, cut + 1);
        float d = trace * per / 4f;
        float dGlow = trace * perGlow / 4f;
        int lineColor = CodexTheme.withAlpha(GuideTheme.TECH, (int) (0xD0 * a));
        int glowColor = CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x38 * a));
        int headColor = CodexTheme.withAlpha(HOT, (int) (0xFF * a));
        for (int side = 1; side <= 3; side += 2) {
            float o = per / 4f * side;
            float og = perGlow / 4f * side;
            CodexTheme.chamferArc(g, px - 1, py - 1, pw + 2, ph + 2, cut + 1, og - dGlow, og + dGlow, glowColor);
            CodexTheme.chamferArc(g, px, py, pw, ph, cut, o - d, o + d, lineColor);
            if (d > 6f) {
                CodexTheme.chamferArc(g, px, py, pw, ph, cut, o - d, o - d + 6f, headColor);
                CodexTheme.chamferArc(g, px, py, pw, ph, cut, o + d - 6f, o + d, headColor);
            }
        }
    }

    /**
     * 四角括号：开场从外侧 10px 带回弹卡入，闪一下后常驻一层淡青；收场随内容一起淡掉
     */
    void drawBrackets(GuiGraphics g) {
        if (openT < BRACKET_START) {
            return;
        }
        float p = (openT - BRACKET_START) / BRACKET_MS;
        float vis = Math.min(1f, p / 0.3f) * (1f - fade);
        if (vis <= 0.01f) {
            return;
        }
        int off = Math.round((1f - GuideTheme.easeOutBack(p)) * 10f);
        float since = openT - BRACKET_START - BRACKET_MS;
        float flash = since < 0f ? Math.min(1f, p * 2.5f) : Math.max(0f, 1f - since / BRACKET_FLASH_MS);
        int alpha = (int) ((0x60 + 0x9F * flash) * vis);
        int color = CodexTheme.withAlpha(GuideTheme.lerpColor(GuideTheme.TECH, HOT, flash * 0.5f), alpha);
        int m = 3 + off;
        CodexTheme.brackets(g, px - m, py - m, pw + m * 2, ph + m * 2, 7, color);
    }

    /**
     * 扫描光束 + 余辉 + 信号干扰条（只在光束扫动期间；收场时跟着内容淡掉）
     */
    void drawBeam(GuiGraphics g, int cut) {
        if (beam <= 0f || beam >= 1f) {
            return;
        }
        float a = 1f - fade;
        if (a <= 0.01f) {
            return;
        }
        int by = revealBottom();
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        beamLayer(g, cut, by, a);
        g.pose().popPose();
    }

    private void beamLayer(GuiGraphics g, int cut, int by, float a) {
        // 余辉：光束上方 14 行，越远越淡（二次衰减）
        for (int k = 14; k >= 1; k--) {
            float f = 1f - k / 15f;
            row(g, by - k, cut, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x46 * f * f * a)));
        }
        // 前沿下方一点散光
        row(g, by + 1, cut, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x38 * a)));
        row(g, by + 2, cut, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x14 * a)));
        // 光束本体：整行科技青，中段叠一道亮白芯
        row(g, by, cut, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0xF0 * a)));
        if (by > py && by < py + ph - 1) {
            int in = inset(by, cut);
            GuideTheme.hairline(g, px + in, by, pw - in * 2, CodexTheme.withAlpha(HOT, (int) (0xE0 * a)));
        }
        // 信号干扰：刚显现的那一带里几条短横条，每 32ms 换一次位置；青 / 猩红错开 2px 做色偏
        int span = pw - cut * 2 - 8;
        if (span <= 64) {
            return;
        }
        int bucket = (int) (openT / GLITCH_STEP_MS);
        for (int k = 0; k < 4; k++) {
            int h = hash(bucket * 7 + k);
            int yy = by - 2 - h % 22;
            if (yy <= py + 1) {
                continue;
            }
            int len = 10 + (h >>> 8) % 50;
            int xx = px + cut + 4 + (h >>> 14) % (span - len);
            boolean red = ((h >>> 24) & 3) == 0;
            int main = red ? CodexTheme.withAlpha(GuideTheme.KUVA, (int) (0x60 * a))
                    : CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x70 * a));
            int ghost = red ? CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x30 * a))
                    : CodexTheme.withAlpha(GuideTheme.KUVA, (int) (0x2A * a));
            g.fill(xx, yy, xx + len, yy + 1, main);
            g.fill(xx + 2, yy + 1, xx + len + 2, yy + 2, ghost);
        }
    }

    /**
     * 全息闪烁：面板整体随机暗跳一下，幅度随时间线性衰减（每 {@value #FLICKER_STEP_MS}ms 换一次）
     */
    void drawFlicker(GuiGraphics g, int cut) {
        if (closeT >= 0f || openT < BEAM_START || openT >= FLICKER_END) {
            return;
        }
        float decay = 1f - (openT - BEAM_START) / (FLICKER_END - BEAM_START);
        int h = hash((int) (openT / FLICKER_STEP_MS) + 977);
        float amp = switch (h & 7) {
            case 0, 1 -> 0.34f;
            case 2 -> 0.18f;
            default -> 0f;
        };
        float f = amp * decay;
        if (f < 0.02f) {
            return;
        }
        overlay(g, cut, CodexTheme.fade(GuideTheme.PLATE, f));
    }

    /** 收场第一段：内容被板材盖住 */
    void drawCloseFade(GuiGraphics g, int cut) {
        if (fade <= 0.01f) {
            return;
        }
        overlay(g, cut, CodexTheme.fade(GuideTheme.PLATE, fade));
    }

    /** 盖满面板内部的一层（抬到 {@link #OVERLAY_Z}，连物品图标一起盖住） */
    private void overlay(GuiGraphics g, int cut, int color) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        CodexTheme.chamferFill(g, px + 1, py + 1, pw - 2, ph - 2, cut - 1, color);
        g.pose().popPose();
    }

    /**
     * 顶栏书名（解密动画）+ 副标题（打字式展开）；开场走完后就是两次普通的 drawString
     *
     * @param x    文字左
     * @param y    书名行顶
     * @param subY 副标题行顶
     */
    void drawTitle(GuiGraphics g, Font font, int x, int y, int subY) {
        if (openT >= DECRYPT_END) {
            g.drawString(font, title, x, y, GuideTheme.BONE, true);
            g.drawString(font, subtitle, x, subY, GuideTheme.ASH, false);
            return;
        }
        int n = glyphs.length;
        if (openT >= DECRYPT_START && n > 0) {
            float t = openT - DECRYPT_START;
            float settleSpan = DECRYPT_END - DECRYPT_START - SCRAMBLE_LEAD - SETTLE_FLASH_MS;
            int cursor = -1;
            for (int i = 0; i < n; i++) {
                if (t < SCRAMBLE_LEAD * i / n) {
                    // 乱码还没铺到这里，后面的更没到
                    break;
                }
                if (blank[i]) {
                    continue;
                }
                int gx = x + glyphX[i];
                float settleAt = SCRAMBLE_LEAD + settleSpan * (i + 1) / n;
                if (t >= settleAt) {
                    float k = (t - settleAt) / SETTLE_FLASH_MS;
                    g.drawString(font, glyphs[i], gx, y, GuideTheme.lerpColor(0xFFFFFFFF, GuideTheme.BONE, k), true);
                    continue;
                }
                if (cursor < 0) {
                    cursor = i;
                }
                // 每个字位各自的相位，免得整串乱码同一帧一起跳
                int h = hash(i * 131 + (int) ((t + i * 17f) / SCRAMBLE_STEP_MS));
                String s;
                int sw;
                if (glyphW[i] >= 8 && (h & 1) == 0) {
                    int idx = (h >>> 1) % NOISE_WIDE_STR.length;
                    s = NOISE_WIDE_STR[idx];
                    sw = noiseWideW[idx];
                } else {
                    int idx = (h >>> 1) % NOISE_STR.length;
                    s = NOISE_STR[idx];
                    sw = noiseW[idx];
                }
                int nx = gx + (glyphW[i] - sw) / 2;
                // 色偏：猩红残影错开 1px 垫在底下
                g.drawString(font, s, nx + 1, y, CodexTheme.withAlpha(GuideTheme.KUVA, 0x70), false);
                g.drawString(font, s, nx, y, CodexTheme.withAlpha(GuideTheme.TECH, 0xE0), false);
            }
            if (cursor >= 0) {
                int cx = x + glyphX[cursor];
                g.fill(cx, y + 10, cx + Math.max(2, glyphW[cursor] - 1), y + 11, GuideTheme.KUVA);
            }
        }
        float sp = (openT - SUB_START) / SUB_MS;
        if (sp <= 0f) {
            return;
        }
        if (sp >= 1f) {
            g.drawString(font, subtitle, x, subY, GuideTheme.ASH, false);
            return;
        }
        int rw = Math.round(subtitleW * GuideTheme.easeOutCubic(sp));
        if (rw > 0) {
            g.enableScissor(x, subY - 1, x + rw, subY + 10);
            g.drawString(font, subtitle, x, subY, GuideTheme.ASH, false);
            g.disableScissor();
        }
        g.fill(x + rw, subY, x + rw + 2, subY + 8, CodexTheme.withAlpha(GuideTheme.TECH, 0xC0));
    }

    /**
     * 翻页擦除的前沿：一道竖直亮线，新页那一侧拖 8px 余辉
     *
     * @param x         前沿所在列
     * @param y         上
     * @param h         高
     * @param rightward 是否从左往右擦（新页在左侧）
     * @param left      擦除区左边界（余辉不越界）
     * @param right     擦除区右边界（不含）
     */
    static void drawWipeFront(GuiGraphics g, int x, int y, int h, boolean rightward, int left, int right) {
        // 同样抬到 OVERLAY_Z：去阅读页时余辉拖在新页（正文）一侧，正文上下 8px 的渐隐带已经写了 z=300 的深度
        g.pose().pushPose();
        g.pose().translate(0f, 0f, OVERLAY_Z);
        wipeFrontLayer(g, x, y, h, rightward, left, right);
        g.pose().popPose();
    }

    private static void wipeFrontLayer(GuiGraphics g, int x, int y, int h, boolean rightward, int left, int right) {
        int dir = rightward ? -1 : 1;
        for (int k = 8; k >= 1; k--) {
            int xx = x + dir * k;
            if (xx < left || xx >= right) {
                continue;
            }
            float f = 1f - k / 9f;
            g.fill(xx, y, xx + 1, y + h, CodexTheme.withAlpha(GuideTheme.TECH, (int) (0x48 * f * f)));
        }
        if (x >= left && x < right) {
            g.fill(x, y, x + 1, y + h, CodexTheme.withAlpha(GuideTheme.TECH, 0xE0));
            g.fill(x, y + h / 4, x + 1, y + h - h / 4, CodexTheme.withAlpha(HOT, 0xB0));
        }
    }

    // ==================== 工具 ====================

    /** 面板在第 yy 行的左右内缩：斜切角那几行要往里收，光效不能画到切掉的角上 */
    private int inset(int yy, int cut) {
        int top = yy - py;
        int bottom = py + ph - 1 - yy;
        int in = 1;
        if (top < cut) {
            in = Math.max(in, cut - top);
        }
        if (bottom < cut) {
            in = Math.max(in, cut - bottom);
        }
        return in;
    }

    /** 面板内的一整行（按斜切角内缩；最上最下一行是外框，不画） */
    private void row(GuiGraphics g, int yy, int cut, int color) {
        if (yy <= py || yy >= py + ph - 1 || (color >>> 24) == 0) {
            return;
        }
        int in = inset(yy, cut);
        g.fill(px + in, yy, px + pw - in, yy + 1, color);
    }

    /** 整数哈希（非负）：乱码、干扰条、闪烁用的伪随机，不分配 Random */
    static int hash(int v) {
        v ^= v >>> 16;
        v *= 0x7feb352d;
        v ^= v >>> 15;
        v *= 0x846ca68b;
        v ^= v >>> 16;
        return v & 0x7FFFFFFF;
    }
}
