package pers.roinflam.kuvalich.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.mercy.MercyCurve;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;

/**
 * 处决者姿态的新曲线（MercyCurve）。姿态类本身只在客户端，这里测曲线和关键帧轨道的性质
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MercyCurveTests {

    private MercyCurveTests() {
    }

    private static MercyCurve.Keys keys(int[] t, int[] mode, float[]... v) {
        int[] start = new int[t.length];
        Arrays.fill(start, -1);
        return new MercyCurve.Keys(t, start, mode, v);
    }

    private static double slope(MercyCurve.Keys k, int c, double t, double h) {
        return (k.sample(c, t + h) - k.sample(c, t - h)) / (2.0D * h);
    }

    /**
     * 平常的帧：经过关键帧的值，同向的帧上速度不归零、左右连续，反向的那一帧速度为 0，两帧之间不冲出两头的范围；
     * 标了「停」的帧速度为 0
     */
    @GameTest(template = "empty")
    public static void curveKeepsVelocity(GameTestHelper h) {
        int c = MercyCurve.CURVE;
        MercyCurve.Keys k = keys(new int[]{0, 100, 250, 400, 600}, new int[]{c, c, c, c, c},
                new float[]{0}, new float[]{10}, new float[]{30}, new float[]{20}, new float[]{20});
        float[] at = {0, 10, 30, 20, 20};
        int[] t = {0, 100, 250, 400, 600};
        for (int i = 0; i < t.length; i++) {
            h.assertTrue(Math.abs(k.sample(0, t[i]) - at[i]) < 1e-4F, "第 " + i + " 帧的值应当正好是关键帧的值");
        }
        double left = slope(k, 0, 99.0D, 1.0D);
        double right = slope(k, 0, 101.0D, 1.0D);
        h.assertTrue(left > 0.05D && right > 0.05D, "同向的帧上速度不该归零：" + left + " / " + right);
        h.assertTrue(Math.abs(left - right) < 0.02D, "同向的帧两边速度应当连续：" + left + " / " + right);
        h.assertTrue(Math.abs(slope(k, 0, 250.0D, 0.5D)) < 0.01D, "反向的那一帧速度应当是 0");
        for (int ms = 0; ms <= 600; ms++) {
            float x = k.sample(0, ms);
            h.assertTrue(x >= -1e-4F && x <= 30.0001F, ms + " ms 冲出了关键帧的范围：" + x);
            if (ms >= 250 && ms <= 400) {
                h.assertTrue(x >= 19.9999F, ms + " ms 在 30 → 20 那段冲过了 20：" + x);
            }
        }

        MercyCurve.Keys held = keys(new int[]{0, 100, 200}, new int[]{c, c | MercyCurve.HOLD, c},
                new float[]{0}, new float[]{10}, new float[]{20});
        h.assertTrue(Math.abs(slope(held, 0, 100.0D, 0.5D)) < 0.01D, "标了停的帧速度应当是 0");
        h.succeed();
    }

    /** 预备先往反方向收 8~12% 再出去，过冲冲过 6~10% 再落回，两种都从 0 起、正好停在 1 */
    @GameTest(template = "empty")
    public static void anticAndOvershoot(GameTestHelper h) {
        float minAntic = 1.0F, maxOver = 0.0F;
        for (int i = 0; i <= 1000; i++) {
            float x = i / 1000.0F;
            minAntic = Math.min(minAntic, MercyCurve.ease(MercyCurve.ANTIC, x));
            maxOver = Math.max(maxOver, MercyCurve.ease(MercyCurve.OVER, x));
        }
        h.assertTrue(minAntic <= -0.08F && minAntic >= -0.12F, "预备往回收的量应当在 8~12%：" + minAntic);
        h.assertTrue(maxOver >= 1.06F && maxOver <= 1.10F, "过冲的量应当在 6~10%：" + maxOver);
        for (int mode : new int[]{MercyCurve.ANTIC, MercyCurve.OVER, MercyCurve.DECEL}) {
            h.assertTrue(Math.abs(MercyCurve.ease(mode, 0.0F)) < 1e-4F, "模式 " + mode + " 应当从 0 起");
            h.assertTrue(Math.abs(MercyCurve.ease(mode, 1.0F) - 1.0F) < 1e-4F, "模式 " + mode + " 应当停在 1");
        }
        // 过冲那一段是甩出去的：头一成就走了大半，不是从静止慢慢起
        h.assertTrue(MercyCurve.ease(MercyCurve.OVER, 0.1F) > 0.4F, "过冲那一段起步太慢");
        h.succeed();
    }

    /** 错峰：走在前面的通道不提前跳过切镜那一帧，落在后面的跳过去以后不回到切镜之前 */
    @GameTest(template = "empty")
    public static void staggerRespectsCuts(GameTestHelper h) {
        int c = MercyCurve.CURVE;
        MercyCurve.Keys k = keys(new int[]{0, 100, 200, 400}, new int[]{c, c, MercyCurve.SNAP, c},
                new float[]{0, 0}, new float[]{10, 10}, new float[]{50, 50}, new float[]{60, 60});
        int[] offset = {-40, 40};
        float[] v = new float[2];
        k.sampleStaggered(190L, offset, v);
        h.assertTrue(v[0] < 11.0F, "提前 40 ms 的通道在切镜前 10 ms 已经跳过去了：" + v[0]);
        k.sampleStaggered(205L, offset, v);
        h.assertTrue(v[1] >= 50.0F, "推后 40 ms 的通道在切镜后还停在切镜之前：" + v[1]);
        k.sampleStaggered(60L, offset, v);
        h.assertTrue(v[0] > v[1], "离切镜远的时候提前的通道应当走在前面");
        h.succeed();
    }
}
