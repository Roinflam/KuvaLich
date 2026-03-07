// Forma.java
// forge/src/main/java/pers/roinflam/kuvalich/item/Forma.java
package pers.roinflam.kuvalich.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 塑形块（Forma）
 * 用于在安魂之融中重新随机已开光武器的基础面板属性
 * 堆叠上限16，史诗品质
 *
 * Forma - Used in Requiem Forge to re-roll base attributes
 * of opened (光-enchanted) weapons. Stack size 16, EPIC rarity.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class Forma extends Item {

    /**
     * 构造函数
     * 注意：注册时需手动设置 stacksTo(16)
     *
     * @param properties 物品属性（由注册处提供）
     */
    public Forma(@Nonnull Item.Properties properties) {
        super(properties.stacksTo(16));
    }

    /**
     * 客户端Tooltip事件监听
     * 在物品名称下方添加灰色斜体描述文本
     *
     * @param event 物品Tooltip事件
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();

        if (item instanceof Forma) {
            List<Component> tooltip = event.getToolTip();
            tooltip.add(1, Component.translatable(item.getDescriptionId() + ".tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * 返回物品稀有度为史诗（紫色）
     *
     * @param stack 物品堆
     * @return 史诗稀有度
     */
    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        return Rarity.EPIC;
    }
}