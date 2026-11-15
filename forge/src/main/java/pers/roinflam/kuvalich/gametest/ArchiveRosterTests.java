package pers.roinflam.kuvalich.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.nemesis.AllyRoster;
import pers.roinflam.kuvalich.nemesis.LichArchiveService;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.nemesis.WaitReason;
import pers.roinflam.kuvalich.network.packet.AllyCommandPacket;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 档案里的盟友名册（C4）：包的往返、名册操作只认自己的名册、按人限频 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchiveRosterTests {

    private static final String BATCH = "kl_archive_roster";

    private ArchiveRosterTests() {
    }

    private static CompoundTag entry(int gen, String name) {
        CompoundTag t = new CompoundTag();
        t.putLong(AllyRoster.SEED, 0x0102_0304_0506_0708L + gen);
        t.putString(AllyRoster.NAME, name);
        t.putInt(AllyRoster.GENERATION, gen);
        t.putInt(AllyRoster.RANK, gen % 5 + 1);
        t.putInt(AllyRoster.WEAPON, 0);
        t.putInt(AllyRoster.WEAPON_LEVEL, 10);
        t.putString(AllyRoster.WEAPON_TYPE, "fire");
        t.putString(AllyRoster.EPHEMERA, gen == 7 ? "plume" : "");
        t.putLong(AllyRoster.CONVERTED_AT, 0L);
        return t;
    }

    private static void giveRoster(ServerPlayer p, CompoundTag... entries) {
        PlayerLedger.setAllies(p, new ArrayList<>(List.of(entries)));
    }

    private static byte[] encode(LichArchivePacket.Data d) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        LichArchivePacket.encode(new LichArchivePacket(d), buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    private static LichArchivePacket.Data decode(byte[] bytes) {
        return LichArchivePacket.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes))).data();
    }

    private static LichArchivePacket.Data base(LichStatePacket st) {
        return new LichArchivePacket.Data(st, "", "", ItemStack.EMPTY, 0, 0, LichArchivePacket.Stats.NONE, 0, 0, 0, 0,
                0, 0, 0, new int[]{-1, -1, -1},
                new LichArchivePacket.Card[]{LichArchivePacket.Card.EMPTY, LichArchivePacket.Card.EMPTY,
                        LichArchivePacket.Card.EMPTY},
                new int[0], 1, List.of(new ItemStack(Items.DIAMOND)), "Varokk Ruhl", 3);
    }

    /** 状态包末尾的幻纹、档案包末尾的名册 / 上限 / 待领取都读得回来，再编一遍字节一样；名册超过包的上限时截断 */
    @GameTest(template = "empty", batch = BATCH)
    public static void rosterPacketRoundTrip(GameTestHelper h) {
        LichStatePacket st = new LichStatePacket(LichState.STALKING, WaitReason.NONE, "Drokag Tuzz", 77, 2, 5, 300, 1,
                2, 0, 4, false, "fire");
        String longName = "Vorrakthuun Ekmarravoss Thal";
        List<LichArchivePacket.Ally> roster = List.of(
                new LichArchivePacket.Ally("Teshu Mak", 2, 3, -5, 0, "poison", "", false, false),
                new LichArchivePacket.Ally(longName, 5, 9, 123456, -1, "", "poison", true, true),
                new LichArchivePacket.Ally("Ozzen Varn", 11, 1, 0, 3, "ice", "", false, false));
        LichArchivePacket.Data d = base(st).withRoster(roster, 5, 37);
        byte[] bytes = encode(d);
        LichArchivePacket.Data d2 = decode(bytes);
        h.assertTrue("fire".equals(d2.state().ephemera), "内嵌状态包的幻纹应当读回 fire");
        h.assertTrue(d2.lastFate() == 3 && "Varokk Ruhl".equals(d2.lastName()), "上一只的结局 3（感化）应当原样读回");
        h.assertTrue(d2.confiscatedCount() == 1 && d2.confiscated().size() == 1, "名册写在没收物品后面，不能把它们读乱");
        h.assertTrue(d2.roster().size() == 3 && d2.rosterCap() == 5 && d2.inboxCount() == 37, "名册条数、上限、待领取");
        LichArchivePacket.Ally a = d2.roster().get(1);
        h.assertTrue(a.name().equals(longName.substring(0, LichStatePacket.MAX_NAME)), "过长的名字按 24 截断");
        h.assertTrue(a.generation() == 5 && a.rank() == 9 && a.seed() == 123456 && a.weapon() == -1,
                "代数、等级、种子、没有武器读回 -1");
        h.assertTrue(a.element().isEmpty() && "poison".equals(a.ephemera()) && a.active() && a.present(),
                "元素、幻纹、出战、在场");
        LichArchivePacket.Ally b = d2.roster().get(0);
        h.assertTrue("Teshu Mak".equals(b.name()) && b.seed() == -5 && "poison".equals(b.element())
                && !b.active() && !b.present(), "第一只原样读回");
        h.assertTrue(Arrays.equals(bytes, encode(d2)), "读回来再编一遍，字节应当一样");

        // 空名册
        LichArchivePacket.Data empty = decode(encode(base(LichStatePacket.EMPTY)));
        h.assertTrue(empty.roster().isEmpty() && empty.rosterCap() == 3 && empty.inboxCount() == 0
                && empty.state().ephemera.isEmpty(), "空名册、默认上限、没有待领取、不带幻纹");

        // 超过上限
        List<LichArchivePacket.Ally> many = new ArrayList<>();
        for (int i = 0; i < LichArchivePacket.MAX_ALLIES + 4; i++) {
            many.add(new LichArchivePacket.Ally("A" + i, i, 1, i, 0, "", "", false, false));
        }
        LichArchivePacket.Data capped = decode(encode(base(st).withRoster(many, 8, 0)));
        h.assertTrue(capped.roster().size() == LichArchivePacket.MAX_ALLIES, "名册按包的上限截断");

        // 上行包
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AllyCommandPacket.encode(new AllyCommandPacket(AllyCommandPacket.RELEASE, 300), buf);
        h.assertTrue(buf.readableBytes() == 3, "名册操作包：一个字节的动作 + 代数");
        h.succeed();
    }

    /** 档案按名册组：出战的没指定时是最新感化的那只，待领取按件数算 */
    @GameTest(template = "empty", batch = BATCH)
    public static void archiveBuildsRoster(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        giveRoster(p, entry(4, "Teshu Mak"), entry(7, "Ozzen Varn"), entry(9, "Kril Dathe"));
        PlayerLedger.setActiveAlly(p, -1);
        PlayerLedger.setInbox(p, List.of(new ItemStack(Items.ARROW, 20), new ItemStack(Items.DIAMOND, 3)));
        LichArchivePacket.Data d = LichArchiveService.build(p);
        h.assertTrue(d != null, "应当组得出档案");
        h.assertTrue(d.roster().size() == 3 && d.rosterCap() == AllyRoster.capacity(), "名册三只、上限照配置");
        h.assertTrue(d.inboxCount() == 23, "待领取按件数算，实际 " + d.inboxCount());
        h.assertTrue(!d.roster().get(0).active() && !d.roster().get(1).active() && d.roster().get(2).active(),
                "没指定出战时出战的是最新感化的那只");
        h.assertTrue("plume".equals(d.roster().get(1).ephemera()) && "fire".equals(d.roster().get(1).element()),
                "幻纹、元素照名册");
        PlayerLedger.setActiveAlly(p, 4);
        d = LichArchiveService.build(p);
        h.assertTrue(d.roster().get(0).active() && !d.roster().get(2).active(), "指定出战后标在指定的那只上");
        h.assertTrue(!d.roster().get(0).present(), "没召出来的不在场");
        PlayerLedger.setInbox(p, List.of());
        h.succeed();
    }

    /** 只能动自己名册里有的代数；设为出战、遣散照名册的规矩走；不认识的动作不做事 */
    @GameTest(template = "empty", batch = BATCH)
    public static void commandOwnRosterOnly(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ServerPlayer other = KuvaTestKit.player(h);
        giveRoster(p, entry(4, "Teshu Mak"), entry(7, "Ozzen Varn"), entry(9, "Kril Dathe"));
        giveRoster(other, entry(12, "Juun Oskar"), entry(4, "Mavek Thol"));
        PlayerLedger.setActiveAlly(other, 12);

        h.assertTrue(AllyCommandPacket.apply(p, AllyCommandPacket.SET_ACTIVE, 7) == AllyCommandPacket.Result.OK,
                "名册里有的代数能设为出战");
        h.assertTrue(PlayerLedger.getActiveAlly(p) == 7, "出战改成 7");
        h.assertTrue(AllyCommandPacket.apply(p, AllyCommandPacket.SET_ACTIVE, 7) == AllyCommandPacket.Result.UNCHANGED,
                "已经是出战的再设一次不做事");
        h.assertTrue(AllyCommandPacket.apply(p, AllyCommandPacket.SET_ACTIVE, 12)
                == AllyCommandPacket.Result.NOT_IN_ROSTER, "别人名册里的代数不认");
        h.assertTrue(AllyCommandPacket.apply(p, AllyCommandPacket.SET_ACTIVE, -3)
                == AllyCommandPacket.Result.NOT_IN_ROSTER, "负的代数不认");
        h.assertTrue(PlayerLedger.getActiveAlly(p) == 7, "不认的操作不能改出战");
        h.assertTrue(AllyCommandPacket.apply(p, 9, 7) == AllyCommandPacket.Result.BAD_ACTION, "不认识的动作不做事");

        h.assertTrue(AllyCommandPacket.apply(p, AllyCommandPacket.RELEASE, 7) == AllyCommandPacket.Result.OK,
                "遣散名册里的那只");
        List<CompoundTag> left = PlayerLedger.getAllies(p);
        h.assertTrue(left.size() == 2 && left.stream().noneMatch(t -> t.getInt(AllyRoster.GENERATION) == 7),
                "遣散后名册里没有 7");
        h.assertTrue(PlayerLedger.getActiveAlly(p) == -1, "遣散的是出战的那只，出战改回默认");
        h.assertTrue(AllyCommandPacket.apply(p, AllyCommandPacket.RELEASE, 7) == AllyCommandPacket.Result.NOT_IN_ROSTER,
                "同一只不能遣散两次");
        h.assertTrue(AllyCommandPacket.apply(p, AllyCommandPacket.RELEASE, 4) == AllyCommandPacket.Result.OK,
                "遣散自己的 4");
        List<CompoundTag> others = PlayerLedger.getAllies(other);
        h.assertTrue(others.size() == 2 && PlayerLedger.getActiveAlly(other) == 12,
                "别人名册里同代数的那只不受影响");
        h.succeed();
    }

    /** 每人每秒最多 5 个，多的丢掉、不执行；过了一秒重新算；两个人各算各的 */
    @GameTest(template = "empty", batch = BATCH)
    public static void commandRateLimited(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ServerPlayer other = KuvaTestKit.player(h);
        giveRoster(p, entry(4, "Teshu Mak"), entry(7, "Ozzen Varn"));
        giveRoster(other, entry(4, "Mavek Thol"));
        AllyCommandPacket.resetRateLimit();
        long t = 1_000_000L;
        for (int i = 0; i < AllyCommandPacket.MAX_PER_SECOND; i++) {
            int gen = i % 2 == 0 ? 4 : 7;
            AllyCommandPacket.Result r = AllyCommandPacket.receive(p, AllyCommandPacket.SET_ACTIVE, gen, t + i * 100L);
            h.assertTrue(r == AllyCommandPacket.Result.OK, "一秒内前 5 个照常执行，第 " + (i + 1) + " 个是 " + r);
        }
        h.assertTrue(PlayerLedger.getActiveAlly(p) == 4, "第 5 个把出战设成 4");
        h.assertTrue(AllyCommandPacket.receive(p, AllyCommandPacket.RELEASE, 7, t + 900L)
                == AllyCommandPacket.Result.RATE_LIMITED, "第 6 个被限频");
        h.assertTrue(PlayerLedger.getAllies(p).size() == 2, "被限频的遣散不能执行");
        h.assertTrue(AllyCommandPacket.receive(other, AllyCommandPacket.SET_ACTIVE, 4, t + 950L)
                == AllyCommandPacket.Result.OK, "别的玩家不受这个人的限频影响");
        h.assertTrue(AllyCommandPacket.receive(p, AllyCommandPacket.RELEASE, 7, t + 1000L)
                == AllyCommandPacket.Result.OK, "过了一秒重新放行");
        h.assertTrue(PlayerLedger.getAllies(p).size() == 1, "放行后的遣散执行了");
        AllyCommandPacket.resetRateLimit();
        h.succeed();
    }

    @AfterBatch(batch = BATCH)
    public static void afterBatch(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }
}
