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
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.Forma;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.module.item.ItemRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
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
 * 5. Forma洗面板（已开光非赤毒武器 + 塑形块 → 直接消耗并重新随机面板）
 *
 * ⭐ 已修复：结果槽支持Shift+点击转移到背包
 * ⭐ 已修复：关闭菜单时根据模式正确处理物品归还/丢弃，彻底杜绝物品复制
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

    /**
     * 融合模式标记：两把赤毒武器融合
     * Evolve mode flag: two Kuva weapons fused
     *
     * 结果是系统生成的预览品，取走时消耗两把武器，
     * 关闭菜单时若未取走则丢弃结果（原始武器已在槽内可返还）
     */
    private boolean evolveMode = false;

    /**
     * 循环模式标记：裂罅模组循环
     * Cycle mode flag: Riven module re-roll
     *
     * 赤毒在处理时已扣除，结果是系统生成的新裂罅预览，
     * 取走时消耗旧裂罅，关闭菜单时若未取走则丢弃结果
     */
    private boolean cycleMode = false;

    /**
     * 基础属性刚处理完毕标记
     * Base attribute just processed flag
     *
     * 适用于"开光"和"Forma洗面板"两种场景：
     * - 武器槽已在处理时被清空
     * - 材料已在处理时被消耗
     * - 结果槽中的物品是唯一副本，关闭时必须返还
     */
    private boolean baseAttributeJustProcessed = false;

    /** 上一tick的武器快照（用于检测输入变化）/ Last tick weapon snapshot */
    private ItemStack lastWeapon = ItemStack.EMPTY;
    /** 上一tick的材料快照（用于检测输入变化）/ Last tick material snapshot */
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

        // 槽位3-11：玩家快捷栏 / Slots 3-11: Player hotbar
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 171 - 10));
        }
        // 槽位12-38：玩家主背包 / Slots 12-38: Player main inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 113 - 10 + row * 18));
            }
        }

        LogUtil.debugEvent("安魂之融菜单创建", player.getName().getString(), "位置: " + pos.toString());
    }

    /**
     * Shift+点击快速转移物品
     *
     * 槽位布局：
     *   0 = 武器槽（左）  1 = 材料槽（中）  2 = 结果槽（右）
     *   3~11 = 快捷栏  12~38 = 主背包
     *
     * ⭐ 结果槽（index=2）支持Shift+点击：
     *    moveItemStackTo完成后，框架自动调用 slot.onTake → onResultTaken()
     *
     * @param player 操作的玩家
     * @param index  被点击的槽位索引
     * @return 转移前的物品副本（用于判断转移是否成功），失败返回EMPTY
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

        // ====== 从容器槽位（0/1/2）转移到背包（3~38）======
        if (index < 3) {
            if (!this.moveItemStackTo(slotStack, 3, 39, true)) {
                return ItemStack.EMPTY;
            }
            // 框架会在下面调用 slot.onTake，ResultSlot.onTake → onResultTaken()
        }
        // ====== 从背包（3~38）转移到容器槽位（0/1）======
        else {
            boolean transferred = false;

            // 优先尝试放入武器槽 / Try weapon slot first
            if (this.moveItemStackTo(slotStack, 0, 1, false)) {
                transferred = true;
            }
            // 再尝试放入材料槽 / Then try material slot
            else if (this.moveItemStackTo(slotStack, 1, 2, false)) {
                transferred = true;
            }

            if (!transferred) {
                return ItemStack.EMPTY;
            }
        }

        // 标准后处理 / Standard post-processing
        if (slotStack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotStack.getCount() == itemstack.getCount()) {
            return ItemStack.EMPTY;
        }

        // 对于ResultSlot，此处调用onTake → onResultTaken()
        slot.onTake(player, slotStack);
        return itemstack;
    }

    /**
     * 每次同步时检测并处理合成
     * 在服务端每tick被调用，负责检测输入变化并触发对应逻辑
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!level.isClientSide) {
            checkAndProcessCrafting();
        }
    }

    /**
     * 检测输入变化并触发合成逻辑
     *
     * 核心逻辑：
     * 1. 输入为空 → 清空结果和模式标记（baseAttribute模式除外，因为武器槽已清空）
     * 2. 输入变化且结果为空 → 尝试合成
     * 3. 输入未变 → 不处理（避免重复触发）
     */
    private void checkAndProcessCrafting() {
        ItemStack currentWeapon = weaponHandler.getStackInSlot(0);
        ItemStack currentMaterial = materialHandler.getStackInSlot(0);
        ItemStack currentResult = resultHandler.getStackInSlot(0);

        boolean inputChanged = !ItemStack.matches(currentWeapon, lastWeapon) ||
                !ItemStack.matches(currentMaterial, lastMaterial);

        // 输入不完整时清理结果（baseAttribute模式除外）
        // Clear result when input is incomplete (except baseAttribute mode)
        if (currentWeapon.isEmpty() || currentMaterial.isEmpty()) {
            if (!currentResult.isEmpty()) {
                if (!baseAttributeJustProcessed) {
                    resultHandler.setStackInSlot(0, ItemStack.EMPTY);
                    evolveMode = false;
                    cycleMode = false;
                }
            }

            // baseAttribute处理完后，结果已放好，下一tick重置标记
            // After baseAttribute processing, result is placed, reset flag next tick
            if (currentResult.isEmpty() && baseAttributeJustProcessed) {
                baseAttributeJustProcessed = false;
            }

            lastWeapon = currentWeapon.copy();
            lastMaterial = currentMaterial.copy();
            return;
        }

        // 输入变化且结果为空时，尝试合成
        // Try crafting when input changed and result is empty
        if (inputChanged && currentResult.isEmpty()) {
            if (baseAttributeJustProcessed) {
                baseAttributeJustProcessed = false;
            }

            processCrafting(currentWeapon, currentMaterial);
            lastWeapon = currentWeapon.copy();
            lastMaterial = currentMaterial.copy();
        }
    }

    /**
     * 根据武器和材料的类型分发到对应的合成处理方法
     *
     * 匹配优先级：
     * 1. 赤毒武器 + 赤毒武器 → 武器融合
     * 2. 武器裂罅 + 赤毒 → 武器裂罅循环
     * 3. 战甲裂罅 + 赤毒 → 战甲裂罅循环
     * 4. 已开光非赤毒未锁定武器 + 塑形块 → Forma洗面板（直接消耗）
     * 5. 未开光物品 + 裂罅碎块 → 添加基础属性（开光）
     *
     * @param weaponStack   武器槽中的物品
     * @param materialStack 材料槽中的物品
     */
    private void processCrafting(ItemStack weaponStack, ItemStack materialStack) {
        // 情况1：赤毒武器融合 / Case 1: Kuva weapon evolve
        if (KuvaWeapon.hasType(weaponStack) && KuvaWeapon.hasType(materialStack)) {
            processWeaponEvolve(weaponStack, materialStack);
            return;
        }

        // 情况2：武器裂罅循环 / Case 2: Item Riven cycle
        if (weaponStack.getItem() instanceof ItemRivenModule && materialStack.getItem() instanceof Kuva) {
            processItemRivenCycle(weaponStack, materialStack);
            return;
        }

        // 情况3：战甲裂罅循环 / Case 3: Warframe Riven cycle
        if (weaponStack.getItem() instanceof WarframeRivenModule && materialStack.getItem() instanceof Kuva) {
            processWarframeRivenCycle(weaponStack, materialStack);
            return;
        }

        // 情况4：Forma洗面板（已开光 + 非赤毒 + 未锁定 + 塑形块 → 直接消耗）
        // Case 4: Forma re-roll (opened + non-Kuva + not locked + Forma → instant consume)
        if (materialStack.getItem() instanceof Forma
                && ItemModule.hasBase(weaponStack)
                && !KuvaWeapon.hasType(weaponStack)
                && !ItemModule.isFormaLocked(weaponStack)
                && weaponStack.getCount() == 1) {
            processFormaReroll(weaponStack, materialStack);
            return;
        }

        // 情况5：添加基础属性（未开光 + 裂罅碎块 → 开光）
        // Case 5: Add base attribute (unopened + Riven Sliver → open)
        if (!weaponStack.isEmpty() && materialStack.getItem() instanceof RivenSliver) {
            processAddBaseAttribute(weaponStack, materialStack);
            return;
        }
    }

    /**
     * 处理赤毒武器融合
     * 两把赤毒武器合并，取较高等级并应用升级倍率
     * 结果是预览品，取走时消耗两把武器
     *
     * @param weaponStack   左侧赤毒武器
     * @param materialStack 中间赤毒武器（作为材料）
     */
    private void processWeaponEvolve(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();

            int weaponLevel = KuvaWeapon.getNumber(weaponStack);
            int materialLevel = KuvaWeapon.getNumber(materialStack);
            int newLevel = Math.max(weaponLevel, materialLevel);

            newLevel = (int) (newLevel * (1 + ModConfig.KUVA_LICH.upgradeMultiplier.get()));
            newLevel = Math.min(newLevel, ModConfig.KUVA_LICH.upgradeLimit.get());

            KuvaWeapon.setType(newWeapon, KuvaWeapon.getType(materialStack));
            KuvaWeapon.setNumber(newWeapon, newLevel);

            resultHandler.setStackInSlot(0, newWeapon);
            evolveMode = true;
        } catch (Exception e) {
            LogUtil.error("武器融合失败", e);
        }
    }

    /**
     * 处理武器裂罅循环
     * 消耗赤毒重新随机裂罅模组属性，消耗量随循环次数和倾向性递增
     * 赤毒在此方法中立即扣除，结果是新裂罅预览，取走时消耗旧裂罅
     *
     * @param weaponStack   武器裂罅模组
     * @param materialStack 赤毒（消耗品）
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
                // 扣除赤毒 / Deduct Kuva
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                materialHandler.setStackInSlot(0, newMaterial);

                // 生成新的裂罅模组 / Generate new Riven module
                boolean isMelee = ItemRivenModule.isMelee(weaponStack);
                ItemStack newRiven = ItemRivenModule.cycleModule(trend, cycleCount, isMelee);
                resultHandler.setStackInSlot(0, newRiven);

                // 更新循环计数 / Update cycle count
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
     * 消耗赤毒重新随机战甲裂罅模组属性
     * 赤毒在此方法中立即扣除，结果是新裂罅预览，取走时消耗旧裂罅
     *
     * @param weaponStack   战甲裂罅模组
     * @param materialStack 赤毒（消耗品）
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
                // 扣除赤毒 / Deduct Kuva
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - kuvaSpend);
                materialHandler.setStackInSlot(0, newMaterial);

                // 生成新的裂罅模组 / Generate new Riven module
                ItemStack newRiven = WarframeRivenModule.cycleModule(trend, cycleCount);
                resultHandler.setStackInSlot(0, newRiven);

                // 更新循环计数 / Update cycle count
                WarframeRivenModule.setCycle(weaponStack, cycleCount + 1);
                cycleMode = true;

                LogUtil.debugEvent("战甲裂罅循环", "消耗赤毒", String.valueOf(kuvaSpend));
            }
        } catch (Exception e) {
            LogUtil.error("战甲裂罅循环失败", e);
        }
    }

    /**
     * 处理Forma洗面板（直接消耗，立即生效）
     *
     * 行为：
     * 1. 复制武器并清除旧面板 → 重新随机
     * 2. 若配置启用Forma锁定且概率命中，给武器打上永久锁定标记
     * 3. 消耗1个塑形块
     * 4. 武器槽清空，结果放到结果槽（唯一副本）
     * 5. 设置baseAttributeJustProcessed标记，确保关闭时正确返还
     *
     * 前提条件（在processCrafting中已验证）：
     * - 武器已开光（hasBase == true）
     * - 武器不是赤毒武器（!KuvaWeapon.hasType）
     * - 武器未被锁定（!isFormaLocked）
     * - 武器数量为1
     * - 材料是塑形块（Forma）
     *
     * @param weaponStack   已开光的非赤毒武器（已确认未锁定）
     * @param materialStack 塑形块
     */
    private void processFormaReroll(ItemStack weaponStack, ItemStack materialStack) {
        try {
            // 复制武器，在副本上操作 / Copy weapon, operate on the copy
            ItemStack newWeapon = weaponStack.copy();

            // 清除旧面板 → 重新随机 / Clear old panel → re-randomize
            if (!ItemModule.clearBaseAttribute(newWeapon)) {
                // 理论上不会走到这里（processCrafting已检查锁定），安全兜底
                // Should not reach here (processCrafting already checked lock), safety fallback
                LogUtil.warn("Forma洗面板失败：物品已锁定");
                return;
            }
            ItemModule.setBaseAttribute(newWeapon);

            // ⭐ 检查是否触发Forma锁定 / Check if Forma lock triggers
            if (ModConfig.KUVA_LICH.formaLockEnabled.get()) {
                double lockChance = ModConfig.KUVA_LICH.formaLockChance.get();
                if (lockChance > 0) {
                    // 使用世界随机数生成器，范围0~100，与lockChance比较
                    // Use world random generator, range 0~100, compare with lockChance
                    double roll = level.random.nextDouble() * 100.0;
                    if (roll < lockChance) {
                        ItemModule.setFormaLocked(newWeapon);
                        LogUtil.debugEvent("Forma锁定触发",
                                newWeapon.getHoverName().getString(),
                                "概率 " + lockChance + "%, 掷骰 " + String.format("%.2f", roll) + " → 面板已被永久锁定");
                    }
                }
            }

            // 消耗1个塑形块 / Consume 1 Forma
            ItemStack newMaterial = materialStack.copy();
            newMaterial.shrink(1);
            materialHandler.setStackInSlot(0, newMaterial);

            // 武器移到结果槽，清空武器槽（和开光逻辑完全一致）
            // Move weapon to result slot, clear weapon slot (identical to opening logic)
            resultHandler.setStackInSlot(0, newWeapon);
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);

            // 复用开光的保护标记（关闭时结果是唯一副本，必须返还）
            // Reuse opening's protection flag (result is the only copy on close, must return)
            baseAttributeJustProcessed = true;

            LogUtil.debugEvent("Forma洗面板", weaponStack.getHoverName().getString(),
                    "面板已重新随机，塑形块已消耗");
        } catch (Exception e) {
            LogUtil.error("Forma洗面板失败", e);
        }
    }

    /**
     * 处理添加基础属性（开光）
     * 未开光的单个物品 + 裂罅碎块 → 带有基础面板属性的物品
     *
     * 处理完成后武器槽立即清空，结果是唯一副本
     *
     * @param weaponStack   未开光的物品
     * @param materialStack 裂罅碎块
     */
    private void processAddBaseAttribute(ItemStack weaponStack, ItemStack materialStack) {
        if (!ItemModule.hasBase(weaponStack) && weaponStack.getCount() == 1) {
            try {
                ItemStack newWeapon = weaponStack.copy();
                ItemModule.setBaseAttribute(newWeapon);

                // 消耗1个裂罅碎块 / Consume 1 Riven Sliver
                ItemStack newMaterial = materialStack.copy();
                newMaterial.setCount(materialStack.getCount() - 1);

                baseAttributeJustProcessed = true;

                // 立即设置结果并清空输入 / Set result and clear input immediately
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
     * 结果物品被玩家取走时的回调
     * 根据当前模式清理对应的输入槽位
     *
     * 各模式行为：
     * - evolveMode：清空武器槽和材料槽（两把武器都被消耗）
     * - cycleMode：清空武器槽（旧裂罅被消耗，赤毒已在处理时扣除）
     * - baseAttributeJustProcessed（开光/Forma）：无需额外处理（武器槽已在处理时清空，材料已消耗）
     */
    public void onResultTaken() {
        // 武器融合：消耗两把武器 / Weapon evolve: consume both weapons
        if (evolveMode) {
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            materialHandler.setStackInSlot(0, ItemStack.EMPTY);
            evolveMode = false;
            LogUtil.debugEvent("安魂之融", "融合结果被取走", "已清空武器和材料槽");
        }

        // 裂罅循环：消耗旧裂罅 / Riven cycle: consume old riven
        if (cycleMode) {
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            cycleMode = false;
            LogUtil.debugEvent("安魂之融", "裂罅循环结果被取走", "已清空武器槽");
        }

        // 开光/Forma：武器槽和材料已在处理时完成消耗，仅重置标记
        // Opening/Forma: weapon slot and material already consumed during processing, just reset flag
        if (baseAttributeJustProcessed) {
            baseAttributeJustProcessed = false;
        }
    }

    /**
     * 菜单关闭时返还物品
     *
     * ⭐ 关键安全逻辑：
     * - 武器槽：总是返还（若还有物品则返还原始物品）
     * - 材料槽：总是返还（若还有剩余材料则返还）
     * - 结果槽：
     *   · evolve/cycle模式 → 结果是预览品，丢弃不返还（防止物品复制）
     *   · baseAttribute模式（含开光和Forma）→ 武器槽已清空，结果是唯一副本，必须返还
     *   · 其他情况 → 正常返还
     *
     * @param player 操作的玩家
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            // 武器槽：总是返还 / Weapon slot: always return
            returnSlotToPlayer(weaponHandler, 0, player);

            // 材料槽：总是返还 / Material slot: always return
            returnSlotToPlayer(materialHandler, 0, player);

            // 结果槽：根据模式决定是否返还 / Result slot: based on mode
            ItemStack resultStack = resultHandler.getStackInSlot(0);
            if (!resultStack.isEmpty()) {
                if (evolveMode || cycleMode) {
                    // ⭐ 预览模式：结果是系统生成的预览品，关闭时丢弃
                    // ⭐ Preview mode: result is a generated preview, discard on close
                    resultHandler.setStackInSlot(0, ItemStack.EMPTY);
                    LogUtil.debugEvent("安魂之融菜单关闭", "丢弃未取走的预览结果",
                            resultStack.getHoverName().getString());
                } else {
                    // 开光/Forma模式或其他：结果是唯一副本，必须返还
                    // Opening/Forma mode or other: result is the only copy, must return
                    returnSlotToPlayer(resultHandler, 0, player);
                }
            }

            // 重置所有状态标记 / Reset all state flags
            evolveMode = false;
            cycleMode = false;
            baseAttributeJustProcessed = false;
        }
    }

    /**
     * 将指定槽位的物品返还给玩家
     * 优先放入背包，背包满时掉落到方块位置
     *
     * @param handler 物品处理器
     * @param slot    槽位索引
     * @param player  目标玩家
     */
    private void returnSlotToPlayer(ItemStackHandler handler, int slot, Player player) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            // 尝试放入玩家背包 / Try to place in player inventory
            if (!this.moveItemStackTo(stack, 3, 39, true)) {
                // 背包满，掉落到世界 / Inventory full, drop to world
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy()));
            }
            // 清空槽位 / Clear slot
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    /**
     * 检查玩家是否仍可与菜单交互
     * 距离限制为8格（64 = 8²）
     *
     * @param player 玩家
     * @return 是否可交互
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    // ============================== 内部槽位类 / Inner Slot Classes ==============================

    /**
     * 武器槽位（左侧）
     *
     * 接受规则：
     * - 赤毒武器（用于融合）
     * - 武器裂罅模组 / 战甲裂罅模组（用于循环）
     * - 未开光的单个物品（用于开光）
     * - 已开光的单个非赤毒武器（用于Forma洗面板，含已锁定的——放入后不会触发Forma，只是不拒绝放入）
     */
    public static class WeaponSlot extends SlotItemHandler {

        /**
         * @param itemHandler 武器物品处理器
         * @param index       槽位索引
         * @param xPosition   GUI X坐标
         * @param yPosition   GUI Y坐标
         */
        public WeaponSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        /**
         * 检查物品是否可以放入武器槽
         *
         * @param stack 要放入的物品
         * @return 是否允许放入
         */
        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            // 赤毒武器（融合用）/ Kuva weapon (for evolve)
            if (KuvaWeapon.hasType(stack)) {
                return super.mayPlace(stack);
            }
            // 裂罅模组（循环用）/ Riven module (for cycle)
            if (stack.getItem() instanceof ItemRivenModule || stack.getItem() instanceof WarframeRivenModule) {
                return super.mayPlace(stack);
            }
            // 未开光物品（开光用）/ Unopened item (for base attribute)
            if (!ItemModule.hasBase(stack) && stack.getCount() == 1) {
                return super.mayPlace(stack);
            }
            // 已开光的非赤毒武器（Forma洗面板用，允许已锁定物品放入，锁定检查在processCrafting中做）
            // Opened non-Kuva weapon (for Forma re-roll, locked items allowed in slot, lock check in processCrafting)
            if (ItemModule.hasBase(stack) && stack.getCount() == 1) {
                return super.mayPlace(stack);
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
     */
    public static class MaterialSlot extends SlotItemHandler {

        /**
         * @param itemHandler 材料物品处理器
         * @param index       槽位索引
         * @param xPosition   GUI X坐标
         * @param yPosition   GUI Y坐标
         */
        public MaterialSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        /**
         * 检查物品是否可以放入材料槽
         *
         * @param stack 要放入的物品
         * @return 是否允许放入
         */
        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            // 赤毒武器（融合材料）/ Kuva weapon (evolve material)
            if (KuvaWeapon.hasType(stack)) {
                return super.mayPlace(stack);
            }
            // 赤毒（裂罅循环消耗品）/ Kuva (Riven cycle consumable)
            if (stack.getItem() instanceof Kuva) {
                return super.mayPlace(stack);
            }
            // 裂罅碎块（开光消耗品）/ Riven Sliver (base attribute consumable)
            if (stack.getItem() instanceof RivenSliver) {
                return super.mayPlace(stack);
            }
            // 塑形块（Forma洗面板消耗品）/ Forma (re-roll consumable)
            if (stack.getItem() instanceof Forma) {
                return super.mayPlace(stack);
            }
            return false;
        }
    }

    /**
     * 结果槽位（右侧，只读）
     *
     * 不允许手动放入物品，取走时触发onResultTaken清理逻辑
     *
     * 支持普通点击和Shift+点击两种取出方式：
     * - 普通点击：Minecraft框架调用 onTake → onResultTaken
     * - Shift+点击：quickMoveStack完成转移后，框架调用 onTake → onResultTaken
     */
    public static class ResultSlot extends SlotItemHandler {
        /** 所属菜单引用 / Reference to parent menu */
        private final MenuRequiemEvolve menu;

        /**
         * @param menu        所属的安魂之融菜单
         * @param itemHandler 结果物品处理器
         * @param index       槽位索引
         * @param xPosition   GUI X坐标
         * @param yPosition   GUI Y坐标
         */
        public ResultSlot(MenuRequiemEvolve menu, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        /**
         * 结果槽不允许手动放入物品
         *
         * @param stack 要放入的物品
         * @return 始终返回false
         */
        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return false;
        }

        /**
         * 物品被取走时的回调
         * 触发菜单的清理逻辑（清空输入槽、重置模式标记）
         *
         * @param player 取走物品的玩家
         * @param stack  被取走的物品
         */
        @Override
        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            menu.onResultTaken();
            super.onTake(player, stack);
        }
    }
}