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
 * 缠流：几股光流绕身上下盘旋，首端亮、尾端细，带子上挂一幅幕（火往上飘、其余往下垂）；跑起来拉成螺旋拖在身后，
 * 挥砍时其中一股甩出去划过身前再回到轨道，每隔几秒先收紧再撑开。火从脚下往上爬、冰从头顶往下落，电是一串跳动的折线。
 */
public final class CoilStyle extends ProtoStyle<CoilStyle.St> {

    private static final int NS_MAX = 5, NODES_MAX = 10, DECALS_MAX = 10;
    private static final float[] SHED = {10, 16, 10, 10};
    private static final float[] SHED_ALL = {0, 28, 20, 26};

    public static final class St extends ProtoStyle.State {
        boolean ready, lite;
        float sc;
        int ns, kind, tickLast;
        double cx, cy, cz;
        float cvx, cvy, cvz, th, vp, sp, fold, acc, acc2, ph;
        FxTrail[] tr;
        FxTrail ct;
        FxParticles pt;
        final double[] hx = new double[NS_MAX], hy = new double[NS_MAX], hz = new double[NS_MAX];
        final boolean[] hasHead = new boolean[NS_MAX];
        final float[] fPrev = new float[NS_MAX];
        final double[][] nx = new double[NS_MAX][NODES_MAX], ny = new double[NS_MAX][NODES_MAX], nz = new double[NS_MAX][NODES_MAX];
        final int[] nn = new int[NS_MAX];
        final double[] dx = new double[DECALS_MAX], dy = new double[DECALS_MAX], dz = new double[DECALS_MAX];
        final float[] dt0 = new float[DECALS_MAX], dlife = new float[DECALS_MAX], dr0 = new float[DECALS_MAX], dr1 = new float[DECALS_MAX],
                da = new float[DECALS_MAX];
        final int[] dspr = new int[DECALS_MAX];
        final boolean[] dhot = new boolean[DECALS_MAX];
        int nd;
        final ElementMotif fx = new ElementMotif();
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(24);
    private final EphemeraPen.Ribbon body = new EphemeraPen.Ribbon(24);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final FxTrail.Sample sample6 = new FxTrail.Sample();
    private final float[] rd = new float[24];
    private final float[] dir = new float[3];
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private EphemeraStaging out;
    private int from, el;

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
        s.ready = false;
        s.fx.reset();
    }

    private static int strands(FxMotion m) {
        return m.lite ? 3 : m.calm ? 4 : 5;
    }

    private static float trailLife(int kind, boolean lite) {
        // 精简档段数少，带子留得太久会被切成多边形，寿命收短一点
        return (kind == 2 ? 1.7F : 1.25F) * (lite ? 0.82F : 1);
    }

    private void init(St s, FxMotion m) {
        s.ready = true;
        s.lite = m.lite;
        s.sc = m.sc;
        s.ns = strands(m);
        s.kind = m.kind;
        s.cx = m.x;
        s.cy = m.y;
        s.cz = m.z;
        s.cvx = m.trackX;
        s.cvy = m.trackY;
        s.cvz = m.trackZ;
        s.th = s.vp = s.sp = 0;
        float life = trailLife(m.kind, m.lite);
        s.tr = new FxTrail[s.ns];
        for (int i = 0; i < s.ns; i++) {
            s.tr[i] = new FxTrail(life, 3.9F * m.sc, 0.05F * m.sc, 3.2F * m.sc);
            s.hasHead[i] = false;
            s.nn[i] = 0;
            s.fPrev[i] = 0;
        }
        s.ct = new FxTrail(0.75F, 3.6F * m.sc, 0.08F * m.sc, 3 * m.sc);
        s.pt = new FxParticles(m.lite ? 26 : 190);
        s.nd = 0;
        s.acc = s.acc2 = 0;
        s.fold = m.sneak ? 1 : 0;
        s.ph = 0;
        s.tickLast = -1;
    }

    private float burstTime, burstK;
    private boolean calm, lite;

    private void addDecal(St s, float t, double x, double y, double z, float life, float r0, float r1, int sprite, float a, boolean hot) {
        if (calm) {
            life *= burstTime;
            r1 = r0 + (r1 - r0) * burstK;
            a *= 0.8F;
        }
        int cap = lite ? 4 : DECALS_MAX;
        if (s.nd >= cap) {
            for (int k = 1; k < s.nd; k++) {
                moveDecal(s, k, k - 1);
            }
            s.nd--;
        }
        int i = s.nd++;
        s.dx[i] = x;
        s.dy[i] = y;
        s.dz[i] = z;
        s.dt0[i] = t;
        s.dlife[i] = life;
        s.dr0[i] = r0;
        s.dr1[i] = r1;
        s.dspr[i] = sprite;
        s.da[i] = a;
        s.dhot[i] = hot;
    }

    private static void moveDecal(St s, int from, int to) {
        s.dx[to] = s.dx[from];
        s.dy[to] = s.dy[from];
        s.dz[to] = s.dz[from];
        s.dt0[to] = s.dt0[from];
        s.dlife[to] = s.dlife[from];
        s.dr0[to] = s.dr0[from];
        s.dr1[to] = s.dr1[from];
        s.dspr[to] = s.dspr[from];
        s.da[to] = s.da[from];
        s.dhot[to] = s.dhot[from];
    }

