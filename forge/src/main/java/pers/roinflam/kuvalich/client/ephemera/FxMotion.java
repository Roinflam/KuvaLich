package pers.roinflam.kuvalich.client.ephemera;

/**
 * 一个宿主这一帧的动作状态，正式画法都读它：位置、地面、身高身宽、速度、朝向，
 * 起跑 / 急停 / 起跳 / 落地 / 挥砍 / 受击各自最近一次的时刻，再加强度档的系数。全从宿主读，不加同步。
 */
public final class FxMotion {

    public static final double NEVER = -1.0E9D;
    /** 隔这么久没画（或体型变了）就当新的一份，各款把自己的拖尾、粒子清掉重来 */
    private static final long GAP_MS = 250L;

    /** 这份状态建起来以后过了几秒 */
    public double t;
    public float dt;
    public boolean fresh = true;
    public boolean lite;
    /** 0 其他、1 火、2 冰、3 电 */
    public int kind;
    public int color;

    public double x, y, z;
    /** 脚下地面的高度（腾空时往下找，找不到就是脚底） */
    public double groundY;
    public float heightAboveGround;
    /** 身高、身宽、整体缩放（1 = 原版玩家） */
    public float H, W, sc;
    public boolean sneak;
    public float fwdX, fwdZ, rightX, rightZ;
    /** 格/秒 */
    public float velX, velY, velZ, speed;
    public boolean onGround = true;
    public float landSpeed;
    public double walkDist;
    public double walkPhase;
    public int swingCount;

    public double tRunStart = NEVER, tStop = NEVER, tJump = NEVER, tLand = NEVER, tSwing = NEVER, tHurt = NEVER;
    public boolean justRunStart, justStop, justJump, justLand, justSwing, justHurt;
    public int nRunStart, nStop, nJump, nLand, nSwing, nHurt;

    public boolean calm;
    public float glow, emit, size, burst, burstTime, trailW, trailA, idle, act, amp;

    private final long bornMs;
    private long lastMs = -1L;
    private float lastScale = -1.0F;
    private boolean lastGround = true;
    private float lastSwing;
    private float lastHurt;
    private float minVy;
    private double airSince = NEVER;
    private double lastFast = NEVER;
    private double stopArmed = NEVER;

    public FxMotion(long nowMs) {
        this.bornMs = nowMs;
    }

    public float since(double eventT) {
        return (float) (t - eventT);
    }

