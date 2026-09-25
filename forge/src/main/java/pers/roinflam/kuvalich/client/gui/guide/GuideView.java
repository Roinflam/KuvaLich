package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Book;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Category;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Entry;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.LinkTarget;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Rich;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「这本书在当前服务器上长什么样」：内容 × 配置快照 × 语言
 *
 * <p>内容模型（{@link GuideContent}）是原文，本类在它上面套一层条件求值：哪些分类 / 条目在本服存在、
 * 标题和摘要解析成什么纯文本、链接指向哪里。界面只和这一层打交道。</p>
 *
 * <p>三样东西任何一样变了，整个视图作废重建（{@link #isStale()}）：资源重载换了内容、
 * 服务端重发了配置（{@link GuideClientConfig#revision()}）、玩家切了语言。重建很便宜 ——
 * 几十个条目的条件求值加标题解析，远不到一毫秒；正文要等真正翻到那一篇才排版。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideView implements GuideMarkup.Resolver {

    final Book book;
    final GuideValues values;
    final int revision;
    final int generation;
    final String language;

    /** 本服可见、且至少有一个可见条目的分类，按 order 排好 */
    final List<CatView> categories;
    final Map<String, CatView> categoriesById;
    /** 可见条目，键 = 分类id/条目id */
    final Map<String, EntryView> entries;
    /** 可见条目按书中顺序 */
    final List<EntryView> entryList;

    private final Map<String, ItemStack> icons = new HashMap<>();

    private GuideView(Book book, GuideValues values, int revision, int generation, String language) {
        this.book = book;
        this.values = values;
        this.revision = revision;
        this.generation = generation;
        this.language = language;

        List<CatView> cats = new ArrayList<>();
        Map<String, CatView> catMap = new LinkedHashMap<>();
        Map<String, EntryView> entryMap = new LinkedHashMap<>();
        List<EntryView> all = new ArrayList<>();
        for (Category c : book.categories) {
            if (!values.test(c.cond)) {
                continue;
            }
            CatView cv = new CatView(this, c, cats.size());
            for (Entry e : c.entries) {
                if (!values.test(e.cond)) {
                    continue;
                }
                EntryView ev = new EntryView(this, cv, e, cv.entries.size(), all.size());
                cv.entries.add(ev);
                all.add(ev);
                entryMap.put(ev.key, ev);
            }
            if (cv.entries.isEmpty()) {
                // 条目全被条件藏掉的分类不显示空格子（点进去什么都没有）
                continue;
            }
            cats.add(cv);
            catMap.put(c.id, cv);
        }
        this.categories = Collections.unmodifiableList(cats);
        this.categoriesById = Collections.unmodifiableMap(catMap);
        this.entries = Collections.unmodifiableMap(entryMap);
        this.entryList = Collections.unmodifiableList(all);
    }

    /**
     * 按当前内容、配置快照、语言建一个视图
     *
     * @return 视图
     */
    static GuideView build() {
        Book book = GuideContentLoader.get();
        int generation = GuideContentLoader.generation();
        int revision = GuideClientConfig.revision();
        GuideValues values = new GuideValues(GuideClientConfig.snapshot(), GuideClientConfig.isSynced());
        return new GuideView(book, values, revision, generation, GuideContentLoader.currentLanguage());
    }

    /**
     * 内容、配置、语言三者之一变了
     *
     * @return 是否需要重建
     */
    boolean isStale() {
        return revision != GuideClientConfig.revision()
                || generation != GuideContentLoader.generation()
                || !language.equals(GuideContentLoader.currentLanguage());
    }

    // ==================== 解析 ====================

    /**
     * 解析一段原文
     *
     * @param raw   原文
     * @param color 默认色
     * @param bold  默认加粗
     * @return 片段序列
     */
    Rich rich(@Nullable String raw, int color, boolean bold) {
        return GuideMarkup.parse(raw, this, color, bold);
    }

    /**
     * 原文 → 纯文本（标题、摘要、搜索）
     */
    String plain(@Nullable String raw) {
        return rich(raw, GuideTheme.BODY, false).plain();
    }

    @Override
    public GuideValues.Resolved placeholder(String token) {
        return values.placeholder(token);
    }

    /**
     * 正在解析的链接标题层数。链接不写显示文字时要取目标的标题，而标题本身也是带标记的原文 ——
     * 标题里再链回自己（或 A 的标题链 B、B 的标题链 A）会无限递归直到栈溢出，
     * 而 StackOverflowError 不是 RuntimeException，哪一层的兜底都接不住，客户端直接崩。
     * 超过一层就不再取标题，退回原始 id。
     */
    private int linkDepth;

    /**
     * 是否正在替某条链接取目标标题。这时算出的标题里，再往下一层的链接退回了原始 id，
     * 不是这个标题「真正的样子」—— 不能写进缓存，也不能读缓存（缓存里那份又嵌着再下一层）。
     * 否则同一个标题显示成什么，取决于左栏 / 面包屑 / 翻页卡谁先把它算出来。
     */
    boolean isResolvingLink() {
        return linkDepth > 0;
    }

    @Override
    public LinkTarget link(String target) {
        if (linkDepth > 0) {
            EntryView ev = target.indexOf('/') < 0 ? null : entries.get(target);
            CatView cv = ev == null && target.indexOf('/') < 0 ? categoriesById.get(target) : null;
            String key = ev != null ? ev.key : cv != null ? cv.entries.get(0).key : null;
            return new LinkTarget(key, target);
        }
        linkDepth++;
        try {
            return resolveLink(target);
        } finally {
            linkDepth--;
        }
    }

    private LinkTarget resolveLink(String target) {
        int slash = target.indexOf('/');
        String catId = slash < 0 ? target : target.substring(0, slash);
        if (slash < 0) {
            CatView cv = categoriesById.get(catId);
            if (cv != null) {
                return new LinkTarget(cv.entries.get(0).key, cv.title());
            }
            Category c = book.byId.get(catId);
            if (c == null) {
                GuideLog.warnOnce("link:" + target, "链接目标分类不存在: [[" + target + "]]");
            }
            return new LinkTarget(null, c != null ? plain(c.title) : target);
        }
        EntryView ev = entries.get(target);
        if (ev != null) {
            return new LinkTarget(ev.key, ev.title());
        }
        // 目标存在但被条件藏掉了：返回它的标题、不给键。GuideMarkup 对这种死链不生成 Link，
        // 按外层正文原样输出（和正文同色、不可点）—— 读者看不出这里原本是个链接，又知道原文提到了什么
        Category c = book.byId.get(catId);
        if (c != null) {
            String entryId = target.substring(slash + 1);
            for (Entry e : c.entries) {
                if (e.id.equals(entryId)) {
                    return new LinkTarget(null, plain(e.title));
                }
            }
        }
        GuideLog.warnOnce("link:" + target, "链接目标条目不存在: [[" + target + "]]");
        return new LinkTarget(null, target);
    }

    // ==================== 图标 ====================

    /**
     * 物品 id → 图标；不存在时用 fallback
     */
    ItemStack icon(@Nullable String id, @Nonnull ItemStack fallback) {
        if (id == null || id.isEmpty()) {
            return fallback;
        }
        ItemStack cached = icons.get(id);
        if (cached == null) {
            cached = stack(id);
            icons.put(id, cached);
        }
        return cached.isEmpty() ? fallback : cached;
    }

    /**
     * 物品 id → ItemStack；不存在时为空栈（并报一次）
     */
    static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) {
            GuideLog.warnOnce("icon:" + id, "物品不存在: " + id);
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private ItemStack bookIcon;

    /** 书本身的图标：顶栏徽记、所有图标的最终兜底（顶栏每帧都画，所以缓存） */
    ItemStack bookIcon() {
        if (bookIcon == null) {
            bookIcon = icon("kuvalich:kuvalich_guide", new ItemStack(Items.BOOK));
        }
        return bookIcon;
    }

    // ==================== 视图节点 ====================

    /**
     * 一个可见分类
     */
    static final class CatView {
        final GuideView view;
        final Category category;
        final int index;
        final List<EntryView> entries = new ArrayList<>();
        private String title;
        private String subtitle;
        private ItemStack icon;

        CatView(GuideView view, Category category, int index) {
            this.view = view;
            this.category = category;
            this.index = index;
        }

        String id() {
            return category.id;
        }

        String title() {
            if (view.isResolvingLink()) {
                // 见 isResolvingLink：链接取标题时现算、不碰缓存
                return view.plain(category.title);
            }
            if (title == null) {
                title = view.plain(category.title);
            }
            return title;
        }

        String subtitle() {
            if (subtitle == null) {
                subtitle = view.plain(category.subtitle);
            }
            return subtitle;
        }

        ItemStack icon() {
            if (icon == null) {
                icon = view.icon(category.icon, view.bookIcon());
            }
            return icon;
        }
    }

    /**
     * 一个可见条目
     */
    static final class EntryView {
        final GuideView view;
        final CatView cat;
        final Entry entry;
        /** 分类id/条目id */
        final String key;
        /** 在分类里的序号 */
        final int index;
        /** 在全书里的序号 */
        final int globalIndex;
        private String title;
        private String summary;
        private ItemStack icon;

        EntryView(GuideView view, CatView cat, Entry entry, int index, int globalIndex) {
            this.view = view;
            this.cat = cat;
            this.entry = entry;
            this.key = cat.category.id + "/" + entry.id;
            this.index = index;
            this.globalIndex = globalIndex;
        }

        String title() {
            if (view.isResolvingLink()) {
                // 见 isResolvingLink：链接取标题时现算、不碰缓存（标题一般很短，现算的开销可以忽略）
                return view.plain(entry.title);
            }
            if (title == null) {
                title = view.plain(entry.title);
            }
            return title;
        }

        String summary() {
            if (summary == null) {
                summary = view.plain(entry.summary);
            }
            return summary;
        }

        ItemStack icon() {
            if (icon == null) {
                icon = view.icon(entry.icon, cat.icon());
            }
            return icon;
        }
    }
}
