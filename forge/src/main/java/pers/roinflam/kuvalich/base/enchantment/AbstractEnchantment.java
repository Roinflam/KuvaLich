package pers.roinflam.kuvalich.base.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import pers.roinflam.kuvalich.utils.LogUtil;

/**
 * 附魔基类
 * Enchantment base class
 */
public abstract class AbstractEnchantment extends Enchantment {

    private final String registryName;

    /**
     * 构造附魔
     * Construct enchantment
     *
     * @param rarityIn 稀有度 / rarity
     * @param typeIn 附魔类型 / enchantment type
     * @param slots 适用装备槽位 / applicable equipment slots
     * @param name 注册名 / registry name
     */
    protected AbstractEnchantment(Rarity rarityIn, EnchantmentCategory typeIn, EquipmentSlot[] slots, String name) {
        super(rarityIn, typeIn, slots);
        this.registryName = name;

        LogUtil.debug("附魔注册成功: " + name + " (稀有度: " + rarityIn + ")");
    }

    /**
     * 获取最大附魔等级的可附魔性
     * Get max enchantability for enchantment level
     *
     * @param enchantmentLevel 附魔等级 / enchantment level
     * @return 最大可附魔性 / max enchantability
     */
    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) * 2;
    }

    /**
     * 获取注册名
     * Get registry name
     */
    public String getRegistryName() {
        return registryName;
    }
}