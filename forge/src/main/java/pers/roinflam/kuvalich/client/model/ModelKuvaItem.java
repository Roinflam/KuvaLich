package pers.roinflam.kuvalich.client.model;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.model.GeoModel;

/**
 * 赤毒（Kuva）物品GeckoLib模型
 * 用于渲染3D物品模型
 */
public class ModelKuvaItem extends GeoModel<Kuva> {

    @Override
    public ResourceLocation getModelResource(Kuva item) {
        return new ResourceLocation(Reference.MOD_ID, "geo/kuva.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(Kuva item) {
        // 贴图路径需要你自己准备对应的PNG文件
        return new ResourceLocation(Reference.MOD_ID, "textures/item/kuva.png");
    }

    @Override
    public ResourceLocation getAnimationResource(Kuva item) {
        // Kuva物品无动画，返回null即可
        return null;
    }
}