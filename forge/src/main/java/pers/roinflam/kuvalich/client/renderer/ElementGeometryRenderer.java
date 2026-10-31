package pers.roinflam.kuvalich.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 磁力持续期间身上的横向断续光带
 *
 * <p>同一时刻 3~5 条青白色的横向光带，各自从头往脚扫（每条 0.6 s 扫完，相位错开），
 * 半径是包围盒半宽的 1.2~1.5 倍、伸出身体两侧，每条都断成几截；头顶上方另有一圈细光环。层数越高带越多。
 * 每条带是竖直的薄圆柱面：一层亮芯（高 0.04 格）外加一层上下渐隐的柔光，从侧面看就是一道横向光带。</p>
 *
 * <p>开着「加法发光」时走加法混合（白天露天按 {@link KuvaFxDirector#additiveScale} 压暗），关掉时走普通透明混合。
 * 深度测试照常（会被方块和实体挡住），不写深度。渲染循环里不分配。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ElementGeometryRenderer {

    private static final String ELEM_MAGNETIC = "magnetic";
    private static final double MAX_RENDER_DIST_SQR = 64.0 * 64.0;

    /** 一圈分几段（断续的最小单位） */
    private static final int SEGMENTS = 28;
    /** 每条带从头扫到脚的时长 */
    private static final float SWEEP_MS = 600.0F;
    private static final float CORE_HALF = 0.02F;
    private static final float GLOW_HALF = 0.07F;

    private static final RenderType BANDS_ADD = BandType.create("kuvalich_magnetic_bands_add", true);
    private static final RenderType BANDS_ALPHA = BandType.create("kuvalich_magnetic_bands", false);

    /** 借 RenderType 的子类拿到受保护的渲染状态 */
    private static final class BandType extends RenderType {
        private BandType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                         boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }

        static RenderType create(String name, boolean additive) {
            return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048,
                    false, false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                            .setTransparencyState(additive ? LIGHTNING_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(false));
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean additive = EffectsDisplayConfig.additiveGlow();
        RenderType type = additive ? BANDS_ADD : BANDS_ALPHA;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        float timeMs = (float) (KuvaFxDirector.clockMs() % 3_600_000L);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (firstPerson && entity == mc.getCameraEntity()) continue;
            int amp = ElementDebuffTracker.getAmplifier(living, ELEM_MAGNETIC);
            if (amp < 0) continue;

            double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
            double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
            double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());
            double dx = ex - cam.x, dy = ey - cam.y, dz = ez - cam.z;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQR) continue;

            if (vc == null) {
                vc = buffers.getBuffer(type);
            }
            float scale = additive ? KuvaFxDirector.additiveScale(ex, ey + living.getBbHeight() * 0.5D, ez) : 1.0F;
            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            drawMagnetic(poseStack.last().pose(), vc, living.getBbWidth(), living.getBbHeight(), amp,
                    entity.getId(), timeMs, scale, additive);
            poseStack.popPose();
        }
        if (vc != null) {
            buffers.endBatch(type);
        }
    }

    /**
     * 一只实体身上的全部光带；矩阵原点在脚底
     *
     * @param amp 磁力等级（0 起），层数越高带越多（3~5 条）
     */
    private static void drawMagnetic(Matrix4f m, VertexConsumer vc, float bw, float bh, int amp, int seed,
                                     float timeMs, float scale, boolean additive) {
        int bands = 3 + Math.min(2, amp / 3);
        float hw = bw * 0.5F;
        float alphaMul = scale * (additive ? 1.0F : 0.8F);
        for (int i = 0; i < bands; i++) {
            float phase = timeMs / SWEEP_MS + (float) i / bands + hash(seed, i) * 0.15F;
            float frac = phase - Mth.floor(phase);
            float y = bh * (1.02F - frac * 1.04F);
            float r = hw * (1.2F + 0.3F * hash(seed + 17, i));
            // 进出两头淡入淡出，扫到中间最亮
            float a = Mth.sin(frac * Mth.PI) * alphaMul;
            if (a <= 0.01F) {
                continue;
            }
            int gapSeed = seed * 31 + i * 7 + (int) (phase);
            ribbon(m, vc, r, y, GLOW_HALF, FxPalette.MAG, a * 0.35F, true, gapSeed);
            ribbon(m, vc, r, y, CORE_HALF, FxPalette.MAG_BAND_CORE, a * 0.95F, false, gapSeed);
        }
        // 头顶上方一圈细光环，慢慢明暗
        float haloA = (0.55F + 0.25F * Mth.sin(timeMs * 0.004F + seed)) * alphaMul;
        float haloY = bh + 0.15F;
        ribbon(m, vc, hw * 1.3F, haloY, GLOW_HALF * 0.7F, FxPalette.MAG, haloA * 0.3F, true, seed * 13 + 5);
        ribbon(m, vc, hw * 1.3F, haloY, CORE_HALF * 0.8F, FxPalette.MAG_CORE, haloA * 0.8F, false, seed * 13 + 5);
    }

    /**
     * 一条竖直的薄圆柱面光带：中心高度 {@code y}、半高 {@code half}；{@code soft} 时上下两沿 α 为 0（柔光），否则整条实心。
     * 按 {@code gapSeed} 断掉其中几段
     */
    private static void ribbon(Matrix4f m, VertexConsumer vc, float r, float y, float half, int rgb, float alpha,
                               boolean soft, int gapSeed) {
        int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        if (a <= 2) {
            return;
        }
        int aEdge = soft ? 0 : a;
        float y0 = y - half, y1 = y + half;
        float step = Mth.TWO_PI / SEGMENTS;
        float rot = hash(gapSeed, 3) * Mth.TWO_PI;
        for (int s = 0; s < SEGMENTS; s++) {
            // 大约三成的段断开：按段号和种子取一个稳定的伪随机
            if (hash(gapSeed, s / 2) < 0.32F) {
                continue;
            }
            float t0 = rot + s * step, t1 = t0 + step;
            float x0 = Mth.cos(t0) * r, z0 = Mth.sin(t0) * r;
            float x1 = Mth.cos(t1) * r, z1 = Mth.sin(t1) * r;
            if (soft) {
                // 两半：沿口 0 → 中线 a → 沿口 0
                vc.vertex(m, x0, y0, z0).color(cr, cg, cb, aEdge).endVertex();
                vc.vertex(m, x1, y0, z1).color(cr, cg, cb, aEdge).endVertex();
                vc.vertex(m, x1, y, z1).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, x0, y, z0).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, x0, y, z0).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, x1, y, z1).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, x1, y1, z1).color(cr, cg, cb, aEdge).endVertex();
                vc.vertex(m, x0, y1, z0).color(cr, cg, cb, aEdge).endVertex();
            } else {
                vc.vertex(m, x0, y0, z0).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, x1, y0, z1).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, x1, y1, z1).color(cr, cg, cb, a).endVertex();
                vc.vertex(m, x0, y1, z0).color(cr, cg, cb, a).endVertex();
            }
        }
    }

    /** 0~1 的稳定伪随机 */
    private static float hash(int a, int b) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private ElementGeometryRenderer() {
    }
}
