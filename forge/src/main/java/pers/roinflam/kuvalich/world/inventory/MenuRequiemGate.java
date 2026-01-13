package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 灭骸之扉菜单类（1.20.1版本）
 * Requiem Gate Menu (1.20.1 version)
 *
 * 业务逻辑100%保持不变，仅API迁移
 * Business logic 100% unchanged, only API migration
 *
 * 核心功能：
 * Core functions:
 * 1. 显示当前解密进度和已解锁的卡片
 * 2. 允许玩家放置3张安魂卡进行解密
 * 3. 自动从玩家Capability加载已配置的卡片
 * 4. 关闭时保存卡片配置到Capability
 */
public class MenuRequiemGate extends AbstractContainerMenu {

    private final Player player;
    private final Level level;
    private final BlockPos pos;

    /** 卡片槽位处理器（3个可放置槽位）/ Card slot handler (3 slots) */
    private final ItemStackHandler cardHandler;

    /** 同步数据容器 / Synchronized data container */
    private final ContainerData data;

    /** 数据索引常量 / Data index constants */
    private static final int DATA_ONE_RIDDLE = 0;
    private static final int DATA_TWO_RIDDLE = 1;
    private static final int DATA_THREE_RIDDLE = 2;
    private static final int DATA_LEVEL = 3;
    private static final int DATA_SCHEDULE = 4;
    private static final int DATA_COUNT = 5;

    /**
     * 服务端构造（从方块打开）
     * Server-side constructor (opened from block)
     */
    public MenuRequiemGate(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_GATE.get(), windowId);

        this.player = playerInventory.player;
        this.level = level;
        this.pos = pos;
        this.cardHandler = new ItemStackHandler(3);
        this.data = new SimpleContainerData(DATA_COUNT);

        // 添加卡片槽位（可放置）/ Add card slots (placeable)
        this.addSlot(new CardSlot(this.cardHandler, 0, 26, 11));
        this.addSlot(new CardSlot(this.cardHandler, 1, 80, 11));
        this.addSlot(new CardSlot(this.cardHandler, 2, 134, 11));

