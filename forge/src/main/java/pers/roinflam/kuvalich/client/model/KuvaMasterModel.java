package pers.roinflam.kuvalich.client.model;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.lich.LichVoice;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.mercy.MercyAnimNames;
import pers.roinflam.kuvalich.mercy.MercyAnimSelector;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.model.GeoModel;

/**
 * 赤毒玄骸模型（GeckoLib 4.x版本）
 * Kuva Master Model (GeckoLib 4.x version)
 *
 * <p>倒地 / 处决的片段单独放在 {@code kuva_mercy.animation.json}，名字以 {@code mercy.} 开头的从那里取，
 * 其余照旧读共用的 {@code kuva.animation.json}。</p>
 */
public class KuvaMasterModel extends GeoModel<KuvaMasterEntity> {

    /** 倒地、处决用的动画文件，玄骸和奴仆共用（骨骼名不同，互不干扰） */
    public static final ResourceLocation MERCY_ANIMATIONS =
            new ResourceLocation(Reference.MOD_ID, "animations/kuva_mercy.animation.json");

    private static final String TALK = "talk";
    private static final String TALK_SHOUT = "talk_shout";
    /** 说话控制器的名字（实体里注册） */
    private static final String TALK_CONTROLLER = "talk";
    private static final RawAnimation MUTE = RawAnimation.begin().thenLoop(MercyAnimSelector.LICH_MUTE);

    /** 每个音节抬一下下巴：头盔跟着头再多抬一点（度，动画文件里 x 负 = 抬头） */
    private static final float NOD_HEAD_DEG = -4.0F;
    private static final float NOD_HELMET_DEG = -1.5F;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);

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

    @Override
    @Nullable
    public Animation getAnimation(KuvaMasterEntity animatable, String name) {
        if (name.startsWith(MercyAnimNames.PREFIX)) {
            return mercyAnimation(name);
        }
        // 说话控制器注册在处决控制器后面，同一根骨骼它说了算：倒地 / 处决期间换成空片段，
        // 头和身子留给处决姿势，说话只剩下面按音节的点头
        if (animatable.getMercyState() != MercyState.NONE && (TALK.equals(name) || TALK_SHOUT.equals(name))) {
            return mercyAnimation(MercyAnimSelector.LICH_MUTE);
        }
        return super.getAnimation(animatable, name);
    }

    /** 从处决动画文件里取一段；资源没加载到时返回 {@code null}，GeckoLib 会当成没有这段 */
    @Nullable
    static Animation mercyAnimation(String name) {
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(MERCY_ANIMATIONS);
        return baked == null ? null : baked.getAnimation(name);
    }

    @Override
    public void handleAnimations(KuvaMasterEntity animatable, long instanceId, AnimationState<KuvaMasterEntity> state) {
        if (animatable.getMercyState() != MercyState.NONE) {
            muteTalk(animatable, instanceId);
        }
        super.handleAnimations(animatable, instanceId, state);
    }

    /**
     * 倒地前就开始说的那句话，说话片段已经载进控制器、不会再问 {@link #getAnimation}，这里当场换成空片段。
     * 不在说话（控制器停着）的不碰，下次开口时 {@link #getAnimation} 自己会换
     */
    private static void muteTalk(AbstractKuva entity, long instanceId) {
        AnimationController<?> talk = entity.getAnimatableInstanceCache().getManagerForId(instanceId)
                .getAnimationControllers().get(TALK_CONTROLLER);
        if (talk == null || talk.getAnimationState() == AnimationController.State.STOPPED) {
            return;
        }
        RawAnimation current = talk.getCurrentRawAnimation();
        if (current != null && current != MUTE) {
            talk.setAnimation(MUTE);
        }
    }

    @Override
    public void setCustomAnimations(KuvaMasterEntity animatable, long instanceId, AnimationState<KuvaMasterEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        float envelope = LichVoice.envelope(animatable, KuvaFxDirector.clockMs());
        if (envelope <= 0.0F) {
            return;
        }
        // GeckoLib 存进骨骼的 x 旋转和动画文件里的符号相反，所以文件里的「负 = 抬头」在这里是加正值。
        // 在关键帧算完以后叠加：说话片段只给底子，和声音对齐靠这一下
        nod(getAnimationProcessor().getBone("Head"), NOD_HEAD_DEG * envelope);
        nod(getAnimationProcessor().getBone("helmet"), NOD_HELMET_DEG * envelope);
    }

    private static void nod(@Nullable CoreGeoBone bone, float fileDeg) {
        if (bone != null) {
            bone.setRotX(bone.getRotX() - fileDeg * DEG_TO_RAD);
        }
    }
}
