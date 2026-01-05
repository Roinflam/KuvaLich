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
import pers.roinflam.kuvalich.config.ConfigKuvaLich;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 安魂之融容器类
 *
 * 用于武器升级和裂罅模组循环
 *
 * 功能：
 * 1. 武器融合升级（两个同类型武器合并，等级提升）
 * 2. 裂罅模组循环（消耗赤毒重置属性）
 * 3. 武器添加基础属性（消耗裂罅碎块）
 *
 * @author RoinFlam
 */
public class ContainerRequiemEvolve extends Container {
    private final World world;
    private final BlockPos pos;

    /** 武器/模组槽位 */
    private final ItemStackHandler weapon;

    /** 材料槽位（武器/赤毒/裂罅碎块） */
    private final ItemStackHandler material;

    /** 结果槽位（只读） */
    private final ItemStackHandler result;

    /** 是否正在进行升级操作 */
    private boolean evolve = false;

    /** 是否正在进行循环操作 */
    private boolean cycle = false;

    /**
     * 构造容器
     *
     * @param entityPlayer 玩家实体
     * @param world 世界对象
     * @param pos 方块位置
     */
    public ContainerRequiemEvolve(EntityPlayer entityPlayer, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;

        // 初始化槽位
        this.weapon = new ItemStackHandler(1);
        this.material = new ItemStackHandler(1);
        this.result = new ItemStackHandler(1);

        // 添加槽位到容器
        this.addSlotToContainer(new WeaponSlot(this.weapon, 0, 31, 32));
        this.addSlotToContainer(new MaterialSlot(this.material, 0, 81, 32));
        this.addSlotToContainer(new ResultSlot(this.result, 0, 131, 32));

        // 添加玩家背包槽位
        InventoryPlayer inventoryPlayer = entityPlayer.inventory;
        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(inventoryPlayer, i, 8 + 18 * i, 171 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 9, 8 + 18 * i, 113 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 18, 8 + 18 * i, 131 - 10));
            this.addSlotToContainer(new Slot(inventoryPlayer, i + 27, 8 + 18 * i, 149 - 10));
        }

        LogUtil.debugEvent("安魂之融容器创建", entityPlayer.getName(), "位置: " + pos.toString());
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

            // 从容器转移到背包（结果槽不允许）
            if (index < 3) {
                LogUtil.debug("禁止从安魂之融容器Shift转移物品");
                return ItemStack.EMPTY;
            }
            // 从背包转移到容器
            else {
                // 尝试放入武器槽
                if (!this.mergeItemStack(slotStack, 0, 1, false)) {
                    // 尝试放入材料槽
                    if (!this.mergeItemStack(slotStack, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
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
     * 处理各种合成逻辑：
     * 1. 武器+武器 = 升级武器
     * 2. 武器裂罅模组+赤毒 = 重置裂罅
     * 3. 战甲裂罅模组+赤毒 = 重置裂罅
     * 4. 武器+裂罅碎块 = 添加基础属性
     */
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        if (!player.world.isRemote) {
            new SynchronizationTask() {
                @Override
                public void run() {
                    try {
                        // 如果不是材料槽的shift点击，执行合成检测
                        if (slotId != 1 || !clickTypeIn.equals(ClickType.QUICK_MOVE)) {
                            handleCrafting(player);
                        }
                    } catch (Exception e) {
                        LogUtil.error("安魂之融合成逻辑发生错误", e);
                        // 清空结果槽，防止物品丢失
                        result.setStackInSlot(0, ItemStack.EMPTY);
                        evolve = false;
                        cycle = false;
                    }
                }
            }.start();
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    /**
     * 处理合成逻辑
     *
     * @param player 玩家
     */
    private void handleCrafting(EntityPlayer player) {
        ItemStack weaponStack = weapon.getStackInSlot(0);
        ItemStack materialStack = material.getStackInSlot(0);
        ItemStack resultStack = result.getStackInSlot(0);

        // 1. 处理武器融合升级
        if (KuvaWeapon.hasType(weaponStack) && KuvaWeapon.hasType(materialStack)) {
            handleWeaponEvolve(weaponStack, materialStack, resultStack);
            return;
        }

        // 2. 处理武器裂罅模组循环
        if (weaponStack.getItem() instanceof ItemRivenModule && materialStack.getItem() instanceof Kuva) {
            handleItemRivenCycle(weaponStack, materialStack, resultStack);
            return;
        }

        // 3. 处理战甲裂罅模组循环
        if (weaponStack.getItem() instanceof WarframeRivenModule && materialStack.getItem() instanceof Kuva) {
            handleWarframeRivenCycle(weaponStack, materialStack, resultStack);
            return;
        }

        // 4. 处理添加基础属性
        if (!weaponStack.isEmpty() && materialStack.getItem() instanceof RivenSliver) {
            handleAddBaseAttribute(weaponStack, materialStack, resultStack);
            return;
        }

        // 如果已有结果但不满足任何条件，清空结果
        if (!resultStack.isEmpty() && !evolve && !cycle) {
            result.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    /**
     * 处理武器融合升级
     *
     * 两个相同类型的武器融合，等级提升
     *
     * @param weaponStack 武器槽物品
     * @param materialStack 材料槽物品
     * @param resultStack 结果槽物品
     */
    private void handleWeaponEvolve(ItemStack weaponStack, ItemStack materialStack, ItemStack resultStack) {
        if (resultStack.isEmpty() && !evolve) {
            try {
                ItemStack newWeapon = weaponStack.copy();

                // 计算新等级（取较高值）
                int weaponLevel = KuvaWeapon.getNumber(weaponStack);
                int materialLevel = KuvaWeapon.getNumber(materialStack);
                int newLevel = Math.max(weaponLevel, materialLevel);

                // 应用升级倍率
                newLevel = (int) (newLevel * (1 + ConfigKuvaLich.upgradeMultiplier));

                // 限制最大等级
                newLevel = Math.min(newLevel, ConfigKuvaLich.upgradeLimit);

                // 继承材料的元素类型
                KuvaWeapon.setType(newWeapon, KuvaWeapon.getType(materialStack));
                KuvaWeapon.setNumber(newWeapon, newLevel);

                // 设置结果
                result.insertItem(0, newWeapon, false);
                evolve = true;
            } catch (Exception e) {
                LogUtil.error("武器融合升级失败", e);
                result.setStackInSlot(0, ItemStack.EMPTY);
                evolve = false;
            }
        } else if (!resultStack.isEmpty() && evolve) {
            // 玩家取走结果
            weapon.setStackInSlot(0, ItemStack.EMPTY);
            material.setStackInSlot(0, ItemStack.EMPTY);
            evolve = false;
            LogUtil.debug("武器融合完成，清空输入槽位");
        } else if (!evolve && !resultStack.isEmpty()) {
            // 输入改变，清空结果
            result.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    /**
     * 处理武器裂罅模组循环
     *
     * 消耗赤毒重置裂罅模组属性
     *
     * @param weaponStack 裂罅模组
     * @param materialStack 赤毒
     * @param resultStack 结果槽物品
     */
    private void handleItemRivenCycle(ItemStack weaponStack, ItemStack materialStack, ItemStack resultStack) {
        if (!ItemRivenModule.isRandom(weaponStack) && resultStack.isEmpty() && !cycle) {
            try {
                // 计算消耗的赤毒数量
                int cycle = ItemRivenModule.getCycle(weaponStack);
                int trend = ItemRivenModule.getTrend(weaponStack);
                int kuvaSpend = Math.min(cycle, 8);
                kuvaSpend += Math.pow(trend, 2) - Math.pow(trend - 1, 2);

                // 检查赤毒数量是否足够
                if (materialStack.getCount() >= kuvaSpend) {
                    // 扣除赤毒
                    ItemStack newMaterial = materialStack.copy();
                    newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                    this.inventorySlots.get(1).putStack(newMaterial);

                    // 生成新的裂罅模组
                    boolean isMelee = ItemRivenModule.isMelee(weaponStack);
                    ItemStack newRiven = ItemRivenModule.cycleModule(trend, cycle, isMelee);
                    result.insertItem(0, newRiven, false);

                    // 增加原模组的循环次数
                    ItemRivenModule.setCycle(weaponStack, cycle + 1);

                    this.cycle = true;

                    LogUtil.debugEvent("武器裂罅循环", "裂罅模组",
                            String.format("消耗赤毒: %d, 循环次数: %d, 倾向性: %d", kuvaSpend, cycle + 1, trend));
                } else {
                    LogUtil.debug("赤毒数量不足，需要: " + kuvaSpend + ", 当前: " + materialStack.getCount());
                }
            } catch (Exception e) {
                LogUtil.error("武器裂罅循环失败", e);
                result.setStackInSlot(0, ItemStack.EMPTY);
                cycle = false;
            }
        } else if (!resultStack.isEmpty() && cycle) {
            // 玩家取走结果
            weapon.setStackInSlot(0, ItemStack.EMPTY);
            cycle = false;
            LogUtil.debug("裂罅循环完成，清空输入槽位");
        } else if (!cycle && !resultStack.isEmpty()) {
            // 输入改变，清空结果
            result.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    /**
     * 处理战甲裂罅模组循环
     *
     * 消耗赤毒重置战甲裂罅模组属性
     *
     * @param weaponStack 战甲裂罅模组
     * @param materialStack 赤毒
     * @param resultStack 结果槽物品
     */
    private void handleWarframeRivenCycle(ItemStack weaponStack, ItemStack materialStack, ItemStack resultStack) {
        if (!WarframeRivenModule.isRandom(weaponStack) && resultStack.isEmpty() && !cycle) {
            try {
                // 计算消耗的赤毒数量
                int cycle = WarframeRivenModule.getCycle(weaponStack);
                int trend = WarframeRivenModule.getTrend(weaponStack);
                int kuvaSpend = Math.min(cycle, 8);
                kuvaSpend += Math.pow(trend, 2);

                // 检查赤毒数量是否足够
                if (materialStack.getCount() >= kuvaSpend) {
                    // 扣除赤毒
                    ItemStack newMaterial = materialStack.copy();
                    newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                    this.inventorySlots.get(1).putStack(newMaterial);

                    // 生成新的战甲裂罅模组
                    ItemStack newRiven = WarframeRivenModule.cycleModule(trend, cycle);
                    result.insertItem(0, newRiven, false);

                    // 增加原模组的循环次数
                    WarframeRivenModule.setCycle(weaponStack, cycle + 1);

                    this.cycle = true;

                    LogUtil.debugEvent("战甲裂罅循环", "战甲裂罅模组",
                            String.format("消耗赤毒: %d, 循环次数: %d, 倾向性: %d", kuvaSpend, cycle + 1, trend));
                } else {
                    LogUtil.debug("赤毒数量不足，需要: " + kuvaSpend + ", 当前: " + materialStack.getCount());
                }
            } catch (Exception e) {
                LogUtil.error("战甲裂罅循环失败", e);
                result.setStackInSlot(0, ItemStack.EMPTY);
                cycle = false;
            }
        } else if (!resultStack.isEmpty() && cycle) {
            // 玩家取走结果
            weapon.setStackInSlot(0, ItemStack.EMPTY);
            cycle = false;
            LogUtil.debug("战甲裂罅循环完成，清空输入槽位");
        } else if (!cycle && !resultStack.isEmpty()) {
            // 输入改变，清空结果
            result.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    /**
     * 处理添加基础属性
     *
     * 消耗裂罅碎块给武器添加基础属性
     *
     * @param weaponStack 武器
     * @param materialStack 裂罅碎块
     * @param resultStack 结果槽物品
     */
    private void handleAddBaseAttribute(ItemStack weaponStack, ItemStack materialStack, ItemStack resultStack) {
        if (!ItemModule.hasBase(weaponStack) && weaponStack.getCount() == 1 && resultStack.isEmpty()) {
            try {
                // 扣除裂罅碎块
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - 1);
                this.inventorySlots.get(1).putStack(newMaterial);

                // 添加基础属性
                ItemStack newWeapon = weaponStack.copy();
                ItemModule.setBaseAttribute(newWeapon);

                // 清空输入槽，设置结果
                weapon.setStackInSlot(0, ItemStack.EMPTY);
                result.setStackInSlot(0, newWeapon);

                LogUtil.debugEvent("添加基础属性", weaponStack.getDisplayName(), "消耗1个裂罅碎块");
            } catch (Exception e) {
                LogUtil.error("添加基础属性失败", e);
                result.setStackInSlot(0, ItemStack.EMPTY);
            }
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
            // 返还武器槽物品
            if (!weapon.getStackInSlot(0).isEmpty()) {
                if (!this.mergeItemStack(weapon.getStackInSlot(0), 3, 3 + 36, true)) {
                    LogUtil.warn("玩家背包已满，物品掉落: " + weapon.getStackInSlot(0).getDisplayName());
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), weapon.getStackInSlot(0)));
                }
            }

            // 返还材料槽物品
            if (!material.getStackInSlot(0).isEmpty()) {
                if (!this.mergeItemStack(material.getStackInSlot(0), 3, 3 + 36, true)) {
                    LogUtil.warn("玩家背包已满，物品掉落: " + material.getStackInSlot(0).getDisplayName());
                    world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), material.getStackInSlot(0)));
                }
            }

            // 只有在没有进行操作时才返还结果槽
            if (!evolve && !cycle) {
                if (!result.getStackInSlot(0).isEmpty()) {
                    if (!this.mergeItemStack(result.getStackInSlot(0), 3, 3 + 36, true)) {
                        LogUtil.warn("玩家背包已满，物品掉落: " + result.getStackInSlot(0).getDisplayName());
                        world.spawnEntity(new EntityItem(world, pos.getX(), pos.getY(), pos.getZ(), result.getStackInSlot(0)));
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
     * 武器槽位类
     *
     * 接受：赤毒武器、裂罅模组、未添加基础属性的武器
     */
    public static class WeaponSlot extends SlotItemHandler {
        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            // 接受赤毒武器
            if (KuvaWeapon.hasType(itemStack)) {
                return super.isItemValid(itemStack);
            }
            // 接受裂罅模组
            if (itemStack.getItem() instanceof ItemRivenModule || itemStack.getItem() instanceof WarframeRivenModule) {
                return super.isItemValid(itemStack);
            }
            // 接受未添加基础属性的武器（单个）
            if (!ItemModule.hasBase(itemStack) && itemStack.getCount() == 1) {
                return super.isItemValid(itemStack);
            }
            return false;
        }
    }

    /**
     * 材料槽位类
     *
     * 接受：赤毒武器、赤毒、裂罅碎块
     */
    public static class MaterialSlot extends SlotItemHandler {
        public MaterialSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            // 接受赤毒武器
            if (KuvaWeapon.hasType(itemStack)) {
                return super.isItemValid(itemStack);
            }
            // 接受赤毒
            if (itemStack.getItem() instanceof Kuva) {
                return super.isItemValid(itemStack);
            }
            // 接受裂罅碎块
            if (itemStack.getItem() instanceof RivenSliver) {
                return super.isItemValid(itemStack);
            }
            return false;
        }
    }

    /**
     * 结果槽位类
     *
     * 只读，不接受任何物品放入
     */
    public static class ResultSlot extends SlotItemHandler {
        public ResultSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean isItemValid(ItemStack itemStack) {
            return false;
        }
    }
}