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
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.worldgen.KuvaSpawnLimiter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 奴仆小队：奴仆不再在野外自然生成，只给有玄骸的玩家放。每人一个倒数，数完在他身边 16~28 格外、看不见的地方放一小队，
 * 一队 {@code thrallSquadSizeMin~Max} 只，彼此挨着
 *
 * <p>默认 180~300 秒一队、一队 1~2 只、身边 48 格内最多 4 只、每人一小时最多 20 只、世界出生点 200 格内不来：
 * 平均四分钟来一两只，一小时二十只上下，碰到上限就等最早那批过了一小时。不理它们的话也只会攒到 4 只为止。
 * 按平均每只 12.5 点进度、三条密语共 264 点算，光靠奴仆要一个小时出头才揭得完，玄骸自己身上给的进度另算；
 * 刷得太密时玩家反映「站几秒就一波」「萌新被奴仆宰了」，所以宁可慢一点。</p>
 *
 * <p>能放的条件：有玄骸、在 {@code thrallSquadDimensions} 里、难度不是和平、不是创造 / 旁观、活着、没在处决、
 * 玄骸没被管理员暂停、离该世界出生点（水平距离）不少于 {@code thrallSquadSpawnSafeRadius}。
 * <b>倒数只在能放的时候往下走</b>：在别的维度、出生点附近、创造模式里待多久都不攒，从不能放变成能放时剩余至少补到 {@code thrallSquadResumeDelaySeconds}（默认 30 秒）
 * （以前记的是绝对时刻，在主城待够一个间隔，一回资源世界一秒内就来一队）。
 * 数完了身边已经够数就重掷一个间隔，快够数、小时额度快满就少放几只，额度满了就等最早那批滑出一小时窗口。
 * 找不到落点（窝在洞里、林子里）隔 {@code thrallSquadRetrySeconds}（默认 15 秒）再试，不每秒试，免得一走到空地马上来。</p>
 *
 * <p>几个人站一起只按一个节奏：给一个人放出一队时，他 {@code thrallSquadRadius} 格内其他有玄骸的玩家的倒数也至少
 * 重置成一整个间隔，这一队也记进他们的小时额度（以前每人各数各的，三个人站一起来得是三倍）。</p>
 *
 * <p>放出来的奴仆直接盯上这个人（只有第一只放登场警示），不常驻：人走远了照原版规矩自然消失。</p>
 *
 * <p>倒数只放内存，下线删掉，上线、刚诞生玄骸时从头数一整个间隔。小时额度的记录按主世界 gameTime 记，
 * 下线不删（不然重登就清零），关服清掉。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ThrallDirector {

    private static final int PERIOD = 20;
    private static final int SPAWN_MIN = 16;
    private static final int SPAWN_MAX = 28;
    private static final int RELAXED_MIN = 12;
    private static final int TRIES = 24;
    /** 同一队里其余几只离第一只多远以内找落点 */
    private static final int SPREAD = 3;
    /** 小时额度的窗口 */
    private static final long HOUR = 60L * 60L * 20L;
    private static final double HALF_WIDTH = 1.5D / 2.0D;
    private static final double HEIGHT = 2.9D;

    private static final Map<UUID, Squad> SQUADS = new HashMap<>();
    /** 每人放过来的批次：{时刻, 只数}，按时间先后排；下线不删 */
    private static final Map<UUID, ArrayDeque<long[]>> HOURLY = new HashMap<>();

    private static final class Squad {
        /** 还要等多少能放的 tick，-1 = 还没定 */
        int wait = -1;
        /** 上一次看的时候是不是放不了 */
        boolean blocked;
    }

    /**
     * 管理命令、测试看的快照
     *
     * @param lastHour      最近一小时放过来几只（含站在别人旁边沾上的）
     * @param spawnDistance 离该世界出生点的水平距离（格）
     */
    public record View(long nextInTicks, int nearby, String blocker, int lastHour, int spawnDistance) {
    }

    private ThrallDirector() {
    }

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
                LogUtil.error("奴仆小队处理玩家 " + player.getGameProfile().getName() + " 时出错", e);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 小时额度不删：下线重登不能清零
        SQUADS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SQUADS.clear();
        HOURLY.clear();
    }

    /** 一个玩家的那一份（每 20 tick）；测试直接调，每调一次倒数走 20 tick */
    public static void tick(ServerPlayer player, long now) {
        RequiemCard card = card(player);
        if (card == null || !card.hasLich() || !ModConfig.KUVA_LICH.thrallSquadEnabled.get()) {
            // 没有玄骸时不计时：诞生那一刻起才从头数一整个间隔
            SQUADS.remove(player.getUUID());
            return;
        }
        Squad s = SQUADS.computeIfAbsent(player.getUUID(), id -> new Squad());
        if (s.wait < 0) {
            s.wait = rollInterval(player);
            return;
        }
        if (!blocker(player, card).isEmpty()) {
            s.blocked = true;
            return;
        }
        if (s.blocked) {
            // 刚回到能放的地方：不管之前攒了多久，至少再等 thrallSquadResumeDelaySeconds（默认 30 秒，0 = 不补）
            s.blocked = false;
            s.wait = Math.max(s.wait, ModConfig.KUVA_LICH.thrallSquadResumeDelaySeconds.get() * 20);
        }
        s.wait = Math.max(0, s.wait - PERIOD);
        if (s.wait > 0) {
            return;
        }
        int room = ModConfig.KUVA_LICH.thrallSquadMaxNearby.get() - nearby(player);
        if (room <= 0) {
            s.wait = rollInterval(player);
            return;
        }
        int quota = quotaLeft(player.getUUID(), now);
        if (quota <= 0) {
            s.wait = (int) Math.max(PERIOD, untilQuota(player.getUUID(), now));
            return;
        }
        int min = ModConfig.KUVA_LICH.thrallSquadSizeMin.get();
        int max = Math.max(min, ModConfig.KUVA_LICH.thrallSquadSizeMax.get());
        int size = Math.min(Math.min(room, quota), min + player.getRandom().nextInt(max - min + 1));
        List<KuvaSlaveEntity> squad = spawnSquad(player, size, true);
        if (squad.isEmpty()) {
            // 找不到落点：隔 thrallSquadRetrySeconds（默认 15 秒）再试
            s.wait = ModConfig.KUVA_LICH.thrallSquadRetrySeconds.get() * 20;
            return;
        }
        s.wait = rollInterval(player);
        record(player.getUUID(), now, squad.size());
        shareWithNeighbours(player, now, squad.size());
    }

    /** 他身边有玄骸的其他人：倒数至少重置成一整个间隔，这一队也记进他们的小时额度 */
    private static void shareWithNeighbours(ServerPlayer player, long now, int count) {
        double r = ModConfig.KUVA_LICH.thrallSquadRadius.get();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || other.distanceToSqr(player) > r * r) {
                continue;
            }
            RequiemCard card = card(other);
            if (card == null || !card.hasLich()) {
                continue;
            }
            Squad s = SQUADS.computeIfAbsent(other.getUUID(), id -> new Squad());
            s.wait = Math.max(s.wait, rollInterval(other));
            record(other.getUUID(), now, count);
        }
    }

    /** 不能放的原因，能放返回空串 */
    public static String blocker(ServerPlayer player) {
        return blocker(player, card(player));
    }

    private static String blocker(ServerPlayer player, @Nullable RequiemCard card) {
        String dim = player.level().dimension().location().toString();
        if (!ModConfig.KUVA_LICH.thrallSquadDimensions.get().contains(dim)) {
            return "dimension";
        }
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return "peaceful";
        }
        if (player.isCreative() || player.isSpectator()) {
            return "creative";
        }
        if (!player.isAlive() || MercyServer.isBusy(player)) {
            return "busy";
        }
        if (card != null && card.isLichPaused()) {
            return "paused";
        }
        if (nearSpawn(player.serverLevel(), player.getX(), player.getZ())) {
            return "spawn";
        }
        return "";
    }

    /** 在不在该世界出生点的保护半径内（水平距离）；半径 0 = 不保护 */
    public static boolean nearSpawn(ServerLevel level, double x, double z) {
        int r = ModConfig.KUVA_LICH.thrallSquadSpawnSafeRadius.get();
        if (r <= 0) {
            return false;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = x - (spawn.getX() + 0.5D);
        double dz = z - (spawn.getZ() + 0.5D);
        return dx * dx + dz * dz < (double) r * r;
    }

    /** 他身边 {@code thrallSquadRadius} 格内现有几只奴仆（不算幼体） */
    public static int nearby(ServerPlayer player) {
        return KuvaSpawnLimiter.countNear(player.serverLevel(), KuvaLichEntities.KUVA_SLAVE.get(), player.position(),
                ModConfig.KUVA_LICH.thrallSquadRadius.get());
    }

    /** 管理命令放一队：落点不管出生点保护 */
    public static List<KuvaSlaveEntity> spawnSquad(ServerPlayer player, int size) {
        return spawnSquad(player, size, false);
    }

    /**
     * 在他身边放一队 {@code size} 只：先找一个看不见的落点放第一只，其余在它周围 3 格内找能站的地方，
     * 站不下的就少放。{@code keepOffSpawn} 为真时落点不要出生点保护半径内的。返回放出来的（一只都没放成是空表）
     */
    private static List<KuvaSlaveEntity> spawnSquad(ServerPlayer player, int size, boolean keepOffSpawn) {
        List<KuvaSlaveEntity> out = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        Predicate<Vec3> accept = keepOffSpawn ? at -> !nearSpawn(level, at.x, at.z) : null;
        Vec3 anchor = LichSpawner.findHidden(player, SPAWN_MIN, SPAWN_MAX, TRIES, false, HALF_WIDTH, HEIGHT, accept);
        if (anchor == null) {
            anchor = LichSpawner.findHidden(player, RELAXED_MIN, SPAWN_MAX, TRIES, true, HALF_WIDTH, HEIGHT, accept);
        }
        if (anchor == null) {
            return out;
        }
        for (int i = 0; i < size; i++) {
            Vec3 at = i == 0 ? anchor : near(level, anchor, accept);
            if (at == null) {
                continue;
            }
            KuvaSlaveEntity thrall = KuvaLichEntities.KUVA_SLAVE.get().create(level);
            if (thrall == null) {
                break;
            }
            float yaw = (float) (Mth.atan2(player.getZ() - at.z, player.getX() - at.x) * (180.0D / Math.PI)) - 90.0F;
            thrall.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            thrall.setYHeadRot(yaw);
            thrall.setYBodyRot(yaw);
            thrall.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null, null);
            if (!level.addFreshEntity(thrall)) {
                continue;
            }
            if (!out.isEmpty()) {
                // 一队只响一次登场警示
                thrall.markAlerted();
            }
            thrall.setTarget(player);
            out.add(thrall);
        }
        if (LogUtil.isDebugEnabled() && !out.isEmpty()) {
            LogUtil.debug("[奴仆小队] " + player.getGameProfile().getName() + "：放了 " + out.size() + " 只 @ "
                    + BlockPos.containing(anchor));
        }
        return out;
    }

    @Nullable
    private static Vec3 near(ServerLevel level, Vec3 anchor, @Nullable Predicate<Vec3> accept) {
        for (int i = 0; i < 8; i++) {
            double x = anchor.x + (level.random.nextInt(SPREAD * 2 + 1) - SPREAD);
            double z = anchor.z + (level.random.nextInt(SPREAD * 2 + 1) - SPREAD);
            Vec3 at = LichSpawner.findStanding(level, x, z, Mth.floor(anchor.y) + 2, HALF_WIDTH, HEIGHT);
            if (at != null && Math.abs(at.y - anchor.y) <= 3.0D && (accept == null || accept.test(at))) {
                return at;
            }
        }
        return null;
    }

    // ==================== 小时额度 ====================

    private static void record(UUID id, long now, int count) {
        HOURLY.computeIfAbsent(id, k -> new ArrayDeque<>()).addLast(new long[]{now, count});
    }

    /** 最近一小时（按 {@code now} 往前数）放过来几只，顺手丢掉滑出窗口的 */
    private static int lastHour(UUID id, long now) {
        ArrayDeque<long[]> log = HOURLY.get(id);
        if (log == null) {
            return 0;
        }
        while (!log.isEmpty() && log.peekFirst()[0] <= now - HOUR) {
            log.pollFirst();
        }
        if (log.isEmpty()) {
            HOURLY.remove(id);
            return 0;
        }
        int sum = 0;
        for (long[] e : log) {
            sum += (int) e[1];
        }
        return sum;
    }

    /** 这一小时还能放几只；上限 0 = 不限 */
    private static int quotaLeft(UUID id, long now) {
        int cap = ModConfig.KUVA_LICH.thrallSquadMaxPerHour.get();
        return cap <= 0 ? Integer.MAX_VALUE : cap - lastHour(id, now);
    }

    /** 额度满了：最早那批还要多久滑出窗口 */
    private static long untilQuota(UUID id, long now) {
        ArrayDeque<long[]> log = HOURLY.get(id);
        return log == null || log.isEmpty() ? 0L : log.peekFirst()[0] + HOUR - now;
    }

    // ==================== 对外：命令、测试 ====================

    /** 下一队还有多久（能放的 tick）、身边几只、放不了的原因、本小时几只、离出生点多远；还没开始计时时 nextInTicks 为 -1 */
    public static View view(ServerPlayer player) {
        Squad s = SQUADS.get(player.getUUID());
        long next = s == null || s.wait < 0 ? -1 : s.wait;
        BlockPos spawn = player.serverLevel().getSharedSpawnPos();
        int dist = (int) Math.round(Math.sqrt(Mth.square(player.getX() - (spawn.getX() + 0.5D))
                + Mth.square(player.getZ() - (spawn.getZ() + 0.5D))));
        return new View(next, nearby(player), blocker(player), lastHour(player.getUUID(), now(player.server)), dist);
    }

    /** 把下一队拨到 {@code ticks} 之后（管理命令；不补「刚回来至少等 thrallSquadResumeDelaySeconds」） */
    public static void setNext(ServerPlayer player, int ticks) {
        Squad s = SQUADS.computeIfAbsent(player.getUUID(), id -> new Squad());
        s.wait = Math.max(0, ticks);
        s.blocked = false;
    }

    private static int rollInterval(ServerPlayer player) {
        int min = ModConfig.KUVA_LICH.thrallSquadIntervalMinSeconds.get();
        int max = Math.max(min, ModConfig.KUVA_LICH.thrallSquadIntervalMaxSeconds.get());
        return (min + player.getRandom().nextInt(max - min + 1)) * 20;
    }

    @Nullable
    private static RequiemCard card(ServerPlayer player) {
        return player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
    }

    private static long now(@Nullable MinecraftServer server) {
        return server == null ? 0L : server.overworld().getGameTime();
    }
}
