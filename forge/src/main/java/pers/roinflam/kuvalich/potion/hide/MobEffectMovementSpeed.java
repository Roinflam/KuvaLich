package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 移动速度增加效果
 * Movement speed increase effect
 */
public class MobEffectMovementSpeed extends HideBase {

    // 颜色：青色 / Color: Cyan
    private static final int COLOR = 0xFF00FFFF;

    // UUID常量 / UUID constant
    private static final UUID MOVEMENT_SPEED_UUID = UUID.fromString("668f048e-5669-4f2d-a173-31121de3eecd");

    public MobEffectMovementSpeed() {
        super(false, COLOR, "movement_speed");

        // 注册属性修改器 / Register attribute modifier
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                MOVEMENT_SPEED_UUID.toString(),
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}