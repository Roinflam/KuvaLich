package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 处决运镜：只对本地处决者、只在玄骸处决、只在 {@code mercy_camera} 开着时接管相机
 *
 * <p>机位都在处决开始那一刻按世界帧算好（{@code Pl} 目标脚底、{@code Pe} 处决者站位、{@code F} 从目标指向处决者、
 * {@code S = Y × F}、{@code U} 向上），每个机位第一次切进去时做一次遮挡检测，之后整段固定、不逐帧算，免得镜头抖。
 * 镜头在 {@code +S} 一侧朝 {@code −S} 看时 {@code +F} 在画面左边，所以三套机位都是处决者在左、目标在中 / 右；
 * 成功收尾的跪地特写和第三人称都放在 {@code −S} 一侧，处决者在右，两个镜头之间不越轴。</p>
 *
 * <p>跟着目标走的量（离目标多远、看它哪个高度）按目标的站姿身高缩放，{@link #RIGS} 里的数是按玄骸（站高 3.4 格、
 * 倒地时头在 2 格上下）定的；以处决者为原点的量按玩家身材算，不缩放。处决者站得离目标很近（0.95~1.25 格），
 * 机位又要同时拍到两个人，所以每个机位都查一遍：镜头不能落在方块里、处决者身上、目标身体范围里，视线前段不能穿过
 * 处决者的头和身子；第三人称收尾还要查目标有没有挡住处决者的头和胸。挡住了先绕注视点往外侧转、再镜像到另一侧，
 * 都不行才允许被方块挡时往回收，最后才退回玩家镜头。</p>
 *
 * <p>位置和朝向由 {@code MixinCameraMercy} 在 {@code Camera#setup} 末尾整个换掉；推镜（FOV）、震动、甩镜、滚转走
 * Forge 的视角事件。机位 FOV 是绝对值，不跟玩家的 FOV 设置（电影镜头要一致）。不做黑边、不做动态模糊。</p>
 *
 * <p>失败和「挣开站起」（这次验的几刀全对、还没到第三刀）在玄骸挣开之后都切宽镜头，看它和被推开的处决者：
 * 失败在判错那一格 + 2000 ms 切、看它化雾；挣开站起在最后那一格 + 1200 ms 切、看它站起来，2300 ms 交还。</p>
 *
 * <p>{@code mercy_camera} 关着时不接管位置，开始时切到原版第三人称背后视角、交还时切回；界面、压混音、声音照常。
 * 奴仆处决不切镜头，{@code thrall_third_person} 开着时同样整段切第三人称背后视角。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MercyCamera {

    /** 跪地特写的压暗层 */
    public static final ResourceLocation SHADE_ID = new ResourceLocation(Reference.MOD_ID, "mercy_shade");
    private static final ResourceLocation VIGNETTE = new ResourceLocation(Reference.MOD_ID, "textures/gui/fx/vignette.png");

    /** 镜头分段 */
    public enum Shot {
        /** 玩家自己的镜头，不接管 */
        GAME,
        /** 侧面中近景 */
        A,
        /** 过肩近景 */
        B,
        /** 贴身仰拍 */
        C,
        /** 绕背：侧面再远一点，整段翻滚都在画里 */
        D,
        /** 踏背：低机位仰拍 */
        E,
        /** 成功：跪地特写 */
        CLOSEUP,
        /** 成功：膝盖高度第三人称 */
        THIRD,
        /** 失败、挣开站起：宽镜头 */
        WIDE
    }

    private static final Shot[] SHOTS = Shot.values();

    /** 一个机位：位置、注视点、FOV；{@code ok} 为假时这一段退回玩家镜头 */
    record ShotPose(Vec3 pos, Vec3 look, float fov, boolean ok) {
    }

    /**
     * 一个机位的定义：位置、注视点各是 (F, S, U) 三个系数，FOV 是绝对值。
     * 以目标脚底为原点的量按目标身高缩放；位置在 {@code fromExecutor}、注视点在 {@code lookFromExecutor} 时
     * 改以处决者站位为原点、按玩家身材不缩放
     */
    record Rig(boolean fromExecutor, boolean lookFromExecutor, double pf, double ps, double pu, double lf, double ls,
               double lu, float fov) {
    }

    /**
     * 各机位，按 {@link Shot} 的顺序（GAME、WIDE 不查表）。
     * <ul>
     *   <li>A 侧面中近景：离目标 2.9 格、胸口偏上的高度平拍，两人都在画里；</li>
     *   <li>B 过肩：处决者右肩后上方，越过他的肩看背对着的目标的后颈；</li>
     *   <li>C 贴身仰拍：斜前方低机位往上看，目标整个头盔在画里；</li>
     *   <li>D 绕背：和 A 同一侧，离目标 3.8 格。处决者从镜头和目标之间翻过去，放在绕背的那一侧才看得到，
     *       放在另一侧翻滚全被目标挡住；</li>
     *   <li>E 踏背：正侧面、离目标 3.3 格，注视点在目标身体中心稍高处。处决者先在它背后、再蹲到它背上、最后闪到它正面，
     *       三处连同背上那一下的高度都在画里；</li>
     *   <li>跪地特写：正侧面（{@code −S} 一侧，目标的右手边、处决者的左手边），在两人中间、离中轴约 3 格，腰的高度略往上看：
     *       跪着的目标和处决者都是侧身入画，处决者的头、两条胳膊和扎进目标身体的刀连成一个人。
     *       原先机位偏到处决者身后，只拍得到他的后脑勺和从目标肩甲两边伸出来的两只手，看着像拼了两个人；
     *       {@code +S} 一侧拍不到脸：目标搭在膝上的左臂和大肩甲正好挡在脸和刀前面（这一段也不画目标手里的武器，
     *       见 {@link #hidesTargetWeapon}）；</li>
     *   <li>膝盖高度第三人称：同样在 {@code −S} 一侧，处决者左手边外侧 1.8 格，低机位仰拍。位置和注视点都以处决者为原点，
     *       各套路站距不同（0.95~1.25 格）时处决者在画里的位置不变。{@code +S} 一侧拍不全处决者：收尾时目标两臂环着他，
     *       那一侧低垂的头盔和胳膊正好挡在他的头和胸前面。</li>
     * </ul>
     */
    static final Rig[] RIGS = {
            null,
            new Rig(false, false, 0.45D, 2.85D, 1.6D, 0.5D, 0.0D, 1.7D, 70.0F),
            new Rig(true, false, 1.25D, 0.9D, 2.15D, -0.6D, 0.0D, 1.7D, 70.0F),
            new Rig(false, false, 1.9D, 1.9D, 0.85D, 0.3D, 0.0D, 2.5D, 74.0F),
            new Rig(false, false, 0.2D, 3.8D, 1.35D, 0.0D, 0.0D, 1.45D, 64.0F),
            new Rig(false, false, -0.15D, 3.3D, 1.5D, -0.15D, 0.0D, 1.8D, 68.0F),
            new Rig(false, false, 0.55D, -2.75D, 1.2D, 0.5D, 0.0D, 1.7D, 55.0F),
            new Rig(true, true, 0.6D, -1.8D, 0.55D, -0.65D, 0.0D, 1.55D, 70.0F),
            null
    };

    /** 机位表按这个站姿身高定（玄骸） */
    private static final double REF_HEIGHT = 3.4D;
    /** 处决者（按玩家身材）：头的球心离脚底多高、往目标那边探出多少、半径；身子是脚底到肩的一根粗杆 */
    private static final double EXEC_HEAD_U = 1.7D;
    private static final double EXEC_HEAD_F = -0.2D;
    private static final double EXEC_HEAD_R = 0.42D;
    private static final double EXEC_BODY_LO = 0.1D;
    private static final double EXEC_BODY_HI = 1.35D;
    private static final double EXEC_BODY_R = 0.33D;
    /** 处决者胸口离脚底多高、往目标那边探出多少 */
    private static final double EXEC_CHEST_U = 1.25D;
    private static final double EXEC_CHEST_F = -0.1D;
    /** 查处决者有没有被目标挡住时，连线最后贴着处决者的这一段不算 */
    private static final double EXEC_SKIP = 0.25D;
    /** 跪着的目标当一根竖杆：从脚底往上多高到多高（按站高 3.4 格的玄骸定，随目标身高缩放）、半径占宽度的比例 */
    private static final double TARGET_BODY_LO = 0.3D;
    private static final double TARGET_KNEEL_TOP = 2.4D;
    private static final double TARGET_BODY_R = 0.45D;
    /** 视线最后这一段（贴着注视点）不查处决者：注视点本来就在两人之间 */
    private static final double SIGHT_SKIP = 0.3D;
    /** 镜头离目标中轴至少这么远（按目标宽度的倍数）：再近就钻进它张开的胳膊、盔甲里了 */
    private static final double TARGET_CLEAR = 0.75D;
    /** 挡住时绕注视点往外侧转的角度，依次试 */
    private static final double[] SWING_DEG = {0.0D, 15.0D, 30.0D, 45.0D, 60.0D};

    /** 每场处决自己的镜头状态 */
    static final class ShotCache {
        /** 开始那一刻的 mercy_camera */
        boolean cameraOn;
        boolean switched;
        @Nullable
        CameraType saved;
        /** 目标站姿身高 ÷ {@link #REF_HEIGHT} */
        double unit = 1.0D;
        /** 目标站姿高、宽（格） */
        double targetHeight = REF_HEIGHT;
        double targetWidth = 1.75D;
        final ShotPose[] poses = new ShotPose[SHOTS.length];
    }

    // 这一帧给 Mixin 的结果（只在渲染线程用，不分配）
    private static double outX, outY, outZ;
    private static float outYaw, outPitch;

    /**
     * 开发命令 {@code /kuvafxc mercyorbit} 摆的固定机位：绕目标转的角度（0 = 处决者那一侧，90 = 侧面）、离目标多远、
     * 镜头和注视点离地多高（平视）。距离不大于 0 就是关着
     */
    static float devOrbitAngle, devOrbitDist, devOrbitHeight;

    private MercyCamera() {
    }

    // ==================== 分段 ====================

    /** 这一场会不会接管相机 */
    static boolean takesOver(MercyClient.Exec e) {
        return e.cinematic && e.shots.cameraOn;
    }

    /** 本地处决者此刻在哪个机位；没有本地处决时是 {@link Shot#GAME}（处决界面据此在宽镜头里不画三格） */
    public static Shot currentShot() {
        MercyClient.Exec e = MercyClient.local();
        return e == null ? Shot.GAME : shotAt(e, e.elapsed());
    }

    /**
     * 跪地特写这一段不画目标手里的武器：特写只能放在目标持武器的那一侧（另一侧被它搭在膝上的胳膊挡着），
     * 各把武器长短不一，长柄的会横在镜头前、占掉小半个画面。切镜头的那一下看不出少了东西，第三人称收尾时照常画
     */
    public static boolean hidesTargetWeapon(int entityId) {
        MercyClient.Exec e = MercyClient.local();
        return e != null && e.targetId == entityId && !e.handedBack && shotAt(e, e.elapsed()) == Shot.CLOSEUP;
    }

    static Shot shotAt(MercyClient.Exec e, long t) {
        Shot s = plannedShot(e, t);
        if (s == Shot.GAME) {
            return s;
        }
        return pose(e, s).ok ? s : Shot.GAME;
    }

    private static Shot plannedShot(MercyClient.Exec e, long t) {
        return takesOver(e) ? plannedShot(e.plan, e.handbackMs, t) : Shot.GAME;
    }

    /** 照计划这一刻该用哪个机位（不看地形放不放得下） */
    static Shot plannedShot(MercyPlan.Plan plan, int handbackMs, long t) {
        if (plan.variant().thrall) {
            return Shot.GAME;
        }
        MercyBeats.Lich b = MercyBeats.lich(plan.variant());
        if (t < b.cutIn || t >= handbackMs) {
            return Shot.GAME;
        }
        Shot own = switch (plan.variant().camera) {
            case 1 -> Shot.B;
            case 2 -> Shot.C;
            case 3 -> Shot.D;
            case 4 -> Shot.E;
            default -> Shot.A;
        };
        return switch (MercyTimeline.outcome(plan)) {
            case SUCCESS -> t < b.closeup ? own : t < b.third ? Shot.CLOSEUP : Shot.THIRD;
            // 感化不切第三人称，特写一直到交还
            case CONVERT -> t < b.closeup ? own : Shot.CLOSEUP;
            case ADVANCE, FAIL -> t < MercyTimeline.wideFromMs(plan) ? own : Shot.WIDE;
            case THRALL -> Shot.GAME;
        };
    }

    // ==================== 机位 ====================

    private static ShotPose pose(MercyClient.Exec e, Shot s) {
        ShotPose p = e.shots.poses[s.ordinal()];
        if (p == null) {
            p = fit(e, s);
            e.shots.poses[s.ordinal()] = p;
        }
        return p;
    }

    /**
     * 算机位并做遮挡检测：先在本侧绕注视点往外转着试，再镜像到另一侧试，都要求镜头和注视点之间没有方块；
     * 还不行就允许被方块挡、放到交点往回 0.2 格（离注视点不到 0.8 格的不要），再按同样的顺序试一遍；都不行退回玩家镜头
     */
    private static ShotPose fit(MercyClient.Exec e, Shot s) {
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 2; i++) {
                int side = i == 0 ? e.side : -e.side;
                ShotPose base = place(e, s, side);
                for (double deg : SWING_DEG) {
                    ShotPose p = check(e, s, swing(e, base.pos(), base.look(), side, deg), base.look(), base.fov(),
                            pass == 1);
                    if (p != null) {
                        return p;
                    }
                }
            }
        }
        ShotPose p = place(e, s, 1);
        return new ShotPose(p.pos(), p.look(), p.fov(), false);
    }

    /** 机位在本侧（{@code side} = 1）或镜像到另一侧（-1）时的位置和注视点 */
    private static ShotPose place(MercyClient.Exec e, Shot s, int side) {
        double u = e.shots.unit;
        if (s == Shot.WIDE) {
            // 离玄骸 ≈5.4 格的低机位，在它背后那一侧，看玄骸和被推开后的处决者（失败 Pe + 1.5F，挣开站起 Pe + 1.0F）的中点
            Vec3 pushed = e.pe.add(e.f.scale(MercyTimeline.pushDistance(e.plan)));
            Vec3 mid = e.pl.add(pushed).scale(0.5D);
            return new ShotPose(frame(e, e.pl, -4.2D * u, 3.4D * u * side, 0.6D * u), mid.add(0.0D, 1.2D * u, 0.0D),
                    70.0F, true);
        }
        Rig r = RIGS[s.ordinal()];
        Vec3 look = r.lookFromExecutor() ? frame(e, e.pe, r.lf(), r.ls() * side, r.lu())
                : frame(e, e.pl, r.lf() * u, r.ls() * u * side, r.lu() * u);
        Vec3 pos = r.fromExecutor() ? frame(e, e.pe, r.pf(), r.ps() * side, r.pu())
                : frame(e, e.pl, r.pf() * u, r.ps() * u * side, r.pu() * u);
        return new ShotPose(pos, look, r.fov(), true);
    }

    private static Vec3 frame(MercyClient.Exec e, Vec3 o, double kf, double ks, double ku) {
        return o.add(e.f.scale(kf)).add(e.s.scale(ks)).add(0.0D, ku, 0.0D);
    }

    /** 把镜头绕过注视点的竖轴往外侧（{@code F} 转向 {@code side·S}）转 {@code deg} 度，高度不变 */
    private static Vec3 swing(MercyClient.Exec e, Vec3 pos, Vec3 look, int side, double deg) {
        if (deg == 0.0D) {
            return pos;
        }
        Vec3 v = pos.subtract(look);
        double a = v.dot(e.f);
        double b = v.dot(e.s) * side;
        double r = Math.toRadians(deg);
        double c = Math.cos(r);
        double sn = Math.sin(r);
        double a2 = a * c - b * sn;
        double b2 = (a * sn + b * c) * side;
        return look.add(e.f.scale(a2)).add(e.s.scale(b2)).add(0.0D, v.y, 0.0D);
    }

    /** 方块、处决者、目标都没挡着就给出这个机位；{@code allowClip} 时被方块挡住的往回收到交点前 */
    @Nullable
    private static ShotPose check(MercyClient.Exec e, Shot s, Vec3 pos, Vec3 look, float fov, boolean allowClip) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != null && mc.player != null) {
            BlockHitResult hit = level.clip(new ClipContext(look, pos, ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                if (!allowClip) {
                    return null;
                }
                Vec3 dir = pos.subtract(look);
                double len = dir.length();
                if (len < 1.0E-4D) {
                    return null;
                }
                pos = hit.getLocation().subtract(dir.scale(0.2D / len));
                if (pos.distanceTo(look) < 0.8D) {
                    return null;
                }
            }
        }
        if (!clearOfBodies(e, pos, look)) {
            return null;
        }
        return s != Shot.THIRD || executorInView(e, pos) ? new ShotPose(pos, look, fov, true) : null;
    }

    /**
     * 镜头不在目标身体范围里，视线（去掉贴着注视点的一小段）不穿过处决者的头和身子。
     * 处决者按站位算（整场锁在那里），头往目标那边探出一点（各套路出刀时都前倾）
     */
    private static boolean clearOfBodies(MercyClient.Exec e, Vec3 pos, Vec3 look) {
        double dx = pos.x - e.pl.x;
        double dz = pos.z - e.pl.z;
        double clear = TARGET_CLEAR * e.shots.targetWidth;
        if (dx * dx + dz * dz < clear * clear && pos.y < e.pl.y + e.shots.targetHeight + 0.3D) {
            return false;
        }
        Vec3 dir = pos.subtract(look);
        double len = dir.length();
        if (len <= SIGHT_SKIP) {
            return false;
        }
        Vec3 from = look.add(dir.scale(SIGHT_SKIP / len));
        Vec3 head = e.pe.add(e.f.scale(EXEC_HEAD_F)).add(0.0D, EXEC_HEAD_U, 0.0D);
        if (segmentPointDistance(from, pos, head) < EXEC_HEAD_R) {
            return false;
        }
        return segmentSegmentDistance(from, pos, e.pe.add(0.0D, EXEC_BODY_LO, 0.0D),
                e.pe.add(0.0D, EXEC_BODY_HI, 0.0D)) >= EXEC_BODY_R;
    }

    /**
     * 第三人称收尾要拍到处决者：镜头到处决者头、胸的连线不能穿过跪着的目标的身子。
     * 目标按一根竖杆算（脚底往上到跪姿的头顶，半径按宽度），连线最后贴着处决者的一小段不算，他本来就站在目标身前
     */
    private static boolean executorInView(MercyClient.Exec e, Vec3 pos) {
        double u = e.shots.unit;
        double r = TARGET_BODY_R * e.shots.targetWidth;
        Vec3 lo = e.pl.add(0.0D, TARGET_BODY_LO * u, 0.0D);
        Vec3 hi = e.pl.add(0.0D, TARGET_KNEEL_TOP * u, 0.0D);
        for (int i = 0; i < 2; i++) {
            Vec3 p = i == 0 ? e.pe.add(e.f.scale(EXEC_HEAD_F)).add(0.0D, EXEC_HEAD_U, 0.0D)
                    : e.pe.add(e.f.scale(EXEC_CHEST_F)).add(0.0D, EXEC_CHEST_U, 0.0D);
            Vec3 d = p.subtract(pos);
            double len = d.length();
            if (len <= EXEC_SKIP) {
                return false;
            }
            Vec3 end = pos.add(d.scale((len - EXEC_SKIP) / len));
            if (segmentSegmentDistance(pos, end, lo, hi) < r) {
                return false;
            }
        }
        return true;
    }

    private static double segmentPointDistance(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 d = b.subtract(a);
        double l2 = d.lengthSqr();
        double t = l2 < 1.0E-9D ? 0.0D : Mth.clamp(p.subtract(a).dot(d) / l2, 0.0D, 1.0D);
        return a.add(d.scale(t)).distanceTo(p);
    }

    /** 两条线段之间的最短距离（只在切机位时算几次） */
    private static double segmentSegmentDistance(Vec3 p1, Vec3 q1, Vec3 p2, Vec3 q2) {
        Vec3 d1 = q1.subtract(p1);
        Vec3 d2 = q2.subtract(p2);
        Vec3 r = p1.subtract(p2);
        double a = d1.lengthSqr();
        double e = d2.lengthSqr();
        double f = d2.dot(r);
        if (a < 1.0E-9D && e < 1.0E-9D) {
            return p1.distanceTo(p2);
        }
        double s;
        double t;
        if (a < 1.0E-9D) {
            s = 0.0D;
            t = Mth.clamp(f / e, 0.0D, 1.0D);
        } else {
            double c = d1.dot(r);
            if (e < 1.0E-9D) {
                t = 0.0D;
                s = Mth.clamp(-c / a, 0.0D, 1.0D);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                s = denom > 1.0E-9D ? Mth.clamp((b * f - c * e) / denom, 0.0D, 1.0D) : 0.0D;
                t = (b * s + f) / e;
                if (t < 0.0D) {
                    t = 0.0D;
                    s = Mth.clamp(-c / a, 0.0D, 1.0D);
                } else if (t > 1.0D) {
                    t = 1.0D;
                    s = Mth.clamp((b - c) / a, 0.0D, 1.0D);
                }
            }
        }
        return p1.add(d1.scale(s)).distanceTo(p2.add(d2.scale(t)));
    }

    // ==================== 视角类型 ====================

    /** 处决开始：不接管时（关了电影镜头、奴仆第三人称选项）当场切原版第三人称背后视角 */
    static void onBegin(MercyClient.Exec e) {
        e.shots.cameraOn = EffectsDisplayConfig.mercyCamera();
        Entity t = e.target();
        if (t != null) {
            EntityDimensions d = t.getType().getDimensions();
            float k = t instanceof LivingEntity le ? le.getScale() : 1.0F;
            e.shots.targetHeight = d.height * k;
            e.shots.targetWidth = d.width * k;
            e.shots.unit = Mth.clamp(e.shots.targetHeight / REF_HEIGHT, 0.3D, 3.0D);
        }
        if ((e.cinematic && !e.shots.cameraOn) || (e.variant.thrall && EffectsDisplayConfig.thrallThirdPerson())) {
            switchToThird(e);
        }
    }

    private static void switchToThird(MercyClient.Exec e) {
        if (e.shots.switched) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        e.shots.saved = mc.options.getCameraType();
        e.shots.switched = true;
        if (e.shots.saved != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    /** 交还、清场：改回原来的视角类型 */
    static void restore(MercyClient.Exec e) {
        if (!e.shots.switched) {
            return;
        }
        e.shots.switched = false;
        Minecraft mc = Minecraft.getInstance();
        if (e.shots.saved != null && mc.options.getCameraType() != e.shots.saved) {
            mc.options.setCameraType(e.shots.saved);
        }
    }

    /** 每帧渲染之前：第一次切进非玩家镜头的机位时把视角类型改成第三人称（第一人称时本地玩家模型不画） */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        MercyClient.Exec e = MercyClient.local();
        if (e != null && !e.handedBack && !e.shots.switched && takesOver(e)
                && shotAt(e, e.elapsed()) != Shot.GAME) {
            switchToThird(e);
        }
    }

    // ==================== 给 Mixin 的入口 ====================

    /**
     * {@code Camera#setup} 末尾调：要接管时算好这一帧的位置和朝向，返回 true，之后用 {@link #x()} 这几个取
     */
    public static boolean override(Camera camera) {
        MercyClient.Exec e = MercyClient.local();
        if (e == null || camera != Minecraft.getInstance().gameRenderer.getMainCamera()) {
            return false;
        }
        if (devOrbitDist > 0.0F) {
            // 奴仆处决不切镜头、视角又被锁着朝向目标，要从侧面核对刀口就只能这样换机位（要先切到第三人称才画得出处决者）
            double a = devOrbitAngle * Mth.DEG_TO_RAD;
            Vec3 look = e.pl.add(0.0D, devOrbitHeight, 0.0D);
            Vec3 pos = look.add(e.f.scale(Math.cos(a) * devOrbitDist)).add(e.s.scale(Math.sin(a) * devOrbitDist));
            Vec3 n = look.subtract(pos).normalize();
            outX = pos.x;
            outY = pos.y;
            outZ = pos.z;
            outYaw = MercyClient.yawOf(n);
            outPitch = MercyClient.pitchOf(n);
            return true;
        }
        long t = e.elapsed();
        Shot s = shotAt(e, t);
        if (s == Shot.GAME) {
            return false;
        }
        ShotPose p = pose(e, s);
        Vec3 dir = p.look.subtract(p.pos);
        double len = dir.length();
        Vec3 n = len > 1.0E-4D ? dir.scale(1.0D / len) : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 pos = p.pos;
        if (s == Shot.CLOSEUP) {
            // 整段沿视线往前推 0.08 格
            MercyBeats.Lich b = MercyBeats.lich(e.variant);
            float u = (t - b.closeup) / (float) (b.third - b.closeup);
            pos = pos.add(n.scale(0.08D * sine(u)));
        } else if (s == Shot.WIDE) {
            // 沿视线反方向匀速后拉 0.2 格 / 秒
            long since = t - MercyTimeline.wideFromMs(e.plan);
            pos = pos.subtract(n.scale(0.2D * Math.max(0L, since) / 1000.0D));
        }
        if (EffectsDisplayConfig.screenEffects()) {
            pos = pos.add(shakeOffset(e, t));
        }
        outX = pos.x;
        outY = pos.y;
        outZ = pos.z;
        outYaw = MercyClient.yawOf(n);
        outPitch = MercyClient.pitchOf(n);
        return true;
    }

    public static double x() {
        return outX;
    }

    public static double y() {
        return outY;
    }

    public static double z() {
        return outZ;
    }

    public static float yaw() {
        return outYaw;
    }

    public static float pitch() {
        return outPitch;
    }

    // ==================== 推镜、震动、甩镜 ====================

    /** 每一刀刺入：FOV × 0.97，40 ms 收进去、120 ms {@code easeOutCubic} 回来 */
    private static float push(MercyClient.Exec e, long t) {
        float k = 1.0F;
        for (int ts : e.stabs) {
            long d = t - ts;
            if (d < 0L || d >= 160L) {
                continue;
            }
            float env = d < 40L ? d / 40.0F : 1.0F - outCubic((d - 40L) / 120.0F);
            k *= 1.0F - 0.03F * env;
        }
        return k;
    }

    /** 奴仆处决的轻推近（选项开着才有）：200 ms 进 200 ms 出 */
    private static float thrallPush(MercyClient.Exec e, long t) {
        long d = t - MercyBeats.thrall(e.variant).stab;
        if (d < 0L || d >= 400L) {
            return 1.0F;
        }
        float env = d < 200L ? sine(d / 200.0F) : 1.0F - sine((d - 200L) / 200.0F);
        return 1.0F - 0.03F * env;
    }

    private static int[] stabTimes(MercyClient.Exec e) {
        return e.stabs;
    }

    /** 每一刀刺入时镜头位置沿随机方向偏 0.02 格，150 ms 内按 (1 − u)² 衰减；出结果那一刀 × 1.6 */
    private static Vec3 shakeOffset(MercyClient.Exec e, long t) {
        int[] stabs = stabTimes(e);
        double x = 0.0D, y = 0.0D, z = 0.0D;
        for (int i = 0; i < stabs.length; i++) {
            float amp = shakeEnvelope(t - stabs[i], 150) * (MercyTimeline.isHeavyStab(e.plan, i) ? 1.6F : 1.0F);
            if (amp <= 0.0F) {
                continue;
            }
            int seed = e.targetId * 31 + i * 7;
            x += hash(seed) * 0.02D * amp;
            y += hash(seed + 1) * 0.02D * amp;
            z += hash(seed + 2) * 0.02D * amp;
        }
        return new Vec3(x, y, z);
    }

    private static float shakeEnvelope(long d, int ms) {
        if (d < 0L || d >= ms) {
            return 0.0F;
        }
        float u = d / (float) ms;
        return (1.0F - u) * (1.0F - u);
    }

    /** 固定种子的 −1~1 伪随机（冻帧时同一刻每帧都一样） */
    private static float hash(int seed) {
        int h = seed * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        return ((h & 0xFFFF) / 32767.5F) - 1.0F;
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return;
        }
        MercyClient.Exec e = MercyClient.local();
        if (e == null || e.handedBack) {
            return;
        }
        long t = e.elapsed();
        Shot s = shotAt(e, t);
        if (s != Shot.GAME) {
            event.setFOV(pose(e, s).fov * push(e, t));
        } else if (e.variant.thrall) {
            if (EffectsDisplayConfig.thrallPushIn()) {
                event.setFOV(event.getFOV() * thrallPush(e, t));
            }
        } else if (t >= MercyBeats.lich(e.variant).cutIn) {
            event.setFOV(event.getFOV() * push(e, t));
        }
    }

    @SubscribeEvent
    public static void onComputeAngles(ViewportEvent.ComputeCameraAngles event) {
        MercyClient.Exec e = MercyClient.local();
        if (e == null || e.handedBack || !EffectsDisplayConfig.screenEffects()
                || event.getCamera() != Minecraft.getInstance().gameRenderer.getMainCamera()) {
            return;
        }
        long t = e.elapsed();
        Shot s = shotAt(e, t);
        int[] stabs = stabTimes(e);
        float yaw = 0.0F, pitch = 0.0F, roll = 0.0F;
        boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        for (int i = 0; i < stabs.length; i++) {
            long d = t - stabs[i];
            int seed = e.targetId * 31 + i * 7 + 11;
            if (s != Shot.GAME || !e.variant.thrall) {
                // 运镜里（以及关了电影镜头的第三人称）：俯仰 / 偏航各 ±0.5°，150 ms
                float amp = shakeEnvelope(d, 150) * (MercyTimeline.isHeavyStab(e.plan, i) ? 1.6F : 1.0F);
                pitch += hash(seed) * 0.5F * amp;
                yaw += hash(seed + 1) * 0.5F * amp;
            } else if (firstPerson) {
                // 奴仆第一人称：轻震，俯仰 ±0.4°、偏航 ±0.3°，100 ms
                float amp = shakeEnvelope(d, 100);
                pitch += hash(seed) * 0.4F * amp;
                yaw += hash(seed + 1) * 0.3F * amp;
            }
            if (s == Shot.C && e.variant == MercyVariant.LICH_C) {
                // 贴身仰拍：每刀刺入甩一下镜，第 1、3 刀往一边、第 2 刀往另一边
                float sign = i == 1 ? -1.0F : 1.0F;
                float env = whip(d);
                yaw += 4.0F * sign * env;
                roll += 1.5F * sign * env;
            }
        }
        if (yaw != 0.0F || pitch != 0.0F) {
            event.setYaw(event.getYaw() + yaw);
            event.setPitch(event.getPitch() + pitch);
        }
        if (roll != 0.0F) {
            event.setRoll(event.getRoll() + roll);
        }
    }

    /** 甩镜：80 ms {@code easeOutQuad} 甩过去，再 220 ms {@code easeInOutSine} 回 0 */
    private static float whip(long d) {
        if (d < 0L || d >= 300L) {
            return 0.0F;
        }
        if (d < 80L) {
            float u = d / 80.0F;
            return 1.0F - (1.0F - u) * (1.0F - u);
        }
        return 1.0F - sine((d - 80L) / 220.0F);
    }

    // ==================== 跪地特写的压暗 ====================

    /** 跪地特写这一段：全屏压暗 35% + 一圈深红暗角，只有晶体在发光 */
    private static void renderShade(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        MercyClient.Exec e = MercyClient.local();
        if (e == null || !EffectsDisplayConfig.screenEffects() || shotAt(e, e.elapsed()) != Shot.CLOSEUP) {
            return;
        }
        g.fill(0, 0, width, height, Math.round(0.35F * 255.0F) << 24);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int rgb = FxPalette.KUVA_DEEP;
        g.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, 0.25F);
        g.blit(VIGNETTE, 0, 0, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Overlays {
        private Overlays() {
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
            event.registerBelowAll(SHADE_ID.getPath(), MercyCamera::renderShade);
        }
    }

    // ==================== 曲线 ====================

    private static float sine(float u) {
        float x = Mth.clamp(u, 0.0F, 1.0F);
        return (float) (0.5D - 0.5D * Math.cos(Math.PI * x));
    }

    private static float outCubic(float u) {
        float x = Mth.clamp(u, 0.0F, 1.0F);
        float k = 1.0F - x;
        return 1.0F - k * k * k;
    }
}
