package pers.roinflam.kuvalich.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaRenderTypes;
import pers.roinflam.kuvalich.client.fx.KuvaThrallRim;
import pers.roinflam.kuvalich.client.renderer.AbstractKuvaRenderer;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * 奴仆的常驻描边：冷色的一圈外壳，一眼认出哪些是奴仆
 *
 * <p>做法是把模型每个立方体各往外撑 {@link #HULL} 格，用只画背面的外壳渲染类型再画一遍：
 * 本体挡住的那部分壳被深度测试吃掉，剩下轮廓外面一圈。壳再沿视线往后推 {@link #DEPTH_PUSH} 格，
 * 身上前后叠着的小块之间就不会冒出线来。深度测试照常，隔着墙看不见（不用原版的发光轮廓就是这个原因）。</p>
 *
 * <ul>
 *   <li>颜色 {@link FxPalette#THRALL_RIM}，透明度 1.6 s 一个周期在 0.45~0.6 之间轻轻呼吸；</li>
 *   <li>挨到固定减伤时 {@link KuvaThrallRim#flash} 把透明度抬到 0.9；</li>
 *   <li>只在 24 格内画；关掉「加法发光」时透明度再乘 {@link KuvaRenderTypes#rimAlphaScale()}，退成淡淡一圈；</li>
 *   <li>跟着本体透明度一起淡；倒地时照留；选项 {@code thrall_outline} 关掉就不画。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class KuvaThrallRimLayer extends GeoRenderLayer<KuvaSlaveEntity> {

    /** 每个立方体往外撑多少格 */
    public static final float HULL = 0.03F;
    /**
     * 壳沿视线往后推多少格：前面的小块那圈壳会压在后面的本体上画出线来，推远以后被本体挡住；
     * 奴仆前后最厚（连背包）约半格，推这么多身上就不再有线，只剩外轮廓
     */
    public static final float DEPTH_PUSH = 0.5F;
    /** 多远以内画 */
    public static final double MAX_DIST = 24.0D;
    private static final float BREATH_MS = 1600.0F;
    private static final float ALPHA_MID = 0.525F;
    private static final float ALPHA_SWING = 0.075F;

    private final AbstractKuvaRenderer<KuvaSlaveEntity> kuvaRenderer;

    public KuvaThrallRimLayer(AbstractKuvaRenderer<KuvaSlaveEntity> renderer) {
        super(renderer);
        this.kuvaRenderer = renderer;
    }

    @Override
    public void render(PoseStack poseStack, KuvaSlaveEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        if (!EffectsDisplayConfig.thrallOutline()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && animatable.isInvisibleTo(mc.player)) {
            return;
        }
        if (animatable.getId() >= 0
                && mc.getEntityRenderDispatcher().distanceToSqr(animatable) > MAX_DIST * MAX_DIST) {
            return;
        }
        float body = this.kuvaRenderer.frameAlpha();
        if (body <= 0.01F) {
            return;
        }
        float ms = (animatable.tickCount + partialTick) * 50.0F;
        float a = ALPHA_MID + ALPHA_SWING * Mth.sin(ms / BREATH_MS * Mth.TWO_PI);
        float flash = KuvaThrallRim.alphaBoost(animatable.getId());
        if (flash > 0.0F) {
            a += (KuvaThrallRim.FLASH_ALPHA - a) * Math.min(1.0F, flash);
        }
        a *= body * KuvaRenderTypes.rimAlphaScale();
        if (a <= 0.004F) {
            return;
        }
        int rgb = FxPalette.THRALL_RIM;
        RenderType hull = KuvaRenderTypes.rimHull();
        // 肖像里的假实体画在正交投影的界面上，往后推会挪位置，不推
        this.kuvaRenderer.setHull(HULL, animatable.getId() >= 0 ? DEPTH_PUSH : 0.0F);
        try {
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, hull, bufferSource.getBuffer(hull),
                    partialTick, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, a);
        } finally {
            this.kuvaRenderer.setHull(0.0F, 0.0F);
        }
    }
}
