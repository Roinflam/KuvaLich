package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
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
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 战甲军械库菜单（1.20.1版本，业务逻辑100%不变）
 * Warframe Table Menu (1.20.1 version, business logic 100% unchanged)
 *
 * ⭐ 非法物品返还优先快捷栏（Inventory.add）
 */
public class MenuRequiemWarframeTable extends AbstractContainerMenu {

    private final Level level;
    private final BlockPos pos;
    private final Player player;

    public ItemStackHandler moduleHandler = null;

    public MenuRequiemWarframeTable(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_WARFRAME_TABLE.get(), windowId);

        this.level = level;
        this.pos = pos;
        this.player = playerInventory.player;

        this.moduleHandler = new ItemStackHandler(8);

        for (int i = 0; i < 4; i++) {
            this.addSlot(new ModuleSlot(this, i, 18 + 41 * i, 12));
        }
        for (int i = 0; i < 4; i++) {
            this.addSlot(new ModuleSlot(this, i + 4, 18 + 41 * i, 39));
        }

        if (!level.isClientSide) {
            loadModulesFromCapability(playerInventory.player);
        }

        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 122));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 64 + row * 18));
            }
        }
    }

    private void loadModulesFromCapability(Player player) {
        try {
            player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).ifPresent(warframeModules -> {
                this.moduleHandler.insertItem(0, warframeModules.getOne(), false);
                this.moduleHandler.insertItem(1, warframeModules.getTwo(), false);
                this.moduleHandler.insertItem(2, warframeModules.getThree(), false);
                this.moduleHandler.insertItem(3, warframeModules.getFour(), false);
                this.moduleHandler.insertItem(4, warframeModules.getFive(), false);
                this.moduleHandler.insertItem(5, warframeModules.getSix(), false);
                this.moduleHandler.insertItem(6, warframeModules.getSeven(), false);
                this.moduleHandler.insertItem(7, warframeModules.getEight(), false);
                LogUtil.debugEvent("战甲军械库菜单创建", player.getName().getString(), "已加载玩家的战甲模组配置");
            });
        } catch (Exception e) { LogUtil.error("加载战甲模组时发生错误", e); }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return itemstack;

        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        if (index < 8) {
            LogUtil.debug("从战甲模组槽位" + index + "转移到背包: " + slotStack.getHoverName().getString());
            if (!this.moveItemStackTo(slotStack, 8, 44, true)) {
                LogUtil.debug("背包已满，无法转移");
                return ItemStack.EMPTY;
            }
            LogUtil.debug("战甲模组转移成功，已触发同步");
        } else {
            if (itemstack.getItem() instanceof AbstractWarframeModule) {
                boolean success = false;
                for (int i = 0; i < 8; i++) {
                    if (this.moveItemStackTo(slotStack, i, i + 1, false)) {
                        success = true;
                        LogUtil.debug("战甲模组从背包转移到槽位" + i + ": " + slotStack.getHoverName().getString());
                        break;
                    }
                }
                if (!success) {
                    LogUtil.debug("所有战甲模组槽位已满或不兼容");
                    return ItemStack.EMPTY;
                }
            } else {
                LogUtil.debug("拒绝转移：不是战甲模组 - " + slotStack.getHoverName().getString());
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) { slot.set(ItemStack.EMPTY); } else { slot.setChanged(); }
        if (slotStack.getCount() == itemstack.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, slotStack);
        return itemstack;
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (!level.isClientSide) {
            saveModulesToCapability(player);
        }
    }

    /**
     * 保存模组到Capability
     * ⭐ 非法物品返还使用 Inventory.add() 优先快捷栏
     */
    private void saveModulesToCapability(Player player) {
        try {
            player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).ifPresent(warframeModules -> {
                for (int i = 0; i < 8; i++) {
                    ItemStack stack = moduleHandler.getStackInSlot(i);
                    if (!stack.isEmpty() && !(stack.getItem() instanceof AbstractWarframeModule)) {
                        LogUtil.warn("检测到非法物品在战甲军械库槽位" + i + ": " + stack.getHoverName().getString() + " - 正在返还");
                        // ⭐ 优先快捷栏
                        if (!player.getInventory().add(stack.copy())) {
                            player.drop(stack.copy(), false);
                        }
                        moduleHandler.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }

                warframeModules.setOne(moduleHandler.getStackInSlot(0));
                warframeModules.setTwo(moduleHandler.getStackInSlot(1));
                warframeModules.setThree(moduleHandler.getStackInSlot(2));
                warframeModules.setFour(moduleHandler.getStackInSlot(3));
                warframeModules.setFive(moduleHandler.getStackInSlot(4));
                warframeModules.setSix(moduleHandler.getStackInSlot(5));
                warframeModules.setSeven(moduleHandler.getStackInSlot(6));
                warframeModules.setEight(moduleHandler.getStackInSlot(7));
                LogUtil.debugEvent("战甲军械库菜单关闭", player.getName().getString(), "战甲模组配置已保存");
            });
        } catch (Exception e) { LogUtil.error("保存战甲模组时发生错误", e); }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) && player.blockPosition().distSqr(this.pos) <= 64;
    }

    // ============================== 内部槽位类 ==============================

    public static class ModuleSlot extends SlotItemHandler {
        private final MenuRequiemWarframeTable menu;
        private final int slotIndex;

        public ModuleSlot(MenuRequiemWarframeTable menu, int index, int xPosition, int yPosition) {
            super(menu.moduleHandler, index, xPosition, yPosition);
            this.menu = menu;
            this.slotIndex = index;
        }

        @Override
        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            super.onTake(player, stack);
            if (!menu.level.isClientSide) {
                try {
                    LogUtil.debugEvent("战甲模组取出", stack.getHoverName().getString(), "槽位: " + slotIndex);
                    syncToCapability();
                } catch (Exception e) { LogUtil.error("战甲模组取出时同步失败", e); }
            }
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (!(stack.getItem() instanceof AbstractWarframeModule)) return false;
            if (AbstractWarframeModule.isRandom(stack)) return false;
            if (!this.getItemHandler().getStackInSlot(slotIndex).isEmpty()) return false;

            for (int i = 0; i < 8; i++) {
                if (i != slotIndex) {
                    ItemStack existingStack = menu.moduleHandler.getStackInSlot(i);
                    if (!existingStack.isEmpty()) {
                        if (stack.getItem() instanceof WarframeRivenModule && existingStack.getItem() instanceof WarframeRivenModule) return false;
                        if (AbstractModule.hasConflict(existingStack, stack)) return false;
                    }
                }
            }
            return super.mayPlace(stack);
        }

        @Override
        public void set(@NotNull ItemStack stack) {
            super.set(stack);
            if (!menu.level.isClientSide) {
                try {
                    if (!stack.isEmpty()) {
                        if (!(stack.getItem() instanceof AbstractWarframeModule)) {
                            LogUtil.error("警告：非战甲模组被放入槽位" + slotIndex + " - " + stack.getHoverName().getString());
                            menu.moduleHandler.setStackInSlot(slotIndex, ItemStack.EMPTY);
                            return;
                        }
                        LogUtil.debugEvent("战甲模组装备", stack.getHoverName().getString(), "槽位: " + slotIndex);
                    }
                    syncToCapability();
                } catch (Exception e) { LogUtil.error("战甲模组装备时同步失败", e); }
            }
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!menu.level.isClientSide) {
                try {
                    LogUtil.debug("战甲模组槽位" + slotIndex + "内容变化，触发同步");
                    syncToCapability();
                } catch (Exception e) { LogUtil.error("槽位变化同步失败", e); }
            }
        }

        private void syncToCapability() {
            try {
                menu.player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).ifPresent(warframeModules -> {
                    warframeModules.setOne(menu.moduleHandler.getStackInSlot(0));
                    warframeModules.setTwo(menu.moduleHandler.getStackInSlot(1));
                    warframeModules.setThree(menu.moduleHandler.getStackInSlot(2));
                    warframeModules.setFour(menu.moduleHandler.getStackInSlot(3));
                    warframeModules.setFive(menu.moduleHandler.getStackInSlot(4));
                    warframeModules.setSix(menu.moduleHandler.getStackInSlot(5));
                    warframeModules.setSeven(menu.moduleHandler.getStackInSlot(6));
                    warframeModules.setEight(menu.moduleHandler.getStackInSlot(7));
                    LogUtil.debug("战甲模组同步到Capability成功");
                });
            } catch (Exception e) { LogUtil.error("同步战甲模组到Capability时发生错误", e); }
        }
    }
}
