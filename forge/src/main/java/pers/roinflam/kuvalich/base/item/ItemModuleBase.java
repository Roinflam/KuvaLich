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

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 武器模组基类（1.20.1版本，业务逻辑100%不变）
 * Item Module Base Class (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
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

    public ItemModuleBase(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isWarframe() {
        return false;
    }

    /**
     * 物品提示事件处理（1.20.1新API）
     * Item tooltip event handler (1.20.1 new API)
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        Item item = itemStack.getItem();
        if (!(item instanceof ItemModuleBase)) {
            return;
        }

        List<Component> tooltip = event.getToolTip();

        if (ModuleBase.isRandom(itemStack)) {
            for (int i = 1; i < 4 && i < tooltip.size(); i++) {
                tooltip.add(i, Component.translatable("kuvaweapon.item_type_random.tooltip")
                        .withStyle(ChatFormatting.GRAY));
            }
        } else {
            addAttributeTooltips(tooltip, itemStack, item);
        }
    }

    /**
     * 添加属性提示（业务逻辑100%不变）
     * Add attribute tooltips (business logic 100% unchanged)
     */
    private static void addAttributeTooltips(List<Component> tooltip, ItemStack itemStack, Item item) {
        int number = 1;

        if (item instanceof ItemRivenModule) {
            number = addRivenTooltips(tooltip, itemStack, number);
        }

        for (Map.Entry<String, Double> attributeTag : ModuleBase.getAttributes(itemStack)) {
            String prefix = attributeTag.getValue() >= 0 ? "+" : "";
            int percentage = (int) (attributeTag.getValue() * 100);
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

    /**
     * 获取击杀叠加词条的最大层数（业务逻辑100%不变）
     * Get max stacks for kill stack attributes (business logic 100% unchanged)
     */
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

    /**
     * 添加Riven模组提示（业务逻辑100%不变）
     * Add Riven module tooltips (business logic 100% unchanged)
     */
    private static int addRivenTooltips(List<Component> tooltip, ItemStack itemStack, int startIndex) {
        int trend = ItemRivenModule.getTrend(itemStack);

        StringBuilder trendBar = new StringBuilder(15);
        for (int i = 0; i < trend; i++) {
            trendBar.append("●");
        }
        for (int i = trend; i < 5; i++) {
            trendBar.append("○");
        }

        tooltip.add(startIndex++,
                Component.translatable("kuvaweapon.item_type_riven_trend.tooltip")
                        .append(" ")
                        .append(Component.literal(trendBar.toString()).withStyle(ChatFormatting.BOLD))
                        .withStyle(ChatFormatting.DARK_PURPLE));

        int cycle = ItemRivenModule.getCycle(itemStack);
        if (cycle > 0) {
            tooltip.add(startIndex++,
                    Component.translatable("kuvaweapon.item_type_riven_cycle.tooltip")
                            .append(" ")
                            .append(Component.literal(String.valueOf(cycle)).withStyle(ChatFormatting.BOLD))
                            .withStyle(ChatFormatting.DARK_PURPLE));
        }

        return startIndex;
    }

    /**
     * 获取模组颜色（业务逻辑100%不变）
     * Get module color (business logic 100% unchanged)
     */
    private static ChatFormatting getModuleColor(Item item) {
        if (item instanceof ItemCommonModule) return ChatFormatting.GOLD;
        if (item instanceof ItemUncommonModule) return ChatFormatting.AQUA;
        if (item instanceof ItemRareModule) return ChatFormatting.YELLOW;
        if (item instanceof ItemPrimeModule) return ChatFormatting.WHITE;
        if (item instanceof ItemRivenModule) return ChatFormatting.LIGHT_PURPLE;
        return ChatFormatting.WHITE;
    }
}