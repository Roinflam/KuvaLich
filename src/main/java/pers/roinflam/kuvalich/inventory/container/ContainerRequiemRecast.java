package pers.roinflam.kuvalich.inventory.container;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

public class ContainerRequiemRecast extends Container {
    private final @NotNull World world;
    @Nonnull
    private final BlockPos pos;

    private final @NotNull ItemStackHandler card;

    public ContainerRequiemRecast(@NotNull EntityPlayer entityPlayer, @NotNull World world, @Nonnull BlockPos pos) {
        this.world = world;
        this.pos = pos;

        this.card = new ItemStackHandler(4);

        this.addSlotToContainer(new CardSlot(this.card, 0, 31, 54));
        this.addSlotToContainer(new CardSlot(this.card, 1, 80, 54));
        this.addSlotToContainer(new CardSlot(this.card, 2, 129, 54));
        this.addSlotToContainer(new CardSlot(this.card, 3, 81, 10));

        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 152 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 94 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 112 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 130 - 10));
        }
    }

    @Override
    public @NotNull ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();
            itemstack = slotStack.copy();
            if (index < card.getSlots()) {
                if (!this.mergeItemStack(slotStack, card.getSlots(), 36 + card.getSlots(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.mergeItemStack(slotStack, 0, card.getSlots(), false)) {
                    return ItemStack.EMPTY;
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
    public @NotNull ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (!player.world.isRemote) {
            new SynchronizationTask() {
                @Override
                public void run() {
                    if (card.getStackInSlot(0).getItem() instanceof RequiemCardBase && card.getStackInSlot(1).getItem() instanceof RequiemCardBase && card.getStackInSlot(2).getItem() instanceof RequiemCardBase) {
                        if (card.getStackInSlot(3).isEmpty()) {
                            card.setStackInSlot(0, ItemStack.EMPTY);
                            card.setStackInSlot(1, ItemStack.EMPTY);
                            card.setStackInSlot(2, ItemStack.EMPTY);
                            card.insertItem(3, new ItemStack(RequiemCardBase.getCard(RandomUtil.getInt(0, 7))), false);
                        }
                    } else if (card.getStackInSlot(0).getItem() instanceof ItemCommonModule && card.getStackInSlot(1).getItem() instanceof ItemCommonModule && card.getStackInSlot(2).getItem() instanceof ItemCommonModule) {
                        if (card.getStackInSlot(3).isEmpty()) {
                            card.setStackInSlot(0, ItemStack.EMPTY);
                            card.setStackInSlot(1, ItemStack.EMPTY);
                            card.setStackInSlot(2, ItemStack.EMPTY);
                            card.insertItem(3, ItemCommonModule.getRandomModule(), false);
                        }
                    } else if (card.getStackInSlot(0).getItem() instanceof ItemUncommonModule && card.getStackInSlot(1).getItem() instanceof ItemUncommonModule && card.getStackInSlot(2).getItem() instanceof ItemUncommonModule) {
                        if (card.getStackInSlot(3).isEmpty()) {
                            card.setStackInSlot(0, ItemStack.EMPTY);
                            card.setStackInSlot(1, ItemStack.EMPTY);
                            card.setStackInSlot(2, ItemStack.EMPTY);
                            card.insertItem(3, ItemUncommonModule.getRandomModule(), false);
                        }
                    } else if (card.getStackInSlot(0).getItem() instanceof ItemRareModule && card.getStackInSlot(1).getItem() instanceof ItemRareModule && card.getStackInSlot(2).getItem() instanceof ItemRareModule) {
                        if (card.getStackInSlot(3).isEmpty()) {
                            card.setStackInSlot(0, ItemStack.EMPTY);
                            card.setStackInSlot(1, ItemStack.EMPTY);
                            card.setStackInSlot(2, ItemStack.EMPTY);
                            card.insertItem(3, ItemRareModule.getRandomModule(), false);
                        }
                    } else if (card.getStackInSlot(0).getItem() instanceof ItemPrimeModule && card.getStackInSlot(1).getItem() instanceof ItemPrimeModule && card.getStackInSlot(2).getItem() instanceof ItemPrimeModule) {
                        if (card.getStackInSlot(3).isEmpty()) {
                            card.setStackInSlot(0, ItemStack.EMPTY);
                            card.setStackInSlot(1, ItemStack.EMPTY);
                            card.setStackInSlot(2, ItemStack.EMPTY);
                            card.insertItem(3, ItemPrimeModule.getRandomModule(), false);
                        }
                    }

                }

            }.start();
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public void onContainerClosed(@NotNull EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            if (!card.getStackInSlot(0).isEmpty()) {
                if (!this.mergeItemStack(card.getStackInSlot(0), 4, 4 + 36, true)) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), card.getStackInSlot(0)));
                }
            }
            if (!card.getStackInSlot(1).isEmpty()) {
                if (!this.mergeItemStack(card.getStackInSlot(1), 4, 4 + 36, true)) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), card.getStackInSlot(1)));
                }
            }
            if (!card.getStackInSlot(2).isEmpty()) {
                if (!this.mergeItemStack(card.getStackInSlot(2), 4, 4 + 36, true)) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), card.getStackInSlot(2)));
                }
            }
            if (!card.getStackInSlot(3).isEmpty()) {
                if (!this.mergeItemStack(card.getStackInSlot(3), 4, 4 + 36, true)) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), card.getStackInSlot(3)));
                }
            }
        }
        super.onContainerClosed(playerIn);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    public static class CardSlot extends SlotItemHandler {

        public CardSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return (itemStack.getItem() instanceof RequiemCardBase || itemStack.getItem() instanceof ItemModuleBase) && !(itemStack.getItem() instanceof ItemRivenModule) && super.isItemValid(itemStack);
        }
    }
}