        // 添加玩家背包槽位 / Add player inventory slots
        // 快捷栏 / Hotbar
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 152 - 10));
        }
        // 主背包 / Main inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 94 - 10 + row * 18));
            }
        }

        // 从玩家Capability加载已配置的卡片（业务逻辑不变）
        // Load configured cards from player Capability (business logic unchanged)
        if (!level.isClientSide) {
            loadCardsFromCapability();
        }

        // 添加数据同步 / Add data synchronization
        this.addDataSlots(this.data);
    }

    /**
     * 从Capability加载卡片（业务逻辑100%保持不变）
     * Load cards from Capability (business logic 100% unchanged)
     */
    private void loadCardsFromCapability() {
        try {
            player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                ItemStack oneCard = cardHandler.getStackInSlot(0);
                ItemStack twoCard = cardHandler.getStackInSlot(1);
                ItemStack threeCard = cardHandler.getStackInSlot(2);

                // 只有当槽位为空时才加载 / Only load if slots are empty
                if (oneCard.isEmpty() && twoCard.isEmpty() && threeCard.isEmpty()) {
                    cardHandler.insertItem(0, requiemCard.getOneCard(), false);
                    cardHandler.insertItem(1, requiemCard.getTwoCard(), false);
                    cardHandler.insertItem(2, requiemCard.getThreeCard(), false);

                    LogUtil.debugEvent("灭骸之扉菜单创建", player.getName().getString(),
                            "已加载玩家的卡片配置");
                }
            });
        } catch (Exception e) {
            LogUtil.error("加载玩家卡片配置时发生错误", e);
        }
    }

    /**
     * 同步数据到客户端（业务逻辑100%保持不变）
     * Sync data to client (business logic 100% unchanged)
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!level.isClientSide) {
            try {
                player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                    // 同步谜语卡片ID / Sync riddle card IDs
                    data.set(DATA_ONE_RIDDLE, requiemCard.getOneRiddle());
                    data.set(DATA_TWO_RIDDLE, requiemCard.getTwoRiddle());
                    data.set(DATA_THREE_RIDDLE, requiemCard.getThreeRiddle());
                    // 同步解密等级 / Sync decryption level
                    data.set(DATA_LEVEL, requiemCard.getUnlockedCardStatus());
                    // 同步解密进度 / Sync decryption progress
                    data.set(DATA_SCHEDULE, requiemCard.getDecryptionProgress());
                });
            } catch (Exception e) {
                LogUtil.error("同步解密数据到客户端时发生错误", e);
            }
        }
    }

    /**
     * 快速转移物品（Shift+点击，业务逻辑100%保持不变）
     * Quick move item (Shift+Click, business logic 100% unchanged)
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // 从容器转移到背包 / From container to inventory
            if (index < 3) {
                if (!this.moveItemStackTo(slotStack, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // 从背包转移到容器 / From inventory to container
            else {
                if (!this.moveItemStackTo(slotStack, 0, 3, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    /**
     * 容器关闭时的处理（业务逻辑100%保持不变）
     * Handle container close (business logic 100% unchanged)
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            saveCardsToCapability(player);
        }
    }

    /**
     * 保存卡片到Capability（业务逻辑100%保持不变）
     * Save cards to Capability (business logic 100% unchanged)
     */
    private void saveCardsToCapability(Player player) {
        try {
            ItemStack oneCard = cardHandler.getStackInSlot(0);
            ItemStack twoCard = cardHandler.getStackInSlot(1);
            ItemStack threeCard = cardHandler.getStackInSlot(2);

            player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                BlockPos blockPos = player.blockPosition();

                // 处理第一张卡片 / Handle first card
                if (!oneCard.isEmpty() && !(oneCard.getItem() instanceof RequiemCardBase)) {
                    LogUtil.warn("检测到非法物品在卡片槽位0: " + oneCard.getHoverName().getString() + " - 正在返还");
                    level.addFreshEntity(new ItemEntity(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), oneCard.copy()));
                } else {
                    requiemCard.setOneCard(oneCard.copy());
                }

                // 处理第二张卡片 / Handle second card
                if (!twoCard.isEmpty() && !(twoCard.getItem() instanceof RequiemCardBase)) {
                    LogUtil.warn("检测到非法物品在卡片槽位1: " + twoCard.getHoverName().getString() + " - 正在返还");
                    level.addFreshEntity(new ItemEntity(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), twoCard.copy()));
                } else {
                    requiemCard.setTwoCard(twoCard.copy());
                }

                // 处理第三张卡片 / Handle third card
                if (!threeCard.isEmpty() && !(threeCard.getItem() instanceof RequiemCardBase)) {
                    LogUtil.warn("检测到非法物品在卡片槽位2: " + threeCard.getHoverName().getString() + " - 正在返还");
                    level.addFreshEntity(new ItemEntity(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), threeCard.copy()));
                } else {
                    requiemCard.setThreeCard(threeCard.copy());
                }

                // 清空容器中的物品计数（防止重复掉落）
                // Clear item counts in container (prevent duplicate drops)
                oneCard.setCount(0);
                twoCard.setCount(0);
                threeCard.setCount(0);

                LogUtil.debugEvent("灭骸之扉菜单关闭", player.getName().getString(), "卡片配置已保存");
            });
        } catch (Exception e) {
            LogUtil.error("保存卡片配置时发生错误", e);
        }
    }

    /**
     * 检查玩家是否可以与菜单交互
     * Check if player can interact with menu
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    /**
     * 获取同步数据（供GUI使用）
     * Get synchronized data (for GUI use)
     */
    public int getOneRiddle() {
        return data.get(DATA_ONE_RIDDLE);
    }

    public int getTwoRiddle() {
        return data.get(DATA_TWO_RIDDLE);
    }

    public int getThreeRiddle() {
        return data.get(DATA_THREE_RIDDLE);
    }

    public int getLevel() {
        return data.get(DATA_LEVEL);
    }

    public int getSchedule() {
        return data.get(DATA_SCHEDULE);
    }

    /**
     * 卡片槽位类（只接受安魂卡，业务逻辑不变）
     * Card slot class (only accepts Requiem Cards, business logic unchanged)
     */
    public static class CardSlot extends SlotItemHandler {
        public CardSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return (stack.getItem() instanceof RequiemCardBase) && super.mayPlace(stack);
        }
    }
}