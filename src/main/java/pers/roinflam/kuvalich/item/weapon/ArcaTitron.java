package pers.roinflam.kuvalich.item.weapon;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.item.KuvaWeaponBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichPotion;
import pers.roinflam.kuvalich.itemstack.KuvaWeapon;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;
import pers.roinflam.kuvalich.utils.util.AttributesUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;

/**
 * 阿卡提龙（Arca Titron）- 战锤
 *
 * 武器特性：
 * 1. 击杀敌人获得攻速Buff（持续20秒）
 *    - 初始叠加6层，每次击杀+1层
 *    - 最高10层
 * 2. 每层Buff增加5%暴击伤害
 *    - 6层时：+30%暴击伤害
 *    - 10层时：+50%暴击伤害
 * 3. 可以破盾
 *
 * 战术思路：
 * - 适合持续战斗，越打越强
 * - 击杀小怪叠层，然后打Boss
 * - Buff持续20秒，需要持续击杀维持层数
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：24%
 * - 暴击倍率：2.0x
 * - 触发几率：38%
 */
@Mod.EventBusSubscriber
public class ArcaTitron extends KuvaWeaponBase {

    public ArcaTitron(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.24, 2.0, 0.38);
    }

    /**
     * 击杀敌人叠加攻速Buff
     * LOWEST优先级：确保在其他死亡事件之后处理
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) return;

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

        // ✅ 使用工具类获取武器（不需要检查攻击冷却，因为是击杀事件）
        ItemStack weapon = WeaponEventUtil.getActiveWeapon(attacker);

        if (weapon != null && weapon.getItem() instanceof ArcaTitron) {
            // ✅ 修复：安全获取现有Buff
            PotionEffect existingEffect = attacker.getActivePotionEffect(KuvaLichPotion.ARCA_TITRON);
            int newAmplifier;

            if (existingEffect != null) {
                // 已有Buff，+1层（最高10层，索引9）
                newAmplifier = Math.min(9, existingEffect.getAmplifier() + 1);
            } else {
                // 新Buff，从6层开始（索引5）
                newAmplifier = 5;
            }

            // 刷新Buff持续时间并增加层数
            attacker.addPotionEffect(new PotionEffect(
                    KuvaLichPotion.ARCA_TITRON,
                    (int) KuvaWeapon.getMagnification(weapon, 400),  // 持续20秒
                    newAmplifier
            ));
        }
    }

    /**
     * 暴击伤害加成
     * 每层Buff增加5%暴击伤害
     */
    @SubscribeEvent
    public static void onCriticalHit(@Nonnull CriticalHitEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getTarget() instanceof EntityLivingBase)) return;

        EntityPlayer attacker = evt.getEntityPlayer();

        // 检查是否有Buff
        PotionEffect buffEffect = attacker.getActivePotionEffect(KuvaLichPotion.ARCA_TITRON);
        if (buffEffect == null) return;

        ItemStack weapon = WeaponEventUtil.getActiveWeapon(attacker);

        if (weapon != null && weapon.getItem() instanceof ArcaTitron) {
            // 每层+5%暴击伤害
            int level = buffEffect.getAmplifier() + 1;  // 层数 = 索引+1
            float bonusDamage = KuvaWeapon.getMagnification(weapon,
                    evt.getDamageModifier() * level * 0.05f);

            evt.setDamageModifier(evt.getDamageModifier() + bonusDamage);
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
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageArcaTitron));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedArcaTitron, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ModConfig.KUVA_WEAPON.movementSpeedArcaTitron, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}