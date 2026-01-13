package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 穿刺效果
 * Puncture effect
 *
 * 效果：降低攻击伤害和攻击速度
 * Effect: Reduce attack damage and attack speed
 */
public class MobEffectPuncture extends HideBase {

    // 颜色：深红色 / Color: Dark red
    private static final int COLOR = 0xFF8B0000;

    // UUID常量 / UUID constants
    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("f9f7ca3a-efb5-9198-4d57-fb867b06a861");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("dcf8e47c-7bfe-fc43-fc4b-0c54f0d2cce0");

    public MobEffectPuncture() {
        super(true, COLOR, "puncture");

        // 注册属性修改器 / Register attribute modifiers
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                ATTACK_DAMAGE_UUID.toString(),
                -0.4,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ATTACK_SPEED,
                ATTACK_SPEED_UUID.toString(),
                -0.4,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}