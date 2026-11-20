package pers.roinflam.kuvalich.client.ephemera;

import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.TAU;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.WHITE;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.hash;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mix;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.mul;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.noise;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.rand;
import static pers.roinflam.kuvalich.client.ephemera.EphemeraFx.smooth;
import static pers.roinflam.kuvalich.client.ephemera.FxMotion.*;

/**
 * 元素母题：各款共用的一层，照原型 elem.js。造型各款自己画，「往外长出来的一截」「沿轮廓的点缀」「身上冒的东西」
 * 「贴地的一块」「落地 / 挥砍 / 受击的爆发」交给这里，同一种元素在哪款上都是同一套形状：
 * 火 = 尖朝上的舌、余烬上飘；冰 = 硬边棱晶、长出来停住再碎；电 = 折线一闪一闪地跳、火花四溅；
 * 毒 = 往下垂的滴、落地成滩；冲击 = 直线和同心圈、按拍子往外震；磁 = 闭合弧圈、往中心吸；辐射 = 放射细线、随机明灭、六边形光斑。
 *
 * <p>每个幻纹实例一份（粒子池、毒滩、爆发都在里面），每帧先 {@link #begin}。除了 {@link #motes} 固定记作粒子，
 * 其余画进笔刷当前的类别 {@code pen.cat}，由各款自己定。</p>
 */
public final class ElementMotif {

    /** 身上冒东西的发射点：位置 + 往外的方向（单位向量） */
    public static final class Sites {
        public double[] x = new double[16], y = new double[16], z = new double[16];
        public float[] nx = new float[16], ny = new float[16], nz = new float[16];
        public int n;

        public Sites clear() {
            n = 0;
            return this;
        }

        public void add(double px, double py, double pz, float dx, float dy, float dz) {
            if (n == x.length) {
                int c = n * 2;
                x = java.util.Arrays.copyOf(x, c);
                y = java.util.Arrays.copyOf(y, c);
                z = java.util.Arrays.copyOf(z, c);
                nx = java.util.Arrays.copyOf(nx, c);
                ny = java.util.Arrays.copyOf(ny, c);
                nz = java.util.Arrays.copyOf(nz, c);
            }
            x[n] = px;
            y[n] = py;
            z[n] = pz;
            nx[n] = dx;
            ny[n] = dy;
            nz[n] = dz;
            n++;
        }
    }

    private static final int DRIP = -1;
    private static final int MAX_BURSTS = 5;

    // 这一帧
    private EphemeraPen pen;
    private FxMotion m;
    private float t;
    /** 主色、亮色（往白混 0.62）、浅色（往白混 0.3）、深色（× 0.22） */
    public int C, hot, pale, deep;

    // 跨帧
    private FxParticles pool;
    private boolean poolLite;
    private float acc;
    private long beatIdx = Long.MIN_VALUE;
    private Sites curSites;
    private final double[] decX = new double[10], decY = new double[10], decZ = new double[10];
    private final float[] decT = new float[10], decR = new float[10];
    private int decN;
    private final double[] bx = new double[MAX_BURSTS], by = new double[MAX_BURSTS], bz = new double[MAX_BURSTS];
    private final float[] bt = new float[MAX_BURSTS], br = new float[MAX_BURSTS], bk = new float[MAX_BURSTS];
    private int bn;
    private int seenLand = Integer.MIN_VALUE, seenSwing, seenHurt;

    private final EphemeraPen.Ribbon rib = new EphemeraPen.Ribbon(10);
    private final EphemeraPen.Ribbon rib2 = new EphemeraPen.Ribbon(10);
    private final double[] zx = new double[9], zy = new double[9], zz = new double[9];
    private final double[] side = new double[3];
    private final float[] rd = new float[3];

    public void begin(EphemeraPen p, FxMotion mo) {
        pen = p;
        m = mo;
        t = (float) mo.t;
        C = mo.color;
        hot = mix(C, WHITE, 0.62F);
        pale = mix(C, WHITE, 0.3F);
        deep = mul(C, 0.22F);
    }

    /** 新的一份（{@code reset} 里调）：粒子、毒滩、爆发全清 */
    public void reset() {
        if (pool != null) {
            pool.clear();
        }
        acc = 0;
        decN = 0;
        bn = 0;
        seenLand = Integer.MIN_VALUE;
        beatIdx = Long.MIN_VALUE;
    }

    /** 粒子池里还有几个（测试、调预算用） */
    public int particles() {
        return pool == null ? 0 : pool.count();
    }

    // ---------- 小件 ----------

    /** dir 在镜头里的侧向：dir × 视线，写进 side */
    private void sideOf(double px, double py, double pz, double dx, double dy, double dz) {
        double vx = px - pen.camX(), vy = py - pen.camY(), vz = pz - pen.camZ();
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

    /** 一片尖朝 tip 的贴图片，贴图上沿对着 tip；笔刷先设好 */
    private void spike(double ax, double ay, double az, double tx, double ty, double tz, float w) {
        double dx = tx - ax, dy = ty - ay, dz = tz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1.0E-6) {
            l = 1;
        }
        sideOf(ax, ay, az, dx / l, dy / l, dz / l);
        double sx = side[0] * w / 2, sy = side[1] * w / 2, sz = side[2] * w / 2;
        pen.quad(tx - sx, ty - sy, tz - sz, tx + sx, ty + sy, tz + sz, ax + sx, ay + sy, az + sz, ax - sx, ay - sy, az - sz);
    }

    /** 菱形片：base 到 tip，最宽处在 mid（0~1）；笔刷先设好 */
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

