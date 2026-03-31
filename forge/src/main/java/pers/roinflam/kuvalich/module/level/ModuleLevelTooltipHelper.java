package pers.roinflam.kuvalich.module.level;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemEvolve;

import java.util.List;

/**
 * 模组等级Tooltip渲染辅助类（客户端专用）
 * <p>
 * 等级进度条：红→金→黄→绿渐变，满级不显示
 * 升级费用仅在安魂之融界面中以灰色显示
 * 裂罅循环费用仅在安魂之融界面中以灰色显示
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class ModuleLevelTooltipHelper {

    private static final char BAR_FILLED = '\u2588';
    private static final char BAR_EMPTY = '\u2591';
    private static final int BAR_LENGTH = 10;

    /**
     * 向Tooltip插入等级进度条行
     * ⭐ 满级时不显示，返回0
     *
     * @return 插入的行数（0或1）
     */
    public static int appendLevelTooltip(List<Component> tooltip, int insertIndex,
                                         int currentLevel, int maxLevel) {
        if (!ModuleLevelHelper.isLevelSystemEnabled()) { return 0; }
        if (currentLevel >= maxLevel) { return 0; }

        double ratio = (double) currentLevel / (double) maxLevel;
        int filledCount = Math.max(0, Math.min((int) Math.round(ratio * BAR_LENGTH), BAR_LENGTH));

        ChatFormatting filledColor;
        if (ratio > 0.8) { filledColor = ChatFormatting.GREEN; }
        else if (ratio > 0.5) { filledColor = ChatFormatting.YELLOW; }
        else if (ratio > 0.25) { filledColor = ChatFormatting.GOLD; }
        else { filledColor = ChatFormatting.RED; }

        MutableComponent barComponent = Component.literal("");

        StringBuilder filledBar = new StringBuilder();
        for (int i = 0; i < filledCount; i++) { filledBar.append(BAR_FILLED); }
        if (filledBar.length() > 0) {
            barComponent.append(Component.literal(filledBar.toString()).withStyle(filledColor));
        }

        StringBuilder emptyBar = new StringBuilder();
        for (int i = filledCount; i < BAR_LENGTH; i++) { emptyBar.append(BAR_EMPTY); }
        if (emptyBar.length() > 0) {
            barComponent.append(Component.literal(emptyBar.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }

        barComponent.append(Component.literal(" Lv." + currentLevel + "/" + maxLevel)
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));

        tooltip.add(insertIndex, barComponent);
        return 1;
    }

    /**
     * 向Tooltip插入升级费用（无条件，灰色）
     */
    public static int appendUpgradeCostTooltip(List<Component> tooltip, int insertIndex,
                                               int currentLevel) {
        if (!ModuleLevelHelper.isLevelSystemEnabled()) { return 0; }
        if (currentLevel >= ModuleLevelHelper.getMaxLevel()) { return 0; }
        int cost = ModuleLevelHelper.getUpgradeCost(currentLevel);
        if (cost <= 0) { return 0; }
        tooltip.add(insertIndex, Component.translatable("item.module.level.upgrade_cost", cost)
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /**
     * ⭐ 仅在安魂之融界面中显示升级费用（灰色）
     */
    public static int appendUpgradeCostTooltipIfInEvolve(List<Component> tooltip, int insertIndex,
                                                         int currentLevel) {
        if (!ModuleLevelHelper.isLevelSystemEnabled()) { return 0; }
        if (currentLevel >= ModuleLevelHelper.getMaxLevel()) { return 0; }
        if (!isInEvolveMenu()) { return 0; }
        int cost = ModuleLevelHelper.getUpgradeCost(currentLevel);
        if (cost <= 0) { return 0; }
        tooltip.add(insertIndex, Component.translatable("item.module.level.upgrade_cost", cost)
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /**
     * ⭐ 仅在安魂之融界面中显示裂罅循环费用（灰色）
     *
     * @param trend      裂罅倾向性（1~5）
     * @param cycleCount 当前已循环次数（从0开始）
     * @return 插入的行数（0或1）
     */
    public static int appendRivenCycleCostTooltipIfInEvolve(List<Component> tooltip, int insertIndex,
                                                            int trend, int cycleCount) {
        if (!isInEvolveMenu()) { return 0; }
        int cost = getRivenCycleCost(trend, cycleCount);
        if (cost <= 0) { return 0; }
        tooltip.add(insertIndex, Component.translatable("item.module.riven.cycle_cost", cost)
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    /**
     * ⭐ 计算裂罅循环费用（赤毒数量）
     * <p>
     * 公式：cost = max(1, round(64 × (min(cycle+1, 8) / 8) × (trend / 5)²))
     * <p>
     * 双维度：倾向性越高越贵，洗卡次数越多越贵，8次后封顶。
     * 5倾向+8次 = 64赤毒（上限）
     * <p>
     * 费率表（部分）：
     * <pre>
     * 倾向\次数  1次  2次  3次  4次  5次  6次  7次  8次+
     *   1        1    1    1    1    1    1    1    1
     *   2        1    2    2    3    3    4    4    4
     *   3        3    6    8   11   14   17   20   23
     *   4        5   10   16   21   26   31   36   41
     *   5        8   16   24   32   40   48   56   64
     * </pre>
     *
     * @param trend      倾向性等级（1~5）
     * @param cycleCount 当前已循环次数（getCycle返回值，0=尚未洗过，即将进行第1次）
     * @return 本次循环所需赤毒数量
     */
    public static int getRivenCycleCost(int trend, int cycleCount) {
        // cycleCount 是已完成的次数，本次是第 cycleCount+1 次
        int effectiveCycle = Math.min(cycleCount + 1, 8);
        double cycleFactor = effectiveCycle / 8.0;
        double trendFactor = Math.pow(trend / 5.0, 2);
        return Math.max(1, (int) Math.round(64.0 * cycleFactor * trendFactor));
    }

    /**
     * 检测当前玩家是否在安魂之融界面中
     */
    private static boolean isInEvolveMenu() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.containerMenu instanceof MenuRequiemEvolve;
        } catch (Exception e) {
            return false;
        }
    }

    private ModuleLevelTooltipHelper() {}
}