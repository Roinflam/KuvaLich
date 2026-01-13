package pers.roinflam.kuvalich.init;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import pers.roinflam.kuvalich.enchantment.*;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 附魔注册类（1.20.1版本）
 * Enchantment registration class (1.20.1 version)
 *
 * 注意：所有附魔类的构造函数都是无参的，参数在各自类内部定义
 * Note: All enchantment classes have parameterless constructors, parameters are defined internally
 */
public class KuvaLichEnchantments {

    /**
     * 附魔延迟注册器
     * Enchantment deferred register
     */
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Reference.MOD_ID);

    /**
     * 经验之运
     * Experience Collector
     *
     * 效果：挖掘方块时额外获得经验
     * Effect: Gain extra experience when mining blocks
     */
    public static final RegistryObject<Enchantment> EXPERIENCE_COLLECTOR =
            ENCHANTMENTS.register("experience_collector", EnchantmentExperienceCollector::new);

    /**
     * 灭骸
     * Requiem Destroyed
     *
     * 效果：
     * 1. 大幅加快安魂方块的挖掘速度
     * 2. 增加安魂矿石的经验掉落
     * 3. 增加安魂卡片和模组的掉落几率
     */
    public static final RegistryObject<Enchantment> REQUIEM_DESTROYED =
            ENCHANTMENTS.register("requiem_destroyed", EnchantmentRequiemDestroyed::new);

    /**
     * 斩杀
     * Lethal
     *
     * 效果：对生命值越低的敌人造成越高的额外伤害
     * Effect: Deal more damage to enemies with lower health
     */
    public static final RegistryObject<Enchantment> LETHAL =
            ENCHANTMENTS.register("lethal", EnchantmentLethal::new);

    /**
     * 先攻
     * First Strike
     *
     * 效果：对满血敌人造成额外伤害，并获得经验
     * Effect: Deal extra damage to enemies at full health and gain experience
     */
    public static final RegistryObject<Enchantment> FIRST_STRIKE =
            ENCHANTMENTS.register("first_strike", EnchantmentFirstStrike::new);

    /**
     * 护卫
     * Escort
     *
     * 效果：满血时大幅减少受到的伤害
     * Effect: Greatly reduce damage taken when at full health
     */
    public static final RegistryObject<Enchantment> ESCORT =
            ENCHANTMENTS.register("escort", EnchantmentEscort::new);

    /**
     * 死亡抵抗
     * Death Resistance
     *
     * 效果：提供对致命伤害的抵抗
     * Effect: Provide resistance to lethal damage
     */
    public static final RegistryObject<Enchantment> DEATH_RESISTANCE =
            ENCHANTMENTS.register("death_resistance", EnchantmentDeathResistance::new);
}