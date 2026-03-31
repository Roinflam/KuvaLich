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
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.base.enchantment.AbstractEnchantment;
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
 * ⭐ 修复：dropModules 现在读取 moduleWeaponRatio 配置而非硬编码75
 * ⭐ 修复：onBreak 使用 LOW 优先级，避免暮色森林等模组结界区方块复原时重复触发掉落
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentRequiemDestroyed extends AbstractEnchantment {

    private static final int MIN_ENCHANTABILITY = 35;
    private static final float FAST_BREAK_SPEED = 300.0f;
    private static final float SLOW_BREAK_SPEED = 1.0f;

    /**
     * 修复：将安魂卡片数组从方法内移到静态字段
     * 原来每次调用 getRandomRequiemCard() 都会重新 new 一个数组，造成不必要的内存分配
     *
     * Fix: move card array from method-local to static field
     * Previously a new array was allocated on every call to getRandomRequiemCard()
     *
     * 注意：仍然使用延迟初始化模式，通过 supplier 在首次使用时才访问注册表
     * Note: still lazy — accessed at call time, not at class load time, to avoid registration order issues
     */
    private static Item[] getRequiemCards() {
        // 字段缓存：只在首次调用时初始化 / Field cache: only initialize on first call
        if (REQUIEM_CARDS == null) {
            REQUIEM_CARDS = new Item[]{
                    KuvaLichItems.FASS_CARD.get(),
                    KuvaLichItems.JAHU_CARD.get(),
                    KuvaLichItems.KHRA_CARD.get(),
                    KuvaLichItems.LOHK_CARD.get(),
                    KuvaLichItems.NETRA_CARD.get(),
                    KuvaLichItems.RIS_CARD.get(),
                    KuvaLichItems.VOME_CARD.get(),
                    KuvaLichItems.XATA_CARD.get()
            };
        }
        return REQUIEM_CARDS;
    }

    private static Item[] REQUIEM_CARDS = null;

    public EnchantmentRequiemDestroyed() {
        super(Enchantment.Rarity.VERY_RARE,
                EnchantmentCategory.DIGGER,
                new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.MAINHAND},
                "requiem_destroyed");
    }

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
     * 安魂矿石破坏事件 — 掉落安魂卡、模组、赤毒资源
     * <p>
     * ⭐ 使用 EventPriority.LOW 确保在暮色森林等模组的结界保护处理之后执行。
     * 暮色森林在未击败对应Boss时会取消方块破坏事件（cancel），
     * 如果我们在默认优先级处理，可能在取消之前就已经掉落物品，
     * 导致方块复原但物品重复掉落。LOW优先级 + isCanceled检查可完全避免此问题。
     * </p>
     *
     * @param evt 方块破坏事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent evt) {
        // ⭐ 检查事件是否已被其他模组取消（暮色森林结界保护等）
        if (evt.isCanceled()) {
            return;
        }

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

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool) > 0) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                KuvaLichEnchantments.REQUIEM_DESTROYED.get(), tool);
        int fortuneLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);

        if (enchantLevel > 0) {
            int multiplier = ModConfig.KUVA_LICH.expMultiplier.get();
            evt.setExpToDrop(evt.getExpToDrop() * multiplier);
        }

        if (shouldDropCard(enchantLevel, fortuneLevel)) {
            ServerLevel world = (ServerLevel) player.level();
            BlockPos pos = evt.getPos();

            dropRequiemCard(world, pos);
            dropModules(world, pos);
            dropKuvaResources(world, pos);
        }
    }

    private static boolean shouldDropCard(int enchantLevel, int fortuneLevel) {
        if (enchantLevel > 0) {
            return RandomUtil.percentageChance(ModConfig.KUVA_LICH.cardDropChanceWithEnchant.get());
        } else {
            double chance = ModConfig.KUVA_LICH.baseCardDropChance.get()
                    - ModConfig.KUVA_LICH.fortuneReductionPerLevel.get() * fortuneLevel;
            double minChance = ModConfig.KUVA_LICH.minCardDropChance.get();
            return RandomUtil.percentageChance(Math.max(minChance, chance));
        }
    }

    private static void dropRequiemCard(ServerLevel world, BlockPos pos) {
        // 修复：使用缓存数组，不再每次 new / Fix: use cached array, no new allocation every call
        Item[] cards = getRequiemCards();
        Item card = cards[RandomUtil.getInt(0, cards.length - 1)];
        spawnItem(world, pos, new ItemStack(card, 1));
    }

    /**
     * 掉落随机品质模组
     * <p>
     * ⭐ 修复：武器/战甲比例现在读取配置 moduleWeaponRatio，不再硬编码75
     * </p>
     *
     * @param world 服务端世界
     * @param pos   掉落位置
     */
    private static void dropModules(ServerLevel world, BlockPos pos) {
        int commonChance = ModConfig.KUVA_LICH.commonModuleDropChance.get();
        int uncommonChance = ModConfig.KUVA_LICH.uncommonModuleDropChance.get();
        int rareChance = ModConfig.KUVA_LICH.rareModuleDropChance.get();

        // ⭐ 修复：从配置读取武器/战甲比例（原先硬编码为75）
        int weaponRatio = ModConfig.KUVA_LICH.moduleWeaponRatio.get();

        int roll = RandomUtil.getInt(0, 99);
        ItemStack module = null;

        if (roll < rareChance) {
            module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemRareModule.getRandomModule()
                    : WarframeRareModule.getRandomModule();
        } else if (roll < rareChance + uncommonChance) {
            module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemUncommonModule.getRandomModule()
                    : WarframeUncommonModule.getRandomModule();
        } else if (roll < rareChance + uncommonChance + commonChance) {
            module = RandomUtil.percentageChance(weaponRatio)
                    ? ItemCommonModule.getRandomModule()
                    : WarframeCommonModule.getRandomModule();
        }

        if (module != null && !module.isEmpty()) {
            spawnItem(world, pos, module);
        }
    }

    private static void dropKuvaResources(ServerLevel world, BlockPos pos) {
        spawnItem(world, pos, new ItemStack(KuvaLichItems.RIVEN_SLIVER.get(), 1));
        spawnItem(world, pos, new ItemStack(KuvaLichItems.KUVA.get(), RandomUtil.getInt(2, 4)));
    }

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