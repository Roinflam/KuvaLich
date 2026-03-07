package pers.roinflam.kuvalich.compat.tacz;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.itemstack.ItemModule;
import pers.roinflam.kuvalich.itemstack.KillStackManager;

import java.util.HashMap;

/**
 * Warframe 武器模组系统与 TACZ 枪械的属性桥接工具类。
 * Bridge between Warframe weapon module system and TACZ gun attributes.
 * <p>
 * 本类不引用任何 TACZ 类，无 TACZ 时也可安全加载（类加载安全）。
 * This class has ZERO TACZ imports; safe to load even without TACZ (classload-safe).
 */
public class WarframeTaczBridge {

    /**
     * ThreadLocal 标志：当 TACZ 枪械自带爆炸逻辑时，抑制 KuvaLich bursting_radius 的 AOE 计算。
     * ThreadLocal flag: suppress KuvaLich bursting_radius AOE when TACZ gun has its own explosion logic.
     * <p>
     * 生命周期：由 TaczCompatEventHandler（NORMAL 优先级）写入，
     * 由 ItemModule.processDamage（LOWEST 优先级）读取并清除。
     * Lifecycle: written by TaczCompatEventHandler (NORMAL priority),
     * read and cleared by ItemModule.processDamage (LOWEST priority).
     */
    private static final ThreadLocal<Boolean> suppressBurstRadius = ThreadLocal.withInitial(() -> false);

    /**
     * ThreadLocal 存储：TACZ 原始弹丸数（多重射击膨胀前的值）。
     * ThreadLocal storage: TACZ original bullet count (before multishot inflation).
     * <p>
     * 生命周期：由 MixinGunShootOnce（修改 bulletAmount 前）写入，
     * 由 MixinBulletDamageSpread（applyShotgunDamageSpread 注入）读取。
     * Lifecycle: written by MixinGunShootOnce (before modifying bulletAmount),
     * read by MixinBulletDamageSpread (in applyShotgunDamageSpread injection).
     * <p>
     * 值为 0 表示未设置（不干预）。
     * Value of 0 means not set (no intervention).
     */
    private static final ThreadLocal<Integer> originalBulletAmount = ThreadLocal.withInitial(() -> 0);

    /**
     * ThreadLocal 存储：满弹夹第一发射击时的额外伤害倍率。
     * ThreadLocal storage: bonus damage multiplier for first bullet from full magazine.
     * <p>
     * 生命周期：由 MixinFirstBulletDetect（shootOnce HEAD）写入，
     * 由 MixinFirstBulletDamage（EntityKineticBullet 构造器）读取，
     * 由 MixinFirstBulletDetect（shootOnce RETURN）清除。
     * Lifecycle: written by MixinFirstBulletDetect (shootOnce HEAD),
     * read by MixinFirstBulletDamage (EntityKineticBullet constructor),
     * cleared by MixinFirstBulletDetect (shootOnce RETURN).
     * <p>
     * 值为 0 表示非第一发或无模组。
     * Value of 0 means not first bullet or no module.
     */
    private static final ThreadLocal<Float> firstBulletDamageBonus = ThreadLocal.withInitial(() -> 0f);

    // ========== 抑制标志 API / Suppress Flag API ==========

    /**
     * 查询当前线程是否处于 bursting_radius 抑制状态。
     *
     * @return 是否抑制
     */
    public static boolean isBurstRadiusSuppressed() {
        return suppressBurstRadius.get();
    }

    /**
     * 设置 bursting_radius 抑制标志。
     *
     * @param suppress 是否抑制
     */
    public static void setSuppressBurstRadius(boolean suppress) {
        suppressBurstRadius.set(suppress);
    }

    /**
     * 清除 bursting_radius 抑制标志。
     */
    public static void clearBurstRadiusSuppressed() {
        suppressBurstRadius.set(false);
    }

    // ========== 原始弹丸数 API / Original Bullet Amount API ==========

