package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.render.damagedisplay.DamageInfo;
import pers.roinflam.kuvalich.utils.helper.task.SynchronizationTask;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.EntityLivingUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

/**
 * 关刀Prime（Guandao Prime）
 *
 * 武器特性：
 * 1. 每次攻击获得攻速Buff（持续5秒）
 *    - 每次+5层（从6层开始叠加）
 * 2. 附加持续流血4秒
 *    - 伤害 = (造成伤害 + 目标最大生命2.5%) / 20
 *    - 每0.25秒触发一次
 *
 * 战术思路：
 * - 持续战斗越来越快
 * - 流血伤害对高血量目标有效
 * - 需要持续攻击维持Buff
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：32%
 * - 暴击倍率：2.4x
 * - 触发几率：20%
 */
@Mod.EventBusSubscriber
public class GuandaoPrime extends KuvaWeaponBase {

    public GuandaoPrime(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.32, 2.4, 0.20);
    }

    /**
     * 叠加攻速Buff + 流血效果
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = evt.getEntityLiving();
        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, GuandaoPrime.class);

        if (weapon != null) {
            // 叠加攻速Buff（+5层）
            PotionEffect existingEffect = attacker.getActivePotionEffect(KuvaLichPotion.GUANDAO_PRIME);
            int currentLevel = existingEffect != null ? existingEffect.getAmplifier() : 5;  // 默认6层（索引5）

            attacker.addPotionEffect(new PotionEffect(
                    KuvaLichPotion.GUANDAO_PRIME,
                    (int) KuvaWeapon.getMagnification(weapon, 100),  // 持续5秒
                    currentLevel + 5  // +5层
            ));

            // 持续流血效果
            float baseDamage = evt.getAmount();
            float maxHealthDamage = hurter.getMaxHealth() * 0.025f;
            float dotDamage = KuvaWeapon.getMagnification(weapon, (baseDamage + maxHealthDamage) / 20);

            new SynchronizationTask(5, 5) {
                private int tick = 0;

                @Override
                public void run() {
                    // 持续4秒（20次）或目标死亡
                    if (++tick > 20 || hurter.isDead) {
                        this.cancel();
                        return;
                    }

                    // 显示伤害数字（仅客户端）
                    if (attacker instanceof EntityPlayer) {
                        double offsetX = (Math.random() - 0.5) * hurter.width;
                        double offsetY = hurter.height * 0.25 + (Math.random() * hurter.height * 0.75);
                        double offsetZ = (Math.random() - 0.5) * hurter.width;
                        Vec3d position = new Vec3d(hurter.posX + offsetX, hurter.posY + offsetY, hurter.posZ + offsetZ);

                        KuvaLich.network.sendTo(
                                new DamagePacket(dotDamage, position, DamageInfo.DamageColor.WHITE.getColor()),
                                (EntityPlayerMP) attacker
                        );
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
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageGuandaoPrime, 2));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedGuandaoPrime));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.max(0, KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.movementSpeedGuandaoPrime));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}