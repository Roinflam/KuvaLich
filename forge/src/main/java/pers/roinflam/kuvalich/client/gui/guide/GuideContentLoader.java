package pers.roinflam.kuvalich.client.gui.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Block;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.BlockType;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Book;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Category;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Entry;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.TipStyle;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从资源包读取指南内容：{@code assets/kuvalich/guide/<语言>/<分类id>.json}
 *
 * <h3>为什么走资源包而不是数据包</h3>
 * <p>内容是给客户端看的文字，本来就该跟着客户端的语言走；放在资源包里，整合包作者还能用自己的
 * 资源包覆盖某一篇（{@code listResources} 只返回最上层那份）。数值不写死在文字里，
 * 所以不需要服务端下发内容 —— 服务端只下发配置快照（{@link GuideClientConfig}）。</p>
 *
 * <h3>语言回退</h3>
 * <p>按分类逐个回退，而不是整本书回退：某个语言只翻了一半时，翻了的分类照常用译文，
 * 没翻的分类才回退。{@code zh_*} 先回退简体中文再回退英文，其余语言先英文后中文 ——
 * 繁体玩家读简体比读英文轻松。</p>
 *
 * <h3>宽容解析</h3>
 * <p>这本书是玩家进服后第一个打开的东西，任何一个写坏的块都不能让它打不开。
 * 所以坏掉的最小单位只跳过它自己：坏块跳过块、坏条目跳过条目、坏文件跳过分类，
 * 每种问题只报一次（{@link GuideLog}）。下划线开头的字段（{@code _src} 等）一律忽略，
 * 那是给核对阶段看的。</p>
 *
 * <p>结果按语言缓存。资源重载（F3+T、切换语言都会触发）时由 {@link GuideReloadListener}
 * 调 {@link #invalidate()} 丢掉；界面通过 {@link #generation()} 发现内容换了。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class GuideContentLoader {

    private static final String ROOT = "guide";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    @Nullable
    private static Book cached;
    @Nullable
    private static String cachedLanguage;
    private static int generation;

    private GuideContentLoader() {}

    /**
     * 当前语言的整本书（首次调用时加载）
     *
     * @return 书；一个分类都读不到时是空书，不会是 null
     */
    @Nonnull
    public static synchronized Book get() {
        String language = currentLanguage();
        if (cached == null || !language.equals(cachedLanguage)) {
            long start = System.nanoTime();
            try {
                cached = load(Minecraft.getInstance().getResourceManager(), language);
            } catch (RuntimeException e) {
                // 单个文件的问题在 load 里已经各自兜住了，能走到这里的是资源管理器本身出了意外。
                // 缓存一本空书而不是往上抛：get() 每帧都会被调，抛出去就是每帧一次异常、界面打不开
                GuideLog.warnOnce("load:" + language, "读取指南内容失败，本次显示为空: " + e);
                cached = Book.empty(language);
            }
            cachedLanguage = language;
            generation++;
            LogUtil.debug(String.format("[冒险指南] 读取 %d 个分类（语言 %s），耗时 %.1f ms",
                    cached.categories.size(), language, (System.nanoTime() - start) / 1e6));
        }
        return cached;
    }

    /**
     * 内容版本号：每次重新加载 +1。界面拿它和自己手上那份比，不一样就重建。
     *
     * @return 版本号
     */
    public static synchronized int generation() {
        return generation;
    }

    /** 丢掉缓存：资源重载时调用，下次 {@link #get()} 重新读 */
    public static synchronized void invalidate() {
        cached = null;
        cachedLanguage = null;
        generation++;
        GuideLog.reset();
    }

    /**
     * 当前客户端语言代码（小写，如 {@code zh_cn}）
     *
     * @return 语言代码
     */
    @Nonnull
    public static String currentLanguage() {
        String selected = Minecraft.getInstance().getLanguageManager().getSelected();
        return selected == null ? "en_us" : selected.toLowerCase(Locale.ROOT);
    }

    /**
     * 回退顺序
     *
     * @param language 当前语言
     * @return 依次尝试的语言目录
     */
    static List<String> fallbackChain(String language) {
        List<String> chain = new ArrayList<>(3);
        chain.add(language);
        if (language.startsWith("zh_")) {
            chain.add("zh_cn");
            chain.add("en_us");
        } else {
            chain.add("en_us");
            chain.add("zh_cn");
        }
        return chain.stream().distinct().toList();
    }

    // ==================== 加载 ====================

    private static Book load(ResourceManager manager, String language) {
        // 分类 id → (语言, 资源)。按回退顺序 putIfAbsent，靠前的语言优先
        Map<String, ResourceLocation> chosen = new LinkedHashMap<>();
        Map<String, String> chosenLang = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = new LinkedHashMap<>();
        for (String lang : fallbackChain(language)) {
            String dir = ROOT + "/" + lang;
            Map<ResourceLocation, Resource> found;
            try {
                found = manager.listResources(dir, rl -> rl.getPath().endsWith(".json"));
            } catch (RuntimeException e) {
                GuideLog.warnOnce("list:" + dir, "列出 " + dir + " 失败: " + e);
                continue;
            }
            for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
                ResourceLocation rl = e.getKey();
                if (!Reference.MOD_ID.equals(rl.getNamespace())) {
                    continue;
                }
                // 只认目录下的直接子文件：guide/zh_cn/start.json
                // （先确认前缀再截：别的资源包实现返回的路径不带这个前缀时，substring 会直接抛出）
                String path = rl.getPath();
                if (!path.startsWith(dir + "/")) {
                    continue;
                }
                String rest = path.substring(dir.length() + 1);
                if (rest.indexOf('/') >= 0) {
                    continue;
                }
                String id = rest.substring(0, rest.length() - ".json".length());
                if (!ID.matcher(id).matches()) {
                    GuideLog.warnOnce("file:" + rl, "文件名不是合法的分类 id（只能用小写字母、数字、下划线），跳过: " + rl);
                    continue;
                }
                if (!chosen.containsKey(id)) {
                    chosen.put(id, rl);
                    chosenLang.put(id, lang);
                    resources.put(rl, e.getValue());
                }
            }
        }

        List<Category> categories = new ArrayList<>();
        for (Map.Entry<String, ResourceLocation> e : chosen.entrySet()) {
            String id = e.getKey();
            String where = chosenLang.get(id) + "/" + id + ".json";
            try {
                JsonElement root = readJson(resources.get(e.getValue()));
                if (!root.isJsonObject()) {
                    GuideLog.warnOnce("root:" + where, where + "：根节点必须是对象，整个分类跳过");
                    continue;
                }
                Category category = parseCategory(id, root.getAsJsonObject(), where);
                if (category != null) {
                    categories.add(category);
                }
            } catch (Exception ex) {
                // 读不出来 / JSON 语法错误：只丢这一个分类
                GuideLog.warnOnce("parse:" + where, where + "：解析失败，整个分类跳过 —— " + ex.getMessage());
            }
        }
        categories.sort(Comparator.comparingInt((Category c) -> c.order).thenComparing(c -> c.id));
        return new Book(categories, language);
    }

    private static JsonElement readJson(Resource resource) throws Exception {
        String text;
        try (InputStream in = resource.open()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // 记事本另存的 UTF-8 带 BOM，Gson 会把它当成非法字符
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        JsonReader reader = new JsonReader(new StringReader(text));
        reader.setLenient(true);
        return JsonParser.parseReader(reader);
    }

    @Nullable
    private static Category parseCategory(String fileId, JsonObject o, String where) {
        String declared = str(o, "id");
        if (declared != null && !declared.equals(fileId)) {
            GuideLog.warnOnce("catid:" + where, where + "：id「" + declared + "」与文件名不一致，以文件名为准");
        }
        JsonElement entriesJson = o.get("entries");
        if (entriesJson == null || !entriesJson.isJsonArray()) {
            GuideLog.warnOnce("entries:" + where, where + "：缺少 entries 数组，整个分类跳过");
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (JsonElement el : entriesJson.getAsJsonArray()) {
            String entryWhere = where + " 第 " + (index++ + 1) + " 个条目";
            try {
                if (!el.isJsonObject()) {
                    GuideLog.warnOnce("entry:" + entryWhere, entryWhere + "：不是对象，跳过");
                    continue;
                }
                Entry entry = parseEntry(el.getAsJsonObject(), entryWhere);
                if (entry == null) {
                    continue;
                }
                if (!seen.add(entry.id)) {
                    GuideLog.warnOnce("dup:" + where + entry.id, where + "：条目 id「" + entry.id + "」重复，后一个跳过");
                    continue;
                }
                entries.add(entry);
            } catch (RuntimeException ex) {
                GuideLog.warnOnce("entry:" + entryWhere, entryWhere + "：解析失败，跳过 —— " + ex);
            }
        }
        int order = 1000;
        JsonElement orderJson = o.get("order");
        if (orderJson != null && orderJson.isJsonPrimitive() && orderJson.getAsJsonPrimitive().isNumber()) {
            order = orderJson.getAsInt();
        }
        String title = str(o, "title");
        String subtitle = str(o, "subtitle");
        return new Category(fileId, order, title != null ? title : fileId, subtitle != null ? subtitle : "",
                str(o, "icon"), cond(o.get("if"), where), entries);
    }

    @Nullable
    private static Entry parseEntry(JsonObject o, String where) {
        String id = str(o, "id");
        if (id == null || !ID.matcher(id).matches()) {
            GuideLog.warnOnce("eid:" + where, where + "：id 缺失或不合法（只能用小写字母、数字、下划线），跳过");
            return null;
        }
        where = where + "「" + id + "」";
        List<Block> blocks = new ArrayList<>();
        JsonElement blocksJson = o.get("blocks");
        if (blocksJson != null && blocksJson.isJsonArray()) {
            int index = 0;
            for (JsonElement el : blocksJson.getAsJsonArray()) {
                String blockWhere = where + " 第 " + (index++ + 1) + " 块";
                try {
                    Block block = el.isJsonObject() ? parseBlock(el.getAsJsonObject(), blockWhere) : null;
                    if (block != null) {
                        blocks.add(block);
                    } else if (!el.isJsonObject()) {
                        GuideLog.warnOnce("block:" + blockWhere, blockWhere + "：不是对象，跳过");
                    }
                } catch (RuntimeException ex) {
                    GuideLog.warnOnce("block:" + blockWhere, blockWhere + "：解析失败，跳过 —— " + ex);
                }
            }
        } else {
            GuideLog.warnOnce("blocks:" + where, where + "：缺少 blocks 数组");
        }
        String title = str(o, "title");
        String summary = str(o, "summary");
        return new Entry(id, title != null ? title : id, str(o, "icon"), summary != null ? summary : "",
                cond(o.get("if"), where), blocks);
    }

    @Nullable
    private static Block parseBlock(JsonObject o, String where) {
        String typeName = str(o, "type");
        BlockType type = BlockType.of(typeName);
        if (type == null) {
            GuideLog.warnOnce("type:" + where, where + "：未知块类型「" + typeName + "」，跳过");
            return null;
        }
        Block.Builder b = new Block.Builder(type);
        b.cond = cond(o.get("if"), where);
        switch (type) {
            case H, P, FORMULA -> {
                b.text = str(o, "text");
                if (b.text == null) {
                    return missing(where, "text");
                }
            }
            case TIP -> {
                b.text = str(o, "text");
                if (b.text == null) {
                    return missing(where, "text");
                }
                b.title = str(o, "title");
                b.style = TipStyle.of(str(o, "style"));
            }
            case LIST, STEPS -> {
                condItems(o.get("items"), b, where);
                if (b.items.isEmpty()) {
                    return missing(where, "items");
                }
                JsonElement ordered = o.get("ordered");
                b.ordered = ordered != null && ordered.isJsonPrimitive() && ordered.getAsBoolean();
            }
            case KV -> {
                b.rows = rows(o.get("rows"), 2, where, b);
                if (b.rows.isEmpty()) {
                    return missing(where, "rows");
                }
            }
            case TABLE -> {
                b.header = strList(o.get("header"));
                if (b.header.isEmpty()) {
                    return missing(where, "header");
                }
                b.rows = rows(o.get("rows"), b.header.size(), where, b);
                b.widths = widths(o.get("widths"), b.header.size(), where);
            }
            case ITEMS -> {
                b.items = strList(o.get("items"));
                if (b.items.isEmpty()) {
                    return missing(where, "items");
                }
                b.caption = str(o, "caption");
            }
            case RECIPE -> {
                b.item = str(o, "item");
                b.recipe = str(o, "recipe");
                if (b.item == null && b.recipe == null) {
                    return missing(where, "item / recipe");
                }
            }
            case ENTITY -> {
                b.entity = str(o, "id");
                if (b.entity == null) {
                    return missing(where, "id");
                }
                JsonElement h = o.get("height");
                if (h != null && h.isJsonPrimitive() && h.getAsJsonPrimitive().isNumber()) {
                    // 太矮看不清，太高一屏放不下
                    b.height = Math.max(24, Math.min(200, h.getAsInt()));
                }
            }
            case DIVIDER -> {
            }
        }
        return b.build();
    }

    @Nullable
    private static Block missing(String where, String field) {
        GuideLog.warnOnce("field:" + where, where + "：缺少字段 " + field + "，跳过");
        return null;
    }

    // ==================== 字段读取 ====================

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        return el.isJsonPrimitive() ? el.getAsString() : null;
    }

    private static List<String> strList(@Nullable JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement x : el.getAsJsonArray()) {
            out.add(x != null && x.isJsonPrimitive() ? x.getAsString() : "");
        }
        return out;
    }

    /**
     * 行数组；列数不对的行补空或截断（报一次）。表格少一格总比整张表不显示强。
     */
    private static List<List<String>> rows(@Nullable JsonElement el, int columns, String where, Block.Builder b) {
        if (el == null || !el.isJsonArray()) {
            return List.of();
        }
        List<List<String>> out = new ArrayList<>();
        List<List<String>> conds = new ArrayList<>();
        for (JsonElement r : el.getAsJsonArray()) {
            // 行可以是 ["a", "b"]，也可以是 {"if": ..., "cells": ["a", "b"]}（只在条件成立时显示这一行）
            List<String> rowCond = List.of();
            if (r != null && r.isJsonObject()) {
                rowCond = cond(r.getAsJsonObject().get("if"), where);
                r = r.getAsJsonObject().get("cells");
            }
            conds.add(rowCond);
            List<String> row = new ArrayList<>(strList(r));
            if (row.size() != columns) {
                GuideLog.warnOnce("cols:" + where, where + "：有行的列数（" + row.size() + "）不等于 " + columns + "，已补齐 / 截断");
                while (row.size() < columns) {
                    row.add("");
                }
                while (row.size() > columns) {
                    row.remove(row.size() - 1);
                }
            }
            out.add(row);
        }
        b.rowConds = conds;
        return out;
    }

    /**
     * list / steps 的项：字符串，或 {"if": ..., "text": "..."}（只在条件成立时显示这一项）
     */
    private static void condItems(@Nullable JsonElement el, Block.Builder b, String where) {
        List<String> items = new ArrayList<>();
        List<List<String>> conds = new ArrayList<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                if (x != null && x.isJsonObject()) {
                    String t = str(x.getAsJsonObject(), "text");
                    items.add(t == null ? "" : t);
                    conds.add(cond(x.getAsJsonObject().get("if"), where));
                } else {
                    items.add(x != null && x.isJsonPrimitive() ? x.getAsString() : "");
                    conds.add(List.of());
                }
            }
        }
        b.items = items;
        b.itemConds = conds;
    }

    @Nullable
    private static float[] widths(@Nullable JsonElement el, int columns, String where) {
        if (el == null || !el.isJsonArray()) {
            return null;
        }
        JsonArray array = el.getAsJsonArray();
        if (array.size() != columns) {
            GuideLog.warnOnce("widths:" + where, where + "：widths 长度不等于列数，改为按内容自适应");
            return null;
        }
        float[] out = new float[columns];
        for (int i = 0; i < columns; i++) {
            JsonElement x = array.get(i);
            float v = x != null && x.isJsonPrimitive() && x.getAsJsonPrimitive().isNumber() ? x.getAsFloat() : 1f;
            out[i] = v > 0f ? v : 1f;
        }
        return out;
    }

    /**
     * if 字段：字符串或字符串数组。格式不对时换成一个必然不成立的条件 ——
     * 条件是用来藏掉「本服不存在的机制」的，写坏了宁可藏起来也别让玩家读到不存在的东西。
     */
    private static List<String> cond(@Nullable JsonElement el, String where) {
        if (el == null || el.isJsonNull()) {
            return List.of();
        }
        if (el.isJsonPrimitive()) {
            return List.of(el.getAsString());
        }
        if (el.isJsonArray()) {
            List<String> out = new ArrayList<>();
            for (JsonElement x : el.getAsJsonArray()) {
                out.add(x != null && x.isJsonPrimitive() ? x.getAsString() : "?invalid");
            }
            return out;
        }
        GuideLog.warnOnce("if:" + where, where + "：if 必须是字符串或字符串数组，按不成立处理");
        return List.of("?invalid");
    }
}
