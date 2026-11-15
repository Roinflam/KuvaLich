package pers.roinflam.kuvalich.client.ephemera;

import net.minecraft.util.Mth;

import java.util.Random;

/**
 * 占位几何：脚下一圈光环 + 往上飘的光点 + 贴地拖尾 + 环身三颗光核。只用来把挂点、状态、预算、两批提交整条跑通，
 * 正式的 6 款画法写好登记进 {@link EphemeraStyles} 后就不会再走到这里。
 */
final class PlaceholderStyle implements EphemeraStyle {

    private static final int RING_FULL = 32;
    private static final int RING_REDUCED = 12;
    private static final int CORES = 3;

    @Override
    public float extent() {
        return 1.2F;
    }

    @Override
    public void draw(EphemeraInstance inst, EphemeraHost host, EphemeraDraw d) {
        if (!(inst.styleState instanceof Random)) {
            inst.styleState = new Random(inst.entityId * 31L + inst.look.type().hashCode());
        }
        Random rnd = (Random) inst.styleState;
        EphemeraAnchor.Frame f = new EphemeraAnchor.Frame();
        EphemeraTier tier = d.tier;
        float t = (d.timeMs % 60_000L) / 1000.0F;
        float activity = Math.max(inst.move, inst.burst * tier.burst);
        float idle = inst.move < 0.05F ? tier.idle : 1.0F;

        if (EphemeraAnchor.FEET.frame(host, inst.look, f)) {
            drawRing(d, f, t, activity, idle);
            drawTrail(inst, d, f, tier);
            emitAndDrawParticles(inst, d, f, rnd, activity);
        }
        if (d.allows(EphemeraAnchor.BODY) && EphemeraAnchor.BODY.frame(host, inst.look, f)) {
            drawCores(d, f, t, activity);
        }
    }

    private static void drawRing(EphemeraDraw d, EphemeraAnchor.Frame f, float t, float activity, float idle) {
        int segs = d.full ? RING_FULL : RING_REDUCED;
        float pulse = 0.04F * idle * Mth.sin(t * 2.4F);
        float ri = (0.40F + pulse) * f.radius;
        float ro = (0.60F + pulse + 0.12F * activity) * f.radius;
        float glow = d.glowAlpha * (0.30F + 0.45F * activity);
        for (int i = 0; i < segs; i++) {
            float a0 = Mth.TWO_PI * i / segs + t * 0.6F;
            float a1 = Mth.TWO_PI * (i + 1) / segs + t * 0.6F;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float v0 = (float) i / segs;
            float v1 = (float) (i + 1) / segs;
            int in0 = d.color(v0, 0.0F), in1 = d.color(v1, 0.0F), out0 = d.color(v0, 1.0F), out1 = d.color(v1, 1.0F);
            // 半透明的底托：亮背景下加法会洗白，靠它托住形状
            d.quad(EphemeraStaging.BASE, EphemeraStaging.TRANSLUCENT,
                    f.x + c0 * ri, f.y, f.z + s0 * ri, f.x + c1 * ri, f.y, f.z + s1 * ri,
                    f.x + c1 * ro, f.y, f.z + s1 * ro, f.x + c0 * ro, f.y, f.z + s0 * ro,
                    EphemeraDraw.argb(in0, 0.22F), EphemeraDraw.argb(in1, 0.22F),
                    EphemeraDraw.argb(out1, 0.0F), EphemeraDraw.argb(out0, 0.0F));
            d.quad(EphemeraStaging.BASE, EphemeraStaging.ADDITIVE,
                    f.x + c0 * ri, f.y + 0.005D, f.z + s0 * ri, f.x + c1 * ri, f.y + 0.005D, f.z + s1 * ri,
                    f.x + c1 * ro, f.y + 0.005D, f.z + s1 * ro, f.x + c0 * ro, f.y + 0.005D, f.z + s0 * ro,
                    EphemeraDraw.argb(in0, glow), EphemeraDraw.argb(in1, glow),
                    EphemeraDraw.argb(out1, 0.0F), EphemeraDraw.argb(out0, 0.0F));
        }
    }

