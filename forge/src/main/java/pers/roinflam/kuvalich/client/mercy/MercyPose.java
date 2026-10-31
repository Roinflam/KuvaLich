package pers.roinflam.kuvalich.client.mercy;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 处决者的姿态：第三人称（自己按 F5、旁观者、运镜里）按关键帧覆盖人形模型各部件的角度，第一人称算两条胳膊的位置
 *
 * <p><b>角度的约定</b>（表里都是度，语义值）：「抬」= 手臂从下垂往身前抬（90 水平前伸）；「外张」往身体外侧张，
 * 「内收」反之；「内扣」= 前伸的手臂往身体中线拧；「扭」正值 = 右肩往前送（和原版右手挥砍刚开始时身体转的方向一致）；
 * 「前倾」正值 = 上身往前俯（和原版下蹲同号）；「腿」正值 = 这条腿往前摆。换成模型弧度：臂 xRot = −抬，
 * 右臂 zRot = +外张、左臂 zRot = +内收，右臂 yRot = −内扣；身体 xRot = +前倾；腿 xRot = −前摆。
 * 前倾时照原版下蹲那组偏移按比例挪头、臂、腿（原版下蹲是 28.6°，比例封顶 1），「蹲」再叠一层。</p>
 *
 * <p>头一律看向目标胸口，表里不写。关键帧之间默认 {@code easeInOutSine}；出刀的帧用 {@code easeOutCubic}、
 * 从上一帧起 120 ms 内到位；切镜时直接摆好的帧不插值。</p>
 *
 * <p>注在 {@code HumanoidModel#setupAnim} 的末尾（见 {@code MixinHumanoidModelMercy}），玩家模型随后自己把袖子、
 * 裤腿、外套复制过去，皮肤外层跟着动；帽子层原版在 setupAnim 里已经复制过，这里改完头再复制一次。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MercyPose {

    // 一行关键帧里各值的下标
    private static final int TWIST = 0, LEAN = 1, SQUAT = 2, R_RAISE = 3, R_ABD = 4, R_IN = 5,
            L_RAISE = 6, L_ADD = 7, R_LEG = 8, L_LEG = 9, N = 10;

    private static final int SINE = 0, OUT = 1, SNAP = 2;
    /** 出刀帧从上一帧起多久到位 */
    private static final int OUT_MS = 120;
    private static final float NA = Float.NaN;
    private static final float DEG = (float) Math.PI / 180.0F;
    private static final double RAD_TO_DEG = 180.0D / Math.PI;

    /** 第一人称画胳膊的那一遍：模型由原版的 renderHand 自己摆，这里不要再改 */
    static boolean firstPersonPass;

    /** 一个关键帧：{@code start} 小于 0 时按默认（出刀帧 t − 120，其余从上一帧起） */
    private record Key(int t, int start, int mode, float[] v) {
    }

    /** 一场处决的整条姿态轨道（开始时按计划位拼好） */
    static final class Track {
        private final Key[] keys;
        private final int[] stabs;
        private final boolean thrall;

        private Track(Key[] keys, int[] stabs, boolean thrall) {
            this.keys = keys;
            this.stabs = stabs;
            this.thrall = thrall;
        }
    }

    private MercyPose() {
    }

    // ==================== 关键帧表 ====================

    private static float[] row(float twist, float lean, float squat, float rRaise, float rAbd, float rIn,
                               float lRaise, float lAdd, float rLeg, float lLeg) {
        return new float[]{twist, lean, squat, rRaise, rAbd, rIn, lRaise, lAdd, rLeg, lLeg};
    }

    private static Key k(int t, int mode, float[] v) {
        return new Key(t, -1, mode, v);
    }

    private static Key k(int t, int start, int mode, float[] v) {
        return new Key(t, start, mode, v);
    }

    /** 正面直刺 */
    private static final Key[] LICH_A = {
            k(0, SINE, row(0, 5, 0, 20, 15, 0, 30, 0, 0, 0)),
            k(250, SINE, row(-10, 12, NA, 35, 20, NA, 105, 15, -15, 15)),
            k(650, SINE, row(-22, 12, NA, 30, 25, NA, 105, 15, NA, NA)),
            k(790, OUT, row(12, 16, NA, 85, 0, NA, 100, 15, NA, NA)),
            k(1000, SINE, row(6, 15, NA, 82, 0, NA, 100, 15, NA, NA)),
            k(1450, SINE, row(-24, 12, NA, 35, 25, NA, 102, 15, NA, NA)),
            k(1680, OUT, row(16, 20, NA, 88, 0, NA, 98, 15, NA, NA)),
            k(1900, SINE, row(8, 19, NA, 85, 0, NA, 98, 15, NA, NA)),
            k(2400, SINE, row(-28, 14, NA, 30, 28, NA, 100, 15, NA, NA)),
            k(2670, OUT, row(22, 26, NA, 92, 0, 10, 95, 15, NA, 25)),
            k(3000, SINE, row(10, 30, NA, 70, 0, NA, 60, 0, NA, NA)),
            k(3400, SINE, row(0, 10, NA, 40, 10, 0, 25, 0, 0, 0)),
    };

    /** 背后锁颈（腿照正面那套站） */
    private static final Key[] LICH_B = {
            k(0, SINE, row(0, 5, 0, 20, 15, 0, 30, 0, 0, 0)),
            k(250, SINE, row(-8, 14, NA, 30, 15, NA, 120, 20, -15, 15)),
            k(650, SINE, row(-18, 14, NA, 25, 22, NA, 120, 20, NA, NA)),
            k(790, OUT, row(10, 18, NA, 80, 0, NA, 118, 20, NA, NA)),
            k(1450, SINE, row(-20, 15, NA, 28, 22, NA, 118, 20, NA, NA)),
            k(1680, OUT, row(14, 22, NA, 84, 0, NA, 116, 20, NA, NA)),
            k(2400, SINE, row(-24, 16, NA, 26, 25, NA, 118, 20, NA, NA)),
            k(2670, OUT, row(20, 28, NA, 90, 0, NA, 110, 20, NA, 25)),
            k(2900, SINE, row(10, 20, NA, 75, 0, NA, 40, 0, NA, NA)),
            k(3300, SINE, row(0, 10, NA, 45, 10, NA, 20, 0, 0, 0)),
    };

    /** 贴身上挑（蹲着冲进怀里，每刀起身送刀） */
    private static final Key[] LICH_C = {
            k(0, SINE, row(0, 5, 0, 20, 15, 0, 30, 0, 0, 0)),
            k(250, SINE, row(0, 25, 1, 15, 10, NA, 95, 10, -15, 15)),
            k(650, SINE, row(NA, 28, 1, 10, 10, NA, 100, 10, NA, NA)),
            k(790, OUT, row(NA, 15, 0.6F, 140, 0, 5, 90, 10, NA, NA)),
            k(1300, SINE, row(NA, 26, 1, 15, 10, 0, 100, 10, NA, NA)),
            k(1680, OUT, row(NA, 12, 0.6F, 145, 0, NA, 88, 10, NA, NA)),
            k(2300, SINE, row(NA, 28, 1, 12, 10, NA, 100, 10, NA, NA)),
            k(2670, OUT, row(NA, 8, 0.3F, 150, 0, NA, 80, 10, NA, 25)),
            k(2900, SINE, row(NA, 10, 0.2F, 120, 0, NA, 40, 0, NA, NA)),
            k(3400, SINE, row(NA, 5, 0, 50, 10, NA, 20, 0, 0, 0)),
    };

    /** 刀没入跪着的玄骸胸口、左手按在它头盔上（第三人称收尾） */
    private static final float[] PLUNGED = row(5, 18, 0, 62, 0, 0, 120, 10, 0, 20);

    /**
     * 跪地特写这一段：刀刚扎进去、刃身还有一截露在外面。拿 {@link #PLUNGED} 拍特写，拳头已经贴到（站得近的套路是埋进）
     * 目标身上，刀整根在它身体里，镜头里只剩刀柄和手。这里上身往后仰、右肩往回带，胳膊抬平、稍往里收，拳头停在
     * 目标身前，刃身从拳头一路斜着扎进它腹部，从特写那一侧看得见。处决者站得越近（A 1.25、B 1.10、C 0.95 格），
     * 往后让得越多；左手照旧按在它头盔上。切到第三人称时直接换成 {@link #PLUNGED}，看着就是一刀捅到底
     */
    private static final float[] CLOSEUP_A = row(-12, -5, 0, 80, 0, 12, 120, 10, 0, 20);
    private static final float[] CLOSEUP_B = row(-26, -20, 0, 80, 0, 14, 120, 10, 0, 20);
    private static final float[] CLOSEUP_C = row(-30, -25, 0, 80, 0, 15, 120, 10, 0, 20);

    /** 成功收尾抽刀以后（三套共用）：抽刀；交还 */
    private static final Key[] SUCCESS_TAIL = {
            k(7680, MercyTimeline.DRAW_OUT_MS, OUT, row(-8, 8, 0, 25, 20, 0, 30, 0, 0, 0)),
            k(MercyTimeline.SUCCESS_END_MS, MercyTimeline.SUCCESS_END_MS - 200, SINE, zero()),
    };

    /** 奴仆：抓肩直刺 */
    private static final Key[] THRALL_1 = {
            k(0, SINE, row(0, 5, 0, 20, 15, 0, 30, 0, 0, 0)),
            k(350, SINE, row(NA, 14, NA, 30, 20, NA, 75, 0, -10, 10)),
            k(800, SINE, row(-20, NA, NA, 25, 25, NA, NA, NA, NA, NA)),
            k(900, OUT, row(12, 22, NA, 60, 0, NA, NA, NA, NA, NA)),
            k(1200, SINE, row(NA, NA, NA, NA, NA, NA, NA, NA, NA, NA)),
            k(1550, 1350, SINE, row(0, 16, NA, 30, 0, NA, 90, 0, NA, NA)),
            k(2000, SINE, row(0, 5, 0, 15, 10, 0, 15, 0, 0, 0)),
            k(MercyTimeline.THRALL_END_MS, SINE, zero()),
    };

    /** 奴仆：锁喉背刺，推开时一脚踹出去 */
    private static final Key[] THRALL_2 = {
            k(0, SINE, row(0, 5, 0, 20, 15, 0, 30, 0, 0, 0)),
            k(400, SINE, row(NA, 16, NA, 25, 20, NA, 85, 35, -10, 10)),
            k(850, SINE, row(-18, NA, NA, 22, 25, NA, NA, NA, NA, NA)),
            k(950, OUT, row(12, 20, NA, 55, 0, NA, NA, NA, NA, NA)),
            k(1250, SINE, row(NA, NA, NA, NA, NA, NA, NA, NA, NA, NA)),
            k(1550, 1400, SINE, row(0, 10, NA, 35, 10, NA, 20, 0, 55, -5)),
            k(1750, SINE, row(NA, 8, NA, NA, NA, NA, NA, NA, 0, 0)),
            k(2000, SINE, row(0, 5, 0, 15, 10, 0, 15, 0, 0, 0)),
            k(MercyTimeline.THRALL_END_MS, SINE, zero()),
    };

    /** 奴仆：压颈下刺（刀举过头再往下扎） */
    private static final Key[] THRALL_3 = {
            k(0, SINE, row(0, 5, 0, 20, 15, 0, 30, 0, 0, 0)),
            k(400, SINE, row(NA, 20, NA, 40, 15, NA, 70, 10, -10, 10)),
            k(850, SINE, row(NA, NA, NA, 150, 5, NA, NA, NA, NA, NA)),
            k(1000, OUT, row(NA, 25, NA, 40, 0, NA, NA, NA, NA, NA)),
            k(1300, SINE, row(NA, NA, NA, NA, NA, NA, NA, NA, NA, NA)),
            k(1600, 1450, SINE, row(0, 14, NA, 30, 0, NA, 80, -30, NA, NA)),
            k(2000, SINE, row(0, 5, 0, 15, 10, 0, 15, 0, 0, 0)),
            k(MercyTimeline.THRALL_END_MS, SINE, zero()),
    };

    /** 判错后被推开、踉跄 */
    private static final float[] PUSHED = row(0, -10, 0, 40, 10, 0, 30, 0, 10, -10);

    private static float[] zero() {
        return row(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    static {
        resolve(LICH_A);
        resolve(LICH_B);
        resolve(LICH_C);
        resolve(THRALL_1);
        resolve(THRALL_2);
        resolve(THRALL_3);
    }

    /** 表里空着的值沿用上一行 */
    private static void resolve(Key[] keys) {
        for (int i = 1; i < keys.length; i++) {
            float[] prev = keys[i - 1].v;
            float[] cur = keys[i].v;
            for (int j = 0; j < N; j++) {
                if (Float.isNaN(cur[j])) {
                    cur[j] = prev[j];
                }
            }
        }
    }

    private static Key[] tableOf(MercyVariant v) {
        return switch (v) {
            case LICH_A -> LICH_A;
            case LICH_B -> LICH_B;
            case LICH_C -> LICH_C;
            case THRALL_1 -> THRALL_1;
            case THRALL_2 -> THRALL_2;
            case THRALL_3 -> THRALL_3;
        };
    }

    private static float[] closeupPose(MercyVariant v) {
        return switch (v) {
            case LICH_B -> CLOSEUP_B;
            case LICH_C -> CLOSEUP_C;
            default -> CLOSEUP_A;
        };
    }

    /** 按计划位拼出这一场的姿态轨道 */
    static Track track(MercyPlan.Plan p, boolean shortOutro) {
        MercyVariant v = p.variant();
        Key[] table = tableOf(v);
        int[] stabs = MercyTimeline.stabTimes(p);
        if (v.thrall) {
            return new Track(table, stabs, true);
        }
        List<Key> out = new ArrayList<>();
        int handback = MercyTimeline.handbackMs(p, shortOutro);
        switch (MercyTimeline.outcome(p)) {
            case SUCCESS -> {
                out.addAll(Arrays.asList(table));
                // 切跪地特写那一刻直接摆成刀扎进去、刃身露一截（特写里要拍到这只手和刀）
                float[] closeup = closeupPose(v);
                out.add(k(MercyTimeline.CLOSEUP_MS, SNAP, closeup));
                if (shortOutro) {
                    // 特写完直接交还：保持到 7320，再 400 ms 回 0（刀跟着抽出来）
                    out.add(k(MercyTimeline.THIRD_MS, SNAP, closeup));
                    out.add(k(MercyTimeline.THIRD_MS + 400, MercyTimeline.THIRD_MS, SINE, zero()));
                } else {
                    // 切第三人称那一刻刀已整根没入，保持到抽刀
                    out.add(k(MercyTimeline.THIRD_MS, SNAP, PLUNGED));
                    out.addAll(Arrays.asList(SUCCESS_TAIL));
                }
            }
            case FAIL -> {
                int stabK = stabs[stabs.length - 1];
                int g = MercyTimeline.failFillMs(p);
                for (Key key : table) {
                    if (key.t <= stabK) {
                        out.add(key);
                    }
                }
                // 判错那一刀保持到 g + 700，之后被推开、踉跄，交还前 200 ms 回 0
                out.add(k(g + 1000, g + MercyTimeline.FAIL_BREAKFREE, SINE, PUSHED.clone()));
                out.add(k(handback, handback - 200, SINE, zero()));
            }
            case SINGLE -> {
                Key hardest = null;
                Key recover = table[table.length - 1];
                for (Key key : table) {
                    if (key.t <= 650) {
                        out.add(key);
                    }
                    if (key.t == 2670) {
                        hardest = key;
                    }
                }
                float[] hit = hardest != null ? hardest.v.clone() : recover.v.clone();
                out.add(k(MercyTimeline.SINGLE_STAB_MS, OUT, hit));
                // 跟着倒下的玄骸往前压
                float[] follow = hit.clone();
                follow[LEAN] = 25.0F;
                out.add(k(1430, SINE, follow));
                out.add(k(2300, 1800, SINE, recover.v.clone()));
                out.add(k(MercyTimeline.SINGLE_END_MS, 2300, SINE, zero()));
            }
            case THRALL -> out.addAll(Arrays.asList(table));
        }
        return new Track(out.toArray(new Key[0]), stabs, false);
    }

    /** 某一刻的姿态值，写进 {@code dst}（长度 {@value #N}） */
    private static void sample(Key[] keys, long t, float[] dst) {
        if (t <= keys[0].t) {
            System.arraycopy(keys[0].v, 0, dst, 0, N);
            return;
        }
        for (int i = 1; i < keys.length; i++) {
            Key cur = keys[i];
            if (t > cur.t) {
                continue;
            }
            Key prev = keys[i - 1];
            float u;
            if (cur.mode == SNAP) {
                u = t >= cur.t ? 1.0F : 0.0F;
            } else {
                int s = cur.start >= 0 ? cur.start : (cur.mode == OUT ? Math.max(prev.t, cur.t - OUT_MS) : prev.t);
                if (t <= s) {
                    u = 0.0F;
                } else {
                    float x = Mth.clamp((t - s) / (float) Math.max(1, cur.t - s), 0.0F, 1.0F);
                    u = cur.mode == OUT ? 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x)
                            : (float) (0.5D - 0.5D * Math.cos(Math.PI * x));
                }
            }
            for (int j = 0; j < N; j++) {
                dst[j] = prev.v[j] + (cur.v[j] - prev.v[j]) * u;
            }
            return;
        }
        System.arraycopy(keys[keys.length - 1].v, 0, dst, 0, N);
    }

    // ==================== 第三人称 ====================

    private static final float[] SCRATCH = new float[N];

    /** 处决期间手里原来拿的东西不画（刀模替它） */
    public static boolean hidesHeldItems(LivingEntity entity) {
        if (!(entity instanceof Player)) {
            return false;
        }
        MercyClient.Exec e = MercyClient.byExecutor(entity.getId());
        return e != null && e.elapsed() < e.handbackMs;
    }

    /** {@code HumanoidModel#setupAnim} 末尾调：实体是正在处决的玩家就覆盖各部件 */
    public static void apply(HumanoidModel<?> model, LivingEntity entity) {
        if (firstPersonPass || !(entity instanceof Player)) {
            return;
        }
        MercyClient.Exec e = MercyClient.byExecutor(entity.getId());
        if (e == null) {
            return;
        }
        long t = e.elapsed();
        float[] v = SCRATCH;
        sample(e.pose.keys, t, v);
        float jitter = 0.0F;
        if (e.pose.thrall) {
            int stab = e.pose.stabs[0];
            if (t >= stab && t < stab + 300) {
                // 拧刀：每 80 ms 抖一下
                jitter = ((t - stab) / 80) % 2 == 0 ? 1.5F : -1.5F;
            }
        }

        // 身体：扭、前倾，前倾和蹲按原版下蹲那组偏移挪位置
        float twist = v[TWIST] * DEG;
        model.body.yRot = twist;
        model.body.xRot = v[LEAN] * DEG;
        float crouch = Mth.clamp(v[LEAN] / 28.6F, 0.0F, 1.0F) + Mth.clamp(v[SQUAT], 0.0F, 1.0F) * 0.6F;
        model.body.y = 3.2F * crouch;
        model.head.y = 4.2F * crouch;
        model.rightArm.y = 2.0F + 3.2F * crouch;
        model.leftArm.y = 2.0F + 3.2F * crouch;
        model.rightLeg.z = 4.0F * crouch;
        model.leftLeg.z = 4.0F * crouch;
        model.rightLeg.y = 12.0F + 0.2F * crouch;
        model.leftLeg.y = 12.0F + 0.2F * crouch;

        // 两臂跟着扭挪肩膀（照原版挥砍那几行）
        float sin = Mth.sin(twist);
        float cos = Mth.cos(twist);
        model.rightArm.z = sin * 5.0F;
        model.rightArm.x = -cos * 5.0F;
        model.leftArm.z = -sin * 5.0F;
        model.leftArm.x = cos * 5.0F;

        model.rightArm.xRot = -(v[R_RAISE] + jitter) * DEG;
        model.rightArm.zRot = v[R_ABD] * DEG;
        model.rightArm.yRot = -v[R_IN] * DEG + twist;
        model.leftArm.xRot = -v[L_RAISE] * DEG;
        model.leftArm.zRot = v[L_ADD] * DEG;
        model.leftArm.yRot = twist;

        // 蹲：两腿前后叉开一点，看起来重心压低
        float squat = Mth.clamp(v[SQUAT], 0.0F, 1.0F);
        model.rightLeg.xRot = -(v[R_LEG] + 18.0F * squat) * DEG;
        model.leftLeg.xRot = -(v[L_LEG] - 18.0F * squat) * DEG;
        model.rightLeg.zRot = 0.005F;
        model.leftLeg.zRot = -0.005F;

        // 头看向目标胸口
        Entity target = e.target();
        double ax = target != null ? target.getX() : e.pl.x;
        double ay = target != null ? target.getY() + target.getBbHeight() * 0.62D : e.pl.y + 1.45D;
        double az = target != null ? target.getZ() : e.pl.z;
        double dx = ax - entity.getX();
        double dy = ay - (entity.getY() + entity.getEyeHeight());
        double dz = az - entity.getZ();
        float lookYaw = (float) (Mth.atan2(-dx, dz) * RAD_TO_DEG);
        float lookPitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * RAD_TO_DEG);
        float yaw = Mth.wrapDegrees(lookYaw - entity.yBodyRot);
        model.head.yRot = Mth.clamp(yaw, -75.0F, 75.0F) * DEG;
        model.head.xRot = Mth.clamp(lookPitch, -60.0F, 60.0F) * DEG;
        model.head.zRot = 0.0F;
        model.hat.copyFrom(model.head);
    }

    // ==================== 第一人称 ====================

    /** 第一人称一条胳膊此刻的拳头位置（视角空间：x 右、y 上、z 朝前，格）和刃尖额外下俯的角度 */
    static final class Arm {
        double x, y, z;
        float pitchDown;
    }

    // 拳头的几个位置
    private static final Vec3 R_START = new Vec3(0.62D, -0.95D, 0.40D);
    private static final Vec3 R_GRAB = new Vec3(0.36D, -0.44D, 0.62D);
    private static final Vec3 R_PRE = new Vec3(0.40D, -0.40D, 0.50D);
    private static final Vec3 R_STAB = new Vec3(0.14D, -0.42D, 0.98D);
    private static final Vec3 R_PULL = new Vec3(0.36D, -0.48D, 0.60D);
    private static final Vec3 L_START = new Vec3(-0.62D, -0.95D, 0.40D);
    private static final Vec3 L_GRAB = new Vec3(-0.16D, -0.26D, 0.86D);
    private static final Vec3 L_PUSH = new Vec3(-0.14D, -0.24D, 1.16D);

    /** 抓住的时刻（奴仆三套各自的，玄骸 400 ms 起手就是抓） */
    private static int grabMs(MercyVariant v) {
        return v == MercyVariant.THRALL_1 ? 350 : 400;
    }

    /** 推开的时刻（玄骸起手阶段没有） */
    private static int pushMs(MercyVariant v) {
        return switch (v) {
            case THRALL_1 -> 1350;
            case THRALL_2 -> 1400;
            case THRALL_3 -> 1450;
            default -> Integer.MAX_VALUE;
        };
    }

    /** 第一人称：算右臂（{@code right}）或左臂此刻的位置 */
    static void firstPerson(MercyClient.Exec e, long t, boolean right, Arm out) {
        MercyVariant v = e.variant;
        int grab = grabMs(v);
        int stab = v.thrall ? v.stabMs(0) : Integer.MAX_VALUE;
        int push = pushMs(v);
        int exitEnd = e.handbackMs - 200;
        int exitStart = exitEnd - 400;
        Vec3 p;
        float pitch = 0.0F;
        if (right) {
            if (t < grab) {
                p = lerp(R_START, R_GRAB, outCubic(t / (float) grab));
            } else if (t < stab - 220) {
                p = R_GRAB;
            } else if (t < stab - 120) {
                p = lerp(R_GRAB, R_PRE, sine((t - (stab - 220)) / 100.0F));
            } else if (t < stab) {
                float u = outCubic((t - (stab - 120)) / 120.0F);
                p = lerp(R_PRE, R_STAB, u);
                pitch = 8.0F * u;
            } else if (t < push) {
                p = R_STAB;
                pitch = 8.0F;
                if (t < stab + 300) {
                    // 拧刀
                    double j = ((t - stab) / 80) % 2 == 0 ? 0.01D : -0.01D;
                    p = p.add(j, -j * 0.5D, 0.0D);
                }
            } else {
                float u = outCubic(Math.min(1.0F, (t - push) / 150.0F));
                p = lerp(R_STAB, R_PULL, u);
                pitch = 8.0F * (1.0F - u);
            }
        } else {
            if (t < grab) {
                p = lerp(L_START, L_GRAB, outCubic(t / (float) grab));
            } else if (t < push) {
                p = L_GRAB;
            } else {
                p = lerp(L_GRAB, L_PUSH, outCubic(Math.min(1.0F, (t - push) / 150.0F)));
            }
        }
        if (t > exitStart) {
            // 交还前往下退出画面
            p = p.add(0.0D, -0.6D * sine(Math.min(1.0F, (t - exitStart) / (float) (exitEnd - exitStart))), 0.0D);
        }
        out.x = p.x;
        out.y = p.y;
        out.z = p.z;
        out.pitchDown = pitch;
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float u) {
        return a.lerp(b, u);
    }

    private static float outCubic(float u) {
        float x = Mth.clamp(u, 0.0F, 1.0F);
        float k = 1.0F - x;
        return 1.0F - k * k * k;
    }

    private static float sine(float u) {
        float x = Mth.clamp(u, 0.0F, 1.0F);
        return (float) (0.5D - 0.5D * Math.cos(Math.PI * x));
    }
}
