package pers.roinflam.kuvalich.module.level;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.tooltip.PanelPalette;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

import java.util.List;

/**
 * 模组等级Tooltip渲染辅助类（客户端专用）
 * <p>
 * 等级进度条：红→金→黄→绿渐变，满级不显示
 * 升级费用仅在安魂之融界面中以灰色显示
 * 裂罅循环费用仅在安魂之融界面中以灰色显示
 * <p>
 * ⭐ 升级费用已根据模组品质缩放：铜25% / 银50% / 金75% / Prime&裂罅100%
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

        // 四档进度色。原版 YELLOW(#FFFF55) 与 GOLD(#FFAA00) 在条上已经够近，
        // 这里刻意让四档跨越绿→青金→橙→红，相邻两档的色相差比原版更大。
        int filledColor;
        if (ratio > 0.8) { filledColor = PanelPalette.BONUS; }          // 接近满级
        else if (ratio > 0.5) { filledColor = PanelPalette.STACK_ACTIVE; }
        else if (ratio > 0.25) { filledColor = PanelPalette.WARN; }
        else { filledColor = PanelPalette.PENALTY; }                    // 刚起步

        MutableComponent barComponent = Component.literal("");

        StringBuilder filledBar = new StringBuilder();
        for (int i = 0; i < filledCount; i++) { filledBar.append(BAR_FILLED); }
        if (filledBar.length() > 0) {
            barComponent.append(Component.literal(filledBar.toString()).withStyle(PanelPalette.style(filledColor)));
        }

        StringBuilder emptyBar = new StringBuilder();
        for (int i = filledCount; i < BAR_LENGTH; i++) { emptyBar.append(BAR_EMPTY); }
        if (emptyBar.length() > 0) {
            // 空格必须看得见「还剩几格」——原版 DARK_GRAY(#555555) 在深色 tooltip 上基本消失，
            // PanelStyle 里的圆点也踩过同一个坑。
            barComponent.append(Component.literal(emptyBar.toString()).withStyle(PanelPalette.style(PanelPalette.DOT_OFF)));
        }

        // 颜色 + 粗体原先靠 withStyle(ChatFormatting...) 变长参数一次传两个枚举值，换 RGB 后要先拼 Style
        barComponent.append(Component.literal(" Lv." + currentLevel + "/" + maxLevel)
                .withStyle(PanelPalette.bold(PanelPalette.VALUE)));

        tooltip.add(insertIndex, barComponent);
        return 1;
    }

    /**
     * ⭐ 仅在安魂之融界面中显示升级费用（含品质缩放，灰色）
     *
     * @param moduleStack 模组物品栈（用于计算品质费用系数）
     * @return 插入的行数（0或1）
     */
    public static int appendUpgradeCostTooltipIfInEvolve(List<Component> tooltip, int insertIndex,
                                                         int currentLevel, ItemStack moduleStack) {
        if (!ModuleLevelHelper.isLevelSystemEnabled()) { return 0; }
        if (currentLevel >= ModuleLevelHelper.getMaxLevel()) { return 0; }
        if (!isInEvolveMenu()) { return 0; }
        int cost = ModuleLevelHelper.getUpgradeCost(currentLevel, moduleStack);
        if (cost <= 0) { return 0; }
        tooltip.add(insertIndex, Component.translatable("item.module.level.upgrade_cost", cost)
                .withStyle(PanelPalette.style(PanelPalette.MUTED)));
        return 1;
    }

    /**
     * ⭐ 仅在安魂之融界面中显示裂罅循环费用（灰色）
     * <p>
     * 区分武器裂罅和战甲裂罅，使用与实际消耗一致的公式。
     *
     * @param trend      裂罅倾向性（1~5）
     * @param cycleCount 当前已循环次数（从0开始）
     * @param isWarframe 是否为战甲裂罅（true=战甲，false=武器）
     * @return 插入的行数（0或1）
     */
    public static int appendRivenCycleCostTooltipIfInEvolve(List<Component> tooltip, int insertIndex,
                                                            int trend, int cycleCount,
                                                            boolean isWarframe) {
        if (!isInEvolveMenu()) { return 0; }
        int cost = isWarframe
                ? getWarframeRivenCycleCost(trend, cycleCount)
                : getWeaponRivenCycleCost(trend, cycleCount);
        if (cost <= 0) { return 0; }
        tooltip.add(insertIndex, Component.translatable("item.module.riven.cycle_cost", cost)
                .withStyle(PanelPalette.style(PanelPalette.MUTED)));
        return 1;
    }

    /**
     * 计算武器裂罅循环费用（赤毒数量）
     * <p>
     * 公式与 RequiemEvolveMenu.processItemRivenCycle 完全一致：
     * cost = min(cycleCount, 8) + trend² - (trend-1)²
     *      = min(cycleCount, 8) + 2×trend - 1
     * <p>
     * 费率表（部分）：
     * <pre>
     * 倾向\次数  0次  1次  2次  3次  4次  5次  6次  7次  8次+
     *   1        1    2    3    4    5    6    7    8    9
     *   2        3    4    5    6    7    8    9   10   11
     *   3        5    6    7    8    9   10   11   12   13
     *   4        7    8    9   10   11   12   13   14   15
     *   5        9   10   11   12   13   14   15   16   17
     * </pre>
     *
     * @param trend      倾向性等级（1~5）
     * @param cycleCount 当前已循环次数（getCycle返回值，0=尚未洗过）
     * @return 本次循环所需赤毒数量
     */
    public static int getWeaponRivenCycleCost(int trend, int cycleCount) {
        int baseCost = Math.min(cycleCount, 8);
        int trendCost = (int) (Math.pow(trend, 2) - Math.pow(trend - 1, 2));
        return baseCost + trendCost;
    }

    /**
     * 计算战甲裂罅循环费用（赤毒数量）
     * <p>
     * 公式与 RequiemEvolveMenu.processWarframeRivenCycle 完全一致：
     * cost = min(cycleCount, 8) + trend²
     * <p>
     * 费率表（部分）：
     * <pre>
     * 倾向\次数  0次  1次  2次  3次  4次  5次  6次  7次  8次+
     *   1        1    2    3    4    5    6    7    8    9
     *   2        4    5    6    7    8    9   10   11   12
     *   3        9   10   11   12   13   14   15   16   17
     *   4       16   17   18   19   20   21   22   23   24
     *   5       25   26   27   28   29   30   31   32   33
     * </pre>
     *
     * @param trend      倾向性等级（1~5）
     * @param cycleCount 当前已循环次数（getCycle返回值，0=尚未洗过）
     * @return 本次循环所需赤毒数量
     */
    public static int getWarframeRivenCycleCost(int trend, int cycleCount) {
        int baseCost = Math.min(cycleCount, 8);
        int trendCost = (int) Math.pow(trend, 2);
        return baseCost + trendCost;
    }

    /**
     * 检测当前玩家是否在安魂之融界面中
     */
    private static boolean isInEvolveMenu() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.containerMenu instanceof RequiemEvolveMenu;
        } catch (Exception e) {
            return false;
        }
    }

    private ModuleLevelTooltipHelper() {}
}
