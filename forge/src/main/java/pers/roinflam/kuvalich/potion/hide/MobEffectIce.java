package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectIce extends HideBase {

    public MobEffectIce(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "ice");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "d689c26c-5a98-1eb9-90ab-0c014b0f1875", -0.01, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "a8ad62d9-ffa8-ccd1-b93f-b6dc3ceb2602", -0.01, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.FLYING_SPEED, "09d42c58-64d7-d9e9-00d4-59a0cf236283", -0.01, 2);
    }

}
