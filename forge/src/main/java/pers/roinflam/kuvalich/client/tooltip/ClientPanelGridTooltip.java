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
 * 武器面板的渲染器：按像素算列宽，标签与数值各自左对齐成两条竖线，列数自适应
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
public final class ClientPanelGridTooltip implements ClientTooltipComponent {

    /** 最多排几列。再多列宽就压不住了，而且人眼横向扫描超过三组就开始费劲 */
    private static final int MAX_COLUMNS = 3;

    /** 分组内容相对标题的缩进 */
    private static final int INDENT = 4;

    /** 同一列里「标签」与「数值」之间的最小间隙 */
    private static final int LABEL_VALUE_GAP = 8;

    /** 相邻两列之间的间隙 */
    private static final int COLUMN_GAP = 10;

    /** 模组名单的列间隙：卡名各有品质颜色，靠颜色就分得开，不必像「标签 数值」那样留宽缝 */
    private static final int NAME_GAP = 6;

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
    private record Piece(Component text, int x, PanelGridTooltip.Align align) {
    }

    /** 一行：若干段文本 + 是否要画背景条 */
    private record Line(List<Piece> pieces, boolean band) {
    }

    private final List<Line> lines;
    private final int width;
    private final int rowHeight;

    private ClientPanelGridTooltip(List<Line> lines, int width, int rowHeight) {
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
    public static ClientPanelGridTooltip layout(PanelGridTooltip data, Font font, int maxWidth, int screenHeight) {
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
            Attempt attempt = build(data, font, columns, maxWidth);

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
            best = build(data, font, 1, maxWidth);
        }

        List<Line> lines = best.lines;
        int width = best.width;
        for (Line line : lines) {
            for (Piece piece : line.pieces()) {
                int right = piece.align() == PanelGridTooltip.Align.RIGHT
                        ? piece.x() : piece.x() + font.width(piece.text());
                width = Math.max(width, right);
            }
        }
        return new ClientPanelGridTooltip(lines, width, rowHeight);
    }

    private record Attempt(List<Line> lines, int width) {
    }

