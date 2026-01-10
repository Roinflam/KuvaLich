package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;


@Mod.EventBusSubscriber
public class MobEffectVirus extends HideBase {
    public MobEffectVirus(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "virus");
    }


    @SubscribeEvent
    public void onLivingAttack(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(this)) {
                int amplifier = healer.getActivePotionEffect(this).getAmplifier();
                evt.setAmount(evt.getAmount() + evt.getAmount() * 0.25f * (amplifier + 1));
            }
        }
    }
}
