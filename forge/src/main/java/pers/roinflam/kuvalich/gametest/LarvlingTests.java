package pers.roinflam.kuvalich.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.nemesis.LarvlingDirector;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.LichNames;
import pers.roinflam.kuvalich.nemesis.LichProfile;
import pers.roinflam.kuvalich.nemesis.LichState;
import pers.roinflam.kuvalich.nemesis.ThrallDirector;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;
import java.util.UUID;

/**
 * 赤毒幼体和奴仆小队：幼体什么时候出、倒地等多久、处决后诞生的玄骸拿的是不是头顶那把；
 * 奴仆只找有玄骸的人、身边有上限；奴仆的进度分给附近所有有玄骸的人
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LarvlingTests {

    private static final String CFG_RULES = "kl_cfg_larvlingRules";
    private static final String ISO_BIRTH = "kl_larvling_iso_birth";
    private static final String ISO_CLAIM = "kl_larvling_iso_claim";
    private static final String ISO_TIMEOUT = "kl_larvling_iso_timeout";
    private static final String ISO_HIT = "kl_larvling_iso_hitWhileDown";
    private static final String CFG_SQUAD = "kl_cfg_thrallSquad";
    private static final String ISO_SHARE = "kl_larvling_iso_shareProgress";

    private LarvlingTests() {
    }

    // ==================== 什么时候出幼体 ====================

    @GameTest(template = "empty", batch = CFG_RULES, timeoutTicks = 100)
    public static void larvlingSpawnRules(GameTestHelper h) {
        openBig(h);
        ServerLevel level = h.getLevel();
        long now = now(h);

        // 创造模式不出
        ServerPlayer creative = KuvaTestKit.player(h);
        creative.setGameMode(GameType.CREATIVE);
        LarvlingDirector.setWait(creative, 0);
        LarvlingDirector.tick(creative, now);
        h.assertTrue("creative".equals(LarvlingDirector.blocker(creative)), "创造模式应当挡住：" + LarvlingDirector.blocker(creative));
        h.assertTrue(LarvlingDirector.larvlingOf(creative) == null, "创造模式不应当出幼体");

        // 已经有玄骸的不出
        ServerPlayer owner = KuvaTestKit.player(h);
        giveLich(owner, 0x11L);
        LarvlingDirector.setWait(owner, 0);
        LarvlingDirector.tick(owner, now);
        h.assertTrue(LarvlingDirector.larvlingOf(owner) == null, "有玄骸的人不应当出幼体");

        // 不在名单里的维度不出
        ServerPlayer p = KuvaTestKit.player(h);
        KuvaTestKit.override(ModConfig.KUVA_LICH.larvlingDimensions, List.of("minecraft:the_nether"));
        LarvlingDirector.setWait(p, 0);
        LarvlingDirector.tick(p, now);
        h.assertTrue("dimension".equals(LarvlingDirector.blocker(p)), "维度不对应当挡住：" + LarvlingDirector.blocker(p));
        h.assertTrue(LarvlingDirector.larvlingOf(p) == null, "维度不对不应当出幼体");
        KuvaTestKit.override(ModConfig.KUVA_LICH.larvlingDimensions, List.of("minecraft:overworld"));

        // 计时没到不出：计时只在调导演时往下走 20 tick
        LarvlingDirector.setWait(p, 200);
        LarvlingDirector.tick(p, now);
        h.assertTrue(LarvlingDirector.larvlingOf(p) == null, "计时没到不应当出幼体");
        h.assertTrue(cardOf(p).getLarvlingWait() == 180, "计时每次走 20 tick，实际剩 " + cardOf(p).getLarvlingWait());

        // 到点：出一只，武器按种子定、记成他的
        LarvlingDirector.setWait(p, 0);
        LarvlingDirector.tick(p, now);
        KuvaLarvlingEntity l = LarvlingDirector.larvlingOf(p);
        h.assertTrue(l != null, "到点应当出一只幼体（场地 " + p.blockPosition() + "）");
        h.assertTrue(p.getUUID().equals(l.getOwnerUUID()), "幼体应当记成他的");
        h.assertTrue(l.getWeaponIndex() == LichProfile.weaponIndex(l.getLichSeed(), KuvaLichItems.KUVA_WEAPONS.size()),
                "武器应当按种子算");
        double d = Math.sqrt(l.distanceToSqr(p));
        h.assertTrue(d >= 9.5D && d <= 29.0D, "应当出在 10~28 格外，实际 " + d);
        h.assertTrue(cardOf(p).getLarvlingWait() == LarvlingDirector.RETRY_AFTER_LOST, "在场期间计时停在「走丢重试」");

        // 每人最多一只：再到点也不出第二只
        LarvlingDirector.setWait(p, 0);
        LarvlingDirector.tick(p, now);
        h.assertTrue(LarvlingDirector.larvlingOf(p) == l, "还在场时不应当换一只");
        int count = level.getEntitiesOfClass(KuvaLarvlingEntity.class, l.getBoundingBox().inflate(64.0D)).size();
        h.assertTrue(count == 1, "附近应当只有一只幼体，实际 " + count);

        // 和平难度不出（难度全服共用，这一项在单独的 batch 里，当场改回）
        MinecraftServer server = level.getServer();
        Difficulty before = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        String blocker = LarvlingDirector.blocker(p);
        server.setDifficulty(before, true);
        h.assertTrue("peaceful".equals(blocker), "和平难度应当挡住：" + blocker);
        l.discard();
        h.succeed();
    }

    @AfterBatch(batch = CFG_RULES)
    public static void afterRules(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 处决幼体：诞生的玄骸拿的就是头顶那把，重名也不换 ====================

    @GameTest(template = "empty", batch = ISO_BIRTH, timeoutTicks = 120)
    public static void larvlingMercyBirthsLichWithShownWeapon(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = cardOf(p);
        // 找一个能直接起名的种子，再让另一个在线的人的玄骸占住这个名字，逼诞生时换名
        long seed = 0x6A11L;
        while (!LichNames.isAllowed(LichNames.generate(seed))) {
            seed++;
        }
        String shownName = LichNames.generate(seed);
        ServerPlayer rival = KuvaTestKit.player(h);
        cardOf(rival).beginLich(shownName, seed ^ 0x5555L, 0, "minecraft:plains", "minecraft:overworld");

        KuvaLarvlingEntity l = spawnLarvling(h, p.getUUID(), seed, p.position().add(0.0D, 0.0D, 2.0D));
        int weapon = l.getWeaponIndex();
        h.assertTrue(weapon >= 0 && weapon == LichProfile.weaponIndex(seed, KuvaLichItems.KUVA_WEAPONS.size()),
                "幼体的武器应当按种子定：" + weapon);
        l.setHealth(1.0F);
        h.assertTrue(l.hurt(p.damageSources().playerAttack(p), 20.0F), "致命一击应当打得进去");
        h.assertTrue(l.getMercyState() == MercyState.DOWNED && l.isAlive(), "幼体应当倒地");
        h.assertTrue(l.getMercyTotalSeconds() == ModConfig.KUVA_LICH.larvlingDownedSeconds.get(),
                "倒地时长应当是幼体自己的配置，实际 " + l.getMercyTotalSeconds());
        h.assertTrue(p.getUUID().equals(l.getMercyQualified()), "打倒它的人还没有玄骸，资格人就是他");
        h.assertTrue(MercyServer.executeNow(p, l) == null, "应当能开始处决");
        long finalSeed = seed;
        h.runAfterDelay(30, () -> {
            h.assertTrue(!l.isAlive(), "tick 30 应当死了");
            h.assertTrue(card.hasLich(), "处决幼体应当诞生玄骸");
            h.assertTrue(card.getLichWeapon() == weapon, "玄骸的武器应当是幼体头顶那把：" + weapon + "，实际 "
                    + card.getLichWeapon());
            h.assertTrue(card.getLichSeed() == finalSeed, "种子应当是幼体刷出来时掷的那个");
            h.assertTrue(!shownName.equalsIgnoreCase(card.getLichName()), "名字被占时应当换名：" + card.getLichName());
            h.assertTrue(LichDirector.stateOf(p) == LichState.STALKING, "刚诞生应当在跟踪：" + LichDirector.stateOf(p));
            double birth = ModConfig.KUVA_LICH.lichBirthRage.get();
            h.assertTrue(Math.abs(card.getLichRage() - birth) < 1.0E-3D, "刚诞生的怒气应当是 " + birth);
            h.assertTrue(card.getDecryptionProgress() == 0 && card.getUnlockedCardStatus() == 0, "幼体不给解密进度");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_BIRTH)
    public static void afterBirth(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 谁能处决：有玄骸的人永远不行，退给主人 ====================

    @GameTest(template = "empty", batch = ISO_CLAIM, timeoutTicks = 120)
    public static void larvlingClaimant(GameTestHelper h) {
        clearAround(h);
        ServerPlayer owner = KuvaTestKit.player(h);
        ServerPlayer hunter = KuvaTestKit.player(h);
        giveLich(hunter, 0x22L);
        long hunterSeed = cardOf(hunter).getLichSeed();
        KuvaLarvlingEntity l = spawnLarvling(h, owner.getUUID(), 0x33L, owner.position().add(0.0D, 0.0D, 2.0D));
        h.assertTrue(owner.getUUID().equals(l.pickClaimant(hunter.getUUID())), "有玄骸的人打倒的，资格人退给主人");
        h.assertTrue(owner.getUUID().equals(l.pickClaimant(owner.getUUID())), "主人自己打倒的就是主人");
        ServerPlayer passer = KuvaTestKit.player(h);
        h.assertTrue(passer.getUUID().equals(l.pickClaimant(passer.getUUID())), "没有玄骸的路人打倒的就是路人");

        // 有玄骸的人硬处决（开发路径不查资格）：幼体照样死，但他什么都不得，主人也不得
        l.setHealth(1.0F);
        h.assertTrue(l.hurt(hunter.damageSources().playerAttack(hunter), 20.0F), "致命一击应当打得进去");
        h.assertTrue(l.getMercyState() == MercyState.DOWNED, "幼体应当倒地");
        h.assertTrue(owner.getUUID().equals(l.getMercyQualified()), "倒地时资格人应当是主人");
        h.assertTrue(MercyServer.executeNow(hunter, l) == null, "开发路径应当能开始处决");
        h.runAfterDelay(30, () -> {
            h.assertTrue(!l.isAlive(), "tick 30 应当死了");
            h.assertTrue(cardOf(hunter).getLichSeed() == hunterSeed, "已经有玄骸的人不应当换一只");
            h.assertTrue(!cardOf(owner).hasLich(), "不是主人处决的，主人不应当得到玄骸");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_CLAIM)
    public static void afterClaim(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 倒地 30 秒没人处决：消散，什么都没有 ====================

    @GameTest(template = "empty", batch = ISO_TIMEOUT, timeoutTicks = 760)
    public static void larvlingDownedTimeout(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        RequiemCard card = cardOf(p);
        KuvaLarvlingEntity l = spawnLarvling(h, p.getUUID(), 0x44L, p.position().add(0.0D, 0.0D, 3.0D));
        l.setHealth(1.0F);
        h.assertTrue(l.hurt(p.damageSources().playerAttack(p), 20.0F), "致命一击应当打得进去");
        h.assertTrue(l.getMercyState() == MercyState.DOWNED, "幼体应当倒地");
        int seconds = ModConfig.KUVA_LICH.larvlingDownedSeconds.get();
        h.assertTrue(seconds == 30, "默认倒地 30 秒，实际 " + seconds);
        // 人走开，免得站在旁边一直被它当成目标
        p.teleportTo(h.getLevel(), p.getX() - 4.0D, p.getY(), p.getZ(), 0.0F, 0.0F);
        h.runAfterDelay(seconds * 20L - 20L, () -> {
            h.assertTrue(l.isAlive() && l.getMercyState() == MercyState.DOWNED,
                    "第 " + (seconds - 1) + " 秒应当还倒着：" + l.getMercyState());
            h.runAfterDelay(40, () -> {
                h.assertTrue(!l.isAlive() || l.isRemoved() || l.getMercyState() == MercyState.LEAVING,
                        "超时应当消散：" + l.getMercyState());
                h.assertTrue(!card.hasLich(), "没被处决不应当诞生玄骸");
                h.assertTrue(l.wasEverDowned() && !l.isBirthGiven(), "记成打过、没给过玄骸");
                h.succeed();
            });
        });
    }

    @AfterBatch(batch = ISO_TIMEOUT)
    public static void afterTimeout(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 倒地时又挨一下：就地消散 ====================

    @GameTest(template = "empty", batch = ISO_HIT, timeoutTicks = 120)
    public static void larvlingHitWhileDown(GameTestHelper h) {
        clearAround(h);
        ServerPlayer p = KuvaTestKit.player(h);
        KuvaLarvlingEntity l = spawnLarvling(h, p.getUUID(), 0x55L, p.position().add(0.0D, 0.0D, 2.0D));
        l.setHealth(1.0F);
        h.assertTrue(l.hurt(p.damageSources().playerAttack(p), 20.0F), "致命一击应当打得进去");
        h.assertTrue(l.getMercyState() == MercyState.DOWNED, "幼体应当倒地");
        h.assertTrue(!l.hurt(p.damageSources().playerAttack(p), 2.0F), "倒地时挨打不算伤害");
        h.assertTrue(l.getMercyState() == MercyState.LEAVING, "倒地时挨打应当就地消散：" + l.getMercyState());
        h.assertTrue(MercyServer.executeNow(p, l) != null, "消散中不能处决");
        h.runAfterDelay(80, () -> {
            h.assertTrue(!cardOf(p).hasLich(), "打散的幼体不给玄骸");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_HIT)
    public static void afterHit(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 奴仆小队：只找有玄骸的人，身边有上限 ====================

    @GameTest(template = "empty", batch = CFG_SQUAD, timeoutTicks = 100)
    public static void thrallSquadsOnlyForLichOwners(GameTestHelper h) {
        openBig(h);
        long now = now(h);
        KuvaTestKit.override(ModConfig.KUVA_LICH.thrallSquadSizeMin, 3);
        KuvaTestKit.override(ModConfig.KUVA_LICH.thrallSquadSizeMax, 3);
        KuvaTestKit.override(ModConfig.KUVA_LICH.thrallSquadMaxNearby, 4);

        // 没有玄骸：到点也不放，也不留计时
        ServerPlayer none = KuvaTestKit.player(h);
        ThrallDirector.setNext(none, 0);
        ThrallDirector.tick(none, now);
        h.assertTrue(ThrallDirector.nearby(none) == 0, "没有玄骸不应当放奴仆");
        h.assertTrue(ThrallDirector.view(none).nextInTicks() < 0, "没有玄骸不应当计时");

        // 有玄骸但在创造模式：不放
        ServerPlayer creative = KuvaTestKit.player(h);
        giveLich(creative, 0x66L);
        creative.setGameMode(GameType.CREATIVE);
        ThrallDirector.setNext(creative, 0);
        ThrallDirector.tick(creative, now);
        h.assertTrue(ThrallDirector.nearby(creative) == 0, "创造模式不应当放奴仆");

        // 有玄骸：到点放一队（一队 3 只）
        ServerPlayer owner = KuvaTestKit.player(h);
        giveLich(owner, 0x77L);
        ThrallDirector.setNext(owner, 0);
        ThrallDirector.tick(owner, now);
        int first = ThrallDirector.nearby(owner);
        // 一队 3 只，第一只以外的贴着它找落点，挨着墙时可能少放
        h.assertTrue(first >= 1 && first <= 3, "有玄骸应当放一队（最多 3 只），实际 " + first);
        h.assertTrue(ThrallDirector.view(owner).nextInTicks() > 0, "放完应当重新计时");

        // 上限 4：再到点只补到 4 只为止；满了就不放
        for (int i = 0; i < 6 && ThrallDirector.nearby(owner) < 4; i++) {
            ThrallDirector.setNext(owner, 0);
            ThrallDirector.tick(owner, now);
            h.assertTrue(ThrallDirector.nearby(owner) <= 4, "不应当超过上限，实际 " + ThrallDirector.nearby(owner));
        }
        h.assertTrue(ThrallDirector.nearby(owner) == 4, "应当补到上限 4，实际 " + ThrallDirector.nearby(owner));
        ThrallDirector.setNext(owner, 0);
        ThrallDirector.tick(owner, now);
        h.assertTrue(ThrallDirector.nearby(owner) == 4, "够数了不应当再放，实际 " + ThrallDirector.nearby(owner));
        for (KuvaSlaveEntity t : h.getLevel().getEntitiesOfClass(KuvaSlaveEntity.class,
                owner.getBoundingBox().inflate(64.0D), e -> e.getType() == KuvaLichEntities.KUVA_SLAVE.get())) {
            t.discard();
        }
        h.succeed();
    }

    @AfterBatch(batch = CFG_SQUAD)
    public static void afterSquad(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 奴仆进度分给 48 格内所有有玄骸的人 ====================

    @GameTest(template = "empty", batch = ISO_SHARE, timeoutTicks = 120)
    public static void thrallProgressShared(GameTestHelper h) {
        clearAround(h);
        int range = ModConfig.KUVA_LICH.thrallProgressShareRange.get();
        h.assertTrue(range == 48, "默认共享范围 48 格，实际 " + range);
        ServerPlayer killer = KuvaTestKit.player(h);
        giveLich(killer, 0x88L);
        // 场地里的另一个有玄骸的人
        ServerPlayer near = KuvaTestKit.player(h);
        giveLich(near, 0x99L);
        near.teleportTo(h.getLevel(), killer.getX() + 3.0D, killer.getY(), killer.getZ(), 0.0F, 0.0F);
        // 有玄骸、但在 60 格外（在石头里，开创造免得窒息）
        ServerPlayer far = KuvaTestKit.player(h);
        giveLich(far, 0xAAL);
        far.setGameMode(GameType.CREATIVE);
        far.teleportTo(h.getLevel(), killer.getX() + 60.0D, killer.getY(), killer.getZ(), 0.0F, 0.0F);
        // 就在旁边、但没有玄骸
        ServerPlayer bystander = KuvaTestKit.player(h);
        bystander.setGameMode(GameType.CREATIVE);
        bystander.teleportTo(h.getLevel(), killer.getX() - 3.0D, killer.getY(), killer.getZ(), 0.0F, 0.0F);

        KuvaSlaveEntity thrall = KuvaLichEntities.KUVA_SLAVE.get().create(h.getLevel());
        h.assertTrue(thrall != null, "应当能造出奴仆");
        Vec3 at = killer.position().add(0.0D, 0.0D, 2.0D);
        thrall.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        h.assertTrue(h.getLevel().addFreshEntity(thrall), "奴仆应当加得进世界");
        thrall.setHealth(1.0F);
        h.assertTrue(thrall.hurt(killer.damageSources().playerAttack(killer), 20.0F), "致命一击应当打得进去");
        h.assertTrue(thrall.getMercyState() == MercyState.DOWNED, "奴仆应当倒地");
        h.assertTrue(MercyServer.executeNow(killer, thrall) == null, "应当能开始处决");
        h.runAfterDelay(30, () -> {
            h.assertTrue(!thrall.isAlive(), "tick 30 应当死了");
            int got = cardOf(killer).getDecryptionProgress();
            h.assertTrue(got > 0, "击杀者应当加进度");
            h.assertTrue(cardOf(near).getDecryptionProgress() == got,
                    "48 格内有玄骸的人应当加一样多：" + got + " / " + cardOf(near).getDecryptionProgress());
            h.assertTrue(cardOf(far).getDecryptionProgress() == 0, "60 格外的不应当加");
            h.assertTrue(cardOf(bystander).getDecryptionProgress() == 0, "没有玄骸的不应当加");
            h.succeed();
        });
    }

    @AfterBatch(batch = ISO_SHARE)
    public static void afterShare(ServerLevel level) {
        KuvaTestKit.afterBatch(level);
    }

    // ==================== 工具 ====================

    private static RequiemCard cardOf(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD)
                .orElseThrow(() -> new IllegalStateException("测试玩家没有安魂卡能力"));
    }

    /** 直接写上一只玄骸（不走诞生流程，不排诞生通讯） */
    private static void giveLich(ServerPlayer p, long salt) {
        long seed = p.getUUID().getLeastSignificantBits() ^ salt;
        cardOf(p).beginLich(LichNames.generate(seed), seed,
                LichProfile.weaponIndex(seed, KuvaLichItems.KUVA_WEAPONS.size()), "minecraft:plains", "minecraft:overworld");
    }

    private static KuvaLarvlingEntity spawnLarvling(GameTestHelper h, @Nullable UUID owner, long seed, Vec3 at) {
        KuvaLarvlingEntity l = KuvaLichEntities.KUVA_LARVLING.get().create(h.getLevel());
        h.assertTrue(l != null, "应当能造出幼体");
        l.setup(owner, seed);
        l.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        h.assertTrue(h.getLevel().addFreshEntity(l), "幼体应当加得进世界");
        return l;
    }

    private static long now(GameTestHelper h) {
        return h.getLevel().getServer().overworld().getGameTime();
    }

    /** 以结构中心为心挖一块 13 × 13 的空地 */
    private static void clearAround(GameTestHelper h) {
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        KuvaTestKit.openArena(h, c.getX() - 6, c.getX() + 6, c.getZ() - 6, c.getZ() + 6);
    }

    /** 导演要在 10~28 格外找落点：挖一块 41 × 41 的封闭场地 */
    private static void openBig(GameTestHelper h) {
        loadTestChunks(h);
        BlockPos c = KuvaTestKit.center(h);
        KuvaTestKit.openArena(h, c.getX() - 20, c.getX() + 20, c.getZ() - 20, c.getZ() + 20);
    }

    private static void loadTestChunks(GameTestHelper h) {
        ChunkPos origin = new ChunkPos(h.absolutePos(BlockPos.ZERO));
        for (int dx = -2; dx < 5; dx++) {
            for (int dz = -2; dz < 5; dz++) {
                h.getLevel().getChunk(origin.x + dx, origin.z + dz);
            }
        }
    }
}
