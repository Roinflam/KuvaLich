package pers.roinflam.kuvalich.mercy;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.fx.ServerFxQueue;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.card.OULL;
import pers.roinflam.kuvalich.nemesis.AllyRoster;
import pers.roinflam.kuvalich.nemesis.EncounterEnd;
import pers.roinflam.kuvalich.nemesis.FinaleSettlement;
import pers.roinflam.kuvalich.nemesis.LichComms;
import pers.roinflam.kuvalich.nemesis.LichDirector;
import pers.roinflam.kuvalich.nemesis.LichLine;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.network.packet.MercyChoiceOpenPacket;
import pers.roinflam.kuvalich.network.packet.MercyChoicePacket;
import pers.roinflam.kuvalich.utils.InventoryUtil;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 怜悯之击的服务端：倒地、请求校验、处决推进、锁定与无敌、中断与快进
 *
 * <p>一场处决开始时就把结果（计划位）算好整个发给客户端，客户端离线跑完整条演出；服务端这里只按 tick 推进
 * 「什么时刻写数据、什么时刻发东西」。时间轴放在每场处决自己的步骤表里，不交给通用的延时队列：
 * 延时队列在玩家下线时会把任务立刻执行，而第一刀落下（定案）之前下线要整场作废、什么都不写。</p>
 *
 * <p>定案（tick 17）之前被打断：作废，目标回到倒地、计时接着走（不足几秒的补到几秒），处决者解锁。
 * 定案之后被打断：把剩下的结算一次做完（快进）——第一刀亮起时处决者已经知道第一张卡对不对，
 * 这时下线要是能让处决作废，就能白拿「第几张对」的信息。</p>
 *
 * <p>有主玄骸的血分三段，每打空一段倒地一次：第 k 次处决只验前 k 张卡、捅 k 刀。验的几刀全对、还没到第三刀，
 * 玄骸挣开站起来（下一段满血、护盾回满）接着打；第三次全对才终结；哪一刀错了照旧升级逃走。
 * 倒地到时没人处决，玄骸把这段血回满站起来，连着三次没人处决才逃走。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class MercyServer {

    // ==================== 时刻（tick，从处决开始算） ====================

    /** 解锁后再多无敌这么多 tick */
    public static final int GRACE_TICKS = 10;
    /** 从这一 tick 起服务端才强制处决者站位（之前由处决者自己的客户端缓入） */
    static final int ENFORCE_FROM = 6;
    /** 三刀成功后实体留多久：REQUIEM 消散 1.5 秒 + 100 毫秒 */
    public static final int SUCCESS_HOLD_TICKS = 32;
    /** 挣开时处决者被推开多远（比判错那一下轻） */
    static final double ADVANCE_PUSH_DISTANCE = 1.0D;
    /** 倒地到时没人处决：站起来要多久（这段时间不动、不挨打） */
    public static final int RISE_TICKS = 32;
    /** 连着这么多次倒地都没人处决，玄骸就逃走 */
    public static final int UNCLAIMED_LIMIT = 3;
    /** 踏背下刺：蹲上后背的高度、闪到正面后比原站距多站开多少（那时它已经直起身跪着，大腿往前伸，比正面直刺站开一点） */
    static final double VAULT_BACK_UP = 1.75D;
    static final double VAULT_FRONT_GAP = 0.35D;
    /** 倒地后多久玄骸说一句 */
    static final int DOWNED_COMM_DELAY = 12;

    // ==================== 其它常量 ====================

    /** 同一玩家两次请求最少隔多少 tick */
    static final int REQUEST_COOLDOWN = 10;
    /** 被拒的动作栏提示同一玩家最少隔多少 tick */
    static final int DENY_COOLDOWN = 20;
    /** 处决者离锁定站位超过这么远（格）算被传走了，当中断 */
    static final double INTERRUPT_DISTANCE = 4.0D;
    /** 处决者离站位超过这么远（格）就拉回去；更近的只清速度 */
    static final double SNAP_DISTANCE = 0.5D;
    static final double DRIFT_DISTANCE = 0.05D;
    /** 判错后处决者被推开多远、每 tick 走多远 */
    static final double PUSH_DISTANCE = 1.5D;
    static final double PUSH_STEP = 0.25D;
    /** 站位找不到能站的地方时每次往外挪多少 */
    static final double STAND_STEP = 0.2D;
    /** 站位贴地时上下找多远 */
    static final double GROUND_SNAP = 0.6D;
    /** 绕背时离目标比站位再远这么多，查路线时按这个半径。玄骸那套绕到它挫过去的那一侧，得站开得多 */
    static final double ORBIT_MARGIN = 0.2D;
    static final double ORBIT_MARGIN_LICH = 0.8D;
    /** 玄骸 / 奴仆的效果包范围 */
    static final double LICH_RANGE = 48.0D;
    static final double THRALL_DOWNED_RANGE = 24.0D;
    static final double THRALL_BEGIN_RANGE = 32.0D;
    /** 倒地包续上位 */
    static final int DOWNED_RESUMED = 1 << 16;
    /** 选择截止后再收这么多 tick 的选择包（客户端倒计时走完那一下按的、路上的包） */
    static final int CHOICE_GRACE_TICKS = 10;

    // ==================== 状态 ====================

    /** 进行中的处决（含已经解锁、还有收尾任务没走完的） */
    private static final List<Exec> ACTIVE = new ArrayList<>();
    /** 处决者 → 锁定着他的那一场 */
    private static final Map<UUID, Exec> LOCKED = new HashMap<>();
    /** 处决者 → 无敌余量到哪一 tick 为止 */
    private static final Map<UUID, Long> GRACE = new HashMap<>();
    /** 玩家上一次请求的 tick */
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    /** 玩家上一次收到被拒提示的 tick */
    private static final Map<UUID, Long> LAST_DENY = new HashMap<>();
    /** 玩家上一次用的玄骸 / 奴仆套路（不连着重复，不存盘） */
    private static final Map<UUID, MercyVariant> LAST_LICH = new HashMap<>();
    private static final Map<UUID, MercyVariant> LAST_THRALL = new HashMap<>();
    /** 演示：下一次处决指定的套路、结果、界面上的卡 */
    private static final Map<UUID, MercyVariant> NEXT_VARIANT = new HashMap<>();
    private static final Map<UUID, Outcome> NEXT_OUTCOME = new HashMap<>();
    private static final Map<UUID, DemoCards> DEMO_CARDS = new HashMap<>();
    /** 倒地计时冻结着的世界（演示用，不存盘） */
    private static final Set<ResourceKey<Level>> FROZEN = new HashSet<>();
    /** 自己数的服务端 tick */
    private static long serverTicks;
    /** GameTest 用：不为 null 时每场处决每 tick 推进前调一下，在里面抛异常就是「推进出错」 */
    @Nullable
    public static volatile Runnable faultOnTick;

    private MercyServer() {
    }

    /** 演示目标的处决结果（{@link #REAL} = 按处决者身上的卡判） */
    public enum Outcome {
        SUCCESS, FAIL1, FAIL2, FAIL3, REAL
    }

    /** 中断的原因 */
    public enum Gone {
        LOGOUT, DEATH, DIMENSION, TELEPORTED, SPECTATOR, TARGET_GONE, TARGET_DIED
    }

    /** 演示时界面上显示的三张卡和处决前剩余次数 */
    private record DemoCards(int[] ids, int[] remaining, boolean returnsItems) {
    }

    // ==================== 一场处决 ====================

    private enum StepKind {
        /** 定案 */
        SETTLE,
        /** 第一刀之后的通讯 */
        COMM,
        /** 判错后的嘲讽 */
        TAUNT,
        /** 推开处决者 */
        PUSH,
        /** 背对处决者的两套成功时转身 */
        TURN,
        /** 奴仆绕到身后的两套：人落地，站位换到身后 */
        LAND,
        /** 失败：玄骸进入离场 */
        LEAVE,
        /** 还没到第三刀全对：玄骸挣开、进起身 */
        RISE,
        /** 给玩家的东西：聊天、掉落、进度、完成提示 */
        PLAYER,
        /** 终结：开感化 / 消灭的选择窗口，处决钟停在这里等结果 */
        CHOICE_OPEN,
        /** 感化：玄骸化雾离场 */
        CONVERT_LEAVE,
        /** 目标死亡 */
        KILL,
        /** 失败：移除玄骸、结算遭遇 */
        ESCAPE,
        /** 处决者解锁 */
        UNLOCK
    }

    private record Step(int tick, StepKind kind, Runnable action) {
    }

    private static final class Exec {
        final AbstractKuva target;
        final ServerPlayer executor;
        final UUID executorId;
        final MercyPlan.Plan plan;
        final int planBits;
        final MercyVariant variant;
        final boolean lich;
        final boolean demo;
        /** 处决开始时目标脚底、从目标指向站位的水平单位向量 */
        final Vec3 pl;
        final Vec3 f;
        /** 处决者锁定站位（判错后会被推开） */
        Vec3 lockPos;
        float targetYaw;
        int age;
        boolean settled;
        boolean locked = true;
        boolean finished;
        /** 自己马上要移除目标（失败收尾），这次移除不算中断 */
        boolean expectRemoval;
        int pushLeft;
        /** 落到目标身后的套路：客户端自己沿路线走，这段时间服务端不按站位拉人，到点把站位换成 {@link #landing} */
        boolean roaming;
        @Nullable
        Vec3 landing;
        final List<Step> steps = new ArrayList<>();
        int next;
        /** 三刀成功：定案时算好的奖励 */
        @Nullable
        KuvaMasterEntity.SuccessResult success;
        /** 失败：定案时算好的等级（收尾时实体可能已经没了） */
        int rankAfter;
        /** 失败：定案时掷过的「给一张 Oull」 */
        boolean oullDrop;
        /** 计划位 bit 31：成功后开选择窗口 */
        final boolean choiceWindow;
        /** 窗口开着、还没定：这期间处决钟不走，这一场也不 finish（巡检靠它判断账有没有人管） */
        boolean awaiting;
        /** 开窗那一刻的 convertEnabled，窗口里热改配置不影响这一场 */
        boolean allowConvert;
        /** 截止（{@link #serverTicks}），之后再收 {@link #CHOICE_GRACE_TICKS} 的包 */
        long choiceDeadline;
        /** {@link AbstractKuva#CHOICE_VANQUISH} / {@link AbstractKuva#CHOICE_CONVERT}，0 = 还没定 */
        byte choice;
        /** 选择那一刻落定的结果，发奖那一拍拿它发聊天、投递 */
        @Nullable
        FinaleSettlement.Settled settledResult;
        boolean announced;

        Exec(AbstractKuva target, ServerPlayer executor, MercyPlan.Plan plan, MercyVariant variant,
             Vec3 pl, Vec3 f, Vec3 pe, boolean choiceWindow) {
            this.target = target;
            this.executor = executor;
            this.executorId = executor.getUUID();
            this.plan = plan;
            this.choiceWindow = choiceWindow;
            this.planBits = MercyPlan.encode(plan) | (choiceWindow ? MercyPlan.CHOICE_WINDOW : 0);
            this.variant = variant;
            this.lich = target instanceof KuvaMasterEntity;
            this.demo = plan.demo();
            this.pl = pl;
            this.f = f;
            this.lockPos = pe;
        }

        boolean failure() {
            return lich && plan.failure();
        }

        /** 这次验的几刀全对、还没到第三刀 */
        boolean advance() {
            return lich && plan.advance();
        }

        /** 按 tick 插到对的位置（同一 tick 的保持加入顺序）：推进时从头逐个往后走，排错了后面的会被前面的卡住 */
        void add(int tick, StepKind kind, Runnable action) {
            int i = steps.size();
            while (i > 0 && steps.get(i - 1).tick() > tick) {
                i--;
            }
            steps.add(i, new Step(tick, kind, action));
        }
    }

    // ==================== 对外入口 ====================

    /** 这个玩家正在处决中，或还在处决结束后的无敌余量里（宿敌「来找你」的条件用它） */
    public static boolean isBusy(ServerPlayer player) {
        UUID id = player.getUUID();
        return LOCKED.containsKey(id) || inGrace(id);
    }

    /** 这个世界的倒地计时是不是被冻结了（演示命令） */
    public static boolean isFrozen(Level level) {
        return !FROZEN.isEmpty() && FROZEN.contains(level.dimension());
    }

    /**
     * 玄骸要离场了（宿敌的各种离场都从这里过），先问怜悯之击：
     *
     * <ul>
     *   <li>没倒地：不管，返回 false；</li>
     *   <li>倒地、起身中、消散中：改成离场中（客户端播起身），离场照常走，返回 false；</li>
     *   <li>处决中、还没定案：作废这一场、处决者解锁，离场照常走，返回 false；</li>
     *   <li>处决中、已定案、判错了或者还没到第三刀：给玩家的东西立刻发，冲击和失败的结算不做了（离场本身就是结算），返回 false；</li>
     *   <li>处决中、已定案、成功：快进到死、奖励立刻发，返回 true，调用方不再放离场。</li>
     * </ul>
     */
    public static boolean interceptDeparture(KuvaMasterEntity lich) {
        if (lich.level().isClientSide) {
            return false;
        }
        MercyState state = lich.getMercyState();
        if (state == MercyState.NONE) {
            return false;
        }
        Exec e = execFor(lich);
        if (e == null) {
            if (state != MercyState.LEAVING && lich.isAlive()) {
                lich.setMercyState(MercyState.LEAVING);
                downedChanged(lich, false);
            }
            return false;
        }
        if (!e.settled) {
            finish(e);
            unlock(e);
            if (lich.isAlive()) {
                lich.setMercyState(MercyState.LEAVING);
                downedChanged(lich, false);
            }
            return false;
        }
        if (e.choice == AbstractKuva.CHOICE_CONVERT) {
            // 感化的后段自己直接 departNow；别的路径要它离场也照常放行，不当成「成功快进」拦下
            return false;
        }
        if (e.failure() || e.advance()) {
            fastForward(e, false);
            if (lich.isAlive()) {
                lich.setMercyState(MercyState.LEAVING);
                downedChanged(lich, false);
            }
            return false;
        }
        fastForward(e, false);
        return true;
    }

    /** 玩家按下处决键后发来的请求：服务端重新校验一切条件后才开始处决 */
    public static void handleRequest(ServerPlayer player, int entityId) {
        UUID id = player.getUUID();
        Long last = LAST_REQUEST.get(id);
        if (last != null && serverTicks - last < REQUEST_COOLDOWN) {
            return;
        }
        LAST_REQUEST.put(id, serverTicks);
        if (!(player.level().getEntity(entityId) instanceof AbstractKuva target) || !target.isAlive()) {
            return;
        }
        MercyState state = target.getMercyState();
        if (state != MercyState.DOWNED) {
            if (state == MercyState.EXECUTING) {
                deny(player, "taken");
            }
            return;
        }
        if (!id.equals(target.getMercyQualified())) {
            return;
        }
        if (!MercyRules.canAct(player) || LOCKED.containsKey(id)) {
            return;
        }
        if (target instanceof KuvaLarvlingEntity && !KuvaLarvlingEntity.canReceiveLich(player)) {
            // 倒地以后他又从别处得了一只玄骸：已经有玄骸的人处决不了幼体
            deny(player, "has_lich");
            return;
        }
        if (!MercyRules.inReach(player, target, MercyRules.REACH_SLACK)) {
            deny(player, "reach");
            return;
        }
        if (!MercyRules.hasSight(player, target)) {
            deny(player, "sight");
            return;
        }
        start(player, target, false);
    }

    /**
     * 处决者离开了（下线、死亡、换维度……）。事件里自动调；GameTest 直接调来模拟下线
     */
    public static void onExecutorGone(ServerPlayer player, Gone reason) {
        for (Exec e : new ArrayList<>(ACTIVE)) {
            if (!e.finished && e.executorId.equals(player.getUUID())) {
                interrupt(e, reason);
            }
        }
    }

    // ==================== 倒地 ====================

    /** 致命伤拦下来、或者这段血打空之后（实体已经把血量压到这段的底）：进倒地 */
    public static void enterDowned(AbstractKuva target, DamageSource source) {
        beginDowned(target, qualifiedFor(target, source));
    }

    /** 管理命令直接打倒：奴仆和演示目标的资格人就是下命令的人，有主玄骸永远是主人 */
    public static void forceDowned(AbstractKuva target, @Nullable ServerPlayer qualified) {
        UUID q;
        if (target instanceof KuvaMasterEntity lich && lich.isOwned() && !lich.isDemo()) {
            q = lich.getOwnerUUID();
        } else if (target instanceof KuvaMasterEntity lich && !lich.isDemo()) {
            // 野生玄骸不倒地
            return;
        } else {
            q = qualified == null ? null : qualified.getUUID();
            if (target instanceof KuvaLarvlingEntity larvling) {
                q = larvling.pickClaimant(q);
            }
        }
        target.setHealth(target.downedHealth());
        beginDowned(target, q);
    }

    private static void beginDowned(AbstractKuva target, @Nullable UUID qualified) {
        target.setMercyQualified(qualified);
        target.setTarget(null);
        target.getNavigation().stop();
        Vec3 v = target.getDeltaMovement();
        target.setDeltaMovement(0.0D, v.y, 0.0D);
        target.setMercyRemaining(target.downedSeconds() * 20);
        target.setMercyActorId(resolveQualifiedId(target));
        target.setMercyPlanBits(0);
        target.setMercyChoice(AbstractKuva.CHOICE_NONE);
        target.setMercyState(MercyState.DOWNED);
        sendDowned(target, false);

        if (target instanceof KuvaMasterEntity lich) {
            downedChanged(lich, true);
            ServerFxQueue.schedule(DOWNED_COMM_DELAY, null, () -> {
                if (lich.isAlive() && !lich.isRemoved() && lich.getMercyState() == MercyState.DOWNED) {
                    sayAs(lich, LichLine.DOWNED, playerByUuid(lich, lich.getMercyQualified()));
                }
            });
        }
    }

    /**
     * 致命一击的资格人：玄骸 = 主人；奴仆按顺序判——伤害来自玩家就是他；来自宠物、召唤物就是它的主人；
     * 烧伤、推下岩浆这类延后的伤害看最近一次被哪个玩家打过；都不是就没有资格人（谁也捅不了，到时消散）。
     * 幼体先按奴仆的规矩找出打倒它的人，他已经有玄骸就退给幼体的主人（见 {@link KuvaLarvlingEntity#pickClaimant}）
     */
    @Nullable
    private static UUID qualifiedFor(AbstractKuva target, DamageSource source) {
        if (target instanceof KuvaMasterEntity lich && lich.isOwned() && !lich.isDemo()) {
            return lich.getOwnerUUID();
        }
        if (target.isDemo() && target.getMercyQualified() != null) {
            return target.getMercyQualified();
        }
        UUID hitter = hitterOf(target, source);
        return target instanceof KuvaLarvlingEntity larvling ? larvling.pickClaimant(hitter) : hitter;
    }

    /** 这一下算谁打的：玩家本人、宠物 / 召唤物 / 盟友的主人、最近 5 秒打过它的玩家，都不是返回 null */
    @Nullable
    private static UUID hitterOf(AbstractKuva target, DamageSource source) {
        Entity e = source.getEntity();
        if (e instanceof Player p) {
            return p.getUUID();
        }
        if (e instanceof OwnableEntity pet && pet.getOwner() instanceof Player owner) {
            return owner.getUUID();
        }
        if (e instanceof KuvaMasterEntity ally && ally.isAlly()) {
            return ally.getAllyOwnerUUID();
        }
        Player recent = target.recentPlayerAttacker();
        return recent == null ? null : recent.getUUID();
    }

    /** 资格人此刻在这个世界里的实体 id，不在为 -1 */
    public static int resolveQualifiedId(AbstractKuva target) {
        Player p = playerByUuid(target, target.getMercyQualified());
        return p == null ? -1 : p.getId();
    }

    @Nullable
    private static ServerPlayer playerByUuid(Entity near, @Nullable UUID id) {
        if (id == null || !(near.level() instanceof ServerLevel level)) {
            return null;
        }
        Player p = level.getPlayerByUUID(id);
        return p instanceof ServerPlayer sp ? sp : null;
    }

    private static void sendDowned(AbstractKuva target, boolean resumed) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        double range = target instanceof KuvaMasterEntity ? LICH_RANGE : THRALL_DOWNED_RANGE;
        int b = (target.getMercyRemainingServer() & 0xFFFF) | (resumed ? DOWNED_RESUMED : 0);
        KuvaFx.near(level, target.chest(), range, FxId.LICH_DOWNED, target, target.getMercyActorId() + 1, b);
    }

    /**
     * 倒地时间到了：分段的玄骸这时这段血已经回满，站起来接着打；连着第 {@link #UNCLAIMED_LIMIT} 次没人处决、
     * 不分段的玄骸起身逃走（宿敌那边发嘲讽、放赤毒雾）；奴仆原地消散。逃走和消散什么都不给
     */
    public static void onDownedTimeout(AbstractKuva target) {
        target.setMercyRemaining(0);
        if (target instanceof KuvaMasterEntity lich) {
            if (lich.isSegmented() && lich.noteUnclaimedDown() < UNCLAIMED_LIMIT) {
                lich.refillSegment();
                // 中途作废的处决会把计划位留下来；清掉，客户端才按「从倒地站起来」挑动作
                lich.setMercyPlanBits(0);
                lich.beginRise(RISE_TICKS);
                downedChanged(lich, false);
                return;
            }
            lich.setMercyState(MercyState.LEAVING);
            downedChanged(lich, false);
            if (lich.isOwned() && !lich.isDemo()) {
                LichDirector.endEncounter(lich.getOwnerPlayer(), lich, EncounterEnd.UNCLAIMED, true);
            } else {
                lich.departNow(EncounterEnd.UNCLAIMED.ordinal());
            }
            return;
        }
        target.beginExpire();
    }

    /** 目标正要被自然消失截下来演离场：处决中的先按中断收掉 */
    public static void onTargetLeaving(AbstractKuva target) {
        Exec e = execFor(target);
        if (e != null) {
            interrupt(e, Gone.TARGET_GONE);
        }
    }

    /** 目标死了（实体回调）。处决自己让它死的那一下不算 */
    public static void onTargetDied(AbstractKuva target) {
        if (target.isMercyFinishing()) {
            return;
        }
        Exec e = execFor(target);
        if (e != null) {
            interrupt(e, Gone.TARGET_DIED);
        }
    }

    private static void downedChanged(KuvaMasterEntity lich, boolean downed) {
        if (lich.isOwned() && !lich.isDemo()) {
            LichDirector.onDownedChanged(lich, downed);
        }
    }

    // ==================== 开始处决 ====================

    private static void start(ServerPlayer player, AbstractKuva target, boolean placeNow) {
        boolean thrall = target instanceof KuvaSlaveEntity;
        ServerLevel level = (ServerLevel) target.level();
        Vec3 pl = target.position();
        Vec3 f = horizontalDir(player.position().subtract(pl), target.getYRot());
        MercyVariant variant = pickVariant(player, thrall, level, pl, f);
        boolean mirror = variant.orbits() && orbitSide(level, player, pl, f, variant) < 0;
        MercyPlan.Plan plan = buildPlan(player, target, variant, mirror);

        Vec3 pe = standPosition(level, player, pl, f, variant.standDist);
        boolean choiceWindow = target instanceof KuvaMasterEntity && plan.success() && convertEnabled();
        Exec e = new Exec(target, player, plan, variant, pl, f, pe, choiceWindow);
        e.targetYaw = facingYaw(f, variant.facing) + variant.faceTurn;
        if (variant.relocates()) {
            e.roaming = true;
            e.landing = landingPosition(level, player, pl, f, pe, variant);
        }

        target.setTarget(null);
        target.getNavigation().stop();
        target.setMercyActorId(player.getId());
        target.setMercyPlanBits(e.planBits);
        target.setMercyChoice(AbstractKuva.CHOICE_NONE);
        target.setMercyState(MercyState.EXECUTING);
        lockTarget(e);
        if (placeNow) {
            snapExecutor(e);
        }

        double range = thrall ? THRALL_BEGIN_RANGE : LICH_RANGE;
        KuvaFx.near(level, pe, range, FxId.MERCY_BEGIN, target, player.getId() + 1, e.planBits);

        buildSteps(e);
        ACTIVE.add(e);
        LOCKED.put(e.executorId, e);
        GRACE.remove(e.executorId);
        (thrall ? LAST_THRALL : LAST_LICH).put(e.executorId, variant);
        if (LogUtil.isDebugEnabled()) {
            LogUtil.debug("怜悯之击开始：" + player.getGameProfile().getName() + " → " + target.getName().getString()
                    + "，套路 " + variant + "，刀数 " + plan.stabs() + "，前缀 " + plan.prefix()
                    + (plan.demo() ? "（演示）" : ""));
        }
    }

    /** 从同类三套里排除上一次的、均匀随机；演示命令指定过的优先（只管下一次） */
    private static MercyVariant pickVariant(ServerPlayer player, boolean thrall, ServerLevel level, Vec3 pl, Vec3 f) {
        UUID id = player.getUUID();
        MercyVariant forced = NEXT_VARIANT.get(id);
        if (forced != null && forced.thrall == thrall) {
            NEXT_VARIANT.remove(id);
            return forced;
        }
        MercyVariant last = (thrall ? LAST_THRALL : LAST_LICH).get(id);
        List<MercyVariant> choices = new ArrayList<>(MercyVariant.values().length);
        for (MercyVariant v : MercyVariant.values()) {
            if (v.thrall == thrall && v != last && fits(level, player, pl, f, v)) {
                choices.add(v);
            }
        }
        return choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
    }

    /**
     * 这一套在这里摆得开：绕背要有一边走得通，落到身后的要身后站得下，翻到正面的要头顶蹲得下人、正面和蹬开后的落点站得下。
     * 基础套路哪里都能演，所以总有得选
     */
    public static boolean fits(ServerLevel level, ServerPlayer player, Vec3 pl, Vec3 f, MercyVariant v) {
        if (v.orbits() && orbitSide(level, player, pl, f, v) == 0) {
            return false;
        }
        if (v.vaultsOver() && !vaultClear(level, player, pl, f, v)) {
            return false;
        }
        if (!roomClear(level, player, pl, f, v)) {
            return false;
        }
        return !v.relocates() || standable(level, player, behind(level, player, pl, f, v));
    }

    /**
     * 新三套整身轨道路过的几处：{角度, 离目标多远, 离地多高, 1 = 蹲着的身量 / 0 = 站着}。角度和客户端整身轨道的「绕」一样，
     * 从站位往 {@code Y × F} 那边量；离地高的那几处连头顶一起查，低顶时不选这一套
     */
    private static final Map<MercyVariant, double[][]> ROOM = new EnumMap<>(MercyVariant.class);

    static {
        // 折翼：荡身最外、荡到它右后方的半路（身子甩平、离地 0.6～0.9 格）、右后方落脚、踩着后腰窜起最高（在它头顶）、
        // 落到它右手边
        ROOM.put(MercyVariant.LICH_F, new double[][]{{-50, 2.6, 0.85, 1}, {-95, 2.2, 0.6, 1}, {-127, 1.56, 0, 0},
                {-123, 0.35, 1.4, 0}, {-90, 1.66, 0, 0}});
        // 断膝三方：滑铲擦过它右手边、右后方第一刀的站位、越背侧翻升起和最高处（团着身在它后背上方，顶上要三格出头）、
        // 左后方落地；前滚绕过它身后、钻到右前方的半路、第三刀的站位
        ROOM.put(MercyVariant.LICH_G, new double[][]{{-60, 1.46, 0, 1}, {-100, 1.49, 0, 1}, {-127, 0.9, 0, 0},
                {-110, 0.45, 1.3, 1}, {-180, 0.1, 1.67, 1}, {144, 0.63, 0, 0}, {-178, 0.64, 0, 1},
                {-120, 1.23, 0, 1}, {-79, 1.56, 0, 0}});
        // 三掠：贴地平飞的两处、左手边落脚；扑上后背、后空翻最高处、背后落脚；腾空越顶的起、顶（离地 2.4 格）、落，
        // 右前方落脚和收势小跳
        ROOM.put(MercyVariant.LICH_H, new double[][]{{30, 1.6, 0.14, 1}, {75, 1.44, 0.07, 1}, {86, 1.43, 0, 0},
                {123, 1.02, 0.86, 1}, {147, 1.25, 1.21, 1}, {164, 0.87, 0, 0}, {172, 0.55, 1.9, 1},
                {254, 1.04, 2.44, 1}, {283, 1.36, 1.99, 1}, {279, 1.45, 0, 0}, {279, 1.7, 0.2, 0}});
    }

    private static boolean roomClear(ServerLevel level, ServerPlayer player, Vec3 pl, Vec3 f, MercyVariant v) {
        double[][] pts = ROOM.get(v);
        if (pts == null) {
            return true;
        }
        Vec3 s = new Vec3(f.z, 0.0D, -f.x);
        for (double[] p : pts) {
            double a = Math.toRadians(p[0]);
            Vec3 at = pl.add(f.scale(Math.cos(a) * p[1])).add(s.scale(Math.sin(a) * p[1]));
            at = p[2] > 0.0D ? new Vec3(at.x, pl.y + p[2], at.z) : snapToGround(level, player, at);
            Pose pose = p[3] > 0.5D ? Pose.CROUCHING : Pose.STANDING;
            if (!level.noCollision(player, player.getDimensions(pose).makeBoundingBox(at))) {
                return false;
            }
        }
        return true;
    }

    /** 踏背下刺：蹲在它后背上（离地约 1.75 格）、闪到它正面、再蹬开往外落 1.5 格，三处都要空着 */
    private static boolean vaultClear(ServerLevel level, ServerPlayer player, Vec3 pl, Vec3 f, MercyVariant v) {
        Vec3 onBack = pl.add(0.0D, VAULT_BACK_UP, 0.0D);
        if (!level.noCollision(player, player.getDimensions(Pose.CROUCHING).makeBoundingBox(onBack))) {
            return false;
        }
        double front = v.standDist + VAULT_FRONT_GAP;
        return standable(level, player, snapToGround(level, player, pl.subtract(f.scale(front))))
                && standable(level, player, snapToGround(level, player, pl.subtract(f.scale(front + PUSH_DISTANCE))));
    }

    /** 绕背走哪边：1 = 目标的左手边（{@code Y × F}），-1 = 右手边，0 = 两边都有方块挡着 */
    private static int orbitSide(ServerLevel level, ServerPlayer player, Vec3 pl, Vec3 f, MercyVariant v) {
        for (int side = 1; side >= -1; side -= 2) {
            double margin = v.thrall ? ORBIT_MARGIN : ORBIT_MARGIN_LICH;
            if (arcClear(level, player, pl, f, v.standDist + margin, side)) {
                return side;
            }
        }
        return 0;
    }

    /** 沿半圈取几个点，蹲着都过得去 */
    private static boolean arcClear(ServerLevel level, ServerPlayer player, Vec3 pl, Vec3 f, double r, int side) {
        Vec3 s = new Vec3(f.z, 0.0D, -f.x).scale(side);
        for (int deg = 45; deg <= 180; deg += 45) {
            double a = Math.toRadians(deg);
            Vec3 at = pl.add(f.scale(Math.cos(a) * r)).add(s.scale(Math.sin(a) * r));
            at = snapToGround(level, player, at);
            if (!level.noCollision(player, player.getDimensions(Pose.CROUCHING).makeBoundingBox(at))) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 behind(ServerLevel level, ServerPlayer player, Vec3 pl, Vec3 f, MercyVariant v) {
        return snapToGround(level, player, pl.subtract(f.scale(v.standDist)));
    }

    private static boolean standable(ServerLevel level, ServerPlayer player, Vec3 at) {
        return level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(at));
    }

    /** 落到目标身后的位置；演示强行指定的套路身后站不下时留在原站位 */
    private static Vec3 landingPosition(ServerLevel level, ServerPlayer player, Vec3 pl, Vec3 f, Vec3 pe, MercyVariant v) {
        Vec3 at = behind(level, player, pl, f, v);
        return standable(level, player, at) ? at : pe;
    }

    /**
     * 开始时就把整场的结果算好：刀数、猜对的前缀、三张卡和次数、有没有归还、会用完几张。
     * 玄骸第 k 次处决（打空了 k 段）捅 k 刀、只验前 k 张，从第一格起逐格判，空着的格子算这一格判错
     * （一张没装就是第一刀判错）；第三次全对才算终结，这时才扣卡、归还物品
     */
    private static MercyPlan.Plan buildPlan(ServerPlayer player, AbstractKuva target, MercyVariant variant, boolean mirror) {
        boolean demo = target.isDemo();
        int[] none = {-1, -1, -1};
        int[] zero = {0, 0, 0};
        if (target instanceof KuvaSlaveEntity) {
            return new MercyPlan.Plan(1, 0, variant, none, zero, false, 0, demo, mirror);
        }
        int stabs = target instanceof KuvaMasterEntity lich ? lich.mercyStabs() : 3;
        RequiemCard card = card(player);
        int[] ids = none;
        int[] remaining = zero;
        int prefix = 0;
        boolean hasConfiscated = false;
        if (card != null) {
            ItemStack[] placed = {card.getOneCard(), card.getTwoCard(), card.getThreeCard()};
            ids = new int[3];
            remaining = new int[3];
            for (int i = 0; i < 3; i++) {
                ids[i] = placed[i] != null && placed[i].getItem() instanceof AbstractRequiemCard c ? c.getID() : -1;
                remaining[i] = usesLeft(placed[i]);
            }
            prefix = card.correctPrefixLength();
            hasConfiscated = card.hasConfiscatedItems();
        }
        boolean returnsItems = hasConfiscated;
        if (demo) {
            DemoCards dc = DEMO_CARDS.get(player.getUUID());
            if (dc != null) {
                ids = dc.ids().clone();
                remaining = dc.remaining().clone();
                returnsItems = dc.returnsItems();
            }
            Outcome o = NEXT_OUTCOME.remove(player.getUUID());
            if (o != null && o != Outcome.REAL) {
                prefix = switch (o) {
                    case FAIL1 -> 0;
                    case FAIL2 -> 1;
                    case FAIL3 -> 2;
                    default -> 3;
                };
            } else {
                // 按身上的卡判、界面上摆的是演示卡：前缀不越过演示卡里第一个空格
                prefix = Math.min(prefix, firstEmpty(ids));
            }
        }
        prefix = Math.min(prefix, stabs);
        boolean success = stabs >= 3 && prefix >= 3;
        int consumed = 0;
        if (success) {
            for (int i = 0; i < 3; i++) {
                if (ids[i] >= 0 && remaining[i] <= 1) {
                    consumed++;
                }
            }
        }
        return new MercyPlan.Plan(stabs, prefix, variant, ids, remaining, success && returnsItems, consumed, demo, mirror);
    }

    /** 第一个空格的下标；三格都放了返回 3 */
    private static int firstEmpty(int[] ids) {
        for (int i = 0; i < 3; i++) {
            if (i >= ids.length || ids[i] < 0) {
                return i;
            }
        }
        return 3;
    }

    /** 一张卡处决之前还剩几次：可损耗的按剩余耐久，不可损耗的用一次就没（记 1） */
    private static int usesLeft(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        if (!stack.isDamageableItem()) {
            return 1;
        }
        return Mth.clamp(stack.getMaxDamage() - stack.getDamageValue(), 0, 3);
    }

    /**
     * 处决者站位：沿「目标 → 玩家」的水平方向离目标 {@code standDist}，上下 0.6 格内贴地；
     * 站不下就每次往外挪 0.2 格直到玩家现在的距离，都不行就原地开始（不拉人）
     */
    private static Vec3 standPosition(ServerLevel level, ServerPlayer player, Vec3 pl, Vec3 f, double standDist) {
        Vec3 here = player.position();
        double current = Math.sqrt((here.x - pl.x) * (here.x - pl.x) + (here.z - pl.z) * (here.z - pl.z));
        for (double dist = standDist; ; dist += STAND_STEP) {
            Vec3 at = new Vec3(pl.x + f.x * dist, here.y, pl.z + f.z * dist);
            at = snapToGround(level, player, at);
            AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(at);
            if (level.noCollision(player, box)) {
                return at;
            }
            if (dist + STAND_STEP > current) {
                return here;
            }
        }
    }

    private static Vec3 snapToGround(ServerLevel level, Entity who, Vec3 at) {
        Vec3 top = at.add(0.0D, GROUND_SNAP, 0.0D);
        Vec3 bottom = at.subtract(0.0D, GROUND_SNAP, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, who));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return new Vec3(at.x, hit.getLocation().y, at.z);
        }
        return at;
    }

    /** 水平单位向量；长度为 0 时按 {@code fallbackYaw} 的朝向 */
    private static Vec3 horizontalDir(Vec3 d, float fallbackYaw) {
        Vec3 h = new Vec3(d.x, 0.0D, d.z);
        if (h.lengthSqr() < 1.0E-6D) {
            float r = fallbackYaw * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(r), 0.0D, Mth.cos(r));
        }
        return h.normalize();
    }

    /** 朝某个水平方向的 yaw（原版约定：0 = 朝 +Z） */
    private static float yawOf(Vec3 v) {
        return (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** 目标在处决开始那一刻被转向哪里：面向处决者（+F）、背对（−F）、侧身朝 −S（S = Y × F，处决者在它左手边） */
    private static float facingYaw(Vec3 f, MercyVariant.Facing facing) {
        return switch (facing) {
            case TOWARD -> yawOf(f);
            case AWAY -> yawOf(f) + 180.0F;
            case SIDE -> yawOf(new Vec3(-f.z, 0.0D, f.x));
        };
    }

    // ==================== 时间轴 ====================

    /** 服务端各步骤的时刻都从 {@link MercyBeats} 取，和客户端演出共用一份 */
    private static void buildSteps(Exec e) {
        if (!e.lich) {
            MercyBeats.Thrall tb = MercyBeats.thrall(e.variant);
            e.add(tb.settleTick, StepKind.SETTLE, () -> e.settled = true);
            e.add(tb.deathTick, StepKind.KILL, () -> kill(e));
            if (e.roaming) {
                e.add(tb.landTick, StepKind.LAND, () -> {
                    e.roaming = false;
                    e.lockPos = e.landing;
                });
            }
            e.add(tb.unlockTick, StepKind.UNLOCK, () -> unlock(e));
            return;
        }
        KuvaMasterEntity lich = (KuvaMasterEntity) e.target;
        MercyBeats.Lich b = MercyBeats.lich(e.variant);
        if (e.advance()) {
            int g = b.fillTick(e.plan.stabs());
            e.add(b.settleTick, StepKind.SETTLE, () -> settleAdvance(e, lich));
            e.add(b.stabCommTick, StepKind.COMM, () -> sayAs(lich, LichLine.STAB, e.executor));
            e.add(g + b.advanceRiseTicks, StepKind.PUSH, () -> e.pushLeft = (int) Math.round(ADVANCE_PUSH_DISTANCE / PUSH_STEP));
            e.add(g + b.advanceRiseTicks, StepKind.RISE, () -> rise(e, lich));
            if (e.variant.turnsToFace()) {
                // 不正对处决者的套路：客户端在挣开后边站边转过来，这边转完再跟上，免得交还时又拧回去
                e.add(g + b.advanceTurnTicks, StepKind.TURN, () -> e.targetYaw = facingYaw(e.f, MercyVariant.Facing.TOWARD));
            }
            e.add(g + b.advanceStandTicks, StepKind.UNLOCK, () -> unlock(e));
            return;
        }
        if (!e.failure()) {
            e.add(b.settleTick, StepKind.SETTLE, () -> settleSuccess(e, lich));
            e.add(b.stabCommTick, StepKind.COMM, () -> sayAs(lich, LichLine.STAB, e.executor));
            if (e.variant.turnsToFace()) {
                e.add(b.closeupTurnTick, StepKind.TURN, () -> e.targetYaw = facingYaw(e.f, MercyVariant.Facing.TOWARD));
            }
            if (e.choiceWindow) {
                // 后段等选完再排（choose）
                e.add(b.choiceOpenTick, StepKind.CHOICE_OPEN, () -> openChoice(e, lich));
                return;
            }
            e.add(b.successDeathTick, StepKind.KILL, () -> kill(e));
            e.add(b.successRewardTick, StepKind.PLAYER, () -> successRewards(e));
            e.add(b.successRewardTick, StepKind.UNLOCK, () -> unlock(e));
            e.add(b.successCompleteTick, StepKind.PLAYER, () -> successComplete(e));
            return;
        }
        int k = e.plan.prefix() + 1;
        int g = b.fillTick(k);
        e.add(b.settleTick, StepKind.SETTLE, () -> settleFailure(e, lich, k));
        if (k >= 2) {
            // 第一刀对了才有「第一刀之后」的那句；第一刀就错的，这一刻已经在判错之后
            e.add(b.stabCommTick, StepKind.COMM, () -> sayAs(lich, LichLine.STAB, e.executor));
        }
        e.add(g + b.failPushTicks, StepKind.PUSH, () -> e.pushLeft = (int) Math.round(PUSH_DISTANCE / PUSH_STEP));
        e.add(g + b.failTauntLineTicks, StepKind.TAUNT, () -> sayAs(lich, LichLine.FAIL, e.executor));
        e.add(g + b.failLeaveTicks, StepKind.LEAVE, () -> {
            if (lich.isAlive()) {
                lich.setMercyState(MercyState.LEAVING);
            }
        });
        e.add(g + b.failUnlockTicks, StepKind.UNLOCK, () -> unlock(e));
        e.add(g + b.failRemoveTicks, StepKind.ESCAPE, () -> escape(e, lich, false));
        e.add(g + b.failChatTicks, StepKind.PLAYER, () -> giveOull(e));
        e.add(g + b.failProgressTicks, StepKind.PLAYER, () -> failureProgress(e, lich));
    }

    /**
     * 三刀全对，定案：记一行尝试、扣卡、终结这一轮、把奖励记进账（发奖那一拍才落定）。
     * 定案抛出时 {@code settled} 还是假，推进出错的兜底按「定案前」处理，目标回到倒地
     */
    private static void settleSuccess(Exec e, KuvaMasterEntity lich) {
        if (e.demo) {
            e.settled = true;
            return;
        }
        ServerPlayer owner = e.executor;
        RequiemCard card = card(owner);
        if (card == null) {
            e.settled = true;
            return;
        }
        LichDirector.onMercyResult(owner, lich, RequiemCard.packAttempt(e.plan.cardIds(),
                new byte[]{RequiemCard.ATTEMPT_RIGHT, RequiemCard.ATTEMPT_RIGHT, RequiemCard.ATTEMPT_RIGHT}, true), true);
        e.success = lich.computeAndApplySuccess(owner, card, lich.blockPosition());
        e.settled = true;
    }

    /**
     * 这次验的几刀全对、还没到第三刀，定案：「没人处决」的连续次数清零，记一行尝试（只亮这次验的几格）。
     * 不扣卡、不给解密进度，第三次全对才一起结算
     */
    private static void settleAdvance(Exec e, KuvaMasterEntity lich) {
        e.settled = true;
        lich.clearUnclaimedDowns();
        if (e.demo) {
            return;
        }
        int k = e.plan.stabs();
        byte[] results = new byte[3];
        for (int i = 0; i < 3; i++) {
            results[i] = (byte) (i < k ? RequiemCard.ATTEMPT_RIGHT : RequiemCard.ATTEMPT_UNJUDGED);
        }
        LichDirector.onMercyResult(e.executor, lich, RequiemCard.packAttempt(e.plan.cardIds(), results, true), false);
    }

    /** 挣开：下一段满血、护盾回满，进起身；演完自己回到能打的状态 */
    private static void rise(Exec e, KuvaMasterEntity lich) {
        if (!lich.isAlive() || lich.isRemoved() || lich.getMercyState() != MercyState.EXECUTING) {
            return;
        }
        lich.advanceSegment();
        MercyBeats.Lich b = MercyBeats.lich(e.variant);
        lich.beginRise(b.advanceStandTicks - b.advanceRiseTicks);
        downedChanged(lich, false);
    }

    /** 判错，定案：记一行尝试、玄骸升级、给主人发升级横幅 */
    private static void settleFailure(Exec e, KuvaMasterEntity lich, int k) {
        e.settled = true;
        lich.clearUnclaimedDowns();
        ServerPlayer owner = e.executor;
        if (e.demo) {
            lich.addDemoFailure();
            e.rankAfter = 1 + lich.getDemoFailures();
            lich.setDemoIdentity(lich.getLichName(), e.rankAfter);
        } else {
            RequiemCard card = card(owner);
            if (card == null) {
                return;
            }
            byte[] results = new byte[3];
            for (int i = 0; i < 3; i++) {
                results[i] = (byte) (i < k - 1 ? RequiemCard.ATTEMPT_RIGHT
                        : i == k - 1 ? RequiemCard.ATTEMPT_WRONG : RequiemCard.ATTEMPT_UNJUDGED);
            }
            LichDirector.onMercyResult(owner, lich, RequiemCard.packAttempt(e.plan.cardIds(), results, true), false);
            e.rankAfter = lich.applyFailure(card);
            e.oullDrop = rollOull(k - 1);
        }
        KuvaFx.player(owner, FxId.RANK_UP, lich.chest(), lich.getId(), e.rankAfter, 1 | (k << 1));
    }

    /** 目标死亡：玄骸成功收尾放 REQUIEM 消散，奴仆走自己的死亡结算 */
    private static void kill(Exec e) {
        AbstractKuva t = e.target;
        if (!t.isAlive() || t.isRemoved()) {
            return;
        }
        if (e.lich && t.level() instanceof ServerLevel level) {
            t.holdDeathTicks(SUCCESS_HOLD_TICKS);
            KuvaFx.near(level, t.chest(), LICH_RANGE, FxId.LICH_DEATH, t, e.executor.getId() + 1, 2);
            t.markDeathFxSent();
        }
        t.setMercyFinishing(true);
        t.setHealth(0.0F);
        t.die(t.damageSources().playerAttack(e.executor));
    }

    /**
     * 发奖那一拍：遗言，然后按消灭落定。按时走到、快进、关服补跑、宿敌离场拦截都经过这一步，
     * 落定出错只记日志，账留着等巡检或登录时补
     */
    private static void successRewards(Exec e) {
        if (e.success != null) {
            try {
                e.success.sayLastWords(e.executor);
            } catch (RuntimeException ex) {
                LogUtil.error("怜悯之击成功收尾时说遗言出错", ex);
            }
            if (e.choiceWindow) {
                announce(e);
                return;
            }
            try {
                FinaleSettlement.resolveNow(e.executor, e.success.generation(), FinaleSettlement.Choice.VANQUISH);
            } catch (RuntimeException ex) {
                LogUtil.error("怜悯之击成功收尾时落定出错，账留着等巡检或登录时补", ex);
            }
        } else if (e.demo && e.target instanceof KuvaMasterEntity lich) {
            // 演示目标什么都不给，遗言照说（录通讯窗用）
            sayAs(lich, LichLine.VANQUISHED, e.executor);
        }
    }

    // ==================== 选择窗口 ====================

    private static boolean convertEnabled() {
        try {
            return ModConfig.KUVA_LICH.convertEnabled.get();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static int choiceTicks() {
        try {
            return ModConfig.KUVA_LICH.convertChoiceSeconds.get() * 20;
        } catch (RuntimeException ex) {
            return 200;
        }
    }

    /**
     * 选择窗口那一拍：账上真有这一场的东西才开窗（定案时卡为空会跳过记账），没有就直接按消灭排后段。
     * 演示目标没有账也开窗，录像、看界面用
     */
    private static void openChoice(Exec e, KuvaMasterEntity lich) {
        if (e.choice != 0) {
            return;
        }
        boolean ledger = e.success != null && PlayerLedger.hasFinale(e.executor);
        if (!ledger && !e.demo) {
            choose(e, false);
            return;
        }
        int ticks = choiceTicks();
        e.awaiting = true;
        e.allowConvert = convertEnabled();
        e.choiceDeadline = serverTicks + ticks;
        lich.setMercyChoice(AbstractKuva.CHOICE_WAITING);
        String release = ledger && e.allowConvert ? AllyRoster.wouldRelease(e.executor) : "";
        MercyChoiceOpenPacket.send(e.executor, new MercyChoiceOpenPacket(lich.getId(), e.allowConvert, ticks, release));
    }

    /**
     * 落定（收到选择、到了截止、或者被快进 / 打断 / 关服时按消灭）：写同步数据、当场落定进收件箱并存盘，
     * 再接着排后段。后段第一步至少晚两 tick：截止落定发生在 tick 末尾，同步数据下一 tick 才发出去，效果包不能抢在结果前面。
     * 落定抛出只记日志，账留着，发奖那一拍再试一次，再不行由巡检按消灭补
     */
    private static void choose(Exec e, boolean convert) {
        if (e.choice != 0) {
            return;
        }
        e.awaiting = false;
        e.choice = convert ? AbstractKuva.CHOICE_CONVERT : AbstractKuva.CHOICE_VANQUISH;
        KuvaMasterEntity lich = (KuvaMasterEntity) e.target;
        lich.setMercyChoice(e.choice);
        if (e.success != null) {
            if (convert) {
                PlayerLedger.markFinaleConvert(e.executor, e.success.generation());
            }
            try {
                e.settledResult = FinaleSettlement.resolve(e.executor, e.success.generation(), choiceOf(e));
            } catch (RuntimeException ex) {
                LogUtil.error("终结选择落定时出错，账留着，发奖那一拍再试", ex);
            }
        }
        MercyBeats.Lich b = MercyBeats.lich(e.variant);
        // 窗口开着时 age 停在开窗那一 tick；开窗之前就被快进的，后段整体往后挪，保证排在已有步骤之后
        int base = Math.max(e.age, b.choiceOpenTick);
        int shift = base - b.choiceOpenTick;
        int first = base + 2;
        if (convert) {
            sayAs(lich, LichLine.CONVERTED, e.executor);
            int end = Math.max(first, b.convertEndTick + shift);
            e.add(Math.max(first, b.convertLeaveTick + shift), StepKind.CONVERT_LEAVE, () -> convertLeave(e, lich));
            e.add(end, StepKind.PLAYER, () -> announce(e));
            e.add(end, StepKind.UNLOCK, () -> unlock(e));
            e.add(end, StepKind.PLAYER, () -> successComplete(e));
            return;
        }
        int reward = Math.max(first, b.successRewardTick + shift);
        e.add(Math.max(first, b.successDeathTick + shift), StepKind.KILL, () -> kill(e));
        e.add(reward, StepKind.PLAYER, () -> successRewards(e));
        e.add(reward, StepKind.UNLOCK, () -> unlock(e));
        e.add(Math.max(reward, b.successCompleteTick + shift), StepKind.PLAYER, () -> successComplete(e));
    }

    private static FinaleSettlement.Choice choiceOf(Exec e) {
        return e.choice == AbstractKuva.CHOICE_CONVERT ? FinaleSettlement.Choice.CONVERT : FinaleSettlement.Choice.VANQUISH;
    }

    /** 选择那一刻落定的结果发聊天、投递；那一刻落定失败的这里再试一次 */
    private static void announce(Exec e) {
        if (e.announced || e.success == null) {
            return;
        }
        e.announced = true;
        if (e.settledResult == null) {
            try {
                e.settledResult = FinaleSettlement.resolve(e.executor, e.success.generation(), choiceOf(e));
            } catch (RuntimeException ex) {
                LogUtil.error("终结重试落定也失败了，账留着等巡检或登录时按消灭补", ex);
                return;
            }
        }
        if (e.settledResult != null) {
            e.settledResult.announce(e.executor);
        }
    }

    /** 感化：玄骸化雾离场。不走 beginDeparture，那里会先问 interceptDeparture，这一场还活着会被当成快进拦下 */
    private static void convertLeave(Exec e, KuvaMasterEntity lich) {
        if (!lich.isAlive() || lich.isRemoved()) {
            return;
        }
        e.expectRemoval = true;
        lich.setMercyState(MercyState.LEAVING);
        downedChanged(lich, false);
        lich.departNow(EncounterEnd.VANQUISHED.ordinal());
    }

    /**
     * 处决者发来的选择。只认：他自己那一场、窗口开着且还没定、截止加宽限之内、值是 0 或 1、
     * 选感化时开窗那一刻允许感化；之后再来的一律丢掉
     */
    public static void handleChoice(ServerPlayer player, byte value) {
        Exec e = LOCKED.get(player.getUUID());
        if (e == null || e.finished || !e.awaiting || e.choice != 0) {
            return;
        }
        if (value != MercyChoicePacket.CONVERT && value != MercyChoicePacket.VANQUISH) {
            return;
        }
        if (serverTicks > e.choiceDeadline + CHOICE_GRACE_TICKS) {
            return;
        }
        boolean convert = value == MercyChoicePacket.CONVERT;
        if (convert && !e.allowConvert) {
            return;
        }
        // 这条路不在 tick 的兜底里，出错自己收，不然处决者会一直锁着
        try {
            choose(e, convert);
        } catch (RuntimeException | LinkageError ex) {
            LogUtil.error("处理终结选择时出错", ex);
            recoverFromError(e);
        }
    }

    private static void successComplete(Exec e) {
        if (e.success != null && !e.executor.hasDisconnected()) {
            DecryptionHudPacket.sendComplete(e.executor, e.success.riddles(), e.success.usesAfter(), e.success.lichName());
        }
    }

    /**
     * 失败时给不给一张 Oull：这次至少判对了一格才掷，概率看配置（百分比）。
     * 在定案时掷好，收尾和快进都只是照结果发
     */
    static boolean rollOull(int prefix) {
        int chance = ModConfig.KUVA_LICH.oullDropChance.get();
        return prefix >= 1 && chance > 0 && (chance >= 100 || ThreadLocalRandom.current().nextInt(100) < chance);
    }

    /** 失败收尾：掷中了就给处决者一张 Oull（背包满了掉在脚下），再发一句聊天 */
    private static void giveOull(Exec e) {
        if (!e.oullDrop || e.demo) {
            return;
        }
        e.oullDrop = false;
        ServerPlayer p = e.executor;
        InventoryUtil.giveOrDrop(p, new ItemStack(KuvaLichItems.OULL_CARD.get()));
        if (!p.hasDisconnected()) {
            PlayerFeedback.chat(p, KuvaPalette.ACCENT, "message.kuvalich.mercy.oull",
                    Component.translatable(KuvaLichItems.OULL_CARD.get().getDescriptionId()));
        }
    }

    private static void failureProgress(Exec e, KuvaMasterEntity lich) {
        if (e.demo) {
            return;
        }
        RequiemCard card = card(e.executor);
        if (card != null) {
            lich.giveDecryptionProgress(e.executor, card);
        }
    }

    /**
     * 失败收尾：玄骸化雾逃走、结算这次遭遇。按时走到这一步时客户端已经放过冲击和化雾，直接移除；
     * 被中断快进时要它自己演一遍赤毒雾离场
     */
    private static void escape(Exec e, KuvaMasterEntity lich, boolean withFx) {
        if (!lich.isAlive() || lich.isRemoved()) {
            return;
        }
        if (lich.getMercyState() != MercyState.LEAVING) {
            lich.setMercyState(MercyState.LEAVING);
        }
        if (e.demo) {
            if (withFx) {
                lich.departNow(EncounterEnd.MERCY_FAILED.ordinal());
            } else {
                e.expectRemoval = true;
                lich.discard();
            }
            return;
        }
        e.expectRemoval = !withFx;
        ServerPlayer owner = e.executor.hasDisconnected() ? null : e.executor;
        LichDirector.endEncounter(owner, lich, EncounterEnd.MERCY_FAILED, withFx);
    }

    // ==================== 每 tick 推进 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        serverTicks++;
        if (!GRACE.isEmpty()) {
            GRACE.values().removeIf(until -> until < serverTicks);
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Exec e : new ArrayList<>(ACTIVE)) {
            if (e.finished) {
                ACTIVE.remove(e);
                continue;
            }
            try {
                tickExec(e);
            } catch (RuntimeException ex) {
                LogUtil.error("推进怜悯之击时出错", ex);
                recoverFromError(e);
            }
        }
    }

    /**
     * 推进抛异常后的收尾：只停掉这一场不够，目标会一直停在处决中（不动、打不了、谁也处决不了）。
     * 没定案的回到倒地；定案成功的账还在就重试一次落定，目标走离场；判错 / 挣开的直接离场
     */
    private static void recoverFromError(Exec e) {
        try {
            if (!e.settled) {
                cancelToDowned(e);
                return;
            }
            finish(e);
            unlock(e);
            if (e.choiceWindow && e.choice == 0) {
                choose(e, false);
            }
            if (e.success != null) {
                try {
                    if (e.choiceWindow) {
                        announce(e);
                    } else {
                        FinaleSettlement.resolveNow(e.executor, e.success.generation(), FinaleSettlement.Choice.VANQUISH);
                    }
                } catch (RuntimeException ex) {
                    LogUtil.error("出错后重试落定也失败了，账留着等巡检或登录时补", ex);
                }
            }
            AbstractKuva t = e.target;
            if (!t.isAlive() || t.isRemoved()) {
                return;
            }
            if (!(t instanceof KuvaMasterEntity lich)) {
                t.beginExpire();
                return;
            }
            if (e.failure() || e.advance()) {
                escape(e, lich, true);
                return;
            }
            lich.setMercyState(MercyState.LEAVING);
            downedChanged(lich, false);
            lich.departNow(EncounterEnd.VANQUISHED.ordinal());
        } catch (RuntimeException ex) {
            LogUtil.error("怜悯之击出错后的收尾也出错了", ex);
            finish(e);
            unlock(e);
        }
    }

    private static void tickExec(Exec e) {
        if (faultOnTick != null) {
            faultOnTick.run();
        }
        if (e.awaiting && serverTicks > e.choiceDeadline + CHOICE_GRACE_TICKS) {
            choose(e, false);
        }
        if (!e.awaiting) {
            e.age++;
            while (!e.finished && !e.awaiting && e.next < e.steps.size() && e.steps.get(e.next).tick() <= e.age) {
                Step s = e.steps.get(e.next++);
                s.action().run();
            }
            if (!e.awaiting && e.next >= e.steps.size()) {
                finish(e);
                unlock(e);
            }
        }
        if (e.target.isRemoved()) {
            if (!e.finished && !e.expectRemoval) {
                interrupt(e, Gone.TARGET_GONE);
            }
        } else {
            lockTarget(e);
        }
        if (e.locked) {
            if (executorGone(e)) {
                interrupt(e, e.executor.isSpectator() ? Gone.SPECTATOR : Gone.TELEPORTED);
                return;
            }
            holdExecutor(e);
        }
    }

    /** 目标每 tick 锁朝向、速度清零 */
    private static void lockTarget(Exec e) {
        AbstractKuva t = e.target;
        t.setYRot(e.targetYaw);
        t.yRotO = e.targetYaw;
        t.setYBodyRot(e.targetYaw);
        t.setYHeadRot(e.targetYaw);
        Vec3 v = t.getDeltaMovement();
        t.setDeltaMovement(0.0D, Math.min(0.0D, v.y), 0.0D);
    }

    private static boolean executorGone(Exec e) {
        ServerPlayer p = e.executor;
        if (p.hasDisconnected() || p.isRemoved() || !p.isAlive() || p.isSpectator() || p.level() != e.target.level()) {
            return true;
        }
        return e.age >= ENFORCE_FROM && p.position().distanceTo(e.lockPos) > INTERRUPT_DISTANCE;
    }

    /** 处决者：第 6 tick 起强制站位（差得多就拉回来、差一点只清速度），判错后的推开每 tick 走一小段 */
    private static void holdExecutor(Exec e) {
        if (e.age < ENFORCE_FROM || e.roaming) {
            return;
        }
        ServerPlayer p = e.executor;
        if (e.pushLeft > 0) {
            e.pushLeft--;
            Vec3 next = e.lockPos.add(e.f.scale(PUSH_STEP));
            AABB box = p.getDimensions(Pose.STANDING).makeBoundingBox(next);
            if (p.level().noCollision(p, box)) {
                e.lockPos = next;
                snapExecutor(e);
                return;
            }
            // 路上有墙就停在墙前
            e.pushLeft = 0;
        }
        double d = p.position().distanceTo(e.lockPos);
        if (d > SNAP_DISTANCE) {
            snapExecutor(e);
        } else if (d > DRIFT_DISTANCE) {
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
        }
    }

    /** 把处决者放到锁定站位、看向目标锚点 */
    private static void snapExecutor(Exec e) {
        ServerPlayer p = e.executor;
        Vec3 at = e.lockPos;
        Vec3 look = MercyRules.anchor(e.target).subtract(at.add(0.0D, p.getEyeHeight(Pose.STANDING), 0.0D));
        float yaw = yawOf(look);
        float pitch = (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        p.connection.teleport(at.x, at.y, at.z, yaw, pitch);
        p.setDeltaMovement(Vec3.ZERO);
    }

    // ==================== 中断、快进、解锁 ====================

    private static void interrupt(Exec e, Gone reason) {
        if (e.finished) {
            return;
        }
        if (LogUtil.isDebugEnabled()) {
            LogUtil.debug("怜悯之击被打断：" + reason + (e.settled ? "（已定案，快进）" : "（未定案，作废）"));
        }
        if (!e.settled) {
            cancelToDowned(e);
            return;
        }
        fastForward(e, true);
    }

    /** 定案前作废：目标回到倒地、计时接着走（不足的补到下限），重发倒地包（带续上位），处决者解锁 */
    private static void cancelToDowned(Exec e) {
        finish(e);
        unlock(e);
        AbstractKuva t = e.target;
        if (!t.isAlive() || t.isRemoved() || t.getMercyState() != MercyState.EXECUTING) {
            return;
        }
        int floor = ModConfig.KUVA_LICH.mercyResumeFloorSeconds.get() * 20;
        t.setMercyRemaining(Math.max(t.getMercyRemainingServer(), floor));
        t.setMercyActorId(resolveQualifiedId(t));
        t.setMercyChoice(AbstractKuva.CHOICE_NONE);
        t.setMercyState(MercyState.DOWNED);
        sendDowned(t, true);
    }

    /**
     * 快进：把还没走的步骤按原顺序立刻做完。
     *
     * @param escapeWithFx 处决者 / 目标离开引起的（失败的玄骸要自己演赤毒雾逃走）；false = 宿敌的离场引起的，
     *                     那边已经在结算这次离场，失败的移除和结算都不做，嘲讽也不发
     */
    private static void fastForward(Exec e, boolean escapeWithFx) {
        finish(e);
        if (e.awaiting) {
            choose(e, false);
        }
        // 循环读 steps.size() 的实时值：碰到开窗按消灭落定，后段接在后面一起跑完
        while (e.next < e.steps.size()) {
            Step s = e.steps.get(e.next++);
            switch (s.kind()) {
                case COMM, TAUNT, PUSH, TURN, LAND -> {
                }
                case CHOICE_OPEN -> choose(e, false);
                case LEAVE -> {
                    if (escapeWithFx) {
                        s.action().run();
                    }
                }
                case ESCAPE -> {
                    if (escapeWithFx && e.target instanceof KuvaMasterEntity lich) {
                        escape(e, lich, true);
                    }
                }
                default -> s.action().run();
            }
        }
        unlock(e);
    }

    private static void finish(Exec e) {
        e.finished = true;
        ACTIVE.remove(e);
    }

    private static void unlock(Exec e) {
        if (!e.locked) {
            return;
        }
        e.locked = false;
        if (LOCKED.get(e.executorId) == e) {
            LOCKED.remove(e.executorId);
        }
        GRACE.put(e.executorId, serverTicks + GRACE_TICKS);
    }

    @Nullable
    private static Exec execFor(AbstractKuva target) {
        for (Exec e : ACTIVE) {
            if (!e.finished && e.target == target) {
                return e;
            }
        }
        return null;
    }

    /** 真玄骸、三刀全对、已经写了账的一场 */
    private static boolean settledSuccess(Exec e) {
        return e.settled && e.success != null;
    }

    /** 这个玩家有没有还没走完的处决（定案后、发奖前账上会有一条，巡检看到这个就不去动它） */
    public static boolean hasActiveExec(UUID executorId) {
        for (Exec e : ACTIVE) {
            if (!e.finished && e.executorId.equals(executorId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inGrace(UUID id) {
        Long until = GRACE.get(id);
        return until != null && until >= serverTicks;
    }

    private static boolean isLocked(Entity entity) {
        return entity instanceof ServerPlayer && LOCKED.containsKey(entity.getUUID());
    }

    // ==================== 事件：锁定、无敌、中断 ====================

    /** 处决者在处决中和解锁后的余量里不受伤；绕过无敌的（/kill）挡不住 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        Entity e = event.getEntity();
        if (!(e instanceof ServerPlayer) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        UUID id = e.getUUID();
        if (LOCKED.containsKey(id) || inGrace(id)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (isLocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 处决中扔东西：放得回背包的放回去（扔出时物品已经从背包里拿走了），放不下的照常扔出。
     * 这里不能再调 {@code player.drop}：它又发一次扔物事件，背包满着就自己调自己直到栈溢出。
     * 只管玩家自己扔的（Q、界面里往外扔，扔出的物品记着是谁扔的）。/give 和任务奖励这类「发东西」
     * 先把整份放进背包、再扔一个只播拾取动画的假掉落物，也走扔物事件，把它放回背包就多出一件。
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player p = event.getPlayer();
        if (!isLocked(p)) {
            return;
        }
        ItemEntity item = event.getEntity();
        if (item.getOwner() != p) {
            return;
        }
        ItemStack stack = item.getItem();
        int total = stack.getCount();
        int kept = putBack(p.getInventory(), stack);
        if (kept <= 0) {
            return;
        }
        p.getInventory().setChanged();
        if (kept >= total) {
            event.setCanceled(true);
        } else {
            item.setItem(stack.copyWithCount(total - kept));
        }
    }

    /**
     * 按格放进背包，返回放进去的件数，不改 {@code stack}。不用 {@code Inventory.add}：创造模式下放不下的部分会被它清零
     */
    private static int putBack(Inventory inv, ItemStack stack) {
        int kept = 0;
        while (kept < stack.getCount()) {
            int slot = inv.getSlotWithRemainingSpace(stack);
            if (slot < 0) {
                slot = inv.getFreeSlot();
            }
            if (slot < 0) {
                break;
            }
            ItemStack there = inv.getItem(slot);
            int room = there.isEmpty() ? Math.min(stack.getMaxStackSize(), inv.getMaxStackSize())
                    : Math.min(there.getMaxStackSize(), inv.getMaxStackSize()) - there.getCount();
            int n = Math.min(room, stack.getCount() - kept);
            if (n <= 0) {
                break;
            }
            if (there.isEmpty()) {
                inv.setItem(slot, stack.copyWithCount(n));
            } else {
                there.grow(n);
            }
            kept += n;
        }
        return kept;
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (isLocked(event.getEntity()) && event.getEntity() instanceof ServerPlayer sp) {
            sp.closeContainer();
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            onExecutorGone(sp, Gone.LOGOUT);
            LAST_REQUEST.remove(sp.getUUID());
            LAST_DENY.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            onExecutorGone(sp, Gone.DIMENSION);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && !event.isCanceled()) {
            onExecutorGone(sp, Gone.DEATH);
        }
    }

    /** 目标被别的东西直接移除（别的模组 discard、区块卸载）当中断；处决者离开世界同理 */
    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || ACTIVE.isEmpty()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof AbstractKuva target) {
            Exec e = execFor(target);
            if (e != null && !e.expectRemoval && !target.isMercyFinishing()) {
                interrupt(e, Gone.TARGET_GONE);
            }
        } else if (entity instanceof ServerPlayer sp && LOCKED.containsKey(sp.getUUID())) {
            onExecutorGone(sp, Gone.DIMENSION);
        }
    }

    /** 关服：已经定案的把给玩家的那部分做完（按消灭落定、进度），其余全部清掉。落定的东西随玩家数据一起存盘 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (Exec e : new ArrayList<>(ACTIVE)) {
            if (e.finished || !e.settled) {
                continue;
            }
            finish(e);
            // 先解锁再补发，补发的东西走平时的给物品路径，不经过处决中的扔物拦截
            unlock(e);
            try {
                if (e.awaiting) {
                    choose(e, false);
                }
            } catch (RuntimeException ex) {
                LogUtil.error("关服时按消灭落定选择窗口出错", ex);
            }
            while (e.next < e.steps.size()) {
                Step s = e.steps.get(e.next++);
                if (s.kind() == StepKind.PLAYER || s.kind() == StepKind.CHOICE_OPEN) {
                    try {
                        if (s.kind() == StepKind.CHOICE_OPEN) {
                            choose(e, false);
                        } else {
                            s.action().run();
                        }
                    } catch (RuntimeException ex) {
                        LogUtil.error("关服时补发怜悯之击的奖励出错", ex);
                    }
                }
            }
        }
        ACTIVE.clear();
        LOCKED.clear();
        GRACE.clear();
        LAST_REQUEST.clear();
        LAST_DENY.clear();
        NEXT_VARIANT.clear();
        NEXT_OUTCOME.clear();
        DEMO_CARDS.clear();
        FROZEN.clear();
        serverTicks = 0;
    }

    // ==================== 演示 / 管理命令的入口 ====================

    /** 指定这个玩家下一次处决用哪套；null = 清掉、回到随机。只管下一次 */
    public static void setNextVariant(ServerPlayer player, @Nullable MercyVariant variant) {
        if (variant == null) {
            NEXT_VARIANT.remove(player.getUUID());
        } else {
            NEXT_VARIANT.put(player.getUUID(), variant);
        }
    }

    /** 指定下一次处决<b>演示目标</b>的结果；null 或 {@link Outcome#REAL} = 按身上的卡判。真玄骸不看它 */
    public static void setNextOutcome(ServerPlayer player, @Nullable Outcome outcome) {
        if (outcome == null || outcome == Outcome.REAL) {
            NEXT_OUTCOME.remove(player.getUUID());
        } else {
            NEXT_OUTCOME.put(player.getUUID(), outcome);
        }
    }

    /**
     * 演示目标在处决界面上显示的三张卡（卡 id 0~8，-1 = 空）和处决前剩余次数（0~3），{@code returnsItems} = 成功时演「归还物品」。
     * 传 null 清掉，回到用处决者身上的卡
     */
    public static void setDemoCards(ServerPlayer player, @Nullable int[] ids, @Nullable int[] remaining, boolean returnsItems) {
        if (ids == null) {
            DEMO_CARDS.remove(player.getUUID());
            return;
        }
        int[] i3 = {-1, -1, -1};
        int[] r3 = {3, 3, 3};
        for (int i = 0; i < 3; i++) {
            if (i < ids.length) {
                i3[i] = ids[i] >= 0 && ids[i] <= OULL.ID ? ids[i] : -1;
            }
            if (remaining != null && i < remaining.length) {
                r3[i] = Mth.clamp(remaining[i], 0, 3);
            }
        }
        DEMO_CARDS.put(player.getUUID(), new DemoCards(i3, r3, returnsItems));
    }

    /**
     * 立刻处决这个倒地目标：跳过距离和视线检查，直接把人放到站位上开始。真玄骸只有主人能处决；
     * 奴仆和演示目标的资格人改成下命令的人。
     *
     * @return null 表示开始了；否则是失败原因（英文，给命令反馈用）
     */
    @Nullable
    public static String executeNow(ServerPlayer player, AbstractKuva target) {
        if (!target.isAlive() || target.getMercyState() != MercyState.DOWNED) {
            return "target is not down";
        }
        if (target.level() != player.level()) {
            return "target is in another dimension";
        }
        if (LOCKED.containsKey(player.getUUID())) {
            return "you are already executing";
        }
        if (!MercyRules.canAct(player)) {
            return "you cannot act right now";
        }
        if (target instanceof KuvaMasterEntity lich && lich.isOwned() && !lich.isDemo()) {
            if (!lich.isOwner(player)) {
                return "only the owner can execute this Lich";
            }
        } else {
            target.setMercyQualified(player.getUUID());
            target.setMercyActorId(player.getId());
        }
        start(player, target, true);
        return null;
    }

    /** 把倒地目标的剩余时间设成这么多秒；不在倒地返回 false */
    public static boolean setTimer(AbstractKuva target, int seconds) {
        if (target.getMercyState() != MercyState.DOWNED) {
            return false;
        }
        target.setMercyRemaining(Math.max(1, seconds * 20));
        sendDowned(target, false);
        return true;
    }

    /** 冻结 / 恢复这个世界里所有倒地计时（不存盘，关服就没了） */
    public static void setFrozen(ServerLevel level, boolean frozen) {
        if (frozen) {
            FROZEN.add(level.dimension());
        } else {
            FROZEN.remove(level.dimension());
        }
    }

    /**
     * 一键复位：范围内所有倒地 / 处决中 / 起身中的目标回到满血、没有状态，相关处决作废、处决者解锁（不写任何数据）。
     * 已经在消散、死亡的不管
     *
     * @return 复位了几只
     */
    public static int resetAround(ServerLevel level, Vec3 center, double radius) {
        int n = 0;
        AABB box = new AABB(center, center).inflate(radius);
        for (AbstractKuva t : level.getEntitiesOfClass(AbstractKuva.class, box,
                k -> k.isAlive() && (k.getMercyState() == MercyState.DOWNED || k.getMercyState() == MercyState.EXECUTING
                        || k.getMercyState() == MercyState.RISING))) {
            Exec e = execFor(t);
            if (e != null && settledSuccess(e)) {
                // 已经定案成功的回不去（卡已经扣了、这一轮已经结束）：快进到死、按消灭落定
                try {
                    fastForward(e, true);
                } catch (RuntimeException ex) {
                    LogUtil.error("复位时快进已定案的处决出错", ex);
                    recoverFromError(e);
                }
                n++;
                continue;
            }
            if (e != null) {
                finish(e);
                unlock(e);
            }
            if (t instanceof KuvaMasterEntity lich) {
                downedChanged(lich, false);
            }
            t.resetMercy();
            n++;
        }
        return n;
    }

    /**
     * 中止自己正在进行的处决：一律按「定案前中断」处理（目标回倒地、不写数据），录像重来用。
     * 真玄骸过了定案不能中止（结果已经写进卡里了）。
     *
     * @return null 表示中止了；否则是失败原因（英文）
     */
    @Nullable
    public static String stop(ServerPlayer player) {
        Exec found = null;
        for (Exec e : ACTIVE) {
            if (!e.finished && e.executorId.equals(player.getUUID())) {
                found = e;
            }
        }
        if (found == null) {
            return "you are not executing anything";
        }
        if (settledSuccess(found)) {
            // 定案成功的回不去，只能提前收尾：快进到死、按消灭落定
            try {
                fastForward(found, true);
            } catch (RuntimeException ex) {
                LogUtil.error("中止时快进已定案的处决出错", ex);
                recoverFromError(found);
            }
            return null;
        }
        if (found.settled && !found.demo) {
            return "the result is already settled";
        }
        if (found.settled) {
            // 演示目标过了定案：失败已经离场中、成功已经死了的回不去，只解锁
            AbstractKuva t = found.target;
            if (t.isAlive() && t.getMercyState() == MercyState.LEAVING) {
                t.setMercyState(MercyState.EXECUTING);
            }
        }
        cancelToDowned(found);
        return null;
    }

    // ==================== 工具 ====================

    /** 玄骸说一句：真玄骸照主人发（主人不在线不发），演示目标用它自己的名字和外观对处决者说 */
    private static void sayAs(KuvaMasterEntity lich, LichLine line, @Nullable ServerPlayer to) {
        if (lich.isDemo()) {
            if (to != null && !to.hasDisconnected()) {
                LichComms.say(to, line, lich);
            }
            return;
        }
        ServerPlayer owner = lich.getOwnerPlayer();
        if (owner != null) {
            // 传实体：定案时这一轮已经结束、卡上没有名字，不传的话定案之后的台词一句也发不出去
            LichComms.say(owner, line, lich);
        }
    }

    private static void deny(ServerPlayer player, String key) {
        Long last = LAST_DENY.get(player.getUUID());
        if (last != null && serverTicks - last < DENY_COOLDOWN) {
            return;
        }
        LAST_DENY.put(player.getUUID(), serverTicks);
        PlayerFeedback.actionBar(player, KuvaPalette.WARN, "kuvalich.mercy.deny." + key);
    }

    @Nullable
    private static RequiemCard card(ServerPlayer player) {
        return player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
    }

    /** 进行中的处决场数（测试用） */
    public static int activeCount() {
        int n = 0;
        for (Exec e : ACTIVE) {
            if (!e.finished) {
                n++;
            }
        }
        return n;
    }

    /** 这只目标正在被处决时，处决已经走了多少 tick；没有为 -1（测试用） */
    public static int ageOf(AbstractKuva target) {
        Exec e = execFor(target);
        return e == null ? -1 : e.age;
    }
}
