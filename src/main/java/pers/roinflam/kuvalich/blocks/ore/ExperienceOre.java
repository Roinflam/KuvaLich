// 文件：ExperienceOre.java - 修正后的版本
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

@Mod.EventBusSubscriber
public class ExperienceOre extends Block implements IHasModel, IHasItem {
    // 常量定义
    private static final float HARDNESS = 3.0f;
    private static final float RESISTANCE = 3.0f;
    private static final int HARVEST_LEVEL = 1; // 石镐
    private static final int MIN_EXP = 5;
    private static final int MAX_EXP = 20;

    private final ItemBlock itemBlock;

    public ExperienceOre(String name, Material material, CreativeTabs creativeTabs) {
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
                "经验矿石注册完成 - 硬度:%.1f, 抗性:%.1f, 需要:石镐",
                HARDNESS, RESISTANCE
        ));
    }

    public static Block getBlock() {
        return KuvaLichBlocks.EXPERIENCE_ORE;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        // 不掉落任何物品，仅掉落经验
        if (world != null && pos != null) {
            LogUtil.debug("经验矿石被破坏 - 位置: " + pos + ", 时运等级: " + fortune);
        }
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        int baseExp = RandomUtil.getInt(MIN_EXP, MAX_EXP);
        int totalExp = baseExp + fortune;

        if (pos != null) {
            // 修正：将多个参数合并为一个字符串
            String details = String.format(
                    "基础经验: %d (范围: %d-%d), 时运等级: %d, 总经验: %d",
                    baseExp, MIN_EXP, MAX_EXP, fortune, totalExp
            );
            LogUtil.debugEvent("经验矿石经验掉落", "位置: " + pos, details);
        }

        return totalExp;
    }

    @Override
    public ItemBlock getItemBlock() {
        return itemBlock;
    }
}