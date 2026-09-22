package pers.roinflam.kuvalich.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import pers.roinflam.kuvalich.client.model.KuvaMasterModel;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 赤毒玄骸渲染器（GeckoLib 4.x版本）
 * Kuva Master Renderer (GeckoLib 4.x version)
 */
public class KuvaMasterRenderer extends GeoEntityRenderer<KuvaMasterEntity> {

    public KuvaMasterRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new KuvaMasterModel());
    }

    @Override
    public void render(KuvaMasterEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 可以在这里添加发光效果、粒子效果等
        // Can add glow effects, particle effects, etc. here

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}