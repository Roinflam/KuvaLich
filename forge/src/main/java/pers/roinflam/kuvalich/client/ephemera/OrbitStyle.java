package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;

/**
 * 星轨：几颗光核在倾斜的轨道上绕身体转，各拖一道弧形光迹，跑起来拉成螺旋，整团身后再拖一条宽尾；
 * 挥砍时一颗脱轨沿刀路划一道，每隔几秒收拢到胸前再迸散。火的光核带火舌、冰是晶体慢转、电的轨道一跳一跳。
 */
public final class OrbitStyle extends ProtoStyle<OrbitStyle.St> {

    private static final float[] RAD_K = {1.0F, 1.22F, 0.86F, 1.1F, 0.95F};
    private static final float[] TILT_K = {1, -0.8F, 0.55F, -1.15F, 0.2F};
    private static final float[] DIR_K = {1, -1, 1, -1, 1};

    public static final class St extends ProtoStyle.State {
        boolean ready, lite;
        float sc;
        final float[] ph = new float[5];
        final FxTrail[] trails = new FxTrail[5];
        FxTrail wake;
        FxParticles dust, shed;
        float burstT, nextBurst;
        float offX, offY, offZ;
        float zapT, zapK;
        float acc0, acc1;
        final ElementMotif fx = new ElementMotif();
        final ElementMotif.Sites sites = new ElementMotif.Sites();
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(32);
    private final EphemeraPen.Ribbon tail = new EphemeraPen.Ribbon(8);
    private final FxTrail.Sample[] samples = new FxTrail.Sample[6];
    private final double[] hx = new double[5], hy = new double[5], hz = new double[5];
    private final float[] dir = new float[3];

    // 这一帧的量
    private boolean calm;

    public OrbitStyle() {
        for (int i = 0; i < samples.length; i++) {
            samples[i] = new FxTrail.Sample();
        }
    }

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
        s.ready = false;
        s.fx.reset();
    }

    private void init(St s, FxMotion m) {
        s.ready = true;
        s.lite = m.lite;
        s.sc = m.sc;
        FxParamsOrbit p = FxParamsOrbit.of(m.kind);
        for (int i = 0; i < 5; i++) {
            s.ph[i] = i * TAU / 5 + 0.3F * i;
            s.trails[i] = new FxTrail(p.life, 4 * m.sc, 0.025F * m.sc, 3 * m.sc);
        }
        s.wake = new FxTrail(0.75F, 3.8F * m.sc, 0.03F * m.sc, 3 * m.sc);
        s.dust = new FxParticles(m.lite ? 14 : 60);
        s.shed = new FxParticles(m.lite ? 14 : 60);
        float t = (float) m.t;
        s.burstT = -10;
        s.nextBurst = t + 2.0F;
        s.offX = s.offY = s.offZ = 0;
        s.zapT = 0;
        s.zapK = 0;
        s.acc0 = s.acc1 = 0;
    }

    private record FxParamsOrbit(float om, float life, float tilt, float drop, float bob) {
        static final FxParamsOrbit FIRE = new FxParamsOrbit(4.4F, 0.7F, 0.75F, 0, 0.08F);
        static final FxParamsOrbit ICE = new FxParamsOrbit(2.4F, 0.95F, 0.35F, 0.16F, 0.02F);
        static final FxParamsOrbit ZAP = new FxParamsOrbit(4.0F, 0.5F, 0.85F, 0, 0);
        static final FxParamsOrbit BASE = new FxParamsOrbit(3.6F, 0.75F, 0.7F, 0, 0.05F);

        static FxParamsOrbit of(int kind) {
            return switch (kind) {
                case 1 -> FIRE;
                case 2 -> ICE;
                case 3 -> ZAP;
                default -> BASE;
            };
        }
    }

    private FxTrail.Sample sampleOf(FxTrail tr, int slot, int segs) {
        return tr.sample(segs, samples[slot]);
    }

