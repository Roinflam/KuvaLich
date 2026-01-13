package pers.roinflam.kuvalich.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 安魂谜语（1.20.1版本，业务逻辑100%不变）
 * Requiem Riddle (1.20.1 version, business logic 100% unchanged)
 *
 * 使用后可直接查看谜语答案
 * Use to directly view riddle answers
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class RequiemRiddle extends Item {

    public RequiemRiddle(@Nonnull Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();

        if (item instanceof RequiemRiddle) {
            List<Component> tooltip = event.getToolTip();
            tooltip.add(1, Component.translatable(item.getDescriptionId() + ".tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * 获取卡片名称组件（业务逻辑100%不变）
     * Get card name component (business logic 100% unchanged)
     */
    private Component getCardName(int id) {
        return Component.translatable(RequiemCardBase.getCard(id).getDescriptionId());
    }

    /**
     * 右键使用物品（1.20.1新API）
     * Use item on right click (1.20.1 new API)
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                if (requiemCard.getOneAnswer() == -1 ||
                        requiemCard.getTwoAnswer() == -1 ||
                        requiemCard.getThreeAnswer() == -1) {

                    // 尚未解密完成 / Not yet fully decrypted
                    player.sendSystemMessage(Component.translatable(
                            "message.kuvalich.requiemRiddleNotAnswer",
                            requiemCard.getKuvaLevel()
                    ).withStyle(ChatFormatting.RED));

                    player.getCooldowns().addCooldown(itemstack.getItem(), 200);
                } else {
                    // 显示答案 / Show answers
                    player.sendSystemMessage(Component.translatable(
                            "message.kuvalich.requiemRiddleAnswer",
                            getCardName(requiemCard.getOneAnswer()),
                            getCardName(requiemCard.getTwoAnswer()),
                            getCardName(requiemCard.getThreeAnswer())
                    ).withStyle(ChatFormatting.GOLD));

                    itemstack.shrink(1);
                }
            });
        }

        return InteractionResultHolder.success(itemstack);
    }

    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        return Rarity.EPIC;
    }
}