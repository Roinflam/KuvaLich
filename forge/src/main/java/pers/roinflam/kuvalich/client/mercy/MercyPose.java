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
import org.joml.Quaternionf;
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
 * 冲过一点再回来；切镜时直接摆好的帧不插值。手臂按「先定指向、再绕自己转」算成四元数，再拆回模型要的 ZYX 欧拉角。</p>
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

    private static final int SINE = 0, OUT = 1, SNAP = 2, IN = 3, BACK = 4;
    /** 出刀帧从上一帧起多久到位 */
    private static final int OUT_MS = 110;
    private static final float NA = Float.NaN;
    private static final float DEG = (float) Math.PI / 180.0F;
    private static final double RAD_TO_DEG = 180.0D / Math.PI;

    /** 玄骸三刀刺入的时刻（三套一样） */
    private static final int S1 = MercyVariant.LICH_A.stabMs(0), S2 = MercyVariant.LICH_A.stabMs(1),
            S3 = MercyVariant.LICH_A.stabMs(2);

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

    /** 正面直刺：左手揪住它的肩颈；第三刀举过头往下扎，两脚蹬在它胸口往后空翻 */
    private static List<Key> lichA() {
        return new ArrayList<>(List.of(
                k(0, SINE, start()),
                k(130, SINE, gather()),
                k(310, BACK, p().tw(-10).ln(15).sq(0.1F).sh(1.5F).r(30, -30, 0).l(110, 20, 0).grip(1)
                        .legs(-16, 18).hd(0, 0)),
                // 拽过来：上身往回收一点
                k(420, SINE, p().tw(-8).ln(11).sq(0.05F).sh(0.8F).r(36, -28, 0).hd(-2, 0)),
                // 蓄力：刀尖对着它，右肩连胳膊一起往后收，再往下压一点
                k(S1 - 200, SINE, p().tw(-26).ln(9).sq(0.15F).sh(-0.3F).r(80, -6, -15).aim(0.7F).pull(3.5F).hd(0, -3)),
                k(S1 - 115, SINE, p().tw(-30).ln(7).sq(0.2F).sh(-0.6F).r(80, -8, -20).pull(4.5F)),
                k(S1, OUT, p().tw(16).ln(17).sq(0.05F).sh(2.5F).r(85, 5, 0).aim(1).pull(-1.5F).legs(-20, 20).hd(3, 0)),
                k(S1 + 80, SINE, p().tw(20).ln(19).sh(3).hd(5, 2)),
                // 拧刀
                k(S1 + 300, SINE, p().tw(22).ln(18).sq(0.15F).r(NA, NA, 40).hd(4, 4)),
                k(S1 + 460, SINE, p().tw(20).ln(17).sq(0.1F).r(NA, NA, 32)),
                // 顺着原路拔出来，收回去蓄下一刀
                k(S1 + 620, IN, p().tw(-24).ln(9).sq(0.1F).sh(0.3F).r(80, -8, -15).aim(0.6F).pull(4).hd(0, -2)),
                k(S2 - 115, SINE, p().tw(-30).ln(7).sq(0.2F).sh(-0.3F).r(80, -10, -22).pull(4.5F)),
                // 第二刀多上半步
                k(S2, OUT, p().tw(20).ln(21).sq(0.05F).sh(3.2F).r(88, 6, 0).aim(1).pull(-2).reach(2).legs(-22, 24)
                        .hd(4, 0)),
                k(S2 + 80, SINE, p().tw(24).ln(23).sh(3.6F).hd(6, -2)),
                k(S2 + 300, SINE, p().tw(26).ln(22).sq(0.15F).r(NA, NA, -40).hd(5, -4)),
                k(S2 + 460, SINE, p().tw(24).ln(21).r(NA, NA, -34)),
                k(S2 + 620, IN, p().tw(-24).ln(6).sq(0.1F).sh(0.5F).r(75, -20, 0).aim(0.3F).pull(3).reach(4).hd(-2, 0)),
                // 第三刀：举过头
                k(S3 - 115, SINE, p().tw(-34).ln(0).sq(0.3F).sh(-1).r(155, -12, 0).aim(0).pull(1).hd(-6, 0)),
                k(S3, OUT, p().tw(26).ln(30).sq(0.15F).sh(4).r(80, 8, 0).aim(1).pull(-1).reach(7).legs(-26, 28)
                        .hd(8, 0)),
                k(S3 + 90, SINE, p().tw(30).ln(33).sq(0.3F).sh(4.5F).hd(10, 0))));
    }

    private static List<Key> lichAFull() {
        List<Key> table = lichA();
        table.addAll(kickOff(S3));
        return table;
    }

    /** 背后锁颈：左臂从后面扣住它的脖子；第二刀把它的头往后扳着捅，拔刀后原地拧身转满一圈，借着转势捅第三刀 */
    private static List<Key> lichB() {
        return new ArrayList<>(List.of(
                k(0, SINE, start()),
                k(130, SINE, gather()),
                k(310, BACK, p().tw(-8).ln(16).sq(0.1F).sh(1.5F).r(30, -30, 0).l(118, 15, 0).grip(1)
                        .legs(-14, 16).hd(2, 0)),
                // 往后一勒
                k(420, SINE, p().tw(-6).ln(12).sq(0.05F).sh(0.6F).hd(0, 0)),
                k(S1 - 200, SINE, p().tw(-24).ln(11).sq(0.15F).sh(-0.3F).r(78, -10, -20).aim(0.6F).pull(3.5F).hd(0, 3)),
                k(S1 - 115, SINE, p().tw(-28).ln(9).sq(0.2F).sh(-0.5F).r(78, -12, -25).pull(4.5F)),
                k(S1, OUT, p().tw(14).ln(18).sq(0.05F).sh(2).r(82, 4, 0).aim(1).pull(-1.5F).legs(-18, 18).hd(4, 0)),
                k(S1 + 80, SINE, p().tw(18).ln(20).sh(2.6F).hd(6, 0)),
                k(S1 + 300, SINE, p().tw(20).ln(19).sq(0.15F).r(NA, NA, 40).hd(4, 3)),
                k(S1 + 460, SINE, p().tw(18).ln(18).sq(0.1F).r(NA, NA, 32)),
                k(S1 + 620, IN, p().tw(-22).ln(9).sq(0.1F).sh(0.3F).r(78, -12, -15).aim(0.5F).pull(4).hd(0, 0)),
                // 左手把它的头往后扳
                k(S2 - 115, SINE, p().tw(-30).ln(7).sq(0.2F).sh(-0.3F).r(78, -14, -22).pull(4.5F).reach(-2).hd(-2, 0)),
                k(S2, OUT, p().tw(18).ln(20).sq(0.05F).sh(2.6F).r(86, 5, 0).aim(1).pull(-2).reach(-1).legs(-22, 22)
                        .hd(4, 0)),
                k(S2 + 80, SINE, p().tw(22).ln(22).sh(3).hd(6, -2)),
                k(S2 + 300, SINE, p().tw(24).ln(21).sq(0.15F).r(NA, NA, -40).hd(5, -3)),
                k(S2 + 460, SINE, p().tw(22).ln(20).r(NA, NA, -34)),
                k(S2 + 620, IN, p().tw(-26).ln(8).sq(0.15F).sh(0).r(55, -50, -10).aim(0).pull(2).reach(0).hd(0, 0)),
                // 最大的一次蓄力：整条胳膊甩到身侧后方
                k(S3 - 115, SINE, p().tw(-36).ln(10).sq(0.3F).sh(-1).r(50, -62, -25).pull(1).hd(-2, 0)),
                k(S3, OUT, p().tw(28).ln(32).sq(0.1F).sh(4.5F).r(90, 8, 0).aim(1).pull(-1.5F).reach(5).legs(-26, 30)
                        .hd(8, 0)),
                k(S3 + 90, SINE, p().tw(32).ln(33).sq(0.25F).sh(5).hd(10, 0)),
                // 玄骸往前扑倒：松手，跟着往前压
                k(S3 + 280, SINE, p().tw(20).ln(34).sq(0.35F).sh(5).r(60, 6, 0).aim(0).pull(0).l(60, 5, 0).grip(0)
                        .legs(-24, 30).hd(14, 0)),
                k(S3 + 600, SINE, p().tw(8).ln(16).sq(0.15F).sh(2).r(42, -10, 0).l(28, 0, 0).legs(-14, 14)
                        .hd(4, 0)),
                k(S3 + 900, OUT, flick()),
                k(S3 + 1250, SINE, ready())));
    }

    /** 贴身上挑：矮身冲进怀里、左掌顶住胸口；每一刀从低处起身往上挑，第三刀整个人顶起来 */
    private static List<Key> lichC() {
        return new ArrayList<>(List.of(
                k(0, SINE, start()),
                k(130, SINE, p().tw(-4).ln(12).sq(0.45F).sh(-0.3F).r(22, -35, 0).l(40, 10, 0).legs(-6, 8).hd(2, 0)),
                k(310, BACK, p().tw(-6).ln(26).sq(1).sh(2.2F).r(14, -30, 0).l(95, 15, 0).grip(1).legs(-18, 18)
                        .hd(-4, 0)),
                k(420, SINE, p().ln(27).sh(1.8F).hd(-5, 0)),
                k(S1 - 200, SINE, p().tw(-18).ln(30).sq(1).sh(1.2F).r(8, -35, -15).hd(-6, 0)),
                k(S1 - 115, SINE, p().tw(-20).ln(32).r(4, -38, -20)),
                k(S1, OUT, p().tw(12).ln(14).sq(0.55F).sh(3).r(140, 6, 0).aim(1).legs(-20, 22).hd(-10, 0)),
                k(S1 + 80, SINE, p().tw(14).ln(12).sq(0.5F).sh(3.4F).hd(-12, 0)),
                k(S1 + 300, SINE, p().tw(16).ln(13).sq(0.55F).r(NA, NA, 40).hd(-10, 3)),
                k(S1 + 460, SINE, p().tw(14).ln(14).sq(0.6F).r(NA, NA, 32)),
                // 拔出来，又矮下去
                k(S1 + 620, IN, p().tw(-16).ln(28).sq(1).sh(1.6F).r(10, -32, -10).aim(0).hd(-6, 0)),
                k(S2 - 115, SINE, p().tw(-22).ln(31).sq(1).sh(1.2F).r(4, -38, -18)),
                k(S2, OUT, p().tw(16).ln(10).sq(0.5F).sh(3.4F).r(146, 6, 0).aim(1).reach(1).legs(-22, 24).hd(-12, 0)),
                k(S2 + 80, SINE, p().tw(18).ln(9).sq(0.45F).sh(3.8F).hd(-14, 0)),
                k(S2 + 300, SINE, p().tw(20).ln(10).sq(0.5F).r(NA, NA, -40).hd(-12, -3)),
                k(S2 + 460, SINE, p().tw(18).ln(11).sq(0.55F).r(NA, NA, -34)),
                k(S2 + 620, IN, p().tw(-18).ln(30).sq(1).sh(1.4F).r(6, -34, -10).aim(0).reach(2).hd(-6, 0)),
                // 压到最低
                k(S3 - 115, SINE, p().tw(-26).ln(35).sq(1).sh(0.6F).r(-5, -40, -20).hd(-8, 0)),
                // 整个人顶起来
                k(S3, OUT, p().tw(20).ln(2).sq(0.15F).sh(4).r(155, 6, 0).aim(1).reach(3).legs(-26, 30).hd(-16, 0)),
                k(S3 + 90, SINE, p().tw(22).ln(-2).sq(0).sh(4.5F).hd(-18, 0))));
    }

    private static List<Key> lichCFull() {
        List<Key> table = lichC();
        table.addAll(kickOff(S3));
        return table;
    }

    /** 翻滚绕背：前两刀和正面直刺一样；第二刀顺势撕出来，扑出去翻滚到它身后，反手从背后捅第三刀 */
    private static List<Key> lichD() {
        List<Key> table = lichA();
        table.removeIf(key -> key.t >= S2 + 100);
        table.addAll(List.of(
                // 撕开刀口，左手一撑它的肩
                k(S2 + 170, IN, p().tw(-28).ln(10).sq(0.25F).sh(0.6F).r(66, -52, 0).aim(0).pull(2).l(96, -10, 0).grip(0)
                        .reach(0).legs(-14, 18).hd(0, 0)),
                k(S2 + 260, SINE, p().tw(-10).ln(26).sq(0.85F).sh(0).r(44, -30, 0).pull(0).l(40, 0, 0).legs(14, -10)
                        .hd(6, 0)),
                k(S2 + 360, OUT, tucked()),
                k(S2 + 620, SINE, tucked()),
                k(S3 - 180, OUT, landed()),
                // 落地就蓄力，左手扣住它的后腰
                k(S3 - 115, SINE, p().tw(-30).ln(10).sq(0.35F).sh(-0.5F).r(80, -10, -20).aim(0.7F).pull(4.5F).l(104, 18, 0)
                        .grip(0.7F).legs(-14, 18).hd(0, 0)),
                k(S3, OUT, p().tw(24).ln(26).sq(0.1F).sh(4).r(86, 8, 0).aim(1).pull(-1.5F).reach(3).legs(-24, 28)
                        .hd(6, 0)),
                k(S3 + 90, SINE, p().tw(30).ln(30).sq(0.25F).sh(4.5F).hd(8, 0)),
                // 玄骸往前扑倒：松手，跟上半步
                k(S3 + 330, SINE, p().tw(16).ln(26).sq(0.3F).sh(3).r(60, 6, 0).aim(0).pull(0).l(50, 5, 0).grip(0).reach(0)
                        .legs(-22, 26).hd(10, 0)),
                k(S3 + 620, SINE, p().tw(8).ln(14).sq(0.15F).sh(1.5F).r(42, -10, 0).l(28, 0, 0).legs(-14, 14)
                        .hd(4, 0)),
                k(S3 + 900, OUT, flick()),
                k(S3 + 1250, SINE, ready())));
        return table;
    }

    /**
     * 踏背下刺：前一刀和背后锁颈一样，从背后扣住它捅进后腰；拔刀顺势把刀举过头一蹬，跳上它弯下去的后背蹲住，左手揪住肩甲，
     * 往脚下的后颈扎第二刀；拔出来整个人化作一道红光（这一段不画人），从它头顶闪到正面，现身就是蓄满的一刀，捅进胸口，
     * 两脚蹬着它往后空翻
     */
    private static List<Key> lichE() {
        List<Key> table = lichB();
        table.removeIf(key -> key.t >= S1 + 500);
        table.addAll(List.of(
                // 拔刀，顺势把刀举过头，往下一蹲蓄力
                k(S1 + 560, IN, p().tw(-10).ln(24).sq(0.9F).sh(0.2F).r(150, -14, 0).aim(0).pull(1).l(60, 10, 0).grip(0)
                        .reach(0).legs(-16, 20).hd(-6, 0)),
                // 蹬地起跳：腿蹬直，刀举在头顶
                k(S1 + 650, OUT, p().tw(-4).ln(-6).sq(0).sh(0).r(170, -8, 0).l(150, 10, 0).legs(-30, 10).hd(-14, 0)),
                // 空中收腿
                k(S1 + 760, SINE, p().tw(-6).ln(14).sq(0.6F).r(172, -8, 0).l(110, 10, 0).legs(40, 60).hd(-6, 0)),
                // 落在背上蹲住，左手揪住肩甲，刀还举着
                k(S2 - 60, IN, p().tw(-12).ln(20).sq(1).sh(0.5F).r(172, -8, 0).aim(0).pull(1).l(92, 10, 0).grip(1)
                        .legs(-26, 36).hd(-8, 0)),
                // 第二刀往脚下的后颈扎，整个人压下去
                k(S2, OUT, p().tw(14).ln(38).sq(1).sh(1.5F).r(96, 4, 0).aim(1).pull(-1.5F).hd(14, 0)),
                k(S2 + 80, SINE, p().tw(16).ln(41).hd(16, 0)),
                k(S2 + 300, SINE, p().tw(18).r(NA, NA, 40).hd(14, 3)),
                // 拔出来，松开肩甲
                k(S2 + 420, IN, p().tw(-6).ln(16).sq(0.8F).sh(0.3F).r(150, -12, 0).aim(0).pull(1).l(70, 10, 0).grip(0)
                        .hd(-4, 0)),
                // 在正面现身：已经是蓄满的架势
                k(MercyTimeline.E_APPEAR_MS, SNAP, p().tw(-30).ln(9).sq(0.25F).sh(-0.4F).r(80, -10, -20).aim(0.6F)
                        .pull(4.5F).l(60, 10, 0).grip(0).reach(0).legs(-16, 20).hd(0, -2)),
                k(S3 - 115, SINE, p().tw(-32).ln(8).sq(0.3F).sh(-0.6F).r(80, -10, -24).pull(4.8F)),
                k(S3, OUT, p().tw(24).ln(22).sq(0.05F).sh(3.6F).r(88, 6, 0).aim(1).pull(-2).reach(3).legs(-24, 26)
                        .hd(5, 0)),
                k(S3 + 90, SINE, p().tw(28).ln(25).sh(4).hd(7, 0))));
        table.addAll(kickOff(S3));
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
                k(MercyTimeline.THRALL_END_MS, SINE, zero()));
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
                k(MercyTimeline.THRALL_END_MS, SINE, zero()));
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
                k(MercyTimeline.THRALL_END_MS, SINE, zero()));
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
                k(MercyTimeline.THRALL_END_MS, SINE, zero()));
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
                k(MercyTimeline.THRALL_END_MS, SINE, zero()));
    }

    private static List<Key> tableOf(MercyVariant v) {
        return switch (v) {
            case LICH_A -> lichAFull();
            case LICH_B -> lichB();
            case LICH_C -> lichCFull();
            case LICH_D -> lichD();
            case LICH_E -> lichE();
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

    /** 一刀的脚下：蓄力往后坐一点，出刀往前送，拔刀退回来 */
    private static void lunge(List<Key> b, int s, float reach) {
        b.add(k(s - 115, SINE, m().dist(0.12F).up(-0.02F)));
        b.add(k(s, OUT, m().dist(-reach).up(-0.06F)));
        b.add(k(s + 80, SINE, m().dist(-reach - 0.04F)));
        b.add(k(s + 460, SINE, m().up(-0.03F)));
        b.add(k(s + 620, IN, m().dist(0).up(0)));
    }

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

    private static List<Key> bodyA() {
        List<Key> b = startedBody();
        lunge(b, S1, 0.22F);
        lunge(b, S2, 0.32F);
        b.add(k(S3 - 115, SINE, m().dist(0.2F)));
        b.add(k(S3, OUT, m().dist(-0.45F).up(-0.06F)));
        b.add(k(S3 + 90, SINE, m().dist(-0.5F)));
        hop(b, S3, -0.5F, 0.0F, 1.5F);
        return b;
    }

    private static List<Key> bodyB() {
        List<Key> b = startedBody();
        lunge(b, S1, 0.2F);
        lunge(b, S2, 0.3F);
        b.add(k(S2 + 640, SINE, m().dist(0.1F)));
        b.add(k(S3 - 40, S2 + 640, IN, m().dist(-0.1F).yaw(-360)));
        b.add(k(S3, OUT, m().dist(-0.5F).up(-0.06F)));
        b.add(k(S3 + 90, SINE, m().dist(-0.55F)));
        b.add(k(S3 + 280, SINE, m().dist(-0.7F).up(0)));
        b.add(k(S3 + 600, SINE, m().dist(-0.45F)));
        return b;
    }

    private static List<Key> bodyC() {
        List<Key> b = startedBody();
        lunge(b, S1, 0.2F);
        lunge(b, S2, 0.28F);
        b.add(k(S3 - 115, SINE, m().dist(-0.05F).up(-0.1F)));
        b.add(k(S3, OUT, m().dist(-0.4F).up(0.3F)));
        hop(b, S3, -0.35F, 0.9F, 1.5F);
        return b;
    }

    private static List<Key> bodyD() {
        List<Key> b = startedBody();
        lunge(b, S1, 0.22F);
        b.add(k(S2 - 115, SINE, m().dist(0.12F)));
        b.add(k(S2, OUT, m().dist(-0.32F).up(-0.06F)));
        b.add(k(S2 + 80, SINE, m().dist(-0.36F)));
        b.add(k(S2 + 170, SINE, m().dist(-0.22F).up(0)));
        // 蹬地，侧身扑出去，沿着它转半圈
        b.add(k(S2 + 260, SINE, m().ang(10).dist(0).yaw(35)));
        b.add(k(S2 + 360, OUT, m().ang(42).dist(0.2F).up(0.22F).yaw(80).flip(45)));
        b.add(k(S2 + 620, SINE, m().ang(135).dist(0.5F).up(0.2F).flip(310)));
        // 落地滑一小段，转回来对着它的后背。第二刀以后它往后挫了半格，正好挫到这一侧，所以比正面那几刀站得远半格
        b.add(k(S3 - 180, OUT, m().ang(170).dist(0.7F).up(0).yaw(35).flip(360)));
        b.add(k(S3 - 115, SINE, m().ang(180).dist(0.8F).yaw(0)));
        b.add(k(S3, OUT, m().dist(0.25F).up(-0.06F)));
        b.add(k(S3 + 90, SINE, m().dist(0.15F)));
        // 它往前扑倒，跟上去半步
        b.add(k(S3 + 330, SINE, m().dist(-0.2F).up(0)));
        b.add(k(S3 + 620, SINE, m().dist(0.0F)));
        return b;
    }

    private static List<Key> bodyE() {
        List<Key> b = startedBody();
        b.add(k(S1 - 115, SINE, m().dist(0.12F).up(-0.02F)));
        b.add(k(S1, OUT, m().dist(-0.24F).up(-0.06F)));
        b.add(k(S1 + 80, SINE, m().dist(-0.28F)));
        b.add(k(S1 + 460, SINE, m().dist(-0.18F).up(-0.03F)));
        // 蹲下、蹬地，跳上它的后背
        b.add(k(S1 + 560, SINE, m().dist(-0.1F).up(-0.12F)));
        b.add(k(S1 + 650, OUT, m().dist(-0.5F).up(1.0F)));
        b.add(k(S1 + 760, SINE, m().dist(-1.2F).up(E_BACK_UP + 0.4F)));
        b.add(k(S2 - 60, IN, m().dist(E_BACK_DIST).up(E_BACK_UP)));
        b.add(k(S2, OUT, m().up(E_BACK_UP - 0.06F)));
        b.add(k(S2 + 300, SINE, m().up(E_BACK_UP - 0.02F)));
        b.add(k(S2 + 420, SINE, m().up(E_BACK_UP + 0.05F)));
        // 化作红光：人不画，这段时间从后背挪到它正面
        b.add(k(MercyTimeline.E_BLINK_MS, SNAP, m().hide(true)));
        b.add(k(MercyTimeline.E_APPEAR_MS - 10, SINE, m().ang(180).dist(E_FRONT_DIST).up(0)));
        b.add(k(MercyTimeline.E_APPEAR_MS, SNAP, m().hide(false)));
        // 第三刀扑上去一大步
        b.add(k(S3 - 115, SINE, m().dist(E_FRONT_DIST + 0.05F).up(-0.02F)));
        b.add(k(S3, OUT, m().dist(E_FRONT_DIST - 0.4F).up(-0.06F)));
        b.add(k(S3 + 90, SINE, m().dist(E_FRONT_DIST - 0.45F)));
        hop(b, S3, E_FRONT_DIST - 0.4F, 0.0F, E_FRONT_DIST - 0.4F + 1.5F);
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
        b.add(k(s + 840, SINE, m().ang(180).dist(0).yaw(0)));
        return b;
    }

    /** 飞跃下刺：从站位直直翻过它头顶，落在身后，再转回来看着它倒下 */
    private static List<Key> bodyT5() {
        float across = -MercyVariant.THRALL_5.standDist;
        List<Key> b = startedBody();
        b.add(k(120, SINE, m().dist(0.15F)));
        b.add(k(280, OUT, m().dist(across * 0.24F).up(0.55F).flip(30)));
        b.add(k(560, SINE, m().dist(across * 0.5F).up(1.85F).flip(130)));
        // 正好翻到头朝下，手臂朝下对着它的后颈（它面向处决者，后颈在中轴偏处决者这一侧）
        b.add(k(800, SINE, m().dist(across * 0.72F).up(2.2F).flip(190)));
        b.add(k(1040, SINE, m().dist(across * 1.3F).up(1.6F).flip(268)));
        b.add(k(1280, IN, m().dist(across * 1.92F).up(0).flip(360)));
        b.add(k(1340, OUT, m().dist(across * 2.0F)));
        b.add(k(1620, 1380, OUT, m().yaw(180)));
        return b;
    }

    private static List<Key> bodyOf(MercyVariant v) {
        return switch (v) {
            case LICH_A -> bodyA();
            case LICH_B -> bodyB();
            case LICH_C -> bodyC();
            case LICH_D -> bodyD();
            case LICH_E -> bodyE();
            case THRALL_1 -> bodyThrall(v.stabMs(0), 0.2F, 1440);
            case THRALL_2 -> bodyThrall(v.stabMs(0), 0.18F, 1500);
            case THRALL_3 -> bodyThrall(v.stabMs(0), 0.12F, 1530);
            case THRALL_4 -> bodyT4();
            case THRALL_5 -> bodyT5();
        };
    }

    /**
     * 判错：被挣开甩回站位外 1.5 格（服务端同时把人推到那里）。人不在站位上时是整个人被甩出去翻一圈：
     * 踏背下刺在背上被掀下来往后翻；在它正面被一把抡起来，从它头顶甩回站位那一侧（沿着中轴越过它，所以「距」是负的）
     */
    private static void thrown(List<Key> body, MercyVariant v, int failK, int t0) {
        if (v == MercyVariant.LICH_E && failK == 2) {
            // 先被掀离它的后背（它马上要站起来，人不能还压在它身上），再往后翻一圈落地
            body.add(k(t0 + 150, OUT, m().dist(0.3F).up(E_BACK_UP + 0.6F).flip(-150)));
            body.add(k(t0 + 440, IN, m().dist(1.5F).up(0).flip(-360)));
        } else if (v == MercyVariant.LICH_E && failK == 3) {
            float across = -2.0F * v.standDist - 1.5F;
            body.add(k(t0 + 200, OUT, m().dist(-v.standDist - 0.1F).up(E_BACK_UP + 1.0F).yaw(90).flip(160)));
            body.add(k(t0 + 480, IN, m().dist(across).up(0).yaw(180).flip(360)));
        } else if (v == MercyVariant.LICH_D && failK == 3) {
            body.add(k(t0 + 200, OUT, m().ang(95).dist(0.6F).up(1.1F).yaw(190).flip(150)));
            body.add(k(t0 + 460, IN, m().ang(0).dist(1.5F).up(0).yaw(360).flip(360)));
        } else {
            body.add(k(t0 + 300, OUT, m().dist(1.5F).up(0)));
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

    /** 按计划位拼出这一场的姿态轨道 */
    static Track track(MercyPlan.Plan p, boolean shortOutro) {
        MercyTimeline.Outcome oc = MercyTimeline.outcome(p);
        MercyVariant v = p.variant();
        List<Key> table = tableOf(v);
        List<Key> moves = bodyOf(v);
        List<Key> body = new ArrayList<>();
        int[] stabs = MercyTimeline.stabTimes(p);
        float[] weight = new float[stabs.length];
        for (int i = 0; i < stabs.length; i++) {
            weight[i] = v.thrall ? 1.2F : (MercyTimeline.isHeavyStab(p, i) ? 1.6F : 1.0F);
        }
        int strainFrom = -1, strainTo = -1;
        List<Key> out = new ArrayList<>();
        switch (oc) {
            case SUCCESS -> {
                out.addAll(table);
                // 特写和收尾按站位摆，切镜头那一刻人回到站位，到交还都不再动
                body.addAll(moves);
                body.add(k(MercyTimeline.CLOSEUP_MS, SNAP, still()));
                body.add(k(MercyTimeline.handbackMs(p, shortOutro), SINE, m()));
                // 切跪地特写那一刻直接摆成刀扎进去、刃身露一截（特写里要拍到这只手和刀）；特写里刀慢慢往里再送一点
                Row closeup = closeup(v);
                out.add(k(MercyTimeline.CLOSEUP_MS, SNAP, closeup));
                Row pressed = closeup(v);
                pressed.v[R_RAISE] += 2.0F;
                pressed.v[TWIST] += 2.0F;
                pressed.v[L_RAISE] -= 2.0F;
                if (shortOutro) {
                    // 特写完直接交还：保持到 7320，再 400 ms 回 0（刀跟着抽出来）
                    out.add(k(MercyTimeline.THIRD_MS - 1, SINE, pressed));
                    out.add(k(MercyTimeline.THIRD_MS + 400, MercyTimeline.THIRD_MS, SINE, zero()));
                } else {
                    out.add(k(MercyTimeline.THIRD_MS - 1, SINE, pressed));
                    // 切第三人称那一刻刀已整根没入，压着等抽刀
                    out.add(k(MercyTimeline.THIRD_MS, SNAP, plunged()));
                    out.add(k(MercyTimeline.DRAW_OUT_MS, SINE, plunged().ln(21).sh(0.5F)));
                    // 抽刀：往回一拽、松开头盔，甩一下刀，交还前回到站姿
                    out.add(k(MercyTimeline.DRAW_OUT_MS + 110, MercyTimeline.DRAW_OUT_MS, OUT,
                            p().tw(-18).ln(6).sh(-1).r(42, -35, -10).l(60, 10, 0).legs(-8, 14).hd(-2, 0)));
                    out.add(k(MercyTimeline.DRAW_OUT_MS + 370, OUT, flick()));
                    out.add(k(MercyTimeline.SUCCESS_END_MS - 200, SINE, ready()));
                    out.add(k(MercyTimeline.SUCCESS_END_MS, MercyTimeline.SUCCESS_END_MS - 200, SINE, zero()));
                }
            }
            case FAIL -> {
                int stabK = stabs[stabs.length - 1];
                int g = MercyTimeline.failFillMs(p);
                for (Key key : table) {
                    if (key.t <= stabK + 90) {
                        out.add(key);
                    }
                }
                for (Key key : moves) {
                    if (key.t <= stabK + 90) {
                        body.add(key);
                    }
                }
                body.add(k(g + MercyTimeline.FAIL_BREAKFREE, SINE, m()));
                thrown(body, v, stabs.length, g + MercyTimeline.FAIL_BREAKFREE);
                body.add(k(MercyTimeline.handbackMs(p, shortOutro), SINE, m()));
                // 判错那一刀拧不动：顶着、手越抖越厉害（抖是叠上去的），直到被挣开
                Row stuck = p().r(NA, NA, 18);
                out.add(k(stabK + 300, SINE, stuck));
                out.add(k(g + MercyTimeline.FAIL_BREAKFREE - 60, SINE, p()));
                strainFrom = stabK + 120;
                strainTo = g + MercyTimeline.FAIL_BREAKFREE;
                // 刀被挣出来、整个人被震开：两臂往外甩、往后仰，脚在身前往后滑（服务端这 300 ms 把人往后推 1.5 格）
                out.add(k(g + MercyTimeline.FAIL_BREAKFREE + 70, OUT, p().tw(-22).ln(-14).sq(0).sh(-2)
                        .r(78, -55, 0).aim(0).pull(0).l(135, -25, 0).grip(0).reach(0).legs(-6, 10).hd(-14, 6)));
                out.add(k(g + MercyTimeline.FAIL_BREAKFREE + 300, SINE, p().tw(-14).ln(-18).sh(-3)
                        .r(60, -60, 20).l(118, -30, 0).legs(16, -12).hd(-10, 4)));
                // 踉跄一步稳住，刀横在身前防着它
                out.add(k(g + MercyTimeline.FAIL_BREAKFREE + 460, OUT, p().tw(-6).ln(12).sq(0.4F).sh(0)
                        .r(38, -45, 20).l(50, -30, 0).legs(-22, 20).hd(4, 0)));
                out.add(k(g + MercyTimeline.FAIL_BREAKFREE + 800, SINE, p().tw(-10).ln(12).sq(0.25F).sh(0.5F)
                        .r(62, -12, -20).l(42, 25, 0).legs(-16, 16).hd(0, 0)));
                int handback = MercyTimeline.handbackMs(p, shortOutro);
                out.add(k(handback, handback - 200, SINE, zero()));
            }
            case THRALL -> {
                out.addAll(table);
                body.addAll(moves);
                body.add(k(MercyTimeline.THRALL_END_MS, SINE, m()));
            }
        }
        Key[] keys = resolve(out);
        Key[][] fp = v.thrall ? thrallFirstPerson(v) : lichFirstPerson(p, stabs);
        Track tr = new Track(keys, resolve(Arrays.asList(fp[0])), resolve(Arrays.asList(fp[1])), resolve(body), stabs,
                weight, anchorsOf(v), strainFrom, strainTo);
        if (v == MercyVariant.LICH_E) {
            tr.alt = ANCHOR_E;
            tr.altFrom = S1 + 560;
            tr.altTo = MercyTimeline.E_BLINK_MS;
            tr.tail = ANCHOR_A;
        }
        return tr;
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
        sample(keys, t, MOVE);
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
        return switch (mode) {
            case OUT -> {
                float k = 1.0F - x;
                yield 1.0F - k * k * k;
            }
            case IN -> x * x;
            case BACK -> {
                // easeOutBack：冲过头 10% 左右再回来
                float k = x - 1.0F;
                yield 1.0F + 2.70158F * k * k * k + 1.70158F * k * k;
            }
            default -> (float) (0.5D - 0.5D * Math.cos(Math.PI * x));
        };
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
            float tr2 = tremor(dt);
            if (tr2 != 0.0F) {
                v[R_RAISE] += 1.2F * w * tr2;
                v[TWIST] += 0.8F * tr2;
                v[R_ROLL] += 2.0F * tr2;
            }
        }
        float s = strain(tr, t);
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
        sample(tr.keys, t, v);
        addLife(tr, t, v);

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
        poseArm(model.rightArm, true, v[R_RAISE], v[R_YAW], v[R_ROLL], v[R_AIM], a.sx, a.sy, a.sz - reach, bodyY);
        poseArm(model.leftArm, false, v[L_RAISE], v[L_YAW], v[L_ROLL], v[L_GRIP], a.gx, a.gy, a.gz - reach, bodyY);

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
        double ay = target != null ? target.getY() + target.getBbHeight() * 0.62D : e.pl.y + 1.45D;
        double az = target != null ? target.getZ() : e.pl.z;
        boolean moved = body(e, t, BODY);
        double dx = ax - (moved ? BODY.x : entity.getX());
        double dy = ay - ((moved ? BODY.y : entity.getY()) + entity.getEyeHeight());
        double ddz = az - (moved ? BODY.z : entity.getZ());
        float lookYaw = (float) (Mth.atan2(-dx, ddz) * RAD_TO_DEG);
        float lookPitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + ddz * ddz)) * RAD_TO_DEG);
        float yaw = Mth.wrapDegrees(lookYaw - (moved ? BODY.yaw : entity.yBodyRot));
        float look = moved ? Mth.clamp(Mth.cos(BODY.flip * DEG), 0.0F, 1.0F) : 1.0F;
        model.head.yRot = Mth.clamp(yaw, -75.0F, 75.0F) * look * DEG;
        model.head.xRot = Mth.clamp(lookPitch * look + v[NOD], -60.0F, 60.0F) * DEG;
        model.head.zRot = v[TILT] * DEG;
        model.hat.copyFrom(model.head);
        SHIFTED.add(model);
    }

    /**
     * 摆一条胳膊：先按表里的「抬、偏」定指向，再按「瞄 / 抓」往锚点那边拉（从这一刻的肩膀位置算），最后绕手臂自己「拧」。
     * 合成一个四元数，拆回模型要的 ZYX 欧拉角（平举时直接改 zRot 是绕身体前后轴转，不是拧胳膊）
     */
    private static void poseArm(ModelPart arm, boolean right, float raise, float yaw, float roll, float aim,
                                float ax, float ay, float az, float bodyY) {
        float yRot = (right ? -yaw : yaw) * DEG + bodyY;
        float xRot = -raise * DEG;
        if (aim > 0.001F) {
            float dx = ax - arm.x;
            float dy = ay - arm.y;
            float dz = az - arm.z;
            float h = Mth.sqrt(dx * dx + dz * dz);
            float aimX = -(float) Mth.atan2(h, dy);
            float aimY = (float) Mth.atan2(-dx, -dz);
            float w = Math.min(aim, 1.0F);
            xRot = Mth.lerp(w, xRot, aimX);
            yRot = Mth.lerp(w, yRot, aimY);
        }
        Q.rotationY(yRot).rotateX(xRot).rotateY((right ? roll : -roll) * DEG);
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
            if (last && oc == MercyTimeline.Outcome.FAIL) {
                int g = MercyTimeline.failFillMs(p);
                stabFollow(r, s, 0.10F, -0.37F, 1.06F, 8, g + MercyTimeline.FAIL_BREAKFREE);
                // 被震开：刀被挣出来，两只手往外一甩
                r.add(f(g + MercyTimeline.FAIL_BREAKFREE + 80, OUT, 0.62F, -0.30F, 0.30F, -20, 0));
                l.add(f(g + MercyTimeline.FAIL_BREAKFREE, SINE, -0.16F, -0.21F, 0.80F, 6, 0));
                l.add(f(g + MercyTimeline.FAIL_BREAKFREE + 80, OUT, -0.50F, -0.10F, 0.55F, -20, 0));
                r.add(f(g + MercyTimeline.FAIL_BREAKFREE + 700, SINE, 0.40F, -0.48F, 0.55F, 0, -10));
                l.add(f(g + MercyTimeline.FAIL_BREAKFREE + 700, SINE, -0.45F, -0.70F, 0.55F, 0, 0));
            } else {
                stabFollow(r, s, 0.10F, -0.37F, 1.06F, 8, s + 460);
                r.add(f(s + 620, IN, 0.45F, -0.42F, 0.45F, -6, -15));
            }
        }
        if (oc != MercyTimeline.Outcome.FAIL) {
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
