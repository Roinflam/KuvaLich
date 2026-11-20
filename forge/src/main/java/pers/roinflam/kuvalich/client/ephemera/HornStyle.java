package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 圣角：两肩各立一道弯月形的骨白弧角，自肩外侧拱起、角尖收到颈侧，弧面刻人字纹，一道亮光沿纹路流向角尖；
 * 根部亮芯下簇几根肩刺，前臂外侧各两枚回钩，烟丝从肩头缠到肘弯。跑动后掠留残影、起跳外张、落地下压，
 * 挥砍时主手一侧甩出同形月牙，受击时弧角裂成三截错位乱跳。
 */
public final class HornStyle extends ProtoStyle<HornStyle.St> {

    static final int CARVE = EphemeraSprites.file("horn/carve", true);

    private static final float A0 = -0.44F, A1 = 3.2F;
    private static final float COS_A0 = (float) Math.cos(A0), SIN_A0 = (float) Math.sin(A0);
    private static final int MAX_NA = 12;
    private static final int HIST = 48;
    private static final int HIST_PTS = MAX_NA / 2 + 1;
    private static final int[] SHARDS = {SHARD_0, SHARD_1, SHARD_2, SHARD_3};

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        // 爆发单独一份：电、磁和精简档只留最新一次，靠整份清掉再放
        final ElementMotif fxB = new ElementMotif();
        FxParticles pt;
        FxTrail tr;
        boolean lite;
        float acc;
        final FxKit.Spring[] root = {new FxKit.Spring(), new FxKit.Spring()};
        final FxKit.Spring[] wrist = {new FxKit.Spring(), new FxKit.Spring()};
        final float[] flow = {Float.NaN, Float.NaN};
        boolean seen;
        int seenLand, seenSwing, seenHurt;
        final double[] hT = new double[HIST];
        final double[] hX = new double[HIST * 2 * HIST_PTS], hY = new double[HIST * 2 * HIST_PTS], hZ = new double[HIST * 2 * HIST_PTS];
        int hHead, hN;
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(16);
    private final EphemeraPen.Ribbon mid = new EphemeraPen.Ribbon(16);
    private final EphemeraPen.Ribbon sh = new EphemeraPen.Ribbon(16);
    private final EphemeraPen.Ribbon out = new EphemeraPen.Ribbon(16);
    private final EphemeraPen.Ribbon aux = new EphemeraPen.Ribbon(16);
    private final EphemeraPen.Ribbon aux2 = new EphemeraPen.Ribbon(16);
    private final EphemeraPen.Ribbon ep = new EphemeraPen.Ribbon(8);
    private final EphemeraPen.Ribbon ep2 = new EphemeraPen.Ribbon(8);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final float[] dir = new float[3];
    private final float[] v3 = new float[3];
    private final double[] zx = new double[9], zy = new double[9], zz = new double[9];

    private final double[][] gmX = new double[2][MAX_NA + 1], gmY = new double[2][MAX_NA + 1], gmZ = new double[2][MAX_NA + 1];
    private final double[] goX = new double[MAX_NA + 1], goY = new double[MAX_NA + 1], goZ = new double[MAX_NA + 1];
    private final double[] giX = new double[MAX_NA + 1], giY = new double[MAX_NA + 1], giZ = new double[MAX_NA + 1];
    private final float[] offX = new float[3], offY = new float[3], offZ = new float[3];

    // 当前这只角的坐标架
    private double rootX, rootY, rootZ;
    private float exX, exY, exZ, eyX, eyY, eyZ, nX, nY, nZ;
    private float co, so, scl, Rr;
    private int nA;
    private double px, py, pz;
    private double ptX, ptY, ptZ;

    @Override
    public float extent() {
        return 2.2F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.fx.reset();
        s.fxB.reset();
        s.pt = null;
        for (int i = 0; i < 2; i++) {
            s.root[i].reset();
            s.wrist[i].reset();
            s.flow[i] = Float.NaN;
        }
        s.seen = false;
        s.hN = 0;
    }

    private void init(St s, FxMotion m) {
        s.lite = m.lite;
        s.pt = new FxParticles(m.lite ? 12 : 70);
        s.tr = new FxTrail(0.28F, 1.2F * m.sc, 0.04F * m.sc, 3 * m.sc);
        s.acc = 0;
        s.hN = 0;
    }

    private float wAt(float f) {
        return Rr * (0.08F + 0.5F * (float) Math.sin(Math.PI * Math.pow(f, 0.8))) * (1 - 0.4F * f);
    }

    /** 弧面上角度 a、半径 r 的点，k 是受击裂开后落在哪一截 */
    private void p2(float a, float r, int k) {
        float x = ((float) Math.cos(a) * r - COS_A0 * Rr) * scl, y = ((float) Math.sin(a) * r - SIN_A0 * Rr) * scl;
        float u = x * co + y * so, v = -x * so + y * co;
        px = rootX + exX * u + eyX * v + offX[k];
        py = rootY + exY * u + eyY * v + offY[k];
        pz = rootZ + exZ * u + eyZ * v + offZ[k];
    }

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        boolean lite = m.lite;
        if (s.pt == null || s.lite != lite) {
            init(s, m);
        }
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W, sc = m.sc;
        float act = m.act, amp = m.amp, Bk = m.burst;
        int el = m.el;
        boolean fp = d.firstPersonSelf, air = !m.onGround;
        float fwdX = m.fwdX, fwdZ = m.fwdZ, rightX = m.rightX, rightZ = m.rightZ;
        ElementMotif fx = s.fx;
        fx.begin(pen, m);
        s.fxB.begin(pen, m);
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        int bone = mix(C, WHITE, el == ICE ? 0.6F : 0.72F);
        int shade = mul(mix(C, WHITE, 0.45F), 0.55F);
        int ink = mul(C, 0.5F);
        float T = m.clock(), bt = m.beat(0), bl = m.blink(4), fk = m.flick(4);
        float run = clamp(m.speed / 5.6F, 0, 1), sneakK = m.sneak ? 0.88F : 1;
        float swE = m.env(m.since(m.tSwing), 0.04F, 0.08F, 0.45F), hurtE = m.env(m.since(m.tHurt), 0.02F, 0.12F, 0.5F);
        float landE = m.env(m.since(m.tLand), 0.03F, 0.05F, 0.4F), jumpE = m.env(m.since(m.tJump), 0.04F, 0.1F, 0.4F);
        float breath = 0.5F + 0.5F * (float) Math.sin(t * 1.6F);
        boolean heavy = el == ELECTRIC || el == POISON || el == MAGNETIC;
        float A = (0.6F + 0.4F * act + 0.08F * breath) * bl * fk;
        if (hurtE > 0.05F) {
            A *= 0.5F + 0.5F * hash((int) Math.floor(m.t * 24));
        }
        // 冲击默认近白，加法层按亮度压，不然一片白
        float lum = (0.3F * (C >> 16 & 255) + 0.59F * (C >> 8 & 255) + 0.11F * (C & 255)) / 255;
        float gK = lum > 0.7F ? 0.55F : 1;

