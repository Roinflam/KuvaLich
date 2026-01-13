package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

/**
 * 毒素效果
 * Poison effect
 *
 * 效果：持续扣除生命值
 * Effect: Continuous health reduction
 */
public class MobEffectPoison extends HideBase {

    // 颜色：紫绿色 / Color: Purple-green
    private static final int COLOR = 0xFF8B008B;

    public MobEffectPoison() {
        super(true, COLOR, "poison");
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        entity.setHealth(entity.getHealth() - entity.getMaxHealth() * 0.0375f);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每30tick（1.5秒）执行一次
        return duration % 30 == 0;
    }
}