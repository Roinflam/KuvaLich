package pers.roinflam.kuvalich.nemesis;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.fx.ServerFxQueue;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.network.packet.LichStatePacket;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 专属玄骸的服务端导演：每个玩家一只，管诞生、怒气、到场、离场和结局
 *
 * <p>每个在线玩家一条狩猎记录（{@link Hunt}），只放内存；卡上存的是能跨重启的那部分（名字、种子、怒气、冷却、
 * 「遭遇进行中」记号）。主人不在线时一律不碰他的卡：服务端拿不到他的能力对象，缓存下来的引用写进去也会丢。
 * 这时的账靠卡上的「遭遇进行中」在下次登录补算。</p>
 *
 * <p>时间统一用主世界的 gameTime（各维度的 gameTime 不一致）。每 tick 只推进「通讯已发、等着现身」和
 * 换位、Boss 条血量；其余（怒气上涨、到点检查、离场条件、随机嘲讽、Boss 条成员）每 20 tick 走一遍
 * {@link #tickHunt}。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class LichDirector {

    // ==================== 节奏 ====================

    /** 慢循环的间隔（tick） */
    private static final int SLOW_PERIOD = 20;
    /** 到点了条件不满足时，多久重查一次 */
    private static final int RECHECK_TICKS = 100;
    /** 找落点：每 tick 试几次、先严格找几 tick、一共找几 tick */
    private static final int SEARCH_TRIES_PER_TICK = 24;
    private static final int SEARCH_STRICT_TICKS = 4;
    private static final int SEARCH_TOTAL_TICKS = 8;
    /** 放宽后最近距离 */
    private static final int RELAXED_MIN_DISTANCE = 12;
    /** 落点怎么都找不到：回到「等着来」，多久后再试（通讯不重发） */
    private static final int SPOT_RETRY_TICKS = 200;
    /** 有嘲讽的离场：先发通讯，再过多久开始离场演出，让字幕先出来 */
    private static final int TAUNT_LEAD_TICKS = 12;
    /** 主人死了：多久后嘲讽、多久后离场 */
    private static final int DEATH_TAUNT_DELAY = 10;
    private static final int DEATH_DEPART_DELAY = 60;
    /** 登录后多久推第一个状态包 */
    private static final int LOGIN_STATE_DELAY = 40;
    /** 诞生通讯：正常诞生 +30 tick，老存档补生在登录后 5 秒 */
    private static final int BIRTH_COMM_DELAY = 30;
    private static final int MIGRATION_COMM_DELAY = 100;
    /** Boss 条：别的玩家进 64 格加、出 80 格删 */
    private static final double BOSSBAR_JOIN = 64.0D;
    private static final double BOSSBAR_LEAVE = 80.0D;
    /** 效果包的广播半径 */
    private static final double FX_RADIUS = 64.0D;
    /** 随机嘲讽每 120 秒掷一次；距上次任何通讯至少 60 秒 */
    private static final int TAUNT_ROLL_PERIOD = 2400;
    private static final int TAUNT_COMM_GAP = 1200;
    /** 状态包每人每秒最多 2 个 */
    private static final int STATE_MIN_GAP = 10;
    /** 怒气变化到多少才单独推一次状态包（千分比） */
    private static final int RAGE_PUSH_STEP = 50;
    /** 卡住换位：落点离主人的距离，以及多近不算卡住 */
    private static final int RELOCATE_MIN = 16;
    private static final int RELOCATE_MAX = 24;
    private static final double STUCK_NEAR = 12.0D;
    /** 起名字时最多换几次种子去躲重名 */
    private static final int NAME_TRIES = 16;

    // ==================== 数据 ====================

    /** 每个在线玩家一条，下线删、关服清空 */
    private static final Map<UUID, Hunt> HUNTS = new HashMap<>();

    /** 一个玩家的狩猎记录（只在内存里） */
    static final class Hunt {
        final UUID owner;
        /** 在场的那只（含离场演出中），没有为 null */
        @Nullable
        KuvaMasterEntity lich;
        /** 怒气满了以后定下的到场时刻（主世界 gameTime），-1 = 没在等 */
        long arriveAt = -1;
        /** 通讯已发、现身的时刻，-1 = 不在「将到」 */
        long spawnAt = -1;
        WaitReason waitReason = WaitReason.NONE;
        long nextCheckAt = -1;
        /** 这次到场是怎么来的；null = 怒气满了自己来 */
        @Nullable
        SummonMode arrivalMode;
        @Nullable
        BlockPos preferredFront;
        @Nullable
        ResourceKey<Level> arrivalDim;
        /** 已经找好的落点 */
        @Nullable
        Vec3 spot;
        int searchTicks;
        /** 这一轮到场的通讯已经发过（落点没找到、回去重等时不重发） */
        boolean arrivalCommSent;
        long lastCommAt = Long.MIN_VALUE / 4;
        long lastRandomTauntAt = Long.MIN_VALUE / 4;
        long lastTauntRollAt;
        /** 最近一次和主人有伤害往来、或被任何人打 */
        long lastEngageAt;
        int stuckTicks;
        int repositions;
        @Nullable
        Vec3 relocateSpot;
        long relocateAt = -1;
        @Nullable
        EncounterEnd pendingEnd;
        boolean downed;
        long loginAt;
        @Nullable
        ServerBossEvent bossBar;
        // 状态包
        @Nullable
        LichStatePacket lastSent;
        long lastSentAt = Long.MIN_VALUE / 4;
        long forceStateAt = -1;
        boolean dirty;

        Hunt(UUID owner) {
            this.owner = owner;
        }

        void clearArrival() {
            arriveAt = -1;
            spawnAt = -1;
            waitReason = WaitReason.NONE;
            nextCheckAt = -1;
            arrivalMode = null;
            preferredFront = null;
            arrivalDim = null;
            spot = null;
            searchTicks = 0;
            arrivalCommSent = false;
        }
    }

    /** 给管理命令、测试看的只读快照 */
    public record HuntView(LichState state, WaitReason waitReason, long arriveAt, long spawnAt, int repositions,
                           int stuckTicks, @Nullable KuvaMasterEntity lich, boolean bossBarShown,
                           List<ServerPlayer> bossBarPlayers) {
    }

    private LichDirector() {
    }

    // ==================== 对外入口 ====================

    /**
     * 解密进度增加之后调（每个发解密进度包的地方紧跟着调）：有玄骸时进度和揭示加怒气。
     * 玄骸不再由进度诞生，改成处决赤毒幼体（{@link #birthFromLarvling}）；没有玄骸时这里什么都不做
     */
    public static void onDecryptionProgress(ServerPlayer player, RequiemCard card, int added, int levelBefore) {
        long now = now(player.server);
        int level = card.getUnlockedCardStatus();
        if (levelBefore < 3 && level >= 3) {
            // 三条刚好揭完只提示这一次（顶部解密条之后只显示「已全部揭示」，接下来去哪、做什么靠这一句）
            PlayerFeedback.chat(player, KuvaPalette.ACCENT, "message.kuvalich.maxLevel");
        }
        if (!card.hasLich()) {
            return;
        }
        Hunt h = hunt(player);
        if (rageCanRise(h, card, now)) {
            double add = Math.max(0, added) * ModConfig.KUVA_LICH.lichRagePerProgressPoint.get();
            // 玄骸由处决幼体诞生，三条线索都在诞生之后揭，第一条也算
            if (level > levelBefore) {
                add += (level - levelBefore) * ModConfig.KUVA_LICH.lichRagePerClueRevealed.get();
            }
            card.setLichRage((float) (card.getLichRage() + add));
        }
        h.dirty = true;
    }

    /**
     * 处决了一只赤毒幼体：处决者的玄骸诞生，种子和武器都用幼体刷出来时掷好的（头顶亮出来的就是这把）。
     * 他已经有玄骸了返回 false、什么都不做
     *
     * @param weapon 幼体身上的武器下标；无效（-1）时按种子算
     */
    public static boolean birthFromLarvling(ServerPlayer executor, long seed, int weapon) {
        RequiemCard card = card(executor);
        if (card == null || card.hasLich()) {
            return false;
        }
        birth(executor, card, seed, weapon, ModConfig.KUVA_LICH.lichBirthRage.get().floatValue(), BIRTH_COMM_DELAY,
                now(executor.server));
        return true;
    }

    /** 管理命令、测试用：不经过幼体直接诞生一只（随机种子，武器按种子算），通讯照常。已经有玄骸返回 false */
    public static boolean birthNow(ServerPlayer player) {
        RequiemCard card = card(player);
        if (card == null || card.hasLich()) {
            return false;
        }
        birth(player, card, randomSeed(player), -1, ModConfig.KUVA_LICH.lichBirthRage.get().floatValue(),
                BIRTH_COMM_DELAY, now(player.server));
        return true;
    }

    /** 奴仆被处决完成（怜悯之击关掉时就是被玩家杀死）：处决者有玄骸就加怒气 */
    public static void onThrallExecuted(ServerPlayer executor, KuvaSlaveEntity thrall) {
        RequiemCard card = card(executor);
        if (card == null || !card.hasLich()) {
            return;
        }
        Hunt h = hunt(executor);
        if (rageCanRise(h, card, now(executor.server))) {
            card.setLichRage((float) (card.getLichRage() + ModConfig.KUVA_LICH.lichRagePerThrallExecution.get()));
            h.dirty = true;
        }
    }

    /** 立刻召一只玄骸到主人身边；没有玄骸、已经在场或正在来的路上返回 false */
    public static boolean summonNow(ServerPlayer owner, SummonMode mode) {
        return summonNow(owner, mode, null);
    }

    /**
     * 同上；{@code preferredFeet} 是面前落点优先试的那一格（安魂通牒对着方块用时，点击面外侧那一格），可以为空。
     *
     * <p>{@link SummonMode#FRONT} 当场生成，召不出来返回 false；{@link SummonMode#ULTIMATUM} 先发通讯、
     * {@link SummonMode#HUNT} 走正常的到场流程，都是安排好就返回 true。三种都无视冷却、怒气和到场条件。</p>
     */
    public static boolean summonNow(ServerPlayer owner, SummonMode mode, @Nullable BlockPos preferredFeet) {
        RequiemCard card = card(owner);
        if (card == null || !card.hasLich()) {
            return false;
        }
        Hunt h = hunt(owner);
        if (liveLich(h) != null || h.spawnAt >= 0) {
            return false;
        }
        long now = now(owner.server);
        h.clearArrival();
        startArrival(owner, h, mode, preferredFeet, now);
        if (mode == SummonMode.FRONT) {
            tickArriving(owner, h, card, now);
            if (h.lich == null) {
                h.clearArrival();
                h.dirty = true;
                return false;
            }
        }
        return true;
    }

    /** 一次处决（或击杀时直接判定）的结果出来了：记一行尝试 */
    public static void onMercyResult(ServerPlayer owner, KuvaMasterEntity lich, int packedAttempt, boolean success) {
        RequiemCard card = card(owner);
        if (card == null) {
            return;
        }
        card.recordAttempt(packedAttempt);
        hunt(owner).dirty = true;
    }

    /**
     * 结束这一次遭遇。同一只玄骸只结算一次（重复调直接返回）。
     *
     * <ol>
     *   <li>主人在线就结算他的卡：怒气清零、「遭遇进行中」清掉、冷却从现在算（管理命令让它走的不进冷却）；</li>
     *   <li>要嘲讽的先发嘲讽，过一会儿再开始离场演出；不要演出的当场移除（实体已经在死的不动，交给死亡流程）。</li>
     * </ol>
     *
     * @param owner 主人；不在线时传 null，这时只放演出、不碰卡
     */
    public static void endEncounter(@Nullable ServerPlayer owner, KuvaMasterEntity lich, EncounterEnd reason,
                                    boolean playDepartFx) {
        if (lich.level().isClientSide || lich.getEncounterEnd() != null) {
            return;
        }
        lich.setEncounterEnd(reason);
        Hunt h = huntOf(lich);
        if (h != null && h.lich == lich) {
            h.pendingEnd = reason;
            h.relocateAt = -1;
            h.relocateSpot = null;
            h.dirty = true;
        }
        if (owner != null && reason != EncounterEnd.VANQUISHED) {
            settle(owner, reason);
        }
        debug(owner, "遭遇结束：" + reason + (playDepartFx ? "（放离场演出）" : ""));

        LichLine taunt = owner == null ? null : tauntFor(reason);
        boolean death = reason == EncounterEnd.OWNER_DIED || reason == EncounterEnd.OWNER_DIED_OTHER;
        int departDelay = 0;
        if (taunt != null) {
            if (death) {
                ServerPlayer target = owner;
                ServerFxQueue.schedule(DEATH_TAUNT_DELAY, owner, () -> sayAndMark(target, taunt));
                departDelay = DEATH_DEPART_DELAY;
            } else {
                sayAndMark(owner, taunt);
                departDelay = TAUNT_LEAD_TICKS;
            }
        }
        if (playDepartFx) {
            if (departDelay <= 0) {
                lich.beginDeparture(reason);
            } else {
                ServerFxQueue.schedule(departDelay, owner, () -> lich.beginDeparture(reason));
            }
        } else if (lich.isAlive() && !lich.isRemoved()) {
            lich.discard();
        }
    }

    /** 玄骸被成功终结（在卡重置之前调）：这一轮结束、记进历史，撤 Boss 条 */
    public static void onVanquished(ServerPlayer owner, KuvaMasterEntity lich) {
        lich.setEncounterEnd(EncounterEnd.VANQUISHED);
        RequiemCard card = card(owner);
        if (card != null) {
            card.endLichRound(RequiemCard.FATE_VANQUISHED);
        }
        Hunt h = hunt(owner);
        if (h.lich == lich) {
            h.pendingEnd = EncounterEnd.VANQUISHED;
            closeBossBar(h);
        }
        h.clearArrival();
        h.dirty = true;
        debug(owner, "玄骸被终结");
    }

    /** 这个玩家当前在场的玄骸（含离场演出中的） */
    public static Optional<KuvaMasterEntity> getLich(ServerPlayer owner) {
        Hunt h = HUNTS.get(owner.getUUID());
        return Optional.ofNullable(h == null ? null : liveLich(h));
    }

    /** 这个玩家的玄骸现在处于什么状态 */
    public static LichState stateOf(ServerPlayer owner) {
        RequiemCard card = card(owner);
        if (card == null) {
            return LichState.UNBORN;
        }
        Hunt h = HUNTS.get(owner.getUUID());
        return computeState(h == null ? new Hunt(owner.getUUID()) : h, card, now(owner.server));
    }

    /** 管理命令、测试用的只读快照；这个玩家没有狩猎记录时返回 null */
    @Nullable
    public static HuntView view(ServerPlayer owner) {
        Hunt h = HUNTS.get(owner.getUUID());
        RequiemCard card = card(owner);
        if (h == null || card == null) {
            return null;
        }
        List<ServerPlayer> bossPlayers = h.bossBar == null ? List.of() : List.copyOf(h.bossBar.getPlayers());
        return new HuntView(computeState(h, card, now(owner.server)), h.waitReason, h.arriveAt, h.spawnAt,
                h.repositions, h.stuckTicks, liveLich(h), h.bossBar != null && h.bossBar.isVisible(), bossPlayers);
    }

    /** 玄骸实体被移出世界时由实体回调：没结算过的按移除原因补结算，然后清掉记录里的引用 */
    public static void onLichRemoved(KuvaMasterEntity lich, @Nullable Entity.RemovalReason reason) {
        if (lich.getEncounterEnd() == null) {
            EncounterEnd end = endForRemoval(lich, reason);
            lich.setEncounterEnd(end);
            ServerPlayer owner = lich.getOwnerPlayer();
            if (owner != null) {
                settle(owner, end);
            }
            debug(owner, "玄骸被移出世界（" + reason + "），按 " + end + " 结算");
        }
        Hunt h = huntOf(lich);
        if (h != null && h.lich == lich) {
            closeBossBar(h);
            h.lich = null;
            h.pendingEnd = null;
            h.downed = false;
            h.repositions = 0;
            h.stuckTicks = 0;
            h.relocateAt = -1;
            h.relocateSpot = null;
            h.dirty = true;
        }
    }

    /**
     * 玄骸进入或离开倒地：改状态、推状态包。倒地期间这段血会慢慢回满，Boss 条照常跟着血量走；
     * 站起来时把「最近交手」记到现在，倒地那段不算晾着
     */
    public static void onDownedChanged(KuvaMasterEntity lich, boolean downed) {
        Hunt h = huntOf(lich);
        if (h != null && h.lich == lich) {
            h.downed = downed;
            if (!downed) {
                h.lastEngageAt = now(lich.getServer());
            }
            h.dirty = true;
        }
    }

    /** 离场演出开始了（实体回调）：Boss 条整条撤掉 */
    public static void onDepartureStarted(KuvaMasterEntity lich) {
        Hunt h = huntOf(lich);
        if (h != null && h.lich == lich) {
            closeBossBar(h);
            h.dirty = true;
        }
    }

    /**
     * 登录：老存档补生、上次掉线 / 关服时在场的那次遭遇按「中断」补结算，登录后第 40 tick 推第一个状态包。
     * 事件里自动调；测试和管理命令也可以直接调
     */
    public static void onLogin(ServerPlayer player) {
        long now = now(player.server);
        Hunt h = HUNTS.computeIfAbsent(player.getUUID(), Hunt::new);
        h.loginAt = now;
        h.lastTauntRollAt = now;
        h.forceStateAt = now + LOGIN_STATE_DELAY;
        RequiemCard card = card(player);
        if (card == null) {
            return;
        }
        if (card.getUnlockedCardStatus() >= 1 && !card.hasLich()) {
            // 揭开过密语却没有玄骸：玄骸出现之前的老存档，或者这一轮被管理命令作废了（现在没有玄骸拿不到解密进度），照旧直接补一只
            birth(player, card, randomSeed(player), -1, ModConfig.KUVA_LICH.lichMigrationRage.get().floatValue(),
                    MIGRATION_COMM_DELAY, now);
        }
        if (card.isLichEncounterOpen()) {
            card.setLichRage(0.0F);
            card.setLichEncounterOpen(false);
            card.setLichCooldownUntil(now + cooldownTicks());
            debug(player, "上次遭遇没结束就掉线 / 关服了，按中断补结算");
        }
        h.dirty = true;
    }

    /**
     * 慢循环（每 20 tick）里一个玩家的那一份：怒气被动上涨、冷却到期、怒气满了定到场时刻、到点检查条件、
     * 离场条件、卡住换位、随机嘲讽、Boss 条成员。服务端 tick 里自动调；测试也可以直接调来快进
     */
    public static void tickHunt(ServerPlayer player, long now) {
        RequiemCard card = card(player);
        if (card == null) {
            return;
        }
        Hunt h = hunt(player);
        if (!card.hasLich()) {
            markIfChanged(player, h, card, now);
            return;
        }
        KuvaMasterEntity lich = liveLich(h);
        if (lich != null) {
            if (h.pendingEnd == null && !lich.isDeparting()) {
                checkDeparture(player, h, lich, now);
            }
            if (h.lich != null && h.bossBar != null) {
                updateBossMembers(player, h, h.lich);
            }
        } else if (h.spawnAt < 0) {
            boolean paused = card.isLichPaused();
            boolean cooling = now < card.getLichCooldownUntil();
            if (!paused) {
                double minutes = ModConfig.KUVA_LICH.lichRageOnlineMinutes.get();
                if (minutes > 0.0D && card.getLichRage() < 1.0F) {
                    card.setLichRage((float) (card.getLichRage() + SLOW_PERIOD / 20.0D / (minutes * 60.0D)));
                }
            }
            if (paused || cooling || card.getLichRage() < 1.0F) {
                if (h.arriveAt >= 0) {
                    h.clearArrival();
                    h.dirty = true;
                }
                maybeRandomTaunt(player, h, card, now);
            } else if (h.arriveAt < 0) {
                int min = ModConfig.KUVA_LICH.lichArrivalDelayMinSeconds.get();
                int max = Math.max(min, ModConfig.KUVA_LICH.lichArrivalDelayMaxSeconds.get());
                h.arriveAt = now + (min + player.getRandom().nextInt(max - min + 1)) * 20L;
                h.nextCheckAt = -1;
                h.dirty = true;
                debug(player, "怒气满了，" + (h.arriveAt - now) / 20 + " 秒后来");
            } else if (now >= h.arriveAt && now >= h.nextCheckAt) {
                WaitReason wait = waitReason(player, h, now);
                if (wait != h.waitReason) {
                    h.waitReason = wait;
                    h.dirty = true;
                }
                if (wait == WaitReason.NONE) {
                    startArrival(player, h, null, null, now);
                } else {
                    h.nextCheckAt = now + RECHECK_TICKS;
                    debug(player, "到点了但在等：" + wait);
                }
            }
        }
        markIfChanged(player, h, card, now);
    }

    // ==================== 事件 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || HUNTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = now(server);
        boolean slow = server.getTickCount() % SLOW_PERIOD == 0;
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            Hunt h = HUNTS.get(player.getUUID());
            if (h == null) {
                continue;
            }
            try {
                tickFast(player, h, now);
                if (slow) {
                    tickHunt(player, now);
                }
                flushState(player, h, now);
            } catch (RuntimeException e) {
                LogUtil.error("玄骸导演处理玩家 " + player.getGameProfile().getName() + " 时出错", e);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            onLogin(player);
        }
    }

    /** 下线：在场的那只按「主人下线」离场（演出给附近别的人看），记录删掉；卡不碰，下次登录补算 */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Hunt h = HUNTS.remove(player.getUUID());
        if (h == null) {
            return;
        }
        KuvaMasterEntity lich = liveLich(h);
        closeBossBar(h);
        if (lich != null) {
            endEncounter(null, lich, EncounterEnd.OWNER_LOGOUT, true);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Hunt h = HUNTS.get(player.getUUID());
        if (h == null) {
            return;
        }
        KuvaMasterEntity lich = liveLich(h);
        if (lich != null && h.pendingEnd == null) {
            endEncounter(player, lich, EncounterEnd.OWNER_DIMENSION, true);
        }
        h.dirty = true;
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Hunt h = HUNTS.get(event.getEntity().getUUID());
        if (h != null) {
            h.dirty = true;
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Hunt h = HUNTS.get(event.getEntity().getUUID());
        if (h != null) {
            h.dirty = true;
        }
    }

    /** 主人死了：凶手是它 → 「把你打死」，别的东西 → 「你死了」；嘲讽 +0.5 秒，离场 +3 秒 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Hunt h = HUNTS.get(player.getUUID());
        if (h == null) {
            return;
        }
        KuvaMasterEntity lich = liveLich(h);
        if (lich == null || h.pendingEnd != null) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        endEncounter(player, lich, killer == lich ? EncounterEnd.OWNER_DIED : EncounterEnd.OWNER_DIED_OTHER, true);
    }

    /**
     * 交手记录：它挨了任何人的打，或者它打了主人，都算「有往来」，晾太久才离场。
     * 只认有攻击者的伤害：岩浆、着火、窒息、溺水、仙人掌这类环境伤害不算交手，不然卡在火里、
     * 被沙砾闷住的玄骸永远判不成晾着。被人点着以后的持续燃烧没有攻击者，点火那一下已经记过了
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof KuvaMasterEntity lich && lich.isOwned()
                && event.getSource().getEntity() != null) {
            Hunt h = huntOf(lich);
            if (h != null && h.lich == lich) {
                h.lastEngageAt = now(lich.getServer());
            }
        } else if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof KuvaMasterEntity lich) {
            Hunt h = HUNTS.get(player.getUUID());
            if (h != null && h.lich == lich) {
                h.lastEngageAt = now(player.server);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (Hunt h : HUNTS.values()) {
            closeBossBar(h);
        }
        HUNTS.clear();
    }

    // ==================== 诞生 ====================

    /** 新掷一个种子（管理命令、老存档补生用；幼体刷出来时也用它） */
    public static long randomSeed(ServerPlayer player) {
        return player.getRandom().nextLong() ^ System.nanoTime();
    }

    /**
     * 诞生：种子是这一只的身份（性格、面板、嗓音都由它算），不改；名字先按种子起，重名时只换起名用的种子，
     * 种子和武器都不动——幼体倒地时头顶亮出来的武器，诞生后手里拿的必须还是那把
     *
     * @param weapon 武器下标；无效（-1）时按种子算
     */
    private static void birth(ServerPlayer player, RequiemCard card, long seed, int weapon, float rage, int commDelay,
                              long now) {
        Set<String> taken = takenNames(player, card);
        String name = null;
        long nameSeed = seed;
        for (int i = 0; i < NAME_TRIES; i++) {
            String candidate = LichNames.generate(nameSeed);
            if (LichNames.isAllowed(candidate) && !taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                name = candidate;
                break;
            }
            nameSeed = mixSeed(nameSeed);
        }
        if (name == null) {
            // 躲不开重名时就用最后一个名字：名字只是显示，种子才是这一只的身份
            name = LichNames.generate(nameSeed);
        }
        int weaponCount = KuvaLichItems.KUVA_WEAPONS.size();
        if (weapon < 0 || weapon >= weaponCount) {
            weapon = LichProfile.weaponIndex(seed, weaponCount);
        }
        ServerLevel level = player.serverLevel();
        String biome = level.getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        String dim = level.dimension().location().toString();
        card.beginLich(name, seed, weapon, biome, dim);
        card.setLichRage(rage);

        Hunt h = hunt(player);
        h.clearArrival();
        h.dirty = true;
        debug(player, "诞生玄骸 " + name + "（第 " + card.getLichGeneration() + " 只，种子 " + seed + "）");

        ServerFxQueue.schedule(commDelay, player, () -> {
            if (!player.hasDisconnected()) {
                // Lotus 那条和玄骸那条同一时刻发：客户端排队，Lotus 说完空一会儿再开玄骸的
                LichComms.say(player, LichLine.LOTUS_LICH_BORN);
                sayAndMark(player, LichLine.BIRTH);
            }
        });
    }

    /** 在线玩家当前的玄骸名、这个玩家最近几只的名字（小写） */
    private static Set<String> takenNames(ServerPlayer player, RequiemCard card) {
        Set<String> taken = new HashSet<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other == player) {
                continue;
            }
            RequiemCard c = card(other);
            if (c != null && c.hasLich()) {
                taken.add(c.getLichName().toLowerCase(Locale.ROOT));
            }
        }
        for (RequiemCard.HistoryEntry entry : card.getHistory()) {
            taken.add(entry.name().toLowerCase(Locale.ROOT));
        }
        return taken;
    }

    private static long mixSeed(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // ==================== 到场 ====================

    /** 到点了：发到场通讯（或通牒通讯），一小会儿后现身；FRONT 不发通讯、当场现身 */
    private static void startArrival(ServerPlayer player, Hunt h, @Nullable SummonMode mode,
                                     @Nullable BlockPos preferred, long now) {
        boolean commAlreadySent = h.arrivalCommSent;
        h.arriveAt = -1;
        h.nextCheckAt = -1;
        h.waitReason = WaitReason.NONE;
        h.arrivalMode = mode;
        h.preferredFront = preferred;
        h.arrivalDim = player.level().dimension();
        h.spot = null;
        h.searchTicks = 0;
        if (mode == SummonMode.FRONT || commAlreadySent) {
            h.spawnAt = now;
        } else {
            sayAndMark(player, mode == SummonMode.ULTIMATUM ? LichLine.ULTIMATUM : LichLine.ARRIVE);
            h.arrivalCommSent = true;
            h.spawnAt = now + ModConfig.KUVA_LICH.lichCommLeadTicks.get();
        }
        h.dirty = true;
        debug(player, "将到（" + (mode == null ? "狩猎" : mode) + "），" + (h.spawnAt - now) + " tick 后现身");
    }

    /** 每 tick：通讯已发、找落点、到点生成 */
    private static void tickArriving(ServerPlayer player, Hunt h, RequiemCard card, long now) {
        if (!card.hasLich()) {
            h.clearArrival();
            h.dirty = true;
            return;
        }
        if (!player.isAlive() || (h.arrivalDim != null && player.level().dimension() != h.arrivalDim)) {
            // 人死了、换了维度：回去重等，通讯不重发
            backToIncoming(player, h, now, 20);
            return;
        }
        boolean front = h.arrivalMode == SummonMode.FRONT || h.arrivalMode == SummonMode.ULTIMATUM;
        int min = ModConfig.KUVA_LICH.lichSpawnMinDistance.get();
        int max = Math.max(min, ModConfig.KUVA_LICH.lichSpawnMaxDistance.get());
        if (!front && h.spot == null && h.searchTicks < SEARCH_TOTAL_TICKS) {
            boolean relaxed = h.searchTicks >= SEARCH_STRICT_TICKS;
            h.spot = LichSpawner.findHidden(player, relaxed ? Math.min(min, RELAXED_MIN_DISTANCE) : min, max,
                    SEARCH_TRIES_PER_TICK, relaxed);
            h.searchTicks++;
        }
        if (now < h.spawnAt) {
            return;
        }
        Vec3 at;
        boolean summonFx = front;
        if (front) {
            at = LichSpawner.findFront(player, h.preferredFront, SEARCH_TRIES_PER_TICK * 2);
            if (at == null) {
                at = LichSpawner.findHidden(player, min, max, SEARCH_TRIES_PER_TICK * 4, false);
            }
            if (at == null) {
                at = LichSpawner.findHidden(player, Math.min(min, RELAXED_MIN_DISTANCE), max,
                        SEARCH_TRIES_PER_TICK * 4, true);
            }
            if (at == null) {
                // 面前和周围都站不下：改走狩猎那套慢慢找
                h.arrivalMode = null;
                h.searchTicks = 0;
                return;
            }
        } else {
            at = h.spot == null ? null : LichSpawner.standingAt(player.serverLevel(), BlockPos.containing(h.spot));
            if (at == null) {
                h.spot = null;
                if (h.searchTicks >= SEARCH_TOTAL_TICKS) {
                    debug(player, "找不到落点，" + SPOT_RETRY_TICKS / 20 + " 秒后重来");
                    backToIncoming(player, h, now, SPOT_RETRY_TICKS);
                }
                return;
            }
        }
        spawnLich(player, h, card, at, summonFx, now);
    }

    private static void backToIncoming(ServerPlayer player, Hunt h, long now, int delay) {
        boolean commSent = h.arrivalCommSent;
        h.clearArrival();
        h.arrivalCommSent = commSent;
        h.arriveAt = now + delay;
        h.dirty = true;
    }

    private static void spawnLich(ServerPlayer player, Hunt h, RequiemCard card, Vec3 at, boolean summonFx, long now) {
        ServerLevel level = player.serverLevel();
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(level);
        if (lich == null) {
            return;
        }
        float yaw = (float) (Mth.atan2(player.getZ() - at.z, player.getX() - at.x) * (180.0D / Math.PI)) - 90.0F;
        lich.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        lich.setYHeadRot(yaw);
        lich.setYBodyRot(yaw);
        lich.bindOwner(player, card);
        lich.beginAppearGrace(KuvaMasterEntity.APPEAR_GRACE_TICKS);
        if (!level.addFreshEntity(lich)) {
            debug(player, "玄骸没能加进世界");
            return;
        }
        h.clearArrival();
        h.lich = lich;
        h.pendingEnd = null;
        h.downed = false;
        h.lastEngageAt = now;
        h.stuckTicks = 0;
        h.repositions = 0;
        h.relocateAt = -1;
        h.relocateSpot = null;
        card.setLichEncounterOpen(true);
        card.incLichEncounters();

        Vec3 chest = at.add(0.0D, lich.getBbHeight() * 0.62D, 0.0D);
        if (summonFx) {
            KuvaFx.near(level, chest, FX_RADIUS, FxId.LICH_SUMMON, lich, player.getId() + 1, 0);
        } else {
            KuvaFx.near(level, at, FX_RADIUS, FxId.LICH_ARRIVE, lich, 0, 0);
        }
        openBossBar(player, h, lich);
        h.dirty = true;
        debug(player, "玄骸现身 @ " + BlockPos.containing(at) + "，离主人 " + Math.round(player.position().distanceTo(at)) + " 格");
    }

    /** 到点时的条件：维度、难度、游戏模式、忙不忙、登录宽限 */
    private static WaitReason waitReason(ServerPlayer player, Hunt h, long now) {
        String dim = player.level().dimension().location().toString();
        if (!ModConfig.KUVA_LICH.lichHuntDimensions.get().contains(dim)) {
            return WaitReason.DIMENSION;
        }
        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return WaitReason.PEACEFUL;
        }
        if (!ModConfig.KUVA_LICH.lichHuntCreative.get() && (player.isCreative() || player.isSpectator())) {
            return WaitReason.CREATIVE;
        }
        if (player.isSleeping() || player.isPassenger() || !player.isAlive() || MercyServer.isBusy(player)) {
            return WaitReason.BUSY;
        }
        if (now - h.loginAt < ModConfig.KUVA_LICH.lichLoginGraceSeconds.get() * 20L) {
            return WaitReason.GRACE;
        }
        return WaitReason.NONE;
    }

    // ==================== 在场：离场条件、换位、Boss 条 ====================

    private static void tickFast(ServerPlayer player, Hunt h, long now) {
        KuvaMasterEntity lich = liveLich(h);
        if (lich != null) {
            if (h.relocateAt >= 0 && now >= h.relocateAt) {
                finishRelocate(player, h, lich);
            }
            if (h.bossBar != null) {
                h.bossBar.setProgress(Mth.clamp(lich.getHealth() / Math.max(1.0F, lich.getMaxHealth()), 0.0F, 1.0F));
            }
            return;
        }
        if (h.spawnAt >= 0) {
            RequiemCard card = card(player);
            if (card != null) {
                tickArriving(player, h, card, now);
            }
        }
    }

    private static void checkDeparture(ServerPlayer player, Hunt h, KuvaMasterEntity lich, long now) {
        if (lich.level() != player.level()) {
            endEncounter(player, lich, EncounterEnd.OWNER_DIMENSION, true);
            return;
        }
        double leash = ModConfig.KUVA_LICH.lichLeashDistance.get();
        if (player.distanceToSqr(lich) > leash * leash) {
            endEncounter(player, lich, EncounterEnd.OWNER_FAR, true);
            return;
        }
        if (lich.level().getDifficulty() == Difficulty.PEACEFUL) {
            endEncounter(player, lich, EncounterEnd.PEACEFUL, true);
            return;
        }
        if (h.downed) {
            // 倒地的不会走、也不会和人交手，计时交给怜悯之击那边
            h.stuckTicks = 0;
            return;
        }
        int idle = ModConfig.KUVA_LICH.lichIdleDepartSeconds.get();
        if (idle > 0 && now - h.lastEngageAt > idle * 20L) {
            endEncounter(player, lich, EncounterEnd.IDLE, true);
            return;
        }
        int repositionSeconds = ModConfig.KUVA_LICH.lichRepositionSeconds.get();
        if (repositionSeconds <= 0 || lich.isAppearing() || h.relocateAt >= 0) {
            h.stuckTicks = 0;
            return;
        }
        if (isStuck(player, lich)) {
            h.stuckTicks += SLOW_PERIOD;
            if (h.stuckTicks >= repositionSeconds * 20 && h.repositions < ModConfig.KUVA_LICH.lichRepositionMax.get()) {
                relocate(player, h, lich, now);
            }
        } else {
            h.stuckTicks = 0;
        }
    }

    /** 离主人 12 格外、到主人的路断了（寻路走完了还没到，或者路径到不了他跟前） */
    private static boolean isStuck(ServerPlayer player, KuvaMasterEntity lich) {
        if (lich.distanceToSqr(player) <= STUCK_NEAR * STUCK_NEAR) {
            return false;
        }
        PathNavigation nav = lich.getNavigation();
        Path path = nav.getPath();
        if (path == null || nav.isDone()) {
            Path probe = nav.createPath(player, 0);
            return probe == null || !probe.canReach();
        }
        return !path.canReach();
    }

    /** 卡住了：借赤毒雾消失，下一 tick 在主人 16~24 格外看不见的地方出现。不撤 Boss 条、不结算、不发通讯 */
    private static void relocate(ServerPlayer player, Hunt h, KuvaMasterEntity lich, long now) {
        Vec3 spot = LichSpawner.findHidden(player, RELOCATE_MIN, RELOCATE_MAX, SEARCH_TRIES_PER_TICK * 4, false);
        if (spot == null) {
            spot = LichSpawner.findHidden(player, RELAXED_MIN_DISTANCE, RELOCATE_MAX, SEARCH_TRIES_PER_TICK * 4, true);
        }
        if (spot == null) {
            h.stuckTicks = 0;
            return;
        }
        if (lich.level() instanceof ServerLevel level) {
            KuvaFx.near(level, lich.position().add(0.0D, lich.getBbHeight() * 0.62D, 0.0D), FX_RADIUS,
                    FxId.LICH_DEPART, lich, 99, 0);
        }
        lich.getNavigation().stop();
        h.relocateSpot = spot;
        h.relocateAt = now + 1;
        debug(player, "卡住了，换位到 " + BlockPos.containing(spot));
    }

    private static void finishRelocate(ServerPlayer player, Hunt h, KuvaMasterEntity lich) {
        Vec3 spot = h.relocateSpot;
        h.relocateAt = -1;
        h.relocateSpot = null;
        h.stuckTicks = 0;
        if (spot == null || lich.level() != player.level() || h.pendingEnd != null) {
            return;
        }
        lich.teleportTo(spot.x, spot.y, spot.z);
        lich.getNavigation().stop();
        lich.beginAppearGrace(KuvaMasterEntity.APPEAR_GRACE_TICKS);
        h.repositions++;
        if (lich.level() instanceof ServerLevel level) {
            KuvaFx.near(level, spot, FX_RADIUS, FxId.LICH_ARRIVE, lich, 1, 0);
        }
    }

    private static void openBossBar(ServerPlayer owner, Hunt h, KuvaMasterEntity lich) {
        closeBossBar(h);
        ServerBossEvent bar = new ServerBossEvent(bossName(owner, lich), BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        bar.setCreateWorldFog(false);
        bar.setProgress(Mth.clamp(lich.getHealth() / Math.max(1.0F, lich.getMaxHealth()), 0.0F, 1.0F));
        h.bossBar = bar;
        updateBossMembers(owner, h, lich);
    }

    /**
     * 名字参数：全大写的玄骸名、等级、主人名、主人 UUID（客户端比对 UUID 决定是不是「别人的玄骸」）。
     * 原版画法下只显示第一个参数
     */
    private static Component bossName(ServerPlayer owner, KuvaMasterEntity lich) {
        return Component.translatable("kuvalich.bossbar.lich", lich.getLichName().toUpperCase(Locale.ROOT),
                lich.getLichRank(), owner.getGameProfile().getName(), owner.getUUID().toString());
    }

    /** 主人一直在；别的玩家进 64 格加、出 80 格删，下线的、不在同一个世界的删 */
    private static void updateBossMembers(ServerPlayer owner, Hunt h, KuvaMasterEntity lich) {
        ServerBossEvent bar = h.bossBar;
        if (bar == null) {
            return;
        }
        bar.setName(bossName(owner, lich));
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
            if (p == owner) {
                continue;
            }
            if (p.hasDisconnected() || p.level() != lich.level()
                    || p.distanceToSqr(lich) > BOSSBAR_LEAVE * BOSSBAR_LEAVE) {
                bar.removePlayer(p);
            }
        }
        if (!owner.hasDisconnected() && !bar.getPlayers().contains(owner)) {
            bar.addPlayer(owner);
        }
        if (lich.level() instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                if (p != owner && !bar.getPlayers().contains(p)
                        && p.distanceToSqr(lich) <= BOSSBAR_JOIN * BOSSBAR_JOIN) {
                    bar.addPlayer(p);
                }
            }
        }
    }

    private static void closeBossBar(Hunt h) {
        if (h.bossBar != null) {
            h.bossBar.removeAllPlayers();
            h.bossBar.setVisible(false);
            h.bossBar = null;
        }
    }

    // ==================== 结算、嘲讽 ====================

    /** 遭遇结束时主人的卡：怒气清零、遭遇记号清掉、进冷却（管理命令让它走的不进冷却） */
    private static void settle(ServerPlayer owner, EncounterEnd reason) {
        RequiemCard card = card(owner);
        if (card == null) {
            return;
        }
        card.setLichRage(0.0F);
        card.setLichEncounterOpen(false);
        if (reason != EncounterEnd.ADMIN) {
            card.setLichCooldownUntil(now(owner.server) + cooldownTicks());
        }
        Hunt h = hunt(owner);
        boolean keepLich = h.lich != null;
        h.clearArrival();
        h.stuckTicks = 0;
        h.dirty = true;
        if (!keepLich) {
            h.repositions = 0;
        }
    }

    /** 离场原因对应的嘲讽；处决失败、遗言另有发送方，没有嘲讽的返回 null */
    @Nullable
    private static LichLine tauntFor(EncounterEnd reason) {
        return switch (reason) {
            case OWNER_DIMENSION, OWNER_FAR, IDLE -> LichLine.DEPART;
            case OWNER_DIED -> LichLine.KILLED_YOU;
            case OWNER_DIED_OTHER -> LichLine.YOU_DIED;
            case UNCLAIMED -> LichLine.UNCLAIMED;
            default -> null;
        };
    }

    private static void sayAndMark(ServerPlayer to, LichLine line) {
        if (LichComms.say(to, line)) {
            Hunt h = HUNTS.get(to.getUUID());
            if (h != null) {
                h.lastCommAt = now(to.server);
            }
        }
    }

    /**
     * 随机嘲讽：只在没来找你的时候（跟踪、冷却中）、怒气至少「恼怒」、没暂停；每 120 秒掷一次，
     * 概率按档位放大，且离上次随机嘲讽够久、离上次任何通讯至少 60 秒
     */
    private static void maybeRandomTaunt(ServerPlayer player, Hunt h, RequiemCard card, long now) {
        double minutes = ModConfig.KUVA_LICH.lichRandomTauntMinutes.get();
        if (minutes <= 0.0D || card.isLichPaused()) {
            return;
        }
        RageTier tier = RageTier.of(card.getLichRage());
        if (tier.ordinal() < RageTier.IRRITATED.ordinal() || now - h.lastTauntRollAt < TAUNT_ROLL_PERIOD) {
            return;
        }
        h.lastTauntRollAt = now;
        if (now - h.lastRandomTauntAt < Math.round(minutes * 1200.0D) || now - h.lastCommAt < TAUNT_COMM_GAP) {
            return;
        }
        double factor = switch (tier) {
            case IRRITATED -> 0.6D;
            case SEETHING -> 1.0D;
            case ENRAGED -> 1.4D;
            case LIVID -> 1.8D;
            default -> 0.0D;
        };
        if (player.getRandom().nextDouble() >= ModConfig.KUVA_LICH.lichRandomTauntChance.get() * factor) {
            return;
        }
        LichLine line = tier.ordinal() >= RageTier.ENRAGED.ordinal() ? LichLine.IDLE_FURIOUS : LichLine.IDLE;
        if (LichComms.say(player, line)) {
            h.lastRandomTauntAt = now;
            h.lastCommAt = now;
        }
    }

    // ==================== 状态包 ====================

    private static LichState computeState(Hunt h, RequiemCard card, long now) {
        if (!card.hasLich()) {
            return LichState.UNBORN;
        }
        KuvaMasterEntity lich = liveLich(h);
        if (lich != null) {
            if (h.pendingEnd != null || lich.isDeparting()) {
                return LichState.DEPARTING;
            }
            return h.downed ? LichState.DOWNED : LichState.PRESENT;
        }
        if (h.spawnAt >= 0) {
            return LichState.ARRIVING;
        }
        if (now < card.getLichCooldownUntil()) {
            return LichState.COOLDOWN;
        }
        if (!card.isLichPaused() && card.getLichRage() >= 1.0F) {
            return LichState.INCOMING;
        }
        return LichState.STALKING;
    }

    private static LichStatePacket buildState(Hunt h, RequiemCard card, long now) {
        LichState state = computeState(h, card, now);
        long cooldownLeft = card.hasLich() ? card.getLichCooldownUntil() - now : 0;
        int cooldownSec = cooldownLeft > 0 ? (int) Math.min(Integer.MAX_VALUE, (cooldownLeft + 19) / 20) : 0;
        return new LichStatePacket(state, state == LichState.INCOMING ? h.waitReason : WaitReason.NONE,
                card.getLichName(), (int) card.getLichSeed(), card.getLichWeapon(),
                card.hasLich() ? card.getLichRank() : 0, Math.round(card.getLichRage() * 1000.0F),
                card.hasLich() ? card.getRageTier() : 0, card.getUnlockedCardStatus(), cooldownSec,
                card.getLichGeneration(), card.isLichPaused());
    }

    /** 和上次发出去的比：状态、名字、等级、档位、线索、冷却开始 / 结束变了，或怒气变化够大，就记一笔要推 */
    private static void markIfChanged(ServerPlayer player, Hunt h, RequiemCard card, long now) {
        LichStatePacket last = h.lastSent;
        if (last == null) {
            h.dirty = true;
            return;
        }
        LichStatePacket cur = buildState(h, card, now);
        if (cur.state != last.state || cur.waitReason != last.waitReason || !cur.name.equals(last.name)
                || cur.rank != last.rank || cur.tier != last.tier || cur.clues != last.clues
                || cur.generation != last.generation || cur.paused != last.paused || cur.weapon != last.weapon
                || (cur.cooldownSec > 0) != (last.cooldownSec > 0)
                || Math.abs(cur.ragePermille - last.ragePermille) >= RAGE_PUSH_STEP) {
            h.dirty = true;
        }
    }

    /** 有要推的就推，但每人两个包之间至少隔 10 tick（多出来的合并成最新的一个）；登录后第 40 tick 才推第一个 */
    private static void flushState(ServerPlayer player, Hunt h, long now) {
        if (h.forceStateAt >= 0) {
            if (now < h.forceStateAt) {
                return;
            }
            h.forceStateAt = -1;
            h.dirty = true;
        }
        if (!h.dirty || now - h.lastSentAt < STATE_MIN_GAP) {
            return;
        }
        RequiemCard card = card(player);
        if (card == null) {
            return;
        }
        LichStatePacket packet = buildState(h, card, now);
        LichStatePacket.send(player, packet);
        h.lastSent = packet;
        h.lastSentAt = now;
        h.dirty = false;
    }

    // ==================== 工具 ====================

    private static long now(@Nullable MinecraftServer server) {
        return server == null ? 0L : server.overworld().getGameTime();
    }

    private static long cooldownTicks() {
        return Math.round(ModConfig.KUVA_LICH.lichCooldownMinutes.get() * 1200.0D);
    }

    @Nullable
    private static RequiemCard card(ServerPlayer player) {
        return player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
    }

    private static Hunt hunt(ServerPlayer player) {
        return HUNTS.computeIfAbsent(player.getUUID(), id -> {
            Hunt h = new Hunt(id);
            long now = now(player.server);
            h.loginAt = now;
            h.lastTauntRollAt = now;
            return h;
        });
    }

    @Nullable
    private static Hunt huntOf(KuvaMasterEntity lich) {
        UUID owner = lich.getOwnerUUID();
        return owner == null ? null : HUNTS.get(owner);
    }

    /** 记录里的那只还在世界里就返回它 */
    @Nullable
    private static KuvaMasterEntity liveLich(Hunt h) {
        KuvaMasterEntity lich = h.lich;
        return lich != null && !lich.isRemoved() ? lich : null;
    }

    /** 怒气只在「跟踪」「冷却」时变（来找你的路上、在场、离场时都停着），暂停时也不变 */
    private static boolean rageCanRise(Hunt h, RequiemCard card, long now) {
        if (card.isLichPaused() || liveLich(h) != null || h.spawnAt >= 0) {
            return false;
        }
        LichState state = computeState(h, card, now);
        return state == LichState.STALKING || state == LichState.COOLDOWN;
    }

    private static EncounterEnd endForRemoval(KuvaMasterEntity lich, @Nullable Entity.RemovalReason reason) {
        if (reason == null) {
            return EncounterEnd.UNLOADED;
        }
        return switch (reason) {
            case KILLED -> EncounterEnd.KILLED_OTHER;
            case DISCARDED -> lich.level().getDifficulty() == Difficulty.PEACEFUL
                    ? EncounterEnd.PEACEFUL : EncounterEnd.KILLED_OTHER;
            default -> EncounterEnd.UNLOADED;
        };
    }

    private static void debug(@Nullable ServerPlayer player, String message) {
        if (LogUtil.isDebugEnabled()) {
            LogUtil.debug("[玄骸导演] " + (player == null ? "-" : player.getGameProfile().getName()) + "：" + message);
        }
    }
}
