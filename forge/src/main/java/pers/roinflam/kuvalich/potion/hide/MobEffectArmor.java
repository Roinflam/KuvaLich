package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.SharedMonsterAttributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectArmor extends HideBase {
    public MobEffectArmor(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "armor");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "526ce451-3186-4ad8-9875-eac501ca6973", 0.1, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "96cfa4a3-4759-49b9-bcc3-5649e0f9329a", 0.1, 2);
    }

}
