package pers.roinflam.kuvalich.mercy;

import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * 倒地和处决期间玄骸 / 奴仆该播哪个动画片段
 *
 * <p>实体的动画控制器两端都会加载，所以这个类放在公共包里，<b>不引用任何客户端类</b>。
 * 处决已经过了多久不能直接问客户端的处决状态，改从静态钩子 {@link #elapsedMs} 取：客户端在启动时把它设成
 * 查本地处决时间轴，服务端和没有时间轴的地方保持默认的 -1。</p>
 *
 * <p>控制器的过渡时长是 0，切片段不插值，所以每个片段的第 0 帧都等于上一段的末帧（动画文件由
 * {@code tools/anim/build_mercy_anim.py} 按同一张姿势表生成）。玄骸判错以后的挣脱从哪个姿势起身取决于
 * 判错的是第几刀，所以挣脱按起点拆成了几段，名字只在这个类里用。这次验的几刀全对、还没到第三刀时的挣开站起
 * 同样按「第几刀」分段；倒地到时没人处决的站起从倒地姿势起。只有这一段切进来时淡 {@value #RISE_BLEND_TICKS} tick：
 * 倒地循环停在哪一帧不一定，第 0 帧对不齐。</p>
 *
 * <p>每帧都会调，返回的都是下面建好的常量，不分配；同一个常量连着返回时 GeckoLib 不会重播。</p>
 */
public final class MercyAnimSelector {

    /**
     * 这只实体正在演的处决已经过了多少毫秒；-1 表示不知道（晚进视野、服务端）。
     * 客户端在 {@code FMLClientSetupEvent} 里替换成自己的实现
     */
    public static ToLongFunction<AbstractKuva> elapsedMs = e -> -1L;

    /**
     * 这只实体正在演的处决的计划位（见 {@link MercyPlan}）。默认读实体同步数据；
     * 本地自己起的演出（不经服务端的开发命令）可以换成本地时间轴上的计划
     */
    public static ToIntFunction<AbstractKuva> planBits = AbstractKuva::getMercyPlan;

    /** 同 {@link #elapsedMs}，但不受顿帧影响（真实钟）；片段开播时记下，算片段速度用 */
    public static ToLongFunction<AbstractKuva> rawElapsedMs = e -> -1L;

    /** 片段从真实时刻 t0 开播、现在该用的速度；客户端换成按顿帧算的比值 */
    public static ClipSpeed clipSpeed = (e, t0) -> 1.0D;

    @FunctionalInterface
    public interface ClipSpeed {
        double at(AbstractKuva entity, long clipStartRaw);
    }

    private static final Double NORMAL = 1.0D;

    /**
     * 主控制器的速度，注册控制器时装一次。只在播怜悯之击片段时跟着顿帧走，别的时候是 1，
     * 免得上一场的速度留到倒地循环、离场片段上
     */
    public static Double speed(AbstractKuva entity) {
        if (entity.mercyClip == null || entity.mercyClipRaw < 0L) {
            return NORMAL;
        }
        double s = clipSpeed.at(entity, entity.mercyClipRaw);
        return s == 1.0D ? NORMAL : Double.valueOf(s);
    }

    /** 控制器每帧选完片段后调：换了片段就记下这一刻的真实钟 */
    public static void track(AbstractKuva entity, @Nullable RawAnimation clip) {
        if (clip != entity.mercyClip) {
            entity.mercyClip = clip;
            entity.mercyClipRaw = clip == null ? -1L : rawElapsedMs.applyAsLong(entity);
        }
    }

    /**
     * 开发命令强制播的片段：返回非 {@code null} 时不管怜悯之击状态，直接播它（准星指着的实体播某一段、冻帧）。
     * 正式版里保持默认，永远是 {@code null}
     */
    public static BiFunction<AbstractKuva, AnimationState<?>, RawAnimation> devOverride = (e, s) -> null;

    // ==================== 只在这里用到的片段名 ====================

    /** 判错在第三刀时用的第三刀：一抽、膝盖一软，不倒（倒下去的话 700 ms 后要从躺平撑起来，处决者的刀也落了空） */
    private static final String LICH_HIT3_HELD = ".hit3f";
    /** 被抓住以后挣脱（倒地超时、离场碰上被抓） */
    private static final String LICH_BREAKFREE_GRAB = "mercy.lich.breakfree.grab";
    /** C 套路被顶起来以后挣脱 */
    private static final String LICH_C_BREAKFREE_RAISED = "mercy.lich.c.breakfree0";
    /** 倒地 / 处决期间替换说话动画的空片段：说话控制器注册在后面，会盖住处决姿势的头和身子 */
    public static final String LICH_MUTE = "mercy.lich.mute";

    // ==================== 时刻（毫秒，从处决开始算） ====================

    /** 倒地进入片段的长度，过了这么久就算已经倒稳 */
    private static final int LICH_ENTER_MS = 600;
    private static final int THRALL_ENTER_MS = 500;
    /** 倒地到时站起来：从倒地循环切过来时淡多少 tick */
    public static final int RISE_BLEND_TICKS = 3;
    /** 刚出现在这个客户端的实体，前几 tick 看到的倒地算「早就倒了」，不补倒下去那一下 */
    private static final int LATE_VIEW_TICKS = 3;

    // ==================== 片段 ====================

    private static final RawAnimation L_ENTER = RawAnimation.begin()
            .thenPlay(MercyAnimNames.LICH_DOWN_ENTER).thenLoop(MercyAnimNames.LICH_DOWNED);
    private static final RawAnimation L_DOWNED = RawAnimation.begin().thenLoop(MercyAnimNames.LICH_DOWNED);
    private static final RawAnimation L_GRAB = hold(MercyAnimNames.LICH_GRAB);
    private static final RawAnimation L_C_GRAB = hold(MercyAnimNames.LICH_C_GRAB);
    private static final RawAnimation L_KNEEL = RawAnimation.begin().thenLoop(MercyAnimNames.LICH_KNEEL);
    private static final RawAnimation L_SLUMP = hold(MercyAnimNames.LICH_SLUMP);
    private static final RawAnimation L_BREAKFREE = hold(MercyAnimNames.LICH_BREAKFREE);
    private static final RawAnimation L_BREAKFREE_GRAB = hold(LICH_BREAKFREE_GRAB);
    private static final RawAnimation L_BREAKFREE_C0 = hold(LICH_C_BREAKFREE_RAISED);

    private static final MercyVariant[] LICH_VARIANTS = {MercyVariant.LICH_A, MercyVariant.LICH_B, MercyVariant.LICH_C,
            MercyVariant.LICH_D, MercyVariant.LICH_E, MercyVariant.LICH_F, MercyVariant.LICH_G,
            MercyVariant.LICH_H};
    /** 起手有自己一段被抓的套路（片段 {@code mercy.lich.<套路>.grab}，挣脱接 {@code <套路>.breakfree0}）；贴身上挑另算 */
    private static final Set<MercyVariant> OWN_GRAB = EnumSet.of(MercyVariant.LICH_F, MercyVariant.LICH_G, MercyVariant.LICH_H);
    private static final MercyVariant[] THRALL_VARIANTS = {MercyVariant.THRALL_1, MercyVariant.THRALL_2,
            MercyVariant.THRALL_3, MercyVariant.THRALL_4, MercyVariant.THRALL_5};
    private static final int LICH_N = LICH_VARIANTS.length;
    private static final int THRALL_N = THRALL_VARIANTS.length;

    /** [套路] 起手被抓 */
    private static final RawAnimation[] L_GRAB_OF = new RawAnimation[LICH_N];
    /** [套路 a~f][第几刀 0~2] */
    private static final RawAnimation[][] L_HIT = new RawAnimation[LICH_N][3];
    /** [套路] 判错在第三刀用的第三刀 */
    private static final RawAnimation[] L_HIT3_HELD = new RawAnimation[LICH_N];
    /** [套路][判错在第几刀 0~2] 从那一刀末帧起身的挣脱 */
    private static final RawAnimation[][] L_BREAKFREE_AT = new RawAnimation[LICH_N][3];
    /** 倒地到时没人处决，从倒地姿势站起来 */
    private static final RawAnimation L_RISE_DOWN = hold(MercyAnimNames.LICH_RISE);
    /** [套路][这次捅了几刀 − 1，0~1] 全对、还没到第三刀，从最后那一刀挣开站起来 */
    private static final RawAnimation[][] L_RISE_AT = new RawAnimation[LICH_N][2];

    private static final RawAnimation T_ENTER = RawAnimation.begin()
            .thenPlay(MercyAnimNames.THRALL_DOWN_ENTER).thenLoop(MercyAnimNames.THRALL_DOWNED);
    private static final RawAnimation T_DOWNED = RawAnimation.begin().thenLoop(MercyAnimNames.THRALL_DOWNED);
    /** [套路 t1~t5] */
    private static final RawAnimation[] T_EXECUTE = new RawAnimation[THRALL_N];

    /** 演出里的片段（不含倒地循环）：处决钟丢了的时候照原样播下去，不跳回倒地 */
    private static final Set<RawAnimation> SHOW = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 玄骸进入离场时，正在播的片段 → 从它的末帧起身的挣脱 */
    private static final Map<RawAnimation, RawAnimation> BREAKFREE_FROM = new IdentityHashMap<>();
    /** 玄骸进入起身时，正在播的片段 → 从它的末帧站起来的那一段（本地时间轴已经结束、晚进视野时按计划位挑） */
    private static final Map<RawAnimation, RawAnimation> RISE_FROM = new IdentityHashMap<>();

    static {
        for (int v = 0; v < LICH_N; v++) {
            MercyVariant variant = LICH_VARIANTS[v];
            L_GRAB_OF[v] = variant == MercyVariant.LICH_C ? L_C_GRAB : L_GRAB;
            if (OWN_GRAB.contains(variant)) {
                L_GRAB_OF[v] = hold("mercy.lich." + variant.animKey + ".grab");
                RawAnimation free = hold("mercy.lich." + variant.animKey + ".breakfree0");
                SHOW.add(L_GRAB_OF[v]);
                SHOW.add(free);
                BREAKFREE_FROM.put(L_GRAB_OF[v], free);
                BREAKFREE_FROM.put(free, free);
            }
            for (int k = 0; k < 3; k++) {
                L_HIT[v][k] = hold(MercyAnimNames.lichHit(variant, k + 1));
                L_BREAKFREE_AT[v][k] = hold("mercy.lich." + variant.animKey + ".breakfree" + (k + 1));
            }
            for (int k = 0; k < 2; k++) {
                L_RISE_AT[v][k] = hold(MercyAnimNames.lichRise(variant, k + 1));
            }
            L_HIT3_HELD[v] = hold("mercy.lich." + variant.animKey + LICH_HIT3_HELD);
        }
        for (int v = 0; v < THRALL_N; v++) {
            T_EXECUTE[v] = hold(MercyAnimNames.thrallExecute(THRALL_VARIANTS[v]));
            SHOW.add(T_EXECUTE[v]);
        }

        SHOW.add(L_GRAB);
        SHOW.add(L_C_GRAB);
        SHOW.add(L_KNEEL);
        SHOW.add(L_SLUMP);
        SHOW.add(L_BREAKFREE);
        SHOW.add(L_BREAKFREE_GRAB);
        SHOW.add(L_BREAKFREE_C0);
        SHOW.add(L_RISE_DOWN);
        for (int v = 0; v < LICH_N; v++) {
            Collections.addAll(SHOW, L_HIT[v]);
            Collections.addAll(SHOW, L_BREAKFREE_AT[v]);
            Collections.addAll(SHOW, L_RISE_AT[v]);
            SHOW.add(L_HIT3_HELD[v]);
        }

        // 起身中被叫走（主人下线之类）：已经在站起来了，接着这一段演完，离场的雾照放
        BREAKFREE_FROM.put(L_RISE_DOWN, L_RISE_DOWN);
        RISE_FROM.put(L_ENTER, L_RISE_DOWN);
        RISE_FROM.put(L_DOWNED, L_RISE_DOWN);
        RISE_FROM.put(L_RISE_DOWN, L_RISE_DOWN);
        for (int v = 0; v < LICH_N; v++) {
            for (int k = 0; k < 2; k++) {
                BREAKFREE_FROM.put(L_RISE_AT[v][k], L_RISE_AT[v][k]);
                RISE_FROM.put(L_HIT[v][k], L_RISE_AT[v][k]);
                RISE_FROM.put(L_RISE_AT[v][k], L_RISE_AT[v][k]);
            }
        }

        BREAKFREE_FROM.put(L_ENTER, L_BREAKFREE);
        BREAKFREE_FROM.put(L_DOWNED, L_BREAKFREE);
        BREAKFREE_FROM.put(L_GRAB, L_BREAKFREE_GRAB);
        BREAKFREE_FROM.put(L_C_GRAB, L_BREAKFREE_C0);
        BREAKFREE_FROM.put(L_BREAKFREE, L_BREAKFREE);
        BREAKFREE_FROM.put(L_BREAKFREE_GRAB, L_BREAKFREE_GRAB);
        BREAKFREE_FROM.put(L_BREAKFREE_C0, L_BREAKFREE_C0);
        for (int v = 0; v < LICH_N; v++) {
            for (int k = 0; k < 3; k++) {
                BREAKFREE_FROM.put(L_HIT[v][k], L_BREAKFREE_AT[v][k]);
                BREAKFREE_FROM.put(L_BREAKFREE_AT[v][k], L_BREAKFREE_AT[v][k]);
            }
            BREAKFREE_FROM.put(L_HIT3_HELD[v], L_BREAKFREE_AT[v][2]);
        }
    }

    private MercyAnimSelector() {
    }

    private static RawAnimation hold(String name) {
        return RawAnimation.begin().thenPlayAndHold(name);
    }

    /**
     * 按当前的怜悯之击状态选一个动画片段。
     *
     * @return 要播的片段；没有处于倒地 / 处决状态时返回 {@code null}，交给原来的行走、待机控制器
     */
    @Nullable
    public static RawAnimation pick(AbstractKuva entity, AnimationState<?> state) {
        RawAnimation forced = devOverride.apply(entity, state);
        if (forced != null) {
            return forced;
        }
        MercyState mercy = entity.getMercyState();
        long t = elapsedMs.applyAsLong(entity);
        // 同步状态还是 NONE、本地却有时间轴的（不经服务端的本地演出），要有计划位才演：
        // 真正的计划位刀数那两位不会是 0，全 0 说明没人给，照常走路待机
        if (mercy == MercyState.NONE && (t < 0 || planBits.applyAsInt(entity) == 0)) {
            entity.downedEnterTick = Double.NaN;
            return null;
        }
        boolean thrall = entity instanceof KuvaSlaveEntity;
        RawAnimation current = state.getController().getCurrentRawAnimation();

        if (mercy == MercyState.LEAVING) {
            // 玄骸：从眼下的姿势撑起来、两臂一甩（判错那一路早在 g + 700 就切过去了，这里接着播同一段）；
            // 奴仆：倒地超时就地消散，保持原样
            if (thrall) {
                return SHOW.contains(current) ? current : T_DOWNED;
            }
            RawAnimation free = BREAKFREE_FROM.get(current);
            return free != null ? free : L_BREAKFREE;
        }
        if (t < 0) {
            if (mercy == MercyState.DOWNED) {
                return downed(entity, state, thrall, current);
            }
            if (mercy == MercyState.RISING && !thrall) {
                return rising(entity, current);
            }
            // 处决中但本地没有时间轴：演到一半时间轴先结束了（交还控制之后、消散途中）就停在当前片段；
            // 晚进视野的保持倒地循环，不补演出
            return SHOW.contains(current) ? current : thrall ? T_DOWNED : L_DOWNED;
        }

        return executing(planBits.applyAsInt(entity), thrall, t);
    }

    /** 处决演到 {@code t} 毫秒时该放哪一段（只看计划位和时刻） */
    public static RawAnimation executing(int bits, boolean thrall, long t) {
        int variantId = (bits >> 4) & 0xF;
        if (thrall) {
            int i = variantId - MercyVariant.THRALL_1.id;
            return T_EXECUTE[i >= 0 && i < THRALL_N ? i : 0];
        }
        int v = lichIndex(variantId);
        MercyBeats.Lich b = MercyBeats.lich(LICH_VARIANTS[v]);
        int stabs = stabsOf(bits);
        int prefix = Math.min((bits >> 2) & 3, stabs);

        if (prefix >= stabs && stabs >= 3) {
            if (t >= b.drawOut) {
                return L_SLUMP;
            }
            if (t >= b.closeup) {
                return L_KNEEL;
            }
        } else if (prefix >= stabs) {
            // 这次验的几刀全对、还没到第三刀：最后一刀照常刺入，那一格填满后挣开站起来（和服务端推人同一拍）
            if (t >= b.fill(stabs) + b.advanceBreakFree) {
                return L_RISE_AT[v][stabs - 1];
            }
        } else {
            // 判错在第 prefix + 1 刀：这一刀照常刺入，之后不再有刀，那一格填满后挣脱（和服务端推人同一拍）
            int wrongAt = b.stab(prefix);
            if (t >= b.fill(prefix + 1) + b.failBreakFree) {
                return L_BREAKFREE_AT[v][prefix];
            }
            if (t >= wrongAt) {
                return prefix == 2 ? L_HIT3_HELD[v] : L_HIT[v][prefix];
            }
        }
        for (int k = stabs - 1; k >= 0; k--) {
            if (t >= b.stab(k)) {
                return L_HIT[v][k];
            }
        }
        return grab(v);
    }

    /** 计划位里的编号在上面那张表里排第几；编号不一定连号，不能直接当下标。不认识的按正面直刺 */
    private static int lichIndex(int variantId) {
        for (int i = 0; i < LICH_N; i++) {
            if (LICH_VARIANTS[i].id == variantId) {
                return i;
            }
        }
        return 0;
    }

    /** 计划位里的刀数（0 按 3 读，和 {@link MercyPlan#decode} 一致） */
    private static int stabsOf(int bits) {
        int stabs = bits & 3;
        return stabs == 0 ? 3 : stabs;
    }

    /**
     * 起身中、本地没有时间轴（倒地到时没人处决；挣开站起时本地时间轴先结束了；晚进视野）：
     * 正在播的片段能接上就接，否则按计划位挑——没有计划位就是从倒地站起来
     */
    private static RawAnimation rising(AbstractKuva entity, @Nullable RawAnimation current) {
        RawAnimation from = current == null ? null : RISE_FROM.get(current);
        if (from != null) {
            return from;
        }
        int bits = planBits.applyAsInt(entity);
        int stabs = stabsOf(bits);
        if (bits == 0 || stabs >= 3) {
            return L_RISE_DOWN;
        }
        int variantId = (bits >> 4) & 0xF;
        return L_RISE_AT[lichIndex(variantId)][stabs - 1];
    }

    /** 切进这一段时控制器淡多少 tick：只有从倒地循环站起来那一段淡，别的切片段不插值（首帧对得上） */
    public static int blendTicks(RawAnimation animation) {
        return animation == L_RISE_DOWN ? RISE_BLEND_TICKS : 0;
    }

    /** 起手被抓住的片段：贴身上挑、折翼各有一段，别的共用 */
    private static RawAnimation grab(int v) {
        return L_GRAB_OF[v];
    }

    /** 倒地：刚倒下时先播倒下去那一下（播完自己接倒地循环），倒稳了只播循环 */
    private static RawAnimation downed(AbstractKuva entity, AnimationState<?> state, boolean thrall,
                                       @Nullable RawAnimation current) {
        RawAnimation enter = thrall ? T_ENTER : L_ENTER;
        if (current == enter) {
            return enter;
        }
        double tick = state.getAnimationTick();
        if (Double.isNaN(entity.downedEnterTick)) {
            // 进入时刻只在回到 NONE 时清掉：处决被打断、回到倒地时不该再从站姿倒一次
            entity.downedEnterTick = entity.tickCount < LATE_VIEW_TICKS ? Double.NEGATIVE_INFINITY : tick;
        }
        int enterMs = thrall ? THRALL_ENTER_MS : LICH_ENTER_MS;
        if ((tick - entity.downedEnterTick) * 50.0D < enterMs) {
            return enter;
        }
        return thrall ? T_DOWNED : L_DOWNED;
    }
}
