package pers.roinflam.kuvalich.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.comm.LichLook;
import pers.roinflam.kuvalich.mercy.KuvaStateKind;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.WaitReason;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.network.packet.KuvaCommPacket;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichArchiveRequestPacket;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.network.packet.MercyRequestPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;
import java.util.List;

/**
 * 自动化测试本身能不能跑起来：空结构载得进来，测试玩家进得了服
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmokeTests {

    private static final String PLAYER_BATCH = "kl_smoke_player";

    private SmokeTests() {
    }

    /** 空结构载入后可以在里面放方块、读方块 */
    @GameTest(template = "empty")
    public static void emptyStructureLoads(GameTestHelper h) {
        KuvaTestKit.floor(h, 1, Blocks.STONE);
        h.assertBlock(KuvaTestKit.center(h), b -> b == Blocks.STONE, "结构中心应当是石头");
        h.assertBlock(KuvaTestKit.center(h).offset(1, 0, 1), b -> b == Blocks.STONE, "3×3 的角上应当是石头");
        h.succeed();
    }

    /** 测试玩家真的进了玩家列表、是生存模式，收尾后又被移走 */
    @GameTest(template = "empty", batch = PLAYER_BATCH)
    public static void mockPlayerJoins(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        ServerLevel level = h.getLevel();
        h.assertTrue(level.getServer().getPlayerList().getPlayers().contains(p), "测试玩家应当在玩家列表里");
        h.assertTrue(!p.isCreative(), "测试玩家不应当是创造模式");
        h.assertTrue(!p.isSpectator(), "测试玩家不应当是旁观模式");
        KuvaTestKit.afterBatch(level);
        h.assertTrue(!level.getServer().getPlayerList().getPlayers().contains(p), "收尾后测试玩家应当已被移出");
        h.succeed();
    }

    /** 第二期新增的 5 个包和扩充过的解密进度包：用样例值编码再解码，逐字段比，并且再编码一遍字节必须一致 */
    @GameTest(template = "empty")
    public static void packetRoundTrip(GameTestHelper h) {
        // ---- 玄骸状态
        LichStatePacket state = new LichStatePacket(LichState.PRESENT, WaitReason.BUSY, "Drokag Tuzz", -123456, 7, 4,
                640, 2, 3, 95, 5, true);
        FriendlyByteBuf buf = newBuf();
        LichStatePacket.encode(state, buf);
        byte[] stateBytes = bytes(buf);
        LichStatePacket state2 = LichStatePacket.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(stateBytes)));
        h.assertTrue(state2.state == LichState.PRESENT && state2.waitReason == WaitReason.BUSY, "状态包：状态和等待原因");
        h.assertTrue("Drokag Tuzz".equals(state2.name) && state2.seed == -123456, "状态包：名字和种子");
        h.assertTrue(state2.weapon == 7 && state2.rank == 4 && state2.ragePermille == 640, "状态包：武器、等级、怒气");
        h.assertTrue(state2.tier == 2 && state2.clues == 3 && state2.cooldownSec == 95, "状态包：档位、线索、冷却");
        h.assertTrue(state2.generation == 5 && state2.paused, "状态包：代数和暂停");
        LichStatePacket noWeapon = new LichStatePacket(LichState.UNBORN, WaitReason.NONE, "", 0, -1, 0, 0, 0, 0, 0, 0, false);
        buf = newBuf();
        LichStatePacket.encode(noWeapon, buf);
        h.assertTrue(LichStatePacket.decode(buf).weapon == -1, "状态包：没有武器应当读回 -1");
        sameBytes(h, "状态包", stateBytes, bytes(encodedState(state2)));

        // ---- 档案请求（空包）和怜悯之击请求
        buf = newBuf();
        LichArchiveRequestPacket.encode(new LichArchiveRequestPacket(), buf);
        h.assertTrue(buf.readableBytes() == 0, "档案请求应当是空包");
        buf = newBuf();
        MercyRequestPacket.encode(new MercyRequestPacket(123456), buf);
        h.assertTrue(MercyRequestPacket.decode(buf).entityId() == 123456, "处决请求：实体 id");

        // ---- 档案
        ItemStack heavy = new ItemStack(Items.DIAMOND_PICKAXE);
        CompoundTag bigTag = new CompoundTag();
        bigTag.putString("pad", "x".repeat(3000));
        heavy.setTag(bigTag);
        ItemStack light = new ItemStack(Items.IRON_AXE);
        CompoundTag smallTag = new CompoundTag();
        smallTag.putInt("k", 7);
        light.setTag(smallTag);
        LichArchivePacket.Data data = new LichArchivePacket.Data(state, "minecraft:plains", "minecraft:overworld",
                new ItemStack(Items.IRON_SWORD), 12, 34, 0x21, 30, 60, 450, 55, 18, 0b0101, 6,
                2, 17, -1, new int[]{3, -1, 6},
                new LichArchivePacket.Card[]{new LichArchivePacket.Card(3, 1, 3), LichArchivePacket.Card.EMPTY,
                        new LichArchivePacket.Card(6, 0, 3)},
                new int[]{RequiemCard.packAttempt(new int[]{3, 1, 6}, new byte[]{1, 2, 0}, true), 77},
                3, List.of(heavy, light, new ItemStack(Items.GOLDEN_APPLE, 3)), "Old Name", 1);
        buf = newBuf();
        LichArchivePacket.encode(new LichArchivePacket(data), buf);
        byte[] archiveBytes = bytes(buf);
        LichArchivePacket.Data d2 = LichArchivePacket.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(archiveBytes))).data();
        h.assertTrue("Drokag Tuzz".equals(d2.state().name) && d2.state().rank == 4, "档案：内嵌的状态");
        h.assertTrue("minecraft:plains".equals(d2.birthBiome()) && "minecraft:overworld".equals(d2.birthDim()), "档案：出生地");
        h.assertTrue(d2.weaponStack().is(Items.IRON_SWORD) && d2.weaponLvMin() == 12 && d2.weaponLvMax() == 34, "档案：武器");
        h.assertTrue(d2.affinity() == 0x21 && d2.adaptPct() == 30 && d2.adaptMax() == 60 && d2.furyPermille() == 450,
                "档案：属性");
        h.assertTrue(d2.dealtPct() == 55 && d2.takenPct() == 18 && d2.rageSources() == 5 && d2.encounters() == 6,
                "档案：伤害修正、怒气来源、交手");
        h.assertTrue(d2.stage() == 2 && d2.progress() == 17 && d2.threshold() == -1, "档案：进度（阈值 -1 要读回 -1）");
        h.assertTrue(Arrays.equals(d2.riddles(), new int[]{3, -1, 6}), "档案：密语");
        h.assertTrue(d2.cards()[0].id() == 3 && d2.cards()[0].damage() == 1 && d2.cards()[0].maxDamage() == 3
                && d2.cards()[1].id() == -1 && d2.cards()[2].id() == 6, "档案：三张卡");
        h.assertTrue(Arrays.equals(d2.attempts(), data.attempts()), "档案：处决记录");
        h.assertTrue(d2.confiscatedCount() == 3 && d2.confiscated().size() == 3, "档案：没收物品个数");
        // 可损坏的物品读回来时原版会补一个 Damage 标签，所以只看大标签有没有被去掉
        h.assertTrue(d2.confiscated().get(0).is(Items.DIAMOND_PICKAXE)
                        && (d2.confiscated().get(0).getTag() == null || !d2.confiscated().get(0).getTag().contains("pad")),
                "档案：超过 2 KB 的 NBT 应当被去掉，物品本身留下");
        h.assertTrue(d2.confiscated().get(1).is(Items.IRON_AXE) && d2.confiscated().get(1).getTag() != null
                && d2.confiscated().get(1).getTag().getInt("k") == 7, "档案：小 NBT 应当原样保留");
        h.assertTrue(d2.confiscated().get(2).is(Items.GOLDEN_APPLE) && d2.confiscated().get(2).getCount() == 3,
                "档案：物品个数");
        h.assertTrue("Old Name".equals(d2.lastName()) && d2.lastFate() == 1, "档案：上一只");
        buf = newBuf();
        LichArchivePacket.encode(new LichArchivePacket(d2), buf);
        sameBytes(h, "档案", bytes(buf), bytes(encoded(data)));

        // ---- 通讯
        LichLook look = new LichLook(-99, 5, (byte) 3, 4321);
        KuvaCommPacket lichLine = new KuvaCommPacket(CommSpeaker.LICH, 1 | 8, Component.literal("Drokag Tuzz"),
                Component.translatable("kuvalich.comm.lich.arrive.1", "Drokag Tuzz", "Steve"), look, 2500);
        buf = newBuf();
        KuvaCommPacket.encode(lichLine, buf);
        byte[] commBytes = bytes(buf);
        KuvaCommPacket c2 = KuvaCommPacket.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(commBytes)));
        h.assertTrue(c2.speaker == CommSpeaker.LICH && c2.flags == 9 && c2.holdMs == 2500, "通讯：发言人、标志、停留");
        h.assertTrue(c2.name != null && "Drokag Tuzz".equals(c2.name.getString()), "通讯：名牌");
        h.assertTrue(lichLine.line.getString().equals(c2.line.getString()), "通讯：台词");
        h.assertTrue(look.equals(c2.look), "通讯：玄骸外观");
        KuvaCommPacket lotusLine = new KuvaCommPacket(CommSpeaker.LOTUS, 0, null, Component.literal("hello"), null, 0);
        buf = newBuf();
        KuvaCommPacket.encode(lotusLine, buf);
        KuvaCommPacket c3 = KuvaCommPacket.decode(buf);
        h.assertTrue(c3.speaker == CommSpeaker.LOTUS && c3.name == null && c3.look == null && c3.holdMs == 0,
                "通讯：Lotus 没有名牌和外观");
        KuvaCommPacket noLook = new KuvaCommPacket(CommSpeaker.LICH, 0, null, Component.literal("x"), null, 0);
        buf = newBuf();
        KuvaCommPacket.encode(noLook, buf);
        LichLook defaulted = KuvaCommPacket.decode(buf).look;
        h.assertTrue(defaulted != null && defaulted.weapon() == -1 && defaulted.entityId() == -1 && defaulted.element() == -1,
                "通讯：玄骸没给外观时读回「全无」");
        sameBytes(h, "通讯", commBytes, bytes(encodedComm(c2)));

        // ---- 解密进度（老签名的构造和新增字段）
        DecryptionHudPacket reveal = new DecryptionHudPacket(DecryptionHudPacket.EVENT_REVEAL, 2, 5, 90, 12,
                5, new int[]{3, -1, 6}, "");
        buf = newBuf();
        DecryptionHudPacket.encode(reveal, buf);
        DecryptionHudPacket r2 = DecryptionHudPacket.decode(buf);
        h.assertTrue(r2.eventType() == DecryptionHudPacket.EVENT_REVEAL && r2.level() == 2 && r2.progress() == 5
                && r2.threshold() == 90 && r2.added() == 12, "解密进度：原有字段");
        h.assertTrue(r2.revealedCard() == 5 && Arrays.equals(r2.riddles(), new int[]{3, -1, 6}) && r2.lichName().isEmpty(),
                "解密进度：揭示卡号和密语");
        DecryptionHudPacket done = new DecryptionHudPacket(DecryptionHudPacket.EVENT_COMPLETE, 0, 0, 60, 0,
                -1, new int[]{3, 1, 6}, "Drokag Tuzz");
        buf = newBuf();
        DecryptionHudPacket.encode(done, buf);
        DecryptionHudPacket done2 = DecryptionHudPacket.decode(buf);
        h.assertTrue("Drokag Tuzz".equals(done2.lichName()) && Arrays.equals(done2.riddles(), new int[]{3, 1, 6}),
                "解密进度：完成时带玄骸名字");
        h.assertTrue(buf.readableBytes() == 0, "解密进度：解码应当读完所有字节");
        DecryptionHudPacket legacy = new DecryptionHudPacket(DecryptionHudPacket.EVENT_PROGRESS, 1, 3, 40, 2);
        buf = newBuf();
        DecryptionHudPacket.encode(legacy, buf);
        DecryptionHudPacket legacy2 = DecryptionHudPacket.decode(buf);
        h.assertTrue(legacy2.revealedCard() == -1 && Arrays.equals(legacy2.riddles(), new int[]{-1, -1, -1}),
                "解密进度：旧构造的揭示信息应当是「没有」");
        h.succeed();
    }

    /** 计划位、状态特效参数、处决记录的编码来回一遍 */
    @GameTest(template = "empty")
    public static void mercyCodecs(GameTestHelper h) {
        for (MercyVariant v : MercyVariant.values()) {
            MercyPlan.Plan plan = new MercyPlan.Plan(v.thrall ? 1 : 3, v.thrall ? 0 : 2, v, new int[]{7, 0, -1},
                    new int[]{3, 1, 0}, true, 2, true);
            MercyPlan.Plan back = MercyPlan.decode(MercyPlan.encode(plan));
            h.assertTrue(back.stabs() == plan.stabs() && back.prefix() == plan.prefix() && back.variant() == v,
                    "计划位：刀数、前缀、套路 " + v);
            h.assertTrue(Arrays.equals(back.cardIds(), plan.cardIds()) && Arrays.equals(back.remaining(), plan.remaining()),
                    "计划位：卡和剩余次数 " + v);
            h.assertTrue(back.returnsItems() && back.consumed() == 2 && back.demo(), "计划位：标志位 " + v);
        }
        int a = KuvaStateKind.pack(KuvaStateKind.ADAPT, KuvaStateKind.adaptParam(5, 3, true, false));
        h.assertTrue(KuvaStateKind.kind(a) == KuvaStateKind.ADAPT, "状态特效：种类");
        int param = KuvaStateKind.param(a);
        h.assertTrue(KuvaStateKind.adaptLayers(param) == 5 && KuvaStateKind.adaptCategory(param) == 3
                && KuvaStateKind.adaptAdded(param) && !KuvaStateKind.adaptFirstFull(param), "状态特效：适应参数");
        int packed = RequiemCard.packAttempt(new int[]{3, 1, 6}, new byte[]{1, 2, 0}, true);
        h.assertTrue(RequiemCard.attemptCardId(packed, 0) == 3 && RequiemCard.attemptResult(packed, 0) == 1
                && RequiemCard.attemptCardId(packed, 1) == 1 && RequiemCard.attemptResult(packed, 1) == 2
                && RequiemCard.attemptCardId(packed, 2) == 6 && RequiemCard.attemptResult(packed, 2) == 0
                && RequiemCard.attemptIsMercy(packed), "处决记录：编码来回");
        h.succeed();
    }

    /** 安魂卡的玄骸数据：新字段存读、老存档（没有这些键）读进来取默认值 */
    @GameTest(template = "empty")
    public static void requiemCardLichData(GameTestHelper h) {
        RequiemCard card = new RequiemCard();
        card.beginLich("Drokag Tuzz", 424242L, 5, "minecraft:plains", "minecraft:overworld");
        card.setLichRage(0.75F);
        card.setLichCooldownUntil(9999L);
        card.setLichEncounterOpen(true);
        card.incLichEncounters();
        card.setLichPaused(true);
        for (int i = 0; i < 7; i++) {
            card.recordAttempt(100 + i);
        }
        h.assertTrue(card.getAttempts().length == RequiemCard.MAX_ATTEMPTS && card.getAttempts()[0] == 106,
                "处决记录最多 5 条、最新在前");
        RequiemCard loaded = new RequiemCard();
        loaded.deserializeNBT(card.serializeNBT());
        h.assertTrue(loaded.hasLich() && "Drokag Tuzz".equals(loaded.getLichName()) && loaded.getLichSeed() == 424242L,
                "存读：名字和种子");
        h.assertTrue(loaded.getLichGeneration() == 1 && loaded.getLichWeapon() == 5 && loaded.getLichRage() == 0.75F,
                "存读：代数、武器、怒气");
        h.assertTrue(loaded.getLichCooldownUntil() == 9999L && loaded.isLichEncounterOpen() && loaded.getLichEncounters() == 1
                && loaded.isLichPaused(), "存读：冷却、遭遇、交手、暂停");
        h.assertTrue("minecraft:plains".equals(loaded.getLichBirthBiome()) && "minecraft:overworld".equals(loaded.getLichBirthDim()),
                "存读：出生地");
        h.assertTrue(Arrays.equals(loaded.getAttempts(), card.getAttempts()), "存读：处决记录");
        RequiemCard cloned = new RequiemCard();
        cloned.clone(loaded);
        h.assertTrue("Drokag Tuzz".equals(cloned.getLichName()) && Arrays.equals(cloned.getAttempts(), card.getAttempts()),
                "克隆：玄骸数据");

        // 结束这一轮：进历史，名字清空，等级要在 reset 之前取
        loaded.setKuvaLevel(3);
        loaded.endLichRound(RequiemCard.FATE_VANQUISHED);
        h.assertTrue(!loaded.hasLich() && loaded.getAttempts().length == 0 && loaded.getLastHistory() != null
                && "Drokag Tuzz".equals(loaded.getLastHistory().name()) && loaded.getLastHistory().rank() == 4
                && loaded.getLastHistory().fate() == RequiemCard.FATE_VANQUISHED, "结束一轮：进历史、清空当前");
        RequiemCard afterHistory = new RequiemCard();
        afterHistory.deserializeNBT(loaded.serializeNBT());
        h.assertTrue(afterHistory.getLastHistory() != null && afterHistory.getLastHistory().generation() == 1,
                "存读：历史");

        // 老存档：去掉所有玄骸键，应当读成默认值而不是报错
        CompoundTag old = card.serializeNBT();
        for (String key : List.of("lichDataVersion", "lichName", "lichSeed", "lichGeneration", "lichRage",
                "lichCooldownUntil", "lichEncounterOpen", "lichEncounters", "lichWeapon", "lichBirthBiome",
                "lichBirthDim", "lichAttempts", "lichHistory", "lichPaused")) {
            old.remove(key);
        }
        RequiemCard legacy = new RequiemCard();
        legacy.deserializeNBT(old);
        h.assertTrue(!legacy.hasLich() && legacy.getLichDataVersion() == RequiemCard.LICH_DATA_VERSION
                && legacy.getLichWeapon() == -1 && legacy.getLichRage() == 0.0F && legacy.getAttempts().length == 0,
                "老存档：玄骸字段取默认值");
        h.succeed();
    }

    private static FriendlyByteBuf newBuf() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static byte[] bytes(FriendlyByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), out);
        return out;
    }

    private static FriendlyByteBuf encodedState(LichStatePacket msg) {
        FriendlyByteBuf buf = newBuf();
        LichStatePacket.encode(msg, buf);
        return buf;
    }

    private static FriendlyByteBuf encoded(LichArchivePacket.Data data) {
        FriendlyByteBuf buf = newBuf();
        LichArchivePacket.encode(new LichArchivePacket(data), buf);
        return buf;
    }

    private static FriendlyByteBuf encodedComm(KuvaCommPacket msg) {
        FriendlyByteBuf buf = newBuf();
        KuvaCommPacket.encode(msg, buf);
        return buf;
    }

    private static void sameBytes(GameTestHelper h, String what, byte[] a, byte[] b) {
        h.assertTrue(Arrays.equals(a, b), what + "：解码后再编码，字节应当和第一次一致");
    }

    @AfterBatch(batch = PLAYER_BATCH)
    public static void afterPlayerBatch(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }
}
