package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectReachDistance extends HideBase {
    public MobEffectReachDistance(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "reach_distance");

        this.registerPotionAttributeModifier(EntityPlayer.REACH_DISTANCE, "a4460772-4f4d-4f3d-8a4f-f613956f5688", 0.1, 2);
    }

}
