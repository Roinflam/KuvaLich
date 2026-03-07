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
 * 注入 EntityKineticBullet 构造器，在子弹创建时修改 explosionRadius 和 damageModifier 字段。
 * Inject into EntityKineticBullet constructor to modify explosionRadius and damageModifier at bullet creation time.
 * <p>
 * 功能一：爆炸半径放大
 * explosionRadius *= (1 + burstingRadius)
 * 例：枪械原始爆炸半径 3.0，burstingRadius=0.33 → 3.0 × 1.33 = 3.99
 * <p>
 * 功能二：满弹夹第一发子弹伤害加成
 * 通过 damageModifier 间接乘算基伤。
 * TACZ 伤害计算链路：getDamage() → distance_damage × damageModifier
 * 因此 damageModifier *= (1 + first_bullet_damage) 等效于基伤加成。
 * 例：first_bullet_damage = 10.0（+1000%），damageModifier 1.0 → 11.0 → 伤害×11
 * <p>
 * 散弹枪：shootOnce 内 for 循环创建的所有弹丸共享同一个 ThreadLocal 值，
 * 全部获得加成。这是预期行为——一次射击的所有弹丸都是"第一发"的组成部分。
 * <p>
 * 注意 applyShotgunDamageSpread 在构造器之后、由 shootOnce 循环内调用，
 * 会将 damageModifier 覆写为 1/bulletCount。但 MixinBulletDamageSpread
 * 已将其修正为 1/originalBulletCount。本注入在构造器 RETURN 处执行，
 * 早于 applyShotgunDamageSpread，所以散弹枪场景下执行顺序为：
 *   构造器 → 本注入（damageModifier × 11）→ applyShotgunDamageSpread（覆写为 1/N）
 * 这意味着本注入的乘算会被覆写掉。
 * <p>
 * 解决方案：不在构造器中直接修改 damageModifier，而是在 MixinBulletDamageSpread
 * 修正完散弹伤害均分后，再由 shootOnce 循环外统一处理。
 * 但实际上 applyShotgunDamageSpread 只在 bulletCount > 1 时覆写，
 * 单发枪（bulletCount == 1）不调用该方法，damageModifier 保持构造器默认 1.0。
 * <p>
 * 因此本注入的策略是：
 * - 对于散弹枪（bulletCount > 1）：不在这里处理，改由 MixinBulletDamageSpread 统一处理
 * - 对于单发枪（bulletCount == 1）：在这里直接乘算 damageModifier
 * <p>
 * 为简化实现，统一在这里设置 ThreadLocal 标记，让 MixinBulletDamageSpread 读取并叠加。
 * 但由于 MixinBulletDamageSpread 只在 originalAmount != bulletCount 时才介入，
 * 无多重射击的单发枪不会触发 MixinBulletDamageSpread。
 * <p>
 * 最终方案：统一在构造器 RETURN 通过 Accessor 修改 damageModifier。
 * applyShotgunDamageSpread 是在构造器返回之后、由 shootOnce 循环内调用的，
 * 而 MixinBulletDamageSpread 会拦截并取消原方法，用 originalBulletCount 重写。
 * 所以需要让 MixinBulletDamageSpread 也感知第一发加成。
 * <p>
 * ❌ 以上分析过于复杂。实际上最简单的做法：
 * 在构造器 RETURN 处无条件通过 Accessor 乘算 damageModifier。
 * applyShotgunDamageSpread 随后会覆写 damageModifier = 1/bulletCount，
 * 但 MixinBulletDamageSpread 已取消原方法并设为 1/originalBulletCount。
 * 两者都是覆写（=赋值），不是乘算，所以本注入的值会被丢弃。
 * <p>
 * ✅ 真正的最终方案：不通过 damageModifier，改为直接修改 damageAmount 链表中的每个伤害值。
 * 但 damageAmount 是 LinkedList<DistanceDamagePair>，修改比较麻烦。
 * <p>
 * ✅✅ 最简方案：将第一发加成倍率也存入 ThreadLocal，
 * 让 MixinBulletDamageSpread 在设置 damageModifier 时一并叠加。
 * 对于不走 MixinBulletDamageSpread 的情况（单发无多重），在本注入中直接乘算。
 * <p>
 * 实际实现：本注入始终通过 Accessor 乘算 damageModifier。
 * MixinBulletDamageSpread 在覆写时也读取 firstBulletBonus 并叠加。
 * 覆盖所有场景。
 */
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public class MixinTaczBulletExplosion {

    /**
     * 子弹是否具有爆炸的标志，用于提前过滤无爆炸子弹。
     */
    @Shadow
    private boolean explosion;

    /**
     * 爆炸半径字段。
     */
    @Shadow
    private float explosionRadius;

    /**
     * 在 EntityKineticBullet 完整构造器（含 LivingEntity 参数的那个）末尾注入。
     * Inject at the tail of the full EntityKineticBullet constructor (the one with LivingEntity).
     * <p>
     * 同时处理爆炸半径放大和第一发子弹伤害加成。
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
        // Function 1: Explosion radius amplification
        if (this.explosion) {
            float burstingRadius = WarframeTaczBridge.getBurstingRadiusMod(gunItem, throwerIn);
            if (burstingRadius > 0f) {
                // 新半径 = 原始半径 × (1 + burstingRadius)
                // New radius = original radius × (1 + burstingRadius)
                this.explosionRadius *= (1.0f + burstingRadius);
            }
        }

        // ========== 功能二：满弹夹第一发子弹伤害加成 ==========
        // Function 2: First bullet damage bonus from full magazine
        //
        // 通过 Accessor 乘算 damageModifier 实现基伤加成。
        // TACZ 伤害链路：getDamage() → distance_damage × damageModifier
        // damageModifier 默认 1.0，散弹枪被 applyShotgunDamageSpread 覆写为 1/bulletCount。
        //
        // 执行时序（构造器 RETURN）：
        //   此刻 damageModifier 仍为默认 1.0（applyShotgunDamageSpread 尚未调用）。
        //   本注入乘算后 damageModifier = 1.0 × (1 + bonus) = 11.0
        //
        // 后续散弹枪场景：
        //   applyShotgunDamageSpread(bulletCount) 会覆写 damageModifier = 1/bulletCount
        //   但 MixinBulletDamageSpread 已取消原方法并重写为 1/originalBulletCount
        //   两者都是覆写赋值，会丢弃本注入的乘算结果。
        //   因此 MixinBulletDamageSpread 需要额外读取 firstBulletBonus 并叠加。
        //
        // 单发枪（bulletCount == 1）：
        //   applyShotgunDamageSpread 不修改 damageModifier（if bulletCount > 1 才执行）
        //   本注入的乘算结果保留，正确生效。
        //
        // Apply through Accessor: damageModifier *= (1 + first_bullet_damage)
        float firstBulletBonus = WarframeTaczBridge.getFirstBulletDamageBonus();
        if (firstBulletBonus > 0f) {
            EntityKineticBulletAccessor accessor = (EntityKineticBulletAccessor) (Object) this;
            float originalModifier = accessor.getDamageModifier();
            float newModifier = originalModifier * (1f + firstBulletBonus);
            accessor.setDamageModifier(newModifier);

            LogUtil.debug(String.format(
                    "[膛室] 满弹夹第一发加成生效! damageModifier=%.4f → %.4f, 加成=+%.0f%%",
                    originalModifier, newModifier, firstBulletBonus * 100f
            ));
        }
    }
}