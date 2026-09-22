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

    /** 列数按面板宽度自适应，不再写死 10 列 —— 窄窗口下 10 列会把格子挤出面板。 */
    private static final int COLUMNS_MIN = 4;
    private static final int COLUMNS_MAX = 14;
    private static final int CELL_W = 22;
    private static final int CELL_H = 23;
    private static final int GAP = 3;
    private static final int IND_SOLID_W = 8;
    private static final int IND_H = 2;
    private static final int IND_TOTAL_W = IND_SOLID_W + 2;
    private static final int SECTION_H = 16;
    private static final int SECTION_GAP = 8;

    /** 开场动画时长。比参考实现的 220ms 略短 —— 图鉴是个会被反复开关的界面，动画太长会碍事。 */
    private static final long OPEN_MS = 170L;

    // ==================== 状态 ====================

    private final Screen parentScreen;
    private final boolean showWeapon;

    private EditBox searchBox;
    private double scrollOffset = 0;
    private int totalContentH = 0;
    private int visibleH = 0;
    private int contentX, contentY, contentW;

    /** 面板边界（内容区之外的外框） */
    private int panelX, panelY, panelW, panelH;
    /** 当前列数，由面板宽度算出 */
    private int columns = 10;
    /** 开场时间戳，用于四角生长与淡入 */
    private long openedAt = 0L;
    /** 搜索框外框的位置（控件本身内缩 4px，框由我们画） */
    private int searchFrameX, searchFrameY, searchFrameW;

    private List<ModuleCodexData.CodexEntry> filtered = null;
    private String lastSearch = "";

    private ModuleCodexData.CodexEntry hoveredEntry = null;
    private int hoverMX, hoverMY;

    // ==================== 每帧缓存 ====================
    //
    // ⭐ 下面三组缓存解决的是同一类问题：render() 每帧都在重算一些「其实很少变」的东西。
    //    图鉴一旦开着，哪怕玩家什么都不做，这些计算也在以帧率持续发生。

    /** 标题里的「已发现/总数」。原先每帧对全量条目重算。 */
    private String cachedTitle = null;
    /** 上次算标题时的全局已发现数，当变更戳用 —— 发现记录只增不减，size 变了就说明要重算。 */
    private int titleDiscoveryStamp = -1;
    /** 上次算标题时用的那张表，配置热重载换了表也要重算。 */
    private List<ModuleCodexData.CodexEntry> titleCountedList = null;

    /** 各品质在当前筛选结果里各有多少条，随 filtered 一起在 recalcContentH 里更新。 */
    private final int[] rarityCounts = new int[4];

    /** 量词后缀，原先每个可见分组头每帧都要 new 一次字符串。 */
    private String countSuffix = null;

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
        if (openedAt == 0L) {
            openedAt = net.minecraft.Util.getMillis();
        }

        // 面板：四周留白，内容全部画在这个框里
        panelX = CodexTheme.MARGIN;
        panelY = CodexTheme.MARGIN;
        panelW = Math.max(160, this.width - CodexTheme.MARGIN * 2);
        panelH = Math.max(120, this.height - CodexTheme.MARGIN * 2);

        // 列数按可用宽度自适应（参考 AlbionMastery 卡片网格的做法）：
        // 先扣掉左右内边距和滚动条占的一条，再看能塞下几列。
        int innerW = panelW - CodexTheme.PAD * 2 - CodexTheme.SCROLLBAR_W - 4;
        columns = Math.max(COLUMNS_MIN, Math.min(COLUMNS_MAX, (innerW + GAP) / (CELL_W + GAP)));

        contentW = columns * (CELL_W + GAP) - GAP;
        contentX = panelX + (panelW - CodexTheme.SCROLLBAR_W - 4 - contentW) / 2;
        contentY = panelY + CodexTheme.HEADER_H + 4;
        visibleH = panelH - CodexTheme.HEADER_H - CodexTheme.FOOTER_H - 8;

        // ⭐ resize / 改 GUI 缩放会让 Minecraft 对同一个 Screen 实例重新调用 init()，
        //    这里必须先把旧搜索词存下来：不存的话新建的空 EditBox 会让紧随其后的
        //    onSearchChanged() 把筛选结果重置成全表，玩家已经输入的搜索词无声消失。
        String previousSearch = searchBox != null ? searchBox.getValue() : "";

        int boxW = Math.min(panelW - CodexTheme.PAD * 2, 220);
        int boxX = panelX + (panelW - boxW) / 2;
        int boxY = panelY + 22;
        // 原版 EditBox 自带的白框在这套暗色面板上很突兀，关掉自己画。
        // 注意：bordered=false 时 EditBox 的文字从 getX() 起画（bordered=true 时是 getX()+4），
        // 所以这里把控件本身内缩 4px，外面那圈框由 drawHeader 画在 boxX..boxX+boxW。
        searchBox = new EditBox(this.font, boxX + 4, boxY + 3, boxW - 8, 12, Component.empty());
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(CodexTheme.TEXT);
        searchBox.setHint(Component.translatable("kuvalich.codex.search_hint"));
        searchBox.setResponder(text -> onSearchChanged());
        if (!previousSearch.isEmpty()) {
            // setValue 会触发上面的 responder，onSearchChanged 因此会用新词跑一次
            searchBox.setValue(previousSearch);
        }
        this.addRenderableWidget(searchBox);
        searchFrameX = boxX;
        searchFrameY = boxY;
        searchFrameW = boxW;

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
        // ⭐ 顺手统计各品质条数：这个数 drawContent 画分组头时要用，
        //    原先是每帧调 countRarity 对整个 filtered 做一次 O(n) 扫描重新数一遍，
        //    而本方法在分组时本来就数过同样的东西，算完却丢掉了。
        java.util.Arrays.fill(rarityCounts, 0);
        if (filtered != null) {
            for (ModuleCodexData.CodexEntry e : filtered) {
                if (e.rarityOrder >= 0 && e.rarityOrder < rarityCounts.length) {
                    rarityCounts[e.rarityOrder]++;
                }
            }
        }
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

    private int groupH(int n) { return ((n + columns - 1) / columns) * (CELL_H + GAP) - GAP; }
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
            int sbX = panelX + panelW - CodexTheme.PAD;
            if (mx >= sbX && mx <= sbX + CodexTheme.SCROLLBAR_W && my >= contentY && my < contentY + visibleH) {
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
        float progress = openProgress();

        CodexTheme.scrim(g, this.width, this.height, progress);
        CodexTheme.panel(g, panelX, panelY, panelW, panelH);
        CodexTheme.vignette(g, panelX, panelY, panelW, panelH);

        hoveredEntry = null;
        drawHeader(g);
        drawContent(g, mx, my);
        CodexTheme.scrollbar(g, panelX + panelW - CodexTheme.PAD, contentY, visibleH,
                scrollOffset, maxScroll());
        drawFooter(g);

        // 四角画在最后：它要压住内容的边缘，而不是被内容盖掉
        CodexTheme.corners(g, panelX, panelY, panelW, panelH, CodexTheme.ACCENT, progress);

        super.render(g, mx, my, pt);
        if (hoveredEntry != null) {
            g.renderTooltip(this.font, hoveredEntry.displayStack, hoverMX, hoverMY);
        }
    }

    /**
     * 开场进度 0~1（缓出三次方）。
     *
     * @return 进度
     */
    private float openProgress() {
        if (openedAt == 0L) return 1f;
        long dt = net.minecraft.Util.getMillis() - openedAt;
        if (dt >= OPEN_MS) return 1f;
        return CodexTheme.easeOutCubic((float) dt / OPEN_MS);
    }

    /**
     * 顶栏：标题 + 已发现徽章 + 搜索框外框 + 分隔线
     *
     * <p>「已发现/总数」原先每帧对<b>全量</b>条目重算发现判定（武器页 429 条），
     * 与玩家有没有操作无关。现在只在全局已发现数变化、或 ModuleCodexData 换了表
     * （配置热重载）时重算 —— 发现记录只增不减，所以 Set.size() 是可靠且 O(1) 的变更戳。</p>
     */
    private void drawHeader(GuiGraphics g) {
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + CodexTheme.HEADER_H,
                CodexTheme.PANEL_ALT);

        String title = this.getTitle().getString();
        g.drawString(this.font, title, panelX + CodexTheme.PAD, panelY + 7, CodexTheme.TEXT, false);

        if (filtered != null) {
            List<ModuleCodexData.CodexEntry> all = showWeapon ?
                    ModuleCodexData.getWeaponModules() : ModuleCodexData.getWarframeModules();
            int stamp = ModuleDiscoveryPacket.getDiscoveredCount();
            if (cachedTitle == null || stamp != titleDiscoveryStamp || all != titleCountedList) {
                int discovered = 0;
                for (ModuleCodexData.CodexEntry e : all) {
                    // 传 moduleType 走免分配重载，省掉旧存档兼容分支里的 substring
                    if (ModuleDiscoveryPacket.isDiscovered(e.discoveryKey, e.moduleType)) discovered++;
                }
                titleDiscoveryStamp = stamp;
                titleCountedList = all;
                cachedTitle = discovered + " / " + all.size();
            }
            int bw = this.font.width(cachedTitle) + 8;
            CodexTheme.badge(g, this.font, cachedTitle,
                    panelX + panelW - CodexTheme.PAD - bw, panelY + 5,
                    CodexTheme.ACCENT_SOFT, CodexTheme.TEXT);
        }

        // 搜索框外框：控件本身是无边框的，框画在它外面；聚焦时描边换成强调色
        CodexTheme.border(g, searchFrameX, searchFrameY, searchFrameW, 18,
                searchBox != null && searchBox.isFocused() ? CodexTheme.ACCENT : CodexTheme.BORDER);

        CodexTheme.divider(g, panelX + 1, panelY + CodexTheme.HEADER_H, panelW - 2, CodexTheme.BORDER);
    }

    private void drawContent(GuiGraphics g, int mx, int my) {
        if (filtered == null || filtered.isEmpty()) {
            String empty = Component.translatable("kuvalich.codex.empty").getString();
            g.drawString(this.font, empty, panelX + (panelW - this.font.width(empty)) / 2,
                    contentY + 30, CodexTheme.TEXT_FAINT, false);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        double s = mc.getWindow().getGuiScale();
        // 裁剪收到面板内容区：越界的格子不该画到顶栏/底栏上
        RenderSystem.enableScissor(
                (int) (panelX * s), (int) ((this.height - contentY - visibleH) * s),
                (int) (panelW * s), (int) (visibleH * s));

        int drawY = contentY - (int) scrollOffset;
        int lastR = -1, idx = 0, groupY = drawY;

        for (int i = 0; i < filtered.size(); i++) {
            ModuleCodexData.CodexEntry e = filtered.get(i);

            if (e.rarityOrder != lastR) {
                if (lastR != -1) drawY = groupY + groupH(idx) + SECTION_GAP;
                lastR = e.rarityOrder; idx = 0;

                if (inView(drawY, SECTION_H)) {
                    int count = (e.rarityOrder >= 0 && e.rarityOrder < rarityCounts.length)
                            ? rarityCounts[e.rarityOrder] : 0;
                    if (countSuffix == null) {
                        countSuffix = Component.translatable("kuvalich.codex.count_suffix").getString();
                    }
                    // 装订线：行首一条 2px 竖条，取该品质自己的颜色，
                    // 让「这一整块属于哪个品质」在滚动时一眼可辨（参考 CarianStyle 的 gutter）
                    int rc = ModuleCodexData.getRarityColor(e.rarityOrder);
                    g.fill(contentX, drawY + 2, contentX + 2, drawY + SECTION_H - 2, rc);

                    String header = ModuleCodexData.getRarityName(e.rarityOrder)
                            + " \u00A78" + count + countSuffix;
                    int textX = contentX + 6;
                    g.drawString(this.font, header, textX, drawY + 4, CodexTheme.TEXT_DIM, false);
                    int lineX = textX + this.font.width(header) + 6;
                    if (lineX < contentX + contentW)
                        CodexTheme.divider(g, lineX, drawY + SECTION_H / 2, contentX + contentW - lineX,
                                CodexTheme.withAlpha(CodexTheme.BORDER, 0xA0));
                }
                drawY += SECTION_H;
                groupY = drawY;
            }

            int col = idx % columns, row = idx / columns;
            int cx = contentX + col * (CELL_W + GAP);
            int cy = groupY + row * (CELL_H + GAP);

            if (inView(cy, CELL_H)) {
                // ⭐ 使用 discoveryKey 检查发现状态 / Use discoveryKey for discovery check
                boolean found = ModuleDiscoveryPacket.isDiscovered(e.discoveryKey, e.moduleType);
                boolean hover = mx >= cx && mx < cx + CELL_W && my >= cy && my < cy + CELL_H
                        && my >= contentY && my < contentY + visibleH;
                drawCell(g, e, cx, cy, found, hover);
                if (hover) { hoveredEntry = e; hoverMX = mx; hoverMY = my; }
            }
            idx++;
        }
        RenderSystem.disableScissor();
    }

    /**
     * 画一个模组格子
     *
     * <p>四种状态（已发现/未发现 × 悬停/常态）只靠<b>背景透明度 + 描边色 + 左侧竖条</b>
     * 区分，不做缩放、不换图标、不加阴影 —— 这是从 AlbionMastery 的卡片那里学来的：
     * 在这么小的尺寸上，位移和缩放只会让网格看起来在抖。</p>
     */
    private void drawCell(GuiGraphics g, ModuleCodexData.CodexEntry e,
                          int x, int y, boolean found, boolean hover) {
        int bg = found
                ? (hover ? CodexTheme.PANEL_HOVER : CodexTheme.PANEL_ALT)
                : (hover ? CodexTheme.withAlpha(CodexTheme.PANEL_HOVER, 0x80)
                         : CodexTheme.withAlpha(CodexTheme.PANEL_ALT, 0x60));
        g.fill(x, y, x + CELL_W, y + CELL_H, bg);

        // 顶部稀有度薄条：两端渐隐，中间实色
        int baseColor = found ? ModuleCodexData.getRarityColor(e.rarityOrder)
                : ModuleCodexData.getRarityColorDim(e.rarityOrder);
        int fadeColor = CodexTheme.withAlpha(baseColor, ((baseColor >>> 24) & 0xFF) / 2);
        int indX = x + (CELL_W - IND_TOTAL_W) / 2;
        int indY = y + 1;
        g.fill(indX, indY, indX + 1, indY + IND_H, fadeColor);
        g.fill(indX + 1, indY, indX + 1 + IND_SOLID_W, indY + IND_H, baseColor);
        g.fill(indX + 1 + IND_SOLID_W, indY, indX + IND_TOTAL_W, indY + IND_H, fadeColor);

        // 物品图标
        int iconX = x + (CELL_W - 16) / 2;
        int iconY = y + IND_H + 3;
        g.renderItem(e.displayStack, iconX, iconY);

        // 未发现：压一层暗幕 + 问号
        if (!found) {
            g.fill(x, y + IND_H + 2, x + CELL_W, y + CELL_H, 0xB0100C0E);
            String q = "?";
            g.drawString(this.font, q,
                    x + (CELL_W - this.font.width(q)) / 2,
                    y + IND_H + (CELL_H - IND_H - 8) / 2,
                    CodexTheme.withAlpha(CodexTheme.TEXT_FAINT, 0x90), false);
        }

        // 悬停：描边 + 左侧 2px 竖条（竖条比纯描边更容易在密集网格里被看到）
        if (hover) {
            int edge = found ? baseColor : CodexTheme.TEXT_DIM;
            CodexTheme.border(g, x, y, CELL_W, CELL_H, CodexTheme.withAlpha(edge, 0xC0));
            g.fill(x, y, x + 2, y + CELL_H, edge);
        }
    }


    /**
     * 底栏：筛选结果计数（左）+ 操作提示（右）
     */
    private void drawFooter(GuiGraphics g) {
        int fy = panelY + panelH - CodexTheme.FOOTER_H;
        g.fill(panelX + 1, fy, panelX + panelW - 1, panelY + panelH - 1, CodexTheme.PANEL_ALT);
        CodexTheme.divider(g, panelX + 1, fy, panelW - 2, CodexTheme.BORDER);

        int textY = fy + (CodexTheme.FOOTER_H - this.font.lineHeight) / 2;

        // 左：搜索命中数，没搜索时不占位
        if (filtered != null && !lastSearch.isEmpty()) {
            if (countSuffix == null) {
                countSuffix = Component.translatable("kuvalich.codex.count_suffix").getString();
            }
            String filterHint = Component.translatable("kuvalich.codex.filter_result").getString()
                    + " " + filtered.size() + countSuffix;
            g.drawString(this.font, filterHint, panelX + CodexTheme.PAD, textY,
                    CodexTheme.TEXT_DIM, false);
        }

        // 右：操作提示。创造模式才提示两种点击 —— 生存模式下这两条操作都不存在，说了只会让人困惑
        StringBuilder sb = new StringBuilder("[TAB] ")
                .append(Component.translatable("kuvalich.codex.close_hint").getString());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) {
            sb.append("    ").append(Component.translatable("kuvalich.codex.creative_hint").getString());
        }
        String hint = sb.toString();
        g.drawString(this.font, hint, panelX + panelW - CodexTheme.PAD - this.font.width(hint), textY,
                CodexTheme.TEXT_FAINT, false);
    }

    // ==================== 工具 ====================

    private boolean inView(int y, int h) { return y + h > contentY - 10 && y < contentY + visibleH + 10; }

}
