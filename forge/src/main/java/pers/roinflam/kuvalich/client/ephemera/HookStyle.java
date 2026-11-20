package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 骨钩：肩背左右对称伸出六根分节骨刺，向外上方张开、尖端回钩，骨缝里的能量沿骨脊流到钩尖；
 * 背心一列发光椎骨把六根连成一副骨架，周身绕着细烟。跑动往后收拢、钩尖拖光尾，起跳往上收、落地往外撑，
 * 挥砍猛地张开前探，受击骨刺乱颤、骨缝闪白。
 */
public final class HookStyle extends ProtoStyle<HookStyle.St> {

    // 柔光带：横向淡出、沿长度不变
    static final int SHEEN = EphemeraSprites.file("hook/haze");
    // 骨面：只沿宽度变，两侧压暗当硬边
    static final int BONE = EphemeraSprites.file("hook/bone");

    // 每根：根部（右、上、前，按 W / H）、伸出方向（外、上、后）、长（按 H）、回钩弯度、粗细、起伏相位
    private static final float[][] ROOT = {{0.36F, 0.86F, -0.45F}, {0.55F, 0.76F, -0.48F}, {0.45F, 0.62F, -0.45F}};
    private static final float[][] OUT = {{0.55F, 1.0F, 0.25F}, {0.95F, 0.55F, 0.35F}, {0.9F, 0.12F, 0.6F}};
    private static final float[] LEN = {0.5F, 0.62F, 0.44F};
    private static final float[] CURL = {2.3F, 2.4F, 2.0F};
    private static final float[] WID = {0.95F, 1.15F, 0.9F};
    private static final float[] PH = {0, 1.1F, 2.2F};
    private static final int NH = 6, NS_MAX = 5, NV_MAX = 4;
    private static final int DARK = 0x08090B;
    // 张开的弹簧 k = 420、钩尖 w0 = 26，dt = 0.04 一步走完误差大，按每步不超过 1/90 秒拆开
    private static final float SUB_HZ = 90;

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        // 爆发另放一份：电、毒、磁连砍两下两圈会叠在一起，只留最新一圈，靠整份清掉再放
        final ElementMotif fxB = new ElementMotif();
        final FxKit.Spring[] tip = new FxKit.Spring[NH];
        final FxTrail[] tr = new FxTrail[NH];
        FxParticles pt;
        boolean ready, lite, seen;
        int seenLand, seenSwing, seenHurt;
        float op, ov, acc;
        double lastEv = Double.NaN;

