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

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 战甲模组基类（1.20.1版本，业务逻辑100%不变）
 * Warframe Module Base Class (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public abstract class WarframeModuleBase extends ModuleBase {

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

    public WarframeModuleBase(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isWarframe() {
        return true;
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        Item item = itemStack.getItem();
        if (!(item instanceof WarframeModuleBase)) {
            return;
        }

        List<Component> tooltip = event.getToolTip();

        if (ModuleBase.isRandom(itemStack)) {
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

        for (Map.Entry<String, Double> attributeTag : ModuleBase.getAttributes(itemStack)) {
            String attributeKey = attributeTag.getKey();
            double value = attributeTag.getValue();

            // ✅ 固定属性显示为固定数值，不显示百分比
            if (attributeKey.equals("fixedHealth") ||
                    attributeKey.equals("fixedShield") ||
                    attributeKey.equals("fixedArmor")) {

                Component attributeName = Component.translatable("kuvaweapon.warframe_attribute_type." + attributeKey);
                ChatFormatting color = getModuleColor(item);

                tooltip.add(number++, Component.literal("+" + (int) value + " ")
                        .append(attributeName)
                        .withStyle(color));
            } else {
                String prefix = value >= 0 ? "+" : "";
                int percentage = (int) (value * 100);
                Component attributeName;

                if (attributeKey.startsWith("killStack")) {
                    int maxStacks = getMaxStacksForAttribute(attributeKey);
                    attributeName = Component.translatable("kuvaweapon.warframe_attribute_type." + attributeKey, maxStacks);
                } else {
                    attributeName = Component.translatable("kuvaweapon.warframe_attribute_type." + attributeKey);
                }

                ChatFormatting color = getModuleColor(item);
                tooltip.add(number++, Component.literal(prefix + percentage + "% ")
                        .append(attributeName)
                        .withStyle(color));
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
        for (int i = 0; i < trend; i++) {
            trendBar.append("●");
        }
        for (int i = trend; i < 5; i++) {
            trendBar.append("○");
        }

        tooltip.add(startIndex++,
                Component.translatable("kuvaweapon.warframe_type_riven_trend.tooltip")
                        .append(" ")
                        .append(Component.literal(trendBar.toString()).withStyle(ChatFormatting.BOLD))
                        .withStyle(ChatFormatting.DARK_PURPLE));

        int cycle = WarframeRivenModule.getCycle(itemStack);
        if (cycle > 0) {
            tooltip.add(startIndex++,
                    Component.translatable("kuvaweapon.warframe_type_riven_cycle.tooltip")
                            .append(" ")
                            .append(Component.literal(String.valueOf(cycle)).withStyle(ChatFormatting.BOLD))
                            .withStyle(ChatFormatting.DARK_PURPLE));
        }

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