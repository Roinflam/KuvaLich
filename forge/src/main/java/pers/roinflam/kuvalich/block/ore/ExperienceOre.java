package pers.roinflam.kuvalich.block.ore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.Collections;
import java.util.List;

/**
 * 经验矿石（1.20.1版本，业务逻辑100%不变）
 * Experience Ore (1.20.1 version, business logic 100% unchanged)
 *
 * 属性保持不变：
 * Properties unchanged:
 * - 硬度: 3.0F (原 HARDNESS)
 * - 抗性: 3.0F (原 RESISTANCE)
 * - 需要工具: 石镐 (原 HARVEST_LEVEL = 1)
 * - 基础经验: 5-20 (原 MIN_EXP - MAX_EXP)
 * - 时运加成: +fortune (业务逻辑不变)
 * - 掉落: 无物品，仅经验
 */
public class ExperienceOre extends Block {

    // 常量定义（业务逻辑100%保持不变）/ Constants (business logic 100% unchanged)
    private static final int MIN_EXP = 5;
    private static final int MAX_EXP = 20;

    public ExperienceOre(Properties properties) {
        super(properties);

        LogUtil.debug(String.format(
                "经验矿石注册完成 - 硬度:%.1f, 抗性:%.1f, 需要:石镐",
                3.0f, 3.0f
        ));
    }

    /**
     * 获取掉落物（业务逻辑100%不变：不掉落任何物品）
     * Get drops (business logic 100% unchanged: no item drops)
     */
    @Override
    public @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder builder) {
        // 不掉落任何物品，仅掉落经验
        // No item drops, only experience
        LogUtil.debug("经验矿石被破坏 - 仅掉落经验");
        return Collections.emptyList();
    }

    /**
     * 尝试掉落经验（1.20.1新方法，包含时运加成逻辑）
     * Try drop experience (1.20.1 new method, includes fortune bonus)
     *
     * 注意：1.20.1中时运加成需要手动实现
     * Note: Fortune bonus needs manual implementation in 1.20.1
     */
    @Override
    protected void tryDropExperience(@NotNull ServerLevel level, @NotNull BlockPos pos,
                                     @NotNull ItemStack tool, @NotNull net.minecraft.util.valueproviders.IntProvider experience) {
        // 获取时运等级（业务逻辑100%不变）
        // Get fortune level (business logic 100% unchanged)
        int fortune = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, tool
        );

        // 计算总经验：基础经验 + 时运等级（业务逻辑100%不变）
        // Calculate total exp: base exp + fortune level (business logic 100% unchanged)
        int baseExp = level.random.nextInt(MIN_EXP, MAX_EXP + 1);
        int totalExp = baseExp + fortune;

        // 掉落经验 / Drop experience
        this.popExperience(level, pos, totalExp);

        String details = String.format(
                "基础经验: %d (范围: %d-%d), 时运等级: %d, 总经验: %d",
                baseExp, MIN_EXP, MAX_EXP, fortune, totalExp
        );
        LogUtil.debugEvent("经验矿石经验掉落", "位置: " + pos, details);
    }
}