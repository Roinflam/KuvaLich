package pers.roinflam.kuvalich.utils;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.config.ModuleConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组注册辅助类
 * 简化模组的创建和注册流程，自动处理禁用检查
 */
public class ModuleRegistryHelper {

    /**
     * 注册一个模组到创造栏和静态列表
     * 自动检查是否被禁用
     *
     * @param item           模组物品实例
     * @param items          创造栏物品列表
     * @param itemStackList  静态模组列表（用于随机获取）
     * @param translationKey 翻译键
     * @param type           模组类型
     * @param attributes     属性数组，格式: [属性名1, 值1, 属性名2, 值2, ...]
     * @param conflictTags   冲突标签（可选，可为null或空）
     */
    public static void register(
            Item item,
            NonNullList<ItemStack> items,
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
        itemStack.setTranslatableName(translationKey);

        // 添加属性（成对解析）
        for (int i = 0; i < attributes.length; i += 2) {
            String attrName = (String) attributes[i];
            float attrValue = ((Number) attributes[i + 1]).floatValue();
            ItemModuleBase.addAttributes(itemStack, attrName, attrValue);
        }

        ItemModuleBase.setType(itemStack, type);

        // 设置冲突标签
        if (conflictTags != null && conflictTags.length > 0) {
            ItemModuleBase.setConflictTags(itemStack, conflictTags);
        }

        items.add(itemStack);
        itemStackList.add(itemStack.copy());
    }

    /**
     * 注册一个无冲突标签的模组（简化版）
     */
    public static void register(
            Item item,
            NonNullList<ItemStack> items,
            List<ItemStack> itemStackList,
            String translationKey,
            String type,
            Object[] attributes
    ) {
        register(item, items, itemStackList, translationKey, type, attributes, (String[]) null);
    }

    /**
     * 从模组列表中过滤掉被禁用的模组
     * 用于随机获取时的过滤
     *
     * @param moduleList 原始模组列表
     * @return 过滤后的可用模组列表
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