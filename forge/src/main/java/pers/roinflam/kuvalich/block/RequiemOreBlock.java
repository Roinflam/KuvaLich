package pers.roinflam.kuvalich.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.Collections;
import java.util.List;

/**
 * 安魂矿石（1.20.1版本，业务逻辑100%不变）
 * Requiem Ore (1.20.1 version, business logic 100% unchanged)
 *
 * 属性保持不变：
 * Properties unchanged:
 * - 硬度: 100.0F (原 HARDNESS)
 * - 抗性: 2000.0F (原 RESISTANCE)
 * - 需要工具: 钻石镐 (原 HARVEST_LEVEL = 3)
 * - 经验: 50-100 (原 MIN_EXP - MAX_EXP)
 * - 掉落: 无物品，仅经验
 */
public class RequiemOreBlock extends Block {

    // 常量定义（业务逻辑100%保持不变）/ Constants (business logic 100% unchanged)
    private static final int MIN_EXP = 50;
    private static final int MAX_EXP = 100;

    /** 经验范围（getExpDrop 按它抽） */
    private static final UniformInt EXP_RANGE = UniformInt.of(MIN_EXP, MAX_EXP);

    /**
     * 构造函数（1.20.1只接收Properties）
     * Constructor (1.20.1 only accepts Properties)
     *
     * 注意：方块注册已在KuvaLichBlocks中完成
     * Note: Block registration is done in KuvaLichBlocks
     */
    public RequiemOreBlock(Properties properties) {
        super(properties);

        LogUtil.debug(String.format(
                "安魂矿石注册完成 - 硬度:%.1f, 抗性:%.1f, 需要:钻石镐",
                100.0f, 2000.0f
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
        LogUtil.debug("安魂矿石被破坏 - 仅掉落经验");
        return Collections.emptyList();
    }

    /**
     * 挖掘经验：50-100，精准采集为 0
     *
     * <p>⭐ 2026-09-25 修复：原先在 {@code spawnAfterBreak} 里判 {@code dropExperience} 再自己 popExperience。
     * 但 Forge 1.20.1 玩家挖方块走的是 {@code playerDestroy → dropResources(..., false)}，这个参数恒为 false，
     * 经验改由 {@code ServerPlayerGameMode.destroyBlock} 按 {@code BreakEvent} 里的数值掉 ——
     * 而事件的初值来自本方法，没覆写就是 0。结果玩家挖安魂矿石一点经验都没有，
     * 灭骸附魔的「经验 × expMultiplier」乘的也是 0（正式服 Tenet 同一条路径，已核对源码）。</p>
     *
     * <p>改成覆写本方法后：玩家挖掘走 BreakEvent（灭骸附魔在那里乘倍率）；
     * 其它需要掉经验的破坏走 Forge 基类的 {@code spawnAfterBreak → dropXpForBlock}，同样读这里。
     * 所以旧的 {@code spawnAfterBreak} 覆写删掉了，留着会在后一种路径里掉两份。</p>
     */
    @Override
    public int getExpDrop(@NotNull BlockState state, @NotNull net.minecraft.world.level.LevelReader level,
                          @NotNull net.minecraft.util.RandomSource randomSource, @NotNull BlockPos pos,
                          int fortuneLevel, int silkTouchLevel) {
        return silkTouchLevel == 0 ? EXP_RANGE.sample(randomSource) : 0;
    }
}