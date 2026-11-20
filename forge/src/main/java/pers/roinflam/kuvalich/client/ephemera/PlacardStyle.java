package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.FLAME_0;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.FLARE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.FLOW_0;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.HEX_0;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.HEX_1;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.SOFT;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.SOFTCORE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.SOFTRING;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.ELECTRIC;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.FIRE;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.ICE;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.IMPACT;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.MAGNETIC;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.POISON;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.RADIATION;

/**
 * 谕牌：头后到肩上方竖着一块上宽下窄的全息牌。牌框上沿两道亮线、四角角标、上角尖翼，牌面一行行铭文滚动，
 * 两道铭文斜带交叉成 X、交叉处一枚徽印，牌底垂一条铭文坠；两手腕外侧各绕半圈铭文带、外面浮一块小牌。
 * 跑动时牌子后飘、身后留牌框残影；挥砍时牌面自上而下刷亮、牌框外扩；受击时各行错位、牌框裂出重影。
 */
public final class PlacardStyle extends ProtoStyle<PlacardStyle.St> {

    static final int GLYPH = EphemeraSprites.file("placard/glyph", true);
    static final int BAR = EphemeraSprites.file("placard/bar");
    static final int SIGIL = EphemeraSprites.file("placard/sigil");

    private static final int HIST = 96;
    private static final int[] OUT_FULL = {8, 3, 2, 3}, OUT_LITE = {4, 2, 1, 2}, SEG_GHOST = {2, 1, 1, 1}, SEG_PULSE = {3, 1, 1, 1};
    // 每侧两片尖翼：根部高度（× 半高）、根部横向（× 那一高度的半宽）、方向、长度、宽度（× 身高）
    private static final float[][] FINS = {{1, 1, 0.72F, 0.7F, 0.19F, 0.055F}, {0.55F, 1.03F, 0.97F, -0.24F, 0.1F, 0.04F}};

    /** 牌子这一帧的位置和朝向；残影要拿过去几帧的 */
    private static final class Fr {
        double t, cx, cy, cz;
        double axX, axY, axZ, ayX, ayY, ayZ, fzX, fzY, fzZ;
        float hh, wt, wb, rc;

        void set(Fr o) {
            t = o.t;
            cx = o.cx;
            cy = o.cy;
            cz = o.cz;
            axX = o.axX;
            axY = o.axY;
            axZ = o.axZ;
            ayX = o.ayX;
            ayY = o.ayY;
            ayZ = o.ayZ;
            fzX = o.fzX;
            fzY = o.fzY;
            fzZ = o.fzZ;
            hh = o.hh;
            wt = o.wt;
            wb = o.wb;
            rc = o.rc;
        }

        float half(float y) {
            return (wb + (wt - wb) * (y + hh) / (2 * hh)) / 2;
        }
    }

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        final ElementMotif.Sites sites = new ElementMotif.Sites();
        final FxKit.Spring c = new FxKit.Spring(), pd = new FxKit.Spring(), wl = new FxKit.Spring(), wr = new FxKit.Spring();
        final Fr[] hist = new Fr[HIST];
        int hHead, hN;
        FxParticles chips;
        FxTrail[] tr;
        boolean lite;
        float acc, roll;

        St() {
            for (int i = 0; i < HIST; i++) {
                hist[i] = new Fr();
            }
        }
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(24), rib2 = new EphemeraPen.Ribbon(24);
    private final EphemeraPen.Ribbon outR = new EphemeraPen.Ribbon(24), edgeR = new EphemeraPen.Ribbon(12);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final Fr cur = new Fr();
    private final double[] q12 = new double[12];
    private final double[] zx = new double[10], zy = new double[10], zz = new double[10];
    private final double[] finX = new double[4], finY = new double[4], finZ = new double[4];
    private final float[] finNx = new float[4];
    private final boolean[] finBig = new boolean[4];
    private final double[] ptX = new double[12], ptY = new double[12], ptZ = new double[12];
    private final float[] cuts = new float[4];
    private ElementMotif curFx;
    private ElementMotif.Sites curSites;

