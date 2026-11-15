package pers.roinflam.kuvalich.client.ephemera;

/**
 * 正式画法自己的粒子池，世界坐标（走过的路上冒的烟留在原地）。每粒一个面片：朝镜头的方片，
 * 或者设了 stretch 时沿速度拉成长条。收敛档按 {@link #thin} 均匀抽掉一部分（累加，不是随机，节奏稳定）；
 * 池满了只有 force 的能挤掉最老的。
 *
 * <p>发射：{@code P.spec().pos(..).vel(..).life(..).size(..).sprite(..).rgb(..)....spawn()}，spec 是复用的。</p>
 */
public final class FxParticles {

    private final int cap;
    private int n;
    public float thin = 1.0F;
    private float thinAcc;

    final double[] x, y, z, floorY;
    final float[] vx, vy, vz, age, life, size0, size1, a, rot, spin, grav, drag, stretch, fadeIn, fadeOut;
    final int[] rgb, sprite, data;
    final boolean[] add, floor;

    private final Spec spec = new Spec();

    public FxParticles(int cap) {
        this.cap = cap;
        x = new double[cap];
        y = new double[cap];
        z = new double[cap];
        floorY = new double[cap];
        vx = new float[cap];
        vy = new float[cap];
        vz = new float[cap];
        age = new float[cap];
        life = new float[cap];
        size0 = new float[cap];
        size1 = new float[cap];
        a = new float[cap];
        rot = new float[cap];
        spin = new float[cap];
        grav = new float[cap];
        drag = new float[cap];
        stretch = new float[cap];
        fadeIn = new float[cap];
        fadeOut = new float[cap];
        rgb = new int[cap];
        sprite = new int[cap];
        data = new int[cap];
        add = new boolean[cap];
        floor = new boolean[cap];
    }

    public int count() {
        return n;
    }

    public void clear() {
        n = 0;
    }

    public Spec spec() {
        return spec.reset();
    }

    public interface Step {
        void apply(FxParticles p, int i, float dt);
    }

