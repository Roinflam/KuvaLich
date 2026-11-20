package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;

/**
 * 流翼：背后两扇由光带组成的翼，每侧几根翎是一串带弹簧的节点，站着缓慢开合，跑起来往后收成几道流光，
 * 起跳、落地、挥砍时靠惯性甩动；每隔几秒一道亮波从翎根走到翎尖。
 */
public final class WingStyle extends ProtoStyle<WingStyle.St> {

    // 每侧几根翎，从上往下：仰角、相对长度
    private static final float[][] ELEV = {{1.02F, 0.58F, 0.14F, -0.3F}, {0.96F, 0.4F, -0.2F}};
    private static final float[][] LEN = {{1.0F, 1.08F, 0.92F, 0.7F}, {1.0F, 1.05F, 0.78F}};
    private static final float[] BEND = {-0.35F, 0.5F, -0.85F, -0.15F};
    private static final float[] FLUT = {3.2F, 7.5F, 1.5F, 4.0F};
    private static final float[] RATE = {34, 58, 30, 30};
    private static final int KMAX = 9, CHMAX = 8;

    public static final class St extends ProtoStyle.State {
        boolean ready, lite, chainInit;
        float sc;
        int nf, k;
        final double[][] px = new double[CHMAX][KMAX], py = new double[CHMAX][KMAX], pz = new double[CHMAX][KMAX];
        final float[][] vx = new float[CHMAX][KMAX], vy = new float[CHMAX][KMAX], vz = new float[CHMAX][KMAX];
        FxParticles pt;
        FxTrail[] tips;
        float fold, air, acc, ph, landPw = 1;
    }

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(KMAX + 1);
    private final FxTrail.Sample sample = new FxTrail.Sample();
    private final double[][] qx = new double[CHMAX][KMAX], qy = new double[CHMAX][KMAX], qz = new double[CHMAX][KMAX];
    private final float[][] qw = new float[CHMAX][KMAX], qa = new float[CHMAX][KMAX];
    private final float[] dir = new float[3];

    @Override
    public float extent() {
        return 2.8F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.ready = false;
    }

    private void init(St s, FxMotion m) {
        s.ready = true;
        s.lite = m.lite;
        s.sc = m.sc;
        s.nf = ELEV[m.lite ? 1 : 0].length;
        s.k = m.lite ? 7 : 9;
        s.chainInit = false;
        s.pt = new FxParticles(m.lite ? 26 : 190);
        s.tips = new FxTrail[m.lite ? 2 : 4];
        for (int i = 0; i < s.tips.length; i++) {
            s.tips[i] = new FxTrail(0.65F, 3.2F * m.sc, 0.06F * m.sc, 3 * m.sc);
        }
        s.fold = m.sneak ? 1 : 0;
        s.air = 0;
        s.acc = 0;
        s.ph = 0;
        s.landPw = 1;
    }

    // 帧内常用量，shed / pick 要用
    private St st;
    private FxMotion mo;
    private int kind, C, hot;
    private float sc, bvX, bvZ;

