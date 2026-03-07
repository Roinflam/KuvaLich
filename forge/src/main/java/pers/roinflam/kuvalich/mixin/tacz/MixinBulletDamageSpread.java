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
 * 修正多重射击导致的伤害均分问题。
 * <p>
 * 问题：TACZ 在 shootOnce 循环内对每颗子弹调用 applyShotgunDamageSpread(bulletAmount)，
 * 其中 bulletAmount 已被 MixinGunShootOnce 膨胀（如 10→19），
 * 导致 damageModifier = 1/19 而非 1/10，总伤害不变。
 * <p>
 * 修正：用 ThreadLocal 中保存的原始弹丸数替换 bulletCount 参数，
 * 使伤害仍按原始弹丸数均分，额外弹丸成为纯增量伤害。
 */
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class MixinBulletDamageSpread {

    /**
     * 拦截 applyShotgunDamageSpread，用原始弹丸数重写 damageModifier。
     * <p>
     * TACZ 原逻辑：damageModifier = 1.0f / bulletCount
     * 修正后逻辑：damageModifier = 1.0f / originalBulletCount
     * <p>
     * 例：原始 10 颗散弹，多重射击后 19 颗
     *     TACZ 原本：每颗 1/19 → 总伤害 19/19 = 1.0（不变）
     *     修正后：每颗 1/10 → 总伤害 19/10 = 1.9（+90% 多重射击收益）
     *
     * @param bulletCount 膨胀后的弹丸总数（由 shootOnce 传入）
     * @param ci          CallbackInfo，用于取消原方法执行
     */
    @Inject(method = "applyShotgunDamageSpread", at = @At("HEAD"), cancellable = true, require = 0)
    private void overrideDamageSpread(int bulletCount, CallbackInfo ci) {
        int originalAmount = WarframeTaczBridge.getOriginalBulletAmount();

        // 仅当 ThreadLocal 中有有效的原始弹丸数且与传入值不同时才修正
        // 如果相同（无多重射击），走 TACZ 原逻辑即可
        if (originalAmount > 0 && originalAmount != bulletCount) {
            // 按原始弹丸数均分伤害，而非膨胀后的总数
            if (originalAmount > 1) {
                // 通过 Mixin 的 Shadow 或反射访问 damageModifier 不方便，
                // 但 applyShotgunDamageSpread 本身非常简单，直接取消原方法并手动设置即可
                ((EntityKineticBulletAccessor) this).setDamageModifier(1.0f / (float) originalAmount);
            }
            // originalAmount == 1 时 damageModifier 保持默认 1.0f，无需修改

            LogUtil.debug(String.format(
                    "[多重射击伤害修正] 传入弹丸数=%d, 原始弹丸数=%d, damageModifier=%.4f",
                    bulletCount, originalAmount, 1.0f / (float) Math.max(originalAmount, 1)
            ));

            ci.cancel();
        }
    }
}