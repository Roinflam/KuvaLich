package pers.roinflam.kuvalich.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.client.mercy.MercyPose;

/**
 * 处决时玩家模型的姿态。目标是原版类，写 Mojmap 名，不带 remap = false。
 *
 * <p>注在 {@code setupAnim} 末尾，原版和别的模组摆完以后再覆盖。泛型擦除后编译出两个 {@code setupAnim}
 * （{@code LivingEntity} 版和桥接的 {@code Entity} 版），描述符写全只注真正的那个。玩家模型在调完父类以后
 * 才复制袖子、裤腿、外套，所以皮肤外层会跟着这里的改动走。</p>
 */
@Mixin(HumanoidModel.class)
public abstract class MixinHumanoidModelMercy {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void kuvalich$mercyPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                    float netHeadYaw, float headPitch, CallbackInfo ci) {
        MercyPose.apply((HumanoidModel<?>) (Object) this, entity);
    }
}
