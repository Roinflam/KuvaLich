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
 * 缠藤：四根粗大的暗色藤蔓从脚边钻出、斜着盘上身，藤梢伸出身外再往外卷；藤身漆黑，只有一道内光脉络，
 * 光团从根往梢流，背后衬一圈往两侧放射的淡光。站着缓缓绕动，跑起来藤梢往后飘、拖光尾，起跳收拢，
 * 落地根须往外铺，挥砍时一根藤顺着挥砍方向抽出去，受击先勒紧、冒暗刺，再松开。
 */
public final class VineStyle extends ProtoStyle<VineStyle.St> {

    static final int BARK = EphemeraSprites.file("vine/bark");
    static final int RIM = EphemeraSprites.file("vine/rim");
    static final int VEIN = EphemeraSprites.file("vine/vein");

    private static final int MAX_V = 4, MAX_P = 17;
    // 根部那几节 w0 接近 30，dt = 0.04 下半隐式积分不稳，按每步不超过 1/90 秒拆开
    private static final float SUB_HZ = 90;
    private static final int FIRE_VEIN = 0xFFD973;

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        FxParticles pt;
        final FxTrail[] tips = new FxTrail[MAX_V];
        final FxKit.Spring[][] seg = new FxKit.Spring[MAX_V][MAX_P];
        final FxKit.Spring[][] sprout = new FxKit.Spring[MAX_V][5];
        final int[][] dripCyc = new int[MAX_V][2];
        boolean ready, lite, heavy;
        float acc, lashAcc;

