package pers.roinflam.kuvalich.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.WarframeModules;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;
import pers.roinflam.kuvalich.ephemera.EphemeraSync;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.network.packet.EphemeraTunePacket;
import pers.roinflam.kuvalich.network.packet.EphemeraTunePacket.Result;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemWarframeTableMenu;

import java.util.List;
import java.util.Random;

/**
 * 军械库调整面板的上行包：往返、只改自己格里那件、格空丢弃、夹范围、伪造数据、限频、当场写回和存盘、
 * 广播给周围的人、调过的值跟着物品走
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EphemeraTuneTests {

    private static final String BATCH = "kl_ephemera_tune";
    private static final int EPH = RequiemWarframeTableMenu.EPHEMERA_SLOT;

    private EphemeraTuneTests() {
    }

    private static WarframeModules wm(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES).orElseThrow(IllegalStateException::new);
    }

    /** 装着一件幻纹打开军械库（打开时从能力装拷贝，和真开界面一样） */
    private static RequiemWarframeTableMenu openWith(ServerPlayer p, ItemStack eph, int containerId) {
        wm(p).setEphemera(eph.copy());
        RequiemWarframeTableMenu menu = new RequiemWarframeTableMenu(containerId, p.getInventory(), p.level(),
                p.blockPosition());
        p.containerMenu = menu;
        return menu;
    }

    private static ItemStack eph(EphemeraType type, String element) {
        return EphemeraItem.create(type, element, "Vesk Arru");
    }

    private static EphemeraTunePacket set(RequiemWarframeTableMenu menu, int c1, int c2, int gradient, float scale,
                                          float yOffset) {
        return new EphemeraTunePacket(menu.containerId, EphemeraTunePacket.SET, c1, c2, gradient, scale, yOffset);
    }

    private static EphemeraTunePacket roundTrip(EphemeraTunePacket pkt) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        EphemeraTunePacket.encode(pkt, buf);
        EphemeraTunePacket back = EphemeraTunePacket.decode(buf);
        if (buf.readableBytes() != 0) {
            throw new IllegalStateException("decode left " + buf.readableBytes() + " bytes");
        }
        return back;
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void packetRoundTripAndGarbage(GameTestHelper h) {
        EphemeraTunePacket pkt = new EphemeraTunePacket(7, EphemeraTunePacket.SET, 0x12AB34, 0xFFEE00,
                EphemeraItem.GRADIENT_FLOW, 1.35F, -0.2F);
        EphemeraTunePacket back = roundTrip(pkt);
        h.assertTrue(back.containerId == 7 && back.action == EphemeraTunePacket.SET && back.color1 == 0x12AB34
                && back.color2 == 0xFFEE00 && back.gradient == EphemeraItem.GRADIENT_FLOW && back.scale == 1.35F
                && back.yOffset == -0.2F, "往返一致");
        EphemeraTunePacket reset = roundTrip(EphemeraTunePacket.reset(3));
        h.assertTrue(reset.action == EphemeraTunePacket.RESET && reset.containerId == 3, "恢复默认往返一致");

        // 截断：菜单号和动作后面就没了
        FriendlyByteBuf cut = new FriendlyByteBuf(Unpooled.buffer());
        cut.writeVarInt(7);
        cut.writeByte(EphemeraTunePacket.SET);
        cut.writeInt(0xFF0000);
        EphemeraTunePacket bad = EphemeraTunePacket.decode(cut);
        h.assertTrue(bad.action != EphemeraTunePacket.SET && bad.action != EphemeraTunePacket.RESET, "截断的包读成无效动作");

        // 菜单号是坏的变长整数
        FriendlyByteBuf junk = new FriendlyByteBuf(Unpooled.buffer());
        for (int i = 0; i < 8; i++) {
            junk.writeByte(0xFF);
        }
        EphemeraTunePacket bad2 = EphemeraTunePacket.decode(junk);
        h.assertTrue(bad2.action != EphemeraTunePacket.SET && bad2.action != EphemeraTunePacket.RESET, "乱码不抛、读成无效动作");

        ServerPlayer p = KuvaTestKit.player(h);
        RequiemWarframeTableMenu menu = openWith(p, eph(EphemeraType.ORBIT, "fire"), 7);
        h.assertTrue(EphemeraTunePacket.apply(p, bad) == Result.BAD_ACTION, "无效动作丢掉");
        h.assertTrue(EphemeraTunePacket.apply(p, new EphemeraTunePacket(7, 9, 0, 0, 0, 1, 0)) == Result.BAD_ACTION,
                "不认识的动作丢掉");
        h.assertTrue(!EphemeraItem.isTuned(menu.ephemeraHandler.getStackInSlot(0)), "丢掉的包不碰物品");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void onlyOwnSlotChanges(GameTestHelper h) {
        ServerPlayer a = KuvaTestKit.player(h);
        ServerPlayer b = KuvaTestKit.player(h);
        RequiemWarframeTableMenu ma = openWith(a, eph(EphemeraType.WING, "ice"), 11);
        RequiemWarframeTableMenu mb = openWith(b, eph(EphemeraType.WING, "ice"), 11);
        ItemStack bBefore = mb.ephemeraHandler.getStackInSlot(0).copy();

        h.assertTrue(EphemeraTunePacket.apply(a, set(ma, 0xFF00AA, 0x00AAFF, EphemeraItem.GRADIENT_VERTICAL, 1.5F, 0.2F))
                == Result.OK, "改自己的");
        ItemStack aNow = ma.ephemeraHandler.getStackInSlot(0);
        h.assertTrue(EphemeraItem.getColor1(aNow) == 0xFF00AA && EphemeraItem.getColor2(aNow) == 0x00AAFF
                && EphemeraItem.getGradient(aNow) == EphemeraItem.GRADIENT_VERTICAL && EphemeraItem.getScale(aNow) == 1.5F
                && EphemeraItem.getYOffset(aNow) == 0.2F, "格里那件改了：" + aNow.getTag());
        h.assertTrue(ItemStack.matches(mb.ephemeraHandler.getStackInSlot(0), bBefore)
                && ItemStack.matches(wm(b).getEphemera(), bBefore), "别人的格子和能力都没动");
        h.assertTrue("wing".equals(EphemeraItem.getType(aNow).id()) && "ice".equals(EphemeraItem.getElement(aNow))
                && "Vesk Arru".equals(EphemeraItem.getOrigin(aNow)), "款式、元素、来历不动");

        // 菜单编号对不上（界面已经换了一个）、没开军械库
        h.assertTrue(EphemeraTunePacket.apply(a, new EphemeraTunePacket(12, EphemeraTunePacket.SET, 0x123456, 0x123456,
                0, 1.0F, 0.0F)) == Result.NO_MENU, "菜单编号不符丢掉");
        a.containerMenu = a.inventoryMenu;
        h.assertTrue(EphemeraTunePacket.apply(a, set(ma, 0x123456, 0x123456, 0, 1.0F, 0.0F)) == Result.NO_MENU,
                "没开军械库丢掉");
        h.assertTrue(EphemeraItem.getColor1(wm(a).getEphemera()) == 0xFF00AA, "丢掉的包不改能力里那件");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void emptyOrForeignSlotDropped(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemWarframeTableMenu menu = openWith(p, ItemStack.EMPTY, 5);
        h.assertTrue(EphemeraTunePacket.apply(p, set(menu, 0xFF0000, 0xFF0000, 1, 1.2F, 0.1F)) == Result.NO_EPHEMERA,
                "格空丢掉");
        h.assertTrue(menu.ephemeraHandler.getStackInSlot(0).isEmpty() && wm(p).getEphemera().isEmpty(),
                "格空时什么都不多出来");

        // 坏档：格里是别的东西 / 款式查不到的幻纹
        menu.ephemeraHandler.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        h.assertTrue(EphemeraTunePacket.apply(p, set(menu, 0xFF0000, 0xFF0000, 1, 1.2F, 0.1F)) == Result.NO_EPHEMERA,
                "不是幻纹丢掉");
        h.assertTrue(!menu.ephemeraHandler.getStackInSlot(0).hasTag(), "钻石没被写上 NBT");
        ItemStack stale = new ItemStack(KuvaLichItems.EPHEMERA.get());
        stale.getOrCreateTag().putString(EphemeraItem.TAG_TYPE, "fire");
        menu.ephemeraHandler.setStackInSlot(0, stale);
        h.assertTrue(EphemeraTunePacket.apply(p, EphemeraTunePacket.reset(5)) == Result.NO_EPHEMERA, "旧款式 id 丢掉");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void valuesClampedAndNbtBounded(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemWarframeTableMenu menu = openWith(p, eph(EphemeraType.COIL, "poison"), 9);
        int baseKeys = menu.ephemeraHandler.getStackInSlot(0).getTag().size();

        EphemeraTunePacket.apply(p, set(menu, 0x000000, 0xFF123456, 99, 50.0F, -3.0F));
        ItemStack st = menu.ephemeraHandler.getStackInSlot(0);
        h.assertTrue(EphemeraItem.getColor1(st) == 0x404040, "全黑提亮到下限：" + Integer.toHexString(EphemeraItem.getColor1(st)));
        h.assertTrue(st.getTag().getInt(EphemeraItem.TAG_COLOR2) == 0x123456, "透明度那个字节去掉");
        h.assertTrue(!st.getTag().contains(EphemeraItem.TAG_GRADIENT), "非法渐变按关闭（缺省就不存键）");
        h.assertTrue(st.getTag().getFloat(EphemeraItem.TAG_SCALE) == EphemeraItem.SCALE_MAX
                && st.getTag().getFloat(EphemeraItem.TAG_Y_OFFSET) == EphemeraItem.Y_OFFSET_MIN, "大小、高度夹到范围");

        EphemeraTunePacket.apply(p, set(menu, 0x00FF00, 0x00FF00, -7, Float.NaN, Float.POSITIVE_INFINITY));
        st = menu.ephemeraHandler.getStackInSlot(0);
        h.assertTrue(!st.getTag().contains(EphemeraItem.TAG_SCALE) && !st.getTag().contains(EphemeraItem.TAG_Y_OFFSET)
                && EphemeraItem.getScale(st) == 1.0F && EphemeraItem.getYOffset(st) == 0.0F, "NaN、无穷落回默认");
        EphemeraTunePacket.apply(p, set(menu, 0x00FF00, 0x00FF00, 2, 1.23456F, 0.33333F));
        st = menu.ephemeraHandler.getStackInSlot(0);
        h.assertTrue(EphemeraItem.getScale(st) == 1.23F && EphemeraItem.getYOffset(st) == 0.33F, "两位小数");

        // 伪造：随机乱填几百次、包尾巴再拖一大截垃圾，物品上的键数和体积都不涨
        Random rnd = new Random(1113L);
        for (int i = 0; i < 300; i++) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeVarInt(9);
            buf.writeByte(rnd.nextInt(3) - 1);
            buf.writeInt(rnd.nextInt());
            buf.writeInt(rnd.nextInt());
            buf.writeByte(rnd.nextInt(256));
            buf.writeFloat(Float.intBitsToFloat(rnd.nextInt()));
            buf.writeFloat(Float.intBitsToFloat(rnd.nextInt()));
            buf.writeBytes(new byte[4096]);
            EphemeraTunePacket.apply(p, EphemeraTunePacket.decode(buf));
        }
        st = menu.ephemeraHandler.getStackInSlot(0);
        CompoundTag tag = st.getTag();
        h.assertTrue(tag.size() <= baseKeys + 5, "最多多出五个键：" + tag.getAllKeys());
        h.assertTrue(tag.toString().length() < 256, "NBT 没膨胀：" + tag);
        h.assertTrue(EphemeraLook.of(st).type().equals("coil"), "款式还在");

        // 调回默认：和没调过的那件完全一样
        EphemeraTunePacket.apply(p, EphemeraTunePacket.reset(9));
        h.assertTrue(ItemStack.matches(menu.ephemeraHandler.getStackInSlot(0), eph(EphemeraType.COIL, "poison")),
                "恢复默认后和新的一样");
        int def = EphemeraType.defaultColor("poison");
        EphemeraTunePacket.apply(p, set(menu, 0x123456, 0x123456, 1, 1.4F, 0.1F));
        h.assertTrue(EphemeraTunePacket.apply(p, set(menu, def, def, 0, 1.0F, 0.0F)) == Result.OK
                && ItemStack.matches(menu.ephemeraHandler.getStackInSlot(0), eph(EphemeraType.COIL, "poison")),
                "手动调回默认值也不留键");
        h.assertTrue(EphemeraTunePacket.apply(p, set(menu, def, def, 0, 1.0F, 0.0F)) == Result.UNCHANGED, "没变不写回");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void rateLimited(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemWarframeTableMenu menu = openWith(p, eph(EphemeraType.SURGE, "fire"), 4);
        long t = 5_000_000L;
        for (int i = 0; i < EphemeraTunePacket.MAX_PER_SECOND; i++) {
            Result r = EphemeraTunePacket.receive(p, set(menu, 0xFF0000 + i, 0xFFFFFF, 0, 1.0F, 0.0F), t + i * 10L);
            h.assertTrue(r == Result.OK, "前 4 个收下：" + r);
        }
        h.assertTrue(EphemeraTunePacket.receive(p, set(menu, 0xABCDEF, 0xFFFFFF, 0, 1.0F, 0.0F), t + 500L)
                == Result.RATE_LIMITED, "一秒内第 5 个被限频");
        h.assertTrue(EphemeraItem.getColor1(menu.ephemeraHandler.getStackInSlot(0)) == 0xFF0003, "限频的那个没改物品");
        h.assertTrue(EphemeraTunePacket.receive(p, set(menu, 0xABCDEF, 0xFFFFFF, 0, 1.0F, 0.0F), t + 1000L)
                == Result.OK, "过了一秒又能改");
        ServerPlayer q = KuvaTestKit.player(h);
        RequiemWarframeTableMenu mq = openWith(q, eph(EphemeraType.SURGE, "fire"), 4);
        h.assertTrue(EphemeraTunePacket.receive(q, set(mq, 0xABCDEF, 0xFFFFFF, 0, 1.0F, 0.0F), t + 600L) == Result.OK,
                "限频按人算");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void writesBackAndPersists(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemWarframeTableMenu menu = openWith(p, eph(EphemeraType.PLUME, "electricity"), 2);
        int v0 = wm(p).getVersion();
        EphemeraTunePacket.apply(p, set(menu, 0xFFD700, 0x8800FF, EphemeraItem.GRADIENT_RADIAL, 0.75F, -0.15F));
        ItemStack inCap = wm(p).getEphemera();
        h.assertTrue(ItemStack.matches(inCap, menu.ephemeraHandler.getStackInSlot(0)), "不关界面，能力里已经是新的");
        h.assertTrue(inCap != menu.ephemeraHandler.getStackInSlot(0), "能力和格子不共用同一个对象");
        h.assertTrue(wm(p).getVersion() != v0, "版本号变了，本人的同步包会发");

        // 不经过关界面那次写回：直接存盘读回
        CompoundTag saved = p.saveWithoutId(new CompoundTag());
        ServerLevel level = h.getLevel();
        ServerPlayer again = new ServerPlayer(level.getServer(), level, p.getGameProfile());
        again.load(saved);
        ItemStack back = wm(again).getEphemera();
        h.assertTrue(EphemeraItem.getColor1(back) == 0xFFD700 && EphemeraItem.getColor2(back) == 0x8800FF
                && EphemeraItem.getGradient(back) == EphemeraItem.GRADIENT_RADIAL && EphemeraItem.getScale(back) == 0.75F
                && EphemeraItem.getYOffset(back) == -0.15F, "存盘读回还在：" + back.getTag());

        // 死亡重生（关界面那次写回不生效的路径）带过去
        ServerPlayer respawned = new ServerPlayer(level.getServer(), level, p.getGameProfile());
        respawned.connection = p.connection;
        respawned.restoreFrom(p, true);
        h.assertTrue(ItemStack.matches(wm(respawned).getEphemera(), inCap), "重生后还是调过的");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void watchersGetNewLook(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemWarframeTableMenu menu = openWith(p, eph(EphemeraType.VORTEX, "radiation"), 6);
        EphemeraSync.poll(p);
        int s0 = EphemeraSync.sentCount();
        EphemeraTunePacket.apply(p, set(menu, 0x33CCFF, 0xFF3366, EphemeraItem.GRADIENT_FLOW, 1.1F, 0.05F));
        h.assertTrue(EphemeraSync.sentCount() == s0 + 1, "改完马上广播一次");
        EphemeraLook look = EphemeraSync.lookOf(p);
        h.assertTrue(look.color1() == 0x33CCFF && look.color2() == 0xFF3366 && look.gradient() == EphemeraItem.GRADIENT_FLOW
                && look.scale() == 1.1F && look.yOffset() == 0.05F, "发出去的是新外观：" + look);
        h.assertTrue(!EphemeraSync.poll(p), "下一轮轮询不重发");
        EphemeraTunePacket.apply(p, set(menu, 0x33CCFF, 0xFF3366, EphemeraItem.GRADIENT_FLOW, 1.1F, 0.05F));
        h.assertTrue(EphemeraSync.sentCount() == s0 + 1, "没变不发");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH)
    public static void tunedValuesTravelWithItem(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemWarframeTableMenu menu = openWith(p, eph(EphemeraType.ORBIT, "magnetic"), 8);
        EphemeraTunePacket.apply(p, set(menu, 0xEE4411, 0x11EE44, EphemeraItem.GRADIENT_VERTICAL, 1.8F, 0.4F));
        ItemStack tuned = menu.ephemeraHandler.getStackInSlot(0).copy();
        List<Component> lines = EphemeraItem.tooltipLines(tuned);
        h.assertTrue(lines.stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && "item.kuvalich.ephemera.tuned".equals(tc.getKey())), "提示框有「外观已调整」");
        h.assertTrue(EphemeraItem.tooltipLines(eph(EphemeraType.ORBIT, "magnetic")).stream()
                .noneMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                        && "item.kuvalich.ephemera.tuned".equals(tc.getKey())), "没调过的没有这一行");

        // shift 点回背包：值跟着物品走
        menu.clicked(EPH, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(menu.ephemeraHandler.getStackInSlot(0).isEmpty() && wm(p).getEphemera().isEmpty(), "取出来了");
        int slot = -1;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(KuvaLichItems.EPHEMERA.get())) {
                slot = i;
            }
        }
        h.assertTrue(slot >= 0 && ItemStack.matches(p.getInventory().getItem(slot), tuned), "背包里那件带着调过的值");
        h.assertTrue(EphemeraTunePacket.apply(p, set(menu, 0x000001, 0x000001, 0, 1.0F, 0.0F)) == Result.NO_EPHEMERA,
                "取出后格空，再发包丢掉");
        h.assertTrue(ItemStack.matches(p.getInventory().getItem(slot), tuned), "背包里那件不受影响");

        // 放回去
        int menuIdx = -1;
        for (int i = RequiemWarframeTableMenu.INV_START; i < RequiemWarframeTableMenu.INV_END; i++) {
            if (menu.getSlot(i).container == p.getInventory() && menu.getSlot(i).getContainerSlot() == slot) {
                menuIdx = i;
            }
        }
        menu.clicked(menuIdx, 0, ClickType.QUICK_MOVE, p);
        h.assertTrue(ItemStack.matches(menu.ephemeraHandler.getStackInSlot(0), tuned)
                && ItemStack.matches(wm(p).getEphemera(), tuned), "放回去值还在");
        h.assertTrue(EphemeraSync.lookOf(p).color1() == 0xEE4411 && EphemeraSync.lookOf(p).scale() == 1.8F,
                "别人看到的也是调过的");
        h.succeed();
    }
}
