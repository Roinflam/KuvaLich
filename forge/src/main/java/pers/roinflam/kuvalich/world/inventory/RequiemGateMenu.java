package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.item.card.OULL;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.InventoryUtil;

/**
 * 灭骸之扉菜单类（1.20.1版本）
 * Requiem Gate Menu (1.20.1 version)
 *
 * 从 1.12.2 版迁移，只换了 API 调用
 * Ported from the 1.12.2 version; only the API calls changed
 *
 * 核心功能：
 * Core functions:
 * 1. 显示当前解密进度和已解锁的卡片
 * 2. 允许玩家放置3张安魂卡进行解密
 * 3. 自动从玩家Capability加载已配置的卡片
 * 4. 卡槽一有变化就写回 Capability（不等关界面，见 {@link #mirrorCardsToCapability}）
 *
 * 新增：autoFillAnswerCards —— 创造模式一键补全正确答案卡片（由 RequiemGateFillPacket 调用）
 */
public class RequiemGateMenu extends AbstractContainerMenu {

    private final Player player;
    private final Level level;
    private final BlockPos pos;

    /** 卡片槽位处理器（3个可放置槽位）/ Card slot handler (3 slots) */
    private final ItemStackHandler cardHandler;

    /** 同步数据容器 / Synchronized data container */
    private final ContainerData data;

    /** removed() 已经把卡存走、清了数量，之后不再往玩家数据里写 */
    private boolean closed;

    /** 数据索引常量 / Data index constants */
    private static final int DATA_ONE_RIDDLE = 0;
    private static final int DATA_TWO_RIDDLE = 1;
    private static final int DATA_THREE_RIDDLE = 2;
    private static final int DATA_LEVEL = 3;
    private static final int DATA_SCHEDULE = 4;
    private static final int DATA_COUNT = 5;

