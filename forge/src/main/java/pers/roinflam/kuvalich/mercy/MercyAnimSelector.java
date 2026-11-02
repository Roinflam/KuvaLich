package pers.roinflam.kuvalich.mercy;

import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.Collections;
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
 * 判错的是第几刀，所以挣脱按起点拆成了几段，名字只在这个类里用。</p>
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
    /** 成功：切跪地特写的那一刻换跪姿 */
    private static final int SUCCESS_KNEEL_MS = 4670;
    /** 成功：抽刀、瘫倒 */
    private static final int SUCCESS_SLUMP_MS = 7530;
    /** 判错之后多久挣脱 */
    private static final int FAIL_BREAKFREE_AFTER_MS = 700;
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
            MercyVariant.LICH_D, MercyVariant.LICH_E};
    private static final MercyVariant[] THRALL_VARIANTS = {MercyVariant.THRALL_1, MercyVariant.THRALL_2,
            MercyVariant.THRALL_3, MercyVariant.THRALL_4, MercyVariant.THRALL_5};
    private static final int LICH_N = LICH_VARIANTS.length;
    private static final int THRALL_N = THRALL_VARIANTS.length;

    /** [套路 a/b/c/d/e][第几刀 0~2] */
    private static final RawAnimation[][] L_HIT = new RawAnimation[LICH_N][3];
    /** [套路] 判错在第三刀用的第三刀 */
    private static final RawAnimation[] L_HIT3_HELD = new RawAnimation[LICH_N];
    /** [套路][判错在第几刀 0~2] 从那一刀末帧起身的挣脱 */
    private static final RawAnimation[][] L_BREAKFREE_AT = new RawAnimation[LICH_N][3];

    private static final RawAnimation T_ENTER = RawAnimation.begin()
            .thenPlay(MercyAnimNames.THRALL_DOWN_ENTER).thenLoop(MercyAnimNames.THRALL_DOWNED);
    private static final RawAnimation T_DOWNED = RawAnimation.begin().thenLoop(MercyAnimNames.THRALL_DOWNED);
    /** [套路 t1~t5] */
    private static final RawAnimation[] T_EXECUTE = new RawAnimation[THRALL_N];

    /** 演出里的片段（不含倒地循环）：处决钟丢了的时候照原样播下去，不跳回倒地 */
    private static final Set<RawAnimation> SHOW = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 玄骸进入离场时，正在播的片段 → 从它的末帧起身的挣脱 */
    private static final Map<RawAnimation, RawAnimation> BREAKFREE_FROM = new IdentityHashMap<>();

    static {
        for (int v = 0; v < LICH_N; v++) {
            MercyVariant variant = LICH_VARIANTS[v];
            for (int k = 0; k < 3; k++) {
                L_HIT[v][k] = hold(MercyAnimNames.lichHit(variant, k + 1));
                L_BREAKFREE_AT[v][k] = hold("mercy.lich." + variant.animKey + ".breakfree" + (k + 1));
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
        for (int v = 0; v < LICH_N; v++) {
            Collections.addAll(SHOW, L_HIT[v]);
            Collections.addAll(SHOW, L_BREAKFREE_AT[v]);
            SHOW.add(L_HIT3_HELD[v]);
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
            // 处决中但本地没有时间轴：演到一半时间轴先结束了（交还控制之后、消散途中）就停在当前片段；
            // 晚进视野的保持倒地循环，不补演出
            return SHOW.contains(current) ? current : thrall ? T_DOWNED : L_DOWNED;
        }

        int bits = planBits.applyAsInt(entity);
        int variantId = (bits >> 4) & 0xF;
        if (thrall) {
            int i = variantId - MercyVariant.THRALL_1.id;
            return T_EXECUTE[i >= 0 && i < THRALL_N ? i : 0];
        }
        int v = variantId >= 0 && variantId < LICH_N ? variantId : 0;
        MercyVariant variant = LICH_VARIANTS[v];
        int prefix = (bits >> 2) & 3;

        if (prefix == 3) {
            if (t >= SUCCESS_SLUMP_MS) {
                return L_SLUMP;
            }
            if (t >= SUCCESS_KNEEL_MS) {
                return L_KNEEL;
            }
        } else {
            // 判错在第 prefix + 1 刀：这一刀照常刺入，之后不再有刀，700 ms 后挣脱
            int wrongAt = variant.stabMs(prefix);
            if (t >= wrongAt + FAIL_BREAKFREE_AFTER_MS) {
                return L_BREAKFREE_AT[v][prefix];
            }
            if (t >= wrongAt) {
                return prefix == 2 ? L_HIT3_HELD[v] : L_HIT[v][prefix];
            }
        }
        for (int k = 2; k >= 0; k--) {
            if (t >= variant.stabMs(k)) {
                return L_HIT[v][k];
            }
        }
        return grab(v);
    }

    /** 起手被抓住的片段：贴身上挑、踏背各有一段，别的共用 */
    private static RawAnimation grab(int v) {
        return v == 2 ? L_C_GRAB : L_GRAB;
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
