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

import javax.annotation.Nonnull;

/**
 * 安魂之武器军械库容器类（完全修复版）
 *
 * 修复内容：
 * - ✅ 修复Shift取出武器后残影BUG（强制同步客户端）
 * - ✅ 武器取出时正确清空所有模组槽
 * - ✅ 模组变动立刻同步武器NBT

 */
public class ContainerRequiemWeaponTable extends Container {
    private final World world;
    private final BlockPos pos;
    private final EntityPlayer player;

    public ItemStackHandler module = null;
    public ItemStackHandler weapon = null;
    private boolean synchronize = false;

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
     * ✅ 修复：正确处理武器取出，清空模组，同步客户端
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
        if (index < 9) {
            // ✅ 武器槽（索引8）被Shift取出
            if (index == 8) {
                LogUtil.debug("Shift取出武器: " + slotStack.getDisplayName());

                // 转移武器到背包
                if (!this.mergeItemStack(slotStack, 9, 9 + 36, true)) {
                    LogUtil.debug("背包已满，无法转移武器");
                    return ItemStack.EMPTY;
                }

                // ✅ 关键：清空所有模组槽
                if (!world.isRemote) {
                    LogUtil.debug("武器被取出，清空所有模组槽");
                    for (int i = 0; i < 8; i++) {
                        this.getSlot(i).putStack(ItemStack.EMPTY);
                        this.module.setStackInSlot(i, ItemStack.EMPTY);
                    }

                    // ✅ 强制同步所有槽位到客户端
                    this.detectAndSendChanges();
                }
            }
            // 模组槽（索引0-7）
            else {
                LogUtil.debug("从模组槽位" + index + "转移到背包: " + slotStack.getDisplayName());

                if (!this.mergeItemStack(slotStack, 9, 9 + 36, true)) {
                    LogUtil.debug("背包已满，无法转移");
                    return ItemStack.EMPTY;
                }

                slot.onSlotChange(slotStack, itemstack);
            }
        }
        // ========== 从背包转移到容器 ==========
        else {
            boolean transferred = false;

            if (itemstack.getItem() instanceof ItemModuleBase) {
                for (int i = 0; i < 8; i++) {
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

    /**
     * ✅ 保留原有的点击逻辑
     */
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

                LogUtil.debugEvent("武器放入军械库", "武器", weaponItemStack.getDisplayName());

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

                for (int i = 0; i < Math.min(8, itemList.tagCount()); i++) {
                    NBTTagCompound itemTag = itemList.getCompoundTagAt(i);
                    ItemStack stack = new ItemStack(itemTag);
                    if (!stack.isEmpty()) {
                        ContainerRequiemWeaponTable.this.getSlot(i).putStack(stack);
                        module.setStackInSlot(i, stack);
                        loadedCount++;
                    }
                }

                ContainerRequiemWeaponTable.this.synchronize = false;
                LogUtil.debugEvent("模组加载完成", weaponItemStack.getDisplayName(), "成功加载 " + loadedCount + " 个模组");

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
            if (!(itemStack.getItem() instanceof ItemModuleBase)) {
                return false;
            }
            if (ItemModuleBase.isRandom(itemStack)) {
                return false;
            }
            if (!this.getItemHandler().getStackInSlot(index).isEmpty()) {
                return false;
            }

            for (int i = 0; i < 8; i++) {
                if (i != index) {
                    ItemStack existingStack = module.getStackInSlot(i);
                    if (!existingStack.isEmpty()) {
                        if (itemStack.getItem() instanceof ItemRivenModule &&
                                existingStack.getItem() instanceof ItemRivenModule) {
                            return false;
                        } else if (ItemModuleBase.getType(existingStack).equals(ItemModuleBase.getType(itemStack))) {
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