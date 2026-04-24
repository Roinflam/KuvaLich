package pers.roinflam.kuvalich.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
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
import pers.roinflam.kuvalich.utils.Reference;

import java.util.OptionalDouble;

/**
 * 元素几何渲染器（客户端 · v6 精简版）
 * Element Geometry Renderer (Client-side · v6 Minimal)
 *
 * <p><b>v6 变更：仅渲染磁力元素的双正交环</b>。v5 版本的冰/火/毒/辐射/腐蚀/
 * 病毒/穿刺/7 种元素几何图形全部移除。用户反馈其他元素线条"太抽象"，
 * 只保留磁力作为"磁场感"的视觉补充（磁力粒子已全部移除）。</p>
 *
 * <p>核心价值：零 PNG 依赖。所有图形通过 {@link VertexConsumer} 直接发送
 * 顶点坐标和颜色到 GPU，使用 {@code DefaultVertexFormat.POSITION_COLOR_NORMAL}
 * 格式（位置 + 颜色 + 法线，没有 UV 纹理坐标）。</p>
 *
 * <p>与其他视觉层的分工：
 * <ul>
 *     <li>{@code ElementRenderHandler}：实体整体染色 + 冰块装饰</li>
 *     <li>{@code ElementParticleEffects}：原版粒子（7 种元素的视觉）</li>
 *     <li>{@code ElementGeometryRenderer}（本类）：<b>仅磁力</b>的纯几何双环</li>
 * </ul></p>
 *
 * <p>绘制时机：{@link RenderLevelStageEvent.Stage#AFTER_PARTICLES}。深度测试
 * 开启，几何线条会被方块/实体遮挡保持世界沉浸感。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ElementGeometryRenderer {

    // ==================== 磁力配色 ====================

    /** 不透明度 */
    private static final int ALPHA = 220;

    /** 磁力紫 / Magnetic purple */
    private static final int[] RGB_MAGNETIC = {200, 100, 255};

    /** 磁力 debuff 名 */
    private static final String ELEM_MAGNETIC = "magnetic";

    /** 最大渲染距离（方块，平方）——与 ElementParticleEffects 的 PARTICLE_RANGE 保持一致 */
    private static final double MAX_RENDER_DIST_SQR = 64.0 * 64.0;

    // ==================== 自定义 RenderType ====================

    /**
     * 自定义线条渲染类型
     * <p>基于 POSITION_COLOR_NORMAL 顶点格式（无纹理），LINES 模式，线宽 3.0，
     * 开启半透明混合 + 深度测试，使用原版的 RENDERTYPE_LINES_SHADER。</p>
     * <p>通过 {@link GeometryRenderType} 辅助子类创建，以绕过 1.20.1 中
     * {@code RenderStateShard} 的 protected 访问限制。</p>
     */
    private static final RenderType GLOW_LINES = GeometryRenderType.createGlowLines();

    /**
     * 辅助类：通过继承 {@link RenderType}（间接继承 {@link RenderStateShard}）
     * 获得对父类 protected 静态成员的访问权限。
     *
     * <p>Minecraft 1.20.1 中 {@code RenderStateShard} 的 {@code LineStateShard}、
     * {@code TRANSLUCENT_TRANSPARENCY}、{@code NO_CULL}、{@code COLOR_DEPTH_WRITE}
     * 声明为 protected。Java 规范允许子类访问父类的 protected 静态成员，
     * 不受包限制。此类不会被实例化，构造器仅为满足 RenderType 抽象父类契约。</p>
     */
    private static final class GeometryRenderType extends RenderType {

        /**
         * 私有构造器：永远不会被调用。
         */
        private GeometryRenderType(String name, VertexFormat format, VertexFormat.Mode mode,
                                   int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                                   Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
        }

        /**
         * 创建磁力线条渲染类型
         *
         * @return 配置好的 GLOW_LINES RenderType 实例
         */
        static RenderType createGlowLines() {
            return RenderType.create(
                    "kuvalich_magnetic_geometry",
                    DefaultVertexFormat.POSITION_COLOR_NORMAL,
                    VertexFormat.Mode.LINES,
                    256,
                    false,
                    false,
                    CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                            .setLineState(new LineStateShard(OptionalDouble.of(3.0)))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_DEPTH_WRITE)
                            .createCompositeState(false)
            );
        }
    }

    // ==================== 主渲染事件 ====================

    /**
     * 每帧渲染所有被挂磁力 debuff 的可见实体身上的双环几何
     *
     * @param event 关卡渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(GLOW_LINES);

        long gameTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        // 取模防止 float 精度溢出（720000 tick ≈ 10 小时）
        float time = (float) (gameTime % 720000L) + partialTick;

        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (firstPerson && entity == mc.getCameraEntity()) continue;

            // 只处理磁力 debuff
            if (!ClientElementDebuffTracker.has(living, ELEM_MAGNETIC)) continue;

            // 距离裁剪
            double ex = entity.getX();
            double ey = entity.getY();
            double ez = entity.getZ();
            double dx = ex - cam.x, dy = ey - cam.y, dz = ez - cam.z;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQR) continue;

            double rx = Mth.lerp(partialTick, entity.xOld, ex) - cam.x;
            double ry = Mth.lerp(partialTick, entity.yOld, ey) - cam.y;
            double rz = Mth.lerp(partialTick, entity.zOld, ez) - cam.z;

            drawMagnetic(poseStack, vc, rx, ry, rz, living.getBbWidth(), living.getBbHeight(), time);
        }

        // 提交顶点数据到 GPU
        buffers.endBatch(GLOW_LINES);
    }

    // ==================== 磁力几何图形 ====================

    /**
     * 磁力：2 个正交大圆环（水平环慢顺时针 + 竖直环快逆时针）
     * <p>模拟磁场的双极几何特征。水平环绕 Y 轴旋转，竖直环绕 Z 轴旋转，
     * 两环方向相反、速度不同，形成"磁场线缠绕"的视觉。</p>
     *
     * @param ps    渲染矩阵栈
     * @param vc    顶点消费者
     * @param rx    实体相对相机 X
     * @param ry    实体相对相机 Y（已减去相机 y）
     * @param rz    实体相对相机 Z
     * @param bw    实体 bbWidth
     * @param bh    实体 bbHeight
     * @param time  渲染时间（用于旋转动画）
     */
    private static void drawMagnetic(PoseStack ps, VertexConsumer vc,
                                     double rx, double ry, double rz,
                                     float bw, float bh, float time) {
        ps.pushPose();
        ps.translate(rx, ry + bh * 0.5, rz);

        float base = bw * 0.9f;
        int cr = RGB_MAGNETIC[0], cg = RGB_MAGNETIC[1], cb = RGB_MAGNETIC[2];

        // ⭐ 呼吸缩放：主体半径在 85% ~ 115% 之间正弦波动（周期约 78 tick ≈ 3.9 秒）
        float breathe = 1.0f + 0.15f * Mth.sin(time * 0.08f);
        float r = base * breathe;

        // 主六边形（XZ 水平平面，绕 Y 轴顺时针慢）
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 1.2f));
        drawCircleOutline(ps.last().pose(), vc, r, 6, cr, cg, cb, ALPHA, Plane.XZ);
        ps.popPose();

        // 副六边形（XY 竖直平面，绕 Z 轴逆时针快，相位错开 30° 避免重影）
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(-time * 2.0f + 30.0f));
        drawCircleOutline(ps.last().pose(), vc, r, 6, cr, cg, cb, ALPHA, Plane.XY);
        ps.popPose();

        // ⭐ 两个卫星三角形绕大六边形公转（180° 对称布置）
        // 卫星半径（小一号）+ 自身呼吸
        float satR = base * 0.28f * (1.0f + 0.1f * Mth.cos(time * 0.12f));
        float orbitR = base * 1.1f;  // 公转轨道半径
        float orbitAngle = time * 4.0f;

        // 卫星 1：顺时针公转 + 自身逆时针自转
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(orbitAngle));
        ps.translate(orbitR, 0, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-time * 6.0f));
        drawCircleOutline(ps.last().pose(), vc, satR, 3, cr, cg, cb, ALPHA, Plane.XZ);
        ps.popPose();

        // 卫星 2：相反方向（180° 对位）+ 自身顺时针自转
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(orbitAngle + 180.0f));
        ps.translate(orbitR, 0, 0);
        ps.mulPose(Axis.YP.rotationDegrees(time * 6.0f));
        drawCircleOutline(ps.last().pose(), vc, satR, 3, cr, cg, cb, ALPHA, Plane.XZ);
        ps.popPose();

        ps.popPose();
    }

    // ==================== 图元工具 ====================

    /** 圆形绘制的平面枚举 */
    private enum Plane { XY, XZ }

    /**
     * 圆形轮廓（可选平面）
     *
     * @param m        变换矩阵
     * @param vc       顶点消费者
     * @param radius   半径
     * @param segments 分段数（越高越圆）
     * @param r/g/b/a  颜色
     * @param plane    平面
     */
    private static void drawCircleOutline(Matrix4f m, VertexConsumer vc,
                                          float radius, int segments,
                                          int r, int g, int b, int a,
                                          Plane plane) {
        for (int i = 0; i < segments; i++) {
            float ang1 = (float) (i * 2 * Math.PI / segments);
            float ang2 = (float) ((i + 1) * 2 * Math.PI / segments);
            float c1 = Mth.cos(ang1) * radius, s1 = Mth.sin(ang1) * radius;
            float c2 = Mth.cos(ang2) * radius, s2 = Mth.sin(ang2) * radius;

            switch (plane) {
                case XZ:
                    vc.vertex(m, c1, 0, s1).color(r, g, b, a).normal(0, 1, 0).endVertex();
                    vc.vertex(m, c2, 0, s2).color(r, g, b, a).normal(0, 1, 0).endVertex();
                    break;
                case XY:
                    vc.vertex(m, c1, s1, 0).color(r, g, b, a).normal(0, 0, 1).endVertex();
                    vc.vertex(m, c2, s2, 0).color(r, g, b, a).normal(0, 0, 1).endVertex();
                    break;
            }
        }
    }

    /** 工具类禁止实例化 */
    private ElementGeometryRenderer() {
    }
}
