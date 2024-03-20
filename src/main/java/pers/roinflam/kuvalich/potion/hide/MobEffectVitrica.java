package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectVitrica extends HideBase {

    public MobEffectVitrica(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "vitrica");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_DAMAGE, "54e6650c-bbb5-e71b-c23b-05b914976ddc", -0.075, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "54e6650c-bbb5-e71b-c23b-05b914976ddc", -0.075, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "54e6650c-bbb5-e71b-c23b-05b914976ddc", -0.075, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "1d2a4d76-3443-62d4-2288-3e8767921e5c", -0.075, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, "d689c26c-5a98-1eb9-90ab-0c014b0f1875", -0.075, 2);
    }

}
