package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 悬晶：身体两侧、自手至肩的高度各悬几块竖立的长条晶石（五棱、上下两段尖），高低前后错落，内部透光，周围萦绕同色雾气，
 * 各自缓缓起伏、自转。跑动时带着惯性往后拖，起跳上浮，落地一沉再弹回；挥砍时主手一侧往前刺出、炸成碎晶再聚回；
 * 受击时震裂错位，片刻复原。元素决定晶石的质地（熔岩、冰棱、浊绿……）和身上长出的东西。
 */
public final class CrystalStyle extends ProtoStyle<CrystalStyle.St> {

    static final int FACET = EphemeraSprites.file("crystal/facet");
    static final int LAVA = EphemeraSprites.file("crystal/lava");
    static final int ICEF = EphemeraSprites.file("crystal/icef");

    private static final int N = 6;
    private static final int MAIN = 1;
    // 左右、离中线（按宽）、中心高（按身高）、前后（按宽）、长（按身高）、粗细（占长）、顶端往外偏
    // 手约 0.42、肩约 0.8 身高；顶尖压在 0.88 以下，不挨着头
    private static final float[][] LAYOUT = {
            {-1, 1.8F, 0.58F, -0.15F, 0.44F, 0.4F, 0.08F},
            {-1, 2.4F, 0.4F, 0.45F, 0.28F, 0.42F, -0.14F},
            {-1, 1.75F, 0.76F, -0.55F, 0.23F, 0.42F, 0.18F},
            {1, 1.85F, 0.52F, 0.08F, 0.42F, 0.4F, -0.07F},
            {1, 1.8F, 0.74F, -0.45F, 0.26F, 0.42F, 0.15F},
            {1, 2.45F, 0.36F, -0.3F, 0.22F, 0.44F, -0.2F},
    };
    private static final int[] ALL = {0, 1, 2, 3, 4, 5};
    private static final int[] LITE = {0, 1, 3, 4};
    // 两种纵剖面，腰线高低不同，同一侧的几块不会长得一样
    private static final float[][] PT = {{-0.5F, -0.14F, 0.2F, 0.5F}, {-0.5F, -0.26F, 0.08F, 0.5F}};
    private static final float[][] PR = {{0, 1, 0.86F, 0}, {0, 0.88F, 1, 0}};
    private static final int[] SEED = new int[N];
    private static final float[][] IRR = new float[N][5], JIT = new float[N][5];
    private static final float[] SPIN = new float[N];
    private static final int[] SHARDS = {SHARD_0, SHARD_1, SHARD_2, SHARD_3};
    private static final int[] BUBBLES = {BUBBLE_0, BUBBLE_1, BUBBLE_2, BUBBLE_3};

    static {
        for (int i = 0; i < N; i++) {
            SEED[i] = 17 + i * 31;
            for (int j = 0; j < 5; j++) {
                IRR[i][j] = 0.78F + 0.4F * h01(i * 13 + j * 5);
                JIT[i][j] = (h01(i * 29 + j * 3) - 0.5F) * 0.5F;
            }
            SPIN[i] = (0.22F + 0.2F * h01(i * 7 + 2)) * ((i & 1) != 0 ? -1 : 1);
        }
    }

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        // 爆发单独一份：精简档和电、磁只留最新一次，要整份清掉再放，不能连身上的粒子一起清
        final ElementMotif fxB = new ElementMotif();
        final FxKit.Spring[] sp = new FxKit.Spring[N];
        FxParticles P, Fg;
        boolean lite;
        float fogAcc, kick, air;
        boolean burstDone = true;
        boolean seen;
        int pSwing, pHurt, eLand, eSwing, eHurt;

        St() {
            for (int i = 0; i < N; i++) {
                sp[i] = new FxKit.Spring();
            }
        }
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(12);
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final float[] v3 = new float[3];
    private final float[] dir = new float[3];
    private final double[] zx = new double[9], zy = new double[9], zz = new double[9];

    // 这一帧各块晶石：先左后右
    private final int[] itCr = new int[N];
    private final double[] itX = new double[N], itY = new double[N], itZ = new double[N];
    private final float[] itAx = new float[N], itAy = new float[N], itAz = new float[N];
    private final float[] itL = new float[N], itR = new float[N], itTh = new float[N];
    private int n, nLeft;

    // 一块晶石的环点
    private final double[][] vx = new double[4][6], vy = new double[4][6], vz = new double[4][6];
    private final float[] ringT = new float[4], ringR = new float[4], bright = new float[4];

    // 这一帧的质地
    private int bodyD, bodyL, add, core, spr;
    private float bodyA, addA, backA, coreA;
    private float ldX, ldY, ldZ;
    private float rX, rZ, fX, fZ;
    private float b1x, b1y, b1z, b2x, b2y, b2z;
    private double camX, camY, camZ;

    private double mX, mZ, gx, gy, gz;
    // 正在画的那块晶石
    private double fcx, fcy, fcz;
    private float fax, fay, faz;
    private final EphemeraPen.Ribbon tmp = new EphemeraPen.Ribbon(12);

    private EphemeraStaging out;
    private int start, cap;

    @Override
    public float extent() {
        return 2.0F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.fx.reset();
        s.fxB.reset();
        for (FxKit.Spring k : s.sp) {
            k.reset();
        }
        s.P = null;
        s.Fg = null;
        s.seen = false;
    }

    private void init(St s, FxMotion m) {
        s.lite = m.lite;
        s.P = new FxParticles(m.lite ? 16 : 48);
        s.Fg = new FxParticles(m.lite ? 12 : 26);
        s.fogAcc = 0;
        s.kick = 0;
        s.air = 0;
        s.burstDone = true;
        s.pSwing = m.nSwing;
        s.pHurt = m.nHurt;
        for (FxKit.Spring k : s.sp) {
            k.reset();
        }
    }

