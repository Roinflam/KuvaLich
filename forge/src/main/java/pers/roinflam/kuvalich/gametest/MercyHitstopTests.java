package pers.roinflam.kuvalich.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyHitstop;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;

/**
 * 处决顿帧的时间映射：只在客户端拧演出钟，这里校验表本身（客户端类在测试服上加载不了）
 */
@GameTestHolder(Reference.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MercyHitstopTests {

    private MercyHitstopTests() {
    }

    /**
     * 每个玄骸套路、每种结果：顿帧没被缩短，服务端可见时刻的滞后不超过上限，一刀的停 + 追在下一刀之前走完，
     * 演出钟不倒走、不超前，刺入那一刻正好等于真实时间，成功时停在选择那一拍之前已经追平、抽刀不晚于死亡
     */
    @GameTest(template = "empty")
    public static void hitstopFitsServerBeats(GameTestHelper h) {
        int lichs = 0;
        for (MercyVariant v : MercyVariant.values()) {
            if (v.thrall) {
                continue;
            }
            lichs++;
            MercyBeats.Lich b = MercyBeats.lich(v);
            h.assertTrue(!b.hitstopShortened(), v + "：建表时有顿帧被缩短了，节拍表和顿帧参数对不上");
            for (int stabs = 1; stabs <= 3; stabs++) {
                for (int prefix = 0; prefix <= stabs; prefix++) {
                    String c = v + " 刀数 " + stabs + " 前缀 " + prefix;
                    int n = MercyHitstop.stabCount(stabs, prefix);
                    int[] s = Arrays.copyOf(b.stabMs(), n);
                    int[] hs = b.hitstopMs(stabs, prefix);
                    h.assertTrue(hs.length == n, c + "：顿帧个数应当等于实际捅的刀数 " + n + "，实际 " + hs.length);
                    for (int i = 0; i < n; i++) {
                        h.assertTrue(hs[i] == MercyHitstop.desired(i, n) && hs[i] > 0,
                                c + "：第 " + (i + 1) + " 刀应当停 " + MercyHitstop.desired(i, n) + " ms，实际 " + hs[i]);
                        h.assertTrue(MercyHitstop.warp(s, hs, s[i]) == s[i], c + "：第 " + (i + 1) + " 刀刺入那一刻演出钟应当等于真实时间");
                        if (i + 1 < n) {
                            h.assertTrue(s[i] + MercyHitstop.span(hs[i]) <= s[i + 1],
                                    c + "：第 " + (i + 1) + " 刀的停顿和追回跨进了下一刀");
                        }
                    }
                    for (int t : b.serverVisibleMs(stabs, prefix)) {
                        double lag = MercyHitstop.lag(s, hs, t);
                        h.assertTrue(lag <= MercyHitstop.MAX_SERVER_LAG_MS, c + "：服务端 " + t + " ms 时演出钟落后 " + lag + " ms");
                    }
                    long prev = Long.MIN_VALUE;
                    boolean paused = false;
                    for (long t = 0; t <= b.successEnd + 500; t++) {
                        long w = MercyHitstop.warp(s, hs, t);
                        h.assertTrue(w <= t && w >= prev, c + "：演出钟在 " + t + " ms 倒走或超前（" + w + "）");
                        if (w == prev) {
                            paused = true;
                        }
                        prev = w;
                    }
                    h.assertTrue(paused, c + "：应当有停顿");
                    if (stabs == 3 && prefix == 3) {
                        h.assertTrue(MercyHitstop.lag(s, hs, b.choiceOpen) == 0.0D, c + "：开选择窗口时演出钟应当已经追平");
                        int deathMs = b.successDeathTick * MercyBeats.TICK_MS;
                        h.assertTrue(MercyHitstop.warp(s, hs, deathMs) >= b.drawOut, c + "：抽刀晚于服务端让它死");
                    }
                }
            }
        }
        h.assertTrue(lichs > 0, "应当至少有一个玄骸套路");
        h.succeed();
    }

    /** 片段速度按比值给：任何时刻 速度 × 开播以来的真实时长 = 演出钟走过的量，停顿里是 0，追平以后回到 1 */
    @GameTest(template = "empty")
    public static void clipSpeedFollowsWarp(GameTestHelper h) {
        MercyBeats.Lich b = MercyBeats.lich(MercyVariant.LICH_A);
        int[] s = b.stabMs();
        int[] hs = b.hitstopMs(3, 3);
        long[] starts = {0L, s[0], s[1] - 120L, s[2]};
        for (long t0 : starts) {
            for (long t = t0 + 1; t <= s[2] + 1500L; t += 3) {
                double speed = MercyHitstop.clipSpeed(s, hs, t0, t);
                double played = speed * (t - t0);
                double want = MercyHitstop.warp(s, hs, t) - MercyHitstop.warp(s, hs, t0);
                h.assertTrue(Math.abs(played - want) <= 1.0D,
                        "片段从 " + t0 + " 开播、" + t + " 时应当播到 " + want + " ms，实际 " + played);
            }
        }
        h.assertTrue(MercyHitstop.clipSpeed(s, hs, s[2], s[2] + hs[2] / 2) == 0.0D, "第三刀的停顿里片段应当停住");
        h.assertTrue(MercyHitstop.clipSpeed(s, hs, s[2], s[2]) == 0.0D, "刚在刺入那一刻开播的片段应当先停住");
        h.assertTrue(MercyHitstop.clipSpeed(s, hs, s[2] + 1000, s[2] + 1500) == 1.0D, "追平以后开播的片段速度应当是 1");
        h.succeed();
    }
}
