package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 击退抗性降低效果
 * Knockback resistance decrease effect
 */
public class MobEffectNegativeKnockbackResistance extends HideBase {

    // 颜色：深棕色 / Color: Dark brown
    private static final int COLOR = 0xFF654321;

    // UUID常量 / UUID constant
    private static final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("5612ee20-975d-b1de-2c5c-fe08c0e07fd8");

    public MobEffectNegativeKnockbackResistance() {
        super(true, COLOR, "negative_knockback_resistance");

        // 注册属性修改器（使用ADDITION模式）
        // Register attribute modifier (using ADDITION mode)
        this.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE,
                KNOCKBACK_RESISTANCE_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.ADDITION);
    }
}