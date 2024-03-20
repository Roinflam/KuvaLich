package pers.roinflam.kuvalich.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.util.ItemUtil;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class Kuva extends Item implements IHasModel {

    public Kuva(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        ItemUtil.registerItem(this, name, creativeTabs);

        KuvaLichItems.ITEMS.add(this);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@NotNull ItemTooltipEvent evt) {
        @NotNull ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof Kuva) {
            evt.getToolTip().add(1, TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC + I18n.format(item.getUnlocalizedName() + ".tooltip"));
        }
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
