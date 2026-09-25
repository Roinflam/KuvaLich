package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Block;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.EntryView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 全书搜索：标题、摘要、正文（占位符解析之后的纯文本）
 *
 * <h3>匹配规则</h3>
 * <p>查询按空白拆成若干词，<b>每个词都要出现</b>才算命中（「暴击 元素」找同时讲到两者的篇）。
 * 中文直接子串匹配；英文统一转小写。排序：标题命中 &gt; 摘要命中 &gt; 只在正文里命中，同档按书中顺序 ——
 * 搜「塑形块」时，讲塑形块的那篇应该排在所有「顺带提了一句塑形块」的篇前面。</p>
 *
 * <h3>为什么输入时不卡</h3>
 * <p>索引（每篇的小写纯文本）只在第一次搜索时建一次，之后每次按键只是几十次 {@code String.contains}。
 * 另外做了和模组图鉴一样的前缀收窄：新查询以旧查询开头时只在上次的结果里筛 ——
 * 不含「暴」的篇一定不含「暴击」，语义完全等价。</p>
 *
 * <p>正文是按当前配置快照解析的，所以搜「25%」能搜到把没收概率写成 {@code {cfg:…|pctv}} 的那一篇。
 * 被条件藏掉的块不进索引：搜不到本服不存在的机制。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideSearch {

    /** 一篇的索引 */
    private static final class Doc {
        final EntryView entry;
        final String title;
        final String summary;
        final String body;
        /** 正文原样（未转小写），截取上下文用 */
        final String bodyRaw;

        Doc(EntryView entry, String title, String summary, String bodyRaw) {
            this.entry = entry;
            this.title = title.toLowerCase(Locale.ROOT);
            this.summary = summary.toLowerCase(Locale.ROOT);
            this.bodyRaw = bodyRaw;
            this.body = bodyRaw.toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 一条结果
     */
    static final class Hit {
        final EntryView entry;
        /** 0 标题命中，1 摘要命中，2 只在正文里 */
        final int rank;
        /** 正文命中时的上下文片段；标题 / 摘要命中时为空串 */
        final String snippet;
        private final Doc doc;

        Hit(Doc doc, int rank, String snippet) {
            this.doc = doc;
            this.entry = doc.entry;
            this.rank = rank;
            this.snippet = snippet;
        }
    }

    /** 全角空格（U+3000）。用强转写而不写 Unicode 转义：转义在编辑工具链里会被提前解成字符，看不出是哪个空格 */
    private static final char FULLWIDTH_SPACE = (char) 0x3000;

    private final GuideView view;
    /** 查配方产物名用（和正文排版共用一份缓存） */
    private final GuideMedia media;
    private List<Doc> docs;
    private String lastQuery = "";
    private List<Hit> lastHits = List.of();

    GuideSearch(GuideView view, GuideMedia media) {
        this.view = view;
        this.media = media;
    }

    /**
     * 查询的规范形式：全角空格当普通空格，再去掉首尾空白
     *
     * <p>中文输入法全角状态下按空格打出来的是 U+3000，{@code trim()} 去不掉它，
     * 正则的空白类默认也不认它 ——「暴击　元素」会被当成一个词整体去匹配，一条都搜不到；
     * 只打一个全角空格也不算空查询，界面显示「没有找到」。界面判断「查询是否为空」也必须走这里，两边才一致。</p>
     *
     * @param query 用户输入
     * @return 规范化后的查询
     */
    static String normalize(String query) {
        return query.replace(FULLWIDTH_SPACE, ' ').trim();
    }

    /**
     * 搜索
     *
     * @param query 用户输入
     * @return 命中列表（按相关度、书中顺序排好）
     */
    @Nonnull
    List<Hit> search(String query) {
        String q = normalize(query).toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            lastQuery = "";
            lastHits = List.of();
            return lastHits;
        }
        if (q.equals(lastQuery)) {
            return lastHits;
        }
        String[] terms = q.split("\\s+");
        List<Doc> source;
        if (!lastQuery.isEmpty() && q.startsWith(lastQuery)) {
            // 前缀收窄：只在上次的结果里筛
            source = new ArrayList<>(lastHits.size());
            for (Hit h : lastHits) {
                source.add(h.doc);
            }
        } else {
            source = docs();
        }
        List<Hit> hits = new ArrayList<>();
        for (Doc d : source) {
            Hit h = match(d, terms);
            if (h != null) {
                hits.add(h);
            }
        }
        hits.sort(Comparator.comparingInt((Hit h) -> h.rank).thenComparingInt(h -> h.entry.globalIndex));
        lastQuery = q;
        lastHits = hits;
        return hits;
    }

    private static Hit match(Doc d, String[] terms) {
        boolean allTitle = true;
        boolean allHead = true;
        for (String t : terms) {
            boolean inTitle = d.title.contains(t);
            boolean inSummary = d.summary.contains(t);
            if (!inTitle && !inSummary && !d.body.contains(t)) {
                return null;
            }
            allTitle &= inTitle;
            allHead &= inTitle || inSummary;
        }
        int rank = allTitle ? 0 : allHead ? 1 : 2;
        return new Hit(d, rank, rank == 2 ? snippet(d, terms) : "");
    }

    /**
     * 正文里第一个命中词前后各取一点，前后被截掉的补省略号
     */
    private static String snippet(Doc d, String[] terms) {
        int at = -1;
        int len = 0;
        for (String t : terms) {
            int i = d.body.indexOf(t);
            if (i >= 0 && (at < 0 || i < at)) {
                at = i;
                len = t.length();
            }
        }
        if (at < 0 || d.bodyRaw.length() != d.body.length()) {
            // 转小写改变了长度（极少数字符）：下标对不上，宁可不给片段
            return "";
        }
        int from = Math.max(0, at - 6);
        int to = Math.min(d.bodyRaw.length(), at + len + 24);
        String s = d.bodyRaw.substring(from, to).trim();
        return (from > 0 ? "…" : "") + s + (to < d.bodyRaw.length() ? "…" : "");
    }

    private List<Doc> docs() {
        if (docs == null) {
            List<Doc> out = new ArrayList<>(view.entryList.size());
            for (EntryView ev : view.entryList) {
                out.add(new Doc(ev, ev.title(), ev.summary(), bodyText(ev)));
            }
            docs = out;
        }
        return docs;
    }

    private String bodyText(EntryView ev) {
        StringBuilder sb = new StringBuilder();
        for (Block raw : ev.entry.blocks) {
            if (!view.values.test(raw.cond)) {
                continue;
            }
            // 行级条件藏掉的行 / 项也不进索引：搜得到却在正文里找不到，最让人困惑
            Block b = raw.visible(view.values::test);
            if (b.isHollow()) {
                // 行全被藏掉的表 / 列表整块不显示（GuideReader.build 同一条规则），表头也就不能进索引
                continue;
            }
            append(sb, b.text);
            append(sb, b.title);
            if (b.type == GuideContent.BlockType.TIP && b.style == GuideContent.TipStyle.WF
                    && (b.title == null || b.title.isEmpty())) {
                // 小知识框不写标题时界面自动补的那个标题（TipLaid），正文里看得到，就得搜得到
                appendLiteral(sb, I18n.get("kuvalich.guide.tip_wf"));
            }
            append(sb, b.caption);
            for (String s : b.header) {
                append(sb, s);
            }
            for (List<String> row : b.rows) {
                for (String s : row) {
                    append(sb, s);
                }
            }
            switch (b.type) {
                case ITEMS -> {
                    // items 块里是物品 id，搜的是它们的名字；不存在的物品正文里直接跳过，这里也跳过（而不是拼出一个「?」）
                    for (String id : b.items) {
                        ItemStack s = GuideView.stack(id);
                        if (!s.isEmpty()) {
                            appendLiteral(sb, itemName(s));
                        }
                    }
                }
                case RECIPE -> {
                    // 配方块正文里写着产物名；查不到配方时整块不显示，也就不进索引
                    GuideMedia.RecipeData data = media.recipe(b.item, b.recipe);
                    if (data != null) {
                        appendLiteral(sb, itemName(data.result));
                    }
                }
                case ENTITY -> appendLiteral(sb, GuideMedia.entityName(b.entity));
                default -> {
                    for (String s : b.items) {
                        append(sb, s);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 物品名（和正文里 {@code getHoverName} 显示的是同一个字符串）；别的模组的物品取名抛异常时不收录
     */
    private static String itemName(ItemStack s) {
        try {
            return s.getHoverName().getString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void append(StringBuilder sb, String raw) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        appendLiteral(sb, view.plain(raw));
    }

    /** 不解析标记，原样收录（界面自己的字样、物品 / 实体名） */
    private static void appendLiteral(StringBuilder sb, @Nullable String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(text.replace('\n', ' '));
    }
}
