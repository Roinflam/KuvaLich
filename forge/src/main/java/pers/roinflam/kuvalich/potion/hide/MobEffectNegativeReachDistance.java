package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 触及距离降低效果
 * Reach distance decrease effect
 */
public class MobEffectNegativeReachDistance extends HideBase {

    // 颜色：深紫色 / Color: Dark purple
    private static final int COLOR = 0xFF4B0082;

    // UUID常量 / UUID constant
    private static final UUID REACH_DISTANCE_UUID = UUID.fromString("593af4a4-1610-40b9-8781-e4d6458b02ab");

    public MobEffectNegativeReachDistance() {
        super(true, COLOR, "negative_reach_distance");

        // 注册属性修改器（1.20.1中使用Forge的BLOCK_REACH）
        // Register attribute modifier (use Forge's BLOCK_REACH in 1.20.1)
        this.addAttributeModifier(ForgeMod.BLOCK_REACH.get(),
                REACH_DISTANCE_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        // 同时修改实体触及距离
        // Also modify entity reach distance
        this.addAttributeModifier(ForgeMod.ENTITY_REACH.get(),
                REACH_DISTANCE_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}