package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectNegativeKnockbackResistance extends HideBase {
    public MobEffectNegativeKnockbackResistance(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "negative_knockback_resistance");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, "5612ee20-975d-b1de-2c5c-fe08c0e07fd8", -0.1, 2);
    }

}
