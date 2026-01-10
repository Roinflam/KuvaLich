package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectNegativeHealth extends HideBase {
    public MobEffectNegativeHealth(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "negative_health");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MAX_HEALTH, "ac5c03ba-a248-4ca2-b87e-6fc2b876e3b9", -0.1, 2);
    }

}