    @Override
    protected void frame(St s, FxMotion m, EphemeraDraw d) {
        boolean lite = m.lite, calm = m.calm;
        if (!s.ready || s.lite != lite || Math.abs(s.sc - m.sc) > 1.0E-3F) {
            init(s, m);
        }
        st = s;
        mo = m;
        float dt = m.dt, t = (float) m.t;
        float H = m.H, W = m.W;
        sc = m.sc;
        kind = m.kind;
        C = m.color;
        hot = mix(C, WHITE, 0.62F);
        int pale = mix(C, WHITE, 0.3F), deep = mul(C, 0.2F);
        int q = lite ? 1 : 0, NF = s.nf, K = s.k, NCH = NF * 2;
        boolean sneak = m.sneak, fp = d.firstPersonSelf;
        float fwdX = m.fwdX, fwdZ = m.fwdZ, rightX = m.rightX, rightZ = m.rightZ;
        bvX = -fwdX;
        bvZ = -fwdZ;
        double bkx = m.x - fwdX * W * (sneak ? 0.57F : 0.4F), bky = m.y + H * (sneak ? 0.61F : 0.6F), bkz = m.z - fwdZ * W * (sneak ? 0.57F : 0.4F);
        double gy = m.groundY;
        float run = clamp(m.speed / 5.6F, 0, 1.1F);
        float Bk = m.burst, act = m.act;
        s.pt.thin = m.emit;

        s.fold += ((sneak ? 1 : 0) - s.fold) * (1 - (float) Math.exp(-dt * 7));
        s.air += ((m.onGround ? 0 : 1) - s.air) * (1 - (float) Math.exp(-dt * 12));
        float fold = s.fold;

        float jumpE = m.env(m.since(m.tJump), 0.02F, 0.05F, 0.14F);
        float landE = m.env(m.since(m.tLand), 0.02F, 0.05F, 0.5F);
        float hurtE = m.env(m.since(m.tHurt), 0.015F, 0.06F, 0.5F);
        float startE = m.env(m.since(m.tRunStart), 0.03F, 0.06F, 0.4F);
        float swingP = m.since(m.tSwing) / 0.3F;
        float swingB = swingP >= 0 && swingP < 1 ? (float) Math.sin(Math.PI * swingP) : 0;
        int swingSide = m.swingCount % 2 != 0 ? 1 : -1;
        // 每隔几秒一次涌动：翼收一下再张开，一道亮波从翎根走到翎尖
        float ph = (t + 1.0F) % 3.4F;
        float flap = EphemeraFx.env0(ph, 0.14F, 0.02F, 0.42F) * (calm ? m.idle : 1), wave = ph / 0.7F;

        float open = 1 + 0.06F * (float) Math.sin(t * 1.15F) + 0.28F * s.air + (0.8F * hurtE + 0.25F * startE) * Bk - 0.6F * fold - 0.42F * run
                - (calm ? 0.16F * (1 - act) : 0);
        float lift = -0.95F * jumpE - 0.6F * landE - 0.32F * flap - 0.45F * fold + 0.12F * s.air;
        float lenK = (1 - 0.42F * fold) * (1 + 0.6F * run) * (1 + (0.12F * landE + 0.25F * hurtE) * Bk) * (calm ? 0.7F + 0.12F * act : 1);
        float amp = (0.1F + 0.07F * run + 0.3F * hurtE * Bk + (kind == 1 ? 0.05F : 0)) * (calm ? 0.6F + 0.4F * act : 1);
        float wf = FLUT[kind], bend = BEND[kind] * (1 - 0.7F * Math.min(1, run)), stiff = kind == 2 ? 1.5F : 1;
        float Lb = 0.7F * H + 0.9F * W;
        double top = m.y + H + 1.1F * sc, floorY = gy + 0.06;
        float vX = m.trackX, vY = m.trackY, vZ = m.trackZ;
        float r1 = Math.min(1, run);

        // 翎是一串带弹簧的节点：每节朝「静止造型 + 迎面风」的方向追，阻尼只算相对身体的速度，
        // 所以匀速跑时造型不变，起步、急停、转弯时靠惯性甩出去再弹回来
        boolean init = !s.chainInit;
        s.chainInit = true;
        float ks0 = 240 * stiff, kd = (float) Math.exp(-(10 + 4 * (stiff - 1)) * dt);
        for (int ci = 0; ci < NCH; ci++) {
            int side = ci < NF ? -1 : 1, j = ci % NF;
            float Rx = rightX * side, Rz = rightZ * side;
            double rx = bkx + Rx * W * (0.12F + 0.09F * j), ry = bky + H * (0.14F - 0.085F * j), rz = bkz + Rz * W * (0.12F + 0.09F * j);
            float seg = Lb * LEN[q][j] * lenK / (K - 1);
            float sw = swingB > 0 && side == swingSide ? swingB : 0;
            double[] nx = s.px[ci], ny = s.py[ci], nz = s.pz[ci];
            float[] ux = s.vx[ci], uy = s.vy[ci], uz = s.vz[ci];
            nx[0] = rx;
            ny[0] = ry;
            nz[0] = rz;
            ux[0] = vX;
            uy[0] = vY;
            uz[0] = vZ;
            double prx = rx, pry = ry, prz = rz;
            for (int i = 1; i < K; i++) {
                float tt = (float) i / (K - 1);
                float el = ELEV[q][j] * open + lift + bend * tt;
                float az = 0.55F + 0.6F * tt + (0.7F - 0.4F * tt) * r1 - 0.3F * landE;
                if (sw > 0) {
                    az += (-1.9F - az) * sw * (0.45F + 0.55F * tt);
                    el += (0.1F - el) * sw;
                }
                float ca = (float) Math.cos(az), sa = (float) Math.sin(az), ce = (float) Math.cos(el), se = (float) Math.sin(el);
                float hX = Rx * ca + bvX * sa, hZ = Rz * ca + bvZ * sa;
                float sdX = -Rx * sa + bvX * ca, sdZ = -Rz * sa + bvZ * ca;
                float f1 = amp * tt * (float) Math.sin(t * wf - i * 0.8F + j * 1.7F + side);
                float f2 = amp * tt * (float) Math.sin(t * wf * 0.63F - i * 0.6F + j * 2.3F);
                float dx = hX * ce + bvX * 1.1F * run * tt + sdX * f2, dy = se + f1 - m.velY * 0.05F * tt, dz = hZ * ce + bvZ * 1.1F * run * tt + sdZ * f2;
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dl < 1.0E-6F) {
                    dl = 1;
                }
                double tx = prx + dx / dl * seg, ty = pry + dy / dl * seg, tz = prz + dz / dl * seg;
                if (init) {
                    nx[i] = tx;
                    ny[i] = ty;
                    nz[i] = tz;
                    ux[i] = vX;
                    uy[i] = vY;
                    uz[i] = vZ;
                } else {
                    float ks = ks0 * (1 - 160.0F / 240.0F * tt);
                    ux[i] += (float) (tx - nx[i]) * ks * dt;
                    uy[i] += (float) (ty - ny[i]) * ks * dt;
                    uz[i] += (float) (tz - nz[i]) * ks * dt;
                    ux[i] = vX + (ux[i] - vX) * kd;
                    uy[i] = vY + (uy[i] - vY) * kd;
                    uz[i] = vZ + (uz[i] - vZ) * kd;
                    nx[i] += ux[i] * dt;
                    ny[i] += uy[i] * dt;
                    nz[i] += uz[i] * dt;
                }
                if (ny[i] > top) {
                    ny[i] = top;
                }
                if (ny[i] < floorY) {
                    ny[i] = floorY;
                }
                double ex = nx[i] - prx, ey = ny[i] - pry, ez = nz[i] - prz, l = Math.sqrt(ex * ex + ey * ey + ez * ez);
                if (l < 1.0E-9) {
                    l = 1;
                }
                nx[i] = prx + ex * seg / l;
                ny[i] = pry + ey * seg / l;
                nz[i] = prz + ez * seg / l;
                prx = nx[i];
                pry = ny[i];
                prz = nz[i];
            }
        }

