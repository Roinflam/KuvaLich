package pers.roinflam.kuvalich.client.model;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.entity.EntityKuvaSlave;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.model.GeoModel;

/**
 * 赤毒奴仆模型（GeckoLib 4.x版本）
 * Kuva Slave Model (GeckoLib 4.x version)
 */
public class ModelKuvaSlave extends GeoModel<EntityKuvaSlave> {

    @Override
    public ResourceLocation getModelResource(EntityKuvaSlave entity) {
        return new ResourceLocation(Reference.MOD_ID, "geo/kuva_slave.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EntityKuvaSlave entity) {
        return new ResourceLocation(Reference.MOD_ID, "textures/entity/kuva_slave.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EntityKuvaSlave entity) {
        return new ResourceLocation(Reference.MOD_ID, "animations/kuva.animation.json");
    }
}