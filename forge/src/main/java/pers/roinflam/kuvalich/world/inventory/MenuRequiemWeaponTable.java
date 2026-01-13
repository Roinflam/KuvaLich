package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
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
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 武器军械库菜单（1.20.1版本，业务逻辑100%不变）
 * Weapon Table Menu (1.20.1 version, business logic 100% unchanged)
 */
public class MenuRequiemWeaponTable extends AbstractContainerMenu {

    private final Level level;
    private final BlockPos pos;
    private final Player player;

    public ItemStackHandler moduleHandler = null;
    public ItemStackHandler weaponHandler = null;

    private boolean synchronize = false;

    /** 模组槽位限制NBT键名 / Module limit NBT key */
    private static final String MODULE_LIMIT_KEY = "moduleLimit";

    public MenuRequiemWeaponTable(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_WEAPON_TABLE.get(), windowId);

        this.level = level;
        this.pos = pos;
        this.player = playerInventory.player;

        this.moduleHandler = new ItemStackHandler(8);
        this.weaponHandler = new ItemStackHandler(1);

        // 添加模组槽位（8个） / Add module slots (8 slots)
        for (int i = 0; i < 4; i++) {
            this.addSlot(new ModuleSlot(this, i, 18 + 41 * i, 12));
        }
        for (int i = 0; i < 4; i++) {
            this.addSlot(new ModuleSlot(this, i + 4, 18 + 41 * i, 39));
        }

        // 添加武器槽位 / Add weapon slot
        this.addSlot(new WeaponSlot(this, 0, 80, 67));

