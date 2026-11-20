package pers.roinflam.kuvalich.client.ephemera;

import java.util.ArrayList;
import java.util.List;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;

/**
 * 踏涌：脚下一片旋转的能量池，池沿一圈光舌，两股细流盘到腰间；走过的地面留下余痕和矮幕，
 * 落地、挥砍时光舌沿地面成排升起。火往上窜、冰一根根长出冰晶再碎掉、电从池沿劈上来。
 */
public final class SurgeStyle extends ProtoStyle<SurgeStyle.St> {

    static final class Decal {
        double x, y, z;
        float life, r0, r1, len, wid, rot, a, t0;
        int sprite;
        boolean hot, white;
    }

    static final class Twig {
        double x, y, z;
        float t0, life, k, seed;
    }

    static final class Slot {
        float ang, born, life, k, rr;
    }

    public static final class St extends ProtoStyle.State {
        boolean lite;
        float sc;
        int nt;
        double cx, cz;
        float cvx, cvz;
        FxParticles pt;
        FxTrail trail;
        final List<Decal> dec = new ArrayList<>();
        final List<Twig> tw = new ArrayList<>();
        Slot[] slots;
        float acc, acc3, fold;
        int step;
        double twAt;
        int twSide = 1;
        float ph;
        boolean ready;
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(32);
    private final EphemeraPen.Ribbon rib2 = new EphemeraPen.Ribbon(32);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final float[] dir = new float[3];

    // 这一帧的量
    private FxMotion m;
    private St st;
    private boolean lite, calm;
    private int kind, C, hot, pale, deep;
    private float H, W, sc, t, R, Hb, LIM, base, fold;
    private double gx, gy, gz;
    private float camRX, camRZ;
    private int tick;

    @Override
    public float extent() {
        return 2.6F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion mo) {
        s.ready = false;
    }

    private void init(St s, FxMotion mo, int nt) {
        s.ready = true;
        s.lite = mo.lite;
        s.sc = mo.sc;
        s.nt = nt;
        s.cx = mo.x;
        s.cz = mo.z;
        s.cvx = s.cvz = 0;
        s.pt = new FxParticles(mo.lite ? 40 : 250);
        s.trail = new FxTrail(1.0F, 3.8F * mo.sc, 0.08F * mo.sc, 3 * mo.sc);
        s.dec.clear();
        s.tw.clear();
        s.slots = new Slot[nt];
        float t0 = (float) mo.t;
        for (int i = 0; i < nt; i++) {
            Slot sl = new Slot();
            sl.ang = (i + 0.3F * (float) Math.sin(i * 12.9F)) / nt * TAU;
            sl.born = t0 - 0.6F - hash(i * 7 + 1) * 1.5F;
            sl.life = 1.6F + hash(i * 3 + 9) * 1.2F;
            sl.k = 0.6F + 0.4F * hash(i * 5 + 2);
            sl.rr = 0.86F + 0.2F * hash(i * 11);
            s.slots[i] = sl;
        }
        s.acc = s.acc3 = 0;
        s.fold = mo.sneak ? 1 : 0;
        s.step = (int) Math.floor(mo.walkPhase / Math.PI);
        s.twAt = mo.walkDist;
        s.twSide = 1;
        s.ph = 0;
    }

