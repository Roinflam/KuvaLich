package pers.roinflam.kuvalich.client.ephemera;

import net.minecraft.util.Mth;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;

import java.util.Arrays;

/**
 * 一帧里所有幻纹的四边形，分两批（半透明、加法），一帧最后两批各提交一次。
 * 每个幻纹直接往批的尾部画，画完 {@link #commit} 按预算把多出来的原地挤掉。坐标已经减过相机位置，颜色是带透明度的 ARGB。
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

    private static final float DEMOTED_ALPHA = 0.6F;

    public static final class Layer {
        float[] pos = new float[256 * 12];
        int[] col = new int[256 * 4];
        float[] uv = new float[256 * 8];
        byte[] cat = new byte[256];
        /** 这个面片是当前幻纹同类（粒子 / 拖尾）里第几个画的，砍预算时按它留前几个 */
        int[] ord = new int[256];
        int quads;
        /** 当前这个幻纹从第几个面片开始，前面的都已经提交过 */
        int start;

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

        void grow() {
            int cap = cat.length * 2;
            pos = Arrays.copyOf(pos, cap * 12);
            col = Arrays.copyOf(col, cap * 4);
            uv = Arrays.copyOf(uv, cap * 8);
            cat = Arrays.copyOf(cat, cap);
            ord = Arrays.copyOf(ord, cap);
        }
    }

    private final Layer[] layers = {new Layer(), new Layer()};
    private int particles;
    private int trails;
    private int lastDropped;

    /** 离相机这么近（格）以内的顶点按距离淡出；0 = 不淡（第一人称看自己时打开） */
    public float nearFade;
    /** 整体乘在透明度上（潜行压暗、远处淡出） */
    public float alphaMul = 1.0F;
    /** 关掉加法混合时，加法的面片改进半透明批、透明度打折。面片写进来时就分批，所以要在画之前定好 */
    public boolean additiveAllowed = true;

    public void clearFrame() {
        for (Layer l : layers) {
            l.quads = 0;
            l.start = 0;
        }
        particles = 0;
        trails = 0;
    }

    public int pending() {
        return layers[0].quads - layers[0].start + layers[1].quads - layers[1].start;
    }

    public int pending(int category) {
        int c = 0;
        for (Layer l : layers) {
            for (int i = l.start; i < l.quads; i++) {
                if (l.cat[i] == category) {
                    c++;
                }
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
        boolean demoted = toLayer == ADDITIVE && !additiveAllowed;
        Layer l = layers[demoted ? TRANSLUCENT : toLayer];
        int i = l.quads;
        if (i == l.cat.length) {
            l.grow();
        }
        float[] uv = l.uv;
        int q = i * 8;
        uv[q] = u0;
        uv[q + 1] = v0;
        uv[q + 2] = u1;
        uv[q + 3] = v1;
        uv[q + 4] = u2;
        uv[q + 5] = v2;
        uv[q + 6] = u3;
        uv[q + 7] = v3;
        float[] pos = l.pos;
        int p = i * 12;
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
        int[] col = l.col;
        int c = i * 4;
        col[c] = shade(c0, x0, y0, z0);
        col[c + 1] = shade(c1, x1, y1, z1);
        col[c + 2] = shade(c2, x2, y2, z2);
        col[c + 3] = shade(c3, x3, y3, z3);
        if (demoted) {
            for (int k = c; k < c + 4; k++) {
                col[k] = (Math.round((col[k] >>> 24) * DEMOTED_ALPHA) << 24) | (col[k] & 0xFFFFFF);
            }
        }
        l.cat[i] = (byte) category;
        l.ord[i] = category == PARTICLE ? particles++ : category == TRAIL ? trails++ : 0;
        l.quads = i + 1;
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
     * 当前这个幻纹画完了：按上限 cap 把多出来的面片挤掉，剩下的算进这一帧。返回留下了几个
     */
    public int commit(int cap) {
        int n = pending();
        int over = Math.max(0, n - Math.max(0, cap));
        int dropParticles = Math.min(over, particles);
        int dropTrails = Math.min(over - dropParticles, trails);
        int keepP = particles - dropParticles;
        int keepT = trails - dropTrails;
        int kept = 0;
        for (Layer l : layers) {
            if (dropParticles > 0 || dropTrails > 0) {
                int w = l.start;
                for (int i = l.start; i < l.quads; i++) {
                    byte c = l.cat[i];
                    if ((c == PARTICLE && l.ord[i] >= keepP) || (c == TRAIL && l.ord[i] >= keepT)) {
                        continue;
                    }
                    if (w != i) {
                        System.arraycopy(l.pos, i * 12, l.pos, w * 12, 12);
                        System.arraycopy(l.col, i * 4, l.col, w * 4, 4);
                        System.arraycopy(l.uv, i * 8, l.uv, w * 8, 8);
                    }
                    w++;
                }
                l.quads = w;
            }
            kept += l.quads - l.start;
            l.start = l.quads;
        }
        lastDropped = n - kept;
        particles = 0;
        trails = 0;
        return kept;
    }

    /**
     * 物品上开了渐变时，把当前这个幻纹的顶点色按位置换成主色到副色之间的颜色（画法里只用主色和它的深浅）。
     * 按这一帧所有顶点的高度（或离宿主的水平距离）归一化，跨度有下限，小件不至于一点点就从头变到尾。
     * 灰、白这类不带色相的顶点不动，底托的暗色按亮度少换一点。hx、hz 是宿主相对相机的水平坐标。
     */
    public void gradePending(int mode, int primary, int secondary, float hx, float hz, float timeSec) {
        if (pending() == 0 || mode == 0 || primary == secondary) {
            return;
        }
        boolean radial = mode == EphemeraItem.GRADIENT_RADIAL;
        boolean flow = mode == EphemeraItem.GRADIENT_FLOW;
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (Layer l : layers) {
            for (int v = l.start * 4, verts = l.quads * 4; v < verts; v++) {
                float m = metric(l.pos, v, radial, hx, hz);
                lo = Math.min(lo, m);
                hi = Math.max(hi, m);
            }
        }
        float invSpan = 1.0F / Math.max(hi - lo, radial ? 0.6F : 0.8F);
        float pr = ((primary >> 16) & 255) / 255.0F, pg = ((primary >> 8) & 255) / 255.0F, pb = (primary & 255) / 255.0F;
        float dr = ((secondary >> 16) & 255) / 255.0F - pr, dg = ((secondary >> 8) & 255) / 255.0F - pg,
                db = (secondary & 255) / 255.0F - pb;
        float invLumP = 1.0F / Math.max(lum(pr, pg, pb), 0.05F);
        float phase = timeSec * 0.45F;
        for (Layer l : layers) {
            int[] col = l.col;
            for (int v = l.start * 4, verts = l.quads * 4; v < verts; v++) {
                int c = col[v];
                float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
                float chroma = Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                float w = Math.max(0.0F, Math.min(1.0F, (chroma - 0.02F) / 0.08F));
                if (w <= 0.0F) {
                    continue;
                }
                float u = Math.max(0.0F, Math.min(1.0F, (metric(l.pos, v, radial, hx, hz) - lo) * invSpan));
                float t = flow ? 0.5F + 0.5F * Mth.sin(Mth.TWO_PI * (u - phase)) : u;
                float s = Math.max(0.0F, Math.min(1.4F, lum(r, g, b) * invLumP)) * w * t;
                float or = Math.max(0.0F, r + dr * s);
                float og = Math.max(0.0F, g + dg * s);
                float ob = Math.max(0.0F, b + db * s);
                float mx = Math.max(or, Math.max(og, ob));
                if (mx > 1.0F) {
                    or /= mx;
                    og /= mx;
                    ob /= mx;
                }
                col[v] = (c & 0xFF000000) | (Math.round(or * 255.0F) << 16) | (Math.round(og * 255.0F) << 8) | Math.round(ob * 255.0F);
            }
        }
    }

    private static float metric(float[] pos, int vertex, boolean radial, float hx, float hz) {
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

    /** 当前这个幻纹不画了，已经写进来的面片扔掉 */
    public void discardPending() {
        for (Layer l : layers) {
            l.quads = l.start;
        }
        particles = 0;
        trails = 0;
    }
}
