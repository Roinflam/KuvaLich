package pers.roinflam.kuvalich.client.model;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.mercy.MercyAnimNames;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * 赤毒奴仆模型（GeckoLib 4.x版本）
 * Kuva Slave Model (GeckoLib 4.x version)
 *
 * <p>倒地 / 处决的片段和玄骸的放在同一个 {@code kuva_mercy.animation.json} 里，名字以 {@code mercy.} 开头的从那里取。
 * 走、跑、待机、起势、出手用自己的 {@code kuva_slave.animation.json}（片段名和玄骸的一样，动作轻、快、发抖）。</p>
 */
public class KuvaSlaveModel extends GeoModel<KuvaSlaveEntity> {

    private final KuvaPoseLayer.Additive poseAdded = new KuvaPoseLayer.Additive();

    @Override
    public ResourceLocation getModelResource(KuvaSlaveEntity entity) {
        return new ResourceLocation(Reference.MOD_ID, "geo/kuva_slave.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(KuvaSlaveEntity entity) {
        return new ResourceLocation(Reference.MOD_ID, "textures/entity/kuva_slave.png");
    }

    @Override
    public ResourceLocation getAnimationResource(KuvaSlaveEntity entity) {
        return new ResourceLocation(Reference.MOD_ID, "animations/kuva_slave.animation.json");
    }

    @Override
    @Nullable
    public Animation getAnimation(KuvaSlaveEntity animatable, String name) {
        if (name.startsWith(MercyAnimNames.PREFIX)) {
            return KuvaMasterModel.mercyAnimation(name);
        }
        return super.getAnimation(animatable, name);
    }

    @Override
    public void setCustomAnimations(KuvaSlaveEntity animatable, long instanceId, AnimationState<KuvaSlaveEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        KuvaPoseLayer.apply(this, animatable, state, false, this.poseAdded);
    }
}
