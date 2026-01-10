package pers.roinflam.kuvalich.inventory.container;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 安魂之铸容器（完全重写版）
 *
 * 核心改进：
 * - 禁用 onSlotChanged 触发合成
 * - 只在 detectAndSendChanges 中统一检测
 * - 事务式修改，避免中间状态
 * - 强制同步客户端

 */
public class ContainerRequiemRecast extends Container {
    private final World world;
    private final BlockPos pos;
    private final EntityPlayer player;

    private final ItemStackHandler card;

    /** 上次检测的输入状态 */
    private ItemStack lastInput0 = ItemStack.EMPTY;
    private ItemStack lastInput1 = ItemStack.EMPTY;
    private ItemStack lastInput2 = ItemStack.EMPTY;

    public ContainerRequiemRecast(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
        this.player = entityPlayer;

        this.card = new ItemStackHandler(4);

        this.addSlotToContainer(new InputSlot(this.card, 0, 31, 54));
        this.addSlotToContainer(new InputSlot(this.card, 1, 80, 54));
        this.addSlotToContainer(new InputSlot(this.card, 2, 129, 54));
        this.addSlotToContainer(new OutputSlot(this.card, 3, 81, 10));

        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 152 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 94 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 112 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 130 - 10));
        }

        LogUtil.debugEvent("安魂之铸容器创建", entityPlayer.getName(), "位置: " + pos.toString());
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot == null || !slot.getHasStack()) {
            return itemstack;
        }

        ItemStack slotStack = slot.getStack();
        itemstack = slotStack.copy();

        if (index < card.getSlots()) {
            if (index == 3) {
                return ItemStack.EMPTY;
            }

            if (!this.mergeItemStack(slotStack, card.getSlots(), 36 + card.getSlots(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (!this.mergeItemStack(slotStack, 0, 3, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        if (slotStack.getCount() == itemstack.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(playerIn, slotStack);

        return itemstack;
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        ItemStack result = super.slotClick(slotId, dragType, clickTypeIn, player);

        if (!player.world.isRemote) {
            checkAndProcessCrafting();
            forceSyncToClient();
        }

        return result;
    }

    /**
     * ✅ 每次同步时检测合成
     */
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (!world.isRemote) {
            checkAndProcessCrafting();
        }
    }

    /**
     * ✅ 检测并处理合成
     */
    private void checkAndProcessCrafting() {
        ItemStack slot0 = card.getStackInSlot(0);
        ItemStack slot1 = card.getStackInSlot(1);
        ItemStack slot2 = card.getStackInSlot(2);
        ItemStack output = card.getStackInSlot(3);

        // 检测输入是否变化
        boolean inputChanged = !ItemStack.areItemStacksEqual(slot0, lastInput0) ||
                !ItemStack.areItemStacksEqual(slot1, lastInput1) ||
                !ItemStack.areItemStacksEqual(slot2, lastInput2);

        // 输出已存在，不处理
        if (!output.isEmpty()) {
            lastInput0 = slot0.copy();
            lastInput1 = slot1.copy();
            lastInput2 = slot2.copy();
            return;
        }

        // 输入不完整，不处理
        if (slot0.isEmpty() || slot1.isEmpty() || slot2.isEmpty()) {
            lastInput0 = slot0.copy();
            lastInput1 = slot1.copy();
            lastInput2 = slot2.copy();
            return;
        }

        // 输入变化了，尝试合成
        if (inputChanged) {
            processCrafting(slot0, slot1, slot2);
            lastInput0 = slot0.copy();
            lastInput1 = slot1.copy();
            lastInput2 = slot2.copy();
        }
    }

    /**
     * ✅ 处理合成（事务式）
     */
    private void processCrafting(ItemStack slot0, ItemStack slot1, ItemStack slot2) {
        // 情况1：3张安魂卡
        if (slot0.getItem() instanceof RequiemCardBase &&
                slot1.getItem() instanceof RequiemCardBase &&
                slot2.getItem() instanceof RequiemCardBase) {
            processRequiemCardCrafting();
            return;
        }

        // 情况2：3个同级模组
        if (slot0.getItem() instanceof ItemModuleBase &&
                slot1.getItem() instanceof ItemModuleBase &&
                slot2.getItem() instanceof ItemModuleBase) {

            if (slot0.getItem() instanceof ItemRivenModule ||
                    slot1.getItem() instanceof ItemRivenModule ||
                    slot2.getItem() instanceof ItemRivenModule) {
                return;
            }

            processModuleCrafting(slot0);
        }
    }

    /**
     * 安魂卡合成
     */
    private void processRequiemCardCrafting() {
        try {
            int randomId = world.rand.nextInt(8);
            ItemStack result = new ItemStack(RequiemCardBase.getCard(randomId));

            // 事务式修改：一次性设置所有状态
            card.setStackInSlot(3, result);
            card.setStackInSlot(0, ItemStack.EMPTY);
            card.setStackInSlot(1, ItemStack.EMPTY);
            card.setStackInSlot(2, ItemStack.EMPTY);

            // 强制同步所有槽位
            forceSyncSlot(0);
            forceSyncSlot(1);
            forceSyncSlot(2);
            forceSyncSlot(3);

            LogUtil.debugEvent("安魂卡合成", "成功", result.getDisplayName());
        } catch (Exception e) {
            LogUtil.error("安魂卡合成失败", e);
        }
    }

    /**
     * 模组合成
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
                // 事务式修改
                card.setStackInSlot(3, result);
                card.setStackInSlot(0, ItemStack.EMPTY);
                card.setStackInSlot(1, ItemStack.EMPTY);
                card.setStackInSlot(2, ItemStack.EMPTY);

                // 强制同步
                forceSyncSlot(0);
                forceSyncSlot(1);
                forceSyncSlot(2);
                forceSyncSlot(3);

                LogUtil.debugEvent("模组合成", "成功", result.getDisplayName());
            }
        } catch (Exception e) {
            LogUtil.error("模组合成失败", e);
        }
    }

    /**
     * ✅ 强制同步单个槽位
     */
    private void forceSyncSlot(int slotIndex) {
        if (player instanceof EntityPlayerMP) {
            ItemStack stack = this.inventorySlots.get(slotIndex).getStack();
            ((EntityPlayerMP) player).connection.sendPacket(
                    new SPacketSetSlot(this.windowId, slotIndex, stack)
            );
        }
    }

    /**
     * ✅ 强制同步所有容器槽位
     */
    private void forceSyncToClient() {
        if (player instanceof EntityPlayerMP) {
            for (int i = 0; i < 4; i++) {
                forceSyncSlot(i);
            }
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            for (int i = 0; i < 4; i++) {
                if (!card.getStackInSlot(i).isEmpty()) {
                    ItemStack stack = card.getStackInSlot(i);
                    if (!this.mergeItemStack(stack, 4, 4 + 36, true)) {
                        world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), stack));
                    }
                }
            }
        }
        super.onContainerClosed(playerIn);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    /**
     * 输入槽位（普通槽位，不触发合成）
     */
    public static class InputSlot extends SlotItemHandler {
        public InputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            if (itemStack.getItem() instanceof RequiemCardBase) {
                return super.isItemValid(itemStack);
            }
            if (itemStack.getItem() instanceof ItemModuleBase &&
                    !(itemStack.getItem() instanceof ItemRivenModule)) {
                return super.isItemValid(itemStack);
            }
            return false;
        }
    }

    /**
     * 输出槽位（只读）
     */
    public static class OutputSlot extends SlotItemHandler {
        public OutputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return false;
        }
    }
}