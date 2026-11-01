package pers.roinflam.kuvalich.base.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;

/**
 * 玄骸 / 奴仆的近战：追击照原版 {@link MeleeAttackGoal}，出手拆成三步
 *
 * <ol>
 *   <li><b>起势</b>：冷却快好了、目标进了「够得着的距离 + {@value #READY_MARGIN} 格」，同步数据 STRIKE_READY 置上，
 *   客户端摆出蓄力姿势（追上来的路上就摆好）；</li>
 *   <li><b>挥臂</b>：起势至少摆了 {@code minReady} tick、冷却好了、目标够得着，发挥臂包，客户端从这一刻起播出手动画；</li>
 *   <li><b>落刀</b>：挥臂后隔 {@value #STRIKE_DELAY} tick 才结算伤害。出手动画的刀在 133 ms 到正前方，
 *   受击变红、击退、命中声都落在 150 ms 左右，看起来是刀到了才挨打。</li>
 * </ol>
 *
 * <p>原版挥臂和伤害在同一 tick，客户端先看到目标变红、刀后到。出手节奏不变：冷却还是 20 tick，挥臂就在原版出手的那一 tick。
 * 落刀时目标已经退出范围（比原版多宽容 {@value #HIT_SLACK} 格）就算砍空，冷却照算。</p>
 */
public class KuvaMeleeGoal extends MeleeAttackGoal {

    /** 挥臂到结算伤害隔几 tick：出手帧在 133 ms，3 tick = 150 ms */
    static final int STRIKE_DELAY = 3;
    /** 落刀时比原版够得着的距离多宽容几格（挥臂那一 tick 还够得着，这 3 tick 里被挤开一点不算躲开） */
    private static final double HIT_SLACK = 0.5D;
    /** 够得着的距离外再加几格就开始起势 */
    private static final double READY_MARGIN = 1.5D;

    private final AbstractKuva kuva;
    /** 冷却还剩几 tick 时开始起势 */
    private final int readyLead;
    /** 起势至少摆几 tick 才挥臂：客户端的蓄力片段要这么久才摆到位 */
    private final int minReady;
    private int readyTicks;
    /** 挥臂后还剩几 tick 落刀，0 = 没有挥出去的刀 */
    private int strikeIn;
    @Nullable
    private LivingEntity strikeTarget;

    public KuvaMeleeGoal(AbstractKuva kuva, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(kuva, speedModifier, followingTargetEvenIfNotSeen);
        this.kuva = kuva;
        // 玄骸的蓄力片段 0.29 s，奴仆 0.2 s，各留一两 tick 的余量
        boolean lich = kuva instanceof KuvaMasterEntity;
        this.readyLead = lich ? 8 : 6;
        this.minReady = lich ? 7 : 5;
    }

    @Override
    public void stop() {
        // 挥出去还没落的那一刀先结算掉（目标还活着、还够得着），被别的目标抢走控制权时不至于白挥
        if (this.strikeIn > 0) {
            land();
        }
        super.stop();
        this.readyTicks = 0;
        this.kuva.setStrikeReady(false);
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distSqr) {
        if (this.strikeIn > 0) {
            if (--this.strikeIn == 0) {
                land();
            }
            return;
        }
        double reachSqr = this.getAttackReachSqr(target);
        double near = Math.sqrt(reachSqr) + READY_MARGIN;
        boolean ready = distSqr <= near * near && this.getTicksUntilNextAttack() <= this.readyLead;
        this.readyTicks = ready ? this.readyTicks + 1 : 0;
        this.kuva.setStrikeReady(ready);
        if (ready && this.readyTicks >= this.minReady && distSqr <= reachSqr && this.isTimeToAttack()) {
            this.resetAttackCooldown();
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.kuva.setStrikeReady(false);
            this.readyTicks = 0;
            this.strikeIn = STRIKE_DELAY;
            this.strikeTarget = target;
        }
    }

    /** 落刀：挥臂时的那个目标还活着、还在范围里才结算 */
    private void land() {
        LivingEntity target = this.strikeTarget;
        this.strikeIn = 0;
        this.strikeTarget = null;
        if (target == null || !target.isAlive() || !this.kuva.isAlive()) {
            return;
        }
        double reach = Math.sqrt(this.getAttackReachSqr(target)) + HIT_SLACK;
        if (this.mob.getPerceivedTargetDistanceSquareForMeleeAttack(target) <= reach * reach) {
            this.mob.doHurtTarget(target);
        }
    }
}
