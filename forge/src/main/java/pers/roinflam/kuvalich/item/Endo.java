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
 * 内融核心（Endo）
 * 用于在安魂之融中升级模组等级。
 * 获取途径：使用开光武器击杀Monster时有概率掉落（受战利品倍率模组影响）。
 * 堆叠上限64，稀有品质。
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class Endo extends Item {

    public Endo(@Nonnull Properties properties) {
        super(properties.stacksTo(64));
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack itemStack = event.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof Endo) {
            List<Component> tooltip = event.getToolTip();
            tooltip.add(1, Component.translatable(item.getDescriptionId() + ".tooltip")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    @Override
    public @NotNull Rarity getRarity(@NotNull ItemStack stack) {
        return Rarity.RARE;
    }
}
