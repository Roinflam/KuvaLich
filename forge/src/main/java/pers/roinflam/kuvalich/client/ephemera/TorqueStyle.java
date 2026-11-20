package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 雷环：两段绞成绳的粗环从颈后的扣环出发，左右对称拱过双肩、落到锁骨前；绳外缠两道光丝，光脉从扣环沿绳流到绳头。
 * 腰两侧各垂一束光焰。跑动时绳头和光焰往后掠、拖出光迹，起跳绳头上扬，落地整圈一沉一张，挥砍时绳环张开、
 * 甩出一道绳形光弧，受击时绳股松散、闪烁错位。
 */
public final class TorqueStyle extends ProtoStyle<TorqueStyle.St> {

    static final int ROPE = EphemeraSprites.file("torque/rope");
    static final int KNOT = EphemeraSprites.file("torque/knot");

    // 右半边绳子中心线（身体局部：x 右、y 上、z 前；x、z 乘身宽，y 乘身高），左边照镜像。
    // 原版模型没有脖子，头底就是肩高，前半截压到 0.72 倍身高以下，不然正面看会挡住下巴和嘴。
    private static final float[][] PATH = {{0.1F, 0.79F, -0.62F}, {0.54F, 0.94F, -0.71F}, {0.97F, 1.03F, -0.37F},
            {1.15F, 0.95F, 0.08F}, {1.02F, 0.81F, 0.46F}, {0.62F, 0.72F, 0.64F}, {0.3F, 0.7F, 0.56F}};
    private static final int N_MAX = 15;
    /** 挥砍那一下粒子、元素母题粒子大约还要这么多面片，爆发占得多时副件按剩下的让 */
    private static final int PARTICLE_RESERVE = 70;

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        final FxKit.Spring body = new FxKit.Spring(), jetL = new FxKit.Spring(), jetR = new FxKit.Spring();
        FxParticles pt;
        final FxTrail[] tr = new FxTrail[2];
        boolean ready, lite;
        float sc, acc, ph;
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(24);
    private final EphemeraPen.Ribbon rib2 = new EphemeraPen.Ribbon(24);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final double[][] clX = new double[2][N_MAX], clY = new double[2][N_MAX], clZ = new double[2][N_MAX];
    private final float[] open = new float[2];
    private final double[] jx = new double[6], jy = new double[6], jz = new double[6];
    private final float[] jw = new float[6], ja = new float[6];
    private final double[] mx = new double[10], my = new double[10], mz = new double[10];
    private final double[] zx = new double[8], zy = new double[8], zz = new double[8];

    // 这一帧
    private double bX, bY, bZ, neckX, neckY, neckZ;
    private float H, W, rX, rZ, fX, fZ, sneak, run, jumpE, landE, hurtE, breath, amp;
    private int qh;
    // ctr / frame3 / radial / sideOf 的结果
    private double ox, oy, oz;
    private float tgX, tgY, tgZ, n1X, n1Y, n1Z, n2X, n2Y, n2Z, rdX, rdZ, sdX, sdY, sdZ;
    private final float[] cr = new float[3];

    @Override
    public float extent() {
        return 1.8F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.fx.reset();
        s.ready = false;
        s.body.reset();
        s.jetL.reset();
        s.jetR.reset();
    }

    private void init(St s, FxMotion m) {
        s.ready = true;
        s.lite = m.lite;
        s.sc = m.sc;
        s.pt = new FxParticles(m.lite ? 12 : 30);
        s.acc = 0;
        s.ph = 0;
        for (int i = 0; i < 2; i++) {
            s.tr[i] = new FxTrail(0.28F, 1.3F * m.sc, 0.04F * m.sc, 3 * m.sc);
        }
    }

    private static void catmull(float f, float[] out) {
        float g = clamp(f, 0, 1) * (PATH.length - 1);
        int k = Math.min(PATH.length - 2, (int) Math.floor(g));
        float t = g - k, t2 = t * t, t3 = t2 * t;
        float[] p0 = PATH[Math.max(0, k - 1)], p1 = PATH[k], p2 = PATH[k + 1], p3 = PATH[Math.min(PATH.length - 1, k + 2)];
        for (int a = 0; a < 3; a++) {
            out[a] = 0.5F * (2 * p1[a] + (-p0[a] + p2[a]) * t + (2 * p0[a] - 5 * p1[a] + 4 * p2[a] - p3[a]) * t2
                    + (-p0[a] + 3 * p1[a] - 3 * p2[a] + p3[a]) * t3);
        }
    }

    /** 一边绳子中心线上 f 处的点，写进 ox / oy / oz；side 0 = 左、1 = 右 */
    private void ctr(int side, float f) {
        int sg = side == 0 ? -1 : 1;
        float op = open[side];
        catmull(f, cr);
        float sn = (float) Math.sin(Math.PI * f);
        float zz = cr[2] * op - 0.45F * run * f * f;
        float yy = cr[1] - 0.03F * run * f + 0.08F * jumpE * f * f - 0.045F * landE * sn + 0.008F * breath * amp * sn;
        double lx = sg * cr[0] * W * op * sneak, ly = yy * H, lz = zz * W * sneak;
        ox = bX + rX * lx + fX * lz;
        oy = bY + ly;
        oz = bZ + rZ * lx + fZ * lz;
        if (hurtE > 0.02F) {
            int k = (int) Math.floor(f * 12) + sg * 50;
            float j = 0.14F * W * hurtE;
            ox += (hash(qh * 7 + k) - 0.5F) * j;
            oy += (hash(qh * 11 + k) - 0.5F) * j;
            oz += (hash(qh * 13 + k) - 0.5F) * j;
        }
    }

