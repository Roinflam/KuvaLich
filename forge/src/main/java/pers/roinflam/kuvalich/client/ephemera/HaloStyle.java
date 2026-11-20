package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;

/**
 * 傲骨：头后竖着一圈刻满铭文的大光环，外有刻度盘、内有两层铭文盘各自反向转，一段亮光沿铭文绕圈流；
 * 两手腕各套三层环跟着手摆。跑起来光环后仰、身后留残影；挥砍整圈外扩、主手甩出一道铭文光弧；受击光环错位闪烁。
 *
 * <p>画的顺序按预算排：元素爆发和粒子先画，然后光环和腕环的主体，再按剩下的余量画元素招牌、拖光和细节。
 * 精简档只按底座算 150。</p>
 */
public final class HaloStyle extends ProtoStyle<HaloStyle.St> {

    static final int TEX_GLYPH = EphemeraSprites.file("halo/glyph", true);
    static final int TEX_RUNE = EphemeraSprites.file("halo/rune");
    static final int TEX_DIAL = EphemeraSprites.file("halo/dial");

    private static final int HIST = 128;
    private static final float PI = (float) Math.PI;

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        final FxKit.Spring hc = new FxKit.Spring(), wl = new FxKit.Spring(), wr = new FxKit.Spring();
        final FxTrail[] tr = new FxTrail[2];
        FxParticles pt;
        boolean lite;
        float acc, kick, roll;
        // 光环最近一段的位置和两根轴：t, c(3), ax(3), ay(3)
        final double[] hT = new double[HIST];
        final double[] hv = new double[HIST * 9];
        int hHead, hN;
    }

    private static final class Wrist {
        double x, y, z, ux, uy, uz, e1x, e1y, e1z, e2x, e2y, e2z;
        float r, aw, swK, rot;
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(32);
    private final FxTrail.Sample smp = new FxTrail.Sample();
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final Wrist[] wrists = {new Wrist(), new Wrist()};
    private final double[] zx = new double[9], zy = new double[9], zz = new double[9];
    private final double[] lx = new double[13], ly = new double[13], lz = new double[13];

    // 这一帧
    private EphemeraStaging out;
    private boolean lite;
    private int cap, from;
    private double cx, cy, cz, axX, axY, axZ, ayX, ayY, ayZ, nX, nY, nZ;
    private double dX, dY, dZ;
    private ElementMotif motif;
    private float swEnv;

    @Override
    public float extent() {
        return 2.4F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.fx.reset();
        s.hc.reset();
        s.wl.reset();
        s.wr.reset();
        s.kick = 0;
        s.roll = 0;
        rebuild(s, m);
    }

    private void rebuild(St s, FxMotion m) {
        s.lite = m.lite;
        s.pt = new FxParticles(m.lite ? 30 : 90);
        s.acc = 0;
        for (int i = 0; i < 2; i++) {
            s.tr[i] = new FxTrail(0.3F, 1.4F * m.sc, 0.04F * m.sc, 3 * m.sc);
        }
        s.hN = 0;
        s.hHead = 0;
    }

    /** 这一段还放得下 n 个面片：完整档按整个幻纹 600 算，精简档只算底座 150 */
    private boolean room(int n) {
        return out.pending() - from + n <= cap;
    }

    /** 拖光、残影的余量：精简档不占底座预算，超了由渲染器砍 */
    private boolean roomT(int n) {
        return lite || room(n);
    }

    private static int accentCost(int el, boolean lite) {
        return switch (el) {
            case FxMotion.FIRE -> 2;
            case FxMotion.ICE -> 4;
            case FxMotion.ELECTRIC, FxMotion.POISON -> 13;
            case FxMotion.MAGNETIC -> lite ? 8 : 16;
            case FxMotion.RADIATION -> 6;
            default -> 3;
        };
    }

    private void dirAt(float a) {
        double c = Math.cos(a), s = Math.sin(a);
        dX = axX * c + ayX * s;
        dY = axY * c + ayY * s;
        dZ = axZ * c + ayZ * s;
    }

    private void ring(double r, double w, int n, float a0, float a1, int uRep) {
        FxKit.ring(pen, cx, cy, cz, axX, axY, axZ, ayX, ayY, ayZ, r, w, n, a0, a1, uRep);
    }

    private void plate(double size, float rot) {
        FxKit.plate(pen, cx, cy, cz, axX, axY, axZ, ayX, ayY, ayZ, size, rot);
    }

    // 冰冠每根的长短是固定的，不随帧变
    private static float fixed(int i) {
        double v = Math.sin(i * 127.1 + 31.7) * 43758.5;
        return (float) (v - Math.floor(v));
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        lite = m.lite;
        if (s.pt == null || s.lite != lite) {
            rebuild(s, m);
        }
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        motif = fx;
        out = d.out;
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc;
        float act = m.act, amp = m.amp, Bk = m.burst;
        int el = m.el;
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        BodyPoints K = m.pts;
        float T = m.clock(), bt = m.beat(0), bl = m.blink(1), fk = m.flick(1);
        float run = EphemeraFx.clamp(m.speed / 5.6F, 0, 1);
        boolean sneak = m.sneak, air = !m.onGround, fp = d.firstPersonSelf;
        float swE = m.env(m.since(m.tSwing), 0.03F, 0.06F, 0.45F);
        swEnv = swE;
        float hurtE = m.env(m.since(m.tHurt), 0.02F, 0.12F, 0.5F);
        float jumpE = m.env(m.since(m.tJump), 0.04F, 0.12F, 0.45F);
        float breath = 0.5F + 0.5F * (float) Math.sin(t * 1.7F);
        boolean heavy = el == FxMotion.ELECTRIC || el == FxMotion.POISON || el == FxMotion.MAGNETIC;

        // ---- 光环的位置和朝向 ----
        double back = -W * 0.5F - 0.15F * W * run, lift = 0.04F * H + 0.015F * H * breath * amp;
        s.hc.step(K.headCenter.x + m.fwdX * back, K.headCenter.y + lift, K.headCenter.z + m.fwdZ * back, m, 15, 0.5F);
        if (m.justHurt) {
            s.hc.vx += m.fwdX * 2.2F * sc;
            s.hc.vz += m.fwdZ * 2.2F * sc;
        }
        cx = s.hc.x;
        cy = s.hc.y;
        cz = s.hc.z;
        float tilt = 0.42F * run + 0.25F * (air ? 1 : 0) + 0.2F * hurtE;
        s.roll += (EphemeraFx.clamp(-m.turn * 0.14F, -0.3F, 0.3F) - s.roll) * (1 - (float) Math.exp(-dt * 6));
        double y0x = -m.fwdX * tilt, y0y = 1, y0z = -m.fwdZ * tilt;
        double yl = Math.sqrt(y0x * y0x + y0y * y0y + y0z * y0z);
        y0x /= yl;
        y0y /= yl;
        y0z /= yl;
        double cr = Math.cos(s.roll), sr = Math.sin(s.roll);
        axX = m.rightX * cr + y0x * sr;
        axY = y0y * sr;
        axZ = m.rightZ * cr + y0z * sr;
        ayX = y0x * cr - m.rightX * sr;
        ayY = y0y * cr;
        ayZ = y0z * cr - m.rightZ * sr;
        nX = axY * ayZ - axZ * ayY;
        nY = axZ * ayX - axX * ayZ;
        nZ = axX * ayY - axY * ayX;
        double nl = Math.sqrt(nX * nX + nY * nY + nZ * nZ);
        nX /= nl;
        nY /= nl;
        nZ /= nl;
        s.kick += dt * (4 * swE + 2.5F * jumpE + 1.5F * hurtE);
        float rot0 = T * 0.18F + s.kick * 0.5F;
        float R = (0.3F * H + 0.25F * W) * (1 + 0.12F * swE * Bk + 0.06F * jumpE + 0.04F * bt + 0.02F * breath * amp) * (sneak ? 0.85F : 1);
        float A = (0.6F + 0.4F * act + 0.08F * breath) * bl * fk;
        if (hurtE > 0.05F) {
            A *= 0.45F + 0.55F * hash((int) Math.floor(m.t * 24));
        }
        record(s, m.t);

        // ---- 手腕：位置、前臂方向、环面两根轴 ----
        int NWA = lite ? 1 : heavy ? 2 : 4;
        sites.clear();
        if (!fp) {
            for (int i = 0; i < 8; i++) {
                dirAt(i / 8.0F * TAU + rot0);
                sites.add(cx + dX * R * 1.1F, cy + dY * R * 1.1F, cz + dZ * R * 1.1F, (float) dX, (float) dY, (float) dZ);
            }
        }
        // 腕环的弹簧很硬（w0 = 30、阻尼比 0.8），dt 到 0.04 时半隐式积分会发散，拆成不超过 0.02 的小步
        int steps = Math.max(1, (int) Math.ceil(dt / 0.02F));
        for (int k = 0; k < 2; k++) {
            int side = k == 0 ? -1 : 1;
            Wrist w = wrists[k];
            BodyPoints.Pt p = side < 0 ? K.wristL : K.wristR, dn = side < 0 ? K.downL : K.downR;
            FxKit.Spring sp = side < 0 ? s.wl : s.wr;
            for (int i = 0; i < steps; i++) {
                sp.step(p.x, p.y, p.z, dt / steps, 30, 0.8F, m.trackX, m.trackY, m.trackZ);
            }
            w.x = sp.x;
            w.y = sp.y;
            w.z = sp.z;
            w.ux = dn.x;
            w.uy = dn.y;
            w.uz = dn.z;
            boolean vertical = Math.abs(dn.y) > 0.9;
            double rx = vertical ? m.fwdX : 0, ry = vertical ? 0 : 1, rz = vertical ? m.fwdZ : 0;
            double ex = w.uy * rz - w.uz * ry, ey = w.uz * rx - w.ux * rz, ez = w.ux * ry - w.uy * rx;
            double el1 = Math.sqrt(ex * ex + ey * ey + ez * ez) + 1.0E-9;
            w.e1x = ex / el1;
            w.e1y = ey / el1;
            w.e1z = ez / el1;
            ex = w.uy * w.e1z - w.uz * w.e1y;
            ey = w.uz * w.e1x - w.ux * w.e1z;
            ez = w.ux * w.e1y - w.uy * w.e1x;
            el1 = Math.sqrt(ex * ex + ey * ey + ez * ez) + 1.0E-9;
            w.e2x = ex / el1;
            w.e2y = ey / el1;
            w.e2z = ez / el1;
            // 第一人称时右腕估计点挥砍会甩到镜头跟前，放大的环能糊住半个屏幕，所以不放大、挥砍那一下先藏起来
            w.swK = side == 1 && !fp ? swE : 0;
            w.r = W * 0.48F * (1 + 0.5F * w.swK * Bk + 0.05F * breath * amp + 0.15F * hurtE);
            w.aw = A * (1 + 0.6F * w.swK) * (fp && side == 1 ? fpSwingHide(m) : 1);
            w.rot = T * 1.3F * side + s.kick;
            s.tr[k].push(w.x, w.y, w.z, m.t);
            for (int i = 0; i < Math.min(2, NWA); i++) {
                float a = (float) i / NWA * TAU + w.rot * 0.5F;
                double c = Math.cos(a), sn = Math.sin(a);
                double drx = w.e1x * c + w.e2x * sn, dry = w.e1y * c + w.e2y * sn, drz = w.e1z * c + w.e2z * sn;
                sites.add(w.x + drx * w.r, w.y + dry * w.r, w.z + drz * w.r, (float) drx, (float) dry, (float) drz);
            }
        }

        // ---- 元素爆发、身上冒的东西、掉下来的铭文碎片：先画，后面的按剩下的让 ----
        pen.cat = EphemeraStaging.PARTICLE;
        fx.events(m.x, m.groundY, m.z, W * 1.6F);
        fx.bursts();
        fx.motes(sites, 12 * (0.5F + 0.5F * act), sc);
        FxParticles P = s.pt;
        P.thin = m.emit;
        if (!fp) {
            s.acc += dt * (2 + 16 * run + 40 * swE + 25 * hurtE) * (lite ? 0.4F : 1);
            while (s.acc >= 1) {
                s.acc -= 1;
                dirAt(rnd() * TAU);
                float rr = R * (0.9F + 0.3F * rnd());
                P.spec().pos(cx + dX * rr, cy + dY * rr, cz + dZ * rr)
                        .vel((float) (dX * 0.35F * sc - m.fwdX * 1.2F * run * sc), (float) (dY * 0.35F * sc + 0.25F * sc),
                                (float) (dZ * 0.35F * sc - m.fwdZ * 1.2F * run * sc))
                        .life(rnd(0.6F, 1.1F)).size(rnd(0.03F, 0.06F) * sc, 0.01F * sc).sprite(EphemeraSprites.WHITE)
                        .rgb(rnd() < 0.4F ? hot : C).a(0.9F).drag(1.5F).rot(rnd()).spawn();
            }
        } else {
            s.acc = 0;
        }
        P.step(dt, null);
        P.draw(pen, m.size, 1, Float.NaN);

        pen.cat = EphemeraStaging.BASE;
        cap = lite ? EphemeraTier.CAP_REDUCED : EphemeraTier.CAP_FULL;
        from = lite ? out.pending() : 0;

        // ---- 光环主体：柔光、铭文主环、外圈亮线、流光、刻度盘和内盘、四枚翼片 ----
        int NR = lite ? 12 : 24, NM = lite ? 12 : 24, NG = lite ? 12 : 27;
        if (!fp) {
            if (!lite) {
                pen.s(SOFT).alphaBlend().rgb(deep).a(0.24F).billboard(cx, cy, cz, R * 2.4F, R * 2.4F);
            }
            pen.s(SOFT).rgb(C).a((0.08F + 0.05F * breath * amp + 0.13F * swE) * A).billboard(cx, cy, cz, R * 3, R * 3);
            rib.clear();
            FxKit.circle(rib, cx, cy, cz, axX, axY, axZ, ayX, ayY, ayZ, R, NR, 0, TAU, R * (0.45F + 0.15F * swE), 1);
            pen.s(SOFT).rgb(C).a(0.24F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);

            // 铭文带每 3 段铺一遍贴图，一圈 9 遍（精简档 4 遍）
            pen.s(TEX_GLYPH).rgb(C).a(0.95F * A * (1 + 0.5F * swE));
            ring(R, R * 0.22F, NG, rot0, rot0 + TAU, 3);
            pen.rgb(hot).a(0.8F * A);
            ring(R * 1.13F, R * 0.032F, NM, 0, TAU, 0);
            float fa = rot0 + t * (el == FxMotion.ICE ? 0.6F : 1.4F);
            pen.s(TEX_GLYPH).rgb(hot).a(0.75F * A);
            ring(R, R * 0.27F, lite ? 3 : 7, fa, fa + 1.2F, 7);
            if (!lite) {
                dirAt(fa + 1.2F);
                pen.s(SOFTCORE).rgb(hot).a(0.6F * A).billboard(cx + dX * R, cy + dY * R, cz + dZ * R, R * 0.5F, R * 0.5F);
            }

            pen.s(TEX_DIAL).rgb(pale).a(0.5F * A);
            plate(R * 2.55F, -rot0 * 0.6F - s.kick);
            pen.s(TEX_RUNE).rgb(C).a(0.75F * A);
            plate(R * 1.55F, rot0 * 1.7F + s.kick * 2);
            if (!lite) {
                pen.s(TEX_RUNE).rgb(hot).a(0.4F * A);
                plate(R * 0.95F, -T * 0.9F - s.kick * 3);
            }

            for (int i = 0; i < 4; i++) {
                dirAt(rot0 * 0.5F + i * TAU / 4 + PI / 4);
                double drx = dX, dry = dY, drz = dZ;
                double sdx = dry * nZ - drz * nY, sdy = drz * nX - drx * nZ, sdz = drx * nY - dry * nX;
                float len = R * (0.34F + 0.3F * swE * Bk + 0.04F * breath * amp + 0.1F * bt);
                double bx = cx + drx * R * 1.17F, by = cy + dry * R * 1.17F, bz = cz + drz * R * 1.17F;
                double tx = bx + drx * len, ty = by + dry * len, tz = bz + drz * len;
                double mx = bx + drx * len * 0.3F, my = by + dry * len * 0.3F, mz = bz + drz * len * 0.3F;
                double w1 = R * 0.09F, w2 = R * 0.03F, sink = R * 0.04F;
                pen.rgb(C).a(0.85F * A).quad(tx, ty, tz, mx + sdx * w1, my + sdy * w1, mz + sdz * w1,
                        bx - drx * sink, by - dry * sink, bz - drz * sink, mx - sdx * w1, my - sdy * w1, mz - sdz * w1);
                pen.rgb(WHITE).a(0.7F * A).quad(tx, ty, tz, mx + sdx * w2, my + sdy * w2, mz + sdz * w2,
                        bx, by, bz, mx - sdx * w2, my - sdy * w2, mz - sdz * w2);
                if (!lite) {
                    pen.s(FLARE).rgb(hot).a(0.5F * A * (0.6F + 0.4F * (float) Math.sin(t * 3 + i * 1.7F)))
                            .billboard(tx, ty, tz, R * 0.22F, R * 0.22F, t * 0.3F);
                }
            }
            scan(0, t, R, A, hot);

            // 挥砍：整圈外扩一道光环 + 冲击圈
            float ss = m.since(m.tSwing) / m.burstTime;
            if (ss < 0.6F) {
                float f = ss / 0.6F;
                pen.s(SOFTRING).rgb(hot).a(0.9F * (1 - f) * A);
                plate(R * 2.78F * (1 + 0.8F * f * Bk), 0);
                pen.s(SHOCK).rgb(C).a(0.8F * (1 - f));
                plate(R * 2.4F * (1 + 1.1F * f * Bk), 0);
            }
            // 受击：铭文带碎成几截错位乱跳
            if (hurtE > 0.05F) {
                int q = (int) Math.floor(m.t / 0.05);
                for (int i = 0; i < (lite ? 3 : 7); i++) {
                    float a0 = hash(q * 17 + i) * TAU, rr = R * (0.8F + 0.5F * hash(q * 5 + i * 3));
                    float off = (hash(q + i * 9) - 0.5F) * R * 0.2F;
                    pen.s(TEX_GLYPH).rgb((i & 1) != 0 ? WHITE : hot).a(0.8F * hurtE);
                    FxKit.ring(pen, cx + axX * off, cy + axY * off, cz + axZ * off, axX, axY, axZ, ayX, ayY, ayZ,
                            rr, R * 0.18F, 2, a0, a0 + 0.5F, 2);
                }
            }
        }

        // ---- 腕环主体：柔光、沿前臂的光柱、铭文环、两侧刻度盘 ----
        for (Wrist w : wrists) {
            if (w.aw <= 0.01F) {
                continue;
            }
            pen.s(SOFT).rgb(C).a((0.2F + 0.3F * w.swK) * w.aw).billboard(w.x, w.y, w.z, w.r * 3.6F, w.r * 3.6F);
            // 手臂竖着时环是平躺的，平视只剩一条线，靠这道光柱撑住
            pen.s(SOFT).rgb(C).a(0.22F * w.aw).beam(w.x - w.ux * 0.2F * sc, w.y - w.uy * 0.2F * sc, w.z - w.uz * 0.2F * sc,
                    w.x + w.ux * 0.12F * sc, w.y + w.uy * 0.12F * sc, w.z + w.uz * 0.12F * sc, w.r * 1.7F);
            pen.s(TEX_GLYPH).rgb(C).a(0.95F * w.aw);
            FxKit.ring(pen, w.x, w.y, w.z, w.e1x, w.e1y, w.e1z, w.e2x, w.e2y, w.e2z, w.r, w.r * 0.34F, lite ? 8 : 14, w.rot, w.rot + TAU, 3);
            if (!lite) {
                wristBand(w, C);
            }
            for (int off = -1; off <= 1; off += 2) {
                double o = off * 0.1F * sc;
                float rr = w.r * (off < 0 ? 0.88F : 1.05F);
                pen.s(TEX_DIAL).rgb(off < 0 ? pale : hot).a(0.75F * w.aw);
                FxKit.plate(pen, w.x + w.ux * o, w.y + w.uy * o, w.z + w.uz * o, w.e1x, w.e1y, w.e1z, w.e2x, w.e2y, w.e2z,
                        rr * 2.05F, -w.rot * 0.7F * off);
            }
        }

        // ---- 元素招牌：环上的特写先画，再沿环一圈凸起 ----
        if (!fp) {
            elementHalo(el, t, T, R, A, bt, bl, C, hot, pale, rot0);
            int NE = lite ? (heavy ? 4 : 6) : (heavy ? 7 : 12);
            int cost = accentCost(el, lite);
            for (int i = 0; i < NE; i++) {
                if (!room(cost)) {
                    break;
                }
                float a = (float) i / NE * TAU + rot0;
                dirAt(a);
                // 火往上窜：上半圈的长一截
                float up = el == FxMotion.FIRE ? 1 + 0.8F * Math.max(0, (float) Math.sin(a)) : 1;
                fx.accent(cx + dX * R * 1.15F, cy + dY * R * 1.15F, cz + dZ * R * 1.15F, dX, dY, dZ,
                        R * (0.26F + 0.22F * swE + 0.06F * breath * amp) * up, R * 0.12F, i, 0.8F * A);
            }
        }

        float tk1 = Math.max(wrists[1].swK * 1.4F, Math.max(run * 0.5F, air ? 0.4F : 0));
        float tk0 = Math.max(run * 0.5F, air ? 0.4F : 0);
        if (!lite) {
            wristTrail(s, 1, tk1, m, C, hot);
        }

        int cost = accentCost(el, lite);
        for (int k = 0; k < 2; k++) {
            Wrist w = wrists[k];
            int side = k == 0 ? -1 : 1;
            for (int i = 0; i < NWA && w.aw > 0.01F; i++) {
                if (!room(cost)) {
                    break;
                }
                float a = (float) i / NWA * TAU + w.rot * 0.5F;
                double c = Math.cos(a), sn = Math.sin(a);
                double drx = w.e1x * c + w.e2x * sn, dry = w.e1y * c + w.e2y * sn, drz = w.e1z * c + w.e2z * sn;
                fx.accent(w.x + drx * w.r * 1.05F, w.y + dry * w.r * 1.05F, w.z + drz * w.r * 1.05F, drx, dry, drz,
                        w.r * (0.7F + 0.8F * w.swK), w.r * 0.3F, 80 + i + (side + 1) * 5, 0.75F * w.aw);
            }
        }

        // ---- 细节：放得下才画 ----
        if (!fp) {
            if (!lite && room(NM)) {
                pen.s(SOFT).alphaBlend().rgb(deep).a(0.5F);
                ring(R, R * 0.36F, NM, 0, TAU, 0);
            }
            if (room(NM)) {
                pen.rgb(hot).a(0.55F * A);
                ring(R * 0.875F, R * 0.02F, NM, 0, TAU, 0);
            }
        }
        for (Wrist w : wrists) {
            if (w.aw <= 0.01F) {
                continue;
            }
            if (lite) {
                if (room(8)) {
                    wristBand(w, C);
                }
                continue;
            }
            if (room(14)) {
                pen.s(SOFT).alphaBlend().rgb(deep).a(0.45F);
                FxKit.ring(pen, w.x, w.y, w.z, w.e1x, w.e1y, w.e1z, w.e2x, w.e2y, w.e2z, w.r, w.r * 0.42F, 14, 0, TAU, 0);
            }
            for (int off = -1; off <= 1; off += 2) {
                if (!room(12)) {
                    break;
                }
                double o = off * 0.1F * sc;
                rib.clear();
                FxKit.circle(rib, w.x + w.ux * o, w.y + w.uy * o, w.z + w.uz * o, w.e1x, w.e1y, w.e1z, w.e2x, w.e2y, w.e2z,
                        w.r * (off < 0 ? 0.88F : 1.05F) * 0.98F, 12, 0, TAU, w.r * 0.1F, 1);
                pen.rgb(hot).a(0.7F * w.aw).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            }
        }
        if (!fp && !lite) {
            for (int k = 1; k < 4 && room(1); k++) {
                scan(k, t, R, A, hot);
            }
        }

        // ---- 残影、拖光（拖尾类） ----
        pen.cat = EphemeraStaging.TRAIL;
        if (lite) {
            wristTrail(s, 1, tk1, m, C, hot);
        }
        if (!fp && (run > 0.05F || air)) {
            int n = lite ? 2 : 4;
            float k = Math.max(run, air ? 0.6F : 0);
            for (int j = 1; j <= n; j++) {
                int h = findHist(s, m.t - j * 0.065, m.t);
                if (h < 0 || !roomT(1)) {
                    break;
                }
                int b = h * 9;
                double[] v = s.hv;
                pen.s(SOFTRING).rgb(C).a(0.45F * k * A * (1 - j / (n + 1.0F)));
                FxKit.plate(pen, v[b], v[b + 1], v[b + 2], v[b + 3], v[b + 4], v[b + 5], v[b + 6], v[b + 7], v[b + 8], R * 2.78F, 0);
                if (!lite && j <= 2 && room(1)) {
                    pen.s(TEX_RUNE).rgb(C).a(0.25F * k * A * (1 - j / 3.0F));
                    FxKit.plate(pen, v[b], v[b + 1], v[b + 2], v[b + 3], v[b + 4], v[b + 5], v[b + 6], v[b + 7], v[b + 8], R * 1.55F, rot0 * 1.7F);
                }
            }
        }
        wristTrail(s, 0, tk0, m, C, hot);

        // ---- 环上的闪点 ----
        if (!fp && !lite) {
            pen.cat = EphemeraStaging.PARTICLE;
            for (int i = 0; i < 6 && room(1); i++) {
                float ph = t * 2.5F + i * 0.37F;
                int q = (int) Math.floor(ph);
                dirAt(hash(q * 13 + i * 71) * TAU + rot0);
                float tw = (float) Math.sin((ph - q) * PI);
                float rr = R * (0.95F + 0.2F * hash(q * 7 + i));
                pen.s(FLARE).rgb(hot).a(0.8F * tw * A)
                        .billboard(cx + dX * rr, cy + dY * rr, cz + dZ * rr, R * 0.3F * tw, R * 0.3F * tw, q);
            }
        }
    }

    /** 扫描线：一根亮辐绕圈扫，k 越大越靠后、越淡 */
    private void scan(int k, float t, float R, float A, int hot) {
        dirAt(t * 2.1F - k * 0.1F);
        double drx = dX, dry = dY, drz = dZ;
        double w = R * 0.035F * (1 + k * 0.4F);
        double sdx = (dry * nZ - drz * nY) * w, sdy = (drz * nX - drx * nZ) * w, sdz = (drx * nY - dry * nX) * w;
        double p0x = cx + drx * R * 0.3F, p0y = cy + dry * R * 0.3F, p0z = cz + drz * R * 0.3F;
        double p1x = cx + drx * R * 1.12F, p1y = cy + dry * R * 1.12F, p1z = cz + drz * R * 1.12F;
        pen.rgb(hot).a(0.55F * A * (1 - k / 4.0F)).va(1, 0.15F, 0.15F, 1)
                .quad(p1x + sdx, p1y + sdy, p1z + sdz, p0x + sdx, p0y + sdy, p0z + sdz,
                        p0x - sdx, p0y - sdy, p0z - sdz, p1x - sdx, p1y - sdy, p1z - sdz);
    }

    /** 记下这一帧光环的位置和两根轴；帧率很高时 3 ms 内的覆盖同一格，128 格总能盖住残影要回看的 0.26 秒 */
    private void record(St s, double now) {
        int i;
        if (s.hN > 0 && now - s.hT[s.hHead] < 0.003) {
            i = s.hHead;
        } else {
            s.hHead = (s.hHead + 1) % HIST;
            s.hN = Math.min(HIST, s.hN + 1);
            i = s.hHead;
        }
        s.hT[i] = now;
        double[] v = s.hv;
        int b = i * 9;
        v[b] = cx;
        v[b + 1] = cy;
        v[b + 2] = cz;
        v[b + 3] = axX;
        v[b + 4] = axY;
        v[b + 5] = axZ;
        v[b + 6] = ayX;
        v[b + 7] = ayY;
        v[b + 8] = ayZ;
    }

    /** 从新到旧找第一条不晚于 want 的记录，只看 0.3 秒以内的 */
    private static int findHist(St s, double want, double now) {
        for (int k = 0; k < s.hN; k++) {
            int i = (s.hHead - k + HIST) % HIST;
            if (now - s.hT[i] > 0.3) {
                return -1;
            }
            if (s.hT[i] <= want) {
                return i;
            }
        }
        return -1;
    }

    /** a 到 b 的折线（n 段），中间各点沿 (sx, sy, sz) 随机偏 ±amp；和元素母题里的电弧同一个算法 */
    private void zig(double ax, double ay, double az, double bx, double by, double bz, int n, float amp, int seed,
                     double sx, double sy, double sz) {
        for (int i = 0; i <= n; i++) {
            float f = (float) i / n;
            float j = i == 0 || i == n ? 0 : (hash(seed * 31 + i * 7) - 0.5F) * 2 * amp;
            zx[i] = ax + (bx - ax) * f + sx * j;
            zy[i] = ay + (by - ay) * f + sy * j;
            zz[i] = az + (bz - az) * f + sz * j;
        }
    }

    private void zigRibbon(int n, float w) {
        rib.clear();
        for (int i = 0; i <= n; i++) {
            rib.add(zx[i], zy[i], zz[i], w, 1);
        }
    }

    private void lineRibbon(float w) {
        rib.clear();
        for (int i = 0; i < lx.length; i++) {
            rib.add(lx[i], ly[i], lz[i], w, 1);
        }
    }

    /** 第一人称挥砍时手臂估计点会扫过镜头前，这只手的东西先藏 0.3 秒（原版挥臂 6 tick），再用 0.15 秒淡回来 */
    static float fpSwingHide(FxMotion m) {
        return Math.min(1, Math.max(0, (m.since(m.tSwing) - 0.3F) / 0.15F));
    }

    /** 腕环外面一圈正对镜头的柔光带 */
    private void wristBand(Wrist w, int C) {
        rib.clear();
        FxKit.circle(rib, w.x, w.y, w.z, w.e1x, w.e1y, w.e1z, w.e2x, w.e2y, w.e2z, w.r, lite ? 8 : 12, 0, TAU, w.r * 0.7F, 1);
        pen.s(SOFT).rgb(C).a(0.4F * w.aw).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
    }

    /** 手的拖光：跑、挥砍时甩出一道铭文光弧 */
    private void wristTrail(St s, int k, float tk, FxMotion m, int C, int hot) {
        Wrist w = wrists[k];
        if (tk <= 0.02F || w.aw <= 0.01F) {
            return;
        }
        final float ww = w.r * (1 + 1.6F * w.swK), aw = w.aw, kk = tk;
        int cat = pen.cat;
        pen.cat = EphemeraStaging.TRAIL;
        if (!lite && room(10)) {
            FxTrail.Sample p = s.tr[k].sample(10, smp);
            pen.s(SOFT).rgb(C);
            FxTrail.draw(pen, p, rib, (q, i) -> ww * 1.6F * (1 - q), (q, i) -> 0.35F * kk * (1 - q) * aw, null,
                    m.trailW, m.trailA, EphemeraPen.FACE_CAM, 0, 1);
        }
        int segs = lite ? 5 : 10;
        if (roomT(segs)) {
            FxTrail.Sample p = s.tr[k].sample(segs, smp);
            pen.s(TEX_GLYPH).rgb(hot);
            FxTrail.draw(pen, p, rib, (q, i) -> ww * 0.7F * (1 - q * 0.6F), (q, i) -> 0.9F * kk * (1 - q) * aw, null,
                    m.trailW, m.trailA, EphemeraPen.FACE_CAM, 0, 1);
        }
        pen.cat = cat;
    }

    /** 各元素在光环上的招牌，每一块先看放不放得下 */
    private void elementHalo(int el, float t, float T, float R, float A, float bt, float bl, int C, int hot, int pale, float rot0) {
        switch (el) {
            case FxMotion.FIRE -> {
                // 内盘烧起来：一圈滚动的火絮
                int n = lite ? 8 : 16;
                if (room(n)) {
                    pen.s(FLOW_0).rgb(C).a(0.4F * A);
                    ring(R * 0.55F, R * 0.45F, n, -T * 0.7F, -T * 0.7F + TAU, 4);
                }
                if (!lite) {
                    for (int i = 0; i < 5 && room(2); i++) {
                        dirAt(PI / 2 + (i - 2) * 0.32F + rot0 * 0.2F);
                        motif.accent(cx + dX * R * 1.2F, cy + dY * R * 1.2F, cz + dZ * R * 1.2F, 0, 1, 0,
                                R * (0.55F + 0.15F * (float) Math.sin(t * 4 + i)), R * 0.2F, 40 + i, 0.7F * A);
                    }
                }
            }
            case FxMotion.ICE -> {
                // 一圈硬边冰晶冠 + 盘心六瓣霜花
                int NI = lite ? 8 : 18;
                float swell = 1 + 0.3F * swEnv;
                for (int i = 0; i < NI && room(2); i++) {
                    dirAt(i / (float) NI * TAU + rot0 * 0.3F);
                    double drx = dX, dry = dY, drz = dZ;
                    double sdx = dry * nZ - drz * nY, sdy = drz * nX - drx * nZ, sdz = drx * nY - dry * nX;
                    float L = R * (0.12F + 0.3F * fixed(i * 3 + 1)) * swell;
                    double bx = cx + drx * R * 1.12F, by = cy + dry * R * 1.12F, bz = cz + drz * R * 1.12F;
                    double tx = bx + drx * L, ty = by + dry * L, tz = bz + drz * L;
                    double mx = bx + drx * L * 0.35F, my = by + dry * L * 0.35F, mz = bz + drz * L * 0.35F;
                    double w1 = R * 0.05F, w2 = R * 0.012F;
                    pen.rgb(pale).a(0.75F * A).va(1, 0.6F, 0.3F, 0.6F).quad(tx, ty, tz, mx + sdx * w1, my + sdy * w1, mz + sdz * w1,
                            bx, by, bz, mx - sdx * w1, my - sdy * w1, mz - sdz * w1);
                    pen.rgb(WHITE).a(0.85F * A).quad(tx, ty, tz, mx + sdx * w2, my + sdy * w2, mz + sdz * w2,
                            bx, by, bz, mx - sdx * w2, my - sdy * w2, mz - sdz * w2);
                }
                if (!lite) {
                    for (int i = 0; i < 6 && room(3); i++) {
                        dirAt(i / 6.0F * TAU - T * 0.1F);
                        double drx = dX, dry = dY, drz = dZ;
                        double sdx = dry * nZ - drz * nY, sdy = drz * nX - drx * nZ, sdz = drx * nY - dry * nX;
                        pen.rgb(WHITE).a(0.7F * A).beam(cx + drx * R * 0.12F, cy + dry * R * 0.12F, cz + drz * R * 0.12F,
                                cx + drx * R * 0.5F, cy + dry * R * 0.5F, cz + drz * R * 0.5F, R * 0.03F);
                        for (int sg = -1; sg <= 1; sg += 2) {
                            double o = sg * R * 0.1F;
                            pen.rgb(pale).a(0.7F * A).beam(cx + drx * R * 0.3F, cy + dry * R * 0.3F, cz + drz * R * 0.3F,
                                    cx + drx * R * 0.42F + sdx * o, cy + dry * R * 0.42F + sdy * o, cz + drz * R * 0.42F + sdz * o, R * 0.02F);
                        }
                    }
                }
                if (room(1)) {
                    pen.s(SOFT).rgb(pale).a(0.12F * A);
                    plate(R * 2.2F, 0);
                }
            }
            case FxMotion.ELECTRIC -> {
                // 几道电弧在环上两点之间横跳
                int q = (int) Math.floor(t / 0.06F), NA = lite ? 1 : 3;
                for (int i = 0; i < NA; i++) {
                    if (hash(q * 3 + i * 11) < 0.25F) {
                        continue;
                    }
                    if (!room(17)) {
                        break;
                    }
                    float a0 = hash(q * 7 + i) * TAU, a1 = a0 + 1.2F + hash(q * 9 + i) * 2.2F;
                    dirAt(a0);
                    double p0x = cx + dX * R, p0y = cy + dY * R, p0z = cz + dZ * R;
                    dirAt(a1);
                    double p1x = cx + dX * R, p1y = cy + dY * R, p1z = cz + dZ * R;
                    double ux = p1x - p0x, uy = p1y - p0y, uz = p1z - p0z, ul = Math.sqrt(ux * ux + uy * uy + uz * uz) + 1.0E-9;
                    ux /= ul;
                    uy /= ul;
                    uz /= ul;
                    double sx = uy * nZ - uz * nY, sy = uz * nX - ux * nZ, sz = ux * nY - uy * nX;
                    double sl = Math.sqrt(sx * sx + sy * sy + sz * sz) + 1.0E-9;
                    zig(p0x, p0y, p0z, p1x, p1y, p1z, 8, R * 0.12F, q * 13 + i, sx / sl, sy / sl, sz / sl);
                    zigRibbon(8, R * 0.14F);
                    pen.s(SOFT).rgb(C).a(0.5F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    zigRibbon(8, R * 0.03F);
                    pen.rgb(hot).a(A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(FLARE).rgb(hot).a(0.8F * A).billboard(p1x, p1y, p1z, R * 0.35F, R * 0.35F, q);
                }
            }
            case FxMotion.POISON -> {
                // 内盘一层慢慢翻的毒絮，下半圈多挂几滴
                int n = lite ? 8 : 16;
                if (room(n)) {
                    pen.s(FLOW_0).rgb(mul(C, 0.8F)).a(0.35F * A);
                    ring(R * 0.55F, R * 0.45F, n, T * 0.3F, T * 0.3F + TAU, 4);
                }
                for (int i = 0; i < (lite ? 2 : 4) && room(13); i++) {
                    dirAt(-PI / 2 + (i - 1.5F) * 0.45F);
                    motif.accent(cx + dX * R * 1.1F, cy + dY * R * 1.1F, cz + dZ * R * 1.1F, 0, -1, 0, R * 0.5F, R * 0.16F, 60 + i, 0.8F * A);
                }
            }
            case FxMotion.MAGNETIC -> {
                // 两道跨过整个光环、鼓出环面的大磁力线，光珠沿线流
                for (int j = 0; j < (lite ? 1 : 2) && room(27); j++) {
                    dirAt(rot0 + j * PI / 2);
                    double d0x = dX, d0y = dY, d0z = dZ;
                    float bulge = R * (0.55F + 0.08F * (float) Math.sin(t * 1.3F + j));
                    for (int i = 0; i <= 12; i++) {
                        double ang = i / 12.0 * Math.PI, along = Math.cos(ang) * R * 1.05F, out2 = Math.sin(ang) * bulge;
                        lx[i] = cx + d0x * along + nX * out2;
                        ly[i] = cy + d0y * along + nY * out2;
                        lz[i] = cz + d0z * along + nZ * out2;
                    }
                    lineRibbon(R * 0.16F);
                    pen.s(SOFT).rgb(C).a(0.35F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    lineRibbon(R * 0.03F);
                    pen.rgb(pale).a(0.85F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    for (int b = 0; b < 3; b++) {
                        float f = (t * 0.5F + b / 3.0F + j * 0.17F) % 1;
                        int i = Math.min(11, (int) Math.floor(f * 12));
                        double ff = f * 12 - i;
                        pen.s(SOFTCORE).rgb(hot).a(0.9F * A).billboard(lx[i] + (lx[i + 1] - lx[i]) * ff, ly[i] + (ly[i + 1] - ly[i]) * ff,
                                lz[i] + (lz[i + 1] - lz[i]) * ff, R * 0.2F, R * 0.2F);
                    }
                }
            }
            case FxMotion.RADIATION -> {
                // 内盘三扇慢转的扇形光 + 盘心六边形
                int n = lite ? 2 : 4;
                for (int i = 0; i < 3 && room(n); i++) {
                    float a = i / 3.0F * TAU + T * 0.4F;
                    pen.s(SOFT).rgb(C).a(0.45F * A * bl);
                    ring(R * 0.42F, R * 0.34F, n, a, a + 0.75F, 0);
                }
                if (room(1)) {
                    pen.s(HEX_0).rgb(hot).a(0.8F * A * bl);
                    plate(R * 0.5F, T * 0.3F);
                }
            }
            default -> {
                // 冲击：每一拍从环上往外推一道冲击圈
                if (room(2)) {
                    float f = 1 - bt;
                    pen.s(SHOCK).rgb(pale).a(0.7F * bt * A);
                    plate(R * 2.6F * (1 + 0.6F * f), 0);
                    pen.s(SHOCK).rgb(WHITE).a(0.5F * bt * A);
                    plate(R * 2.4F * (1 + 0.25F * f), 0);
                }
            }
        }
    }
}
