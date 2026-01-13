package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 生命值增加效果
 * Health increase effect
 */
public class MobEffectHealth extends HideBase {

    // 颜色：红色 / Color: Red
    private static final int COLOR = 0xFFFF0000;

    // UUID常量 / UUID constant
    private static final UUID MAX_HEALTH_UUID = UUID.fromString("58a0e999-d0c7-4832-8bc5-e3865bcce871");

    public MobEffectHealth() {
        super(false, COLOR, "health");

        // 注册属性修改器 / Register attribute modifier
        this.addAttributeModifier(Attributes.MAX_HEALTH,
                MAX_HEALTH_UUID.toString(),
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}