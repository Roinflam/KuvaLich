package pers.roinflam.kuvalich.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 死亡抵抗附魔
 * Death Resistance Enchantment
 *
 * 效果：提供对致命伤害的抵抗
 * Effect: Provide resistance to lethal damage
 *
 * 注意：具体的死亡抵抗逻辑需要在LivingDeathEvent中实现
 * Note: Specific death resistance logic needs to be implemented in LivingDeathEvent
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentDeathResistance extends EnchantmentBase {

    // 常量定义 / Constants
    private static final int MAX_LEVEL = 3;
    private static final int BASE_ENCHANTABILITY = 25;
    private static final int ENCHANTABILITY_PER_LEVEL = 25;

    public EnchantmentDeathResistance() {
        super(Enchantment.Rarity.VERY_RARE,
                EnchantmentCategory.ARMOR,
                new EquipmentSlot[]{
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET
                },
                "death_resistance");
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return BASE_ENCHANTABILITY + (enchantmentLevel - 1) * ENCHANTABILITY_PER_LEVEL;
    }
}