package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

/**
 * 维特利卡（Vitrica）
 *
 * 武器特性：
 * 1. 每次攻击给目标叠加1层Debuff（持续10秒，最高8层）
 * 2. 对有Debuff的目标：伤害x1.25
 * 3. 对无Debuff的目标：伤害x0.75
 *
 * 战术思路：
 * - 前期叠层伤害低，需要先叠满层数
 * - 满层后持续打击可维持高伤害
 * - 适合持续战斗，不适合换目标
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：23%
 * - 暴击倍率：2.3x
 * - 触发几率：33%
 */
@Mod.EventBusSubscriber
public class Vitrica extends KuvaWeaponBase {

    public Vitrica(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.23, 2.3, 0.33);
    }

    /**
     * 叠加Debuff效果
     * 每次攻击+1层（最高8层）
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        // ✅ 使用工具类检查武器和攻击冷却
        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Vitrica.class);

        if (weapon != null) {
            // ✅ 修复：安全获取现有效果
            PotionEffect existingEffect = hurter.getActivePotionEffect(KuvaLichPotion.VITRICA);
            int newAmplifier = (existingEffect != null)
                    ? Math.min(7, existingEffect.getAmplifier() + 1)  // 已有效果，+1层（最高8层，索引7）
                    : 0;  // 新效果，从1层开始（索引0）

            // 刷新效果持续时间并增加层数
            hurter.addPotionEffect(new PotionEffect(
                    KuvaLichPotion.VITRICA,
                    (int) KuvaWeapon.getMagnification(weapon, 200),  // 持续10秒
                    newAmplifier
            ));
        }
    }

    /**
     * 伤害修正
     * 有Debuff：x1.25，无Debuff：x0.75
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Vitrica.class);

        if (weapon != null) {
            // 根据目标是否有Debuff调整伤害
            float multiplier = (hurter.getActivePotionEffect(KuvaLichPotion.VITRICA) != null)
                    ? 1.25f  // 有Debuff：伤害提升25%
                    : 0.75f; // 无Debuff：伤害降低25%

            evt.setAmount(KuvaWeapon.getMagnification(weapon, evt.getAmount() * multiplier, 2));
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageVitrica));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedVitrica, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ModConfig.KUVA_WEAPON.movementSpeedVitrica, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}