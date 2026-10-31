package pers.roinflam.kuvalich.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.model.GeoModel;

/**
 * 裂罅碎块（RivenSliver）物品GeckoLib模型
 * 带有idle浮动动画
 */
public class RivenSliverItemModel extends GeoModel<RivenSliver> {

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

    /**
     * 没进世界时（标题画面打开冒险指南）GeckoLib 给 Molang 查询赋值会直接引用 {@code mc.level} 而空指针。
     * 这个物品的动画不读任何 Molang 查询，没有世界时干脆不赋值，图标照样画得出来；进了世界照常走父类
     */
    @Override
    public void applyMolangQueries(RivenSliver animatable, double animTime) {
        if (Minecraft.getInstance().level != null) {
            super.applyMolangQueries(animatable, animTime);
        }
    }
}
