package pers.roinflam.kuvalich.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

/**
 * 灭骸附魔
 * Requiem Destroyed Enchantment
 *
 * 效果：
 * 1. 大幅加快安魂方块的挖掘速度
 * 2. 增加安魂矿石的经验掉落
 * 3. 增加安魂卡片和模组的掉落几率
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentRequiemDestroyed extends EnchantmentBase {

    // 常量定义 / Constants
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
    private static final int MODULE_TYPE_CHANCE = 75;

    public EnchantmentRequiemDestroyed() {
        super(Enchantment.Rarity.VERY_RARE,
                EnchantmentCategory.DIGGER,
                new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.MAINHAND},
                "requiem_destroyed");
    }

    /**
     * ✅ 获取随机安魂卡片（延迟初始化）
     * Get random Requiem Card (lazy initialization)
     */
    private static Item getRandomRequiemCard() {
        Item[] cards = {
                KuvaLichItems.FASS_CARD.get(),
                KuvaLichItems.JAHU_CARD.get(),
                KuvaLichItems.KHRA_CARD.get(),
                KuvaLichItems.LOHK_CARD.get(),
                KuvaLichItems.NETRA_CARD.get(),
                KuvaLichItems.RIS_CARD.get(),
                KuvaLichItems.VOME_CARD.get(),
                KuvaLichItems.XATA_CARD.get()
        };
        return cards[RandomUtil.getInt(0, cards.length - 1)];
    }

    /**
     * 修改挖掘速度
     * Modify break speed
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed evt) {
        Block block = evt.getState().getBlock();
        if (!isRequiemBlock(block)) {
            return;
        }

        Player player = evt.getEntity();
        if (player == null) {
            return;
        }

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                KuvaLichEnchantments.REQUIEM_DESTROYED.get(), tool);

        if (enchantLevel > 0) {
            evt.setNewSpeed(FAST_BREAK_SPEED);
        } else {
            BlockState state = evt.getState();
            float defaultSpeed = tool.getDestroySpeed(state);
            evt.setNewSpeed(Math.min(defaultSpeed, SLOW_BREAK_SPEED));
        }
    }

    /**
     * 方块破坏事件（1.20.1中用于处理掉落物和经验）
     * Block break event (used for handling drops and experience in 1.20.1)
     */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent evt) {
        Block block = evt.getState().getBlock();
        if (!block.equals(KuvaLichBlocks.REQUIEM_ORE.get())) {
            return;
        }

        Player player = evt.getPlayer();
        if (player == null || player.level().isClientSide) {
            return;
        }

        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }

        // 检查是否有精准采集 / Check for silk touch
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                KuvaLichEnchantments.REQUIEM_DESTROYED.get(), tool);
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);

        // 增加经验掉落 / Increase experience drop
        if (enchantLevel > 0) {
            evt.setExpToDrop(evt.getExpToDrop() * EXP_MULTIPLIER);
        }

        // 处理额外掉落物 / Handle extra drops
        if (shouldDropCard(enchantLevel, fortuneLevel)) {
            ServerLevel world = (ServerLevel) player.level();
            BlockPos pos = evt.getPos();

            dropRequiemCard(world, pos);
            dropModules(world, pos);
            dropKuvaResources(world, pos);
        }
    }

    /**
     * 检查是否应该掉落卡片
     * Check if should drop card
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
     * Drop Requiem Card
     */
    private static void dropRequiemCard(ServerLevel world, BlockPos pos) {
        Item card = getRandomRequiemCard(); // ✅ 使用方法替代静态数组
        spawnItem(world, pos, new ItemStack(card, 1));
    }

    /**
     * 掉落模组
     * Drop modules
     */
    private static void dropModules(ServerLevel world, BlockPos pos) {
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
     * Drop Kuva resources
     */
    private static void dropKuvaResources(ServerLevel world, BlockPos pos) {
        // 掉落Riven碎片 / Drop Riven Sliver
        spawnItem(world, pos, new ItemStack(KuvaLichItems.RIVEN_SLIVER.get(), 1));

        // 掉落Kuva / Drop Kuva
        spawnItem(world, pos, new ItemStack(KuvaLichItems.KUVA.get(), RandomUtil.getInt(2, 4)));
    }

    /**
     * 生成掉落物
     * Spawn item
     */
    private static void spawnItem(ServerLevel world, BlockPos pos, ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        ItemEntity entityItem = new ItemEntity(world,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                itemStack);
        world.addFreshEntity(entityItem);
    }

    /**
     * 检查是否为安魂方块
     * Check if is Requiem block
     */
    private static boolean isRequiemBlock(Block block) {
        return block.equals(KuvaLichBlocks.REQUIEM_ORE.get())
                || block.equals(KuvaLichBlocks.REQUIEM_GATE.get())
                || block.equals(KuvaLichBlocks.REQUIEM_RECAST.get())
                || block.equals(KuvaLichBlocks.REQUIEM_EVOLVE.get());
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return MIN_ENCHANTABILITY;
    }
}