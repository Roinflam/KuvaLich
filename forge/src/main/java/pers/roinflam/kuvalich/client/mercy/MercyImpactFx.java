package pers.roinflam.kuvalich.client.mercy;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.FxTier;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaFxGeometry;
import pers.roinflam.kuvalich.client.fx.KuvaParticles;
import pers.roinflam.kuvalich.client.fx.KuvaScreenFx;

/**
 * 怜悯之击每一刀刺入的打击感：刺入点的冲击环（白芯 + 赤毒红外圈）、往处决者那边喷的火花、一小团赤毒雾，
 * 处决者自己的屏幕边缘再来一圈赤毒红脉冲。出结果的那一刀环更大、多一圈。
 *
 * <p>由时间轴在刺入时刻调；顿帧时演出钟正好在真实的刺入时刻跨过去，几何和粒子各按自己的真实时间走，
 * 不跟着停。刀口那一下小闪光和碎光仍是 {@code RequiemFx.bladeSpark}。</p>
 */
@OnlyIn(Dist.CLIENT)
final class MercyImpactFx {

    /** 旁观者离得再远就不画了 */
    private static final double MAX_DIST = 32.0D;
    private static final RandomSource RANDOM = RandomSource.create();

    private MercyImpactFx() {
    }

    static void stab(MercyClient.Exec x, int n, boolean heavy) {
        Entity t = x.target();
        if (t == null) {
            return;
        }
        Entity ex = x.executor();
        Vec3 back = ex != null ? horizontal(ex.position().subtract(t.position()), x.f) : x.f;
        Vec3 tip = MercyTimeline.chest(t).add(back.scale(0.35D));
        if (x.cinematic) {
            KuvaScreenFx.edgePulse(FxPalette.KUVA, heavy ? 0.32F : 0.22F, 40, heavy ? 420 : 280);
        }
        if (KuvaFxDirector.camDist(tip) > MAX_DIST) {
            return;
        }
        KuvaFxDirector.start("mercy impact " + n, tip, FxTier.A).at(0, () -> {
            float grow = heavy ? 1.5F : 1.0F;
            KuvaFxGeometry.ring(tip).normal(KuvaFxGeometry.Normal.CAMERA)
                    .radius(0.12F, 0.62F * grow, FxCurve.OUT_QUART).thick(0.16F, 0.03F)
                    .color(FxPalette.KUVA).alpha(0.85F, FxCurve.FADE_SQ).life(heavy ? 360 : 260).add();
            KuvaFxGeometry.ring(tip).normal(KuvaFxGeometry.Normal.CAMERA)
                    .radius(0.06F, 0.34F * grow, FxCurve.OUT_QUART).thick(0.07F, 0.015F)
                    .color(FxPalette.WHITE).alpha(1.0F, FxCurve.FADE_SQ).life(heavy ? 220 : 170).add();
            if (heavy) {
                // 多的那一圈立在刺入方向上，稍晚扩开
                KuvaFxGeometry.ring(tip).normal(back)
                        .radius(0.2F, 1.25F, FxCurve.OUT_CUBIC).thick(0.1F, 0.02F)
                        .color(FxPalette.KUVA_DEEP).color2(FxPalette.KUVA).alpha(0.7F, FxCurve.FADE_SQ).life(460).add();
            }
            int sparks = heavy ? 10 : 6;
            for (int i = 0; i < sparks; i++) {
                double speed = 0.22D + RANDOM.nextDouble() * (heavy ? 0.3D : 0.2D);
                Vec3 v = back.scale(speed).add(jitter(0.12D)).add(0.0D, 0.04D, 0.0D);
                KuvaParticles.spark(tip.x, tip.y, tip.z).vel(v.x, v.y, v.z).drag(0.82F).gravity(0.03F)
                        .color(FxPalette.KUVA_HOT).color2(FxPalette.KUVA).size(0.35F, 0.08F)
                        .orient(KuvaParticles.Orient.VELOCITY).life(4 + RANDOM.nextInt(4)).spawn();
            }
            int puffs = heavy ? 5 : 3;
            for (int i = 0; i < puffs; i++) {
                Vec3 v = back.scale(0.02D).add(jitter(0.025D));
                KuvaParticles.mist(tip.x, tip.y, tip.z).vel(v.x, v.y, v.z).color(FxPalette.KUVA_MIST).alpha(0.4F)
                        .size(0.3F, heavy ? 0.9F : 0.7F).life(12 + RANDOM.nextInt(6)).spawn();
            }
        });
    }

    /** 水平单位向量；太短就用备用的 */
    private static Vec3 horizontal(Vec3 v, Vec3 fallback) {
        double len = Math.sqrt(v.x * v.x + v.z * v.z);
        return len < 1.0E-3D ? fallback : new Vec3(v.x / len, 0.0D, v.z / len);
    }

    private static Vec3 jitter(double r) {
        return new Vec3((RANDOM.nextDouble() * 2.0D - 1.0D) * r, (RANDOM.nextDouble() * 2.0D - 1.0D) * r,
                (RANDOM.nextDouble() * 2.0D - 1.0D) * r);
    }
}
