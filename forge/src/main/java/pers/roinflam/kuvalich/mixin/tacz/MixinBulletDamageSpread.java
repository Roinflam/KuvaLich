// MixinBulletDamageSpread.java
package pers.roinflam.kuvalich.mixin.tacz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 注入 EntityKineticBullet.applyShotgunDamageSpread(int)，
 * 修正多重射击导致的伤害均分问题，并叠加满弹夹第一发伤害加成、枪械伤害加成和玄骸强化乘数。
 * <p>
 * 修正后逻辑：
 *   damageModifier = (1 / originalBulletCount)
 *                  × (1 + firstBulletBonus)
 *                  × (1 + gunDamageBonus)
 *                  × gunEnhanceMultiplier
 */
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class MixinBulletDamageSpread {

    /**
     * 拦截 applyShotgunDamageSpread，用原始弹丸数重写 damageModifier，
     * 并叠加满弹夹第一发伤害加成、枪械伤害加成和玄骸强化乘数。
     *
     * @param bulletCount 传入的弹丸总数（可能是膨胀后的）
     * @param ci          CallbackInfo，用于取消原方法执行
     */
    @Inject(method = "applyShotgunDamageSpread", at = @At("HEAD"), cancellable = true, require = 0)
    private void overrideDamageSpread(int bulletCount, CallbackInfo ci) {
        int originalAmount = WarframeTaczBridge.getOriginalBulletAmount();
        float firstBulletBonus = WarframeTaczBridge.getFirstBulletDamageBonus();
        float gunDamageBonus = WarframeTaczBridge.getGunDamageBonus();
        double enhanceMultiplier = WarframeTaczBridge.getGunEnhanceMultiplier();

        // 需要介入的条件：有多重射击修正、有第一发加成、有枪械伤害加成、或有玄骸强化
        boolean hasMultishotCorrection = originalAmount > 0 && originalAmount != bulletCount;
        boolean hasFirstBulletBonus = firstBulletBonus > 0f;
        boolean hasGunDamageBonus = gunDamageBonus > 0f;
        boolean hasEnhanceBonus = enhanceMultiplier != 1.0;

        if (!hasMultishotCorrection && !hasFirstBulletBonus && !hasGunDamageBonus && !hasEnhanceBonus) {
            // 无任何修正，走 TACZ 原逻辑
            return;
        }

        // 确定用于均分伤害的弹丸数（优先使用原始值，否则用传入值）
        int effectiveBulletCount = (originalAmount > 0) ? originalAmount : bulletCount;

        // 计算基础 damageModifier
        float modifier;
        if (effectiveBulletCount > 1) {
            modifier = 1.0f / (float) effectiveBulletCount;
        } else {
            modifier = 1.0f;
        }

        // 叠加第一发加成
        if (hasFirstBulletBonus) {
            modifier *= (1f + firstBulletBonus);
        }

        // 叠加枪械伤害加成（独立乘区）
        if (hasGunDamageBonus) {
            modifier *= (1f + gunDamageBonus);
        }

        // 叠加玄骸强化乘数（独立乘区）
        if (hasEnhanceBonus) {
            modifier *= (float) enhanceMultiplier;
        }

        ((EntityKineticBulletAccessor) this).setDamageModifier(modifier);

        LogUtil.debug(String.format(
                "[伤害修正] 传入弹丸数=%d, 原始弹丸数=%d, 第一发加成=%.0f%%, 枪械伤害=%.0f%%, 玄骸强化=%.2fx, damageModifier=%.4f",
                bulletCount, effectiveBulletCount, firstBulletBonus * 100f, gunDamageBonus * 100f, enhanceMultiplier, modifier
        ));

        ci.cancel();
    }
}