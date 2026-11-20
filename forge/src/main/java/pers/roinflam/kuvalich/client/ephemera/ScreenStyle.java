package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.clamp;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraSprites.*;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 数幕：身后从腰到头顶错开叠着五层横向的全息数据屏，屏是一段圆心在身前的大弧，微微弯着环住后背。
 * 楼群般起落的数据柱、滚动的格子行、一行数据字、一道示波线；屏框两头方括号，上沿有数据包流过；
 * 身上不时往两侧射出细直光线，端头挂一枚数据标签。跑动各层往后拉开、两头拖光痕，起跳层距拉开、落地压低，
 * 挥砍数据顶满、屏面外扩、亮光沿上沿往两头冲，受击屏面撕裂错位。
 *
 * <p>画的顺序：屏主体 → 元素招牌 → 元素粒子和爆发 → 光线、碎屑按剩下的面片数让。</p>
 */
public final class ScreenStyle extends ProtoStyle<ScreenStyle.St> {

    static final int TEX_SKY = EphemeraSprites.file("screen/sky", true);
    static final int TEX_TICKER = EphemeraSprites.file("screen/ticker", true);
    static final int TEX_TEXT = EphemeraSprites.file("screen/text", true);
    static final int TEX_VGLOW = EphemeraSprites.file("screen/vglow");

    private static final int K_TEXT = 0, K_SKY = 1, K_COLS = 2, K_GRID = 3, K_WAVE = 4;
    // y、h 按身高，back、hw、off 按体宽；下标就是层的种类
    private static final float[] Y = {1.11F, 0.9F, 0.74F, 0.6F, 0.47F};
    private static final float[] BACK = {1.25F, 0.95F, 1.2F, 1.45F, 1.05F};
    private static final float[] HW = {1.3F, 2.3F, 1.7F, 2.5F, 1.4F};
    private static final float[] OFF = {0.3F, -0.05F, 0.35F, -0.3F, 0.15F};
    private static final float[] LH = {0.075F, 0.2F, 0.16F, 0.14F, 0.085F};
    private static final int[] DIR = {1, 1, -1, -1, 1};
    private static final int[] NSEG = {6, 8, 8, 8, 6};
    private static final int[] ORDER_FULL = {0, 1, 2, 3, 4}, ORDER_LITE = {1, 2, 3};
    /**
     * 组的弹簧按相对宿主阻尼，匀速跟跑时不落后；照定稿要留的那点后拖是 0.124 − 0.09 倍速度
     * （绝对阻尼 2·0.62/10 的滞后减去目标点往前补的 0.09），直接挪到目标点上。
     */
    private static final float LAG = 2 * 0.62F / 10 - 0.09F;
    private static final float PI = (float) Math.PI;

    public static final class St extends ProtoStyle.State {
        final ElementMotif fx = new ElementMotif();
        final FxKit.Spring c = new FxKit.Spring();
        FxParticles pt;
        boolean lite;
        float acc, sw;
        double scr;
    }

    private static final class Lay {
        final int i;
        int n;
        boolean small;
        final double[] tx = new double[9], ty = new double[9], tz = new double[9];
        final double[] bx = new double[9], by = new double[9], bz = new double[9];
        final float[] fa = new float[9];
        float a0, a1, ac, ah, yc, h, rb, la;

        Lay(int i) {
            this.i = i;
        }

        float ang(float f) {
            return a0 + (a1 - a0) * f;
        }
    }

    private final Lay[] lays = {new Lay(0), new Lay(1), new Lay(2), new Lay(3), new Lay(4)};
    private final Lay[] geo = new Lay[5];
    private int ng;
    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(32);
    private final EphemeraPen.Ribbon rib2 = new EphemeraPen.Ribbon(32);
    private final ElementMotif.Sites sites = new ElementMotif.Sites();
    private final double[] zx = new double[8], zy = new double[8], zz = new double[8];
    private final double[] bzX = new double[10], bzY = new double[10], bzZ = new double[10];

    // 这一帧
    private FxMotion m;
    private EphemeraStaging out;
    private boolean lite;
    private double cX, cY, cZ;
    private float fwX, fwZ, rtX, rtZ, rc, H, W;
    private double px, py, pz;
    private double sdX, sdY, sdZ;

    @Override
    public float extent() {
        return 3.4F;
    }

    @Override
    protected St newState() {
        return new St();
    }

    @Override
    protected void reset(St s, FxMotion m) {
        s.fx.reset();
        s.c.reset();
        s.pt = null;
    }

    private static double fr(double x) {
        return x - Math.floor(x);
    }

    // 每根冰棱、每道光线的固定随机数，不随帧变
    private static float fixed(int i) {
        double v = Math.sin(i * 127.1 + 31.7) * 43758.5;
        return (float) (v - Math.floor(v));
    }

    /** 屏弧上的点：ang 是弧上的角度，y 是离组底的高度 */
    private void at(Lay g, double ang, double y) {
        double f = rc - g.rb - Math.cos(ang) * rc, s = Math.sin(ang) * rc;
        px = cX + fwX * f + rtX * s;
        py = cY + y;
        pz = cZ + fwZ * f + rtZ * s;
    }

