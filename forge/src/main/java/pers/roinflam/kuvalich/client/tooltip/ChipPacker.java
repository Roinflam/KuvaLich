package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 把一组「数值块」按像素预算打包成尽量少的行
 *
 * <p>这是把 55~65 行压到 18 行的主力：改造前「近战伤害 +180%」独占一行，
 * 而这行只用掉屏幕宽度的三分之一，右边三分之二白白浪费。</p>
 *
 * <p>⭐ <b>必须用 {@code font.width(Component)} 而不是 {@code font.width(String)}</b>：
 * 加粗样式下每个字符要多占 1px，量文本时把样式丢掉会系统性低估约 10%，
 * 中文满配时右边缘会直接穿出屏幕。</p>
 *
 * <p>⭐ 只做<b>流式打包</b>，不做多列表格。纯 {@code Component} 方案下想让列对齐
 * 只能靠空格近似，中文（一个汉字约等于两个字符宽）和不同字体包下必然错位。
 * 流式打包天然不需要对齐 —— 这是选这个排版方案的隐藏收益。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class ChipPacker {

    /** chip 之间的分隔（两个空格，比一个空格更容易区分「两条词条」和「一条词条的名值」） */
    private static final String SEPARATOR = "  ";

    /** 像素预算下限：再窄就会变成一行一个 chip，失去打包的意义 */
    private static final int MIN_BUDGET = 120;

    /** 距屏幕边缘至少留这么多像素，避免 tooltip 贴边被原版强行换行 */
    private static final int SCREEN_MARGIN = 40;

    /**
     * 计算当前分辨率下一行能用多少像素
     *
     * <p>⭐ 只做「物理上不可能」的钳制（下限 120px、上限不超过屏宽减边距），
     * <b>不再用一个固定的 320px 上限去否决配置</b>。
     * 原来那个写死的上限意味着：整合包作者把 {@code widthRatio} 调到 0.9，
     * 在 1920×1080 / GUI 缩放 2（可视宽 960）下算出 840px，却被一刀切回 320px ——
     * 配置形同虚设。</p>
     *
     * @param widthRatio 占屏宽的比例（配置项，默认 0.5）
     */
    public static int budget(double widthRatio) {
        Minecraft mc = Minecraft.getInstance();
        int screen = mc.getWindow().getGuiScaledWidth();
        int raw = (int) (screen * widthRatio) - 24;
        int hardMax = Math.max(MIN_BUDGET, screen - SCREEN_MARGIN);
        return Math.max(MIN_BUDGET, Math.min(hardMax, raw));
    }

    /**
     * 打包
     *
     * @param chips       待打包的数值块，顺序保持不变
     * @param firstPrefix 第一行的前缀（装订线 + 组名）
     * @param contPrefix  续行的前缀（装订线 + 等宽空白，让续行视觉上缩进到组名右侧）
     * @param budgetPx    一行的像素预算（含前缀）
     * @return 打包后的行
     */
    public static List<Component> pack(List<Component> chips,
                                       Component firstPrefix,
                                       Component contPrefix,
                                       int budgetPx) {
        List<Component> lines = new ArrayList<>();
        if (chips.isEmpty()) {
            return lines;
        }

        Font font = Minecraft.getInstance().font;
        int sepWidth = font.width(SEPARATOR);

        MutableComponent line = firstPrefix.copy();
        int used = font.width(firstPrefix);
        boolean lineEmpty = true;

        for (Component chip : chips) {
            int chipWidth = font.width(chip);
            int need = lineEmpty ? chipWidth : sepWidth + chipWidth;

            // 换行条件：放不下，且当前行已经有内容（否则单个超宽 chip 会导致空行）
            if (!lineEmpty && used + need > budgetPx) {
                lines.add(line);
                line = contPrefix.copy();
                used = font.width(contPrefix);
                lineEmpty = true;
                need = chipWidth;
            }

            if (!lineEmpty) {
                line.append(Component.literal(SEPARATOR));
            }
            line.append(chip);
            used += need;
            lineEmpty = false;
        }

        lines.add(line);
        return lines;
    }

    /**
     * 构造续行前缀：与第一行前缀等宽的空白（保证续行的 chip 与首行的 chip 左对齐）
     *
     * <p>用空格凑宽度只能做到「接近」，但续行本来就只需要视觉上缩进，
     * 不需要与上一行严格对齐，误差一两个像素看不出来。</p>
     */
    private ChipPacker() {
    }
}
