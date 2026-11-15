package pers.roinflam.kuvalich.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.network.packet.WarframeModuleSyncPacket;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemMenuLogoutGuard;
import pers.roinflam.kuvalich.world.inventory.RequiemWarframeTableMenu;

import java.util.List;

/**
 * 战甲军械库的幻纹格：放进、取出、shift 进出、换款、关界面、掉线、重登、重生，幻纹总件数一件不多一件不少
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EphemeraSlotTests {

    private static final String BATCH = "kl_ephemera_slot";
    private static final int EPH = RequiemWarframeTableMenu.EPHEMERA_SLOT;
    /** 菜单里快捷栏第 0 格的序号 */
    private static final int HOTBAR0 = RequiemWarframeTableMenu.INV_START;

    private EphemeraSlotTests() {
    }

    private static WarframeModules wm(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).orElseThrow(IllegalStateException::new);
    }

    private static RequiemWarframeTableMenu open(ServerPlayer p) {
        RequiemWarframeTableMenu menu = new RequiemWarframeTableMenu(0, p.getInventory(), p.level(), p.blockPosition());
        p.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer p) {
        p.doCloseContainer();
    }

    private static ItemStack eph(EphemeraType type, String element) {
        return EphemeraItem.create(type, element, "Vesk Arru");
    }

    /** 背包 + 能力里 + 光标上 + 开着的菜单幻纹格里一共几件幻纹（菜单格和能力是同一件的两份，开着时只数能力） */
    private static int total(ServerPlayer p) {
        int n = p.getInventory().countItem(KuvaLichItems.EPHEMERA.get());
        if (!wm(p).getEphemera().isEmpty()) {
            n++;
        }
        ItemStack carried = p.containerMenu.getCarried();
        if (carried.is(KuvaLichItems.EPHEMERA.get())) {
            n += carried.getCount();
        }
        return n;
    }

    /** 开着的菜单里幻纹格和能力对得上，而且不是同一个对象 */
    private static void inSync(GameTestHelper h, RequiemWarframeTableMenu menu, ServerPlayer p, String when) {
        ItemStack inSlot = menu.getSlot(EPH).getItem();
        ItemStack inCap = wm(p).getEphemera();
        h.assertTrue(ItemStack.matches(inSlot, inCap), when + "：幻纹格和能力应当一致：" + inSlot + " / " + inCap);
        h.assertTrue(inSlot.isEmpty() || inSlot != inCap, when + "：幻纹格和能力不能是同一个对象");
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void placeAndTakeWriteBack(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ItemStack e1 = eph(EphemeraType.SURGE, "fire");
        p.getInventory().setItem(0, e1.copy());
        RequiemWarframeTableMenu menu = open(p);

        menu.clicked(HOTBAR0, 0, ClickType.PICKUP, p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().isEmpty() && ItemStack.matches(wm(p).getEphemera(), e1), "放进去当场写回能力");
        inSync(h, menu, p, "放进去");
        h.assertTrue(total(p) == 1, "放进去后一共 1 件：" + total(p));

        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        h.assertTrue(ItemStack.matches(menu.getCarried(), e1) && wm(p).getEphemera().isEmpty(), "拿起来当场清掉能力");
        h.assertTrue(total(p) == 1, "拿在手上一共 1 件");
        menu.clicked(HOTBAR0 + 5, 0, ClickType.PICKUP, p);
        h.assertTrue(ItemStack.matches(p.getInventory().getItem(5), e1) && total(p) == 1, "放回背包");

        // 再放进去，关界面后还在
        menu.clicked(HOTBAR0 + 5, 0, ClickType.PICKUP, p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        close(p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e1) && p.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == 0,
                "关界面后幻纹留在格里");
        RequiemWarframeTableMenu again = open(p);
        h.assertTrue(ItemStack.matches(again.getSlot(EPH).getItem(), e1), "再打开格里还是它");
        inSync(h, again, p, "再打开");
        close(p);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void shiftInAndOutNeverDeletes(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ItemStack e1 = eph(EphemeraType.ORBIT, "ice");
        ItemStack e2 = eph(EphemeraType.COIL, "poison");
        p.getInventory().setItem(13, e1.copy());
        p.getInventory().setItem(2, e2.copy());
        RequiemWarframeTableMenu menu = open(p);
        int e1Slot = menuIndexOf(menu, p, 13);
        int e2Slot = menuIndexOf(menu, p, 2);

        menu.clicked(e1Slot, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e1) && p.getInventory().getItem(13).isEmpty(),
                "shift 点进幻纹格");
        inSync(h, menu, p, "shift 点进");

        menu.clicked(e2Slot, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e1) && ItemStack.matches(p.getInventory().getItem(2), e2),
                "幻纹格有东西时 shift 不替换、背包那件不动");
        h.assertTrue(total(p) == 2, "两件都在：" + total(p));

        menu.clicked(EPH, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(wm(p).getEphemera().isEmpty() && menu.getSlot(EPH).getItem().isEmpty()
                && p.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == 2, "shift 点回背包");

        // 背包塞满时 shift 点出去：留在格里，不丢
        menu.clicked(e2Slot, 0, ClickType.QUICK_MOVE, p);
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getItem(i).isEmpty()) {
                p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
        }
        int before = p.getInventory().countItem(KuvaLichItems.EPHEMERA.get());
        menu.clicked(EPH, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e2) && ItemStack.matches(menu.getSlot(EPH).getItem(), e2)
                && p.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == before, "背包满了 shift 点不出去，留在格里");

        // 圆石 shift 点不进幻纹格（也不是战甲模组，哪都不去）
        menu.clicked(menuIndexOf(menu, p, 0), 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(ItemStack.matches(menu.getSlot(EPH).getItem(), e2), "圆石没挤进幻纹格");
        close(p);
        h.assertTrue(total(p) == 2, "最后还是两件：" + total(p));
        h.succeed();
    }

    /** 背包第 invIndex 格在菜单里的序号 */
    private static int menuIndexOf(RequiemWarframeTableMenu menu, ServerPlayer p, int invIndex) {
        for (int i = RequiemWarframeTableMenu.INV_START; i < RequiemWarframeTableMenu.INV_END; i++) {
            if (menu.getSlot(i).container == p.getInventory() && menu.getSlot(i).getContainerSlot() == invIndex) {
                return i;
            }
        }
        throw new IllegalStateException("背包格 " + invIndex + " 不在菜单里");
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void swapStylesAndHotbarKeys(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ItemStack e1 = eph(EphemeraType.PLUME, "fire");
        ItemStack e2 = eph(EphemeraType.WING, "electricity");
        ItemStack e3 = eph(EphemeraType.VORTEX, "magnetic");
        p.getInventory().setItem(0, e1.copy());
        p.getInventory().setItem(1, e2.copy());
        p.getInventory().setItem(3, e3.copy());
        p.getInventory().setItem(4, new ItemStack(Items.DIAMOND, 5));
        RequiemWarframeTableMenu menu = open(p);

        menu.clicked(HOTBAR0, 0, ClickType.PICKUP, p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        // 拿着另一款点有东西的格子：换款
        menu.clicked(HOTBAR0 + 1, 0, ClickType.PICKUP, p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e2) && ItemStack.matches(menu.getCarried(), e1), "点击换款");
        inSync(h, menu, p, "点击换款");
        menu.clicked(HOTBAR0 + 1, 0, ClickType.PICKUP, p);
        h.assertTrue(total(p) == 3, "换款后三件都在：" + total(p));

        // 数字键和快捷栏第 3 格对调
        menu.clicked(EPH, 3, ClickType.SWAP, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e3) && ItemStack.matches(p.getInventory().getItem(3), e2),
                "数字键换款");
        inSync(h, menu, p, "数字键换款");
        // 快捷栏第 4 格是钻石：换不进来
        menu.clicked(EPH, 4, ClickType.SWAP, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e3) && p.getInventory().getItem(4).getCount() == 5,
                "数字键换不进别的东西");
        h.assertTrue(total(p) == 3, "三件都在：" + total(p));

        // Q 扔出去：能力清掉，地上多一件
        menu.clicked(EPH, 0, ClickType.THROW, p);
        List<ItemEntity> dropped = p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4.0D),
                e -> e.getItem().is(KuvaLichItems.EPHEMERA.get()));
        h.assertTrue(wm(p).getEphemera().isEmpty() && dropped.size() == 1 && ItemStack.matches(dropped.get(0).getItem(), e3),
                "扔出去的就是格里那件：" + dropped.size());
        dropped.forEach(ItemEntity::discard);
        close(p);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void rejectsOtherItems(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        p.getInventory().setItem(1, new ItemStack(KuvaLichItems.WARFRAME_COMMON_MODULE.get()));
        RequiemWarframeTableMenu menu = open(p);

        menu.clicked(HOTBAR0, 0, ClickType.PICKUP, p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(EPH).getItem().isEmpty() && menu.getCarried().getCount() == 3,
                "钻石放不进幻纹格，还在光标上");
        menu.clicked(HOTBAR0, 0, ClickType.PICKUP, p);
        menu.clicked(HOTBAR0 + 1, 0, ClickType.PICKUP, p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(EPH).getItem().isEmpty() && menu.getCarried().is(KuvaLichItems.WARFRAME_COMMON_MODULE.get()),
                "战甲模组也放不进幻纹格");
        menu.clicked(HOTBAR0 + 1, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getSlot(EPH).mayPlace(eph(EphemeraType.SURGE, "fire")), "幻纹放得进");

        // 绕过 mayPlace 直接塞进来（别的模组整理背包这类写法）：退回背包，不吞
        menu.getSlot(EPH).set(new ItemStack(Items.EMERALD, 7));
        h.assertTrue(menu.getSlot(EPH).getItem().isEmpty() && wm(p).getEphemera().isEmpty()
                && p.getInventory().countItem(Items.EMERALD) == 7, "直接塞进来的绿宝石退回背包");
        close(p);
        h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 3
                && p.getInventory().countItem(KuvaLichItems.WARFRAME_COMMON_MODULE.get()) == 1
                && p.getInventory().countItem(Items.EMERALD) == 7, "关界面后背包里的东西都在");

        // 能力里存着不是幻纹的东西（坏档）：照样装进格里、拿得出来，不在关界面时清掉
        wm(p).setEphemera(new ItemStack(Items.GOLD_INGOT, 2));
        RequiemWarframeTableMenu menu2 = open(p);
        h.assertTrue(menu2.getSlot(EPH).getItem().is(Items.GOLD_INGOT), "坏档里的东西装进格里");
        close(p);
        h.assertTrue(wm(p).getEphemera().is(Items.GOLD_INGOT) && wm(p).getEphemera().getCount() == 2, "关界面不清掉");
        RequiemWarframeTableMenu menu3 = open(p);
        menu3.clicked(EPH, 0, ClickType.QUICK_MOVE, p);
        close(p);
        h.assertTrue(wm(p).getEphemera().isEmpty() && p.getInventory().countItem(Items.GOLD_INGOT) == 2, "拿得出来");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void openCloseFiftyTimesConserves(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        WarframeModules m = wm(p);
        ItemStack[] mods = new ItemStack[8];
        for (int i = 0; i < 8; i++) {
            mods[i] = new ItemStack(KuvaLichItems.WARFRAME_COMMON_MODULE.get());
            mods[i].getOrCreateTag().putInt("kl_test_mark", i);
        }
        m.setOne(mods[0].copy());
        m.setTwo(mods[1].copy());
        m.setThree(mods[2].copy());
        m.setFour(mods[3].copy());
        m.setFive(mods[4].copy());
        m.setSix(mods[5].copy());
        m.setSeven(mods[6].copy());
        m.setEight(mods[7].copy());
        ItemStack e1 = eph(EphemeraType.SURGE, "radiation");
        EphemeraItem.setColors(e1, 0x22CCFF, 0xAA3300);
        m.setEphemera(e1.copy());

        // 只开不动：打开时装格不触发写回，关界面写回的是相同内容，版本号不变
        int version = m.getVersion();
        open(p);
        close(p);
        h.assertTrue(m.getVersion() == version, "只开关一次不该改动能力：" + version + " → " + m.getVersion());

        for (int round = 0; round < 50; round++) {
            RequiemWarframeTableMenu menu = open(p);
            inSync(h, menu, p, "第 " + round + " 次打开");
            switch (round % 4) {
                case 1 -> {
                    // 拿到光标上就关界面：原版把光标上的还进背包
                    menu.clicked(EPH, 0, ClickType.PICKUP, p);
                    close(p);
                    h.assertTrue(m.getEphemera().isEmpty() && p.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == 1,
                            "光标上拿着关界面，进了背包");
                    RequiemWarframeTableMenu back = open(p);
                    back.clicked(menuIndexOf(back, p, slotOfEphemera(p)), 0, ClickType.QUICK_MOVE, p);
                }
                case 2 -> {
                    menu.clicked(EPH, 0, ClickType.QUICK_MOVE, p);
                    menu.clicked(menuIndexOf(menu, p, slotOfEphemera(p)), 0, ClickType.QUICK_MOVE, p);
                }
                case 3 -> {
                    // 光标拿着幻纹双击空出来的幻纹格：一件堆叠上限 1，什么都收不到，放回去
                    menu.clicked(EPH, 0, ClickType.PICKUP, p);
                    menu.clicked(EPH, 0, ClickType.PICKUP_ALL, p);
                    h.assertTrue(menu.getCarried().getCount() == 1 && ItemStack.matches(menu.getCarried(), e1),
                            "双击后光标上还是那一件");
                    menu.clicked(EPH, 0, ClickType.PICKUP, p);
                }
                default -> {
                }
            }
            close(p);
            h.assertTrue(total(p) == 1 && ItemStack.matches(m.getEphemera(), e1), "第 " + round + " 次关上后幻纹还在格里、只有一件");
        }
        ItemStack[] now = {m.getOne(), m.getTwo(), m.getThree(), m.getFour(), m.getFive(), m.getSix(), m.getSeven(), m.getEight()};
        for (int i = 0; i < 8; i++) {
            h.assertTrue(ItemStack.matches(now[i], mods[i]), "第 " + i + " 格模组还在：" + now[i]);
        }
        h.assertTrue(EphemeraItem.getColor1(m.getEphemera()) == 0x22CCFF, "调过的颜色没丢");
        h.assertTrue(p.getInventory().countItem(KuvaLichItems.WARFRAME_COMMON_MODULE.get()) == 0, "背包里没多出模组");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void swapToEmptyHotbarAndOffhand(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ItemStack e1 = eph(EphemeraType.VORTEX, "electricity");
        wm(p).setEphemera(e1.copy());
        p.getInventory().setItem(5, ItemStack.EMPTY);
        p.getInventory().offhand.set(0, ItemStack.EMPTY);
        RequiemWarframeTableMenu menu = open(p);

        menu.clicked(EPH, 5, ClickType.SWAP, p);
        h.assertTrue(wm(p).getEphemera().isEmpty() && ItemStack.matches(p.getInventory().getItem(5), e1),
                "数字键换到空快捷栏格");
        inSync(h, menu, p, "数字键换出");
        menu.clicked(EPH, 5, ClickType.SWAP, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e1) && p.getInventory().getItem(5).isEmpty(), "数字键换回来");

        // F：副手（按钮 40）
        menu.clicked(EPH, Inventory.SLOT_OFFHAND, ClickType.SWAP, p);
        h.assertTrue(wm(p).getEphemera().isEmpty() && ItemStack.matches(p.getOffhandItem(), e1), "F 换到副手");
        inSync(h, menu, p, "F 换出");
        menu.clicked(EPH, Inventory.SLOT_OFFHAND, ClickType.SWAP, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e1) && p.getOffhandItem().isEmpty(), "F 换回来");

        p.getInventory().offhand.set(0, new ItemStack(Items.DIAMOND, 3));
        menu.clicked(EPH, Inventory.SLOT_OFFHAND, ClickType.SWAP, p);
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e1) && p.getOffhandItem().getCount() == 3,
                "副手拿着别的东西换不进来");
        h.assertTrue(total(p) == 1, "一件不多一件不少：" + total(p));
        close(p);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void carriedFullInventoryOnClose(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ItemStack e1 = eph(EphemeraType.SURGE, "poison");
        wm(p).setEphemera(e1.copy());
        for (int i = 0; i < 36; i++) {
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        p.getInventory().offhand.set(0, new ItemStack(Items.DIRT, 64));
        RequiemWarframeTableMenu menu = open(p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        close(p);
        List<ItemEntity> dropped = p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4.0D),
                e -> e.getItem().is(KuvaLichItems.EPHEMERA.get()));
        h.assertTrue(wm(p).getEphemera().isEmpty() && dropped.size() == 1 && ItemStack.matches(dropped.get(0).getItem(), e1),
                "背包满时光标上那件掉在脚下（原版），只有一件：" + dropped.size());
        dropped.forEach(ItemEntity::discard);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void carriedSurvivesDeathAndTravel(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ItemStack e1 = eph(EphemeraType.WING, "ice");
        wm(p).setEphemera(e1.copy());
        RequiemWarframeTableMenu menu = open(p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        h.assertTrue(ItemStack.matches(menu.getCarried(), e1) && wm(p).getEphemera().isEmpty(), "拿到光标上");

        LivingDeathEvent cancelled = new LivingDeathEvent(p, p.damageSources().generic());
        cancelled.setCanceled(true);
        RequiemMenuLogoutGuard.closeArmoryBeforeDeath(cancelled);
        h.assertTrue(p.containerMenu == menu && ItemStack.matches(menu.getCarried(), e1), "死亡被别的模组取消就不动");

        // 真死一次（死亡不掉落，看光标上那件是不是进了背包而不是掉在地上）
        MinecraftServer server = h.getLevel().getServer();
        GameRules.BooleanValue keep = h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        boolean wasKeep = keep.get();
        keep.set(true, server);
        try {
            p.setHealth(0.0F);
            p.die(p.damageSources().generic());
        } finally {
            keep.set(wasKeep, server);
        }
        List<ItemEntity> onGround = p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4.0D),
                e -> e.getItem().is(KuvaLichItems.EPHEMERA.get()));
        h.assertTrue(onGround.isEmpty(), "死亡时光标上那件不掉地上：" + onGround.size());
        h.assertTrue(p.containerMenu == p.inventoryMenu && p.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == 1,
                "死亡前先关界面、放回背包");

        ServerPlayer q = KuvaTestKit.player(h);
        ItemStack e2 = eph(EphemeraType.COIL, "fire");
        wm(q).setEphemera(e2.copy());
        RequiemWarframeTableMenu qm = open(q);
        qm.clicked(EPH, 0, ClickType.PICKUP, q);
        EntityTravelToDimensionEvent stay = new EntityTravelToDimensionEvent(q, Level.NETHER);
        stay.setCanceled(true);
        RequiemMenuLogoutGuard.closeArmoryBeforeTravel(stay);
        h.assertTrue(q.containerMenu == qm, "传送被取消就不动");
        MinecraftForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(q, Level.NETHER));
        h.assertTrue(q.containerMenu == q.inventoryMenu && q.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == 1
                && wm(q).getEphemera().isEmpty(), "跨维度前先关界面、放回背包");
        h.succeed();
    }

    private static int slotOfEphemera(ServerPlayer p) {
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getItem(i).is(KuvaLichItems.EPHEMERA.get())) {
                return i;
            }
        }
        throw new IllegalStateException("背包里没有幻纹");
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void logoutWithMenuOpen(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ItemStack e1 = eph(EphemeraType.COIL, "impact");
        ItemStack e2 = eph(EphemeraType.ORBIT, "fire");
        p.getInventory().setItem(0, e1.copy());
        p.getInventory().setItem(1, e2.copy());
        RequiemWarframeTableMenu menu = open(p);
        menu.clicked(HOTBAR0, 0, ClickType.PICKUP, p);
        menu.clicked(EPH, 0, ClickType.PICKUP, p);
        // 第二件拿在光标上时掉线
        menu.clicked(HOTBAR0 + 1, 0, ClickType.PICKUP, p);
        pers.roinflam.kuvalich.world.inventory.RequiemMenuLogoutGuard.closeRequiemMenuBeforeSave(
                new PlayerEvent.PlayerLoggedOutEvent(p));
        h.assertTrue(p.containerMenu == p.inventoryMenu, "掉线时先关了界面");
        h.assertTrue(ItemStack.matches(wm(p).getEphemera(), e1) && p.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == 1,
                "掉线：格里那件留在能力里，光标上那件回背包");

        ServerPlayer again = relog(h, p);
        h.assertTrue(ItemStack.matches(wm(again).getEphemera(), e1)
                && again.getInventory().countItem(KuvaLichItems.EPHEMERA.get()) == 1, "重进后两件都在");
        h.succeed();
    }

    /** 整份玩家数据存盘再读进一个新玩家对象（不进玩家列表） */
    private static ServerPlayer relog(GameTestHelper h, ServerPlayer p) {
        CompoundTag saved = p.saveWithoutId(new CompoundTag());
        ServerPlayer again = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), p.getGameProfile());
        again.load(saved);
        return again;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void survivesSaveRespawnAndSync(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ItemStack e1 = eph(EphemeraType.WING, "poison");
        EphemeraItem.setColors(e1, 0x123456, 0x654321);
        EphemeraItem.setGradient(e1, EphemeraItem.GRADIENT_RADIAL);
        EphemeraItem.setScale(e1, 1.5F);
        EphemeraItem.setYOffset(e1, -0.25F);
        wm(p).setEphemera(e1.copy());
        wm(p).setThree(new ItemStack(KuvaLichItems.WARFRAME_COMMON_MODULE.get()));

        ServerPlayer loaded = relog(h, p);
        ItemStack back = wm(loaded).getEphemera();
        h.assertTrue(ItemStack.matches(back, e1), "存盘读回：幻纹一模一样");
        h.assertTrue(EphemeraItem.getGradient(back) == EphemeraItem.GRADIENT_RADIAL && EphemeraItem.getScale(back) == 1.5F
                && EphemeraItem.getYOffset(back) == -0.25F && EphemeraItem.getColor2(back) == 0x654321
                && "poison".equals(EphemeraItem.getElement(back)), "外观参数和元素都在");

        // 老档没有幻纹键：读出来是空，模组不受影响
        CompoundTag old = wm(p).serializeNBT();
        old.remove("ephemera");
        WarframeModules fresh = new WarframeModules();
        fresh.deserializeNBT(old);
        h.assertTrue(fresh.getEphemera().isEmpty() && fresh.getThree().is(KuvaLichItems.WARFRAME_COMMON_MODULE.get()),
                "老档缺键 = 空");

        // 死亡重生、从末地回来
        ServerPlayer respawned = cloneOf(h, p, true);
        h.assertTrue(ItemStack.matches(wm(respawned).getEphemera(), e1), "死亡重生后还在");
        h.assertTrue(wm(respawned).getEphemera() != wm(p).getEphemera(), "重生后新旧玩家不共用同一个对象");
        ServerPlayer fromEnd = cloneOf(h, respawned, false);
        h.assertTrue(ItemStack.matches(wm(fromEnd).getEphemera(), e1), "从末地回来后还在");

        // 本人的同步包：第 9 项往返
        ItemStack[] mods = new ItemStack[8];
        java.util.Arrays.fill(mods, ItemStack.EMPTY);
        mods[2] = new ItemStack(KuvaLichItems.WARFRAME_COMMON_MODULE.get());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        WarframeModuleSyncPacket.encode(new WarframeModuleSyncPacket(mods, new int[0], new int[0], e1), buf);
        WarframeModuleSyncPacket read = WarframeModuleSyncPacket.decode(buf);
        h.assertTrue(ItemStack.matches(read.ephemera(), e1) && buf.readableBytes() == 0, "同步包带着幻纹、读完不剩字节");
        h.succeed();
    }

    private static ServerPlayer cloneOf(GameTestHelper h, ServerPlayer old, boolean wasDeath) {
        ServerLevel level = h.getLevel();
        ServerPlayer fresh = new ServerPlayer(level.getServer(), level, old.getGameProfile());
        fresh.connection = old.connection;
        fresh.restoreFrom(old, wasDeath);
        return fresh;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void giveCommandTakesElement(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        String name = p.getGameProfile().getName();
        h.assertTrue(run(h, "kuvalich ephemera give " + name + " orbit") == 1, "不写元素能给");
        h.assertTrue(run(h, "kuvalich ephemera give " + name + " coil ice Vesk Arru") == 1, "写元素和来历能给");
        h.assertTrue(run(h, "kuvalich ephemera give " + name + " coil slash") == 0, "不是赤毒武器的元素不给");
        h.assertTrue(run(h, "kuvalich ephemera give " + name + " fire") == 0, "旧的元素名款式不给");

        ItemStack orbit = ItemStack.EMPTY;
        ItemStack coil = ItemStack.EMPTY;
        int count = 0;
        for (ItemStack s : p.getInventory().items) {
            if (!s.is(KuvaLichItems.EPHEMERA.get())) {
                continue;
            }
            count++;
            if (EphemeraItem.getType(s) == EphemeraType.ORBIT) {
                orbit = s;
            } else if (EphemeraItem.getType(s) == EphemeraType.COIL) {
                coil = s;
            }
        }
        h.assertTrue(count == 2, "一共给了两件：" + count);
        h.assertTrue(EphemeraType.DEFAULT_ELEMENT.equals(EphemeraItem.getElement(orbit)) && EphemeraItem.getOrigin(orbit).isEmpty(),
                "不写元素按默认元素、没有来历");
        h.assertTrue("ice".equals(EphemeraItem.getElement(coil)) && "Vesk Arru".equals(EphemeraItem.getOrigin(coil)),
                "元素和来历照写");
        h.succeed();
    }

    private static int run(GameTestHelper h, String command) {
        MinecraftServer server = h.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        return server.getCommands().performPrefixedCommand(source, command);
    }

    @AfterBatch(batch = BATCH)
    public static void afterBatch(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }
}
