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
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentEscort extends EnchantmentBase {

    public EnchantmentEscort(@NotNull Rarity rarityIn, @NotNull EnumEnchantmentType typeIn, EntityEquipmentSlot @NotNull [] slots) {
        super(rarityIn, typeIn, slots, "escort");
    }

    public static @NotNull Enchantment getEnchantment() {
        return KuvaLichEnchantments.ESCORT;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (hurter.getMaxHealth() == hurter.getHealth()) {
                int bonusLevel = 0;
                for (@NotNull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel = Math.max(bonusLevel, EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack));
                    }
                }
                bonusLevel = Math.min(bonusLevel, 3);
                if (bonusLevel > 0) {
                    evt.setAmount((float) Math.min(hurter.getMaxHealth() * 0.99, (evt.getAmount() * bonusLevel * 0.25)));
                }
            }
        }
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 25 + (enchantmentLevel - 1) * 25;
    }

}
