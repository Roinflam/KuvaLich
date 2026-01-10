// 文件：EnchantmentExperienceCollector.java
// 路径：src/main/java/pers/roinflam/kuvalich/enchantment/EnchantmentExperienceCollector.java
package pers.roinflam.kuvalich.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.base.enchantment.EnchantmentBase;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentExperienceCollector extends EnchantmentBase {
    // 常量定义
    private static final int MAX_LEVEL = 5;
    private static final int BASE_ENCHANTABILITY = 15;
    private static final int ENCHANTABILITY_PER_LEVEL = 10;
    private static final int BASE_EXP_BONUS = 1;
    private static final float EXP_BONUS_PER_LEVEL = 1.5f;

    public EnchantmentExperienceCollector(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots, "experience_collector");
    }

    public static Enchantment getEnchantment() {
        return KuvaLichEnchantments.EXPERIENCE_COLLECTOR;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent evt) {
        if (evt.getExpToDrop() <= 0) {
            return;
        }

        EntityPlayer player = evt.getPlayer();
        if (player == null) {
            return;
        }

        ItemStack tool = player.getHeldItem(player.getActiveHand());
        if (tool == null || tool.isEmpty()) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), tool);
        if (enchantLevel <= 0) {
            return;
        }

        // 计算经验加成
        int bonusExp = (int) (BASE_EXP_BONUS + enchantLevel * EXP_BONUS_PER_LEVEL);
        evt.setExpToDrop(evt.getExpToDrop() + bonusExp);
    }

    @Override
    public int getMaxLevel() {
        return MAX_LEVEL;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return BASE_ENCHANTABILITY + (enchantmentLevel - 1) * ENCHANTABILITY_PER_LEVEL;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && ench != Enchantments.SILK_TOUCH;
    }
}