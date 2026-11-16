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
    private static final MercyVariant B = MercyVariant.LICH_B;
    private static final MercyVariant C = MercyVariant.LICH_C;
    private static final MercyVariant D = MercyVariant.LICH_D;
    private static final MercyVariant E = MercyVariant.LICH_E;
    private static final MercyVariant F = MercyVariant.LICH_F;
    private static final MercyVariant G = MercyVariant.LICH_G;
    private static final MercyVariant H = MercyVariant.LICH_H;
    private static final MercyVariant[] PLANNED = {A, B, C, D, E, F, G, H};

    private MercyCameraPathTests() {
    }

    private static float[] at(MercyVariant v, int last, Motion m, long t, long raw) {
        float[] out = new float[MercyCameraPath.CHANNELS];
        if (!MercyCameraPath.sample(v, last, m, t, raw, out)) {
            throw new AssertionError(v + " 在 " + m + " 档应当有轨迹");
        }
        return out;
    }

    private static float[] at(int last, Motion m, long t, long raw) {
        return at(A, last, m, t, raw);
    }

    /** 不等顿帧：真实钟远远在前，刺入的猛推已经推完 */
    private static float[] at(int last, Motion m, long t) {
        return at(A, last, m, t, t + 10_000L);
    }

    private static float[] at(MercyVariant v, int last, Motion m, long t) {
        return at(v, last, m, t, t + 10_000L);
    }

    /** 静态档一律没有这一层；玄骸六套都排了轨迹，奴仆几套各档都没有 */
    @GameTest(template = "empty")
    public static void staticAndUnplannedHaveNoPath(GameTestHelper h) {
        float[] out = new float[MercyCameraPath.CHANNELS];
        for (MercyVariant v : PLANNED) {
            for (long t = 0; t < 9000; t += 25) {
                h.assertFalse(MercyCameraPath.sample(v, 3, Motion.STATIC, t, t, out), v + " 静态档不该有轨迹：" + t);
            }
            h.assertTrue(MercyCameraPath.has(v), v + " 应当排了轨迹");
        }
        for (MercyVariant v : MercyVariant.values()) {
            if (!v.thrall) {
                continue;
            }
            h.assertFalse(MercyCameraPath.has(v), v + " 奴仆不排轨迹");
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
        // 绕的上限 80°/秒：第二、三刀之间要从斜后方绕回来
        float[] limit = {0.003F, 0.08F, 0.003F, 0.02F, 0.001F};
        for (MercyVariant v : PLANNED) {
            MercyBeats.Lich b = MercyBeats.lich(v);
            int s2 = b.stab(1), s3 = b.stab(2);
            float[] prev = at(v, 3, Motion.FULL, b.cutIn);
            for (long t = b.cutIn + 1; t < b.closeup; t++) {
                float[] cur = at(v, 3, Motion.FULL, t);
                if (t != s2 && t != s3) {
                    for (int c = 0; c < MercyCameraPath.CHANNELS; c++) {
                        h.assertTrue(Math.abs(cur[c] - prev[c]) <= limit[c],
                                v + " " + t + " ms 第 " + c + " 通道一毫秒变了 " + (cur[c] - prev[c]));
                    }
                }
                prev = cur;
            }
        }
        h.succeed();
    }

    /**
     * 背后锁颈：第一刀过肩、推拉不大，勒脖子那一段慢慢往里蹭；第二刀硬切到斜后方压低；拔刀转圈时拉开升回去；
     * 第三刀刺入猛推
     */
    @GameTest(template = "empty")
    public static void lichBThreeFramings(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(B);
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        int d = MercyCameraPath.DOLLY, o = MercyCameraPath.ORBIT, r = MercyCameraPath.RISE;
        float[] first = at(B, 3, Motion.FULL, s1);
        float[] beforeCut = at(B, 3, Motion.FULL, s2 - 1);
        float[] second = at(B, 3, Motion.FULL, s2);
        float[] windup = at(B, 3, Motion.FULL, s3 - 60);
        float[] third = at(B, 3, Motion.FULL, s3 + 100);
        h.assertTrue(Math.abs(first[d]) < 0.08F, "第一刀应当是过肩原样，推拉不大：" + first[d]);
        h.assertTrue(beforeCut[d] - first[d] > 0.06F, "勒脖子那一段应当慢慢推近：" + first[d] + " → " + beforeCut[d]);
        h.assertTrue(beforeCut[o] - second[o] > 20.0F, "第二刀应当切到斜后方：" + beforeCut[o] + " → " + second[o]);
        h.assertTrue(second[r] < -0.4F, "第二刀应当压低：" + second[r]);
        h.assertTrue(windup[d] < second[d] - 0.15F, "转圈时应当拉开：" + windup[d]);
        h.assertTrue(windup[r] > 0.0F && windup[o] > second[o] + 10.0F, "转圈时应当升回过肩：" + windup[r] + " / " + windup[o]);
        h.assertTrue(third[d] - windup[d] > 0.2F, "第三刀刺入后应当猛推近：" + windup[d] + " → " + third[d]);
        for (long t = b.cutIn; t < b.closeup; t += 5) {
            h.assertTrue(at(B, 3, Motion.FULL, t)[o] <= 0.0F, "过肩机位不该往处决者背后绕：" + t);
        }
        h.succeed();
    }

    /**
     * 贴身上挑：第一刀斜前方低机位、推拉不大，慢慢往里蹭；第二刀硬切到正侧面；第三刀前拉开压低往上看；
     * 第三刀刺入猛推，后空翻时拉开升高
     */
    @GameTest(template = "empty")
    public static void lichCThreeFramings(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(C);
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        int d = MercyCameraPath.DOLLY, o = MercyCameraPath.ORBIT, r = MercyCameraPath.RISE;
        float[] first = at(C, 3, Motion.FULL, s1);
        float[] beforeCut = at(C, 3, Motion.FULL, s2 - 1);
        float[] second = at(C, 3, Motion.FULL, s2);
        float[] windup = at(C, 3, Motion.FULL, s3 - 80);
        float[] third = at(C, 3, Motion.FULL, s3 + 100);
        float[] flip = at(C, 3, Motion.FULL, s3 + 700);
        h.assertTrue(Math.abs(first[d]) < 0.08F, "第一刀应当是原机位，推拉不大：" + first[d]);
        h.assertTrue(beforeCut[d] - first[d] > 0.03F, "前两刀之间应当慢慢推近：" + first[d] + " → " + beforeCut[d]);
        h.assertTrue(beforeCut[o] - second[o] > 20.0F, "第二刀应当切到正侧面：" + beforeCut[o] + " → " + second[o]);
        h.assertTrue(second[r] > 0.05F && second[r] < 0.3F, "第二刀应当稍抬一点，从它的胳膊底下看：" + second[r]);
        h.assertTrue(windup[d] < second[d] - 0.15F && windup[r] < 0.0F, "第三刀前应当拉开压低：" + windup[d] + " / " + windup[r]);
        h.assertTrue(third[d] - windup[d] > 0.2F, "第三刀刺入后应当猛推近：" + windup[d] + " → " + third[d]);
        h.assertTrue(flip[d] < third[d] - 0.2F && flip[r] > 0.2F, "后空翻时应当拉开升高：" + flip[d] + " / " + flip[r]);
        for (long t = b.cutIn; t < b.closeup; t += 5) {
            h.assertTrue(at(C, 3, Motion.FULL, t)[o] <= -15.0F, "注视点挪低以后至少往侧面绕 15°，不然视线擦着处决者的头：" + t);
        }
        h.assertTrue(MercyCameraPath.lookDrop(C) > 0.0F && MercyCameraPath.lookDrop(A) == 0.0F
                && MercyCameraPath.lookDrop(B) == 0.0F, "只有贴身上挑挪注视点");
        h.succeed();
    }

    /**
     * 翻滚绕背：前两刀往处决者那一侧绕、推近成中近景；第二刀硬切到斜后方压低；翻滚时拉开升高；
     * 第三刀刺入猛推、往它背后再绕一点
     */
    @GameTest(template = "empty")
    public static void lichDThreeFramings(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(D);
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        int d = MercyCameraPath.DOLLY, o = MercyCameraPath.ORBIT, r = MercyCameraPath.RISE;
        float[] first = at(D, 3, Motion.FULL, s1);
        float[] beforeCut = at(D, 3, Motion.FULL, s2 - 1);
        float[] second = at(D, 3, Motion.FULL, s2);
        float[] roll = at(D, 3, Motion.FULL, s2 + 900);
        float[] third = at(D, 3, Motion.FULL, s3 + 100);
        h.assertTrue(first[d] > 0.15F, "本套机位离得远，前两刀应当推近：" + first[d]);
        h.assertTrue(beforeCut[o] - second[o] > 20.0F, "第二刀应当切到斜后方：" + beforeCut[o] + " → " + second[o]);
        h.assertTrue(second[r] < -0.4F, "第二刀应当是低机位往上拍：" + second[r]);
        h.assertTrue(roll[d] < second[d] - 0.25F && roll[r] > 0.2F, "翻滚时应当拉开升高：" + roll[d] + " / " + roll[r]);
        h.assertTrue(third[d] - roll[d] > 0.2F, "第三刀刺入后应当猛推近：" + roll[d] + " → " + third[d]);
        h.assertTrue(first[o] > 10.0F && first[r] < -0.2F, "前两刀应当往处决者那一侧绕、压低，不被它的左肩甲挡住：" + first[o] + " / " + first[r]);
        h.assertTrue(third[o] < roll[o] - 10.0F, "第三刀应当往它背后再绕一点：" + roll[o] + " → " + third[o]);
        h.assertTrue(MercyCameraPath.lookDrop(D) == 0.0F, "翻滚绕背不挪注视点");
        h.succeed();
    }

    /**
     * 踏背下刺：第一刀往背后那一侧绕、推近；跳上背时拉开升高；第二刀硬切到正面那一侧高处往下看；化光时拉开；
     * 第三刀刺入猛推、稍往正面绕
     */
    @GameTest(template = "empty")
    public static void lichEThreeFramings(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(E);
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        int d = MercyCameraPath.DOLLY, o = MercyCameraPath.ORBIT, r = MercyCameraPath.RISE;
        float[] first = at(E, 3, Motion.FULL, s1);
        float[] vault = at(E, 3, Motion.FULL, s1 + 760);
        float[] beforeCut = at(E, 3, Motion.FULL, s2 - 1);
        float[] second = at(E, 3, Motion.FULL, s2);
        float[] blink = at(E, 3, Motion.FULL, b.appear);
        float[] third = at(E, 3, Motion.FULL, s3 + 100);
        h.assertTrue(first[d] > 0.1F && first[o] > 5.0F, "第一刀应当往它背后那一侧绕、推近：" + first[d] + " / " + first[o]);
        h.assertTrue(vault[d] < first[d] - 0.2F && vault[r] > 0.3F, "跳上背时应当拉开升高：" + vault[d] + " / " + vault[r]);
        h.assertTrue(beforeCut[o] - second[o] > 20.0F && second[r] > 0.6F,
                "第二刀应当切到正面那一侧的高处：" + second[o] + " / " + second[r]);
        h.assertTrue(blink[d] < second[d] - 0.2F, "化光时应当拉开：" + blink[d]);
        h.assertTrue(third[d] - blink[d] > 0.2F && third[o] < blink[o] - 8.0F,
                "第三刀刺入后应当猛推、往正面绕：" + third[d] + " / " + third[o]);
        h.assertTrue(MercyCameraPath.lookDrop(E) == 0.0F, "踏背下刺不挪注视点");
        h.succeed();
    }

    /**
     * 折翼：第一刀贴着侧面、几乎不绕；拧的那一段跟着往它背后绕、升高；第二刀刺入猛推、带一点滚；把它提起来时
     * 升到最高；第三刀前绕回来压低，刺入再猛推
     */
    @GameTest(template = "empty")
    public static void lichFThreeFramings(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(F);
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        int d = MercyCameraPath.DOLLY, o = MercyCameraPath.ORBIT, r = MercyCameraPath.RISE;
        int roll = MercyCameraPath.ROLL;
        float[] first = at(F, 3, Motion.FULL, s1 + 100);
        float[] twist = at(F, 3, Motion.FULL, s1 + 790);
        float[] beforeCut = at(F, 3, Motion.FULL, s2 - 1);
        float[] second = at(F, 3, Motion.FULL, s2);
        float[] lift = at(F, 3, Motion.FULL, s2 + 640);
        float[] cock = at(F, 3, Motion.FULL, s3 - 1);
        float[] third = at(F, 3, Motion.FULL, s3);
        h.assertTrue(Math.abs(first[o]) < 3.0F && first[d] > 0.05F, "第一刀应当贴着侧面推近：" + first[d] + " / " + first[o]);
        h.assertTrue(twist[o] > 15.0F && twist[r] > 0.8F, "拧的时候应当往背后绕、升高：" + twist[o] + " / " + twist[r]);
        h.assertTrue(second[d] - beforeCut[d] > 0.05F && second[roll] > 1.0F,
                "第二刀刺入应当猛推、带一点滚：" + second[d] + " / " + second[roll]);
        h.assertTrue(lift[r] > second[r] + 0.3F, "提起来时应当升到最高：" + lift[r]);
        h.assertTrue(cock[o] < lift[o] - 10.0F && cock[r] < lift[r] - 0.5F, "第三刀前应当绕回来压低：" + cock[o] + " / " + cock[r]);
        h.assertTrue(third[d] - cock[d] > 0.05F, "第三刀刺入应当猛推：" + third[d]);
        h.assertTrue(MercyCameraPath.lookDrop(F) == 0.0F, "折翼不挪注视点");
        h.succeed();
    }

    /**
     * 断膝三方：滑铲时在左边多绕一截，第一刀前绕回来；一拔刀就往它右后方绕、侧翻最高处升起来；第二刀刺入猛推、带一点滚；
     * 前滚时转回正后方、降下来；第三刀刺入再猛推
     */
    @GameTest(template = "empty")
    public static void lichGThreeFramings(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(G);
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        int d = MercyCameraPath.DOLLY, o = MercyCameraPath.ORBIT, r = MercyCameraPath.RISE;
        int roll = MercyCameraPath.ROLL;
        float[] slide = at(G, 3, Motion.FULL, s1 - 620);
        float[] first = at(G, 3, Motion.FULL, s1);
        float[] vault = at(G, 3, Motion.FULL, s1 + 620);
        float[] beforeCut = at(G, 3, Motion.FULL, s2 - 1);
        float[] second = at(G, 3, Motion.FULL, s2);
        float[] rolled = at(G, 3, Motion.FULL, s2 + 800);
        float[] cock = at(G, 3, Motion.FULL, s3 - 1);
        float[] third = at(G, 3, Motion.FULL, s3);
        h.assertTrue(slide[o] > 30.0F && first[o] < slide[o] - 25.0F, "滑铲时应当多绕、第一刀前绕回来：" + slide[o] + " / " + first[o]);
        h.assertTrue(vault[o] < first[o] - 20.0F && vault[r] > 0.3F, "侧翻时应当往它右后方绕、升起来：" + vault[o] + " / " + vault[r]);
        h.assertTrue(beforeCut[o] < vault[o] - 20.0F, "第二刀前应当绕到它右后方：" + beforeCut[o]);
        h.assertTrue(second[d] - beforeCut[d] > 0.05F && second[roll] > 1.0F,
                "第二刀刺入应当猛推、带一点滚：" + second[d] + " / " + second[roll]);
        h.assertTrue(rolled[o] > second[o] + 25.0F && rolled[r] < 0.1F, "前滚时应当转回正后方、降下来：" + rolled[o] + " / " + rolled[r]);
        h.assertTrue(third[d] - cock[d] > 0.05F, "第三刀刺入应当猛推：" + third[d]);
        h.assertTrue(MercyCameraPath.lookDrop(G) == 0.0F, "断膝三方不挪注视点");
        h.succeed();
    }

    /**
     * 三掠：一掠时推近、升高，第一刀再往里送一点；第二刀前往侧面绕、升到最高，刺入猛推、带一点滚；腾空越顶那一段
     * 往回绕、降下来，第三刀刺入猛推、往反方向滚；它倒下时绕到正面、压低、拉开
     */
    @GameTest(template = "empty")
    public static void lichHThreeFramings(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(H);
        int s1 = b.stab(0), s2 = b.stab(1), s3 = b.stab(2);
        int d = MercyCameraPath.DOLLY, o = MercyCameraPath.ORBIT, r = MercyCameraPath.RISE;
        int roll = MercyCameraPath.ROLL;
        float[] entry = at(H, 3, Motion.FULL, b.cutIn + 1);
        float[] first = at(H, 3, Motion.FULL, s1 + 60);
        float[] beforeCut = at(H, 3, Motion.FULL, s2 - 1);
        float[] second = at(H, 3, Motion.FULL, s2);
        float[] cock = at(H, 3, Motion.FULL, s3 - 1);
        float[] third = at(H, 3, Motion.FULL, s3);
        float[] fall = at(H, 3, Motion.FULL, s3 + 950);
        h.assertTrue(first[d] > entry[d] + 0.1F && first[r] > entry[r] + 0.8F, "一掠应当推近、升高：" + first[d] + " / " + first[r]);
        h.assertTrue(beforeCut[o] < first[o] - 20.0F && beforeCut[r] > first[r] + 0.5F,
                "第二刀前应当往侧面绕、升到最高：" + beforeCut[o] + " / " + beforeCut[r]);
        h.assertTrue(second[d] - beforeCut[d] > 0.05F && second[roll] < -1.0F,
                "第二刀刺入应当猛推、带一点滚：" + second[d] + " / " + second[roll]);
        h.assertTrue(cock[o] > second[o] + 15.0F && cock[r] < second[r] - 0.4F, "越顶那一段应当往回绕、降下来：" + cock[o] + " / " + cock[r]);
        h.assertTrue(third[d] - cock[d] > 0.05F && third[roll] > 1.0F, "第三刀刺入应当猛推、往反方向滚：" + third[d] + " / " + third[roll]);
        h.assertTrue(fall[o] > 5.0F && fall[d] < 0.0F && fall[r] < 0.5F, "它倒下时应当绕到正面、拉开、压低：" + fall[o] + " / " + fall[d]);
        h.assertTrue(MercyCameraPath.lookDrop(H) == 0.0F, "三掠不挪注视点");
        h.succeed();
    }

    /** 减弱档只留推拉，和完整档的推拉一样；绕角整段固定（多数是 0）、不升降、不滚、不跟人 */
    @GameTest(template = "empty")
    public static void reducedKeepsDollyOnly(GameTestHelper h) {
        for (MercyVariant v : PLANNED) {
            MercyBeats.Lich b = MercyBeats.lich(v);
            for (long t = b.cutIn; t < b.closeup; t += 7) {
                float[] full = at(v, 3, Motion.FULL, t);
                float[] red = at(v, 3, Motion.REDUCED, t);
                h.assertTrue(full[MercyCameraPath.DOLLY] == red[MercyCameraPath.DOLLY],
                        v + " " + t + " ms 减弱档的推拉应当和完整档一样");
                h.assertTrue(red[MercyCameraPath.ORBIT] == MercyCameraPath.reducedOrbit(v),
                        v + " " + t + " ms 减弱档的绕角应当整段固定");
                for (int c = MercyCameraPath.RISE; c < MercyCameraPath.CHANNELS; c++) {
                    h.assertTrue(red[c] == 0.0F, v + " " + t + " ms 减弱档第 " + c + " 通道应当是 0");
                }
            }
        }
        h.succeed();
    }

    /** 不到三刀就出结果的：最后一刀之后再走一小段就停，后面那一刀的切镜、蓄力拉开、猛推都不出现 */
    @GameTest(template = "empty")
    public static void earlyOutcomeHoldsPath(GameTestHelper h) {
        for (MercyVariant v : PLANNED) {
            MercyBeats.Lich b = MercyBeats.lich(v);
            int s1 = b.stab(0), s2 = b.stab(1);
            float[] hold2 = at(v, 2, Motion.FULL, s2 + MercyCameraPath.HOLD_AFTER_LAST_MS);
            float[] hold1 = at(v, 1, Motion.FULL, s1 + MercyCameraPath.HOLD_AFTER_LAST_MS);
            for (long t = s1; t < 6000; t += 13) {
                if (t >= s2 + MercyCameraPath.HOLD_AFTER_LAST_MS) {
                    float[] cur = at(v, 2, Motion.FULL, t, t);
                    for (int c = 0; c < MercyCameraPath.CHANNELS; c++) {
                        h.assertTrue(cur[c] == hold2[c], v + " 第二刀出结果时 " + t + " ms 应当停住");
                    }
                }
                if (t >= s1 + MercyCameraPath.HOLD_AFTER_LAST_MS) {
                    float[] cur = at(v, 1, Motion.FULL, t, t);
                    for (int c = 0; c < MercyCameraPath.CHANNELS; c++) {
                        h.assertTrue(cur[c] == hold1[c], v + " 第一刀出结果时 " + t + " ms 应当停住");
                    }
                }
                h.assertTrue(MercyCameraPath.segment(v, 1, t) == 0, v + " 第一刀出结果时不该切到第二刀的机位");
            }
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
