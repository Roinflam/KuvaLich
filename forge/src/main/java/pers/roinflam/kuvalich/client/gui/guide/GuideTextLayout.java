package pers.roinflam.kuvalich.client.gui.guide;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Link;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Rich;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Span;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 中英混排折行
 *
 * <h3>为什么不用原版的 {@code Font.split}</h3>
 * <p>原版按宽度硬切：中文会把逗号句号切到下一行开头（「，选择特化加成」），
 * 数字会被从中间劈开（「18」「0 秒」）。这里按断行机会切：</p>
 * <ul>
 *   <li>中日韩字符前后都可以断（逐字可断）；</li>
 *   <li>英文、数字按词断，词内不断；空格处断，断开后行首的空格吃掉；</li>
 *   <li>避头尾：句读、右括号、百分号这类不能出现在行首，左括号不能留在行尾。
 *       真撞上了就把前一个字一起挪到下一行 —— 这比让标点悬在行首或者溢出边界都好看；</li>
 *   <li>一个词比整行还宽（长 id、长数字串）时才退回按字硬切。</li>
 * </ul>
 * <p>样式跨行自然延续：折行只是在片段序列上切刀，每一段仍带着自己的颜色 / 加粗 / 链接。</p>
 *
 * <h3>宽度按浮点累加</h3>
 * <p>Unifont 的中文字形前进量是半像素的倍数，逐字取整再累加，一行三十个字能差出十几像素，
 * 行尾会参差不齐甚至溢出。这里按字形的真实浮点宽度累加（按码点缓存，只在排版时算），
 * 片段的 x 取累加值四舍五入，与字体自己画的位置最多差半个像素。</p>
 *
 * <p>排版只在缓存未命中时发生（换条目、换宽度、换配置快照），所以这里允许分配；
 * 渲染路径只读 {@link Line} 里预先建好的 {@link FormattedCharSequence}。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideTextLayout {

    /** 不能出现在行首 */
    private static final String NO_LINE_START = "，。、；：！？）」』》〉】”’…,.;:!?)]}%·～ー—";
    /** 不能留在行尾 */
    private static final String NO_LINE_END = "（「『《〈【“‘([{";

    /**
     * 一行里同样式的一段
     */
    static final class Piece {
        /** 纯文本（截断、省略号用） */
        final String text;
        final FormattedCharSequence seq;
        /** 链接悬停时换上的样式；非链接为 null */
        @Nullable
        final FormattedCharSequence hoverSeq;
        final int x;
        final int w;
        @Nullable
        final Link link;

        Piece(String text, FormattedCharSequence seq, @Nullable FormattedCharSequence hoverSeq, int x, int w, @Nullable Link link) {
            this.text = text;
            this.seq = seq;
            this.hoverSeq = hoverSeq;
            this.x = x;
            this.w = w;
            this.link = link;
        }
    }

    /**
     * 一行
     */
    static final class Line {
        static final Piece[] NONE = new Piece[0];
        final Piece[] pieces;
        final int width;

        Line(Piece[] pieces, int width) {
            this.pieces = pieces;
            this.width = width;
        }
    }

    /**
     * 单个码点的宽度来源：正式运行时就是字体本身；抽出成接口是为了能脱离游戏用固定字宽自测折行规则
     */
    interface Measure {
        float width(int codepoint, Style style);
    }

    private final Font font;
    private final Measure measure;
    /** 码点 × 2 + 是否加粗 → 宽度 */
    private final Int2FloatOpenHashMap widthCache = new Int2FloatOpenHashMap();

    GuideTextLayout(Font font) {
        this(font, splitterMeasure(font.getSplitter()));
    }

    GuideTextLayout(Font font, Measure measure) {
        this.font = font;
        this.measure = measure;
    }

    private static Measure splitterMeasure(StringSplitter splitter) {
        return (cp, style) -> splitter.stringWidth(FormattedCharSequence.codepoint(cp, style));
    }

    Font font() {
        return font;
    }

    // ==================== 对外 ====================

    /**
     * 按宽度折行
     *
     * @param rich     解析好的文本
     * @param maxWidth 最大行宽
     * @return 行；空文本返回空数组
     */
    Line[] wrap(Rich rich, int maxWidth) {
        if (rich.isEmpty()) {
            return new Line[0];
        }
        Flat f = flatten(rich);
        List<Line> lines = new ArrayList<>();
        int n = f.n;
        int pos = 0;
        float max = Math.max(1, maxWidth);
        while (pos < n) {
            int start = pos;
            float w = 0f;
            int i = pos;
            int lastBreak = -1;
            boolean forced = false;
            while (i < n) {
                int c = f.cps[i];
                if (c == '\n') {
                    forced = true;
                    break;
                }
                float cw = f.w[i];
                if (w + cw > max && i > start) {
                    break;
                }
                if (i > start && canBreak(f.cps, i)) {
                    lastBreak = i;
                }
                w += cw;
                i++;
            }
            if (i >= n || forced) {
                lines.add(build(f, start, i));
                pos = forced ? i + 1 : n;
                continue;
            }
            // 放不下第 i 个字：优先在它前面断，否则退到最近的断行机会，实在没有就硬切
            int cut = canBreak(f.cps, i) ? i : lastBreak;
            if (cut <= start) {
                cut = i;
            }
            lines.add(build(f, start, cut));
            pos = cut;
            while (pos < n && f.cps[pos] == ' ') {
                pos++;
            }
            // 软折行恰好落在「行尾空格 + 换行符」上：这一行已经断开了，换行符不能再断出一行空行
            if (pos > cut && pos < n && f.cps[pos] == '\n') {
                pos++;
            }
        }
        return lines.toArray(new Line[0]);
    }

    /**
     * 不折行时最宽那一行的宽度（表格列宽的「理想宽度」）
     *
     * @param rich 文本
     * @return 像素
     */
    int naturalWidth(Rich rich) {
        if (rich.isEmpty()) {
            return 0;
        }
        Flat f = flatten(rich);
        float best = 0f;
        float cur = 0f;
        for (int i = 0; i < f.n; i++) {
            if (f.cps[i] == '\n') {
                best = Math.max(best, cur);
                cur = 0f;
            } else {
                cur += f.w[i];
            }
        }
        return (int) Math.ceil(Math.max(best, cur));
    }

    /**
     * 最宽的不可断单元（表格列宽的下限：再窄就得把词劈开）
     *
     * @param rich 文本
     * @return 像素
     */
    int minWidth(Rich rich) {
        if (rich.isEmpty()) {
            return 0;
        }
        Flat f = flatten(rich);
        float best = 0f;
        float cur = 0f;
        for (int i = 0; i < f.n; i++) {
            int c = f.cps[i];
            if (c == '\n' || c == ' ') {
                best = Math.max(best, cur);
                cur = 0f;
                continue;
            }
            if (i > 0 && canBreak(f.cps, i)) {
                best = Math.max(best, cur);
                cur = 0f;
            }
            cur += f.w[i];
        }
        return (int) Math.ceil(Math.max(best, cur));
    }

    /**
     * 纯文本的宽度（与折行用同一套字宽，列表序号这类要和正文对齐的地方用它）
     *
     * @param s 文本
     * @return 像素
     */
    int width(String s) {
        float w = 0f;
        for (int off = 0; off < s.length(); ) {
            int cp = s.codePointAt(off);
            off += Character.charCount(cp);
            w += charWidth(cp, false, Style.EMPTY);
        }
        return (int) Math.ceil(w);
    }

    /**
     * 纯文本折行，最多 maxLines 行；放不下的部分在最后一行末尾补省略号
     *
     * <p>首页格子的副标题用：它要居中、要截断，而片段序列不方便截断，所以直接给字符串。</p>
     *
     * @param text     文本
     * @param width    行宽
     * @param maxLines 最多几行
     * @return 各行文本
     */
    List<String> wrapPlain(String text, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty() || maxLines <= 0) {
            return out;
        }
        Line[] lines = wrap(GuideMarkup.literal(text, GuideTheme.ASH), width);
        for (int i = 0; i < lines.length && i < maxLines; i++) {
            StringBuilder sb = new StringBuilder();
            for (Piece p : lines[i].pieces) {
                sb.append(p.text);
            }
            String s = sb.toString();
            if (i == maxLines - 1 && lines.length > maxLines) {
                int ell = font.width("…");
                s = font.width(s) + ell <= width ? s + "…" : font.plainSubstrByWidth(s, Math.max(0, width - ell)) + "…";
            }
            out.add(s);
        }
        return out;
    }

    // ==================== 断行规则 ====================

    /**
     * 能否在 i-1 与 i 之间断开
     */
    private static boolean canBreak(int[] cps, int i) {
        int a = cps[i - 1];
        int b = cps[i];
        if (b == ' ' || a == ' ') {
            return true;
        }
        if (NO_LINE_START.indexOf(b) >= 0) {
            return false;
        }
        if (NO_LINE_END.indexOf(a) >= 0) {
            return false;
        }
        if (isCjk(a) || isCjk(b)) {
            return true;
        }
        // 英文里的连字符、斜杠后面可以断：长复合词（fire/ice、wide-area）不至于整个挤到下一行
        return a == '-' || a == '/';
    }

    private static boolean isCjk(int cp) {
        if (cp < 0x2E80) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL) {
            return true;
        }
        // 全角标点、CJK 符号块也逐字可断（是否能放行首另由避头表管）
        return (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    // ==================== 内部 ====================

    /** 拍平后的码点序列：每个码点记着属于哪个片段、宽多少 */
    private static final class Flat {
        int[] cps;
        int[] span;
        float[] w;
        int n;
        Style[] styles;
        Style[] hoverStyles;
        Rich rich;
    }

    private Flat flatten(Rich rich) {
        List<Span> spans = rich.spans;
        int total = 0;
        for (Span s : spans) {
            total += s.text.codePointCount(0, s.text.length());
        }
        Flat f = new Flat();
        f.rich = rich;
        f.cps = new int[total];
        f.span = new int[total];
        f.w = new float[total];
        f.styles = new Style[spans.size()];
        f.hoverStyles = new Style[spans.size()];
        int k = 0;
        for (int si = 0; si < spans.size(); si++) {
            Span s = spans.get(si);
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(s.color & 0xFFFFFF)).withBold(s.bold);
            f.styles[si] = style;
            if (s.link != null && s.link.isLive()) {
                f.hoverStyles[si] = Style.EMPTY.withColor(TextColor.fromRgb(GuideTheme.KUVA & 0xFFFFFF)).withBold(s.bold);
            }
            String t = s.text;
            for (int off = 0; off < t.length(); ) {
                int cp = t.codePointAt(off);
                off += Character.charCount(cp);
                f.cps[k] = cp;
                f.span[k] = si;
                f.w[k] = cp == '\n' ? 0f : charWidth(cp, s.bold, style);
                k++;
            }
        }
        f.n = k;
        return f;
    }

    private float charWidth(int cp, boolean bold, Style style) {
        int key = cp * 2 + (bold ? 1 : 0);
        if (widthCache.containsKey(key)) {
            return widthCache.get(key);
        }
        float w = measure.width(cp, style);
        widthCache.put(key, w);
        return w;
    }

    private Line build(Flat f, int start, int end) {
        // 行尾空格不计宽度，也不画
        while (end > start && f.cps[end - 1] == ' ') {
            end--;
        }
        if (end <= start) {
            return new Line(Line.NONE, 0);
        }
        List<Piece> pieces = new ArrayList<>(2);
        float x = 0f;
        int a = start;
        while (a < end) {
            int si = f.span[a];
            int b = a;
            float w = 0f;
            while (b < end && f.span[b] == si) {
                w += f.w[b];
                b++;
            }
            String text = new String(f.cps, a, b - a);
            Span span = f.rich.spans.get(si);
            FormattedCharSequence seq = FormattedCharSequence.forward(text, f.styles[si]);
            FormattedCharSequence hover = f.hoverStyles[si] != null
                    ? FormattedCharSequence.forward(text, f.hoverStyles[si]) : null;
            int px = Math.round(x);
            int pw = Math.round(x + w) - px;
            pieces.add(new Piece(text, seq, hover, px, pw, span.link));
            x += w;
            a = b;
        }
        return new Line(pieces.toArray(new Piece[0]), (int) Math.ceil(x));
    }
}
