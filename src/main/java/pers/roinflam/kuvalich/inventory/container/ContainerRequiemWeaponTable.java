package pers.roinflam.kuvalich.inventory.container;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

public class ContainerRequiemWeaponTable extends Container {
    private final @NotNull World world;
    @Nonnull
    private final BlockPos pos;

    public @NotNull ItemStackHandler module = null;
    public @NotNull ItemStackHandler weapon = null;
    public boolean synchronize = false;
    public boolean synchronizeWeapon = false;

    public ContainerRequiemWeaponTable(@NotNull EntityPlayer entityPlayer, @NotNull World world, @Nonnull BlockPos pos) {
        this.world = world;
        this.pos = pos;

        module = new ItemStackHandler(8);
        weapon = new ItemStackHandler(1);

        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i, 18 + 41 * i, 12));
        }
        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i + 4, 18 + 41 * i, 39));
        }
        this.addSlotToContainer(new WeaponSlot(weapon, 0, 80, 67));

        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 151));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 93));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 111));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 129));
        }
    }


    @Override
    public @NotNull ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();
            itemstack = slotStack.copy();
            if (index < 9) {
                return ItemStack.EMPTY;
//                if (!this.mergeItemStack(slotStack, 9, 9 + 36, false)) {
//                    return ItemStack.EMPTY;
//                }
            } else {
                if (itemstack.getItem() instanceof ItemModuleBase) {
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
                } else {
                    if (!this.mergeItemStack(slotStack, 8, 9, false)) {
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

//    public boolean hasSameModule(ItemStack itemStack) {
//        for (int i = 0; i < 8; i++) {
//            if (this.inventorySlots.get(i) != null && this.inventorySlots.get(i).getHasStack()) {
//                ItemStack alreadyItemStack = this.inventorySlots.get(i).getStack();
//                if (itemStack.getItem() instanceof ItemRivenModule && alreadyItemStack.getItem() instanceof ItemRivenModule) {
//                    return true;
//                } else if (ItemModuleBase.getType(alreadyItemStack).equals(ItemModuleBase.getType(itemStack))) {
//                    return true;
//                }
//            }
//        }
//        return false;
//    }

    @Override
    public @NotNull ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (!player.world.isRemote) {
            new SynchronizationTask() {

                @Override
                public void run() {
//            if (slotId >= 0 && slotId < 8) {
//                ItemStack weaponItemStack = ContainerRequiemWeaponTable.weapon.getStackInSlot(0).copy();
//                if (!weaponItemStack.isEmpty() && ItemModule.hasBase(weaponItemStack)) {
//                    @NotNull NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();
//                    @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
//                    @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");
//
//                    NBTTagList itemList = new NBTTagList();
//                    for (int i = 0; i < 8; i++) {
//                        if (!module.getStackInSlot(i).isEmpty()) {
//                            ItemStack moduleItemStack = module.getStackInSlot(i);
//                            if (moduleItemStack.getItem() instanceof ItemModuleBase) {
//                                NBTTagCompound itemTag = new NBTTagCompound();
//                                moduleItemStack.writeToNBT(itemTag);
//                                itemList.appendTag(itemTag);
//                            }
//                        } else {
//                            NBTTagCompound itemTag = new NBTTagCompound();
//                            ItemStack.EMPTY.writeToNBT(itemTag);
//                            itemList.appendTag(itemTag);
//                        }
//                    }
//                    weaponModule.setTag("modules", itemList);
//                    tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
//                    nbtTagCompound.setTag("tag", tag);
//                    weaponItemStack.setTagCompound(tag);
////
//                    synchronizeWeapon = true;
//                    ContainerRequiemWeaponTable.this.inventorySlots.get(8).putStack(weaponItemStack);
//                    ContainerRequiemWeaponTable.weapon.setStackInSlot(0, weaponItemStack);
//                    synchronizeWeapon = false;
//
////                    ItemStack moduleItemStack = ContainerRequiemWeaponTable.module.getStackInSlot(slotId).copy();
////                    if (moduleItemStack.isEmpty()) {
////                        if (!player.inventory.addItemStackToInventory(moduleItemStack)) {
////                            World world = player.world;
////                            EntityItem entityItem = new EntityItem(world, player.posX, player.posY, player.posZ, moduleItemStack);
////                            world.spawnEntity(entityItem);
////                        }
////                        ContainerRequiemWeaponTable.this.getSlot(slotId).putStack(ItemStack.EMPTY);
////                        ContainerRequiemWeaponTable.module.setStackInSlot(slotId, ItemStack.EMPTY);
////                    }
//                    return ItemStack.EMPTY;
//////                    this.getItemHandler().insertItem(0, weaponItemStack, false);
//                }
//            }
                    if (slotId == 8) {
                        ItemStack weaponItemStack = ContainerRequiemWeaponTable.this.weapon.getStackInSlot(0);
                        if (weaponItemStack.isEmpty()) {
                            for (int i = 0; i < 8; i++) {
                                ContainerRequiemWeaponTable.this.getSlot(i).putStack(ItemStack.EMPTY);
                                ContainerRequiemWeaponTable.this.module.setStackInSlot(i, ItemStack.EMPTY);
                            }
                        }
                    }
//                    if (slotId != 8 || (!player.inventory.getItemStack().isEmpty() && ItemModule.hasBase(player.inventory.getItemStack()))) {
//                        ItemStack weaponItemStack = ContainerRequiemWeaponTable.weapon.getStackInSlot(0);
//                        if (weaponItemStack.isEmpty()) {
//                            weaponItemStack = player.inventory.getItemStack();
//                        }
//                        if (!weaponItemStack.isEmpty() && ItemModule.hasBase(weaponItemStack)) {
//                            @NotNull NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();
//                            @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
//                            @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");
//
//                            NBTTagList itemList = new NBTTagList();
//                            for (int i = 0; i < 8; i++) {
//                                if (!module.getStackInSlot(i).isEmpty()) {
//                                    ItemStack moduleItemStack = module.getStackInSlot(i);
//                                    if (moduleItemStack.getItem() instanceof ItemModuleBase) {
//                                        NBTTagCompound itemTag = new NBTTagCompound();
//                                        moduleItemStack.writeToNBT(itemTag);
//                                        itemList.appendTag(itemTag);
//                                    }
//                                } else {
//                                    NBTTagCompound itemTag = new NBTTagCompound();
//                                    ItemStack.EMPTY.writeToNBT(itemTag);
//                                    itemList.appendTag(itemTag);
//                                }
//                            }
//                            weaponModule.setTag("modules", itemList);
//                            tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
//                            nbtTagCompound.setTag("tag", tag);
//                            weaponItemStack.setTagCompound(tag);
//                            System.out.println(weaponItemStack.serializeNBT());
////                            ContainerRequiemWeaponTable.weapon.setStackInSlot(0, weaponItemStack);
//                            new SynchronizationTask() {
//
//                                @Override
//                                public void run() {
//                                    if ((!player.inventory.getItemStack().isEmpty() && ItemModule.hasBase(player.inventory.getItemStack()))) {
//                                        for (int i = 0; i < 8; i++) {
//                                            ContainerRequiemWeaponTable.this.getSlot(i).putStack(ItemStack.EMPTY);
//                                            module.setStackInSlot(i, ItemStack.EMPTY);
//                                        }
//                                    }
//                                }
//
//                            }.start();
//                        }
//                    }
                }
            }.start();
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public void onContainerClosed(@NotNull EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            if (!weapon.getStackInSlot(0).isEmpty()) {
                if (!this.mergeItemStack(weapon.getStackInSlot(0), 9, 9 + 36, true)) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), weapon.getStackInSlot(0)));
                }
            }
        }
        super.onContainerClosed(playerIn);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    public class WeaponSlot extends SlotItemHandler {
        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            if (!weapon.getStackInSlot(0).isEmpty()) {
                return false;
            }
            return ItemModule.hasBase(itemStack) && super.isItemValid(itemStack);
        }

        @Override
        public void putStack(@NotNull ItemStack weaponItemStack) {
//            System.out.println(weaponItemStack.serializeNBT() + ":" + world.isRemote);
//            if (!synchronizeWeapon) {
            @NotNull NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();
            @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
            @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");
            NBTTagList itemList = weaponModule.getTagList("modules", 10);
            for (int i = 0; i < 8; i++) {
                NBTTagCompound itemTag = itemList.getCompoundTagAt(i);
                ItemStack stack = new ItemStack(itemTag);
                if (!stack.isEmpty()) {
                    ContainerRequiemWeaponTable.this.synchronize = true;
                    ContainerRequiemWeaponTable.this.getSlot(i).putStack(stack);
                    module.setStackInSlot(i, stack);
                }
            }
//            }
            super.putStack(weaponItemStack);
        }
    }

    public class ModuleSlot extends SlotItemHandler {
        @Override
        public ItemStack onTake(EntityPlayer thePlayer, ItemStack stack) {
            new SynchronizationTask() {

                @Override
                public void run() {
                    if (!world.isRemote) {
                        ItemStack weaponItemStack = ContainerRequiemWeaponTable.this.weapon.getStackInSlot(0).copy();
                        if (!weaponItemStack.isEmpty() && ItemModule.hasBase(weaponItemStack)) {
                            @NotNull NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();
                            @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
                            @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");

                            NBTTagList itemList = new NBTTagList();
                            for (int i = 0; i < 8; i++) {
                                if (!module.getStackInSlot(i).isEmpty()) {
                                    ItemStack moduleItemStack = module.getStackInSlot(i);
                                    if (moduleItemStack.getItem() instanceof ItemModuleBase) {
                                        NBTTagCompound itemTag = new NBTTagCompound();
                                        moduleItemStack.writeToNBT(itemTag);
                                        itemList.appendTag(itemTag);
                                    }
                                } else {
                                    NBTTagCompound itemTag = new NBTTagCompound();
                                    ItemStack.EMPTY.writeToNBT(itemTag);
                                    itemList.appendTag(itemTag);
                                }
                            }
                            weaponModule.setTag("modules", itemList);
                            tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
                            nbtTagCompound.setTag("tag", tag);
                            weaponItemStack.setTagCompound(tag);

                            ContainerRequiemWeaponTable.this.inventorySlots.get(8).putStack(weaponItemStack);
                            ((EntityPlayerMP) entityPlayer).sendContainerToPlayer(entityPlayer.inventoryContainer);
                        }
                        ModuleSlot.this.putStack(ItemStack.EMPTY);
                    }
                }
            }.start();
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
            if (weapon.getStackInSlot(0).isEmpty()) {
                return false;
            }
            if (itemStack.getItem() instanceof ItemModuleBase && !ItemModuleBase.isRandom(itemStack)) {
                if (!this.getItemHandler().getStackInSlot(index).isEmpty()) {
                    return false;
                }
                for (int i = 0; i < 8; i++) {
                    if (i != index) {
                        if (!module.getStackInSlot(i).isEmpty()) {
                            ItemStack alreadyItemStack = module.getStackInSlot(i);
                            if (itemStack.getItem() instanceof ItemRivenModule && alreadyItemStack.getItem() instanceof ItemRivenModule) {
                                return false;
                            } else if (ItemModuleBase.getType(alreadyItemStack).equals(ItemModuleBase.getType(itemStack))) {
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
            if (!ContainerRequiemWeaponTable.this.synchronize) {
                new SynchronizationTask() {

                    @Override
                    public void run() {
                        if (!world.isRemote) {
                            ItemStack weaponItemStack = ContainerRequiemWeaponTable.this.weapon.getStackInSlot(0).copy();
                            if (!weaponItemStack.isEmpty() && ItemModule.hasBase(weaponItemStack)) {
                                @NotNull NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();
                                @NotNull NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
                                @NotNull NBTTagCompound weaponModule = tag.getCompoundTag(Reference.MOD_ID + "_weaponModules");

                                NBTTagList itemList = new NBTTagList();
                                for (int i = 0; i < 8; i++) {
                                    if (!module.getStackInSlot(i).isEmpty()) {
                                        ItemStack moduleItemStack = module.getStackInSlot(i);
                                        if (moduleItemStack.getItem() instanceof ItemModuleBase) {
                                            NBTTagCompound itemTag = new NBTTagCompound();
                                            moduleItemStack.writeToNBT(itemTag);
                                            itemList.appendTag(itemTag);
                                        }
                                    } else {
                                        NBTTagCompound itemTag = new NBTTagCompound();
                                        ItemStack.EMPTY.writeToNBT(itemTag);
                                        itemList.appendTag(itemTag);
                                    }
                                }
                                weaponModule.setTag("modules", itemList);
                                tag.setTag(Reference.MOD_ID + "_weaponModules", weaponModule);
                                nbtTagCompound.setTag("tag", tag);
                                weaponItemStack.setTagCompound(tag);

//                    ContainerRequiemWeaponTable.weapon.setStackInSlot(0, ItemStack.EMPTY);
                                ContainerRequiemWeaponTable.this.inventorySlots.get(8).putStack(weaponItemStack);
//                    this.getItemHandler().insertItem(0, weaponItemStack, false);
                            }
                        }
                    }

                }.start();
            }
            ContainerRequiemWeaponTable.this.synchronize = false;
            super.putStack(itemStack);
        }
    }
}
