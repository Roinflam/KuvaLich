package pers.roinflam.kuvalich.mixin.tacz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * EntityKineticBullet 的字段访问器。
 * 用于从其他 Mixin 类中访问 private 字段。
 */
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public interface EntityKineticBulletAccessor {

    /**
     * 获取子弹伤害系数。
     * TACZ getDamage() 最终返回：damage × damageModifier
     *
     * @return 伤害系数
     */
    @Accessor("damageModifier")
    float getDamageModifier();

    /**
     * 设置子弹伤害系数。
     * TACZ 原始逻辑：散弹枪按弹丸数均分伤害（damageModifier = 1/bulletCount）。
     *
     * @param damageModifier 伤害系数
     */
    @Accessor("damageModifier")
    void setDamageModifier(float damageModifier);
}