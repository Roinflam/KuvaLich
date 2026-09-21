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

    /** 背景条左右各外扩多少像素（让底色包住内容而不是刚好贴边） */
    private static final int BAND_PAD = 2;

    /**
     * 估算要给「面板以外的东西」留多少像素高度
     *
     * <p>物品名一行、tooltip 背景上下各 3px 内边距，再加一点余量给同屏的其它内容
     * （TACZ 的枪械信息块本身就有十几行）。这里拿不到它们的真实高度，只能留一个保守值。</p>
     */
    private static final int RESERVED_HEIGHT = 40;

    /** 再挤也要留下的行数：宁可溢出也不能只画一两行 */
    private static final int MIN_ROWS = 6;

    // ==================== 排好版的结果 ====================

    /** 一段要绘制的文本：x 是左边界（LEFT）或右边界（RIGHT） */
    private record Piece(Component text, int x, PanelGridComponent.Align align) {
    }

    /** 一行：若干段文本 + 是否要画背景条 */
    private record Line(List<Piece> pieces, boolean band) {
    }

    private final List<Line> lines;
    private final int width;
    private final int rowHeight;

    private ClientPanelGrid(List<Line> lines, int width, int rowHeight) {
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
    public static ClientPanelGrid layout(PanelGridComponent data, Font font, int maxWidth, int screenHeight) {
        int rowHeight = TooltipConfig.PANEL.rowHeight.get();

        // ⭐ 屏幕高度参与的是<b>列数选择</b>，不是截断。
        //    屏幕矮时把内容压成多列（更宽更矮）能放下更多，这是有意义的；
        //    但真放不下时<b>不截断</b> —— 整合包里有支持按住 SHIFT 滚动 tooltip 的模组，
        //    截掉的内容玩家本来是能滚出来看的，截了反而是帮倒忙。
        int screenRows = Math.max(MIN_ROWS, (screenHeight - RESERVED_HEIGHT) / Math.max(1, rowHeight));

        // ⭐ 列数目标：四个视图<b>必须用同一个值</b>。
        //
        //    列数是按行数预算反推的（下面那个循环：先试一列，够用就停），
        //    所以预算不同 = 列数不同 = 排版不同。曾经给功能键视图放宽过这个预算，
        //    结果屏幕一高，SHIFT 视图的一列排版就「够用」了，于是停在一列、每条一行，
        //    而默认视图还是两列 —— 同一把武器按下 SHIFT 像换了个模组。
        int layoutTarget = Math.min(TooltipConfig.PANEL.maxLines.get(), screenRows);

        int columnCap = data.maxColumns() > 0 ? Math.min(data.maxColumns(), MAX_COLUMNS) : MAX_COLUMNS;

        Attempt best = null;
        for (int columns = 1; columns <= columnCap; columns++) {
            Attempt attempt = build(data, font, columns);

            if (attempt.width > maxWidth) {
                // 再加列只会更宽，用上一个能放下的方案
                break;
            }
            best = attempt;
            if (attempt.lines.size() <= layoutTarget) {
                break;
            }
        }

        if (best == null) {
            // 连一列都塞不进 maxWidth（极窄屏 / 极端 GUI 缩放）：还是按一列画，让它自己溢出，
            // 总比整块不显示强
            best = build(data, font, 1);
        }

        List<Line> lines = best.lines;
        int width = best.width;
        for (Line line : lines) {
            for (Piece piece : line.pieces()) {
                int right = piece.align() == PanelGridComponent.Align.RIGHT
                        ? piece.x() : piece.x() + font.width(piece.text());
                width = Math.max(width, right);
            }
        }
        return new ClientPanelGrid(lines, width, rowHeight);
    }

    private record Attempt(List<Line> lines, int width) {
    }

    private static Attempt build(PanelGridComponent data, Font font, int columns) {
        List<Line> out = new ArrayList<>();
        int maxWidth = 0;
        // 只对「有标题的分组」交替上底色：Forma 锁定行、按键提示行这种游离的单行不参与
        int bandIndex = 0;

        for (PanelGridComponent.Section section : data.sections()) {
            boolean band = false;
            if (section.header() != null) {
                // ⭐ 标题独立成行、不缩进，内容缩进在它下面 —— 层次靠位置和颜色，不靠符号
                out.add(new Line(List.of(new Piece(section.header(), 0, PanelGridComponent.Align.LEFT)), false));
                maxWidth = Math.max(maxWidth, font.width(section.header()));
                band = bandIndex++ % 2 == 0;
            }

            List<List<Piece>> rows = new ArrayList<>();
            if (section instanceof PanelGridComponent.Pairs pairs) {
                maxWidth = Math.max(maxWidth, layoutPairs(pairs, font, columns, rows));
            } else if (section instanceof PanelGridComponent.Table table) {
                maxWidth = Math.max(maxWidth, layoutTable(table, font, rows));
            } else if (section instanceof PanelGridComponent.Columns cols) {
                maxWidth = Math.max(maxWidth, layoutColumns(cols, font, columns, rows));
            } else if (section instanceof PanelGridComponent.Flow flow) {
                for (Component line : flow.lines()) {
                    rows.add(List.of(new Piece(line, INDENT, PanelGridComponent.Align.LEFT)));
                    maxWidth = Math.max(maxWidth, INDENT + font.width(line));
                }
            }
            for (List<Piece> row : rows) {
                out.add(new Line(row, band));
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

        // ⭐ 只要组里有一条「排不进格子」的宽词条，<b>整组</b>就退回单列。
        //
        //    试过只让那一条独占行、其余照常分列，结果更难看：同一组里出现了三个
        //    不同的右边界（第一列的值、第二列的值、独占行的值各对齐各的），
        //    数值参差不齐。单列虽然多占几行，但所有数值共用一个右边界，
        //    一眼扫下去是一条直线。
        for (PanelGridComponent.Cell cell : cells) {
            if (cell.wide()) {
                return layoutSingleColumn(cells, font, out);
            }
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

    /**
     * 整组单列：一条一行，所有数值共用同一个右边界
     *
     * <p>保持声明顺序，不把宽词条挪到末尾 —— 顺序本身是有意义的
     * （「最终数值」组就是按基础伤害 → 攻击速度 → 触发几率 → 暴击的顺序读的）。</p>
     *
     * @return 这一组占到的右边界
     */
    private static int layoutSingleColumn(List<PanelGridComponent.Cell> cells, Font font, List<List<Piece>> out) {
        int right = INDENT;
        for (PanelGridComponent.Cell cell : cells) {
            right = Math.max(right,
                    INDENT + font.width(cell.label()) + LABEL_VALUE_GAP + font.width(cell.value()));
        }
        for (PanelGridComponent.Cell cell : cells) {
            out.add(List.of(
                    new Piece(cell.label(), INDENT, PanelGridComponent.Align.LEFT),
                    new Piece(cell.value(), right, PanelGridComponent.Align.RIGHT)));
        }
        return right;
    }

    /**
     * 单值多列分组（已装模组名单）：把一串条目按列数自适应地排成对齐的几列
     *
     * <p>为什么不继续用流式打包：流式排出来行尾会挂一个孤零零的分隔符，
     * 而且条目左边界参差不齐，跟面板其余部分那套「列对齐」的视觉完全不是一路。</p>
     */
    private static int layoutColumns(PanelGridComponent.Columns section, Font font, int columns,
                                     List<List<Piece>> out) {
        List<Component> items = section.items();
        if (items.isEmpty()) {
            return 0;
        }
        // 条目少时不必强行分列；条目多时跟随全局列数，但至少两列，否则 8 张卡要占 8 行
        int cols = items.size() <= 2 ? items.size() : Math.max(2, Math.min(columns, items.size()));
        int rows = (items.size() + cols - 1) / cols;

        int[] colW = new int[cols];
        for (int i = 0; i < items.size(); i++) {
            int c = i % cols;
            colW[c] = Math.max(colW[c], font.width(items.get(i)));
        }

        int[] colX = new int[cols];
        int cursor = INDENT;
        for (int c = 0; c < cols; c++) {
            colX[c] = cursor;
            cursor += colW[c] + COLUMN_GAP;
        }

        for (int r = 0; r < rows; r++) {
            List<Piece> line = new ArrayList<>(cols);
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                if (idx >= items.size()) {
                    break;
                }
                line.add(new Piece(items.get(idx), colX[c], PanelGridComponent.Align.LEFT));
            }
            out.add(line);
        }
        return cursor - COLUMN_GAP;
    }

    /** 固定列结构的表格（叠层）：列宽取该列最宽的内容 */
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
        for (Line line : lines) {
            for (Piece piece : line.pieces()) {
                int drawX = piece.align() == PanelGridComponent.Align.RIGHT
                        ? piece.x() - font.width(piece.text())
                        : piece.x();
                font.drawInBatch(piece.text().getVisualOrderText(), x + drawX, lineY, -1, true,
                        matrix, buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            }
            lineY += rowHeight;
        }
    }

    /**
     * 分组背景条
     *
     * <p>⚠️ 1.20.1 的 {@code GuiGraphics#renderTooltipInternal} 是先 {@code renderText}
     * 再 {@code renderImage}，两者同在 z=400 的 pose 里（已在字节码层确认：
     * renderText@292 → renderImage@369 → popPose@407）。所以这里要把 z 往回挪一点，
     * 让底色落到文字下面。颜色本身透明度很低，即便哪天渲染流程被别的模组换掉、z 没压住，
     * 也只是一层很淡的洗色，不会糊住字。</p>
     */
    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (!TooltipConfig.PANEL.showGroupBands.get()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, -1);
        int lineY = y;
        for (Line line : lines) {
            if (line.band()) {
                graphics.fill(x - BAND_PAD, lineY - 1,
                        x + width + BAND_PAD, lineY + rowHeight - 1, PanelPalette.GROUP_BAND);
            }
            lineY += rowHeight;
        }
        graphics.pose().popPose();
    }
}
