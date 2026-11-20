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
 * 碎旋：上百片暗色与主色相间的碎片聚成一条带子，从脚下绕身螺旋上升，到头顶散成一圈光冠，按节拍一段一段往上涌；
 * 带心一道光流，周身浮着细光尘。跑起来往身后拖，起跳收紧，落地底部铺开，挥砍整条甩开，受击碎片四散再聚拢。
 */
public final class HelixStyle extends ProtoStyle<HelixStyle.St> {

    static final int CHIP0 = EphemeraSprites.file("helix/chip0");
    static final int CHIP1 = EphemeraSprites.file("helix/chip1");

    private static final float TURNS = 1.75F;
    // 每拍整条带往上涌多少（占全程）、一拍多长（秒，按元素时钟）
    private static final float ADV = 0.34F, PER = 0.95F;
    private static final int NSEG = 4;
    // 碎片参数跑的全程：-0.04 起头，0.92 以上是头顶散开的冠
    private static final float FR = 1.22F;
    private static final int MAXQ = 150, MAXB = 21;
    // 第一人称：胸口以上的带子和碎片贴着眼睛转，不画
    private static final float FP_TOP = 0.62F;

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        FxParticles pt, dust;
        boolean ready, lite;
        double ph;
        float tp, kick, air, dacc, vsx, vsy, vsz;
        int seenSwing, seenHurt, seenLand;
    }

    private final double[] qx = new double[MAXQ], qy = new double[MAXQ], qz = new double[MAXQ];
    private final float[] qf = new float[MAXQ], qcr = new float[MAXQ], qfade = new float[MAXQ], qrx = new float[MAXQ], qrz = new float[MAXQ],
            qseg = new float[MAXQ], qh7 = new float[MAXQ], qh9 = new float[MAXQ], qfacing = new float[MAXQ];
    private final int[] qsd = new int[MAXQ], qi = new int[MAXQ];
    private final boolean[] qdark = new boolean[MAXQ];
    private int nq;

    private final double[] bx = new double[MAXB], by = new double[MAXB], bz = new double[MAXB];
    private final float[] bf = new float[MAXB], bsg = new float[MAXB], be = new float[MAXB];

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(12);
    private final EphemeraPen.Ribbon rib2 = new EphemeraPen.Ribbon(12);
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final double[] side = new double[3];
    private final double[] zx = new double[9], zy = new double[9], zz = new double[9];
    private final double[] lx = new double[11], ly = new double[11], lz = new double[11];
    private final float[] dir = new float[3];

    // 这一帧
    private EphemeraStaging out;
    private int cap, from;
    private float H, W, Bk, amp, bt, swE, landE, breath, rot, tight;
    private float fwdX, fwdZ, rightX, rightZ, swDx, swDy, swDz, vsx, vsy, vsz;
    private double mx, my, mz, off;
    // core() / trailAt() 的输出
    private double ox, oy, oz;
    private float rdX, rdZ, trX, trY, trZ;

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

    private void init(St s, FxMotion m, float T) {
        s.ready = true;
        s.lite = m.lite;
        s.pt = new FxParticles(m.lite ? 24 : 60);
        s.dust = new FxParticles(m.lite ? 8 : 40);
        s.dacc = 0;
        s.ph = 0;
        s.tp = T;
        s.kick = 0;
        s.air = 0;
        s.vsx = s.vsy = s.vsz = 0;
        s.seenSwing = m.nSwing;
        s.seenHurt = m.nHurt;
        s.seenLand = m.nLand;
    }

    /** 完整档按整个幻纹（扣掉留给粒子的）算，精简档只算底座 */
    private int left() {
        return cap - (out.pending() - from);
    }

    private boolean room(int n, int keep) {
        return left() - keep >= n;
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

    private static int groundCost(int el, boolean lite) {
        return switch (el) {
            case FIRE -> 2;
            case ICE -> 19;
            case ELECTRIC -> 21;
            case IMPACT -> 3;
            case MAGNETIC -> lite ? 21 : 51;
            default -> 5;
        };
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        boolean lite = m.lite, fp = d.firstPersonSelf;
        float T = m.clock();
        if (!s.ready || s.lite != lite) {
            init(s, m, T);
        }
        out = d.out;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        int el = m.el;
        float dt = m.dt, t = (float) m.t, sc = m.sc;
        H = m.H;
        W = m.W;
        float act = m.act;
        amp = m.amp;
        Bk = m.burst;
        bt = m.beat(0);
        float bl = m.blink(11);
        float run = clamp(m.speed / 5.6F, 0, 1);
        swE = m.env(m.since(m.tSwing), 0.03F, 0.08F, 0.5F);
        float hurtE = m.env(m.since(m.tHurt), 0.02F, 0.12F, 0.7F);
        landE = m.env(m.since(m.tLand), 0.01F, 0.06F, 0.45F);
        breath = 0.5F + 0.5F * (float) Math.sin(t * 1.6F);
        fwdX = m.fwdX;
        fwdZ = m.fwdZ;
        rightX = m.rightX;
        rightZ = m.rightZ;
        mx = m.x;
        my = m.y;
        mz = m.z;

        // ---- 节拍：每拍开头猛涌一下再放缓，整条带一段一段往上走 ----
        float dT = clamp(T - s.tp, 0, 0.1F);
        s.tp = T;
        s.air += ((m.onGround ? 0 : 1) - s.air) * Math.min(1, dt * 8);
        s.ph += dT / PER * (0.8F + 0.3F * act + 0.5F * run + 0.5F * s.air) * (1 + 1.5F * swE);
        double phi = Math.floor(s.ph);
        float ph = (float) (s.ph - phi);
        off = (phi + ph + 0.8 * Math.sin(TAU * ph) / TAU) * ADV;
        float surge = (float) Math.exp(-ph * 4);
        // 涌到头顶要晚半拍左右
        float crownE = (float) Math.exp(-((ph + 0.55F) % 1) * 5);

        // 跟随：速度抹平后拿来往后拖，越往上拖得越远
        float kv = 1 - (float) Math.exp(-dt * 8);
        s.vsx += (m.velX - s.vsx) * kv;
        s.vsy += (m.velY - s.vsy) * kv;
        s.vsz += (m.velZ - s.vsz) * kv;
        vsx = s.vsx;
        vsy = s.vsy;
        vsz = s.vsz;
        int sg = (m.swingCount & 1) != 0 ? 1 : -1;
        {
            float x = fwdX + rightX * sg * 0.6F, y = 0.15F, z = fwdZ + rightZ * sg * 0.6F;
            float l = (float) Math.sqrt(x * x + y * y + z * z);
            swDx = x / l;
            swDy = y / l;
            swDz = z / l;
        }
        s.kick += dt * swE * 7 * sg + dt * hurtE * 2;
        rot = T * 0.6F + s.kick;
        tight = 1 - 0.28F * s.air;

        float A = (0.62F + 0.38F * act + 0.06F * breath) * bl;
        if (hurtE > 0.05F) {
            A *= 0.7F + 0.3F * hash((int) Math.floor(m.t * 22));
        }

        // 爆发先画：电、磁挥砍那一下一次就上百个面片，后面的副件按剩下的让
        pen.cat = EphemeraStaging.PARTICLE;
        fx.events(m.x, m.groundY, m.z, W * 1.6F);
        fx.bursts();
        boolean newEv = s.seenSwing != m.nSwing || s.seenHurt != m.nHurt || s.seenLand != m.nLand;
        if (lite) {
            cap = EphemeraTier.CAP_REDUCED;
            from = out.pending();
        } else {
            // 粒子最后画、没法半截停，按池里现有的数先留出来
            cap = EphemeraTier.CAP_FULL - s.pt.count() - s.dust.count() - fx.particles() - (el == POISON ? 20 : 0) - (newEv ? 20 : 0) - 12;
            from = 0;
        }
        boolean heavy = el == ELECTRIC || el == POISON || el == MAGNETIC;
        int aCost = accentCost(el, lite);
        int NCA = lite ? 2 : heavy ? 3 : 6;
        int NA = lite ? 2 : 5, NL = lite ? 2 : 3, NP = lite ? 7 : 10;
        boolean groundOn = el != MAGNETIC && !(lite && (el == ICE || el == ELECTRIC));
        int gKeep = 3 + (groundOn ? groundCost(el, lite) : 0);
        int tailKeep = gKeep + (fp ? 0 : 3 + NCA * aCost);
        int sigCost = el == ELECTRIC ? NA * 11 : el == MAGNETIC && !fp ? NL * ((lite ? 1 : 2) * NP + 2) : el == IMPACT ? 2 : 0;

        // ---- 碎片：固定的一串槽位沿带子往上走，到顶散开、脚下重新补上，所以带子永远是满的 ----
        int N = lite ? 40 : MAXQ;
        nq = 0;
        for (int i = 0; i < N; i++) {
            double uu = (double) i / N + off;
            double cyc = Math.floor(uu);
            float f = (float) (uu - cyc) * FR - 0.04F;
            int sd = i * 131 + (int) cyc * 977;
            float crr = f > 0.92F ? Math.min(1, (f - 0.92F) / 0.26F) : 0;
            float fade = smooth(clamp((f + 0.04F) / 0.12F, 0, 1)) * (1 - (float) Math.pow(crr, 2.2));
            if (fade < 0.02F || (fp && f > FP_TOP)) {
                continue;
            }
            core(f, (hash(sd * 7 + 1) - 0.5F) * 0.5F, (hash(sd * 7 + 2) - 0.5F) * 0.2F, (hash(sd * 7 + 3) - 0.5F) * 0.3F);
            double px = ox, py = oy, pz = oz;
            if (hurtE > 0.01F) {
                float z = hash(sd * 7 + 5) * 2 - 1, a = hash(sd * 7 + 6) * TAU, r = (float) Math.sqrt(Math.max(0, 1 - z * z));
                float k = hurtE * H * (0.2F + 0.35F * hash(sd * 7 + 4));
                px += r * Math.cos(a) * k;
                py += (z * 0.6F + 0.2F) * k;
                pz += r * Math.sin(a) * k;
            }
            int q = nq++;
            qx[q] = px;
            qy[q] = py;
            qz[q] = pz;
            qf[q] = f;
            qcr[q] = crr;
            qfade[q] = fade;
            qrx[q] = rdX;
            qrz[q] = rdZ;
            qsd[q] = sd;
            qi[q] = i;
            qseg[q] = 0.5F + 0.5F * (float) Math.cos(TAU * NSEG * i / N);
            qh7[q] = hash(sd * 7 + 7);
            qh9[q] = hash(sd * 7 + 9);
            qdark[q] = hash(sd * 7 + 8) < 0.38F;
            qfacing[q] = -(rdX * pen.fwdX + rdZ * pen.fwdZ);
        }

        // ---- 体积：暗托 + 正对镜头的柔光带 + 带心光流 ----
        int NS = lite ? 8 : 20;
        for (int j = 0; j <= NS; j++) {
            float f = (float) j / NS;
            core(f, 0, 0, 0);
            bx[j] = ox;
            by[j] = oy;
            bz[j] = oz;
            bf[j] = f;
            bsg[j] = segAt(f);
            be[j] = smooth(clamp(f / 0.1F, 0, 1)) * (1 - smooth(clamp((f - 0.8F) / 0.2F, 0, 1)));
        }
        float BW = H * 0.32F * (1 + 0.5F * swE + 0.3F * landE);
        // 浅色（冲击默认近白）叠起来容易糊成一片，按亮度压一点；受击时带子打散，柔光跟着散掉
        float lum = (0.3F * ((C >> 16) & 255) + 0.59F * ((C >> 8) & 255) + 0.11F * (C & 255)) / 255.0F;
        float hk = Math.min(1, 0.62F / Math.max(0.3F, lum)) * (1 - 0.65F * hurtE);
        pen.cat = EphemeraStaging.BASE;
        // 柔光用一串正对镜头的光团，不用丝带：螺旋转到正对镜头那段时丝带会拧成尖刺；
        // 带子顺着视线那段光团会叠在一起，按切线和视线的夹角压一压
        for (int j = 1; j < NS; j++) {
            if (fp && bf[j] > FP_TOP) {
                continue;
            }
            double tx = bx[j + 1] - bx[j - 1], ty = by[j + 1] - by[j - 1], tz = bz[j + 1] - bz[j - 1];
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1.0E-9;
            tx /= tl;
            ty /= tl;
            tz /= tl;
            double cx = ty * pen.fwdZ - tz * pen.fwdY, cy = tz * pen.fwdX - tx * pen.fwdZ, cz = tx * pen.fwdY - ty * pen.fwdX;
            float k = (0.3F + 0.7F * (float) Math.sqrt(cx * cx + cy * cy + cz * cz)) * be[j] * hk;
            if (!lite && (j & 1) != 0) {
                pen.s(SOFT).alphaBlend().rgb(deep).a(0.28F * k).billboard(bx[j], by[j], bz[j], BW * 1.6F, BW * 1.6F);
            }
            if (!lite) {
                pen.s(SOFT).rgb(C).a((0.22F + 0.14F * bsg[j] + 0.1F * surge) * k * A).billboard(bx[j], by[j], bz[j], BW * 1.9F, BW * 1.9F);
            }
            pen.s(SOFT).rgb(C).a((0.42F + 0.45F * bsg[j]) * k * A).billboard(bx[j], by[j], bz[j], BW * 0.8F, BW * 0.8F);
        }
        // 带心光流：一条贴着螺旋柱面的窄带（宽度方向沿身体竖轴），转到侧面自然变细
        float fu = ((-T * 0.9F) % 0.5F + 0.5F) % 0.5F, CW = H * 0.08F * (1 + 0.6F * swE);
        float flowA = 1.3F * A * (1 - 0.7F * hurtE);
        flowSide(0, NS, CW);
        double spx = side[0], spy = side[1], spz = side[2];
        for (int j = 0; j < NS; j++) {
            flowSide(j + 1, NS, CW);
            double snx = side[0], sny = side[1], snz = side[2];
            if (!(fp && bf[j + 1] > FP_TOP)) {
                float ka = (0.15F + 0.85F * bsg[j] * bsg[j]) * be[j], kb = (0.15F + 0.85F * bsg[j + 1] * bsg[j + 1]) * be[j + 1];
                pen.s(FLOW_0).rgb(hot).a(flowA).va(ka, kb, kb, ka).uv4(fu, 0, fu + 0.5F, 0, fu + 0.5F, 1, fu, 1);
                pen.quad(bx[j] + spx, by[j] + spy, bz[j] + spz, bx[j + 1] + snx, by[j + 1] + sny, bz[j + 1] + snz,
                        bx[j + 1] - snx, by[j + 1] - sny, bz[j + 1] - snz, bx[j] - spx, by[j] - spy, bz[j] - spz);
            }
            spx = snx;
            spy = sny;
            spz = snz;
        }
        if (!fp) {
            pen.s(SOFT).rgb(C).a((0.035F + 0.03F * surge + 0.08F * swE) * A).billboard(mx, my + H * 0.55F, mz, H * 1.3F, H * 1.6F);
        }

        // ---- 碎片本体 ----
        int darkRgb = el == FIRE ? 0x170B08 : el == ICE ? 0x0A121C : el == POISON ? 0x0A1408 : mix(0x0F1113, deep, 0.4F);
        int qb = (int) Math.floor(m.t / 0.08);
        int every = switch (el) {
            case FIRE -> 4;
            case ICE -> 13;
            case ELECTRIC -> 17;
            case IMPACT -> 7;
            case RADIATION -> 8;
            default -> 0;
        };
        int chipKeep = tailKeep + sigCost;
        sites.clear();
        for (int q = 0; q < nq; q++) {
            double px = qx[q], py = qy[q], pz = qz[q];
            int sd = qsd[q], i = qi[q];
            float seg = qseg[q], cr = qcr[q], h7 = qh7[q], h9 = qh9[q];
            // 转到身后的暗一点、小一点
            float dk = 0.72F + 0.28F * qfacing[q];
            float sz = H * (0.034F + 0.04F * h7) * (0.72F + 0.48F * seg) * (1 + 0.5F * cr) * (0.9F + 0.1F * dk);
            float a = qfade[q] * A * dk * (0.6F + 0.4F * seg);
            float tum = 0.3F + 0.7F * Math.abs((float) Math.cos(T * (2 + 4 * h9) + sd));
            float r0 = sd * 1.3F + T * (h9 - 0.5F) * 5;
            int spr = (sd & 1) != 0 ? CHIP1 : CHIP0;
            if (qdark[q]) {
                pen.s(spr).alphaBlend().rgb(darkRgb).a(0.85F * qfade[q] * (0.65F + 0.35F * act))
                        .billboard(px, py, pz, sz * 1.3F * (el == ICE ? 1 : tum), sz * 1.3F, el == ICE ? sd : r0);
                continue;
            }
            int col = sd % 9 == 0 && cr < 0.2F ? WHITE : sd % 3 == 0 ? hot : C;
            if (!lite && i % 3 == 0 && room(1, chipKeep)) {
                pen.s(SOFT).rgb(C).a(0.26F * a).billboard(px, py, pz, sz * 3.8F, sz * 3.8F);
            }
            switch (el) {
                case ICE -> {
                    // 冰：碎片是棱晶，硬边、不翻滚，几乎不转
                    float rx = qrx[q], rz = qrz[q], kk = (h9 - 0.5F) * 1.4F;
                    double dx = rz * kk, dy = 1, dz = -rx * kk, dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= dl;
                    dy /= dl;
                    dz /= dl;
                    float L = sz * (2 + 1.4F * h7);
                    double ax = px - dx * L * 0.5, ay = py - dy * L * 0.5, az = pz - dz * L * 0.5;
                    double tx = px + dx * L * 0.5, ty = py + dy * L * 0.5, tz = pz + dz * L * 0.5;
                    pen.s(EphemeraSprites.WHITE).rgb(pale).a(0.85F * a).va(1, 0.6F, 0.35F, 0.6F);
                    kite(ax, ay, az, tx, ty, tz, sz * 0.95F, 0.35F);
                    if (!lite && room(2, chipKeep)) {
                        pen.s(EphemeraSprites.WHITE).rgb(WHITE).a(0.9F * a);
                        kite(ax, ay, az, tx, ty, tz, sz * 0.22F, 0.4F);
                    }
                    if (hash((int) Math.floor(m.t * 1.5) * 17 + sd) > 0.9F && room(1, chipKeep)) {
                        pen.s(FLARE).rgb(WHITE).a(0.8F * a).billboard(tx, ty, tz, sz * 2.2F, sz * 2.2F, 0);
                    }
                }
                case POISON -> {
                    // 毒：亮的碎片是黏滴，少数往下拉出一道涎丝
                    pen.s(DROP).rgb(sd % 3 != 0 ? C : pale).a(a).billboard(px, py, pz, sz, sz * 1.35F);
                    if (h9 < (lite ? 0.08F : 0.14F) && room(2, chipKeep)) {
                        float g = (T * 0.5F + h7) % 1, L = H * (0.03F + 0.11F * g);
                        pen.s(SOFT).rgb(C).a(0.7F * a).beam(px, py, pz, px, py - L, pz, sz * 0.45F * (1 - 0.5F * g));
                        pen.s(DROP).rgb(pale).a(a).billboard(px, py - L, pz, sz * 0.7F, sz * 0.9F);
                    }
                }
                case IMPACT -> {
                    // 冲击：方块碎片，打拍那一下整体一鼓
                    float k = 1 + 0.55F * bt;
                    pen.s(EphemeraSprites.WHITE).rgb(col == C ? pale : col).a(a * (0.75F + 0.25F * bt))
                            .billboard(px, py, pz, sz * 0.85F * k, sz * 0.85F * k, r0);
                }
                case MAGNETIC -> {
                    // 磁：碎片被磁化成亮珠
                    pen.s(spr).rgb(col).a(0.8F * a).billboard(px, py, pz, sz * 1.1F, sz * 1.1F, r0);
                    if ((i & 1) != 0) {
                        pen.s(SOFTCORE).rgb(hot).a(0.9F * a).billboard(px, py, pz, sz * 0.8F, sz * 0.8F);
                    }
                }
                case RADIATION -> {
                    // 辐射：每片各自咔哒明灭，偶尔爆一下星芒
                    float hb = hash(qb * 31 + sd);
                    float k = hb < 0.15F ? 0.2F : hb > 0.9F ? 1.6F : 1;
                    pen.s(spr).rgb(col).a(Math.min(1, a * k)).billboard(px, py, pz, sz * 1.15F * tum, sz * 1.15F, r0);
                    if (hb > 0.9F) {
                        pen.s(FLARE).rgb(hot).a(0.85F * a).billboard(px, py, pz, sz * 3.4F, sz * 3.4F, 0.785F);
                    } else if (hb > 0.86F) {
                        pen.s(HEX_0).rgb(hot).a(0.8F * a).billboard(px, py, pz, sz * 2.6F, sz * 2.6F, sd);
                    }
                }
                default -> {
                    // 火、电：多边形碎片翻滚；电的每片各自按 50 ms 通断
                    float on = el == ELECTRIC ? m.flick(sd) : 1;
                    pen.s(spr).rgb(el == FIRE && sd % 3 != 0 ? mix(C, hot, 0.4F) : col).a(a * on)
                            .billboard(px, py, pz, sz * 1.15F * tum, sz * 1.15F, r0);
                }
            }
            // 元素凸起：火每两三片一道火舌，其余几种隔得开一些
            if (every > 0 && i % every == 0 && cr < 0.5F && room(aCost, chipKeep)) {
                float rx = qrx[q], rz = qrz[q];
                if (el == FIRE) {
                    fx.accent(px, py, pz, 0, 1, 0, sz * (1.8F + 1.2F * seg), sz, sd, 0.85F * a);
                } else {
                    float l = (float) Math.sqrt(rx * rx + 0.09F + rz * rz);
                    fx.accent(px, py, pz, rx / l, 0.3F / l, rz / l, sz * 3, sz * 0.8F, sd, 0.85F * a);
                }
            }
            if (i % 7 == 0 && sites.n < 16) {
                float rx = qrx[q], rz = qrz[q], l = (float) Math.sqrt(rx * rx + 0.16F + rz * rz);
                sites.add(px, py, pz, rx / l, 0.4F / l, rz / l);
            }
        }

        // ---- 元素招牌 ----
        signature(m, el, lite, fp, t, C, hot, pale, A, NA, NL, NP, tailKeep);

        // ---- 头顶的冠：涌上来的碎片在这里散开，拍子到顶时亮一下 ----
        if (!fp) {
            trailAt(1.05F);
            BodyPoints.Pt ht = m.pts.headTop;
            double ccx = ht.x + trX, ccy = ht.y + H * 0.15F + trY, ccz = ht.z + trZ;
            float Rc = W * (0.9F + 0.3F * crownE + 0.6F * swE * Bk + 0.4F * hurtE);
            pen.s(SOFTRING).rgb(C).a((0.22F + 0.3F * crownE) * A).flat(ccx, ccy, ccz, Rc * 2.6F, Rc * 2.6F, rot);
            pen.s(SOFT).rgb(C).a((0.12F + 0.2F * crownE) * A).billboard(ccx, ccy, ccz, Rc * 2.6F, Rc * 1.5F);
            if (el == RADIATION) {
                pen.s(HEX_1).rgb(hot).a(0.55F * A * bl).flat(ccx, ccy, ccz, Rc * 1.6F, Rc * 1.6F, T * 0.3F);
            }
            for (int j = 0; j < NCA; j++) {
                if (!room(aCost, gKeep)) {
                    break;
                }
                float ang = rot * 0.7F + (j + 0.5F) * TAU / NCA;
                float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                float dx, dy, dz;
                if (el == FIRE) {
                    dx = 0;
                    dy = 1;
                    dz = 0;
                } else if (el == POISON) {
                    dx = 0;
                    dy = -1;
                    dz = 0;
                } else {
                    float l = (float) Math.sqrt(1.25F);
                    dx = cs / l;
                    dy = 0.5F / l;
                    dz = sn / l;
                }
                fx.accent(ccx + cs * Rc * 0.95F, ccy, ccz + sn * Rc * 0.95F, dx, dy, dz, H * (0.1F + 0.05F * crownE + 0.06F * swE), H * 0.035F,
                        200 + j, 0.8F * A);
            }
            sites.add(ccx, ccy, ccz, 0, 1, 0);
        }

        // ---- 脚下：带子从这里起头 ----
        double gy = m.groundY + 0.03;
        float g1 = W * 2.8F * (1 + 0.5F * landE), g2 = W * 2.2F * (1 + 0.6F * landE);
        pen.s(SOFT).rgb(C).a((0.1F + 0.15F * landE) * A).flat(mx, gy, mz, g1, g1);
        pen.s(SOFTRING).rgb(C).a((0.22F + 0.1F * surge + 0.3F * landE) * A).flat(mx, gy, mz, g2, g2, -rot);
        if (groundOn && room(groundCost(el, lite), 1)) {
            fx.ground(mx, m.groundY, mz, W * 0.85F, 0.45F * A);
        }

        // 挥砍：腰间一圈甩出去的光环
        float ss = m.since(m.tSwing) / m.burstTime;
        if (ss < 0.5F) {
            float f = ss / 0.5F, rr = W * 2.4F * (1 + 1.4F * f * Bk);
            pen.s(SOFTRING).rgb(hot).a(0.65F * (1 - f) * A).flat(mx, my + H * 0.55F, mz, rr, rr, rot);
        }

        // ---- 事件：挥砍沿方向甩出一串碎片，受击四散，落地往外溅 ----
        pen.cat = EphemeraStaging.PARTICLE;
        FxParticles P = s.pt;
        P.thin = Math.min(1, m.emit);
        if (s.seenSwing != m.nSwing) {
            s.seenSwing = m.nSwing;
            if (m.since(m.tSwing) <= 0.2F) {
                spray(P, 0, lite ? 8 : 20, darkRgb, C, hot, sc);
            }
        }
        if (s.seenHurt != m.nHurt) {
            s.seenHurt = m.nHurt;
            if (m.since(m.tHurt) <= 0.2F) {
                spray(P, 1, lite ? 8 : 12, darkRgb, C, hot, sc);
            }
        }
        if (s.seenLand != m.nLand) {
            s.seenLand = m.nLand;
            if (m.since(m.tLand) <= 0.2F) {
                spray(P, 2, lite ? 8 : 12, darkRgb, C, hot, sc);
            }
        }
        P.step(dt, null);
        P.draw(pen, m.size, 1, Float.NaN);

        // 氛围：绕身慢慢旋升的细光尘，不跟人走，跑起来自然落在身后
        FxParticles D = s.dust;
        D.thin = Math.min(1, m.emit);
        s.dacc += dt * (lite ? 5 : 14) * (0.6F + 0.6F * act + 0.8F * run);
        while (s.dacc >= 1) {
            s.dacc -= 1;
            float a = rnd() * TAU, rr = W * rnd(0.6F, 1.7F);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            D.spec().pos(mx + cs * rr, my + H * rnd(0, 1.1F), mz + sn * rr).vel(-sn * 0.35F * sc, rnd(0.2F, 0.5F) * sc, cs * 0.35F * sc)
                    .life(rnd(1.3F, 2.3F)).size(rnd(0.018F, 0.032F) * sc, 0.006F * sc).sprite(SOFTCORE).rgb(rnd() < 0.4F ? hot : C)
                    .a(0.75F).drag(0.4F).spawn();
        }
        D.step(dt, null);
        D.draw(pen, m.size, 1, Float.NaN);

        fx.motes(sites, 16 * (0.5F + 0.5F * act), sc);
        pen.cat = EphemeraStaging.BASE;
    }

    /** kind 0 挥砍、1 受击、2 落地：从带子上挑几片甩出去 */
    private void spray(FxParticles P, int kind, int max, int darkRgb, int C, int hot, float sc) {
        int n = 0;
        for (int q = 0; q < nq && n < max; q++) {
            int i = qi[q];
            float f = qf[q];
            if (kind == 0 && (f < 0.15F || f > 0.85F || i % 3 != 0)) {
                continue;
            }
            if (kind == 2 && (f > 0.3F || i % 2 != 0)) {
                continue;
            }
            if (kind == 1 && i % 5 != 0) {
                continue;
            }
            n++;
            float rx = qrx[q], rz = qrz[q];
            float vx, vy, vz;
            if (kind == 0) {
                float a = rnd(2.5F, 5), b = rnd(1, 2.5F);
                vx = swDx * a + rx * b;
                vy = swDy * a;
                vz = swDz * a + rz * b;
            } else if (kind == 2) {
                float a = rnd(2, 3.5F);
                vx = rx * a;
                vy = rnd(0.5F, 1.5F);
                vz = rz * a;
            } else {
                EphemeraFx.randDir(dir);
                float x = rx + dir[0] * 0.6F, y = dir[1] * 0.6F, z = rz + dir[2] * 0.6F;
                float l = (float) Math.sqrt(x * x + y * y + z * z) + 1.0E-6F, v = rnd(2, 4) / l;
                vx = x * v;
                vy = y * v;
                vz = z * v;
            }
            boolean dark = qdark[q];
            FxParticles.Spec sp = P.spec().pos(qx[q], qy[q], qz[q]).vel(vx * sc, vy * sc, vz * sc).life(rnd(0.35F, 0.65F))
                    .size(H * 0.05F, H * 0.02F).sprite(dark ? CHIP0 : CHIP1).rgb(dark ? darkRgb : (qsd[q] & 1) != 0 ? C : hot)
                    .a(0.9F).rot(rnd(0, 6)).spin(rnd(-14, 14)).drag(2.4F);
            if (dark) {
                sp.alphaBlend();
            }
            sp.spawn();
        }
    }

    private void signature(FxMotion m, int el, boolean lite, boolean fp, float t, int C, int hot, int pale, float A, int NA, int NL, int NP,
                           int keep) {
        switch (el) {
            case ELECTRIC -> {
                // 电：相邻碎片之间跳弧，每 50 ms 换一批
                int q5 = (int) Math.floor(m.t / 0.05);
                for (int k = 0; k < NA && nq > 4; k++) {
                    if (hash(q5 * 3 + k * 11) < 0.2F) {
                        continue;
                    }
                    int i0 = (int) (hash(q5 * 7 + k) * (nq - 4)), i1 = i0 + 1 + (int) (hash(q5 * 5 + k * 3) * 3);
                    if (qdark[i0] && qdark[i1]) {
                        continue;
                    }
                    double dx = qx[i1] - qx[i0], dy = qy[i1] - qy[i0], dz = qz[i1] - qz[i0];
                    float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > 0.75F * H || dist < 0.05F) {
                        continue;
                    }
                    if (!room(11, keep)) {
                        break;
                    }
                    zig(qx[i0], qy[i0], qz[i0], qx[i1], qy[i1], qz[i1], 5, dist * 0.22F, q5 * 13 + k);
                    float ka = Math.min(qfade[i0], qfade[i1]) * A;
                    pen.s(SOFT).rgb(C).a(0.5F * ka).ribbon(zigRibbon(5, H * 0.05F), EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(EphemeraSprites.WHITE).rgb(hot).a(ka).ribbon(zigRibbon(5, H * 0.009F), EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(FLARE).rgb(hot).a(0.8F * ka).billboard(qx[i1], qy[i1], qz[i1], H * 0.07F, H * 0.07F, q5);
                }
            }
            case MAGNETIC -> {
                // 磁：几道从脚下到头顶的磁力线把螺旋兜住，光珠沿线往上流；第一人称会从眼前穿过，不画
                if (fp) {
                    return;
                }
                double bx0 = mx, by0 = my + H * 0.04F, bz0 = mz;
                trailAt(1);
                BodyPoints.Pt ht = m.pts.headTop;
                double tx0 = ht.x + trX, ty0 = ht.y + H * 0.12F + trY, tz0 = ht.z + trZ;
                int cost = (lite ? 1 : 2) * NP + 2;
                for (int j = 0; j < NL; j++) {
                    if (!room(cost, keep)) {
                        break;
                    }
                    float ang = -rot * 0.5F + j * TAU / NL, cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                    float rx = rightX * cs + fwdX * sn, rz = rightZ * cs + fwdZ * sn;
                    float bulge = W * (1.4F + 0.12F * (float) Math.sin(t * 1.2F + j * 2) + 0.6F * swE);
                    for (int k = 0; k <= NP; k++) {
                        float tt = (float) k / NP;
                        trailAt(tt);
                        double ex = tx0 + trX * (tt - 1), ey = ty0 + trY * (tt - 1), ez = tz0 + trZ * (tt - 1);
                        float e = (1 - (float) Math.cos(Math.PI * tt)) / 2, b = (float) Math.sin(Math.PI * tt) * bulge;
                        lx[k] = bx0 + (ex - bx0) * e + rx * b;
                        ly[k] = by0 + (ey - by0) * e;
                        lz[k] = bz0 + (ez - bz0) * e + rz * b;
                    }
                    if (!lite) {
                        rib.clear();
                        for (int k = 0; k <= NP; k++) {
                            rib.add(lx[k], ly[k], lz[k], H * 0.07F, 0.25F + 0.75F * (float) Math.sin(Math.PI * k / NP));
                        }
                        pen.s(SOFT).rgb(C).a(0.3F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    }
                    rib.clear();
                    for (int k = 0; k <= NP; k++) {
                        rib.add(lx[k], ly[k], lz[k], H * 0.011F, 0.25F + 0.75F * (float) Math.sin(Math.PI * k / NP));
                    }
                    pen.s(EphemeraSprites.WHITE).rgb(pale).a(0.85F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    for (int b = 0; b < 2; b++) {
                        float f = (t * 0.45F + b / 2.0F + j * 0.21F) % 1;
                        int i = Math.min(NP - 1, (int) Math.floor(f * NP));
                        float ff = f * NP - i;
                        pen.s(SOFTCORE).rgb(hot).a(0.95F * A).billboard(lx[i] + (lx[i + 1] - lx[i]) * ff, ly[i] + (ly[i + 1] - ly[i]) * ff,
                                lz[i] + (lz[i + 1] - lz[i]) * ff, H * 0.06F, H * 0.06F);
                    }
                }
            }
            case IMPACT -> {
                // 冲击：打拍那一下，从带子上推出两道水平冲击圈
                if (bt <= 0.03F) {
                    return;
                }
                for (int k = 0; k < 2 && room(1, keep); k++) {
                    float hy = k == 0 ? 0.3F : 0.78F, kk = k == 0 ? 1 : 0.8F;
                    float rr = W * (2.2F + 2 * (1 - bt)) * kk;
                    trailAt(hy);
                    pen.s(SHOCK).rgb(pale).a(0.75F * bt * A).flat(mx + trX, my + H * (0.03F + 1.18F * hy) + trY, mz + trZ, rr, rr);
                }
            }
            default -> {
            }
        }
    }

    /** 速度往后拖的偏移，f 越往上拖得越远，写进 trX/trY/trZ */
    private void trailAt(float f) {
        float k = 0.17F * (float) Math.pow(Math.max(0, f), 1.3);
        float vx = -vsx * k, vy = -vsy * k * 0.35F, vz = -vsz * k;
        float l = (float) Math.sqrt(vx * vx + vy * vy + vz * vz), cap = 0.7F * H;
        if (l > cap) {
            float s = cap / l;
            vx *= s;
            vy *= s;
            vz *= s;
        }
        trX = vx;
        trY = vy;
        trZ = vz;
    }

    /** 带子中线上 f 处的点（0 脚下，1 头顶），jr / jy / ja 是每片碎片自己的偏移；写进 ox/oy/oz、rdX/rdZ */
    private void core(float f, float jr, float jy, float ja) {
        float ff = Math.max(0, f), cr = f > 0.92F ? (f - 0.92F) / 0.26F : 0, fm = Math.min(1, ff);
        float R = W * (1.02F - 0.34F * Math.min(ff, 0.92F) + jr * (0.6F + ff)) * tight;
        R *= 1 + 0.6F * landE * (1 - fm) * (1 - fm) + 0.75F * swE * Bk * (0.35F + 0.65F * (float) Math.sin(Math.PI * fm))
                + 0.05F * breath * amp + 0.16F * bt;
        R += W * 1.5F * (float) Math.pow(cr, 1.2);
        float y = H * (0.03F + 1.18F * Math.min(f, 0.92F) + 0.4F * Math.max(0, f - 0.92F) + jy * (1 + cr) + 0.06F * (float) Math.sin(Math.PI * cr))
                * (1 - 0.16F * landE * (1 - fm));
        float ang = rot + TURNS * TAU * f + ja;
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
        rdX = rightX * c + fwdX * s;
        rdZ = rightZ * c + fwdZ * s;
        trailAt(f);
        ox = mx + rdX * R + trX;
        oy = my + y + trY;
        oz = mz + rdZ * R + trZ;
        if (swE > 0.01F) {
            float k = swE * Bk * W * 0.45F * (float) Math.sin(Math.PI * fm);
            ox += swDx * k;
            oy += swDy * k;
            oz += swDz * k;
        }
    }

    private float segAt(float f) {
        double x = (f + 0.04F) / FR - off;
        return 0.5F + 0.5F * (float) Math.cos(TAU * NSEG * (x - Math.floor(x)));
    }

    /** 光流第 j 点的半宽向量：身体竖轴去掉切线分量，写进 side */
    private void flowSide(int j, int NS, float cw) {
        int a = Math.max(0, j - 1), b = Math.min(NS, j + 1);
        double tx = bx[b] - bx[a], ty = by[b] - by[a], tz = bz[b] - bz[a];
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1.0E-9;
        tx /= tl;
        ty /= tl;
        tz /= tl;
        double vx = -tx * ty, vy = 1 - ty * ty, vz = -tz * ty;
        double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-6) {
            side[0] = side[1] = side[2] = 0;
            return;
        }
        double k = cw / 2 / vl;
        side[0] = vx * k;
        side[1] = vy * k;
        side[2] = vz * k;
    }

    /** 菱形片：a 到 tip，最宽处在 mid（0~1）；笔刷先设好 */
    private void kite(double ax, double ay, double az, double tx, double ty, double tz, float w, float mid) {
        double dx = tx - ax, dy = ty - ay, dz = tz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) {
            l = 1;
        }
        sideOf(ax, ay, az, dx / l, dy / l, dz / l);
        double cx = ax + dx * mid, cy = ay + dy * mid, cz = az + dz * mid;
        double sx = side[0] * w / 2, sy = side[1] * w / 2, sz = side[2] * w / 2;
        pen.quad(tx, ty, tz, cx + sx, cy + sy, cz + sz, ax, ay, az, cx - sx, cy - sy, cz - sz);
    }

    /** a 到 b 的折线（n 段），中间各点沿镜头侧向随机偏 ±amp，写进 zx/zy/zz */
    private void zig(double ax, double ay, double az, double bx0, double by0, double bz0, int n, float amp, int seed) {
        double dx = bx0 - ax, dy = by0 - ay, dz = bz0 - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-9;
        sideOf((ax + bx0) / 2, (ay + by0) / 2, (az + bz0) / 2, dx / l, dy / l, dz / l);
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
