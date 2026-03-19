package pers.roinflam.kuvalich.compat.tacz;

import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.module.KillStackManager;

import java.util.HashMap;

/**
 * Warframe 武器模组系统与 TACZ 枪械的属性桥接工具类。
 * Bridge between Warframe weapon module system and TACZ gun attributes.
 * <p>
 * 本类不引用任何 TACZ 类，无 TACZ 时也可安全加载（类加载安全）。
 * This class has ZERO TACZ imports; safe to load even without TACZ (classload-safe).
 */
public class WarframeTaczBridge {

    // ========== 射击期间 ThreadLocal / Shoot-time ThreadLocal ==========

    /** bursting_radius 抑制标志 */
    private static final ThreadLocal<Boolean> suppressBurstRadius = ThreadLocal.withInitial(() -> false);
    AttachmentPropertyManager
    /** TACZ 原始弹丸数（多重射击膨胀前的值） */
    private static final ThreadLocal<Integer> originalBulletAmount = ThreadLocal.withInitial(() -> 0);

    /** 满弹夹第一发射击时的额外伤害倍率 */
    private static final ThreadLocal<Float> firstBulletDamageBonus = ThreadLocal.withInitial(() -> 0f);

    /** 枪械伤害加成倍率（独立乘区） */
    private static final ThreadLocal<Float> gunDamageBonus = ThreadLocal.withInitial(() -> 0f);

    // ========== 缓存刷新期间 ThreadLocal / Cache Refresh ThreadLocal ==========

    /**
     * AttachmentPropertyManager.postChangeEvent 的 shooter 上下文。
     * 由 MixinAttachmentPropertyContext（HEAD）写入，
     * 由 TaczCompatEventHandler.onAttachmentPropertyEvent 读取并清除。
     */
    private static final ThreadLocal<LivingEntity> cacheContextShooter = new ThreadLocal<>();

    /**
     * AttachmentPropertyManager.postChangeEvent 的 gunItem 上下文。
     * 生命周期同 cacheContextShooter。
     */
    private static final ThreadLocal<ItemStack> cacheContextGunItem = new ThreadLocal<>();

    // ========== 抑制标志 API ==========

    public static boolean isBurstRadiusSuppressed() {
        return suppressBurstRadius.get();
    }

    public static void setSuppressBurstRadius(boolean suppress) {
        suppressBurstRadius.set(suppress);
    }

    public static void clearBurstRadiusSuppressed() {
        suppressBurstRadius.set(false);
    }

    // ========== 原始弹丸数 API ==========

    public static int getOriginalBulletAmount() {
        return originalBulletAmount.get();
    }

    public static void setOriginalBulletAmount(int amount) {
        originalBulletAmount.set(amount);
    }

    public static void clearOriginalBulletAmount() {
        originalBulletAmount.set(0);
    }

    // ========== 第一发子弹伤害 API ==========

    public static float getFirstBulletDamageBonus() {
        return firstBulletDamageBonus.get();
    }

    public static void setFirstBulletDamageBonus(float bonus) {
        firstBulletDamageBonus.set(bonus);
    }

    public static void clearFirstBulletDamageBonus() {
        firstBulletDamageBonus.set(0f);
    }

    // ========== 枪械伤害 API ==========

    public static float getGunDamageBonus() {
        return gunDamageBonus.get();
    }

    public static void setGunDamageBonus(float bonus) {
        gunDamageBonus.set(bonus);
    }

    public static void clearGunDamageBonus() {
        gunDamageBonus.set(0f);
    }

    // ========== 缓存上下文 API ==========

    public static LivingEntity getCacheContextShooter() {
        return cacheContextShooter.get();
    }

    public static void setCacheContextShooter(LivingEntity shooter) {
        cacheContextShooter.set(shooter);
    }

    public static ItemStack getCacheContextGunItem() {
        return cacheContextGunItem.get();
    }

    public static void setCacheContextGunItem(ItemStack gunItem) {
        cacheContextGunItem.set(gunItem);
    }

    public static void clearCacheContext() {
        cacheContextShooter.remove();
        cacheContextGunItem.remove();
    }

    // ========== 模组属性读取 ==========

    public static float getFireRateMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
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

    public static float getMultishotMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
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

    public static float getBurstingRadiusMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("bursting_radius", 0.0).floatValue();
    }

    // ========== TACZ 枪械属性读取（第一批）==========

    public static float getReloadSpeedMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("reload_speed", 0.0).floatValue();
    }

    public static float getMagazineSizeMod(ItemStack gunItem) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("magazine_size", 0.0).floatValue();
    }

    public static float getProjectileSpeedMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("projectile_speed", 0.0).floatValue();
    }

    public static float getRecoilReductionMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("recoil_reduction", 0.0).floatValue();
    }

    public static float getFirstBulletDamageMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("first_bullet_damage", 0.0).floatValue();
    }

    // ========== TACZ 枪械属性读取（第二批）==========

    /**
     * 获取枪械上装载的 gun_damage 模组合计修正值。
     * 语义：独立伤害乘区。damageModifier *= (1 + gun_damage)。
     */
    public static float getGunDamageMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("gun_damage", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 headshot_damage 模组合计修正值。
     * 语义：爆头倍率加成。headShotMultiplier *= (1 + headshot_damage)。
     */
    public static float getHeadshotDamageMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("headshot_damage", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 aim_time 模组合计修正值。
     * 语义：瞄准速度加成。newAdsTime = originalAdsTime / (1 + aim_time)。
     */
    public static float getAimTimeMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("aim_time", 0.0).floatValue();
    }

    /**
     * 获取枪械上装载的 accuracy 模组合计修正值。
     * 语义：精准度提升。newInaccuracy = oldInaccuracy * max(0, 1 - accuracy)。
     */
    public static float getAccuracyMod(ItemStack gunItem, LivingEntity shooter) {
        if (gunItem == null || gunItem.isEmpty() || !WeaponModuleHandler.hasBase(gunItem)) {
            return 0f;
        }
        HashMap<String, Double> attributes = WeaponModuleHandler.getWeaponAttributes(gunItem);
        return attributes.getOrDefault("accuracy", 0.0).floatValue();
    }
}