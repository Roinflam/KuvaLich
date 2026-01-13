package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.common.ForgeMod;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 触及距离增加效果
 * Reach distance increase effect
 *
 * 注意：1.20.1中使用ForgeMod.BLOCK_REACH和ENTITY_REACH
 * Note: In 1.20.1 use ForgeMod.BLOCK_REACH and ENTITY_REACH
 */
public class MobEffectReachDistance extends HideBase {

    // 颜色：紫色 / Color: Purple
    private static final int COLOR = 0xFF800080;

    // UUID常量 / UUID constant
    private static final UUID REACH_DISTANCE_UUID = UUID.fromString("a4460772-4f4d-4f3d-8a4f-f613956f5688");

    public MobEffectReachDistance() {
        super(false, COLOR, "reach_distance");

        // 注册属性修改器（1.20.1中使用Forge的BLOCK_REACH）
        // Register attribute modifier (use Forge's BLOCK_REACH in 1.20.1)
        this.addAttributeModifier(ForgeMod.BLOCK_REACH.get(),
                REACH_DISTANCE_UUID.toString(),
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        // 同时修改实体触及距离
        // Also modify entity reach distance
        this.addAttributeModifier(ForgeMod.ENTITY_REACH.get(),
                REACH_DISTANCE_UUID.toString(),
                0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}