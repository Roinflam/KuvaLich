package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectHealth extends HideBase {
    public MobEffectHealth(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "health");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MAX_HEALTH, "58a0e999-d0c7-4832-8bc5-e3865bcce871", 0.1, 2);
    }

}
