package pers.roinflam.kuvalich.client.ephemera;

/**
 * 幻纹用的柔光贴图：开游戏时现算一张 1024 的图集（格子 128，白色 + 透明度，个别带亮度），不带图片资源。
 * 画法里只认这里的编号，顶点色乘上去就是最终颜色。不引用客户端类，GameTest 能直接调。
 */
public final class EphemeraSprites {

    public static final int ATLAS = 1024;
    public static final int CELL = 128;
    private static final int PER_ROW = ATLAS / CELL;

    public static final int WHITE = 0;
    public static final int SOFT = 1;
    public static final int SOFTCORE = 2;
    public static final int SOFTRING = 3;
    public static final int SHOCK = 4;
    public static final int FLARE = 5;
    public static final int STREAK = 6;
    public static final int COMET = 7;
    public static final int DOT = 8;
    public static final int SLASH = 9;
    public static final int WISP = 10;
    public static final int FLOW_0 = 11;
    public static final int FLOW_1 = 12;
    public static final int FLOW_2 = 13;
    public static final int SMOKE_0 = 14;
    public static final int FLAME_0 = 18;
    public static final int CRACK_0 = 20;
    public static final int CRACK_1 = 21;
    public static final int CRYSTAL = 22;
    public static final int BAND = 23;
    public static final int CRESCENT = 24;
    public static final int TONGUE = 25;
    public static final int HAZE = 26;
    public static final int VEIL = 27;
    public static final int SLASHBAND = 28;
    public static final int COUNT = 29;

    private EphemeraSprites() {
    }

    /** 横向半张一个周期（滚动纹理取 [x, x + 0.5] 这一段无缝），u 可以按 0.5 整段平移 */
    public static boolean periodic(int id) {
        return id == FLOW_0 || id == FLOW_1 || id == FLOW_2 || id == VEIL || id == BAND || id == HAZE;
    }

    // 往里缩半个像素，线性过滤不会采到隔壁格
    public static float u0(int id) {
        return ((id % PER_ROW) * CELL + 0.5F) / ATLAS;
    }

    public static float v0(int id) {
        return ((id / PER_ROW) * CELL + 0.5F) / ATLAS;
    }

    public static float u1(int id) {
        return ((id % PER_ROW + 1) * CELL - 0.5F) / ATLAS;
    }

    public static float v1(int id) {
        return ((id / PER_ROW + 1) * CELL - 0.5F) / ATLAS;
    }

    /** 整张图集，ARGB，行优先 */
    public static int[] generate() {
        int[] px = new int[ATLAS * ATLAS];
        float[] lum = new float[CELL * CELL];
        float[] alpha = new float[CELL * CELL];
        for (int id = 0; id < COUNT; id++) {
            java.util.Arrays.fill(lum, 1.0F);
            java.util.Arrays.fill(alpha, 0.0F);
            paint(id, lum, alpha);
            int ox = (id % PER_ROW) * CELL, oy = (id / PER_ROW) * CELL;
            for (int j = 0; j < CELL; j++) {
                for (int i = 0; i < CELL; i++) {
                    int k = j * CELL + i;
                    int a = Math.round(clamp(alpha[k]) * 255.0F);
                    int l = Math.round(clamp(lum[k]) * 255.0F);
                    px[(oy + j) * ATLAS + ox + i] = (a << 24) | (l << 16) | (l << 8) | l;
                }
            }
        }
        return px;
    }

    private static float clamp(double v) {
        return (float) (v < 0 ? 0 : v > 1 ? 1 : v);
    }

