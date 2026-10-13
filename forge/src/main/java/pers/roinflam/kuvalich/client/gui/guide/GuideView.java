package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Block;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.BlockType;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Book;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Category;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Entry;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.LinkTarget;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Rich;
import pers.roinflam.kuvalich.utils.Reference;

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
 * <h3>全局只有一份（{@link #current()}）</h3>
 * <p>指南界面和物品提示上的「按住 Ctrl 查阅」（{@link GuideLookup}）共用同一个实例 —— 指南关着的时候也要能查
 * 「这个物品在哪一篇」。上面三样任何一样变了，下次来取时整个重建。</p>
 *
 * <h3>物品 → (条目, 块) 索引（{@link #find}）</h3>
 * <p>查阅用。按下面的优先级取，同一优先级按全书顺序取先出现的：</p>
 * <ol>
 *   <li>条目的 {@code items} 字段里手标的（这一篇是它的专篇；任何命名空间都行）；</li>
 *   <li>配方块的产物（{@code recipe} 块的 {@code item}）；</li>
 *   <li>物品块（{@code items} 块）里列出的；</li>
 *   <li>条目图标；</li>
 *   <li>正文里用 {@code {item:…}} 提到的（标题、摘要、各块的文字、列表项、表格格子、提示框标题都算）。</li>
 * </ol>
 * <p>第 1 条以外都是猜，只给手标漏掉的物品兜底，而且只收本模组的物品：原版物品在书里多半是举例或配图
 * （「×3 黑曜石」、拿钻石剑当话题图标），挂上查阅行反倒误导，要做查阅就写进 {@code items}。
 * 图标排在配方、物品块后面：入门教程的章节、词条速查常拿本模组物品当配图，按「图标 = 专篇」猜会把一大批物品引到教程章去。</p>
 * <p>落到哪一块（{@link #firstBlockShowing}）：篇里第一个写着它名字的小标题（一篇讲好几样东西时，每样一节）→
 * 这一篇的图标就是它（整篇都在讲它）→ 篇首；否则第一块展示它的配方 / 物品块 / 提到它的块，都没有就篇首。</p>
 * <p><b>只收可见的内容</b>：被 {@code if} 藏掉的分类 / 条目 / 块，以及被行级 {@code if} 藏掉的列表项 / 表格行里的物品都不算 ——
 * 物品只出现在被藏掉的内容里时，提示框上不显示查阅那一行；手标的专篇被藏掉时，退回按上面的规则在可见内容里找。
 * 块序号是 {@link EntryView#blocks()} 的下标，和阅读页排版用的是同一份，所以对得上。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideView implements GuideMarkup.Resolver {

    @Nullable
    private static GuideView current;

    /** 正文里的物品占位符 */
    private static final java.util.regex.Pattern ITEM_REF = java.util.regex.Pattern.compile("\\{item:\\s*([^}\\s]+)\\s*}");

    /**
     * 查阅的目标
     *
     * @param entryKey 条目键（分类/条目）
     * @param block    块序号（{@link EntryView#blocks()} 里的下标）；-1 表示篇首
     */
    record Target(String entryKey, int block) {}

    @Nullable
    private Map<Item, Target> index;

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
     * 当前的视图（全局共用一份；内容、配置、语言任何一样变了就重建）
     *
     * @return 视图
     */
    @Nonnull
    static GuideView current() {
        GuideView view = current;
        if (view == null || view.isStale()) {
            view = build();
            current = view;
        }
        return view;
    }

    /**
     * 按当前内容、配置快照、语言建一个视图
     *
     * @return 视图
     */
    private static GuideView build() {
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

    // ==================== 查阅索引 ====================

    /**
     * 书里讲这个物品的条目和块（优先级见类注释）
     *
     * @param item 物品
     * @return 目标；书里没有（或只在被藏掉的内容里）时为 null
     */
    @Nullable
    Target find(Item item) {
        if (index == null) {
            index = buildIndex();
        }
        return index.get(item);
    }

    private Map<Item, Target> buildIndex() {
        Map<Item, Target> map = new HashMap<>();
        // 1. 条目手标的专篇（任何命名空间）
        for (EntryView ev : entryList) {
            for (String raw : ev.entry.items) {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                Item item = id == null ? null : item(id);
                if (item != null && !map.containsKey(item)) {
                    map.put(item, new Target(ev.key, firstBlockShowing(ev, item)));
                }
            }
        }
        // 2 ~ 5 是兜底的猜测，只收本模组的物品
        // 2. 配方块的产物
        for (EntryView ev : entryList) {
            List<Block> blocks = ev.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                Block b = blocks.get(i);
                if (b.type == BlockType.RECIPE && b.item != null) {
                    guess(map, b.item, ev, i);
                }
            }
        }
        // 3. 物品块
        for (EntryView ev : entryList) {
            List<Block> blocks = ev.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                Block b = blocks.get(i);
                if (b.type == BlockType.ITEMS) {
                    for (String raw : b.items) {
                        guess(map, raw, ev, i);
                    }
                }
            }
        }
        // 4. 条目图标
        for (EntryView ev : entryList) {
            Item item = ev.entry.icon == null ? null : ownItem(ev.entry.icon);
            if (item != null && !map.containsKey(item)) {
                map.put(item, new Target(ev.key, firstBlockShowing(ev, item)));
            }
        }
        // 5. 正文里提到的：标题 / 摘要算篇首，块里的算那一块
        for (EntryView ev : entryList) {
            for (String raw : mentions(ev.entry.title, ev.entry.summary)) {
                guess(map, raw, ev, -1);
            }
            List<Block> blocks = ev.blocks();
            for (int i = 0; i < blocks.size(); i++) {
                for (String raw : mentions(blocks.get(i))) {
                    guess(map, raw, ev, i);
                }
            }
        }
        return map;
    }

    /** 兜底猜测：本模组的物品、还没有落点时才收 */
    private static void guess(Map<Item, Target> map, String raw, EntryView ev, int block) {
        Item item = ownItem(raw);
        if (item != null) {
            map.putIfAbsent(item, new Target(ev.key, block));
        }
    }

    /** 本模组命名空间下注册过的物品；别的命名空间、不存在的都是 null */
    @Nullable
    private static Item ownItem(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        return id == null || !Reference.MOD_ID.equals(id.getNamespace()) ? null : item(id);
    }

    /**
     * 这个物品在这一篇里落到哪一块（规则见类注释）：写着它名字的小标题 → 图标就是它则篇首 →
     * 配方产物 → 物品块 → 正文提到 → 篇首（-1）
     */
    private static int firstBlockShowing(EntryView ev, Item item) {
        List<Block> blocks = ev.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.type == BlockType.H) {
                for (String raw : mentions(b)) {
                    if (is(raw, item)) {
                        return i;
                    }
                }
            }
        }
        if (ev.entry.icon != null && is(ev.entry.icon, item)) {
            return -1;
        }
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.type == BlockType.RECIPE && b.item != null && is(b.item, item)) {
                return i;
            }
        }
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.type == BlockType.ITEMS) {
                for (String raw : b.items) {
                    if (is(raw, item)) {
                        return i;
                    }
                }
            }
        }
        for (int i = 0; i < blocks.size(); i++) {
            for (String raw : mentions(blocks.get(i))) {
                if (is(raw, item)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean is(String raw, Item item) {
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        return id != null && item(id) == item;
    }

    /** 一块里所有带文字的字段中 {@code {item:…}} 提到的物品 id（物品块的 items 是 id 本身，不在这里） */
    private static List<String> mentions(Block b) {
        List<String> out = new ArrayList<>(2);
        collect(b.text, out);
        collect(b.title, out);
        collect(b.caption, out);
        if (b.type != BlockType.ITEMS) {
            for (String s : b.items) {
                collect(s, out);
            }
        }
        for (String s : b.header) {
            collect(s, out);
        }
        for (List<String> row : b.rows) {
            for (String s : row) {
                collect(s, out);
            }
        }
        return out;
    }

    private static List<String> mentions(@Nullable String a, @Nullable String b) {
        List<String> out = new ArrayList<>(1);
        collect(a, out);
        collect(b, out);
        return out;
    }

    private static void collect(@Nullable String raw, List<String> out) {
        if (raw == null || raw.indexOf("{item:") < 0) {
            return;
        }
        java.util.regex.Matcher m = ITEM_REF.matcher(raw);
        while (m.find()) {
            out.add(m.group(1));
        }
    }

    @Nullable
    private static Item item(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        return item == null || item == Items.AIR ? null : item;
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
        @Nullable
        private List<Block> blocks;

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

        /**
         * 当前配置下显示的块：块级 {@code if} 成立、去掉行级 {@code if} 不成立的列表项 / 表格行之后不是空的。
         * 阅读页排版、搜索、查阅索引都用这一份，块序号因此处处对得上
         */
        List<Block> blocks() {
            if (blocks == null) {
                List<Block> out = new ArrayList<>(entry.blocks.size());
                for (Block b : entry.blocks) {
                    if (view.values.test(b.cond)) {
                        Block vb = b.visible(view.values::test);
                        if (!vb.isHollow()) {
                            out.add(vb);
                        }
                    }
                }
                blocks = Collections.unmodifiableList(out);
            }
            return blocks;
        }
    }
}