    private static float sawEnv(float f) {
        return smooth(f / 0.07F) * (1 - smooth((f - 0.86F) / 0.14F));
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        lite = m.lite;
        calm = m.calm;
        burstTime = m.burstTime;
        burstK = m.burst;
        int NS = strands(m);
        if (!s.ready || s.lite != lite || Math.abs(s.sc - m.sc) > 1.0E-3F || s.ns != NS) {
            init(s, m);
        }
        int kind = m.kind;
        s.fx.begin(pen, m);
        boolean ek = kind == 0;
        out = d.out;
        from = out.pending();
        el = m.el;
        if (s.kind != kind) {
            s.kind = kind;
            for (FxTrail tr : s.tr) {
                tr.clear();
                tr.life = trailLife(kind, lite);
            }
        }
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc;
        int C = m.color, hot = mix(C, WHITE, 0.62F), pale = mix(C, WHITE, 0.3F), deep = mul(C, 0.2F);
        double gy = m.groundY;
        float fwdX = m.fwdX, fwdZ = m.fwdZ, rightX = m.rightX, rightZ = m.rightZ;
        float run = clamp(m.speed / 5.6F, 0, 1.1F), r1 = Math.min(1, run);
        float Bk = m.burst, act = m.act;
        float LIM = 2.35F * sc;
        float SAW = kind == 1 ? 0.6F : kind == 2 ? 0.28F : 0;
        boolean fp = d.firstPersonSelf;
        FxParticles P = s.pt;
        P.thin = m.emit;
        s.fold += ((m.sneak ? 1 : 0) - s.fold) * (1 - (float) Math.exp(-dt * 7));
        float fold = s.fold;

        float jumpE = m.env(m.since(m.tJump), 0.05F, 0.12F, 0.3F);
        float landE = m.env(m.since(m.tLand), 0.03F, 0.08F, 0.45F);
        float hurtE = m.env(m.since(m.tHurt), 0.02F, 0.08F, 0.45F);
        float startE = m.env(m.since(m.tRunStart), 0.05F, 0.1F, 0.4F);
        float sinceSw = m.since(m.tSwing) / m.burstTime;
        float swingW = env0(sinceSw, 0.09F, 0.22F, 0.25F);
        // 每隔几秒：先收紧转快，再一下子撑开
        float ph = (t + 1.4F) % 3.6F;
        float ib = calm ? m.idle : 1, squeeze = env0(ph, 0.3F, 0.05F, 0.08F) * ib, burst = env0(ph - 0.38F, 0.05F, 0.05F, 0.5F) * ib;
        int tick = (int) Math.floor(t / 0.06F);
        boolean newTick = tick != s.tickLast;
        s.tickLast = tick;

        // 盘旋的中心带惯性跟着人：起步拖在后面，急停往前荡过去再回来
        if (m.justRunStart) {
            s.cvx -= fwdX * 2.5F * sc;
            s.cvz -= fwdZ * 2.5F * sc;
        }
        {
            float w0 = 15, z = 0.45F, wy = 22;
            s.cvx += (float) (((m.x - s.cx) * w0 * w0 - (s.cvx - m.trackX) * 2 * z * w0) * dt);
            s.cvz += (float) (((m.z - s.cz) * w0 * w0 - (s.cvz - m.trackZ) * 2 * z * w0) * dt);
            s.cvy += (float) (((m.y - s.cy) * wy * wy - (s.cvy - m.trackY) * 2 * 0.8F * wy) * dt);
            s.cx += s.cvx * dt;
            s.cy += s.cvy * dt;
            s.cz += s.cvz * dt;
            double ox = s.cx - m.x, oy = s.cy - m.y, oz = s.cz - m.z, l = Math.sqrt(ox * ox + oy * oy + oz * oz), mx = 0.8F * sc;
            if (l > mx) {
                s.cx = m.x + ox * mx / l;
                s.cy = m.y + oy * mx / l;
                s.cz = m.z + oz * mx / l;
            }
            if (s.cy < gy) {
                s.cy = gy;
            }
        }
        double cx = s.cx, cy = s.cy, cz = s.cz;

        float R0 = 1.15F * W + 0.1F * H;
        float rK = (1 - 0.3F * fold) * (1 - 0.4F * squeeze + 0.4F * burst) * (1 - 0.42F * jumpE) * (1 + 1.25F * landE * Bk) * (1 + 0.8F * hurtE * Bk)
                * (calm ? 0.88F + 0.08F * act : 1);
        float om = (4.6F + 2.4F * run) * (kind == 2 ? 0.6F : 1) * (1 - 0.45F * fold) * (1 + 0.9F * squeeze + 1.5F * jumpE + 0.6F * startE + 0.5F * swingW);
        s.th += om * dt;
        s.vp += 1.25F * (1 + 2.2F * jumpE) * dt;
        s.sp += SAW * dt;
        float flick = hurtE > 0 ? 0.55F + 0.45F * hash(tick * 5 + 1) : 1;
        float base = (1 - 0.15F * fold) * flick;
        float wHead = (0.38F * H + 0.24F * W) * (1 - 0.3F * fold) * (calm ? 0.68F + 0.1F * act : 1);

        // 托底
        pen.cat = EphemeraStaging.BASE;
        pen.s(SOFT).alphaBlend().rgb(0).a(0.4F).flat(m.x, gy + 0.02, m.z, R0 * 3.2F, R0 * 3.2F);
        if (!fp) {
            pen.s(SOFT).alphaBlend().rgb(0).a(0.24F).billboard(cx, cy + 0.5F * H, cz, R0 * 3.0F, H * 1.35F);
            pen.s(SOFT).rgb(C).a((0.13F + 0.25F * burst + 0.2F * hurtE * Bk) * base * (calm ? 0.7F : 1))
                    .billboard(cx, cy + 0.5F * H, cz, R0 * ((calm ? 2.0F : 2.6F) + 1.2F * burst), H * ((calm ? 1.0F : 1.2F) + 0.3F * burst));
        }
        float disc = R0 * (calm ? 1.9F : 2.4F);
        if (ek) {
            s.fx.ground(m.x, gy, m.z, disc * 0.5F, base * (calm ? 0.75F : 1));
            // 爆发先画，后面股上的凸起按剩下的余量让
            pen.cat = EphemeraStaging.PARTICLE;
            s.fx.events(m.x, gy, m.z, R0 * 1.6F);
            s.fx.bursts();
            pen.cat = EphemeraStaging.BASE;
            sites.clear();
        } else {
            pen.s(SOFTCORE).rgb(C).a(0.3F * base * (calm ? 0.7F : 1)).flat(m.x, gy + 0.035, m.z, disc, disc);
        }

        // 跑起来身后裹一层宽而淡的光鞘，跟着盘旋中心走，转弯时自己带弧
        float sheath = smooth(clamp((m.speed / 5.6F - 0.25F) / 0.6F, 0, 1)) * (1 - 0.5F * fold);
        float scroll = 0.5F * (1 - ((t * (kind == 2 ? 0.3F : 0.9F)) % 1));
        s.ct.push(cx, cy + 0.52F * H * (1 - 0.3F * fold), cz, t);
        if (sheath > 0.02F && !fp) {
            pen.cat = EphemeraStaging.TRAIL;
            int sg = lite ? 6 : 10;
            float wS = 0.75F * H + 0.6F * W;
            s.ct.sample(sg, sample);
            pen.s(BAND).alphaBlend().rgb(deep);
            FxTrail.draw(pen, sample, rib, (x, i) -> wS * (0.8F + 0.5F * x), (x, i) -> 0.4F * sheath * Math.min(1, x * 5) * (1 - x), null,
                    m.trailW, m.trailA, EphemeraPen.FACE_CAM, 0.2F, 0.8F);
            final float fb = base;
            pen.s(FLOW_2).rgb(C);
            FxTrail.draw(pen, sample, rib, (x, i) -> wS * (0.9F + 0.7F * x),
                    (x, i) -> 0.36F * sheath * Math.min(1, x * 5) * (float) Math.pow(1 - x, 1.2) * fb, null,
                    m.trailW, m.trailA, EphemeraPen.FACE_CAM, scroll, scroll + 0.5F);
            if (!lite) {
                pen.s(FLOW_1).rgb(pale);
                FxTrail.draw(pen, sample, rib, (x, i) -> wS * (0.5F + 0.4F * x), (x, i) -> 0.22F * sheath * Math.min(1, x * 5) * (1 - x) * fb, null,
                        m.trailW, m.trailA, EphemeraPen.FACE_CAM, 0.5F - scroll, 1 - scroll);
            }
            pen.cat = EphemeraStaging.BASE;
        }

        // 每一股
        int swingIdx = m.swingCount % NS, swingSide = m.swingCount % 2 != 0 ? 1 : -1;
        int tex = kind == 2 ? FLOW_1 : FLOW_0;
        for (int i = 0; i < NS; i++) {
            float phi = (float) i / NS * TAU;
            float th = s.th + phi;
            float r = R0 * rK * (1 + 0.12F * (float) Math.sin(2 * th + i)), y, env = 1;
            float f = SAW > 0 ? frac(s.sp + (float) i / NS) : 0;
            if (kind == 1) {
                y = H * (0.04F + 1.08F * f) + 0.25F * sc * f;
                env = sawEnv(f);
                r *= 1.12F - 0.35F * f;
            } else if (kind == 2) {
                y = H * (1.08F - 1.05F * f);
                env = sawEnv(f);
                r *= 0.8F + 0.45F * f;
            } else {
                y = H * (0.5F + 0.44F * (float) Math.sin(s.vp + phi + i * 0.6F));
            }
            if (hurtE > 0) {
                r *= 1 + 0.5F * (noise(t * 9, i * 7) - 0.5F) * hurtE;
                y += H * 0.3F * (noise(t * 8, i * 13 + 2) - 0.5F) * hurtE;
            }
            y = y * (1 - 0.5F * fold) * (1 - landE) + 0.1F * H * landE;
            r = Math.min(r, LIM);
            double hx = cx + Math.cos(th) * r, hy = cy + y, hz = cz + Math.sin(th) * r;
            float thick = 1;
            boolean swung = i == swingIdx && swingW > 0;
            if (swung) {
                // 被甩出去的这一股：绕到身前划半圈，再并回轨道
                float e = smooth(Math.min(1, sinceSw / 0.3F)), a = swingSide * (-2.1F + 4.2F * e);
                float sr = Math.min(LIM, 2.1F * R0) * (calm ? 0.75F : 1), ca = (float) Math.cos(a) * sr, sa = (float) Math.sin(a) * sr;
                double ax = m.x + fwdX * ca + rightX * sa, ay = m.y + H * (0.86F - 0.5F * e), az = m.z + fwdZ * ca + rightZ * sa;
                hx += (ax - hx) * swingW;
                hy += (ay - hy) * swingW;
                hz += (az - hz) * swingW;
                thick = 1 + 0.7F * swingW * Bk;
                env = Math.max(env, swingW);
            }
            if (hy < gy + 0.06) {
                hy = gy + 0.06;
            }
            boolean had = s.hasHead[i];
            double ox = s.hx[i], oy = s.hy[i], oz = s.hz[i];
            float hvx = had ? (float) ((hx - ox) / dt) : 0, hvy = had ? (float) ((hy - oy) / dt) : 0, hvz = had ? (float) ((hz - oz) / dt) : 0;
            s.hx[i] = hx;
            s.hy[i] = hy;
            s.hz[i] = hz;
            s.hasHead[i] = true;
            FxTrail tr = s.tr[i];
            tr.maxLen = (3.9F + 3 * r1) * sc;
            tr.push(hx, hy, hz, t);

            // 冰：流落到地面时结一圈霜，溅几片碎冰
            if (kind == 2 && had && s.fPrev[i] > 0.8F && f < 0.2F) {
                addDecal(s, t, ox, gy + 0.04, oz, 0.9F, W * 0.6F, W * 3.2F, SOFTRING, 0.8F, false);
                for (int k = 0; k < (lite ? 2 : 5); k++) {
                    EphemeraFx.randDir(dir);
                    P.spec().pos(ox, gy + 0.1F * sc, oz).vel(dir[0] * 1.2F * sc, dir[1] * 1.2F * sc + 1.5F * sc, dir[2] * 1.2F * sc)
                            .life(rnd(0.5F, 0.9F)).size(rnd(0.08F, 0.14F) * sc).sprite(CRYSTAL).rgb(mix(C, WHITE, 0.5F)).grav(8 * sc)
                            .rot(rnd(0, 6.28F)).spin(rnd(-6, 6)).floor(gy + 0.02).fade(0.02F, 0.3F).spawn();
                }
            }
            s.fPrev[i] = f;

            if (!fp) {
                if (kind == 3) {
                    drawZap(s, i, hx, hy, hz, tick, newTick, thick, wHead, base, scroll, C, hot, deep, sc, m);
                } else {
                    drawFlow(s, i, f, SAW, swung, swingW, hx, hy, hz, thick, wHead, base, scroll, tex, env, kind, C, hot, pale, deep, H, sc, fold, r1, t);
                }
            }
            if (ek) {
                double ox2 = hx - cx, oz2 = hz - cz, ol = Math.max(1.0E-6, Math.sqrt(ox2 * ox2 + oz2 * oz2));
                sites.add(hx, hy, hz, (float) (ox2 / ol), 0, (float) (oz2 / ol));
                FxTrail trm = s.tr[i];
                if (trm.points() > 6) {
                    int pi = Math.min(trm.points() - 1, 5);
                    double px = trm.x(pi), pz = trm.z(pi), qx = px - cx, qz = pz - cz, ql = Math.max(1.0E-6, Math.sqrt(qx * qx + qz * qz));
                    sites.add(px, trm.y(pi), pz, (float) (qx / ql), 0, (float) (qz / ql));
                }
                continue;
            }

            // 沿途抖落
            float rate = SHED[kind] * (lite ? 0.25F : 1) * env * (1 - 0.5F * fold);
            if (kind == 3 && noise(t * 3 + i, 9) < 0.5F) {
                rate = 0;
            }
            if (rnd() < rate * dt) {
                EphemeraFx.randDir(dir);
                if (kind == 1) {
                    if (rnd() < 0.5F) {
                        P.spec().pos(hx, hy, hz).vel(hvx * 0.15F, hvy * 0.15F + rnd(0.6F, 1.4F) * sc, hvz * 0.15F).life(rnd(0.4F, 0.7F))
                                .size(0.26F * sc, 0.6F * sc).sprite(SMOKE_0 + (int) (rnd() * 4)).rgb(C).a(0.28F).rot(rnd(0, 6.28F)).fade(0.15F, 0.6F).spawn();
                    } else {
                        P.spec().pos(hx, hy, hz).vel(hvx * 0.2F + dir[0] * 0.6F * sc, hvy * 0.2F + rnd(1.2F, 2.4F) * sc + dir[1] * 0.6F * sc, hvz * 0.2F + dir[2] * 0.6F * sc)
                                .life(rnd(0.6F, 1.1F)).size(0.07F * sc, 0.02F * sc).sprite(SOFTCORE).rgb(hot).grav(-1.2F * sc).drag(1).stretch(0.05F).spawn();
                    }
                } else if (kind == 2) {
                    if (rnd() < 0.65F) {
                        P.spec().pos(hx, hy, hz).vel(hvx * 0.15F + dir[0] * 0.4F * sc, hvy * 0.15F + dir[1] * 0.4F * sc, hvz * 0.15F + dir[2] * 0.4F * sc)
                                .life(rnd(0.8F, 1.3F)).size(rnd(0.07F, 0.13F) * sc, 0.04F * sc).sprite(CRYSTAL).rgb(mix(C, WHITE, 0.55F)).grav(5 * sc)
                                .drag(0.6F).rot(rnd(0, 6.28F)).spin(rnd(-4, 4)).floor(gy + 0.02).fade(0.05F, 0.3F).spawn();
                    } else {
                        P.spec().pos(hx, hy, hz).vel(0, -0.35F * sc, 0).life(rnd(1.0F, 1.5F)).size(0.3F * sc, 0.7F * sc).sprite(SMOKE_0 + (int) (rnd() * 4))
                                .alphaBlend().rgb(mix(C, WHITE, 0.6F)).a(0.24F).grav(0.6F * sc).drag(1.2F).floor(gy + 0.02).rot(rnd(0, 6.28F))
                                .fade(0.25F, 0.6F).spawn();
                    }
                } else if (kind == 3) {
                    float k = rnd(2.5F, 6) * sc;
                    P.spec().pos(hx, hy, hz).vel(dir[0] * k, dir[1] * k, dir[2] * k).life(rnd(0.1F, 0.24F)).size(0.06F * sc, 0.03F * sc)
                            .sprite(STREAK).rgb(hot).stretch(0.07F).drag(3).fade(0.02F, 0.5F).spawn();
                } else if (rnd() < 0.35F) {
                    P.spec().pos(hx, hy, hz).vel(hvx * 0.12F, hvy * 0.12F, hvz * 0.12F).life(rnd(0.5F, 0.8F)).size(0.24F * sc, 0.55F * sc)
                            .sprite(SMOKE_0 + (int) (rnd() * 4)).rgb(C).a(0.2F).rot(rnd(0, 6.28F)).fade(0.15F, 0.6F).spawn();
                } else {
                    float jx = dir[0] * 0.08F * sc, jy = dir[1] * 0.08F * sc, jz = dir[2] * 0.08F * sc;
                    EphemeraFx.randDir(dir);
                    P.spec().pos(hx + jx, hy + jy, hz + jz).vel(hvx * 0.25F + dir[0] * 0.5F * sc, hvy * 0.25F + dir[1] * 0.5F * sc, hvz * 0.25F + dir[2] * 0.5F * sc)
                            .life(rnd(0.6F, 1.1F)).size(rnd(0.07F, 0.12F) * sc, 0.02F * sc).sprite(SOFTCORE).rgb(rnd() < 0.3F ? hot : C).drag(1.6F).spawn();
                }
            }
        }

        if (ek && !fp) {
            s.fx.motes(sites, (28 + 24 * r1) * (1 - 0.5F * fold) * (calm ? 0.5F + 0.5F * act : 1), sc);
        }

        // 抖落的频率按「每股每秒」算太稀，这里再按总量补一遍
        s.acc += dt * SHED_ALL[kind] * (lite ? 0.25F : 1) * (1 - 0.5F * fold) * (calm ? 0.5F + 0.5F * act : 1);
        while (s.acc >= 1) {
            s.acc -= 1;
            FxTrail tr = s.tr[(int) (rnd() * NS)];
            if (tr.points() < 4) {
                continue;
            }
            int pi = (int) (rnd() * Math.min(tr.points(), 24));
            double px = tr.x(pi), py = tr.y(pi), pz = tr.z(pi);
            EphemeraFx.randDir(dir);
            if (kind == 3) {
                if (noise(t * 3.3F, 2) > 0.5F) {
                    float k = rnd(2, 5) * sc;
                    P.spec().pos(px, py, pz).vel(dir[0] * k, dir[1] * k, dir[2] * k).life(rnd(0.1F, 0.22F)).size(0.06F * sc, 0.03F * sc)
                            .sprite(STREAK).rgb(hot).stretch(0.07F).drag(3).fade(0.02F, 0.5F).spawn();
                }
            } else {
                float jx = dir[0] * 0.06F * sc, jy = dir[1] * 0.06F * sc, jz = dir[2] * 0.06F * sc;
                EphemeraFx.randDir(dir);
                float up = kind == 1 ? rnd(0.6F, 1.6F) * sc : kind == 2 ? -rnd(0.3F, 0.9F) * sc : 0;
                FxParticles.Spec sp = P.spec().pos(px + jx, py + jy, pz + jz).vel(dir[0] * 0.35F * sc, dir[1] * 0.35F * sc + up, dir[2] * 0.35F * sc)
                        .life(rnd(0.5F, 1.0F)).size(rnd(0.06F, 0.11F) * sc, 0.02F * sc).sprite(SOFTCORE).rgb(rnd() < 0.3F ? hot : C).drag(1.2F);
                if (kind == 2) {
                    sp.floor(gy + 0.02);
                }
                sp.spawn();
            }
        }

        // 跑动时各股身后留下大团的淡烟，把几道流线之间填起来
        if (run > 0.3F && !ek) {
            s.acc2 += dt * (lite ? 4 : 22) * r1 * (calm ? 0.5F : 1);
            while (s.acc2 >= 1) {
                s.acc2 -= 1;
                int hi = (int) (rnd() * NS);
                EphemeraFx.randDir(dir);
                P.spec().pos(s.hx[hi] + dir[0] * 0.1F * sc, s.hy[hi] + dir[1] * 0.1F * sc, s.hz[hi] + dir[2] * 0.1F * sc)
                        .vel(m.velX * 0.25F, m.velY * 0.25F + (kind == 1 ? 0.7F : kind == 2 ? -0.3F : 0.1F) * sc, m.velZ * 0.25F).life(rnd(0.45F, 0.7F))
                        .size(0.4F * sc, 0.95F * sc).sprite(SMOKE_0 + (int) (rnd() * 4)).rgb(C).a(kind == 3 ? 0.14F : 0.2F).rot(rnd(0, 6.28F))
                        .fade(0.2F, 0.6F).spawn();
            }
        }

        // 电：时不时从一股劈到另一股，或者劈到地上
        if (kind == 3 && !fp) {
            for (int k = 0; k < (lite ? 2 : 5); k++) {
                if (hash(tick * 41 + k * 7) > 0.5F) {
                    continue;
                }
                int ai = Math.min(NS - 1, (int) Math.floor(hash(tick * 43 + k) * NS));
                double ax = s.hx[ai], ay = s.hy[ai], az = s.hz[ai], bx, by, bz;
                boolean toGround = hash(tick * 47 + k) < 0.4F;
                if (toGround) {
                    bx = ax + (hash(tick * 53 + k) - 0.5F) * sc;
                    by = gy + 0.05;
                    bz = az + (hash(tick * 59 + k) - 0.5F) * sc;
                } else {
                    int bi = Math.min(NS - 1, (int) Math.floor(hash(tick * 61 + k) * NS));
                    bx = s.hx[bi];
                    by = s.hy[bi];
                    bz = s.hz[bi];
                }
                float len = (float) Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
                if (len < 0.2F * sc) {
                    continue;
                }
                int spr = k % 2 != 0 ? CRACK_0 : CRACK_1;
                if (!lite) {
                    pen.s(spr).alphaBlend().rgb(mul(C, 0.38F)).a(0.55F * base).beam(ax, ay, az, bx, by, bz, len * 0.6F);
                }
                pen.s(spr).rgb(hot).a(0.9F * base).beam(ax, ay, az, bx, by, bz, len * 0.45F);
                if (toGround) {
                    pen.s(SOFTCORE).rgb(hot).a(0.9F * base).flat(bx, by, bz, 0.7F * sc, 0.7F * sc);
                }
            }
        }

        // 涌动：撑开的一瞬
        if (s.ph < 0.38F && ph >= 0.38F) {
            addDecal(s, t, cx, cy + 0.5F * H, cz, 0.55F, R0 * 1.2F, Math.min(LIM, R0 * 2.6F) * 2, SOFTRING, calm ? 0.45F : 0.9F, true);
            for (int k = 0; k < (lite ? 5 : 16); k++) {
                float a = k / 16.0F * TAU + rnd(), ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                P.spec().pos(cx + ca * R0 * 0.8F, cy + H * rnd(0.2F, 0.9F), cz + sa * R0 * 0.8F).vel(ca * 3.2F * sc, rnd(-0.5F, 0.8F) * sc, sa * 3.2F * sc)
                        .life(rnd(0.3F, 0.5F)).size(0.1F * sc, 0.03F * sc).sprite(COMET).rgb(k % 3 != 0 ? C : hot).stretch(0.1F).drag(3.5F).force().spawn();
            }
        }
        s.ph = ph;

        // 动作
        if (m.justRunStart) {
            for (int k = 0; k < (lite ? 5 : 14); k++) {
                EphemeraFx.randDir(dir);
                double px = cx + dir[0] * 0.4F * W, py = cy + H * rnd(0.2F, 0.9F) + dir[1] * 0.4F * W, pz = cz + dir[2] * 0.4F * W;
                EphemeraFx.randDir(dir);
                float b = -rnd(2.5F, 6) * sc;
                P.spec().pos(px, py, pz).vel(fwdX * b + dir[0] * 1.2F * sc, dir[1] * 1.2F * sc, fwdZ * b + dir[2] * 1.2F * sc).life(rnd(0.3F, 0.55F))
                        .size(0.1F * sc, 0.03F * sc).sprite(COMET).rgb(hot).stretch(0.1F).drag(2.5F).force().spawn();
            }
        }
        if (m.justStop) {
            for (int k = 0; k < (lite ? 5 : 14); k++) {
                int hi = k % NS;
                EphemeraFx.randDir(dir);
                double px = s.hx[hi] + dir[0] * 0.1F * sc, py = s.hy[hi] + dir[1] * 0.1F * sc, pz = s.hz[hi] + dir[2] * 0.1F * sc;
                EphemeraFx.randDir(dir);
                float b = rnd(2, 5) * sc;
                P.spec().pos(px, py, pz).vel(fwdX * b + dir[0] * 1.2F * sc, dir[1] * 1.2F * sc, fwdZ * b + dir[2] * 1.2F * sc).life(rnd(0.3F, 0.55F))
                        .size(0.1F * sc, 0.03F * sc).sprite(COMET).rgb(hot).stretch(0.1F).drag(3).force().spawn();
            }
        }
        if (m.justJump) {
            addDecal(s, t, m.x, gy + 0.045, m.z, 0.5F, R0, Math.min(LIM, R0 * 2.2F) * 2, SHOCK, 0.95F, true);
        }
        if (m.justLand) {
            float pw = clamp(m.landSpeed / (8 * (float) Math.sqrt(sc)), 0.7F, 1.5F);
            addDecal(s, t, m.x, gy + 0.045, m.z, 0.55F, R0, Math.min(LIM, R0 * 2.4F * pw) * 2, SHOCK, 1, true);
            for (int k = 0; k < (lite ? 6 : 16); k++) {
                float a = (k + rnd()) / 16 * TAU, ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                P.spec().pos(m.x + ca * R0, gy + 0.1F * sc, m.z + sa * R0).vel(ca * 3 * sc * pw, rnd(0.4F, 1.6F) * sc, sa * 3 * sc * pw)
                        .life(rnd(0.35F, 0.6F)).size(0.11F * sc, 0.03F * sc).sprite(COMET).rgb(k % 3 != 0 ? C : hot).stretch(0.1F).drag(3)
                        .grav(4 * sc).floor(gy + 0.02).force().spawn();
            }
        }
        if (m.justSwing) {
            int hi = swingIdx;
            for (int k = 0; k < (lite ? 3 : 8); k++) {
                EphemeraFx.randDir(dir);
                float b = swingSide * rnd(2, 5) * sc;
                P.spec().pos(s.hx[hi], s.hy[hi], s.hz[hi]).vel(rightX * b + dir[0] * 1.5F * sc, dir[1] * 1.5F * sc, rightZ * b + dir[2] * 1.5F * sc)
                        .life(rnd(0.2F, 0.4F)).size(0.09F * sc, 0.03F * sc).sprite(COMET).rgb(hot).stretch(0.09F).drag(3).force().spawn();
            }
        }
        if (m.justHurt) {
            double ccy = cy + 0.5F * H;
            for (int k = 0; k < (lite ? 8 : 24); k++) {
                int hi = k % NS;
                double ex = s.hx[hi] - cx, ey = s.hy[hi] - ccy, ez = s.hz[hi] - cz, el = Math.max(1.0E-6, Math.sqrt(ex * ex + ey * ey + ez * ez));
                EphemeraFx.randDir(dir);
                double px = s.hx[hi] + dir[0] * 0.15F * sc, py = s.hy[hi] + dir[1] * 0.15F * sc, pz = s.hz[hi] + dir[2] * 0.15F * sc;
                EphemeraFx.randDir(dir);
                float b = rnd(2.5F, 6) * sc;
                P.spec().pos(px, py, pz).vel((float) (ex / el) * b + dir[0] * 1.5F * sc, (float) (ey / el) * b + dir[1] * 1.5F * sc,
                                (float) (ez / el) * b + dir[2] * 1.5F * sc).life(rnd(0.25F, 0.5F)).size(0.1F * sc, 0.03F * sc).sprite(COMET)
                        .rgb(k % 2 != 0 ? WHITE : hot).stretch(0.09F).drag(4).force().spawn();
            }
        }
        if (hurtE > 0 && !fp) {
            float g = H * 1.7F * hurtE * Bk;
            pen.s(FLARE).rgb(WHITE).a(0.75F * hurtE).billboard(cx, cy + 0.55F * H, cz, g, g);
        }

        // 平着的圈
        for (int i = s.nd - 1; i >= 0; i--) {
            float k = (t - s.dt0[i]) / s.dlife[i];
            if (k >= 1 || k < 0) {
                for (int j = i + 1; j < s.nd; j++) {
                    moveDecal(s, j, j - 1);
                }
                s.nd--;
                continue;
            }
            float e = 1 - (1 - k) * (1 - k), a = s.da[i] * (float) Math.pow(1 - k, 1.4), r = s.dr0[i] + (s.dr1[i] - s.dr0[i]) * e;
            pen.s(s.dspr[i]).alphaBlend().rgb(deep).a(a * 0.85F).flat(s.dx[i], s.dy[i], s.dz[i], r * 1.03F, r * 1.03F);
            pen.s(s.dspr[i]).rgb(s.dhot[i] ? pale : C).a(a).flat(s.dx[i], s.dy[i] + 0.005, s.dz[i], r, r);
        }

        P.step(dt, null);
        // 第一人称：股头就在眼睛这一圈打转，抖落的粒子贴着镜头飞，不画
        if (!fp) {
            pen.cat = EphemeraStaging.PARTICLE;
            P.draw(pen, m.size, 1, Float.NaN);
        }
    }

