package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectPuncture extends HideBase {
    public MobEffectPuncture(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "puncture");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_DAMAGE, "f9f7ca3a-efb5-9198-4d57-fb867b06a861", -0.4, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "dcf8e47c-7bfe-fc43-fc4b-0c54f0d2cce0", -0.4, 2);
    }

}
