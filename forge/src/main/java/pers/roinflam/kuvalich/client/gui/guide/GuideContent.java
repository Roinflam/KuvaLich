package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * 冒险指南的内容模型：一本书 = 若干分类，分类 = 若干条目，条目 = 若干块
 *
 * <p>这里只存 JSON 里的<b>原文</b>（带标签、占位符、链接的字符串），不做任何解析。
 * 同一份内容要在不同的配置快照下渲染出不同的数值 —— 服务端热重载配置后书要跟着变，
 * 所以解析放在界面那一层按快照版本做，模型本身在资源重载之前一直不变。</p>
 *
 * <p>所有集合都是不可变的，加载器构造完就交出去，界面只读。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class GuideContent {

    private GuideContent() {}

    /** 块类型，对应内容格式规范 §1.2 */
    public enum BlockType {
        H, P, LIST, STEPS, KV, TABLE, TIP, FORMULA, ITEMS, RECIPE, ENTITY, DIVIDER;

        /**
         * 按 JSON 里的 type 字段找类型
         *
         * @param name type 字段
         * @return 类型；不认识时为 null
         */
        @Nullable
        public static BlockType of(@Nullable String name) {
            if (name == null) {
                return null;
            }
            return switch (name) {
                case "h" -> H;
                case "p" -> P;
                case "list" -> LIST;
                case "steps" -> STEPS;
                case "kv" -> KV;
                case "table" -> TABLE;
                case "tip" -> TIP;
                case "formula" -> FORMULA;
                case "items" -> ITEMS;
                case "recipe" -> RECIPE;
                case "entity" -> ENTITY;
                case "divider" -> DIVIDER;
                default -> null;
            };
        }
    }

    /** 提示框样式 */
    public enum TipStyle {
        INFO, WARN, GOOD, BAD,
        /** 星际战甲小知识：解释术语在原作里是什么，不影响玩法理解，没玩过原作的玩家可以跳过 */
        WF;

        @Nonnull
        public static TipStyle of(@Nullable String name) {
            if ("warn".equals(name)) {
                return WARN;
            }
            if ("good".equals(name)) {
                return GOOD;
            }
            if ("bad".equals(name)) {
                return BAD;
            }
            if ("wf".equals(name)) {
                return WF;
            }
            return INFO;
        }

        public int color() {
            return switch (this) {
                case INFO -> GuideTheme.TIP_INFO;
                case WARN -> GuideTheme.TIP_WARN;
                case GOOD -> GuideTheme.TIP_GOOD;
                case BAD -> GuideTheme.TIP_BAD;
                case WF -> GuideTheme.TIP_WF;
            };
        }
    }

    /**
     * 一本书：按 order 排好序的分类
     */
    public static final class Book {
        public final List<Category> categories;
        public final Map<String, Category> byId;
        /** 实际读的是哪个语言目录（回退之后），只用于日志 */
        public final String language;

        public Book(@Nonnull List<Category> categories, @Nonnull String language) {
            this.categories = List.copyOf(categories);
            Map<String, Category> map = new LinkedHashMap<>();
            for (Category c : this.categories) {
                map.put(c.id, c);
            }
            this.byId = Collections.unmodifiableMap(map);
            this.language = language;
        }

        public static Book empty(String language) {
            return new Book(List.of(), language);
        }
    }

    /**
     * 分类：首页的一个格子
     */
    public static final class Category {
        public final String id;
        public final int order;
        public final String title;
        public final String subtitle;
        @Nullable
        public final String icon;
        /** 全部成立才显示；空表示无条件 */
        public final List<String> cond;
        public final List<Entry> entries;

        public Category(String id, int order, String title, String subtitle, @Nullable String icon,
                        List<String> cond, List<Entry> entries) {
            this.id = id;
            this.order = order;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.cond = List.copyOf(cond);
            this.entries = List.copyOf(entries);
        }
    }

    /**
     * 条目：阅读页的一篇
     */
    public static final class Entry {
        public final String id;
        public final String title;
        @Nullable
        public final String icon;
        public final String summary;
        public final List<String> cond;
        public final List<Block> blocks;

        public Entry(String id, String title, @Nullable String icon, String summary,
                     List<String> cond, List<Block> blocks) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.summary = summary;
            this.cond = List.copyOf(cond);
            this.blocks = List.copyOf(blocks);
        }
    }

    /**
     * 块：一个类型加上它用得到的字段，其余字段为空
     *
     * <p>用一个类装全部块类型而不是一种块一个子类：块只是数据，真正按类型分派的是排版
     * （{@link GuideBlocks}），在那边 switch 一次比在这里维护十二个几乎空的子类清楚。</p>
     */
    public static final class Block {
        public final BlockType type;
        public final List<String> cond;
        /** h / p / formula / tip 的正文 */
        public final String text;
        /** tip 的标题（可空） */
        @Nullable
        public final String title;
        public final TipStyle style;
        /** list / steps 的文本项；items 块的物品 id */
        public final List<String> items;
        /** 与 items 等长：每一项自己的 if（空表 = 总是显示） */
        public final List<List<String>> itemConds;
        public final boolean ordered;
        /** kv / table 的行 */
        public final List<List<String>> rows;
        /** 与 rows 等长：每一行自己的 if（空表 = 总是显示） */
        public final List<List<String>> rowConds;
        /** table 表头 */
        public final List<String> header;
        /** table 列宽权重，可空 */
        @Nullable
        public final float[] widths;
        /** items 块的说明（可空） */
        @Nullable
        public final String caption;
        /** recipe：按产物查 */
        @Nullable
        public final String item;
        /** recipe：按配方 id 查 */
        @Nullable
        public final String recipe;
        /** entity：实体类型 id */
        @Nullable
        public final String entity;
        /** entity：预览高度 */
        public final int height;

        private Block(Builder b) {
            this.type = b.type;
            this.cond = List.copyOf(b.cond);
            this.text = b.text == null ? "" : b.text;
            this.title = b.title;
            this.style = b.style;
            this.items = List.copyOf(b.items);
            this.itemConds = pad(b.itemConds, this.items.size());
            this.ordered = b.ordered;
            this.rows = b.rows.stream().map(List::copyOf).toList();
            this.rowConds = pad(b.rowConds, this.rows.size());
            this.header = List.copyOf(b.header);
            this.widths = b.widths;
            this.caption = b.caption;
            this.item = b.item;
            this.recipe = b.recipe;
            this.entity = b.entity;
            this.height = b.height;
        }

        private static List<List<String>> pad(List<List<String>> conds, int size) {
            List<List<String>> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                out.add(i < conds.size() && conds.get(i) != null ? List.copyOf(conds.get(i)) : List.of());
            }
            return List.copyOf(out);
        }

        /**
         * 去掉条件不成立的列表项 / 表格行
         *
         * <p>让「服务端开了才有」的玩法能细到一行：例如盔甲槽模组关着时，槽位表里就没有头盔那一行。
         * 没有任何行级条件时直接返回自己，不复制。</p>
         *
         * @param test 条件求值（同块级 if 的规则）
         * @return 过滤后的块
         */
        public Block visible(Predicate<List<String>> test) {
            boolean any = false;
            for (List<String> c : itemConds) {
                any |= !c.isEmpty();
            }
            for (List<String> c : rowConds) {
                any |= !c.isEmpty();
            }
            if (!any) {
                return this;
            }
            Builder b = toBuilder();
            List<String> its = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                if (itemConds.get(i).isEmpty() || test.test(itemConds.get(i))) {
                    its.add(items.get(i));
                }
            }
            List<List<String>> rs = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                if (rowConds.get(i).isEmpty() || test.test(rowConds.get(i))) {
                    rs.add(rows.get(i));
                }
            }
            b.items = its;
            b.itemConds = List.of();
            b.rows = rs;
            b.rowConds = List.of();
            return b.build();
        }

        /**
         * 行级条件过滤后，列表 / 表格是否一行不剩（整块应当隐藏）
         *
         * @return true 表示该隐藏
         */
        public boolean isHollow() {
            return switch (type) {
                case LIST, STEPS, ITEMS -> items.isEmpty();
                case KV, TABLE -> rows.isEmpty();
                default -> false;
            };
        }

        private Builder toBuilder() {
            Builder b = new Builder(type);
            b.cond = cond;
            b.text = text;
            b.title = title;
            b.style = style;
            b.items = items;
            b.itemConds = itemConds;
            b.ordered = ordered;
            b.rows = rows;
            b.rowConds = rowConds;
            b.header = header;
            b.widths = widths;
            b.caption = caption;
            b.item = item;
            b.recipe = recipe;
            b.entity = entity;
            b.height = height;
            return b;
        }

        /** 块的构造器：加载器边解析边填 */
        public static final class Builder {
            public BlockType type;
            public List<String> cond = List.of();
            public String text;
            public String title;
            public TipStyle style = TipStyle.INFO;
            public List<String> items = List.of();
            public List<List<String>> itemConds = List.of();
            public boolean ordered;
            public List<List<String>> rows = List.of();
            public List<List<String>> rowConds = List.of();
            public List<String> header = List.of();
            public float[] widths;
            public String caption;
            public String item;
            public String recipe;
            public String entity;
            public int height = 64;

            public Builder(BlockType type) {
                this.type = type;
            }

            public Block build() {
                return new Block(this);
            }
        }
    }
}