    @Override
    protected void frame(St s, FxMotion mo, EphemeraDraw d) {
        m = mo;
        st = s;
        lite = mo.lite;
        calm = mo.calm;
        kind = mo.kind;
        H = mo.H;
        W = mo.W;
        sc = mo.sc;
        t = (float) mo.t;
        float dt = mo.dt;
        C = mo.color;
        hot = mix(C, WHITE, 0.62F);
        pale = mix(C, WHITE, 0.3F);
        deep = mul(C, 0.2F);
        gx = mo.x;
        gy = mo.groundY;
        gz = mo.z;
        float B = mo.burst, act = mo.act;
        float run = EphemeraFx.clamp(mo.speed / 5.6F, 0, 1.1F);
        int NT = calm ? (lite ? 5 : 10) : (lite ? 7 : 14);
        LIM = 2.4F * sc * (calm ? 0.75F : 1);
        if (!s.ready || s.lite != lite || Math.abs(s.sc - sc) > 1.0E-3F || s.nt != NT) {
            init(s, mo, NT);
        }
        FxParticles P = s.pt;
        P.thin = calm ? mo.emit : Math.min(1, mo.emit);
        s.fold += ((mo.sneak ? 1 : 0) - s.fold) * (1 - (float) Math.exp(-dt * 7));
        fold = s.fold;

        float landE = mo.env(mo.since(mo.tLand), 0.02F, 0.06F, 0.45F);
        float hurtIn = mo.env(mo.since(mo.tHurt), 0.02F, 0.04F, 0.12F);
        float hurtOut = mo.env(mo.since(mo.tHurt) - 0.1F, 0.08F, 0.08F, 0.35F);
        float hurtE = Math.max(hurtIn, hurtOut);
        final float PERIOD = 3.3F;
        float ph = (t + 0.8F) % PERIOD;
        float surge = EphemeraFx.env0(ph, 0.1F, 0.5F, 0.4F) * (calm ? mo.idle : 1);
        float waveAng = ph / 0.75F * TAU;
        tick = (int) Math.floor(t / 0.07F);

        // 池心带点惯性跟着脚走：起步落在后面，急停往前荡一下
        if (mo.justRunStart) {
            s.cvx += mo.fwdX * -2.2F * sc;
            s.cvz += mo.fwdZ * -2.2F * sc;
        }
        {
            float w0 = 17, zt = 0.5F;
            s.cvx += (float) (((gx - s.cx) * w0 * w0 - (s.cvx - mo.trackX) * 2 * zt * w0) * dt);
            s.cvz += (float) (((gz - s.cz) * w0 * w0 - (s.cvz - mo.trackZ) * 2 * zt * w0) * dt);
            s.cx += s.cvx * dt;
            s.cz += s.cvz * dt;
            double ox = s.cx - gx, oz = s.cz - gz, l = Math.sqrt(ox * ox + oz * oz), mx = 0.7 * sc;
            if (l > mx) {
                s.cx = gx + ox * mx / l;
                s.cz = gz + oz * mx / l;
            }
            if (mo.fresh) {
                s.cx = gx;
                s.cz = gz;
            }
        }
        R = Math.min(1.6F * sc, 1.2F * W + 0.24F * H) * (1 - 0.4F * fold) * (calm ? 0.8F + 0.06F * act : 1)
                * (1 + 0.1F * surge + (0.35F * landE - 0.35F * hurtIn + 0.3F * hurtOut) * B);
        float flick = kind == 3 ? (hash(tick * 17) > 0.15F ? 1 : 0.5F) : 1;
        base = (1 - 0.15F * fold) * flick * (hurtE > 0 ? 0.6F + 0.4F * hash(tick * 5) : 1);
        float scroll = 0.5F * ((t * (kind == 2 ? 0.12F : 0.45F)) % 1);
        double cx = s.cx, cz = s.cz;
        boolean firstPerson = d.firstPersonSelf;

        pen.cat = EphemeraStaging.BASE;
        // ---------- 池 ----------
        pen.s(SOFT).alphaBlend().rgb(0).a(0.5F).flat(cx, gy + 0.02, cz, R * 3.1F, R * 3.1F);
        pen.s(SOFTCORE).rgb(C).a(0.4F * base).flat(cx, gy + 0.035, cz, R * 2.3F, R * 2.3F);
        if (!lite && !firstPerson) {
            pen.s(SOFT).rgb(C).a(0.2F * base).billboard(cx, gy + 0.22 * H, cz, R * 2.4F, 0.6F * H);
        }
        int N = lite ? 16 : 22;
        float rot = t * (kind == 2 ? 0.25F : 0.9F);
        ring(N, rot, true, R * 0.75F, 0.04F, BAND, false, deep, 0.85F, 0.2F, 0.8F);
        ring(N, rot, true, R * 0.62F, 0.045F, kind == 2 ? FLOW_1 : FLOW_0, true, C, base, scroll, scroll + 1);
        if (!lite) {
            ringConst(14, -rot * 1.7F, R * 0.52F, R * 0.5F, 0.05F, FLOW_2, pale, 0.8F * base, scroll, scroll + 0.5F);
            // 三条旋臂，从池心甩到池沿
            for (int j = 0; j < 3; j++) {
                rib.clear();
                for (int k = 0; k <= 7; k++) {
                    float f = k / 7.0F, a = -rot * 1.3F + j * TAU / 3 + f * 2.4F;
                    float sn = (float) Math.sin(Math.PI * f);
                    rib.add(cx + Math.cos(a) * R * (0.12 + 0.95 * f), gy + 0.055, cz + Math.sin(a) * R * (0.12 + 0.95 * f),
                            R * 0.34F * (float) Math.pow(sn, 0.7), 0.75F * sn * base * (1 - 0.6F * fold));
                }
                pen.s(BAND).rgb(hot).ribbon(rib, EphemeraPen.FACE_FLAT, 0.2F, 0.8F);
            }
        }
        if (kind == 3) {
            // 电：池面上铺一张一直在的电网，隔两拍换一次纹路，不跟着落雷一起灭
            int NG = lite ? 3 : 7, t2 = tick >> 1;
            float gb = (1 - 0.15F * fold) * (0.75F + 0.25F * flick);
            ringConst(lite ? 10 : 16, -rot * 0.6F, R * 0.78F, R * 1.15F, 0.052F, FLOW_1, hot, 0.95F * gb, scroll * 2, scroll * 2 + 1.5F);
            for (int j = 0; j < NG; j++) {
                float a = (float) j / NG * TAU + rot * 0.35F + (hash(t2 * 13 + j) - 0.5F) * 0.5F;
                float len = Math.min(LIM * 0.95F, R * (1.5F + 0.9F * hash(t2 * 17 + j)));
                pen.s((j + t2) % 2 != 0 ? CRACK_0 : CRACK_1).rgb(j % 2 != 0 ? hot : C).a((0.6F + 0.4F * hash(tick * 19 + j)) * gb)
                        .flat(cx + Math.cos(a) * len * 0.5, gy + 0.056, cz + Math.sin(a) * len * 0.5, len, len * 0.42F, -a);
            }
        }

        // ---------- 立起来的片：光舌 / 冰晶 ----------
        float crx = pen.rightX, crz = pen.rightZ, cl = (float) Math.sqrt(crx * crx + crz * crz);
        camRX = cl > 1.0E-4F ? crx / cl : 1;
        camRZ = cl > 1.0E-4F ? crz / cl : 0;
        Hb = (0.4F * H + 0.1F * W) * (1 - 0.68F * fold) * (1 + 0.15F * run) * (calm ? 0.58F + 0.2F * act : 1);
        float windX = mo.velX * -0.035F, windY = mo.velY * -0.035F, windZ = mo.velZ * -0.035F;
        boolean shatterAll = mo.justLand || mo.justHurt;
        boolean hitLand = mo.justLand;
        for (int i = 0; i < NT; i++) {
            Slot sl = s.slots[i];
            float dAng = (sl.ang - waveAng) % TAU;
            if (dAng > Math.PI) {
                dAng -= TAU;
            }
            if (dAng < -Math.PI) {
                dAng += TAU;
            }
            float bump = ph < 1.5F ? (float) Math.exp(-dAng * dAng / 0.5F) * surge : 0;
            float ox = (float) Math.cos(sl.ang), oz = (float) Math.sin(sl.ang);
            float tx = -oz, tz = ox;
            double bx = cx + ox * R * sl.rr, by = gy + 0.03, bz = cz + oz * R * sl.rr;
            if (kind == 2) {
                // 冰：一根根长出来，立一阵，碎掉，换个位置再长
                float age = t - sl.born;
                float h = Hb * 1.15F * sl.k * smooth(age / 0.35F) * (1 + 0.5F * bump);
                if (age > sl.life || shatterAll) {
                    if (age > 0.2F) {
                        shatter(bx, by, bz, h, lite ? 2 : 4);
                    }
                    sl.born = t + rnd(0, 0.25F);
                    sl.life = rnd(1.4F, 2.8F);
                    sl.ang += rnd(0.4F, 1.4F);
                    sl.k = rnd(0.6F, 1.0F);
                } else if (age > 0) {
                    tongueAt(bx, by, bz, h, ox * h * 0.38F, 0, oz * h * 0.38F, base, i, 1);
                }
            } else if (kind == 3) {
                // 电：每 0.07 秒换一批，从池沿劈上来
                float r = hash(tick * 29 + i * 13);
                if (r < (calm ? 0.4F : 0.6F) + 0.4F * bump + 0.4F * landE * B) {
                    float a = hash(tick * 31 + i) * TAU, h = Hb * (0.8F + 1.3F * hash(tick * 37 + i));
                    float rr = R * (0.5F + 0.6F * hash(tick * 41 + i));
                    double ax = cx + Math.cos(a) * rr, ay = gy + 0.04, az = cz + Math.sin(a) * rr;
                    float back = -h * 0.35F * hash(tick * 43 + i);
                    bolt(ax, ay, az, ax + Math.cos(a) * back, ay + h, az + Math.sin(a) * back, base);
                }
            } else {
                float f = kind == 1 ? 5.5F : 2.4F;
                float n1 = noise(t * f + i * 7.3F, 3), n2 = noise(t * f * 0.7F + i * 3.1F, 8) - 0.5F;
                float h = Hb * sl.k * (0.5F + 0.95F * n1) * (1 + 1.4F * bump + (1.5F * landE + 0.8F * hurtOut) * B);
                float ko = h * (0.12F + 0.5F * hurtOut), kt = h * (0.18F + 0.3F * n2);
                tongueAt(bx, by, bz, h, ox * ko + tx * kt + windX * h, windY * h, oz * ko + tz * kt + windZ * h, base, i, 1);
            }
        }

        // 两股从池沿盘上来的流，绕着腿收到腰间（第一人称不画，低头会糊在眼前）
        if (!firstPerson) {
            int NV = lite || (calm && act < 0.5F) ? 1 : 2, seg = lite ? 8 : 12;
            float top = 0.62F * H * (1 - 0.55F * fold) * (1 + 0.25F * surge) * (calm ? 0.8F : 1);
            float spin = t * (kind == 2 ? -1.1F : 3.4F), dirY = kind == 2 ? -1 : 1;
            float us = 0.5F * ((t * (kind == 2 ? 0.3F : 1.1F)) % 1);
            for (int j = 0; j < NV; j++) {
                if (kind == 3 && hash(tick * 7 + j * 3) < 0.3F) {
                    continue;
                }
                rib.clear();
                for (int k = 0; k <= seg; k++) {
                    float f = (float) k / seg, a = spin * dirY + j * (float) Math.PI + f * 4.6F;
                    float jag = kind == 3 ? (hash(tick * 19 + j * 31 + k) - 0.5F) * 0.25F * R : 0;
                    float rr = R * (0.95F - 0.5F * f) + jag, wk = f * f * 3 * H;
                    float w = R * (0.5F - 0.3F * f) * Math.min(1, f * 5 + 0.3F) * (calm ? 0.7F : 1);
                    float al = (float) Math.sin(Math.PI * Math.min(1, f * 1.15F + 0.08F)) * base * (calm ? 0.75F : 1);
                    rib.add(cx + Math.cos(a) * rr + windX * wk, gy + 0.05 + f * top + windY * wk, cz + Math.sin(a) * rr + windZ * wk,
                            w, al, mix(C, pale, f));
                }
                pen.s(BAND).alphaBlend().rgb(deep).ribbon(rib2.scaled(rib, 1.25F, 0.7F), EphemeraPen.FACE_CAM, 0.2F, 0.8F);
                float u0 = dirY > 0 ? 0.5F - us : us;
                pen.s(kind == 2 ? FLOW_1 : FLOW_0).ribbon(rib, EphemeraPen.FACE_CAM, u0, u0 + 0.5F);
                if (!lite) {
                    pen.s(BAND).rgb(hot).ribbon(rib2.scaled(rib, 0.22F, 0.9F), EphemeraPen.FACE_CAM, 0.2F, 0.8F);
                }
            }
        }

        // ---------- 身后地上的余痕 ----------
        FxTrail tr = s.trail;
        tr.life = kind == 1 ? 1.5F : 1.0F;
        tr.maxLen = (kind == 1 ? 5.2F : 3.8F) * sc;
        tr.push(gx, gy + 0.04, gz, t);
        if (tr.length() > 0.3 * sc) {
            pen.cat = EphemeraStaging.TRAIL;
            float tw = W * (kind == 1 ? 2.7F : 1.9F) * (1 - 0.35F * fold), nar = kind == 1 ? 0.3F : 0.55F;
            int seg = lite ? 8 : 14;
            FxTrail.Sample pts = tr.sample(seg, sample);
            float tW = mo.trailW, tA = mo.trailA, bs = base;
            pen.s(BAND).alphaBlend().rgb(deep);
            FxTrail.draw(pen, pts, rib, (x, i) -> tw * 1.2F * (1 - nar * x), (x, i) -> 0.8F * (float) Math.pow(1 - x, 0.7) * head(x),
                    null, tW, tA, EphemeraPen.FACE_FLAT, 0.2F, 0.8F);
            pen.s(kind == 2 ? FLOW_1 : FLOW_0).rgb(C);
            FxTrail.draw(pen, pts, rib, (x, i) -> tw * (1 - nar * x),
                    (x, i) -> Math.min(1, 1.6F * (1 - x)) * bs * fl(pts.dist[i]) * head(x),
                    null, tW, tA, EphemeraPen.FACE_FLAT, scroll, scroll + 1);
            // 余痕上立一道矮幕：下半截埋在地里，露出来的是从地面往上淡出去的那一半
            float wallH = (0.34F * H + 0.1F * W) * (kind == 1 ? 1.6F : 1) * (1 - 0.6F * fold) * (calm ? 0.55F : 1);
            float us = 0.5F * ((t * (kind == 2 ? 0.1F : 0.5F)) % 1);
            float nk = kind == 2 ? 0.3F : 2.5F;
            for (int k = 0; k + 1 < pts.n; k++) {
                float sA = pts.s[k], sB = pts.s[k + 1];
                float hA = wallH * (float) Math.pow(1 - sA, 1.1) * head(sA) * (0.6F + 0.8F * noise(pts.dist[k] / (0.5F * sc) - t * nk, 6));
                float hB = wallH * (float) Math.pow(1 - sB, 1.1) * head(sB) * (0.6F + 0.8F * noise(pts.dist[k + 1] / (0.5F * sc) - t * nk, 6));
                float aA = (1 - sA) * head(sA) * fl(pts.dist[k]), aB = (1 - sB) * head(sB) * fl(pts.dist[k + 1]);
                float uA = us + 0.5F * pts.dist[k] / (3.8F * sc), uB = us + 0.5F * pts.dist[k + 1] / (3.8F * sc);
                double ax = pts.x[k], ay = pts.y[k], az = pts.z[k], bx = pts.x[k + 1], by = pts.y[k + 1], bz = pts.z[k + 1];
                if (!lite) {
                    pen.s(BAND).alphaBlend().rgb(deep).a(0.6F).va(aA, aB, aB, aA).uv(0.2F, 0, 0.8F, 1)
                            .quad(ax, ay + hA, az, bx, by + hB, bz, bx, by - hB, bz, ax, ay - hA, az);
                }
                pen.s(kind == 2 ? FLOW_1 : FLOW_0).rgb(C).a(0.95F * base * tA).va(aA, aB, aB, aA).uv4(uA, 0, uB, 0, uB, 1, uA, 1)
                        .quad(ax, ay + hA, az, bx, by + hB, bz, bx, by - hB, bz, ax, ay - hA, az);
            }
            if (!lite) {
                pen.s(BAND).rgb(hot);
                FxTrail.draw(pen, pts, rib, (x, i) -> tw * 0.22F * (1 - x), (x, i) -> 0.9F * (1 - x) * bs * fl(pts.dist[i]) * head(x),
                        null, tW, tA, EphemeraPen.FACE_FLAT, 0.2F, 0.8F);
            }
            pen.cat = EphemeraStaging.BASE;
        }
        // 沿路留下一根根立着的：火是烧剩的火苗，冰是冒出来的冰晶，电是补劈的一下
        if (mo.onGround && mo.speed > 1.5F && mo.walkDist - s.twAt > (kind == 1 ? 0.2F : 0.36F) * sc / Math.max(0.05F, mo.emit)) {
            s.twAt = mo.walkDist;
            s.twSide = -s.twSide;
            int cap = kind == 1 ? (lite ? 6 : 20) : kind == 3 ? (lite ? 5 : 14) : (lite ? 4 : 12);
            while (s.tw.size() >= cap) {
                s.tw.remove(0);
            }
            boolean fire = kind == 1;
            Twig w = new Twig();
            float side = s.twSide * W * (fire ? rnd(0, 0.8F) : rnd(0.25F, 0.6F));
            w.x = gx + mo.rightX * side;
            w.y = gy + 0.03;
            w.z = gz + mo.rightZ * side;
            w.t0 = t;
            w.life = new float[]{0.85F, 1.2F, 1.25F, 0.5F}[kind];
            w.k = fire ? rnd(1.1F, 1.9F) : rnd(0.9F, 1.4F);
            w.seed = rnd() * 100;
            s.tw.add(w);
        }
        // 火：烧过的地面一路冒烟，夹着往上窜的火星
        if (kind == 1 && mo.onGround && mo.speed > 1.5F && tr.points() > 3) {
            s.acc3 += dt * (lite ? 6 : 34);
            while (s.acc3 >= 1) {
                s.acc3 -= 1;
                int pi = (int) (rnd() * tr.points());
                double px = tr.x(pi) + rnd(-0.45F, 0.45F) * W, py = gy + 0.12 * sc, pz = tr.z(pi) + rnd(-0.45F, 0.45F) * W;
                float roll = rnd();
                if (roll < 0.6F) {
                    P.spec().pos(px, py, pz).vel(0, rnd(0.9F, 1.6F) * sc, 0).life(rnd(0.6F, 0.95F)).size(0.4F * sc, 1.0F * sc)
                            .sprite(SMOKE_0 + (int) (rnd() * 4)).rgb(C).a(0.4F).rot(rnd(0, 6.28F)).spin(rnd(-0.6F, 0.6F)).fade(0.2F, 0.6F).spawn();
                } else if (roll < 0.8F) {
                    P.spec().pos(px, py, pz).vel(0, rnd(1.0F, 1.5F) * sc, 0).life(rnd(0.9F, 1.3F)).size(0.35F * sc, 1.0F * sc)
                            .sprite(SMOKE_0 + (int) (rnd() * 4)).alphaBlend().rgb(mix(0x1A1717, C, 0.18F)).a(0.32F).rot(rnd(0, 6.28F))
                            .spin(rnd(-0.5F, 0.5F)).fade(0.25F, 0.6F).spawn();
                } else {
                    EphemeraFx.randDir(dir);
                    P.spec().pos(px, py, pz).vel(dir[0] * 0.5F * sc, rnd(1.8F, 3.2F) * sc + dir[1] * 0.5F * sc, dir[2] * 0.5F * sc)
                            .life(rnd(0.6F, 1.0F)).size(0.07F * sc, 0.02F * sc).sprite(SOFTCORE).rgb(hot).grav(-1.5F * sc).drag(0.8F)
                            .stretch(0.05F).spawn();
                }
            }
        }
        for (int i = s.tw.size() - 1; i >= 0; i--) {
            Twig w = s.tw.get(i);
            float age = t - w.t0, k = age / w.life;
            if (k >= 1) {
                if (kind == 2) {
                    shatter(w.x, w.y, w.z, Hb * w.k, 2);
                }
                s.tw.remove(i);
                continue;
            }
            if (kind == 2) {
                float lean = 0.1F * (float) Math.sin(w.seed) * Hb;
                tongueAt(w.x, w.y, w.z, Hb * 1.05F * w.k * smooth(age / 0.25F), mo.rightX * lean, 0, mo.rightZ * lean,
                        Math.min(1, (1 - k) * 4), w.seed, 1);
            } else if (kind == 3) {
                if (hash(tick + i * 7) > 0.3F) {
                    EphemeraFx.randDir(dir);
                    bolt(w.x, w.y, w.z, w.x + dir[0] * 0.2F * sc, w.y + Hb * 1.2F * w.k + dir[1] * 0.2F * sc, w.z + dir[2] * 0.2F * sc, 1 - k);
                }
            } else {
                float lean = 0.08F * Hb * (float) Math.sin(t * 7 + w.seed);
                tongueAt(w.x, w.y, w.z, Hb * w.k * (0.6F + 0.6F * noise(t * 5 + w.seed, 2)) * (1 - k * k), mo.rightX * lean, 0, mo.rightZ * lean,
                        (1 - k * k) * (kind == 1 ? 0.85F * smooth(age / 0.22F) : 1), w.seed, kind == 1 ? 1.6F : 1);
            }
        }

        // ---------- 粒子 ----------
        float rate = new float[]{50, 64, 44, 30}[kind] * (lite ? 0.17F : 1) * (1 - 0.65F * fold) * (1 + 0.8F * surge) * (calm ? 0.5F + 0.5F * act : 1);
        if (kind == 3) {
            rate *= noise(t * 2.7F, 4) > 0.45F ? 1.8F : 0.2F;
        }
        s.acc += dt * rate;
        int nRise = (int) Math.floor(s.acc);
        s.acc -= nRise;
        rise(P, nRise, 1);

        // 涌动开头：一圈往外扩，池里多冒一把
        if (s.ph > ph) {
            addDecal(cx, gy, cz, 0.7F, R * 0.8F, Math.min(LIM, R * 2.1F) * 2, 0, 0, 0, SOFTRING, true, false, 0.8F);
            rise(P, lite ? 4 : 16, 1.5F);
        }
        s.ph = ph;

        // 脚步：每落一步，地上点开一小圈
        int stepN = (int) Math.floor(mo.walkPhase / Math.PI);
        if (stepN != s.step) {
            s.step = stepN;
            if (mo.onGround && mo.speed > 1) {
                float side = (stepN % 2 != 0 ? 1 : -1) * 0.3F * W;
                double fx = gx + mo.rightX * side, fy = gy + 0.05, fz = gz + mo.rightZ * side;
                addDecal(fx, fy, fz, 0.4F, W * 0.5F, W * 2.0F, 0, 0, 0, SOFTRING, false, false, 0.8F);
                if (!lite) {
                    for (int k = 0; k < 4; k++) {
                        EphemeraFx.randDir(dir);
                        P.spec().pos(fx, fy, fz).vel(dir[0] * sc, rnd(1.5F, 3) * sc + dir[1] * sc, dir[2] * sc).life(rnd(0.3F, 0.5F))
                                .size(0.08F * sc, 0.03F * sc).sprite(COMET).rgb(hot).stretch(0.1F).grav(6 * sc).spawn();
                    }
                }
            }
        }

        // ---------- 动作 ----------
        if (mo.justRunStart) {
            addDecal(gx, gy, gz, 0.45F, R, Math.min(LIM, R * 1.6F) * 2, 0, 0, 0, SHOCK, true, false, 0.8F);
            for (int k = 0; k < (lite ? 6 : 16); k++) {
                EphemeraFx.randDir(dir);
                float b = -rnd(2.5F, 6) * sc, sd = rnd(-1.5F, 1.5F) * sc;
                P.spec().pos(gx + dir[0] * 0.3F * W, gy + 0.1 * sc + dir[1] * 0.3F * W, gz + dir[2] * 0.3F * W)
                        .vel(mo.fwdX * b + mo.rightX * sd, rnd(0.2F, 1.6F) * sc, mo.fwdZ * b + mo.rightZ * sd).life(rnd(0.3F, 0.55F))
                        .size(0.1F * sc, 0.03F * sc).sprite(COMET).rgb(hot).stretch(0.1F).drag(2.5F).force().spawn();
            }
        }
        if (mo.justStop) {
            float r0 = (float) Math.atan2(mo.fwdZ, mo.fwdX);
            for (int k = 0; k < 3; k++) {
                float sd = (k - 1) * 0.4F * W;
                addDecal(gx + mo.fwdX * R * 0.9F + mo.rightX * sd, gy + 0.05, gz + mo.fwdZ * R * 0.9F + mo.rightZ * sd,
                        0.5F, 0, 0, R * 1.8F, W * 0.6F, r0, STREAK, true, false, 0.9F);
            }
            for (int k = 0; k < (lite ? 6 : 14); k++) {
                float sd = rnd(-0.5F, 0.5F) * W, f = rnd(2.5F, 5.5F) * sc, sv = rnd(-1.2F, 1.2F) * sc;
                P.spec().pos(gx + mo.rightX * sd, gy + 0.08 * sc, gz + mo.rightZ * sd)
                        .vel(mo.fwdX * f + mo.rightX * sv, rnd(0.2F, 1.4F) * sc, mo.fwdZ * f + mo.rightZ * sv).life(rnd(0.3F, 0.55F))
                        .size(0.1F * sc, 0.03F * sc).sprite(COMET).rgb(hot).stretch(0.1F).drag(3).grav(4 * sc).floor(gy + 0.02).force().spawn();
            }
        }
        if (mo.justJump) {
            addDecal(gx, gy, gz, 0.5F, R, Math.min(LIM, R * 2.2F) * 2, 0, 0, 0, SHOCK, true, false, 1);
            for (int k = 0; k < (lite ? 7 : 18); k++) {
                float a = rnd(0, TAU), rr = R * rnd(0.2F, 0.7F);
                P.spec().pos(cx + Math.cos(a) * rr, gy + 0.06, cz + Math.sin(a) * rr)
                        .vel((float) Math.cos(a) * 0.6F * sc, rnd(4, 8.5F) * (float) Math.sqrt(sc) * sc, (float) Math.sin(a) * 0.6F * sc)
                        .life(rnd(0.35F, 0.6F)).size(0.12F * sc, 0.04F * sc).sprite(COMET).rgb(k % 3 != 0 ? C : hot).stretch(0.12F)
                        .drag(1.5F).force().spawn();
            }
        }
        // 腾空时地上的池子用一道光柱连着脚底
        if (mo.heightAboveGround > 0.08F * sc) {
            float k = EphemeraFx.clamp(mo.heightAboveGround / (0.5F * sc), 0, 1);
            double ay = gy + 0.05;
            pen.s(BAND).alphaBlend().rgb(deep).a(0.6F * k).uv(0.2F, 0, 0.8F, 1).beam(cx, ay, cz, mo.x, mo.y, mo.z, W * (calm ? 0.95F : 1.5F));
            pen.s(FLOW_1).rgb(C).a(0.9F * k * base).uv(scroll, 0, scroll + 0.3F, 1).beam(cx, ay, cz, mo.x, mo.y, mo.z, W * (calm ? 0.8F : 1.3F));
            pen.s(BAND).rgb(hot).a(0.7F * k * base).uv(0.2F, 0, 0.8F, 1).beam(cx, ay, cz, mo.x, mo.y, mo.z, W * 0.5F);
        }
        if (hitLand) {
            float pw = EphemeraFx.clamp(mo.landSpeed / (8 * (float) Math.sqrt(sc)), 0.7F, 1.5F);
            float r1 = Math.min(LIM, R * 1.9F * pw) * 2;
            addDecal(gx, gy, gz, 0.55F, R, r1, 0, 0, 0, SHOCK, true, false, 1);
            addDecal(gx, gy, gz, 0.45F, R * 0.6F, r1 * 0.8F, 0, 0, 0, SOFTRING, false, false, 0.8F);
            for (int k = 0; k < (lite ? 8 : 22); k++) {
                float a = (k + rnd()) / 22 * TAU;
                P.spec().pos(cx + Math.cos(a) * R * 0.8F, gy + 0.08, cz + Math.sin(a) * R * 0.8F)
                        .vel((float) Math.cos(a) * 2.6F * sc * pw, rnd(2, 4.2F) * sc, (float) Math.sin(a) * 2.6F * sc * pw)
                        .life(rnd(0.4F, 0.65F)).size(0.12F * sc, 0.04F * sc).sprite(kind == 2 ? CRYSTAL : COMET).rgb(k % 3 != 0 ? C : hot)
                        .stretch(kind == 2 ? 0 : 0.11F).grav(11 * sc).spin(6).floor(gy + 0.02).force().spawn();
            }
            if (!lite) {
                for (int k = 0; k < 8; k++) {
                    float a = (k + rnd()) / 8 * TAU;
                    FxParticles.Spec sp = P.spec().pos(cx + Math.cos(a) * R * 0.7F, gy + 0.12 * sc, cz + Math.sin(a) * R * 0.7F)
                            .vel((float) Math.cos(a) * 3 * sc * pw, 0.3F * sc, (float) Math.sin(a) * 3 * sc * pw).life(rnd(0.5F, 0.75F))
                            .size(0.35F * sc, 0.8F * sc).sprite(SMOKE_0 + k % 4).rgb(k % 2 != 0 ? deep : C).a(k % 2 != 0 ? 0.5F : 0.3F)
                            .drag(3.2F).rot(a).fade(0.05F, 0.7F).force();
                    if (k % 2 != 0) {
                        sp.alphaBlend();
                    }
                    sp.spawn();
                }
            }
        }
        // 挥砍：身前地上一道刀波推出去，沿弧线依次炸起一排
        float sinceSw = mo.since(mo.tSwing) / mo.burstTime;
        if (sinceSw < 0.55F) {
            int side = mo.swingCount % 2 != 0 ? 1 : -1;
            float e = smooth(Math.min(1, sinceSw / 0.3F)), fade = 1 - smooth((sinceSw - 0.2F) / 0.3F);
            float rr = Math.min(LIM * 0.92F, R * 1.5F) * (calm ? 0.8F : 1);
            double ccx = gx + mo.fwdX * rr * (0.1 + 0.5 * e), ccy = gy + 0.06, ccz = gz + mo.fwdZ * rr * (0.1 + 0.5 * e);
            float hs = rr * (0.7F + 0.35F * e);
            crescent(ccx, ccy, ccz, hs * 1.05F, false, deep, 0.8F * fade);
            crescent(ccx, ccy, ccz, hs, true, C, fade);
            crescent(ccx, ccy, ccz, hs * 0.94F, true, hot, 0.9F * fade);
            int NW = lite ? 4 : 7;
            for (int k = 0; k < NW; k++) {
                float f = NW > 1 ? (float) k / (NW - 1) : 0.5F, a = side * (-1.05F + 2.1F * f);
                float dx = mo.fwdX * (float) Math.cos(a) + mo.rightX * (float) Math.sin(a);
                float dz = mo.fwdZ * (float) Math.cos(a) + mo.rightZ * (float) Math.sin(a);
                float ev = EphemeraFx.env0(sinceSw - f * 0.16F, 0.05F, 0.04F, 0.26F);
                if (ev <= 0) {
                    continue;
                }
                double bx = gx + dx * rr, by = gy + 0.03, bz = gz + dz * rr;
                float h = 0.75F * H * ev * (0.8F + 0.4F * hash(k * 7 + mo.swingCount)) * (calm ? 0.6F : 1);
                if (kind == 3) {
                    bolt(bx, by, bz, bx + dx * 0.15F * h, by + h, bz + dz * 0.15F * h, ev);
                } else {
                    tongueAt(bx, by, bz, h, dx * h * 0.3F, 0, dz * h * 0.3F, Math.min(1, ev * 1.5F), k, 1);
                }
            }
        }
        if (mo.justSwing && !lite) {
            for (int k = 0; k < 8; k++) {
                float sd = rnd(-1, 1) * R, f = rnd(2, 4.5F) * sc;
                P.spec().pos(gx + mo.fwdX * R + mo.rightX * sd, gy + 0.1 * sc, gz + mo.fwdZ * R + mo.rightZ * sd)
                        .vel(mo.fwdX * f, rnd(1, 3) * sc, mo.fwdZ * f).life(rnd(0.25F, 0.45F)).size(0.09F * sc, 0.03F * sc)
                        .sprite(COMET).rgb(hot).stretch(0.1F).grav(7 * sc).force().spawn();
            }
        }
        if (mo.justHurt) {
            addDecal(gx, gy, gz, 0.4F, R * 0.5F, Math.min(LIM, R * 2) * 2, 0, 0, 0, SHOCK, false, true, 1);
            for (int k = 0; k < (lite ? 8 : 24); k++) {
                float a = rnd(0, TAU), sp = rnd(3.5F, 7) * sc;
                P.spec().pos(cx + Math.cos(a) * R * 0.5F, gy + rnd(0.05F, 0.4F) * H, cz + Math.sin(a) * R * 0.5F)
                        .vel((float) Math.cos(a) * sp, rnd(-0.2F, 1.2F) * sc, (float) Math.sin(a) * sp).life(rnd(0.25F, 0.45F))
                        .size(0.1F * sc, 0.03F * sc).sprite(COMET).rgb(k % 2 != 0 ? WHITE : hot).stretch(0.09F).drag(4.5F).force().spawn();
            }
        }

        // ---------- 贴地的圈和划痕 ----------
        for (int i = s.dec.size() - 1; i >= 0; i--) {
            Decal o = s.dec.get(i);
            float k = (t - o.t0) / o.life;
            if (k >= 1) {
                s.dec.remove(i);
                continue;
            }
            float e = 1 - (1 - k) * (1 - k), a = o.a * (float) Math.pow(1 - k, 1.4);
            int rgb = o.white ? WHITE : o.hot ? pale : C;
            if (o.len > 0) {
                float L = o.len * (0.4F + 0.6F * e);
                pen.s(o.sprite).alphaBlend().rgb(deep).a(a * 0.8F).flat(o.x, o.y, o.z, L, o.wid, o.rot);
                pen.s(o.sprite).rgb(rgb).a(a).flat(o.x, o.y + 0.005, o.z, L, o.wid, o.rot);
            } else {
                float r = o.r0 + (o.r1 - o.r0) * e;
                if (!lite || o.sprite == SHOCK) {
                    pen.s(o.sprite).alphaBlend().rgb(deep).a(a * 0.85F).flat(o.x, o.y, o.z, r * 1.03F, r * 1.03F);
                }
                pen.s(o.sprite).rgb(rgb).a(a).flat(o.x, o.y + 0.005, o.z, r, r);
            }
        }

        // 往上盘的细流绕着身体收拢
        final double pcx = cx, pcz = cz;
        final float vX = mo.velX, vZ = mo.velZ, pullK = 5.5F * sc;
        P.step(dt, (p, i, dtt) -> {
            if (p.data[i] == 1) {
                double dx = pcx - p.x[i], dz = pcz - p.z[i], l = Math.sqrt(dx * dx + dz * dz);
                if (l < 1.0E-6) {
                    l = 1;
                }
                double pull = pullK * dtt / l;
                p.vx[i] += (float) (dx * pull + vX * 1.2F * dtt);
                p.vz[i] += (float) (dz * pull + vZ * 1.2F * dtt);
            }
        });
        pen.cat = EphemeraStaging.PARTICLE;
        P.draw(pen, mo.size, 1, Float.NaN);
        m = null;
        st = null;
    }

