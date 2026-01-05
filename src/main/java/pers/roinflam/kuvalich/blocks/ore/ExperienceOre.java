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

/**
 * 经验矿石方块类
 *
 * 特性：
 * - 中等硬度（3）中等爆炸抗性（3）
 * - 需要石镐（等级1）挖掘
 * - 不掉落物品，仅掉落经验
 * - 经验掉落：5-20点 + 时运等级
 *
 * 生成：
 * - 维度：主世界
 * - Y轴：1-128
 * - 矿脉大小：2-4个方块
 * - 每区块：2-8个矿脉
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber
public class ExperienceOre extends Block implements IHasModel, IHasItem {
    private final ItemBlock itemBlock;

    /**
     * 构造经验矿石
     *
     * @param name 注册名
     * @param material 材质
     * @param creativeTabs 创造模式标签页
     */
    public ExperienceOre(String name, Material material, CreativeTabs creativeTabs) {
        super(material);

        // 注册方块和物品
        itemBlock = BlockUtil.registerBlock(this, name, creativeTabs, true);
        KuvaLichItems.ITEMS.add(itemBlock);
        KuvaLichBlocks.BLOCKS.add(this);

        // 设置方块属性
        setSoundType(SoundType.STONE);
        setHardness(3);
        setResistance(3);
        setHarvestLevel("pickaxe", 1);

        // 注册事件总线
        MinecraftForge.EVENT_BUS.register(this);

        LogUtil.debug("经验矿石注册完成 - 硬度:3, 抗性:3, 需要:石镐");
    }

    /**
     * 获取方块实例
     *
     * @return 经验矿石方块
     */
    public static Block getBlock() {
        return KuvaLichBlocks.EXPERIENCE_ORE;
    }

    /**
     * 获取掉落物
     *
     * 经验矿石不掉落任何物品，仅掉落经验
     *
     * @param drops 掉落物列表
     * @param world 世界
     * @param pos 方块位置
     * @param state 方块状态
     * @param fortune 时运等级
     */
    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        // 不掉落任何物品
        LogUtil.debug("经验矿石被破坏 - 位置: " + pos.toString() + ", 时运等级: " + fortune);
    }

    /**
     * 获取经验掉落
     *
     * 经验值：5-20点（随机）+ 时运等级
     * 时运会增加经验掉落
     *
     * @param state 方块状态
     * @param world 世界
     * @param pos 方块位置
     * @param fortune 时运等级
     * @return 经验值
     */
    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        int exp = RandomUtil.getInt(5, 20) + fortune;
        LogUtil.debugEvent("经验矿石经验掉落", "位置: " + pos.toString(),
                "基础经验: 5-20, 时运等级: " + fortune + ", 总经验: " + exp);
        return exp;
    }

    @Override
    public ItemBlock getItemBlock() {
        return itemBlock;
    }
}