package pers.roinflam.kuvalich.client.ephemera;

/**
 * 拖尾：记挂点的历史世界位置，取样时先 Catmull-Rom 细分（转弯处才圆），再按寿命和最大长度平滑截尾
 * （尾巴是缩回去的，不是一截截掉），最后按弧长均匀重采样。life 秒、maxLen 格；一步跳得比 jump 还远（瞬移）就清空。
 */
public final class FxTrail {

    private static final int CAP = 240;
    private static final int SUB = 4;

    public float life;
    public float maxLen;
    public float minDist;
    public float jump;

    private final double[] px = new double[CAP], py = new double[CAP], pz = new double[CAP], pt = new double[CAP];
    private int n;
    private double now;

    private double[] dx = new double[CAP * SUB + 1], dy = new double[CAP * SUB + 1], dz = new double[CAP * SUB + 1],
            dt = new double[CAP * SUB + 1];
    private final double[] acc = new double[CAP * SUB + 2];

    public FxTrail(float life, float maxLen, float minDist, float jump) {
        this.life = life;
        this.maxLen = maxLen;
        this.minDist = minDist;
        this.jump = jump;
    }

    public void clear() {
        n = 0;
    }

    public int points() {
        return n;
    }

    public double x(int i) {
        return px[i];
    }

    public double y(int i) {
        return py[i];
    }

    public double z(int i) {
        return pz[i];
    }

    public void push(double x, double y, double z, double t) {
        now = t;
        if (n > 0 && dist(x, y, z, px[0], py[0], pz[0]) > jump) {
            n = 0;
        }
        if (n >= 2 && dist(px[1], py[1], pz[1], x, y, z) < minDist) {
            px[0] = x;
            py[0] = y;
            pz[0] = z;
            pt[0] = t;
        } else {
            int m = Math.min(n, CAP - 1);
            System.arraycopy(px, 0, px, 1, m);
            System.arraycopy(py, 0, py, 1, m);
            System.arraycopy(pz, 0, pz, 1, m);
            System.arraycopy(pt, 0, pt, 1, m);
            px[0] = x;
            py[0] = y;
            pz[0] = z;
            pt[0] = t;
            n = m + 1;
        }
        double len = 0;
        for (int i = 1; i < n; i++) {
            len += dist(px[i - 1], py[i - 1], pz[i - 1], px[i], py[i], pz[i]);
            if (len > maxLen * 1.2 || t - pt[i] > life * 1.2) {
                n = i + 1;
                break;
            }
        }
    }

    public double length() {
        double len = 0;
        for (int i = 1; i < n; i++) {
            len += dist(px[i - 1], py[i - 1], pz[i - 1], px[i], py[i], pz[i]);
        }
        return len;
    }

