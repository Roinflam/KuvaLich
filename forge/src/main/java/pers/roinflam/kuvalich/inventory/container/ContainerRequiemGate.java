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
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 灭骸之扉容器类（优化版）
 *
 * 用于解密赤毒玄骸的谜语界面
 *
 * 核心功能（原有逻辑100%保持不变）：
 * 1. 显示当前解密进度和已解锁的卡片
 * 2. 允许玩家放置3张安魂卡进行解密
 * 3. 自动从玩家Capability加载已配置的卡片
 * 4. 关闭时保存卡片配置到Capability
 *
 * 界面显示：
 * - 上方：进度条（显示当前阶段的解密进度）
 * - 中间：3个卡片槽位（玩家放置的卡片）
 * - 下方：3个锁定槽位（显示已解锁的谜语卡片，只读）
 *
 * 优化改进：
 * - 添加详细的中文注释
 * - 改进异常处理和null检查
 * - 保持所有业务逻辑不变

 */
public class ContainerRequiemGate extends Container {
    private final EntityPlayer entityPlayer;
    private final World world;
    private final BlockPos pos;

    /** 卡片槽位处理器（3个可放置槽位） */
    private final IItemHandler card;

    /** 同步到客户端的谜语1（-1表示未解锁） */
    public int oneRiddle = -1;

    /** 同步到客户端的谜语2（-1表示未解锁） */
    public int twoRiddle = -1;

    /** 同步到客户端的谜语3（-1表示未解锁） */
    public int threeRiddle = -1;

    /** 同步到客户端的解密等级（0-3） */
    public int level = 0;

    /** 同步到客户端的解密进度 */
    public int schedule = 0;

