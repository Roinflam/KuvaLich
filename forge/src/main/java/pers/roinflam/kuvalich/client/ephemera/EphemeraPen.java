package pers.roinflam.kuvalich.client.ephemera;

/**
 * 正式画法用的画笔：贴图编号、混合方式、颜色、透明度先设到「笔刷」上，再调一个图元（面片、朝镜头片、平躺片、光束、丝带），
 * 画完笔刷回到默认（加法、白格、主色、不透明），类别 {@link #cat} 不回。坐标一律世界坐标。
 * 加法层的透明度统一乘 {@link #glow}（强度档按环境亮度给的那一截），半透明底托不乘。
 */
public final class EphemeraPen {

    public static final int FACE_CAM = 0;
    public static final int FACE_FLAT = 1;
    public static final int FACE_NORMAL = 2;

    private EphemeraDraw d;
    private EphemeraStaging out;
    private double cx, cy, cz;
    public float rightX, rightY, rightZ, upX, upY, upZ;
    /** 镜头朝前（= 上 × 右），原型的 d.cam.fwd */
    public float fwdX, fwdY, fwdZ;

    public int cat = EphemeraStaging.BASE;
    public float glow = 1.0F;
    /** 笔刷没设颜色时用的颜色（这一款的主色） */
    public int base;

    private int sprite;
    private boolean add;
    private int rgb;
    private float a;
    private float su0, sv0, su1, sv1;
    private boolean hasVa;
    private final float[] va = new float[4];
    private boolean hasVrgb;
    private final int[] vrgb = new int[4];
    private boolean hasUv4;
    private final float[] uv4 = new float[8];

    private final double[] p = new double[12];

    public void begin(EphemeraDraw draw, int baseRgb, float glowAlpha) {
        d = draw;
        out = draw.out;
        cx = draw.camX;
        cy = draw.camY;
        cz = draw.camZ;
        rightX = draw.rightX;
        rightY = draw.rightY;
        rightZ = draw.rightZ;
        upX = draw.upX;
        upY = draw.upY;
        upZ = draw.upZ;
        fwdX = upY * rightZ - upZ * rightY;
        fwdY = upZ * rightX - upX * rightZ;
        fwdZ = upX * rightY - upY * rightX;
        base = baseRgb;
        glow = glowAlpha;
        cat = EphemeraStaging.BASE;
        reset();
    }

    public double camX() {
        return cx;
    }

    public double camY() {
        return cy;
    }

    public double camZ() {
        return cz;
    }

    /** 笔刷现在的设置（拆成多段画的工具要一段段重设笔刷时读） */
    public int sprite() {
        return sprite;
    }

    public int rgbNow() {
        return rgb;
    }

    public float alpha() {
        return a;
    }

    public boolean additive() {
        return add;
    }

    private void reset() {
        sprite = EphemeraSprites.WHITE;
        add = true;
        rgb = base;
        a = 1.0F;
        su0 = 0;
        sv0 = 0;
        su1 = 1;
        sv1 = 1;
        hasVa = false;
        hasVrgb = false;
        hasUv4 = false;
    }

    // ---------- 笔刷 ----------

    public EphemeraPen s(int spriteId) {
        sprite = spriteId;
        return this;
    }

    /** 半透明混合（底托） */
    public EphemeraPen alphaBlend() {
        add = false;
        return this;
    }

    public EphemeraPen rgb(int c) {
        rgb = c;
        return this;
    }

    public EphemeraPen a(float alpha) {
        a = alpha;
        return this;
    }

    /** 贴图里取哪一块，0~1 */
    public EphemeraPen uv(float u0, float v0, float u1, float v1) {
        su0 = u0;
        sv0 = v0;
        su1 = u1;
        sv1 = v1;
        return this;
    }

    public EphemeraPen uv4(float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3) {
        hasUv4 = true;
        uv4[0] = u0;
        uv4[1] = v0;
        uv4[2] = u1;
        uv4[3] = v1;
        uv4[4] = u2;
        uv4[5] = v2;
        uv4[6] = u3;
        uv4[7] = v3;
        return this;
    }

    /** 四个顶点各自的透明度系数 */
    public EphemeraPen va(float a0, float a1, float a2, float a3) {
        hasVa = true;
        va[0] = a0;
        va[1] = a1;
        va[2] = a2;
        va[3] = a3;
        return this;
    }