    /** 按弧长均匀重采样成 segs 段（segs + 1 个点）写进 out；不够两点时 out.n = 0 */
    public Sample sample(int segs, Sample out) {
        out.n = 0;
        if (n < 2) {
            return out;
        }
        int m = 0;
        for (int i = 0; i < n - 1; i++) {
            int a0 = Math.max(i - 1, 0), a2 = i + 1, a3 = Math.min(i + 2, n - 1);
            double t1 = pt[i], t2 = pt[i + 1];
            for (int k = 0; k < SUB; k++) {
                double u = (double) k / SUB;
                dx[m] = cr(px[a0], px[i], px[a2], px[a3], u);
                dy[m] = cr(py[a0], py[i], py[a2], py[a3], u);
                dz[m] = cr(pz[a0], pz[i], pz[a2], pz[a3], u);
                dt[m] = t1 + (t2 - t1) * u;
                m++;
            }
        }
        dx[m] = px[n - 1];
        dy[m] = py[n - 1];
        dz[m] = pz[n - 1];
        dt[m] = pt[n - 1];
        m++;
        // 截尾：超寿命或超长的部分切掉，切点插值
        int cut = 1;
        double len = 0;
        for (int i = 1; i < m; i++) {
            double seg = dist(dx[i - 1], dy[i - 1], dz[i - 1], dx[i], dy[i], dz[i]);
            double ageB = now - dt[i], ageA = now - dt[i - 1];
            double f = 1;
            if (ageB > life) {
                f = Math.min(f, (life - ageA) / Math.max(ageB - ageA, 1e-6));
            }
            if (len + seg > maxLen) {
                f = Math.min(f, (maxLen - len) / Math.max(seg, 1e-6));
            }
            if (f < 1) {
                if (f > 0) {
                    dx[i] = dx[i - 1] + (dx[i] - dx[i - 1]) * f;
                    dy[i] = dy[i - 1] + (dy[i] - dy[i - 1]) * f;
                    dz[i] = dz[i - 1] + (dz[i] - dz[i - 1]) * f;
                    dt[i] = dt[i - 1] + (dt[i] - dt[i - 1]) * f;
                    cut = i + 1;
                }
                break;
            }
            cut = i + 1;
            len += seg;
        }
        if (cut < 2) {
            return out;
        }
        acc[0] = 0;
        for (int i = 1; i < cut; i++) {
            acc[i] = acc[i - 1] + dist(dx[i - 1], dy[i - 1], dz[i - 1], dx[i], dy[i], dz[i]);
        }
        double total = acc[cut - 1];
        if (total < 1e-4) {
            return out;
        }
        out.ensure(segs + 1);
        int j = 0;
        for (int k = 0; k <= segs; k++) {
            double want = total * k / segs;
            while (j < cut - 2 && acc[j + 1] < want) {
                j++;
            }
            double f = Math.max(0, Math.min(1, (want - acc[j]) / Math.max(acc[j + 1] - acc[j], 1e-6)));
            out.x[k] = dx[j] + (dx[j + 1] - dx[j]) * f;
            out.y[k] = dy[j] + (dy[j + 1] - dy[j]) * f;
            out.z[k] = dz[j] + (dz[j + 1] - dz[j]) * f;
            out.s[k] = (float) k / segs;
            out.age[k] = (float) (now - (dt[j] + (dt[j + 1] - dt[j]) * f));
            out.dist[k] = (float) want;
        }
        out.n = segs + 1;
        out.total = (float) total;
        return out;
    }

    private static double cr(double a, double b, double c, double d, double u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u2 + (-a + 3 * b - 3 * c + d) * u3);
    }

    private static double dist(double ax, double ay, double az, double bx, double by, double bz) {
        double x = ax - bx, y = ay - by, z = az - bz;
        return Math.sqrt(x * x + y * y + z * z);
    }

    /** 取样结果：s 0 = 头、1 = 尾；dist 离头多远（格）；age 这一点是几秒前记的 */
    public static final class Sample {
        public double[] x = new double[0], y = new double[0], z = new double[0];
        public float[] s = new float[0], age = new float[0], dist = new float[0];
        public int n;
        public float total;

        void ensure(int cap) {
            if (x.length < cap) {
                x = new double[cap];
                y = new double[cap];
                z = new double[cap];
                s = new float[cap];
                age = new float[cap];
                dist = new float[cap];
            }
        }
    }

    public interface Fn {
        float at(float s, int i);
    }

    /** 把取样画成丝带：宽度、透明度按 s 给；kw、ka 是收敛档的拖尾系数（借拖尾画形状的传 1） */
    public static void draw(EphemeraPen pen, Sample pts, EphemeraPen.Ribbon buf, Fn width, Fn alpha, Fn rgb,
                            float kw, float ka, int face, float u0, float u1) {
        if (pts.n < 2) {
            pen.ribbon(buf.clear(), face, u0, u1);
            return;
        }
        buf.clear();
        for (int i = 0; i < pts.n; i++) {
            float s = pts.s[i];
            if (rgb != null) {
                buf.add(pts.x[i], pts.y[i], pts.z[i], width.at(s, i) * kw, alpha.at(s, i) * ka, (int) rgb.at(s, i));
            } else {
                buf.add(pts.x[i], pts.y[i], pts.z[i], width.at(s, i) * kw, alpha.at(s, i) * ka);
            }
        }
        pen.ribbon(buf, face, u0, u1);
    }
}
