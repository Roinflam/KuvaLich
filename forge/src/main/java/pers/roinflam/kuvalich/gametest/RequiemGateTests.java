package pers.roinflam.kuvalich.gametest;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.card.OULL;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.SummonMode;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemGateMenu;
import pers.roinflam.kuvalich.world.inventory.RequiemMenuLogoutGuard;

/**
 * 灭骸之扉的卡槽：放进 / 拿走 / 换卡 / 关了再开 / 断线重连都不丢卡、不多卡；同一种卡只能放一张，
 * 每个入口（光标点、Shift、数字键、副手键、拖放）都拦；存档里已有的重复卡登录、开扉时退还；
 * 开着扉时安魂成功扣卡，扣掉的卡不会被卡槽写回来；处决中扉里的卡动不了
 *
 * <p>点击一律走 {@link AbstractContainerMenu#clicked}，和客户端发来的点击包在服务端走的是同一段原版代码。</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RequiemGateTests {

    private static final String GATE = "kl_gate_core";
    private static final String ISO_CONSUME = "kl_gate_iso_consumeWhileOpen";
    private static final String ISO_MERCY_LOCK = "kl_gate_iso_lockedDuringMercy";

    /** 菜单里卡槽之后的第一格是快捷栏第 1 格 */
    private static final int HOTBAR_0 = 3;

    private static final int KHRA = 0;
    private static final int JAHU = 1;
    private static final int NETRA = 4;
    private static final int VOME = 5;
    private static final int XATA = 6;

    private RequiemGateTests() {
    }

    // ==================== 三格都放了卡，关了再开，三张都还在 ====================

    @GameTest(template = "empty", batch = GATE)
    public static void gateKeepsAllCardsOnReopen(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = emptyCards(p);
        p.getInventory().clearContent();
        Item[] items = {cardItem(JAHU), cardItem(NETRA), cardItem(XATA)};

        RequiemGateMenu menu = open(p, 1);
        for (int i = 0; i < 3; i++) {
            place(menu, p, i, new ItemStack(items[i]));
            h.assertTrue(menu.getSlot(i).getItem().is(items[i]) && menu.getCarried().isEmpty(), "第 " + (i + 1) + " 格应当放得进");
        }
        assertCards(h, card, items, "放齐三张后");
        menu.removed(p);
        assertCards(h, card, items, "关界面后");
        // 同一个界面被关第二次（别的模组再关一遍之类）：卡槽已经清零，不能拿它去覆盖玩家数据
        menu.removed(p);
        assertCards(h, card, items, "同一个界面关第二次后");

        RequiemGateMenu again = open(p, 2);
        assertSlots(h, again, items, "关了再开");
        assertCards(h, card, items, "重开后");
        again.removed(p);

        // 断线重连：下线时先关界面再存档，存档读回来，再开一次三张都在
        RequiemGateMenu beforeLogout = open(p, 3);
        p.containerMenu = beforeLogout;
        RequiemMenuLogoutGuard.closeRequiemMenuBeforeSave(new PlayerEvent.PlayerLoggedOutEvent(p));
        h.assertTrue(p.containerMenu == p.inventoryMenu, "下线时扉应当先关掉");
        card.deserializeNBT(card.serializeNBT());
        RequiemGateMenu afterLogin = open(p, 4);
        assertSlots(h, afterLogin, items, "断线重连后");
        assertCards(h, card, items, "断线重连后");
        afterLogin.removed(p);
        h.assertTrue(countCards(p) == 0, "背包里不该多出卡");
        h.succeed();
    }

    // ==================== 丢卡的那一步：第 1 格有一张，往第 2 格放卡，关了再开 ====================

    @GameTest(template = "empty", batch = GATE)
    public static void gateKeepsSecondSlotOnReopen(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = emptyCards(p);
        p.getInventory().clearContent();
        card.setOneCard(new ItemStack(cardItem(NETRA)));

        RequiemGateMenu menu = open(p, 1);
        h.assertTrue(menu.getSlot(0).getItem().is(cardItem(NETRA)), "第 1 格应当载入玩家数据里的那张");
        place(menu, p, 1, new ItemStack(cardItem(VOME)));
        menu.removed(p);

        RequiemGateMenu again = open(p, 2);
        h.assertTrue(again.getSlot(0).getItem().is(cardItem(NETRA)) && again.getSlot(1).getItem().is(cardItem(VOME)),
                "重开后第 1、2 格都该在：" + describe(again));
        h.assertTrue(card.getTwoCard().is(cardItem(VOME)), "玩家数据里第 2 格不该被清掉");
        again.removed(p);
        h.succeed();
    }

    // ==================== 放进 / 换卡 / 拿走：卡的总数不变 ====================

    @GameTest(template = "empty", batch = GATE)
    public static void gateSwapAndTakeConservesCards(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = emptyCards(p);
        p.getInventory().clearContent();
        card.setOneCard(new ItemStack(cardItem(KHRA)));
        card.setTwoCard(new ItemStack(cardItem(JAHU)));
        card.setThreeCard(new ItemStack(cardItem(NETRA)));
        p.getInventory().setItem(0, new ItemStack(cardItem(VOME)));

        RequiemGateMenu menu = open(p, 1);
        h.assertTrue(total(p, card, menu) == 4, "开扉时一共 4 张：" + total(p, card, menu));
        // 光标拿着 XATA 点第 2 格：换下 JAHU
        menu.setCarried(new ItemStack(cardItem(XATA)));
        menu.clicked(1, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(1).getItem().is(cardItem(XATA)) && menu.getCarried().is(cardItem(JAHU)), "换卡：XATA 进格、JAHU 到光标");
        h.assertTrue(card.getTwoCard().is(cardItem(XATA)), "换卡当下玩家数据跟着换");
        h.assertTrue(total(p, card, menu) == 5, "换卡后一共 5 张（多了光标上那张 XATA）：" + total(p, card, menu));
        // 把 JAHU 放回背包，数字键 1 把快捷栏的 VOME 换进第 3 格
        p.getInventory().setItem(1, menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(2, 0, ClickType.SWAP, p);
        h.assertTrue(menu.getSlot(2).getItem().is(cardItem(VOME)) && p.getInventory().getItem(0).is(cardItem(NETRA)),
                "数字键：VOME 进第 3 格、NETRA 回快捷栏");
        h.assertTrue(card.getThreeCard().is(cardItem(VOME)), "数字键换卡后玩家数据跟着换");
        // Shift 把第 1 格拿回背包
        menu.clicked(0, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(menu.getSlot(0).getItem().isEmpty() && card.getOneCard().isEmpty(), "Shift 拿走：卡槽和玩家数据都空");
        // 光标拿起第 2 格，开着光标关界面：卡回背包
        menu.clicked(1, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().is(cardItem(XATA)) && card.getTwoCard().isEmpty(), "拿起第 2 格当下玩家数据清掉");
        menu.removed(p);
        h.assertTrue(menu.getCarried().isEmpty(), "关界面时光标上的卡应当交还");
        h.assertTrue(total(p, card, null) == 5, "关界面后一共还是 5 张：背包 " + countCards(p) + "，扉里 " + countSaved(card));
        h.assertTrue(countSaved(card) == 1 && card.getThreeCard().is(cardItem(VOME)), "扉里只剩第 3 格的 VOME");
        h.succeed();
    }

    // ==================== 同一种卡只能放一张：每个入口都拦 ====================

    @GameTest(template = "empty", batch = GATE)
    public static void gateRejectsDuplicateCards(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = emptyCards(p);
        p.getInventory().clearContent();
        RequiemGateMenu menu = open(p, 1);
        place(menu, p, 0, new ItemStack(cardItem(NETRA)));
        ItemStack second = new ItemStack(cardItem(NETRA));

        h.assertTrue(!menu.getSlot(1).mayPlace(second) && !menu.getSlot(2).mayPlace(second), "别的格子已有 NETRA，第二张放不进");
        h.assertTrue(menu.getSlot(0).mayPlace(second), "同一格换一张同种的不算第二张");
        h.assertTrue(menu.getSlot(1).mayPlace(new ItemStack(cardItem(VOME))), "别的卡照常放得进");

        // 光标点
        menu.setCarried(second.copy());
        menu.clicked(1, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getCarried().is(cardItem(NETRA)), "光标点：第二张留在光标上");
        menu.clicked(1, 1, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getCarried().is(cardItem(NETRA)), "右键点：第二张留在光标上");
        // 拖放
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, p);
        menu.clicked(1, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, p);
        menu.clicked(2, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, p);
        menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, p);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty() && menu.getCarried().is(cardItem(NETRA)),
                "拖放：第二张留在光标上");
        menu.setCarried(ItemStack.EMPTY);

        // Shift 快速移动
        p.getInventory().setItem(0, second.copy());
        menu.clicked(HOTBAR_0, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(p.getInventory().getItem(0).is(cardItem(NETRA)), "Shift：第二张留在快捷栏");
        // 数字键：空格子、和放着别的卡的格子都换不进
        menu.clicked(1, 0, ClickType.SWAP, p);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty() && p.getInventory().getItem(0).is(cardItem(NETRA)), "数字键换到空格：换不进");
        place(menu, p, 2, new ItemStack(cardItem(VOME)));
        menu.clicked(2, 0, ClickType.SWAP, p);
        h.assertTrue(menu.getSlot(2).getItem().is(cardItem(VOME)) && p.getInventory().getItem(0).is(cardItem(NETRA)),
                "数字键换到放着 VOME 的格子：换不进");
        // 副手键
        p.getInventory().setItem(40, second.copy());
        menu.clicked(1, 40, ClickType.SWAP, p);
        h.assertTrue(menu.getSlot(1).getItem().isEmpty() && p.getInventory().getItem(40).is(cardItem(NETRA)), "副手键：换不进");

        // 把第 1 格的 NETRA 挪到第 2 格（扉里还是只有一张）可以
        menu.clicked(0, 0, ClickType.PICKUP, p);
        menu.clicked(1, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().is(cardItem(NETRA)), "同一张在扉里挪格子照常可以");

        menu.removed(p);
        h.assertTrue(countSaved(card) == 2 && card.getTwoCard().is(cardItem(NETRA)) && card.getThreeCard().is(cardItem(VOME)),
                "存下的是一张 NETRA、一张 VOME：" + savedNames(card));
        h.assertTrue(countCards(p) == 2, "两张被拦下的 NETRA 都还在背包里：" + countCards(p));
        h.succeed();
    }

    // ==================== 存档里已有重复卡：开扉时多的退还，背包满了掉在脚下 ====================

    @GameTest(template = "empty", batch = GATE)
    public static void gateReturnsStoredDuplicates(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = emptyCards(p);
        p.getInventory().clearContent();
        ItemStack worn = new ItemStack(cardItem(NETRA));
        worn.setDamageValue(1);
        card.setOneCard(new ItemStack(cardItem(NETRA)));
        card.setTwoCard(worn.copy());
        card.setThreeCard(new ItemStack(cardItem(VOME)));

        RequiemGateMenu menu = open(p, 1);
        h.assertTrue(menu.getSlot(0).getItem().is(cardItem(NETRA)) && menu.getSlot(1).getItem().isEmpty()
                && menu.getSlot(2).getItem().is(cardItem(VOME)), "重复的那张不该留在卡槽里：" + describe(menu));
        h.assertTrue(card.getOneCard().is(cardItem(NETRA)) && card.getTwoCard().isEmpty() && card.getThreeCard().is(cardItem(VOME)),
                "玩家数据里重复的那张也拿掉：" + savedNames(card));
        int at = findCard(p, NETRA);
        h.assertTrue(at >= 0, "多出的那张应当退回背包");
        h.assertTrue(p.getInventory().getItem(at).getDamageValue() == 1, "退回的就是多出的那张（耐久也不变）");
        menu.removed(p);

        // 三张全一样、背包塞满：留一张，两张掉在脚下
        card.setOneCard(new ItemStack(cardItem(XATA)));
        card.setTwoCard(new ItemStack(cardItem(XATA)));
        card.setThreeCard(new ItemStack(cardItem(XATA)));
        for (int i = 0; i < 36; i++) {
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        AABB around = p.getBoundingBox().inflate(3.0D);
        discardItems(h, around);
        RequiemGateMenu full = open(p, 2);
        h.assertTrue(countSaved(card) == 1 && full.getSlot(0).getItem().is(cardItem(XATA)), "三张一样只留第 1 格那张：" + describe(full));
        int dropped = 0;
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> !e.isRemoved())) {
            if (e.getItem().is(cardItem(XATA))) {
                dropped += e.getItem().getCount();
            }
        }
        h.assertTrue(dropped == 2, "背包满了，两张多的掉在脚下：" + dropped);
        full.removed(p);
        discardItems(h, around);

        // 两张 Oull：多的那张也退
        p.getInventory().clearContent();
        card.setOneCard(new ItemStack(KuvaLichItems.OULL_CARD.get()));
        card.setTwoCard(new ItemStack(cardItem(KHRA)));
        card.setThreeCard(new ItemStack(KuvaLichItems.OULL_CARD.get()));
        RequiemGateMenu oull = open(p, 3);
        h.assertTrue(OULL.is(card.getOneCard()) && card.getTwoCard().is(cardItem(KHRA)) && card.getThreeCard().isEmpty(),
                "两张 Oull 只留第 1 格那张：" + savedNames(card));
        h.assertTrue(findCard(p, OULL.ID) >= 0, "多的 Oull 退回背包");
        oull.removed(p);
        h.succeed();
    }

    // ==================== 存档里已有重复卡：登录时就退还，死亡界面上登录的先不动 ====================

    @GameTest(template = "empty", batch = GATE)
    public static void loginReturnsStoredDuplicates(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = emptyCards(p);
        p.getInventory().clearContent();
        card.setOneCard(new ItemStack(cardItem(VOME)));
        card.setTwoCard(new ItemStack(cardItem(KHRA)));
        card.setThreeCard(new ItemStack(cardItem(VOME)));
        // 第一次走事件总线发，顺带确认登录检查真的注册上了
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(card.getOneCard().is(cardItem(VOME)) && card.getTwoCard().is(cardItem(KHRA)) && card.getThreeCard().isEmpty(),
                "登录时第 3 格重复的 VOME 拿掉：" + savedNames(card));
        h.assertTrue(findCard(p, VOME) >= 0 && countCards(p) == 1, "拿掉的那张进了背包");
        // 没有重复的登录什么都不动
        RequiemGateMenu.DuplicateCardLoginCheck.onLoggedIn(new PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(countSaved(card) == 2 && countCards(p) == 1, "没有重复时登录不该动卡");

        // 死着登录：先不动，开扉时再退
        p.getInventory().clearContent();
        card.setThreeCard(new ItemStack(cardItem(KHRA)));
        p.setHealth(0.0F);
        RequiemGateMenu.DuplicateCardLoginCheck.onLoggedIn(new PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(countSaved(card) == 3 && countCards(p) == 0, "死亡界面上登录先不动：" + savedNames(card));
        p.setHealth(p.getMaxHealth());
        RequiemGateMenu menu = open(p, 1);
        h.assertTrue(card.getThreeCard().isEmpty() && findCard(p, KHRA) >= 0, "活过来开扉时再退：" + savedNames(card));
        menu.removed(p);
        h.succeed();
    }

    // ==================== 开着扉时安魂成功：扣掉的卡不被卡槽写回来 ====================

    @GameTest(template = "empty", batch = ISO_CONSUME, timeoutTicks = 120)
    public static void gateOpenWhenCardsConsumed(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = cardOf(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        h.runAfterDelay(2, () -> {
            card.setOneAnswer(KHRA);
            card.setTwoAnswer(JAHU);
            card.setThreeAnswer(NETRA);
            card.setOneCard(new ItemStack(cardItem(KHRA)));
            card.setTwoCard(new ItemStack(cardItem(JAHU)));
            card.setThreeCard(new ItemStack(cardItem(NETRA)));
            p.getInventory().clearContent();
            RequiemGateMenu menu = open(p, 7);
            p.containerMenu = menu;

            lich.computeAndApplySuccess(p, card, lich.blockPosition());
            // 下一 tick 原版照常调 broadcastChanges
            menu.broadcastChanges();
            p.containerMenu.broadcastChanges();

            h.assertTrue(card.getOneCard().isEmpty() && card.getTwoCard().isEmpty() && card.getThreeCard().isEmpty(),
                    "扣掉的卡不该被卡槽写回玩家数据：" + savedNames(card));
            if (p.containerMenu == menu) {
                // 扉还开着：卡槽里不该留着扣掉的卡
                for (int i = 0; i < 3; i++) {
                    h.assertTrue(menu.getSlot(i).getItem().isEmpty(), "扉里第 " + (i + 1) + " 格不该还留着扣掉的卡");
                }
                menu.removed(p);
                p.containerMenu = p.inventoryMenu;
            }
            h.assertTrue(countSaved(card) == 0, "关界面后玩家数据里也没有卡：" + savedNames(card));
            int worn = 0;
            for (ItemStack s : p.getInventory().items) {
                if (s.getItem() instanceof AbstractRequiemCard) {
                    h.assertTrue(s.getDamageValue() == 1, "背包里的卡都该是扣过一次的：" + s.getDescriptionId() + " 损耗 " + s.getDamageValue());
                    worn++;
                }
            }
            h.assertTrue(worn == 3, "三张卡各扣一次回到背包，不多不少：" + worn);
            lich.discard();
            h.succeed();
        });
    }

    // ==================== 处决中扉里的卡动不了：开始时按当时的卡定了结果，中途拿走成功时就扣不到 ====================

    @GameTest(template = "empty", batch = ISO_MERCY_LOCK, timeoutTicks = 120)
    public static void gateLockedDuringMercy(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = cardOf(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            card.setOneAnswer(KHRA);
            card.setTwoAnswer(JAHU);
            card.setThreeAnswer(NETRA);
            card.setOneCard(new ItemStack(cardItem(KHRA)));
            card.setTwoCard(new ItemStack(cardItem(JAHU)));
            card.setThreeCard(new ItemStack(cardItem(NETRA)));
            p.getInventory().clearContent();
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            // 处决开始了扉还开着（改过的客户端才做得到；扉就在脚下，不是离太远失效），发点击包 Shift 拿第 1 格
            RequiemGateMenu menu = open(p, 7);
            p.containerMenu = menu;
            h.assertTrue(!menu.stillValid(p), "处决中扉应当失效");
            shiftClickPacket(p, menu, 0);
            h.assertTrue(menu.getSlot(0).getItem().is(cardItem(KHRA)) && card.getOneCard().is(cardItem(KHRA)) && countCards(p) == 0,
                    "处决中点卡槽不该把卡拿走：" + describe(menu) + "，背包 " + countCards(p));
            h.runAfterDelay(2, () -> {
                h.assertTrue(p.containerMenu == p.inventoryMenu, "失效的扉应当被原版关掉");
                h.assertTrue(countSaved(card) == 3 && countCards(p) == 0, "关掉时卡照常存着：" + savedNames(card));
                h.assertTrue(MercyServer.stop(p) == null, "还没定案，应当能中止");
                // 过了余量扉照常能用：同样的点击包这次把卡拿回背包（上面那个包没生效不是因为包本身不对）
                h.runAfterDelay(MercyServer.GRACE_TICKS + 2, () -> {
                    h.assertTrue(!MercyServer.isBusy(p), "应当已经解锁、过了余量");
                    RequiemGateMenu after = open(p, 8);
                    p.containerMenu = after;
                    shiftClickPacket(p, after, 0);
                    h.assertTrue(after.getSlot(0).getItem().isEmpty() && card.getOneCard().isEmpty() && findCard(p, KHRA) >= 0,
                            "解锁后 Shift 点击照常拿回背包：" + describe(after));
                    after.removed(p);
                    p.containerMenu = p.inventoryMenu;
                    h.assertTrue(total(p, card, null) == 3, "前后一共还是 3 张：背包 " + countCards(p) + "，扉里 " + countSaved(card));
                    lich.discard();
                    h.succeed();
                });
            });
        });
    }

    @AfterBatch(batch = GATE)
    public static void afterGate(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = ISO_CONSUME)
    public static void afterConsume(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = ISO_MERCY_LOCK)
    public static void afterMercyLock(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 工具 ====================

    private static RequiemCard cardOf(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD)
                .orElseThrow(() -> new IllegalStateException("测试玩家没有安魂卡能力"));
    }

    private static RequiemCard emptyCards(ServerPlayer p) {
        RequiemCard card = cardOf(p);
        card.setOneCard(ItemStack.EMPTY);
        card.setTwoCard(ItemStack.EMPTY);
        card.setThreeCard(ItemStack.EMPTY);
        return card;
    }

    private static Item cardItem(int id) {
        return KuvaLichItems.getRequiemCard(id);
    }

    private static RequiemGateMenu open(ServerPlayer p, int windowId) {
        return new RequiemGateMenu(windowId, p.getInventory(), p.level(), p.blockPosition());
    }

    /** 像客户端那样发一个 Shift 点击包，走服务端处理点击包的原版代码（会先查界面还有没有效） */
    private static void shiftClickPacket(ServerPlayer p, RequiemGateMenu menu, int slot) {
        p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot, 0,
                ClickType.QUICK_MOVE, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
    }

    /** 光标拿着这张卡左键点第 slot 格 */
    private static void place(RequiemGateMenu menu, ServerPlayer p, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ClickType.PICKUP, p);
    }

    private static void assertSlots(GameTestHelper h, RequiemGateMenu menu, Item[] items, String when) {
        for (int i = 0; i < 3; i++) {
            h.assertTrue(menu.getSlot(i).getItem().is(items[i]), when + "第 " + (i + 1) + " 格应当还在：" + describe(menu));
        }
    }

    private static void assertCards(GameTestHelper h, RequiemCard card, Item[] items, String when) {
        ItemStack[] saved = {card.getOneCard(), card.getTwoCard(), card.getThreeCard()};
        for (int i = 0; i < 3; i++) {
            h.assertTrue(saved[i].is(items[i]), when + "玩家数据第 " + (i + 1) + " 格应当是 " + items[i] + "：" + savedNames(card));
        }
    }

    private static String describe(RequiemGateMenu menu) {
        StringBuilder sb = new StringBuilder("卡槽");
        for (int i = 0; i < 3; i++) {
            ItemStack s = menu.getSlot(i).getItem();
            sb.append(' ').append(s.isEmpty() ? "空" : s.getDescriptionId());
        }
        return sb.toString();
    }

    private static String savedNames(RequiemCard card) {
        StringBuilder sb = new StringBuilder("玩家数据");
        for (ItemStack s : new ItemStack[]{card.getOneCard(), card.getTwoCard(), card.getThreeCard()}) {
            sb.append(' ').append(s.isEmpty() ? "空" : s.getDescriptionId());
        }
        return sb.toString();
    }

    /** 背包里（含副手）有几张安魂卡 */
    private static int countCards(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof AbstractRequiemCard) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int countSaved(RequiemCard card) {
        int n = 0;
        for (ItemStack s : new ItemStack[]{card.getOneCard(), card.getTwoCard(), card.getThreeCard()}) {
            if (!s.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** 背包 + 光标 + 玩家数据（扉里）一共几张卡；卡槽一变就回写玩家数据，两边一致，只数玩家数据 */
    private static int total(ServerPlayer p, RequiemCard card, RequiemGateMenu menu) {
        int carried = menu != null && menu.getCarried().getItem() instanceof AbstractRequiemCard ? 1 : 0;
        return countCards(p) + countSaved(card) + carried;
    }

    private static int findCard(ServerPlayer p, int id) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).getItem() instanceof AbstractRequiemCard c && c.getID() == id) {
                return i;
            }
        }
        return -1;
    }

    private static void discardItems(GameTestHelper h, AABB area) {
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> !e.isRemoved())) {
            e.discard();
        }
    }

    /** 走正常的进度流程揭开第一条密语，再让玄骸诞生（平常要处决一只幼体，这里直接生） */
    private static void bornByProgress(ServerPlayer p, RequiemCard card) {
        int threshold = ModConfig.KUVA_LICH.firstStage.get();
        card.setDecryptionProgress(Math.max(0, threshold - 1));
        int levelBefore = card.getUnlockedCardStatus();
        card.addPotion(1);
        LichDirector.onDecryptionProgress(p, card, 1, levelBefore);
        LichDirector.birthNow(p);
    }

    /** 立即召到面前（场地同 MercyCoreTests：先载区块、挖一块封闭场地） */
    private static KuvaMasterEntity summonFront(GameTestHelper h, ServerPlayer p) {
        ChunkPos origin = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        for (int dx = -1; dx < 4; dx++) {
            for (int dz = -1; dz < 4; dz++) {
                h.getLevel().getChunk(origin.x + dx, origin.z + dz);
            }
        }
        BlockPos c = KuvaTestKit.center(h);
        KuvaTestKit.openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 3, c.getZ() + 11);
        h.assertTrue(LichDirector.summonNow(p, SummonMode.FRONT), "立即召唤应当成功");
        return LichDirector.getLich(p).orElseThrow(() -> new IllegalStateException("召唤后应当有在场的玄骸"));
    }
}