    /** a 到 b 的折线（n 段），中间各点沿 (sx, sy, sz) 随机偏 ±amp，写进 zx/zy/zz */
    private void zig(double ax, double ay, double az, double bx0, double by0, double bz0, int n, float amp, int seed,
                     double sx, double sy, double sz) {
        for (int i = 0; i <= n; i++) {
            float f = (float) i / n;
            float j = i == 0 || i == n ? 0 : (hash(seed * 31 + i * 7) - 0.5F) * 2 * amp;
            zx[i] = ax + (bx0 - ax) * f + sx * j;
            zy[i] = ay + (by0 - ay) * f + sy * j;
            zz[i] = az + (bz0 - az) * f + sz * j;
        }
    }

    private void zigRibbon(EphemeraPen.Ribbon r, int n, float w) {
        r.clear();
        for (int i = 0; i <= n; i++) {
            r.add(zx[i], zy[i], zz[i], w, 1);
        }
    }

    // ---------- 凸起 ----------

    /**
     * 往外长出来的一截：光舌、翎尖、刺、光环上的齿都走它。根部 (x, y, z)，朝向 (dx, dy, dz) 是单位向量，
     * len 长、w 宽，seed 每根一个（决定错开的节律），a 透明度。
     */
    public void accent(double x, double y, double z, double dx, double dy, double dz, float len, float w, int seed, float a) {
        if (a <= 0.01F || len <= 1.0E-4F) {
            return;
        }
        switch (m.el) {
            case FIRE -> {
                float fl = 0.75F + 0.35F * noise(t * 3.1F + seed * 1.7F, seed);
                float sw = (noise(t * 2.3F, seed + 9) - 0.5F) * len * 0.4F;
                double tx = x + dx * len * fl + m.rightX * sw, ty = y + dy * len * fl + len * 0.35F, tz = z + dz * len * fl + m.rightZ * sw;
                pen.s((seed & 1) != 0 ? EphemeraSprites.FLAME_0 + 1 : EphemeraSprites.FLAME_0).rgb(C).a(0.85F * a);
                spike(x, y, z, tx, ty, tz, w * 1.5F);
                pen.s(EphemeraSprites.FLAME_0).rgb(hot).a(0.75F * a);
                spike(x, y, z, x + (tx - x) * 0.6, y + (ty - y) * 0.6, z + (tz - z) * 0.6, w * 0.7F);
            }
            case ICE -> {
                float per = 2.6F, ph = ((t * 0.9F + hash(seed) * per) % per) / per;
                float grow = ph < 0.15F ? smooth(ph / 0.15F) : ph < 0.85F ? 1 : 1 - (ph - 0.85F) / 0.15F;
                float L = len * (0.6F + 0.5F * hash(seed + 3)) * grow;
                if (L < 0.01F) {
                    return;
                }
                double tx = x + dx * L, ty = y + dy * L, tz = z + dz * L;
                sideOf(x, y, z, dx, dy, dz);
                double mx = x + (tx - x) * 0.3, my = y + (ty - y) * 0.3, mz = z + (tz - z) * 0.3;
                double sx = side[0] * w * 0.45F, sy = side[1] * w * 0.45F, sz = side[2] * w * 0.45F;
                pen.alphaBlend().rgb(deep).a(0.45F * a);
                pen.quad(tx, ty, tz, mx + sx, my + sy, mz + sz, x, y, z, mx - sx, my - sy, mz - sz);
                pen.rgb(pale).a(0.55F * a).va(1, 0.5F, 0.3F, 0.5F);
                kite(x, y, z, tx, ty, tz, w * 0.8F, 0.3F);
                pen.rgb(WHITE).a(0.8F * a);
                kite(x + (tx - x) * 0.2, y + (ty - y) * 0.2, z + (tz - z) * 0.2, tx, ty, tz, w * 0.22F, 0.4F);
                if (hash((int) Math.floor(t * 2) * 17 + seed) > 0.7F) {
                    pen.s(EphemeraSprites.FLARE).rgb(hot).a(0.6F * a).billboard(tx, ty, tz, w * 1.2F, w * 1.2F, t);
                }
            }
            case ELECTRIC -> {
                float on = m.flick(seed);
                int q = (int) Math.floor(t / 0.05F);
                float L = len * (0.7F + 0.5F * hash(q * 3 + seed));
                double tx = x + dx * L, ty = y + dy * L, tz = z + dz * L;
                sideOf(x, y, z, dx, dy, dz);
                zig(x, y, z, tx, ty, tz, 6, w * 0.9F, q * 7 + seed, side[0], side[1], side[2]);
                zigRibbon(rib, 6, w * 1.1F);
                pen.s(EphemeraSprites.SOFT).rgb(C).a(0.35F * a * on).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                zigRibbon(rib, 6, w * 0.18F);
                pen.rgb(hot).a(0.95F * a * on).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                if (on > 0.5F) {
                    pen.s(EphemeraSprites.FLARE).rgb(hot).a(0.7F * a).billboard(tx, ty, tz, w * 1.6F, w * 1.6F, q);
                }
            }
            case POISON -> {
                // 往外伸出去一段就垂下来，末端鼓成一滴
                float sag = 0.5F + 0.15F * (float) Math.sin(t * 1.6F + seed);
                double mx = x + dx * len * 0.5, my = y + dy * len * 0.5 - len * 0.1, mz = z + dz * len * 0.5;
                double tx = x + dx * len * 0.75, ty = y + dy * len * 0.75 - len * sag, tz = z + dz * len * 0.75;
                rib.clear();
                for (int i = 0; i <= 6; i++) {
                    float f = i / 6.0F, g = 1 - f;
                    double px = g * g * x + 2 * g * f * mx + f * f * tx, py = g * g * y + 2 * g * f * my + f * f * ty,
                            pz = g * g * z + 2 * g * f * mz + f * f * tz;
                    rib.add(px, py, pz, w * (0.55F - 0.3F * (float) Math.sin(f * Math.PI) + (f > 0.8F ? (f - 0.8F) * 3.5F : 0)), 1);
                }
                pen.s(EphemeraSprites.SOFT).alphaBlend().rgb(deep).a(0.5F * a).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                rib2.scaled(rib, 0.6F, 1);
                pen.s(EphemeraSprites.SOFT).rgb(C).a(0.75F * a).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
                pen.s(EphemeraSprites.DROP).rgb(pale).a(0.8F * a).billboard(tx, ty, tz, w * 0.9F, w * 1.1F);
            }
            case IMPACT -> {
                float b = m.beat(hash(seed) * 0.12F);
                float L = len * (0.45F + 0.75F * b);
                double tx = x + dx * L, ty = y + dy * L, tz = z + dz * L;
                pen.s(EphemeraSprites.STREAK).rgb(pale).a((0.5F + 0.5F * b) * a).beam(x, y, z, tx, ty, tz, w * (0.5F + 0.6F * b));
                pen.rgb(WHITE).a((0.3F + 0.6F * b) * a).beam(x, y, z, tx, ty, tz, w * 0.15F);
                if (b > 0.05F) {
                    float sz = w * (1 + 3 * (1 - b));
                    pen.s(EphemeraSprites.SHOCK).rgb(hot).a(b * 0.9F * a).billboard(tx, ty, tz, sz, sz);
                }
            }
            case MAGNETIC -> {
                // 从根部出去、绕回根部旁边的一道磁力线，上面有珠子在流
                sideOf(x, y, z, dx, dy, dz);
                float wob = 0.15F * (float) Math.sin(t * 1.3F + seed);
                final int N = 7;
                rib.clear();
                for (int i = 0; i <= N; i++) {
                    float ang = (float) i / N * (float) Math.PI;
                    double out = Math.sin(ang) * len * (0.9F + wob), sd = (0.5 - 0.5 * Math.cos(ang)) * len * 0.55 - len * 0.27;
                    rib.add(x + dx * out + side[0] * sd, y + dy * out + side[1] * sd, z + dz * out + side[2] * sd,
                            w * 0.22F, 0.3F + 0.7F * (float) Math.sin(ang));
                }
                if (!m.lite) {
                    rib2.clear();
                    for (int i = 0; i < rib.n; i++) {
                        rib2.add(rib.x[i], rib.y[i], rib.z[i], w * 0.9F, rib.a[i]);
                    }
                    pen.s(EphemeraSprites.SOFT).rgb(C).a(0.3F * a).ribbon(rib2, EphemeraPen.FACE_CAM, 0, 1);
                }
                pen.rgb(pale).a(0.85F * a).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                for (int j = 0; j < (m.lite ? 1 : 2); j++) {
                    float f = (t * 0.7F + j * 0.5F + hash(seed)) % 1;
                    int i = Math.min(N - 1, (int) Math.floor(f * N));
                    float ff = f * N - i;
                    pen.s(EphemeraSprites.SOFTCORE).rgb(hot).a(0.9F * a).billboard(rib.x[i] + (rib.x[i + 1] - rib.x[i]) * ff,
                            rib.y[i] + (rib.y[i + 1] - rib.y[i]) * ff, rib.z[i] + (rib.z[i + 1] - rib.z[i]) * ff, w * 0.7F, w * 0.7F);
                }
            }
            default -> {
                float bl = m.blink(seed);
                int q = (int) Math.floor(t / 0.08F);
                float L = len * (0.6F + 0.6F * hash(q * 5 + seed));
                double tx = x + dx * L, ty = y + dy * L, tz = z + dz * L;
                pen.s(EphemeraSprites.STREAK).rgb(C).a(0.35F * a * bl).beam(x, y, z, tx, ty, tz, w * 0.9F);
                // 虚线 + 端头六边形光斑：和冰的实心棱晶在小尺寸下也分得开
                float ph = (t * 2.5F + hash(seed)) % 1;
                for (int j = 0; j < 3; j++) {
                    float f0 = (j + ph) / 3.2F, f1 = f0 + 0.17F;
                    if (f1 > 1) {
                        continue;
                    }
                    pen.rgb(hot).a(0.95F * a * Math.min(1, bl)).beam(x + (tx - x) * f0, y + (ty - y) * f0, z + (tz - z) * f0,
                            x + (tx - x) * f1, y + (ty - y) * f1, z + (tz - z) * f1, w * 0.3F);
                }
                pen.s(EphemeraSprites.HEX_0).rgb(hot).a(0.8F * a * Math.min(1.2F, bl)).billboard(tx, ty, tz, w * 1.5F, w * 1.5F, t * 0.5F);
                if (bl > 1.2F) {
                    pen.s(EphemeraSprites.FLARE).rgb(hot).a(0.9F * a).billboard(tx, ty, tz, w * 2.4F, w * 2.4F, 0.785F);
                }
            }
        }
    }

