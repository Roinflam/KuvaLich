package pers.roinflam.kuvalich.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.config.TooltipConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器面板的渲染器：按像素算列宽，标签左对齐、数值右对齐，列数自适应
 *
 * <p><b>自适应规则</b>：先按一列（一条属性一行）排；如果总行数超过
 * {@code panel.maxLines}，再试两列、三列，取<b>第一个放得下</b>的方案。
 * 属性少的武器因此保持「一条一行」的宽松排版（这是改造前就很好读的形态），
 * 只有毕业武器那种四五十条词条才会被压成多列。</p>
 *
 * <p><b>为什么列数不是越多越好</b>：每加一列 tooltip 就宽一截，
 * 而「铺满屏幕」是竖着横着两个方向的事。第一版无条件流式打包把一把只装了
 * 一张卡的 AKM 也挤成一行，行数是少了，tooltip 却被撑得比 TACZ 自己的行宽一倍，
 * 名字还被迫缩成两个字 —— 两头不讨好。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPanelGrid implements ClientTooltipComponent {

    /** 最多排几列。再多列宽就压不住了，而且人眼横向扫描超过三组就开始费劲 */
    private static final int MAX_COLUMNS = 3;

    /** 分组内容相对标题的缩进 */
    private static final int INDENT = 4;

    /** 同一列里「标签」与「数值」之间的最小间隙 */
    private static final int LABEL_VALUE_GAP = 8;

    /** 相邻两列之间的间隙 */
    private static final int COLUMN_GAP = 10;

    // ==================== 排好版的结果 ====================

    /** 一段要绘制的文本：x 是左边界（LEFT）或右边界（RIGHT） */
    private record Piece(Component text, int x, PanelGridComponent.Align align) {
    }

    private final List<List<Piece>> lines;
    private final int width;
    private final int rowHeight;

    private ClientPanelGrid(List<List<Piece>> lines, int width, int rowHeight) {
        this.lines = lines;
        this.width = width;
        this.rowHeight = rowHeight;
    }

    // ==================== 排版 ====================

    /**
     * 排版
     *
     * @param data     结构化内容
     * @param font     字体（列宽全部按它实测，不做任何字符数估算）
     * @param maxWidth 允许的最大宽度（像素）
     */
    public static ClientPanelGrid layout(PanelGridComponent data, Font font, int maxWidth) {
        int rowHeight = TooltipConfig.PANEL.rowHeight.get();
        int maxLines = TooltipConfig.PANEL.maxLines.get();

        int columnCap = data.maxColumns() > 0 ? Math.min(data.maxColumns(), MAX_COLUMNS) : MAX_COLUMNS;

        Attempt best = null;
        for (int columns = 1; columns <= columnCap; columns++) {
            Attempt attempt = build(data, font, columns);

            if (attempt.width > maxWidth) {
                // 再加列只会更宽，用上一个能放下的方案
                break;
            }
            best = attempt;
            if (attempt.lines.size() <= maxLines) {
                break;
            }
        }

        if (best == null) {
            // 连一列都塞不进 maxWidth（极窄屏 / 极端 GUI 缩放）：还是按一列画，让它自己溢出，
            // 总比整块不显示强
            best = build(data, font, 1);
        }

        List<List<Piece>> lines = truncate(best.lines, maxLines, font);
        int width = best.width;
        for (List<Piece> line : lines) {
            for (Piece piece : line) {
                int right = piece.align() == PanelGridComponent.Align.RIGHT
                        ? piece.x() : piece.x() + font.width(piece.text());
                width = Math.max(width, right);
            }
        }
        return new ClientPanelGrid(lines, width, rowHeight);
    }

    /**
     * 行数硬上限兜底
     *
     * <p>1.20.1 原版对超高 tooltip 只做纵向钳位，<b>既不裁剪也不滚动</b> ——
     * 高度超过屏幕时上下同时溢出，连物品名都会被切掉。
     * 多列排版已经把最坏情况压下去很多，但毕业武器仍可能超出，所以要有这条硬约束。</p>
     *
     * <p>截断从<b>末尾</b>开始，而分组顺序刻意把「核心面板 → 叠层 → 元素」排在最前，
     * 所以先被丢掉的一定是静态词条明细，不会是玩家最关心的实时状态。</p>
     */
    private static List<List<Piece>> truncate(List<List<Piece>> lines, int maxLines, Font font) {
        if (lines.size() <= maxLines) {
            return lines;
        }
        int keep = Math.max(1, maxLines - 1);
        List<List<Piece>> out = new ArrayList<>(lines.subList(0, keep));
        Component note = Component.translatable("kuvalich.panel.truncated", lines.size() - keep)
                .withStyle(PanelPalette.italic(PanelPalette.WARN));
        out.add(List.of(new Piece(note, 0, PanelGridComponent.Align.LEFT)));
        return out;
    }

    private record Attempt(List<List<Piece>> lines, int width) {
    }

    private static Attempt build(PanelGridComponent data, Font font, int columns) {
        List<List<Piece>> out = new ArrayList<>();
        int maxWidth = 0;

        for (PanelGridComponent.Section section : data.sections()) {
            if (section.header() != null) {
                // ⭐ 标题独立成行、不缩进，内容缩进在它下面 —— 层次靠位置和颜色，不靠符号
                out.add(List.of(new Piece(section.header(), 0, PanelGridComponent.Align.LEFT)));
                maxWidth = Math.max(maxWidth, font.width(section.header()));
            }

            if (section instanceof PanelGridComponent.Pairs pairs) {
                maxWidth = Math.max(maxWidth, layoutPairs(pairs, font, columns, out));
            } else if (section instanceof PanelGridComponent.Table table) {
                maxWidth = Math.max(maxWidth, layoutTable(table, font, out));
            } else if (section instanceof PanelGridComponent.Flow flow) {
                for (Component line : flow.lines()) {
                    out.add(List.of(new Piece(line, INDENT, PanelGridComponent.Align.LEFT)));
                    maxWidth = Math.max(maxWidth, INDENT + font.width(line));
                }
            }
        }

        return new Attempt(out, maxWidth);
    }

    /**
     * 「标签 + 数值」分组的排版
     *
     * <p>行优先填充：先从左到右填满一行，再换下一行。这样自然阅读顺序
     * 与描述表里的声明顺序一致。</p>
     */
    private static int layoutPairs(PanelGridComponent.Pairs pairs, Font font, int columns, List<List<Piece>> out) {
        List<PanelGridComponent.Cell> cells = pairs.cells();
        if (cells.isEmpty()) {
            return 0;
        }
        int cols = Math.min(columns, cells.size());
        int rows = (cells.size() + cols - 1) / cols;

        // 每一列各自算标签宽与数值宽 —— 列宽按该列实际内容定，不搞全局统一宽度，
        // 否则一条特别长的词条会把所有列都撑开
        int[] labelW = new int[cols];
        int[] valueW = new int[cols];
        for (int i = 0; i < cells.size(); i++) {
            int c = i % cols;
            labelW[c] = Math.max(labelW[c], font.width(cells.get(i).label()));
            valueW[c] = Math.max(valueW[c], font.width(cells.get(i).value()));
        }

        int[] colX = new int[cols];
        int[] colRight = new int[cols];
        int cursor = INDENT;
        for (int c = 0; c < cols; c++) {
            colX[c] = cursor;
            colRight[c] = cursor + labelW[c] + LABEL_VALUE_GAP + valueW[c];
            cursor = colRight[c] + COLUMN_GAP;
        }

        for (int r = 0; r < rows; r++) {
            List<Piece> line = new ArrayList<>(cols * 2);
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                if (idx >= cells.size()) {
                    break;
                }
                PanelGridComponent.Cell cell = cells.get(idx);
                line.add(new Piece(cell.label(), colX[c], PanelGridComponent.Align.LEFT));
                line.add(new Piece(cell.value(), colRight[c], PanelGridComponent.Align.RIGHT));
            }
            out.add(line);
        }

        return colRight[cols - 1];
    }

    /** 固定列结构的表格（叠层 / 元素）：列宽取该列最宽的内容 */
    private static int layoutTable(PanelGridComponent.Table table, Font font, List<List<Piece>> out) {
        if (table.rows().isEmpty()) {
            return 0;
        }
        int cols = table.aligns().length;
        int[] colW = new int[cols];
        for (List<Component> row : table.rows()) {
            for (int c = 0; c < cols && c < row.size(); c++) {
                colW[c] = Math.max(colW[c], font.width(row.get(c)));
            }
        }

        int[] colX = new int[cols];
        int[] colRight = new int[cols];
        int cursor = INDENT;
        for (int c = 0; c < cols; c++) {
            colX[c] = cursor;
            colRight[c] = cursor + colW[c];
            cursor = colRight[c] + COLUMN_GAP;
        }

        for (List<Component> row : table.rows()) {
            List<Piece> line = new ArrayList<>(cols);
            for (int c = 0; c < cols && c < row.size(); c++) {
                PanelGridComponent.Align align = table.aligns()[c];
                int x = align == PanelGridComponent.Align.RIGHT ? colRight[c] : colX[c];
                line.add(new Piece(row.get(c), x, align));
            }
            out.add(line);
        }

        return colRight[cols - 1];
    }

    // ==================== ClientTooltipComponent ====================

    @Override
    public int getHeight() {
        return lines.size() * rowHeight;
    }

    @Override
    public int getWidth(Font font) {
        return width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        int lineY = y;
        for (List<Piece> line : lines) {
            for (Piece piece : line) {
                int drawX = piece.align() == PanelGridComponent.Align.RIGHT
                        ? piece.x() - font.width(piece.text())
                        : piece.x();
                font.drawInBatch(piece.text().getVisualOrderText(), x + drawX, lineY, -1, true,
                        matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
            lineY += rowHeight;
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        // 纯文本，不画图
    }
}
