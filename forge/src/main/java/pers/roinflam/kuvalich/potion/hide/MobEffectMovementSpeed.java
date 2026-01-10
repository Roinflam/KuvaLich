package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectMovementSpeed extends HideBase {
    public MobEffectMovementSpeed(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "movement_speed");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "668f048e-5669-4f2d-a173-31121de3eecd", 0.1, 2);
    }

}
