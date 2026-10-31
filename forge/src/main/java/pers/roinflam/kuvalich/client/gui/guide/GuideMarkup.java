package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 行内标记解析：把一段原文变成带样式的片段序列
 *
 * <p>支持的写法见内容格式规范 §1.3：颜色 / 强调标签（可嵌套）、{@code {cfg:…}} 等占位符、
 * {@code [[分类/条目|文字]]} 链接、反斜杠转义。{@code {item:…}} 展开成<b>物品链接</b>：物品名加点线下划线，
 * 悬停出物品提示（和物品格一样）；不跳转。</p>
 *
 * <h3>故障段 {@code <x>…</x>}</h3>
 * <p>Ordis 说话卡带的那一段（从第一个卡住的字起，到最后一个 {@code --} 止）。它不改颜色，只给片段打上
 * {@link Span#glitch} 标记，由排版拆成逐字的小片段、绘制时画成撕裂错色（{@link GuideRender#line}）。
 * 字照常显示，{@code --} 也显示；纯文本（搜索、复制）里只去掉标签。</p>
 *
 * <h3>坏标记怎么办</h3>
 * <p>这是给人写的格式，写错是常态。原则是「尽量照字面显示，绝不抛异常」：
 * 不认识的 {@code <…>} 原样显示尖括号；缺右花括号的 {@code {} 原样显示（后文还有别的占位符时也一样，
 * 不会一路吞到那个占位符的右括号）；链接目标格式不对或已被藏掉时按正文显示、不可点；
 * 闭合标签对不上栈顶时往下找同名的一层一起弹掉，找不到就忽略；到结尾还没闭合的自动闭合。
 * 校验脚本会把这些报成错误，界面这边只负责不崩、不吞字。</p>
 *
 * <h3>汉字和字母、数字之间自动补空格</h3>
 * <p>中文排版的习惯是汉字与字母、数字之间空一个半角空格，可占位符展开出来的是什么字，写的时候看不到
 * （物品名「Gram Prime」、配置值「10」），没法手动空。所以解析完之后统一补：占位符或链接展开出的文字<b>首尾</b>是字母 / 数字，
 * 又紧挨着汉字时，在两者之间补一个空格。判断看的是两边相邻的<b>可见字符</b>（颜色标签不产生字符，自然就跨过去了）；
 * 原文里已经写了空格的，相邻字符是空格，不会重复补。普通文字之间的中英混排不管，照写法要求自己写
 * （「赤毒玄骸 冒险指南」这类手写的空格不受影响）。</p>
 *
 * <h3>为什么解析和排版分开</h3>
 * <p>解析的结果只和「原文 + 配置快照」有关，排版还和宽度有关。窗口缩放时只需要重排，
 * 搜索索引只需要解析出的纯文本，两边都不必重复对方的工作。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class GuideMarkup {

    private static final Pattern TAG = Pattern.compile("<(/?)([a-z]+)(?::([0-9A-Fa-f]{6}))?>");
    private static final Pattern LINK_TARGET = Pattern.compile("[a-z0-9_]+(/[a-z0-9_]+)?");

    private GuideMarkup() {}

    /**
     * 一段同样式的文字
     */
    public static final class Span {
        public final String text;
        /** ARGB */
        public final int color;
        public final boolean bold;
        /** 属于哪个链接；不是链接为 null */
        @Nullable
        public final Link link;
        /** 哪一个占位符 / 链接展开出来的（每处一个对象，补空格时按它分界）；普通文字为 null */
        @Nullable
        final Object token;
        /** 在 {@code <x>…</x>} 故障段里 */
        public final boolean glitch;

        Span(String text, int color, boolean bold, @Nullable Link link, @Nullable Object token) {
            this(text, color, bold, link, token, false);
        }

        Span(String text, int color, boolean bold, @Nullable Link link, @Nullable Object token, boolean glitch) {
            this.text = text;
            this.color = color;
            this.bold = bold;
            this.link = link;
            this.token = token;
            this.glitch = glitch;
        }
    }

    /**
     * 一个链接：条目链接（{@code [[分类/条目]]}）或物品链接（{@code {item:…}}）。一个链接折行后会拆成多个片段，
     * 它们共享同一个 Link 对象 —— 悬停其中一段时整条链接一起变色，靠的就是对象同一性。
     *
     * <p><b>流出解析器的条目链接一律可跳转</b>（{@link #entryKey} 非 null）：目标不存在、格式不对、
     * 被条件藏掉的「死链」不生成 Link，由 {@code parseInto} 按外层正文原样输出。
     * 物品链接一律带物品（{@link #stack}），只出提示、不跳转 —— 点击时界面只认 {@link #entryKey}。</p>
     */
    public static final class Link {
        public final String target;
        /** 条目链接的目标键（分类/条目）；物品链接为 null */
        @Nullable
        public final String entryKey;
        /** 物品链接的物品；条目链接为 EMPTY */
        public final ItemStack stack;

        Link(String target, @Nullable String entryKey) {
            this(target, entryKey, ItemStack.EMPTY);
        }

        Link(String target, @Nullable String entryKey, ItemStack stack) {
            this.target = target;
            this.entryKey = entryKey;
            this.stack = stack;
        }

        public boolean isLive() {
            return entryKey != null || !stack.isEmpty();
        }

        public boolean isItem() {
            return !stack.isEmpty();
        }
    }

    /**
     * 解析结果
     */
    public static final class Rich {
        public static final Rich EMPTY = new Rich(List.of());

        public final List<Span> spans;
        private String plain;

        Rich(List<Span> spans) {
            this.spans = spans;
        }

        public boolean isEmpty() {
            return spans.isEmpty();
        }

        /**
         * 去掉样式后的纯文本（搜索、列表标题用）
         *
         * @return 纯文本
         */
        public String plain() {
            if (plain == null) {
                StringBuilder sb = new StringBuilder();
                for (Span s : spans) {
                    sb.append(s.text);
                }
                plain = sb.toString();
            }
            return plain;
        }
    }

    /**
     * 链接目标的解析结果
     *
     * @param entryKey 可跳转的条目键；不可跳转时为 null
     * @param title    没写显示文字时用的标题（目标完全不存在时就是原始 id）
     */
    public record LinkTarget(@Nullable String entryKey, String title) {}

    /**
     * 解析时需要的外部信息
     */
    public interface Resolver {
        /**
         * @param token 花括号里的内容
         * @return 解析结果
         */
        GuideValues.Resolved placeholder(String token);

        /**
         * @param target {@code 分类} 或 {@code 分类/条目}
         * @return 链接目标
         */
        LinkTarget link(String target);
    }

    /**
     * 不解析标记，整段当作一种样式的纯文本（界面自己的字样用）
     *
     * @param text  文本
     * @param color 颜色
     * @return 片段序列
     */
    @Nonnull
    public static Rich literal(@Nullable String text, int color) {
        if (text == null || text.isEmpty()) {
            return Rich.EMPTY;
        }
        return new Rich(List.of(new Span(text, color, false, null, null)));
    }

    /**
     * 解析一段原文
     *
     * @param raw       原文
     * @param resolver  占位符与链接的解析
     * @param baseColor 默认文字色
     * @param baseBold  默认是否加粗
     * @return 片段序列
     */
    @Nonnull
    public static Rich parse(@Nullable String raw, @Nonnull Resolver resolver, int baseColor, boolean baseBold) {
        if (raw == null || raw.isEmpty()) {
            return Rich.EMPTY;
        }
        Builder out = new Builder();
        parseInto(raw, resolver, baseColor, baseBold, null, null, out);
        out.flush();
        return new Rich(autoSpace(out.spans));
    }

    /**
     * @param link  外层链接（正在解析某条链接的显示文字时），顶层为 null
     * @param token 外层占位符 / 链接的标识（补空格时整条链接当一个整体），顶层为 null
     */
    private static void parseInto(String raw, Resolver resolver, int baseColor, boolean baseBold,
                                  @Nullable Link link, @Nullable Object token, Builder out) {
        // 样式栈：tags[i] 是第 i 层的标签名，colors / bolds 是进入该层后的样式
        List<String> tags = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        List<Boolean> bolds = new ArrayList<>();
        int color = baseColor;
        boolean bold = baseBold;
        int n = raw.length();
        int i = 0;
        Matcher tagMatcher = TAG.matcher(raw);
        while (i < n) {
            char ch = raw.charAt(i);
            if (ch == '\\') {
                if (i + 1 < n) {
                    out.append(raw.charAt(i + 1), color, bold, link, token);
                    i += 2;
                } else {
                    out.append(ch, color, bold, link, token);
                    i++;
                }
                continue;
            }
            if (ch == '\r') {
                i++;
                continue;
            }
            if (ch == '\t') {
                out.append(' ', color, bold, link, token);
                i++;
                continue;
            }
            if (ch == '<') {
                tagMatcher.region(i, n);
                if (tagMatcher.lookingAt()) {
                    boolean closing = !tagMatcher.group(1).isEmpty();
                    String tag = tagMatcher.group(2);
                    String hex = tagMatcher.group(3);
                    if ("x".equals(tag) && hex == null) {
                        // 故障段不进样式栈：它不改颜色，套在别的标签里外都行；多出来的 </x> 忽略
                        if (!closing) {
                            out.glitchDepth++;
                        } else if (out.glitchDepth > 0) {
                            out.glitchDepth--;
                        }
                        i = tagMatcher.end();
                        continue;
                    }
                    int tagColor = tagColor(tag, hex);
                    if (tagColor != 0) {
                        if (!closing) {
                            tags.add(tag);
                            colors.add(color);
                            bolds.add(bold);
                            // em 是「亮白加粗」；里面再套颜色标签时颜色换掉、加粗保留
                            color = link != null ? color : tagColor;
                            bold = bold || "em".equals(tag);
                        } else {
                            int at = tags.lastIndexOf(tag);
                            if (at >= 0) {
                                color = colors.get(at);
                                bold = bolds.get(at);
                                while (tags.size() > at) {
                                    int last = tags.size() - 1;
                                    tags.remove(last);
                                    colors.remove(last);
                                    bolds.remove(last);
                                }
                            }
                        }
                        i = tagMatcher.end();
                        continue;
                    }
                }
                out.append(ch, color, bold, link, token);
                i++;
                continue;
            }
            if (ch == '{') {
                int j = raw.indexOf('}', i + 1);
                if (j < 0) {
                    out.append(ch, color, bold, link, token);
                    i++;
                    continue;
                }
                String inner = raw.substring(i + 1, j);
                int nested = inner.indexOf('{');
                if (nested >= 0) {
                    // 落单的 {：找到的 } 其实属于后面另一个占位符。整段当 token 会把中间的字连同
                    // 那个本来好好的占位符一起吞成一个「?」—— 这个 { 按字面输出，后面的照常解析
                    GuideLog.warnOnce("brace:" + raw, "落单的 {（缺右花括号，要显示花括号请写 \\{）: "
                            + raw.substring(i, Math.min(n, i + 1 + nested + 24)));
                    out.append(ch, color, bold, link, token);
                    i++;
                    continue;
                }
                GuideValues.Resolved r = resolver.placeholder(inner);
                // 每处占位符一个标识：补空格按「这一处展开出的文字」的首尾判断。在链接里时整条链接算一处
                Object tok = token != null ? token : new Object();
                if (!r.stack.isEmpty() && link == null && !r.missing) {
                    // 物品名做成物品链接：物品链接色 + 点线下划线，悬停出物品提示
                    Link l = new Link(inner, null, r.stack);
                    out.append(r.text, GuideTheme.ITEM_LINK, bold, l, tok);
                } else {
                    int c = r.missing && link == null ? GuideTheme.MISSING : color;
                    out.append(r.text, c, bold, link, tok);
                }
                i = j + 1;
                continue;
            }
            if (ch == '[' && i + 1 < n && raw.charAt(i + 1) == '[' && link == null) {
                int j = raw.indexOf("]]", i + 2);
                if (j >= 0) {
                    String body = raw.substring(i + 2, j);
                    int pipe = body.indexOf('|');
                    String target = (pipe < 0 ? body : body.substring(0, pipe)).trim();
                    String display = pipe < 0 ? null : body.substring(pipe + 1);
                    LinkTarget resolved;
                    if (LINK_TARGET.matcher(target).matches()) {
                        resolved = resolver.link(target);
                    } else {
                        // 大写、三段路径、中间带空格……：按死链处理（下面照正文显示原文），但要留一条日志，
                        // 不然不跑校验脚本的整合包作者 / 翻译者查不到链接为什么点不动
                        GuideLog.warnOnce("linkfmt:" + target, "链接目标格式不对（只能是小写的 分类 或 分类/条目）: [[" + target + "]]");
                        resolved = new LinkTarget(null, target);
                    }
                    Object tok = new Object();
                    if (resolved.entryKey() == null) {
                        // 目标不存在或被条件藏掉了（对应玩法没开）：按外层正文显示，不可点、不提示，
                        // 玩家看不出这里原本是个链接 —— 书里不该让人察觉「有东西没开」。
                        // 这里不生成 Link，所以流出解析器的 Link 一律可跳转
                        if (display == null) {
                            out.append(resolved.title(), color, bold, null, tok);
                        } else {
                            parseInto(display, resolver, color, bold, null, tok, out);
                        }
                        i = j + 2;
                        continue;
                    }
                    Link l = new Link(target, resolved.entryKey());
                    int linkColor = GuideTheme.LINK;
                    if (display == null) {
                        out.append(resolved.title(), linkColor, bold, l, tok);
                    } else {
                        parseInto(display, resolver, linkColor, bold, l, tok, out);
                    }
                    i = j + 2;
                    continue;
                }
            }
            out.append(ch, color, bold, link, token);
            i++;
        }
    }

    /**
     * 标签对应的颜色；不认识的标签返回 0（按字面显示）
     */
    private static int tagColor(String tag, @Nullable String hex) {
        if (hex != null) {
            if (!"c".equals(tag)) {
                return 0;
            }
            return 0xFF000000 | Integer.parseInt(hex, 16);
        }
        return switch (tag) {
            case "hl" -> GuideTheme.TAG_HL;
            case "t" -> GuideTheme.TAG_T;
            case "w" -> GuideTheme.TAG_W;
            case "g" -> GuideTheme.TAG_G;
            case "b" -> GuideTheme.TAG_B;
            case "dim" -> GuideTheme.TAG_DIM;
            case "em" -> GuideTheme.TAG_EM;
            // </c> 闭合标签不带颜色；开标签 <c> 缺颜色时按正文色处理（写错也不吞字）
            case "c" -> GuideTheme.BODY;
            default -> 0;
        };
    }

    // ==================== 自动补空格 ====================

    /**
     * 在「占位符 / 链接展开的文字」与相邻汉字之间补半角空格（规则见类注释）。没有要补的地方时原样返回。
     */
    static List<Span> autoSpace(List<Span> spans) {
        List<Span> out = null;
        for (int i = 0; i < spans.size(); i++) {
            Span cur = spans.get(i);
            if (i > 0) {
                Span prev = spans.get(i - 1);
                if (prev.token != cur.token && needsSpace(prev, cur)) {
                    if (out == null) {
                        out = new ArrayList<>(spans.subList(0, i));
                    }
                    // 空格跟着「普通文字」那一侧的样式走，且不属于任何链接（链接下划线不会多出一截）
                    Span plainSide = prev.token == null ? prev : cur.token == null ? cur : prev;
                    out.add(new Span(" ", plainSide.color, plainSide.bold, null, null));
                }
            }
            if (out != null) {
                out.add(cur);
            }
        }
        return out == null ? spans : out;
    }

    private static boolean needsSpace(Span prev, Span cur) {
        if (prev.text.isEmpty() || cur.text.isEmpty()) {
            return false;
        }
        int a = prev.text.codePointBefore(prev.text.length());
        int b = cur.text.codePointAt(0);
        // 左边是占位符、以字母 / 数字结尾，右边紧跟汉字；或者左边汉字、右边是以字母 / 数字开头的占位符
        return prev.token != null && isLatin(a) && isHan(b)
                || cur.token != null && isHan(a) && isLatin(b);
    }

    /** 汉字、假名、谚文（不含全角标点：字母和全角标点之间不空格） */
    private static boolean isHan(int cp) {
        if (cp < 0x2E80) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
    }

    private static boolean isLatin(int cp) {
        return cp < 0x2E80 && Character.isLetterOrDigit(cp);
    }

    /**
     * 边解析边合并：连续同样式、同一处来源的字符攒成一个片段
     */
    private static final class Builder {
        final List<Span> spans = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        int color;
        boolean bold;
        Link link;
        Object token;
        /** 当前在几层 {@code <x>} 里（链接显示文字的递归解析共用同一个 Builder，所以放这里） */
        int glitchDepth;
        /** 攒着的这段文字是不是故障段 */
        boolean glitch;

        void append(char ch, int c, boolean b, @Nullable Link l, @Nullable Object t) {
            switchTo(c, b, l, t);
            text.append(ch);
        }

        void append(String s, int c, boolean b, @Nullable Link l, @Nullable Object t) {
            if (s.isEmpty()) {
                return;
            }
            switchTo(c, b, l, t);
            text.append(s);
        }

        private void switchTo(int c, boolean b, @Nullable Link l, @Nullable Object t) {
            boolean g = glitchDepth > 0;
            if (text.length() > 0 && (c != color || b != bold || l != link || t != token || g != glitch)) {
                flush();
            }
            color = c;
            bold = b;
            link = l;
            token = t;
            glitch = g;
        }

        void flush() {
            if (text.length() > 0) {
                spans.add(new Span(text.toString(), color, bold, link, token, glitch));
                text.setLength(0);
            }
        }
    }
}
