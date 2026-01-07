package pers.roinflam.kuvalich.base.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.tabs.KuvaLichTab;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.util.ItemUtil;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber
public abstract class ModuleBase extends Item implements IHasModel {
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

    public ModuleBase(@Nonnull String name) {
        ItemUtil.registerItem(this, name, KuvaLichTab.getTab());
        setMaxStackSize(1);
        KuvaLichItems.ITEMS.add(this);
    }

    public abstract boolean isWarframe();

    // ========== 原有方法 ==========

    public static boolean isRandom(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return false;
        }
        NBTTagCompound tag = itemStack.getSubCompound(Reference.MOD_ID + "_modules");
        return tag != null && tag.getBoolean("Random");
    }

    public static void setRandom(ItemStack itemStack, boolean random) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }
        NBTTagCompound kuvalichModule = itemStack.getOrCreateSubCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.setBoolean("Random", random);
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

        NBTTagCompound kuvalich = itemStack.getSubCompound(Reference.MOD_ID + "_modules");
        if (kuvalich == null) {
            return Collections.emptySet();
        }

        Map<String, Double> attributeMap = new LinkedHashMap<>();
        NBTTagList attributeList = kuvalich.getTagList("attributeList", Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < attributeList.tagCount(); i++) {
            NBTTagCompound attributeTag = attributeList.getCompoundTagAt(i);
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

        NBTTagCompound kuvalichModule = itemStack.getOrCreateSubCompound(Reference.MOD_ID + "_modules");
        NBTTagList attributeList = kuvalichModule.getTagList("attributeList", Constants.NBT.TAG_COMPOUND);

        NBTTagCompound attributeTag = new NBTTagCompound();
        attributeTag.setString("attributeType", attributeType);
        attributeTag.setDouble("attributeValue", attributeValue);
        attributeList.appendTag(attributeTag);

        kuvalichModule.setTag("attributeList", attributeList);
        invalidateCache(itemStack);
    }

    public static void setType(ItemStack itemStack, String type) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }
        NBTTagCompound kuvalichModule = itemStack.getOrCreateSubCompound(Reference.MOD_ID + "_modules");
        kuvalichModule.setString("type", type);
        invalidateCache(itemStack);
    }

    public static String getType(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "";
        }
        NBTTagCompound kuvalichModule = itemStack.getSubCompound(Reference.MOD_ID + "_modules");
        return kuvalichModule != null ? kuvalichModule.getString("type") : "";
    }

    // ========== ✅ 冲突标签系统 ==========

    /**
     * 设置MOD的冲突标签（可设置多个，双向互斥）
     * @param itemStack 物品堆
     * @param tags 冲突标签List，例如 Arrays.asList("melee_crit_chance", "melee_damage")
     */
    public static void setConflictTags(ItemStack itemStack, List<String> tags) {
        if (itemStack == null || itemStack.isEmpty() || tags == null || tags.isEmpty()) {
            return;
        }

        NBTTagCompound kuvalichModule = itemStack.getOrCreateSubCompound(Reference.MOD_ID + "_modules");
        NBTTagList tagList = new NBTTagList();

        for (String tag : tags) {
            if (tag != null && !tag.isEmpty()) {
                tagList.appendTag(new NBTTagString(tag));
            }
        }

        kuvalichModule.setTag("conflictTags", tagList);
        invalidateCache(itemStack);
    }

    /**
     * 便捷方法：直接传入可变参数
     */
    public static void setConflictTags(ItemStack itemStack, String... tags) {
        setConflictTags(itemStack, Arrays.asList(tags));
    }

    /**
     * 获取MOD的所有冲突标签
     * @param itemStack 物品堆
     * @return 冲突标签列表
     */
    public static List<String> getConflictTags(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Collections.emptyList();
        }

        NBTTagCompound kuvalichModule = itemStack.getSubCompound(Reference.MOD_ID + "_modules");
        if (kuvalichModule == null || !kuvalichModule.hasKey("conflictTags")) {
            return Collections.emptyList();
        }

        NBTTagList tagList = kuvalichModule.getTagList("conflictTags", Constants.NBT.TAG_STRING);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < tagList.tagCount(); i++) {
            result.add(tagList.getStringTagAt(i));
        }

        return result;
    }

    /**
     * 检查两个MOD是否冲突（双向检测）
     * @param stack1 第一个MOD
     * @param stack2 第二个MOD
     * @return true=冲突，false=不冲突
     */
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

        // 双向检测：只要有任意一个标签相同就冲突
        for (String tag1 : tags1) {
            if (tags2.contains(tag1)) {
                return true;
            }
        }

        return false;
    }

    // ========== 缓存管理 ==========

    private static int getCacheKey(ItemStack itemStack) {
        NBTTagCompound nbt = itemStack.getTagCompound();
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