package pers.roinflam.kuvalich.init;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.kuvalich.block.ore.ExperienceOre;
import pers.roinflam.kuvalich.block.ore.RequiemOre;
import pers.roinflam.kuvalich.block.table.*;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 方块注册类（硬度抗性与原代码100%一致）
 * Block registration class (hardness/resistance 100% identical to original)
 */
public class KuvaLichBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Reference.MOD_ID);

    // ========== 矿石 / Ores ==========

    /**
     * 安魂矿石
     * Requiem Ore
     *
     * 原代码属性（100%保持一致）：
     * Original properties (100% unchanged):
     * - 硬度 / Hardness: 100.0F
     * - 抗性 / Resistance: 2000.0F
     * - 需要工具 / Required tool: 钻石镐 / Diamond Pickaxe (level 3)
     * - 经验 / Experience: 50-100
     */
    public static final RegistryObject<Block> REQUIEM_ORE = BLOCKS.register("requiem_ore",
            () -> new RequiemOre(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(100.0F, 2000.0F)  // ✅ 硬度100, 抗性2000
                    .sound(SoundType.STONE)));

    /**
     * 经验矿石
     * Experience Ore
     *
     * 原代码属性（100%保持一致）：
     * Original properties (100% unchanged):
     * - 硬度 / Hardness: 3.0F
     * - 抗性 / Resistance: 3.0F
     * - 需要工具 / Required tool: 石镐 / Stone Pickaxe (level 1)
     * - 经验 / Experience: 5-20 + 时运等级 / + fortune level
     */
    public static final RegistryObject<Block> EXPERIENCE_ORE = BLOCKS.register("experience_ore",
            () -> new ExperienceOre(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 3.0F)  // ✅ 硬度3, 抗性3
                    .sound(SoundType.STONE)));

    // ========== 功能方块 / Functional Blocks ==========

    /**
     * 灭骸之扉
     * Requiem Gate
     *
     * 原代码属性（100%保持一致）：
     * Original properties (100% unchanged):
     * - 硬度 / Hardness: 100.0F
     * - 抗性 / Resistance: 2000.0F
     * - 需要工具 / Required tool: 钻石镐 / Diamond Pickaxe (level 3)
     * - 碰撞箱 / Collision: (0, 0, 0) -> (1, 1.15, 1)
     */
    public static final RegistryObject<Block> REQUIEM_GATE = BLOCKS.register("requiem_gate",
            () -> new RequiemGate(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(100.0F, 2000.0F)  // ✅ 硬度100, 抗性2000
                    .sound(SoundType.STONE)
                    .noOcclusion()));  // 非完整方块

    /**
     * 安魂之铸
     * Requiem Recast
     *
     * 原代码属性（100%保持一致）：
     * Original properties (100% unchanged):
     * - 硬度 / Hardness: 100.0F
     * - 抗性 / Resistance: 2000.0F
     * - 需要工具 / Required tool: 钻石镐 / Diamond Pickaxe (level 3)
     */
    public static final RegistryObject<Block> REQUIEM_RECAST = BLOCKS.register("requiem_recast",
            () -> new RequiemRecast(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(100.0F, 2000.0F)  // ✅ 硬度100, 抗性2000
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    /**
     * 安魂之融
     * Requiem Evolve
     *
     * 原代码属性（100%保持一致）：
     * Original properties (100% unchanged):
     * - 硬度 / Hardness: 100.0F
     * - 抗性 / Resistance: 2000.0F
     * - 需要工具 / Required tool: 钻石镐 / Diamond Pickaxe (level 3)
     */
    public static final RegistryObject<Block> REQUIEM_EVOLVE = BLOCKS.register("requiem_evolve",
            () -> new RequiemEvolve(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(100.0F, 2000.0F)  // ✅ 硬度100, 抗性2000
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    /**
     * 武器军械库
     * Requiem Weapon Table
     *
     * 原代码属性（100%保持一致）：
     * Original properties (100% unchanged):
     * - 硬度 / Hardness: 100.0F
     * - 抗性 / Resistance: 2000.0F
     * - 需要工具 / Required tool: 钻石镐 / Diamond Pickaxe (level 3)
     */
    public static final RegistryObject<Block> REQUIEM_WEAPON_TABLE = BLOCKS.register("requiem_weapon_table",
            () -> new RequiemWeaponTable(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(100.0F, 2000.0F)  // ✅ 硬度100, 抗性2000
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    /**
     * 战甲军械库
     * Requiem Warframe Table
     *
     * 原代码属性（100%保持一致）：
     * Original properties (100% unchanged):
     * - 硬度 / Hardness: 100.0F
     * - 抗性 / Resistance: 2000.0F
     * - 需要工具 / Required tool: 钻石镐 / Diamond Pickaxe (level 3)
     */
    public static final RegistryObject<Block> REQUIEM_WARFRAME_TABLE = BLOCKS.register("requiem_warframe_table",
            () -> new RequiemWarframeTable(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(100.0F, 2000.0F)  // ✅ 硬度100, 抗性2000
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    // ========== 方块物品注册 / Block Items Registration ==========

    /**
     * 注册方块物品的辅助方法
     * Helper method to register block items
     */
    public static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return KuvaLichItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    // 在KuvaLichItems中注册所有方块物品
    // Register all block items in KuvaLichItems
    public static final RegistryObject<Item> REQUIEM_ORE_ITEM = registerBlockItem("requiem_ore", REQUIEM_ORE);
    public static final RegistryObject<Item> EXPERIENCE_ORE_ITEM = registerBlockItem("experience_ore", EXPERIENCE_ORE);
    public static final RegistryObject<Item> REQUIEM_GATE_ITEM = registerBlockItem("requiem_gate", REQUIEM_GATE);
    public static final RegistryObject<Item> REQUIEM_RECAST_ITEM = registerBlockItem("requiem_recast", REQUIEM_RECAST);
    public static final RegistryObject<Item> REQUIEM_EVOLVE_ITEM = registerBlockItem("requiem_evolve", REQUIEM_EVOLVE);
    public static final RegistryObject<Item> REQUIEM_WEAPON_TABLE_ITEM = registerBlockItem("requiem_weapon_table", REQUIEM_WEAPON_TABLE);
    public static final RegistryObject<Item> REQUIEM_WARFRAME_TABLE_ITEM = registerBlockItem("requiem_warframe_table", REQUIEM_WARFRAME_TABLE);
}