    private static float head(float x) {
        return Math.min(1, x * 9);
    }

    private float fl(float dist) {
        return kind == 3 ? (hash(tick * 3 + (int) Math.floor(dist / (0.4F * sc))) > 0.3F ? 1 : 0.15F) : 1;
    }

    /** 圈用一段段四边形自己拼，首尾才接得上；edge 为真时半径沿圈起伏 */
    private void ring(int n, float rot0, boolean edge, float w, float y, int sprite, boolean add, int rgb, float a, float u0, float u1) {
        double cx = st.cx, cz = st.cz;
        for (int k = 0; k < n; k++) {
            float a0 = (float) k / n * TAU, a1 = (float) (k + 1) / n * TAU;
            float r0 = edge ? edge(a0) : R, r1 = edge ? edge(a1) : R;
            float ua = u0 + (u1 - u0) * k / n, ub = u0 + (u1 - u0) * (k + 1) / n;
            double c0 = Math.cos(a0 + rot0), s0 = Math.sin(a0 + rot0), c1 = Math.cos(a1 + rot0), s1 = Math.sin(a1 + rot0);
            pen.s(sprite).rgb(rgb).a(a).uv4(ua, 0, ub, 0, ub, 1, ua, 1);
            if (!add) {
                pen.alphaBlend();
            }
            pen.quad(cx + c0 * (r0 + w / 2), gy + y, cz + s0 * (r0 + w / 2), cx + c1 * (r1 + w / 2), gy + y, cz + s1 * (r1 + w / 2),
                    cx + c1 * (r1 - w / 2), gy + y, cz + s1 * (r1 - w / 2), cx + c0 * (r0 - w / 2), gy + y, cz + s0 * (r0 - w / 2));
        }
    }

