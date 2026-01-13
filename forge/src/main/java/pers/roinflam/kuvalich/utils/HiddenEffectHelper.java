package pers.roinflam.kuvalich.utils;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 隐藏效果辅助工具类
 * Hidden effect helper utility
 *
 * 用于简化隐藏效果的应用
 * Used to simplify applying hidden effects
 */
public class HiddenEffectHelper {

    /**
     * 应用隐藏效果（不显示粒子和图标）
     * Apply hidden effect (no particles and no icon)
     *
     * @param entity 目标实体 / target entity
     * @param effect 效果 / effect
     * @param duration 持续时间(tick) / duration in ticks
     * @param amplifier 效果等级 / amplifier level
     */
    public static void apply(LivingEntity entity, MobEffect effect, int duration, int amplifier) {
        entity.addEffect(new MobEffectInstance(
                effect,
                duration,
                amplifier,
                false,  // ambient - 环境效果
                false,  // visible - 不显示粒子
                false   // showIcon - 不显示图标
        ));
    }

    /**
     * 应用隐藏效果（等级默认为0）
     * Apply hidden effect (default amplifier 0)
     */
    public static void apply(LivingEntity entity, MobEffect effect, int duration) {
        apply(entity, effect, duration, 0);
    }

    /**
     * 批量应用多个隐藏效果
     * Batch apply multiple hidden effects
     *
     * @param entity 目标实体 / target entity
     * @param effects 效果数组 / effects array
     * @param duration 持续时间(tick) / duration in ticks
     * @param amplifier 效果等级 / amplifier level
     */
    public static void applyMultiple(LivingEntity entity, MobEffect[] effects, int duration, int amplifier) {
        for (MobEffect effect : effects) {
            apply(entity, effect, duration, amplifier);
        }
    }

    /**
     * 检查实体是否有指定的隐藏效果
     * Check if entity has specified hidden effect
     */
    public static boolean hasEffect(LivingEntity entity, MobEffect effect) {
        return entity.hasEffect(effect);
    }

    /**
     * 移除隐藏效果
     * Remove hidden effect
     */
    public static void remove(LivingEntity entity, MobEffect effect) {
        entity.removeEffect(effect);
    }
}