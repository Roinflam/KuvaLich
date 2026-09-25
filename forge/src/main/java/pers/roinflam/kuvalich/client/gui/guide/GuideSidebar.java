package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.CatView;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.EntryView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 阅读页左栏：目录（分组折叠列表）或搜索结果
 *
 * <h3>为什么是「只展开当前分类」的折叠目录</h3>
 * <p>十个分类、六十来篇，全部展开要滚好几屏，找别的分类得先滚过当前分类的所有条目；
 * 只放一排分类图标又得靠悬停才知道哪个是哪个。折叠目录两头都顾到：
 * 所有分类的名字始终在眼前，<b>点一下分类名就直接跳到该分类第一篇</b>（切换一步到位），
 * 当前分类自动展开、其余收起，树线把条目挂在分类下面，层级一眼可见。</p>
 *
 * <p>行在切换时一次性拍平成 {@link Row} 序列、标签按宽度截好，渲染时只遍历可见的行、不做任何字符串运算。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
final class GuideSidebar {

    private static final int HEAD_H = 15;
    private static final int CAT_H = 18;
    private static final int ENTRY_H = 15;
    private static final int RESULT_H = 23;
    private static final int SCROLLBAR_ZONE = 6;

    enum Kind { CAT, ENTRY, RESULT }

    /**
     * 一行
     */
    static final class Row {
        final Kind kind;
        @Nullable
        final CatView cat;
        @Nullable
        final EntryView entry;
        final int y;
        final int h;
        final String label;
        final String sub;
        final String count;
        /** count 的字宽（建行时量好，渲染时右对齐用） */
        final int countW;
        /** 是不是本分类最后一个条目（树线画到一半） */
        final boolean last;
        /** 搜索结果的序号 */
        final int resultIndex;

