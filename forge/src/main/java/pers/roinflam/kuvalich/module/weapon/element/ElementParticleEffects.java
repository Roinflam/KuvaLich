package pers.roinflam.kuvalich.module.weapon.element;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;

import javax.annotation.Nullable;

/**
 * 元素触发的音画效果包分发器（只在服务端用）
 *
 * <p>以前这里是一堆 {@code ServerLevel#sendParticles}：每次触发、每个持续状态每拍都往周围玩家广播成串的原版粒子包，
 * 刷怪场里一秒几百个包，画面还是原版的烟和火星。现在服务端只说「发生了什么」：
 * 每次触发发一个 {@link FxId#ELEMENT_PROC} 效果包，画面、声音、持续状态贴身的那一层都由客户端自己放。</p>
 *
 * <p>效果包的 {@code a = 元素序号 | 等级 << 8 | 持续量 << 16}，{@code b = 攻击者 id + 1}。持续量：切割是流血的轮数
 * （每轮 20 tick），电击是麻痹的 tick 数（客户端拿它给身上的电弧倒计时；这两种状态不走元素状态同步包），其余元素填 0。</p>
 *
 * <p>限流：一般元素走 {@link ParticleEmissionGuard#tryAcquire}（每实体每 tick 一次、全局每 tick 30 次）；
 * 电击和爆炸带范围 / 额外伤害，和其它元素抢同一个额度的话，满额时它们会没声没光，所以走自己的小额度
 * （{@link ParticleEmissionGuard#tryAcquireHeavy}）。限流只跳过效果包，伤害和元素状态照常结算。</p>
 */
public final class ElementParticleEffects {

    /** 元素序号，和 {@code kuvaweapon.type.*} 的顺序一致 */
    public static final int FIRE = 0, ICE = 1, POISON = 2, ELECTRICITY = 3, SLASH = 4, PUNCTURE = 5, IMPACT = 6,
            GAS = 7, RADIATION = 8, MAGNETIC = 9, CORROSION = 10, EXPLOSION = 11, VIRUS = 12;

    /** 毒气云的可见半径（格） */
    private static final double GAS_CLOUD_RANGE = 32.0D;

    /** 持续量最多占 15 位，免得把 a 的符号位顶上去 */
    private static final int EXTRA_MAX = 0x7FFF;

    private ElementParticleEffects() {
    }

    /**
     * 一次元素触发
     *
     * @param hurter    受击者
     * @param attacker  攻击者（可为 null）
     * @param element   元素序号
     * @param amplifier 元素等级（层数 − 1）
     * @param extra     持续量：切割是流血轮数、电击是麻痹 tick 数，其余 0
     */
    public static void proc(LivingEntity hurter, @Nullable LivingEntity attacker, int element, int amplifier,
                            int extra) {
        if (hurter == null || !(hurter.level() instanceof ServerLevel sl)) {
            return;
        }
        boolean heavy = element == ELECTRICITY || element == EXPLOSION;
        boolean ok = heavy ? ParticleEmissionGuard.tryAcquireHeavy(sl, hurter) : ParticleEmissionGuard.tryAcquire(sl, hurter);
        if (!ok) {
            return;
        }
        int a = (element & 0xFF) | (Math.max(0, Math.min(255, amplifier)) << 8)
                | (Math.max(0, Math.min(EXTRA_MAX, extra)) << 16);
        KuvaFx.tracking(hurter, FxId.ELEMENT_PROC, a, attacker == null ? 0 : attacker.getId() + 1);
    }

    /**
     * 毒气云：发给云心 32 格内的玩家，客户端在这么多 tick 里自己续雾
     *
     * @param center        云心（地面）
     * @param durationTicks 持续 tick
     */
    public static void gasCloud(ServerLevel level, Vec3 center, int durationTicks, @Nullable LivingEntity attacker) {
        if (level == null || durationTicks <= 0 || !ParticleEmissionGuard.tryAcquireGlobal(level)) {
            return;
        }
        KuvaFx.near(level, center, GAS_CLOUD_RANGE, FxId.GAS_CLOUD, -1, durationTicks,
                attacker == null ? 0 : attacker.getId() + 1);
    }
}
