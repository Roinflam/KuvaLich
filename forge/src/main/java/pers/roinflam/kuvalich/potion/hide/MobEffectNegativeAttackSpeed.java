package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 攻击速度降低效果
 * Attack speed decrease effect
 */
public class MobEffectNegativeAttackSpeed extends HideBase {

    // 颜色：橙色 / Color: Orange
    private static final int COLOR = 0xFFFFA500;

    // UUID常量 / UUID constant
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("cc2c413f-a688-c135-335d-f8c0cb2a890d");

    public MobEffectNegativeAttackSpeed() {
        super(true, COLOR, "negative_attack_speed");

        // 注册属性修改器 / Register attribute modifier
        this.addAttributeModifier(Attributes.ATTACK_SPEED,
                ATTACK_SPEED_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}