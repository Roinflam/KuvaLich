package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectNegativeArmor extends HideBase {
    public MobEffectNegativeArmor(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "negative_armor");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "a87e8a34-c4bc-469d-9998-5ee2b7981582", -0.1, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "5477e61e-19a6-4c54-9df9-1b0c4a09a411", -0.1, 2);
    }

}
