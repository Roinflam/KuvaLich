// 文件：ContainerRequiemWeaponTable.java
// 路径：src/main/java/pers/roinflam/kuvalich/inventory/container/ContainerRequiemWeaponTable.java
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
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nonnull;

public class ContainerRequiemWeaponTable extends Container {
    private final World world;
    private final BlockPos pos;
    private final EntityPlayer player;

    public ItemStackHandler module = null;
    public ItemStackHandler weapon = null;
    private boolean synchronize = false;

    /** 模组槽位限制NBT键名 */
    private static final String MODULE_LIMIT_KEY = "moduleLimit";

    public ContainerRequiemWeaponTable(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
        this.player = entityPlayer;

        module = new ItemStackHandler(8);
        weapon = new ItemStackHandler(1);

        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i, 18 + 41 * i, 12));
        }
        for (int i = 0; i < 4; i++) {
            this.addSlotToContainer(new ModuleSlot(entityPlayer, module, i + 4, 18 + 41 * i, 39));
        }
        this.addSlotToContainer(new WeaponSlot(weapon, 0, 80, 67));

        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 151));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 93));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 111));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 129));
        }

        LogUtil.debugEvent("武器军械库容器创建", entityPlayer.getName(), "位置: " + pos.toString());
    }

    /**
     * 获取当前武器的模组槽位限制
     * 读取武器NBT中的 moduleLimit 值，范围0-8，默认8（无限制）
     *
     * @return 允许使用的槽位数量（0-8）
     */
    public int getModuleLimit() {
        ItemStack weaponStack = weapon.getStackInSlot(0);

        // 没有武器时，所有槽位都不可用
        if (weaponStack == null || weaponStack.isEmpty()) {
            return 0;
        }

        // 检查武器是否有NBT
        NBTTagCompound tag = weaponStack.getTagCompound();
        if (tag == null) {
            // 没有NBT，默认全部解锁
            return 8;
        }

        // 检查是否有 moduleLimit 键
        if (!tag.hasKey(MODULE_LIMIT_KEY)) {
            // 没有该键，默认全部解锁
            return 8;
        }

        // 读取值并限制在0-8范围内
        int limit = tag.getInteger(MODULE_LIMIT_KEY);
        return Math.max(0, Math.min(8, limit));
    }

    /**
     * 检查指定槽位是否已解锁
     *
     * @param slotIndex 槽位索引（0-7）
     * @return 是否已解锁
     */
    public boolean isSlotUnlocked(int slotIndex) {
        return slotIndex < getModuleLimit();
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

        if (index < 9) {
            if (index == 8) {
                LogUtil.debug("Shift取出武器: " + slotStack.getDisplayName());

                if (!this.mergeItemStack(slotStack, 9, 9 + 36, true)) {
                    LogUtil.debug("背包已满，无法转移武器");
                    return ItemStack.EMPTY;
                }

                if (!world.isRemote) {
                    LogUtil.debug("武器被取出，清空所有模组槽");
                    for (int i = 0; i < 8; i++) {
                        this.getSlot(i).putStack(ItemStack.EMPTY);
                        this.module.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    this.detectAndSendChanges();
                }
            } else {
                LogUtil.debug("从模组槽位" + index + "转移到背包: " + slotStack.getDisplayName());

                if (!this.mergeItemStack(slotStack, 9, 9 + 36, true)) {
                    LogUtil.debug("背包已满，无法转移");
                    return ItemStack.EMPTY;
                }

                slot.onSlotChange(slotStack, itemstack);
            }
        } else {
            boolean transferred = false;

            if (itemstack.getItem() instanceof ItemModuleBase) {
                // 只尝试放入已解锁的槽位
                int limit = getModuleLimit();
                for (int i = 0; i < limit; i++) {
                    if (this.mergeItemStack(slotStack, i, i + 1, false)) {
                        transferred = true;
                        break;
                    }
                }
                if (!transferred) {
                    return ItemStack.EMPTY;
                }
            } else if (ItemModule.hasBase(itemstack)) {
                if (!this.mergeItemStack(slotStack, 8, 9, false)) {
                    return ItemStack.EMPTY;
                }
                transferred = true;
            } else {
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
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (!player.world.isRemote && slotId == 8) {
            ItemStack weaponStack = this.weapon.getStackInSlot(0);
            ItemStack result = super.slotClick(slotId, dragType, clickTypeIn, player);
            ItemStack newWeaponStack = this.weapon.getStackInSlot(0);

            if (!weaponStack.isEmpty() && newWeaponStack.isEmpty()) {
                LogUtil.debugEvent("武器槽清空（点击）", player.getName(), "清除所有已装备模组");
                for (int i = 0; i < 8; i++) {
                    this.getSlot(i).putStack(ItemStack.EMPTY);
                    this.module.setStackInSlot(i, ItemStack.EMPTY);
                }
                this.detectAndSendChanges();
            }
            return result;
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        if (!playerIn.world.isRemote) {
            ItemStack weaponStack = weapon.getStackInSlot(0);
            if (!weaponStack.isEmpty()) {
                if (!this.mergeItemStack(weaponStack, 9, 9 + 36, true)) {
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), weaponStack));
                }
            }
        }
        super.onContainerClosed(playerIn);
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer playerIn) {
        return playerIn.world.equals(this.world) && playerIn.getDistanceSq(this.pos) <= 64;
    }

    public class WeaponSlot extends SlotItemHandler {
        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            if (itemStack == null || itemStack.isEmpty()) {
                return false;
            }
            if (!weapon.getStackInSlot(0).isEmpty()) {
                return false;
            }
            if (!ItemModule.hasBase(itemStack)) {
                return false;
            }
            return super.isItemValid(itemStack);
        }

        @Override
        public void putStack(ItemStack weaponItemStack) {
            try {
                if (weaponItemStack == null || weaponItemStack.isEmpty()) {
                    super.putStack(weaponItemStack);
                    return;
                }

                // 读取武器的槽位限制
                int limit = 8;
                NBTTagCompound weaponTag = weaponItemStack.getTagCompound();
                if (weaponTag != null && weaponTag.hasKey(MODULE_LIMIT_KEY)) {
                    limit = Math.max(0, Math.min(8, weaponTag.getInteger(MODULE_LIMIT_KEY)));
                }

                LogUtil.debugEvent("武器放入军械库", "武器", weaponItemStack.getDisplayName() + " (槽位限制: " + limit + ")");

                NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();
                if (!nbtTagCompound.hasKey("tag")) {
                    super.putStack(weaponItemStack);
                    return;
                }

                NBTTagCompound tag = nbtTagCompound.getCompoundTag("tag");
                String weaponModuleKey = Reference.MOD_ID + "_weaponModules";
                if (!tag.hasKey(weaponModuleKey)) {
                    super.putStack(weaponItemStack);
                    return;
                }

                NBTTagCompound weaponModule = tag.getCompoundTag(weaponModuleKey);
                if (!weaponModule.hasKey("modules")) {
                    super.putStack(weaponItemStack);
                    return;
                }

                NBTTagList itemList = weaponModule.getTagList("modules", 10);
                int loadedCount = 0;
                ContainerRequiemWeaponTable.this.synchronize = true;

                // 只加载在槽位限制范围内的模组
                for (int i = 0; i < Math.min(limit, Math.min(8, itemList.tagCount())); i++) {
                    NBTTagCompound itemTag = itemList.getCompoundTagAt(i);
                    ItemStack stack = new ItemStack(itemTag);
                    if (!stack.isEmpty()) {
                        ContainerRequiemWeaponTable.this.getSlot(i).putStack(stack);
                        module.setStackInSlot(i, stack);
                        loadedCount++;
                    }
                }

                ContainerRequiemWeaponTable.this.synchronize = false;
                LogUtil.debugEvent("模组加载完成", weaponItemStack.getDisplayName(), "成功加载 " + loadedCount + " 个模组（限制: " + limit + "）");

            } catch (Exception e) {
                LogUtil.error("武器放入军械库时发生错误", e);
                for (int i = 0; i < 8; i++) {
                    ContainerRequiemWeaponTable.this.getSlot(i).putStack(ItemStack.EMPTY);
                    module.setStackInSlot(i, ItemStack.EMPTY);
                }
            }

            super.putStack(weaponItemStack);
        }
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
                    LogUtil.debugEvent("模组从军械库取出", stack.getDisplayName(), "槽位: " + index);
                    syncWeaponNBT();
                } catch (Exception e) {
                    LogUtil.error("模组取出时同步失败", e);
                }
            }

            return result;
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            if (itemStack == null || itemStack.isEmpty()) {
                return false;
            }
            if (weapon.getStackInSlot(0).isEmpty()) {
                return false;
            }

            // ✅ 检查槽位是否已解锁
            if (!isSlotUnlocked(index)) {
                LogUtil.debug("槽位 " + index + " 未解锁，当前限制: " + getModuleLimit());
                return false;
            }

            if (!(itemStack.getItem() instanceof ItemModuleBase)) {
                return false;
            }
            if (ItemModuleBase.isRandom(itemStack)) {
                return false;
            }
            if (!this.getItemHandler().getStackInSlot(index).isEmpty()) {
                return false;
            }

            // 使用冲突检测系统
            for (int i = 0; i < 8; i++) {
                if (i != index) {
                    ItemStack existingStack = module.getStackInSlot(i);
                    if (!existingStack.isEmpty()) {
                        // 紫卡特殊处理（只能装一个）
                        if (itemStack.getItem() instanceof ItemRivenModule &&
                                existingStack.getItem() instanceof ItemRivenModule) {
                            return false;
                        }

                        // 双向冲突检测（type + 冲突标签）
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

            if (!ContainerRequiemWeaponTable.this.synchronize && !world.isRemote) {
                try {
                    if (itemStack != null && !itemStack.isEmpty()) {
                        LogUtil.debugEvent("模组装备到军械库", itemStack.getDisplayName(), "槽位: " + index);
                    }
                    syncWeaponNBT();
                } catch (Exception e) {
                    LogUtil.error("模组装备时同步失败", e);
                }
            }
        }

        @Override
        public void onSlotChanged() {
            super.onSlotChanged();

            if (!world.isRemote && !ContainerRequiemWeaponTable.this.synchronize) {
                try {
                    LogUtil.debug("模组槽位" + index + "内容变化，触发同步");
                    syncWeaponNBT();
                } catch (Exception e) {
                    LogUtil.error("槽位变化同步失败", e);
                }
            }
        }

        private void syncWeaponNBT() {
            ItemStack weaponItemStack = ContainerRequiemWeaponTable.this.weapon.getStackInSlot(0);

            if (weaponItemStack == null || weaponItemStack.isEmpty() || !ItemModule.hasBase(weaponItemStack)) {
                return;
            }

            try {
                weaponItemStack = weaponItemStack.copy();

                NBTTagCompound nbtTagCompound = weaponItemStack.serializeNBT();
                NBTTagCompound tag = nbtTagCompound.hasKey("tag") ?
                        nbtTagCompound.getCompoundTag("tag") : new NBTTagCompound();

                String weaponModuleKey = Reference.MOD_ID + "_weaponModules";
                NBTTagCompound weaponModule = tag.hasKey(weaponModuleKey) ?
                        tag.getCompoundTag(weaponModuleKey) : new NBTTagCompound();

                NBTTagList itemList = new NBTTagList();
                for (int i = 0; i < 8; i++) {
                    NBTTagCompound itemTag = new NBTTagCompound();
                    ItemStack moduleStack = module.getStackInSlot(i);

                    if (moduleStack != null && !moduleStack.isEmpty()) {
                        if (moduleStack.getItem() instanceof ItemModuleBase) {
                            moduleStack.writeToNBT(itemTag);
                        } else {
                            ItemStack.EMPTY.writeToNBT(itemTag);
                        }
                    } else {
                        ItemStack.EMPTY.writeToNBT(itemTag);
                    }
                    itemList.appendTag(itemTag);
                }

                weaponModule.setTag("modules", itemList);
                tag.setTag(weaponModuleKey, weaponModule);
                nbtTagCompound.setTag("tag", tag);
                weaponItemStack.setTagCompound(tag);

                ContainerRequiemWeaponTable.this.weapon.setStackInSlot(0, weaponItemStack);
                ContainerRequiemWeaponTable.this.inventorySlots.get(8).putStack(weaponItemStack);

                if (entityPlayer instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) entityPlayer).connection.sendPacket(
                            new net.minecraft.network.play.server.SPacketSetSlot(
                                    ContainerRequiemWeaponTable.this.windowId,
                                    8,
                                    weaponItemStack
                            )
                    );
                    ContainerRequiemWeaponTable.this.detectAndSendChanges();
                }

                LogUtil.debug("武器NBT同步成功");
            } catch (Exception e) {
                LogUtil.error("同步武器NBT时发生错误", e);
            }
        }
    }
}