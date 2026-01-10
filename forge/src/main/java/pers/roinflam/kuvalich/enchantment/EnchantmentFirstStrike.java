// 文件：EnchantmentFirstStrike.java
// 路径：src/main/java/pers/roinflam/kuvalich/enchantment/EnchantmentFirstStrike.java
package pers.roinflam.kuvalich.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentFirstStrike extends EnchantmentBase {
    // 常量定义
    private static final int MAX_LEVEL = 3;
    private static final int BASE_ENCHANTABILITY = 25;
    private static final int ENCHANTABILITY_PER_LEVEL = 10;
    private static final float BASE_DAMAGE_MULTIPLIER = 2.0f;
    private static final float DAMAGE_MULTIPLIER_PER_LEVEL = 0.5f;
    private static final float MAX_DAMAGE_CAP = 0.99f;
    private static final float EXPERIENCE_MULTIPLIER = 0.1f;
    private static final float MAX_EXPERIENCE_CAP = 0.33f;

    public EnchantmentFirstStrike(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots, "firststrike");
    }

    public static Enchantment getEnchantment() {
        return KuvaLichEnchantments.FIRST_STRIKE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
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
        if (target == null || target.getHealth() != target.getMaxHealth()) {
            return;
        }

        // 计算首击伤害
        float damageMultiplier = BASE_DAMAGE_MULTIPLIER + (enchantLevel - 1) * DAMAGE_MULTIPLIER_PER_LEVEL;
        float bonusDamage = evt.getAmount() * (damageMultiplier - 1.0f);
        float finalDamage = evt.getAmount() + bonusDamage;

        // 检查是否会秒杀
        boolean isLethal = finalDamage >= target.getMaxHealth() * MAX_DAMAGE_CAP;

        if (!isLethal) {
            // 不秒杀时限制伤害上限
            finalDamage = Math.min(target.getMaxHealth() * MAX_DAMAGE_CAP, finalDamage);
        }

        evt.setAmount(finalDamage);

        // 给予玩家经验
        if (attacker instanceof EntityPlayer) {
            giveExperience((EntityPlayer) attacker, target, bonusDamage, enchantLevel);
        }
    }

    /**
     * 给予玩家经验
     */
    private static void giveExperience(EntityPlayer player, EntityLivingBase target, float bonusDamage, int level) {
        float baseExp = bonusDamage + bonusDamage * (level - 1) * DAMAGE_MULTIPLIER_PER_LEVEL;
        float maxExp = target.getMaxHealth() * MAX_EXPERIENCE_CAP;

        int experience = (int) Math.min(maxExp, baseExp * EXPERIENCE_MULTIPLIER);
        player.addExperience(experience);
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