        Row(Kind kind, @Nullable CatView cat, @Nullable EntryView entry, int y, int h, String label, String sub,
            String count, int countW, boolean last, int resultIndex) {
            this.kind = kind;
            this.cat = cat;
            this.entry = entry;
            this.y = y;
            this.h = h;
            this.label = label;
            this.sub = sub;
            this.count = count;
            this.countW = countW;
            this.last = last;
            this.resultIndex = resultIndex;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int contentH;
    private boolean searchMode;
    private String headLabel = "";
    private String headCount = "";
    private int headCountW;
    /** 没有搜索结果时的提示（按栏宽折好行） */
    private List<String> emptyLines = List.of();

    int x;
    int y;
    int w;
    int h;
    float scroll;
    float target;
    private final GuideScrollDrag bar = new GuideScrollDrag();

    void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    boolean isSearchMode() {
        return searchMode;
    }

    private int listTop() {
        return y + HEAD_H + 2;
    }

    private int listH() {
        return h - HEAD_H - 2;
    }

    // ==================== 构建 ====================

    /**
     * 目录模式：全部分类，只展开当前分类
     */
    void showTree(GuideView view, @Nullable CatView expanded, Font font) {
        boolean modeChanged = searchMode;
        searchMode = false;
        rows.clear();
        int rowW = w - SCROLLBAR_ZONE;
        int cy = 0;
        for (CatView cat : view.categories) {
            String count = String.valueOf(cat.entries.size());
            int countW = font.width(count);
            int labelW = rowW - 22 - countW - 8;
            rows.add(new Row(Kind.CAT, cat, null, cy, CAT_H, GuideTheme.ellipsize(font, cat.title(), labelW), "",
                    count, countW, false, -1));
            cy += CAT_H;
            if (expanded != null && cat == expanded) {
                for (int i = 0; i < cat.entries.size(); i++) {
                    EntryView ev = cat.entries.get(i);
                    rows.add(new Row(Kind.ENTRY, cat, ev, cy, ENTRY_H,
                            GuideTheme.ellipsize(font, ev.title(), rowW - 34 - 4), "", "", 0,
                            i == cat.entries.size() - 1, -1));
                    cy += ENTRY_H;
                }
                cy += 3;
            }
        }
        contentH = cy;
        headLabel = I18n.get("kuvalich.guide.contents");
        headCount = I18n.get("kuvalich.guide.entry_count", view.entryList.size());
        headCountW = font.width(headCount);
        if (modeChanged) {
            scroll = target = 0;
        }
        clamp();
    }

    /**
     * 搜索模式：命中列表（带分类名与上下文）
     *
     * @param text 排版器：「没有结果」的提示要按词、按避头尾规则折行
     */
    void showResults(List<GuideSearch.Hit> hits, GuideTextLayout text) {
        Font font = text.font();
        boolean modeChanged = !searchMode;
        searchMode = true;
        rows.clear();
        int rowW = w - SCROLLBAR_ZONE;
        int cy = 0;
        for (int i = 0; i < hits.size(); i++) {
            GuideSearch.Hit hit = hits.get(i);
            EntryView ev = hit.entry;
            String sub = hit.snippet.isEmpty() ? ev.cat.title() : ev.cat.title() + " · " + hit.snippet;
            rows.add(new Row(Kind.RESULT, ev.cat, ev, cy, RESULT_H,
                    GuideTheme.ellipsize(font, ev.title(), rowW - 24 - 4),
                    GuideTheme.ellipsize(font, sub, rowW - 24 - 4), "", 0, false, i));
            cy += RESULT_H;
        }
        contentH = cy;
        if (hits.isEmpty()) {
            // 窄栏里一句话可能放不下：只在结果变化时折一次。走正文的折行规则 ——
            // 按像素硬切的话英文会从单词中间断开（"No matching entries. T" / "ry another…"），中文逗号也可能落到行首
            emptyLines = text.wrapPlain(I18n.get("kuvalich.guide.no_results"), w - 16, 4);
        }
        headLabel = I18n.get("kuvalich.guide.results");
        headCount = I18n.get("kuvalich.guide.result_count", hits.size());
        headCountW = font.width(headCount);
        // 每次输入结果都会变：回到顶部，第一条永远在眼前
        scroll = target = 0;
        if (modeChanged) {
            clamp();
        }
    }

    // ==================== 滚动 ====================

    private float maxScroll() {
        return Math.max(0, contentH - listH() + 4);
    }

    private void clamp() {
        float max = maxScroll();
        target = Math.max(0, Math.min(max, target));
        scroll = Math.max(0, Math.min(max, scroll));
    }

    /**
     * 把某篇（目录模式）滚进可视区
     */
    void reveal(@Nullable EntryView ev) {
        if (ev == null) {
            return;
        }
        for (Row row : rows) {
            if (row.entry == ev) {
                revealRow(row);
                return;
            }
        }
    }

    /**
     * 把第 index 条结果滚进可视区
     */
    void revealResult(int index) {
        for (Row row : rows) {
            if (row.resultIndex == index) {
                revealRow(row);
                return;
            }
        }
    }

    private void revealRow(Row row) {
        int lh = listH();
        int top = row.y;
        if (row.kind == Kind.ENTRY && row.entry != null) {
            // 目录模式下连同所属分类的标题行一起带进来（放得下的话），否则看不到自己在哪个分类里
            int catTop = row.y - row.entry.index * ENTRY_H - CAT_H;
            if (row.y + row.h - catTop <= lh) {
                top = catTop;
            }
        }
        if (top < target) {
            target = top;
        } else if (row.y + row.h > target + lh) {
            target = row.y + row.h - lh + 4;
        }
        clamp();
    }

    /**
     * 窗口尺寸变了、重建了列表之后，把滚动位置放回原处（{@link #showResults} 会把它归零）
     */
    void restoreScroll(float to) {
        scroll = target = to;
        clamp();
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (!contains(mx, my)) {
            return false;
        }
        target -= (float) (delta * 30);
        clamp();
        return true;
    }

    private boolean overScrollbar(double mx, double my) {
        int top = listTop();
        return maxScroll() > 0 && mx >= x + w - SCROLLBAR_ZONE && mx < x + w && my >= top && my < top + listH();
    }

    /** 按在滚动条上：点轨道跳过去 / 抓住滑块（与正文的滚动条同一手感） */
    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !overScrollbar(mx, my)) {
            return false;
        }
        scroll = target = bar.press(my, listTop() + 1, listH() - 2, scroll, maxScroll());
        return true;
    }

