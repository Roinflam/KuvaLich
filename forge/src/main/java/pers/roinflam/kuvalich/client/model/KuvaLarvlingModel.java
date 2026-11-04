package pers.roinflam.kuvalich.client.model;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.mercy.MercyAnimNames;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * 赤毒幼体模型：几何、贴图是自己的，骨架和奴仆同一套（骨骼名一样），动作直接用奴仆的
 *
 * <p>走、跑、待机、出手取 {@code kuva_slave.animation.json}；倒地、处决取 {@code kuva_mercy.animation.json} 里
 * {@code mercy.} 开头的片段，走的是奴仆那几套（幼体在处决流程里就是奴仆）。</p>
 */
public class KuvaLarvlingModel extends GeoModel<KuvaLarvlingEntity> {

    private static final ResourceLocation MODEL = new ResourceLocation(Reference.MOD_ID, "geo/kuva_larvling.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID,
            "textures/entity/kuva_larvling.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(Reference.MOD_ID,
            "animations/kuva_slave.animation.json");

    @Override
    public ResourceLocation getModelResource(KuvaLarvlingEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(KuvaLarvlingEntity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(KuvaLarvlingEntity entity) {
        return ANIMATION;
    }

    @Override
    @Nullable
    public Animation getAnimation(KuvaLarvlingEntity animatable, String name) {
        if (name.startsWith(MercyAnimNames.PREFIX)) {
            return KuvaMasterModel.mercyAnimation(name);
        }
        return super.getAnimation(animatable, name);
    }

    @Override
    public void setCustomAnimations(KuvaLarvlingEntity animatable, long instanceId, AnimationState<KuvaLarvlingEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        KuvaPoseLayer.apply(this, animatable, state, false);
    }
}
