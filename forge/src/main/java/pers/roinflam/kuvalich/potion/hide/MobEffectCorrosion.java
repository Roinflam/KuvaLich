package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 腐蚀效果
 * Corrosion effect
 *
 * 效果：降低护甲和护甲韧性
 * Effect: Reduce armor and armor toughness
 */
public class MobEffectCorrosion extends HideBase {

    // 颜色：棕绿色 / Color: Brown-green
    private static final int COLOR = 0xFF556B2F;

    // UUID常量 / UUID constants
    private static final UUID ARMOR_UUID = UUID.fromString("24bd151e-db46-5104-a839-2f622ee2b7b7");
    private static final UUID ARMOR_TOUGHNESS_UUID = UUID.fromString("73f4e5dc-7089-33f4-4167-6af8c0b9a71e");

    public MobEffectCorrosion() {
        super(true, COLOR, "corrosion");

        // 注册属性修改器 / Register attribute modifiers
        this.addAttributeModifier(Attributes.ARMOR,
                ARMOR_UUID.toString(),
                -0.2,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS,
                ARMOR_TOUGHNESS_UUID.toString(),
                -0.2,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}