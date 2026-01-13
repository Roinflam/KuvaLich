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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模组基类（1.20.1版本，业务逻辑100%不变）
 * Module Base Class (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber
public abstract class ModuleBase extends Item {

    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_DURATION_MS = 30000;
    private static final Map<Integer, CacheEntry> ATTRIBUTE_CACHE = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final Set<Map.Entry<String, Double>> attributes;
        final long timestamp;

        CacheEntry(Set<Map.Entry<String, Double>> attributes) {
            this.attributes = attributes;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }

    /**
     * 1.20.1构造函数：只接收Properties，不需要name参数
     * 1.20.1 constructor: only accepts Properties, no name parameter needed
     */
    public ModuleBase(@Nonnull Item.Properties properties) {
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

    public static Set<Map.Entry<String, Double>> getAttributes(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Collections.emptySet();
        }

        int cacheKey = getCacheKey(itemStack);
        CacheEntry cached = ATTRIBUTE_CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.attributes;
        }

        CompoundTag kuvalich = itemStack.getTagElement(Reference.MOD_ID + "_modules");
        if (kuvalich == null) {
            return Collections.emptySet();
        }

        Map<String, Double> attributeMap = new LinkedHashMap<>();
        ListTag attributeList = kuvalich.getList("attributeList", Tag.TAG_COMPOUND);

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

        ATTRIBUTE_CACHE.put(cacheKey, new CacheEntry(result));
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

    private static int getCacheKey(ItemStack itemStack) {
        CompoundTag nbt = itemStack.getTag();
        return nbt != null ? nbt.hashCode() : 0;
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