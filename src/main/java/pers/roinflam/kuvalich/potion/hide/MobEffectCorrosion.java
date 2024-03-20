package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectCorrosion extends HideBase {
    public MobEffectCorrosion(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "corrosion");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "24bd151e-db46-5104-a839-2f622ee2b7b7", -0.2, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "73f4e5dc-7089-33f4-4167-6af8c0b9a71e", -0.2, 2);
    }


}