        // 添加玩家背包槽位 / Add player inventory slots
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 151));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 93 + row * 18));
            }
        }

        LogUtil.debugEvent("武器军械库菜单创建", player.getName().getString(), "位置: " + pos.toString());
    }

    /**
     * 获取当前武器的模组槽位限制（业务逻辑100%不变）
     * Get current weapon's module slot limit (business logic 100% unchanged)
     */
    public int getModuleLimit() {
        ItemStack weaponStack = weaponHandler.getStackInSlot(0);

        if (weaponStack == null || weaponStack.isEmpty()) {
            return 0;
        }

        CompoundTag tag = weaponStack.getTag();
        if (tag == null) {
            return 8;
        }

        if (!tag.contains(MODULE_LIMIT_KEY)) {
            return 8;
        }

        int limit = tag.getInt(MODULE_LIMIT_KEY);
        return Math.max(0, Math.min(8, limit));
    }

    /**
     * 检查指定槽位是否已解锁（业务逻辑100%不变）
     * Check if specified slot is unlocked (business logic 100% unchanged)
     */
    public boolean isSlotUnlocked(int slotIndex) {
        return slotIndex < getModuleLimit();
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

        if (index < 9) {
            if (index == 8) {
                // 武器槽位 / Weapon slot
                LogUtil.debug("Shift取出武器: " + slotStack.getHoverName().getString());

                if (!level.isClientSide) {
                    // ⭐ 关键修复：先同步模组到武器NBT，再转移武器
                    syncAllModulesToWeapon();
                }

                if (!this.moveItemStackTo(slotStack, 9, 45, true)) {
                    LogUtil.debug("背包已满，无法转移武器");
                    return ItemStack.EMPTY;
                }

                if (!level.isClientSide) {
                    LogUtil.debug("武器被取出，清空所有模组槽");
                    for (int i = 0; i < 8; i++) {
                        this.getSlot(i).set(ItemStack.EMPTY);
                        this.moduleHandler.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    this.broadcastChanges();
                }
            } else {
                // 模组槽位 / Module slot
                LogUtil.debug("从模组槽位" + index + "转移到背包: " + slotStack.getHoverName().getString());

                if (!this.moveItemStackTo(slotStack, 9, 45, true)) {
                    LogUtil.debug("背包已满，无法转移");
                    return ItemStack.EMPTY;
                }
            }
        } else {
            // 从背包转移 / From inventory
            boolean transferred = false;

            if (itemstack.getItem() instanceof ItemModuleBase) {
                // 只尝试放入已解锁的槽位 / Only try unlocked slots
                int limit = getModuleLimit();
                for (int i = 0; i < limit; i++) {
                    if (this.moveItemStackTo(slotStack, i, i + 1, false)) {
                        transferred = true;
                        break;
                    }
                }
                if (!transferred) {
                    return ItemStack.EMPTY;
                }
            } else if (ItemModule.hasBase(itemstack)) {
                if (!this.moveItemStackTo(slotStack, 8, 9, false)) {
                    return ItemStack.EMPTY;
                }
                transferred = true;
            } else {
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

    /**
     * 同步所有模组到武器NBT（修复无限复制bug）
     * Sync all modules to weapon NBT (fix duplication bug)
     */
    private void syncAllModulesToWeapon() {
        ItemStack weaponItemStack = weaponHandler.getStackInSlot(0);

        if (weaponItemStack == null || weaponItemStack.isEmpty()) {
            return;
        }

        try {
            weaponItemStack = weaponItemStack.copy();

            CompoundTag nbtTagCompound = weaponItemStack.serializeNBT();
            CompoundTag tag = nbtTagCompound.contains("tag") ?
                    nbtTagCompound.getCompound("tag") : new CompoundTag();

            String weaponModuleKey = Reference.MOD_ID + "_weaponModules";
            CompoundTag weaponModule = new CompoundTag();

            ListTag itemList = new ListTag();
            for (int i = 0; i < 8; i++) {
                CompoundTag itemTag = new CompoundTag();
                ItemStack moduleStack = moduleHandler.getStackInSlot(i);

                if (moduleStack != null && !moduleStack.isEmpty()) {
                    if (moduleStack.getItem() instanceof ItemModuleBase) {
                        moduleStack.save(itemTag);
                    } else {
                        ItemStack.EMPTY.save(itemTag);
                    }
                } else {
                    ItemStack.EMPTY.save(itemTag);
                }
                itemList.add(itemTag);
            }

            weaponModule.put("modules", itemList);
            tag.put(weaponModuleKey, weaponModule);
            nbtTagCompound.put("tag", tag);
            weaponItemStack.setTag(tag);

            weaponHandler.setStackInSlot(0, weaponItemStack);
            this.slots.get(8).set(weaponItemStack);

            LogUtil.debug("武器模组数据同步完成（Shift转移前）");
        } catch (Exception e) {
            LogUtil.error("同步武器模组数据时发生错误", e);
        }
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            ItemStack weaponStack = weaponHandler.getStackInSlot(0);
            if (!weaponStack.isEmpty()) {
                if (!this.moveItemStackTo(weaponStack, 9, 45, true)) {
                    level.addFreshEntity(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), weaponStack));
                }
            }
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    /**
     * 武器槽位类（业务逻辑100%不变）
     * Weapon slot class (business logic 100% unchanged)
     */
    public static class WeaponSlot extends SlotItemHandler {
        private final MenuRequiemWeaponTable menu;

        public WeaponSlot(MenuRequiemWeaponTable menu, int index, int xPosition, int yPosition) {
            super(menu.weaponHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            if (!menu.weaponHandler.getStackInSlot(0).isEmpty()) {
                return false;
            }
            if (!ItemModule.hasBase(stack)) {
                return false;
            }
            return super.mayPlace(stack);
        }

        @Override
        public void set(@NotNull ItemStack weaponItemStack) {
            try {
                if (weaponItemStack == null || weaponItemStack.isEmpty()) {
                    super.set(weaponItemStack);
                    return;
                }

                // 读取武器的槽位限制 / Read weapon's slot limit
                int limit = 8;
                CompoundTag weaponTag = weaponItemStack.getTag();
                if (weaponTag != null && weaponTag.contains(MODULE_LIMIT_KEY)) {
                    limit = Math.max(0, Math.min(8, weaponTag.getInt(MODULE_LIMIT_KEY)));
                }

                LogUtil.debugEvent("武器放入军械库", "武器",
                        weaponItemStack.getHoverName().getString() + " (槽位限制: " + limit + ")");

                CompoundTag nbtTagCompound = weaponItemStack.serializeNBT();
                if (!nbtTagCompound.contains("tag")) {
                    super.set(weaponItemStack);
                    return;
                }

                CompoundTag tag = nbtTagCompound.getCompound("tag");
                String weaponModuleKey = Reference.MOD_ID + "_weaponModules";
                if (!tag.contains(weaponModuleKey)) {
                    super.set(weaponItemStack);
                    return;
                }

                CompoundTag weaponModule = tag.getCompound(weaponModuleKey);
                if (!weaponModule.contains("modules")) {
                    super.set(weaponItemStack);
                    return;
                }

                ListTag itemList = weaponModule.getList("modules", Tag.TAG_COMPOUND);
                int loadedCount = 0;
                menu.synchronize = true;

                // 只加载在槽位限制范围内的模组 / Only load modules within slot limit
                for (int i = 0; i < Math.min(limit, Math.min(8, itemList.size())); i++) {
                    CompoundTag itemTag = itemList.getCompound(i);
                    ItemStack stack = ItemStack.of(itemTag);
                    if (!stack.isEmpty()) {
                        menu.getSlot(i).set(stack);
                        menu.moduleHandler.setStackInSlot(i, stack);
                        loadedCount++;
                    }
                }

                menu.synchronize = false;
                LogUtil.debugEvent("模组加载完成", weaponItemStack.getHoverName().getString(),
                        "成功加载 " + loadedCount + " 个模组（限制: " + limit + "）");

            } catch (Exception e) {
                LogUtil.error("武器放入军械库时发生错误", e);
                for (int i = 0; i < 8; i++) {
                    menu.getSlot(i).set(ItemStack.EMPTY);
                    menu.moduleHandler.setStackInSlot(i, ItemStack.EMPTY);
                }
            }

            super.set(weaponItemStack);
        }
    }

    /**
     * 模组槽位类（业务逻辑100%不变）
     * Module slot class (business logic 100% unchanged)
     */
    public static class ModuleSlot extends SlotItemHandler {
        private final MenuRequiemWeaponTable menu;
        private final int slotIndex;

        public ModuleSlot(MenuRequiemWeaponTable menu, int index, int xPosition, int yPosition) {
            super(menu.moduleHandler, index, xPosition, yPosition);
            this.menu = menu;
            this.slotIndex = index;
        }

        @Override
        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            super.onTake(player, stack);

            if (!menu.level.isClientSide) {
                try {
                    LogUtil.debugEvent("模组从军械库取出", stack.getHoverName().getString(),
                            "槽位: " + slotIndex);
                    syncWeaponNBT();
                } catch (Exception e) {
                    LogUtil.error("模组取出时同步失败", e);
                }
            }
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            if (menu.weaponHandler.getStackInSlot(0).isEmpty()) {
                return false;
            }

            // 检查槽位是否已解锁 / Check if slot is unlocked
            if (!menu.isSlotUnlocked(slotIndex)) {
                LogUtil.debug("槽位 " + slotIndex + " 未解锁，当前限制: " + menu.getModuleLimit());
                return false;
            }

            if (!(stack.getItem() instanceof ItemModuleBase)) {
                return false;
            }
            if (ItemModuleBase.isRandom(stack)) {
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
                        if (stack.getItem() instanceof ItemRivenModule &&
                                existingStack.getItem() instanceof ItemRivenModule) {
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

            if (!menu.synchronize && !menu.level.isClientSide) {
                try {
                    if (stack != null && !stack.isEmpty()) {
                        LogUtil.debugEvent("模组装备到军械库", stack.getHoverName().getString(),
                                "槽位: " + slotIndex);
                    }
                    syncWeaponNBT();
                } catch (Exception e) {
                    LogUtil.error("模组装备时同步失败", e);
                }
            }
        }

        @Override
        public void setChanged() {
            super.setChanged();

            if (!menu.level.isClientSide && !menu.synchronize) {
                try {
                    LogUtil.debug("模组槽位" + slotIndex + "内容变化，触发同步");
                    syncWeaponNBT();
                } catch (Exception e) {
                    LogUtil.error("槽位变化同步失败", e);
                }
            }
        }

        /**
         * 同步武器NBT（业务逻辑100%不变）
         * Sync weapon NBT (business logic 100% unchanged)
         */
        private void syncWeaponNBT() {
            ItemStack weaponItemStack = menu.weaponHandler.getStackInSlot(0);

            if (weaponItemStack == null || weaponItemStack.isEmpty() ||
                    !ItemModule.hasBase(weaponItemStack)) {
                return;
            }

            try {
                weaponItemStack = weaponItemStack.copy();

                CompoundTag nbtTagCompound = weaponItemStack.serializeNBT();
                CompoundTag tag = nbtTagCompound.contains("tag") ?
                        nbtTagCompound.getCompound("tag") : new CompoundTag();

                String weaponModuleKey = Reference.MOD_ID + "_weaponModules";
                CompoundTag weaponModule = tag.contains(weaponModuleKey) ?
                        tag.getCompound(weaponModuleKey) : new CompoundTag();

                ListTag itemList = new ListTag();
                for (int i = 0; i < 8; i++) {
                    CompoundTag itemTag = new CompoundTag();
                    ItemStack moduleStack = menu.moduleHandler.getStackInSlot(i);

                    if (moduleStack != null && !moduleStack.isEmpty()) {
                        if (moduleStack.getItem() instanceof ItemModuleBase) {
                            moduleStack.save(itemTag);
                        } else {
                            ItemStack.EMPTY.save(itemTag);
                        }
                    } else {
                        ItemStack.EMPTY.save(itemTag);
                    }
                    itemList.add(itemTag);
                }

                weaponModule.put("modules", itemList);
                tag.put(weaponModuleKey, weaponModule);
                nbtTagCompound.put("tag", tag);
                weaponItemStack.setTag(tag);

                menu.weaponHandler.setStackInSlot(0, weaponItemStack);
                menu.slots.get(8).set(weaponItemStack);

                if (menu.player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.containerMenu.broadcastChanges();
                }

                LogUtil.debug("武器NBT同步成功");
            } catch (Exception e) {
                LogUtil.error("同步武器NBT时发生错误", e);
            }
        }
    }
}