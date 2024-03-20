package pers.roinflam.kuvalich.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentDeathResistance extends EnchantmentBase {

    public EnchantmentDeathResistance(@NotNull Rarity rarityIn, @NotNull EnumEnchantmentType typeIn, EntityEquipmentSlot @NotNull [] slots) {
        super(rarityIn, typeIn, slots, "death_resistance");
    }

    public static @NotNull Enchantment getEnchantment() {
        return KuvaLichEnchantments.DEATH_RESISTANCE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getEntityLiving() instanceof EntityPlayer) {
                if (!evt.getSource().canHarmInCreative()) {
                    EntityPlayer entityPlayer = (EntityPlayer) evt.getEntityLiving();
                    int bonusLevel = 0;
                    for (@NotNull ItemStack itemStack : entityPlayer.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    bonusLevel = Math.min(bonusLevel, 3);
                    if (bonusLevel > 0 && evt.getAmount() >= entityPlayer.getHealth()) {
                        if (entityPlayer.experienceTotal > 0) {
                            evt.setCanceled(true);
                            if (entityPlayer.getHealth() > 0.01) {
                                double amount = evt.getAmount() - entityPlayer.getHealth() + 0.01;
                                int lastExperienceTotal = entityPlayer.experienceTotal;
                                entityPlayer.sendMessage(new TextComponentString(evt.getSource().getDamageType() + ":" + amount));
                                int experienceTotal = (int) (lastExperienceTotal - (lastExperienceTotal * 0.025 * Math.min(40 * bonusLevel - 1, amount)) / bonusLevel - Math.max(bonusLevel, amount));
                                experienceTotal = Math.max(0, experienceTotal);
                                entityPlayer.experience = 0.0F;
                                entityPlayer.experienceTotal = 0;
                                entityPlayer.experienceLevel = 0;
                                entityPlayer.addExperience(experienceTotal);
                                entityPlayer.setHealth((float) 0.01);
                            } else {
                                double amount = evt.getAmount();
                                int lastExperienceTotal = entityPlayer.experienceTotal;
                                int experienceTotal = (int) (lastExperienceTotal - (lastExperienceTotal * 0.025 * Math.min(40 * bonusLevel - 1, amount)) / bonusLevel - Math.max(bonusLevel, amount));
                                experienceTotal = Math.max(0, experienceTotal);
                                entityPlayer.experience = 0.0F;
                                entityPlayer.experienceTotal = 0;
                                entityPlayer.experienceLevel = 0;
                                entityPlayer.addExperience(experienceTotal);
                            }
                        }
                    }
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
