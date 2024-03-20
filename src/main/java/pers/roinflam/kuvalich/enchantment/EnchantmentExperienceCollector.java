package pers.roinflam.kuvalich.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentExperienceCollector extends EnchantmentBase {

    public EnchantmentExperienceCollector(@NotNull Rarity rarityIn, @NotNull EnumEnchantmentType typeIn, EntityEquipmentSlot @NotNull [] slots) {
        super(rarityIn, typeIn, slots, "experience_collector");
    }

    public static @NotNull Enchantment getEnchantment() {
        return KuvaLichEnchantments.EXPERIENCE_COLLECTOR;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.@NotNull BreakEvent evt) {
        if (evt.getExpToDrop() > 0) {
            EntityPlayer entityPlayer = evt.getPlayer();
            if (entityPlayer.getHeldItem(entityPlayer.getActiveHand()) != null) {
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), entityPlayer.getHeldItem(entityPlayer.getActiveHand()));
                if (bonusLevel > 0) {
                    evt.setExpToDrop((int) (evt.getExpToDrop() + 1 + bonusLevel * 1.5));
                }
            }
        }
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 15 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && ench != Enchantments.SILK_TOUCH;
    }
}
