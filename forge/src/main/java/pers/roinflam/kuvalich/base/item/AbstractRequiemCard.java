package pers.roinflam.kuvalich.base.item;

import net.minecraft.network.chat.Component;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import net.minecraft.network.chat.TextColor;
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
 * 安魂卡片基类
 * Requiem Card Base Class
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
     * 获取指定ID的安魂卡片
     * Get requiem card by ID
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
                    .withStyle(style -> style.withColor(TextColor.fromRgb(KuvaPalette.MUTED)).withItalic(true)));

            tooltip.add(2, Component.translatable(descriptionId + ".second_tooltip")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(KuvaPalette.MUTED)).withItalic(true)));
        }
    }

    /**
     * 根据耐久度返回稀有度
     * Get rarity based on durability
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
     * 这几张卡暂时不画物品自带的耐久条：界面已经在格子旁边另画了剩余次数时，自带的那条是重复的。
     * 按对象比较，所以同一个界面里背包那几格的卡照常有条
     */
    private static volatile ItemStack[] barHidden = new ItemStack[0];

    /** 画格子前传入要藏条的卡，画完不带参数再调一次还原 */
    public static void hideBarFor(ItemStack... stacks) {
        barHidden = stacks;
    }

    @Override
    public boolean isBarVisible(@Nonnull ItemStack stack) {
        for (ItemStack hidden : barHidden) {
            if (hidden == stack) {
                return false;
            }
        }
        return super.isBarVisible(stack);
    }

    /**
     * 获取卡片ID（需要在子类中实现）
     * Get card ID (must be implemented in subclass)
     */
    public abstract int getID();
}