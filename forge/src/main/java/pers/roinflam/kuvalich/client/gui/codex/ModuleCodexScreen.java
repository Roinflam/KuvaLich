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
import pers.roinflam.kuvalich.network.message.CodexGiveItemPacket;
import pers.roinflam.kuvalich.network.message.ModuleDiscoveryPacket;

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

        int boxW = Math.min(contentW, 200);
        int boxX = (this.width - boxW) / 2;
        int boxY = TITLE_H + 2;
        searchBox = new EditBox(this.font, boxX, boxY, boxW, 14, Component.empty());
        searchBox.setMaxLength(50);
        searchBox.setBordered(true);
        searchBox.setHint(Component.translatable("kuvalich.codex.search_hint"));
        searchBox.setResponder(text -> onSearchChanged());
        this.addRenderableWidget(searchBox);

        onSearchChanged();
    }

    private void onSearchChanged() {
        String query = searchBox.getValue().trim().toLowerCase();
        if (query.equals(lastSearch) && filtered != null) return;
        lastSearch = query;

        List<ModuleCodexData.CodexEntry> all = showWeapon ?
                ModuleCodexData.getWeaponModules() : ModuleCodexData.getWarframeModules();
        if (query.isEmpty()) {
            filtered = all;
        } else {
            filtered = new ArrayList<>();
            for (ModuleCodexData.CodexEntry e : all) {
                if (e.searchableText.contains(query)) filtered.add(e);
            }
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
        // ⭐ 创造模式：左键点击模组 → 发给背包（携带rarityOrder）
        if (btn == 0 && hoveredEntry != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.isCreative()) {
                NetworkRegistryHandler.getChannel().sendToServer(
                        new CodexGiveItemPacket(hoveredEntry.moduleType, showWeapon,
                                hoveredEntry.rarityOrder));
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

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging && maxScroll() > 0) {
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
        String hint = "[TAB] " + Component.translatable("kuvalich.codex.close_hint").getString();
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
