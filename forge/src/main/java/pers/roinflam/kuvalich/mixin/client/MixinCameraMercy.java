package pers.roinflam.kuvalich.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.client.mercy.MercyCamera;

/**
 * 处决时接管相机。目标是原版类，写 Mojmap 名，不带 remap = false。
 *
 * <p>在 {@code Camera#setup} 末尾把位置和朝向整个换成处决机位：原版第三人称那段（往后拉、防穿墙）已经跑完，
 * 这里直接覆盖，渲染用的视图矩阵、视锥剔除、声音监听位置都跟着走。只换主相机，别的模组自建的相机不碰。
 * {@code setPosition} 有 (double, double, double) 和 (Vec3) 两个重载，这里用前一个，描述符写全。</p>
 */
@Mixin(Camera.class)
public abstract class MixinCameraMercy {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL"))
    private void kuvalich$mercyShot(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
                                    float partialTick, CallbackInfo ci) {
        if (MercyCamera.override((Camera) (Object) this)) {
            this.setRotation(MercyCamera.yaw(), MercyCamera.pitch());
            this.setPosition(MercyCamera.x(), MercyCamera.y(), MercyCamera.z());
        }
    }
}
