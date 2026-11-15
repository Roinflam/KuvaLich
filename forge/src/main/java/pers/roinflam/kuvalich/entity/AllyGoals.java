package pers.roinflam.kuvalich.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mercy.MercyState;

import java.util.EnumSet;
import java.util.UUID;

/** 盟友的索敌、跟随和敌我判断 */
public final class AllyGoals {

    /** 主动找敌人的半径（格），从盟友身上量 */
    public static final double SEEK_RANGE = 16.0D;
    /** 追着打的目标离主人超过这么远就放掉，回到主人身边 */
    private static final double LEASH = 24.0D;
    /** 主人挨打 / 打人之后多久以内，那个对手算「正在交手」（tick） */
    private static final int ENGAGE_MEMORY = 100;

    private AllyGoals() {
    }

    /**
     * 能不能打、能不能被震荡扫到。只认实现了 Enemy 的；玩家、宠物、村民、铁傀儡天然不在内。
     * 苦力怕不主动去打：贴脸一砍它就在主人身边炸了
     */
    public static boolean isFoe(KuvaMasterEntity ally, Entity e) {
        if (!(e instanceof LivingEntity living) || e == ally || !living.isAlive() || !(e instanceof Enemy)) {
            return false;
        }
        if (e instanceof Player || e instanceof Creeper) {
            return false;
        }
        if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            return false;
        }
        if (e instanceof AbstractKuva kuva) {
            if (kuva.isDemo() || kuva.getMercyState() != MercyState.NONE) {
                return false;
            }
            if (kuva instanceof KuvaMasterEntity lich) {
                if (lich.isAlly()) {
                    return false;
                }
                // 别人的专属玄骸不碰；主人自己的照打，打空一段照常倒地，处决还是主人来
                UUID owner = lich.getOwnerUUID();
                return owner == null || owner.equals(ally.getAllyOwnerUUID());
            }
        }
        return true;
    }

    /** 伤害是不是玩家这一边打的：玩家本人（含他射出的东西）、有主人的宠物 / 召唤物、任何人的盟友 */
    public static boolean fromPlayerSide(DamageSource source) {
        Entity e = source.getEntity();
        if (e instanceof Player) {
            return true;
        }
        if (e instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            return true;
        }
        return e instanceof KuvaMasterEntity lich && lich.isAlly();
    }

    /** 主人在处决、或手上的目标不再该打：放下目标、原地待命 */
    public static void standDownIfNeeded(KuvaMasterEntity ally) {
        ServerPlayer owner = ally.getAllyOwnerPlayer();
        LivingEntity target = ally.getTarget();
        boolean busy = owner != null && MercyServer.isBusy(owner);
        if (busy || target != null && !isFoe(ally, target)) {
            ally.setTarget(null);
            if (busy) {
                ally.getNavigation().stop();
            }
        }
    }

    /** 该优先打的：正在打主人的、主人正在打的、刚打过自己的 */
    @Nullable
    static LivingEntity defendTarget(KuvaMasterEntity ally, ServerPlayer owner) {
        int now = owner.tickCount;
        LivingEntity hurtBy = owner.getLastHurtByMob();
        if (hurtBy != null && now - owner.getLastHurtByMobTimestamp() <= ENGAGE_MEMORY && valid(ally, owner, hurtBy)) {
            return hurtBy;
        }
        LivingEntity hit = owner.getLastHurtMob();
        if (hit != null && now - owner.getLastHurtMobTimestamp() <= ENGAGE_MEMORY && valid(ally, owner, hit)) {
            return hit;
        }
        LivingEntity mine = ally.getLastHurtByMob();
        if (mine != null && ally.tickCount - ally.getLastHurtByMobTimestamp() <= ENGAGE_MEMORY
                && valid(ally, owner, mine)) {
            return mine;
        }
        return null;
    }

    private static boolean valid(KuvaMasterEntity ally, ServerPlayer owner, LivingEntity e) {
        return e.level() == ally.level() && isFoe(ally, e) && e.distanceToSqr(owner) <= LEASH * LEASH;
    }

    @Nullable
    private static LivingEntity nearestFoe(KuvaMasterEntity ally, ServerPlayer owner) {
        AABB box = ally.getBoundingBox().inflate(SEEK_RANGE, 6.0D, SEEK_RANGE);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : ally.level().getEntitiesOfClass(LivingEntity.class, box, e -> valid(ally, owner, e))) {
            double d = e.distanceToSqr(ally);
            if (d < bestDist && ally.getSensing().hasLineOfSight(e)) {
                best = e;
                bestDist = d;
            }
        }
        return best;
    }

    /** 唯一的目标 AI：先护主，再就近打敌对生物。永远不会选中玩家、宠物、别人的盟友 */
    static final class Target extends TargetGoal {
        private final KuvaMasterEntity ally;
        private int cooldown;

        Target(KuvaMasterEntity ally) {
            super(ally, false);
            this.ally = ally;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            this.cooldown = 5;
            ServerPlayer owner = ally.getAllyOwnerPlayer();
            if (owner == null || owner.level() != ally.level() || MercyServer.isBusy(owner)) {
                return false;
            }
            LivingEntity pick = defendTarget(ally, owner);
            if (pick == null) {
                pick = nearestFoe(ally, owner);
            }
            this.targetMob = pick;
            return pick != null;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            ServerPlayer owner = ally.getAllyOwnerPlayer();
            if (target == null || owner == null || MercyServer.isBusy(owner) || !valid(ally, owner, target)) {
                return false;
            }
            // 主人这边有了更该打的就换过去
            LivingEntity defend = defendTarget(ally, owner);
            return defend == null || defend == target;
        }

        @Override
        public void start() {
            this.mob.setTarget(this.targetMob);
            super.start();
        }
    }

    /** 手上没目标时回到主人身边 */
    static final class Follow extends Goal {
        private static final double START = 6.0D;
        private static final double STOP = 3.0D;
        private final KuvaMasterEntity ally;
        @Nullable
        private ServerPlayer owner;
        private int repath;

        Follow(KuvaMasterEntity ally) {
            this.ally = ally;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer o = ally.getAllyOwnerPlayer();
            if (o == null || o.level() != ally.level() || ally.getTarget() != null || MercyServer.isBusy(o)
                    || ally.distanceToSqr(o) < START * START) {
                return false;
            }
            this.owner = o;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.owner != null && this.owner.isAlive() && this.owner.level() == ally.level()
                    && ally.getTarget() == null && !MercyServer.isBusy(this.owner)
                    && ally.distanceToSqr(this.owner) > STOP * STOP;
        }

        @Override
        public void start() {
            this.repath = 0;
        }

        @Override
        public void stop() {
            this.owner = null;
            ally.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }
            ally.getLookControl().setLookAt(this.owner, 10.0F, ally.getMaxHeadXRot());
            if (--this.repath <= 0) {
                this.repath = 10;
                ally.getNavigation().moveTo(this.owner, 1.15D);
            }
        }
    }
}
