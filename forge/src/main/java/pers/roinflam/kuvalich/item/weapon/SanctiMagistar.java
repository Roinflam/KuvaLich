package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 圣教权杖（Sancti Magistar）
 *
 * 武器特性：
 * 1. 攻击时对15格范围内同类生物持续治疗
 *    - 治疗量 = 造成伤害 × 5% / 50次
 *    - 持续5秒，每0.1秒触发一次
 * 2. 手持时受非实体伤害-50%
 *    - 包括：摔落、窒息、岩浆、火焰等环境伤害
 * 3. 可以破盾
 *
 * 战术思路：
 * - 团队辅助型武器
 * - 攻击敌人时治疗队友
 * - 提供额外生存能力（减伤）
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：30%
 * - 暴击倍率：2.0x
 * - 触发几率：20%
 */
@Mod.EventBusSubscriber
public class SanctiMagistar extends KuvaWeaponBase {

    public SanctiMagistar(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.30, 2.0, 0.20);
    }

    /**
     * 双重效果：
     * 1. 攻击时：范围治疗同类生物
     * 2. 受击时：非实体伤害-50%
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) return;

        DamageSource damageSource = evt.getSource();
        EntityLivingBase hurter = evt.getEntityLiving();

        // 情况1：攻击者持有武器 -> 范围治疗
        if (damageSource.getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();

            ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, SanctiMagistar.class);

            if (weapon != null) {
                // 治疗量 = 伤害 × 5% / 50次
                float healAmount = KuvaWeapon.getMagnification(weapon, evt.getAmount() * 0.05f, 0.5f) / 50;

                // 持续治疗：5秒，每0.1秒触发一次（共50次）
                new SynchronizationTask(5, 1) {
                    private int ticks = 0;

                    @Override
                    public void run() {
                        if (++ticks > 50) {
                            this.cancel();
                            return;
                        }

                        // 获取范围内同类生物（不包括目标）
                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class, attacker, 15,
                                e -> e.getClass() == attacker.getClass() && !e.equals(hurter)
                        );

                        // 治疗所有同类生物
                        for (@Nonnull EntityLivingBase ally : entities) {
                            ally.heal(healAmount);
                        }
                    }
                }.start();
            }
        }
        // 情况2：受击者持有武器 + 非实体伤害 -> 减伤50%
        else if (damageSource.getTrueSource() == null) {
            ItemStack weapon = WeaponEventUtil.getActiveWeapon(hurter);

            if (weapon != null && weapon.getItem() instanceof SanctiMagistar) {
                // 非实体伤害减少50%
                evt.setAmount(evt.getAmount() - KuvaWeapon.getMagnification(weapon, evt.getAmount() * 0.5f, 2));
            }
        }
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, EntityLivingBase entity, EntityLivingBase attacker) {
        return true;
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageSanctiMagistar));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedSanctiMagistar, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ModConfig.KUVA_WEAPON.movementSpeedSanctiMagistar, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}