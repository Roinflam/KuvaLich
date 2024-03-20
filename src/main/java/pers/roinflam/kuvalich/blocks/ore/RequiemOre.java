package pers.roinflam.kuvalich.blocks.ore;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.IHasItem;
import pers.roinflam.kuvalich.utils.IHasModel;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.BlockUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class RequiemOre extends Block implements IHasModel, IHasItem {
    @Nullable
    private final ItemBlock itemBlock;

    public RequiemOre(@Nonnull String name, @Nonnull Material material, @Nonnull CreativeTabs creativeTabs) {
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
        return KuvaLichBlocks.REQUIEM_ORE;
    }

    @Override
    public void getDrops(@Nonnull NonNullList<ItemStack> drops, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState state, int fortune) {

    }

    @Override
    public int getExpDrop(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, int fortune) {
        return RandomUtil.getInt(50, 100);
    }

    @Nullable
    @Override
    public ItemBlock getItemBlock() {
        return itemBlock;
    }


}

