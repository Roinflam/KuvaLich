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
import pers.roinflam.kuvalich.item.module.warframe.*;

import javax.annotation.Nonnull;
import java.util.*;

@Mod.EventBusSubscriber
public abstract class WarframeModuleBase extends ModuleBase {

    public static final Set<String> WARFRAME_ATTRIBUTE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "health", "shield", "armor", "sprintSpeed", "shieldRecoveryRate",
                    "shieldRecoveryDelay", "knockbackResistance", "fireProtection",
                    "electricProtection", "homologousProtection", "reachDistance",
                    "diggingSpeed", "responseRate", "itemDropMultiplier",
                    "jumpBoost", "fallProtection",

                    // 战甲击杀叠加词条（执刑官系列）
                    "killStackHealth",
                    "killStackShield",
                    "killStackArmor",
                    "killStackSprintSpeed",
                    "killStackShieldRecoveryRate",
                    "killStackShieldRecoveryDelay",
                    "killStackFireProtection",
                    "killStackElectricProtection",
                    "killStackHomologousProtection",
                    "killStackResponseRate",
                    "killStackItemDropMultiplier",
                    "killStackDiggingSpeed"
            ))
    );

    public WarframeModuleBase(@Nonnull String name) {
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
        if (!(item instanceof WarframeModuleBase)) {
            return;
        }

        if (ModuleBase.isRandom(itemStack)) {
            for (int i = 1; i < 4; i++) {
                evt.getToolTip().add(i, TextFormatting.GRAY + I18n.format("kuvaweapon.warframe_type_random.tooltip"));
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

        if (item instanceof WarframeRivenModule) {
            number = addRivenTooltips(evt, itemStack, number);
        }

        for (Map.Entry<String, Double> attributeTag : ModuleBase.getAttributes(itemStack)) {
            String prefix = attributeTag.getValue() >= 0 ? "+" : "";
            int percentage = (int) (attributeTag.getValue() * 100);
            String attributeKey = attributeTag.getKey();

            String attributeName;
            if (attributeKey.startsWith("killStack")) {
                int maxStacks = getMaxStacksForAttribute(attributeKey);
                attributeName = I18n.format("kuvaweapon.warframe_attribute_type." + attributeKey, maxStacks);
            } else {
                attributeName = I18n.format("kuvaweapon.warframe_attribute_type." + attributeKey);
            }

            TextFormatting color = getModuleColor(item);
            evt.getToolTip().add(number++, color + prefix + percentage + "% " + attributeName);
        }

        evt.getToolTip().add(number, TextFormatting.WHITE + I18n.format("kuvaweapon.warframe_type.tooltip"));
    }

    /**
     * 获取击杀叠加词条的最大层数（从配置读取）
     */
    private static int getMaxStacksForAttribute(String attributeKey) {
        switch (attributeKey) {
            case "killStackHealth":
                return ModConfig.KUVA_LICH.maxStacksHealth;
            case "killStackShield":
                return ModConfig.KUVA_LICH.maxStacksShield;
            case "killStackArmor":
                return ModConfig.KUVA_LICH.maxStacksArmor;
            case "killStackSprintSpeed":
                return ModConfig.KUVA_LICH.maxStacksSprintSpeed;
            case "killStackShieldRecoveryRate":
                return ModConfig.KUVA_LICH.maxStacksShieldRecoveryRate;
            case "killStackShieldRecoveryDelay":
                return ModConfig.KUVA_LICH.maxStacksShieldRecoveryDelay;
            case "killStackFireProtection":
                return ModConfig.KUVA_LICH.maxStacksFireProtection;
            case "killStackElectricProtection":
                return ModConfig.KUVA_LICH.maxStacksElectricProtection;
            case "killStackHomologousProtection":
                return ModConfig.KUVA_LICH.maxStacksHomologousProtection;
            case "killStackResponseRate":
                return ModConfig.KUVA_LICH.maxStacksResponseRate;
            case "killStackItemDropMultiplier":
                return ModConfig.KUVA_LICH.maxStacksItemDropMultiplier;
            case "killStackDiggingSpeed":
                return ModConfig.KUVA_LICH.maxStacksDiggingSpeed;
            default:
                return 0;
        }
    }

    /**
     * 添加Riven模组提示
     */
    private static int addRivenTooltips(ItemTooltipEvent evt, ItemStack itemStack, int startIndex) {
        int trend = WarframeRivenModule.getTrend(itemStack);

        StringBuilder trendBar = new StringBuilder(5);
        for (int i = 0; i < trend; i++) {
            trendBar.append("●");
        }
        for (int i = trend; i < 5; i++) {
            trendBar.append("○");
        }

        evt.getToolTip().add(startIndex++,
                TextFormatting.DARK_PURPLE + I18n.format("kuvaweapon.warframe_type_riven_trend.tooltip")
                        + " " + TextFormatting.BOLD + trendBar);

        int cycle = WarframeRivenModule.getCycle(itemStack);
        if (cycle > 0) {
            evt.getToolTip().add(startIndex++,
                    TextFormatting.DARK_PURPLE + I18n.format("kuvaweapon.warframe_type_riven_cycle.tooltip")
                            + " " + TextFormatting.BOLD + cycle);
        }

        return startIndex;
    }

    /**
     * 获取模组颜色
     */
    private static TextFormatting getModuleColor(Item item) {
        if (item instanceof WarframeCommonModule) return TextFormatting.GOLD;
        if (item instanceof WarframeUncommonModule) return TextFormatting.AQUA;
        if (item instanceof WarframeRareModule) return TextFormatting.YELLOW;
        if (item instanceof WarframePrimeModule) return TextFormatting.WHITE;
        if (item instanceof WarframeRivenModule) return TextFormatting.LIGHT_PURPLE;
        return TextFormatting.WHITE;
    }
}