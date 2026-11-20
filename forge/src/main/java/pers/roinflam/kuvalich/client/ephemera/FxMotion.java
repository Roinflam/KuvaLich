package pers.roinflam.kuvalich.client.ephemera;

/**
 * 一个宿主这一帧的动作状态，正式画法都读它：位置、地面、身高身宽、速度、朝向，
 * 起跑 / 急停 / 起跳 / 落地 / 挥砍 / 受击各自最近一次的时刻，再加强度档的系数。全从宿主读，不加同步。
 */
public final class FxMotion {

    public static final double NEVER = -1.0E9D;
    /** 隔这么久没画（或体型变了）就当新的一份，各款把自己的拖尾、粒子清掉重来 */
    private static final double GAP_S = 0.25D;
    /** 各款的弹簧是半隐式积分，最硬的一根（盘旋中心竖直方向，k=484、c=35.2）步长过 0.044 秒就发散 */
    private static final float MAX_DT = 0.04F;
    /** 一帧挪了这么多格就当瞬移；鞘翅加烟花一 tick 也就 3 格出头 */
    private static final double TELEPORT = 8.0D;
    /** 原地跳一下约 0.6 秒、1.25 格；腾空超过这么久或离起跳的地面这么高，就不算「跳一下」了 */
    private static final float AIR_FOLLOW_S = 1.0F;
    private static final double PROBE_S = 0.05D;
    /** 横着挪了这么远就重新往下找地面，跳上方块时贴地层才不晚一拍 */
    private static final double PROBE_MOVE = 0.05D;
    private static final float AIR_FOLLOW_H = 2.5F;

    /** 这份状态建起来以后过了几秒 */
    public double t;
    public float dt;
    public boolean fresh = true;
    public boolean lite;
    /** 0 其他、1 火、2 冰、3 电 */
    public int kind;
    public int color;

    public double x, y, z;
    /** 贴地层的基准高度：着地是脚底，跳一下时留在起跳那层地面，飞行、泡水、爬梯、骑乘时跟着脚走 */
    public double groundY;
    /** 脚离贴地层基准多高；挂到脚下时是 0 */
    public float heightAboveGround;
    /** 贴地层挂到脚下的程度 0~1：0 = 留在地上，1 = 跟着脚走（飞行、水里、爬梯、骑乘） */
    public float attach;
    /** 身高、身宽、整体缩放（1 = 原版玩家） */
    public float H, W, sc;
    public boolean sneak;
    public float fwdX, fwdZ, rightX, rightZ;
    /** 格/秒，水平速度抹平过，给倾斜、拖曳这类看速度大小的用 */
    public float velX, velY, velZ, speed;
    /** 模型这一 tick 实际的移动速度（格/秒），没抹平；弹簧的阻尼按它算，起步、急停才不慢半拍 */
    public float trackX, trackY, trackZ;
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

    private final double born;
    private double last = Double.NaN;
    private float lastScale = -1.0F;
    private boolean lastGround = true;
    private float lastSwing;
    private float lastHurt;
    private float minVy;
    private double airSince = NEVER;
    private double lastFast = NEVER;
    private double stopArmed = NEVER;
    private double lastX, lastZ, lastFeet = Double.NaN;
    private double takeoff = Double.NaN;
    private double groundSmooth, groundBase;
    private double probed = Double.NaN, probedAt = NEVER, probedX, probedZ;
    private float airTime;

    public FxMotion(double now) {
        this.born = now;
    }

    public float since(double eventT) {
        return (float) (t - eventT);
    }