    private void drawZap(St s, int i, double hx, double hy, double hz, int tick, boolean newTick, float thick, float wHead, float base,
                         float scroll, int C, int hot, int deep, float sc, FxMotion m) {
        // 电：不走连续的带子，每 0.06 秒记一个折点，折点之间用闪电段连起来
        double[] nx = s.nx[i], ny = s.ny[i], nz = s.nz[i];
        int cap = lite ? 8 : NODES_MAX;
        if (newTick) {
            int n = Math.min(s.nn[i] + 1, cap);
            for (int k = n - 1; k > 0; k--) {
                nx[k] = nx[k - 1];
                ny[k] = ny[k - 1];
                nz[k] = nz[k - 1];
            }
            EphemeraFx.randDir(dir);
            nx[0] = hx + dir[0] * 0.16F * sc;
            ny[0] = hy + dir[1] * 0.16F * sc;
            nz[0] = hz + dir[2] * 0.16F * sc;
            s.nn[i] = n;
        } else if (s.nn[i] > cap) {
            s.nn[i] = cap;
        }
        int n = s.nn[i];
        if (n > 0) {
            nx[0] = hx;
            ny[0] = hy;
            nz[0] = hz;
        }
        boolean dark = hash(tick * 11 + i * 3) < 0.18F;
        FxTrail tr = s.tr[i];
        int segs = lite ? 6 : 10;
        tr.sample(segs, sample);
        pen.cat = EphemeraStaging.TRAIL;
        pen.s(BAND).alphaBlend().rgb(deep);
        FxTrail.draw(pen, sample, rib, (x, k) -> wHead * 0.9F * (1 - 0.7F * x), (x, k) -> 0.55F * (1 - x), null, m.trailW, m.trailA,
                EphemeraPen.FACE_CAM, 0.2F, 0.8F);
        if (!lite) {
            pen.s(FLOW_1).rgb(C);
            FxTrail.draw(pen, sample, rib, (x, k) -> wHead * 1.1F * (1 - 0.6F * x), (x, k) -> 0.75F * (1 - x) * base * (dark ? 0.3F : 1), null,
                    m.trailW, m.trailA, EphemeraPen.FACE_CAM, scroll, scroll + 0.5F);
        }
        pen.cat = EphemeraStaging.BASE;
        for (int k = 0; k + 1 < n; k++) {
            double ax = nx[k], ay = ny[k], az = nz[k], bx = nx[k + 1], by = ny[k + 1], bz = nz[k + 1];
            float fa = 1 - (float) k / n;
            float len = (float) Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
            if (len < 0.02F * sc || len > 3 * sc) {
                continue;
            }
            float on = hash(tick * 17 + i * 29 + k * 7) > 0.2F && !dark ? 1 : 0.2F;
            boolean alt = (k + i + (tick >> 1)) % 2 != 0;
            pen.s(alt ? CRACK_0 : CRACK_1).rgb(hot).a(fa * on * base).beam(ax, ay, az, bx, by, bz, Math.max(len * 0.5F, 0.2F * sc) * thick);
            pen.s(STREAK).alphaBlend().rgb(deep).a(0.7F * fa).beam(ax, ay, az, bx, by, bz, wHead * 0.6F);
            pen.s(STREAK).rgb(C).a(0.6F * fa * on * base).beam(ax, ay, az, bx, by, bz, wHead * 0.55F);
            if (!lite) {
                pen.s(alt ? CRACK_1 : CRACK_0).rgb(C).a(0.7F * fa * on * base).beam(bx, by, bz, ax, ay, az, Math.max(len * 0.7F, 0.3F * sc) * thick);
            }
        }
        pen.s(FLARE).rgb(WHITE).a((dark ? 0.3F : 1) * base).billboard(hx, hy, hz, wHead * 0.9F * thick, wHead * 0.9F * thick, (float) m.t * 2 + i);
        pen.s(SOFTCORE).rgb(C).a(0.7F * base).billboard(hx, hy, hz, wHead * 1.2F, wHead * 1.2F);
    }

