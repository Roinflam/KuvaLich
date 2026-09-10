package pers.roinflam.kuvalich.base.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模组基类（1.20.1版本，业务逻辑100%不变）
 * Module Base Class (1.20.1 version, business logic 100% unchanged)
 *
 * <p>⭐ 正确性修复：属性缓存此前直接用 {@code nbt.hashCode()}（32 位 int）当唯一键，
 * 两个 NBT 内容不同但哈希相同的模组会互相命中对方的缓存，
 * 静默返回错误的属性集合（战斗数值直接算错，且没有任何报错）。
 * 现在缓存条目额外保存一份建立时的 NBT 快照，命中时用 {@code equals} 校验，
 * 校验不通过视为未命中并重新计算覆盖，从而把哈希从"唯一键"降级为"索引"。</p>
 *
 * <p>⭐ 性能修复（本次）：缓存的键计算与内容校验此前基于
 * {@code itemStack.getTag()}（整把武器的完整 NBT，含 8 个模组的全部嵌套数据），
 * 一次 {@link #getAttributes} 最坏会做三次全树递归：
 * <ol>
 *   <li>{@code hashCode()} 递归整棵 NBT 树算索引；</li>
 *   <li>{@code equals()} 再递归整棵树做防碰撞校验；</li>
 *   <li>未命中时 {@code copy()} 深拷贝整棵树存快照。</li>
 * </ol>
 * 这三次遍历的成本远超"直接读一遍 attributeList"本身，缓存反而成了负优化。
 * 现在改为只基于模组自身的 {@code <modid>_modules} 子 tag，
 * 数据体积小一个数量级，防碰撞语义完全不变。</p>
 */
@Mod.EventBusSubscriber
public abstract class AbstractModule extends Item {

    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_DURATION_MS = 30000;
    private static final Map<Integer, CacheEntry> ATTRIBUTE_CACHE = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final Set<Map.Entry<String, Double>> attributes;
        final long timestamp;
        /**
         * ⭐ 建立缓存时的模组子 tag 快照（深拷贝）。
         * <p>用于命中时校验内容是否真的一致，防止 hashCode 碰撞导致返回其他物品的属性。
         * 必须是拷贝而非引用：若持有活引用，物品原地修改 NBT 后校验仍会通过，
         * 反而会掩盖真实的缓存失效。</p>
         */
        @Nullable
        final CompoundTag nbtSnapshot;

        CacheEntry(Set<Map.Entry<String, Double>> attributes, @Nullable CompoundTag nbtSnapshot) {
            this.attributes = attributes;
            this.timestamp = System.currentTimeMillis();
            this.nbtSnapshot = nbtSnapshot;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }

        /**
         * ⭐ 校验缓存条目是否真的属于给定的模组子 tag（防哈希碰撞）
         *
         * @param moduleNbt 当前物品的模组子 tag（可为 null）
         * @return 内容一致返回 true
         */
        boolean matches(@Nullable CompoundTag moduleNbt) {
            if (nbtSnapshot == null) {
                return moduleNbt == null;
            }
            return nbtSnapshot.equals(moduleNbt);
        }
    }

    /**
     * 1.20.1构造函数：只接收Properties，不需要name参数
     * 1.20.1 constructor: only accepts Properties, no name parameter needed
     */
    public AbstractModule(@Nonnull Properties properties) {
        super(properties.stacksTo(1)); // 设置最大堆叠数为1 / Set max stack size to 1
    }

    public abstract boolean isWarframe();

    // ========== 原有方法（API已更新）==========

    public static boolean isRandom(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        CompoundTag tag = itemStack.getTagElement(Reference.MOD_ID + "_modules");
        return tag != null && tag.getBoolean("Random");
    }

    public static void setRandom(ItemStack itemStack, boolean random) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }
        CompoundTag kuvalichModule = itemStack.getOrCreateTagElement(Reference.MOD_ID + "_modules");
        kuvalichModule.putBoolean("Random", random);
        invalidateCache(itemStack);
    }

    /**
     * 读取模组的属性集合（带缓存）
     *
     * <p>⭐ 缓存命中条件：「模组子 tag 哈希相同 且 未过期 且 子 tag 内容一致」。
     * 第三个条件用于消除哈希碰撞导致的错值。</p>
     *
     * <p>⭐ 性能：键与校验均只基于 {@code <modid>_modules} 子 tag，
     * 不再遍历整把武器的完整 NBT。</p>
     *
     * @param itemStack 模组物品栈
     * @return 属性键值对集合，无模组数据时返回空集合
     */
    public static Set<Map.Entry<String, Double>> getAttributes(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Collections.emptySet();
        }

        // ⭐ 只取模组自身的子 tag：既是缓存键的来源，也是属性数据的来源，一次读取复用
        CompoundTag moduleNbt = itemStack.getTagElement(Reference.MOD_ID + "_modules");
        if (moduleNbt == null) {
            return Collections.emptySet();
        }

        int cacheKey = moduleNbt.hashCode();

        CacheEntry cached = ATTRIBUTE_CACHE.get(cacheKey);
        // ⭐ matches 校验：哈希相同但内容不同时视为未命中
        if (cached != null && !cached.isExpired() && cached.matches(moduleNbt)) {
            return cached.attributes;
        }

        Map<String, Double> attributeMap = new LinkedHashMap<>();
        ListTag attributeList = moduleNbt.getList("attributeList", Tag.TAG_COMPOUND);

        for (int i = 0; i < attributeList.size(); i++) {
            CompoundTag attributeTag = attributeList.getCompound(i);
            attributeMap.put(
                    attributeTag.getString("attributeType"),
                    attributeTag.getDouble("attributeValue")
            );
        }

        Set<Map.Entry<String, Double>> result = attributeMap.entrySet();

        if (ATTRIBUTE_CACHE.size() >= MAX_CACHE_SIZE) {
            cleanExpiredCache();
            if (ATTRIBUTE_CACHE.size() >= MAX_CACHE_SIZE) {
                cleanOldestCache();
            }
        }

        // ⭐ 存快照而非引用：物品 NBT 原地变更后校验会失败，从而正确地重新计算
        ATTRIBUTE_CACHE.put(cacheKey, new CacheEntry(result, moduleNbt.copy()));
        return result;
    }

    public static void addAttributes(ItemStack itemStack, String attributeType, double attributeValue) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        CompoundTag kuvalichModule = itemStack.getOrCreateTagElement(Reference.MOD_ID + "_modules");
        ListTag attributeList = kuvalichModule.getList("attributeList", Tag.TAG_COMPOUND);

        CompoundTag attributeTag = new CompoundTag();
        attributeTag.putString("attributeType", attributeType);
        attributeTag.putDouble("attributeValue", attributeValue);
        attributeList.add(attributeTag);

        kuvalichModule.put("attributeList", attributeList);
        invalidateCache(itemStack);
    }

    public static void setType(ItemStack itemStack, String type) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }
        CompoundTag kuvalichModule = itemStack.getOrCreateTagElement(Reference.MOD_ID + "_modules");
        kuvalichModule.putString("type", type);
        invalidateCache(itemStack);
    }

    public static String getType(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "";
        }
        CompoundTag kuvalichModule = itemStack.getTagElement(Reference.MOD_ID + "_modules");
        return kuvalichModule != null ? kuvalichModule.getString("type") : "";
    }

    // ========== ✅ 冲突标签系统（API已更新）==========

    public static void setConflictTags(ItemStack itemStack, List<String> tags) {
        if (itemStack == null || itemStack.isEmpty() || tags == null || tags.isEmpty()) {
            return;
        }

        CompoundTag kuvalichModule = itemStack.getOrCreateTagElement(Reference.MOD_ID + "_modules");
        ListTag tagList = new ListTag();

        for (String tag : tags) {
            if (tag != null && !tag.isEmpty()) {
                tagList.add(StringTag.valueOf(tag));
            }
        }

        kuvalichModule.put("conflictTags", tagList);
        invalidateCache(itemStack);
    }

    public static void setConflictTags(ItemStack itemStack, String... tags) {
        setConflictTags(itemStack, Arrays.asList(tags));
    }

    public static List<String> getConflictTags(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Collections.emptyList();
        }

        CompoundTag kuvalichModule = itemStack.getTagElement(Reference.MOD_ID + "_modules");
        if (kuvalichModule == null || !kuvalichModule.contains("conflictTags")) {
            return Collections.emptyList();
        }

        ListTag tagList = kuvalichModule.getList("conflictTags", Tag.TAG_STRING);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < tagList.size(); i++) {
            result.add(tagList.getString(i));
        }

        return result;
    }

    public static boolean hasConflict(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null || stack1.isEmpty() || stack2 == null || stack2.isEmpty()) {
            return false;
        }

        // ========== 1. 检查type冲突（原有逻辑）==========
        String type1 = getType(stack1);
        String type2 = getType(stack2);
        if (!type1.isEmpty() && !type2.isEmpty() && type1.equals(type2)) {
            return true;
        }

        // ========== 2. 检查冲突标签（双向）==========
        List<String> tags1 = getConflictTags(stack1);
        List<String> tags2 = getConflictTags(stack2);

        for (String tag1 : tags1) {
            if (tags2.contains(tag1)) {
                return true;
            }
        }

        return false;
    }

    // ========== 缓存管理 ==========

    /**
     * 计算物品的缓存索引
     *
     * <p>⭐ 注意：返回值只作为哈希索引使用，不再被当作唯一键。
     * 真正的身份校验由 {@link CacheEntry#matches(CompoundTag)} 完成。</p>
     *
     * <p>⭐ 只基于模组自身的子 tag，不再遍历整把武器的完整 NBT。</p>
     *
     * @param itemStack 物品栈
     * @return 缓存索引
     */
    private static int getCacheKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return 0;
        }
        CompoundTag moduleNbt = itemStack.getTagElement(Reference.MOD_ID + "_modules");
        return moduleNbt != null ? moduleNbt.hashCode() : 0;
    }

    private static void invalidateCache(ItemStack itemStack) {
        ATTRIBUTE_CACHE.remove(getCacheKey(itemStack));
    }

    public static void cleanExpiredCache() {
        ATTRIBUTE_CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private static void cleanOldestCache() {
        if (ATTRIBUTE_CACHE.isEmpty()) return;

        List<Map.Entry<Integer, CacheEntry>> entries = new ArrayList<>(ATTRIBUTE_CACHE.entrySet());
        entries.sort(Comparator.comparingLong(e -> e.getValue().timestamp));

        int removeCount = entries.size() / 2;
        for (int i = 0; i < removeCount; i++) {
            ATTRIBUTE_CACHE.remove(entries.get(i).getKey());
        }
    }

    public static String getCacheStats() {
        long expired = ATTRIBUTE_CACHE.values().stream().filter(CacheEntry::isExpired).count();
        return String.format("缓存总数: %d, 过期: %d, 有效: %d, 容量: %d%%",
                ATTRIBUTE_CACHE.size(),
                expired,
                ATTRIBUTE_CACHE.size() - expired,
                ATTRIBUTE_CACHE.size() * 100 / MAX_CACHE_SIZE);
    }
}
