package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 病毒效果
 * Virus effect
 *
 * 效果：受到的伤害增加
 * Effect: Increase damage taken
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class MobEffectVirus extends HideBase {

    // 颜色：深绿色 / Color: Dark green
    private static final int COLOR = 0xFF006400;

    public MobEffectVirus() {
        super(true, COLOR, "virus");
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity target = evt.getEntity();
        if (target == null || !target.hasEffect(pers.roinflam.kuvalich.init.KuvaLichMobEffects.VIRUS.get())) {
            return;
        }

        int amplifier = target.getEffect(pers.roinflam.kuvalich.init.KuvaLichMobEffects.VIRUS.get()).getAmplifier();
        evt.setAmount(evt.getAmount() + evt.getAmount() * 0.25f * (amplifier + 1));
    }
}