        float wF = (0.25F * H + 0.16F * W) * (lite ? 1.25F : 1) * (1 - 0.25F * fold) * (calm ? 0.8F : 1);
        float scroll = 0.5F * (1 - ((t * (kind == 2 ? 0.25F : kind == 1 ? 0.9F : 0.55F)) % 1));
        int tick = (int) Math.floor(t / 0.06F);
        float hurtFlick = hurtE > 0 ? 0.55F + 0.45F * hash(tick * 7 + 3) : 1;
        float base = (1 - 0.2F * fold) * hurtFlick;

        pen.cat = EphemeraStaging.BASE;
        // 第一人称看自己：背后的托底、翎和翎间膜都在身后或头顶，低头时会糊住脚下，一律不画
        if (!fp) {
            pen.s(SOFT).alphaBlend().rgb(0).a(0.26F).billboard(bkx + bvX * W * 0.2F, bky, bkz + bvZ * W * 0.2F, H * 1.5F, H * 1.5F);
            float g = H * (0.9F + 0.25F * flap);
            pen.s(SOFTCORE).rgb(C).a(0.09F + 0.3F * flap + 0.4F * hurtE * Bk).billboard(bkx + bvX * W * 0.25F, bky, bkz + bvZ * W * 0.25F, g, g);
        }

