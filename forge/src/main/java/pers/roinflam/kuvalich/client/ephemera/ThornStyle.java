package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 棘链：几枚带刺的金属雷球在身体四周不同高度漂浮，球与球之间以光线相连，织成一张缀满光点的网；
 * 每隔几秒聚拢到胸前蓄能再弹开，光网随之往外扩一圈。跑动时雷球拖在身后，落地往下往外一沉，
 * 挥砍时依次朝挥砍方向射出再收回，受击时连线崩断、雷球四散。
 */
public final class ThornStyle extends ProtoStyle<ThornStyle.St> {

    static final int MINE = EphemeraSprites.file("thorn/mine");
    static final int SEAM = EphemeraSprites.file("thorn/seam");
    static final int MESH = EphemeraSprites.file("thorn/mesh");
    static final int HALO = EphemeraSprites.file("thorn/halo");

    private static final int NB = 7;
    /** 聚散一轮的秒数 */
    private static final float PER = 6.5F;
    // 方位角（度，从右往前量）、高度（身高倍数）、离身半径（身宽倍数）。头那么高的几颗放在两侧，正面背面都不挡脸；前五颗是精简档
    private static final float[][] LAY = {{10, 0.72F, 1.8F}, {170, 0.55F, 1.85F}, {-60, 1.2F, 1.9F}, {-150, 0.95F, 1.7F},
            {70, 0.3F, 1.9F}, {120, 1.12F, 1.95F}, {-95, 0.42F, 1.6F}};
    // 按方位角排的一圈，连线和网面都沿这个顺序
    private static final int[] ORDER7 = {4, 5, 1, 3, 6, 2, 0}, ORDER5 = {4, 1, 3, 2, 0};
    // 每三颗张一片网；张角超过一百二十度的那片会横在身前，不画
    private static final int[][] TRIS7 = {{0, 1, 2}, {2, 3, 4}, {4, 5, 6}}, TRIS5 = {{0, 1, 2}, {2, 3, 4}};
    /** 立方体八个角当刺的方向：交错的前四个（正四面体）是能量刺、挂元素凸起，后四个是短的金属刺 */
    private static final float[][] DIRS = new float[8][3];
    /** 各球的自转轴，和赤道面上的两根轴（环贴在赤道面上） */
    private static final float[][] AXIS = new float[NB][3], E1 = new float[NB][3], E2 = new float[NB][3];

    static {
        int[][] c = {{1, 1, 1}, {-1, -1, 1}, {-1, 1, -1}, {1, -1, -1}, {-1, -1, -1}, {1, 1, -1}, {1, -1, 1}, {-1, 1, 1}};
        float s3 = (float) (1 / Math.sqrt(3));
        for (int j = 0; j < 8; j++) {
            for (int k = 0; k < 3; k++) {
                DIRS[j][k] = c[j][k] * s3;
            }
        }
        for (int i = 0; i < NB; i++) {
            float ax = hash(i * 3 + 1) - 0.5F, ay = hash(i * 3 + 2) - 0.5F + 0.9F, az = hash(i * 3 + 3) - 0.5F;
            float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            ax /= l;
            ay /= l;
            az /= l;
            AXIS[i][0] = ax;
            AXIS[i][1] = ay;
            AXIS[i][2] = az;
            // e1 = axis × (0.3, 0, 1)，e2 = axis × e1
            float ex = ay, ey = az * 0.3F - ax, ez = -ay * 0.3F;
            l = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
            ex /= l;
            ey /= l;
            ez /= l;
            E1[i][0] = ex;
            E1[i][1] = ey;
            E1[i][2] = ez;
            E2[i][0] = ay * ez - az * ey;
            E2[i][1] = az * ex - ax * ez;
            E2[i][2] = ax * ey - ay * ex;
        }
    }

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        final FxKit.Spring[] sp = new FxKit.Spring[NB];
        final float[] spin = new float[NB];
        FxParticles P, S;
        boolean lite;
        int el = -1;
        float acc, air, lph;
        int seenSwing, seenHurt;

        St() {
            for (int i = 0; i < NB; i++) {
                sp[i] = new FxKit.Spring();
            }
        }
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(8);
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final float[] dir = new float[3], v3 = new float[3];
    private final double[] side = new double[3];

    // 这一帧各球：p 是画的位置（含挥砍射出），o 是射出前的位置
    private final double[] px = new double[NB], py = new double[NB], pz = new double[NB];
    private final double[] ox = new double[NB], oy = new double[NB], oz = new double[NB];
    private final float[] dX = new float[NB], dZ = new float[NB], bE = new float[NB], bShot = new float[NB];
    private final float[] bvx = new float[NB], bvy = new float[NB], bvz = new float[NB], bSz = new float[NB];
    private final float[] edX = new float[4], edY = new float[4], edZ = new float[4];

    // 一根连线的点和崩断时的半截
    private final double[] lx = new double[6], ly = new double[6], lz = new double[6];
    private final double[] hx = new double[2], hy = new double[2], hz = new double[2];
    private final double[] spX = new double[6], spY = new double[6], spZ = new double[6];
    private int sparkN;

    // 这一帧的量
    private EphemeraStaging out;
    private int start, cap;
    private boolean lite;
    private int el;
    private boolean heavy, simple;
    private float Rb, g, swE, hk, linkW, hotW;
    private float t, T, H, W, act, bt, bl, hurtE, hurtS, landE, breath, A, charge, rel, swX, swY, swZ;
    private double cgx, cgy, cgz;
    private int N, q5;
    private int[] order;
    private int[][] tris;
    private int C, hot, pale;

    @Override
    public float extent() {
        return 3.2F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.fx.reset();
        for (FxKit.Spring k : s.sp) {
            k.reset();
        }
        s.P = null;
    }

