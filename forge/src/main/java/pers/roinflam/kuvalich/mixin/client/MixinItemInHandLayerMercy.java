package pers.roinflam.kuvalich.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.client.mercy.MercyPose;

/**
 * 处决时不画手里的物品。目标是原版类，写 Mojmap 名，不带 remap = false。
 *
 * <p>处决期间手腕上弹出的刀替代手里的东西，原来拿的武器整层不画。玩家用的 {@code PlayerItemInHandLayer}
 * 只重写了 {@code renderArmWithItem}，{@code render} 是这里的，注一处就够。{@code render} 同样有泛型桥接方法，
 * 描述符写全。</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayerMercy {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void kuvalich$hideDuringMercy(PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                          LivingEntity entity, float limbSwing, float limbSwingAmount,
                                          float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                          CallbackInfo ci) {
        if (MercyPose.hidesHeldItems(entity)) {
            ci.cancel();
        }
    }
}
