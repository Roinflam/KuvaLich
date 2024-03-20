package pers.roinflam.kuvalich.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.blocks.capability.RequiemCard;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.util.ItemUtil;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class RequiemRiddle extends Item implements IHasModel {

    public RequiemRiddle(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        ItemUtil.registerItem(this, name, creativeTabs);

        setMaxStackSize(1);

        KuvaLichItems.ITEMS.add(this);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(@NotNull ItemTooltipEvent evt) {
        @NotNull ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof RequiemRiddle) {
            evt.getToolTip().add(1, TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC + I18n.format(item.getUnlocalizedName() + ".tooltip"));
        }
    }

    private TextComponentTranslation getCardName(int id) {
        return new TextComponentTranslation(RequiemCardBase.getCard(id).getUnlocalizedName() + ".name");
    }

    @Override
    public @NotNull ActionResult<ItemStack> onItemRightClick(World worldIn, @NotNull EntityPlayer playerIn, EnumHand handIn) {
        @NotNull ItemStack itemstack = playerIn.getHeldItem(handIn);
        if (!worldIn.isRemote) {
            @org.jetbrains.annotations.Nullable RequiemCard requiemCard = playerIn.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);

            if (requiemCard.getOneAnswer() == -1 || requiemCard.getTwoAnswer() == -1 || requiemCard.getThreeAnswer() == -1) {
                @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.requiemRiddleNotAnswer", requiemCard.getKuvaLevel());
                textComponentString.getStyle().setColor(TextFormatting.RED);
                playerIn.sendMessage(textComponentString);

                playerIn.resetCooldown();
                playerIn.getCooldownTracker().setCooldown(itemstack.getItem(), 200);
            } else {
                @NotNull TextComponentTranslation textComponentString = new TextComponentTranslation("message.kuvalich.requiemRiddleAnswer", getCardName(requiemCard.getOneAnswer()), getCardName(requiemCard.getTwoAnswer()), getCardName(requiemCard.getThreeAnswer()));
                textComponentString.getStyle().setColor(TextFormatting.GOLD);
                playerIn.sendMessage(textComponentString);

                itemstack.shrink(1);
            }
        }
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
