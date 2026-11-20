package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.env0;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;

/**
 * 踏涡：脚下一盘旋涡，涡上卷起一圈往上收的涡幕和几股升流裹住全身；跑起来地上留光痕和足印，落地炸开冲击环和地裂，
 * 挥砍时身前地面沿弧线依次喷出光柱，受击时涡幕被冲散再卷起来，每隔几秒旋涡涌起一次。
 */
public final class VortexStyle extends ProtoStyle<VortexStyle.St> {

    private record Params(float spin, float rise, float life, float swirl, float wallH, float scroll) {
        static final Params FIRE = new Params(2.6F, 1.5F, 0.85F, 2.2F, 1.15F, 1.1F);
        static final Params ICE = new Params(0.7F, -0.35F, 1.4F, 0.6F, 0.6F, -0.18F);
        static final Params ZAP = new Params(1.6F, 0.9F, 0.5F, 1.2F, 0.95F, 0);
        static final Params BASE = new Params(1.6F, 1.0F, 1.0F, 1.6F, 0.95F, 0.5F);

        static Params of(int kind) {
            return switch (kind) {
                case 1 -> FIRE;
                case 2 -> ICE;
                case 3 -> ZAP;
                default -> BASE;
            };
        }
    }

    private static final int PRINTS = 8, BOLTS = 4, NC_MAX = 14, NV_MAX = 3;

    public static final class St extends ProtoStyle.State {
        boolean ready, lite;
        float sc;
        FxParticles plume, core, spark;
        FxTrail trail;
        final double[] prX = new double[PRINTS], prY = new double[PRINTS], prZ = new double[PRINTS];
        final float[] prT = new float[PRINTS], prRot = new float[PRINTS];
        int nPr;
        long lastStep;
        final float[] acc = new float[3];
        float spinA, wallU, wallV, burstT, nextBurst, boltT, zapK, crystalT;
        final float[] bA0 = new float[BOLTS], bR0 = new float[BOLTS], bA1 = new float[BOLTS], bH = new float[BOLTS];
        final int[] bSpr = new int[BOLTS];
        final boolean[] bOn = new boolean[BOLTS];
        int nb;
        final ElementMotif fx = new ElementMotif();
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(16);
    private final EphemeraPen.Ribbon pts = new EphemeraPen.Ribbon(16);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final double[][] rx = new double[NV_MAX + 1][NC_MAX + 1], ry = new double[NV_MAX + 1][NC_MAX + 1], rz = new double[NV_MAX + 1][NC_MAX + 1];
    private final float[] dir = new float[3];
    private final ElementMotif.Sites sites = new ElementMotif.Sites();

    @Override
    public float extent() {
        return 2.8F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.ready = false;
        s.fx.reset();
    }

    private void init(St s, FxMotion m) {
        s.ready = true;
        s.lite = m.lite;
        s.sc = m.sc;
        float t = (float) m.t;
        s.plume = new FxParticles(m.lite ? 6 : 22);
        s.core = new FxParticles(m.lite ? 12 : 50);
        s.spark = new FxParticles(m.lite ? 8 : 34);
        s.trail = new FxTrail(0.55F, 3.4F * m.sc, 0.03F * m.sc, 3 * m.sc);
        s.nPr = 0;
        s.lastStep = (long) Math.floor(m.walkPhase / Math.PI);
        s.acc[0] = s.acc[1] = s.acc[2] = 0;
        s.spinA = s.wallU = s.wallV = 0;
        s.burstT = -10;
        s.nextBurst = t + 1.0F;
        s.nb = 0;
        s.boltT = 0;
        s.zapK = 0;
        s.crystalT = t - 2;
    }

