package pers.roinflam.kuvalich.utils.util;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EntityPlayerUtil {

    public static float getAttackDamage(@NotNull EntityPlayer entityPlayer, Entity targetEntity) {
        float damage = (float) entityPlayer.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        float otherDamage = 0;
        if (targetEntity instanceof EntityLivingBase) {
            otherDamage = EnchantmentHelper.getModifierForCreature(entityPlayer.getHeldItemMainhand(), ((EntityLivingBase) targetEntity).getCreatureAttribute());
        } else {
            otherDamage = EnchantmentHelper.getModifierForCreature(entityPlayer.getHeldItemMainhand(), EnumCreatureAttribute.UNDEFINED);
        }

        boolean isCriticalHit = entityPlayer.fallDistance > 0.0F && !entityPlayer.onGround && !entityPlayer.isOnLadder() && !entityPlayer.isInWater() && !entityPlayer.isPotionActive(MobEffects.BLINDNESS) && !entityPlayer.isRiding() && targetEntity instanceof EntityLivingBase;
        isCriticalHit = isCriticalHit && !entityPlayer.isSprinting();

        net.minecraftforge.event.entity.player.@Nullable CriticalHitEvent hitResult = net.minecraftforge.common.ForgeHooks.getCriticalHit(entityPlayer, targetEntity, isCriticalHit, isCriticalHit ? 1.5F : 1.0F);
        if (hitResult != null) {
            damage *= hitResult.getDamageModifier();
        }

        return damage + otherDamage;
    }

}
