package pers.roinflam.kuvalich.block.ore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
public class RequiemOre extends Block {

    // 常量定义（业务逻辑100%保持不变）/ Constants (business logic 100% unchanged)
    private static final int MIN_EXP = 50;
    private static final int MAX_EXP = 100;

    /** 经验范围提供器，用于spawnAfterBreak调用 */
    private static final UniformInt EXP_RANGE = UniformInt.of(MIN_EXP, MAX_EXP);

    /**
     * 构造函数（1.20.1只接收Properties）
     * Constructor (1.20.1 only accepts Properties)
     *
     * 注意：方块注册已在KuvaLichBlocks中完成
     * Note: Block registration is done in KuvaLichBlocks
     */
    public RequiemOre(Properties properties) {
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
     * 方块被破坏后触发经验掉落（业务逻辑100%不变）
     * Trigger experience drop after block is broken (business logic 100% unchanged)
     *
     * 注意：Block基类的spawnAfterBreak不会自动调用tryDropExperience，
     * 只有DropExperienceBlock才会。因此必须手动重写此方法来触发经验掉落。
     * Note: Block base class's spawnAfterBreak does not call tryDropExperience automatically,
     * only DropExperienceBlock does. Must override this method to trigger exp drops.
     *
     * @param state 方块状态
     * @param level 服务端世界
     * @param pos 方块位置
     * @param tool 使用的工具
     * @param dropExperience 是否应该掉落经验（丝绸之触时为false）
     */
    @Override
    public void spawnAfterBreak(@NotNull BlockState state, @NotNull ServerLevel level,
                                @NotNull BlockPos pos, @NotNull ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (dropExperience) {
            // 掉落50-100经验（业务逻辑不变）
            // Drop 50-100 experience (business logic unchanged)
            int exp = EXP_RANGE.sample(level.random);
            if (exp > 0) {
                this.popExperience(level, pos, exp);
            }

            LogUtil.debugEvent("安魂矿石经验掉落",
                    "位置: " + pos,
                    "经验: " + exp + " (范围: " + MIN_EXP + "-" + MAX_EXP + ")"
            );
        }
    }
}