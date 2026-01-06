package pers.roinflam.kuvalich.base.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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
    private static final Set<String> MODULE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("common", "uncommon", "rare", "prime", "riven"))
    );

    // 🔧 优化：添加缓存大小限制和更精细的控制
    private static final int MAX_CACHE_SIZE = 1000; // 最多缓存1000个物品
    private static final long CACHE_DURATION_MS = 30000; // 30秒过期
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

        // 检查缓存
        CacheEntry cached = ATTRIBUTE_CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.attributes;
        }

        // 缓存未命中，从NBT读取
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

        // 🔧 优化：检查缓存大小，超过限制时清理
        if (ATTRIBUTE_CACHE.size() >= MAX_CACHE_SIZE) {
            cleanExpiredCache();
            // 如果清理后还是满的，清理最老的一半
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

    private static int getCacheKey(ItemStack itemStack) {
        NBTTagCompound nbt = itemStack.getTagCompound();
        return nbt != null ? nbt.hashCode() : 0;
    }

    private static void invalidateCache(ItemStack itemStack) {
        ATTRIBUTE_CACHE.remove(getCacheKey(itemStack));
    }

    /**
     * 清理过期缓存
     */
    public static void cleanExpiredCache() {
        ATTRIBUTE_CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 🔧 新增：清理最老的一半缓存
     */
    private static void cleanOldestCache() {
        if (ATTRIBUTE_CACHE.isEmpty()) return;

        List<Map.Entry<Integer, CacheEntry>> entries = new ArrayList<>(ATTRIBUTE_CACHE.entrySet());
        entries.sort(Comparator.comparingLong(e -> e.getValue().timestamp));

        int removeCount = entries.size() / 2;
        for (int i = 0; i < removeCount; i++) {
            ATTRIBUTE_CACHE.remove(entries.get(i).getKey());
        }
    }

    /**
     * 🔧 新增：获取缓存统计信息（用于调试）
     */
    public static String getCacheStats() {
        long expired = ATTRIBUTE_CACHE.values().stream().filter(CacheEntry::isExpired).count();
        return String.format("缓存总数: %d, 过期: %d, 有效: %d, 容量: %d%%",
                ATTRIBUTE_CACHE.size(),
                expired,
                ATTRIBUTE_CACHE.size() - expired,
                ATTRIBUTE_CACHE.size() * 100 / MAX_CACHE_SIZE);
    }
}