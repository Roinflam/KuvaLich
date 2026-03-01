package pers.roinflam.kuvalich.utils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.config.ModuleConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模组注册辅助类
 * Module registration helper class
 *
 * 简化模组的创建和注册流程，自动处理禁用检查和属性倍率
 * Simplify module creation and registration process, automatically handle disable checks and attribute multiplier
 */
public class ModuleRegistryHelper {

    /**
     * 关键词条集合 —— 使用 keyAttributeMultiplier 单独缩放
     * Key attributes — scaled by keyAttributeMultiplier independently
     *
     * 判定标准：缩放后会破坏机制本身的逻辑
     * Criteria: scaling would break the mechanic's own logic
     *
     * - 暴击几率/倍率：代码中有100%/200%/300%特殊档位判定，缩放会错乱档位行为
     * - 触发几率/时间：超过100%有整除多次触发的特殊逻辑，缩放会破坏触发次数计算
     * - 护盾恢复延迟/速率：乘算机制（baseMultiplier * (1 ± value)），再叠全局倍率会双重缩放
     * - 以上对应的击杀叠层版本同理
     */
    private static final Set<String> KEY_ATTRIBUTES = new HashSet<>(Arrays.asList(

            // ========== 暴击类（档位机制）==========
            "meleeCriticalStrikeProbability",       // 近战暴击几率
            "meleeCriticalStrikeMultiplier",        // 近战暴击伤害
            "remoteCriticalStrikeProbability",      // 远程暴击几率
            "remoteCriticalStrikeMultiplier",       // 远程暴击伤害
            "dashMeleeCriticalStrikeProbability",   // 冲刺攻击时暴击几率

            // ========== 触发类（多次触发机制）==========
            "triggerChance",                        // 触发几率
            "dashTriggerChance",                    // 冲刺攻击时触发几率
            "triggerTime",                          // 触发时间

            // ========== 护盾机制类（乘算机制）==========
            "shieldRecoveryDelay",                  // 护盾恢复延迟
            "shieldRecoveryRate",                   // 护盾恢复速率

            // ========== 击杀叠层：对应关键词条 ==========
            "killStackMeleeCriticalMultiplier",     // 近战暴击伤害（击杀叠层）
            "killStackTriggerChance",               // 触发几率（击杀叠层）
            "killStackShieldRecoveryRate",          // 护盾恢复速率（击杀叠层）
            "killStackShieldRecoveryDelay"          // 护盾恢复延迟（击杀叠层）
    ));

    /**
     * 注册一个模组到创造栏和静态列表
     * Register a module to creative tab and static list
     *
     * 关键词条使用 keyAttributeMultiplier，其余使用 moduleAttributeMultiplier，默认均为1.0
     * Key attributes use keyAttributeMultiplier, others use moduleAttributeMultiplier, both default to 1.0
     *
     * @param item           模组物品实例 / module item instance
     * @param items          创造栏物品列表（可为null）/ creative tab item list (can be null)
     * @param itemStackList  静态模组列表（用于随机获取）/ static module list (for random access)
     * @param translationKey 翻译键 / translation key
     * @param type           模组类型 / module type
     * @param attributes     属性数组，格式: [属性名1, 值1, 属性名2, 值2, ...] / attributes array
     * @param conflictTags   冲突标签（可选）/ conflict tags (optional)
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

        // 分别读取两套倍率，默认均为1.0互不干扰
        // Read both multipliers separately, both default to 1.0 and are independent
        double generalMultiplier = ModConfig.KUVA_LICH.moduleAttributeMultiplier.get();
        double keyMultiplier = ModConfig.KUVA_LICH.keyAttributeMultiplier.get();

        // 成对解析属性，按分类选择对应倍率
        // Parse attributes in pairs, apply corresponding multiplier by category
        for (int i = 0; i < attributes.length; i += 2) {
            String attrName = (String) attributes[i];
            double attrValue = ((Number) attributes[i + 1]).doubleValue();

            if (KEY_ATTRIBUTES.contains(attrName)) {
                // 关键词条：使用关键词条倍率
                // Key attribute: use key attribute multiplier
                attrValue = attrValue * keyMultiplier;
            } else {
                // 普通词条：使用通用倍率
                // General attribute: use general multiplier
                attrValue = attrValue * generalMultiplier;
            }

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
        List<ItemStack> result = new java.util.ArrayList<>();
        for (ItemStack stack : moduleList) {
            String type = ModuleBase.getType(stack);
            if (!ModuleConfig.isTypeDisabled(type)) {
                result.add(stack);
            }
        }
        return result;
    }
}