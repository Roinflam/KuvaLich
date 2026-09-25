package pers.roinflam.kuvalich.client.gui.guide;

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
 * {@code [[分类/条目|文字]]} 链接、反斜杠转义。</p>
 *
 * <h3>坏标记怎么办</h3>
 * <p>这是给人写的格式，写错是常态。原则是「尽量照字面显示，绝不抛异常」：
 * 不认识的 {@code <…>} 原样显示尖括号；缺右花括号的 {@code {} 原样显示（后文还有别的占位符时也一样，
 * 不会一路吞到那个占位符的右括号）；链接目标格式不对或已被藏掉时按正文显示、不可点；
 * 闭合标签对不上栈顶时往下找同名的一层一起弹掉，找不到就忽略；到结尾还没闭合的自动闭合。
 * 校验脚本会把这些报成错误，界面这边只负责不崩、不吞字。</p>
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

        Span(String text, int color, boolean bold, @Nullable Link link) {
            this.text = text;
            this.color = color;
            this.bold = bold;
            this.link = link;
        }
    }

    /**
     * 一个链接。一个链接折行后会拆成多个片段，它们共享同一个 Link 对象 ——
     * 悬停其中一段时整条链接一起变色，靠的就是对象同一性。
     *
     * <p><b>流出解析器的 Link 一律可跳转</b>（{@link #entryKey} 非 null）：目标不存在、格式不对、
     * 被条件藏掉的「死链」不生成 Link，由 {@code parseInto} 按外层正文原样输出。
     * 界面各处的 {@link #isLive()} 判断因此恒为真，留着只作防御。</p>
     */
    public static final class Link {
        public final String target;
        /** 目标条目的键（分类/条目）。解析器只为可跳转的目标生成 Link，所以实际不会是 null */
        @Nullable
        public final String entryKey;

        Link(String target, @Nullable String entryKey) {
            this.target = target;
            this.entryKey = entryKey;
        }

        public boolean isLive() {
            return entryKey != null;
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
        return new Rich(List.of(new Span(text, color, false, null)));
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
        parseInto(raw, resolver, baseColor, baseBold, null, out);
        out.flush();
        return new Rich(out.spans);
    }

    private static void parseInto(String raw, Resolver resolver, int baseColor, boolean baseBold,
                                  @Nullable Link link, Builder out) {
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
                    out.append(raw.charAt(i + 1), color, bold, link);
                    i += 2;
                } else {
                    out.append(ch, color, bold, link);
                    i++;
                }
                continue;
            }
            if (ch == '\r') {
                i++;
                continue;
            }
            if (ch == '\t') {
                out.append(' ', color, bold, link);
                i++;
                continue;
            }
            if (ch == '<') {
                tagMatcher.region(i, n);
                if (tagMatcher.lookingAt()) {
                    boolean closing = !tagMatcher.group(1).isEmpty();
                    String tag = tagMatcher.group(2);
                    String hex = tagMatcher.group(3);
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
                out.append(ch, color, bold, link);
                i++;
                continue;
            }
            if (ch == '{') {
                int j = raw.indexOf('}', i + 1);
                if (j < 0) {
                    out.append(ch, color, bold, link);
                    i++;
                    continue;
                }
                String token = raw.substring(i + 1, j);
                int inner = token.indexOf('{');
                if (inner >= 0) {
                    // 落单的 {：找到的 } 其实属于后面另一个占位符。整段当 token 会把中间的字连同
                    // 那个本来好好的占位符一起吞成一个「?」—— 这个 { 按字面输出，后面的照常解析
                    GuideLog.warnOnce("brace:" + raw, "落单的 {（缺右花括号，要显示花括号请写 \\{）: "
                            + raw.substring(i, Math.min(n, i + 1 + inner + 24)));
                    out.append(ch, color, bold, link);
                    i++;
                    continue;
                }
                GuideValues.Resolved r = resolver.placeholder(token);
                int c = r.missing && link == null ? GuideTheme.MISSING : color;
                out.append(r.text, c, bold, link);
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
                    if (resolved.entryKey() == null) {
                        // 目标不存在或被条件藏掉了（对应玩法没开）：按外层正文显示，不可点、不提示，
                        // 玩家看不出这里原本是个链接 —— 书里不该让人察觉「有东西没开」。
                        // 这里不生成 Link，所以流出解析器的 Link 一律可跳转
                        if (display == null) {
                            out.append(resolved.title(), color, bold, link);
                        } else {
                            parseInto(display, resolver, color, bold, link, out);
                        }
                        i = j + 2;
                        continue;
                    }
                    Link l = new Link(target, resolved.entryKey());
                    int linkColor = GuideTheme.LINK;
                    if (display == null) {
                        out.append(resolved.title(), linkColor, bold, l);
                    } else {
                        parseInto(display, resolver, linkColor, bold, l, out);
                    }
                    i = j + 2;
                    continue;
                }
            }
            out.append(ch, color, bold, link);
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

    /**
     * 边解析边合并：连续同样式的字符攒成一个片段
     */
    private static final class Builder {
        final List<Span> spans = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        int color;
        boolean bold;
        Link link;

        void append(char ch, int c, boolean b, @Nullable Link l) {
            switchTo(c, b, l);
            text.append(ch);
        }

        void append(String s, int c, boolean b, @Nullable Link l) {
            if (s.isEmpty()) {
                return;
            }
            switchTo(c, b, l);
            text.append(s);
        }

        private void switchTo(int c, boolean b, @Nullable Link l) {
            if (text.length() > 0 && (c != color || b != bold || l != link)) {
                flush();
            }
            color = c;
            bold = b;
            link = l;
        }

        void flush() {
            if (text.length() > 0) {
                spans.add(new Span(text.toString(), color, bold, link));
                text.setLength(0);
            }
        }
    }
}