    private static float mod05(float v) {
        return ((v % 0.5F) + 0.5F) % 0.5F;
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        boolean lite = m.lite, calm = m.calm;
        if (!s.ready || s.lite != lite || Math.abs(s.sc - m.sc) > 1.0E-3F) {
            init(s, m);
        }
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc;
        float fwdX = m.fwdX, fwdZ = m.fwdZ, rightX = m.rightX, rightZ = m.rightZ;
        int C = m.color, deep = mul(C, 0.14F), body = mul(C, 0.5F), mid = mul(C, 0.32F), hot = mix(C, WHITE, 0.45F);
        int kind = m.kind;
        boolean zap = kind == 3, ice = kind == 2, fire = kind == 1, ek = kind == 0;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        int from = d.out.pending();
        Params P = Params.of(kind);
        float Bk = m.burst, act = m.act;
        boolean fp = d.firstPersonSelf, sneak = m.sneak;
        float aroundR = W * 0.92F;
        double gy = m.groundY;
        s.plume.thin = s.core.thin = s.spark.thin = m.emit;
        // 亮地面（白天、雪地）上加法层几乎看不出来，靠压暗的托底和一层半透明的暗色旋臂托住形状
        boolean snow = d.glowAlpha >= EphemeraTier.GLOW_SNOW - 1.0E-3F;
        float bright = snow ? 1 : clamp((d.glowAlpha - EphemeraTier.GLOW_NIGHT) / (EphemeraTier.GLOW_DAY - EphemeraTier.GLOW_NIGHT), 0, 1);
        float under = snow ? 1 : 0.45F * bright;

        if (t >= s.nextBurst) {
            s.burstT = t;
            s.nextBurst = t + 2.8F + rnd() * 1.4F;
        }
        float sinceB = t - s.burstT;
        float eB = env0(sinceB, 0.1F, 0.15F, 0.7F) * (calm ? m.idle : 1);
        float eJump = m.env(m.since(m.tJump), 0.04F, 0.1F, 0.4F);
        float eLand = m.env(m.since(m.tLand), 0.02F, 0.05F, 0.6F) * clamp(m.landSpeed / 8, 0.6F, 1.5F);
        float eRun = m.env(m.since(m.tRunStart), 0.04F, 0.1F, 0.5F);
        float eStop = m.env(m.since(m.tStop), 0.04F, 0.1F, 0.55F);
        // 受击：涡幕先被冲散（往外鼓、变淡），0.6 秒里重新卷起来
        float eHurt = m.env(m.since(m.tHurt), 0.03F, 0.1F, 0.5F);
        float spd = clamp(m.speed / 5.6F, 0, 1);
        float inten = (sneak ? 0.5F : 1) * (1 + 0.4F * eB + 0.4F * eLand * Bk);

        double Gx = m.x, Gy = gy + 0.03, Gz = m.z;
        // 矮胖体型宽得多，范围上限却跟着身高走，所以封个顶
        float Rg = Math.min(W * 1.2F + H * 0.35F, 1.45F * sc) * (sneak ? 0.72F : 1) * (1 + 0.18F * eB + (0.3F * eLand + 0.25F * eHurt) * Bk)
                * (calm ? 0.84F + 0.08F * act : 1);
        s.spinA += dt * P.spin * (1 + 1.2F * eB + 0.8F * spd + 1.5F * eRun) * (sneak ? 0.5F : 1);
        float spinA = s.spinA;
        float lift = clamp(1 - m.heightAboveGround / (H * 0.9F), 0, 1);
        if (zap && t - s.boltT > 0.06F) {
            s.boltT = t;
            s.zapK = rnd();
        }
        float zapK = s.zapK;

        pen.cat = EphemeraStaging.BASE;
        // 地面：暗底 + 主色底（亮地面上靠这两层托住颜色）+ 发光盘
        pen.s(SOFT).alphaBlend().rgb(deep).a(Math.min(0.95F, (0.75F + 0.15F * under) * (0.5F + 0.5F * lift))).flat(Gx, Gy - 0.012, Gz, Rg * 3, Rg * 3);
        pen.s(SOFT).alphaBlend().rgb(body).a(0.5F * lift).flat(Gx, Gy - 0.008, Gz, Rg * 2.3F, Rg * 2.3F);
        pen.s(SOFT).rgb(C).a(0.2F * inten * lift).flat(Gx, Gy, Gz, Rg * 2, Rg * 2);
        pen.s(SOFTRING).rgb(C).a(0.6F * inten * lift).flat(Gx, Gy + 0.005, Gz, Rg * 2.3F, Rg * 2.3F, spinA * 0.5F);
        if (ek) {
            fx.ground(Gx, gy, Gz, Rg * 0.5F, Math.min(1, inten) * lift);
            // 爆发先画，涡幕和升流上的凸起按剩下的余量让
            pen.cat = EphemeraStaging.PARTICLE;
            fx.events(Gx, gy, Gz, Rg * 1.2F);
            fx.bursts();
            pen.cat = EphemeraStaging.BASE;
            sites.clear();
            for (int k = 0; k < 8; k++) {
                // 第一人称只留脚边那圈，腰上冒的会糊在镜头前
                if (fp && k % 2 != 0) {
                    continue;
                }
                float a = k * TAU / 8 + spinA * 0.6F, ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float rr = Rg * 0.6F, hy = H * (k % 2 == 0 ? 0.08F : 0.45F) * (sneak ? 0.6F : 1);
                sites.add(Gx + ca * rr, gy + hy, Gz + sa * rr, ca * 0.96F, 0.28F, sa * 0.96F);
            }
        }

        // 旋臂：平躺的丝带，螺旋往外甩
        int NS = lite ? 6 : 10;
        for (int j = 0; j < 3; j++) {
            pts.clear();
            for (int i = 0; i <= NS; i++) {
                float f = (float) i / NS, r = Rg * (0.12F + 0.98F * f);
                float ang = spinA + j * TAU / 3 - f * 2.4F;
                if (fire) {
                    ang += 0.15F * (float) Math.sin(t * 9 + f * 6 + j);
                }
                float w = Rg * 0.42F * (float) Math.pow(Math.sin(Math.PI * Math.min(1, f * 1.1F)), 0.6) * (1 + 0.4F * eB);
                if (ice) {
                    w *= 0.8F;
                }
                pts.add(Gx + Math.cos(ang) * r, Gy + 0.006 * j, Gz + Math.sin(ang) * r, w, lift);
            }
            float u0 = zap ? (zapK * 0.5F + j * 0.13F) % 0.5F : (t * 0.5F + j * 0.17F) % 0.5F;
            if (under > 0.01F) {
                pen.s(BAND).alphaBlend().rgb(mul(C, 0.3F)).ribbon(rib.scaled(pts, 1.3F, 0.5F * under), EphemeraPen.FACE_FLAT, 0.2F, 0.8F);
            }
            pen.s(zap ? CRACK_0 : ice ? FLOW_2 : FLOW_0).rgb(C).ribbon(rib.scaled(pts, zap ? 1.6F : 1.3F, 0.85F * inten), EphemeraPen.FACE_FLAT, u0, u0 + 0.5F);
            if (!lite || j == 0) {
                pen.s(zap ? CRACK_1 : STREAK).rgb(hot).ribbon(rib.scaled(pts, zap ? 0.8F : 0.35F, inten), EphemeraPen.FACE_FLAT,
                        zap ? u0 : 0, zap ? u0 + 0.5F : 1);
            }
        }

        // 地面光痕：只有跑起来才看得出
        s.trail.push(m.x, gy + 0.04, m.z, t);
        float trA = clamp(spd * 1.2F, 0, 1) * (sneak ? 0.5F : 1);
        if (trA > 0.02F) {
            pen.cat = EphemeraStaging.TRAIL;
            int seg = lite ? 6 : 12;
            s.trail.sample(seg, sample);
            final float fRg = Rg, fA = trA, fI = inten;
            pen.s(STREAK).alphaBlend().rgb(lite ? body : mid);
            FxTrail.draw(pen, sample, rib, (q, i) -> fRg * 1.5F * (1 - q * 0.6F), (q, i) -> 0.5F * fA * (1 - q), null, m.trailW, m.trailA,
                    EphemeraPen.FACE_FLAT, 0.5F, 1);
            float u0 = mod05(-t * 1.2F);
            pen.s(ice ? FLOW_2 : FLOW_0).rgb(C);
            FxTrail.draw(pen, sample, rib, (q, i) -> fRg * 1.2F * (1 - q * 0.7F), (q, i) -> 0.8F * fA * fI * (1 - q) * smooth(q * 5), null,
                    m.trailW, m.trailA, EphemeraPen.FACE_FLAT, u0, u0 + 0.5F);
            pen.cat = EphemeraStaging.BASE;
        }

        // 足印：每半个步伐周期落一个
        long stepN = (long) Math.floor(m.walkPhase / Math.PI);
        if (stepN != s.lastStep) {
            s.lastStep = stepN;
            if (m.onGround && m.speed > 0.5F) {
                if (s.nPr >= (lite ? 4 : PRINTS)) {
                    for (int k = 1; k < s.nPr; k++) {
                        s.prX[k - 1] = s.prX[k];
                        s.prY[k - 1] = s.prY[k];
                        s.prZ[k - 1] = s.prZ[k];
                        s.prT[k - 1] = s.prT[k];
                        s.prRot[k - 1] = s.prRot[k];
                    }
                    s.nPr--;
                }
                float sd = (stepN % 2 != 0 ? 1 : -1) * W * 0.3F;
                int k = s.nPr++;
                s.prX[k] = m.x + rightX * sd;
                s.prY[k] = gy + 0.045;
                s.prZ[k] = m.z + rightZ * sd;
                s.prT[k] = t;
                s.prRot[k] = (float) Math.atan2(fwdZ, fwdX);
            }
        }
        for (int k = 0; k < s.nPr; k++) {
            float age = t - s.prT[k], kk = 1 - smooth(age / 1.3F);
            if (kk <= 0) {
                continue;
            }
            float sz = W * 1.1F * (1 + 0.3F * smooth(age / 0.25F));
            if (under > 0.01F) {
                pen.s(RUNE).alphaBlend().rgb(mid).a(0.6F * kk * under).flat(s.prX[k], s.prY[k] - 0.002, s.prZ[k], sz * 1.05F, sz * 1.05F, s.prRot[k] + age * 0.5F);
            }
            pen.s(RUNE).rgb(C).a(0.9F * kk * inten).flat(s.prX[k], s.prY[k], s.prZ[k], sz, sz, s.prRot[k] + age * 0.5F);
            if (!lite) {
                pen.s(SOFT).rgb(hot).a(0.35F * kk * kk * inten).flat(s.prX[k], s.prY[k] + 0.004, s.prZ[k], sz * 0.8F, sz * 0.8F);
            }
        }

        // 冰：脚边一圈冰晶，涌起时碎掉再长回来
        boolean burstNow = sinceB < dt * 1.5F;
        if (ice) {
            if (burstNow) {
                s.crystalT = t;
            }
            int n = lite ? 4 : 7;
            float grow = smooth((t - s.crystalT - 0.15F) / 1.1F);
            for (int i = 0; i < n; i++) {
                float ang = i * TAU / n + 0.4F + spinA * 0.15F, rr = Rg * (0.62F + 0.12F * (i % 2));
                float hgt = H * (0.32F + 0.14F * ((i * 5) % 3) / 2) * grow * (sneak ? 0.6F : 1);
                if (hgt < 0.02F) {
                    continue;
                }
                double bx = Gx + Math.cos(ang) * rr, bz = Gz + Math.sin(ang) * rr, by = Gy + hgt * 0.45F;
                float lean = 0.25F * (i % 2 != 0 ? 1 : -1);
                pen.s(CRYSTAL).alphaBlend().rgb(body).a(0.7F * lift).billboard(bx, by, bz, hgt * 0.5F, hgt, lean);
                pen.s(CRYSTAL).rgb(hot).a(0.8F * inten * lift).billboard(bx, by, bz, hgt * 0.5F, hgt, lean);
            }
            if (burstNow) {
                for (int i = 0; i < (lite ? 5 : 14); i++) {
                    float ang = rnd() * TAU;
                    EphemeraFx.randDir(dir);
                    s.spark.spec().pos(Gx + Math.cos(ang) * Rg * 0.65F, Gy + H * 0.2F, Gz + Math.sin(ang) * Rg * 0.65F)
                            .vel(dir[0] * 2 * sc, (dir[1] + 1) * 2 * sc, dir[2] * 2 * sc).life(0.7F).size(H * 0.07F, H * 0.03F).sprite(CRYSTAL).rgb(hot)
                            .grav(H * 6).rot(rnd() * 6).spin(8).floor(gy + 0.02).force().spawn();
                }
            }
        }

        boolean air = !m.onGround;
        double bpx = m.x, bpy = air ? gy + (m.y - gy) * 0.85 : gy, bpz = m.z;
        float leanX = m.velX * -0.09F, leanZ = m.velZ * -0.09F;

        // 第一人称：涡幕、柱光和升流都裹着镜头，只留地上的
        if (!fp) {
            // 涡幕：一圈往上收的筒，纹理斜着往上卷；正对镜头那面薄、两侧边缘叠得厚，看着就是一根卷起来的涡
            int NC = lite ? 8 : NC_MAX, NV = lite ? 2 : NV_MAX;
            float wallH = H * P.wallH * (sneak ? 0.7F : 1) * (1 + 0.15F * eB + 0.2F * eJump) * (1 - 0.35F * eHurt) * (calm ? 0.55F + 0.3F * act : 1);
            float r0 = Rg * (0.85F + 0.25F * eHurt * Bk), r1 = Math.max(aroundR * 1.45F, Rg * (ice ? 0.75F : 0.5F)) * (1 + 0.6F * eHurt * Bk);
            // 电击不连续滚，隔一会儿跳一格
            if (zap) {
                s.wallU = zapK * 0.5F;
                s.wallV = ((zapK * 7) % 1) * 0.5F;
            } else {
                s.wallU = mod05(s.wallU + dt * 0.12F * (P.swirl / 1.6F) * (1 + 1.5F * eB + spd));
                s.wallV = mod05(s.wallV + dt * P.scroll * (1 + eB + 0.6F * spd));
            }
            for (int k = 0; k <= NV; k++) {
                float f = (float) k / NV, rr = r0 + (r1 - r0) * (float) Math.pow(f, 0.8);
                for (int j = 0; j <= NC; j++) {
                    float ang = spinA * 0.6F + j * TAU / NC;
                    double x = bpx + Math.cos(ang) * rr + leanX * f * f * 2.2F, y = bpy + 0.03 + f * wallH, z = bpz + Math.sin(ang) * rr + leanZ * f * f * 2.2F;
                    if (zap) {
                        EphemeraFx.randDir(dir);
                        float jk = H * 0.025F * f;
                        x += dir[0] * jk;
                        y += dir[1] * jk;
                        z += dir[2] * jk;
                    }
                    rx[k][j] = x;
                    ry[k][j] = y;
                    rz[k][j] = z;
                }
            }
            float wallA = inten * lift * (1 - 0.6F * eHurt) * (zap ? (zapK < 0.15F ? 0.3F : 1) : 1) * (calm ? 0.55F + 0.3F * act : 1);
            float bodyA = (sneak ? 0.5F : 1) * lift * (1 - 0.5F * eHurt) * (calm ? 0.5F : 1);
            for (int k = 0; k < NV; k++) {
                for (int j = 0; j < NC; j++) {
                    if (zap && hash(j * 13 + k * 7 + (int) Math.floor(zapK * 50)) < 0.2F) {
                        continue;
                    }
                    float ua = s.wallU + 0.5F * j / NC, ub = s.wallU + 0.5F * (j + 1) / NC;
                    float vt = s.wallV + 0.5F * (1 - (float) (k + 1) / NV), vb = s.wallV + 0.5F * (1 - (float) k / NV);
                    float a0 = wallVa(k + 1, j, NV, fire, t), a1 = wallVa(k + 1, j + 1, NV, fire, t), a2 = wallVa(k, j + 1, NV, fire, t), a3 = wallVa(k, j, NV, fire, t);
                    if (!lite || k == 0) {
                        pen.s(SHEATH).alphaBlend().rgb(body).a(0.72F * bodyA).va(a0, a1, a2, a3).uv4(ua, vt, ub, vt, ub, vb, ua, vb);
                        wallQuad(k, j);
                    }
                    pen.s(SHEATH).rgb(C).a(0.7F * wallA).va(a0, a1, a2, a3).uv4(ua, vt, ub, vt, ub, vb, ua, vb);
                    wallQuad(k, j);
                }
            }
            if (ek) {
                // 涡幕顶上是淡出去的，顶沿取在还看得清的六成高处
                float fe = 0.62F, re = r0 + (r1 - r0) * (float) Math.pow(fe, 0.8);
                int ne = lite ? 2 : 7;
                for (int j = 0; j < ne; j++) {
                    if (!lite && d.out.pending() - from > 380) {
                        break;
                    }
                    float ang = spinA * 0.6F + (j + 0.5F) * TAU / ne, ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
                    double x = bpx + ca * re + leanX * fe * fe * 2.2F, y = bpy + 0.03 + fe * wallH, z = bpz + sa * re + leanZ * fe * fe * 2.2F;
                    float oy = m.el == FxMotion.POISON ? -1.4F : m.el == FxMotion.RADIATION ? 0.9F : 0.35F, l = (float) Math.sqrt(1 + oy * oy);
                    float w = W * 0.2F * (calm ? 0.85F : 1);
                    fx.accent(x, y, z, ca / l, oy / l, sa / l, w * (m.el == FxMotion.POISON ? 2.2F : 3), w, j * 7 + 3, Math.min(1, wallA * 1.3F));
                }
            }

            // 柱身的整体光：竖直的宽光条
            {
                double tx = bpx + leanX * 1.4F, ty = bpy + H * (sneak ? 0.8F : 1.15F) * (1 + 0.2F * eB) * (calm ? 0.8F : 1), tz = bpz + leanZ * 1.4F;
                double by = bpy - H * 0.1F;
                pen.s(STREAK).alphaBlend().rgb(deep).a(calm ? 0.25F : 0.4F).beam(bpx, by, bpz, tx, ty, tz, Rg * (calm ? 1.3F : 1.8F));
                pen.s(STREAK).rgb(C).a(0.14F * inten * (ice ? 0.7F : 1) * (calm ? 0.6F : 1)).beam(bpx, by, bpz, tx, ty, tz, Rg * (calm ? 1.0F : 1.4F));
                if (!lite) {
                    pen.s(STREAK).rgb(hot).a(0.2F * inten).beam(bpx, by, bpz, bpx + (tx - bpx) * 0.7, by + (ty - by) * 0.7, bpz + (tz - bpz) * 0.7, Rg * 0.6F);
                }
            }

            // 升流：几股从涡里盘上去的能量带，纹理一直往上走（冰往下走）
            {
                int NH = lite || calm ? 2 : 3, n = lite ? 6 : 10;
                float colH = H * (sneak ? 0.7F : 1.05F) * (1 + 0.15F * eB + 0.25F * eJump) * (calm ? 0.7F + 0.2F * act : 1);
                float dirUp = ice ? -1 : 1;
                for (int j = 0; j < NH; j++) {
                    if (zap && ((zapK * 5 + j * 0.37F) % 1) < 0.25F) {
                        continue;
                    }
                    pts.clear();
                    float ph = j * TAU / NH + spinA * 0.8F;
                    for (int i = 0; i <= n; i++) {
                        float f = (float) i / n, ang = ph + f * 4.2F;
                        float r = Math.max(aroundR * 1.2F, Rg * (0.6F - 0.3F * f)) * (1 + 0.25F * eB + 0.4F * eHurt * Bk);
                        double x = bpx + Math.cos(ang) * r + leanX * f * f * 2.2F, y = bpy + 0.05 + f * colH, z = bpz + Math.sin(ang) * r + leanZ * f * f * 2.2F;
                        if (zap) {
                            EphemeraFx.randDir(dir);
                            x += dir[0] * H * 0.04F;
                            y += dir[1] * H * 0.04F;
                            z += dir[2] * H * 0.04F;
                        }
                        float w = W * 0.6F * (float) Math.pow(Math.sin(Math.PI * f), 0.7) * (1 + 0.4F * eB) * (calm ? 0.7F : 1);
                        if (fire) {
                            w *= 0.8F + 0.4F * noise(t * 8 + f * 5 + j * 4, 5);
                        }
                        pts.add(x, y, z, w, (float) Math.pow(Math.sin(Math.PI * Math.min(1, f * 1.15F)), 0.8));
                    }
                    float sp = fire ? 1.6F : ice ? 0.35F : 1;
                    float u0 = zap ? zapK * 0.5F : mod05(-t * sp * dirUp * 0.5F + j * 0.21F);
                    if (zap) {
                        pen.s(CRACK_0).alphaBlend().rgb(mul(C, 0.35F)).ribbon(rib.scaled(pts, 2.0F, 0.7F), EphemeraPen.FACE_CAM, u0, u0 + 0.5F);
                    }
                    pen.s(zap ? CRACK_0 : ice ? FLOW_2 : FLOW_0).rgb(C).ribbon(rib.scaled(pts, zap ? 1.8F : 1.7F, 0.8F * inten), EphemeraPen.FACE_CAM, u0, u0 + 0.5F);
                    pen.s(zap ? CRACK_1 : STREAK).rgb(hot).ribbon(rib.scaled(pts, zap ? 0.9F : 0.3F, 0.85F * inten), EphemeraPen.FACE_CAM, 0, 1);
                    if (ek && (!lite || j == 0) && (lite || d.out.pending() - from < 420)) {
                        // 升流的头在 0.75 处还亮，再往上就淡没了，凸起从这里接出去
                        int a = Math.round(0.75F * n), b = Math.min(n, a + 1);
                        double tx = pts.x[b] - pts.x[a], ty = pts.y[b] - pts.y[a] + H * 0.08F, tz = pts.z[b] - pts.z[a], tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                        if (tl > 1.0E-5) {
                            float w = W * 0.26F * (calm ? 0.85F : 1);
                            fx.accent(pts.x[a], pts.y[a], pts.z[a], tx / tl, ty / tl, tz / tl, H * 0.24F * (calm ? 0.8F : 1), w, 60 + j, 0.85F * Math.min(1, inten));
                            sites.add(pts.x[a], pts.y[a], pts.z[a], (float) (tx / tl), (float) (ty / tl), (float) (tz / tl));
                        }
                    }
                }
            }

            // 电击：从地面涡里往身上打几道跳动的电弧
            if (zap) {
                int nb = lite ? 2 : BOLTS;
                if (s.nb == 0 || rnd() < dt * 14) {
                    s.nb = nb;
                    for (int i = 0; i < nb; i++) {
                        s.bA0[i] = rnd() * TAU;
                        s.bR0[i] = 0.5F + rnd() * 0.6F;
                        s.bA1[i] = s.bA0[i] + (rnd() - 0.5F) * 1.6F;
                        s.bH[i] = 0.25F + rnd() * 0.85F;
                        s.bSpr[i] = rnd() < 0.5F ? CRACK_0 : CRACK_1;
                        s.bOn[i] = rnd() < 0.85F;
                    }
                }
                for (int i = 0; i < s.nb; i++) {
                    if (!s.bOn[i]) {
                        continue;
                    }
                    double ax = Gx + Math.cos(s.bA0[i]) * Rg * s.bR0[i], az = Gz + Math.sin(s.bA0[i]) * Rg * s.bR0[i];
                    double bx = bpx + Math.cos(s.bA1[i]) * aroundR * 1.3F, by = bpy + H * s.bH[i] * (sneak ? 0.6F : 1), bz = bpz + Math.sin(s.bA1[i]) * aroundR * 1.3F;
                    if (!lite) {
                        pen.s(s.bSpr[i]).alphaBlend().rgb(mul(C, 0.35F)).a(0.7F).beam(ax, Gy, az, bx, by, bz, H * 0.24F);
                    }
                    pen.s(s.bSpr[i]).rgb(C).a(0.7F * inten).beam(ax, Gy, az, bx, by, bz, H * 0.2F);
                    pen.s(s.bSpr[i]).rgb(hot).a(inten).beam(ax, Gy, az, bx, by, bz, H * 0.09F);
                }
            }
        }

        // 挥砍：身前地面沿弧线依次喷出光柱
        float sSw = m.since(m.tSwing) / m.burstTime;
        if (sSw >= 0 && sSw < 0.75F) {
            int side = m.swingCount % 2 != 0 ? 1 : -1, nj = lite ? 5 : 9;
            for (int j = 0; j < nj; j++) {
                float f = (float) j / (nj - 1), local = sSw - f * 0.16F;
                if (local < 0) {
                    continue;
                }
                float e = env0(local, 0.06F, 0.06F, 0.38F);
                if (e <= 0) {
                    continue;
                }
                float ang = side * (-1.25F + 2.5F * f), ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
                float dx = fwdX * ca + rightX * sa, dz = fwdZ * ca + rightZ * sa, rr = Rg * 1.1F * (1 + 0.15F * smooth(local / 0.3F));
                double bx = Gx + dx * rr, by = Gy, bz = Gz + dz * rr;
                float hgt = H * (0.55F + 0.35F * (float) Math.sin(Math.PI * f)) * (0.4F + 0.6F * e) * (calm ? 0.6F : 1);
                if (ice) {
                    pen.s(CRYSTAL).alphaBlend().rgb(body).a(0.6F * e).billboard(bx, by + hgt * 0.45F, bz, hgt * 0.45F, hgt, (f - 0.5F) * 0.6F);
                    pen.s(CRYSTAL).rgb(hot).a(e).billboard(bx, by + hgt * 0.45F, bz, hgt * 0.45F, hgt, (f - 0.5F) * 0.6F);
                } else {
                    pen.s(zap ? CRACK_0 : COMET).rgb(C).a(e * inten).beam(bx, by + hgt, bz, bx, by, bz, H * (zap ? 0.26F : 0.42F));
                    pen.s(zap ? CRACK_1 : COMET).rgb(hot).a(e * inten).beam(bx, by + hgt, bz, bx, by, bz, H * 0.14F);
                }
                if (!lite) {
                    pen.s(SOFTCORE).rgb(C).a(e * 0.8F).flat(bx, by + 0.01, bz, Rg * 0.7F * e, Rg * 0.7F * e);
                }
            }
        }

        // 落地：冲击环 + 地裂
        if (eLand > 0.01F) {
            float sl = m.since(m.tLand) / m.burstTime, k = smooth(sl / 0.45F);
            float r = Math.min(2.3F * sc, Rg * (0.4F + 1.1F * k * Bk) * Math.min(1.2F, 0.75F + 0.3F * m.landSpeed / 8));
            pen.s(SOFT).alphaBlend().rgb(deep).a(0.5F * eLand).flat(Gx, Gy + 0.008, Gz, r * 2.2F, r * 2.2F);
            pen.s(SHOCK).rgb(hot).a(Math.min(1, eLand * 1.2F)).flat(Gx, Gy + 0.012, Gz, r * 2, r * 2);
            if (!lite) {
                pen.s(SOFTRING).rgb(C).a(eLand * 0.4F).flat(Gx, Gy + 0.01, Gz, r * 1.6F, r * 1.6F);
            }
            int nc = lite ? 4 : 8;
            for (int i = 0; i < nc; i++) {
                float ang = i * TAU / nc + 0.3F, len = Rg * (0.9F + 0.5F * ((i * 7) % 3) / 2) * (0.5F + 0.5F * k);
                float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang), w = H * 0.12F * eLand;
                double ax = Gx + ca * Rg * 0.25F, az = Gz + sa * Rg * 0.25F, bx = Gx + ca * len, bz = Gz + sa * len, y = Gy + 0.015;
                float nX = -sa * w, nZ = ca * w;
                pen.s(zap ? CRACK_0 : STREAK).rgb(hot).a(eLand).quad(ax + nX, y, az + nZ, bx + nX, y, bz + nZ, bx - nX, y, bz - nZ, ax - nX, y, az - nZ);
            }
        }