        // 爆发先画：挥砍、受击那一下电 / 磁 / 毒贴着预算，爆发要留住，后面的副件超了由渲染器先砍
        pen.cat = EphemeraStaging.PARTICLE;
        bursts(s, m);

        float Rr = 0.17F * H * sneakK;
        int NA = lite ? 7 : 12;
        this.Rr = Rr;
        nA = NA;
        sites.clear();
        int qh = (int) Math.floor(m.t / 0.05);
        double htX = 0, htY = 0, htZ = 0;

        for (int hi = 0; hi < 2; hi++) {
            int sg = hi == 0 ? -1 : 1;
            pen.cat = EphemeraStaging.BASE;
            float swK = (sg > 0 ? 1 : 0.3F) * swE;
            float Ah = A * (1 + 0.7F * swK) * gK;
            // 后掠：跑得越快弧面越往后倒；腾空再倒一点，落地往前一压
            float back = 0.55F * run + 0.22F * (air ? 1 : 0) - 0.18F * landE + 0.15F * hurtE;
            norm(-fwdX * back, 1, -fwdZ * back);
            eyX = v3[0];
            eyY = v3[1];
            eyZ = v3[2];
            // 弧面斜着放（外端朝前），侧面看也有面，角尖落在颈后、不扎进脸
            norm(rightX * sg + fwdX * 0.7F, 0, rightZ * sg + fwdZ * 0.7F);
            float dp = v3[0] * eyX + v3[1] * eyY + v3[2] * eyZ;
            norm(v3[0] - eyX * dp, v3[1] - eyY * dp, v3[2] - eyZ * dp);
            exX = v3[0];
            exY = v3[1];
            exZ = v3[2];
            norm(exY * eyZ - exZ * eyY, exZ * eyX - exX * eyZ, exX * eyY - exY * eyX);
            nX = v3[0];
            nY = v3[1];
            nZ = v3[2];

            float upK = 0.82F * H - 0.045F * H * landE + 0.012F * H * breath * amp + 0.03F * H * jumpE;
            float side = sg * 0.95F * W * sneakK, fw = -0.1F * W;
            FxKit.Spring rs = s.root[hi];
            rs.step(m.x + rightX * side + fwdX * fw, m.y + upK, m.z + rightZ * side + fwdZ * fw, m, 20, 0.6F);
            if (m.justHurt) {
                rs.vx -= fwdX * 1.6F * sc;
                rs.vz -= fwdZ * 1.6F * sc;
            }
            rootX = rs.x;
            rootY = rs.y;
            rootZ = rs.z;

            float open = (0.8F * swK + 0.3F * jumpE) * Bk - 0.12F * landE + 0.08F * bt;
            scl = 1 + (0.2F * swK + 0.06F * jumpE) * Bk + 0.035F * breath * amp + 0.06F * bt;
            co = (float) Math.cos(open);
            so = (float) Math.sin(open);
            // 受击：弧角裂成三截，各自错开乱跳
            boolean cracked = hurtE > 0.08F;
            for (int k = 0; k < 3; k++) {
                if (cracked && k > 0) {
                    float kk = 0.17F * H * hurtE * k / 2;
                    offX[k] = (hash(qh * 7 + k * 13 + sg * 3) - 0.5F) * kk;
                    offY[k] = (hash(qh * 11 + k * 5 + sg) - 0.5F) * kk;
                    offZ[k] = (hash(qh * 3 + k * 17 + sg * 9) - 0.5F) * kk;
                } else {
                    offX[k] = offY[k] = offZ[k] = 0;
                }
            }
            double[] gx = gmX[hi], gy = gmY[hi], gz = gmZ[hi];
            for (int i = 0; i <= NA; i++) {
                float f = (float) i / NA, a = A0 + (A1 - A0) * f, w = wAt(f);
                int k = cracked ? Math.min(2, (int) Math.floor(f * 3)) : 0;
                p2(a, Rr, k);
                gx[i] = px;
                gy[i] = py;
                gz[i] = pz;
                p2(a, Rr + w / 2, k);
                goX[i] = px;
                goY[i] = py;
                goZ[i] = pz;
                p2(a, Rr - w / 2, k);
                giX[i] = px;
                giY[i] = py;
                giZ[i] = pz;
            }
            p2(0, 0, 0);
            double cenX = px, cenY = py, cenZ = pz;
            double rpX = gx[0], rpY = gy[0], rpZ = gz[0], tipX = gx[NA], tipY = gy[NA], tipZ = gz[NA];
            norm((float) (tipX - gx[NA - 1]), (float) (tipY - gy[NA - 1]), (float) (tipZ - gz[NA - 1]));
            float tdX = v3[0], tdY = v3[1], tdZ = v3[2];

            // ---- 托底柔光 ----
            if (!fp) {
                int mi = Math.round(NA * 0.45F);
                float vs = Rr * 3.6F * scl;
                pen.s(SOFT).rgb(C).a((0.12F + 0.06F * breath * amp + 0.2F * swK) * Ah)
                        .billboard((cenX + gx[mi]) / 2, (cenY + gy[mi]) / 2, (cenZ + gz[mi]) / 2, vs, vs);
            }

            // ---- 弧角本体：按裂开的几截分别画 ----
            float cu = 0.5F - ((T * (el == ICE ? 0.08F : 0.18F)) % 0.5F);
            int nP = cracked ? 3 : 1;
            for (int k = 0; k < nP; k++) {
                int i0 = Math.round(k * NA / (float) nP), i1 = Math.round((k + 1) * NA / (float) nP);
                mid.clear();
                sh.clear();
                out.clear();
                for (int i = i0; i <= i1; i++) {
                    float f = (float) i / NA, a = A0 + (A1 - A0) * f, w = wAt(f);
                    p2(a, Rr, k);
                    mid.add(px, py, pz, w * scl, 1);
                    p2(a, Rr - w * 0.24F, k);
                    sh.add(px, py, pz, w * scl * 0.48F, 1);
                    p2(a, Rr + w / 2, k);
                    out.add(px, py, pz, 0.011F * H + 0.012F * H * swK, 0.35F + 0.65F * (float) Math.sin(Math.PI * f));
                }
                float f0 = (float) i0 / NA, f1 = (float) i1 / NA;
                // 正对镜头的一条柔光：侧着看弧面只剩一条线，靠它撑住体量
                pen.s(SOFT).rgb(C).a(0.34F * Ah).ribbon(wid(mid, 2.8F, 0.06F * H), EphemeraPen.FACE_CAM, 0, 1);
                // 深色硬描边：雪地、白天也压得住骨白的轮廓
                pen.alphaBlend().rgb(mul(deep, 0.6F)).a(0.8F).ribbon(wid(mid, 1.22F, 0.014F * H), EphemeraPen.FACE_NORMAL, nX, nY, nZ, 0, 1);
                pen.alphaBlend().rgb(bone).a(0.94F).ribbon(mid, EphemeraPen.FACE_NORMAL, nX, nY, nZ, 0, 1);
                if (!lite) {
                    pen.alphaBlend().rgb(shade).a(0.55F).ribbon(sh, EphemeraPen.FACE_NORMAL, nX, nY, nZ, 0, 1);
                }
                pen.s(CARVE).alphaBlend().rgb(ink).a(0.95F).ribbon(wid(mid, 0.8F, 0), EphemeraPen.FACE_NORMAL, nX, nY, nZ,
                        cu + f0 * 0.5F, cu + f1 * 0.5F);
                pen.rgb(hot).a(0.95F * Ah).ribbon(out, EphemeraPen.FACE_CAM, 0, 1);
                // 厚度：外缘一圈侧面，正侧方看过去是一片有厚度的硬刃
                if (!lite) {
                    for (int i = 0; i + 2 < out.n; i += 2) {
                        float t0 = mid.w[i] * 0.2F, t1 = mid.w[i + 2] * 0.2F;
                        pen.alphaBlend().rgb(shade).a(0.85F)
                                .quad(out.x[i] + nX * t0, out.y[i] + nY * t0, out.z[i] + nZ * t0,
                                        out.x[i + 2] + nX * t1, out.y[i + 2] + nY * t1, out.z[i + 2] + nZ * t1,
                                        out.x[i + 2] - nX * t1, out.y[i + 2] - nY * t1, out.z[i + 2] - nZ * t1,
                                        out.x[i] - nX * t0, out.y[i] - nY * t0, out.z[i] - nZ * t0);
                    }
                }
            }

            // ---- 一道亮光沿刻纹从根部流向角尖，挥砍时连着冲几道 ----
            float pf = s.flow[hi];
            if (Float.isNaN(pf)) {
                pf = sg > 0 ? 0 : 0.6F;
            }
            pf = (pf + dt * (el == ICE ? 0.3F : 0.6F) * (1 + 4 * swK)) % 1.35F;
            s.flow[hi] = pf;
            if (pf < 1.12F) {
                float ci = pf * NA;
                aux.clear();
                int j1 = Math.min(NA, (int) Math.ceil(ci + 1));
                for (int i = Math.max(0, (int) Math.floor(ci - 2.5F)); i <= j1; i++) {
                    aux.add(gx[i], gy[i], gz[i], wAt((float) i / NA) * scl * 0.75F + 0.01F * H, Math.max(0, 1 - Math.abs(i - ci) / 2.4F));
                }
                pen.s(SOFT).rgb(hot).a(0.95F * Ah).ribbon(aux, EphemeraPen.FACE_NORMAL, nX, nY, nZ, 0, 1);
                if (!lite) {
                    int ii = Math.min(NA, Math.round(ci));
                    pen.s(SOFTCORE).rgb(hot).a(0.6F * Ah * Math.min(1, (1.12F - pf) * 6)).billboard(gx[ii], gy[ii], gz[ii], 0.1F * H, 0.1F * H);
                }
            }

            // ---- 角尖星芒、根部亮芯 ----
            float ts = 0.11F * H * (1 + swK);
            pen.s(FLARE).rgb(hot).a((0.45F + 0.3F * breath * amp + 0.5F * swK) * Ah).billboard(tipX, tipY, tipZ, ts, ts, t * 0.4F);
            float gs = 0.045F * H * (1 + 0.4F * swK + 0.2F * bt);
            FxKit.glow(pen, rpX, rpY, rpZ, 0.17F * H * (1 + 0.3F * swK), C, hot, (0.65F + 0.35F * breath * amp) * Ah);
            pen.rgb(hot).a(0.85F * Ah).quad(rpX + eyX * gs * 1.4F, rpY + eyY * gs * 1.4F, rpZ + eyZ * gs * 1.4F,
                    rpX + exX * gs * 0.7F, rpY + exY * gs * 0.7F, rpZ + exZ * gs * 0.7F,
                    rpX - eyX * gs * 1.4F, rpY - eyY * gs * 1.4F, rpZ - eyZ * gs * 1.4F,
                    rpX - exX * gs * 0.7F, rpY - exY * gs * 0.7F, rpZ - exZ * gs * 0.7F);

            // ---- 肩刺：根部往外下方簇几根带弯的硬刺 ----
            int NS = lite ? 0 : 3;
            for (int j = 0; j < NS; j++) {
                float ang = -0.6F - j * 0.48F - 0.3F * open;
                float c = (float) Math.cos(ang), sn = (float) Math.sin(ang);
                norm(exX * c + eyX * sn, exY * c + eyY * sn, exZ * c + eyZ * sn);
                float dX = v3[0], dY = v3[1], dZ = v3[2];
                float L = H * (0.17F - 0.03F * j) * (1 + 0.5F * swK * Bk + 0.12F * bt + 0.15F * hurtE);
                float on = (j - 1) * 0.035F * H, oy = -0.01F * H * j;
                double bx = rpX + nX * on + eyX * oy, by = rpY + nY * on + eyY * oy, bz = rpZ + nZ * on + eyZ * oy;
                aux.clear();
                for (int i = 0; i <= 3; i++) {
                    float f = i / 3.0F, a = L * f, dn = -L * 0.4F * f * f;
                    aux.add(bx + dX * a + eyX * dn, by + dY * a + eyY * dn, bz + dZ * a + eyZ * dn, 0.042F * H * (1 - f) + 0.004F * H, 1);
                }
                pen.alphaBlend().rgb(bone).a(0.92F).ribbon(aux, EphemeraPen.FACE_CAM, 0, 1);
                pen.rgb(hot).a(0.75F * Ah).ribbon(wid(aux, 0.32F, 0), EphemeraPen.FACE_CAM, 0, 1);
                if (j == 0) {
                    sites.add(aux.x[3], aux.y[3], aux.z[3], dX, dY, dZ);
                }
            }

            // ---- 前臂回钩：跟着估出来的手腕摆 ----
            BodyPoints.Pt wr = sg > 0 ? m.pts.wristR : m.pts.wristL, dw = sg > 0 ? m.pts.downR : m.pts.downL;
            FxKit.Spring ws = s.wrist[hi];
            follow(ws, wr.x, wr.y, wr.z, m, 30, 0.8F);
            double hpX = ws.x, hpY = ws.y, hpZ = ws.z;
            float dnX = (float) dw.x, dnY = (float) dw.y, dnZ = (float) dw.z;
            float srX = rightX * sg, srZ = rightZ * sg, sd = srX * dnX + srZ * dnZ;
            norm(srX - dnX * sd, -dnY * sd, srZ - dnZ * sd);
            float ovX = v3[0], ovY = v3[1], ovZ = v3[2];
            double elX = hpX - dnX * 0.2F * sc, elY = hpY - dnY * 0.2F * sc, elZ = hpZ - dnZ * 0.2F * sc;
            int NH = lite ? 1 : 2;
            htX = hpX;
            htY = hpY;
            htZ = hpZ;
            for (int j = 0; j < NH; j++) {
                // 根部挪到前臂外表面，不然大半截藏在胳膊里
                float lf = 0.3F + 0.45F * j, ob = 0.11F * sc;
                double bx = elX + (hpX - elX) * lf + ovX * ob, by = elY + (hpY - elY) * lf + ovY * ob, bz = elZ + (hpZ - elZ) * lf + ovZ * ob;
                float L = H * (0.13F - 0.03F * j) * (1 + (sg > 0 ? 0.7F * swE * Bk : 0) + 0.12F * bt);
                aux.clear();
                for (int i = 0; i <= 4; i++) {
                    float f = i / 4.0F, a = f * 1.7F;
                    float o = (float) Math.sin(a) * L * 0.8F, dd = -(1 - (float) Math.cos(a)) * L * 0.55F;
                    aux.add(bx + ovX * o + dnX * dd, by + ovY * o + dnY * dd, bz + ovZ * o + dnZ * dd, 0.034F * H * (1 - f) + 0.004F * H, 1);
                }
                pen.alphaBlend().rgb(bone).a(0.92F).ribbon(aux, EphemeraPen.FACE_CAM, 0, 1);
                pen.rgb(hot).a(0.8F * Ah).ribbon(wid(aux, 0.32F, 0), EphemeraPen.FACE_CAM, 0, 1);
                if (!lite && !heavy) {
                    pen.s(SOFT).rgb(C).a(0.5F * Ah).billboard(aux.x[4], aux.y[4], aux.z[4], 0.07F * H, 0.07F * H);
                }
                if (j == 0) {
                    htX = aux.x[2];
                    htY = aux.y[2];
                    htZ = aux.z[2];
                }
                sites.add(aux.x[4], aux.y[4], aux.z[4], ovX, ovY, ovZ);
            }
            if (!lite) {
                pen.s(SOFT).rgb(C).a(0.16F * Ah).beam(elX, elY, elZ, hpX, hpY, hpZ, 0.1F * H);
            }
            // 主手挥砍、跑动时回钩拖出一道光
            if (sg > 0) {
                s.tr.push(htX, htY, htZ, m.t);
                float tk = Math.max(swE * 1.4F, Math.max(run * 0.45F, air ? 0.3F : 0));
                if (tk > 0.02F && !fp) {
                    pen.cat = EphemeraStaging.TRAIL;
                    s.tr.sample(lite ? 4 : 6, sample);
                    final float k1 = 0.13F * H * (1 + swE), k2 = 0.45F * tk * Ah, k3 = 0.022F * H * (1 + swE), k4 = 0.85F * tk * Ah;
                    if (!lite) {
                        pen.s(SOFT).rgb(C);
                        FxTrail.draw(pen, sample, rib, (q, i) -> k1 * (1 - q), (q, i) -> k2 * (1 - q), null, m.trailW, m.trailA,
                                EphemeraPen.FACE_CAM, 0, 1);
                    }
                    pen.rgb(hot);
                    FxTrail.draw(pen, sample, rib, (q, i) -> k3 * (1 - q), (q, i) -> k4 * (1 - q), null, m.trailW, m.trailA,
                            EphemeraPen.FACE_CAM, 0, 1);
                    pen.cat = EphemeraStaging.BASE;
                }
            }

            // ---- 烟丝：一缕从肩头螺旋缠到肘弯，一缕绕着弧角打转（氛围，超预算先砍） ----
            pen.cat = EphemeraStaging.PARTICLE;
            float Tw = el == ICE ? t * 0.4F : el == ELECTRIC ? T : t;
            {
                int N = lite ? 5 : 9;
                double tpX = rpX - rightX * sg * 0.05F * W, tpY = rpY + 0.02F * H, tpZ = rpZ - rightZ * sg * 0.05F * W;
                aux.clear();
                for (int i = 0; i <= N; i++) {
                    float f = (float) i / N;
                    float ang = f * 4.4F + Tw * 1.1F * sg;
                    float r = H * (0.05F + 0.05F * (float) Math.sin(Math.PI * f)) * (1 + 0.5F * run * f + 0.3F * swK);
                    float nz = (noise(t * 0.8F + f * 3, 31 + sg) - 0.5F) * 0.07F * H;
                    float lat = sg * ((float) Math.cos(ang) * r + nz), fo = (float) Math.sin(ang) * r, vk = -0.07F * f;
                    aux.add(tpX + (elX - tpX) * f + rightX * lat + fwdX * fo + m.velX * vk,
                            tpY + (elY - tpY) * f + m.velY * vk,
                            tpZ + (elZ - tpZ) * f + rightZ * lat + fwdZ * fo + m.velZ * vk,
                            H * (0.03F + 0.06F * (float) Math.sin(Math.PI * f)), (float) Math.sin(Math.PI * Math.min(1, f * 1.1F + 0.04F)));
                }
                float uo = (t * 0.22F) % 0.5F;
                if (!lite) {
                    pen.s(FLOW_0).rgb(C).a(0.6F * Ah).ribbon(wid(aux, 2, 0), EphemeraPen.FACE_CAM, uo, uo + 0.5F);
                }
                pen.s(FLOW_1).rgb(hot).a(0.95F * Ah).ribbon(aux, EphemeraPen.FACE_CAM, 0.5F - uo, 1 - uo);
            }
            if (!lite && !heavy) {
                aux.clear();
                for (int i = 0; i <= NA; i++) {
                    float f = (float) i / NA, ph = f * 7 - Tw * 2.2F * sg, rr = (0.55F * wAt(f) + 0.02F * H) * scl * (1 + 0.6F * swK);
                    radAt(i);
                    float c = (float) Math.cos(ph) * rr, sn = (float) Math.sin(ph) * rr * 1.4F;
                    aux.add(gx[i] + nX * c + v3[0] * sn, gy[i] + nY * c + v3[1] * sn, gz[i] + nZ * c + v3[2] * sn,
                            0.035F * H, 0.25F + 0.75F * (float) Math.sin(Math.PI * f));
                }
                float uo = (t * 0.3F + 0.2F) % 0.5F;
                pen.s(FLOW_1).rgb(hot).a(0.6F * Ah).ribbon(aux, EphemeraPen.FACE_CAM, uo, uo + 0.5F);
            }
            pen.cat = EphemeraStaging.BASE;

            // ---- 元素：外缘按元素长东西（毒沿内缘），角尖再伸一根；磁力不走沿边凸起，闭合磁力线就是它的招牌 ----
            if (el != MAGNETIC) {
                boolean poi = el == POISON;
                int step = lite ? 4 : poi ? 4 : el == ELECTRIC ? 3 : 2;
                ep.clear();
                for (int i = 2; i < NA - 1; i += step) {
                    ep.add(poi ? giX[i] : goX[i], poi ? giY[i] : goY[i], poi ? giZ[i] : goZ[i], 0, 1);
                }
                if (ep.n >= 2) {
                    int l = ep.n - 1;
                    double tx = ep.x[l] - ep.x[0], ty = ep.y[l] - ep.y[0], tz = ep.z[l] - ep.z[0];
                    double vx = (ep.x[0] + ep.x[l]) / 2 - pen.camX(), vy = (ep.y[0] + ep.y[l]) / 2 - pen.camY(), vz = (ep.z[0] + ep.z[l]) / 2 - pen.camZ();
                    radAt(Math.round(NA / 2.0F));
                    double cx = ty * vz - tz * vy, cy = tz * vx - tx * vz, cz = tx * vy - ty * vx;
                    EphemeraPen.Ribbon use = ep;
                    // 凸起朝「切线 × 视线」那边长，点的顺序反了就往里长
                    if (cx * v3[0] + cy * v3[1] + cz * v3[2] < 0) {
                        ep2.clear();
                        for (int i = l; i >= 0; i--) {
                            ep2.add(ep.x[i], ep.y[i], ep.z[i], 0, 1);
                        }
                        use = ep2;
                    }
                    fx.edge(use, (poi ? 0.055F : 0.04F) * H * (1 + 0.5F * swK), 0.85F * A * gK, sg + 7);
                }
                float adX = tdX, adY = tdY, adZ = tdZ;
                if (poi) {
                    adX = 0;
                    adY = -1;
                    adZ = 0;
                } else if (el == FIRE) {
                    norm(tdX, tdY + 1, tdZ);
                    adX = v3[0];
                    adY = v3[1];
                    adZ = v3[2];
                }
                fx.accent(tipX, tipY, tipZ, adX, adY, adZ, 0.13F * H * (1 + 0.6F * swK), 0.05F * H, sg > 0 ? 21 : 22, 0.95F * A * gK);
            }

            if (el == FIRE) {
                // 弧角外面裹一层往上翻的火絮
                aux.clear();
                for (int i = 0; i <= NA; i++) {
                    float f = (float) i / NA;
                    aux.add(gx[i], gy[i] + 0.02F * H, gz[i], wAt(f) * 2.2F * scl + 0.05F * H, 0.3F + 0.7F * (float) Math.sin(Math.PI * f));
                }
                float u0 = (t * 0.6F) % 0.5F;
                pen.s(FLOW_0).rgb(C).a((lite ? 0.35F : 0.5F) * A).ribbon(aux, EphemeraPen.FACE_CAM, u0, u0 + 0.5F);
                if (!lite) {
                    int mi = Math.round(NA * 0.45F);
                    fx.accent(gx[mi], gy[mi], gz[mi], 0, 1, 0, 0.16F * H * (1 + 0.5F * swK), 0.07F * H, 30 + sg, 0.8F * A);
                }
            } else if (el == ICE) {
                // 内缘挂一排冰棱，角面蒙一层霜
                int NI = lite ? 2 : 4;
                norm(pen.fwdZ, 0, -pen.fwdX);
                float sdX = v3[0], sdY = v3[1], sdZ = v3[2];
                for (int j = 0; j < NI; j++) {
                    int i = Math.round(NA * (0.25F + 0.5F * j / Math.max(1, NI - 1)));
                    float ph = ((T * 0.8F + j * 0.37F + (sg > 0 ? 0.5F : 0)) % 2.2F) / 2.2F;
                    float grow = ph < 0.2F ? smooth(ph / 0.2F) : ph < 0.88F ? 1 : 1 - (ph - 0.88F) / 0.12F;
                    float L = H * (0.09F + 0.06F * hash(j * 7 + sg)) * grow * (1 + 0.3F * swK);
                    if (L < 0.005F) {
                        continue;
                    }
                    double bx = giX[i], by = giY[i], bz = giZ[i], tpy = by - L, my = by - L * 0.25F;
                    float w1 = 0.02F * H, w2 = 0.004F * H;
                    pen.rgb(pale).a(0.85F * A).va(1, 0.6F, 0.3F, 0.6F)
                            .quad(bx, tpy, bz, bx + sdX * w1, my + sdY * w1, bz + sdZ * w1, bx, by, bz, bx - sdX * w1, my - sdY * w1, bz - sdZ * w1);
                    pen.rgb(WHITE).a(0.9F * A)
                            .quad(bx, tpy, bz, bx + sdX * w2, my + sdY * w2, bz + sdZ * w2, bx, by, bz, bx - sdX * w2, my - sdY * w2, bz - sdZ * w2);
                }
                if (!lite) {
                    aux.clear();
                    for (int i = 0; i <= NA; i++) {
                        aux.add(gx[i], gy[i], gz[i], wAt((float) i / NA) * 1.6F * scl, 1);
                    }
                    pen.s(SOFT).rgb(pale).a(0.25F * A).ribbon(aux, EphemeraPen.FACE_NORMAL, nX, nY, nZ, 0, 1);
                }
            } else if (el == ELECTRIC) {
                // 弧口里一道电弧横跨根部和角尖、一路往上爬到弧顶再重来；隔一阵再从角上劈到前臂的回钩
                int q = (int) Math.floor(m.t / 0.06);
                if (hash(q * 3 + sg * 5) > 0.15F) {
                    float lad = (t * (0.9F + 1.5F * swK) + (sg > 0 ? 0.5F : 0)) % 1;
                    // 只跨弧角外半段：角尖那头在正面看藏在头后
                    int i0 = Math.round(lad * NA * 0.3F), i1 = Math.round(NA * (0.66F - 0.24F * lad));
                    int nz = lite ? 4 : 6;
                    zig(gx[i0], gy[i0], gz[i0], gx[i1], gy[i1], gz[i1], nz, 0.035F * H, q * 13 + sg, eyX, eyY, eyZ);
                    if (!lite) {
                        pen.s(SOFT).rgb(C).a(0.8F * A).ribbon(zigRibbon(nz, 0.14F * H), EphemeraPen.FACE_CAM, 0, 1);
                    }
                    pen.rgb(hot).a(A).ribbon(zigRibbon(nz, 0.02F * H), EphemeraPen.FACE_CAM, 0, 1);
                    if (!lite) {
                        pen.s(FLARE).rgb(hot).a(0.85F * A).billboard(zx[0], zy[0], zz[0], 0.1F * H, 0.1F * H, q);
                        pen.s(FLARE).rgb(hot).a(0.85F * A).billboard(zx[nz], zy[nz], zz[nz], 0.1F * H, 0.1F * H, q);
                    }
                }
                int q2 = (int) Math.floor(m.t / 0.09);
                if (!lite && hash(q2 * 11 + sg * 3) > 0.5F) {
                    int i0 = Math.round(NA * 0.1F);
                    double ax = gx[i0], ay = gy[i0], az = gz[i0];
                    norm((float) (htX - ax), (float) (htY - ay), (float) (htZ - az));
                    norm(v3[1] * pen.fwdZ - v3[2] * pen.fwdY, v3[2] * pen.fwdX - v3[0] * pen.fwdZ, v3[0] * pen.fwdY - v3[1] * pen.fwdX);
                    zig(ax, ay, az, htX, htY, htZ, 8, 0.05F * H, q2 * 17 + sg, v3[0], v3[1], v3[2]);
                    pen.rgb(hot).a(0.95F * A).ribbon(zigRibbon(8, 0.016F * H), EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(FLARE).rgb(hot).a(0.8F * A).billboard(htX, htY, htZ, 0.12F * H, 0.12F * H, q2);
                }
            } else if (el == POISON) {
                // 根部和角尖之间垂一道黏丝，最低处鼓成一滴往下坠
                int NS2 = lite ? 4 : 7;
                float sag = Rr * (0.45F + 0.12F * (float) Math.sin(t * 1.4F + sg) + 0.3F * run);
                int i1 = Math.round(NA * 0.66F);
                aux.clear();
                for (int i = 0; i <= NS2; i++) {
                    float f = (float) i / NS2, sp = (float) Math.sin(Math.PI * f);
                    aux.add(giX[1] + (giX[i1] - giX[1]) * f, giY[1] + (giY[i1] - giY[1]) * f - sag * sp, giZ[1] + (giZ[i1] - giZ[1]) * f,
                            0.024F * H * (1 + 1.4F * (float) Math.pow(sp, 6)), 1);
                }
                if (!lite) {
                    pen.s(SOFT).rgb(C).a(0.35F * A).ribbon(wid(aux, 3, 0), EphemeraPen.FACE_CAM, 0, 1);
                }
                pen.rgb(mix(C, pale, 0.5F)).a(0.9F * A).ribbon(aux, EphemeraPen.FACE_CAM, 0, 1);
                int di = Math.round(NS2 / 2.0F);
                pen.s(DROP).rgb(pale).a(0.95F * A).billboard(aux.x[di], aux.y[di] - 0.04F * H, aux.z[di], 0.075F * H, 0.1F * H);
                // 角面上一层慢慢翻的毒絮
                if (!lite) {
                    aux.clear();
                    for (int i = 0; i <= NA; i++) {
                        float f = (float) i / NA;
                        aux.add(gx[i], gy[i], gz[i], wAt(f) * 1.9F * scl + 0.02F * H, 0.3F + 0.7F * (float) Math.sin(Math.PI * f));
                    }
                    float u0 = (t * 0.12F) % 0.5F;
                    pen.s(FLOW_0).rgb(mul(C, 0.85F)).a(0.4F * A).ribbon(aux, EphemeraPen.FACE_CAM, u0, u0 + 0.5F);
                }
            } else if (el == IMPACT) {
                // 每一拍从弧心往外推一道冲击圈
                if (bt > 0.02F) {
                    pen.s(SHOCK).rgb(pale).a(0.75F * bt * gK);
                    FxKit.plate(pen, cenX, cenY, cenZ, exX, exY, exZ, eyX, eyY, eyZ, Rr * 2.6F * (1 + 0.7F * (1 - bt)), 0);
                }
            } else if (el == MAGNETIC) {
                // 磁力线接上弧角缺的那一截，合成一个闭合的圈，光珠沿圈流
                int NMg = lite ? 5 : 9;
                float wob = 0.4F + 0.08F * (float) Math.sin(t * 1.3F + sg);
                aux.clear();
                for (int i = 0; i <= NMg; i++) {
                    float f = (float) i / NMg, a = A1 + (A0 + EphemeraFx.TAU - A1) * f, bulge = (float) Math.sin(Math.PI * f);
                    p2(a, Rr * (1 + 0.15F * bulge), 0);
                    float o = bulge * Rr * wob;
                    aux.add(px + nX * o, py + nY * o, pz + nZ * o, 0.018F * H, 0.3F + 0.7F * bulge);
                }
                if (!lite) {
                    pen.s(SOFT).rgb(C).a(0.5F * A).ribbon(wid(aux, 0, 0.08F * H), EphemeraPen.FACE_CAM, 0, 1);
                }
                pen.rgb(pale).a(0.95F * A).ribbon(aux, EphemeraPen.FACE_CAM, 0, 1);
                // 第二道：从根部出去、绕过弧心外侧回到角尖，和弧面交叉，像一枚陀螺仪
                if (!lite) {
                    float rk = Rr * (0.75F + 0.1F * (float) Math.sin(t * 1.1F + sg));
                    aux2.clear();
                    for (int i = 0; i <= 10; i++) {
                        float f = i / 10.0F, sa = (float) Math.sin(f * Math.PI), o = sa * rk, oy = sa * Rr * 0.5F;
                        aux2.add(rpX + (tipX - rpX) * f + nX * o + eyX * oy, rpY + (tipY - rpY) * f + nY * o + eyY * oy,
                                rpZ + (tipZ - rpZ) * f + nZ * o + eyZ * oy, 0.014F * H, 0.35F + 0.65F * sa);
                    }
                    pen.s(SOFT).rgb(C).a(0.4F * A).ribbon(wid(aux2, 0, 0.07F * H), EphemeraPen.FACE_CAM, 0, 1);
                    pen.rgb(pale).a(0.85F * A).ribbon(aux2, EphemeraPen.FACE_CAM, 0, 1);
                    float f2 = (t * 0.4F + (sg > 0 ? 0.5F : 0)) % 1;
                    int i2 = Math.min(9, (int) Math.floor(f2 * 10));
                    bead(aux2, i2, f2 * 10 - i2, 0.07F * H, hot, 0.95F * A);
                }
                for (int b = 0; b < (lite ? 1 : 2); b++) {
                    float f = (t * 0.5F + b * 0.5F + (sg > 0 ? 0.25F : 0)) % 1;
                    int i = Math.min(NMg - 1, (int) Math.floor(f * NMg));
                    bead(aux, i, f * NMg - i, 0.06F * H, hot, 0.95F * A);
                }
            } else {
                // 弧心一枚慢转的六边形光斑，跟着明灭
                pen.s(HEX_0).rgb(hot).a(0.7F * A * Math.min(1.3F, bl));
                FxKit.plate(pen, cenX, cenY, cenZ, exX, exY, exZ, eyX, eyY, eyZ, Rr * 1.1F * scl, T * 0.3F + sg);
                if (!lite) {
                    pen.s(HEX_1).rgb(C).a(0.3F * A * bl);
                    FxKit.plate(pen, cenX, cenY, cenZ, exX, exY, exZ, eyX, eyY, eyZ, Rr * 1.9F * scl, -T * 0.2F);
                }
            }

            // ---- 挥砍：主手一侧的弧角甩出一道同形的月牙光影 ----
            if (sg > 0 && !fp) {
                float ss = m.since(m.tSwing) / m.burstTime;
                if (ss < 0.55F) {
                    float f = ss / 0.55F, ek = 1 + 1.1F * f * Bk, fo = (0.15F + 1.2F * f) * sc * Bk, uo = 0.12F * f * sc;
                    aux.clear();
                    for (int i = 0; i <= NA; i += 2) {
                        aux.add(rootX + (gx[i] - rootX) * ek + fwdX * fo, rootY + (gy[i] - rootY) * ek + uo, rootZ + (gz[i] - rootZ) * ek + fwdZ * fo,
                                wAt((float) i / NA) * (1.6F + 1.5F * f), 1);
                    }
                    pen.cat = EphemeraStaging.TRAIL;
                    pen.s(SOFT).rgb(C).a(0.6F * (1 - f) * gK).ribbon(wid(aux, 2.2F, 0), EphemeraPen.FACE_CAM, 0, 1);
                    pen.rgb(hot).a(0.7F * (1 - f) * gK).ribbon(wid(aux, 0.6F, 0), EphemeraPen.FACE_NORMAL, nX, nY, nZ, 0, 1);
                    pen.cat = EphemeraStaging.BASE;
                }
            }

            int s3 = Math.round(NA * 0.3F), s6 = Math.round(NA * 0.6F);
            radAt(s3);
            sites.add(gx[s3], gy[s3], gz[s3], v3[0], v3[1], v3[2]);
            radAt(s6);
            sites.add(gx[s6], gy[s6], gz[s6], v3[0], v3[1], v3[2]);
            sites.add(tipX, tipY, tipZ, tdX, tdY, tdZ);
        }

        // ---- 跑动残影：弧角在身后留几道淡影 ----
        record(s, m, NA);
        if (!fp && (run > 0.05F || air)) {
            int n = lite ? 0 : heavy ? 2 : 3;
            float k = Math.max(run, air ? 0.6F : 0);
            int np = NA / 2 + 1;
            pen.cat = EphemeraStaging.TRAIL;
            for (int j = 1; j <= n; j++) {
                double tgt = m.t - j * 0.06;
                int h = -1;
                for (int c = 0; c < s.hN; c++) {
                    int idx = (s.hHead - c + HIST) % HIST;
                    if (s.hT[idx] <= tgt) {
                        h = idx;
                        break;
                    }
                }
                if (h < 0 || m.t - s.hT[h] > 0.3) {
                    break;
                }
                float ga = 0.45F * k * A * gK * (1 - j / (float) (n + 1));
                for (int hi = 0; hi < 2; hi++) {
                    rib.clear();
                    int base = (h * 2 + hi) * HIST_PTS;
                    for (int i = 0; i < np; i++) {
                        rib.add(s.hX[base + i], s.hY[base + i], s.hZ[base + i], 0.055F * H, 1);
                    }
                    pen.s(SOFT).rgb(C).a(ga).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                }
            }
        }

        // ---- 粒子：弧角上飘的烟丝和光尘，受击崩碎片，落地往下撒 ----
        pen.cat = EphemeraStaging.PARTICLE;
        FxParticles P = s.pt;
        P.thin = m.emit;
        float vX = m.velX, vY = m.velY, vZ = m.velZ;
        s.acc += dt * (3 + 12 * run + 35 * swE + 20 * hurtE) * (lite ? 0.4F : 1);
        while (s.acc >= 1) {
            s.acc -= 1;
            onArc();
            if (rnd() < 0.55F) {
                P.spec().pos(ptX, ptY, ptZ).vel(-vX * 0.3F, -vY * 0.3F + 0.45F * sc, -vZ * 0.3F).life(rnd(0.7F, 1.1F))
                        .size(0.1F * sc, 0.2F * sc).sprite(WISP).rgb(C).a(0.55F * gK).drag(1.2F).rot(rnd(-0.4F, 0.4F)).spawn();
            } else {
                EphemeraFx.randDir(dir);
                P.spec().pos(ptX, ptY, ptZ).vel(dir[0] * 0.3F * sc, dir[1] * 0.3F * sc + 0.3F * sc, dir[2] * 0.3F * sc).life(rnd(0.5F, 0.9F))
                        .size(0.035F * sc, 0.01F * sc).sprite(SOFTCORE).rgb(hot).a(0.9F).drag(1.5F).spawn();
            }
        }
        if (m.justHurt) {
            for (int j = 0; j < (lite ? 4 : 10); j++) {
                onArc();
                EphemeraFx.randDir(dir);
                float sp = rnd(1.2F, 2.4F) * sc;
                P.spec().pos(ptX, ptY, ptZ).vel(dir[0] * sp, dir[1] * sp + sc, dir[2] * sp).life(rnd(0.5F, 0.8F))
                        .size(0.06F * sc, 0.03F * sc).sprite(SHARDS[j & 3]).rgb(hot).a(1).grav(7 * sc).spin(rnd(-9, 9)).rot(rnd(0, 6))
                        .keep().force().spawn();
            }
        }
        if (m.justLand) {
            for (int j = 0; j < (lite ? 3 : 8); j++) {
                onArc();
                EphemeraFx.randDir(dir);
                P.spec().pos(ptX, ptY, ptZ).vel(dir[0] * 0.6F * sc, dir[1] * 0.6F * sc - 1.2F * sc, dir[2] * 0.6F * sc).life(0.5F)
                        .size(0.04F * sc, 0.01F * sc).sprite(SOFTCORE).rgb(hot).a(0.9F).grav(4 * sc).stretch(0.05F).keep().force().spawn();
            }
        }
        P.step(dt, null);
        P.draw(pen, m.size, 1, Float.NaN);
        fx.motes(sites, (heavy ? 8 : 14) * (0.5F + 0.5F * act), m.sc);
    }

