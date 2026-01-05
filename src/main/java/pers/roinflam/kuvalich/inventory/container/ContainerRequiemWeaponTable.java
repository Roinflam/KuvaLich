package pers.roinflam.kuvalich.inventory.container;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 安魂之武器军械库容器类
 *
 * 用于管理武器模组的装备界面
 * 支持最多8个模组槽位和1个武器槽位
 *
 * 核心功能：
 * 1. 武器放入时自动加载已装备的模组
 * 2. 模组放入/取出时自动同步到武器NBT
 * 3. 防止装备重复模组
 * 4. 防止装备多个裂罅模组
 * 5. 防止在没有武器时装备模组
 *
 * @author RoinFlam
 */
public class ContainerRequiemWeaponTable extends Container {
    private final World world;
    private final BlockPos pos;

    /** 模组物品处理器 - 8个槽位 */
    public ItemStackHandler module = null;

    /** 武器物品处理器 - 1个槽位 */
    public ItemStackHandler weapon = null;

    /** 同步标志 - 防止递归同步 */
    public boolean synchronize = false;

    /** 武器同步标志 - 防止武器槽递归同步（已废弃但保留兼容性） */
    public boolean synchronizeWeapon = false;

    /**
     * 构造容器
     *
     * @param entityPlayer 玩家实体
     * @param world 世界对象
     * @param pos 方块位置
     */
    public ContainerRequiemWeaponTable(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;

        // 初始化物品处理器
        module = new ItemStackHandler(8);
        weapon = new ItemStackHandler(1);

        // 添加模组槽位 - 上排4个
        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i, 18 + 41 * i, 12));
        }
        // 添加模组槽位 - 下排4个
        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i + 4, 18 + 41 * i, 39));
        }
        // 添加武器槽位 - 居中位置
        this.addSlotToContainer(new WeaponSlot(weapon, 0, 80, 67));

        // 添加玩家背包槽位
        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 151));        // 快捷栏
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 93));     // 背包第一排
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 111));   // 背包第二排
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 129));   // 背包第三排
        }

        LogUtil.debugEvent("武器军械库容器创建", entityPlayer.getName(), "位置: " + pos.toString());
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
            if (index < 9) {
                LogUtil.debug("尝试将物品从武器军械库转移到背包: " + slotStack.getDisplayName());
                return ItemStack.EMPTY; // 不允许shift+点击取出
            }
            // 从背包转移到容器
            else {
                if (itemstack.getItem() instanceof ItemModuleBase) {
                    // 尝试放入模组槽
                    boolean success = false;
                    for (int i = 0; i < 8; i++) {
                        if (this.mergeItemStack(slotStack, i, i + 1, false)) {
                            success = true;
                            LogUtil.debug("物品成功转移到模组槽位" + i + ": " + slotStack.getDisplayName());
                            break;
                        }
                    }
                    if (!success) {
                        LogUtil.debug("所有模组槽位已满或不兼容");
                        return ItemStack.EMPTY;
                    }
                } else if (ItemModule.hasBase(itemstack)) {
                    // 尝试放入武器槽
                    if (!this.mergeItemStack(slotStack, 8, 9, false)) {
                        LogUtil.debug("武器槽位已有物品或不兼容");
                        return ItemStack.EMPTY;
                    }
                    LogUtil.debug("物品成功转移到武器槽位: " + slotStack.getDisplayName());
                } else {
                    // 既不是模组也不是武器，禁止转移
                    LogUtil.debug("拒绝转移：既不是模组也不是武器 - " + slotStack.getDisplayName());
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
     * 主要处理武器取出时清空所有模组槽位的逻辑
     */
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (!player.world.isRemote) {
            new SynchronizationTask() {
                @Override
                public void run() {
                    // 如果是武器槽位被清空，清除所有模组
                    if (slotId == 8) {
                        ItemStack weaponItemStack = ContainerRequiemWeaponTable.this.weapon.getStackInSlot(0);
                        if (weaponItemStack.isEmpty()) {
                            LogUtil.debugEvent("武器槽清空", player.getName(), "清除所有已装备模组");
                            for (int i = 0; i < 8; i++) {
                                ContainerRequiemWeaponTable.this.getSlot(i).putStack(ItemStack.EMPTY);
                                ContainerRequiemWeaponTable.this.module.setStackInSlot(i, ItemStack.EMPTY);
                            }
                        }
                    }
                }
            }.start();
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    /**
     * 容器关闭时的处理
     *
     * 将武器槽中的武器返还给玩家
     */
    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            if (!weapon.getStackInSlot(0).isEmpty()) {
                LogUtil.debug("容器关闭 - 返还武器: " + weapon.getStackInSlot(0).getDisplayName());
                if (!this.mergeItemStack(weapon.getStackInSlot(0), 9, 9 + 36, true)) {
                    // 背包满了，掉落到地上
                    LogUtil.warn("玩家背包已满，武器掉落到地上");
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), weapon.getStackInSlot(0)));
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
     * 武器槽位类
     *
     * 特殊功能：
     * 1. 只接受有基础属性的武器
     * 2. 放入武器时自动加载已装备的模组
     */
    public class WeaponSlot extends SlotItemHandler {
        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        /**
         * 检查物品是否可以放入此槽位
         *
         * 条件：
         * 1. 槽位必须为空
         * 2. 物品必须有基础属性
         */
        @Override
        public boolean isItemValid(ItemStack itemStack) {
            if (!weapon.getStackInSlot(0).isEmpty()) {
                LogUtil.debug("拒绝放入：武器槽位已有武器");
                return false;
            }
            if (!ItemModule.hasBase(itemStack)) {
                LogUtil.debug("拒绝放入：物品没有基础属性 - " + itemStack.getDisplayName());
                return false;
            }
            return super.isItemValid(itemStack);
        }

        /**
         * 放入武器时的处理
         *
         * 自动从武器NBT中读取已装备的模组并显示在界面上
         */
        @Override
        public void putStack(ItemStack weaponItemStack) {
            try {
                // 检查武器是否为空
                if (weaponItemStack.isEmpty()) {
                    LogUtil.debug("武器槽位被清空");
                    super.putStack(weaponItemStack);
                    return;
                }

                LogUtil.debugEvent("武器放入军械库", "武器", weaponItemStack.getDisplayName());

                // 读取武器的NBT数据
                NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();

                // 检查是否有tag标签
                if (!nbtTagCompound.hasKey("tag")) {
                    LogUtil.debug("武器没有tag数据，跳过模组加载");
                    super.putStack(weaponItemStack);
                    return;
                }

                NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");

                // 检查是否有武器模组数据
                String weaponModuleKey = Reference.MOD_ID + "_weaponModules";
                if (!tag.hasKey(weaponModuleKey)) {
                    LogUtil.debug("武器没有模组数据，跳过模组加载");
                    super.putStack(weaponItemStack);
                    return;
                }

                NBTTagCompound weaponModule = tag.getCompoundTag(weaponModuleKey);

                // 检查是否有模组列表
                if (!weaponModule.hasKey("modules")) {
                    LogUtil.debug("武器模组数据中没有modules列表");
                    super.putStack(weaponItemStack);
                    return;
                }

                NBTTagList itemList = weaponModule.getTagList("modules", 10);

                // 加载模组到槽位
                int loadedCount = 0;
                for (int i = 0; i < Math.min(8, itemList.tagCount()); i++) {
                    NBTTagCompound itemTag = itemList.getCompoundTagAt(i);
                    ItemStack stack = new ItemStack(itemTag);
                    if (!stack.isEmpty()) {
                        ContainerRequiemWeaponTable.this.synchronize = true;
                        ContainerRequiemWeaponTable.this.getSlot(i).putStack(stack);
                        module.setStackInSlot(i, stack);
                        loadedCount++;
                        LogUtil.debug("加载模组到槽位" + i + ": " + stack.getDisplayName());
                    }
                }

                LogUtil.debugEvent("模组加载完成", weaponItemStack.getDisplayName(),
                        "成功加载 " + loadedCount + " 个模组");

            } catch (Exception e) {
                LogUtil.error("武器放入军械库时发生错误: " + weaponItemStack.getDisplayName(), e);
                // 发生错误时清空所有模组槽，防止数据不一致
                for (int i = 0; i < 8; i++) {
                    ContainerRequiemWeaponTable.this.getSlot(i).putStack(ItemStack.EMPTY);
                    module.setStackInSlot(i, ItemStack.EMPTY);
                }
            }

            super.putStack(weaponItemStack);
        }
    }

    /**
     * 模组槽位类
     *
     * 特殊功能：
     * 1. 只接受非随机的模组
     * 2. 同一武器不能装备相同类型的模组
     * 3. 同一武器只能装备一个裂罅模组
     * 4. 模组变动时自动同步到武器NBT
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
         * 取出后需要同步武器NBT，清除该模组的数据
         */
        @Override
        public ItemStack onTake(EntityPlayer thePlayer, ItemStack stack) {
            if (!world.isRemote) {
                new SynchronizationTask() {
                    @Override
                    public void run() {
                        try {
                            LogUtil.debugEvent("模组从军械库取出", stack.getDisplayName(), "槽位: " + index);

                            // 同步武器NBT
                            syncWeaponNBT();

                            // 清空当前槽位
                            ModuleSlot.this.putStack(ItemStack.EMPTY);
                        } catch (Exception e) {
                            LogUtil.error("模组取出时同步失败", e);
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
         * 1. 武器槽必须有武器
         * 2. 必须是ItemModuleBase类型
         * 3. 不能是随机模组
         * 4. 当前槽位必须为空
         * 5. 不能与其他槽位的模组重复（除了索引不同）
         * 6. 裂罅模组只能装备一个
         */
        @Override
        public boolean isItemValid(ItemStack itemStack) {
            // 检查是否有武器
            if (weapon.getStackInSlot(0).isEmpty()) {
                LogUtil.debug("拒绝装备：武器槽位为空");
                return false;
            }

            // 检查是否是有效的模组
            if (!(itemStack.getItem() instanceof ItemModuleBase)) {
                LogUtil.debug("拒绝装备：不是武器模组 - " + itemStack.getDisplayName());
                return false;
            }

            if (ItemModuleBase.isRandom(itemStack)) {
                LogUtil.debug("拒绝装备：随机模组必须先揭示 - " + itemStack.getDisplayName());
                return false;
            }

            // 检查当前槽位是否为空
            if (!this.getItemHandler().getStackInSlot(index).isEmpty()) {
                LogUtil.debug("拒绝装备：槽位" + index + "已有模组");
                return false;
            }

            // 检查是否与已装备的模组冲突
            for (int i = 0; i < 8; i++) {
                if (i != index) {
                    ItemStack existingStack = module.getStackInSlot(i);
                    if (!existingStack.isEmpty()) {
                        // 检查裂罅模组冲突
                        if (itemStack.getItem() instanceof ItemRivenModule &&
                                existingStack.getItem() instanceof ItemRivenModule) {
                            LogUtil.debug("拒绝装备: 已有裂罅模组在槽位" + i);
                            return false;
                        }
                        // 检查模组类型冲突
                        else if (ItemModuleBase.getType(existingStack).equals(ItemModuleBase.getType(itemStack))) {
                            LogUtil.debug("拒绝装备: 模组类型重复 - " + ItemModuleBase.getType(itemStack) + " (槽位" + i + "已有)");
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
         * 放入后需要同步武器NBT，保存模组数据
         */
        @Override
        public void putStack(ItemStack itemStack) {
            // 如果是同步操作，不需要再次同步
            if (!ContainerRequiemWeaponTable.this.synchronize) {
                new SynchronizationTask() {
                    @Override
                    public void run() {
                        if (!world.isRemote) {
                            try {
                                if (!itemStack.isEmpty()) {
                                    LogUtil.debugEvent("模组装备到军械库", itemStack.getDisplayName(), "槽位: " + index);
                                }

                                // 同步武器NBT
                                syncWeaponNBT();
                            } catch (Exception e) {
                                LogUtil.error("模组装备时同步失败", e);
                            }
                        }
                    }
                }.start();
            }

            ContainerRequiemWeaponTable.this.synchronize = false;
            super.putStack(itemStack);
        }

        /**
         * 同步武器NBT数据
         *
         * 将当前所有模组槽位的数据写入武器的NBT
         * 这样武器取出后再放入时可以恢复模组配置
         */
        private void syncWeaponNBT() {
            ItemStack weaponItemStack = ContainerRequiemWeaponTable.this.weapon.getStackInSlot(0).copy();

            // 检查武器是否有效
            if (weaponItemStack.isEmpty() || !ItemModule.hasBase(weaponItemStack)) {
                return;
            }

            try {
                // 获取或创建NBT结构
                NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();
                NBTTagCompound tag = nbtTagCompound.hasKey("tag") ?
                        nbtTagCompound.getCompoundTag("tag") : new NBTTagCompound();

                String weaponModuleKey = Reference.MOD_ID + "_weaponModules";
                NBTTagCompound weaponModule = tag.hasKey(weaponModuleKey) ?
                        tag.getCompoundTag(weaponModuleKey) : new NBTTagCompound();

                // 写入模组列表
                NBTTagList itemList = new NBTTagList();
                for (int i = 0; i < 8; i++) {
                    NBTTagCompound itemTag = new NBTTagCompound();
                    if (!module.getStackInSlot(i).isEmpty()) {
                        ItemStack moduleItemStack = module.getStackInSlot(i);
                        if (moduleItemStack.getItem() instanceof ItemModuleBase) {
                            moduleItemStack.writeToNBT(itemTag);
                        } else {
                            ItemStack.EMPTY.writeToNBT(itemTag);
                        }
                    } else {
                        ItemStack.EMPTY.writeToNBT(itemTag);
                    }
                    itemList.appendTag(itemTag);
                }

                // 保存NBT数据
                weaponModule.setTag("modules", itemList);
                tag.setTag(weaponModuleKey, weaponModule);
                nbtTagCompound.setTag("tag", tag);
                weaponItemStack.setTagCompound(tag);

                // 更新武器槽位
                ContainerRequiemWeaponTable.this.inventorySlots.get(8).putStack(weaponItemStack);
                ((EntityPlayerMP) entityPlayer).sendContainerToPlayer(entityPlayer.inventoryContainer);

                LogUtil.debug("武器NBT同步成功");
            } catch (Exception e) {
                LogUtil.error("同步武器NBT时发生错误", e);
            }
        }
    }
}