        St() {
            for (int i = 0; i < NH; i++) {
                tip[i] = new FxKit.Spring();
            }
        }
    }

    private final double[][] px = new double[NH][NS_MAX + 1], py = new double[NH][NS_MAX + 1], pz = new double[NH][NS_MAX + 1];
    private final int[] hk = new int[NH], hsg = new int[NH], hidx = new int[NH];
    private final float[] hwb = new float[NH];
    private final float[][] tipDir = new float[NH][3];
    private final double[] spX = new double[NV_MAX], spY = new double[NV_MAX], spZ = new double[NV_MAX];
    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(3 * NS_MAX + 2);
    private final EphemeraPen.Ribbon rib2 = new EphemeraPen.Ribbon(3 * NS_MAX + 2);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final float[] dir = new float[3];
    private final double[] side = new double[3];
    private final double[] zx = new double[9], zy = new double[9], zz = new double[9];
    // 电弧的端点对：同侧相邻两根，再加左右最下面那对
    private final int[] pairA = new int[5], pairB = new int[5];

    private EphemeraStaging out;
    private boolean liteNow;
    private int cap;
    private int nh, ns;

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
        s.fxB.reset();
        s.ready = false;
        s.seen = false;
    }

    private void init(St s, FxMotion m) {
        s.ready = true;
        s.lite = m.lite;
        s.pt = new FxParticles(m.lite ? 24 : 50);
        s.acc = 0;
        s.op = 0;
        s.ov = 0;
        s.lastEv = Double.NaN;
        for (int i = 0; i < NH; i++) {
            s.tr[i] = new FxTrail(0.28F, 1.4F * m.sc, 0.04F * m.sc, 3 * m.sc);
            s.tip[i].reset();
        }
    }

    private static float mod05(float v) {
        return ((v % 0.5F) + 0.5F) % 0.5F;
    }

    /** 精简档只算底座，完整档按整个幻纹（扣掉留给粒子的） */
    private int left() {
        return liteNow ? EphemeraTier.CAP_REDUCED - out.pending(EphemeraStaging.BASE) : cap - out.pending();
    }

    private static int accentCost(int el, boolean lite) {
        return switch (el) {
            case FIRE -> 2;
            case ICE -> 5;
            case IMPACT -> 3;
            case ELECTRIC, POISON -> 13;
            case MAGNETIC -> lite ? 8 : 17;
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
        liteNow = lite;
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc;
        float act = m.act, amp = m.amp, Bk = m.burst;
        int el = m.el;
        boolean sneak = m.sneak, fp = d.firstPersonSelf;
        float fX = m.fwdX, fZ = m.fwdZ, rX = m.rightX, rZ = m.rightZ;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        s.fxB.begin(pen, m);
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        BodyPoints K = m.pts;
        float T = m.clock(), bt = m.beat(0), bl = m.blink(5), fk = m.flick(5);
        float run = clamp(m.speed / 5.6F, 0, 1);
        float swE = m.env(m.since(m.tSwing), 0.02F, 0.06F, 0.4F), hurtE = m.env(m.since(m.tHurt), 0.02F, 0.12F, 0.55F);
        float jumpE = m.env(m.since(m.tJump), 0.03F, 0.1F, 0.4F), landE = m.env(m.since(m.tLand), 0.01F, 0.06F, 0.45F);
        float slow = el == ICE ? 0.5F : el == POISON ? 0.75F : 1;
        float breath = 0.5F + 0.5F * (float) Math.sin(t * 1.3F * slow);
        float A = (0.55F + 0.45F * act + 0.05F * breath) * bl * fk;
        if (hurtE > 0.05F) {
            A *= 0.75F + 0.25F * hash((int) Math.floor(m.t * 20));
        }
        // 近白的颜色（冲击）加法层叠起来一片白，按亮度压
        float lum = (0.3F * ((C >> 16) & 255) + 0.59F * ((C >> 8) & 255) + 0.11F * (C & 255)) / 255;
        float gK = 1 - 0.5F * clamp((lum - 0.5F) / 0.4F, 0, 1);
        boolean busy = Math.max(swE, Math.max(hurtE, landE)) > 0.05F;
        boolean heavy = el == ELECTRIC || el == POISON || el == MAGNETIC;

        // 爆发先画：电、毒、磁一圈就一百来个面片，后面的副件按剩下的让
        pen.cat = EphemeraStaging.PARTICLE;
        bursts(s, m, heavy, W * 1.5F);
        // 粒子最后画、没法半截停，按池里现有的数先留出来
        cap = EphemeraTier.CAP_FULL - s.pt.count() - fx.particles() - (el == POISON ? 20 : 0) - 16;

        // 张开程度带回弹：挥砍、受击猛张过头再落回
        float ot = Math.max(swE * (0.85F + 0.35F * Bk), hurtE * 0.9F) + 0.3F * landE + 0.22F * bt + 0.05F * breath * amp;
        int nsub = Math.max(1, (int) Math.ceil(dt * SUB_HZ));
        float sdt = dt / nsub;
        for (int n = 0; n < nsub; n++) {
            s.ov += ((ot - s.op) * 420 - s.ov * 17) * sdt;
            s.op += s.ov * sdt;
        }
        float open = Math.max(-0.2F, s.op), op1 = clamp(open, 0, 1);
        float fold = run * (1 - op1);
        float fall = !m.onGround && m.velY < 0 ? clamp(-m.velY / 8, 0, 1) : 0;

        int NS = lite ? 4 : 5;
        ns = NS;
        int boneC = mix(mix(C, WHITE, 0.84F), hot, 0.6F * hurtE * (hash((int) Math.floor(m.t * 25)) > 0.4F ? 1 : 0.3F));
        int boneEnd = mul(boneC, 0.32F);
        float fu = ((-T * 0.9F) % 1 + 1) % 1;
        // 一道能量从根部沿骨缝推到钩尖，过一会儿再来
        float pulseAt = (T * 0.45F) % 1.6F;
        int q45 = (int) Math.floor(m.t / 0.045);

        pen.cat = EphemeraStaging.BASE;
        // 背后的暗托和柔光托底；第一人称低头会糊在眼前，不画
        if (!fp) {
            double mx = m.x - fX * W * 0.7F, my = m.y + H * 0.7F, mz = m.z - fZ * W * 0.7F;
            pen.s(SOFT).alphaBlend().rgb(deep).a(lite ? 0.16F : 0.26F).billboard(mx, my, mz, H * 1.25F, H * 0.95F);
            pen.s(SOFT).rgb(C).a((0.05F + 0.04F * breath * amp + 0.1F * swE + 0.08F * hurtE) * A * gK).billboard(mx, my, mz, H * 1.3F, H * 0.9F);
        }

        // ---- 骨架：左边一组、右边一组，每组从上往下 ----
        nh = 0;
        float sneakK = sneak ? 0.93F : 1;
        for (int si = 0; si < 2; si++) {
            int sg = si == 0 ? -1 : 1;
            for (int k = 0; k < (lite ? 2 : 3); k++) {
                int idx = k * 2 + (sg > 0 ? 1 : 0), h = nh++;
                hk[h] = k;
                hsg[h] = sg;
                hidx[h] = idx;
                double r0x = m.x + rX * sg * ROOT[k][0] * W + fX * ROOT[k][2] * W;
                double r0y = m.y + ROOT[k][1] * H * sneakK;
                double r0z = m.z + rZ * sg * ROOT[k][0] * W + fZ * ROOT[k][2] * W;
                float br = 0.06F * amp * (float) Math.sin(T * 1.4F + PH[k] + (sg > 0 ? 0 : 0.6F))
                        + 0.04F * run * (float) Math.sin(m.walkPhase * 2 + PH[k]);
                float jx = hurtE * 0.5F * (hash(q45 * 13 + idx * 5) - 0.5F), jy = hurtE * 0.5F * (hash(q45 * 17 + idx * 3) - 0.5F);
                float ox = OUT[k][0] * (1 + 0.9F * open - 0.5F * fold + 0.3F * landE + 0.25F * fall - 0.25F * jumpE) + jx;
                float oy = OUT[k][1] + 0.32F * open + br + 0.55F * jumpE - 0.4F * landE - 0.12F * fold + 0.15F * fall + jy;
                float oz = OUT[k][2] * (1 + 1.4F * fold) - 0.45F * open;
                double dX = rX * sg * ox - fX * oz, dY = oy, dZ = rZ * sg * ox - fZ * oz;
                double l = Math.sqrt(dX * dX + dY * dY + dZ * dZ) + 1.0E-9;
                dX /= l;
                dY /= l;
                dZ /= l;
                // 回钩方向：朝下、略朝外朝前，和起始方向正交
                float fw = 0.08F + 0.3F * op1;
                double cX = rX * sg * 0.45F + fX * fw, cY = -1, cZ = rZ * sg * 0.45F + fZ * fw;
                double dot = cX * dX + cY * dY + cZ * dZ;
                cX -= dX * dot;
                cY -= dY * dot;
                cZ -= dZ * dot;
                l = Math.sqrt(cX * cX + cY * cY + cZ * cZ) + 1.0E-9;
                cX /= l;
                cY /= l;
                cZ /= l;
                float len = H * LEN[k] * (sneak ? 0.85F : 1) * (1 + 0.2F * open + 0.03F * breath * amp);
                float curl = CURL[k] * (1 - 0.38F * op1) + 0.35F * fold;
                double[] X = px[h], Y = py[h], Z = pz[h];
                X[0] = r0x;
                Y[0] = r0y;
                Z[0] = r0z;
                for (int j = 0; j < NS; j++) {
                    double th = curl * Math.pow((j + 0.5) / NS, 2.4);
                    double cs = Math.cos(th), sn = Math.sin(th);
                    double ex = dX * cs + cX * sn, ey = dY * cs + cY * sn, ez = dZ * cs + cZ * sn;
                    double el2 = Math.sqrt(ex * ex + ey * ey + ez * ez) + 1.0E-9;
                    double seg = len / NS * (1.12 - 0.24 * j / NS) / el2;
                    X[j + 1] = X[j] + ex * seg;
                    Y[j + 1] = Y[j] + ey * seg;
                    Z[j + 1] = Z[j] + ez * seg;
                }
                // 骨头是硬的，只让钩尖带一点惯性：起跑拖后、急停往前一点
                FxKit.Spring sp = s.tip[idx];
                for (int n = 0; n < nsub; n++) {
                    sp.step(X[NS], Y[NS], Z[NS], sdt, 26, 0.5F, m.trackX, m.trackY, m.trackZ);
                }
                double lx = sp.x - X[NS], ly = sp.y - Y[NS], lz = sp.z - Z[NS];
                double ll = Math.sqrt(lx * lx + ly * ly + lz * lz), lmax = len * 0.3F;
                if (ll > lmax) {
                    lx *= lmax / ll;
                    ly *= lmax / ll;
                    lz *= lmax / ll;
                }
                for (int j = 1; j <= NS; j++) {
                    double f = (double) j / NS;
                    f *= f;
                    X[j] += lx * f;
                    Y[j] += ly * f;
                    Z[j] += lz * f;
                }
                hwb[h] = W * 0.25F * WID[k] * (1 + 0.1F * op1) * (lite ? 1.1F : 1);
                float tx = (float) (X[NS] - X[NS - 1]), ty = (float) (Y[NS] - Y[NS - 1]), tz = (float) (Z[NS] - Z[NS - 1]);
                float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz) + 1.0E-6F;
                tipDir[h][0] = tx / tl;
                tipDir[h][1] = ty / tl;
                tipDir[h][2] = tz / tl;
                s.tr[idx].push(X[NS], Y[NS], Z[NS], m.t);
            }
        }

        // ---- 骨身：暗边、骨面、倒刺、外晕、骨脊流光、亮芯、骨缝光点、钩尖柔光 ----
        for (int h = 0; h < nh; h++) {
            int k = hk[h], sg = hsg[h], idx = hidx[h];
            float wb = hwb[h];
            double[] X = px[h], Y = py[h], Z = pz[h];
            // 分节：每节近端粗、往远端收，骨缝处掐细；骨色从根部象牙白渐暗到钩尖。精简档每节少一个点，暗边那串点正好是它
            rib.clear();
            rib2.clear();
            for (int j = 0; j < NS; j++) {
                for (int e = 0; e < 3; e++) {
                    if (lite && e == 2) {
                        continue;
                    }
                    float f = e == 0 ? 0 : e == 1 ? 0.22F : 0.72F;
                    float g = (j + f) / NS;
                    float w = wb * (1 - 0.7F * g) * (e == 0 ? (j == 0 ? 0.8F : 0.62F) : e == 2 ? 0.86F : 1);
                    double qx = X[j] + (X[j + 1] - X[j]) * f, qy = Y[j] + (Y[j + 1] - Y[j]) * f, qz = Z[j] + (Z[j + 1] - Z[j]) * f;
                    rib.add(qx, qy, qz, w, 1, mix(boneC, boneEnd, (float) Math.pow(g, 1.6)));
                    if (e != 2) {
                        rib2.add(qx, qy, qz, w * 1.25F + W * 0.035F, 1);
                    }
                }
            }
            rib.add(X[NS], Y[NS], Z[NS], 0.0005F, 1, boneEnd);
            rib2.add(X[NS], Y[NS], Z[NS], 0.0005F * 1.25F + W * 0.035F, 1);
            if (!lite && !(busy && heavy)) {
                pen.alphaBlend().rgb(DARK).a(0.6F).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
            }
            pen.s(BONE).alphaBlend().a(0.92F).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);

            // 倒刺：中间那根第二节外上方一根往回勾的小刺
            if (k == 1) {
                double ax = X[1], ay = Y[1], az = Z[1];
                double gx = X[2] - ax, gy = Y[2] - ay, gz = Z[2] - az, gl = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1.0E-9;
                gx /= gl;
                gy /= gl;
                gz /= gl;
                double nx = rX * sg * 0.6F - fX * 0.3F, ny = 1, nz = rZ * sg * 0.6F - fZ * 0.3F;
                double nd = nx * gx + ny * gy + nz * gz;
                nx -= gx * nd;
                ny -= gy * nd;
                nz -= gz * nd;
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1.0E-9;
                double bx = nx / nl - gx * 0.7, by = ny / nl - gy * 0.7, bz = nz / nl - gz * 0.7;
                double bdl = Math.sqrt(bx * bx + by * by + bz * bz) + 1.0E-9;
                double bl2 = wb * 1.5F * (1 + 0.25F * op1) / bdl;
                double cx = ax + (X[2] - ax) * 0.55, cy = ay + (Y[2] - ay) * 0.55, cz = az + (Z[2] - az) * 0.55;
                double tx = cx + bx * bl2, ty = cy + by * bl2, tz = cz + bz * bl2;
                if (!lite) {
                    pen.alphaBlend().rgb(DARK).a(0.6F);
                    kite(cx, cy, cz, tx, ty, tz, wb * 0.95F, 0.25F);
                }
                pen.s(BONE).alphaBlend().rgb(boneC).a(0.9F);
                kite(cx, cy, cz, tx, ty, tz, wb * 0.62F, 0.25F);
                if (!lite) {
                    pen.s(SOFTCORE).rgb(hot).a(0.45F * A * gK).billboard(tx, ty, tz, wb * 0.9F, wb * 0.9F);
                }
            }

            // 外晕：沿骨的一层柔光，动起来才亮
            float glowA = (0.3F + 0.25F * act + 0.4F * op1 + 0.25F * hurtE) * gK * A;
            rib.clear();
            for (int j = 0; j <= NS; j++) {
                if (j % 2 == 0 || j == NS) {
                    float f = (float) j / NS;
                    rib.add(X[j], Y[j], Z[j], wb * (3.4F - 1.6F * f), Math.min(1, 0.35F + f));
                }
            }
            pen.s(SHEEN).rgb(C).a(glowA).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            // 骨脊里的能量流：从根往尖滚，越近钩尖越显
            rib.clear();
            for (int j = 0; j <= NS; j++) {
                float f = (float) j / NS;
                rib.add(X[j], Y[j], Z[j], wb * (0.5F - 0.25F * f), 0.35F + 0.65F * f);
            }
            float u0 = mod05(fu + idx * 0.17F);
            pen.s(FLOW_0).rgb(C).a((0.75F + 0.25F * act) * A * gK).ribbon(rib, EphemeraPen.FACE_CAM, u0, u0 + 0.5F);
            if (!busy && !lite) {
                for (int j = 0; j <= NS; j++) {
                    float f = (float) j / NS;
                    rib.w[j] = wb * 0.1F;
                    rib.a[j] = 0.2F + 0.8F * f;
                }
                pen.rgb(hot).a((0.35F + 0.4F * act) * A * gK).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            }
            // 骨缝的光点：一道脉冲从根推到尖
            for (int j = 1; j < NS; j++) {
                float f = (float) j / NS, e = (f - pulseAt + 0.08F * k) * 5;
                float pw = Math.max((float) Math.exp(-e * e), Math.max(0.8F * swE, hurtE * (hash(q45 * 7 + idx * 11 + j) > 0.5F ? 1 : 0.2F)));
                float sz = wb * (0.75F + 1.3F * pw);
                pen.s(SOFTCORE).rgb(hot).a((0.3F + 0.7F * pw) * A * gK).billboard(X[j], Y[j], Z[j], sz, sz);
            }
            FxKit.glow(pen, X[NS], Y[NS], Z[NS], wb * (2.0F + 1.6F * swE), C, WHITE, (0.6F + 0.4F * swE) * A * gK);
            if (swE > 0.15F && !lite) {
                pen.s(FLARE).rgb(hot).a(swE * 0.8F * A * gK).billboard(X[NS], Y[NS], Z[NS], wb * 5 * swE, wb * 5 * swE, idx);
            }
        }

        // ---- 背心一列椎骨：把两边的钩连成一副骨架 ----
        int NV = lite ? 3 : 4;
        for (int i = 0; i < NV; i++) {
            float f = (float) i / (NV - 1);
            float back = W * (0.42F + 0.06F * (float) Math.sin(f * Math.PI));
            double cx = m.x - fX * back, cy = m.y + H * (0.84F - 0.3F * f) * sneakK, cz = m.z - fZ * back;
            spX[i] = cx;
            spY[i] = cy;
            spZ[i] = cz;
            float hw = W * (0.3F - 0.1F * f) * (1 + 0.15F * op1);
            if (!lite) {
                rib.clear();
                rib.add(cx - rX * hw, cy, cz - rZ * hw, W * 0.035F * 1.35F + W * 0.03F, 1);
                rib.add(cx, cy, cz, W * 0.13F * 1.35F + W * 0.03F, 1);
                rib.add(cx + rX * hw, cy, cz + rZ * hw, W * 0.035F * 1.35F + W * 0.03F, 1);
                pen.alphaBlend().rgb(DARK).a(0.6F).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            }
            rib.clear();
            rib.add(cx - rX * hw, cy, cz - rZ * hw, W * 0.035F, 1);
            rib.add(cx, cy, cz, W * 0.13F, 1);
            rib.add(cx + rX * hw, cy, cz + rZ * hw, W * 0.035F, 1);
            pen.s(BONE).alphaBlend().rgb(boneC).a(0.9F).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            float e = (1 - f) * 0.4F - pulseAt + 0.25F;
            float pw = (float) Math.exp(-e * e * 30);
            float sz = W * (0.14F + 0.2F * pw + 0.12F * swE);
            pen.s(SOFTCORE).rgb(hot).a((0.4F + 0.6F * pw) * A * gK).billboard(cx, cy, cz, sz, sz);
        }
        rib.clear();
        for (int i = 0; i < NV; i++) {
            rib.add(spX[i] - fX * W * 0.03F, spY[i], spZ[i] - fZ * W * 0.03F, W * 0.12F, 0.4F + 0.6F * (float) Math.sin(Math.PI * (i + 0.5F) / NV));
        }
        float fu5 = mod05(fu);
        pen.s(FLOW_0).rgb(C).a((0.45F + 0.35F * act) * A * gK).ribbon(rib, EphemeraPen.FACE_CAM, fu5, fu5 + 0.4F);

        // ---- 元素招牌 ----
        signature(fx, m, el, lite, busy, t, C, hot, pale, deep, A, gK, bt, slow, op1, H, W);

        // ---- 钩尖凸起；电、毒、磁一根十几个面片，最下面那对不长，挥砍时只留中间那对 ----
        sites.clear();
        int cost = accentCost(el, lite);
        boolean onlyMid = lite && (el == ELECTRIC || el == POISON);
        for (int h = 0; h < nh; h++) {
            int k = hk[h], sg = hsg[h], idx = hidx[h];
            float wb = hwb[h];
            float[] td = tipDir[h];
            boolean want = onlyMid ? k == 1 : !heavy || k == 1 || (k == 0 && !busy);
            if (want && left() >= cost) {
                fx.accent(px[h][NS], py[h][NS], pz[h][NS], td[0], td[1], td[2], wb * (2.2F + 1.6F * swE), wb * 0.9F, 80 + idx, 0.85F * A);
            }
            int mj = NS / 2;
            float nx = rX * sg, ny = 0.3F, nz = rZ * sg, nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            sites.add(px[h][mj], py[h][mj], pz[h][mj], nx / nl, ny / nl, nz / nl);
            sites.add(px[h][NS], py[h][NS], pz[h][NS], td[0], td[1], td[2]);
        }

        // ---- 钩尖拖尾 ----
        pen.cat = EphemeraStaging.TRAIL;
        int segs = lite || heavy ? 4 : 6;
        for (int h = 0; h < nh; h++) {
            FxTrail tr = s.tr[hidx[h]];
            float tk = smooth((float) (tr.length() - 0.12F * sc) / (0.45F * sc));
            if (tk <= 0.01F || (!lite && left() < segs)) {
                continue;
            }
            tr.sample(segs, sample);
            if (sample.n < 2) {
                continue;
            }
            float wb = hwb[h];
            rib.clear();
            for (int i = 0; i < sample.n; i++) {
                float q = sample.s[i];
                rib.add(sample.x[i], sample.y[i], sample.z[i], wb * 1.1F * (1 - q) * m.trailW, 0.75F * tk * (1 - q) * A * gK * m.trailA);
            }
            pen.s(FLOW_0).rgb(C).ribbon(rib, EphemeraPen.FACE_CAM, fu5, fu5 + 0.5F);
        }

        // ---- 烟丝：几缕绕肩背打转，跑起来往后扯；环着身子，第一人称不画 ----
        pen.cat = EphemeraStaging.PARTICLE;
        int NW = fp ? 0 : lite ? 2 : busy ? (heavy ? 0 : 3) : heavy ? 4 : 6;
        float wispA = (0.6F + 0.3F * act + 0.2F * swE) * A * gK;
        for (int i = 0; i < NW; i++) {
            if (!lite && left() < 6) {
                break;
            }
            int sg = i % 2 != 0 ? 1 : -1;
            double ax, ay, az;
            if (i < 2) {
                BodyPoints.Pt p = sg > 0 ? K.shoulderR : K.shoulderL;
                ax = p.x;
                ay = p.y;
                az = p.z;
            } else if (i < 4) {
                ax = K.back.x + rX * sg * W * 0.3F;
                ay = K.back.y - H * 0.12F;
                az = K.back.z + rZ * sg * W * 0.3F;
            } else {
                ax = K.neck.x + rX * sg * W * 0.4F - fX * W * 0.3F;
                ay = K.neck.y;
                az = K.neck.z + rZ * sg * W * 0.4F - fZ * W * 0.3F;
            }
            float spd = (0.55F + 0.15F * i) * slow * (i % 3 != 0 ? 1 : -1);
            float lift = H * (0.22F + 0.1F * (float) Math.sin(t * 0.7F + i));
            rib.clear();
            for (int j = 0; j <= 6; j++) {
                float f = j / 6.0F, a = t * spd + i * 1.7F + f * 2.4F, r = W * (0.4F + 0.35F * f) * (1 + 0.3F * op1);
                float o1 = sg * (0.15F * W + (float) Math.cos(a) * r);
                float ob = (float) Math.sin(a) * r * 0.7F + W * 0.25F + run * f * H * 0.4F;
                float oyy = f * lift - H * 0.05F;
                rib.add(ax + rX * o1 - fX * ob, ay + oyy, az + rZ * o1 - fZ * ob,
                        W * (0.28F + 0.3F * f) * (float) Math.sin(Math.PI * (0.1F + 0.85F * f)), (float) Math.sin(Math.PI * f));
            }
            float wu = mod05(-t * 0.25F * slow + i * 0.21F);
            pen.s(FLOW_0).rgb(i % 3 == 2 ? pale : C).a(wispA).ribbon(rib, EphemeraPen.FACE_CAM, wu, wu + 0.5F);
        }

        // ---- 挥砍、受击时钩尖甩出骨屑光点，平时骨节上零星飘出光点 ----
        FxParticles P = s.pt;
        P.thin = Math.min(1, m.emit);
        double evT = m.since(m.tSwing) < m.since(m.tHurt) ? m.tSwing : m.tHurt;
        if (evT != s.lastEv && m.since(evT) < 0.05F) {
            s.lastEv = evT;
            for (int h = 0; h < nh; h++) {
                float wb = hwb[h];
                float[] td = tipDir[h];
                for (int n = 0; n < (lite ? 1 : 2); n++) {
                    EphemeraFx.randDir(dir);
                    float v = rnd(1.5F, 3) * sc;
                    P.spec().pos(px[h][NS], py[h][NS], pz[h][NS])
                            .vel(td[0] * v + dir[0] * 0.8F * sc, td[1] * v + dir[1] * 0.8F * sc, td[2] * v + dir[2] * 0.8F * sc)
                            .life(rnd(0.35F, 0.6F)).size(wb * 0.9F, wb * 0.2F).sprite(SOFTCORE).rgb(hot).a(0.9F)
                            .drag(4).stretch(0.05F).keep().spawn();
                }
            }
        }
        s.acc += dt * (2 + 8 * run + 20 * swE) * (lite ? 0.4F : 1);
        while (s.acc >= 1 && nh > 0) {
            s.acc -= 1;
            int h = Math.min(nh - 1, (int) (rnd() * nh));
            int j = Math.min(NS, 1 + (int) (rnd() * NS));
            float wb = hwb[h];
            EphemeraFx.randDir(dir);
            P.spec().pos(px[h][j], py[h][j], pz[h][j])
                    .vel(dir[0] * 0.25F * sc + m.velX * 0.15F, dir[1] * 0.25F * sc + m.velY * 0.15F, dir[2] * 0.25F * sc + m.velZ * 0.15F)
                    .life(rnd(0.5F, 1)).size(wb * rnd(0.5F, 0.8F), wb * 0.1F).sprite(SOFTCORE)
                    .rgb(rnd() < 0.5F ? hot : C).a(0.7F).drag(2).spawn();
        }
        P.step(dt, null);
        P.draw(pen, m.size, gK, Float.NaN);

        fx.motes(sites, 14 * (0.5F + 0.5F * act), sc);
        pen.cat = EphemeraStaging.BASE;
    }

    /** 落地、挥砍、受击各放一圈元素爆发；电、毒、磁一圈就一百多个面片，连着来时只留最新的一圈 */
    private static void bursts(St s, FxMotion m, boolean only, float r) {
        if (!s.seen) {
            s.seen = true;
            s.seenLand = m.nLand;
            s.seenSwing = m.nSwing;
            s.seenHurt = m.nHurt;
        }
        if (s.seenLand != m.nLand) {
            s.seenLand = m.nLand;
            if (m.since(m.tLand) < 0.2F) {
                fire(s, only, m, r, 1);
            }
        }
        if (s.seenSwing != m.nSwing) {
            s.seenSwing = m.nSwing;
            if (m.since(m.tSwing) < 0.2F) {
                fire(s, only, m, r * 0.8F, 0.8F);
            }
        }
        if (s.seenHurt != m.nHurt) {
            s.seenHurt = m.nHurt;
            if (m.since(m.tHurt) < 0.2F) {
                fire(s, only, m, r, 0.9F);
            }
        }
        s.fxB.bursts();
    }

    private static void fire(St s, boolean only, FxMotion m, float r, float k) {
        if (only) {
            s.fxB.reset();
        }
        s.fxB.fire(m.x, m.groundY, m.z, r, k);
    }

    private void signature(ElementMotif fx, FxMotion m, int el, boolean lite, boolean busy, float t, int C, int hot, int pale, int deep,
                           float A, float gK, float bt, float slow, float op1, float H, float W) {
        int NS = ns;
        float rX = m.rightX, rZ = m.rightZ, fX = m.fwdX, fZ = m.fwdZ;
        switch (el) {
            case FIRE -> {
                if (lite) {
                    return;
                }
                // 骨脊上窜的火舌
                for (int h = 0; h < nh && left() >= 2; h++) {
                    int j = 3;
                    float wb = hwb[h];
                    fx.accent(px[h][j], py[h][j], pz[h][j], 0, 1, 0, wb * (1.6F + 0.6F * (float) Math.sin(t * 5 + j + hidx[h])), wb * 0.9F,
                            50 + hidx[h] * 5 + j, 0.6F * A);
                }
            }
            case ICE -> {
                // 骨节上结出的棱晶，钩尖再接一截冰刺
                for (int h = 0; h < nh; h++) {
                    float wb = hwb[h];
                    int sg = hsg[h], idx = hidx[h];
                    for (int j = lite ? 2 : 1; j < NS; j += 2) {
                        if (left() < 2) {
                            return;
                        }
                        double gx = px[h][j + 1] - px[h][j - 1], gy = py[h][j + 1] - py[h][j - 1], gz = pz[h][j + 1] - pz[h][j - 1];
                        double gl = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1.0E-9;
                        gx /= gl;
                        gy /= gl;
                        gz /= gl;
                        double nx = rX * sg * 0.6F, ny = 0.8F, nz = rZ * sg * 0.6F;
                        double nd = nx * gx + ny * gy + nz * gz;
                        nx -= gx * nd;
                        ny -= gy * nd;
                        nz -= gz * nd;
                        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1.0E-9;
                        float L = wb * (1.6F + 0.9F * ((j * 7 + idx * 3) % 5) / 5);
                        double ax = px[h][j], ay = py[h][j], az = pz[h][j];
                        double tx = ax + nx / nl * L, ty = ay + ny / nl * L, tz = az + nz / nl * L;
                        pen.rgb(pale).a(0.75F * A);
                        kite(ax, ay, az, tx, ty, tz, wb * 0.8F, 0.3F);
                        pen.rgb(WHITE).a(0.85F * A);
                        kite(ax, ay, az, tx, ty, tz, wb * 0.2F, 0.4F);
                        if (lite) {
                            break;
                        }
                    }
                    if (left() < 2) {
                        return;
                    }
                    float[] td = tipDir[h];
                    double ax = px[h][NS], ay = py[h][NS], az = pz[h][NS];
                    double tx = ax + td[0] * wb * 2.4F, ty = ay + td[1] * wb * 2.4F, tz = az + td[2] * wb * 2.4F;
                    pen.rgb(pale).a(0.8F * A);
                    kite(ax, ay, az, tx, ty, tz, wb * 0.7F, 0.25F);
                    pen.rgb(WHITE).a(0.9F * A);
                    kite(ax, ay, az, tx, ty, tz, wb * 0.18F, 0.3F);
                }
            }
            case ELECTRIC -> {
                // 同侧相邻两根钩之间、左右最下面那对之间来回打弧
                int q = (int) Math.floor(m.t / 0.06);
                int per = nh / 2, np = 0;
                int[] pa = pairA, pb = pairB;
                for (int si = 0; si < 2; si++) {
                    for (int i = 0; i < per - 1; i++) {
                        pa[np] = si * per + i;
                        pb[np++] = si * per + i + 1;
                    }
                }
                pa[np] = per - 1;
                pb[np++] = nh - 1;
                float swE = m.env(m.since(m.tSwing), 0.02F, 0.06F, 0.4F);
                int shown = 0, most = lite ? 1 : 3;
                for (int n = 0; n < np; n++) {
                    if (shown >= most || hash(q * 5 + n) < 0.35F - 0.3F * swE) {
                        continue;
                    }
                    if (left() < 14) {
                        return;
                    }
                    shown++;
                    int ha = pa[n], hb = pb[n];
                    int ia = Math.min(NS, 2 + (int) Math.floor(hash(q * 3 + n * 7) * (NS - 1)));
                    int ib = Math.min(NS, 2 + (int) Math.floor(hash(q * 9 + n * 3) * (NS - 1)));
                    float wb = hwb[ha];
                    zig(px[ha][ia], py[ha][ia], pz[ha][ia], px[hb][ib], py[hb][ib], pz[hb][ib], 7, wb * 0.9F, q * 11 + n);
                    pen.s(SOFT).rgb(C).a(0.45F * A).ribbon(zigRibbon(7, wb * 1.1F), EphemeraPen.FACE_CAM, 0, 1);
                    pen.rgb(hot).a(A).ribbon(zigRibbon(7, wb * 0.16F), EphemeraPen.FACE_CAM, 0, 1);
                }
            }
            case POISON -> {
                // 回钩最低处挂一串往下拉的黏滴
                for (int h = 0; h < nh && left() >= 5; h++) {
                    float wb = hwb[h];
                    int lo = 1;
                    for (int j = 2; j <= NS; j++) {
                        if (py[h][j] < py[h][lo]) {
                            lo = j;
                        }
                    }
                    int j0 = Math.max(1, lo - 1);
                    double x0 = px[h][j0], y0 = py[h][j0], z0 = pz[h][j0];
                    float sag = wb * (2.2F + 1.2F * (float) Math.sin(t * 1.4F * slow + hidx[h] * 1.9F));
                    double y1 = y0 - sag;
                    rib.clear();
                    rib.add(x0, y0, z0, wb * 0.45F * 1.6F, 1);
                    rib.add(x0, (y0 + y1) / 2, z0, wb * 0.22F * 1.6F, 1);
                    rib.add(x0, y1, z0, wb * 0.4F * 1.6F, 1);
                    pen.s(SOFT).alphaBlend().rgb(deep).a(0.5F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    rib.w[0] = wb * 0.45F;
                    rib.w[1] = wb * 0.22F;
                    rib.w[2] = wb * 0.4F;
                    pen.s(SOFT).rgb(C).a(0.8F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(DROP).rgb(pale).a(0.85F * A).billboard(x0, y1, z0, wb * 1.2F, wb * 1.5F);
                }
            }
            case IMPACT -> {
                // 每拍从背心往六个钩尖打一道直光，钩尖推出冲击圈
                if (bt <= 0.05F) {
                    return;
                }
                for (int h = 0; h < nh && left() >= 2; h++) {
                    float wb = hwb[h];
                    double x1 = px[h][NS], y1 = py[h][NS], z1 = pz[h][NS];
                    pen.s(STREAK).rgb(pale).a(0.6F * bt * A * gK).beam(spX[1], spY[1], spZ[1], x1, y1, z1, wb * 0.5F * bt);
                    float sz = wb * (2 + 6 * (1 - bt));
                    pen.s(SHOCK).rgb(pale).a(bt * 0.85F * A).billboard(x1, y1, z1, sz, sz);
                }
            }
            case MAGNETIC -> {
                if (lite) {
                    return;
                }
                // 同侧上下两根钩尖之间一道往外鼓的磁力线，光珠沿线流
                int per = nh / 2;
                for (int si = 0; si < 2; si++) {
                    if (left() < (busy ? 10 : 18)) {
                        return;
                    }
                    int sg = si == 0 ? -1 : 1, ha = si * per, hb = si * per + per - 1;
                    double ax = px[ha][NS], ay = py[ha][NS], az = pz[ha][NS], bx = px[hb][NS], by = py[hb][NS], bz = pz[hb][NS];
                    float out = sg * H * 0.32F * (1 + 0.3F * op1), bk = H * 0.08F;
                    double mx = (ax + bx) / 2 + rX * out - fX * bk, my = (ay + by) / 2, mz = (az + bz) / 2 + rZ * out - fZ * bk;
                    rib.clear();
                    for (int i = 0; i <= 8; i++) {
                        float f = i / 8.0F, g = 1 - f;
                        rib.add(g * g * ax + 2 * g * f * mx + f * f * bx, g * g * ay + 2 * g * f * my + f * f * by,
                                g * g * az + 2 * g * f * mz + f * f * bz, W * 0.2F, 1);
                    }
                    if (!busy) {
                        pen.s(SOFT).rgb(C).a(0.35F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    }
                    for (int i = 0; i < rib.n; i++) {
                        rib.w[i] = W * 0.03F;
                    }
                    pen.rgb(pale).a(0.85F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    for (int j = 0; j < 2; j++) {
                        float f = (t * 0.6F + j * 0.5F + (sg > 0 ? 0.25F : 0)) % 1;
                        int i = Math.min(7, (int) Math.floor(f * 8));
                        float ff = f * 8 - i;
                        pen.s(SOFTCORE).rgb(hot).a(0.9F * A).billboard(rib.x[i] + (rib.x[i + 1] - rib.x[i]) * ff,
                                rib.y[i] + (rib.y[i + 1] - rib.y[i]) * ff, rib.z[i] + (rib.z[i + 1] - rib.z[i]) * ff, W * 0.16F, W * 0.16F);
                    }
                }
            }
            case RADIATION -> {
                // 骨节上随机咔哒一闪的六边形
                int q = (int) Math.floor(m.t / 0.08);
                for (int h = 0; h < nh && left() >= 1; h++) {
                    int idx = hidx[h];
                    if (hash(q * 7 + idx) > 0.5F) {
                        int j = Math.min(NS, 1 + (int) Math.floor(hash(q * 3 + idx * 5) * (NS - 1)));
                        float sz = hwb[h] * 2.2F;
                        pen.s(HEX_0).rgb(hot).a(0.85F * A).billboard(px[h][j], py[h][j], pz[h][j], sz, sz, q);
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
        rib2.clear();
        for (int i = 0; i <= n; i++) {
            rib2.add(zx[i], zy[i], zz[i], w, 1);
        }
        return rib2;
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