    /** 落地、挥砍、受击在脚下各放一次元素爆发；电、磁的爆发一次就上百个面片，和精简档一样只留最新一次 */
    private void bursts(St s, FxMotion m) {
        if (!s.seen) {
            s.seen = true;
            s.seenLand = m.nLand;
            s.seenSwing = m.nSwing;
            s.seenHurt = m.nHurt;
        }
        boolean only = m.lite || m.el == ELECTRIC || m.el == MAGNETIC;
        float r = m.W * 1.6F;
        if (s.seenLand != m.nLand) {
            s.seenLand = m.nLand;
            if (m.since(m.tLand) < 0.2F) {
                shoot(s, only, m, r, 1);
            }
        }
        if (s.seenSwing != m.nSwing) {
            s.seenSwing = m.nSwing;
            if (m.since(m.tSwing) < 0.2F) {
                shoot(s, only, m, r * 0.8F, 0.8F);
            }
        }
        if (s.seenHurt != m.nHurt) {
            s.seenHurt = m.nHurt;
            if (m.since(m.tHurt) < 0.2F) {
                shoot(s, only, m, r, 0.9F);
            }
        }
        s.fxB.bursts();
    }

    private static void shoot(St s, boolean only, FxMotion m, float r, float k) {
        if (only) {
            s.fxB.reset();
        }
        s.fxB.fire(m.x, m.groundY, m.z, r, k);
    }

