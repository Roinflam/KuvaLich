// MixinFirstBulletDetect.java
package pers.roinflam.kuvalich.mixin.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 ModernKineticGunScriptAPI.shootOnce，管理射击期间的 ThreadLocal 状态。
 * <p>
 * HEAD：检测满弹夹第一发 + 设置枪械伤害加成 + 设置玄骸强化乘数
 * RETURN：清除所有 ThreadLocal
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class MixinFirstBulletDetect {

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ItemStack itemStack;

    /**
     * shootOnce 方法头部：检测满弹夹射击 + 设置枪械伤害 + 设置玄骸强化乘数 ThreadLocal。
     */
    @Inject(method = "shootOnce", at = @At("HEAD"), require = 0)
    private void detectFirstBullet(boolean isAiming, CallbackInfo ci) {
        // 默认清除，确保每次射击重新判定
        WarframeTaczBridge.clearFirstBulletDamageBonus();
        WarframeTaczBridge.clearGunDamageBonus();
        WarframeTaczBridge.clearGunEnhanceMultiplier();

        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        // ========== 玄骸强化乘数（不依赖开光，仅检查NBT） ==========
        // 独立于模组系统，任何带有 kuvalich_gun_enhance_count 标记的TACZ枪均可生效
        double enhanceMultiplier = TaczGunEnhanceUtil.getDamageMultiplier(itemStack);
        if (enhanceMultiplier != 1.0) {
            WarframeTaczBridge.setGunEnhanceMultiplier(enhanceMultiplier);
        }

        // ========== 满弹夹第一发检测 ==========
        float firstBulletMod = WarframeTaczBridge.getFirstBulletDamageMod(itemStack, shooter);
        if (firstBulletMod > 0f) {
            IGun iGun = IGun.getIGunOrNull(itemStack);
            if (iGun != null) {
                int currentAmmo = iGun.getCurrentAmmoCount(itemStack);
                ResourceLocation gunId = iGun.getGunId(itemStack);
                int maxAmmo = TimelessAPI.getCommonGunIndex(gunId)
                        .map(index -> AttachmentDataUtils.getAmmoCountWithAttachment(itemStack, index.getGunData()))
                        .orElse(0);

                if (maxAmmo > 0 && currentAmmo >= maxAmmo) {
                    WarframeTaczBridge.setFirstBulletDamageBonus(firstBulletMod);
                }
            }
        }

        // ========== 枪械伤害加成（每次射击都生效） ==========
        float gunDamageMod = WarframeTaczBridge.getGunDamageMod(itemStack, shooter);
        if (gunDamageMod > 0f) {
            WarframeTaczBridge.setGunDamageBonus(gunDamageMod);
        }
    }

    /**
     * shootOnce 方法返回时清除所有 ThreadLocal，防止泄漏到后续射击。
     */
    @Inject(method = "shootOnce", at = @At("RETURN"), require = 0)
    private void clearShootFlags(boolean isAiming, CallbackInfo ci) {
        WarframeTaczBridge.clearFirstBulletDamageBonus();
        WarframeTaczBridge.clearGunDamageBonus();
        WarframeTaczBridge.clearGunEnhanceMultiplier();
    }
}