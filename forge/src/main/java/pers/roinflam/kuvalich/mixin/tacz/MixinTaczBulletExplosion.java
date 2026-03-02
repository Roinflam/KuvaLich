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

/**
 * 注入 EntityKineticBullet 构造器，在子弹创建时直接修改 explosionRadius 字段。
 * Inject into EntityKineticBullet constructor to modify explosionRadius at bullet creation time.
 * <p>
 * 效果：explosionRadius *= (1 + burstingRadius)
 * 例：枪械原始爆炸半径 3.0，burstingRadius=0.33 → 3.0 × 1.33 = 3.99
 * <p>
 * 在构造器末尾修改字段的好处：onHitEntity、onHitBlock、onBulletTick 三处爆炸触发点全部生效，
 * 无需 ThreadLocal 协调，无时序问题。
 * Benefit of modifying field at constructor tail: all three explosion trigger points
 * (onHitEntity, onHitBlock, onBulletTick) benefit automatically, no ThreadLocal timing issues.
 */
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class MixinTaczBulletExplosion {

    /**
     * 子弹是否具有爆炸的标志，用于提前过滤无爆炸子弹。
     */
    @Shadow
    private boolean explosion;

    /**
     * 爆炸半径字段，注入结束时直接修改此值。
     */
    @Shadow
    private float explosionRadius;

    /**
     * 在 EntityKineticBullet 完整构造器（含 LivingEntity 参数的那个）末尾注入。
     * Inject at the tail of the full EntityKineticBullet constructor (the one with LivingEntity).
     * <p>
     * 仅当子弹本身有爆炸（this.explosion == true）且枪械装了 KuvaLich bursting_radius 模组时生效。
     * Only applies when the bullet has explosion (this.explosion == true) AND
     * the gun has KuvaLich bursting_radius modules.
     *
     * @param type           EntityType
     * @param worldIn        世界
     * @param throwerIn      射击者（持枪实体）
     * @param gunItem        枪械 ItemStack
     * @param ammoId         弹药 ID
     * @param gunId          枪械 ID
     * @param gunDisplayId   枪械显示 ID
     * @param isTracerAmmo   是否曳光弹
     * @param gunData        枪械数据
     * @param bulletData     子弹数据
     * @param ci             CallbackInfo
     */
    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V",
            at = @At("RETURN"),
            require = 0
    )
    private void applyKuvaLichExplosionRadius(
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
        // 无爆炸的子弹直接跳过
        // Skip bullets without explosion
        if (!this.explosion) {
            return;
        }

        float burstingRadius = WarframeTaczBridge.getBurstingRadiusMod(gunItem, throwerIn);
        if (burstingRadius <= 0f) {
            return;
        }

        // 直接修改字段：新半径 = 原始半径 × (1 + burstingRadius)
        // Directly modify field: new radius = original radius × (1 + burstingRadius)
        // 例：原始 3.0，burstingRadius=0.33 → 3.0 × 1.33 = 3.99
        this.explosionRadius *= (1.0f + burstingRadius);
    }
}