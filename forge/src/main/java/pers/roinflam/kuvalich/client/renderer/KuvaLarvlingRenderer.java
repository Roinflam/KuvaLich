package pers.roinflam.kuvalich.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.client.larvling.LarvlingOverhead;
import pers.roinflam.kuvalich.client.larvling.LarvlingWeaponLayer;
import pers.roinflam.kuvalich.client.mercy.KuvaFuryLayer;
import pers.roinflam.kuvalich.client.model.KuvaLarvlingModel;
import pers.roinflam.kuvalich.client.renderer.layer.KuvaGlowLayer;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 赤毒幼体渲染器
 *
 * <p>模型用奴仆的骨架摆（高约 40 像素），画的时候整只缩到 {@link KuvaLarvlingEntity#MODEL_SCALE}，站着约 1.9 格，和玩家差不多高；
 * 碰撞箱在 {@code KuvaLichEntities} 里按缩放后的身高定。骨骼的转动、倒地圈的锚点、手里的武器都在缩放之后的矩阵里，
 * 跟着一起缩。</p>
 *
 * <p>层：发光遮罩（只有两只眼睛，在头上）、手里那把将来玄骸的武器（{@link LarvlingWeaponLayer}）、
 * 交战越久越明显的怒意。没有奴仆那圈冷色描边，一眼和奴仆分开。淡入淡出、死亡、名牌和玄骸 / 奴仆共用
 * （{@link AbstractKuvaRenderer}），倒地时头顶的武器展示在 {@link LarvlingOverhead}。</p>
 */
public class KuvaLarvlingRenderer extends AbstractKuvaRenderer<KuvaLarvlingEntity> {

    public static final ResourceLocation GLOW_MASK = new ResourceLocation(Reference.MOD_ID,
            "textures/entity/kuva_larvling_glowmask.png");
    /** 遮罩上有亮点的骨骼，其余骨骼发光层不画 */
    private static final String[] GLOW_BONES = {"Head"};

    public KuvaLarvlingRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new KuvaLarvlingModel());
        withScale(KuvaLarvlingEntity.MODEL_SCALE);
        // 倒地圈挂在头的中间（骨架同奴仆；模型坐标，缩放跟着矩阵走）
        setMercyAnchor("Head", 0.0F, 36.5F, 0.0F);
        addRenderLayer(new KuvaGlowLayer<>(this, GLOW_MASK, GLOW_BONES));
        addRenderLayer(new LarvlingWeaponLayer(this));
        addRenderLayer(new KuvaFuryLayer<>(this));
    }

    @Override
    public void render(KuvaLarvlingEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        LarvlingOverhead.render(entity, poseStack, bufferSource, partialTick);
    }
}
