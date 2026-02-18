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
import pers.roinflam.kuvalich.config.ModConfig;
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
 * 2. 增加安魂矿石的经验掉落（倍率可配置）
 * 3. 增加安魂卡片和模组的掉落几率（概率均可配置）
 *
 * 所有概率常量已移至 ModConfig.KUVA_LICH，可在配置文件中调整
 * All probability constants moved to ModConfig.KUVA_LICH, configurable in config file
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentRequiemDestroyed extends EnchantmentBase {

    /** 最低附魔消耗 / Minimum enchantability */
    private static final int MIN_ENCHANTABILITY = 35;

    /** 有附魔时的高速挖掘速度 / High break speed when enchantment is present */
    private static final float FAST_BREAK_SPEED = 300.0f;

    /** 无附魔时对安魂方块的降速 / Slow break speed for requiem blocks without enchantment */
    private static final float SLOW_BREAK_SPEED = 1.0f;

    public EnchantmentRequiemDestroyed() {
        super(Enchantment.Rarity.VERY_RARE,
                EnchantmentCategory.DIGGER,
                new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.MAINHAND},
                "requiem_destroyed");
    }

    /**
     * 获取随机安魂卡片（延迟初始化，避免注册时序问题）
     * Get random Requiem Card (lazy init to avoid registration order issues)
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
     * 修改挖掘速度事件
     * Modify break speed event
     *
     * 持有灭骸附魔时对安魂方块有极高挖掘速度，否则降速
     * With enchantment: very fast break speed for requiem blocks; without: slow
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
     * 方块破坏事件（处理掉落物和经验）
     * Block break event (handles drops and experience)
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

        // 有精准采集时不触发额外掉落 / No extra drops with Silk Touch
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                KuvaLichEnchantments.REQUIEM_DESTROYED.get(), tool);
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);

        // 有灭骸附魔时增加经验掉落（倍率从配置文件读取）
        // Increase XP drop with enchantment (multiplier from config)
        if (enchantLevel > 0) {
            int multiplier = ModConfig.KUVA_LICH.expMultiplier.get();
            evt.setExpToDrop(evt.getExpToDrop() * multiplier);
        }

        // 判断是否触发额外掉落 / Determine if extra drops should happen
        if (shouldDropCard(enchantLevel, fortuneLevel)) {
            ServerLevel world = (ServerLevel) player.level();
            BlockPos pos = evt.getPos();

            dropRequiemCard(world, pos);
            dropModules(world, pos);
            dropKuvaResources(world, pos);
        }
    }

    /**
     * 判断是否应该掉落安魂卡片
     * Check if Requiem Card should drop
     *
     * 有附魔时使用 cardDropChanceWithEnchant，无附魔时使用 baseCardDropChance（受时运削减）
     * With enchant: use cardDropChanceWithEnchant; without: use baseCardDropChance (reduced by Fortune)
     *
     * @param enchantLevel 灭骸附魔等级 / Requiem Destroyed enchantment level
     * @param fortuneLevel 时运附魔等级 / Fortune enchantment level
     * @return true表示应掉落卡片 / true if card should drop
     */
    private static boolean shouldDropCard(int enchantLevel, int fortuneLevel) {
        if (enchantLevel > 0) {
            // 有灭骸附魔：使用配置的高概率
            // With Requiem Destroyed: use configured high chance
            return RandomUtil.percentageChance(ModConfig.KUVA_LICH.cardDropChanceWithEnchant.get());
        } else {
            // 无灭骸附魔：基础概率减去时运加成的削减，但不低于最低概率
            // Without enchant: base chance minus fortune reduction, capped at minimum
            double chance = ModConfig.KUVA_LICH.baseCardDropChance.get()
                    - ModConfig.KUVA_LICH.fortuneReductionPerLevel.get() * fortuneLevel;
            double minChance = ModConfig.KUVA_LICH.minCardDropChance.get();
            return RandomUtil.percentageChance(Math.max(minChance, chance));
        }
    }

    /**
     * 掉落安魂卡片
     * Drop Requiem Card
     */
    private static void dropRequiemCard(ServerLevel world, BlockPos pos) {
        Item card = getRandomRequiemCard();
        spawnItem(world, pos, new ItemStack(card, 1));
    }

    /**
     * 掉落模组（根据配置概率随机选择等级）
     * Drop modules (grade randomly selected based on configured chances)
     *
     * 先检查黄金，再检查白银，再检查青铜；75%概率为武器模组，25%为战甲模组
     * Check rare first, then uncommon, then common; 75% weapon mod, 25% warframe mod
     */
    private static void dropModules(ServerLevel world, BlockPos pos) {
        int commonChance = ModConfig.KUVA_LICH.commonModuleDropChance.get();
        int uncommonChance = ModConfig.KUVA_LICH.uncommonModuleDropChance.get();
        int rareChance = ModConfig.KUVA_LICH.rareModuleDropChance.get();

        double roll = Math.random() * 100;
        ItemStack module = null;

        if (roll < rareChance) {
            // 黄金模组 / Rare (Gold) module
            module = RandomUtil.percentageChance(75)
                    ? ItemRareModule.getRandomModule()
                    : WarframeRareModule.getRandomModule();
        } else if (roll < rareChance + uncommonChance) {
            // 白银模组 / Uncommon (Silver) module
            module = RandomUtil.percentageChance(75)
                    ? ItemUncommonModule.getRandomModule()
                    : WarframeUncommonModule.getRandomModule();
        } else if (roll < rareChance + uncommonChance + commonChance) {
            // 青铜模组 / Common (Bronze) module
            module = RandomUtil.percentageChance(75)
                    ? ItemCommonModule.getRandomModule()
                    : WarframeCommonModule.getRandomModule();
        }

        if (module != null && !module.isEmpty()) {
            spawnItem(world, pos, module);
        }
    }

    /**
     * 掉落Kuva资源（裂罅碎块和赤毒）
     * Drop Kuva resources (Riven Sliver and Kuva)
     */
    private static void dropKuvaResources(ServerLevel world, BlockPos pos) {
        spawnItem(world, pos, new ItemStack(KuvaLichItems.RIVEN_SLIVER.get(), 1));
        spawnItem(world, pos, new ItemStack(KuvaLichItems.KUVA.get(), RandomUtil.getInt(2, 4)));
    }

    /**
     * 在指定位置生成掉落物实体
     * Spawn item entity at specified position
     *
     * @param world    服务端世界 / Server level
     * @param pos      方块位置 / Block position
     * @param itemStack 要生成的物品堆 / Item stack to spawn
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
     * 检查方块是否属于安魂类方块（减速范围）
     * Check if block is a Requiem block (speed reduction target)
     *
     * @param block 要检查的方块 / Block to check
     * @return true表示是安魂方块 / true if it's a requiem block
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