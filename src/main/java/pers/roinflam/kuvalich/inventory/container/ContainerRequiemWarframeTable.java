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
import pers.roinflam.kuvalich.base.item.WarframeModuleBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 安魂之战甲军械库容器类（支持Shift快速移动）
 *
 * 功能：
 * 1. 从玩家Capability加载已装备的模组
 * 2. 模组放入/取出时立刻同步到玩家Capability
 * 3. ✅ 支持Shift+点击快速移动（双向）
 * 4. 防止装备重复模组
 * 5. 防止装备多个裂罅模组

 */
public class ContainerRequiemWarframeTable extends Container {
    private final World world;
    private final BlockPos pos;

    public ItemStackHandler module = null;

    public ContainerRequiemWarframeTable(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;

        module = new ItemStackHandler(8);

        // 添加模组槽位 - 上排4个
        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i, 18 + 41 * i, 12));
        }
        // 添加模组槽位 - 下排4个
        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i + 4, 18 + 41 * i, 39));
        }

        // 从玩家Capability加载模组
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

        // 添加玩家背包槽位
        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 122));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 64));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 81));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 99));
        }
    }

    /**
     * 快速转移物品（Shift+点击）
     *
     * ✅ 支持双向快速移动：
     * - 从背包到容器：自动放入模组槽
     * - 从容器到背包：取出模组并同步Capability
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot == null || !slot.getHasStack()) {
            return itemstack;
        }

        ItemStack slotStack = slot.getStack();
        itemstack = slotStack.copy();

        // ========== 从容器转移到背包 ==========
        if (index < 8) {
            LogUtil.debug("从战甲模组槽位" + index + "转移到背包: " + slotStack.getDisplayName());

            // ✅ 转移到背包
            if (!this.mergeItemStack(slotStack, 8, 8 + 36, true)) {
                LogUtil.debug("背包已满，无法转移");
                return ItemStack.EMPTY;
            }

            // ✅ 转移后触发槽位变化，会自动调用 onSlotChanged -> 同步Capability
            slot.onSlotChange(slotStack, itemstack);

            LogUtil.debug("战甲模组转移成功，已触发同步");
        }
        // ========== 从背包转移到容器 ==========
        else {
            // 只有战甲模组才能放入
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

        // 更新槽位状态
        if (slotStack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }

        // 如果物品数量没变化，说明转移失败
        if (slotStack.getCount() == itemstack.getCount()) {
            return ItemStack.EMPTY;
        }

        // 通知槽位物品被取走
        slot.onTake(playerIn, slotStack);

        return itemstack;
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            try {
                WarframeModules warframeModules = playerIn.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);

                if (warframeModules != null) {
                    // 遍历所有槽位，检查并处理非法物品
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

                    // 保存合法的模组到Capability
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

    /**
     * 战甲模组槽位类
     */
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

            for (int i = 0; i < 8; i++) {
                if (i != index) {
                    ItemStack existingStack = module.getStackInSlot(i);
                    if (!existingStack.isEmpty()) {
                        if (itemStack.getItem() instanceof WarframeRivenModule &&
                                existingStack.getItem() instanceof WarframeRivenModule) {
                            return false;
                        } else if (WarframeModuleBase.getType(existingStack).equals(WarframeModuleBase.getType(itemStack))) {
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

        /**
         * ✅ 覆盖 onSlotChanged 方法
         * Shift+点击转移时会调用此方法
         */
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