package pers.roinflam.kuvalich.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentDeathResistance extends EnchantmentBase {

    public EnchantmentDeathResistance(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot [] slots) {
        super(rarityIn, typeIn, slots, "death_resistance");
    }

    public static Enchantment getEnchantment() {
        return KuvaLichEnchantments.DEATH_RESISTANCE;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 25 + (enchantmentLevel - 1) * 25;
    }
}