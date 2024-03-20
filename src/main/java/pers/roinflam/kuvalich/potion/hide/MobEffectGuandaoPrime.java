package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectGuandaoPrime extends HideBase {
    public MobEffectGuandaoPrime(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "guandao_prime");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "6618726d-bbc7-4abf-6585-513b401e96e8", 0.01, 2);
    }

}