    private static void drawTrail(EphemeraInstance inst, EphemeraDraw d, EphemeraAnchor.Frame f, EphemeraTier tier) {
        EphemeraInstance.Trail trail = inst.trail;
        trail.push(f.x, f.y, f.z, d.timeMs);
        int n = trail.size();
        if (n < 2 || inst.move < 0.02F) {
            return;
        }
        float w0 = 0.22F * f.radius * tier.trailWidth;
        float a0 = 0.55F * tier.trailAlpha * inst.move;
        for (int i = 0; i < n - 1; i++) {
            double ax = trail.x(i), az = trail.z(i), bx = trail.x(i + 1), bz = trail.z(i + 1);
            double dx = bx - ax, dz = bz - az;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-4D) {
                continue;
            }
            double px = -dz / len, pz = dx / len;
            float k0 = 1.0F - (float) i / (n - 1);
            float k1 = 1.0F - (float) (i + 1) / (n - 1);
            double wa = w0 * k0, wb = w0 * k1;
            int ca = d.color(0.0F, 1.0F - k0), cb = d.color(0.0F, 1.0F - k1);
            double y = trail.y(i) + 0.01D;
            d.quad(EphemeraStaging.TRAIL, EphemeraStaging.ADDITIVE,
                    ax - px * wa, y, az - pz * wa, ax + px * wa, y, az + pz * wa,
                    bx + px * wb, y, bz + pz * wb, bx - px * wb, y, bz - pz * wb,
                    EphemeraDraw.argb(ca, a0 * k0), EphemeraDraw.argb(ca, a0 * k0),
                    EphemeraDraw.argb(cb, a0 * k1), EphemeraDraw.argb(cb, a0 * k1));
        }
    }

    private static void emitAndDrawParticles(EphemeraInstance inst, EphemeraDraw d, EphemeraAnchor.Frame f,
                                             Random rnd, float activity) {
        EphemeraInstance.Particles p = inst.particles;
        p.lift = 0.4F * f.scale;
        int spawn = inst.emitCount((6.0F + 34.0F * activity) * d.emit());
        for (int k = 0; k < spawn; k++) {
            float a = rnd.nextFloat() * Mth.TWO_PI;
            float r = (0.45F + 0.15F * rnd.nextFloat()) * f.radius;
            p.spawn(Mth.cos(a) * r, 0.02F, Mth.sin(a) * r,
                    Mth.cos(a) * 0.15F * f.radius, (0.5F + 0.6F * rnd.nextFloat()) * f.scale, Mth.sin(a) * 0.15F * f.radius,
                    0.6F + 0.4F * rnd.nextFloat(), (0.045F + 0.02F * rnd.nextFloat()) * f.scale * d.tier.particleSize,
                    d.color(rnd.nextFloat(), 0.5F));
        }
        for (int i = 0, n = p.slots(); i < n; i++) {
            if (!p.alive(i)) {
                continue;
            }
            float life = p.t(i);
            float fade = life < 0.15F ? life / 0.15F : 1.0F - (life - 0.15F) / 0.85F;
            d.billboard(EphemeraStaging.PARTICLE, EphemeraStaging.ADDITIVE,
                    f.x + p.x[i], f.y + p.y[i], f.z + p.z[i], p.size[i] * (1.0F - 0.5F * life),
                    EphemeraDraw.argb(p.rgb[i], d.glowAlpha * fade));
        }
    }

    private static void drawCores(EphemeraDraw d, EphemeraAnchor.Frame f, float t, float activity) {
        float orbit = 0.55F * f.radius * (1.0F + 0.15F * activity);
        float speed = 1.2F + 1.8F * activity;
        for (int i = 0; i < CORES; i++) {
            float a = t * speed + Mth.TWO_PI * i / CORES;
            double x = f.x + Mth.cos(a) * orbit;
            double z = f.z + Mth.sin(a) * orbit;
            double y = f.y + Mth.sin(t * 1.7F + i * 2.1F) * 0.12D * f.scale;
            int c = d.color((float) i / CORES, 0.0F);
            d.billboard(EphemeraStaging.BASE, EphemeraStaging.TRANSLUCENT, x, y, z, 0.11F * f.scale,
                    EphemeraDraw.argb(c, 0.18F));
            d.billboard(EphemeraStaging.BASE, EphemeraStaging.ADDITIVE, x, y, z, 0.06F * f.scale,
                    EphemeraDraw.argb(c, d.glowAlpha * (0.6F + 0.4F * activity)));
        }
    }
}
