package pers.roinflam.kuvalich.base.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.level.ModuleLevelTooltipHelper;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 武器模组基类（1.20.1版本）
 * Item Module Base Class
 *
 * ⭐ 属性词条根据等级动态缩放显示
 * ⭐ 满级不显示等级Tooltip
 * ⭐ 裂罅在安魂之融中显示洗卡费用（倾向+次数双维度）
 * ⭐ 升级费用根据品质缩放（铜25%/银50%/金75%/Prime&裂罅100%）
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public abstract class AbstractItemModule extends AbstractModule {

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
                    "gas", "radiation", "magnetic", "corrosion", "explosion", "virus",
                    "killStackBaseDamage", "killStackMultishot", "killStackMeleeCriticalMultiplier",
                    "killStackTriggerChance", "killStackAttackRange", "killStackAttackSpeed",
                    "killStackBurstingRadius", "killStackFiringRate",
                    "reload_speed", "magazine_size", "projectile_speed", "recoil_reduction",
                    "gun_damage", "headshot_damage", "aim_time", "accuracy"
            ))
    );

    public AbstractItemModule(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    public boolean isWarframe() {
        return false;
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) { return; }
        Item item = itemStack.getItem();
        if (!(item instanceof AbstractItemModule)) { return; }
        List<Component> tooltip = event.getToolTip();
        if (AbstractModule.isRandom(itemStack)) {
            for (int i = 1; i < 4 && i < tooltip.size(); i++) {
                tooltip.add(i, Component.translatable("kuvaweapon.item_type_random.tooltip")
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            addAttributeTooltips(tooltip, itemStack, item);
        }
    }

    private static void addAttributeTooltips(List<Component> tooltip, ItemStack itemStack, Item item) {
        int number = 1;

        if (item instanceof ItemRivenModule) {
            number = addRivenTooltips(tooltip, itemStack, number);
        }

        // ⭐ 等级系统Tooltip（满级自动跳过）
        if (ModuleLevelHelper.isLevelSystemEnabled()) {
            int currentLevel = ModuleLevelHelper.getModuleLevel(itemStack);
            int maxLevel = ModuleLevelHelper.getMaxLevel();
            number += ModuleLevelTooltipHelper.appendLevelTooltip(tooltip, number, currentLevel, maxLevel);
            // ⭐ 传入itemStack以支持品质缩放费用显示
            number += ModuleLevelTooltipHelper.appendUpgradeCostTooltipIfInEvolve(tooltip, number, currentLevel, itemStack);
        }

        // ⭐ 属性值根据等级缩放后显示
        double levelMult = ModuleLevelHelper.getEffectiveMultiplier(itemStack);

        for (Map.Entry<String, Double> attributeTag : AbstractModule.getAttributes(itemStack)) {
            double scaledValue = attributeTag.getValue() * levelMult;
            String prefix = scaledValue >= 0 ? "+" : "";
            int percentage = (int) (scaledValue * 100);
            String attributeKey = attributeTag.getKey();

            Component attributeName;
            if (attributeKey.startsWith("killStack")) {
                int maxStacks = getMaxStacksForAttribute(attributeKey);
                attributeName = Component.translatable("kuvaweapon.item_attribute_type." + attributeKey, maxStacks);
            } else {
                attributeName = Component.translatable("kuvaweapon.item_attribute_type." + attributeKey);
            }

            ChatFormatting color = getModuleColor(item);
            tooltip.add(number++, Component.literal(prefix + percentage + "% ")
                    .append(attributeName)
                    .withStyle(color));
        }

        tooltip.add(number, Component.translatable("kuvaweapon.item_type.tooltip")
                .withStyle(ChatFormatting.WHITE));
    }

    private static int getMaxStacksForAttribute(String attributeKey) {
        switch (attributeKey) {
            case "killStackBaseDamage": return ModConfig.KUVA_LICH.maxStacksBaseDamage.get();
            case "killStackMultishot": return ModConfig.KUVA_LICH.maxStacksMultishot.get();
            case "killStackMeleeCriticalMultiplier": return ModConfig.KUVA_LICH.maxStacksMeleeCritMult.get();
            case "killStackTriggerChance": return ModConfig.KUVA_LICH.maxStacksTriggerChance.get();
            case "killStackAttackRange": return ModConfig.KUVA_LICH.maxStacksAttackRange.get();
            case "killStackAttackSpeed": return ModConfig.KUVA_LICH.maxStacksAttackSpeed.get();
            case "killStackBurstingRadius": return ModConfig.KUVA_LICH.maxStacksBurstingRadius.get();
            case "killStackFiringRate": return ModConfig.KUVA_LICH.maxStacksFiringRate.get();
            default: return 0;
        }
    }

    private static int addRivenTooltips(List<Component> tooltip, ItemStack itemStack, int startIndex) {
        int trend = ItemRivenModule.getTrend(itemStack);
        StringBuilder trendBar = new StringBuilder(15);
        for (int i = 0; i < trend; i++) { trendBar.append("●"); }
        for (int i = trend; i < 5; i++) { trendBar.append("○"); }
        tooltip.add(startIndex++,
                Component.translatable("kuvaweapon.item_type_riven_trend.tooltip")
                        .append(" ").append(Component.literal(trendBar.toString()).withStyle(ChatFormatting.BOLD))
                        .withStyle(ChatFormatting.DARK_PURPLE));
        int cycle = ItemRivenModule.getCycle(itemStack);
        if (cycle > 0) {
            tooltip.add(startIndex++,
                    Component.translatable("kuvaweapon.item_type_riven_cycle.tooltip")
                            .append(" ").append(Component.literal(String.valueOf(cycle)).withStyle(ChatFormatting.BOLD))
                            .withStyle(ChatFormatting.DARK_PURPLE));
        }
        // ⭐ 在安魂之融中显示洗卡所需赤毒（武器裂罅公式：min(cycle,8) + trend² - (trend-1)²）
        startIndex += ModuleLevelTooltipHelper.appendRivenCycleCostTooltipIfInEvolve(
                tooltip, startIndex, trend, cycle, false);
        return startIndex;
    }

    private static ChatFormatting getModuleColor(Item item) {
        if (item instanceof ItemCommonModule) return ChatFormatting.GOLD;
        if (item instanceof ItemUncommonModule) return ChatFormatting.AQUA;
        if (item instanceof ItemRareModule) return ChatFormatting.YELLOW;
        if (item instanceof ItemPrimeModule) return ChatFormatting.WHITE;
        if (item instanceof ItemRivenModule) return ChatFormatting.LIGHT_PURPLE;
        return ChatFormatting.WHITE;
    }
}
