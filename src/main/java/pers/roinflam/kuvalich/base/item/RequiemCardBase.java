package pers.roinflam.kuvalich.base.item;

import net.minecraft.client.resources.I18n;
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
import pers.roinflam.kuvalich.tabs.KuvaLichTab;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.util.ItemUtil;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public abstract class RequiemCardBase extends Item implements IHasModel {

    public RequiemCardBase(@Nonnull String name) {
        ItemUtil.registerItem(this, name, KuvaLichTab.getTab());

        setMaxStackSize(1);

        KuvaLichItems.ITEMS.add(this);
        this.setMaxDamage(3);
    }

    public static @NotNull Item getCard(int id) {
        RequiemCardBase @NotNull [] requiemCard = new RequiemCardBase[]{KuvaLichItems.KHRA_CARD, KuvaLichItems.JAHU_CARD, KuvaLichItems.FASS_CARD, KuvaLichItems.LOHK_CARD, KuvaLichItems.NETRA_CARD, KuvaLichItems.VOME_CARD, KuvaLichItems.XATA_CARD, KuvaLichItems.RIS_CARD};
        return requiemCard[id];
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@NotNull ItemTooltipEvent evt) {
        @NotNull ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof RequiemCardBase) {
            evt.getToolTip().add(1, TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC + I18n.format(item.getUnlocalizedName() + ".first_tooltip"));
            evt.getToolTip().add(2, TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC + I18n.format(item.getUnlocalizedName() + ".second_tooltip"));
        }
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        if (!stack.isItemStackDamageable() || stack.getItemDamage() == 0) {
            return EnumRarity.EPIC;
        } else if (stack.getItemDamage() == 1) {
            return EnumRarity.RARE;
        } else if (stack.getItemDamage() == 2) {
            return EnumRarity.UNCOMMON;
        } else {
            return EnumRarity.COMMON;
        }
    }

    public abstract int getID();

}
