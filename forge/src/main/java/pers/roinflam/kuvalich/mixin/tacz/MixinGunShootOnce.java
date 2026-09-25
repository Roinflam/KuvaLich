// MixinGunShootOnce.java
package pers.roinflam.kuvalich.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 注入 ModernKineticGunScriptAPI.shootOnce(boolean)，实现 Warframe 多重射击。
 * <p>
 * 实现方式：修改 shootOnce 内部的 bulletAmount 变量（Math.max(II)I 处），
 * 在 TACZ 原有子弹数量基础上叠加 Warframe 多重射击额外子弹数。
 * <p>
 * 不使用递归调用 shootOnce，避免 CycleTaskHelper 异步时序问题。
 */
@Mixin(targets = "com.tacz.guns.item.ModernKineticGunScriptAPI", remap = false)
public class MixinGunShootOnce {

    @Shadow
    private LivingEntity shooter;

    @Shadow
    private ItemStack itemStack;

    /**
     * 本次扣扳机时 TACZ 的原始弹丸数（多重射击膨胀前）
     *
     * <p>多连发的后几发在之后的 tick 里执行（见 {@code MixinGunShootContext} 的说明），
     * 那时 ThreadLocal 里的原始弹丸数可能已被同一 tick 里别的射手覆盖 —— 例如步枪的第二发读到霰弹枪的 8，
     * {@code MixinBulletDamageSpread} 就会把伤害按 8 颗均分。所以记在这个实例上，每一发开火前恢复进 ThreadLocal。</p>
     */
    @Unique
    private int kuvalich$originalAmount;

    /**
     * 修改 shootOnce 内部 bulletAmount 的初始值（Math.max(bulletData.getBulletAmount(), 1) 处）。
     * <p>
     * Warframe 多重射击公式（按原始弹丸数缩放）：
     *   scaledMultishot = multishotMod × originalAmount
     *   整数部分：必定额外发射的子弹数
     *   小数部分：概率触发额外一发
     * <p>
     * 例：散弹枪 8 颗 + 100% multishot → 额外 8 颗 → 共 16 颗
     *     散弹枪 8 颗 + 50%  multishot → 额外 4 颗 → 共 12 颗
     *     单发枪 1 颗 + 50%  multishot → 50% 概率额外 1 颗 → 共 1~2 颗
     * <p>
     * 同时将原始弹丸数存入 ThreadLocal，供 MixinBulletDamageSpread 使用，
     * 确保 applyShotgunDamageSpread 按原始弹丸数均分伤害，而非膨胀后的总数。
     *
     * @param originalAmount TACZ 原始 bulletAmount（已经过 Math.max 计算）
     * @return 叠加 Warframe 多重射击后的 bulletAmount
     */
    @ModifyExpressionValue(
            method = "shootOnce",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 0),
            require = 0
    )
    private int kuvalich$applyMultishot(int originalAmount) {
        this.kuvalich$originalAmount = originalAmount;
        if (itemStack == null || itemStack.isEmpty()) {
            // 无多重射击时也要存原始值，保证 ThreadLocal 状态一致
            WarframeTaczBridge.setOriginalBulletAmount(originalAmount);
            return originalAmount;
        }

        // 先存原始弹丸数，供 MixinBulletDamageSpread 读取
        WarframeTaczBridge.setOriginalBulletAmount(originalAmount);

        float multishotMod = WarframeTaczBridge.getMultishotMod(itemStack, shooter);

        // 负值多重射击由 TaczCompatEventHandler 的 GunFireEvent 处理
        if (multishotMod <= 0f) {
            return originalAmount;
        }

        // 将 multishotMod 乘以原始弹丸数，使散弹枪按比例翻倍
        // 例：8 颗散弹 × 1.0 multishot → scaledMultishot = 8.0 → 额外 8 颗 → 共 16 颗
        float scaledMultishot = multishotMod * originalAmount;

        // 整数部分：必定额外发射的子弹数
        int extraBullets = (int) scaledMultishot;

        // 小数部分：概率触发额外一发
        float extraChance = scaledMultishot - extraBullets;
        if (extraChance > 0f && Math.random() < extraChance) {
            extraBullets++;
        }

        int finalAmount = originalAmount + extraBullets;

        return finalAmount;
    }

    /**
     * 每一发开火前恢复本次扣扳机的原始弹丸数（多连发的后几发也用自己这把枪的值）
     *
     * <p>method 写完整描述符的原因见 {@code MixinGunShootContext} 类注释：只写名字的话，TACZ 改版后
     * lambda 编号错位会让整个 mixin 应用失败（连多重射击也没了），或者注入落到热量 lambda 上提前清值。</p>
     */
    @Inject(method = "lambda$shootOnce$2(ZLcom/tacz/guns/resource/pojo/data/gun/GunData;ILcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFIZ)Z", at = @At("HEAD"), require = 0)
    private void kuvalich$restoreOriginalAmount(CallbackInfoReturnable<Boolean> cir) {
        if (this.kuvalich$originalAmount > 0) {
            WarframeTaczBridge.setOriginalBulletAmount(this.kuvalich$originalAmount);
        }
    }

    /**
     * 这一发打完清掉，不让它留给同一 tick 里的别的射手
     */
    @Inject(method = "lambda$shootOnce$2(ZLcom/tacz/guns/resource/pojo/data/gun/GunData;ILcom/tacz/guns/resource/pojo/data/gun/BulletData;Lcom/tacz/guns/api/entity/IGunOperator;FFIZ)Z", at = @At("RETURN"), require = 0)
    private void kuvalich$clearOriginalAmount(CallbackInfoReturnable<Boolean> cir) {
        WarframeTaczBridge.clearOriginalBulletAmount();
    }
}