    boolean mouseDragged(double my, int button) {
        if (!bar.dragging()) {
            return false;
        }
        if (button != 0) {
            bar.release();
            return false;
        }
        scroll = target = bar.drag(my, listH() - 2, maxScroll());
        return true;
    }

    void release() {
        bar.release();
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * 鼠标下的行
     */
    @Nullable
    Row rowAt(double mx, double my) {
        int top = listTop();
        if (mx < x || mx >= x + w - SCROLLBAR_ZONE || my < top || my >= top + listH()) {
            return null;
        }
        double local = my - top + scroll;
        for (Row row : rows) {
            if (local >= row.y && local < row.y + row.h) {
                return row;
            }
        }
        return null;
    }

    int resultCount() {
        return searchMode ? rows.size() : 0;
    }

    @Nullable
    EntryView resultAt(int index) {
        for (Row row : rows) {
            if (row.resultIndex == index) {
                return row.entry;
            }
        }
        return null;
    }

    // ==================== 绘制 ====================

    /**
     * @param r       绘制上下文
     * @param current 当前条目
     * @param cursor  键盘选中的搜索结果序号（-1 表示没有）
     * @param delta   距上一帧的秒数
     * @param offsetY 开合动画的位移
     */
    void render(GuideRender r, @Nullable EntryView current, int cursor, float delta, int offsetY) {
        GuiGraphics g = r.g;
        scroll = GuideTheme.approach(scroll, target, delta, 18f);

        // 左栏底色比右栏深一档：导航与正文分成两块，视线不会在两边来回串
        g.fill(x, y, x + w, y + h, GuideTheme.withAlpha(GuideTheme.PLATE_DEEP, 0xB0));
        g.fill(x + w, y, x + w + 1, y + h, GuideTheme.withAlpha(GuideTheme.EDGE, 0xC0));

        // 栏头：「目录 / 搜索结果」+ 计数
        g.drawString(r.font, headLabel, x + 6, y + 4, searchMode ? GuideTheme.TECH : GuideTheme.ASH, false);
        g.drawString(r.font, headCount, x + w - SCROLLBAR_ZONE - 2 - headCountW, y + 4,
                searchMode && rows.isEmpty() ? GuideTheme.TAG_W : GuideTheme.FAINT, false);
        g.fill(x + 4, y + HEAD_H, x + w - 4, y + HEAD_H + 1, GuideTheme.withAlpha(GuideTheme.EDGE, 0xA0));

        int top = listTop();
        int lh = listH();
        if (rows.isEmpty() && searchMode) {
            int ly = top + 10;
            for (String s : emptyLines) {
                g.drawString(r.font, s, x + 8, ly, GuideTheme.FAINT, false);
                ly += GuideTheme.LINE_H;
            }
            return;
        }

        g.enableScissor(x, top + offsetY, x + w, top + lh + offsetY);
        int base = top - Math.round(scroll);
        Row hovered = r.mouseActive ? rowAt(r.mouseX, r.mouseY) : null;
        int rowW = w - SCROLLBAR_ZONE;
        for (Row row : rows) {
            int ry = base + row.y;
            if (ry + row.h < top) {
                continue;
            }
            if (ry > top + lh) {
                break;
            }
            boolean hover = row == hovered;
            switch (row.kind) {
                case CAT -> drawCat(r, row, ry, rowW, hover, current);
                case ENTRY -> drawEntry(r, row, ry, rowW, hover, current);
                case RESULT -> drawResult(r, row, ry, rowW, hover, current, cursor);
            }
        }
        g.disableScissor();

        float max = maxScroll();
        if (max > 0) {
            boolean active = bar.dragging() || (r.mouseActive && overScrollbar(r.mouseX, r.mouseY));
            CodexTheme.scrollbar(g, x + w - SCROLLBAR_ZONE + 2, top + 1, lh - 2, scroll, max, active);
        }
    }

    private void drawCat(GuideRender r, Row row, int ry, int rowW, boolean hover, @Nullable EntryView current) {
        GuiGraphics g = r.g;
        boolean active = current != null && current.cat == row.cat;
        if (active) {
            g.fill(x, ry, x + rowW, ry + row.h, GuideTheme.withAlpha(GuideTheme.TECH, 0x14));
            g.fill(x, ry, x + 2, ry + row.h, GuideTheme.TECH);
        } else if (hover) {
            GuideTheme.gradientRow(g, x, ry, rowW, row.h, GuideTheme.withAlpha(GuideTheme.TECH, 0x2A));
        }
        icon(g, row.cat.icon(), x + 5, ry + 3);
        int color = active ? GuideTheme.BONE : hover ? GuideTheme.BONE : GuideTheme.ASH;
        g.drawString(r.font, row.label, x + 21, ry + 5, color, active);
        g.drawString(r.font, row.count, x + rowW - 4 - row.countW, ry + 5, active ? GuideTheme.TECH : GuideTheme.FAINT, false);
    }

    private void drawEntry(GuideRender r, Row row, int ry, int rowW, boolean hover, @Nullable EntryView current) {
        GuiGraphics g = r.g;
        boolean selected = row.entry == current;
        // 树线：把条目挂在分类下面
        int tx = x + 10;
        int treeColor = GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF);
        g.fill(tx, ry, tx + 1, row.last ? ry + row.h / 2 + 1 : ry + row.h, treeColor);
        g.fill(tx + 1, ry + row.h / 2, tx + 5, ry + row.h / 2 + 1, treeColor);
        int left = x + 16;
        if (selected) {
            g.fill(left, ry, x + rowW, ry + row.h, GuideTheme.withAlpha(GuideTheme.KUVA, 0x24));
            // 选中行的竖条缓慢呼吸：一屏里唯一还在动的元素，视线离开再回来时能立刻找回当前位置
            g.fill(left, ry, left + 2, ry + row.h,
                    GuideTheme.fade(GuideTheme.KUVA, GuideTheme.breathe(r.time, 2.4f, 0.55f)));
        } else if (hover) {
            GuideTheme.gradientRow(g, left, ry, x + rowW - left, row.h, GuideTheme.withAlpha(GuideTheme.TECH, 0x2A));
        }
        icon(g, row.entry.icon(), left + 4, ry + 2);
        int color = selected ? GuideTheme.BONE : hover ? GuideTheme.BONE : 0xFFA9B4BA;
        g.drawString(r.font, row.label, left + 19, ry + 4, color, false);
    }

