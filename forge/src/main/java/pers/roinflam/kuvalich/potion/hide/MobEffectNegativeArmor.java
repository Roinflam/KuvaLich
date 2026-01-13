package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 护甲降低效果
 * Armor decrease effect
 */
public class MobEffectNegativeArmor extends HideBase {

    // 颜色：灰色 / Color: Gray
    private static final int COLOR = 0xFF808080;

    // UUID常量 / UUID constants
    private static final UUID ARMOR_UUID = UUID.fromString("a87e8a34-c4bc-469d-9998-5ee2b7981582");
    private static final UUID ARMOR_TOUGHNESS_UUID = UUID.fromString("5477e61e-19a6-4c54-9df9-1b0c4a09a411");

    public MobEffectNegativeArmor() {
        super(true, COLOR, "negative_armor");

        // 注册属性修改器 / Register attribute modifiers
        this.addAttributeModifier(Attributes.ARMOR,
                ARMOR_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS,
                ARMOR_TOUGHNESS_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}