    /**
     * 服务端构造（从方块打开）
     * Server-side constructor (opened from block)
     */
    public RequiemGateMenu(int windowId, Inventory playerInventory, Level level, BlockPos pos) {
        super(KuvaLichMenuTypes.REQUIEM_GATE.get(), windowId);

        this.player = playerInventory.player;
        this.level = level;
        this.pos = pos;
        this.cardHandler = new ItemStackHandler(3) {
            @Override
            protected void onContentsChanged(int slot) {
                mirrorCardsToCapability();
            }
        };
        this.data = new SimpleContainerData(DATA_COUNT);

        // 添加卡片槽位（可放置）/ Add card slots (placeable)
        this.addSlot(new CardSlot(this.cardHandler, 0, 26, 11));
        this.addSlot(new CardSlot(this.cardHandler, 1, 80, 11));
        this.addSlot(new CardSlot(this.cardHandler, 2, 134, 11));

        // 添加玩家背包槽位 / Add player inventory slots
        // 快捷栏 / Hotbar
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + 18 * i, 152 - 10));
        }
        // 主背包 / Main inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 94 - 10 + row * 18));
            }
        }

        // 从玩家Capability加载已配置的卡片
        // Load configured cards from player Capability
        if (!level.isClientSide) {
            loadCardsFromCapability();
        }

        // 添加数据同步 / Add data synchronization
        this.addDataSlots(this.data);
    }

    /**
     * 从Capability加载卡片
     * Load cards from Capability
     */
    private void loadCardsFromCapability() {
        try {
            player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                ItemStack oneCard = cardHandler.getStackInSlot(0);
                ItemStack twoCard = cardHandler.getStackInSlot(1);
                ItemStack threeCard = cardHandler.getStackInSlot(2);

                // 只有当槽位为空时才加载 / Only load if slots are empty
                // 放副本：卡槽和玩家数据各管各的对象，拿出来的那张不会还挂在玩家数据上
                if (oneCard.isEmpty() && twoCard.isEmpty() && threeCard.isEmpty()) {
                    cardHandler.insertItem(0, requiemCard.getOneCard().copy(), false);
                    cardHandler.insertItem(1, requiemCard.getTwoCard().copy(), false);
                    cardHandler.insertItem(2, requiemCard.getThreeCard().copy(), false);

                    LogUtil.debugEvent("灭骸之扉菜单创建", player.getName().getString(),
                            "已加载玩家的卡片配置");
                }
            });
        } catch (Exception e) {
            LogUtil.error("加载玩家卡片配置时发生错误", e);
        }
    }

    /**
     * 同步数据到客户端
     * Sync data to client
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!level.isClientSide) {
            // 槽位里的物品被原地改了数量 / NBT 时不会触发 onContentsChanged，每 tick 再对一遍
            mirrorCardsToCapability();
            try {
                player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                    // 同步谜语卡片ID / Sync riddle card IDs
                    data.set(DATA_ONE_RIDDLE, requiemCard.getOneRiddle());
                    data.set(DATA_TWO_RIDDLE, requiemCard.getTwoRiddle());
                    data.set(DATA_THREE_RIDDLE, requiemCard.getThreeRiddle());
                    // 同步解密等级 / Sync decryption level
                    data.set(DATA_LEVEL, requiemCard.getUnlockedCardStatus());
                    // 同步解密进度 / Sync decryption progress
                    data.set(DATA_SCHEDULE, requiemCard.getDecryptionProgress());
                });
            } catch (Exception e) {
                LogUtil.error("同步解密数据到客户端时发生错误", e);
            }
        }
    }

    /**
     * 卡槽写回玩家数据（只在服务端；和现有的不一样才写）
     *
     * <p>以前只在关界面时写回，可原版下线时是先存档、后关界面（{@code PlayerList#remove}），
     * 开着界面下线，刚放进去的卡写在存档之后就丢了；从卡槽拿出来的那张，玩家数据里却还留着，等于多了一张。
     * 现在放进 / 拿走当下就写，玩家数据始终和卡槽一致；下线时由 {@link RequiemMenuLogoutGuard} 在存档前关界面，
     * 两头都兜住。不是安魂卡的东西不写进去（卡槽本来也不收），关界面时照旧退回。</p>
     */
    private void mirrorCardsToCapability() {
        if (level.isClientSide || closed) {
            return;
        }
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            ItemStack[] saved = {requiemCard.getOneCard(), requiemCard.getTwoCard(), requiemCard.getThreeCard()};
            for (int i = 0; i < 3; i++) {
                ItemStack inSlot = cardHandler.getStackInSlot(i);
                ItemStack card = inSlot.getItem() instanceof AbstractRequiemCard ? inSlot : ItemStack.EMPTY;
                if (ItemStack.matches(saved[i], card)) {
                    continue;
                }
                switch (i) {
                    case 0 -> requiemCard.setOneCard(card.copy());
                    case 1 -> requiemCard.setTwoCard(card.copy());
                    default -> requiemCard.setThreeCard(card.copy());
                }
            }
        });
    }

    /**
     * 快速转移物品（Shift+点击）
     * Quick move item (Shift+Click)
     */
    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            // 从容器转移到背包 / From container to inventory
            if (index < 3) {
                if (!this.moveItemStackTo(slotStack, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // 从背包转移到容器 / From inventory to container
            else {
                if (!this.moveItemStackTo(slotStack, 0, 3, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    /**
     * 容器关闭时的处理
     * Handle container close
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!level.isClientSide) {
            saveCardsToCapability(player);
            closed = true;
        }
    }

    /**
     * 保存卡片到Capability
     * Save cards to Capability
     */
    private void saveCardsToCapability(Player player) {
        try {
            // 兜底：卡槽本身不收第二张 Oull，万一还是多出来了（别的模组往里塞），多的退回背包
            boolean oullSeen = false;
            for (int i = 0; i < 3; i++) {
                if (OULL.is(cardHandler.getStackInSlot(i))) {
                    if (oullSeen) {
                        InventoryUtil.giveOrDrop(player, cardHandler.getStackInSlot(i).copy());
                        cardHandler.setStackInSlot(i, ItemStack.EMPTY);
                    }
                    oullSeen = true;
                }
            }
            ItemStack oneCard = cardHandler.getStackInSlot(0);
            ItemStack twoCard = cardHandler.getStackInSlot(1);
            ItemStack threeCard = cardHandler.getStackInSlot(2);

            player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                BlockPos blockPos = player.blockPosition();

                // 处理第一张卡片 / Handle first card
                if (!oneCard.isEmpty() && !(oneCard.getItem() instanceof AbstractRequiemCard)) {
                    LogUtil.warn("检测到非法物品在卡片槽位0: " + oneCard.getHoverName().getString() + " - 正在返还");
                    level.addFreshEntity(new ItemEntity(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), oneCard.copy()));
                } else {
                    requiemCard.setOneCard(oneCard.copy());
                }

                // 处理第二张卡片 / Handle second card
                if (!twoCard.isEmpty() && !(twoCard.getItem() instanceof AbstractRequiemCard)) {
                    LogUtil.warn("检测到非法物品在卡片槽位1: " + twoCard.getHoverName().getString() + " - 正在返还");
                    level.addFreshEntity(new ItemEntity(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), twoCard.copy()));
                } else {
                    requiemCard.setTwoCard(twoCard.copy());
                }

                // 处理第三张卡片 / Handle third card
                if (!threeCard.isEmpty() && !(threeCard.getItem() instanceof AbstractRequiemCard)) {
                    LogUtil.warn("检测到非法物品在卡片槽位2: " + threeCard.getHoverName().getString() + " - 正在返还");
                    level.addFreshEntity(new ItemEntity(level, blockPos.getX(), blockPos.getY(), blockPos.getZ(), threeCard.copy()));
                } else {
                    requiemCard.setThreeCard(threeCard.copy());
                }

                // 清空容器中的物品计数（防止重复掉落）
                // Clear item counts in container (prevent duplicate drops)
                oneCard.setCount(0);
                twoCard.setCount(0);
                threeCard.setCount(0);

                LogUtil.debugEvent("灭骸之扉菜单关闭", player.getName().getString(), "卡片配置已保存");
            });
        } catch (Exception e) {
            LogUtil.error("保存卡片配置时发生错误", e);
        }
    }

    /**
     * 一键补全当前玄骸的正确答案卡片（仅服务端调用，创造模式专用）
     * <p>
     * 从玩家 RequiemCard 能力读取 oneAnswer/twoAnswer/threeAnswer（带正确顺序），
     * 创建对应的三张安魂卡按正确顺序填入卡槽，省去手动查看答案并从背包找卡的步骤。
     * 填入当下就写进能力（{@link #mirrorCardsToCapability}），
     * 之后击杀玄骸即可用这些卡自动验证解密。
     * <p>
     * 若答案尚未生成（玩家还未解锁第一阶段，answer 为 -1），静默返回不做任何操作。
     * 填入前会把玩家手动放入的旧卡返还背包（背包满则掉落脚下），避免被覆盖吞掉。
     *
     * @param serverPlayer 触发补全的服务端玩家（已在网络包层校验为创造模式）
     */
    public void autoFillAnswerCards(ServerPlayer serverPlayer) {
        if (level.isClientSide) {
            return;
        }
        serverPlayer.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            int oneAnswer = requiemCard.getOneAnswer();
            int twoAnswer = requiemCard.getTwoAnswer();
            int threeAnswer = requiemCard.getThreeAnswer();

            // 答案尚未生成（未解锁第一阶段），无法补全，静默返回
            if (oneAnswer < 0 || twoAnswer < 0 || threeAnswer < 0) {
                LogUtil.debug("灭骸之扉一键补全：答案尚未生成，已忽略");
                return;
            }

            // 返还玩家手动放入的旧卡，避免被覆盖丢失
            returnExistingCards(serverPlayer);

            // 按正确顺序填入正确答案卡片
            cardHandler.setStackInSlot(0, new ItemStack(AbstractRequiemCard.getCard(oneAnswer)));
            cardHandler.setStackInSlot(1, new ItemStack(AbstractRequiemCard.getCard(twoAnswer)));
            cardHandler.setStackInSlot(2, new ItemStack(AbstractRequiemCard.getCard(threeAnswer)));

            // 立即同步卡槽变更到客户端
            broadcastChanges();

            LogUtil.debugEvent("灭骸之扉一键补全", serverPlayer.getName().getString(),
                    "答案: " + oneAnswer + " / " + twoAnswer + " / " + threeAnswer);
        });
    }

    /**
     * 将卡槽中玩家手动放入的旧卡返还玩家（背包满则掉落脚下），并清空对应卡槽。
     * <p>
     * 供一键补全前调用，避免直接覆盖导致玩家原本放入的卡片凭空消失。
     *
     * @param serverPlayer 接收返还卡片的玩家
     */
    private void returnExistingCards(ServerPlayer serverPlayer) {
        for (int i = 0; i < 3; i++) {
            ItemStack oldCard = cardHandler.getStackInSlot(i);
            if (!oldCard.isEmpty()) {
                InventoryUtil.giveOrDrop(serverPlayer, oldCard.copy());
                cardHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 检查玩家是否可以与菜单交互
     * Check if player can interact with menu
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.level().equals(this.level) &&
                player.blockPosition().distSqr(this.pos) <= 64;
    }

    /**
     * 获取同步数据（供GUI使用）
     * Get synchronized data (for GUI use)
     */
    public int getOneRiddle() {
        return data.get(DATA_ONE_RIDDLE);
    }

    public int getTwoRiddle() {
        return data.get(DATA_TWO_RIDDLE);
    }

    public int getThreeRiddle() {
        return data.get(DATA_THREE_RIDDLE);
    }

    public int getLevel() {
        return data.get(DATA_LEVEL);
    }

    public int getSchedule() {
        return data.get(DATA_SCHEDULE);
    }

    /**
     * 卡片槽位类（只接受安魂卡）
     * Card slot class (only accepts Requiem Cards)
     */
    public static class CardSlot extends SlotItemHandler {
        public CardSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
            super(itemHandler, index, xPosition, yPosition);
        }

        /** 只收安魂卡；Oull 同时只能装一张，别的格子已经有一张时不收（拖放、Shift 快速移动、数字键换位都走这里） */
        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (!(stack.getItem() instanceof AbstractRequiemCard) || !super.mayPlace(stack)) {
                return false;
            }
            return !OULL.is(stack) || !hasOullElsewhere(getItemHandler(), getSlotIndex());
        }
    }

    /** 卡槽里除了 {@code except} 这一格，别的格子有没有 Oull */
    static boolean hasOullElsewhere(IItemHandler handler, int except) {
        for (int i = 0; i < Math.min(3, handler.getSlots()); i++) {
            if (i != except && OULL.is(handler.getStackInSlot(i))) {
                return true;
            }
        }
        return false;
    }
}