    private void drawResult(GuideRender r, Row row, int ry, int rowW, boolean hover, @Nullable EntryView current, int cursor) {
        GuiGraphics g = r.g;
        boolean selected = row.entry == current;
        boolean focused = row.resultIndex == cursor;
        if (selected) {
            g.fill(x, ry, x + rowW, ry + row.h, GuideTheme.withAlpha(GuideTheme.KUVA, 0x20));
            g.fill(x, ry, x + 2, ry + row.h, GuideTheme.KUVA);
        } else if (hover || focused) {
            GuideTheme.gradientRow(g, x, ry, rowW, row.h, GuideTheme.withAlpha(GuideTheme.TECH, focused ? 0x40 : 0x2A));
        }
        if (focused) {
            CodexTheme.brackets(g, x + 1, ry, rowW - 2, row.h, 3, GuideTheme.TECH);
        }
        g.renderItem(row.entry.icon(), x + 4, ry + 3);
        g.drawString(r.font, row.label, x + 24, ry + 3, selected || hover ? GuideTheme.BONE : 0xFFB9C4CA, false);
        g.drawString(r.font, row.sub, x + 24, ry + 13, GuideTheme.FAINT, false);
        g.fill(x + 4, ry + row.h - 1, x + rowW - 4, ry + row.h, GuideTheme.withAlpha(GuideTheme.EDGE, 0x50));
    }

    /**
     * 0.75 倍的物品图标（12px）：列表行高只有十几像素，原尺寸图标会把行撑开
     */
    private static void icon(GuiGraphics g, ItemStack stack, int ix, int iy) {
        g.pose().pushPose();
        g.pose().translate(ix, iy, 0f);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }
}
