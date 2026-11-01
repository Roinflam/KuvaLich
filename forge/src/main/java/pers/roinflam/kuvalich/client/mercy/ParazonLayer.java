package pers.roinflam.kuvalich.client.mercy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 处决时从手掌下面弹出的刀：第三人称挂在玩家渲染器上，第一人称接管手的绘制
 *
 * <p>模型在 {@code models/mercy/parazon.json}，写法照方块模型（{@code from} / {@code to} / 各面 {@code uv}），
 * 但这里自己读、自己烘成顶点表：贴图不在方块图集里，发光层又要用同一套 uv 换一张图再画一遍，
 * 走烘焙模型反而绕。坐标按像素，原点在拳头末端的前臂中轴上，{@code z} 顺着前臂往外，{@code y} 朝前臂背面。
 * 各面的 u / v 方向：上下面 u 沿 x、v 沿 z；东西面 u 沿 z、v 从上往下；南北面 u 沿 x、v 从上往下；
 * 面上写 {@code "rotation": 90} 时 u、v 对调。</p>
 *
 * <p>刃身随演出伸缩（沿 z 拉长），护腕和刃座跟着按三倍速度弹出来；收刀反过来。发光层按加法叠加，
 * 关了加法发光就不画，每一刀刺入时亮一下。</p>
 *
 * <p>第一人称：奴仆处决全程、玄骸处决切镜之前（以及某个机位放不下退回玩家镜头时），原版的两只手和手里的东西都不画，
 * 改由这里按 {@link MercyPose#firstPerson} 给的拳头位置把两条胳膊摆出来，刀挂在右臂上。</p>
 */
@OnlyIn(Dist.CLIENT)
public class ParazonLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation MODEL = new ResourceLocation(Reference.MOD_ID, "models/mercy/parazon.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID, "textures/mercy/parazon.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Reference.MOD_ID, "textures/mercy/parazon_glow.png");

    /** 拳头末端在手臂部件里的高度（像素） */
    private static final float FIST_Y = 10.0F;
    /** 刃身从这里开始往外伸（像素） */
    private static final float BLADE_ROOT_Z = 1.0F;
    /** 护腕和刃座的中心（像素），弹出时绕它缩放 */
    private static final float MOUNT_CENTER_Z = -4.0F;
    /** 第一人称两肩的位置（视角空间：x 右、y 上、z 朝前，格） */
    private static final float SHOULDER_X = 0.45F, SHOULDER_Y = -0.70F, SHOULDER_Z = -0.15F;

    /** 刀整体往手腕方向收的距离（像素）：刃根落在手掌下面，不是从拳头前面才开始 */
    private static final float PALM_SHIFT_Z = -3.0F;

    /** 手臂部件到模型坐标：先转到顺着前臂，再绕前臂翻半圈，把模型里朝前臂背面的刀翻到手掌那一侧 */
    private static final Quaternionf ARM_TO_BLADE = new Quaternionf().rotationX((float) Math.toRadians(-90.0D))
            .rotateZ((float) Math.toRadians(180.0D));

    private final boolean slim;

    public ParazonLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, boolean slim) {
        super(parent);
        this.slim = slim;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        MercyClient.Exec e = MercyClient.byExecutor(player.getId());
        if (e == null) {
            return;
        }
        long t = e.elapsed();
        float ext = MercyTimeline.bladeExtension(e.plan, t, e.shortOutro);
        if (ext <= 0.01F) {
            return;
        }
        poseStack.pushPose();
        drawOnArm(poseStack, buffers, packedLight, getParentModel().rightArm, slim, e, t, ext);
        poseStack.popPose();
    }

    // ==================== 画刀 ====================

    /** 从手臂部件的坐标系起画：先套手臂自己的位置和旋转，再挪到拳头末端 */
    private static void drawOnArm(PoseStack ps, MultiBufferSource buffers, int light, ModelPart arm, boolean slim,
                                  MercyClient.Exec e, long t, float ext) {
        Baked model = model();
        if (model == null) {
            return;
        }
        arm.translateAndRotate(ps);
        ps.translate(armCenterX(true, slim) / 16.0F, FIST_Y / 16.0F, 0.0F);
        ps.mulPose(ARM_TO_BLADE);
        ps.translate(0.0F, 0.0F, PALM_SHIFT_Z / 16.0F);

        float glow = EffectsDisplayConfig.additiveGlow() ? glowLevel(e, t) : 0.0F;
        VertexConsumer base = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        float mount = Math.min(1.0F, ext * 3.0F);
        ps.pushPose();
        ps.translate(0.0F, 0.0F, MOUNT_CENTER_Z / 16.0F);
        ps.scale(mount, mount, mount);
        ps.translate(0.0F, 0.0F, -MOUNT_CENTER_Z / 16.0F);
        emit(ps.last(), base, model.mount, light, 1.0F);
        ps.popPose();

        ps.pushPose();
        ps.translate(0.0F, 0.0F, BLADE_ROOT_Z / 16.0F);
        ps.scale(1.0F, 1.0F, ext);
        ps.translate(0.0F, 0.0F, -BLADE_ROOT_Z / 16.0F);
        emit(ps.last(), base, model.blade, light, 1.0F);
        ps.popPose();

        if (glow > 0.0F) {
            VertexConsumer add = buffers.getBuffer(RenderType.eyes(GLOW));
            ps.pushPose();
            ps.translate(0.0F, 0.0F, MOUNT_CENTER_Z / 16.0F);
            ps.scale(mount, mount, mount);
            ps.translate(0.0F, 0.0F, -MOUNT_CENTER_Z / 16.0F);
            emit(ps.last(), add, model.mount, light, glow);
            ps.popPose();
            ps.pushPose();
            ps.translate(0.0F, 0.0F, BLADE_ROOT_Z / 16.0F);
            ps.scale(1.0F, 1.0F, ext);
            ps.translate(0.0F, 0.0F, -BLADE_ROOT_Z / 16.0F);
            emit(ps.last(), add, model.blade, light, glow * ext);
            ps.popPose();
        }
    }

    /** 发光的亮度：平时 0.7，每一刀刺入那一下亮到 1、200 ms 内退回去 */
    private static float glowLevel(MercyClient.Exec e, long t) {
        float k = 0.7F;
        for (int ts : e.stabs) {
            long d = t - ts;
            if (d >= 0L && d < 200L) {
                k = Math.max(k, 1.0F - 0.3F * (d / 200.0F));
            }
        }
        return k * KuvaFxDirector.additiveScale(e.anchor());
    }

    /** 手臂方块在部件里的横向中心（像素） */
    private static float armCenterX(boolean right, boolean slim) {
        if (right) {
            return slim ? -0.5F : -1.0F;
        }
        return slim ? 0.5F : 1.0F;
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc, float[] quads, int light, float bright) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int q = 0; q < quads.length; q += QUAD) {
            float nx = quads[q + 20], ny = quads[q + 21], nz = quads[q + 22];
            for (int i = 0; i < 4; i++) {
                int o = q + i * 5;
                vc.vertex(m, quads[o], quads[o + 1], quads[o + 2])
                        .color(bright, bright, bright, 1.0F)
                        .uv(quads[o + 3], quads[o + 4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(n, nx, ny, nz)
                        .endVertex();
            }
        }
    }

    // ==================== 第一人称 ====================

    // 第一人称摆手臂用的临时量（只在渲染线程用）
    private static final MercyPose.Arm ARM = new MercyPose.Arm();
    private static final Quaternionf PART = new Quaternionf();
    private static final Quaternionf ALIGN = new Quaternionf();
    private static final Matrix3f FROM = new Matrix3f();
    private static final Matrix3f TO = new Matrix3f();
    private static final Vector3f AXIS = new Vector3f();
    private static final Vector3f TOP = new Vector3f();
    private static final Vector3f SIDE = new Vector3f();
    private static final Vector3f DIR = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final Vector3f CROSS = new Vector3f();
    private static final Vector3f FIST = new Vector3f();

    /** 本地玩家此刻要不要由这里画第一人称的手 */
    private static boolean drawsFirstPerson(MercyClient.Exec e, long t) {
        Minecraft mc = Minecraft.getInstance();
        return e.local && !e.handedBack && t < e.handbackMs && mc.options.getCameraType().isFirstPerson()
                && MercyCamera.shotAt(e, t) == MercyCamera.Shot.GAME;
    }

    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
    public static final class Hands {
        private Hands() {
        }

        @SubscribeEvent
        public static void onRenderHand(RenderHandEvent event) {
            MercyClient.Exec e = MercyClient.local();
            if (e == null) {
                return;
            }
            long t = e.elapsed();
            if (!drawsFirstPerson(e, t)) {
                return;
            }
            event.setCanceled(true);
            if (event.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            AbstractClientPlayer player = mc.player;
            if (player == null || player.isInvisible()) {
                return;
            }
            EntityRenderer<? super AbstractClientPlayer> r = mc.getEntityRenderDispatcher().getRenderer(player);
            if (!(r instanceof PlayerRenderer renderer)) {
                return;
            }
            boolean slim = "slim".equals(player.getModelName());
            PoseStack ps = event.getPoseStack();
            MultiBufferSource buffers = event.getMultiBufferSource();
            int light = event.getPackedLight();
            float ext = MercyTimeline.bladeExtension(e.plan, t, e.shortOutro);
            MercyPose.firstPersonPass = true;
            try {
                drawArm(renderer, player, ps, buffers, light, e, t, false, slim, 0.0F);
                drawArm(renderer, player, ps, buffers, light, e, t, true, slim, ext);
            } finally {
                MercyPose.firstPersonPass = false;
            }
        }
    }

    /**
     * 把一条胳膊摆到给定的拳头位置：前臂指向「肩 → 拳头」的方向（再按出刀的角度往下压一点），
     * 前臂背面朝视角的上方（拧刀时绕前臂转开），然后照原版的画法把胳膊和袖子画出来
     */
    private static void drawArm(PlayerRenderer renderer, AbstractClientPlayer player, PoseStack ps,
                                MultiBufferSource buffers, int light, MercyClient.Exec e, long t, boolean right,
                                boolean slim, float ext) {
        MercyPose.firstPerson(e, t, right, ARM);
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        // 原版画第一人称手时会用全零参数摆一次模型，先照样摆一次，拿到那时手臂部件的位置和朝向
        model.attackTime = 0.0F;
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        ModelPart arm = right ? model.rightArm : model.leftArm;
        arm.xRot = 0.0F;

        PART.rotationZYX(arm.zRot, arm.yRot, arm.xRot);
        AXIS.set(0.0F, 1.0F, 0.0F).rotate(PART);
        TOP.set(0.0F, 0.0F, -1.0F).rotate(PART);
        FIST.set(armCenterX(right, slim) / 16.0F, FIST_Y / 16.0F, 0.0F).rotate(PART)
                .add(arm.x / 16.0F, arm.y / 16.0F, arm.z / 16.0F);

        // 视角空间里朝前是 −z
        float px = (float) ARM.x, py = (float) ARM.y, pz = (float) -ARM.z;
        float sx = right ? SHOULDER_X : -SHOULDER_X;
        DIR.set(px - sx, py - SHOULDER_Y, pz + SHOULDER_Z).normalize();
        if (ARM.pitchDown != 0.0F) {
            CROSS.set(DIR).cross(0.0F, 1.0F, 0.0F);
            if (CROSS.lengthSquared() > 1.0E-6F) {
                CROSS.normalize();
                DIR.rotateAxis((float) Math.toRadians(-ARM.pitchDown), CROSS.x, CROSS.y, CROSS.z);
            }
        }
        UP.set(0.0F, 1.0F, 0.0F).fma(-DIR.y, DIR).normalize();
        if (ARM.roll != 0.0F) {
            // 拧刀：前臂背面绕着前臂转
            UP.rotateAxis((float) Math.toRadians(ARM.roll), DIR.x, DIR.y, DIR.z);
        }

        // 旋转：把（前臂轴、背面、两者叉乘）这组正交基转到（朝向、上方、两者叉乘）
        SIDE.set(AXIS).cross(TOP);
        FROM.set(AXIS, TOP, SIDE);
        CROSS.set(DIR).cross(UP);
        TO.set(DIR, UP, CROSS);
        FROM.transpose();
        TO.mul(FROM);
        ALIGN.setFromNormalized(TO);

        ps.pushPose();
        ps.translate(px, py, pz);
        ps.mulPose(ALIGN);
        ps.translate(-FIST.x, -FIST.y, -FIST.z);
        if (right) {
            renderer.renderRightHand(ps, buffers, light, player);
            if (ext > 0.01F) {
                ps.pushPose();
                drawOnArm(ps, buffers, light, model.rightArm, slim, e, t, ext);
                ps.popPose();
            }
        } else {
            renderer.renderLeftHand(ps, buffers, light, player);
        }
        ps.popPose();
    }

    // ==================== 挂到玩家渲染器上 ====================

    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        @SuppressWarnings("unchecked")
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer<?, ?> r = event.getSkin(skin);
                if (r instanceof PlayerRenderer pr) {
                    pr.addLayer(new ParazonLayer(pr, "slim".equals(skin)));
                }
            }
        }

        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                baked = null;
                loadFailed = false;
            });
        }
    }

    // ==================== 读模型 ====================

    /** 一个四边形占的浮点数：4 个顶点 ×（x y z u v）+ 法线 */
    private static final int QUAD = 23;

    private record Baked(float[] mount, float[] blade) {
    }

    @Nullable
    private static Baked baked;
    private static boolean loadFailed;

    @Nullable
    private static Baked model() {
        if (baked == null && !loadFailed) {
            try {
                baked = load();
            } catch (Exception ex) {
                loadFailed = true;
                LogUtil.error("腕刃模型读取失败：" + MODEL, ex);
            }
        }
        return baked;
    }

    private static Baked load() throws Exception {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(MODEL);
        if (res.isEmpty()) {
            throw new IllegalStateException("找不到模型文件");
        }
        JsonObject root;
        try (Reader reader = res.get().openAsReader()) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        float tw = 16.0F, th = 16.0F;
        if (root.has("texture_size")) {
            JsonArray size = root.getAsJsonArray("texture_size");
            tw = size.get(0).getAsFloat();
            th = size.get(1).getAsFloat();
        }
        List<float[]> mount = new ArrayList<>();
        List<float[]> blade = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("elements")) {
            JsonObject o = el.getAsJsonObject();
            float[] from = vec(o.getAsJsonArray("from"));
            float[] to = vec(o.getAsJsonArray("to"));
            Quaternionf rot = null;
            float[] origin = null;
            // 原版只许 22.5° 的倍数，小角度写在 bend 里（原版不认这个键，读 models 下的文件时不会报错）
            String rotKey = o.has("bend") ? "bend" : (o.has("rotation") ? "rotation" : null);
            if (rotKey != null) {
                JsonObject r = o.getAsJsonObject(rotKey);
                origin = vec(r.getAsJsonArray("origin"));
                float angle = (float) Math.toRadians(r.get("angle").getAsFloat());
                rot = switch (r.get("axis").getAsString()) {
                    case "x" -> new Quaternionf().rotationX(angle);
                    case "y" -> new Quaternionf().rotationY(angle);
                    default -> new Quaternionf().rotationZ(angle);
                };
            }
            boolean isBlade = o.has("group") && "blade".equals(o.get("group").getAsString());
            JsonObject faces = o.getAsJsonObject("faces");
            for (String dir : faces.keySet()) {
                JsonObject f = faces.getAsJsonObject(dir);
                float[] uv = vec(f.getAsJsonArray("uv"));
                boolean swap = f.has("rotation") && f.get("rotation").getAsInt() % 180 == 90;
                (isBlade ? blade : mount).add(bakeFace(dir, from, to, uv, swap, tw, th, rot, origin));
            }
        }
        return new Baked(flatten(mount), flatten(blade));
    }

    private static float[] vec(JsonArray a) {
        float[] v = new float[a.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = a.get(i).getAsFloat();
        }
        return v;
    }

    private static float[] flatten(List<float[]> quads) {
        float[] out = new float[quads.size() * QUAD];
        for (int i = 0; i < quads.size(); i++) {
            System.arraycopy(quads.get(i), 0, out, i * QUAD, QUAD);
        }
        return out;
    }

    /**
     * 烘一个面：按方向定好平面、u 轴、v 轴，四个角按 (0,0) (0,1) (1,1) (1,0) 排，绕向和法线不一致就反过来排
     */
    private static float[] bakeFace(String dir, float[] from, float[] to, float[] uv, boolean swap, float tw, float th,
                                    @Nullable Quaternionf rot, @Nullable float[] origin) {
        int fixed, ua, va;
        float plane;
        boolean vDown;
        float nx = 0.0F, ny = 0.0F, nz = 0.0F;
        switch (dir) {
            case "up" -> {
                fixed = 1; plane = to[1]; ua = 0; va = 2; vDown = false; ny = 1.0F;
            }
            case "down" -> {
                fixed = 1; plane = from[1]; ua = 0; va = 2; vDown = false; ny = -1.0F;
            }
            case "east" -> {
                fixed = 0; plane = to[0]; ua = 2; va = 1; vDown = true; nx = 1.0F;
            }
            case "west" -> {
                fixed = 0; plane = from[0]; ua = 2; va = 1; vDown = true; nx = -1.0F;
            }
            case "south" -> {
                fixed = 2; plane = to[2]; ua = 0; va = 1; vDown = true; nz = 1.0F;
            }
            case "north" -> {
                fixed = 2; plane = from[2]; ua = 0; va = 1; vDown = true; nz = -1.0F;
            }
            default -> throw new IllegalArgumentException("未知的面：" + dir);
        }
        float[][] st = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        float[][] pos = new float[4][3];
        float[][] tex = new float[4][2];
        for (int i = 0; i < 4; i++) {
            float s = st[i][0], t = st[i][1];
            pos[i][fixed] = plane;
            pos[i][ua] = Mth.lerp(s, from[ua], to[ua]);
            pos[i][va] = vDown ? Mth.lerp(t, to[va], from[va]) : Mth.lerp(t, from[va], to[va]);
            float a = swap ? t : s;
            float b = swap ? s : t;
            tex[i][0] = Mth.lerp(a, uv[0], uv[2]) / tw;
            tex[i][1] = Mth.lerp(b, uv[1], uv[3]) / th;
        }
        // 绕向：(p1 − p0) × (p2 − p0) 要和法线同向
        float ax = pos[1][0] - pos[0][0], ay = pos[1][1] - pos[0][1], az = pos[1][2] - pos[0][2];
        float bx = pos[2][0] - pos[0][0], by = pos[2][1] - pos[0][1], bz = pos[2][2] - pos[0][2];
        float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        int[] order = cx * nx + cy * ny + cz * nz >= 0.0F ? new int[]{0, 1, 2, 3} : new int[]{0, 3, 2, 1};

        Vector3f n = new Vector3f(nx, ny, nz);
        if (rot != null) {
            n.rotate(rot);
        }
        float[] out = new float[QUAD];
        for (int k = 0; k < 4; k++) {
            int i = order[k];
            Vector3f p = new Vector3f(pos[i][0], pos[i][1], pos[i][2]);
            if (rot != null) {
                p.sub(origin[0], origin[1], origin[2]).rotate(rot).add(origin[0], origin[1], origin[2]);
            }
            int o = k * 5;
            out[o] = p.x / 16.0F;
            out[o + 1] = p.y / 16.0F;
            out[o + 2] = p.z / 16.0F;
            out[o + 3] = tex[i][0];
            out[o + 4] = tex[i][1];
        }
        out[20] = n.x;
        out[21] = n.y;
        out[22] = n.z;
        return out;
    }
}
