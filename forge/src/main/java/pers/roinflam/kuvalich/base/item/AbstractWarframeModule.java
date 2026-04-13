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
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.level.ModuleLevelTooltipHelper;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 战甲模组基类（1.20.1版本）
 * Warframe Module Base Class
 *
 * ⭐ 属性词条根据等级动态缩放显示
 * ⭐ 满级不显示等级Tooltip
 * ⭐ 裂罅在安魂之融中显示洗卡费用（倾向+次数双维度）
 * ⭐ 升级费用根据品质缩放（铜25%/银50%/金75%/Prime&裂罅100%）
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public abstract class AbstractWarframeModule extends AbstractModule {

    public static final Set<String> WARFRAME_ATTRIBUTE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "health", "shield", "armor", "sprintSpeed", "shieldRecoveryRate",
                    "shieldRecoveryDelay", "knockbackResistance", "fireProtection",
                    "electricProtection", "homologousProtection", "reachDistance",
                    "diggingSpeed", "responseRate", "itemDropMultiplier",
                    "jumpBoost", "fallProtection",
                    "fixedHealth", "fixedShield", "fixedArmor",
                    "killStackHealth", "killStackShield", "killStackArmor", "killStackSprintSpeed",
                    "killStackShieldRecoveryRate", "killStackShieldRecoveryDelay",
                    "killStackFireProtection", "killStackElectricProtection",
                    "killStackHomologousProtection", "killStackResponseRate",
                    "killStackItemDropMultiplier", "killStackDiggingSpeed"
            ))
    );

    public AbstractWarframeModule(@Nonnull Properties properties) {
        super(properties);
    }

    @Override
    public boolean isWarframe() {
        return true;
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) { return; }
        Item item = itemStack.getItem();
        if (!(item instanceof AbstractWarframeModule)) { return; }
        List<Component> tooltip = event.getToolTip();
        if (AbstractModule.isRandom(itemStack)) {
            for (int i = 1; i < 4 && i < tooltip.size(); i++) {
                tooltip.add(i, Component.translatable("kuvaweapon.warframe_type_random.tooltip")
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            addAttributeTooltips(tooltip, itemStack, item);
        }
    }

    private static void addAttributeTooltips(List<Component> tooltip, ItemStack itemStack, Item item) {
        int number = 1;

        if (item instanceof WarframeRivenModule) {
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
            String attributeKey = attributeTag.getKey();
            double scaledValue = attributeTag.getValue() * levelMult;

            if (attributeKey.equals("fixedHealth") || attributeKey.equals("fixedShield") || attributeKey.equals("fixedArmor")) {
                Component attributeName = Component.translatable("kuvaweapon.warframe_attribute_type." + attributeKey);
                ChatFormatting color = getModuleColor(item);
                tooltip.add(number++, Component.literal("+" + (int) scaledValue + " ")
                        .append(attributeName).withStyle(color));
            } else {
                String prefix = scaledValue >= 0 ? "+" : "";
                int percentage = (int) (scaledValue * 100);
                Component attributeName;
                if (attributeKey.startsWith("killStack")) {
                    int maxStacks = getMaxStacksForAttribute(attributeKey);
                    attributeName = Component.translatable("kuvaweapon.warframe_attribute_type." + attributeKey, maxStacks);
                } else {
                    attributeName = Component.translatable("kuvaweapon.warframe_attribute_type." + attributeKey);
                }
                ChatFormatting color = getModuleColor(item);
                tooltip.add(number++, Component.literal(prefix + percentage + "% ")
                        .append(attributeName).withStyle(color));
            }
        }

        tooltip.add(number, Component.translatable("kuvaweapon.warframe_type.tooltip")
                .withStyle(ChatFormatting.WHITE));
    }

    private static int getMaxStacksForAttribute(String attributeKey) {
        switch (attributeKey) {
            case "killStackHealth": return ModConfig.KUVA_LICH.maxStacksHealth.get();
            case "killStackShield": return ModConfig.KUVA_LICH.maxStacksShield.get();
            case "killStackArmor": return ModConfig.KUVA_LICH.maxStacksArmor.get();
            case "killStackSprintSpeed": return ModConfig.KUVA_LICH.maxStacksSprintSpeed.get();
            case "killStackShieldRecoveryRate": return ModConfig.KUVA_LICH.maxStacksShieldRecoveryRate.get();
            case "killStackShieldRecoveryDelay": return ModConfig.KUVA_LICH.maxStacksShieldRecoveryDelay.get();
            case "killStackFireProtection": return ModConfig.KUVA_LICH.maxStacksFireProtection.get();
            case "killStackElectricProtection": return ModConfig.KUVA_LICH.maxStacksElectricProtection.get();
            case "killStackHomologousProtection": return ModConfig.KUVA_LICH.maxStacksHomologousProtection.get();
            case "killStackResponseRate": return ModConfig.KUVA_LICH.maxStacksResponseRate.get();
            case "killStackItemDropMultiplier": return ModConfig.KUVA_LICH.maxStacksItemDropMultiplier.get();
            case "killStackDiggingSpeed": return ModConfig.KUVA_LICH.maxStacksDiggingSpeed.get();
            default: return 0;
        }
    }

    private static int addRivenTooltips(List<Component> tooltip, ItemStack itemStack, int startIndex) {
        int trend = WarframeRivenModule.getTrend(itemStack);
        StringBuilder trendBar = new StringBuilder(5);
        for (int i = 0; i < trend; i++) { trendBar.append("●"); }
        for (int i = trend; i < 5; i++) { trendBar.append("○"); }
        tooltip.add(startIndex++,
                Component.translatable("kuvaweapon.warframe_type_riven_trend.tooltip")
                        .append(" ").append(Component.literal(trendBar.toString()).withStyle(ChatFormatting.BOLD))
                        .withStyle(ChatFormatting.DARK_PURPLE));
        int cycle = WarframeRivenModule.getCycle(itemStack);
        if (cycle > 0) {
            tooltip.add(startIndex++,
                    Component.translatable("kuvaweapon.warframe_type_riven_cycle.tooltip")
                            .append(" ").append(Component.literal(String.valueOf(cycle)).withStyle(ChatFormatting.BOLD))
                            .withStyle(ChatFormatting.DARK_PURPLE));
        }
        // ⭐ 在安魂之融中显示洗卡所需赤毒（战甲裂罅公式：min(cycle,8) + trend²）
        startIndex += ModuleLevelTooltipHelper.appendRivenCycleCostTooltipIfInEvolve(
                tooltip, startIndex, trend, cycle, true);
        return startIndex;
    }

    private static ChatFormatting getModuleColor(Item item) {
        if (item instanceof WarframeCommonModule) return ChatFormatting.GOLD;
        if (item instanceof WarframeUncommonModule) return ChatFormatting.AQUA;
        if (item instanceof WarframeRareModule) return ChatFormatting.YELLOW;
        if (item instanceof WarframePrimeModule) return ChatFormatting.WHITE;
        if (item instanceof WarframeRivenModule) return ChatFormatting.LIGHT_PURPLE;
        return ChatFormatting.WHITE;
    }
}
