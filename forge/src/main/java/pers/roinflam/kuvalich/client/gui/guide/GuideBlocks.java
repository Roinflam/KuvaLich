package pers.roinflam.kuvalich.client.gui.guide;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Block;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.BlockType;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Rich;
import pers.roinflam.kuvalich.client.gui.guide.GuideTextLayout.Line;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.EntryView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 各类块的排版与绘制
 *
 * <p>每种块分两步：{@code layout} 在宽度确定时把文字折好行、把表格列宽算好、把配方查好，
 * 得到一个只含坐标和预建字形序列的 {@link Laid}；{@code render} 每帧只按这些结果画，
 * 不解析、不测宽、不分配。排版结果由 {@link GuideReader} 按（条目、宽度、配置版本）缓存。</p>
 *
 * <p>视觉上每种块都沿用图鉴的几何语言：结构靠 1px 线和极淡的底色分层，
 * 表头、公式框、步骤徽记用科技青，只有悬停中的东西才会出现赤毒猩红。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideBlocks {

    private static final int LH = GuideTheme.LINE_H;

    private GuideBlocks() {}

    /**
     * 排好版的块
     */
    abstract static class Laid {
        /** 块高度（不含块间距） */
        int height;
        /** 块类型；标题块、提示语块为 null */
        @Nullable
        BlockType type;

        /**
         * @param r 绘制上下文
         * @param x 块左
         * @param y 块顶
         * @param w 块宽（= 排版宽度）
         */
        abstract void render(GuideRender r, int x, int y, int w);
    }

    /**
     * 排版时需要的东西
     */
    static final class Ctx {
        final GuideView view;
        final GuideTextLayout text;
        final GuideMedia media;
        final int width;
        /**
         * 键值表标签列的最小自然宽度：同一篇里、中间只隔着小标题 / 段落 / 提示框 / 分隔线 / 公式的
         * 几张键值表共用一个标签列宽，否则各按各的最长标签定宽，上下几张表的中缝错开
         * （「数值速查」是小标题和键值表交替排的，最明显）。
         * 由 {@link GuideReader} 在排每张键值表前按它所在的那一段设置，其余块为 0。
         */
        int kvLabelHint;

        Ctx(GuideView view, GuideTextLayout text, GuideMedia media, int width) {
            this.view = view;
            this.text = text;
            this.media = media;
            this.width = width;
        }

        Line[] wrap(@Nullable String raw, int color, boolean bold, int w) {
            return text.wrap(view.rich(raw, color, bold), w);
        }
    }

    // ==================== 工厂 ====================

    /**
     * 排一个块
     *
     * @param b 块
     * @param c 排版上下文
     * @return 排好的块；应当整块隐藏时（查不到配方、空列表）返回 null
     */
    @Nullable
    static Laid layout(Block b, Ctx c) {
        Laid laid = switch (b.type) {
            case H -> new Heading(c.wrap(b.text, GuideTheme.HEADING, false, c.width - 7));
            case P -> new Paragraph(c.wrap(b.text, GuideTheme.BODY, false, c.width));
            case LIST -> ListLaid.of(b, c, b.ordered ? ListLaid.ORDERED : ListLaid.BULLET);
            case STEPS -> ListLaid.of(b, c, ListLaid.STEPS);
            case KV -> KvLaid.of(b, c);
            case TABLE -> TableLaid.of(b, c);
            case TIP -> TipLaid.of(b, c);
            case FORMULA -> new FormulaLaid(c.wrap(b.text, GuideTheme.BONE, false, c.width - 24));
            case ITEMS -> ItemsLaid.of(b, c);
            case RECIPE -> {
                GuideMedia.RecipeData data = c.media.recipe(b.item, b.recipe);
                yield data == null ? null : new RecipeLaid(data, c);
            }
            case ENTITY -> new EntityLaid(b.entity, b.height, c);
            case DIVIDER -> new Divider();
        };
        if (laid != null) {
            laid.type = b.type;
        }
        return laid;
    }

    /**
     * 键值表标签列的自然宽度（最长标签不折行时的宽度）
     *
     * @param b 键值表块
     * @param c 排版上下文
     * @return 像素宽
     */
    static int kvNaturalLabelWidth(Block b, Ctx c) {
        int natural = 0;
        for (List<String> row : b.rows) {
            natural = Math.max(natural, c.text.naturalWidth(c.view.rich(row.get(0), GuideTheme.LABEL, false)));
        }
        return natural;
    }

    /**
     * 篇末的「上一篇 / 下一篇」
     *
     * @param prev 全书顺序里的上一篇（可空）
     * @param next 全书顺序里的下一篇（可空）
     * @param c    排版上下文
     * @return 两张并排的翻页卡
     */
    static Laid pager(@Nullable GuideView.EntryView prev, @Nullable GuideView.EntryView next, GuideView.EntryView here, Ctx c) {
        return new PagerLaid(prev, next, here, c);
    }

    /**
     * 条目开头的标题区：图标徽框 + 分类面包屑 + 标题 + 摘要 + 分隔线
     */
    static Laid title(EntryView ev, Ctx c) {
        return new TitleLaid(ev, c);
    }

    /**
     * 一段暗色提示（空条目等）
     */
    static Laid message(String text, Ctx c) {
        return new Paragraph(c.text.wrap(c.view.rich(text, GuideTheme.ASH, false), c.width));
    }

    // ==================== 标题区 ====================

    private static final class TitleLaid extends Laid {
        private static final int ICON = 26;
        final ItemStack icon;
        final String crumb;
        final Line[] title;
        final Line[] summary;
        final int summaryY;

        TitleLaid(EntryView ev, Ctx c) {
            this.icon = ev.icon();
            this.crumb = GuideTheme.ellipsize(c.text.font(), ev.cat.title(), c.width - ICON - 8);
            this.title = c.text.wrap(c.view.rich(ev.entry.title, GuideTheme.BONE, false), c.width - ICON - 8);
            String s = ev.summary();
            this.summary = s.isEmpty() ? new Line[0] : c.text.wrap(c.view.rich(ev.entry.summary, GuideTheme.ASH, false), c.width);
            int head = Math.max(ICON, 12 + title.length * LH);
            this.summaryY = head + 5;
            this.height = summaryY + summary.length * LH + (summary.length > 0 ? 4 : 0) + 3;
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            GuideTheme.emblem(g, x, y, ICON, r.fill(GuideTheme.PLATE_DEEP), r.fill(GuideTheme.withAlpha(GuideTheme.TECH, 0xB0)));
            // 左上角一枚猩红角标：这一篇就是「当前」，和左栏选中行的猩红竖条呼应
            CodexTheme.cornerTab(g, x + 1, y + 1, 5, r.fill(GuideTheme.KUVA));
            if (r.solidReady()) {
                g.renderItem(icon, x + (ICON - 16) / 2, y + (ICON - 16) / 2);
            }

            int tx = x + ICON + 8;
            g.drawString(r.font, crumb, tx, y + 1, r.text(GuideTheme.withAlpha(GuideTheme.TECH, 0xD0)), false);
            r.lines(title, tx, y + 12, LH, true);
            r.lines(summary, x, y + summaryY, LH, false);

            int ly = y + height - 1;
            g.fill(x, ly, x + w, ly + 1, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0)));
            GuideTheme.sweep(g, x, ly, w, r.time, 0.2f, GuideTheme.TECH, 0.7f * r.alpha);
        }
    }

    // ==================== 文字类 ====================

    private static final class Heading extends Laid {
        final Line[] lines;

        Heading(Line[] lines) {
            this.lines = lines;
            this.height = Math.max(1, lines.length) * LH;
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            // 左侧一道科技青短竖条，和图鉴分组标题的角标同一个作用：扫视时一眼找到段落起点
            g.fill(x, y, x + 2, y + 9, r.fill(GuideTheme.TECH));
            r.lines(lines, x + 7, y, LH, true);
            if (lines.length > 0) {
                Line last = lines[lines.length - 1];
                int lx = x + 7 + last.width + 6;
                int ly = y + (lines.length - 1) * LH + 4;
                if (lx < x + w - 8) {
                    g.fill(lx, ly, x + w, ly + 1, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xB0)));
                }
            }
        }
    }

    private static final class Paragraph extends Laid {
        final Line[] lines;

        Paragraph(Line[] lines) {
            this.lines = lines;
            this.height = lines.length * LH - (lines.length > 0 ? 2 : 0);
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            r.lines(lines, x, y, LH, false);
        }
    }

    /**
     * 列表 / 有序列表 / 步骤
     */
    private static final class ListLaid extends Laid {
        static final int BULLET = 0;
        static final int ORDERED = 1;
        static final int STEPS = 2;

        final int kind;
        final Line[][] items;
        final int[] itemY;
        final int[] itemH;
        final String[] numbers;
        /** 步骤徽记里序号的字宽（排版时量好，渲染时居中用） */
        final int[] numberW;
        final int indent;

        private ListLaid(int kind, Line[][] items, int[] itemY, int[] itemH, String[] numbers, int[] numberW,
                         int indent, int height) {
            this.kind = kind;
            this.items = items;
            this.itemY = itemY;
            this.itemH = itemH;
            this.numbers = numbers;
            this.numberW = numberW;
            this.indent = indent;
            this.height = height;
        }

        static ListLaid of(Block b, Ctx c, int kind) {
            int n = b.items.size();
            String[] numbers = new String[n];
            int[] numberW = new int[n];
            int indent;
            if (kind == ORDERED) {
                int maxW = 0;
                for (int i = 0; i < n; i++) {
                    numbers[i] = (i + 1) + ".";
                    maxW = Math.max(maxW, c.text.width(numbers[i]));
                }
                indent = maxW + 5;
            } else if (kind == STEPS) {
                for (int i = 0; i < n; i++) {
                    numbers[i] = String.valueOf(i + 1);
                    numberW[i] = c.text.width(numbers[i]);
                }
                indent = 21;
            } else {
                indent = 10;
            }
            int gap = kind == STEPS ? 7 : 3;
            Line[][] items = new Line[n][];
            int[] ys = new int[n];
            int[] hs = new int[n];
            int y = 0;
            for (int i = 0; i < n; i++) {
                items[i] = c.wrap(b.items.get(i), GuideTheme.BODY, false, c.width - indent);
                int h = Math.max(1, items[i].length) * LH - 2;
                if (kind == STEPS) {
                    h = Math.max(h, 11);
                }
                ys[i] = y;
                hs[i] = h;
                y += h + (i < n - 1 ? gap : 0);
            }
            // 步骤徽记比字高，最后一个徽记的下沿会露出块外一点，补回来
            return new ListLaid(kind, items, ys, hs, numbers, numberW, indent, y + (kind == STEPS ? 2 : 0));
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            for (int i = 0; i < items.length; i++) {
                int iy = y + itemY[i];
                if (!r.visible(iy - 3, itemH[i] + 10)) {
                    continue;
                }
                switch (kind) {
                    case ORDERED -> g.drawString(r.font, numbers[i], x, iy, r.text(GuideTheme.TAG_T), false);
                    case STEPS -> {
                        // 竖向连接线：从本徽记下沿接到下一个徽记上沿，把几步串成一条流程
                        if (i < items.length - 1) {
                            int top = iy + 11;
                            int bottom = y + itemY[i + 1] - 2;
                            if (bottom > top) {
                                g.fill(x + 6, top, x + 7, bottom, r.fill(GuideTheme.withAlpha(GuideTheme.TECH, 0x55)));
                            }
                        }
                        int bx = x;
                        int by = iy - 2;
                        CodexTheme.chamferFill(g, bx, by, 13, 13, 3, r.fill(GuideTheme.PLATE_HI));
                        CodexTheme.chamferOutline(g, bx, by, 13, 13, 3, r.fill(GuideTheme.withAlpha(GuideTheme.TECH, 0xC0)));
                        // 13×13 的徽记，中心在第 6 列 / 第 6 行；7 行高的点阵从第 3 行画起正好上下各留 3 格
                        GuideTheme.digits(g, numbers[i], bx + 6, by + 3, r.text(GuideTheme.TECH));
                    }
                    default -> g.fill(x + 2, iy + 3, x + 5, iy + 6, r.fill(GuideTheme.withAlpha(GuideTheme.TECH, 0xC0)));
                }
                r.lines(items[i], x + indent, iy, LH, false);
            }
        }
    }

    /**
     * 两列键值表
     */
    private static final class KvLaid extends Laid {
        private static final int PAD_X = 6;
        private static final int PAD_Y = 3;
        private static final int GAP = 10;

        final Line[][] labels;
        final Line[][] values;
        final int[] rowY;
        final int[] rowH;
        final int labelW;

        private KvLaid(Line[][] labels, Line[][] values, int[] rowY, int[] rowH, int labelW, int height) {
            this.labels = labels;
            this.values = values;
            this.rowY = rowY;
            this.rowH = rowH;
            this.labelW = labelW;
            this.height = height;
        }

        static KvLaid of(Block b, Ctx c) {
            int n = b.rows.size();
            Rich[] lr = new Rich[n];
            Rich[] vr = new Rich[n];
            int natural = 0;
            for (int i = 0; i < n; i++) {
                lr[i] = c.view.rich(b.rows.get(i).get(0), GuideTheme.LABEL, false);
                vr[i] = c.view.rich(b.rows.get(i).get(1), GuideTheme.BONE, false);
                natural = Math.max(natural, c.text.naturalWidth(lr[i]));
            }
            // 标签列按最长标签定宽，但最多占一半：值（尤其是列表类配置）往往更长，需要更多地方折行
            natural = Math.max(natural, c.kvLabelHint);
            int inner = c.width - PAD_X * 2 - GAP;
            int labelW = Math.max(24, Math.min(natural, inner / 2));
            int valueW = Math.max(24, inner - labelW);
            Line[][] labels = new Line[n][];
            Line[][] values = new Line[n][];
            int[] ys = new int[n];
            int[] hs = new int[n];
            int y = 0;
            for (int i = 0; i < n; i++) {
                labels[i] = c.text.wrap(lr[i], labelW);
                values[i] = c.text.wrap(vr[i], valueW);
                int lines = Math.max(1, Math.max(labels[i].length, values[i].length));
                hs[i] = lines * LH - 2 + PAD_Y * 2;
                ys[i] = y;
                y += hs[i];
            }
            return new KvLaid(labels, values, ys, hs, labelW, y + 1);
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            int sepX = x + PAD_X + labelW + GAP / 2;
            g.fill(x, y, x + w, y + 1, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0)));
            for (int i = 0; i < labels.length; i++) {
                int ry = y + 1 + rowY[i];
                if (!r.visible(ry, rowH[i])) {
                    continue;
                }
                if (i % 2 == 0) {
                    g.fill(x, ry, x + w, ry + rowH[i], r.fill(GuideTheme.withAlpha(GuideTheme.PLATE_HI, 0xC0)));
                }
                r.lines(labels[i], x + PAD_X, ry + PAD_Y, LH, false);
                r.lines(values[i], sepX + GAP / 2, ry + PAD_Y, LH, false);
            }
            g.fill(sepX, y + 1, sepX + 1, y + height - 1, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0x70)));
            g.fill(x, y + height - 1, x + w, y + height, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0)));
        }
    }

    /**
     * 多列表格
     */
    private static final class TableLaid extends Laid {
        private static final int PAD_X = 4;
        private static final int PAD_Y = 3;
        private static final int SOFT_MIN = 36;

        final int[] colX;
        final int[] colW;
        final Line[][] header;
        final int headerH;
        final Line[][][] cells;
        final int[] rowY;
        final int[] rowH;

        private TableLaid(int[] colX, int[] colW, Line[][] header, int headerH, Line[][][] cells,
                          int[] rowY, int[] rowH, int height) {
            this.colX = colX;
            this.colW = colW;
            this.header = header;
            this.headerH = headerH;
            this.cells = cells;
            this.rowY = rowY;
            this.rowH = rowH;
            this.height = height;
        }

        static TableLaid of(Block b, Ctx c) {
            int cols = b.header.size();
            int rows = b.rows.size();
            Rich[] hr = new Rich[cols];
            Rich[][] cr = new Rich[rows][cols];
            for (int j = 0; j < cols; j++) {
                hr[j] = c.view.rich(b.header.get(j), GuideTheme.TABLE_HEAD, false);
            }
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    cr[i][j] = c.view.rich(b.rows.get(i).get(j), GuideTheme.BODY, false);
                }
            }
            int[] colW = columnWidths(b, c, hr, cr, cols, rows);
            int[] colX = new int[cols];
            int acc = 0;
            for (int j = 0; j < cols; j++) {
                colX[j] = acc;
                acc += colW[j];
            }
            Line[][] header = new Line[cols][];
            int headerLines = 1;
            for (int j = 0; j < cols; j++) {
                header[j] = c.text.wrap(hr[j], Math.max(8, colW[j] - PAD_X * 2));
                headerLines = Math.max(headerLines, header[j].length);
            }
            int headerH = headerLines * LH - 2 + PAD_Y * 2 + 1;
            Line[][][] cells = new Line[rows][cols][];
            int[] rowY = new int[rows];
            int[] rowH = new int[rows];
            int y = headerH;
            for (int i = 0; i < rows; i++) {
                int lines = 1;
                for (int j = 0; j < cols; j++) {
                    cells[i][j] = c.text.wrap(cr[i][j], Math.max(8, colW[j] - PAD_X * 2));
                    lines = Math.max(lines, cells[i][j].length);
                }
                rowY[i] = y;
                rowH[i] = lines * LH - 2 + PAD_Y * 2;
                y += rowH[i];
            }
            return new TableLaid(colX, colW, header, headerH, cells, rowY, rowH, y + 1);
        }

        /**
         * 列宽
         *
         * <p>没给 widths 时按内容自适应：先保证每列放得下最长的不可断单元（词、数字），
         * 剩下的宽度按「理想宽度 - 最小宽度」的比例分，于是短列不会被拉宽、长文本列拿到大部分空间；
         * 全部内容一行放得下时按理想宽度等比放大。</p>
         *
         * <p>给了 widths 就按权重分，但<b>同样先保底</b>：权重是按阅读栏最宽时定的，
         * 窄屏（480×270 时正文只有三百来像素）上权重 1 的「顺序」列只剩 16px，两个字要拆成两行。
         * 分到的宽度低于本列最小宽度的列先按最小宽度定死，剩下的宽度再按其余列的权重分，直到稳定。</p>
         */
        /**
         * 单元格的「最好别再窄」宽度
         *
         * <p>中文逐字可断，严格的最小宽度只有一个字（9px）—— 按它分列，「顺序」这种两个字的表头
         * 在窄屏上会被竖着拆成两行。所以短内容（不超过 {@value #SOFT_MIN}px，约四个汉字）整格不折，
         * 长内容最多压到 {@value #SOFT_MIN}px 或它最长的英文单词。</p>
         */
        private static int softMin(Ctx c, Rich r) {
            return Math.min(c.text.naturalWidth(r), Math.max(c.text.minWidth(r), SOFT_MIN));
        }

        private static int[] columnWidths(Block b, Ctx c, Rich[] hr, Rich[][] cr, int cols, int rows) {
            int total = c.width;
            int[] min = new int[cols];
            int[] max = new int[cols];
            for (int j = 0; j < cols; j++) {
                min[j] = softMin(c, hr[j]);
                max[j] = c.text.naturalWidth(hr[j]);
                for (int i = 0; i < rows; i++) {
                    min[j] = Math.max(min[j], softMin(c, cr[i][j]));
                    max[j] = Math.max(max[j], c.text.naturalWidth(cr[i][j]));
                }
                min[j] = Math.min(min[j], total / 2) + PAD_X * 2 + 1;
                max[j] = Math.max(min[j], max[j] + PAD_X * 2 + 1);
            }
            long sumMin = 0;
            long sumMax = 0;
            for (int j = 0; j < cols; j++) {
                sumMin += min[j];
                sumMax += max[j];
            }
            double[] w = new double[cols];
            if (sumMin >= total) {
                // 连最小宽度都放不下：只能按最小宽度等比压缩，长词会被劈开
                for (int j = 0; j < cols; j++) {
                    w[j] = min[j] * (double) total / sumMin;
                }
            } else if (b.widths != null) {
                boolean[] fixed = new boolean[cols];
                double remaining = total;
                double pool = 0;
                for (float f : b.widths) {
                    pool += f;
                }
                boolean changed = true;
                while (changed && pool > 0) {
                    changed = false;
                    for (int j = 0; j < cols; j++) {
                        if (!fixed[j]) {
                            w[j] = remaining * b.widths[j] / pool;
                        }
                    }
                    for (int j = 0; j < cols; j++) {
                        if (!fixed[j] && w[j] < min[j]) {
                            fixed[j] = true;
                            w[j] = min[j];
                            remaining -= min[j];
                            pool -= b.widths[j];
                            changed = true;
                        }
                    }
                }
            } else if (sumMax <= total) {
                for (int j = 0; j < cols; j++) {
                    w[j] = max[j] * (double) total / sumMax;
                }
            } else {
                double extra = total - sumMin;
                double flex = sumMax - sumMin;
                for (int j = 0; j < cols; j++) {
                    w[j] = min[j] + extra * (max[j] - min[j]) / flex;
                }
            }
            int[] out = new int[cols];
            int used = 0;
            for (int j = 0; j < cols; j++) {
                out[j] = j == cols - 1 ? total - used : (int) Math.round(w[j]);
                used += out[j];
            }
            return out;
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            int edge = r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0));
            int grid = r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0x60));
            // 表头：高一层的板材 + 下沿一道科技青
            if (r.visible(y, headerH)) {
                g.fill(x, y, x + w, y + headerH, r.fill(GuideTheme.PLATE_HI));
                for (int j = 0; j < header.length; j++) {
                    r.lines(header[j], x + colX[j] + PAD_X, y + PAD_Y, LH, false);
                }
                g.fill(x, y + headerH - 1, x + w, y + headerH, r.fill(GuideTheme.withAlpha(GuideTheme.TECH, 0x90)));
            }
            for (int i = 0; i < cells.length; i++) {
                int ry = y + rowY[i];
                if (!r.visible(ry, rowH[i])) {
                    continue;
                }
                if (i % 2 == 1) {
                    g.fill(x, ry, x + w, ry + rowH[i], r.fill(GuideTheme.withAlpha(GuideTheme.PLATE_HI, 0x90)));
                }
                for (int j = 0; j < cells[i].length; j++) {
                    r.lines(cells[i][j], x + colX[j] + PAD_X, ry + PAD_Y, LH, false);
                }
                if (i < cells.length - 1) {
                    g.fill(x, ry + rowH[i] - 1, x + w, ry + rowH[i], grid);
                }
            }
            for (int j = 1; j < colX.length; j++) {
                g.fill(x + colX[j], y, x + colX[j] + 1, y + height - 1, grid);
            }
            CodexTheme.border(g, x, y, w, height, edge);
        }
    }

    /**
     * 提示框：左侧色条 + 极淡的同色底
     */
    private static final class TipLaid extends Laid {
        private static final int PAD_L = 9;
        private static final int PAD_R = 7;
        private static final int PAD_Y = 5;

        final int color;
        final Line[] title;
        final Line[] text;
        final int textY;

        private TipLaid(int color, Line[] title, Line[] text) {
            this.color = color;
            this.title = title;
            this.text = text;
            this.textY = PAD_Y + (title.length > 0 ? title.length * LH + 2 : 0);
            this.height = textY + Math.max(1, text.length) * LH - 2 + PAD_Y;
        }

        static TipLaid of(Block b, Ctx c) {
            int color = b.style.color();
            int w = c.width - PAD_L - PAD_R;
            String t = b.title;
            if ((t == null || t.isEmpty()) && b.style == GuideContent.TipStyle.WF) {
                // 小知识框不写标题时给一个固定标题，玩家一眼就知道这段是原作背景、可以跳过
                t = net.minecraft.client.resources.language.I18n.get("kuvalich.guide.tip_wf");
            }
            Line[] title = t == null || t.isEmpty() ? new Line[0] : c.wrap(t, color, false, w);
            return new TipLaid(color, title, c.wrap(b.text, GuideTheme.BODY, false, w));
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            g.fill(x, y, x + w, y + height, r.fill(GuideTheme.withAlpha(color, 0x18)));
            g.fill(x, y, x + 2, y + height, r.fill(color));
            // 右上角一枚同色小切角，呼应图鉴格子的品质角标
            for (int i = 0; i < 5; i++) {
                g.fill(x + w - 5 + i, y + i, x + w, y + i + 1, r.fill(GuideTheme.withAlpha(color, 0x70)));
            }
            r.lines(title, x + PAD_L, y + PAD_Y, LH, true);
            r.lines(text, x + PAD_L, y + textY, LH, false);
        }
    }

    /**
     * 篇末翻页卡：左「上一篇」、右「下一篇」，跨分类时写成「下一章 · 分类名」
     *
     * <h3>为什么要有</h3>
     * <p>入门指南是按顺序写的教程，玩家读完一篇该去哪应当一眼可见，而不是回左栏找。
     * 下一篇按<b>全书顺序</b>接（分类末篇接下一分类的首篇），一路点下去就能读完整本书。</p>
     *
     * <p>标题本身是一条普通链接（{@code [[分类/条目|标题]]}），所以悬停变色、点击跳转、历史记录
     * 都走现成的链接逻辑；鼠标在整张卡上任意位置都算悬停在这条链接上，点哪都能翻页。</p>
     */
    private static final class PagerLaid extends Laid {
        private static final int GAP = 8;
        private static final int PAD = 6;
        private static final int ICON = 16;

        @Nullable
        final Card prev;
        @Nullable
        final Card next;

        private static final class Card {
            final ItemStack icon;
            final String label;
            /** label 的字宽：前进卡要右对齐，排版时量好，渲染路径不测宽 */
            final int labelW;
            final Line title;
            @Nullable
            final GuideMarkup.Link link;

            Card(ItemStack icon, String label, int labelW, Line title) {
                this.icon = icon;
                this.label = label;
                this.labelW = labelW;
                this.title = title;
                GuideMarkup.Link l = null;
                for (GuideTextLayout.Piece p : title.pieces) {
                    if (p.link != null) {
                        l = p.link;
                        break;
                    }
                }
                this.link = l;
            }
        }

        PagerLaid(@Nullable GuideView.EntryView prev, @Nullable GuideView.EntryView next, GuideView.EntryView here, Ctx c) {
            int textW = textW((c.width - GAP) / 2);
            this.prev = prev == null ? null : card(prev, here, false, textW, c);
            this.next = next == null ? null : card(next, here, true, textW, c);
            this.height = PAD * 2 + LH * 2 - 2;
        }

        /**
         * 卡里文字区的宽度。排版截断和绘制对齐必须用同一个值：两边差几像素，
         * 截好的最长标题就会越过内边距贴到卡边上
         *
         * @param cardW 卡宽
         */
        private static int textW(int cardW) {
            return cardW - PAD * 2 - ICON - 8;
        }

        private static Card card(GuideView.EntryView target, GuideView.EntryView here, boolean forward, int textW, Ctx c) {
            String label;
            if (target.cat != here.cat) {
                label = net.minecraft.client.resources.language.I18n.get(forward ? "kuvalich.guide.pager.next_chapter" : "kuvalich.guide.pager.prev_chapter", target.cat.title());
            } else {
                label = net.minecraft.client.resources.language.I18n.get(forward ? "kuvalich.guide.pager.next" : "kuvalich.guide.pager.prev");
            }
            label = GuideTheme.ellipsize(c.text.font(), label, textW);
            String title = GuideTheme.ellipsize(c.text.font(), target.title(), textW);
            String markup = "[[" + target.key + "|" + escape(title) + "]]";
            Line[] lines = c.text.wrap(c.view.rich(markup, GuideTheme.BONE, false), textW + 40);
            return new Card(target.icon(), label, c.text.font().width(label), lines.length > 0 ? lines[0] : new Line(Line.NONE, 0));
        }

        private static String escape(String s) {
            StringBuilder sb = new StringBuilder(s.length() + 4);
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch == '{' || ch == '[' || ch == '<' || ch == '\\') {
                    sb.append('\\');
                }
                sb.append(ch);
            }
            return sb.toString();
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            int cardW = (w - GAP) / 2;
            if (prev != null) {
                card(r, prev, x, y, cardW, false);
            }
            if (next != null) {
                card(r, next, x + w - cardW, y, cardW, true);
            }
        }

        private void card(GuideRender r, Card cd, int x, int y, int w, boolean forward) {
            GuiGraphics g = r.g;
            boolean over = cd.link != null && r.over(x, y, w, height);
            if (over) {
                r.nextHoverLink = cd.link;
            }
            boolean hot = cd.link != null && cd.link == r.hoverLink;
            g.fill(x, y, x + w, y + height, r.fill(hot ? GuideTheme.withAlpha(GuideTheme.KUVA, 0x22) : GuideTheme.PLATE_DEEP));
            CodexTheme.border(g, x, y, w, height, r.fill(hot ? GuideTheme.KUVA : GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0)));
            // 朝翻页方向的那条边用科技青加粗，像一个指向箭头的按钮
            int ex = forward ? x + w - 2 : x;
            g.fill(ex, y, ex + 2, y + height, r.fill(hot ? GuideTheme.KUVA : GuideTheme.TECH));
            int iconX = forward ? x + w - PAD - ICON - 2 : x + PAD + 2;
            if (r.solidReady()) {
                g.renderItem(cd.icon, iconX, y + (height - ICON) / 2);
            }
            int tx = forward ? x + PAD : x + PAD + 2 + ICON + 6;
            int textW = textW(w);
            if (forward) {
                g.drawString(r.font, cd.label, tx + textW - cd.labelW, y + PAD, r.text(GuideTheme.ASH), false);
                r.line(cd.title, tx + textW - cd.title.width, y + PAD + LH, false);
            } else {
                g.drawString(r.font, cd.label, tx, y + PAD, r.text(GuideTheme.ASH), false);
                r.line(cd.title, tx, y + PAD + LH, false);
            }
        }
    }

    /**
     * 公式框：凹陷底 + 四角瞄准括号，文字居中
     */
    private static final class FormulaLaid extends Laid {
        final Line[] lines;

        FormulaLaid(Line[] lines) {
            this.lines = lines;
            this.height = Math.max(1, lines.length) * LH - 2 + 14;
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            g.fill(x, y, x + w, y + height, r.fill(GuideTheme.PLATE_DEEP));
            CodexTheme.border(g, x, y, w, height, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xA0)));
            CodexTheme.brackets(g, x, y, w, height, 5, r.fill(GuideTheme.TECH));
            r.linesCentered(lines, x, w, y + 7, LH, false);
        }
    }

    // ==================== 物品 / 配方 / 实体 ====================

    /**
     * 一排物品：图标 + 名称，按宽度自动换行
     */
    private static final class ItemsLaid extends Laid {
        private static final int SLOT = 18;
        private static final int ROW_H = 22;

        final ItemStack[] stacks;
        final String[] names;
        final int[] cx;
        final int[] cy;
        final int[] cw;
        final Line[] caption;
        final int captionY;

        private ItemsLaid(ItemStack[] stacks, String[] names, int[] cx, int[] cy, int[] cw, Line[] caption, int captionY, int height) {
            this.stacks = stacks;
            this.names = names;
            this.cx = cx;
            this.cy = cy;
            this.cw = cw;
            this.caption = caption;
            this.captionY = captionY;
            this.height = height;
        }

        @Nullable
        static ItemsLaid of(Block b, Ctx c) {
            List<ItemStack> list = new ArrayList<>();
            for (String id : b.items) {
                ItemStack s = GuideView.stack(id);
                if (!s.isEmpty()) {
                    list.add(s);
                }
            }
            if (list.isEmpty()) {
                return null;
            }
            int n = list.size();
            ItemStack[] stacks = list.toArray(new ItemStack[0]);
            String[] names = new String[n];
            int[] xs = new int[n];
            int[] ys = new int[n];
            int[] ws = new int[n];
            int x = 0;
            int y = 0;
            for (int i = 0; i < n; i++) {
                String name = GuideTheme.ellipsize(c.text.font(), stacks[i].getHoverName().getString(), c.width - SLOT - 6);
                int w = SLOT + 5 + c.text.font().width(name) + 10;
                if (x > 0 && x + w > c.width) {
                    x = 0;
                    y += ROW_H;
                }
                names[i] = name;
                xs[i] = x;
                ys[i] = y;
                ws[i] = Math.min(w, c.width);
                x += w;
            }
            int gridH = y + SLOT;
            Line[] caption = b.caption == null ? new Line[0] : c.wrap(b.caption, GuideTheme.ASH, false, c.width);
            int captionY = gridH + 4;
            int height = caption.length > 0 ? captionY + caption.length * LH - 2 : gridH;
            return new ItemsLaid(stacks, names, xs, ys, ws, caption, captionY, height);
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            for (int i = 0; i < stacks.length; i++) {
                int sx = x + cx[i];
                int sy = y + cy[i];
                if (!r.visible(sy, SLOT)) {
                    continue;
                }
                boolean over = r.over(sx, sy, cw[i] - 6, SLOT);
                g.fill(sx, sy, sx + SLOT, sy + SLOT, r.fill(GuideTheme.PLATE_DEEP));
                CodexTheme.border(g, sx, sy, SLOT, SLOT, r.fill(over ? GuideTheme.KUVA : GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF)));
                if (r.solidReady()) {
                    g.renderItem(stacks[i], sx + 1, sy + 1);
                }
                g.drawString(r.font, names[i], sx + SLOT + 5, sy + 5, r.text(over ? GuideTheme.BONE : GuideTheme.BODY), false);
                if (over) {
                    r.hoverStack = stacks[i];
                }
            }
            r.lines(caption, x, y + captionY, LH, false);
        }
    }

    /**
     * 工作台配方：3×3 + 箭头 + 产物；候选材料每秒轮换
     */
    private static final class RecipeLaid extends Laid {
        private static final int SLOT = 18;
        private static final int FRAME_W = 3 * SLOT + 12 + 22 + 12 + 26 + 16;
        private static final int FRAME_H = 3 * SLOT + 16;

        final GuideMedia.RecipeData data;
        final String resultName;
        final int resultNameW;

        RecipeLaid(GuideMedia.RecipeData data, Ctx c) {
            this.data = data;
            this.resultName = GuideTheme.ellipsize(c.text.font(), data.result.getHoverName().getString(), c.width);
            this.resultNameW = c.text.font().width(resultName);
            this.height = FRAME_H + 13;
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            int fx = x + Math.max(0, (w - FRAME_W) / 2);
            CodexTheme.chamferFill(g, fx, y, FRAME_W, FRAME_H, 6, r.fill(GuideTheme.PLATE_DEEP));
            CodexTheme.holoSurface(g, fx + 2, y + 2, FRAME_W - 4, FRAME_H - 4, (int) (10 * r.alpha));
            CodexTheme.chamferOutline(g, fx, y, FRAME_W, FRAME_H, 6, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF)));

            int gx = fx + 8;
            int gy = y + 8;
            long tick = r.now / 1000L;
            for (int i = 0; i < 9; i++) {
                int sx = gx + (i % 3) * SLOT;
                int sy = gy + (i / 3) * SLOT;
                g.fill(sx, sy, sx + SLOT, sy + SLOT, r.fill(0xFF0A0E11));
                CodexTheme.border(g, sx, sy, SLOT, SLOT, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xC0)));
                ItemStack[] cand = data.slots[i];
                if (cand != null && cand.length > 0) {
                    ItemStack s = cand[(int) (tick % cand.length)];
                    if (r.solidReady()) {
                        g.renderItem(s, sx + 1, sy + 1);
                    }
                    if (r.over(sx, sy, SLOT, SLOT)) {
                        r.hoverStack = s;
                        CodexTheme.border(g, sx, sy, SLOT, SLOT, r.fill(GuideTheme.KUVA));
                    }
                }
            }

            // 箭头：一根杆 + 三层阶梯箭头，科技青
            int ax = gx + 3 * SLOT + 12;
            int ay = gy + SLOT + SLOT / 2;
            int arrow = r.fill(GuideTheme.TECH);
            g.fill(ax, ay, ax + 17, ay + 1, arrow);
            for (int i = 1; i <= 4; i++) {
                g.fill(ax + 17 - i, ay - i, ax + 18 - i, ay + i + 1, arrow);
            }

            int rx = ax + 22 + 12;
            int ry = gy + SLOT + SLOT / 2 - 13;
            g.fill(rx, ry, rx + 26, ry + 26, r.fill(0xFF0A0E11));
            CodexTheme.border(g, rx, ry, 26, 26, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xC0)));
            boolean over = r.over(rx, ry, 26, 26);
            CodexTheme.brackets(g, rx - 1, ry - 1, 28, 28, 5, r.fill(over ? GuideTheme.KUVA : GuideTheme.TECH));
            if (r.solidReady()) {
                g.renderItem(data.result, rx + 5, ry + 5);
                g.renderItemDecorations(r.font, data.result, rx + 5, ry + 5);
            }
            if (over) {
                r.hoverStack = data.result;
            }

            g.drawString(r.font, resultName, x + (w - resultNameW) / 2, y + FRAME_H + 3, r.text(GuideTheme.ASH), false);
        }
    }

    /**
     * 实体预览：模型随鼠标转头；建不出来时框里换成问号占位，框下照常写名字
     */
    private static final class EntityLaid extends Laid {
        final String id;
        final int boxH;
        final String name;
        final int nameW;
        /** 退化时框里那枚问号的字宽 */
        final int qW;

        EntityLaid(String id, int boxH, Ctx c) {
            this.id = id;
            this.boxH = boxH;
            this.height = boxH + 14;
            // 名字在排版时取好、量好：渲染路径上不查注册表、不测宽
            this.name = GuideTheme.ellipsize(c.text.font(), GuideMedia.entityName(id), c.width);
            this.nameW = c.text.font().width(name);
            this.qW = c.text.font().width("?");
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            int bw = Math.min(w, Math.max(120, boxH * 2));
            int bx = x + (w - bw) / 2;
            CodexTheme.chamferFill(g, bx, y, bw, boxH, 6, r.fill(GuideTheme.PLATE_DEEP));
            CodexTheme.holoSurface(g, bx + 2, y + 2, bw - 4, boxH - 4, (int) (10 * r.alpha));
            CodexTheme.chamferOutline(g, bx, y, bw, boxH, 6, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF)));
            // 地台：一道中间实、两端淡的科技青线，模型站在上面
            int floorY = y + boxH - 7;
            GuideTheme.hairline(g, bx + 10, floorY, bw - 20, r.fill(GuideTheme.withAlpha(GuideTheme.TECH, 0x90)));

            boolean inView = r.visible(y, boxH);
            LivingEntity entity = inView ? r.media.entity(id) : null;
            if (entity == null) {
                if (inView) {
                    // 建不出来：框里只放一枚淡色问号占位，名字只在框下面写一次（原先框里框外各一遍，上下两行一模一样）
                    g.drawString(r.font, "?", bx + (bw - qW) / 2, y + boxH / 2 - 4, r.text(GuideTheme.FAINT), false);
                }
            } else if (r.solidReady()) {
                int cx = bx + bw / 2;
                int bottom = floorY;
                float size = Math.max(0.3f, Math.max(entity.getBbHeight(), entity.getBbWidth() * 0.9f));
                int scale = Math.max(4, (int) ((boxH - 16) / size));
                float eye = entity.getEyeHeight() * scale;
                float lookX;
                float lookY;
                if (r.mouseActive) {
                    lookX = cx - r.mouseX;
                    lookY = bottom - eye - r.mouseY;
                } else {
                    // 鼠标不在阅读区时缓缓左右环顾，不至于僵成一张贴图。
                    // 周期 8 秒：能整除 GuideTheme.time() 的 720 秒回绕，回绕那一刻不会跳头
                    lookX = (float) Math.sin(r.time * (Math.PI / 4.0)) * 30f;
                    lookY = -10f;
                }
                PoseStack.Pose top = g.pose().last();
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, bottom, scale, lookX, lookY, entity);
                } catch (Throwable t) {
                    // 渲染半途抛出会留下没弹掉的矩阵，帧末 PoseStack 自检会直接崩游戏 —— 先把栈恢复。
                    // clear() 为真表示只剩根矩阵：万一栈已经被多弹过、找不回 top，也不能弹空（弹空会再抛一次）
                    while (!g.pose().clear() && g.pose().last() != top) {
                        g.pose().popPose();
                    }
                    // 原版这个方法中途会关掉实体阴影、换成实体光照，正常走完才会还原；半途抛出时自己还原，
                    // 否则之后世界里的实体都没有影子、界面物品的光照也不对
                    Minecraft.getInstance().getEntityRenderDispatcher().setRenderShadow(true);
                    Lighting.setupFor3DItems();
                    r.media.markEntityFailed(id, t);
                }
            }
            g.drawString(r.font, name, x + (w - nameW) / 2, y + boxH + 3, r.text(GuideTheme.ASH), false);
        }
    }

    /**
     * 分隔线：两端渐隐的细线，中间一枚菱形刻度
     */
    private static final class Divider extends Laid {
        Divider() {
            this.height = 7;
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            int cy = y + 3;
            int half = w / 2;
            GuideTheme.hairline(g, x, cy, w, r.fill(GuideTheme.withAlpha(0xFF3A4E5A, 0xFF)));
            int dx = x + half;
            g.fill(dx - 1, cy - 2, dx + 1, cy - 1, r.fill(GuideTheme.TECH));
            g.fill(dx - 2, cy - 1, dx + 2, cy + 2, r.fill(GuideTheme.TECH));
            g.fill(dx - 1, cy + 2, dx + 1, cy + 3, r.fill(GuideTheme.TECH));
        }
    }
}
