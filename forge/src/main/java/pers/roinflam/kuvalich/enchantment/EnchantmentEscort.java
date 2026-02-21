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
 *
 * 减伤计算（修复后）：
 * 等级1：乘以 (1 - 1 * 0.25) = 0.75，减伤 25%
 * 等级2：乘以 (1 - 2 * 0.25) = 0.50，减伤 50%
 * 等级3：乘以 (1 - 3 * 0.25) = 0.25，减伤 75%
 *
 * Damage reduction (after fix):
 * Level 1: multiply by (1 - 1 * 0.25) = 0.75, reduce 25%
 * Level 2: multiply by (1 - 2 * 0.25) = 0.50, reduce 50%
 * Level 3: multiply by (1 - 3 * 0.25) = 0.25, reduce 75%
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentEscort extends EnchantmentBase {

    private static final int MAX_LEVEL = 3;
    private static final int BASE_ENCHANTABILITY = 25;
    private static final int ENCHANTABILITY_PER_LEVEL = 25;
    private static final float DAMAGE_REDUCTION_PER_LEVEL = 0.25f;
    // 保留最低伤害上限，防止完全免疫 / Keep a minimum damage floor to prevent full immunity
    private static final float MIN_DAMAGE_MULTIPLIER = 0.01f;

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
        if (target.getHealth() != target.getMaxHealth()) {
            return;
        }

        int maxLevel = getMaxEnchantmentLevel(target);
        if (maxLevel <= 0) {
            return;
        }

        maxLevel = Math.min(maxLevel, MAX_LEVEL);

        // 修复：减伤应为 amount * (1 - level * reductionPerLevel)
        // 原代码是 amount * level * 0.25，等级越高伤害反而越大（逻辑反了）
        //
        // Fix: reduction should be amount * (1 - level * reductionPerLevel)
        // Original code was amount * level * 0.25, which INCREASED damage at higher levels (inverted logic)
        float damageMultiplier = Math.max(MIN_DAMAGE_MULTIPLIER, 1.0f - maxLevel * DAMAGE_REDUCTION_PER_LEVEL);
        evt.setAmount(evt.getAmount() * damageMultiplier);
    }

    /**
     * 获取护甲上的最高附魔等级
     * Get max enchantment level from armor
     */
    private static int getMaxEnchantmentLevel(LivingEntity entity) {
        int maxLevel = 0;

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