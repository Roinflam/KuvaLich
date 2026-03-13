package pers.roinflam.kuvalich.base.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.init.KuvaLichItems;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 安魂卡片基类（1.20.1版本，业务逻辑100%不变）
 * Requiem Card Base Class (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public abstract class AbstractRequiemCard extends Item {

    public AbstractRequiemCard(@Nonnull Item.Properties properties) {
        super(properties
                .stacksTo(1)           // 最大堆叠数1 / Max stack size 1
                .durability(3)         // 最大耐久度3 / Max durability 3
        );
    }

    /**
     * 获取指定ID的安魂卡片（业务逻辑100%不变）
     * Get requiem card by ID (business logic 100% unchanged)
     */
    public static Item getCard(int id) {
        // 注意：这里需要从KuvaLichItems获取，需要确保KuvaLichItems已正确定义
        // Note: Need to get from KuvaLichItems, ensure KuvaLichItems is properly defined
        return KuvaLichItems.getRequiemCard(id);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();

        if (item instanceof AbstractRequiemCard) {
            List<Component> tooltip = event.getToolTip();

            // 获取物品的描述ID（1.20.1新API）
            // Get item description ID (1.20.1 new API)
            String descriptionId = item.getDescriptionId();

            tooltip.add(1, Component.translatable(descriptionId + ".first_tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

            tooltip.add(2, Component.translatable(descriptionId + ".second_tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * 根据耐久度返回稀有度（业务逻辑100%不变）
     * Get rarity based on durability (business logic 100% unchanged)
     */
    @Override
    public @Nonnull Rarity getRarity(@Nonnull ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getDamageValue() == 0) {
            return Rarity.EPIC;
        } else if (stack.getDamageValue() == 1) {
            return Rarity.RARE;
        } else if (stack.getDamageValue() == 2) {
            return Rarity.UNCOMMON;
        } else {
            return Rarity.COMMON;
        }
    }

    /**
     * 获取卡片ID（需要在子类中实现）
     * Get card ID (must be implemented in subclass)
     */
    public abstract int getID();
}