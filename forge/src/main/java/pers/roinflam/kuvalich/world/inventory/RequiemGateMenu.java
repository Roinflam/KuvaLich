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
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.InventoryUtil;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;

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
 * 2. 允许玩家放置3张安魂卡进行解密（同一种卡只能放一张，见 {@link CardSlot#mayPlace}）
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

    /** 正在把玩家数据摆进卡槽，这期间卡槽的变化不回写（见 {@link #loadCardsFromCapability}） */
    private boolean loading;

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
     *
     * <p>三张先一起读出来，摆进卡槽时不回写。卡槽一变就回写玩家数据（{@link #mirrorCardsToCapability}），
     * 以前逐格 insertItem：摆第 1 格触发的回写拿还空着的第 2、3 格把玩家数据里的那两张清成了空，
     * 接着读第 2、3 格读到的已经是空的——第 1 格有卡时关了扉再开，第 2、3 格的卡就没了，哪儿也找不回来。</p>
     *
     * <p>摆之前先把存档里同一种卡多出来的退还玩家（{@link #returnDuplicateCards}）。</p>
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
                    returnDuplicateCards(player);
                    ItemStack[] saved = {requiemCard.getOneCard().copy(), requiemCard.getTwoCard().copy(),
                            requiemCard.getThreeCard().copy()};
                    loading = true;
                    try {
                        for (int i = 0; i < 3; i++) {
                            cardHandler.setStackInSlot(i, saved[i]);
                        }
                    } finally {
                        loading = false;
                    }

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
        if (level.isClientSide || closed || loading) {
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

        // 只存一次：存完卡槽的数量已经清零，同一个界面再关一次就会拿空卡槽把玩家数据里的卡全清掉
        if (!level.isClientSide && !closed) {
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
            // 兜底：卡槽本身不收同一种的第二张（含 Oull），万一还是多出来了（别的模组往里塞），留最前面那张，多的退回背包
            for (int i = 1; i < 3; i++) {
                int id = RequiemCard.placedCardId(cardHandler.getStackInSlot(i));
                for (int j = 0; id >= 0 && j < i; j++) {
                    if (RequiemCard.placedCardId(cardHandler.getStackInSlot(j)) == id) {
                        InventoryUtil.giveOrDrop(player, cardHandler.getStackInSlot(i).copy());
                        cardHandler.setStackInSlot(i, ItemStack.EMPTY);
                        break;
                    }
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
        // 处决中（含结束后那一小段余量）卡槽不许动：处决开始时就按当时放的卡定了结果，中途把卡拿走，成功时就扣不到卡。
        // 正常客户端开着界面按不出处决键，这里挡的是改过的客户端。失效的界面原版不处理点击，下一 tick 关掉（关时照常存卡）
        if (player instanceof ServerPlayer serverPlayer && MercyServer.isBusy(serverPlayer)) {
            return false;
        }
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

        /**
         * 只收安魂卡；同一种卡（Oull 也一样）同时只能装一张，别的格子已经有同种的时不收。
         * 光标放、右键放、拖放、Shift 快速移动、数字键 / 副手键换位都走这里；同一格换一张同种的不算第二张
         */
        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            if (!(stack.getItem() instanceof AbstractRequiemCard) || !super.mayPlace(stack)) {
                return false;
            }
            return !hasSameCardElsewhere(getItemHandler(), getSlotIndex(), stack);
        }
    }

    /** 卡槽里除了 {@code except} 这一格，别的格子有没有和 {@code stack} 同一种的卡 */
    static boolean hasSameCardElsewhere(IItemHandler handler, int except, ItemStack stack) {
        int id = RequiemCard.placedCardId(stack);
        if (id < 0) {
            return false;
        }
        for (int i = 0; i < Math.min(3, handler.getSlots()); i++) {
            if (i != except && RequiemCard.placedCardId(handler.getStackInSlot(i)) == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * 玩家数据里同一种卡多出来的（不许放重复卡之前放进去、存在档里的）退还玩家：放回背包，满了掉在脚下，提示一句。
     * 登录和开扉时各查一次，没有重复的什么都不做
     *
     * @return 退还了几张
     */
    public static int returnDuplicateCards(Player player) {
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
        if (requiemCard == null) {
            return 0;
        }
        List<ItemStack> extra = requiemCard.takeDuplicateCards();
        if (extra.isEmpty()) {
            return 0;
        }
        for (ItemStack stack : extra) {
            InventoryUtil.giveOrDrop(player, stack);
        }
        LogUtil.info("灭骸之扉：" + player.getName().getString() + " 的卡槽里有 " + extra.size() + " 张重复的安魂卡，已退还");
        PlayerFeedback.chat(player, KuvaPalette.WARN, "kuvalich.gate.duplicates_returned", extra.size());
        return extra.size();
    }

    /**
     * 要改玩家数据里的卡之前（安魂成功扣卡）先关掉这个玩家开着的扉。
     *
     * <p>卡槽和玩家数据是两份：扉开着的时候扣了玩家数据里的卡，卡槽里还是扣之前那几张，
     * 下一 tick 的回写就把它们原样写回去——用完的卡又回来了，留下的那几张背包里一份、扉里一份。
     * 先关：关的时候卡槽写回玩家数据，之后不再回写，扣卡照常扣；光标上拿着的卡不在玩家数据里，退回背包，不扣。</p>
     */
    public static void closeBeforeCardsChange(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.containerMenu instanceof RequiemGateMenu) {
            serverPlayer.closeContainer();
        }
    }

    /** 登录时查一次存档里的重复卡。死亡界面上登录的先不动（这时放进背包，重生不保留物品栏就没了），等开扉时再查 */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID)
    public static final class DuplicateCardLoginCheck {

        private DuplicateCardLoginCheck() {
        }

        @SubscribeEvent
        public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && player.isAlive()) {
                returnDuplicateCards(player);
            }
        }
    }
}
