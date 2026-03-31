package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.AbstractItemModule;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.*;

/**
 * 安魂之铸菜单（1.20.1版本）
 * Requiem Recast Menu (1.20.1 version)
 * <p>
 * ⭐ 新增功能：
 * 1. 战甲模组三合一（可通过配置开关，默认开）
 * 2. Prime模组三合一（可通过配置开关，默认关，产出未揭示的Prime）
 * 3. 裂罅模组三合一（可通过配置开关，默认关，产出未揭示的裂罅）
 * 4. 跨品质三合一（可通过配置开关，默认关，产出品质按放入比例随机）
 * 5. 武器和战甲各自独立配置
 * <p>
 * ⭐ New features:
 * 1. Warframe module 3-to-1 (configurable, default ON)
 * 2. Prime module 3-to-1 (configurable, default OFF, output: unveiled Prime)
 * 3. Riven module 3-to-1 (configurable, default OFF, output: unveiled Riven)
 * 4. Cross-tier 3-to-1 (configurable, default OFF, output tier based on input ratio)
 * 5. Weapon and warframe have independent configs
 */
public class MenuRequiemRecast extends AbstractContainerMenu {

    private final Level level;
    private final BlockPos pos;
    private final Player player;

    private final ItemStackHandler cardHandler;

    /** 上次检测的输入状态 / Last detected input state */
    private ItemStack lastInput0 = ItemStack.EMPTY;
    private ItemStack lastInput1 = ItemStack.EMPTY;
    private ItemStack lastInput2 = ItemStack.EMPTY;

    /**
     * 构造函数
     *
     * @param windowId        窗口ID
     * @param playerInventory 玩家背包
     * @param level           世界
     * @param pos             方块位置
     */
    public MenuRequiemRecast(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_RECAST.get(), windowId);

        this.level = level;
        this.pos = pos;
        this.player = playerInventory.player;
        this.cardHandler = new ItemStackHandler(4);

        // 添加输入/输出槽位 / Add input/output slots
        this.addSlot(new InputSlot(this.cardHandler, 0, 31, 54));
        this.addSlot(new InputSlot(this.cardHandler, 1, 80, 54));
        this.addSlot(new InputSlot(this.cardHandler, 2, 129, 54));
        this.addSlot(new OutputSlot(this.cardHandler, 3, 80, 10));

