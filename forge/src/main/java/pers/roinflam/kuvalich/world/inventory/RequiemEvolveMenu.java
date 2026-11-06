package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.Endo;
import pers.roinflam.kuvalich.item.Forma;
import pers.roinflam.kuvalich.item.ShadowForma;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.LichReliquary;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.packet.RivenCyclePacket;
import pers.roinflam.kuvalich.network.packet.RivenCycleStatePacket;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.InventoryUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 安魂之融菜单（1.20.1版本）
 * Requiem Evolve Menu
 *
 * 支持功能：
 * 1. 武器融合（两把赤毒武器 → 新武器）
 * 2. 武器裂罅循环（裂罅模组 + 赤毒 → 新裂罅）
 * 3. 战甲裂罅循环（战甲裂罅模组 + 赤毒 → 新裂罅）
 * 4. 添加基础属性（未开光物品 + 裂罅碎块 → 开光物品）
 * 5. Forma洗面板（已开光非赤毒未锁定武器 + 塑形块 → 直接消耗并重新随机面板）
 * 6. TACZ枪械强化（TACZ枪械 + 玄骸之遗 → 永久增强基础伤害）
 * 7. 模组升级（模组 + 内融核心 → 预览升级结果，取出时消耗材料）
 *    升级费用根据品质缩放：铜25% / 银50% / 金75% / Prime&裂罅100%
 *
 * <p>裂罅循环不再放进去就自动洗：界面上按「循环」才洗、才扣赤毒（{@link #handleRivenRequest}）。
 * 洗 1 张时新卡照旧出在结果格里当预览（拿走或按「换上」算换，再按一次「再循环」这张作废）；
 * 洗 N 张（2~10）时候选只放在本菜单里，发给客户端只用来画，挑一张换进物品格、或者都不要。
 * 价格是连着洗 N 次的总价（每次按当时的循环次数算），原卡循环次数 +N，候选都记成洗完以后的次数。
 * 赤毒先扣材料格、不够再扣背包，扣完从背包把材料格补满。挑的时候物品格、材料格锁住；
 * 关界面 = 候选作废、原卡退回、赤毒不退。</p>
 */
public class RequiemEvolveMenu extends AbstractContainerMenu {

    /** 一次最多洗几张 */
    public static final int RIVEN_MAX_ROLL = 10;
    /** 算价时倾向的上限（正常卡 1～5），挡住改坏的数据把价格算溢出 */
    private static final int RIVEN_TREND_CAP = 100;
    /** 裂罅循环请求的结果码（随状态包发回客户端） */
    public static final byte RIVEN_OK = 0;
    /** 序号对不上（重复点、同一 tick 第二个挑选）或者现在不是能做这件事的状态 */
    public static final byte RIVEN_STALE = 1;
    /** 赤毒（材料格 + 背包）不够 */
    public static final byte RIVEN_KUVA_SHORT = 2;
    /** 这一类裂罅在配置里停用了，洗不出新卡（不扣赤毒） */
    public static final byte RIVEN_DISABLED = 3;
    /** 格子里的东西不对（不是已揭示的裂罅、材料不是赤毒、结果格里还有别的东西） */
    public static final byte RIVEN_INVALID = 4;

    private final Level level;
    private final BlockPos pos;
    private final Player player;

    private final ItemStackHandler weaponHandler;
    private final ItemStackHandler materialHandler;
    private final ItemStackHandler resultHandler;

    /** 武器融合预览模式 */
    private boolean evolveMode = false;
    /** 裂罅循环预览模式 */
    private boolean cycleMode = false;
    /** 模组升级预览模式 */
    private boolean upgradeMode = false;
    /** 基础属性刚处理完标记（开光/Forma/TACZ强化，立即消耗型） */
    private boolean baseAttributeJustProcessed = false;

    private ItemStack lastWeapon = ItemStack.EMPTY;
    private ItemStack lastMaterial = ItemStack.EMPTY;

    /**
     * 多张洗的候选（服务端是唯一的一份；客户端是状态包发来的副本，只用来画和判断锁格）。
     * 非空 = 正在挑选，物品格、材料格锁住
     */
    private final List<ItemStack> rivenCandidates = new ArrayList<>();
    /** 洗完那一刻物品格里的原卡（挑选时核对它还在） */
    private ItemStack rivenOriginal = ItemStack.EMPTY;
    /**
     * 单张洗的预览是从哪张原卡洗出来的（洗完那一刻物品格里的样子）。拿走结果格、按「换上」前都核对物品格还是它：
     * 平常每次点击后的同步就会把对不上的预览清掉，这里再挡一道，防的是别的东西在同一 tick 里绕过格子检查直接挪走原卡
     */
    private ItemStack rivenPreviewOf = ItemStack.EMPTY;
    /** 状态序号：每洗一次、挑完、放弃、换上、关界面都 +1，请求带的序号对不上就作废 */
    private int rivenSerial;

    // ---- 只在客户端用 ----
    /** 收到第几个状态包了（界面靠它发现有新状态），最近一个包的扣费、张数、结果码 */
    private int clientRivenVersion;
    private int clientRivenPaid;
    private int clientRivenRolled;
    private byte clientRivenReason;
    /** 界面演出期间不让原版画（也不能点）的格子：物品格、结果格、玩家背包 */
    public boolean clientHideInput;
    public boolean clientHideResult;
    public boolean clientHideInventory;

    public RequiemEvolveMenu(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_EVOLVE.get(), windowId);
        this.level = level;
        this.pos = pos;
        this.player = playerInventory.player;
        this.weaponHandler = new ItemStackHandler(1);
        this.materialHandler = new ItemStackHandler(1);
        this.resultHandler = new ItemStackHandler(1);

        this.addSlot(new WeaponSlot(this, this.weaponHandler, 0, 31, 32));
        this.addSlot(new MaterialSlot(this, this.materialHandler, 0, 80, 32));
        this.addSlot(new ResultSlot(this, this.resultHandler, 0, 131, 32));

        for (int i = 0; i < 9; i++) { this.addSlot(new InventorySlot(this, playerInventory, i, 8 + 18 * i, 171 - 10)); }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) { this.addSlot(new InventorySlot(this, playerInventory, col + row * 9 + 9, 8 + col * 18, 113 - 10 + row * 18)); }
        }
    }

    // ==================== Shift+点击 ====================

    /**
     * 快速转移物品（Shift+点击）
     * <p>
     * 修复：材料类物品直接路由到材料槽，跳过武器槽判断。
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        // 挑选裂罅候选时物品格、材料格锁住：Shift 点进点出都不行（moveItemStackTo 只看 mayPlace，这里整个拦掉）
        if (isPickingRiven()) { return ItemStack.EMPTY; }
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) { return itemstack; }
        ItemStack slotStack = slot.getItem();
        itemstack = slotStack.copy();

        // 从容器转移到玩家背包
        if (index < 3) {
            if (!this.moveItemStackTo(slotStack, 3, 39, true)) { return ItemStack.EMPTY; }
        }
        // 从玩家背包转移到容器
        else {
            boolean transferred = false;

            if (isMaterialOnly(slotStack)) {
                // 材料类物品直接进材料槽，跳过武器槽
                transferred = this.moveItemStackTo(slotStack, 1, 2, false);
            } else {
                // 其他物品：先尝试武器槽，再尝试材料槽
                if (this.moveItemStackTo(slotStack, 0, 1, false)) { transferred = true; }
                else if (this.moveItemStackTo(slotStack, 1, 2, false)) { transferred = true; }
            }

            if (!transferred) { return ItemStack.EMPTY; }
        }

        if (slotStack.isEmpty()) { slot.set(ItemStack.EMPTY); } else { slot.setChanged(); }
        if (slotStack.getCount() == itemstack.getCount()) { return ItemStack.EMPTY; }
        slot.onTake(player, slotStack);
        return itemstack;
    }

    /**
     * 判断物品是否为纯材料类（只应放入材料槽，不应放入武器槽）
     */
    private static boolean isMaterialOnly(ItemStack stack) {
        return stack.getItem() instanceof Kuva
                || stack.getItem() instanceof Endo
                || stack.getItem() instanceof RivenSliver
                || stack.getItem() instanceof Forma
                || stack.getItem() instanceof LichReliquary;
    }

    // ==================== 同步（三重保险）====================

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide) { checkAndProcessCrafting(); }
        super.broadcastChanges();
    }

    @Override
    public void broadcastFullState() {
        if (!level.isClientSide) { checkAndProcessCrafting(); }
        super.broadcastFullState();
    }

    private void forceContainerSlotSync() {
        if (!(player instanceof ServerPlayer serverPlayer)) { return; }
        int stateId = this.incrementStateId();
        for (int i = 0; i < 3; i++) {
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                    this.containerId, stateId, i, this.slots.get(i).getItem().copy()));
        }
    }

    // ==================== 合成检测 ====================

    private void checkAndProcessCrafting() {
        ItemStack currentWeapon = weaponHandler.getStackInSlot(0);
        ItemStack currentMaterial = materialHandler.getStackInSlot(0);
        ItemStack currentResult = resultHandler.getStackInSlot(0);
        // 挑选中两格都锁着，什么也不处理
        if (isPickingRiven()) {
            rememberInputs();
            return;
        }
        // 裂罅循环的预览已经付过赤毒：原卡还在就一直留着，往材料格添、取赤毒都不动它；原卡拿走或换了才作废（赤毒不退）
        if (cycleMode) {
            if (!currentResult.isEmpty() && ItemStack.matches(currentWeapon, lastWeapon)) {
                lastMaterial = currentMaterial.copy();
                return;
            }
            resultHandler.setStackInSlot(0, ItemStack.EMPTY);
            currentResult = ItemStack.EMPTY;
            cycleMode = false;
        }
        boolean inputChanged = !ItemStack.matches(currentWeapon, lastWeapon) || !ItemStack.matches(currentMaterial, lastMaterial);

        if (inputChanged && !currentResult.isEmpty() && (evolveMode || cycleMode || upgradeMode)) {
            resultHandler.setStackInSlot(0, ItemStack.EMPTY);
            currentResult = ItemStack.EMPTY;
            evolveMode = false;
            cycleMode = false;
            upgradeMode = false;
        }

        if (currentWeapon.isEmpty() || currentMaterial.isEmpty()) {
            if (!currentResult.isEmpty()) {
                if (!baseAttributeJustProcessed) { resultHandler.setStackInSlot(0, ItemStack.EMPTY); evolveMode = false; cycleMode = false; upgradeMode = false; }
            }
            if (currentResult.isEmpty() && baseAttributeJustProcessed) { baseAttributeJustProcessed = false; }
            lastWeapon = weaponHandler.getStackInSlot(0).copy();
            lastMaterial = materialHandler.getStackInSlot(0).copy();
            return;
        }

        if (inputChanged && currentResult.isEmpty()) {
            if (baseAttributeJustProcessed) { baseAttributeJustProcessed = false; }
            processCrafting(currentWeapon, currentMaterial);
            lastWeapon = weaponHandler.getStackInSlot(0).copy();
            lastMaterial = materialHandler.getStackInSlot(0).copy();
            forceContainerSlotSync();
        }
    }

    private void processCrafting(ItemStack weaponStack, ItemStack materialStack) {
        if (KuvaWeaponUtil.hasType(weaponStack) && KuvaWeaponUtil.hasType(materialStack)) {
            processWeaponEvolve(weaponStack, materialStack); return;
        }
        // 裂罅 + 赤毒：不自动洗，等界面上按「循环」（handleRivenRequest）
        if ((weaponStack.getItem() instanceof WeaponRivenModule || weaponStack.getItem() instanceof WarframeRivenModule)
                && materialStack.getItem() instanceof Kuva) {
            return;
        }
        // 暗影塑形块必须排在普通 Forma 之前：它也是 Forma 子类，未锁定时会被下一条分支截走并掷锁定
        if (materialStack.getItem() instanceof ShadowForma && WeaponModuleHandler.hasBase(weaponStack)
                && !KuvaWeaponUtil.hasType(weaponStack) && weaponStack.getCount() == 1) {
            processShadowFormaReroll(weaponStack, materialStack); return;
        }
        if (materialStack.getItem() instanceof Forma && WeaponModuleHandler.hasBase(weaponStack)
                && !KuvaWeaponUtil.hasType(weaponStack) && !WeaponModuleHandler.isFormaLocked(weaponStack)
                && weaponStack.getCount() == 1) {
            processFormaReroll(weaponStack, materialStack); return;
        }
        if (!weaponStack.isEmpty() && materialStack.getItem() instanceof RivenSliver) {
            processAddBaseAttribute(weaponStack, materialStack); return;
        }
        if (TaczGunEnhanceUtil.isLichReliquary(materialStack) && TaczGunEnhanceUtil.isEnhanceableTaczGun(weaponStack)
                && weaponStack.getCount() == 1) {
            processGunEnhance(weaponStack, materialStack); return;
        }
        if (ModuleLevelHelper.isLevelSystemEnabled()
                && materialStack.getItem() instanceof Endo
                && (weaponStack.getItem() instanceof AbstractModule)
                && !AbstractModule.isRandom(weaponStack)
                && !ModuleLevelHelper.isMaxLevel(weaponStack)
                && weaponStack.getCount() == 1) {
            processModuleUpgrade(weaponStack, materialStack);
            return;
        }
    }

    // ==================== 合成处理方法 ====================

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
        } catch (Exception e) { LogUtil.error("武器融合失败", e); }
    }

    // ==================== 裂罅循环 ====================

    /** 物品格里能洗的裂罅：已揭示、单张 */
    public static boolean isCyclableRiven(ItemStack s) {
        Item it = s.getItem();
        return (it instanceof WeaponRivenModule || it instanceof WarframeRivenModule)
                && !AbstractModule.isRandom(s) && s.getCount() == 1;
    }

    /** 这张裂罅已经循环过几次 */
    public static int rivenCycleOf(ItemStack riven) {
        return riven.getItem() instanceof WarframeRivenModule ? WarframeRivenModule.getCycle(riven) : WeaponRivenModule.getCycle(riven);
    }

    /**
     * 循环过 {@code cycle} 次的这张裂罅再洗一次要多少赤毒：
     * 武器 min(cycle, 8) + trend² − (trend − 1)²，战甲 min(cycle, 8) + trend²（和卡面上「循环所需」一致）
     */
    public static int rivenCycleCost(ItemStack riven, int cycle) {
        // 倾向、次数都来自物品数据：指令给的空白卡倾向是 0（算出 -1，扣钱反而多出一个），改坏的数据可能是负数或大到溢出，
        // 所以两头都夹住，单次至少 1 个
        int base = Mth.clamp(cycle, 0, 8);
        if (riven.getItem() instanceof WarframeRivenModule) {
            int trend = Mth.clamp(WarframeRivenModule.getTrend(riven), 1, RIVEN_TREND_CAP);
            return Math.max(1, base + trend * trend);
        }
        int trend = Mth.clamp(WeaponRivenModule.getTrend(riven), 1, RIVEN_TREND_CAP);
        return Math.max(1, base + 2 * trend - 1);
    }

    /** 连着洗 {@code n} 次的总价：每次按当时的循环次数算 */
    public static int rivenCyclePrice(ItemStack riven, int n) {
        int cycle = rivenCycleOf(riven);
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += rivenCycleCost(riven, cycle + i);
        }
        return sum;
    }

    /** 正在挑多张洗的候选（物品格、材料格锁住） */
    public boolean isPickingRiven() {
        return !rivenCandidates.isEmpty();
    }

    /** 候选（客户端是副本，只读） */
    public List<ItemStack> rivenCandidates() {
        return Collections.unmodifiableList(rivenCandidates);
    }

    public int rivenSerial() {
        return rivenSerial;
    }

    /** 结果格里现在是不是一张付过赤毒的裂罅预览 */
    public boolean hasRivenPreview() {
        return cycleMode && !resultHandler.getStackInSlot(0).isEmpty();
    }

    public int clientRivenVersion() {
        return clientRivenVersion;
    }

    public int clientRivenPaid() {
        return clientRivenPaid;
    }

    public int clientRivenRolled() {
        return clientRivenRolled;
    }

    public byte clientRivenReason() {
        return clientRivenReason;
    }

    /** 客户端收到状态包 */
    public void acceptRivenState(int serial, List<ItemStack> candidates, int paid, int rolled, byte reason) {
        if (!level.isClientSide) {
            return;
        }
        rivenSerial = serial;
        rivenCandidates.clear();
        rivenCandidates.addAll(candidates);
        clientRivenPaid = paid;
        clientRivenRolled = rolled;
        clientRivenReason = reason;
        clientRivenVersion++;
    }

    /**
     * 处理界面发来的裂罅循环请求（服务端；调用方已核对窗口号和距离）。
     * 不管成没成都回一个状态包：成了带新序号和候选，没成带原序号和原因，客户端据此收回提前放的演出
     */
    public void handleRivenRequest(ServerPlayer sp, byte action, int serial, int arg) {
        if (level.isClientSide || sp.containerMenu != this) {
            return;
        }
        if (serial != rivenSerial) {
            sendRivenState(sp, 0, 0, RIVEN_STALE);
            return;
        }
        switch (action) {
            case RivenCyclePacket.ROLL -> rollRiven(sp, arg);
            case RivenCyclePacket.PICK -> pickRiven(sp, arg);
            case RivenCyclePacket.DISMISS -> dismissRiven(sp);
            case RivenCyclePacket.SWAP -> swapRivenPreview(sp);
            default -> sendRivenState(sp, 0, 0, RIVEN_STALE);
        }
    }

    /**
     * 循环：先把 N 张都生成出来（生成不了就什么都不扣），再扣赤毒、原卡循环次数 +N。
     * 1 张：放进结果格当预览（顶掉手上那张预览）；多张：候选留在菜单里等挑
     */
    private void rollRiven(ServerPlayer sp, int n) {
        ItemStack riven = weaponHandler.getStackInSlot(0);
        ItemStack kuva = materialHandler.getStackInSlot(0);
        if (n < 1 || n > RIVEN_MAX_ROLL || isPickingRiven()) {
            sendRivenState(sp, 0, 0, RIVEN_STALE);
            return;
        }
        // 结果格里只能是空的、或者上一张裂罅预览（别的操作刚做好还没拿走的东西不能被顶掉）
        if (!isCyclableRiven(riven) || kuva.isEmpty() || !(kuva.getItem() instanceof Kuva)
                || (!resultHandler.getStackInSlot(0).isEmpty() && !cycleMode)) {
            sendRivenState(sp, 0, 0, RIVEN_INVALID);
            return;
        }
        int price = rivenCyclePrice(riven, n);
        if (kuva.getCount() + countLike(sp, kuva) < price) {
            sendRivenState(sp, 0, 0, RIVEN_KUVA_SHORT);
            return;
        }
        boolean warframe = riven.getItem() instanceof WarframeRivenModule;
        int before = rivenCycleOf(riven);
        int after = before + n;
        List<ItemStack> made = new ArrayList<>(n);
        try {
            int trend = warframe ? WarframeRivenModule.getTrend(riven) : WeaponRivenModule.getTrend(riven);
            int mode = warframe ? 0 : WeaponRivenModule.getRivenMode(riven);
            boolean levels = ModuleLevelHelper.isLevelSystemEnabled();
            int originalLevel = levels ? ModuleLevelHelper.getModuleLevel(riven) : 0;
            for (int i = 0; i < n; i++) {
                // cycleModule 写进新卡的次数是传入值 + 1：传 after − 1，每张候选都是洗完以后的次数
                ItemStack c = warframe ? WarframeRivenModule.cycleModule(trend, after - 1)
                        : WeaponRivenModule.cycleModule(trend, after - 1, mode);
                if (c.isEmpty()) {
                    sendRivenState(sp, 0, 0, RIVEN_DISABLED);
                    return;
                }
                if (levels) {
                    ModuleLevelHelper.setModuleLevel(c, originalLevel);
                }
                made.add(c);
            }
        } catch (Exception e) {
            LogUtil.error(warframe ? "战甲裂罅循环失败" : "武器裂罅循环失败", e);
            sendRivenState(sp, 0, 0, RIVEN_INVALID);
            return;
        }
        payKuva(sp, kuva, price);
        ItemStack updated = riven.copy();
        if (warframe) {
            WarframeRivenModule.setCycle(updated, after);
        } else {
            WeaponRivenModule.setCycle(updated, after);
        }
        weaponHandler.setStackInSlot(0, updated);
        if (n == 1) {
            resultHandler.setStackInSlot(0, made.get(0));
            cycleMode = true;
            rivenPreviewOf = updated.copy();
        } else {
            resultHandler.setStackInSlot(0, ItemStack.EMPTY);
            cycleMode = false;
            rivenCandidates.addAll(made);
            rivenOriginal = updated.copy();
        }
        rivenSerial++;
        rememberInputs();
        broadcastChanges();
        sendRivenState(sp, price, n, RIVEN_OK);
    }

    /** 挑一张换上：原卡销毁、新卡进物品格。物品格里已经不是洗完时那张原卡了就不给（防止拿两张） */
    private void pickRiven(ServerPlayer sp, int index) {
        if (!isPickingRiven() || index < 0 || index >= rivenCandidates.size()) {
            sendRivenState(sp, 0, 0, RIVEN_STALE);
            return;
        }
        ItemStack chosen = rivenCandidates.get(index).copy();
        boolean intact = ItemStack.matches(weaponHandler.getStackInSlot(0), rivenOriginal);
        clearRivenCandidates();
        rivenSerial++;
        if (!intact) {
            LogUtil.warn("裂罅循环挑选时物品格里已经不是原卡，候选作废：" + sp.getName().getString());
            broadcastChanges();
            sendRivenState(sp, 0, 0, RIVEN_INVALID);
            return;
        }
        weaponHandler.setStackInSlot(0, chosen);
        rememberInputs();
        broadcastChanges();
        sendRivenState(sp, 0, 0, RIVEN_OK);
    }

    /** 都不要：候选全部作废，原卡留着，赤毒不退 */
    private void dismissRiven(ServerPlayer sp) {
        if (!isPickingRiven()) {
            sendRivenState(sp, 0, 0, RIVEN_STALE);
            return;
        }
        clearRivenCandidates();
        rivenSerial++;
        broadcastChanges();
        sendRivenState(sp, 0, 0, RIVEN_OK);
    }

    /** 单张的「换上」：结果格里的预览直接进物品格，原卡销毁（和拿走结果格一样，只是卡留在台上接着洗） */
    private void swapRivenPreview(ServerPlayer sp) {
        ItemStack preview = resultHandler.getStackInSlot(0);
        if (isPickingRiven() || !cycleMode || preview.isEmpty() || !isCyclableRiven(weaponHandler.getStackInSlot(0))) {
            sendRivenState(sp, 0, 0, RIVEN_STALE);
            return;
        }
        if (!rivenPreviewIntact()) {
            LogUtil.warn("裂罅循环换上时物品格里已经不是原卡，预览作废：" + sp.getName().getString());
            resultHandler.setStackInSlot(0, ItemStack.EMPTY);
            cycleMode = false;
            rivenPreviewOf = ItemStack.EMPTY;
            rivenSerial++;
            rememberInputs();
            broadcastChanges();
            sendRivenState(sp, 0, 0, RIVEN_INVALID);
            return;
        }
        weaponHandler.setStackInSlot(0, preview.copy());
        resultHandler.setStackInSlot(0, ItemStack.EMPTY);
        cycleMode = false;
        rivenPreviewOf = ItemStack.EMPTY;
        rivenSerial++;
        rememberInputs();
        broadcastChanges();
        sendRivenState(sp, 0, 0, RIVEN_OK);
    }

    private void clearRivenCandidates() {
        rivenCandidates.clear();
        rivenOriginal = ItemStack.EMPTY;
    }

    /** 单张洗的预览还对得上物品格里的原卡（不是预览状态时恒为 true，只在服务端有意义） */
    private boolean rivenPreviewIntact() {
        return !cycleMode || rivenPreviewOf.isEmpty() || ItemStack.matches(weaponHandler.getStackInSlot(0), rivenPreviewOf);
    }

    /** 记下现在两格的东西，下一次 broadcastChanges 不把自己刚做的改动当成「玩家换了输入」 */
    private void rememberInputs() {
        lastWeapon = weaponHandler.getStackInSlot(0).copy();
        lastMaterial = materialHandler.getStackInSlot(0).copy();
    }

    /** 玩家背包 36 格（含快捷栏）里和 {@code like} 同一种、同样数据的东西有几个 */
    private static int countLike(Player p, ItemStack like) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, like)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /**
     * 扣 {@code price} 个赤毒：先扣材料格，不够的从背包扣（从背包最后一格往前，快捷栏最后动），
     * 扣完再从背包把材料格补满（一组）。调用前已核对过总数够
     */
    private void payKuva(Player p, ItemStack kuva, int price) {
        ItemStack like = kuva.copyWithCount(1);
        ItemStack slot = kuva.copy();
        int fromSlot = Math.min(slot.getCount(), price);
        slot.shrink(fromSlot);
        int left = price - fromSlot;
        Inventory inv = p.getInventory();
        for (int i = 35; i >= 0 && left > 0; i--) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, like)) {
                int take = Math.min(s.getCount(), left);
                s.shrink(take);
                left -= take;
            }
        }
        int room = Math.min(like.getMaxStackSize(), materialHandler.getSlotLimit(0)) - slot.getCount();
        for (int i = 35; i >= 0 && room > 0; i--) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, like)) {
                int take = Math.min(s.getCount(), room);
                if (slot.isEmpty()) {
                    slot = like.copyWithCount(take);
                } else {
                    slot.grow(take);
                }
                s.shrink(take);
                room -= take;
            }
        }
        materialHandler.setStackInSlot(0, slot);
        inv.setChanged();
        if (left > 0) {
            LogUtil.warn("裂罅循环扣赤毒时少扣了 " + left + " 个：" + p.getName().getString());
        }
    }

    private void sendRivenState(ServerPlayer sp, int paid, int rolled, byte reason) {
        List<ItemStack> copies = new ArrayList<>(rivenCandidates.size());
        for (ItemStack s : rivenCandidates) {
            copies.add(s.copy());
        }
        NetworkRegistryHandler.getChannel().send(PacketDistributor.PLAYER.with(() -> sp),
                new RivenCycleStatePacket(this.containerId, rivenSerial, copies, paid, rolled, reason));
    }

    private void processFormaReroll(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();
            if (!WeaponModuleHandler.clearBaseAttribute(newWeapon)) { return; }
            WeaponModuleHandler.setBaseAttribute(newWeapon);
            if (ModConfig.KUVA_LICH.formaLockEnabled.get()) {
                double lockChance = ModConfig.KUVA_LICH.formaLockChance.get();
                if (lockChance > 0) {
                    double roll = level.random.nextDouble() * 100.0;
                    if (roll < lockChance) { WeaponModuleHandler.setFormaLocked(newWeapon); }
                }
            }
            ItemStack newMaterial = materialStack.copy();
            newMaterial.shrink(1);
            materialHandler.setStackInSlot(0, newMaterial);
            resultHandler.setStackInSlot(0, newWeapon);
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            baseAttributeJustProcessed = true;
        } catch (Exception e) { LogUtil.error("Forma洗面板失败", e); }
    }

    /** 暗影塑形块：先解锁再洗面板，本次不掷锁定概率 */
    private void processShadowFormaReroll(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newWeapon = weaponStack.copy();
            WeaponModuleHandler.clearFormaLocked(newWeapon);
            if (!WeaponModuleHandler.clearBaseAttribute(newWeapon)) { return; }
            WeaponModuleHandler.setBaseAttribute(newWeapon);
            ItemStack newMaterial = materialStack.copy();
            newMaterial.shrink(1);
            materialHandler.setStackInSlot(0, newMaterial);
            resultHandler.setStackInSlot(0, newWeapon);
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            baseAttributeJustProcessed = true;
        } catch (Exception e) { LogUtil.error("暗影Forma洗面板失败", e); }
    }

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
            } catch (Exception e) { LogUtil.error("添加基础属性失败", e); baseAttributeJustProcessed = false; }
        }
    }

    private void processGunEnhance(ItemStack weaponStack, ItemStack materialStack) {
        try {
            ItemStack newGun = weaponStack.copy();
            int oldCount = TaczGunEnhanceUtil.getEnhanceCount(newGun);
            TaczGunEnhanceUtil.setEnhanceCount(newGun, oldCount + 1);
            ItemStack newMaterial = materialStack.copy();
            newMaterial.shrink(1);
            materialHandler.setStackInSlot(0, newMaterial);
            resultHandler.setStackInSlot(0, newGun);
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            baseAttributeJustProcessed = true;
        } catch (Exception e) { LogUtil.error("TACZ枪械强化失败", e); baseAttributeJustProcessed = false; }
    }

    private void processModuleUpgrade(ItemStack moduleStack, ItemStack endoStack) {
        try {
            int currentLevel = ModuleLevelHelper.getModuleLevel(moduleStack);
            int cost = ModuleLevelHelper.getUpgradeCost(currentLevel, moduleStack);
            if (cost <= 0 || endoStack.getCount() < cost) { return; }

            ItemStack upgradedModule = moduleStack.copy();
            int newLevel = currentLevel + 1;
            ModuleLevelHelper.setModuleLevel(upgradedModule, newLevel);

            resultHandler.setStackInSlot(0, upgradedModule);
            upgradeMode = true;

            LogUtil.debugEvent("模组升级预览", moduleStack.getHoverName().getString(),
                    "等级: " + currentLevel + " → " + newLevel + ", 所需内融核心: " + cost);
        } catch (Exception e) {
            LogUtil.error("模组升级预览失败", e);
        }
    }

    // ==================== 结果取走回调 ====================

    public void onResultTaken() {
        if (evolveMode) {
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            materialHandler.setStackInSlot(0, ItemStack.EMPTY);
            evolveMode = false;
        }
        if (cycleMode) {
            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);
            cycleMode = false;
            rivenPreviewOf = ItemStack.EMPTY;
        }
        if (upgradeMode) {
            ItemStack moduleStack = weaponHandler.getStackInSlot(0);
            ItemStack endoStack = materialHandler.getStackInSlot(0);
            int currentLevel = ModuleLevelHelper.getModuleLevel(moduleStack);
            int cost = ModuleLevelHelper.getUpgradeCost(currentLevel, moduleStack);

            if (cost > 0 && endoStack.getCount() >= cost) {
                endoStack.shrink(cost);
                materialHandler.setStackInSlot(0, endoStack);
            }

            weaponHandler.setStackInSlot(0, ItemStack.EMPTY);

            int newLevel = currentLevel + 1;
            String masteryKey = ModuleLevelHelper.getMasteryKey(moduleStack);
            if (!masteryKey.isEmpty()) {
                player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                    requiemCard.updateMasteryLevel(masteryKey, newLevel);
                });
            }

            LogUtil.debugEvent("模组升级确认", moduleStack.getHoverName().getString(),
                    "等级: " + currentLevel + " → " + newLevel + ", 消耗内融核心: " + cost);
            upgradeMode = false;
        }
        if (baseAttributeJustProcessed) {
            baseAttributeJustProcessed = false;
        }
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (!level.isClientSide) {
            // 多张洗没挑就关界面（含下线）：候选作废、赤毒不退，物品格里的原卡照常退回
            clearRivenCandidates();
            rivenSerial++;
            returnSlotToPlayer(weaponHandler, 0, player);
            returnSlotToPlayer(materialHandler, 0, player);
            ItemStack resultStack = resultHandler.getStackInSlot(0);
            if (!resultStack.isEmpty()) {
                if (evolveMode || cycleMode || upgradeMode) {
                    resultHandler.setStackInSlot(0, ItemStack.EMPTY);
                } else {
                    returnSlotToPlayer(resultHandler, 0, player);
                }
            }
            evolveMode = false;
            cycleMode = false;
            upgradeMode = false;
            baseAttributeJustProcessed = false;
        }
    }

    /**
     * 将槽位中的物品返还给玩家
     * 使用 Inventory.add() 优先填充快捷栏，满了掉脚下
     */
    private void returnSlotToPlayer(ItemStackHandler handler, int slot, Player player) {
        ItemStack stack = handler.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            InventoryUtil.giveOrDrop(player, stack);
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) && player.blockPosition().distSqr(this.pos) <= 64;
    }

    // ============================== 内部槽位类 ==============================

    /** 物品格。挑裂罅候选时锁住（放不进、拿不走）；客户端演出期间可以藏起来不画 */
    public static class WeaponSlot extends SlotItemHandler {
        private final RequiemEvolveMenu menu;

        public WeaponSlot(RequiemEvolveMenu menu, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return !menu.isPickingRiven() && super.mayPickup(player);
        }

        @Override
        public boolean isActive() {
            return !menu.clientHideInput;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (menu.isPickingRiven()) { return false; }
            if (KuvaWeaponUtil.hasType(stack)) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof WeaponRivenModule || stack.getItem() instanceof WarframeRivenModule) { return super.mayPlace(stack); }
            if (!WeaponModuleHandler.hasBase(stack) && stack.getCount() == 1) { return super.mayPlace(stack); }
            if (WeaponModuleHandler.hasBase(stack) && stack.getCount() == 1) { return super.mayPlace(stack); }
            try {
                if (TaczGunEnhanceUtil.isTaczGun(stack) && stack.getCount() == 1) { return super.mayPlace(stack); }
            } catch (NoClassDefFoundError ignored) {}
            if (ModuleLevelHelper.isLevelSystemEnabled()
                    && stack.getItem() instanceof AbstractModule
                    && !AbstractModule.isRandom(stack)
                    && !ModuleLevelHelper.isMaxLevel(stack)
                    && stack.getCount() == 1) {
                return super.mayPlace(stack);
            }
            return false;
        }
    }

    /** 材料格。挑裂罅候选时同样锁住 */
    public static class MaterialSlot extends SlotItemHandler {
        private final RequiemEvolveMenu menu;

        public MaterialSlot(RequiemEvolveMenu menu, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return !menu.isPickingRiven() && super.mayPickup(player);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (menu.isPickingRiven()) { return false; }
            if (KuvaWeaponUtil.hasType(stack)) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof Kuva) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof RivenSliver) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof Forma) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof LichReliquary) { return super.mayPlace(stack); }
            if (stack.getItem() instanceof Endo) { return super.mayPlace(stack); }
            return false;
        }
    }

    public static class ResultSlot extends SlotItemHandler {
        private final RequiemEvolveMenu menu;
        public ResultSlot(RequiemEvolveMenu menu, IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) { return false; }

        /** 服务端：裂罅预览只在洗出它的那张原卡还在物品格里时能拿（客户端不知道原卡是哪张，交给服务端判） */
        @Override
        public boolean mayPickup(@NotNull Player player) {
            if (!menu.level.isClientSide && !menu.rivenPreviewIntact()) {
                return false;
            }
            return super.mayPickup(player);
        }

        @Override
        public boolean isActive() {
            return !menu.clientHideResult;
        }

        @Override
        public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            menu.onResultTaken();
            super.onTake(player, stack);
        }
    }

    /** 玩家背包格：多张洗铺开候选时背包让出位置，客户端这时不画也不能点（服务端照常） */
    public static class InventorySlot extends Slot {
        private final RequiemEvolveMenu menu;

        public InventorySlot(RequiemEvolveMenu menu, Inventory inventory, int index, int xPosition, int yPosition) {
            super(inventory, index, xPosition, yPosition);
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return !menu.clientHideInventory;
        }
    }
}
