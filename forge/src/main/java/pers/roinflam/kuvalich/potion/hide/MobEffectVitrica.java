package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 金璃剑效果
 * Vitrica effect
 *
 * 效果：全属性降低
 * Effect: Reduce all attributes
 */
public class MobEffectVitrica extends HideBase {

    // 颜色：水晶色 / Color: Crystal
    private static final int COLOR = 0xFFE0FFFF;

    // UUID常量 / UUID constants
    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("54e6650c-bbb5-e71b-c23b-05b914976ddc");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("54e6650c-bbb5-e71b-c23b-05b914976ddc");
    private static final UUID ARMOR_UUID = UUID.fromString("54e6650c-bbb5-e71b-c23b-05b914976ddc");
    private static final UUID ARMOR_TOUGHNESS_UUID = UUID.fromString("1d2a4d76-3443-62d4-2288-3e8767921e5c");
    private static final UUID MOVEMENT_SPEED_UUID = UUID.fromString("d689c26c-5a98-1eb9-90ab-0c014b0f1875");

    public MobEffectVitrica() {
        super(true, COLOR, "vitrica");

        // 注册属性修改器 / Register attribute modifiers
        this.addAttributeModifier(Attributes.ATTACK_DAMAGE,
                ATTACK_DAMAGE_UUID.toString(),
                -0.075,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ATTACK_SPEED,
                ATTACK_SPEED_UUID.toString(),
                -0.075,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ARMOR,
                ARMOR_UUID.toString(),
                -0.075,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS,
                ARMOR_TOUGHNESS_UUID.toString(),
                -0.075,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.MOVEMENT_SPEED,
                MOVEMENT_SPEED_UUID.toString(),
                -0.075,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}