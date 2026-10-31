package pers.roinflam.kuvalich.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.client.fx.ElementTint;
import pers.roinflam.kuvalich.client.fx.KuvaRenderTypes;
import pers.roinflam.kuvalich.client.fx.script.ElementFx;
import pers.roinflam.kuvalich.client.fx.script.ElementStatusFx;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 原版生物的元素整只染色、冰块装饰和病毒触发时的描边闪
 *
 * <ol>
 *     <li>{@link RenderLivingEvent.Pre}：按 {@link ElementTint#tintRgb} 的颜色调 {@link RenderSystem#setShaderColor}
 *         整只染色（玄骸和奴仆走 GeckoLib，不发这个事件，它们的渲染器自己调同一个方法取色）。</li>
 *     <li>{@link RenderLivingEvent.Post}：无条件复位颜色，画冰块装饰；病毒刚触发的 120 ms 里用外壳描边闪一下病毒色。</li>
 * </ol>
 *
 * <p><b>为什么要在 Pre / Post 各 flush 一次</b>：{@code MultiBufferSource.BufferSource} 是延后绘制的，
 * 实体顶点进缓冲后要等统一 {@code endBatch()} 才真正画，那时着色器读到的颜色是最后一次 {@code setShaderColor} 的值。
 * 不在前后手动 flush，Post 里的复位会让这只的染色在真正绘制时丢掉（表现为「冰块出了但不变色」）。
 * flush 会打断批渲染，所以只给 64 格内、真的有元素状态的实体做。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ElementRenderHandler {

    private static final String ELEM_ICE = "ice";

    /** 冰晶在实体身上的相对位置（x / z 以宽为单位，y 以高为单位） */
    private static final float[][] ICE_CRYSTAL_POSITIONS = {
            {0.00f, 0.20f, 0.00f},   // 腹部
            {0.35f, 0.55f, 0.00f},   // 右肩
            {-0.35f, 0.55f, 0.00f},  // 左肩
            {0.00f, 0.85f, 0.00f},   // 头顶
            {0.00f, 0.40f, 0.30f},   // 后背
    };

    private static final double MAX_RENDER_DIST_SQR = 64.0 * 64.0;

    private static boolean beyondRenderDistance(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return false;
        net.minecraft.world.phys.Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        double dx = camPos.x - entity.getX();
        double dy = camPos.y - entity.getY();
        double dz = camPos.z - entity.getZ();
        return dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQR;
    }

    /** 实体渲染前：元素染色 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled()) return;
        int rgb = ElementTint.tintRgb(event.getEntity());
        if (rgb >= 0) {
            // flush 之前累积的顶点，免得这只的颜色染到前面画的东西上
            flushBuffers(event.getMultiBufferSource());
            RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                    (rgb & 0xFF) / 255.0F, 1.0F);
        }
    }

    /** 实体渲染后：复位染色 + 冰块装饰 + 病毒描边闪 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        // 若 Pre 染过色，复位前必须 flush，这只的顶点才会按染色画出来。
        // 这里重新判定而不是记住 Pre 的结果，是为了兼容嵌套渲染（骑乘时递归画乘客）
        if (ElementTint.tintRgb(entity) >= 0) {
            flushBuffers(event.getMultiBufferSource());
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (beyondRenderDistance(entity)) {
            return;
        }
        if (ElementDebuffTracker.has(entity, ELEM_ICE)) {
            renderIceCrystalsOnEntity(entity, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        }
        float rim = ElementStatusFx.virusRimAlpha(entity.getId());
        if (rim > 0.0F) {
            renderVirusRim(event, entity, rim);
        }
    }

    private static void flushBuffers(MultiBufferSource source) {
        if (source instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    /**
     * 病毒触发那一下的描边闪：同一个模型、同一套姿态按中心放大一点再画一遍外壳，只剩轮廓外面一圈
     */
    private static void renderVirusRim(RenderLivingEvent.Post<?, ?> event, LivingEntity e, float alpha) {
        EntityModel<?> model = event.getRenderer().getModel();
        PoseStack ps = event.getPoseStack();
        float pt = event.getPartialTick();
        int rgb = ElementFx.virusColor();
        float a = 0.8F * alpha * KuvaRenderTypes.rimAlphaScale();
        // 照原版生物渲染器的变换：转身体朝向、翻转、往下挪 1.501，模型各部件的姿态这一帧已经摆好了
        ps.pushPose();
        float bodyYaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.translate(0.0F, -1.501F, 0.0F);
        float cy = 1.501F - e.getBbHeight() * 0.5F;
        ps.translate(0.0F, cy, 0.0F);
        ps.scale(KuvaRenderTypes.RIM_HULL_SCALE, KuvaRenderTypes.RIM_HULL_SCALE, KuvaRenderTypes.RIM_HULL_SCALE);
        ps.translate(0.0F, -cy, 0.0F);
        model.renderToBuffer(ps, event.getMultiBufferSource().getBuffer(KuvaRenderTypes.rimHull()), 0xF000F0,
                OverlayTexture.NO_OVERLAY, ((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, a);
        ps.popPose();
    }

    /** 在实体身上渲染蓝冰装饰 */
    private static void renderIceCrystalsOnEntity(LivingEntity entity, PoseStack poseStack,
                                                  MultiBufferSource buffers, int packedLight) {
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BlockState iceState = Blocks.BLUE_ICE.defaultBlockState();

        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        float baseScale = Math.min(width, height) * 0.35f;

        for (float[] relativePos : ICE_CRYSTAL_POSITIONS) {
            poseStack.pushPose();
            try {
                poseStack.translate(
                        relativePos[0] * width,
                        relativePos[1] * height,
                        relativePos[2] * width
                );
                float rotationDeg = (relativePos[0] + relativePos[1] + relativePos[2]) * 150.0f;
                poseStack.mulPose(Axis.YP.rotationDegrees(rotationDeg));
                poseStack.mulPose(Axis.XP.rotationDegrees(rotationDeg * 0.3f));

                poseStack.scale(baseScale, baseScale, baseScale);
                poseStack.translate(-0.5, -0.5, -0.5);

                blockRenderer.renderSingleBlock(iceState, poseStack, buffers,
                        packedLight, OverlayTexture.NO_OVERLAY);
            } finally {
                poseStack.popPose();
            }
        }
    }

    private ElementRenderHandler() {
    }
}