    /** 每 10 ms 记一份两只角的中线点给残影用 */
    private void record(St s, FxMotion m, int NA) {
        if (s.hN > 0 && m.t - s.hT[s.hHead] < 0.01) {
            return;
        }
        s.hHead = (s.hHead + 1) % HIST;
        s.hN = Math.min(HIST, s.hN + 1);
        s.hT[s.hHead] = m.t;
        for (int hi = 0; hi < 2; hi++) {
            int base = (s.hHead * 2 + hi) * HIST_PTS;
            for (int i = 0, j = 0; i <= NA; i += 2, j++) {
                s.hX[base + j] = gmX[hi][i];
                s.hY[base + j] = gmY[hi][i];
                s.hZ[base + j] = gmZ[hi][i];
            }
        }
    }

    /** w0 = 30 在 dt = 0.04 下半隐式积分会发散，拆成不超过 0.02 秒的小步 */
    private static void follow(FxKit.Spring sp, double x, double y, double z, FxMotion m, float w0, float zeta) {
        int n = Math.max(1, (int) Math.ceil(m.dt / 0.02F));
        float h = m.dt / n;
        for (int i = 0; i < n; i++) {
            sp.step(x, y, z, h, w0, zeta, m.trackX, m.trackY, m.trackZ);
        }
    }

