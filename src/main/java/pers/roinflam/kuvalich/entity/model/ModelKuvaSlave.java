package pers.roinflam.kuvalich.entity.model;// Made with Blockbench 4.0.1
// Exported for Minecraft version 1.7 - 1.12
// Paste this class into your mod and generate all required imports


import net.minecraft.util.ResourceLocation;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib3.model.AnimatedGeoModel;

import javax.annotation.Nonnull;

public class ModelKuvaSlave extends AnimatedGeoModel {

    @Nonnull
    @Override
    public ResourceLocation getAnimationFileLocation(Object entity) {
        return new ResourceLocation(Reference.MOD_ID, "animations/kuva.json");
    }

    @Nonnull
    @Override
    public ResourceLocation getModelLocation(Object entity) {
        return new ResourceLocation(Reference.MOD_ID, "geo/kuva_slave.geo.json");
    }

    @Nonnull
    @Override
    public ResourceLocation getTextureLocation(Object entity) {
        return new ResourceLocation(Reference.MOD_ID, "textures/entity/kuva_slave.png");
    }

}