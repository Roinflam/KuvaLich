package pers.roinflam.kuvalich.base.potion.hide;

import net.minecraft.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.potion.PotionBase;

public abstract class HideBase extends PotionBase {

    protected HideBase(boolean isBadEffectIn, int liquidColorIn, @NotNull String name) {
        super(isBadEffectIn, liquidColorIn, name);
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean shouldRender(PotionEffect effect) {
        return false;
    }

    @Override
    public boolean shouldRenderInvText(PotionEffect effect) {
        return false;
    }

    @Override
    public boolean shouldRenderHUD(PotionEffect effect) {
        return false;
    }

}
