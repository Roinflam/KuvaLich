// MixinTaczBulletExplosion.java
package pers.roinflam.kuvalich.mixin.tacz;

import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.kuvalich.compat.tacz.WarframeTaczBridge;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 注入 EntityKineticBullet 构造器，在子弹创建时修改字段。
 * <p>
 * 功能一：爆炸半径放大
 * explosionRadius *= (1 + burstingRadius)
 * <p>
 * 功能二：满弹夹第一发子弹伤害加成
 * damageModifier *= (1 + first_bullet_damage)
 * <p>
 * 功能三：枪械伤害独立乘区
 * damageModifier *= (1 + gun_damage)
 * <p>
 * 功能四：玄骸强化独立乘区
 * damageModifier *= gunEnhanceMultiplier
 * <p>
 * 注意：headshot_damage 和 aim_time、accuracy 一样走 AttachmentPropertyEvent 缓存修改，
 * 不在此处处理。
 */
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class MixinTaczBulletExplosion {

    /** 子弹是否具有爆炸的标志 */
    @Shadow
    private boolean explosion;

    /** 爆炸半径字段 */
    @Shadow
    private float explosionRadius;

    /**
     * 在 EntityKineticBullet 完整构造器末尾注入。
     * 处理爆炸半径放大、第一发子弹伤害、枪械伤害和玄骸强化。
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void applyKuvaLichBulletModifiers(
            EntityType<?> type,
            Level worldIn,
            LivingEntity throwerIn,
            ItemStack gunItem,
            ResourceLocation ammoId,
            ResourceLocation gunId,
            ResourceLocation gunDisplayId,
            boolean isTracerAmmo,
            GunData gunData,
            BulletData bulletData,
            CallbackInfo ci
    ) {
        // ========== 功能一：爆炸半径放大 ==========
        if (this.explosion) {
            float burstingRadius = WarframeTaczBridge.getBurstingRadiusMod(gunItem, throwerIn);
            if (burstingRadius > 0f) {
                this.explosionRadius *= (1.0f + burstingRadius);
            }
        }

        EntityKineticBulletAccessor accessor = (EntityKineticBulletAccessor) (Object) this;

        // ========== 功能二：满弹夹第一发子弹伤害加成 ==========
        float firstBulletBonus = WarframeTaczBridge.getFirstBulletDamageBonus();
        if (firstBulletBonus > 0f) {
            float originalModifier = accessor.getDamageModifier();
            float newModifier = originalModifier * (1f + firstBulletBonus);
            accessor.setDamageModifier(newModifier);

            LogUtil.debug(String.format(
                    "[膛室] 满弹夹第一发加成生效! damageModifier=%.4f → %.4f, 加成=+%.0f%%",
                    originalModifier, newModifier, firstBulletBonus * 100f
            ));
        }

        // ========== 功能三：枪械伤害独立乘区 ==========
        float gunDamageBonus = WarframeTaczBridge.getGunDamageBonus();
        if (gunDamageBonus > 0f) {
            float currentModifier = accessor.getDamageModifier();
            float newModifier = currentModifier * (1f + gunDamageBonus);
            accessor.setDamageModifier(newModifier);

            LogUtil.debug(String.format(
                    "[枪械伤害] gun_damage生效! damageModifier=%.4f → %.4f, 加成=+%.0f%%",
                    currentModifier, newModifier, gunDamageBonus * 100f
            ));
        }

        // ========== 功能四：玄骸强化独立乘区 ==========
        double enhanceMultiplier = WarframeTaczBridge.getGunEnhanceMultiplier();
        if (enhanceMultiplier != 1.0) {
            float currentModifier = accessor.getDamageModifier();
            float newModifier = (float) (currentModifier * enhanceMultiplier);
            accessor.setDamageModifier(newModifier);

            LogUtil.debug(String.format(
                    "[玄骸强化] 强化乘数生效! damageModifier=%.4f → %.4f, 乘数=%.2fx",
                    currentModifier, newModifier, enhanceMultiplier
            ));
        }
    }
}