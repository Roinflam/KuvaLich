package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectNegativeAttackSpeed extends HideBase {
    public MobEffectNegativeAttackSpeed(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "negative_attack_speed");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "cc2c413f-a688-c135-335d-f8c0cb2a890d", -0.1, 2);
    }

}
