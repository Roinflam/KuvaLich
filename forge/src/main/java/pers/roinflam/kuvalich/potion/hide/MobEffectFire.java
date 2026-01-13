package pers.roinflam.kuvalich.potion.hide;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import pers.roinflam.kuvalich.base.potion.hide.HideBase;

import java.util.UUID;

/**
 * 火焰效果
 * Fire effect
 *
 * 效果：持续火焰伤害 + 降低护甲
 * Effect: Continuous fire damage + Reduce armor
 */
public class MobEffectFire extends HideBase {

    // 颜色：橙红色 / Color: Orange-red
    private static final int COLOR = 0xFFFF4500;

    // UUID常量 / UUID constants
    private static final UUID ARMOR_UUID = UUID.fromString("35999521-78bc-9f76-6449-2f2c3d1d02e6");
    private static final UUID ARMOR_TOUGHNESS_UUID = UUID.fromString("6e85e3ef-5533-45f1-532a-4f48fb5370bd");

    public MobEffectFire() {
        super(true, COLOR, "fire");

        // 注册属性修改器 / Register attribute modifiers
        // 1.20.1中使用addAttributeModifier
        this.addAttributeModifier(Attributes.ARMOR,
                ARMOR_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);

        this.addAttributeModifier(Attributes.ARMOR_TOUGHNESS,
                ARMOR_TOUGHNESS_UUID.toString(),
                -0.1,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // 检查是否有吸收伤害（伤害盾）
        if (entity.getAbsorptionAmount() > 0) {
            entity.hurt(entity.damageSources().inFire(), entity.getMaxHealth() * 0.01875f);
        } else {
            entity.hurt(entity.damageSources().inFire(), entity.getMaxHealth() * 0.0375f);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每30tick（1.5秒）执行一次
        return duration % 30 == 0;
    }
}