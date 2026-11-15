package pers.roinflam.kuvalich.client.mercy;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyCurve;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 处决者的姿态：第三人称（自己按 F5、旁观者、运镜里）按关键帧覆盖人形模型各部件的角度，第一人称算两条胳膊的位置
 *
 * <p><b>每一刀的节奏</b>：蓄力（右肩往后拉、刀收到腰侧）→ 出刀（扭身送肩、上身前压、往前送一步）→ 刺中那一下的顿挫
 * → 拧刀 → 拔出来、顺势收回去蓄下一刀。左手全程抓 / 按住目标，出刀时往回拽，把它往刀上送。三刀一刀比一刀狠：
 * 第一刀短促，第二刀多上半步，第三刀整个人压上去（正面那套是举过头往下扎，背后那套是从身侧抡满再捅）。
 * 关键帧只管大动作，刺中的顿挫、拧刀时手上的抖、喘气是叠在上面算的（见 {@link #addLife}），
 * 所以停着的时候人也是活的。</p>
 *
 * <p><b>角度的约定</b>（表里都是度，语义值）：「扭」正值 = 右肩往前送、左肩往后带。原版挥砍刚开始时 {@code body.yRot}
 * 为正、右肩先往后拉（{@code rightArm.z = sin(body.yRot) × 5}，往后是 +z），所以模型里 {@code body.yRot = −扭}。
 * 「前倾」正值往前俯（和原版下蹲同号）；「蹲」0~1 在此之外再叠一层原版下蹲的偏移；「送」= 上身和胯往目标那边挪几个像素，
 * 落在后面的那条腿跟着斜过去、脚不动。手臂：「抬」= 从下垂往身前抬（90 水平前伸、180 举过头）；「偏」正值往身体中线偏、
 * 负值往外；「拧」= 绕手臂自己转（拧刀就是它）；「收」= 右肩往后收几个像素（胳膊不会弯，直刺的蓄力和拔刀
 * 靠整条胳膊往后缩、出刀时往前送）。「瞄」0~1：右臂往刀口那一点指的程度（刺中时 1，刀尖正对胸口 / 后背）；
 * 「抓」0~1：左手往抓握点指的程度（抓着时 1，身子怎么扭左手都还按在原处）。「够」= 两个点再往前挪几个像素
 * （目标被捅得退了半步，手跟过去）。「腿」正值往前摆。「点头」正值低头、「歪头」正值往右歪，叠在看向目标之上。</p>
 *
 * <p>抓握点、刀口按套路各一组，写在模型坐标里（像素，原点在脖子根，x 往左、y 往下、z 往后，和人形模型一样），
 * 是按目标倒地 / 被抓时的样子量的。头一律看向目标胸口。关键帧之间默认 {@code easeInOutSine}；出刀的帧用
 * {@code easeOutCubic}、从上一帧起 110 ms 内到位；拔刀用 {@code easeInQuad}（越拔越快）；抓上去那一下用 {@code easeOutBack}
 * 冲过一点再回来；切镜时直接摆好的帧不插值。手臂按「先定指向、再绕自己转」算成四元数，再拆回模型要的 ZYX 欧拉角。
 * 上面是旧写法。正面直刺已经换成新写法（{@link MercyCurve}）：帧间速度连续、出刀有预备、拔刀有过冲、各部位错峰起步、
 * 迈步单独一层、头有滞后、首尾和原版姿态混合；别的套路铺过来之前照旧，逐帧不变。</p>
 *
 * <p><b>整身轨道</b>：上面这些只动身上的部件，人钉在站位上，再怎么摆也是站桩。每一套另有一条整身轨道管整个人在哪、
 * 朝哪、翻到什么角度：「绕」= 绕目标转了多少度（0 是开始的站位，180 是目标身后）、「距」= 比站位离目标远多少格、
 * 「高」= 离地多少格、「转」= 在面向目标之上再转多少度、「翻」正值往前翻、「倾」往右倒。算出来是世界坐标，
 * 由 {@link MercyBodyRender} 在画玩家之前挪模型，所以每个客户端看到的都一样，和服务端把人放在哪无关。
 * 玄骸的几套人其实一直在站位上（切特写时轨道回到 0），结尾要和真实位置对上：判错被甩出去、蹬开空翻落地的地方都是
 * 服务端推到的位置；奴仆落到身后的两套本地玩家真的沿路线走（见 {@code MercyClient#roam}）。</p>
 *
 * <p>注在 {@code HumanoidModel#setupAnim} 的末尾（见 {@code MixinHumanoidModelMercy}），玩家模型随后自己把袖子、
 * 裤腿、外套复制过去，皮肤外层跟着动；帽子层原版在 setupAnim 里已经复制过，这里改完头再复制一次。
 * 原版每帧不复位身子和头的 z、头的 zRot，这里挪过的模型记下来，下一个不在处决里的玩家用到它时先放回 0
 * （同一种皮肤的玩家共用一个模型）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MercyPose {

    // 一行关键帧里各值的下标
    private static final int TWIST = 0, LEAN = 1, SQUAT = 2, SHIFT = 3,
            R_RAISE = 4, R_YAW = 5, R_ROLL = 6, R_AIM = 7,
            L_RAISE = 8, L_YAW = 9, L_ROLL = 10, L_GRIP = 11, REACH = 12,
            R_LEG = 13, L_LEG = 14, NOD = 15, TILT = 16, R_PULL = 17, N = 18;
    // 第一人称一行：拳头位置（视角空间：x 右、y 上、z 朝前，格）、刃尖额外下俯、绕前臂拧
    private static final int FX = 0, FY = 1, FZ = 2, FPITCH = 3, FROLL = 4, FN = 5;
    // 整身一行：绕、距、高、转、翻、倾、藏（大于 0.5 时整个人不画）
    private static final int B_ANG = 0, B_DIST = 1, B_UP = 2, B_YAW = 3, B_FLIP = 4, B_ROLL = 5, B_HIDE = 6, BN = 7;
    /** 整身偏移在开头和结尾怎么和真实位置接上：这么多毫秒内渐变 */
    private static final int BODY_BLEND_MS = 200;

    private static final int SINE = MercyCurve.SINE, OUT = MercyCurve.OUT, SNAP = MercyCurve.SNAP, IN = MercyCurve.IN,
            BACK = MercyCurve.BACK;
    // 新写法（正面直刺）才用的几种，见 MercyCurve
    private static final int CURVE = MercyCurve.CURVE, ANTIC = MercyCurve.ANTIC, OVER = MercyCurve.OVER,
            DECEL = MercyCurve.DECEL;
    private static final int OUT_MS = MercyCurve.OUT_MS;
    /**
     * 新写法各通道的起步延迟（毫秒，负的是提前）：胯和扭身比手臂早 45、躯干早 25、头早 75，手腕的拧晚 25。
     * 以手臂为 0，刀落下去的那一刻还是关键帧写的时刻；腿另由迈步那层管，也按 0
     */
    private static final int[] STAGGER = new int[N];

    static {
        STAGGER[TWIST] = -45;
        STAGGER[SQUAT] = -45;
        STAGGER[SHIFT] = -45;
        STAGGER[LEAN] = -25;
        STAGGER[R_ROLL] = 25;
        STAGGER[L_ROLL] = 25;
        STAGGER[NOD] = -75;
        STAGGER[TILT] = -75;
    }

    /** 新写法交还前这么久把姿态的权重淡到 0，和原版的站立 / 走路姿态混；开头同理从原版淡入 */
    private static final int BLEND_OUT_MS = 250, BLEND_IN_MS = 150;
    /** 头看向目标的一阶滞后 */
    private static final double HEAD_LAG_MS = 80.0D;
    private static final float NA = Float.NaN;
    private static final float DEG = (float) Math.PI / 180.0F;
    private static final double RAD_TO_DEG = 180.0D / Math.PI;

    /** 第一人称画胳膊的那一遍：模型由原版的 renderHand 自己摆，这里不要再改 */
    static boolean firstPersonPass;

    /** 一个关键帧：{@code start} 小于 0 时按默认（出刀帧 t − 110，其余从上一帧起） */
    private record Key(int t, int start, int mode, float[] v) {
    }

    /** 抓握点和刀口（模型坐标，像素） */
    private record Anchors(float gx, float gy, float gz, float sx, float sy, float sz) {
    }

    /** 一场处决的整条姿态轨道（开始时按计划位拼好） */
    static final class Track {
        private final Key[] keys;
        private final Key[] fpRight;
        private final Key[] fpLeft;
        /** 整身轨道 */
        private final Key[] body;
        private final int[] stabs;
        /** 每一刀顿挫的轻重：出结果的那一刀重 */
        private final float[] weight;
        private final Anchors anchors;
        /** 这一段时间里改用另一套抓握点和刀口（踏背下刺往脚下扎的那一刀）；没有为 null */
        private Anchors alt;
        private int altFrom, altTo;
        /** 过了 altTo 以后再换的一套（踏背下刺闪到正面以后照正面直刺）；没有为 null，回到 anchors */
        private Anchors tail;
        /** 判错那一刀被顶住、手越抖越厉害的时段；没有为 −1 */
        private final int strainFrom, strainTo;
        /** 过了这一刻不再覆盖（交还后让原版的走路摆臂接手） */
        private final int endT;
        /** 按新写法排的（现在是正面直刺、背后锁颈、贴身上挑）：曲线、错峰、迈步、头的滞后、和原版混合都只对它生效，别的套路逐帧照旧 */
        private boolean v2;
        private MercyCurve.Keys curve, bodyCurve;
        /** 迈步那层：叠在两条腿上的摆角；没有为 null */
        @Nullable
        private MercyCurve.Keys feet;
        /** 这一场抖动的幅度和相位（按场次种子） */
        private float shakeAmp = 1.0F, phaseA, phaseB;
        private long headT = Long.MIN_VALUE;
        private float headYaw, headPitch;

        private Track(Key[] keys, Key[] fpRight, Key[] fpLeft, Key[] body, int[] stabs, float[] weight, Anchors anchors,
                      int strainFrom, int strainTo) {
            this.keys = keys;
            this.fpRight = fpRight;
            this.fpLeft = fpLeft;
            this.body = body;
            this.stabs = stabs;
            this.weight = weight;
            this.anchors = anchors;
            this.strainFrom = strainFrom;
            this.strainTo = strainTo;
            this.endT = Math.max(keys[keys.length - 1].t, fpRight[fpRight.length - 1].t);
        }

        private Anchors anchorsAt(long t) {
            if (alt != null && t >= altFrom) {
                return t < altTo ? alt : tail != null ? tail : anchors;
            }
            return anchors;
        }
    }

    private MercyPose() {
    }

    // ==================== 关键帧的写法 ====================

    /** 一行姿态，没写的通道沿用上一行 */
    private static final class Row {
        private final float[] v = new float[N];

        private Row() {
            Arrays.fill(v, NA);
        }

        Row tw(float twist) {
            v[TWIST] = twist;
            return this;
        }

        Row ln(float lean) {
            v[LEAN] = lean;
            return this;
        }

        Row sq(float squat) {
            v[SQUAT] = squat;
            return this;
        }

        Row sh(float shift) {
            v[SHIFT] = shift;
            return this;
        }

        /** 右臂：抬、偏、拧 */
        Row r(float raise, float yaw, float roll) {
            v[R_RAISE] = raise;
            v[R_YAW] = yaw;
            v[R_ROLL] = roll;
            return this;
        }

        Row aim(float aim) {
            v[R_AIM] = aim;
            return this;
        }

        Row pull(float pull) {
            v[R_PULL] = pull;
            return this;
        }

        /** 左臂：抬、偏、拧 */
        Row l(float raise, float yaw, float roll) {
            v[L_RAISE] = raise;
            v[L_YAW] = yaw;
            v[L_ROLL] = roll;
            return this;
        }

        Row grip(float grip) {
            v[L_GRIP] = grip;
            return this;
        }

        Row reach(float reach) {
            v[REACH] = reach;
            return this;
        }

        Row legs(float right, float left) {
            v[R_LEG] = right;
            v[L_LEG] = left;
            return this;
        }

        Row hd(float nod, float tilt) {
            v[NOD] = nod;
            v[TILT] = tilt;
            return this;
        }
    }

    private static Row p() {
        return new Row();
    }

    /** 整身一行，没写的通道沿用上一行 */
    private static final class Move {
        private final float[] v = new float[BN];

        private Move() {
            Arrays.fill(v, NA);
        }

        Move ang(float deg) {
            v[B_ANG] = deg;
            return this;
        }

        Move dist(float blocks) {
            v[B_DIST] = blocks;
            return this;
        }

        Move up(float blocks) {
            v[B_UP] = blocks;
            return this;
        }

        Move yaw(float deg) {
            v[B_YAW] = deg;
            return this;
        }

        Move flip(float deg) {
            v[B_FLIP] = deg;
            return this;
        }

        Move roll(float deg) {
            v[B_ROLL] = deg;
            return this;
        }

        Move hide(boolean hidden) {
            v[B_HIDE] = hidden ? 1.0F : 0.0F;
            return this;
        }
    }

    private static Move m() {
        return new Move();
    }

    /** 站在站位上 */
    private static Move still() {
        return m().ang(0).dist(0).up(0).yaw(0).flip(0).roll(0).hide(false);
    }

    private static Key k(int t, int mode, Move move) {
        return new Key(t, -1, mode, move.v.clone());
    }

    private static Key k(int t, int start, int mode, Move move) {
        return new Key(t, start, mode, move.v.clone());
    }

    private static Key k(int t, int mode, Row row) {
        return new Key(t, -1, mode, row.v.clone());
    }

    private static Key k(int t, int start, int mode, Row row) {
        return new Key(t, start, mode, row.v.clone());
    }

    /** 同一行挪到另一个时刻 */
    private static Key at(Key key, int t, int mode) {
        return new Key(t, -1, mode, key.v.clone());
    }

    /** 第一人称一行 */
    private static Key f(int t, int mode, float x, float y, float z, float pitch, float roll) {
        return new Key(t, -1, mode, new float[]{x, y, z, pitch, roll});
    }

    /** 处决开始那一刻的站姿 */
    private static Row start() {
        return p().tw(0).ln(4).sq(0).sh(0).r(18, -35, 0).aim(0).pull(0).l(22, -30, 0).grip(0).reach(0)
                .legs(0, 0).hd(0, 0);
    }

    private static Row zero() {
        return p().tw(0).ln(0).sq(0).sh(0).r(0, 0, 0).aim(0).pull(0).l(0, 0, 0).grip(0).reach(0).legs(0, 0)
                .hd(0, 0);
    }

    /** 抓上去之前先往下一沉、收一下手（起手的预备） */
    private static Row gather() {
        return p().tw(-5).ln(9).sq(0.3F).sh(-0.5F).r(24, -35, 0).l(42, 10, 0).legs(-4, 6).hd(4, 0);
    }

    /** 收刀以后甩一下 */
    private static Row flick() {
        return p().tw(-16).ln(9).sq(0).sh(0.5F).r(22, -60, 30).aim(0).l(26, -10, 0).grip(0).legs(-10, 10).hd(2, 0);
    }

    /** 站定、刀还在手上，喘着气 */
    private static Row ready() {
        return p().tw(-4).ln(7).sq(0).sh(0).r(28, -32, 10).l(22, -15, 0).legs(-8, 8).hd(0, 0);
    }

    // ==================== 玄骸三套 ====================

    private static final Anchors ANCHOR_A = new Anchors(4, -2, -10, 0, 1, -13);
    private static final Anchors ANCHOR_B = new Anchors(2, -5, -11, 0, 4, -12);
    private static final Anchors ANCHOR_C = new Anchors(1, -1, -9, 0, -3, -9);
    /** 踏背下刺蹲在背上那一段：刀往脚下的后颈扎，左手揪住肩甲 */
    private static final Anchors ANCHOR_E = new Anchors(3, 8, -9, 0, 22, -9);

    /** 蹲着落地、一只手撑开（翻滚和空翻的收势） */
    private static Row landed() {
        return p().tw(-6).ln(28).sq(1).sh(0).r(40, -38, 8).aim(0).pull(0).l(30, -30, 0).grip(0).legs(-24, 32).hd(4, 0);
    }

    /** 抱成一团 */
    private static Row tucked() {
        return p().tw(0).ln(36).sq(1).sh(0).r(72, -10, 0).aim(0).pull(0).l(72, -10, 0).grip(0).legs(72, 84).hd(10, 0);
    }

    /** 蹬开目标：先屈腿，再两脚蹬出去、上身往后仰，翻一圈落地 */
    private static List<Key> kickOff(int s) {
        return List.of(
                k(s + 170, SINE, p().tw(10).ln(22).sq(0.7F).sh(1).r(70, 6, 0).aim(0.3F).pull(0).l(60, 10, 0).grip(0)
                        .legs(34, 58).hd(0, 0)),
                k(s + 260, OUT, p().tw(0).ln(-26).sq(0).sh(0).r(125, -18, 0).aim(0).l(125, -18, 0).legs(70, 78)
                        .hd(-14, 0)),
                k(s + 520, SINE, tucked()),
                k(s + 700, SINE, landed()),
                k(s + 980, OUT, flick()),
                k(s + 1250, SINE, ready()));
    }

    /**
     * 正面直刺按新写法排：姿势和旧表差不多，节奏重排。第一刀快而狠，只蓄 90 ms；第二刀迈一步、拧得久；第三刀
     * 举过头蓄 280 ms 再扎。出刀先往回收一点再甩出去，拔刀越拔越快、顺势带过头再落回下一刀的蓄力位
     */
    private static List<Key> lichA2(MercyBeats.Lich b) {
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        List<Key> t = new ArrayList<>(List.of(
                k(0, CURVE, start()),
                k(130, CURVE, gather()),
                k(310, OVER, p().tw(-10).ln(15).sq(0.1F).sh(1.5F).r(30, -30, 0).l(110, 20, 0).grip(1)
                        .legs(-16, 18).hd(0, 0)),
                k(420, CURVE, p().tw(-8).ln(11).sq(0.05F).sh(0.8F).r(36, -28, 0).hd(-2, 0)),
                k(s1 - 220, CURVE, p().tw(-24).ln(9).sq(0.15F).sh(-0.3F).r(80, -6, -15).aim(0.7F).pull(3.5F).hd(0, -3)),
                k(s1 - 90, CURVE, p().tw(-30).ln(7).sq(0.2F).sh(-0.6F).r(80, -8, -20).pull(4.5F).legs(-18, 20)),
                k(s1, ANTIC, p().tw(16).ln(17).sq(0.05F).sh(2.5F).r(85, 5, 0).aim(1).pull(-1.5F).legs(-20, 20).hd(3, 0)),
                k(s1 + 80, CURVE, p().tw(20).ln(19).sh(3).hd(5, 2)),
                k(s1 + 240, CURVE, p().tw(22).ln(18).sq(0.15F).r(NA, NA, 40).hd(4, 4)),
                k(s1 + 380, CURVE, p().tw(20).ln(17).sq(0.1F).r(NA, NA, 32)),
                k(s1 + 520, IN, p().tw(-16).ln(10).sq(0.1F).sh(0.6F).r(80, -8, -12).aim(0.6F).pull(3.4F).hd(0, -2)),
                k(s2 - 130, OVER, p().tw(-30).ln(7).sq(0.2F).sh(-0.3F).r(80, -10, -22).aim(0.7F).pull(4.5F)
                        .legs(-20, 22)),
                // 第二刀：迈步那层另算（见 feet），这里只管上身
                k(s2, ANTIC, p().tw(20).ln(21).sq(0.05F).sh(3.2F).r(88, 6, 0).aim(1).pull(-2).reach(2).legs(-22, 24)
                        .hd(4, 0)),
                k(s2 + 80, CURVE, p().tw(24).ln(23).sh(3.6F).hd(6, -2)),
                k(s2 + 240, CURVE, p().tw(26).ln(22).sq(0.15F).r(NA, NA, -40).hd(5, -4)),
                k(s2 + 400, CURVE, p().tw(28).ln(23).sq(0.2F).r(NA, NA, -48).hd(6, -5)),
                k(s2 + 500, CURVE, p().tw(25).ln(21).r(NA, NA, -36)),
                k(s2 + 580, IN, p().tw(-14).ln(8).sq(0.15F).sh(0.5F).r(100, -18, 0).aim(0.3F).pull(3).reach(4).hd(-2, 0)),
                // 第三刀：甩上头顶，顶在那儿再往后拧紧一点，猛扎下去。第二、三刀间隔改长以后甩上去和顶住分成两帧，
                // 不然一个 OVER 拖 400 多毫秒，甩的那一下就软了
                k(s3 - 420, OVER, p().tw(-28).ln(2).sq(0.25F).sh(-0.8F).r(148, -11, 0).aim(0).pull(1).hd(-5, 0)),
                k(s3 - 280, CURVE, p().tw(-33).ln(1).sq(0.32F).sh(-1.1F).r(155, -12, 0).aim(0).pull(1.6F).hd(-6, 0)),
                k(s3, ANTIC, p().tw(26).ln(30).sq(0.15F).sh(4).r(80, 8, 0).aim(1).pull(-1).reach(7).legs(-26, 28)
                        .hd(8, 0)),
                k(s3 + 90, CURVE, p().tw(30).ln(33).sq(0.3F).sh(4.5F).hd(10, 0))));
        t.add(k(s3 + 170, CURVE, p().tw(10).ln(22).sq(0.7F).sh(1).r(70, 6, 0).aim(0.3F).pull(0).l(60, 10, 0).grip(0)
                .legs(34, 58).hd(0, 0)));
        t.add(k(s3 + 260, OVER, p().tw(0).ln(-26).sq(0).sh(0).r(125, -18, 0).aim(0).l(125, -18, 0).legs(70, 78)
                .hd(-14, 0)));
        t.add(k(s3 + 520, CURVE, tucked()));
        t.add(k(s3 + 700, IN, landed()));
        // 落地往下一沉卸力
        t.add(k(s3 + 790, CURVE, landed().ln(32).hd(7, 0)));
        t.add(k(s3 + 980, OUT, flick()));
        t.add(k(s3 + 1250, CURVE, ready()));
        return t;
    }

    /** 第二刀的迈步：前腿（左）在刺入前 160 ms 抬、前 20 ms 落，后腿蹬一下；只叠在腿上 */
    private static List<Key> stepA2(int s2) {
        return List.of(
                k(0, CURVE, p().legs(0, 0)),
                k(s2 - 160, CURVE, p().legs(0, 0)),
                k(s2 - 90, CURVE, p().legs(-6, 18)),
                k(s2 - 20, IN, p().legs(0, 0)),
                k(s2 + 200, CURVE, p().legs(0, 0)));
    }

    /** 背后锁颈：左臂从后面扣住它的脖子；第二刀把它的头往后扳着捅，拔刀后原地拧身转满一圈，借着转势捅第三刀 */
    private static List<Key> lichB(MercyBeats.Lich b) {
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        return new ArrayList<>(List.of(
                k(0, SINE, start()),
                k(130, SINE, gather()),
                k(310, BACK, p().tw(-8).ln(16).sq(0.1F).sh(1.5F).r(30, -30, 0).l(118, 15, 0).grip(1)
                        .legs(-14, 16).hd(2, 0)),
                // 往后一勒
                k(420, SINE, p().tw(-6).ln(12).sq(0.05F).sh(0.6F).hd(0, 0)),
                k(s1 - 200, SINE, p().tw(-24).ln(11).sq(0.15F).sh(-0.3F).r(78, -10, -20).aim(0.6F).pull(3.5F).hd(0, 3)),
                k(s1 - 115, SINE, p().tw(-28).ln(9).sq(0.2F).sh(-0.5F).r(78, -12, -25).pull(4.5F)),
                k(s1, OUT, p().tw(14).ln(18).sq(0.05F).sh(2).r(82, 4, 0).aim(1).pull(-1.5F).legs(-18, 18).hd(4, 0)),
                k(s1 + 80, SINE, p().tw(18).ln(20).sh(2.6F).hd(6, 0)),
                k(s1 + 300, SINE, p().tw(20).ln(19).sq(0.15F).r(NA, NA, 40).hd(4, 3)),
                k(s1 + 460, SINE, p().tw(18).ln(18).sq(0.1F).r(NA, NA, 32)),
                k(s1 + 620, IN, p().tw(-22).ln(9).sq(0.1F).sh(0.3F).r(78, -12, -15).aim(0.5F).pull(4).hd(0, 0)),
                // 左手把它的头往后扳
                k(s2 - 115, SINE, p().tw(-30).ln(7).sq(0.2F).sh(-0.3F).r(78, -14, -22).pull(4.5F).reach(-2).hd(-2, 0)),
                k(s2, OUT, p().tw(18).ln(20).sq(0.05F).sh(2.6F).r(86, 5, 0).aim(1).pull(-2).reach(-1).legs(-22, 22)
                        .hd(4, 0)),
                k(s2 + 80, SINE, p().tw(22).ln(22).sh(3).hd(6, -2)),
                k(s2 + 300, SINE, p().tw(24).ln(21).sq(0.15F).r(NA, NA, -40).hd(5, -3)),
                k(s2 + 460, SINE, p().tw(22).ln(20).r(NA, NA, -34)),
                k(s2 + 620, IN, p().tw(-26).ln(8).sq(0.15F).sh(0).r(55, -50, -10).aim(0).pull(2).reach(0).hd(0, 0)),
                // 最大的一次蓄力：整条胳膊甩到身侧后方
                k(s3 - 115, SINE, p().tw(-36).ln(10).sq(0.3F).sh(-1).r(50, -62, -25).pull(1).hd(-2, 0)),
                k(s3, OUT, p().tw(28).ln(32).sq(0.1F).sh(4.5F).r(90, 8, 0).aim(1).pull(-1.5F).reach(5).legs(-26, 30)
                        .hd(8, 0)),
                k(s3 + 90, SINE, p().tw(32).ln(33).sq(0.25F).sh(5).hd(10, 0)),
                // 玄骸往前扑倒：松手，跟着往前压
                k(s3 + 280, SINE, p().tw(20).ln(34).sq(0.35F).sh(5).r(60, 6, 0).aim(0).pull(0).l(60, 5, 0).grip(0)
                        .legs(-24, 30).hd(14, 0)),
                k(s3 + 600, SINE, p().tw(8).ln(16).sq(0.15F).sh(2).r(42, -10, 0).l(28, 0, 0).legs(-14, 14)
                        .hd(4, 0)),
                k(s3 + 900, OUT, flick()),
                k(s3 + 1250, SINE, ready())));
    }

    /**
     * 背后锁颈按新写法排，节奏「长—短」：第一刀捅进后腰，拔出来以后整个人往后坐、左臂把它的脖子往上勒，憋住 400 来毫秒；
     * 第二刀扳着它的头捅，拧得短、拔得快，拔刀时松开脖子，原地拧身转一圈，转回来左手按住它的肩膀借势捅第三刀。
     * 踏背下刺的第一刀还复用旧的 {@link #lichB}，那张表别动
     */
    private static List<Key> lichB2(MercyBeats.Lich b) {
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        List<Key> t = new ArrayList<>(List.of(
                k(0, CURVE, start()),
                k(130, CURVE, gather()),
                k(310, OVER, p().tw(-8).ln(16).sq(0.1F).sh(1.5F).r(30, -30, 0).l(118, 15, 0).grip(1)
                        .legs(-14, 16).hd(2, 0)),
                k(420, CURVE, p().tw(-6).ln(12).sq(0.05F).sh(0.6F).hd(0, 0)),
                k(s1 - 220, CURVE, p().tw(-22).ln(11).sq(0.15F).sh(-0.3F).r(78, -10, -20).aim(0.6F).pull(3.5F).hd(0, 3)),
                k(s1 - 90, CURVE, p().tw(-28).ln(9).sq(0.2F).sh(-0.5F).r(78, -12, -25).pull(4.5F).legs(-16, 18)),
                k(s1, ANTIC, p().tw(14).ln(18).sq(0.05F).sh(2).r(82, 4, 0).aim(1).pull(-1.5F).legs(-18, 18).hd(4, 0)),
                k(s1 + 80, CURVE, p().tw(18).ln(20).sh(2.6F).hd(6, 0)),
                k(s1 + 240, CURVE, p().tw(20).ln(19).sq(0.15F).r(NA, NA, 40).hd(4, 3)),
                k(s1 + 380, CURVE, p().tw(18).ln(18).sq(0.1F).r(NA, NA, 32)),
                k(s1 + 520, IN, p().tw(-16).ln(10).sq(0.1F).sh(0.3F).r(76, -14, -12).aim(0.5F).pull(3.6F).hd(0, 0)),
                // 勒：往后一坐，左臂往上往回收，刀横在它腰后不动
                k(s1 + 640, OVER, p().tw(-10).ln(-4).sq(0.3F).sh(-1.3F).r(72, -18, -14).aim(0.4F).pull(3).l(128, 12, 0)
                        .reach(-3).legs(-20, 22).hd(-6, 0)),
                k(s1 + 820, CURVE, p().tw(-12).ln(-6).sq(0.34F).sh(-1.6F).l(131, 12, 0).reach(-3.6F).hd(-7, 2)),
                k(s2 - 240, CURVE, p().tw(-15).ln(-5).sq(0.34F).sh(-1.6F).r(76, -14, -22).aim(0.6F).pull(4).l(132, 12, 0)
                        .reach(-4).hd(-6, 3)),
                k(s2 - 110, CURVE, p().tw(-30).ln(4).sq(0.25F).sh(-0.6F).r(78, -14, -25).aim(0.7F).pull(4.6F).reach(-3)
                        .hd(-2, 0)),
                k(s2, ANTIC, p().tw(18).ln(20).sq(0.05F).sh(2.2F).r(86, 5, 0).aim(1).pull(-2).l(124, 14, 0).reach(-2)
                        .legs(-20, 22).hd(4, 0)),
                k(s2 + 80, CURVE, p().tw(22).ln(22).sh(2.8F).hd(6, -2)),
                k(s2 + 200, CURVE, p().tw(25).ln(21).sq(0.15F).r(NA, NA, -44).hd(5, -3)),
                k(s2 + 300, CURVE, p().tw(23).ln(20).r(NA, NA, -36)),
                // 拔刀同时松开脖子，刀顺着拔出来的方向甩到身侧，带着整个人转起来
                k(s2 + 380, IN, p().tw(-20).ln(8).sq(0.15F).sh(0.3F).r(60, -46, -10).aim(0).pull(2.5F).l(70, -10, 0)
                        .grip(0).reach(0).hd(0, 0)),
                k(s3 - 200, OVER, p().tw(-36).ln(9).sq(0.3F).sh(-0.6F).r(52, -64, -25).pull(1).l(46, -20, 0).hd(-2, 0)),
                k(s3 - 60, CURVE, p().tw(-38).ln(12).sq(0.3F).sh(-0.4F).r(50, -66, -28).l(100, 10, 0).grip(1).hd(0, 0)),
                k(s3, ANTIC, p().tw(28).ln(32).sq(0.1F).sh(4.5F).r(90, 8, 0).aim(1).pull(-1.5F).reach(5).l(112, 10, 0)
                        .legs(-26, 30).hd(8, 0)),
                k(s3 + 90, CURVE, p().tw(32).ln(33).sq(0.25F).sh(5).hd(10, 0))));
        // 它往前扑倒：松手，跟着往前压
        t.add(k(s3 + 280, CURVE, p().tw(20).ln(34).sq(0.35F).sh(5).r(60, 6, 0).aim(0).pull(0).l(60, 5, 0).grip(0)
                .legs(-24, 30).hd(14, 0)));
        t.add(k(s3 + 600, CURVE, p().tw(8).ln(16).sq(0.15F).sh(2).r(42, -10, 0).l(28, 0, 0).legs(-14, 14).hd(4, 0)));
        t.add(k(s3 + 900, OUT, flick()));
        t.add(k(s3 + 1250, CURVE, ready()));
        return t;
    }

    /** 背后锁颈的步子：转圈时右腿离地带一下，转回来左脚往前落一步踩实再扎第三刀；只叠在腿上 */
    private static List<Key> stepB2(int s2, int s3) {
        return List.of(
                k(0, CURVE, p().legs(0, 0)),
                k(s2 + 420, CURVE, p().legs(0, 0)),
                k(s2 + 560, CURVE, p().legs(-16, 6)),
                k(s3 - 240, CURVE, p().legs(0, 0)),
                k(s3 - 160, CURVE, p().legs(0, 0)),
                k(s3 - 90, CURVE, p().legs(-6, 20)),
                k(s3 - 20, IN, p().legs(0, 0)),
                k(s3 + 200, CURVE, p().legs(0, 0)));
    }

    /**
     * 贴身上挑按新写法排，节奏「连珠」：三刀间隔都贴着下限。每刀蹲低、刀尖朝上蓄 90 ms，整个人往上弹着挑进去，拧两下，
     * 拔刀顺势落回低位接着蓄下一刀；第三刀前压到最低憋一下，整个人顶起来，借着它往上一窜蹬开后空翻
     */
    private static List<Key> lichC2(MercyBeats.Lich b) {
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        List<Key> t = new ArrayList<>(List.of(
                k(0, CURVE, start()),
                k(130, CURVE, p().tw(-4).ln(14).sq(0.5F).sh(-0.3F).r(22, -35, 0).l(40, 10, 0).legs(-8, 10).hd(2, 0)),
                // 冲进怀里，左掌根顶住胸口
                k(310, OVER, p().tw(-6).ln(27).sq(1).sh(2.4F).r(14, -30, 0).l(98, 15, 0).grip(1).legs(-18, 18).hd(-4, 0)),
                k(420, CURVE, p().ln(26).sh(1.8F).l(95, 15, 0).hd(-5, 0)),
                k(s1 - 200, CURVE, p().tw(-16).ln(30).sq(1).sh(1.2F).r(10, -34, -14).pull(1).hd(-6, 0)),
                k(s1 - 90, CURVE, p().tw(-22).ln(33).sq(1).sh(0.8F).r(2, -40, -22).pull(1.6F).legs(-20, 20).hd(-7, 0)),
                k(s1, ANTIC, p().tw(12).ln(14).sq(0.55F).sh(3).r(140, 6, 0).aim(1).pull(-1).legs(-20, 22).hd(-10, 0)),
                k(s1 + 80, CURVE, p().tw(15).ln(11).sq(0.45F).sh(3.5F).hd(-13, 0)),
                k(s1 + 200, CURVE, p().tw(16).ln(12).sq(0.5F).r(NA, NA, 40).hd(-11, 3)),
                k(s1 + 300, CURVE, p().tw(15).ln(13).sq(0.55F).r(NA, NA, 32)),
                // 拔出来顺势落回去，刀尖还朝上
                k(s1 + 380, IN, p().tw(-14).ln(27).sq(0.95F).sh(1.6F).r(14, -32, -10).aim(0).pull(0.8F).hd(-6, 0)),
                k(s2 - 200, CURVE, p().tw(-18).ln(31).sq(1).sh(1.2F).r(6, -36, -16).pull(1.2F)),
                k(s2 - 90, CURVE, p().tw(-24).ln(34).sq(1).sh(0.8F).r(0, -40, -22).pull(1.8F).hd(-7, 0)),
                k(s2, ANTIC, p().tw(16).ln(10).sq(0.45F).sh(3.6F).r(146, 6, 0).aim(1).pull(-1.2F).reach(1).legs(-22, 24)
                        .hd(-12, 0)),
                k(s2 + 80, CURVE, p().tw(19).ln(7).sq(0.4F).sh(4).hd(-15, 0)),
                k(s2 + 200, CURVE, p().tw(20).ln(9).sq(0.45F).r(NA, NA, -40).hd(-13, -3)),
                k(s2 + 300, CURVE, p().tw(19).ln(10).sq(0.5F).r(NA, NA, -34)),
                k(s2 + 380, IN, p().tw(-16).ln(29).sq(1).sh(1.4F).r(8, -34, -10).aim(0).pull(0.8F).reach(2).hd(-6, 0)),
                // 压到最低憋住，左手往下按着它的胸口借力
                k(s3 - 200, OVER, p().tw(-28).ln(37).sq(1).sh(0.4F).r(-8, -42, -24).pull(2.2F).l(84, 12, 0).hd(-9, 0)),
                k(s3 - 80, CURVE, p().tw(-27).ln(36).sq(1).sh(0.5F).r(-6, -41, -22).pull(2.4F).l(86, 12, 0).hd(-8, 0)),
                k(s3, ANTIC, p().tw(20).ln(2).sq(0.15F).sh(4).r(155, 6, 0).aim(1).pull(-1.5F).reach(3).l(108, 15, 0)
                        .legs(-26, 30).hd(-16, 0)),
                k(s3 + 90, CURVE, p().tw(23).ln(-3).sq(0).sh(4.6F).hd(-19, 0))));
        t.add(k(s3 + 170, CURVE, p().tw(10).ln(22).sq(0.7F).sh(1).r(70, 6, 0).aim(0.3F).pull(0).l(60, 10, 0).grip(0)
                .reach(0).legs(34, 58).hd(0, 0)));
        t.add(k(s3 + 260, OVER, p().tw(0).ln(-26).sq(0).sh(0).r(125, -18, 0).aim(0).l(125, -18, 0).legs(70, 78)
                .hd(-14, 0)));
        t.add(k(s3 + 520, CURVE, tucked()));
        t.add(k(s3 + 700, IN, landed()));
        t.add(k(s3 + 790, CURVE, landed().ln(32).hd(7, 0)));
        t.add(k(s3 + 980, OUT, flick()));
        t.add(k(s3 + 1250, CURVE, ready()));
        return t;
    }

    /** 贴身上挑、翻滚绕背的步子：第二、三刀各往前送一步（前腿刺入前 160 ms 抬、前 20 ms 落）；只叠在腿上 */
    private static List<Key> stepC2(int s2, int s3) {
        return List.of(
                k(0, CURVE, p().legs(0, 0)),
                k(s2 - 160, CURVE, p().legs(0, 0)),
                k(s2 - 90, CURVE, p().legs(-6, 18)),
                k(s2 - 20, IN, p().legs(0, 0)),
                k(s3 - 160, CURVE, p().legs(0, 0)),
                k(s3 - 90, CURVE, p().legs(-8, 22)),
                k(s3 - 20, IN, p().legs(0, 0)),
                k(s3 + 120, CURVE, p().legs(0, 0)));
    }

    /**
     * 翻滚绕背按新写法排，节奏「长—更长」：第一刀捅进去拧两下，拔出来把刀往外一甩，左手揪住它往回一拽，借势捅第二刀；
     * 第二刀拔刀往它左边一撕，压低一蹬，侧身扑出去贴着它翻滚半圈，单手撑地落在它身后，拧身起来左手按住它的肩捅第三刀
     */
    private static List<Key> lichD2(MercyBeats.Lich b) {
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        List<Key> t = new ArrayList<>(List.of(
                k(0, CURVE, start()),
                k(130, CURVE, gather()),
                k(310, OVER, p().tw(-10).ln(15).sq(0.1F).sh(1.5F).r(30, -30, 0).l(110, 20, 0).grip(1)
                        .legs(-16, 18).hd(0, 0)),
                k(420, CURVE, p().tw(-8).ln(11).sq(0.05F).sh(0.8F).r(36, -28, 0).hd(-2, 0)),
                k(s1 - 220, CURVE, p().tw(-24).ln(9).sq(0.15F).sh(-0.3F).r(80, -6, -15).aim(0.7F).pull(3.5F).hd(0, -3)),
                k(s1 - 90, CURVE, p().tw(-30).ln(7).sq(0.2F).sh(-0.6F).r(80, -8, -20).pull(4.5F).legs(-18, 20)),
                k(s1, ANTIC, p().tw(16).ln(17).sq(0.05F).sh(2.5F).r(85, 5, 0).aim(1).pull(-1.5F).legs(-20, 20).hd(3, 0)),
                k(s1 + 80, CURVE, p().tw(20).ln(19).sh(3).hd(5, 2)),
                k(s1 + 240, CURVE, p().tw(22).ln(18).sq(0.15F).r(NA, NA, 40).hd(4, 4)),
                k(s1 + 380, CURVE, p().tw(21).ln(18).sq(0.15F).r(NA, NA, 46).hd(4, 5)),
                // 拔出来往外一甩：刀甩到身侧，左手松开
                k(s1 + 520, IN, p().tw(-20).ln(9).sq(0.1F).sh(0.2F).r(62, -40, -10).aim(0.2F).pull(2).l(70, 0, 0).grip(0)
                        .hd(0, -2)),
                k(s1 + 660, OVER, p().tw(-34).ln(5).sq(0.2F).sh(-0.4F).r(40, -80, 22).aim(0).pull(0.5F).l(52, -6, 0)
                        .legs(-12, 14).hd(-2, 0)),
                // 揪住它往回一拽，顺手把刀收回来对着它
                k(s1 + 820, OVER, p().tw(-14).ln(12).sq(0.15F).sh(0.4F).r(66, -30, -12).aim(0.5F).pull(3).l(116, 18, 0)
                        .grip(1).reach(-2).hd(0, -2)),
                k(s2 - 130, OVER, p().tw(-30).ln(7).sq(0.2F).sh(-0.4F).r(80, -10, -22).aim(0.7F).pull(4.6F).l(112, 18, 0)
                        .reach(-1).legs(-20, 22)),
                k(s2, ANTIC, p().tw(20).ln(21).sq(0.05F).sh(3.2F).r(88, 6, 0).aim(1).pull(-2).reach(2).legs(-22, 24)
                        .hd(4, 0)),
                k(s2 + 80, CURVE, p().tw(24).ln(23).sh(3.6F).hd(6, -2)),
                k(s2 + 200, CURVE, p().tw(26).ln(22).sq(0.15F).r(NA, NA, -44).hd(5, -4)),
                k(s2 + 300, CURVE, p().tw(24).ln(21).r(NA, NA, -36)),
                // 往外一撕：刀顺着拔出来的方向甩到身后，左手在它肩上一撑
                k(s2 + 380, IN, p().tw(-30).ln(14).sq(0.3F).sh(0.4F).r(58, -70, -10).aim(0).pull(1.5F).l(96, -10, 0)
                        .grip(0).reach(0).legs(-14, 18).hd(0, 0)),
                // 压低，两臂往后一收，蹬出去时整个人抻直往前扑
                k(s2 + 460, OVER, p().tw(-16).ln(32).sq(0.9F).sh(0.6F).r(30, -56, 0).pull(0).l(40, 4, 0).legs(-30, 40)
                        .hd(6, 0)),
                k(s2 + 540, CURVE, p().tw(-4).ln(36).sq(0.4F).sh(1.5F).r(110, -20, 0).l(110, 10, 0).legs(10, -24)
                        .hd(-8, 0)),
                k(s2 + 620, CURVE, tucked()),
                k(s2 + 860, CURVE, tucked()),
                // 落地：一只手撑地、往下一沉，滑一下
                k(s3 - 360, IN, landed()),
                k(s3 - 280, CURVE, landed().ln(34).hd(8, 0)),
                // 拧身起来，左手按住它的肩，刀往后拉满
                k(s3 - 170, OVER, p().tw(-34).ln(14).sq(0.4F).sh(-0.6F).r(78, -14, -24).aim(0.7F).pull(4.6F).l(108, 18, 0)
                        .grip(0.8F).legs(-18, 22).hd(0, 0)),
                k(s3 - 70, CURVE, p().tw(-37).ln(12).sq(0.45F).sh(-0.8F).r(78, -16, -26).aim(0.75F).pull(5).l(112, 18, 0)
                        .grip(1).hd(-1, 0)),
                k(s3, ANTIC, p().tw(26).ln(28).sq(0.1F).sh(4.2F).r(88, 8, 0).aim(1).pull(-1.5F).reach(4).l(112, 14, 0)
                        .legs(-26, 30).hd(7, 0)),
                k(s3 + 90, CURVE, p().tw(30).ln(31).sq(0.25F).sh(4.6F).hd(9, 0))));
        // 它往前扑倒：松手，跟着往前压
        t.add(k(s3 + 300, CURVE, p().tw(18).ln(30).sq(0.3F).sh(3.5F).r(60, 6, 0).aim(0).pull(0).l(54, 5, 0).grip(0)
                .reach(0).legs(-22, 28).hd(12, 0)));
        t.add(k(s3 + 620, CURVE, p().tw(8).ln(14).sq(0.15F).sh(1.5F).r(42, -10, 0).l(28, 0, 0).legs(-14, 14).hd(4, 0)));
        t.add(k(s3 + 900, OUT, flick()));
        t.add(k(s3 + 1250, CURVE, ready()));
        return t;
    }

    /**
     * 踏背下刺：前一刀和背后锁颈一样，从背后扣住它捅进后腰；拔刀顺势把刀举过头一蹬，跳上它弯下去的后背蹲住，左手揪住肩甲，
     * 往脚下的后颈扎第二刀；拔出来整个人化作一道红光（这一段不画人），从它头顶闪到正面，现身就是蓄满的一刀，捅进胸口，
     * 两脚蹬着它往后空翻
     */
    private static List<Key> lichE(MercyBeats.Lich b) {
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        List<Key> table = lichB(b);
        table.removeIf(key -> key.t >= s1 + 500);
        table.addAll(List.of(
                // 拔刀，顺势把刀举过头，往下一蹲蓄力
                k(s1 + 560, IN, p().tw(-10).ln(24).sq(0.9F).sh(0.2F).r(150, -14, 0).aim(0).pull(1).l(60, 10, 0).grip(0)
                        .reach(0).legs(-16, 20).hd(-6, 0)),
                // 蹬地起跳：腿蹬直，刀举在头顶
                k(s1 + 650, OUT, p().tw(-4).ln(-6).sq(0).sh(0).r(170, -8, 0).l(150, 10, 0).legs(-30, 10).hd(-14, 0)),
                // 空中收腿
                k(s1 + 760, SINE, p().tw(-6).ln(14).sq(0.6F).r(172, -8, 0).l(110, 10, 0).legs(40, 60).hd(-6, 0)),
                // 落在背上蹲住，左手揪住肩甲，刀还举着
                k(s2 - 60, IN, p().tw(-12).ln(20).sq(1).sh(0.5F).r(172, -8, 0).aim(0).pull(1).l(92, 10, 0).grip(1)
                        .legs(-26, 36).hd(-8, 0)),
                // 第二刀往脚下的后颈扎，整个人压下去
                k(s2, OUT, p().tw(14).ln(38).sq(1).sh(1.5F).r(96, 4, 0).aim(1).pull(-1.5F).hd(14, 0)),
                k(s2 + 80, SINE, p().tw(16).ln(41).hd(16, 0)),
                k(s2 + 300, SINE, p().tw(18).r(NA, NA, 40).hd(14, 3)),
                // 拔出来，松开肩甲
                k(s2 + 420, IN, p().tw(-6).ln(16).sq(0.8F).sh(0.3F).r(150, -12, 0).aim(0).pull(1).l(70, 10, 0).grip(0)
                        .hd(-4, 0)),
                // 在正面现身：已经是蓄满的架势
                k(b.appear, SNAP, p().tw(-30).ln(9).sq(0.25F).sh(-0.4F).r(80, -10, -20).aim(0.6F)
                        .pull(4.5F).l(60, 10, 0).grip(0).reach(0).legs(-16, 20).hd(0, -2)),
                k(s3 - 115, SINE, p().tw(-32).ln(8).sq(0.3F).sh(-0.6F).r(80, -10, -24).pull(4.8F)),
                k(s3, OUT, p().tw(24).ln(22).sq(0.05F).sh(3.6F).r(88, 6, 0).aim(1).pull(-2).reach(3).legs(-24, 26)
                        .hd(5, 0)),
                k(s3 + 90, SINE, p().tw(28).ln(25).sh(4).hd(7, 0))));
        table.addAll(kickOff(s3));
        return table;
    }

    /**
     * 跪地特写这一段：刀刚扎进去、刃身还有一截露在外面。拿 {@link #plunged} 拍特写，拳头已经贴到（站得近的套路是埋进）
     * 目标身上，刀整根在它身体里，镜头里只剩刀柄和手。这里上身往后仰、肩往前送，胳膊抬平、稍往里收，拳头停在
     * 目标身前，刃身从拳头一路斜着扎进它腹部，从特写那一侧看得见。处决者站得越近（A 1.25、B 1.10、C 0.95 格），
     * 往后让得越多；左手按在它头盔上。这几行是对着特写机位摆的，改了要回特写里看刀还在不在画面里
     */
    private static Row closeup(MercyVariant v) {
        return switch (v) {
            case LICH_B, LICH_E -> p().tw(26).ln(-20).sq(0).sh(0).r(80, 14, 0).aim(0).pull(0).l(115.2F, -4.6F, 0).grip(0).reach(0)
                    .legs(0, 20).hd(0, 0);
            case LICH_C -> p().tw(30).ln(-25).sq(0).sh(0).r(80, 15, 0).aim(0).pull(0).l(114.7F, -4.4F, 0).grip(0).reach(0)
                    .legs(0, 20).hd(0, 0);
            default -> p().tw(12).ln(-5).sq(0).sh(0).r(80, 12, 0).aim(0).pull(0).l(117.5F, -5.3F, 0).grip(0).reach(0)
                    .legs(0, 20).hd(0, 0);
        };
    }

    /** 刀没入跪着的玄骸胸口、左手按在它头盔上（第三人称收尾），切镜时直接摆好 */
    private static Row plunged() {
        return p().tw(-5).ln(18).sq(0).sh(0).r(62, 0, 0).aim(0).pull(0).l(120.4F, -5.8F, 0).grip(0).reach(0)
                .legs(0, 20).hd(0, 0);
    }

    // ==================== 奴仆三套 ====================

    private static final Anchors ANCHOR_T1 = new Anchors(4, 3, -10, 0, 6, -11);
    private static final Anchors ANCHOR_T2 = new Anchors(-2, 4, -9, 0, 7, -10);
    private static final Anchors ANCHOR_T3 = new Anchors(0, 10, -10, 0, 12, -11);

    /** 飞跃下刺翻到最高点时整身离地多高（格） */
    private static final float T5_APEX = 2.2F;
    /** 飞跃下刺头朝下那一刻，刀尖比整身位置低多少（格）：支点 0.85 格处翻过来，肩到了身位上方 0.4，再往下一条胳膊加一截刀 */
    private static final float T5_TIP_DROP = 0.6F;
    /** 奴仆几套出刀时往前送的距离（0.12~0.22 格，取中），缩锚点时算目标中心在哪用 */
    private static final float THRALL_LUNGE = 0.17F;

    /**
     * 目标比动作原本按的那只小（{@code scale} < 1）时的抓握点和刀口。目标是绕自己的脚底中心缩的，点也照着缩：
     * 高度按离地多高缩，前后按离目标中心多远缩（目标中心在处决者前方「站距 − 出刀前送」处），左右按离中轴多远缩。
     * 锚点是玩家模型坐标（像素，y 朝下、脚底是 24，往前是 −z），换算成格要乘玩家的 0.9375，两边一除就约掉了
     */
    private static Anchors scaled(Anchors a, MercyVariant v, float scale) {
        if (scale >= 0.999F) {
            return a;
        }
        float center = -(v.standDist - THRALL_LUNGE) * 16.0F / 0.9375F;
        return new Anchors(a.gx * scale, 24.0F - scale * (24.0F - a.gy), center + scale * (a.gz - center),
                a.sx * scale, 24.0F - scale * (24.0F - a.sy), center + scale * (a.sz - center));
    }

    /** 抓肩直刺：揪着肩把它拽直，往下捅进胸口、拧刀，拔刀的同时一把推开 */
    private static List<Key> thrall1() {
        int s = MercyVariant.THRALL_1.stabMs(0);
        return List.of(
                k(0, SINE, start()),
                k(110, SINE, gather()),
                k(300, BACK, p().tw(-8).ln(19).sq(0.1F).sh(1.5F).r(30, -30, 0).l(72, 20, 0).grip(1).legs(-14, 16)
                        .hd(4, 0)),
                k(350, SINE, p().ln(18)),
                // 揪着肩把它拽直
                k(560, SINE, p().tw(-12).ln(12).sq(0).sh(0.6F).r(40, -34, -10).reach(-2).hd(2, 0)),
                k(s - 140, SINE, p().tw(-26).ln(12).sq(0.15F).sh(0).r(62, -8, -22).aim(0.6F).pull(3.5F)),
                k(s - 115, SINE, p().tw(-29).sq(0.2F).sh(-0.3F).r(62, -10, -26).pull(4.5F)),
                k(s, OUT, p().tw(16).ln(22).sq(0.1F).sh(2.5F).r(62, 5, 0).aim(1).pull(-1.5F).legs(-18, 20).hd(6, 0)),
                k(s + 80, SINE, p().tw(20).ln(24).sh(3).hd(8, 0)),
                k(s + 280, SINE, p().tw(22).sq(0.2F).r(NA, NA, 42).hd(6, 3)),
                k(1350, SINE, p().tw(20).ln(23).r(NA, NA, 36)),
                // 拔刀、左手一把推开
                k(1440, IN, p().tw(-18).ln(6).sq(0).sh(1).r(50, -30, -10).aim(0.3F).pull(3).l(96, 15, 0).grip(0)
                        .hd(-2, 0)),
                k(1560, SINE, p().tw(-20).ln(4).sh(1.4F).l(92, 10, 0)),
                k(1760, OUT, p().tw(-8).ln(6).sh(0.5F).r(22, -60, 30).aim(0).pull(0).l(40, -8, 0).legs(-8, 8)
                        .hd(2, 0)),
                k(2000, SINE, p().tw(0).ln(4).sq(0).sh(0).r(18, -30, 0).l(20, -15, 0).legs(-3, 3).hd(0, 0)),
                k(MercyBeats.thrall(MercyVariant.THRALL_1).end, SINE, zero()));
    }

    /** 锁喉背刺：左臂从后面勒住脖子往后拽，刀捅进后背，松手的同时一脚蹬出去 */
    private static List<Key> thrall2() {
        int s = MercyVariant.THRALL_2.stabMs(0);
        return List.of(
                k(0, SINE, start()),
                k(110, SINE, gather()),
                k(320, BACK, p().tw(-6).ln(16).sq(0.1F).sh(1.5F).r(28, -30, 0).l(86, 40, 0).grip(1).legs(-12, 14)
                        .hd(4, 0)),
                k(400, SINE, p().ln(14)),
                // 往后勒
                k(620, SINE, p().tw(-10).ln(7).sq(0.1F).sh(0.4F).reach(-2).hd(0, 4)),
                k(s - 140, SINE, p().tw(-24).ln(10).sq(0.15F).r(58, -8, -20).aim(0.6F).pull(3.5F)),
                k(s - 115, SINE, p().tw(-28).sq(0.2F).r(58, -10, -26).pull(4.5F)),
                k(s, OUT, p().tw(16).ln(20).sq(0.1F).sh(2).r(58, 5, 0).aim(1).pull(-1.5F).reach(0).legs(-16, 18)
                        .hd(6, 0)),
                k(s + 80, SINE, p().tw(20).ln(22).sh(2.5F).hd(8, 0)),
                k(s + 280, SINE, p().tw(22).sq(0.2F).r(NA, NA, 42).hd(6, 3)),
                k(1380, SINE, p().tw(20).r(NA, NA, 36)),
                // 松手、提膝，一脚蹬出去
                k(1450, IN, p().tw(-6).ln(-4).sq(0).sh(0).r(40, -30, -10).aim(0).pull(1).l(40, -10, 0).grip(0)
                        .legs(65, -4)
                        .hd(0, 0)),
                k(1530, OUT, p().tw(-4).ln(-8).sh(1.5F).legs(52, -8)),
                k(1700, SINE, p().tw(-2).ln(4).sh(0.5F).legs(8, 0)),
                k(1850, OUT, p().tw(-10).ln(6).r(22, -60, 30).pull(0).l(30, -15, 0).legs(-6, 6).hd(2, 0)),
                k(2050, SINE, p().tw(0).ln(4).sh(0).r(18, -30, 0).l(20, -15, 0).legs(-3, 3).hd(0, 0)),
                k(MercyBeats.thrall(MercyVariant.THRALL_2).end, SINE, zero()));
    }

    /** 压颈下刺：左手按住后颈往下压，刀举过头一下扎进去、压着拧，抽刀时横着把它推翻 */
    private static List<Key> thrall3() {
        int s = MercyVariant.THRALL_3.stabMs(0);
        return List.of(
                k(0, SINE, start()),
                k(110, SINE, gather()),
                k(320, BACK, p().tw(-4).ln(22).sq(0.15F).sh(1.5F).r(32, -30, 0).l(60, 20, 0).grip(1).legs(-12, 14)
                        .hd(6, 0)),
                k(400, SINE, p().ln(21)),
                // 往下压
                k(620, SINE, p().ln(24).sq(0.28F).reach(1).hd(8, 0)),
                // 刀举过头
                k(s - 140, SINE, p().tw(-12).ln(6).sq(0).sh(0.6F).r(165, -8, 0).hd(-6, 0)),
                k(s - 115, SINE, p().ln(4).r(170, -8, 0)),
                k(s, OUT, p().tw(12).ln(30).sq(0.42F).sh(2.5F).r(40, 5, 0).aim(1).legs(-16, 18).hd(10, 0)),
                k(s + 80, SINE, p().ln(33).sq(0.48F).hd(12, 0)),
                k(s + 260, SINE, p().tw(14).r(NA, NA, 36).hd(10, 3)),
                k(1440, SINE, p().tw(12).r(NA, NA, 30)),
                // 抽刀、横着一推
                k(1530, OUT, p().tw(-16).ln(14).sq(0.1F).sh(1.6F).r(55, -30, -10).aim(0).l(82, -12, 0).grip(0)
                        .hd(2, 0)),
                k(1660, SINE, p().tw(-18).ln(10).sh(1.8F).l(74, -24, 0)),
                k(1850, OUT, p().tw(-10).ln(6).sq(0).sh(0.5F).r(22, -60, 30).l(30, -15, 0).legs(-6, 6).hd(2, 0)),
                k(2050, SINE, p().tw(0).ln(4).sh(0).r(18, -30, 0).l(20, -15, 0).legs(-3, 3).hd(0, 0)),
                k(MercyBeats.thrall(MercyVariant.THRALL_3).end, SINE, zero()));
    }

    /** 穿胸绕背：揪住肩一刀捅穿，不拧刀，抽出来就势翻滚到它身后，它往前栽倒 */
    private static List<Key> thrall4() {
        int s = MercyVariant.THRALL_4.stabMs(0);
        return List.of(
                k(0, SINE, start()),
                k(110, SINE, gather()),
                k(280, BACK, p().tw(-8).ln(19).sq(0.1F).sh(1.5F).r(30, -30, 0).l(72, 20, 0).grip(1).legs(-14, 16)
                        .hd(4, 0)),
                k(s - 140, SINE, p().tw(-26).ln(12).sq(0.15F).sh(0).r(62, -8, -22).aim(0.6F).pull(3.5F).hd(2, 0)),
                k(s - 115, SINE, p().tw(-29).sq(0.2F).sh(-0.3F).r(62, -10, -26).pull(4.5F)),
                k(s, OUT, p().tw(18).ln(24).sq(0.1F).sh(3).r(62, 5, 0).aim(1).pull(-1.5F).legs(-20, 22).hd(6, 0)),
                k(s + 80, SINE, p().tw(22).ln(26).sh(3.4F).hd(8, 0)),
                k(s + 190, IN, p().tw(-28).ln(10).sq(0.25F).sh(0.6F).r(66, -52, 0).aim(0).pull(2).l(92, -10, 0).grip(0)
                        .legs(-14, 18).hd(0, 0)),
                k(s + 270, SINE, p().tw(-10).ln(26).sq(0.85F).sh(0).r(44, -30, 0).pull(0).l(40, 0, 0).legs(14, -10)
                        .hd(6, 0)),
                k(s + 360, OUT, tucked()),
                k(s + 620, SINE, tucked()),
                k(s + 760, OUT, landed()),
                k(1800, OUT, flick()),
                k(2050, SINE, p().tw(0).ln(4).sq(0).sh(0).r(18, -30, 0).l(20, -15, 0).legs(-3, 3).hd(0, 0)),
                k(MercyBeats.thrall(MercyVariant.THRALL_4).end, SINE, zero()));
    }

    /** 飞跃下刺：往后一坐蹬地跃起，从它头顶往前翻，倒着的时候手臂正好朝下，一刀扎进后颈，落在身后转回来 */
    private static List<Key> thrall5() {
        int s = MercyVariant.THRALL_5.stabMs(0);
        return List.of(
                k(0, SINE, start()),
                k(120, SINE, p().tw(-6).ln(14).sq(0.7F).sh(-0.5F).r(20, -40, 0).l(20, -30, 0).legs(-10, 12).hd(4, 0)),
                k(280, OUT, p().tw(0).ln(4).sq(0).sh(0).r(112, -20, 0).l(60, -20, 0).legs(-22, 32).hd(-8, 0)),
                k(560, SINE, p().ln(30).sq(0.8F).r(165, -10, 0).l(100, -10, 0).legs(50, 70).hd(8, 0)),
                k(s - 60, SINE, p().ln(22).sq(0.5F).r(150, -12, 0).pull(2).l(84, -20, 0).legs(34, 54)),
                k(s, OUT, p().tw(8).ln(16).sq(0.3F).r(178, 0, 0).pull(-2).legs(22, 44).hd(-6, 0)),
                k(s + 240, SINE, tucked()),
                k(s + 480, SINE, landed()),
                k(1560, SINE, p().tw(0).ln(26).sq(0.9F).r(50, -30, 0).l(30, -20, 0).legs(-14, 22).hd(2, 0)),
                k(1800, OUT, flick()),
                k(2050, SINE, p().tw(0).ln(4).sq(0).sh(0).r(18, -30, 0).l(20, -15, 0).legs(-3, 3).hd(0, 0)),
                k(MercyBeats.thrall(MercyVariant.THRALL_5).end, SINE, zero()));
    }

    private static List<Key> tableOf(MercyVariant v) {
        return switch (v) {
            case LICH_A -> lichA2(MercyBeats.lich(v));
            case LICH_B -> lichB2(MercyBeats.lich(v));
            case LICH_C -> lichC2(MercyBeats.lich(v));
            case LICH_D -> lichD2(MercyBeats.lich(v));
            case LICH_E -> lichE(MercyBeats.lich(v));
            case THRALL_1 -> thrall1();
            case THRALL_2 -> thrall2();
            case THRALL_3 -> thrall3();
            case THRALL_4 -> thrall4();
            case THRALL_5 -> thrall5();
        };
    }

    // ==================== 整身轨道 ====================

    /**
     * 踏背下刺：蹲在它后背上的位置（它弯着腰，后背最高处在身体中心往前约 0.4 格、离地 1.8 格上下）；
     * 闪到正面后的站距：人化成红光那一刻它就直起身跪着（同正面直刺第二刀，往后挫了半格），出刀那一下离它中心
     * 1.0 格：它跪着时大腿往前伸，再近手臂会插进大腿里
     */
    private static final float E_BACK_DIST = -1.5F, E_BACK_UP = 1.75F, E_FRONT_DIST = 0.3F;

    /** 蹬开目标往后空翻一圈，落在站位外 {@code land} 格（和 {@link #kickOff} 的上半身对着） */
    private static void hop(List<Key> b, int s, float near, float base, float land) {
        b.add(k(s + 170, SINE, m().dist(near).up(base + 0.3F)));
        b.add(k(s + 260, OUT, m().dist(near + 0.2F).up(base + 0.7F).flip(-40)));
        b.add(k(s + 520, SINE, m().dist((near + land) * 0.5F).up(base * 0.5F + 0.85F).flip(-200)));
        b.add(k(s + 700, IN, m().dist(land).up(0).flip(-360)));
    }

    private static List<Key> startedBody() {
        List<Key> b = new ArrayList<>();
        b.add(k(0, SINE, still()));
        return b;
    }

    /**
     * 正面直刺新写法的脚下。往前送超过 0.25 格必须迈步，不够的只压重心不挪人：第一刀不挪；第二刀迈一步送 0.36 格，
     * 人跟着落脚走、先快后慢，之后就站在前面不退回去（退回去又得迈一步）；第三刀只再压 0.15 格
     */
    private static List<Key> bodyA2(MercyBeats.Lich beats) {
        int s1 = beats.stab(0), s2 = beats.stab(1), s3 = beats.stab(2);
        List<Key> b = startedBody();
        b.add(k(s1 - 90, CURVE, m().dist(0.04F).up(-0.01F)));
        b.add(k(s1, ANTIC, m().dist(-0.08F).up(-0.06F)));
        b.add(k(s1 + 300, CURVE, m().dist(-0.06F).up(-0.03F)));
        b.add(k(s1 + 520, CURVE, m().dist(0.02F).up(0)));
        b.add(k(s2 - 160, CURVE, m().dist(0.06F).up(0)));
        b.add(k(s2 - 20, DECEL, m().dist(-0.3F).up(-0.04F)));
        b.add(k(s2, CURVE, m().dist(-0.34F).up(-0.06F)));
        b.add(k(s2 + 400, CURVE, m().dist(-0.33F).up(-0.03F)));
        // 刀举过头顶时重心往后、人往上提一点，扎下去再一起压
        b.add(k(s3 - 300, CURVE, m().dist(-0.28F).up(0.015F)));
        b.add(k(s3 - 120, CURVE, m().dist(-0.29F).up(0)));
        b.add(k(s3, IN, m().dist(-0.45F).up(-0.06F)));
        b.add(k(s3 + 90, CURVE, m().dist(-0.5F)));
        hop(b, s3, -0.5F, 0.0F, 1.5F);
        return b;
    }

    /**
     * 背后锁颈新写法的脚下：前两刀都不到 0.25 格，只压重心；勒脖子时往后坐 0.1 格。原地转一圈（转速两头慢中间快，
     * 转回来正好赶上出刀），落脚那一步送 0.4 格扎第三刀，它往前扑倒再跟半步不到
     */
    private static List<Key> bodyB2(MercyBeats.Lich beats) {
        int s1 = beats.stab(0), s2 = beats.stab(1), s3 = beats.stab(2);
        List<Key> b = startedBody();
        b.add(k(s1 - 90, CURVE, m().dist(0.04F).up(-0.01F)));
        b.add(k(s1, ANTIC, m().dist(-0.1F).up(-0.06F)));
        b.add(k(s1 + 300, CURVE, m().dist(-0.08F).up(-0.03F)));
        b.add(k(s1 + 520, CURVE, m().dist(0.0F).up(0)));
        b.add(k(s1 + 700, CURVE, m().dist(0.1F).up(0.01F)));
        b.add(k(s2 - 240, CURVE, m().dist(0.12F).up(0)));
        b.add(k(s2 - 110, CURVE, m().dist(0.1F).up(-0.01F)));
        b.add(k(s2, ANTIC, m().dist(-0.12F).up(-0.05F)));
        b.add(k(s2 + 300, CURVE, m().dist(-0.08F).up(-0.02F)));
        b.add(k(s2 + 400, CURVE, m().dist(-0.05F).up(0).yaw(0)));
        b.add(k(s2 + 520, CURVE, m().dist(-0.04F).yaw(-70)));
        b.add(k(s3 - 160, CURVE, m().dist(-0.06F).yaw(-290)));
        // 这几帧都用 CURVE：挨着别的模式的帧速度按 0 接，转到一半会停一下
        b.add(k(s3 - 20, CURVE, m().dist(-0.4F).up(-0.04F).yaw(-360)));
        b.add(k(s3, CURVE, m().dist(-0.45F).up(-0.06F)));
        b.add(k(s3 + 90, CURVE, m().dist(-0.5F)));
        b.add(k(s3 + 280, CURVE, m().dist(-0.62F).up(0)));
        b.add(k(s3 + 600, CURVE, m().dist(-0.5F)));
        return b;
    }

    /**
     * 贴身上挑新写法的脚下：第一刀只往上弹不挪人；第二刀送一步 0.26 格；第三刀前压到最低，刺入送一步再往上顶 0.3 格，
     * 接着蹬着它往上一窜后空翻
     */
    private static List<Key> bodyC2(MercyBeats.Lich beats) {
        int s1 = beats.stab(0), s2 = beats.stab(1), s3 = beats.stab(2);
        List<Key> b = startedBody();
        b.add(k(s1 - 90, CURVE, m().dist(0.04F).up(-0.08F)));
        b.add(k(s1, ANTIC, m().dist(-0.12F).up(0.04F)));
        b.add(k(s1 + 300, CURVE, m().dist(-0.1F).up(0.02F)));
        b.add(k(s1 + 380, CURVE, m().dist(-0.02F).up(-0.06F)));
        b.add(k(s2 - 200, CURVE, m().dist(0.04F).up(-0.1F)));
        b.add(k(s2 - 90, CURVE, m().dist(0.06F).up(-0.1F)));
        b.add(k(s2, ANTIC, m().dist(-0.2F).up(0.08F)));
        b.add(k(s2 + 300, CURVE, m().dist(-0.18F).up(0.05F)));
        b.add(k(s2 + 380, CURVE, m().dist(-0.1F).up(-0.06F)));
        b.add(k(s3 - 200, CURVE, m().dist(-0.05F).up(-0.14F)));
        b.add(k(s3 - 80, CURVE, m().dist(-0.06F).up(-0.13F)));
        b.add(k(s3, ANTIC, m().dist(-0.4F).up(0.3F)));
        b.add(k(s3 + 90, CURVE, m().dist(-0.42F).up(0.34F)));
        hop(b, s3, -0.35F, 0.9F, 1.5F);
        return b;
    }

    /**
     * 翻滚绕背新写法的脚下：前两刀照正面直刺压重心，第一刀甩刀时往后让一点、拽它时贴回去，第二刀迈一步送 0.32 格。
     * 拔刀往下压 0.14 格，蹬地侧身扑出去，贴着它翻滚半圈（440 ms，最高离地 0.4 格），落地往外滑一小段、拧身对着它的后背。
     * 落在身后比正面远半格：它第二刀以后往后挫了半格，正好挫到这一侧。翻滚这几帧都用 CURVE，挨着别的模式的帧速度按 0 接，
     * 滚到一半会停一下
     */
    private static List<Key> bodyD2(MercyBeats.Lich beats) {
        int s1 = beats.stab(0), s2 = beats.stab(1), s3 = beats.stab(2);
        List<Key> b = startedBody();
        b.add(k(s1 - 90, CURVE, m().dist(0.04F).up(-0.01F)));
        b.add(k(s1, ANTIC, m().dist(-0.08F).up(-0.06F)));
        b.add(k(s1 + 300, CURVE, m().dist(-0.06F).up(-0.03F)));
        b.add(k(s1 + 520, CURVE, m().dist(0.04F).up(0)));
        b.add(k(s1 + 660, CURVE, m().dist(0.12F).up(0.01F)));
        b.add(k(s1 + 820, CURVE, m().dist(0.05F).up(0)));
        b.add(k(s2 - 160, CURVE, m().dist(0.06F).up(0)));
        b.add(k(s2 - 20, DECEL, m().dist(-0.28F).up(-0.04F)));
        b.add(k(s2, CURVE, m().dist(-0.32F).up(-0.06F)));
        b.add(k(s2 + 300, CURVE, m().dist(-0.3F).up(-0.03F)));
        b.add(k(s2 + 380, CURVE, m().ang(0).dist(-0.22F).up(-0.05F).yaw(0).flip(0)));
        b.add(k(s2 + 460, CURVE, m().ang(4).dist(-0.12F).up(-0.14F).yaw(22)));
        b.add(k(s2 + 540, CURVE, m().ang(26).dist(0.2F).up(0.32F).yaw(72).flip(40)));
        b.add(k(s2 + 700, CURVE, m().ang(88).dist(0.42F).up(0.4F).yaw(84).flip(190)));
        b.add(k(s2 + 860, CURVE, m().ang(146).dist(0.45F).up(0.16F).yaw(84).flip(320)));
        b.add(k(s3 - 360, CURVE, m().ang(170).dist(0.55F).up(0).yaw(62).flip(360)));
        b.add(k(s3 - 280, CURVE, m().ang(177).dist(0.63F).up(-0.1F).yaw(34)));
        b.add(k(s3 - 170, CURVE, m().ang(180).dist(0.63F).up(-0.03F).yaw(0)));
        b.add(k(s3 - 70, CURVE, m().dist(0.66F).up(-0.02F)));
        b.add(k(s3, ANTIC, m().dist(0.22F).up(-0.06F)));
        b.add(k(s3 + 90, CURVE, m().dist(0.16F)));
        // 它往前扑倒，跟上去半步
        b.add(k(s3 + 300, CURVE, m().dist(-0.18F).up(0)));
        b.add(k(s3 + 620, CURVE, m().dist(-0.05F)));
        return b;
    }

    private static List<Key> bodyE(MercyBeats.Lich beats) {
        int s1 = beats.stab(0), s2 = beats.stab(1), s3 = beats.stab(2);
        List<Key> b = startedBody();
        b.add(k(s1 - 115, SINE, m().dist(0.12F).up(-0.02F)));
        b.add(k(s1, OUT, m().dist(-0.24F).up(-0.06F)));
        b.add(k(s1 + 80, SINE, m().dist(-0.28F)));
        b.add(k(s1 + 460, SINE, m().dist(-0.18F).up(-0.03F)));
        // 蹲下、蹬地，跳上它的后背
        b.add(k(s1 + 560, SINE, m().dist(-0.1F).up(-0.12F)));
        b.add(k(s1 + 650, OUT, m().dist(-0.5F).up(1.0F)));
        b.add(k(s1 + 760, SINE, m().dist(-1.2F).up(E_BACK_UP + 0.4F)));
        b.add(k(s2 - 60, IN, m().dist(E_BACK_DIST).up(E_BACK_UP)));
        b.add(k(s2, OUT, m().up(E_BACK_UP - 0.06F)));
        b.add(k(s2 + 300, SINE, m().up(E_BACK_UP - 0.02F)));
        b.add(k(s2 + 420, SINE, m().up(E_BACK_UP + 0.05F)));
        // 化作红光：人不画，这段时间从后背挪到它正面
        b.add(k(beats.blink, SNAP, m().hide(true)));
        b.add(k(beats.appear - 10, SINE, m().ang(180).dist(E_FRONT_DIST).up(0)));
        b.add(k(beats.appear, SNAP, m().hide(false)));
        // 第三刀扑上去一大步
        b.add(k(s3 - 115, SINE, m().dist(E_FRONT_DIST + 0.05F).up(-0.02F)));
        b.add(k(s3, OUT, m().dist(E_FRONT_DIST - 0.4F).up(-0.06F)));
        b.add(k(s3 + 90, SINE, m().dist(E_FRONT_DIST - 0.45F)));
        hop(b, s3, E_FRONT_DIST - 0.4F, 0.0F, E_FRONT_DIST - 0.4F + 1.5F);
        return b;
    }

    private static List<Key> bodyThrall(int s, float reach, int back) {
        List<Key> b = startedBody();
        b.add(k(s - 115, SINE, m().dist(0.1F).up(-0.02F)));
        b.add(k(s, OUT, m().dist(-reach).up(-0.05F)));
        b.add(k(s + 80, SINE, m().dist(-reach - 0.04F)));
        b.add(k(back - 120, SINE, m().up(-0.03F)));
        b.add(k(back, IN, m().dist(0).up(0)));
        return b;
    }

    /** 穿胸绕背：捅进去、抽出来，沿着它翻滚半圈，落在身后 */
    private static List<Key> bodyT4() {
        int s = MercyVariant.THRALL_4.stabMs(0);
        List<Key> b = startedBody();
        b.add(k(s - 115, SINE, m().dist(0.1F)));
        b.add(k(s, OUT, m().dist(-0.22F).up(-0.05F)));
        b.add(k(s + 80, SINE, m().dist(-0.26F)));
        b.add(k(s + 190, SINE, m().dist(-0.12F).up(0)));
        b.add(k(s + 270, SINE, m().ang(10).dist(0).yaw(35)));
        b.add(k(s + 360, OUT, m().ang(42).dist(0.2F).up(0.2F).yaw(80).flip(45)));
        b.add(k(s + 620, SINE, m().ang(138).dist(0.2F).up(0.2F).flip(310)));
        b.add(k(s + 740, OUT, m().ang(174).dist(0.06F).up(0).yaw(30).flip(360)));
        // 走到身后就是落地：服务端在这之后才把站位换过去
        b.add(k(MercyBeats.thrall(MercyVariant.THRALL_4).land, SINE, m().ang(180).dist(0).yaw(0)));
        return b;
    }

    /**
     * 飞跃下刺：从站位直直翻过它头顶，落在身后，再转回来看着它倒下
     *
     * <p>目标小一号时整条弧压低：头朝下那一刻刀尖在整身位置下方约 {@link #T5_TIP_DROP} 格，要刀尖跟着它的后颈按
     * {@code scale} 缩，跳起的高度就得缩成 {@code scale + (1 − scale) × 0.6 / 2.2}（2.2 是最高点）</p>
     */
    private static List<Key> bodyT5(float scale) {
        float across = -MercyVariant.THRALL_5.standDist;
        float h = scale + (1.0F - scale) * T5_TIP_DROP / T5_APEX;
        List<Key> b = startedBody();
        b.add(k(120, SINE, m().dist(0.15F)));
        b.add(k(280, OUT, m().dist(across * 0.24F).up(0.55F * h).flip(30)));
        b.add(k(560, SINE, m().dist(across * 0.5F).up(1.85F * h).flip(130)));
        // 正好翻到头朝下，手臂朝下对着它的后颈（它面向处决者，后颈在中轴偏处决者这一侧）
        b.add(k(800, SINE, m().dist(across * 0.72F).up(T5_APEX * h).flip(190)));
        b.add(k(1040, SINE, m().dist(across * 1.3F).up(1.6F * h).flip(268)));
        b.add(k(1280, IN, m().dist(across * 1.92F).up(0).flip(360)));
        b.add(k(1340, OUT, m().dist(across * 2.0F)));
        b.add(k(MercyBeats.thrall(MercyVariant.THRALL_5).land, 1380, OUT, m().yaw(180)));
        return b;
    }

    private static List<Key> bodyOf(MercyVariant v, float scale) {
        return switch (v) {
            case LICH_A -> bodyA2(MercyBeats.lich(v));
            case LICH_B -> bodyB2(MercyBeats.lich(v));
            case LICH_C -> bodyC2(MercyBeats.lich(v));
            case LICH_D -> bodyD2(MercyBeats.lich(v));
            case LICH_E -> bodyE(MercyBeats.lich(v));
            case THRALL_1 -> bodyThrall(v.stabMs(0), 0.2F, 1440);
            case THRALL_2 -> bodyThrall(v.stabMs(0), 0.18F, 1500);
            case THRALL_3 -> bodyThrall(v.stabMs(0), 0.12F, 1530);
            case THRALL_4 -> bodyT4();
            case THRALL_5 -> bodyT5(scale);
        };
    }

    /**
     * 被挣开：甩回站位外 {@code push} 格（判错 1.5、挣开站起 1.0，服务端同时把人推到那里）。人不在站位上时是整个人
     * 被甩出去翻一圈：踏背下刺在背上被掀下来往后翻；在它正面被一把抡起来，从它头顶甩回站位那一侧（沿着中轴越过它，
     * 所以「距」是负的）
     */
    private static void thrown(List<Key> body, MercyVariant v, int lastK, int t0, float push) {
        if (v == MercyVariant.LICH_E && lastK == 2) {
            // 先被掀离它的后背（它马上要站起来，人不能还压在它身上），再往后翻一圈落地
            body.add(k(t0 + 150, OUT, m().dist(0.3F).up(E_BACK_UP + 0.6F).flip(-150)));
            body.add(k(t0 + 440, IN, m().dist(push).up(0).flip(-360)));
        } else if (v == MercyVariant.LICH_E && lastK == 3) {
            float across = -2.0F * v.standDist - push;
            body.add(k(t0 + 200, OUT, m().dist(-v.standDist - 0.1F).up(E_BACK_UP + 1.0F).yaw(90).flip(160)));
            body.add(k(t0 + 480, IN, m().dist(across).up(0).yaw(180).flip(360)));
        } else if (v == MercyVariant.LICH_D && lastK == 3) {
            // 从它背后往镜头另一侧甩回去（ang 走 180 → 360），走镜头这一侧会擦着镜头飞过去
            body.add(k(t0 + 200, OUT, m().ang(265).dist(0.6F).up(1.1F).yaw(-170).flip(150)));
            body.add(k(t0 + 460, IN, m().ang(360).dist(push).up(0).yaw(-360).flip(360)));
        } else {
            body.add(k(t0 + 300, OUT, m().dist(push).up(0)));
        }
    }

    /** 套路表里到 {@code until} 为止的帧（之后是下一刀的蓄力，这一场不演了） */
    private static void cutAfter(List<Key> from, List<Key> to, int until) {
        for (Key key : from) {
            if (key.t <= until) {
                to.add(key);
            }
        }
    }

    private static Anchors anchorsOf(MercyVariant v) {
        return switch (v) {
            case LICH_A -> ANCHOR_A;
            case LICH_B -> ANCHOR_B;
            case LICH_C -> ANCHOR_C;
            case LICH_D -> ANCHOR_A;
            case LICH_E -> ANCHOR_B;
            case THRALL_1, THRALL_4 -> ANCHOR_T1;
            case THRALL_2 -> ANCHOR_T2;
            case THRALL_3 -> ANCHOR_T3;
            case THRALL_5 -> ANCHOR_T1;
        };
    }


    // ==================== 拼轨道 ====================

    /**
     * 按计划位拼出这一场的姿态轨道
     *
     * @param scale 目标身材相对动作原本按的那只（{@link AbstractKuva#mercyBodyScale}），奴仆几套的刀口、抓握点和跳起的高度按它缩
     */
    static Track track(MercyPlan.Plan p, boolean shortOutro, float scale) {
        return track(p, shortOutro, scale, 0);
    }

    /** @param seed 这一场的种子，只管抖动的幅度和相位 */
    static Track track(MercyPlan.Plan p, boolean shortOutro, float scale, int seed) {
        MercyTimeline.Outcome oc = MercyTimeline.outcome(p);
        MercyVariant v = p.variant();
        List<Key> table = tableOf(v);
        List<Key> moves = bodyOf(v, scale);
        List<Key> body = new ArrayList<>();
        int[] stabs = MercyTimeline.stabTimes(p);
        float[] weight = new float[stabs.length];
        for (int i = 0; i < stabs.length; i++) {
            weight[i] = v.thrall ? 1.2F : (MercyTimeline.isHeavyStab(p, i) ? 1.6F : 1.0F);
        }
        int strainFrom = -1, strainTo = -1;
        List<Key> out = new ArrayList<>();
        switch (oc) {
            case SUCCESS, CONVERT -> {
                MercyBeats.Lich lb = MercyBeats.lich(v);
                boolean convert = oc == MercyTimeline.Outcome.CONVERT;
                out.addAll(table);
                // 特写和收尾按站位摆，切镜头那一刻人回到站位，到交还都不再动
                body.addAll(moves);
                body.add(k(lb.closeup, SNAP, still()));
                body.add(k(MercyTimeline.handbackMs(p, shortOutro), SINE, m()));
                // 切跪地特写那一刻直接摆成刀扎进去、刃身露一截（特写里要拍到这只手和刀）；特写里刀慢慢往里再送一点
                Row closeup = closeup(v);
                out.add(k(lb.closeup, SNAP, closeup));
                Row pressed = closeup(v);
                pressed.v[R_RAISE] += 2.0F;
                pressed.v[TWIST] += 2.0F;
                pressed.v[L_RAISE] -= 2.0F;
                if (shortOutro || convert) {
                    // 特写完直接交还：保持到切镜那一刻（感化是起身化雾那一刻），再 400 ms 回 0（刀跟着抽出来）
                    int from = convert ? lb.convertLeave : lb.third;
                    out.add(k(from - 1, SINE, pressed));
                    out.add(k(from + 400, from, SINE, zero()));
                } else {
                    out.add(k(lb.third - 1, SINE, pressed));
                    // 切第三人称那一刻刀已整根没入，压着等抽刀
                    out.add(k(lb.third, SNAP, plunged()));
                    out.add(k(lb.drawOut, SINE, plunged().ln(21).sh(0.5F)));
                    // 抽刀：往回一拽、松开头盔，甩一下刀，交还前回到站姿
                    out.add(k(lb.drawOut + 110, lb.drawOut, OUT,
                            p().tw(-18).ln(6).sh(-1).r(42, -35, -10).l(60, 10, 0).legs(-8, 14).hd(-2, 0)));
                    out.add(k(lb.drawOut + 370, OUT, flick()));
                    out.add(k(lb.successEnd - 200, SINE, ready()));
                    out.add(k(lb.successEnd, lb.successEnd - 200, SINE, zero()));
                }
            }
            case ADVANCE -> {
                // 这一刀也对：照常拧刀，它攒着劲往上顶（最后 250 ms 手开始抖），猛地挣开站起来，人被推开 1 格、
                // 踉跄一下稳住，刀架在身前接着打
                int stabK = stabs[stabs.length - 1];
                int free = MercyTimeline.breakFreeMs(p);
                cutAfter(table, out, stabK + 90);
                cutAfter(moves, body, stabK + 90);
                body.add(k(free, SINE, m()));
                thrown(body, v, stabs.length, free, (float) MercyTimeline.ADVANCE_PUSH);
                body.add(k(MercyTimeline.handbackMs(p, shortOutro), SINE, m()));
                // 第二刀往回拧（踏背下刺那一刀在背上，照它自己的方向）
                float roll = stabs.length == 2 && v != MercyVariant.LICH_E ? -40.0F : 40.0F;
                out.add(k(stabK + 300, SINE, p().r(NA, NA, roll).hd(NA, Math.signum(roll) * 3.0F)));
                out.add(k(stabK + 460, SINE, p().r(NA, NA, roll * 0.8F)));
                out.add(k(free - 60, SINE, p().r(NA, NA, roll * 0.7F)));
                strainFrom = free - 250;
                strainTo = free;
                // 被顶开：比判错那一下轻，两臂往外一甩、上身后仰，退一步就站住
                out.add(k(free + 70, OUT, p().tw(-16).ln(-8).sq(0).sh(-1.5F)
                        .r(70, -50, 0).aim(0).pull(0).l(112, -25, 0).grip(0).reach(0).legs(-4, 8).hd(-10, 4)));
                out.add(k(free + 300, SINE, p().tw(-10).ln(-8).sh(-2)
                        .r(54, -52, 16).l(92, -28, 0).legs(12, -8).hd(-6, 2)));
                out.add(k(free + 460, OUT, p().tw(-6).ln(12).sq(0.35F).sh(0)
                        .r(40, -40, 16).l(48, -28, 0).legs(-18, 18).hd(4, 0)));
                // 刀横在身前，盯着它站起来
                out.add(k(free + 800, SINE, p().tw(-10).ln(10).sq(0.25F).sh(0.4F)
                        .r(60, -14, -18).l(40, 24, 0).legs(-14, 14).hd(0, 0)));
                int handback = MercyTimeline.handbackMs(p, shortOutro);
                out.add(k(handback, handback - 200, SINE, zero()));
            }
            case FAIL -> {
                int stabK = stabs[stabs.length - 1];
                int free = MercyTimeline.breakFreeMs(p);
                cutAfter(table, out, stabK + 90);
                cutAfter(moves, body, stabK + 90);
                body.add(k(free, SINE, m()));
                thrown(body, v, stabs.length, free, (float) MercyTimeline.FAIL_PUSH);
                body.add(k(MercyTimeline.handbackMs(p, shortOutro), SINE, m()));
                // 判错那一刀拧不动：顶着、手越抖越厉害（抖是叠上去的），直到被挣开
                Row stuck = p().r(NA, NA, 18);
                out.add(k(stabK + 300, SINE, stuck));
                out.add(k(free - 60, SINE, p()));
                strainFrom = stabK + 120;
                strainTo = free;
                // 刀被挣出来、整个人被震开：两臂往外甩、往后仰，脚在身前往后滑（服务端这 300 ms 把人往后推 1.5 格）
                out.add(k(free + 70, OUT, p().tw(-22).ln(-14).sq(0).sh(-2)
                        .r(78, -55, 0).aim(0).pull(0).l(135, -25, 0).grip(0).reach(0).legs(-6, 10).hd(-14, 6)));
                out.add(k(free + 300, SINE, p().tw(-14).ln(-18).sh(-3)
                        .r(60, -60, 20).l(118, -30, 0).legs(16, -12).hd(-10, 4)));
                // 踉跄一步稳住，刀横在身前防着它
                out.add(k(free + 460, OUT, p().tw(-6).ln(12).sq(0.4F).sh(0)
                        .r(38, -45, 20).l(50, -30, 0).legs(-22, 20).hd(4, 0)));
                out.add(k(free + 800, SINE, p().tw(-10).ln(12).sq(0.25F).sh(0.5F)
                        .r(62, -12, -20).l(42, 25, 0).legs(-16, 16).hd(0, 0)));
                int handback = MercyTimeline.handbackMs(p, shortOutro);
                out.add(k(handback, handback - 200, SINE, zero()));
            }
            case THRALL -> {
                out.addAll(table);
                body.addAll(moves);
                body.add(k(MercyBeats.thrall(v).end, SINE, m()));
            }
        }
        boolean v2 = v == MercyVariant.LICH_A || v == MercyVariant.LICH_B || v == MercyVariant.LICH_C
                || v == MercyVariant.LICH_D;
        if (v2) {
            // 不再回零硬交：最后一帧停在前一帧的姿势上，交还前那 250 ms 由 apply 和原版姿态混过去
            out.set(out.size() - 1, k(out.get(out.size() - 1).t, CURVE, p()));
        }
        Key[] keys = resolve(out);
        Key[][] fp = v.thrall ? thrallFirstPerson(v) : lichFirstPerson(p, stabs);
        Anchors anchors = v.thrall ? scaled(anchorsOf(v), v, scale) : anchorsOf(v);
        Key[] bodyKeys = resolve(body);
        Track tr = new Track(keys, resolve(Arrays.asList(fp[0])), resolve(Arrays.asList(fp[1])), bodyKeys, stabs,
                weight, anchors, strainFrom, strainTo);
        if (v2) {
            tr.v2 = true;
            tr.curve = curveOf(keys, N);
            tr.bodyCurve = curveOf(bodyKeys, BN);
            if (v == MercyVariant.LICH_A && stabs.length >= 2) {
                tr.feet = curveOf(resolve(stepA2(stabs[1])), N);
            } else if (v == MercyVariant.LICH_B && stabs.length >= 3) {
                tr.feet = curveOf(resolve(stepB2(stabs[1], stabs[2])), N);
            } else if ((v == MercyVariant.LICH_C || v == MercyVariant.LICH_D) && stabs.length >= 3) {
                tr.feet = curveOf(resolve(stepC2(stabs[1], stabs[2])), N);
            } else if ((v == MercyVariant.LICH_C || v == MercyVariant.LICH_D) && stabs.length == 2) {
                // 第三刀那一步落在挣开的时候，只留第二刀的
                tr.feet = curveOf(resolve(stepA2(stabs[1])), N);
            }
            int h = seed * 0x9E3779B1;
            tr.shakeAmp = 0.8F + 0.45F * ((h >>> 8) & 0xFF) / 255.0F;
            tr.phaseA = ((h >>> 16) & 0xFF) / 255.0F * (float) (Math.PI * 2.0D);
            tr.phaseB = ((h >>> 24) & 0xFF) / 255.0F * (float) (Math.PI * 2.0D);
        }
        if (v == MercyVariant.LICH_E) {
            tr.alt = ANCHOR_E;
            MercyBeats.Lich lb = MercyBeats.lich(v);
            tr.altFrom = lb.stab(0) + 560;
            tr.altTo = lb.blink;
            tr.tail = ANCHOR_A;
        }
        return tr;
    }

    /** 按 {@code step} 毫秒把整条轨道采样成文本（上身、两只第一人称手、整身、抓握点），改节拍前后对比用 */
    static void dump(MercyPlan.Plan p, boolean shortOutro, float scale, int step, StringBuilder out) {
        Track tr = track(p, shortOutro, scale);
        int end = Math.max(tr.endT, tr.body[tr.body.length - 1].t) + step;
        float[] v = new float[N];
        float[] fr = new float[FN];
        float[] fl = new float[FN];
        float[] b = new float[BN];
        for (int t = 0; t <= end; t += step) {
            pose(tr, t, v);
            sample(tr.fpRight, t, fr);
            sample(tr.fpLeft, t, fl);
            sampleBody(tr, t, b);
            out.append(t).append(" P");
            for (float x : v) {
                out.append(' ').append(x);
            }
            out.append(" R");
            for (float x : fr) {
                out.append(' ').append(x);
            }
            out.append(" L");
            for (float x : fl) {
                out.append(' ').append(x);
            }
            out.append(" B");
            for (float x : b) {
                out.append(' ').append(x);
            }
            out.append(" A ").append(tr.anchorsAt(t)).append('\n');
        }
    }

    /** 处决者此刻的整身位置（脚底，世界坐标）、模型朝向和翻转；{@code blend} 是这些偏移此刻生效几成 */
    static final class Body {
        double x, y, z;
        float yaw, flip, roll, blend;
        /** 这一刻整个人不画（化作红光闪过去的那一下） */
        boolean hidden;
    }

    private static final float[] MOVE = new float[BN];

    /** 算这一刻的整身状态；轨道走完了返回 false，人回到真实位置 */
    static boolean body(MercyClient.Exec e, long t, Body out) {
        Key[] keys = e.pose.body;
        int end = keys[keys.length - 1].t;
        if (t >= end) {
            return false;
        }
        sampleBody(e.pose, t, MOVE);
        float a = MOVE[B_ANG] * e.side * DEG;
        double cos = Mth.cos(a);
        double sin = Mth.sin(a);
        double dx = e.f.x * cos + e.s.x * sin;
        double dz = e.f.z * cos + e.s.z * sin;
        double r = e.dist + MOVE[B_DIST];
        out.x = e.pl.x + dx * r;
        out.y = e.pe.y + MOVE[B_UP];
        out.z = e.pl.z + dz * r;
        out.yaw = MercyClient.yawOf(new Vec3(-dx, 0.0D, -dz)) + MOVE[B_YAW] * e.side;
        // 翻满一圈的表里停在 ±360，折回来，不然首尾淡入淡出按比例缩放时会倒着再转一圈
        out.flip = Mth.wrapDegrees(MOVE[B_FLIP]);
        out.roll = Mth.wrapDegrees(MOVE[B_ROLL] * e.side);
        out.blend = Mth.clamp(Math.min(t, end - t) / (float) BODY_BLEND_MS, 0.0F, 1.0F);
        out.hidden = MOVE[B_HIDE] > 0.5F;
        return true;
    }

    private static MercyCurve.Keys curveOf(Key[] keys, int width) {
        int n = keys.length;
        int[] t = new int[n];
        int[] start = new int[n];
        int[] mode = new int[n];
        float[][] v = new float[n][];
        for (int i = 0; i < n; i++) {
            t[i] = keys[i].t;
            start[i] = keys[i].start;
            mode[i] = keys[i].mode;
            v[i] = keys[i].v.length == width ? keys[i].v : Arrays.copyOf(keys[i].v, width);
        }
        return new MercyCurve.Keys(t, start, mode, v);
    }

    /** 这一刻上身各通道的值（含叠上去的顿挫、抖、喘） */
    private static void pose(Track tr, long t, float[] v) {
        if (tr.v2) {
            tr.curve.sampleStaggered(t, STAGGER, v);
            if (tr.feet != null) {
                v[R_LEG] += tr.feet.sample(R_LEG, t);
                v[L_LEG] += tr.feet.sample(L_LEG, t);
            }
        } else {
            sample(tr.keys, t, v);
        }
        addLife(tr, t, v);
    }

    private static void sampleBody(Track tr, long t, float[] dst) {
        if (tr.v2) {
            for (int c = 0; c < BN; c++) {
                dst[c] = tr.bodyCurve.sample(c, t);
            }
        } else {
            sample(tr.body, t, dst);
        }
    }

    /** 表里空着的值沿用上一行（各行都是这一场自己的副本，改了不影响别的场） */
    private static Key[] resolve(List<Key> list) {
        Key[] keys = new Key[list.size()];
        for (int i = 0; i < keys.length; i++) {
            Key key = list.get(i);
            keys[i] = new Key(key.t, key.start, key.mode, key.v.clone());
        }
        for (int i = 1; i < keys.length; i++) {
            float[] prev = keys[i - 1].v;
            float[] cur = keys[i].v;
            for (int j = 0; j < cur.length; j++) {
                if (Float.isNaN(cur[j])) {
                    cur[j] = prev[j];
                }
            }
        }
        return keys;
    }

    /** 某一刻的值，写进 {@code dst}（长度和关键帧一样） */
    private static void sample(Key[] keys, long t, float[] dst) {
        int n = dst.length;
        if (t <= keys[0].t) {
            System.arraycopy(keys[0].v, 0, dst, 0, n);
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
                    u = ease(cur.mode, x);
                }
            }
            for (int j = 0; j < n; j++) {
                dst[j] = prev.v[j] + (cur.v[j] - prev.v[j]) * u;
            }
            return;
        }
        System.arraycopy(keys[keys.length - 1].v, 0, dst, 0, n);
    }

    private static float ease(int mode, float x) {
        return MercyCurve.ease(mode, x);
    }

    // ==================== 叠在关键帧上的 ====================

    /** 刺中以后 {@code dt} 毫秒的顿挫：惯性先往里多送一下，再被反力顶回来，衰减着晃两下，约半秒 */
    private static float jolt(long dt) {
        if (dt < 0L || dt > 520L) {
            return 0.0F;
        }
        return (float) (Math.exp(-dt / 140.0D) * Math.sin(Math.PI * 2.0D * dt / 300.0D));
    }

    /** 刀在里面拧的那一阵手上的抖：刺中后 100~420 ms，两头收住 */
    private static float tremor(long dt) {
        if (dt < 100L || dt > 420L) {
            return 0.0F;
        }
        float env = (float) Math.sin(Math.PI * (dt - 100L) / 320.0D);
        return env * (float) Math.sin(Math.PI * 2.0D * dt / 85.0D);
    }

    /** 判错那一刀顶住时的抖：越往后越厉害 */
    private static float strain(Track tr, long t) {
        if (tr.strainFrom < 0 || t < tr.strainFrom || t >= tr.strainTo) {
            return 0.0F;
        }
        float u = (t - tr.strainFrom) / (float) (tr.strainTo - tr.strainFrom);
        return (0.5F + 2.5F * u) * (float) Math.sin(Math.PI * 2.0D * t / 75.0D);
    }

    // 新写法的两种抖：两路周期不成倍数的正弦叠起来，幅度和相位按场次变，不再像马达
    private static float tremor2(Track tr, long dt) {
        if (dt < 100L || dt > 420L) {
            return 0.0F;
        }
        float env = (float) Math.sin(Math.PI * (dt - 100L) / 320.0D);
        return env * tr.shakeAmp * (0.65F * (float) Math.sin(Math.PI * 2.0D * dt / 83.0D + tr.phaseA)
                + 0.45F * (float) Math.sin(Math.PI * 2.0D * dt / 131.0D + tr.phaseB));
    }

    private static float strain2(Track tr, long t) {
        if (tr.strainFrom < 0 || t < tr.strainFrom || t >= tr.strainTo) {
            return 0.0F;
        }
        float u = (t - tr.strainFrom) / (float) (tr.strainTo - tr.strainFrom);
        return (0.5F + 2.5F * u) * tr.shakeAmp * (0.65F * (float) Math.sin(Math.PI * 2.0D * t / 71.0D + tr.phaseA)
                + 0.45F * (float) Math.sin(Math.PI * 2.0D * t / 113.0D + tr.phaseB));
    }

    /** 喘气，每捅一刀喘得重一点；交还前 300 ms 收掉，免得最后一帧跳一下 */
    private static float breath(Track tr, long t, int done, float phase) {
        float fade = Mth.clamp((tr.endT - t) / 300.0F, 0.0F, 1.0F);
        return fade * (0.8F + 0.45F * done) * (float) Math.sin(Math.PI * 2.0D * t / 1500.0D + phase);
    }

    /** 顿挫、拧刀时的抖、判错时的顶、喘气，叠到采样出来的姿态上 */
    private static void addLife(Track tr, long t, float[] v) {
        int done = 0;
        for (int i = 0; i < tr.stabs.length; i++) {
            long dt = t - tr.stabs[i];
            if (dt < 0L) {
                continue;
            }
            done++;
            float w = tr.weight[i];
            float j = jolt(dt);
            if (j != 0.0F) {
                v[LEAN] += 5.0F * w * j;
                v[TWIST] += 5.0F * w * j;
                v[R_RAISE] += 3.0F * w * j;
                v[SHIFT] += w * j;
                v[R_PULL] -= 1.2F * w * j;
                v[NOD] += 7.0F * w * j;
                v[L_RAISE] -= 2.5F * w * j;
            }
            if (tr.v2 && dt < 300L) {
                // 刺中那一下头往下一点：先于顿挫到顶
                float kick = dt / 35.0F;
                v[NOD] += 4.0F * w * kick * (float) Math.exp(1.0D - kick);
            }
            float tr2 = tr.v2 ? tremor2(tr, dt) : tremor(dt);
            if (tr2 != 0.0F) {
                v[R_RAISE] += 1.2F * w * tr2;
                v[TWIST] += 0.8F * tr2;
                v[R_ROLL] += 2.0F * tr2;
            }
        }
        float s = tr.v2 ? strain2(tr, t) : strain(tr, t);
        if (s != 0.0F) {
            v[R_RAISE] += s;
            v[TWIST] += 0.6F * s;
            v[LEAN] += 0.3F * s;
            v[R_ROLL] += 1.5F * s;
            v[L_RAISE] += 0.8F * s;
        }
        float b = breath(tr, t, done, 0.0F);
        v[LEAN] += 0.9F * b;
        v[NOD] -= 1.1F * b;
        v[R_RAISE] += 0.7F * b;
        v[L_RAISE] += 0.7F * b;
    }

    // ==================== 第三人称 ====================

    private static final float[] SCRATCH = new float[N];
    private static final Body BODY = new Body();
    private static final Quaternionf Q = new Quaternionf();
    private static final Quaternionf Q_AIM = new Quaternionf();
    /** 原版两条腿的左右位置（建模型时定下，之后原版不再动它） */
    private static final float LEG_X = 1.9F;
    /** 身子、头的 z、头的 zRot 和腿的左右位置被挪过的模型（原版不复位这几样，用完要放回去） */
    private static final Set<HumanoidModel<?>> SHIFTED = Collections.newSetFromMap(new WeakHashMap<>());

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
        long t = e != null ? e.elapsed() : 0L;
        if (e == null || t >= e.pose.endT) {
            if (!SHIFTED.isEmpty() && SHIFTED.remove(model)) {
                model.body.z = 0.0F;
                model.head.z = 0.0F;
                model.head.zRot = 0.0F;
                model.rightLeg.x = -LEG_X;
                model.leftLeg.x = LEG_X;
                model.hat.copyFrom(model.head);
            }
            return;
        }
        Track tr = e.pose;
        float[] v = SCRATCH;
        pose(tr, t, v);
        float mix = tr.v2 ? vanillaMix(tr, t) : 1.0F;
        if (mix < 1.0F) {
            storeVanilla(model);
        }

        // 身体：扭、前倾，前倾和蹲按原版下蹲那组偏移挪位置；「送」把上身和胯一起往前挪
        float bodyY = -v[TWIST] * DEG;
        model.body.yRot = bodyY;
        model.body.xRot = v[LEAN] * DEG;
        float crouch = Mth.clamp(v[LEAN] / 28.6F, 0.0F, 1.0F) + Mth.clamp(v[SQUAT], 0.0F, 1.0F) * 0.6F;
        float dz = -v[SHIFT];
        model.body.y = 3.2F * crouch;
        model.body.z = dz;
        model.head.y = 4.2F * crouch;
        model.head.z = dz;
        model.rightArm.y = 2.0F + 3.2F * crouch;
        model.leftArm.y = 2.0F + 3.2F * crouch;
        // 身子绕脖子转，原版那组偏移只管不扭身的 0~28.6°（下蹲）。往后仰、弯得比下蹲还深、或者边扭边弯时，
        // 身子底边会离开胯，腰上前后、左右错开一截：腿再跟着身子底边挪过去（往后仰就是挺胯，脚往前送）。
        // 原版的身子是一整块、没有单独的胯，扭身时底边跟着转：两条腿也得一起转、各自落在转过去的底边两侧，
        // 不然扭三四十度时腰上一边凸出一截，上下半身像拧开了
        float leanRad = v[LEAN] * DEG;
        float crouchRad = Mth.clamp(v[LEAN], 0.0F, 28.6F) * DEG;
        float sin = Mth.sin(bodyY);
        float cos = Mth.cos(bodyY);
        float hipX = 12.0F * Mth.sin(leanRad) * sin;
        float hipZ = 12.0F * (Mth.sin(leanRad) * cos - Mth.sin(crouchRad)) + 4.0F * crouch + dz;
        float hipY = 12.0F * (Mth.cos(leanRad) - Mth.cos(crouchRad));
        // 绕竖轴转 bodyY：(x, 0, 0) → (x·cos, 0, −x·sin)，和上面胯的算法、下面两肩的挪法同一个方向
        model.rightLeg.x = hipX - LEG_X * cos;
        model.leftLeg.x = hipX + LEG_X * cos;
        model.rightLeg.z = hipZ + LEG_X * sin;
        model.leftLeg.z = hipZ - LEG_X * sin;
        model.rightLeg.y = 12.0F + 0.2F * crouch + hipY;
        model.leftLeg.y = 12.0F + 0.2F * crouch + hipY;

        // 两肩跟着扭挪位置（照原版挥砍那几行）
        model.rightArm.z = sin * 5.0F + dz + v[R_PULL];
        model.rightArm.x = -cos * 5.0F;
        model.leftArm.z = -sin * 5.0F + dz;
        model.leftArm.x = cos * 5.0F;

        Anchors a = tr.anchorsAt(t);
        float reach = v[REACH];
        poseArm(model.rightArm, true, v[R_RAISE], v[R_YAW], v[R_ROLL], v[R_AIM], a.sx, a.sy, a.sz - reach, bodyY, tr.v2);
        poseArm(model.leftArm, false, v[L_RAISE], v[L_YAW], v[L_ROLL], v[L_GRIP], a.gx, a.gy, a.gz - reach, bodyY, tr.v2);

        // 腿：往前送的时候后脚不动，落在后面的那条腿斜过去补上
        float comp = (float) (Math.atan2(v[SHIFT], 12.0D) * RAD_TO_DEG);
        float rLeg = v[R_LEG];
        float lLeg = v[L_LEG];
        if (comp > 0.0F == rLeg <= lLeg) {
            rLeg -= comp;
        } else {
            lLeg -= comp;
        }
        model.rightLeg.xRot = -rLeg * DEG;
        model.leftLeg.xRot = -lLeg * DEG;
        // 腿的截面跟身子底边一起转（前后摆按转过去的方向摆）
        model.rightLeg.yRot = bodyY;
        model.leftLeg.yRot = bodyY;
        model.rightLeg.zRot = 0.005F;
        model.leftLeg.zRot = -0.005F;

        // 头看向目标胸口，再叠点头、歪头；翻过来的时候看不了，头跟着身子走
        Entity target = e.target();
        double ax = target != null ? target.getX() : e.pl.x;
        double ay = target != null ? MercyTimeline.chest(target).y : e.pl.y + 1.45D;
        double az = target != null ? target.getZ() : e.pl.z;
        boolean moved = body(e, t, BODY);
        double dx = ax - (moved ? BODY.x : entity.getX());
        double dy = ay - ((moved ? BODY.y : entity.getY()) + entity.getEyeHeight());
        double ddz = az - (moved ? BODY.z : entity.getZ());
        float lookYaw = (float) (Mth.atan2(-dx, ddz) * RAD_TO_DEG);
        float lookPitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + ddz * ddz)) * RAD_TO_DEG);
        float yaw = Mth.wrapDegrees(lookYaw - (moved ? BODY.yaw : entity.yBodyRot));
        float look = moved ? Mth.clamp(Mth.cos(BODY.flip * DEG), 0.0F, 1.0F) : 1.0F;
        float headYaw = Mth.clamp(yaw, -75.0F, 75.0F) * look;
        float headPitch = lookPitch * look;
        if (tr.v2) {
            // 头有惯性：一阶滞后跟过去。按演出钟算，顿帧时跟着停；同一帧画两遍（阴影之类）不会多跟一步
            long dt = t - tr.headT;
            if (tr.headT == Long.MIN_VALUE || dt < 0L || dt > 250L) {
                tr.headYaw = headYaw;
                tr.headPitch = headPitch;
            } else if (dt > 0L) {
                float k = 1.0F - (float) Math.exp(-dt / HEAD_LAG_MS);
                tr.headYaw += (headYaw - tr.headYaw) * k;
                tr.headPitch += (headPitch - tr.headPitch) * k;
            }
            tr.headT = t;
            headYaw = tr.headYaw;
            headPitch = tr.headPitch;
        }
        model.head.yRot = headYaw * DEG;
        model.head.xRot = Mth.clamp(headPitch + v[NOD], -60.0F, 60.0F) * DEG;
        model.head.zRot = v[TILT] * DEG;
        if (mix < 1.0F) {
            mixVanilla(model, mix);
        }
        model.hat.copyFrom(model.head);
        SHIFTED.add(model);
    }

    /** 处决姿态此刻占几成：开头从原版淡入、交还前淡回原版 */
    private static float vanillaMix(Track tr, long t) {
        int end = tr.keys[tr.keys.length - 1].t;
        float u = Mth.clamp(Math.min(t / (float) BLEND_IN_MS, (end - t) / (float) BLEND_OUT_MS), 0.0F, 1.0F);
        return u * u * (3.0F - 2.0F * u);
    }

    private static final float[] VANILLA = new float[36];

    /**
     * 原版这一帧摆出来的姿态，在覆盖之前记下。身子和头的 z、头的 zRot、腿的左右位置原版每帧不复位
     * （这时候还是上一帧处决留下的），按原版的默认值记
     */
    private static void storeVanilla(HumanoidModel<?> model) {
        ModelPart[] parts = {model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int i = 0; i < parts.length; i++) {
            ModelPart m = parts[i];
            int o = i * 6;
            VANILLA[o] = m.x;
            VANILLA[o + 1] = m.y;
            VANILLA[o + 2] = m.z;
            VANILLA[o + 3] = m.xRot;
            VANILLA[o + 4] = m.yRot;
            VANILLA[o + 5] = m.zRot;
        }
        VANILLA[2] = 0.0F;
        VANILLA[8] = 0.0F;
        VANILLA[11] = 0.0F;
        VANILLA[24] = -LEG_X;
        VANILLA[30] = LEG_X;
    }

    private static void mixVanilla(HumanoidModel<?> model, float w) {
        ModelPart[] parts = {model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int i = 0; i < parts.length; i++) {
            ModelPart m = parts[i];
            int o = i * 6;
            m.x = Mth.lerp(w, VANILLA[o], m.x);
            m.y = Mth.lerp(w, VANILLA[o + 1], m.y);
            m.z = Mth.lerp(w, VANILLA[o + 2], m.z);
            m.xRot = lerpAngle(w, VANILLA[o + 3], m.xRot);
            m.yRot = lerpAngle(w, VANILLA[o + 4], m.yRot);
            m.zRot = lerpAngle(w, VANILLA[o + 5], m.zRot);
        }
    }

    // 拆出来的欧拉角可能和原版差一整圈，按最短的方向混
    private static float lerpAngle(float w, float from, float to) {
        float d = (to - from) % Mth.TWO_PI;
        if (d > Mth.PI) {
            d -= Mth.TWO_PI;
        } else if (d < -Mth.PI) {
            d += Mth.TWO_PI;
        }
        return from + d * w;
    }

    /**
     * 摆一条胳膊：先按表里的「抬、偏」定指向，再按「瞄 / 抓」往锚点那边拉（从这一刻的肩膀位置算），最后绕手臂自己「拧」。
     * 合成一个四元数，拆回模型要的 ZYX 欧拉角（平举时直接改 zRot 是绕身体前后轴转，不是拧胳膊）
     */
    private static void poseArm(ModelPart arm, boolean right, float raise, float yaw, float roll, float aim,
                                float ax, float ay, float az, float bodyY, boolean slerp) {
        float yRot = (right ? -yaw : yaw) * DEG + bodyY;
        float xRot = -raise * DEG;
        boolean aimed = false;
        if (aim > 0.001F) {
            float dx = ax - arm.x;
            float dy = ay - arm.y;
            float dz = az - arm.z;
            float h = Mth.sqrt(dx * dx + dz * dz);
            float aimX = -(float) Mth.atan2(h, dy);
            float aimY = (float) Mth.atan2(-dx, -dz);
            float w = Math.min(aim, 1.0F);
            if (slerp) {
                // 欧拉角线性插值在瞄到七八成时会拽一下、拧的方向还可能绕远，新写法改成球面插值
                Q.rotationY(yRot).rotateX(xRot).slerp(Q_AIM.rotationY(aimY).rotateX(aimX), w);
                aimed = true;
            } else {
                xRot = Mth.lerp(w, xRot, aimX);
                yRot = Mth.lerp(w, yRot, aimY);
            }
        }
        if (aimed) {
            Q.rotateY((right ? roll : -roll) * DEG);
        } else {
            Q.rotationY(yRot).rotateX(xRot).rotateY((right ? roll : -roll) * DEG);
        }
        // 拆成 Rz·Ry·Rx（ModelPart 的顺序）。JOML 自带的 getEulerAnglesZYX 和 rotationZYX 对不上，自己从矩阵算
        float qx = Q.x, qy = Q.y, qz = Q.z, qw = Q.w;
        float m00 = 1.0F - 2.0F * (qy * qy + qz * qz);
        float m10 = 2.0F * (qx * qy + qz * qw);
        float m20 = 2.0F * (qx * qz - qy * qw);
        float m21 = 2.0F * (qy * qz + qx * qw);
        float m22 = 1.0F - 2.0F * (qx * qx + qy * qy);
        arm.xRot = (float) Mth.atan2(m21, m22);
        arm.yRot = (float) Math.asin(Mth.clamp(-m20, -1.0F, 1.0F));
        arm.zRot = (float) Mth.atan2(m10, m00);
    }

    // ==================== 第一人称 ====================

    /** 第一人称一条胳膊此刻的拳头位置（视角空间：x 右、y 上、z 朝前，格）、刃尖额外下俯的角度、绕前臂拧的角度 */
    static final class Arm {
        double x, y, z;
        float pitchDown;
        float roll;
    }

    private static final float[] FP = new float[FN];

    // 起手时拳头在画面外右下 / 左下
    private static Key[] fpStart(boolean right) {
        float sx = right ? 0.62F : -0.62F;
        return new Key[]{f(0, SINE, sx, -0.95F, 0.40F, 0, 0)};
    }

    /** 奴仆三套的第一人称（处决默认就是第一人称看） */
    private static Key[][] thrallFirstPerson(MercyVariant v) {
        int s = v.stabMs(0);
        List<Key> r = new ArrayList<>(Arrays.asList(fpStart(true)));
        List<Key> l = new ArrayList<>(Arrays.asList(fpStart(false)));
        switch (v) {
            case THRALL_2 -> {
                // 左臂横过来勒住脖子，往后拽；刀从右边收回来，往下捅进后背
                l.add(f(300, OUT, 0.00F, -0.36F, 0.80F, -10, 0));
                l.add(f(400, SINE, 0.04F, -0.34F, 0.74F, -12, 0));
                l.add(f(650, SINE, 0.06F, -0.30F, 0.62F, -14, 0));
                l.add(f(s, SINE, 0.05F, -0.32F, 0.66F, -12, 0));
                l.add(f(1400, SINE, 0.05F, -0.33F, 0.66F, -12, 0));
                l.add(f(1520, OUT, -0.40F, -0.70F, 0.70F, 0, 0));
                l.add(f(1750, SINE, -0.55F, -0.88F, 0.50F, 0, 0));
                r.add(f(400, SINE, 0.45F, -0.52F, 0.55F, 0, -10));
                r.add(f(s - 140, SINE, 0.55F, -0.48F, 0.36F, -6, -25));
                r.add(f(s - 115, SINE, 0.57F, -0.47F, 0.33F, -8, -28));
                r.add(f(s, OUT, 0.16F, -0.50F, 0.96F, 20, 0));
                stabFollow(r, s, 0.14F, -0.52F, 1.02F, 22, 1400);
                r.add(f(1490, IN, 0.45F, -0.45F, 0.48F, -4, 10));
            }
            case THRALL_4 -> {
                // 揪住肩一刀捅穿，抽出来就翻滚：两只手都收到画面外，落地再抬起来
                l.add(f(280, OUT, -0.20F, -0.30F, 0.92F, 0, 0));
                l.add(f(s, SINE, -0.16F, -0.26F, 0.70F, 6, 0));
                l.add(f(s + 190, SINE, -0.14F, -0.24F, 0.92F, 0, 0));
                l.add(f(s + 340, IN, -0.55F, -0.95F, 0.45F, 0, 0));
                r.add(f(350, OUT, 0.42F, -0.50F, 0.58F, 0, -10));
                r.add(f(s - 140, SINE, 0.50F, -0.46F, 0.40F, -8, -25));
                r.add(f(s - 115, SINE, 0.53F, -0.45F, 0.36F, -10, -28));
                r.add(f(s, OUT, 0.13F, -0.40F, 1.00F, 12, 0));
                r.add(f(s + 80, SINE, 0.11F, -0.42F, 1.06F, 14, 5));
                r.add(f(s + 190, IN, 0.42F, -0.42F, 0.50F, -4, 10));
                r.add(f(s + 340, IN, 0.62F, -0.95F, 0.40F, 0, 0));
                r.add(f(s + 700, SINE, 0.62F, -0.95F, 0.40F, 0, 0));
            }
            case THRALL_5 -> {
                // 跃起时两手张开，头顶上往下看着它，一刀扎下去；落地前收走
                l.add(f(280, OUT, -0.45F, -0.55F, 0.70F, 0, 0));
                l.add(f(s, SINE, -0.40F, -0.60F, 0.70F, 0, 0));
                l.add(f(1200, SINE, -0.55F, -0.95F, 0.45F, 0, 0));
                r.add(f(280, OUT, 0.45F, -0.50F, 0.55F, 0, -10));
                r.add(f(s - 140, SINE, 0.38F, 0.05F, 0.45F, -10, 0));
                r.add(f(s - 115, SINE, 0.37F, 0.10F, 0.42F, -14, 0));
                r.add(f(s, OUT, 0.12F, -0.58F, 0.82F, 60, 0));
                stabFollow(r, s, 0.12F, -0.62F, 0.84F, 62, s + 180);
                r.add(f(s + 340, IN, 0.40F, -0.35F, 0.55F, -10, 0));
            }
            case THRALL_3 -> {
                // 左手按住后颈往下压；刀举过头，一下扎下去、压着拧，抽刀时左手横着推
                l.add(f(300, OUT, -0.22F, -0.52F, 0.84F, 35, 0));
                l.add(f(400, SINE, -0.22F, -0.55F, 0.80F, 38, 0));
                l.add(f(700, SINE, -0.22F, -0.60F, 0.80F, 42, 0));
                l.add(f(s, SINE, -0.22F, -0.63F, 0.80F, 45, 0));
                l.add(f(1450, SINE, -0.22F, -0.62F, 0.80F, 45, 0));
                l.add(f(1560, OUT, 0.05F, -0.50F, 1.10F, 20, 0));
                l.add(f(1780, SINE, -0.45F, -0.85F, 0.65F, 0, 0));
                r.add(f(400, SINE, 0.45F, -0.55F, 0.55F, 0, -10));
                r.add(f(s - 140, SINE, 0.38F, 0.05F, 0.45F, -10, 0));
                r.add(f(s - 115, SINE, 0.37F, 0.10F, 0.42F, -14, 0));
                r.add(f(s, OUT, 0.12F, -0.58F, 0.82F, 60, 0));
                stabFollow(r, s, 0.12F, -0.62F, 0.84F, 62, 1450);
                r.add(f(1530, IN, 0.40F, -0.35F, 0.55F, -10, 0));
            }
            default -> {
                // 揪住肩往回拽，把它往刀上送；拔刀的同时一把推开
                l.add(f(280, OUT, -0.20F, -0.30F, 0.92F, 0, 0));
                l.add(f(350, SINE, -0.18F, -0.32F, 0.86F, 10, 0));
                l.add(f(600, SINE, -0.17F, -0.24F, 0.76F, 6, 0));
                l.add(f(s, SINE, -0.16F, -0.26F, 0.70F, 6, 0));
                l.add(f(1350, SINE, -0.16F, -0.27F, 0.71F, 6, 0));
                l.add(f(1450, OUT, -0.10F, -0.22F, 1.18F, -5, 0));
                l.add(f(1700, SINE, -0.45F, -0.75F, 0.70F, 0, 0));
                r.add(f(350, OUT, 0.42F, -0.50F, 0.58F, 0, -10));
                r.add(f(s - 140, SINE, 0.50F, -0.46F, 0.40F, -8, -25));
                r.add(f(s - 115, SINE, 0.53F, -0.45F, 0.36F, -10, -28));
                r.add(f(s, OUT, 0.13F, -0.40F, 1.00F, 12, 0));
                stabFollow(r, s, 0.11F, -0.42F, 1.06F, 14, 1350);
                r.add(f(1440, IN, 0.42F, -0.42F, 0.50F, -4, 10));
            }
        }
        // 甩刀、放下
        r.add(f(1680, OUT, 0.60F, -0.72F, 0.55F, 10, 30));
        r.add(f(1900, SINE, 0.55F, -0.70F, 0.45F, 4, 15));
        l.add(f(1900, SINE, -0.55F, -0.88F, 0.50F, 0, 0));
        return new Key[][]{r.toArray(new Key[0]), l.toArray(new Key[0])};
    }

    /** 刺中以后：往里再送一点、拧刀、顶着 */
    private static void stabFollow(List<Key> r, int s, float x, float y, float z, float pitch, int holdUntil) {
        r.add(f(s + 80, SINE, x, y, z, pitch, 5));
        r.add(f(s + 280, SINE, x + 0.01F, y - 0.01F, z - 0.02F, pitch, 45));
        r.add(f(holdUntil, SINE, x + 0.01F, y - 0.01F, z - 0.03F, pitch, 40));
    }

    /**
     * 玄骸的第一人称：正常只演到 400 ms 切镜之前（伸手揪住它、刀抬起来）；机位放不下退回玩家镜头时，
     * 后面每一刀照同一个套路接着演
     */
    private static Key[][] lichFirstPerson(MercyPlan.Plan p, int[] stabs) {
        List<Key> r = new ArrayList<>(Arrays.asList(fpStart(true)));
        List<Key> l = new ArrayList<>(Arrays.asList(fpStart(false)));
        l.add(f(260, OUT, -0.18F, -0.20F, 0.95F, 0, 0));
        l.add(f(330, SINE, -0.16F, -0.24F, 0.88F, 8, 0));
        l.add(f(420, SINE, -0.16F, -0.20F, 0.80F, 6, 0));
        r.add(f(400, SINE, 0.42F, -0.50F, 0.56F, 0, -10));
        MercyTimeline.Outcome oc = MercyTimeline.outcome(p);
        for (int i = 0; i < stabs.length; i++) {
            int s = stabs[i];
            boolean last = i == stabs.length - 1;
            r.add(f(s - 200, SINE, 0.50F, -0.46F, 0.40F, -8, -25));
            r.add(f(s - 115, SINE, 0.53F, -0.45F, 0.36F, -10, -28));
            r.add(f(s, OUT, 0.12F, -0.36F, 1.00F, 6, 0));
            if (last && (oc == MercyTimeline.Outcome.FAIL || oc == MercyTimeline.Outcome.ADVANCE)) {
                int free = MercyTimeline.breakFreeMs(p);
                stabFollow(r, s, 0.10F, -0.37F, 1.06F, 8, free);
                // 被震开：刀被挣出来，两只手往外一甩
                r.add(f(free + 80, OUT, 0.62F, -0.30F, 0.30F, -20, 0));
                l.add(f(free, SINE, -0.16F, -0.21F, 0.80F, 6, 0));
                l.add(f(free + 80, OUT, -0.50F, -0.10F, 0.55F, -20, 0));
                r.add(f(free + 700, SINE, 0.40F, -0.48F, 0.55F, 0, -10));
                l.add(f(free + 700, SINE, -0.45F, -0.70F, 0.55F, 0, 0));
            } else {
                stabFollow(r, s, 0.10F, -0.37F, 1.06F, 8, s + 460);
                r.add(f(s + 620, IN, 0.45F, -0.42F, 0.45F, -6, -15));
            }
        }
        if (oc == MercyTimeline.Outcome.SUCCESS || oc == MercyTimeline.Outcome.CONVERT) {
            int end = stabs[stabs.length - 1];
            l.add(f(end + 330, SINE, -0.45F, -0.70F, 0.60F, 0, 0));
            r.add(f(end + 900, OUT, 0.60F, -0.72F, 0.55F, 10, 30));
            r.add(f(end + 1200, SINE, 0.50F, -0.66F, 0.50F, 4, 10));
        }
        return new Key[][]{r.toArray(new Key[0]), l.toArray(new Key[0])};
    }

    /** 第一人称：算右臂（{@code right}）或左臂此刻的位置 */
    static void firstPerson(MercyClient.Exec e, long t, boolean right, Arm out) {
        Track tr = e.pose;
        sample(right ? tr.fpRight : tr.fpLeft, t, FP);
        float x = FP[FX], y = FP[FY], z = FP[FZ], pitch = FP[FPITCH], roll = FP[FROLL];
        for (int i = 0; i < tr.stabs.length; i++) {
            long dt = t - tr.stabs[i];
            float w = tr.weight[i];
            float j = jolt(dt);
            float tm = tremor(dt);
            if (right) {
                // 刺中：拳头往里多送一下再被顶回来；拧刀时手上抖
                z += 0.05F * w * j;
                y -= 0.015F * w * j;
                x += 0.005F * tm;
                y += 0.004F * tm;
                roll += 3.0F * tm;
            } else {
                // 左手往回拽
                z -= 0.03F * w * j;
                y += 0.01F * w * j;
            }
        }
        float s = strain(tr, t);
        if (right) {
            x += 0.004F * s;
            roll += 2.0F * s;
        }
        y += 0.006F * breath(tr, t, 0, right ? 0.0F : 0.6F);
        int exitEnd = e.handbackMs - 200;
        int exitStart = exitEnd - 400;
        if (t > exitStart) {
            // 交还前往下退出画面
            y -= 0.6F * sineEase(Math.min(1.0F, (t - exitStart) / (float) (exitEnd - exitStart)));
        }
        out.x = x;
        out.y = y;
        out.z = z;
        out.pitchDown = pitch;
        out.roll = roll;
    }

    private static float sineEase(float u) {
        float x = Mth.clamp(u, 0.0F, 1.0F);
        return (float) (0.5D - 0.5D * Math.cos(Math.PI * x));
    }
}
