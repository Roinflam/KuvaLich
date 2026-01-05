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
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.blocks.capability.WarframeModules;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 安魂之战甲军械库容器类
 *
 * 用于管理战甲模组的装备界面
 * 支持最多8个模组槽位
 *
 * 核心功能：
 * 1. 从玩家Capability加载已装备的模组
 * 2. 模组放入/取出时自动同步到玩家Capability
 * 3. 防止装备重复模组
 * 4. 防止装备多个裂罅模组
 * 5. 防止放入非战甲模组物品（如武器）
 *
 * @author RoinFlam
 */
public class ContainerRequiemWarframeTable extends Container {
    private final World world;
    private final BlockPos pos;

    /** 模组物品处理器 - 8个槽位 */
    public ItemStackHandler module = null;

    /**
     * 构造容器
     *
     * 自动从玩家的WarframeModules Capability中加载已装备的模组
     *
     * @param entityPlayer 玩家实体
     * @param world 世界对象
     * @param pos 方块位置
     */
    public ContainerRequiemWarframeTable(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;

        // 初始化模组处理器
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

                LogUtil.debugEvent("战甲军械库容器创建", entityPlayer.getName(),
                        "已加载玩家的战甲模组配置");
            } else {
                LogUtil.warn("无法获取玩家的WarframeModules Capability");
            }
        } catch (Exception e) {
            LogUtil.error("加载战甲模组时发生错误", e);
        }

        // 添加玩家背包槽位
        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 122));      // 快捷栏
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 64));   // 背包第一排
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 81));  // 背包第二排
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 99));  // 背包第三排
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
            if (index < 8) {
                if (!this.mergeItemStack(slotStack, 8, 8 + 36, true)) {
                    LogUtil.debug("物品转移失败：背包已满");
                    return ItemStack.EMPTY;
                }
                LogUtil.debug("物品从战甲军械库转移到背包: " + slotStack.getDisplayName());
            }
            // 从背包转移到容器
            else {
                // 只有战甲模组才能放入
                if (itemstack.getItem() instanceof WarframeModuleBase) {
                    boolean success = false;
                    for (int i = 0; i < 8; i++) {
                        if (this.mergeItemStack(slotStack, i, i + 1, false)) {
                            success = true;
                            LogUtil.debug("物品转移到战甲军械库槽位" + i + ": " + slotStack.getDisplayName());
                            break;
                        }
                    }
                    if (!success) {
                        LogUtil.debug("所有战甲模组槽位已满或不兼容");
                        return ItemStack.EMPTY;
                    }
                } else {
                    // 不是战甲模组，禁止转移
                    LogUtil.debug("拒绝转移：不是战甲模组 - " + slotStack.getDisplayName());
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
     * 1. 将所有合法的战甲模组保存到玩家的WarframeModules Capability
     * 2. 将所有非法物品（非战甲模组）返还给玩家或掉落到地上
     */
    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            try {
                WarframeModules warframeModules = playerIn.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);

                if (warframeModules != null) {
                    // 遍历所有槽位
                    for (int i = 0; i < 8; i++) {
                        ItemStack stack = module.getStackInSlot(i);

                        // 检查是否是合法的战甲模组
                        if (!stack.isEmpty() && !(stack.getItem() instanceof WarframeModuleBase)) {
                            // 不是战甲模组，返还给玩家
                            LogUtil.warn("检测到非法物品在战甲军械库槽位" + i + ": " + stack.getDisplayName() + " - 正在返还");

                            if (!this.mergeItemStack(stack, 8, 8 + 36, true)) {
                                // 背包满了，掉落到地上
                                LogUtil.warn("玩家背包已满，物品掉落到地上: " + stack.getDisplayName());
                                world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), stack.copy()));
                            }

                            // 清空槽位
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

                    LogUtil.debugEvent("战甲军械库容器关闭", playerIn.getName(),
                            "战甲模组配置已保存");
                } else {
                    LogUtil.error("无法保存战甲模组：WarframeModules Capability为null");
                }
            } catch (Exception e) {
                LogUtil.error("保存战甲模组时发生错误", e);
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
     * 战甲模组槽位类
     *
     * 特殊功能：
     * 1. 只接受非随机的战甲模组
     * 2. 同一战甲不能装备相同类型的模组
     * 3. 同一战甲只能装备一个裂罅模组
     * 4. 模组变动时自动同步到玩家Capability
     */
    public class ModuleSlot extends SlotItemHandler {
        public int index;
        public EntityPlayer entityPlayer;

        public ModuleSlot(EntityPlayer entityPlayer, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.index = index;
            this.entityPlayer = entityPlayer;
        }

        /**
         * 玩家取出模组时的处理
         *
         * 取出后需要同步到玩家Capability
         */
        @Override
        public ItemStack onTake(EntityPlayer thePlayer, ItemStack stack) {
            if (!world.isRemote) {
                new SynchronizationTask() {
                    @Override
                    public void run() {
                        try {
                            LogUtil.debugEvent("战甲模组取出", stack.getDisplayName(), "槽位: " + index);

                            // 同步到玩家Capability
                            syncToCapability();
                        } catch (Exception e) {
                            LogUtil.error("战甲模组取出时同步失败", e);
                        }
                    }
                }.start();
            }
            return super.onTake(thePlayer, stack);
        }

        /**
         * 检查模组是否可以放入此槽位
         *
         * 检查项：
         * 1. 必须是WarframeModuleBase类型（防止武器等其他物品）
         * 2. 不能是随机模组
         * 3. 当前槽位必须为空
         * 4. 不能与其他槽位的模组重复
         * 5. 裂罅模组只能装备一个
         */
        @Override
        public boolean isItemValid(ItemStack itemStack) {
            // 第一道防线：必须是战甲模组
            if (!(itemStack.getItem() instanceof WarframeModuleBase)) {
                LogUtil.debug("拒绝放入：不是战甲模组 - " + itemStack.getDisplayName());
                return false;
            }

            // 第二道防线：不能是随机模组
            if (WarframeModuleBase.isRandom(itemStack)) {
                LogUtil.debug("拒绝放入：随机模组必须先揭示 - " + itemStack.getDisplayName());
                return false;
            }

            // 检查当前槽位是否为空
            if (!this.getItemHandler().getStackInSlot(index).isEmpty()) {
                LogUtil.debug("拒绝放入：槽位" + index + "已有模组");
                return false;
            }

            // 检查是否与已装备的模组冲突
            for (int i = 0; i < 8; i++) {
                if (i != index) {
                    ItemStack existingStack = module.getStackInSlot(i);
                    if (!existingStack.isEmpty()) {
                        // 检查裂罅模组冲突
                        if (itemStack.getItem() instanceof WarframeRivenModule &&
                                existingStack.getItem() instanceof WarframeRivenModule) {
                            LogUtil.debug("拒绝装备: 已有战甲裂罅模组在槽位" + i);
                            return false;
                        }
                        // 检查模组类型冲突
                        else if (WarframeModuleBase.getType(existingStack).equals(WarframeModuleBase.getType(itemStack))) {
                            LogUtil.debug("拒绝装备: 战甲模组类型重复 - " + WarframeModuleBase.getType(itemStack) + " (槽位" + i + "已有)");
                            return false;
                        }
                    }
                }
            }

            return super.isItemValid(itemStack);
        }

        /**
         * 放入模组时的处理
         *
         * 放入后需要同步到玩家Capability
         */
        @Override
        public void putStack(ItemStack itemStack) {
            if (!world.isRemote) {
                new SynchronizationTask() {
                    @Override
                    public void run() {
                        try {
                            if (!itemStack.isEmpty()) {
                                // 再次验证是否是战甲模组（双重保险）
                                if (!(itemStack.getItem() instanceof WarframeModuleBase)) {
                                    LogUtil.error("警告：非战甲模组被放入槽位" + index + " - " + itemStack.getDisplayName());
                                    return;
                                }
                                LogUtil.debugEvent("战甲模组装备", itemStack.getDisplayName(), "槽位: " + index);
                            }

                            // 同步到玩家Capability
                            syncToCapability();
                        } catch (Exception e) {
                            LogUtil.error("战甲模组装备时同步失败", e);
                        }
                    }
                }.start();
            }
            super.putStack(itemStack);
        }

        /**
         * 同步所有模组到玩家的WarframeModules Capability
         *
         * 确保玩家的战甲模组配置始终是最新的
         */
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