    private void drawFlow(St s, int i, float f, float SAW, boolean swung, float swingW, double hx, double hy, double hz, float thick,
                          float wHead, float base, float scroll, int tex, float env, int kind, int C, int hot, int pale, int deep,
                          float H, float sc, float fold, float r1, float t) {
        int seg = lite ? 12 : 18;
        float wP = 0.6F - 0.25F * r1, aP = 0.9F - 0.4F * r1;
        FxTrail tr = s.tr[i];
        tr.sample(seg, sample);
        int n = sample.n;
        if (n > 1) {
            float veilH = (kind == 1 ? 0.46F : kind == 2 ? 0.42F : 0.36F) * H * (1 - 0.4F * fold) * (calm ? 0.55F : 1);
            float up = kind == 1 ? 1 : -1;
            body.clear();
            for (int k = 0; k < n; k++) {
                float x = sample.s[k];
                float wob = kind == 1 ? 0.8F + 0.4F * noise(sample.dist[k] / (0.3F * sc) - t * 7, i) : 1;
                float w = wHead * thick * (float) Math.pow(1 - x, wP) * (0.5F + 0.5F * Math.min(1, x * 8)) * wob;
                float a = (float) Math.pow(1 - x, aP) * envAt(f, SAW, swung, swingW, sample.age[k], x) * base * (calm ? 0.85F : 1);
                body.add(sample.x[k], sample.y[k], sample.z[k], w, a, mix(pale, C, Math.min(1, x * 2.5F)));
                rd[k] = sample.dist[k];
            }
            // 路径拐急弯的地方把带子收窄，不然宽带一翻面就是一块尖角
            for (int k = 1; k + 1 < n; k++) {
                double e0x = body.x[k] - body.x[k - 1], e0y = body.y[k] - body.y[k - 1], e0z = body.z[k] - body.z[k - 1];
                double e1x = body.x[k + 1] - body.x[k], e1y = body.y[k + 1] - body.y[k], e1z = body.z[k + 1] - body.z[k];
                double l0 = Math.sqrt(e0x * e0x + e0y * e0y + e0z * e0z), l1 = Math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
                if (l0 < 1e-5 || l1 < 1e-5) {
                    continue;
                }
                float cs = (float) ((e0x * e1x + e0y * e1y + e0z * e1z) / (l0 * l1));
                if (cs < 0.55F) {
                    body.w[k] *= Math.max(0.2F, (cs + 0.3F) / 0.85F);
                }
            }
            rib.clear();
            for (int k = 0; k < n; k++) {
                rib.add(body.x[k], body.y[k], body.z[k], body.w[k] * 1.05F, Math.min(1, body.a[k]));
            }
            pen.s(BAND).alphaBlend().rgb(deep).ribbon(rib, EphemeraPen.FACE_CAM, 0.2F, 0.8F);
            if (kind == 0) {
                motifEdge(s, i, n, wHead * thick);
            }
            // 带子上挂一幅幕：火往上飘，其余往下垂
            float us = 0.5F * ((t * 0.35F) % 1);
            int vs = lite ? 2 : 1;
            float span = 3.9F * sc;
            for (int k = 0; k + 1 < n && kind != 0; k += vs) {
                int b = Math.min(k + vs, n - 1);
                float aA = body.a[k], aB = body.a[b];
                if (aA <= 0.01F && aB <= 0.01F) {
                    continue;
                }
                float xA = sample.s[k], xB = sample.s[b];
                float hA = veilH * (0.55F + 0.9F * noise(rd[k] / (0.45F * sc) + t * 1.7F, i + 3)) * (float) Math.sin(Math.PI * Math.min(1, xA * 1.5F));
                float hB = veilH * (0.55F + 0.9F * noise(rd[b] / (0.45F * sc) + t * 1.7F, i + 3)) * (float) Math.sin(Math.PI * Math.min(1, xB * 1.5F));
                float uA = us + 0.5F * rd[k] / span, uB = us + 0.5F * rd[b] / span;
                pen.s(tex).rgb(C).a(calm ? 0.6F : 1).va(aA, aB, aB, aA).uv4(uA, 0.5F, uB, 0.5F, uB, 1, uA, 1)
                        .quad(body.x[k], body.y[k], body.z[k], body.x[b], body.y[b], body.z[b],
                                body.x[b], body.y[b] + up * hB, body.z[b], body.x[k], body.y[k] + up * hA, body.z[k]);
            }
            pen.s(tex).ribbon(body, EphemeraPen.FACE_CAM, scroll, scroll + 0.5F);
            if (!lite) {
                rib.clear();
                for (int k = 0; k < n; k++) {
                    rib.add(body.x[k], body.y[k], body.z[k], body.w[k] * 0.16F, body.a[k] * (1 - 0.6F * sample.s[k]));
                }
                pen.s(BAND).rgb(hot).ribbon(rib, EphemeraPen.FACE_CAM, 0.2F, 0.8F);
            }
        }
        float ha = env * base;
        pen.s(SOFT).alphaBlend().rgb(deep).a(0.6F * ha).billboard(hx, hy, hz, wHead * 1.25F * thick, wHead * 1.25F * thick);
        pen.s(SOFTCORE).rgb(mix(C, WHITE, 0.15F)).a(ha).billboard(hx, hy, hz, wHead * 1.1F * thick, wHead * 1.1F * thick);
        if (!lite) {
            pen.s(FLARE).rgb(WHITE).a(0.8F * ha).billboard(hx, hy, hz, wHead * 0.8F * thick, wHead * 0.8F * thick, t * 1.5F + i);
        }
        // 冰：带子上冻着一粒粒冰晶，跟带子一起留在原地
        if (kind == 2 && !lite) {
            tr.sample(6, sample6);
            for (int k = 1; k < sample6.n - 1; k++) {
                float x = sample6.s[k];
                float a = (float) Math.pow(1 - x, 0.8) * envAt(f, SAW, swung, swingW, sample6.age[k], x) * base, sz = wHead * (0.55F - 0.3F * x);
                pen.s(CRYSTAL).rgb(mix(C, WHITE, 0.4F)).a(0.9F * a).billboard(sample6.x[k], sample6.y[k], sample6.z[k], sz * 0.55F, sz * 1.3F, i + k * 1.3F);
            }
        }
    }

