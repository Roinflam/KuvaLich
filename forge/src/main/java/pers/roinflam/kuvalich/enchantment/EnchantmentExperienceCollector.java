package pers.roinflam.kuvalich.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import pers.roinflam.kuvalich.base.enchantment.AbstractEnchantment;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 经验之运附魔
 * Experience Collector Enchantment
 *
 * 效果：挖掘方块时额外获得经验
 * Effect: Gain extra experience when mining blocks
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentExperienceCollector extends AbstractEnchantment {

    // 常量定义 / Constants
    private static final int MAX_LEVEL = 5;
    private static final int BASE_ENCHANTABILITY = 15;
    private static final int ENCHANTABILITY_PER_LEVEL = 10;
    private static final int BASE_EXP_BONUS = 1;
    private static final float EXP_BONUS_PER_LEVEL = 1.5f;

    public EnchantmentExperienceCollector() {
        super(Enchantment.Rarity.RARE,
                EnchantmentCategory.DIGGER,
                new EquipmentSlot[]{EquipmentSlot.MAINHAND},
                "experience_collector");
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent evt) {
        if (evt.getExpToDrop() <= 0) {
            return;
        }

        Player player = evt.getPlayer();
        if (player == null) {
            return;
        }

        // 1.20.1中使用getMainHandItem()
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return;
        }

        int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(
                KuvaLichEnchantments.EXPERIENCE_COLLECTOR.get(), tool);
        if (enchantLevel <= 0) {
            return;
        }

        // 计算经验加成 / Calculate experience bonus
        int bonusExp = (int) (BASE_EXP_BONUS + enchantLevel * EXP_BONUS_PER_LEVEL);
        evt.setExpToDrop(evt.getExpToDrop() + bonusExp);
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
        return super.checkCompatibility(ench) && ench != Enchantments.SILK_TOUCH;
    }
}