    /** 绳子在 f 处的切线 tg 和两根法线 n1（水平）、n2 */
    private void frame3(int side, float f) {
        ctr(side, Math.min(1, f + 0.02F));
        double ax = ox, ay = oy, az = oz;
        ctr(side, Math.max(0, f - 0.02F));
        double tx = ax - ox, ty = ay - oy, tz = az - oz, l = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (l < 1.0E-9) {
            tx = fX;
            ty = 0;
            tz = fZ;
            l = 1;
        }
        tgX = (float) (tx / l);
        tgY = (float) (ty / l);
        tgZ = (float) (tz / l);
        // tg × up
        float cx = -tgZ, cz = tgX, cl = (float) Math.sqrt(cx * cx + cz * cz);
        if (cl < 1.0E-3F) {
            n1X = rX;
            n1Y = 0;
            n1Z = rZ;
        } else {
            n1X = cx / cl;
            n1Y = 0;
            n1Z = cz / cl;
        }
        n2X = tgY * n1Z - tgZ * n1Y;
        n2Y = tgZ * n1X - tgX * n1Z;
        n2Z = tgX * n1Y - tgY * n1X;
    }

    /** 颈心指向 p 的水平方向 */
    private void radial(double px, double pz) {
        double dx = px - neckX, dz = pz - neckZ, l = Math.sqrt(dx * dx + dz * dz);
        if (l < 1.0E-6) {
            rdX = rX;
            rdZ = rZ;
        } else {
            rdX = (float) (dx / l);
            rdZ = (float) (dz / l);
        }
    }

