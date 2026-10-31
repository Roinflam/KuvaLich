package pers.roinflam.kuvalich.client.comm;

import net.minecraft.ChatFormatting;
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

import java.util.List;
import java.util.Locale;

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

    /** 不该出现在行首的收尾标点 */
    private static final String NO_LINE_START = "。，、；：！？…—」』）》〉,.;:!?)";

    /**
     * 按宽度折行（宽度单位同字体像素）。故障段的样式会带进每一行。
     * 句号、逗号这类收尾标点折到行首时（一句话最后剩个「。」单独占一行最难看），放宽一个全角字的宽度重折，
     * 让它挂在上一行末尾；最多放宽两次，所以最多超出两个字宽
     */
    public static List<FormattedCharSequence> wrap(Font font, FormattedText text, int maxWidth) {
        int w = Math.max(8, maxWidth);
        List<FormattedText> lines = font.getSplitter().splitLines(text, w, Style.EMPTY);
        for (int k = 0; k < 2 && closerAtLineStart(lines); k++) {
            w += font.width("。");
            lines = font.getSplitter().splitLines(text, w, Style.EMPTY);
        }
        return Language.getInstance().getVisualOrder(lines);
    }

    private static boolean closerAtLineStart(List<FormattedText> lines) {
        for (int i = 1; i < lines.size(); i++) {
            String s = lines.get(i).getString();
            if (!s.isEmpty() && NO_LINE_START.indexOf(s.charAt(0)) >= 0) {
                return true;
            }
        }
        return false;
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
