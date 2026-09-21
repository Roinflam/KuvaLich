// MixinGunShootContext.java
package pers.roinflam.kuvalich.mixin.tacz;

import com.tacz.guns.api.item.IGun;
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
public abstract class MixinGunShootContext {

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ItemStack itemStack;

    /**
     * TACZ 自己的「这把枪最多装多少发」。
     *
     * <p>不要自己去调 {@code AttachmentDataUtils.getAmmoCountWithAttachment}：别的模组
     *（比如 AlbionMastery 的弹匣特化）是挂在<b>这个方法内部的调用点</b>上改容量的，
     * 绕过去算出来的是没算别人加成的数。满弹判定拿它当阈值，会从「只有第一发」
     * 变成「弹药量还很高时每一发都算第一发」，或者反过来永远判不到满弹。
     *
     * @return 这把枪当前的弹匣上限
     */
    @Shadow
    public abstract int getMaxAmmoCount();

    /**
     * shootOnce 方法头部：检测满弹夹射击 + 设置枪械伤害 + 设置玄骸强化乘数 ThreadLocal。
     */
    @Inject(method = "shootOnce", at = @At("HEAD"), require = 0)
    private void kuvalich$detectFirstBullet(boolean isAiming, CallbackInfo ci) {
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
                int maxAmmo = getMaxAmmoCount();

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
    private void kuvalich$clearShootFlags(boolean isAiming, CallbackInfo ci) {
        WarframeTaczBridge.clearFirstBulletDamageBonus();
        WarframeTaczBridge.clearGunDamageBonus();
        WarframeTaczBridge.clearGunEnhanceMultiplier();
    }
}