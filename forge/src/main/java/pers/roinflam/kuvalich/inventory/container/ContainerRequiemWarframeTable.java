package pers.roinflam.kuvalich.inventory.container;

import net.minecraft.entity.item.EntityItem;
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
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.base.item.WarframeModuleBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;

public class ContainerRequiemWarframeTable extends Container {
    private final World world;
    private final BlockPos pos;

    public ItemStackHandler module = null;

    public ContainerRequiemWarframeTable(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;

        module = new ItemStackHandler(8);

        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i, 18 + 41 * i, 12));
        }
        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i + 4, 18 + 41 * i, 39));
        }

        try {
            WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);

            if (warframeModules != null) {
                this.module.insertItem(0, warframeModules.getOne(), false);
                this.module.insertItem(1, warframeModules.getTwo(), false);
                this.module.insertItem(2, warframeModules.getThree(), false);
                this.module.insertItem(3, warframeModules.getFour(), false);
                this.module.insertItem(4, warframeModules.getFive(), false);
                this.module.insertItem(5, warframeModules.getSix(), false);
                this.module.insertItem(6, warframeModules.getSeven(), false);
                this.module.insertItem(7, warframeModules.getEight(), false);

                LogUtil.debugEvent("战甲军械库容器创建", entityPlayer.getName(), "已加载玩家的战甲模组配置");
            } else {
                LogUtil.warn("无法获取玩家的WarframeModules Capability");
            }
        } catch (Exception e) {
            LogUtil.error("加载战甲模组时发生错误", e);
        }

        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 122));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 64));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 81));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 99));
        }
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

        if (index < 8) {
            LogUtil.debug("从战甲模组槽位" + index + "转移到背包: " + slotStack.getDisplayName());

            if (!this.mergeItemStack(slotStack, 8, 8 + 36, true)) {
                LogUtil.debug("背包已满，无法转移");
                return ItemStack.EMPTY;
            }

            slot.onSlotChange(slotStack, itemstack);

            LogUtil.debug("战甲模组转移成功，已触发同步");
        } else {
            if (itemstack.getItem() instanceof WarframeModuleBase) {
                boolean success = false;
                for (int i = 0; i < 8; i++) {
                    if (this.mergeItemStack(slotStack, i, i + 1, false)) {
                        success = true;
                        LogUtil.debug("战甲模组从背包转移到槽位" + i + ": " + slotStack.getDisplayName());
                        break;
                    }
                }
                if (!success) {
                    LogUtil.debug("所有战甲模组槽位已满或不兼容");
                    return ItemStack.EMPTY;
                }
            } else {
                LogUtil.debug("拒绝转移：不是战甲模组 - " + slotStack.getDisplayName());
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
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            try {
                WarframeModules warframeModules = playerIn.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);

                if (warframeModules != null) {
                    for (int i = 0; i < 8; i++) {
                        ItemStack stack = module.getStackInSlot(i);

                        if (!stack.isEmpty() && !(stack.getItem() instanceof WarframeModuleBase)) {
                            LogUtil.warn("检测到非法物品在战甲军械库槽位" + i + ": " + stack.getDisplayName() + " - 正在返还");

                            if (!this.mergeItemStack(stack, 8, 8 + 36, true)) {
                                LogUtil.warn("玩家背包已满，物品掉落到地上: " + stack.getDisplayName());
                                world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy()));
                            }

                            module.setStackInSlot(i, ItemStack.EMPTY);
                        }
                    }

                    warframeModules.setOne(module.getStackInSlot(0));
                    warframeModules.setTwo(module.getStackInSlot(1));
                    warframeModules.setThree(module.getStackInSlot(2));
                    warframeModules.setFour(module.getStackInSlot(3));
                    warframeModules.setFive(module.getStackInSlot(4));
                    warframeModules.setSix(module.getStackInSlot(5));
                    warframeModules.setSeven(module.getStackInSlot(6));
                    warframeModules.setEight(module.getStackInSlot(7));

                    LogUtil.debugEvent("战甲军械库容器关闭", playerIn.getName(), "战甲模组配置已保存");
                } else {
                    LogUtil.error("无法保存战甲模组：WarframeModules Capability为null");
                }
            } catch (Exception e) {
                LogUtil.error("保存战甲模组时发生错误", e);
            }
        }
        super.onContainerClosed(playerIn);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    public class ModuleSlot extends SlotItemHandler {
        public int index;
        public EntityPlayer entityPlayer;

        public ModuleSlot(EntityPlayer entityPlayer, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.index = index;
            this.entityPlayer = entityPlayer;
        }

        @Override
        public ItemStack onTake(EntityPlayer thePlayer, ItemStack stack) {
            ItemStack result = super.onTake(thePlayer, stack);

            if (!world.isRemote) {
                try {
                    LogUtil.debugEvent("战甲模组取出", stack.getDisplayName(), "槽位: " + index);
                    syncToCapability();
                } catch (Exception e) {
                    LogUtil.error("战甲模组取出时同步失败", e);
                }
            }

            return result;
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            if (!(itemStack.getItem() instanceof WarframeModuleBase)) {
                return false;
            }
            if (WarframeModuleBase.isRandom(itemStack)) {
                return false;
            }
            if (!this.getItemHandler().getStackInSlot(index).isEmpty()) {
                return false;
            }

            // ✅ 使用新的冲突检测系统
            for (int i = 0; i < 8; i++) {
                if (i != index) {
                    ItemStack existingStack = module.getStackInSlot(i);
                    if (!existingStack.isEmpty()) {
                        // 紫卡特殊处理（只能装一个）
                        if (itemStack.getItem() instanceof WarframeRivenModule &&
                                existingStack.getItem() instanceof WarframeRivenModule) {
                            return false;
                        }

                        // ✅ 双向冲突检测（type + 冲突标签）
                        if (ModuleBase.hasConflict(existingStack, itemStack)) {
                            return false;
                        }
                    }
                }
            }
            return super.isItemValid(itemStack);
        }

        @Override
        public void putStack(ItemStack itemStack) {
            super.putStack(itemStack);

            if (!world.isRemote) {
                try {
                    if (!itemStack.isEmpty()) {
                        if (!(itemStack.getItem() instanceof WarframeModuleBase)) {
                            LogUtil.error("警告：非战甲模组被放入槽位" + index + " - " + itemStack.getDisplayName());
                            module.setStackInSlot(index, ItemStack.EMPTY);
                            return;
                        }
                        LogUtil.debugEvent("战甲模组装备", itemStack.getDisplayName(), "槽位: " + index);
                    }
                    syncToCapability();
                } catch (Exception e) {
                    LogUtil.error("战甲模组装备时同步失败", e);
                }
            }
        }

        @Override
        public void onSlotChanged() {
            super.onSlotChanged();

            if (!world.isRemote) {
                try {
                    LogUtil.debug("战甲模组槽位" + index + "内容变化，触发同步");
                    syncToCapability();
                } catch (Exception e) {
                    LogUtil.error("槽位变化同步失败", e);
                }
            }
        }

        private void syncToCapability() {
            try {
                WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);

                if (warframeModules != null) {
                    warframeModules.setOne(module.getStackInSlot(0));
                    warframeModules.setTwo(module.getStackInSlot(1));
                    warframeModules.setThree(module.getStackInSlot(2));
                    warframeModules.setFour(module.getStackInSlot(3));
                    warframeModules.setFive(module.getStackInSlot(4));
                    warframeModules.setSix(module.getStackInSlot(5));
                    warframeModules.setSeven(module.getStackInSlot(6));
                    warframeModules.setEight(module.getStackInSlot(7));

                    LogUtil.debug("战甲模组同步到Capability成功");
                } else {
                    LogUtil.error("同步失败：无法获取WarframeModules Capability");
                }
            } catch (Exception e) {
                LogUtil.error("同步战甲模组到Capability时发生错误", e);
            }
        }
    }
}