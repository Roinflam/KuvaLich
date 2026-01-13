package pers.roinflam.kuvalich.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 斩杀附魔
 * Lethal Enchantment
 *
 * 效果：对生命值越低的敌人造成越高的额外伤害
 * Effect: Deal more damage to enemies with lower health
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentLethal extends EnchantmentBase {

    // 常量定义 / Constants
    private static final int MAX_LEVEL = 5;
    private static final int BASE_ENCHANTABILITY = 10;
    private static final int ENCHANTABILITY_PER_LEVEL = 10;
    private static final float DAMAGE_BONUS_PER_LEVEL = 0.15f;

    public EnchantmentLethal() {
        super(Enchantment.Rarity.VERY_RARE,
                EnchantmentCategory.WEAPON,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND},
                "lethal");
    }

    @SubscribeEvent
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
                KuvaLichEnchantments.LETHAL.get(), weapon);
        if (enchantLevel <= 0) {
            return;
        }

        LivingEntity target = evt.getEntity();
        if (target == null) {
            return;
        }

        // 计算基于目标生命值的额外伤害
        // Calculate bonus damage based on target health
        float healthPercent = 1.0f - (target.getHealth() / target.getMaxHealth());
        float bonusDamage = evt.getAmount() * healthPercent * DAMAGE_BONUS_PER_LEVEL * enchantLevel;

        evt.setAmount(evt.getAmount() + bonusDamage);
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return BASE_ENCHANTABILITY + (enchantmentLevel - 1) * ENCHANTABILITY_PER_LEVEL;
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        return super.checkCompatibility(ench) && ench != KuvaLichEnchantments.FIRST_STRIKE.get();
    }
}