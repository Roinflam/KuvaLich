package pers.roinflam.kuvalich.mixin.tacz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 注入 EntityKineticBullet.applyShotgunDamageSpread(int)，
 * 修正多重射击导致的伤害均分问题，并叠加满弹夹第一发伤害加成。
 * <p>
 * 问题：TACZ 在 shootOnce 循环内对每颗子弹调用 applyShotgunDamageSpread(bulletAmount)，
 * 其中 bulletAmount 已被 MixinGunShootOnce 膨胀（如 10→19），
 * 导致 damageModifier = 1/19 而非 1/10，总伤害不变。
 * <p>
 * 修正：用 ThreadLocal 中保存的原始弹丸数替换 bulletCount 参数，
 * 使伤害仍按原始弹丸数均分，额外弹丸成为纯增量伤害。
 * <p>
 * 同时叠加膛室第一发加成：damageModifier = (1 / originalBulletCount) × (1 + firstBulletBonus)
 * 这是因为 applyShotgunDamageSpread 是覆写赋值（不是乘算），
 * 会丢弃构造器中 MixinTaczBulletExplosion 设置的乘算结果，
 * 所以需要在这里统一处理。
 */
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class MixinBulletDamageSpread {

    /**
     * 拦截 applyShotgunDamageSpread，用原始弹丸数重写 damageModifier，
     * 并叠加满弹夹第一发伤害加成。
     * <p>
     * TACZ 原逻辑：damageModifier = 1.0f / bulletCount
     * 修正后逻辑：damageModifier = (1.0f / originalBulletCount) × (1 + firstBulletBonus)
     * <p>
     * 例（多重射击）：原始 10 颗散弹，多重射击后 19 颗，无膛室
     *     修正后：每颗 1/10 → 总伤害 19/10 = 1.9（+90% 多重射击收益）
     * <p>
     * 例（多重射击+膛室）：原始 10 颗，多重后 19 颗，膛室 +1000%
     *     修正后：每颗 (1/10) × 11 = 1.1 → 总伤害 19 × 1.1 = 20.9
     * <p>
     * 例（散弹无多重但有膛室）：原始 8 颗，无多重，膛室 +1000%
     *     originalAmount == bulletCount → 走本方法
     *     每颗 (1/8) × 11 = 1.375 → 总伤害 8 × 1.375 = 11.0（×11 符合预期）
     *
     * @param bulletCount 传入的弹丸总数（可能是膨胀后的）
     * @param ci          CallbackInfo，用于取消原方法执行
     */
    @Inject(method = "applyShotgunDamageSpread", at = @At("HEAD"), cancellable = true, require = 0)
    private void overrideDamageSpread(int bulletCount, CallbackInfo ci) {
        int originalAmount = WarframeTaczBridge.getOriginalBulletAmount();
        float firstBulletBonus = WarframeTaczBridge.getFirstBulletDamageBonus();

        // 需要介入的条件：有多重射击修正（originalAmount != bulletCount）或有第一发加成
        boolean hasMultishotCorrection = originalAmount > 0 && originalAmount != bulletCount;
        boolean hasFirstBulletBonus = firstBulletBonus > 0f;

        if (!hasMultishotCorrection && !hasFirstBulletBonus) {
            // 无修正，走 TACZ 原逻辑
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

        ((EntityKineticBulletAccessor) this).setDamageModifier(modifier);

        LogUtil.debug(String.format(
                "[伤害修正] 传入弹丸数=%d, 原始弹丸数=%d, 第一发加成=%.0f%%, damageModifier=%.4f",
                bulletCount, effectiveBulletCount, firstBulletBonus * 100f, modifier
        ));

        ci.cancel();
    }
}