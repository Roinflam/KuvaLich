package pers.roinflam.kuvalich.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.card.OULL;
import pers.roinflam.kuvalich.mercy.KuvaStateKind;
import pers.roinflam.kuvalich.mercy.KuvaStateServer;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.nemesis.LichTraits;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.SummonMode;
import pers.roinflam.kuvalich.module.KillStackManager;
import pers.roinflam.kuvalich.module.KillStackManager.StackType;
import pers.roinflam.kuvalich.module.weapon.WeaponCombatHandler;
import pers.roinflam.kuvalich.utils.LivingEntityUtil;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemGateMenu;
import pers.roinflam.kuvalich.world.inventory.RequiemMenuLogoutGuard;
import pers.roinflam.kuvalich.world.inventory.RequiemWeaponTableMenu;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 怜悯之击服务端核心：倒地不死、倒地无敌、按前缀定案、失败和成功的时间轴、空格算判错、Oull、
 * 奴仆处决、自然消失的截停、碰撞箱跟着同步数据变
 *
 * <p>处决时间轴在每个服务端 tick 末尾推进一步，从开始处决的那个测试回调起算，第 k 个测试 tick 时处决正好走了 k 步，
 * 下面的「tick 17」「tick 151」都是这个意思。会放出玄骸的、会动的奴仆、改配置或难度的，各自单独一个 batch。</p>
 *
 * <p>这里召出来的玄骸一律先摆到最后一段（打空过两段，下次处决捅三刀、倒地血量 1）、打掉护盾，测的是三刀处决本身；
 * 分段、护盾、第一 / 二次处决见 {@link MercySegmentTests}。</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MercyCoreTests {

    private static final String CORE = "kl_mercy_core";
    private static final String ISO_DOWNED = "kl_mercy_iso_lichDownedNotDead";
    private static final String ISO_INVULN = "kl_mercy_iso_downedInvulnerable";
    private static final String ISO_PREFIX = "kl_mercy_iso_prefixJudgement";
    private static final String ISO_FAIL = "kl_mercy_iso_failTimeline";
    private static final String ISO_SUCCESS = "kl_mercy_iso_successRewardTiming";
    private static final String ISO_EMPTY = "kl_mercy_iso_emptyCardsFailFirst";
    private static final String ISO_THIRD_EMPTY = "kl_mercy_iso_thirdEmptyFailsThird";
    private static final String ISO_OULL_SUCCESS = "kl_mercy_iso_oullDefeatsLich";
    private static final String CFG_OULL_DROP = "kl_cfg_oullDropOnFailure";
    private static final String CFG_OULL_PREFIX0 = "kl_cfg_oullNoDropWithoutHit";
    private static final String CFG_OULL_ZERO = "kl_cfg_oullNoDropAtZero";
    private static final String ISO_THRALL = "kl_cfg_thrallExecution";
    private static final String ISO_THRALL_BIRTH = "kl_mercy_iso_thrallRevealBirth";
    private static final String CFG_AFFINITY = "kl_cfg_affinityHitFlags";
    private static final String ISO_NATURAL = "kl_mercy_iso_naturalLeaveDissolves";
    private static final String ISO_DIRECT = "kl_mercy_iso_downedSurvivesDirectDamage";
    private static final String ISO_EXEC_STACKS = "kl_mercy_iso_executeStacksOnRealDeath";
    private static final String ISO_MODDED = "kl_mercy_iso_moddedKillsCannotBypassDowned";

    /** 倒地后碰撞箱高度：玄骸 3.4 × 0.70、奴仆 2.9 × 0.65 */
    private static final float LICH_DOWNED_HEIGHT = 2.38F;
    private static final float THRALL_DOWNED_HEIGHT = 1.885F;

    /** 收到过死亡事件的实体（只增不减，按 UUID 查） */
    private static final Set<UUID> DIED = Collections.synchronizedSet(new HashSet<>());
    private static boolean listening;

    /** 改难度的 batch 收尾时改回去 */
    private static Difficulty savedDifficulty;

    private MercyCoreTests() {
    }

    // ==================== #1 有主玄骸挨了致命一击：倒地、不死 ====================

    @GameTest(template = "empty", batch = ISO_DOWNED, timeoutTicks = 200)
    public static void lichDownedNotDead(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        bornByProgress(p, card(p));
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        // 显形期间不挨打，等它显形完
        h.runAfterDelay(KuvaMasterEntity.APPEAR_GRACE_TICKS + 8, () -> {
            lich.setHealth(1.0F);
            h.assertTrue(lich.hurt(p.damageSources().playerAttack(p), 20.0F), "致命一击应当打得进去");
            h.assertTrue(lich.isAlive() && !lich.isDeadOrDying() && !lich.isRemoved(), "应当还活着");
            h.assertTrue(Math.abs(lich.getHealth() - 1.0F) < 1.0E-4F, "血量应当压回 1，实际 " + lich.getHealth());
            h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "状态应当是倒地，实际 " + lich.getMercyState());
            assertHeight(h, lich, LICH_DOWNED_HEIGHT);
            h.assertTrue(!DIED.contains(lich.getUUID()), "倒地不应当触发死亡事件");
            h.assertTrue(LichDirector.stateOf(p) == LichState.DOWNED, "导演的状态应当是倒地：" + LichDirector.stateOf(p));
            h.assertTrue(lich.getMercyActorId() == p.getId(), "资格人应当是主人");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_DOWNED)
    public static void afterDowned(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #2 倒地后刀砍、点火、爆炸都没用 ====================

    @GameTest(template = "empty", batch = ISO_INVULN, timeoutTicks = 200)
    public static void downedInvulnerable(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        bornByProgress(p, card(p));
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        lich.forceDowned(p);
        h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "强制倒地应当生效");
        Vec3 at = lich.position();
        h.assertTrue(!lich.hurt(p.damageSources().playerAttack(p), 50.0F), "倒地后剑砍应当打不进去");
        lich.setSecondsOnFire(5);
        h.getLevel().explode(null, lich.getX() + 1.0D, lich.getY() + 0.5D, lich.getZ(), 4.0F, Level.ExplosionInteraction.NONE);
        h.runAfterDelay(40, () -> {
            h.assertTrue(lich.isAlive(), "应当还活着");
            float expect = downedRefill(lich);
            h.assertTrue(Math.abs(lich.getHealth() - expect) < 0.01F, "血量应当只跟着倒地计时往回涨（" + expect
                    + "），实际 " + lich.getHealth());
            h.assertTrue(lich.position().distanceTo(at) < 0.05D, "不应当被推走：" + at + " → " + lich.position());
            h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "应当还在倒地");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_INVULN)
    public static void afterInvulnerable(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #7 前缀判定：tick 16 不写，tick 17 定案 ====================

    @GameTest(template = "empty", batch = ISO_PREFIX, timeoutTicks = 360)
    public static void prefixJudgement(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        // 同一只玄骸依次跑 p = 0 / 1 / 2，每次看完定案就复位重来；p = 3 放最后（成功会结束这一轮）
        prefixRound(h, p, card, lich, 0, 2, () ->
                prefixRound(h, p, card, lich, 1, 2, () ->
                        prefixRound(h, p, card, lich, 2, 2, () ->
                                prefixRound(h, p, card, lich, 3, 2, h::succeed))));
    }

    /** 跑一次指定前缀的处决，核对计划位、tick 16 / 17 的数据，然后复位接着下一轮 */
    private static void prefixRound(GameTestHelper h, ServerPlayer p, RequiemCard card, KuvaMasterEntity lich,
                                    int prefix, long delay, Runnable next) {
        h.runAfterDelay(delay, () -> {
            setCards(card, prefix);
            h.assertTrue(card.correctPrefixLength() == prefix, "前缀应当是 " + prefix + "，实际 " + card.correctPrefixLength());
            int level = card.getKuvaLevel();
            int attempts = card.getAttempts().length;
            int weaponIndex = card.getLichWeapon();
            int weaponLevel = card.getLichWeaponLevel();
            String weaponType = card.getLichWeaponType();
            lich.forceDowned(p);
            // 下面按正面直刺的定案 tick（17）查；折翼第一刀晚，要到 19
            MercyServer.setNextVariant(p, MercyVariant.LICH_A);
            String err = MercyServer.executeNow(p, lich);
            h.assertTrue(err == null, "应当能开始处决：" + err);
            MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
            h.assertTrue(plan.stabs() == 3 && plan.prefix() == prefix, "计划位不对：刀数 " + plan.stabs() + "，前缀 " + plan.prefix());
            h.runAfterDelay(16, () -> {
                h.assertTrue(MercyServer.ageOf(lich) == 16, "处决应当走了 16 步，实际 " + MercyServer.ageOf(lich));
                h.assertTrue(card.getKuvaLevel() == level && card.getAttempts().length == attempts && card.isReadyCard(),
                        "p = " + prefix + "：tick 16 时还不该写任何数据");
            });
            h.runAfterDelay(17, () -> {
                if (prefix < 3) {
                    h.assertTrue(card.getKuvaLevel() == level + 1, "p = " + prefix + "：定案后玄骸应当升一级");
                    h.assertTrue(card.getAttempts().length == Math.min(attempts + 1, RequiemCard.MAX_ATTEMPTS),
                            "p = " + prefix + "：应当记一行尝试");
                    MercyServer.resetAround(h.getLevel(), lich.position(), 4.0D);
                    h.assertTrue(lich.getMercyState() == MercyState.NONE, "复位后应当回到正常");
                } else {
                    h.assertTrue(card.isReadyCard() && card.getOneCard().getDamageValue() == 1
                                    && card.getTwoCard().getDamageValue() == 1 && card.getThreeCard().getDamageValue() == 1,
                            "成功：三张卡应当各扣一次、留在扉上");
                    h.assertTrue(card.getKuvaLevel() == 0 && !card.hasLich(), "成功：这一轮应当重置过");
                    RequiemCard.HistoryEntry last = card.getLastHistory();
                    h.assertTrue(last != null && last.fate() == RequiemCard.FATE_VANQUISHED, "成功：历史里应当记成被终结");
                    h.assertTrue(survivingCards(p) == 0, "成功：卡留在扉上，背包里不该多出卡，实际 " + survivingCards(p));
                    h.assertTrue(weaponLevel > 0 && !weaponType.isEmpty(), "诞生时应当定下武器的等级和元素");
                    // 奖励要等处决演完（发奖那一拍）才落定：给的那把应当就是诞生时定下的武器、等级、元素
                    MercyBeats.Lich b = MercyBeats.lich(plan.variant());
                    h.runAfterDelay(b.successRewardTick - b.settleTick + 5, () -> {
                        ItemStack got = ItemStack.EMPTY;
                        List<ItemStack> owned = new ArrayList<>(p.getInventory().items);
                        owned.addAll(PlayerLedger.getInbox(p));
                        for (ItemStack s : owned) {
                            if (KuvaLichItems.KUVA_WEAPONS.contains(s.getItem())) {
                                got = s;
                            }
                        }
                        h.assertTrue(!got.isEmpty() && got.getItem() == KuvaLichItems.KUVA_WEAPONS.get(weaponIndex),
                                "成功：应当拿到它手里那把武器：" + owned);
                        h.assertTrue(KuvaWeaponUtil.getNumber(got) == weaponLevel
                                        && weaponType.equals(KuvaWeaponUtil.getType(got)),
                                "成功：掉的武器应当是诞生时定下的 " + weaponLevel + " " + weaponType + "，实际 "
                                        + KuvaWeaponUtil.getNumber(got) + " " + KuvaWeaponUtil.getType(got));
                        next.run();
                    });
                    return;
                }
                next.run();
            });
        });
    }

    @AfterBatch(batch = ISO_PREFIX)
    public static void afterPrefix(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #8 第二刀判错的时间轴 ====================

    @GameTest(template = "empty", batch = ISO_FAIL, timeoutTicks = 260)
    public static void failTimeline(GameTestHelper h) {
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
            // 各套路第二格的时刻不一样，随机到哪套就差几个 tick，钉住一套、时刻从表里取
            MercyServer.setNextVariant(p, MercyVariant.LICH_A);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_A);
            int g = b.fillTick(2);
            int leave = g + b.failLeaveTicks, remove = g + b.failRemoveTicks, award = g + b.failProgressTicks;
            AABB area = new AABB(lich.blockPosition()).inflate(12.0D);
            clearItems(h, area);
            h.runAfterDelay(leave - 1, () -> h.assertTrue(lich.getMercyState() != MercyState.LEAVING,
                    "tick " + (leave - 1) + " 还不该离场"));
            h.runAfterDelay(leave, () -> h.assertTrue(lich.getMercyState() == MercyState.LEAVING,
                    "tick " + leave + " 应当进入离场，实际 " + lich.getMercyState()));
            h.runAfterDelay(remove - 1, () -> h.assertTrue(!lich.isRemoved(), "tick " + (remove - 1) + " 还不该移除"));
            h.runAfterDelay(remove, () -> h.assertTrue(lich.isRemoved(), "tick " + remove + " 应当已经移除"));
            h.runAfterDelay(award - 1, () -> h.assertTrue(card.getDecryptionProgress() == progress
                    && card.getUnlockedCardStatus() == stage, "tick " + (award - 1) + " 还不该给进度"));
            h.runAfterDelay(award, () -> {
                h.assertTrue(card.getDecryptionProgress() != progress || card.getUnlockedCardStatus() != stage,
                        "tick " + award + " 应当给了解密进度");
                h.assertTrue(itemsIn(h, area) == 0, "失败全程不应当有掉落物：" + itemNames(h, area));
                h.assertTrue(!DIED.contains(lich.getUUID()), "失败不应当触发死亡事件");
                h.assertTrue(!MercyServer.isBusy(p), "处决者应当已经解锁、过了无敌余量");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_FAIL)
    public static void afterFail(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #9 成功：死亡和奖励的时刻 ====================

    @GameTest(template = "empty", batch = ISO_SUCCESS, timeoutTicks = 300)
    public static void successRewardTiming(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        // 各套第三刀时刻不同，死亡 / 发奖的 tick 按套路从节拍表取；钉在正面直刺上
        MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_A);
        int death = b.successDeathTick, reward = b.successRewardTick;
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            lich.forceDowned(p);
            MercyServer.setNextVariant(p, MercyVariant.LICH_A);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            AABB area = new AABB(lich.blockPosition()).inflate(12.0D);
            clearItems(h, area);
            h.runAfterDelay(death - 1, () -> {
                h.assertTrue(lich.isAlive(), "tick " + (death - 1) + " 还活着");
                h.assertTrue(itemsIn(h, area) == 0, "死之前还不该有掉落物：" + itemNames(h, area));
                h.assertTrue(reliquaries(p) == 0 && PlayerLedger.hasFinale(p), "死之前奖励还在账上、没发");
            });
            h.runAfterDelay(death, () -> {
                h.assertTrue(!lich.isAlive() && DIED.contains(lich.getUUID()), "tick " + death + " 应当死了（走原版死亡流程）");
                h.assertTrue(MercyServer.isBusy(p), "tick " + death + " 处决者还锁着");
            });
            h.runAfterDelay(reward, () -> {
                h.assertTrue(reliquaries(p) == 1 && !PlayerLedger.hasFinale(p), "tick " + reward + " 奖励应当落定、进了背包");
                h.assertTrue(itemsIn(h, area) == 0, "奖励不该掉在地上：" + itemNames(h, area));
            });
            h.runAfterDelay(death + 29, () -> h.assertTrue(!lich.isRemoved(), "死后 32 tick 内实体还在（留给消散演出）"));
            h.runAfterDelay(death + 34, () -> {
                h.assertTrue(lich.isRemoved(), "死后 32 tick 应当已经移除");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_SUCCESS)
    public static void afterSuccess(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #12 卡没放齐：照三刀判，空格算判错 ====================

    @GameTest(template = "empty", batch = ISO_EMPTY, timeoutTicks = 200)
    public static void emptyCardsFailFirst(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            card.setOneCard(ItemStack.EMPTY);
            card.setTwoCard(ItemStack.EMPTY);
            card.setThreeCard(ItemStack.EMPTY);
            int level = card.getKuvaLevel();
            int attempts = card.getAttempts().length;
            lich.forceDowned(p);
            MercyServer.setNextVariant(p, MercyVariant.LICH_A);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
            h.assertTrue(plan.stabs() == 3 && plan.prefix() == 0, "没装卡也照三刀判、第一刀错：刀数 " + plan.stabs()
                    + "，前缀 " + plan.prefix());
            h.runAfterDelay(17, () -> {
                h.assertTrue(card.getKuvaLevel() == level + 1, "定案后玄骸应当升一级");
                h.assertTrue(card.getAttempts().length == Math.min(attempts + 1, RequiemCard.MAX_ATTEMPTS), "应当记一行尝试");
                int row = card.getAttempts()[0];
                h.assertTrue(RequiemCard.attemptCardId(row, 0) == RequiemCard.ATTEMPT_NO_CARD
                                && RequiemCard.attemptResult(row, 0) == RequiemCard.ATTEMPT_WRONG
                                && RequiemCard.attemptResult(row, 1) == RequiemCard.ATTEMPT_UNJUDGED,
                        "记录：第一位没放卡、判错，后面没判到");
            });
            // 第一格 tick 17 判错：+48 离场、+58 移除（逃走）
            h.runAfterDelay(76, () -> {
                h.assertTrue(lich.isRemoved() && !DIED.contains(lich.getUUID()), "玄骸应当逃走了，不是死了");
                h.assertTrue(card.hasLich() && card.getKuvaLevel() == level + 1, "这一轮还在，等级 +1");
            });
            // 解锁在 +55，之后还有 10 tick 无敌余量
            h.runAfterDelay(84, () -> {
                h.assertTrue(!MercyServer.isBusy(p), "处决者应当已经解锁、过了无敌余量");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_EMPTY)
    public static void afterEmpty(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = ISO_THIRD_EMPTY, timeoutTicks = 200)
    public static void thirdEmptyFailsThird(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            card.setThreeCard(ItemStack.EMPTY);
            h.assertTrue(card.correctPrefixLength() == 2, "两对一空：前缀应当是 2，实际 " + card.correctPrefixLength());
            int level = card.getKuvaLevel();
            lich.forceDowned(p);
            MercyServer.setNextVariant(p, MercyVariant.LICH_A);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
            h.assertTrue(plan.stabs() == 3 && plan.prefix() == 2 && plan.cardIds()[2] == -1,
                    "计划位：三刀、第三刀判错、第三格空：刀数 " + plan.stabs() + "，前缀 " + plan.prefix());
            h.runAfterDelay(17, () -> {
                h.assertTrue(card.getKuvaLevel() == level + 1, "定案后玄骸应当升一级");
                int row = card.getAttempts()[0];
                h.assertTrue(RequiemCard.attemptResult(row, 0) == RequiemCard.ATTEMPT_RIGHT
                                && RequiemCard.attemptResult(row, 1) == RequiemCard.ATTEMPT_RIGHT
                                && RequiemCard.attemptResult(row, 2) == RequiemCard.ATTEMPT_WRONG
                                && RequiemCard.attemptCardId(row, 2) == RequiemCard.ATTEMPT_NO_CARD,
                        "记录：前两位对、第三位没放卡判错");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_THIRD_EMPTY)
    public static void afterThirdEmpty(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== Oull：放在哪一格都算对 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void oullFitsAnySlot(GameTestHelper h) {
        RequiemCard card = new RequiemCard();
        card.setUnlockedCardStatus(1);
        card.setOneAnswer(0);
        card.setTwoAnswer(1);
        card.setThreeAnswer(2);
        int[] answers = {0, 1, 2};
        for (int at = 0; at < 3; at++) {
            ItemStack[] stacks = new ItemStack[3];
            for (int i = 0; i < 3; i++) {
                stacks[i] = new ItemStack(KuvaLichItems.getRequiemCard(i == at ? OULL.ID : answers[i]));
            }
            card.setOneCard(stacks[0]);
            card.setTwoCard(stacks[1]);
            card.setThreeCard(stacks[2]);
            h.assertTrue(card.correctPrefixLength() == 3 && card.isCorrectAnswer(), "Oull 在第 " + (at + 1) + " 格应当算对");
            h.assertTrue(card.isFirstCorrectAnswer() && card.isTwoCorrectAnswer(), "Oull 在第 " + (at + 1) + " 格：单格判定也算对");
        }
        // Oull 只顶它自己那一格：第一格 Oull、第二格放错，前缀停在 1
        card.setOneCard(new ItemStack(KuvaLichItems.OULL_CARD.get()));
        card.setTwoCard(new ItemStack(KuvaLichItems.getRequiemCard(7)));
        card.setThreeCard(new ItemStack(KuvaLichItems.getRequiemCard(2)));
        h.assertTrue(card.correctPrefixLength() == 1 && !card.isCorrectAnswer(), "Oull + 错卡：前缀应当是 1");
        h.assertTrue(KuvaLichItems.getRequiemCard(OULL.ID) == KuvaLichItems.OULL_CARD.get(), "id 8 应当取到 Oull");
        h.assertTrue(!RequiemCard.fits(ItemStack.EMPTY, 0), "空格不算对");
        int packed = RequiemCard.packAttempt(new int[]{OULL.ID, 1, -1}, new byte[]{1, 2, 0}, true);
        h.assertTrue(RequiemCard.attemptCardId(packed, 0) == OULL.ID && RequiemCard.attemptCardId(packed, 2) == RequiemCard.ATTEMPT_NO_CARD,
                "处决记录应当能记下 Oull");
        h.succeed();
    }

    // ==================== Oull：同时只能装一张 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void gateRejectsSecondOull(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        card.setOneCard(ItemStack.EMPTY);
        card.setTwoCard(ItemStack.EMPTY);
        card.setThreeCard(ItemStack.EMPTY);
        p.getInventory().clearContent();
        RequiemGateMenu menu = new RequiemGateMenu(1, p.getInventory(), h.getLevel(), p.blockPosition());
        ItemStack oull = new ItemStack(KuvaLichItems.OULL_CARD.get());
        h.assertTrue(menu.getSlot(0).mayPlace(oull), "第一张 Oull 应当放得进");
        menu.getSlot(0).set(oull.copy());
        h.assertTrue(!menu.getSlot(1).mayPlace(oull) && !menu.getSlot(2).mayPlace(oull), "第二张 Oull 应当放不进");
        h.assertTrue(menu.getSlot(1).mayPlace(new ItemStack(KuvaLichItems.getRequiemCard(1))), "普通卡照常放得进");
        h.assertTrue(menu.getSlot(0).mayPlace(oull), "同一格换一张 Oull 不算第二张");
        // Shift 快速移动：快捷栏第一格（菜单里的第 3 格）放一张 Oull，移不进卡槽
        p.getInventory().setItem(0, oull.copy());
        menu.quickMoveStack(p, 3);
        h.assertTrue(OULL.is(p.getInventory().getItem(0)), "Shift 移动：第二张 Oull 应当留在背包里");
        h.assertTrue(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(), "Shift 移动：卡槽里不该多出 Oull");
        // 普通卡照常能 Shift 进去
        p.getInventory().setItem(1, new ItemStack(KuvaLichItems.getRequiemCard(2)));
        menu.quickMoveStack(p, 4);
        h.assertTrue(p.getInventory().getItem(1).isEmpty() && !menu.getSlot(1).getItem().isEmpty(), "Shift 移动：普通卡应当进卡槽");
        menu.removed(p);
        h.assertTrue(OULL.is(card.getOneCard()) && !OULL.is(card.getTwoCard()) && !OULL.is(card.getThreeCard()),
                "关界面后存下的只有一张 Oull");
        h.succeed();
    }

    // ==================== 灭骸之扉：放进 / 拿走当下就存，不等关界面 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void gateSavesCardsWhileOpen(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        card.setOneCard(ItemStack.EMPTY);
        card.setTwoCard(ItemStack.EMPTY);
        card.setThreeCard(ItemStack.EMPTY);
        p.getInventory().clearContent();
        RequiemGateMenu menu = new RequiemGateMenu(1, p.getInventory(), h.getLevel(), p.blockPosition());
        ItemStack first = new ItemStack(KuvaLichItems.getRequiemCard(1));
        menu.getSlot(0).set(first.copy());
        h.assertTrue(ItemStack.matches(card.getOneCard(), first), "放进卡槽当下就该写进玩家数据");
        ItemStack taken = menu.getSlot(0).remove(1);
        h.assertTrue(!taken.isEmpty() && card.getOneCard().isEmpty(), "拿走当下玩家数据里也该清掉");
        // 重开界面：卡槽里放的是副本，拿出来的那张不会还挂在玩家数据上
        card.setTwoCard(first.copy());
        RequiemGateMenu again = new RequiemGateMenu(2, p.getInventory(), h.getLevel(), p.blockPosition());
        h.assertTrue(again.getSlot(1).getItem() != card.getTwoCard(), "卡槽和玩家数据不该共用同一个物品对象");
        again.getSlot(1).remove(1);
        h.assertTrue(card.getTwoCard().isEmpty(), "重开后拿走的卡也该从玩家数据里清掉");
        h.succeed();
    }

    // ==================== 开着安魂界面下线：存档前先关界面 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void logoutClosesRequiemMenuBeforeSave(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        p.getInventory().clearContent();
        RequiemWeaponTableMenu menu = new RequiemWeaponTableMenu(1, p.getInventory(), h.getLevel(), p.blockPosition());
        menu.weaponHandler.setStackInSlot(0, new ItemStack(KuvaLichItems.KUVA_SHILDEG.get()));
        menu.setCarried(new ItemStack(KuvaLichItems.getRequiemCard(3)));
        p.containerMenu = menu;
        RequiemMenuLogoutGuard.closeRequiemMenuBeforeSave(new PlayerEvent.PlayerLoggedOutEvent(p));
        h.assertTrue(p.containerMenu == p.inventoryMenu, "下线时应当先关掉安魂界面");
        h.assertTrue(p.getInventory().countItem(KuvaLichItems.KUVA_SHILDEG.get()) == 1, "军械库里的武器应当退回背包");
        h.assertTrue(p.getInventory().countItem(KuvaLichItems.getRequiemCard(3)) == 1, "光标上的卡应当放回背包，不是扔在地上");
        h.assertTrue(!PlayerLedger.hasInbox(p), "背包有空位时不进待领取");
        h.succeed();
    }

    @GameTest(template = "empty", batch = CORE)
    public static void logoutFullInventoryMenuSlotsToInbox(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        fillWithDirt(p);
        RequiemWeaponTableMenu menu = new RequiemWeaponTableMenu(1, p.getInventory(), h.getLevel(), p.blockPosition());
        menu.weaponHandler.setStackInSlot(0, new ItemStack(KuvaLichItems.KUVA_SHILDEG.get()));
        menu.setCarried(new ItemStack(KuvaLichItems.getRequiemCard(3)));
        p.containerMenu = menu;
        RequiemMenuLogoutGuard.beforeRemove(p);

        List<ItemEntity> dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4.0D),
                e -> !e.getItem().is(Items.DIRT));
        dropped.forEach(ItemEntity::discard);
        h.assertTrue(dropped.isEmpty(), "背包满时掉线，槽位和光标上的都不扔地上：" + dropped.size());
        h.assertTrue(p.containerMenu == p.inventoryMenu && menu.getCarried().isEmpty()
                && menu.weaponHandler.getStackInSlot(0).isEmpty(), "界面关了，光标和槽位都空");
        h.assertTrue(inboxCount(p, KuvaLichItems.KUVA_SHILDEG.get()) == 1 && inboxCount(p, KuvaLichItems.getRequiemCard(3)) == 1
                && PlayerLedger.inboxCount(p) == 2, "武器和卡各一件进待领取：" + PlayerLedger.getInbox(p));
        h.assertTrue(p.getInventory().countItem(Items.DIRT) == 37 * 64, "背包原样");

        ServerPlayer again = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), p.getGameProfile());
        again.load(p.saveWithoutId(new CompoundTag()));
        h.assertTrue(PlayerLedger.inboxCount(again) == 2, "存盘读回后待领取里还在");

        // 平常关界面不受影响：背包满照旧扔在脚下，不进待领取
        ServerPlayer q = KuvaTestKit.player(h);
        fillWithDirt(q);
        RequiemWeaponTableMenu qm = new RequiemWeaponTableMenu(2, q.getInventory(), h.getLevel(), q.blockPosition());
        qm.weaponHandler.setStackInSlot(0, new ItemStack(KuvaLichItems.KUVA_SHILDEG.get()));
        q.containerMenu = qm;
        q.doCloseContainer();
        List<ItemEntity> qDropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, q.getBoundingBox().inflate(4.0D),
                e -> e.getItem().is(KuvaLichItems.KUVA_SHILDEG.get()));
        qDropped.forEach(ItemEntity::discard);
        h.assertTrue(qDropped.size() == 1 && !PlayerLedger.hasInbox(q), "平常关界面照旧扔脚下：" + qDropped.size());
        h.succeed();
    }

    // 当场存盘要读盘上的文件才测得到，内存里序列化一遍再读回不算
    @GameTest(template = "empty", batch = CORE)
    public static void logoutInboxReachesDisk(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer p = KuvaTestKit.player(h);
        fillWithDirt(p);
        RequiemWeaponTableMenu menu = new RequiemWeaponTableMenu(1, p.getInventory(), h.getLevel(), p.blockPosition());
        menu.weaponHandler.setStackInSlot(0, new ItemStack(KuvaLichItems.KUVA_SHILDEG.get()));
        p.containerMenu = menu;
        RequiemMenuLogoutGuard.beforeRemove(p);
        h.assertTrue(inboxOnDisk(server, p) == 1, "进了待领取应当当场存盘：" + inboxOnDisk(server, p));

        // 走一遍真的移除流程
        ServerPlayer q = KuvaTestKit.player(h);
        fillWithDirt(q);
        RequiemWeaponTableMenu qm = new RequiemWeaponTableMenu(2, q.getInventory(), h.getLevel(), q.blockPosition());
        qm.weaponHandler.setStackInSlot(0, new ItemStack(KuvaLichItems.KUVA_SHILDEG.get()));
        qm.setCarried(new ItemStack(KuvaLichItems.getRequiemCard(3)));
        q.containerMenu = qm;
        AABB around = q.getBoundingBox().inflate(4.0D);
        server.getPlayerList().remove(q);
        List<ItemEntity> dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> !e.getItem().is(Items.DIRT));
        dropped.forEach(ItemEntity::discard);
        h.assertTrue(dropped.isEmpty(), "真下线时槽位和光标上的也不扔地上：" + dropped.size());
        h.assertTrue(PlayerLedger.inboxCount(q) == 2 && inboxOnDisk(server, q) == 2,
                "武器和卡都在待领取里，盘上也是：" + PlayerLedger.inboxCount(q) + " / " + inboxOnDisk(server, q));
        h.succeed();
    }

    private static int inboxOnDisk(MinecraftServer server, ServerPlayer p) {
        ServerPlayer again = new ServerPlayer(server, p.serverLevel(), p.getGameProfile());
        server.getPlayerList().load(again);
        return PlayerLedger.inboxCount(again);
    }

    private static void fillWithDirt(ServerPlayer p) {
        for (int i = 0; i < 36; i++) {
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        p.getInventory().offhand.set(0, new ItemStack(Items.DIRT, 64));
    }

    private static int inboxCount(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : PlayerLedger.getInbox(p)) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ==================== Oull：和两张对的卡一起击败玄骸，扣一次 ====================

    @GameTest(template = "empty", batch = ISO_OULL_SUCCESS, timeoutTicks = 200)
    public static void oullDefeatsLich(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            card.setTwoCard(new ItemStack(KuvaLichItems.OULL_CARD.get()));
            p.getInventory().clearContent();
            lich.forceDowned(p);
            MercyServer.setNextVariant(p, MercyVariant.LICH_A);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            MercyPlan.Plan plan = MercyPlan.decode(lich.getMercyPlan());
            h.assertTrue(plan.prefix() == 3 && plan.cardIds()[1] == OULL.ID, "计划位：成功、第二格是 Oull");
            h.runAfterDelay(17, () -> {
                h.assertTrue(!card.hasLich(), "成功：这一轮结束");
                ItemStack kept = card.getTwoCard();
                h.assertTrue(OULL.is(kept) && kept.getDamageValue() == 1, "Oull 应当扣一次后留在扉上第 2 格，耐久损耗 "
                        + (OULL.is(kept) ? kept.getDamageValue() : "（不在）"));
                h.assertTrue(survivingCards(p) == 0, "背包里不该多出卡");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_OULL_SUCCESS)
    public static void afterOullSuccess(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== Oull：处决失败、猜对过才按几率掉 ====================

    /** 概率配 100、第二刀判错（前缀 1）：收尾时给一张 */
    @GameTest(template = "empty", batch = CFG_OULL_DROP, timeoutTicks = 200)
    public static void oullDropOnFailure(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.oullDropChance, 100);
        oullDropRound(h, 1, true);
    }

    @AfterBatch(batch = CFG_OULL_DROP)
    public static void afterOullDrop(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    /** 概率配 100、第一刀就错（前缀 0）：不给 */
    @GameTest(template = "empty", batch = CFG_OULL_PREFIX0, timeoutTicks = 200)
    public static void oullNoDropWithoutHit(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.oullDropChance, 100);
        oullDropRound(h, 0, false);
    }

    @AfterBatch(batch = CFG_OULL_PREFIX0)
    public static void afterOullPrefix0(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    /** 概率配 0、第三刀判错（前缀 2）：不给 */
    @GameTest(template = "empty", batch = CFG_OULL_ZERO, timeoutTicks = 260)
    public static void oullNoDropAtZero(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.oullDropChance, 0);
        oullDropRound(h, 2, false);
    }

    @AfterBatch(batch = CFG_OULL_ZERO)
    public static void afterOullZero(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    /** 处决到判错、等收尾走完，看背包里有没有多一张 Oull（地上也不该有） */
    private static void oullDropRound(GameTestHelper h, int prefix, boolean expect) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, prefix);
            p.getInventory().clearContent();
            lich.forceDowned(p);
            MercyServer.setNextVariant(p, MercyVariant.LICH_A);
            h.assertTrue(MercyServer.executeNow(p, lich) == null, "应当能开始处决");
            AABB area = new AABB(lich.blockPosition()).inflate(12.0D);
            clearItems(h, area);
            // 判错那一格填满之后 failChatTicks 给；各套填格时刻不同，钉在正面直刺上按节拍表取
            MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_A);
            int give = b.fillTick(prefix + 1) + b.failChatTicks;
            h.runAfterDelay(give - 1, () -> h.assertTrue(oullCount(p) == 0, "收尾之前还不该给"));
            h.runAfterDelay(give + 1, () -> {
                int got = oullCount(p);
                h.assertTrue(got == (expect ? 1 : 0), "前缀 " + prefix + "：应当" + (expect ? "给一张" : "不给") + " Oull，实际 " + got);
                h.assertTrue(itemsIn(h, area) == 0, "不该掉在地上：" + itemNames(h, area));
                h.succeed();
            });
        });
    }

    private static int oullCount(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (OULL.is(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    // ==================== #13 处决奴仆 ====================

    @GameTest(template = "empty", batch = ISO_THRALL, timeoutTicks = 120)
    public static void thrallExecution(GameTestHelper h) {
        // 奴仆的掉落是按概率的，测试里把赤毒的掉率调成 100%，保证有东西掉
        KuvaTestKit.override(ModConfig.KUVA_LICH.slaveKuvaDropChance, 100);
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        card.setDecryptionProgress(0);
        float rage = card.getLichRage();
        int stage = card.getUnlockedCardStatus();
        KuvaSlaveEntity thrall = spawnThrall(h, p.position().add(0.0D, 0.0D, 2.0D));
        thrall.setHealth(1.0F);
        h.assertTrue(thrall.hurt(p.damageSources().playerAttack(p), 20.0F), "致命一击应当打得进去");
        h.assertTrue(thrall.getMercyState() == MercyState.DOWNED && thrall.isAlive(), "奴仆应当倒地");
        assertHeight(h, thrall, THRALL_DOWNED_HEIGHT);
        h.assertTrue(thrall.getMercyQualified() != null && thrall.getMercyQualified().equals(p.getUUID()),
                "资格人应当是打倒它的人");
        h.assertTrue(MercyServer.executeNow(p, thrall) == null, "应当能开始处决");
        AABB area = new AABB(thrall.blockPosition()).inflate(12.0D);
        clearItems(h, area);
        h.runAfterDelay(29, () -> {
            h.assertTrue(thrall.isAlive(), "tick 29 还活着");
            h.assertTrue(itemsIn(h, area) == 0, "死之前不该有掉落物：" + itemNames(h, area));
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(!thrall.isAlive(), "tick 30 应当死了");
            int got = card.getDecryptionProgress();
            h.assertTrue(card.getUnlockedCardStatus() == stage, "这一下不该跨阶段（测试前提）");
            int min = ModConfig.KUVA_LICH.minDecryptionProgress.get();
            int max = ModConfig.KUVA_LICH.maxDecryptionProgress.get();
            h.assertTrue(got >= min && got <= max, "进度应当在 [" + min + ", " + max + "]，实际 " + got);
            h.assertTrue(itemsIn(h, area) > 0, "应当有掉落物");
            // 多出来的应当是一次处决的量加上这次进度按点数折算的量；跟踪期间怒气本身也在慢慢涨，
            // 所以上限再放出这 30 tick 的一点自然增长
            double expect = ModConfig.KUVA_LICH.lichRagePerThrallExecution.get()
                    + got * ModConfig.KUVA_LICH.lichRagePerProgressPoint.get();
            double delta = card.getLichRage() - rage;
            h.assertTrue(delta >= expect - 1.0E-4D && delta < expect + 0.005D,
                    "怒气应当多了一次处决加 " + got + " 点进度的量 " + expect + "（" + rage + " → " + card.getLichRage() + "）");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_THRALL)
    public static void afterThrall(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 倒地后被直接写血量的路线打不死，之后照常处决、判定 ====================

    @GameTest(template = "empty", batch = ISO_DIRECT, timeoutTicks = 260)
    public static void downedSurvivesDirectDamage(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            setCards(card, 3);
            lich.forceDowned(p);
            h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "强制倒地应当生效");
            int stacks = KillStackManager.getStacks(p, StackType.BASE_DAMAGE);
            // 处决词条判定成立：1 tick 后强杀
            executeEffect(p, lich);
            // 流血、毒素、武器自带斩杀都是这条：直接把血扣光再调 die
            LivingEntityUtil.kill(lich, p.damageSources().playerAttack(p));
            assertStillDowned(h, lich, "直接强杀之后");
            h.runAfterDelay(2, () -> {
                assertStillDowned(h, lich, "处决词条强杀之后");
                // 真实子弹：命中后隔 1 tick 按真伤扣血，够致死就强杀
                trueDamageLater(lich, p, 50.0F);
                assertStillDowned(h, lich, "延迟真伤之后");
                // 不走 die、直接把血量写成 0
                lich.setHealth(0.0F);
            });
            h.runAfterDelay(5, () -> {
                assertStillDowned(h, lich, "血量直接写成 0 之后");
                h.assertTrue(!DIED.contains(lich.getUUID()), "倒地期间不该有死亡事件");
                h.assertTrue(card.hasLich() && LichDirector.stateOf(p) == LichState.DOWNED,
                        "遭遇不该被这些伤害结束：" + LichDirector.stateOf(p));
                h.assertTrue(KillStackManager.getStacks(p, StackType.BASE_DAMAGE) == stacks,
                        "处决词条没打死倒地的玄骸，不该加击杀叠层：" + stacks + " → "
                                + KillStackManager.getStacks(p, StackType.BASE_DAMAGE));
                AABB area = new AABB(lich.blockPosition()).minmax(new AABB(p.blockPosition())).inflate(6.0D);
                clearItems(h, area);
                // 死亡 / 发奖的 tick 按套路从节拍表取，钉在正面直刺上（随机到别的套路时写死的 tick 对不上）
                MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_A);
                MercyServer.setNextVariant(p, MercyVariant.LICH_A);
                h.assertTrue(MercyServer.executeNow(p, lich) == null, "之后应当照常能开始处决");
                h.runAfterDelay(20, () -> h.assertTrue(!card.hasLich(), "tick 17 应当已经定案、这一轮结束"));
                h.runAfterDelay(b.successDeathTick + 1, () -> h.assertTrue(!lich.isAlive() && DIED.contains(lich.getUUID()),
                        "tick " + (b.successDeathTick + 1) + " 应当处决致死（走原版死亡流程）"));
                h.runAfterDelay(b.successRewardTick + 2, () -> {
                    h.assertTrue(reliquaries(p) == 1, "tick " + (b.successRewardTick + 2) + " 应当收到安魂成功的奖励");
                    h.succeed();
                });
            });
        });
    }

    @AfterBatch(batch = ISO_DIRECT)
    public static void afterDirect(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 别的模组的真伤、斩杀：倒地期间打不死，没倒地的先倒地；/kill 命令、真掉出世界照常 ====================

    @GameTest(template = "empty", batch = ISO_MODDED, timeoutTicks = 120)
    public static void moddedKillsCannotBypassDowned(GameTestHelper h) {
        listenDeaths();
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(2, () -> {
            lich.forceDowned(p);
            h.assertTrue(lich.getMercyState() == MercyState.DOWNED, "强制倒地应当生效");
            KuvaTestKit.assaultLikeOtherMods(lich, p);
            // 没倒地的奴仆挨借 genericKill 的真伤、挨模组自己调的 kill()：先倒地，不直接死
            KuvaSlaveEntity a = spawnThrall(h, p.position().add(2.0D, 0.0D, 2.0D));
            a.hurt(a.damageSources().genericKill(), Float.MAX_VALUE);
            KuvaSlaveEntity b = spawnThrall(h, p.position().add(-2.0D, 0.0D, 2.0D));
            b.kill();
            h.assertTrue(a.isAlive() && a.getMercyState() == MercyState.DOWNED,
                    "借 genericKill 的真伤应当只打成倒地：" + a.getMercyState() + "，活着 " + a.isAlive());
            h.assertTrue(b.isAlive() && b.getMercyState() == MercyState.DOWNED,
                    "模组自己调 kill() 应当只打成倒地：" + b.getMercyState() + "，活着 " + b.isAlive());
            KuvaTestKit.assaultLikeOtherMods(a, p);
            // 隔 2 tick：最后一种把血量写成 0 的，要等死亡计时补回来
            h.runAfterDelay(2, () -> {
                assertStillDowned(h, lich, "别的模组的几种强杀之后（玄骸）");
                assertStillDowned(h, a, "别的模组的几种强杀之后（奴仆）");
                h.assertTrue(!DIED.contains(lich.getUUID()) && !DIED.contains(a.getUUID()) && !DIED.contains(b.getUUID()),
                        "倒地期间不该有死亡事件");
                h.assertTrue(card.hasLich() && LichDirector.stateOf(p) == LichState.DOWNED,
                        "遭遇不该被这些伤害结束：" + LichDirector.stateOf(p));
                // 处决走完照样打得死：收尾那下的 setHealth(0) 不受倒地血量下限影响
                h.assertTrue(MercyServer.executeNow(p, a) == null, "之后应当照常能处决奴仆");
                // /kill 命令照常真死
                lich.invulnerableTime = 0;
                h.assertTrue(KuvaTestKit.killCommand(h, lich) == 1, "/kill 应当打中玄骸");
                h.assertTrue(!lich.isAlive() && DIED.contains(lich.getUUID()), "/kill 命令应当直接打死倒地的玄骸：" + aliveDetail(lich));
                // 真掉出世界（脚底低于世界底 64 格）照常真死
                b.teleportTo(b.getX(), h.getLevel().getMinBuildHeight() - 70.0D, b.getZ());
                b.invulnerableTime = 0;
                b.hurt(b.damageSources().fellOutOfWorld(), 1000.0F);
                h.assertTrue(!b.isAlive() && DIED.contains(b.getUUID()), "真掉出世界应当直接打死倒地的奴仆：" + aliveDetail(b));
                h.runAfterDelay(30, () -> {
                    h.assertTrue(!a.isAlive() && DIED.contains(a.getUUID()), "tick 30 处决应当打死奴仆：" + aliveDetail(a));
                    h.succeed();
                });
            });
        });
    }

    private static String aliveDetail(AbstractKuva t) {
        return "状态 " + t.getMercyState() + "，活着 " + t.isAlive() + "，移除 " + t.getRemovalReason() + "，血 " + t.getHealth();
    }

    @AfterBatch(batch = ISO_MODDED)
    public static void afterModded(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 处决词条：真死了才加击杀叠层 ====================

    @GameTest(template = "empty", batch = ISO_EXEC_STACKS, timeoutTicks = 120)
    public static void executeStacksOnRealDeath(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        card.setDecryptionProgress(0);
        int stage = card.getUnlockedCardStatus();
        KuvaSlaveEntity thrall = spawnThrall(h, p.position().add(0.0D, 0.0D, 2.0D));
        thrall.setHealth(1.0F);
        h.assertTrue(thrall.hurt(p.damageSources().playerAttack(p), 20.0F), "致命一击应当打得进去");
        h.assertTrue(thrall.getMercyState() == MercyState.DOWNED, "奴仆应当倒地");
        Zombie zombie = EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(zombie != null, "应当能造出僵尸");
        Vec3 zAt = p.position().add(0.0D, 0.0D, -3.0D);
        zombie.moveTo(zAt.x, zAt.y, zAt.z, 0.0F, 0.0F);
        zombie.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(zombie), "僵尸应当加得进世界");
        int stacks = KillStackManager.getStacks(p, StackType.BASE_DAMAGE);
        executeEffect(p, thrall);
        executeEffect(p, zombie);
        h.runAfterDelay(1, () -> trueDamageLater(thrall, p, 50.0F));
        h.runAfterDelay(4, () -> {
            assertStillDowned(h, thrall, "处决词条和延迟真伤之后");
            h.assertTrue(!zombie.isAlive(), "处决词条应当打死普通僵尸");
            h.assertTrue(KillStackManager.getStacks(p, StackType.BASE_DAMAGE) == stacks + 1,
                    "只有真死了的僵尸算一次击杀：" + stacks + " → " + KillStackManager.getStacks(p, StackType.BASE_DAMAGE));
            h.assertTrue(card.getDecryptionProgress() == 0, "奴仆还没死，不该给进度");
            h.assertTrue(MercyServer.executeNow(p, thrall) == null, "之后应当照常能开始处决");
            h.runAfterDelay(30, () -> {
                h.assertTrue(!thrall.isAlive(), "tick 30 应当处决致死");
                int got = card.getDecryptionProgress();
                int min = ModConfig.KUVA_LICH.minDecryptionProgress.get();
                int max = ModConfig.KUVA_LICH.maxDecryptionProgress.get();
                h.assertTrue(card.getUnlockedCardStatus() == stage, "这一下不该跨阶段（测试前提）");
                h.assertTrue(got >= min && got <= max, "处决给的进度应当在 [" + min + ", " + max + "]，实际 " + got);
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_EXEC_STACKS)
    public static void afterExecStacks(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 没有玄骸时处决奴仆：不给进度，也不诞生（玄骸只从幼体来） ====================

    @GameTest(template = "empty", batch = ISO_THRALL_BIRTH, timeoutTicks = 120)
    public static void thrallWithoutLichGivesNothing(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        h.assertTrue(!card.hasLich() && card.getUnlockedCardStatus() == 0, "测试前提：新玩家没有玄骸、一条密语都没揭");
        // 进度补到第一段只差一点：以前这一下就会揭开第一条、当场诞生
        int before = Math.max(0, ModConfig.KUVA_LICH.firstStage.get() - 1);
        card.setDecryptionProgress(before);
        KuvaSlaveEntity thrall = spawnThrall(h, p.position().add(0.0D, 0.0D, 2.0D));
        thrall.setHealth(1.0F);
        h.assertTrue(thrall.hurt(p.damageSources().playerAttack(p), 20.0F), "致命一击应当打得进去");
        h.assertTrue(thrall.getMercyState() == MercyState.DOWNED && thrall.isAlive(), "奴仆应当倒地");
        h.assertTrue(MercyServer.executeNow(p, thrall) == null, "应当能开始处决");
        h.runAfterDelay(30, () -> {
            h.assertTrue(!thrall.isAlive(), "tick 30 应当死了");
            h.assertTrue(card.getDecryptionProgress() == before && card.getUnlockedCardStatus() == 0,
                    "没有玄骸时奴仆不给进度（进度 " + card.getDecryptionProgress() + "）");
            h.assertTrue(!card.hasLich(), "处决奴仆不应当诞生玄骸");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_THRALL_BIRTH)
    public static void afterThrallBirth(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 弱点 / 抗性按实际倍率发包 ====================

    @GameTest(template = "empty", batch = CFG_AFFINITY, timeoutTicks = 60)
    public static void affinityHitFlags(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        // 固定倍率：奴仆挨箭是弱点（发给附近），挨魔法是抗性（只发给打它的人），近战两样都不发
        KuvaSlaveEntity thrall = spawnThrall(h, h.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D)));
        thrall.setNoAi(true);
        Arrow arrow = new Arrow(h.getLevel(), p);
        arrow.setPos(thrall.getX(), thrall.getY() + 1.0D, thrall.getZ());
        long weak0 = KuvaStateServer.sentCount(KuvaStateKind.WEAK);
        long resist0 = KuvaStateServer.sentCount(KuvaStateKind.RESIST);
        thrall.invulnerableTime = 0;
        h.assertTrue(thrall.hurt(thrall.damageSources().arrow(arrow, p), 1.0F), "箭应当打得进去");
        h.assertTrue(KuvaStateServer.sentCount(KuvaStateKind.WEAK) == weak0 + 1, "弓箭打奴仆应当发一次弱点");
        thrall.invulnerableTime = 0;
        h.assertTrue(thrall.hurt(p.damageSources().playerAttack(p), 1.0F), "近战应当打得进去");
        h.assertTrue(KuvaStateServer.sentCount(KuvaStateKind.WEAK) == weak0 + 1
                && KuvaStateServer.sentCount(KuvaStateKind.RESIST) == resist0, "近战既不是弱点也不是抗性");
        thrall.invulnerableTime = 0;
        h.assertTrue(thrall.hurt(p.damageSources().indirectMagic(p, p), 1.0F), "魔法应当打得进去");
        h.assertTrue(KuvaStateServer.sentCount(KuvaStateKind.RESIST) == resist0 + 1, "魔法打奴仆应当发一次抗性");
        thrall.discard();

        // 每只不同（默认开）：有主的玄骸按自己那四类的倍率算，箭不一定是弱点
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichVariedTraits, true);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(lich != null, "应当能造出玄骸");
        lich.bindOwner(p, card);
        LichTraits traits = LichTraits.current(card.getLichSeed(), card.getLichRank());
        float arrowMult = lich.damageTypeMultiplier(lich.damageSources().arrow(arrow, p));
        float meleeMult = lich.damageTypeMultiplier(p.damageSources().playerAttack(p));
        h.assertTrue(arrowMult == (float) traits.affinity(1), "弓箭的倍率应当是 " + traits.affinity(1) + "，实际 " + arrowMult);
        h.assertTrue(meleeMult == (float) traits.affinity(0), "近战的倍率应当是 " + traits.affinity(0) + "，实际 " + meleeMult);
        lich.discard();

        // 关掉：专属玄骸也照固定倍率（弓箭 ×1.25、近战 ×1）
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichVariedTraits, false);
        KuvaMasterEntity plain = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(plain != null, "应当能造出玄骸");
        plain.bindOwner(p, card);
        h.assertTrue(plain.damageTypeMultiplier(plain.damageSources().arrow(arrow, p)) == 1.25F
                && plain.damageTypeMultiplier(p.damageSources().playerAttack(p)) == 1.0F, "关掉后应当是固定倍率");
        plain.discard();
        arrow.discard();
        h.succeed();
    }

    // ==================== 专属面板套到实体上 ====================

    @GameTest(template = "empty", batch = CFG_AFFINITY, timeoutTicks = 60)
    public static void traitsOnEntity(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichVariedTraits, true);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(lich != null, "应当能造出玄骸");
        lich.bindOwner(p, card);
        LichTraits t = LichTraits.current(card.getLichSeed(), card.getLichRank());
        h.assertTrue(Math.abs(lich.getMaxHealth() - t.maxHealth) < 1.0E-3D && lich.getHealth() == lich.getMaxHealth(),
                "最大生命应当是 " + t.maxHealth + " 且满血，实际 " + lich.getHealth() + " / " + lich.getMaxHealth());
        h.assertTrue(Math.abs(lich.getAttributeValue(Attributes.ATTACK_DAMAGE) - t.attack) < 1.0E-6D
                && Math.abs(lich.getAttributeValue(Attributes.ARMOR) - t.armor) < 1.0E-6D
                && Math.abs(lich.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - t.knockbackResist) < 1.0E-6D,
                "攻击、护甲、击退抗性应当套上面板");
        h.assertTrue(lich.adaptStackCap() == t.adaptStacks() && lich.combatTraits() != null,
                "适应上限应当按面板");
        double fury = ModConfig.KUVA_LICH.battleBoost.get() * t.furyScale();
        h.assertTrue(Math.abs(lich.furyPerSecond() - fury) < 1.0E-9D, "交战增幅应当按档位");

        // 判错一次：等级 +1，最大生命跟着等级曲线涨，血量比例不变
        lich.setHealth(lich.getMaxHealth() * 0.5F);
        int rank = card.getLichRank();
        lich.applyFailure(card);
        double step = ModConfig.KUVA_LICH.lichRankHealthStep.get();
        double expect = t.maxHealth * LichTraits.rankScale(rank + 1, step) / LichTraits.rankScale(rank, step);
        h.assertTrue(card.getLichRank() == rank + 1 && Math.abs(lich.getMaxHealth() - expect) < 1.0E-2D,
                "升一级后最大生命应当是 " + expect + "，实际 " + lich.getMaxHealth());
        h.assertTrue(Math.abs(lich.getHealth() / lich.getMaxHealth() - 0.5F) < 1.0E-3F, "血量比例应当不变");
        lich.discard();

        // 无主的不套面板，照原来的固定数值
        KuvaMasterEntity wild = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(wild != null && wild.combatTraits() == null
                && Math.abs(wild.getMaxHealth() - LichTraits.BASE_HEALTH) < 1.0E-3D
                && wild.adaptStackCap() == 6, "无主的玄骸应当还是 200 血、适应 6 层");
        wild.discard();
        h.succeed();
    }

    @AfterBatch(batch = CFG_AFFINITY)
    public static void afterAffinity(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #26 原版自然消失：附近有人先演离场 ====================

    @BeforeBatch(batch = ISO_NATURAL)
    public static void beforeNatural(ServerLevel level) {
        savedDifficulty = level.getServer().getWorldData().getDifficulty();
    }

    @GameTest(template = "empty", batch = ISO_NATURAL, timeoutTicks = 120)
    public static void naturalLeaveDissolves(GameTestHelper h) {
        listenDeaths();
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichWildAllowed, true);
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        if (server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            server.setDifficulty(Difficulty.NORMAL, true);
        }
        clearAround(h);
        Vec3 base = h.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        // ② 64 格内没有玩家：切和平后直接移除
        KuvaSlaveEntity loneThrall = spawnThrall(h, base);
        KuvaMasterEntity loneLich = spawnWildLich(h, base.add(2.0D, 0.0D, 0.0D));
        server.setDifficulty(Difficulty.PEACEFUL, true);
        h.runAfterDelay(2, () -> {
            h.assertTrue(loneThrall.isRemoved() && loneLich.isRemoved(), "附近没人时应当照原样直接移除");
            // ① 旁边 10 格内站一个人：先演离场
            ServerPlayer p = KuvaTestKit.player(h);
            p.setGameMode(GameType.CREATIVE);
            KuvaSlaveEntity thrall = spawnThrall(h, p.position().add(3.0D, 0.0D, 0.0D));
            KuvaMasterEntity lich = spawnWildLich(h, p.position().add(-3.0D, 0.0D, 0.0D));
            AABB area = new AABB(p.blockPosition()).inflate(12.0D);
            clearItems(h, area);
            h.runAfterDelay(2, () -> {
                h.assertTrue(!thrall.isRemoved() && !lich.isRemoved(), "附近有人时当场不移除");
                h.assertTrue(thrall.getMercyState() == MercyState.LEAVING && lich.getMercyState() == MercyState.LEAVING,
                        "两只都应当进离场：" + thrall.getMercyState() + " / " + lich.getMercyState());
            });
            h.runAfterDelay(12, () -> h.assertTrue(lich.isRemoved(), "玄骸离场 8 tick 后应当移除"));
            h.runAfterDelay(26, () -> h.assertTrue(!thrall.isRemoved(), "奴仆消散 30 tick 之内还在"));
            h.runAfterDelay(34, () -> {
                h.assertTrue(thrall.isRemoved(), "奴仆消散 30 tick 后应当移除");
                h.assertTrue(itemsIn(h, area) == 0, "不应当有掉落物：" + itemNames(h, area));
                h.assertTrue(!DIED.contains(thrall.getUUID()) && !DIED.contains(lich.getUUID()), "不应当触发死亡事件");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_NATURAL)
    public static void afterNatural(ServerLevel level) {
        if (savedDifficulty != null) {
            level.getServer().setDifficulty(savedDifficulty, true);
            savedDifficulty = null;
        }
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #27 碰撞箱跟着同步数据变 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void hitboxFollowsSyncedState(GameTestHelper h) {
        clearAround(h);
        Vec3 at = h.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        KuvaSlaveEntity thrall = spawnThrall(h, at);
        thrall.setNoAi(true);
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(lich != null, "应当能造出玄骸");
        lich.markDemo();
        lich.setNoAi(true);
        lich.moveTo(at.x + 2.0D, at.y, at.z, 0.0F, 0.0F);
        h.assertTrue(h.getLevel().addFreshEntity(lich), "演示玄骸应当加得进世界");
        float lichStand = lich.getBbHeight();
        float thrallStand = thrall.getBbHeight();
        // 只写同步数据、不调 refreshDimensions：两端都靠 onSyncedDataUpdated 刷
        lich.setMercyState(MercyState.DOWNED);
        thrall.setMercyState(MercyState.DOWNED);
        assertHeight(h, lich, LICH_DOWNED_HEIGHT);
        assertHeight(h, thrall, THRALL_DOWNED_HEIGHT);
        lich.setMercyState(MercyState.NONE);
        thrall.setMercyState(MercyState.NONE);
        h.assertTrue(Math.abs(lich.getBbHeight() - lichStand) < 1.0E-3F && Math.abs(thrall.getBbHeight() - thrallStand) < 1.0E-3F,
                "设回正常后碰撞箱应当复原");
        lich.discard();
        thrall.discard();
        h.succeed();
    }

    /** 消散中的奴仆、演离场的无主玄骸不存盘（关着 AI、状态又不进存档，存下来读回来是一只不会动的怪） */
    @GameTest(template = "empty", batch = CORE)
    public static void leavingNotSaved(GameTestHelper h) {
        clearAround(h);
        Vec3 at = h.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
        KuvaSlaveEntity thrall = spawnThrall(h, at);
        thrall.setNoAi(true);
        h.assertTrue(thrall.shouldBeSaved(), "平常的奴仆应当存盘");
        thrall.beginExpire();
        h.assertTrue(thrall.isLeaving() && !thrall.shouldBeSaved(), "消散中的奴仆不应当存盘");
        KuvaMasterEntity wild = spawnWildLich(h, at.add(3.0D, 0.0D, 0.0D));
        wild.setNoAi(true);
        h.assertTrue(wild.isWild() && wild.shouldBeSaved(), "平常的无主玄骸应当存盘");
        wild.departNow(0);
        h.assertTrue(wild.isDeparting() && !wild.shouldBeSaved(), "演离场的无主玄骸不应当存盘");
        thrall.discard();
        wild.discard();
        h.succeed();
    }

    @AfterBatch(batch = CORE)
    public static void afterCore(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 工具 ====================

    static void listenDeaths() {
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

    /** 处决词条（阈值 100%，带基础伤害击杀叠层）打一下：判定成立，1 tick 后强杀 */
    private static void executeEffect(ServerPlayer p, LivingEntity target) {
        HashMap<String, Double> attributes = new HashMap<>();
        attributes.put("execute_threshold", 1.0D);
        attributes.put("killStackBaseDamage", 1.0D);
        try {
            Method m = WeaponCombatHandler.class.getDeclaredMethod("applyExecuteEffects",
                    LivingEntity.class, LivingEntity.class, HashMap.class, boolean.class, float.class);
            m.setAccessible(true);
            m.invoke(null, p, target, attributes, true, 0.0F);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调不到处决词条的结算", e);
        }
    }

    /** 和真实子弹命中后隔 1 tick 的那一下一样：不致死就直接扣血，致死就强杀 */
    private static void trueDamageLater(LivingEntity target, ServerPlayer p, float damage) {
        if (target.isDeadOrDying()) {
            return;
        }
        if (target.getHealth() - damage > 0.01F) {
            LivingEntityUtil.damageHealthDirectly(target, damage);
        } else {
            LivingEntityUtil.kill(target, p.damageSources().playerAttack(p));
        }
    }

    /** 还在倒地、血量没掉出这一段（倒地期间这一段的血跟着计时从底往回涨，所以不比具体值） */
    private static void assertStillDowned(GameTestHelper h, AbstractKuva t, String when) {
        h.assertTrue(t.isAlive() && t.getMercyState() == MercyState.DOWNED
                        && t.getHealth() >= t.downedHealth() - 1.0E-4F && t.getHealth() <= t.segmentCeil() + 1.0E-3F,
                when + "应当还在倒地、血量还在这一段里：状态 " + t.getMercyState() + "，活着 " + t.isAlive()
                        + "，移除 " + t.getRemovalReason() + "，血 " + t.getHealth());
    }

    /** 倒地中此刻该有的血量：这一段的底按倒地计时走过的比例往顶涨（最后一段从 1 起） */
    private static float downedRefill(AbstractKuva t) {
        int total = Math.max(1, ModConfig.KUVA_LICH.mercyDownedSeconds.get() * 20);
        float u = Mth.clamp(1.0F - (float) t.getMercyRemainingServer() / total, 0.0F, 1.0F);
        float floor = t.segmentFloor();
        return Math.max(1.0F, floor + (t.segmentCeil() - floor) * u);
    }

    private static RequiemCard card(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD)
                .orElseThrow(() -> new IllegalStateException("测试玩家没有安魂卡能力"));
    }

    /** 走正常的进度流程揭开第一条密语，再让玄骸诞生（平常要处决一只幼体，这里直接生） */
    static void bornByProgress(ServerPlayer p, RequiemCard card) {
        int threshold = ModConfig.KUVA_LICH.firstStage.get();
        card.setDecryptionProgress(Math.max(0, threshold - 1));
        int levelBefore = card.getUnlockedCardStatus();
        card.addPotion(1);
        LichDirector.onDecryptionProgress(p, card, 1, levelBefore);
        LichDirector.birthNow(p);
    }

    /**
     * 摆三张卡，让前缀长度正好是 {@code prefix}：答案定为 0 / 1 / 2，前 prefix 张放对的，第 prefix + 1 张放错的，
     * 之后的放对的（判错之后不看）。卡都是新的（耐久满）
     */
    static void setCards(RequiemCard card, int prefix) {
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

    /** 背包里有几张安魂卡 */
    private static int survivingCards(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) {
            if (!s.isEmpty() && s.getItem() instanceof pers.roinflam.kuvalich.base.item.AbstractRequiemCard) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** 立即召到面前并返回那只：摆到最后一段（下次处决三刀）、打掉护盾且不再恢复 */
    static KuvaMasterEntity summonFront(GameTestHelper h, ServerPlayer p) {
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 3, c.getZ() + 11);
        h.assertTrue(LichDirector.summonNow(p, SummonMode.FRONT), "立即召唤应当成功");
        KuvaMasterEntity lich = LichDirector.getLich(p).orElseThrow(() -> new IllegalStateException("召唤后应当有在场的玄骸"));
        lich.setLichPhase(KuvaMasterEntity.SEGMENTS - 1);
        lich.breakShield(SHIELD_HOLD);
        return lich;
    }

    /** 测试期间护盾不恢复 */
    private static final int SHIELD_HOLD = 1_000_000;

    private static KuvaSlaveEntity spawnThrall(GameTestHelper h, Vec3 at) {
        KuvaSlaveEntity thrall = KuvaLichEntities.KUVA_SLAVE.get().create(h.getLevel());
        h.assertTrue(thrall != null, "应当能造出奴仆");
        thrall.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        h.assertTrue(h.getLevel().addFreshEntity(thrall), "奴仆应当加得进世界");
        return thrall;
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
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, area, MercyCoreTests::isOurDrop).size();
    }

    /** 终结奖励里固定有一个圣骸匣：背包里和收件箱里的加起来 */
    static int reliquaries(ServerPlayer p) {
        int n = p.getInventory().countItem(KuvaLichItems.LICH_RELIQUARY.get());
        for (ItemStack s : PlayerLedger.getInbox(p)) {
            if (s.is(KuvaLichItems.LICH_RELIQUARY.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static boolean isOurDrop(ItemEntity e) {
        if (e.isRemoved()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(e.getItem().getItem());
        return id != null && Reference.MOD_ID.equals(id.getNamespace());
    }

    /** 清掉区域里已有的掉落物（上一批测试留在地上的） */
    private static void clearItems(GameTestHelper h, AABB area) {
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> !e.isRemoved())) {
            e.discard();
        }
    }

    /** 区域里的掉落物（失败信息里列出来，方便看是谁掉的） */
    private static String itemNames(GameTestHelper h, AABB area) {
        StringBuilder sb = new StringBuilder();
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area, MercyCoreTests::isOurDrop)) {
            sb.append(' ').append(e.getItem().getDescriptionId()).append('×').append(e.getItem().getCount())
                    .append('@').append(e.blockPosition().toShortString()).append("/age").append(e.getAge());
        }
        return sb.toString();
    }

    /**
     * 折翼荡身、踏背都在目标右手边那一侧：空地上能选；头顶只有两格高、右手边贴墙都不选，左手边贴墙照选。
     * 正面直刺哪里都能演
     */
    @GameTest(template = "empty")
    public static void lichFNeedsRoomOnItsRight(GameTestHelper h) {
        clearAround(h);
        BlockPos c = KuvaTestKit.center(h);
        ServerPlayer p = KuvaTestKit.player(h);
        ServerLevel level = h.getLevel();
        Vec3 pl = h.absoluteVec(new Vec3(c.getX() + 0.5D, 0.0D, c.getZ() + 0.5D));
        // 目标朝 +X 站着，右手边是 +Z
        Vec3 f = new Vec3(1.0D, 0.0D, 0.0D);
        MercyVariant v = MercyVariant.LICH_F;
        h.assertTrue(MercyServer.fits(level, p, pl, f, v), "空地上应当能选折翼");
        for (int x = c.getX() - 5; x <= c.getX() + 5; x++) {
            for (int z = c.getZ() - 5; z <= c.getZ() + 5; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        h.assertFalse(MercyServer.fits(level, p, pl, f, v), "头顶两格时不该选折翼");
        h.assertTrue(MercyServer.fits(level, p, pl, f, MercyVariant.LICH_A), "头顶两格时正面直刺照样能选");
        for (int x = c.getX() - 5; x <= c.getX() + 5; x++) {
            for (int z = c.getZ() - 5; z <= c.getZ() + 5; z++) {
                h.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        for (int x = c.getX() - 5; x <= c.getX() + 5; x++) {
            for (int y = 0; y <= 4; y++) {
                h.setBlock(new BlockPos(x, y, c.getZ() - 2), Blocks.STONE);
            }
        }
        h.assertTrue(MercyServer.fits(level, p, pl, f, v), "左手边贴墙时折翼照样能选");
        for (int x = c.getX() - 5; x <= c.getX() + 5; x++) {
            for (int y = 0; y <= 4; y++) {
                h.setBlock(new BlockPos(x, y, c.getZ() + 2), Blocks.STONE);
            }
        }
        h.assertFalse(MercyServer.fits(level, p, pl, f, v), "右手边贴墙时不该选折翼");
        h.succeed();
    }

    /**
     * 断膝三方滑铲擦过它右手边、越背侧翻团着身翻过它后背上方：空地上能选；头顶只有三格、右手边贴墙都不选，
     * 左手边两格外有墙照选。正面直刺哪里都能演
     */
    @GameTest(template = "empty")
    public static void lichGNeedsRoomOverItsBack(GameTestHelper h) {
        clearAround(h);
        BlockPos c = KuvaTestKit.center(h);
        ServerPlayer p = KuvaTestKit.player(h);
        ServerLevel level = h.getLevel();
        Vec3 pl = h.absoluteVec(new Vec3(c.getX() + 0.5D, 0.0D, c.getZ() + 0.5D));
        // 目标朝 +X 站着，右手边是 +Z
        Vec3 f = new Vec3(1.0D, 0.0D, 0.0D);
        MercyVariant v = MercyVariant.LICH_G;
        h.assertTrue(MercyServer.fits(level, p, pl, f, v), "空地上应当能选断膝三方");
        for (int x = c.getX() - 5; x <= c.getX() + 5; x++) {
            for (int z = c.getZ() - 5; z <= c.getZ() + 5; z++) {
                h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
            }
        }
        h.assertFalse(MercyServer.fits(level, p, pl, f, v), "头顶三格时不该选断膝三方");
        h.assertTrue(MercyServer.fits(level, p, pl, f, MercyVariant.LICH_A), "头顶三格时正面直刺照样能选");
        for (int x = c.getX() - 5; x <= c.getX() + 5; x++) {
            for (int z = c.getZ() - 5; z <= c.getZ() + 5; z++) {
                h.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
            }
        }
        for (int x = c.getX() - 5; x <= c.getX() + 5; x++) {
            for (int y = 0; y <= 4; y++) {
                h.setBlock(new BlockPos(x, y, c.getZ() - 2), Blocks.STONE);
            }
        }
        h.assertTrue(MercyServer.fits(level, p, pl, f, v), "左手边两格外有墙时断膝三方照样能选");
        for (int x = c.getX() - 5; x <= c.getX() + 5; x++) {
            for (int y = 0; y <= 4; y++) {
                h.setBlock(new BlockPos(x, y, c.getZ() - 2), Blocks.AIR);
                h.setBlock(new BlockPos(x, y, c.getZ() + 2), Blocks.STONE);
            }
        }
        h.assertFalse(MercyServer.fits(level, p, pl, f, v), "右手边贴墙时不该选断膝三方");
        h.succeed();
    }

    private static void assertHeight(GameTestHelper h, AbstractKuva e, float expected) {
        h.assertTrue(Math.abs(e.getBbHeight() - expected) < 0.01F,
                e.getType().getDescriptionId() + " 倒地碰撞箱高应当是 " + expected + "，实际 " + e.getBbHeight());
    }

    /** 挖一块封闭的场地（地板、围墙、封顶，和天然地形隔开），见 {@link KuvaTestKit#openArena} */
    private static void openArena(GameTestHelper h, int x0, int x1, int z0, int z1) {
        KuvaTestKit.openArena(h, x0, x1, z0, z1);
    }

    /** 不放玄骸的测试：以结构中心为心挖一块 13 × 13 的空地 */
    private static void clearAround(GameTestHelper h) {
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 6, c.getZ() + 6);
    }

    /** 测试结构周围的区块同步载入（只载有强制票的那些） */
    private static void loadTestChunks(GameTestHelper h) {
        ChunkPos origin = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        for (int dx = -1; dx < 4; dx++) {
            for (int dz = -1; dz < 4; dz++) {
                h.getLevel().getChunk(origin.x + dx, origin.z + dz);
            }
        }
    }
}
