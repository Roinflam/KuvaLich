// 路径：forge/src/main/java/pers/roinflam/kuvalich/mixin/tacz/MixinGunReloadSpeed.java
package pers.roinflam.kuvalich.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 ModernKineticGunScriptAPI，实现 Warframe 装填速度模组。
 * <p>
 * 原理：TACZ 脚本通过 getReloadTime() 获取「装填已耗时间」，
 * 与枪械的 feed/cooldown 时间阈值比较来推进装填状态。
 * 膨胀返回值使脚本认为时间过得更快 → 装填更早完成。
 * <p>
 * reload_speed 语义：增量百分比（0.5 = +50% → 装填速度提升50%）
 * 公式：膨胀后耗时 = 实际耗时 × (1 + reload_speed)
 * <p>
 * 同时修改 getBoltTime()，使拉栓动作也受装填速度影响。
 * <p>
 * 注意：此方式通过加速逻辑判定实现快速装填，动画可能被提前截断而非等比加速。
 * 若后续需要动画同步加速，需额外注入 TACZ 动画系统。
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class MixinGunReloadSpeed {

    /**
     * 射击者实体引用（用于读取击杀叠加等玩家相关属性）
     */
    @Shadow
    private LivingEntity shooter;

    /**
     * 枪械 ItemStack（用于读取 KuvaLich 武器模组属性）
     */
    @Shadow
    private ItemStack itemStack;

    /**
     * 修改 getReloadTime() 返回值，使装填逻辑加速。
     * <p>
     * TACZ 脚本典型逻辑：if (getReloadTime() >= feedTime * 1000) → 装填完成
     * 膨胀返回值后，条件更早满足 → 装填更快完成。
     * <p>
     * 例：reload_speed = 0.5（+50%），实际耗时 1000ms → 返回 1500ms
     *     脚本认为已过 1500ms，提前进入下一装填阶段。
     *
     * @param original 原始已耗时间（毫秒）
     * @return 膨胀后的已耗时间
     */
    @ModifyReturnValue(method = "getReloadTime", at = @At("RETURN"), require = 0)
    private long applyWarframeReloadSpeed(long original) {
        if (itemStack == null || itemStack.isEmpty()) {
            return original;
        }

        float reloadSpeedMod = WarframeTaczBridge.getReloadSpeedMod(itemStack, shooter);

        // 无修正时直接返回
        if (Math.abs(reloadSpeedMod) < 0.001f) {
            return original;
        }

        // reload_speed <= -1.0：完全无法装填，返回 0（时间永远不够）
        if (reloadSpeedMod <= -1.0f) {
            return 0L;
        }

        // 膨胀已耗时间：elapsed × (1 + reloadSpeed)
        // +50% → 乘以 1.5 → 时间加速 → 装填更快
        // -30% → 乘以 0.7 → 时间减速 → 装填更慢
        return (long) (original * (1f + reloadSpeedMod));
    }

    /**
     * 修改 getBoltTime() 返回值，使拉栓动作也受装填速度模组影响。
     * <p>
     * 逻辑与 getReloadTime 完全一致。
     *
     * @param original 原始拉栓已耗时间（毫秒）
     * @return 膨胀后的拉栓已耗时间
     */
    @ModifyReturnValue(method = "getBoltTime", at = @At("RETURN"), require = 0)
    private long applyWarframeBoltSpeed(long original) {
        if (itemStack == null || itemStack.isEmpty()) {
            return original;
        }

        float reloadSpeedMod = WarframeTaczBridge.getReloadSpeedMod(itemStack, shooter);

        if (Math.abs(reloadSpeedMod) < 0.001f) {
            return original;
        }

        if (reloadSpeedMod <= -1.0f) {
            return 0L;
        }

        return (long) (original * (1f + reloadSpeedMod));
    }
}