    /** 星轨自己的光迹比底座默认收得少（这款靠光迹认） */
    private void tdraw(FxTrail.Sample pts, FxTrail.Fn width, FxTrail.Fn alpha, FxTrail.Fn rgb, float u0, float u1) {
        FxTrail.draw(pen, pts, rib, width, alpha, rgb, calm ? 0.78F : 1, calm ? 0.88F : 1, EphemeraPen.FACE_CAM, u0, u1);
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
        double ax0 = m.x, ay0 = m.y + H * 0.555, az0 = m.z;
        double gy = m.groundY;
        float fwdX = m.fwdX, fwdZ = m.fwdZ, rightX = m.rightX, rightZ = m.rightZ;
        int C = m.color, deep = mul(C, 0.14F), body = mul(C, 0.42F), hot = mix(C, WHITE, 0.55F);
        int kind = m.kind;
        boolean zap = kind == 3;
        // 毒、冲击、磁、辐射：光核换形、光迹尾端和散落物走元素母题；火、冰、电照原来的画法
        boolean ek = kind == 0;
        ElementMotif em = s.fx;
        int from = d.out.pending();
        if (ek) {
            em.begin(pen, m);
        }
        FxParamsOrbit P = FxParamsOrbit.of(kind);
        int N = lite ? 4 : 5;
        float B = m.burst, act = m.act, ib = calm ? m.idle : 1;
        s.dust.thin = m.emit;
        s.shed.thin = m.emit;
        boolean fp = d.firstPersonSelf;

        if (t >= s.nextBurst) {
            s.burstT = t;
            s.nextBurst = t + 3.6F + rnd() * 1.6F;
        }
        float sinceB = t - s.burstT;
        float eJump = m.env(m.since(m.tJump), 0.08F, 0.15F, 0.4F);
        float eLand = m.env(m.since(m.tLand), 0.03F, 0.08F, 0.55F) * EphemeraFx.clamp(m.landSpeed / 8, 0.6F, 1.4F);
        float eRun = m.env(m.since(m.tRunStart), 0.06F, 0.1F, 0.5F);
        float eStop = m.env(m.since(m.tStop), 0.05F, 0.08F, 0.6F);
        float eHurt = m.env(m.since(m.tHurt), 0.02F, 0.15F, 0.45F);
        boolean sneak = m.sneak;
        float spd = EphemeraFx.clamp(m.speed / 5.6F, 0, 1);
        if (zap && t - s.zapT > 0.05F) {
            s.zapT = t;
            s.zapK = rnd();
        }

        // 收拢再迸散：前 0.4 秒收到胸前，之后猛地甩出去再回到原轨
        float rK = 1, flash = 0;
        if (sinceB < 0.4F) {
            rK = 1 - 0.72F * smooth(sinceB / 0.4F);
        } else if (sinceB < 1.3F) {
            float a = sinceB - 0.4F;
            rK = a < 0.12F ? 0.28F + 1.27F * smooth(a / 0.12F) : 1 + 0.55F * (1 - smooth((a - 0.12F) / 0.75F));
            flash = m.env(a, 0.02F, 0.05F, 0.45F);
        }
        float conv = sinceB < 0.4F ? smooth(sinceB / 0.4F) : 0;
        rK = 1 + (rK - 1) * ib;
        flash *= ib;
        conv *= ib;
        rK *= (sneak ? 0.62F : 1) * (1 + (0.35F * eLand + 0.4F * eHurt) * B);

        float offT = -W * (0.35F * spd + 1.1F * eRun) + W * 1.5F * eStop;
        float kOff = 1 - (float) Math.exp(-dt * 7);
        s.offX += (fwdX * offT - s.offX) * kOff;
        s.offZ += (fwdZ * offT - s.offZ) * kOff;
        double ccx = ax0 + s.offX, ccy = ay0 - H * P.drop + (sneak ? H * 0.12 : 0), ccz = az0 + s.offZ;
        if (eLand > 0) {
            float k = eLand * 0.75F;
            ccx += (m.x - ccx) * k;
            ccy += (gy + H * 0.12 - ccy) * k;
            ccz += (m.z - ccz) * k;
        }
        float R0 = (W * 1.0F + H * 0.3F) * rK * (calm ? 0.86F + 0.1F * act : 1);
        float omK = (sneak ? 0.5F : 1) * (1 + 1.2F * conv + 0.6F * eRun + 0.4F * spd);
        float inten = (sneak ? 0.55F : 1) * (1 + 0.6F * flash + 0.3F * conv);

        pen.cat = EphemeraStaging.BASE;
        if (!fp) {
            pen.s(SOFT).alphaBlend().rgb(deep).a(0.45F).billboard(ccx, ccy, ccz, H * 1.6F, H * 1.6F);
            float g = H * (calm ? 0.95F : 1.25F) * (1 + 0.3F * flash);
            pen.s(SOFT).rgb(C).a(0.18F * inten + 0.25F * conv).billboard(ccx, ccy, ccz, g, g);
        }

        float sSw = m.since(m.tSwing);
        int swIdx = m.swingCount % N, swSide = m.swingCount % 2 != 0 ? 1 : -1;
        for (int i = 0; i < N; i++) {
            s.ph[i] += dt * P.om * omK * DIR_K[i] * (1 - 0.12F * i);
            float th = P.tilt * TILT_K[i];
            th = th * (1 - eLand) + ((float) Math.PI / 2 - 0.25F) * Math.signum(TILT_K[i]) * eJump * (1 - eLand);
            float az = i * 1.6F + t * 0.35F * (i % 2 != 0 ? -1 : 1);
            float e1x = (float) Math.cos(az), e1z = (float) Math.sin(az);
            // n = up·cos th + (ax × up)·sin th；e2 = n × e1
            float nx = -e1z * (float) Math.sin(th), ny = (float) Math.cos(th), nz = e1x * (float) Math.sin(th);
            float e2x = ny * e1z - nz * 0, e2y = nz * e1x - nx * e1z, e2z = nx * 0 - ny * e1x;
            float el = (float) Math.sqrt(e2x * e2x + e2y * e2y + e2z * e2z);
            if (el > 1.0E-6F) {
                e2x /= el;
                e2y /= el;
                e2z /= el;
            }
            float ph = s.ph[i];
            if (zap) {
                int seg = (int) Math.floor(ph / 0.55F);
                ph = seg * 0.55F + (hash(seg * 7 + i) - 0.5F) * 0.3F;
            }
            float r = Math.min(2.2F * sc, R0 * RAD_K[i] * (1 + 0.25F * eHurt * (float) Math.sin(t * 40 + i * 2)));
            double px = ccx + e1x * Math.cos(ph) * r + e2x * Math.sin(ph) * r;
            double py = ccy + e2y * Math.sin(ph) * r;
            double pz = ccz + e1z * Math.cos(ph) * r + e2z * Math.sin(ph) * r;
            py += H * P.bob * Math.sin(t * 2.3 + i * 1.7) + H * 0.35 * eJump * (i % 2 != 0 ? 1 : -0.4);
            if (kind == 1) {
                py += H * 0.06 * Math.sin(t * 11 + i * 3);
            }
            // 挥砍：这一颗脱轨沿刀路走一遍，再回轨
            if (i == swIdx && sSw < 0.6F) {
                float pr = smooth(sSw / 0.24F), ang = -2.1F + 3.6F * pr;
                float a2x = rightX * swSide, a2y = 0.7F, a2z = rightZ * swSide;
                float al = (float) Math.sqrt(a2x * a2x + a2y * a2y + a2z * a2z);
                a2x /= al;
                a2y /= al;
                a2z /= al;
                float Rs = H * 0.62F + W * 0.75F;
                double apx = ax0 + a2x * Math.cos(ang) * Rs + fwdX * Math.sin(ang) * Rs;
                double apy = ay0 + a2y * Math.cos(ang) * Rs;
                double apz = az0 + a2z * Math.cos(ang) * Rs + fwdZ * Math.sin(ang) * Rs;
                float back = sSw < 0.3F ? 1 : 1 - smooth((sSw - 0.3F) / 0.3F);
                px += (apx - px) * back;
                py += (apy - py) * back;
                pz += (apz - pz) * back;
            }
            // 轨道斜着会扫到地下，光核贴着地面滑过去
            double floor = gy + H * 0.14;
            if (py < floor) {
                py = floor;
            }
            hx[i] = px;
            hy[i] = py;
            hz[i] = pz;
            s.trails[i].push(px, py, pz, t);
        }

        if (!fp) {
            // 光核先画，精简档的预算再紧也不会把核挤掉
            for (int i = 0; i < N; i++) {
                double px = hx[i], py = hy[i], pz = hz[i];
                float sz = H * (lite ? 0.34F : 0.3F) * (1 + 0.5F * flash + (i == swIdx && sSw < 0.4F ? 0.5F * B : 0)) * (sneak ? 0.7F : 1)
                        * (calm ? 0.8F + 0.1F * act : 1);
                pen.s(SOFT).alphaBlend().rgb(body).a(0.5F).billboard(px, py, pz, sz * 2.4F, sz * 2.4F);
                pen.s(SOFT).rgb(C).a(0.35F * inten).billboard(px, py, pz, sz * 2.8F, sz * 2.8F);
                if (kind == 2) {
                    pen.s(CRYSTAL).rgb(hot).a(0.95F * inten).billboard(px, py, pz, sz * 0.75F, sz * 1.25F, t * 1.5F + i);
                    if (!lite) {
                        pen.s(SOFTCORE).rgb(WHITE).a(0.6F * inten).billboard(px, py, pz, sz * 0.9F, sz * 0.9F);
                    }
                } else if (kind == 1) {
                    FxTrail tr = s.trails[i];
                    int back = Math.min(3, tr.points() - 1);
                    double vx = px - tr.x(back), vy = py - tr.y(back), vz = pz - tr.z(back);
                    float tx = (float) (vx * pen.rightX + vy * pen.rightY + vz * pen.rightZ);
                    float ty = (float) (vx * pen.upX + vy * pen.upY + vz * pen.upZ);
                    // 火舌尖朝运动反方向偏上，像被风扯着
                    float rot = (float) Math.atan2(tx, -ty) * 0.6F;
                    if (!lite) {
                        pen.s(i % 2 != 0 ? FLAME_0 : FLAME_0 + 1).rgb(C).a(0.6F * inten).billboard(px, py + sz * 0.15, pz, sz * 0.55F, sz * 0.9F, rot);
                    }
                    pen.s(SOFTCORE).rgb(hot).a(inten).billboard(px, py, pz, sz * 0.9F, sz * 0.9F);
                } else if (ek) {
                    core(m, em, i, px, py, pz, sz, inten, lite);
                } else {
                    pen.s(SOFTCORE).rgb(hot).a(inten).billboard(px, py, pz, sz, sz);
                    if (!lite || zap) {
                        pen.s(FLARE).rgb(zap ? WHITE : hot).a((zap ? (s.zapK > 0.3F ? 0.9F : 0.2F) : 0.75F) * inten)
                                .billboard(px, py, pz, sz * 1.6F, sz * 1.6F, zap ? s.zapK * 6 : t * 0.8F + i);
                    }
                }
            }
        }

        // 整团星群的宽尾：记的是轨道中心，跑起来身后一条宽而淡的弧，转弯跟着甩
        s.wake.push(ccx, ccy, ccz, t);
        if (!fp) {
            pen.cat = EphemeraStaging.TRAIL;
            float lk = smooth((float) (s.wake.length() - H * 0.3F) / (H * 0.9F));
            if (lk > 0.01F) {
                int seg = lite ? 6 : 14;
                float wd = R0 * 1.5F * (1 + 0.3F * spd) * (calm ? 0.75F : 1);
                FxTrail.Sample pts = sampleOf(s.wake, 5, seg);
                final float fInten = inten;
                pen.s(HAZE).alphaBlend().rgb(body);
                tdraw(pts, (q, i) -> wd * (1 - q * 0.5F), (q, i) -> 0.45F * wakeAl(q, lk), null, 0, 1);
                float u0 = zap ? s.zapK * 0.5F : ((t * 0.7F) % 0.5F);
                pen.s(zap ? FLOW_1 : FLOW_0);
                tdraw(pts, (q, i) -> wd * 0.8F * (1 - q * 0.55F), (q, i) -> 0.7F * wakeAl(q, lk) * fInten, null, u0, u0 + 0.5F);
                if (!lite) {
                    pen.s(HAZE);
                    tdraw(pts, (q, i) -> wd * 1.1F * (1 - q * 0.5F), (q, i) -> 0.5F * wakeAl(q, lk) * fInten, null, 0, 1);
                }
            }

            // 各颗的光迹：底下一层半透明柔光带托住颜色，外面宽而淡的光晕，中间流纹，最里一条亮芯
            int seg = lite ? 8 : 14;
            for (int i = 0; i < N; i++) {
                FxTrail.Sample pts = sampleOf(s.trails[i], i, seg);
                if (pts.n < 2) {
                    continue;
                }
                float big = (i == swIdx && sSw < 0.6F ? 1 + 0.5F * B : 1) * (1 + 0.3F * flash);
                float wd = (W * 0.42F + H * 0.05F) * big;
                float fl = zap && ((s.zapK * 13 + i * 0.37F) % 1) < 0.15F ? 0.3F : 1;
                final float fInten = inten;
                pen.s(HAZE).alphaBlend().rgb(body);
                tdraw(pts, (q, k) -> wd * 2.2F * (1 - q * 0.5F), (q, k) -> 0.55F * (1 - q) * smooth(q / 0.08F), null, 0, 1);
                if (!lite) {
                    pen.s(HAZE);
                    tdraw(pts, (q, k) -> wd * 2.8F * (1 - q * 0.4F),
                            (q, k) -> 0.3F * fInten * fl * (float) Math.pow(1 - q, 1.2) * smooth(q / 0.1F), null, 0, 1);
                }
                if (zap) {
                    float u0 = (s.zapK * 0.5F + i * 0.11F) % 0.5F;
                    pen.s(CRACK_0);
                    tdraw(pts, (q, k) -> wd * 1.7F * (1 - q * 0.6F), (q, k) -> 0.95F * fInten * fl * (1 - q), null, u0, u0 + 0.5F);
                    pen.s(CRACK_1);
                    tdraw(pts, (q, k) -> wd * (1 - q * 0.6F), (q, k) -> fInten * fl * (1 - q), (q, k) -> mix(WHITE, hot, q), 0.5F - u0, 1 - u0);
                } else {
                    float u0 = (t * (kind == 2 ? 0.2F : 0.8F) + i * 0.13F) % 0.5F;
                    pen.s(kind == 2 ? FLOW_2 : FLOW_0);
                    tdraw(pts, (q, k) -> wd * 1.35F * (float) Math.pow(1 - q, 0.7), (q, k) -> 0.9F * fInten * (1 - q), null, u0, u0 + 0.5F);
                    pen.s(STREAK);
                    tdraw(pts, (q, k) -> wd * 0.45F * (float) Math.pow(1 - q, 0.9), (q, k) -> fInten * (float) Math.pow(1 - q, 1.4),
                            (q, k) -> mix(hot, C, q), 0.5F, 1);
                    if (ek) {
                        // 光迹后半段长出这一元素的凸起；给后面的粒子留位置，总数不过 600
                        int lo = lite ? 3 : 5, hi = lite ? 4 : 8;
                        int room = EphemeraTier.CAP_FULL - 12 - em.particles() - s.shed.count() - (d.out.pending() - from);
                        if (hi < pts.n && room >= (lite ? 1 : 2) * edgeCost(m.el, lite)) {
                            tail.clear();
                            for (int k = lo; k <= hi; k++) {
                                tail.add(pts.x[k], pts.y[k], pts.z[k], 0, 1);
                            }
                            em.edge(tail, wd * 0.35F, 0.75F * inten, i * 7 + 3);
                        }
                    }
                }
            }
            pen.cat = EphemeraStaging.BASE;

            // 电击：光核之间时不时连一道弧
            if (zap) {
                int[][] pairs = lite ? new int[][]{{0, 1}} : new int[][]{{0, 1}, {2, 3}, {1, 2}};
                for (int k = 0; k < pairs.length; k++) {
                    if (((s.zapK * 7 + k * 0.29F) % 1) > 0.55F) {
                        continue;
                    }
                    int a = pairs[k][0], b = pairs[k][1];
                    int spr = (k + (int) (s.zapK * 10)) % 2 != 0 ? CRACK_0 : CRACK_1;
                    if (!lite) {
                        pen.s(spr).alphaBlend().rgb(mul(C, 0.35F)).a(0.7F).beam(hx[a], hy[a], hz[a], hx[b], hy[b], hz[b], H * 0.22F);
                    }
                    pen.s(spr).rgb(hot).a(0.85F * inten).beam(hx[a], hy[a], hz[a], hx[b], hy[b], hz[b], H * 0.16F);
                }
            }

            // 迸散的一下：胸前闪光 + 一圈冲击环
            if (flash > 0.01F) {
                float a = sinceB - 0.4F, k = smooth(a / 0.45F);
                float w = H * (0.6F + 1.2F * k * B);
                pen.s(SHOCK).rgb(hot).a(flash * 0.8F).billboard(ax0, ay0, az0, w, w);
                pen.s(zap ? FLARE : SOFTCORE).rgb(WHITE).a(flash).billboard(ax0, ay0, az0, H * 0.9F * flash, H * 0.9F * flash);
            }
            if (conv > 0.05F && !lite) {
                pen.s(FLARE).rgb(hot).a(conv * 0.8F).billboard(ccx, ccy, ccz, H * 0.5F * conv, H * 0.5F * conv, t * 3);
            }
            // 受击：轨道被撞得往外鼓，身边炸一圈
            if (eHurt > 0.01F) {
                float k = smooth(m.since(m.tHurt) / (0.4F * m.burstTime));
                float w = H * (0.5F + 1.1F * k * B);
                pen.s(SHOCK).rgb(C).a(eHurt).billboard(ccx, ccy, ccz, w, w);
            }
            // 挥砍那一下在刀路中点闪一下
            if (sSw < 0.35F) {
                float e = m.env(sSw - 0.08F, 0.04F, 0.03F, 0.2F);
                if (e > 0.01F) {
                    float w = H * 0.9F * e * B;
                    pen.s(zap ? CRACK_0 : FLARE).rgb(hot).a(e).billboard(hx[swIdx], hy[swIdx], hz[swIdx], w, w, swSide * 0.6F);
                }
            }
        }

        // 落地：光核砸到地面，扩开一圈
        if (eLand > 0.01F) {
            float sl = m.since(m.tLand) / m.burstTime;
            float r = Math.min(2.3F * sc, H * (0.3F + 1.0F * B * smooth(sl / 0.45F)));
            double g = gy + 0.035;
            pen.s(SOFT).alphaBlend().rgb(deep).a(0.45F * eLand).flat(m.x, g, m.z, r * 2, r * 2);
            pen.s(SHOCK).rgb(hot).a(Math.min(1, eLand * 1.3F)).flat(m.x, g + 0.01, m.z, r * 2, r * 2);
        }

        // 散落物：每颗光核沿途掉东西；第一人称看自己时不撒，光核本来就没画
        FxParticles shed = s.shed, dust = s.dust;
        float vX = m.velX, vY = m.velY, vZ = m.velZ;
        if (!fp && ek) {
            // 每颗光核往外冒这一元素的东西，顶替原来的散落物和星尘
            ElementMotif.Sites sites = s.sites.clear();
            for (int i = 0; i < N; i++) {
                float ox = (float) (hx[i] - ccx), oy = (float) (hy[i] - ccy), oz = (float) (hz[i] - ccz);
                float ol = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
                if (ol < 1.0E-4F) {
                    ox = 0;
                    oy = 1;
                    oz = 0;
                    ol = 1;
                }
                sites.add(hx[i], hy[i], hz[i], ox / ol, oy / ol, oz / ol);
            }
            em.motes(sites, 34 * (sneak ? 0.45F : 1) * (1 + 2 * flash) * (calm ? m.amp : 1), sc);
        }
        if (!fp) {
            s.acc0 += ek ? 0 : dt * (lite ? 10 : 40) * (sneak ? 0.4F : 1) * (1 + flash * 3) * (calm ? m.amp : 1);
            while (s.acc0 >= 1) {
                s.acc0 -= 1;
                int h = (int) (rnd() * N);
                EphemeraFx.randDir(dir);
                if (kind == 1) {
                    shed.spec().pos(hx[h], hy[h], hz[h]).vel(vX * 0.2F + dir[0] * 0.3F * sc, vY * 0.2F + dir[1] * 0.3F * sc, vZ * 0.2F + dir[2] * 0.3F * sc)
                            .life(0.55F).size(H * 0.12F, H * 0.03F).sprite(SOFT).rgb(mix(C, hot, rnd() * 0.5F)).a(0.6F).grav(-H * 1.6F)
                            .fade(0.1F, 0.5F).spawn();
                } else if (kind == 2) {
                    shed.spec().pos(hx[h], hy[h], hz[h]).vel(dir[0] * 0.25F * sc, dir[1] * 0.25F * sc, dir[2] * 0.25F * sc).life(1.2F)
                            .size(H * 0.06F, H * 0.04F).sprite(rnd() < 0.6F ? CRYSTAL : FLARE).rgb(hot).grav(H * 1.4F).drag(1.2F)
                            .rot(rnd() * 6).spin((rnd() - 0.5F) * 6).floor(gy + 0.02).spawn();
                } else if (zap) {
                    shed.spec().pos(hx[h], hy[h], hz[h]).vel(dir[0] * 4 * sc, dir[1] * 4 * sc, dir[2] * 4 * sc).life(0.12F)
                            .size(H * 0.025F).sprite(EphemeraSprites.WHITE).rgb(WHITE).stretch(0.03F).spawn();
                } else {
                    shed.spec().pos(hx[h], hy[h], hz[h]).vel(dir[0] * 0.3F * sc, dir[1] * 0.3F * sc, dir[2] * 0.3F * sc).life(0.7F)
                            .size(H * 0.05F, 0).sprite(DOT).rgb(hot).drag(1).spawn();
                }
            }
            // 身周的星尘：轨道壳上慢慢漂，一闪一闪
            s.acc1 += ek ? 0 : dt * (lite ? 6 : 26) * (sneak ? 0.5F : 1) * (calm ? 0.6F : 1);
            while (s.acc1 >= 1) {
                s.acc1 -= 1;
                EphemeraFx.randDir(dir);
                float rr = R0 * (0.8F + rnd() * 0.6F);
                dust.spec().pos(ccx + dir[0] * rr, ccy + dir[1] * rr, ccz + dir[2] * rr)
                        .vel(vX * 0.4F + dir[2] * 0.4F * sc, vY * 0.4F, vZ * 0.4F - dir[0] * 0.4F * sc).life(1.2F + rnd())
                        .size(H * (0.04F + rnd() * 0.04F), 0).sprite(rnd() < 0.5F ? FLARE : DOT).rgb(rnd() < 0.5F ? hot : C).rot(rnd() * 3)
                        .fade(0.3F, 0.5F).grav(kind == 2 ? H * 0.2F : kind == 1 ? -H * 0.4F : 0).spawn();
            }
            if (sinceB >= 0.4F && sinceB < 0.4F + dt * 1.5F) {
                for (int i = 0; i < (lite ? 8 : 24); i++) {
                    EphemeraFx.randDir(dir);
                    float sp = 5 * sc * (0.6F + rnd() * 0.6F);
                    shed.spec().pos(ccx + dir[0] * H * 0.1F, ccy + dir[1] * H * 0.1F, ccz + dir[2] * H * 0.1F).vel(dir[0] * sp, dir[1] * sp, dir[2] * sp)
                            .life(0.6F).size(H * 0.05F, 0).sprite(zap ? EphemeraSprites.WHITE : SOFTCORE).stretch(0.04F).rgb(hot).drag(3).force().spawn();
                }
            }
            if (m.justLand) {
                scatter(shed, N, lite ? 6 : 18, 3.5F, 0.5F, H, sc, hot);
            }
            if (m.justHurt) {
                scatter(shed, N, lite ? 8 : 24, 5.5F, 0.4F, H, sc, hot);
            }
            if (m.justRunStart || m.justStop) {
                scatter(shed, N, lite ? 4 : 12, 2.5F, 0.45F, H, sc, hot);
            }
        }
        final float jit = 3 * sc;
        shed.step(dt, kind == 1 ? (p, i, dtt) -> {
            p.vx[i] += (rnd() - 0.5F) * jit * dtt;
            p.vz[i] += (rnd() - 0.5F) * jit * dtt;
        } : null);
        dust.step(dt, null);
        pen.cat = EphemeraStaging.PARTICLE;
        shed.draw(pen, m.size, inten, Float.NaN);
        dust.draw(pen, m.size, 1, t);
    }

