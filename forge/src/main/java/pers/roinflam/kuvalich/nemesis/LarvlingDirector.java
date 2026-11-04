package pers.roinflam.kuvalich.nemesis;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 赤毒幼体的导演：还没有玄骸的玩家，按在线时间每 {@code larvlingIntervalMin~MaxMinutes} 分钟（随机）在他附近、
 * 看不见的地方放一只幼体，同一个人同时最多一只
 *
 * <p>计时只在他在线、没有玄骸时走，剩多少跟着安魂卡存盘（{@link RequiemCard#getLarvlingWait()}），下线再上来接着数。
 * 到点了还要满足：在 {@code larvlingDimensions} 里、难度不是和平、不是创造 / 旁观、活着、没睡觉、没骑东西、没在处决；
 * 不满足就每秒再看一次。登录后 30 秒内不放。</p>
 *
 * <p>幼体在场期间计时不走。它没了以后：进过倒地的（被处决诞生了玄骸、倒地被打散、倒地超时）重新掷一个完整的间隔；
 * 没倒地就没了的（主人下线、走远、换维度、死了，区块卸载、关服）算走丢了，2 分钟后补一只。
 * 刷出来那一刻就先把计时记成「2 分钟」，关服丢了的幼体，下次也是 2 分钟后补。</p>
 *
 * <p>主人离开：每秒看一次，主人下线、死了、换了世界、离它超过 96 格、已经有了玄骸，没倒地的幼体就地消散；
 * 已经倒地的不收，留给有资格的人处决或者等它超时。</p>
 *
 * <p>开销：每 20 tick 每个玩家几次比较；只有到点那一次才找落点（最多 24 次射线检测，找不到下一秒再试）。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class LarvlingDirector {

    /** 慢循环间隔（tick） */
    private static final int PERIOD = 20;
    /** 登录后多久才放 */
    private static final int LOGIN_GRACE_TICKS = 30 * 20;
    /** 没倒地就走丢了：多久后补一只 */
    public static final int RETRY_AFTER_LOST = 2 * 60 * 20;
    /** 落点连着找不到几次就先歇一会儿，歇多久 */
    private static final int SEARCH_FAILS = 10;
    private static final int SEARCH_BACKOFF = 30 * 20;
    /** 落点离主人多远：先在这个范围里找看不见的，找不到再放宽 */
    private static final int SPAWN_MIN = 16;
    private static final int SPAWN_MAX = 28;
    private static final int RELAXED_MIN = 10;
    private static final int TRIES = 24;
    /** 主人离它超过这么远就收掉 */
    private static final double LEASH = 96.0D;
    /** 幼体的碰撞箱（同奴仆） */
    private static final double HALF_WIDTH = 1.5D / 2.0D;
    private static final double HEIGHT = 2.9D;

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    /** 一个在线玩家的记录（只在内存里） */
    private static final class Track {
        @Nullable
        KuvaLarvlingEntity larvling;
        long loginAt;
        int searchFails;
    }

    /** 管理命令、测试看的快照 */
    public record View(int waitTicks, @Nullable KuvaLarvlingEntity larvling, String blocker) {
    }

    private LarvlingDirector() {
    }

    // ==================== 事件 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        long now = now(server);
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            try {
                tick(player, now);
            } catch (RuntimeException e) {
                LogUtil.error("幼体导演处理玩家 " + player.getGameProfile().getName() + " 时出错", e);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            track(player).loginAt = now(player.server);
        }
    }

    /** 下线：没倒地的幼体就地消散，记录删掉（计时在卡上，刷出来时已经记成 2 分钟） */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Track t = TRACKS.remove(event.getEntity().getUUID());
        if (t != null && t.larvling != null && !t.larvling.isRemoved()) {
            t.larvling.dismiss();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKS.clear();
    }

    // ==================== 慢循环 ====================

    /** 一个玩家的那一份（服务端 tick 里每 20 tick 调；测试直接调来快进，每调一次计时走 20 tick） */
    public static void tick(ServerPlayer player, long now) {
        RequiemCard card = card(player);
        if (card == null) {
            return;
        }
        Track t = track(player);
        KuvaLarvlingEntity live = t.larvling;
        if (live != null) {
            if (live.isRemoved()) {
                t.larvling = null;
                // 打过了（处决 / 倒地被打散 / 超时）重新等一整轮；没打过就没了按走丢算
                card.setLarvlingWait(live.wasEverDowned() ? rollWait(player) : RETRY_AFTER_LOST);
            } else {
                if (shouldDismiss(player, card, live)) {
                    live.dismiss();
                }
                return;
            }
        }
        if (card.hasLich() || !ModConfig.KUVA_LICH.larvlingEnabled.get()) {
            return;
        }
        int wait = card.getLarvlingWait();
        if (wait < 0) {
            wait = rollWait(player);
        }
        wait = Math.max(0, wait - PERIOD);
        card.setLarvlingWait(wait);
        if (wait > 0 || now - t.loginAt < LOGIN_GRACE_TICKS || !blocker(player).isEmpty()) {
            return;
        }
        KuvaLarvlingEntity spawned = spawn(player, false);
        if (spawned == null) {
            if (++t.searchFails >= SEARCH_FAILS) {
                t.searchFails = 0;
                card.setLarvlingWait(SEARCH_BACKOFF);
            }
            return;
        }
        t.searchFails = 0;
        PlayerFeedback.actionBar(player, KuvaPalette.LICH, "message.kuvalich.larvling.appeared");
    }

    /** 主人下线（不在列表里）、死了、换了世界、走远了、已经有了玄骸 */
    private static boolean shouldDismiss(ServerPlayer owner, RequiemCard card, KuvaLarvlingEntity larvling) {
        if (card.hasLich() || !owner.isAlive() || owner.hasDisconnected() || larvling.level() != owner.level()) {
            return true;
        }
        return owner.distanceToSqr(larvling) > LEASH * LEASH;
    }

    /** 不能放的原因，能放返回空串：维度、难度、游戏模式、忙 */
    public static String blocker(ServerPlayer player) {
        String dim = player.level().dimension().location().toString();
        if (!ModConfig.KUVA_LICH.larvlingDimensions.get().contains(dim)) {
            return "dimension";
        }
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return "peaceful";
        }
        if (player.isCreative() || player.isSpectator()) {
            return "creative";
        }
        if (!player.isAlive() || player.isSleeping() || player.isPassenger() || MercyServer.isBusy(player)) {
            return "busy";
        }
        return "";
    }

    // ==================== 放一只 ====================

    /**
     * 在玩家附近放一只幼体，记成他的：{@code front} 为真时放在面前 5~9 格（管理命令），否则放在 16~28 格外看不见的地方，
     * 找不到再放宽到 10 格、不要求看不见。已经有一只的先收掉。找不到落点返回 null
     */
    @Nullable
    public static KuvaLarvlingEntity spawn(ServerPlayer player, boolean front) {
        ServerLevel level = player.serverLevel();
        Vec3 at = front ? LichSpawner.findFront(player, null, TRIES * 2, HALF_WIDTH, HEIGHT) : null;
        if (at == null) {
            at = LichSpawner.findHidden(player, SPAWN_MIN, SPAWN_MAX, TRIES, false, HALF_WIDTH, HEIGHT);
        }
        if (at == null) {
            at = LichSpawner.findHidden(player, RELAXED_MIN, SPAWN_MAX, TRIES, true, HALF_WIDTH, HEIGHT);
        }
        if (at == null) {
            return null;
        }
        KuvaLarvlingEntity larvling = KuvaLichEntities.KUVA_LARVLING.get().create(level);
        if (larvling == null) {
            return null;
        }
        Track t = track(player);
        if (t.larvling != null && !t.larvling.isRemoved()) {
            t.larvling.discard();
        }
        larvling.setup(player.getUUID(), LichDirector.randomSeed(player));
        float yaw = (float) (Mth.atan2(player.getZ() - at.z, player.getX() - at.x) * (180.0D / Math.PI)) - 90.0F;
        larvling.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        larvling.setYHeadRot(yaw);
        larvling.setYBodyRot(yaw);
        larvling.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null, null);
        if (!level.addFreshEntity(larvling)) {
            return null;
        }
        larvling.setTarget(player);
        t.larvling = larvling;
        RequiemCard card = card(player);
        if (card != null) {
            // 在场期间计时不走；关服、区块卸载把它弄丢了，下次也是 2 分钟后补
            card.setLarvlingWait(RETRY_AFTER_LOST);
        }
        if (LogUtil.isDebugEnabled()) {
            LogUtil.debug("[幼体导演] " + player.getGameProfile().getName() + "：放了一只 @ " + BlockPos.containing(at)
                    + "，武器 " + larvling.getWeaponIndex());
        }
        return larvling;
    }

    // ==================== 对外：命令、测试 ====================

    /** 这个玩家眼下的幼体（没有或者已经没了返回 null） */
    @Nullable
    public static KuvaLarvlingEntity larvlingOf(ServerPlayer player) {
        Track t = TRACKS.get(player.getUUID());
        return t == null || t.larvling == null || t.larvling.isRemoved() ? null : t.larvling;
    }

    /** 把计时拨到还剩 {@code ticks}（在线 tick），顺带去掉登录宽限 */
    public static void setWait(ServerPlayer player, int ticks) {
        RequiemCard card = card(player);
        if (card != null) {
            card.setLarvlingWait(Math.max(0, ticks));
        }
        track(player).loginAt = Long.MIN_VALUE / 4;
    }

    public static View view(ServerPlayer player) {
        RequiemCard card = card(player);
        return new View(card == null ? -1 : card.getLarvlingWait(), larvlingOf(player), blocker(player));
    }

    // ==================== 工具 ====================

    private static int rollWait(ServerPlayer player) {
        double min = ModConfig.KUVA_LICH.larvlingIntervalMinMinutes.get();
        double max = Math.max(min, ModConfig.KUVA_LICH.larvlingIntervalMaxMinutes.get());
        double minutes = min + player.getRandom().nextDouble() * (max - min);
        return (int) Math.round(minutes * 1200.0D);
    }

    private static Track track(ServerPlayer player) {
        return TRACKS.computeIfAbsent(player.getUUID(), id -> {
            Track t = new Track();
            t.loginAt = now(player.server);
            return t;
        });
    }

    @Nullable
    private static RequiemCard card(ServerPlayer player) {
        return player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
    }

    private static long now(@Nullable MinecraftServer server) {
        return server == null ? 0L : server.overworld().getGameTime();
    }
}
