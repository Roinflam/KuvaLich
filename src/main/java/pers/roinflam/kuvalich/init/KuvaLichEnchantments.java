package pers.roinflam.kuvalich.init;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.kuvalich.enchantment.*;

import java.util.ArrayList;
import java.util.List;

public class KuvaLichEnchantments {
    public static final List<Enchantment> ENCHANTMENTS = new ArrayList<Enchantment>();

    public static final EnchantmentExperienceCollector EXPERIENCE_COLLECTOR = new EnchantmentExperienceCollector(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.DIGGER,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentRequiemDestroyed REQUIEM_DESTROYED = new EnchantmentRequiemDestroyed(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.DIGGER,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentLethal LETHAL = new EnchantmentLethal(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentFirstStrike FIRST_STRIKE = new EnchantmentFirstStrike(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.WEAPON,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}
    );
    public static final EnchantmentEscort ESCORT = new EnchantmentEscort(
            Enchantment.Rarity.RARE,
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
    public static final EnchantmentDeathResistance DEATH_RESISTANCE = new EnchantmentDeathResistance(
            Enchantment.Rarity.VERY_RARE,
            EnumEnchantmentType.ARMOR,
            new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
    );
}