    /** 光核按元素换形：毒是垂着的一滴，冲击是按拍子鼓出去的圈，磁是两颗对转的小核，辐射是明灭的星芒 */
    private void core(FxMotion m, ElementMotif em, int i, double px, double py, double pz, float sz, float inten, boolean lite) {
        float t = (float) m.t;
        switch (m.el) {
            case FxMotion.POISON -> {
                float wob = 0.08F * (float) Math.sin(m.clock() * 5 + i * 1.9F);
                pen.s(DROP).rgb(em.pale).a(0.95F * inten).billboard(px, py, pz, sz * 0.72F * (1 + wob), sz * 1.05F * (1 - wob));
                pen.s(SOFTCORE).rgb(em.hot).a(0.8F * inten).billboard(px, py - sz * 0.14F, pz, sz * 0.5F, sz * 0.5F);
            }
            case FxMotion.IMPACT -> {
                float b = m.beat(i * 0.04F);
                pen.s(SOFTCORE).rgb(em.hot).a(inten).billboard(px, py, pz, sz * (0.75F + 0.35F * b), sz * (0.75F + 0.35F * b));
                pen.s(RING).rgb(em.pale).a((0.45F + 0.55F * b) * inten).billboard(px, py, pz, sz * (1.0F + 0.45F * b), sz * (1.0F + 0.45F * b));
                if (b > 0.05F && !lite) {
                    float w = sz * (1.3F + 2.2F * (1 - b));
                    pen.s(SHOCK).rgb(em.hot).a(0.85F * b * inten).billboard(px, py, pz, w, w);
                }
            }
            case FxMotion.MAGNETIC -> {
                float ang = m.clock() * 6 * (i % 2 != 0 ? -1 : 1) + i * 1.3F;
                float cs = (float) Math.cos(ang) * sz * 0.42F, sn = (float) Math.sin(ang) * sz * 0.42F;
                double ox = pen.rightX * cs + pen.upX * sn, oy = pen.rightY * cs + pen.upY * sn, oz = pen.rightZ * cs + pen.upZ * sn;
                pen.s(SOFTRING).rgb(em.C).a(0.5F * inten).billboard(px, py, pz, sz * 1.15F, sz * 1.15F);
                pen.s(SOFTCORE).rgb(em.hot).a(inten).billboard(px + ox, py + oy, pz + oz, sz * 0.5F, sz * 0.5F);
                pen.s(SOFTCORE).rgb(em.hot).a(inten).billboard(px - ox, py - oy, pz - oz, sz * 0.5F, sz * 0.5F);
            }
            default -> {
                float bl = m.blink(i);
                pen.s(SOFTCORE).rgb(em.hot).a(Math.min(1, bl) * inten).billboard(px, py, pz, sz * 0.55F, sz * 0.55F);
                pen.s(FLARE).rgb(em.hot).a(Math.min(1, bl) * inten).billboard(px, py, pz, sz * 1.5F * (0.75F + 0.25F * bl), sz * 1.5F * (0.75F + 0.25F * bl), 0.785F);
                if (!lite) {
                    pen.s(HEX_0).rgb(em.C).a(0.45F * Math.min(1, bl) * inten).billboard(px, py, pz, sz * 1.2F, sz * 1.2F, t * 0.5F + i);
                }
                if (bl > 1.2F) {
                    pen.s(FLARE).rgb(WHITE).a(0.9F * inten).billboard(px, py, pz, sz * 2.4F, sz * 2.4F);
                }
            }
        }
    }

    /** 母题一根凸起大约几个面片 */
    private static int edgeCost(int el, boolean lite) {
        return switch (el) {
            case FxMotion.POISON -> 13;
            case FxMotion.MAGNETIC -> lite ? 8 : 16;
            case FxMotion.RADIATION -> 6;
            default -> 3;
        };
    }

    private static float wakeAl(float q, float lk) {
        return (float) Math.pow(1 - q, 1.1) * smooth(q / 0.22F) * lk;
    }

    private void scatter(FxParticles shed, int N, int n, float k, float life, float H, float sc, int hot) {
        for (int i = 0; i < n; i++) {
            int h = i % N;
            EphemeraFx.randDir(dir);
            float sp = k * sc * (0.5F + rnd());
            shed.spec().pos(hx[h], hy[h], hz[h]).vel(dir[0] * sp, dir[1] * sp, dir[2] * sp).life(life).size(H * 0.06F, 0)
                    .sprite(SOFTCORE).rgb(hot).drag(2.5F).force().spawn();
        }
    }
}
