package pers.roinflam.kuvalich.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import pers.roinflam.kuvalich.entity.EntityKuvaMaster;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.util.ItemUtil;

import javax.annotation.Nonnull;

/**
 * 安魂通牒
 *
 * 使用后可召唤赤毒玄骸
 * 成功破解赤毒玄骸后有概率获得
 */
@Mod.EventBusSubscriber
public class RequiemUltimatum extends Item implements IHasModel {

    public RequiemUltimatum(@Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        ItemUtil.registerItem(this, name, creativeTabs);
        setMaxStackSize(1);
        KuvaLichItems.ITEMS.add(this);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent evt) {
        ItemStack itemStack = evt.getItemStack();
        Item item = itemStack.getItem();
        if (item instanceof RequiemUltimatum) {
            evt.getToolTip().add(1, TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC +
                    I18n.format(item.getUnlocalizedName() + ".tooltip"));
        }
    }

    @Nonnull
    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, @Nonnull EnumHand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);

        if (!worldIn.isRemote) {
            // 在玩家面前生成赤毒玄骸
            BlockPos spawnPos = playerIn.getPosition().offset(playerIn.getHorizontalFacing(), 3);
            EntityKuvaMaster kuvaMaster = new EntityKuvaMaster(worldIn);
            kuvaMaster.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            worldIn.spawnEntity(kuvaMaster);

            if (!playerIn.capabilities.isCreativeMode) {
                itemstack.shrink(1);
            }
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, itemstack);
    }

    @Nonnull
    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand,
                                      EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (worldIn.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        ItemStack itemstack = player.getHeldItem(hand);

        // 在点击的方块上方生成赤毒玄骸
        BlockPos spawnPos = pos.offset(facing);
        EntityKuvaMaster kuvaMaster = new EntityKuvaMaster(worldIn);
        kuvaMaster.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        worldIn.spawnEntity(kuvaMaster);

        if (!player.capabilities.isCreativeMode) {
            itemstack.shrink(1);
        }

        return EnumActionResult.SUCCESS;
    }

    @Nonnull
    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }
}