        // 受击：地上一圈冲击环往外推
        if (eHurt > 0.01F) {
            float k = smooth(m.since(m.tHurt) / m.burstTime / 0.45F), r = Math.min(2.3F * sc, Rg * (0.6F + 0.9F * k * Bk));
            pen.s(SHOCK).rgb(C).a(eHurt).flat(Gx, Gy + 0.014, Gz, r * 2, r * 2);
        }

        if (eStop > 0.02F && !lite) {
            double ax = m.x + fwdX * W * 0.5F, az = m.z + fwdZ * W * 0.5F, y = gy + 0.045;
            double bx = ax + fwdX * Rg * 1.2F * eStop, bz = az + fwdZ * Rg * 1.2F * eStop;
            float w0 = Rg * 0.25F, w1 = Rg * 0.15F;
            pen.s(STREAK).rgb(C).a(0.8F * eStop).uv(0, 0, 1, 1).quad(ax - rightX * w0, y, az - rightZ * w0, bx - rightX * w1, y, bz - rightZ * w1,
                    bx + rightX * w1, y, bz + rightZ * w1, ax + rightX * w0, y, az + rightZ * w0);
        }

        // 粒子只留三类：大而淡的升雾、沿螺旋升起的亮线、零星火花
        float rate = (sneak ? 0.45F : 1) * (1 + 2.0F * eB + 0.6F * spd + 1.5F * eJump) * (calm ? 0.5F + 0.5F * act : 1);
        float riseV = H * P.rise * (sneak ? 0.45F : 1) * (1 + 0.6F * eB);
        float inX = m.velX * 0.55F, inZ = m.velZ * 0.55F;
        double floor = gy + 0.02;
        s.acc[0] += dt * (lite ? 6 : 20) * rate;
        while (s.acc[0] >= 1) {
            s.acc[0] -= 1;
            if (ice) {
                float ang = ring(Rg, 0.3F, 0.8F, bpx, bpy, bpz, H * 0.3F, H * 0.8F);
                s.plume.spec().pos(ringX, ringY, ringZ).vel(inX - (float) Math.sin(ang) * 0.3F * sc, -H * 0.3F, inZ + (float) Math.cos(ang) * 0.3F * sc)
                        .life(1.3F).size(H * 0.4F, H * 0.75F).sprite(SOFT).rgb(mix(C, WHITE, 0.3F)).a(0.16F).floor(floor).fade(0.3F, 0.6F).data(1).spawn();
            } else {
                float ang = ring(Rg, 0.2F, 0.6F, bpx, bpy, bpz, 0, H * 0.2F);
                s.plume.spec().pos(ringX, ringY, ringZ)
                        .vel(inX - (float) Math.sin(ang) * P.swirl * 0.4F * sc, riseV * 0.8F, inZ + (float) Math.cos(ang) * P.swirl * 0.4F * sc).life(P.life)
                        .size(H * 0.4F, H * 0.65F).sprite(SOFT).rgb(C).a(0.13F).drag(0.6F).fade(0.25F, 0.6F).data(1).spawn();
            }
        }
        s.acc[1] += dt * (lite ? 6 : 16) * rate * (ek ? 0 : 1);
        while (s.acc[1] >= 1) {
            s.acc[1] -= 1;
            if (ice) {
                ring(Rg, 0.1F, 1.0F, bpx, bpy, bpz, H * 0.7F, H * 1.3F);
                EphemeraFx.randDir(dir);
                s.core.spec().pos(ringX, ringY, ringZ).vel(inX + dir[0] * 0.15F * sc, dir[1] * 0.15F * sc, inZ + dir[2] * 0.15F * sc).life(1.3F)
                        .size(H * 0.045F, H * 0.03F).sprite(rnd() < 0.5F ? FLARE : DOT).rgb(hot).grav(H * 0.5F).drag(2.5F).floor(floor).rot(rnd() * 3).spawn();
            } else {
                float ang = ring(Rg, 0.15F, 0.6F, bpx, bpy, bpz, 0, H * 0.2F);
                s.core.spec().pos(ringX, ringY, ringZ)
                        .vel(inX - (float) Math.sin(ang) * P.swirl * 0.5F * sc, riseV * (0.9F + rnd() * 0.7F), inZ + (float) Math.cos(ang) * P.swirl * 0.5F * sc)
                        .life(P.life * (0.7F + rnd() * 0.5F)).size(H * 0.035F, H * 0.01F).sprite(EphemeraSprites.WHITE).rgb(hot).stretch(0.06F).drag(0.5F)
                        .data(1).spawn();
            }
        }
        s.acc[2] += dt * (lite ? 3 : 8) * rate * (ek ? 0 : 1);
        while (s.acc[2] >= 1) {
            s.acc[2] -= 1;
            ring(Rg, 0.2F, 0.9F, bpx, bpy, bpz, 0, H * 0.4F);
            EphemeraFx.randDir(dir);
            if (ice) {
                s.spark.spec().pos(ringX, ringY + H * 0.6F, ringZ).vel(dir[0] * 0.3F * sc, dir[1] * 0.3F * sc, dir[2] * 0.3F * sc).life(1.0F)
                        .size(H * 0.06F, H * 0.02F).sprite(CRYSTAL).rgb(hot).grav(H * 1.2F).rot(rnd() * 6).spin(3).floor(floor).spawn();
            } else if (zap) {
                s.spark.spec().pos(ringX, ringY + rnd() * H, ringZ).vel(dir[0] * 4 * sc, dir[1] * 4 * sc, dir[2] * 4 * sc).life(0.1F).size(H * 0.025F)
                        .sprite(EphemeraSprites.WHITE).rgb(WHITE).stretch(0.035F).spawn();
            } else {
                s.spark.spec().pos(ringX, ringY, ringZ).vel(inX + (rnd() - 0.5F) * sc, riseV * 1.8F, inZ + (rnd() - 0.5F) * sc).life(0.55F)
                        .size(H * 0.022F).sprite(EphemeraSprites.WHITE).rgb(hot).stretch(0.05F).drag(1).spawn();
            }
        }