    public void update(EphemeraInstance inst, EphemeraHost h, EphemeraDraw d) {
        long now = d.timeMs;
        t = (now - bornMs) / 1000.0D;
        EphemeraHost.Pose pose = h.pose();
        float scale = inst.look.scale();
        sc = h.height() / pose.vanillaHeight * scale;
        sneak = pose == EphemeraHost.Pose.CROUCH;
        H = pose == EphemeraHost.Pose.STAND || sneak ? h.height() * scale : 1.8F * sc;
        W = Math.max(h.width(), 0.05F) * scale;
        boolean gap = lastMs < 0L || now - lastMs > GAP_MS || Math.abs(sc - lastScale) > 1.0E-3F;
        fresh = gap;
        dt = gap ? 1.0F / 60.0F : Math.min(1.0F / 30.0F, Math.max(0.0F, (now - lastMs) / 1000.0F));
        lastMs = now;
        lastScale = sc;
        lite = !d.full;
        color = inst.look.color1();
        kind = switch (inst.look.element()) {
            case "fire" -> 1;
            case "ice" -> 2;
            case "electricity" -> 3;
            default -> 0;
        };

        x = h.x();
        z = h.z();
        y = h.y() + inst.look.yOffset();
        double g = h.groundY();
        groundY = (Double.isNaN(g) ? h.y() : Math.min(g, h.y())) + inst.look.yOffset();
        heightAboveGround = (float) Math.max(0.0D, y - groundY);
        float yaw = h.bodyYaw() * ((float) Math.PI / 180.0F);
        fwdX = -(float) Math.sin(yaw);
        fwdZ = (float) Math.cos(yaw);
        rightX = -fwdZ;
        rightZ = fwdX;

        boolean ground = h.onGround();
        float rawX = (float) h.vx() * 20.0F, rawZ = (float) h.vz() * 20.0F, rawY = (float) h.vy() * 20.0F;
        if (gap) {
            velX = rawX;
            velZ = rawZ;
            if (fresh) {
                resetEvents();
            }
        } else {
            // 位移按 tick 跳变，抹平一点，不然每 50 ms 抖一下
            float k = Math.min(1.0F, dt * 18.0F);
            velX += (rawX - velX) * k;
            velZ += (rawZ - velZ) * k;
        }
        velY = ground ? 0.0F : rawY;
        speed = (float) Math.sqrt(velX * velX + velZ * velZ);
        if (ground) {
            walkDist += speed * dt;
        }
        walkPhase = walkDist / 2.4D * Math.PI * 2;

        justRunStart = justStop = justJump = justLand = justSwing = justHurt = false;
        if (!gap) {
            if (lastGround && !ground && rawY > 0.0F) {
                justJump = true;
                tJump = t;
                nJump++;
            }
            if (!ground) {
                if (lastGround) {
                    airSince = t;
                    minVy = 0.0F;
                }
                minVy = Math.min(minVy, rawY);
            } else if (!lastGround && (t - airSince > 0.15D || minVy < -4.0F)) {
                justLand = true;
                tLand = t;
                nLand++;
                landSpeed = -minVy;
            }
            // 起跑：从站着起步，或者走着开始疾跑；急停：跑着的时候 0.35 秒内停下来
            if (speed > 3.0F) {
                if (t - lastFast > 0.6D && t - tRunStart > 0.6D) {
                    justRunStart = true;
                    tRunStart = t;
                    nRunStart++;
                }
                lastFast = t;
            }
            // 走路松手也会在零点几秒里停下，只认疾跑中停住
            if (speed > 4.6F) {
                stopArmed = t;
            }
            if (speed > 4.9F && t - lastFast < 0.05D && t - tRunStart > 1.0D && t - tStop > 0.3D && sprintEdge()) {
                justRunStart = true;
                tRunStart = t;
                nRunStart++;
            }
            if (speed < 1.0F && ground && t - stopArmed < 0.35D) {
                justStop = true;
                tStop = t;
                nStop++;
                stopArmed = NEVER;
            }
            float sw = h.swing();
            if (sw > 0.0F && lastSwing <= 0.0F) {
                justSwing = true;
                tSwing = t;
                nSwing++;
                swingCount++;
            }
            float hu = h.hurt();
            if (hu > lastHurt + 0.01F) {
                justHurt = true;
                tHurt = t;
                nHurt++;
            }
        }
        wasSprint = speed > 4.9F;
        lastGround = ground;
        onGround = ground;
        lastSwing = h.swing();
        lastHurt = h.hurt();

        EphemeraTier tier = d.tier;
        calm = tier != EphemeraTier.FULL_OPEN;
        if (calm) {
            act = EphemeraFx.clamp(Math.max(Math.max(speed / 5.6F, onGround ? 0.0F : 1.0F),
                    Math.max(Math.max(EphemeraFx.env0(since(tLand), 0, 0.1F, 0.5F), EphemeraFx.env0(since(tSwing), 0, 0.3F, 0.45F)),
                            EphemeraFx.env0(since(tHurt), 0, 0.5F, 0.45F))), 0, 1);
            idle = tier.idle;
            amp = idle + (1 - idle) * act;
            glow = d.glowAlpha * (0.85F + 0.15F * act);
            emit = tier.emit;
            size = tier.particleSize;
            burst = tier.burst;
            burstTime = tier.burstTime;
            trailW = tier.trailWidth;
            trailA = tier.trailAlpha;
        } else {
            act = amp = idle = glow = emit = size = burst = burstTime = trailW = trailA = 1.0F;
        }
        emit *= Math.min(1.0F, d.emitScale);
    }

    private boolean wasSprint;

    private boolean sprintEdge() {
        return !wasSprint;
    }

    private void resetEvents() {
        tRunStart = tStop = tJump = tLand = tSwing = tHurt = NEVER;
        lastFast = stopArmed = airSince = NEVER;
        lastGround = true;
    }

    /** 收敛档的事件包络整体压短；循环用的呼吸节律走 {@link EphemeraFx#env0}，不压 */
    public float env(float since, float rise, float hold, float fall) {
        return EphemeraFx.env0(since / burstTime, rise, hold, fall);
    }
}
