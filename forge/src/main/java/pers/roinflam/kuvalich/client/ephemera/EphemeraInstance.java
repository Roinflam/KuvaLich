package pers.roinflam.kuvalich.client.ephemera;

import pers.roinflam.kuvalich.ephemera.EphemeraLook;

/**
 * 一个带幻纹的实体在客户端留的那份状态：粒子池、拖尾历史、动作包络。实体离开世界、卸下或换款（包括改了颜色）时整份丢掉重建。
 * 动作全从宿主的速度、着地、挥砍、受击读，不加同步。
 */
public final class EphemeraInstance {

    /** 跑满（疾跑约 5.6 格/秒）时 move = 1 */
    private static final float RUN_SPEED = 5.6F;
    private static final float MOVING_SPEED = 1.0F;
    /** 收敛档前一次爆发持续多久（秒），再乘档位的 burstTime */
    private static final float BURST_SECONDS = 0.6F;

    public final int entityId;
    public final EphemeraLook look;
    public final EphemeraStyle style;

    /** 平滑后的移动量 0~1 */
    public float move;
    /** 起跑、急停、起跳、落地、挥砍、受击时置 1，按档位的时长落回 0 */
    public float burst;
    public final Particles particles = new Particles(128);
    public final Trail trail = new Trail(32);
    /** 各款自己要留的东西 */
    public Object styleState;
    /** 发射的小数部分攒到下一帧 */
    public float emitCarry;
    /** 上一帧到这一帧过了几秒（封顶 0.1） */
    public float dt;
    /** 最近一次被画到的时刻，渲染器据此丢掉长期没画的 */
    public long lastDrawnMs;

    private static final float SHOW_SECONDS = 0.35F;
    private float shown = 1.0F;
    private boolean shownReady;

    private long lastMs = -1L;
    private boolean lastGround = true;
    private boolean wasMoving;
    private float lastSwing;
    private float lastHurt;

    public EphemeraInstance(int entityId, EphemeraLook look, EphemeraStyle style) {
        this.entityId = entityId;
        this.look = look;
        this.style = style;
    }

    public void update(EphemeraHost h, long nowMs, EphemeraTier tier) {
        dt = lastMs < 0L ? 0.0F : Math.min(0.1F, Math.max(0.0F, (nowMs - lastMs) / 1000.0F));
        lastMs = nowMs;
        float speed = (float) Math.sqrt(h.vx() * h.vx() + h.vz() * h.vz()) * 20.0F;
        float target = Math.min(1.0F, speed / RUN_SPEED);
        move += (target - move) * Math.min(1.0F, dt * 6.0F);

        boolean moving = speed > MOVING_SPEED;
        boolean event = moving != wasMoving
                || (lastGround && !h.onGround() && h.vy() > 0.0D)
                || (!lastGround && h.onGround())
                || (h.swing() > 0.0F && lastSwing <= 0.0F)
                || (h.hurt() > lastHurt + 0.01F);
        if (event && dt > 0.0F) {
            burst = 1.0F;
        } else {
            burst = Math.max(0.0F, burst - dt / (BURST_SECONDS * tier.burstTime));
        }
        wasMoving = moving;
        lastGround = h.onGround();
        lastSwing = h.swing();
        lastHurt = h.hurt();

        particles.step(dt);
        trail.age(nowMs);
    }

    /** 收起 / 亮回来，返回这一帧的透明度；头一帧直接到位，不演过渡。要在 {@link #update} 之后调 */
    public float fade(boolean visible) {
        float target = visible ? 1.0F : 0.0F;
        if (!shownReady) {
            shownReady = true;
            shown = target;
        } else if (shown != target) {
            float step = dt / SHOW_SECONDS;
            shown = visible ? Math.min(1.0F, shown + step) : Math.max(0.0F, shown - step);
        }
        return shown;
    }

    /** 这一帧该发几个粒子：rate = 每秒几个 */
    public int emitCount(float rate) {
        emitCarry += rate * dt;
        int n = (int) emitCarry;
        emitCarry -= n;
        return n;
    }

    /** 自己管的粒子池，满了覆盖最老的 */
    public static final class Particles {
        public final float[] x, y, z, vx, vy, vz, age, life, size;
        public final int[] rgb;
        private final int cap;
        private int head;
        private int count;
        /** 每秒的速度衰减系数，0 = 不衰减 */
        public float drag = 1.5F;
        /** 竖直加速度（格/秒²），往上窜给正的 */
        public float lift;

        Particles(int cap) {
            this.cap = cap;
            x = new float[cap];
            y = new float[cap];
            z = new float[cap];
            vx = new float[cap];
            vy = new float[cap];
            vz = new float[cap];
            age = new float[cap];
            life = new float[cap];
            size = new float[cap];
            rgb = new int[cap];
        }

        /** 坐标存相对宿主脚底的偏移，宿主走了粒子跟着走 */
        public void spawn(float px, float py, float pz, float pvx, float pvy, float pvz, float plife, float psize, int color) {
            int i = head;
            head = (head + 1) % cap;
            count = Math.min(cap, count + 1);
            x[i] = px;
            y[i] = py;
            z[i] = pz;
            vx[i] = pvx;
            vy[i] = pvy;
            vz[i] = pvz;
            age[i] = 0.0F;
            life[i] = Math.max(0.05F, plife);
            size[i] = psize;
            rgb[i] = color;
        }

        void step(float dt) {
            if (dt <= 0.0F) {
                return;
            }
            float k = Math.max(0.0F, 1.0F - drag * dt);
            for (int i = 0; i < count; i++) {
                if (age[i] >= life[i]) {
                    continue;
                }
                age[i] += dt;
                vy[i] += lift * dt;
                vx[i] *= k;
                vy[i] *= k;
                vz[i] *= k;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
                z[i] += vz[i] * dt;
            }
        }

        public int slots() {
            return count;
        }

        public boolean alive(int i) {
            return age[i] < life[i];
        }

        /** 0 刚生 ~ 1 将灭 */
        public float t(int i) {
            return age[i] / life[i];
        }
    }

    /** 拖尾历史：世界坐标，新的在前 */
    public static final class Trail {
        private final double[] x, y, z;
        private final long[] at;
        private final int cap;
        private int head;
        private int count;
        /** 超过这么久的点丢掉 */
        public long maxAgeMs = 450L;
        /** 和上一个点隔这么远（格）才记一个新点 */
        public double minStep = 0.12D;

        Trail(int cap) {
            this.cap = cap;
            x = new double[cap];
            y = new double[cap];
            z = new double[cap];
            at = new long[cap];
        }

        public void push(double px, double py, double pz, long nowMs) {
            if (count > 0) {
                int last = (head - 1 + cap) % cap;
                double dx = px - x[last], dy = py - y[last], dz = pz - z[last];
                if (dx * dx + dy * dy + dz * dz < minStep * minStep) {
                    return;
                }
            }
            x[head] = px;
            y[head] = py;
            z[head] = pz;
            at[head] = nowMs;
            head = (head + 1) % cap;
            count = Math.min(cap, count + 1);
        }

        void age(long nowMs) {
            while (count > 0 && nowMs - at[(head - count + cap) % cap] > maxAgeMs) {
                count--;
            }
        }

        public int size() {
            return count;
        }

        /** 第 i 新的点（0 = 最新） */
        public double x(int i) {
            return x[(head - 1 - i + cap * 2) % cap];
        }

        public double y(int i) {
            return y[(head - 1 - i + cap * 2) % cap];
        }

        public double z(int i) {
            return z[(head - 1 - i + cap * 2) % cap];
        }
    }
}