    private void init(St s, FxMotion m, boolean heavy) {
        s.lite = m.lite;
        s.el = m.el;
        s.P = new FxParticles(m.lite ? 20 : heavy ? 70 : 100);
        s.S = new FxParticles(m.lite ? 12 : 36);
        s.acc = 0;
        s.air = 0;
        s.lph = 1;
        java.util.Arrays.fill(s.spin, 0);
        s.seenSwing = m.nSwing;
        s.seenHurt = m.nHurt;
    }

    private int room() {
        return cap - (out.pending() - start);
    }

    private int baseLeft() {
        return EphemeraTier.CAP_REDUCED - out.pending(EphemeraStaging.BASE);
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        lite = m.lite;
        el = m.el;
        heavy = el == ELECTRIC || el == MAGNETIC || el == POISON;
        boolean fp = d.firstPersonSelf;
        if (s.P == null || s.lite != lite || s.el != el) {
            init(s, m, heavy);
        }
        out = d.out;
        start = out.pending();
        cap = lite ? EphemeraTier.CAP_REDUCED : EphemeraTier.CAP_FULL;
        float dt = m.dt, sc = m.sc, amp = m.amp, Bk = m.burst;
        t = (float) m.t;
        H = m.H;
        W = m.W;
        act = m.act;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        C = fx.C;
        hot = fx.hot;
        pale = fx.pale;
        T = m.clock();
        bt = m.beat(0);
        bl = m.blink(10);
        float run = clamp(m.speed / 5.6F, 0, 1);
        float swS = m.since(m.tSwing);
        hurtS = m.since(m.tHurt);
        swE = m.env(swS, 0.03F, 0.08F, 0.45F);
        hurtE = m.env(hurtS, 0.02F, 0.1F, 0.55F);
        landE = m.env(m.since(m.tLand), 0.01F, 0.06F, 0.4F) * clamp((m.landSpeed > 0 ? m.landSpeed : 8) / 8, 0.6F, 1.4F);
        breath = 0.5F + 0.5F * (float) Math.sin(m.t * 1.6);
        N = lite ? 5 : NB;
        order = lite ? ORDER5 : ORDER7;
        tris = lite ? TRIS5 : TRIS7;
        Rb = 0.09F * H;
        s.air += ((m.onGround ? 0 : 1) - s.air) * Math.min(1, dt * 8);
        float air = s.air;
        float rX = m.rightX, rZ = m.rightZ, fX = m.fwdX, fZ = m.fwdZ;

        // ---- 聚散：大半时间散着漂，聚到胸前、蓄一下、弹开 ----
        double tc = m.t * (el == ICE ? 0.75 : el == POISON ? 0.85 : 1);
        float ph = (float) ((tc / PER + 0.3) % 1.0);
        charge = 0;
        if (ph < 0.58F) {
            g = 0;
        } else if (ph < 0.7F) {
            g = smooth((ph - 0.58F) / 0.12F);
        } else if (ph < 0.8F) {
            g = 1;
            charge = (ph - 0.7F) / 0.1F;
        } else {
            g = 1 - smooth((ph - 0.8F) / 0.04F);
        }
        // 收敛档站着时聚得浅一点
        float ak = 0.6F + 0.4F * amp;
        g *= ak;
        charge *= ak;
        rel = ph >= 0.8F ? (float) Math.exp(-(ph - 0.8F) * PER * 3.2F) * ak : 0;
        boolean released = s.lph < 0.8F && ph >= 0.8F;
        s.lph = ph;

        // 近白的颜色叠起来一片白，柔光按亮度压
        float lum = (0.3F * (C >> 16 & 255) + 0.59F * (C >> 8 & 255) + 0.11F * (C & 255)) / 255;
        hk = Math.min(1, 0.62F / Math.max(0.3F, lum));
        A = (0.62F + 0.38F * act + 0.06F * breath) * bl;
        if (hurtE > 0.05F) {
            A *= 0.7F + 0.3F * hash((int) Math.floor(m.t * 22));
        }

        cgx = m.x;
        cgy = m.y + H * 0.52F;
        cgz = m.z;
        float sg = (m.swingCount & 1) != 0 ? 1 : -1;
        norm(fX + rX * sg * 0.5F, 0.1F, fZ + rZ * sg * 0.5F);
        swX = v3[0];
        swY = v3[1];
        swZ = v3[2];
        q5 = (int) Math.floor(m.t / 0.05);

        // ---- 球的位置：各自一根弹簧追站位，跑起来拖在后面 ----
        for (int i = 0; i < N; i++) {
            float[] L = LAY[i];
            float hs = hash(i * 13 + 5);
            float th = (float) Math.toRadians(L[0]) + 0.4F * (noise(T * 0.13F, 40 + i) - 0.5F);
            float cs = (float) Math.cos(th), sn = (float) Math.sin(th);
            float dx = rX * cs + fX * sn, dz = rZ * cs + fZ * sn;
            float r = W * L[2] * (1 + 0.1F * (noise(T * 0.22F, 60 + i) - 0.5F)) * (1 - 0.18F * air) * (1 + 0.4F * landE) * (1 + 0.12F * bt);
            float h = H * L[1] + 0.045F * H * (float) Math.sin(T * 0.9F + i * 1.7F) + 0.06F * H * air - 0.13F * H * landE;
            // 聚到腰胸一圈、不过肩，正面看不挡脸
            r += (W * 1.25F - r) * g;
            h += (H * (0.52F + (L[1] - 0.65F) * 0.3F) - h) * g;
            double tx = m.x + dx * r - fX * run * W * 0.3F, ty = m.y + h, tz = m.z + dz * r - fZ * run * W * 0.3F;
            float w0 = (8 + 3 * hs) * (1 + 0.6F * g), zeta = 0.42F;
            // 弹簧阻尼按相对宿主算，匀速跑时本来不落后；目标往后挪 v·2ζ/ω，留住按绝对速度阻尼时那一截拖尾
            float lag = 2 * zeta / w0;
            FxKit.Spring k = s.sp[i];
            k.step(tx - m.velX * lag, ty - m.velY * lag, tz - m.velZ * lag, m, w0, zeta);
            double x = k.x, y = k.y, z = k.z;
            if (hurtE > 0.01F) {
                int qh = (int) Math.floor(m.t * 25);
                float j = 0.12F * H * hurtE, o = W * 0.5F * hurtE;
                x += dx * o + (hash(qh + i * 3) - 0.5F) * j;
                y += (hash(qh + i * 3 + 1) - 0.5F) * j;
                z += dz * o + (hash(qh + i * 3 + 2) - 0.5F) * j;
            }
            ox[i] = x;
            oy[i] = y;
            oz[i] = z;
            // 挥砍：不过弹簧，按顺序朝挥砍方向射出去再收回，才够脆
            float e = m.env(swS - i * 0.03F, 0.06F, 0.05F, 0.3F);
            float shot = W * 3.2F * e * (0.6F + 0.4F * Bk);
            if (shot > 0.001F) {
                x += swX * shot;
                y += swY * shot + shot * 0.18F * (float) Math.sin(i * 2.1F);
                z += swZ * shot;
            }
            px[i] = x;
            py[i] = y;
            pz[i] = z;
            dX[i] = dx;
            dZ[i] = dz;
            bE[i] = e;
            bShot[i] = shot;
            bvx[i] = k.vx;
            bvy[i] = k.vy;
            bvz[i] = k.vz;
            bSz[i] = Rb * (1 + 0.25F * bt) * (1 + 0.15F * charge) * (1 + 0.12F * e);
            s.spin[i] += dt * (0.5F + 0.6F * hs) * (1 + 4 * charge + 4 * rel + 3 * e + 2 * hurtE) * (el == ICE ? 0.5F : 1);
        }

        // 完整档先画元素爆发，后面的粒子按剩下的让；精简档先保主体，爆发放最后
        fx.events(m.x, m.groundY, m.z, W * 1.6F);
        if (!lite) {
            pen.cat = EphemeraStaging.PARTICLE;
            fx.bursts();
        }

        sites.clear();
        sparkN = 0;
        pen.cat = EphemeraStaging.BASE;
        if (!fp) {
            // ---- 氛围：身边一层很淡的光，聚拢蓄能时亮起来 ----
            pen.s(SOFT).rgb(C).a((0.03F + 0.05F * g + 0.12F * rel + 0.05F * swE) * A * hk).billboard(cgx, cgy, cgz, H * 1.5F, H * 1.8F);
            if (g > 0.05F) {
                float w = H * 0.5F * (1 + 0.5F * charge);
                pen.s(SOFT).rgb(C).a((0.1F + 0.2F * charge) * g * A * hk).billboard(cgx, cgy, cgz, w, w);
                w = H * 0.16F * (1 + charge);
                pen.s(SOFTCORE).rgb(hot).a(0.5F * charge * A).billboard(cgx, cgy, cgz, w, w);
            }
            if (rel > 0.02F) {
                float R = H * (0.4F + 1.9F * (1 - rel));
                pen.s(el == IMPACT ? SHOCK : SOFTRING).rgb(C).a(0.55F * rel * A * hk).billboard(cgx, cgy, cgz, R, R);
                if (!lite) {
                    pen.s(SOFTRING).rgb(hot).a(0.3F * rel * A * hk).billboard(cgx, cgy, cgz, R * 0.7F, R * 0.7F);
                }
            }
            // 球身底下的暗托先画：半透明批里第一片是正对镜头的方片，大小只跟体型走
            for (int i = 0; i < N; i++) {
                float w = bSz[i] * 4.6F;
                pen.s(SOFT).alphaBlend().rgb(0).a(0.3F * A).billboard(px[i], py[i], pz[i], w, w);
            }

            if (lite) {
                balls(s, m, fx);
                ground(m, fx);
                web(m);
            } else {
                web(m);
                balls(s, m, fx);
            }
        }

        // ---- 光网粒子：沿连线不停冒、慢慢往外飘；弹开那一下整团往外扩 ----
        FxParticles P = s.P, S = s.S;
        P.thin = m.emit;
        S.thin = m.emit;
        if (!fp) {
            s.acc += dt * (lite ? 10 : heavy ? 26 : 36) * (0.45F + 0.5F * act + 1.2F * g + 2 * charge);
            while (s.acc >= 1) {
                s.acc -= 1;
                int j = Math.min(N - 1, (int) (rnd() * N));
                int a = order[j], b = order[(j + 1) % N];
                float f = rnd();
                EphemeraFx.randDir(dir);
                double qx = px[a] + (px[b] - px[a]) * f + dir[0] * 0.06F * H;
                double qy = py[a] + (py[b] - py[a]) * f + dir[1] * 0.06F * H;
                double qz = pz[a] + (pz[b] - pz[a]) * f + dir[2] * 0.06F * H;
                float ex = (float) (qx - cgx), ey = (float) (qy - cgy) * 0.3F, ez = (float) (qz - cgz);
                float el2 = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
                if (el2 > 1.0E-4F) {
                    ex /= el2;
                    ey /= el2;
                    ez /= el2;
                } else {
                    ex = fX;
                    ey = 0;
                    ez = fZ;
                }
                P.spec().pos(qx, qy, qz).vel((ex * 0.22F - ez * 0.18F) * sc, (ey * 0.22F + 0.05F) * sc, (ez * 0.22F + ex * 0.18F) * sc)
                        .life(rnd(1.1F, 1.9F)).size(H * 0.05F, H * 0.016F).sprite(rnd() < 0.5F ? DOT : SOFTCORE)
                        .rgb(rnd() < 0.3F ? hot : C).drag(0.5F).fade(0.2F, 0.5F).spawn();
            }
            if (released) {
                for (int j = 0; j < (lite ? 10 : 34); j++) {
                    EphemeraFx.randDir(dir);
                    dir[1] *= 0.6F;
                    float sp = rnd(2.2F, 3.4F) * sc;
                    P.spec().pos(cgx + dir[0] * 0.12F * H, cgy + dir[1] * 0.12F * H, cgz + dir[2] * 0.12F * H)
                            .vel(dir[0] * sp, dir[1] * sp, dir[2] * sp).life(rnd(0.8F, 1.3F)).size(H * 0.03F, H * 0.01F).sprite(DOT)
                            .rgb(rnd() < 0.4F ? hot : C).a(0.95F).drag(3).force().spawn();
                }
            }
        } else {
            s.acc = 0;
        }
        P.step(dt, null);

        // ---- 事件：挥砍从每颗球射出火花，受击从断线处崩火花 ----
        if (s.seenSwing != m.nSwing) {
            s.seenSwing = m.nSwing;
            if (swS < 0.2F && !fp) {
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < (lite ? 1 : 3); j++) {
                        EphemeraFx.randDir(dir);
                        float sp = rnd(4, 7);
                        S.spec().pos(ox[i], oy[i], oz[i])
                                .vel((swX * sp + dir[0] * 1.5F) * sc, (swY * sp + dir[1] * 1.5F) * sc, (swZ * sp + dir[2] * 1.5F) * sc)
                                .life(rnd(0.25F, 0.45F)).size(H * 0.03F, H * 0.01F).sprite(DOT).rgb(hot).drag(3).stretch(0.05F).spawn();
                    }
                }
            }
        }
        if (s.seenHurt != m.nHurt) {
            s.seenHurt = m.nHurt;
            if (!fp) {
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < (lite ? 1 : 3); j++) {
                        EphemeraFx.randDir(dir);
                        float sp = rnd(2, 4);
                        S.spec().pos(px[i], py[i], pz[i])
                                .vel((dX[i] * sp + dir[0] * 2) * sc, dir[1] * 2 * sc, (dZ[i] * sp + dir[2] * 2) * sc)
                                .life(rnd(0.3F, 0.5F)).size(H * 0.032F, H * 0.01F).sprite(DOT).rgb(hot).grav(4 * sc).drag(1.5F)
                                .stretch(0.04F).spawn();
                    }
                }
            }
        }
        for (int k = 0; k < sparkN; k++) {
            if (rnd() < 0.3F) {
                EphemeraFx.randDir(dir);
                float sp = 2.5F * sc;
                S.spec().pos(spX[k], spY[k], spZ[k]).vel(dir[0] * sp, dir[1] * sp, dir[2] * sp).life(0.2F).size(H * 0.025F, 0)
                        .sprite(DOT).rgb(hot).stretch(0.04F).spawn();
            }
        }
        S.step(dt, null);

        if (fp || !lite) {
            ground(m, fx);
        }
        fx.motes(sites, (5 + 8 * g + 8 * charge) * (0.5F + 0.5F * act), m.sc);
        if (lite) {
            pen.cat = EphemeraStaging.PARTICLE;
            fx.bursts();
        }
        if (!fp) {
            pen.cat = EphemeraStaging.PARTICLE;
            int left = drawPool(P, m.size, hk, room());
            drawPool(S, m.size, 1, left);
        }
    }

    /** 网面、连线、聚拢时的辐线 */
    private void web(FxMotion m) {
        // ---- 网面 ----
        float fa = (0.1F + 0.25F * act + 0.25F * g + 0.35F * charge + 0.6F * rel + 0.3F * swE) * A * hk
                * (hurtE > 0.05F ? hash(q5 * 3) : 1);
        for (int[] tr : tris) {
            int a = order[tr[0]], b = order[tr[1]], c = order[tr[2]];
            pen.s(MESH).rgb(el == IMPACT ? pale : C).a(fa).uv4(0, 1, 1, 1, 0.5F, 0, 0.5F, 0).va(0.6F, 0.6F, 1, 1)
                    .quad(px[a], py[a], pz[a], px[b], py[b], pz[b], px[c], py[c], pz[c], px[c], py[c], pz[c]);
        }

        // ---- 连线：一圈 + 每片网的对角 ----
        float la0 = (0.42F + 0.25F * act + 0.3F * g + 0.25F * charge + 0.5F * rel + 0.6F * swE) * A;
        linkW = Rb * (1.2F + 0.5F * g + 0.4F * swE);
        hotW = Rb * 0.17F * (1 + 0.5F * swE);
        int nLinks = N + tris.length;
        for (int li = 0; li < nLinks; li++) {
            boolean ring = li < N;
            int ia, ib;
            if (ring) {
                ia = order[li];
                ib = order[(li + 1) % N];
            } else {
                int[] tr = tris[li - N];
                ia = order[tr[0]];
                ib = order[tr[2]];
            }
            float la = la0 * (ring ? 1 : 0.55F + 0.45F * g);
            // 受击：一部分连线崩断，只剩两头各一截
            float cut = 0;
            if (hurtE > 0.05F && hash(li * 17 + (int) Math.floor(hurtS * 4)) < 0.5F) {
                cut = hurtE;
            }
            double ex = px[ib] - px[ia], ey = py[ib] - py[ia], ez = pz[ib] - pz[ia];
            double L = Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (L < Rb * 2.2F) {
                continue;
            }
            ex /= L;
            ey /= L;
            ez /= L;
            double ax = px[ia] + ex * Rb * 0.9F, ay = py[ia] + ey * Rb * 0.9F, az = pz[ia] + ez * Rb * 0.9F;
            double bx = px[ib] - ex * Rb * 0.9F, by = py[ib] - ey * Rb * 0.9F, bz = pz[ib] - ez * Rb * 0.9F;
            sideOf((ax + bx) * 0.5, (ay + by) * 0.5, (az + bz) * 0.5, ex, ey, ez);
            double sx = side[0], sy = side[1], sz = side[2];
            boolean fancy = ring && (!lite || !heavy);
            int n;
            if (el == ELECTRIC && fancy) {
                n = 5;
                int seed = q5 * 7 + li * 3;
                for (int j = 0; j < n; j++) {
                    float f = j / 4.0F, jit = j == 0 || j == 4 ? 0 : (hash(seed * 31 + j * 7) - 0.5F) * 2 * Rb * 0.7F;
                    lx[j] = ax + (bx - ax) * f + sx * jit;
                    ly[j] = ay + (by - ay) * f + sy * jit;
                    lz[j] = az + (bz - az) * f + sz * jit;
                }
            } else if (el == MAGNETIC && fancy) {
                n = 6;
                double k = L * 0.22 * ((li & 1) != 0 ? 1 : -1);
                double mx = (ax + bx) * 0.5 + sx * k, my = (ay + by) * 0.5 + sy * k + L * 0.06, mz = (az + bz) * 0.5 + sz * k;
                for (int j = 0; j < n; j++) {
                    double f = j / 5.0, u = 1 - f;
                    lx[j] = u * u * ax + 2 * u * f * mx + f * f * bx;
                    ly[j] = u * u * ay + 2 * u * f * my + f * f * by;
                    lz[j] = u * u * az + 2 * u * f * mz + f * f * bz;
                }
            } else if (el == POISON && fancy) {
                n = 5;
                double sag = L * (0.16 + 0.04 * Math.sin(T * 1.3F + li));
                for (int j = 0; j < n; j++) {
                    double f = j / 4.0;
                    lx[j] = ax + (bx - ax) * f;
                    ly[j] = ay + (by - ay) * f - sag * 4 * f * (1 - f);
                    lz[j] = az + (bz - az) * f;
                }
            } else {
                n = 2;
                lx[0] = ax;
                ly[0] = ay;
                lz[0] = az;
                lx[1] = bx;
                ly[1] = by;
                lz[1] = bz;
            }
            float on = m.flick(li);
            int last = n - 1;
            // 精简档底座放不下时，这根只留主色柔光，后面每根至少还能画一截
            simple = false;
            if (lite) {
                int per = el == ICE || el == RADIATION ? 5 : 2;
                simple = baseLeft() < (cut > 0 ? 2 * per : per + 2) + (nLinks - li - 1) * 2;
            }
            if (cut > 0) {
                if (last == 1) {
                    float k = 0.35F * (1 - cut);
                    hx[0] = lx[0];
                    hy[0] = ly[0];
                    hz[0] = lz[0];
                    hx[1] = lx[0] + (lx[1] - lx[0]) * k;
                    hy[1] = ly[0] + (ly[1] - ly[0]) * k;
                    hz[1] = lz[0] + (lz[1] - lz[0]) * k;
                    link(hx, hy, hz, 0, 2, ring, la, on, li, t);
                    if (sparkN < 6) {
                        spX[sparkN] = hx[1];
                        spY[sparkN] = hy[1];
                        spZ[sparkN++] = hz[1];
                    }
                    hx[0] = lx[0] + (lx[1] - lx[0]) * (1 - k);
                    hy[0] = ly[0] + (ly[1] - ly[0]) * (1 - k);
                    hz[0] = lz[0] + (lz[1] - lz[0]) * (1 - k);
                    hx[1] = lx[1];
                    hy[1] = ly[1];
                    hz[1] = lz[1];
                    link(hx, hy, hz, 0, 2, ring, la, on, li, t);
                } else {
                    int h0 = Math.max(1, (int) Math.floor(last * 0.35F));
                    link(lx, ly, lz, 0, h0 + 1, ring, la, on, li, t);
                    link(lx, ly, lz, last - h0, h0 + 1, ring, la, on, li, t);
                    if (sparkN < 6) {
                        spX[sparkN] = lx[h0];
                        spY[sparkN] = ly[h0];
                        spZ[sparkN++] = lz[h0];
                    }
                }
                continue;
            }
            link(lx, ly, lz, 0, n, ring, la, on, li, t);
            if (!ring) {
                continue;
            }
            // 光点沿线流，聚拢时流得快
            pen.cat = EphemeraStaging.PARTICLE;
            int nb = lite ? 1 : 2;
            for (int j = 0; j < nb; j++) {
                float f = frac(T * (0.3F + 0.9F * g + 1.2F * charge) + (float) j / nb + hash(li * 9));
                int i0 = Math.min(last - 1, (int) Math.floor(f * last));
                double k = f * last - i0;
                float w = Rb * 0.7F;
                pen.s(SOFTCORE).rgb(hot).a(0.85F * la * on).billboard(lx[i0] + (lx[i0 + 1] - lx[i0]) * k,
                        ly[i0] + (ly[i0 + 1] - ly[i0]) * k, lz[i0] + (lz[i0 + 1] - lz[i0]) * k, w, w);
            }
            pen.cat = EphemeraStaging.BASE;
            if (simple) {
                continue;
            }
            // 元素招牌挂在连线上
            double mx = (lx[0] + lx[last]) * 0.5, my = (ly[0] + ly[last]) * 0.5, mz = (lz[0] + lz[last]) * 0.5;
            if (el == FIRE) {
                // 连线像烧着的引信，一路窜起火苗
                for (int j = 0; j < (lite ? 1 : 2); j++) {
                    double f = frac(T * 0.4F + j * 0.5F + hash(li * 5));
                    double qx = lx[0] + (lx[last] - lx[0]) * f, qy = ly[0] + (ly[last] - ly[0]) * f, qz = lz[0] + (lz[last] - lz[0]) * f;
                    float fl = 0.7F + 0.5F * noise(m.t * 4 + li * 3 + j, li);
                    pen.s(((li + j) & 1) != 0 ? FLAME_0 + 1 : FLAME_0).rgb(C).a(0.8F * la);
                    spike(qx, qy - Rb * 0.2F, qz, qx, qy + Rb * 2.2F * fl, qz, Rb * 1.1F);
                }
            } else if (el == POISON) {
                // 最低处挂着一滴，慢慢拉长、落下
                int lo = last / 2;
                float gph = frac(T * 0.45F + hash(li * 3));
                double ey2 = ly[lo] - Rb * (0.3F + 2.2F * gph * gph);
                pen.s(SOFT).rgb(C).a(0.7F * la).beam(lx[lo], ly[lo], lz[lo], lx[lo], ey2, lz[lo], Rb * 0.3F * (1 - 0.5F * gph));
                pen.s(DROP).rgb(pale).a(0.9F * la * (1 - gph * gph * gph * gph)).billboard(lx[lo], ey2, lz[lo], Rb * 0.75F, Rb * 0.95F);
            } else if (el == IMPACT && bt > 0.05F) {
                // 打拍那一下，连线中点往两头推出一道亮条
                float k2 = 0.5F * (1 - bt);
                for (int e = 0; e <= last; e += last) {
                    pen.s(STREAK).rgb(WHITE).a(bt * la * hk).beam(mx + (lx[e] - mx) * k2, my + (ly[e] - my) * k2, mz + (lz[e] - mz) * k2,
                            mx + (lx[e] - mx) * (k2 + 0.25F), my + (ly[e] - my) * (k2 + 0.25F), mz + (lz[e] - mz) * (k2 + 0.25F), Rb * 0.5F);
                }
            } else if (el == RADIATION && bl > 1.2F) {
                pen.s(HEX_0).rgb(hot).a(0.85F * la).billboard(mx, my, mz, Rb * 2, Rb * 2, t);
            }
        }

        // 聚拢时各球往胸前的那一点牵一根辐线
        if (g > 0.05F && (!lite || baseLeft() >= 2 * N)) {
            for (int i = 0; i < N; i++) {
                double tx = px[i] + (cgx - px[i]) * 0.85, ty = py[i] + (cgy - py[i]) * 0.85, tz = pz[i] + (cgz - pz[i]) * 0.85;
                pen.s(SOFT).rgb(C).a(0.3F * g * A * hk).beam(px[i], py[i], pz[i], tx, ty, tz, Rb * 0.7F);
                pen.rgb(hot).a((0.4F + 0.5F * charge) * g * A).beam(px[i], py[i], pz[i], tx, ty, tz, Rb * 0.12F);
            }
        }
    }

    /** 雷球本体，顺带记下身上冒东西的发射点 */
    private void balls(St s, FxMotion m, ElementMotif fx) {
        // ---- 雷球本体 ----
        int metal = el == FIRE ? 0x572E21 : el == ICE ? 0x99BDD6 : el == POISON ? 0x4D6147 : mix(0x7A808C, C, 0.14F);
        int spikeC = mul(metal, el == ICE ? 0.85F : 0.6F);
        int nAcc = lite || heavy ? 1 : el == FIRE ? 4 : 3;
        for (int i = 0; i < N; i++) {
            double cx = px[i], cy = py[i], cz = pz[i];
            float sz = bSz[i], e = bE[i], shot = bShot[i];
            float ang = s.spin[i] + i;
            float on = el == ELECTRIC ? m.flick(30 + i) : 1;
            // 拖尾：射出去时、跑动被甩开时
            float vx = bvx[i], vy = bvy[i], vz = bvz[i], spd = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            pen.cat = EphemeraStaging.TRAIL;
            if (shot > 0.04F * H) {
                pen.s(COMET).rgb(C).a(0.75F * e * A * hk)
                        .beam(cx - swX * shot * 0.8F, cy - swY * shot * 0.8F, cz - swZ * shot * 0.8F, cx, cy, cz, sz * 1.6F);
            } else if (spd > 1.2F) {
                float tl = Math.min(0.55F * H, spd * 0.1F) / spd;
                pen.s(COMET).rgb(C).a(Math.min(0.55F, (spd - 1.2F) * 0.25F) * A * hk)
                        .beam(cx - vx * tl, cy - vy * tl, cz - vz * tl, cx, cy, cz, sz * 1.5F);
            }
            pen.cat = EphemeraStaging.BASE;
            float gw = sz * 7;
            pen.s(SOFT).rgb(C).a((0.22F + 0.05F * g + 0.12F * charge + 0.3F * rel + 0.25F * e) * A * hk).billboard(cx, cy, cz, gw, gw);
            float[] ax = AXIS[i];
            // 金属短刺
            for (int j = 4; j < 8; j++) {
                rot(DIRS[j], ax, ang);
                float l0 = sz * 0.6F, l1 = sz * (1.9F + 0.5F * hurtE);
                pen.alphaBlend().rgb(spikeC).a(0.95F);
                kite(cx + v3[0] * l0, cy + v3[1] * l0, cz + v3[2] * l0, cx + v3[0] * l1, cy + v3[1] * l1, cz + v3[2] * l1, sz * 0.62F, 0.22F);
            }
            // 能量刺：一直在，元素凸起叠在它外面
            float eLen = sz * (1.55F + 0.35F * g + 0.5F * charge + 0.7F * e + 0.6F * hurtE);
            for (int j = 0; j < 4; j++) {
                rot(DIRS[j], ax, ang);
                edX[j] = v3[0];
                edY[j] = v3[1];
                edZ[j] = v3[2];
                if (!lite || j < 2) {
                    float l0 = sz * 0.55F;
                    pen.rgb(el == ICE ? pale : hot).a(0.85F * A * on).va(1, 0.7F, 0.4F, 0.7F);
                    kite(cx + v3[0] * l0, cy + v3[1] * l0, cz + v3[2] * l0, cx + v3[0] * eLen, cy + v3[1] * eLen, cz + v3[2] * eLen, sz * 0.48F, 0.28F);
                }
            }
            pen.s(MINE).alphaBlend().rgb(metal).a(0.96F).billboard(cx, cy, cz, sz * 2.3F, sz * 2.3F);
            pen.s(SEAM).rgb(el == FIRE ? mix(C, hot, 0.3F) : C).a((0.55F + 0.4F * charge + 0.4F * e + 0.1F * breath) * A * on * hk)
                    .billboard(cx, cy, cz, sz * 2.25F, sz * 2.25F, ang * 1.3F);
            float cw = sz * (1 + 0.15F * charge + 0.15F * breath);
            pen.s(SOFTCORE).rgb(hot).a(0.95F * A * on).billboard(cx, cy, cz, cw, cw);
            // 环在球自己的赤道面上，随轴倾斜，看得出是立体的
            float[] e1 = E1[i], e2 = E2[i];
            if (!lite) {
                pen.s(HALO).rgb(C).a((0.55F + 0.35F * charge + 0.3F * rel) * A * hk);
                FxKit.plate(pen, cx, cy, cz, e1[0], e1[1], e1[2], e2[0], e2[1], e2[2], sz * 3.6F * (1 + 0.4F * rel + 0.3F * e), ang);
            }
            if (el == MAGNETIC) {
                pen.s(SOFTRING).rgb(pale).a(0.4F * A);
                FxKit.plate(pen, cx, cy, cz, ax[0], ax[1], ax[2], e1[0], e1[1], e1[2], sz * 3.2F, -ang);
            }
            if (el == IMPACT && bt > 0.05F) {
                float w = sz * (2 + 4 * (1 - bt));
                pen.s(SHOCK).rgb(pale).a(0.8F * bt * A * hk).billboard(cx, cy, cz, w, w);
            }
            if (el == RADIATION && hash((int) Math.floor(m.t / 0.08) * 13 + i) > 0.86F) {
                pen.s(HEX_0).rgb(hot).a(0.8F * A).billboard(cx, cy, cz, sz * 3.2F, sz * 3.2F, t);
            }
            if (el == ICE && hash((int) Math.floor(m.t * 1.5) * 11 + i) > 0.75F) {
                pen.s(FLARE).rgb(WHITE).a(0.8F * A).billboard(cx + edX[0] * eLen, cy + edY[0] * eLen, cz + edZ[0] * eLen, sz * 2, sz * 2, 0);
            }
            // 元素凸起
            if (!((el == MAGNETIC || lite && heavy) && (i & 1) != 0)) {
                for (int j = 0; j < nAcc; j++) {
                    float ddx = edX[j], ddy = edY[j], ddz = edZ[j];
                    if (el == FIRE) {
                        norm(ddx, ddy + 0.8F, ddz);
                        ddx = v3[0];
                        ddy = v3[1];
                        ddz = v3[2];
                    } else if (el == POISON && j == 0) {
                        ddx = 0;
                        ddy = -1;
                        ddz = 0;
                    }
                    float b0 = sz * 0.8F;
                    fx.accent(cx + ddx * b0, cy + ddy * b0, cz + ddz * b0, ddx, ddy, ddz,
                            sz * (1.5F + 0.5F * g + 0.8F * e + 0.6F * hurtE), sz * 0.6F, i * 8 + j, 0.85F * A);
                }
            }
            double hx0 = cx - m.x, hz0 = cz - m.z, hl = Math.sqrt(hx0 * hx0 + hz0 * hz0);
            if (hl > 1.0E-4) {
                sites.add(cx, cy, cz, (float) (hx0 / hl), 0, (float) (hz0 / hl));
            } else {
                sites.add(cx, cy, cz, m.fwdX, 0, m.fwdZ);
            }
        }
    }

    /** 脚下：一层柔光、一张网纹，再加元素的贴地纹样 */
    private void ground(FxMotion m, ElementMotif fx) {
        pen.cat = EphemeraStaging.BASE;
        double gy = m.groundY + 0.03;
        float r1 = W * 3 * (1 + 0.6F * landE), r2 = W * 2.4F * (1 + 0.8F * landE);
        pen.s(SOFT).rgb(C).a((0.08F + 0.06F * g + 0.15F * landE) * A * hk).flat(m.x, gy, m.z, r1, r1);
        pen.s(MESH).rgb(C).a((0.18F + 0.2F * rel + 0.35F * landE) * A * hk).flat(m.x, gy, m.z, r2, r2, T * 0.1F);
        if (el != MAGNETIC && !(lite && (el == ICE || el == ELECTRIC))) {
            fx.ground(m.x, m.groundY, m.z, W * 0.8F, 0.4F * A);
        }
    }

    /** 一根连线（或崩断后的半截）：暗托、主色柔光，再按元素叠亮芯 / 棱晶 / 虚线；点在 X/Y/Z 的 [from, from+cnt) */
    private void link(double[] X, double[] Y, double[] Z, int from, int cnt, boolean ring, float la, float on, int li, float t) {
        if (ring && !lite && (!heavy || cnt == 2)) {
            band(X, Y, Z, from, cnt, Rb * 0.55F);
            pen.s(SOFT).alphaBlend().rgb(0).a(0.22F * la).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
        }
        band(X, Y, Z, from, cnt, linkW);
        pen.s(SOFT).rgb(C).a(0.45F * la * on * hk).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
        if (simple) {
            return;
        }
        int e = from + cnt - 1;
        if (el == ICE) {
            // 冰：连线是一串首尾相接的细棱晶
            int n = cnt == 2 ? 3 : 1;
            for (int j = 0; j < n; j++) {
                double f0 = (double) j / n, f1 = (double) (j + 1) / n;
                pen.rgb(pale).a(0.85F * la);
                kite(X[from] + (X[e] - X[from]) * f0, Y[from] + (Y[e] - Y[from]) * f0, Z[from] + (Z[e] - Z[from]) * f0,
                        X[from] + (X[e] - X[from]) * f1, Y[from] + (Y[e] - Y[from]) * f1, Z[from] + (Z[e] - Z[from]) * f1, Rb * 0.32F, 0.5F);
            }
            band(X, Y, Z, from, cnt, Rb * 0.06F);
            pen.rgb(WHITE).a(0.7F * la).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
        } else if (el == RADIATION) {
            // 辐射：虚线，各段自己明灭
            int q = (int) Math.floor(t / 0.08F);
            for (int j = 0; j < 4; j++) {
                float hb = hash(q * 7 + li * 5 + j);
                if (hb < 0.15F) {
                    continue;
                }
                double f0 = (j + 0.15) / 4, f1 = (j + 0.85) / 4;
                pen.rgb(hot).a(Math.min(1, 0.9F * la * (hb > 0.88F ? 1.5F : 1))).beam(
                        X[from] + (X[e] - X[from]) * f0, Y[from] + (Y[e] - Y[from]) * f0, Z[from] + (Z[e] - Z[from]) * f0,
                        X[from] + (X[e] - X[from]) * f1, Y[from] + (Y[e] - Y[from]) * f1, Z[from] + (Z[e] - Z[from]) * f1, Rb * 0.18F);
            }
        } else {
            band(X, Y, Z, from, cnt, hotW);
            pen.rgb(hot).a(0.95F * la * on).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
        }
    }

    private void band(double[] X, double[] Y, double[] Z, int from, int cnt, float w) {
        rib.clear();
        for (int i = from; i < from + cnt; i++) {
            rib.add(X[i], Y[i], Z[i], w, 1);
        }
    }

    /** 同 {@link FxParticles#draw}，最多画 max 个；返回还剩几个名额 */
    private int drawPool(FxParticles p, float sizeMul, float alphaMul, int max) {
        int drawn = 0;
        for (int i = 0; i < p.count() && drawn < max; i++) {
            float k = p.age[i] / p.life[i];
            float sz = (p.size0[i] + (p.size1[i] - p.size0[i]) * k) * sizeMul;
            float fade = Math.min(1, k / Math.max(p.fadeIn[i], 1e-4F)) * Math.min(1, (1 - k) / Math.max(p.fadeOut[i], 1e-4F));
            float al = p.a[i] * fade * alphaMul;
            if (al <= 0.002F || sz <= 0) {
                continue;
            }
            drawn++;
            pen.s(p.sprite[i]).rgb(p.rgb[i]).a(al);
            if (!p.add[i]) {
                pen.alphaBlend();
            }
            if (p.stretch[i] > 0) {
                double tx = p.x[i] - p.vx[i] * p.stretch[i], ty = p.y[i] - p.vy[i] * p.stretch[i], tz = p.z[i] - p.vz[i] * p.stretch[i];
                double dx = tx - p.x[i], dy = ty - p.y[i], dz = tz - p.z[i];
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) > sz * 0.5F) {
                    pen.beam(tx, ty, tz, p.x[i], p.y[i], p.z[i], sz);
                    continue;
                }
            }
            pen.billboard(p.x[i], p.y[i], p.z[i], sz, sz, p.rot[i]);
        }
        return Math.max(0, max - drawn);
    }

    // ---------- 小件 ----------

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private void norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0E-6F) {
            v3[0] = 0;
            v3[1] = 1;
            v3[2] = 0;
            return;
        }
        v3[0] = x / l;
        v3[1] = y / l;
        v3[2] = z / l;
    }

    /** v 绕单位轴 k 转 a，写进 v3 */
    private void rot(float[] v, float[] k, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float kx = k[0], ky = k[1], kz = k[2];
        float cx = ky * v[2] - kz * v[1], cy = kz * v[0] - kx * v[2], cz = kx * v[1] - ky * v[0];
        float kd = (kx * v[0] + ky * v[1] + kz * v[2]) * (1 - c);
        v3[0] = v[0] * c + cx * s + kx * kd;
        v3[1] = v[1] * c + cy * s + ky * kd;
        v3[2] = v[2] * c + cz * s + kz * kd;
    }

    /** dir 在镜头里的侧向：dir × 视线，写进 side */
    private void sideOf(double px0, double py0, double pz0, double dx, double dy, double dz) {
        double vx = px0 - pen.camX(), vy = py0 - pen.camY(), vz = pz0 - pen.camZ();
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1.0E-6) {
            side[0] = pen.rightX;
            side[1] = pen.rightY;
            side[2] = pen.rightZ;
        } else {
            side[0] = sx / l;
            side[1] = sy / l;
            side[2] = sz / l;
        }
    }

    /** 尖朝 tip 的贴图片，贴图上沿对着 tip；笔刷先设好 */
    private void spike(double ax, double ay, double az, double tx, double ty, double tz, float w) {
        double dx = tx - ax, dy = ty - ay, dz = tz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) {
            l = 1;
        }
        sideOf(ax, ay, az, dx / l, dy / l, dz / l);
        double sx = side[0] * w / 2, sy = side[1] * w / 2, sz = side[2] * w / 2;
        pen.quad(tx - sx, ty - sy, tz - sz, tx + sx, ty + sy, tz + sz, ax + sx, ay + sy, az + sz, ax - sx, ay - sy, az - sz);
    }

    /** 菱形片：base 到 tip，最宽处在 mid（0~1）；笔刷先设好，顶点顺序 tip、一侧、base、另一侧 */
    private void kite(double ax, double ay, double az, double tx, double ty, double tz, float w, float mid) {
        double dx = tx - ax, dy = ty - ay, dz = tz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) {
            l = 1;
        }
        sideOf(ax, ay, az, dx / l, dy / l, dz / l);
        double mx = ax + dx * mid, my = ay + dy * mid, mz = az + dz * mid;
        double sx = side[0] * w / 2, sy = side[1] * w / 2, sz = side[2] * w / 2;
        pen.quad(tx, ty, tz, mx + sx, my + sy, mz + sz, ax, ay, az, mx - sx, my - sy, mz - sz);
    }
}
