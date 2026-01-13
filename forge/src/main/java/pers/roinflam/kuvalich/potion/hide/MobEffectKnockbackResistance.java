package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 击退抗性增加效果
 * Knockback resistance increase effect
 */
public class MobEffectKnockbackResistance extends HideBase {

    // 颜色：棕色 / Color: Brown
    private static final int COLOR = 0xFFA52A2A;

    // UUID常量 / UUID constant
    private static final UUID KNOCKBACK_RESISTANCE_UUID = UUID.fromString("045a487e-3764-4a6c-b37e-f55e59a45535");

    public MobEffectKnockbackResistance() {
        super(false, COLOR, "knockback_resistance");

        // 注册属性修改器（使用ADDITION模式）
        // Register attribute modifier (using ADDITION mode)
        this.addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE,
                KNOCKBACK_RESISTANCE_UUID.toString(),
                0.1,
                AttributeModifier.Operation.ADDITION);
    }
}