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
}