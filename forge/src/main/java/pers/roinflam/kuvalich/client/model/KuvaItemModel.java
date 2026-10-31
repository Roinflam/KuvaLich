package pers.roinflam.kuvalich.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.model.GeoModel;

/**
 * 赤毒（Kuva）物品GeckoLib模型
 * 用于渲染3D物品模型
 */
public class KuvaItemModel extends GeoModel<Kuva> {

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

    /**
     * 没进世界时（标题画面打开冒险指南）GeckoLib 给 Molang 查询赋值会直接引用 {@code mc.level} 而空指针。
     * 赤毒没有动画、不读任何 Molang 查询，没有世界时干脆不赋值，图标照样画得出来；进了世界照常走父类
     */
    @Override
    public void applyMolangQueries(Kuva animatable, double animTime) {
        if (Minecraft.getInstance().level != null) {
            super.applyMolangQueries(animatable, animTime);
        }
    }
}
