package pers.roinflam.kuvalich.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.KuvaGlow;
import pers.roinflam.kuvalich.client.renderer.AbstractKuvaRenderer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 玄骸 / 奴仆的发光层：只点遮罩上的几处焦点（玄骸胸口晶体和面甲两点、奴仆胸口一道裂纹），不点全身饰线
 *
 * <p><b>两遍</b>：</p>
 * <ol>
 *   <li>盖色：半透明自发光（不吃光照），遮罩的颜色 × 亮度乘子（封顶 1）盖在本体上。胸口晶体的原贴图是亮绿格子，
 *       纯加法叠红会叠成黄白，所以先把焦点盖成赤毒色；乘子低时是暗红，夜里也比周围亮。</li>
 *   <li>提亮：乘子超过 1（到场、消散、说话顶上去、怒意）时，再用加法把超出的部分叠上去，焦点发烫变亮。
 *       这一遍是加法层：白天露天乘 {@link KuvaFxDirector#additiveScale}，关掉「加法发光」时不画。</li>
 * </ol>
 *
 * <p>乘子来自 {@link KuvaGlow}（心跳、说话、倒地、消散、提亮、持续乘子都在那里算），两遍都跟着本体透明度一起淡。
 * 只画遮罩上有亮点的那几根骨骼：在主渲染过程里按骨骼画（{@code renderForBone}），不整只再画一遍，
 * 画完把本体的缓冲接回来。</p>
 */
@OnlyIn(Dist.CLIENT)
public class KuvaGlowLayer<T extends AbstractKuva> extends GeoRenderLayer<T> {

    /** 提亮那一遍：乘子每超出 1 叠多少 */
    private static final float ADD_GAIN = 0.8F;

    private final String[] bones;
    private final RenderType cover;
    private final RenderType add;

    // 这一帧算好的值（preRender 里算，同一帧各骨骼共用）
    private boolean frameOn;
    private float coverLevel;
    private float coverAlpha;
    private float addLevel;

    /**
     * @param mask  发光遮罩，和本体贴图同尺寸；不亮的像素要全透明且 RGB 为 0（加法那一遍按 RGB 叠）
     * @param bones 遮罩上有亮点的骨骼名
     */
    public KuvaGlowLayer(GeoRenderer<T> renderer, ResourceLocation mask, String... bones) {
        super(renderer);
        this.bones = bones;
        this.cover = RenderType.entityTranslucentEmissive(mask);
        this.add = RenderType.eyes(mask);
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                          MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                          int packedOverlay) {
        this.frameOn = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && animatable.isInvisibleTo(mc.player)) {
            return;
        }
        float alpha = getRenderer() instanceof AbstractKuvaRenderer<?> r ? r.frameAlpha()
                : AbstractKuvaRenderer.bodyAlpha(animatable, partialTick);
        if (alpha <= 0.003F) {
            return;
        }
        float m = KuvaGlow.multiplier(animatable, partialTick);
        this.coverLevel = Math.min(1.0F, m);
        this.coverAlpha = Math.min(1.0F, alpha);
        this.addLevel = 0.0F;
        if (m > 1.0F && EffectsDisplayConfig.additiveGlow()) {
            // 肖像里的假实体不在世界里，不按日照压
            float day = animatable.getId() < 0 ? 1.0F
                    : KuvaFxDirector.additiveScale(animatable.getX(), animatable.getY(0.6D), animatable.getZ());
            this.addLevel = Math.min(1.0F, (m - 1.0F) * ADD_GAIN) * day * this.coverAlpha;
        }
        this.frameOn = true;
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay) {
        if (!this.frameOn || bone.isHidden() || !wanted(bone.getName())) {
            return;
        }
        float c = this.coverLevel;
        getRenderer().renderCubesOfBone(poseStack, bone, bufferSource.getBuffer(this.cover), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, c, c, c, this.coverAlpha);
        float a = this.addLevel;
        if (a > 0.004F) {
            getRenderer().renderCubesOfBone(poseStack, bone, bufferSource.getBuffer(this.add), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, a, a, a, 1.0F);
        }
        // 立即模式的缓冲源换渲染类型时会把共用的缓冲交掉，接着画本体之前要按本体的类型再要一次
        bufferSource.getBuffer(renderType);
    }

    private boolean wanted(String name) {
        for (String b : this.bones) {
            if (b.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