    // ---------- 沿轮廓点缀 ----------

    /**
     * 沿主体轮廓点缀：pts 是这款主体的一串边缘点（只读 x / y / z 和 n），每隔一点长一根凸起，电只在跑动的一小段上画。
     * 往外的方向：火、辐射朝上，毒朝下，其余是「切线 × 视线」，所以点的顺序决定朝哪边长，反了就往里长。
     */
    public void edge(EphemeraPen.Ribbon pts, float w, float a, int seed) {
        int n = pts.n;
        if (n < 2) {
            return;
        }
        int step = m.el == ELECTRIC ? 1 : 2;
        int at = m.el == ELECTRIC ? (int) Math.floor(hash((int) Math.floor(t / 0.12F) * 7 + seed) * n) : 0;
        float len = w * (m.el == POISON ? 2.2F : 3), ak = a * (m.el == MAGNETIC ? 0.6F : 1);
        for (int i = 0; i < n; i += step) {
            if (m.el == ELECTRIC && Math.abs(i - at) > 2) {
                continue;
            }
            double px = pts.x[i], py = pts.y[i], pz = pts.z[i];
            double ox, oy, oz;
            if (m.el == FIRE || m.el == RADIATION) {
                ox = 0;
                oy = 1;
                oz = 0;
            } else if (m.el == POISON) {
                ox = 0;
                oy = -1;
                oz = 0;
            } else {
                int i1 = Math.min(n - 1, i + 1), i0 = Math.max(0, i - 1);
                double tx = pts.x[i1] - pts.x[i0], ty = pts.y[i1] - pts.y[i0], tz = pts.z[i1] - pts.z[i0];
                double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tl < 1.0E-6) {
                    continue;
                }
                sideOf(px, py, pz, tx / tl, ty / tl, tz / tl);
                ox = side[0];
                oy = side[1];
                oz = side[2];
            }
            accent(px, py, pz, ox, oy, oz, len, w, seed * 97 + i, ak);
        }
    }

    // ---------- 身上冒的东西 ----------

    /**
     * 身上冒的粒子，各元素自己的形状、重力和流场。rate 每秒大约冒几个（收敛档的发射率由这里再乘），sc 是尺寸
     * （一般传 m.sc）。粒子、毒滩一律记作粒子类别，画完笔刷类别还原。
     */
    public void motes(Sites sites, float rate, float sc) {
        boolean lite = m.lite;
        float dt = Math.min(m.dt, 1 / 30.0F);
        if (pool == null || poolLite != lite) {
            pool = new FxParticles(lite ? 40 : 160);
            poolLite = lite;
            acc = 0;
            decN = 0;
        }
        FxParticles P = pool;
        P.thin = m.calm ? m.emit : Math.min(1, m.emit);
        curSites = sites;
        int el = m.el;
        acc += dt * rate * (lite ? 0.35F : 1);
        while (acc >= 1 && sites.n > 0) {
            acc -= 1;
            int si = Math.min(sites.n - 1, (int) (rand() * sites.n));
            float nx = sites.nx[si], ny = sites.ny[si], nz = sites.nz[si];
            EphemeraFx.randDir(rd);
            double px = sites.x[si] + rd[0] * 0.05F * sc, py = sites.y[si] + rd[1] * 0.05F * sc, pz = sites.z[si] + rd[2] * 0.05F * sc;
            switch (el) {
                case FIRE -> {
                    P.spec().pos(px, py, pz).vel(nx * 0.4F * sc, ny * 0.4F * sc + 1.4F * sc, nz * 0.4F * sc).life(rand(0.5F, 0.9F))
                            .size(0.05F * sc, 0.01F * sc).sprite(EphemeraSprites.SOFTCORE).rgb(hot).grav(-1.5F * sc).drag(1.2F).stretch(0.06F).spawn();
                    if (rand() < 0.3F) {
                        P.spec().pos(px, py, pz).vel(0, 1.0F * sc, 0).life(0.45F).size(0.18F * sc, 0.04F * sc)
                                .sprite(EphemeraSprites.FLAME_0 + (rand() < 0.5F ? 0 : 1)).rgb(C).a(0.7F).grav(-1 * sc).spawn();
                    }
                }
                case ICE -> {
                    P.spec().pos(px, py, pz).vel(nx * 0.15F * sc, ny * 0.15F * sc - 0.25F * sc, nz * 0.15F * sc).life(rand(1.0F, 1.6F))
                            .size(0.07F * sc, 0.05F * sc).sprite(EphemeraSprites.CRYSTAL).rgb(pale).rot(rand(0, 6)).spin(rand(-1, 1))
                            .grav(0.6F * sc).drag(2).floor(m.groundY).spawn();
                    if (rand() < 0.4F) {
                        P.spec().pos(px, py, pz).life(0.35F).size(0.12F * sc, 0).sprite(EphemeraSprites.FLARE).rgb(WHITE).a(0.8F).spawn();
                    }
                }
                case ELECTRIC -> {
                    EphemeraFx.randDir(rd);
                    float dx = nx + rd[0] * 0.9F, dy = ny + rd[1] * 0.9F, dz = nz + rd[2] * 0.9F;
                    float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-6F;
                    for (int j = 0; j < 2; j++) {
                        float v = rand(2.5F, 5) * sc / l;
                        P.spec().pos(px, py, pz).vel(dx * v, dy * v, dz * v).life(rand(0.08F, 0.18F)).size(0.035F * sc, 0.01F * sc)
                                .sprite(EphemeraSprites.DOT).rgb(hot).grav(6 * sc).stretch(0.05F).spawn();
                    }
                }
                case POISON -> {
                    if (rand() < 0.55F) {
                        P.spec().pos(px, py, pz).vel(0, -0.2F * sc, 0).life(1.2F).size(0.06F * sc, 0.05F * sc).sprite(EphemeraSprites.DROP)
                                .rgb(pale).grav(7 * sc).stretch(0.03F).data(DRIP).spawn();
                    } else {
                        P.spec().pos(px, py, pz).vel(nx * 0.1F * sc, ny * 0.1F * sc + 0.35F * sc, nz * 0.1F * sc).life(rand(0.8F, 1.4F))
                                .size(0.05F * sc, 0.1F * sc).sprite(EphemeraSprites.SOFTRING).rgb(C).a(0.8F).drag(1.5F).fade(0.1F, 0.15F).spawn();
                    }
                    if (rand() < 0.25F) {
                        P.spec().pos(px, py, pz).vel(0, -0.3F * sc, 0).life(1.6F).size(0.3F * sc, 0.7F * sc)
                                .sprite(EphemeraSprites.SMOKE_0 + (int) (rand() * 4)).rgb(mul(C, 0.45F)).a(0.35F).alphaBlend()
                                .grav(0.3F * sc).drag(1.5F).floor(m.groundY).rot(rand(0, 6)).spawn();
                    }
                }
                case IMPACT -> {
                    // 冲击不匀着冒，攒到拍子上一起蹦（见下面）
                    if (rand() < 0.2F) {
                        P.spec().pos(px, py, pz).vel(nx * 0.6F * sc, ny * 0.6F * sc, nz * 0.6F * sc).life(0.4F).size(0.04F * sc, 0.02F * sc)
                                .sprite(EphemeraSprites.WHITE).rgb(pale).grav(8 * sc).spin(8).rot(rand(0, 6)).spawn();
                    }
                }
                case MAGNETIC -> P.spec().pos(px + nx * 0.35F * sc, py + ny * 0.35F * sc, pz + nz * 0.35F * sc)
                        .vel(-nx * 0.2F * sc, -ny * 0.2F * sc, -nz * 0.2F * sc).life(rand(0.7F, 1.1F)).size(0.035F * sc, 0.02F * sc)
                        .sprite(EphemeraSprites.DOT).rgb(hot).stretch(0.08F).data(si * 2 + (rand() < 0.5F ? 1 : 0)).spawn();
                default -> {
                    if (rand() < 0.6F) {
                        float o = rand(0, 0.25F) * sc;
                        P.spec().pos(px + nx * o, py + ny * o, pz + nz * o).life(rand(0.06F, 0.2F)).size(0.1F * sc)
                                .sprite(EphemeraSprites.FLARE).rgb(hot).rot(0.785F).fade(0, 0).spawn();
                    } else {
                        P.spec().pos(px, py, pz).vel(nx * 0.4F * sc, ny * 0.4F * sc, nz * 0.4F * sc).life(1.0F).size(0.08F * sc, 0.2F * sc)
                                .sprite(EphemeraSprites.SOFTCORE).rgb(C).a(0.5F).drag(0.8F).spawn();
                    }
                }
            }
        }
        // 冲击：拍子落下那一下从各处往外崩一圈碎块
        if (el == IMPACT && sites.n > 0) {
            long bi = (long) Math.floor(m.t / BEAT);
            if (beatIdx != bi) {
                beatIdx = bi;
                int nn = lite ? 3 : 8;
                for (int j = 0; j < nn; j++) {
                    int si = Math.min(sites.n - 1, (int) (rand() * sites.n));
                    EphemeraFx.randDir(rd);
                    float dx = sites.nx[si] + rd[0] * 0.4F, dy = sites.ny[si] + rd[1] * 0.4F, dz = sites.nz[si] + rd[2] * 0.4F;
                    float v = rand(1.5F, 2.6F) * sc / ((float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-6F);
                    P.spec().pos(sites.x[si], sites.y[si], sites.z[si]).vel(dx * v, dy * v, dz * v).life(0.35F).size(0.05F * sc, 0.03F * sc)
                            .sprite(EphemeraSprites.WHITE).rgb(pale).grav(9 * sc).spin(10).rot(rand(0, 6)).drag(2).spawn();
                }
            }
        }
        final double floorY = m.groundY;
        final float scc = sc;
        P.step(dt, (p, i, d2) -> {
            if (el == MAGNETIC && p.data[i] >= 0) {
                // 往发射点那根竖轴吸，同时绕着转
                int si = p.data[i] >> 1;
                Sites ss = curSites;
                if (si < ss.n) {
                    double tx = ss.x[si] - p.x[i], ty = ss.y[si] - p.y[i], tz = ss.z[si] - p.z[i];
                    double l = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1.0E-3;
                    float w = (p.data[i] & 1) != 0 ? 1 : -1;
                    double cl = Math.sqrt(tz * tz + tx * tx) + 1.0E-6;
                    double ax = -tz / cl * w * 1.2F * scc, az = tx / cl * w * 1.2F * scc;
                    float k = 1 - d2 * 2;
                    p.vx[i] = (float) (p.vx[i] * k + (tx * 3 / l * scc + ax) * d2 * 3);
                    p.vy[i] = (float) (p.vy[i] * k + (ty * 3 / l * scc) * d2 * 3);
                    p.vz[i] = (float) (p.vz[i] * k + (tz * 3 / l * scc + az) * d2 * 3);
                }
            }
            if (p.data[i] == DRIP && p.y[i] < floorY + 0.04) {
                if (decN < (poolLite ? 3 : 10)) {
                    decX[decN] = p.x[i];
                    decY[decN] = floorY + 0.03;
                    decZ[decN] = p.z[i];
                    decT[decN] = t;
                    decR[decN] = rand(0.12F, 0.22F) * scc;
                    decN++;
                }
                p.age[i] = p.life[i];
            }
        });
        int cat = pen.cat;
        pen.cat = EphemeraStaging.PARTICLE;
        P.draw(pen, m.size, 1, Float.NaN);
        // 毒滴落地留的小滩
        int w = 0;
        for (int i = 0; i < decN; i++) {
            if (t - decT[i] >= 1.4F) {
                continue;
            }
            decX[w] = decX[i];
            decY[w] = decY[i];
            decZ[w] = decZ[i];
            decT[w] = decT[i];
            decR[w] = decR[i];
            w++;
        }
        decN = w;
        for (int i = 0; i < decN; i++) {
            float f = (t - decT[i]) / 1.4F, r = decR[i] * (0.6F + 0.6F * Math.min(1, f * 4));
            pen.s(EphemeraSprites.SPLAT_0 + (((int) (decT[i] * 10)) & 3)).alphaBlend().rgb(mul(C, 0.35F)).a(0.5F * (1 - f))
                    .flat(decX[i], decY[i], decZ[i], r * 2, r * 2, decT[i] * 7);
            pen.s(EphemeraSprites.SOFTRING).rgb(C).a(0.5F * (1 - f)).flat(decX[i], decY[i] + 0.005, decZ[i], r * 1.6F, r * 1.6F);
        }
        pen.cat = cat;
    }

    // ---------- 贴地 ----------

    /** 贴地的元素纹样：(x, y, z) 是地面点（一般 m.x, m.groundY, m.z），r 半径 */
    public void ground(double x, double y, double z, float r, float a) {
        if (a <= 0.01F) {
            return;
        }
        double gy = y + 0.035;
        switch (m.el) {
            case FIRE -> {
                pen.s(EphemeraSprites.SOFT).alphaBlend().rgb(0x0D0503).a(0.45F * a).flat(x, gy, z, r * 2.2F, r * 2.2F);
                pen.s(EphemeraSprites.SOFTRING).rgb(C).a((0.45F + 0.15F * (float) Math.sin(t * 7)) * a).flat(x, gy, z, r * 2, r * 2, t * 0.5F);
            }
            case ICE -> {
                // 六瓣霜花，平躺在地上，慢慢转
                pen.s(EphemeraSprites.SOFT).rgb(pale).a(0.18F * a).flat(x, gy, z, r * 2.1F, r * 2.1F);
                for (int i = 0; i < 6; i++) {
                    float ang = i / 6.0F * TAU + t * 0.05F;
                    double dx = Math.cos(ang), dz = Math.sin(ang), sx = -dz, sz = dx;
                    double tx = x + dx * r * 0.95, tz = z + dz * r * 0.95, mx = x + dx * r * 0.35, mz = z + dz * r * 0.35;
                    pen.rgb(pale).a(0.55F * a);
                    pen.quad(tx, gy, tz, mx + sx * r * 0.13, gy, mz + sz * r * 0.13, x, gy, z, mx - sx * r * 0.13, gy, mz - sz * r * 0.13);
                    double m2x = x + dx * r * 0.62, m2z = z + dz * r * 0.62;
                    for (int sg = -1; sg <= 1; sg += 2) {
                        pen.rgb(WHITE).a(0.35F * a);
                        pen.quad(m2x + (dx + sx * sg) * r * 0.2, gy, m2z + (dz + sz * sg) * r * 0.2, m2x + sx * 0.02 * r, gy, m2z + sz * 0.02 * r,
                                m2x, gy, m2z, m2x - sx * 0.02 * r, gy, m2z - sz * 0.02 * r);
                    }
                }
            }
            case ELECTRIC -> {
                int q = (int) Math.floor(t / 0.07F);
                pen.s(EphemeraSprites.SOFT).rgb(C).a(0.2F * a).flat(x, gy, z, r * 1.6F, r * 1.6F);
                for (int i = 0; i < 4; i++) {
                    if (hash(q * 11 + i) < 0.35F) {
                        continue;
                    }
                    float ang = hash(q * 3 + i * 17) * TAU;
                    double dx = Math.cos(ang), dz = Math.sin(ang), L = r * rand(0.7F, 1.1F);
                    zig(x, gy, z, x + dx * L, gy, z + dz * L, 5, r * 0.12F, q * 5 + i, -dz, 0, dx);
                    zigRibbon(rib, 5, r * 0.05F);
                    pen.rgb(hot).a(0.9F * a).ribbon(rib, EphemeraPen.FACE_FLAT, 0, 1);
                }
            }
            case POISON -> {
                pen.s(EphemeraSprites.SPLAT_1).alphaBlend().rgb(mul(C, 0.3F)).a(0.55F * a).flat(x, gy, z, r * 2.2F, r * 2.0F, 0.4F);
                pen.s(EphemeraSprites.SPLAT_1).rgb(C).a(0.35F * a).flat(x, gy + 0.004, z, r * 2.0F, r * 1.8F, 0.4F);
                for (int i = 0; i < 3; i++) {
                    float ph = (t * 0.6F + i / 3.0F) % 1;
                    float ang = i * 2.1F + (float) Math.floor(t * 0.6F + i / 3.0F) * 1.3F;
                    float sz = r * (0.08F + 0.12F * ph);
                    pen.s(EphemeraSprites.SOFTRING).rgb(pale).a((ph < 0.9F ? 0.8F : 0) * a)
                            .billboard(x + Math.cos(ang) * r * 0.55, gy + 0.01 + ph * 0.05 * m.sc, z + Math.sin(ang) * r * 0.55, sz, sz);
                }
            }
            case IMPACT -> {
                float b = m.beat(0);
                float ph = (float) ((m.t % BEAT) / BEAT);
                pen.s(EphemeraSprites.SOFT).alphaBlend().rgb(0x1A1A1A).a(0.3F * a).flat(x, gy, z, r * 1.8F, r * 1.8F);
                pen.s(EphemeraSprites.SHOCK).rgb(pale).a((1 - ph) * 0.8F * a).flat(x, gy, z, r * (0.8F + 1.8F * ph), r * (0.8F + 1.8F * ph));
                pen.s(EphemeraSprites.SOFTRING).rgb(C).a((0.25F + 0.5F * b) * a).flat(x, gy, z, r, r);
            }
            case MAGNETIC -> {
                // 两极之间的铁屑弧：一串短划沿偶极场线排开，慢慢转
                float rot = t * 0.3F, ca = (float) Math.cos(rot), sa = (float) Math.sin(rot);
                int rows = m.lite ? 4 : 10;
                for (int i = 0; i < rows; i++) {
                    int sd = i < rows / 2 ? 1 : -1, j = i % (rows / 2);
                    float A = 0.2F + j * 0.15F * (m.lite ? 2.5F : 1);
                    for (int k2 = 0; k2 < 5; k2++) {
                        float th = (k2 + 0.5F) / 5 * (float) Math.PI;
                        float px = (float) Math.cos(th) * r * 0.85F, pz = sd * (float) Math.sin(th) * r * A * 1.6F;
                        float tx = -(float) Math.sin(th), tz = sd * (float) Math.cos(th) * A * 1.6F, tl = (float) Math.sqrt(tx * tx + tz * tz);
                        double cx = x + px * ca - pz * sa, cz = z + px * sa + pz * ca;
                        double dx = (tx * ca - tz * sa) / tl, dz = (tx * sa + tz * ca) / tl;
                        float fl = ((t * 0.8F - k2 / 5.0F + j * 0.13F) % 1 + 1) % 1;
                        rib.clear();
                        rib.add(cx - dx * r * 0.07, gy, cz - dz * r * 0.07, r * 0.03F, 1);
                        rib.add(cx + dx * r * 0.07, gy, cz + dz * r * 0.07, r * 0.03F, 1);
                        pen.rgb(pale).a((0.25F + 0.5F * (1 - fl)) * a).ribbon(rib, EphemeraPen.FACE_FLAT, 0, 1);
                    }
                }
                pen.s(EphemeraSprites.SOFTCORE).rgb(C).a(0.5F * a).flat(x, gy, z, r * 0.6F, r * 0.6F);
            }
            default -> {
                float bl = m.blink(3);
                pen.s(EphemeraSprites.HEX_0).rgb(C).a(0.3F * a * bl).flat(x, gy, z, r * 2, r * 2, t * 0.2F);
                pen.s(EphemeraSprites.SOFT).rgb(C).a(0.25F * a * (0.8F + 0.2F * (float) Math.sin(t * 5))).flat(x, gy, z, r * 2.4F, r * 2.4F);
                for (int i = 0; i < 3; i++) {
                    float ang = i / 3.0F * TAU + t * 0.4F;
                    double dx = Math.cos(ang), dz = Math.sin(ang);
                    rib.clear();
                    rib.add(x + dx * r * 0.25, gy, z + dz * r * 0.25, r * 0.18F, 1);
                    rib.add(x + dx * r * 0.9, gy, z + dz * r * 0.9, r * 0.5F, 1);
                    pen.s(EphemeraSprites.SOFT).rgb(C).a(0.35F * a * bl).ribbon(rib, EphemeraPen.FACE_FLAT, 0, 1);
                }
            }
        }
    }

    // ---------- 事件爆发 ----------

    /** 在 (x, y, z) 放一次爆发（一般是地面点），r 半径，k 强度；之后每帧 {@link #bursts} 画完 */
    public void fire(double x, double y, double z, float r, float k) {
        if (bn == MAX_BURSTS) {
            for (int i = 1; i < bn; i++) {
                bx[i - 1] = bx[i];
                by[i - 1] = by[i];
                bz[i - 1] = bz[i];
                bt[i - 1] = bt[i];
                br[i - 1] = br[i];
                bk[i - 1] = bk[i];
            }
            bn--;
        }
        bx[bn] = x;
        by[bn] = y;
        bz[bn] = z;
        bt[bn] = t;
        br[bn] = r;
        bk[bn] = k;
        bn++;
    }

    /** 落地、挥砍、受击时在 (x, y, z) 各放一次爆发；每帧调一次 */
    public void events(double x, double y, double z, float r) {
        if (seenLand == Integer.MIN_VALUE) {
            seenLand = m.nLand;
            seenSwing = m.nSwing;
            seenHurt = m.nHurt;
        }
        if (seenLand != m.nLand) {
            seenLand = m.nLand;
            if (m.since(m.tLand) < 0.2F) {
                fire(x, y, z, r, 1);
            }
        }
        if (seenSwing != m.nSwing) {
            seenSwing = m.nSwing;
            if (m.since(m.tSwing) < 0.2F) {
                fire(x, y, z, r * 0.8F, 0.8F);
            }
        }
        if (seenHurt != m.nHurt) {
            seenHurt = m.nHurt;
            if (m.since(m.tHurt) < 0.2F) {
                fire(x, y, z, r, 0.9F);
            }
        }
    }

    /** 画所有还没放完的爆发 */
    public void bursts() {
        float life = 0.9F * m.burstTime;
        int w = 0;
        for (int i = 0; i < bn; i++) {
            if (t - bt[i] < life && t >= bt[i]) {
                bx[w] = bx[i];
                by[w] = by[i];
                bz[w] = bz[i];
                bt[w] = bt[i];
                br[w] = br[i];
                bk[w] = bk[i];
                w++;
            }
        }
        bn = w;
        int N = m.lite ? 6 : 12;
        for (int i = 0; i < bn; i++) {
            float f = (t - bt[i]) / life, R = br[i] * (0.4F + m.burst), A = bk[i];
            double cx = bx[i], cy = by[i], cz = bz[i], gy = cy + 0.04;
            switch (m.el) {
                case FIRE -> {
                    float h = (float) Math.sin(Math.min(1, f * 1.6F) * Math.PI);
                    for (int j = 0; j < N; j++) {
                        float ang = (float) j / N * TAU, rr = R * (0.5F + 0.6F * f);
                        accent(cx + Math.cos(ang) * rr, gy, cz + Math.sin(ang) * rr, 0, 1, 0, R * 0.9F * h, R * 0.22F, 300 + j, A * h);
                    }
                }
                case ICE -> {
                    float g = f < 0.2F ? f / 0.2F : 1, gone = f > 0.7F ? (f - 0.7F) / 0.3F : 0;
                    for (int j = 0; j < N; j++) {
                        float ang = (float) j / N * TAU + 0.3F;
                        double dx = Math.cos(ang), dz = Math.sin(ang);
                        double ax = cx + dx * R * 0.55, az = cz + dz * R * 0.55;
                        double tx = ax + dx * R * 0.35 * g, ty = gy + R * 0.7F * g * (0.7F + 0.5F * hash(j)) - R * gone * 0.6F, tz = az + dz * R * 0.35 * g;
                        pen.rgb(pale).a(0.8F * A * (1 - gone));
                        kite(ax, gy, az, tx, ty, tz, R * 0.2F, 0.3F);
                        pen.rgb(WHITE).a(0.8F * A * (1 - gone));
                        kite(ax, gy, az, tx, ty, tz, R * 0.06F, 0.4F);
                    }
                }
                case ELECTRIC -> {
                    float on = f < 0.6F ? m.flick(99) : 0;
                    int q = (int) Math.floor(t / 0.05F);
                    double topY = cy + R * 0.6F;
                    for (int j = 0; j < N / 2; j++) {
                        float ang = hash(j * 7 + q) * TAU;
                        double ex = cx + Math.cos(ang) * R * (0.8F + 0.6F * f), ez = cz + Math.sin(ang) * R * (0.8F + 0.6F * f);
                        double dx = ex - cx, dy = gy - topY, dz = ez - cz, l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1.0E-6;
                        sideOf(cx, topY, cz, dx / l, dy / l, dz / l);
                        zig(cx, topY, cz, ex, gy, ez, 7, R * 0.15F, j * 13 + q, side[0], side[1], side[2]);
                        zigRibbon(rib, 7, R * 0.12F);
                        pen.s(EphemeraSprites.SOFT).rgb(C).a(0.4F * A * on).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                        zigRibbon(rib, 7, R * 0.025F);
                        pen.rgb(hot).a(A * on).ribbon(rib, EphemeraPen.FACE_CAM, 0, 1);
                    }
                    if (f < 0.25F) {
                        pen.s(EphemeraSprites.FLARE).rgb(hot).a((1 - f / 0.25F) * A).billboard(cx, topY, cz, R * 2, R * 2);
                    }
                }
                case POISON -> {
                    float rr = R * (0.4F + 1.3F * (float) Math.sqrt(f));
                    pen.s(EphemeraSprites.SPLAT_2).alphaBlend().rgb(mul(C, 0.3F)).a(0.6F * A * (1 - f)).flat(cx, gy, cz, rr * 2, rr * 2, bt[i]);
                    pen.s(EphemeraSprites.SPLAT_2).rgb(C).a(0.5F * A * (1 - f)).flat(cx, gy + 0.005, cz, rr * 1.8F, rr * 1.8F, bt[i]);
                    float fl = Math.min(1, f * 1.4F);
                    for (int j = 0; j < N; j++) {
                        float ang = (float) j / N * TAU;
                        double rad = R * (0.3F + 1.1F * fl);
                        pen.s(EphemeraSprites.DROP).rgb(pale).a(A * (1 - f)).billboard(cx + Math.cos(ang) * rad,
                                gy + R * 1.4F * fl * (1 - fl) * (0.6F + 0.6F * hash(j)), cz + Math.sin(ang) * rad, R * 0.14F, R * 0.18F);
                    }
                }
                case IMPACT -> {
                    for (int k = 0; k < 2; k++) {
                        float dl = k == 0 ? 0 : 0.12F, wk = k == 0 ? 1 : 0.6F;
                        float ff = Math.max(0, f - dl) / (1 - dl);
                        if (ff <= 0) {
                            continue;
                        }
                        pen.s(EphemeraSprites.SHOCK).rgb(pale).a((1 - ff) * A * wk).flat(cx, gy, cz, R * (0.6F + 3 * ff), R * (0.6F + 3 * ff));
                    }
                    for (int j = 0; j < N; j++) {
                        float ang = (float) j / N * TAU + 0.2F;
                        double dx = Math.cos(ang), dz = Math.sin(ang), a0 = R * (0.5F + 2.2F * f), a1 = a0 + R * 0.6F * (1 - f);
                        rib.clear();
                        rib.add(cx + dx * a0, gy, cz + dz * a0, R * 0.06F, 1);
                        rib.add(cx + dx * a1, gy, cz + dz * a1, R * 0.01F, 1);
                        pen.rgb(WHITE).a((1 - f) * A).ribbon(rib, EphemeraPen.FACE_FLAT, 0, 1);
                    }
                }
                case MAGNETIC -> {
                    // 先往里收成一点，再弹开一圈
                    float rr = f < 0.4F ? R * 1.8F * (1 - f / 0.4F) + R * 0.1F : R * 0.1F + R * 2.4F * (f - 0.4F) / 0.6F;
                    pen.s(EphemeraSprites.SOFTRING).rgb(pale).a(A * (f < 0.4F ? 0.8F : 0.8F * (1 - (f - 0.4F) / 0.6F)))
                            .flat(cx, gy, cz, rr * 2, rr * 2, t);
                    for (int j = 0; j < N / 2; j++) {
                        float ang = (float) j / (N / 2) * TAU + t * 2;
                        double dx = Math.cos(ang), dy = 0.3, dz = Math.sin(ang), l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        accent(cx, cy + R * 0.4F, cz, dx / l, dy / l, dz / l, R * 0.9F * (1 - f), R * 0.25F, 500 + j, A * (1 - f));
                    }
                }
                default -> {
                    double py = cy + R * 0.5F;
                    if (f < 0.2F) {
                        pen.s(EphemeraSprites.SOFTCORE).rgb(hot).a(A * (1 - f / 0.2F)).billboard(cx, py, cz, R * 3 * (1 - f), R * 3 * (1 - f));
                    }
                    pen.s(EphemeraSprites.HEX_1).rgb(C).a(A * (1 - f) * 0.7F).flat(cx, gy, cz, R * (0.5F + 3 * f), R * (0.5F + 3 * f));
                    int q = (int) Math.floor(t / 0.06F);
                    for (int j = 0; j < N; j++) {
                        EphemeraFx.randDir(rd);
                        if (hash(j + q * 9) < 0.4F) {
                            continue;
                        }
                        double dx = rd[0], dy = Math.abs(rd[1]), dz = rd[2];
                        pen.rgb(hot).a(A * (1 - f)).beam(cx + dx * R * 0.3F, py + dy * R * 0.3F, cz + dz * R * 0.3F,
                                cx + dx * R * (0.6F + 1.6F * f), py + dy * R * (0.6F + 1.6F * f), cz + dz * R * (0.6F + 1.6F * f), R * 0.05F);
                    }
                }
            }
        }
    }
}