    public EphemeraPen vrgb(int c0, int c1, int c2, int c3) {
        hasVrgb = true;
        vrgb[0] = c0;
        vrgb[1] = c1;
        vrgb[2] = c2;
        vrgb[3] = c3;
        return this;
    }

    // ---------- 图元 ----------

    public void quad(double x0, double y0, double z0, double x1, double y1, double z1,
                     double x2, double y2, double z2, double x3, double y3, double z3) {
        p[0] = x0;
        p[1] = y0;
        p[2] = z0;
        p[3] = x1;
        p[4] = y1;
        p[5] = z1;
        p[6] = x2;
        p[7] = y2;
        p[8] = z2;
        p[9] = x3;
        p[10] = y3;
        p[11] = z3;
        emit();
        reset();
    }

    /** 朝镜头的片，w × h，rot 绕视线转 */
    public void billboard(double x, double y, double z, float w, float h, float rot) {
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float ax = (rightX * cs + upX * sn) * w / 2, ay = (rightY * cs + upY * sn) * w / 2, az = (rightZ * cs + upZ * sn) * w / 2;
        float bx = (-rightX * sn + upX * cs) * h / 2, by = (-rightY * sn + upY * cs) * h / 2, bz = (-rightZ * sn + upZ * cs) * h / 2;
        quad(x - ax + bx, y - ay + by, z - az + bz, x + ax + bx, y + ay + by, z + az + bz,
                x + ax - bx, y + ay - by, z + az - bz, x - ax - bx, y - ay - by, z - az - bz);
    }

    public void billboard(double x, double y, double z, float w, float h) {
        billboard(x, y, z, w, h, 0.0F);
    }

    /** 平躺在水平面上的片，rot 绕竖轴 */
    public void flat(double x, double y, double z, float w, float h, float rot) {
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float ax = cs * w / 2, az = sn * w / 2;
        float bx = -sn * h / 2, bz = cs * h / 2;
        quad(x - ax - bx, y, z - az - bz, x + ax - bx, y, z + az - bz, x + ax + bx, y, z + az + bz, x - ax + bx, y, z - az + bz);
    }

    public void flat(double x, double y, double z, float w, float h) {
        flat(x, y, z, w, h, 0.0F);
    }

