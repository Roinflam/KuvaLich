package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 生命值降低效果
 * Health decrease effect
 */
public class MobEffectNegativeHealth extends HideBase {

    // 颜色：暗红色 / Color: Dark red
    private static final int COLOR = 0xFF800000;

    // UUID常量 / UUID constant
    private static final UUID MAX_HEALTH_UUID = UUID.fromString("ac5c03ba-a248-4ca2-b87e-6fc2b876e3b9");

    public MobEffectNegativeHealth() {
        super(true, COLOR, "negative_health");

        // 注册属性修改器 / Register attribute modifier
        this.addAttributeModifier(Attributes.MAX_HEALTH,
                MAX_HEALTH_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}