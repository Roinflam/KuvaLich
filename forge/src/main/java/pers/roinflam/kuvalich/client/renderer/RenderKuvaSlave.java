package pers.roinflam.kuvalich.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import pers.roinflam.kuvalich.client.model.ModelKuvaSlave;
import pers.roinflam.kuvalich.entity.EntityKuvaSlave;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * 赤毒奴仆渲染器（GeckoLib 4.x版本）
 * Kuva Slave Renderer (GeckoLib 4.x version)
 */
public class RenderKuvaSlave extends GeoEntityRenderer<EntityKuvaSlave> {

    /**
     * GeckoLib 4.x构造函数
     * GeckoLib 4.x constructor
     *
     * 注意：不再接收RenderManager，改为EntityRendererProvider.Context
     * Note: No longer accepts RenderManager, uses EntityRendererProvider.Context instead
     */
    public RenderKuvaSlave(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ModelKuvaSlave());
    }

    /**
     * 可选：自定义渲染（如果需要特殊效果）
     * Optional: Custom rendering (if special effects needed)
     */
    @Override
    public void render(EntityKuvaSlave entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        // 可以在这里添加自定义渲染逻辑
        // Custom rendering logic can be added here

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}