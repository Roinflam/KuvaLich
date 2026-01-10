// 文件：EnchantmentRequiemDestroyed.java
// 路径：src/main/java/pers/roinflam/kuvalich/enchantment/EnchantmentRequiemDestroyed.java
package pers.roinflam.kuvalich.enchantment;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.module.item.ItemCommonModule;
import pers.roinflam.kuvalich.item.module.item.ItemRareModule;
import pers.roinflam.kuvalich.item.module.item.ItemUncommonModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeCommonModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRareModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeUncommonModule;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

@Mod.EventBusSubscriber
public class EnchantmentRequiemDestroyed extends EnchantmentBase {
    // 常量定义
    private static final int MIN_ENCHANTABILITY = 35;
    private static final float FAST_BREAK_SPEED = 300.0f;
    private static final float SLOW_BREAK_SPEED = 1.0f;
    private static final int EXP_MULTIPLIER = 2;

    private static final int CARD_DROP_CHANCE_WITH_ENCHANT = 75;
    private static final int BASE_CARD_DROP_CHANCE = 25;
    private static final double FORTUNE_REDUCTION_PER_LEVEL = 2.5;
    private static final int MIN_CARD_DROP_CHANCE = 5;

    private static final int COMMON_MODULE_CHANCE = 15;
    private static final int UNCOMMON_MODULE_CHANCE = 10;
    private static final int RARE_MODULE_CHANCE = 5;
    private static final int MODULE_TYPE_CHANCE = 75; // 75%掉落武器模组，25%掉落战甲模组

    private static final Item[] REQUIEM_CARDS = {
            KuvaLichItems.FASS_CARD, KuvaLichItems.JAHU_CARD, KuvaLichItems.KHRA_CARD,
            KuvaLichItems.LOHK_CARD, KuvaLichItems.NETRA_CARD, KuvaLichItems.RIS_CARD,
            KuvaLichItems.VOME_CARD, KuvaLichItems.XATA_CARD
    };

    public EnchantmentRequiemDestroyed(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots, "requiem_destroyed");
    }

    public static Enchantment getEnchantment() {
        return KuvaLichEnchantments.REQUIEM_DESTROYED;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed evt) {
        Block block = evt.getState().getBlock();
        if (!isRequiemBlock(block)) {
            return;
        }

        EntityPlayer player = evt.getEntityPlayer();
        if (player == null || player.swingingHand == null) {
            return;
        }

        ItemStack tool = player.getHeldItem(player.swingingHand);
        if (tool == null || tool.isEmpty()) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), tool);

        if (enchantLevel > 0) {
            evt.setNewSpeed(FAST_BREAK_SPEED);
        } else {
            IBlockState state = evt.getState();
            float defaultSpeed = tool.getDestroySpeed(state);
            evt.setNewSpeed(Math.min(defaultSpeed, SLOW_BREAK_SPEED));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent evt) {
        Block block = evt.getState().getBlock();
        if (!block.equals(KuvaLichBlocks.REQUIEM_ORE)) {
            return;
        }

        EntityPlayer player = evt.getPlayer();
        if (player == null) {
            return;
        }

        ItemStack tool = player.getHeldItem(player.getActiveHand());
        if (tool == null || tool.isEmpty()) {
            return;
        }

        if (EnchantmentHelper.getEnchantmentLevel(getEnchantment(), tool) > 0) {
            evt.setExpToDrop(evt.getExpToDrop() * EXP_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onHarvestDrops(BlockEvent.HarvestDropsEvent evt) {
        Block block = evt.getState().getBlock();
        if (!block.equals(KuvaLichBlocks.REQUIEM_ORE)) {
            return;
        }

        EntityPlayer player = evt.getHarvester();
        if (player == null) {
            return;
        }

        ItemStack tool = player.getHeldItem(player.getActiveHand());
        if (tool == null || tool.isEmpty()) {
            return;
        }

        // 检查是否有精准采集
        if (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), tool);
        int fortuneLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, tool);

        if (shouldDropCard(enchantLevel, fortuneLevel)) {
            dropRequiemCard(evt);
            dropModules(evt);
            dropKuvaResources(evt);
        }
    }

    /**
     * 检查是否应该掉落卡片
     */
    private static boolean shouldDropCard(int enchantLevel, int fortuneLevel) {
        if (enchantLevel > 0) {
            return RandomUtil.percentageChance(CARD_DROP_CHANCE_WITH_ENCHANT);
        } else {
            double chance = BASE_CARD_DROP_CHANCE - FORTUNE_REDUCTION_PER_LEVEL * fortuneLevel;
            return RandomUtil.percentageChance(Math.max(MIN_CARD_DROP_CHANCE, chance));
        }
    }

    /**
     * 掉落安魂卡片
     */
    private static void dropRequiemCard(BlockEvent.HarvestDropsEvent evt) {
        Item card = REQUIEM_CARDS[RandomUtil.getInt(0, REQUIEM_CARDS.length - 1)];
        Block block = evt.getState().getBlock();
        evt.getDrops().add(new ItemStack(card, 1, block.damageDropped(evt.getState())));
    }

    /**
     * 掉落模组
     */
    private static void dropModules(BlockEvent.HarvestDropsEvent evt) {
        World world = evt.getWorld();
        BlockPos pos = evt.getPos();

        double roll = Math.random() * 100;
        ItemStack module = null;

        if (roll < COMMON_MODULE_CHANCE) {
            module = RandomUtil.percentageChance(MODULE_TYPE_CHANCE)
                    ? ItemCommonModule.getRandomModule()
                    : WarframeCommonModule.getRandomModule();
        } else if (roll < COMMON_MODULE_CHANCE + UNCOMMON_MODULE_CHANCE) {
            module = RandomUtil.percentageChance(MODULE_TYPE_CHANCE)
                    ? ItemUncommonModule.getRandomModule()
                    : WarframeUncommonModule.getRandomModule();
        } else if (roll < COMMON_MODULE_CHANCE + UNCOMMON_MODULE_CHANCE + RARE_MODULE_CHANCE) {
            module = RandomUtil.percentageChance(MODULE_TYPE_CHANCE)
                    ? ItemRareModule.getRandomModule()
                    : WarframeRareModule.getRandomModule();
        }

        if (module != null && !module.isEmpty()) {
            spawnItem(world, pos, module);
        }
    }

    /**
     * 掉落Kuva资源
     */
    private static void dropKuvaResources(BlockEvent.HarvestDropsEvent evt) {
        World world = evt.getWorld();
        BlockPos pos = evt.getPos();

        // 掉落Riven碎片
        spawnItem(world, pos, new ItemStack(KuvaLichItems.RivenSliver, 1));

        // 掉落Kuva
        spawnItem(world, pos, new ItemStack(KuvaLichItems.KUVA, RandomUtil.getInt(2, 4)));
    }

    /**
     * 生成掉落物
     */
    private static void spawnItem(World world, BlockPos pos, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        EntityItem entityItem = new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), itemStack);
        world.spawnEntity(entityItem);
    }

    /**
     * 检查是否为安魂方块
     */
    private static boolean isRequiemBlock(Block block) {
        return block.equals(KuvaLichBlocks.REQUIEM_ORE)
                || block.equals(KuvaLichBlocks.REQUIEM_GATE)
                || block.equals(KuvaLichBlocks.REQUIEM_RECAST)
                || block.equals(KuvaLichBlocks.REQUIEM_EVOLVE);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return MIN_ENCHANTABILITY;
    }
}