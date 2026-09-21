package pers.roinflam.kuvalich.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 元素实体渲染处理器（客户端 · v8）
 * Element Entity Render Handler (Client-side · v8)
 *
 * <p>职责：
 * <ol>
 *     <li>在 {@link RenderLivingEvent.Pre} 根据实体身上的元素 debuff 调用
 *         {@link RenderSystem#setShaderColor} 整体染色实体。</li>
 *     <li>在 {@link RenderLivingEvent.Post} 无条件重置染色避免污染后续渲染，
 *         同时渲染冰块等装饰物。</li>
 * </ol></p>
 *
 * <p>⭐ v8 性能优化：染色计算改为<b>零分配</b>。
 * 原 {@code getEntityTint} 通过 {@code getAllActiveElements} 拿一个 List，
 * 内部还要先做一次快照 List，而它在 Pre / Post 各被调用一次，
 * 即每实体每帧 2~4 次 List 分配。现改为
 * {@link ClientElementDebuffTracker#forEachActiveElement} 配合静态累加器，
 * 全程只在最终返回时分配一个 {@link Vector3f}。</p>
 *
 * <p><b>关于 {@code endBatch()} 强制 flush 的保留说明：</b>
 * Minecraft 的 {@code MultiBufferSource.BufferSource} 是 deferred 渲染——
 * 实体模型顶点进 buffer 后不会立即 draw，而是等统一 {@code endBatch()} 时才 draw，
 * 此时 shader 读到的 {@code ColorModulator} uniform 是<b>最后一次</b>
 * {@code setShaderColor} 的值。若不在 Pre/Post 手动 flush，
 * Post 里的颜色重置会让本实体的染色在最终 draw 时丢失（表现为"冰块出但不变色"）。
 * 这个 flush 会打断批渲染、增加 draw call，但它是当前架构下保证染色正确的必要代价；
 * 已通过 64 格距离裁剪把影响范围限制在近处实体。</p>
 *
 * <p>v6 色调保留：{@code TINT_VIRUS} 为粉色 (1.00, 0.50, 0.80)。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ElementRenderHandler {

    // ==================== 元素染色色板 ====================

    /** 冰冻蓝 / Ice blue */
    private static final Vector3f TINT_ICE = new Vector3f(0.55f, 0.75f, 1.00f);

    /** 毒素强绿 / Poison strong green */
    private static final Vector3f TINT_POISON = new Vector3f(0.25f, 1.00f, 0.25f);

    /** 火焰橙红 / Fire orange-red */
    private static final Vector3f TINT_FIRE = new Vector3f(1.00f, 0.70f, 0.50f);

    /** 辐射黄绿 / Radiation yellow-green */
    private static final Vector3f TINT_RADIATION = new Vector3f(0.85f, 1.00f, 0.45f);

    /** 腐蚀深绿 / Corrosion dark green */
    private static final Vector3f TINT_CORROSION = new Vector3f(0.60f, 0.90f, 0.40f);

    /** 病毒粉色 / Virus pink */
    private static final Vector3f TINT_VIRUS = new Vector3f(1.00f, 0.50f, 0.80f);

    /** 磁力紫 / Magnetic purple */
    private static final Vector3f TINT_MAGNETIC = new Vector3f(0.85f, 0.55f, 1.00f);

    /** 穿刺浅银 / Puncture pale silver */
    private static final Vector3f TINT_PUNCTURE = new Vector3f(0.75f, 0.80f, 0.90f);

    // ==================== 元素名常量 ====================

    private static final String ELEM_ICE = "ice";
    private static final String ELEM_FIRE = "fire";
    private static final String ELEM_POISON = "poison";
    private static final String ELEM_RADIATION = "radiation";
    private static final String ELEM_CORROSION = "corrosion";
    private static final String ELEM_VIRUS = "virus";
    private static final String ELEM_MAGNETIC = "magnetic";
    private static final String ELEM_PUNCTURE = "puncture";

    // ==================== 冰块装饰配置 ====================

    /**
     * 冰晶在实体身上的相对位置（x, y, z 倍率）
     * x/z 以实体 bbWidth 为单位，y 以 bbHeight 为单位
     */
    private static final float[][] ICE_CRYSTAL_POSITIONS = {
            {0.00f, 0.20f, 0.00f},   // 腹部
            {0.35f, 0.55f, 0.00f},   // 右肩
            {-0.35f, 0.55f, 0.00f},  // 左肩
            {0.00f, 0.85f, 0.00f},   // 头顶
            {0.00f, 0.40f, 0.30f},   // 后背
    };

    // ==================== 距离裁剪 ====================

    /** 最大渲染距离平方（64 格） */
    private static final double MAX_RENDER_DIST_SQR = 64.0 * 64.0;

    // ==================== 零分配染色累加器 ====================

    /**
     * ⭐ 染色累加器：[r, g, b, count]
     * <p>渲染只在客户端主线程执行，静态复用安全，避免每帧分配临时容器。</p>
     */
    private static final float[] TINT_ACCUMULATOR = new float[4];

    /**
     * 判断实体是否超出 64 格渲染距离
     *
     * @param entity 目标实体
     * @return true 表示超出距离（应跳过所有渲染）
     */
    private static boolean beyondRenderDistance(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return false;
        net.minecraft.world.phys.Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        double dx = camPos.x - entity.getX();
        double dy = camPos.y - entity.getY();
        double dz = camPos.z - entity.getZ();
        return dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQR;
    }

    // ==================== 渲染事件监听 ====================

    /**
     * 实体渲染前：应用元素染色
     *
     * @param event 渲染前事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled()) return;

        LivingEntity entity = event.getEntity();
        // ⭐ 64 格距离裁剪
        if (beyondRenderDistance(entity)) return;

        Vector3f tint = getEntityTint(entity);
        if (tint != null) {
            // ⭐ flush 之前累积的顶点，避免当前 tint 污染前面实体的渲染
            flushBuffers(event.getMultiBufferSource());
            RenderSystem.setShaderColor(tint.x(), tint.y(), tint.z(), 1.0f);
        }
    }

    /**
     * 实体渲染后：无条件重置染色 + 渲染冰块装饰
     *
     * @param event 渲染后事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        boolean visible = !beyondRenderDistance(entity);

        // ⭐ 若 Pre 染过色，必须在重置颜色前 flush，确保当前实体的顶点用 tint 颜色 draw
        //    这里重新判定（而非缓存 Pre 的结果）是为了兼容嵌套渲染（骑乘实体递归渲染 passenger）
        //    的场景；在零分配改造后，重新判定的成本仅为一次 map 查询 + 少量浮点运算。
        if (visible && getEntityTint(entity) != null) {
            flushBuffers(event.getMultiBufferSource());
        }

        // 无条件重置颜色
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 冰块装饰同样遵守 64 格距离
        if (visible && ClientElementDebuffTracker.has(entity, ELEM_ICE)) {
            renderIceCrystalsOnEntity(entity, event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight());
        }
    }

    /**
     * 强制 flush BufferSource 的所有累积顶点，立即 draw 到 GPU。
     *
     * @param source MultiBufferSource（正常应为 BufferSource 实例）
     */
    private static void flushBuffers(MultiBufferSource source) {
        if (source instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    // ==================== 染色判断（零分配） ====================

    /**
     * 根据实体身上所有 active 元素 debuff 计算混合染色色值
     *
     * <p>每个 debuff 先按 amplifier 做白色→满色的 lerp（最低级约 70% 饱和，满级 100%），
     * 然后多 debuff 做等权 RGB 平均。</p>
     *
     * <p>⭐ 零分配：不再构造中间 List，直接用静态累加器累加。</p>
     *
     * @param entity 实体
     * @return 染色色值，无 debuff 返回 null
     */
    private static Vector3f getEntityTint(LivingEntity entity) {
        TINT_ACCUMULATOR[0] = 0f;
        TINT_ACCUMULATOR[1] = 0f;
        TINT_ACCUMULATOR[2] = 0f;
        TINT_ACCUMULATOR[3] = 0f;

        ClientElementDebuffTracker.forEachActiveElement(entity, (element, amplifier) -> {
            Vector3f tint = getElementTintForLevel(element, amplifier);
            if (tint == null) {
                return;
            }
            TINT_ACCUMULATOR[0] += tint.x();
            TINT_ACCUMULATOR[1] += tint.y();
            TINT_ACCUMULATOR[2] += tint.z();
            TINT_ACCUMULATOR[3] += 1f;
        });

        int count = (int) TINT_ACCUMULATOR[3];
        if (count <= 0) {
            return null;
        }
        float inv = 1.0f / count;
        return new Vector3f(
                TINT_ACCUMULATOR[0] * inv,
                TINT_ACCUMULATOR[1] * inv,
                TINT_ACCUMULATOR[2] * inv
        );
    }

    /**
     * 按元素 debuff 的 amplifier 计算最终染色（从白色向满色线性插值）
     *
     * <p>公式：{@code tint = 1 - factor * (1 - baseTint)}，factor ∈ [0.7, 1.0]：
     * 最低级（amplifier=0）factor=0.7，保证颜色有明显辨识度不会太淡；
     * 满级 factor=1.0，结果完全等于 baseTint。</p>
     *
     * <p>对单层元素（{@code getMaxLevel} 返回 0，例如毒素）直接返回满色。</p>
     *
     * @param element   元素名
     * @param amplifier 当前元素等级
     * @return 插值后的染色，无定义元素返回 null
     */
    private static Vector3f getElementTintForLevel(String element, int amplifier) {
        Vector3f baseTint = getElementBaseTint(element);
        if (baseTint == null) return null;

        int maxLevel = getMaxLevel(element);
        if (maxLevel <= 0) return baseTint;
        if (amplifier < 0) return baseTint;

        float ratio = Math.min((float) amplifier / maxLevel, 1.0f);
        float factor = 0.7f + 0.3f * ratio;

        return new Vector3f(
                1.0f - factor * (1.0f - baseTint.x()),
                1.0f - factor * (1.0f - baseTint.y()),
                1.0f - factor * (1.0f - baseTint.z())
        );
    }

    /**
     * 元素名 → 满饱和（满级）染色常量引用
     *
     * @param element 元素名
     * @return 对应的满色常量，无定义时返回 null
     */
    private static Vector3f getElementBaseTint(String element) {
        switch (element) {
            case ELEM_ICE: return TINT_ICE;
            case ELEM_FIRE: return TINT_FIRE;
            case ELEM_POISON: return TINT_POISON;
            case ELEM_RADIATION: return TINT_RADIATION;
            case ELEM_CORROSION: return TINT_CORROSION;
            case ELEM_VIRUS: return TINT_VIRUS;
            case ELEM_MAGNETIC: return TINT_MAGNETIC;
            case ELEM_PUNCTURE: return TINT_PUNCTURE;
            default: return null;
        }
    }

    /**
     * 元素名 → 最大 amplifier 等级
     * <p>用于染色 lerp 计算。必须与 {@code DynamicAttributes} 里各元素
     * createInstance 时的实际上限保持同步。</p>
     *
     * @param element 元素名
     * @return 最大等级，单层元素返回 0
     */
    private static int getMaxLevel(String element) {
        switch (element) {
            case ELEM_FIRE: return 3;
            case ELEM_ICE: return 8;
            case ELEM_POISON: return 0;       // 单层：固定满色
            case ELEM_RADIATION: return 9;
            case ELEM_CORROSION: return 9;
            case ELEM_VIRUS: return 9;
            case ELEM_MAGNETIC: return 9;
            case ELEM_PUNCTURE: return 3;
            default: return 0;
        }
    }

    // ==================== 冰块渲染 ====================

    /**
     * 在实体身上渲染蓝冰装饰（类似暮色森林寒冰弓效果）
     *
     * @param entity      冰冻的实体
     * @param poseStack   渲染矩阵栈
     * @param buffers     MultiBufferSource
     * @param packedLight 打包光照值
     */
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

    /** 工具类禁止实例化 / Utility class, no instantiation */
    private ElementRenderHandler() {
    }
}
