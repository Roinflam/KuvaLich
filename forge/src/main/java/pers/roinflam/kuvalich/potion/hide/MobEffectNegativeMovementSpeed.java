package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 移动速度降低效果
 * Movement speed decrease effect
 */
public class MobEffectNegativeMovementSpeed extends HideBase {

    // 颜色：深青色 / Color: Dark cyan
    private static final int COLOR = 0xFF008B8B;

    // UUID常量 / UUID constant
    private static final UUID MOVEMENT_SPEED_UUID = UUID.fromString("d7c96c9d-8be3-4711-af76-605d3e8eb3f7");

    public MobEffectNegativeMovementSpeed() {
        super(true, COLOR, "negative_movement_speed");

        // 注册属性修改器 / Register attribute modifier
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                MOVEMENT_SPEED_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}