    /** 还能画几个面片（这一帧这一份的上限减去已经写了的） */
    private int room() {
        return cap - (out.pending() - start);
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        boolean lite = m.lite, fp = d.firstPersonSelf;
        if (s.P == null || s.lite != lite) {
            init(s, m);
        }
        out = d.out;
        start = out.pending();
        cap = lite ? EphemeraTier.CAP_REDUCED : EphemeraTier.CAP_FULL;
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc, act = m.act, amp = m.amp, Bk = m.burst;
        int el = m.el;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        s.fxB.begin(pen, m);
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        float T = m.clock(), bt = m.beat(0);
        float run = clamp(m.speed / 5.6F, 0, 1);
        rX = m.rightX;
        rZ = m.rightZ;
        fX = m.fwdX;
        fZ = m.fwdZ;
        camX = pen.camX();
        camY = pen.camY();
        camZ = pen.camZ();
        mX = m.x;
        mZ = m.z;
        // 近白的颜色（冲击默认色）加法叠起来一片白，柔光按亮度压
        float lum = (0.2126F * (C >> 16 & 255) + 0.7152F * (C >> 8 & 255) + 0.0722F * (C & 255)) / 255;
        float lk = clamp(0.5F / (lum + 1.0E-3F), 0.45F, 1);
        float breath = 0.5F + 0.5F * (float) Math.sin(t * 1.5F);
        float swS = m.since(m.tSwing), hurtS = m.since(m.tHurt);
        float swE = m.env(swS, 0.04F, 0.1F, 0.45F), hurtE = m.env(hurtS, 0.02F, 0.12F, 0.6F);
        float crackE = m.env(hurtS, 0.02F, 0.22F, 0.5F), landE = m.env(m.since(m.tLand), 0.02F, 0.06F, 0.5F);
        // 刺出之后才炸开：先往前送 0.1 秒，再散成碎晶、聚回
        float spl = m.env(swS - 0.1F, 0.05F, 0.12F, 0.5F);
        s.air += ((m.onGround ? 0 : 1) - s.air) * Math.min(1, dt * 6);
        s.kick += dt * (6 * hurtE + 4 * swE);

        float A = (0.66F + 0.34F * act + 0.05F * breath * amp) * (lite ? 0.9F : 1);
        if (hurtE > 0.05F) {
            A *= 0.7F + 0.3F * hash((int) Math.floor(m.t * 22));
        }
        texture(el, C, hot, pale, deep, lk);
        norm(-pen.rightX * 0.45F - pen.fwdX * 0.35F, 0.85F - pen.rightY * 0.45F - pen.fwdY * 0.35F, -pen.rightZ * 0.45F - pen.fwdZ * 0.35F);
        ldX = v3[0];
        ldY = v3[1];
        ldZ = v3[2];

        // 元素爆发：完整档先画，后面的副件按剩下的让；精简档先保晶石，放到后面看余量
        events(s, m);
        if (!lite) {
            pen.cat = EphemeraStaging.PARTICLE;
            s.fxB.bursts();
        }

        // ---------- 每块晶石的位置：弹簧追目标，跑起来拖在后面、顶端往后仰 ----------
        int[] list = lite ? LITE : ALL;
        n = 0;
        for (int pass = 0; pass < 2; pass++) {
            int want = pass == 0 ? -1 : 1;
            for (int ci : list) {
                if ((int) LAYOUT[ci][0] == want) {
                    place(s, m, ci, fp, T, bt, swE, hurtE, landE, Bk);
                }
            }
            if (pass == 0) {
                nLeft = n;
            }
        }
        sites.clear();

        // ---------- 每侧一团同色雾光托底、脚下一片光 ----------
        pen.cat = EphemeraStaging.BASE;
        for (int side = 0; side < 2; side++) {
            int i0 = side == 0 ? 0 : nLeft, i1 = side == 0 ? nLeft : n;
            if (i1 <= i0) {
                continue;
            }
            int sd = side == 0 ? -1 : 1;
            if (!fp) {
                centroid(i0, i1);
                pen.s(SOFT).rgb(C).a((0.18F + 0.05F * breath + 0.08F * swE) * lk * A).billboard(gx, gy, gz, W * 2.2F, H * 0.7F);
            }
            pen.s(SOFT).rgb(C).a((0.12F + 0.12F * landE) * lk * A)
                    .flat(m.x + rX * sd * W * 1.6F, m.groundY + 0.03, m.z + rZ * sd * W * 1.6F, W * 1.8F, W * 1.8F);
        }

        // ---------- 晶石本体 ----------
        int q50 = (int) Math.floor(m.t / 0.05);
        for (int i = 0; i < n; i++) {
            int ci = itCr[i], seed = SEED[ci];
            boolean main = LAYOUT[ci][0] == MAIN;
            double cx = itX[i], cy = itY[i], cz = itZ[i];
            float ax = itAx[i], ay = itAy[i], az = itAz[i], L = itL[i], R = itR[i], th = itTh[i];
            float al = A * (1 - 0.7F * occ(cx, cz, W));
            if (el == RADIATION) {
                al *= m.blink(seed);
            }
            float fk = el == ELECTRIC ? (hash(q50 * 13 + seed * 5) > 0.2F ? 1 : 0.45F) : 1;
            float sp = main ? spl : 0;
            float mainA = 1 - smooth(sp / 0.35F);
            // 挥砍炸开：散成几块小碎晶，往外飞一截再聚回原处
            if (sp > 0.02F) {
                float fa = smooth(sp / 0.2F) * al;
                for (int k = 0; k < 3; k++) {
                    // 精简档碎晶只画发光那层，挥砍那一下才放得进 150
                    if (room() < (lite ? 8 : 16)) {
                        break;
                    }
                    float h1 = h01(seed * 3 + k), h2 = h01(seed * 5 + k * 7), kf = k / 2.0F - 0.5F;
                    float sd = LAYOUT[ci][0];
                    norm(fX * 0.55F + rX * sd * (h1 - 0.3F) * 1.6F + ax * kf * 1.2F,
                            (h2 - 0.6F) * 0.8F + ay * kf * 1.2F,
                            fZ * 0.55F + rZ * sd * (h1 - 0.3F) * 1.6F + az * kf * 1.2F);
                    float dx = v3[0], dy = v3[1], dz = v3[2];
                    float r = sp * W * (0.7F + 0.4F * h2) * Bk, along = kf * L * 0.6F;
                    norm(ax + dx * 0.7F, ay + dy * 0.7F, az + dz * 0.7F);
                    crystal(ci, cx + dx * r + ax * along, cy + dy * r + ay * along, cz + dz * r + az * along, v3[0], v3[1], v3[2],
                            L * (0.5F + 0.15F * h1), R * 0.78F, th * 3 + k * 1.7F + sp * 6, fa, 4, -0.5F, 0.5F, false, !lite);
                }
            }
            if (mainA < 0.02F) {
                sites.add(cx, cy, cz, ax, ay, az);
                continue;
            }
            float mA = al * mainA;
            if (crackE > 0.04F && !lite) {
                // 受击：从一道斜裂处断成上下两截，左右错开
                float cut = -0.05F + 0.2F * h01(seed + 9), off = W * 0.11F * crackE;
                float tw = 0.25F * crackE * ((ci & 1) != 0 ? 1 : -1);
                sideOf(ax, ay, az);
                float sx = v3[0], sy = v3[1], sz = v3[2];
                norm(ax - sx * tw, ay - sy * tw, az - sz * tw);
                crystal(ci, cx - sx * off, cy - sy * off, cz - sz * off, v3[0], v3[1], v3[2], L, R, th, mA, 5, -0.5F, cut, false, true);
                norm(ax + sx * tw, ay + sy * tw, az + sz * tw);
                float up = L * 0.04F * crackE;
                crystal(ci, cx + sx * off + ax * up, cy + sy * off + ay * up, cz + sz * off + az * up, v3[0], v3[1], v3[2],
                        L, R, th + 0.4F * crackE, mA, 5, cut, 0.5F, false, true);
                double ccx = cx + ax * cut * L, ccy = cy + ay * cut * L, ccz = cz + az * cut * L;
                pen.s(STREAK).rgb(WHITE).a(0.9F * crackE * A).beam(ccx - sx * R * 1.6F, ccy - sy * R * 1.6F, ccz - sz * R * 1.6F,
                        ccx + sx * R * 1.6F, ccy + sy * R * 1.6F, ccz + sz * R * 1.6F, R * 0.5F);
                pen.s(FLARE).rgb(hot).a(0.8F * crackE * A).billboard(ccx, ccy, ccz, R * 3.2F, R * 3.2F, seed);
            } else {
                crystal(ci, cx, cy, cz, ax, ay, az, L, R, th, mA * fk, 5, -0.5F, 0.5F, !lite, true);
            }
            // 内光：沿长轴一道柔光 + 中心亮芯；外面一圈晕
            float pulse = 0.82F + 0.18F * (float) Math.sin(T * 2.2F + seed) + 0.5F * swE * (main ? 1 : 0.3F) + 0.4F * bt;
            pen.s(SOFT).rgb(core).a(coreA * pulse * mA * fk).beam(cx - ax * L * 0.36F, cy - ay * L * 0.36F, cz - az * L * 0.36F,
                    cx + ax * L * 0.36F, cy + ay * L * 0.36F, cz + az * L * 0.36F, R * 2.4F);
            float cs = R * 1.5F * pulse;
            pen.s(SOFTCORE).rgb(core).a(0.5F * Math.min(1, pulse) * mA * fk * (el == ICE ? 0.6F : 1)).billboard(cx, cy, cz, cs, cs);
            if (!lite && mainA > 0.5F) {
                pen.s(SOFT).rgb(C).a((el == FIRE ? 0.3F : 0.2F + 0.06F * breath) * lk * mA).billboard(cx, cy, cz, L * 1.1F, L * 1.6F);
            }
            // 跑起来身后拖一道残光
            if (run > 0.15F && !lite && ci % 3 == 0 && room() > 0) {
                pen.cat = EphemeraStaging.TRAIL;
                pen.s(STREAK).rgb(C).a(0.32F * run * lk * mA).beam(cx - m.velX * 0.09F, cy - m.velY * 0.3F * 0.09F, cz - m.velZ * 0.09F,
                        cx, cy, cz, R * 2.6F);
                pen.cat = EphemeraStaging.BASE;
            }
            sites.add(cx + ax * L * 0.5F, cy + ay * L * 0.5F, cz + az * L * 0.5F, ax, ay, az);
        }

        if (lite && room() >= burstCost(el)) {
            pen.cat = EphemeraStaging.PARTICLE;
            s.fxB.bursts();
            pen.cat = EphemeraStaging.BASE;
        }

        // ---------- 元素招牌 ----------
        for (int i = 0; i < n; i++) {
            int ci = itCr[i], seed = SEED[ci];
            boolean main = LAYOUT[ci][0] == MAIN;
            double cx = itX[i], cy = itY[i], cz = itZ[i];
            float ax = itAx[i], ay = itAy[i], az = itAz[i], L = itL[i], R = itR[i];
            float al = A * (1 - 0.5F * occ(cx, cz, W)) * (1 - smooth((main ? spl : 0) / 0.35F));
            if (al < 0.03F || room() < signCost(el, lite)) {
                continue;
            }
            // 精简档电、毒的凸起一根十几片，只长在两块大的上，给身上冒的粒子留余量
            boolean minor = lite && (el == ELECTRIC || el == POISON) && ci != 0 && ci != 3;
            double tpx = cx + ax * L * 0.5F, tpy = cy + ay * L * 0.5F, tpz = cz + az * L * 0.5F;
            double bmx = cx - ax * L * 0.5F, bmy = cy - ay * L * 0.5F, bmz = cz - az * L * 0.5F;
            float sd = LAYOUT[ci][0];
            switch (el) {
                case FIRE -> {
                    // 顶上窜火，底尖挂一滴熔光
                    float k = L * 0.08F;
                    fx.accent(tpx - ax * k, tpy - ay * k, tpz - az * k, ax, ay, az, L * (0.5F + 0.15F * (float) Math.sin(t * 4 + seed)),
                            R * 1.7F, seed, 0.85F * al);
                    pen.s(SOFTCORE).rgb(0xFFBF59).a(0.8F * al).billboard(bmx, bmy, bmz, R * 1.4F, R * 1.8F);
                }
                case ICE -> {
                    // 腰上结出几根小冰棱，顶尖偶尔一闪
                    basis(ax, ay, az);
                    for (int j = 0; j <= (lite ? 0 : 2); j += 2) {
                        float ang = itTh[i] + j / 5.0F * TAU;
                        float rdx = b1x * (float) Math.cos(ang) + b2x * (float) Math.sin(ang);
                        float rdy = b1y * (float) Math.cos(ang) + b2y * (float) Math.sin(ang);
                        float rdz = b1z * (float) Math.cos(ang) + b2z * (float) Math.sin(ang);
                        float ka = (j != 0 ? 0.12F : -0.18F) * L, kr = R * 0.8F;
                        double bx = cx + ax * ka + rdx * kr, by = cy + ay * ka + rdy * kr, bz = cz + az * ka + rdz * kr;
                        float ta = j != 0 ? 0.9F : -0.5F;
                        norm(rdx + ax * ta, rdy + ay * ta, rdz + az * ta);
                        double tx = bx + v3[0] * R * 1.9F, ty = by + v3[1] * R * 1.9F, tz = bz + v3[2] * R * 1.9F;
                        pen.rgb(pale).a(0.75F * al);
                        kite(bx, by, bz, tx, ty, tz, R * 0.75F, 0.3F);
                        pen.rgb(WHITE).a(0.9F * al);
                        kite(bx, by, bz, tx, ty, tz, R * 0.2F, 0.35F);
                    }
                    if (hash((int) Math.floor(m.t * 1.4) * 17 + seed) > 0.62F) {
                        pen.s(FLARE).rgb(WHITE).a(0.85F * al).billboard(tpx, tpy, tpz, R * 3.4F, R * 3.4F, 0);
                    }
                    fx.accent(tpx, tpy, tpz, ax, ay, az, L * 0.22F, R * 0.9F, seed, 0.7F * al);
                }
                case POISON -> {
                    // 晶体里冒泡，底尖拉出一道黏丝往下坠
                    for (int k = 0; k < (lite ? 1 : 2); k++) {
                        float f = (T * 0.35F + h01(seed + k * 3)) % 1;
                        float along = (f - 0.45F) * L * 0.75F, lat = (h01(seed + k) - 0.5F) * R * 0.8F;
                        float bs = R * 0.55F;
                        pen.s(BUBBLES[(seed + k) & 3]).rgb(pale).a(0.9F * al * (float) Math.sin(Math.PI * f))
                                .billboard(cx + ax * along + rX * lat, cy + ay * along, cz + az * along + rZ * lat, bs, bs);
                    }
                    if (minor) {
                        break;
                    }
                    norm(-ax + rX * sd * 0.6F, -ay, -az + rZ * sd * 0.6F);
                    fx.accent(bmx, bmy, bmz, v3[0], v3[1], v3[2], L * 0.35F, R, seed, 0.85F * al);
                }
                case IMPACT -> {
                    // 拍子落下：晶石腰上推出一道冲击圈
                    if (bt > 0.03F) {
                        basis(ax, ay, az);
                        pen.s(SHOCK).rgb(pale).a(0.7F * bt * al * lk);
                        FxKit.plate(pen, cx, cy, cz, b1x, b1y, b1z, b2x, b2y, b2z, R * (2.5F + 7 * (1 - bt)), 0);
                    }
                    fx.accent(tpx, tpy, tpz, ax, ay, az, L * 0.35F, R * 0.8F, seed, 0.75F * al);
                }
                case RADIATION -> {
                    int q = (int) Math.floor(m.t / 0.08);
                    float hb = hash(q * 31 + seed);
                    if (hb > 0.72F) {
                        float o = (hash(q * 7 + seed) - 0.5F) * L * 0.7F;
                        pen.s(HEX_0).rgb(hot).a(0.85F * al).billboard(cx + ax * o, cy + ay * o, cz + az * o, R * 2.2F, R * 2.2F, q);
                    }
                    fx.accent(tpx, tpy, tpz, ax, ay, az, L * 0.4F, R * 0.7F, seed, 0.75F * al);
                    if (!lite && hb < 0.3F) {
                        float k = (hb - 0.15F) * 4;
                        norm(rX * sd + fX * k, 0, rZ * sd + fZ * k);
                        fx.accent(cx, cy, cz, v3[0], v3[1], v3[2], L * 0.5F, R * 0.6F, seed + 3, 0.6F * al);
                    }
                }
                case ELECTRIC -> {
                    if (!minor) {
                        fx.accent(tpx, tpy, tpz, ax, ay, az, L * 0.35F, R * 0.8F, seed, 0.8F * al);
                    }
                }
                default -> {
                }
            }
        }

        if (el == ELECTRIC) {
            // 同一侧的晶石之间跳弧，偶尔一道劈到手上
            int NA = lite || swE > 0.1F || hurtE > 0.1F ? 1 : 2;
            int nz = lite ? 4 : 6;
            for (int side = 0; side < 2; side++) {
                int sd = side == 0 ? -1 : 1, g0 = side == 0 ? 0 : nLeft, gn = (side == 0 ? nLeft : n) - g0;
                if (gn == 0) {
                    continue;
                }
                for (int k = 0; k < NA; k++) {
                    if (hash(q50 * 3 + k * 11 + sd * 5) < 0.3F || room() < nz * 2 + 1) {
                        continue;
                    }
                    double pax, pay, paz, pbx, pby, pbz;
                    if (k == 1 && hash(q50 * 11 + sd) < 0.4F || gn < 2) {
                        int it = g0 + (int) Math.floor(hash(q50 + sd) * gn);
                        float hl = itL[it] * 0.5F;
                        pax = itX[it] - itAx[it] * hl;
                        pay = itY[it] - itAy[it] * hl;
                        paz = itZ[it] - itAz[it] * hl;
                        BodyPoints.Pt w = sd > 0 ? m.pts.wristR : m.pts.wristL;
                        pbx = w.x;
                        pby = w.y;
                        pbz = w.z;
                        double ddx = pbx - pax, ddy = pby - pay, ddz = pbz - paz;
                        if (Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) > 0.6F * H) {
                            continue;
                        }
                    } else {
                        int i0 = (int) Math.floor(hash(q50 * 7 + k + sd) * gn), i1 = (i0 + 1 + k) % gn;
                        if (i0 == i1) {
                            continue;
                        }
                        i0 += g0;
                        i1 += g0;
                        float o0 = itL[i0] * (hash(q50 + 3) - 0.5F) * 0.8F, o1 = itL[i1] * (hash(q50 + 9) - 0.5F) * 0.8F;
                        pax = itX[i0] + itAx[i0] * o0;
                        pay = itY[i0] + itAy[i0] * o0;
                        paz = itZ[i0] + itAz[i0] * o0;
                        pbx = itX[i1] + itAx[i1] * o1;
                        pby = itY[i1] + itAy[i1] * o1;
                        pbz = itZ[i1] + itAz[i1] * o1;
                    }
                    double ddx = pbx - pax, ddy = pby - pay, ddz = pbz - paz;
                    float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                    norm((float) ddx, (float) ddy, (float) ddz);
                    sideOf(v3[0], v3[1], v3[2]);
                    zig(pax, pay, paz, pbx, pby, pbz, nz, dist * 0.2F, q50 * 13 + k + sd * 7, v3[0], v3[1], v3[2]);
                    pen.s(SOFT).rgb(C).a(0.45F * A).ribbon(zigRibbon(nz, H * 0.05F), EphemeraPen.FACE_CAM, 0, 1);
                    pen.rgb(hot).a(A).ribbon(zigRibbon(nz, H * 0.01F), EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(FLARE).rgb(hot).a(0.8F * A).billboard(pbx, pby, pbz, H * 0.07F, H * 0.07F, q50);
                }
            }
        } else if (el == MAGNETIC) {
            // 每块晶石像一根磁棒：一道磁力线从顶尖绕出去回到底尖，绕着长轴慢转，光珠沿线流
            int NP = lite ? 6 : 8;
            for (int i = 0; i < n; i++) {
                if (lite && (i & 1) != 0) {
                    continue;
                }
                int ci = itCr[i], seed = SEED[ci];
                if (LAYOUT[ci][0] == MAIN && spl > 0.05F) {
                    continue;
                }
                double cx = itX[i], cy = itY[i], cz = itZ[i];
                float ax = itAx[i], ay = itAy[i], az = itAz[i], L = itL[i], R = itR[i];
                float al = A * (1 - 0.5F * occ(cx, cz, W));
                boolean glow = !lite && ci % 3 == 0;
                if (al < 0.03F || room() < (glow ? 2 * NP : NP) + 1) {
                    continue;
                }
                basis(ax, ay, az);
                float ang = T * 0.9F + seed, ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
                float nx = b1x * ca + b2x * sa, ny = b1y * ca + b2y * sa, nzz = b1z * ca + b2z * sa;
                float bulge = R * (3.2F + 0.6F * (float) Math.sin(T * 1.3F + seed) + 2 * swE);
                float hl = L * 0.5F;
                rib.clear();
                for (int k = 0; k <= NP; k++) {
                    float f = (float) k / NP, mixK = (1 - (float) Math.cos(Math.PI * f)) / 2;
                    float along = hl - L * mixK, o = (float) Math.sin(Math.PI * f) * bulge, w = (float) Math.sin(TAU * f) * L * 0.12F;
                    rib.add(cx + ax * (along + w) + nx * o, cy + ay * (along + w) + ny * o, cz + az * (along + w) + nzz * o, H * 0.01F, 1);
                }
                if (glow) {
                    pen.s(SOFT).rgb(C).a(0.3F * al).ribbon(widen(H * 0.06F), EphemeraPen.FACE_CAM, 0, 1);
                }
                pen.rgb(pale).a(0.85F * al).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                float f = (t * 0.6F + h01(seed)) % 1;
                int k = Math.min(NP - 1, (int) Math.floor(f * NP));
                float ff = f * NP - k;
                float bs = H * 0.05F;
                pen.s(SOFTCORE).rgb(hot).a(0.95F * al).billboard(rib.x[k] + (rib.x[k + 1] - rib.x[k]) * ff,
                        rib.y[k] + (rib.y[k + 1] - rib.y[k]) * ff, rib.z[k] + (rib.z[k + 1] - rib.z[k]) * ff, bs, bs);
            }
        }

        // 炸开那一下：主手一侧一团白闪、一圈冲击
        if (spl > 0.02F && !lite && !fp && n > nLeft && room() >= 2) {
            centroid(nLeft, n);
            float f = clamp((swS - 0.1F) / 0.4F, 0, 1), s1 = W * (0.8F + 1.6F * f), s2 = W * 1.1F;
            pen.cat = EphemeraStaging.PARTICLE;
            pen.s(SHOCK).rgb(hot).a(0.45F * (1 - f) * A * lk).billboard(gx, gy, gz, s1, s1);
            pen.s(FLARE).rgb(hot).a(0.6F * spl * (1 - f) * A).billboard(gx, gy, gz, s2, s2, 0.4F);
            pen.cat = EphemeraStaging.BASE;
        }

        if (!(lite && (el == ICE || el == ELECTRIC || el == MAGNETIC)) && room() >= groundCost(el, lite)) {
            fx.ground(m.x, m.groundY, m.z, W * 0.9F, 0.35F * A);
        }

        // ---------- 粒子：炸开时甩出碎屑，受击掉渣 ----------
        pen.cat = EphemeraStaging.PARTICLE;
        FxParticles P = s.P;
        P.thin = m.emit;
        if (s.pSwing != m.nSwing) {
            s.pSwing = m.nSwing;
            s.burstDone = false;
        }
        if (!s.burstDone && swS > 0.1F) {
            s.burstDone = true;
            for (int i = nLeft; i < n; i++) {
                float rr = itR[i];
                for (int k = 0; k < (lite ? 3 : 6); k++) {
                    EphemeraFx.randDir(dir);
                    norm(fX * 1.2F + dir[0] * 0.9F, dir[1] * 0.9F, fZ * 1.2F + dir[2] * 0.9F);
                    float v = rnd(2.5F, 4.5F) * sc;
                    P.spec().pos(itX[i], itY[i], itZ[i]).vel(v3[0] * v, v3[1] * v, v3[2] * v).life(rnd(0.3F, 0.55F))
                            .size(rr * 0.9F, rr * 0.2F).sprite(SHARDS[k & 3]).rgb((k & 1) != 0 ? hot : C).drag(3)
                            .rot(rnd(0, 6)).spin(rnd(-12, 12)).keep().spawn();
                }
            }
        }
        if (s.pHurt != m.nHurt) {
            s.pHurt = m.nHurt;
            for (int i = 0; i < n; i++) {
                float rr = itR[i];
                for (int k = 0; k < (lite ? 1 : 3); k++) {
                    EphemeraFx.randDir(dir);
                    float v = rnd(1, 2.2F) * sc;
                    P.spec().pos(itX[i], itY[i], itZ[i]).vel(dir[0] * v, dir[1] * v - 0.5F * sc, dir[2] * v).life(rnd(0.4F, 0.7F))
                            .size(rr * 0.7F, rr * 0.15F).sprite(SHARDS[k & 3]).rgb(hot).grav(4 * sc).drag(1.5F)
                            .rot(rnd(0, 6)).spin(rnd(-10, 10)).keep().spawn();
                }
            }
        }
        P.step(dt, null);
        drawSome(P, m.size, room());

        // 身上冒的元素粒子：一次画完没法截，余量不够这一帧就先不冒
        if (sites.n > 0 && room() >= fx.particles() + (lite ? 8 : 24)) {
            fx.motes(sites, 14 * (0.5F + 0.5F * act), m.sc);
        }

        // 雾气：从晶石周围慢慢渗出，不跟人走，跑起来自然留在后面；最后画，余量不够先砍它
        FxParticles Fg = s.Fg;
        Fg.thin = m.emit;
        s.fogAcc += dt * n * (lite ? 0.6F : 1.5F) * (0.7F + 0.5F * act + 0.6F * run);
        boolean poisonFog = el == POISON;
        while (s.fogAcc >= 1 && n > 0) {
            s.fogAcc -= 1;
            int i = Math.min(n - 1, (int) (rnd() * n));
            float L = itL[i], R = itR[i], along = rnd(-0.4F, 0.4F) * L;
            EphemeraFx.randDir(dir);
            float sd = LAYOUT[itCr[i]][0];
            float vy = el == ICE || el == POISON ? -0.12F : el == FIRE ? 0.3F : 0.06F;
            FxParticles.Spec f = Fg.spec().pos(itX[i] + itAx[i] * along + dir[0] * R * 1.2F, itY[i] + itAy[i] * along + dir[1] * R * 1.2F,
                            itZ[i] + itAz[i] * along + dir[2] * R * 1.2F)
                    .vel(rX * sd * 0.08F * sc, vy * sc, rZ * sd * 0.08F * sc).life(rnd(1.4F, 2.4F)).size(L * 0.5F, L * 1.1F)
                    .sprite(SMOKE_0 + Math.min(3, (int) (rnd() * 4)))
                    .rgb(poisonFog ? mix(deep, C, 0.4F) : el == FIRE ? mix(C, 0x4D1A0D, 0.4F) : C)
                    .a(poisonFog ? 0.3F : 0.14F * lk).rot(rnd() * 6).spin(rnd(-0.4F, 0.4F)).drag(0.6F);
            if (poisonFog) {
                f.alphaBlend();
            }
            f.spawn();
        }
        Fg.step(dt, null);
        drawSome(Fg, m.size, room());
        pen.cat = EphemeraStaging.BASE;
    }

