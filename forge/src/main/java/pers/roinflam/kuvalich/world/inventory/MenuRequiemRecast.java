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
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 安魂之铸菜单（1.20.1版本，业务逻辑100%不变）
 * Requiem Recast Menu (1.20.1 version, business logic 100% unchanged)
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
     * 每次同步时检测合成（业务逻辑100%不变）
     * Check crafting on sync (business logic 100% unchanged)
     */
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
     * 处理合成（业务逻辑100%不变）
     * Process crafting (business logic 100% unchanged)
     */
    private void processCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        // 情况1：3张安魂卡 / Case 1: 3 Requiem Cards
        if (slot0.getItem() instanceof AbstractRequiemCard &&
                slot1.getItem() instanceof AbstractRequiemCard &&
                slot2.getItem() instanceof AbstractRequiemCard) {
            processRequiemCardCrafting();
            return;
        }

        // 情况2：3个同级模组 / Case 2: 3 same-tier modules
        if (slot0.getItem() instanceof AbstractItemModule &&
                slot1.getItem() instanceof AbstractItemModule &&
                slot2.getItem() instanceof AbstractItemModule) {

            if (slot0.getItem() instanceof ItemRivenModule ||
                    slot1.getItem() instanceof ItemRivenModule ||
                    slot2.getItem() instanceof ItemRivenModule) {
                return;
            }

            processModuleCrafting(slot0);
        }
    }

    /**
     * 安魂卡合成（业务逻辑100%不变）
     * Requiem Card crafting (business logic 100% unchanged)
     */
    private void processRequiemCardCrafting() {
        try {
            int randomId = level.random.nextInt(8);
            ItemStack result = new ItemStack(AbstractRequiemCard.getCard(randomId));

            // 事务式修改：一次性设置所有状态 / Transactional change
            cardHandler.setStackInSlot(3, result);
            cardHandler.setStackInSlot(0, ItemStack.EMPTY);
            cardHandler.setStackInSlot(1, ItemStack.EMPTY);
            cardHandler.setStackInSlot(2, ItemStack.EMPTY);

            // 强制同步所有槽位 / Force sync all slots
            broadcastFullState();

            LogUtil.debugEvent("安魂卡合成", "成功", result.getHoverName().getString());
        } catch (Exception e) {
            LogUtil.error("安魂卡合成失败", e);
        }
    }

    /**
     * 模组合成（业务逻辑100%不变）
     * Module crafting (business logic 100% unchanged)
     */
    private void processModuleCrafting(ItemStack slot0) {
        try {
            ItemStack result = null;

            if (slot0.getItem() instanceof ItemCommonModule) {
                result = ItemCommonModule.getRandomModule();
            } else if (slot0.getItem() instanceof ItemUncommonModule) {
                result = ItemUncommonModule.getRandomModule();
            } else if (slot0.getItem() instanceof ItemRareModule) {
                result = ItemRareModule.getRandomModule();
            } else if (slot0.getItem() instanceof ItemPrimeModule) {
                result = ItemPrimeModule.getRandomModule();
            }

            if (result != null) {
                // 事务式修改 / Transactional change
                cardHandler.setStackInSlot(3, result);
                cardHandler.setStackInSlot(0, ItemStack.EMPTY);
                cardHandler.setStackInSlot(1, ItemStack.EMPTY);
                cardHandler.setStackInSlot(2, ItemStack.EMPTY);

                // 强制同步 / Force sync
                broadcastFullState();

                LogUtil.debugEvent("模组合成", "成功", result.getHoverName().getString());
            }
        } catch (Exception e) {
            LogUtil.error("模组合成失败", e);
        }
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
     * 输入槽位（普通槽位，不触发合成）
     * Input slot (normal slot, doesn't trigger crafting)
     */
    public static class InputSlot extends SlotItemHandler {
        public InputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (stack.getItem() instanceof AbstractRequiemCard) {
                return super.mayPlace(stack);
            }
            if (stack.getItem() instanceof AbstractItemModule &&
                    !(stack.getItem() instanceof ItemRivenModule)) {
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
        public OutputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }
    }
}