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
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 安魂之融容器类

 */
public class ContainerRequiemEvolve extends Container {
    private final World world;
    private final BlockPos pos;
    private final EntityPlayer player;

    private final ItemStackHandler weapon;
    private final ItemStackHandler material;
    private final ItemStackHandler result;

    private boolean evolveMode = false;
    private boolean cycleMode = false;
    private boolean baseAttributeJustProcessed = false;

    private ItemStack lastWeapon = ItemStack.EMPTY;
    private ItemStack lastMaterial = ItemStack.EMPTY;

    public ContainerRequiemEvolve(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
        this.player = entityPlayer;

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

        LogUtil.debugEvent("安魂之融容器创建", entityPlayer.getName(), "位置: " + pos.toString());
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

        if (index < 3) {
            if (index == 2) {
                return ItemStack.EMPTY;
            }

            if (!this.mergeItemStack(slotStack, 3, 3 + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean transferred = false;

            if (this.mergeItemStack(slotStack, 0, 1, false)) {
                transferred = true;
            } else if (this.mergeItemStack(slotStack, 1, 2, false)) {
                transferred = true;
            }

            if (!transferred) {
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

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (!world.isRemote) {
            checkAndProcessCrafting();
        }
    }

    private void checkAndProcessCrafting() {
        ItemStack currentWeapon = weapon.getStackInSlot(0);
        ItemStack currentMaterial = material.getStackInSlot(0);
        ItemStack currentResult = result.getStackInSlot(0);

        boolean inputChanged = !ItemStack.areItemStacksEqual(currentWeapon, lastWeapon) ||
                !ItemStack.areItemStacksEqual(currentMaterial, lastMaterial);

        if (currentWeapon.isEmpty() || currentMaterial.isEmpty()) {
            if (!currentResult.isEmpty()) {
                if (!baseAttributeJustProcessed) {
                    result.setStackInSlot(0, ItemStack.EMPTY);
                    evolveMode = false;
                    cycleMode = false;
                    forceSyncSlot(2);
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

    private void processWeaponEvolve(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();

            int weaponLevel = KuvaWeapon.getNumber(weaponStack);
            int materialLevel = KuvaWeapon.getNumber(materialStack);
            int newLevel = Math.max(weaponLevel, materialLevel);

            newLevel = (int) (newLevel * (1 + ModConfig.KUVA_LICH.upgradeMultiplier));
            newLevel = Math.min(newLevel, ModConfig.KUVA_LICH.upgradeLimit);

            KuvaWeapon.setType(newWeapon, KuvaWeapon.getType(materialStack));
            KuvaWeapon.setNumber(newWeapon, newLevel);

            result.setStackInSlot(0, newWeapon);
            evolveMode = true;
            forceSyncSlot(2);
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
                material.setStackInSlot(0, newMaterial);
                forceSyncSlot(1);

                boolean isMelee = ItemRivenModule.isMelee(weaponStack);
                ItemStack newRiven = ItemRivenModule.cycleModule(trend, cycleCount, isMelee);
                result.setStackInSlot(0, newRiven);
                forceSyncSlot(2);

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
                material.setStackInSlot(0, newMaterial);
                forceSyncSlot(1);

                ItemStack newRiven = WarframeRivenModule.cycleModule(trend, cycleCount);
                result.setStackInSlot(0, newRiven);
                forceSyncSlot(2);

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

                result.setStackInSlot(0, newWeapon);
                material.setStackInSlot(0, newMaterial);
                weapon.setStackInSlot(0, ItemStack.EMPTY);

                forceSyncSlot(0);
                forceSyncSlot(1);
                forceSyncSlot(2);

                LogUtil.debugEvent("添加基础属性", weaponStack.getDisplayName(), "开光完成");
            } catch (Exception e) {
                LogUtil.error("添加基础属性失败", e);
                baseAttributeJustProcessed = false;
            }
        }
    }

    private void forceSyncSlot(int slotIndex) {
        if (player instanceof EntityPlayerMP) {
            ItemStack stack = this.inventorySlots.get(slotIndex).getStack();
            ((EntityPlayerMP) player).connection.sendPacket(
                    new SPacketSetSlot(this.windowId, slotIndex, stack)
            );
        }
    }

    private void forceSyncToClient() {
        if (player instanceof EntityPlayerMP) {
            for (int i = 0; i < 3; i++) {
                forceSyncSlot(i);
            }
        }
    }

    public void onResultTaken() {
        if (evolveMode) {
            weapon.setStackInSlot(0, ItemStack.EMPTY);
            material.setStackInSlot(0, ItemStack.EMPTY);
            evolveMode = false;
            forceSyncSlot(0);
            forceSyncSlot(1);
        }

        if (cycleMode) {
            weapon.setStackInSlot(0, ItemStack.EMPTY);
            cycleMode = false;
            forceSyncSlot(0);
        }

        if (baseAttributeJustProcessed) {
            baseAttributeJustProcessed = false;
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            if (!weapon.getStackInSlot(0).isEmpty()) {
                if (!this.mergeItemStack(weapon.getStackInSlot(0), 3, 3 + 36, true)) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), weapon.getStackInSlot(0)));
                }
            }

            if (!material.getStackInSlot(0).isEmpty()) {
                if (!this.mergeItemStack(material.getStackInSlot(0), 3, 3 + 36, true)) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), material.getStackInSlot(0)));
                }
            }

            if (!result.getStackInSlot(0).isEmpty()) {
                if (!this.mergeItemStack(result.getStackInSlot(0), 3, 3 + 36, true)) {
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
            if (KuvaWeapon.hasType(itemStack)) {
                return super.isItemValid(itemStack);
            }
            if (itemStack.getItem() instanceof ItemRivenModule || itemStack.getItem() instanceof WarframeRivenModule) {
                return super.isItemValid(itemStack);
            }
            if (!ItemModule.hasBase(itemStack) && itemStack.getCount() == 1) {
                return super.isItemValid(itemStack);
            }
            return false;
        }
    }

    public static class MaterialSlot extends SlotItemHandler {
        public MaterialSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            if (KuvaWeapon.hasType(itemStack)) {
                return super.isItemValid(itemStack);
            }
            if (itemStack.getItem() instanceof Kuva) {
                return super.isItemValid(itemStack);
            }
            if (itemStack.getItem() instanceof RivenSliver) {
                return super.isItemValid(itemStack);
            }
            return false;
        }
    }

    public class ResultSlot extends SlotItemHandler {
        public ResultSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return false;
        }

        @Override
        public ItemStack onTake(EntityPlayer thePlayer, ItemStack stack) {
            ContainerRequiemEvolve.this.onResultTaken();
            return super.onTake(thePlayer, stack);
        }
    }
}