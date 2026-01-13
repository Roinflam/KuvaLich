package pers.roinflam.kuvalich.utils.util;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.CriticalHitEvent;

/**
 * 玩家实体工具类
 * Player entity utility class
 */
public class EntityPlayerUtil {

    /**
     * 获取玩家的攻击伤害
     * Get player's attack damage
     *
     * ✅ 业务逻辑与1.12.2完全一致
     *
     * @param player 玩家 / player
     * @param targetEntity 目标实体 / target entity
     * @return 总攻击伤害 / total attack damage
     */
    public static float getAttackDamage(Player player, Entity targetEntity) {
        // ✅ 1.12.2: getEntityAttribute(ATTACK_DAMAGE).getAttributeValue()
        // ✅ 1.20.1: getAttributeValue(ATTACK_DAMAGE)
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        // ✅ 附魔额外伤害（API重命名但逻辑一致）
        // 1.12.2: getModifierForCreature() → 1.20.1: getDamageBonus()
        float otherDamage = 0;
        if (targetEntity instanceof LivingEntity livingEntity) {
            otherDamage = EnchantmentHelper.getDamageBonus(
                    player.getMainHandItem(),
                    livingEntity.getMobType()
            );
        } else {
            otherDamage = EnchantmentHelper.getDamageBonus(
                    player.getMainHandItem(),
                    MobType.UNDEFINED
            );
        }

        // ✅ 暴击判定（与1.12.2完全一致）
        boolean isCriticalHit = player.fallDistance > 0.0F
                && !player.onGround()
                && !player.onClimbable()
                && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.isPassenger()
                && targetEntity instanceof LivingEntity;
        isCriticalHit = isCriticalHit && !player.isSprinting();

        // ✅ Forge暴击事件（与1.12.2完全一致）
        // 1.20.1中ForgeHooks.getCriticalHit仍然存在
        CriticalHitEvent hitResult = ForgeHooks.getCriticalHit(
                player,
                targetEntity,
                isCriticalHit,
                isCriticalHit ? 1.5F : 1.0F
        );

        // ✅ 应用暴击倍率（与1.12.2完全一致）
        if (hitResult != null) {
            damage *= hitResult.getDamageModifier();
        }

        // ✅ 总伤害计算（与1.12.2完全一致）
        return damage + otherDamage;
    }
}