    /** 一块晶石的目标、弹簧、朝向、大小，追加进这一帧的列表 */
    private void place(St s, FxMotion m, int ci, boolean fp, float T, float bt, float swE, float hurtE, float landE, float Bk) {
        float[] Ly = LAYOUT[ci];
        float H = m.H, W = m.W;
        int sd = (int) Ly[0], seed = SEED[ci];
        // 第一人称看自己时主手一侧不往前刺，免得扎到眼前
        float sw = sd == MAIN && !fp ? swE : 0;
        float bob = H * 0.02F * (float) Math.sin(T * (0.9F + 0.3F * h01(seed)) + seed);
        float lat = sd * W * (Ly[1] + 0.25F * landE + 0.12F * s.air + 0.2F * bt) + W * 0.04F * (float) Math.sin(T * 0.6F + seed * 2);
        float up = H * (Ly[2] - 0.06F * landE + 0.04F * s.air) + bob;
        float fw = W * Ly[3] + W * 0.05F * (float) Math.sin(T * 0.47F + seed);
        // 挥砍：主手一侧往前刺，高度压在胸口以下，不扫到脸前
        if (sw > 0.001F) {
            fw += W * (1.5F + 0.5F * (ci % 3)) * sw * Bk;
            lat -= sd * W * 0.45F * sw;
            up += H * (0.6F - Ly[2]) * 0.45F * sw;
        } else if (swE > 0.001F) {
            fw += W * 0.25F * swE;
        }
        if (hurtE > 0.01F) {
            int q = (int) Math.floor(m.t / 0.045);
            lat += (hash(q * 7 + seed) - 0.5F) * W * 0.3F * hurtE;
            up += (hash(q * 5 + seed * 3) - 0.5F) * H * 0.06F * hurtE;
            fw += (hash(q * 3 + seed) - 0.5F) * W * 0.3F * hurtE;
        }
        double tx = m.x + rX * lat + fX * fw, ty = m.y + up, tz = m.z + rZ * lat + fZ * fw;
        // 弹簧阻尼按相对宿主算，匀速跑时不落后；目标往后挪 0.4·2ζ/ω·v，留四成惯性当拖尾
        float w0 = 6.5F + 2.5F * h01(seed + 1) + 16 * sw, zeta = 0.55F, lagK = 0.4F * 2 * zeta / w0;
        FxKit.Spring spr = s.sp[ci];
        follow(spr, tx - m.velX * lagK, ty, tz - m.velZ * lagK, m, w0, zeta);
        float lgX = (float) (spr.x - tx), lgY = (float) (spr.y - ty), lgZ = (float) (spr.z - tz);
        float tl = sd * Ly[6] + 0.06F * (float) Math.sin(T * 0.7F + seed), tf = 0.06F * (float) Math.cos(T * 0.55F + seed);
        norm(rX * tl + fX * tf, 1, rZ * tl + fZ * tf);
        float k = 2.4F / H;
        norm(v3[0] + lgX * k, v3[1] + lgY * 0.3F * k, v3[2] + lgZ * k);
        float ax = v3[0], ay = v3[1], az = v3[2];
        if (sw > 0.001F) {
            norm(fX - sd * 0.2F * rX, 0.15F, fZ - sd * 0.2F * rZ);
            float f = Math.min(0.85F, sw * 1.1F);
            norm(ax + (v3[0] - ax) * f, ay + (v3[1] - ay) * f, az + (v3[2] - az) * f);
            ax = v3[0];
            ay = v3[1];
            az = v3[2];
        }
        float L = H * Ly[4] * (1 + 0.12F * bt + 0.05F * (0.5F + 0.5F * (float) Math.sin(m.t * 1.5F)) * m.amp);
        itCr[n] = ci;
        itX[n] = spr.x;
        itY[n] = spr.y;
        itZ[n] = spr.z;
        itAx[n] = ax;
        itAy[n] = ay;
        itAz[n] = az;
        itL[n] = L;
        itR[n] = L * Ly[5] * 0.5F;
        itTh[n] = T * SPIN[ci] * (m.el == MAGNETIC ? 2.2F : 1) + s.kick * ((ci & 1) != 0 ? -1 : 1) + seed;
        n++;
    }

