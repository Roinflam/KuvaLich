package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

/**
 * 磁力效果
 * Magnetic effect
 *
 * 效果：清除护盾
 * Effect: Remove shields
 */
public class MobEffectMagnetic extends HideBase {

    // 颜色：蓝灰色 / Color: Blue-gray
    private static final int COLOR = 0xFF5F9EA0;

    public MobEffectMagnetic() {
        super(true, COLOR, "magnetic");
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.getAbsorptionAmount() > 0) {
            entity.setAbsorptionAmount(0);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每30tick（1.5秒）执行一次
        return duration % 30 == 0;
    }
}