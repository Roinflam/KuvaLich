package pers.roinflam.kuvalich.base.entity;

import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.mercy.MercyState;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 玄骸 / 奴仆的日常动画怎么挑：主控制器的走、跑、待机，和单独一层的起势 / 出手
 *
 * <p>动画文件由 {@code tools/anim/build_daily_anim.py} 生成，玄骸读 {@code kuva.animation.json}，奴仆读
 * {@code kuva_slave.animation.json}，片段名两边一样。</p>
 *
 * <p><b>起势 / 出手为什么单独一层</b>：主控制器只管走、跑、待机，腿一直是对的；出手那层（{@code attack} 控制器，排在最后）
 * 站着时连腿带根骨骼一起盖（蹲、跨步、重心往前送），边走边打时只盖上身（{@code _up} 片段），腿接着走。
 * 这层换片段、停下时都不靠 GeckoLib 的过渡（见下面的常量），由模型那边从上一帧画出来的姿势淡过去（{@code KuvaPoseLayer}），
 * 这里只记「切了一刀、淡多久」。</p>
 *
 * <p><b>时序</b>（服务端见 {@link KuvaMeleeGoal}）：同步数据「起势」置上 → 播 {@code ready_x}（站姿到蓄力姿势）再循环
 * {@code ready_x_hold}；挥臂包到 → 播 {@code attack_x}，出手帧 0.1333 s，和挥砍声、扫击弧对齐；伤害在挥臂后 3 tick 结算。
 * 每次起势换一套出手（a / b / c，不和上一套重复）。</p>
 *
 * <p>两端都加载，不引用客户端类。状态全是客户端字段，每只实体一份；服务端也会建，但不用。</p>
 */
public final class KuvaBodyAnim {

    private enum Stage {
        NONE, READY, ATTACK
    }

    private static final String[] VARIANT = {"a", "b", "c"};

    private static final RawAnimation STAY = RawAnimation.begin().thenLoop("stay");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");

    /** [0 站着 / 1 只管上身][套路] */
    private static final RawAnimation[][] READY = new RawAnimation[2][3];
    private static final RawAnimation[][] HOLD = new RawAnimation[2][3];
    private static final RawAnimation[][] ATTACK = new RawAnimation[2][3];

    static {
        for (int m = 0; m < 2; m++) {
            String suffix = m == 0 ? "" : "_up";
            for (int v = 0; v < 3; v++) {
                String k = VARIANT[v];
                READY[m][v] = RawAnimation.begin().thenPlay("ready_" + k + suffix).thenLoop("ready_" + k + "_hold" + suffix);
                HOLD[m][v] = RawAnimation.begin().thenLoop("ready_" + k + "_hold" + suffix);
                ATTACK[m][v] = RawAnimation.begin().thenPlay("attack_" + k + suffix);
            }
        }
    }

    /** 走、跑、待机之间的过渡（tick）：玄骸沉，换得慢一点 */
    private static final int LICH_BLEND = 4;
    private static final int THRALL_BLEND = 3;
    /** 蓄力片段摆到位要多久（毫秒）：到位了再挥臂只淡 {@value #WARM_FADE_MS} ms，没到位淡 {@value #COLD_FADE_MS} ms */
    private static final long LICH_READY_MS = 300L;
    private static final long THRALL_READY_MS = 210L;
    /** 出手片段在这一刻已经收回站姿，这层就此放手（毫秒，比最短的出手片段短一点） */
    private static final long LICH_ATTACK_MS = 780L;
    private static final long THRALL_ATTACK_MS = 520L;
    /** 出手打完、刀收回来以后（毫秒）起势又亮了，直接接下一刀的蓄力 */
    private static final long COMBO_AFTER_MS = 300L;
    /** 站着出手时开始挪步：过了这个时刻（毫秒）就提前放手，腿交回走路，不在地上滑 */
    private static final long MOVE_RELEASE_MS = 300L;
    /** 走 / 停至少保持这么久（毫秒）才换片段，免得速度在门槛上抖时来回切 */
    private static final long MOVE_DEBOUNCE_MS = 150L;

    // 这层换片段一律不用 GeckoLib 的过渡：它拿来起插的姿势是本帧主控制器刚写的（走路 / 待机），不是画面上这层的姿势，
    // 起势接出手、出手接下一次起势都会先闪回站姿。改成每次换片段记一刀「切」，模型那边从上一帧画出来的姿势淡过去（毫秒）
    private static final long LICH_ENTER_FADE_MS = 120L;
    private static final long THRALL_ENTER_FADE_MS = 80L;
    private static final long WARM_FADE_MS = 40L;
    private static final long COLD_FADE_MS = 80L;
    private static final long SWITCH_FADE_MS = 150L;
    private static final long LICH_EXIT_FADE_MS = 200L;
    private static final long THRALL_EXIT_FADE_MS = 150L;

    private final AbstractKuva entity;
    private final boolean lich;