    /**
     * 获取当前线程存储的原始弹丸数。
     *
     * @return 原始弹丸数，0 表示未设置
     */
    public static int getOriginalBulletAmount() {
        return originalBulletAmount.get();
    }

    /**
     * 设置原始弹丸数（多重射击膨胀前的值）。
     * <p>
     * 由 MixinGunShootOnce 在修改 bulletAmount 之前调用，
     * 确保 MixinBulletDamageSpread 能用原始值计算 damageModifier。
     *
     * @param amount 原始弹丸数
     */
    public static void setOriginalBulletAmount(int amount) {
        originalBulletAmount.set(amount);
    }

    /**
     * 清除原始弹丸数。
     */
    public static void clearOriginalBulletAmount() {
        originalBulletAmount.set(0);
    }

    // ========== 第一发子弹伤害 API / First Bullet Damage API ==========

    /**
     * 获取当前线程的第一发子弹伤害加成。
     *
     * @return 伤害加成倍率，0 表示无加成
     */
    public static float getFirstBulletDamageBonus() {
        return firstBulletDamageBonus.get();
    }

    /**
     * 设置第一发子弹伤害加成。
     *
     * @param bonus 伤害加成倍率
     */
    public static void setFirstBulletDamageBonus(float bonus) {
        firstBulletDamageBonus.set(bonus);
    }

    /**
     * 清除第一发子弹伤害加成。
     */
    public static void clearFirstBulletDamageBonus() {
        firstBulletDamageBonus.set(0f);
    }

    // ========== 模组属性读取 / Module Attribute Getters ==========

