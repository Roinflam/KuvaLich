package pers.roinflam.kuvalich.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.AllyGoals;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.nemesis.AllyDirector;
import pers.roinflam.kuvalich.nemesis.AllyRoster;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 感化后的盟友（C3）。现身判断里有「身边 48 格内已有 2 只别人的盟友就不来」，同一 batch 的测试是挨着摆的，
 * 所以会召出盟友的测试每个 batch 最多放两个
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AllyTests {

    private static final String SPAWN = "kl_ally_spawn";
    private static final String DAMAGE = "kl_ally_damage";
    private static final String AOE = "kl_ally_aoe";
    private static final String LEAVE = "kl_ally_leave";
    private static final String TRIGGER = "kl_ally_trigger";
    private static final String CAP = "kl_ally_cap";
    private static final String ROSTER = "kl_ally_roster";
    private static final String HUNT = "kl_ally_hunt";
    private static final String CREDIT = "kl_ally_credit";

    /** 新玩家进服后 60 tick 不吃非绕过无敌的伤害，挨打的测试等它过去 */
    private static final int T0 = 62;

    private AllyTests() {
    }

    // ==================== 工具 ====================

    private static ServerPlayer allyPlayer(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        KuvaTestKit.override(ModConfig.KUVA_LICH.allyEnabled, true);
        MinecraftServer server = h.getLevel().getServer();
        if (server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            server.setDifficulty(Difficulty.NORMAL, true);
        }
        return p;
    }

    private static CompoundTag entry(int gen, String name, long seed) {
        CompoundTag t = new CompoundTag();
        t.putLong(AllyRoster.SEED, seed);
        t.putString(AllyRoster.NAME, name);
        t.putInt(AllyRoster.GENERATION, gen);
        t.putInt(AllyRoster.RANK, 3);
        t.putInt(AllyRoster.WEAPON, 0);
        t.putInt(AllyRoster.WEAPON_LEVEL, 10);
        t.putString(AllyRoster.WEAPON_TYPE, "");
        t.putString(AllyRoster.EPHEMERA, "");
        t.putLong(AllyRoster.CONVERTED_AT, 0L);
        return t;
    }

    private static void giveRoster(ServerPlayer p, CompoundTag... entries) {
        PlayerLedger.setAllies(p, new ArrayList<>(List.of(entries)));
    }

    /** 名册放一只、开出现身的场地、强制召出来 */
    private static KuvaMasterEntity summoned(GameTestHelper h, ServerPlayer p, String name) {
        BlockPos c = KuvaTestKit.center(h);
        KuvaTestKit.openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 3, c.getZ() + 11);
        giveRoster(p, entry(7, name, 0x1234_5678_9ABCL ^ name.hashCode()));
        String why = AllyDirector.summon(p, true, false);
        h.assertTrue(why.isEmpty(), "盟友应当召得出来，实际：" + why);
        KuvaMasterEntity ally = AllyDirector.presentOf(p.getUUID());
        h.assertTrue(ally != null, "召出来以后导演应当记着在场");
        return ally;
    }

    private static Husk husk(GameTestHelper h, Vec3 at, boolean ai) {
        Husk z = EntityType.HUSK.create(h.getLevel());
        h.assertTrue(z != null, "应当能造出尸壳");
        z.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        z.setNoAi(!ai);
        z.setPersistenceRequired();
        h.assertTrue(h.getLevel().addFreshEntity(z), "尸壳应当加得进世界");
        return z;
    }

    private static int count(GameTestHelper h, Class<? extends Entity> type, AABB box) {
        return h.getLevel().getEntitiesOfClass(type, box).size();
    }

    // ==================== 生成 ====================

    @GameTest(template = "empty", batch = SPAWN, timeoutTicks = 80)
    public static void spawnNotWildNotSaved(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        // 野生玄骸不许出现时，盟友照样要进得来、也不能被标成野生
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichWildAllowed, false);
        KuvaMasterEntity ally = summoned(h, p, "Vor Halcyon");
        h.assertTrue(!ally.isRemoved() && ally.isAlive(), "进世界时不该被拦掉");
        h.assertTrue(ally.isAlly() && ally.isAllyOf(p), "应当是这个玩家的盟友");
        h.assertTrue(!ally.isOwned() && !ally.isWild(), "盟友不能有主、也不能是野生");
        h.assertTrue(!ally.shouldBeSaved() && !ally.canChangeDimensions(), "盟友不存盘、不走传送门");
        h.assertTrue("Vor Halcyon".equals(ally.getLichName()) && ally.getAllyGeneration() == 7, "名字、代数照名册");
        h.assertTrue(ally.combatTraits() != null, "盟友应当有按种子算的面板");
        h.assertTrue(!ally.canBeExecutedBy(p), "盟友不能被处决");
        h.assertTrue(!ally.isPreventingPlayerRest(p), "身边有盟友照样能睡觉");
        h.assertTrue(PlayerLedger.getAllyCooldownUntil(p) == 0L, "管理命令召的不写冷却");
        h.assertTrue("present".equals(AllyDirector.blocker(p, h.getLevel().getServer().overworld().getGameTime(), true)),
                "已经有一只在场时不再来第二只");
        h.assertTrue(!AllyDirector.summon(p, true, false).isEmpty() && AllyDirector.presentOf(p.getUUID()) == ally,
                "同一玩家召两次只有一只");
        h.succeed();
    }

    @GameTest(template = "empty", batch = SPAWN, timeoutTicks = 80)
    public static void loginDelayAndBlockers(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        long now = h.getLevel().getServer().overworld().getGameTime();
        h.assertTrue("roster".equals(AllyDirector.blocker(p, now, false)), "名册空不来");
        giveRoster(p, entry(1, "Tel Varo", 99L));
        AllyDirector.markLogin(p);
        h.assertTrue("login".equals(AllyDirector.blocker(p, now, false)), "登录不满 60 秒不来");
        AllyDirector.skipLoginDelay(p);
        PlayerLedger.setAllyCooldownUntil(p, now + 1000L);
        h.assertTrue("cooldown".equals(AllyDirector.blocker(p, now, false)), "冷却没到不来");
        PlayerLedger.setAllyCooldownUntil(p, 0L);
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue("creative".equals(AllyDirector.blocker(p, now, false)), "创造模式不来");
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        KuvaTestKit.override(ModConfig.KUVA_LICH.allyEnabled, false);
        h.assertTrue("disabled".equals(AllyDirector.blocker(p, now, false)), "总开关关着不来");
        KuvaTestKit.override(ModConfig.KUVA_LICH.allyEnabled, true);
        h.assertTrue(AllyDirector.blocker(p, now, false).isEmpty(), "条件都满足时应当能来");
        h.succeed();
    }

    // ==================== 伤害 ====================

    @GameTest(template = "empty", batch = DAMAGE, timeoutTicks = 120)
    public static void immuneToPlayerSide(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        ServerPlayer other = allyPlayer(h);
        KuvaMasterEntity ally = summoned(h, p, "Ruk Senna");
        h.runAfterDelay(KuvaMasterEntity.APPEAR_GRACE_TICKS + 2, () -> {
            float hp = ally.getHealth();
            ally.invulnerableTime = 0;
            h.assertTrue(!ally.hurt(p.damageSources().playerAttack(p), 20.0F), "主人打不动");
            ally.invulnerableTime = 0;
            h.assertTrue(!ally.hurt(other.damageSources().playerAttack(other), 20.0F), "别的玩家打不动");
            Arrow arrow = new Arrow(h.getLevel(), other);
            ally.invulnerableTime = 0;
            h.assertTrue(!ally.hurt(ally.damageSources().arrow(arrow, other), 20.0F), "玩家射的箭打不动");
            Wolf wolf = EntityType.WOLF.create(h.getLevel());
            h.assertTrue(wolf != null, "应当能造出狼");
            wolf.moveTo(ally.getX(), ally.getY(), ally.getZ() + 2.0D);
            wolf.tame(other);
            ally.invulnerableTime = 0;
            h.assertTrue(!ally.hurt(ally.damageSources().mobAttack(wolf), 20.0F), "玩家的宠物打不动");
            h.assertTrue(ally.getHealth() == hp, "玩家这一边的伤害一点都不该吃");
            Husk z = husk(h, ally.position().add(2.0D, 0.0D, 0.0D), false);
            ally.invulnerableTime = 0;
            h.assertTrue(ally.hurt(ally.damageSources().mobAttack(z), 4.0F) && ally.getHealth() < hp, "敌对生物的伤害照常吃");
            h.assertTrue(!AllyGoals.isFoe(ally, p) && !AllyGoals.isFoe(ally, other) && !AllyGoals.isFoe(ally, wolf),
                    "玩家、宠物都不是它的敌人");
            h.assertTrue(AllyGoals.isFoe(ally, z), "尸壳是敌人");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = DAMAGE, timeoutTicks = 120)
    public static void knockedOutLeavesNoDrops(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        KuvaMasterEntity ally = summoned(h, p, "Mok Teral");
        AABB box = new AABB(ally.blockPosition()).inflate(10.0D);
        h.runAfterDelay(KuvaMasterEntity.APPEAR_GRACE_TICKS + 2, () -> {
            Husk z = husk(h, ally.position().add(2.0D, 0.0D, 0.0D), false);
            // 单次伤害有软上限，先把血压到 1 再打
            ally.setHealth(1.0F);
            ally.invulnerableTime = 0;
            ally.hurt(ally.damageSources().mobAttack(z), 50.0F);
            h.assertTrue(ally.isAlive() && ally.isDeparting(), "血量到 0 应当是退去，不是死");
            h.assertTrue(ally.getMercyState() == MercyState.NONE, "盟友不进倒地");
            h.runAfterDelay(KuvaMasterEntity.DEPART_TICKS + 3, () -> {
                h.assertTrue(ally.isRemoved(), "退去演完应当移除");
                h.assertTrue(AllyDirector.presentOf(p.getUUID()) == null, "移除后导演那边的在场应当清掉");
                h.assertTrue(count(h, ItemEntity.class, box) == 0 && count(h, ExperienceOrb.class, box) == 0,
                        "不掉东西、不掉经验");
                h.assertTrue(PlayerLedger.getAllies(p).size() == 1, "被打倒不影响名册");
                h.succeed();
            });
        });
    }

    // ==================== 震荡 / 索敌 ====================

    @GameTest(template = "empty", batch = AOE, timeoutTicks = 200)
    public static void quakeOnlyHitsEnemies(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        ServerPlayer mate = allyPlayer(h);
        KuvaMasterEntity ally = summoned(h, p, "Dra Kesh");
        // 等过新玩家的出生无敌，不然「主人不掉血」测不出东西
        h.runAfterDelay(T0, () -> {
            ally.setNoAi(true);
            Vec3 at = ally.position();
            p.teleportTo(h.getLevel(), at.x + 1.0D, at.y, at.z, 0.0F, 0.0F);
            mate.teleportTo(h.getLevel(), at.x - 1.0D, at.y, at.z, 0.0F, 0.0F);
            Villager v = EntityType.VILLAGER.create(h.getLevel());
            Wolf wolf = EntityType.WOLF.create(h.getLevel());
            h.assertTrue(v != null && wolf != null, "应当能造出村民和狼");
            v.moveTo(at.x, at.y, at.z + 1.0D);
            v.setNoAi(true);
            wolf.moveTo(at.x, at.y, at.z - 1.0D);
            wolf.setNoAi(true);
            wolf.tame(mate);
            h.getLevel().addFreshEntity(v);
            h.getLevel().addFreshEntity(wolf);
            Husk main = husk(h, at.add(1.0D, 0.0D, 1.0D), false);
            Husk side = husk(h, at.add(-1.0D, 0.0D, 1.0D), false);
            float pHp = p.getHealth();
            float mHp = mate.getHealth();
            float vHp = v.getHealth();
            float wHp = wolf.getHealth();
            float sideHp = side.getHealth();
            ally.doHurtTarget(main);
            h.assertTrue(p.getHealth() == pHp && mate.getHealth() == mHp, "主人和队友站在旁边不掉血");
            h.assertTrue(v.getHealth() == vHp && wolf.getHealth() == wHp, "村民、宠物不被扫到");
            h.assertTrue(side.getHealth() < sideHp, "旁边的敌对生物照常被扫到");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = AOE, timeoutTicks = 300)
    public static void fightsEnemyNeverPlayers(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        KuvaMasterEntity ally = summoned(h, p, "Zan Oruk");
        // 摆在主人和盟友之间，场地只开到盟友身后几格
        Husk z = husk(h, ally.position().add(1.5D, 0.0D, -2.0D), false);
        boolean[] targetedPlayer = {false};
        float hp0 = p.getHealth();
        for (int t = 1; t < 200; t++) {
            h.runAfterDelay(t, () -> {
                if (ally.getTarget() instanceof Player) {
                    targetedPlayer[0] = true;
                }
            });
        }
        h.runAfterDelay(200, () -> {
            h.assertTrue(!targetedPlayer[0], "盟友的目标不该是玩家");
            h.assertTrue(z.getLastHurtByMob() == ally || !z.isAlive(), "盟友应当去打身边的敌对生物");
            h.assertTrue(p.getHealth() >= hp0, "主人不该掉血");
            h.succeed();
        });
    }

    // ==================== 离场 ====================

    @GameTest(template = "empty", batch = LEAVE, timeoutTicks = 80)
    public static void ownerLogoutAndDeathDepart(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        ServerPlayer q = allyPlayer(h);
        KuvaMasterEntity a = summoned(h, p, "Hes Mira");
        KuvaMasterEntity b = summoned(h, q, "Ost Vale");
        h.getLevel().getServer().getPlayerList().remove(p);
        h.assertTrue(a.isDeparting(), "主人下线盟友当场退去");
        q.hurt(q.damageSources().genericKill(), Float.MAX_VALUE);
        h.assertTrue(!q.isAlive() && b.isDeparting(), "主人死亡盟友当场退去");
        h.runAfterDelay(KuvaMasterEntity.DEPART_TICKS + 3, () -> {
            h.assertTrue(a.isRemoved() && b.isRemoved(), "退去后移除");
            h.assertTrue(AllyDirector.presentOf(p.getUUID()) == null && AllyDirector.presentOf(q.getUUID()) == null,
                    "在场应当清掉");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = LEAVE + "_kill", timeoutTicks = 80)
    public static void killAndDiscardClearPresence(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        ServerPlayer q = allyPlayer(h);
        KuvaMasterEntity a = summoned(h, p, "Kai Runo");
        KuvaMasterEntity b = summoned(h, q, "Lus Peyr");
        h.assertTrue(KuvaTestKit.killCommand(h, a) == 1, "/kill 应当打中");
        b.discard();
        h.assertTrue(AllyDirector.presentOf(q.getUUID()) == null, "被 discard 当场清掉在场");
        h.runAfterDelay(30, () -> {
            h.assertTrue(a.isRemoved() && AllyDirector.presentOf(p.getUUID()) == null, "/kill 之后清掉在场");
            h.assertTrue(PlayerLedger.getAllies(p).size() == 1, "/kill 不动名册");
            h.assertTrue(AllyDirector.summon(p, true, false).isEmpty(), "清掉之后还能再来");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = LEAVE + "_far", timeoutTicks = 80)
    public static void ownerFarAway(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        KuvaMasterEntity a = summoned(h, p, "Tor Vash");
        Vec3 at = p.position();
        p.teleportTo(h.getLevel(), at.x + 60.0D, at.y + 30.0D, at.z, 0.0F, 0.0F);
        AllyDirector.patrol(h.getLevel().getServer(), h.getLevel().getServer().overworld().getGameTime());
        h.assertTrue(a.isDeparting(), "主人离开 48 格就退去");
        h.succeed();
    }

    // ==================== 触发 ====================

    @GameTest(template = "empty", batch = TRIGGER, timeoutTicks = 200)
    public static void triggerArmsRetriesAndCooldown(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        BlockPos c = KuvaTestKit.center(h);
        KuvaTestKit.openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 3, c.getZ() + 11);
        giveRoster(p, entry(2, "Bel Corin", 5L));
        Husk z = husk(h, p.position().add(0.0D, 0.0D, -2.0D), false);
        KuvaTestKit.override(ModConfig.KUVA_LICH.allyAssistChance, 0.0D);
        h.runAfterDelay(T0, () -> {
            // 血量还在阈值以上：不掷
            p.hurt(p.damageSources().mobAttack(z), 2.0F);
        });
        h.runAfterDelay(T0 + 1, () -> {
            h.assertTrue(AllyDirector.isArmed(p.getUUID()), "没跌破阈值不该掷");
            p.setHealth(p.getMaxHealth() * 0.35F);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(z), 4.0F);
        });
        h.runAfterDelay(T0 + 2, () -> {
            h.assertTrue(!AllyDirector.isArmed(p.getUUID()), "跌破阈值应当掷过一次");
            h.assertTrue(AllyDirector.presentOf(p.getUUID()) == null, "概率 0 不该来");
            // 概率调成 1，但还在重试间隔里，再挨打也不掷
            KuvaTestKit.override(ModConfig.KUVA_LICH.allyAssistChance, 1.0D);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(z), 1.0F);
        });
        h.runAfterDelay(T0 + 3, () -> {
            h.assertTrue(AllyDirector.presentOf(p.getUUID()) == null, "重试间隔内不再掷");
            // 玩家打的不算
            AllyDirector.resetTrigger(p.getUUID());
            ServerPlayer other = allyPlayer(h);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().playerAttack(other), 1.0F);
        });
        h.runAfterDelay(T0 + 4, () -> {
            h.assertTrue(AllyDirector.presentOf(p.getUUID()) == null && AllyDirector.isArmed(p.getUUID()),
                    "被玩家打见底不该叫盟友");
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(z), 1.0F);
        });
        h.runAfterDelay(T0 + 5, () -> {
            KuvaMasterEntity ally = AllyDirector.presentOf(p.getUUID());
            h.assertTrue(ally != null, "概率 1、上了膛、跌破阈值应当来");
            long now = h.getLevel().getServer().overworld().getGameTime();
            long until = PlayerLedger.getAllyCooldownUntil(p);
            long expect = ModConfig.KUVA_LICH.allyCooldownMinutes.get() * 1200L;
            h.assertTrue(until > now && until <= now + expect, "冷却截止应当写进玩家数据，实际差 " + (until - now));
            // 冷却在 PlayerPersisted 里：搬到另一个玩家身上（相当于读回存档）照样在
            ServerPlayer copy = allyPlayer(h);
            copy.getPersistentData().put(Player.PERSISTED_NBT_TAG,
                    p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).copy());
            h.assertTrue(PlayerLedger.getAllyCooldownUntil(copy) == until, "冷却随玩家数据保存");
            ally.discard();
            AllyDirector.resetTrigger(p.getUUID());
            p.setHealth(3.0F);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(z), 1.0F);
        });
        h.runAfterDelay(T0 + 6, () -> {
            h.assertTrue(AllyDirector.presentOf(p.getUUID()) == null, "冷却期间不再来（内存里的状态清掉也一样）");
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = CAP, timeoutTicks = 80)
    public static void serverCap(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        ServerPlayer q = allyPlayer(h);
        // 上一个 batch 留下、还没移除的不在退去中的盟友也算在内
        KuvaTestKit.override(ModConfig.KUVA_LICH.allyMaxActive, AllyDirector.activeCount() + 1);
        summoned(h, p, "Ari Sulm");
        giveRoster(q, entry(3, "Yen Draka", 77L));
        h.assertTrue("server_cap".equals(AllyDirector.summon(q, true, false)), "全服上限到了不再来");
        h.succeed();
    }

    // ==================== 名册 ====================

    @GameTest(template = "empty", batch = ROSTER, timeoutTicks = 80)
    public static void evictedActiveLeaves(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        KuvaMasterEntity a = summoned(h, p, "Sol Ferro");
        PlayerLedger.setActiveAlly(p, 7);
        KuvaTestKit.override(ModConfig.KUVA_LICH.allyRosterSize, 1);
        String released = AllyRoster.add(p, entry(8, "Nox Imra", 42L));
        h.assertTrue("Sol Ferro".equals(released), "上限 1 时被挤掉的是出战的那只，实际：" + released);
        h.assertTrue(a.isDeparting(), "被挤出名册的那只正在场上就马上退去");
        h.assertTrue(PlayerLedger.getActiveAlly(p) == -1, "出战指向被删的那只时改回默认");
        CompoundTag next = AllyDirector.entryToSummon(p);
        h.assertTrue(next != null && next.getInt(AllyRoster.GENERATION) == 8, "之后出战的是最新那只");
        // 上限调小后读档不截
        giveRoster(p, entry(1, "A", 1L), entry(2, "B", 2L), entry(3, "C", 3L));
        h.assertTrue(PlayerLedger.getAllies(p).size() == 3, "上限调小后读出来的名册不截");
        h.assertTrue(AllyRoster.release(p, 2) && PlayerLedger.getAllies(p).size() == 2, "遣散删掉这一条");
        h.assertTrue(!AllyRoster.release(p, 2), "再遣散一次没有效果");
        h.succeed();
    }

    // ==================== 和现役玄骸、奴仆同场 ====================

    @GameTest(template = "empty", batch = HUNT, timeoutTicks = 120)
    public static void besideOwnedLichAndThrall(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        RequiemCard card = p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElseThrow(IllegalStateException::new);
        MercyCoreTests.bornByProgress(p, card);
        KuvaMasterEntity lich = MercyCoreTests.summonFront(h, p);
        KuvaTestKit.override(ModConfig.KUVA_LICH.allyEnabled, true);
        giveRoster(p, entry(9, "Ilk Dovan", 1234L));
        h.assertTrue(AllyDirector.summon(p, true, false).isEmpty(), "有现役玄骸时盟友照样能来");
        KuvaMasterEntity ally = AllyDirector.presentOf(p.getUUID());
        h.assertTrue(ally != null && ally != lich, "盟友是另一只实体");
        h.assertTrue(LichDirector.getLich(p).orElse(null) == lich, "导演的 Hunt 还是那只现役玄骸");
        h.assertTrue(AllyGoals.isFoe(ally, lich), "主人自己的现役玄骸照打");
        h.assertTrue(!AllyGoals.isFoe(ally, ally), "不打自己");

        // 别人的现役玄骸不碰
        ServerPlayer other = allyPlayer(h);
        KuvaMasterEntity foreign = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(foreign != null, "应当能造出玄骸");
        RequiemCard oc = other.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElseThrow(IllegalStateException::new);
        MercyCoreTests.bornByProgress(other, oc);
        foreign.bindOwner(other, oc);
        h.assertTrue(!AllyGoals.isFoe(ally, foreign), "别人的专属玄骸不是它的敌人");

        // 倒地的不打，主人处决时手上的目标放下
        lich.forceDowned(p);
        h.assertTrue(!AllyGoals.isFoe(ally, lich), "倒地 / 处决中的赤毒实体不当目标");
        ally.setTarget(lich);
        AllyGoals.standDownIfNeeded(ally);
        h.assertTrue(ally.getTarget() == null, "目标进了倒地就放下");

        // 盟友打倒的奴仆，资格人算给盟友的主人
        Vec3 at = p.position().add(2.0D, 0.0D, 2.0D);
        KuvaSlaveEntity thrall = KuvaLichEntities.KUVA_SLAVE.get().create(h.getLevel());
        h.assertTrue(thrall != null, "应当能造出奴仆");
        thrall.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        h.assertTrue(h.getLevel().addFreshEntity(thrall), "奴仆应当加得进世界");
        thrall.setHealth(1.0F);
        thrall.hurt(ally.damageSources().mobAttack(ally), 20.0F);
        h.assertTrue(thrall.getMercyState() == MercyState.DOWNED, "奴仆应当倒地");
        h.assertTrue(p.getUUID().equals(thrall.getMercyQualified()), "资格人应当是盟友的主人");

        ally.discard();
        h.assertTrue(LichDirector.getLich(p).orElse(null) == lich && lich.isAlive(), "盟友移除不碰现役玄骸");
        h.succeed();
    }

    // ==================== 收尾 ====================

    /** 怜悯之击关着：盟友直接打死的幼体、奴仆算给主人，和玩家亲手打死一样，只算一份 */
    @GameTest(template = "empty", batch = CREDIT, timeoutTicks = 120)
    public static void mercyOffKillsCreditOwner(GameTestHelper h) {
        ServerPlayer p = allyPlayer(h);
        KuvaTestKit.override(ModConfig.KUVA_LICH.mercyEnabled, false);
        KuvaTestKit.override(ModConfig.KUVA_LICH.slaveKuvaDropChance, 100);
        RequiemCard card = p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElseThrow(IllegalStateException::new);
        h.assertTrue(!card.hasLich(), "前提：主人还没有玄骸");
        KuvaMasterEntity ally = summoned(h, p, "Hask Oron");
        h.runAfterDelay(KuvaMasterEntity.APPEAR_GRACE_TICKS + 2, () -> {
            Vec3 at = p.position().add(2.0D, 0.0D, 2.0D);
            KuvaLarvlingEntity larvling = KuvaLichEntities.KUVA_LARVLING.get().create(h.getLevel());
            h.assertTrue(larvling != null, "应当能造出幼体");
            larvling.setup(null, 0x5EED_0042L);
            larvling.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            larvling.setNoAi(true);
            h.assertTrue(h.getLevel().addFreshEntity(larvling), "幼体应当加得进世界");
            larvling.setHealth(1.0F);
            larvling.hurt(ally.damageSources().mobAttack(ally), 20.0F);
            h.assertTrue(!larvling.isAlive() && larvling.getMercyState() == MercyState.NONE, "关着时幼体应当直接死");
            h.assertTrue(card.hasLich(), "盟友打死的幼体应当给主人诞生玄骸");

            card.setDecryptionProgress(0);
            int stage = card.getUnlockedCardStatus();
            AABB area = new AABB(p.blockPosition()).inflate(12.0D);
            for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, area)) {
                e.discard();
            }
            KuvaSlaveEntity thrall = KuvaLichEntities.KUVA_SLAVE.get().create(h.getLevel());
            h.assertTrue(thrall != null, "应当能造出奴仆");
            thrall.moveTo(at.x, at.y, at.z - 4.0D, 0.0F, 0.0F);
            thrall.setNoAi(true);
            h.assertTrue(h.getLevel().addFreshEntity(thrall), "奴仆应当加得进世界");
            thrall.setHealth(1.0F);
            thrall.hurt(ally.damageSources().mobAttack(ally), 20.0F);
            h.assertTrue(!thrall.isAlive() && thrall.getMercyState() == MercyState.NONE, "关着时奴仆应当直接死");
            int got = card.getDecryptionProgress();
            int min = ModConfig.KUVA_LICH.minDecryptionProgress.get();
            int max = ModConfig.KUVA_LICH.maxDecryptionProgress.get();
            h.assertTrue(card.getUnlockedCardStatus() == stage, "这一下不该跨阶段（测试前提）");
            h.assertTrue(got >= min && got <= max, "主人分到一份进度，应当在 [" + min + ", " + max + "]，实际 " + got);
            long kuva = h.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                    e -> e.getItem().is(KuvaLichItems.KUVA.get())).size();
            h.assertTrue(kuva == 1, "赤毒只掉一份：" + kuva);
            h.succeed();
        });
    }

    @AfterBatch(batch = SPAWN)
    public static void afterSpawn(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = DAMAGE)
    public static void afterDamage(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = AOE)
    public static void afterAoe(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = LEAVE)
    public static void afterLeave(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = LEAVE + "_kill")
    public static void afterLeaveKill(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = LEAVE + "_far")
    public static void afterLeaveFar(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = TRIGGER)
    public static void afterTrigger(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = CAP)
    public static void afterCap(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = ROSTER)
    public static void afterRoster(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = HUNT)
    public static void afterHunt(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = CREDIT)
    public static void afterCredit(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }
}
