package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;

/**
 * 曳翎：背后两侧各三片光羽，羽间张着流动的翼膜，外罩一层柔光；跑起来光翼后掠、在身后拖出两片光幕，
 * 起跳振翼、落地下拍、挥砍时一侧前扫带出弧光、受击先收拢再炸开，每隔几秒整体鼓动一次。
 */
public final class PlumeStyle extends ProtoStyle<PlumeStyle.St> {

    private static final float[] ELEV0 = {1.15F, 0.5F, -0.22F};
    private static final float[] LEN_K = {1.0F, 1.18F, 0.82F};
    private static final float[] WID_K = {1, 0.9F, 0.72F};
    private static final int[] TIP_FULL = {0, 3, 1, 4};
    private static final int[] TIP_LITE = {0, 3};

    public static final class St extends ProtoStyle.State {
        boolean ready, lite;
        float sc;
        float open, sweep, flap;
        final float[] fswing = new float[2];
        FxParticles motes, plume, fx, shards;
        FxTrail[] tips;
        final FxTrail[] sheet = new FxTrail[2];
        float burstT, nextBurst, acc, accF, accM, zapT, zapK;
        int seenBlast = -1;
        final ElementMotif motif = new ElementMotif();
    }

    private record Params(float curl, float amp, float om, float flow, float life, int glow, int core) {
        static final Params FIRE = new Params(0.28F, 0.07F, 7.5F, 1.4F, 0.5F, FLOW_0, STREAK);
        static final Params ICE = new Params(-0.06F, 0.012F, 1.4F, 0.25F, 0.75F, FLOW_2, STREAK);
        static final Params ZAP = new Params(0.05F, 0.03F, 3, 0, 0.38F, CRACK_0, CRACK_1);
        static final Params BASE = new Params(0.12F, 0.05F, 4, 0.8F, 0.55F, FLOW_0, STREAK);

        static Params of(int kind) {
            return switch (kind) {
                case 1 -> FIRE;
                case 2 -> ICE;
                case 3 -> ZAP;
                default -> BASE;
            };
        }
    }

    private static final int NSEG_FULL = 8;
    private final EphemeraPen.Ribbon[] feathers = new EphemeraPen.Ribbon[6];
    private final EphemeraPen.Ribbon[] wingMid = new EphemeraPen.Ribbon[2];
    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(32);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final float[] dir = new float[3];
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final double[] tailX = new double[2], tailY = new double[2], tailZ = new double[2];
    private final float[] tailD = new float[6], tailW = new float[2], tailA = new float[2];
    private int tailN;
    private boolean calm;
    private float trailW, trailA;
    private int nseg;

