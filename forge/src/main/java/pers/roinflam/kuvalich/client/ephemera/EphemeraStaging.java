package pers.roinflam.kuvalich.client.ephemera;

import pers.roinflam.kuvalich.ephemera.EphemeraItem;

import java.util.Arrays;

/**
 * 一帧里所有幻纹的四边形。每个幻纹先画进暂存区，{@link #commit} 按预算裁一遍再并进两批（半透明、加法），
 * 一帧最后两批各提交一次。坐标已经减过相机位置，颜色是带透明度的 ARGB。
 *
 * <p>超预算时先砍粒子、再砍拖尾，底座（环、池、羽片这类主体）不砍，整款不会突然消失。
 * 两类都从后往前砍：粒子后发的先砍，拖尾按约定从头往尾画，砍掉的是最旧的那一截。</p>
 */
public final class EphemeraStaging {

    public static final int BASE = 0;
    public static final int TRAIL = 1;
    public static final int PARTICLE = 2;

    public static final int TRANSLUCENT = 0;
    public static final int ADDITIVE = 1;

    public static final class Layer {
        float[] pos = new float[256 * 12];
        int[] col = new int[256 * 4];
        float[] uv = new float[256 * 8];
        int quads;

        public int quads() {
            return quads;
        }

        public float[] positions() {
            return pos;
        }

        public int[] colors() {
            return col;
        }

        public float[] uvs() {
            return uv;
        }

        void ensure(int more) {
            int need = quads + more;
            if (need * 4 > col.length) {
                int cap = Math.max(need, col.length / 2);
                pos = Arrays.copyOf(pos, cap * 12);
                col = Arrays.copyOf(col, cap * 4);
                uv = Arrays.copyOf(uv, cap * 8);
            }
        }
    }

    private float[] pos = new float[256 * 12];
    private int[] col = new int[256 * 4];
    private float[] uv = new float[256 * 8];
    private byte[] cat = new byte[256];
    private byte[] layer = new byte[256];
    private int n;
    private int lastDropped;

    private final Layer[] layers = {new Layer(), new Layer()};

    /** 离相机这么近（格）以内的顶点按距离淡出；0 = 不淡（第一人称看自己时打开） */
    public float nearFade;
    /** 整体乘在透明度上（潜行压暗、远处淡出） */
    public float alphaMul = 1.0F;
    /** 关掉加法混合时，加法那批改进半透明批，透明度乘这个 */
    public boolean additiveAllowed = true;

    public void clearFrame() {
        n = 0;
        layers[0].quads = 0;
        layers[1].quads = 0;
    }

    public int pending() {
        return n;
    }

    public int pending(int category) {
        int c = 0;
        for (int i = 0; i < n; i++) {
            if (cat[i] == category) {
                c++;
            }
        }
        return c;
    }

    public Layer layer(int which) {
        return layers[which];
    }

    /** 上一次 commit 砍掉了几个 */
    public int lastDropped() {
        return lastDropped;
    }

    /** 不带贴图的面片：四个顶点都采白格中心 */
    public void quad(int category, int toLayer,
                     float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3,
                     int c0, int c1, int c2, int c3) {
        float u = (EphemeraSprites.u0(EphemeraSprites.WHITE) + EphemeraSprites.u1(EphemeraSprites.WHITE)) * 0.5F;
        float v = (EphemeraSprites.v0(EphemeraSprites.WHITE) + EphemeraSprites.v1(EphemeraSprites.WHITE)) * 0.5F;
        quad(category, toLayer, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, c0, c1, c2, c3,
                u, v, u, v, u, v, u, v);
    }

    public void quad(int category, int toLayer,
                     float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3,
                     int c0, int c1, int c2, int c3,
                     float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        if (n == cat.length) {
            int cap = n * 2;
            pos = Arrays.copyOf(pos, cap * 12);
            col = Arrays.copyOf(col, cap * 4);
            uv = Arrays.copyOf(uv, cap * 8);
            cat = Arrays.copyOf(cat, cap);
            layer = Arrays.copyOf(layer, cap);
        }
        int q = n * 8;
        uv[q] = u0;
        uv[q + 1] = v0;
        uv[q + 2] = u1;
        uv[q + 3] = v1;
        uv[q + 4] = u2;
        uv[q + 5] = v2;
        uv[q + 6] = u3;
        uv[q + 7] = v3;
        int p = n * 12;
        pos[p] = x0;
        pos[p + 1] = y0;
        pos[p + 2] = z0;
        pos[p + 3] = x1;
        pos[p + 4] = y1;
        pos[p + 5] = z1;
        pos[p + 6] = x2;
        pos[p + 7] = y2;
        pos[p + 8] = z2;
        pos[p + 9] = x3;
        pos[p + 10] = y3;
        pos[p + 11] = z3;
        int c = n * 4;
        col[c] = shade(c0, x0, y0, z0);
        col[c + 1] = shade(c1, x1, y1, z1);
        col[c + 2] = shade(c2, x2, y2, z2);
        col[c + 3] = shade(c3, x3, y3, z3);
        cat[n] = (byte) category;
        layer[n] = (byte) toLayer;
        n++;
    }

