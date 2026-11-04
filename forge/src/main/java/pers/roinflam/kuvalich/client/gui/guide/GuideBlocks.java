package pers.roinflam.kuvalich.client.gui.guide;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.comm.CommPortraits;
import pers.roinflam.kuvalich.client.comm.CommText;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.Block;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.BlockType;
import pers.roinflam.kuvalich.client.gui.guide.GuideContent.CommWho;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Rich;
import pers.roinflam.kuvalich.client.gui.guide.GuideTextLayout.Line;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.EntryView;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.comm.LichLook;
import pers.roinflam.kuvalich.config.GuideDisplayConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    /** 一段文字最后一行下面不留的行距（块高 = 行数 × LH − TAIL） */
    private static final int TAIL = GuideTheme.LINE_TAIL;

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
        /** 正在排的条目（分类/条目）：通讯框拿它拼「看过」记录的键。由 {@link GuideReader} 设 */
        String entryKey = "";
        /** 这一篇里已经排了几条玄骸通讯（前两条渲模型肖像，之后的退回徽记） */
        int lichComms;

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
            case COMM -> CommLaid.of(b, c);
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
                GuideItemIcon.render(g, icon, x + (ICON - 16) / 2, y + (ICON - 16) / 2);
            }

            int tx = x + ICON + 8;
            // 面包屑不透明：小字再叠半透明，在近黑底上发虚
            g.drawString(r.font, crumb, tx, y + 1, r.text(GuideTheme.TECH), false);
            r.lines(title, tx, y + 12, LH, true);
            r.lines(summary, x, y + summaryY, LH, false);

            // 静止的结构线：左端一截科技青，和小标题的竖条同一个作用（原先这里有一道往复的流光，读的地方不放常驻动画）
            int ly = y + height - 1;
            g.fill(x, ly, x + w, ly + 1, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0)));
            g.fill(x, ly, x + Math.min(w, ICON), ly + 1, r.fill(GuideTheme.withAlpha(GuideTheme.TECH, 0xB0)));
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
            this.height = lines.length * LH - (lines.length > 0 ? TAIL : 0);
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
                int h = Math.max(1, items[i].length) * LH - TAIL;
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
                hs[i] = lines * LH - TAIL + PAD_Y * 2;
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
            int headerH = headerLines * LH - TAIL + PAD_Y * 2 + 1;
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
                rowH[i] = lines * LH - TAIL + PAD_Y * 2;
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
     * 提示框：统一的底（{@link GuideTheme#TIP_BG}）+ 左侧色条 + 同色标题；颜色只在色条、标题和右上角切角上
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
            this.height = textY + Math.max(1, text.length) * LH - TAIL + PAD_Y;
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
            g.fill(x, y, x + w, y + height, r.fill(GuideTheme.TIP_BG));
            g.fill(x, y, x + 2, y + height, r.fill(color));
            // 右上角一枚同色小切角，呼应图鉴格子的品质角标；不透明（半透明的彩色叠在底上会混出脏色）
            for (int i = 0; i < 5; i++) {
                g.fill(x + w - 5 + i, y + i, x + w, y + i + 1, r.fill(color));
            }
            r.lines(title, x + PAD_L, y + PAD_Y, LH, true);
            r.lines(text, x + PAD_L, y + textY, LH, false);
        }
    }

    /**
     * 通讯框：左侧发言人的肖像窗，名牌压在肖像左下角，右边是台词
     *
     * <h3>只在第一次看到时演一遍</h3>
     * <p>第一次看到（{@link CommMemory} 里没记过，开场已就绪、换篇淡入基本完成、块至少一半露在阅读栏里）的那一帧起：
     * 色条从中点向上下长出，肖像开框（Lotus / Ordis 100ms 上下展开，玄骸 130ms 横向拖影），同时响一声接通音；
     * 先只有肖像，330ms（玄骸 670ms）起台词整段从左到右软边擦出，用时按字数在 150~700ms 之间，擦出开始 550ms 后名牌淡入；
     * 擦完再「说」1.2 秒（Lotus 亮线扫描、Ordis 涟漪、玄骸播说话动作）后进静止态。看过的通讯一律直接画静止态，不演、不响。
     * 还没满足「第一次看到」之前画待机态：只有底板和暗色条。</p>
     *
     * <h3>故障段</h3>
     * <p>擦出前沿走到 Ordis 的 {@code <x>…</x>} 时整段一次显出、横跳两拍（38ms × 2），肖像同时碎块错位、整窗色差，
     * 前沿在这里停 160ms 再往后擦。</p>
     *
     * <h3>肖像</h3>
     * <p>肖像和游戏里的通讯窗是同一套画面（{@link CommPortraits}），这里只给框和这一帧的状态。它是立即画的、不进
     * {@code drawManaged} 的批次，所以它会先把前面攒着的底板、色条上屏再画，肖像正好盖在底板上；名牌、台词照常进批次，
     * 最后画在肖像上面。Lotus / Ordis 静止后缓冲只画一次；玄骸模型的 idle 一直在动 —— 这是阅读栏里唯一的常驻动画，
     * 原作的通讯肖像本来就是活的。没有世界（标题画面）时画不出玄骸模型，一篇里第三条起的玄骸通讯也不再渲模型，
     * 都退回 28×28 的徽记，居中画在肖像窗里。</p>
     *
     * <h3>高度一次算死</h3>
     * <p>块高按全文排好，擦出只决定每个字的不透明度，下面的块不跟着挤、页面不抖。每个字的位置、在整段上的 x、
     * 故障段的起止都在排版时算好，渲染只做查表；擦出期间逐字画，擦完整行画。</p>
     *
     * <h3>不抬 z</h3>
     * <p>通讯框的一切都在 z=0，画在阅读栏上下渐隐（z=300）之前，滚动时肖像、台词一起被渐隐盖住。</p>
     */
    private static final class CommLaid extends Laid {
        private static final int PAD_L = 8;
        private static final int PAD_R = 8;
        private static final int PAD_Y = 6;

        // ---- 肖像窗与正文的位置：换肖像尺寸只改这两个数，正文起点、块高、名牌宽度上限都跟着算 ----
        /** 肖像窗宽高（指南像素），左上角在 (PAD_L, PAD_Y) */
        private static final int PORTRAIT_W = 54;
        private static final int PORTRAIT_H = 36;
        /** 正文起点：肖像右边空 8 */
        private static final int TEXT_X = PAD_L + PORTRAIT_W + 8;
        /** 名牌在肖像里，正文从块顶开始 */
        private static final int TEXT_Y = PAD_Y;
        /** 最矮的块：肖像上下各留 PAD_Y，三行以内都是这个高度 */
        private static final int MIN_H = PAD_Y * 2 + PORTRAIT_H;
        /** 名牌离肖像左、下边各 3；宽过肖像宽 − 6 先去掉字距，再缩到放得下 */
        private static final int NAME_INSET = 3;
        private static final int NAME_MAX_W = PORTRAIT_W - 2 * NAME_INSET;
        private static final float NAME_MIN_SCALE = 0.6f;
        private static final int NAME_GAP = 1;
        private static final Style NAME_STYLE = Style.EMPTY.withBold(true);

        // ---- 时间轴（毫秒，从第一次看到算起；墙钟） ----
        private static final int BAR_MS = 120;
        /** 台词开始擦出：友方开框后先看 330ms 的脸，玄骸 670ms */
        private static final int TEXT_FRIEND_MS = 330;
        private static final int TEXT_LICH_MS = 670;
        /** 擦出开始后名牌淡入 */
        private static final int NAME_DELAY_MS = 550;
        private static final int NAME_FADE_MS = 100;
        /** 擦完之后还「说」这么久，再进静止态 */
        private static final int TALK_TAIL_MS = 1200;
        /** 擦出前沿的软边宽度（指南像素） */
        private static final float FEATHER = 18f;
        /** 故障段整段显出后，前沿停这么久 */
        private static final int GLITCH_PAUSE = 160;
        /** 故障段显出时整段横跳两拍，一拍这么长 */
        private static final int KICK_MS = 38;
        /** 静止态交给肖像的「开框后已过」：早就开好了，微粒也早就在 */
        private static final long LONG_OPEN_MS = 60_000L;

        // ---- 退回徽记时的动画 ----
        private static final int FLICKER_MS = 190;
        private static final int SCAN_MS = 700;
        private static final int RIPPLE_EVERY = 240;
        private static final int RIPPLE_LIFE = 480;
        private static final float HEART_MS = 1200f;
        /** 故障时徽记的色差副本：偏红的品红、青 */
        private static final int SPLIT_MAGENTA = 0xFFFF4FD8;
        private static final int SPLIT_CYAN = 0xFF4FE8FF;
        /** 玄骸说话时裂纹随心跳在暗红和近白之间明灭 */
        private static final int CRACK_LOW = 0xFF8E0A1E;
        private static final int CRACK_HIGH = 0xFFFFD0C4;
        /** 一篇里前两条玄骸通讯渲模型，第三条起退回徽记 */
        private static final int MAX_LICH_PORTRAITS = 2;

        /** 还没满足「第一次看到」 */
        private static final long STANDBY = -1L;
        /** 静止态（看过了 / 演完了） */
        private static final long STILL = Long.MAX_VALUE / 4;

        final CommWho who;
        final CommSpeaker speaker;
        /** 看过的记录用的键：分类/条目#n */
        final String key;
        final int color;
        /** 待机色条、待机切角：发言人色按 35% 混进底色的不透明色（半透明的彩色叠在底上会混出脏色） */
        final int dim;
        final int[] palette;
        final Line[] text;
        /** 这一篇里第三条起的玄骸通讯，只画徽记 */
        final boolean badgeOnly;

        // ---- 名牌：排版时取好字、量好宽 ----
        final FormattedCharSequence[] nameGlyph;
        final float[] nameX;
        final float nameScale;

        // ---- 擦出：每个字一项，按阅读顺序 ----
        final FormattedCharSequence[] glyph;
        /** 故障段字的残影字形（不带颜色）；不是故障段为 null */
        final FormattedCharSequence[] ghost;
        /** 行内 x（不含故障段的错位）、故障段本体的错位、第几行、在整段上的 x、属于第几个故障段（-1 = 不是） */
        final float[] glyphX;
        final int[] glyphJx;
        final int[] glyphLine;
        final float[] glyphStrip;
        final int[] glyphSeg;
        /** 每个故障段在整段上的起止 x、擦出前沿走到它的时刻（相对擦出开始） */
        final float[] segStart;
        final float[] segEnd;
        final int[] segAt;
        /** 前沿每毫秒走多远（故障段那几截不算，一下跳过） */
        final float speed;
        /** 擦出开始的时刻、擦出总用时（含故障段的停顿）、演完进静止态的时刻 */
        final int textStart;
        final int wipeTotal;
        final int endT;

        /** 交给肖像的这一帧状态（复用，渲染路径不分配） */
        private final CommPortraits.Frame frame = new CommPortraits.Frame();
        /** 退回徽记时每帧算颜色用的两份四色数组（复用） */
        private final int[] colors = new int[4];
        private final int[] scanColors = new int[4];

        private CommLaid(Block b, Ctx c) {
            this.who = b.who;
            this.speaker = switch (who) {
                case LOTUS -> CommSpeaker.LOTUS;
                case ORDIS -> CommSpeaker.ORDIS;
                case LICH -> CommSpeaker.LICH;
            };
            this.key = c.entryKey + "#" + b.commIndex;
            this.color = switch (who) {
                case LOTUS -> GuideTheme.TECH;
                case ORDIS -> GuideTheme.TIP_WF;
                case LICH -> GuideTheme.KUVA;
            };
            this.dim = GuideTheme.lerpColor(GuideTheme.TIP_BG, color, 0.35f);
            this.palette = CommBadges.palette(who);
            this.badgeOnly = who == CommWho.LICH && c.lichComms++ >= MAX_LICH_PORTRAITS;
            this.text = c.wrap(b.text, GuideTheme.BODY, false, Math.max(40, c.width - TEXT_X - PAD_R));
            this.height = Math.max(MIN_H, TEXT_Y + Math.max(1, text.length) * LH - TAIL + PAD_Y);

            Font font = c.text.font();
            StringSplitter splitter = font.getSplitter();

            // 名牌：粗体、拉丁字母转大写、字距 1；放不下先去字距，再整体缩小（最小 0.6 倍）
            String name = I18n.get(switch (who) {
                case LOTUS -> "kuvalich.guide.comm.lotus";
                case ORDIS -> "kuvalich.guide.comm.ordis";
                case LICH -> "entity.kuvalich.kuva_master";
            }).toUpperCase(Locale.ROOT);
            int nn = name.codePointCount(0, name.length());
            this.nameGlyph = new FormattedCharSequence[nn];
            float[] adv = new float[nn];
            float bare = 0f;
            int k = 0;
            for (int off = 0; off < name.length(); k++) {
                int cp = name.codePointAt(off);
                off += Character.charCount(cp);
                nameGlyph[k] = FormattedCharSequence.codepoint(cp, NAME_STYLE);
                adv[k] = splitter.stringWidth(nameGlyph[k]);
                bare += adv[k];
            }
            int gap = bare + NAME_GAP * Math.max(0, nn - 1) <= NAME_MAX_W ? NAME_GAP : 0;
            this.nameX = new float[nn];
            float nx = 0f;
            for (int i = 0; i < nn; i++) {
                nameX[i] = nx;
                nx += adv[i] + (i < nn - 1 && !Character.isWhitespace(name.codePointAt(name.offsetByCodePoints(0, i))) ? gap : 0);
            }
            this.nameScale = nx > NAME_MAX_W ? Math.max(NAME_MIN_SCALE, NAME_MAX_W / nx) : 1f;

            // 擦出：把各行接成一条，记下每个字在整条上的 x；故障段是连续的几个故障字（可以跨行）
            int total = 0;
            for (Line l : text) {
                for (GuideTextLayout.Piece p : l.pieces) {
                    total += p.text.codePointCount(0, p.text.length());
                }
            }
            this.glyph = new FormattedCharSequence[total];
            this.ghost = new FormattedCharSequence[total];
            this.glyphX = new float[total];
            this.glyphJx = new int[total];
            this.glyphLine = new int[total];
            this.glyphStrip = new float[total];
            this.glyphSeg = new int[total];
            List<float[]> segs = new ArrayList<>();
            StringBuilder plain = new StringBuilder();
            float lineBase = 0f;
            float stripEnd = 0f;
            k = 0;
            for (int li = 0; li < text.length; li++) {
                for (GuideTextLayout.Piece p : text[li].pieces) {
                    plain.append(p.text);
                    float px = p.x;
                    for (int off = 0; off < p.text.length(); k++) {
                        int cp = p.text.codePointAt(off);
                        off += Character.charCount(cp);
                        FormattedCharSequence one = FormattedCharSequence.codepoint(cp, p.style);
                        float w = splitter.stringWidth(one);
                        glyph[k] = p.glitch ? p.seq : one;
                        ghost[k] = p.ghost;
                        glyphX[k] = p.glitch ? p.x : px;
                        glyphJx[k] = p.jx;
                        glyphLine[k] = li;
                        glyphStrip[k] = lineBase + glyphX[k];
                        stripEnd = glyphStrip[k] + w;
                        if (p.glitch) {
                            boolean joins = k > 0 && glyphSeg[k - 1] >= 0;
                            if (!joins) {
                                segs.add(new float[]{glyphStrip[k], stripEnd});
                            } else {
                                segs.get(segs.size() - 1)[1] = stripEnd;
                            }
                            glyphSeg[k] = segs.size() - 1;
                        } else {
                            glyphSeg[k] = -1;
                        }
                        px += w;
                    }
                }
                lineBase += text[li].width;
            }
            int n = segs.size();
            this.segStart = new float[n];
            this.segEnd = new float[n];
            this.segAt = new int[n];
            float skipped = 0f;
            for (int i = 0; i < n; i++) {
                segStart[i] = segs.get(i)[0];
                segEnd[i] = segs.get(i)[1];
                skipped += segEnd[i] - segStart[i];
            }
            int wipeMs = CommText.wipeDurationMs(plain.toString());
            float travel = Math.max(1f, stripEnd + FEATHER - skipped);
            this.speed = travel / wipeMs;
            float before = 0f;
            for (int i = 0; i < n; i++) {
                segAt[i] = Math.round((segStart[i] - before) / speed) + i * GLITCH_PAUSE;
                before += segEnd[i] - segStart[i];
            }
            this.textStart = who == CommWho.LICH ? TEXT_LICH_MS : TEXT_FRIEND_MS;
            this.wipeTotal = wipeMs + n * GLITCH_PAUSE;
            this.endT = textStart + wipeTotal + TALK_TAIL_MS;
        }

        static CommLaid of(Block b, Ctx c) {
            return new CommLaid(b, c);
        }

        /**
         * 这一帧处在哪个阶段：待机 / 演出中（返回第一次看到之后的毫秒数）/ 静止
         */
        private long phase(GuideRender r, int y) {
            if (CommMemory.seen(key)) {
                return STILL;
            }
            CommMemory.Typing typing = CommMemory.typing(key);
            if (typing == null) {
                // 第一次看到：开场就绪、不在翻页擦除的旧页上、换篇淡入基本完成、块至少一半露在阅读栏里（篇末那条要滚进来才开始）
                int top = Math.max(y, r.clipTop);
                int bottom = Math.min(y + height, r.clipBottom);
                if (!r.mouseActive || r.alpha < 0.8f || (bottom - top) * 2 < height) {
                    return STANDBY;
                }
                if (!GuideDisplayConfig.commTypewriter()) {
                    // 开框与擦出关着：直接显示全文，也不响
                    CommMemory.markSeen(key);
                    return STILL;
                }
                CommMemory.start(key, r.now, endT);
                GuideSfx.comm(who);
                return 0L;
            }
            long e = r.now - typing.start();
            if (e >= endT) {
                CommMemory.markSeen(key);
                return STILL;
            }
            return Math.max(0L, e);
        }

        /** 擦出前沿在整段上的 x（{@code te} = 擦出开始后的毫秒数）：走到故障段一下跳到段尾，停一会再接着走 */
        private float front(long te) {
            float skipped = 0f;
            for (int i = 0; i < segAt.length; i++) {
                if (te < segAt[i]) {
                    return speed * (te - (long) i * GLITCH_PAUSE) + skipped;
                }
                if (te < segAt[i] + GLITCH_PAUSE) {
                    return segEnd[i];
                }
                skipped += segEnd[i] - segStart[i];
            }
            return speed * (te - (long) segAt.length * GLITCH_PAUSE) + skipped;
        }

        /** 故障段显出时整段横跳的偏移：第一拍 +1、第二拍 −1，其余 0 */
        private int kick(long te) {
            for (int at : segAt) {
                if (te >= at && te < at + 2 * KICK_MS) {
                    return te - at < KICK_MS ? 1 : -1;
                }
            }
            return 0;
        }

        @Override
        void render(GuideRender r, int x, int y, int w) {
            GuiGraphics g = r.g;
            long e = phase(r, y);
            boolean standby = e == STANDBY;
            boolean still = e == STILL;

            g.fill(x, y, x + w, y + height, r.fill(GuideTheme.TIP_BG));
            // 左色条：从中点向上下长出（待机时是一道暗条，亮条从它中间长出来）
            if (standby || e < BAR_MS) {
                g.fill(x, y, x + 2, y + height, r.fill(dim));
            }
            if (!standby) {
                float p = e >= BAR_MS ? 1f : GuideTheme.easeOutCubic(e / (float) BAR_MS);
                int cut = Math.round(height / 2f * (1f - p));
                g.fill(x, y + cut, x + 2, y + height - cut, r.fill(color));
            }
            // 右上角 5 级切角，同提示框
            int corner = r.fill(standby ? dim : color);
            for (int i = 0; i < 5; i++) {
                g.fill(x + w - 5 + i, y + i, x + w, y + i + 1, corner);
            }
            if (standby) {
                return;
            }

            // te：擦出开始后的毫秒数（之前为负）；说话态从擦出开始到演完
            long te = still ? STILL : e - textStart;
            boolean talking = !still && te >= 0L;
            int kick = still || te < 0L ? 0 : kick(te);
            int px = x + PAD_L;
            int py = y + PAD_Y;
            if (r.visible(py, PORTRAIT_H)) {
                boolean drawn = false;
                if (!badgeOnly) {
                    frame.reset();
                    frame.alpha = r.alpha;
                    frame.talking = talking;
                    frame.wiping = talking && te < wipeTotal;
                    frame.still = still;
                    frame.kick = kick != 0;
                    frame.openMs = still ? LONG_OPEN_MS : e;
                    // 肖像在台词左边：玄骸朝右看着台词
                    frame.facing = 1;
                    drawn = CommPortraits.draw(g, speaker, LichLook.NONE, px, py, PORTRAIT_W, PORTRAIT_H, frame, r.partialTick);
                }
                if (!drawn) {
                    g.fill(px, py, px + PORTRAIT_W, py + PORTRAIT_H, r.fill(GuideTheme.PLATE_DEEP));
                    badge(r, px + (PORTRAIT_W - CommBadges.SIZE) / 2, py + (PORTRAIT_H - CommBadges.SIZE) / 2,
                            still ? FLICKER_MS : e, talking ? te : -1L, kick);
                }
                nameplate(r, px, py, still ? 1f : (te - NAME_DELAY_MS) / (float) NAME_FADE_MS);
            }

            int tx = x + TEXT_X;
            int ty = y + TEXT_Y;
            if (still || te >= wipeTotal) {
                r.lines(text, tx, ty, LH, false);
            } else if (te >= 0L) {
                wipe(r, tx, ty, te, kick);
            }
        }

        /** 名牌：肖像左下角，粗体、发言人色、带阴影 */
        private void nameplate(GuideRender r, int px, int py, float a) {
            if (a < CommText.MIN_GLYPH_ALPHA) {
                return;
            }
            int c = r.text(GuideTheme.withAlpha(color, Math.round(Math.min(1f, a) * 255f)));
            PoseStack ps = r.g.pose();
            ps.pushPose();
            ps.translate(px + NAME_INSET, py + PORTRAIT_H - NAME_INSET - 8f * nameScale, 0f);
            if (nameScale != 1f) {
                ps.scale(nameScale, nameScale, 1f);
            }
            for (int i = 0; i < nameGlyph.length; i++) {
                r.g.drawString(r.font, nameGlyph[i], nameX[i], 0f, c, true);
            }
            ps.popPose();
        }

        /**
         * 擦出中：逐字按前沿算不透明度画，太淡的字不画（原版字体把 α 低于 4/255 的颜色当成不透明，会闪一下满亮）。
         * 故障段的字到了时刻整段直接满亮；残影先整段画、本体后画，免得后一个字的残影叠在前一个字的本体上
         */
        private void wipe(GuideRender r, int tx, int ty, long te, int kick) {
            GuiGraphics g = r.g;
            float front = front(te);
            for (int k = 0; k < glyph.length; k++) {
                int seg = glyphSeg[k];
                if (seg < 0 || te < segAt[seg]) {
                    continue;
                }
                int ly = ty + glyphLine[k] * LH;
                if (!r.visible(ly, LH) || ghost[k] == null) {
                    continue;
                }
                int gx = tx + Math.round(glyphX[k]) + kick;
                g.drawString(r.font, ghost[k], gx - 1, ly + 1, r.text(GuideRender.GHOST_MAGENTA), false);
                g.drawString(r.font, ghost[k], gx + 1, ly - 1, r.text(GuideRender.GHOST_CYAN), false);
            }
            for (int k = 0; k < glyph.length; k++) {
                int seg = glyphSeg[k];
                float a;
                if (seg >= 0) {
                    a = te >= segAt[seg] ? 1f : 0f;
                } else {
                    a = CommText.wipeAlpha(front, glyphStrip[k], FEATHER);
                }
                if (a < CommText.MIN_GLYPH_ALPHA) {
                    continue;
                }
                int ly = ty + glyphLine[k] * LH;
                if (!r.visible(ly, LH)) {
                    continue;
                }
                int c = r.text(GuideTheme.withAlpha(0xFFFFFFFF, Math.round(a * 255f)));
                if (seg >= 0) {
                    g.drawString(r.font, glyph[k], tx + Math.round(glyphX[k]) + glyphJx[k] + kick, ly, c, false);
                } else {
                    g.drawString(r.font, glyph[k], tx + glyphX[k], (float) ly, c, false);
                }
            }
        }

        /**
         * 退回徽记时：开框按全息闪烁表上屏，说话期间是说话态，其余时间静止
         *
         * @param e  第一次看到之后的毫秒数（闪烁用）
         * @param te 擦出开始后的毫秒数；不在说话态为负
         */
        private void badge(GuideRender r, int bx, int by, long e, long te, int kick) {
            GuiGraphics g = r.g;
            int well = GuideTheme.PLATE_DEEP;
            float k = e < FLICKER_MS ? FxCurve.flicker(e) : 1f;
            for (int i = 0; i < 4; i++) {
                colors[i] = r.fill(GuideTheme.lerpColor(well, palette[i], k));
            }
            boolean speaking = te >= 0L;
            int[] frameLayer = CommBadges.frame(who);
            int[] base = CommBadges.base(who);
            int[] accent = CommBadges.accent(who);
            if (who == CommWho.ORDIS && kick != 0) {
                // 故障段的两拍：整枚徽记左右各叠一份色差副本，碎块额外往右上错一格
                int magenta = r.fill(GuideTheme.lerpColor(well, SPLIT_MAGENTA, 0.6f));
                int cyan = r.fill(GuideTheme.lerpColor(well, SPLIT_CYAN, 0.6f));
                CommBadges.drawMono(g, frameLayer, bx - 1, by, magenta);
                CommBadges.drawMono(g, base, bx - 1, by, magenta);
                CommBadges.drawMono(g, accent, bx - 1, by, magenta);
                CommBadges.drawMono(g, frameLayer, bx + 1, by, cyan);
                CommBadges.drawMono(g, base, bx + 1, by, cyan);
                CommBadges.drawMono(g, accent, bx + 1, by, cyan);
            }
            CommBadges.draw(g, frameLayer, bx, by, colors);
            CommBadges.draw(g, base, bx, by, colors);
            switch (who) {
                case LOTUS -> {
                    CommBadges.draw(g, accent, bx, by, colors);
                    if (speaking) {
                        // 一道 1px 亮线每 700ms 从顶扫到底，后面拖两行渐暗
                        int row = 1 + (int) (te % SCAN_MS) * (CommBadges.SIZE - 2) / SCAN_MS;
                        for (int d = 0; d < 3; d++) {
                            int rr = row - d;
                            if (rr < 0) {
                                continue;
                            }
                            float hk = d == 0 ? 1f : d == 1 ? 0.55f : 0.25f;
                            for (int i = 0; i < 4; i++) {
                                scanColors[i] = r.fill(GuideTheme.lerpColor(palette[i], palette[CommBadges.C_HI], hk));
                            }
                            CommBadges.drawRow(g, frameLayer, bx, by, rr, scanColors);
                            CommBadges.drawRow(g, base, bx, by, rr, scanColors);
                        }
                    }
                }
                case ORDIS -> {
                    CommBadges.draw(g, accent, bx + (kick != 0 ? 1 : 0), by - (kick != 0 ? 1 : 0), colors);
                    if (speaking) {
                        // 每 240ms 生一道涟漪，480ms 里半径 10 → 14 外扩、颜色从主色褪到槽底色；同一时刻至多两道
                        int age0 = (int) te;
                        int newest = age0 / RIPPLE_EVERY;
                        for (int q = newest; q >= 0 && q >= newest - 1; q--) {
                            int age = age0 - q * RIPPLE_EVERY;
                            if (age >= RIPPLE_LIFE) {
                                continue;
                            }
                            float f = age / (float) RIPPLE_LIFE;
                            float radius = 10f + 4f * f;
                            int layer = radius < 11f ? 0 : radius < 13f ? 1 : 2;
                            CommBadges.drawMono(g, CommBadges.ripple(layer), bx, by,
                                    r.fill(GuideTheme.lerpColor(palette[CommBadges.C_MAIN], well, f)));
                        }
                    }
                }
                case LICH -> {
                    if (speaking) {
                        float beat = FxCurve.heartbeat(te / HEART_MS, HEART_MS);
                        CommBadges.drawMono(g, accent, bx, by, r.fill(GuideTheme.lerpColor(CRACK_LOW, CRACK_HIGH, beat)));
                    } else {
                        CommBadges.draw(g, accent, bx, by, colors);
                    }
                }
            }
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
            this.height = PAD * 2 + LH * 2 - TAIL;
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
            // 按住时底色转暗（赤毒暗面）
            boolean press = hot && r.mouseDown;
            g.fill(x, y, x + w, y + height, r.fill(press ? GuideTheme.PRESS_BG
                    : hot ? GuideTheme.withAlpha(GuideTheme.KUVA, 0x22) : GuideTheme.PLATE_DEEP));
            CodexTheme.border(g, x, y, w, height, r.fill(hot ? GuideTheme.KUVA : GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0)));
            // 朝翻页方向的那条边用科技青加粗，像一个指向箭头的按钮
            int ex = forward ? x + w - 2 : x;
            g.fill(ex, y, ex + 2, y + height, r.fill(hot ? GuideTheme.KUVA : GuideTheme.TECH));
            int iconX = forward ? x + w - PAD - ICON - 2 : x + PAD + 2;
            if (r.solidReady()) {
                GuideItemIcon.render(g, cd.icon, iconX, y + (height - ICON) / 2);
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
            this.height = Math.max(1, lines.length) * LH - TAIL + 14;
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
            int height = caption.length > 0 ? captionY + caption.length * LH - TAIL : gridH;
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
                    GuideItemIcon.render(g, stacks[i], sx + 1, sy + 1);
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
     * 工作台配方：3×3 + 箭头 + 产物
     *
     * <p>一格有几种候选材料时（比如「任意木板」），平时停在第一种，<b>鼠标压着这张配方时</b>才每秒轮换 ——
     * 读的地方不放常驻动画。这种格子右上角有一枚 3px 的科技青小三角，告诉读者「这里不止一种」。</p>
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
            boolean cycling = r.over(fx, y, FRAME_W, FRAME_H);
            long tick = cycling ? r.now / 1000L : 0L;
            for (int i = 0; i < 9; i++) {
                int sx = gx + (i % 3) * SLOT;
                int sy = gy + (i / 3) * SLOT;
                g.fill(sx, sy, sx + SLOT, sy + SLOT, r.fill(0xFF0A0E11));
                CodexTheme.border(g, sx, sy, SLOT, SLOT, r.fill(GuideTheme.withAlpha(GuideTheme.EDGE, 0xC0)));
                ItemStack[] cand = data.slots[i];
                if (cand != null && cand.length > 0) {
                    ItemStack s = cand[(int) (tick % cand.length)];
                    if (r.solidReady()) {
                        GuideItemIcon.render(g, s, sx + 1, sy + 1);
                    }
                    if (cand.length > 1) {
                        // 「不止一种」：右上角 3px 小三角，抬到物品图标之上
                        g.pose().pushPose();
                        g.pose().translate(0f, 0f, 200f);
                        int tri = r.fill(GuideTheme.TECH);
                        for (int k = 0; k < 3; k++) {
                            g.fill(sx + SLOT - 4 + k, sy + 1 + k, sx + SLOT - 1, sy + 2 + k, tri);
                        }
                        g.pose().popPose();
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
                GuideItemIcon.render(g, data.result, rx + 5, ry + 5);
                g.renderItemDecorations(r.font, data.result, rx + 5, ry + 5);
            }
            if (over) {
                r.hoverStack = data.result;
            }

            g.drawString(r.font, resultName, x + (w - resultNameW) / 2, y + FRAME_H + 3, r.text(GuideTheme.ASH), false);
        }
    }

    /**
     * 实体预览：鼠标在阅读区时模型跟着鼠标转头，不在时静止侧身；建不出来时框里换成问号占位，框下照常写名字
     */
    private static final class EntityLaid extends Laid {
        /** 预览实体推到第几 tick 停住：够走完待机的淡入（玄骸 4 tick、奴仆 3 tick）再往里走几帧 */
        private static final int SETTLE_TICKS = 12;

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
                    g.drawString(r.font, "?", bx + (bw - qW) / 2, y + boxH / 2 - 4, r.text(GuideTheme.FAINT_TEXT), false);
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
                    // 鼠标不在阅读区时停在微微侧身、略抬头的姿势（原先会自己左右环顾；读的地方不放常驻动画）
                    lookX = 24f;
                    lookY = -10f;
                }
                // 预览实体不在世界里、不走 tick，GeckoLib 按 tickCount 算动画时间：一直是 0 就永远卡在「淡进待机」的头几帧，
                // 摆出来的是别处同模型实体留在骨骼上的姿势（头歪着）。头几帧每帧推一 tick，让待机走完淡入再停住（读的地方不放常驻动画）
                if (entity.tickCount < SETTLE_TICKS) {
                    entity.tickCount++;
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
