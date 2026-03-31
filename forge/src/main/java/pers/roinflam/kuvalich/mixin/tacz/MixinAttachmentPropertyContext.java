package pers.roinflam.kuvalich.mixin.tacz;

import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 AttachmentPropertyManager.postChangeEvent，保存 shooter 和 gunItem 到 ThreadLocal。
 * Inject into AttachmentPropertyManager.postChangeEvent to save context.
 * <p>
 * postChangeEvent 触发 AttachmentPropertyEvent，事件处理器中需要 shooter 和 gunItem
 * 来读取 KuvaLich 模组属性（aim_time、accuracy 等）。
 * <p>
 * 执行顺序：本 Mixin（HEAD）保存上下文 → postChangeEvent 发送事件
 * → TaczCompatEventHandler.onAttachmentPropertyEvent 读取上下文并修改缓存
 */
@Mixin(targets = "com.tacz.guns.resource.modifier.AttachmentPropertyManager", remap = false)
public class MixinAttachmentPropertyContext {

    /**
     * 在 postChangeEvent 方法头部保存 shooter 和 gunItem 到 ThreadLocal。
     *
     * @param shooter 持枪实体
     * @param gunItem 枪械 ItemStack
     * @param ci      CallbackInfo
     */
    @Inject(method = "postChangeEvent", at = @At("HEAD"), require = 0)
    private static void saveShooterContext(LivingEntity shooter, ItemStack gunItem, CallbackInfo ci) {
        WarframeTaczBridge.setCacheContextShooter(shooter);
        WarframeTaczBridge.setCacheContextGunItem(gunItem);
    }
}