    /** 元素决定晶石本身的质地 */
    private void texture(int el, int C, int hot, int pale, int deep, float lk) {
        bodyD = mix(deep, 0, 0.35F);
        bodyL = mix(C, deep, 0.38F);
        bodyA = 0.62F;
        spr = FACET;
        add = C;
        addA = 0.75F * lk;
        backA = 0;
        core = hot;
        coreA = 0.55F * lk;
        switch (el) {
            case FIRE -> {
                // 熔岩晶：面本身不透光，只有裂缝和棱发亮
                bodyD = 0x210904;
                bodyL = 0x6B1C09;
                bodyA = 0.85F;
                spr = LAVA;
                add = mix(C, 0xFFCC59, 0.45F);
                addA = 1.25F;
                core = 0xFF9938;
                coreA = 0.85F;
            }
            case ICE -> {
                // 冰棱几乎透明，背面的棱也透出来
                bodyD = mix(deep, pale, 0.25F);
                bodyL = pale;
                bodyA = 0.12F;
                spr = ICEF;
                add = pale;
                addA = 0.85F;
                backA = 0.3F;
                core = WHITE;
                coreA = 0.22F;
            }
            case POISON -> {
                bodyD = 0x081205;
                bodyL = mix(C, deep, 0.45F);
                bodyA = 0.7F;
                addA = 0.7F;
                coreA = 0.45F;
            }
            case IMPACT -> {
                bodyL = mix(C, 0x595C66, 0.5F);
                bodyA = 0.45F;
                addA = 0.55F * lk;
            }
            case ELECTRIC -> coreA = 0.6F;
            default -> {
            }
        }
    }

