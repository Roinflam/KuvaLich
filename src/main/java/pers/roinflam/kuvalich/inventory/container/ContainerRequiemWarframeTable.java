package pers.roinflam.kuvalich.inventory.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.WarframeModuleBase;
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.blocks.capability.WarframeModules;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

public class ContainerRequiemWarframeTable extends Container {
    private final @NotNull World world;
    @Nonnull
    private final BlockPos pos;

    public @NotNull ItemStackHandler module = null;

    public ContainerRequiemWarframeTable(@NotNull EntityPlayer entityPlayer, @NotNull World world, @Nonnull BlockPos pos) {
        this.world = world;
        this.pos = pos;

        module = new ItemStackHandler(8);

        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i, 18 + 41 * i, 12));
        }
        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i + 4, 18 + 41 * i, 39));
        }

        @Nullable WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);
        this.module.insertItem(0, warframeModules.getOne(), false);
        this.module.insertItem(1, warframeModules.getTwo(), false);
        this.module.insertItem(2, warframeModules.getThree(), false);
        this.module.insertItem(3, warframeModules.getFour(), false);
        this.module.insertItem(4, warframeModules.getFive(), false);
        this.module.insertItem(5, warframeModules.getSix(), false);
        this.module.insertItem(6, warframeModules.getSeven(), false);
        this.module.insertItem(7, warframeModules.getEight(), false);

        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 122));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 64));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 81));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 99));
        }
    }


    @Override
    public @NotNull ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();
            itemstack = slotStack.copy();
            if (index < 8) {
//                return ItemStack.EMPTY;
                if (!this.mergeItemStack(slotStack, 8, 8 + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (itemstack.getItem() instanceof WarframeModuleBase) {
                    boolean success = false;
                    for (int i = 0; i < 8; i++) {
                        if (this.mergeItemStack(slotStack, i, i + 1, false)) {
                            success = true;
                            break;
                        }
                    }
                    if (!success) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            if (slotStack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }
        return itemstack;
    }


    @Override
    public void onContainerClosed(@NotNull EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            @Nullable WarframeModules warframeModules = playerIn.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);
            warframeModules.setOne(module.getStackInSlot(0));
            warframeModules.setTwo(module.getStackInSlot(1));
            warframeModules.setThree(module.getStackInSlot(2));
            warframeModules.setFour(module.getStackInSlot(3));
            warframeModules.setFive(module.getStackInSlot(4));
            warframeModules.setSix(module.getStackInSlot(5));
            warframeModules.setSeven(module.getStackInSlot(6));
            warframeModules.setEight(module.getStackInSlot(7));
        }
        super.onContainerClosed(playerIn);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    public class ModuleSlot extends SlotItemHandler {
        @Override
        public ItemStack onTake(EntityPlayer thePlayer, ItemStack stack) {
            if (!world.isRemote) {
                new SynchronizationTask() {

                    @Override
                    public void run() {
                        @Nullable WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);
                        warframeModules.setOne(module.getStackInSlot(0));
                        warframeModules.setTwo(module.getStackInSlot(1));
                        warframeModules.setThree(module.getStackInSlot(2));
                        warframeModules.setFour(module.getStackInSlot(3));
                        warframeModules.setFive(module.getStackInSlot(4));
                        warframeModules.setSix(module.getStackInSlot(5));
                        warframeModules.setSeven(module.getStackInSlot(6));
                        warframeModules.setEight(module.getStackInSlot(7));
                    }
                }.start();
            }
            return super.onTake(thePlayer, stack);
        }

        public int index;
        public EntityPlayer entityPlayer;

        public ModuleSlot(EntityPlayer entityPlayer, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.index = index;
            this.entityPlayer = entityPlayer;
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            if (itemStack.getItem() instanceof WarframeModuleBase && !WarframeModuleBase.isRandom(itemStack)) {
                if (!this.getItemHandler().getStackInSlot(index).isEmpty()) {
                    return false;
                }
                for (int i = 0; i < 8; i++) {
                    if (i != index) {
                        if (!module.getStackInSlot(i).isEmpty()) {
                            ItemStack alreadyItemStack = module.getStackInSlot(i);
                            if (itemStack.getItem() instanceof WarframeRivenModule && alreadyItemStack.getItem() instanceof WarframeRivenModule) {
                                return false;
                            } else if (WarframeModuleBase.getType(alreadyItemStack).equals(WarframeModuleBase.getType(itemStack))) {
                                return false;
                            }
                        }
                    }
                }
                return super.isItemValid(itemStack);
            }
            return false;
        }

        @Override
        public void putStack(@NotNull ItemStack itemStack) {
            if (!world.isRemote) {
                new SynchronizationTask() {

                    @Override
                    public void run() {
                        @Nullable WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);
                        warframeModules.setOne(module.getStackInSlot(0));
                        warframeModules.setTwo(module.getStackInSlot(1));
                        warframeModules.setThree(module.getStackInSlot(2));
                        warframeModules.setFour(module.getStackInSlot(3));
                        warframeModules.setFive(module.getStackInSlot(4));
                        warframeModules.setSix(module.getStackInSlot(5));
                        warframeModules.setSeven(module.getStackInSlot(6));
                        warframeModules.setEight(module.getStackInSlot(7));
                    }

                }.start();
            }
            super.putStack(itemStack);
        }
    }
}