        // 八根翎的根挤在背心一小块地方，全按满亮度叠会糊成一片白：根部用压暗的本色，亮芯过了前三分之一才出来
        int rootC = mul(C, 0.55F);
        int texGlow = kind == 1 ? FLOW_0 : kind == 2 || kind == 3 ? FLOW_1 : PLUME;
        for (int ci = 0; ci < NCH; ci++) {
            float flick = 1, jag = 0;
            if (kind == 3) {
                flick = hash(tick * 13 + ci * 5) > 0.22F ? 1 : 0.2F;
                jag = (0.05F * H + 0.04F * W) * (hash(tick * 3 + ci) > 0.5F ? 1 : 0.35F);
            }
            double[] nx = s.px[ci], ny = s.py[ci], nz = s.pz[ci];
            for (int i = 0; i < K; i++) {
                float tt = (float) i / (K - 1);
                double x = nx[i], y = ny[i], z = nz[i];
                if (i > 0 && i < K - 1) {
                    x = 0.25 * nx[i - 1] + 0.5 * x + 0.25 * nx[i + 1];
                    y = 0.25 * ny[i - 1] + 0.5 * y + 0.25 * ny[i + 1];
                    z = 0.25 * nz[i - 1] + 0.5 * z + 0.25 * nz[i + 1];
                }
                if (jag > 0 && i > 0) {
                    float ju = (hash(tick * 31 + ci * 17 + i) - 0.5F) * 2 * jag * tt, jr = (hash(tick * 37 + ci * 11 + i) - 0.5F) * 2 * jag * tt;
                    y += ju;
                    x += rightX * jr;
                    z += rightZ * jr;
                }
                float bump = (float) Math.exp(-Math.pow((tt - wave) / 0.16F, 2));
                float w = wF * (0.3F + 0.7F * smooth(tt / 0.35F)) * (1 - (float) Math.pow(tt, 2.6)) * (1 + 0.45F * bump);
                if (kind == 1) {
                    w *= 0.85F + 0.3F * noise(t * 6 + i * 0.7F, ci);
                }
                float a = (0.1F + 0.9F * smooth((tt - 0.06F) / 0.5F)) * smooth(tt / 0.12F) * (1 - tt * tt * tt) * (1 + 1.1F * bump) * base * flick;
                qx[ci][i] = x;
                qy[ci][i] = y;
                qz[ci][i] = z;
                qw[ci][i] = w;
                qa[ci][i] = a;
            }
            if (fp) {
                continue;
            }
            fill(ci, K, 1.4F, 0.95F, 0, -1);
            pen.s(BAND).alphaBlend().rgb(deep).ribbon(rib, EphemeraPen.FACE_CAM, 0.2F, 0.8F);
            if (!lite) {
                fill(ci, K, 1, 0.3F, 0, -1);
                pen.s(BAND).rgb(C).ribbon(rib, EphemeraPen.FACE_CAM, 0.2F, 0.8F);
            }
            rib.clear();
            for (int i = 0; i < K; i++) {
                float tt = (float) i / (K - 1);
                int col = tt < 0.4F ? mix(rootC, C, tt / 0.4F) : mix(C, pale, clamp((tt - 0.6F) / 0.4F, 0, 1));
                rib.add(qx[ci][i], qy[ci][i], qz[ci][i], qw[ci][i] * 1.1F, Math.min(1, qa[ci][i] * 0.9F), col);
            }
            pen.s(texGlow).ribbon(rib, EphemeraPen.FACE_CAM, scroll, scroll + 0.5F);
            if (!lite) {
                fill(ci, K, 0.16F, 0.9F, 0.1F, 0.4F);
                pen.s(BAND).rgb(hot).ribbon(rib, EphemeraPen.FACE_CAM, 0.2F, 0.8F);
            }
        }

        // 翎和翎之间的膜，把一根根光带连成一扇
        if (!fp) {
            for (int ci = 0; ci + 1 < NCH; ci++) {
                if ((ci < NF) != (ci + 1 < NF)) {
                    continue;
                }
                int cb = ci + 1;
                for (int i = 1; i < K - 1; i++) {
                    float a0 = Math.min(qa[ci][i], qa[cb][i]), a1 = Math.min(qa[ci][i + 1], qa[cb][i + 1]);
                    float u0 = scroll + 0.5F * i / (K - 1), u1 = scroll + 0.5F * (i + 1) / (K - 1);
                    if (!lite) {
                        pen.s(BAND).alphaBlend().rgb(deep).a(0.5F).va(a0, a1, a1, a0).uv(0.2F, 0, 0.8F, 1)
                                .quad(qx[ci][i], qy[ci][i], qz[ci][i], qx[ci][i + 1], qy[ci][i + 1], qz[ci][i + 1],
                                        qx[cb][i + 1], qy[cb][i + 1], qz[cb][i + 1], qx[cb][i], qy[cb][i], qz[cb][i]);
                    }
                    pen.s(FLOW_0).rgb(C).a(calm ? 0.3F : 0.45F).va(a0, a1, a1, a0).uv4(u0, 0, u1, 0, u1, 1, u0, 1)
                            .quad(qx[ci][i], qy[ci][i], qz[ci][i], qx[ci][i + 1], qy[ci][i + 1], qz[ci][i + 1],
                                    qx[cb][i + 1], qy[cb][i + 1], qz[cb][i + 1], qx[cb][i], qy[cb][i], qz[cb][i]);
                }
            }
        }

