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
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 安魂之融菜单（1.20.1版本，业务逻辑100%不变）
 * Requiem Evolve Menu (1.20.1 version, business logic 100% unchanged)
 */
public class MenuRequiemEvolve extends AbstractContainerMenu {

    private final Level level;
    private final BlockPos pos;
    private final Player player;

    private final ItemStackHandler weaponHandler;
    private final ItemStackHandler materialHandler;
    private final ItemStackHandler resultHandler;

    private boolean evolveMode = false;
    private boolean cycleMode = false;
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

        // 添加玩家背包槽位 / Add player inventory slots
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 171 - 10));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 113 - 10 + row * 18));
            }
        }

        LogUtil.debugEvent("安魂之融菜单创建", player.getName().getString(), "位置: " + pos.toString());
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

        if (index < 3) {
            if (index == 2) {
                return ItemStack.EMPTY;
            }

            if (!this.moveItemStackTo(slotStack, 3, 39, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean transferred = false;

            if (this.moveItemStackTo(slotStack, 0, 1, false)) {
                transferred = true;
            } else if (this.moveItemStackTo(slotStack, 1, 2, false)) {
                transferred = true;
            }

            if (!transferred) {
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

    /**
     * 检测并处理合成（业务逻辑100%不变）
     * Check and process crafting (business logic 100% unchanged)
     */
    private void checkAndProcessCrafting() {
        ItemStack currentWeapon = weaponHandler.getStackInSlot(0);
        ItemStack currentMaterial = materialHandler.getStackInSlot(0);
        ItemStack currentResult = resultHandler.getStackInSlot(0);

        boolean inputChanged = !ItemStack.matches(currentWeapon, lastWeapon) ||
                !ItemStack.matches(currentMaterial, lastMaterial);

        if (currentWeapon.isEmpty() || currentMaterial.isEmpty()) {
            if (!currentResult.isEmpty()) {
                if (!baseAttributeJustProcessed) {
                    resultHandler.setStackInSlot(0, ItemStack.EMPTY);
                    evolveMode = false;
                    cycleMode = false;
                }
            }

            if (currentResult.isEmpty() && baseAttributeJustProcessed) {
                baseAttributeJustProcessed = false;
            }

            lastWeapon = currentWeapon.copy();
            lastMaterial = currentMaterial.copy();
            return;
        }

        if (inputChanged && currentResult.isEmpty()) {
            if (baseAttributeJustProcessed) {
                baseAttributeJustProcessed = false;
            }

            processCrafting(currentWeapon, currentMaterial);
            lastWeapon = currentWeapon.copy();
            lastMaterial = currentMaterial.copy();
        }
    }

    /**
     * 处理合成（业务逻辑100%不变）
     * Process crafting (business logic 100% unchanged)
     */
    private void processCrafting(ItemStack weaponStack, ItemStack materialStack) {
        if (KuvaWeapon.hasType(weaponStack) && KuvaWeapon.hasType(materialStack)) {
            processWeaponEvolve(weaponStack, materialStack);
            return;
        }

        if (weaponStack.getItem() instanceof ItemRivenModule && materialStack.getItem() instanceof Kuva) {
            processItemRivenCycle(weaponStack, materialStack);
            return;
        }

        if (weaponStack.getItem() instanceof WarframeRivenModule && materialStack.getItem() instanceof Kuva) {
            processWarframeRivenCycle(weaponStack, materialStack);
            return;
        }

        if (!weaponStack.isEmpty() && materialStack.getItem() instanceof RivenSliver) {
            processAddBaseAttribute(weaponStack, materialStack);
            return;
        }
    }

    // 业务逻辑方法保持100%不变，仅修改API调用
    // Business logic methods 100% unchanged, only API calls modified

    private void processWeaponEvolve(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();

            int weaponLevel = KuvaWeapon.getNumber(weaponStack);
            int materialLevel = KuvaWeapon.getNumber(materialStack);
            int newLevel = Math.max(weaponLevel, materialLevel);

            newLevel = (int) (newLevel * (1 + ModConfig.KUVA_LICH.upgradeMultiplier.get()));
            newLevel = Math.min(newLevel, ModConfig.KUVA_LICH.upgradeLimit.get());

            KuvaWeapon.setType(newWeapon, KuvaWeapon.getType(materialStack));
            KuvaWeapon.setNumber(newWeapon, newLevel);

            resultHandler.setStackInSlot(0, newWeapon);
            evolveMode = true;
        } catch (Exception e) {
            LogUtil.error("武器融合失败", e);
        }
    }

    private void processItemRivenCycle(ItemStack weaponStack, ItemStack materialStack) {
        if (ItemRivenModule.isRandom(weaponStack)) {
            return;
        }

        try {
            int cycleCount = ItemRivenModule.getCycle(weaponStack);
            int trend = ItemRivenModule.getTrend(weaponStack);
            int kuvaSpend = Math.min(cycleCount, 8);
            kuvaSpend += Math.pow(trend, 2) - Math.pow(trend - 1, 2);

            if (materialStack.getCount() >= kuvaSpend) {
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                materialHandler.setStackInSlot(0, newMaterial);

                boolean isMelee = ItemRivenModule.isMelee(weaponStack);
                ItemStack newRiven = ItemRivenModule.cycleModule(trend, cycleCount, isMelee);
                resultHandler.setStackInSlot(0, newRiven);

                ItemRivenModule.setCycle(weaponStack, cycleCount + 1);
                cycleMode = true;

                LogUtil.debugEvent("武器裂罅循环", "消耗赤毒", String.valueOf(kuvaSpend));
            }
        } catch (Exception e) {
            LogUtil.error("武器裂罅循环失败", e);
        }
    }

    private void processWarframeRivenCycle(ItemStack weaponStack, ItemStack materialStack) {
        if (WarframeRivenModule.isRandom(weaponStack)) {
            return;
        }

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
                resultHandler.setStackInSlot(0, newRiven);

                WarframeRivenModule.setCycle(weaponStack, cycleCount + 1);
                cycleMode = true;

                LogUtil.debugEvent("战甲裂罅循环", "消耗赤毒", String.valueOf(kuvaSpend));
            }
        } catch (Exception e) {
            LogUtil.error("战甲裂罅循环失败", e);
        }
    }

    private void processAddBaseAttribute(ItemStack weaponStack, ItemStack materialStack) {
        if (!ItemModule.hasBase(weaponStack) && weaponStack.getCount() == 1) {
            try {
                ItemStack newWeapon = weaponStack.copy();
                ItemModule.setBaseAttribute(newWeapon);

                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - 1);

                baseAttributeJustProcessed = true;

                resultHandler.setStackInSlot(0, newWeapon);
                materialHandler.setStackInSlot(0, newMaterial);
                weaponHandler.setStackInSlot(0, ItemStack.EMPTY);

                LogUtil.debugEvent("添加基础属性", weaponStack.getHoverName().getString(), "开光完成");
            } catch (Exception e) {
                LogUtil.error("添加基础属性失败", e);
                baseAttributeJustProcessed = false;
            }
        }
    }

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

        if (baseAttributeJustProcessed) {
            baseAttributeJustProcessed = false;
        }
    }

    /**
     * 菜单关闭时返还物品（⭐已修复：融合/循环模式下结果槽物品不再返还，防止刷物品bug）
     * Return items when menu closes (⭐Fixed: result slot items no longer returned in evolve/cycle mode, prevents duplication bug)
     *
     * 逻辑说明：
     * Logic explanation:
     * - 武器槽（左侧）：总是返还给玩家（原始物品）
     *   Weapon slot (left): always returned to player (original item)
     * - 材料槽（中间）：总是返还给玩家（剩余的材料）
     *   Material slot (middle): always returned to player (remaining material)
     * - 结果槽（右侧）：
     *   Result slot (right):
     *   - evolveMode / cycleMode 下：这是系统生成的预览物品，丢弃不返还
     *     In evolveMode / cycleMode: this is a system-generated preview, discard and do not return
     *   - baseAttribute 模式下：武器槽已被清空，结果是唯一副本，必须返还
     *     In baseAttribute mode: weapon slot is already empty, result is the only copy, must return
     *   - 其他情况：正常返还
     *     Other cases: return normally
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            // 武器槽：总是返还 / Weapon slot: always return
            returnSlotToPlayer(weaponHandler, 0, player);

            // 材料槽：总是返还 / Material slot: always return
            returnSlotToPlayer(materialHandler, 0, player);

            // 结果槽：根据模式决定是否返还 / Result slot: return based on mode
            ItemStack resultStack = resultHandler.getStackInSlot(0);
            if (!resultStack.isEmpty()) {
                if (evolveMode || cycleMode) {
                    // ⭐ 修复核心：融合/循环模式下，结果是生成的预览品，关闭时丢弃
                    // ⭐ Core fix: in evolve/cycle mode, result is a generated preview, discard on close
                    resultHandler.setStackInSlot(0, ItemStack.EMPTY);
                    LogUtil.debugEvent("安魂之融菜单关闭", "丢弃未取走的结果物品",
                            resultStack.getHoverName().getString());
                } else {
                    // 基础属性模式或其他情况：结果是唯一副本，必须返还
                    // Base attribute mode or other: result is the only copy, must return
                    returnSlotToPlayer(resultHandler, 0, player);
                }
            }

            // 重置所有状态标记 / Reset all state flags
            evolveMode = false;
            cycleMode = false;
            baseAttributeJustProcessed = false;
        }
    }

    /**
     * 将指定槽位的物品返还给玩家（优先放入背包，背包满则掉落）
     * Return item from specified slot to player (prefer inventory, drop if full)
     *
     * @param handler 物品处理器 / Item handler
     * @param slot 槽位索引 / Slot index
     * @param player 目标玩家 / Target player
     */
    private void returnSlotToPlayer(ItemStackHandler handler, int slot, Player player) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            // 尝试放入玩家背包（槽位3~38对应玩家背包区域）
            // Try to place in player inventory (slots 3~38 correspond to player inventory area)
            if (!this.moveItemStackTo(stack, 3, 39, true)) {
                // 背包满，掉落到世界 / Inventory full, drop to world
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy()));
            }
            // 清空槽位（防止重复处理）/ Clear slot (prevent duplicate processing)
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    // ============================== Slot classes ==============================

    /**
     * 武器槽位（左侧，接受：赤毒武器/裂罅模组/无基础属性的单个物品）
     * Weapon slot (left side, accepts: Kuva weapons / Riven modules / single items without base attribute)
     */
    public static class WeaponSlot extends SlotItemHandler {
        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (KuvaWeapon.hasType(stack)) {
                return super.mayPlace(stack);
            }
            if (stack.getItem() instanceof ItemRivenModule || stack.getItem() instanceof WarframeRivenModule) {
                return super.mayPlace(stack);
            }
            if (!ItemModule.hasBase(stack) && stack.getCount() == 1) {
                return super.mayPlace(stack);
            }
            return false;
        }
    }

    /**
     * 材料槽位（中间，接受：赤毒武器/赤毒/裂罅碎片）
     * Material slot (middle, accepts: Kuva weapons / Kuva / Riven Slivers)
     */
    public static class MaterialSlot extends SlotItemHandler {
        public MaterialSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (KuvaWeapon.hasType(stack)) {
                return super.mayPlace(stack);
            }
            if (stack.getItem() instanceof Kuva) {
                return super.mayPlace(stack);
            }
            if (stack.getItem() instanceof RivenSliver) {
                return super.mayPlace(stack);
            }
            return false;
        }
    }

    /**
     * 结果槽位（右侧，只读，取走时触发onResultTaken清理逻辑）
     * Result slot (right side, read-only, triggers onResultTaken cleanup on take)
     */
    public static class ResultSlot extends SlotItemHandler {
        private final MenuRequiemEvolve menu;

        public ResultSlot(MenuRequiemEvolve menu, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            menu.onResultTaken();
            super.onTake(player, stack);
        }
    }
}