    /**
     * 一块晶石：五棱（碎晶四棱），上下两段尖、中间一段腰，只画朝向镜头的面；背面按质地淡淡透出来当内部的棱。
     * t0 / t1 截取长轴上的一段（受击断开时用），body 为假时只画发光那层。
     */
    private void crystal(int ci, double cx, double cy, double cz, float ax, float ay, float az, float L, float R, float th, float al,
                         int sides, float t0, float t1, boolean back, boolean body) {
        if (al < 0.01F) {
            return;
        }
        float[] Pt = PT[ci & 1], Pr = PR[ci & 1];
        basis(ax, ay, az);
        int nr = 0;
        ringT[nr] = t0;
        ringR[nr++] = rAt(Pt, Pr, t0);
        for (int k = 1; k <= 2; k++) {
            if (Pt[k] > t0 + 1.0E-3F && Pt[k] < t1 - 1.0E-3F) {
                ringT[nr] = Pt[k];
                ringR[nr++] = Pr[k];
            }
        }
        ringT[nr] = t1;
        ringR[nr++] = rAt(Pt, Pr, t1);
        for (int r = 0; r < nr; r++) {
            float o = ringT[r] * L;
            double ccx = cx + ax * o, ccy = cy + ay * o, ccz = cz + az * o;
            for (int j = 0; j <= sides; j++) {
                int jj = j % sides;
                float ang = th + (float) jj / sides * TAU + JIT[ci][jj], rr = ringR[r] * R * IRR[ci][jj];
                float c = (float) Math.cos(ang) * rr, s = (float) Math.sin(ang) * rr;
                vx[r][j] = ccx + b1x * c + b2x * s;
                vy[r][j] = ccy + b1y * c + b2y * s;
                vz[r][j] = ccz + b1z * c + b2z * s;
            }
            bright[r] = 0.45F + 0.55F * Math.min(1, ringR[r] * 1.1F);
        }
        fcx = cx;
        fcy = cy;
        fcz = cz;
        fax = ax;
        fay = ay;
        faz = az;
        // 最正对镜头的那面先画，其余照顺序；凸体的正面彼此不叠，先后不影响观感
        int best = -1;
        float bestFc = -2;
        for (int k = 0; k < nr - 1; k++) {
            for (int j = 0; j < sides; j++) {
                float fc = facet(k, j, false, al, back, body);
                if (fc > bestFc) {
                    bestFc = fc;
                    best = k * sides + j;
                }
            }
        }
        if (best >= 0) {
            facet(best / sides, best % sides, true, al, back, body);
        }
        for (int k = 0; k < nr - 1; k++) {
            for (int j = 0; j < sides; j++) {
                if (k * sides + j != best) {
                    facet(k, j, true, al, back, body);
                }
            }
        }
    }

