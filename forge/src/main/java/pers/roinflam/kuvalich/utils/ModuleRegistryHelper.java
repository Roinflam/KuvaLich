package pers.roinflam.kuvalich.utils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.config.ModuleConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组注册辅助类
 * Module registration helper class
 *
 * 简化模组的创建和注册流程，自动处理禁用检查和属性倍率
 * Simplify module creation and registration process, automatically handle disable checks and attribute multiplier
 */
public class ModuleRegistryHelper {

    /**
     * 注册一个模组到创造栏和静态列表
     * Register a module to creative tab and static list
     *
     * 自动检查是否被禁用，并应用全局属性倍率
     * Automatically check if disabled, and apply global attribute multiplier
     *
     * @param item           模组物品实例 / module item instance
     * @param items          创造栏物品列表（可为null，仅用于初始化时）/ creative tab item list (can be null, only for initialization)
     * @param itemStackList  静态模组列表（用于随机获取）/ static module list (for random access)
     * @param translationKey 翻译键 / translation key
     * @param type           模组类型 / module type
     * @param attributes     属性数组，格式: [属性名1, 值1, 属性名2, 值2, ...] / attributes array
     * @param conflictTags   冲突标签（可选，可为null或空）/ conflict tags (optional)
     */
    public static void register(
            Item item,
            List<ItemStack> items,
            List<ItemStack> itemStackList,
            String translationKey,
            String type,
            Object[] attributes,
            String... conflictTags
    ) {
        // 检查是否被禁用
        if (ModuleConfig.isTypeDisabled(type)) {
            return;
        }

        ItemStack itemStack = new ItemStack(item);
        itemStack.setHoverName(net.minecraft.network.chat.Component.translatable(translationKey));

        // 获取全局模组属性倍率
        // Get global module attribute multiplier
        double multiplier = ModConfig.KUVA_LICH.moduleAttributeMultiplier.get();

        // 添加属性（成对解析），乘以全局倍率
        // Add attributes (parse in pairs), multiply by global multiplier
        for (int i = 0; i < attributes.length; i += 2) {
            String attrName = (String) attributes[i];
            double attrValue = ((Number) attributes[i + 1]).doubleValue();
            // 应用倍率（正负值均等比例缩放）
            // Apply multiplier (both positive and negative values are scaled proportionally)
            attrValue = attrValue * multiplier;
            ItemModuleBase.addAttributes(itemStack, attrName, (float) attrValue);
        }

        ItemModuleBase.setType(itemStack, type);

        // 设置冲突标签
        if (conflictTags != null && conflictTags.length > 0) {
            ItemModuleBase.setConflictTags(itemStack, conflictTags);
        }

        if (items != null) {
            items.add(itemStack);
        }

        itemStackList.add(itemStack.copy());
    }

    /**
     * 注册一个无冲突标签的模组（简化版）
     * Register a module without conflict tags (simplified version)
     */
    public static void register(
            Item item,
            List<ItemStack> items,
            List<ItemStack> itemStackList,
            String translationKey,
            String type,
            Object[] attributes
    ) {
        register(item, items, itemStackList, translationKey, type, attributes, (String[]) null);
    }

    /**
     * 从模组列表中过滤掉被禁用的模组
     * Filter out disabled modules from module list
     *
     * @param moduleList 原始模组列表 / original module list
     * @return 过滤后的可用模组列表 / filtered available module list
     */
    public static List<ItemStack> filterDisabled(List<ItemStack> moduleList) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : moduleList) {
            String type = ModuleBase.getType(stack);
            if (!ModuleConfig.isTypeDisabled(type)) {
                result.add(stack);
            }
        }
        return result;
    }
}