    private int shade(int argb, float x, float y, float z) {
        float a = ((argb >>> 24) / 255.0F) * alphaMul;
        if (nearFade > 0.0F) {
            float d = (float) Math.sqrt(x * x + y * y + z * z);
            if (d < nearFade) {
                a *= d / nearFade;
            }
        }
        int ai = Math.max(0, Math.min(255, Math.round(a * 255.0F)));
        return (ai << 24) | (argb & 0xFFFFFF);
    }

    /**
     * 把暂存区里这一个幻纹的四边形按上限 cap 裁掉多出来的，并进两批，清空暂存区。返回留下了几个
     */
    public int commit(int cap) {
        int particles = 0;
        int trails = 0;
        for (int i = 0; i < n; i++) {
            if (cat[i] == PARTICLE) {
                particles++;
            } else if (cat[i] == TRAIL) {
                trails++;
            }
        }
        int over = Math.max(0, n - Math.max(0, cap));
        int dropParticles = Math.min(over, particles);
        int dropTrails = Math.min(over - dropParticles, trails);
        // 从后往前砍：只留前 keepP 个粒子、前 keepT 段拖尾
        int keepP = particles - dropParticles;
        int keepT = trails - dropTrails;
        int seenP = 0;
        int seenT = 0;
        int kept = 0;
        for (int i = 0; i < n; i++) {
            if (cat[i] == PARTICLE && seenP++ >= keepP) {
                continue;
            }
            if (cat[i] == TRAIL && seenT++ >= keepT) {
                continue;
            }
            int to = layer[i] == ADDITIVE && additiveAllowed ? ADDITIVE : TRANSLUCENT;
            Layer l = layers[to];
            l.ensure(1);
            System.arraycopy(pos, i * 12, l.pos, l.quads * 12, 12);
            System.arraycopy(uv, i * 8, l.uv, l.quads * 8, 8);
            if (layer[i] == ADDITIVE && !additiveAllowed) {
                for (int k = 0; k < 4; k++) {
                    int c = col[i * 4 + k];
                    l.col[l.quads * 4 + k] = (Math.round((c >>> 24) * 0.6F) << 24) | (c & 0xFFFFFF);
                }
            } else {
                System.arraycopy(col, i * 4, l.col, l.quads * 4, 4);
            }
            l.quads++;
            kept++;
        }
        lastDropped = n - kept;
        n = 0;
        return kept;
    }

    /**
     * 物品上开了渐变时，把暂存区里这一个幻纹的顶点色按位置换成主色到副色之间的颜色（画法里只用主色和它的深浅）。
     * 按这一帧所有顶点的高度（或离宿主的水平距离）归一化，跨度有下限，小件不至于一点点就从头变到尾。
     * 灰、白这类不带色相的顶点不动，底托的暗色按亮度少换一点。hx、hz 是宿主相对相机的水平坐标。
     */
    public void gradePending(int mode, int primary, int secondary, float hx, float hz, float timeSec) {
        if (n == 0 || mode == 0 || primary == secondary) {
            return;
        }
        boolean radial = mode == EphemeraItem.GRADIENT_RADIAL;
        boolean flow = mode == EphemeraItem.GRADIENT_FLOW;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (int v = 0, verts = n * 4; v < verts; v++) {
            float m = metric(v, radial, hx, hz);
            lo = Math.min(lo, m);
            hi = Math.max(hi, m);
        }
        float span = Math.max(hi - lo, radial ? 0.6F : 0.8F);
        float pr = ((primary >> 16) & 255) / 255.0F, pg = ((primary >> 8) & 255) / 255.0F, pb = (primary & 255) / 255.0F;
        float sr = ((secondary >> 16) & 255) / 255.0F, sg = ((secondary >> 8) & 255) / 255.0F, sb = (secondary & 255) / 255.0F;
        float lumP = Math.max(lum(pr, pg, pb), 0.05F);
        for (int v = 0, verts = n * 4; v < verts; v++) {
            int c = col[v];
            float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
            float chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
            float w = Math.max(0.0F, Math.min(1.0F, (chroma - 0.02F) / 0.08F));
            if (w <= 0.0F) {
                continue;
            }
            float u = Math.max(0.0F, Math.min(1.0F, (metric(v, radial, hx, hz) - lo) / span));
            float t = flow ? 0.5F + 0.5F * (float) Math.sin(Math.PI * 2 * (u - timeSec * 0.45F)) : u;
            float s = Math.max(0.0F, Math.min(1.4F, lum(r, g, b) / lumP)) * w;
            float or = Math.max(0.0F, r + (sr - pr) * t * s);
            float og = Math.max(0.0F, g + (sg - pg) * t * s);
            float ob = Math.max(0.0F, b + (sb - pb) * t * s);
            float mx = Math.max(or, Math.max(og, ob));
            if (mx > 1.0F) {
                or /= mx;
                og /= mx;
                ob /= mx;
            }
            col[v] = (c & 0xFF000000) | (Math.round(or * 255.0F) << 16) | (Math.round(og * 255.0F) << 8) | Math.round(ob * 255.0F);
        }
    }

    private float metric(int vertex, boolean radial, float hx, float hz) {
        int p = vertex * 3;
        if (radial) {
            float dx = pos[p] - hx, dz = pos[p + 2] - hz;
            return (float) Math.sqrt(dx * dx + dz * dz);
        }
        return pos[p + 1];
    }

    private static float lum(float r, float g, float b) {
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    /** 不提交，直接扔掉暂存区（宿主中途不画了） */
    public void discardPending() {
        n = 0;
    }
}