    /**
     * 获取枪械上装载的 firing_rate 模组合计修正值。
     *
     * @param gunItem 枪械 ItemStack
     * @param shooter 持枪实体
     * @return firing_rate 增量值，无模组时返回 0
     */
    public static float getFireRateMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !ItemModule.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = ItemModule.getWeaponAttributes(gunItem);
        double firingRate = attributes.getOrDefault("firing_rate", 0.0);
        if (shooter instanceof Player player) {
            Double stackBonus = attributes.get("killStackFiringRate");
            if (stackBonus != null) {
                int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.FIRING_RATE);
                if (stacks > 0) {
                    firingRate += stackBonus * stacks;
                }
            }
        }
        return (float) firingRate;
    }

    /**
     * 获取枪械上装载的 multishot 模组合计修正值。
     *
     * @param gunItem 枪械 ItemStack
     * @param shooter 持枪实体
     * @return multishot 增量值，无模组时返回 0
     */
    public static float getMultishotMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !ItemModule.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = ItemModule.getWeaponAttributes(gunItem);
        double multishot = attributes.getOrDefault("multishot", 0.0);
        if (shooter instanceof Player player) {
            Double stackBonus = attributes.get("killStackMultishot");
            if (stackBonus != null) {
                int stacks = KillStackManager.getStacks(player, KillStackManager.StackType.MULTISHOT);
                if (stacks > 0) {
                    multishot += stackBonus * stacks;
                }
            }
        }
        return (float) multishot;
    }

    /**
     * 获取枪械上装载的 bursting_radius 模组合计修正值。
     * Get total bursting_radius modifier from KuvaLich weapon modules on the gun.
     * <p>
     * 用于在 EntityKineticBullet 构造器中直接修改 explosionRadius 字段：
     * explosionRadius *= (1 + burstingRadius)
     *
     * @param gunItem 枪械 ItemStack
     * @param shooter 持枪实体
     * @return bursting_radius 增量值，无模组时返回 0
     */
    public static float getBurstingRadiusMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !ItemModule.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = ItemModule.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("bursting_radius", 0.0).floatValue();
    }

    // ========== TACZ 枪械新属性读取 / New TACZ Gun Attribute Getters ==========

    /**
     * 获取枪械上装载的 reload_speed 模组合计修正值。
     * Get total reload_speed modifier from KuvaLich weapon modules on the gun.
     * <p>
     * 用于膨胀装填已耗时间，使脚本判定更早完成装填。
     * Used to inflate elapsed reload time so script logic completes reload earlier.
     * <p>
     * 公式：膨胀后耗时 = 实际耗时 × (1 + reload_speed)
     *
     * @param gunItem 枪械 ItemStack
     * @param shooter 持枪实体
     * @return reload_speed 增量值，无模组时返回 0
     */
    public static float getReloadSpeedMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !ItemModule.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = ItemModule.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("reload_speed", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 magazine_size 模组合计修正值。
     * Get total magazine_size modifier from KuvaLich weapon modules on the gun.
     * <p>
     * 用于修改 AttachmentDataUtils.getAmmoCountWithAttachment 返回值。
     * Used to modify the return value of AttachmentDataUtils.getAmmoCountWithAttachment.
     * <p>
     * 注意：此方法不接受 shooter 参数，因为 AttachmentDataUtils.getAmmoCountWithAttachment
     * 是静态方法，调用上下文中无法获取射击者实体。
     * Note: No shooter param because AttachmentDataUtils.getAmmoCountWithAttachment is static.
     *
     * @param gunItem 枪械 ItemStack
     * @return magazine_size 增量值，无模组时返回 0
     */
    public static float getMagazineSizeMod(ItemStack gunItem) {
        if (gunItem == null || gunItem.isEmpty() || !ItemModule.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = ItemModule.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("magazine_size", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 projectile_speed 模组合计修正值。
     * Get total projectile_speed modifier from KuvaLich weapon modules on the gun.
     * <p>
     * 用于修改 shootOnce 内部 processedSpeed（子弹飞行速度）。
     * 速度提升 → 同一 lifetime 内子弹飞行更远 → 有效射程自然增加。
     * Used to modify processedSpeed in shootOnce (bullet flight speed).
     * Higher speed → bullet travels farther in same lifetime → effective range increases.
     *
     * @param gunItem 枪械 ItemStack
     * @param shooter 持枪实体
     * @return projectile_speed 增量值，无模组时返回 0
     */
    public static float getProjectileSpeedMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !ItemModule.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = ItemModule.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("projectile_speed", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 recoil_reduction 模组合计修正值。
     * Get total recoil_reduction modifier from KuvaLich weapon modules on the gun.
     * <p>
     * 用于缩放 CameraSetupEvent 中 genPitch/YawSplineFunction 的 modifier 参数。
     * 在客户端侧生效，直接降低摄像机后坐力偏移量。
     * Used to scale the modifier parameter of genPitch/YawSplineFunction in CameraSetupEvent.
     * Takes effect client-side, directly reducing camera recoil displacement.
     *
     * @param gunItem 枪械 ItemStack
     * @param shooter 持枪实体
     * @return recoil_reduction 增量值，无模组时返回 0
     */
    public static float getRecoilReductionMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !ItemModule.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = ItemModule.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("recoil_reduction", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 first_bullet_damage 模组合计修正值。
     * Get total first_bullet_damage modifier from KuvaLich weapon modules on the gun.
     * <p>
     * 语义：满弹夹射出第一发时，子弹基伤直接乘以 (1 + first_bullet_damage)。
     * Semantics: when firing the first bullet from a full magazine,
     * bullet base damage is multiplied by (1 + first_bullet_damage).
     * <p>
     * 例：first_bullet_damage = 10.0（+1000%），原始 10 伤害 → 10 × 11 = 110 伤害
     * Example: first_bullet_damage = 10.0 (+1000%), original 10 dmg → 10 × 11 = 110 dmg
     *
     * @param gunItem 枪械 ItemStack
     * @param shooter 持枪实体
     * @return first_bullet_damage 增量值，无模组时返回 0
     */
    public static float getFirstBulletDamageMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !ItemModule.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = ItemModule.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("first_bullet_damage", 0.0).floatValue();
    }
}