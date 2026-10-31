package pers.roinflam.kuvalich.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 粒子、几何的预算
 *
 * <ul>
 *   <li>每个客户端 tick 本模组新粒子上限 220（FULL）× 强度倍数（REDUCED 0.55、MINIMAL 0.2），活跃粒子上限 1200 × 同一倍数；
 *       原版「粒子」选项为「减少」时再 ×0.5，「最少」时只留核心层。</li>
 *   <li>按档预留：两种上限里各有 40% 只给 S / A 级用，B / C 级最多用到 60%——混战里元素触发再多，
 *       也饿不死同一 tick 的玄骸演出。</li>
 *   <li>主角压制：附近有 S 级演出时，B / C 级的粒子只放一半、几何不画。</li>
 *   <li>远近：离相机 32 格外只留核心层，64 格外一律不画。</li>
 * </ul>
 *
 * <p>活跃粒子不靠 {@code remove()} 计数：粒子引擎换世界时直接清表、超量时直接挤掉最老的，都不调 {@code remove()}。
 * 这里记下自己生成的粒子，每 tick 把已经死掉或不再被 tick 的剔掉，剩下的就是活跃数。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FxBudget {

    public static final int NEW_PER_TICK_FULL = 220;
    public static final int ACTIVE_PARTICLES_FULL = 1200;
    public static final int ACTIVE_GEOMETRY = 96;
    /** B / C 级能用到的比例 */
    private static final float MINOR_SHARE = 0.6F;

    private static final List<KuvaParticle> LIVE = new ArrayList<>();
    private static int tickCounter;
    private static int newThisTick;
    private static int newThisTickMinor;
    private static int heroSkip;

    private FxBudget() {
    }

    /**
     * 这一次能不能再生成一个粒子；{@code core} 层在预算紧时、远处也尽量保留。位置用来判断远近和主角压制
     */
    public static boolean tryParticle(FxTier tier, boolean core, Vec3 pos) {
        EffectsDisplayConfig.Intensity intensity = EffectsDisplayConfig.intensity();
        ParticleStatus vanilla = Minecraft.getInstance().options.particles().get();
        boolean minimal = intensity == EffectsDisplayConfig.Intensity.MINIMAL || vanilla == ParticleStatus.MINIMAL;
        if (minimal && !core) {
            return false;
        }
        double d = KuvaFxDirector.camDist(pos);
        if (d > KuvaFxDirector.LOD_CULL_DIST || (d > KuvaFxDirector.LOD_CORE_DIST && !core)) {
            return false;
        }
        float scale = intensity.budgetScale * (vanilla == ParticleStatus.DECREASED ? 0.5F : 1.0F);
        int perTick = Math.max(4, Math.round(NEW_PER_TICK_FULL * scale));
        int active = Math.max(16, Math.round(ACTIVE_PARTICLES_FULL * scale));
        boolean major = tier.isMajor();
        if (!major) {
            perTick = Math.round(perTick * MINOR_SHARE);
            active = Math.round(active * MINOR_SHARE);
            if (newThisTickMinor >= perTick) {
                return false;
            }
            if (!core && KuvaFxDirector.heroActiveNear(pos) && (heroSkip++ & 1) == 0) {
                return false;
            }
        } else if (newThisTick >= perTick) {
            return false;
        }
        if (LIVE.size() >= active) {
            return false;
        }
        newThisTick++;
        if (!major) {
            newThisTickMinor++;
        }
        return true;
    }

    /** 这一次能不能再加一个几何图元 */
    public static boolean tryGeometry(FxTier tier, boolean core, Vec3 pos) {
        EffectsDisplayConfig.Intensity intensity = EffectsDisplayConfig.intensity();
        if (intensity == EffectsDisplayConfig.Intensity.MINIMAL && !core) {
            return false;
        }
        double d = KuvaFxDirector.camDist(pos);
        if (d > KuvaFxDirector.LOD_CULL_DIST || (d > KuvaFxDirector.LOD_CORE_DIST && !core)) {
            return false;
        }
        int cap = ACTIVE_GEOMETRY;
        if (!tier.isMajor()) {
            cap = Math.round(cap * MINOR_SHARE);
            if (KuvaFxDirector.heroActiveNear(pos)) {
                return false;
            }
        }
        return KuvaFxGeometry.activeCount() < cap;
    }

    /** 不知道位置时（按在相机处算，不做远近取舍） */
    public static boolean tryParticle(FxTier tier, boolean core) {
        var cam = KuvaFxDirector.camera();
        return tryParticle(tier, core, cam == null ? Vec3.ZERO : cam.getPosition());
    }

    /** 不知道位置时（按在相机处算，不做远近取舍） */
    public static boolean tryGeometry(FxTier tier, boolean core) {
        var cam = KuvaFxDirector.camera();
        return tryGeometry(tier, core, cam == null ? Vec3.ZERO : cam.getPosition());
    }

    // ==================== 粒子登记 ====================

    static void track(KuvaParticle p) {
        LIVE.add(p);
    }

    static int tickCounter() {
        return tickCounter;
    }

    public static int liveParticles() {
        return LIVE.size();
    }

    public static int newThisTick() {
        return newThisTick;
    }

    static void onTickStart() {
        tickCounter++;
        newThisTick = 0;
        newThisTickMinor = 0;
        // 已经移除的、或者两 tick 以上没被粒子引擎 tick 过的（被挤掉 / 清表了）都不算活跃
        LIVE.removeIf(p -> !p.isAlive() || tickCounter - p.lastTicked > 2);
    }

    static void clear() {
        LIVE.clear();
    }
}
