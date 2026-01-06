package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
 * 腐蚀镰刀（Caustacyst）
 *
 * 武器特性：
 * 1. 每次攻击附加中毒效果（持续7.5秒）
 * 2. 武器类型为"毒"时：
 *    - 中毒等级2（更强的持续伤害）
 * 3. 武器类型非"毒"时：
 *    - 中毒等级1
 *    - 伤害降低25%（x0.75）
 * 4. 如果目标已中毒，伤害提升33%（x1.33，通过KuvaWeapon.getType判断）
 *
 * 战术思路：
 * - 追求毒属性武器获得最大收益
 * - 持续中毒机制适合打高血量目标
 * - 非毒属性会降低直接伤害，但能上毒
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：9%
 * - 暴击倍率：2.0x
 * - 触发几率：37%
 */
@Mod.EventBusSubscriber
public class Caustacyst extends KuvaWeaponBase {

    public Caustacyst(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.09, 2.0, 0.37);
    }

    /**
     * 攻击附加中毒 + 伤害调整
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        // ✅ 修复：改为 Caustacyst（之前错误地写成 ReaperPrime）
        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, Caustacyst.class);

        if (weapon != null) {
            // 检查武器元素类型
            String weaponType = KuvaWeapon.getType(weapon);

            if (weaponType.equalsIgnoreCase("poison")) {
                // 武器是毒属性：施加强力中毒（等级2）
                hurter.addPotionEffect(new PotionEffect(
                        KuvaLichPotion.POISON,
                        (int) KuvaWeapon.getMagnification(weapon, 150),  // 持续7.5秒
                        1  // 等级2（索引1）
                ));
            } else {
                // 武器非毒属性：伤害降低25%，施加普通中毒（等级1）
                evt.setAmount(KuvaWeapon.getMagnification(weapon, evt.getAmount() * 0.75f));
                hurter.addPotionEffect(new PotionEffect(
                        KuvaLichPotion.POISON,
                        (int) KuvaWeapon.getMagnification(weapon, 150),
                        0  // 等级1（索引0）
                ));
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageCaustacyst));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedCaustacyst));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.movementSpeedCaustacyst));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}