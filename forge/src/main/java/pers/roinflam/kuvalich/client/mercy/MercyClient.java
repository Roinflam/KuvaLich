package pers.roinflam.kuvalich.client.mercy;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.KuvaKeyMappings;
import pers.roinflam.kuvalich.client.comm.CommHud;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.FxTimeline;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.sound.KuvaMixer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.mercy.MercyAnimSelector;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Set;

/**
 * 怜悯之击的客户端状态：本地玩家是不是处决者、有没有在运镜、各个目标正在演到哪里
 *
 * <p>收到开始处决的效果包时建一份 {@link Exec}（按目标、按处决者各记一次），排好整条演出时间轴
 * （{@link MercyTimeline}）。跑这份时间轴的不只是处决者：48 格内收到包的旁观者也各自排一份，
 * 看到的是世界里的动作、火花和声音；镜头、界面、压混音、输入锁只在处决者本人的客户端上。</p>
 *
 * <p>晚进视野的客户端没收到开始的包，就没有这份状态：目标按同步来的计划位摆倒地循环（动画那边自己选），
 * 处决者照常画，不补演出。</p>
 *
 * <p>处决者本人在处决期间：移动、鼠标转向、滚轮、攻击 / 使用 / 丢弃 / 切视角 / 热栏这些键都被吃掉，
 * 聊天和 Esc 照常；原 HUD 只留聊天、字幕、通讯窗和处决自己的几层。时间轴交还、并且目标的同步状态已经离开
 * 「处决中」（或者目标已经没了）两个都满足才放开输入。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MercyClient {

    /** 压混音用的 key */
    public static final String DUCK_KEY = "mercy";

    /** 处决运镜时还留着的 HUD 层，其余一律不画（别的模组的也一样，白名单制是有意的） */
    public static final Set<ResourceLocation> HUD_ALLOW = Set.of(
            new ResourceLocation("minecraft", "chat_panel"),
            new ResourceLocation("minecraft", "subtitles"),
            new ResourceLocation(Reference.MOD_ID, "mercy_hud"),
            new ResourceLocation(Reference.MOD_ID, "rank_banner"),
            new ResourceLocation(Reference.MOD_ID, "kuva_screen_fx"),
            new ResourceLocation(Reference.MOD_ID, "mercy_prompt"),
            MercyCamera.SHADE_ID,
            CommHud.OVERLAY_ID);

    /** 站位缓入用多久 */
    private static final int EASE_IN_MS = 250;
    /** 服务端迟迟不改状态时，交还后最多再锁这么久输入 */
    private static final int RELEASE_TIMEOUT_MS = 3000;
    /** 判断「目标本来就该消失了」时放宽的余量（网络和两端时钟差） */
    private static final int VANISH_SLACK_MS = 300;

    /**
     * 本地玩家看到的这一场处决
     *
     * @param elapsedMs 已过毫秒，按演出钟，冻帧时停住
     * @param cinematic 本地玩家是处决者且镜头被接管
     */
    public record View(int targetId, int executorId, MercyPlan.Plan plan, long elapsedMs, boolean cinematic) {
    }

    /** 一场处决在这个客户端上的全部状态 */
    static final class Exec {
        final int targetId;
        final int executorId;
        final MercyPlan.Plan plan;
        /** 开始时收到的原始计划位（目标的动画按它选片段） */
        final int planBits;
        final MercyVariant variant;
        /** 目标脚底（开始那一刻） */
        final Vec3 pl;
        /** 处决者站位（脚底） */
        final Vec3 pe;
        /** 从目标指向处决者的水平单位向量 */
        final Vec3 f;
        /** {@code Y × F}：从处决者看过去是它的右手边 */
        final Vec3 s;
        /** 站位离目标多远（水平，格） */
        final double dist;
        /** 绕背往 {@link #s} 那边走是 1，反过来是 -1 */
        final int side;
        /** 本地玩家就是处决者 */
        final boolean local;
        /** 本地玩家是玄骸处决的处决者（藏 HUD、界面声、压混音都看它） */
        final boolean cinematic;
        /** 开始那一刻的「成功后跳过第三人称」选项 */
        final boolean shortOutro;
        final int handbackMs;
        final int hudUntilMs;
        final int endMs;
        /** 这一场每一刀刺入的时刻（开始时算好，镜头和刀模每帧读，不再分配） */
        final int[] stabs;
        final MercyTimeline timeline;
        final MercyPose.Track pose;
        @Nullable
        final FxTimeline hero;

        long startMs;
        /** 冻帧的时刻；小于 0 = 没冻 */
        long frozenAt = -1L;
        /** 目标的同步状态已经到过「处决中」（效果包常比同步数据先到，到过以后再离开才算中断） */
        boolean seenExecuting;
        boolean handedBack;
        boolean released;

        // 本地处决者的站位缓入
        Vec3 startPos;
        float startYaw;
        float startPitch;
        boolean easeDone;

        // 镜头（MercyCamera 用）
        final MercyCamera.ShotCache shots = new MercyCamera.ShotCache();

        Exec(int targetId, int executorId, int planBits, Vec3 pl, Vec3 pe, Vec3 f, boolean local, Entity target) {
            this.targetId = targetId;
            this.executorId = executorId;
            this.plan = MercyPlan.decode(planBits);
            this.planBits = planBits;
            this.variant = plan.variant();
            this.pl = pl;
            this.pe = pe;
            this.f = f;
            this.s = new Vec3(f.z, 0.0D, -f.x);
            this.dist = Math.sqrt((pe.x - pl.x) * (pe.x - pl.x) + (pe.z - pl.z) * (pe.z - pl.z));
            this.side = plan.mirror() ? -1 : 1;
            this.local = local;
            this.cinematic = local && !variant.thrall;
            this.shortOutro = EffectsDisplayConfig.mercyShortOutro();
            this.handbackMs = MercyTimeline.handbackMs(plan, shortOutro);
            this.hudUntilMs = cinematic ? MercyTimeline.hudUntilMs(plan, shortOutro) : 0;
            this.endMs = MercyTimeline.endMs(plan, shortOutro);
            this.stabs = MercyTimeline.stabTimes(plan);
            this.startMs = KuvaFxDirector.clockMs();
            this.timeline = MercyTimeline.build(this);
            this.pose = MercyPose.track(plan, shortOutro,
                    target instanceof AbstractKuva k ? k.mercyBodyScale() : 1.0F);
            FxTimeline h = KuvaFxDirector.start("mercy", MercyTimeline.chest(target), FxTier.S);
            this.hero = h.follow(target).until(endMs);
        }

        /** 已过毫秒（演出钟；冻帧时停在冻住的那一刻） */
        long elapsed() {
            return frozenAt >= 0L ? frozenAt : KuvaFxDirector.clockMs() - startMs;
        }

        @Nullable
        Entity target() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? null : level.getEntity(targetId);
        }

        @Nullable
        Entity executor() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? null : level.getEntity(executorId);
        }

        /** 目标胸口（处决者的头、刀尖都朝这里） */
        Vec3 anchor() {
            Entity t = target();
            return t != null ? MercyTimeline.chest(t) : pl.add(0.0D, 1.45D, 0.0D);
        }

        /** 处决者右手前方（抽刀那一道短光的位置） */
        Vec3 handPos() {
            Entity ex = executor();
            Vec3 base = ex != null ? ex.position() : pe;
            return base.add(0.0D, 1.15D, 0.0D).add(f.scale(-0.45D)).add(s.scale(0.32D));
        }

        /** 目标这一刻该朝哪（度） */
        float targetYaw(long t) {
            MercyVariant.Facing facing = variant.facing;
            if (!variant.thrall && facing == MercyVariant.Facing.AWAY
                    && MercyTimeline.outcome(plan) == MercyTimeline.Outcome.SUCCESS && t >= MercyTimeline.CLOSEUP_MS) {
                // 背对处决者的套路成功：切特写那一刻转成面向处决者跪着
                facing = MercyVariant.Facing.TOWARD;
            }
            float turn = turnProgress(t);
            if (turn > 0.0F) {
                // 背对处决者的套路挣开站起：一边站起来一边转半圈面向他
                float away = yawOf(f.scale(-1.0D));
                return away + 180.0F * side * turn;
            }
            Vec3 dir = switch (facing) {
                case TOWARD -> f;
                case AWAY -> f.scale(-1.0D);
                // 侧身朝 −S：处决者在它左手边（和服务端 facingYaw 一致）
                case SIDE -> s.scale(-1.0D);
            };
            return yawOf(dir);
        }

        /** 背对处决者的套路挣开站起时转身转到几成（0~1，easeInOutSine）；别的情况恒为 0 */
        float turnProgress(long t) {
            if (variant.thrall || variant.facing != MercyVariant.Facing.AWAY
                    || MercyTimeline.outcome(plan) != MercyTimeline.Outcome.ADVANCE) {
                return 0.0F;
            }
            int g = MercyTimeline.verdictFillMs(plan);
            float u = Mth.clamp((t - g - MercyTimeline.ADVANCE_TURN_FROM)
                    / (float) (MercyTimeline.ADVANCE_TURN_TO - MercyTimeline.ADVANCE_TURN_FROM), 0.0F, 1.0F);
            return (float) (0.5D - 0.5D * Math.cos(Math.PI * u));
        }
    }

    private static final Int2ObjectOpenHashMap<Exec> BY_TARGET = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Exec> BY_EXECUTOR = new Int2ObjectOpenHashMap<>();
    @Nullable
    private static Exec localExec;

    private MercyClient() {
    }

    // ==================== 查询 ====================

    /** 本地玩家是任何一场处决（含奴仆）的处决者、还没解锁 */
    public static boolean isActive() {
        return localExec != null && !localExec.released;
    }

    /** 该不该把原 HUD 藏起来（玄骸处决运镜中；失败时一直藏到横幅出来以后） */
    public static boolean hidesHud() {
        Exec e = localExec;
        return e != null && e.cinematic && e.elapsed() < e.hudUntilMs;
    }

    /** 本地玩家是一场玄骸处决的处决者、演出还没交还 */
    public static boolean isCinematic() {
        Exec e = localExec;
        return e != null && e.cinematic && e.elapsed() < e.handbackMs;
    }

    /** 这个实体（目标或处决者）正在演的处决已过多少毫秒；不在演返回 -1 */
    public static long elapsedMs(int entityId) {
        Exec e = BY_TARGET.get(entityId);
        if (e == null) {
            e = BY_EXECUTOR.get(entityId);
        }
        return e == null ? -1L : e.elapsed();
    }

    /** 本地玩家当前参与的那场处决；没有为 {@code null} */
    @Nullable
    public static View view() {
        Exec e = localExec;
        if (e == null) {
            return null;
        }
        long t = e.elapsed();
        return new View(e.targetId, e.executorId, e.plan, t, e.cinematic && t < e.handbackMs);
    }

    @Nullable
    static Exec local() {
        return localExec;
    }

    @Nullable
    static Exec byExecutor(int entityId) {
        return BY_EXECUTOR.isEmpty() ? null : BY_EXECUTOR.get(entityId);
    }

    @Nullable
    static Exec byTarget(int entityId) {
        return BY_TARGET.isEmpty() ? null : BY_TARGET.get(entityId);
    }

    static boolean anyActive() {
        return !BY_TARGET.isEmpty();
    }

    // ==================== 开始与结束 ====================

    /** 处决开始的效果包到了：a = 处决者 id + 1，b = 计划位，坐标 = 处决者站位 */
    public static void onBegin(KuvaFxPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Entity target = level.getEntity(packet.entityId());
        if (target == null) {
            return;
        }
        begin(target, packet.a - 1, packet.b, packet.pos());
    }

    /**
     * 本地起一场处决（效果包和调试命令共用）。{@code standPos} 和目标重合时（以目标为原点发的调试包），
     * 按处决者现在的位置和套路的站位距离自己算站位
     */
    static void begin(Entity target, int executorId, int planBits, Vec3 standPos) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Exec old = BY_TARGET.get(target.getId());
        if (old != null) {
            end(old);
        }
        old = BY_EXECUTOR.get(executorId);
        if (old != null) {
            end(old);
        }
        Entity executor = level.getEntity(executorId);
        Vec3 pl = target.position();
        Vec3 pe = standPos;
        Vec3 horiz = new Vec3(pe.x - pl.x, 0.0D, pe.z - pl.z);
        if (horiz.lengthSqr() < 0.09D) {
            Vec3 from = executor != null ? executor.position() : pl.add(target.getLookAngle());
            Vec3 d = new Vec3(from.x - pl.x, 0.0D, from.z - pl.z);
            if (d.lengthSqr() < 1.0E-4D) {
                Vec3 look = target.getLookAngle();
                d = new Vec3(look.x, 0.0D, look.z);
            }
            if (d.lengthSqr() < 1.0E-4D) {
                d = new Vec3(0.0D, 0.0D, 1.0D);
            }
            d = d.normalize();
            float dist = MercyPlan.decode(planBits).variant().standDist;
            pe = new Vec3(pl.x + d.x * dist, executor != null ? executor.getY() : pl.y, pl.z + d.z * dist);
            horiz = d;
        }
        Vec3 f = horiz.normalize();
        boolean local = mc.player != null && mc.player.getId() == executorId;
        Exec e = new Exec(target.getId(), executorId, planBits, pl, pe, f, local, target);
        BY_TARGET.put(e.targetId, e);
        BY_EXECUTOR.put(e.executorId, e);
        applyTargetFacing(e, target, 0L);
        if (local) {
            LocalPlayer p = mc.player;
            localExec = e;
            e.startPos = p.position();
            e.startYaw = p.getYRot();
            e.startPitch = p.getXRot();
            p.setDeltaMovement(0.0D, Math.min(0.0D, p.getDeltaMovement().y), 0.0D);
            MercyCamera.onBegin(e);
        }
        // 第 0 ms 的阶段（收掉倒地低鸣）当帧就放
        e.timeline.advance(e, 0L);
    }

    /** 这只目标的处决被中断了（服务端发来倒地续上、或者状态退回倒地）：立刻收场 */
    static void interrupt(int targetId) {
        Exec e = BY_TARGET.get(targetId);
        if (e != null) {
            end(e);
        }
    }

    /** 全部清掉（下线、换维度、换世界） */
    static void clearAll() {
        for (Exec e : BY_TARGET.values().toArray(new Exec[0])) {
            end(e);
        }
        BY_TARGET.clear();
        BY_EXECUTOR.clear();
        localExec = null;
    }

    /** 收场：取消时间轴、改回视角、放开输入和混音、刀模和藏起来的处决者都恢复 */
    private static void end(Exec e) {
        BY_TARGET.remove(e.targetId, e);
        BY_EXECUTOR.remove(e.executorId, e);
        if (e.hero != null) {
            e.hero.cancel();
        }
        if (e == localExec) {
            MercyCamera.restore(e);
            KuvaMixer.release(DUCK_KEY, 150);
            localExec = null;
        }
    }

    // ==================== 冻帧（调试命令） ====================

    /** 把本地正在进行的处决冻在 {@code ms}；本地没有就冻随便哪一场。没有任何处决返回 false */
    static boolean freeze(long ms) {
        Exec e = localExec;
        if (e == null) {
            for (Exec x : BY_TARGET.values()) {
                e = x;
                break;
            }
        }
        if (e == null) {
            return false;
        }
        e.frozenAt = Math.max(0L, ms);
        e.timeline.seek(e, e.frozenAt);
        return true;
    }

    /** 解冻：从冻住的那一刻接着走 */
    static boolean unfreeze() {
        boolean any = false;
        for (Exec e : BY_TARGET.values()) {
            if (e.frozenAt >= 0L) {
                e.startMs = KuvaFxDirector.clockMs() - e.frozenAt;
                e.frozenAt = -1L;
                any = true;
            }
        }
        return any;
    }

    // ==================== 每帧 / 每 tick ====================

    /** 到点的演出阶段在世界渲染一开始就执行，几何当帧就画得出来 */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || BY_TARGET.isEmpty()) {
            return;
        }
        for (Exec e : BY_TARGET.values().toArray(new Exec[0])) {
            e.timeline.advance(e, e.elapsed());
        }
    }

    /** 本地处决者的站位缓入按帧走，转头才不会一跳一跳；落到目标身后的套路接着按帧沿路线走 */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Exec e = localExec;
        if (e == null) {
            return;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        long t = e.elapsed();
        if (e.easeDone) {
            if (e.variant.relocates() && t < e.handbackMs) {
                roam(e, p, t);
            }
            return;
        }
        float u = Mth.clamp(t / (float) EASE_IN_MS, 0.0F, 1.0F);
        float k = 1.0F - (1.0F - u) * (1.0F - u) * (1.0F - u);
        Vec3 pos = e.startPos.lerp(e.pe, k);
        Vec3 eye = pos.add(0.0D, p.getEyeHeight(), 0.0D);
        Vec3 d = e.anchor().subtract(eye);
        float yaw = yawOf(d);
        float pitch = pitchOf(d);
        float y = e.startYaw + Mth.wrapDegrees(yaw - e.startYaw) * k;
        float x = e.startPitch + (pitch - e.startPitch) * k;
        place(p, pos.x, pos.y, pos.z, y, x);
        if (u >= 1.0F && e.frozenAt < 0L) {
            e.easeDone = true;
        }
    }

    private static final MercyPose.Body ROAM = new MercyPose.Body();

    /**
     * 人真的沿路线走（第一人称、原版第三人称的镜头都跟着真实位置）。绕背时一直看着目标；
     * 从头顶翻过去的那套目标会跑到正下方，看向它的偏航角要跳半圈，所以朝向跟身体走，只有俯仰看着它
     */
    private static void roam(Exec e, LocalPlayer p, long t) {
        if (!MercyPose.body(e, t, ROAM)) {
            return;
        }
        Vec3 d = e.anchor().subtract(ROAM.x, ROAM.y + p.getEyeHeight(), ROAM.z);
        float yaw = e.variant.orbits() ? yawOf(d) : ROAM.yaw;
        double r = yaw * Mth.DEG_TO_RAD;
        double along = -Math.sin(r) * d.x + Math.cos(r) * d.z;
        float pitch = Mth.clamp((float) (Mth.atan2(-d.y, along) * Mth.RAD_TO_DEG), -89.0F, 89.0F);
        place(p, ROAM.x, ROAM.y, ROAM.z, yaw, pitch);
        p.resetFallDistance();
    }

    private static void place(LocalPlayer p, double x, double y, double z, float yaw, float pitch) {
        p.setPos(x, y, z);
        p.xo = p.xOld = x;
        p.yo = p.yOld = y;
        p.zo = p.zOld = z;
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END) {
            if (!BY_TARGET.isEmpty()) {
                // 不画世界的帧（窗口最小化之类）也照样推进
                for (Exec e : BY_TARGET.values().toArray(new Exec[0])) {
                    e.timeline.advance(e, e.elapsed());
                    smoothTurn(e);
                }
            }
            return;
        }
        if (mc.level == null) {
            if (!BY_TARGET.isEmpty()) {
                clearAll();
            }
            return;
        }
        if (isActive()) {
            swallowKeys(mc);
            LocalPlayer p = mc.player;
            if (p != null && localExec.easeDone) {
                p.setDeltaMovement(0.0D, p.getDeltaMovement().y, 0.0D);
            }
        }
        if (BY_TARGET.isEmpty()) {
            return;
        }
        for (Exec e : BY_TARGET.values().toArray(new Exec[0])) {
            tickExec(e);
        }
    }

    private static void tickExec(Exec e) {
        long t = e.elapsed();
        Entity target = e.target();
        boolean mayVanish = MercyTimeline.targetMayVanish(e.plan, t + VANISH_SLACK_MS);
        if (target == null || target.isRemoved()) {
            if (!mayVanish) {
                end(e);
                return;
            }
        } else if (target instanceof AbstractKuva kuva) {
            MercyState st = kuva.getMercyState();
            if (st == MercyState.EXECUTING) {
                e.seenExecuting = true;
            } else if (e.seenExecuting && !mayVanish && !brokeFree(e, st, t)) {
                // 处决中途退回倒地 / 复位 / 被提前离场：收场
                end(e);
                return;
            }
            if (kuva.isAlive()) {
                applyTargetFacing(e, kuva, t);
            }
        }
        if (!e.handedBack && t >= e.handbackMs) {
            e.handedBack = true;
            if (e == localExec) {
                MercyCamera.restore(e);
            }
        }
        if (!e.released && e.handedBack) {
            boolean targetDone = target == null || target.isRemoved() || !target.isAlive()
                    || !(target instanceof AbstractKuva k) || k.getMercyState() != MercyState.EXECUTING;
            if (targetDone || t >= e.handbackMs + RELEASE_TIMEOUT_MS) {
                e.released = true;
            }
        }
        if (e.released && t >= e.endMs) {
            end(e);
        }
    }

    /**
     * 玄骸挣脱以后离开「处决中」是正常的：失败分支转入离场；挣开站起转入起身、再回到能打的状态
     * （服务端在挣开那一 tick 就改状态，同步可能比本地时间轴早到一点，放宽 {@value #VANISH_SLACK_MS} ms）
     */
    private static boolean brokeFree(Exec e, MercyState st, long t) {
        return switch (MercyTimeline.outcome(e.plan)) {
            case FAIL -> st == MercyState.LEAVING && t >= MercyTimeline.breakFreeMs(e.plan);
            case ADVANCE -> (st == MercyState.RISING || st == MercyState.NONE)
                    && t + VANISH_SLACK_MS >= MercyTimeline.breakFreeMs(e.plan);
            default -> false;
        };
    }

    /**
     * 挣开站起时转身那一段：tick 开头按住的朝向在实体自己的 tick 里被抄进 O 值，渲染时两头一样、一 tick 跳一格。
     * 这里在实体 tick 之后把 O 值写成这一刻、当前值写成下一 tick 的朝向，画面上就按帧转过去
     */
    private static void smoothTurn(Exec e) {
        long t = e.elapsed();
        if (e.frozenAt >= 0L || (e.turnProgress(t) <= 0.0F && e.turnProgress(t + 50L) <= 0.0F)
                || e.turnProgress(t) >= 1.0F) {
            return;
        }
        if (!(e.target() instanceof LivingEntity le) || !le.isAlive()) {
            return;
        }
        float now = e.targetYaw(t);
        float next = e.targetYaw(t + 50L);
        le.yRotO = now;
        le.yBodyRotO = now;
        le.yHeadRotO = now;
        le.setYRot(next);
        le.yBodyRot = next;
        le.setYHeadRot(next);
    }

    /** 目标在处决开始那一刻被转向（服务端同时设同样的朝向）；期间每 tick 再按住，免得插值把它拧回去 */
    private static void applyTargetFacing(Exec e, Entity target, long t) {
        if (!(target instanceof LivingEntity le)) {
            return;
        }
        float yaw = e.targetYaw(t);
        le.setYRot(yaw);
        le.yRotO = yaw;
        le.yBodyRot = yaw;
        le.yBodyRotO = yaw;
        le.setYHeadRot(yaw);
        le.yHeadRotO = yaw;
    }

    // ==================== 输入锁 ====================

    private static void swallowKeys(Minecraft mc) {
        var o = mc.options;
        swallow(o.keyAttack);
        swallow(o.keyUse);
        swallow(o.keyDrop);
        swallow(o.keyInventory);
        swallow(o.keyTogglePerspective);
        swallow(o.keySwapOffhand);
        swallow(o.keyPickItem);
        for (KeyMapping k : o.keyHotbarSlots) {
            swallow(k);
        }
        swallow(KuvaKeyMappings.ARCHIVE);
        swallow(KuvaKeyMappings.MERCY);
    }

    private static void swallow(KeyMapping k) {
        while (k.consumeClick()) {
            // 吃空这一 tick 攒下的按键
        }
        k.setDown(false);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isActive()) {
            return;
        }
        Input in = event.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (isActive()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (isActive()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    // ==================== HUD、处决者显隐 ====================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (hidesHud() && !HUD_ALLOW.contains(event.getOverlay().id())) {
            event.setCanceled(true);
        }
    }

    // ==================== 清场 ====================

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
        MercyFx.clearAll();
    }

    /** 重生、换维度时本地玩家会换一个新实体 */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        clearAll();
        MercyFx.clearAll();
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof DeathScreen && localExec != null) {
            end(localExec);
        }
    }

    // ==================== 小工具 ====================

    /** 水平方向向量对应的偏航角（度，MC 约定：0 = 朝 +Z） */
    static float yawOf(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }

    /** 方向向量对应的俯仰角（度，MC 约定：正 = 往下看） */
    static float pitchOf(Vec3 dir) {
        double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        return (float) -Math.toDegrees(Math.atan2(dir.y, h));
    }

    /**
     * 这只目标正在演的处决的计划位：本地有它的时间轴就用时间轴开场时的计划位，否则读同步数据。
     * 效果包常比同步数据先到，本地开发命令起的演出服务端根本没设计划位，两种情况目标都得跟着时间轴演
     */
    static int planBits(AbstractKuva target) {
        Exec e = BY_TARGET.isEmpty() ? null : BY_TARGET.get(target.getId());
        return e != null ? e.planBits : target.getMercyPlan();
    }

    /** 启动时把「处决已过多久」的钩子接到本地时间轴上（动画控制器两端都会加载，不能直接引客户端类） */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                MercyAnimSelector.elapsedMs = e -> MercyClient.elapsedMs(e.getId());
                MercyAnimSelector.planBits = MercyClient::planBits;
            });
        }
    }
}
