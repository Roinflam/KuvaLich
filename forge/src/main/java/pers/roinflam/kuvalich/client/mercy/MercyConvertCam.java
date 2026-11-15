package pers.roinflam.kuvalich.client.mercy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyCameraPath;

/**
 * 感化收尾「背身而去」的双人侧面机位：拔刀那一下还在跪地特写里，后跃半空切过来。照候选的运镜按世界方向慢慢绕：
 * 开头在两人侧面、它右手边那一侧，他转身走开时绕到它身后（前景是它的背、远处是走开的人），他回头时停在侧后方，
 * 看得见他回头的脸和它垂首。注视点在两人中间，跟着走开的人挪。
 *
 * <p>减弱档不绕、不跟人，只按同一组距离推拉；静态档是一个固定机位，框住它和人走到的地方。整条路线（各档）
 * 在第一次用到时查遮挡，本侧不行镜像到另一侧，都不行降一档，最后退回跪地特写</p>
 */
@OnlyIn(Dist.CLIENT)
final class MercyConvertCam {

    /**
     * 候选 conv2v6 的运镜，时刻相对跪地特写、整体晚 200 ms（前面多了拔刀和后跃）：
     * {时刻, 侧角（0 = 处决者起始那一侧，+90 = 它右手边，180 = 它身后）, 俯仰, 离注视点多远, 注视点离地多高}
     */
    private static final float[][] KEYS = {
            {0, 60, -6, 4.8F, 1.5F}, {620, 62, -6, 4.8F, 1.5F}, {1320, 80, -6, 5.4F, 1.6F},
            {2580, 100, -6, 6.4F, 1.7F}, {3560, 106, -6, 6.4F, 1.7F}, {5700, 106, -7, 6.2F, 1.7F}};
    private static final float FOV = 60.0F;
    /** 静态档 / 减弱档的侧角 */
    private static final float FIXED_AZ = 92.0F;
    private static final float STATIC_DIST = 6.3F, STATIC_LOOK_H = 1.6F;
    /** 注视点在两人连线上偏人那一侧多少 */
    private static final double LOOK_BIAS = 0.5D;
    /** 注视点跟人时前后各取一点平均，人顿一下镜头不跟着顿 */
    private static final int LOOK_WINDOW_MS = 160;
    /** 镜头离处决者至少这么远 */
    private static final double CLEAR_OF_EXECUTOR = 0.9D;

    private static final MercyPose.Body BODY = new MercyPose.Body();
    private static final float[] KEY = new float[5];

    private MercyConvertCam() {
    }

    /** 查过遮挡、定下用哪一侧哪一档；返回切过来那一刻的机位（{@code ok} 为假就退回跪地特写） */
    static MercyCamera.ShotPose fit(MercyClient.Exec e) {
        MercyBeats.Lich b = MercyBeats.lich(e.variant);
        MercyCameraPath.Motion asked = e.shots.motion;
        MercyCameraPath.Motion[] tries = asked == MercyCameraPath.Motion.STATIC
                ? new MercyCameraPath.Motion[]{MercyCameraPath.Motion.STATIC}
                : new MercyCameraPath.Motion[]{asked, MercyCameraPath.Motion.STATIC};
        for (MercyCameraPath.Motion mo : tries) {
            for (int mirror : new int[]{1, -1}) {
                e.convertCamMirror = mirror;
                e.convertCamMotion = mo;
                if (clear(e, b)) {
                    MercyCamera.ShotPose p = at(e, b.convertCut);
                    return new MercyCamera.ShotPose(p.pos(), p.look(), FOV, true);
                }
            }
        }
        e.convertCamMirror = 1;
        e.convertCamMotion = MercyCameraPath.Motion.STATIC;
        MercyCamera.ShotPose p = at(e, b.convertCut);
        return new MercyCamera.ShotPose(p.pos(), p.look(), FOV, false);
    }

