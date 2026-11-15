package pers.roinflam.kuvalich.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.KuvaStateKind;
import pers.roinflam.kuvalich.mercy.KuvaStateServer;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.nemesis.EncounterEnd;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.nemesis.SummonMode;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Collections;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 怜悯之击的其余规则：绕过倒地的死、野生玄骸、谁能处决、奴仆的资格人、处决被打断和离场、倒地超时、奖励的规矩、
 * 处决者无敌、套路不重复、默认值、开关关掉、演示目标不写数据也不存盘、存读档、和平难度、特殊状态包的限流，
 * 以及管理命令每个子命令走一遍。
 *
 * <p>处决时间轴在每个服务端 tick 末尾推进一步：从开始处决的那个测试回调起算，第 k 个测试 tick 时处决正好走了 k 步，
 * 下面的「tick 20」都是这个意思。会放出玄骸的、会动的、改配置或难度的、动全局计数和冻结开关的，各自单独一个 batch；
 * 只摆不动的实体的几条放在 {@link #MORE} 里一起跑。</p>
 *
 * <p>{@link #summonFront} 召出来的玄骸先摆到最后一段（下次处决三刀）、打掉护盾；/kill 那条用刚召出来的原样（第一段、满护盾）。</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MercyTests {

    private static final String MORE = "kl_mercy_more";
    private static final String ISO_KILL = "kl_mercy_iso_killBypasses";
    private static final String ISO_WILD = "kl_mercy_iso_wildLichDies";
    private static final String ISO_OWNER = "kl_mercy_iso_onlyOwnerExecutes";
    private static final String ISO_ELIGIBLE = "kl_mercy_iso_thrallEligibility";
    private static final String ISO_INTERRUPT = "kl_mercy_iso_interruptBeforeSettle";
    private static final String ISO_AFTER_OK = "kl_mercy_iso_interruptAfterSettle";
    private static final String ISO_AFTER_FAIL = "kl_mercy_iso_interruptAfterSettleFail";
    private static final String ISO_EXPIRE = "kl_mercy_iso_thrallExpireNoReward";
    private static final String CFG_REWARD = "kl_cfg_thrallRewardRules";
    private static final String ISO_UNCLAIMED = "kl_mercy_iso_lichUnclaimed";
    private static final String DIFF_INVULN = "kl_mercy_diff_executorInvulnerable";
    private static final String ISO_VARIANT = "kl_mercy_iso_variantNoRepeat";
    private static final String CFG_DISABLED = "kl_cfg_mercyDisabled";
    private static final String ISO_DEPART = "kl_mercy_iso_departureDuringExecution";
    private static final String ISO_DEPART_LATE = "kl_mercy_iso_departureAfterSettle";
    private static final String ISO_DEMO = "kl_mercy_iso_demoNoWrites";
    private static final String DIFF_PEACEFUL = "kl_mercy_diff_peacefulThrall";
    private static final String ISO_STATE = "kl_mercy_iso_stateFxRateLimit";
    private static final String ISO_COMMANDS = "kl_mercy_iso_commandsSmoke";

    /** 定案的那一 tick；五套玄骸的节拍现在一样，按正面直刺取 */
    private static final int SETTLE = MercyBeats.lich(MercyVariant.LICH_A).settleTick;
    /** 奴仆处决：处决者解锁的那一 tick（几套都一样） */
    private static final int THRALL_UNLOCK = MercyBeats.thrall(MercyVariant.THRALL_1).unlockTick;
    /** 第二刀判错的失败：第二格亮的那一 tick 之后给进度 */
    private static final int FAIL2_PROGRESS_TICK = MercyBeats.lich(MercyVariant.LICH_A).fillTick(2)
            + MercyBeats.lich(MercyVariant.LICH_A).failProgressTicks;

    /** 收到过死亡事件的实体（只增不减，按 UUID 查） */
    private static final Set<UUID> DIED = Collections.synchronizedSet(new HashSet<>());
    private static boolean listening;

    /** 改难度的 batch 收尾时改回去 */
    private static Difficulty savedDifficulty;

    private MercyTests() {
    }

    // ==================== 节拍表 ====================

    /**
     * 服务端的 tick 和客户端的毫秒对得上：同时发生的拍相差不到半 tick，必须在客户端之后的拍（挣开转身、奴仆落地）
     * 不早于客户端
     */
    @GameTest(template = "empty")
    public static void beatsServerMatchesClient(GameTestHelper h) {
        int tick = MercyBeats.TICK_MS;
        for (MercyVariant v : MercyVariant.values()) {
            if (v.thrall) {
                MercyBeats.Thrall b = MercyBeats.thrall(v);
                near(h, v + " 奴仆定案", b.settleTick, b.stab);
                near(h, v + " 奴仆死亡", b.deathTick, b.death);
                near(h, v + " 奴仆解锁", b.unlockTick, b.end);
                h.assertTrue(v.relocates() == (b.land >= 0), v + " 换站位的套路才有落地时刻");
                continue;
            }
            MercyBeats.Lich b = MercyBeats.lich(v);
            for (int k = 1; k <= 3; k++) {
                near(h, v + " 第 " + k + " 格", b.fillTick(k), b.fill(k));
            }
            h.assertTrue(b.settleTick == b.fillTick(1), v + " 定案就是第一格填满");
            near(h, v + " 背对的转过来", b.closeupTurnTick, b.closeup);
            near(h, v + " 成功死亡", b.successDeathTick, b.death);
            near(h, v + " 成功发奖", b.successRewardTick, b.returnLight);
            near(h, v + " 成功完成", b.successCompleteTick, b.successEnd);
            near(h, v + " 判错推开", b.failPushTicks, b.failBreakFree);
            near(h, v + " 判错离场", b.failLeaveTicks, b.failEscape);
            near(h, v + " 判错解锁", b.failUnlockTicks, b.failEnd);
            near(h, v + " 挣开推开", b.advanceRiseTicks, b.advanceBreakFree);
            near(h, v + " 挣开站稳", b.advanceStandTicks, b.advanceEnd);
            h.assertTrue(b.choiceOpen == b.closeup, v + " 选择窗口先等于跪地特写");
            h.assertTrue(b.closeupTurnTick * tick <= b.closeup + tick / 2, v + " 转身不该晚于特写");
        }
        h.succeed();
    }

    /** 各拍的先后：换站位在轨道走完之后、转身在客户端转完之后、通讯在第一格之后、刀间隔不小于下限，三条分支的步骤顺序 */
    @GameTest(template = "empty")
    public static void beatsOrder(GameTestHelper h) {
        int tick = MercyBeats.TICK_MS;
        for (MercyVariant v : MercyVariant.values()) {
            if (v.thrall) {
                MercyBeats.Thrall b = MercyBeats.thrall(v);
                if (b.land >= 0) {
                    h.assertTrue(b.landTick * tick >= b.land, v + " 换站位 " + b.landTick * tick + " 早于轨道终点 " + b.land);
                    h.assertTrue(b.landTick < b.unlockTick, v + " 落地要在解锁之前");
                }
                h.assertTrue(b.settleTick < b.deathTick && b.deathTick < b.unlockTick, v + " 奴仆：定案 < 死亡 < 解锁");
                h.assertTrue(b.bladeGone < b.end, v + " 刀模在交还前缩完");
                continue;
            }
            MercyBeats.Lich b = MercyBeats.lich(v);
            for (int i = 0; i < 3; i++) {
                h.assertTrue(b.fill(i + 1) >= b.stab(i), v + " 第 " + (i + 1) + " 格不能早于刺入");
            }
            for (int i = 0; i < 2; i++) {
                int gap = b.stab(i + 1) - b.stab(i);
                h.assertTrue(gap >= b.minGap(i), v + " 第 " + (i + 1) + "、" + (i + 2) + " 刀间隔 " + gap + " 小于下限 " + b.minGap(i));
            }
            h.assertTrue(b.stabCommTick > b.settleTick && b.stabCommTick * tick > b.fill(1), v + " 通讯要在第一格填满之后");
            for (int k = 1; k <= 2; k++) {
                int serverTurn = (b.fillTick(k) + b.advanceTurnTicks) * tick;
                int clientTurn = b.fill(k) + b.advanceTurnTo;
                h.assertTrue(serverTurn >= clientTurn, v + " 第 " + k + " 次挣开：服务端转身 " + serverTurn + " 早于客户端转完 " + clientTurn);
            }
            if (v == MercyVariant.LICH_E) {
                h.assertTrue(b.stab(1) < b.blink && b.blink < b.appear && b.appear < b.stab(2), "踏背：第二刀 < 化光 < 现身 < 第三刀");
            } else {
                h.assertTrue(b.blink < 0 && b.appear < 0, v + " 只有踏背下刺有化光、现身");
            }
            // 成功
            ascending(h, v + " 成功", b.settleTick, b.stabCommTick, b.closeupTurnTick, b.successDeathTick, b.successRewardTick,
                    b.successCompleteTick);
            ascending(h, v + " 成功（客户端）", b.stab(2), b.verdict, b.lastSpark, b.commDismiss, b.closeup, b.hudSuccessFade,
                    b.third, b.drawOut, b.death, b.returnLight, b.successEnd);
            // 判错（g 之后的相对 tick）
            ascending(h, v + " 判错", b.failPushTicks, b.failTauntLineTicks, b.failLeaveTicks, b.failUnlockTicks,
                    b.failRemoveTicks, b.failChatTicks, b.failProgressTicks);
            ascending(h, v + " 判错（客户端）", b.failGrowl, b.failBreakFree, b.failWide, b.failEscape, b.failEnd, b.failBanner,
                    b.failHud);
            // 挣开
            ascending(h, v + " 挣开", b.advanceRiseTicks, b.advanceTurnTicks, b.advanceStandTicks);
            ascending(h, v + " 挣开（客户端）", b.advanceGrowl, b.advanceBreakFree, b.advanceTurnFrom, b.advanceTurnTo,
                    b.advanceEnd);
            h.assertTrue(b.fillTick(1) + b.failPushTicks > b.settleTick, v + " 第一刀判错：推开在定案之后");
            int[] seen = b.serverVisibleMs(3, 3);
            h.assertTrue(seen[0] == b.settleTick * tick && seen[seen.length - 1] == b.successCompleteTick * tick,
                    v + " 服务端可见时刻从定案到完成：" + java.util.Arrays.toString(seen));
        }
        h.succeed();
    }

    /** 正面直刺「短—长」、背后锁颈「长—短」、贴身上挑「连珠」，玄骸的 hit1 / hit2 片段长度跟刀间隔走（动画文件是脚本生成的，改表忘了重新生成就会对不上） */
    @GameTest(template = "empty")
    public static void lichHitClipsFollowBeats(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_A);
        int gap1 = b.stab(1) - b.stab(0), gap2 = b.stab(2) - b.stab(1);
        h.assertTrue(gap1 < gap2, "正面直刺应当第二刀紧跟、第三刀前留蓄力：" + gap1 + " / " + gap2);
        com.google.gson.JsonObject anims;
        try (java.io.InputStream in = MercyTests.class.getResourceAsStream(
                "/assets/" + Reference.MOD_ID + "/animations/kuva_mercy.animation.json")) {
            h.assertTrue(in != null, "找不到 kuva_mercy.animation.json");
            anims = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        MercyBeats.Lich bb = MercyBeats.lich(MercyVariant.LICH_B);
        int b1 = bb.stab(1) - bb.stab(0), b2 = bb.stab(2) - bb.stab(1);
        h.assertTrue(b1 > b2, "背后锁颈应当第一刀后勒住憋一下、后两刀连着：" + b1 + " / " + b2);
        MercyBeats.Lich bc = MercyBeats.lich(MercyVariant.LICH_C);
        int c1 = bc.stab(1) - bc.stab(0), c2 = bc.stab(2) - bc.stab(1);
        h.assertTrue(c1 == c2 && c1 < Math.min(gap1, b2) + 20, "贴身上挑应当三刀等距连着挑：" + c1 + " / " + c2);
        MercyBeats.Lich bd = MercyBeats.lich(MercyVariant.LICH_D);
        int d1 = bd.stab(1) - bd.stab(0), d2 = bd.stab(2) - bd.stab(1);
        h.assertTrue(d1 > gap1 + 100 && d2 > d1 + 200 && d2 > gap2,
                "翻滚绕背应当两次都长、第三刀前留够翻滚：" + d1 + " / " + d2);
        int[][] gaps = {{gap1, gap2}, {b1, b2}, {c1, c2}, {d1, d2}};
        String[] keys = {"a", "b", "c", "d"};
        for (int v = 0; v < keys.length; v++) {
            for (int k = 1; k <= 2; k++) {
                String name = "mercy.lich." + keys[v] + ".hit" + k;
                h.assertTrue(anims.has(name), "缺片段 " + name);
                int len = Math.round(anims.getAsJsonObject(name).get("animation_length").getAsFloat() * 1000.0F);
                h.assertTrue(Math.abs(len - gaps[v][k - 1]) <= 1,
                        name + " 长 " + len + " ms，刀间隔是 " + gaps[v][k - 1] + " ms");
            }
        }
        h.succeed();
    }

    private static void near(GameTestHelper h, String what, int ticks, int ms) {
        int d = ticks * MercyBeats.TICK_MS - ms;
        h.assertTrue(Math.abs(d) <= MercyBeats.TICK_MS / 2, what + "：服务端 " + ticks + " tick，客户端 " + ms + " ms");
    }

    private static void ascending(GameTestHelper h, String what, int... t) {
        for (int i = 1; i < t.length; i++) {
            h.assertTrue(t[i] > t[i - 1], what + "：第 " + i + " 拍 " + t[i] + " 不晚于前一拍 " + t[i - 1]);
        }
    }

    // ==================== #3 /kill、虚空：绕过倒地直接死 ====================

    @GameTest(template = "empty", batch = ISO_KILL, timeoutTicks = 200)
    public static void killBypasses(GameTestHelper h) {
        listenDeaths();
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        openArena(h, c.getX() - 9, c.getX() + 9, c.getZ() - 3, c.getZ() + 11);
        ServerPlayer a = KuvaTestKit.player(h);
        ServerPlayer b = KuvaTestKit.player(h);
        // 两个人各站一边，各自的玄骸召到各自面前
        Vec3 mid = h.absoluteVec(new Vec3(c.getX() + 0.5D, 1.0D, c.getZ() + 0.5D));
        a.teleportTo(h.getLevel(), mid.x - 5.0D, mid.y, mid.z, 0.0F, 0.0F);
        b.teleportTo(h.getLevel(), mid.x + 5.0D, mid.y, mid.z, 0.0F, 0.0F);
        RequiemCard ca = card(a);
        RequiemCard cb = card(b);
        bornByProgress(a, ca);
        bornByProgress(b, cb);
        h.assertTrue(LichDirector.summonNow(a, SummonMode.FRONT) && LichDirector.summonNow(b, SummonMode.FRONT),
                "两只都应当召得出来");
        KuvaMasterEntity la = LichDirector.getLich(a).orElseThrow(() -> new IllegalStateException("a 应当有在场的玄骸"));
        KuvaMasterEntity lb = LichDirector.getLich(b).orElseThrow(() -> new IllegalStateException("b 应当有在场的玄骸"));
        a.setGameMode(GameType.CREATIVE);
        b.setGameMode(GameType.CREATIVE);
        int[] before = {ca.getDecryptionProgress(), ca.getUnlockedCardStatus(), ca.getKuvaLevel(),
                cb.getDecryptionProgress(), cb.getUnlockedCardStatus(), cb.getKuvaLevel()};
        h.runAfterDelay(2, () -> {
            la.forceDowned(a);
            lb.forceDowned(b);
            h.assertTrue(la.getMercyState() == MercyState.DOWNED && lb.getMercyState() == MercyState.DOWNED, "两只都应当先倒地");
            AABB area = new AABB(BlockPos.containing(mid)).inflate(14.0D);
            clearItems(h, area);
            // 照真实情形来：/kill 走命令，虚空要真掉到世界底下 64 格以外（别的模组借这两种伤害源的真伤打不死倒地的）
            KuvaTestKit.killCommand(h, la);
            lb.teleportTo(lb.getX(), h.getLevel().getMinBuildHeight() - 70.0D, lb.getZ());
            lb.hurt(lb.damageSources().fellOutOfWorld(), 1000.0F);
            h.assertTrue(!la.isAlive() && DIED.contains(la.getUUID()), "/kill 应当直接打死倒地的玄骸：" + aliveDetail(la)
                    + "，护盾 " + la.getAbsorptionAmount() + "，死亡事件 " + DIED.contains(la.getUUID()));
            h.assertTrue(!lb.isAlive() && DIED.contains(lb.getUUID()), "掉出世界应当直接打死倒地的玄骸：" + aliveDetail(lb)
                    + "，护盾 " + lb.getAbsorptionAmount() + "，死亡事件 " + DIED.contains(lb.getUUID()));
            h.runAfterDelay(22, () -> {
                h.assertTrue(la.isRemoved() && lb.isRemoved(), "死后 20 tick 内应当移除");
                h.assertTrue(la.getEncounterEnd() == EncounterEnd.KILLED_OTHER, "/kill 的结束原因应当是 KILLED_OTHER：" + la.getEncounterEnd());
                h.assertTrue(lb.getEncounterEnd() == EncounterEnd.KILLED_OTHER, "虚空的结束原因应当是 KILLED_OTHER：" + lb.getEncounterEnd());
                int[] after = {ca.getDecryptionProgress(), ca.getUnlockedCardStatus(), ca.getKuvaLevel(),
                        cb.getDecryptionProgress(), cb.getUnlockedCardStatus(), cb.getKuvaLevel()};
                h.assertTrue(java.util.Arrays.equals(before, after), "主人的进度和玄骸等级都不该变："
                        + java.util.Arrays.toString(before) + " → " + java.util.Arrays.toString(after));
                h.assertTrue(itemsIn(h, area) == 0, "地上不应当有掉落物：" + itemNames(h, area));
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_KILL)
    public static void afterKill(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #4 刷怪蛋出来的无主玄骸：直接死、不倒地 ====================

    @GameTest(template = "empty", batch = ISO_WILD, timeoutTicks = 100)
    public static void wildLichDies(GameTestHelper h) {
        listenDeaths();
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        KuvaMasterEntity lich = spawnWildLich(h, p.position().add(0.0D, 0.0D, 3.0D));
        h.assertTrue(lich.isWild() && !lich.isOwned(), "应当是无主的野生玄骸");
        lich.setHealth(1.0F);
        lich.hurt(p.damageSources().playerAttack(p), 20.0F);
        h.assertTrue(!lich.isAlive() && DIED.contains(lich.getUUID()), "野生玄骸应当直接死");
        h.assertTrue(lich.getMercyState() == MercyState.NONE, "野生玄骸不应当倒地：" + lich.getMercyState());
        h.succeed();
    }

    @AfterBatch(batch = ISO_WILD)
    public static void afterWild(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #5 别人打倒的有主玄骸只有主人能处决 ====================

    @GameTest(template = "empty", batch = ISO_OWNER, timeoutTicks = 200)
    public static void onlyOwnerExecutes(GameTestHelper h) {
        listenDeaths();
        ServerPlayer owner = KuvaTestKit.player(h);
        ServerPlayer other = KuvaTestKit.player(h);
        bornByProgress(owner, card(owner));
        KuvaMasterEntity lich = summonFront(h, owner);
        owner.setGameMode(GameType.CREATIVE);
        other.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(KuvaMasterEntity.APPEAR_GRACE_TICKS + 8, () -> {
            lich.setHealth(1.0F);
            h.assertTrue(lich.hurt(other.damageSources().playerAttack(other), 20.0F), "别人的致命一击应当打得进去");
            h.assertTrue(lich.isAlive() && lich.getMercyState() == MercyState.DOWNED && !DIED.contains(lich.getUUID()),
                    "应当倒地而不是死：" + lich.getMercyState());
            h.assertTrue(!lich.canBeExecutedBy(other) && lich.canBeExecutedBy(owner), "只有主人能处决");
            h.assertTrue(lich.getMercyActorId() == owner.getId(), "资格人应当是主人");
            standBy(other, lich, 2.0D, 90.0F);
            MercyServer.handleRequest(other, lich.getId());
            h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "别人请求后应当还在倒地：" + lich.getMercyState());
            standBy(owner, lich, 2.0D, -90.0F);
            MercyServer.handleRequest(owner, lich.getId());
            h.assertTrue(lich.getMercyState() == MercyState.EXECUTING, "主人请求后应当开始处决：" + lich.getMercyState());
            // 收掉这一场（定案前，什么都不写）
            MercyServer.onExecutorGone(owner, MercyServer.Gone.LOGOUT);
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_OWNER)
    public static void afterOwner(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #6 奴仆的资格人：自己打倒、宠物咬死、岩浆烧死 ====================

    @GameTest(template = "empty", batch = ISO_ELIGIBLE, timeoutTicks = 100)
    public static void thrallEligibility(GameTestHelper h) {
        clearAround(h);
        ServerPlayer a = KuvaTestKit.player(h);
        ServerPlayer b = KuvaTestKit.player(h);
        a.setGameMode(GameType.CREATIVE);
        b.setGameMode(GameType.CREATIVE);
        Vec3 base = a.position();
        KuvaSlaveEntity bySword = spawnThrall(h, base.add(2.0D, 0.0D, 2.0D));
        KuvaSlaveEntity byWolf = spawnThrall(h, base.add(-2.0D, 0.0D, 2.0D));
        KuvaSlaveEntity byLava = spawnThrall(h, base.add(0.0D, 0.0D, 4.0D));
        Wolf wolf = EntityType.WOLF.create(h.getLevel());
        h.assertTrue(wolf != null, "应当能造出狼");
        wolf.moveTo(base.x, base.y, base.z - 2.0D, 0.0F, 0.0F);
        wolf.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(wolf), "狼应当加得进世界");
        wolf.tame(a);

        bySword.setHealth(1.0F);
        bySword.hurt(a.damageSources().playerAttack(a), 20.0F);
        byWolf.setHealth(1.0F);
        byWolf.hurt(h.getLevel().damageSources().mobAttack(wolf), 20.0F);
        byLava.setHealth(1.0F);
        byLava.hurt(h.getLevel().damageSources().lava(), 20.0F);
        for (KuvaSlaveEntity t : List.of(bySword, byWolf, byLava)) {
            h.assertTrue(t.isAlive() && t.getMercyState() == MercyState.DOWNED, "三只都应当倒地：" + downedDetail(h, t));
        }
        h.assertTrue(a.getUUID().equals(bySword.getMercyQualified()) && bySword.getMercyActorId() == a.getId(),
                "自己打倒的：资格人是 a");
        h.assertTrue(a.getUUID().equals(byWolf.getMercyQualified()) && byWolf.getMercyActorId() == a.getId(),
                "a 的狼咬死的：资格人是 a");
        h.assertTrue(byLava.getMercyQualified() == null && byLava.getMercyActorId() == -1,
                "岩浆烧死的：没有资格人，实际 " + byLava.getMercyActorId());

        standBy(b, bySword, 1.8D, 0.0F);
        MercyServer.handleRequest(b, bySword.getId());
        h.assertTrue(bySword.getMercyState() == MercyState.DOWNED, "b 请求 a 打倒的那只应当被拒");
        // 请求有 10 tick 的节流，隔开再请求第二只
        h.runAfterDelay(12, () -> {
            standBy(b, byWolf, 1.8D, 0.0F);
            MercyServer.handleRequest(b, byWolf.getId());
            h.assertTrue(byWolf.getMercyState() == MercyState.DOWNED, "b 请求 a 的狼咬死的那只应当被拒");
            standBy(a, byLava, 1.8D, 0.0F);
            MercyServer.handleRequest(a, byLava.getId());
            h.assertTrue(byLava.getMercyState() == MercyState.DOWNED, "没有资格人的那只谁请求都不行");
            bySword.discard();
            byWolf.discard();
            byLava.discard();
            wolf.discard();
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_ELIGIBLE)
    public static void afterEligible(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #10 定案前被打断：作废、回倒地 ====================

    @GameTest(template = "empty", batch = ISO_INTERRUPT, timeoutTicks = 200)
    public static void interruptBeforeSettle(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            Snapshot before = Snapshot.of(card);
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            h.runAfterDelay(10, () -> {
                h.assertTrue(MercyServer.ageOf(lich) == 10, "处决应当走了 10 步，实际 " + MercyServer.ageOf(lich));
                MercyServer.onExecutorGone(p, MercyServer.Gone.LOGOUT);
                h.assertTrue(lich.isAlive() && lich.getMercyState() == MercyState.DOWNED, "应当回到倒地：" + lich.getMercyState());
                h.assertTrue(lich.getMercyRemainingServer() >= 100, "剩余应当至少 100 tick，实际 " + lich.getMercyRemainingServer());
                h.assertTrue(MercyServer.ageOf(lich) == -1, "这一场应当已经作废");
                before.assertSame(h, card, "定案前中断");
            });
            // 解锁后还有 10 tick 的无敌余量，过了余量才算不忙
            h.runAfterDelay(10 + MercyServer.GRACE_TICKS + 2, () -> {
                h.assertTrue(!MercyServer.isBusy(p), "处决者应当已经解锁");
                before.assertSame(h, card, "定案前中断之后");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_INTERRUPT)
    public static void afterInterrupt(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #11 定案后被打断：快进 ====================

    @GameTest(template = "empty", batch = ISO_AFTER_OK, timeoutTicks = 200)
    public static void interruptAfterSettle(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            AABB area = settleArea(lich, p);
            clearItems(h, area);
            h.runAfterDelay(20, () -> {
                h.assertTrue(lich.isAlive(), "tick 20 还活着：" + aliveDetail(lich));
                h.assertTrue(itemsIn(h, area) == 0, "tick 20 还没有掉落：" + itemNames(h, area) + "；玄骸 " + aliveDetail(lich));
                h.assertTrue(!card.hasLich(), "tick 17 已经定案、这一轮已经结束");
                MercyServer.onExecutorGone(p, MercyServer.Gone.LOGOUT);
                h.assertTrue(!lich.isAlive() && DIED.contains(lich.getUUID()), "成功定案后下线：玄骸当 tick 死亡");
                h.assertTrue(MercyCoreTests.reliquaries(p) == 1 && !PlayerLedger.hasFinale(p), "成功定案后下线：当 tick 落定");
                h.assertTrue(itemsIn(h, area) == 0, "奖励不该掉在地上：" + itemNames(h, area));
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_AFTER_OK)
    public static void afterSettleOk(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = ISO_AFTER_FAIL, timeoutTicks = 200)
    public static void interruptAfterSettleFail(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 1);
            int progress = card.getDecryptionProgress();
            int stage = card.getUnlockedCardStatus();
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            h.runAfterDelay(20, () -> {
                h.assertTrue(lich.getMercyState() == MercyState.EXECUTING, "tick 20 还在处决中");
                h.assertTrue(card.getDecryptionProgress() == progress && card.getUnlockedCardStatus() == stage,
                        "tick 20 还没给进度");
                MercyServer.onExecutorGone(p, MercyServer.Gone.LOGOUT);
                h.assertTrue(lich.getMercyState() == MercyState.LEAVING, "失败定案后下线：当 tick 进离场，实际 " + lich.getMercyState());
                h.assertTrue(card.getDecryptionProgress() != progress || card.getUnlockedCardStatus() != stage,
                        "失败定案后下线：进度当 tick 到账");
                h.assertTrue(!DIED.contains(lich.getUUID()), "失败不应当死");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_AFTER_FAIL)
    public static void afterSettleFail(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #14 奴仆倒地超时：消散，什么都不给 ====================

    @GameTest(template = "empty", batch = ISO_EXPIRE, timeoutTicks = 160)
    public static void thrallExpireNoReward(GameTestHelper h) {
        listenDeaths();
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        card.setDecryptionProgress(0);
        int stage = card.getUnlockedCardStatus();
        KuvaSlaveEntity thrall = spawnThrall(h, p.position().add(0.0D, 0.0D, 2.0D));
        thrall.setHealth(1.0F);
        thrall.hurt(p.damageSources().playerAttack(p), 20.0F);
        h.assertTrue(thrall.getMercyState() == MercyState.DOWNED, "应当倒地");
        // 倒地时长的配置下限是 10 秒，这里用命令同一个入口把剩余时间改成 2 秒
        h.assertTrue(MercyServer.setTimer(thrall, 2), "改倒地剩余时间应当成功");
        AABB area = new AABB(thrall.blockPosition()).inflate(12.0D);
        clearItems(h, area);
        h.runAfterDelay(37, () -> h.assertTrue(thrall.getMercyState() == MercyState.DOWNED, "tick 37 还在倒地"));
        h.runAfterDelay(42, () -> h.assertTrue(thrall.getMercyState() == MercyState.LEAVING && !thrall.isRemoved(),
                "tick 40 前后应当开始消散：" + thrall.getMercyState()));
        h.runAfterDelay(66, () -> h.assertTrue(!thrall.isRemoved(), "消散 30 tick 之内还在"));
        h.runAfterDelay(74, () -> {
            h.assertTrue(thrall.isRemoved(), "tick 70 前后应当移除");
            h.assertTrue(itemsIn(h, area) == 0, "不应当有掉落物：" + itemNames(h, area));
            h.assertTrue(card.getDecryptionProgress() == 0 && card.getUnlockedCardStatus() == stage, "进度不该变");
            h.assertTrue(!DIED.contains(thrall.getUUID()), "消散不触发死亡事件");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_EXPIRE)
    public static void afterExpire(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #15 奴仆的奖励：/kill 什么都不给；奴仆不倒地时打死当场给 ====================

    @GameTest(template = "empty", batch = CFG_REWARD, timeoutTicks = 100)
    public static void thrallRewardRules(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.slaveKuvaDropChance, 100);
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        card.setDecryptionProgress(0);
        int stage = card.getUnlockedCardStatus();
        AABB area = new AABB(p.blockPosition()).inflate(12.0D);
        clearItems(h, area);
        KuvaSlaveEntity killed = spawnThrall(h, p.position().add(0.0D, 0.0D, 2.0D));
        KuvaTestKit.killCommand(h, killed);
        h.assertTrue(!killed.isAlive(), "/kill 应当直接打死奴仆");
        h.runAfterDelay(2, () -> {
            h.assertTrue(itemsIn(h, area) == 0, "/kill 不应当有掉落：" + itemNames(h, area));
            h.assertTrue(card.getDecryptionProgress() == 0 && card.getUnlockedCardStatus() == stage, "/kill 不给进度");
            KuvaTestKit.override(ModConfig.KUVA_LICH.mercyThralls, false);
            KuvaSlaveEntity slain = spawnThrall(h, p.position().add(0.0D, 0.0D, -2.0D));
            slain.setHealth(1.0F);
            slain.hurt(p.damageSources().playerAttack(p), 20.0F);
            h.assertTrue(!slain.isAlive() && slain.getMercyState() == MercyState.NONE, "奴仆不倒地时应当直接死");
            int got = card.getDecryptionProgress();
            int min = ModConfig.KUVA_LICH.minDecryptionProgress.get();
            int max = ModConfig.KUVA_LICH.maxDecryptionProgress.get();
            h.assertTrue(card.getUnlockedCardStatus() == stage, "这一下不该跨阶段（测试前提）");
            h.assertTrue(got >= min && got <= max, "当场给进度，应当在 [" + min + ", " + max + "]，实际 " + got);
            h.assertTrue(itemsIn(h, area) > 0, "当场有掉落");
            h.succeed();
        });
    }

    @AfterBatch(batch = CFG_REWARD)
    public static void afterReward(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #16 有主玄骸倒地超时：前两次站起来接着打，连着第三次逃走，什么都不写 ====================

    @GameTest(template = "empty", batch = ISO_UNCLAIMED, timeoutTicks = 320)
    public static void lichUnclaimed(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            int level = card.getKuvaLevel();
            int attempts = card.getAttempts().length;
            Runnable third = () -> {
                lich.forceDowned(p);
                h.assertTrue(MercyServer.setTimer(lich, 2), "改倒地剩余时间应当成功");
                h.runAfterDelay(37, () -> h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "第三次：tick 37 还在倒地"));
                h.runAfterDelay(43, () -> {
                    h.assertTrue(lich.getMercyState() == MercyState.LEAVING || lich.isRemoved(),
                            "第三次：tick 40 前后应当逃走：" + lich.getMercyState());
                    h.assertTrue(lich.getEncounterEnd() == EncounterEnd.UNCLAIMED, "结束原因应当是 UNCLAIMED：" + lich.getEncounterEnd());
                    h.assertTrue(card.getKuvaLevel() == level && card.getAttempts().length == attempts, "等级和尝试记录都不该变");
                    h.assertTrue(card.hasLich(), "玄骸只是逃走，这一轮还在");
                    h.succeed();
                });
            };
            unclaimedRise(h, p, lich, 1, () -> unclaimedRise(h, p, lich, 2, third));
        });
    }

    /** 打倒、2 秒后没人处决：站起来（这一段回满），起身演完回到能打的状态，再接下一步 */
    private static void unclaimedRise(GameTestHelper h, ServerPlayer p, KuvaMasterEntity lich, int nth, Runnable next) {
        lich.forceDowned(p);
        h.assertTrue(MercyServer.setTimer(lich, 2), "第 " + nth + " 次：改倒地剩余时间应当成功");
        h.runAfterDelay(37, () -> h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "第 " + nth + " 次：tick 37 还在倒地"));
        h.runAfterDelay(43, () -> {
            h.assertTrue(lich.getMercyState() == MercyState.RISING, "第 " + nth + " 次：tick 40 前后应当站起来："
                    + lich.getMercyState());
            h.assertTrue(lich.getUnclaimedDowns() == nth, "连着没人处决的次数应当是 " + nth + "，实际 " + lich.getUnclaimedDowns());
            h.assertTrue(Math.abs(lich.getHealth() - lich.segmentCeil()) < 1.0E-3F, "站起来时这一段回满：" + lich.getHealth()
                    + " / " + lich.segmentCeil());
            h.assertTrue(lich.getEncounterEnd() == null, "站起来不结束遭遇");
        });
        h.runAfterDelay(43 + MercyServer.RISE_TICKS + 2, () -> {
            h.assertTrue(lich.getMercyState() == MercyState.NONE, "第 " + nth + " 次：起身演完应当回到能打的状态："
                    + lich.getMercyState());
            next.run();
        });
    }

    @AfterBatch(batch = ISO_UNCLAIMED)
    public static void afterUnclaimed(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #17 处决者在处决中和余量里不受伤 ====================

    @BeforeBatch(batch = DIFF_INVULN)
    public static void beforeInvulnerable(ServerLevel level) {
        rememberDifficulty(level.getServer());
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
    }

    @GameTest(template = "empty", batch = DIFF_INVULN, timeoutTicks = 300)
    public static void executorInvulnerable(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        KuvaSlaveEntity thrall = spawnThrall(h, p.position().add(0.0D, 0.0D, 2.0D));
        Zombie zombie = EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(zombie != null, "应当能造出僵尸");
        zombie.moveTo(p.getX() + 2.0D, p.getY(), p.getZ(), 0.0F, 0.0F);
        zombie.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(zombie), "僵尸应当加得进世界");
        // 新进服的玩家有 3 秒出生保护，等它过去
        h.runAfterDelay(80, () -> {
            p.setHealth(p.getMaxHealth());
            thrall.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, thrall) == null, "应当能开始处决");
            h.runAfterDelay(5, () -> h.assertTrue(!zombieHits(p, zombie), "处决中不应当掉血"));
            h.runAfterDelay(THRALL_UNLOCK + 3, () -> h.assertTrue(!zombieHits(p, zombie), "解锁后的余量里也不应当掉血"));
            h.runAfterDelay(THRALL_UNLOCK + MercyServer.GRACE_TICKS + 4, () -> {
                h.assertTrue(zombieHits(p, zombie), "过了余量应当掉血");
                zombie.discard();
                h.succeed();
            });
        });
    }

    /** 让僵尸打一下，返回掉没掉血 */
    private static boolean zombieHits(ServerPlayer p, Zombie zombie) {
        float before = p.getHealth();
        p.invulnerableTime = 0;
        p.hurt(p.damageSources().mobAttack(zombie), 4.0F);
        return p.getHealth() < before - 1.0E-3F;
    }

    @AfterBatch(batch = DIFF_INVULN)
    public static void afterInvulnerable(ServerLevel level) {
        restoreDifficulty(level.getServer());
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #18 同一个人相邻两次不用同一套 ====================

    @GameTest(template = "empty", batch = ISO_VARIANT, timeoutTicks = 100)
    public static void variantNoRepeat(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        KuvaSlaveEntity thrall = spawnDemoThrall(h, p.position().add(1.5D, 0.0D, 2.0D));
        KuvaMasterEntity lich = spawnDemoLich(h, p.position().add(-1.5D, 0.0D, 2.0D));
        thrall.forceDowned(p);
        lich.forceDowned(p);
        h.assertTrue(thrall.getMercyState() == MercyState.DOWNED && lich.getMercyState() == MercyState.DOWNED,
                "两只演示目标都应当倒地");
        countVariants(h, p, thrall, true);
        countVariants(h, p, lich, false);
        thrall.discard();
        lich.discard();
        h.succeed();
    }

    /** 开始 300 次、每次马上中止（定案前中止什么都不写），记下服务端每次选的套路 */
    private static void countVariants(GameTestHelper h, ServerPlayer p, AbstractKuva target, boolean thrall) {
        Map<MercyVariant, Integer> counts = new EnumMap<>(MercyVariant.class);
        MercyVariant last = null;
        int rounds = 300;
        for (int i = 0; i < rounds; i++) {
            String err = MercyServer.executeNow(p, target);
            h.assertTrue(err == null, "第 " + i + " 次应当能开始：" + err);
            MercyVariant v = MercyPlan.decode(target.getMercyPlan()).variant();
            h.assertTrue(v.thrall == thrall, "选出的套路类别不对：" + v);
            h.assertTrue(v != last, "第 " + i + " 次和上一次重复了：" + v);
            counts.merge(v, 1, Integer::sum);
            last = v;
            h.assertTrue(MercyServer.stop(p) == null, "第 " + i + " 次应当能中止");
            h.assertTrue(target.getMercyState() == MercyState.DOWNED, "中止后应当回到倒地");
        }
        // 平地上每一套都放得下，各套至少拿到平均份额的一半
        long kinds = Arrays.stream(MercyVariant.values()).filter(v -> v.thrall == thrall).count();
        int floor = (int) (rounds / kinds / 2);
        for (MercyVariant v : MercyVariant.values()) {
            if (v.thrall == thrall) {
                int n = counts.getOrDefault(v, 0);
                h.assertTrue(n >= floor, v + " 只占 " + n + " / " + rounds + "，应当 ≥ " + floor);
            }
        }
    }

    @AfterBatch(batch = ISO_VARIANT)
    public static void afterVariant(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #19 解密进度的默认值 ====================

    @GameTest(template = "empty", batch = MORE)
    public static void progressDefaults(GameTestHelper h) {
        int min = ModConfig.KUVA_LICH.minDecryptionProgress.getDefault();
        int max = ModConfig.KUVA_LICH.maxDecryptionProgress.getDefault();
        h.assertTrue(min == 6, "minDecryptionProgress 默认应当是 6，实际 " + min);
        h.assertTrue(max == 19, "maxDecryptionProgress 默认应当是 19，实际 " + max);
        h.succeed();
    }

    // ==================== #20 怜悯之击关掉：打死当场判定 ====================

    @GameTest(template = "empty", batch = CFG_DISABLED, timeoutTicks = 200)
    public static void mercyDisabled(GameTestHelper h) {
        listenDeaths();
        KuvaTestKit.override(ModConfig.KUVA_LICH.mercyEnabled, false);
        ServerPlayer p = KuvaTestKit.player(h);
        bornByProgress(p, card(p));
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(KuvaMasterEntity.APPEAR_GRACE_TICKS + 8, () -> {
            lich.setHealth(1.0F);
            lich.hurt(p.damageSources().playerAttack(p), 20.0F);
            h.assertTrue(!lich.isAlive() && DIED.contains(lich.getUUID()), "关掉后应当直接死");
            h.assertTrue(lich.getMercyState() == MercyState.NONE, "关掉后不应当倒地：" + lich.getMercyState());
            h.succeed();
        });
    }

    @AfterBatch(batch = CFG_DISABLED)
    public static void afterDisabled(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #21 处决中碰上宿敌的离场 ====================

    @GameTest(template = "empty", batch = ISO_DEPART, timeoutTicks = 200)
    public static void departureDuringExecution(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            Snapshot before = Snapshot.of(card);
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            h.runAfterDelay(10, () -> {
                lich.beginDeparture(EncounterEnd.OWNER_FAR);
                h.assertTrue(lich.isDeparting(), "定案前：玄骸照常离场");
                h.assertTrue(MercyServer.ageOf(lich) == -1, "定案前：这一场应当作废");
                h.assertTrue(!DIED.contains(lich.getUUID()), "定案前：不应当死");
                before.assertSame(h, card, "定案前离场");
            });
            h.runAfterDelay(10 + MercyServer.GRACE_TICKS + 2, () -> {
                h.assertTrue(!MercyServer.isBusy(p), "处决者应当已经解锁");
                h.assertTrue(lich.isRemoved(), "离场演完应当移除");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_DEPART)
    public static void afterDepart(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = ISO_DEPART_LATE, timeoutTicks = 200)
    public static void departureAfterSettle(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            AABB area = settleArea(lich, p);
            clearItems(h, area);
            h.runAfterDelay(20, () -> {
                h.assertTrue(lich.isAlive(), "tick 20 还活着：" + aliveDetail(lich));
                h.assertTrue(itemsIn(h, area) == 0, "tick 20 还没有掉落：" + itemNames(h, area) + "；玄骸 " + aliveDetail(lich));
                lich.beginDeparture(EncounterEnd.OWNER_FAR);
                h.assertTrue(!lich.isAlive() && DIED.contains(lich.getUUID()), "定案后：快进到死");
                h.assertTrue(!lich.isDeparting(), "定案后：不应当再放离场");
                h.assertTrue(MercyCoreTests.reliquaries(p) == 1 && !PlayerLedger.hasFinale(p), "定案后：当场落定");
                h.assertTrue(itemsIn(h, area) == 0, "奖励不该掉在地上：" + itemNames(h, area));
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_DEPART_LATE)
    public static void afterDepartLate(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #22 演示目标判错：什么都不写 ====================

    @GameTest(template = "empty", batch = ISO_DEMO, timeoutTicks = 240)
    public static void demoNoWrites(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        Snapshot before = Snapshot.of(card);
        h.assertTrue(run(p, "kuvalich mercy spawn lich") == 1, "spawn lich 应当成功");
        KuvaMasterEntity lich = nearestDemo(h, p, KuvaMasterEntity.class);
        h.assertTrue(lich != null && lich.getMercyState() == MercyState.DOWNED, "面前应当有一只倒地的演示玄骸");
        aimAt(p, lich);
        h.assertTrue(run(p, "kuvalich mercy outcome fail2") == 1, "outcome fail2 应当成功");
        h.assertTrue(run(p, "kuvalich mercy exec") == 1, "exec 应当成功");
        MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
        h.assertTrue(plan.demo() && plan.stabs() == 3 && plan.prefix() == 1,
                "计划应当是演示、三刀、第二刀错：演示 " + plan.demo() + "，刀数 " + plan.stabs() + "，前缀 " + plan.prefix());
        h.runAfterDelay(SETTLE + 1, () -> before.assertSame(h, card, "演示定案后"));
        h.runAfterDelay(FAIL2_PROGRESS_TICK + 6, () -> {
            before.assertSame(h, card, "演示演完后");
            h.assertTrue(lich.isRemoved(), "演示玄骸判错后应当逃走、移除");
            h.assertTrue(lich.getDemoFailures() == 1 && lich.getLichRank() == 2, "演示玄骸自己记一次判错、横幅等级 2");
            h.assertTrue(!MercyServer.isBusy(p), "处决者应当已经解锁");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_DEMO)
    public static void afterDemo(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #23 倒地 / 处决中的奴仆存读档 ====================

    @GameTest(template = "empty", batch = MORE, timeoutTicks = 100)
    public static void thrallSaveLoad(GameTestHelper h) {
        BlockPos c = KuvaTestKit.center(h);
        loadTestChunks(h);
        openArena(h, c.getX() - 2, c.getX() + 2, c.getZ() - 2, c.getZ() + 2);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        Vec3 base = p.position();
        KuvaSlaveEntity downed = spawnThrall(h, base.add(1.0D, 0.0D, 1.5D));
        KuvaSlaveEntity executing = spawnThrall(h, base.add(-1.0D, 0.0D, 1.5D));
        downed.forceDowned(p);
        executing.forceDowned(p);
        h.assertTrue(MercyServer.executeNow(p, executing) == null, "应当能开始处决");
        h.assertTrue(executing.getMercyState() == MercyState.EXECUTING, "第二只应当在处决中");
        KuvaSlaveEntity r1 = reload(h, downed, base.add(1.0D, 0.0D, -1.5D));
        KuvaSlaveEntity r2 = reload(h, executing, base.add(-1.0D, 0.0D, -1.5D));
        // 原来那只处决中的先收掉，免得 30 tick 后处决把它杀了、给出奖励
        MercyServer.onExecutorGone(p, MercyServer.Gone.LOGOUT);
        downed.discard();
        executing.discard();
        h.assertTrue(r1.getMercyState() == MercyState.DOWNED, "倒地的读回来应当还在倒地：" + r1.getMercyState());
        h.assertTrue(r2.getMercyState() == MercyState.DOWNED, "处决中的读回来应当按倒地：" + r2.getMercyState());
        int rem1 = r1.getMercyRemainingServer();
        int rem2 = r2.getMercyRemainingServer();
        h.assertTrue(rem1 > 20 && rem2 > 20, "剩余时间应当读回来：" + rem1 + " / " + rem2);
        h.runAfterDelay(20, () -> {
            h.assertTrue(r1.getMercyState() == MercyState.DOWNED && r2.getMercyState() == MercyState.DOWNED, "20 tick 后还在倒地");
            int d1 = rem1 - r1.getMercyRemainingServer();
            int d2 = rem2 - r2.getMercyRemainingServer();
            h.assertTrue(d1 >= 18 && d1 <= 22 && d2 >= 18 && d2 <= 22, "剩余时间应当接着走：" + d1 + " / " + d2);
            r1.discard();
            r2.discard();
            h.succeed();
        });
    }

    /** 存成 NBT 再读进一只新的（去掉 UUID，免得和原来那只撞），放到 {@code at} */
    private static KuvaSlaveEntity reload(GameTestHelper h, KuvaSlaveEntity source, Vec3 at) {
        CompoundTag tag = new CompoundTag();
        source.saveWithoutId(tag);
        tag.remove("UUID");
        KuvaSlaveEntity copy = KuvaLichEntities.KUVA_SLAVE.get().create(h.getLevel());
        h.assertTrue(copy != null, "应当能造出奴仆");
        copy.load(tag);
        copy.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        h.assertTrue(h.getLevel().addFreshEntity(copy), "读回来的奴仆应当加得进世界");
        return copy;
    }

    // ==================== #24 倒地时切和平：消散 ====================

    @BeforeBatch(batch = DIFF_PEACEFUL)
    public static void beforePeaceful(ServerLevel level) {
        rememberDifficulty(level.getServer());
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
    }

    @GameTest(template = "empty", batch = DIFF_PEACEFUL, timeoutTicks = 100)
    public static void peacefulThrall(GameTestHelper h) {
        listenDeaths();
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        RequiemCard card = card(p);
        int progress = card.getDecryptionProgress();
        int stage = card.getUnlockedCardStatus();
        KuvaSlaveEntity thrall = spawnThrall(h, p.position().add(0.0D, 0.0D, 2.0D));
        thrall.setHealth(1.0F);
        boolean hit = thrall.hurt(p.damageSources().playerAttack(p), 20.0F);
        h.assertTrue(thrall.getMercyState() == MercyState.DOWNED, "应当倒地：打进去 " + hit + "，" + downedDetail(h, thrall));
        AABB area = new AABB(thrall.blockPosition()).inflate(12.0D);
        clearItems(h, area);
        h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
        h.runAfterDelay(2, () -> h.assertTrue(thrall.getMercyState() == MercyState.LEAVING && !thrall.isRemoved(),
                "切和平后应当开始消散：" + thrall.getMercyState()));
        h.runAfterDelay(27, () -> h.assertTrue(!thrall.isRemoved(), "消散 30 tick 之内还在"));
        h.runAfterDelay(35, () -> {
            h.assertTrue(thrall.isRemoved(), "消散完应当移除");
            h.assertTrue(itemsIn(h, area) == 0, "不应当有掉落物：" + itemNames(h, area));
            h.assertTrue(card.getDecryptionProgress() == progress && card.getUnlockedCardStatus() == stage, "进度不该变");
            h.assertTrue(!DIED.contains(thrall.getUUID()), "消散不触发死亡事件");
            h.succeed();
        });
    }

    @AfterBatch(batch = DIFF_PEACEFUL)
    public static void afterPeaceful(ServerLevel level) {
        restoreDifficulty(level.getServer());
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #25 特殊状态包的限流 ====================

    @GameTest(template = "empty", batch = ISO_STATE, timeoutTicks = 100)
    public static void stateFxRateLimit(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        KuvaMasterEntity lich = spawnDemoLich(h, p.position().add(0.0D, 0.0D, 2.5D));
        long adapt0 = KuvaStateServer.sentCount(KuvaStateKind.ADAPT);
        long added0 = KuvaStateServer.sentAdaptAddedCount();
        long guard0 = KuvaStateServer.sentCount(KuvaStateKind.GUARD);
        // 1 秒内近战 20 下：每 tick 一下，先清掉受击无敌帧
        for (int i = 0; i < 20; i++) {
            h.runAfterDelay(i + 1, () -> {
                lich.invulnerableTime = 0;
                lich.hurt(p.damageSources().playerAttack(p), 4.0F);
            });
        }
        h.runAfterDelay(22, () -> {
            long added = KuvaStateServer.sentAdaptAddedCount() - added0;
            long notAdded = KuvaStateServer.sentCount(KuvaStateKind.ADAPT) - adapt0 - added;
            long guard = KuvaStateServer.sentCount(KuvaStateKind.GUARD) - guard0;
            h.assertTrue(lich.isAlive() && lich.getMercyState() == MercyState.NONE, "20 下打不倒它（测试前提）");
            h.assertTrue(added >= 1 && added <= 6, "加层的 ADAPT 应当在 1~6 个，实际 " + added);
            h.assertTrue(notAdded <= 5, "不加层的 ADAPT 应当 ≤ 5 个，实际 " + notAdded);
            h.assertTrue(guard >= 1 && guard <= 2, "GUARD 应当在 1~2 个，实际 " + guard);
            lich.discard();
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_STATE)
    public static void afterState(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #28 演示目标不存盘 ====================

    @GameTest(template = "empty", batch = MORE, timeoutTicks = 60)
    public static void demoNotSaved(GameTestHelper h) {
        BlockPos c = KuvaTestKit.center(h);
        loadTestChunks(h);
        openArena(h, c.getX() - 2, c.getX() + 2, c.getZ(), c.getZ() + 4);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        h.assertTrue(run(p, "kuvalich mercy spawn lich") == 1, "spawn lich 应当成功");
        h.assertTrue(run(p, "kuvalich mercy spawn thrall") == 1, "spawn thrall 应当成功");
        KuvaMasterEntity lich = nearestDemo(h, p, KuvaMasterEntity.class);
        KuvaSlaveEntity thrall = nearestDemo(h, p, KuvaSlaveEntity.class);
        h.assertTrue(lich != null && thrall != null, "面前应当各有一只演示目标");
        KuvaSlaveEntity normal = spawnThrall(h, thrall.position());
        h.assertTrue(!lich.shouldBeSaved(), "演示玄骸不应当存盘");
        h.assertTrue(!thrall.shouldBeSaved(), "演示奴仆不应当存盘");
        h.assertTrue(normal.shouldBeSaved(), "同一位置的普通奴仆应当存盘");
        lich.discard();
        thrall.discard();
        normal.discard();
        h.succeed();
    }

    @AfterBatch(batch = MORE)
    public static void afterMore(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 管理命令每个子命令走一遍 ====================

    @GameTest(template = "empty", batch = ISO_COMMANDS, timeoutTicks = 200)
    public static void commandsSmoke(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        p.setGameMode(GameType.CREATIVE);
        h.assertTrue(run(p, "kuvalich mercy next lich_c") == 1, "next lich_c");
        h.assertTrue(run(p, "kuvalich mercy next random") == 1, "next random");
        h.assertTrue(run(p, "kuvalich mercy next nope") == 0, "认不出的套路应当失败");
        h.assertTrue(run(p, "kuvalich mercy spawn thrall thrall_2") == 1, "spawn thrall thrall_2");
        KuvaSlaveEntity thrall = nearestDemo(h, p, KuvaSlaveEntity.class);
        h.assertTrue(thrall != null && thrall.getMercyState() == MercyState.DOWNED, "演示奴仆应当倒地");
        aimAt(p, thrall);
        h.assertTrue(run(p, "kuvalich mercy spawn thrall lich_a") == 0, "奴仆不能用玄骸的套路");
        h.assertTrue(run(p, "kuvalich mercy timer 5") == 1, "timer 5");
        h.assertTrue(thrall.getMercyRemainingServer() == 100, "剩余应当是 100 tick，实际 " + thrall.getMercyRemainingServer());
        h.assertTrue(run(p, "kuvalich mercy freeze on") == 1, "freeze on");
        for (String s : new String[]{"adapt", "adapt 6", "full", "guard", "guard 30", "strike", "weak"}) {
            h.assertTrue(run(p, "kuvalich mercy state " + s) == 1, "state " + s);
        }
        h.assertTrue(run(p, "kuvalich mercy state fury 4") == 1, "state fury 4");
        h.assertTrue(thrall.getFuryPermille() == 3000, "fury 4 应当把增幅临时设成 3000‰，实际 " + thrall.getFuryPermille());
        h.assertTrue(run(p, "kuvalich mercy cards vome 1 - 2 0 3 return") == 1, "cards 带次数和归还");
        h.assertTrue(run(p, "kuvalich mercy cards vome nope ris") == 0, "认不出的卡应当失败");
        h.assertTrue(run(p, "kuvalich mercy cards clear") == 1, "cards clear");
        h.assertTrue(run(p, "kuvalich mercy outcome fail2") == 1, "outcome fail2");
        h.assertTrue(run(p, "kuvalich mercy outcome real") == 1, "outcome real");
        h.assertTrue(run(p, "kuvalich mercy exec success") == 0, "奴仆的处决没有结果可选");
        h.runAfterDelay(10, () -> {
            h.assertTrue(thrall.getMercyRemainingServer() == 100, "冻结时倒计时不走，实际 " + thrall.getMercyRemainingServer());
            h.assertTrue(run(p, "kuvalich mercy freeze off") == 1, "freeze off");
            h.assertTrue(run(p, "kuvalich mercy exec") == 1, "exec");
            h.assertTrue(thrall.getMercyState() == MercyState.EXECUTING, "应当在处决中");
            h.assertTrue(MercyPlan.decode(thrall.getMercyPlan()).variant() == MercyVariant.THRALL_2,
                    "spawn 给的套路应当用上：" + MercyPlan.decode(thrall.getMercyPlan()).variant());
            h.assertTrue(run(p, "kuvalich mercy stop") == 1, "stop");
            h.assertTrue(thrall.getMercyState() == MercyState.DOWNED, "stop 后应当回到倒地");
            h.assertTrue(run(p, "kuvalich mercy stop") == 0, "没在处决时 stop 应当失败");
            h.assertTrue(run(p, "kuvalich mercy reset") == 1, "reset");
            h.assertTrue(thrall.getMercyState() == MercyState.NONE
                    && Math.abs(thrall.getHealth() - thrall.getMaxHealth()) < 1.0E-3F, "reset 后满血、没有状态");
            thrall.setNoAi(true);
            h.assertTrue(run(p, "kuvalich mercy down") == 1, "down（准星指着的）");
            h.assertTrue(thrall.getMercyState() == MercyState.DOWNED, "down 后应当倒地");
            h.assertTrue(run(p, "kuvalich mercy down") == 0, "已经倒地的再 down 应当失败");
            h.assertTrue(run(p, "kuvalich mercy reset") == 1, "再 reset");
            KuvaSlaveEntity plain = spawnThrall(h, p.position().add(-2.0D, 0.0D, 0.0D));
            h.assertTrue(run(p, "kuvalich mercy down @e[type=kuvalich:kuva_slave,distance=..6]") == 1, "down 选择器");
            h.assertTrue(plain.getMercyState() == MercyState.DOWNED && p.getUUID().equals(plain.getMercyQualified()),
                    "选择器打倒的奴仆：资格人是下命令的人");
            h.assertTrue(run(p, "kuvalich mercy spawn lich lich_b Vor Kalla") == 1, "spawn lich lich_b 带名字");
            KuvaMasterEntity lich = nearestDemo(h, p, KuvaMasterEntity.class);
            h.assertTrue(lich != null && "Vor Kalla".equals(lich.getLichName()) && lich.getLichRank() == 1,
                    "演示玄骸的名字和等级：" + (lich == null ? "没有" : lich.getLichName() + " / " + lich.getLichRank()));
            aimAt(p, lich);
            h.assertTrue(run(p, "kuvalich mercy phase 1") == 1 && lich.getLichPhase() == 1 && lich.mercyStabs() == 2,
                    "phase 1：演示玄骸下次处决应当两刀，实际 " + lich.mercyStabs());
            h.assertTrue(run(p, "kuvalich mercy shield 50") == 0, "演示玄骸没有护盾，shield 应当失败");
            h.assertTrue(run(p, "kuvalich mercy reset") == 1, "收尾 reset");
            MercyServer.setNextVariant(p, null);
            MercyServer.setNextOutcome(p, null);
            for (AbstractKuva k : h.getLevel().getEntitiesOfClass(AbstractKuva.class, p.getBoundingBox().inflate(12.0D))) {
                k.discard();
            }
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_COMMANDS)
    public static void afterCommands(ServerLevel level) {
        MercyServer.setFrozen(level, false);
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 工具 ====================

    /** 卡上会被处决写到的几项 */
    private record Snapshot(int level, int attempts, int progress, int stage, boolean ready, boolean hasLich, int[] damage) {

        static Snapshot of(RequiemCard card) {
            ItemStack[] cards = {card.getOneCard(), card.getTwoCard(), card.getThreeCard()};
            int[] damage = new int[3];
            for (int i = 0; i < 3; i++) {
                damage[i] = cards[i] == null || cards[i].isEmpty() ? -1 : cards[i].getDamageValue();
            }
            return new Snapshot(card.getKuvaLevel(), card.getAttempts().length, card.getDecryptionProgress(),
                    card.getUnlockedCardStatus(), card.isReadyCard(), card.hasLich(), damage);
        }

        void assertSame(GameTestHelper h, RequiemCard card, String when) {
            Snapshot now = of(card);
            h.assertTrue(level == now.level && attempts == now.attempts && progress == now.progress && stage == now.stage
                            && ready == now.ready && hasLich == now.hasLich && java.util.Arrays.equals(damage, now.damage),
                    when + "：卡上的数据不该变，" + this + " → " + now);
        }
    }

    private static void listenDeaths() {
        if (listening) {
            return;
        }
        listening = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingDeathEvent.class, e -> {
            if (!e.isCanceled()) {
                DIED.add(e.getEntity().getUUID());
            }
        });
    }

    private static RequiemCard card(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD)
                .orElseThrow(() -> new IllegalStateException("测试玩家没有安魂卡能力"));
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

    /**
     * 摆三张卡，让前缀长度正好是 {@code prefix}：答案定为 0 / 1 / 2，前 prefix 张放对的，第 prefix + 1 张放错的，
     * 之后的放对的。卡都是新的（耐久满）
     */
    private static void setCards(RequiemCard card, int prefix) {
        card.setOneAnswer(0);
        card.setTwoAnswer(1);
        card.setThreeAnswer(2);
        int[] answers = {0, 1, 2};
        ItemStack[] stacks = new ItemStack[3];
        for (int i = 0; i < 3; i++) {
            int id = i == prefix ? 7 : answers[i];
            stacks[i] = new ItemStack(KuvaLichItems.getRequiemCard(id));
        }
        card.setOneCard(stacks[0]);
        card.setTwoCard(stacks[1]);
        card.setThreeCard(stacks[2]);
        card.setDecryptionProgress(0);
    }

    /** 以玩家的身份跑一条命令（权限 2），返回命令的结果；语法错返回 -1 */
    private static int run(ServerPlayer p, String command) {
        CommandSourceStack src = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        try {
            return p.server.getCommands().getDispatcher().execute(command, src);
        } catch (CommandSyntaxException e) {
            return -1;
        }
    }

    /** 玩家附近最近的一只演示目标 */
    @Nullable
    private static <T extends AbstractKuva> T nearestDemo(GameTestHelper h, ServerPlayer p, Class<T> type) {
        T best = null;
        double bestD = Double.MAX_VALUE;
        for (T e : h.getLevel().getEntitiesOfClass(type, p.getBoundingBox().inflate(8.0D), e -> e.isAlive() && e.isDemo())) {
            double d = e.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** 让玩家看着这只实体的中心（命令里「准星指着的」就是它） */
    private static void aimAt(ServerPlayer p, AbstractKuva e) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, e.getBoundingBox().getCenter());
    }

    /** 把玩家放到目标旁边 {@code dist} 格（从目标指向结构中心的方向再转 {@code turn} 度）、看着它 */
    private static void standBy(ServerPlayer p, AbstractKuva target, double dist, float turn) {
        Vec3 t = target.position();
        Vec3 toward = p.position().subtract(t);
        float yaw = (float) Math.atan2(toward.z, toward.x) + turn * Mth.DEG_TO_RAD;
        double x = t.x + Math.cos(yaw) * dist;
        double z = t.z + Math.sin(yaw) * dist;
        float face = (float) (Mth.atan2(t.z - z, t.x - x) * Mth.RAD_TO_DEG) - 90.0F;
        p.teleportTo((ServerLevel) p.level(), x, t.y, z, face, 20.0F);
    }

    /** 立即召到面前并返回那只：摆到最后一段（下次处决三刀）、打掉护盾且不再恢复 */
    private static KuvaMasterEntity summonFront(GameTestHelper h, ServerPlayer p) {
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 3, c.getZ() + 11);
        h.assertTrue(LichDirector.summonNow(p, SummonMode.FRONT), "立即召唤应当成功");
        KuvaMasterEntity lich = LichDirector.getLich(p).orElseThrow(() -> new IllegalStateException("召唤后应当有在场的玄骸"));
        lich.setLichPhase(KuvaMasterEntity.SEGMENTS - 1);
        lich.breakShield(1_000_000);
        return lich;
    }

    /** 倒地断言失败时的现场：状态、死活、血量、位置和脚下、身上的方块 */
    private static String downedDetail(GameTestHelper h, AbstractKuva t) {
        BlockPos at = t.blockPosition();
        return t.getMercyState() + "，活着 " + t.isAlive() + "，移除 " + t.getRemovalReason() + "，血 " + t.getHealth()
                + "，难度 " + h.getLevel().getDifficulty() + "，倒地开关 " + ModConfig.KUVA_LICH.mercyEnabled.get()
                + "/" + ModConfig.KUVA_LICH.mercyThralls.get() + "，在 " + at.toShortString() + " 脚下 "
                + h.getLevel().getBlockState(at.below()).getBlock().getDescriptionId() + " 身上 "
                + h.getLevel().getBlockState(at).getBlock().getDescriptionId();
    }

    /**
     * 处决定案后清 / 数掉落的范围：玄骸和处决者两边都罩进去，再往外放 6 格，大致就是本测试自己那块封闭场地。
     * 原来按玄骸外扩 12 格，处决者那头可能没清到，又会把场地外的东西也算进来
     */
    private static AABB settleArea(KuvaMasterEntity lich, ServerPlayer p) {
        return new AABB(lich.blockPosition()).minmax(new AABB(p.blockPosition())).inflate(6.0D);
    }

    /** 掉落 / 存活断言失败时的现场：死活、移除原因、怜悯之击状态、血量、位置 */
    private static String aliveDetail(AbstractKuva t) {
        return "活着 " + t.isAlive() + "，移除 " + t.getRemovalReason() + "，状态 " + t.getMercyState()
                + "，血 " + t.getHealth() + "，在 " + t.blockPosition().toShortString();
    }

    /** 不会走动的普通奴仆 */
    private static KuvaSlaveEntity spawnThrall(GameTestHelper h, Vec3 at) {
        KuvaSlaveEntity thrall = KuvaLichEntities.KUVA_SLAVE.get().create(h.getLevel());
        h.assertTrue(thrall != null, "应当能造出奴仆");
        thrall.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        thrall.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(thrall), "奴仆应当加得进世界");
        return thrall;
    }

    private static KuvaSlaveEntity spawnDemoThrall(GameTestHelper h, Vec3 at) {
        KuvaSlaveEntity thrall = KuvaLichEntities.KUVA_SLAVE.get().create(h.getLevel());
        h.assertTrue(thrall != null, "应当能造出奴仆");
        thrall.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        thrall.setNoAi(true);
        thrall.markDemo();
        h.assertTrue(h.getLevel().addFreshEntity(thrall), "演示奴仆应当加得进世界");
        return thrall;
    }

    private static KuvaMasterEntity spawnDemoLich(GameTestHelper h, Vec3 at) {
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(lich != null, "应当能造出玄骸");
        lich.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        lich.setNoAi(true);
        lich.markDemo();
        h.assertTrue(h.getLevel().addFreshEntity(lich), "演示玄骸应当加得进世界");
        lich.setDemoIdentity("Grax Vokk", 1);
        return lich;
    }

    /** 像刷怪蛋那样放一只无主玄骸 */
    private static KuvaMasterEntity spawnWildLich(GameTestHelper h, Vec3 at) {
        ServerLevel level = h.getLevel();
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(level);
        h.assertTrue(lich != null, "应当能造出玄骸");
        lich.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        lich.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.SPAWN_EGG, null, null);
        h.assertTrue(level.addFreshEntity(lich), "刷怪蛋来源的玄骸应当加得进世界");
        return lich;
    }

    /**
     * 区域里本模组的掉落物有几个。只数本模组的：挖空地时测试结构的启动按钮会掉下来；
     * 各 batch 的场地摆在同一批位置上，开测前先用 {@link #clearItems} 清掉上一批留下的
     */
    private static int itemsIn(GameTestHelper h, AABB area) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, area, MercyTests::isOurDrop).size();
    }

    private static boolean isOurDrop(ItemEntity e) {
        if (e.isRemoved()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getItem().getItem());
        return id != null && Reference.MOD_ID.equals(id.getNamespace());
    }

    private static void clearItems(GameTestHelper h, AABB area) {
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> !e.isRemoved())) {
            e.discard();
        }
    }

    private static String itemNames(GameTestHelper h, AABB area) {
        StringBuilder sb = new StringBuilder();
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area, MercyTests::isOurDrop)) {
            sb.append(' ').append(e.getItem().getDescriptionId()).append('×').append(e.getItem().getCount())
                    .append('@').append(e.blockPosition().toShortString());
        }
        return sb.toString();
    }

    /**
     * 挖一块封闭的场地（见 {@link KuvaTestKit#openArena(GameTestHelper, int, int, int, int, int)}）：
     * 地板铺在地面那层（y = 0），往上 4 格空着。地面是实的，测试玩家（服务端不给玩家算重力）和摆出来的实体
     * 就站在同一层上，准星正好平着打中它们
     */
    private static void openArena(GameTestHelper h, int x0, int x1, int z0, int z1) {
        KuvaTestKit.openArena(h, x0, x1, z0, z1, 0);
    }

    /** 以结构中心为心挖一块 13 × 13 的空地 */
    private static void clearAround(GameTestHelper h) {
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 6, c.getZ() + 6);
    }

    private static void loadTestChunks(GameTestHelper h) {
        ChunkPos origin = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        for (int dx = -1; dx < 4; dx++) {
            for (int dz = -1; dz < 4; dz++) {
                h.getLevel().getChunk(origin.x + dx, origin.z + dz);
            }
        }
    }

    private static void rememberDifficulty(MinecraftServer server) {
        if (savedDifficulty == null) {
            savedDifficulty = server.getWorldData().getDifficulty();
        }
    }

    private static void restoreDifficulty(MinecraftServer server) {
        if (savedDifficulty != null) {
            server.setDifficulty(savedDifficulty, true);
            savedDifficulty = null;
        }
    }
}