        if (ek) {
            fx.motes(sites, 24 * rate, sc);
        }

        if (m.justLand) {
            geyser(s, lite ? 8 : 26, ice ? 3 : 6, 3, 0.7F, Rg, Gx, Gy, Gz, H, sc, hot, ice, floor);
            if (ice) {
                s.crystalT = t;
            }
        }
        if (m.justJump) {
            geyser(s, lite ? 5 : 14, 1, 4, 0.4F, Rg, Gx, Gy, Gz, H, sc, hot, ice, floor);
        }
        if (burstNow) {
            geyser(s, lite ? 5 : 16, ice ? 1.5F : 5, 1, 0.8F, Rg, Gx, Gy, Gz, H, sc, hot, ice, floor);
        }
        if (m.justRunStart) {
            kick(s, lite ? 5 : 16, -fwdX, -fwdZ, 5, m, H, W, sc, hot, floor);
        }
        if (m.justStop) {
            kick(s, lite ? 5 : 16, fwdX, fwdZ, 4, m, H, W, sc, hot, floor);
        }
        if (m.justHurt) {
            // 被冲散的涡幕碎成一圈往外甩的亮线
            for (int i = 0; i < (lite ? 8 : 24); i++) {
                float ang = rnd() * TAU, ca = (float) Math.cos(ang), sa = (float) Math.sin(ang), sp = 5 * sc * (0.6F + rnd() * 0.6F);
                s.spark.spec().pos(bpx + ca * Rg * 0.6F, bpy + H * (0.1F + rnd() * 0.8F), bpz + sa * Rg * 0.6F).vel(ca * sp, sc * (rnd() - 0.3F), sa * sp)
                        .life(0.35F).size(H * 0.04F).sprite(EphemeraSprites.WHITE).rgb(hot).stretch(0.05F).drag(2).force().spawn();
            }
        }

