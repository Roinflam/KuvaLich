// 文件：EnchantmentEscort.java
// 路径：src/main/java/pers/roinflam/kuvalich/enchantment/EnchantmentEscort.java
package pers.roinflam.kuvalich.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentEscort extends EnchantmentBase {
    // 常量定义
    private static final int MAX_LEVEL = 3;
    private static final int BASE_ENCHANTABILITY = 25;
    private static final int ENCHANTABILITY_PER_LEVEL = 25;
    private static final float DAMAGE_REDUCTION_PER_LEVEL = 0.25f;
    private static final float MAX_DAMAGE_CAP = 0.99f;

    public EnchantmentEscort(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots, "escort");
    }

    public static Enchantment getEnchantment() {
        return KuvaLichEnchantments.ESCORT;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingDamageEvent evt) {
        if (evt.getEntity() == null || evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase target = evt.getEntityLiving();
        if (target == null || target.getHealth() != target.getMaxHealth()) {
            return;
        }

        int maxLevel = getMaxEnchantmentLevel(target);
        if (maxLevel <= 0) {
            return;
        }

        // 限制等级上限
        maxLevel = Math.min(maxLevel, MAX_LEVEL);

        // 计算减伤后的伤害
        float reducedDamage = evt.getAmount() * maxLevel * DAMAGE_REDUCTION_PER_LEVEL;
        float maxAllowedDamage = target.getMaxHealth() * MAX_DAMAGE_CAP;

        evt.setAmount(Math.min(maxAllowedDamage, reducedDamage));
    }

    /**
     * 获取护甲上的最高附魔等级
     */
    private static int getMaxEnchantmentLevel(EntityLivingBase entity) {
        int maxLevel = 0;

        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (armor == null || armor.isEmpty()) {
                continue;
            }

            int level = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), armor);
            maxLevel = Math.max(maxLevel, level);
        }

        return maxLevel;
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return BASE_ENCHANTABILITY + (enchantmentLevel - 1) * ENCHANTABILITY_PER_LEVEL;
    }
}