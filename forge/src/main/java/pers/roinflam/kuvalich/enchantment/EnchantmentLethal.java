// 文件：EnchantmentLethal.java
// 路径：src/main/java/pers/roinflam/kuvalich/enchantment/EnchantmentLethal.java
package pers.roinflam.kuvalich.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentLethal extends EnchantmentBase {
    // 常量定义
    private static final int MAX_LEVEL = 5;
    private static final int BASE_ENCHANTABILITY = 10;
    private static final int ENCHANTABILITY_PER_LEVEL = 10;
    private static final float DAMAGE_BONUS_PER_LEVEL = 0.15f;

    public EnchantmentLethal(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots, "lethal");
    }

    public static Enchantment getEnchantment() {
        return KuvaLichEnchantments.LETHAL;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity() == null || evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
        ItemStack weapon = attacker.getHeldItemMainhand();

        if (weapon == null || weapon.isEmpty()) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), weapon);
        if (enchantLevel <= 0) {
            return;
        }

        EntityLivingBase target = evt.getEntityLiving();
        if (target == null) {
            return;
        }

        // 计算基于目标生命值的额外伤害
        float healthPercent = 1.0f - (target.getHealth() / target.getMaxHealth());
        float bonusDamage = evt.getAmount() * healthPercent * DAMAGE_BONUS_PER_LEVEL * enchantLevel;

        evt.setAmount(evt.getAmount() + bonusDamage);
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return BASE_ENCHANTABILITY + (enchantmentLevel - 1) * ENCHANTABILITY_PER_LEVEL;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && ench != KuvaLichEnchantments.FIRST_STRIKE;
    }
}