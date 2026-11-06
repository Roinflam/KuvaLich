package pers.roinflam.kuvalich.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.network.packet.RivenCyclePacket;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 安魂之融的裂罅循环：放进去不自动洗；单张洗、再循环、换上；多张洗挑一张、都不要、关界面；
 * 价格是连洗 N 次的总价、赤毒扣完从背包补满；挑的时候两格锁住；序号对不上的请求作废。
 * 每一步都核对：裂罅总数不多不少、赤毒只少了该扣的那些
 *
 * <p>请求直接调 {@link RequiemEvolveMenu#handleRivenRequest}（和服务端收到请求包后走的是同一段），
 * 点击一律走 {@link RequiemEvolveMenu#clicked}。</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RivenCycleTests {

    private static final String RIVEN = "kl_riven_cycle";

    private static final int IN = 0;
    private static final int MAT = 1;
    private static final int RES = 2;
    /** 菜单里三格之后的第一格是快捷栏第 1 格 */
    private static final int HOTBAR_0 = 3;

    private RivenCycleTests() {
    }

    // ==================== 放进去不自动洗 ====================

    @GameTest(template = "empty", batch = RIVEN)
    public static void rivenNoAutoRoll(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        RequiemEvolveMenu menu = open(p, 1);
        ItemStack riven = weaponRiven(3, 6);
        place(menu, p, IN, riven.copy());
        place(menu, p, MAT, kuva(64));
        menu.broadcastChanges();
        menu.broadcastChanges();
        h.assertTrue(menu.getSlot(RES).getItem().isEmpty(), "放进去不该自动出预览");
        h.assertTrue(menu.getSlot(MAT).getItem().getCount() == 64, "放进去不该扣赤毒");
        h.assertTrue(WeaponRivenModule.getCycle(menu.getSlot(IN).getItem()) == 6, "放进去循环次数不该变");
        menu.removed(p);
        h.assertTrue(countRivens(p, null) == 1 && countKuva(p, null) == 64, "关了以后卡和赤毒都回背包");
        h.succeed();
    }

    // ==================== 单张：循环 → 再循环 → 换上；材料格从背包补满；旧序号作废 ====================

    @GameTest(template = "empty", batch = RIVEN)
    public static void rivenSingleRollAndSwap(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        p.getInventory().setItem(20, kuva(64));
        p.getInventory().setItem(21, kuva(40));
        RequiemEvolveMenu menu = open(p, 1);
        place(menu, p, IN, weaponRiven(3, 6));
        place(menu, p, MAT, kuva(10));
        int kuva0 = countKuva(p, menu);

        int cost = RequiemEvolveMenu.rivenCycleCost(menu.getSlot(IN).getItem(), 6);
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 1);
        h.assertTrue(menu.hasRivenPreview(), "按循环应当出预览");
        h.assertTrue(WeaponRivenModule.getCycle(menu.getSlot(IN).getItem()) == 7, "原卡循环次数 +1");
        h.assertTrue(WeaponRivenModule.getCycle(menu.getSlot(RES).getItem()) == 7, "预览记成洗完以后的次数");
        h.assertTrue(countKuva(p, menu) == kuva0 - cost, "只扣一次的钱：" + countKuva(p, menu) + " / " + (kuva0 - cost));
        h.assertTrue(menu.getSlot(MAT).getItem().getCount() == 64, "材料格应当从背包补满：" + menu.getSlot(MAT).getItem().getCount());

        // 旧序号：作废，不扣钱
        int stale = menu.rivenSerial() - 1;
        int before = countKuva(p, menu);
        ItemStack preview = menu.getSlot(RES).getItem().copy();
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, stale, 1);
        h.assertTrue(countKuva(p, menu) == before && ItemStack.matches(preview, menu.getSlot(RES).getItem()),
                "旧序号的循环不该生效");

        // 预览中往材料格再添赤毒：预览留着
        p.getInventory().setItem(22, kuva(5));
        menu.clicked(HOTBAR_0 + 22, 0, ClickType.QUICK_MOVE, p);
        menu.broadcastChanges();
        h.assertTrue(menu.hasRivenPreview(), "预览中动材料格不该把预览清掉");

        // 再循环：这张作废，再扣一次
        int cost2 = RequiemEvolveMenu.rivenCycleCost(menu.getSlot(IN).getItem(), 7);
        int k1 = countKuva(p, menu);
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 1);
        h.assertTrue(countKuva(p, menu) == k1 - cost2, "再循环扣第二次的钱");
        h.assertTrue(WeaponRivenModule.getCycle(menu.getSlot(RES).getItem()) == 8, "再循环的预览是 8 次");

        // 换上：预览进物品格，原卡没了
        ItemStack want = menu.getSlot(RES).getItem().copy();
        menu.handleRivenRequest(p, RivenCyclePacket.SWAP, menu.rivenSerial(), 0);
        h.assertTrue(ItemStack.matches(menu.getSlot(IN).getItem(), want), "换上以后物品格是预览那张");
        h.assertTrue(menu.getSlot(RES).getItem().isEmpty(), "换上以后结果格空");
        h.assertTrue(countRivens(p, menu) == 1, "换上前后只有一张裂罅：" + countRivens(p, menu));
        // 再换一次：没有预览，作废
        menu.handleRivenRequest(p, RivenCyclePacket.SWAP, menu.rivenSerial(), 0);
        h.assertTrue(ItemStack.matches(menu.getSlot(IN).getItem(), want) && countRivens(p, menu) == 1, "没有预览时换上不该生效");
        menu.removed(p);
        h.assertTrue(countRivens(p, null) == 1, "关界面后背包里一张裂罅");
        h.succeed();
    }

    // ==================== 拿走结果格也算换上 ====================

    @GameTest(template = "empty", batch = RIVEN)
    public static void rivenTakeResultReplaces(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        RequiemEvolveMenu menu = open(p, 1);
        place(menu, p, IN, warframeRiven(2, 0));
        place(menu, p, MAT, kuva(64));
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 1);
        h.assertTrue(menu.hasRivenPreview(), "战甲裂罅也能洗");
        menu.clicked(RES, 0, ClickType.QUICK_MOVE, p);
        menu.broadcastChanges();
        h.assertTrue(menu.getSlot(IN).getItem().isEmpty(), "拿走预览后原卡消耗");
        h.assertTrue(countRivens(p, menu) == 1, "拿走后一共一张：" + countRivens(p, menu));
        menu.removed(p);
        h.succeed();
    }

    // ==================== 多张：总价、锁格、挑一张、同一序号第二次挑作废 ====================

    @GameTest(template = "empty", batch = RIVEN)
    public static void rivenMultiPick(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        p.getInventory().setItem(30, kuva(64));
        p.getInventory().setItem(31, kuva(64));
        RequiemEvolveMenu menu = open(p, 1);
        place(menu, p, IN, weaponRiven(4, 2));
        place(menu, p, MAT, kuva(64));
        int kuva0 = countKuva(p, menu);
        int price = RequiemEvolveMenu.rivenCyclePrice(menu.getSlot(IN).getItem(), 10);
        int expect = 0;
        for (int i = 0; i < 10; i++) {
            expect += Math.min(2 + i, 8) + 2 * 4 - 1;
        }
        h.assertTrue(price == expect, "10 连的总价按每次当时的次数算：" + price + " / " + expect);

        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 10);
        h.assertTrue(menu.isPickingRiven() && menu.rivenCandidates().size() == 10, "应当有 10 张候选");
        h.assertTrue(countKuva(p, menu) == kuva0 - price, "扣总价：" + (kuva0 - countKuva(p, menu)) + " / " + price);
        h.assertTrue(WeaponRivenModule.getCycle(menu.getSlot(IN).getItem()) == 12, "原卡循环次数 +10");
        for (ItemStack c : menu.rivenCandidates()) {
            h.assertTrue(WeaponRivenModule.getCycle(c) == 12, "候选都记成洗完以后的 12 次");
        }
        h.assertTrue(menu.getSlot(RES).getItem().isEmpty(), "多张洗不出预览");
        h.assertTrue(countRivens(p, menu) == 1, "候选不算进背包：" + countRivens(p, menu));

        // 锁格：拿、Shift、往里放、数字键换都不行
        ItemStack orig = menu.getSlot(IN).getItem().copy();
        menu.clicked(IN, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(menu.getSlot(IN).getItem(), orig), "挑选中拿不走原卡");
        menu.clicked(IN, 0, ClickType.QUICK_MOVE, p);
        menu.clicked(MAT, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(ItemStack.matches(menu.getSlot(IN).getItem(), orig) && !menu.getSlot(MAT).getItem().isEmpty(),
                "挑选中 Shift 拿不走");
        menu.clicked(IN, 0, ClickType.SWAP, p);
        h.assertTrue(ItemStack.matches(menu.getSlot(IN).getItem(), orig), "挑选中数字键换不走");
        p.getInventory().setItem(0, kuva(7));
        menu.clicked(HOTBAR_0, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(p.getInventory().getItem(0).getCount() == 7, "挑选中 Shift 放不进材料格");
        h.assertTrue(menu.quickMoveStack(p, HOTBAR_0).isEmpty(), "quickMoveStack 整个拦掉");
        menu.broadcastChanges();
        h.assertTrue(menu.isPickingRiven(), "挑选中 broadcastChanges 不该清候选");

        // 旧序号挑：作废
        int serial = menu.rivenSerial();
        menu.handleRivenRequest(p, RivenCyclePacket.PICK, serial - 1, 3);
        h.assertTrue(menu.isPickingRiven() && ItemStack.matches(menu.getSlot(IN).getItem(), orig), "旧序号挑不生效");
        // 下标越界：作废
        menu.handleRivenRequest(p, RivenCyclePacket.PICK, serial, 10);
        menu.handleRivenRequest(p, RivenCyclePacket.PICK, serial, -1);
        h.assertTrue(menu.isPickingRiven(), "越界下标不生效");
        // 挑第 4 张
        ItemStack want = menu.rivenCandidates().get(3).copy();
        menu.handleRivenRequest(p, RivenCyclePacket.PICK, serial, 3);
        h.assertTrue(!menu.isPickingRiven() && ItemStack.matches(menu.getSlot(IN).getItem(), want), "挑完物品格是第 4 张");
        // 同一 tick 第二个挑选（同一个序号）：作废
        menu.handleRivenRequest(p, RivenCyclePacket.PICK, serial, 5);
        h.assertTrue(ItemStack.matches(menu.getSlot(IN).getItem(), want), "同一序号第二次挑不生效");
        h.assertTrue(countRivens(p, menu) == 1, "挑完一共一张：" + countRivens(p, menu));
        // 挑完解锁
        menu.clicked(IN, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(menu.getSlot(IN).getItem().isEmpty(), "挑完可以拿走");
        menu.removed(p);
        h.assertTrue(countRivens(p, null) == 1, "关界面后一张");
        h.succeed();
    }

    // ==================== 都不要、关界面：原卡留着，候选作废，赤毒不退 ====================

    @GameTest(template = "empty", batch = RIVEN)
    public static void rivenDismissAndClose(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        p.getInventory().setItem(30, kuva(64));
        RequiemEvolveMenu menu = open(p, 1);
        place(menu, p, IN, weaponRiven(2, 0));
        place(menu, p, MAT, kuva(64));
        int kuva0 = countKuva(p, menu);
        int price3 = RequiemEvolveMenu.rivenCyclePrice(menu.getSlot(IN).getItem(), 3);
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 3);
        menu.handleRivenRequest(p, RivenCyclePacket.DISMISS, menu.rivenSerial(), 0);
        h.assertTrue(!menu.isPickingRiven() && WeaponRivenModule.getCycle(menu.getSlot(IN).getItem()) == 3,
                "都不要：原卡留着、次数照加");
        h.assertTrue(countKuva(p, menu) == kuva0 - price3, "都不要不退赤毒");
        // 挑选中不能再洗
        int price2 = RequiemEvolveMenu.rivenCyclePrice(menu.getSlot(IN).getItem(), 2);
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 2);
        int k2 = countKuva(p, menu);
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 2);
        h.assertTrue(countKuva(p, menu) == k2 && menu.rivenCandidates().size() == 2, "挑选中再按循环不生效");
        h.assertTrue(k2 == kuva0 - price3 - price2, "第二轮扣两张的钱");
        ItemStack orig = menu.getSlot(IN).getItem().copy();
        menu.removed(p);
        h.assertTrue(countRivens(p, null) == 1, "关界面：只退回原卡一张：" + countRivens(p, null));
        h.assertTrue(findRiven(p) != null && ItemStack.matches(findRiven(p), orig), "退回的是原卡");
        h.assertTrue(countKuva(p, null) == k2, "关界面不退赤毒");
        // 关了以后挑：作废（菜单不是当前菜单）
        menu.handleRivenRequest(p, RivenCyclePacket.PICK, menu.rivenSerial(), 0);
        h.assertTrue(countRivens(p, null) == 1, "关了以后挑不出东西");
        h.succeed();
    }

    // ==================== 赤毒不够、结果格里有别的东西：不洗、不扣 ====================

    @GameTest(template = "empty", batch = RIVEN)
    public static void rivenRefusesBadStates(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        RequiemEvolveMenu menu = open(p, 1);
        place(menu, p, IN, weaponRiven(5, 8));
        place(menu, p, MAT, kuva(20));
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 3);
        h.assertTrue(!menu.isPickingRiven() && menu.getSlot(MAT).getItem().getCount() == 20
                && WeaponRivenModule.getCycle(menu.getSlot(IN).getItem()) == 8, "赤毒不够不洗不扣");
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 11);
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 0);
        h.assertTrue(menu.getSlot(MAT).getItem().getCount() == 20 && menu.getSlot(RES).getItem().isEmpty(), "张数越界不生效");
        menu.removed(p);

        // 开光做好了还没拿走：结果格里那把剑不能被裂罅预览顶掉
        p.getInventory().clearContent();
        RequiemEvolveMenu m2 = open(p, 2);
        place(m2, p, IN, new ItemStack(Items.IRON_SWORD));
        place(m2, p, MAT, new ItemStack(KuvaLichItems.RIVEN_SLIVER.get(), 2));
        m2.broadcastChanges();
        h.assertTrue(m2.getSlot(RES).getItem().is(Items.IRON_SWORD), "开光应当出结果");
        m2.clicked(MAT, 0, ClickType.QUICK_MOVE, p);
        m2.broadcastChanges();
        place(m2, p, IN, weaponRiven(1, 0));
        place(m2, p, MAT, kuva(64));
        m2.broadcastChanges();
        m2.handleRivenRequest(p, RivenCyclePacket.ROLL, m2.rivenSerial(), 1);
        h.assertTrue(m2.getSlot(RES).getItem().is(Items.IRON_SWORD), "结果格里的剑不能被顶掉");
        h.assertTrue(m2.getSlot(MAT).getItem().getCount() == 64, "没洗就不扣");
        m2.removed(p);
        h.assertTrue(countItem(p, Items.IRON_SWORD) == 1, "剑退回背包");
        h.succeed();
    }

    // ==================== 背包快满时关界面：材料格那组放不全的部分掉在脚下，不能凭空没了 ====================

    @GameTest(template = "empty", batch = RIVEN)
    public static void rivenCloseFullInventoryKeepsKuva(GameTestHelper h) {
        ServerPlayer p = fresh(h);
        Inventory inv = p.getInventory();
        for (int i = 0; i < 33; i++) {
            inv.setItem(i, new ItemStack(Items.STONE, 64));
        }
        for (int i = 33; i < 36; i++) {
            inv.setItem(i, kuva(64));
        }
        RequiemEvolveMenu menu = open(p, 1);
        menu.getSlot(IN).set(weaponRiven(3, 8));
        menu.getSlot(MAT).set(kuva(64));
        menu.broadcastChanges();
        int total0 = countKuva(p, menu);
        int cost = RequiemEvolveMenu.rivenCycleCost(menu.getSlot(IN).getItem(), 8);
        menu.handleRivenRequest(p, RivenCyclePacket.ROLL, menu.rivenSerial(), 1);
        h.assertTrue(menu.hasRivenPreview() && countKuva(p, menu) == total0 - cost, "应当洗一次、扣 " + cost);
        h.assertTrue(menu.getSlot(MAT).getItem().getCount() == 64, "材料格从背包补满");
        menu.removed(p);

        AABB box = p.getBoundingBox().inflate(6.0D);
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, box);
        int kuvaDropped = 0;
        int rivenDropped = 0;
        for (ItemEntity e : drops) {
            ItemStack s = e.getItem();
            if (s.getItem() instanceof Kuva) {
                kuvaDropped += s.getCount();
            } else if (s.getItem() instanceof WeaponRivenModule) {
                rivenDropped += s.getCount();
            }
            e.discard();
        }
        int kept = countKuva(p, null) + kuvaDropped;
        h.assertTrue(kept == total0 - cost, "赤毒一个都不能丢：背包 " + countKuva(p, null) + " + 脚下 " + kuvaDropped
                + "，应当 " + (total0 - cost));
        h.assertTrue(countRivens(p, null) + rivenDropped == 1, "原卡退回（背包满就掉在脚下）");
        h.succeed();
    }

    // ==================== 指令给的白板卡（倾向 0）、改坏的数据：价格夹在正数 ====================

    @GameTest(template = "empty", batch = RIVEN)
    public static void rivenPriceNeverBelowOne(GameTestHelper h) {
        ItemStack blank = new ItemStack(KuvaLichItems.ITEM_RIVEN_MODULE.get());
        h.assertTrue(RequiemEvolveMenu.rivenCycleCost(blank, 0) >= 1, "白板卡单次价格至少 1："
                + RequiemEvolveMenu.rivenCycleCost(blank, 0));
        h.assertTrue(RequiemEvolveMenu.rivenCycleCost(blank, -50) >= 1, "负的循环次数也至少 1");
        ItemStack big = warframeRiven(3, 0);
        big.getOrCreateTag().getCompound(Reference.MOD_ID + "_modules").putInt("Trend", Integer.MAX_VALUE);
        int c = RequiemEvolveMenu.rivenCycleCost(big, 0);
        h.assertTrue(c >= 1 && RequiemEvolveMenu.rivenCyclePrice(big, RequiemEvolveMenu.RIVEN_MAX_ROLL) > 0,
                "倾向改成极大值不能算溢出成负价：" + c);
        h.succeed();
    }

    @AfterBatch(batch = RIVEN)
    public static void afterRiven(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 工具 ====================

    private static ServerPlayer fresh(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        p.getInventory().clearContent();
        return p;
    }

    private static RequiemEvolveMenu open(ServerPlayer p, int windowId) {
        RequiemEvolveMenu menu = new RequiemEvolveMenu(windowId, p.getInventory(), p.level(), p.blockPosition());
        p.containerMenu = menu;
        return menu;
    }

    private static ItemStack weaponRiven(int trend, int cycle) {
        // cycleModule 写进去的次数是传入值 + 1
        ItemStack s = WeaponRivenModule.cycleModule(trend, cycle - 1, WeaponRivenModule.MODE_MELEE);
        WeaponRivenModule.setCycle(s, cycle);
        return s;
    }

    private static ItemStack warframeRiven(int trend, int cycle) {
        ItemStack s = WarframeRivenModule.cycleModule(trend, cycle - 1);
        WarframeRivenModule.setCycle(s, cycle);
        return s;
    }

    private static ItemStack kuva(int n) {
        return new ItemStack(KuvaLichItems.KUVA.get(), n);
    }

    /** 光标拿着这件东西左键点第 slot 格 */
    private static void place(RequiemEvolveMenu menu, ServerPlayer p, int slot, ItemStack stack) {
        menu.setCarried(stack);
        menu.clicked(slot, 0, ClickType.PICKUP, p);
        if (!menu.getCarried().isEmpty()) {
            p.getInventory().add(menu.getCarried());
            menu.setCarried(ItemStack.EMPTY);
        }
    }

    private static List<ItemStack> all(ServerPlayer p, RequiemEvolveMenu menu) {
        List<ItemStack> out = new ArrayList<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            out.add(inv.getItem(i));
        }
        if (menu != null) {
            out.add(menu.getSlot(IN).getItem());
            out.add(menu.getSlot(MAT).getItem());
            out.add(menu.getSlot(RES).getItem());
            out.add(menu.getCarried());
        }
        return out;
    }

    private static int countRivens(ServerPlayer p, RequiemEvolveMenu menu) {
        int n = 0;
        for (ItemStack s : all(p, menu)) {
            if (s.getItem() instanceof WeaponRivenModule || s.getItem() instanceof WarframeRivenModule) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int countKuva(ServerPlayer p, RequiemEvolveMenu menu) {
        int n = 0;
        for (ItemStack s : all(p, menu)) {
            if (s.getItem() instanceof Kuva) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static int countItem(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : all(p, null)) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static ItemStack findRiven(ServerPlayer p) {
        for (ItemStack s : all(p, null)) {
            if (s.getItem() instanceof WeaponRivenModule || s.getItem() instanceof WarframeRivenModule) {
                return s;
            }
        }
        return null;
    }
}
