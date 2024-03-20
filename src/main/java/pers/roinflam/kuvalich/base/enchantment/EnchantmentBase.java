package pers.roinflam.kuvalich.base.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.utils.util.EnchantmentUtil;

public abstract class EnchantmentBase extends Enchantment {

    protected EnchantmentBase(@NotNull Rarity rarityIn, @NotNull EnumEnchantmentType typeIn, EntityEquipmentSlot @NotNull [] slots, @NotNull String name) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, name);
        KuvaLichEnchantments.ENCHANTMENTS.add(this);
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }
}
