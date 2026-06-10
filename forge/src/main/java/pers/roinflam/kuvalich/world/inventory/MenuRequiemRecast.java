package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.*;

/**
 * 安魂之铸菜单（1.20.1版本）
 * Requiem Recast Menu (1.20.1 version)
 *
 * <p>支持功能：
 * <ol>
 *   <li>三张安魂卡合成随机安魂卡</li>
 *   <li>三张武器/战甲模组三合一（含同品质、跨品质、Prime、裂罅，受配置控制）</li>
 *   <li>⭐ 单张模组卡分解为内融核心（豆子）：
 *       仅放入一张武器/战甲模组时，输出槽预览出对应数量的内融核心，
 *       取走内融核心后才消耗该模组卡（即分解）。
 *       分解数量公式（总量上限64）：
 *       <pre>
 *         品质基础值 + 等级加成 + 紫卡循环加成
 *       </pre>
 *       品质基础值：铜卡1 / 银卡2 / 金卡4 / Prime与紫卡8；
 *       等级加成：每升一级多1个（即 当前等级-1，仅等级系统开启时计算）；
 *       紫卡循环加成：紫卡每循环1次多1个（武器/战甲紫卡均适用，未揭示的不计循环）。</li>
 * </ol></p>
 *
 * <p>⭐ 关闭时返还物品优先快捷栏（Inventory.add）。
 *    分解预览中的内融核心（输出槽）关闭时不返还，因对应模组卡仍在输入槽且会被返还。</p>
 */
public class MenuRequiemRecast extends AbstractContainerMenu {

    private final Level level;
    private final BlockPos pos;
    private final Player player;

    private final ItemStackHandler cardHandler;

    private ItemStack lastInput0 = ItemStack.EMPTY;
    private ItemStack lastInput1 = ItemStack.EMPTY;
    private ItemStack lastInput2 = ItemStack.EMPTY;

    /**
     * ⭐ 分解预览模式标记。
     * 为 true 时，输出槽中的内融核心是单卡分解的预览结果（尚未消耗输入卡），
     * 取走后由 {@link #onDecomposeResultTaken()} 消耗对应的模组卡。
     * 三合一产物不设置此标记，因此两套逻辑互不干扰。
     */
    private boolean decomposeMode = false;

    /**
     * ⭐ 重入保护标志（仅服务端主线程访问）。
     * processDecompose / consumeAndOutput / processRequiemCardCrafting 内部会调用
     * broadcastFullState，而 broadcastFullState → broadcastChanges → checkAndProcessCrafting
     * 会重入本处理逻辑。重入时直接返回，避免无限递归导致 StackOverflowError。
     */
    private boolean processing = false;

    public MenuRequiemRecast(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_RECAST.get(), windowId);

        this.level = level;
        this.pos = pos;
        this.player = playerInventory.player;
        this.cardHandler = new ItemStackHandler(4);

