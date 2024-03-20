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
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.item.module.warframe.*;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber
public abstract class WarframeModuleBase extends ModuleBase {
    public final static List<String> warframeAttributeType = new ArrayList<String>(Arrays.asList("health", "shield", "armor", "sprintSpeed", "shieldRecoveryRate", "shieldRecoveryDelay", "knockbackResistance", "fireProtection", "electricProtection", "homologousProtection", "reachDistance", "diggingSpeed", "responseRate", "itemDropMultiplier"));

    public WarframeModuleBase(@Nonnull String name) {
        super(name);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@NotNull ItemTooltipEvent evt) {
        @NotNull ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof WarframeModuleBase) {
            if (ModuleBase.isRandom(itemStack)) {
                for (int i = 1; i < 4; i++) {
                    evt.getToolTip().add(i, TextFormatting.GRAY + I18n.format("kuvaweapon.warframe_type_random.tooltip"));
                }
            } else {
                int number = 1;
                if (item instanceof WarframeRivenModule) {
                    int trend = WarframeRivenModule.getTrend(itemStack);
                    String str = "";
                    for (int i = 0; i < trend; i++) {
                        str += "¡ñ";
                    }
                    for (int i = trend; i < 5; i++) {
                        str += "¡ð";
                    }
                    evt.getToolTip().add(number++, TextFormatting.DARK_PURPLE + I18n.format("kuvaweapon.warframe_type_riven_trend.tooltip") + " " + TextFormatting.BOLD + str);
                    if (WarframeRivenModule.getCycle(itemStack) > 0) {
                        evt.getToolTip().add(number++, TextFormatting.DARK_PURPLE + I18n.format("kuvaweapon.warframe_type_riven_cycle.tooltip") + " " + TextFormatting.BOLD + WarframeRivenModule.getCycle(itemStack));
                    }
                }
                for (Map.Entry<String, Double> attributeTag : ModuleBase.getAttributes(itemStack)) {
                    String string = "";
                    if (attributeTag.getValue() >= 0) {
                        string = "+";
                    } else {
                        string = "";
                    }
                    if (item instanceof WarframeCommonModule) {
                        evt.getToolTip().add(number++, TextFormatting.GOLD + string + (int) (attributeTag.getValue() * 100) + "% " + I18n.format("kuvaweapon.warframe_attribute_type." + attributeTag.getKey()));
                    } else if (item instanceof WarframeUncommonModule) {
                        evt.getToolTip().add(number++, TextFormatting.AQUA + string + (int) (attributeTag.getValue() * 100) + "% " + I18n.format("kuvaweapon.warframe_attribute_type." + attributeTag.getKey()));
                    } else if (item instanceof WarframeRareModule) {
                        evt.getToolTip().add(number++, TextFormatting.YELLOW + string + (int) (attributeTag.getValue() * 100) + "% " + I18n.format("kuvaweapon.warframe_attribute_type." + attributeTag.getKey()));
                    } else if (item instanceof WarframePrimeModule) {
                        evt.getToolTip().add(number++, TextFormatting.WHITE + string + (int) (attributeTag.getValue() * 100) + "% " + I18n.format("kuvaweapon.warframe_attribute_type." + attributeTag.getKey()));
                    } else if (item instanceof WarframeRivenModule) {
                        evt.getToolTip().add(number++, TextFormatting.LIGHT_PURPLE + string + (int) (attributeTag.getValue() * 100) + "% " + I18n.format("kuvaweapon.warframe_attribute_type." + attributeTag.getKey()));
                    }
                }
                evt.getToolTip().add(number, TextFormatting.WHITE + I18n.format("kuvaweapon.warframe_type.tooltip"));
            }
        }
    }
}