    /** a 到 b 的光束，侧向朝着镜头展开 w 宽；贴图 u 沿 a → b */
    public void beam(double ax, double ay, double az, double bx, double by, double bz, float w) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) * 0.5 - cx, my = (ay + by) * 0.5 - cy, mz = (az + bz) * 0.5 - cz;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1.0E-6) {
            sx = rightX;
            sy = rightY;
            sz = rightZ;
            l = 1;
        }
        double k = w / 2 / l;
        sx *= k;
        sy *= k;
        sz *= k;
        quad(ax + sx, ay + sy, az + sz, bx + sx, by + sy, bz + sz, bx - sx, by - sy, bz - sz, ax - sx, ay - sy, az - sz);
    }

    /**
     * 丝带：相邻两点之间一个面片，贴图 u 沿长度按弧长从 u0 到 u1，v 横跨宽度。
     * face 为 {@link #FACE_NORMAL} 时丝带躺在和 (nx, ny, nz) 垂直的面里。笔刷对每一段都有效，画完才回默认。
     */
    public void ribbon(Ribbon r, int face, float nx, float ny, float nz, float u0, float u1) {
        int n = r.n;
        if (n < 2) {
            reset();
            return;
        }
        r.prepare();
        double total = r.acc[n - 1] > 0 ? r.acc[n - 1] : 1;
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            double tx = r.x[i1] - r.x[i0], ty = r.y[i1] - r.y[i0], tz = r.z[i1] - r.z[i0];
            double qx, qy, qz;
            if (face == FACE_CAM) {
                qx = cx - r.x[i];
                qy = cy - r.y[i];
                qz = cz - r.z[i];
            } else if (face == FACE_FLAT) {
                qx = 0;
                qy = 1;
                qz = 0;
            } else {
                qx = nx;
                qy = ny;
                qz = nz;
            }
            double sx = ty * qz - tz * qy, sy = tz * qx - tx * qz, sz = tx * qy - ty * qx;
            double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (l < 1.0E-6) {
                sx = rightX;
                sy = rightY;
                sz = rightZ;
                l = 1;
            }
            double k = r.w[i] / 2 / l;
            r.lx[i] = r.x[i] + sx * k;
            r.ly[i] = r.y[i] + sy * k;
            r.lz[i] = r.z[i] + sz * k;
            r.rx[i] = r.x[i] - sx * k;
            r.ry[i] = r.y[i] - sy * k;
            r.rz[i] = r.z[i] - sz * k;
            r.u[i] = (float) (u0 + (u1 - u0) * r.acc[i] / total);
        }
        boolean keepVrgb = hasVrgb;
        int c = rgb;
        for (int i = 0; i < n - 1; i++) {
            float a0 = r.a[i], a1 = r.a[i + 1];
            if (a0 <= 0.002F && a1 <= 0.002F) {
                continue;
            }
            if (r.w[i] <= 0 && r.w[i + 1] <= 0) {
                continue;
            }
            p[0] = r.lx[i];
            p[1] = r.ly[i];
            p[2] = r.lz[i];
            p[3] = r.lx[i + 1];
            p[4] = r.ly[i + 1];
            p[5] = r.lz[i + 1];
            p[6] = r.rx[i + 1];
            p[7] = r.ry[i + 1];
            p[8] = r.rz[i + 1];
            p[9] = r.rx[i];
            p[10] = r.ry[i];
            p[11] = r.rz[i];
            hasVa = true;
            va[0] = a0;
            va[1] = a1;
            va[2] = a1;
            va[3] = a0;
            if (r.hasRgb) {
                int c0 = r.rgb[i], c1 = r.rgb[i + 1];
                hasVrgb = true;
                vrgb[0] = c0;
                vrgb[1] = c1;
                vrgb[2] = c1;
                vrgb[3] = c0;
            } else {
                hasVrgb = keepVrgb;
                rgb = c;
            }
            hasUv4 = true;
            uv4[0] = r.u[i];
            uv4[1] = 0;
            uv4[2] = r.u[i + 1];
            uv4[3] = 0;
            uv4[4] = r.u[i + 1];
            uv4[5] = 1;
            uv4[6] = r.u[i];
            uv4[7] = 1;
            emit();
        }
        reset();
    }

    public void ribbon(Ribbon r, int face, float u0, float u1) {
        ribbon(r, face, 0, 0, 0, u0, u1);
    }

    private void emit() {
        float alpha = a * (add ? glow : 1.0F);
        if (!(alpha > 0.001F)) {
            return;
        }
        if (hasVa && !(Math.max(Math.max(va[0], va[1]), Math.max(va[2], va[3])) * alpha > 0.001F)) {
            return;
        }
        float bu0 = EphemeraSprites.u0(sprite), bv0 = EphemeraSprites.v0(sprite);
        float du = EphemeraSprites.u1(sprite) - bu0, dv = EphemeraSprites.v1(sprite) - bv0;
        float t0u, t0v, t1u, t1v, t2u, t2v, t3u, t3v;
        if (hasUv4) {
            t0u = uv4[0];
            t0v = uv4[1];
            t1u = uv4[2];
            t1v = uv4[3];
            t2u = uv4[4];
            t2v = uv4[5];
            t3u = uv4[6];
            t3v = uv4[7];
        } else {
            t0u = su0;
            t0v = sv0;
            t1u = su1;
            t1v = sv0;
            t2u = su1;
            t2v = sv1;
            t3u = su0;
            t3v = sv1;
        }
        if (EphemeraSprites.periodic(sprite)) {
            // 半张一个周期：整段平移 0.5 的整数倍，滚出格子的部分接回来
            float lo = Math.min(Math.min(t0u, t1u), Math.min(t2u, t3u));
            float shift = (float) Math.floor(lo / 0.5F) * 0.5F;
            t0u = Math.min(1, t0u - shift);
            t1u = Math.min(1, t1u - shift);
            t2u = Math.min(1, t2u - shift);
            t3u = Math.min(1, t3u - shift);
        }
        int c0 = hasVrgb ? vrgb[0] : rgb, c1 = hasVrgb ? vrgb[1] : rgb, c2 = hasVrgb ? vrgb[2] : rgb, c3 = hasVrgb ? vrgb[3] : rgb;
        float a0 = hasVa ? alpha * va[0] : alpha, a1 = hasVa ? alpha * va[1] : alpha;
        float a2 = hasVa ? alpha * va[2] : alpha, a3 = hasVa ? alpha * va[3] : alpha;
        out.quad(cat, add ? EphemeraStaging.ADDITIVE : EphemeraStaging.TRANSLUCENT,
                (float) (p[0] - cx), (float) (p[1] - cy), (float) (p[2] - cz),
                (float) (p[3] - cx), (float) (p[4] - cy), (float) (p[5] - cz),
                (float) (p[6] - cx), (float) (p[7] - cy), (float) (p[8] - cz),
                (float) (p[9] - cx), (float) (p[10] - cy), (float) (p[11] - cz),
                EphemeraDraw.argb(c0, a0), EphemeraDraw.argb(c1, a1), EphemeraDraw.argb(c2, a2), EphemeraDraw.argb(c3, a3),
                bu0 + du * clamp01(t0u), bv0 + dv * clamp01(t0v), bu0 + du * clamp01(t1u), bv0 + dv * clamp01(t1v),
                bu0 + du * clamp01(t2u), bv0 + dv * clamp01(t2v), bu0 + du * clamp01(t3u), bv0 + dv * clamp01(t3v));
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    /** 丝带的点：位置、宽、透明度，可选逐点颜色。复用，每帧 clear 再 add */
    public static final class Ribbon {
        public double[] x, y, z;
        public float[] w, a;
        public int[] rgb;
        public boolean hasRgb;
        public int n;
        double[] acc, lx, ly, lz, rx, ry, rz;
        float[] u;

        public Ribbon(int cap) {
            alloc(cap);
        }

        private void alloc(int cap) {
            x = new double[cap];
            y = new double[cap];
            z = new double[cap];
            w = new float[cap];
            a = new float[cap];
            rgb = new int[cap];
            acc = new double[cap];
            lx = new double[cap];
            ly = new double[cap];
            lz = new double[cap];
            rx = new double[cap];
            ry = new double[cap];
            rz = new double[cap];
            u = new float[cap];
        }

        public Ribbon clear() {
            n = 0;
            hasRgb = false;
            return this;
        }

        public void add(double px, double py, double pz, float width, float alpha) {
            if (n == x.length) {
                grow();
            }
            x[n] = px;
            y[n] = py;
            z[n] = pz;
            w[n] = width;
            a[n] = alpha;
            rgb[n] = 0;
            n++;
        }

        public void add(double px, double py, double pz, float width, float alpha, int color) {
            add(px, py, pz, width, alpha);
            rgb[n - 1] = color;
            hasRgb = true;
        }

        /** 同一串点换一组宽度 / 透明度系数再画一层 */
        public Ribbon scaled(Ribbon src, float wk, float ak) {
            clear();
            for (int i = 0; i < src.n; i++) {
                add(src.x[i], src.y[i], src.z[i], src.w[i] * wk, src.a[i] * ak);
            }
            return this;
        }

        private void grow() {
            int cap = x.length * 2;
            Ribbon t = new Ribbon(cap);
            System.arraycopy(x, 0, t.x, 0, n);
            System.arraycopy(y, 0, t.y, 0, n);
            System.arraycopy(z, 0, t.z, 0, n);
            System.arraycopy(w, 0, t.w, 0, n);
            System.arraycopy(a, 0, t.a, 0, n);
            System.arraycopy(rgb, 0, t.rgb, 0, n);
            x = t.x;
            y = t.y;
            z = t.z;
            w = t.w;
            a = t.a;
            rgb = t.rgb;
            acc = t.acc;
            lx = t.lx;
            ly = t.ly;
            lz = t.lz;
            rx = t.rx;
            ry = t.ry;
            rz = t.rz;
            u = t.u;
        }

        void prepare() {
            acc[0] = 0;
            for (int i = 1; i < n; i++) {
                double dx = x[i] - x[i - 1], dy = y[i] - y[i - 1], dz = z[i] - z[i - 1];
                acc[i] = acc[i - 1] + Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
        }
    }
}
