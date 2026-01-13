package pers.roinflam.kuvalich.init;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import pers.roinflam.kuvalich.potion.hide.*;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 药水效果注册类
 * MobEffect registration class
 *
 * 注意：1.20.1中Potion已重命名为MobEffect
 * Note: Potion renamed to MobEffect in 1.20.1
 */
public class KuvaLichMobEffects {

    /**
     * 药水效果延迟注册器
     * MobEffect deferred register
     */
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Reference.MOD_ID);

    // ========== 元素效果 / Elemental Effects ==========

    public static final RegistryObject<MobEffect> FIRE = MOB_EFFECTS.register("fire",
            MobEffectFire::new);

    public static final RegistryObject<MobEffect> ICE = MOB_EFFECTS.register("ice",
            MobEffectIce::new);

    public static final RegistryObject<MobEffect> POISON = MOB_EFFECTS.register("poison_effect",
            MobEffectPoison::new);

    // ========== 武器特效 / Weapon Effects ==========

    public static final RegistryObject<MobEffect> GUANDAO_PRIME = MOB_EFFECTS.register("guandao_prime",
            MobEffectGuandaoPrime::new);

    public static final RegistryObject<MobEffect> ARCA_TITRON = MOB_EFFECTS.register("arca_titron",
            MobEffectArcaTitron::new);

    public static final RegistryObject<MobEffect> VITRICA = MOB_EFFECTS.register("vitrica",
            MobEffectVitrica::new);

    public static final RegistryObject<MobEffect> MAGNETIC = MOB_EFFECTS.register("magnetic",
            MobEffectMagnetic::new);

    public static final RegistryObject<MobEffect> PUNCTURE = MOB_EFFECTS.register("puncture",
            MobEffectPuncture::new);

    public static final RegistryObject<MobEffect> CORROSION = MOB_EFFECTS.register("corrosion",
            MobEffectCorrosion::new);

    public static final RegistryObject<MobEffect> VIRUS = MOB_EFFECTS.register("virus",
            MobEffectVirus::new);

    // ========== 隐藏属性效果 / Hidden Attribute Effects ==========

    public static final RegistryObject<MobEffect> ATTACK_SPEED = MOB_EFFECTS.register("attack_speed_effect",
            MobEffectAttackSpeed::new);

    public static final RegistryObject<MobEffect> NEGATIVE_ATTACK_SPEED = MOB_EFFECTS.register("negative_attack_speed",
            MobEffectNegativeAttackSpeed::new);

    public static final RegistryObject<MobEffect> HEALTH = MOB_EFFECTS.register("health_effect",
            MobEffectHealth::new);

    public static final RegistryObject<MobEffect> NEGATIVE_HEALTH = MOB_EFFECTS.register("negative_health",
            MobEffectNegativeHealth::new);

    public static final RegistryObject<MobEffect> ARMOR = MOB_EFFECTS.register("armor_effect",
            MobEffectArmor::new);

    public static final RegistryObject<MobEffect> NEGATIVE_ARMOR = MOB_EFFECTS.register("negative_armor",
            MobEffectNegativeArmor::new);

    public static final RegistryObject<MobEffect> MOVEMENT_SPEED = MOB_EFFECTS.register("movement_speed_effect",
            MobEffectMovementSpeed::new);

    public static final RegistryObject<MobEffect> NEGATIVE_MOVEMENT_SPEED = MOB_EFFECTS.register("negative_movement_speed",
            MobEffectNegativeMovementSpeed::new);

    public static final RegistryObject<MobEffect> KNOCKBACK_RESISTANCE = MOB_EFFECTS.register("knockback_resistance_effect",
            MobEffectKnockbackResistance::new);

    public static final RegistryObject<MobEffect> NEGATIVE_KNOCKBACK_RESISTANCE = MOB_EFFECTS.register("negative_knockback_resistance",
            MobEffectNegativeKnockbackResistance::new);

    public static final RegistryObject<MobEffect> REACH_DISTANCE = MOB_EFFECTS.register("reach_distance_effect",
            MobEffectReachDistance::new);

    public static final RegistryObject<MobEffect> NEGATIVE_REACH_DISTANCE = MOB_EFFECTS.register("negative_reach_distance",
            MobEffectNegativeReachDistance::new);
}