package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.mercy.MercyBeats;
import pers.roinflam.kuvalich.mercy.MercyCameraPath;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 处决运镜：只对本地处决者、只在玄骸处决、只在 {@code mercy_camera} 开着时接管相机
 *
 * <p>机位都在处决开始那一刻按世界帧算好（{@code Pl} 目标脚底、{@code Pe} 处决者站位、{@code F} 从目标指向处决者、
 * {@code S = Y × F}、{@code U} 向上），每个机位第一次切进去时做一次遮挡检测，之后机位本身不再逐帧算，免得镜头抖。
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
 * <p>{@code mercy_camera_motion} 不是静态档时，排过轨迹的套路在本套机位上叠 {@link MercyCameraPath} 的推拉、绕、升降、
 * 滚转，注视点跟着处决者的整身位移走；轨迹各段的两头和中点切进来时查一遍遮挡，有一处过不了整段退回固定机位。
 * 从玩家视角进机位改成 250 ms 推出去（排过轨迹的套路是 300 ms 绕注视点的弧线），失败和挣开站起切宽镜头改成 350 ms
 * 移过去（路上被方块挡住时照旧硬切）；
 * 刀点上的切换、换到另一侧的特写和第三人称照旧硬切。静态档和没有这一层时逐帧一样。</p>
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
        /** 折翼：它右前方偏低，荡身、拧、踏背腾起都在它右手边 */
        F,
        /** 断膝三方：它身后偏左，滑铲到它右后方、越背翻到左后方、前滚到右前方都在画里 */
        G,
        /** 三掠：它左前方偏高往下看，三次换位和三刀都在这一侧 */
        H,
        /** 成功：跪地特写 */
        CLOSEUP,
        /** 成功：膝盖高度第三人称 */
        THIRD,
        /** 失败、挣开站起：宽镜头 */
        WIDE
    }

    private static final Shot[] SHOTS = Shot.values();

    private static final int SHAKE_MS = 260;
    private static final int SHAKE_HEAVY_MS = 360;
    /** 震动正弦的基础角频率：照 2.1 弧度 / tick，几路乘 1.7~3.9 后落在 11~26 Hz */
    private static final float SHAKE_RAD_PER_MS = 2.1F / 50.0F;
    private static final int FOV_IN_MS = 60;
    private static final int FOV_OUT_MS = 220;
    /** 注视点跟处决者的指数逼近时间常数 */
    private static final double FOLLOW_TAU_MS = 110.0D;

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
     *   <li>F 折翼：在 {@code −S} 一侧、目标右前方离它 5 格的低机位。目标偏开站，处决者先在它右前方，荡到它右后方、
     *       再窜到它右手边，三处都在这一侧；机位放在 {@code +S} 一侧开场就被它挡住。注视点在目标往处决者站位挪 0.5 格，
     *       轨迹的跟人把它带到两人中间；</li>
     *   <li>G 断膝三方：在它身后偏左（{@code +S} 一侧）离注视点 4 格、略高。处决者先滑到它右后方，再从背上翻到左后方，
     *       最后滚到它右前方，三处从背后都看得到，侧翻那一圈的平面也差不多正对镜头；放在它正面会被它扑过来的身子挡住。
     *       进机位从它左边绕过去，不从它举起的右手那边过；</li>
     *   <li>H 三掠：在 {@code +S} 一侧、目标左前方离它 5 格出头，比注视点高 1.15 格往下看。处决者开场在它左前方，
     *       掠到它左手边、扑到它背后、腾空越过头顶落到它右前方，第二刀扎的是折下去的后背，机位低了看不见入点。
     *       注视点往镜头这侧挪 0.5 格：放在中轴上时视线擦着处决者站位的头，整条轨迹都过不了遮挡检测、退回固定机位；</li>
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
            new Rig(false, false, 4.4D, -2.95D, 2.05D, 0.5D, 0.0D, 1.2D, 62.0F),
            new Rig(false, false, -4.05D, 1.05D, 1.9D, -0.2D, 0.0D, 1.1D, 62.0F),
            new Rig(false, false, 5.03D, 1.58D, 2.55D, 0.7D, 0.5D, 1.4D, 60.0F),
            new Rig(false, false, 0.55D, -2.75D, 1.2D, 0.5D, 0.0D, 1.7D, 55.0F),
            new Rig(true, true, 0.6D, -1.8D, 0.55D, -0.65D, 0.0D, 1.55D, 70.0F),
            null
    };

    /** 机位表按这个站姿身高定（玄骸） */
    private static final double REF_HEIGHT = 3.4D;
    /** 处决者（按玩家身材）：头的球心离脚底多高、往目标那边探出多少、半径；身子是脚底到肩的一根粗杆 */
    private static final double EXEC_HEAD_U = 1.7D;
    /** 判错、挣开时拉开多少（推拉比例） */
    private static final float ESCAPE_PULL = 0.3F;
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
    /** 弧线进机位时起点的注视点至少在眼前多远 */
    private static final double ENTRY_LOOK_REACH = 3.0D;
    /** 进机位时镜头离处决者头的球心不到这么远就不画他的头（不然满屏是从里面看的脸） */
    private static final double ENTRY_HIDE_HEAD = EXEC_HEAD_R + 0.15D;
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
        /** 开始那一刻的 mercy_camera_motion */
        MercyCameraPath.Motion motion = MercyCameraPath.Motion.STATIC;
        /** 轨迹、进机位、切宽镜头的遮挡：0 还没查，1 能走，-1 退回硬切 / 固定机位 */
        byte pathState, entryState, wideState, escapeState;
        /** 注视点跟人的偏移（已经平滑过），和它上一次更新时的真实钟、轨迹段 */
        double fx, fy, fz;
        long followRaw = -1L;
        int followSeg = -1;
        /** 这一帧轨迹给的滚转 */
        float roll;
    }

    // 这一帧给 Mixin 的结果（只在渲染线程用，不分配）
    private static double outX, outY, outZ;
    private static float outYaw, outPitch;
    /** 这一帧在走弧线进机位 */
    private static boolean arcEntryFrame;
    /** 这一帧藏了处决者的头，画完放回去 */
    @Nullable
    private static PlayerModel<AbstractClientPlayer> hiddenHead;
    private static boolean hatWasVisible;

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
     * 各把武器长短不一，长柄的会横在镜头前、占掉小半个画面。切镜头的那一下看不出少了东西，第三人称收尾时照常画。
     * 断膝三方整场都不画：它攥腕、侧倒时右手一直贴着处决者，刀会横穿过他的身子；特写本来就藏着，接着藏到交还
     */
    public static boolean hidesTargetWeapon(int entityId) {
        MercyClient.Exec e = MercyClient.local();
        if (e != null && e.targetId == entityId && !e.handedBack && shotAt(e, e.elapsed()) == Shot.CLOSEUP) {
            return true;
        }
        // 断膝三方和折翼按目标查，旁观者看到的也一样
        MercyClient.Exec f = MercyClient.byTarget(entityId);
        if (f == null || f.handedBack) {
            return false;
        }
        if (f.variant == MercyVariant.LICH_G) {
            return true;
        }
        // 折翼整场攥着它拿武器的那只手腕，武器跟着胳膊甩会横穿处决者；判错、挣开藏到切宽镜头，成功藏到交还
        if (f.variant != MercyVariant.LICH_F) {
            return false;
        }
        MercyTimeline.Outcome oc = MercyTimeline.outcome(f.plan);
        boolean freed = oc == MercyTimeline.Outcome.FAIL || oc == MercyTimeline.Outcome.ADVANCE;
        return f.elapsed() < (freed ? MercyTimeline.wideFromMs(f.plan) : f.handbackMs);
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
        Shot own = own(plan.variant());
        return switch (MercyTimeline.outcome(plan)) {
            case SUCCESS -> t < b.closeup ? own : t < b.third ? Shot.CLOSEUP : Shot.THIRD;
            // 感化不切第三人称，特写一直到交还
            case CONVERT -> t < b.closeup ? own : Shot.CLOSEUP;
            case ADVANCE, FAIL -> t < MercyTimeline.wideFromMs(plan) ? own : Shot.WIDE;
            case THRALL -> Shot.GAME;
        };
    }

    private static Shot own(MercyVariant v) {
        return switch (v.camera) {
            case 1 -> Shot.B;
            case 2 -> Shot.C;
            case 3 -> Shot.D;
            case 4 -> Shot.E;
            case 5 -> Shot.F;
            case 6 -> Shot.G;
            case 7 -> Shot.H;
            default -> Shot.A;
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
        if (!clearOfBodies(e, pos, look, executorAt(e, s))) {
            return null;
        }
        return s != Shot.THIRD || executorInView(e, pos) ? new ShotPose(pos, look, fov, true) : null;
    }

    /**
     * 查视线时处决者站在哪。宽镜头那时他已经被推开了，注视点就在两人中间、离原站位不到 0.2 格，
     * 按原站位查视线必定穿过他的身子，宽镜头永远放不下。静态档照旧按原站位（要和改之前一样）
     */
    private static Vec3 executorAt(MercyClient.Exec e, Shot s) {
        if (s == Shot.WIDE && e.shots.motion != MercyCameraPath.Motion.STATIC) {
            return e.pe.add(e.f.scale(MercyTimeline.pushDistance(e.plan)));
        }
        return e.pe;
    }

    private static boolean clearOfTarget(MercyClient.Exec e, Vec3 pos) {
        return MercyCameraPath.outsideColumn(pos, e.pl, TARGET_CLEAR * e.shots.targetWidth,
                e.pl.y + e.shots.targetHeight + 0.3D);
    }

    private static Vec3 executorHead(Vec3 pe, MercyClient.Exec e) {
        return pe.add(e.f.scale(EXEC_HEAD_F)).add(0.0D, EXEC_HEAD_U, 0.0D);
    }

    /**
     * 镜头不在目标身体范围里，视线（去掉贴着注视点的一小段）不穿过处决者的头和身子。
     * 处决者一般按站位算（整场锁在那里），头往目标那边探出一点（各套路出刀时都前倾）
     */
    private static boolean clearOfBodies(MercyClient.Exec e, Vec3 pos, Vec3 look, Vec3 pe) {
        if (!clearOfTarget(e, pos)) {
            return false;
        }
        Vec3 dir = pos.subtract(look);
        double len = dir.length();
        if (len <= SIGHT_SKIP) {
            return false;
        }
        Vec3 from = look.add(dir.scale(SIGHT_SKIP / len));
        if (segmentPointDistance(from, pos, executorHead(pe, e)) < EXEC_HEAD_R) {
            return false;
        }
        return segmentSegmentDistance(from, pos, pe.add(0.0D, EXEC_BODY_LO, 0.0D),
                pe.add(0.0D, EXEC_BODY_HI, 0.0D)) >= EXEC_BODY_R;
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

    // ==================== 轨迹、跟人、过渡 ====================

    private static final float[] MOVE = new float[MercyCameraPath.CHANNELS];
    private static final MercyPose.Body BODY = new MercyPose.Body();

    /** 本套机位在这一场里交给下一个镜头的时刻 */
    private static int windowEnd(MercyClient.Exec e) {
        return switch (MercyTimeline.outcome(e.plan)) {
            case SUCCESS, CONVERT -> MercyBeats.lich(e.variant).closeup;
            case ADVANCE, FAIL -> MercyTimeline.wideFromMs(e.plan);
            case THRALL -> e.handbackMs;
        };
    }

    /** 在固定机位上叠推拉、绕、升降；注视点只按套路挪一个定值，不随时间动 */
    private static ShotPose moved(MercyClient.Exec e, ShotPose base, float[] m) {
        Vec3 look = base.look().add(0.0D, -MercyCameraPath.lookDrop(e.variant) * e.shots.unit, 0.0D);
        Vec3 pos = base.pos();
        if (m[MercyCameraPath.ORBIT] != 0.0F) {
            int side = pos.subtract(look).dot(e.s) >= 0.0D ? 1 : -1;
            pos = swing(e, pos, look, side, -m[MercyCameraPath.ORBIT]);
        }
        if (m[MercyCameraPath.RISE] != 0.0F) {
            pos = pos.add(0.0D, m[MercyCameraPath.RISE] * e.shots.unit, 0.0D);
        }
        if (m[MercyCameraPath.DOLLY] != 0.0F) {
            pos = look.add(pos.subtract(look).scale(1.0D - m[MercyCameraPath.DOLLY]));
        }
        return new ShotPose(pos, look, base.fov(), true);
    }

    /** 轨迹各段两头和中点都过得了遮挡检测才走轨迹，第一次用到时查 */
    private static boolean pathOk(MercyClient.Exec e, Shot s, ShotPose base, int lastStab) {
        ShotCache c = e.shots;
        if (c.pathState == 0) {
            c.pathState = 1;
            for (int ms : MercyCameraPath.checkTimes(e.variant, lastStab, windowEnd(e))) {
                if (!MercyCameraPath.sample(e.variant, lastStab, c.motion, ms, ms + 10_000L, MOVE)) {
                    break;
                }
                ShotPose q = moved(e, base, MOVE);
                if (check(e, s, q.pos(), q.look(), q.fov(), false) == null) {
                    c.pathState = -1;
                    break;
                }
            }
        }
        return c.pathState > 0;
    }

    /**
     * 本套机位此刻的位置和注视点：排过轨迹的叠上轨迹和跟人。{@code live} 时顺带更新跟人的平滑和这一帧的滚转；
     * 不更新时注视点用上一次的跟人偏移（切宽镜头时从这里起步，不跳）
     */
    private static ShotPose ownMoved(MercyClient.Exec e, Shot s, long t, long raw, boolean live) {
        ShotPose base = pose(e, s);
        int last = MercyTimeline.lastStab(e.plan);
        if (!pathOk(e, s, base, last)
                || !MercyCameraPath.sample(e.variant, last, e.shots.motion, t, raw, MOVE)) {
            return base;
        }
        MOVE[MercyCameraPath.DOLLY] -= escapePull(e, s, base, t);
        ShotPose q = moved(e, base, MOVE);
        if (live) {
            e.shots.roll = MOVE[MercyCameraPath.ROLL];
            follow(e, t, raw, MOVE[MercyCameraPath.FOLLOW], MercyCameraPath.segment(e.variant, last, t));
        }
        return new ShotPose(q.pos(), q.look().add(e.shots.fx, e.shots.fy, e.shots.fz), q.fov(), true);
    }

    /**
     * 判错、挣开时玄骸从蹲着一下站起来，近景装不下它，头出画、身子贴着镜头；从挣开到切宽镜头之间慢慢拉开。
     * 拉到头的位置先查一次遮挡，过不了就不拉
     */
    private static float escapePull(MercyClient.Exec e, Shot s, ShotPose base, long t) {
        int from = MercyTimeline.breakFreeMs(e.plan);
        // 折翼挣开后 100 ms 就切宽镜头，这么短拉开只会顿一下
        if (from < 0 || t <= from || e.variant == MercyVariant.LICH_F) {
            return 0.0F;
        }
        if (e.shots.escapeState == 0) {
            float d = MOVE[MercyCameraPath.DOLLY];
            MOVE[MercyCameraPath.DOLLY] = d - ESCAPE_PULL;
            ShotPose far = moved(e, base, MOVE);
            MOVE[MercyCameraPath.DOLLY] = d;
            e.shots.escapeState = (byte) (check(e, s, far.pos(), far.look(), far.fov(), false) != null ? 1 : -1);
        }
        if (e.shots.escapeState < 0) {
            return 0.0F;
        }
        float x = Mth.clamp((t - from) / (float) (MercyTimeline.wideFromMs(e.plan) - from), 0.0F, 1.0F);
        return ESCAPE_PULL * x * x * (3.0F - 2.0F * x);
    }

    /**
     * 注视点跟处决者的整身位移（上步、蹬开），上下只跟一半；按真实钟指数逼近，和帧率无关。切到下一段时直接对齐
     */
    private static void follow(MercyClient.Exec e, long t, long raw, float w, int seg) {
        double tx = 0.0D, ty = 0.0D, tz = 0.0D;
        if (w > 0.0F && e.pose != null && MercyPose.body(e, t, BODY)) {
            double k = w * BODY.blend;
            tx = (BODY.x - e.pe.x) * k;
            ty = (BODY.y - e.pe.y) * k * 0.5D;
            tz = (BODY.z - e.pe.z) * k;
        }
        ShotCache c = e.shots;
        if (c.followSeg != seg || c.followRaw < 0L || raw < c.followRaw) {
            c.fx = tx;
            c.fy = ty;
            c.fz = tz;
        } else {
            double a = 1.0D - Math.exp(-(raw - c.followRaw) / FOLLOW_TAU_MS);
            c.fx += (tx - c.fx) * a;
            c.fy += (ty - c.fy) * a;
            c.fz += (tz - c.fz) * a;
        }
        c.followRaw = raw;
        c.followSeg = seg;
    }

    /** 从玩家视角推出去进机位走到几成（已缓动）；不在这一段是 −1 */
    private static float entryProgress(MercyClient.Exec e, long t) {
        long since = t - MercyBeats.lich(e.variant).cutIn;
        boolean arc = entryArc(e);
        int ms = arc ? MercyCameraPath.ENTRY_ARC_MS : MercyCameraPath.ENTRY_MS;
        if (since < 0L || since >= ms) {
            return -1.0F;
        }
        return arc ? MercyCameraPath.entryEase(since / (float) ms) : outCubic(since / (float) ms);
    }

    /** 排过轨迹的套路进机位绕注视点走弧线；别的套路还是直线推出去（等它们排轨迹时一起换） */
    private static boolean entryArc(MercyClient.Exec e) {
        return MercyCameraPath.has(e.variant);
    }

    /**
     * 路上没有方块才推出去，否则照旧硬切。弧线逐段查方块，离开处决者的头以后各点都要在目标身体范围外；
     * 直线只查眼睛到机位、半路到注视点
     */
    private static boolean entryOk(MercyClient.Exec e, Vec3 eye, Vec3 eyeLook, Vec3 pos, Vec3 look) {
        ShotCache c = e.shots;
        if (c.entryState == 0) {
            c.entryState = 1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                if (entryArc(e) && e.variant.facing == MercyVariant.Facing.TOWARD && pos.subtract(look).dot(e.s) < 0.0D) {
                    // 面对面的套路机位换到了目标右手边（贴墙时），弧线要从它举着的武器里穿过去；武器长短各异查不了，照旧硬切。
                    // 背对的套路机位在处决者身后，碰不到它的武器
                    c.entryState = -1;
                } else if (entryArc(e)) {
                    Vec3 prev = eye;
                    for (int i = 1; i <= MercyCameraPath.ENTRY_SAMPLES && c.entryState > 0; i++) {
                        float x = i / (float) MercyCameraPath.ENTRY_SAMPLES;
                        Vec3 p = MercyCameraPath.entryArc(eye, eyeLook, pos, look, x);
                        if (blocked(mc, prev, p) || (i == MercyCameraPath.ENTRY_SAMPLES / 2
                                && blocked(mc, eyeLook.lerp(look, x), p))) {
                            c.entryState = -1;
                        }
                        prev = p;
                    }
                    if (c.entryState > 0 && !MercyCameraPath.entryClear(eye, eyeLook, pos, look,
                            executorHead(e.pe, e), EXEC_HEAD_R, e.pl, TARGET_CLEAR * e.shots.targetWidth,
                            e.pl.y + e.shots.targetHeight + 0.3D)) {
                        c.entryState = -1;
                    }
                } else {
                    Vec3 mid = eye.lerp(pos, 0.5D);
                    if (blocked(mc, eye, pos) || blocked(mc, look, mid)) {
                        c.entryState = -1;
                    }
                }
            }
        }
        return c.entryState > 0;
    }

    private static boolean blocked(Minecraft mc, Vec3 a, Vec3 b) {
        return mc.level.clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player))
                .getType() != HitResult.Type.MISS;
    }

    /** 切宽镜头的过渡走到几成（已缓动）；不在这一段是 −1 */
    private static float wideProgress(MercyClient.Exec e, long t) {
        int from = MercyTimeline.wideFromMs(e.plan);
        long since = t - from;
        if (from < 0 || since < 0L || since >= MercyCameraPath.WIDE_BLEND_MS) {
            return -1.0F;
        }
        return sine(since / (float) MercyCameraPath.WIDE_BLEND_MS);
    }

    /** 切宽镜头前最后一刻本套机位的样子；本套机位没用上（退回了玩家镜头）时是 null */
    @Nullable
    private static ShotPose wideFrom(MercyClient.Exec e) {
        Shot o = own(e.variant);
        if (!pose(e, o).ok()) {
            return null;
        }
        long t = MercyTimeline.wideFromMs(e.plan) - 1L;
        return ownMoved(e, o, t, t + 10_000L, false);
    }

    /**
     * 镜头走的那条线上没有方块、半路的视线没被方块挡、路上几点都不在目标身子里才移过去，否则照旧硬切。
     * 不查处决者：过渡时他已经被推开，注视点正从两人中间往他那边移，按哪个站位查都会误判
     */
    private static boolean wideOk(MercyClient.Exec e, ShotPose from, Vec3 pos, Vec3 look) {
        ShotCache c = e.shots;
        if (c.wideState == 0) {
            c.wideState = 1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                Vec3 mid = from.pos().lerp(pos, 0.5D);
                if (blocked(mc, from.pos(), pos) || blocked(mc, from.look().lerp(look, 0.5D), mid)) {
                    c.wideState = -1;
                }
            }
            for (int i = 1; i < 4 && c.wideState > 0; i++) {
                if (!clearOfTarget(e, from.pos().lerp(pos, i / 4.0D))) {
                    c.wideState = -1;
                }
            }
        }
        return c.wideState > 0;
    }

    /** 机位的 FOV；进机位、切宽镜头的过渡里从上一个镜头的 FOV 渐变过来 */
    private static float shotFov(MercyClient.Exec e, Shot s, long t, float gameFov) {
        float fov = pose(e, s).fov;
        if (e.shots.motion == MercyCameraPath.Motion.STATIC) {
            return fov;
        }
        if (s == own(e.variant)) {
            float x = entryProgress(e, t);
            if (x >= 0.0F && e.shots.entryState >= 0) {
                return Mth.lerp(x, gameFov, fov);
            }
        } else if (s == Shot.WIDE) {
            float x = wideProgress(e, t);
            ShotPose o = pose(e, own(e.variant));
            if (x >= 0.0F && e.shots.wideState >= 0 && o.ok()) {
                return Mth.lerp(x, o.fov(), fov);
            }
        }
        return fov;
    }

    private static float shakeScale(MercyClient.Exec e) {
        float k = EffectsDisplayConfig.mercyShake();
        return e.shots.motion == MercyCameraPath.Motion.REDUCED ? k * MercyCameraPath.REDUCED_SHAKE : k;
    }

    // ==================== 视角类型 ====================

    /** 处决开始：不接管时（关了电影镜头、奴仆第三人称选项）当场切原版第三人称背后视角 */
    static void onBegin(MercyClient.Exec e) {
        e.shots.cameraOn = EffectsDisplayConfig.mercyCamera();
        e.shots.motion = EffectsDisplayConfig.mercyCameraMotion();
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
        showHead();
        MercyClient.Exec e = MercyClient.local();
        if (e != null && !e.handedBack && !e.shots.switched && takesOver(e)
                && shotAt(e, e.elapsed()) != Shot.GAME) {
            switchToThird(e);
        }
    }

    /** 弧线进机位刚起步那几帧镜头还在处决者头里，不画他的头和帽子层 */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        showHead();
        MercyClient.Exec e = MercyClient.local();
        if (!arcEntryFrame || e == null || event.getEntity() != Minecraft.getInstance().player
                || executorHead(e.pe, e).distanceToSqr(outX, outY, outZ) >= ENTRY_HIDE_HEAD * ENTRY_HIDE_HEAD) {
            return;
        }
        PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
        hatWasVisible = m.hat.visible;
        m.head.visible = false;
        m.hat.visible = false;
        hiddenHead = m;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        showHead();
    }

    /** Pre 被别处取消时没有 Post，下一次渲染前也放回去 */
    private static void showHead() {
        if (hiddenHead != null) {
            hiddenHead.head.visible = true;
            hiddenHead.hat.visible = hatWasVisible;
            hiddenHead = null;
        }
    }

    // ==================== 给 Mixin 的入口 ====================

    /**
     * {@code Camera#setup} 末尾调：要接管时算好这一帧的位置和朝向，返回 true，之后用 {@link #x()} 这几个取
     */
    public static boolean override(Camera camera) {
        MercyClient.Exec e = MercyClient.local();
        if (camera != Minecraft.getInstance().gameRenderer.getMainCamera()) {
            return false;
        }
        arcEntryFrame = false;
        if (e == null) {
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
        e.shots.roll = 0.0F;
        long t = e.elapsed();
        Shot s = shotAt(e, t);
        if (s == Shot.GAME) {
            return false;
        }
        ShotPose p = pose(e, s);
        Vec3 pos = p.pos;
        Vec3 look = p.look;
        boolean moving = e.shots.motion != MercyCameraPath.Motion.STATIC;
        if (moving && s == own(e.variant)) {
            ShotPose q = ownMoved(e, s, t, e.rawElapsed(), true);
            pos = q.pos();
            look = q.look();
        }
        Vec3 dir = look.subtract(pos);
        double len = dir.length();
        Vec3 n = len > 1.0E-4D ? dir.scale(1.0D / len) : new Vec3(0.0D, 0.0D, 1.0D);
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
        if (moving) {
            float x = -1.0F;
            Vec3 fromPos = null, fromLook = null;
            if (s == own(e.variant)) {
                x = entryProgress(e, t);
                if (x >= 0.0F) {
                    LocalPlayer pl = Minecraft.getInstance().player;
                    float pt = Minecraft.getInstance().getFrameTime();
                    fromPos = pl != null ? pl.getEyePosition(pt) : pos;
                    Vec3 view = pl != null ? pl.getViewVector(pt) : n;
                    double reach = look.distanceTo(fromPos);
                    if (entryArc(e)) {
                        // 弧线绕注视点转，起点的注视点放远一点，镜头起步时往侧面走得快，早点离开处决者自己的头
                        reach = Math.max(reach, ENTRY_LOOK_REACH);
                    }
                    fromLook = fromPos.add(view.scale(reach));
                    if (entryOk(e, fromPos, fromLook, pos, look)) {
                        e.shots.roll *= x;
                    } else {
                        x = -1.0F;
                    }
                }
            } else if (s == Shot.WIDE) {
                x = wideProgress(e, t);
                if (x >= 0.0F) {
                    ShotPose from = wideFrom(e);
                    if (from != null && wideOk(e, from, pos, look)) {
                        fromPos = from.pos();
                        fromLook = from.look();
                    } else {
                        x = -1.0F;
                    }
                }
            }
            if (x >= 0.0F && fromPos != null) {
                arcEntryFrame = s != Shot.WIDE && entryArc(e);
                pos = arcEntryFrame ? MercyCameraPath.entryArc(fromPos, fromLook, pos, look, x) : fromPos.lerp(pos, x);
                look = fromLook.lerp(look, x);
                dir = look.subtract(pos);
                len = dir.length();
                if (len > 1.0E-4D) {
                    n = dir.scale(1.0D / len);
                }
            }
        }
        if (EffectsDisplayConfig.screenEffects()) {
            pos = pos.add(shakeOffset(e, e.rawElapsed()));
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

    /**
     * 每一刀刺入的 FOV 冲击：60 ms 推近、220 ms 缓回，普通 6%、出结果那一刀 9%。读真实钟，顿帧时照样打出来；
     * 强度跟着 mercy_shake 走，但最多到 1 倍
     */
    private static float fovKick(MercyClient.Exec e, long raw) {
        float k = Math.min(1.0F, EffectsDisplayConfig.mercyShake());
        if (k <= 0.0F) {
            return 1.0F;
        }
        float m = 1.0F;
        for (int i = 0; i < e.stabs.length; i++) {
            long d = raw - e.stabs[i];
            if (d < 0L || d >= FOV_IN_MS + FOV_OUT_MS) {
                continue;
            }
            float env = d < FOV_IN_MS ? outCubic(d / (float) FOV_IN_MS) : 1.0F - sine((d - FOV_IN_MS) / (float) FOV_OUT_MS);
            m *= 1.0F - (MercyTimeline.isHeavyStab(e.plan, i) ? 0.09F : 0.06F) * env * k;
        }
        return m;
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

    /**
     * 每一刀刺入的位置冲量：主要沿刺入方向往前顶，侧向、上下再叠几路不同频率的正弦（平滑、不抽搐）。
     * 普通 0.06 格、出结果那一刀 × 1.6，乘 mercy_shake
     */
    private static Vec3 shakeOffset(MercyClient.Exec e, long raw) {
        float k = shakeScale(e);
        if (k <= 0.0F) {
            return Vec3.ZERO;
        }
        int[] stabs = stabTimes(e);
        double x = 0.0D, y = 0.0D, z = 0.0D;
        for (int i = 0; i < stabs.length; i++) {
            boolean heavy = MercyTimeline.isHeavyStab(e.plan, i);
            long d = raw - stabs[i];
            float env = impactEnvelope(d, heavy ? SHAKE_HEAVY_MS : SHAKE_MS);
            if (env <= 0.0F) {
                continue;
            }
            double amp = 0.06D * (heavy ? 1.6D : 1.0D) * env * k;
            float ph = hash(e.targetId * 31 + i * 7) * Mth.PI;
            float w = d * SHAKE_RAD_PER_MS;
            double along = amp * (0.7D + 0.3D * Mth.sin(2.7F * w + ph));
            double side = amp * 0.45D * (0.6F * Mth.sin(1.9F * w + ph) + 0.4F * Mth.sin(3.3F * w + 2.0F * ph));
            double up = amp * 0.35D * (0.6F * Mth.sin(2.3F * w + 3.0F * ph) + 0.4F * Mth.sin(3.7F * w + ph));
            // f 从目标指向处决者，刀往反方向走
            x += -e.f.x * along + e.s.x * side;
            y += up;
            z += -e.f.z * along + e.s.z * side;
        }
        return new Vec3(x, y, z);
    }

    /** 冲量包络：前 1/8 冲到顶，之后按平方衰减 */
    private static float impactEnvelope(long d, int ms) {
        if (d < 0L || d >= ms) {
            return 0.0F;
        }
        float u = d / (float) ms;
        return Math.min(1.0F, u * 8.0F) * (1.0F - u) * (1.0F - u);
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
        long raw = e.rawElapsed();
        Shot s = shotAt(e, t);
        if (s != Shot.GAME) {
            event.setFOV(shotFov(e, s, t, (float) event.getFOV()) * fovKick(e, raw));
        } else if (e.variant.thrall) {
            if (EffectsDisplayConfig.thrallPushIn()) {
                event.setFOV(event.getFOV() * thrallPush(e, t));
            }
        } else if (t >= MercyBeats.lich(e.variant).cutIn) {
            event.setFOV(event.getFOV() * fovKick(e, raw));
        }
    }

    @SubscribeEvent
    public static void onComputeAngles(ViewportEvent.ComputeCameraAngles event) {
        MercyClient.Exec e = MercyClient.local();
        if (e == null || e.handedBack
                || event.getCamera() != Minecraft.getInstance().gameRenderer.getMainCamera()) {
            return;
        }
        long t = e.elapsed();
        Shot s = shotAt(e, t);
        // 轨迹的滚转是构图，不归屏幕特效开关管
        float roll = s != Shot.GAME ? e.shots.roll : 0.0F;
        if (!EffectsDisplayConfig.screenEffects()) {
            if (roll != 0.0F) {
                event.setRoll(event.getRoll() + roll);
            }
            return;
        }
        long raw = e.rawElapsed();
        int[] stabs = stabTimes(e);
        float k = shakeScale(e);
        float yaw = 0.0F, pitch = 0.0F;
        boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        for (int i = 0; i < stabs.length; i++) {
            long d = raw - stabs[i];
            int seed = e.targetId * 31 + i * 7 + 11;
            if (s != Shot.GAME || !e.variant.thrall) {
                // 运镜里（以及关了电影镜头的第三人称）：几路正弦叠加乘冲量包络，1.2°、出结果那一刀 × 1.6
                boolean heavy = MercyTimeline.isHeavyStab(e.plan, i);
                float env = impactEnvelope(d, heavy ? SHAKE_HEAVY_MS : SHAKE_MS);
                if (env > 0.0F && k > 0.0F) {
                    float a = 1.2F * (heavy ? 1.6F : 1.0F) * env * k;
                    float ph = hash(seed) * Mth.PI;
                    float w = d * SHAKE_RAD_PER_MS;
                    yaw += a * (0.6F * Mth.sin(1.9F * w + ph) + 0.4F * Mth.sin(3.3F * w + 2.0F * ph));
                    pitch += a * (0.6F * Mth.sin(2.3F * w + 3.0F * ph) + 0.4F * Mth.sin(3.9F * w + 4.0F * ph));
                    roll += 0.5F * a * Mth.sin(1.7F * w + 5.0F * ph);
                }
            } else if (firstPerson) {
                // 奴仆第一人称：轻震，俯仰 ±0.4°、偏航 ±0.3°，100 ms
                float amp = shakeEnvelope(d, 100) * k;
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
