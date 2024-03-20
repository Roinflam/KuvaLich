package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectKnockbackResistance extends HideBase {
    public MobEffectKnockbackResistance(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "knockback_resistance");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, "045a487e-3764-4a6c-b37e-f55e59a45535", 0.1, 0);
    }

}
