package pers.roinflam.kuvalich.client.mercy;

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
import pers.roinflam.kuvalich.client.renderer.AbstractKuvaRenderer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 战斗增幅的渲染层：交战越久，玄骸身上原本那几道赤毒红纹路越亮
 *
 * <p>遮罩 {@code kuva_master_furymask.png}（tools/fx/make_furymask.py 从本体贴图挑出赤毒红像素生成）整只自发光再画一遍。
 * 亮度按怒意档位：2 档 0.25、3 档 0.5、4 档 0.8 并按 1.2 Hz 在 ±15% 之间呼吸，档位变了 1 s 内过渡过去，
 * 值由 {@link KuvaStateFx#furyLayerLevel} 给（倒地、死了、关掉状态特效时是 0）。特效强度「最少」时整层不画。</p>
 *
 * <p>开着「加法发光」时用 {@code eyes}（加法，不吃光照），白天露天乘 {@link KuvaFxDirector#additiveScale}；
 * 关掉时改半透明自发光，α 再乘 0.6。跟着本体透明度一起淡。奴仆的渲染器也挂着这一层，但奴仆的怒意只看背囊的光点，这里不画。</p>
 */
@OnlyIn(Dist.CLIENT)
public class KuvaFuryLayer<T extends AbstractKuva> extends GeoRenderLayer<T> {

    /** 怒意纹路遮罩，和玄骸本体贴图同尺寸 */
    public static final ResourceLocation FURY_MASK = new ResourceLocation(Reference.MOD_ID,
            "textures/entity/kuva_master_furymask.png");

    private final RenderType additive = RenderType.eyes(FURY_MASK);
    private final RenderType translucent = RenderType.entityTranslucentEmissive(FURY_MASK);

    public KuvaFuryLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        // 强度「最少」不画纹路层；这里直接拦，切到最少档当帧就没有，不等 1 s 的过渡
        if (!(animatable instanceof KuvaMasterEntity) || animatable.getId() < 0 || !EffectsDisplayConfig.kuvaStateFx()
                || EffectsDisplayConfig.intensity() == EffectsDisplayConfig.Intensity.MINIMAL) {
            return;
        }
        float level = KuvaStateFx.furyLayerLevel(animatable);
        if (level <= 0.004F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && animatable.isInvisibleTo(mc.player)) {
            return;
        }
        float body = getRenderer() instanceof AbstractKuvaRenderer<?> r ? r.frameAlpha()
                : AbstractKuvaRenderer.bodyAlpha(animatable, partialTick);
        if (body <= 0.01F) {
            return;
        }
        if (EffectsDisplayConfig.additiveGlow()) {
            float k = Math.min(1.0F, level) * Math.min(1.0F, body)
                    * KuvaFxDirector.additiveScale(animatable.getX(), animatable.getY(0.6D), animatable.getZ());
            if (k <= 0.004F) {
                return;
            }
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, this.additive,
                    bufferSource.getBuffer(this.additive), partialTick, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, k, k, k, 1.0F);
        } else {
            float a = Math.min(1.0F, level * 0.6F * body);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, this.translucent,
                    bufferSource.getBuffer(this.translucent), partialTick, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, a);
        }
    }
}
