package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
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
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.Endo;
import pers.roinflam.kuvalich.item.Forma;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.LichReliquary;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 安魂之融菜单（1.20.1版本）
 * Requiem Evolve Menu
 *
 * 支持功能：
 * 1. 武器融合（两把赤毒武器 → 新武器）
 * 2. 武器裂罅循环（裂罅模组 + 赤毒 → 新裂罅）
 * 3. 战甲裂罅循环（战甲裂罅模组 + 赤毒 → 新裂罅）
 * 4. 添加基础属性（未开光物品 + 裂罅碎块 → 开光物品）
 * 5. Forma洗面板（已开光非赤毒未锁定武器 + 塑形块 → 直接消耗并重新随机面板）
 * 6. TACZ枪械强化（TACZ枪械 + 玄骸之遗 → 永久增强基础伤害）
 * 7. ⭐ 模组升级（模组 + 内融核心 → 预览升级结果，取出时消耗材料）
 *    ⭐ 升级费用根据品质缩放：铜25% / 银50% / 金75% / Prime&裂罅100%
 */
public class MenuRequiemEvolve extends AbstractContainerMenu {

    private final Level level;
    private final BlockPos pos;
    private final Player player;

    private final ItemStackHandler weaponHandler;
    private final ItemStackHandler materialHandler;
    private final ItemStackHandler resultHandler;

    /** 武器融合预览模式 */
    private boolean evolveMode = false;
    /** 裂罅循环预览模式 */
    private boolean cycleMode = false;
    /** ⭐ 模组升级预览模式 */
    private boolean upgradeMode = false;
    /** 基础属性刚处理完标记（开光/Forma/TACZ强化，立即消耗型） */
    private boolean baseAttributeJustProcessed = false;

    private ItemStack lastWeapon = ItemStack.EMPTY;
    private ItemStack lastMaterial = ItemStack.EMPTY;

    public MenuRequiemEvolve(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_EVOLVE.get(), windowId);
        this.level = level;
        this.pos = pos;
        this.player = playerInventory.player;
        this.weaponHandler = new ItemStackHandler(1);
        this.materialHandler = new ItemStackHandler(1);
        this.resultHandler = new ItemStackHandler(1);

        this.addSlot(new WeaponSlot(this.weaponHandler, 0, 31, 32));
        this.addSlot(new MaterialSlot(this.materialHandler, 0, 80, 32));
        this.addSlot(new ResultSlot(this, this.resultHandler, 0, 131, 32));