        St() {
            for (int i = 0; i < MAX_V; i++) {
                for (int j = 0; j < MAX_P; j++) {
                    seg[i][j] = new FxKit.Spring();
                }
                for (int j = 0; j < 5; j++) {
                    sprout[i][j] = new FxKit.Spring();
                }
            }
        }
    }

    private final double[][] px = new double[MAX_V][MAX_P], py = new double[MAX_V][MAX_P], pz = new double[MAX_V][MAX_P];
    private final float[] thk = new float[MAX_V], lashV = new float[MAX_V], hsV = new float[MAX_V], handV = new float[MAX_V];
    private final float[] rootA = new float[MAX_V], rootR = new float[MAX_V];
    private final float[] pul0 = new float[MAX_V], pul1 = new float[MAX_V], flareV = new float[MAX_V];
    private final double[] rx = new double[MAX_P], ry = new double[MAX_P], rz = new double[MAX_P];
    private final double[] spx = new double[5], spy = new double[5], spz = new double[5];
    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(MAX_P + 2);
    private final EphemeraPen.Ribbon zr = new EphemeraPen.Ribbon(10);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final float[] dir = new float[3];
    private final double[] side = new double[3];
    private final double[] zx = new double[9], zy = new double[9], zz = new double[9];

    private EphemeraStaging out;
    private int cap, from;
    private int nv, NR, NH, NT, NS;
    private float fr, fh, hH;
    private double hx, hy, hz;
    private float ha;

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

    private void init(St s, FxMotion m, boolean heavy) {
        s.ready = true;
        s.lite = m.lite;
        s.heavy = heavy;
        s.pt = new FxParticles(m.lite ? 12 : heavy ? 18 : 28);
        s.acc = 0;
        s.lashAcc = 0;
        for (int i = 0; i < MAX_V; i++) {
            s.tips[i] = new FxTrail(0.3F, 1.3F * m.sc, 0.04F * m.sc, 3 * m.sc);
            for (FxKit.Spring sp : s.seg[i]) {
                sp.reset();
            }
            for (FxKit.Spring sp : s.sprout[i]) {
                sp.reset();
            }
            s.dripCyc[i][0] = s.dripCyc[i][1] = Integer.MIN_VALUE;
        }
    }

    /** 完整档按整个幻纹（扣掉留给粒子的）算，精简档只算爆发之后画的 */
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
        int el = m.el;
        // 电和磁的共用元素层（贴地铁屑、爆发）本身就贵，自己这边让出一截
        boolean heavy = el == MAGNETIC || el == ELECTRIC;
        if (!s.ready || s.lite != lite || s.heavy != heavy) {
            init(s, m, heavy);
        }
        out = d.out;
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc;
        hH = H;
        float act = m.act, amp = m.amp, Bk = m.burst;
        boolean fp = d.firstPersonSelf;
        float fwdX = m.fwdX, fwdZ = m.fwdZ, rightX = m.rightX, rightZ = m.rightZ;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        float T = m.clock(), bt = m.beat(0);
        float run = clamp(m.speed / 5.6F, 0, 1), air = m.onGround ? 0 : 1;
        float sinceHurt = m.since(m.tHurt);
        float swE = m.env(m.since(m.tSwing), 0.03F, 0.1F, 0.5F), hurtE = m.env(sinceHurt, 0.02F, 0.12F, 0.3F);
        // 松开接在勒紧后面；收敛档事件整体压短，起点跟着一起压
        float loosen = m.env(sinceHurt - 0.32F * m.burstTime, 0.08F, 0.05F, 0.45F);
        float jumpE = m.env(m.since(m.tJump), 0.03F, 0.12F, 0.4F), landE = m.env(m.since(m.tLand), 0.01F, 0.08F, 0.55F);
        float breath = 0.5F + 0.5F * (float) Math.sin(t * 1.1F);
        // 近白的颜色（冲击默认色）加法层叠起来一片白，按亮度往下压
        float lum = (0.3F * ((C >> 16) & 255) + 0.59F * ((C >> 8) & 255) + 0.11F * (C & 255)) / 255;
        float lumK = lum > 0.55F ? 1 - 0.55F * (lum - 0.55F) / 0.45F : 1, rimK = 0.5F + 0.5F * lumK;
        float A = (0.62F + 0.38F * act) * m.blink(5);
        if (hurtE > 0.05F) {
            A *= 0.75F + 0.25F * hash((int) Math.floor(m.t * 22));
        }
        int n = lite ? 3 : 4;
        NR = lite ? 1 : 2;
        NH = lite ? 6 : 9;
        NT = lite ? 4 : 5;
        NS = NR + NH + NT;
        nv = n;
        fr = (float) NR / NS;
        fh = (float) (NR + NH) / NS;
        int dark = darkOf(deep);
        int swN = m.swingCount;
        float sg = (swN & 1) != 0 ? 1 : -1;
        float swX = fwdX + rightX * sg * 0.7F, swY = 0.3F, swZ = fwdZ + rightZ * sg * 0.7F;
        float swL = (float) Math.sqrt(swX * swX + swY * swY + swZ * swZ);
        swX /= swL;
        swY /= swL;
        swZ /= swL;
        int lashI = Math.floorMod(swN, n);
        // 受击先勒紧再松开（带一点回弹），起跳收拢，落地往外一撑，冲击按拍鼓一下
        float rad = (1 - 0.3F * hurtE + 0.14F * loosen) * (1 - 0.14F * jumpE * (1 - landE)) * (1 + 0.16F * landE + 0.07F * bt);
        float drift = T * 0.09F;
        double gx = m.x, gy = m.groundY, gz = m.z;
        float gA = 1 - 0.7F * air;

        // 爆发和贴地纹样先画：电、磁挥砍那一下就上百个面片，后面的副件按剩下的让
        pen.cat = EphemeraStaging.PARTICLE;
        fx.events(gx, gy, gz, W * 1.6F);
        fx.bursts();
        fx.ground(gx, gy, gz, W, 0.5F * A);
        if (lite) {
            cap = EphemeraTier.CAP_REDUCED;
            from = out.pending();
        } else {
            // 粒子最后画、没法半截停，按池里现有的数先留出来
            cap = EphemeraTier.CAP_FULL - s.pt.count() - fx.particles() - (el == POISON ? 20 : 0) - 12;
            from = 0;
        }

        // ---- 地上的根盘：暗影 + 一圈光 ----
        pen.cat = EphemeraStaging.BASE;
        float sh = W * 2.6F * (1 + 0.3F * landE), rg = W * 2.2F * (1 + 0.5F * landE);
        pen.s(SOFT).alphaBlend().rgb(0).a(0.45F * gA).flat(gx, gy + 0.012, gz, sh, sh);
        pen.s(SOFTRING).rgb(C).a((0.25F + 0.4F * landE) * gA * lumK * A).flat(gx, gy + 0.016, gz, rg, rg, drift);

        // ---- 藤骨架 ----
        int nsub = Math.max(1, (int) Math.ceil(dt * SUB_HZ));
        float sdt = dt / nsub;
        sites.clear();
        for (int i = 0; i < n; i++) {
            float hs = hash(i * 11 + 3), hand = (i & 1) != 0 ? 1 : -1;
            hsV[i] = hs;
            handV[i] = hand;
            float a0 = (float) i / n * TAU + 0.5F + 0.35F * hs + drift;
            float sweep = (float) Math.PI * (0.78F + 0.2F * hs) + 0.15F * (float) Math.sin(T * 0.45F + i * 1.7F) * amp;
            float top = H * (0.6F + 0.2F * hash(i * 5 + 1));
            float th = H * (0.12F + 0.02F * hs) * (1 + 0.12F * hurtE);
            thk[i] = th;
            float lash = i == lashI ? swE * (0.75F + 0.25F * Bk) : 0;
            lashV[i] = lash;

            // 根：从脚边地上斜着钻出来，起跳时收进来
            float rR = W * (1.15F + 0.35F * landE) * (1 - 0.35F * air), rA = a0 - hand * 0.45F;
            rootR[i] = rR;
            rootA[i] = rA;
            double gpx = m.x + (rightX * Math.cos(rA) + fwdX * Math.sin(rA)) * rR, gpy = m.y + 0.01;
            double gpz = m.z + (rightZ * Math.cos(rA) + fwdZ * Math.sin(rA)) * rR;
            hel(0, a0, hand, sweep, top, i, T, amp, breath, rad, m);
            int k = 0;
            for (int j = 0; j < NR; j++) {
                float f = (float) j / NR;
                rx[k] = gpx + (hx - gpx) * f;
                ry[k] = gpy + (hy - gpy) * f + H * 0.03F * Math.sin(Math.PI * f);
                rz[k] = gpz + (hz - gpz) * f;
                k++;
            }
            for (int j = 0; j <= NH; j++) {
                hel((float) j / NH, a0, hand, sweep, top, i, T, amp, breath, rad, m);
                rx[k] = hx;
                ry[k] = hy;
                rz[k] = hz;
                k++;
            }
            // 藤梢：顺着盘绕的方向伸出身外，再向外卷回来
            hel(1 - 1.0F / NH, a0, hand, sweep, top, i, T, amp, breath, rad, m);
            double qx = hx, qy = hy, qz = hz;
            hel(1, a0, hand, sweep, top, i, T, amp, breath, rad, m);
            double d0x = hx - qx, d0y = hy - qy, d0z = hz - qz, dl = Math.sqrt(d0x * d0x + d0y * d0y + d0z * d0z) + 1.0E-9;
            d0x /= dl;
            d0y /= dl;
            d0z /= dl;
            double Rx = rightX * Math.cos(ha) + fwdX * Math.sin(ha), Rz = rightZ * Math.cos(ha) + fwdZ * Math.sin(ha), Ry = 0;
            double dot = Rx * d0x + Ry * d0y + Rz * d0z;
            Rx -= d0x * dot;
            Ry -= d0y * dot;
            Rz -= d0z * dot;
            double Rl = Math.sqrt(Rx * Rx + Ry * Ry + Rz * Rz) + 1.0E-9;
            Rx /= Rl;
            Ry /= Rl;
            Rz /= Rl;
            float Lt = H * (0.52F + 0.1F * hs) * (1 + 0.6F * lash) * (1 + 0.12F * act + 0.15F * landE);
            float curl = (2.7F + 0.35F * breath * amp) * (1 - 0.7F * lash) * (1 - 0.3F * run) * (1 + 0.25F * hurtE);
            qx = hx;
            qy = hy;
            qz = hz;
            for (int j = 1; j <= NT; j++) {
                float g = (float) j / NT, tq = curl * (float) Math.pow(g, 1.25);
                double c = Math.cos(tq), sn = Math.sin(tq), step = Lt / NT * (1.3F - 0.65F * g);
                qx += (d0x * c + Rx * sn) * step;
                qy += (d0y * c + Ry * sn) * step;
                qz += (d0z * c + Rz * sn) * step;
                rx[k] = qx;
                ry[k] = qy;
                rz[k] = qz;
                k++;
            }

            // 越往梢越软：跑起来往后飘，急停往前甩；挥砍那一根整段沿挥砍方向抽出去
            for (int j = 0; j <= NS; j++) {
                float f = (float) j / NS, wf = Math.max(0, (f - 0.3F) / 0.7F);
                if (wf <= 0) {
                    px[i][j] = rx[j];
                    py[i][j] = ry[j];
                    pz[i][j] = rz[j];
                    continue;
                }
                float back = -run * W * 0.55F * wf * wf, ls = W * 2.3F * lash * (float) Math.pow(wf, 1.4), dn = -H * 0.05F * jumpE * wf;
                double tx = rx[j] + fwdX * back + swX * ls, ty = ry[j] + swY * ls + dn, tz = rz[j] + fwdZ * back + swZ * ls;
                FxKit.Spring sp = s.seg[i][j];
                float w0 = 30 - 21 * wf, z = 0.62F - 0.17F * wf;
                for (int q = 0; q < nsub; q++) {
                    sp.step(tx, ty, tz, sdt, w0, z, m.trackX, m.trackY, m.trackZ);
                }
                px[i][j] = sp.x;
                py[i][j] = sp.y;
                pz[i][j] = sp.z;
            }

            // 光脉里往上流的光团：两团错开；冲击按拍从根冲到梢
            float pv = ((T * 0.2F + hs) % 1 + 1) % 1;
            pul0[i] = el == IMPACT ? (float) ((m.t % 0.85) / 0.85) * 1.6F - 0.1F : pv * 1.25F - 0.12F;
            pul1[i] = ((pv + 0.5F) % 1) * 1.25F - 0.12F;
            flareV[i] = 1 + 0.25F * act + 1.2F * lash + 0.5F * swE + 0.6F * hurtE;

            s.tips[i].push(px[i][NS], py[i][NS], pz[i][NS], m.t);
            int mi = NR + Math.round(NH * 0.5F);
            double mdx = px[i][mi] - m.x, mdz = pz[i][mi] - m.z, ml = Math.sqrt(mdx * mdx + mdz * mdz) + 1.0E-9;
            sites.add(px[i][mi], py[i][mi], pz[i][mi], (float) (mdx / ml), 0, (float) (mdz / ml));
            tipDir(i);
            sites.add(px[i][NS], py[i][NS], pz[i][NS], dir[0], dir[1], dir[2]);
        }

        int vRgb = el == FIRE ? mix(C, FIRE_VEIN, 0.45F) : el == ICE ? pale : mix(C, WHITE, 0.2F);
        int qf = (int) Math.floor(m.t / 0.05), qr = (int) Math.floor(m.t / 0.08);

        // ---- 藤身：勾边光、暗色藤皮、内光脉络、梢上的光点；第一人称整根绕在眼前，不画 ----
        if (!fp) {
            for (int i = 0; i < n; i++) {
                float th = thk[i], lash = lashV[i], flare = flareV[i];
                // 勾边光是宽而软的一层，精简档隔一个点取，省下的给背光和元素
                rib.clear();
                int rs = lite ? 2 : 1;
                for (int j = 0; j <= NS; j += rs) {
                    rib.add(px[i][j], py[i][j], pz[i][j], wAt((float) j / NS, th) * 1.8F, 1);
                    if (j + rs > NS && j != NS) {
                        rib.add(px[i][NS], py[i][NS], pz[i][NS], wAt(1, th) * 1.8F, 1);
                    }
                }
                pen.s(RIM).rgb(C).a((0.34F + 0.1F * act + 0.45F * lash + 0.3F * hurtE) * rimK * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                rib.clear();
                for (int j = 0; j <= NS; j++) {
                    rib.add(px[i][j], py[i][j], pz[i][j], wAt((float) j / NS, th), 1);
                }
                pen.s(BARK).alphaBlend().rgb(dark).a(0.96F).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                rib.clear();
                for (int j = 0; j <= NS; j++) {
                    float f = (float) j / NS;
                    float g0 = (f - pul0[i]) / 0.07F, g1 = (f - pul1[i]) / 0.07F;
                    float pk = (float) (Math.exp(-g0 * g0) + Math.exp(-g1 * g1));
                    float a = (0.32F + 0.1F * breath + 0.95F * pk) * flare * Math.min(1, f * 5 + 0.3F);
                    if (el == ELECTRIC) {
                        a *= hash(qf * 13 + j * 3 + i * 17) > 0.25F ? 1.15F : 0.15F;
                    } else if (el == RADIATION) {
                        float h = hash(qr * 7 + j + i * 29);
                        a *= h < 0.15F ? 0.1F : h > 0.9F ? 1.8F : 1;
                    }
                    rib.add(px[i][j], py[i][j], pz[i][j], wAt(f, th) * (0.36F + 0.14F * pk), Math.min(1.6F, a),
                            mix(vRgb, WHITE, Math.min(0.6F, 0.45F * pk)));
                }
                pen.s(VEIN).a(0.85F * A * lumK + 0.15F * A).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                FxKit.glow(pen, px[i][NS], py[i][NS], pz[i][NS], th * (1.5F + 1.5F * lash), C, hot, (0.55F + 0.3F * act) * A * lumK);
            }
        }

        if (!lite) {
            for (int i = 0; i < n; i++) {
                float th = thk[i], hand = handV[i];
                if (!fp) {
                    sprout(s, m, i, th, T, amp, act, hurtE, landE, sdt, nsub);
                    float ws = th * 0.55F;
                    rib.clear();
                    for (int j = 0; j <= 4; j++) {
                        rib.add(spx[j], spy[j], spz[j], ws * (1 - j / 4.0F * 0.75F), 1);
                    }
                    pen.s(BARK).alphaBlend().rgb(dark).a(0.95F).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    if (!heavy) {
                        rib.clear();
                        for (int j = 0; j <= 4; j++) {
                            rib.add(spx[j], spy[j], spz[j], ws * (1 - j / 4.0F * 0.75F) * 0.38F, 0.4F + 0.5F * j / 4);
                        }
                        pen.s(VEIN).rgb(vRgb).a(0.8F * A * lumK * flareV[i]).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    }
                    pen.s(SOFTCORE).rgb(hot).a(0.7F * A * lumK).billboard(spx[4], spy[4], spz[4], th * 0.7F, th * 0.7F);
                }

                // 根须：贴地往外铺，落地时铺得更开
                float rl = W * (0.45F + 0.5F * landE + 0.08F * breath * amp) * gA;
                if (rl > 0.02F) {
                    float ra = rootA[i] + hand * 0.25F, rs = ra + (float) Math.PI / 2, rR = rootR[i];
                    double rdx = rightX * Math.cos(ra) + fwdX * Math.sin(ra), rdz = rightZ * Math.cos(ra) + fwdZ * Math.sin(ra);
                    double rsx = rightX * Math.cos(rs) + fwdX * Math.sin(rs), rsz = rightZ * Math.cos(rs) + fwdZ * Math.sin(rs);
                    rib.clear();
                    for (int j = 0; j <= 3; j++) {
                        float g = j / 3.0F, o = rl * 0.25F * (float) Math.sin(g * 2.5F) * hand;
                        rib.add(gx + rdx * (rR + rl * g) + rsx * o, gy + 0.02, gz + rdz * (rR + rl * g) + rsz * o, th * (1.1F - 0.85F * g), 1);
                    }
                    pen.s(BARK).alphaBlend().rgb(dark).a(0.9F * gA).ribbon(rib, EphemeraPen.FACE_FLAT, 0, 1);
                    for (int j = 0; j <= 3; j++) {
                        float g = j / 3.0F;
                        rib.w[j] = th * (0.6F - 0.45F * g);
                        rib.a[j] = 1 - 0.6F * g;
                    }
                    pen.s(VEIN).rgb(vRgb).a((0.6F + 0.6F * landE) * A * lumK * gA).ribbon(rib, EphemeraPen.FACE_FLAT, 0, 1);
                }
            }
        }

        if (!fp) {
            // ---- 元素招牌 ----
            signature(s, m, el, lite, t, C, hot, pale, A, lumK, bt, swE, hurtE, H, sc);

            int tipCost = accentCost(el, lite);
            for (int i = 0; i < n; i++) {
                float th = thk[i], lash = lashV[i];
                // 磁力的藤梢已经两两连了磁力弧，梢上不再各挂一圈，只留抽出去的那根
                if ((el != MAGNETIC || lash > 0.05F || lite) && left() >= tipCost) {
                    tipDir(i);
                    fx.accent(px[i][NS], py[i][NS], pz[i][NS], dir[0], dir[1], dir[2], th * (1.3F + 1.6F * lash + 0.4F * act), th * 0.6F,
                            10 + i, 0.85F * A);
                }
            }

            // 受击：藤身冒出一排暗刺
            if (hurtE > 0.05F) {
                int nk = lite || heavy ? 2 : 3;
                for (int i = 0; i < n; i++) {
                    float th = thk[i];
                    for (int k = 0; k < nk && left() >= 2; k++) {
                        int j = NR + 1 + Math.round((k + 0.5F) / nk * (NH - 1));
                        double bx = px[i][j], by = py[i][j], bz = pz[i][j];
                        double ox = bx - m.x, oz = bz - m.z, ol = Math.sqrt(ox * ox + oz * oz) + 1.0E-9;
                        ox /= ol;
                        oz /= ol;
                        double oy = 0.35F * (hash(i * 9 + k) - 0.3F);
                        ol = Math.sqrt(ox * ox + oy * oy + oz * oz) + 1.0E-9;
                        float L = th * (1.2F + 0.8F * hash(i * 3 + k * 5)) * hurtE, reach = wAt((float) j / NS, th) * 0.4F + L;
                        double tx = bx + ox / ol * reach, ty = by + oy / ol * reach, tz = bz + oz / ol * reach;
                        pen.alphaBlend().rgb(dark).a(0.95F);
                        spike(bx, by, bz, tx, ty, tz, th * 0.5F);
                        pen.rgb(hot).a(0.8F * hurtE * A * lumK);
                        spike(bx + (tx - bx) * 0.45, by + (ty - by) * 0.45, bz + (tz - bz) * 0.45, tx, ty, tz, th * 0.18F);
                    }
                }
            }

            // 光脉里流的光团
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < (lite ? 1 : 2) && left() >= 1; k++) {
                    float ph = k == 0 ? pul0[i] : pul1[i];
                    if (ph <= 0.02F || ph >= 0.99F) {
                        continue;
                    }
                    float x = ph * NS;
                    int j = Math.min(NS - 1, (int) Math.floor(x));
                    float u = x - j, sz = wAt(ph, thk[i]) * 1.5F * flareV[i];
                    pen.s(SOFTCORE).rgb(hot).a(0.75F * A * lumK).billboard(px[i][j] + (px[i][j + 1] - px[i][j]) * u,
                            py[i][j] + (py[i][j + 1] - py[i][j]) * u, pz[i][j] + (pz[i][j + 1] - pz[i][j]) * u, sz, sz);
                }
            }

            // ---- 背后放射的淡光：只往两侧和斜下放，正上方留空，不压头 ----
            int NRAY = lite ? 4 : heavy ? 6 : 10;
            if (left() >= NRAY + 1) {
                double bcx = m.pts.back.x - fwdX * W * 0.3F, bcy = m.pts.back.y - H * 0.04F, bcz = m.pts.back.z - fwdZ * W * 0.3F;
                pen.s(SOFT).rgb(C).a((0.1F + 0.08F * breath + 0.12F * swE) * lumK * A).billboard(bcx, bcy, bcz, H * 0.95F, H * 1.15F);
                float rayA = (0.2F + 0.28F * act + 0.3F * swE) * lumK * A;
                int rayC = mix(pale, WHITE, 0.35F);
                float fk = -0.35F - 0.9F * run;
                for (int k = 0; k < NRAY; k++) {
                    float hs = hash(k * 13 + 7), sd = (k & 1) != 0 ? 1 : -1, kk = (float) (k / 2) / Math.max(1, NRAY / 2 - 1);
                    float ang = sd * (0.75F + 1.75F * kk) + 0.08F * (float) Math.sin(T * 0.4F + k);
                    float sa = (float) Math.sin(ang), ca = (float) Math.cos(ang);
                    double dx = rightX * sa + fwdX * fk, dy = ca, dz = rightZ * sa + fwdZ * fk, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    dx /= l;
                    dy /= l;
                    dz /= l;
                    float L = H * (0.3F + 0.22F * hs) * (0.85F + 0.25F * (float) Math.sin(T * 0.7F + k * 2.1F) * amp + 0.35F * act + 0.5F * swE);
                    double ax = bcx + dx * W * 0.45F, ay = bcy + dy * W * 0.45F, az = bcz + dz * W * 0.45F;
                    pen.s(STREAK).rgb(rayC).a(rayA * (0.55F + 0.45F * hs)).beam(ax, ay, az, ax + dx * L, ay + dy * L, az + dz * L, H * (0.05F + 0.025F * hs));
                }
            }

            // ---- 藤梢拖尾：动起来才看得见 ----
            pen.cat = EphemeraStaging.TRAIL;
            int segs = lite ? 4 : 6;
            for (int i = 0; i < n; i++) {
                FxTrail tr = s.tips[i];
                float tk = smooth((float) (tr.length() - 0.12F * sc) / (0.45F * sc));
                if (tk <= 0.01F || left() < segs) {
                    continue;
                }
                tr.sample(segs, sample);
                if (sample.n < 2) {
                    continue;
                }
                float tw = thk[i] * (0.9F + 0.8F * lashV[i]);
                rib.clear();
                for (int j = 0; j < sample.n; j++) {
                    float q = sample.s[j];
                    rib.add(sample.x[j], sample.y[j], sample.z[j], tw * (1 - q) * m.trailW, 0.6F * tk * (1 - q) * A * lumK * m.trailA);
                }
                pen.s(SOFT).rgb(C).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            }
        }

        // ---- 氛围：沿藤冒出的光孢和暗絮，慢慢往上飘 ----
        pen.cat = EphemeraStaging.PARTICLE;
        FxParticles P = s.pt;
        P.thin = Math.min(1, m.emit);
        s.acc += dt * (3 + 10 * run + 30 * swE + 25 * hurtE + 20 * landE) * (lite ? 0.4F : 1);
        while (s.acc >= 1) {
            s.acc -= 1;
            int i = Math.min(n - 1, (int) (rnd() * n));
            int j = NR + Math.min(NH + NT - 2, (int) (rnd() * (NH + NT - 1)));
            float th = thk[i];
            EphemeraFx.randDir(dir);
            if (rnd() < 0.4F) {
                P.spec().pos(px[i][j], py[i][j], pz[i][j])
                        .vel(dir[0] * 0.15F * sc, dir[1] * 0.15F * sc + 0.25F * sc, dir[2] * 0.15F * sc)
                        .life(rnd(0.9F, 1.6F)).size(th * rnd(1.2F, 2), th * 2.6F).sprite(SOFT).alphaBlend().rgb(dark).a(0.5F).drag(1.5F).spawn();
            } else {
                P.spec().pos(px[i][j], py[i][j], pz[i][j])
                        .vel(dir[0] * 0.3F * sc + m.velX * 0.15F, dir[1] * 0.3F * sc + 0.35F * sc + m.velY * 0.15F, dir[2] * 0.3F * sc + m.velZ * 0.15F)
                        .life(rnd(0.7F, 1.3F)).size(th * rnd(0.4F, 0.7F), th * 0.1F).sprite(SOFTCORE).rgb(rnd() < 0.5F ? hot : C)
                        .a(0.85F * lumK).drag(1.8F).spawn();
            }
        }
        // 挥砍抽出去那一根的梢上甩出一串光点，按每秒 60 个冒，和帧率无关
        if (swE > 0.3F) {
            s.lashAcc += dt * 60;
            float th = thk[lashI];
            while (s.lashAcc >= 1) {
                s.lashAcc -= 1;
                EphemeraFx.randDir(dir);
                P.spec().pos(px[lashI][NS], py[lashI][NS], pz[lashI][NS])
                        .vel(swX * 2.2F * sc + dir[0] * 0.6F * sc, swY * 2.2F * sc + dir[1] * 0.6F * sc, swZ * 2.2F * sc + dir[2] * 0.6F * sc)
                        .life(rnd(0.3F, 0.5F)).size(th * 0.8F, 0.01F).sprite(SOFTCORE).rgb(hot).a(0.9F * lumK).drag(3).spawn();
            }
        } else {
            s.lashAcc = 0;
        }
        if (el == POISON) {
            drips(s, m, lite, t, C, pale, A, sc, fp);
        }
        P.step(dt, null);
        if (!fp) {
            P.draw(pen, m.size, 1, Float.NaN);
            fx.motes(sites, 9 * (0.5F + 0.5F * act), sc);
        } else {
            fx.motes(sites.clear(), 0, sc);
        }
        pen.cat = EphemeraStaging.BASE;
    }

    /** 第 i 根藤螺旋上 g（0 = 底、1 = 顶）处的点，写进 hx/hy/hz，角度写进 ha */
    private void hel(float g, float a0, float hand, float sweep, float top, int i, float T, float amp, float breath, float rad, FxMotion m) {
        float a = a0 + hand * sweep * g + 0.07F * (float) Math.sin(T * 0.6F + i + g * 4) * amp;
        float r = m.W * (0.7F + 0.22F * (float) Math.sin(Math.PI * g) + 0.03F * breath * amp) * rad;
        double c = Math.cos(a), sn = Math.sin(a);
        hx = m.x + (m.rightX * c + m.fwdX * sn) * r;
        hy = m.y + m.H * 0.05F + (top - m.H * 0.05F) * g;
        hz = m.z + (m.rightZ * c + m.fwdZ * sn) * r;
        ha = a;
    }

    /** 藤身宽度：根部最粗，盘绕段慢慢收，梢上收成尖 */
    private float wAt(float f, float th) {
        if (f < fr) {
            return th * (1.35F - 0.35F * f / fr);
        }
        if (f < fh) {
            return th * (1 - 0.36F * (f - fr) / (fh - fr));
        }
        return th * 0.64F * (float) Math.pow(Math.max(0, 1 - (f - fh) / (1 - fh)), 0.8) + hH * 0.007F;
    }

    private int rel(float f) {
        return Math.min(NS, Math.round(f * NS));
    }

    /** 第 i 根藤梢的朝向，写进 dir */
    private void tipDir(int i) {
        float dx = (float) (px[i][NS] - px[i][NS - 1]), dy = (float) (py[i][NS] - py[i][NS - 1]), dz = (float) (pz[i][NS] - pz[i][NS - 1]);
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-6F;
        dir[0] = dx / l;
        dir[1] = dy / l;
        dir[2] = dz / l;
    }

    /** 副件：藤身半腰分出一根小卷须，往外上方卷；点写进 spx/spy/spz */
    private void sprout(St s, FxMotion m, int i, float th, float T, float amp, float act, float hurtE, float landE, float sdt, int nsub) {
        int bi = NR + Math.round(NH * (0.4F + 0.15F * hsV[i]));
        double bx = px[i][bi], by = py[i][bi], bz = pz[i][bi];
        double ox = bx - m.x, oz = bz - m.z, ol = Math.sqrt(ox * ox + oz * oz) + 1.0E-9;
        ox /= ol;
        oz /= ol;
        double ux = ox * 0.3, uy = 0.7, uz = oz * 0.3, ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float Ls = m.H * 0.2F * (1 + 0.35F * act + 0.5F * hurtE + 0.3F * landE);
        float bc = 2.4F + 0.3F * (float) Math.sin(T * 0.8F + i * 2) * amp;
        spx[0] = bx;
        spy[0] = by;
        spz[0] = bz;
        double qx = bx, qy = by, qz = bz;
        for (int j = 1; j <= 4; j++) {
            float g = j / 4.0F, a = bc * g * g;
            double c = Math.cos(a), sn = Math.sin(a), step = Ls / 4 * (1.25F - 0.5F * g);
            qx += (ox * c + ux * sn) * step;
            qy += (uy * sn) * step;
            qz += (oz * c + uz * sn) * step;
            FxKit.Spring sp = s.sprout[i][j];
            for (int q = 0; q < nsub; q++) {
                sp.step(qx, qy, qz, sdt, 22 - 6 * g, 0.55F, m.trackX, m.trackY, m.trackZ);
            }
            spx[j] = sp.x;
            spy[j] = sp.y;
            spz[j] = sp.z;
        }
    }

    private void signature(St s, FxMotion m, int el, boolean lite, float t, int C, int hot, int pale, float A, float lumK, float bt,
                           float swE, float hurtE, float H, float sc) {
        ElementMotif fx = s.fx;
        int n = nv;
        switch (el) {
            case FIRE -> {
                // 藤背上窜火舌，光脉烧成熔痕
                for (int i = 0; i < n; i++) {
                    for (int k = 0; k < (lite ? 1 : 3) && left() >= 2; k++) {
                        float f = lite ? 0.45F : k == 0 ? 0.32F : k == 1 ? 0.52F : 0.7F;
                        int j = rel(f);
                        float th = thk[i];
                        fx.accent(px[i][j], py[i][j] + wAt(f, th) * 0.3F, pz[i][j], 0, 1, 0, th * (1.3F + 0.4F * (float) Math.sin(t * 5 + f * 9 + i)),
                                th * 0.75F, 50 + i * 5 + Math.round(f * 10), 0.6F * A);
                    }
                }
            }
            case ICE -> {
                // 藤脊上结一簇簇硬边棱晶，长出来、停住
                for (int i = 0; i < n; i++) {
                    float th = thk[i];
                    for (int k = 0; k < (lite ? 2 : 4) && left() >= 4; k++) {
                        float f = lite ? (k == 0 ? 0.4F : 0.65F) : k == 0 ? 0.28F : k == 1 ? 0.45F : k == 2 ? 0.62F : 0.78F;
                        int j = Math.round(f * NS);
                        double bx = px[i][j], by = py[i][j], bz = pz[i][j];
                        double ox = bx - m.x, oz = bz - m.z, ol = Math.sqrt(ox * ox + oz * oz) + 1.0E-9;
                        ox /= ol;
                        oz /= ol;
                        double oy = 0.5;
                        ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
                        ox /= ol;
                        oy /= ol;
                        oz /= ol;
                        double gx = px[i][j + 1] - px[i][j - 1], gy = py[i][j + 1] - py[i][j - 1], gz = pz[i][j + 1] - pz[i][j - 1];
                        double gl = Math.sqrt(gx * gx + gy * gy + gz * gz) + 1.0E-9;
                        gx /= gl;
                        gy /= gl;
                        gz /= gl;
                        float off = wAt(f, th) * 0.25F;
                        double ax = bx + ox * off, ay = by + oy * off, az = bz + oz * off;
                        for (int q = -1; q <= 1; q += 2) {
                            double dx = ox + gx * 0.45 * q, dy = oy + gy * 0.45 * q, dz = oz + gz * 0.45 * q;
                            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-9;
                            float L = th * (1.1F + 0.7F * hash(i * 7 + j * 3 + q)) * (1 + 0.4F * swE);
                            double tx = ax + dx / dl * L, ty = ay + dy / dl * L, tz = az + dz / dl * L;
                            pen.rgb(pale).a(0.75F * A);
                            kite(ax, ay, az, tx, ty, tz, th * 0.55F, 0.3F);
                            pen.rgb(WHITE).a(0.85F * A);
                            kite(ax, ay, az, tx, ty, tz, th * 0.14F, 0.4F);
                        }
                    }
                }
            }
            case ELECTRIC -> {
                // 相邻两根藤之间来回跳弧
                int q = (int) Math.floor(m.t / 0.06);
                int arcs = 0;
                for (int k = 0; k < n && arcs < 2; k++) {
                    if (hash(q * 5 + k) < 0.35F || left() < 13) {
                        continue;
                    }
                    int b = (k + 1) % n;
                    int ia = NR + 2 + (int) Math.floor(hash(q * 3 + k * 7) * (NH - 2));
                    int ib = Math.min(NS, ia + (int) Math.floor(hash(q * 9 + k) * 3));
                    double ax = px[k][ia], ay = py[k][ia], az = pz[k][ia], bx = px[b][ib], by = py[b][ib], bz = pz[b][ib];
                    double dx = bx - ax, dy = by - ay, dz = bz - az;
                    if (dx * dx + dy * dy + dz * dz > H * 0.9F * H * 0.9F) {
                        continue;
                    }
                    arcs++;
                    float th = thk[k];
                    zig(ax, ay, az, bx, by, bz, 6, th * 0.5F, q * 11 + k);
                    pen.s(SOFT).rgb(C).a(0.45F * A).ribbon(zigRibbon(6, th * 0.7F), EphemeraPen.FACE_CAM, 0, 1);
                    pen.rgb(hot).a(A).ribbon(zigRibbon(6, th * 0.12F), EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(FLARE).rgb(hot).a(0.8F * A).billboard(ax, ay, az, th * 1.4F, th * 1.4F, q);
                }
            }
            case POISON -> {
                // 毒：藤身下沿挂着黏滴，慢慢拉长，拉到头就坠下去（坠下去的粒子在 drips 里发）
                float per = 1.8F;
                for (int i = 0; i < n; i++) {
                    float th = thk[i];
                    for (int k = 0; k < (lite ? 1 : 2) && left() >= 4; k++) {
                        float f = lite ? 0.5F : k == 0 ? 0.4F : 0.62F;
                        int j = rel(f);
                        double bx = px[i][j], by = py[i][j] - wAt(f, th) * 0.4F, bz = pz[i][j];
                        float ph0 = t * 0.6F + hash(i * 7 + (int) (f * 31)) * per, ph = (ph0 % per) / per;
                        float L = th * (0.4F + 2.4F * ph * ph);
                        zr.clear();
                        zr.add(bx, by, bz, th * 0.6F, 1);
                        zr.add(bx, by - L * 0.55F, bz, th * 0.26F, 1);
                        zr.add(bx, by - L, bz, th * 0.34F, 1);
                        pen.s(SOFT).rgb(C).a(0.85F * A).ribbon(zr, EphemeraPen.FACE_CAM, 0, 1);
                        pen.s(DROP).rgb(pale).a(0.9F * A).billboard(bx, by - L - th * 0.3F, bz, th * (0.7F + 0.5F * ph), th * (0.9F + 0.7F * ph));
                    }
                }
            }
            case IMPACT -> {
                // 拍子落下时藤身几个节点各推出一个冲击圈
                if (bt <= 0.05F) {
                    return;
                }
                for (int i = 0; i < n && left() >= 1; i++) {
                    int j = rel(0.55F);
                    float sz = thk[i] * (1.5F + 5 * (1 - bt));
                    pen.s(SHOCK).rgb(pale).a(bt * 0.75F * A * lumK).billboard(px[i][j], py[i][j], pz[i][j], sz, sz);
                }
            }
            case MAGNETIC -> {
                if (lite) {
                    return;
                }
                // 两两藤梢之间连一道磁力弧，光珠沿弧流
                for (int k = 0; k + 1 < n && left() >= 20; k += 2) {
                    double ax = px[k][NS], ay = py[k][NS], az = pz[k][NS], bx = px[k + 1][NS], by = py[k + 1][NS], bz = pz[k + 1][NS];
                    double mx = (ax + bx) / 2, my = (ay + by) / 2 + H * 0.25F, mz = (az + bz) / 2;
                    float th = thk[k];
                    zr.clear();
                    for (int j = 0; j <= 8; j++) {
                        float f = j / 8.0F, g = 1 - f;
                        zr.add(g * g * ax + 2 * g * f * mx + f * f * bx, g * g * ay + 2 * g * f * my + f * f * by,
                                g * g * az + 2 * g * f * mz + f * f * bz, th * 0.8F, 1);
                    }
                    if (swE < 0.1F && hurtE < 0.1F) {
                        pen.s(SOFT).rgb(C).a(0.35F * A).ribbon(zr, EphemeraPen.FACE_CAM, 0, 1);
                    }
                    for (int j = 0; j < zr.n; j++) {
                        zr.w[j] = th * 0.12F;
                    }
                    pen.rgb(pale).a(0.85F * A).ribbon(zr, EphemeraPen.FACE_CAM, 0, 1);
                    for (int j = 0; j < 2; j++) {
                        float f = (t * 0.5F + k * 0.3F + j * 0.5F) % 1;
                        int ii = Math.min(7, (int) Math.floor(f * 8));
                        float ff = f * 8 - ii;
                        pen.s(SOFTCORE).rgb(hot).a(0.9F * A).billboard(zr.x[ii] + (zr.x[ii + 1] - zr.x[ii]) * ff,
                                zr.y[ii] + (zr.y[ii + 1] - zr.y[ii]) * ff, zr.z[ii] + (zr.z[ii + 1] - zr.z[ii]) * ff, th * 0.9F, th * 0.9F);
                    }
                }
            }
            case RADIATION -> {
                // 藤身上随机咔哒一闪的六边形
                int q = (int) Math.floor(m.t / 0.08);
                for (int i = 0; i < n; i++) {
                    for (int k = 0; k < (lite ? 1 : 2) && left() >= 1; k++) {
                        if (hash(q * 7 + i * 3 + k) > 0.5F) {
                            int j = NR + (int) Math.floor(hash(q * 3 + i * 5 + k) * (NH + NT - 1));
                            float sz = thk[i] * 1.6F;
                            pen.s(HEX_0).rgb(hot).a(0.85F * A).billboard(px[i][j], py[i][j], pz[i][j], sz, sz, q);
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    /** 毒滴拉到头那一下坠下去：按周期数认新的一轮，和画不画无关 */
    private void drips(St s, FxMotion m, boolean lite, float t, int C, int pale, float A, float sc, boolean fp) {
        float per = 1.8F;
        for (int i = 0; i < nv; i++) {
            float th = thk[i];
            for (int k = 0; k < (lite ? 1 : 2); k++) {
                float f = lite ? 0.5F : k == 0 ? 0.4F : 0.62F;
                float ph0 = t * 0.6F + hash(i * 7 + (int) (f * 31)) * per, ph = (ph0 % per) / per;
                int cyc = (int) Math.floor(ph0 / per);
                int prev = s.dripCyc[i][k];
                s.dripCyc[i][k] = cyc;
                if (fp || prev == Integer.MIN_VALUE || prev == cyc) {
                    continue;
                }
                int j = rel(f);
                float L = th * (0.4F + 2.4F * ph * ph);
                s.pt.spec().pos(px[i][j], py[i][j] - wAt(f, th) * 0.4F - L, pz[i][j]).vel(0, -0.4F * sc, 0).grav(9 * sc).life(0.7F)
                        .size(th, th * 0.8F).sprite(DROP).rgb(pale).a(0.9F).floor(m.groundY + 0.02).keep().fade(0.1F, 0.2F).spawn();
            }
        }
    }

    private static int darkOf(int deep) {
        int r = Math.round(255 * 0.05F + 0.4F * ((deep >> 16) & 255));
        int g = Math.round(255 * 0.045F + 0.4F * ((deep >> 8) & 255));
        int b = Math.round(255 * 0.058F + 0.4F * (deep & 255));
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    /** 一片长条：a 到 tip，宽 w，侧向朝镜头；笔刷先设好 */
    private void spike(double ax, double ay, double az, double tx, double ty, double tz, float w) {
        double dx = tx - ax, dy = ty - ay, dz = tz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) {
            l = 1;
        }
        sideOf(ax, ay, az, dx / l, dy / l, dz / l);
        double sx = side[0] * w / 2, sy = side[1] * w / 2, sz = side[2] * w / 2;
        pen.quad(tx - sx, ty - sy, tz - sz, tx + sx, ty + sy, tz + sz, ax + sx, ay + sy, az + sz, ax - sx, ay - sy, az - sz);
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