    private void ringConst(int n, float rot0, float rad, float w, float y, int sprite, int rgb, float a, float u0, float u1) {
        float keep = R;
        R = rad;
        ring(n, rot0, false, w, y, sprite, true, rgb, a, u0, u1);
        R = keep;
    }

    private float edge(float a) {
        return R * (0.92F + 0.07F * (float) Math.sin(3 * a + t * 2.2F) + 0.05F * (float) Math.sin(5 * a - t * 3.1F));
    }

    private void addDecal(double x, double y, double z, float life, float r0, float r1, float len, float wid, float rot,
                          int sprite, boolean hot, boolean white, float a) {
        Decal o = new Decal();
        o.x = x;
        o.y = y;
        o.z = z;
        o.life = life;
        o.r0 = r0;
        o.r1 = r1;
        o.len = len;
        o.wid = wid;
        o.rot = rot;
        o.sprite = sprite;
        o.hot = hot;
        o.white = white;
        o.a = a;
        if (calm) {
            o.life *= m.burstTime;
            if (len <= 0) {
                o.r1 = o.r0 + (o.r1 - o.r0) * m.burst;
            } else {
                o.len *= m.burst;
            }
            o.a *= 0.8F;
        }
        if (st.dec.size() >= (lite ? 6 : 16)) {
            st.dec.remove(0);
        }
        o.t0 = t;
        st.dec.add(o);
    }