    /** 随便挑一只角上的一点，放进 ptX/ptY/ptZ */
    private void onArc() {
        int hi = rnd() < 0.5F ? 0 : 1;
        int i = Math.min(nA, (int) (rnd() * (nA + 1)));
        ptX = gmX[hi][i];
        ptY = gmY[hi][i];
        ptZ = gmZ[hi][i];
    }

    /** gm[i] 处往外（弧的径向）的方向，写进 v3 */
    private void radAt(int i) {
        float f = (float) i / nA, a = A0 + (A1 - A0) * f, w = wAt(f);
        float x = (float) Math.cos(a) * w / 2 * scl, y = (float) Math.sin(a) * w / 2 * scl;
        norm(exX * (x * co + y * so) + eyX * (-x * so + y * co), exY * (x * co + y * so) + eyY * (-x * so + y * co),
                exZ * (x * co + y * so) + eyZ * (-x * so + y * co));
    }

    private void bead(EphemeraPen.Ribbon r, int i, float f, float size, int c, float a) {
        pen.s(SOFTCORE).rgb(c).a(a).billboard(r.x[i] + (r.x[i + 1] - r.x[i]) * f, r.y[i] + (r.y[i + 1] - r.y[i]) * f,
                r.z[i] + (r.z[i + 1] - r.z[i]) * f, size, size);
    }