        // 粒子推进：打旋 + 受击时往外推
        final double ccx = bpx, ccz = bpz;
        final float push = eHurt * 6 * sc, tg = P.swirl * 2.2F * sc, inward = 0.8F * sc, jit = 0.12F * sc;
        final boolean fz = zap;
        FxParticles.Step swirl = (p, i, dtt) -> {
            if (p.data[i] != 0) {
                double ox = p.x[i] - ccx, oz = p.z[i] - ccz, rr = Math.sqrt(ox * ox + oz * oz);
                if (rr < 1.0E-6) {
                    rr = 1;
                }
                float nx = (float) (ox / rr), nz = (float) (oz / rr);
                p.vx[i] += (-nz * tg - nx * inward + nx * push) * dtt;
                p.vz[i] += (nx * tg - nz * inward + nz * push) * dtt;
            }
            if (fz && rnd() < dtt * 10) {
                EphemeraFx.randDir(dir);
                p.x[i] += dir[0] * jit;
                p.y[i] += dir[1] * jit;
                p.z[i] += dir[2] * jit;
            }
        };
        s.plume.step(dt, swirl);
        s.core.step(dt, swirl);
        s.spark.step(dt, null);
        // 第一人称：升雾和亮线都从脚边往上穿过视野，只画火花
        pen.cat = EphemeraStaging.PARTICLE;
        if (!fp) {
            s.plume.draw(pen, m.size, inten, Float.NaN);
            s.core.draw(pen, m.size, inten, Float.NaN);
        }
        s.spark.draw(pen, m.size, inten, Float.NaN);
    }