    /** 第 k 段第 j 面：算朝向，draw 为真时照朝向画出来；返回朝镜头的程度（退化的面返回 -2） */
    private float facet(int k, int j, boolean draw, float al, boolean back, boolean body) {
        double cx = fcx, cy = fcy, cz = fcz;
        float ax = fax, ay = fay, az = faz;
        float b0 = bright[k], b1 = bright[k + 1];
        double p0x = vx[k][j], p0y = vy[k][j], p0z = vz[k][j];
        double p1x = vx[k][j + 1], p1y = vy[k][j + 1], p1z = vz[k][j + 1];
        double p2x = vx[k + 1][j + 1], p2y = vy[k + 1][j + 1], p2z = vz[k + 1][j + 1];
        double p3x = vx[k + 1][j], p3y = vy[k + 1][j], p3z = vz[k + 1][j];
        double mx = (p0x + p1x + p2x + p3x) * 0.25, my = (p0y + p1y + p2y + p3y) * 0.25, mz = (p0z + p1z + p2z + p3z) * 0.25;
        double ux = p1x - p0x + p2x - p3x, uy = p1y - p0y + p2y - p3y, uz = p1z - p0z + p2z - p3z;
        double wx = p3x - p0x + p2x - p1x, wy = p3y - p0y + p2y - p1y, wz = p3z - p0z + p2z - p1z;
        double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        double dcx = mx - cx, dcy = my - cy, dcz = mz - cz, dd = dcx * ax + dcy * ay + dcz * az;
        double rdx = dcx - ax * dd, rdy = dcy - ay * dd, rdz = dcz - az * dd;
        if (nx * rdx + ny * rdy + nz * rdz < 0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double vdx = camX - mx, vdy = camY - my, vdz = camZ - mz, vl = Math.sqrt(vdx * vdx + vdy * vdy + vdz * vdz);
        if (nl < 1.0E-12 || vl < 1.0E-9) {
            return -2;
        }
        nx /= nl;
        ny /= nl;
        nz /= nl;
        vdx /= vl;
        vdy /= vl;
        vdz /= vl;
        float fc = (float) (nx * vdx + ny * vdy + nz * vdz);
        if (!draw) {
            return fc;
        }
        if (fc < 0) {
            if (back && backA > 0) {
                pen.s(spr).rgb(add).a(backA * al).va(b0, b0, b1, b1).quad(p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z, p3x, p3y, p3z);
            }
            return fc;
        }
        float sh = 0.25F + 0.75F * Math.max(0, (float) (nx * ldX + ny * ldY + nz * ldZ)), rim = 1 - fc;
        double hx = ldX + vdx, hy = ldY + vdy, hz = ldZ + vdz, hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
        float gl = hl < 1.0E-9 ? 0 : (float) Math.pow(Math.max(0, (nx * hx + ny * hy + nz * hz) / hl), 22);
        if (body) {
            pen.s(WHITE).alphaBlend().rgb(mix(bodyD, bodyL, sh)).a(bodyA * Math.min(1, al * 1.2F))
                    .quad(p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z, p3x, p3y, p3z);
        }
        pen.s(spr).rgb(mix(add, WHITE, Math.min(0.85F, gl * 1.3F))).a((addA * (0.25F + 0.45F * sh + 0.5F * rim) + 0.7F * gl) * al)
                .va(b0, b0, b1, b1).quad(p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z, p3x, p3y, p3z);
        return fc;
    }

    private static float rAt(float[] Pt, float[] Pr, float t) {
        for (int k = 0; k < 3; k++) {
            if (t <= Pt[k + 1]) {
                return Pr[k] + (Pr[k + 1] - Pr[k]) * (t - Pt[k]) / (Pt[k + 1] - Pt[k]);
            }
        }
        return 0;
    }

    /** 正面看时挡在躯干前的晶石淡一点，人物轮廓留得出来 */
    private float occ(double cx, double cz, float W) {
        double relX = cx - mX, relZ = cz - mZ;
        float depth = (float) (relX * pen.fwdX + relZ * pen.fwdZ), lat = Math.abs((float) (relX * pen.rightX + relZ * pen.rightZ));
        return depth < 0 ? (1 - smooth((lat - W * 0.55F) / (W * 0.5F))) * smooth(-depth / (W * 0.6F)) : 0;
    }

    /** 落地、挥砍、受击在脚下各放一次元素爆发；精简档和电、磁一次就上百个面片，只留最新一次 */
    private void events(St s, FxMotion m) {
        if (!s.seen) {
            s.seen = true;
            s.eLand = m.nLand;
            s.eSwing = m.nSwing;
            s.eHurt = m.nHurt;
        }
        boolean only = m.lite || m.el == ELECTRIC || m.el == MAGNETIC;
        float r = m.W * 1.6F;
        if (s.eLand != m.nLand) {
            s.eLand = m.nLand;
            if (m.since(m.tLand) < 0.2F) {
                shoot(s, only, m, r, 1);
            }
        }
        if (s.eSwing != m.nSwing) {
            s.eSwing = m.nSwing;
            if (m.since(m.tSwing) < 0.2F) {
                shoot(s, only, m, r * 0.8F, 0.8F);
            }
        }
        if (s.eHurt != m.nHurt) {
            s.eHurt = m.nHurt;
            if (m.since(m.tHurt) < 0.2F) {
                shoot(s, only, m, r, 0.9F);
            }
        }
    }

    private static void shoot(St s, boolean only, FxMotion m, float r, float k) {
        if (only) {
            s.fxB.reset();
        }
        s.fxB.fire(m.x, m.groundY, m.z, r, k);
    }

    // 下面几个是各段面片数的上限估计，只拿来判断余量够不够

    private int burstCost(int el) {
        int nn = 6;
        return switch (el) {
            case FIRE, ICE -> 2 * nn;
            case ELECTRIC -> nn / 2 * 14 + 1;
            case MAGNETIC -> 1 + nn / 2 * 8;
            default -> nn + 2;
        };
    }

    private static int signCost(int el, boolean lite) {
        return switch (el) {
            case FIRE -> 3;
            case ICE -> lite ? 7 : 9;
            case POISON -> lite ? 14 : 15;
            case IMPACT -> 4;
            case RADIATION -> lite ? 7 : 14;
            case ELECTRIC -> 13;
            default -> 0;
        };
    }

    private static int groundCost(int el, boolean lite) {
        return switch (el) {
            case FIRE -> 2;
            case ICE -> 19;
            case ELECTRIC -> 21;
            case MAGNETIC -> lite ? 21 : 51;
            case IMPACT -> 3;
            default -> 5;
        };
    }

    /** 粒子池最多画 max 个：一次全画会把这一帧顶出上限，余量不够时只画前面那些 */
    private void drawSome(FxParticles p, float sizeMul, int max) {
        int drawn = 0;
        for (int i = 0; i < p.count() && drawn < max; i++) {
            float k = p.age[i] / p.life[i];
            float sz = (p.size0[i] + (p.size1[i] - p.size0[i]) * k) * sizeMul;
            float fade = Math.min(1, k / Math.max(p.fadeIn[i], 1e-4F)) * Math.min(1, (1 - k) / Math.max(p.fadeOut[i], 1e-4F));
            float al = p.a[i] * fade;
            if (al <= 0.002F || sz <= 0) {
                continue;
            }
            pen.s(p.sprite[i]).rgb(p.rgb[i]).a(al);
            if (!p.add[i]) {
                pen.alphaBlend();
            }
            pen.billboard(p.x[i], p.y[i], p.z[i], sz, sz, p.rot[i]);
            drawn++;
        }
    }

    private void centroid(int i0, int i1) {
        gx = gy = gz = 0;
        for (int i = i0; i < i1; i++) {
            gx += itX[i];
            gy += itY[i];
            gz += itZ[i];
        }
        int c = i1 - i0;
        gx /= c;
        gy /= c;
        gz /= c;
    }

    /** 长轴 a 的两根横轴：b1 = a × 右，b2 = b1 × a；a 几乎朝右时改用朝前 */
    private void basis(float ax, float ay, float az) {
        float x = ay * rZ, y = az * rX - ax * rZ, z = -ay * rX;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0E-3F) {
            x = ay * fZ;
            y = az * fX - ax * fZ;
            z = -ay * fX;
            l = (float) Math.sqrt(x * x + y * y + z * z);
        }
        if (l < 1.0E-6F) {
            x = 1;
            y = 0;
            z = 0;
            l = 1;
        }
        b1x = x / l;
        b1y = y / l;
        b1z = z / l;
        b2x = b1y * az - b1z * ay;
        b2y = b1z * ax - b1x * az;
        b2z = b1x * ay - b1y * ax;
    }

