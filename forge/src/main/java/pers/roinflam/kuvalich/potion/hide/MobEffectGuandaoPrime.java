package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 关刀Prime效果
 * Guandao Prime effect
 *
 * 效果：提升攻击速度
 * Effect: Increase attack speed
 */
public class MobEffectGuandaoPrime extends HideBase {

    // 颜色：金色 / Color: Gold
    private static final int COLOR = 0xFFFFD700;

    // UUID常量 / UUID constant
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("6618726d-bbc7-4abf-6585-513b401e96e8");

    public MobEffectGuandaoPrime() {
        super(false, COLOR, "guandao_prime");

        // 注册属性修改器 / Register attribute modifier
        this.addAttributeModifier(Attributes.ATTACK_SPEED,
                ATTACK_SPEED_UUID.toString(),
                0.01,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}