    /** dir 在镜头里的侧向（dir × 镜头朝前），写进 sd */
    private void sideOf(double dx, double dy, double dz) {
        double sx = dy * pen.fwdZ - dz * pen.fwdY, sy = dz * pen.fwdX - dx * pen.fwdZ, sz = dx * pen.fwdY - dy * pen.fwdX;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1.0E-4) {
            sdX = pen.rightX;
            sdY = pen.rightY;
            sdZ = pen.rightZ;
        } else {
            sdX = (float) (sx / l);
            sdY = (float) (sy / l);
            sdZ = (float) (sz / l);
        }
    }

    private static float rnd01(int i) {
        double v = Math.sin(i * 127.1 + 31.7) * 43758.5;
        return (float) (v - Math.floor(v));
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        boolean lite = m.lite, fp = d.firstPersonSelf;
        if (!s.ready || s.lite != lite || Math.abs(s.sc - m.sc) > 1.0E-3F) {
            init(s, m);
        }
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        float dt = m.dt, t = (float) m.t;
        H = m.H;
        W = m.W;
        float sc = m.sc, act = m.act, Bk = m.burst;
        amp = m.amp;
        rX = m.rightX;
        rZ = m.rightZ;
        fX = m.fwdX;
        fZ = m.fwdZ;
        int ek = m.el;
        float speedK = switch (ek) {
            case ICE -> 0.45F;
            case ELECTRIC -> 1.3F;
            case POISON -> 0.6F;
            case MAGNETIC -> 0.8F;
            default -> 1.0F;
        };
        float sway = switch (ek) {
            case FIRE -> 1.0F;
            case ELECTRIC -> 0.2F;
            case POISON -> 0.4F;
            case MAGNETIC -> 0.5F;
            case RADIATION -> 0.1F;
            default -> 0.0F;
        };
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        float T = m.clock(), bt = m.beat(0), bl = m.blink(3), fk = m.flick(3);
        run = clamp(m.speed / 5.6F, 0, 1);
        sneak = m.sneak ? 0.9F : 1;
        float swE = m.env(m.since(m.tSwing), 0.03F, 0.06F, 0.4F);
        hurtE = m.env(m.since(m.tHurt), 0.02F, 0.12F, 0.45F);
        jumpE = m.env(m.since(m.tJump), 0.04F, 0.1F, 0.4F);
        landE = m.env(m.since(m.tLand), 0.02F, 0.06F, 0.35F);
        breath = 0.5F + 0.5F * (float) Math.sin(t * 1.7F);
        FxParticles P = s.pt;
        P.thin = m.emit;

        // 光脉和光丝的推进量：跑、挥砍时流得快；冰慢、电一格一格跳
        s.ph += dt * speedK * (0.4F + 0.8F * run + 2.4F * swE + 1.2F * hurtE);
        float ph = ek == ELECTRIC ? (float) Math.floor(s.ph / 0.04F) * 0.04F : s.ph;

        float A = (0.6F + 0.4F * act + 0.08F * breath) * bl * fk;
        if (hurtE > 0.05F) {
            A *= 0.5F + 0.5F * hash((int) Math.floor(t * 24));
        }
        qh = (int) Math.floor(t / 0.05F);
        int qe = qh;

        // 整套挂在脖子上跟着人，只留加减速和转弯的晃动
        s.body.step(m.x, m.y, m.z, m, 16, 0.6F);
        if (m.justHurt) {
            // 定稿是挨打后 0.03 秒内每帧推一下（60 帧下约两帧），这里一次推两份，和帧率无关
            s.body.vx -= fX * 2.4F * sc;
            s.body.vy += 1.6F * sc;
            s.body.vz -= fZ * 2.4F * sc;
        }
        bX = s.body.x;
        bY = s.body.y;
        bZ = s.body.z;
        neckX = bX;
        neckY = bY + 0.8F * H;
        neckZ = bZ;

        float rr = 0.15F * W * sneak * (1 + 0.18F * bt + 0.1F * swE);
        // 电、毒、磁的元素件本身就贵，光丝少分几段
        boolean heavy = ek == ELECTRIC || ek == POISON || ek == MAGNETIC;
        int N = lite ? 9 : 15, NHP = lite ? 9 : heavy ? 12 : 19;
        int ropeCol = ek == FIRE ? mul(mix(C, 0xFFD999, 0.25F), 0.75F)
                : ek == POISON ? mul(C, 0.7F)
                : ek == ICE ? mix(C, WHITE, 0.75F)
                : mix(C, WHITE, 0.6F);
        int bodyCol = hurtE > 0.05F && (qh & 1) != 0 ? WHITE : ropeCol;

        // 两边的中心线
        for (int side = 0; side < 2; side++) {
            float sgw = side == 0 ? 0.6F : 1.4F;
            open[side] = 1 + 0.03F * breath * amp + 0.18F * swE * Bk * sgw + 0.06F * bt + 0.14F * landE + 0.25F * hurtE;
            for (int i = 0; i < N; i++) {
                ctr(side, (float) i / (N - 1));
                clX[side][i] = ox;
                clY[side][i] = oy;
                clZ[side][i] = oz;
            }
        }

        // 元素爆发先画：电、磁的爆发一次就上百个面片，后面的副件按剩下的让
        int before = d.out.pending();
        pen.cat = lite ? EphemeraStaging.PARTICLE : EphemeraStaging.BASE;
        fx.events(m.x, m.groundY, m.z, W * 1.6F);
        fx.bursts();
        pen.cat = EphemeraStaging.BASE;
        int room = EphemeraTier.CAP_FULL - PARTICLE_RESERVE - (d.out.pending() - before);
        boolean trim1 = !lite && room < 430, trim2 = !lite && room < 345;

        // 颈后扣环
        double kX = (clX[0][0] + clX[1][0]) * 0.5 - fX * 0.06F * W, kY = (clY[0][0] + clY[1][0]) * 0.5 - 0.035F * H,
                kZ = (clZ[0][0] + clZ[1][0]) * 0.5 - fZ * 0.06F * W;
        float KS = 0.48F * W * (1 + 0.15F * swE + 0.08F * bt);
        if (!fp) {
            pen.s(SOFT).rgb(C).a((0.22F + 0.25F * swE) * A).billboard(kX, kY, kZ, KS * 2.2F, KS * 2.2F);
            if (!lite) {
                pen.s(SOFT).alphaBlend().rgb(deep).a(0.55F);
                FxKit.plate(pen, kX, kY, kZ, rX, 0, rZ, 0, 1, 0, KS * 1.15F, 0);
            }
            pen.s(KNOT).rgb(ek == ICE ? WHITE : pale).a(0.9F * A);
            FxKit.plate(pen, kX, kY, kZ, rX, 0, rZ, 0, 1, 0, KS, -T * 0.6F);
            pen.s(SOFTCORE).rgb(hot).a(0.8F * A).billboard(kX, kY, kZ, KS * 0.45F, KS * 0.45F);
        }

        sites.clear();
        for (int side = 0; side < 2; side++) {
            int sg = side == 0 ? -1 : 1;
            double[] cx = clX[side], cy = clY[side], cz = clZ[side];
            double tipX = cx[N - 1], tipY = cy[N - 1], tipZ = cz[N - 1];
            double tdx = tipX - cx[N - 2], tdy = tipY - cy[N - 2], tdz = tipZ - cz[N - 2], tl = Math.sqrt(tdx * tdx + tdy * tdy + tdz * tdz);
            if (tl < 1.0E-9) {
                tdx = fX;
                tdz = fZ;
                tl = 1;
            }
            float tdX = (float) (tdx / tl), tdY = (float) (tdy / tl), tdZ = (float) (tdz / tl);
            float flash = 0;

            if (!fp) {
                // 暗托 + 体积柔光：正对镜头，侧面看也是一整条粗光
                rib.clear();
                for (int i = 0; i < N; i++) {
                    rib.add(cx[i], cy[i], cz[i], ropeW(rr, i, N) * 1.75F, 1);
                }
                pen.s(SOFT).alphaBlend().rgb(deep).a(0.45F).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                if ((!lite && !trim1) || swE > 0.05F) {
                    rib.clear();
                    for (int i = 0; i < N; i++) {
                        rib.add(cx[i], cy[i], cz[i], rr * (4 + 3 * (float) Math.sin(Math.PI * i / (N - 1))), 1);
                    }
                    // 精简档只在挥砍时画，算进拖尾，超了先砍它
                    if (lite) {
                        pen.cat = EphemeraStaging.TRAIL;
                    }
                    pen.s(SOFT).rgb(C).a((0.17F + 0.07F * breath * amp + 0.22F * swE + 0.1F * run) * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    pen.cat = EphemeraStaging.BASE;
                }

                // 绳身：绞纹贴图分两截贴，一截一套股纹
                int h = (N - 1) / 2;
                for (int half = 0; half < 2; half++) {
                    int i0 = half == 0 ? 0 : h, i1 = half == 0 ? h : N - 1;
                    rib.clear();
                    for (int i = i0; i <= i1; i++) {
                        rib.add(cx[i], cy[i], cz[i], ropeW(rr, i, N), 1);
                    }
                    pen.s(ROPE).alphaBlend().rgb(bodyCol).a(0.95F).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    if (!lite && !heavy) {
                        pen.s(ROPE).rgb(hot).a((0.2F + 0.3F * swE) * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    }
                }

                // 沿绳流动的能量（从扣环流向绳头）
                float fu = ((-ph * 0.55F) % 0.5F + 0.5F) % 0.5F;
                rib.clear();
                for (int i = 0; i < N; i++) {
                    rib.add(cx[i], cy[i], cz[i], ropeW(rr, i, N) * 1.7F, i == 0 ? 0.4F : 1);
                }
                pen.s(FLOW_1).rgb(ek == FIRE ? hot : C).a(0.55F * A * (1 + 0.8F * swE)).ribbon(rib, EphemeraPen.FACE_CAM, fu, fu + 0.5F);

                // 缠在绳外的两道光丝，螺旋往绳头推进；背着镜头的一半压暗
                int NH = lite || trim2 ? 1 : 2;
                for (int k = 0; k < NH; k++) {
                    int dk = ek == MAGNETIC && k == 1 ? -1 : 1;
                    rib.clear();
                    for (int i = 0; i < NHP; i++) {
                        float f = (float) i / (NHP - 1);
                        frame3(side, f);
                        ctr(side, f);
                        float a0 = k * (float) Math.PI + dk * (f * TAU * 2.4F * sg - ph * 5 * sg);
                        float r = rr * (1.2F + 0.7F * hurtE + 0.35F * bt + 0.12F * swE);
                        if (ek == ELECTRIC) {
                            r *= 0.75F + 0.6F * hash(qe * 13 + i * 7 + k * 3 + sg * 5);
                        } else if (ek == POISON) {
                            r *= 1 + 0.25F * (float) Math.sin(f * 9 - t * 1.5F);
                        }
                        float ca = (float) Math.cos(a0) * r, sa = (float) Math.sin(a0) * r;
                        double offX = n1X * ca + n2X * sa, offY = n1Y * ca + n2Y * sa, offZ = n1Z * ca + n2Z * sa;
                        double px = ox + offX, py = oy + offY, pz = oz + offZ;
                        double vx = pen.camX() - ox, vy = pen.camY() - oy, vz = pen.camZ() - oz;
                        double ol = Math.sqrt(offX * offX + offY * offY + offZ * offZ) + 1.0E-9, vl = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-9;
                        float face = (float) ((offX * vx + offY * vy + offZ * vz) / (ol * vl));
                        float a = (0.25F + 0.75F * Math.max(0, face)) * (f > 0.95F ? 0.5F : 1);
                        if (ek == RADIATION && ((int) Math.floor(f * 12 + T * 4) & 1) == 0) {
                            a *= 0.12F;
                        }
                        rib.add(px, py, pz, rr * (ek == ICE ? 0.35F : 0.5F), a);
                    }
                    pen.s(SOFT).rgb(ek == ICE ? WHITE : hot).a(0.95F * A * (1 + 0.5F * swE)).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                }

                // 光脉：一颗颗亮珠从扣环沿绳跑到绳头，到了绳头一亮
                int NP = lite ? 1 : 3;
                for (int j = 0; j < NP; j++) {
                    float pf = (ph * 0.42F + (float) j / NP + (sg > 0 ? 0.17F : 0)) % 1;
                    float pk = (float) Math.sin(Math.PI * Math.min(1, pf * 1.15F));
                    ctr(side, pf);
                    pen.s(SOFT).rgb(C).a(0.55F * A * pk).billboard(ox, oy, oz, rr * 4.2F, rr * 4.2F);
                    pen.s(SOFTCORE).rgb(hot).a(0.95F * A * pk).billboard(ox, oy, oz, rr * 1.8F, rr * 1.8F);
                    if (!lite && !trim1) {
                        rib.clear();
                        for (int q = 0; q < 4; q++) {
                            ctr(side, Math.max(0, pf - q * 0.035F));
                            rib.add(ox, oy, oz, rr * 1.1F * (1 - q / 4.0F), 1 - q / 4.0F);
                        }
                        pen.rgb(hot).a(0.8F * A * pk).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    }
                    if (pf > 0.82F) {
                        flash = Math.max(flash, (float) Math.sin(Math.PI * (pf - 0.82F) / 0.18F));
                    }
                }

                // 绳头：收成一颗亮结
                float TS = rr * (1.8F + 1.0F * flash + 1.5F * swE * (sg > 0 ? 1 : 0.5F));
                pen.s(SOFT).rgb(C).a((0.25F + 0.4F * flash) * A).billboard(tipX, tipY, tipZ, TS * 2.2F, TS * 2.2F);
                pen.s(KNOT).rgb(pale).a(0.85F * A).billboard(tipX, tipY, tipZ, TS, TS, -T * 1.2F * sg);
                pen.s(SOFTCORE).rgb(hot).a(0.9F * A).billboard(tipX, tipY, tipZ, TS * 0.55F, TS * 0.55F);
                if (!lite && flash > 0.3F) {
                    pen.s(FLARE).rgb(hot).a(0.6F * flash * A).billboard(tipX, tipY, tipZ, TS * 2, TS * 2, sg * 0.4F);
                }

                // 元素：绳外沿一路长东西，绳头往前下方伸一根
                int NE = ek == MAGNETIC ? 0 : lite ? (heavy ? 0 : 2) : heavy ? (trim2 ? 1 : 2) : 5;
                for (int i = 0; i < NE; i++) {
                    float f = 0.18F + 0.66F * (i + 0.5F) / NE;
                    ctr(side, f);
                    radial(ox, oz);
                    float dx = rdX, dy = 0, dz = rdZ;
                    if (ek == FIRE) {
                        dy = 1;
                        dx *= 0.35F;
                        dz *= 0.35F;
                    } else if (ek == POISON) {
                        dy = -1;
                        dx *= 0.25F;
                        dz *= 0.25F;
                    } else if (ek == RADIATION) {
                        dy = 0.5F;
                    } else if (ek == ICE) {
                        dy = 0.2F;
                    }
                    float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= dl;
                    dy /= dl;
                    dz /= dl;
                    float L = 0.085F * H * (1 + 0.5F * swE + 0.3F * run) * (ek == FIRE ? 0.85F : 1);
                    fx.accent(ox + dx * rr * 0.8F, oy + dy * rr * 0.8F, oz + dz * rr * 0.8F, dx, dy, dz, L, 0.045F * H, sg * 37 + i, 0.85F * A);
                }
                {
                    float dx = tdX, dy = tdY - 0.4F, dz = tdZ, dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-6F;
                    fx.accent(tipX, tipY, tipZ, dx / dl, dy / dl, dz / dl, 0.15F * H * (1 + 0.6F * swE + 0.4F * flash), 0.05F * H,
                            sg > 0 ? 11 : 12, 0.95F * A);
                }

                if (ek == FIRE) {
                    // 火：拱顶一排大火舌，像绳子烧着了
                    for (int i = 0; i < (lite ? 1 : 2); i++) {
                        ctr(side, 0.33F + i * 0.14F);
                        float dx = -fX * 0.3F * run, dz = -fZ * 0.3F * run, dl = (float) Math.sqrt(dx * dx + 1 + dz * dz);
                        fx.accent(ox, oy + rr * 0.6F, oz, dx / dl, 1 / dl, dz / dl,
                                0.2F * H * (0.85F + 0.25F * (float) Math.sin(t * 5 + i * 2 + sg)) * (1 + 0.4F * swE), 0.07F * H, 70 + i + sg * 5, 0.85F * A);
                    }
                } else if (ek == ICE) {
                    // 冰：前半截下沿挂冰凌
                    for (int i = 0; i < (lite ? 1 : 3); i++) {
                        ctr(side, 0.62F + i * 0.12F);
                        fx.accent(ox, oy - rr * 0.7F, oz, 0, -1, 0, 0.1F * H, 0.04F * H, 90 + i + sg * 7, 0.9F * A);
                    }
                } else if (ek == MAGNETIC && !lite) {
                    // 磁：绳头往下兜过肩臂外侧再回到扣环的一道磁力线，和上面的绳环合成一个闭合的圈；光珠沿线流
                    int mid = N / 2;
                    radial((tipX + cx[mid]) * 0.5, (tipZ + cz[mid]) * 0.5);
                    float out = W * (0.55F + 0.08F * (float) Math.sin(t * 1.3F + sg));
                    rib.clear();
                    for (int i = 0; i <= 9; i++) {
                        float f = i / 9.0F, sn = (float) Math.sin(f * Math.PI);
                        mx[i] = tipX + (kX - tipX) * f + rdX * sn * out;
                        my[i] = tipY + (kY - tipY) * f - sn * 0.36F * H;
                        mz[i] = tipZ + (kZ - tipZ) * f + rdZ * sn * out;
                        rib.add(mx[i], my[i], mz[i], rr * 1.6F, 1);
                    }
                    pen.s(SOFT).rgb(pale).a(A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    for (int b = 0; b < 2; b++) {
                        float f = (t * 0.45F + b / 2.0F + (sg > 0 ? 0.15F : 0)) % 1;
                        int i = Math.min(8, (int) Math.floor(f * 9));
                        float g = f * 9 - i;
                        pen.s(SOFTCORE).rgb(hot).a(0.9F * A).billboard(mx[i] + (mx[i + 1] - mx[i]) * g, my[i] + (my[i + 1] - my[i]) * g,
                                mz[i] + (mz[i + 1] - mz[i]) * g, rr * 2.4F, rr * 2.4F);
                    }
                }

                ctr(side, 0.3F);
                sites.add(ox, oy, oz, 0, 1, 0);
                ctr(side, 0.5F);
                radial(ox, oz);
                sites.add(ox, oy, oz, rdX, 0, rdZ);
                ctr(side, 0.75F);
                radial(ox, oz);
                sites.add(ox, oy, oz, rdX, 0, rdZ);
                sites.add(tipX, tipY, tipZ, tdX, tdY, tdZ);
            }

            // 腰侧光焰：在腰侧偏后，第一人称低头也看得见，照画
            FxKit.Spring js = side == 0 ? s.jetL : s.jetR;
            js.step(m.x + rX * sg * 0.8F * W - fX * 0.22F * W, m.y + 0.5F * H, m.z + rZ * sg * 0.8F * W - fZ * 0.22F * W, m, 20, 0.7F);
            double jbX = js.x, jbY = js.y, jbZ = js.z;
            float air = m.onGround ? 0 : 1;
            float jdR, jdU, jdF;
            if (ek == FIRE) {
                jdR = sg * 0.95F;
                jdU = 0.55F;
                jdF = -0.3F - 0.9F * run;
            } else if (ek == POISON) {
                jdR = sg * 0.3F;
                jdU = -1;
                jdF = -0.15F - 0.6F * run;
            } else {
                jdR = sg * 0.95F;
                jdU = -0.6F - 0.4F * air;
                jdF = -0.4F - 0.9F * run;
            }
            float jdx = rX * jdR + fX * jdF, jdy = jdU, jdz = rZ * jdR + fZ * jdF;
            float jl = (float) Math.sqrt(jdx * jdx + jdy * jdy + jdz * jdz);
            jdx /= jl;
            jdy /= jl;
            jdz /= jl;
            float JL = 0.36F * H * sneak * (1 + 0.25F * act + 0.4F * (sg > 0 ? swE : 0.3F * swE) + 0.3F * jumpE + 0.25F * landE);
            // jd × fwd
            float sdx = jdy * fZ, sdy = jdz * fX - jdx * fZ, sdz = -jdy * fX;
            float sl = (float) Math.sqrt(sdx * sdx + sdy * sdy + sdz * sdz);
            if (sl < 1.0E-6F) {
                sdx = rX;
                sdy = 0;
                sdz = rZ;
            } else {
                sdx /= sl;
                sdy /= sl;
                sdz /= sl;
            }
            pen.s(SOFT).rgb(C).a((0.3F + 0.2F * swE) * A).billboard(jbX, jbY, jbZ, 0.22F * H, 0.22F * H);
            pen.s(SOFTCORE).rgb(hot).a(0.8F * A).billboard(jbX, jbY, jbZ, 0.07F * H, 0.07F * H);
            if (ek == ICE) {
                // 冰：一扇硬边冰晶，不摆动
                for (int j = 0; j < (lite ? 2 : 4); j++) {
                    float ang = (j - 1.5F) * 0.38F, cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                    float dx = jdx * cs + sdx * sn, dy = jdy * cs + sdy * sn, dz = jdz * cs + sdz * sn;
                    float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= dl;
                    dy /= dl;
                    dz /= dl;
                    float L = JL * (0.55F + 0.35F * rnd01(j * 3 + sg * 11)) * (1 + 0.25F * swE);
                    double tpX = jbX + dx * L, tpY = jbY + dy * L, tpZ = jbZ + dz * L;
                    double mdX = jbX + dx * L * 0.32F, mdY = jbY + dy * L * 0.32F, mdZ = jbZ + dz * L * 0.32F;
                    sideOf(dx, dy, dz);
                    float wq = 0.05F * H;
                    shard(tpX, tpY, tpZ, mdX, mdY, mdZ, jbX, jbY, jbZ, wq * 1.2F);
                    pen.alphaBlend().rgb(deep).a(0.5F);
                    emitShard();
                    pen.rgb(pale).a(0.8F * A).va(1, 0.55F, 0.3F, 0.55F);
                    shard(tpX, tpY, tpZ, mdX, mdY, mdZ, jbX, jbY, jbZ, wq);
                    emitShard();
                    pen.rgb(WHITE).a(0.9F * A);
                    shard(tpX, tpY, tpZ, mdX, mdY, mdZ, jbX, jbY, jbZ, wq * 0.22F);
                    emitShard();
                    sites.add(tpX, tpY, tpZ, dx, dy, dz);
                }
            } else {
                int NJ = lite ? 1 : 3;
                for (int j = 0; j < NJ; j++) {
                    float spread = (j - (NJ - 1) / 2.0F) * 0.55F;
                    for (int i = 0; i <= 5; i++) {
                        float f = i / 5.0F;
                        float wav = (float) Math.sin(T * 9 + f * 5 + j * 2) * 0.05F * H * f * (0.3F + sway);
                        if (ek == ELECTRIC) {
                            wav += (hash(qe * 5 + i * 3 + j * 17 + sg * 29) - 0.5F) * 0.09F * H * f;
                        }
                        float along = JL * f * (1 - 0.15F * Math.abs(spread)), across = spread * JL * f * 0.6F + wav;
                        jx[i] = jbX + jdx * along + sdx * across;
                        jy[i] = jbY + jdy * along + sdy * across - (ek == POISON ? 0.08F * H * f * f : 0);
                        jz[i] = jbZ + jdz * along + sdz * across;
                        jw[i] = 0.17F * W * (1.2F - f) + 0.025F * W;
                        ja[i] = 1 - f * 0.75F;
                    }
                    float u0 = (ph * 1.6F + j * 0.17F) % 0.5F;
                    rib.clear();
                    for (int i = 0; i <= 5; i++) {
                        rib.add(jx[i], jy[i], jz[i], jw[i] * 2.1F, ja[i]);
                    }
                    pen.s(FLOW_0).rgb(C).a(0.85F * A).ribbon(rib, EphemeraPen.FACE_CAM, u0, u0 + 0.5F);
                    rib.clear();
                    for (int i = 0; i < 4; i++) {
                        rib.add(jx[i], jy[i], jz[i], jw[i] * 0.5F, ja[i]);
                    }
                    pen.rgb(hot).a(0.55F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    if (ek == POISON) {
                        pen.s(DROP).rgb(pale).a(0.85F * A).billboard(jx[5], jy[5], jz[5], 0.05F * H, 0.065F * H);
                    } else if (ek == FIRE && !lite) {
                        fx.accent(jx[3], jy[3], jz[3], jdx, jdy, jdz, 0.12F * H, 0.05F * H, 120 + j + sg * 3, 0.7F * A);
                    }
                    if (j == NJ / 2) {
                        sites.add(jx[2], jy[2], jz[2], jdx, jdy, jdz);
                        sites.add(jx[5], jy[5], jz[5], jdx, jdy, jdz);
                    }
                }
            }

            // 跑、跳、挥砍时绳头拖出光迹
            FxTrail tr = s.tr[side];
            tr.push(tipX, tipY, tipZ, t);
            float tk = Math.max(Math.max(run * 0.7F, (sg > 0 ? 1.2F : 0.6F) * swE), m.onGround ? 0 : 0.5F);
            if (tk > 0.02F && !fp) {
                pen.cat = EphemeraStaging.TRAIL;
                final float fa = A, frr = rr;
                if (!lite && !heavy) {
                    tr.sample(8, sample);
                    pen.s(SOFT).rgb(C);
                    FxTrail.draw(pen, sample, rib2, (q, i) -> frr * 3.2F * (1 - q), (q, i) -> 0.4F * tk * (1 - q) * fa, null,
                            m.trailW, m.trailA, EphemeraPen.FACE_CAM, 0, 1);
                }
                tr.sample(lite ? 4 : 8, sample);
                pen.rgb(hot);
                FxTrail.draw(pen, sample, rib2, (q, i) -> frr * 0.8F * (1 - q * 0.7F), (q, i) -> 0.8F * tk * (1 - q) * fa, null,
                        m.trailW, m.trailA, EphemeraPen.FACE_CAM, 0, 1);
                pen.cat = EphemeraStaging.BASE;
            }
        }

        if (!fp) {
            // 两边之间的元素招牌
            if (ek == ELECTRIC) {
                // 电：左右两段绳之间、绳到肩之间来回跳弧
                int q = (int) Math.floor(t / 0.06F), NA = lite ? 1 : 2 + (swE > 0.2F ? 1 : 0);
                for (int i = 0; i < NA; i++) {
                    if (hash(q * 3 + i * 11) < 0.2F) {
                        continue;
                    }
                    int kind = (int) Math.floor(hash(q * 7 + i * 5) * 3);
                    double p0x, p0y, p0z, p1x, p1y, p1z;
                    if (kind == 0) {
                        float f = 0.08F + 0.2F * hash(q + i);
                        ctr(0, f);
                        p0x = ox;
                        p0y = oy;
                        p0z = oz;
                        ctr(1, f);
                        p1x = ox;
                        p1y = oy;
                        p1z = oz;
                    } else if (kind == 1) {
                        p0x = clX[0][N - 1];
                        p0y = clY[0][N - 1];
                        p0z = clZ[0][N - 1];
                        p1x = clX[1][N - 1];
                        p1y = clY[1][N - 1];
                        p1z = clZ[1][N - 1];
                    } else {
                        int side = hash(q * 9 + i) < 0.5F ? 0 : 1;
                        ctr(side, 0.35F + 0.4F * hash(q * 13 + i));
                        p0x = ox;
                        p0y = oy;
                        p0z = oz;
                        BodyPoints.Pt sh = side == 0 ? m.pts.shoulderL : m.pts.shoulderR;
                        p1x = sh.x;
                        p1y = sh.y;
                        p1z = sh.z;
                    }
                    double dx = p1x - p0x, dy = p1y - p0y, dz = p1z - p0z, dl = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-9;
                    sideOf(dx / dl, dy / dl, dz / dl);
                    zig(p0x, p0y, p0z, p1x, p1y, p1z, 7, 0.06F * H, q * 13 + i);
                    rib.clear();
                    for (int k = 0; k <= 7; k++) {
                        rib.add(zx[k], zy[k], zz[k], rr * 1.6F, 1);
                    }
                    pen.s(SOFT).rgb(C).a(0.5F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    rib.clear();
                    for (int k = 0; k <= 7; k++) {
                        rib.add(zx[k], zy[k], zz[k], rr * 0.3F, 1);
                    }
                    pen.rgb(hot).a(A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(FLARE).rgb(hot).a(0.8F * A).billboard(p1x, p1y, p1z, rr * 3, rr * 3, q);
                }
            } else if (ek == POISON && !lite) {
                // 毒：两个绳头之间垂一道黏丝，往下滴
                float sag = 0.1F * H * (1 + 0.2F * (float) Math.sin(t * 1.4F)) * (1 - 0.5F * run);
                double ax = clX[0][N - 1], ay = clY[0][N - 1], az = clZ[0][N - 1], bx = clX[1][N - 1], by = clY[1][N - 1], bz = clZ[1][N - 1];
                rib.clear();
                rib2.clear();
                for (int i = 0; i <= 8; i++) {
                    float f = i / 8.0F, sn = (float) Math.sin(Math.PI * f), w = rr * (0.5F + 0.5F * sn);
                    double px = ax + (bx - ax) * f, py = ay + (by - ay) * f - sag * sn, pz = az + (bz - az) * f;
                    rib.add(px, py, pz, w * 1.6F, 1);
                    rib2.add(px, py, pz, w, 1);
                }
                pen.s(SOFT).alphaBlend().rgb(deep).a(0.5F).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                pen.s(SOFT).rgb(C).a(0.8F * A).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
                float dp = (t * 0.7F) % 1;
                pen.s(DROP).rgb(pale).a(0.9F * A * (1 - dp)).billboard(rib2.x[4], rib2.y[4] - 0.03F * H - 0.3F * H * dp * dp, rib2.z[4],
                        0.045F * H, 0.06F * H);
            } else if (ek == IMPACT) {
                // 冲击：每一拍从颈肩往外推一道水平冲击圈
                float f = 1 - bt;
                pen.s(SHOCK).rgb(pale).a(0.6F * bt * A);
                FxKit.plate(pen, neckX, neckY, neckZ, rX, 0, rZ, fX, 0, fZ, W * 2.6F * (1 + 0.7F * f), 0);
                if (!lite) {
                    pen.s(SHOCK).rgb(WHITE).a(0.4F * bt * A);
                    FxKit.plate(pen, neckX, neckY + 0.06F * H, neckZ, rX, 0, rZ, fX, 0, fZ, W * 2.2F * (1 + 0.35F * f), 0);
                }
            } else if (ek == RADIATION) {
                // 辐射：扣环后面三扇慢转的扇形光 + 六边形
                double rx = kX - fX * 0.02F * W, rz = kZ - fZ * 0.02F * W;
                for (int i = 0; i < 3; i++) {
                    float a = i / 3.0F * TAU + T * 0.4F;
                    pen.s(SOFT).rgb(C).a(0.5F * A * bl);
                    FxKit.ring(pen, rx, kY, rz, rX, 0, rZ, 0, 1, 0, KS * 0.62F, KS * 0.55F, lite ? 2 : 3, a, a + 0.8F, 0);
                }
                pen.s(HEX_0).rgb(hot).a(0.8F * A * bl).billboard(kX, kY, kZ, KS * 0.7F, KS * 0.7F, T * 0.3F);
            } else if (ek == ICE && !lite) {
                // 冰：扣环外一圈六瓣霜花
                double lx = kX - fX * 0.02F * W, lz = kZ - fZ * 0.02F * W;
                for (int i = 0; i < 6; i++) {
                    float a = i / 6.0F * TAU + T * 0.15F, c = (float) Math.cos(a), sn = (float) Math.sin(a);
                    double drX = rX * c, drY = sn, drZ = rZ * c, sdX2 = -rX * sn, sdY2 = c, sdZ2 = -rZ * sn;
                    pen.rgb(WHITE).a(0.75F * A).beam(lx + drX * KS * 0.45F, kY + drY * KS * 0.45F, lz + drZ * KS * 0.45F,
                            lx + drX * KS * 0.95F, kY + drY * KS * 0.95F, lz + drZ * KS * 0.95F, KS * 0.06F);
                    pen.rgb(pale).a(0.7F * A).beam(lx + drX * KS * 0.7F, kY + drY * KS * 0.7F, lz + drZ * KS * 0.7F,
                            lx + drX * KS * 0.85F + sdX2 * KS * 0.12F, kY + drY * KS * 0.85F + sdY2 * KS * 0.12F,
                            lz + drZ * KS * 0.85F + sdZ2 * KS * 0.12F, KS * 0.04F);
                }
            } else if (ek == FIRE && !lite) {
                // 火：扣环烧成一团，往上冒火舌
                for (int i = 0; i < 3; i++) {
                    fx.accent(kX + rX * (i - 1) * KS * 0.3F, kY + KS * 0.2F, kZ + rZ * (i - 1) * KS * 0.3F, 0, 1, 0,
                            KS * (0.9F + 0.3F * (float) Math.sin(t * 4 + i)), KS * 0.35F, 140 + i, 0.75F * A);
                }
            }

            // 挥砍：右边绳子甩出一道绳形光弧，算作残影
            float ssw = m.since(m.tSwing) / (0.38F * m.burstTime);
            if (ssw >= 0 && ssw < 1) {
                pen.cat = EphemeraStaging.TRAIL;
                float e = 1 - (float) Math.pow(1 - ssw, 2.2), fade = 1 - ssw;
                double caX = bX + fX * 0.1F * W, caY = bY + 0.6F * H, caZ = bZ + fZ * 0.1F * W;
                float RA = W * (1.05F + 0.55F * e) * (0.8F + 0.2F * Bk);
                float head = -0.4F + ((float) Math.PI + 0.6F) * e, tail = head - 1.9F;
                int NA = lite ? 6 : 10;
                rib.clear();
                for (int i = 0; i <= NA; i++) {
                    float f = (float) i / NA, a = tail + (head - tail) * f, c = (float) Math.cos(a), sn = (float) Math.sin(a);
                    rib.add(caX + rX * c * RA + fX * sn * RA, caY + c * 0.22F * H, caZ + rZ * c * RA + fZ * sn * RA,
                            rr * (0.6F + 2.2F * (float) Math.sin(Math.PI * Math.pow(f, 0.7))), f);
                }
                if (!lite) {
                    rib2.scaled(rib, 2.6F, 1);
                    pen.s(SOFT).rgb(C).a(0.55F * fade * A).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
                }
                pen.s(ROPE).rgb(hot).a(0.95F * fade * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                pen.s(FLARE).rgb(hot).a(0.8F * fade * A).billboard(rib.x[NA], rib.y[NA], rib.z[NA], rr * 5, rr * 5, t);
                pen.cat = EphemeraStaging.BASE;
            }
            float ring = Math.max(swE * Bk, landE);
            if (ring > 0.02F) {
                float sl = m.since(m.tLand), ss = m.since(m.tSwing);
                float ff = (sl < ss ? sl : ss) / 0.4F;
                pen.s(SOFTRING).rgb(hot).a(0.7F * ring * A);
                FxKit.plate(pen, neckX, neckY - 0.04F * H, neckZ, rX, 0, rZ, fX, 0, fZ, W * (2.4F + 2 * Math.min(1, ff)), 0);
            }

            // 沿绳溢出的能量火星：站着很少，跑、挥砍、受击时往后飘一串
            s.acc += dt * (3 + 14 * run + 32 * swE + 30 * hurtE) * (lite ? 0.4F : 1);
            while (s.acc >= 1) {
                s.acc -= 1;
                int side = rnd() < 0.5F ? 0 : 1;
                float f = 0.1F + 0.9F * rnd();
                frame3(side, f);
                ctr(side, f);
                radial(ox, oz);
                float out = (0.3F + 1.2F * hurtE) * sc, back = -1.3F * run * sc;
                P.spec().pos(ox + rdX * rr * 1.2F, oy, oz + rdZ * rr * 1.2F)
                        .vel(tgX * 0.5F * sc + rdX * out + fX * back, tgY * 0.5F * sc + 0.2F * sc, tgZ * 0.5F * sc + rdZ * out + fZ * back)
                        .life(rnd(0.4F, 0.8F)).size(rnd(0.03F, 0.055F) * sc, 0.01F * sc).sprite(SOFTCORE).rgb(rnd() < 0.5F ? hot : C).a(0.95F)
                        .drag(1.8F).stretch(0.04F).spawn();
            }
        }
        P.step(dt, null);
        if (!fp) {
            pen.cat = EphemeraStaging.PARTICLE;
            P.draw(pen, m.size, 1, Float.NaN);
            pen.cat = EphemeraStaging.BASE;
        }
        fx.motes(sites, 11 * (0.5F + 0.5F * act) * (1 + 0.6F * run), sc);
    }

    private static float ropeW(float rr, int i, int n) {
        return rr * 2 * (0.85F + 0.2F * (float) Math.sin(Math.PI * i / (n - 1)));
    }

    // 冰晶片：尖 tp、腰 md 两侧、根 jb，侧向取 sideOf 的结果
    private final double[] sq = new double[12];

    private void shard(double tpX, double tpY, double tpZ, double mdX, double mdY, double mdZ, double jbX, double jbY, double jbZ, float w) {
        sq[0] = tpX;
        sq[1] = tpY;
        sq[2] = tpZ;
        sq[3] = mdX + sdX * w;
        sq[4] = mdY + sdY * w;
        sq[5] = mdZ + sdZ * w;
        sq[6] = jbX;
        sq[7] = jbY;
        sq[8] = jbZ;
        sq[9] = mdX - sdX * w;
        sq[10] = mdY - sdY * w;
        sq[11] = mdZ - sdZ * w;
    }

    private void emitShard() {
        pen.quad(sq[0], sq[1], sq[2], sq[3], sq[4], sq[5], sq[6], sq[7], sq[8], sq[9], sq[10], sq[11]);
    }

    /** a 到 b 的折线，n 段 n+1 个点，中间的点沿 sd 随机偏 amp 以内，写进 zx / zy / zz */
    private void zig(double ax, double ay, double az, double bx, double by, double bz, int n, float amp, int seed) {
        for (int i = 0; i <= n; i++) {
            float f = (float) i / n;
            float j = i == 0 || i == n ? 0 : (hash(seed * 31 + i * 7) - 0.5F) * 2 * amp;
            zx[i] = ax + (bx - ax) * f + sdX * j;
            zy[i] = ay + (by - ay) * f + sdY * j;
            zz[i] = az + (bz - az) * f + sdZ * j;
        }
    }
}
