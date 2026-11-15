package pers.roinflam.kuvalich.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyCameraPath;
import pers.roinflam.kuvalich.mercy.MercyCameraPath.Motion;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 处决镜头的轨迹层（MercyCameraPath）。镜头本身只在客户端，这里测轨迹的分段、切点、猛推和各档的差别
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MercyCameraPathTests {

    private static final MercyVariant A = MercyVariant.LICH_A;

    private MercyCameraPathTests() {
    }

    private static float[] at(int last, Motion m, long t, long raw) {
        float[] out = new float[MercyCameraPath.CHANNELS];
        if (!MercyCameraPath.sample(A, last, m, t, raw, out)) {
            throw new AssertionError("正面直刺在 " + m + " 档应当有轨迹");
        }
        return out;
    }

    /** 不等顿帧：真实钟远远在前，刺入的猛推已经推完 */
    private static float[] at(int last, Motion m, long t) {
        return at(last, m, t, t + 10_000L);
    }

    /** 静态档一律没有这一层；没排轨迹的套路（现在除了正面直刺都是）各档都没有 */
    @GameTest(template = "empty")
    public static void staticAndUnplannedHaveNoPath(GameTestHelper h) {
        float[] out = new float[MercyCameraPath.CHANNELS];
        for (long t = 0; t < 9000; t += 25) {
            h.assertFalse(MercyCameraPath.sample(A, 3, Motion.STATIC, t, t, out), "静态档不该有轨迹：" + t);
        }
        h.assertTrue(MercyCameraPath.has(A), "正面直刺应当排了轨迹");
        for (MercyVariant v : MercyVariant.values()) {
            if (v == A) {
                continue;
            }
            h.assertFalse(MercyCameraPath.has(v), v + " 还没排轨迹");
            for (Motion m : Motion.values()) {
                h.assertFalse(MercyCameraPath.sample(v, 3, m, 1500L, 1500L, out), v + " 在 " + m + " 档不该有轨迹");
            }
        }
        h.succeed();
    }

    /**
     * 三刀三种景别：第一刀中近景；第二刀那一刻硬切推近、绕到斜后方压低；拔刀后拉开升高；第三刀刺入后比蓄力时近得多
     */
    @GameTest(template = "empty")
    public static void lichAThreeFramings(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(A);
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        float[] first = at(3, Motion.FULL, s1);
        float[] beforeCut = at(3, Motion.FULL, s2 - 1);
        float[] second = at(3, Motion.FULL, s2);
        float[] windup = at(3, Motion.FULL, s3 - 40);
        float[] third = at(3, Motion.FULL, s3 + 100);
        int d = MercyCameraPath.DOLLY;
        h.assertTrue(Math.abs(first[d]) < 0.08F, "第一刀应当是中近景，推拉不大：" + first[d]);
        h.assertTrue(second[d] - beforeCut[d] > 0.1F, "第二刀那一刻应当硬切推近：" + beforeCut[d] + " → " + second[d]);
        h.assertTrue(beforeCut[MercyCameraPath.ORBIT] - second[MercyCameraPath.ORBIT] > 20.0F,
                "第二刀应当切到玄骸斜后方，看刀尖透背：" + second[MercyCameraPath.ORBIT]);
        h.assertTrue(second[MercyCameraPath.RISE] < -0.4F, "第二刀应当是低机位往上拍：" + second[MercyCameraPath.RISE]);
        h.assertTrue(windup[d] < second[d] - 0.15F, "第三刀蓄力时应当拉开：" + windup[d]);
        h.assertTrue(windup[MercyCameraPath.RISE] > 0.1F, "蓄力时镜头应当升高，拍到举过头顶的刀");
        h.assertTrue(third[d] - windup[d] > 0.2F, "第三刀刺入后应当猛推近：" + windup[d] + " → " + third[d]);
        h.assertTrue(MercyCameraPath.segment(A, 3, s2 - 1) != MercyCameraPath.segment(A, 3, s2),
                "第二刀应当换段（硬切）");
        h.succeed();
    }

    /** 猛推按真实钟走：顿帧把演出钟停在刺入那一刻时照样推过去，推之前和上一段的末值接上 */
    @GameTest(template = "empty")
    public static void lichAPunchReadsRawClock(GameTestHelper h) {
        int s3 = MercyBeats.lich(A).stab(2);
        int d = MercyCameraPath.DOLLY;
        float pre = at(3, Motion.FULL, s3 - 1)[d];
        float start = at(3, Motion.FULL, s3, s3)[d];
        float mid = at(3, Motion.FULL, s3, s3 + MercyCameraPath.PUNCH_MS / 4)[d];
        float done = at(3, Motion.FULL, s3, s3 + MercyCameraPath.PUNCH_MS)[d];
        h.assertTrue(Math.abs(start - pre) < 0.01F, "猛推应当从上一段的末值起步：" + pre + " / " + start);
        h.assertTrue(mid > start + 0.05F && mid < done, "演出钟停着时猛推照样往前走：" + start + " / " + mid + " / " + done);
        h.assertTrue(Math.abs(done - at(3, Motion.FULL, s3)[d]) < 1e-5F, "真实钟过了猛推窗口就是这一段的值");
        h.succeed();
    }

    /** 除了第二刀的硬切和第三刀的猛推，各通道逐毫秒连续（不跳、不抖） */
    @GameTest(template = "empty")
    public static void lichAContinuousBetweenCuts(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(A);
        int s2 = b.stab(1), s3 = b.stab(2);
        // 绕的上限 80°/秒：第二、三刀之间要从斜后方绕回侧面
        float[] limit = {0.003F, 0.08F, 0.003F, 0.02F, 0.001F};
        float[] prev = at(3, Motion.FULL, b.cutIn);
        for (long t = b.cutIn + 1; t < b.closeup; t++) {
            float[] cur = at(3, Motion.FULL, t);
            if (t != s2 && t != s3) {
                for (int c = 0; c < MercyCameraPath.CHANNELS; c++) {
                    h.assertTrue(Math.abs(cur[c] - prev[c]) <= limit[c],
                            t + " ms 第 " + c + " 通道一毫秒变了 " + (cur[c] - prev[c]));
                }
            }
            prev = cur;
        }
        h.succeed();
    }

    /** 减弱档只留推拉，和完整档的推拉一样；不绕、不升降、不滚、不跟人 */
    @GameTest(template = "empty")
    public static void reducedKeepsDollyOnly(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(A);
        for (long t = b.cutIn; t < b.closeup; t += 7) {
            float[] full = at(3, Motion.FULL, t);
            float[] red = at(3, Motion.REDUCED, t);
            h.assertTrue(full[MercyCameraPath.DOLLY] == red[MercyCameraPath.DOLLY], t + " ms 减弱档的推拉应当和完整档一样");
            for (int c = MercyCameraPath.ORBIT; c < MercyCameraPath.CHANNELS; c++) {
                h.assertTrue(red[c] == 0.0F, t + " ms 减弱档第 " + c + " 通道应当是 0");
            }
        }
        h.succeed();
    }

    /** 不到三刀就出结果的：最后一刀之后再走一小段就停，后面那一刀的切镜、蓄力拉开、猛推都不出现 */
    @GameTest(template = "empty")
    public static void earlyOutcomeHoldsPath(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(A);
        int s1 = b.stab(0), s2 = b.stab(1);
        float[] hold2 = at(2, Motion.FULL, s2 + MercyCameraPath.HOLD_AFTER_LAST_MS);
        float[] hold1 = at(1, Motion.FULL, s1 + MercyCameraPath.HOLD_AFTER_LAST_MS);
        for (long t = s1; t < 6000; t += 13) {
            if (t >= s2 + MercyCameraPath.HOLD_AFTER_LAST_MS) {
                float[] cur = at(2, Motion.FULL, t, t);
                for (int c = 0; c < MercyCameraPath.CHANNELS; c++) {
                    h.assertTrue(cur[c] == hold2[c], "第二刀出结果时 " + t + " ms 应当停住");
                }
            }
            if (t >= s1 + MercyCameraPath.HOLD_AFTER_LAST_MS) {
                float[] cur = at(1, Motion.FULL, t, t);
                for (int c = 0; c < MercyCameraPath.CHANNELS; c++) {
                    h.assertTrue(cur[c] == hold1[c], "第一刀出结果时 " + t + " ms 应当停住");
                }
            }
            h.assertTrue(MercyCameraPath.segment(A, 1, t) == 0, "第一刀出结果时不该切到第二刀的机位");
        }
        h.succeed();
    }

    // 正面直刺进机位时的样子（目标脚底在原点，处决者在 +X 1.25 格，侧面机位在 +Z），取自录像里的相机轨迹；
    // 起点的注视点在眼前 3 格（客户端的 ENTRY_LOOK_REACH）
    private static final Vec3 EYE = new Vec3(1.25D, 1.62D, 0.0D);
    private static final Vec3 EYE_LOOK = new Vec3(-1.72D, 1.29D, 0.0D);
    private static final Vec3 SIDE = new Vec3(0.46D, 1.64D, 2.92D);
    private static final Vec3 SIDE_LOOK = new Vec3(0.51D, 1.74D, 0.0D);
    private static final Vec3 HEAD = new Vec3(1.05D, 1.7D, 0.0D);

    private static double heading(float x) {
        Vec3 p = MercyCameraPath.entryArc(EYE, EYE_LOOK, SIDE, SIDE_LOOK, x);
        Vec3 l = EYE_LOOK.lerp(SIDE_LOOK, x);
        return Math.toDegrees(Math.atan2(l.z - p.z, l.x - p.x));
    }

    /** 进机位的弧线两头对得上，朝向起步慢慢转开，60 帧下哪一帧都不甩 */
    @GameTest(template = "empty")
    public static void entryArcEasesTheTurn(GameTestHelper h) {
        h.assertTrue(MercyCameraPath.entryArc(EYE, EYE_LOOK, SIDE, SIDE_LOOK, 0.0F).distanceTo(EYE) < 1e-6D, "弧线应当从眼睛起步");
        h.assertTrue(MercyCameraPath.entryArc(EYE, EYE_LOOK, SIDE, SIDE_LOOK, 1.0F).distanceTo(SIDE) < 1e-6D, "弧线应当落在机位上");
        double frame = 1000.0D / 60.0D;
        int frames = (int) Math.ceil(MercyCameraPath.ENTRY_ARC_MS / frame);
        double prev = heading(0.0F);
        double total = 0.0D;
        for (int i = 1; i <= frames; i++) {
            float x = MercyCameraPath.entryEase((float) (i * frame / MercyCameraPath.ENTRY_ARC_MS));
            double cur = heading(x);
            double d = Math.abs(Mth.wrapDegrees(cur - prev));
            h.assertTrue(i > 1 || d < 3.0D, "第一帧只该转一点：" + d);
            h.assertTrue(d < 12.0D, "第 " + i + " 帧转了 " + d + "°");
            total += d;
            prev = cur;
        }
        h.assertTrue(total > 80.0D && total < 100.0D, "一共应当转到侧面：" + total);
        h.succeed();
    }

    /** 弧线离开处决者的头以后不进目标的身子；同样两头走直线会横穿目标（原来的穿模） */
    @GameTest(template = "empty")
    public static void entryArcStaysClearOfTarget(GameTestHelper h) {
        double r = 0.75D * 1.75D;
        double top = 3.7D;
        h.assertTrue(MercyCameraPath.entryClear(EYE, EYE_LOOK, SIDE, SIDE_LOOK, HEAD, 0.42D, Vec3.ZERO, r, top),
                "弧线应当绕开目标");
        boolean straightHits = false;
        for (int i = 1; i < MercyCameraPath.ENTRY_SAMPLES; i++) {
            Vec3 p = EYE.lerp(SIDE, i / (double) MercyCameraPath.ENTRY_SAMPLES);
            straightHits |= p.distanceTo(HEAD) > 0.42D && !MercyCameraPath.outsideColumn(p, Vec3.ZERO, r, top);
        }
        h.assertTrue(straightHits, "直线应当穿过目标的身子（这条测试的几何没摆对）");
        h.assertFalse(MercyCameraPath.entryClear(EYE, EYE_LOOK, SIDE, SIDE_LOOK, HEAD, 0.42D, Vec3.ZERO, 2.0D, top),
                "目标再宽一些时弧线也过不去，应当查出来");
        h.succeed();
    }

    /** 遮挡检测的时刻：每段两头和中点，到本套机位交出去之前为止 */
    @GameTest(template = "empty")
    public static void checkTimesCoverEachSegment(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(A);
        int s2 = b.stab(1), s3 = b.stab(2);
        int[] full = MercyCameraPath.checkTimes(A, 3, b.closeup);
        h.assertTrue(full.length == 9, "三段应当各查三处：" + full.length);
        int[] want = {b.cutIn, s2 - 1, s2, s3 - 1, s3, b.closeup - 1};
        for (int w : want) {
            boolean found = false;
            for (int x : full) {
                found |= x == w;
            }
            h.assertTrue(found, "应当查 " + w + " ms");
        }
        for (int x : full) {
            h.assertTrue(x >= b.cutIn && x < b.closeup, "查的时刻应当在本套机位的时段里：" + x);
        }
        int[] early = MercyCameraPath.checkTimes(A, 1, 2830);
        h.assertTrue(early.length == 3, "第一刀出结果只有第一段：" + early.length);
        h.assertTrue(early[2] == b.stab(0) + MercyCameraPath.HOLD_AFTER_LAST_MS, "第一段查到轨迹停住的那一刻");
        h.succeed();
    }
}
