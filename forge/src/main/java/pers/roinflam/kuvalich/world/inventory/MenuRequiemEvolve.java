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
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.Forma;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.LichReliquary;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.weapon.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 安魂之融菜单（1.20.1版本）
 * Requiem Evolve Menu (1.20.1 version)
 *
 * 支持功能：
 * 1. 武器融合（两把赤毒武器 → 新武器）
 * 2. 武器裂罅循环（裂罅模组 + 赤毒 → 新裂罅）
 * 3. 战甲裂罅循环（战甲裂罅模组 + 赤毒 → 新裂罅）
 * 4. 添加基础属性（未开光物品 + 裂罅碎块 → 开光物品）
 * 5. Forma洗面板（已开光非赤毒未锁定武器 + 塑形块 → 直接消耗并重新随机面板）
 * 6. TACZ枪械强化（TACZ枪械 + 玄骸之遗 → 永久增强基础伤害）
 */
public class MenuRequiemEvolve extends AbstractContainerMenu {

    /** 所在世界 / World reference */
    private final Level level;
    /** 方块位置 / Block position */
    private final BlockPos pos;
    /** 操作的玩家 / Operating player */
    private final Player player;

    /** 武器槽位处理器（左侧，1个槽位）/ Weapon handler (left, 1 slot) */
    private final ItemStackHandler weaponHandler;
    /** 材料槽位处理器（中间，1个槽位）/ Material handler (middle, 1 slot) */
    private final ItemStackHandler materialHandler;
    /** 结果槽位处理器（右侧，1个槽位）/ Result handler (right, 1 slot) */
    private final ItemStackHandler resultHandler;

    /** 融合模式标记 */
    private boolean evolveMode = false;
    /** 循环模式标记 */
    private boolean cycleMode = false;
    /** 基础属性刚处理完毕标记（含开光、Forma、枪械强化） */
    private boolean baseAttributeJustProcessed = false;

    /** 上一tick的武器快照 */
    private ItemStack lastWeapon = ItemStack.EMPTY;
    /** 上一tick的材料快照 */
    private ItemStack lastMaterial = ItemStack.EMPTY;

    /**
     * 构造函数（服务端，从方块打开）
     *
     * @param windowId        窗口ID
     * @param playerInventory 玩家背包
     * @param level           世界
     * @param pos             方块位置
     */
    public MenuRequiemEvolve(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_EVOLVE.get(), windowId);

        this.level = level;
        this.pos = pos;
        this.player = playerInventory.player;

        this.weaponHandler = new ItemStackHandler(1);
        this.materialHandler = new ItemStackHandler(1);
        this.resultHandler = new ItemStackHandler(1);

        // 槽位0：武器（左侧）/ Slot 0: Weapon (left)
        this.addSlot(new WeaponSlot(this.weaponHandler, 0, 31, 32));
        // 槽位1：材料（中间）/ Slot 1: Material (middle)
        this.addSlot(new MaterialSlot(this.materialHandler, 0, 80, 32));
        // 槽位2：结果（右侧）/ Slot 2: Result (right)
        this.addSlot(new ResultSlot(this, this.resultHandler, 0, 131, 32));

