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
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.base.item.WarframeModuleBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 战甲军械库菜单（1.20.1版本，业务逻辑100%不变）
 * Warframe Table Menu (1.20.1 version, business logic 100% unchanged)
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

        // 添加模组槽位（8个）/ Add module slots (8 slots)
        for (int i = 0; i < 4; i++) {
            this.addSlot(new ModuleSlot(this, i, 18 + 41 * i, 12));
        }
        for (int i = 0; i < 4; i++) {
            this.addSlot(new ModuleSlot(this, i + 4, 18 + 41 * i, 39));
        }

        // 从Capability加载战甲模组（业务逻辑100%不变）
        // Load warframe modules from Capability (business logic 100% unchanged)
        if (!level.isClientSide) {
            loadModulesFromCapability(playerInventory.player);
        }

        // 添加玩家背包槽位 / Add player inventory slots
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 122));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 64 + row * 18));
            }
        }
    }

    /**
     * 从Capability加载模组（业务逻辑100%不变）
     * Load modules from Capability (business logic 100% unchanged)
     */
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

                LogUtil.debugEvent("战甲军械库菜单创建", player.getName().getString(),
                        "已加载玩家的战甲模组配置");
            });
        } catch (Exception e) {
            LogUtil.error("加载战甲模组时发生错误", e);
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot == null || !slot.hasItem()) {
            return itemstack;
        }

        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        if (index < 8) {
            // 从模组槽位转移到背包 / From module slot to inventory
            LogUtil.debug("从战甲模组槽位" + index + "转移到背包: " + slotStack.getHoverName().getString());

            if (!this.moveItemStackTo(slotStack, 8, 44, true)) {
                LogUtil.debug("背包已满，无法转移");
                return ItemStack.EMPTY;
            }

            LogUtil.debug("战甲模组转移成功，已触发同步");
        } else {
            // 从背包转移到模组槽位 / From inventory to module slot
            if (itemstack.getItem() instanceof WarframeModuleBase) {
                boolean success = false;
                for (int i = 0; i < 8; i++) {
                    if (this.moveItemStackTo(slotStack, i, i + 1, false)) {
                        success = true;
                        LogUtil.debug("战甲模组从背包转移到槽位" + i + ": " +
                                slotStack.getHoverName().getString());
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

        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotStack.getCount() == itemstack.getCount()) {
            return ItemStack.EMPTY;
        }

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
     * 保存模组到Capability（业务逻辑100%不变）
     * Save modules to Capability (business logic 100% unchanged)
     */
    private void saveModulesToCapability(Player player) {
        try {
            player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).ifPresent(warframeModules -> {
                // 检测并返还非法物品 / Detect and return invalid items
                for (int i = 0; i < 8; i++) {
                    ItemStack stack = moduleHandler.getStackInSlot(i);

                    if (!stack.isEmpty() && !(stack.getItem() instanceof WarframeModuleBase)) {
                        LogUtil.warn("检测到非法物品在战甲军械库槽位" + i + ": " +
                                stack.getHoverName().getString() + " - 正在返还");

                        if (!this.moveItemStackTo(stack, 8, 44, true)) {
                            LogUtil.warn("玩家背包已满，物品掉落到地上: " + stack.getHoverName().getString());
                            level.addFreshEntity(new ItemEntity(level, pos.getX(), pos.getY(),
                                    pos.getZ(), stack.copy()));
                        }

                        moduleHandler.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }

                // 保存到Capability / Save to Capability
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
        } catch (Exception e) {
            LogUtil.error("保存战甲模组时发生错误", e);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    /**
     * 战甲模组槽位类（业务逻辑100%不变）
     * Warframe module slot class (business logic 100% unchanged)
     */
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
                    LogUtil.debugEvent("战甲模组取出", stack.getHoverName().getString(),
                            "槽位: " + slotIndex);
                    syncToCapability();
                } catch (Exception e) {
                    LogUtil.error("战甲模组取出时同步失败", e);
                }
            }
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (!(stack.getItem() instanceof WarframeModuleBase)) {
                return false;
            }
            if (WarframeModuleBase.isRandom(stack)) {
                return false;
            }
            if (!this.getItemHandler().getStackInSlot(slotIndex).isEmpty()) {
                return false;
            }

            // 冲突检测（业务逻辑100%不变）/ Conflict detection (business logic 100% unchanged)
            for (int i = 0; i < 8; i++) {
                if (i != slotIndex) {
                    ItemStack existingStack = menu.moduleHandler.getStackInSlot(i);
                    if (!existingStack.isEmpty()) {
                        // 紫卡特殊处理 / Riven module special handling
                        if (stack.getItem() instanceof WarframeRivenModule &&
                                existingStack.getItem() instanceof WarframeRivenModule) {
                            return false;
                        }

                        // 双向冲突检测 / Bidirectional conflict detection
                        if (ModuleBase.hasConflict(existingStack, stack)) {
                            return false;
                        }
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
                        if (!(stack.getItem() instanceof WarframeModuleBase)) {
                            LogUtil.error("警告：非战甲模组被放入槽位" + slotIndex + " - " +
                                    stack.getHoverName().getString());
                            menu.moduleHandler.setStackInSlot(slotIndex, ItemStack.EMPTY);
                            return;
                        }
                        LogUtil.debugEvent("战甲模组装备", stack.getHoverName().getString(),
                                "槽位: " + slotIndex);
                    }
                    syncToCapability();
                } catch (Exception e) {
                    LogUtil.error("战甲模组装备时同步失败", e);
                }
            }
        }

        @Override
        public void setChanged() {
            super.setChanged();

            if (!menu.level.isClientSide) {
                try {
                    LogUtil.debug("战甲模组槽位" + slotIndex + "内容变化，触发同步");
                    syncToCapability();
                } catch (Exception e) {
                    LogUtil.error("槽位变化同步失败", e);
                }
            }
        }

        /**
         * 同步到Capability（业务逻辑100%不变）
         * Sync to Capability (business logic 100% unchanged)
         */
        private void syncToCapability() {
            try {
                menu.player.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES)
                        .ifPresent(warframeModules -> {
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
            } catch (Exception e) {
                LogUtil.error("同步战甲模组到Capability时发生错误", e);
            }
        }
    }
}