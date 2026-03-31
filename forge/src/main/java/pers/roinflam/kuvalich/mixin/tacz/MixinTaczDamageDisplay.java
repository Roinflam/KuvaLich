package pers.roinflam.kuvalich.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;

/**
 * Mixin: TACZ枪械伤害显示增强
 * <p>目标方法：{@code AttachmentDataUtils.getDamageWithAttachment(ItemStack, GunData)}
 * <br>作用：在TACZ Tooltip计算伤害后，乘以玄骸强化倍率，使Tooltip直接显示增强后的伤害值</p>
 *
 * <p>此方法是TACZ枪械Tooltip获取伤害的唯一入口，修改返回值即可影响所有Tooltip显示。
 * <br>战斗伤害走的是独立的 AttachmentCacheProperty 缓存链路，由 TaczCompatEventHandler 处理。</p>
 */
@Mixin(value = com.tacz.guns.util.AttachmentDataUtils.class, remap = false)
public abstract class MixinTaczDamageDisplay {

    /**
     * 在getDamageWithAttachment返回值上乘以枪械强化倍率
     *
     * @param original 原始返回值（TACZ计算的含配件伤害）
     * @param gun      枪械物品堆（原方法参数）
     * @param gunData  枪械数据（原方法参数）
     * @return 乘以强化倍率后的伤害值
     */
    @ModifyReturnValue(method = "getDamageWithAttachment", at = @At("RETURN"), require = 0)
    private static double kuvalich$applyGunEnhanceDamage(double original,
                                                         ItemStack gun,
                                                         GunData gunData) {
        double multiplier = TaczGunEnhanceUtil.getDamageMultiplier(gun);
        if (multiplier != 1.0) {
            return original * multiplier;
        }
        return original;
    }
}
