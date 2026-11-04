package pers.roinflam.kuvalich.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.nemesis.EncounterEnd;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.LichNames;
import pers.roinflam.kuvalich.nemesis.LichSpawner;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.SummonMode;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * 专属玄骸服务端核心：诞生、一人一只、不自然生成、怒气到场、冷却、各种离场、不存盘、老存档补生、
 * 处决记录、一轮结束、读档清老玄骸、克隆、卡住换位
 *
 * <p>不放玄骸出来的测试放一个 batch（{@link #CORE}）；会放出玄骸的（它会追人、会走动）、
 * 改配置或难度的，各自单独一个 batch。</p>
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NemesisCoreTests {

    private static final String CORE = "kl_nemesis_core";
    private static final String ISO_ONE = "kl_nemesis_iso_onePerPlayer";
    private static final String ISO_SPAWNER = "kl_nemesis_iso_noNaturalOrSpawner";
    private static final String ISO_ARRIVAL = "kl_nemesis_iso_rageTriggersArrival";
    private static final String ISO_COOLDOWN = "kl_nemesis_iso_cooldownFloor";
    private static final String ISO_FAR = "kl_nemesis_iso_departWhenFar";
    private static final String ISO_DIM = "kl_nemesis_iso_departOnDimension";
    private static final String ISO_LOGOUT = "kl_nemesis_iso_departOnLogout";
    private static final String ISO_DEATH = "kl_nemesis_iso_departOnOwnerDeath";
    private static final String ISO_IDLE = "kl_nemesis_iso_departWhenIdle";
    private static final String ISO_IDLE_ENV = "kl_nemesis_iso_idleIgnoresEnvironment";
    private static final String ISO_ADMIN = "kl_nemesis_iso_departAdmin";
    private static final String ISO_STUCK = "kl_nemesis_iso_stuckRelocates";

    /** 改难度的 batch 收尾时改回去 */
    private static Difficulty savedDifficulty;

    private NemesisCoreTests() {
    }

    // ==================== #1 诞生 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void birthBasics(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        h.assertTrue(card.hasLich(), "诞生后应当有玄骸");
        h.assertTrue(LichNames.isAllowed(card.getLichName()), "名字应当合规：" + card.getLichName());
        h.assertTrue(card.getLichGeneration() == 1, "第一只的代数应当是 1");
        h.assertTrue(LichDirector.stateOf(p) == LichState.STALKING, "刚诞生应当在跟踪：" + LichDirector.stateOf(p));
        h.assertTrue(Math.abs(card.getLichRage() - ModConfig.KUVA_LICH.lichBirthRage.get()) < 1.0E-4D,
                "刚诞生的怒气应当是 lichBirthRage");
        h.assertTrue(!card.getLichBirthBiome().isEmpty() && !card.getLichBirthDim().isEmpty(), "出生地应当记下");
        h.assertTrue(card.getLichWeapon() >= 0, "应当定下手里的武器");
        h.succeed();
    }

    /** 玄骸由幼体诞生，三条线索都在诞生之后揭：第一条也要加怒气 */
    @GameTest(template = "empty", batch = CORE)
    public static void firstClueAddsRage(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        h.assertTrue(LichDirector.birthNow(p), "应当能直接诞生");
        h.assertTrue(card.getUnlockedCardStatus() == 0, "诞生时还没揭线索（测试前提）");
        float rage = card.getLichRage();
        card.setDecryptionProgress(Math.max(0, ModConfig.KUVA_LICH.firstStage.get() - 1));
        card.addPotion(1);
        h.assertTrue(card.getUnlockedCardStatus() == 1, "这一点进度应当揭开第一条");
        LichDirector.onDecryptionProgress(p, card, 1, 0);
        double expect = ModConfig.KUVA_LICH.lichRagePerClueRevealed.get()
                + ModConfig.KUVA_LICH.lichRagePerProgressPoint.get();
        double delta = card.getLichRage() - rage;
        h.assertTrue(Math.abs(delta - expect) < 1.0E-3D,
                "第一条线索应当加 " + expect + " 怒气（" + rage + " → " + card.getLichRage() + "）");
        h.succeed();
    }

    // ==================== #2 同一个玩家同时最多一只 ====================

    @GameTest(template = "empty", batch = ISO_ONE, timeoutTicks = 100)
    public static void onePerPlayer(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        bornByProgress(p, card(p));
        h.assertTrue(LichDirector.summonNow(p, SummonMode.FRONT), "第一次立即召唤应当成功");
        h.assertTrue(!LichDirector.summonNow(p, SummonMode.FRONT), "已经在场时再召应当失败");
        h.assertTrue(!LichDirector.summonNow(p, SummonMode.HUNT), "已经在场时按狩猎召也应当失败");
        List<KuvaMasterEntity> mine = ownedBy(h.getLevel(), p);
        h.assertTrue(mine.size() == 1, "世界里这个主人的玄骸应当只有 1 只，实际 " + mine.size());
        KuvaMasterEntity lich = mine.get(0);
        h.assertTrue(lich.isOwner(p) && !lich.isWild(), "召出来的应当是他的、不是野生的");
        h.assertTrue(LichDirector.getLich(p).orElse(null) == lich, "导演记的应当就是这只");
        LichDirector.endEncounter(p, lich, EncounterEnd.ADMIN, false);
        h.assertTrue(lich.isRemoved(), "不放演出的结束应当当场移除");
        h.succeed();
    }

    @AfterBatch(batch = ISO_ONE)
    public static void afterOne(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #3 自然生成、刷怪笼都出不来 ====================

    @GameTest(template = "empty", batch = ISO_SPAWNER, timeoutTicks = 500)
    public static void noNaturalOrSpawner(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos center = h.absolutePos(KuvaTestKit.center(h));
        EntityType<KuvaMasterEntity> type = KuvaLichEntities.KUVA_MASTER.get();

        // ① 刷怪表里没有玄骸（老配置里 kuvaLichSpawnWeight 还是 15 也一样）
        List<MobSpawnSettings.SpawnerData> monsters = level.getBiome(center).value().getMobSettings()
                .getMobs(MobCategory.MONSTER).unwrap();
        h.assertTrue(monsters.stream().noneMatch(d -> d.type == type), "群系刷怪表里不应当有玄骸");

        // ② 落点规则：自然生成、刷怪笼、区块生成一律 false
        for (MobSpawnType t : new MobSpawnType[]{MobSpawnType.NATURAL, MobSpawnType.SPAWNER,
                MobSpawnType.CHUNK_GENERATION}) {
            h.assertTrue(!SpawnPlacements.checkSpawnRules(type, level, t, center, level.getRandom()),
                    "落点规则应当拒绝 " + t);
        }

        // ④ finalizeSpawn(SPAWNER) 之后直接加进世界：拦下
        KuvaMasterEntity direct = type.create(level);
        h.assertTrue(direct != null, "应当能造出实体");
        direct.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 0.0F, 0.0F);
        direct.finalizeSpawn(level, level.getCurrentDifficultyAt(center), MobSpawnType.SPAWNER, null, null);
        h.assertTrue(!level.addFreshEntity(direct), "刷怪笼来源的玄骸不应当加得进世界");

        // ③ 两个刷玄骸的刷怪笼：一个普通、一个带自定义生成条件（亮度 0~15），旁边站一个玩家，跑 400 tick
        ServerPlayer p = KuvaTestKit.player(h);
        placeSpawner(h, new BlockPos(1, 1, 1), false);
        placeSpawner(h, new BlockPos(3, 1, 3), true);
        h.failIfEver(() -> {
            if (!liches(level, center, 24.0D).isEmpty()) {
                h.fail("刷怪笼刷出了玄骸");
            }
        });
        h.runAfterDelay(400, () -> {
            h.assertTrue(p.isAlive(), "测试玩家应当一直在");
            h.succeed();
        });
    }

    private static void placeSpawner(GameTestHelper h, BlockPos rel, boolean customRules) {
        h.setBlock(rel, Blocks.SPAWNER);
        BlockPos abs = h.absolutePos(rel);
        if (!(h.getLevel().getBlockEntity(abs) instanceof SpawnerBlockEntity spawner)) {
            h.fail("刷怪笼方块实体没放上");
            return;
        }
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "kuvalich:kuva_master");
        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        if (customRules) {
            CompoundTag rules = new CompoundTag();
            rules.put("block_light_limit", range(0, 15));
            rules.put("sky_light_limit", range(0, 15));
            spawnData.put("custom_spawn_rules", rules);
        }
        CompoundTag tag = new CompoundTag();
        tag.put("SpawnData", spawnData);
        tag.putShort("Delay", (short) 0);
        tag.putShort("MinSpawnDelay", (short) 20);
        tag.putShort("MaxSpawnDelay", (short) 40);
        tag.putShort("SpawnCount", (short) 4);
        tag.putShort("MaxNearbyEntities", (short) 6);
        tag.putShort("RequiredPlayerRange", (short) 32);
        tag.putShort("SpawnRange", (short) 3);
        spawner.getSpawner().load(h.getLevel(), abs, tag);
        spawner.setChanged();
    }

    private static CompoundTag range(int min, int max) {
        CompoundTag t = new CompoundTag();
        t.putInt("min_inclusive", min);
        t.putInt("max_inclusive", max);
        return t;
    }

    @AfterBatch(batch = ISO_SPAWNER)
    public static void afterSpawner(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #5 怒气满了就来 ====================

    @BeforeBatch(batch = ISO_ARRIVAL)
    public static void beforeArrival(ServerLevel level) {
        rememberDifficulty(level.getServer());
    }

    @GameTest(template = "empty", batch = ISO_ARRIVAL, timeoutTicks = 600)
    public static void rageTriggersArrival(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichArrivalDelayMinSeconds, 1);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichArrivalDelayMaxSeconds, 1);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichCommLeadTicks, 20);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichSpawnMinDistance, 10);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichSpawnMaxDistance, 12);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichLoginGraceSeconds, 0);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        openArena(h, c.getX() - 13, c.getX() + 13, c.getZ() - 13, c.getZ() + 13);
        bornByProgress(p, card);
        card.setLichRage(0.999F);
        boolean[] sawIncoming = new boolean[1];
        h.onEachTick(() -> {
            LichState s = LichDirector.stateOf(p);
            if (s == LichState.INCOMING || s == LichState.ARRIVING) {
                sawIncoming[0] = true;
            }
        });
        h.succeedWhen(() -> {
            Optional<KuvaMasterEntity> lich = LichDirector.getLich(p);
            h.assertTrue(lich.isPresent(), "玄骸还没到");
            KuvaMasterEntity e = lich.get();
            double dx = e.getX() - p.getX();
            double dz = e.getZ() - p.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            h.assertTrue(d >= 9.0D && d <= 13.0D, "现身点离主人应当在 10~12 格，实际 " + d);
            h.assertTrue(e.isOwner(p) && card.getLichName().equals(e.getLichName())
                    && p.getGameProfile().getName().equals(e.getOwnerName()), "名字 / 主人同步数据应当对");
            h.assertTrue(e.getLichRank() == card.getLichRank(), "等级同步数据应当对");
            h.assertTrue(sawIncoming[0], "到场前应当经过「将到」");
            h.assertTrue(card.isLichEncounterOpen() && card.getLichEncounters() == 1, "遭遇应当记上");
            LichDirector.HuntView view = LichDirector.view(p);
            h.assertTrue(view != null && view.bossBarShown() && view.bossBarPlayers().contains(p),
                    "主人应当在 Boss 条上");
        });
    }

    @AfterBatch(batch = ISO_ARRIVAL)
    public static void afterArrival(ServerLevel level) {
        restoreDifficulty(level.getServer());
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #6 冷却下限 ====================

    @GameTest(template = "empty", batch = ISO_COOLDOWN, timeoutTicks = 400)
    public static void cooldownFloor(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichCooldownMinutes, 10.0D / 60.0D);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        LichDirector.endEncounter(p, lich, EncounterEnd.IDLE, false);
        h.assertTrue(card.getLichRage() == 0.0F && !card.isLichEncounterOpen(), "遭遇结束应当清怒气和遭遇记号");
        long cooldownEnd = card.getLichCooldownUntil();
        h.assertTrue(cooldownEnd > overworldTime(p), "应当进了冷却");
        card.setLichRage(1.0F);
        h.startSequence()
                .thenExecuteAfter(100, () -> h.assertTrue(LichDirector.stateOf(p) == LichState.COOLDOWN,
                        "冷却 10 秒内应当还在冷却：" + LichDirector.stateOf(p)))
                .thenWaitUntil(() -> h.assertTrue(overworldTime(p) > cooldownEnd + 20, "等冷却过去"))
                .thenExecute(() -> {
                    LichDirector.tickHunt(p, overworldTime(p));
                    LichState s = LichDirector.stateOf(p);
                    h.assertTrue(s == LichState.INCOMING || s == LichState.ARRIVING || s == LichState.PRESENT,
                            "冷却过了、怒气满应当往下走：" + s);
                })
                .thenSucceed();
    }

    @AfterBatch(batch = ISO_COOLDOWN)
    public static void afterCooldown(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== #7a~e 各种离场 ====================

    @GameTest(template = "empty", batch = ISO_FAR, timeoutTicks = 200)
    public static void departWhenFar(GameTestHelper h) {
        // 拴绳调短到 32 格、主人挪开 60 格：留足余量，它往回追的那几步不会把距离拉回绳内
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichLeashDistance, 32);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        p.teleportTo(h.getLevel(), p.getX() + 60.0D, p.getY(), p.getZ(), 0.0F, 0.0F);
        expectDeparted(h, p, card, lich, 80, EncounterEnd.OWNER_FAR, true);
    }

    @AfterBatch(batch = ISO_FAR)
    public static void afterFar(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = ISO_DIM, timeoutTicks = 200)
    public static void departOnDimension(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "测试服应当有下界");
        p.teleportTo(nether, 0.5D, 100.0D, 0.5D, 0.0F, 0.0F);
        expectDeparted(h, p, card, lich, 60, EncounterEnd.OWNER_DIMENSION, true);
    }

    @AfterBatch(batch = ISO_DIM)
    public static void afterDim(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = ISO_LOGOUT, timeoutTicks = 200)
    public static void departOnLogout(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeedWhen(() -> {
            h.assertTrue(lich.isRemoved(), "主人下线后玄骸应当离场");
            // 下线时不碰卡：「遭遇进行中」还留着，下次登录按中断补结算
            h.assertTrue(card.isLichEncounterOpen(), "下线时不应当动卡");
            h.assertTrue(lich.getEncounterEnd() == EncounterEnd.OWNER_LOGOUT, "结束原因应当是主人下线：" + lich.getEncounterEnd());
            // 移出玩家列表时能力已经失效；真正重新登录是新的玩家对象读存档，这里恢复能力来模拟
            p.reviveCaps();
            LichDirector.onLogin(p);
            h.assertTrue(card.getLichRage() == 0.0F && !card.isLichEncounterOpen()
                    && card.getLichCooldownUntil() > overworldTime(p), "重新登录应当按中断补结算：怒气 0、进冷却");
        });
    }

    @AfterBatch(batch = ISO_LOGOUT)
    public static void afterLogout(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = ISO_DEATH, timeoutTicks = 200)
    public static void departOnOwnerDeath(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        // 被它杀的（刚进服的 60 tick 无敌挡掉了正常的伤害，这里直接走死亡流程）
        p.setHealth(0.0F);
        p.die(lich.damageSources().mobAttack(lich));
        h.assertTrue(!p.isAlive(), "测试玩家应当死了");
        // 主人死了：+3 秒才开始离场演出，再 8 tick 移除
        expectDeparted(h, p, card, lich, 90, EncounterEnd.OWNER_DIED, true);
    }

    @AfterBatch(batch = ISO_DEATH)
    public static void afterDeath(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @GameTest(template = "empty", batch = ISO_IDLE, timeoutTicks = 300)
    public static void departWhenIdle(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichIdleDepartSeconds, 5);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        // 创造模式：它不会盯上主人，也就没有伤害往来
        p.setGameMode(GameType.CREATIVE);
        h.runAfterDelay(60, () -> h.assertTrue(!lich.isRemoved(), "5 秒还没到时不应当离场"));
        expectDeparted(h, p, card, lich, 200, EncounterEnd.IDLE, true);
    }

    @AfterBatch(batch = ISO_IDLE)
    public static void afterIdle(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    /** 没有攻击者的伤害（环境伤害）不算交手：一直挨仙人掌扎，照样按晾着离场 */
    @GameTest(template = "empty", batch = ISO_IDLE_ENV, timeoutTicks = 300)
    public static void idleIgnoresEnvironmentDamage(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichIdleDepartSeconds, 5);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);
        int[] hits = new int[1];
        h.onEachTick(() -> {
            if (h.getTick() % 10 == 0 && !lich.isRemoved() && lich.isAlive() && !lich.isDeparting()) {
                lich.invulnerableTime = 0;
                if (lich.hurt(lich.damageSources().cactus(), 1.0F)) {
                    hits[0]++;
                }
                lich.setHealth(lich.getMaxHealth());
            }
        });
        h.runAfterDelay(60, () -> h.assertTrue(!lich.isRemoved(), "5 秒还没到时不应当离场"));
        expectDeparted(h, p, card, lich, 200, EncounterEnd.IDLE, true,
                () -> hits[0] >= 3 ? null : "环境伤害应当真打进去了（只打进 " + hits[0] + " 次）");
    }

    @AfterBatch(batch = ISO_IDLE_ENV)
    public static void afterIdleEnv(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    /** 管理命令让它走：离场，但不进冷却 */
    @GameTest(template = "empty", batch = ISO_ADMIN, timeoutTicks = 100)
    public static void departAdmin(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        LichDirector.endEncounter(p, lich, EncounterEnd.ADMIN, true);
        h.succeedWhen(() -> {
            h.assertTrue(lich.isRemoved(), "应当离场");
            h.assertTrue(card.getLichRage() == 0.0F, "怒气应当清零");
            h.assertTrue(card.getLichCooldownUntil() <= overworldTime(p), "管理命令让它走不应当进冷却");
        });
    }

    @AfterBatch(batch = ISO_ADMIN)
    public static void afterAdmin(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    /** 在 {@code withinTicks} 内实体被移除、怒气 0、冷却已设 */
    private static void expectDeparted(GameTestHelper h, ServerPlayer p, RequiemCard card, KuvaMasterEntity lich,
                                       int withinTicks, EncounterEnd reason, boolean cooldown) {
        expectDeparted(h, p, card, lich, withinTicks, reason, cooldown, null);
    }

    /** 同上，另加一项检查：{@code extra} 返回不为 null 的说明就算没过 */
    private static void expectDeparted(GameTestHelper h, ServerPlayer p, RequiemCard card, KuvaMasterEntity lich,
                                       int withinTicks, EncounterEnd reason, boolean cooldown,
                                       @Nullable Supplier<String> extra) {
        long start = h.getTick();
        h.succeedWhen(() -> {
            String problem = null;
            if (!lich.isRemoved()) {
                problem = "玄骸还没离场";
            } else if (lich.getEncounterEnd() != reason) {
                problem = "结束原因应当是 " + reason;
            } else if (card.getLichRage() != 0.0F) {
                problem = "怒气应当清零";
            } else if (card.isLichEncounterOpen()) {
                problem = "遭遇记号应当清掉";
            } else if (cooldown && card.getLichCooldownUntil() <= overworldTime(p)) {
                problem = "应当进了冷却";
            } else if (LichDirector.getLich(p).isPresent()) {
                problem = "导演那边的引用应当清掉";
            } else if (extra != null) {
                problem = extra.get();
            }
            if (problem == null) {
                return;
            }
            String detail = problem + "（原因 " + lich.getEncounterEnd() + "，离场中 " + lich.isDeparting()
                    + "，removed " + lich.getRemovalReason() + "，状态 " + LichDirector.stateOf(p)
                    + "，距离 " + (int) Math.sqrt(p.distanceToSqr(lich)) + "，怒气 " + card.getLichRage() + "）";
            if (h.getTick() - start > withinTicks) {
                h.fail(withinTicks + " tick 内没有按预期离场：" + detail);
            }
            h.fail(detail);
        });
    }

    // ==================== #8 有主的不存盘 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void notSaved(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        ServerLevel level = h.getLevel();
        KuvaMasterEntity owned = KuvaLichEntities.KUVA_MASTER.get().create(level);
        KuvaMasterEntity wild = KuvaLichEntities.KUVA_MASTER.get().create(level);
        h.assertTrue(owned != null && wild != null, "应当能造出实体");
        owned.bindOwner(p, card);
        wild.markWild();
        h.assertTrue(!owned.shouldBeSaved(), "有主的不应当存盘");
        h.assertTrue(wild.shouldBeSaved(), "无主的应当存盘");
        CompoundTag tag = wild.saveWithoutId(new CompoundTag());
        h.assertTrue(tag.getBoolean("KuvaWild"), "无主的存档里应当有 KuvaWild");
        h.assertTrue(!owned.canChangeDimensions(), "有主的不应当走传送门");
        h.assertTrue(card.getLichName().equals(owned.getName().getString()), "有主的名字应当是玄骸名");
        h.succeed();
    }

    // ==================== #11 老存档补生 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void migrateOldSave(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);

        card.deserializeNBT(oldSave(2));
        h.assertTrue(card.getLichDataVersion() == RequiemCard.LICH_DATA_VERSION && !card.hasLich(),
                "读老存档：版本应当补上、先不生成玄骸");
        LichDirector.onLogin(p);
        h.assertTrue(card.hasLich(), "阶段 2 的老存档登录时应当补生一只");
        h.assertTrue(Math.abs(card.getLichRage() - ModConfig.KUVA_LICH.lichMigrationRage.get()) < 1.0E-4D,
                "补生的怒气应当是 lichMigrationRage");
        h.assertTrue(card.getUnlockedCardStatus() == 2, "谜语阶段不应当变");

        card.deserializeNBT(oldSave(0));
        LichDirector.onLogin(p);
        h.assertTrue(!card.hasLich(), "阶段 0 的老存档不应当补生");
        h.succeed();
    }

    /** 一份没有任何玄骸键的老存档，谜语阶段为 {@code stage} */
    private static CompoundTag oldSave(int stage) {
        RequiemCard old = new RequiemCard();
        old.setUnlockedCardStatus(stage);
        CompoundTag tag = old.serializeNBT();
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("lich")) {
                tag.remove(key);
            }
        }
        return tag;
    }

    // ==================== #13 处决记录 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void attemptRecord(GameTestHelper h) {
        RequiemCard card = new RequiemCard();
        for (int i = 0; i < 7; i++) {
            card.recordAttempt(RequiemCard.packAttempt(new int[]{i % 8, 1, 2}, new byte[]{1, 2, 0}, i % 2 == 0));
        }
        int[] attempts = card.getAttempts();
        h.assertTrue(attempts.length == RequiemCard.MAX_ATTEMPTS, "处决记录应当只留 5 条");
        h.assertTrue(RequiemCard.attemptCardId(attempts[0], 0) == 6, "最新的应当在最前");
        int packed = RequiemCard.packAttempt(new int[]{3, 7, -1}, new byte[]{1, 1, 2}, true);
        h.assertTrue(RequiemCard.attemptCardId(packed, 0) == 3 && RequiemCard.attemptCardId(packed, 1) == 7
                && RequiemCard.attemptCardId(packed, 2) == RequiemCard.ATTEMPT_NO_CARD, "卡 id 往返");
        h.assertTrue(RequiemCard.attemptResult(packed, 0) == RequiemCard.ATTEMPT_RIGHT
                && RequiemCard.attemptResult(packed, 1) == RequiemCard.ATTEMPT_RIGHT
                && RequiemCard.attemptResult(packed, 2) == RequiemCard.ATTEMPT_WRONG, "结果往返");
        h.assertTrue(RequiemCard.attemptIsMercy(packed)
                && !RequiemCard.attemptIsMercy(RequiemCard.packAttempt(new int[0], new byte[0], false)), "怜悯之击标志往返");
        attempts[0] = -1;
        h.assertTrue(card.getAttempts()[0] != -1, "取到的应当是副本");
        h.succeed();
    }

    // ==================== #14 一轮结束 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void roundEnd(GameTestHelper h) {
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        String first = card.getLichName();
        card.recordAttempt(RequiemCard.packAttempt(new int[]{1, 2, 3}, new byte[]{2, 0, 0}, true));
        card.setKuvaLevel(2);
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(h.getLevel());
        h.assertTrue(lich != null, "应当能造出实体");
        lich.bindOwner(p, card);

        LichDirector.onVanquished(p, lich);
        card.reset();
        h.assertTrue(!card.hasLich(), "终结后应当没有玄骸");
        h.assertTrue(card.getAttempts().length == 0, "处决记录应当清空");
        RequiemCard.HistoryEntry last = card.getLastHistory();
        h.assertTrue(last != null && first.equals(last.name()) && last.rank() == 3
                && last.fate() == RequiemCard.FATE_VANQUISHED, "名字、等级、结局应当进历史");
        h.assertTrue(LichDirector.stateOf(p) == LichState.UNBORN, "终结后状态应当是没有玄骸");

        bornByProgress(p, card);
        h.assertTrue(card.hasLich() && !first.equalsIgnoreCase(card.getLichName()),
                "再揭开第一条应当诞生一只名字不同的新玄骸");
        h.assertTrue(card.getLichGeneration() == 2, "第二只的代数应当是 2");
        h.succeed();
    }

    // ==================== #15 读档时清掉以前自然生成的 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void purgeLegacy(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichPurgeLegacy, true);
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(2.5D, 0.0D, 2.5D));

        KuvaMasterEntity legacy = reloaded(level, at, false);
        level.addLegacyChunkEntities(Stream.of(legacy));
        h.assertTrue(!legacy.hasWildTag(), "老玄骸读回来不应当带 KuvaWild");
        h.assertTrue(level.getEntity(legacy.getUUID()) == null,
                "没有 KuvaWild 的老玄骸读档时应当被拦下（added=" + legacy.isAddedToWorld()
                        + "，removed=" + legacy.isRemoved() + "，wild=" + legacy.isWild() + "）");

        KuvaMasterEntity wild = reloaded(level, at, true);
        level.addLegacyChunkEntities(Stream.of(wild));
        h.assertTrue(level.getEntity(wild.getUUID()) == wild && wild.isWild(), "带 KuvaWild 的应当留下、还是野生的");
        wild.discard();
        h.succeed();
    }

    /** 造一只玄骸、存成 NBT 再读回来（模拟区块读档时的实体） */
    private static KuvaMasterEntity reloaded(ServerLevel level, Vec3 at, boolean wild) {
        KuvaMasterEntity e = KuvaLichEntities.KUVA_MASTER.get().create(level);
        e.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        if (wild) {
            e.markWild();
        }
        CompoundTag tag = new CompoundTag();
        e.save(tag);
        Entity loaded = EntityType.loadEntityRecursive(tag, level, x -> x);
        return (KuvaMasterEntity) loaded;
    }

    // ==================== #16 克隆一个不漏 ====================

    @GameTest(template = "empty", batch = CORE)
    public static void cloneCopiesLichFields(GameTestHelper h) {
        RequiemCard card = new RequiemCard();
        card.beginLich("Grul Morr", 77L, 2, "minecraft:plains", "minecraft:overworld");
        card.setKuvaLevel(1);
        card.endLichRound(RequiemCard.FATE_ADMIN);
        card.beginLich("Zhaux Bekk", -991L, 4, "minecraft:desert", "minecraft:the_nether");
        card.setLichRage(0.42F);
        card.setLichCooldownUntil(123456L);
        card.setLichEncounterOpen(true);
        card.incLichEncounters();
        card.incLichEncounters();
        card.recordAttempt(11);
        card.recordAttempt(22);
        card.setLichPaused(true);

        RequiemCard copy = new RequiemCard();
        copy.clone(card);
        h.assertTrue(copy.getLichDataVersion() == card.getLichDataVersion(), "版本");
        h.assertTrue(copy.getLichName().equals(card.getLichName()), "名字");
        h.assertTrue(copy.getLichSeed() == card.getLichSeed(), "种子");
        h.assertTrue(copy.getLichGeneration() == card.getLichGeneration(), "代数");
        h.assertTrue(copy.getLichRage() == card.getLichRage(), "怒气");
        h.assertTrue(copy.getLichCooldownUntil() == card.getLichCooldownUntil(), "冷却");
        h.assertTrue(copy.isLichEncounterOpen() == card.isLichEncounterOpen(), "遭遇记号");
        h.assertTrue(copy.getLichEncounters() == card.getLichEncounters(), "交手次数");
        h.assertTrue(copy.getLichWeapon() == card.getLichWeapon(), "武器");
        h.assertTrue(copy.getLichBirthBiome().equals(card.getLichBirthBiome())
                && copy.getLichBirthDim().equals(card.getLichBirthDim()), "出生地");
        h.assertTrue(Arrays.equals(copy.getAttempts(), card.getAttempts()), "处决记录");
        h.assertTrue(copy.getHistory().equals(card.getHistory()), "历史");
        h.assertTrue(copy.isLichPaused() == card.isLichPaused(), "暂停");
        // 以后加了新字段却忘了抄：存档里所有 lich 开头的键也要一致
        CompoundTag a = card.serializeNBT();
        CompoundTag b = copy.serializeNBT();
        for (String key : a.getAllKeys()) {
            if (key.startsWith("lich")) {
                h.assertTrue(a.get(key).equals(b.get(key)), "克隆后存档键 " + key + " 不一致");
            }
        }
        h.succeed();
    }

    // ==================== #17 卡住了借雾换位 ====================

    @GameTest(template = "empty", batch = ISO_STUCK, timeoutTicks = 600)
    public static void stuckRelocates(GameTestHelper h) {
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichRepositionSeconds, 3);
        KuvaTestKit.override(ModConfig.KUVA_LICH.lichIdleDepartSeconds, 0);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = card(p);
        bornByProgress(p, card);
        KuvaMasterEntity lich = summonFront(h, p);
        p.setGameMode(GameType.CREATIVE);

        // 主人关进封闭的石盒子，玄骸挪到盒子外 20 格
        BlockPos c = KuvaTestKit.center(h);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(c.offset(dx, 3, dz), Blocks.STONE);
                if (dx != 0 || dz != 0) {
                    h.setBlock(c.offset(dx, 1, dz), Blocks.STONE);
                    h.setBlock(c.offset(dx, 2, dz), Blocks.STONE);
                }
            }
        }
        // 盒子外 14~26 格另开一块封闭的空地，和主人那边不连通
        openArena(h, c.getX() + 14, c.getX() + 26, c.getZ() - 10, c.getZ() + 10);
        Vec3 away = h.absoluteVec(new Vec3(c.getX() + 20.5D, 0.0D, c.getZ() + 0.5D));
        BlockPos awayPos = BlockPos.containing(away);
        h.assertTrue(LichSpawner.standingAt(h.getLevel(), awayPos) != null, "挪过去的那一格应当站得住：" + awayPos + " "
                + h.getLevel().getBlockState(awayPos.below()) + " / " + h.getLevel().getBlockState(awayPos)
                + " / " + h.getLevel().getBlockState(awayPos.above(2)) + " 主人 " + p.blockPosition());
        lich.teleportTo(away.x, away.y, away.z);
        h.succeedWhen(() -> {
            LichDirector.HuntView view = LichDirector.view(p);
            h.assertTrue(view != null && view.repositions() >= 1, "还没换位（卡住计时 "
                    + (view == null ? -1 : view.stuckTicks()) + "；" + stuckDetail(h, p, lich, view) + "）");
            h.assertTrue(!lich.isRemoved(), "换位后玄骸应当还在");
            h.assertTrue(view.bossBarShown() && view.bossBarPlayers().contains(p), "换位不应当撤 Boss 条");
            h.assertTrue(lich.getEncounterEnd() == null && card.isLichEncounterOpen(), "换位不应当结算");
        });
    }

    /** 换位断言失败时的现场：玄骸在哪、在不在跑 tick、是不是在显形 / 离场，寻路和换位相关的配置读出来是多少 */
    private static String stuckDetail(GameTestHelper h, ServerPlayer p, KuvaMasterEntity lich,
                                      @Nullable LichDirector.HuntView view) {
        BlockPos at = lich.blockPosition();
        net.minecraft.world.level.pathfinder.Path path = lich.getNavigation().getPath();
        return "状态 " + (view == null ? "-" : view.state())
                + "，玄骸 " + h.relativePos(at) + (lich.isRemoved() ? " 已移除 " + lich.getRemovalReason() : "")
                + (lich.isAppearing() ? " 显形中" : "") + (lich.isDeparting() ? " 离场中" : "")
                + "，离主人 " + String.format("%.1f", Math.sqrt(lich.distanceToSqr(p)))
                + "，实体 tick " + h.getLevel().isPositionEntityTicking(at)
                + "，路径 " + (path == null ? "无" : (lich.getNavigation().isDone() ? "走完" : "在走") + "/能到 " + path.canReach())
                + "，结算 " + lich.getEncounterEnd()
                + "，配置 换位 " + ModConfig.KUVA_LICH.lichRepositionSeconds.get() + " 秒 / 闲置 "
                + ModConfig.KUVA_LICH.lichIdleDepartSeconds.get() + " 秒";
    }

    @AfterBatch(batch = ISO_STUCK)
    public static void afterStuck(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    @AfterBatch(batch = CORE)
    public static void afterCore(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 工具 ====================

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

    /** 立即召到面前并返回那只 */
    private static KuvaMasterEntity summonFront(GameTestHelper h, ServerPlayer p) {
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 3, c.getZ() + 11);
        if (!LichDirector.summonNow(p, SummonMode.FRONT)) {
            StringBuilder sb = new StringBuilder();
            for (int dz = 5; dz <= 9; dz += 2) {
                BlockPos col = p.blockPosition().offset(0, -1, dz);
                sb.append(" dz").append(dz).append(':').append(h.getLevel().hasChunkAt(col) ? "" : "未载")
                        .append(h.getLevel().getBlockState(col.below()).getBlock().getDescriptionId().replace("block.minecraft.", ""))
                        .append('/').append(h.getLevel().getBlockState(col).getBlock().getDescriptionId().replace("block.minecraft.", ""))
                        .append('/').append(LichSpawner.standingAt(h.getLevel(), col) != null);
            }
            h.fail("立即召唤应当成功（" + p.blockPosition() + "，朝向 " + p.getYRot() + "，有玄骸 " + card(p).hasLich()
                    + "，状态 " + LichDirector.stateOf(p) + "，前方" + sb + "）");
        }
        return LichDirector.getLich(p).orElseThrow(() -> new IllegalStateException("召唤后应当有在场的玄骸"));
    }

    /** 挖一块封闭的场地（地板、围墙、封顶，和天然地形隔开），见 {@link KuvaTestKit#openArena} */
    private static void openArena(GameTestHelper h, int x0, int x1, int z0, int z1) {
        KuvaTestKit.openArena(h, x0, x1, z0, z1);
    }

    /**
     * 测试结构摆下时只给周围 5 × 5 个区块加了强制加载票，真正载入是异步的；batch 一开始就跑的测试
     * 找落点时旁边的区块可能还没载完，这里同步载入。只载有强制票的那些，免得临时载入的区块过后又卸掉、
     * 把站在上面的玄骸一起卸了
     */
    private static void loadTestChunks(GameTestHelper h) {
        ChunkPos origin = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        for (int dx = -1; dx < 4; dx++) {
            for (int dz = -1; dz < 4; dz++) {
                h.getLevel().getChunk(origin.x + dx, origin.z + dz);
            }
        }
    }

    private static List<KuvaMasterEntity> ownedBy(ServerLevel level, ServerPlayer p) {
        return level.getEntitiesOfClass(KuvaMasterEntity.class, new AABB(p.blockPosition()).inflate(96.0D),
                e -> e.isOwner(p) && !e.isRemoved());
    }

    private static List<KuvaMasterEntity> liches(ServerLevel level, BlockPos center, double radius) {
        return level.getEntitiesOfClass(KuvaMasterEntity.class, new AABB(center).inflate(radius), e -> !e.isRemoved());
    }

    private static long overworldTime(ServerPlayer p) {
        return p.server.overworld().getGameTime();
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