    private static Attempt build(PanelGridTooltip data, Font font, int columns, int widthCap) {
        List<PanelGridTooltip.Section> sections = data.sections();
        List<List<List<Piece>>> sectionRows = new ArrayList<>(sections.size());
        int maxWidth = 0;

        // 第一遍：除模组名单外的分组先排，得出面板本来就有的宽度
        for (PanelGridTooltip.Section section : sections) {
            if (section.header() != null) {
                maxWidth = Math.max(maxWidth, font.width(section.header()));
            }
            List<List<Piece>> rows = new ArrayList<>();
            if (section instanceof PanelGridTooltip.Pairs pairs) {
                maxWidth = Math.max(maxWidth, layoutPairs(pairs, font, columns, rows));
            } else if (section instanceof PanelGridTooltip.Table table) {
                maxWidth = Math.max(maxWidth, layoutTable(table, font, rows));
            } else if (section instanceof PanelGridTooltip.Flow flow) {
                for (Component line : flow.lines()) {
                    rows.add(List.of(new Piece(line, INDENT, PanelGridTooltip.Align.LEFT)));
                    maxWidth = Math.max(maxWidth, INDENT + font.width(line));
                }
            }
            sectionRows.add(rows);
        }

        // 第二遍：模组名单可以往宽里多排一列来消掉末行空格，上限是面板允许的最大宽度
        //
        // ⭐ 曾经把上限定为「其余分组的宽度」，结果装满 8 张 Prime 卡时名字太长，四列永远超预算，
        //    末行空格照旧。三列模式本来就是因为太高才启用的，拿宽度换掉一行 + 空格正合其意。
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i) instanceof PanelGridTooltip.Columns cols) {
                maxWidth = Math.max(maxWidth, layoutColumns(cols, font, columns, widthCap, sectionRows.get(i)));
            }
        }

        List<Line> out = new ArrayList<>();
        // 只对「有标题的分组」交替上底色：Forma 锁定行这种游离的单行不参与
        int bandIndex = 0;
        for (int i = 0; i < sections.size(); i++) {
            PanelGridTooltip.Section section = sections.get(i);
            boolean band = false;
            if (section.header() != null) {
                // ⭐ 标题独立成行、不缩进，内容缩进在它下面 —— 层次靠位置和颜色，不靠符号
                out.add(new Line(List.of(new Piece(section.header(), 0, PanelGridTooltip.Align.LEFT)), false));
                band = bandIndex++ % 2 == 0;
            }
            for (List<Piece> row : sectionRows.get(i)) {
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
    private static int layoutPairs(PanelGridTooltip.Pairs pairs, Font font, int columns, List<List<Piece>> out) {
        List<PanelGridTooltip.Cell> cells = pairs.cells();
        if (cells.isEmpty()) {
            return 0;
        }

        // ⭐ 只要组里有一条「排不进格子」的宽词条，<b>整组</b>就退回单列。
        //
        //    试过只让那一条独占行、其余照常分列，结果更难看：同一组里出现了三个
        //    不同的数值起点（第一列的值、第二列的值、独占行的值各对齐各的），
        //    数值参差不齐。单列虽然多占几行，但所有数值共用一条起始竖线，
        //    一眼扫下去是一条直线。
        for (PanelGridTooltip.Cell cell : cells) {
            if (cell.wide()) {
                return layoutSingleColumn(cells, font, out);
            }
        }

        int cols = Math.min(columns, cells.size());
        int rows = (cells.size() + cols - 1) / cols;

        // 每一列各自算标签宽与数值宽 —— 列宽按该列实际内容定，不搞全局统一宽度，
        // 否则一条特别长的词条会把所有列都撑开
        //
        // ⭐ 数值左对齐（紧跟标签列之后），不贴列右边界。原先右对齐时，同列里一个长值
        //    会把短值往右推，短值与自己标签的间隙大过列间距，看起来像是下一列的值。
        //    各行单位混杂（% / x / m），右对齐带来的「上下比大小」本来也用不上。
        //    击杀叠层表（layoutTable）的数值列是同类加成，那里仍按各列声明的对齐方式（右对齐）。
        int[] labelW = new int[cols];
        int[] valueW = new int[cols];
        for (int i = 0; i < cells.size(); i++) {
            int c = i % cols;
            labelW[c] = Math.max(labelW[c], font.width(cells.get(i).label()));
            valueW[c] = Math.max(valueW[c], font.width(cells.get(i).value()));
        }

        int[] colX = new int[cols];
        int[] valueX = new int[cols];
        int[] colRight = new int[cols];
        int cursor = INDENT;
        for (int c = 0; c < cols; c++) {
            colX[c] = cursor;
            valueX[c] = cursor + labelW[c] + LABEL_VALUE_GAP;
            colRight[c] = valueX[c] + valueW[c];
            cursor = colRight[c] + COLUMN_GAP;
        }

        for (int r = 0; r < rows; r++) {
            List<Piece> line = new ArrayList<>(cols * 2);
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                if (idx >= cells.size()) {
                    break;
                }
                PanelGridTooltip.Cell cell = cells.get(idx);
                line.add(new Piece(cell.label(), colX[c], PanelGridTooltip.Align.LEFT));
                line.add(new Piece(cell.value(), valueX[c], PanelGridTooltip.Align.LEFT));
            }
            out.add(line);
        }

        return colRight[cols - 1];
    }

    /**
     * 整组单列：一条一行，所有数值共用同一条起始竖线（左对齐，理由见 {@link #layoutPairs}）
     *
     * <p>保持声明顺序，不把宽词条挪到末尾 —— 顺序本身是有意义的
     * （「最终数值」组就是按基础伤害 → 攻击速度 → 触发几率 → 暴击的顺序读的）。</p>
     *
     * @return 这一组占到的右边界
     */
    private static int layoutSingleColumn(List<PanelGridTooltip.Cell> cells, Font font, List<List<Piece>> out) {
        int labelW = 0;
        int valueW = 0;
        for (PanelGridTooltip.Cell cell : cells) {
            labelW = Math.max(labelW, font.width(cell.label()));
            valueW = Math.max(valueW, font.width(cell.value()));
        }
        int valueX = INDENT + labelW + LABEL_VALUE_GAP;
        for (PanelGridTooltip.Cell cell : cells) {
            out.add(List.of(
                    new Piece(cell.label(), INDENT, PanelGridTooltip.Align.LEFT),
                    new Piece(cell.value(), valueX, PanelGridTooltip.Align.LEFT)));
        }
        return valueX + valueW;
    }

    /**
     * 单值多列分组（已装模组名单）：把一串条目按列数自适应地排成对齐的几列
     *
     * <p>为什么不继续用流式打包：流式排出来行尾会挂一个孤零零的分隔符，
     * 而且条目左边界参差不齐，跟面板其余部分那套「列对齐」的视觉完全不是一路。</p>
     *
     * <p>⭐ 列数不死跟全局列数。装满 8 张卡时全局三列会排成 3+3+2，末行空一格很扎眼；
     * 所以在「不增加行数、不超过面板最大宽度」的前提下，挑末行空格最少的列数 ——
     * 8 张卡就变成 4×2（还少占一行）。只有窄屏下四列连最大宽度都放不下时才维持原列数。</p>
     *
     * @param budget 面板允许的最大宽度；多排一列不许超过它
     */
    private static int layoutColumns(PanelGridTooltip.Columns section, Font font, int columns, int budget,
                                     List<List<Piece>> out) {
        List<Component> items = section.items();
        if (items.isEmpty()) {
            return 0;
        }
        int n = items.size();
        // 条目少时不必强行分列；条目多时跟随全局列数，但至少两列，否则 8 张卡要占 8 行
        int base = n <= 2 ? n : Math.max(2, Math.min(columns, n));
        int baseRows = (n + base - 1) / base;
        int allowedWidth = Math.max(budget, columnsWidth(items, font, base));

        int cols = base;
        int bestEmpty = base * baseRows - n;
        for (int c = 2; c <= Math.min(n, base + 1) && bestEmpty > 0; c++) {
            int rows = (n + c - 1) / c;
            int empty = c * rows - n;
            if (c == base || rows > baseRows || empty >= bestEmpty) {
                continue;
            }
            if (columnsWidth(items, font, c) > allowedWidth) {
                continue;
            }
            cols = c;
            bestEmpty = empty;
        }

        int[][] grid = arrange(items, font, cols);
        int[] colW = columnWidths(grid, items, font);
        int[] colX = new int[colW.length];
        int cursor = INDENT;
        for (int c = 0; c < colW.length; c++) {
            colX[c] = cursor;
            cursor += colW[c] + NAME_GAP;
        }

        for (int[] row : grid) {
            List<Piece> line = new ArrayList<>(row.length);
            for (int c = 0; c < row.length; c++) {
                if (row[c] >= 0) {
                    line.add(new Piece(items.get(row[c]), colX[c], PanelGridTooltip.Align.LEFT));
                }
            }
            out.add(line);
        }
        return cursor - NAME_GAP;
    }

    /**
     * 把条目摆进 {@code cols} 列：返回 [行][列] 的条目下标，空位为 -1
     *
     * <p>⭐ 多行时<b>按名字宽度从长到短、逐列往下填</b>，而不是按装配顺序逐行填。
     * 每列宽度取该列最长的名字，按装配顺序排时「屠魔圣典 Prime」和「辐射弹药」会落在同一列，
     * 短名后面白白空出一大截；长名跟长名、短名跟短名放一起，整块能窄一截
     * （满装 8 张 Prime 为主的卡约省 30px）。代价是名单不再对应槽位顺序 —— 名单只用来认卡，
     * 槽位顺序对玩家没有意义。只有一行时保持原顺序（一行怎么排总宽都一样）。</p>
     */
    private static int[][] arrange(List<Component> items, Font font, int cols) {
        int n = items.size();
        int rows = (n + cols - 1) / cols;
        if (rows > 1) {
            // 逐列往下填时实际用到的列数（如 6 条要 4 列 → 两行 → 只用满 3 列），别留一整列空的
            cols = (n + rows - 1) / rows;
        }
        int[][] grid = new int[rows][cols];
        for (int[] row : grid) {
            java.util.Arrays.fill(row, -1);
        }
        if (rows == 1) {
            for (int i = 0; i < n; i++) {
                grid[0][i] = i;
            }
            return grid;
        }
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        // 稳定排序：等宽的名字保持原先的相对顺序
        order.sort((a, b) -> Integer.compare(font.width(items.get(b)), font.width(items.get(a))));
        for (int k = 0; k < n; k++) {
            grid[k % rows][k / rows] = order.get(k);
        }
        return grid;
    }

    /** 每一列的宽度（取该列最长的名字） */
    private static int[] columnWidths(int[][] grid, List<Component> items, Font font) {
        int[] colW = new int[grid[0].length];
        for (int[] row : grid) {
            for (int c = 0; c < row.length; c++) {
                if (row[c] >= 0) {
                    colW[c] = Math.max(colW[c], font.width(items.get(row[c])));
                }
            }
        }
        return colW;
    }

    /** 按 {@code cols} 列摆开后的右边界 */
    private static int columnsWidth(List<Component> items, Font font, int cols) {
        int width = INDENT - NAME_GAP;
        for (int w : columnWidths(arrange(items, font, cols), items, font)) {
            width += w + NAME_GAP;
        }
        return width;
    }

    /** 固定列结构的表格（叠层）：列宽取该列最宽的内容 */
    private static int layoutTable(PanelGridTooltip.Table table, Font font, List<List<Piece>> out) {
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
                PanelGridTooltip.Align align = table.aligns()[c];
                int x = align == PanelGridTooltip.Align.RIGHT ? colRight[c] : colX[c];
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
                int drawX = piece.align() == PanelGridTooltip.Align.RIGHT
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