        for (int i = 0; i < 9; i++) { this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 171 - 10)); }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) { this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 113 - 10 + row * 18)); }
        }
    }

    // ==================== Shift+点击 ====================

    /**
     * 快速转移物品（Shift+点击）
     * <p>
     * ⭐ 修复：材料类物品直接路由到材料槽，跳过武器槽判断。
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) { return itemstack; }
        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        // 从容器转移到玩家背包
        if (index < 3) {
            if (!this.moveItemStackTo(slotStack, 3, 39, true)) { return ItemStack.EMPTY; }
        }
        // 从玩家背包转移到容器
        else {
            boolean transferred = false;

            if (isMaterialOnly(slotStack)) {
                // ⭐ 材料类物品直接进材料槽，跳过武器槽
                transferred = this.moveItemStackTo(slotStack, 1, 2, false);
            } else {
                // 其他物品：先尝试武器槽，再尝试材料槽
                if (this.moveItemStackTo(slotStack, 0, 1, false)) { transferred = true; }
                else if (this.moveItemStackTo(slotStack, 1, 2, false)) { transferred = true; }
            }

            if (!transferred) { return ItemStack.EMPTY; }
        }

        if (slotStack.isEmpty()) { slot.set(ItemStack.EMPTY); } else { slot.setChanged(); }
        if (slotStack.getCount() == itemstack.getCount()) { return ItemStack.EMPTY; }
        slot.onTake(player, slotStack);
        return itemstack;
    }

    /**
     * 判断物品是否为纯材料类（只应放入材料槽，不应放入武器槽）
     */
    private static boolean isMaterialOnly(ItemStack stack) {
        return stack.getItem() instanceof Kuva
                || stack.getItem() instanceof Endo
                || stack.getItem() instanceof RivenSliver
                || stack.getItem() instanceof Forma
                || stack.getItem() instanceof LichReliquary;
    }

    // ==================== 同步（三重保险）====================

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide) { checkAndProcessCrafting(); }
        super.broadcastChanges();
    }

    @Override
    public void broadcastFullState() {
        if (!level.isClientSide) { checkAndProcessCrafting(); }
        super.broadcastFullState();
    }

    private void forceContainerSlotSync() {
        if (!(player instanceof ServerPlayer serverPlayer)) { return; }
        int stateId = this.incrementStateId();
        for (int i = 0; i < 3; i++) {
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                    this.containerId, stateId, i, this.slots.get(i).getItem().copy()));
        }
    }

    // ==================== 合成检测 ====================

    private void checkAndProcessCrafting() {
        ItemStack currentWeapon = weaponHandler.getStackInSlot(0);
        ItemStack currentMaterial = materialHandler.getStackInSlot(0);
        ItemStack currentResult = resultHandler.getStackInSlot(0);
        boolean inputChanged = !ItemStack.matches(currentWeapon, lastWeapon) || !ItemStack.matches(currentMaterial, lastMaterial);

        if (inputChanged && !currentResult.isEmpty() && (evolveMode || cycleMode || upgradeMode)) {
            resultHandler.setStackInSlot(0, ItemStack.EMPTY);
            currentResult = ItemStack.EMPTY;
            evolveMode = false;
            cycleMode = false;
            upgradeMode = false;
        }

        if (currentWeapon.isEmpty() || currentMaterial.isEmpty()) {
            if (!currentResult.isEmpty()) {
                if (!baseAttributeJustProcessed) { resultHandler.setStackInSlot(0, ItemStack.EMPTY); evolveMode = false; cycleMode = false; upgradeMode = false; }
            }
            if (currentResult.isEmpty() && baseAttributeJustProcessed) { baseAttributeJustProcessed = false; }
            lastWeapon = weaponHandler.getStackInSlot(0).copy();
            lastMaterial = materialHandler.getStackInSlot(0).copy();
            return;
        }

        if (inputChanged && currentResult.isEmpty()) {
            if (baseAttributeJustProcessed) { baseAttributeJustProcessed = false; }
            processCrafting(currentWeapon, currentMaterial);
            lastWeapon = weaponHandler.getStackInSlot(0).copy();
            lastMaterial = materialHandler.getStackInSlot(0).copy();
            forceContainerSlotSync();
        }
    }

    private void processCrafting(ItemStack weaponStack, ItemStack materialStack) {
        if (KuvaWeaponUtil.hasType(weaponStack) && KuvaWeaponUtil.hasType(materialStack)) {
            processWeaponEvolve(weaponStack, materialStack); return;
        }
        if (weaponStack.getItem() instanceof ItemRivenModule && materialStack.getItem() instanceof Kuva) {
            processItemRivenCycle(weaponStack, materialStack); return;
        }
        if (weaponStack.getItem() instanceof WarframeRivenModule && materialStack.getItem() instanceof Kuva) {
            processWarframeRivenCycle(weaponStack, materialStack); return;
        }
        if (materialStack.getItem() instanceof Forma && WeaponModuleHandler.hasBase(weaponStack)
                && !KuvaWeaponUtil.hasType(weaponStack) && !WeaponModuleHandler.isFormaLocked(weaponStack)
                && weaponStack.getCount() == 1) {
            processFormaReroll(weaponStack, materialStack); return;
        }
        if (!weaponStack.isEmpty() && materialStack.getItem() instanceof RivenSliver) {
            processAddBaseAttribute(weaponStack, materialStack); return;
        }
        if (TaczGunEnhanceUtil.isLichReliquary(materialStack) && TaczGunEnhanceUtil.isEnhanceableTaczGun(weaponStack)
                && weaponStack.getCount() == 1) {
            processGunEnhance(weaponStack, materialStack); return;
        }
        if (ModuleLevelHelper.isLevelSystemEnabled()
                && materialStack.getItem() instanceof Endo
                && (weaponStack.getItem() instanceof AbstractModule)
                && !AbstractModule.isRandom(weaponStack)
                && !ModuleLevelHelper.isMaxLevel(weaponStack)
                && weaponStack.getCount() == 1) {
            processModuleUpgrade(weaponStack, materialStack);
            return;
        }
    }

    // ==================== 合成处理方法 ====================

    private void processWeaponEvolve(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();
            int weaponLevel = KuvaWeaponUtil.getNumber(weaponStack);
            int materialLevel = KuvaWeaponUtil.getNumber(materialStack);
            int newLevel = Math.max(weaponLevel, materialLevel);
            newLevel = (int) (newLevel * (1 + ModConfig.KUVA_LICH.upgradeMultiplier.get()));
            newLevel = Math.min(newLevel, ModConfig.KUVA_LICH.upgradeLimit.get());
            KuvaWeaponUtil.setType(newWeapon, KuvaWeaponUtil.getType(materialStack));
            KuvaWeaponUtil.setNumber(newWeapon, newLevel);
            resultHandler.setStackInSlot(0, newWeapon);
            evolveMode = true;
        } catch (Exception e) { LogUtil.error("武器融合失败", e); }
    }

    private void processItemRivenCycle(ItemStack weaponStack, ItemStack materialStack) {
        if (ItemRivenModule.isRandom(weaponStack)) { return; }
        try {
            int cycleCount = ItemRivenModule.getCycle(weaponStack);
            int trend = ItemRivenModule.getTrend(weaponStack);
            int kuvaSpend = Math.min(cycleCount, 8);
            kuvaSpend += Math.pow(trend, 2) - Math.pow(trend - 1, 2);
            if (materialStack.getCount() >= kuvaSpend) {
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                materialHandler.setStackInSlot(0, newMaterial);
                int rivenMode = ItemRivenModule.getRivenMode(weaponStack);
                ItemStack newRiven = ItemRivenModule.cycleModule(trend, cycleCount, rivenMode);
                if (ModuleLevelHelper.isLevelSystemEnabled()) {
                    int originalLevel = ModuleLevelHelper.getModuleLevel(weaponStack);
                    ModuleLevelHelper.setModuleLevel(newRiven, originalLevel);
                }
                resultHandler.setStackInSlot(0, newRiven);
                ItemRivenModule.setCycle(weaponStack, cycleCount + 1);
                cycleMode = true;
            }
        } catch (Exception e) { LogUtil.error("武器裂罅循环失败", e); }
    }

    private void processWarframeRivenCycle(ItemStack weaponStack, ItemStack materialStack) {
        if (WarframeRivenModule.isRandom(weaponStack)) { return; }
        try {
            int cycleCount = WarframeRivenModule.getCycle(weaponStack);
            int trend = WarframeRivenModule.getTrend(weaponStack);
            int kuvaSpend = Math.min(cycleCount, 8);
            kuvaSpend += Math.pow(trend, 2);
            if (materialStack.getCount() >= kuvaSpend) {
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                materialHandler.setStackInSlot(0, newMaterial);
                ItemStack newRiven = WarframeRivenModule.cycleModule(trend, cycleCount);
                if (ModuleLevelHelper.isLevelSystemEnabled()) {
                    int originalLevel = ModuleLevelHelper.getModuleLevel(weaponStack);
                    ModuleLevelHelper.setModuleLevel(newRiven, originalLevel);
                }
                resultHandler.setStackInSlot(0, newRiven);
                WarframeRivenModule.setCycle(weaponStack, cycleCount + 1);
                cycleMode = true;
            }
        } catch (Exception e) { LogUtil.error("战甲裂罅循环失败", e); }
    }

    private void processFormaReroll(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();
            if (!WeaponModuleHandler.clearBaseAttribute(newWeapon)) { return; }
            WeaponModuleHandler.setBaseAttribute(newWeapon);
            if (ModConfig.KUVA_LICH.formaLockEnabled.get()) {
                double lockChance = ModConfig.KUVA_LICH.formaLockChance.get();
                if (lockChance > 0) {
                    double roll = level.random.nextDouble() * 100.0;
                    if (roll < lockChance) { WeaponModuleHandler.setFormaLocked(newWeapon); }
                }
            }
            ItemStack newMaterial = materialStack.copy();
            newMaterial.shrink(1);
            materialHandler.setStackInSlot(0, newMaterial);
            resultHandler.setStackInSlot(0, newWeapon);
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            baseAttributeJustProcessed = true;
        } catch (Exception e) { LogUtil.error("Forma洗面板失败", e); }
    }

    private void processAddBaseAttribute(ItemStack weaponStack, ItemStack materialStack) {
        if (!WeaponModuleHandler.hasBase(weaponStack) && weaponStack.getCount() == 1) {
            try {
                ItemStack newWeapon = weaponStack.copy();
                WeaponModuleHandler.setBaseAttribute(newWeapon);
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - 1);
                baseAttributeJustProcessed = true;
                resultHandler.setStackInSlot(0, newWeapon);
                materialHandler.setStackInSlot(0, newMaterial);
                weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            } catch (Exception e) { LogUtil.error("添加基础属性失败", e); baseAttributeJustProcessed = false; }
        }
    }

    private void processGunEnhance(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newGun = weaponStack.copy();
            int oldCount = TaczGunEnhanceUtil.getEnhanceCount(newGun);
            TaczGunEnhanceUtil.setEnhanceCount(newGun, oldCount + 1);
            ItemStack newMaterial = materialStack.copy();
            newMaterial.shrink(1);
            materialHandler.setStackInSlot(0, newMaterial);
            resultHandler.setStackInSlot(0, newGun);
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            baseAttributeJustProcessed = true;
        } catch (Exception e) { LogUtil.error("TACZ枪械强化失败", e); baseAttributeJustProcessed = false; }
    }

    private void processModuleUpgrade(ItemStack moduleStack, ItemStack endoStack) {
        try {
            int currentLevel = ModuleLevelHelper.getModuleLevel(moduleStack);
            int cost = ModuleLevelHelper.getUpgradeCost(currentLevel, moduleStack);
            if (cost <= 0 || endoStack.getCount() < cost) { return; }

            ItemStack upgradedModule = moduleStack.copy();
            int newLevel = currentLevel + 1;
            ModuleLevelHelper.setModuleLevel(upgradedModule, newLevel);

            resultHandler.setStackInSlot(0, upgradedModule);
            upgradeMode = true;

            LogUtil.debugEvent("模组升级预览", moduleStack.getHoverName().getString(),
                    "等级: " + currentLevel + " → " + newLevel + ", 所需内融核心: " + cost);
        } catch (Exception e) {
            LogUtil.error("模组升级预览失败", e);
        }
    }

    // ==================== 结果取走回调 ====================

    public void onResultTaken() {
        if (evolveMode) {
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            materialHandler.setStackInSlot(0, ItemStack.EMPTY);
            evolveMode = false;
        }
        if (cycleMode) {
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            cycleMode = false;
        }
        if (upgradeMode) {
            ItemStack moduleStack = weaponHandler.getStackInSlot(0);
            ItemStack endoStack = materialHandler.getStackInSlot(0);
            int currentLevel = ModuleLevelHelper.getModuleLevel(moduleStack);
            int cost = ModuleLevelHelper.getUpgradeCost(currentLevel, moduleStack);

            if (cost > 0 && endoStack.getCount() >= cost) {
                endoStack.shrink(cost);
                materialHandler.setStackInSlot(0, endoStack);
            }

            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);

            int newLevel = currentLevel + 1;
            String masteryKey = ModuleLevelHelper.getMasteryKey(moduleStack);
            if (!masteryKey.isEmpty()) {
                player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                    requiemCard.updateMasteryLevel(masteryKey, newLevel);
                });
            }

            LogUtil.debugEvent("模组升级确认", moduleStack.getHoverName().getString(),
                    "等级: " + currentLevel + " → " + newLevel + ", 消耗内融核心: " + cost);
            upgradeMode = false;
        }
        if (baseAttributeJustProcessed) {
            baseAttributeJustProcessed = false;
        }
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (!level.isClientSide) {
            returnSlotToPlayer(weaponHandler, 0, player);
            returnSlotToPlayer(materialHandler, 0, player);
            ItemStack resultStack = resultHandler.getStackInSlot(0);
            if (!resultStack.isEmpty()) {
                if (evolveMode || cycleMode || upgradeMode) {
                    resultHandler.setStackInSlot(0, ItemStack.EMPTY);
                } else {
                    returnSlotToPlayer(resultHandler, 0, player);
                }
            }
            evolveMode = false;
            cycleMode = false;
            upgradeMode = false;
            baseAttributeJustProcessed = false;
        }
    }

    /**
     * 将槽位中的物品返还给玩家
     * ⭐ 使用 Inventory.add() 优先填充快捷栏，满了掉脚下
     */
    private void returnSlotToPlayer(ItemStackHandler handler, int slot, Player player) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) && player.blockPosition().distSqr(this.pos) <= 64;
    }

    // ============================== 内部槽位类 ==============================

    public static class WeaponSlot extends SlotItemHandler {
        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (KuvaWeaponUtil.hasType(stack)) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof ItemRivenModule || stack.getItem() instanceof WarframeRivenModule) { return super.mayPlace(stack); }
            if (!WeaponModuleHandler.hasBase(stack) && stack.getCount() == 1) { return super.mayPlace(stack); }
            if (WeaponModuleHandler.hasBase(stack) && stack.getCount() == 1) { return super.mayPlace(stack); }
            try {
                if (TaczGunEnhanceUtil.isTaczGun(stack) && stack.getCount() == 1) { return super.mayPlace(stack); }
            } catch (NoClassDefFoundError ignored) {}
            if (ModuleLevelHelper.isLevelSystemEnabled()
                    && stack.getItem() instanceof AbstractModule
                    && !AbstractModule.isRandom(stack)
                    && !ModuleLevelHelper.isMaxLevel(stack)
                    && stack.getCount() == 1) {
                return super.mayPlace(stack);
            }
            return false;
        }
    }

    public static class MaterialSlot extends SlotItemHandler {
        public MaterialSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (KuvaWeaponUtil.hasType(stack)) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof Kuva) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof RivenSliver) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof Forma) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof LichReliquary) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof Endo) { return super.mayPlace(stack); }
            return false;
        }
    }

    public static class ResultSlot extends SlotItemHandler {
        private final MenuRequiemEvolve menu;
        public ResultSlot(MenuRequiemEvolve menu, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) { return false; }

        @Override
        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            menu.onResultTaken();
            super.onTake(player, stack);
        }
    }
}
