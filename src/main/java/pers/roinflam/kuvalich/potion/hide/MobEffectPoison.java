package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


public class MobEffectPoison extends HideBase {
    public MobEffectPoison(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "poison");
    }

    @Override
    public void performEffect(@NotNull EntityLivingBase entityLivingBaseIn, int amplifier) {
        entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - entityLivingBaseIn.getMaxHealth() * 0.0375f);
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 30 == 0;
    }

}