    private static double cl(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double gauss(double x, double sg) {
        return Math.exp(-(x * x) / (2 * sg * sg));
    }

    private static double edge0(double r) {
        return cl((1 - r) / 0.08);
    }

    // 横向周期为半张图的噪声：u 走一圈正好两个周期
    private static double pnoise(double u, double v, double freq, int seed, int oct) {
        double ang = u * Math.PI * 4, r = freq / (Math.PI * 2) * 2;
        return EphemeraFx.fbm3(Math.cos(ang) * r + 10, Math.sin(ang) * r + 10, v * freq, seed, oct);
    }

    /** 竖直方向分段线性的透明度（canvas 线性渐变的写法） */
    private static double stops(double y, double[] s) {
        if (y <= s[0]) {
            return s[1];
        }
        for (int i = 2; i < s.length; i += 2) {
            if (y <= s[i]) {
                double f = (y - s[i - 2]) / Math.max(1e-9, s[i] - s[i - 2]);
                return s[i - 1] + (s[i + 1] - s[i - 1]) * f;
            }
        }
        return s[s.length - 1];
    }

    private static final double[] BAND_STOPS = {0, 0, 0.1, 0, 0.2, 0.1, 0.33, 0.5, 0.43, 0.92, 0.5, 1, 0.57, 0.92,
            0.67, 0.5, 0.8, 0.1, 0.9, 0, 1, 0};
    private static final double[] HAZE_STOPS = {0, 0, 0.12, 0.05, 0.28, 0.32, 0.4, 0.75, 0.5, 1, 0.6, 0.75, 0.72, 0.32,
            0.88, 0.05, 1, 0};

    private static void paint(int id, float[] lum, float[] alpha) {
        if (id == CRACK_0 || id == CRACK_1) {
            crack(id - CRACK_0, alpha);
            return;
        }
        if (id == CRYSTAL) {
            crystal(alpha);
            return;
        }
        int n = CELL;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double u = (i + 0.5) / n, v = (j + 0.5) / n;
                double r = Math.hypot(u - 0.5, v - 0.5) * 2;
                double a;
                double l = 1;
                switch (id) {
                    case WHITE -> a = 1;
                    case SOFT -> a = Math.max(0, gauss(r, 0.36) - gauss(1, 0.36)) / (1 - gauss(1, 0.36));
                    case SOFTCORE -> a = (0.95 * gauss(r, 0.09) + 0.5 * gauss(r, 0.34)) * edge0(r);
                    case SOFTRING -> a = gauss(r - 0.72, 0.09) * edge0(r);
                    case SHOCK -> a = (gauss(r - 0.86, 0.035) + 0.35 * gauss(r - 0.74, 0.1)) * edge0(r);
                    case FLARE -> {
                        double x = u - 0.5, y = v - 0.5;
                        double arm = gauss(y, 0.012) * Math.pow(cl(1 - Math.abs(x) * 2), 2)
                                + gauss(x, 0.012) * Math.pow(cl(1 - Math.abs(y) * 2), 2);
                        a = cl(gauss(r, 0.08) + 0.9 * arm) * edge0(r);
                    }
                    case STREAK -> a = gauss(v - 0.5, 0.15) * Math.pow(Math.sin(Math.PI * u), 0.7);
                    case COMET -> {
                        // 头在右（u = 1），往左拖细
                        double w = 0.04 + 0.13 * Math.pow(u, 0.8);
                        a = gauss(v - 0.5, w) * Math.pow(u, 1.4) * cl((1 - u) / 0.06);
                    }
                    case DOT -> a = cl((0.42 - r) * 14);
                    case SLASH -> {
                        double x = (u - 0.5) * 2, y = (v - 0.5) * 2;
                        double r1 = Math.hypot(x, y - 0.6), r2 = Math.hypot(x, y - 0.95);
                        double ang = Math.atan2(x, -(y - 0.6));
                        double taper = Math.pow(Math.max(0, Math.cos(ang * 1.1)), 1.5);
                        a = cl((1.25 - r1) * 14) * cl((r2 - 1.15) * 5) * taper * edge0(r * 0.9);
                    }
                    case WISP -> {
                        double cx = 0.5 + 0.17 * Math.sin(v * Math.PI * 2.2 + 0.6) * Math.sin(v * Math.PI);
                        a = gauss(u - cx, 0.045 + 0.04 * (1 - v)) * Math.pow(Math.sin(Math.PI * v), 0.9);
                    }
                    case FLOW_0 -> {
                        double nz = pnoise(u, v, 5, 3, 4);
                        l = 0.8 + 0.2 * nz;
                        a = gauss(v - 0.5, 0.2) * cl((nz - 0.32) * 2.6);
                    }
                    case FLOW_1 -> {
                        double nz = pnoise(u, v, 4, 7, 3);
                        double ridge = 1 - Math.abs(2 * nz - 1);
                        a = gauss(v - 0.5, 0.26) * Math.pow(ridge, 7);
                    }
                    case FLOW_2 -> {
                        double s = 0.5 + 0.5 * Math.sin((u * 6 + v * 0.8) * Math.PI * 2 + pnoise(u, v, 3, 11, 2) * 3);
                        a = gauss(v - 0.5, 0.22) * Math.pow(s, 3);
                    }
                    case BAND -> a = stops(v, BAND_STOPS);
                    case HAZE -> a = stops(v, HAZE_STOPS);
                    case CRESCENT -> {
                        // 贴地的刀波：外沿利、内沿虚，鼓的那边朝 v = 1
                        double x = u * 2 - 1, y = v * 2 - 1;
                        double r1 = Math.hypot(x, y + 0.35), r2 = Math.hypot(x, y + 1.2);
                        double inner = cl((r2 - 1.72) / 0.3);
                        a = cl((1.2 - r1) * 16) * inner * inner * cl((0.95 - Math.abs(x)) / 0.35);
                    }
                    case TONGUE -> {
                        // 底宽顶尖，中间一道亮芯，边上虚
                        double x = u * 2 - 1, y = 1 - v;
                        double cx = 0.22 * Math.sin(y * 4.6 + 0.4) * y;
                        double w = 0.86 * Math.pow(1 - y, 0.7) * Math.min(1, y / 0.16 + 0.45);
                        double dx = Math.abs(x - cx) / Math.max(w, 1e-3);
                        a = cl(Math.pow(cl(1 - dx), 1.3) * (0.4 + 0.6 * cl(1 - dx * 2.2)) * cl(y / 0.05) * cl((1 - y) / 0.12) * 1.5);
                    }
                    case VEIL -> {
                        double ph = u * Math.PI * 4;
                        double st = 0.5 + 0.5 * Math.sin(ph * 3 + 2.2 * Math.sin(v * 7 + Math.sin(ph) * 1.5));
                        double st2 = 0.5 + 0.5 * Math.sin(ph * 5 - v * 9 + Math.cos(ph * 2) * 2);
                        double edge = Math.min(1, Math.min(v / 0.12, (1 - v) / 0.12));
                        a = Math.min(1, (0.55 + 0.45 * st * st + 0.3 * st2 * st2 * st2) * Math.max(0, edge));
                    }
                    case SLASHBAND -> {
                        double hard = Math.min(1, (v - 0.08) / 0.05), soft = Math.pow(Math.max(0, 1 - v), 1.6);
                        double streak = 0.65 + 0.35 * Math.sin(v * 40 + Math.sin(u * 12) * 2);
                        a = Math.max(0, hard) * soft * streak * Math.min(1, Math.min(u / 0.1, (1 - u) / 0.05));
                    }
                    default -> {
                        if (id >= SMOKE_0 && id < SMOKE_0 + 4) {
                            int k = id - SMOKE_0;
                            double nz = EphemeraFx.fbm3(u * 4.2, v * 4.2, k * 7.1, 40 + k, 5);
                            a = cl((nz - 0.28 + (1 - r) * 0.75 - 0.42) * 2.2) * edge0(r);
                            l = 0.78 + 0.22 * EphemeraFx.fbm3(u * 6, v * 6, k, 90 + k, 3);
                        } else if (id >= FLAME_0 && id < FLAME_0 + 2) {
                            // 底圆顶尖的火舌，v = 0 在顶
                            int k = id - FLAME_0;
                            double y = 1 - v, x = (u - 0.5) * 2;
                            double wob = (EphemeraFx.fbm3(u * 5, v * 3, k * 3, 70 + k, 3) - 0.5) * 0.35;
                            double w = 0.62 * Math.pow(cl(1 - y), 0.75) * Math.pow(cl(y / 0.22), 0.5);
                            double dx = Math.abs(x + wob * y) / Math.max(w, 1e-3);
                            a = cl((1 - dx) * 3) * cl(y / 0.05);
                            l = 0.75 + 0.25 * (1 - y);
                        } else {
                            a = 0;
                        }
                    }
                }
                int k = j * n + i;
                alpha[k] = clamp(a);
                lum[k] = clamp(l);
            }
        }
    }

    /** 闪电裂纹：中点位移折线，三层线宽叠柔光（照 canvas 描边 + shadowBlur 的样子近似） */
    private static void crack(int which, float[] alpha) {
        int[] seed = {1000 + which * 77};
        double[] xs = {0.04, 0.96};
        double[] ys = {0.5, 0.5};
        for (int lv = 0; lv < 6; lv++) {
            int m = xs.length;
            double[] nx = new double[m * 2 - 1];
            double[] ny = new double[m * 2 - 1];
            nx[0] = xs[0];
            ny[0] = ys[0];
            for (int k = 1; k < m; k++) {
                nx[k * 2 - 1] = (xs[k - 1] + xs[k]) / 2;
                ny[k * 2 - 1] = (ys[k - 1] + ys[k]) / 2 + (EphemeraFx.hash(seed[0]++) - 0.5) * 0.5 / Math.pow(1.9, lv);
                nx[k * 2] = xs[k];
                ny[k * 2] = ys[k];
            }
            xs = nx;
            ys = ny;
        }
        int n = CELL;
        double[][] layers = {{6, 14, 0.35}, {2.5, 6, 0.8}, {1.2, 0, 1}};
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double px = i + 0.5, py = j + 0.5;
                double best = 1e9;
                for (int k = 1; k < xs.length; k++) {
                    double ax = xs[k - 1] * n, ay = cl2(ys[k - 1]) * n, bx = xs[k] * n, by = cl2(ys[k]) * n;
                    best = Math.min(best, segDist(px, py, ax, ay, bx, by));
                }
                double keep = 1;
                for (double[] ly : layers) {
                    double half = ly[0] / 2;
                    double cov = cl(half - best + 0.5);
                    double sh = ly[1] > 0 ? gauss(Math.max(0, best - half), ly[1] / 2) : 0;
                    keep *= 1 - ly[2] * Math.max(cov, sh);
                }
                alpha[j * n + i] = clamp(1 - keep);
            }
        }
    }

    private static double cl2(double y) {
        return y < 0.1 ? 0.1 : y > 0.9 ? 0.9 : y;
    }

    private static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
        double t = l2 < 1e-9 ? 0 : cl(((px - ax) * dx + (py - ay) * dy) / l2);
        return Math.hypot(px - ax - dx * t, py - ay - dy * t);
    }

    /** 晶体：硬边多边形，斜向渐变，白描边 + 中线 */
    private static void crystal(float[] alpha) {
        double[] xs = {0.5, 0.8, 0.62, 0.36, 0.2};
        double[] ys = {0.02, 0.35, 0.98, 0.96, 0.38};
        int n = CELL;
        double lw = 0.03 * n;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double px = (i + 0.5) / n, py = (j + 0.5) / n;
                boolean in = false;
                double ed = 1e9;
                for (int k = 0, m = xs.length, p = m - 1; k < m; p = k++) {
                    if ((ys[k] > py) != (ys[p] > py) && px < (xs[p] - xs[k]) * (py - ys[k]) / (ys[p] - ys[k]) + xs[k]) {
                        in = !in;
                    }
                    ed = Math.min(ed, segDist(px * n, py * n, xs[p] * n, ys[p] * n, xs[k] * n, ys[k] * n));
                }
                double t = (px + py) / 2;
                double g = t < 0.55 ? 1 - 0.45 * t / 0.55 : 0.55 + 0.3 * (t - 0.55) / 0.45;
                double fill = in ? g * cl(ed + 0.5) : 0;
                double stroke = cl(lw / 2 - ed + 0.5);
                double mid = 0.9 * cl(lw / 2 - segDist(px * n, py * n, 0.5 * n, 0.02 * n, 0.5 * n, 0.97 * n) + 0.5);
                double a = 1 - (1 - fill) * (1 - stroke) * (1 - (in ? mid : 0));
                alpha[j * n + i] = clamp(a);
            }
        }
    }
}
