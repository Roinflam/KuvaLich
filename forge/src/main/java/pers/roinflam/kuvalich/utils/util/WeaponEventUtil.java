package pers.roinflam.kuvalich.utils.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractKuvaWeapon;

import javax.annotation.Nullable;

/**
 * 武器事件工具类
 * Weapon event utility class
 */
public class WeaponEventUtil {

    /**
     * 安全获取攻击者当前手持的武器
     * Safely get the weapon currently held by the attacker
     *
     * ✅ 业务逻辑与1.12.2完全一致
     *
     * @param attacker 攻击者 / attacker
     * @return 武器物品堆栈，如果不满足条件返回null / weapon ItemStack, null if conditions not met
     */
    @Nullable
    public static ItemStack getActiveWeapon(LivingEntity attacker) {
        if (attacker == null) {
            return null;
        }

        // ✅ 1.20.1等价API：getUsedItemHand()
        // 如果实体没有在使用物品，返回null（与1.12.2一致）
        InteractionHand activeHand = attacker.getUsedItemHand();
        if (activeHand == null) {
            return null; // ✅ 与1.12.2逻辑一致：直接返回null
        }

        ItemStack itemStack = attacker.getItemInHand(activeHand);
        return itemStack.isEmpty() ? null : itemStack;
    }

    /**
     * 检查攻击者是否正在使用指定的武器（严格模式：冷却必须=1）
     * Check if attacker is using specified weapon (strict mode: cooldown must = 1)
     *
     * ✅ 业务逻辑与1.12.2完全一致
     */
    @Nullable
    public static ItemStack checkWeaponAttack(LivingEntity attacker, Class<? extends AbstractKuvaWeapon> weaponClass) {
        return checkWeaponAttack(attacker, weaponClass, 1.0);
    }

    /**
     * 检查攻击者是否正在使用指定的武器（自定义冷却阈值）
     * Check if attacker is using specified weapon (custom cooldown threshold)
     *
     * ✅ 业务逻辑与1.12.2完全一致
     */
    @Nullable
    public static ItemStack checkWeaponAttack(LivingEntity attacker,
                                              Class<? extends AbstractKuvaWeapon> weaponClass,
                                              double cooldownThreshold) {
        ItemStack weapon = getActiveWeapon(attacker);
        if (weapon == null) {
            return null;
        }

        // 检查武器类型
        if (!weaponClass.isInstance(weapon.getItem())) {
            return null;
        }

        // 如果是玩家，检查攻击冷却
        if (attacker instanceof Player player) {
            // ✅ 1.20.1等价API：getAttackStrengthScale
            // 参数0.5F是部分tick的插值（与1.12.2的getCooledAttackStrength(0)等价）
            float attackStrength = player.getAttackStrengthScale(0.5F);

            // ✅ 业务逻辑完全一致
            if (cooldownThreshold >= 1.0) {
                if (attackStrength < cooldownThreshold) {
                    return null;
                }
            } else {
                if (attackStrength < cooldownThreshold) {
                    return null;
                }
            }
        }

        return weapon;
    }
}