    private static boolean clear(MercyClient.Exec e, MercyBeats.Lich b) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return true;
        }
        for (int t = b.convertCut; t <= e.handbackMs; t += 200) {
            MercyCamera.ShotPose p = at(e, t);
            if (level.clip(new ClipContext(p.look(), p.pos(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    mc.player)).getType() != HitResult.Type.MISS) {
                return false;
            }
            if (!MercyCameraPath.outsideColumn(p.pos(), e.pl, 0.75D * e.shots.targetWidth,
                    e.pl.y + e.shots.targetHeight + 0.3D)) {
                return false;
            }
            if (executorAt(e, t).add(0.0D, 1.0D, 0.0D).distanceTo(p.pos()) < CLEAR_OF_EXECUTOR) {
                return false;
            }
        }
        return true;
    }

    /** 这一刻的机位（按查遮挡时定下的那一侧那一档） */
    static MercyCamera.ShotPose at(MercyClient.Exec e, long t) {
        MercyBeats.Lich b = MercyBeats.lich(e.variant);
        MercyCameraPath.Motion mo = e.convertCamMotion;
        double u = e.shots.unit;
        float az, el, dist, lh;
        Vec3 look;
        if (mo == MercyCameraPath.Motion.STATIC) {
            az = FIXED_AZ;
            el = -6.0F;
            dist = STATIC_DIST;
            lh = STATIC_LOOK_H;
        } else {
            key(t - b.closeup);
            az = mo == MercyCameraPath.Motion.FULL ? KEY[1] : FIXED_AZ;
            el = KEY[2];
            dist = KEY[3];
            lh = KEY[4];
        }
        if (mo == MercyCameraPath.Motion.FULL) {
            Vec3 m = executorAt(e, t - LOOK_WINDOW_MS).add(executorAt(e, t)).add(executorAt(e, t + LOOK_WINDOW_MS))
                    .scale(1.0D / 3.0D);
            look = e.pl.lerp(m, LOOK_BIAS);
        } else {
            look = e.pl.lerp(executorAt(e, e.handbackMs - 50L), LOOK_BIAS);
        }
        look = new Vec3(look.x, e.pl.y + lh * u, look.z);
        // 侧角从 F 往它右手边（−S）转；整段路线绕目标转过的角度跟着加上
        double th = Math.toRadians(e.convertRoom.bearing() - e.convertCamMirror * az);
        Vec3 dir = e.f.scale(Math.cos(th)).add(e.s.scale(Math.sin(th) * e.side));
        double r = dist * (0.5D + 0.5D * u);
        double er = Math.toRadians(el);
        Vec3 pos = look.add(dir.scale(r * Math.cos(er))).add(0.0D, r * Math.sin(er), 0.0D);
        return new MercyCamera.ShotPose(pos, look, FOV, true);
    }

    /** 处决者此刻（整身轨道）在哪；轨道外按站位 */
    private static Vec3 executorAt(MercyClient.Exec e, long t) {
        long tt = Math.max(MercyBeats.lich(e.variant).closeup, Math.min(t, e.handbackMs - 1L));
        if (e.pose != null && MercyPose.body(e, tt, BODY)) {
            return new Vec3(BODY.x, e.pe.y, BODY.z);
        }
        return e.pe;
    }

    /** 关键帧之间按 smoothstep 走 */
    private static void key(long t) {
        float[] a = KEYS[0], c = KEYS[KEYS.length - 1];
        if (t <= a[0]) {
            System.arraycopy(a, 0, KEY, 0, 5);
            return;
        }
        if (t >= c[0]) {
            System.arraycopy(c, 0, KEY, 0, 5);
            return;
        }
        for (int i = 1; i < KEYS.length; i++) {
            float[] q = KEYS[i];
            if (t <= q[0]) {
                float[] p = KEYS[i - 1];
                float x = (t - p[0]) / (q[0] - p[0]);
                float s = x * x * (3.0F - 2.0F * x);
                for (int j = 0; j < 5; j++) {
                    KEY[j] = p[j] + (q[j] - p[j]) * s;
                }
                return;
            }
        }
    }
}