        this.addSlot(new InputSlot(this.cardHandler, 0, 31, 54));
        this.addSlot(new InputSlot(this.cardHandler, 1, 80, 54));
        this.addSlot(new InputSlot(this.cardHandler, 2, 129, 54));
        // ⭐ 输出槽改为持有菜单引用，以便取走分解产物时回调消耗输入卡
        this.addSlot(new OutputSlot(this, this.cardHandler, 3, 80, 10));

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

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!level.isClientSide) {
            checkAndProcessCrafting();
        }
    }

    private void checkAndProcessCrafting() {
        // ⭐ 重入保护：processDecompose/consumeAndOutput 内部会调用 broadcastFullState，
        //    而 broadcastFullState → broadcastChanges → checkAndProcessCrafting 会重入本方法。
        //    重入时直接返回，避免无限递归导致 StackOverflowError。
        //    注意 super.broadcastChanges() 仍会在 broadcastFullState 内执行，槽位变更照常同步给客户端。
        if (processing) {
            return;
        }
        processing = true;
        try {
            ItemStack slot0 = cardHandler.getStackInSlot(0);
            ItemStack slot1 = cardHandler.getStackInSlot(1);
            ItemStack slot2 = cardHandler.getStackInSlot(2);
            ItemStack output = cardHandler.getStackInSlot(3);

            boolean inputChanged = !ItemStack.matches(slot0, lastInput0) ||
                    !ItemStack.matches(slot1, lastInput1) ||
                    !ItemStack.matches(slot2, lastInput2);

            // ⭐ 分解预览模式：输入发生变化时，先清空旧的内融核心预览，避免输入变更后残留旧预览
            if (inputChanged && decomposeMode && !output.isEmpty()) {
                cardHandler.setStackInSlot(3, ItemStack.EMPTY);
                output = ItemStack.EMPTY;
                decomposeMode = false;
            }

            if (!output.isEmpty()) {
                lastInput0 = slot0.copy(); lastInput1 = slot1.copy(); lastInput2 = slot2.copy();
                return;
            }

            // ⭐ 分解检测：恰好只有一张可分解模组卡时，预览分解为内融核心
            int singleCardSlot = findSingleDecomposableCardSlot(slot0, slot1, slot2);
            if (singleCardSlot >= 0) {
                if (inputChanged) {
                    processDecompose(singleCardSlot);
                    lastInput0 = slot0.copy(); lastInput1 = slot1.copy(); lastInput2 = slot2.copy();
                }
                return;
            }

            if (slot0.isEmpty() || slot1.isEmpty() || slot2.isEmpty()) {
                lastInput0 = slot0.copy(); lastInput1 = slot1.copy(); lastInput2 = slot2.copy();
                return;
            }

            if (inputChanged) {
                processCrafting(slot0, slot1, slot2);
                lastInput0 = slot0.copy(); lastInput1 = slot1.copy(); lastInput2 = slot2.copy();
            }
        } finally {
            // ⭐ 无论正常返回还是异常，都恢复重入保护标志，避免后续 tick 被永久挡住
            processing = false;
        }
    }

    private void processCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        if (slot0.getItem() instanceof AbstractRequiemCard &&
                slot1.getItem() instanceof AbstractRequiemCard &&
                slot2.getItem() instanceof AbstractRequiemCard) {
            processRequiemCardCrafting(); return;
        }
        if (slot0.getItem() instanceof AbstractItemModule &&
                slot1.getItem() instanceof AbstractItemModule &&
                slot2.getItem() instanceof AbstractItemModule) {
            processWeaponModuleCrafting(slot0, slot1, slot2); return;
        }
        if (ModConfig.KUVA_LICH.enableWarframeModuleRecast.get() &&
                slot0.getItem() instanceof AbstractWarframeModule &&
                slot1.getItem() instanceof AbstractWarframeModule &&
                slot2.getItem() instanceof AbstractWarframeModule) {
            processWarframeModuleCrafting(slot0, slot1, slot2); return;
        }
    }

    private void processRequiemCardCrafting() {
        try {
            int randomId = level.random.nextInt(8);
            ItemStack result = new ItemStack(AbstractRequiemCard.getCard(randomId));
            cardHandler.setStackInSlot(3, result);
            cardHandler.setStackInSlot(0, ItemStack.EMPTY);
            cardHandler.setStackInSlot(1, ItemStack.EMPTY);
            cardHandler.setStackInSlot(2, ItemStack.EMPTY);
            broadcastFullState();
            LogUtil.debugEvent("安魂卡合成", "成功", result.getHoverName().getString());
        } catch (Exception e) { LogUtil.error("安魂卡合成失败", e); }
    }

    private static final int TIER_COMMON = 0;
    private static final int TIER_UNCOMMON = 1;
    private static final int TIER_RARE = 2;
    private static final int TIER_PRIME = 3;
    private static final int TIER_RIVEN = 4;
    private static final int TIER_UNKNOWN = -1;

    private int getWeaponModuleTier(ItemStack stack) {
        if (stack.getItem() instanceof ItemRivenModule) return TIER_RIVEN;
        if (stack.getItem() instanceof ItemPrimeModule) return TIER_PRIME;
        if (stack.getItem() instanceof ItemRareModule) return TIER_RARE;
        if (stack.getItem() instanceof ItemUncommonModule) return TIER_UNCOMMON;
        if (stack.getItem() instanceof ItemCommonModule) return TIER_COMMON;
        return TIER_UNKNOWN;
    }

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

    private void processWeaponModuleCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        try {
            int tier0 = getWeaponModuleTier(slot0);
            int tier1 = getWeaponModuleTier(slot1);
            int tier2 = getWeaponModuleTier(slot2);
            if (tier0 == TIER_UNKNOWN || tier1 == TIER_UNKNOWN || tier2 == TIER_UNKNOWN) return;

            if (tier0 == TIER_RIVEN && tier1 == TIER_RIVEN && tier2 == TIER_RIVEN) {
                if (!ModConfig.KUVA_LICH.enableRivenModuleRecast.get()) return;
                ItemStack result = getRandomWeaponModuleByTier(TIER_RIVEN);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("武器裂罅三合一", "成功", result.getHoverName().getString()); }
                return;
            }
            if (tier0 == TIER_PRIME && tier1 == TIER_PRIME && tier2 == TIER_PRIME) {
                if (!ModConfig.KUVA_LICH.enablePrimeModuleRecast.get()) return;
                ItemStack result = getRandomWeaponModuleByTier(TIER_PRIME);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("武器Prime三合一", "成功", result.getHoverName().getString()); }
                return;
            }

            boolean allBaseTier = isBaseTier(tier0) && isBaseTier(tier1) && isBaseTier(tier2);
            if (!allBaseTier) return;

            boolean allSameTier = (tier0 == tier1 && tier1 == tier2);
            if (allSameTier) {
                ItemStack result = getRandomWeaponModuleByTier(tier0);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("武器模组三合一", "同品质", result.getHoverName().getString()); }
            } else {
                if (!ModConfig.KUVA_LICH.enableCrossTierRecast.get()) return;
                int selectedTier = selectTierByRatio(tier0, tier1, tier2);
                ItemStack result = getRandomWeaponModuleByTier(selectedTier);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("武器模组跨品质三合一", "品质=" + selectedTier, result.getHoverName().getString()); }
            }
        } catch (Exception e) { LogUtil.error("武器模组三合一失败", e); }
    }

    private int getWarframeModuleTier(ItemStack stack) {
        if (stack.getItem() instanceof WarframeRivenModule) return TIER_RIVEN;
        if (stack.getItem() instanceof WarframePrimeModule) return TIER_PRIME;
        if (stack.getItem() instanceof WarframeRareModule) return TIER_RARE;
        if (stack.getItem() instanceof WarframeUncommonModule) return TIER_UNCOMMON;
        if (stack.getItem() instanceof WarframeCommonModule) return TIER_COMMON;
        return TIER_UNKNOWN;
    }

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

    private void processWarframeModuleCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        try {
            int tier0 = getWarframeModuleTier(slot0);
            int tier1 = getWarframeModuleTier(slot1);
            int tier2 = getWarframeModuleTier(slot2);
            if (tier0 == TIER_UNKNOWN || tier1 == TIER_UNKNOWN || tier2 == TIER_UNKNOWN) return;

            if (tier0 == TIER_RIVEN && tier1 == TIER_RIVEN && tier2 == TIER_RIVEN) {
                if (!ModConfig.KUVA_LICH.enableWarframeRivenModuleRecast.get()) return;
                ItemStack result = getRandomWarframeModuleByTier(TIER_RIVEN);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("战甲裂罅三合一", "成功", result.getHoverName().getString()); }
                return;
            }
            if (tier0 == TIER_PRIME && tier1 == TIER_PRIME && tier2 == TIER_PRIME) {
                if (!ModConfig.KUVA_LICH.enableWarframePrimeModuleRecast.get()) return;
                ItemStack result = getRandomWarframeModuleByTier(TIER_PRIME);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("战甲Prime三合一", "成功", result.getHoverName().getString()); }
                return;
            }

            boolean allBaseTier = isBaseTier(tier0) && isBaseTier(tier1) && isBaseTier(tier2);
            if (!allBaseTier) return;

            boolean allSameTier = (tier0 == tier1 && tier1 == tier2);
            if (allSameTier) {
                ItemStack result = getRandomWarframeModuleByTier(tier0);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("战甲模组三合一", "同品质", result.getHoverName().getString()); }
            } else {
                if (!ModConfig.KUVA_LICH.enableWarframeCrossTierRecast.get()) return;
                int selectedTier = selectTierByRatio(tier0, tier1, tier2);
                ItemStack result = getRandomWarframeModuleByTier(selectedTier);
                if (result != null) { consumeAndOutput(result); LogUtil.debugEvent("战甲模组跨品质三合一", "品质=" + selectedTier, result.getHoverName().getString()); }
            }
        } catch (Exception e) { LogUtil.error("战甲模组三合一失败", e); }
    }

    private boolean isBaseTier(int tier) {
        return tier == TIER_COMMON || tier == TIER_UNCOMMON || tier == TIER_RARE;
    }

    private int selectTierByRatio(int tier0, int tier1, int tier2) {
        Map<Integer, Integer> tierCounts = new HashMap<>();
        tierCounts.merge(tier0, 1, Integer::sum);
        tierCounts.merge(tier1, 1, Integer::sum);
        tierCounts.merge(tier2, 1, Integer::sum);
        double random = level.random.nextDouble() * 3.0;
        double cumulative = 0.0;
        for (Map.Entry<Integer, Integer> entry : tierCounts.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) return entry.getKey();
        }
        return tier0;
    }

    private void consumeAndOutput(ItemStack result) {
        cardHandler.setStackInSlot(3, result);
        cardHandler.setStackInSlot(0, ItemStack.EMPTY);
        cardHandler.setStackInSlot(1, ItemStack.EMPTY);
        cardHandler.setStackInSlot(2, ItemStack.EMPTY);
        broadcastFullState();
    }

    // ==================== ⭐ 模组分解为内融核心 ====================

    /**
     * 检测三个输入槽是否恰好只放了一张可分解的模组卡。
     * <p>
     * 仅武器/战甲模组可分解，安魂卡不可分解；放入卡的数量必须为 1。
     *
     * @param slot0 输入槽0物品
     * @param slot1 输入槽1物品
     * @param slot2 输入槽2物品
     * @return 那张可分解模组卡所在的槽位索引（0/1/2），不满足条件返回 -1
     */
    private int findSingleDecomposableCardSlot(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        ItemStack[] slots = {slot0, slot1, slot2};
        int filledCount = 0;
        int cardSlot = -1;
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].isEmpty()) {
                filledCount++;
                cardSlot = i;
            }
        }
        // 必须恰好一张
        if (filledCount != 1 || cardSlot < 0) {
            return -1;
        }
        ItemStack card = slots[cardSlot];
        // 必须是武器/战甲模组（安魂卡不可分解）
        if (!(card.getItem() instanceof AbstractItemModule) && !(card.getItem() instanceof AbstractWarframeModule)) {
            return -1;
        }
        // 数量必须为 1，避免堆叠卡的分解歧义
        if (card.getCount() != 1) {
            return -1;
        }
        return cardSlot;
    }

    /**
     * 处理单张模组卡的分解预览。
     * <p>
     * 将对应数量的内融核心放入输出槽作为预览并标记分解模式；
     * 此时不消耗输入卡，待玩家取走输出的内融核心后（{@link #onDecomposeResultTaken()}）才消耗。
     *
     * @param cardSlot 待分解模组卡所在的输入槽索引
     */
    private void processDecompose(int cardSlot) {
        try {
            ItemStack card = cardHandler.getStackInSlot(cardSlot);
            int beanCount = getDecomposeBeanCount(card);
            if (beanCount <= 0) {
                return;
            }
            ItemStack beans = new ItemStack(KuvaLichItems.ENDO.get(), beanCount);
            cardHandler.setStackInSlot(3, beans);
            decomposeMode = true;
            broadcastFullState();
            LogUtil.debugEvent("模组分解预览", card.getHoverName().getString(),
                    "分解为内融核心 x" + beanCount);
        } catch (Exception e) {
            LogUtil.error("模组分解预览失败", e);
        }
    }

    /**
     * 计算单张模组卡分解可获得的内融核心（豆子）数量。
     * <p>
     * 计算公式（总量上限 64）：
     * <pre>
     *   品质基础值 + 等级加成 + 紫卡循环加成
     * </pre>
     * 品质基础值：铜卡 1 / 银卡 2 / 金卡 4 / Prime 与紫卡 8。<br>
     * 等级加成：仅等级系统开启时计算，等于 (当前等级 - 1)，即每升一级多 1 个。<br>
     * 紫卡循环加成：紫卡（武器/战甲）每循环 1 次多 1 个，未揭示的紫卡不计循环。
     *
     * @param moduleStack 待分解的模组物品栈
     * @return 分解可获得的内融核心数量，非已知模组品质或空栈返回 0
     */
    private int getDecomposeBeanCount(ItemStack moduleStack) {
        if (moduleStack == null || moduleStack.isEmpty()) {
            return 0;
        }
        Item item = moduleStack.getItem();

        // ===== 品质基础值 =====
        int base;
        if (item instanceof ItemRivenModule || item instanceof WarframeRivenModule
                || item instanceof ItemPrimeModule || item instanceof WarframePrimeModule) {
            base = 8; // Prime 与紫卡
        } else if (item instanceof ItemRareModule || item instanceof WarframeRareModule) {
            base = 4; // 金卡
        } else if (item instanceof ItemUncommonModule || item instanceof WarframeUncommonModule) {
            base = 2; // 银卡
        } else if (item instanceof ItemCommonModule || item instanceof WarframeCommonModule) {
            base = 1; // 铜卡
        } else {
            return 0; // 非已知模组品质，不可分解
        }

        // ===== 等级加成：每升一级多 1 个（仅等级系统开启时计算）=====
        int levelBonus = 0;
        if (ModuleLevelHelper.isLevelSystemEnabled()) {
            int level = ModuleLevelHelper.getModuleLevel(moduleStack);
            levelBonus = Math.max(0, level - 1);
        }

        // ===== 紫卡循环加成 + 倾向性加成（均未揭示不计）=====
        int cycleBonus = 0;
        int trendBonus = 0;
        if (item instanceof ItemRivenModule && !ItemRivenModule.isRandom(moduleStack)) {
            cycleBonus = Math.max(0, ItemRivenModule.getCycle(moduleStack));
            trendBonus = getRivenTrendBonus(ItemRivenModule.getTrend(moduleStack));
        } else if (item instanceof WarframeRivenModule && !WarframeRivenModule.isRandom(moduleStack)) {
            cycleBonus = Math.max(0, WarframeRivenModule.getCycle(moduleStack));
            trendBonus = getRivenTrendBonus(WarframeRivenModule.getTrend(moduleStack));
        }

        // ===== 总量上限 64 =====
        int total = base + levelBonus + cycleBonus + trendBonus;
        return Math.min(total, 64);
    }

    /**
     * 计算紫卡（裂罅）倾向性对应的额外内融核心加成。
     * <p>
     * 倾向性 1 不加成；倾向性 ≥2 时按 2^(trend-1) 递增：
     * trend2 → +2，trend3 → +4，trend4 → +8，trend5 → +16。
     * 该加成与品质基础、等级、循环加成相互叠加，最终仍受总量上限 64 约束。
     *
     * @param trend 倾向性等级（getTrend 返回值）
     * @return 倾向性额外加成，trend ≤ 1 返回 0
     */
    private int getRivenTrendBonus(int trend) {
        if (trend < 2) {
            return 0;
        }
        // 钳制指数上限，防止极端倾向值导致移位溢出（最终仍受总量上限 64 约束）
        int exp = Math.min(trend - 1, 16);
        return 1 << exp;
    }

    /**
     * 取走分解产物（内融核心）后的回调：消耗对应的输入模组卡。
     * <p>
     * 仅当处于分解模式且输出槽已清空（内融核心已全部取走）时才消耗输入卡，
     * 避免背包装不下时部分取走却误消耗卡。
     * 三合一产物 decomposeMode 为 false，调用此方法会直接返回，不受影响。
     */
    public void onDecomposeResultTaken() {
        if (!decomposeMode) {
            return;
        }
        // 内融核心尚未全部取走时不消耗输入卡
        if (!cardHandler.getStackInSlot(3).isEmpty()) {
            return;
        }
        // 清空唯一的输入模组卡（即被分解的那张）
        for (int i = 0; i < 3; i++) {
            if (!cardHandler.getStackInSlot(i).isEmpty()) {
                cardHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        decomposeMode = false;
        broadcastFullState();
        LogUtil.debug("模组分解完成，已消耗模组卡");
    }

    public void broadcastFullState() {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.containerMenu.broadcastChanges();
        }
    }

    /**
     * ⭐ 关闭时返还物品：使用 Inventory.add() 优先快捷栏。
     * 分解预览中的内融核心（输出槽）不返还，因对应模组卡仍在输入槽且会被返还。
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            for (int i = 0; i < 4; i++) {
                ItemStack stack = cardHandler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    // ⭐ 分解预览的内融核心（输出槽）不返还，避免玩家既拿回模组卡又白嫖内融核心
                    if (i == 3 && decomposeMode) {
                        cardHandler.setStackInSlot(i, ItemStack.EMPTY);
                        continue;
                    }
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                    cardHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
            decomposeMode = false;
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    public static class InputSlot extends SlotItemHandler {
        public InputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            // 安魂卡：用于三张安魂卡合成
            if (stack.getItem() instanceof AbstractRequiemCard) return super.mayPlace(stack);
            // ⭐ 所有武器/战甲模组均可放入：单张→分解为内融核心，三张→三合一。
            //    三合一的品质限制（Prime/紫卡/跨品质开关）仍在 processCrafting 内部判定，此处不再前置拦截，
            //    否则默认配置下 Prime/紫卡卡片无法放入，也就无法被分解。
            if (stack.getItem() instanceof AbstractItemModule || stack.getItem() instanceof AbstractWarframeModule) {
                return super.mayPlace(stack);
            }
            return false;
        }
    }

    public static class OutputSlot extends SlotItemHandler {
        private final MenuRequiemRecast menu;

        public OutputSlot(MenuRequiemRecast menu, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) { return false; }

        @Override
        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            // ⭐ 取走分解产物时消耗输入模组卡（三合一产物 decomposeMode=false，不受影响）
            menu.onDecomposeResultTaken();
            super.onTake(player, stack);
        }
    }
}