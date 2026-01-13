package pers.roinflam.kuvalich.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 护卫附魔
 * Escort Enchantment
 *
 * 效果：满血时大幅减少受到的伤害
 * Effect: Greatly reduce damage taken when at full health
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentEscort extends EnchantmentBase {

    // 常量定义 / Constants
    private static final int MAX_LEVEL = 3;
    private static final int BASE_ENCHANTABILITY = 25;
    private static final int ENCHANTABILITY_PER_LEVEL = 25;
    private static final float DAMAGE_REDUCTION_PER_LEVEL = 0.25f;
    private static final float MAX_DAMAGE_CAP = 0.99f;

    public EnchantmentEscort() {
        super(Enchantment.Rarity.RARE,
                EnchantmentCategory.ARMOR,
                new EquipmentSlot[]{
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET
                },
                "escort");
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (evt.getEntity() == null || evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity target = evt.getEntity();
        if (target == null || target.getHealth() != target.getMaxHealth()) {
            return;
        }

        int maxLevel = getMaxEnchantmentLevel(target);
        if (maxLevel <= 0) {
            return;
        }

        // 限制等级上限 / Limit max level
        maxLevel = Math.min(maxLevel, MAX_LEVEL);

        // 计算减伤后的伤害 / Calculate reduced damage
        float reducedDamage = evt.getAmount() * maxLevel * DAMAGE_REDUCTION_PER_LEVEL;
        float maxAllowedDamage = target.getMaxHealth() * MAX_DAMAGE_CAP;

        evt.setAmount(Math.min(maxAllowedDamage, reducedDamage));
    }

    /**
     * 获取护甲上的最高附魔等级
     * Get max enchantment level from armor
     */
    private static int getMaxEnchantmentLevel(LivingEntity entity) {
        int maxLevel = 0;

        // 1.20.1中使用getArmorSlots()
        for (ItemStack armor : entity.getArmorSlots()) {
            if (armor == null || armor.isEmpty()) {
                continue;
            }

            int level = EnchantmentHelper.getItemEnchantmentLevel(
                    KuvaLichEnchantments.ESCORT.get(), armor);
            maxLevel = Math.max(maxLevel, level);
        }

        return maxLevel;
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