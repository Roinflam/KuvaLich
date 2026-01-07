package pers.roinflam.kuvalich.base.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.item.module.item.*;

import javax.annotation.Nonnull;
import java.util.*;

@Mod.EventBusSubscriber
public abstract class ItemModuleBase extends ModuleBase {
    // 使用不可变Set提升性能
    public static final Set<String> ITEM_ATTRIBUTE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "meleeDamage", "remoteDamage", "arrowDamage", "projectileDamage", "magicDamage",
                    "multishot", "attackSpeed", "attackRange", "triggerChance",
                    "meleeCriticalStrikeProbability", "meleeCriticalStrikeMultiplier",
                    "remoteCriticalStrikeProbability", "remoteCriticalStrikeMultiplier",
                    "bane_of_undefined", "bane_of_undead", "bane_of_arthropod", "bane_of_illager",
                    "fire", "ice", "poison", "electricity", "firing_rate", "triggerTime",
                    "slash", "puncture", "impact", "dashMeleeCriticalStrikeProbability",
                    "dashAttackRange", "dashTriggerChance", "baseDamageWhenNotCriticalStrike",
                    "bursting_radius",
                    // 复合元素词条
                    "gas", "radiation", "magnetic", "corrosion", "explosion", "virus",
                    // 击杀叠加词条
                    "killStackBaseDamage",              // 击杀时：目标身上每一种负面效果可增加基础伤害
                    "killStackMultishot",               // 击杀时：多重射击
                    "killStackMeleeCriticalMultiplier", // 击杀时：近战暴击伤害
                    "killStackTriggerChance",           // 击杀时：触发几率
                    "killStackAttackRange",             // 击杀时：攻击范围
                    "killStackAttackSpeed",             // 击杀时：攻击速度
                    "killStackBurstingRadius",          // 击杀时：爆炸半径
                    "killStackFiringRate"               // 击杀时：射速
            ))
    );

    public ItemModuleBase(@Nonnull String name) {
        super(name);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        Item item = itemStack.getItem();
        if (!(item instanceof ItemModuleBase)) {
            return;
        }

        if (ModuleBase.isRandom(itemStack)) {
            for (int i = 1; i < 4; i++) {
                evt.getToolTip().add(i, TextFormatting.GRAY + I18n.format("kuvaweapon.item_type_random.tooltip"));
            }
        } else {
            addAttributeTooltips(evt, itemStack, item);
        }
    }

    /**
     * 添加属性提示文本
     */
    private static void addAttributeTooltips(ItemTooltipEvent evt, ItemStack itemStack, Item item) {
        int number = 1;

        // Riven模组特殊处理
        if (item instanceof ItemRivenModule) {
            number = addRivenTooltips(evt, itemStack, number);
        }

        // 添加属性列表
        for (Map.Entry<String, Double> attributeTag : ModuleBase.getAttributes(itemStack)) {
            String prefix = attributeTag.getValue() >= 0 ? "+" : "";
            int percentage = (int) (attributeTag.getValue() * 100);
            String attributeKey = attributeTag.getKey();

            // 获取翻译文本
            String attributeName;

            // 如果是击杀叠加词条，传入最大层数参数（使用 %d 占位符）
            if (attributeKey.startsWith("killStack")) {
                int maxStacks = getMaxStacksForAttribute(attributeKey);
                attributeName = I18n.format("kuvaweapon.item_attribute_type." + attributeKey, maxStacks);
            } else {
                attributeName = I18n.format("kuvaweapon.item_attribute_type." + attributeKey);
            }

            TextFormatting color = getModuleColor(item);
            evt.getToolTip().add(number++, color + prefix + percentage + "% " + attributeName);
        }

        evt.getToolTip().add(number, TextFormatting.WHITE + I18n.format("kuvaweapon.item_type.tooltip"));
    }

    /**
     * 获取击杀叠加词条的最大层数（从配置读取）
     *
     * @param attributeKey 属性键名
     * @return 最大层数
     */
    private static int getMaxStacksForAttribute(String attributeKey) {
        switch (attributeKey) {
            case "killStackBaseDamage":
                return ModConfig.KUVA_LICH.maxStacksBaseDamage;
            case "killStackMultishot":
                return ModConfig.KUVA_LICH.maxStacksMultishot;
            case "killStackMeleeCriticalMultiplier":
                return ModConfig.KUVA_LICH.maxStacksMeleeCritMult;
            case "killStackTriggerChance":
                return ModConfig.KUVA_LICH.maxStacksTriggerChance;
            case "killStackAttackRange":
                return ModConfig.KUVA_LICH.maxStacksAttackRange;
            case "killStackAttackSpeed":
                return ModConfig.KUVA_LICH.maxStacksAttackSpeed;
            case "killStackBurstingRadius":
                return ModConfig.KUVA_LICH.maxStacksBurstingRadius;
            case "killStackFiringRate":
                return ModConfig.KUVA_LICH.maxStacksFiringRate;
            default:
                return 0; // 默认值（正常情况不会用到）
        }
    }

    /**
     * 添加Riven模组提示
     */
    private static int addRivenTooltips(ItemTooltipEvent evt, ItemStack itemStack, int startIndex) {
        int trend = ItemRivenModule.getTrend(itemStack);

        // 使用 ASCII 字符避免 GBK 乱码：[*] 表示已激活，[ ] 表示未激活
        StringBuilder trendBar = new StringBuilder(15);
        for (int i = 0; i < trend; i++) {
            trendBar.append("[*]");
        }
        for (int i = trend; i < 5; i++) {
            trendBar.append("[ ]");
        }

        evt.getToolTip().add(startIndex++,
                TextFormatting.DARK_PURPLE + I18n.format("kuvaweapon.item_type_riven_trend.tooltip")
                        + " " + TextFormatting.BOLD + trendBar);

        int cycle = ItemRivenModule.getCycle(itemStack);
        if (cycle > 0) {
            evt.getToolTip().add(startIndex++,
                    TextFormatting.DARK_PURPLE + I18n.format("kuvaweapon.item_type_riven_cycle.tooltip")
                            + " " + TextFormatting.BOLD + cycle);
        }

        return startIndex;
    }

    /**
     * 获取模组颜色
     */
    private static TextFormatting getModuleColor(Item item) {
        if (item instanceof ItemCommonModule) return TextFormatting.GOLD;
        if (item instanceof ItemUncommonModule) return TextFormatting.AQUA;
        if (item instanceof ItemRareModule) return TextFormatting.YELLOW;
        if (item instanceof ItemPrimeModule) return TextFormatting.WHITE;
        if (item instanceof ItemRivenModule) return TextFormatting.LIGHT_PURPLE;
        return TextFormatting.WHITE;
    }
}