        // 每侧最上面的翎尖在世界里拖一条尾：留在经过的地方慢慢收细，转弯时自己成弧
        pen.cat = EphemeraStaging.TRAIL;
        for (int k = 0; k < s.tips.length; k++) {
            int ci = (k % 2) * NF + (k >> 1);
            FxTrail tr = s.tips[k];
            tr.push(s.px[ci][K - 1], s.py[ci][K - 1], s.pz[ci][K - 1], t);
            if (fp || tr.length() < 0.25F * sc) {
                continue;
            }
            int sg = lite ? 5 : 8;
            float wT = wF * (k < 2 ? 0.6F : 0.42F) * (1 - 0.3F * fold);
            final float fb = base;
            tr.sample(sg, sample);
            pen.s(BAND).alphaBlend().rgb(deep);
            FxTrail.draw(pen, sample, rib, (x, i) -> tipW(wT, x) * 1.35F, (x, i) -> 0.9F * tipA(fb, x), null, m.trailW, m.trailA,
                    EphemeraPen.FACE_CAM, 0.2F, 0.8F);
            pen.s(texGlow).rgb(C);
            FxTrail.draw(pen, sample, rib, (x, i) -> tipW(wT, x), (x, i) -> tipA(fb, x), null, m.trailW, m.trailA,
                    EphemeraPen.FACE_CAM, scroll, scroll + 0.5F);
            if (!lite) {
                pen.s(BAND).rgb(hot);
                FxTrail.draw(pen, sample, rib, (x, i) -> tipW(wT, x) * 0.2F, (x, i) -> 0.9F * tipA(fb, x) * (1 - x), null, m.trailW, m.trailA,
                        EphemeraPen.FACE_CAM, 0.2F, 0.8F);
            }
        }
        pen.cat = EphemeraStaging.BASE;

