package pers.roinflam.kuvalich.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributeInstance;
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributeManager;
import pers.roinflam.kuvalich.dynamicattribute.DynamicAttributes;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.SummonMode;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 分段血条和玄骸护盾：一下只能打空一段、倒地期间这一段回血、没人处决就站起来、第 k 次处决只验前 k 张卡、
 * 前 k 张全对且 k 不到 3 时挣开站起进下一段、第三次全对才终结、中途判错照常失败；护盾先吃伤害、打掉后隔一会儿恢复、
 * 有护盾时磁力伤害翻倍。
 *
 * <p>时间约定同 {@link MercyCoreTests}：从开始处决的那个测试回调起算，第 k 个测试 tick 时处决正好走了 k 步。
 * 第 1 / 2 / 3 格在 tick 17 / 34 / 53 填满；挣开站起在最后那一格 + 14 推开处决者、进起身，+ 46 回到能打的状态、解锁。
 * 每条都会放出会动的玄骸，各自单独一个 batch。</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MercySegmentTests {

    private static final String ISO_CUT = "kl_seg_iso_segmentCut";
    private static final String ISO_REFILL = "kl_seg_iso_downedRefillRises";
    private static final String ISO_ADV1 = "kl_seg_iso_firstExecutionRises";
    private static final String ISO_ADV2 = "kl_seg_iso_secondExecutionRises";
    private static final String ISO_FULL = "kl_seg_iso_threeExecutionsVanquish";
    private static final String ISO_WRONG2 = "kl_seg_iso_secondStabWrongFails";
    private static final String ISO_SHIELD = "kl_seg_iso_shieldAbsorbs";
    private static final String ISO_REGEN = "kl_seg_iso_shieldRegens";
    private static final String ISO_MAGNETIC = "kl_seg_iso_magneticOnShield";

    /** 测试期间护盾不恢复 */
    private static final int SHIELD_HOLD = 1_000_000;
    private static final float EPS = 1.0E-3F;

    private MercySegmentTests() {
    }

    // ==================== 一下只能打空一段 ====================

    @GameTest(template = "empty", batch = ISO_CUT, timeoutTicks = 120)
    public static void segmentCut(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        bornByProgress(p, card(p));
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        // 显形期间不挨打，等它显形完
        h.runAfterDelay(KuvaMasterEntity.APPEAR_GRACE_TICKS + 8, () -> {
            h.assertTrue(lich.isSegmented() && lich.getLichPhase() == 0 && lich.mercyStabs() == 1,
                    "刚召出来：分段、一段没空、下次处决一刀：分段 " + lich.isSegmented() + "，段 " + lich.getLichPhase()
                            + "，刀数 " + lich.mercyStabs());
            lich.breakShield(SHIELD_HOLD);
            float max = lich.getMaxHealth();
            float floor = lich.segmentFloor();
            h.assertTrue(Math.abs(floor - max * 2.0F / 3.0F) < EPS && Math.abs(lich.segmentCeil() - max) < EPS,
                    "第一段是最上面那三分之一：底 " + floor + "，顶 " + lich.segmentCeil() + "，最大 " + max);
            lich.setHealth(floor + 5.0F);
            h.assertTrue(lich.hurt(p.damageSources().playerAttack(p), max), "这一下应当打得进去");
            h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "打空这一段应当倒地：" + lich.getMercyState());
            h.assertTrue(Math.abs(lich.getHealth() - floor) < EPS, "一下打穿也只掉到这一段的底：" + lich.getHealth()
                    + " / " + floor);
            h.assertTrue(lich.getLichPhase() == 0 && lich.mercyStabs() == 1, "倒地不改段数");
            lich.invulnerableTime = 0;
            h.assertTrue(!lich.hurt(p.damageSources().playerAttack(p), 50.0F), "倒地后打不进去");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_CUT)
    public static void afterCut(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 倒地期间这一段回血，到时没人处决就站起来 ====================

    @GameTest(template = "empty", batch = ISO_REFILL, timeoutTicks = 160)
    public static void downedRefillRises(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            int level = card.getKuvaLevel();
            int attempts = card.getAttempts().length;
            lich.breakShield(SHIELD_HOLD);
            lich.forceDowned(p);
            float floor = lich.segmentFloor();
            float ceil = lich.segmentCeil();
            h.assertTrue(lich.getMercyState() == MercyState.DOWNED && Math.abs(lich.getHealth() - floor) < EPS,
                    "强制倒地停在这一段的底：" + lich.getHealth() + " / " + floor);
            h.assertTrue(MercyServer.setTimer(lich, 2), "改倒地剩余时间应当成功");
            h.runAfterDelay(20, () -> {
                float hp = lich.getHealth();
                h.assertTrue(lich.getMercyState() == MercyState.DOWNED && hp > floor + EPS && hp <= ceil + EPS,
                        "倒地期间这一段的血从底往回涨：" + hp + "（底 " + floor + "，顶 " + ceil + "）");
            });
            h.runAfterDelay(37, () -> h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "tick 37 还在倒地"));
            h.runAfterDelay(43, () -> {
                h.assertTrue(lich.getMercyState() == MercyState.RISING, "tick 40 前后应当站起来：" + lich.getMercyState());
                h.assertTrue(Math.abs(lich.getHealth() - ceil) < EPS, "站起来时这一段回满：" + lich.getHealth());
                h.assertTrue(lich.getUnclaimedDowns() == 1 && lich.getLichPhase() == 0,
                        "记一次没人处决、段数不变：" + lich.getUnclaimedDowns() + " / " + lich.getLichPhase());
                lich.invulnerableTime = 0;
                h.assertTrue(!lich.hurt(p.damageSources().playerAttack(p), 50.0F), "起身中打不进去");
            });
            h.runAfterDelay(43 + MercyServer.RISE_TICKS + 2, () -> {
                h.assertTrue(lich.getMercyState() == MercyState.NONE, "起身演完回到能打的状态：" + lich.getMercyState());
                lich.heal(lich.getMaxHealth());
                h.assertTrue(lich.getHealth() <= ceil + EPS, "回血越不过这一段的顶：" + lich.getHealth() + " / " + ceil);
                h.assertTrue(card.getKuvaLevel() == level && card.getAttempts().length == attempts,
                        "没人处决：等级、尝试记录都不变");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_REFILL)
    public static void afterRefill(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 第一次处决：只验第一张，对了就挣开站起 ====================

    @GameTest(template = "empty", batch = ISO_ADV1, timeoutTicks = 160)
    public static void firstExecutionRises(GameTestHelper h) {
        advanceRound(h, 0);
    }

    @AfterBatch(batch = ISO_ADV1)
    public static void afterAdv1(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 第二次处决：验前两张，全对也挣开站起 ====================

    @GameTest(template = "empty", batch = ISO_ADV2, timeoutTicks = 180)
    public static void secondExecutionRises(GameTestHelper h) {
        advanceRound(h, 1);
    }

    @AfterBatch(batch = ISO_ADV2)
    public static void afterAdv2(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    /**
     * 打空过 {@code phase} 段的玄骸，三张卡全对：这次只验前 phase + 1 张，定案时记一行（前几格对、后面没判到），
     * 不扣卡、不升级；最后那一格 + 14 推开处决者 1 格、进下一段（血在新一段的顶、护盾回满），+ 46 回到能打的状态、解锁
     */
    private static void advanceRound(GameTestHelper h, int phase) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        int k = phase + 1;
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            int level = card.getKuvaLevel();
            int progress = card.getDecryptionProgress();
            lich.setLichPhase(phase);
            lich.breakShield(SHIELD_HOLD);
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
            h.assertTrue(plan.stabs() == k && plan.prefix() == k && plan.advance(),
                    "第 " + k + " 次处决只验前 " + k + " 张：刀数 " + plan.stabs() + "，前缀 " + plan.prefix());
            MercyBeats.Lich b = MercyBeats.lich(plan.variant());
            int g = b.fillTick(k);
            h.runAfterDelay(b.settleTick, () -> {
                h.assertTrue(card.getKuvaLevel() == level && card.hasLich() && card.isReadyCard(),
                        "挣开站起：不升级、这一轮还在、卡不扣");
                int row = card.getAttempts()[0];
                for (int i = 0; i < 3; i++) {
                    int want = i < k ? RequiemCard.ATTEMPT_RIGHT : RequiemCard.ATTEMPT_UNJUDGED;
                    h.assertTrue(RequiemCard.attemptResult(row, i) == want,
                            "尝试记录第 " + (i + 1) + " 位应当是 " + want + "，实际 " + RequiemCard.attemptResult(row, i));
                }
            });
            double[] before = new double[1];
            h.runAfterDelay(g + b.advanceRiseTicks - 1, () -> {
                h.assertTrue(lich.getMercyState() == MercyState.EXECUTING && lich.getLichPhase() == phase,
                        "挣开之前还在处决中、段数不变：" + lich.getMercyState() + " / " + lich.getLichPhase());
                before[0] = horizontal(p.position(), lich.position());
            });
            h.runAfterDelay(g + b.advanceRiseTicks + 1, () -> {
                h.assertTrue(lich.getMercyState() == MercyState.RISING, "挣开：应当进起身，实际 " + lich.getMercyState());
                h.assertTrue(lich.getLichPhase() == phase + 1 && lich.mercyStabs() == k + 1,
                        "进下一段：段 " + lich.getLichPhase() + "，下次刀数 " + lich.mercyStabs());
                float ceil = lich.getMaxHealth() * (KuvaMasterEntity.SEGMENTS - phase - 1) / KuvaMasterEntity.SEGMENTS;
                h.assertTrue(Math.abs(lich.getHealth() - ceil) < EPS && Math.abs(lich.segmentCeil() - ceil) < EPS,
                        "血在新一段的顶：" + lich.getHealth() + " / " + ceil);
                h.assertTrue(Math.abs(lich.getShield() - lich.shieldCap()) < EPS && lich.shieldCap() > 0.0F,
                        "护盾回满：" + lich.getShield() + " / " + lich.shieldCap());
            });
            h.runAfterDelay(g + b.advanceStandTicks + 1, () -> {
                h.assertTrue(lich.getMercyState() == MercyState.NONE && lich.isAlive(),
                        "起身演完回到能打的状态：" + lich.getMercyState());
                double pushed = horizontal(p.position(), lich.position()) - before[0];
                h.assertTrue(pushed > 0.7D && pushed < 1.3D, "处决者应当被推开约 1 格，实际 " + pushed);
            });
            h.runAfterDelay(g + b.advanceStandTicks + MercyServer.GRACE_TICKS + 2, () -> {
                h.assertTrue(!MercyServer.isBusy(p), "处决者应当已经解锁、过了无敌余量");
                h.assertTrue(card.getDecryptionProgress() == progress && card.getKuvaLevel() == level,
                        "挣开站起不给进度、不升级");
                h.succeed();
            });
        });
    }

    // ==================== 三次处决连着全对：第三次才终结 ====================

    @GameTest(template = "empty", batch = ISO_FULL, timeoutTicks = 300)
    public static void threeExecutionsVanquish(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            lich.breakShield(SHIELD_HOLD);
            Runnable third = () -> {
                h.assertTrue(lich.getLichPhase() == 2, "两次挣开站起后应当在最后一段：" + lich.getLichPhase());
                lich.forceDowned(p);
                h.assertTrue(MercyServer.executeNow(p, lich) == null, "第三次应当能开始处决");
                MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
                h.assertTrue(plan.stabs() == 3 && plan.prefix() == 3 && plan.success(),
                        "第三次三刀全对：刀数 " + plan.stabs() + "，前缀 " + plan.prefix());
                h.runAfterDelay(MercyBeats.lich(plan.variant()).settleTick + 1, () -> {
                    h.assertTrue(card.isReadyCard() && card.getOneCard().getDamageValue() == 1 && !card.hasLich(),
                            "第三次全对：卡各扣一次留在扉上、这一轮结束");
                    RequiemCard.HistoryEntry last = card.getLastHistory();
                    h.assertTrue(last != null && last.fate() == RequiemCard.FATE_VANQUISHED, "历史里应当记成被终结");
                    h.succeed();
                });
            };
            executeAndRise(h, p, lich, 1, () -> executeAndRise(h, p, lich, 2, third));
        });
    }

    /** 打倒、处决，前 k 张全对：等它站起来回到能打的状态，再接下一步 */
    private static void executeAndRise(GameTestHelper h, ServerPlayer p, KuvaMasterEntity lich, int k, Runnable next) {
        lich.forceDowned(p);
        h.assertTrue(MercyServer.executeNow(p, lich) == null, "第 " + k + " 次应当能开始处决");
        MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
        h.assertTrue(plan.stabs() == k && plan.advance(), "第 " + k + " 次：刀数 " + plan.stabs() + "，前缀 " + plan.prefix());
        MercyBeats.Lich b = MercyBeats.lich(plan.variant());
        h.runAfterDelay(b.fillTick(k) + b.advanceStandTicks + 2, () -> {
            h.assertTrue(lich.getMercyState() == MercyState.NONE && lich.getLichPhase() == k,
                    "第 " + k + " 次挣开站起后：状态 " + lich.getMercyState() + "，段 " + lich.getLichPhase());
            next.run();
        });
    }

    @AfterBatch(batch = ISO_FULL)
    public static void afterFull(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 第二次处决第二张错：照常失败 ====================

    @GameTest(template = "empty", batch = ISO_WRONG2, timeoutTicks = 160)
    public static void secondStabWrongFails(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 1);
            int level = card.getKuvaLevel();
            lich.setLichPhase(1);
            lich.breakShield(SHIELD_HOLD);
            lich.forceDowned(p);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
            h.assertTrue(plan.stabs() == 2 && plan.prefix() == 1 && plan.failure(),
                    "第二次处决、第二张错：刀数 " + plan.stabs() + "，前缀 " + plan.prefix());
            MercyBeats.Lich b = MercyBeats.lich(plan.variant());
            h.runAfterDelay(b.settleTick, () -> {
                h.assertTrue(card.getKuvaLevel() == level + 1, "判错：玄骸升一级");
                int row = card.getAttempts()[0];
                h.assertTrue(RequiemCard.attemptResult(row, 0) == RequiemCard.ATTEMPT_RIGHT
                                && RequiemCard.attemptResult(row, 1) == RequiemCard.ATTEMPT_WRONG
                                && RequiemCard.attemptResult(row, 2) == RequiemCard.ATTEMPT_UNJUDGED,
                        "记录：第一位对、第二位错、第三位没判到");
            });
            int g = b.fillTick(2);
            h.runAfterDelay(g + b.failLeaveTicks - 1, () -> h.assertTrue(lich.getMercyState() == MercyState.EXECUTING,
                    "离场之前还在处决中：" + lich.getMercyState()));
            h.runAfterDelay(g + b.failLeaveTicks + 1, () -> {
                h.assertTrue(lich.getMercyState() == MercyState.LEAVING || lich.isRemoved(),
                        "判错后照常挣脱逃走：" + lich.getMercyState());
                h.assertTrue(card.hasLich(), "逃走不结束这一轮");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_WRONG2)
    public static void afterWrong2(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 护盾先吃伤害 ====================

    @GameTest(template = "empty", batch = ISO_SHIELD, timeoutTicks = 100)
    public static void shieldAbsorbs(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        bornByProgress(p, card(p));
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(KuvaMasterEntity.APPEAR_GRACE_TICKS + 8, () -> {
            float cap = lich.shieldCap();
            h.assertTrue(cap > 0.0F && Math.abs(lich.getShield() - cap) < EPS && Math.abs(lich.getShieldMax() - cap) < EPS,
                    "召出来护盾是满的：" + lich.getShield() + " / " + cap);
            float hp = lich.getHealth();
            h.assertTrue(lich.hurt(p.damageSources().playerAttack(p), 10.0F), "这一下应当打得进去");
            h.assertTrue(Math.abs(lich.getHealth() - hp) < EPS, "护盾没打穿时血量不动：" + hp + " → " + lich.getHealth());
            h.assertTrue(lich.getShield() < cap - EPS, "护盾应当被扣掉一截：" + lich.getShield() + " / " + cap);
            h.assertTrue(lich.getShieldDelayTicks() == ModConfig.KUVA_LICH.lichShieldDelaySeconds.get() * 20,
                    "挨打后护盾重新等：" + lich.getShieldDelayTicks());
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_SHIELD)
    public static void afterShield(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 护盾打掉后隔一会儿恢复 ====================

    @GameTest(template = "empty", batch = ISO_REGEN, timeoutTicks = 100)
    public static void shieldRegens(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        bornByProgress(p, card(p));
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            lich.breakShield(10);
            h.assertTrue(lich.getShield() <= 0.0F && lich.getShieldDelayTicks() == 10, "打掉护盾、等 10 tick");
            h.runAfterDelay(6, () -> h.assertTrue(lich.getShield() <= 0.0F, "等的这段时间不恢复：" + lich.getShield()));
            // 等完以后每秒（tickCount 整 20）回一截
            h.runAfterDelay(10 + 20 + 3, () -> {
                float cap = lich.shieldCap();
                h.assertTrue(lich.getShield() > 0.0F && lich.getShield() <= cap + EPS,
                        "等完应当开始恢复：" + lich.getShield() + " / " + cap);
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_REGEN)
    public static void afterRegen(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 有护盾时磁力伤害翻倍 ====================

    @GameTest(template = "empty", batch = ISO_MAGNETIC, timeoutTicks = 60)
    public static void magneticOnShield(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        bornByProgress(p, card(p));
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            h.assertTrue(lich.getShield() > 0.0F, "召出来有护盾");
            DynamicAttributeManager.apply(lich, new DynamicAttributeInstance(DynamicAttributes.MAGNETIC, 200, 0));
            LivingHurtEvent onShield = new LivingHurtEvent(lich, lich.damageSources().generic(), 10.0F);
            DynamicAttributes.ElementCombatHandler.onLivingHurt(onShield);
            h.assertTrue(Math.abs(onShield.getAmount() - 20.0F) < EPS, "有护盾时磁力 0 级伤害翻倍：" + onShield.getAmount());
            lich.breakShield(SHIELD_HOLD);
            LivingHurtEvent bare = new LivingHurtEvent(lich, lich.damageSources().generic(), 10.0F);
            DynamicAttributes.ElementCombatHandler.onLivingHurt(bare);
            h.assertTrue(Math.abs(bare.getAmount() - 10.0F) < EPS, "护盾打掉以后磁力不再加伤：" + bare.getAmount());
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_MAGNETIC)
    public static void afterMagnetic(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 工具 ====================

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
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

    /** 摆三张新卡，前缀长度正好是 {@code prefix}（答案 0 / 1 / 2，第 prefix + 1 张放错的） */
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

    /** 立即召到面前并返回那只（刚召出来：第一段、满护盾） */
    private static KuvaMasterEntity summonFront(GameTestHelper h, ServerPlayer p) {
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        KuvaTestKit.openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 3, c.getZ() + 11);
        h.assertTrue(LichDirector.summonNow(p, SummonMode.FRONT), "立即召唤应当成功");
        return LichDirector.getLich(p).orElseThrow(() -> new IllegalStateException("召唤后应当有在场的玄骸"));
    }

    /** 测试结构周围的区块同步载入 */
    private static void loadTestChunks(GameTestHelper h) {
        ChunkPos origin = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        for (int dx = -1; dx < 4; dx++) {
            for (int dz = -1; dz < 4; dz++) {
                h.getLevel().getChunk(origin.x + dx, origin.z + dz);
            }
        }
    }
}
