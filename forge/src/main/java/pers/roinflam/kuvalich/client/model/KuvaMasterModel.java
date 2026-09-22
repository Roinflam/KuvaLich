package pers.roinflam.kuvalich.client.model;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.model.GeoModel;

/**
 * 赤毒玄骸模型（GeckoLib 4.x版本）
 * Kuva Master Model (GeckoLib 4.x version)
 */
public class KuvaMasterModel extends GeoModel<KuvaMasterEntity> {

    @Override
    public ResourceLocation getModelResource(KuvaMasterEntity entity) {
        return new ResourceLocation(Reference.MOD_ID, "geo/kuva_master.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(KuvaMasterEntity entity) {
        return new ResourceLocation(Reference.MOD_ID, "textures/entity/kuva_master.png");
    }

    @Override
    public ResourceLocation getAnimationResource(KuvaMasterEntity entity) {
        return new ResourceLocation(Reference.MOD_ID, "animations/kuva.animation.json");
    }
}