    public PlumeStyle() {
        for (int i = 0; i < 6; i++) {
            feathers[i] = new EphemeraPen.Ribbon(NSEG_FULL + 1);
        }
        for (int i = 0; i < 2; i++) {
            wingMid[i] = new EphemeraPen.Ribbon(NSEG_FULL + 1);
        }
    }

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
        s.ready = false;
        s.motif.reset();
    }

    private void init(St s, FxMotion m) {
        s.ready = true;
        s.lite = m.lite;
        s.sc = m.sc;
        Params P = Params.of(m.kind);
        s.open = 0.8F;
        s.sweep = 0;
        s.flap = 0;
        s.fswing[0] = s.fswing[1] = 0;
        s.motes = new FxParticles(m.lite ? 12 : 44);
        s.plume = new FxParticles(m.lite ? 5 : 18);
        s.fx = new FxParticles(m.lite ? 12 : 40);
        s.shards = new FxParticles(m.lite ? 10 : 36);
        s.tips = new FxTrail[m.lite ? 2 : 4];
        for (int i = 0; i < s.tips.length; i++) {
            s.tips[i] = new FxTrail(P.life, 3.4F * m.sc, 0.03F * m.sc, 3 * m.sc);
        }
        for (int i = 0; i < 2; i++) {
            s.sheet[i] = new FxTrail(0.6F, 3.6F * m.sc, 0.03F * m.sc, 3 * m.sc);
        }
        s.burstT = -10;
        s.nextBurst = (float) m.t + 1.2F;
        s.acc = s.accF = s.accM = 0;
        s.zapT = 0;
        s.zapK = 0;
    }

    private static float mod05(float v) {
        return ((v % 0.5F) + 0.5F) % 0.5F;
    }

    private void tdraw(FxTrail tr, int segs, FxTrail.Fn width, FxTrail.Fn alpha, FxTrail.Fn rgb, float u0, float u1) {
        FxTrail.draw(pen, sample, rib, width, alpha, rgb, trailW, trailA, EphemeraPen.FACE_CAM, u0, u1);
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        boolean lite = m.lite;
        calm = m.calm;
        if (!s.ready || s.lite != lite || Math.abs(s.sc - m.sc) > 1.0E-3F) {
            init(s, m);
        }
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc;
        boolean sneak = m.sneak;
        float fwdX = m.fwdX, fwdZ = m.fwdZ, rightX = m.rightX, rightZ = m.rightZ;
        double bx = m.x - fwdX * W * (sneak ? 0.57F : 0.4F), by = m.y + H * (sneak ? 0.61F : 0.6F), bz = m.z - fwdZ * W * (sneak ? 0.57F : 0.4F);
        double ax0 = m.x, ay0 = m.y + H * 0.555, az0 = m.z;
        double gy = m.groundY;
        int C = m.color, deep = mul(C, 0.13F), body = mul(C, 0.5F), hot = mix(C, WHITE, 0.5F);
        int kind = m.kind;
        boolean zap = kind == 3;
        // 毒、冲击、磁、辐射：翼缘、翼尖拖尾的末端、背心冒的、受击那下交给元素母题
        boolean ek = kind == 0;
        ElementMotif motif = s.motif;
        motif.begin(pen, m);
        // 电击的默认色偏紫、亮度低，白天灰底上暗托底一压就发闷：按天光把暗托底减淡、主色托底提一点，夜里不动
        float zapDay = zap ? (d.glowAlpha >= EphemeraTier.GLOW_SNOW - 1.0E-3F ? 1
                : EphemeraFx.clamp((d.glowAlpha - EphemeraTier.GLOW_NIGHT) / (EphemeraTier.GLOW_DAY - EphemeraTier.GLOW_NIGHT), 0, 1)) : 0;
        Params P = Params.of(kind);
        int NSEG = lite ? 5 : NSEG_FULL;
        nseg = NSEG;
        float Bk = m.burst, act = m.act, ib = calm ? m.idle : 1;
        trailW = m.trailW;
        trailA = m.trailA;
        s.motes.thin = s.plume.thin = s.fx.thin = s.shards.thin = m.emit;
        boolean fp = d.firstPersonSelf;

        if (t >= s.nextBurst) {
            s.burstT = t;
            s.nextBurst = t + 3.2F + rnd() * 1.6F;
        }
        float sinceB = t - s.burstT;
        float eB = EphemeraFx.env0(sinceB, 0.15F, 0.05F, 0.8F) * ib, eBdown = EphemeraFx.env0(sinceB - 0.12F, 0.08F, 0.02F, 0.45F) * ib;
        float eJump = m.env(m.since(m.tJump), 0.06F, 0.12F, 0.4F);
        float eLand = m.env(m.since(m.tLand), 0.03F, 0.04F, 0.55F) * EphemeraFx.clamp(m.landSpeed / 8, 0.6F, 1.5F);
        float eRun = m.env(m.since(m.tRunStart), 0.05F, 0.1F, 0.55F);
        float eStop = m.env(m.since(m.tStop), 0.04F, 0.12F, 0.6F);
        float hk = m.since(m.tHurt);
        // 受击：先猛收 0.08 秒，再炸开
        float pinch = m.env(hk, 0.04F, 0.03F, 0.07F), blast = m.env(hk - 0.08F, 0.05F, 0.12F, 0.45F);
        float eSw = m.env(m.since(m.tSwing), 0.04F, 0.1F, 0.4F);
        float spd = EphemeraFx.clamp(m.speed / 5.6F, 0, 1);
        int swSide = m.swingCount % 2 != 0 ? 1 : -1;

        float k8 = 1 - (float) Math.exp(-dt * 8), k14 = 1 - (float) Math.exp(-dt * 14);
        float openT = (sneak ? 0.35F : 0.9F) + 0.3F * eB + 0.5F * eJump + 0.45F * eStop - 0.25F * spd;
        float sweepT = Math.max(-0.3F, (sneak ? 0.6F : 0.3F + spd * 0.55F) + (m.onGround ? 0 : 0.15F) - 0.7F * eStop + 0.4F * eRun);
        s.open += (openT - s.open) * k8;
        s.sweep += (sweepT - s.sweep) * k8;
        float flapT = 0.55F * eJump - 0.75F * eLand - 0.55F * eBdown + 0.25F * eB;
        s.flap += (flapT - s.flap) * k14;
        for (int i = 0; i < 2; i++) {
            int sg = i != 0 ? 1 : -1;
            float tgt = sg == swSide ? eSw : 0;
            s.fswing[i] += (tgt - s.fswing[i]) * (1 - (float) Math.exp(-dt * 20));
        }
        float intensity = (sneak ? 0.55F : 1) * (1 + 0.35F * eB + (0.25F * eRun + 0.3F * eLand + 0.5F * blast) * Bk);
        float span = (H * 0.5F + W * 0.55F) * (0.6F + 0.4F * s.open) * (1 + 0.12F * spd) * (1 - 0.5F * pinch + 0.45F * blast * Bk)
                * (calm ? 0.78F + 0.1F * act : 1);
        float fan = 1 - 0.6F * pinch + 0.5F * blast;

        // 电击：翼面每隔几十毫秒换一次折线，偶尔整片闪掉
        if (t - s.zapT > 0.055F) {
            s.zapT = t;
            s.zapK = rnd();
        }

        for (int i = 0; i < 2; i++) {
            int sg = i != 0 ? 1 : -1;
            float Rx = rightX * sg, Rz = rightZ * sg;
            double rx = bx + rightX * sg * W * 0.22F, ry = by + H * 0.02F, rz = bz + rightZ * sg * W * 0.22F;
            float fs = s.fswing[i];
            for (int k = 0; k < 3; k++) {
                float th = (ELEV0[k] - 0.45F) * fan * (0.55F + 0.45F * s.open) + 0.45F + s.flap * (1 - 0.3F * k) + 0.06F * (float) Math.sin(t * 1.7F + k);
                float L = span * LEN_K[k];
                float sw = s.sweep - 1.3F * fs - 0.9F * blast;
                float oh = (float) Math.cos(th) * (1 - Math.max(0, sw) * 0.5F), ov = (float) Math.sin(th);
                float jitter = blast * 0.12F + (zap ? 0.02F : 0);
                EphemeraPen.Ribbon fe = feathers[i * 3 + k].clear();
                for (int q = 0; q <= NSEG; q++) {
                    float f = (float) q / NSEG;
                    float wv = (float) Math.sin(t * P.om - f * 3.2F - k * 0.9F + sg * 0.4F) * P.amp * f;
                    double px = rx + Rx * oh * L * f, py = ry + ov * L * f, pz = rz + Rz * oh * L * f;
                    float bk = L * (0.18F * f + sw * 0.95F * f * f), fw = L * wv * 0.8F;
                    px += -fwdX * bk + fwdX * fw;
                    pz += -fwdZ * bk + fwdZ * fw;
                    py += L * (P.curl * f * f + wv);
                    if (fs > 0.01F) {
                        float a1 = L * fs * 0.6F * f * f, a2 = -L * fs * 0.25F * f;
                        px += fwdX * a1 + Rx * a2;
                        pz += fwdZ * a1 + Rz * a2;
                    }
                    if (jitter > 0) {
                        float j1 = (rnd() - 0.5F) * jitter * L * f, j2 = (rnd() - 0.5F) * jitter * L * f;
                        px += rightX * j1;
                        pz += rightZ * j1;
                        py += j2;
                    }
                    float w = H * 0.26F * WID_K[k] * (float) Math.pow(1 - f, 0.65) * (0.3F + 0.7F * smooth(f * 3.5F));
                    if (kind == 1) {
                        w *= 0.8F + 0.4F * noise(t * 9 + f * 4 + k * 3 + i * 7, 3);
                    }
                    fe.add(px, py, pz, w, 1);
                }
            }
        }

        pen.cat = EphemeraStaging.BASE;
        {
            float g = H * 1.1F * (1 + 0.2F * eB);
            pen.s(SOFT).alphaBlend().rgb(deep).a(0.5F).billboard(bx - fwdX * W * 0.2F, by, bz - fwdZ * W * 0.2F, g, g);
            float g2 = H * (calm ? 0.7F : 0.95F);
            pen.s(SOFT).rgb(C).a(0.3F * intensity).billboard(bx, by, bz, g2, g2);
            float g3 = H * (calm ? 0.24F : 0.32F) * (1 + 0.6F * eB + 0.4F * eRun * Bk);
            pen.s(SOFTCORE).rgb(hot).a(0.9F * intensity).billboard(bx, by, bz, g3, g3);
        }

        float flick = zap ? (s.zapK < 0.12F ? 0.25F : 1) : 1;

        // 翼体：三片光羽的中线上罩一条正对镜头的宽柔光带，侧面看翼也是一整块
        for (int i = 0; i < 2; i++) {
            EphemeraPen.Ribbon fa = feathers[i * 3], fb = feathers[i * 3 + 1], fc = feathers[i * 3 + 2];
            EphemeraPen.Ribbon mid = wingMid[i].clear();
            for (int q = 0; q <= NSEG; q++) {
                float f = (float) q / NSEG;
                double cx = (fa.x[q] + fb.x[q] + fc.x[q]) / 3, cy = (fa.y[q] + fb.y[q] + fc.y[q]) / 3, cz = (fa.z[q] + fb.z[q] + fc.z[q]) / 3;
                double sx = fa.x[q] - fc.x[q], sy = fa.y[q] - fc.y[q], sz = fa.z[q] - fc.z[q];
                float spread = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                mid.add(cx, cy, cz, spread * 1.3F + H * 0.24F * (1 - 0.5F * f) + H * 0.08F, volAl(f));
            }
            if (!lite) {
                pen.s(HAZE).alphaBlend().rgb(deep).ribbon(rib.scaled(mid, 1.3F, 0.62F * (sneak ? 0.7F : 1) * (1 - 0.6F * zapDay)), EphemeraPen.FACE_CAM, 0, 1);
            }
            pen.s(HAZE).alphaBlend().rgb(zapDay > 0 ? mix(body, C, 0.6F * zapDay) : body).ribbon(rib.scaled(mid, 0.85F, 0.48F * flick), EphemeraPen.FACE_CAM, 0, 1);
            pen.s(HAZE).rgb(C).ribbon(rib.scaled(mid, 0.7F, 0.5F * intensity * flick * (calm ? 0.55F : 1)), EphemeraPen.FACE_CAM, 0, 1);
        }

        // 相邻两片光羽之间张一层翼膜
        float vu0 = zap ? s.zapK * 0.5F : mod05(-t * Math.max(P.flow, 0.15F) * 0.4F);
        float memA = (zap ? 0.45F + 0.3F * zapDay : 0.9F) * intensity * flick * (calm ? 0.7F : 1);
        for (int i = 0; i < 2; i++) {
            for (int k = 0; k < 2; k++) {
                EphemeraPen.Ribbon fa = feathers[i * 3 + k], fb = feathers[i * 3 + k + 1];
                for (int q = 1; q <= NSEG; q++) {
                    float f0 = (float) (q - 1) / NSEG, f1 = (float) q / NSEG;
                    float ua = vu0 + 0.5F * f0, ub = vu0 + 0.5F * f1;
                    pen.s(VEIL).rgb(C).a(memA).va(wingAl(f0), wingAl(f1), wingAl(f1), wingAl(f0)).uv4(ua, 0, ub, 0, ub, 1, ua, 1)
                            .quad(fa.x[q - 1], fa.y[q - 1], fa.z[q - 1], fa.x[q], fa.y[q], fa.z[q], fb.x[q], fb.y[q], fb.z[q],
                                    fb.x[q - 1], fb.y[q - 1], fb.z[q - 1]);
                }
            }
        }
        for (int fi = 0; fi < 6; fi++) {
            EphemeraPen.Ribbon fe = feathers[fi];
            int i = fi / 3, k = fi % 3;
            float u0 = 0, u1 = 1;
            if (zap) {
                u0 = (s.zapK * 7 + k * 0.31F + i * 0.17F) % 0.5F;
                u1 = u0 + 0.5F;
            } else if (P.flow > 0) {
                u0 = mod05(-t * P.flow * 0.5F + k * 0.13F + i * 0.29F);
                u1 = u0 + 0.5F;
            }
            // 电击的折线本身是细线，亮背景上要靠底下一道暗色折线托住
            if (zap && !lite) {
                pen.s(CRACK_0).alphaBlend().rgb(mul(C, 0.38F)).ribbon(rib.scaled(fe, 2.4F, 0.75F * flick), EphemeraPen.FACE_CAM, u0, u1);
            }
            pen.s(P.glow).rgb(C).ribbon(rib.scaled(fe, zap ? 2.2F : 1.6F, 0.95F * intensity * flick), EphemeraPen.FACE_CAM, u0, u1);
            if (!lite || k == 0) {
                float cu0 = zap ? (u0 + 0.21F) % 0.5F : 0, cu1 = zap ? cu0 + 0.5F : 1;
                pen.s(P.core).rgb(hot).ribbon(rib.scaled(fe, zap ? 1.4F : 0.4F, 0.9F * intensity * flick), EphemeraPen.FACE_CAM, cu0, cu1);
            }
        }

        // 翼缘：最上一片光羽外侧长一排母题凸起，朝向取翼面内「离开中间那片羽」的一侧，镜头转到前面也不会往里长
        // 受击那下的爆发（磁一次近百个面片）放完之前让出来，翼缘先不长
        if (ek && m.since(m.tHurt) >= m.burstTime + 0.1F) {
            float ew = H * 0.06F, ea = Math.min(1, 0.9F * intensity) * (calm ? 0.8F : 1);
            int q0 = lite ? NSEG - 2 : 4;
            for (int i = 0; i < 2; i++) {
                EphemeraPen.Ribbon fe = feathers[i * 3], fm = feathers[i * 3 + 1];
                for (int q = q0; q < NSEG; q += 3) {
                    double tx = fe.x[q + 1] - fe.x[q - 1], ty = fe.y[q + 1] - fe.y[q - 1], tz = fe.z[q + 1] - fe.z[q - 1];
                    double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                    if (tl < 1.0E-6) {
                        continue;
                    }
                    tx /= tl;
                    ty /= tl;
                    tz /= tl;
                    double ox = fe.x[q] - fm.x[q], oy = fe.y[q] - fm.y[q], oz = fe.z[q] - fm.z[q];
                    double dp = ox * tx + oy * ty + oz * tz;
                    ox -= tx * dp;
                    oy -= ty * dp;
                    oz -= tz * dp;
                    double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
                    if (ol < 1.0E-6) {
                        ox = 0;
                        oy = 1;
                        oz = 0;
                    } else {
                        ox /= ol;
                        oy /= ol;
                        oz /= ol;
                    }
                    float len = ew * (m.el == FxMotion.POISON ? 2.2F : 3);
                    motif.accent(fe.x[q], fe.y[q], fe.z[q], ox, oy, oz, len, ew, i * 17 + q,
                            ea * (m.el == FxMotion.MAGNETIC ? 0.6F : 1));
                }
            }
        }

        // 冰：光羽上长出晶片
        if (kind == 2) {
            for (int fi = 0; fi < 6; fi++) {
                EphemeraPen.Ribbon fe = feathers[fi];
                int k = fi % 3;
                int n = lite ? (k == 0 ? 1 : 0) : 3;
                for (int j = 0; j < n; j++) {
                    int qi = Math.min(NSEG - 1, Math.round(NSEG * (0.4F + 0.22F * j)));
                    double tx = fe.x[qi + 1] - fe.x[qi], ty = fe.y[qi + 1] - fe.y[qi], tz = fe.z[qi + 1] - fe.z[qi];
                    float sx = (float) (tx * pen.rightX + ty * pen.rightY + tz * pen.rightZ), sy = (float) (tx * pen.upX + ty * pen.upY + tz * pen.upZ);
                    float sz = H * (0.13F - 0.025F * j) * (1 + 0.3F * eB);
                    float off = (j % 2 != 0 ? 1 : -1) * 0.5F;
                    pen.s(CRYSTAL).rgb(hot).a(0.85F * intensity).billboard(fe.x[qi], fe.y[qi] + sz * 0.25F, fe.z[qi], sz * 0.6F, sz,
                            (float) Math.atan2(-sx, sy) + off);
                }
            }
        }

        // 翼面光幕：记的是翼面中心的世界位置，跑起来拖成两片宽幕，转弯时跟着甩
        int sSeg = lite ? 6 : 12;
        pen.cat = EphemeraStaging.TRAIL;
        for (int i = 0; i < 2; i++) {
            FxTrail tr = s.sheet[i];
            int qi = Math.round(NSEG * 0.7F);
            EphemeraPen.Ribbon mid = wingMid[i], top = feathers[i * 3];
            tr.push(mid.x[qi] + (top.x[qi] - mid.x[qi]) * 0.4, mid.y[qi] + (top.y[qi] - mid.y[qi]) * 0.4, mid.z[qi] + (top.z[qi] - mid.z[qi]) * 0.4, t);
            float lk = smooth((float) (tr.length() - H * 0.4F) / (H * 0.8F));
            if (lk <= 0.01F || fp) {
                continue;
            }
            float wd = Math.max(mid.w[qi] * 1.1F, H * 0.6F) * (1 + 0.3F * spd) * (calm ? 0.85F : 1);
            float calmK = calm ? 0.8F : 1;
            tr.sample(sSeg, sample);
            if (!lite) {
                pen.s(HAZE).alphaBlend().rgb(deep);
                tdraw(tr, sSeg, (q, n) -> wd * 1.3F * (1 - q * 0.4F), (q, n) -> 0.5F * sheetAl(q, lk, calmK), null, 0, 1);
            }
            pen.s(HAZE).alphaBlend().rgb(body);
            tdraw(tr, sSeg, (q, n) -> wd * 0.8F * (1 - q * 0.4F), (q, n) -> 0.42F * sheetAl(q, lk, calmK) * flick, null, 0, 1);
            float u0 = zap ? s.zapK * 0.5F : mod05(-t * 1.1F + i * 0.23F);
            final float fI = intensity;
            pen.s(zap ? FLOW_1 : FLOW_0);
            tdraw(tr, sSeg, (q, n) -> wd * (1 - q * 0.45F), (q, n) -> 0.6F * sheetAl(q, lk, calmK) * fI * flick, null, u0, u0 + 0.5F);
            if (!lite) {
                pen.s(HAZE);
                tdraw(tr, sSeg, (q, n) -> wd * 0.7F * (1 - q * 0.5F), (q, n) -> 0.28F * sheetAl(q, lk, calmK) * fI, null, 0, 1);
            }
        }

        // 翼尖拖尾：亮边
        int[] tipIdx = lite ? TIP_LITE : TIP_FULL;
        for (int n = 0; n < s.tips.length && n < tipIdx.length; n++) {
            EphemeraPen.Ribbon fe = feathers[tipIdx[n]];
            s.tips[n].push(fe.x[NSEG], fe.y[NSEG], fe.z[NSEG], t);
        }
        int tSeg = lite ? 7 : 12;
        // 粒子最后画、没法半截停，拖尾末端的母题按池里现有的数先给粒子留出来
        tailN = 0;
        int reserve = (lite ? EphemeraTier.CAP_REDUCED : EphemeraTier.CAP_FULL) - s.shards.count() - s.plume.count() - s.fx.count()
                - s.motes.count() - motif.particles() - 40;
        if (!fp) {
            for (int n = 0; n < s.tips.length; n++) {
                FxTrail tr = s.tips[n];
                float big = n < 2 ? 1 : 0.7F;
                float wd = H * 0.1F * big * (1 + 0.5F * spd);
                // 站着时翼尖只有很短一截历史，画出来是一小块，按长度淡入
                float lk = smooth((float) (tr.length() - H * 0.5F) / (H * 0.7F));
                if (lk <= 0.01F) {
                    continue;
                }
                tr.sample(tSeg, sample);
                float u0 = zap ? s.zapK * 0.5F : mod05(-t * 0.9F);
                final float fI = intensity;
                if (!lite) {
                    pen.s(HAZE).rgb(C);
                    tdraw(tr, tSeg, (q, i) -> wd * 4 * (1 - q * 0.5F), (q, i) -> 0.2F * fI * (1 - q) * smooth(q / 0.15F) * lk, null, 0, 1);
                }
                if (zap && !lite) {
                    pen.s(CRACK_1).alphaBlend().rgb(mul(C, 0.38F));
                    tdraw(tr, tSeg, (q, i) -> wd * 2.4F * (float) Math.pow(1 - q, 0.8), (q, i) -> 0.7F * (1 - q) * lk, null, u0, u0 + 0.5F);
                }
                pen.s(zap ? CRACK_1 : FLOW_0);
                tdraw(tr, tSeg, (q, i) -> wd * (zap ? 2 : 1.3F) * (float) Math.pow(1 - q, 0.8) * (0.25F + 0.75F * smooth(q / 0.2F)),
                        (q, i) -> 0.9F * fI * flick * (1 - q) * lk, (q, i) -> mix(hot, C, Math.min(1, q * 2.5F)), u0, u0 + 0.5F);
                // 拖尾末端顺着尾巴往后长一截母题；先记下，挥砍、爆发画完再看剩下的预算
                if (ek && n < 2 && sample.n >= 3) {
                    int j = Math.max(1, Math.min(sample.n - 2, Math.round((sample.n - 1) * 0.6F)));
                    double tx = sample.x[j + 1] - sample.x[j - 1], ty = sample.y[j + 1] - sample.y[j - 1], tz = sample.z[j + 1] - sample.z[j - 1];
                    double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                    if (tl > 1.0E-6) {
                        int c = tailN++;
                        tailX[c] = sample.x[j];
                        tailY[c] = sample.y[j];
                        tailZ[c] = sample.z[j];
                        tailD[c * 3] = (float) (tx / tl);
                        tailD[c * 3 + 1] = (float) (ty / tl);
                        tailD[c * 3 + 2] = (float) (tz / tl);
                        tailW[c] = wd * 1.2F;
                        tailA[c] = Math.min(1, 2 * (1 - sample.s[j])) * lk * Math.min(1, intensity);
                    }
                }
            }
        }
        pen.cat = EphemeraStaging.BASE;

        // 挥砍：同侧光羽前扫，扫过的路线留下一道弧光（第一人称在眼前，不画）
        float sSw = m.since(m.tSwing) / m.burstTime;
        if (sSw < 0.55F && !fp) {
            float pr = smooth(sSw / 0.2F), fade = 1 - smooth((sSw - 0.18F) / 0.37F);
            double c0x = ax0, c0y = ay0 + H * 0.05F, c0z = az0;
            float Rs = H * 0.62F + W * 0.7F;
            float axx = rightX * swSide, axy = 0.75F, axz = rightZ * swSide, al = (float) Math.sqrt(axx * axx + axy * axy + axz * axz);
            axx /= al;
            axy /= al;
            axz /= al;
            float a0 = -2.2F, a1 = 1.35F, head = a0 + (a1 - a0) * pr, len = 2.3F;
            int n = lite ? 6 : 14;
            EphemeraPen.Ribbon arc = wingMid[0].clear();
            for (int j = 0; j <= n; j++) {
                float f = (float) j / n, ang = head - len * (1 - f) * (0.4F + 0.6F * pr);
                if (ang < a0 - 0.01F) {
                    continue;
                }
                float c = (float) Math.cos(ang), sn = (float) Math.sin(ang), r = Rs * (0.9F + 0.1F * f);
                arc.add(c0x + (axx * c + fwdX * sn) * r, c0y + axy * c * r, c0z + (axz * c + fwdZ * sn) * r,
                        H * 0.42F * (float) Math.pow(f, 1.1) * (calm ? 0.6F : 1), fade * (float) Math.pow(f, 0.6));
            }
            if (arc.n > 1) {
                pen.s(HAZE).alphaBlend().rgb(lite ? body : deep).ribbon(rib.scaled(arc, 1.4F, 0.5F), EphemeraPen.FACE_CAM, 0, 1);
                pen.s(SLASHBAND).rgb(C).ribbon(arc, EphemeraPen.FACE_CAM, 0, 1);
                pen.s(STREAK).rgb(hot).ribbon(rib.scaled(arc, 0.35F, 1), EphemeraPen.FACE_CAM, 0.5F, 1);
                if (!lite) {
                    pen.s(HAZE).rgb(C).ribbon(rib.scaled(arc, 1.6F, 0.35F), EphemeraPen.FACE_CAM, 0, 1);
                }
            }
        }

        // 落地：光羽下拍，地面扩开一圈
        if (eLand > 0.01F) {
            float sl = m.since(m.tLand) / m.burstTime;
            float r = Math.min(2.3F * sc, H * (0.35F + 1.0F * Bk * smooth(sl / 0.5F)) * Math.min(1.25F, 0.7F + 0.35F * m.landSpeed / 8));
            double g = gy + 0.03;
            pen.s(SOFT).alphaBlend().rgb(deep).a(0.45F * eLand).flat(m.x, g, m.z, r * 2, r * 2);
            pen.s(SHOCK).rgb(C).a(Math.min(1, eLand * 1.2F)).flat(m.x, g + 0.01, m.z, r * 2, r * 2);
        }

        // 受击炸开：背后一圈冲击环 + 一团闪光；毒、冲击、磁、辐射换成母题的爆发（炸开那一刻放）
        if (ek) {
            motif.bursts();
        } else if (blast > 0.01F && !fp) {
            float k = smooth((hk / m.burstTime - 0.08F) / 0.4F), r = H * (0.4F + 0.8F * k * Bk) * (calm ? 0.8F : 1);
            pen.s(SOFT).alphaBlend().rgb(deep).a(0.4F * blast).billboard(bx, by, bz, r * 1.1F, r * 1.1F);
            pen.s(SHOCK).rgb(hot).a(blast).billboard(bx, by, bz, r, r);
            float w = H * 0.8F * blast * Bk;
            pen.s(zap ? FLARE : SOFTCORE).rgb(WHITE).a(blast).billboard(bx, by, bz, w, w, zap ? s.zapK * 6 : 0);
        }

        pen.cat = EphemeraStaging.TRAIL;
        for (int c = 0; c < tailN && d.out.pending() + 16 <= reserve; c++) {
            motif.accent(tailX[c], tailY[c], tailZ[c], tailD[c * 3], tailD[c * 3 + 1], tailD[c * 3 + 2], tailW[c] * 3, tailW[c], 60 + c, tailA[c]);
        }
        pen.cat = EphemeraStaging.BASE;

        float vX = m.velX, vY = m.velY, vZ = m.velZ;
        // 毒、冲击、磁、辐射另有母题从背心冒，这层泛光点少冒一些
        float rate = (lite ? 10 : 40) * (ek ? 0.6F : 1) * (sneak ? 0.4F : 1) * (1 + 1.5F * eB + spd * 0.6F);
        s.acc += dt * rate;
        while (s.acc >= 1) {
            s.acc -= 1;
            int fi = pick();
            EphemeraFx.randDir(dir);
            s.motes.spec().pos(ptX, ptY, ptZ).vel(vX * 0.25F + dir[0] * 0.35F * sc, vY * 0.25F + dir[1] * 0.35F * sc, vZ * 0.25F + dir[2] * 0.35F * sc)
                    .life(0.5F + rnd() * 0.5F).size(H * (0.035F + rnd() * 0.03F), 0).sprite(rnd() < 0.3F ? FLARE : SOFTCORE)
                    .rgb(rnd() < 0.5F ? hot : C).drag(2).grav(kind == 2 ? H * 0.8F : kind == 1 ? -H * 1.2F : 0).rot(rnd() * 6).spawn();
        }
        // 羽间柔雾：大而淡，速度只继承一小半，跑起来自己落在翼后面
        s.accM += dt * (lite ? 6 : 22) * (sneak ? 0.5F : 1) * (1 + 0.5F * spd);
        while (s.accM >= 1) {
            s.accM -= 1;
            pick();
            s.plume.spec().pos(ptX, ptY, ptZ).vel(vX * 0.3F, (kind == 2 ? -0.25F : kind == 1 ? 0.5F : 0.1F) * sc, vZ * 0.3F).life(0.7F)
                    .size(H * 0.3F, H * 0.6F).sprite(SOFT).rgb(C).a(0.2F).fade(0.3F, 0.6F).spawn();
        }
        s.accF += ek ? 0 : dt * (lite ? 8 : 32) * (sneak ? 0.4F : 1) * (1 + eB);
        while (s.accF >= 1) {
            s.accF -= 1;
            pick();
            EphemeraFx.randDir(dir);
            if (kind == 1) {
                s.fx.spec().pos(ptX, ptY, ptZ).vel(vX * 0.3F, vY * 0.3F + 0.6F * sc, vZ * 0.3F).life(0.45F).size(H * 0.1F, H * 0.02F)
                        .sprite(rnd() < 0.5F ? FLAME_0 : FLAME_0 + 1).rgb(mix(C, hot, rnd() * 0.4F)).a(0.8F).grav(-H * 1.6F).fade(0.15F, 0.5F).spawn();
            } else if (kind == 2) {
                s.fx.spec().pos(ptX, ptY, ptZ).vel(vX * 0.15F + dir[0] * 0.2F * sc, vY * 0.15F + dir[1] * 0.2F * sc, vZ * 0.15F + dir[2] * 0.2F * sc)
                        .life(1.1F).size(H * 0.05F, H * 0.03F).sprite(rnd() < 0.5F ? CRYSTAL : FLARE).rgb(hot).grav(H * 0.9F).drag(1.5F)
                        .rot(rnd() * 6).spin((rnd() - 0.5F) * 4).floor(gy + 0.02).spawn();
            } else if (zap) {
                if (rnd() < 0.5F) {
                    continue;
                }
                s.fx.spec().pos(ptX, ptY, ptZ).vel(dir[0] * 3.5F * sc, dir[1] * 3.5F * sc, dir[2] * 3.5F * sc).life(0.12F).size(H * 0.025F)
                        .sprite(EphemeraSprites.WHITE).rgb(WHITE).stretch(0.03F).drag(4).spawn();
            } else {
                s.fx.spec().pos(ptX, ptY, ptZ).vel(vX * 0.2F + dir[0] * 0.25F * sc, vY * 0.2F + dir[1] * 0.25F * sc, vZ * 0.2F + dir[2] * 0.25F * sc)
                        .life(0.7F).size(H * 0.06F, 0).sprite(DOT).rgb(hot).drag(1).spawn();
            }
        }

        if (m.justLand) {
            burstOut(s.motes, lite ? 6 : 20, 0, bx, by, bz, -0.6F, 4, 0.5F, H, sc, hot, fwdX, fwdZ);
        }
        if (m.justJump) {
            burstOut(s.motes, lite ? 5 : 14, 1, bx, by, bz, -2, 3.5F, 0.45F, H, sc, hot, fwdX, fwdZ);
        }
        if (m.justRunStart) {
            burstOut(s.motes, lite ? 5 : 14, 2, bx, by, bz, -2.5F, 4, 0.5F, H, sc, hot, fwdX, fwdZ);
        }
        if (m.justStop) {
            burstOut(s.motes, lite ? 5 : 14, 2, bx, by, bz, 2.5F, 3.5F, 0.5F, H, sc, hot, fwdX, fwdZ);
        }
        // 受击的羽片在炸开那一刻（收拢之后）甩出去
        if (hk >= 0.08F && hk < 0.08F + dt * 1.5F && s.seenBlast != m.nHurt) {
            s.seenBlast = m.nHurt;
            if (ek) {
                motif.fire(m.x, gy, m.z, W * 1.6F, 0.9F);
            }
            int n = lite ? 10 : 36;
            for (int j = 0; j < n; j++) {
                EphemeraPen.Ribbon fe = feathers[j % 6];
                float q = 0.35F + 0.65F * ((float) (j / 6) / Math.max(1, n / 6 - 1));
                int pi = Math.min(NSEG, Math.round(q * NSEG));
                EphemeraFx.randDir(dir);
                double dx = fe.x[pi] - bx + dir[0] * 0.35, dy = fe.y[pi] - by + dir[1] * 0.35, dz = fe.z[pi] - bz + dir[2] * 0.35;
                double l = Math.max(1.0E-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
                float sp = (4 + rnd() * 4) * sc;
                s.shards.spec().pos(fe.x[pi], fe.y[pi], fe.z[pi]).vel((float) (dx / l) * sp, (float) (dy / l) * sp, (float) (dz / l) * sp)
                        .life(0.45F).size(H * 0.07F, H * 0.02F).sprite(zap ? EphemeraSprites.WHITE : STREAK).rgb(j % 3 != 0 ? C : hot)
                        .stretch(0.05F).drag(3.5F).force().spawn();
            }
        }
        if (sinceB < dt * 1.5F) {
            burstOut(s.motes, lite ? 5 : 16, 3, bx, by, bz, 0.3F, 2.5F, 0.7F, H, sc, hot, fwdX, fwdZ);
        }

        // 电击：翼尖之间偶尔连一道弧
        if (zap && s.zapK > 0.55F) {
            EphemeraPen.Ribbon a = feathers[0], b = feathers[3];
            pen.s(CRACK_0).alphaBlend().rgb(mul(C, 0.38F)).a(0.7F).beam(a.x[NSEG], a.y[NSEG], a.z[NSEG], b.x[NSEG], b.y[NSEG], b.z[NSEG], H * 0.2F);
            pen.s(s.zapK > 0.8F ? CRACK_0 : CRACK_1).rgb(hot).a(0.85F * intensity)
                    .beam(a.x[NSEG], a.y[NSEG], a.z[NSEG], b.x[NSEG], b.y[NSEG], b.z[NSEG], H * 0.14F);
            if (!lite) {
                EphemeraPen.Ribbon a2 = feathers[1], b2 = feathers[(int) (s.zapK * 10) % 2 != 0 ? 0 : 2];
                pen.s(CRACK_1).rgb(hot).a(0.7F * intensity).beam(a2.x[NSEG], a2.y[NSEG], a2.z[NSEG], b2.x[NSEG], b2.y[NSEG], b2.z[NSEG], H * 0.1F);
            }
        }

        final float jit = 3 * sc;
        FxParticles.Step step = kind == 1 ? (p, i, dtt) -> {
            p.vx[i] += (rnd() - 0.5F) * jit * dtt;
            p.vz[i] += (rnd() - 0.5F) * jit * dtt;
        } : null;
        s.motes.step(dt, step);
        s.plume.step(dt, null);
        s.fx.step(dt, step);
        s.shards.step(dt, null);
        pen.cat = EphemeraStaging.PARTICLE;
        s.shards.draw(pen, m.size, 1, Float.NaN);
        s.plume.draw(pen, m.size, intensity, Float.NaN);
        s.fx.draw(pen, m.size, 1, Float.NaN);
        s.motes.draw(pen, m.size, intensity, Float.NaN);
        if (ek) {
            sites.clear();
            sites.add(bx - fwdX * W * 0.15F, by, bz - fwdZ * W * 0.15F, -fwdX, 0, -fwdZ);
            for (int i = 0; i < 2; i++) {
                EphemeraPen.Ribbon fe = feathers[i * 3 + 1];
                for (int q = NSEG / 2; q <= NSEG; q += NSEG - NSEG / 2) {
                    double ox = fe.x[q] - bx, oy = fe.y[q] - by, oz = fe.z[q] - bz, ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
                    if (ol > 1.0E-6) {
                        sites.add(fe.x[q], fe.y[q], fe.z[q], (float) (ox / ol), (float) (oy / ol), (float) (oz / ol));
                    }
                }
            }
            motif.motes(sites, 13 * (sneak ? 0.4F : 1) * (1 + eB) * (calm ? 0.5F + 0.5F * act : 1), sc);
        }
    }

    private double ptX, ptY, ptZ;

    /** 随便挑一片光羽外段上的一点，放进 ptX/ptY/ptZ */
    private int pick() {
        int fi = (int) (rnd() * 6);
        float q = 0.3F + rnd() * 0.7F;
        int pi = Math.min(nseg, (int) Math.floor(q * nseg));
        EphemeraPen.Ribbon fe = feathers[fi];
        ptX = fe.x[pi];
        ptY = fe.y[pi];
        ptZ = fe.z[pi];
        return fi;
    }

    /**
     * 从光羽上往外甩一把：mode 0 = 背心往外再往下压（落地）、1 = 往下（起跳）、2 = 沿前后（起跑 / 急停，k 正负定方向）、
     * 3 = 背心往外（鼓动）
     */
    private void burstOut(FxParticles motes, int n, int mode, double bx, double by, double bz, float k, float spdK, float life,
                          float H, float sc, int hot, float fwdX, float fwdZ) {
        for (int j = 0; j < n; j++) {
            pick();
            EphemeraFx.randDir(dir);
            double dx, dy, dz;
            switch (mode) {
                case 0 -> {
                    dx = ptX - bx;
                    dy = ptY - by + k;
                    dz = ptZ - bz;
                }
                case 1 -> {
                    dx = dir[0];
                    dy = dir[1] + k;
                    dz = dir[2];
                }
                case 2 -> {
                    dx = dir[0] + fwdX * k;
                    dy = dir[1];
                    dz = dir[2] + fwdZ * k;
                }
                default -> {
                    dx = ptX - bx + dir[0] * k;
                    dy = ptY - by + dir[1] * k;
                    dz = ptZ - bz + dir[2] * k;
                }
            }
            double l = Math.max(1.0E-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
            float sp = spdK * sc * (0.6F + rnd() * 0.8F);
            motes.spec().pos(ptX, ptY, ptZ).vel((float) (dx / l) * sp, (float) (dy / l) * sp, (float) (dz / l) * sp).life(life)
                    .size(H * 0.06F, 0).sprite(SOFTCORE).rgb(hot).drag(3).force().spawn();
        }
    }

    private static float wingAl(float f) {
        return smooth(f * 4) * (1 - smooth((f - 0.5F) / 0.5F));
    }

    private static float volAl(float f) {
        return smooth(f * 3) * (1 - smooth((f - 0.62F) / 0.38F));
    }

    private static float sheetAl(float q, float lk, float calmK) {
        return (float) Math.pow(1 - q, 1.2) * smooth(q / 0.15F) * lk * calmK;
    }
}
