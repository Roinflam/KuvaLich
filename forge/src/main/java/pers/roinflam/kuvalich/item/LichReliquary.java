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
 * 玄骸之遗（1.20.1版本，业务逻辑100%不变）
 * Lich Reliquary (1.20.1 version, business logic 100% unchanged)
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class LichReliquary extends Item {

    public LichReliquary(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();

        if (item instanceof LichReliquary) {
            List<Component> tooltip = event.getToolTip();
            tooltip.add(1, Component.translatable(item.getDescriptionId() + ".tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        return Rarity.EPIC;
    }
}