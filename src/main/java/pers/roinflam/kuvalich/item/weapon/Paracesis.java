package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

/**
 * 帕拉西斯（Paracesis）- PVP神器
 *
 * 武器特性：
 * 1. 对玩家伤害x2（对怪物正常）
 * 2. 70%伤害转为持续伤害（5秒内持续扣血）
 * 3. 30%伤害立即生效
 * 4. 所有伤害无视护甲
 *
 * 战术思路：
 * - PVP专用，对玩家伤害翻倍
 * - 持续伤害机制，即使对方逃跑也会持续掉血
 * - 无视护甲，穿透防御
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：31%
 * - 暴击倍率：2.6x
 * - 触发几率：22%
 */
@Mod.EventBusSubscriber
public class Paracesis extends KuvaWeaponBase {

    public Paracesis(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.31, 2.6, 0.22);
    }

    /**
     * 对玩家伤害翻倍
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Paracesis.class);

        if (weapon != null) {
            // 对玩家伤害x2
            if (hurter instanceof EntityPlayer) {
                evt.setAmount(evt.getAmount() * 2);
            }

            // 32%概率额外暴击
            if (RandomUtil.percentageChance(31)) {
                evt.setAmount(evt.getAmount() * 2.6f);
            }
        }
    }

    /**
     * 伤害转换为持续伤害 + 无视护甲
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Paracesis.class);

        if (weapon != null) {
            // 无视护甲
            evt.getSource().setDamageBypassesArmor();

            // 70%伤害转为持续伤害，30%立即生效
            float totalDamage = evt.getAmount();
            float dotDamage = KuvaWeapon.getMagnification(weapon, totalDamage * 0.7f / 100);  // 分100次造成
            evt.setAmount(totalDamage * 0.3f);  // 立即造成30%

            // 持续伤害：5秒内每0.05秒造成一次（共100次）
            new SynchronizationTask(5, 1) {
                private int tick = 0;

                @Override
                public void run() {
                    // 持续5秒（100 ticks）或目标死亡
                    if (++tick > 100 || hurter.isDead) {
                        this.cancel();
                        return;
                    }

                    // 造成持续伤害
                    if (hurter.getHealth() - dotDamage * 2 > 0) {
                        hurter.setHealth(hurter.getHealth() - dotDamage);
                    } else {
                        EntityLivingUtil.kill(hurter, DamageSource.causeMobDamage(attacker).setDamageBypassesArmor());
                        this.cancel();
                    }
                }
            }.start();
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageParacesis));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedParacesis, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ModConfig.KUVA_WEAPON.movementSpeedParacesis, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}