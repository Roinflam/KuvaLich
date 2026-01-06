// 文件：WarframeModuleBase.java
// 路径：src/main/java/pers/roinflam/kuvalich/base/item/WarframeModuleBase.java
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

import pers.roinflam.kuvalich.item.module.warframe.*;

import javax.annotation.Nonnull;
import java.util.*;

@Mod.EventBusSubscriber
public abstract class WarframeModuleBase extends ModuleBase {
    // 使用不可变Set提升性能
    public static final Set<String> WARFRAME_ATTRIBUTE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "health", "shield", "armor", "sprintSpeed", "shieldRecoveryRate",
                    "shieldRecoveryDelay", "knockbackResistance", "fireProtection",
                    "electricProtection", "homologousProtection", "reachDistance",
                    "diggingSpeed", "responseRate", "itemDropMultiplier"
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

        // Warframe Riven模组特殊处理
        if (item instanceof WarframeRivenModule) {
            number = addRivenTooltips(evt, itemStack, number);
        }

        // 添加属性列表
        for (Map.Entry<String, Double> attributeTag : ModuleBase.getAttributes(itemStack)) {
            String prefix = attributeTag.getValue() >= 0 ? "+" : "";
            int percentage = (int) (attributeTag.getValue() * 100);
            String attributeName = I18n.format("kuvaweapon.warframe_attribute_type." + attributeTag.getKey());

            TextFormatting color = getModuleColor(item);
            evt.getToolTip().add(number++, color + prefix + percentage + "% " + attributeName);
        }

        evt.getToolTip().add(number, TextFormatting.WHITE + I18n.format("kuvaweapon.warframe_type.tooltip"));
    }

    /**
     * 添加Riven模组提示
     */
    private static int addRivenTooltips(ItemTooltipEvent evt, ItemStack itemStack, int startIndex) {
        int trend = WarframeRivenModule.getTrend(itemStack);

        // 优化：使用StringBuilder
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