package pers.roinflam.kuvalich.block.ore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.init.KuvaLichItems;
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
 * - 需要工具: 石镐 / Stone Pickaxe (level 1)
 * - 基础经验: 5-20 (原 MIN_EXP - MAX_EXP)
 * - 时运加成: +fortune (业务逻辑不变)
 * - 掉落: 无物品，仅经验
 *
 * ⭐ 新增：50%概率额外掉落1-2个内融核心（不受时运影响）
 * ⭐ NEW: 50% chance to drop 1-2 Endo (not affected by Fortune)
 *
 * ⭐ 修复：将掉落逻辑从 spawnAfterBreak/tryDropExperience 移至 playerDestroy，
 *    解决 Mohist（Forge+Bukkit 混合端）环境下 spawnAfterBreak 不被调用导致什么都不掉的问题。
 *    playerDestroy 在所有环境下均可靠调用。
 * ⭐ FIX: Moved drop logic from spawnAfterBreak/tryDropExperience to playerDestroy,
 *    fixing issue where spawnAfterBreak is not called on Mohist (Forge+Bukkit hybrid),
 *    causing no drops at all. playerDestroy is reliably called in all environments.
 */
public class ExperienceOre extends Block {

    // 常量定义（业务逻辑100%保持不变）/ Constants (business logic 100% unchanged)
    private static final int MIN_EXP = 5;
    private static final int MAX_EXP = 20;

    /** 内融核心掉落概率（50%）/ Endo drop chance (50%) */
    private static final double ENDO_DROP_CHANCE = 0.5;

    /** 内融核心最小掉落数量 / Endo min drop amount */
    private static final int ENDO_MIN_AMOUNT = 1;

    /** 内融核心最大掉落数量 / Endo max drop amount */
    private static final int ENDO_MAX_AMOUNT = 2;

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
     *
     * 注意：物品掉落（内融核心）在 playerDestroy 中硬编码处理，不走 loot table。
     * Note: Item drops (Endo) are hardcoded in playerDestroy, not through loot table.
     */
    @Override
    public @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder builder) {
        // 不通过 loot table 掉落任何物品，经验和内融核心在 playerDestroy 中处理
        // No loot table drops; XP and Endo are handled in playerDestroy
        return Collections.emptyList();
    }

    /**
     * ⭐ 修复核心：玩家破坏方块时直接处理所有掉落
     * ⭐ Core fix: Handle all drops directly when player destroys the block
     *
     * 替代原 spawnAfterBreak → tryDropExperience 调用链。
     * playerDestroy 在原版、Forge、Mohist 环境下都会被可靠调用。
     *
     * 掉落规则（业务逻辑100%不变）：
     * - 精准采集：不掉落经验和内融核心
     * - 正常挖掘：掉落经验（基础5-20 + 时运等级），50%概率掉落1-2个内融核心
     *
     * Replaces the spawnAfterBreak → tryDropExperience call chain.
     * playerDestroy is reliably called in vanilla, Forge, and Mohist environments.
     *
     * Drop rules (business logic 100% unchanged):
     * - Silk Touch: no XP or Endo drops
     * - Normal mining: XP (base 5-20 + fortune level), 50% chance for 1-2 Endo
     */
    @Override
    public void playerDestroy(@NotNull Level level, @NotNull Player player, @NotNull BlockPos pos,
                              @NotNull BlockState state, @Nullable BlockEntity blockEntity,
                              @NotNull ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 精准采集时不掉落经验和内融核心 / Silk Touch: no drops
        int silkTouch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool);
        if (silkTouch > 0) {
            return;
        }

        // ========== 经验掉落（业务逻辑100%不变）/ XP drop (unchanged) ==========

        // 获取时运等级 / Get fortune level
        int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);

        // 计算总经验：基础经验 + 时运等级 / Total XP: base + fortune
        int baseExp = serverLevel.random.nextInt(MIN_EXP, MAX_EXP + 1);
        int totalExp = baseExp + fortune;

        // 掉落经验 / Drop experience
        this.popExperience(serverLevel, pos, totalExp);

        // ========== 内融核心掉落（不受时运影响）/ Endo drop (not affected by Fortune) ==========

        if (serverLevel.random.nextDouble() < ENDO_DROP_CHANCE) {
            int endoAmount = ENDO_MIN_AMOUNT + serverLevel.random.nextInt(ENDO_MAX_AMOUNT - ENDO_MIN_AMOUNT + 1);
            ItemStack endoStack = new ItemStack(KuvaLichItems.ENDO.get(), endoAmount);

            // 使用 popResource 在方块位置生成掉落物（与原版矿石掉落物逻辑一致）
            // Use popResource to spawn drops at block position (consistent with vanilla ore drops)
            popResource(serverLevel, pos, endoStack);

            LogUtil.debugEvent("经验矿石内融核心掉落", "位置: " + pos, "数量: " + endoAmount);
        }

        String details = String.format(
                "基础经验: %d (范围: %d-%d), 时运等级: %d, 总经验: %d",
                baseExp, MIN_EXP, MAX_EXP, fortune, totalExp
        );
        LogUtil.debugEvent("经验矿石经验掉落", "位置: " + pos, details);
    }
}