    /** 同一串点换宽度再画一层：宽 = 原宽 × k + add，透明度照旧；结果在 rib 里 */
    private EphemeraPen.Ribbon wid(EphemeraPen.Ribbon src, float k, float add) {
        rib.clear();
        for (int i = 0; i < src.n; i++) {
            rib.add(src.x[i], src.y[i], src.z[i], src.w[i] * k + add, src.a[i]);
        }
        return rib;
    }

    /** a 到 b 的折线（n 段），中间各点沿 (sx, sy, sz) 随机偏 ±amp */
    private void zig(double ax, double ay, double az, double bx, double by, double bz, int n, float amp, int seed,
                     float sx, float sy, float sz) {
        for (int i = 0; i <= n; i++) {
            float f = (float) i / n;
            float j = i == 0 || i == n ? 0 : (hash(seed * 31 + i * 7) - 0.5F) * 2 * amp;
            zx[i] = ax + (bx - ax) * f + sx * j;
            zy[i] = ay + (by - ay) * f + sy * j;
            zz[i] = az + (bz - az) * f + sz * j;
        }
    }

    private EphemeraPen.Ribbon zigRibbon(int n, float w) {
        rib.clear();
        for (int i = 0; i <= n; i++) {
            rib.add(zx[i], zy[i], zz[i], w, 1);
        }
        return rib;
    }

    private void norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        if (l < 1.0E-6F) {
            v3[0] = 0;
            v3[1] = 1;
            v3[2] = 0;
            return;
        }
        v3[0] = x / l;
        v3[1] = y / l;
        v3[2] = z / l;
    }
}
