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

/**
 * 模组基类（1.20.1版本，业务逻辑100%不变）
 * Module Base Class (1.20.1 version, business logic 100% unchanged)
 *
 * <p>⭐ 2026-09-23 性能修复：{@link #getAttributes} 的哈希校验缓存已整体移除，改为直接解析。
 * 历史上这里先后有过两版缓存（完整 ItemStack NBT → 模组子 tag），都基于
 * "{@code hashCode()} 当索引 + {@code equals()} 防碰撞校验" 的思路，命中一次要递归遍历
 * 两遍模组子 tag（attributeList 的每个词条各算一次 hashCode、再 equals 一次），
 * 未命中还要再加一次 {@code copy()} 深拷贝存快照——而"递归遍历一遍 attributeList 取值"
 * 本身只需要一遍遍历。也就是说，命中时的缓存开销（两遍遍历）天然大于不缓存直接解析（一遍遍历），
 * 缓存在这个访问模式下无论命中与否都不可能是净赢。
 * 量级（MFS r3 采样报告，白天高峰批，stackq 口径）：{@code WarframeEffectHandler.onPlayerTick}
 * 调用链下，{@code getAttributes} 里 {@code CompoundTag.hashCode} + {@code CacheEntry.matches}
 * 两帧合计 A 服约 0.022 ms/tick（占该链 0.094 的约 24%）、B 服约 0.016 ms/tick（约 22%）。
 * 直接解析后 {@code getAttributes} 退化成纯函数（无副作用、无跨调用状态），
 * 输出内容与旧实现相同（旧缓存命中时已用 equals 保证内容一致），不存在旧版两次修过的哈希碰撞类问题。</p>
 */
@Mod.EventBusSubscriber
public abstract class AbstractModule extends Item {

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
    }

    /**
     * 读取模组的属性集合（直接解析，无缓存）
     *
     * <p>⭐ 2026-09-23：不再缓存。见类头 javadoc——这里的哈希校验缓存命中一次要遍历两遍
     * attributeList（hashCode + equals），比直接解析一遍还贵，缓存不了了之。
     * 纯函数：只读 {@code <modid>_modules} 子 tag 的 attributeList，无副作用。</p>
     *
     * @param itemStack 模组物品栈
     * @return 属性键值对集合，无模组数据时返回空集合
     */
    public static Set<Map.Entry<String, Double>> getAttributes(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return Collections.emptySet();
        }

        CompoundTag moduleNbt = itemStack.getTagElement(Reference.MOD_ID + "_modules");
        if (moduleNbt == null) {
            return Collections.emptySet();
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

        return attributeMap.entrySet();
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
    }

    public static void setType(ItemStack itemStack, String type) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }
        CompoundTag kuvalichModule = itemStack.getOrCreateTagElement(Reference.MOD_ID + "_modules");
        kuvalichModule.putString("type", type);
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
     * ⭐ 2026-09-23：{@link #getAttributes} 的哈希校验缓存已整体移除（见类头 javadoc），
     * 本方法保留为空实现，只为不去动 {@code KuvaLich.java} 里那个每 5 分钟跑一次的
     * 后台 {@code Timer}（不在主线程/tick 路径上，不属于本次优化范围，改动它没有收益
     * 反而多一处要核对的 diff）。
     */
    public static void cleanExpiredCache() {
        // no-op：缓存已移除
    }
}
