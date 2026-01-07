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
                    "killStackBurstingRadius", "killStackFiringRate"
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

    private static void addAttributeTooltips(ItemTooltipEvent evt, ItemStack itemStack, Item item) {
        int number = 1;

        if (item instanceof ItemRivenModule) {
            number = addRivenTooltips(evt, itemStack, number);
        }

        for (Map.Entry<String, Double> attributeTag : ModuleBase.getAttributes(itemStack)) {
            String prefix = attributeTag.getValue() >= 0 ? "+" : "";
            int percentage = (int) (attributeTag.getValue() * 100);
            String attributeKey = attributeTag.getKey();

            String attributeName;
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

    private static int getMaxStacksForAttribute(String attributeKey) {
        switch (attributeKey) {
            case "killStackBaseDamage": return ModConfig.KUVA_LICH.maxStacksBaseDamage;
            case "killStackMultishot": return ModConfig.KUVA_LICH.maxStacksMultishot;
            case "killStackMeleeCriticalMultiplier": return ModConfig.KUVA_LICH.maxStacksMeleeCritMult;
            case "killStackTriggerChance": return ModConfig.KUVA_LICH.maxStacksTriggerChance;
            case "killStackAttackRange": return ModConfig.KUVA_LICH.maxStacksAttackRange;
            case "killStackAttackSpeed": return ModConfig.KUVA_LICH.maxStacksAttackSpeed;
            case "killStackBurstingRadius": return ModConfig.KUVA_LICH.maxStacksBurstingRadius;
            case "killStackFiringRate": return ModConfig.KUVA_LICH.maxStacksFiringRate;
            default: return 0;
        }
    }

    private static int addRivenTooltips(ItemTooltipEvent evt, ItemStack itemStack, int startIndex) {
        int trend = ItemRivenModule.getTrend(itemStack);

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

    private static TextFormatting getModuleColor(Item item) {
        if (item instanceof ItemCommonModule) return TextFormatting.GOLD;
        if (item instanceof ItemUncommonModule) return TextFormatting.AQUA;
        if (item instanceof ItemRareModule) return TextFormatting.YELLOW;
        if (item instanceof ItemPrimeModule) return TextFormatting.WHITE;
        if (item instanceof ItemRivenModule) return TextFormatting.LIGHT_PURPLE;
        return TextFormatting.WHITE;
    }
}