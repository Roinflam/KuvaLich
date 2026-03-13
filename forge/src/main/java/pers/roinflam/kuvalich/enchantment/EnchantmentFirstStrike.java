// EnchantmentFirstStrike.java
package pers.roinflam.kuvalich.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 先攻附魔
 * First Strike Enchantment
 *
 * 效果：对满血敌人造成额外伤害，并获得经验
 * Effect: Deal extra damage to enemies at full health and gain experience
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentFirstStrike extends EnchantmentBase {

    // 常量定义 / Constants
    private static final int MAX_LEVEL = 3;
    private static final int BASE_ENCHANTABILITY = 25;
    private static final int ENCHANTABILITY_PER_LEVEL = 10;
    private static final float BASE_DAMAGE_MULTIPLIER = 2.0f;
    private static final float DAMAGE_MULTIPLIER_PER_LEVEL = 0.5f;
    private static final float MAX_DAMAGE_CAP = 0.99f;
    private static final float EXPERIENCE_MULTIPLIER = 0.1f;
    private static final float MAX_EXPERIENCE_CAP = 0.33f;
    /** 经验绝对上限 / Absolute experience cap */
    private static final int MAX_EXPERIENCE_ABSOLUTE = 1000;

    public EnchantmentFirstStrike() {
        super(Enchantment.Rarity.VERY_RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND},
                "first_strike");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();
        ItemStack weapon = attacker.getMainHandItem();

        if (weapon.isEmpty()) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                KuvaLichEnchantments.FIRST_STRIKE.get(), weapon);
        if (enchantLevel <= 0) {
            return;
        }

        LivingEntity target = evt.getEntity();
        if (target == null || target.getHealth() != target.getMaxHealth()) {
            return;
        }

        // 计算首击伤害 / Calculate first strike damage
        float damageMultiplier = BASE_DAMAGE_MULTIPLIER + (enchantLevel - 1) * DAMAGE_MULTIPLIER_PER_LEVEL;
        float bonusDamage = evt.getAmount() * (damageMultiplier - 1.0f);
        float finalDamage = evt.getAmount() + bonusDamage;

        // 检查是否会秒杀 / Check if it will be lethal
        boolean isLethal = finalDamage >= target.getMaxHealth() * MAX_DAMAGE_CAP;

        if (!isLethal) {
            // 不秒杀时限制伤害上限 / Limit damage if not lethal
            finalDamage = Math.min(target.getMaxHealth() * MAX_DAMAGE_CAP, finalDamage);
        }

        evt.setAmount(finalDamage);

        // 给予玩家经验 / Give player experience
        if (attacker instanceof Player) {
            giveExperience((Player) attacker, target, bonusDamage, enchantLevel);
        }
    }

    /**
     * 给予玩家经验
     * Give player experience
     *
     * @param player 攻击者玩家 / Attacker player
     * @param target 被攻击的目标 / Target entity
     * @param bonusDamage 额外伤害值 / Bonus damage dealt
     * @param level 附魔等级 / Enchantment level
     */
    private static void giveExperience(Player player, LivingEntity target, float bonusDamage, int level) {
        float baseExp = bonusDamage + bonusDamage * (level - 1) * DAMAGE_MULTIPLIER_PER_LEVEL;
        float maxExp = target.getMaxHealth() * MAX_EXPERIENCE_CAP;

        // 取血量比例上限和绝对上限中的较小值 / Take the smaller of health-based cap and absolute cap
        int experience = (int) Math.min(MAX_EXPERIENCE_ABSOLUTE, Math.min(maxExp, baseExp * EXPERIENCE_MULTIPLIER));
        player.giveExperiencePoints(experience);
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