    // 这一帧
    private EphemeraStaging out;
    private FxMotion m;
    private int cap, finN, el, C, hot, pale, deep;
    private boolean lite, tight, tight2, sparse, crowded, heavy, air;
    private float t, H, W, sc, S, gK, A, Af, eW, swE, swS, hurtE, run, Bk, T, act, amp, breath, bt, bl, fk, yc, yT;
    private int qf, qh;
    private double qx, qy, qz;

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
        s.chips = null;
        s.c.reset();
        s.pd.reset();
        s.wl.reset();
        s.wr.reset();
        s.hN = 0;
        s.roll = 0;
    }

    private static void init(St s, FxMotion m) {
        s.lite = m.lite;
        s.chips = new FxParticles(m.lite ? 12 : 70);
        s.acc = 0;
        s.hN = 0;
        s.roll = 0;
        s.tr = new FxTrail[2];
        for (int i = 0; i < 2; i++) {
            s.tr[i] = new FxTrail(0.28F, 1.3F * m.sc, 0.04F * m.sc, 3 * m.sc);
        }
    }

    @Override
    protected void frame(St s, FxMotion mo, EphemeraDraw d) {
        m = mo;
        lite = m.lite;
        if (s.chips == null || s.lite != lite) {
            init(s, m);
        }
        boolean fp = d.firstPersonSelf;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        curFx = fx;
        curSites = s.sites;
        out = d.out;
        cap = lite ? EphemeraTier.CAP_REDUCED : EphemeraTier.CAP_FULL;
        float dt = m.dt;
        t = (float) m.t;
        H = m.H;
        W = m.W;
        sc = m.sc;
        Bk = m.burst;
        act = m.act;
        amp = m.amp;
        el = m.el;
        C = fx.C;
        hot = fx.hot;
        pale = fx.pale;
        deep = fx.deep;
        BodyPoints K = m.pts;
        T = m.clock();
        bt = m.beat(0);
        bl = m.blink(2);
        fk = m.flick(2);
        // 近白的颜色（冲击）柔光按亮度压，不然整块牌一片白
        float lum = (0.3F * (C >> 16 & 255) + 0.59F * (C >> 8 & 255) + 0.11F * (C & 255)) / 255.0F;
        gK = lum > 0.5F ? 0.5F / lum : 1;
        run = clamp(m.speed / 5.6F, 0, 1);
        float sneakK = m.sneak ? 0.85F : 1;
        air = !m.onGround;
        heavy = el == ELECTRIC || el == POISON || el == MAGNETIC;
        swS = m.since(m.tSwing);
        swE = m.env(swS, 0.02F, 0.06F, 0.42F);
        hurtE = m.env(m.since(m.tHurt), 0.02F, 0.15F, 0.4F);
        float jumpE = m.env(m.since(m.tJump), 0.04F, 0.1F, 0.4F);
        float landE = m.env(m.since(m.tLand), 0.01F, 0.05F, 0.35F);
        breath = 0.5F + 0.5F * (float) Math.sin(t * 1.5F);
        qf = (int) Math.floor(m.t / 0.09);
        qh = (int) Math.floor(m.t / 0.05);

        // 脚下的元素爆发先画：量出它占了多少面片，牌上的细节再按剩下的让（电、磁的爆发一下就上百）
        pen.cat = EphemeraStaging.BASE;
        fx.events(K.ground.x, K.ground.y, K.ground.z, W * 1.6F);
        fx.bursts();
        int load = out.pending();
        tight = load > (lite ? 12 : 20);
        tight2 = load > (lite ? 30 : 110);
        sparse = lite && tight;
        crowded = lite && load > 60;

        // 牌心带惯性；弹簧阻尼按相对宿主的速度算，匀速跑时不落后，只在加减速、转弯时甩
        float back = -W * 0.74F - 0.32F * W * run;
        float lift = 0.01F * H + 0.012F * H * breath * amp - 0.03F * H * landE;
        s.c.step(K.headCenter.x + m.fwdX * back, K.headCenter.y + lift, K.headCenter.z + m.fwdZ * back, m, 11, 0.62F);
        if (m.justHurt) {
            s.c.vx += m.fwdX * 2 * sc;
            s.c.vz += m.fwdZ * 2 * sc;
        }
        float tilt = 0.28F * run + 0.12F * (air ? 1 : 0) + 0.1F * jumpE + 0.15F * hurtE;
        s.roll += (clamp(-m.turn * 0.12F, -0.25F, 0.25F) - s.roll) * (1 - (float) Math.exp(-dt * 6));
        double il = 1 / Math.sqrt(1 + tilt * tilt);
        double y0x = -m.fwdX * tilt * il, y0y = il, y0z = -m.fwdZ * tilt * il;
        double cr = Math.cos(s.roll), sr = Math.sin(s.roll);
        Fr f = cur;
        f.t = m.t;
        f.cx = s.c.x;
        f.cy = s.c.y;
        f.cz = s.c.z;
        f.axX = m.rightX * cr + y0x * sr;
        f.axY = y0y * sr;
        f.axZ = m.rightZ * cr + y0z * sr;
        f.ayX = y0x * cr - m.rightX * sr;
        f.ayY = y0y * cr;
        f.ayZ = y0z * cr - m.rightZ * sr;
        double fzx = f.ayY * f.axZ - f.ayZ * f.axY, fzy = f.ayZ * f.axX - f.ayX * f.axZ, fzz = f.ayX * f.axY - f.ayY * f.axX;
        double fl = Math.sqrt(fzx * fzx + fzy * fzy + fzz * fzz);
        f.fzX = fzx / fl;
        f.fzY = fzy / fl;
        f.fzZ = fzz / fl;
        S = sneakK * (1 + 0.08F * swE * Bk + 0.03F * bt + 0.015F * breath * amp);
        f.hh = 0.26F * H * S * (1 + 0.05F * jumpE - 0.07F * landE);
        f.wt = 3.05F * W * S;
        f.wb = 1.15F * W * S;
        // 牌面绕头微微弯过来，侧面看也不至于只剩一条线
        f.rc = 2.3F * W;

        A = (0.62F + 0.38F * act + 0.06F * breath * amp) * bl;
        if (hurtE > 0.05F) {
            A *= 0.5F + 0.5F * hash((int) Math.floor(m.t * 24));
        }
        Af = A * fk;
        eW = 0.011F * H * sneakK;
        pushHist(s, f);

        float hh = f.hh;
        yc = hh - 2 * hh * (f.wt / (f.wt + f.wb));
        yT = hh + 0.05F * H * S;

        // 牌底的坠子：自己一根软弹簧；第一人称也照样步进，切回第三人称时不从老地方甩过来
        P(f, 0, -hh, 0);
        double topX = qx, topY = qy, topZ = qz;
        float pLen = 0.21F * H * S;
        s.pd.step(topX - f.ayX * pLen - f.fzX * 0.05F * sc, topY - f.ayY * pLen - f.fzY * 0.05F * sc, topZ - f.ayZ * pLen - f.fzZ * 0.05F * sc,
                m, 7, 0.4F);

        // 第一人称看自己：牌子挂在头后、手上的小牌在胸前两侧，都会糊在视野边上，一律不画；脚下的爆发照画
        s.sites.clear();
        if (!fp) {
            plate(f, s, topX, topY, topZ, pLen);
            element(f);
        }
        wrists(s, fp);
        fx.motes(s.sites, 10 * (0.5F + 0.5F * act), sc);
        if (!fp) {
            motion(s, f);
        }
        handTrails(s, fp);
        chips(s, f, fp, dt);
        m = null;
        out = null;
    }

    private int left() {
        return cap - out.pending();
    }

    // ---------- 牌 ----------

    private void plate(Fr f, St s, double topX, double topY, double topZ, float pLen) {
        float hh = f.hh, wt = f.wt, wb = f.wb;
        pen.cat = EphemeraStaging.BASE;

        // 体积：正对镜头一团柔光 + 贴着牌面的底光 + 暗托（中间淡，从背后看不把头盖黑）
        P(f, 0, hh * 0.1F, 0);
        pen.s(SOFT).rgb(C).a((0.13F + 0.05F * breath * amp + 0.14F * swE) * A * gK).billboard(qx, qy, qz, wt * 1.3F, hh * 3);
        if (!lite) {
            for (int sx = -1; sx <= 1; sx += 2) {
                P(f, sx * f.half(hh * 0.4F) * 0.6F, hh * 0.4F, 0);
                pen.s(SOFT).rgb(C).a((0.08F + 0.04F * act) * A * gK).billboard(qx, qy, qz, wt * 0.6F, hh * 1.6F);
            }
        }
        int NB = lite ? 3 : 6;
        for (int k = 0; k < NB; k++) {
            float f0 = (float) k / NB, f1 = (float) (k + 1) / NB;
            float xt0 = -f.half(hh) + 2 * f.half(hh) * f0, xt1 = -f.half(hh) + 2 * f.half(hh) * f1;
            float xb0 = -f.half(-hh) + 2 * f.half(-hh) * f0, xb1 = -f.half(-hh) + 2 * f.half(-hh) * f1;
            if (!lite) {
                float d0 = darkK(f0), d1 = darkK(f1);
                pen.alphaBlend().rgb(deep).a(0.6F * (m.sneak ? 0.85F : 1)).va(d0, d1, d1, d0);
                quadP(f, xt0, hh, xt1, hh, xb1, -hh, xb0, -hh, 0);
            }
            pen.s(SOFT).rgb(C).a((0.17F + 0.25F * swE) * A * gK).uv(f0, 0, f1, 1);
            quadP(f, xt0, hh, xt1, hh, xb1, -hh, xb0, -hh, 0);
        }

        // 后层铭文：牌后一点、字大而淡、反着滚，侧面看有前后两层
        float zB = 0.09F * sc, gapX = 0.42F * W * S;
        if (!lite && !tight) {
            int rgb = mul(C, 0.85F);
            for (int i = 0; i < 4; i++) {
                float y = hh * (0.62F - i * 0.42F), xr = f.half(y) * 0.92F, sr = -T * 0.025F * ((i & 1) != 0 ? 1 : -1) + i * 0.13F;
                if (y > hh * -0.5F) {
                    band(f, -xr, y, -gapX, y, hh * 0.2F, 0, GLYPH, rgb, 0.32F * A, sr, zB, 0, false);
                    band(f, gapX, y, xr, y, hh * 0.2F, 0, GLYPH, rgb, 0.32F * A, sr + 0.2F, zB, 0, false);
                } else {
                    band(f, -xr, y, xr, y, hh * 0.2F, 0, GLYPH, rgb, 0.32F * A, sr, zB, 0, false);
                }
            }
        }

        // 铭文行
        int NR = lite ? 3 : 7;
        float pad = hh * 0.12F, rowH = (2 * hh - 2 * pad) / NR, th = rowH * 0.72F;
        int shuffle = (int) Math.floor(m.t / 3.2);
        float scanY = hh - 2 * hh * clamp(swS / (0.3F * m.burstTime), 0, 1);
        float idlePh = (float) ((m.t / 3.4) % 1), idleY = hh * 1.1F - idlePh * 2.6F * hh;
        float[] cuts = this.cuts;
        for (int i = 0; i < NR; i++) {
            float ym = hh - pad - (i + 0.5F) * rowH;
            int dir = (i & 1) != 0 ? 1 : -1;
            float shift = hurtE > 0 ? (hash(qh * 7 + i * 13) - 0.5F) * 0.5F * W * hurtE : 0;
            int nSeg = lite ? 2 : 2 + (hash(i * 5 + shuffle * 3) > 0.5F ? 1 : 0);
            cuts[0] = 0;
            for (int k = 1; k < nSeg; k++) {
                cuts[k] = (float) k / nSeg + (hash(i * 31 + k * 7 + shuffle * 11) - 0.5F) * 0.18F;
            }
            cuts[nSeg] = 1;
            float xr = f.half(ym) * 0.86F;
            float scan = (float) Math.exp(-Math.pow((ym - scanY) / (rowH * 0.9F), 2)) * swE;
            float near = (float) Math.exp(-Math.pow((ym - idleY) / (rowH * 1.2F), 2));
            float rowOn = 1;
            if (el == ELECTRIC) {
                rowOn = hash(qh * 7 + i * 3) > 0.25F ? 1 : 0.12F;
            } else if (el == RADIATION) {
                rowOn = m.blink(10 + i);
            }
            // 每行底下一道横向柔光，牌面像一排排亮着的屏
            if (!lite && !tight2) {
                float ga = (0.2F + 0.18F * near + 0.3F * scan) * A * gK * rowOn;
                if (ym > yc) {
                    band(f, -xr, ym, -gapX, ym, th * 2.4F, 1, BAR, C, ga);
                    band(f, gapX, ym, xr, ym, th * 2.4F, 1, BAR, C, ga);
                } else {
                    band(f, -xr, ym, xr, ym, th * 2.4F, 1, BAR, C, ga);
                }
            }
            for (int k = 0; k < nSeg; k++) {
                float f0 = cuts[k] + 0.02F, f1 = cuts[k + 1] - 0.02F;
                if (f1 <= f0) {
                    continue;
                }
                float h = hash(qf * 17 + i * 7 + k * 3);
                float flk = h < 0.06F ? 0.15F : 0.65F + 0.35F * h;
                if (hurtE > 0 && hash(qh * 3 + i + k * 5) < 0.4F * hurtE) {
                    flk = 0.1F;
                }
                float a = Math.min(1.2F, 0.95F * A * flk * rowOn * (1 + 0.5F * swE) * (1 + 0.6F * near) + scan);
                int rgb = scan > 0.2F || i == 0 ? hot : C;
                float xa = -xr + 2 * xr * f0 + shift, xb = -xr + 2 * xr * f1 + shift, sr = T * 0.04F * dir * (1 + 1.5F * run) + hash(i * 3 + k);
                // 交叉点以上头所在的那一块留空：从背后看头不被字盖住，正面看那里本来就被头挡着
                if (ym > yc && xa < gapX && xb > -gapX) {
                    if (xa < -gapX - 0.02F) {
                        band(f, xa, ym, -gapX, ym, th, 0, GLYPH, rgb, a, sr, 0, 0, false);
                    }
                    if (xb > gapX + 0.02F) {
                        band(f, gapX, ym, xb, ym, th, 0, GLYPH, rgb, a, sr + 0.17F, 0, 0, false);
                    }
                } else {
                    band(f, xa, ym, xb, ym, th, 0, GLYPH, rgb, a, sr, 0, 0, false);
                }
            }
        }
        // 常驻的一道慢扫描线 + 挥砍时自上而下的亮带
        if (idleY < hh && idleY > -hh) {
            float xr = f.half(idleY), k = (float) Math.sin(Math.PI * (idleY + hh) / (2 * hh));
            band(f, -xr, idleY, xr, idleY, hh * 0.22F, lite ? 1 : 3, BAR, C, 0.22F * k * A * gK);
            if (!lite) {
                band(f, -xr, idleY, xr, idleY, eW * 0.7F, 3, EphemeraSprites.WHITE, hot, 0.5F * k * A);
            }
        }
        if (swE > 0.02F && scanY > -hh) {
            float xr = f.half(scanY) * 1.04F;
            band(f, -xr, scanY, xr, scanY, hh * 0.5F, lite ? 1 : 3, BAR, hot, 0.5F * swE * gK);
            band(f, -xr, scanY, xr, scanY, eW * 1.2F, lite ? 1 : 3, EphemeraSprites.WHITE, WHITE, 0.8F * swE);
        }

        // X 两道铭文斜带，光团沿带子往交叉处流；两道带子在下半截挤到一起，往下逐渐淡掉，免得交叉处叠成一团白
        float aw = 0.085F * H * S * (1 + 0.3F * swE);
        for (int sx = -1; sx <= 1; sx += 2) {
            float x0 = sx * f.half(hh) * 0.93F, y0 = hh * 0.93F, x1 = -sx * f.half(-hh) * 0.93F, y1 = -hh * 0.93F;
            if (!sparse) {
                band(f, x0, y0, x1, y1, aw * 2.8F, lite ? 2 : 4, BAR, C, 0.55F * A * gK, Float.NaN, 0, 0, true);
            }
            band(f, x0, y0, x1, y1, aw, 0, GLYPH, sx > 0 ? C : pale, 0.95F * A * (1 + 0.4F * swE), -T * 0.09F * sx, 0, 0, true);
            if (!lite) {
                for (int sg = -1; sg <= 1; sg += 2) {
                    band(f, x0, y0, x1, y1, eW * 0.55F, 3, EphemeraSprites.WHITE, hot, 0.7F * Af, Float.NaN, 0, sg * aw * 0.62F, false);
                }
            }
            // 外侧再并一股细铭文，斜带看着是几股绞在一起
            if (!lite && !tight) {
                band(f, x0, y0, x1, y1, aw * 0.42F, 0, GLYPH, C, 0.7F * A, T * 0.13F * sx + 0.3F, 0, -sx * aw * 1.05F, true);
            }
            int NPK = lite ? 1 : 2;
            for (int j = 0; j < NPK; j++) {
                float fr = (t * 0.42F * (1 + 0.8F * run) + (float) j / NPK + (sx > 0 ? 0.27F : 0)) % 1;
                float px = x0 + (x1 - x0) * fr, py = y0 + (y1 - y0) * fr, k = (float) Math.sin(Math.PI * fr);
                P(f, px, py, 0);
                pen.s(SOFTCORE).rgb(hot).a(0.75F * k * A * gK).billboard(qx, qy, qz, aw * 2.2F, aw * 2.2F);
                if (!lite && !tight) {
                    band(f, px - (x1 - x0) * 0.06F, py - (y1 - y0) * 0.06F, px + (x1 - x0) * 0.06F, py + (y1 - y0) * 0.06F, aw * 0.9F, 1,
                            GLYPH, WHITE, 0.7F * k * A, T * 0.3F, 0, 0, false);
                }
            }
        }
        // 交叉处的徽印（辐射换成六边形，在元素那段画）
        P(f, 0, yc, 0);
        double cXx = qx, cXy = qy, cXz = qz;
        if (el != RADIATION) {
            pen.s(SIGIL).rgb(el == ICE ? pale : C).a(0.75F * Af);
            FxKit.plate(pen, cXx, cXy, cXz, f.axX, f.axY, f.axZ, f.ayX, f.ayY, f.ayZ, 0.21F * H * S * (1 + 0.25F * swE), T * 0.35F + swE * 2);
        }
        if (!lite) {
            pen.s(SOFT).rgb(C).a(0.12F * A * gK).billboard(cXx, cXy, cXz, 0.26F * H, 0.26F * H);
        }

        // 牌框：上沿亮线、两侧和下沿
        poly(outR, f, lite ? OUT_LITE : OUT_FULL, 1, 0, 0, eW, 1);
        if (!lite) {
            rib.scaled(outR, 8, 1);
            glowLine(rib, 0.36F * Af, C);
        }
        rib.scaled(outR, 1 + 0.5F * swE, 1);
        coreLine(rib, 0.9F * Af * (1 + 0.4F * swE), hot);
        // 上沿上面再一道，中间留给题签
        for (int sx = -1; sx <= 1; sx += 2) {
            int n = lite ? 2 : 3;
            float xa = sx * f.half(hh) * 1.04F, xb = sx * 0.34F * W;
            rib.clear();
            for (int k = 0; k <= n; k++) {
                P(f, xa + (xb - xa) * k / n, yT, 0);
                rib.add(qx, qy, qz, eW * 0.8F, 1);
            }
            coreLine(rib, 0.8F * Af, hot);
            if (!lite) {
                rib2.scaled(rib, 5, 1);
                glowLine(rib2, 0.25F * Af, C);
            }
        }
        // 题签：上沿正中一小条铭文 + 一颗菱形
        float gap = 0.05F * H * S;
        band(f, -0.3F * W * S, yT, 0.3F * W * S, yT, gap * 0.9F, 1, GLYPH, hot, 0.9F * Af, T * 0.05F, 0, 0, false);
        {
            float yg = yT + gap * 1.15F, r = gap * 0.75F * (1 + 0.3F * swE + 0.2F * bt);
            pen.rgb(C).a(0.9F * Af);
            quadP(f, 0, yg + r, r * 0.6F, yg, 0, yg - r, -r * 0.6F, yg, 0);
            pen.rgb(WHITE).a(0.85F * Af);
            quadP(f, 0, yg + r * 0.5F, r * 0.3F, yg, 0, yg - r * 0.5F, -r * 0.3F, yg, 0);
            if (!lite) {
                P(f, 0, yg, 0);
                pen.s(SOFTCORE).rgb(hot).a(0.4F * A * gK).billboard(qx, qy, qz, r * 4, r * 4);
            }
        }
        // 角标：每个角外面一个 L 形，挥砍时往外推
        float bLen = 0.075F * H * S * (1 + 0.6F * swE), bOff = eW * (3 + 4 * swE * Bk);
        int nCorner = sparse ? 0 : lite ? 2 : 4;
        for (int i = 0; i < nCorner; i++) {
            float px = cornerX(f, i), py = cornerY(f, i);
            float e1x = cornerX(f, (i + 3) % 4) - px, e1y = cornerY(f, (i + 3) % 4) - py;
            float e2x = cornerX(f, i + 1) - px, e2y = cornerY(f, i + 1) - py;
            float l1 = (float) Math.hypot(e1x, e1y), l2 = (float) Math.hypot(e2x, e2y);
            e1x /= l1;
            e1y /= l1;
            e2x /= l2;
            e2y /= l2;
            float ox = -(e1x + e2x), oy = -(e1y + e2y), lo = (float) Math.hypot(ox, oy);
            ox /= lo;
            oy /= lo;
            float bx = px + ox * bOff, by = py + oy * bOff;
            band(f, bx, by, bx + e1x * bLen, by + e1y * bLen, eW * 1.1F, 1, EphemeraSprites.WHITE, hot, 0.85F * Af);
            band(f, bx, by, bx + e2x * bLen, by + e2y * bLen, eW * 1.1F, 1, EphemeraSprites.WHITE, hot, 0.85F * Af);
        }
        // 尖翼：上角一大一小两片，远处靠它认出牌的轮廓
        finN = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (float[] fin : FINS) {
                float L0 = fin[4];
                boolean big = L0 > 0.15F;
                if (sparse && !big) {
                    continue;
                }
                float by = hh * fin[0], bx = sx * f.half(by) * fin[1];
                float dx = sx * fin[2], dy = fin[3], dl = (float) Math.hypot(dx, dy), ux = dx / dl, uy = dy / dl, nx = -uy, ny = ux;
                float L = L0 * H * S * (1 + 0.9F * swE * Bk + 0.05F * breath * amp + 0.3F * bt), w = fin[5] * H * S;
                float tipX = bx + ux * L, tipY = by + uy * L, midX = bx + ux * L * 0.32F, midY = by + uy * L * 0.32F;
                float bkX = bx - ux * L * 0.08F, bkY = by - uy * L * 0.08F;
                pen.rgb(el == ICE ? pale : C).a(0.9F * Af);
                quadP(f, tipX, tipY, midX + nx * w / 2, midY + ny * w / 2, bkX, bkY, midX - nx * w / 2, midY - ny * w / 2, 0);
                pen.rgb(WHITE).a(0.75F * Af);
                quadP(f, tipX, tipY, midX + nx * w * 0.14F, midY + ny * w * 0.14F, bx, by, midX - nx * w * 0.14F, midY - ny * w * 0.14F, 0);
                P(f, tipX, tipY, 0);
                finX[finN] = qx;
                finY[finN] = qy;
                finZ[finN] = qz;
                finNx[finN] = (float) (f.axX * ux + f.ayX * uy);
                finBig[finN] = big;
                finN++;
                if (!lite && !tight && big) {
                    pen.s(FLARE).rgb(hot).a(0.55F * A * (0.6F + 0.4F * (float) Math.sin(t * 2.6F + sx))).billboard(qx, qy, qz, 0.09F * H, 0.09F * H, t * 0.3F);
                }
            }
        }

        if (!lite) {
            // 上沿往上投几道淡光，像全息投影的光幕边
            for (int j = 0; j < 5; j++) {
                float x = (j / 4.0F - 0.5F) * wt * 0.82F, k = 0.6F + 0.4F * (float) Math.sin(t * 1.3F + j * 1.9F);
                P(f, x, yT, 0);
                float len = 0.2F * H * S * k * (1 + 0.6F * swE);
                pen.s(BAR).rgb(C).a(0.3F * A * gK * k).va(1, 0, 0, 1)
                        .beam(qx, qy, qz, qx + f.ayX * len, qy + f.ayY * len, qz + f.ayZ * len, 0.06F * H);
            }
            // 牌边浮着的数据点：固定几个槽位，各自往上飘、忽明忽暗
            for (int j = 0; j < 12; j++) {
                double rate = 0.22 + 0.1 * prn(j), u = m.t * rate + prn(j + 40);
                float ph = (float) (u % 1);
                int cyc = (int) Math.floor(u);
                float side = prn(j * 3 + cyc * 7);
                float x, y;
                if (side < 0.5F) {
                    x = (prn(j * 5 + cyc) * 2 - 1) * wt * 0.5F;
                    y = yT + 0.03F * H;
                } else {
                    int sx = side < 0.75F ? -1 : 1;
                    y = (prn(j * 9 + cyc) * 2 - 1) * hh * 0.8F;
                    x = sx * (f.half(y) + 0.04F * H);
                }
                P(f, x, y, 0);
                float up = ph * 0.16F * H, bk = -ph * 0.5F * run * sc;
                float tw = (float) Math.sin(Math.PI * ph) * (hash((int) Math.floor(m.t * 12) + j * 31) < 0.15F ? 0.3F : 1);
                pen.s(EphemeraSprites.WHITE).rgb(j % 3 != 0 ? C : hot).a(0.9F * tw * A)
                        .billboard(qx + f.ayX * up + m.fwdX * bk, qy + f.ayY * up, qz + f.ayZ * up + m.fwdZ * bk, 0.024F * H, 0.024F * H,
                                (j & 1) != 0 ? (float) Math.PI / 4 : 0);
            }
        }

        // 牌底垂的铭文坠
        double ex = s.pd.x, ey = s.pd.y, ez = s.pd.z;
        double ctlX = topX - f.ayX * pLen * 0.5, ctlY = topY - f.ayY * pLen * 0.5, ctlZ = topZ - f.ayZ * pLen * 0.5;
        for (int k = 0; k <= 4; k++) {
            double g = k / 4.0, a0 = (1 - g) * (1 - g), a1 = 2 * g * (1 - g), a2 = g * g;
            ptX[k] = topX * a0 + ctlX * a1 + ex * a2;
            ptY[k] = topY * a0 + ctlY * a1 + ey * a2;
            ptZ[k] = topZ * a0 + ctlZ * a1 + ez * a2;
        }
        if (!lite) {
            rib.clear();
            for (int k = 0; k <= 4; k++) {
                rib.add(ptX[k], ptY[k], ptZ[k], 0.11F * H, 1 - k / 6.0F);
            }
            pen.s(BAR).rgb(C).a(0.3F * A * gK).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
        }
        rib.clear();
        for (int k = 0; k <= 4; k++) {
            rib.add(ptX[k], ptY[k], ptZ[k], 0.045F * H, 1 - k / 7.0F);
        }
        float pScroll = ((T * 0.06F) % 0.5F + 0.5F) % 0.5F;
        pen.s(GLYPH).rgb(C).a(0.95F * A).ribbon(rib, EphemeraPen.FACE_CAM, pScroll, pScroll + Math.min(0.5F, 0.5F * pLen / (0.045F * H * 5)));
        if (!sparse) {
            double hx = f.axX * 0.07F * W, hy = f.axY * 0.07F * W, hz = f.axZ * 0.07F * W;
            double dx = f.ayX * 0.012F * H, dy = f.ayY * 0.012F * H, dz = f.ayZ * 0.012F * H;
            pen.rgb(hot).a(0.85F * Af).quad(topX - hx, topY - hy, topZ - hz, topX + hx, topY + hy, topZ + hz,
                    topX + hx - dx, topY + hy - dy, topZ + hz - dz, topX - hx - dx, topY - hy - dy, topZ - hz - dz);
        }
        pen.s(EphemeraSprites.WHITE).rgb(hot).a(0.9F * A).billboard(ex, ey, ez, 0.04F * H, 0.04F * H, (float) Math.PI / 4);
        if (!lite) {
            pen.s(SOFTCORE).rgb(C).a(0.5F * A * gK).billboard(ex, ey, ez, 0.12F * H, 0.12F * H);
        }
    }

    private static float darkK(float f) {
        return 0.04F + 0.44F * (float) Math.pow(Math.abs(2 * f - 1), 1.5);
    }

    // ---------- 元素 ----------

    private void element(Fr f) {
        ElementMotif fx = curFx;
        float hh = f.hh;
        pen.cat = EphemeraStaging.BASE;
        int NT = heavy ? (lite ? 2 : 3) : lite ? 5 : 9, NS = lite ? 2 : 3;
        float hT = f.half(hh);
        // 上沿：外侧朝上
        edgeR.clear();
        for (int i = 0; i < NT; i++) {
            P(f, -hT + 2 * hT * (i + 0.5F) / NT, yT, 0);
            edgeR.add(qx, qy, qz, 0, 1);
        }
        float ew = 0.045F * H * (1 + 0.4F * swE) * (el == FIRE ? 1.35F : el == ICE ? 1.4F : 1);
        // 精简档两次爆发叠在一起时（全开档连砍两下电就有八十多片）上沿的点缀让给爆发；只有电的一次爆发时留正中一根
        if (lite && el == ELECTRIC && tight) {
            if (!crowded) {
                P(f, 0, yT, 0);
                fx.accent(qx, qy, qz, (float) f.ayX, (float) f.ayY, (float) f.ayZ, ew * 3, ew, 97, 0.85F * A);
            }
        } else if (!crowded) {
            orient(edgeR, f.ayX, f.ayY, f.ayZ);
            fx.edge(edgeR, ew, 0.85F * A, 1);
        }
        // 两侧：外侧是牌心到侧边中点的方向
        P(f, 0, -hh + 2 * hh * 0.5F / NS, 0);
        double mx = qx, my = qy, mz = qz;
        float y0s = -hh + 2 * hh * 0.5F / NS;
        P(f, -f.half(y0s), y0s, 0);
        float oLx = (float) (qx - mx), oLy = (float) (qy - my), oLz = (float) (qz - mz), lL = (float) Math.sqrt(oLx * oLx + oLy * oLy + oLz * oLz);
        P(f, f.half(y0s), y0s, 0);
        float oRx = (float) (qx - mx), oRy = (float) (qy - my), oRz = (float) (qz - mz), lR = (float) Math.sqrt(oRx * oRx + oRy * oRy + oRz * oRz);
        oLx /= lL;
        oLy /= lL;
        oLz /= lL;
        oRx /= lR;
        oRy /= lR;
        oRz /= lR;
        if (!lite && !heavy) {
            for (int sd = -1; sd <= 1; sd += 2) {
                edgeR.clear();
                for (int i = 0; i < NS; i++) {
                    float y = -hh + 2 * hh * (i + 0.5F) / NS;
                    P(f, sd * f.half(y), y, 0);
                    edgeR.add(qx, qy, qz, 0, 1);
                }
                if (sd < 0) {
                    orient(edgeR, oLx, oLy, oLz);
                } else {
                    orient(edgeR, oRx, oRy, oRz);
                }
                fx.edge(edgeR, ew * 0.8F, 0.7F * A, sd < 0 ? 2 : 3);
            }
        }
        float dnX = (float) -f.ayX, dnY = (float) -f.ayY, dnZ = (float) -f.ayZ;
        P(f, 0, yc, 0);
        double cXx = qx, cXy = qy, cXz = qz;

        if (el == FIRE) {
            // 牌面下半烧起来：几根火舌贴着牌面往上舔
            int NF = lite ? 3 : 6;
            for (int j = 0; j < NF; j++) {
                float yb = -hh * (0.95F - 0.5F * prn(j * 3 + 1)), xr = f.half(yb) * 0.8F, x = -xr + 2 * xr * (j + 0.5F) / NF;
                float L = hh * (0.55F + 0.35F * noise(t * 2.6F + j * 1.7F, j)) * (1 + 0.5F * swE), w = hh * 0.32F;
                float sway = (noise(t * 2.1F + j, j + 5) - 0.5F) * w * 0.8F;
                pen.s(FLAME_0 + (j & 1)).rgb(C).a(0.6F * A);
                quadP(f, x - w / 2 + sway, yb + L, x + w / 2 + sway, yb + L, x + w / 2, yb, x - w / 2, yb, 0);
                if (!lite) {
                    pen.s(FLAME_0).rgb(hot).a(0.5F * A);
                    quadP(f, x - w * 0.25F + sway * 0.6F, yb + L * 0.6F, x + w * 0.25F + sway * 0.6F, yb + L * 0.6F, x + w * 0.25F, yb, x - w * 0.25F, yb, 0);
                }
            }
            for (int i = 0; i < finN; i++) {
                if (finBig[i]) {
                    fx.accent(finX[i], finY[i], finZ[i], 0, 1, 0, 0.16F * H * (1 + 0.6F * swE), 0.06F * H, 40 + (finNx[i] > 0 ? 1 : 0), 0.8F * A);
                }
            }
        } else if (el == ICE) {
            // 牌面结一层霜、下沿挂冰凌、交叉处一朵六瓣霜花
            int NBk = lite ? 2 : 4;
            for (int k = 0; k < NBk; k++) {
                float f0 = (float) k / NBk, f1 = (float) (k + 1) / NBk;
                pen.rgb(pale).a(0.1F * A).va(1, 1, 0.3F, 0.3F);
                quadP(f, -f.half(hh) + 2 * f.half(hh) * f0, hh, -f.half(hh) + 2 * f.half(hh) * f1, hh,
                        -f.half(-hh) + 2 * f.half(-hh) * f1, -hh, -f.half(-hh) + 2 * f.half(-hh) * f0, -hh, 0);
            }
            int NI = lite ? 3 : 6;
            for (int i = 0; i < NI; i++) {
                float fr = (i + 0.5F) / NI, y = -hh + 2 * hh * 0.12F * Math.abs(2 * fr - 1), x = (2 * fr - 1) * f.half(y) * 0.95F;
                P(f, x, y, 0);
                fx.accent(qx, qy, qz, dnX, dnY, dnZ, 0.22F * H * (0.6F + 0.6F * prn(i * 7)) * (1 + 0.4F * swE), 0.065F * H, 20 + i, 0.9F * A);
            }
            if (!lite) {
                float r = 0.13F * H * S;
                for (int i = 0; i < 6; i++) {
                    float a = i / 6.0F * TAU - T * 0.1F, dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    P(f, dx * r * 0.15F, yc + dy * r * 0.15F, 0);
                    double ax = qx, ay = qy, az = qz;
                    P(f, dx * r, yc + dy * r, 0);
                    pen.rgb(WHITE).a(0.75F * A).beam(ax, ay, az, qx, qy, qz, eW * 1.2F);
                    P(f, dx * r * 0.5F, yc + dy * r * 0.5F, 0);
                    ax = qx;
                    ay = qy;
                    az = qz;
                    for (int sg = -1; sg <= 1; sg += 2) {
                        float sd = sg * r * 0.2F;
                        P(f, dx * r * 0.72F - dy * sd, yc + dy * r * 0.72F + dx * sd, 0);
                        pen.rgb(pale).a(0.7F * A).beam(ax, ay, az, qx, qy, qz, eW * 0.8F);
                    }
                }
            }
        } else if (el == ELECTRIC) {
            // 牌框上两点之间电弧横跳
            int q = (int) Math.floor(m.t / 0.06), NA = sparse ? 0 : lite ? 1 : 2, nO = outR.n;
            for (int i = 0; i < NA; i++) {
                if (hash(q * 3 + i * 11) < 0.22F) {
                    continue;
                }
                int i0 = (int) Math.floor(hash(q * 7 + i) * nO), i1 = (i0 + (int) Math.floor(nO * (0.3F + 0.4F * hash(q * 9 + i)))) % nO;
                arc(outR.x[i0], outR.y[i0], outR.z[i0], outR.x[i1], outR.y[i1], outR.z[i1], f, 8, 0.05F * H, q * 13 + i);
                ribbonZig(8, 0.07F * H);
                pen.s(SOFT).rgb(C).a(0.55F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                ribbonZig(8, 0.012F * H);
                pen.rgb(hot).a(A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                pen.s(FLARE).rgb(hot).a(0.85F * A).billboard(outR.x[i1], outR.y[i1], outR.z[i1], 0.12F * H, 0.12F * H, q);
            }
            // 上角尖翼当电极：翼尖往牌面里劈一道
            if (!lite) {
                for (int i = 0; i < finN; i++) {
                    if (!finBig[i] || hash(q * 5 + (finNx[i] > 0 ? 1 : 0)) < 0.35F) {
                        continue;
                    }
                    P(f, (hash(q * 3 + 7) - 0.5F) * f.wt * 0.5F, (hash(q * 11 + 2) - 0.2F) * hh, 0);
                    arc(finX[i], finY[i], finZ[i], qx, qy, qz, f, 7, 0.04F * H, q * 17 + 3);
                    ribbonZig(7, 0.045F * H);
                    pen.s(SOFT).rgb(C).a(0.45F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    ribbonZig(7, 0.008F * H);
                    pen.rgb(hot).a(A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                }
            }
        } else if (el == POISON) {
            // 牌底往下挂黏滴，牌面里气泡慢慢往上冒、一层毒絮往下淌
            float dl = 0.15F * H * (1 + 0.3F * swE), dw = 0.06F * H;
            if (lite) {
                P(f, 0, -hh, 0);
                fx.accent(qx, qy, qz, dnX, dnY, dnZ, dl, dw, 60, 0.9F * A);
            } else {
                int n = 0;
                for (int i = 0; i < 3; i++) {
                    P(f, (i - 1) * f.half(-hh) * 0.75F, -hh, 0);
                    fx.accent(qx, qy, qz, dnX, dnY, dnZ, dl, dw, 60 + n++, 0.9F * A);
                }
                if (!tight) {
                    for (int i = 0; i < finN; i++) {
                        if (!finBig[i]) {
                            fx.accent(finX[i], finY[i], finZ[i], dnX, dnY, dnZ, dl, dw, 60 + n++, 0.9F * A);
                        }
                    }
                }
                for (int j = 0; j < 2; j++) {
                    float y = hh - 2 * hh * ((T * 0.12F + j * 0.5F) % 1), k = (float) Math.sin(Math.PI * (y + hh) / (2 * hh)), xr = f.half(y);
                    band(f, -xr, y, xr, y, hh * 0.6F, 2, FLOW_0, mul(C, 0.85F), 0.6F * k * A);
                }
                for (int j = 0; j < 5; j++) {
                    double u = m.t * 0.3 + j / 5.0;
                    float ph = (float) (u % 1), y = -hh + 2 * hh * ph, x = (prn(j * 5 + (int) Math.floor(u)) * 2 - 1) * f.half(y) * 0.8F;
                    P(f, x, y, -0.02F * sc);
                    float sz = 0.035F * H * (0.6F + ph);
                    pen.s(SOFTRING).rgb(pale).a(0.8F * A * (ph < 0.9F ? 1 : 0)).billboard(qx, qy, qz, sz, sz);
                }
            }
        } else if (el == IMPACT) {
            // 每一拍牌框往外震两道
            float ph = (float) ((m.t % 0.85) / 0.85);
            for (int r = 0; r < 2; r++) {
                float dl = r == 0 ? 0 : 0.15F, ww = r == 0 ? 1 : 0.6F;
                float fr = Math.max(0, ph - dl) / (1 - dl), k = 1 + 0.45F * fr;
                if (fr <= 0) {
                    continue;
                }
                if (!lite && !tight) {
                    poly(rib, f, SEG_PULSE, k, 0, 0, eW * 6, 1);
                    glowLine(rib, 0.5F * (1 - fr) * ww * A, pale);
                }
                poly(rib, f, SEG_PULSE, k, 0, 0, eW * 1.6F, 1);
                coreLine(rib, 0.85F * (1 - fr) * ww * A, pale);
            }
            for (int i = 0; i < 4; i++) {
                float px = cornerX(f, i), py = cornerY(f, i);
                double dx = f.axX * Math.signum(px) + f.ayX * Math.signum(py) * 0.8, dy = f.axY * Math.signum(px) + f.ayY * Math.signum(py) * 0.8,
                        dz = f.axZ * Math.signum(px) + f.ayZ * Math.signum(py) * 0.8, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                P(f, px, py, 0);
                fx.accent(qx, qy, qz, (float) (dx / l), (float) (dy / l), (float) (dz / l), 0.12F * H, 0.04F * H, 70 + i, 0.8F * A);
            }
        } else if (el == MAGNETIC) {
            // 两道磁力线从上角绕到牌后、收回牌底，光珠沿线流
            for (int sx = lite ? 1 : -1; sx <= 1; sx += 2) {
                P(f, sx * f.half(hh), hh, 0);
                double a0x = qx, a0y = qy, a0z = qz;
                P(f, 0, -hh, 0);
                double a1x = qx, a1y = qy, a1z = qz;
                float bow = (0.38F + 0.05F * (float) Math.sin(t * 1.3F + sx)) * sc;
                for (int i = 0; i <= 10; i++) {
                    double g = i / 10.0, b = Math.sin(Math.PI * g);
                    ptX[i] = a0x + (a1x - a0x) * g - f.fzX * b * bow + f.axX * sx * b * 0.3F * sc;
                    ptY[i] = a0y + (a1y - a0y) * g - f.fzY * b * bow + f.axY * sx * b * 0.3F * sc;
                    ptZ[i] = a0z + (a1z - a0z) * g - f.fzZ * b * bow + f.axZ * sx * b * 0.3F * sc;
                }
                if (!lite && !tight) {
                    rib.clear();
                    for (int i = 0; i <= 10; i++) {
                        rib.add(ptX[i], ptY[i], ptZ[i], 0.07F * H, 1);
                    }
                    pen.s(BAR).rgb(C).a(0.4F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                }
                rib.clear();
                for (int i = 0; i <= 10; i++) {
                    rib.add(ptX[i], ptY[i], ptZ[i], 0.012F * H, 1);
                }
                pen.rgb(pale).a(0.85F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                for (int b = 0; b < 3; b++) {
                    float fr = (t * 0.5F + b / 3.0F + (sx > 0 ? 0.17F : 0)) % 1;
                    int i = Math.min(9, (int) Math.floor(fr * 10));
                    double g = fr * 10 - i;
                    pen.s(SOFTCORE).rgb(hot).a(0.9F * A).billboard(ptX[i] + (ptX[i + 1] - ptX[i]) * g, ptY[i] + (ptY[i + 1] - ptY[i]) * g,
                            ptZ[i] + (ptZ[i + 1] - ptZ[i]) * g, 0.06F * H, 0.06F * H);
                }
            }
        } else if (el == RADIATION) {
            // 交叉处换成六边形 + 三扇慢转的扇形光，牌面上咔哒闪点
            for (int i = 0; i < 3; i++) {
                float a = i / 3.0F * TAU + T * 0.4F;
                pen.s(SOFT).rgb(C).a(0.5F * A * bl);
                FxKit.ring(pen, cXx, cXy, cXz, f.axX, f.axY, f.axZ, f.ayX, f.ayY, f.ayZ, 0.11F * H * S, 0.1F * H * S, lite ? 2 : 3, a, a + 0.8F, 0);
            }
            pen.s(HEX_0).rgb(hot).a(0.85F * A * bl);
            FxKit.plate(pen, cXx, cXy, cXz, f.axX, f.axY, f.axZ, f.ayX, f.ayY, f.ayZ, 0.12F * H * S, T * 0.3F);
            pen.s(HEX_1).rgb(C).a(0.45F * A * Math.min(1.3F, bl)).billboard(cXx, cXy, cXz, 0.2F * H, 0.2F * H);
            if (!lite) {
                for (int j = 0; j < 4; j++) {
                    int q = (int) Math.floor(m.t / 0.08) * 4 + j;
                    if (hash(q * 7) < 0.55F) {
                        continue;
                    }
                    float y = (hash(q * 3 + 1) * 2 - 1) * hh * 0.85F, x = (hash(q * 5 + 2) * 2 - 1) * f.half(y) * 0.85F;
                    P(f, x, y, -0.02F * sc);
                    pen.s(FLARE).rgb(hot).a(0.95F * A).billboard(qx, qy, qz, 0.11F * H, 0.11F * H, 0.785F);
                }
            }
        }

        // 身上冒东西的发射点：上沿、两侧、牌底
        ElementMotif.Sites sites = curSites;
        for (int i = 0; i < NT; i++) {
            P(f, -hT + 2 * hT * (i + 0.5F) / NT, yT, 0);
            sites.add(qx, qy, qz, (float) f.ayX, (float) f.ayY, (float) f.ayZ);
        }
        for (int i = 0; i < NS; i++) {
            float y = -hh + 2 * hh * (i + 0.5F) / NS;
            P(f, -f.half(y), y, 0);
            sites.add(qx, qy, qz, oLx, oLy, oLz);
            P(f, f.half(y), y, 0);
            sites.add(qx, qy, qz, oRx, oRy, oRz);
        }
        for (int i = 0; i < 3; i++) {
            P(f, (i - 1) * f.half(-hh) * 0.75F, -hh, 0);
            sites.add(qx, qy, qz, dnX, dnY, dnZ);
        }
    }

    /** edge 按「切线 × 视线」定外侧，点的顺序反了就会朝牌里长 */
    private void orient(EphemeraPen.Ribbon r, double ox, double oy, double oz) {
        int n = r.n;
        if (n < 2) {
            return;
        }
        double tx = r.x[n - 1] - r.x[0], ty = r.y[n - 1] - r.y[0], tz = r.z[n - 1] - r.z[0];
        double cx = ty * pen.fwdZ - tz * pen.fwdY, cy = tz * pen.fwdX - tx * pen.fwdZ, cz = tx * pen.fwdY - ty * pen.fwdX;
        if (cx * ox + cy * oy + cz * oz >= 0) {
            return;
        }
        for (int i = 0, j = n - 1; i < j; i++, j--) {
            double x = r.x[i], y = r.y[i], z = r.z[i];
            r.x[i] = r.x[j];
            r.y[i] = r.y[j];
            r.z[i] = r.z[j];
            r.x[j] = x;
            r.y[j] = y;
            r.z[j] = z;
        }
    }

    /** a 到 b 的折线，中间各点沿「ab × 牌面法线」随机偏，写进 zx/zy/zz */
    private void arc(double ax, double ay, double az, double bx, double by, double bz, Fr f, int n, float amp, int seed) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-9;
        dx /= l;
        dy /= l;
        dz /= l;
        double sx = dy * f.fzZ - dz * f.fzY, sy = dz * f.fzX - dx * f.fzZ, sz = dx * f.fzY - dy * f.fzX, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1.0E-6) {
            sx = pen.rightX;
            sy = pen.rightY;
            sz = pen.rightZ;
        } else {
            sx /= sl;
            sy /= sl;
            sz /= sl;
        }
        for (int i = 0; i <= n; i++) {
            double g = (double) i / n;
            float j = i == 0 || i == n ? 0 : (hash(seed * 31 + i * 7) - 0.5F) * 2 * amp;
            zx[i] = ax + (bx - ax) * g + sx * j;
            zy[i] = ay + (by - ay) * g + sy * j;
            zz[i] = az + (bz - az) * g + sz * j;
        }
    }

    private void ribbonZig(int n, float w) {
        rib.clear();
        for (int i = 0; i <= n; i++) {
            rib.add(zx[i], zy[i], zz[i], w, 1);
        }
    }

    // ---------- 手腕 ----------

    private void wrists(St s, boolean fp) {
        BodyPoints K = m.pts;
        pen.cat = EphemeraStaging.BASE;
        for (int si = 0; si < 2; si++) {
            int side = si == 0 ? -1 : 1;
            BodyPoints.Pt wp = side < 0 ? K.wristL : K.wristR, dn = side < 0 ? K.downL : K.downR;
            FxKit.Spring sp = side < 0 ? s.wl : s.wr;
            // 阻尼比 0.8 时 dt = 0.04 下 w0 最多到 24 左右还稳
            sp.step(wp.x, wp.y, wp.z, m, 22, 0.8F);
            double hx = sp.x, hy = sp.y, hz = sp.z;
            double axX = dn.x, axY = dn.y, axZ = dn.z;
            double ox = m.rightX * side, oy = 0, oz = m.rightZ * side, dp = ox * axX + oy * axY + oz * axZ;
            ox -= axX * dp;
            oy -= axY * dp;
            oz -= axZ * dp;
            double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (ol < 1.0E-6) {
                ox = m.fwdX;
                oy = 0;
                oz = m.fwdZ;
            } else {
                ox /= ol;
                oy /= ol;
                oz /= ol;
            }
            double e2x = axY * oz - axZ * oy, e2y = axZ * ox - axX * oz, e2z = axX * oy - axY * ox, el2 = Math.sqrt(e2x * e2x + e2y * e2y + e2z * e2z);
            e2x /= el2;
            e2y /= el2;
            e2z /= el2;
            float swK = side == 1 ? swE : 0, Aw = A * (1 + 0.6F * swK);
            float r = W * 0.42F * (1 + 0.35F * swK * Bk + 0.12F * hurtE + 0.04F * breath * amp), hb = 0.065F * sc * (1 + 0.4F * swK);
            float tOff = r + 0.13F * sc * (1 + 0.9F * swK * Bk), tDown = -0.02F * sc + 0.012F * sc * (float) Math.sin(t * 1.7F + side);
            double tcx = hx + ox * tOff + axX * tDown, tcy = hy + oy * tOff + axY * tDown, tcz = hz + oz * tOff + axZ * tDown;
            s.tr[si].push(tcx, tcy, tcz, m.t);
            if (fp) {
                continue;
            }
            pen.s(SOFT).rgb(C).a((0.24F + 0.3F * swK) * Aw * gK).billboard(hx, hy, hz, r * 3.6F, r * 3.6F);
            if (!lite) {
                pen.s(SOFT).rgb(C).a(0.2F * Aw * gK).beam(hx - axX * 0.2F * sc, hy - axY * 0.2F * sc, hz - axZ * 0.2F * sc,
                        hx + axX * 0.1F * sc, hy + axY * 0.1F * sc, hz + axZ * 0.1F * sc, r * 1.5F);
            }
            // 外侧半圈铭文带
            int NA = crowded ? 2 : lite ? 4 : 7;
            float a0 = -1.9F, a1 = 1.9F, segL = r * (a1 - a0) / NA, uw = Math.min(0.5F, 0.5F * segL / (2 * hb * 5)), wr = T * 0.05F * side;
            float glitch = hurtE > 0 ? (hash(qh * 5 + side) - 0.5F) * 0.6F * hurtE : 0;
            for (int k = 0; k < NA; k++) {
                float b0 = a0 + (a1 - a0) * k / NA + glitch, b1 = a0 + (a1 - a0) * (k + 1) / NA + glitch;
                double c0 = Math.cos(b0) * r, s0 = Math.sin(b0) * r, c1 = Math.cos(b1) * r, s1 = Math.sin(b1) * r;
                double p0x = hx + ox * c0 + e2x * s0, p0y = hy + oy * c0 + e2y * s0, p0z = hz + oz * c0 + e2z * s0;
                double p1x = hx + ox * c1 + e2x * s1, p1y = hy + oy * c1 + e2y * s1, p1z = hz + oz * c1 + e2z * s1;
                if (!lite && !tight) {
                    float h2 = hb * 1.3F;
                    pen.s(BAR).alphaBlend().rgb(deep).a(0.45F).quad(p0x - axX * h2, p0y - axY * h2, p0z - axZ * h2, p1x - axX * h2, p1y - axY * h2, p1z - axZ * h2,
                            p1x + axX * h2, p1y + axY * h2, p1z + axZ * h2, p0x + axX * h2, p0y + axY * h2, p0z + axZ * h2);
                }
                float u0 = ((wr + k * uw) % 0.5F + 0.5F) % 0.5F;
                pen.s(GLYPH).rgb(k == 3 ? hot : C).a(0.95F * Aw * (hash(qf * 5 + k + side * 9) < 0.06F ? 0.2F : 1))
                        .uv4(u0, 0, u0 + uw, 0, u0 + uw, 1, u0, 1)
                        .quad(p0x - axX * hb, p0y - axY * hb, p0z - axZ * hb, p1x - axX * hb, p1y - axY * hb, p1z - axZ * hb,
                                p1x + axX * hb, p1y + axY * hb, p1z + axZ * hb, p0x + axX * hb, p0y + axY * hb, p0z + axZ * hb);
            }
            if (!lite) {
                for (int sg = tight2 ? 1 : -1; sg <= 1; sg += 2) {
                    rib.clear();
                    float h = sg * hb * 1.35F;
                    for (int k = 0; k <= NA; k++) {
                        double b = a0 + (a1 - a0) * k / NA + glitch, c = Math.cos(b) * r, sn = Math.sin(b) * r;
                        rib.add(hx + ox * c + e2x * sn + axX * h, hy + oy * c + e2y * sn + axY * h, hz + oz * c + e2z * sn + axZ * h, 0.012F * sc, 1);
                    }
                    pen.rgb(hot).a(0.8F * Aw * fk).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                }
            }
            // 小谕牌：正对镜头，倒梯形框 + 两行字
            float ts = sc * (1 + 0.5F * swK), tw = 0.14F * ts, tb = 0.065F * ts, th2 = 0.085F * ts;
            tagPt(tcx, tcy, tcz, -tw, th2, 0);
            tagPt(tcx, tcy, tcz, tw, th2, 1);
            tagPt(tcx, tcy, tcz, tb, -th2, 2);
            tagPt(tcx, tcy, tcz, -tb, -th2, 3);
            float lk = -th2 * 0.8F;
            pen.rgb(hot).a(0.5F * Aw).beam(hx + ox * r * 1.05F, hy + oy * r * 1.05F, hz + oz * r * 1.05F,
                    tcx + pen.upX * lk, tcy + pen.upY * lk, tcz + pen.upZ * lk, 0.006F * sc);
            if (!lite) {
                pen.alphaBlend().rgb(deep).a(0.4F);
                tagQuad();
                pen.s(SOFT).rgb(C).a(0.35F * Aw * gK);
                tagQuad();
            }
            for (int k = 0; k < (sparse ? 1 : 2); k++) {
                float y0 = th2 * (0.55F - k * 0.75F), y1 = y0 - th2 * 0.42F, xr = (tb + (tw - tb) * (y0 + th2) / (2 * th2)) * 0.78F;
                float uo = ((T * 0.06F * side + k * 0.21F) % 0.5F + 0.5F) % 0.5F, uw2 = Math.min(0.5F, 0.5F * 2 * xr / (th2 * 0.42F * 5));
                double rx = pen.rightX, ry = pen.rightY, rz = pen.rightZ, ux = pen.upX, uy = pen.upY, uz = pen.upZ;
                pen.s(GLYPH).rgb(k != 0 ? C : hot).a(0.95F * Aw).uv4(uo, 0, uo + uw2, 0, uo + uw2, 1, uo, 1)
                        .quad(tcx - rx * xr + ux * y0, tcy - ry * xr + uy * y0, tcz - rz * xr + uz * y0,
                                tcx + rx * xr + ux * y0, tcy + ry * xr + uy * y0, tcz + rz * xr + uz * y0,
                                tcx + rx * xr + ux * y1, tcy + ry * xr + uy * y1, tcz + rz * xr + uz * y1,
                                tcx - rx * xr + ux * y1, tcy - ry * xr + uy * y1, tcz - rz * xr + uz * y1);
            }
            rib.clear();
            for (int k = 0; k <= 4; k++) {
                int c = k & 3;
                rib.add(ptX[c], ptY[c], ptZ[c], 0.01F * sc, 1);
            }
            pen.rgb(hot).a(0.9F * Aw * fk).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            // 精简档省掉小牌角上的元素点缀
            int nAcc = lite ? 0 : tight2 ? 0 : heavy ? 1 : 2;
            if (nAcc > 0) {
                double dx = ox * 0.7, dy = oy * 0.7 + 0.7, dz = oz * 0.7, dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                for (int i = 0; i < nAcc; i++) {
                    int c = i == 0 ? (side > 0 ? 1 : 0) : (side > 0 ? 0 : 1);
                    curFx.accent(ptX[c], ptY[c], ptZ[c], (float) (dx / dl), (float) (dy / dl), (float) (dz / dl), 0.11F * sc * (1 + 0.8F * swK), 0.04F * sc,
                            80 + i + (side + 1) * 5, 0.8F * Aw);
                }
            }
            s.sites.add(tcx, tcy, tcz, (float) ox, (float) oy, (float) oz);
        }
    }

    /** 小牌的第 i 个角：镜头右 x、上 y，写进 ptX/ptY/ptZ[i] */
    private void tagPt(double cx, double cy, double cz, float x, float y, int i) {
        ptX[i] = cx + pen.rightX * x + pen.upX * y;
        ptY[i] = cy + pen.rightY * x + pen.upY * y;
        ptZ[i] = cz + pen.rightZ * x + pen.upZ * y;
    }

    private void tagQuad() {
        pen.quad(ptX[0], ptY[0], ptZ[0], ptX[1], ptY[1], ptZ[1], ptX[2], ptY[2], ptZ[2], ptX[3], ptY[3], ptZ[3]);
    }

    // ---------- 残影、挥砍、受击 ----------

    private void motion(St s, Fr f) {
        // 这几样不画也不缺轮廓，精简档看剩下的余量再画
        pen.cat = EphemeraStaging.TRAIL;
        if (run > 0.05F || air) {
            int n = lite ? 1 : 4;
            float kk = Math.max(run, air ? 0.6F : 0);
            for (int j = 1; j <= n; j++) {
                Fr h = findHist(s, m.t - j * 0.07);
                if (h == null || left() < (lite ? 8 : 24)) {
                    break;
                }
                float k = kk * (1 - (float) j / (n + 1));
                if (!lite && !tight) {
                    poly(rib, h, SEG_GHOST, 1, 0, 0, eW * 5, 1);
                    glowLine(rib, 0.4F * k * A, C);
                    // 残影里也带一层牌面的光，不只是几根线框
                    pen.s(SOFT).rgb(C).a(0.6F * k * A * gK).uv(0, 0, 0.5F, 1);
                    quadP(h, -h.wt / 2, h.hh, 0, h.hh, 0, -h.hh, -h.wb / 2, -h.hh, 0);
                    pen.s(SOFT).rgb(C).a(0.6F * k * A * gK).uv(0.5F, 0, 1, 1);
                    quadP(h, 0, h.hh, h.wt / 2, h.hh, h.wb / 2, -h.hh, 0, -h.hh, 0);
                }
                poly(rib, h, SEG_GHOST, 1, 0, 0, eW * 0.7F, 1);
                coreLine(rib, 0.4F * k * A, C);
                if (!lite && j == 1) {
                    for (int sx = -1; sx <= 1; sx += 2) {
                        rib.clear();
                        P(h, sx * h.wt / 2 * 0.9F, h.hh * 0.9F, 0);
                        rib.add(qx, qy, qz, eW * 2, 1);
                        P(h, -sx * h.wb / 2 * 0.9F, -h.hh * 0.9F, 0);
                        rib.add(qx, qy, qz, eW * 2, 1);
                        coreLine(rib, 0.35F * k * A, C);
                    }
                }
            }
        }
        // 挥砍：牌框整圈往外推一道
        float sF = swS / (0.55F * m.burstTime);
        if (sF >= 0 && sF < 1 && left() > (lite ? 6 : 30)) {
            float k = 1 + 0.55F * sF * Bk, oy = f.hh * 0.1F * sF;
            int[] segs = lite ? SEG_GHOST : OUT_LITE;
            if (!lite) {
                poly(rib, f, segs, k, 0, oy, eW * 7, 1);
                glowLine(rib, 0.6F * (1 - sF), C);
            }
            poly(rib, f, segs, k, 0, oy, eW * 1.6F * (1 - 0.5F * sF), 1);
            coreLine(rib, 0.9F * (1 - sF), hot);
        }
        // 受击：牌框裂出两道偏移的重影
        if (hurtE > 0.05F) {
            for (int j = 0; j < (lite ? 1 : 2); j++) {
                if (left() < 8) {
                    break;
                }
                float ox = (hash(qh * 11 + j * 5) - 0.5F) * 0.35F * W * hurtE + (j != 0 ? 1 : -1) * 0.05F * W;
                float oy = (hash(qh * 13 + j) - 0.5F) * 0.06F * H * hurtE;
                poly(rib, f, SEG_PULSE, 1, ox, oy, eW * 1.3F, 1);
                coreLine(rib, 0.75F * hurtE, j != 0 ? 0xFF4D4D : pale);
            }
        }
    }

    /** 手的拖光：跑、挥砍时甩出一道铭文光弧；精简档只留主手挥砍那一下 */
    private void handTrails(St s, boolean fp) {
        if (fp) {
            return;
        }
        pen.cat = EphemeraStaging.TRAIL;
        for (int si = 0; si < 2; si++) {
            int side = si == 0 ? -1 : 1;
            float swK = side == 1 ? swE : 0, Aw = A * (1 + 0.6F * swK);
            float tk = lite ? swK * 1.4F : Math.max(swK * 1.4F, Math.max(run * 0.45F, air ? 0.35F : 0));
            if (tk <= 0.02F || left() < (lite ? 6 : 18)) {
                continue;
            }
            FxTrail tr = s.tr[si];
            final float ww = 0.07F * sc * (1 + 1.4F * swK), kA = tk * Aw;
            if (!lite) {
                tr.sample(8, sample);
                pen.s(BAR).rgb(C);
                FxTrail.draw(pen, sample, rib, (x, i) -> ww * 2.2F * (1 - x), (x, i) -> 0.4F * kA * (1 - x) * gK, null, m.trailW, m.trailA,
                        EphemeraPen.FACE_CAM, 0, 1);
            }
            tr.sample(lite ? 4 : 8, sample);
            pen.s(GLYPH).rgb(hot);
            FxTrail.draw(pen, sample, rib, (x, i) -> ww * (1 - x * 0.5F), (x, i) -> 0.9F * kA * (1 - x), null, m.trailW, m.trailA,
                    EphemeraPen.FACE_CAM, 0, 1);
        }
    }

    // ---------- 铭文碎屑 ----------

    private void chips(St s, Fr f, boolean fp, float dt) {
        FxParticles P = s.chips;
        P.thin = m.emit;
        if (!fp) {
            // 站着偶尔掉一两片，跑起来往后洒，挥砍、受击、落地时迸一把
            s.acc += dt * (1.2F + 14 * run + 22 * swE) * (lite ? 0.4F : 1);
            while (s.acc >= 1) {
                s.acc -= 1;
                spawnChip(P, f, false);
            }
            if (m.justHurt) {
                for (int i = 0; i < (lite ? 5 : 14); i++) {
                    spawnChip(P, f, true);
                }
            }
            if (m.justLand) {
                for (int i = 0; i < (lite ? 3 : 8); i++) {
                    spawnChip(P, f, true);
                }
            }
        } else {
            s.acc = 0;
        }
        P.step(dt, null);
        pen.cat = EphemeraStaging.PARTICLE;
        // 挥砍、受击那一下各样东西都在冒，碎屑只画到余量用完为止
        float sm = m.size;
        for (int i = 0; i < P.count(); i++) {
            if (left() <= 2) {
                break;
            }
            float k = P.age[i] / P.life[i];
            float sz = (P.size0[i] + (P.size1[i] - P.size0[i]) * k) * sm;
            float fade = Math.min(1, k / Math.max(P.fadeIn[i], 1.0E-4F)) * Math.min(1, (1 - k) / Math.max(P.fadeOut[i], 1.0E-4F));
            float al = P.a[i] * fade;
            if (al <= 0.002F || sz <= 0) {
                continue;
            }
            pen.s(P.sprite[i]).rgb(P.rgb[i]).a(al).billboard(P.x[i], P.y[i], P.z[i], sz, sz, P.rot[i]);
        }
    }

    private void spawnChip(FxParticles P, Fr f, boolean burst) {
        float y = (rnd() * 2 - 1) * f.hh * 0.9F, x = (rnd() * 2 - 1) * f.half(y) * 0.95F;
        P(f, x, y, 0);
        double ovx = f.axX * x + f.ayX * y * 0.6, ovy = f.axY * x + f.ayY * y * 0.6, ovz = f.axZ * x + f.ayZ * y * 0.6;
        double ol = Math.sqrt(ovx * ovx + ovy * ovy + ovz * ovz) + 1.0E-6;
        float vx, vy, vz;
        if (burst) {
            float o = rnd(0.8F, 1.8F) * sc, b = rnd(0.2F, 0.8F) * sc;
            vx = (float) (ovx / ol * o - f.fzX * b);
            vy = (float) (ovy / ol * o - f.fzY * b);
            vz = (float) (ovz / ol * o - f.fzZ * b);
        } else {
            vx = (float) (-f.fzX * 0.25F * sc) - m.fwdX * 1.3F * run * sc;
            vy = (float) (-f.fzY * 0.25F * sc) + 0.12F * sc;
            vz = (float) (-f.fzZ * 0.25F * sc) - m.fwdZ * 1.3F * run * sc;
        }
        P.spec().pos(qx, qy, qz).vel(vx, vy, vz).life(rnd(0.5F, 1.0F)).size(rnd(0.035F, 0.06F) * sc, 0.015F * sc).sprite(EphemeraSprites.WHITE)
                .rgb(rnd() < 0.4F ? hot : C).a(0.95F).drag(burst ? 2.5F : 1.2F).rot(rnd() < 0.5F ? 0 : (float) Math.PI / 4).fade(0.1F, 0.6F).spawn();
    }

    // ---------- 牌面坐标 ----------

    /** 牌面坐标 (x 横、y 竖、z 往牌后) 到世界坐标，写进 qx/qy/qz；x 按半径 rc 绕头弯 */
    private void P(Fr f, double x, double y, double z) {
        double a = x / f.rc, sx = f.rc * Math.sin(a), dz = f.rc * (1 - Math.cos(a)) - z;
        qx = f.cx + f.axX * sx + f.fzX * dz + f.ayX * y;
        qy = f.cy + f.axY * sx + f.fzY * dz + f.ayY * y;
        qz = f.cz + f.axZ * sx + f.fzZ * dz + f.ayZ * y;
    }

    /** 牌面上四个点的面片；笔刷先设好 */
    private void quadP(Fr f, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, float z) {
        P(f, x0, y0, z);
        q12[0] = qx;
        q12[1] = qy;
        q12[2] = qz;
        P(f, x1, y1, z);
        q12[3] = qx;
        q12[4] = qy;
        q12[5] = qz;
        P(f, x2, y2, z);
        q12[6] = qx;
        q12[7] = qy;
        q12[8] = qz;
        P(f, x3, y3, z);
        q12[9] = qx;
        q12[10] = qy;
        q12[11] = qz;
        pen.quad(q12[0], q12[1], q12[2], q12[3], q12[4], q12[5], q12[6], q12[7], q12[8], q12[9], q12[10], q12[11]);
    }

    private void band(Fr f, float x0, float y0, float x1, float y1, float w, int n, int spr, int rgb, float a) {
        band(f, x0, y0, x1, y1, w, n, spr, rgb, a, Float.NaN, 0, 0, false);
    }

    /**
     * 牌面里的一段带子：(x0, y0) → (x1, y1)，宽 w，n 段（0 = 按长度自动）。scroll 不是 NaN 时贴铭文、沿长度连续滚；
     * off 沿法向平移；fade 时往终点逐渐淡掉。全是加法层。
     */
    private void band(Fr f, float x0, float y0, float x1, float y1, float w, int n, int spr, int rgb, float a, float scroll, float z, float off,
                      boolean fade) {
        float dx = x1 - x0, dy = y1 - y0, L = (float) Math.hypot(dx, dy);
        if (L < 1.0E-6F) {
            L = 1.0E-6F;
        }
        float nx = -dy / L, ny = dx / L, ox = nx * off, oy = ny * off, hw = w / 2;
        float lp = w * 5;
        if (n <= 0) {
            n = Math.max(1, (int) Math.ceil(L / lp));
        }
        float uw = Math.min(0.5F, 0.5F * (L / n) / lp);
        for (int k = 0; k < n; k++) {
            float xa = x0 + dx * k / n + ox, ya = y0 + dy * k / n + oy, xb = x0 + dx * (k + 1) / n + ox, yb = y0 + dy * (k + 1) / n + oy;
            pen.s(spr).rgb(rgb).a(a);
            if (fade) {
                float g0 = 1 - 0.6F * ((float) k / n) * ((float) k / n), g1 = 1 - 0.6F * ((float) (k + 1) / n) * ((float) (k + 1) / n);
                pen.va(g0, g1, g1, g0);
            }
            if (!Float.isNaN(scroll)) {
                float u0 = ((scroll + k * uw) % 0.5F + 0.5F) % 0.5F;
                pen.uv4(u0, 0, u0 + uw, 0, u0 + uw, 1, u0, 1);
            }
            quadP(f, xa + nx * hw, ya + ny * hw, xb + nx * hw, yb + ny * hw, xb - nx * hw, yb - ny * hw, xa - nx * hw, ya - ny * hw, z);
        }
    }

    /** 牌框一圈（左上 → 右上 → 右下 → 左下 → 左上），每条边分 segs 段；坐标先乘 k 再平移 (ox, oy) */
    private void poly(EphemeraPen.Ribbon r, Fr f, int[] segs, float k, float ox, float oy, float w, float a) {
        r.clear();
        for (int i = 0; i < 4; i++) {
            float ax = cornerX(f, i), ay = cornerY(f, i), bx = cornerX(f, i + 1), by = cornerY(f, i + 1);
            int n = segs[i];
            for (int j = i == 0 ? 0 : 1; j <= n; j++) {
                float x = ax + (bx - ax) * j / n, y = ay + (by - ay) * j / n;
                P(f, x * k + ox, y * k + oy, 0);
                r.add(qx, qy, qz, w, a);
            }
        }
    }

    private static float cornerX(Fr f, int i) {
        return switch (i & 3) {
            case 0 -> -f.wt / 2;
            case 1 -> f.wt / 2;
            case 2 -> f.wb / 2;
            default -> -f.wb / 2;
        };
    }

    private static float cornerY(Fr f, int i) {
        return (i & 3) < 2 ? f.hh : -f.hh;
    }

    private void glowLine(EphemeraPen.Ribbon r, float a, int rgb) {
        pen.s(BAR).rgb(rgb).a(a * gK).ribbon(r, EphemeraPen.FACE_CAM, 0, 1);
    }

    private void coreLine(EphemeraPen.Ribbon r, float a, int rgb) {
        pen.rgb(rgb).a(a).ribbon(r, EphemeraPen.FACE_CAM, 0, 1);
    }

    // ---------- 残影用的历史 ----------

    private static void pushHist(St s, Fr f) {
        if (s.hN > 0 && f.t - s.hist[s.hHead].t < 0.004) {
            s.hist[s.hHead].set(f);
            return;
        }
        s.hHead = (s.hHead + 1) % HIST;
        s.hist[s.hHead].set(f);
        s.hN = Math.min(s.hN + 1, HIST);
        while (s.hN > 2 && f.t - s.hist[(s.hHead - s.hN + 1 + HIST) % HIST].t > 0.3) {
            s.hN--;
        }
    }

    private static Fr findHist(St s, double before) {
        for (int k = 0; k < s.hN; k++) {
            Fr h = s.hist[(s.hHead - k + HIST) % HIST];
            if (h.t <= before) {
                return h;
            }
        }
        return null;
    }

    /** 固定种子的伪随机 0~1：同一个 i 每帧都一样 */
    private static float prn(double i) {
        double v = Math.sin(i * 91.3 + 17.1) * 43758.5;
        return (float) (v - Math.floor(v));
    }
}