        // 添加玩家背包槽位 / Add player inventory slots
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 152 - 10));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 94 - 10 + row * 18));
            }
        }

        LogUtil.debugEvent("安魂之铸菜单创建", player.getName().getString(), "位置: " + pos.toString());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot == null || !slot.hasItem()) {
            return itemstack;
        }

        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        if (index < 4) {
            if (index == 3) {
                return ItemStack.EMPTY;
            }

            if (!this.moveItemStackTo(slotStack, 4, 40, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.moveItemStackTo(slotStack, 0, 3, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotStack.getCount() == itemstack.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, slotStack);
        return itemstack;
    }

    /**
     * 每次同步时检测合成
     * Check crafting on sync
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!level.isClientSide) {
            checkAndProcessCrafting();
        }
    }

    /**
     * 检测并处理合成
     * Check and process crafting
     */
    private void checkAndProcessCrafting() {
        ItemStack slot0 = cardHandler.getStackInSlot(0);
        ItemStack slot1 = cardHandler.getStackInSlot(1);
        ItemStack slot2 = cardHandler.getStackInSlot(2);
        ItemStack output = cardHandler.getStackInSlot(3);

        // 检测输入是否变化 / Check if input changed
        boolean inputChanged = !ItemStack.matches(slot0, lastInput0) ||
                !ItemStack.matches(slot1, lastInput1) ||
                !ItemStack.matches(slot2, lastInput2);

        // 输出已存在，不处理 / Output exists, skip
        if (!output.isEmpty()) {
            lastInput0 = slot0.copy();
            lastInput1 = slot1.copy();
            lastInput2 = slot2.copy();
            return;
        }

        // 输入不完整，不处理 / Input incomplete, skip
        if (slot0.isEmpty() || slot1.isEmpty() || slot2.isEmpty()) {
            lastInput0 = slot0.copy();
            lastInput1 = slot1.copy();
            lastInput2 = slot2.copy();
            return;
        }

        // 输入变化了，尝试合成 / Input changed, try crafting
        if (inputChanged) {
            processCrafting(slot0, slot1, slot2);
            lastInput0 = slot0.copy();
            lastInput1 = slot1.copy();
            lastInput2 = slot2.copy();
        }
    }

    /**
     * 处理合成（已扩展支持战甲模组、Prime、裂罅、跨品质）
     * Process crafting (extended: warframe modules, Prime, Riven, cross-tier)
     *
     * @param slot0 第一个输入槽
     * @param slot1 第二个输入槽
     * @param slot2 第三个输入槽
     */
    private void processCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        // 情况1：3张安魂卡 / Case 1: 3 Requiem Cards
        if (slot0.getItem() instanceof AbstractRequiemCard &&
                slot1.getItem() instanceof AbstractRequiemCard &&
                slot2.getItem() instanceof AbstractRequiemCard) {
            processRequiemCardCrafting();
            return;
        }

        // 情况2：3个武器模组 / Case 2: 3 weapon modules
        if (slot0.getItem() instanceof AbstractItemModule &&
                slot1.getItem() instanceof AbstractItemModule &&
                slot2.getItem() instanceof AbstractItemModule) {
            processWeaponModuleCrafting(slot0, slot1, slot2);
            return;
        }

        // 情况3：3个战甲模组（需要配置启用）/ Case 3: 3 warframe modules (config required)
        if (ModConfig.KUVA_LICH.enableWarframeModuleRecast.get() &&
                slot0.getItem() instanceof AbstractWarframeModule &&
                slot1.getItem() instanceof AbstractWarframeModule &&
                slot2.getItem() instanceof AbstractWarframeModule) {
            processWarframeModuleCrafting(slot0, slot1, slot2);
            return;
        }
    }

    // ========== 安魂卡合成 / Requiem Card Crafting ==========

    /**
     * 安魂卡合成（业务逻辑100%不变）
     * Requiem Card crafting (business logic 100% unchanged)
     */
    private void processRequiemCardCrafting() {
        try {
            int randomId = level.random.nextInt(8);
            ItemStack result = new ItemStack(AbstractRequiemCard.getCard(randomId));

            // 事务式修改 / Transactional change
            cardHandler.setStackInSlot(3, result);
            cardHandler.setStackInSlot(0, ItemStack.EMPTY);
            cardHandler.setStackInSlot(1, ItemStack.EMPTY);
            cardHandler.setStackInSlot(2, ItemStack.EMPTY);

            broadcastFullState();
            LogUtil.debugEvent("安魂卡合成", "成功", result.getHoverName().getString());
        } catch (Exception e) {
            LogUtil.error("安魂卡合成失败", e);
        }
    }

    // ========== 武器模组品质常量 / Weapon Module Tier Constants ==========

    /** 品质ID：青铜 */
    private static final int TIER_COMMON = 0;
    /** 品质ID：白银 */
    private static final int TIER_UNCOMMON = 1;
    /** 品质ID：黄金 */
    private static final int TIER_RARE = 2;
    /** 品质ID：Prime */
    private static final int TIER_PRIME = 3;
    /** 品质ID：裂罅 */
    private static final int TIER_RIVEN = 4;
    /** 品质ID：未知/不支持 */
    private static final int TIER_UNKNOWN = -1;

    // ========== 武器模组三合一 / Weapon Module 3-to-1 ==========

    /**
     * 获取武器模组的品质ID
     * Get weapon module tier ID
     *
     * @param stack 模组物品栈
     * @return 品质ID常量
     */
    private int getWeaponModuleTier(ItemStack stack) {
        if (stack.getItem() instanceof ItemRivenModule) return TIER_RIVEN;
        if (stack.getItem() instanceof ItemPrimeModule) return TIER_PRIME;
        if (stack.getItem() instanceof ItemRareModule) return TIER_RARE;
        if (stack.getItem() instanceof ItemUncommonModule) return TIER_UNCOMMON;
        if (stack.getItem() instanceof ItemCommonModule) return TIER_COMMON;
        return TIER_UNKNOWN;
    }

    /**
     * 根据品质ID获取武器模组的随机产物
     * Get random weapon module by tier ID
     *
     * @param tier 品质ID
     * @return 随机模组物品栈，不支持的品质返回null
     */
    private ItemStack getRandomWeaponModuleByTier(int tier) {
        switch (tier) {
            case TIER_COMMON: return ItemCommonModule.getRandomModule();
            case TIER_UNCOMMON: return ItemUncommonModule.getRandomModule();
            case TIER_RARE: return ItemRareModule.getRandomModule();
            case TIER_PRIME: return ItemPrimeModule.getRandomModule();
            case TIER_RIVEN: return ItemRivenModule.getRandomModule();
            default: return null;
        }
    }

    /**
     * 处理武器模组三合一合成
     * Process weapon module 3-to-1 crafting
     * <p>
     * 规则：
     * 1. 裂罅：3个裂罅 → 未揭示裂罅（需配置启用）
     * 2. Prime：3个Prime → 未揭示Prime（需配置启用）
     * 3. 基础三级（青铜/白银/黄金）：
     *    a. 同品质 → 对应品质随机模组
     *    b. 跨品质（需配置启用）→ 按比例随机品质
     * 4. 混合类别（Prime混基础等）→ 不合成
     *
     * @param slot0 第一个武器模组
     * @param slot1 第二个武器模组
     * @param slot2 第三个武器模组
     */
    private void processWeaponModuleCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        try {
            int tier0 = getWeaponModuleTier(slot0);
            int tier1 = getWeaponModuleTier(slot1);
            int tier2 = getWeaponModuleTier(slot2);

            // 未知品质不处理 / Unknown tier, skip
            if (tier0 == TIER_UNKNOWN || tier1 == TIER_UNKNOWN || tier2 == TIER_UNKNOWN) {
                return;
            }

            // ===== 裂罅三合一 / Riven 3-to-1 =====
            if (tier0 == TIER_RIVEN && tier1 == TIER_RIVEN && tier2 == TIER_RIVEN) {
                if (!ModConfig.KUVA_LICH.enableRivenModuleRecast.get()) {
                    return;
                }
                ItemStack result = getRandomWeaponModuleByTier(TIER_RIVEN);
                if (result != null) {
                    consumeAndOutput(result);
                    LogUtil.debugEvent("武器裂罅三合一", "成功", result.getHoverName().getString());
                }
                return;
            }

            // ===== Prime三合一 / Prime 3-to-1 =====
            if (tier0 == TIER_PRIME && tier1 == TIER_PRIME && tier2 == TIER_PRIME) {
                if (!ModConfig.KUVA_LICH.enablePrimeModuleRecast.get()) {
                    return;
                }
                ItemStack result = getRandomWeaponModuleByTier(TIER_PRIME);
                if (result != null) {
                    consumeAndOutput(result);
                    LogUtil.debugEvent("武器Prime三合一", "成功", result.getHoverName().getString());
                }
                return;
            }

            // ===== 基础三级（青铜/白银/黄金）/ Base 3 tiers (Common/Uncommon/Rare) =====
            // 检查是否全部属于基础三级 / Check if all are base 3 tiers
            boolean allBaseTier = isBaseTier(tier0) && isBaseTier(tier1) && isBaseTier(tier2);
            if (!allBaseTier) {
                // 混合了Prime/裂罅和基础品质，不允许合成
                // Mixed Prime/Riven with base tiers, crafting not allowed
                return;
            }

            // 同品质检查 / Same tier check
            boolean allSameTier = (tier0 == tier1 && tier1 == tier2);

            if (allSameTier) {
                // 同品质三合一 / Same tier 3-to-1
                ItemStack result = getRandomWeaponModuleByTier(tier0);
                if (result != null) {
                    consumeAndOutput(result);
                    LogUtil.debugEvent("武器模组三合一", "同品质", result.getHoverName().getString());
                }
            } else {
                // 跨品质检查 / Cross-tier check
                if (!ModConfig.KUVA_LICH.enableCrossTierRecast.get()) {
                    return;
                }
                // 按比例随机品质 / Probability-based tier selection
                int selectedTier = selectTierByRatio(tier0, tier1, tier2);
                ItemStack result = getRandomWeaponModuleByTier(selectedTier);
                if (result != null) {
                    consumeAndOutput(result);
                    LogUtil.debugEvent("武器模组跨品质三合一", "品质=" + selectedTier, result.getHoverName().getString());
                }
            }
        } catch (Exception e) {
            LogUtil.error("武器模组三合一失败", e);
        }
    }

    // ========== 战甲模组三合一 / Warframe Module 3-to-1 ==========

    /**
     * 获取战甲模组的品质ID
     * Get warframe module tier ID
     *
     * @param stack 模组物品栈
     * @return 品质ID常量
     */
    private int getWarframeModuleTier(ItemStack stack) {
        if (stack.getItem() instanceof WarframeRivenModule) return TIER_RIVEN;
        if (stack.getItem() instanceof WarframePrimeModule) return TIER_PRIME;
        if (stack.getItem() instanceof WarframeRareModule) return TIER_RARE;
        if (stack.getItem() instanceof WarframeUncommonModule) return TIER_UNCOMMON;
        if (stack.getItem() instanceof WarframeCommonModule) return TIER_COMMON;
        return TIER_UNKNOWN;
    }

    /**
     * 根据品质ID获取战甲模组的随机产物
     * Get random warframe module by tier ID
     *
     * @param tier 品质ID
     * @return 随机模组物品栈，不支持的品质返回null
     */
    private ItemStack getRandomWarframeModuleByTier(int tier) {
        switch (tier) {
            case TIER_COMMON: return WarframeCommonModule.getRandomModule();
            case TIER_UNCOMMON: return WarframeUncommonModule.getRandomModule();
            case TIER_RARE: return WarframeRareModule.getRandomModule();
            case TIER_PRIME: return WarframePrimeModule.getRandomModule();
            case TIER_RIVEN: return WarframeRivenModule.getRandomModule();
            default: return null;
        }
    }

    /**
     * 处理战甲模组三合一合成
     * Process warframe module 3-to-1 crafting
     * <p>
     * 规则与武器模组完全一致，但使用独立的配置开关。
     * Rules identical to weapon modules but with independent config toggles.
     *
     * @param slot0 第一个战甲模组
     * @param slot1 第二个战甲模组
     * @param slot2 第三个战甲模组
     */
    private void processWarframeModuleCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        try {
            int tier0 = getWarframeModuleTier(slot0);
            int tier1 = getWarframeModuleTier(slot1);
            int tier2 = getWarframeModuleTier(slot2);

            // 未知品质不处理 / Unknown tier, skip
            if (tier0 == TIER_UNKNOWN || tier1 == TIER_UNKNOWN || tier2 == TIER_UNKNOWN) {
                return;
            }

            // ===== 裂罅三合一 / Riven 3-to-1 =====
            if (tier0 == TIER_RIVEN && tier1 == TIER_RIVEN && tier2 == TIER_RIVEN) {
                if (!ModConfig.KUVA_LICH.enableWarframeRivenModuleRecast.get()) {
                    return;
                }
                ItemStack result = getRandomWarframeModuleByTier(TIER_RIVEN);
                if (result != null) {
                    consumeAndOutput(result);
                    LogUtil.debugEvent("战甲裂罅三合一", "成功", result.getHoverName().getString());
                }
                return;
            }

            // ===== Prime三合一 / Prime 3-to-1 =====
            if (tier0 == TIER_PRIME && tier1 == TIER_PRIME && tier2 == TIER_PRIME) {
                if (!ModConfig.KUVA_LICH.enableWarframePrimeModuleRecast.get()) {
                    return;
                }
                ItemStack result = getRandomWarframeModuleByTier(TIER_PRIME);
                if (result != null) {
                    consumeAndOutput(result);
                    LogUtil.debugEvent("战甲Prime三合一", "成功", result.getHoverName().getString());
                }
                return;
            }

            // ===== 基础三级 / Base 3 tiers =====
            boolean allBaseTier = isBaseTier(tier0) && isBaseTier(tier1) && isBaseTier(tier2);
            if (!allBaseTier) {
                return;
            }

            boolean allSameTier = (tier0 == tier1 && tier1 == tier2);

            if (allSameTier) {
                ItemStack result = getRandomWarframeModuleByTier(tier0);
                if (result != null) {
                    consumeAndOutput(result);
                    LogUtil.debugEvent("战甲模组三合一", "同品质", result.getHoverName().getString());
                }
            } else {
                if (!ModConfig.KUVA_LICH.enableWarframeCrossTierRecast.get()) {
                    return;
                }
                int selectedTier = selectTierByRatio(tier0, tier1, tier2);
                ItemStack result = getRandomWarframeModuleByTier(selectedTier);
                if (result != null) {
                    consumeAndOutput(result);
                    LogUtil.debugEvent("战甲模组跨品质三合一", "品质=" + selectedTier, result.getHoverName().getString());
                }
            }
        } catch (Exception e) {
            LogUtil.error("战甲模组三合一失败", e);
        }
    }

    // ========== 通用工具方法 / Utility Methods ==========

    /**
     * 判断品质ID是否属于基础三级（青铜/白银/黄金）
     * Check if tier ID is one of the base 3 tiers (Common/Uncommon/Rare)
     *
     * @param tier 品质ID
     * @return 是否为基础三级
     */
    private boolean isBaseTier(int tier) {
        return tier == TIER_COMMON || tier == TIER_UNCOMMON || tier == TIER_RARE;
    }

    /**
     * 根据三个输入品质的比例随机选择产出品质
     * Select output tier based on input tier ratio
     * <p>
     * 每种品质出现的次数作为权重：
     * - 2金+1铜 → 金66.7%，铜33.3%
     * - 1金+1银+1铜 → 各33.3%
     * - 3金 → 金100%
     * <p>
     * Each tier's occurrence count is used as weight:
     * - 2 Gold + 1 Bronze → Gold 66.7%, Bronze 33.3%
     * - 1 Gold + 1 Silver + 1 Bronze → each 33.3%
     * - 3 Gold → Gold 100%
     *
     * @param tier0 第一个输入的品质ID
     * @param tier1 第二个输入的品质ID
     * @param tier2 第三个输入的品质ID
     * @return 随机选中的品质ID
     */
    private int selectTierByRatio(int tier0, int tier1, int tier2) {
        // 统计各品质出现次数 / Count occurrences of each tier
        Map<Integer, Integer> tierCounts = new HashMap<>();
        tierCounts.merge(tier0, 1, Integer::sum);
        tierCounts.merge(tier1, 1, Integer::sum);
        tierCounts.merge(tier2, 1, Integer::sum);

        // 按权重随机选择 / Weighted random selection
        // 总权重固定为3（三个输入）/ Total weight is always 3 (three inputs)
        double random = level.random.nextDouble() * 3.0;
        double cumulative = 0.0;

        for (Map.Entry<Integer, Integer> entry : tierCounts.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                return entry.getKey();
            }
        }

        // 兜底：返回第一个品质 / Fallback: return first tier
        return tier0;
    }

    /**
     * 消耗三个输入槽并设置产出
     * Consume all three input slots and set output
     *
     * @param result 产出物品栈
     */
    private void consumeAndOutput(ItemStack result) {
        cardHandler.setStackInSlot(3, result);
        cardHandler.setStackInSlot(0, ItemStack.EMPTY);
        cardHandler.setStackInSlot(1, ItemStack.EMPTY);
        cardHandler.setStackInSlot(2, ItemStack.EMPTY);
        broadcastFullState();
    }

    /**
     * 强制同步所有容器槽位
     * Force sync all container slots
     */
    public void broadcastFullState() {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
        }
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            for (int i = 0; i < 4; i++) {
                if (!cardHandler.getStackInSlot(i).isEmpty()) {
                    ItemStack stack = cardHandler.getStackInSlot(i);
                    if (!this.moveItemStackTo(stack, 4, 40, true)) {
                        level.addFreshEntity(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), stack));
                    }
                }
            }
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    /**
     * 输入槽位（已扩展支持战甲模组、Prime、裂罅）
     * Input slot (extended: warframe modules, Prime, Riven support)
     * <p>
     * 接受规则：
     * 1. 安魂卡 → 始终接受
     * 2. 武器模组（非裂罅）→ 始终接受
     * 3. 武器裂罅模组 → 仅当配置启用时接受
     * 4. 战甲模组（非裂罅）→ 仅当配置启用战甲三合一时接受
     * 5. 战甲裂罅模组 → 仅当配置启用战甲三合一且启用战甲裂罅时接受
     */
    public static class InputSlot extends SlotItemHandler {

        /**
         * @param itemHandler 物品处理器
         * @param index       槽位索引
         * @param xPosition   GUI X坐标
         * @param yPosition   GUI Y坐标
         */
        public InputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            // 安魂卡始终接受 / Always accept Requiem Cards
            if (stack.getItem() instanceof AbstractRequiemCard) {
                return super.mayPlace(stack);
            }

            // ===== 武器模组 / Weapon Modules =====
            if (stack.getItem() instanceof AbstractItemModule) {
                // 武器裂罅：需要配置启用 / Weapon Riven: config required
                if (stack.getItem() instanceof ItemRivenModule) {
                    return ModConfig.KUVA_LICH.enableRivenModuleRecast.get() && super.mayPlace(stack);
                }
                // 武器Prime：需要配置启用 / Weapon Prime: config required
                if (stack.getItem() instanceof ItemPrimeModule) {
                    return ModConfig.KUVA_LICH.enablePrimeModuleRecast.get() && super.mayPlace(stack);
                }
                // 其他武器模组（青铜/白银/黄金）：始终接受 / Other weapon modules (Common/Uncommon/Rare): always
                return super.mayPlace(stack);
            }

            // ===== 战甲模组 / Warframe Modules =====
            if (stack.getItem() instanceof AbstractWarframeModule) {
                // 战甲三合一总开关 / Warframe recast master toggle
                if (!ModConfig.KUVA_LICH.enableWarframeModuleRecast.get()) {
                    return false;
                }
                // 战甲裂罅：需要配置启用 / Warframe Riven: config required
                if (stack.getItem() instanceof WarframeRivenModule) {
                    return ModConfig.KUVA_LICH.enableWarframeRivenModuleRecast.get() && super.mayPlace(stack);
                }
                // 战甲Prime：需要配置启用 / Warframe Prime: config required
                if (stack.getItem() instanceof WarframePrimeModule) {
                    return ModConfig.KUVA_LICH.enableWarframePrimeModuleRecast.get() && super.mayPlace(stack);
                }
                // 其他战甲模组（青铜/白银/黄金）：三合一启用时接受 / Other warframe modules: accept when recast enabled
                return super.mayPlace(stack);
            }

            return false;
        }
    }

    /**
     * 输出槽位（只读）
     * Output slot (read-only)
     */
    public static class OutputSlot extends SlotItemHandler {

        /**
         * @param itemHandler 物品处理器
         * @param index       槽位索引
         * @param xPosition   GUI X坐标
         * @param yPosition   GUI Y坐标
         */
        public OutputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }
    }
}