    /** 从背后看时屏正好横在头和上半身前面：这一块按离人体轴线的远近挖淡 */
    private float hole(double x, double y, double z) {
        double hy = y - m.y;
        if (hy < 0.3F * H || hy > 1.12F * H) {
            return 1;
        }
        double tx = pen.camX() - m.x, ty = pen.camY() - y, tz = pen.camZ() - m.z;
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tl < 1.0E-6) {
            return 1;
        }
        tx /= tl;
        ty /= tl;
        tz /= tl;
        double dx = x - m.x, dz = z - m.z;
        double along = dx * tx + dz * tz;
        if (along <= 0) {
            return 1;
        }
        double qx = dx - tx * along, qy = -ty * along, qz = dz - tz * along;
        double k = Math.sqrt(qx * qx + qy * qy + qz * qz) / (0.72F * W);
        return (float) (1 - 0.85F * Math.min(1, along / (0.3F * W)) * Math.exp(-k * k));
    }

    private float holeAt() {
        return hole(px, py, pz);
    }

    /** 第 k 段屏面，上沿往上挪 up、下沿往下挪 dn；笔刷先设好 */
    private void band(Lay g, int k, double up, double dn) {
        pen.quad(g.tx[k], g.ty[k] + up, g.tz[k], g.tx[k + 1], g.ty[k + 1] + up, g.tz[k + 1],
                g.bx[k + 1], g.by[k + 1] - dn, g.bz[k + 1], g.bx[k], g.by[k] - dn, g.bz[k]);
    }

    /** 贴图横向画了两个周期，每段屏占 0.25，滚动时段与段首尾接得上 */
    private void strip(Lay g, int sprite, int rgb, float a, double off, double dy) {
        for (int k = 0; k < g.n; k++) {
            float u0 = (float) (fr((off + 0.25 * k) / 0.5) * 0.5), u1 = u0 + 0.25F;
            pen.s(sprite).rgb(rgb).a(a).uv4(u0, 0, u1, 0, u1, 1, u0, 1).va(g.fa[k], g.fa[k + 1], g.fa[k + 1], g.fa[k]);
            band(g, k, dy, -dy);
        }
    }

    /** 上沿（或下沿）连成一条丝带 */
    private void edgeRibbon(Lay g, boolean top, float w) {
        rib.clear();
        for (int k = 0; k <= g.n; k++) {
            if (top) {
                rib.add(g.tx[k], g.ty[k], g.tz[k], w, g.fa[k]);
            } else {
                rib.add(g.bx[k], g.by[k], g.bz[k], w, g.fa[k]);
            }
        }
    }

    /** 上沿中间那几个点（挖得太淡的不算），给元素沿轮廓点缀 */
    private EphemeraPen.Ribbon rail(Lay g) {
        rib.clear();
        for (int k = 1; k < g.n; k++) {
            if (g.fa[k] > 0.6F) {
                rib.add(g.tx[k], g.ty[k], g.tz[k], 1, 1);
            }
        }
        return rib;
    }

    private Lay find(int kind) {
        for (int j = 0; j < ng; j++) {
            if (geo[j].i == kind) {
                return geo[j];
            }
        }
        return null;
    }

    /** 这一帧还剩多少面片：完整档按整个幻纹 600 算，精简档也按 150 算（定稿的光线、碎屑就是按这个让的） */
    private int left() {
        return (lite ? EphemeraTier.CAP_REDUCED : EphemeraTier.CAP_FULL) - out.pending();
    }

    /** dir 在镜头里的侧向：dir × 视线 */
    private void sideOf(double x, double y, double z, double dx, double dy, double dz) {
        double vx = x - pen.camX(), vy = y - pen.camY(), vz = z - pen.camZ();
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1.0E-6) {
            sdX = pen.rightX;
            sdY = pen.rightY;
            sdZ = pen.rightZ;
        } else {
            sdX = sx / l;
            sdY = sy / l;
            sdZ = sz / l;
        }
    }

    /** 菱形片：根到尖，最宽处在 mid；笔刷先设好 */
    private void kite(double ax, double ay, double az, double tx, double ty, double tz, float w, float mid) {
        double dx = tx - ax, dy = ty - ay, dz = tz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) {
            l = 1;
        }
        sideOf(ax, ay, az, dx / l, dy / l, dz / l);
        double mx = ax + dx * mid, my = ay + dy * mid, mz = az + dz * mid;
        double sx = sdX * w / 2, sy = sdY * w / 2, sz = sdZ * w / 2;
        pen.quad(tx, ty, tz, mx + sx, my + sy, mz + sz, ax, ay, az, mx - sx, my - sy, mz - sz);
    }

    /** a 到 b 的折线，中间各点沿 (sx, sy, sz) 随机偏 ±amp；和元素母题里的电弧同一个算法 */
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

    @Override
    protected void frame(St s, FxMotion mo, EphemeraDraw d) {
        m = mo;
        lite = mo.lite;
        out = d.out;
        if (s.pt == null || s.lite != lite) {
            s.lite = lite;
            s.pt = new FxParticles(lite ? 24 : 50);
            s.acc = 0;
            s.scr = 0;
            s.sw = 0;
        }
        ElementMotif fx = s.fx;
        fx.begin(pen, mo);
        float dt = Math.min(mo.dt, 1 / 30.0F), t = (float) mo.t;
        H = mo.H;
        W = mo.W;
        float sc = mo.sc, act = mo.act, amp = mo.amp, Bk = mo.burst;
        int el = mo.el;
        int C = fx.C, hot = fx.hot, pale = fx.pale, deep = fx.deep;
        float T = mo.clock(), bt = mo.beat(0), fk = mo.flick(7);
        float run = clamp(mo.speed / 5.6F, 0, 1);
        boolean sneak = mo.sneak, air = !mo.onGround;
        float swS = mo.since(mo.tSwing), swE = mo.env(swS, 0.03F, 0.08F, 0.4F);
        float hurtE = mo.env(mo.since(mo.tHurt), 0.02F, 0.15F, 0.45F);
        float jumpE = mo.env(mo.since(mo.tJump), 0.04F, 0.12F, 0.4F);
        float landS = mo.since(mo.tLand), landE = mo.env(landS, 0.02F, 0.06F, 0.35F);
        float breath = 0.5F + 0.5F * (float) Math.sin(mo.t * 1.5);
        // 近白的颜色（冲击默认色）柔光一叠就是一片白，按亮度压
        float luma = (0.3F * ((C >> 16) & 255) + 0.59F * ((C >> 8) & 255) + 0.11F * (C & 255)) / 255.0F;
        float gK = clamp(0.55F / Math.max(luma, 0.01F), 0.4F, 1);

        // 滚动：冰慢、毒稍慢、电按格跳；跑、挥砍时加速
        float spdK = el == ICE ? 0.45F : el == POISON ? 0.6F : 1;
        s.scr += dt * spdK * (0.08F + 0.05F * act + 0.4F * run + 1.4F * swE + (air ? 0.15F : 0));
        double scr = el == ELECTRIC ? Math.floor(s.scr / 0.03) * 0.03 : s.scr;

        s.c.step(mo.x - mo.velX * LAG, mo.y, mo.z - mo.velZ * LAG, mo, 10, 0.62F);
        if (mo.justHurt) {
            s.c.vx -= mo.fwdX * 1.6F * sc;
            s.c.vz -= mo.fwdZ * 1.6F * sc;
        }
        cX = s.c.x;
        cY = s.c.y;
        cZ = s.c.z;
        // 转身时屏组慢半拍跟着转
        s.sw += (clamp(-mo.turn * 0.12F, -0.35F, 0.35F) - s.sw) * (1 - (float) Math.exp(-dt * 5));
        float cs = (float) Math.cos(s.sw), sn = (float) Math.sin(s.sw);
        fwX = mo.fwdX * cs + mo.rightX * sn;
        fwZ = mo.fwdZ * cs + mo.rightZ * sn;
        rtX = mo.rightX * cs - mo.fwdX * sn;
        rtZ = mo.rightZ * cs - mo.fwdZ * sn;

        float A = (0.55F + 0.45F * act + 0.06F * breath * amp) * fk;
        if (hurtE > 0.05F) {
            A *= 0.5F + 0.5F * hash((int) Math.floor(mo.t * 25));
        }
        float spread = (1 + 0.22F * run + 0.35F * jumpE + (air ? 0.12F : 0) - 0.3F * landE + 0.15F * swE * Bk) * (sneak ? 0.85F : 1);
        rc = W * 4.3F * (1 - 0.1F * run);
        float magPull = el == MAGNETIC ? 0.07F * (0.5F + 0.5F * (float) Math.sin(T * 1.6F)) : 0;
        int[] order = lite ? ORDER_LITE : ORDER_FULL;

        pen.cat = EphemeraStaging.BASE;
        // 整组屏后面一团正对镜头的柔光，远处看是一整块
        pen.s(SOFT).rgb(C).a((0.09F + 0.03F * breath * amp + 0.12F * swE) * gK * A)
                .billboard(cX - fwX * W * 1.2F, cY + H * 0.8F, cZ - fwZ * W * 1.2F, W * 5.5F * (1 + 0.15F * swE), H * 0.95F);

        ng = 0;
        sites.clear();
        for (int i : order) {
            Lay g = lays[i];
            geo[ng++] = g;
            int n = lite ? 6 : NSEG[i];
            boolean small = i == K_TEXT || i == K_WAVE;
            g.n = n;
            g.small = small;
            float LA = A * mo.blink(3 + i) * (i == K_SKY ? 1 : 0.88F);
            g.la = LA;
            g.rb = W * BACK[i] * (1 + 0.32F * run * (0.4F + 0.2F * i) + 0.1F * bt + 0.2F * swE * Bk + 0.12F * hurtE);
            float yc = H * (0.76F + (Y[i] - 0.76F) * spread - (sneak ? 0.07F : 0)) + H * 0.012F * (float) Math.sin(mo.t * 1.2 + i * 1.9) * amp
                    - H * 0.035F * landE * (1.2F - 0.2F * i);
            float h = H * LH[i] * (1 + 0.35F * swE * Bk + 0.08F * bt + 0.03F * breath * amp);
            float hw = W * HW[i] * (1 + 0.18F * swE * Bk + 0.07F * bt + 0.08F * run - magPull);
            float jit = hurtE > 0.02F ? (hash((int) Math.floor(mo.t / 0.05) * 7 + i) - 0.5F) * 0.8F * hurtE * W : 0;
            g.yc = yc;
            g.h = h;
            g.ac = (OFF[i] * W + jit) / rc;
            g.ah = hw / rc;
            g.a0 = g.ac - g.ah;
            g.a1 = g.ac + g.ah;
            for (int k = 0; k <= n; k++) {
                float a = g.ang((float) k / n);
                // 受击时屏面一段段上下错开，像信号撕裂
                float ty = hurtE > 0.05F ? (hash((int) Math.floor(mo.t / 0.06) * 11 + k + i * 17) - 0.5F) * h * 0.7F * hurtE : 0;
                // 毒：屏的下沿中间往下坠
                float sag = el == POISON ? h * 0.45F * (float) Math.sin(PI * k / n) * (0.8F + 0.2F * (float) Math.sin(T * 1.4F + i)) : 0;
                at(g, a, yc + h / 2 + ty);
                g.tx[k] = px;
                g.ty[k] = py;
                g.tz[k] = pz;
                at(g, a, yc - h / 2 + ty - sag);
                g.bx[k] = px;
                g.by[k] = py;
                g.bz[k] = pz;
                at(g, a, yc + ty);
                g.fa[k] = (k == 0 || k == n ? 0.45F : 1) * holeAt();
            }

            // ---- 柔光托底（大屏才有）+ 暗底 ----
            if (!small && (!lite || i == K_SKY)) {
                for (int k = 0; k < n; k++) {
                    pen.s(TEX_VGLOW).rgb(C).a((0.22F + 0.06F * breath * amp + 0.25F * swE) * gK * LA).va(g.fa[k], g.fa[k + 1], g.fa[k + 1], g.fa[k]);
                    band(g, k, h * 0.85F, h * 0.85F);
                }
            }
            for (int k = 0; k < n; k++) {
                pen.alphaBlend().rgb(deep).a(0.42F * (0.75F + 0.25F * act)).va(g.fa[k], g.fa[k + 1], g.fa[k + 1], g.fa[k]);
                band(g, k, h * 0.12F, h * 0.12F);
            }

            // ---- 屏面内容 ----
            double uOff = scr * DIR[i] * (i == K_TEXT ? 0.6 : 0.35) + i * 0.13;
            float cA = 1.05F * LA * (1 + 0.6F * swE);
            int cC = mix(C, hot, 0.3F);
            if (i == K_SKY) {
                strip(g, TEX_SKY, cC, cA, uOff, 0);
                if (!lite) {
                    strip(g, TEX_TICKER, hot, 0.3F * cA, uOff * 1.7 + 0.21, 0);
                }
            } else if (i == K_GRID) {
                strip(g, TEX_TICKER, cC, cA, uOff, 0);
            } else if (i == K_TEXT) {
                strip(g, TEX_TEXT, hot, 0.85F * cA, uOff, 0);
            } else if (i == K_COLS) {
                // 柱状数据：柱子跟着滚动横移，高度按噪声起落，挥砍时一齐顶满
                int nc = lite ? 7 : 12;
                double shift = scr * DIR[i] * 5;
                int base = (int) Math.floor(shift);
                float sf = (float) (shift - base);
                for (int j = 0; j < nc; j++) {
                    float pos = (j + sf) / nc;
                    int id = j - base;
                    float ff = Math.min(1, Math.min(pos, 1 - pos) * nc / 1.5F + 0.1F);
                    float v = 0.15F + 0.85F * noise(T * 1.4F + id * 0.61F, 20 + i);
                    float hh = Math.min(1, v + 0.95F * swE * (0.6F + 0.4F * hash(id * 3))) * h * 0.92F;
                    float da = (g.a1 - g.a0) / nc * 0.32F, am = g.ang(pos);
                    float y0 = yc - h / 2 + h * 0.04F;
                    at(g, am, yc);
                    float hA = ff * holeAt();
                    at(g, am - da, y0 + hh);
                    double q0x = px, q0y = py, q0z = pz;
                    at(g, am + da, y0 + hh);
                    double q1x = px, q1y = py, q1z = pz;
                    at(g, am + da, y0);
                    double q2x = px, q2y = py, q2z = pz;
                    at(g, am - da, y0);
                    pen.rgb(v > 0.75F ? hot : C).a(0.8F * cA * hA).va(1, 1, 0.35F, 0.35F).quad(q0x, q0y, q0z, q1x, q1y, q1z, q2x, q2y, q2z, px, py, pz);
                    if (!lite) {
                        double up = h * 0.07F;
                        pen.rgb(WHITE).a(0.7F * cA * hA).quad(q0x, q0y + up, q0z, q1x, q1y + up, q1z, q1x, q1y, q1z, q0x, q0y, q0z);
                    }
                    if (el == FIRE && !lite && v > 0.55F) {
                        at(g, am, y0 + hh);
                        fx.accent(px, py, pz, 0, 1, 0, h * 0.9F, h * 0.28F, 200 + j, 0.75F * cA * hA);
                    }
                }
            } else {
                // 示波线：几道正弦叠起来往一边跑，电击时折成锯齿
                final int NW = 16;
                rib.clear();
                for (int k = 0; k <= NW; k++) {
                    float f = (float) k / NW;
                    float w = (float) (0.55 * Math.sin(f * 15 - scr * 40 * DIR[i]) + 0.35 * Math.sin(f * 37 + T * 3.1F)
                            + 0.6 * swE * Math.sin(f * 60 + T * 9));
                    if (el == ELECTRIC) {
                        w = Math.round(w * 2.5F) / 2.5F + (hash((int) Math.floor(mo.t / 0.05) * 5 + k) - 0.5F) * 0.5F;
                    }
                    at(g, g.ang(f), yc + h * 0.42F * clamp(w, -1.3F, 1.3F));
                    rib.add(px, py, pz, h * 0.55F, Math.min(1, Math.min(f, 1 - f) * 8 + 0.2F) * holeAt());
                }
                rib2.scaled(rib, 0.13F / 0.55F, 1);
                pen.s(SOFT).rgb(C).a(0.4F * gK * LA).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                pen.rgb(hot).a(0.9F * LA * (1 + 0.5F * swE)).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
            }
            // 受击：内容错开一份偏色的残影
            if (hurtE > 0.05F && (i == K_SKY || i == K_GRID)) {
                pen.cat = EphemeraStaging.TRAIL;
                strip(g, i == K_SKY ? TEX_SKY : TEX_TICKER, hot, 0.45F * hurtE * LA, uOff + 0.07,
                        h * (hash((int) Math.floor(mo.t / 0.05) + i) - 0.5F) * 0.6F);
                pen.cat = EphemeraStaging.BASE;
            }
            // 冰：屏面蒙一层霜
            if (el == ICE && !small && (!lite || i == K_SKY)) {
                strip(g, TEX_VGLOW, pale, 0.16F * LA, 0, 0);
            }
            // 毒：屏面上慢慢翻的毒絮
            if (el == POISON && i == K_SKY) {
                strip(g, FLOW_0, mul(C, 0.85F), 0.32F * LA, T * 0.08F, 0);
            }

            // ---- 框：上沿亮线、下沿细线、两头方括号 ----
            edgeRibbon(g, true, h * (small ? 0.12F : 0.09F));
            pen.rgb(hot).a(0.85F * LA * (1 + 0.5F * swE)).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            if (el == ICE && !small) {
                edgeRibbon(g, true, h * 0.22F);
                pen.s(SOFT).rgb(WHITE).a(0.45F * LA).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            }
            if (!small && !lite) {
                edgeRibbon(g, false, h * 0.05F);
                pen.rgb(C).a((0.55F + 0.45F * landE) * LA).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
            }
            for (int e = 0; e <= n; e += n) {
                int sg = e == 0 ? 1 : -1;
                float ex = h * 0.18F;
                double ptx = g.tx[e], pty = g.ty[e] + ex, ptz = g.tz[e];
                double pbx = g.bx[e], pby = g.by[e] - ex, pbz = g.bz[e];
                pen.rgb(hot).a(0.85F * LA).beam(ptx, pty, ptz, pbx, pby, pbz, h * 0.08F);
                if (!small && !lite) {
                    int e2 = e + sg;
                    pen.rgb(hot).a(0.75F * LA).beam(ptx, pty, ptz, g.tx[e] + (g.tx[e2] - g.tx[e]) * 0.3,
                            g.ty[e] + (g.ty[e2] - g.ty[e]) * 0.3 + ex, g.tz[e] + (g.tz[e2] - g.tz[e]) * 0.3, h * 0.07F);
                    pen.rgb(hot).a(0.75F * LA).beam(pbx, pby, pbz, g.bx[e] + (g.bx[e2] - g.bx[e]) * 0.3,
                            g.by[e] + (g.by[e2] - g.by[e]) * 0.3 - ex, g.bz[e] + (g.bz[e2] - g.bz[e]) * 0.3, h * 0.07F);
                }
                if (i == K_SKY) {
                    pen.s(SOFTCORE).rgb(hot).a(0.55F * LA).billboard(ptx, pty, ptz, h * 0.6F, h * 0.6F);
                }
            }

            // ---- 能量流动：扫描亮条 + 上沿流过的数据包 ----
            if (!small || !lite) {
                float sf = (float) fr(scr * 0.9 * DIR[i] + i * 0.37), sa = g.ang(sf);
                at(g, sa, yc);
                float sh = (float) Math.sin(sf * PI) * holeAt();
                at(g, sa, yc + h * 0.7F);
                double s0x = px, s0y = py, s0z = pz;
                at(g, sa, yc - h * 0.7F);
                if (!small) {
                    pen.s(TEX_VGLOW).rgb(C).a(0.4F * gK * LA * sh).beam(s0x, s0y, s0z, px, py, pz, h * 0.5F);
                }
                pen.rgb(hot).a(0.75F * LA * sh).beam(s0x, s0y - h * 0.15F, s0z, px, py + h * 0.15F, pz, h * 0.07F);
            }
            if (!lite && !small) {
                for (int j = 0; j < 2; j++) {
                    float f = (float) fr((mo.t * 0.22 + j * 0.5 + i * 0.21) * DIR[i]);
                    at(g, g.ang(f), yc + h / 2);
                    double ppx = px, ppy = py, ppz = pz;
                    float ff = (float) Math.sin(f * PI) * holeAt();
                    pen.s(SOFTCORE).rgb(hot).a(0.9F * LA * ff).billboard(ppx, ppy, ppz, h * 0.5F, h * 0.5F);
                    at(g, g.ang(f - DIR[i] * 0.14F), yc + h / 2);
                    pen.s(STREAK).rgb(C).a(0.7F * LA * ff).beam(ppx, ppy, ppz, px, py, pz, h * 0.22F);
                }
            }
            // 挥砍：亮光沿上沿从中间往两头冲
            if (swE > 0.02F) {
                float f = clamp(swS / (0.3F * mo.burstTime), 0, 1);
                for (int sg = -1; sg <= 1; sg += 2) {
                    at(g, g.ac + sg * g.ah * f, yc + h / 2);
                    pen.s(FLARE).rgb(hot).a(0.9F * (1 - f) * LA * gK).billboard(px, py, pz, h * 1.5F, h * 1.5F, 0);
                }
            }
            // 落地：下沿两头各闪一下
            if (landE > 0.05F && !small) {
                for (int e = 0; e <= n; e += n) {
                    pen.s(SOFT).rgb(hot).a(0.6F * landE * LA).billboard(g.bx[e], g.by[e], g.bz[e], h * 1.2F * (1 + landS * 3), h * 0.5F);
                }
            }
            // 跑动：屏两头往后拖出光痕
            if (run > 0.1F && !small) {
                pen.cat = EphemeraStaging.TRAIL;
                for (int e = 0; e <= n; e += n) {
                    double mx = (g.tx[e] + g.bx[e]) * 0.5, my = (g.ty[e] + g.by[e]) * 0.5, mz = (g.tz[e] + g.bz[e]) * 0.5;
                    float back = W * (0.8F + 0.25F * i) * run * (lite ? 0.7F : 1);
                    pen.s(STREAK).rgb(C).a(0.35F * run * gK * LA).beam(mx, my, mz, mx - fwX * back, my, mz - fwZ * back, h * 0.8F);
                    if (!lite) {
                        pen.s(STREAK).rgb(hot).a(0.65F * run * LA).beam(g.tx[e], g.ty[e], g.tz[e],
                                g.tx[e] - fwX * back * 1.2F, g.ty[e], g.tz[e] - fwZ * back * 1.2F, h * 0.09F);
                    }
                }
                pen.cat = EphemeraStaging.BASE;
            }

            sites.add(g.tx[1], g.ty[1], g.tz[1], 0, 1, 0);
            sites.add(g.tx[n - 1], g.ty[n - 1], g.tz[n - 1], 0, 1, 0);
            sites.add(g.tx[n / 2], g.ty[n / 2], g.tz[n / 2], -fwX, 0, -fwZ);
        }

        // ---- 元素 ----
        elements(el, fx, t, T, swE, gK, magPull, C, hot, pale);

        // 元素粒子和爆发先画，后面的光线、碎屑按剩下的面片数让
        pen.cat = EphemeraStaging.PARTICLE;
        fx.motes(sites, 7 * (0.5F + 0.5F * act), sc);
        fx.events(mo.x, mo.groundY, mo.z, W * 1.5F);
        fx.bursts();
        pen.cat = EphemeraStaging.BASE;

        // ---- 身上往两侧射的细直光线，端头挂一枚数据标签 ----
        // 第一人称看自己时这些从胸口往两边扫，会横在视野边上，不画
        boolean heavy = el == ELECTRIC || el == POISON || el == MAGNETIC;
        int NL = d.firstPersonSelf ? 0 : lite ? 2 : heavy ? 4 : 6;
        for (int r = 0; r < NL; r++) {
            float per = 1.4F + 0.6F * fixed(r * 5 + 1);
            double tt = mo.t + fixed(r * 3) * per;
            int slot = (int) Math.floor(tt / per);
            float ph = (float) fr(tt / per);
            float vis = (ph < 0.55F ? (float) Math.sin(ph / 0.55F * PI) : 0) * (0.55F + 0.45F * act);
            float grow = Math.min(1, ph / 0.15F);
            if (swE > vis) {
                vis = swE;
                grow = Math.max(grow, clamp(swS / 0.12F, 0, 1));
            }
            if (vis < 0.03F || left() < 70) {
                continue;
            }
            int side = (r & 1) != 0 ? 1 : -1;
            float h1 = hash(slot * 13 + r * 7), h2 = hash(slot * 5 + r * 3), h3 = hash(slot * 3 + r);
            double dx = rtX * side - fwX * (0.15F + 0.55F * h1), dy = (h2 - 0.45F) * 0.35F, dz = rtZ * side - fwZ * (0.15F + 0.55F * h1);
            double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= dl;
            dy /= dl;
            dz /= dl;
            double bx = cX + rtX * side * W * 0.3F, by = cY + H * (0.52F + 0.3F * h3), bz = cZ + rtZ * side * W * 0.3F;
            float len = W * (2.6F + 1.6F * h1) * grow * (1 + 0.3F * swE * Bk);
            double tx = bx + dx * len, ty = by + dy * len, tz = bz + dz * len;
            pen.s(TEX_VGLOW).rgb(C).a(0.32F * vis * gK * A).beam(bx, by, bz, tx, ty, tz, W * 0.11F);
            pen.rgb(hot).a(0.9F * vis * A).beam(bx, by, bz, tx, ty, tz, W * 0.022F);
            pen.s(SOFTCORE).rgb(hot).a(0.85F * vis * A).billboard(tx, ty, tz, W * 0.2F, W * 0.2F);
            if (!lite && grow >= 1 && left() > 150) {
                double gx = tx + rtX * side * W * 0.18F, gy = ty + W * 0.13F, gz = tz + rtZ * side * W * 0.18F;
                pen.alphaBlend().rgb(deep).a(0.5F * vis).billboard(gx, gy, gz, W * 0.62F, W * 0.15F);
                pen.s(TEX_TEXT).rgb(C).a(0.95F * vis * A).billboard(gx, gy, gz, W * 0.56F, W * 0.12F);
            }
            if (!heavy || (el == POISON && r < 2)) {
                fx.accent(tx, ty, tz, dx, dy, dz, W * 0.45F * vis, W * 0.07F, 60 + r, 0.6F * vis * A);
            }
        }

        // ---- 氛围：屏上飘出的像素碎屑，跑动往后吹、挥砍和受击往外迸 ----
        pen.cat = EphemeraStaging.PARTICLE;
        FxParticles P = s.pt;
        P.thin = mo.emit;
        s.acc += dt * (3 + 20 * run + 60 * swE + 35 * hurtE) * (lite ? 0.35F : 1);
        while (s.acc >= 1 && ng > 0) {
            if (left() < 140) {
                s.acc = 0;
                break;
            }
            s.acc -= 1;
            Lay g = geo[Math.min(ng - 1, (int) (rnd() * ng))];
            float a = g.ang(rnd());
            at(g, a, g.yc + (rnd() - 0.5F) * g.h);
            double ppx = px, ppy = py, ppz = pz;
            at(g, a + 0.01F, g.yc);
            double ax = px, az = pz;
            at(g, a - 0.01F, g.yc);
            double tgx = ax - px, tgz = az - pz, tl = Math.sqrt(tgx * tgx + tgz * tgz) + 1.0E-9;
            tgx /= tl;
            tgz /= tl;
            double ox = ppx - cX, oy = ppy - (cY + g.yc), oz = ppz - cZ, ol = Math.sqrt(ox * ox + oy * oy + oz * oz) + 1.0E-9;
            float ok = (0.15F + 1.8F * swE + 1.2F * hurtE) * sc, tk = DIR[g.i] * 0.25F * sc;
            P.spec().pos(ppx, ppy, ppz)
                    .vel((float) (tgx * tk + ox / ol * ok - fwX * 1.5F * run * sc), (float) (oy / ol * ok + 0.12F * sc),
                            (float) (tgz * tk + oz / ol * ok - fwZ * 1.5F * run * sc))
                    .life(rnd(0.5F, 1.0F)).size(rnd(0.025F, 0.05F) * sc, 0.008F * sc).sprite(EphemeraSprites.WHITE)
                    .rgb(rnd() < 0.4F ? hot : C).a(0.9F).drag(1.2F).spawn();
        }
        P.step(dt, null);
        P.draw(pen, mo.size, 1, Float.NaN);
        pen.cat = EphemeraStaging.BASE;
    }

    private void elements(int el, ElementMotif fx, float t, float T, float swE, float gK, float magPull, int C, int hot, int pale) {
        Lay sky = find(K_SKY);
        switch (el) {
            case FIRE -> {
                // 每层上沿一排火舌，最上一层两头再窜一截火冠
                for (int j = 0; j < ng; j++) {
                    Lay g = geo[j];
                    fx.edge(rail(g), g.h * 0.32F * (1 + 0.4F * swE), 0.8F * g.la, 10 + g.i);
                }
                if (!lite) {
                    Lay g = geo[0];
                    for (int e = 0; e <= g.n; e += g.n) {
                        fx.accent(g.tx[e], g.ty[e], g.tz[e], 0, 1, 0, H * (0.16F + 0.08F * swE), g.h * 1.1F, 30 + e, 0.8F * g.la);
                    }
                }
            }
            case ICE -> {
                // 屏下沿挂一排冰棱：长出来、停住、碎掉，按根错开
                for (int j = 0; j < ng; j++) {
                    Lay g = geo[j];
                    if (g.small || (lite && g != sky)) {
                        continue;
                    }
                    for (int k = 0; k < g.n; k++) {
                        float per = 3.2F, ph = (float) fr((t * 0.5 + fixed(k * 7 + g.i * 13) * per) / per);
                        float grow = ph < 0.12F ? EphemeraFx.smooth(ph / 0.12F) : ph < 0.88F ? 1 : 1 - (ph - 0.88F) / 0.12F;
                        float Lc = g.h * (0.8F + 1.4F * fixed(k * 3 + g.i)) * grow * (1 + 0.3F * swE);
                        if (Lc < 0.005F) {
                            continue;
                        }
                        float f = 0.25F + 0.5F * fixed(k * 5 + g.i * 3);
                        double bx = g.bx[k] + (g.bx[k + 1] - g.bx[k]) * f, by = g.by[k] + (g.by[k + 1] - g.by[k]) * f,
                                bz = g.bz[k] + (g.bz[k + 1] - g.bz[k]) * f;
                        float ha = g.la * g.fa[k];
                        pen.rgb(pale).a(0.8F * ha).va(0.6F, 0.8F, 1, 0.8F);
                        kite(bx, by, bz, bx, by - Lc, bz, g.h * 0.32F, 0.22F);
                        pen.rgb(WHITE).a(0.9F * ha);
                        kite(bx, by, bz, bx, by - Lc, bz, g.h * 0.06F, 0.3F);
                    }
                }
                fx.edge(rail(sky), sky.h * 0.28F, 0.8F * sky.la, 11);
                // 屏的两个上角各结一簇往外上方张的冰晶
                for (int j = 0; j < ng; j++) {
                    Lay g = geo[j];
                    if (g.small || (lite && g != sky)) {
                        continue;
                    }
                    for (int e = 0; e <= g.n; e += g.n) {
                        int e2 = e == 0 ? 1 : g.n - 1;
                        double ox = g.tx[e] - g.tx[e2], oy = g.ty[e] - g.ty[e2], oz = g.tz[e] - g.tz[e2];
                        double ol = Math.sqrt(ox * ox + oy * oy + oz * oz) + 1.0E-9;
                        ox /= ol;
                        oy /= ol;
                        oz /= ol;
                        for (int q = 0; q < 3; q++) {
                            float ko = 0.35F + 0.35F * q, ku = 1 - 0.25F * q;
                            double dx = ox * ko, dy = oy * ko + ku, dz = oz * ko, dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            float Lc = g.h * (1.3F - 0.3F * q) * (1 + 0.4F * swE);
                            double tx = g.tx[e] + dx / dl * Lc, ty = g.ty[e] + dy / dl * Lc, tz = g.tz[e] + dz / dl * Lc;
                            pen.rgb(pale).a(0.8F * g.la).va(1, 0.7F, 0.4F, 0.7F);
                            kite(g.tx[e], g.ty[e], g.tz[e], tx, ty, tz, g.h * 0.3F, 0.3F);
                            pen.rgb(WHITE).a(0.9F * g.la);
                            kite(g.tx[e], g.ty[e], g.tz[e], tx, ty, tz, g.h * 0.07F, 0.35F);
                        }
                    }
                }
                if (!lite) {
                    int nb = 0;
                    for (int j = 0; j < ng; j++) {
                        if (!geo[j].small) {
                            nb++;
                        }
                    }
                    for (int j = 0; j < 4 && nb > 0; j++) {
                        int q = (int) Math.floor(m.t * 1.5 + j * 0.31);
                        Lay g = nthBig(q % nb);
                        float f = 0.1F + 0.8F * hash(q * 7 + j);
                        float tw = (float) Math.sin(fr(m.t * 1.5 + j * 0.31) * PI);
                        at(g, g.ang(f), g.yc + (hash(q * 3 + j) - 0.5F) * g.h);
                        pen.s(FLARE).rgb(WHITE).a(0.8F * tw * g.la).billboard(px, py, pz, g.h * 0.9F * tw, g.h * 0.9F * tw, q);
                    }
                }
            }
            case ELECTRIC -> {
                // 电弧在上下两层屏之间横跳
                int q = (int) Math.floor(m.t / 0.06), NA = lite ? 1 : 3 + (swE > 0.1F ? 1 : 0);
                for (int j = 0; j < NA; j++) {
                    if (hash(q * 3 + j * 11) < 0.25F) {
                        continue;
                    }
                    int pi = (int) Math.floor(hash(q * 5 + j * 7) * (ng - 1));
                    Lay gU = geo[pi], gL = geo[pi + 1];
                    float fU = 0.1F + 0.8F * hash(q * 7 + j), fL = clamp(fU + (hash(q * 9 + j) - 0.5F) * 0.5F, 0.05F, 0.95F);
                    at(gU, gU.ang(fU), gU.yc - gU.h / 2);
                    double p0x = px, p0y = py, p0z = pz;
                    at(gL, gL.ang(fL), gL.yc + gL.h / 2);
                    zig(p0x, p0y, p0z, px, py, pz, 7, W * 0.1F, q * 13 + j, rtX, 0, rtZ);
                    float ha = Math.min(gU.la, gL.la) * hole((p0x + px) * 0.5, (p0y + py) * 0.5, (p0z + pz) * 0.5);
                    zigRibbon(7, W * 0.13F);
                    pen.s(SOFT).rgb(C).a(0.45F * ha).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    zigRibbon(7, W * 0.025F);
                    pen.rgb(hot).a(ha).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    pen.s(FLARE).rgb(hot).a(0.8F * ha).billboard(zx[7], zy[7], zz[7], W * 0.3F, W * 0.3F, q);
                }
                // 上沿短路：沿整条上沿一道折线电流，一闪一闪
                int qq = (int) Math.floor(m.t / 0.05);
                for (int j = 0; j < ng; j++) {
                    Lay g = geo[j];
                    if (lite ? g != sky : (g.small || g.i == K_COLS)) {
                        continue;
                    }
                    if (hash(qq * 7 + g.i) < 0.35F && swE < 0.1F) {
                        continue;
                    }
                    rib.clear();
                    for (int k = 0; k <= g.n; k++) {
                        float f = (float) k / g.n, jy = k == 0 || k == g.n ? 0 : (hash(qq * 31 + k * 7 + g.i) - 0.5F) * g.h * 0.5F;
                        at(g, g.ang(f), g.yc + g.h / 2 + jy);
                        rib.add(px, py, pz, g.h * 0.4F, holeAt());
                    }
                    rib2.scaled(rib, 0.2F, 1);
                    pen.s(SOFT).rgb(C).a(0.45F * g.la).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    pen.rgb(WHITE).a(0.9F * g.la).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
                }
            }
            case POISON -> {
                // 下坠的屏底挂几道黏流，末端鼓成一滴；往屏外侧伸一点再垂下来，正面看不会被人和屏挡住
                for (int j = 0; j < ng; j++) {
                    Lay g = geo[j];
                    if (g.small || (lite && g != sky)) {
                        continue;
                    }
                    for (int fi = 0; fi < (lite ? 1 : 2); fi++) {
                        float f = lite ? 0.15F : fi == 0 ? 0.12F : 0.85F;
                        int k = Math.round(f * g.n);
                        float so = f < 0.5F ? -0.7F : 0.7F;
                        double ox = -fwX * 0.3F + rtX * so, oy = -0.35F, oz = -fwZ * 0.3F + rtZ * so;
                        double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
                        fx.accent(g.bx[k], g.by[k], g.bz[k], ox / ol, oy / ol, oz / ol, g.h * (2.4F + 0.8F * swE), g.h * 0.5F,
                                40 + g.i * 3 + k, g.la * g.fa[k]);
                    }
                }
            }
            case IMPACT -> {
                // 每一拍：一道白亮竖线从屏中间往两头推，推到头往外打出一截直光条、炸一个冲击圈
                float ph = (float) fr(m.t / 0.85), f = Math.min(1, ph / 0.3F);
                float g2 = clamp((ph - 0.2F) / 0.35F, 0, 1), e2 = clamp((ph - 0.22F) / 0.3F, 0, 1);
                for (int j = 0; j < ng; j++) {
                    Lay g = geo[j];
                    if (g.small) {
                        continue;
                    }
                    for (int sg = -1; sg <= 1; sg += 2) {
                        float a = g.ac + sg * g.ah * f;
                        if (f < 1) {
                            at(g, a, g.yc + g.h * 0.8F);
                            double ux = px, uy = py, uz = pz;
                            at(g, a, g.yc - g.h * 0.8F);
                            pen.rgb(WHITE).a(0.75F * (1 - f) * g.la).beam(ux, uy, uz, px, py, pz, g.h * 0.1F);
                        }
                        int ke = sg < 0 ? 0 : g.n, k2 = ke - sg;
                        double mx = (g.tx[ke] + g.bx[ke]) * 0.5, my = (g.ty[ke] + g.by[ke]) * 0.5, mz = (g.tz[ke] + g.bz[ke]) * 0.5;
                        if (g2 > 0 && g2 < 1) {
                            float sz = g.h * (1.5F + 6 * g2);
                            pen.s(SHOCK).rgb(pale).a(0.8F * (1 - g2) * g.la * gK).billboard(mx, my, mz, sz, sz);
                        }
                        if (e2 > 0 && e2 < 1) {
                            double ox = mx - (g.tx[k2] + g.bx[k2]) * 0.5, oy = my - (g.ty[k2] + g.by[k2]) * 0.5, oz = mz - (g.tz[k2] + g.bz[k2]) * 0.5;
                            double ol = Math.sqrt(ox * ox + oy * oy + oz * oz) + 1.0E-9, L = W * (0.3F + 1.2F * e2) / ol;
                            double tx = mx + ox * L, ty = my + oy * L, tz = mz + oz * L;
                            pen.s(STREAK).rgb(pale).a(0.7F * (1 - e2) * g.la * gK).beam(mx, my, mz, tx, ty, tz, g.h * 0.45F * (1 - 0.5F * e2));
                            pen.rgb(WHITE).a(0.8F * (1 - e2) * g.la).beam(mx, my, mz, tx, ty, tz, g.h * 0.07F);
                        }
                    }
                }
                fx.edge(rail(sky), sky.h * 0.3F, 0.8F * sky.la, 13);
            }
            case MAGNETIC -> {
                // 屏的两头之间兜着闭合的磁力线：从上层一头往外鼓出去、绕回下层同一头，光珠沿线流
                int np = lite ? 1 : 2, NP = lite ? 7 : 9;
                for (int pr = 0; pr < np; pr++) {
                    Lay gU = pr == 0 ? sky : find(K_TEXT), gL = pr == 0 ? find(K_GRID) : find(K_COLS);
                    float la = Math.min(gU.la, gL.la);
                    for (int sg = -1; sg <= 1; sg += 2) {
                        int kU = sg < 0 ? 0 : gU.n, kL = sg < 0 ? 0 : gL.n;
                        double ax = (gU.tx[kU] + gU.bx[kU]) * 0.5, ay = (gU.ty[kU] + gU.by[kU]) * 0.5, az = (gU.tz[kU] + gU.bz[kU]) * 0.5;
                        double bx = (gL.tx[kL] + gL.bx[kL]) * 0.5, by = (gL.ty[kL] + gL.by[kL]) * 0.5, bz = (gL.tz[kL] + gL.bz[kL]) * 0.5;
                        int kU2 = kU - sg, kL2 = kL - sg;
                        double oax = ax - (gU.tx[kU2] + gU.bx[kU2]) * 0.5, oay = ay - (gU.ty[kU2] + gU.by[kU2]) * 0.5, oaz = az - (gU.tz[kU2] + gU.bz[kU2]) * 0.5;
                        double obx = bx - (gL.tx[kL2] + gL.bx[kL2]) * 0.5, oby = by - (gL.ty[kL2] + gL.by[kL2]) * 0.5, obz = bz - (gL.tz[kL2] + gL.bz[kL2]) * 0.5;
                        double la0 = Math.sqrt(oax * oax + oay * oay + oaz * oaz) + 1.0E-9, lb0 = Math.sqrt(obx * obx + oby * oby + obz * obz) + 1.0E-9;
                        float bow = W * (0.9F + 0.15F * (float) Math.sin(T * 1.3F + gU.i)) * (1 + 0.4F * swE - 0.3F * magPull / 0.07F);
                        double c1x = ax + oax / la0 * bow - fwX * W * 0.3F, c1y = ay + oay / la0 * bow, c1z = az + oaz / la0 * bow - fwZ * W * 0.3F;
                        double c2x = bx + obx / lb0 * bow - fwX * W * 0.3F, c2y = by + oby / lb0 * bow, c2z = bz + obz / lb0 * bow - fwZ * W * 0.3F;
                        for (int k = 0; k <= NP; k++) {
                            double u = (double) k / NP, v = 1 - u;
                            double k0 = v * v * v, k1 = 3 * v * v * u, k2 = 3 * v * u * u, k3 = u * u * u;
                            bzX[k] = ax * k0 + c1x * k1 + c2x * k2 + bx * k3;
                            bzY[k] = ay * k0 + c1y * k1 + c2y * k2 + by * k3;
                            bzZ[k] = az * k0 + c1z * k1 + c2z * k2 + bz * k3;
                        }
                        if (gU == sky) {
                            rib.clear();
                            for (int k = 0; k <= NP; k++) {
                                rib.add(bzX[k], bzY[k], bzZ[k], W * 0.18F, 1);
                            }
                            pen.s(SOFT).rgb(C).a(0.38F * gK * la).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                        }
                        rib.clear();
                        for (int k = 0; k <= NP; k++) {
                            rib.add(bzX[k], bzY[k], bzZ[k], W * 0.035F, 1);
                        }
                        pen.rgb(pale).a(0.9F * la).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                        for (int b = 0; b < (lite ? 1 : 3); b++) {
                            float f = (float) fr(m.t * 0.45 + b / 3.0 + gU.i * 0.1);
                            int kk = Math.min(NP - 1, (int) Math.floor(f * NP));
                            double ff = f * NP - kk;
                            pen.s(SOFTCORE).rgb(hot).a(0.95F * la).billboard(bzX[kk] + (bzX[kk + 1] - bzX[kk]) * ff,
                                    bzY[kk] + (bzY[kk + 1] - bzY[kk]) * ff, bzZ[kk] + (bzZ[kk + 1] - bzZ[kk]) * ff, W * 0.22F, W * 0.22F);
                        }
                    }
                }
            }
            default -> {
                // 辐射：屏面上六边形咔哒明灭，上沿往外放射细线
                int q = (int) Math.floor(m.t / 0.08);
                for (int j = 0; j < ng; j++) {
                    Lay g = geo[j];
                    if (g.small) {
                        continue;
                    }
                    for (int k = 0; k < (lite ? 1 : 2); k++) {
                        float hh = hash(q * 13 + g.i * 7 + k);
                        if (hh < 0.45F) {
                            continue;
                        }
                        at(g, g.ang(0.1F + 0.8F * hash(q * 5 + g.i + k * 3)), g.yc + (hash(q * 3 + k) - 0.5F) * g.h * 0.6F);
                        float ho = holeAt();
                        pen.s(HEX_0).rgb(hot).a(0.85F * g.la * ho).billboard(px, py, pz, g.h * 1.1F, g.h * 1.1F, t * 0.4F);
                        if (hh > 0.88F) {
                            pen.s(FLARE).rgb(hot).a(0.7F * g.la * ho).billboard(px, py, pz, g.h * 2, g.h * 2, 0.785F);
                        }
                    }
                }
                for (int j = 0; j < ng; j++) {
                    Lay g = geo[j];
                    if (lite ? g != sky : (g.small || g.i == K_COLS)) {
                        continue;
                    }
                    fx.edge(rail(g), g.h * 0.3F, 0.8F * g.la, 15 + g.i);
                }
            }
        }
    }

    /** 第 j 块大屏（不算字行和示波线） */
    private Lay nthBig(int j) {
        for (int i = 0; i < ng; i++) {
            if (!geo[i].small && j-- == 0) {
                return geo[i];
            }
        }
        return geo[0];
    }
}
