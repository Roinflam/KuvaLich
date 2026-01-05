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
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 安魂之铸容器类
 *
 * 用于合成随机物品
 *
 * 功能：
 * 1. 3张安魂卡 -> 1张随机安魂卡
 * 2. 3个同级模组 -> 1个同级随机模组
 *
 * 合成规则：
 * - 青铜模组 + 青铜模组 + 青铜模组 = 随机青铜模组
 * - 白银模组 + 白银模组 + 白银模组 = 随机白银模组
 * - 黄金模组 + 黄金模组 + 黄金模组 = 随机黄金模组
 * - Prime模组 + Prime模组 + Prime模组 = 随机Prime模组
 * - 不接受裂罅模组
 *
 * @author RoinFlam
 */
public class ContainerRequiemRecast extends Container {
    private final World world;
    private final BlockPos pos;

    /** 3个输入槽位 + 1个输出槽位 */
    private final ItemStackHandler card;

    /**
     * 构造容器
     *
     * @param entityPlayer 玩家实体
     * @param world 世界对象
     * @param pos 方块位置
     */
    public ContainerRequiemRecast(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;

        // 初始化槽位（3个输入 + 1个输出）
        this.card = new ItemStackHandler(4);

        // 添加输入槽位（下方3个）
        this.addSlotToContainer(new InputSlot(this.card, 0, 31, 54));
        this.addSlotToContainer(new InputSlot(this.card, 1, 80, 54));
        this.addSlotToContainer(new InputSlot(this.card, 2, 129, 54));

        // 添加输出槽位（上方中间）
        this.addSlotToContainer(new OutputSlot(this.card, 3, 81, 10));

        // 添加玩家背包槽位
        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 152 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 94 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 112 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 130 - 10));
        }

        LogUtil.debugEvent("安魂之铸容器创建", entityPlayer.getName(), "位置: " + pos.toString());
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
     * 槽位点击事件处理
     *
     * 检测输入槽位的物品，如果满足条件则生成输出
     */
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (!player.world.isRemote) {
            new SynchronizationTask() {
                @Override
                public void run() {
                    try {
                        handleCrafting();
                    } catch (Exception e) {
                        LogUtil.error("安魂之铸合成逻辑发生错误", e);
                        card.setStackInSlot(3, ItemStack.EMPTY);
                    }
                }
            }.start();
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    /**
     * 处理合成逻辑
     *
     * 检查3个输入槽位：
     * 1. 如果都是安魂卡 -> 生成随机安魂卡
     * 2. 如果都是同级模组 -> 生成同级随机模组
     * 3. 否则清空输出槽
     */
    private void handleCrafting() {
        ItemStack slot0 = card.getStackInSlot(0);
        ItemStack slot1 = card.getStackInSlot(1);
        ItemStack slot2 = card.getStackInSlot(2);
        ItemStack output = card.getStackInSlot(3);

        // 如果输出槽已有物品，说明玩家还没取走，不处理
        if (!output.isEmpty()) {
            return;
        }

        // 检查是否3个槽位都有物品
        if (slot0.isEmpty() || slot1.isEmpty() || slot2.isEmpty()) {
            return;
        }

        // 情况1：3张安魂卡合成
        if (slot0.getItem() instanceof RequiemCardBase &&
                slot1.getItem() instanceof RequiemCardBase &&
                slot2.getItem() instanceof RequiemCardBase) {

            handleRequiemCardCrafting();
            return;
        }

        // 情况2：3个同级模组合成
        if (slot0.getItem() instanceof ItemModuleBase &&
                slot1.getItem() instanceof ItemModuleBase &&
                slot2.getItem() instanceof ItemModuleBase) {

            // 不接受裂罅模组
            if (slot0.getItem() instanceof ItemRivenModule ||
                    slot1.getItem() instanceof ItemRivenModule ||
                    slot2.getItem() instanceof ItemRivenModule) {
                LogUtil.debug("拒绝合成：不接受裂罅模组");
                return;
            }

            handleModuleCrafting();
            return;
        }
    }

    /**
     * 处理安魂卡合成
     *
     * 3张安魂卡 -> 1张随机安魂卡
     */
    private void handleRequiemCardCrafting() {
        try {
            // 消耗输入物品
            card.setStackInSlot(0, ItemStack.EMPTY);
            card.setStackInSlot(1, ItemStack.EMPTY);
            card.setStackInSlot(2, ItemStack.EMPTY);

            // 生成随机安魂卡（0-7共8种）
            int randomId = world.rand.nextInt(8);
            ItemStack result = new ItemStack(RequiemCardBase.getCard(randomId));
            card.insertItem(3, result, false);

            LogUtil.debugEvent("安魂卡合成", "安魂之铸",
                    "3张卡片 -> " + result.getDisplayName());
        } catch (Exception e) {
            LogUtil.error("安魂卡合成失败", e);
        }
    }

    /**
     * 处理模组合成
     *
     * 3个同级模组 -> 1个同级随机模组
     *
     * 性能优化：使用instanceof替代多次类型检查
     */
    private void handleModuleCrafting() {
        ItemStack slot0 = card.getStackInSlot(0);
        Class<?> moduleClass = slot0.getItem().getClass();

        try {
            ItemStack result = null;

            // 使用instanceof链式判断，减少类型检查次数
            if (slot0.getItem() instanceof ItemCommonModule) {
                result = ItemCommonModule.getRandomModule();
                LogUtil.debug("合成青铜模组");
            } else if (slot0.getItem() instanceof ItemUncommonModule) {
                result = ItemUncommonModule.getRandomModule();
                LogUtil.debug("合成白银模组");
            } else if (slot0.getItem() instanceof ItemRareModule) {
                result = ItemRareModule.getRandomModule();
                LogUtil.debug("合成黄金模组");
            } else if (slot0.getItem() instanceof ItemPrimeModule) {
                result = ItemPrimeModule.getRandomModule();
                LogUtil.debug("合成Prime模组");
            }

            if (result != null) {
                // 消耗输入物品
                card.setStackInSlot(0, ItemStack.EMPTY);
                card.setStackInSlot(1, ItemStack.EMPTY);
                card.setStackInSlot(2, ItemStack.EMPTY);

                // 设置输出
                card.insertItem(3, result, false);

                LogUtil.debugEvent("模组合成", "安魂之铸",
                        "3个" + moduleClass.getSimpleName() + " -> " + result.getDisplayName());
            }
        } catch (Exception e) {
            LogUtil.error("模组合成失败", e);
        }
    }

    /**
     * 容器关闭时的处理
     *
     * 返还所有物品给玩家
     */
    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            // 返还所有槽位的物品
            for (int i = 0; i < 4; i++) {
                if (!card.getStackInSlot(i).isEmpty()) {
                    ItemStack stack = card.getStackInSlot(i);
                    if (!this.mergeItemStack(stack, 4, 4 + 36, true)) {
                        LogUtil.warn("玩家背包已满，物品掉落: " + stack.getDisplayName());
                        world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), stack));
                    }
                }
            }
        }
        super.onContainerClosed(playerIn);
    }

    /**
     * 检查玩家是否可以与容器交互
     */
    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    /**
     * 输入槽位类
     *
     * 接受：安魂卡、非裂罅模组
     */
    public static class InputSlot extends SlotItemHandler {
        public InputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            // 接受安魂卡
            if (itemStack.getItem() instanceof RequiemCardBase) {
                return super.isItemValid(itemStack);
            }
            // 接受模组（但不接受裂罅模组）
            if (itemStack.getItem() instanceof ItemModuleBase &&
                    !(itemStack.getItem() instanceof ItemRivenModule)) {
                return super.isItemValid(itemStack);
            }
            return false;
        }
    }

    /**
     * 输出槽位类
     *
     * 只读，不接受任何物品放入
     */
    public static class OutputSlot extends SlotItemHandler {
        public OutputSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return false;
        }
    }
}