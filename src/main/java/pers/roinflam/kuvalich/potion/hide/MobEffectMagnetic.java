package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectMagnetic extends HideBase {
    public MobEffectMagnetic(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "magnetic");
    }

    @Override
    public void performEffect(@NotNull EntityLivingBase entityLivingBaseIn, int amplifier) {
        if (entityLivingBaseIn.getAbsorptionAmount() > 0) {
            entityLivingBaseIn.setAbsorptionAmount(0);
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 30 == 0;
    }

}
