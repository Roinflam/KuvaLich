package pers.roinflam.kuvalich.utils.util;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;

import javax.annotation.Nullable;

/**
 * 武器事件工具类
 *
 * 统一处理武器事件中的常见检查：
 * 1. 安全获取手持武器（避免NPE）
 * 2. 检查攻击冷却时机
 * 3. 验证武器类型
 */
public class WeaponEventUtil {

    /**
     * 安全获取攻击者当前手持的武器
     *
     * @param attacker 攻击者
     * @return 武器物品堆栈，如果不满足条件返回null
     */
    @Nullable
    public static ItemStack getActiveWeapon(EntityLivingBase attacker) {
        if (attacker == null) {
            return null;
        }

        EnumHand activeHand = attacker.getActiveHand();
        if (activeHand == null) {
            return null;
        }

        ItemStack itemStack = attacker.getHeldItem(activeHand);
        return itemStack.isEmpty() ? null : itemStack;
    }

    /**
     * 检查攻击者是否正在使用指定的武器（严格模式：冷却必须=1）
     *
     * @param attacker 攻击者
     * @param weaponClass 武器类型
     * @return 如果正在使用返回武器堆栈，否则返回null
     */
    @Nullable
    public static ItemStack checkWeaponAttack(EntityLivingBase attacker, Class<? extends KuvaWeaponBase> weaponClass) {
        return checkWeaponAttack(attacker, weaponClass, 1.0);
    }

    /**
     * 检查攻击者是否正在使用指定的武器（自定义冷却阈值）
     *
     * @param attacker 攻击者
     * @param weaponClass 武器类型
     * @param cooldownThreshold 冷却阈值（0-1），例如：
     *                          - 1.0 表示必须完全冷却
     *                          - 0.66 表示冷却到66%即可触发
     * @return 如果正在使用返回武器堆栈，否则返回null
     */
    @Nullable
    public static ItemStack checkWeaponAttack(EntityLivingBase attacker,
                                              Class<? extends KuvaWeaponBase> weaponClass,
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
        if (attacker instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) attacker;
            float ticksSinceSwing = EntityLivingUtil.getTicksSinceLastSwing(player);

            // 严格检查：必须达到指定冷却
            if (cooldownThreshold >= 1.0) {
                if (ticksSinceSwing < cooldownThreshold) {
                    return null;
                }
            } else {
                // 宽松检查：冷却达到阈值即可
                if (ticksSinceSwing < cooldownThreshold) {
                    return null;
                }
            }
        }

        return weapon;
    }
}