        // 电：相邻两根翎之间跳电弧
        if (kind == 3 && !fp) {
            int arcs = lite ? 2 : 5;
            for (int k = 0; k < arcs; k++) {
                float h0 = hash(tick * 53 + k * 7);
                if (h0 > 0.55F) {
                    continue;
                }
                int ci = (int) Math.floor(hash(tick * 59 + k) * (NCH - 1)), cb = ci + 1;
                if ((ci < NF) != (cb < NF)) {
                    continue;
                }
                int i = 2 + (int) Math.floor(hash(tick * 61 + k) * (K - 3)), i2 = Math.min(K - 1, i + (hash(tick * 67 + k) > 0.5F ? 1 : 0));
                double ax = qx[ci][i], ay = qy[ci][i], az = qz[ci][i], bx = qx[cb][i2], by = qy[cb][i2], bz = qz[cb][i2];
                float len = (float) Math.sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz));
                int spr = h0 > 0.27F ? CRACK_0 : CRACK_1;
                // 细折线在亮背景上要靠底下一道暗色托住
                if (!lite) {
                    pen.s(spr).alphaBlend().rgb(mul(C, 0.38F)).a(0.6F * base).beam(ax, ay, az, bx, by, bz, len * 0.7F);
                }
                pen.s(spr).rgb(hot).a(0.95F * base).beam(ax, ay, az, bx, by, bz, len * 0.55F);
                pen.s(FLARE).rgb(WHITE).a(0.8F * base).billboard(bx, by, bz, 0.22F * sc, 0.22F * sc);
            }
        }

        // 粒子：从翎上抖落
        float rate = RATE[kind] * (lite ? 0.2F : 1) * (1 - 0.7F * fold) * (0.7F + 0.5F * run) * (calm ? 0.5F + 0.5F * act : 1);
        if (kind == 3) {
            rate *= noise(t * 3.1F, 5) > 0.45F ? 1.8F : 0.15F;
        }
        s.acc += dt * rate;
        while (s.acc >= 1) {
            s.acc -= 1;
            int ci = (int) (rnd() * NCH), i = 2 + (int) (rnd() * (K - 2));
            shed(ci, Math.min(K - 1, i), 1);
        }
        // 亮波走到翎尖时抖出一把
        if (s.ph < 0.62F && ph >= 0.62F) {
            for (int ci = 0; ci < NCH; ci++) {
                for (int k = 0; k < (lite || calm ? 1 : 3); k++) {
                    shed(ci, K - 1 - (k % 2), 2.2F);
                }
            }
        }
        s.ph = ph;

        FxParticles P = s.pt;
        if (m.justRunStart) {
            for (int k = 0; k < (lite ? 6 : 18); k++) {
                EphemeraFx.randDir(dir);
                double ox = bkx + dir[0] * 0.3F * W, oy = bky + dir[1] * 0.3F * W, oz = bkz + dir[2] * 0.3F * W;
                EphemeraFx.randDir(dir);
                float sp = rnd(2, 5) * sc;
                P.spec().pos(ox, oy, oz).vel(bvX * sp + dir[0] * 1.2F * sc, dir[1] * 1.2F * sc, bvZ * sp + dir[2] * 1.2F * sc).life(rnd(0.3F, 0.6F))
                        .size(0.1F * sc, 0.03F * sc).sprite(STREAK).rgb(hot).stretch(0.09F).drag(2.5F).force().spawn();
            }
        }
        if (startE > 0 && !fp) {
            float kk = 1 - startE, g = H * (0.5F + 1.1F * kk * Bk), off = W * (0.3F + 1.2F * kk * Bk);
            pen.s(SOFTRING).rgb(pale).a(0.8F * startE).billboard(bkx + bvX * off, bky, bkz + bvZ * off, g, g);
        }
        if (m.justStop) {
            for (int k = 0; k < (lite ? 6 : 16); k++) {
                shed(k % NCH, K - 1 - (k % 3), 2.5F);
            }
        }
        if (m.justJump) {
            for (int k = 0; k < (lite ? 5 : 12); k++) {
                float a = k / 12.0F * TAU;
                P.spec().pos(m.x, gy + 0.08F * sc, m.z).vel((float) Math.cos(a) * 1.6F * sc, 0.3F * sc, (float) Math.sin(a) * 1.6F * sc).life(0.45F)
                        .size(0.22F * sc, 0.4F * sc).sprite(SOFT).rgb(C).a(0.5F).drag(3).force().spawn();
            }
        }
        if (jumpE > 0) {
            float r = W * (1.6F + 2.2F * (1 - jumpE) * Bk);
            pen.s(SOFTRING).rgb(pale).a(0.7F * jumpE).flat(m.x, gy + 0.04, m.z, r, r);
        }
        if (m.justLand) {
            float pw = clamp(m.landSpeed / (8 * (float) Math.sqrt(sc)), 0.7F, 1.5F);
            for (int k = 0; k < (lite ? 6 : 14); k++) {
                float a = (k + rnd()) / 14 * TAU;
                float lvx = (float) Math.cos(a) * 2.6F * sc * pw, lvz = (float) Math.sin(a) * 2.6F * sc * pw, lvy = 0.5F * sc;
                FxParticles.Spec sp = P.spec().pos(m.x + lvx * 0.08F, gy + 0.1F * sc + lvy * 0.08F, m.z + lvz * 0.08F).vel(lvx, lvy, lvz)
                        .life(rnd(0.45F, 0.7F)).size(0.3F * sc, 0.6F * sc).sprite(SMOKE_0 + (k % 4)).rgb(k % 2 != 0 ? deep : C)
                        .a(k % 2 != 0 ? 0.45F : 0.4F).drag(3.2F).rot(a).fade(0.05F, 0.7F).force();
                if (k % 2 != 0) {
                    sp.alphaBlend();
                }
                sp.spawn();
            }
            s.landPw = pw;
        }
        if (landE > 0) {
            float kk = Math.min(1, m.since(m.tLand) / (0.5F * m.burstTime)), e = 1 - (1 - kk) * (1 - kk);
            float r = Math.min(4.8F * sc, W * 1.4F + 3.6F * sc * s.landPw * e * Bk);
            pen.s(SOFT).alphaBlend().rgb(0).a(0.3F * landE).flat(m.x, gy + 0.03, m.z, r * 0.9F, r * 0.9F);
            pen.s(SHOCK).alphaBlend().rgb(deep).a(0.8F * landE).flat(m.x, gy + 0.04, m.z, r * 1.02F, r * 1.02F);
            pen.s(SHOCK).rgb(hot).a(0.95F * landE).flat(m.x, gy + 0.045, m.z, r, r);
            pen.s(SOFTRING).rgb(C).a(0.6F * landE).flat(m.x, gy + 0.05, m.z, r * 0.72F, r * 0.72F);
        }

        // 挥砍：身前一道平着扫过去的月牙，和甩到前面的那扇翼接上；第一人称就在眼前，不画
        float sinceSw = m.since(m.tSwing) / m.burstTime;
        if (sinceSw < 0.45F && sinceSw >= 0 && !fp) {
            float kk = Math.min(1, sinceSw / 0.3F), e = smooth(kk), fade = 1 - smooth((sinceSw - 0.18F) / 0.27F);
            float ang = swingSide * (-1.0F + 2.0F * e);
            float fwx = fwdX * (float) Math.cos(ang) + rightX * (float) Math.sin(ang), fwz = fwdZ * (float) Math.cos(ang) + rightZ * (float) Math.sin(ang);
            float tlx = -fwz, tly = -0.85F * swingSide, tlz = fwx, tl = (float) Math.sqrt(tlx * tlx + tly * tly + tlz * tlz);
            tlx /= tl;
            tly /= tl;
            tlz /= tl;
            double cx = m.x + fwx * (0.1F + 0.25F * e) * H, cy = m.y + 0.555F * H + 0.06F * H, cz = m.z + fwz * (0.1F + 0.25F * e) * H;
            float hs = (0.58F * H + 0.6F * W) * (calm ? 0.72F : 1);
            crescent(cx, cy, cz, tlx, tly, tlz, fwx, fwz, hs * 1.05F, deep, 0.75F * fade, false);
            crescent(cx, cy, cz, tlx, tly, tlz, fwx, fwz, hs, C, fade, true);
            crescent(cx, cy, cz, tlx, tly, tlz, fwx, fwz, hs * 0.93F, hot, 0.9F * fade, true);
            if (m.justSwing) {
                for (int i = 0; i < (lite ? 4 : 10); i++) {
                    EphemeraFx.randDir(dir);
                    float a = swingSide * rnd(2, 5) * sc, b = rnd(1, 3) * sc;
                    P.spec().pos(cx + fwx * hs * 0.6F + dir[0] * 0.2F * sc, cy + dir[1] * 0.2F * sc, cz + fwz * hs * 0.6F + dir[2] * 0.2F * sc)
                            .vel(tlx * a + fwx * b, tly * a, tlz * a + fwz * b).life(rnd(0.2F, 0.4F)).size(0.08F * sc, 0.02F * sc)
                            .sprite(STREAK).rgb(hot).stretch(0.08F).drag(3).force().spawn();
                }
            }
        }
        if (m.justHurt) {
            for (int k = 0; k < (lite ? 8 : 30); k++) {
                shed((int) (rnd() * NCH), 2 + (int) (rnd() * (K - 2)), calm ? 3 : 5);
            }
        }
        if (hurtE > 0 && !fp) {
            float g = H * 1.9F * hurtE * Bk;
            pen.s(FLARE).rgb(WHITE).a(0.8F * hurtE).billboard(bkx, bky, bkz, g, g);
        }

        P.step(dt, null);
        pen.cat = EphemeraStaging.PARTICLE;
        P.draw(pen, m.size, 1, Float.NaN);
        st = null;
        mo = null;
    }

    private static float tipW(float wT, float x) {
        return wT * Math.min(1, 0.35F + x * 5) * (float) Math.pow(1 - x, 0.7);
    }

    private static float tipA(float base, float x) {
        return (float) Math.pow(1 - x, 0.8) * base;
    }

    /** 把第 ci 根翎的点抄进丝带缓冲；aFrom 大于等于 0 时透明度再乘 smooth((tt − aFrom) / aSpan) */
    private void fill(int ci, int K, float wk, float ak, float aFrom, float aSpan) {
        rib.clear();
        for (int i = 0; i < K; i++) {
            float a = Math.min(1, qa[ci][i] * ak);
            if (aSpan > 0) {
                a *= smooth(((float) i / (K - 1) - aFrom) / aSpan);
            }
            rib.add(qx[ci][i], qy[ci][i], qz[ci][i], qw[ci][i] * wk, a);
        }
    }

    private void crescent(double cx, double cy, double cz, float tx, float ty, float tz, float fx, float fz, float hs,
                          int rgb, float a, boolean add) {
        pen.s(CRESCENT).rgb(rgb).a(a);
        if (!add) {
            pen.alphaBlend();
        }
        pen.quad(cx - tx * hs - fx * hs, cy - ty * hs, cz - tz * hs - fz * hs,
                cx + tx * hs - fx * hs, cy + ty * hs, cz + tz * hs - fz * hs,
                cx + tx * hs + fx * hs, cy + ty * hs, cz + tz * hs + fz * hs,
                cx - tx * hs + fx * hs, cy - ty * hs, cz - tz * hs + fz * hs);
    }

    private void shed(int ci, int i, float boost) {
        St s = st;
        FxMotion m = mo;
        EphemeraFx.randDir(dir);
        double x = s.px[ci][i] + dir[0] * 0.06F * sc, y = s.py[ci][i] + dir[1] * 0.06F * sc, z = s.pz[ci][i] + dir[2] * 0.06F * sc;
        float nvx = s.vx[ci][i] * 0.35F, nvy = s.vy[ci][i] * 0.35F, nvz = s.vz[ci][i] * 0.35F;
        EphemeraFx.randDir(dir);
        FxParticles P = s.pt;
        double floor = m.groundY + 0.02;
        if (kind == 1) {
            if (rnd() < 0.6F) {
                float up = rnd(0.6F, 1.2F) * sc, k = 0.25F * sc * boost;
                P.spec().pos(x, y, z).vel(nvx + dir[0] * k, nvy + up + dir[1] * k, nvz + dir[2] * k).life(rnd(0.35F, 0.6F))
                        .size(rnd(0.2F, 0.3F) * sc, 0.5F * sc).sprite(SMOKE_0 + (int) (rnd() * 4)).rgb(C).a(0.3F).grav(-1.8F * sc).drag(1.2F)
                        .rot(rnd(-0.3F, 0.3F)).fade(0.15F, 0.6F).spawn();
            } else {
                float up = rnd(1.0F, 2.2F) * sc, k = 0.7F * sc * boost;
                P.spec().pos(x, y, z).vel(nvx + dir[0] * k, nvy + up + dir[1] * k, nvz + dir[2] * k).life(rnd(0.6F, 1.1F))
                        .size(0.07F * sc, 0.02F * sc).sprite(SOFTCORE).rgb(hot).grav(-1.2F * sc).drag(1.0F).stretch(0.05F).spawn();
            }
        } else if (kind == 2) {
            if (rnd() < 0.7F) {
                float k = 0.5F * sc * boost;
                P.spec().pos(x, y, z).vel(nvx + dir[0] * k, nvy + dir[1] * k, nvz + dir[2] * k).life(rnd(0.8F, 1.3F))
                        .size(rnd(0.09F, 0.17F) * sc, 0.05F * sc).sprite(CRYSTAL).rgb(mix(C, WHITE, 0.55F)).grav(5.5F * sc).drag(0.6F)
                        .rot(rnd(0, 6.28F)).spin(rnd(-4, 4)).floor(floor).fade(0.05F, 0.3F).spawn();
            } else {
                float k = 0.15F * sc;
                P.spec().pos(x, y, z).vel(nvx + dir[0] * k, nvy - 0.35F * sc + dir[1] * k, nvz + dir[2] * k).life(rnd(1.0F, 1.5F))
                        .size(0.3F * sc, 0.65F * sc).sprite(SMOKE_0 + (int) (rnd() * 4)).alphaBlend().rgb(mix(C, WHITE, 0.6F)).a(0.22F)
                        .grav(0.5F * sc).drag(1.5F).floor(floor).rot(rnd(0, 6.28F)).fade(0.25F, 0.6F).spawn();
            }
        } else if (kind == 3) {
            float k = rnd(2.5F, 6) * sc * boost;
            P.spec().pos(x, y, z).vel(dir[0] * k, dir[1] * k, dir[2] * k).life(rnd(0.1F, 0.24F)).size(0.06F * sc, 0.03F * sc)
                    .sprite(STREAK).rgb(hot).stretch(0.07F).drag(3).fade(0.02F, 0.5F).spawn();
        } else {
            float k = 0.5F * sc * boost;
            P.spec().pos(x, y, z).vel(nvx + dir[0] * k + bvX * 0.3F * sc, nvy + dir[1] * k, nvz + dir[2] * k + bvZ * 0.3F * sc)
                    .life(rnd(0.6F, 1.1F)).size(rnd(0.07F, 0.13F) * sc, 0.02F * sc).sprite(SOFTCORE).rgb(rnd() < 0.3F ? hot : C).drag(1.6F)
                    .grav(-0.25F * sc).spawn();
        }
    }
}
