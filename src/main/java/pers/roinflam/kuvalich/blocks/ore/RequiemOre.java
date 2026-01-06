// 文件：RequiemOre.java
// 路径：src/main/java/pers/roinflam/kuvalich/blocks/ore/RequiemOre.java
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
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.BlockUtil;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class RequiemOre extends Block implements IHasModel, IHasItem {
    // 常量定义
    private static final float HARDNESS = 100.0f;
    private static final float RESISTANCE = 2000.0f;
    private static final int HARVEST_LEVEL = 3; // 钻石镐
    private static final int MIN_EXP = 50;
    private static final int MAX_EXP = 100;

    private final ItemBlock itemBlock;

    public RequiemOre(String name, Material material, CreativeTabs creativeTabs) {
        super(material);

        // 注册方块和物品
        itemBlock = BlockUtil.registerBlock(this, name, creativeTabs, true);
        KuvaLichItems.ITEMS.add(itemBlock);
        KuvaLichBlocks.BLOCKS.add(this);

        // 设置方块属性
        setSoundType(SoundType.STONE);
        setHardness(HARDNESS);
        setResistance(RESISTANCE);
        setHarvestLevel("pickaxe", HARVEST_LEVEL);

        // 注册事件总线
        MinecraftForge.EVENT_BUS.register(this);

        LogUtil.debug(String.format(
                "安魂矿石注册完成 - 硬度:%.1f, 抗性:%.1f, 需要:钻石镐",
                HARDNESS, RESISTANCE
        ));
    }

    public static Block getBlock() {
        return KuvaLichBlocks.REQUIEM_ORE;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        // 不掉落任何物品，仅掉落经验
        if (world != null && pos != null) {
            LogUtil.debug("安魂矿石被破坏 - 位置: " + pos + ", 时运等级: " + fortune);
        }
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        int exp = RandomUtil.getInt(MIN_EXP, MAX_EXP);

        if (pos != null) {
            LogUtil.debugEvent("安魂矿石经验掉落",
                    "位置: " + pos,
                    "经验值: " + exp + " (范围: " + MIN_EXP + "-" + MAX_EXP + ")"
            );
        }

        return exp;
    }

    @Override
    public ItemBlock getItemBlock() {
        return itemBlock;
    }
}