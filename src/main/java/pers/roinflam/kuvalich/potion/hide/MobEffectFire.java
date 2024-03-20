package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import javax.annotation.Nonnull;


public class MobEffectFire extends HideBase {
    public MobEffectFire(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "fire");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "35999521-78bc-9f76-6449-2f2c3d1d02e6", -0.1, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "6e85e3ef-5533-45f1-532a-4f48fb5370bd", -0.1, 2);
    }

    @Override
    public void performEffect(@NotNull EntityLivingBase entityLivingBaseIn, int amplifier) {
        if(entityLivingBaseIn.getAbsorptionAmount() > 0){
            entityLivingBaseIn.attackEntityFrom(DamageSource.IN_FIRE, entityLivingBaseIn.getMaxHealth() * 0.01875f);
        } else {
            entityLivingBaseIn.attackEntityFrom(DamageSource.IN_FIRE, entityLivingBaseIn.getMaxHealth() * 0.0375f);
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 30 == 0;
    }

}