    public void step(float dt, Step fn) {
        int w = 0;
        for (int i = 0; i < n; i++) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                continue;
            }
            if (fn != null) {
                fn.apply(this, i, dt);
            }
            if (grav[i] != 0) {
                vy[i] -= grav[i] * dt;
            }
            if (drag[i] != 0) {
                float k = (float) Math.exp(-drag[i] * dt);
                vx[i] *= k;
                vy[i] *= k;
                vz[i] *= k;
            }
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            z[i] += vz[i] * dt;
            if (floor[i] && y[i] < floorY[i]) {
                y[i] = floorY[i];
                if (vy[i] < 0) {
                    vy[i] = 0;
                }
            }
            rot[i] += spin[i] * dt;
            if (w != i) {
                move(i, w);
            }
            w++;
        }
        n = w;
    }

    private void move(int from, int to) {
        x[to] = x[from];
        y[to] = y[from];
        z[to] = z[from];
        floorY[to] = floorY[from];
        vx[to] = vx[from];
        vy[to] = vy[from];
        vz[to] = vz[from];
        age[to] = age[from];
        life[to] = life[from];
        size0[to] = size0[from];
        size1[to] = size1[from];
        a[to] = a[from];
        rot[to] = rot[from];
        spin[to] = spin[from];
        grav[to] = grav[from];
        drag[to] = drag[from];
        stretch[to] = stretch[from];
        fadeIn[to] = fadeIn[from];
        fadeOut[to] = fadeOut[from];
        rgb[to] = rgb[from];
        sprite[to] = sprite[from];
        data[to] = data[from];
        add[to] = add[from];
        floor[to] = floor[from];
    }

    /** sizeMul 整体乘大小（收敛档的 size 已经算在里面），alphaMul 整体乘透明度；twinkle 不是 NaN 时按它一闪一闪 */
    public void draw(EphemeraPen pen, float sizeMul, float alphaMul, float twinkle) {
        for (int i = 0; i < n; i++) {
            float k = age[i] / life[i];
            float sz = (size0[i] + (size1[i] - size0[i]) * k) * sizeMul;
            float fade = Math.min(1, k / Math.max(fadeIn[i], 1e-4F)) * Math.min(1, (1 - k) / Math.max(fadeOut[i], 1e-4F));
            float al = a[i] * fade * alphaMul;
            if (!Float.isNaN(twinkle)) {
                al *= 0.55F + 0.45F * (float) Math.sin(twinkle * 9 + rot[i] * 7);
            }
            if (al <= 0.002F || sz <= 0) {
                continue;
            }
            pen.s(sprite[i]).rgb(rgb[i]).a(al);
            if (!add[i]) {
                pen.alphaBlend();
            }
            if (stretch[i] > 0) {
                double tx = x[i] - vx[i] * stretch[i], ty = y[i] - vy[i] * stretch[i], tz = z[i] - vz[i] * stretch[i];
                double dx = tx - x[i], dy = ty - y[i], dz = tz - z[i];
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) > sz * 0.5F) {
                    pen.beam(tx, ty, tz, x[i], y[i], z[i], sz);
                    continue;
                }
            }
            pen.billboard(x[i], y[i], z[i], sz, sz, rot[i]);
        }
    }

    public final class Spec {
        private double sx, sy, sz, sfloor;
        private float svx, svy, svz, slife, ss0, ss1, sa, srot, sspin, sgrav, sdrag, sstretch, sin, sout;
        private int srgb, ssprite, sdata;
        private boolean sadd, sfloorOn, sforce, skeep;

        Spec reset() {
            svx = svy = svz = 0;
            slife = 1;
            ss0 = ss1 = 0.2F;
            sa = 1;
            srot = sspin = sgrav = sdrag = sstretch = 0;
            sin = 0.1F;
            sout = 0.5F;
            srgb = EphemeraFx.WHITE;
            ssprite = EphemeraSprites.SOFT;
            sdata = 0;
            sadd = true;
            sfloorOn = sforce = skeep = false;
            return this;
        }

        public Spec pos(double px, double py, double pz) {
            sx = px;
            sy = py;
            sz = pz;
            return this;
        }

        public Spec vel(float x, float y, float z) {
            svx = x;
            svy = y;
            svz = z;
            return this;
        }

        public Spec life(float v) {
            slife = v;
            return this;
        }

        /** 起止大小 */
        public Spec size(float from, float to) {
            ss0 = from;
            ss1 = to;
            return this;
        }

        public Spec size(float v) {
            return size(v, v);
        }

        public Spec a(float v) {
            sa = v;
            return this;
        }

        public Spec rgb(int c) {
            srgb = c;
            return this;
        }

        public Spec sprite(int id) {
            ssprite = id;
            return this;
        }

        public Spec alphaBlend() {
            sadd = false;
            return this;
        }

        public Spec rot(float r) {
            srot = r;
            return this;
        }

        public Spec spin(float s) {
            sspin = s;
            return this;
        }

        /** 向下的加速度，负的往上飘 */
        public Spec grav(float g) {
            sgrav = g;
            return this;
        }

        public Spec drag(float v) {
            sdrag = v;
            return this;
        }

        public Spec stretch(float v) {
            sstretch = v;
            return this;
        }

        public Spec fade(float in, float out) {
            sin = in;
            sout = out;
            return this;
        }

        /** 不钻到这个高度以下 */
        public Spec floor(double y) {
            sfloorOn = true;
            sfloor = y;
            return this;
        }

        public Spec data(int v) {
            sdata = v;
            return this;
        }

        public Spec force() {
            sforce = true;
            return this;
        }

        public Spec keep() {
            skeep = true;
            return this;
        }

        public int spawn() {
            if (thin < 1 && !skeep) {
                thinAcc += thin;
                if (thinAcc < 1) {
                    return -1;
                }
                thinAcc -= 1;
            }
            int i;
            if (n >= cap) {
                if (!sforce) {
                    return -1;
                }
                // 挤掉最老的（下标 0），保持从老到新的顺序
                for (int k = 1; k < n; k++) {
                    move(k, k - 1);
                }
                i = n - 1;
            } else {
                i = n++;
            }
            x[i] = sx;
            y[i] = sy;
            z[i] = sz;
            floorY[i] = sfloor;
            floor[i] = sfloorOn;
            vx[i] = svx;
            vy[i] = svy;
            vz[i] = svz;
            age[i] = 0;
            life[i] = slife > 0 ? slife : 1;
            size0[i] = ss0;
            size1[i] = ss1;
            a[i] = sa;
            rot[i] = srot;
            spin[i] = sspin;
            grav[i] = sgrav;
            drag[i] = sdrag;
            stretch[i] = sstretch;
            fadeIn[i] = sin;
            fadeOut[i] = sout;
            rgb[i] = srgb;
            sprite[i] = ssprite;
            data[i] = sdata;
            add[i] = sadd;
            return i;
        }
    }
}