    /** dir 在镜头里的侧向：dir × 镜头朝前，写进 v3 */
    private void sideOf(float dx, float dy, float dz) {
        float x = dy * pen.fwdZ - dz * pen.fwdY, y = dz * pen.fwdX - dx * pen.fwdZ, z = dx * pen.fwdY - dy * pen.fwdX;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0E-4F) {
            v3[0] = pen.rightX;
            v3[1] = pen.rightY;
            v3[2] = pen.rightZ;
            return;
        }
        v3[0] = x / l;
        v3[1] = y / l;
        v3[2] = z / l;
    }

    /** 菱形片：base 到 tip，最宽处在 mid（0~1）；笔刷先设好 */
    private void kite(double ax, double ay, double az, double tx, double ty, double tz, float w, float mid) {
        double dx = tx - ax, dy = ty - ay, dz = tz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) {
            l = 1;
        }
        sideOf((float) (dx / l), (float) (dy / l), (float) (dz / l));
        double mx = ax + dx * mid, my = ay + dy * mid, mz = az + dz * mid;
        double sx = v3[0] * w / 2, sy = v3[1] * w / 2, sz = v3[2] * w / 2;
        pen.quad(tx, ty, tz, mx + sx, my + sy, mz + sz, ax, ay, az, mx - sx, my - sy, mz - sz);
    }

    /** a 到 b 的折线（n 段），中间各点沿 (sx, sy, sz) 随机偏 ±amp */
    private void zig(double ax, double ay, double az, double bx, double by, double bz, int nn, float amp, int seed,
                     float sx, float sy, float sz) {
        for (int i = 0; i <= nn; i++) {
            float f = (float) i / nn;
            float j = i == 0 || i == nn ? 0 : (hash(seed * 31 + i * 7) - 0.5F) * 2 * amp;
            zx[i] = ax + (bx - ax) * f + sx * j;
            zy[i] = ay + (by - ay) * f + sy * j;
            zz[i] = az + (bz - az) * f + sz * j;
        }
    }

    private EphemeraPen.Ribbon zigRibbon(int nn, float w) {
        rib.clear();
        for (int i = 0; i <= nn; i++) {
            rib.add(zx[i], zy[i], zz[i], w, 1);
        }
        return rib;
    }

    /** rib 的点抄进 tmp、宽度换成 w，rib 原样留着 */
    private EphemeraPen.Ribbon widen(float w) {
        tmp.clear();
        for (int i = 0; i < rib.n; i++) {
            tmp.add(rib.x[i], rib.y[i], rib.z[i], w, rib.a[i]);
        }
        return tmp;
    }

    /** w0 = 25 在 dt = 0.04 下半隐式积分贴着发散的边，拆成不超过 0.02 秒的小步 */
    private static void follow(FxKit.Spring sp, double x, double y, double z, FxMotion m, float w0, float zeta) {
        int k = Math.max(1, (int) Math.ceil(m.dt / 0.02F));
        float h = m.dt / k;
        for (int i = 0; i < k; i++) {
            sp.step(x, y, z, h, w0, zeta, m.trackX, m.trackY, m.trackZ);
        }
    }

    private static float h01(double i) {
        double v = Math.sin(i * 127.1 + 31.7) * 43758.5;
        return (float) (v - Math.floor(v));
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
}