    private void crescent(double x, double y, double z, float hs, boolean add, int rgb, float a) {
        float rx = m.rightX * hs, rz = m.rightZ * hs, fx = m.fwdX * hs, fz = m.fwdZ * hs;
        pen.s(CRESCENT).rgb(rgb).a(a);
        if (!add) {
            pen.alphaBlend();
        }
        pen.quad(x - rx - fx, y, z - rz - fz, x + rx - fx, y, z + rz - fz, x + rx + fx, y, z + rz + fz, x - rx + fx, y, z - rz + fz);
    }

    private void upright(double bx, double by, double bz, float w, float h, float lx, float ly, float lz) {
        double tx = bx + lx, ty = by + h + ly, tz = bz + lz;
        float hx = camRX * w / 2, hz = camRZ * w / 2;
        pen.quad(tx - hx, ty, tz - hz, tx + hx, ty, tz + hz, bx + hx, by, bz + hz, bx - hx, by, bz - hz);
    }

    private void tongueAt(double bx, double by, double bz, float h, float lx, float ly, float lz, float a, float seed, float wk) {
        if (h < 0.04F * sc || a <= 0.01F) {
            return;
        }
        if (kind == 2) {
            float w = h * 0.42F;
            pen.s(CRYSTAL).alphaBlend().rgb(mix(C, WHITE, 0.25F)).a(0.8F * a);
            upright(bx, by, bz, w, h, lx, ly, lz);
            pen.s(CRYSTAL).rgb(C).a(0.55F * a);
            upright(bx, by, bz, w, h, lx, ly, lz);
            if (!lite) {
                pen.s(FLARE).rgb(WHITE).a(0.5F * a * (0.5F + 0.5F * (float) Math.sin(t * 3 + seed * 9)))
                        .billboard(bx + lx * 0.75, by + h * 0.75 + ly * 0.75, bz + lz * 0.75, w * 1.3F, w * 1.3F);
            }
        } else {
            float w = Math.max(h * 0.7F, 0.45F * W) * wk;
            boolean flip = ((int) Math.floor(seed) + (int) Math.floor(t * 3 + seed * 0.37F)) % 2 != 0;
            float u0 = flip ? 1 : 0, u1 = flip ? 0 : 1;
            pen.s(TONGUE).alphaBlend().rgb(deep).a(0.8F * a).uv(u0, 0, u1, 1);
            upright(bx, by, bz, w * 1.25F, h * 1.08F, lx, ly, lz);
            pen.s(TONGUE).rgb(C).a(a).uv(u0, 0, u1, 1);
            upright(bx, by, bz, w, h, lx, ly, lz);
            if (!lite) {
                pen.s(TONGUE).rgb(hot).a(0.9F * a).uv(u0, 0, u1, 1);
                upright(bx, by, bz, w * 0.5F, h * 0.6F, lx * 0.6F, ly * 0.6F, lz * 0.6F);
            }
        }
    }

