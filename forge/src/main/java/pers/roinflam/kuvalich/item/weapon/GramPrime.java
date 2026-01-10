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
import pers.roinflam.kuvalich.utils.util.EntityUtil;
import pers.roinflam.kuvalich.utils.util.WeaponEventUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 巨剑Prime（Gram Prime）
 *
 * 武器特性：
 * - 攻击时对目标周围4格范围内所有敌人造成AOE伤害
 * - AOE伤害 = 主目标伤害 × 66% × 攻击冷却进度
 * - 满冷却时AOE伤害达到66%，快速连击时AOE伤害更低
 *
 * 战术思路：
 * - 面对群体敌人时非常强力
 * - 需要等待攻击冷却恢复以获得最大AOE伤害
 * - 适合清怪，不适合单体Boss
 *
 * 基础属性：
 * - 伤害倍率：80%-120%（95%概率为100%）
 * - 暴击率：32%
 * - 暴击倍率：2.6x
 * - 触发几率：32%
 */
@Mod.EventBusSubscriber
public class GramPrime extends KuvaWeaponBase {

    public GramPrime(String name) {
        super(name);
    }

    @Override
    public ItemStack getBaseAttribute(ItemStack itemStack) {
        double damage = RandomUtil.percentageChance(95) ? 1.0 : RandomUtil.getInt(80, 120) / 100.0;
        return setBaseWeaponAttribute(itemStack, damage, 0.32, 2.6, 0.32);
    }

    /**
     * AOE范围伤害
     * 攻击时对周围敌人造成66%伤害（受攻击冷却影响）
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent evt) {
        if (evt.getEntity().world.isRemote) return;
        if (!(evt.getTarget() instanceof EntityLivingBase)) return;

        EntityLivingBase hurter = (EntityLivingBase) evt.getTarget();
        EntityPlayer attacker = evt.getEntityPlayer();

        // ✅ 使用工具类，但要求攻击冷却>75%才触发AOE
        ItemStack weapon = WeaponEventUtil.checkWeaponAttack(attacker, GramPrime.class, 0.75);

        if (weapon != null) {
            // 获取攻击冷却进度（0-1）
            float cooldownProgress = EntityLivingUtil.getTicksSinceLastSwing(attacker);

            // 获取目标周围4格范围内的所有敌人
            @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class, hurter,
                    KuvaWeapon.getMagnification(weapon, 4, 6),  // 基础4格，可被武器倍率影响
                    entityLivingBase -> !entityLivingBase.equals(hurter) && !entityLivingBase.equals(attacker)
            );

            for (@Nonnull EntityLivingBase nearbyEnemy : entities) {
                // 计算对主目标的伤害
                float mainTargetDamage = EntityPlayerUtil.getAttackDamage(attacker, nearbyEnemy);

                // AOE伤害 = 主目标伤害 × 66% × 攻击冷却进度
                // 满冷却时为66%，快速连击时更低
                float aoeDamage = mainTargetDamage * 0.66f * cooldownProgress;

                nearbyEnemy.attackEntityFrom(DamageSource.causePlayerDamage(attacker), aoeDamage);
            }
        }
    }

    @Override
    public double getAttackDamageAmount(ItemStack itemStack) {
        return AttributesUtil.getDamage(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackDamageGramPrime));
    }

    @Override
    public double getAttackSpeedAmount(ItemStack itemStack) {
        return AttributesUtil.getDamageSpeed(KuvaWeapon.getMagnification(itemStack, ModConfig.KUVA_WEAPON.attackSpeedGramPrime, 2));
    }

    @Override
    public double getMovementSpeedAmount(ItemStack itemStack) {
        return Math.min(0, -1 + KuvaWeapon.getMagnification(itemStack, 1 + ModConfig.KUVA_WEAPON.movementSpeedGramPrime, 2));
    }

    @Override
    public int getMovementSpeedOperation() {
        return 2;
    }
}