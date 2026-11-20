package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 星盘：身体两侧手的高度各浮一张星图圆盘，盘面刻星座和刻度，外圈一道星宫铭文带，内层轨道盘反着转，指针带一扇扫光，
 * 几颗行星沿轨道走，两枚小金属件绕盘飞；两张盘之间在身后连一道淡轨道，每隔几秒有长直光线斜穿星盘一闪而过。
 * 跑动时盘往后拖残影，挥砍时主手那张推到身前放大并射出一道光线，受击时盘面错位闪烁、往外弹开。
 */
public final class DiscStyle extends ProtoStyle<DiscStyle.St> {

    static final int CHART = EphemeraSprites.file("disc/chart");
    static final int ORBIT = EphemeraSprites.file("disc/orbit");
    static final int GIMBAL = EphemeraSprites.file("disc/gimbal");
    static final int ZODIAC = EphemeraSprites.file("disc/zodiac");

    // 星图贴图里的星，闪点要落在同一批位置上，生成式和贴图导出时一致
    private static final float[] STAR_R = new float[16], STAR_A = new float[16];

    static {
        for (int i = 0; i < 16; i++) {
            STAR_R[i] = 0.16F + 0.66F * (float) Math.sqrt(rnd01(i * 3 + 1));
            STAR_A[i] = rnd01(i * 7 + 2) * TAU;
        }
    }

    private static final float[] PLANET_R = {0.32F, 0.6F, 0.92F};
    private static final int HIST = 160;
    /** 挥砍、受击时粒子和母题粒子大约还要这么多，前面的副件按剩下的让 */
    private static final int PARTICLE_RESERVE = 90;

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        final FxKit.Spring[] sp = {new FxKit.Spring(), new FxKit.Spring()};
        FxParticles pt;
        boolean ready, lite;
        float acc;
        final float[] spin = new float[2];
        // 每张盘最近 0.3 秒的位置和盘面两根轴，环形缓冲，hHead 是最新一条
        final double[][] hT = new double[2][HIST];
        final double[][] hc = new double[2][HIST * 9];
        final int[] hHead = new int[2], hN = new int[2];
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(32);
    private final EphemeraPen.Ribbon rib2 = new EphemeraPen.Ribbon(32);
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final double[] zx = new double[10], zy = new double[10], zz = new double[10];
    private final double[] lx = new double[13], ly = new double[13], lz = new double[13];

    // 两张盘：0 = 左、1 = 右
    private final double[] dcX = new double[2], dcY = new double[2], dcZ = new double[2];
    private final float[] dR = new float[2];
    private final float[] da = new float[12];

    private double cx, cy, cz;
    private float nX, nY, nZ, e1X, e1Y, e1Z, e2X, e2Y, e2Z, R;
    // dirAt / at 的结果
    private float dX, dY, dZ;
    private double px, py, pz;

