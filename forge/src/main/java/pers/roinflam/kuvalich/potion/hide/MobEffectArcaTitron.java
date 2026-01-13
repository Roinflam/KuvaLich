package pers.roinflam.kuvalich.potion.hide;

import pers.roinflam.kuvalich.base.potion.hide.HideBase;

/**
 * 弧电振子锤效果
 * Arca Titron effect
 *
 * 效果：用于标记，无实际属性修改
 * Effect: Used for marking, no actual attribute modification
 */
public class MobEffectArcaTitron extends HideBase {

    // 颜色：电蓝色 / Color: Electric blue
    private static final int COLOR = 0xFF1E90FF;

    public MobEffectArcaTitron() {
        super(false, COLOR, "arca_titron");
    }
}