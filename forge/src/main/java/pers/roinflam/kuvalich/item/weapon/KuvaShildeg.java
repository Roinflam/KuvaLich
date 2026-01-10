package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

/**
 * 赤毒重锤（Kuva Shildeg）
 *
 * 武器特性：
 * 1. 目标有护甲：无视护甲
 * 2. 目标无护甲：伤害x1.25
 * 3. 可以破盾
 *
 * 战术思路：
 * - 对高护甲目标非常有效（无视护甲）
 * - 对低护甲目标也有伤害加成
 * - 全局收益，没有副作用
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：31%
 * - 暴击倍率：2.7x
 * - 触发几率：27%
 */
@Mod.EventBusSubscriber
public class KuvaShildeg extends KuvaWeaponBase {

    public KuvaShildeg(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.31, 2.7, 0.27);
    }

    /**
     * 护甲检测与伤害调整
     * 有护甲：无视护甲
     * 无护甲：伤害x1.25
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, KuvaShildeg.class);

        if (weapon != null) {
            // 检查目标护甲值
            if (hurter.getTotalArmorValue() > 0) {
                // 有护甲：无视护甲
                evt.getSource().setDamageBypassesArmor();
            } else {
                // 无护甲：伤害提升25%
                evt.setAmount(evt.getAmount() * KuvaWeapon.getMagnification(weapon, 1.25f));
            }
        }
    }

    /**
     * 可以破盾
     */
    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, EntityLivingBase entity, EntityLivingBase attacker) {
        return true;
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageKuvaShildeg));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedKuvaShildeg, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ModConfig.KUVA_WEAPON.movementSpeedKuvaShildeg, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}