package pers.roinflam.kuvalich.blocks.table;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.network.KuvaLichGuiHandler;
import pers.roinflam.kuvalich.utils.IHasItem;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.util.BlockUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class RequiemRecast extends Block implements IHasModel, IHasItem {

    @Nullable
    private final ItemBlock itemBlock;

    public RequiemRecast(@Nonnull String name, @Nonnull Material material, @Nonnull CreativeTabs creativeTabs) {
        super(material);

        itemBlock = BlockUtil.registerBlock(this, name, creativeTabs, true);
        KuvaLichItems.ITEMS.add(itemBlock);
        KuvaLichBlocks.BLOCKS.add(this);

        setSoundType(SoundType.STONE);

        setHardness(100);
        setResistance(2000);
        setHarvestLevel("pickaxe", 3);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Nonnull
    public static Block getBlock() {
        return KuvaLichBlocks.REQUIEM_RECAST;
    }

    @Override
    public boolean isFullCube(@Nonnull IBlockState state) {
        return false;
    }

    @Override
    public void addCollisionBoxToList(@Nonnull IBlockState state, @Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull AxisAlignedBB entityBox, @Nonnull List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState) {
        if (!isActualState) {
            state = state.getActualState(worldIn, pos);
        }

        addCollisionBoxToList(pos, entityBox, collidingBoxes, new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.15D, 1D));
    }

    @Override
    public boolean isOpaqueCube(@Nonnull IBlockState state) {
        return false;
    }

    @Override
    public boolean onBlockActivated(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state, @Nonnull EntityPlayer playerIn, @Nonnull EnumHand hand, @Nonnull EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!worldIn.isRemote) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            playerIn.openGui(Reference.MOD_ID, KuvaLichGuiHandler.REQUIEM_RECAST, worldIn, x, y, z);
        }
        return true;
    }

    @Nullable
    @Override
    public ItemBlock getItemBlock() {
        return itemBlock;
    }
}