    private Stage stage = Stage.NONE;
    private long stageMs;
    private int variant = -1;
    private boolean full;
    private int seenSwing;
    private boolean moving;
    private long movingChangedMs;
    private boolean layerActive;

    /** 这层换了片段或停下（模型那边下一帧从上一帧画出来的姿势淡过去，然后清掉） */
    public boolean cutPending;
    /** 这一刀淡多久（毫秒） */
    public long cutFadeMs;
    /** 模型那边用：上一帧画出来的姿势、正在淡出的起点姿势（每根骨骼 6 个数：旋转 xyz、位移 xyz）和淡出开始的时刻 */
    public float[] lastPose;
    public float[] fromPose;
    public long fadeStartMs;
    public long fadeMs;

    KuvaBodyAnim(AbstractKuva entity) {
        this.entity = entity;
        this.lich = entity instanceof KuvaMasterEntity;
    }

    /** 主控制器：怜悯之击没接管时走这里 */
    PlayState locomotion(AnimationState<?> state, boolean hasTarget) {
        state.getController().transitionLength(this.lich ? LICH_BLEND : THRALL_BLEND);
        if (state.isMoving()) {
            return state.setAndContinue(hasTarget ? RUN : WALK);
        }
        return state.setAndContinue(STAY);
    }

    /** 起势 / 出手这层 */
    PlayState attackLayer(AnimationState<?> state) {
        AnimationController<?> c = state.getController();
        c.transitionLength(0);
        AbstractKuva e = this.entity;
        if (e.getMercyState() != MercyState.NONE || e.isDeadOrDying()) {
            this.stage = Stage.NONE;
            this.seenSwing = e.swingSeq;
            return stop();
        }
        long now = AbstractKuva.CLIENT_CLOCK.getAsLong();
        boolean movingNow = state.isMoving();
        if (movingNow != this.moving && now - this.movingChangedMs >= MOVE_DEBOUNCE_MS) {
            this.moving = movingNow;
            this.movingChangedMs = now;
        }

        if (e.swingSeq != this.seenSwing) {
            this.seenSwing = e.swingSeq;
            boolean warm = this.stage == Stage.READY && now - this.stageMs >= (this.lich ? LICH_READY_MS : THRALL_READY_MS);
            if (this.stage != Stage.READY) {
                this.variant = nextVariant();
                this.full = !this.moving;
            }
            this.stage = Stage.ATTACK;
            this.stageMs = now;
            c.forceAnimationReset();
            return play(state, ATTACK[this.full ? 0 : 1][this.variant], warm ? WARM_FADE_MS : COLD_FADE_MS);
        }

        boolean ready = e.isStrikeReady();
        boolean combo = false;
        if (this.stage == Stage.ATTACK) {
            long t = now - this.stageMs;
            boolean done = t >= (this.lich ? LICH_ATTACK_MS : THRALL_ATTACK_MS)
                    || c.getAnimationState() == AnimationController.State.STOPPED;
            if (this.full && this.moving && t >= MOVE_RELEASE_MS) {
                done = true;
            }
            combo = ready && t >= COMBO_AFTER_MS;
            if (!done && !combo) {
                return play(state, null, 0L);
            }
            this.stage = Stage.NONE;
        }

        if (ready) {
            if (this.stage != Stage.READY) {
                this.stage = Stage.READY;
                this.stageMs = now;
                this.variant = nextVariant();
                this.full = !this.moving;
                long fade = combo ? SWITCH_FADE_MS : this.lich ? LICH_ENTER_FADE_MS : THRALL_ENTER_FADE_MS;
                return play(state, READY[this.full ? 0 : 1][this.variant], fade);
            }
            // 憋着劲时开始走 / 停下：换成只管上身 / 连腿的循环，已经蓄好的姿势不重来
            if (this.full == this.moving) {
                this.full = !this.moving;
                return play(state, HOLD[this.full ? 0 : 1][this.variant], SWITCH_FADE_MS);
            }
            return play(state, null, 0L);
        }

        this.stage = Stage.NONE;
        return stop();
    }

    private PlayState play(AnimationState<?> state, RawAnimation anim, long fadeMs) {
        this.layerActive = true;
        if (anim == null) {
            return PlayState.CONTINUE;
        }
        cut(fadeMs);
        return state.setAndContinue(anim);
    }

    private PlayState stop() {
        if (this.layerActive) {
            this.layerActive = false;
            cut(this.lich ? LICH_EXIT_FADE_MS : THRALL_EXIT_FADE_MS);
        }
        return PlayState.STOP;
    }

    private void cut(long fadeMs) {
        this.cutPending = true;
        this.cutFadeMs = fadeMs;
    }

    private int nextVariant() {
        if (this.variant < 0) {
            return ThreadLocalRandom.current().nextInt(VARIANT.length);
        }
        return (this.variant + 1 + ThreadLocalRandom.current().nextInt(VARIANT.length - 1)) % VARIANT.length;
    }
}