    private void wallQuad(int k, int j) {
        pen.quad(rx[k + 1][j], ry[k + 1][j], rz[k + 1][j], rx[k + 1][j + 1], ry[k + 1][j + 1], rz[k + 1][j + 1],
                rx[k][j + 1], ry[k][j + 1], rz[k][j + 1], rx[k][j], ry[k][j], rz[k][j]);
    }

    /** 竖向透明度：根部实、顶上散；火焰的顶边按列抖动 */
    private static float wallVa(int k, int j, int NV, boolean fire, float t) {
        float f = (float) k / NV;
        float a = (float) Math.pow(1 - f, fire ? 0.8 : 1.3) * (0.3F + 0.7F * smooth(f * 4 + 0.25F));
        if (fire && k > 0) {
            a *= 0.55F + 0.6F * noise(t * 7 + j * 1.7F + k * 3, 9);
        }
        return a;
    }

    private double ringX, ringY, ringZ;

    /** 在旋涡里随机挑一点放进 ringX/Y/Z，返回所在的角度 */
    private float ring(float Rg, float rMin, float rMax, double bx, double by, double bz, float y0, float y1) {
        float ang = rnd() * TAU, rr = Rg * (rMin + rnd() * (rMax - rMin));
        ringX = bx + Math.cos(ang) * rr;
        ringY = by + y0 + rnd() * (y1 - y0);
        ringZ = bz + Math.sin(ang) * rr;
        return ang;
    }