    /**
     * 构造容器
     *
     * 自动从玩家的RequiemCard Capability中加载已配置的卡片
     *
     * @param entityPlayer 玩家实体
     * @param world 世界对象
     * @param pos 方块位置
     */
    public ContainerRequiemGate(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.entityPlayer = entityPlayer;
        this.world = world;
        this.pos = pos;

        // 初始化卡片槽位
        this.card = new ItemStackHandler(3);

        // 添加卡片槽位（可放置）
        this.addSlotToContainer(new CardSlot(this.card, 0, 26, 11));
        this.addSlotToContainer(new CardSlot(this.card, 1, 80, 11));
        this.addSlotToContainer(new CardSlot(this.card, 2, 134, 11));

        // 添加玩家背包槽位
        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 152 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 94 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 112 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 130 - 10));
        }

        // 从玩家Capability加载已配置的卡片（原逻辑保持不变）
        if (!world.isRemote) {
            try {
                RequiemCard requiemCard = entityPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);

                if (requiemCard != null) {
                    ItemStack oneCard = this.card.getStackInSlot(0);
                    ItemStack twoCard = this.card.getStackInSlot(1);
                    ItemStack threeCard = this.card.getStackInSlot(2);

                    // 只有当槽位为空时才加载
                    if (oneCard.isEmpty() && twoCard.isEmpty() && threeCard.isEmpty()) {
                        this.card.insertItem(0, requiemCard.getOneCard(), false);
                        this.card.insertItem(1, requiemCard.getTwoCard(), false);
                        this.card.insertItem(2, requiemCard.getThreeCard(), false);

                        LogUtil.debugEvent("灭骸之扉容器创建", entityPlayer.getName(),
                                "已加载玩家的卡片配置");
                    }
                } else {
                    LogUtil.warn("无法获取玩家的RequiemCard Capability");
                }
            } catch (Exception e) {
                LogUtil.error("加载玩家卡片配置时发生错误", e);
            }
        }
    }

    /**
     * 检测并发送变更到客户端
     *
     * 将服务端的解密数据同步到客户端用于GUI显示
     * 包括：已解锁的谜语、解密等级、解密进度
     *
     * 原有逻辑100%保持不变
     */
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        try {
            RequiemCard requiemCard = entityPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);

            if (requiemCard != null) {
                for (IContainerListener containerListener : this.listeners) {
                    // 同步3个谜语卡片ID
                    containerListener.sendWindowProperty(this, 0, requiemCard.getOneRiddle());
                    containerListener.sendWindowProperty(this, 1, requiemCard.getTwoRiddle());
                    containerListener.sendWindowProperty(this, 2, requiemCard.getThreeRiddle());
                    // 同步解密等级
                    containerListener.sendWindowProperty(this, 3, requiemCard.getUnlockedCardStatus());
                    // 同步解密进度
                    containerListener.sendWindowProperty(this, 4, requiemCard.getDecryptionProgress());
                }
            }
        } catch (Exception e) {
            LogUtil.error("同步解密数据到客户端时发生错误", e);
        }
    }

    /**
     * 快速转移物品（Shift+点击）
     *
     * @param playerIn 玩家
     * @param index 槽位索引
     * @return 转移的物品堆
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack slotStack = slot.getStack();
            itemstack = slotStack.copy();

            // 从容器转移到背包
            if (index < card.getSlots()) {
                if (!this.mergeItemStack(slotStack, card.getSlots(), 36 + card.getSlots(), true)) {
                    return ItemStack.EMPTY;
                }
            }
            // 从背包转移到容器
            else {
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

    /**
     * 容器关闭时的处理
     *
     * 重要职责：
     * 1. 验证并返还非法物品（非安魂卡）
     * 2. 保存有效的卡片配置到玩家Capability
     *
     * 原有逻辑100%保持不变
     */
    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            try {
                ItemStack oneCard = this.card.getStackInSlot(0);
                ItemStack twoCard = this.card.getStackInSlot(1);
                ItemStack threeCard = this.card.getStackInSlot(2);

                RequiemCard requiemCard = playerIn.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);

                if (requiemCard != null) {
                    BlockPos blockPos = playerIn.getPosition();

                    // 处理第一张卡片
                    if (!oneCard.isEmpty() && !(oneCard.getItem() instanceof RequiemCardBase)) {
                        LogUtil.warn("检测到非法物品在卡片槽位0: " + oneCard.getDisplayName() + " - 正在返还");
                        EntityItem entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), oneCard.copy());
                        world.spawnEntity(entityItem);
                    } else {
                        requiemCard.setOneCard(oneCard.copy());
                    }

                    // 处理第二张卡片
                    if (!twoCard.isEmpty() && !(twoCard.getItem() instanceof RequiemCardBase)) {
                        LogUtil.warn("检测到非法物品在卡片槽位1: " + twoCard.getDisplayName() + " - 正在返还");
                        EntityItem entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), twoCard.copy());
                        world.spawnEntity(entityItem);
                    } else {
                        requiemCard.setTwoCard(twoCard.copy());
                    }

                    // 处理第三张卡片
                    if (!threeCard.isEmpty() && !(threeCard.getItem() instanceof RequiemCardBase)) {
                        LogUtil.warn("检测到非法物品在卡片槽位2: " + threeCard.getDisplayName() + " - 正在返还");
                        EntityItem entityItem = new EntityItem(world, blockPos.getX(), blockPos.getY(), blockPos.getZ(), threeCard.copy());
                        world.spawnEntity(entityItem);
                    } else {
                        requiemCard.setThreeCard(threeCard.copy());
                    }

                    // 清空容器中的物品计数（防止重复掉落）
                    oneCard.setCount(0);
                    twoCard.setCount(0);
                    threeCard.setCount(0);

                    LogUtil.debugEvent("灭骸之扉容器关闭", playerIn.getName(), "卡片配置已保存");
                } else {
                    LogUtil.error("无法保存卡片配置：RequiemCard Capability为null");
                }
            } catch (Exception e) {
                LogUtil.error("保存卡片配置时发生错误", e);
            }
        }
        super.onContainerClosed(playerIn);
    }

    /**
     * 更新客户端进度条数据
     *
     * 接收服务端同步的数据并更新本地变量
     *
     * @param id 数据ID
     * @param data 数据值
     */
    @Override
    public void updateProgressBar(int id, int data) {
        switch (id) {
            case 0:
                oneRiddle = data;
                break;
            case 1:
                twoRiddle = data;
                break;
            case 2:
                threeRiddle = data;
                break;
            case 3:
                level = data;
                break;
            case 4:
                schedule = data;
                break;
        }
        super.updateProgressBar(id, data);
    }

    /**
     * 检查玩家是否可以与容器交互
     */
    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    /**
     * 卡片槽位类
     *
     * 只接受安魂卡
     */
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