    private void bolt(double ax, double ay, double az, double bx, double by, double bz, float a) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        pen.s(hash((int) (tick + ax * 91)) > 0.5F ? CRACK_0 : CRACK_1).rgb(hot).a(a).beam(ax, ay, az, bx, by, bz, len * 0.5F);
        pen.s(FLARE).rgb(WHITE).a(0.9F * a).billboard(bx, by, bz, 0.26F * sc, 0.26F * sc);
        pen.s(SOFTCORE).rgb(hot).a(0.8F * a).flat(ax, ay + 0.01, az, 0.5F * sc, 0.5F * sc);
        if (!lite) {
            pen.s(CRACK_1).rgb(C).a(0.9F * a).flat(ax, ay + 0.015, az, 1.3F * sc, 0.65F * sc,
                    hash(tick * 3 + (int) Math.floor(az * 57)) * 6.28F);
        }
    }

    private void shatter(double bx, double by, double bz, float h, int n) {
        FxParticles P = st.pt;
        for (int k = 0; k < n; k++) {
            EphemeraFx.randDir(dir);
            P.spec().pos(bx, by + h * rnd(0.2F, 0.9F), bz).vel(dir[0] * 1.4F * sc, dir[1] * 1.4F * sc + 1.2F * sc, dir[2] * 1.4F * sc)
                    .life(rnd(0.5F, 0.9F)).size(rnd(0.08F, 0.15F) * sc, 0.04F * sc).sprite(CRYSTAL).rgb(mix(C, WHITE, 0.5F))
                    .grav(9 * sc).rot(rnd(0, 6.28F)).spin(rnd(-6, 6)).floor(gy + 0.02).fade(0.02F, 0.3F).spawn();
        }
        EphemeraFx.randDir(dir);
        P.spec().pos(bx, by + h * 0.3F, bz).vel(dir[0] * 0.3F * sc, dir[1] * 0.3F * sc, dir[2] * 0.3F * sc).life(0.9F)
                .size(0.3F * sc, 0.7F * sc).sprite(SMOKE_0 + n % 4).alphaBlend().rgb(mix(C, WHITE, 0.6F)).a(0.3F).floor(gy + 0.02)
                .fade(0.1F, 0.7F).spawn();
    }

