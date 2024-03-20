package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectNegativeMovementSpeed extends HideBase {
    public MobEffectNegativeMovementSpeed(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "negative_movement_speed");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "d7c96c9d-8be3-4711-af76-605d3e8eb3f7", -0.1, 2);
    }

}
