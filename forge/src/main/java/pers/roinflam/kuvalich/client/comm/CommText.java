package pers.roinflam.kuvalich.client.comm;

import net.minecraft.ChatFormatting;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.lich.LichVoice;
import pers.roinflam.kuvalich.comm.CommSpeaker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 通讯窗的文字：整句软边擦出、名牌、Ordis 的故障段
 *
 * <p><b>整句擦出</b>：把几行字接成一条，算出每个字形在这一条上的起点 x。擦出前沿
 * {@code f = (总宽 + F) × 进度}，F 是软边宽度（通讯窗 24 字体像素，指南 18 指南像素）；每个字形的不透明度
 * {@code clamp((f − x) / F, 0, 1)}。不透明度低于 0.1 的字形不画：原版字体把 α 低于 4/255 的颜色当成不透明，
 * 直接画会闪一下满亮。擦出期间逐字形画（一次批量提交），擦完后没有故障段的行整行画。</p>
 *
 * <p><b>故障段</b>：台词里用 {@code <x>…</x>} 包住的一段（只有 Ordis 用）。{@link #parse} 去掉标记、
 * 给这一段的字打上 {@link #GLITCH} 样式（只带一个插入文本，不影响宽度和颜色），画的时候认这个样式：先画两层残影
 * （左下 1 像素品红、右上 1 像素青，α 0.45），再画本体，本体逐字按 0、+1、−1 循环水平错开 1 像素。静态，不闪。</p>
 *
 * <p><b>名牌</b>：粗体、拉丁字母转大写、拉丁字母之间多空 2 像素、汉字之间多空 1 像素、发言人色、带阴影。</p>
 *
 * <p>坐标都是当前姿态矩阵下的单位，一个字体像素 = 一个单位（通讯窗先把姿态缩放到字号，指南直接用指南像素）。
 * 字形、宽度按传进来的对象缓存，同一个列表 / 名字每帧传同一个对象就不会重复算，画的时候不分配。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class CommText {

    /** 通讯窗擦出前沿的软边宽度，字体像素 */
    public static final int FEATHER = 24;
    /** 低于这个不透明度的字形跳过不画 */
    public static final float MIN_GLYPH_ALPHA = 0.1F;
    /** 字幕颜色 */
    public static final int SUBTITLE_RGB = 0xF0F0F4;
    /** 擦出用时的上下限 */
    public static final int WIPE_MIN_MS = 150;
    public static final int WIPE_MAX_MS = 700;

    /** 故障段的样式：只带一个插入文本作记号，宽度、颜色和普通字一样 */
    public static final Style GLITCH = Style.EMPTY.withInsertion("kuvalich:glitch");
    private static final String GLITCH_OPEN = "<x>";
    private static final String GLITCH_CLOSE = "</x>";
    /** 故障段两层残影的颜色（偏红的品红、青），α 0.45 */
    private static final int GHOST_MAGENTA = 0xFF4FD8;
    private static final int GHOST_CYAN = 0x4FE8FF;
    private static final float GHOST_ALPHA = 0.45F;
    /** 故障段本体逐字错开的循环 */
    private static final int[] JITTER = {0, 1, -1};

    private static final Style NAME_STYLE = Style.EMPTY.withBold(true);
    /** 名牌字距：拉丁字母、汉字 */
    private static final int NAME_GAP_LATIN = 2;
    private static final int NAME_GAP_HAN = 1;

    private static final GlyphCache LINES_CACHE = new GlyphCache(4);
    private static final GlyphCache NAME_CACHE = new GlyphCache(3);

    private CommText() {
    }

    // ==================== 解析与计数 ====================

    /**
     * 把一句台词的纯文字变成可以排版的文本：去掉颜色代码，认出 {@code <x>…</x>} 故障段（去掉标记、打上 {@link #GLITCH}）。
     * 没闭合的 {@code <x>} 一直算到句末
     */
    public static MutableComponent parse(String raw) {
        String s = ChatFormatting.stripFormatting(raw);
        if (s == null) {
            s = "";
        }
        MutableComponent out = Component.empty();
        int i = 0;
        boolean glitch = false;
        while (i < s.length()) {
            String tag = glitch ? GLITCH_CLOSE : GLITCH_OPEN;
            int j = s.indexOf(tag, i);
            int end = j < 0 ? s.length() : j;
            if (end > i) {
                out.append(Component.literal(s.substring(i, end)).withStyle(glitch ? GLITCH : Style.EMPTY));
            }
            if (j < 0) {
                break;
            }
            i = j + tag.length();
            glitch = !glitch;
        }
        return out;
    }

    /** 只去掉颜色代码，故障段标记留着（拆段时用） */
    public static String stripColors(String raw) {
        String s = ChatFormatting.stripFormatting(raw);
        return s != null ? s : "";
    }

    /** 去掉颜色代码和故障段标记后的纯文字（计数、聊天栏、嘟囔用） */
    public static String plain(String raw) {
        String s = ChatFormatting.stripFormatting(raw);
        if (s == null) {
            return "";
        }
        return s.indexOf('<') < 0 ? s : s.replace(GLITCH_OPEN, "").replace(GLITCH_CLOSE, "");
    }

    /** 这个字形是不是故障段里的 */
    public static boolean isGlitch(Style style) {
        return GLITCH.getInsertion().equals(style.getInsertion());
    }

    /** 汉字数（连同假名、韩文） */
    public static int hanCount(String plain) {
        int n = 0;
        for (int i = 0; i < plain.length(); ) {
            int cp = plain.codePointAt(i);
            i += Character.charCount(cp);
            if (LichVoice.isHan(cp)) {
                n++;
            }
        }
        return n;
    }

    /** 拉丁字符数：汉字以外、不是空白的字符（标点、数字、字母都算） */
    public static int latinCount(String plain) {
        int n = 0;
        for (int i = 0; i < plain.length(); ) {
            int cp = plain.codePointAt(i);
            i += Character.charCount(cp);
            if (!LichVoice.isHan(cp) && !Character.isWhitespace(cp)) {
                n++;
            }
        }
        return n;
    }

    /** 折算字数：汉字算 2、拉丁字符算 1。{@link #wipeProgress} 的第二个参数用它 */
    public static int weightedLength(String plain) {
        return hanCount(plain) * 2 + latinCount(plain);
    }

    /** 擦出用时：clamp(汉字数 × 7 + 拉丁字符数 × 3.5, 150, 700) 毫秒 */
    public static int wipeDurationMs(String plain) {
        return wipeDurationForWeighted(weightedLength(plain));
    }

    private static int wipeDurationForWeighted(int weighted) {
        return Math.max(WIPE_MIN_MS, Math.min(WIPE_MAX_MS, Math.round(weighted * 3.5F)));
    }

    /**
     * 擦出进度 0~1
     *
     * @param elapsedMs 从开始擦出算起过了多久
     * @param charCount 折算字数（{@link #weightedLength}：汉字算 2、拉丁字符算 1），用时 = clamp(折算字数 × 3.5, 150, 700) ms
     */
    public static float wipeProgress(long elapsedMs, int charCount) {
        return progressFor(elapsedMs, wipeDurationForWeighted(charCount));
    }

    /** 给定擦出用时的进度 0~1 */
    public static float progressFor(long elapsedMs, int durationMs) {
        if (elapsedMs <= 0L) {
            return 0.0F;
        }
        return elapsedMs >= durationMs ? 1.0F : elapsedMs / (float) durationMs;
    }

    /** 一个起点在 {@code glyphX} 的字形，在擦出前沿走到 {@code front} 时的不透明度 */
    public static float wipeAlpha(float front, float glyphX, float feather) {
        return Math.max(0.0F, Math.min(1.0F, (front - glyphX) / feather));
    }

    /** 不能出现在行首的字：句读、右括号、右引号、百分号之类 */
    public static final String NO_LINE_START = "，。、；：！？）」』》〉】”’…,.;:!?)]}%·～ー—";
    /** 不能留在行尾的字：左括号、左引号 */
    public static final String NO_LINE_END = "（「『《〈【“‘([{";

    /**
     * 能否在 {@code cps[i-1]} 和 {@code cps[i]} 之间断行（通讯字幕、指南正文共用这一套）：
     * 空格两侧可以断；避头尾表里的不能断；中日韩字符前后都可以断；英文、数字按词，词内不断，
     * 只有连字符、斜杠后面可以断；故障记号 {@code --} 当闭合标点，不放行首、两个连字符之间也不断
     */
    public static boolean canBreak(int[] cps, int i) {
        int a = cps[i - 1];
        int b = cps[i];
        if (b == ' ' || a == ' ') {
            return true;
        }
        if (b == '-' && (a == '-' || i + 1 < cps.length && cps[i + 1] == '-')) {
            return false;
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
        return a == '-' || a == '/';
    }

    /** 中日韩文字、全角标点和 CJK 符号块：逐字可断 */
    public static boolean isCjk(int cp) {
        if (cp < 0x2E80) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        if (script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL) {
            return true;
        }
        return (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    /** 按宽度折行，见 {@link #wrap(Font, FormattedText, int, String...)}，没有要整块保住的词 */
    public static List<FormattedCharSequence> wrap(Font font, FormattedText text, int maxWidth) {
        return wrap(font, text, maxWidth, (String[]) null);
    }

    /**
     * 按宽度折行（宽度单位同字体像素），中日韩友好：
     * <ul>
     *   <li>汉字之间可以断，英文单词整个保住、在空格处断（断开处的空格吃掉）；</li>
     *   <li>避头尾：「，。！？」）」这类不放行首、「（「『」不放行尾，撞上了就连前一个字一起挪到下一行；</li>
     *   <li>{@code keep} 里的词（插进台词的玄骸名，「Zher Vekk」中间那个空格不能断）整个当一个字，
     *       只有它自己比一行还宽时才在它里面断（先找它里面的空格）；</li>
     *   <li>一个词比整行还宽时才按字硬切；台词里的换行符照断；</li>
     *   <li>中文句子最后一行只剩一两个字时并回上一行（最多超出两个全角字宽）。</li>
     * </ul>
     * 原版的 {@code StringSplitter.splitLines} 只认空格，「Vekk！记住这个名字……」整串没有空格会被当成一个词，
     * 结果在名字中间的空格断开、第一行只剩半个名字。故障段的样式会带进每一行
     */
    public static List<FormattedCharSequence> wrap(Font font, FormattedText text, int maxWidth, String... keep) {
        return Language.getInstance().getVisualOrder(wrapLines(font, text, maxWidth, keep));
    }

    /** 同 {@link #wrap(Font, FormattedText, int, String...)}，返回没转成显示顺序的各行 */
    public static List<FormattedText> wrapLines(Font font, FormattedText text, int maxWidth, String... keep) {
        float w = Math.max(8, maxWidth);
        List<Integer> cpList = new ArrayList<>();
        List<Style> styleList = new ArrayList<>();
        text.visit((style, str) -> {
            str.codePoints().forEach(cp -> {
                cpList.add(cp);
                styleList.add(style);
            });
            return Optional.empty();
        }, Style.EMPTY);
        int n = cpList.size();
        int[] cps = new int[n];
        Style[] styles = styleList.toArray(new Style[0]);
        float[] cw = new float[n];
        StringSplitter splitter = font.getSplitter();
        for (int i = 0; i < n; i++) {
            cps[i] = cpList.get(i);
            cw[i] = cps[i] == '\n' ? 0.0F : splitter.stringWidth(FormattedCharSequence.codepoint(cps[i], styles[i]));
        }
        boolean[] brk = new boolean[n + 1];
        for (int i = 1; i < n; i++) {
            brk[i] = canBreak(cps, i);
        }
        // 要整块保住的词：词里面一律不断
        boolean[] inKeep = new boolean[n + 1];
        if (keep != null) {
            for (String k : keep) {
                if (k == null || k.isEmpty()) {
                    continue;
                }
                int[] kc = k.codePoints().toArray();
                for (int s0 = 0; s0 + kc.length <= n; s0++) {
                    int j = 0;
                    while (j < kc.length && cps[s0 + j] == kc[j]) {
                        j++;
                    }
                    if (j == kc.length) {
                        for (int q = s0 + 1; q < s0 + kc.length; q++) {
                            brk[q] = false;
                            inKeep[q] = true;
                        }
                        s0 += kc.length - 1;
                    }
                }
            }
        }

        // 每行的 [起, 止) 码点下标，和这一行是不是因为放不下才断的（台词里的换行符断的不算）
        int[] ls = new int[8];
        int[] le = new int[8];
        boolean[] soft = new boolean[8];
        int count = 0;
        int start = 0;
        while (start < n) {
            while (start < n && cps[start] == ' ') {
                start++;
            }
            if (start >= n) {
                break;
            }
            float x = 0.0F;
            int last = -1;
            int end = n;
            int next = n;
            for (int i = start; i < n; i++) {
                if (cps[i] == '\n') {
                    end = i;
                    next = i + 1;
                    break;
                }
                if (i > start && brk[i]) {
                    last = i;
                }
                if (i > start && x + cw[i] > w) {
                    if (last > start) {
                        end = last;
                    } else {
                        // 这一行里一个能断的地方都没有：保住的词自己就比一行宽，先在它里面的空格断，再不行按字切
                        end = i;
                        for (int q = i - 1; q > start; q--) {
                            if (inKeep[q] && cps[q] == ' ') {
                                end = q;
                                break;
                            }
                        }
                    }
                    next = end;
                    break;
                }
                x += cw[i];
            }
            int e = end;
            while (e > start && cps[e - 1] == ' ') {
                e--;
            }
            if (count == ls.length) {
                ls = Arrays.copyOf(ls, count * 2);
                le = Arrays.copyOf(le, count * 2);
                soft = Arrays.copyOf(soft, count * 2);
            }
            ls[count] = start;
            le[count] = e;
            soft[count] = next == end;
            count++;
            start = next;
        }
        // 防孤字：中文句子最后一行只剩一两个字（「看。」）时并回上一行，最多超出两个全角字宽
        if (count >= 2 && soft[count - 2]) {
            float full = font.width("。");
            float lastW = 0.0F, prevW = 0.0F;
            boolean han = false;
            for (int i = ls[count - 1]; i < le[count - 1]; i++) {
                lastW += cw[i];
                han |= isCjk(cps[i]);
            }
            for (int i = ls[count - 2]; i < le[count - 2]; i++) {
                prevW += cw[i];
            }
            if (han && lastW <= 2.0F * full && ls[count - 1] == le[count - 2] && prevW + lastW <= w + 2.0F * full) {
                le[count - 2] = le[count - 1];
                count--;
            }
        }
        List<FormattedText> lines = new ArrayList<>(Math.max(1, count));
        for (int k = 0; k < count; k++) {
            lines.add(slice(cps, styles, ls[k], le[k]));
        }
        if (lines.isEmpty()) {
            lines.add(FormattedText.EMPTY);
        }
        return lines;
    }

    /** [from, to) 这一段按样式切成几块拼起来 */
    private static FormattedText slice(int[] cps, Style[] styles, int from, int to) {
        List<FormattedText> parts = new ArrayList<>(2);
        int i = from;
        while (i < to) {
            int j = i + 1;
            while (j < to && styles[j].equals(styles[i])) {
                j++;
            }
            parts.add(FormattedText.of(new String(cps, i, j - i), styles[i]));
            i = j;
        }
        return parts.isEmpty() ? FormattedText.EMPTY : FormattedText.composite(parts);
    }

    // ==================== 整句擦出 ====================

    /**
     * 把几行字从左到右擦出来，前沿带软边（通讯窗的 24 字体像素）
     *
     * @param progress 擦出进度 0~1
     */
    public static void drawWipe(GuiGraphics g, Font font, List<FormattedCharSequence> lines, int x, int y,
                                int lineHeight, int argb, float progress) {
        drawWipe(g, font, lines, x, y, lineHeight, argb, progress, FEATHER, true);
    }

    /**
     * 同上，指定软边宽度、要不要阴影（指南的正文 F = 18、不带阴影）
     */
    public static void drawWipe(GuiGraphics g, Font font, List<FormattedCharSequence> lines, int x, int y,
                                int lineHeight, int argb, float progress, float feather, boolean shadow) {
        drawWipe(g, font, lines, (float) x, (float) y, (float) lineHeight, argb, progress, feather, shadow);
    }

    /**
     * 同上，坐标和行距可以是小数（通讯窗的行距按屏幕高算，折成字体像素不一定是整数）
     */
    public static void drawWipe(GuiGraphics g, Font font, List<FormattedCharSequence> lines, float x, float y,
                                float lineHeight, int argb, float progress, float feather, boolean shadow) {
        if (lines.isEmpty() || progress <= 0.0F) {
            return;
        }
        Glyphs gl = LINES_CACHE.lines(font, lines);
        float baseAlpha = ((argb >>> 24) & 0xFF) / 255.0F;
        if ((argb & 0xFF000000) == 0) {
            baseAlpha = 1.0F;
        }
        if (baseAlpha < MIN_GLYPH_ALPHA) {
            return;
        }
        int rgb = argb & 0xFFFFFF;
        boolean done = progress >= 1.0F;
        float front = (gl.total + feather) * Math.min(1.0F, progress);

        DRAW.set(g, font, gl, lines, x, y, lineHeight, rgb, baseAlpha, front, feather, done, shadow);
        g.drawManaged(DRAW);
        DRAW.clear();
    }

    /** 擦出时逐字形画的那一段，写成可复用的对象免得每帧建闭包 */
    private static final class WipeDraw implements Runnable {
        GuiGraphics g;
        Font font;
        Glyphs gl;
        List<FormattedCharSequence> lines;
        int rgb;
        float x, y, lineH, alpha, front, feather;
        boolean done, shadow;

        void set(GuiGraphics g, Font font, Glyphs gl, List<FormattedCharSequence> lines, float x, float y,
                 float lineH, int rgb, float alpha, float front, float feather, boolean done, boolean shadow) {
            this.g = g;
            this.font = font;
            this.gl = gl;
            this.lines = lines;
            this.x = x;
            this.y = y;
            this.lineH = lineH;
            this.rgb = rgb;
            this.alpha = alpha;
            this.front = front;
            this.feather = feather;
            this.done = done;
            this.shadow = shadow;
        }

        void clear() {
            g = null;
            font = null;
            gl = null;
            lines = null;
        }

        @Override
        public void run() {
            if (done) {
                int color = argbOf(rgb, alpha);
                for (int li = 0; li < lines.size(); li++) {
                    if (!gl.lineHasGlitch[li]) {
                        g.drawString(font, lines.get(li), x, y + li * lineH, color, shadow);
                    }
                }
            }
            for (int k = 0; k < gl.count; k++) {
                if (done && !gl.lineHasGlitch[gl.line[k]]) {
                    continue;
                }
                float a = done ? alpha : alpha * wipeAlpha(front, gl.stripX[k], feather);
                if (a < MIN_GLYPH_ALPHA) {
                    continue;
                }
                float gx = x + gl.lineX[k];
                float gy = y + gl.line[k] * lineH;
                if (gl.glitch[k]) {
                    float ga = a * GHOST_ALPHA;
                    if (ga >= MIN_GLYPH_ALPHA) {
                        g.drawString(font, gl.seq[k], gx - 1.0F, gy + 1.0F, argbOf(GHOST_MAGENTA, ga), false);
                        g.drawString(font, gl.seq[k], gx + 1.0F, gy - 1.0F, argbOf(GHOST_CYAN, ga), false);
                    }
                    gx += JITTER[gl.glitchIndex[k] % JITTER.length];
                }
                g.drawString(font, gl.seq[k], gx, gy, argbOf(rgb, a), shadow);
            }
        }
    }

    private static final WipeDraw DRAW = new WipeDraw();

    /** 这几行接成一条的总宽（字体像素） */
    public static int stripWidth(Font font, List<FormattedCharSequence> lines) {
        return Math.round(LINES_CACHE.lines(font, lines).total);
    }

    // ==================== 名牌 ====================

    /** 发言人名牌：粗体、拉丁字母转大写、发言人色、带阴影；(x, y) 是名牌左上角 */
    public static void drawNameplate(GuiGraphics g, Font font, Component name, CommSpeaker who, int x, int y,
                                     float alpha) {
        if (alpha < MIN_GLYPH_ALPHA) {
            return;
        }
        Glyphs gl = NAME_CACHE.name(font, name);
        NAME_DRAW.set(g, font, gl, x, y, argbOf(who.argb() & 0xFFFFFF, alpha));
        g.drawManaged(NAME_DRAW);
        NAME_DRAW.clear();
    }

    /** 名牌画出来有多宽（字体像素，含字距） */
    public static int nameplateWidth(Font font, Component name) {
        return Math.round(NAME_CACHE.name(font, name).total);
    }

    private static final class NameDraw implements Runnable {
        GuiGraphics g;
        Font font;
        Glyphs gl;
        int x, y, color;

        void set(GuiGraphics g, Font font, Glyphs gl, int x, int y, int color) {
            this.g = g;
            this.font = font;
            this.gl = gl;
            this.x = x;
            this.y = y;
            this.color = color;
        }

        void clear() {
            g = null;
            font = null;
            gl = null;
        }

        @Override
        public void run() {
            for (int k = 0; k < gl.count; k++) {
                g.drawString(font, gl.seq[k], x + gl.lineX[k], (float) y, color, true);
            }
        }
    }

    private static final NameDraw NAME_DRAW = new NameDraw();

    // ==================== 字形缓存 ====================

    /** 一段文字拆成的字形：每个字形的序列、在行内 / 整条上的 x、属于第几行、是不是故障段 */
    private static final class Glyphs {
        Object key;
        Font font;
        int count;
        FormattedCharSequence[] seq;
        float[] lineX;
        float[] stripX;
        int[] line;
        boolean[] glitch;
        int[] glitchIndex;
        boolean[] lineHasGlitch;
        float total;
    }

    /** 按对象身份缓存的几份字形，最近用的放前面 */
    private static final class GlyphCache {
        private final Glyphs[] slots;

        GlyphCache(int size) {
            slots = new Glyphs[size];
        }

        private Glyphs find(Object key, Font font) {
            for (int i = 0; i < slots.length; i++) {
                Glyphs s = slots[i];
                if (s != null && s.key == key && s.font == font) {
                    if (i > 0) {
                        System.arraycopy(slots, 0, slots, 1, i);
                        slots[0] = s;
                    }
                    return s;
                }
            }
            return null;
        }

        private Glyphs put(Glyphs gl) {
            System.arraycopy(slots, 0, slots, 1, slots.length - 1);
            slots[0] = gl;
            return gl;
        }

        Glyphs lines(Font font, List<FormattedCharSequence> lines) {
            Glyphs hit = find(lines, font);
            return hit != null ? hit : put(buildLines(font, lines));
        }

        Glyphs name(Font font, Component name) {
            Glyphs hit = find(name, font);
            return hit != null ? hit : put(buildName(font, name));
        }
    }

    private static Glyphs alloc(Object key, Font font, int n, int lineCount) {
        Glyphs gl = new Glyphs();
        gl.key = key;
        gl.font = font;
        gl.seq = new FormattedCharSequence[n];
        gl.lineX = new float[n];
        gl.stripX = new float[n];
        gl.line = new int[n];
        gl.glitch = new boolean[n];
        gl.glitchIndex = new int[n];
        gl.lineHasGlitch = new boolean[Math.max(1, lineCount)];
        return gl;
    }

    private static Glyphs buildLines(Font font, List<FormattedCharSequence> lines) {
        int[] n = {0};
        for (FormattedCharSequence l : lines) {
            l.accept((idx, style, cp) -> {
                n[0]++;
                return true;
            });
        }
        Glyphs gl = alloc(lines, font, n[0], lines.size());
        float[] strip = {0.0F};
        int[] k = {0};
        int[] glitchRun = {0};
        for (int li = 0; li < lines.size(); li++) {
            final int lineIdx = li;
            float[] lx = {0.0F};
            lines.get(li).accept((idx, style, cp) -> {
                int i = k[0]++;
                FormattedCharSequence one = FormattedCharSequence.codepoint(cp, style);
                float w = font.width(one);
                gl.seq[i] = one;
                gl.lineX[i] = lx[0];
                gl.stripX[i] = strip[0];
                gl.line[i] = lineIdx;
                boolean gli = isGlitch(style);
                gl.glitch[i] = gli;
                if (gli) {
                    gl.glitchIndex[i] = glitchRun[0]++;
                    gl.lineHasGlitch[lineIdx] = true;
                } else {
                    glitchRun[0] = 0;
                }
                lx[0] += w;
                strip[0] += w;
                return true;
            });
        }
        gl.count = k[0];
        gl.total = strip[0];
        return gl;
    }

    private static Glyphs buildName(Font font, Component name) {
        String s = ChatFormatting.stripFormatting(name.getString());
        if (s == null) {
            s = "";
        }
        s = s.toUpperCase(Locale.ROOT);
        int n = s.codePointCount(0, s.length());
        Glyphs gl = alloc(name, font, n, 1);
        float x = 0.0F;
        int k = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            FormattedCharSequence one = FormattedCharSequence.codepoint(cp, NAME_STYLE);
            gl.seq[k] = one;
            gl.lineX[k] = x;
            gl.stripX[k] = x;
            x += font.width(one);
            if (i < s.length()) {
                x += LichVoice.isHan(cp) ? NAME_GAP_HAN : (Character.isWhitespace(cp) ? 0 : NAME_GAP_LATIN);
            }
            k++;
        }
        gl.count = k;
        gl.total = x;
        return gl;
    }

    private static int argbOf(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
