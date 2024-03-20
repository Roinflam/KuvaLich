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
import pers.roinflam.kuvalich.config.ConfigKuvaLich;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

public class ContainerRequiemEvolve extends Container {
    private final @NotNull World world;
    @Nonnull
    private final BlockPos pos;

    private final @NotNull ItemStackHandler weapon;
    private final @NotNull ItemStackHandler material;
    private final @NotNull ItemStackHandler result;
    private boolean evolve = false;
    private boolean cycle = false;

    public ContainerRequiemEvolve(@NotNull EntityPlayer entityPlayer, @NotNull World world, @Nonnull BlockPos pos) {
        this.world = world;
        this.pos = pos;

        this.weapon = new ItemStackHandler(1);
        this.material = new ItemStackHandler(1);
        this.result = new ItemStackHandler(1);

        this.addSlotToContainer(new WeaponSlot(this.weapon, 0, 31, 32));
        this.addSlotToContainer(new MaterialSlot(this.material, 0, 81, 32));
        this.addSlotToContainer(new ResultSlot(this.result, 0, 131, 32));

        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 171 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 113 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 131 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 149 - 10));
        }
    }


    @Override
    public @NotNull ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);
        if (slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();
            itemstack = slotStack.copy();
            if (index < 3) {
                return ItemStack.EMPTY;
//                if (index == 1) {
//                    if (!this.inventorySlots.get(0).getHasStack() || !this.inventorySlots.get(2).getHasStack()) {
//                        if (!this.mergeItemStack(slotStack, 3, 36 + 3, true)) {
//                            return ItemStack.EMPTY;
//                        }
//                    } else {
//                        return ItemStack.EMPTY;
//                    }
//                } else {
//                    if (!this.mergeItemStack(slotStack, 3, 36 + 3, true)) {
//                        return ItemStack.EMPTY;
//                    }
//                }
            } else {
                if (!this.mergeItemStack(slotStack, 0, 1, false)) {
                    if (!this.mergeItemStack(slotStack, 1, 2, false)) {
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
    public @NotNull ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (!player.world.isRemote) {
            new SynchronizationTask() {
                @Override
                public void run() {
                    if (slotId != 1 || !clickTypeIn.equals(ClickType.QUICK_MOVE)) {
                        if (evolve) {
                            if (slotId == 2) {
                                weapon.setStackInSlot(0, ItemStack.EMPTY);
                                material.setStackInSlot(0, ItemStack.EMPTY);
                                evolve = false;
                                return;
                            } else {
                                if (slotId == 0 || slotId == 1) {
                                    result.setStackInSlot(0, ItemStack.EMPTY);
                                    evolve = false;
                                    return;
                                }
                            }
                        } else if (cycle) {
                            if (slotId == 2) {
                                weapon.setStackInSlot(0, ItemStack.EMPTY);
                                cycle = false;
                                return;
                            } else {
                                if (slotId == 0 || slotId == 1) {
                                    result.setStackInSlot(0, ItemStack.EMPTY);
                                    cycle = false;
                                    return;
                                }
                            }
                        }
                    }
                    if (KuvaWeapon.hasType(weapon.getStackInSlot(0)) && KuvaWeapon.hasType(material.getStackInSlot(0))) {
                        if (result.getStackInSlot(0).isEmpty()) {
                            evolve = true;
                            ItemStack weaponItem = weapon.getStackInSlot(0).copy();
                            ItemStack materialItem = material.getStackInSlot(0).copy();
                            int number = Math.max(KuvaWeapon.getNumber(weaponItem), KuvaWeapon.getNumber(materialItem));
                            number = Math.min(number, ConfigKuvaLich.upgradeLimit);
                            number = (int) (number * (1 + ConfigKuvaLich.upgradeMultiplier));
                            KuvaWeapon.setType(weaponItem, KuvaWeapon.getType(materialItem));
                            KuvaWeapon.setNumber(weaponItem, number);

                            result.insertItem(0, weaponItem, false);
                        }
                    } else if (weapon.getStackInSlot(0).getItem() instanceof ItemRivenModule && material.getStackInSlot(0).getItem() instanceof Kuva) {
                        int kuvaSpend = Math.min(ItemRivenModule.getCycle(weapon.getStackInSlot(0)), 8);
                        kuvaSpend += Math.pow(ItemRivenModule.getTrend(weapon.getStackInSlot(0)), 2) - Math.pow(ItemRivenModule.getTrend(weapon.getStackInSlot(0)) - 1, 2);
                        if (material.getStackInSlot(0).getCount() >= kuvaSpend &&
                                !ItemRivenModule.isRandom(weapon.getStackInSlot(0)) && result.getStackInSlot(0).isEmpty()) {
                            cycle = true;
                            ItemStack materialItemStack = material.getStackInSlot(0).copy();
                            materialItemStack.setCount(materialItemStack.getCount() - kuvaSpend);
                            ContainerRequiemEvolve.this.inventorySlots.get(1).putStack(materialItemStack);

                            ItemStack resultItemStack = weapon.getStackInSlot(0).copy();
                            result.insertItem(0, ItemRivenModule.cycleModule(ItemRivenModule.getTrend(resultItemStack), ItemRivenModule.getCycle(resultItemStack), ItemRivenModule.isMelee(resultItemStack)), false);

                            ItemRivenModule.setCycle(weapon.getStackInSlot(0), ItemRivenModule.getCycle(weapon.getStackInSlot(0).copy()) + 1);
                        }
                    } else if (weapon.getStackInSlot(0).getItem() instanceof WarframeRivenModule && material.getStackInSlot(0).getItem() instanceof Kuva) {
                        int kuvaSpend = Math.min(WarframeRivenModule.getCycle(weapon.getStackInSlot(0)), 8);
                        kuvaSpend += Math.pow(WarframeRivenModule.getTrend(weapon.getStackInSlot(0)), 2);
                        if (material.getStackInSlot(0).getCount() >= kuvaSpend &&
                                !WarframeRivenModule.isRandom(weapon.getStackInSlot(0)) && result.getStackInSlot(0).isEmpty()) {
                            cycle = true;
                            ItemStack materialItemStack = material.getStackInSlot(0).copy();
                            materialItemStack.setCount(materialItemStack.getCount() - kuvaSpend);
                            ContainerRequiemEvolve.this.inventorySlots.get(1).putStack(materialItemStack);

                            ItemStack resultItemStack = weapon.getStackInSlot(0).copy();
                            result.insertItem(0, WarframeRivenModule.cycleModule(WarframeRivenModule.getTrend(resultItemStack), WarframeRivenModule.getCycle(resultItemStack)), false);

                            WarframeRivenModule.setCycle(weapon.getStackInSlot(0), WarframeRivenModule.getCycle(weapon.getStackInSlot(0).copy()) + 1);
                        }
                    } else if (!weapon.getStackInSlot(0).isEmpty() && material.getStackInSlot(0).getItem() instanceof RivenSliver) {
                        if (!ItemModule.hasBase(weapon.getStackInSlot(0)) && result.getStackInSlot(0).isEmpty() && weapon.getStackInSlot(0).getCount() == 1) {
                            ItemStack materialItemStack = material.getStackInSlot(0).copy();
                            materialItemStack.setCount(materialItemStack.getCount() - 1);
                            ContainerRequiemEvolve.this.inventorySlots.get(1).putStack(materialItemStack);

                            ItemStack weaponItemStack = weapon.getStackInSlot(0).copy();

                            ItemModule.setBaseAttribute(weaponItemStack);
                            weapon.setStackInSlot(0, ItemStack.EMPTY);
                            result.setStackInSlot(0, weaponItemStack);
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
            if (!weapon.getStackInSlot(0).isEmpty()) {
                world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), weapon.getStackInSlot(0)));
            }
            if (!material.getStackInSlot(0).isEmpty()) {
                world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), material.getStackInSlot(0)));
            }
            if (!evolve && !cycle) {
                if (!result.getStackInSlot(0).isEmpty()) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), result.getStackInSlot(0)));
                }
            }
        }
        super.onContainerClosed(playerIn);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    public static class WeaponSlot extends SlotItemHandler {

        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return (KuvaWeapon.hasType(itemStack) || itemStack.getItem() instanceof ItemRivenModule || (!ItemModule.hasBase(itemStack) && itemStack.getCount() == 1)) && super.isItemValid(itemStack);
        }
    }

    public static class MaterialSlot extends SlotItemHandler {

        public MaterialSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return (KuvaWeapon.hasType(itemStack) || itemStack.getItem() instanceof Kuva || itemStack.getItem() instanceof RivenSliver) && super.isItemValid(itemStack);
        }
    }

    public static class ResultSlot extends SlotItemHandler {

        public ResultSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return false;
        }
    }
}
