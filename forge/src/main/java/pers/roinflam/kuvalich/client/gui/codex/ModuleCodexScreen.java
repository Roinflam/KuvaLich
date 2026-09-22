package pers.roinflam.kuvalich.client.gui.codex;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.packet.CodexGiveItemPacket;
import pers.roinflam.kuvalich.network.packet.CodexInstallModulePacket;
import pers.roinflam.kuvalich.network.packet.ModuleDiscoveryPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组图鉴界面
 * Module Codex Screen
 *
 * 功能清单：
 * - 稀有度指示器：10×2薄条 + 两端渐隐
 * - 搜索框：关键词实时筛选
 * - 所有模组悬停显示原生Tooltip
 * - 底部 [TAB] 返回提示（带底衬）
 * - ⭐ 创造模式点击模组 → 发给背包（满了掉脚下）
 * - ⭐ 发现记录使用 discoveryKey（type:rarityOrder）区分品质
 *
 * <p>⭐ 本次改动（配合 {@link ModuleCodexData} 的惰性化）：</p>
 * <ol>
 *   <li>{@code e.searchableText} 字段访问改为 {@code e.getSearchableText()} 方法调用。
 *       该字段已改为惰性构建的私有字段，只有在真正需要匹配时才会触发一次
 *       {@code getTooltipLines} 计算，打开图鉴时不再有批量构建的卡顿。</li>
 *   <li>{@link #onSearchChanged()} 增加<b>前缀增量收窄</b>：新查询以旧查询为前缀时，
 *       从上一次的结果集继续筛选而非重新遍历全表。因为超集查询的结果必然是
 *       子集查询结果的子集，语义完全等价；连续输入时只有第一个字符需要遍历全表，
 *       后续按键的开销随结果集收缩而快速下降。退格 / 改词时前缀不成立，
 *       自动回退到全表筛选，结果始终正确。</li>
 * </ol>
 * <p>搜索结果与改动前完全一致，仅计算时机和范围不同。</p>
 */
@OnlyIn(Dist.CLIENT)
public class ModuleCodexScreen extends Screen {

    // ==================== 布局 ====================

    private static final int COLUMNS = 10;
    private static final int CELL_W = 22;
    private static final int CELL_H = 23;
    private static final int GAP = 3;
    private static final int IND_SOLID_W = 8;
    private static final int IND_H = 2;
    private static final int IND_TOTAL_W = IND_SOLID_W + 2;
    private static final int SECTION_H = 16;
    private static final int SECTION_GAP = 6;
    private static final int TITLE_H = 18;
    private static final int SEARCH_H = 18;
    private static final int TOP_H = TITLE_H + SEARCH_H + 8;
    private static final int BOTTOM_H = 20;
    private static final int SCROLLBAR_W = 4;

    // ==================== 颜色 ====================

    private static final int BG = 0xD0000000;
    private static final int CELL_BG_FOUND = 0x28FFFFFF;
    private static final int CELL_BG_MISS = 0x10FFFFFF;
    private static final int CELL_HOVER_FOUND = 0x45FFFFFF;
    private static final int CELL_HOVER_MISS = 0x20FFFFFF;
    private static final int OVERLAY_MISS = 0xA0080810;
    private static final int SECTION_LINE_COLOR = 0x30FFFFFF;
    private static final int HINT_TEXT = 0xFFBBBBBB;

    private static final int FILTER_TEXT = 0xFFAAAAAA;

    // ==================== 状态 ====================

    private final Screen parentScreen;
    private final boolean showWeapon;

    private EditBox searchBox;
    private double scrollOffset = 0;
    private int totalContentH = 0;
    private int visibleH = 0;
    private int contentX, contentY, contentW;

    private List<ModuleCodexData.CodexEntry> filtered = null;
    private String lastSearch = "";

    private ModuleCodexData.CodexEntry hoveredEntry = null;
    private int hoverMX, hoverMY;

    private boolean dragging = false;
    private double dragStartY, dragStartScroll;

    public ModuleCodexScreen(Screen parent, boolean showWeapon) {
        super(Component.translatable(
                showWeapon ? "kuvalich.codex.title.weapon" : "kuvalich.codex.title.warframe"));
        this.parentScreen = parent;
        this.showWeapon = showWeapon;
    }

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        super.init();
        contentW = COLUMNS * (CELL_W + GAP) - GAP;
        contentX = (this.width - contentW) / 2;
        contentY = TOP_H;
        visibleH = this.height - TOP_H - BOTTOM_H;

        // ⭐ resize / 改 GUI 缩放会让 Minecraft 对同一个 Screen 实例重新调用 init()，
        //    这里必须先把旧搜索词存下来：不存的话新建的空 EditBox 会让紧随其后的
        //    onSearchChanged() 把筛选结果重置成全表，玩家已经输入的搜索词无声消失。
        String previousSearch = searchBox != null ? searchBox.getValue() : "";

        int boxW = Math.min(contentW, 200);
        int boxX = (this.width - boxW) / 2;
        int boxY = TITLE_H + 2;
        searchBox = new EditBox(this.font, boxX, boxY, boxW, 14, Component.empty());
        searchBox.setMaxLength(50);
        searchBox.setBordered(true);
        searchBox.setHint(Component.translatable("kuvalich.codex.search_hint"));
        searchBox.setResponder(text -> onSearchChanged());
        if (!previousSearch.isEmpty()) {
            // setValue 会触发上面的 responder，onSearchChanged 因此会用新词跑一次
            searchBox.setValue(previousSearch);
        }
        this.addRenderableWidget(searchBox);

        onSearchChanged();
    }

    /**
     * 搜索词变化时重新筛选条目
     *
     * <p>⭐ 前缀增量收窄：当新查询以旧查询为前缀且已有结果集时，
     * 只需在旧结果集内继续筛选。这在语义上完全等价——
     * 若某条目不含 "fi"，则必然也不含 "fir"。</p>
     *
     * <p>⭐ 可搜索文本通过 {@link ModuleCodexData.CodexEntry#getSearchableText()}
     * 惰性获取，未被匹配到的条目永远不会付出 tooltip 构建开销。</p>
     */
    private void onSearchChanged() {
        String query = searchBox.getValue().trim().toLowerCase();
        if (query.equals(lastSearch) && filtered != null) return;

        // ⭐ 判断能否从上一次的结果集继续收窄（必须在更新 lastSearch 之前判断）
        boolean canNarrow = filtered != null
                && !query.isEmpty()
                && !lastSearch.isEmpty()
                && query.startsWith(lastSearch);

        lastSearch = query;

        List<ModuleCodexData.CodexEntry> all = showWeapon ?
                ModuleCodexData.getWeaponModules() : ModuleCodexData.getWarframeModules();
        if (query.isEmpty()) {
            filtered = all;
        } else {
            // 增量收窄时从旧结果集筛选，否则从全表筛选
            List<ModuleCodexData.CodexEntry> source = canNarrow ? filtered : all;
            List<ModuleCodexData.CodexEntry> result = new ArrayList<>();
            for (ModuleCodexData.CodexEntry e : source) {
                if (e.getSearchableText().contains(query)) result.add(e);
            }
            filtered = result;
        }
        recalcContentH();
        scrollOffset = clamp(scrollOffset);
    }

    private void recalcContentH() {
        if (filtered == null || filtered.isEmpty()) { totalContentH = 0; return; }
        int h = 0, lastR = -1, count = 0;
        for (ModuleCodexData.CodexEntry e : filtered) {
            if (e.rarityOrder != lastR) {
                if (lastR != -1) h += groupH(count) + SECTION_GAP;
                h += SECTION_H; count = 0; lastR = e.rarityOrder;
            }
            count++;
        }
        if (count > 0) h += groupH(count);
        h += SECTION_GAP;
        totalContentH = h;
    }

    private int groupH(int n) { return ((n + COLUMNS - 1) / COLUMNS) * (CELL_H + GAP) - GAP; }
    private double maxScroll() { return Math.max(0, totalContentH - visibleH); }
    private double clamp(double s) { return Math.max(0, Math.min(s, maxScroll())); }

    // ==================== 输入 ====================

    @Override
    public boolean keyPressed(int key, int scan, int mod) {
        if (key == GLFW.GLFW_KEY_TAB) { onClose(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!searchBox.getValue().isEmpty()) { searchBox.setValue(""); return true; }
            onClose(); return true;
        }
        if (searchBox.isFocused() && searchBox.keyPressed(key, scan, mod)) return true;
        return super.keyPressed(key, scan, mod);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parentScreen); }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollOffset = clamp(scrollOffset - delta * (CELL_H + GAP) * 2);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // ⭐ 创造模式：左键点击模组 → 发给背包；Shift+左键 → 直接装到第一个空槽
        //    用 Shift 区分而不是改掉原有的「发给背包」：两个都是有用的操作，
        //    Shift+点击在原版里本来就是「同一个位置的另一种动作」的惯例。
        if (btn == 0 && hoveredEntry != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.isCreative()) {
                if (hasShiftDown()) {
                    // 能不能装、装到哪个槽，全部由服务端判定 —— 这里只是发起请求。
                    // 客户端不做任何「看起来有空位」的预判，那种预判在改装客户端面前毫无意义，
                    // 而且会和服务端的冲突判定产生不一致。
                    NetworkRegistryHandler.getChannel().sendToServer(
                            new CodexInstallModulePacket(hoveredEntry.moduleType, showWeapon,
                                    hoveredEntry.rarityOrder));
                } else {
                    NetworkRegistryHandler.getChannel().sendToServer(
                            new CodexGiveItemPacket(hoveredEntry.moduleType, showWeapon,
                                    hoveredEntry.rarityOrder));
                }
                return true;
            }
        }

        // 滚动条拖拽 / Scrollbar drag
        if (btn == 0 && maxScroll() > 0) {
            int sbX = contentX + contentW + 6;
            if (mx >= sbX && mx <= sbX + SCROLLBAR_W && my >= contentY && my < contentY + visibleH) {
                dragging = true; dragStartY = my; dragStartScroll = scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) { dragging = false; return super.mouseReleased(mx, my, btn); }

    /**
     * ⭐ 界面关闭时兜底复位拖拽状态
     *
     * <p>按住滚动条滑块的同时 Alt+Tab 切走、在失焦期间松手，这次 release 事件不会传到
     * {@link #mouseReleased}，{@code dragging} 会一直是 true。切回来后只要在图鉴里
     * 按下鼠标并轻微移动，就会复用上一次残留的 {@code dragStartY / dragStartScroll}，
     * 把滚动位置跳到一个和鼠标位置毫不相干的值。</p>
     */
    @Override
    public void removed() {
        dragging = false;
        super.removed();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        // ⭐ 同时校验按键：残留的 dragging 配上一次非左键拖拽也会触发跳变
        if (btn != 0) {
            dragging = false;
        }
        if (dragging && btn == 0 && maxScroll() > 0) {
            double trackH = visibleH - 20.0;
            scrollOffset = clamp(dragStartScroll + (my - dragStartY) * (maxScroll() / trackH));
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, BG);
        hoveredEntry = null;
        drawTitle(g);
        drawContent(g, mx, my);
        drawScrollbar(g);
        drawBottom(g);
        super.render(g, mx, my, pt);
        if (hoveredEntry != null) {
            g.renderTooltip(this.font, hoveredEntry.displayStack, hoverMX, hoverMY);
        }
    }

    private void drawTitle(GuiGraphics g) {
        if (filtered == null) return;
        List<ModuleCodexData.CodexEntry> all = showWeapon ?
                ModuleCodexData.getWeaponModules() : ModuleCodexData.getWarframeModules();
        int total = all.size(), discovered = 0;
        for (ModuleCodexData.CodexEntry e : all) {
            // ⭐ 使用 discoveryKey 检查发现状态 / Use discoveryKey for discovery check
            if (ModuleDiscoveryPacket.isDiscovered(e.discoveryKey)) discovered++;
        }
        String title = this.getTitle().getString() + " \u00A77(" + discovered + "/" + total + ")";
        g.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 4, 0xFFE0E0E0, true);
    }

    private void drawContent(GuiGraphics g, int mx, int my) {
        if (filtered == null || filtered.isEmpty()) {
            String empty = Component.translatable("kuvalich.codex.empty").getString();
            g.drawString(this.font, empty, (this.width - this.font.width(empty)) / 2,
                    contentY + 30, 0xFF666666, false);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        double s = mc.getWindow().getGuiScale();
        RenderSystem.enableScissor(0, (int) ((this.height - contentY - visibleH) * s),
                (int) (this.width * s), (int) (visibleH * s));

        int drawY = contentY - (int) scrollOffset;
        int lastR = -1, idx = 0, groupY = drawY;

        for (int i = 0; i < filtered.size(); i++) {
            ModuleCodexData.CodexEntry e = filtered.get(i);

            if (e.rarityOrder != lastR) {
                if (lastR != -1) drawY = groupY + groupH(idx) + SECTION_GAP;
                lastR = e.rarityOrder; idx = 0;

                if (inView(drawY, SECTION_H)) {
                    int count = countRarity(filtered, e.rarityOrder);
                    String header = ModuleCodexData.getRarityName(e.rarityOrder)
                            + " \u00A78\u2014 " + count
                            + Component.translatable("kuvalich.codex.count_suffix").getString();
                    g.drawString(this.font, header, contentX, drawY + 4, 0xFFAAAAAA, false);
                    int lineX = contentX + this.font.width(header) + 6;
                    if (lineX < contentX + contentW)
                        g.fill(lineX, drawY + SECTION_H / 2, contentX + contentW, drawY + SECTION_H / 2 + 1, SECTION_LINE_COLOR);
                }
                drawY += SECTION_H;
                groupY = drawY;
            }

            int col = idx % COLUMNS, row = idx / COLUMNS;
            int cx = contentX + col * (CELL_W + GAP);
            int cy = groupY + row * (CELL_H + GAP);

            if (inView(cy, CELL_H)) {
                // ⭐ 使用 discoveryKey 检查发现状态 / Use discoveryKey for discovery check
                boolean found = ModuleDiscoveryPacket.isDiscovered(e.discoveryKey);
                boolean hover = mx >= cx && mx < cx + CELL_W && my >= cy && my < cy + CELL_H
                        && my >= contentY && my < contentY + visibleH;
                drawCell(g, e, cx, cy, found, hover);
                if (hover) { hoveredEntry = e; hoverMX = mx; hoverMY = my; }
            }
            idx++;
        }
        RenderSystem.disableScissor();
    }

    private void drawCell(GuiGraphics g, ModuleCodexData.CodexEntry e,
                          int x, int y, boolean found, boolean hover) {
        int bg = found ? (hover ? CELL_HOVER_FOUND : CELL_BG_FOUND)
                : (hover ? CELL_HOVER_MISS : CELL_BG_MISS);
        g.fill(x, y, x + CELL_W, y + CELL_H, bg);

        // 稀有度薄条指示器
        int baseColor = found ? ModuleCodexData.getRarityColor(e.rarityOrder)
                : ModuleCodexData.getRarityColorDim(e.rarityOrder);
        int fadeColor = (baseColor & 0x00FFFFFF) | (((baseColor >>> 24) / 2) << 24);
        int indX = x + (CELL_W - IND_TOTAL_W) / 2;
        int indY = y + 1;
        g.fill(indX, indY, indX + 1, indY + IND_H, fadeColor);
        g.fill(indX + 1, indY, indX + 1 + IND_SOLID_W, indY + IND_H, baseColor);
        g.fill(indX + 1 + IND_SOLID_W, indY, indX + IND_TOTAL_W, indY + IND_H, fadeColor);

        // 物品图标
        int iconX = x + (CELL_W - 16) / 2;
        int iconY = y + IND_H + 3;
        g.renderItem(e.displayStack, iconX, iconY);

        // 未发现覆盖
        if (!found) {
            g.fill(x, y + IND_H + 2, x + CELL_W, y + CELL_H, OVERLAY_MISS);
            String q = "?";
            g.drawString(this.font, q,
                    x + (CELL_W - this.font.width(q)) / 2,
                    y + IND_H + (CELL_H - IND_H - 8) / 2,
                    0x44FFFFFF, false);
        }

        // 悬停边框
        if (hover) {
            int bc = (found ? ModuleCodexData.getRarityColor(e.rarityOrder) : 0xFFAAAAAA) & 0x60FFFFFF;
            g.fill(x, y, x + CELL_W, y + 1, bc);
            g.fill(x, y + CELL_H - 1, x + CELL_W, y + CELL_H, bc);
            g.fill(x, y, x + 1, y + CELL_H, bc);
            g.fill(x + CELL_W - 1, y, x + CELL_W, y + CELL_H, bc);
        }
    }

    private void drawScrollbar(GuiGraphics g) {
        if (maxScroll() <= 0) return;
        int sbX = contentX + contentW + 6;
        g.fill(sbX, contentY, sbX + SCROLLBAR_W, contentY + visibleH, 0x20FFFFFF);
        double ratio = (double) visibleH / totalContentH;
        int thumbH = Math.max(16, (int) (visibleH * ratio));
        int thumbY = contentY + (int) ((visibleH - thumbH) * (scrollOffset / maxScroll()));
        g.fill(sbX, thumbY, sbX + SCROLLBAR_W, thumbY + thumbH, 0x60FFFFFF);
    }

    private void drawBottom(GuiGraphics g) {
        // 筛选结果
        if (filtered != null && !lastSearch.isEmpty()) {
            String filterHint = Component.translatable("kuvalich.codex.filter_result").getString()
                    + " " + filtered.size()
                    + Component.translatable("kuvalich.codex.count_suffix").getString();
            int fw = this.font.width(filterHint);
            int fx = (this.width - fw) / 2;
            int fy = this.height - 26;
            g.drawString(this.font, filterHint, fx, fy, FILTER_TEXT, false);
        }

        // [TAB] 返回
        StringBuilder sb = new StringBuilder("[TAB] ")
                .append(Component.translatable("kuvalich.codex.close_hint").getString());
        // 创造模式才提示两种点击 —— 生存模式下这两条操作都不存在，说了只会让人困惑
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) {
            sb.append("    ").append(Component.translatable("kuvalich.codex.creative_hint").getString());
        }
        String hint = sb.toString();
        int hw = this.font.width(hint);
        int hx = (this.width - hw) / 2;
        int hy = this.height - 14;
        g.drawString(this.font, hint, hx, hy, HINT_TEXT, false);
    }

    // ==================== 工具 ====================

    private boolean inView(int y, int h) { return y + h > contentY - 10 && y < contentY + visibleH + 10; }

    private int countRarity(List<ModuleCodexData.CodexEntry> list, int order) {
        int c = 0; for (ModuleCodexData.CodexEntry e : list) if (e.rarityOrder == order) c++; return c;
    }
}