        // 槽位3-11：玩家快捷栏
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 171 - 10));
        }
        // 槽位12-38：玩家主背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 113 - 10 + row * 18));
            }
        }

        LogUtil.debugEvent("安魂之融菜单创建", player.getName().getString(), "位置: " + pos.toString());
    }

    /**
     * Shift+点击快速转移物品
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot == null || !slot.hasItem()) {
            return itemstack;
        }

        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        if (index < 3) {
            if (!this.moveItemStackTo(slotStack, 3, 39, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean transferred = false;
            if (this.moveItemStackTo(slotStack, 0, 1, false)) {
                transferred = true;
            } else if (this.moveItemStackTo(slotStack, 1, 2, false)) {
                transferred = true;
            }
            if (!transferred) {
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
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!level.isClientSide) {
            checkAndProcessCrafting();
        }
    }

    /**
     * 检测输入变化并触发合成逻辑
     */
    private void checkAndProcessCrafting() {
        ItemStack currentWeapon = weaponHandler.getStackInSlot(0);
        ItemStack currentMaterial = materialHandler.getStackInSlot(0);
        ItemStack currentResult = resultHandler.getStackInSlot(0);

        boolean inputChanged = !ItemStack.matches(currentWeapon, lastWeapon) ||
                !ItemStack.matches(currentMaterial, lastMaterial);

        // 输入变化时，清除预览模式的过期结果（防止无限刷物品）
        if (inputChanged && !currentResult.isEmpty() && (evolveMode || cycleMode)) {
            resultHandler.setStackInSlot(0, ItemStack.EMPTY);
            currentResult = ItemStack.EMPTY;
            evolveMode = false;
            cycleMode = false;
            LogUtil.debugEvent("安魂之融", "输入变化，清除过期预览结果", "防止物品复制");
        }

        if (currentWeapon.isEmpty() || currentMaterial.isEmpty()) {
            if (!currentResult.isEmpty()) {
                if (!baseAttributeJustProcessed) {
                    resultHandler.setStackInSlot(0, ItemStack.EMPTY);
                    evolveMode = false;
                    cycleMode = false;
                }
            }

            if (currentResult.isEmpty() && baseAttributeJustProcessed) {
                baseAttributeJustProcessed = false;
            }

            lastWeapon = weaponHandler.getStackInSlot(0).copy();
            lastMaterial = materialHandler.getStackInSlot(0).copy();
            return;
        }

        if (inputChanged && currentResult.isEmpty()) {
            if (baseAttributeJustProcessed) {
                baseAttributeJustProcessed = false;
            }

            processCrafting(currentWeapon, currentMaterial);

            lastWeapon = weaponHandler.getStackInSlot(0).copy();
            lastMaterial = materialHandler.getStackInSlot(0).copy();
        }
    }

    /**
     * 根据武器和材料的类型分发到对应的合成处理方法
     *
     * 匹配优先级：
     * 1. 赤毒武器 + 赤毒武器 → 武器融合
     * 2. 武器裂罅 + 赤毒 → 武器裂罅循环
     * 3. 战甲裂罅 + 赤毒 → 战甲裂罅循环
     * 4. 已开光非赤毒未锁定武器 + 塑形块 → Forma洗面板
     * 5. 未开光物品 + 裂罅碎块 → 添加基础属性（开光）
     * 6. TACZ枪械 + 玄骸之遗 → 枪械永久强化
     *
     * @param weaponStack   武器槽中的物品
     * @param materialStack 材料槽中的物品
     */
    private void processCrafting(ItemStack weaponStack, ItemStack materialStack) {
        // 情况1：赤毒武器融合
        if (KuvaWeaponUtil.hasType(weaponStack) && KuvaWeaponUtil.hasType(materialStack)) {
            processWeaponEvolve(weaponStack, materialStack);
            return;
        }

        // 情况2：武器裂罅循环
        if (weaponStack.getItem() instanceof ItemRivenModule && materialStack.getItem() instanceof Kuva) {
            processItemRivenCycle(weaponStack, materialStack);
            return;
        }

        // 情况3：战甲裂罅循环
        if (weaponStack.getItem() instanceof WarframeRivenModule && materialStack.getItem() instanceof Kuva) {
            processWarframeRivenCycle(weaponStack, materialStack);
            return;
        }

        // 情况4：Forma洗面板
        if (materialStack.getItem() instanceof Forma
                && WeaponModuleHandler.hasBase(weaponStack)
                && !KuvaWeaponUtil.hasType(weaponStack)
                && !WeaponModuleHandler.isFormaLocked(weaponStack)
                && weaponStack.getCount() == 1) {
            processFormaReroll(weaponStack, materialStack);
            return;
        }

        // 情况5：添加基础属性（开光）
        if (!weaponStack.isEmpty() && materialStack.getItem() instanceof RivenSliver) {
            processAddBaseAttribute(weaponStack, materialStack);
            return;
        }

        // 情况6：TACZ枪械 + 玄骸之遗 → 枪械永久强化
        if (TaczGunEnhanceUtil.isLichReliquary(materialStack)
                && TaczGunEnhanceUtil.isEnhanceableTaczGun(weaponStack)
                && weaponStack.getCount() == 1) {
            processGunEnhance(weaponStack, materialStack);
            return;
        }
    }

    // ==================== 合成处理方法 ====================

    /**
     * 处理赤毒武器融合
     */
    private void processWeaponEvolve(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();

            int weaponLevel = KuvaWeaponUtil.getNumber(weaponStack);
            int materialLevel = KuvaWeaponUtil.getNumber(materialStack);
            int newLevel = Math.max(weaponLevel, materialLevel);

            newLevel = (int) (newLevel * (1 + ModConfig.KUVA_LICH.upgradeMultiplier.get()));
            newLevel = Math.min(newLevel, ModConfig.KUVA_LICH.upgradeLimit.get());

            KuvaWeaponUtil.setType(newWeapon, KuvaWeaponUtil.getType(materialStack));
            KuvaWeaponUtil.setNumber(newWeapon, newLevel);

            resultHandler.setStackInSlot(0, newWeapon);
            evolveMode = true;
        } catch (Exception e) {
            LogUtil.error("武器融合失败", e);
        }
    }

    /**
     * 处理武器裂罅循环
     */
    private void processItemRivenCycle(ItemStack weaponStack, ItemStack materialStack) {
        if (ItemRivenModule.isRandom(weaponStack)) {
            return;
        }

        try {
            int cycleCount = ItemRivenModule.getCycle(weaponStack);
            int trend = ItemRivenModule.getTrend(weaponStack);
            int kuvaSpend = Math.min(cycleCount, 8);
            kuvaSpend += Math.pow(trend, 2) - Math.pow(trend - 1, 2);

            if (materialStack.getCount() >= kuvaSpend) {
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                materialHandler.setStackInSlot(0, newMaterial);

                int rivenMode = ItemRivenModule.getRivenMode(weaponStack);

                ItemStack newRiven = ItemRivenModule.cycleModule(trend, cycleCount, rivenMode);
                resultHandler.setStackInSlot(0, newRiven);

                ItemRivenModule.setCycle(weaponStack, cycleCount + 1);
                cycleMode = true;

                LogUtil.debugEvent("武器裂罅循环", "消耗赤毒", String.valueOf(kuvaSpend));
            }
        } catch (Exception e) {
            LogUtil.error("武器裂罅循环失败", e);
        }
    }

    /**
     * 处理战甲裂罅循环
     */
    private void processWarframeRivenCycle(ItemStack weaponStack, ItemStack materialStack) {
        if (WarframeRivenModule.isRandom(weaponStack)) {
            return;
        }

        try {
            int cycleCount = WarframeRivenModule.getCycle(weaponStack);
            int trend = WarframeRivenModule.getTrend(weaponStack);
            int kuvaSpend = Math.min(cycleCount, 8);
            kuvaSpend += Math.pow(trend, 2);

            if (materialStack.getCount() >= kuvaSpend) {
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                materialHandler.setStackInSlot(0, newMaterial);

                ItemStack newRiven = WarframeRivenModule.cycleModule(trend, cycleCount);
                resultHandler.setStackInSlot(0, newRiven);

                WarframeRivenModule.setCycle(weaponStack, cycleCount + 1);
                cycleMode = true;

                LogUtil.debugEvent("战甲裂罅循环", "消耗赤毒", String.valueOf(kuvaSpend));
            }
        } catch (Exception e) {
            LogUtil.error("战甲裂罅循环失败", e);
        }
    }

    /**
     * 处理Forma洗面板
     */
    private void processFormaReroll(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();

            if (!WeaponModuleHandler.clearBaseAttribute(newWeapon)) {
                LogUtil.warn("Forma洗面板失败：物品已锁定");
                return;
            }
            WeaponModuleHandler.setBaseAttribute(newWeapon);

            if (ModConfig.KUVA_LICH.formaLockEnabled.get()) {
                double lockChance = ModConfig.KUVA_LICH.formaLockChance.get();
                if (lockChance > 0) {
                    double roll = level.random.nextDouble() * 100.0;
                    if (roll < lockChance) {
                        WeaponModuleHandler.setFormaLocked(newWeapon);
                        LogUtil.debugEvent("Forma锁定触发",
                                newWeapon.getHoverName().getString(),
                                "概率 " + lockChance + "%, 掷骰 " + String.format("%.2f", roll) + " → 面板已被永久锁定");
                    }
                }
            }

            ItemStack newMaterial = materialStack.copy();
            newMaterial.shrink(1);
            materialHandler.setStackInSlot(0, newMaterial);

            resultHandler.setStackInSlot(0, newWeapon);
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);

            baseAttributeJustProcessed = true;

            LogUtil.debugEvent("Forma洗面板", weaponStack.getHoverName().getString(),
                    "面板已重新随机，塑形块已消耗");
        } catch (Exception e) {
            LogUtil.error("Forma洗面板失败", e);
        }
    }

    /**
     * 处理添加基础属性（开光）
     */
    private void processAddBaseAttribute(ItemStack weaponStack, ItemStack materialStack) {
        if (!WeaponModuleHandler.hasBase(weaponStack) && weaponStack.getCount() == 1) {
            try {
                ItemStack newWeapon = weaponStack.copy();
                WeaponModuleHandler.setBaseAttribute(newWeapon);

                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - 1);

                baseAttributeJustProcessed = true;

                resultHandler.setStackInSlot(0, newWeapon);
                materialHandler.setStackInSlot(0, newMaterial);
                weaponHandler.setStackInSlot(0, ItemStack.EMPTY);

                LogUtil.debugEvent("添加基础属性", weaponStack.getHoverName().getString(), "开光完成");
            } catch (Exception e) {
                LogUtil.error("添加基础属性失败", e);
                baseAttributeJustProcessed = false;
            }
        }
    }

    /**
     * 处理TACZ枪械永久强化（玄骸之遗 + 枪械 → 永久增强基础伤害）
     *
     * <p>行为：
     * <br>1. 复制枪械
     * <br>2. 强化次数+1（NBT存储在枪械上）
     * <br>3. 消耗1个玄骸之遗
     * <br>4. 枪械移到结果槽，清空武器槽（唯一副本）
     * <br>5. 设置baseAttributeJustProcessed标记，确保关闭时正确返还</p>
     *
     * <p>前提条件（在processCrafting中已验证）：
     * <br>- 武器是可强化的TACZ枪械（配置启用、未达上限）
     * <br>- 材料是玄骸之遗
     * <br>- 武器数量为1</p>
     *
     * @param weaponStack   TACZ枪械
     * @param materialStack 玄骸之遗
     */
    private void processGunEnhance(ItemStack weaponStack, ItemStack materialStack) {
        try {
            // 复制枪械，在副本上操作
            ItemStack newGun = weaponStack.copy();

            // 强化次数+1
            int oldCount = TaczGunEnhanceUtil.getEnhanceCount(newGun);
            TaczGunEnhanceUtil.setEnhanceCount(newGun, oldCount + 1);

            // 消耗1个玄骸之遗
            ItemStack newMaterial = materialStack.copy();
            newMaterial.shrink(1);
            materialHandler.setStackInSlot(0, newMaterial);

            // 枪械移到结果槽，清空武器槽（和开光/Forma逻辑一致）
            resultHandler.setStackInSlot(0, newGun);
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);

            // 复用开光的保护标记（关闭时结果是唯一副本，必须返还）
            baseAttributeJustProcessed = true;

            double totalPercent = TaczGunEnhanceUtil.getTotalEnhancePercent(newGun);
            LogUtil.debugEvent("TACZ枪械强化", weaponStack.getHoverName().getString(),
                    "强化次数: " + (oldCount + 1) + ", 总增幅: " + String.format("+%.0f%%", totalPercent * 100));
        } catch (Exception e) {
            LogUtil.error("TACZ枪械强化失败", e);
            baseAttributeJustProcessed = false;
        }
    }

    // ==================== 结果取走回调 ====================

    /**
     * 结果物品被玩家取走时的回调
     */
    public void onResultTaken() {
        if (evolveMode) {
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            materialHandler.setStackInSlot(0, ItemStack.EMPTY);
            evolveMode = false;
            LogUtil.debugEvent("安魂之融", "融合结果被取走", "已清空武器和材料槽");
        }

        if (cycleMode) {
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            cycleMode = false;
            LogUtil.debugEvent("安魂之融", "裂罅循环结果被取走", "已清空武器槽");
        }

        if (baseAttributeJustProcessed) {
            baseAttributeJustProcessed = false;
        }
    }

    // ==================== 菜单关闭 ====================

    /**
     * 菜单关闭时返还物品
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            returnSlotToPlayer(weaponHandler, 0, player);
            returnSlotToPlayer(materialHandler, 0, player);

            ItemStack resultStack = resultHandler.getStackInSlot(0);
            if (!resultStack.isEmpty()) {
                if (evolveMode || cycleMode) {
                    resultHandler.setStackInSlot(0, ItemStack.EMPTY);
                    LogUtil.debugEvent("安魂之融菜单关闭", "丢弃未取走的预览结果",
                            resultStack.getHoverName().getString());
                } else {
                    returnSlotToPlayer(resultHandler, 0, player);
                }
            }

            evolveMode = false;
            cycleMode = false;
            baseAttributeJustProcessed = false;
        }
    }

    /**
     * 将指定槽位的物品返还给玩家
     */
    private void returnSlotToPlayer(ItemStackHandler handler, int slot, Player player) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            if (!this.moveItemStackTo(stack, 3, 39, true)) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy()));
            }
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    // ============================== 内部槽位类 ==============================

    /**
     * 武器槽位（左侧）
     *
     * 接受规则：
     * - 赤毒武器（用于融合）
     * - 武器裂罅模组 / 战甲裂罅模组（用于循环）
     * - 未开光的单个物品（用于开光）
     * - 已开光的单个非赤毒武器（用于Forma洗面板）
     * - TACZ枪械（用于玄骸强化）
     */
    public static class WeaponSlot extends SlotItemHandler {

        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            // 赤毒武器（融合用）
            if (KuvaWeaponUtil.hasType(stack)) {
                return super.mayPlace(stack);
            }
            // 裂罅模组（循环用）
            if (stack.getItem() instanceof ItemRivenModule || stack.getItem() instanceof WarframeRivenModule) {
                return super.mayPlace(stack);
            }
            // 未开光物品（开光用）
            if (!WeaponModuleHandler.hasBase(stack) && stack.getCount() == 1) {
                return super.mayPlace(stack);
            }
            // 已开光的非赤毒武器（Forma洗面板用）
            if (WeaponModuleHandler.hasBase(stack) && stack.getCount() == 1) {
                return super.mayPlace(stack);
            }
            // TACZ枪械（玄骸强化用） — 安全调用，无TACZ时返回false
            try {
                if (TaczGunEnhanceUtil.isTaczGun(stack) && stack.getCount() == 1) {
                    return super.mayPlace(stack);
                }
            } catch (NoClassDefFoundError ignored) {
                // TACZ未安装时忽略
            }
            return false;
        }
    }

    /**
     * 材料槽位（中间）
     *
     * 接受规则：
     * - 赤毒武器（融合的第二把武器）
     * - 赤毒（裂罅循环的消耗品）
     * - 裂罅碎块（开光的消耗品）
     * - 塑形块（Forma洗面板的消耗品）
     * - 玄骸之遗（TACZ枪械强化的消耗品）
     */
    public static class MaterialSlot extends SlotItemHandler {

        public MaterialSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            // 赤毒武器（融合材料）
            if (KuvaWeaponUtil.hasType(stack)) {
                return super.mayPlace(stack);
            }
            // 赤毒（裂罅循环消耗品）
            if (stack.getItem() instanceof Kuva) {
                return super.mayPlace(stack);
            }
            // 裂罅碎块（开光消耗品）
            if (stack.getItem() instanceof RivenSliver) {
                return super.mayPlace(stack);
            }
            // 塑形块（Forma洗面板消耗品）
            if (stack.getItem() instanceof Forma) {
                return super.mayPlace(stack);
            }
            // 玄骸之遗（TACZ枪械强化消耗品）
            if (stack.getItem() instanceof LichReliquary) {
                return super.mayPlace(stack);
            }
            return false;
        }
    }

    /**
     * 结果槽位（右侧，只读）
     */
    public static class ResultSlot extends SlotItemHandler {
        private final MenuRequiemEvolve menu;

        public ResultSlot(MenuRequiemEvolve menu, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            menu.onResultTaken();
            super.onTake(player, stack);
        }
    }
}
