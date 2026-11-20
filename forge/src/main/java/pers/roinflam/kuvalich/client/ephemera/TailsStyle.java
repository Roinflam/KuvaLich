package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 狐尾：腰后扇形伸出五条蓬松的能量尾巴，尾身是流动的光絮、尾尖发白，每节带惯性、越往尾尖越软。
 * 站着缓缓摇摆，跑动往后拉平拖出光尾，起跳下甩、落地外散，挥砍整束张开上扬，受击炸毛竖起。
 */
public final class TailsStyle extends ProtoStyle<TailsStyle.St> {

    // 横向淡出、沿长度不变，铺在丝带上两头不会变细
    static final int FUR = EphemeraSprites.file("tails/haze");

    private static final int MAX_T = 5, MAX_S = 10;
    // 尾根那几节 w0 到 34，dt = 0.04 下半隐式积分不稳，按每步不超过 1/90 秒拆开
    private static final float SUB_HZ = 90;

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        FxParticles pt;
        final FxTrail[] tips = new FxTrail[MAX_T];
        final FxKit.Spring[][] seg = new FxKit.Spring[MAX_T][MAX_S + 1];
        boolean ready, lite;
        float acc;

        St() {
            for (FxKit.Spring[] row : seg) {
                for (int i = 0; i < row.length; i++) {
                    row[i] = new FxKit.Spring();
                }
            }
        }
    }

    private final double[][] px = new double[MAX_T][MAX_S + 1], py = new double[MAX_T][MAX_S + 1], pz = new double[MAX_T][MAX_S + 1];
    private final float[] tlen = new float[MAX_T];
    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(MAX_S + 2);
    private final EphemeraPen.Ribbon zr = new EphemeraPen.Ribbon(10);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final float[] dir = new float[3];
    private final double[] side = new double[3];
    private final double[] zx = new double[9], zy = new double[9], zz = new double[9];

    private EphemeraStaging out;
    private int cap, from;
    private int nt, ns;
    private float Wt;

    @Override
    public float extent() {
        return 2.6F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.fx.reset();
        s.ready = false;
    }

    private void init(St s, FxMotion m) {
        s.ready = true;
        s.lite = m.lite;
        s.pt = new FxParticles(m.lite ? 30 : 100);
        s.acc = 0;
        for (int k = 0; k < MAX_T; k++) {
            s.tips[k] = new FxTrail(0.32F, 1.5F * m.sc, 0.04F * m.sc, 3 * m.sc);
            for (FxKit.Spring sp : s.seg[k]) {
                sp.reset();
            }
        }
    }

    private static float mod05(float v) {
        return ((v % 0.5F) + 0.5F) % 0.5F;
    }

    private static float prof(float f) {
        return Math.max(0, (float) Math.sin(Math.PI * Math.pow(f, 0.42))) * (1 - 0.35F * f * f * f) + 0.15F * (1 - f);
    }

    private static float fade(float f) {
        return Math.min(1, f * 6 + 0.3F) * (1 - 0.25F * f);
    }

    /** 完整档按整个幻纹（扣掉留给粒子的）算，精简档只算底座 */
    private int left() {
        return cap - (out.pending() - from);
    }

    private static int accentCost(int el, boolean lite) {
        return switch (el) {
            case FIRE -> 2;
            case ICE, IMPACT -> 4;
            case ELECTRIC, POISON -> 13;
            case MAGNETIC -> lite ? 9 : 17;
            default -> 6;
        };
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        boolean lite = m.lite;
        if (!s.ready || s.lite != lite) {
            init(s, m);
        }
        out = d.out;
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc;
        float act = m.act, amp = m.amp, Bk = m.burst;
        int el = m.el;
        boolean sneak = m.sneak, fp = d.firstPersonSelf;
        float fwdX = m.fwdX, fwdZ = m.fwdZ, rightX = m.rightX, rightZ = m.rightZ;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        float bt = m.beat(0), bl = m.blink(3), fk = m.flick(3);
        float run = clamp(m.speed / 5.6F, 0, 1);
        float swE = m.env(m.since(m.tSwing), 0.03F, 0.08F, 0.5F), hurtE = m.env(m.since(m.tHurt), 0.02F, 0.15F, 0.55F);
        float jumpE = m.env(m.since(m.tJump), 0.03F, 0.1F, 0.4F), landE = m.env(m.since(m.tLand), 0.01F, 0.06F, 0.45F);
        float breath = 0.5F + 0.5F * (float) Math.sin(t * 1.5F);
        float slow = el == ICE ? 0.5F : el == POISON ? 0.75F : 1;
        boolean heavy = el == ELECTRIC || el == POISON || el == MAGNETIC;

        // 爆发先画：电、磁挥砍那一下一次就上百个面片，后面的副件按剩下的让
        pen.cat = EphemeraStaging.PARTICLE;
        fx.events(m.x, m.groundY, m.z, W * 1.6F);
        fx.bursts();
        if (lite) {
            cap = EphemeraTier.CAP_REDUCED;
            from = out.pending();
        } else {
            // 粒子最后画、没法半截停，按池里现有的数先留出来
            cap = EphemeraTier.CAP_FULL - s.pt.count() - fx.particles() - (el == POISON ? 20 : 0) - 12;
            from = 0;
        }

        int NT = lite ? 3 : 5, NS = lite ? 6 : 10;
        nt = NT;
        ns = NS;
        double rx = m.x - fwdX * W * 0.34F, ry = m.y + H * (sneak ? 0.5F : 0.47F), rz = m.z - fwdZ * W * 0.34F;
        float Lt = H * 0.7F * (1 + 0.06F * breath * amp) * (sneak ? 0.85F : 1);
        float Wt = H * 0.22F * (1 + 0.35F * hurtE + 0.15F * swE);
        this.Wt = Wt;
        float A = (0.62F + 0.38F * act + 0.06F * breath) * bl * fk;
        if (hurtE > 0.05F) {
            A *= 0.7F + 0.3F * hash((int) Math.floor(m.t * 20));
        }
        // 挥砍时整束张开、往上甩；受击时炸毛竖起；起跳往下甩，落地往外散
        float spreadK = 1 + 0.7F * swE * Bk + 0.5F * hurtE + 0.35F * landE;
        float liftK = 0.5F * swE + 0.6F * hurtE - 0.45F * jumpE;

        pen.cat = EphemeraStaging.BASE;
        // 暗托和大柔光罩在腰后，第一人称低头会糊在脚下，不画
        if (!fp) {
            pen.s(SOFT).alphaBlend().rgb(deep).a(lite ? 0.18F : 0.28F)
                    .billboard(rx - fwdX * Lt * 0.35F, ry - Lt * 0.15F, rz - fwdZ * Lt * 0.35F, Lt * 1.5F, Lt * 1.5F);
        }
        FxKit.glow(pen, rx, ry, rz, W * 1.5F * (1 + 0.4F * swE), C, hot, 0.7F * A);
        if (!fp) {
            pen.s(SOFT).rgb(C).a((0.07F + 0.05F * breath * amp + 0.12F * swE) * A)
                    .billboard(rx - fwdX * Lt * 0.4F, ry, rz - fwdZ * Lt * 0.4F, Lt * 1.6F, Lt * 1.3F);
        }

        // ---- 尾骨架：每节一个弹簧，越往尾尖越软 ----
        int nsub = Math.max(1, (int) Math.ceil(dt * SUB_HZ));
        float sdt = dt / nsub;
        for (int k = 0; k < NT; k++) {
            float kk = NT == 1 ? 0 : (float) k / (NT - 1) - 0.5F;
            float th = kk * 1.7F * spreadK;
            float ct = (float) Math.cos(th), st = (float) Math.sin(th);
            float oX = -fwdX * ct + rightX * st, oZ = -fwdZ * ct + rightZ * st;
            float sideK = Math.abs(kk) * 2;
            float len = Lt * (1 - 0.12F * sideK);
            tlen[k] = len;
            float ph = t * 2.1F * slow + k * 1.3F;
            float wagA = Lt * (0.06F + 0.1F * amp) * (1 - 0.5F * run);
            for (int i = 0; i <= NS; i++) {
                float f = (float) i / NS;
                // 站着：先往后上翘再往下卷；跑起来往后拉平
                float curl = len * (0.3F * (float) Math.sin(Math.PI * f) - 0.34F * f * f - 0.08F * sideK * f);
                float flat = len * (0.12F * f - 0.05F * f * f);
                float yy = curl + (flat - curl) * (0.75F * run) + len * liftK * f * f;
                float back = len * f * (0.8F + 0.25F * run);
                float wag = wagA * (float) Math.pow(f, 1.4) * (float) Math.sin(ph - f * 2.6F);
                float sd = wag + kk * len * 0.15F * f;
                double tx = rx + oX * back + rightX * sd, ty = ry + yy, tz = rz + oZ * back + rightZ * sd;
                if (i == 0) {
                    px[k][0] = tx;
                    py[k][0] = ty;
                    pz[k][0] = tz;
                    continue;
                }
                FxKit.Spring sp = s.seg[k][i];
                float w0 = 34 - 26 * f, z = 0.55F - 0.15F * f;
                for (int n = 0; n < nsub; n++) {
                    sp.step(tx, ty, tz, sdt, w0, z, m.trackX, m.trackY, m.trackZ);
                }
                px[k][i] = sp.x;
                py[k][i] = sp.y;
                pz[k][i] = sp.z;
            }
            s.tips[k].push(px[k][NS], py[k][NS], pz[k][NS], m.t);
        }

        // ---- 尾身：暗托、外晕、实体柔光、滚动光絮、亮絮、亮芯 ----
        float fu = ((-t * 0.9F * slow) % 1 + 1) % 1;
        for (int k = 0; k < NT; k++) {
            float wk = Wt * tlen[k] / Lt;
            if (!lite) {
                pen.s(FUR).alphaBlend().rgb(deep).ribbon(body(k, wk * 1.4F, 0.45F), EphemeraPen.FACE_CAM, 0, 1);
            }
            pen.s(FUR).rgb(C).ribbon(body(k, wk * 1.9F, 0.35F * A), EphemeraPen.FACE_CAM, 0, 1);
            pen.s(FUR).rgb(C).ribbon(body(k, wk, 0.95F * A), EphemeraPen.FACE_CAM, 0, 1);
            float u0 = mod05(fu + k * 0.13F);
            pen.s(FLOW_0).rgb(C).ribbon(body(k, wk * 1.15F, A), EphemeraPen.FACE_CAM, u0, u0 + 0.5F);
            if (!lite) {
                float u2 = mod05(fu * 2 + k * 0.3F);
                pen.s(FLOW_2).rgb(pale).ribbon(body(k, wk * 0.8F, 0.55F * A), EphemeraPen.FACE_CAM, u2, u2 + 0.5F);
            }
            rib.clear();
            for (int i = 0; i <= NS; i++) {
                float f = (float) i / NS;
                rib.add(px[k][i], py[k][i], pz[k][i], wk * prof(f) * 0.5F + Wt * 0.05F * f, (0.6F + 0.4F * f) * A);
            }
            pen.s(FUR).rgb(hot).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            FxKit.glow(pen, px[k][NS], py[k][NS], pz[k][NS], Wt * 1.4F * (1 + 0.6F * swE), C, WHITE, 0.8F * A);
        }

        // ---- 元素招牌 ----
        signature(fx, m, el, lite, t, C, hot, pale, A, bt);

        // ---- 尾尖一根、尾身一两根凸起；电、毒、磁一根就十几个面片，尾身只留一根 ----
        sites.clear();
        int tipCost = accentCost(el, lite);
        for (int k = 0; k < NT; k++) {
            int ti = NS;
            float tdx = (float) (px[k][ti] - px[k][ti - 1]), tdy = (float) (py[k][ti] - py[k][ti - 1]), tdz = (float) (pz[k][ti] - pz[k][ti - 1]);
            float tl = (float) Math.sqrt(tdx * tdx + tdy * tdy + tdz * tdz) + 1.0E-6F;
            tdx /= tl;
            tdy /= tl;
            tdz /= tl;
            if (left() >= tipCost) {
                fx.accent(px[k][ti], py[k][ti], pz[k][ti], tdx, tdy, tdz, Wt * (1.1F + 0.9F * swE), Wt * 0.55F, 10 + k, 0.85F * A);
            }
            float wk = Wt * tlen[k] / Lt;
            int nMid = lite || heavy ? 1 : 2;
            for (int j = 0; j < nMid; j++) {
                float f = nMid == 1 ? 0.55F : j == 0 ? 0.4F : 0.7F;
                int i = Math.round(f * NS);
                double ox, oy, oz;
                if (el == FIRE) {
                    ox = 0;
                    oy = 1;
                    oz = 0;
                } else if (el == POISON) {
                    ox = 0;
                    oy = -1;
                    oz = 0;
                } else {
                    double gx = px[k][i + 1] - px[k][i - 1], gy = py[k][i + 1] - py[k][i - 1], gz = pz[k][i + 1] - pz[k][i - 1];
                    double gl = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1.0E-9;
                    sideOf(px[k][i], py[k][i], pz[k][i], gx / gl, gy / gl, gz / gl);
                    ox = side[0];
                    oy = side[1];
                    oz = side[2];
                }
                // 精简档里电、毒、磁的尾身凸起放不下，只留尾尖那根
                if (!(lite && heavy) && left() >= tipCost) {
                    float off = wk * prof(f) * 0.4F;
                    fx.accent(px[k][i] + ox * off, py[k][i] + oy * off, pz[k][i] + oz * off, ox, oy, oz,
                            Wt * (0.6F + 0.4F * swE), Wt * 0.35F, 20 + k * 3 + i, 0.7F * A);
                }
                sites.add(px[k][i], py[k][i], pz[k][i], (float) ox, (float) oy, (float) oz);
            }
            sites.add(px[k][ti], py[k][ti], pz[k][ti], tdx, tdy, tdz);
        }

        // ---- 尾尖拖尾：动起来才看得见 ----
        pen.cat = EphemeraStaging.TRAIL;
        int segs = lite ? 5 : 9;
        float tu = mod05(fu);
        for (int k = 0; k < NT; k++) {
            FxTrail tr = s.tips[k];
            float tk = smooth((float) (tr.length() - 0.15F * sc) / (0.5F * sc));
            if (tk <= 0.01F || (!lite && left() < segs)) {
                continue;
            }
            tr.sample(segs, sample);
            if (sample.n < 2) {
                continue;
            }
            rib.clear();
            for (int i = 0; i < sample.n; i++) {
                float q = sample.s[i];
                rib.add(sample.x[i], sample.y[i], sample.z[i], Wt * 0.7F * (1 - q) * m.trailW, 0.7F * tk * (1 - q) * A * m.trailA);
            }
            pen.s(FLOW_0).rgb(C).ribbon(rib, EphemeraPen.FACE_CAM, tu, tu + 0.5F);
        }

        // ---- 身上冒的：元素粒子 + 尾巴甩出来的光絮 ----
        pen.cat = EphemeraStaging.PARTICLE;
        fx.motes(sites, 14 * (0.5F + 0.5F * act), sc);
        FxParticles P = s.pt;
        P.thin = Math.min(1, m.emit);
        s.acc += dt * (3 + 20 * run + 40 * swE + 30 * hurtE) * (lite ? 0.4F : 1);
        float vX = m.velX, vY = m.velY, vZ = m.velZ;
        while (s.acc >= 1) {
            s.acc -= 1;
            int k = Math.min(NT - 1, (int) (rnd() * NT));
            int i = Math.min(NS, 4 + (int) (rnd() * (NS - 3)));
            EphemeraFx.randDir(dir);
            P.spec().pos(px[k][i], py[k][i], pz[k][i])
                    .vel(dir[0] * 0.4F * sc + vX * 0.2F, dir[1] * 0.4F * sc + vY * 0.2F, dir[2] * 0.4F * sc + vZ * 0.2F)
                    .life(rnd(0.5F, 0.9F)).size(Wt * rnd(0.35F, 0.6F), Wt * 0.05F).sprite(SOFTCORE)
                    .rgb(rnd() < 0.5F ? hot : C).a(0.8F).drag(2).spawn();
        }
        P.step(dt, null);
        P.draw(pen, m.size, 1, Float.NaN);
        pen.cat = EphemeraStaging.BASE;
    }

    /** 第 k 条尾巴的中线，宽 = wk × 轮廓，透明度 = a × 根部淡入 */
    private EphemeraPen.Ribbon body(int k, float wk, float a) {
        rib.clear();
        for (int i = 0; i <= ns; i++) {
            float f = (float) i / ns;
            rib.add(px[k][i], py[k][i], pz[k][i], wk * prof(f), a * fade(f));
        }
        return rib;
    }

    private void signature(ElementMotif fx, FxMotion m, int el, boolean lite, float t, int C, int hot, int pale, float A, float bt) {
        int NT = nt, NS = ns;
        float Wt = this.Wt;
        switch (el) {
            case FIRE -> {
                if (lite) {
                    return;
                }
                // 尾身上窜的火舌：每条尾巴背上两簇
                for (int k = 0; k < NT; k++) {
                    for (int j = 0; j < 2 && left() >= 2; j++) {
                        int i = Math.min(NS - 1, 3 + j * 3);
                        fx.accent(px[k][i], py[k][i], pz[k][i], 0, 1, 0, Wt * (0.8F + 0.3F * (float) Math.sin(t * 5 + j + k)), Wt * 0.5F,
                                50 + k * 5 + j, 0.55F * A);
                    }
                }
            }
            case ICE -> {
                // 尾脊上一排硬边冰晶
                float rX = m.rightX, rZ = m.rightZ;
                for (int k = 0; k < NT; k++) {
                    for (int j = 0; j < (lite ? 2 : 4) && left() >= 2; j++) {
                        int i = 2 + j * 2;
                        double gx = px[k][i + 1] - px[k][i - 1], gy = py[k][i + 1] - py[k][i - 1], gz = pz[k][i + 1] - pz[k][i - 1];
                        double gl = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1.0E-9;
                        gx /= gl;
                        gy /= gl;
                        gz /= gl;
                        // 切线 × 身体右侧，再往上抬一点
                        double nx = gy * rZ, ny = gz * rX - gx * rZ + 0.4, nz = -gy * rX;
                        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1.0E-9;
                        float L = Wt * (0.9F + 0.6F * ((j * 7 + k * 3) % 5) / 5);
                        double ax = px[k][i], ay = py[k][i], az = pz[k][i];
                        double tx = ax + nx / nl * L, ty = ay + ny / nl * L, tz = az + nz / nl * L;
                        pen.s(EphemeraSprites.WHITE).rgb(pale).a(0.8F * A);
                        kite(ax, ay, az, tx, ty, tz, Wt * 0.45F, 0.3F);
                        pen.s(EphemeraSprites.WHITE).rgb(WHITE).a(0.85F * A);
                        kite(ax, ay, az, tx, ty, tz, Wt * 0.12F, 0.4F);
                    }
                }
            }
            case ELECTRIC -> {
                // 相邻两条尾巴之间来回打弧
                int q = (int) Math.floor(m.t / 0.06);
                for (int k = 0; k < NT - 1; k++) {
                    if (hash(q * 5 + k) < 0.4F || left() < 12) {
                        continue;
                    }
                    int i = 3 + (int) (hash(q * 3 + k * 7) * (NS - 4));
                    int i2 = Math.min(NS, i + 1);
                    zig(px[k][i], py[k][i], pz[k][i], px[k + 1][i2], py[k + 1][i2], pz[k + 1][i2], 6, Wt * 0.35F, q * 11 + k);
                    pen.s(SOFT).rgb(C).a(0.5F * A).ribbon(zigRibbon(6, Wt * 0.45F), EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(EphemeraSprites.WHITE).rgb(hot).a(A).ribbon(zigRibbon(6, Wt * 0.08F), EphemeraPen.FACE_CAM, 0, 1);
                }
            }
            case IMPACT -> {
                // 每拍尾尖各推出一个冲击圈
                if (bt <= 0.05F) {
                    return;
                }
                float sz = Wt * (1.5F + 4 * (1 - bt));
                for (int k = 0; k < NT && left() >= 1; k++) {
                    pen.s(SHOCK).rgb(pale).a(bt * 0.8F * A).billboard(px[k][NS], py[k][NS], pz[k][NS], sz, sz);
                }
            }
            case MAGNETIC -> {
                if (lite) {
                    return;
                }
                // 两两尾尖之间连一道磁力弧，光珠沿弧流
                for (int k = 0; k < NT - 1 && left() >= 17; k += 2) {
                    double ax = px[k][NS], ay = py[k][NS], az = pz[k][NS], bx = px[k + 1][NS], by = py[k + 1][NS], bz = pz[k + 1][NS];
                    double mx = (ax + bx) / 2, my = (ay + by) / 2 + Wt * 1.6F, mz = (az + bz) / 2;
                    zr.clear();
                    for (int i = 0; i <= 8; i++) {
                        float f = i / 8.0F, g = 1 - f;
                        zr.add(g * g * ax + 2 * g * f * mx + f * f * bx, g * g * ay + 2 * g * f * my + f * f * by,
                                g * g * az + 2 * g * f * mz + f * f * bz, Wt * 0.5F, 1);
                    }
                    pen.s(SOFT).rgb(C).a(0.35F * A).ribbon(zr, EphemeraPen.FACE_CAM, 0, 1);
                    float f = (t * 0.6F + k * 0.3F) % 1;
                    int i = Math.min(7, (int) Math.floor(f * 8));
                    float ff = f * 8 - i;
                    double bx2 = zr.x[i] + (zr.x[i + 1] - zr.x[i]) * ff, by2 = zr.y[i] + (zr.y[i + 1] - zr.y[i]) * ff,
                            bz2 = zr.z[i] + (zr.z[i + 1] - zr.z[i]) * ff;
                    for (int j = 0; j < zr.n; j++) {
                        zr.w[j] = Wt * 0.08F;
                    }
                    pen.s(EphemeraSprites.WHITE).rgb(pale).a(0.85F * A).ribbon(zr, EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(SOFTCORE).rgb(hot).a(0.9F * A).billboard(bx2, by2, bz2, Wt * 0.6F, Wt * 0.6F);
                }
            }
            case RADIATION -> {
                // 尾身上随机咔哒一闪的六边形
                int q = (int) Math.floor(m.t / 0.08);
                for (int k = 0; k < NT && left() >= 1; k++) {
                    if (hash(q * 7 + k) > 0.55F) {
                        int i = 2 + (int) (hash(q * 3 + k * 5) * (NS - 2));
                        pen.s(HEX_0).rgb(hot).a(0.85F * A).billboard(px[k][i], py[k][i], pz[k][i], Wt * 1.1F, Wt * 1.1F, q);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** 菱形片：a 到 tip，最宽处在 mid（0~1）；笔刷先设好 */
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

    /** a 到 b 的折线（n 段），中间各点沿镜头侧向随机偏 ±amp，写进 zx/zy/zz */
    private void zig(double ax, double ay, double az, double bx, double by, double bz, int n, float amp, int seed) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-9;
        sideOf((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2, dx / l, dy / l, dz / l);
        for (int i = 0; i <= n; i++) {
            float f = (float) i / n;
            float j = i == 0 || i == n ? 0 : (hash(seed * 31 + i * 7) - 0.5F) * 2 * amp;
            zx[i] = ax + dx * f + side[0] * j;
            zy[i] = ay + dy * f + side[1] * j;
            zz[i] = az + dz * f + side[2] * j;
        }
    }

    private EphemeraPen.Ribbon zigRibbon(int n, float w) {
        zr.clear();
        for (int i = 0; i <= n; i++) {
            zr.add(zx[i], zy[i], zz[i], w, 1);
        }
        return zr;
    }

    /** dir 在镜头里的侧向（dir × 视线），写进 side */
    private void sideOf(double ax, double ay, double az, double dx, double dy, double dz) {
        double vx = ax - pen.camX(), vy = ay - pen.camY(), vz = az - pen.camZ();
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
}
