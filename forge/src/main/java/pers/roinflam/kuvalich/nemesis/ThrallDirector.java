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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 奴仆小队：奴仆不再在野外自然生成，只给有玄骸的玩家放。每人一个计时，到点在他身边 16~28 格外、看不见的地方放一小队，
 * 一队 {@code thrallSquadSizeMin~Max} 只，彼此挨着
 *
 * <p>默认 60~120 秒一队、一队 1~3 只、身边 48 格内最多 6 只：平均一分半钟来两只，处决一只大约 2 秒，
 * 打完一队有一阵空档，不会一队没清完下一队又压上来；不理它们的话也只会攒到 6 只为止。
 * 按平均每只 12.5 点进度、三条密语共 264 点算，专心打大约 15 分钟能揭完，和玄骸怒气只靠在线 20 分钟攒满是一个量级，
 * 一直有得打、又不至于满屏都是。</p>
 *
 * <p>到点时要满足：有玄骸、在 {@code thrallSquadDimensions} 里、难度不是和平、不是创造 / 旁观、活着、没在处决。
 * 不满足就每秒再看一次，计时停在「到点」。身边已经够数就跳过这一轮重新计时，快够数就少放几只。
 * 找不到落点下一秒再试，连着 5 次找不到等 10 秒。放出来的奴仆直接盯上这个人（只有第一只放登场警示），
 * 不常驻：人走远了照原版规矩自然消失。</p>
 *
 * <p>计时用主世界 gameTime，只放内存；下线删掉，上线、刚诞生玄骸时从头计一整个间隔。</p>
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
    private static final int SEARCH_FAILS = 5;
    private static final int SEARCH_BACKOFF = 10 * 20;
    private static final double HALF_WIDTH = 1.5D / 2.0D;
    private static final double HEIGHT = 2.9D;

    private static final Map<UUID, Squad> SQUADS = new HashMap<>();

    private static final class Squad {
        /** 下一队的时刻（主世界 gameTime），-1 = 还没定 */
        long nextAt = -1;
        int fails;
    }

    /** 管理命令、测试看的快照 */
    public record View(long nextInTicks, int nearby, String blocker) {
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
        SQUADS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SQUADS.clear();
    }

    /** 一个玩家的那一份（每 20 tick）；测试直接调 */
    public static void tick(ServerPlayer player, long now) {
        RequiemCard card = card(player);
        if (card == null || !card.hasLich() || !ModConfig.KUVA_LICH.thrallSquadEnabled.get()) {
            // 没有玄骸时不计时：诞生那一刻起才从头数一整个间隔
            SQUADS.remove(player.getUUID());
            return;
        }
        Squad s = SQUADS.computeIfAbsent(player.getUUID(), id -> new Squad());
        if (s.nextAt < 0) {
            s.nextAt = now + rollInterval(player);
            return;
        }
        if (now < s.nextAt || !blocker(player).isEmpty()) {
            return;
        }
        int room = ModConfig.KUVA_LICH.thrallSquadMaxNearby.get() - nearby(player);
        if (room <= 0) {
            s.nextAt = now + rollInterval(player);
            return;
        }
        int min = ModConfig.KUVA_LICH.thrallSquadSizeMin.get();
        int max = Math.max(min, ModConfig.KUVA_LICH.thrallSquadSizeMax.get());
        int size = Math.min(room, min + player.getRandom().nextInt(max - min + 1));
        if (spawnSquad(player, size).isEmpty()) {
            if (++s.fails >= SEARCH_FAILS) {
                s.fails = 0;
                s.nextAt = now + SEARCH_BACKOFF;
            }
            return;
        }
        s.fails = 0;
        s.nextAt = now + rollInterval(player);
    }

    /** 不能放的原因，能放返回空串 */
    public static String blocker(ServerPlayer player) {
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
        return "";
    }

    /** 他身边 {@code thrallSquadRadius} 格内现有几只奴仆（不算幼体） */
    public static int nearby(ServerPlayer player) {
        return KuvaSpawnLimiter.countNear(player.serverLevel(), KuvaLichEntities.KUVA_SLAVE.get(), player.position(),
                ModConfig.KUVA_LICH.thrallSquadRadius.get());
    }

    /**
     * 在他身边放一队 {@code size} 只：先找一个看不见的落点放第一只，其余在它周围 3 格内找能站的地方，
     * 站不下的就少放。返回放出来的（一只都没放成是空表）
     */
    public static List<KuvaSlaveEntity> spawnSquad(ServerPlayer player, int size) {
        List<KuvaSlaveEntity> out = new ArrayList<>();
        Vec3 anchor = LichSpawner.findHidden(player, SPAWN_MIN, SPAWN_MAX, TRIES, false, HALF_WIDTH, HEIGHT);
        if (anchor == null) {
            anchor = LichSpawner.findHidden(player, RELAXED_MIN, SPAWN_MAX, TRIES, true, HALF_WIDTH, HEIGHT);
        }
        if (anchor == null) {
            return out;
        }
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < size; i++) {
            Vec3 at = i == 0 ? anchor : near(level, anchor);
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
    private static Vec3 near(ServerLevel level, Vec3 anchor) {
        for (int i = 0; i < 8; i++) {
            double x = anchor.x + (level.random.nextInt(SPREAD * 2 + 1) - SPREAD);
            double z = anchor.z + (level.random.nextInt(SPREAD * 2 + 1) - SPREAD);
            Vec3 at = LichSpawner.findStanding(level, x, z, Mth.floor(anchor.y) + 2, HALF_WIDTH, HEIGHT);
            if (at != null && Math.abs(at.y - anchor.y) <= 3.0D) {
                return at;
            }
        }
        return null;
    }

    /** 下一队还有多久、身边几只、放不了的原因（命令看状态用）；还没开始计时时 nextInTicks 为 -1 */
    public static View view(ServerPlayer player) {
        Squad s = SQUADS.get(player.getUUID());
        long next = s == null || s.nextAt < 0 ? -1 : Math.max(0, s.nextAt - now(player.server));
        return new View(next, nearby(player), blocker(player));
    }

    /** 把下一队拨到 {@code ticks} 之后（管理命令） */
    public static void setNext(ServerPlayer player, int ticks) {
        SQUADS.computeIfAbsent(player.getUUID(), id -> new Squad()).nextAt = now(player.server) + Math.max(0, ticks);
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
