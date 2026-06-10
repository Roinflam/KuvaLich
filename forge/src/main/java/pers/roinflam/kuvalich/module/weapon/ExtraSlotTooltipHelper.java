package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.*;

/**
 * 额外装备槽位Tooltip辅助类（客户端专用）
 * Extra equipment slot tooltip helper (client-only)
 * <p>
 * 提供两个功能：
 * 1. mergeExtraSlotIntoAttributes — 将额外槽位属性合并到面板属性表（仅主手武器）
 * 2. appendExtraSlotTooltip — 在"已装备以下模组"上方追加额外装备加成明细（仅主手武器）
 * <p>
 * 元素词条在额外加成区块中合并为一行"元素伤害"，避免与主手触发属性显示产生歧义。
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class ExtraSlotTooltipHelper {

    /**
     * 非元素属性的显示顺序表（与 WeaponModuleHandler.onItemTooltip 中面板属性的显示顺序一致）
     * 元素词条不在此列表中，统一合并为"元素伤害"一行显示。
     * Non-element attribute display order (matches panel order).
     * Element attributes are excluded — they are merged into a single "Elemental Damage" line.
     */
    private static final List<String> ATTRIBUTE_ORDER = List.of(
            "meleeDamage", "remoteDamage", "arrowDamage", "projectileDamage", "magicDamage",
            "baseDamageWhenNotCriticalStrike",
            "attackRange", "bursting_radius", "attackSpeed", "firing_rate",
            "meleeCriticalStrikeProbability", "remoteCriticalStrikeProbability",
            "meleeCriticalStrikeMultiplier", "remoteCriticalStrikeMultiplier",
            "multishot", "triggerChance", "triggerTime",
            "first_bullet_damage",
            "bane_of_undefined", "bane_of_undead", "bane_of_arthropod", "bane_of_illager",
            "dashMeleeCriticalStrikeProbability", "dashAttackRange", "dashTriggerChance",
            "reload_speed", "magazine_size", "projectile_speed", "recoil_reduction",
            "gun_damage", "headshot_damage", "aim_time", "accuracy",
            // 第三批新词条 / Third batch new attributes
            "true_bullet", "gun_loot_drop", "execute_threshold", "purge_buff", "execute_chance",
            // 击杀叠层 / Kill stacks
            "killStackBaseDamage", "killStackMultishot", "killStackMeleeCriticalMultiplier",
            "killStackTriggerChance", "killStackAttackRange", "killStackAttackSpeed",
            "killStackBurstingRadius", "killStackFiringRate"
    );

    /**
     * 所有元素属性键集合（在额外加成区块中合并为一行）
     * All element attribute keys (merged into single line in extra slot display)
     */
    private static final Set<String> ELEMENT_ATTRIBUTES = Set.of(
            "fire", "ice", "poison", "electricity",
            "slash", "puncture", "impact",
            "gas", "radiation", "magnetic", "corrosion", "explosion", "virus"
    );

    /**
     * 击杀叠层属性 → 基础属性名的映射（用于获取无 %d 占位符的显示名）
     * Kill stack attribute → base attribute name mapping (avoids %d formatting error)
     */
    private static final Map<String, String> KILL_STACK_TO_BASE = Map.of(
            "killStackBaseDamage", "meleeDamage",
            "killStackMultishot", "multishot",
            "killStackMeleeCriticalMultiplier", "meleeCriticalStrikeMultiplier",
            "killStackTriggerChance", "triggerChance",
            "killStackAttackRange", "attackRange",
            "killStackAttackSpeed", "attackSpeed",
            "killStackBurstingRadius", "bursting_radius",
            "killStackFiringRate", "firing_rate"
    );

    // ========== 主手检测 / Main Hand Detection ==========

    /**
     * 判断当前查看的物品是否为主手武器
     * Check if the viewed item is the main hand weapon
     * <p>
     * 引用比较优先（物品栏GUI中tooltip事件通常传入槽位原始引用）；
     * 内容比较兜底，但当主副手内容完全相同时仅引用匹配才通过。
     *
     * @param weaponStack 被查看的物品栈 / item being viewed
     * @param player      玩家 / player
     * @return 是否为主手武器 / whether it's the main hand weapon
     */
    private static boolean isMainHandWeapon(ItemStack weaponStack, Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // 引用比较：直接是主手对象 → 一定是主手
        // Reference match: definitely main hand
        if (weaponStack == mainHand) {
            return true;
        }

        // 内容比较：匹配主手内容
        // Content match: matches main hand content
        if (ItemStack.matches(weaponStack, mainHand)) {
            // 如果副手也匹配（主副手内容相同），无法区分 → 不显示
            // If off-hand also matches (identical items), can't distinguish → skip
            return !ItemStack.matches(weaponStack, offHand);
        }

        return false;
    }

    // ========== 公开方法 / Public Methods ==========

    /**
     * 将额外槽位属性合并到面板属性表（仅主手武器）
     * Merge extra slot attributes into tooltip attribute map (main hand only)
     *
     * @param player      查看物品的玩家（可为null） / player (may be null)
     * @param weaponStack 被查看的物品栈 / item being viewed
     * @param attributes  面板属性表（会被直接修改） / tooltip attribute map (modified in-place)
     */
    public static void mergeExtraSlotIntoAttributes(Player player, ItemStack weaponStack, HashMap<String, Double> attributes) {
        if (player == null || !isMainHandWeapon(weaponStack, player)) {
            return;
        }

        HashMap<String, Double> extraAttrs = WeaponCombatHandler.getCachedExtraSlotAttributes(player);
        for (Map.Entry<String, Double> entry : extraAttrs.entrySet()) {
            if (Math.abs(entry.getValue()) >= 0.001) {
                attributes.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
    }

    /**
     * 在Tooltip中追加额外槽位属性加成明细（仅主手武器，放在"已装备以下模组"上方）
     * Append extra slot attribute bonus details (main hand only, above "Equipped modules")
     * <p>
     * 非元素属性按与面板相同的顺序排列，元素词条合并为一行"元素伤害"。
     *
     * @param tooltip     Tooltip组件列表 / tooltip component list
     * @param player      查看物品的玩家（可为null） / player (may be null)
     * @param weaponStack 被查看的武器物品栈 / weapon ItemStack being viewed
     * @param index       当前插入位置 / current insert position
     * @return 插入的行数（用于调用方更新 index）/ number of lines inserted
     */
    public static int appendExtraSlotTooltip(List<Component> tooltip, Player player, ItemStack weaponStack, int index) {
        if (player == null || !isMainHandWeapon(weaponStack, player)) {
            return 0;
        }

        HashMap<String, Double> extraAttrs = WeaponCombatHandler.getCachedExtraSlotAttributes(player);

        // 按预定义顺序收集非元素有效词条 / Collect non-element entries in defined order
        List<Map.Entry<String, Double>> orderedEntries = new ArrayList<>();
        for (String key : ATTRIBUTE_ORDER) {
            Double value = extraAttrs.get(key);
            if (value != null && Math.abs(value) >= 0.001) {
                orderedEntries.add(new AbstractMap.SimpleEntry<>(key, value));
            }
        }
        // 追加不在预定义列表中且非元素的词条（兜底）/ Append unlisted non-element attributes
        for (Map.Entry<String, Double> entry : extraAttrs.entrySet()) {
            if (Math.abs(entry.getValue()) >= 0.001
                    && !ATTRIBUTE_ORDER.contains(entry.getKey())
                    && !ELEMENT_ATTRIBUTES.contains(entry.getKey())) {
                orderedEntries.add(entry);
            }
        }

        // 计算元素伤害总值 / Calculate total element damage
        double totalElementDamage = 0;
        for (String elemKey : ELEMENT_ATTRIBUTES) {
            Double value = extraAttrs.get(elemKey);
            if (value != null) {
                totalElementDamage += value;
            }
        }

        // 无任何有效词条则跳过 / Skip if nothing to show
        if (orderedEntries.isEmpty() && Math.abs(totalElementDamage) < 0.001) {
            return 0;
        }

        int linesAdded = 0;

        // 标题行（绿色加粗，无图标）/ Header line (green bold, no icon)
        tooltip.add(index + linesAdded, Component.translatable("item.module.extra_slot_header")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        linesAdded++;

        // 按顺序显示非元素属性 / Display non-element attributes in order
        for (Map.Entry<String, Double> entry : orderedEntries) {
            String attrKey = entry.getKey();
            double value = entry.getValue();
            String displayName = getAttributeDisplayName(attrKey);
            String displayValue = formatAttributeValue(attrKey, value);

            tooltip.add(index + linesAdded, Component.literal("  " + displayName + " ")
                    .append(Component.literal(displayValue)
                            .withStyle(value >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
            linesAdded++;
        }

        // 元素伤害合并为一行（使用与面板相同的"元素伤害"翻译键）
        // Element damage merged into single line (using same "Elemental Damage" translation key)
        if (Math.abs(totalElementDamage) >= 0.001) {
            String elemName = I18n.get("item.module.triggerDamage");
            String elemValue = (totalElementDamage >= 0 ? "+" : "") + (int) (totalElementDamage * 100) + "%";
            tooltip.add(index + linesAdded, Component.literal("  " + elemName + " ")
                    .append(Component.literal(elemValue)
                            .withStyle(totalElementDamage >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)));
            linesAdded++;
        }

        return linesAdded;
    }

    // ========== 显示名与格式化 / Display Name & Formatting ==========

    /**
     * 获取属性的显示名称（国际化，安全处理 killStack 的 %d 占位符）
     * Get localized display name (safely handles killStack %d placeholders)
     *
     * @param attrKey 属性键名 / attribute key
     * @return 本地化显示名称 / localized display name
     */
    private static String getAttributeDisplayName(String attrKey) {
        // 击杀叠层：映射到基础属性名获取显示名，避免 %d 格式化崩溃
        // Kill stacks: map to base attribute name to avoid %d formatting crash
        if (attrKey.startsWith("killStack")) {
            String baseAttr = KILL_STACK_TO_BASE.get(attrKey);
            if (baseAttr != null) {
                return getAttributeDisplayName(baseAttr);
            }
        }

        // 优先尝试短名称 / Try short name first
        String key1 = "item.module." + attrKey;
        if (I18n.exists(key1)) {
            return I18n.get(key1);
        }

        // 再尝试描述名称（截取第一行）/ Try descriptive name (first line)
        String key2 = "kuvaweapon.item_attribute_type." + attrKey;
        if (I18n.exists(key2)) {
            String full = I18n.get(key2);
            int lineBreak = full.indexOf('\n');
            if (lineBreak > 0) {
                return full.substring(0, lineBreak);
            }
            return full;
        }

        return attrKey;
    }

    /**
     * 格式化属性值为显示字符串（国际化）
     * Format attribute value as localized display string
     *
     * @param attrKey 属性键名 / attribute key
     * @param value   属性值 / attribute value
     * @return 格式化后的字符串 / formatted string
     */
    private static String formatAttributeValue(String attrKey, double value) {
        String sign = value >= 0 ? "+" : "";

        // 爆炸半径显示为米 / Burst radius displayed as meters
        if (attrKey.equals("bursting_radius")) {
            return sign + String.format("%.1f", value * 2) + "m";
        }

        // 击杀叠层类：加"每层"后缀 / Kill stack: add "per stack" suffix
        if (attrKey.startsWith("killStack")) {
            String perStack = I18n.get("item.module.extra_slot_per_stack");
            return sign + (int) (value * 100) + "%/" + perStack;
        }

        // ⭐ 秒杀概率数值极小，保留小数并去掉末尾多余的0（0.010%→0.01%）/ Instant kill: tiny value
        if (attrKey.equals("execute_chance")) {
            String percentText = String.format("%.3f", value * 100);
            if (percentText.indexOf('.') >= 0) {
                percentText = percentText.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            return sign + percentText + "%";
        }

        // 默认显示为百分比 / Default display as percentage
        return sign + (int) (value * 100) + "%";
    }

    /** 工具类禁止实例化 / Utility class, no instantiation */
    private ExtraSlotTooltipHelper() {
    }
}