    // 光流外缘交给元素母题：沿带子取几点往外长一截。方向按离盘旋轴往外算，不用切线 × 视线，
    // 股是绕着人转的，切线方向一圈下来会翻面，那样有一半时间往身体里长
    private static final float[] EDGE_AT = {0.1F, 0.28F, 0.48F}, EDGE_AT_LITE = {0.22F};

    private void motifEdge(St s, int strand, int n, float wHead) {
        float[] at = lite ? EDGE_AT_LITE : EDGE_AT;
        for (int j = 0; j < at.length; j++) {
            // 磁力一根就要十几个面片，挥砍时爆发也在画，按这一帧已经写了多少让
            if (!lite && out.pending() - from > (j == 0 ? 350 : 310)) {
                return;
            }
            int k = Math.min(n - 1, Math.round(at[j] * (n - 1)));
            float a = body.a[k];
            if (a <= 0.02F) {
                continue;
            }
            double px = body.x[k], py = body.y[k], pz = body.z[k];
            double ox = px - s.cx, oz = pz - s.cz, ol = Math.sqrt(ox * ox + oz * oz);
            if (ol < 1.0E-4) {
                continue;
            }
            ox /= ol;
            oz /= ol;
            boolean poison = el == FxMotion.POISON;
            double oy = poison ? -1.6 : el == FxMotion.RADIATION ? 0.5 : 0.25;
            double l = Math.sqrt(ox * ox + oy * oy + oz * oz);
            float w = wHead * (0.32F - 0.12F * j / at.length);
            s.fx.accent(px, py, pz, ox / l, oy / l, oz / l, w * (poison ? 2.2F : 3), w, strand * 13 + j * 5, a * (calm ? 0.85F : 1));
        }
    }

    private static float envAt(float f, float SAW, boolean swung, float swingW, float age, float x) {
        if (SAW <= 0) {
            return 1;
        }
        float fb = f - age * SAW;
        boolean old = fb < 0;
        if (old) {
            fb += 1;
        }
        float e = sawEnv(fb);
        // 甩出去的那股整条提亮，但从头顶跳回脚下的那一截连线不能跟着亮
        return swung && !old ? Math.max(e, swingW * (1 - x) * smooth(fb / 0.07F)) : e;
    }
}