    private EphemeraStaging out;
    private boolean lite;
    private int from, baseFrom;

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
        s.pt = new FxParticles(m.lite ? 20 : 50);
        s.acc = 0;
        for (int i = 0; i < 2; i++) {
            s.sp[i].reset();
            s.spin[i] = 0;
            s.hN[i] = 0;
            s.hHead[i] = 0;
        }
    }

    private static float rnd01(int i) {
        double v = Math.sin(i * 127.1 + 31.7) * 43758.5;
        return (float) (v - Math.floor(v));
    }

    private void dirAt(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        dX = e1X * c + e2X * s;
        dY = e1Y * c + e2Y * s;
        dZ = e1Z * c + e2Z * s;
    }

    private void at(float a, float r) {
        dirAt(a);
        px = cx + dX * r;
        py = cy + dY * r;
        pz = cz + dZ * r;
    }

    private void plate(float size, float rot) {
        FxKit.plate(pen, cx, cy, cz, e1X, e1Y, e1Z, e2X, e2Y, e2Z, size, rot);
    }

    private void ring(float r, float w, int n, float a0, float a1, int uRep) {
        FxKit.ring(pen, cx, cy, cz, e1X, e1Y, e1Z, e2X, e2Y, e2Z, r, w, n, a0, a1, uRep);
    }

    /** 完整档按整个幻纹 600（留出粒子），精简档只算底座 150 */
    private boolean room(int n) {
        if (lite) {
            return out.pending(EphemeraStaging.BASE) - baseFrom + n <= EphemeraTier.CAP_REDUCED;
        }
        return out.pending() - from + n <= EphemeraTier.CAP_FULL - PARTICLE_RESERVE;
    }

    private static int accentCost(int el, boolean lite) {
        return switch (el) {
            case FIRE -> 2;
            case ICE -> 4;
            case ELECTRIC, POISON -> 13;
            case MAGNETIC -> lite ? 8 : 16;
            case RADIATION -> 6;
            default -> 3;
        };
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        lite = m.lite;
        boolean fp = d.firstPersonSelf;
        if (!s.ready || s.lite != lite) {
            init(s, m);
        }
        out = d.out;
        from = out.pending();
        baseFrom = out.pending(EphemeraStaging.BASE);
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc, act = m.act, amp = m.amp, Bk = m.burst;
        float fX = m.fwdX, fZ = m.fwdZ, rX = m.rightX, rZ = m.rightZ;
        int ek = m.el;
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        // 近白的颜色（冲击默认色）加法叠起来一片白，柔光按亮度压
        float lum = (((C >> 16) & 255) + ((C >> 8) & 255) + (C & 255)) / 765.0F;
        float gk = lum > 0.75F ? 0.45F : lum > 0.6F ? 0.7F : 1;
        float T = m.clock(), bt = m.beat(0), bl = m.blink(5), fk = m.flick(5);
        float run = clamp(m.speed / 5.6F, 0, 1), sneak = m.sneak ? 0.85F : 1;
        float swS = m.since(m.tSwing), swE = m.env(swS, 0.03F, 0.08F, 0.5F);
        float hurtE = m.env(m.since(m.tHurt), 0.02F, 0.12F, 0.5F), jumpE = m.env(m.since(m.tJump), 0.04F, 0.12F, 0.45F);
        float landE = m.env(m.since(m.tLand), 0.02F, 0.06F, 0.4F);
        float breath = 0.5F + 0.5F * (float) Math.sin(t * 1.5F);
        boolean air = !m.onGround;
        // 电的通断只压三成，整张盘一闪没了只剩暗托，看着像坏了
        float A = (0.6F + 0.4F * act) * bl * (0.7F + 0.3F * fk);
        int qh = (int) Math.floor(t / 0.05F);
        boolean heavy = ek == ELECTRIC || ek == POISON || ek == MAGNETIC;
        // 受击、挥砍那一下脚下的元素爆发很贵，电、毒、磁这时把星点、碎带、第二道磁力线让出来
        boolean busy = heavy && Math.max(hurtE, swE) > 0.15F;
        FxParticles PP = s.pt;
        PP.thin = m.emit;
        int evCat = lite ? EphemeraStaging.PARTICLE : EphemeraStaging.BASE;

        // 元素爆发先画，后面的副件按剩下的让
        pen.cat = evCat;
        fx.events(m.x, m.groundY, m.z, W * 1.6F);
        fx.bursts();
        pen.cat = EphemeraStaging.BASE;

        sites.clear();
        double vX = m.velX, vY = m.velY, vZ = m.velZ;
        for (int si = 0; si < 2; si++) {
            int sg = si == 0 ? -1 : 1;
            float swing = sg > 0 ? swE : 0;
            float swingPos = fp ? 0 : swing;
            BodyPoints.Pt wr = sg > 0 ? m.pts.wristR : m.pts.wristL;
            float R0 = 0.25F * H * sneak;
            R = R0 * (1 + 0.32F * swing * Bk + 0.05F * bt + 0.025F * breath * amp + 0.08F * jumpE + 0.06F * hurtE);

            // 停在手外侧略高处，跟着摆臂前后荡；起跳往外张、落地往下一沉
            float outD = 0.4F * W + R0 * 0.95F + 0.15F * W * jumpE + 0.15F * W * hurtE;
            float bob = 0.022F * H * (float) Math.sin(t * 1.3F + sg * 1.1F) * (0.4F + 0.6F * amp);
            float up = 0.06F * H + bob + 0.05F * H * jumpE - 0.06F * H * landE;
            double smX = wr.x + rX * sg * outD - fX * 0.04F * W, smY = wr.y + up, smZ = wr.z + rZ * sg * outD - fZ * 0.04F * W;
            float fr = 0.85F * W + R0 * 0.55F;
            double frX = m.x + rX * sg * 0.3F * W + fX * fr, frY = m.y + 0.62F * H, frZ = m.z + rZ * sg * 0.3F * W + fZ * fr;
            float lk = swingPos * 0.9F;
            double tx = smX + (frX - smX) * lk, ty = smY + (frY - smY) * lk, tz = smZ + (frZ - smZ) * lk;
            // 弹簧阻尼按相对宿主算，稳态不落后；照定稿留一半滞后，跑起来两张盘略往后拖
            float lag = 0.55F / 11;
            FxKit.Spring spr = s.sp[si];
            spr.step(tx - vX * lag, ty - vY * lag, tz - vZ * lag, m, 11, 0.55F);
            if (m.justHurt) {
                // 定稿是挨打后 0.03 秒内每帧推一下（60 帧约两帧），这里一次推两份
                spr.vx += rX * sg * 3.2F * sc;
                spr.vz += rZ * sg * 3.2F * sc;
            }
            cx = spr.x;
            cy = spr.y;
            cz = spr.z;
            if (hurtE > 0) {
                float j = 0.12F * W * hurtE;
                cx += (hash(qh * 3 + sg) - 0.5F) * j;
                cy += (hash(qh * 5 + sg) - 0.5F) * j;
                cz += (hash(qh * 7 + sg) - 0.5F) * j;
            }

            // 盘挡在镜头和身体之间时，盘面几层淡下去，只留亮边和浑仪环，人的轮廓还看得清
            double bcX = m.x, bcY = m.y + 0.6F * H, bcZ = m.z;
            double tcX = pen.camX() - bcX, tcY = pen.camY() - bcY, tcZ = pen.camZ() - bcZ;
            double tcl = Math.sqrt(tcX * tcX + tcY * tcY + tcZ * tcZ) + 1.0E-9;
            tcX /= tcl;
            tcY /= tcl;
            tcZ /= tcl;
            double dvX = cx - bcX, dvY = cy - bcY, dvZ = cz - bcZ;
            double along = dvX * tcX + dvY * tcY + dvZ * tcZ;
            double lqX = dvX - tcX * along, lqY = dvY - tcY * along, lqZ = dvZ - tcZ * along;
            float lat = (float) Math.sqrt(lqX * lqX + lqY * lqY + lqZ * lqZ);
            float occ = clamp((float) along / (0.5F * W), 0, 1) * clamp(1 - (lat - 0.35F * W) / (R * 1.1F), 0, 1);
            float fK = 1 - 0.65F * occ;

            // 盘面朝外偏前，各自慢慢晃；跑起来上沿往后倒，挥砍时转成正对前方
            float wob = (float) Math.sin(t * 0.7F + sg * 2) * 0.12F, wob2 = (float) Math.sin(t * 0.53F + sg) * 0.1F;
            float of = 0.62F - 0.35F * run + wob2;
            float oX = rX * sg * 0.78F + fX * of, oY = wob + 0.2F * jumpE, oZ = rZ * sg * 0.78F + fZ * of;
            float ol = (float) Math.sqrt(oX * oX + oY * oY + oZ * oZ);
            float sk = swing * 0.85F;
            nX = oX / ol + (fX - oX / ol) * sk;
            nY = oY / ol * (1 - sk);
            nZ = oZ / ol + (fZ - oZ / ol) * sk;
            float nl = (float) Math.sqrt(nX * nX + nY * nY + nZ * nZ);
            nX /= nl;
            nY /= nl;
            nZ /= nl;
            float ub = -0.45F * run - 0.2F * landE;
            float uX = fX * ub, uY = 1, uZ = fZ * ub;
            float ul = (float) Math.sqrt(uX * uX + uY * uY + uZ * uZ);
            uX /= ul;
            uY /= ul;
            uZ /= ul;
            float un = uX * nX + uY * nY + uZ * nZ;
            e1X = uX - nX * un;
            e1Y = uY - nY * un;
            e1Z = uZ - nZ * un;
            float el = (float) Math.sqrt(e1X * e1X + e1Y * e1Y + e1Z * e1Z);
            e1X /= el;
            e1Y /= el;
            e1Z /= el;
            e2X = nY * e1Z - nZ * e1Y;
            e2Y = nZ * e1X - nX * e1Z;
            e2Z = nX * e1Y - nY * e1X;

            s.spin[si] += dt * (0.3F + 1.5F * run + 6 * swE + 3 * jumpE + 2.5F * hurtE + 0.06F * breath) * (ek == ICE ? 0.55F : 1);
            float rot = s.spin[si] * sg + sg;
            if (ek == ELECTRIC) {
                rot = (float) Math.floor(rot / 0.06F) * 0.06F;
            }
            float glitch = hurtE > 0.05F && hash(qh * 11 + sg) < 0.45F * hurtE ? 0.3F : 1;
            float AA = A * glitch * (1 + 0.6F * swing);
            dcX[si] = cx;
            dcY[si] = cy;
            dcZ[si] = cz;
            dR[si] = R;
            int ao = si * 6;
            da[ao] = e1X;
            da[ao + 1] = e1Y;
            da[ao + 2] = e1Z;
            da[ao + 3] = e2X;
            da[ao + 4] = e2Y;
            da[ao + 5] = e2Z;

            // 残影：记下最近 0.3 秒的盘，跑起来身后拖几张
            pushHist(s, si, t);
            if (run > 0.05F || air) {
                int nG = lite ? 1 : 3;
                float k = Math.max(run, air ? 0.6F : 0);
                pen.cat = EphemeraStaging.TRAIL;
                for (int j = 1; j <= nG; j++) {
                    int h = findHist(s, si, t - j * 0.07F);
                    if (h < 0) {
                        break;
                    }
                    double[] hc = s.hc[si];
                    int o = h * 9;
                    float fa = k * AA * (1 - (float) j / (nG + 1));
                    pen.s(SOFTRING).rgb(C).a(0.4F * fa * gk);
                    FxKit.plate(pen, hc[o], hc[o + 1], hc[o + 2], (float) hc[o + 3], (float) hc[o + 4], (float) hc[o + 5],
                            (float) hc[o + 6], (float) hc[o + 7], (float) hc[o + 8], R * 2.75F, 0);
                    if (!lite && j <= 2) {
                        pen.s(CHART).rgb(C).a(0.3F * fa * fK);
                        FxKit.plate(pen, hc[o], hc[o + 1], hc[o + 2], (float) hc[o + 3], (float) hc[o + 4], (float) hc[o + 5],
                                (float) hc[o + 6], (float) hc[o + 7], (float) hc[o + 8], R * 2.0F, rot * 0.6F);
                    }
                }
                pen.cat = EphemeraStaging.BASE;
            }

            // 体积：暗托 + 正对镜头的柔光 + 盘面柔光
            if (!lite) {
                pen.s(SOFT).alphaBlend().rgb(deep).a(0.2F * fK);
                plate(R * 2.4F, 0);
            }
            pen.s(SOFT).rgb(C).a((0.09F + 0.04F * breath * amp + 0.12F * swing + 0.05F * run) * AA * gk * fK)
                    .billboard(cx, cy, cz, R * 2.7F, R * 2.7F);
            pen.s(SOFT).rgb(C).a(0.24F * AA * gk * fK);
            plate(R * 2.3F, 0);

            // 盘面：星图、反转的轨道盘
            pen.s(CHART).rgb(hot).a(0.9F * AA * fK);
            plate(R * 2.0F, rot * 0.6F);
            pen.s(ORBIT).rgb(C).a(0.8F * AA * fK);
            plate(R * 1.3F, -rot * 1.5F);

            // 两道浑仪环斜插在盘上，慢慢绕盘轴转
            for (int i = 0; i < (lite ? 1 : 2); i++) {
                float g = t * 0.35F * sg + s.spin[si] * 0.5F + i * (float) Math.PI / 2;
                dirAt(g);
                float gxX = dX, gxY = dY, gxZ = dZ;
                dirAt(g + (float) Math.PI / 2);
                float tl = (i != 0 ? -1 : 1) * (1.0F + 0.15F * (float) Math.sin(t * 0.6F + i));
                float st = (float) Math.sin(tl), ct = (float) Math.cos(tl);
                float gyX = nX * st + dX * ct, gyY = nY * st + dY * ct, gyZ = nZ * st + dZ * ct;
                pen.s(GIMBAL).rgb(i != 0 ? C : pale).a(0.6F * AA);
                FxKit.plate(pen, cx, cy, cz, gxX, gxY, gxZ, gyX, gyY, gyZ, R * 2.5F * (1 + 0.1F * swing), t * 0.4F * sg);
            }

            // 外圈：星宫铭文带 + 亮边 + 绕圈流光
            int NM = lite ? 12 : 24;
            pen.s(ZODIAC).rgb(C).a(0.9F * AA * (1 + 0.4F * swing));
            ring(R * 1.13F, R * 0.16F, NM, -rot * 0.5F, -rot * 0.5F + TAU, NM / 4);
            int NS = lite ? 12 : 24;
            rib.clear();
            FxKit.circle(rib, cx, cy, cz, e1X, e1Y, e1Z, e2X, e2Y, e2Z, R * 1.24F, NS, 0, TAU, R * 0.026F, 1);
            pen.rgb(hot).a(0.85F * AA).ribbon(rib, EphemeraPen.FACE_NORMAL, nX, nY, nZ, 0, 1);
            if (lite) {
                // 精简档的外圈柔光带换成一片柔光环
                pen.s(SOFTRING).rgb(C).a(0.26F * AA * gk);
                plate(R * 2.75F, 0);
            } else {
                rib.clear();
                FxKit.circle(rib, cx, cy, cz, e1X, e1Y, e1Z, e2X, e2Y, e2Z, R * 1.2F, 14, 0, TAU, R * (0.36F + 0.12F * swing), 1);
                pen.s(SOFT).rgb(C).a(0.26F * AA * gk).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            }
            float fa = -rot * 0.5F + t * (ek == ICE ? 0.5F : 1.3F) * sg;
            pen.s(ZODIAC).rgb(hot).a(0.75F * AA);
            ring(R * 1.13F, R * 0.2F, lite ? 3 : 6, fa, fa + 1.1F * sg, 6);
            if (!lite) {
                at(fa + 1.1F * sg, R * 1.13F);
                pen.s(SOFTCORE).rgb(hot).a(0.6F * AA * gk).billboard(px, py, pz, R * 0.42F, R * 0.42F);
            }

            // 四枚菱形凸钉：远处也认得出是一张带方位的盘
            for (int i = 0; i < 4; i++) {
                dirAt(rot * 0.6F + i * TAU / 4);
                float drX = dX, drY = dY, drZ = dZ;
                float sdX = drY * nZ - drZ * nY, sdY = drZ * nX - drX * nZ, sdZ = drX * nY - drY * nX;
                float sl = (float) Math.sqrt(sdX * sdX + sdY * sdY + sdZ * sdZ) + 1.0E-9F;
                sdX /= sl;
                sdY /= sl;
                sdZ /= sl;
                float len = R * (0.2F + 0.3F * swing * Bk + 0.1F * bt + 0.03F * breath * amp + 0.08F * jumpE);
                double bX = cx + drX * R * 1.25F, bY = cy + drY * R * 1.25F, bZ = cz + drZ * R * 1.25F;
                double tpX = bX + drX * len, tpY = bY + drY * len, tpZ = bZ + drZ * len;
                double mX = bX + drX * len * 0.3F, mY = bY + drY * len * 0.3F, mZ = bZ + drZ * len * 0.3F;
                float w0 = R * 0.08F, w1 = R * 0.025F, bk = R * 0.04F;
                pen.rgb(C).a(0.85F * AA).quad(tpX, tpY, tpZ, mX + sdX * w0, mY + sdY * w0, mZ + sdZ * w0,
                        bX - drX * bk, bY - drY * bk, bZ - drZ * bk, mX - sdX * w0, mY - sdY * w0, mZ - sdZ * w0);
                if (!lite) {
                    pen.rgb(WHITE).a(0.7F * AA).quad(tpX, tpY, tpZ, mX + sdX * w1, mY + sdY * w1, mZ + sdZ * w1,
                            bX, bY, bZ, mX - sdX * w1, mY - sdY * w1, mZ - sdZ * w1);
                }
            }

            // 指针 + 扫光 + 盘心
            float pa = T * 0.6F * sg + 0.6F + s.spin[si] * 0.8F * sg;
            for (int k = 0; k < (lite ? 1 : 3); k++) {
                at(pa - sg * k * 0.16F, R * 0.95F);
                double q1X = px, q1Y = py, q1Z = pz;
                at(pa - sg * (k + 1) * 0.16F, R * 0.95F);
                pen.rgb(C).a(0.22F * AA * (1 - k / 3.0F) * gk * fK).va(1, 1, 0.5F, 0.5F)
                        .quad(cx, cy, cz, cx, cy, cz, q1X, q1Y, q1Z, px, py, pz);
            }
            rib.clear();
            at(pa + (float) Math.PI, R * 0.25F);
            rib.add(px, py, pz, R * 0.05F, 1);
            rib.add(cx, cy, cz, R * 0.065F, 1);
            at(pa, R * 0.97F);
            rib.add(px, py, pz, R * 0.014F, 1);
            pen.rgb(hot).a(0.95F * AA).ribbon(rib, EphemeraPen.FACE_NORMAL, nX, nY, nZ, 0, 1);
            if (!lite) {
                pen.s(FLARE).rgb(hot).a(0.8F * AA).billboard(px, py, pz, R * 0.2F, R * 0.2F, 0.785F);
            }
            pen.s(SOFTCORE).rgb(hot).a(0.75F * AA * gk).billboard(cx, cy, cz, R * 0.42F, R * 0.42F);

            // 行星：三颗沿轨道盘走，最外那颗大一点带一圈小光环
            for (int i = 0; i < 3; i++) {
                float rr = R * 1.3F / 2 * PLANET_R[i];
                at(-rot * 1.5F + t * (0.9F - i * 0.25F) * sg * (ek == ICE ? 0.5F : 1) + i * 2.1F, rr);
                float sz = R * (0.12F + 0.05F * i);
                if (!lite) {
                    pen.s(SOFT).rgb(C).a(0.4F * AA * gk).billboard(px, py, pz, sz * 2, sz * 2);
                }
                pen.s(SOFTCORE).rgb(hot).a(0.95F * AA).billboard(px, py, pz, sz, sz);
                if (i == 2 && !lite) {
                    pen.s(SOFTRING).rgb(pale).a(0.7F * AA);
                    FxKit.plate(pen, px, py, pz, e1X, e1Y, e1Z, e2X, e2Y, e2Z, sz * 2.4F, 0);
                }
            }

            // 星点：贴图里的亮星轮流闪一下
            if (!lite && !busy) {
                pen.cat = EphemeraStaging.PARTICLE;
                for (int i = 0; i < 4; i++) {
                    float ph = t * 1.8F + i * 0.41F + si * 0.2F;
                    int q = (int) Math.floor(ph);
                    int st = Math.min(15, (int) Math.floor(hash(q * 13 + i * 7 + si) * 16));
                    float tw = (float) Math.sin((ph - Math.floor(ph)) * Math.PI);
                    at(STAR_A[st] + rot * 0.6F, STAR_R[st] * R);
                    pen.s(FLARE).rgb(hot).a(0.85F * tw * AA).billboard(px, py, pz, R * 0.3F * tw, R * 0.3F * tw, q);
                }
                pen.cat = EphemeraStaging.BASE;
            }

            // 小金属件：两枚暗色菱片绕着盘外飞，带一道亮棱
            for (int i = 0; i < (lite ? 1 : 2); i++) {
                dirAt(t * (0.55F + 0.2F * i) * -sg + i * (float) Math.PI + 3 * hurtE);
                float drX = dX, drY = dY, drZ = dZ;
                float rr = R * (1.55F + 0.1F * (float) Math.sin(t * 1.1F + i) + 0.4F * hurtE + 0.25F * swing);
                float nn = R * 0.18F * (float) Math.sin(t * 0.9F + i * 2);
                double mX = cx + drX * rr + nX * nn, mY = cy + drY * rr + nY * nn, mZ = cz + drZ * rr + nZ * nn;
                float tX = nY * drZ - nZ * drY, tY = nZ * drX - nX * drZ, tZ = nX * drY - nY * drX;
                float tl = (float) Math.sqrt(tX * tX + tY * tY + tZ * tZ) + 1.0E-9F;
                tX /= tl;
                tY /= tl;
                tZ /= tl;
                float L = R * 0.2F, w = R * 0.08F, w4 = w * 0.25F;
                double q0X = mX + tX * L, q0Y = mY + tY * L, q0Z = mZ + tZ * L;
                double q2X = mX - tX * L * 0.6F, q2Y = mY - tY * L * 0.6F, q2Z = mZ - tZ * L * 0.6F;
                pen.alphaBlend().rgb(mul(deep, 0.6F)).a(0.85F * Math.min(1, AA)).quad(q0X, q0Y, q0Z, mX + drX * w, mY + drY * w, mZ + drZ * w,
                        q2X, q2Y, q2Z, mX - drX * w, mY - drY * w, mZ - drZ * w);
                pen.rgb(hot).a(0.8F * AA).quad(q0X, q0Y, q0Z, mX + drX * w4, mY + drY * w4, mZ + drZ * w4,
                        q2X, q2Y, q2Z, mX - drX * w4, mY - drY * w4, mZ - drZ * w4);
                sites.add(mX, mY, mZ, drX, drY, drZ);
            }

            // 挥砍：这一侧的盘外扩一圈、冲击圈推出去；落地两张盘各荡一圈；受击星宫带碎成几截错位乱跳
            pen.cat = evCat;
            if (swing > 0) {
                float f = clamp(swS / (m.burstTime * 0.6F), 0, 1);
                if (f < 1) {
                    pen.s(SOFTRING).rgb(hot).a(0.85F * (1 - f) * AA * gk);
                    plate(R * 2.75F * (1 + 0.8F * f * Bk), 0);
                    pen.s(SHOCK).rgb(C).a(0.8F * (1 - f));
                    plate(R * 2.5F * (1 + 1.2F * f * Bk), 0);
                }
            }
            if (landE > 0.02F) {
                float f = clamp(m.since(m.tLand) / 0.4F, 0, 1);
                pen.s(SOFTRING).rgb(C).a(0.6F * (1 - f) * gk);
                plate(R * 2.6F * (1 + 0.6F * f), 0);
            }
            if (hurtE > 0.05F) {
                int nf = lite || busy ? 2 : 5;
                for (int i = 0; i < nf && room(2); i++) {
                    float a0 = hash(qh * 17 + i + si * 40) * TAU, rr = R * (0.85F + 0.55F * hash(qh * 5 + i * 3 + si));
                    float off = (hash(qh + i * 9) - 0.5F) * R * 0.25F;
                    pen.s(ZODIAC).rgb((i & 1) != 0 ? WHITE : hot).a(0.8F * hurtE);
                    FxKit.ring(pen, cx + e1X * off, cy + e1Y * off, cz + e1Z * off, e1X, e1Y, e1Z, e2X, e2Y, e2Z,
                            rr, R * 0.15F, 2, a0, a0 + 0.6F, 2);
                }
            }
            pen.cat = EphemeraStaging.BASE;

            // 元素：盘缘往外长
            int NE = lite ? switch (ek) {
                case FIRE, IMPACT -> 4;
                case ICE -> 3;
                case MAGNETIC, RADIATION -> 2;
                case ELECTRIC -> 1;
                default -> 0;
            } : heavy ? 3 : 8;
            int ac = accentCost(ek, lite);
            for (int i = 0; i < NE; i++) {
                if (i > 0 && !room(ac)) {
                    break;
                }
                dirAt(rot * 0.4F + (float) i / NE * TAU);
                // 火往上窜：朝上那半圈长一截
                float upK = ek == FIRE ? 0.7F + 0.9F * Math.max(0, dY) : 1;
                fx.accent(cx + dX * R * 1.27F, cy + dY * R * 1.27F, cz + dZ * R * 1.27F, dX, dY, dZ,
                        R * (heavy ? 0.46F : 0.36F) * (1 + 0.7F * swing + 0.12F * breath * amp) * upK, R * (heavy ? 0.2F : 0.17F),
                        i + si * 20, Math.min(1, 1.15F * AA));
            }
            // 水平面里和盘面垂直的横向（up × n）
            float hX = nZ, hZ = -nX, hl = (float) Math.sqrt(hX * hX + hZ * hZ);
            if (hl < 1.0E-4F) {
                hX = rX;
                hZ = rZ;
            } else {
                hX /= hl;
                hZ /= hl;
            }
            if (ek == FIRE) {
                // 盘心烧起来：一圈滚动的火絮，盘顶再顶起三根大火舌
                pen.s(FLOW_0).rgb(C).a(0.45F * AA);
                ring(R * 0.55F, R * 0.5F, lite ? 6 : 12, -T * 0.8F * sg, -T * 0.8F * sg + TAU, 3);
                if (!lite) {
                    for (int i = 0; i < 3 && room(2); i++) {
                        float o = (i - 1) * R * 0.35F;
                        fx.accent(cx + hX * o, cy + R * 1.2F, cz + hZ * o, 0, 1, 0,
                                R * (0.6F + 0.15F * (float) Math.sin(t * 4 + i * 2) + 0.4F * swing), R * 0.24F, 40 + i + si * 5, 0.75F * AA);
                    }
                }
            } else if (ek == ICE) {
                // 冰：一圈硬边冰晶冠 + 盘心六瓣霜花
                int NI = lite ? 6 : 14;
                for (int i = 0; i < NI; i++) {
                    dirAt((float) i / NI * TAU + rot * 0.3F);
                    float drX = dX, drY = dY, drZ = dZ;
                    float sdX = drY * nZ - drZ * nY, sdY = drZ * nX - drX * nZ, sdZ = drX * nY - drY * nX;
                    float sl = (float) Math.sqrt(sdX * sdX + sdY * sdY + sdZ * sdZ) + 1.0E-9F;
                    sdX /= sl;
                    sdY /= sl;
                    sdZ /= sl;
                    float L = R * (0.14F + 0.32F * rnd01(i * 3 + 1 + si * 50)) * (1 + 0.35F * swing);
                    double bX = cx + drX * R * 1.24F, bY = cy + drY * R * 1.24F, bZ = cz + drZ * R * 1.24F;
                    double tpX = bX + drX * L, tpY = bY + drY * L, tpZ = bZ + drZ * L;
                    double mX = bX + drX * L * 0.35F, mY = bY + drY * L * 0.35F, mZ = bZ + drZ * L * 0.35F;
                    float w0 = R * 0.055F, w1 = R * 0.014F;
                    pen.rgb(pale).a(0.75F * AA).va(1, 0.6F, 0.3F, 0.6F).quad(tpX, tpY, tpZ, mX + sdX * w0, mY + sdY * w0, mZ + sdZ * w0,
                            bX, bY, bZ, mX - sdX * w0, mY - sdY * w0, mZ - sdZ * w0);
                    if (!lite) {
                        pen.rgb(WHITE).a(0.85F * AA).quad(tpX, tpY, tpZ, mX + sdX * w1, mY + sdY * w1, mZ + sdZ * w1,
                                bX, bY, bZ, mX - sdX * w1, mY - sdY * w1, mZ - sdZ * w1);
                    }
                }
                if (!lite) {
                    for (int i = 0; i < 6; i++) {
                        dirAt((float) i / 6 * TAU - T * 0.1F);
                        float drX = dX, drY = dY, drZ = dZ;
                        float sdX = drY * nZ - drZ * nY, sdY = drZ * nX - drX * nZ, sdZ = drX * nY - drY * nX;
                        float sl = (float) Math.sqrt(sdX * sdX + sdY * sdY + sdZ * sdZ) + 1.0E-9F;
                        sdX /= sl;
                        sdY /= sl;
                        sdZ /= sl;
                        pen.rgb(WHITE).a(0.7F * AA).beam(cx + drX * R * 0.1F, cy + drY * R * 0.1F, cz + drZ * R * 0.1F,
                                cx + drX * R * 0.48F, cy + drY * R * 0.48F, cz + drZ * R * 0.48F, R * 0.035F);
                        pen.rgb(pale).a(0.7F * AA).beam(cx + drX * R * 0.28F, cy + drY * R * 0.28F, cz + drZ * R * 0.28F,
                                cx + drX * R * 0.4F + sdX * R * 0.1F, cy + drY * R * 0.4F + sdY * R * 0.1F, cz + drZ * R * 0.4F + sdZ * R * 0.1F,
                                R * 0.022F);
                    }
                }
                pen.s(SOFT).rgb(pale).a(0.12F * AA);
                plate(R * 2.1F, 0);
            } else if (ek == ELECTRIC) {
                // 电：几道电弧沿盘缘两点之间横跳；精简档不画外面那层柔光
                int q = (int) Math.floor(t / 0.06F);
                for (int i = 0; i < (lite || busy ? 1 : 2); i++) {
                    if (hash(q * 3 + i * 11 + si * 7) < 0.25F || !room(lite ? 8 : 15)) {
                        continue;
                    }
                    float a0 = hash(q * 7 + i + si * 3) * TAU, a1 = a0 + 1.2F + hash(q * 9 + i + si) * 2.2F;
                    at(a0, R * 1.2F);
                    double p0X = px, p0Y = py, p0Z = pz;
                    at(a1, R * 1.2F);
                    double ddX = px - p0X, ddY = py - p0Y, ddZ = pz - p0Z;
                    double sX = ddY * nZ - ddZ * nY, sY = ddZ * nX - ddX * nZ, sZ = ddX * nY - ddY * nX;
                    double sl = Math.sqrt(sX * sX + sY * sY + sZ * sZ) + 1.0E-9;
                    zig(p0X, p0Y, p0Z, px, py, pz, 7, R * 0.14F, q * 13 + i + si * 31, sX / sl, sY / sl, sZ / sl);
                    if (!lite) {
                        zigRibbon(rib, 7, R * 0.24F);
                        pen.s(SOFT).rgb(C).a(0.55F * AA).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    }
                    zigRibbon(rib, 7, R * 0.05F);
                    pen.rgb(hot).a(AA).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(FLARE).rgb(hot).a(0.8F * AA).billboard(px, py, pz, R * 0.38F, R * 0.38F, q);
                }
            } else if (ek == POISON) {
                // 毒：盘心慢慢翻的毒絮，下沿挂几滴
                pen.s(FLOW_0).rgb(mul(C, 0.8F)).a(0.4F * AA);
                ring(R * 0.55F, R * 0.5F, lite ? 6 : 12, T * 0.3F * sg, T * 0.3F * sg + TAU, 3);
                for (int i = 0; i < (lite ? 1 : 3); i++) {
                    if (i > 0 && !room(14)) {
                        break;
                    }
                    float o = (i - 1) * R * 0.4F;
                    double qX = cx + hX * o, qY = cy - R * 1.18F, qZ = cz + hZ * o;
                    float L = R * (0.75F + 0.15F * i);
                    fx.accent(qX, qY, qZ, 0, -1, 0, L, R * 0.24F, 60 + i + si * 5, Math.min(1, 1.15F * AA));
                    pen.s(SOFT).rgb(C).a(0.35F * AA).billboard(qX, qY - L * 0.6F, qZ, R * 0.45F, R * 0.6F);
                }
            } else if (ek == IMPACT) {
                // 冲击：每一拍从盘缘往外推一道冲击圈
                float f = 1 - bt;
                pen.s(SHOCK).rgb(pale).a(0.7F * bt * A);
                plate(R * 2.6F * (1 + 0.6F * f), 0);
                pen.s(SHOCK).rgb(WHITE).a(0.4F * bt * A);
                plate(R * 2.45F * (1 + 0.25F * f), 0);
            } else if (ek == RADIATION) {
                // 辐射：盘心三扇慢转的扇形光 + 六边形
                for (int i = 0; i < 3; i++) {
                    float a = i / 3.0F * TAU + T * 0.4F * sg;
                    pen.s(SOFT).rgb(C).a(0.6F * AA * bl);
                    ring(R * 0.42F, R * 0.36F, lite ? 2 : 3, a, a + 0.75F, 0);
                }
                pen.s(HEX_0).rgb(hot).a(0.85F * AA * bl);
                plate(R * 0.85F, T * 0.3F);
            }

            sites.add(cx, cy, cz, nX, nY, nZ);
            at(pa, R * 0.6F);
            sites.add(px, py, pz, nX, nY, nZ);
        }

        // 两张盘之间：身后一道轨道（磁力换成两道磁力线），光珠沿着走
        {
            boolean mag = ek == MAGNETIC;
            int NL = mag ? (lite || busy ? 1 : 2) : 1, NP = lite ? 8 : 12;
            for (int j = 0; j < NL; j++) {
                float depth = W * (mag ? 0.9F + 0.35F * j : 0.75F) + 0.2F * W * run;
                float lift = H * (mag ? (j != 0 ? 0.32F : -0.06F) : 0.05F) + (mag ? 0.04F * H * (float) Math.sin(t * 1.2F + j) : 0);
                for (int i = 0; i <= NP; i++) {
                    float f = (float) i / NP, sn = (float) Math.sin(f * Math.PI);
                    lx[i] = dcX[0] + (dcX[1] - dcX[0]) * f - fX * depth * sn;
                    ly[i] = dcY[0] + (dcY[1] - dcY[0]) * f + lift * sn;
                    lz[i] = dcZ[0] + (dcZ[1] - dcZ[0]) * f - fZ * depth * sn;
                }
                float aa = mag ? A : 0.35F * A * (0.7F + 0.3F * act + 0.4F * swE);
                rib.clear();
                for (int i = 0; i <= NP; i++) {
                    rib.add(lx[i], ly[i], lz[i], H * (mag ? 0.09F : 0.04F), 1);
                }
                pen.cat = lite ? EphemeraStaging.TRAIL : EphemeraStaging.BASE;
                pen.s(SOFT).rgb(C).a((mag ? 0.35F : 0.25F) * aa * gk).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                pen.cat = EphemeraStaging.BASE;
                rib.clear();
                for (int i = 0; i <= NP; i++) {
                    rib.add(lx[i], ly[i], lz[i], H * (mag ? 0.016F : 0.006F), mag || (i & 1) != 0 ? 1 : 0.25F);
                }
                pen.rgb(mag ? pale : hot).a(aa).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                for (int k = 0; k < (lite ? 1 : 3); k++) {
                    float f = t * (mag ? 0.45F : 0.3F) * (j != 0 ? -1 : 1) + k / 3.0F + j * 0.17F;
                    f -= (float) Math.floor(f);
                    int i = Math.min(NP - 1, (int) Math.floor(f * NP));
                    float g = f * NP - i;
                    pen.s(SOFTCORE).rgb(hot).a(0.9F * aa).billboard(lx[i] + (lx[i + 1] - lx[i]) * g, ly[i] + (ly[i + 1] - ly[i]) * g,
                            lz[i] + (lz[i + 1] - lz[i]) * g, H * 0.06F, H * 0.06F);
                }
            }
        }

        // 长直光线：每 1.6 秒一格掷一次，斜穿某一张盘；挥砍时从挥砍那张盘往前射一道
        // 第一人称看自己时不画：一道一个半身高的光斜穿视野
        if (!fp) {
            pen.cat = EphemeraStaging.TRAIL;
            final float SL = 1.6F;
            int k0 = (int) Math.floor(t / SL);
            float chance = 0.4F + 0.35F * run + 0.2F * act;
            for (int k = k0 - 1; k <= k0; k++) {
                for (int j = 0; j < 2; j++) {
                    if (hash(k * 37 + j * 101 + 7) > (j != 0 ? chance * 0.45F : chance)) {
                        continue;
                    }
                    float t0 = k * SL + hash(k * 13 + j) * (SL - 0.5F), age = t - t0;
                    if (age < 0 || age > 0.55F) {
                        continue;
                    }
                    int di = hash(k * 5 + j * 3) < 0.5F ? 0 : 1, sg = di != 0 ? 1 : -1;
                    float ang = (0.3F + 0.5F * hash(k * 7 + j)) * (hash(k * 9 + j) < 0.5F ? -1 : 1);
                    float side = sg * (0.15F + 0.2F * hash(k * 17 + j));
                    float bdX = fX * (float) Math.cos(ang) + rX * side, bdY = (float) Math.sin(ang), bdZ = fZ * (float) Math.cos(ang) + rZ * side;
                    float bl2 = (float) Math.sqrt(bdX * bdX + bdY * bdY + bdZ * bdZ);
                    bdX /= bl2;
                    bdY /= bl2;
                    bdZ /= bl2;
                    float off = (hash(k * 19 + j) - 0.5F) * 0.4F * H;
                    drawBeam(s, m, dcX[di] + bdX * off, dcY[di] + bdY * off, dcZ[di] + bdZ * off, bdX, bdY, bdZ, age, 0.55F, 1.5F * H, 1,
                            k * 7 + j, di, C, hot, pale, gk, bl, act);
                }
            }
            if (swS < 0.45F) {
                float bdX = fX - rX * 0.25F, bdY = 0.35F, bdZ = fZ - rZ * 0.25F;
                float bl2 = (float) Math.sqrt(bdX * bdX + bdY * bdY + bdZ * bdZ);
                bdX /= bl2;
                bdY /= bl2;
                bdZ /= bl2;
                float off = 0.55F * H;
                drawBeam(s, m, dcX[1] + bdX * off, dcY[1] + bdY * off, dcZ[1] + bdZ * off, bdX, bdY, bdZ, swS, 0.45F, 1.7F * H, 1.5F,
                        999 + (int) Math.floor(m.tSwing * 13), 1, C, hot, pale, gk, bl, act);
            }
            pen.cat = EphemeraStaging.BASE;
        }

        // 星尘：站着零星几颗，跑、挥砍、受击时盘边一路往后撒
        s.acc += dt * (3 + 14 * run + 35 * swE + 25 * hurtE + 8 * jumpE) * (lite ? 0.4F : 1) * (heavy ? 0.6F : 1);
        while (s.acc >= 1) {
            s.acc -= 1;
            int di = rnd() < 0.5F ? 0 : 1;
            float a = rnd() * TAU, rr = dR[di] * (0.9F + 0.5F * rnd());
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            int ao = di * 6;
            double qX = dcX[di], qY = dcY[di], qZ = dcZ[di];
            float drX = da[ao] * c + da[ao + 3] * sn, drY = da[ao + 1] * c + da[ao + 4] * sn, drZ = da[ao + 2] * c + da[ao + 5] * sn;
            PP.spec().pos(qX + drX * rr, qY + drY * rr, qZ + drZ * rr)
                    .vel(drX * 0.25F * sc - fX * 1.3F * run * sc, drY * 0.25F * sc + 0.08F * sc, drZ * 0.25F * sc - fZ * 1.3F * run * sc)
                    .life(rnd(0.7F, 1.3F)).size(rnd(0.03F, 0.06F) * sc, 0.005F * sc).sprite(rnd() < 0.3F ? FLARE : SOFTCORE)
                    .rgb(rnd() < 0.5F ? hot : C).a(0.85F).drag(1.2F).rot(0.785F).spawn();
        }
        PP.step(dt, null);
        pen.cat = EphemeraStaging.PARTICLE;
        PP.draw(pen, m.size, 1, Float.NaN);
        pen.cat = EphemeraStaging.BASE;
        fx.motes(sites, 10 * (0.5F + 0.5F * act), sc);
    }

    private void pushHist(St s, int si, float t) {
        double[] hT = s.hT[si], hc = s.hc[si];
        int head = (s.hHead[si] + 1) % HIST;
        s.hHead[si] = head;
        s.hN[si] = Math.min(HIST, s.hN[si] + 1);
        hT[head] = t;
        int o = head * 9;
        hc[o] = cx;
        hc[o + 1] = cy;
        hc[o + 2] = cz;
        hc[o + 3] = e1X;
        hc[o + 4] = e1Y;
        hc[o + 5] = e1Z;
        hc[o + 6] = e2X;
        hc[o + 7] = e2Y;
        hc[o + 8] = e2Z;
        while (s.hN[si] > 2 && t - hT[(head - s.hN[si] + 1 + HIST) % HIST] > 0.3F) {
            s.hN[si]--;
        }
    }

    /** 最新的一条「时间不晚于 tq」的记录，没有返回 -1 */
    private static int findHist(St s, int si, float tq) {
        double[] hT = s.hT[si];
        for (int k = 0; k < s.hN[si]; k++) {
            int idx = (s.hHead[si] - k + HIST) % HIST;
            if (hT[idx] <= tq) {
                return idx;
            }
        }
        return -1;
    }

    private void drawBeam(St s, FxMotion m, double mx, double my, double mz, float bdX, float bdY, float bdZ, float age, float life,
                          float L, float bw, int seed, int di, int C, int hot, int pale, float gk, float bl, float act) {
        float H = m.H, t = (float) m.t, sc = m.sc;
        int ek = m.el;
        float f = age / life;
        // 光线从一头刷到另一头：头部先走，尾巴随后淡掉
        float hd = smooth(f * 2.2F), tl = smooth(f * 1.6F - 0.5F);
        float s0 = -L / 2 + L * tl, s1 = -L / 2 + L * hd;
        double aX = mx + bdX * s0, aY = my + bdY * s0, aZ = mz + bdZ * s0;
        double bX = mx + bdX * s1, bY = my + bdY * s1, bZ = mz + bdZ * s1;
        float fa = (1 - f * f) * (0.75F + 0.25F * act) * (lite ? 0.8F : 1);
        if (ek == ELECTRIC) {
            int q = (int) Math.floor(t / 0.05F);
            double sX = bdY * pen.fwdZ - bdZ * pen.fwdY, sY = bdZ * pen.fwdX - bdX * pen.fwdZ, sZ = bdX * pen.fwdY - bdY * pen.fwdX;
            double sl = Math.sqrt(sX * sX + sY * sY + sZ * sZ);
            if (sl < 1.0E-6) {
                sX = 0;
                sY = 1;
                sZ = 0;
                sl = 1;
            }
            int n = lite ? 5 : 9;
            zig(aX, aY, aZ, bX, bY, bZ, n, 0.035F * H, q * 7 + seed, sX / sl, sY / sl, sZ / sl);
            zigRibbon(rib2, n, 0.08F * H * bw);
            pen.s(SOFT).rgb(C).a(0.55F * fa).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
            zigRibbon(rib2, n, 0.014F * H * bw);
            pen.rgb(hot).a(fa).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
        } else if (ek == RADIATION) {
            // 辐射：虚线，一截一截地亮
            int ns = lite ? 4 : 7;
            int qq = (int) Math.floor(t / 0.08F);
            for (int i = 0; i < ns; i++) {
                if (hash(qq * 3 + i + seed) < 0.2F) {
                    continue;
                }
                float g0 = (float) i / ns, g1 = (i + 0.6F) / ns;
                double x0 = aX + (bX - aX) * g0, y0 = aY + (bY - aY) * g0, z0 = aZ + (bZ - aZ) * g0;
                double x1 = aX + (bX - aX) * g1, y1 = aY + (bY - aY) * g1, z1 = aZ + (bZ - aZ) * g1;
                pen.s(STREAK).rgb(C).a(0.8F * fa * bl).beam(x0, y0, z0, x1, y1, z1, 0.06F * H * bw);
                pen.rgb(hot).a(fa * bl).beam(x0, y0, z0, x1, y1, z1, 0.012F * H * bw);
            }
        } else {
            pen.s(STREAK).rgb(C).a(0.35F * fa * gk).beam(aX, aY, aZ, bX, bY, bZ, 0.16F * H * bw);
            pen.s(STREAK).rgb(C).a(0.8F * fa).beam(aX, aY, aZ, bX, bY, bZ, 0.06F * H * bw);
            pen.rgb(hot).a(fa).beam(aX, aY, aZ, bX, bY, bZ, 0.012F * H * bw);
        }
        if (hd < 1) {
            pen.s(FLARE).rgb(hot).a(fa).billboard(bX, bY, bZ, 0.14F * H * bw, 0.14F * H * bw, 0.785F);
        }
        // 光线上的几个星节点：头扫过时亮一下
        if (!lite) {
            for (int i = 0; i < 3; i++) {
                float g = 0.2F + 0.3F * i + 0.1F * hash(seed + i), dd = hd - g;
                if (dd < 0 || g < tl) {
                    continue;
                }
                float tw = (float) Math.exp(-dd * 6);
                float sg = -L / 2 + L * g;
                double qX = mx + bdX * sg, qY = my + bdY * sg, qZ = mz + bdZ * sg;
                pen.s(FLARE).rgb(hot).a(0.9F * tw * fa).billboard(qX, qY, qZ, 0.09F * H * tw, 0.09F * H * tw, 0);
                if (ek == IMPACT) {
                    pen.s(SHOCK).rgb(pale).a(0.6F * tw * fa);
                    FxKit.plate(pen, qX, qY, qZ, m.rightX, 0, m.rightZ, 0, 1, 0, 0.2F * H * (1.5F - tw), 0);
                } else if (ek == ICE) {
                    pen.rgb(pale).a(0.8F * fa).quad(qX, qY + 0.06F * H, qZ, qX + bdX * 0.025F * H, qY + bdY * 0.025F * H, qZ + bdZ * 0.025F * H,
                            qX, qY - 0.03F * H, qZ, qX - bdX * 0.025F * H, qY - bdY * 0.025F * H, qZ - bdZ * 0.025F * H);
                }
            }
        }
        // 光线沿途掉东西：火掉余烬，毒滴下去，磁吸回盘
        if ((ek == FIRE || ek == POISON || ek == MAGNETIC) && hd < 1 && rnd() < m.dt * (lite ? 10 : 30)) {
            FxParticles PP = s.pt;
            if (ek == FIRE) {
                PP.spec().pos(bX, bY, bZ).vel((rnd() - 0.5F) * 0.3F * sc, 0.8F * sc, (rnd() - 0.5F) * 0.3F * sc).life(0.6F)
                        .size(0.05F * sc, 0.01F * sc).sprite(SOFTCORE).rgb(hot).a(0.9F).grav(-0.5F * sc).stretch(0.08F).spawn();
            } else if (ek == POISON) {
                PP.spec().pos(bX, bY, bZ).vel(0, -0.2F * sc, 0).life(0.8F).size(0.05F * sc, 0.03F * sc).sprite(SOFTCORE).rgb(C).a(0.9F)
                        .grav(4 * sc).stretch(0.05F).spawn();
            } else {
                double gX = dcX[di] - bX, gY = dcY[di] - bY, gZ = dcZ[di] - bZ, gl = Math.sqrt(gX * gX + gY * gY + gZ * gZ) + 1.0E-9;
                float v = 1.2F * sc;
                PP.spec().pos(bX, bY, bZ).vel((float) (gX / gl * v), (float) (gY / gl * v), (float) (gZ / gl * v)).life(0.5F)
                        .size(0.035F * sc, 0.01F * sc).sprite(DOT).rgb(hot).a(0.9F).drag(1).spawn();
            }
        }
    }

    /** a 到 b 的折线（n 段），中间各点沿 (sx, sy, sz) 随机偏 ±amp，写进 zx / zy / zz */
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

    private void zigRibbon(EphemeraPen.Ribbon r, int n, float w) {
        r.clear();
        for (int i = 0; i <= n; i++) {
            r.add(zx[i], zy[i], zz[i], w, 1);
        }
    }
}
