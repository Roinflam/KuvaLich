package pers.roinflam.kuvalich.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentFirstStrike extends EnchantmentBase {

    public EnchantmentFirstStrike(@NotNull Rarity rarityIn, @NotNull EnumEnchantmentType typeIn, EntityEquipmentSlot @NotNull [] slots) {
        super(rarityIn, typeIn, slots, "firststrike");
        KuvaLichEnchantments.ENCHANTMENTS.add(this);
    }

    public static @NotNull Enchantment getEnchantment() {
        return KuvaLichEnchantments.FIRST_STRIKE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0 && hurter.getMaxHealth() == hurter.getHealth()) {
                        if (evt.getAmount() >= hurter.getMaxHealth() * 0.99) {
                            evt.setAmount((float) (evt.getAmount() * 2 + evt.getAmount() * (bonusLevel - 1) * 0.5));
                        } else {
                            evt.setAmount((float) Math.min(hurter.getMaxHealth() * 0.99, evt.getAmount() * 2 + evt.getAmount() * (bonusLevel - 1) * 0.5));
                        }
                        if (attacker instanceof EntityPlayer) {
                            @NotNull EntityPlayer entityPlayer = (EntityPlayer) attacker;
                            entityPlayer.addExperience((int) Math.min(hurter.getMaxHealth() * 0.33, (evt.getAmount() + evt.getAmount() * (bonusLevel - 1) * 0.5) * 0.1));
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
        return 25 + (enchantmentLevel - 1) * 10;
    }

}
