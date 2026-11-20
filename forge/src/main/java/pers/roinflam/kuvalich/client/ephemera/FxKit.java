package pers.roinflam.kuvalich.client.ephemera;

/**
 * 新各款共用的小件，照原型 kit.js：一圈点、三层丝带、平面圆环、平面贴图、柔光点、跟随弹簧。
 * 平面都用两根轴 (ax, ay) 张开，两根轴自己保证单位长、互相垂直。
 */
public final class FxKit {

    private FxKit() {
    }

    /** 一圈（或一段弧）点追加进 r：圆心 c、两根轴、半径 rad，n 段共 n+1 个点，宽 w、透明度 a */
    public static void circle(EphemeraPen.Ribbon r, double cx, double cy, double cz,
                              double axX, double axY, double axZ, double ayX, double ayY, double ayZ,
                              double rad, int n, float a0, float a1, float w, float a) {
        for (int i = 0; i <= n; i++) {
            float ang = a0 + (a1 - a0) * i / n;
            double c = Math.cos(ang) * rad, s = Math.sin(ang) * rad;
            r.add(cx + axX * c + ayX * s, cy + axY * c + ayY * s, cz + axZ * c + ayZ * s, w, a);
        }
    }

    /**
     * 「暗托 + 主色 + 亮芯」三层丝带。src 里的宽、透明度是主色那层的，暗托宽 1.35 倍、亮芯宽 0.25 倍。
     * sprite 是主色层的贴图（原型默认 soft），face / 法线同 {@link EphemeraPen#ribbon}；tmp 是借来拼另两层的丝带。
     */
    public static void band3(EphemeraPen pen, EphemeraPen.Ribbon src, EphemeraPen.Ribbon tmp, int C, float a,
                             int sprite, boolean dark, int face, float nx, float ny, float nz, float u0, float u1) {
        int hot = EphemeraFx.mix(C, EphemeraFx.WHITE, 0.62F);
        if (dark) {
            tmp.clear();
            for (int i = 0; i < src.n; i++) {
                tmp.add(src.x[i], src.y[i], src.z[i], src.w[i] * 1.35F, 1);
            }
            pen.s(EphemeraSprites.SOFT).alphaBlend().rgb(0).a(0.35F * a).ribbon(tmp, face, nx, ny, nz, 0, 1);
        }
        pen.s(sprite).rgb(C).a(a).ribbon(src, face, nx, ny, nz, u0, u1);
        tmp.scaled(src, 0.25F, 1);
        pen.rgb(hot).a(0.9F * a).ribbon(tmp, face, nx, ny, nz, 0, 1);
    }

    /**
     * 平面里的一圈（或一段弧）四边形，n 段；uRep 大于 0 时沿圈每 uRep 段平铺一遍贴图，否则每段都是整张。
     * 笔刷（贴图、颜色、透明度、混合）对每一段都有效。
     */
    public static void ring(EphemeraPen pen, double cx, double cy, double cz,
                            double axX, double axY, double axZ, double ayX, double ayY, double ayZ,
                            double r, double w, int n, float a0, float a1, int uRep) {
        int sprite = pen.sprite();
        int rgb = pen.rgbNow();
        float a = pen.alpha();
        boolean add = pen.additive();
        double ro = r + w / 2, ri = r - w / 2;
        for (int i = 0; i < n; i++) {
            float b0 = a0 + (a1 - a0) * i / n, b1 = a0 + (a1 - a0) * (i + 1) / n;
            double c0 = Math.cos(b0), s0 = Math.sin(b0), c1 = Math.cos(b1), s1 = Math.sin(b1);
            float k = uRep > 0 ? (float) (i % uRep) / uRep : 0, k1 = uRep > 0 ? k + 1.0F / uRep : 1;
            pen.s(sprite).rgb(rgb).a(a);
            if (!add) {
                pen.alphaBlend();
            }
            pen.uv4(k, 0, k1, 0, k1, 1, k, 1);
            pen.quad(cx + (axX * c0 + ayX * s0) * ro, cy + (axY * c0 + ayY * s0) * ro, cz + (axZ * c0 + ayZ * s0) * ro,
                    cx + (axX * c1 + ayX * s1) * ro, cy + (axY * c1 + ayY * s1) * ro, cz + (axZ * c1 + ayZ * s1) * ro,
                    cx + (axX * c1 + ayX * s1) * ri, cy + (axY * c1 + ayY * s1) * ri, cz + (axZ * c1 + ayZ * s1) * ri,
                    cx + (axX * c0 + ayX * s0) * ri, cy + (axY * c0 + ayY * s0) * ri, cz + (axZ * c0 + ayZ * s0) * ri);
        }
    }

    /** 平面上一张整贴图（圆环、刻度盘这类贴图直接贴进平面，一片就是一整圈），size 是边长，rot 在平面里转 */
    public static void plate(EphemeraPen pen, double cx, double cy, double cz,
                             double axX, double axY, double axZ, double ayX, double ayY, double ayZ,
                             double size, float rot) {
        double cs = Math.cos(rot), sn = Math.sin(rot), h = size / 2;
        double xX = (axX * cs + ayX * sn) * h, xY = (axY * cs + ayY * sn) * h, xZ = (axZ * cs + ayZ * sn) * h;
        double yX = (-axX * sn + ayX * cs) * h, yY = (-axY * sn + ayY * cs) * h, yZ = (-axZ * sn + ayZ * cs) * h;
        pen.quad(cx - xX - yX, cy - xY - yY, cz - xZ - yZ, cx + xX - yX, cy + xY - yY, cz + xZ - yZ,
                cx + xX + yX, cy + xY + yY, cz + xZ + yZ, cx - xX + yX, cy - xY + yY, cz - xZ + yZ);
    }

    /** 柔光点：外晕 + 亮芯，两片 */
    public static void glow(EphemeraPen pen, double x, double y, double z, float size, int C, int hot, float a) {
        pen.s(EphemeraSprites.SOFT).rgb(C).a(0.35F * a).billboard(x, y, z, size, size);
        float k = size * 0.35F;
        pen.s(EphemeraSprites.SOFTCORE).rgb(hot).a(0.9F * a).billboard(x, y, z, k, k);
    }

    /**
     * 带惯性追目标的点：移动时拖在后面、急停往前荡。阻尼按「相对宿主的速度」算（tx/ty/tz 传 m.trackX/Y/Z），
     * 不然跟着人匀速跑时也会一直落后一截。w0 是角频率（原型默认 14）、z 是阻尼比（默认 0.6）；
     * 半隐式积分在 dt = 0.04 时要 4 − 2·(2·z·w0)·dt − w0²·dt² > 0，w0 别超过约 30。
     */
    public static final class Spring {
        public double x, y, z;
        public float vx, vy, vz;
        private boolean init;

        public void reset() {
            init = false;
        }

        public void step(double px, double py, double pz, float dt, float w0, float zeta, float tx, float ty, float tz) {
            if (!init) {
                init = true;
                x = px;
                y = py;
                z = pz;
                vx = tx;
                vy = ty;
                vz = tz;
                return;
            }
            float k = w0 * w0, c = 2 * zeta * w0;
            vx += (float) (((px - x) * k - (vx - tx) * c) * dt);
            vy += (float) (((py - y) * k - (vy - ty) * c) * dt);
            vz += (float) (((pz - z) * k - (vz - tz) * c) * dt);
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
        }

        public void step(double px, double py, double pz, FxMotion m, float w0, float zeta) {
            step(px, py, pz, m.dt, w0, zeta, m.trackX, m.trackY, m.trackZ);
        }
    }
}
