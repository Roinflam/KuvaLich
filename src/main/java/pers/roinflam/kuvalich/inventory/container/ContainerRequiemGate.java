    package pers.roinflam.kuvalich.inventory.container;

    import net.minecraft.entity.item.EntityItem;
    import net.minecraft.entity.player.EntityPlayer;
    import net.minecraft.entity.player.InventoryPlayer;
    import net.minecraft.inventory.Container;
    import net.minecraft.inventory.IContainerListener;
    import net.minecraft.inventory.Slot;
    import net.minecraft.item.ItemStack;
    import net.minecraft.util.math.BlockPos;
    import net.minecraft.world.World;
    import net.minecraftforge.items.IItemHandler;
    import net.minecraftforge.items.ItemStackHandler;
    import net.minecraftforge.items.SlotItemHandler;
    import org.jetbrains.annotations.NotNull;
    import org.jetbrains.annotations.Nullable;
    import pers.roinflam.kuvalich.base.item.RequiemCardBase;
    import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
    import pers.roinflam.kuvalich.blocks.capability.RequiemCard;

    import javax.annotation.Nonnull;

    public class ContainerRequiemGate extends Container {
        private final @NotNull EntityPlayer entityPlayer;
        private final @NotNull World world;
        @Nonnull
        private final BlockPos pos;

        private final @NotNull IItemHandler card;
        public int oneRiddle = -1;
        public int twoRiddle = -1;
        public int threeRiddle = -1;
        public int level = 0;
        public int schedule = 0;

        public ContainerRequiemGate(@NotNull EntityPlayer entityPlayer, @NotNull World world, @Nonnull BlockPos pos) {
            this.entityPlayer = entityPlayer;
            this.world = world;
            this.pos = pos;

            this.card = new ItemStackHandler(3);

            this.addSlotToContainer(new CardSlot(this.card, 0, 26, 11));
            this.addSlotToContainer(new CardSlot(this.card, 1, 80, 11));
            this.addSlotToContainer(new CardSlot(this.card, 2, 134, 11));

    //        this.addSlotToContainer(new SlotItemHandler(this.lock, 0, 26, 56));
    //        this.addSlotToContainer(new SlotItemHandler(this.lock, 1, 80, 56));
    //        this.addSlotToContainer(new SlotItemHandler(this.lock, 2, 134, 56));

            InventoryPlayer inventoryPlayer = entityPlayer.inventory;
            for (int i = 0; i < 9; i++) {
                this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 152 - 10));
                this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 94 - 10));
                this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 112 - 10));
                this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 130 - 10));
            }
            if (!world.isRemote) {
                @NotNull ItemStack oneCard = this.card.getStackInSlot(0);
                @NotNull ItemStack twoCard = this.card.getStackInSlot(1);
                @NotNull ItemStack threeCard = this.card.getStackInSlot(2);
                if (oneCard.isEmpty() && twoCard.isEmpty() && threeCard.isEmpty()) {
                    @Nullable RequiemCard requiemCard = entityPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
                    this.card.insertItem(0, requiemCard.getOneCard(), false);
                    this.card.insertItem(1, requiemCard.getTwoCard(), false);
                    this.card.insertItem(2, requiemCard.getThreeCard(), false);
                }
            }
        }

        @Override
        public void detectAndSendChanges() {
            super.detectAndSendChanges();
            @Nullable RequiemCard requiemCard = entityPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
            for (@NotNull IContainerListener containerListener : this.listeners) {
                containerListener.sendWindowProperty(this, 0, requiemCard.getOneRiddle());
                containerListener.sendWindowProperty(this, 1, requiemCard.getTwoRiddle());
                containerListener.sendWindowProperty(this, 2, requiemCard.getThreeRiddle());
                containerListener.sendWindowProperty(this, 3, requiemCard.getUnlockedCardStatus());
                containerListener.sendWindowProperty(this, 4, requiemCard.getDecryptionProgress());
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
        public void onContainerClosed(@NotNull EntityPlayer playerIn) {
            if (!playerIn.world.isRemote) {
                @NotNull ItemStack oneCard = this.card.getStackInSlot(0);
                @NotNull ItemStack twoCard = this.card.getStackInSlot(1);
                @NotNull ItemStack threeCard = this.card.getStackInSlot(2);
                @Nullable RequiemCard requiemCard = playerIn.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
                @NotNull BlockPos blockPos = playerIn.getPosition();
                if (!oneCard.isEmpty() && !(oneCard.getItem() instanceof RequiemCardBase)) {
                    @NotNull EntityItem entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), oneCard.copy());
                    world.spawnEntity(entityItem);
                } else {
                    requiemCard.setOneCard(oneCard.copy());
                }
                if (!twoCard.isEmpty() && !(twoCard.getItem() instanceof RequiemCardBase)) {
                    @NotNull EntityItem entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), twoCard.copy());
                    world.spawnEntity(entityItem);
                } else {
                    requiemCard.setTwoCard(twoCard.copy());
                }
                if (!threeCard.isEmpty() && !(threeCard.getItem() instanceof RequiemCardBase)) {
                    @NotNull EntityItem entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), threeCard.copy());
                    world.spawnEntity(entityItem);
                } else {
                    requiemCard.setThreeCard(threeCard.copy());
                }
                oneCard.setCount(0);
                twoCard.setCount(0);
                threeCard.setCount(0);
            }
            super.onContainerClosed(playerIn);
        }

        @Override
        public void updateProgressBar(int id, int data) {
            if (id == 0) {
                oneRiddle = data;
            } else if (id == 1) {
                twoRiddle = data;
            } else if (id == 2) {
                threeRiddle = data;
            } else if (id == 3) {
                level = data;
            } else if (id == 4) {
                schedule = data;
            }
            super.updateProgressBar(id, data);
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
                return (itemStack.getItem() instanceof RequiemCardBase) && super.isItemValid(itemStack);
            }
        }


    }
