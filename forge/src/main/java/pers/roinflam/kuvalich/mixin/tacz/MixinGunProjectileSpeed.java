// 路径：forge/src/main/java/pers/roinflam/kuvalich/mixin/tacz/MixinGunProjectileSpeed.java
package pers.roinflam.kuvalich.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 ModernKineticGunScriptAPI.shootOnce，实现 Warframe 投射物速度模组。
 * <p>
 * TACZ 子弹速度计算链路（shootOnce 内部）：
 *   1. speed = cacheProperty.getCache(AMMO_SPEED)        // 配件修正后的速度
 *   2. speed *= GLOBAL_BULLET_SPEED_MODIFIER              // 全局倍率
 *   3. processedSpeed = Mth.clamp(speed / 20.0F, 0, MAX)  // 转换为 tick 内移动距离
 * <p>
 * 注入点：修改 Mth.clamp(FFF)F 的返回值（processedSpeed），
 * 在 TACZ 所有速度修正完成后叠加 KuvaLich 投射物速度模组。
 * <p>
 * projectile_speed 语义：增量百分比（0.5 = +50% → 子弹飞行速度提升50%）
 * 公式：最终速度 = processedSpeed × (1 + projectile_speed)
 * <p>
 * 附带效果：速度提升 → 同一 lifetime 内子弹飞行更远 → 有效射程自然增加。
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class MixinGunProjectileSpeed {

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ItemStack itemStack;

    /**
     * 修改 shootOnce 内部 processedSpeed 的计算结果（Mth.clamp 处）。
     * <p>
     * shootOnce 内只有一处 Mth.clamp(FFF)F 调用，即 processedSpeed 的计算，
     * 因此 ordinal = 0 可以精确定位。
     *
     * @param processedSpeed TACZ 计算完毕的子弹速度（blocks/tick）
     * @return 叠加 KuvaLich 投射物速度模组后的最终速度
     */
    @ModifyExpressionValue(
            method = "shootOnce",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", ordinal = 0),
            require = 0
    )
    private float applyWarframeProjectileSpeed(float processedSpeed) {
        if (itemStack == null || itemStack.isEmpty()) {
            return processedSpeed;
        }

        float projectileSpeedMod = WarframeTaczBridge.getProjectileSpeedMod(itemStack, shooter);

        // 无修正时直接返回
        if (Math.abs(projectileSpeedMod) < 0.001f) {
            return processedSpeed;
        }

        // projectile_speed <= -1.0：子弹无法飞出，速度归零
        if (projectileSpeedMod <= -1.0f) {
            return 0f;
        }

        // 最终速度 = processedSpeed × (1 + projectileSpeedMod)
        // +50% → 乘以 1.5 → 子弹更快、射程更远
        // -30% → 乘以 0.7 → 子弹更慢、射程更短
        return processedSpeed * (1f + projectileSpeedMod);
    }
}