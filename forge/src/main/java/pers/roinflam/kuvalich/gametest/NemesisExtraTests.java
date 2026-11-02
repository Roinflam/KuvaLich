package pers.roinflam.kuvalich.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.comm.KuvaComm;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.nemesis.EncounterEnd;
import pers.roinflam.kuvalich.nemesis.LichArchiveService;
import pers.roinflam.kuvalich.nemesis.LichComms;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.LichLine;
import pers.roinflam.kuvalich.nemesis.LichNames;
import pers.roinflam.kuvalich.nemesis.LichPersona;
import pers.roinflam.kuvalich.nemesis.LichProfile;
import pers.roinflam.kuvalich.nemesis.LichTraits;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.network.packet.KuvaCommPacket;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.network.packet.LichArchivePacket;
import pers.roinflam.kuvalich.network.packet.LichArchiveRequestPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * 专属玄骸服务端外围：名字、弱点抗性、通讯限流与聊天回显、安魂通牒、没收、谜语解全、档案请求限流、管理命令
 *
 * <p>要看「真的发出去了什么」的测试直接读测试玩家连接上积下的出站包：测试玩家的连接挂在一个
 * {@link EmbeddedChannel} 上，服务端发给他的包都留在通道的出站队列里，按模组通道的包序号认出是哪种包再解码。</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NemesisExtraTests {

    private static final String CORE = "kl_nemesis_extra";
    private static final String ISO_ULTIMATUM = "kl_nemesis_iso_ultimatumOwn";
    private static final String ISO_COMMANDS = "kl_nemesis_iso_commandsSmoke";
    private static final String CFG_CONFISCATE = "kl_cfg_confiscateOnlyWithLich";
    private static final String CFG_ECHO = "kl_cfg_chatEchoModes";

    private static final ResourceLocation CHANNEL = new ResourceLocation(Reference.MOD_ID, "main");

    private static Difficulty savedDifficulty;

    private NemesisExtraTests() {
    }

    // ==================== #12 名字：确定、合规、够多 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void nameDeterminism(GameTestHelper h) {
        Set<String> distinct = new HashSet<>();
        int originalHits = 0;
        for (long seed = 0; seed < 100000; seed++) {
            String a = LichNames.generate(seed);
            String b = LichNames.generate(seed);
            if (!a.equals(b)) {
                h.fail("同一个种子应当拼出同一个名字：种子 " + seed + " 得到 " + a + " / " + b);
                return;
            }
            if (!LichNames.passesRules(a) || !LichNames.isAllowed(a)) {
                h.fail("拼出来的名字应当合规：种子 " + seed + " 得到 " + a);
                return;
            }
            String lower = a.toLowerCase(Locale.ROOT);
            for (char ch : new char[]{'q', 'w', 'y', 'c', 'j'}) {
                if (lower.indexOf(ch) >= 0) {
                    h.fail("名字里不该有 " + ch + "：" + a);
                    return;
                }
            }
            int space = lower.indexOf(' ');
            if (LichNames.ORIGINAL.contains(lower.substring(0, space))
                    || LichNames.ORIGINAL.contains(lower.substring(space + 1))) {
                originalHits++;
            }
            distinct.add(a);
        }
        h.assertTrue(originalHits == 0, "名或姓不应当撞上原作玄骸的名字，命中 " + originalHits + " 次");
        h.assertTrue(distinct.size() >= 20000, "十万个种子应当拼出至少两万个不同的名字，实际 " + distinct.size());
        // 几条拒收规则本身
        h.assertTrue(!LichNames.passesRules("Fabb Kogg") && !LichNames.passesRules("Grul Fett"), "原作名应当被拒");
        h.assertTrue(!LichNames.passesRules("Kraaag Tuzz"), "三连字母应当被拒");
        h.assertTrue(!LichNames.passesRules("Gaau Tuzz"), "名里三个元音连写应当被拒");
        h.assertTrue(!LichNames.passesRules("Drokagzsh Tuzz"), "名超过 8 个字母应当被拒");
        h.assertTrue(!LichNames.isAllowed("Bigshit Mo") && !LichNames.isAllowed("Ab§c"), "不雅词、格式代码应当被拒");
        h.assertTrue(LichNames.isAllowed("Vrok Tazz") && !LichNames.isAllowed(" Vrok"), "首尾空白应当被拒");
        h.assertTrue(LichNames.upper("Drokag Tuzz").equals("DROKAG TUZZ"), "全大写写法");
        h.succeed();
    }

    // ==================== 专属面板（按种子、等级、性格） ====================

    @GameTest(template = "empty", batch = CORE)
    public static void traitsBySeed(GameTestHelper h) {
        int[] personas = new int[2];
        int[] weakCount = new int[4];
        // 各性格各项技能的档位合计（专长按 3 算），比两种性格的偏向
        int[][] tierSum = new int[2][LichTraits.SKILLS];
        for (long seed = 0; seed < 4000; seed++) {
            LichTraits t = LichTraits.seeded(seed, 2, 0.15D);
            LichTraits again = LichTraits.seeded(seed, 2, 0.15D);
            h.assertTrue(t.packedTiers() == again.packedTiers() && t.maxHealth == again.maxHealth
                    && java.util.Arrays.equals(t.affinityPercents(), again.affinityPercents()), "同一个种子应当同一个结果");
            boolean jovial = t.persona == LichPersona.JOVIAL;
            h.assertTrue(t.persona == LichPersona.of((int) seed), "性格和台词那边同一个算法");
            personas[jovial ? 0 : 1]++;
            int weak = 0;
            int resist = 0;
            for (int c = 0; c < LichTraits.CATEGORIES; c++) {
                double m = t.affinity(c);
                if (m > 1.0D) {
                    weak++;
                    weakCount[c]++;
                    h.assertTrue(m >= 1.25D - 1.0E-9D && m <= 1.40D + 1.0E-9D, "弱点倍率应当在 1.25~1.40：" + m);
                } else if (m < 1.0D) {
                    resist++;
                    double low = c == LichTraits.CAT_MELEE ? LichTraits.MELEE_RESIST_FLOOR : jovial ? 0.75D : 0.60D;
                    h.assertTrue(m >= low - 1.0E-9D && m <= (jovial ? 0.85D : 0.75D) + 1.0E-9D
                            || c == LichTraits.CAT_MELEE && Math.abs(m - LichTraits.MELEE_RESIST_FLOOR) < 1.0E-9D,
                            "抗性倍率越界：种子 " + seed + " 第 " + c + " 类 " + m);
                }
            }
            h.assertTrue(jovial ? weak == 2 && resist == 1 : weak == 1 && resist == 2,
                    "活泼 2 弱 1 抗、理性 1 弱 2 抗：种子 " + seed + " 得到 " + weak + " 弱 " + resist + " 抗");
            int sig = t.signature();
            h.assertTrue(sig >= 0 && (jovial ? sig == LichTraits.FURY || sig == LichTraits.QUAKE || sig == LichTraits.LIFESTEAL
                    : sig == LichTraits.ADAPT || sig == LichTraits.REGEN || sig == LichTraits.QUAKE),
                    "专长应当在性格的池子里：种子 " + seed + " 得到 " + sig);
            int sigs = 0;
            for (int i = 0; i < LichTraits.SKILLS; i++) {
                sigs += t.isSignature(i) ? 1 : 0;
                tierSum[jovial ? 0 : 1][i] += t.tier(i);
            }
            h.assertTrue(sigs == 1, "专长恰好一项");
            double hpBase = LichTraits.BASE_HEALTH * (jovial ? 0.85D : 1.20D);
            h.assertTrue(t.maxHealth >= hpBase * 0.92D - 1.0E-6D && t.maxHealth <= hpBase * 1.08D + 1.0E-6D,
                    "2 级生命应当在性格底子 ±8% 内：" + t.maxHealth);
        }
        h.assertTrue(personas[0] > 1600 && personas[1] > 1600, "两种性格应当差不多一半一半：" + personas[0] + " / " + personas[1]);
        for (int i = 0; i < 4; i++) {
            h.assertTrue(weakCount[i] > 700, "四类都应当有机会当弱点：第 " + i + " 类只有 " + weakCount[i] + " 次");
        }
        h.assertTrue(tierSum[0][LichTraits.FURY] > tierSum[1][LichTraits.FURY]
                && tierSum[0][LichTraits.LIFESTEAL] > tierSum[1][LichTraits.LIFESTEAL]
                && tierSum[1][LichTraits.ADAPT] > tierSum[0][LichTraits.ADAPT]
                && tierSum[1][LichTraits.REGEN] > tierSum[0][LichTraits.REGEN], "活泼偏增幅、吸血，理性偏适应、自愈");
        h.succeed();
    }

    @GameTest(template = "empty", batch = CORE)
    public static void traitsRankCurve(GameTestHelper h) {
        double[] expect = {0.85D, 1.0D, 1.15D, 1.30D, 1.45D, 1.60D, 1.60D, 1.60D};
        for (int rank = 1; rank <= expect.length; rank++) {
            h.assertTrue(Math.abs(LichTraits.rankScale(rank, 0.15D) - expect[rank - 1]) < 1.0E-9D,
                    rank + " 级的倍率应当是 " + expect[rank - 1]);
        }
        h.assertTrue(LichTraits.rankScale(5, 0.0D) == 1.0D, "步长 0 时不随等级变");
        // 关掉每只不同：2 级就是改版前所有玄骸那套
        LichTraits plain = LichTraits.uniform(12345L, 2, 0.15D);
        h.assertTrue(!plain.varied && plain.maxHealth == LichTraits.BASE_HEALTH && plain.attack == LichTraits.BASE_ATTACK
                && plain.armor == LichTraits.BASE_ARMOR && plain.signature() == -1
                && plain.adaptPercent() == 5 && plain.adaptStacks() == 6 && plain.furyScale() == 1.0D
                && plain.lifesteal() == 0.2D && plain.quakeRadius() == 3.0D && plain.regenScale() == 1.0D,
                "固定面板 2 级应当和改版前一样");
        h.assertTrue(plain.affinityMask() == 0xC2, "固定面板的弱点抗性应当是弱弓箭、抗其他弹射物和魔法（0xC2）");
        h.assertTrue(Math.abs(LichTraits.uniform(12345L, 1, 0.15D).maxHealth - 170.0D) < 1.0E-9D, "1 级是 85%");
        LichTraits r2 = LichTraits.seeded(777L, 2, 0.15D);
        LichTraits r4 = LichTraits.seeded(777L, 4, 0.15D);
        h.assertTrue(Math.abs(r4.maxHealth - r2.maxHealth * 1.30D) < 1.0E-6D && Math.abs(r4.armor - r2.armor * 1.30D) < 1.0E-9D
                && r4.attack == r2.attack && r4.packedTiers() == r2.packedTiers(), "等级只改生命和护甲");
        h.succeed();
    }

    // ==================== 通讯限流 ====================

    @GameTest(template = "empty", batch = CORE, timeoutTicks = 500)
    public static void commCooldown(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        giveLich(p);
        drain(p);
        h.assertTrue(LichComms.say(p, LichLine.ARRIVE), "第一句应当发出去");
        List<KuvaCommPacket> sent = comms(p);
        h.assertTrue(sent.size() == 1 && sent.get(0).speaker == CommSpeaker.LICH
                        && keyOf(sent.get(0).line).startsWith(lichKeyPrefix(p, "arrive")),
                "应当正好发了一个玄骸的到场通讯包，实际 " + sent.size());
        h.assertTrue((sent.get(0).flags & KuvaComm.ARRIVAL) != 0, "到场那句应当带青色故障显形");
        h.assertTrue(sent.get(0).look != null && sent.get(0).look.seed() == (int) card(p).getLichSeed(),
                "外观应当是他那只的种子");
        h.assertTrue(!LichComms.say(p, LichLine.DEPART), "400 ms 内同一个发言人的第二句应当被丢");
        h.assertTrue(LichComms.say(p, LichLine.LOTUS_LICH_BORN), "Lotus 和玄骸分开算，同一刻应当能发");
        List<KuvaCommPacket> lotus = comms(p);
        h.assertTrue(lotus.size() == 1 && lotus.get(0).speaker == CommSpeaker.LOTUS, "应当发了 Lotus 那句");

        // 不重复：不受限流地连发 30 句随机嘲讽，相邻两句不应当相同
        int last = -1;
        for (int i = 0; i < 30; i++) {
            h.assertTrue(LichComms.sayExact(p, LichLine.IDLE, -1, null), "管理用的发送不受限流");
            int now = LichComms.lastIndex(p, LichLine.IDLE);
            h.assertTrue(now != last, "同一类连着两次不应当抽到同一条（第 " + i + " 次）");
            last = now;
        }
        drain(p);

        h.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    h.assertTrue(!LichComms.say(p, LichLine.IDLE), "低优先级的在 8 秒内有过通讯时应当被丢");
                    h.assertTrue(LichComms.say(p, LichLine.DEPART), "过了 400 ms，中优先级的应当能发");
                })
                .thenIdle(165)
                .thenExecute(() -> h.assertTrue(LichComms.say(p, LichLine.CONFISCATE),
                        "8 秒没说话，没收那句应当能发"))
                .thenIdle(165)
                .thenExecute(() -> {
                    h.assertTrue(!LichComms.say(p, LichLine.CONFISCATE), "没收类 30 秒内只说一句");
                    h.assertTrue(LichComms.say(p, LichLine.IDLE), "8 秒没说话，随机嘲讽应当能发");
                })
                .thenSucceed();
    }

    // ==================== 聊天回显三档 ====================

    @GameTest(template = "empty", batch = CFG_ECHO)
    public static void chatEchoModes(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        giveLich(p);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichCommChatEcho, ModConfig.KuvaLichConfig.LichCommEcho.NONE);
        for (LichLine line : LichLine.values()) {
            h.assertTrue((LichComms.flagsFor(line) & KuvaComm.CHAT_COPY) == 0, "「不回显」时 " + line + " 不应当进聊天栏");
        }
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichCommChatEcho, ModConfig.KuvaLichConfig.LichCommEcho.IMPORTANT);
        LichLine[] important = {LichLine.BIRTH, LichLine.FAIL, LichLine.VANQUISHED, LichLine.KILLED_YOU, LichLine.YOU_DIED};
        for (LichLine line : LichLine.values()) {
            boolean expect = false;
            for (LichLine i : important) {
                expect |= i == line;
            }
            h.assertTrue(((LichComms.flagsFor(line) & KuvaComm.CHAT_COPY) != 0) == expect,
                    "「重要」时 " + line + (expect ? " 应当" : " 不应当") + "进聊天栏");
        }
        drain(p);
        h.assertTrue(LichComms.sayExact(p, LichLine.FAIL, 0, null), "应当发出去");
        List<KuvaCommPacket> fail = comms(p);
        h.assertTrue(fail.size() == 1 && (fail.get(0).flags & KuvaComm.CHAT_COPY) != 0, "判错那句的包里应当带「进聊天栏」");
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichCommChatEcho, ModConfig.KuvaLichConfig.LichCommEcho.ALL);
        for (LichLine line : LichLine.values()) {
            h.assertTrue((LichComms.flagsFor(line) & KuvaComm.CHAT_COPY) != 0, "「全部」时 " + line + " 应当进聊天栏");
        }
        h.assertTrue(LichComms.sayExact(p, LichLine.IDLE, 0, null), "应当发出去");
        List<KuvaCommPacket> idle = comms(p);
        h.assertTrue(idle.size() == 1 && (idle.get(0).flags & KuvaComm.CHAT_COPY) != 0
                && (idle.get(0).flags & KuvaComm.AMBIENT) != 0, "「全部」时随机嘲讽也进聊天栏，并且标成随口一句");
        h.succeed();
    }

    @AfterBatch(batch = CFG_ECHO)
    public static void afterEcho(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 没收只在有玄骸时 ====================

    @GameTest(template = "empty", batch = CFG_CONFISCATE, timeoutTicks = 100)
    public static void confiscateOnlyWithLich(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.confiscationChance, 100.0D);
        ServerLevel level = h.getLevel();

        // 揭开过密语、但这一只已经没了：不没收
        ServerPlayer without = KuvaTestKit.player(h);
        RequiemCard c0 = card(without);
        c0.setUnlockedCardStatus(1);
        c0.setOneRiddle(0);
        h.assertTrue(!c0.hasLich() && c0.hasAnyRiddleUnlocked(), "前提：揭开过密语、没有玄骸");
        ItemEntity apple = drop(level, without, new ItemStack(Items.APPLE, 3));
        apple.playerTouch(without);
        h.assertTrue(c0.getConfiscatedItemCount() == 0, "没有玄骸时不应当没收");
        h.assertTrue(without.getInventory().countItem(Items.APPLE) == 3, "没有玄骸时应当照常捡起");

        // 有玄骸：概率 100% 一定没收
        ServerPlayer with = KuvaTestKit.player(h);
        giveLich(with);
        RequiemCard c1 = card(with);
        drain(with);
        ItemEntity bread = drop(level, with, new ItemStack(Items.BREAD, 2));
        bread.playerTouch(with);
        h.assertTrue(c1.getConfiscatedItemCount() == 1, "有玄骸时应当没收，实际 " + c1.getConfiscatedItemCount());
        h.assertTrue(bread.isRemoved() && with.getInventory().countItem(Items.BREAD) == 0, "被没收的东西不应当进背包");
        List<KuvaFxPacket> fx = fx(with);
        h.assertTrue(fx.stream().anyMatch(f -> f.fx == FxId.CONFISCATE.id && f.a == with.getId() + 1),
                "应当给附近的人发没收演出（a = 被没收的人）");
        h.startSequence()
                .thenIdle(8)
                .thenExecute(() -> {
                    List<KuvaCommPacket> sent = comms(with);
                    h.assertTrue(sent.size() == 1 && keyOf(sent.get(0).line).startsWith(lichKeyPrefix(with, "confiscate")),
                            "没收后应当嘲讽一句（通讯），实际 " + sent.size());
                })
                .thenSucceed();
    }

    @AfterBatch(batch = CFG_CONFISCATE)
    public static void afterConfiscate(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 档案请求限流 ====================

    @GameTest(template = "empty", batch = CORE, timeoutTicks = 2000)
    public static void archiveRequestThrottle(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        giveLich(p);
        drain(p);
        request(p);
        request(p);
        List<LichArchivePacket> first = archives(p);
        h.assertTrue(first.size() == 1, "500 ms 内连要两次只回一次，实际 " + first.size());
        LichArchivePacket.Data d = first.get(0).data();
        RequiemCard card = card(p);
        h.assertTrue(d.state().name.equals(card.getLichName()), "回的是他自己那只");
        LichTraits traits = LichTraits.current(card.getLichSeed(), card.getLichRank());
        h.assertTrue(java.util.Arrays.equals(d.stats().affinityPct(), traits.affinityPercents())
                && d.stats().skills() == traits.packedTiers() && d.stats().health() == (int) Math.round(traits.maxHealth),
                "档案面板照同一个函数");
        // 测试服的 tick 跑得比真实时间快，限流按真实时间算，所以按真实时间等
        long sentAt = Util.getMillis();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Util.getMillis() - sentAt > 600L, "等过 500 ms"))
                .thenExecute(() -> {
                    request(p);
                    h.assertTrue(archives(p).size() == 1, "过了 500 ms 应当再回一次");
                })
                .thenSucceed();
    }

    // ==================== 谜语解全发演出 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void riddleSolvedSendsFx(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        Item riddle = KuvaLichItems.REQUIEM_RIDDLE.get();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(riddle, 2));
        drain(p);
        riddle.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().getCount() == 2, "答案没齐时不应当用掉");
        h.assertTrue(fx(p).stream().noneMatch(f -> f.fx == FxId.RIDDLE_SOLVED.id), "答案没齐时不应当发解全演出");

        card.setOneAnswer(0);
        card.setTwoAnswer(3);
        card.setThreeAnswer(5);
        riddle.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().getCount() == 1, "答案齐了应当用掉一个");
        h.assertTrue(fx(p).stream().filter(f -> f.fx == FxId.RIDDLE_SOLVED.id).count() == 1,
                "应当只给本人发一个解全演出");
        h.succeed();
    }

    // ==================== 台词键和说话动画都在 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void commKeysAndTalkAnimation(GameTestHelper h) {
        for (String lang : new String[]{"zh_cn", "en_us"}) {
            JsonObject json = readJson("/assets/kuvalich/lang/" + lang + ".json");
            h.assertTrue(json != null, "读不到语言文件 " + lang);
            for (LichLine line : LichLine.values()) {
                for (LichPersona persona : LichPersona.values()) {
                    for (int i = 0; i < line.count(); i++) {
                        String key = line.key(persona, i);
                        h.assertTrue(json.has(key), lang + " 缺台词键 " + key);
                    }
                }
            }
        }
        // 两种性格大致各占一半
        int jovial = 0;
        for (int seed = 0; seed < 2000; seed++) {
            jovial += LichPersona.of(seed * 7919 + 13) == LichPersona.JOVIAL ? 1 : 0;
        }
        h.assertTrue(jovial > 800 && jovial < 1200, "两种性格应当差不多一样多，实际活泼 " + jovial + " / 2000");
        JsonObject anim = readJson("/assets/kuvalich/animations/kuva.animation.json");
        h.assertTrue(anim != null, "读不到动画文件");
        JsonObject all = anim.getAsJsonObject("animations");
        h.assertTrue(all.has("talk") && all.getAsJsonObject("talk").get("loop").getAsBoolean(), "应当有循环的 talk");
        h.assertTrue(all.has("talk_shout") && !all.getAsJsonObject("talk_shout").has("loop"), "应当有只播一遍的 talk_shout");
        h.succeed();
    }

    @AfterBatch(batch = CORE)
    public static void afterCore(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #10 安魂通牒只召自己那只 ====================

    @BeforeBatch(batch = ISO_ULTIMATUM)
    public static void beforeUltimatum(ServerLevel level) {
        rememberDifficulty(level.getServer());
    }

    @GameTest(template = "empty", batch = ISO_ULTIMATUM, timeoutTicks = 200)
    public static void ultimatumOwn(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        prepareFront(h);
        Item ultimatum = KuvaLichItems.REQUIEM_ULTIMATUM.get();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ultimatum, 3));

        // ① 还没有玄骸：不消耗、什么都不来
        ultimatum.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().getCount() == 3, "没有玄骸时不应当消耗");
        h.assertTrue(LichDirector.stateOf(p) == LichState.UNBORN && LichDirector.getLich(p).isEmpty(), "没有玄骸时什么都不来");

        // ② 有玄骸：消耗一个、怒气填满、先通讯后现身
        bornByProgress(p, card);
        card.setLichRage(0.2F);
        drain(p);
        ultimatum.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().getCount() == 2, "应当消耗一个，实际剩 " + p.getMainHandItem().getCount());
        h.assertTrue(card.getLichRage() >= 1.0F, "怒气应当填满");
        h.assertTrue(LichDirector.stateOf(p) == LichState.ARRIVING, "应当在来的路上：" + LichDirector.stateOf(p));
        h.assertTrue(p.getCooldowns().isOnCooldown(ultimatum), "用完应当有冷却");
        List<KuvaCommPacket> said = comms(p);
        h.assertTrue(said.stream().anyMatch(c -> keyOf(c.line).startsWith(lichKeyPrefix(p, "ultimatum"))
                && (c.flags & KuvaComm.ARRIVAL) != 0), "应当先发通牒那句（带青色故障显形）");

        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(LichDirector.getLich(p).isPresent(), "玄骸还没到"))
                .thenExecute(() -> {
                    KuvaMasterEntity lich = LichDirector.getLich(p).orElseThrow();
                    h.assertTrue(lich.isOwner(p) && lich.getLichName().equals(card.getLichName()), "来的应当是他自己那只");
                    h.assertTrue(lich.isAppearing(), "刚现身应当在显形期");
                    h.assertTrue(fx(p).stream().anyMatch(f -> f.fx == FxId.LICH_SUMMON.id && f.a == p.getId() + 1
                            && f.entityId() == lich.getId()), "现身时应当发召唤演出（a = 召唤者）");
                    // ③ 已经在场：不消耗
                    ultimatum.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
                    h.assertTrue(p.getMainHandItem().getCount() == 2, "在场时再用不应当消耗");
                    LichDirector.endEncounter(p, lich, EncounterEnd.ADMIN, false);
                })
                .thenSucceed();
    }

    @AfterBatch(batch = ISO_ULTIMATUM)
    public static void afterUltimatum(ServerLevel level) {
        restoreDifficulty(level.getServer());
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 管理命令每个子命令跑一遍 ====================

    @GameTest(template = "empty", batch = ISO_COMMANDS, timeoutTicks = 200)
    public static void commandsSmoke(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        prepareFront(h);
        MinecraftServer server = h.getLevel().getServer();
        CommandSourceStack src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        String n = p.getGameProfile().getName();
        Function<String, Integer> run = cmd -> {
            try {
                return server.getCommands().getDispatcher().execute("kuvalich lich " + cmd, src);
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException("命令解析失败：" + cmd + "：" + e.getMessage(), e);
            }
        };

        h.assertTrue(run.apply("pause " + n + " on") == 1 && card.isLichPaused(), "pause on");
        h.assertTrue(run.apply("info " + n) == 1, "info（没有玄骸）");
        h.assertTrue(run.apply("summon " + n) == 0, "没有玄骸时 summon 应当失败");
        h.assertTrue(run.apply("birth " + n) == 1 && card.hasLich(), "birth");
        h.assertTrue(run.apply("birth " + n) == 0, "已有玄骸时 birth 应当失败");
        h.assertTrue(run.apply("info " + n) == 1, "info");
        h.assertTrue(run.apply("attempt " + n + " correct wrong none") == 1 && card.getAttempts().length == 1, "attempt");
        int packed = card.getAttempts()[0];
        h.assertTrue(RequiemCard.attemptResult(packed, 0) == RequiemCard.ATTEMPT_RIGHT
                && RequiemCard.attemptResult(packed, 1) == RequiemCard.ATTEMPT_WRONG
                && RequiemCard.attemptCardId(packed, 2) == RequiemCard.ATTEMPT_NO_CARD, "attempt 的编码");
        h.assertTrue(run.apply("attempt " + n + " right wrong none") == 0, "结果写错应当失败");
        h.assertTrue(run.apply("rage " + n + " 0.5") == 1 && Math.abs(card.getLichRage() - 0.5F) < 1.0E-4F, "rage");
        h.assertTrue(run.apply("cooldown " + n + " clear") == 1 && card.getLichCooldownUntil() == 0L, "cooldown clear");
        h.assertTrue(run.apply("taunt " + n + " arrive 2") == 1 && LichComms.lastIndex(p, LichLine.ARRIVE) == 1, "taunt 指定序号");
        h.assertTrue(run.apply("taunt " + n + " idle") == 1, "taunt 随机");
        h.assertTrue(run.apply("taunt " + n + " random") == 1, "taunt 随便一类");
        h.assertTrue(run.apply("taunt " + n + " archive") == 0, "archive 不支持");
        h.assertTrue(run.apply("taunt " + n + " nonsense") == 0, "不认识的类应当失败");
        h.assertTrue(run.apply("taunt " + n + " birth 9") == 0, "序号越界应当失败");
        h.assertTrue(run.apply("rename " + n + " Vrok Tazz") == 1 && card.getLichName().equals("Vrok Tazz"), "rename");
        h.assertTrue(run.apply("rename " + n + " Big Shit") == 0 && card.getLichName().equals("Vrok Tazz"), "不雅名应当被拒");
        h.assertTrue(run.apply("depart " + n) == 0, "不在场时 depart 应当失败");
        h.assertTrue(run.apply("summon " + n + " front") == 1, "summon front");
        KuvaMasterEntity lich = LichDirector.getLich(p).orElseThrow();
        h.assertTrue(lich.getLichName().equals("Vrok Tazz"), "在场那只用改过的名字");
        h.assertTrue(run.apply("rename " + n + " Grul Morr") == 1 && lich.getLichName().equals("Grul Morr"), "改名在场那只跟着改");
        h.assertTrue(run.apply("summon " + n + " hunt") == 0, "在场时再召应当失败");
        h.assertTrue(run.apply("info " + n) == 1, "info（在场）");
        run.apply("down " + n);
        h.assertTrue(run.apply("depart " + n) == 1, "depart");
        h.assertTrue(run.apply("reset " + n) == 1 && !card.hasLich(), "reset");
        h.assertTrue(card.getUnlockedCardStatus() >= 1, "reset 不动谜语阶段");
        h.assertTrue(run.apply("summon " + n + " ultimatum") == 0, "没有玄骸时 summon ultimatum 应当失败");

        // 离场演出放完（8 tick）之后再召下一只：离场中的那只还算「在场」
        h.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(LichDirector.getLich(p).isEmpty(), "离场演出放完应当没有在场的了");
                    h.assertTrue(run.apply("birth " + n + " Zhaux Bekk") == 1 && card.hasLich()
                            && card.getLichName().equals("Zhaux Bekk"), "揭开过密语后作废的，birth 带名字应当能再生一只");
                    h.assertTrue(card.getLichGeneration() == 2, "第二只");
                    h.assertTrue(run.apply("down " + n) >= 0, "down（不在场时先召到面前）");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    run.apply("depart " + n);
                    h.assertTrue(run.apply("reset " + n) == 1 && !card.hasLich(), "第二只 reset");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(run.apply("birth " + n) == 1 && card.getLichGeneration() == 3, "第三只");
                    h.assertTrue(run.apply("summon " + n + " hunt") == 1, "summon hunt");
                    h.assertTrue(run.apply("reset " + n) == 1 && !card.hasLich(), "来的路上 reset");
                    h.assertTrue(run.apply("pause " + n + " off") == 1 && !card.isLichPaused(), "pause off");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(LichDirector.getLich(p).isEmpty(), "作废之后不应当再有在场的玄骸");
                    List<KuvaMasterEntity> left = h.getLevel().getEntitiesOfClass(KuvaMasterEntity.class,
                            new AABB(p.blockPosition()).inflate(64.0D), e -> e.isOwner(p) && !e.isRemoved());
                    h.assertTrue(left.isEmpty(), "世界里不应当留下他的玄骸：" + left.size());
                })
                .thenSucceed();
    }

    @BeforeBatch(batch = ISO_COMMANDS)
    public static void beforeCommands(ServerLevel level) {
        rememberDifficulty(level.getServer());
    }

    @AfterBatch(batch = ISO_COMMANDS)
    public static void afterCommands(ServerLevel level) {
        restoreDifficulty(level.getServer());
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 工具 ====================

    private static RequiemCard card(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD)
                .orElseThrow(() -> new IllegalStateException("测试玩家没有安魂卡能力"));
    }

    /** 直接给他写上一只玄骸（不走诞生流程，不排诞生通讯，免得搅了限流） */
    private static void giveLich(ServerPlayer p) {
        long seed = p.getUUID().getLeastSignificantBits();
        card(p).beginLich(LichNames.generate(seed), seed, LichProfile.weaponIndex(seed, KuvaLichItems.KUVA_WEAPONS.size()),
                "minecraft:plains", "minecraft:overworld");
    }

    /** 走正常的进度流程揭开第一条密语 */
    private static void bornByProgress(ServerPlayer p, RequiemCard card) {
        int threshold = ModConfig.KUVA_LICH.firstStage.get();
        card.setDecryptionProgress(Math.max(0, threshold - 1));
        int levelBefore = card.getUnlockedCardStatus();
        card.addPotion(1);
        LichDirector.onDecryptionProgress(p, card, 1, levelBefore);
    }

    private static ItemEntity drop(ServerLevel level, ServerPlayer p, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, p.getX() + 1.5D, p.getY(), p.getZ(), stack);
        e.setNoPickUpDelay();
        level.addFreshEntity(e);
        return e;
    }

    /** 面前落点要用的空地：在结构中心往前（+z）挖一块封闭的场地，并把测试区块同步载入 */
    private static void prepareFront(GameTestHelper h) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(origin);
        for (int dx = -1; dx < 4; dx++) {
            for (int dz = -1; dz < 4; dz++) {
                h.getLevel().getChunk(chunk.x + dx, chunk.z + dz);
            }
        }
        BlockPos c = KuvaTestKit.center(h);
        KuvaTestKit.openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 3, c.getZ() + 11);
    }

    // -------- 读测试玩家收到的包 --------

    private static EmbeddedChannel channel(ServerPlayer p) {
        return (EmbeddedChannel) p.connection.connection.channel();
    }

    /** 扔掉到目前为止积下的出站包 */
    private static void drain(ServerPlayer p) {
        EmbeddedChannel ch = channel(p);
        while (ch.readOutbound() != null) {
            // 只是清空
        }
    }

    /** 读出积下的、属于本模组通道的某一种包（读完就从队列里拿掉，其余的包一并丢掉） */
    private static <T> List<T> take(ServerPlayer p, Object sample, Function<FriendlyByteBuf, T> decoder) {
        int wanted = indexOf(sample);
        List<T> out = new ArrayList<>();
        EmbeddedChannel ch = channel(p);
        Object msg;
        while ((msg = ch.readOutbound()) != null) {
            if (msg instanceof ClientboundCustomPayloadPacket custom && CHANNEL.equals(custom.getIdentifier())) {
                FriendlyByteBuf data = custom.getData();
                if (data.readUnsignedByte() == wanted) {
                    out.add(decoder.apply(data));
                }
                data.release();
            }
        }
        return out;
    }

    private static List<KuvaCommPacket> comms(ServerPlayer p) {
        return take(p, new KuvaCommPacket(CommSpeaker.LOTUS, 0, null, Component.empty(), null, 0), KuvaCommPacket::decode);
    }

    private static List<KuvaFxPacket> fx(ServerPlayer p) {
        return take(p, new KuvaFxPacket(0, 0, 0, 0, 0, 0, 0), KuvaFxPacket::decode);
    }

    private static List<LichArchivePacket> archives(ServerPlayer p) {
        return take(p, new LichArchivePacket(LichArchiveService.build(p)), LichArchivePacket::decode);
    }

    /** 这种包在模组通道里的序号：让通道自己编一个样本，读第一个字节 */
    private static int indexOf(Object sample) {
        Packet<?> vanilla = KuvaLich.network.toVanillaPacket(sample, NetworkDirection.PLAY_TO_CLIENT);
        FriendlyByteBuf data = ((ClientboundCustomPayloadPacket) vanilla).getData();
        int index = data.readUnsignedByte();
        data.release();
        return index;
    }

    /** 模拟客户端发来一个「要档案」的请求，走服务端真实的收包流程 */
    private static void request(ServerPlayer p) {
        Packet<?> c2s = KuvaLich.network.toVanillaPacket(new LichArchiveRequestPacket(), NetworkDirection.PLAY_TO_SERVER);
        p.connection.handleCustomPayload((ServerboundCustomPayloadPacket) c2s);
    }

    private static String keyOf(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** 这个玩家那只玄骸在某个场合的台词键前缀（按它的性格） */
    private static String lichKeyPrefix(ServerPlayer p, String situation) {
        return "kuvalich.comm.lich." + LichPersona.of((int) card(p).getLichSeed()).id + "." + situation + ".";
    }

    private static JsonObject readJson(String path) {
        try (InputStream in = NemesisExtraTests.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static void rememberDifficulty(MinecraftServer server) {
        savedDifficulty = server.getWorldData().getDifficulty();
        if (savedDifficulty == Difficulty.PEACEFUL) {
            server.setDifficulty(Difficulty.NORMAL, true);
        }
    }

    private static void restoreDifficulty(MinecraftServer server) {
        if (savedDifficulty != null) {
            server.setDifficulty(savedDifficulty, true);
            savedDifficulty = null;
        }
    }
}
