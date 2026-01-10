package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectNegativeReachDistance extends HideBase {
    public MobEffectNegativeReachDistance(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "negative_reach_distance");

        this.registerPotionAttributeModifier(EntityPlayer.REACH_DISTANCE, "593af4a4-1610-40b9-8781-e4d6458b02ab", -0.1, 2);
    }

}
