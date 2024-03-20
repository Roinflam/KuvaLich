package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.DamageSource;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectAttackSpeed extends HideBase {
    public MobEffectAttackSpeed(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "attack_speed");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "9d273c20-b591-cb58-3a87-68818010a6d3", 0.1, 2);
    }

}
