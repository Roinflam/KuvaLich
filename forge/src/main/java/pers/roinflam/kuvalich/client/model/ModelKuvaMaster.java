package pers.roinflam.kuvalich.client.model;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.entity.EntityKuvaMaster;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.model.GeoModel;

/**
 * 赤毒玄骸模型（GeckoLib 4.x版本）
 * Kuva Master Model (GeckoLib 4.x version)
 */
public class ModelKuvaMaster extends GeoModel<EntityKuvaMaster> {

    @Override
    public ResourceLocation getModelResource(EntityKuvaMaster entity) {
        return new ResourceLocation(Reference.MOD_ID, "geo/kuva_master.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EntityKuvaMaster entity) {
        return new ResourceLocation(Reference.MOD_ID, "textures/entity/kuva_master.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EntityKuvaMaster entity) {
        return new ResourceLocation(Reference.MOD_ID, "animations/kuva.animation.json");
    }
}