    private void rise(FxParticles P, int n, float boost) {
        double cx = st.cx, cz = st.cz;
        for (int i = 0; i < n; i++) {
            float a = rnd(0, TAU), r = R * rnd(0.55F, 1.05F);
            float ox = (float) Math.cos(a), oz = (float) Math.sin(a), tx = -oz, tz = ox;
            double px = cx + ox * r, py = gy + 0.06, pz = cz + oz * r;
            if (kind == 2) {
                // 冰往下沉：细雪从腰间落到地上，寒雾贴着地往外爬
                if (rnd() < 0.72F) {
                    P.spec().pos(px, py + rnd(0.35F, 0.75F) * H, pz).vel(tx * 0.4F * sc, -rnd(0.5F, 1.0F) * sc, tz * 0.4F * sc)
                            .life(rnd(0.9F, 1.4F)).size(rnd(0.04F, 0.08F) * sc).sprite(SOFTCORE).rgb(mix(C, WHITE, 0.6F)).grav(0.6F * sc)
                            .floor(gy + 0.02).fade(0.2F, 0.25F).spawn();
                } else {
                    float sp = rnd(0.4F, 0.9F) * sc;
                    P.spec().pos(px, py, pz).vel(ox * sp, 0, oz * sp).life(rnd(1.1F, 1.6F)).size(0.35F * sc, 0.9F * sc)
                            .sprite(SMOKE_0 + (int) (rnd() * 4)).alphaBlend().rgb(mix(C, WHITE, 0.65F)).a(0.26F).drag(0.8F)
                            .rot(rnd(0, 6.28F)).spin(rnd(-0.4F, 0.4F)).fade(0.25F, 0.6F).floor(gy + 0.02).spawn();
                }
            } else if (kind == 3) {
                EphemeraFx.randDir(dir);
                float sp = rnd(2.5F, 6) * sc * boost;
                P.spec().pos(px, py + rnd(0, 0.5F) * H, pz).vel(dir[0] * sp, dir[1] * sp, dir[2] * sp).life(rnd(0.1F, 0.22F))
                        .size(0.06F * sc, 0.03F * sc).sprite(STREAK).rgb(hot).stretch(0.07F).drag(3).fade(0.02F, 0.5F).spawn();
            } else {
                float up = rnd(1.3F, 2.6F) * sc * boost * (kind == 1 ? 1.25F : 1);
                float vx = tx * 1.9F * sc - ox * 0.3F * sc, vz = tz * 1.9F * sc - oz * 0.3F * sc;
                float roll = rnd();
                if (roll < 0.5F) {
                    P.spec().pos(px, py, pz).vel(vx, up, vz).life(rnd(0.55F, 0.95F)).size(rnd(0.14F, 0.22F) * sc, 0.05F * sc)
                            .sprite(COMET).rgb(roll < 0.15F ? hot : C).stretch(0.16F).fade(0.1F, 0.5F).data(1).spawn();
                } else if (roll < 0.85F) {
                    P.spec().pos(px, py, pz).vel(vx * 0.6F, up * 0.6F, vz * 0.6F).life(rnd(0.5F, 0.8F)).size(0.3F * sc, 0.7F * sc)
                            .sprite(SMOKE_0 + (int) (rnd() * 4)).rgb(C).a(0.3F).rot(rnd(0, 6.28F)).fade(0.2F, 0.6F).data(1).spawn();
                } else {
                    EphemeraFx.randDir(dir);
                    P.spec().pos(px, py, pz).vel(vx + dir[0] * 0.6F * sc, up + dir[1] * 0.6F * sc, vz + dir[2] * 0.6F * sc)
                            .life(rnd(0.8F, 1.3F)).size(0.06F * sc, 0.02F * sc).sprite(SOFTCORE).rgb(hot)
                            .grav(kind == 1 ? -1.5F * sc : 0.5F * sc).drag(0.8F).data(1).spawn();
                }
            }
        }
    }
}