    public void update(EphemeraInstance inst, EphemeraHost h, EphemeraDraw d) {
        double now = d.time;
        t = now - born;
        EphemeraHost.Pose pose = h.pose();
        float scale = inst.look.scale();
        sc = h.height() / pose.vanillaHeight * scale;
        sneak = pose == EphemeraHost.Pose.CROUCH;
        H = pose == EphemeraHost.Pose.STAND || sneak ? h.height() * scale : 1.8F * sc;
        W = Math.max(h.width(), 0.05F) * scale;
        boolean gap = Double.isNaN(last) || now - last > GAP_S || Math.abs(sc - lastScale) > 1.0E-3F;
        fresh = gap;
        dt = gap ? 1.0F / 60.0F : Math.min(MAX_DT, Math.max(0.0F, (float) (now - last)));
        last = now;
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
        double vy0 = h.vehicleY();
        double feet = h.riding() && !Double.isNaN(vy0) ? vy0 : h.y();
        boolean ground = h.onGround();
        boolean jumped = false;
        if (!gap && !Double.isNaN(lastFeet) && Math.abs(feet - lastFeet) + Math.abs(x - lastX) + Math.abs(z - lastZ) > TELEPORT) {
            // 传送、换维度：当新的一份，免得拖尾、弹簧从老地方一路扫过来
            gap = true;
            fresh = true;
            jumped = true;
            dt = 1.0F / 60.0F;
        }
        lastX = x;
        lastZ = z;
        lastFeet = feet;
        updateGround(h, feet, ground, gap);
        groundY = groundBase + inst.look.yOffset();
        heightAboveGround = (float) Math.max(0.0D, feet - groundBase);
        float yaw = h.bodyYaw() * ((float) Math.PI / 180.0F);
        fwdX = -(float) Math.sin(yaw);
        fwdZ = (float) Math.cos(yaw);
        rightX = -fwdZ;
        rightZ = fwdX;

        // 瞬移那一帧的位移差不是速度
        float rawX = jumped ? 0.0F : (float) h.vx() * 20.0F, rawZ = jumped ? 0.0F : (float) h.vz() * 20.0F,
                rawY = jumped ? 0.0F : (float) h.vy() * 20.0F;
        trackX = rawX;
        trackY = rawY;
        trackZ = rawZ;
        if (gap) {
            velX = rawX;
            velZ = rawZ;
            if (fresh) {
                resetEvents();
            }
        } else {
            // 位移按 tick 跳变，抹平一点，不然每 50 ms 抖一下
            float k = 1.0F - (float) Math.exp(-dt * 18.0F);
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
            act = EphemeraFx.clamp(Math.max(Math.max(speed / 5.6F, onGround ? 0.0F : 1.0F - attach),
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

    /**
     * 贴地层的基准。跳一下、下个台阶照旧留在地上：腾空时取起跳那层地面，脚落到它下面就换到下面找到的地面，
     * 往下找到更高的地面（跳上方块）就抬上去。创造飞行、泡在水里、爬梯、骑乘、趴着、腾空太久或离地太高时整个挂到脚下，
     * 两种之间按 {@link #attach} 渐变，基准本身的跳变也抹平，不许一帧跳过去。
     */
    private void updateGround(EphemeraHost h, double feet, boolean ground, boolean gap) {
        EphemeraHost.Pose pose = h.pose();
        boolean lying = pose == EphemeraHost.Pose.SWIM || pose == EphemeraHost.Pose.FLY || pose == EphemeraHost.Pose.SLEEP;
        double target;
        if (ground || h.riding()) {
            // 着地取这一 tick 的脚底：落地那个 tick 里插值出来的脚还在半空，拿它当地面贴地层会先蹦上去再落回来
            double floor = h.riding() ? Double.NaN : h.groundY();
            target = Double.isNaN(floor) ? feet : Math.min(feet, floor);
            takeoff = target;
            airTime = 0.0F;
            probedAt = NEVER;
        } else {
            // 往下扫方块碰撞不便宜：原地跳时隔一个 tick 查一次，横着挪动了才马上重查
            if (gap || t - probedAt >= PROBE_S || Math.abs(x - probedX) + Math.abs(z - probedZ) > PROBE_MOVE) {
                probed = h.groundY();
                probedAt = t;
                probedX = x;
                probedZ = z;
            }
            double found = probed;
            if (gap || Double.isNaN(takeoff)) {
                takeoff = Double.isNaN(found) ? feet : Math.min(found, feet);
                airTime = 0.0F;
            }
            airTime += dt;
            boolean hasFloor = !Double.isNaN(found) && found <= feet;
            if (feet >= takeoff) {
                // 还在起跳那层上面：跳一下、跳过沟都留在起跳处，跳上方块就抬上去
                target = hasFloor ? Math.max(takeoff, found) : takeoff;
            } else {
                // 走下台阶、掉下去：直接落到下面的地面，不跟着脚一路往下滑
                target = hasFloor ? found : feet;
            }
        }
        boolean follow = h.riding() || h.flying() || h.inFluid() || h.climbing() || lying
                || (!ground && (airTime > AIR_FOLLOW_S || feet - Math.max(target, takeoff) > AIR_FOLLOW_H * Math.max(sc, 0.25F)));
        if (gap) {
            groundSmooth = target;
            attach = follow ? 1.0F : 0.0F;
        } else {
            if (target > groundSmooth && ground) {
                groundSmooth = target;
            } else {
                groundSmooth += (target - groundSmooth) * (1.0F - (float) Math.exp(-dt * 25.0F));
            }
            attach += ((follow ? 1.0F : 0.0F) - attach) * (1.0F - (float) Math.exp(-dt * 8.0F));
        }
        groundSmooth = Math.min(groundSmooth, feet);
        groundBase = groundSmooth + (feet - groundSmooth) * attach;
    }

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
