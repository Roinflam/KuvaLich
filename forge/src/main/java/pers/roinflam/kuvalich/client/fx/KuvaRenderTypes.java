package pers.roinflam.kuvalich.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 本模组自己的渲染类型
 */
@OnlyIn(Dist.CLIENT)
public final class KuvaRenderTypes {

    /** 4×4 纯白：颜色全由顶点色给 */
    public static final ResourceLocation WHITE = new ResourceLocation(Reference.MOD_ID, "textures/misc/white.png");
    /** 原版模型的外壳描边按中心放大的倍数（病毒描边闪、效果展示台用；玄骸 / 奴仆走每个立方体各自外撑，不用它） */
    public static final float RIM_HULL_SCALE = 1.035F;

    private static RenderType rimHull;

    private KuvaRenderTypes() {
    }

    /**
     * 外壳描边：把模型往外撑一圈再用这个类型画一遍，只画背面，剩下的就是轮廓外面一圈
     *
     * <p>怎么撑由调用方定：原版模型（病毒描边闪）整体按中心放大 {@link #RIM_HULL_SCALE} 倍；
     * 玄骸 / 奴仆的 GeckoLib 模型整体放大会让头顶、脚底的边很粗、躯干几乎没边，改由渲染器让每个立方体各自往外撑
     * （{@code AbstractKuvaRenderer#setHull}）。</p>
     *
     * <ul>
     *   <li>实体半透明自发光着色器、贴图是一张纯白，颜色和透明度走顶点色（调用方传描边色）；</li>
     *   <li>普通半透明混合；深度测试照常（不透墙，这是不用原版发光轮廓的原因），不写深度；</li>
     *   <li>剔除反过来：画的时候剔正面、只留背面，画完把剔除面还原成背面。</li>
     * </ul>
     *
     * <p>要在模型本体画完（深度已写）之后画，模型里面那部分壳才会被挡住。开了光影包时照样能画，颜色可能偏。
     * 关掉「加法发光」时调用方把描边的 α 再乘 {@link #rimAlphaScale()}。</p>
     */
    public static RenderType rimHull() {
        if (rimHull == null) {
            rimHull = Types.createRimHull();
        }
        return rimHull;
    }

    /** 描边透明度的系数：开着加法发光是 1，关掉时 0.6 */
    public static float rimAlphaScale() {
        return EffectsDisplayConfig.additiveGlow() ? 1.0F : 0.6F;
    }

    /** 借 RenderType 的子类拿到受保护的渲染状态 */
    private static final class Types extends RenderType {
        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                      boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }

        /** 剔正面：只剩背面朝着相机的那半个壳 */
        private static final CullStateShard CULL_FRONT = new CullStateShard(true) {
            @Override
            public void setupRenderState() {
                RenderSystem.enableCull();
                GL11.glCullFace(GL11.GL_FRONT);
            }

            @Override
            public void clearRenderState() {
                GL11.glCullFace(GL11.GL_BACK);
            }
        };

        static RenderType createRimHull() {
            // 不按距离排序：同一圈壳是同一个颜色、同一个透明度，片与片谁先画叠出来都一样，排序是白花的（围上来二十只时约占一成帧时间）
            return RenderType.create("kuvalich_rim_hull", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    256, true, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(WHITE, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(CULL_FRONT)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false));
        }
    }
}
