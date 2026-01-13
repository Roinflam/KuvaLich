package pers.roinflam.kuvalich.base.potion;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.kuvalich.utils.LogUtil;

import javax.annotation.Nonnull;

/**
 * 药水效果基类
 * Base class for potion effects
 *
 * 注意：1.20.1中Potion已重命名为MobEffect
 * Note: Potion renamed to MobEffect in 1.20.1
 */
public abstract class PotionBase extends MobEffect {

    private final String registryName;

    /**
     * 构造药水效果
     * Construct potion effect
     *
     * @param isBadEffectIn 是否为负面效果 / is bad effect
     * @param liquidColorIn 液体颜色（ARGB格式）/ liquid color (ARGB format)
     * @param name 注册名 / registry name
     */
    protected PotionBase(boolean isBadEffectIn, int liquidColorIn, @Nonnull String name) {
        // 1.20.1中使用MobEffectCategory替代boolean
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("药水注册名不能为空");
        }

        this.registryName = name;

        try {
            LogUtil.debug("药水效果注册成功: " + name +
                    " (负面效果: " + isBadEffectIn +
                    ", 颜色: 0x" + Integer.toHexString(liquidColorIn).toUpperCase() + ")");
        } catch (Exception e) {
            LogUtil.error("药水效果注册失败: " + name, e);
            throw new RuntimeException("无法注册药水效果: " + name, e);
        }
    }

    /**
     * 执行效果
     * Perform effect
     *
     * @param entity 目标实体 / target entity
     * @param amplifier 效果等级 / amplifier
     */
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // 默认无操作，子类可以重写
        // Default no operation, subclasses can override
    }

    /**
     * 是否应该执行效果
     * Whether should apply effect
     *
     * @param duration 剩余时间 / remaining duration
     * @param amplifier 效果等级 / amplifier
     * @return 是否执行 / whether to apply
     */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 默认每秒执行一次（性能友好）
        // Default execute once per second (performance friendly)
        return duration % 20 == 0;
    }

    /**
     * 获取注册名
     * Get registry name
     */
    public String getRegistryName() {
        return registryName;
    }
}