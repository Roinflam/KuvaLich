package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.EntityPlayerUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

/**
 * 德斯特拉Prime（Destreza Prime）- 细剑
 *
 * 武器特性：
 * 1. 攻击时额外造成一次独立伤害判定
 *    - 额外伤害 = 主伤害 × 33% × 攻击冷却进度
 * 2. 减少目标无敌时间（减半）
 *    - 可以更快地进行连击
 *
 * 战术思路：
 * - 双重伤害判定，实际伤害提升33%+
 * - 减少无敌时间，适合快速连击
 * - 攻击冷却>66%才触发额外伤害（避免无意义触发）
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：32%
 * - 暴击倍率：3.0x
 * - 触发几率：20%
 */
@Mod.EventBusSubscriber
public class DestrezaPrime extends KuvaWeaponBase {

    public DestrezaPrime(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.32, 3.0, 0.20);
    }

    /**
     * 额外伤害判定
     * 攻击冷却>66%时触发
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getTarget() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = (EntityLivingBase) evt.getTarget();
        EntityPlayer attacker = evt.getEntityPlayer();

        // ✅ 使用工具类，要求攻击冷却>66%
        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, DestrezaPrime.class, 0.66);

        if (weapon != null) {
            // 获取攻击冷却进度
            float cooldownProgress = EntityLivingUtil.getTicksSinceLastSwing(attacker);

            // 计算主伤害
            float mainDamage = EntityPlayerUtil.getAttackDamage(attacker, hurter);

            // 额外伤害 = 主伤害 × 33% × 冷却进度
            float extraDamage = mainDamage * 0.333f * cooldownProgress;

            // 造成独立伤害判定
            if (hurter.attackEntityFrom(DamageSource.causePlayerDamage(attacker), extraDamage)) {
                // 减少无敌时间（减半），允许更快连击
                hurter.hurtResistantTime = hurter.maxHurtResistantTime / 2;
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageDestrezaPrime));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedDestrezaPrime));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.movementSpeedDestrezaPrime));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}