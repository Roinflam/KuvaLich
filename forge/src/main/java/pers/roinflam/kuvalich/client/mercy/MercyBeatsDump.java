package pers.roinflam.kuvalich.client.mercy;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.mercy.MercyAnimSelector;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyCameraPath;
import pers.roinflam.kuvalich.mercy.MercyHitstop;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * 把每套处决每个结果按 10 ms 步长导成文本：时间轴各阶段、关键时刻、机位、目标朝向、玄骸片段、界面透明度、
 * 处决者姿态。只做计算，不碰渲染和世界，改节拍前后各导一份做 diff。
 *
 * <p>开发命令 {@code /kuvafxc mercybeats} 写到游戏目录；不起客户端时用 {@link #main} 跑（参数是输出文件）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class MercyBeatsDump {

    public static final int STEP_MS = 10;

    /** 玄骸几种结果：{刀数, 前缀}，和 {@code mercytest} 的名字对着 */
    private static final int[][] LICH_CASES = {{3, 3}, {3, 0}, {3, 1}, {3, 2}, {1, 1}, {2, 2}, {1, 0}, {2, 0}, {2, 1}};
    private static final String[] LICH_NAMES = {"success", "fail1", "fail2", "fail3", "adv1", "adv2", "k1fail1", "k2fail1",
            "k2fail2"};

    private MercyBeatsDump() {
    }

    public static String dump() {
        StringBuilder out = new StringBuilder(1 << 24);
        for (MercyVariant v : MercyVariant.values()) {
            if (v.thrall) {
                plan(out, v.name(), new MercyPlan.Plan(1, 1, v, new int[]{-1, -1, -1}, new int[3], false, 0, false, false, false),
                        false);
                continue;
            }
            for (int i = 0; i < LICH_CASES.length; i++) {
                int stabs = LICH_CASES[i][0], prefix = LICH_CASES[i][1];
                boolean success = stabs == 3 && prefix == 3;
                MercyPlan.Plan p = new MercyPlan.Plan(stabs, prefix, v, new int[]{0, 1, 2}, new int[]{3, 3, 3}, success,
                        1, false, false, false);
                plan(out, v.name() + " " + LICH_NAMES[i], p, false);
                if (success) {
                    plan(out, v.name() + " " + LICH_NAMES[i] + " short", p, true);
                    plan(out, v.name() + " convert", p.withConvert(), false);
                }
            }
        }
        return out.toString();
    }

    private static void plan(StringBuilder out, String name, MercyPlan.Plan p, boolean shortOutro) {
        int bits = MercyPlan.encode(p);
        int handback = MercyTimeline.handbackMs(p, shortOutro);
        int end = MercyTimeline.endMs(p, shortOutro);
        out.append("=== ").append(name).append('\n');
        out.append("outcome ").append(MercyTimeline.outcome(p))
                .append(" stabs ").append(Arrays.toString(MercyTimeline.stabTimes(p)))
                .append(" lastStab ").append(MercyTimeline.lastStab(p))
                .append(" failFill ").append(MercyTimeline.failFillMs(p))
                .append(" verdictFill ").append(MercyTimeline.verdictFillMs(p))
                .append(" breakFree ").append(MercyTimeline.breakFreeMs(p))
                .append(" wide ").append(MercyTimeline.wideFromMs(p))
                .append(" push ").append(MercyTimeline.pushDistance(p))
                .append(" handback ").append(handback)
                .append(" hudUntil ").append(MercyTimeline.hudUntilMs(p, shortOutro))
                .append(" end ").append(end).append('\n');
        out.append("stages ").append(Arrays.toString(MercyTimeline.build(p).stageTimes())).append('\n');
        int wrong = MercyHud.wrongStab(p);
        int[] fill = new int[4];
        int banner = -1;
        if (!p.variant().thrall) {
            MercyBeats.Lich b = MercyBeats.lich(p.variant());
            for (int k = 1; k <= 3; k++) {
                fill[k] = MercyHud.fillMs(p, k);
            }
            banner = wrong == 0 ? -1 : b.fill(wrong) + b.failBanner;
            int[] st = MercyTimeline.stabTimes(p);
            int[] hs = b.hitstopMs(p.stabs(), p.prefix());
            double maxLag = 0.0D;
            for (int ms : b.serverVisibleMs(p.stabs(), p.prefix())) {
                maxLag = Math.max(maxLag, MercyHitstop.lag(st, hs, ms));
            }
            out.append("hitstop ").append(Arrays.toString(hs)).append(" serverLagMax ")
                    .append(Math.round(maxLag)).append('\n');
        } else {
            // 奴仆没有三格；改之前这里打的是全局那份
            fill = new int[]{0, 830, 1680, 2670};
            banner = -1;
        }
        out.append("hud fill ").append(fill[1]).append(' ').append(fill[2]).append(' ').append(fill[3])
                .append(" banner ").append(banner)
                .append('\n');
        for (int t = 0; t <= end + STEP_MS; t += STEP_MS) {
            RawAnimation clip = MercyAnimSelector.executing(bits, p.variant().thrall, t,
                    MercyTimeline.outcome(p) == MercyTimeline.Outcome.CONVERT);
            out.append(t)
                    .append(" shot ").append(MercyCamera.plannedShot(p, handback, t))
                    .append(" face ").append(MercyClient.facingAt(p, t))
                    .append(" turn ").append(MercyClient.turnProgress(p, t))
                    .append(" clip ").append(clip == null || clip.getAnimationStages().isEmpty() ? "-"
                            : clip.getAnimationStages().get(0).animationName())
                    .append(" blade ").append(MercyTimeline.bladeExtension(p, t, shortOutro))
                    .append(" vanish ").append(MercyTimeline.targetMayVanish(p, t))
                    .append(" hud ").append(p.variant().thrall ? "- -"
                            : MercyHud.groupAlpha(p, t, true) + " " + MercyHud.groupAlpha(p, t, false))
                    .append('\n');
        }
        if (!p.variant().thrall && MercyCameraPath.has(p.variant())) {
            // 镜头轨迹（真实钟 = 演出钟，不算顿帧）；静态档没有这一层，上面的 shot 列就是它
            int last = MercyTimeline.lastStab(p);
            float[] m = new float[MercyCameraPath.CHANNELS];
            for (MercyCameraPath.Motion mo : new MercyCameraPath.Motion[]{MercyCameraPath.Motion.FULL,
                    MercyCameraPath.Motion.REDUCED}) {
                out.append("camera ").append(mo).append('\n');
                for (int t = 0; t <= end; t += STEP_MS) {
                    MercyCamera.Shot shot = MercyCamera.plannedShot(p, handback, t);
                    if (shot.ordinal() < MercyCamera.Shot.A.ordinal() || shot.ordinal() > MercyCamera.Shot.H.ordinal()
                            || !MercyCameraPath.sample(p.variant(), last, mo, t, t, m)) {
                        continue;
                    }
                    out.append(t).append(" seg ").append(MercyCameraPath.segment(p.variant(), last, t))
                            .append(String.format(Locale.ROOT, " dolly %.3f orbit %.2f rise %.3f roll %.2f follow %.2f",
                                    m[0], m[1], m[2], m[3], m[4]))
                            .append('\n');
                }
            }
        }
        out.append("pose\n");
        MercyPose.dump(p, shortOutro, 1.0F, STEP_MS, out);
        if (p.variant().thrall) {
            out.append("pose scale 0.7\n");
            MercyPose.dump(p, shortOutro, 0.7F, STEP_MS, out);
        }
    }

    public static void write(Path file) throws IOException {
        Files.writeString(file, dump(), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws IOException {
        write(Path.of(args.length > 0 ? args[0] : "mercy-beats.txt"));
    }
}
