package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 护甲增加效果
 * Armor increase effect
 */
public class MobEffectArmor extends HideBase {

    // 颜色：银色 / Color: Silver
    private static final int COLOR = 0xFFC0C0C0;

    // UUID常量 / UUID constants
    private static final UUID ARMOR_UUID = UUID.fromString("526ce451-3186-4ad8-9875-eac501ca6973");
    private static final UUID ARMOR_TOUGHNESS_UUID = UUID.fromString("96cfa4a3-4759-49b9-bcc3-5649e0f9329a");

    public MobEffectArmor() {
        super(false, COLOR, "armor");

        // 注册属性修改器 / Register attribute modifiers
        this.addAttributeModifier(Attributes.ARMOR,
                ARMOR_UUID.toString(),
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS,
                ARMOR_TOUGHNESS_UUID.toString(),
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}