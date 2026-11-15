package pers.roinflam.kuvalich.client.ephemera;

/** 画法共用的小工具：确定性哈希和噪声、事件包络、随机数、RGB 混色。哈希和噪声的结果和网页原型逐位一致。 */
public final class EphemeraFx {

    public static final float TAU = (float) (Math.PI * 2);
    public static final int WHITE = 0xFFFFFF;

    private static long seed = 0x9E3779B97F4A7C15L;

    private EphemeraFx() {
    }

    public static float hash(int i) {
        int x = i ^ 0x9E3779B9;
        x = (x ^ (x >>> 16)) * 0x85EBCA6B;
        x = (x ^ (x >>> 13)) * 0xC2B2AE35;
        x ^= x >>> 16;
        return (float) ((x & 0xFFFFFFFFL) / 4294967296.0D);
    }

    public static float noise(double x, int seed) {
        int i = (int) Math.floor(x);
        float f = (float) (x - i), u = f * f * (3 - 2 * f);
        int s = seed * 7919;
        return hash(i + s) * (1 - u) + hash(i + 1 + s) * u;
    }

    private static double hash3(int i, int j, int k, int s) {
        return hash((i * 73856093) ^ (j * 19349663) ^ (k * 83492791) ^ (s * 0x9E3779B1));
    }

    public static double noise3(double x, double y, double z, int seed) {
        int i = (int) Math.floor(x), j = (int) Math.floor(y), k = (int) Math.floor(z);
        double fx = x - i, fy = y - j, fz = z - k;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        double a = lerp(lerp(hash3(i, j, k, seed), hash3(i + 1, j, k, seed), fx),
                lerp(hash3(i, j + 1, k, seed), hash3(i + 1, j + 1, k, seed), fx), fy);
        double b = lerp(lerp(hash3(i, j, k + 1, seed), hash3(i + 1, j, k + 1, seed), fx),
                lerp(hash3(i, j + 1, k + 1, seed), hash3(i + 1, j + 1, k + 1, seed), fx), fy);
        return lerp(a, b, fz);
    }

    public static double fbm3(double x, double y, double z, int seed, int oct) {
        double a = 0, w = 0.5, f = 1, n = 0;
        for (int o = 0; o < oct; o++) {
            a += noise3(x * f, y * f, z * f, seed + o * 31) * w;
            n += w;
            w *= 0.5;
            f *= 2.03;
        }
        return a / n;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static float clamp(float x, float a, float b) {
        return x < a ? a : x > b ? b : x;
    }

    public static float smooth(float t) {
        t = clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** 事件包络：since 秒前发生，rise 秒升到 1，hold 秒保持，fall 秒落回 0；没发生或已过完是 0 */
    public static float env0(float since, float rise, float hold, float fall) {
        if (!(since >= 0)) {
            return 0;
        }
        if (since < rise) {
            return smooth(since / Math.max(rise, 1e-4F));
        }
        since -= rise;
        if (since < hold) {
            return 1;
        }
        since -= hold;
        if (since < fall) {
            return 1 - smooth(since / Math.max(fall, 1e-4F));
        }
        return 0;
    }

    /** 0~1 均匀随机；只在渲染线程用 */
    public static float rand() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (float) ((seed >>> 40) / (double) (1L << 24));
    }

    public static float rand(float a, float b) {
        return a + rand() * (b - a);
    }

    /** 单位球面上的随机方向，写进 out[0..2] */
    public static void randDir(float[] out) {
        float z = rand(-1, 1), t = rand(0, TAU), r = (float) Math.sqrt(1 - z * z);
        out[0] = r * (float) Math.cos(t);
        out[1] = z;
        out[2] = r * (float) Math.sin(t);
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = Math.round(((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = Math.round((a & 255) + (((b & 255) - (a & 255)) * t));
        return (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(bl);
    }

    public static int mul(int c, float k) {
        return (clampByte(Math.round(((c >> 16) & 255) * k)) << 16) | (clampByte(Math.round(((c >> 8) & 255) * k)) << 8)
                | clampByte(Math.round((c & 255) * k));
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
