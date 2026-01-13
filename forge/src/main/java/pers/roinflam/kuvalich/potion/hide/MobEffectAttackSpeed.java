package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 攻击速度增加效果
 * Attack speed increase effect
 */
public class MobEffectAttackSpeed extends HideBase {

    // 颜色：黄色 / Color: Yellow
    private static final int COLOR = 0xFFFFFF00;

    // UUID常量 / UUID constant
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("9d273c20-b591-cb58-3a87-68818010a6d3");

    public MobEffectAttackSpeed() {
        super(false, COLOR, "attack_speed");

        // 注册属性修改器 / Register attribute modifier
        this.addAttributeModifier(Attributes.ATTACK_SPEED,
                ATTACK_SPEED_UUID.toString(),
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}