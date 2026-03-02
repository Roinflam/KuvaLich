package pers.roinflam.kuvalich.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;

/**
 * 注入 GunData.getShootInterval，应用 Warframe 射速模组修正。
 * firing_rate 语义：增量百分比（0.4 = +40%，-0.4 = -40%，-1.0 = 完全无法射击）
 * 新间隔 = 原始间隔 / (1 + firing_rate)，间隔越短射速越快
 */
@Mixin(targets = "com.tacz.guns.resource.pojo.data.gun.GunData", remap = false)
public class MixinGunShootInterval {

    /**
     * 修改 getShootInterval 返回值，缩短或延长射击冷却。
     *
     * @param original 原始间隔（毫秒）
     * @param shooter  射击者
     * @param fireMode 射击模式
     * @param gunItem  枪械 ItemStack
     * @return 修正后的间隔（毫秒）
     */
    @ModifyReturnValue(
            method = "getShootInterval(Lnet/minecraft/world/entity/LivingEntity;Lcom/tacz/guns/api/item/gun/FireMode;Lnet/minecraft/world/item/ItemStack;)J",
            at = @At("RETURN"),
            require = 0
    )
    private long applyWarframeFireRate(long original, LivingEntity shooter, FireMode fireMode, ItemStack gunItem) {
        float fireRateMod = WarframeTaczBridge.getFireRateMod(gunItem, shooter);

        // 无修正时直接返回
        if (Math.abs(fireRateMod) < 0.001f) {
            return original;
        }

        // firing_rate <= -1.0：完全无法射击，返回极大值
        if (fireRateMod <= -1.0f) {
            return Long.MAX_VALUE / 2;
        }

        // 新间隔 = 原始间隔 / (1 + firing_rate)
        // +40% → 除以 1.4 → 间隔缩短 → 射速提升
        // -40% → 除以 0.6 → 间隔延长 → 射速降低
        long result = (long) (original / (1f + fireRateMod));

        // 保证最低间隔不低于 50ms
        result = Math.max(result, 50L);

        return result;
    }
}