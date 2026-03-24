package pers.roinflam.kuvalich.client.model;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.model.GeoModel;

/**
 * 裂罅碎块（RivenSliver）物品GeckoLib模型
 * 带有idle浮动动画
 */
public class ModelRivenSliverItem extends GeoModel<RivenSliver> {

    @Override
    public ResourceLocation getModelResource(RivenSliver item) {
        return new ResourceLocation(Reference.MOD_ID, "geo/riven_sliver.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RivenSliver item) {
        // 贴图路径需要你自己准备对应的PNG文件
        return new ResourceLocation(Reference.MOD_ID, "textures/item/riven_sliver.png");
    }

    @Override
    public ResourceLocation getAnimationResource(RivenSliver item) {
        return new ResourceLocation(Reference.MOD_ID, "animations/riven_sliver.animation.json");
    }
}