    private void geyser(St s, int n, float upK, float outK, float life, float Rg, double gx, double gy, double gz, float H, float sc,
                        int hot, boolean ice, double floor) {
        for (int i = 0; i < n; i++) {
            float ang = rnd() * TAU, rr = Rg * rnd() * 0.6F, ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            s.core.spec().pos(gx + ca * rr, gy + 0.05, gz + sa * rr)
                    .vel(ca * outK * sc * (0.5F + rnd()), upK * sc * (0.5F + rnd()), sa * outK * sc * (0.5F + rnd())).life(life).size(H * 0.07F, 0)
                    .sprite(SOFTCORE).rgb(hot).grav(ice ? H * 5 : H * 2).drag(1.5F).floor(floor).force().spawn();
        }
    }

    private void kick(St s, int n, float dx, float dz, float k, FxMotion m, float H, float W, float sc, int hot, double floor) {
        for (int i = 0; i < n; i++) {
            float o = (rnd() - 0.5F) * W, sp = k * sc * (0.5F + rnd());
            s.core.spec().pos(m.x + m.rightX * o, m.groundY + 0.08, m.z + m.rightZ * o).vel(dx * sp, 1.5F * sc * rnd(), dz * sp).life(0.5F)
                    .size(H * 0.06F, 0).sprite(SOFTCORE).rgb(hot).drag(2.5F).grav(H * 2).floor(floor).force().spawn();
        }
    }
}
