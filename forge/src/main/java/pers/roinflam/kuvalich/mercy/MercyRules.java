package pers.roinflam.kuvalich.mercy;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;

/**
 * 怜悯之击的开关与判定：谁会倒地、处决锚点在哪、离多近才能动手
 *
 * <p>只读配置和实体状态，两端都能调；配置是 COMMON，客户端读到的是本地配置文件的值，所以玩法判断一律以服务端为准。</p>
 */
public final class MercyRules {

    /** 玄骸的处决锚点在胸口：脚底 + 碰撞箱高 × 0.62 */
    public static final double LICH_ANCHOR = 0.62D;
    /** 奴仆的处决锚点在头部：脚底 + 碰撞箱高 × 0.82 */
    public static final double THRALL_ANCHOR = 0.82D;
    /** 服务端校验距离时多给的余量（格），容忍网络延迟 */
    public static final double REACH_SLACK = 1.0D;

    private MercyRules() {
    }

    /**
     * 这只实体走不走怜悯之击：玄骸、幼体看 {@code mercyEnabled}，奴仆还要 {@code mercyThralls}。
     * 不走的就照旧被打死时当场结算（幼体被玩家打死就诞生玄骸）
     */
    public static boolean enabledFor(AbstractKuva e) {
        if (!ModConfig.KUVA_LICH.mercyEnabled.get()) {
            return false;
        }
        if (e instanceof KuvaLarvlingEntity) {
            return true;
        }
        return !(e instanceof KuvaSlaveEntity) || ModConfig.KUVA_LICH.mercyThralls.get();
    }

    /**
     * 怜悯之击关着时被打死的奴仆、幼体算给谁：玩家本人，或者宠物、召唤物、盟友的主人（主人要在线）。
     * 和处决资格认的是同一批人，只是这里不看「最近打过它的玩家」
     */
    @Nullable
    public static Player creditedKiller(DamageSource source) {
        Entity e = source.getEntity();
        if (e instanceof Player p) {
            return p;
        }
        if (e instanceof OwnableEntity pet && pet.getOwner() instanceof Player owner) {
            return owner;
        }
        if (e instanceof KuvaMasterEntity ally && ally.isAlly()) {
            return ally.getAllyOwnerPlayer();
        }
        return null;
    }

    /**
     * 受到致命伤时会不会先倒地：有主玄骸、演示目标、幼体、开了奴仆倒地的奴仆。
     * 无主的野生玄骸不倒地，直接死
     */
    public static boolean canDown(AbstractKuva e) {
        if (!ModConfig.KUVA_LICH.mercyEnabled.get()) {
            return false;
        }
        if (e.isDemo()) {
            return true;
        }
        if (e instanceof KuvaMasterEntity lich) {
            return lich.shouldEnterDowned();
        }
        if (e instanceof KuvaLarvlingEntity) {
            return true;
        }
        return e instanceof KuvaSlaveEntity && ModConfig.KUVA_LICH.mercyThralls.get();
    }

    /** 可处决距离（格），随同步数据下发给客户端画提示 */
    public static float reach() {
        return ModConfig.KUVA_LICH.mercyReach.get().floatValue();
    }

    /** 处决锚点：玄骸胸口、奴仆头部，按当前（倒地后变矮的）碰撞箱算 */
    public static Vec3 anchor(AbstractKuva e) {
        double k = e instanceof KuvaMasterEntity ? LICH_ANCHOR : THRALL_ANCHOR;
        return e.position().add(0.0D, e.getBbHeight() * k, 0.0D);
    }

    /** 玩家眼睛到锚点的距离在 {@code reach + slack} 以内 */
    public static boolean inReach(Player p, AbstractKuva e, double slack) {
        double max = reach() + slack;
        return p.getEyePosition().distanceToSqr(anchor(e)) <= max * max;
    }

    /** 玩家看得见它（原版视线判定：眼睛到眼睛之间没有挡视线的方块） */
    public static boolean hasSight(Player p, AbstractKuva e) {
        return p.hasLineOfSight(e);
    }

    /** 玩家此刻能不能当处决者：活着、不是旁观、不在载具上、没睡觉 */
    public static boolean canAct(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isSleeping();
    }
}
