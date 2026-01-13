package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 冰冻效果
 * Ice effect
 *
 * 效果：降低移动速度 + 降低攻击速度 + 降低飞行速度
 * Effect: Reduce movement speed + Reduce attack speed + Reduce flying speed
 */
public class MobEffectIce extends HideBase {

    // 颜色：冰蓝色 / Color: Ice blue
    private static final int COLOR = 0xFF00FFFF;

    // UUID常量 / UUID constants
    private static final UUID MOVEMENT_SPEED_UUID = UUID.fromString("d689c26c-5a98-1eb9-90ab-0c014b0f1875");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("a8ad62d9-ffa8-ccd1-b93f-b6dc3ceb2602");
    private static final UUID FLYING_SPEED_UUID = UUID.fromString("09d42c58-64d7-d9e9-00d4-59a0cf236283");

    public MobEffectIce() {
        super(true, COLOR, "ice");

        // 注册属性修改器 / Register attribute modifiers
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                MOVEMENT_SPEED_UUID.toString(),
                -0.01,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ATTACK_SPEED,
                ATTACK_SPEED_UUID.toString(),
                -0.01,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.FLYING_SPEED,
                FLYING_SPEED_UUID.toString(),
                -0.01,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}