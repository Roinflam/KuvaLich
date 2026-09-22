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

    /**
     * 列数按可用宽度自适应
     *
     * <p>上限从 14 提到 20：14 列的网格只有 347px 宽，而面板在常见分辨率下能到 600px 以上，
     * 剩下两百多像素全成了左右两侧的死空间。上限仍然要有 —— 1080p@缩放2 能排到 36 列，
     * 那么长的一行扫视起来比滚动还累。</p>
     */
    private static final int COLUMNS_MIN = 4;
    private static final int COLUMNS_MAX = 20;

    /** 内容区左侧留给数据刻度条的宽度 */
    private static final int STRIP_W = 8;
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
    /** 本帧时间，render 开头取一次，供所有绘制方法共用 */
    private long frameMillis = 0L;
    /** 本帧玩家是否创造模式，render 开头取一次 */
    private boolean creativeMode = false;
    /** 本帧鼠标位置，供 drawHeader 做清除按钮的悬停判定 */
    private int lastMouseX, lastMouseY;
    /** 搜索框右侧「清除」按钮的命中区，只在有搜索词时有效 */
    private int clearBtnX, clearBtnY, clearBtnSize;
    /** 底栏提示文案缓存：两种模式各拼一次，不要每帧 new */
    private String footerHintCreative = null;
    private String footerHintSurvival = null;
    /** 筛选结果前缀缓存 */
    private String filterPrefix = null;

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

    /** 各品质的已发现数 / 总数。与标题计数同一次遍历算出来，共用同一套失效条件。 */
    private final int[] rarityFound = new int[4];
    private final int[] rarityTotal = new int[4];

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

        // ⭐ 布局顺序是「先按屏幕算能排几列 → 再把面板收到这几列真正需要的宽度」，
        //    而不是「先铺满屏幕 → 再往里塞格子」。
        //
        //    反过来做就会出现之前那个问题：面板铺满屏幕、列数却卡在上限，
        //    两侧各空出一百多像素的死区。现在面板宽度由内容决定，
        //    多余的宽度还给背景（被遮罩压暗的游戏画面），看起来是一个居中的终端窗口，
        //    而不是一个填不满的横幅。
        panelY = CodexTheme.MARGIN;
        panelH = Math.max(120, this.height - CodexTheme.MARGIN * 2);

        // 面板内部的固定开销：左内边距 + 刻度条 + 右内边距（滚动条画在右内边距里）
        int chromeW = CodexTheme.PAD * 2 + STRIP_W;
        int availW = Math.max(160, this.width - CodexTheme.MARGIN * 2);
        int innerAvail = Math.max(CELL_W, availW - chromeW);

        columns = Math.max(COLUMNS_MIN, Math.min(COLUMNS_MAX, (innerAvail + GAP) / (CELL_W + GAP)));
        contentW = columns * (CELL_W + GAP) - GAP;

        panelW = Math.min(availW, contentW + chromeW);
        panelX = (this.width - panelW) / 2;

        contentX = panelX + CodexTheme.PAD + STRIP_W;
        contentY = panelY + CodexTheme.HEADER_H + 4;
        visibleH = panelH - CodexTheme.HEADER_H - CodexTheme.FOOTER_H - 8;

        // ⭐ resize / 改 GUI 缩放会让 Minecraft 对同一个 Screen 实例重新调用 init()，
        //    这里必须先把旧搜索词存下来：不存的话新建的空 EditBox 会让紧随其后的
        //    onSearchChanged() 把筛选结果重置成全表，玩家已经输入的搜索词无声消失。
        String previousSearch = searchBox != null ? searchBox.getValue() : "";

        // ⭐ 搜索框靠右、与计数徽章同排：原先它单独占第二行且居中，
        //    左边的标题和它之间空出一大段，顶栏看着很散。
        //
        //    徽章宽度按「最宽可能文本」预留而不是按当前文本算 —— 当前文本会随
        //    已发现数增长而变宽（"9 / 429" -> "129 / 429"），按实际宽度定位
        //    会让搜索框在玩家解锁模组时莫名其妙地横向跳一下。
        String titleText = this.getTitle().getString();
        int titleZone = 12 + this.font.width(titleText) + 16;
        int badgeReserve = this.font.width("9999 / 9999") + 12 + 8;

        int boxW = panelW - CodexTheme.PAD * 2 - titleZone - badgeReserve;
        boxW = Math.max(80, Math.min(boxW, 240));
        int boxX = panelX + panelW - CodexTheme.PAD - badgeReserve - boxW;
        int boxY = panelY + 7;
        // 原版 EditBox 自带的白框在这套暗色面板上很突兀，关掉自己画。
        // 注意：bordered=false 时 EditBox 的文字从 getX() 起画（bordered=true 时是 getX()+4），
        // 所以这里把控件本身内缩 4px，外面那圈框由 drawHeader 画在 boxX..boxX+boxW。
        // x 往右让开前导提示符 ">"，文字基线正好落在下划线之上
        searchBox = new EditBox(this.font, boxX + 12, boxY + 3, boxW - 16, 12, Component.empty());
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(CodexTheme.BONE);
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

        // 清除搜索
        if (btn == 0 && clearBtnSize > 0
                && mx >= clearBtnX && mx < clearBtnX + clearBtnSize
                && my >= clearBtnY && my < clearBtnY + clearBtnSize) {
            searchBox.setValue("");
            this.setFocused(searchBox);
            searchBox.setFocused(true);
            return true;
        }

        // 滚动条：命中滑块就原地开始拖，命中轨道空白处就跳过去
        if (btn == 0 && maxScroll() > 0) {
            int sbX = panelX + panelW - CodexTheme.PAD;
            if (mx >= sbX && mx <= sbX + CodexTheme.SCROLLBAR_W && my >= contentY && my < contentY + visibleH) {
                // ⭐ 改造前整条轨道都只是「原地开始拖拽」：点一下轨道空白处再松手，
                //    scrollOffset 一点没变，画面纹丝不动 —— 看起来就像滚动条失灵。
                //    滑块往往比轨道短得多，所以这块「点了没反应」的死区还不小。
                int trackH = visibleH;
                int thumbH = Math.max(CodexTheme.THUMB_MIN_H,
                        (int) (trackH * (trackH / (trackH + maxScroll()))));
                int thumbY = contentY + (int) ((trackH - thumbH) * (scrollOffset / maxScroll()));
                if (my < thumbY || my >= thumbY + thumbH) {
                    // 点轨道：把滑块中心挪到点击处。瞬时跳转，与滚轮的手感一致，不加缓动。
                    double target = (my - contentY - thumbH / 2.0) / Math.max(1, trackH - thumbH);
                    scrollOffset = clamp(target * maxScroll());
                }
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
        frameMillis = net.minecraft.Util.getMillis();
        Minecraft mc0 = Minecraft.getInstance();
        creativeMode = mc0.player != null && mc0.player.isCreative();

        hoveredEntry = null;
        lastMouseX = mx;
        lastMouseY = my;

        // ⭐ 整块绘制包在 drawManaged 里 —— 这是这个界面最重要的一处性能处理。
        //
        //    GuiGraphics.fill 最终那个重载会调 flushIfUnmanaged()，也就是说
        //    **每一次 fill 默认都会把批次 flush 掉、变成一个独立的 draw call**。
        //    而这个界面是全自绘的：面板斜切、全息扫描线、刻度、微粒，再加上
        //    每个可见格子十来次 fill（20 列 × 十几行 = 两三百个格子），
        //    一帧下来是三千多次 fill —— 不包起来就是三千多个 draw call。
        //
        //    managed 期间 fill 只往缓冲里堆顶点，退出时一次性提交。
        //    drawString 内部同样是 flushIfUnmanaged，在这里也一并被批住了；
        //    renderItem 本身不做任何 flush，放在里面是安全的。
        //
        //    唯独 tooltip 不能放进来：renderTooltipInternal 自己用了 drawManaged，
        //    嵌套会让内层退出时把 managed 置回 false，外层剩下的绘制又退化成逐次 flush。
        g.drawManaged(() -> {
            g.fill(0, 0, this.width, this.height, CodexTheme.fade(CodexTheme.VOID, progress));

            // 主框体：斜切板材 -> 全息表面 -> 环境微粒 -> 内容 -> 发光轮廓。
            // 顺序要紧：微粒在板材之上、格子之下；轮廓压在最外层。
            CodexTheme.chamferFill(g, panelX, panelY, panelW, panelH, CodexTheme.CHAMFER, CodexTheme.PLATE);
            CodexTheme.holoSurface(g, panelX + 2, panelY + 2, panelW - 4, panelH - 4, 12);
            CodexTheme.motes(g, panelX + 2, contentY, panelW - 4, Math.max(0, visibleH), frameMillis, progress);

            drawHeader(g);
            drawContent(g, mx, my);
            CodexTheme.scrollbar(g, panelX + panelW - CodexTheme.PAD, contentY, visibleH,
                    scrollOffset, maxScroll(), dragging || overScrollbar(mx, my));
            drawFooter(g);

            // 左缘一条数据刻度，把留白仪器化
            CodexTheme.tickStrip(g, panelX + CodexTheme.PAD, contentY, visibleH, CodexTheme.TECH);

            CodexTheme.chamferGlow(g, panelX, panelY, panelW, panelH, CodexTheme.CHAMFER,
                    CodexTheme.withAlpha(CodexTheme.TECH, 0xB0), progress);
        });

        super.render(g, mx, my, pt);
        if (hoveredEntry != null) {
            g.renderTooltip(this.font, hoveredEntry.displayStack, hoverMX, hoverMY);
        }
    }

    /**
     * 鼠标是不是压在滚动条那一列上
     *
     * @param mx 鼠标 x
     * @param my 鼠标 y
     * @return 是否命中
     */
    private boolean overScrollbar(int mx, int my) {
        if (maxScroll() <= 0) return false;
        int sbX = panelX + panelW - CodexTheme.PAD;
        return mx >= sbX - 2 && mx <= sbX + CodexTheme.SCROLLBAR_W + 2
                && my >= contentY && my < contentY + visibleH;
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
        int hx = panelX + 1, hw = panelW - 2;
        g.fill(hx, panelY + 1, hx + hw, panelY + CodexTheme.HEADER_H, CodexTheme.PLATE_HI);
        CodexTheme.holoSurface(g, hx, panelY + 1, hw, CodexTheme.HEADER_H - 1, 10);

        // 标题：左边一个赤毒色斜角标记，下面压一条短细线
        // 标题与搜索框在同一行上垂直居中：搜索框高 18、顶在 panelY+7，中心在 +16；
        // 标题文字高 9、顶在 panelY+11，中心也是 +15.5，两者对齐。
        int tx = panelX + CodexTheme.PAD;
        CodexTheme.cornerTab(g, tx, panelY + 11, 8, CodexTheme.KUVA);
        String title = this.getTitle().getString();
        int titleX = tx + 12;
        // 带阴影：细体字压在扫描线和网格上，不带阴影会糊进背景
        g.drawString(this.font, title, titleX, panelY + 11, CodexTheme.BONE, true);
        g.fill(titleX, panelY + 22, titleX + this.font.width(title), panelY + 23,
                CodexTheme.withAlpha(CodexTheme.KUVA, 0x90));

        // 已发现徽章（右上）。计数只在全局已发现数变化、或 ModuleCodexData 换了表
        // （配置热重载）时重算 —— 发现记录只增不减，Set.size() 是可靠且 O(1) 的变更戳。
        if (filtered != null) {
            List<ModuleCodexData.CodexEntry> all = showWeapon ?
                    ModuleCodexData.getWeaponModules() : ModuleCodexData.getWarframeModules();
            int stamp = ModuleDiscoveryPacket.getDiscoveredCount();
            if (cachedTitle == null || stamp != titleDiscoveryStamp || all != titleCountedList) {
                // ⭐ 一次遍历同时算出总进度与各品质进度 —— 分组标题要用后者，
                //    没必要为它再扫一遍 429 条。失效条件与总进度完全相同。
                int discovered = 0;
                java.util.Arrays.fill(rarityFound, 0);
                java.util.Arrays.fill(rarityTotal, 0);
                for (ModuleCodexData.CodexEntry e : all) {
                    // 传 moduleType 走免分配重载，省掉旧存档兼容分支里的 substring
                    boolean found = ModuleDiscoveryPacket.isDiscovered(e.discoveryKey, e.moduleType);
                    if (found) discovered++;
                    if (e.rarityOrder >= 0 && e.rarityOrder < rarityTotal.length) {
                        rarityTotal[e.rarityOrder]++;
                        if (found) rarityFound[e.rarityOrder]++;
                    }
                }
                titleDiscoveryStamp = stamp;
                titleCountedList = all;
                cachedTitle = discovered + " / " + all.size();
            }
            int bw = this.font.width(cachedTitle) + 12;
            // 徽章右对齐到面板内边距；它比搜索框矮，往下挪 2px 与之居中对齐
            CodexTheme.badge(g, this.font, cachedTitle,
                    panelX + panelW - CodexTheme.PAD - bw, panelY + 9,
                    CodexTheme.withAlpha(CodexTheme.TECH, 0x22), CodexTheme.EMBER);
        }

        // ⭐ 搜索框从「一圈斜切描边」改成「前导提示符 + 底部下划线」。
        //    整圈描边在这套以细线组织信息的界面里显得过重，而且框体本身比文字高一截，
        //    文字浮在里面上不着天下不着地 —— 你看到的「文字歪了」就是这个。
        //    下划线式的输入行把文字锚在一条基线上，也更像终端。
        boolean focused = searchBox != null && searchBox.isFocused();
        int lineColor = focused ? CodexTheme.TECH : CodexTheme.EDGE;
        int underY = searchFrameY + 15;
        g.fill(searchFrameX, underY, searchFrameX + searchFrameW, underY + 1, lineColor);
        if (focused) {
            // 聚焦时下划线向外溢一层辉光，并在两端各加一个短竖标
            g.fill(searchFrameX, underY + 1, searchFrameX + searchFrameW, underY + 2,
                    CodexTheme.withAlpha(lineColor, 0x40));
        }
        g.fill(searchFrameX, underY - 4, searchFrameX + 1, underY + 1, lineColor);
        g.fill(searchFrameX + searchFrameW - 1, underY - 4, searchFrameX + searchFrameW, underY + 1, lineColor);
        // 前导提示符
        g.drawString(this.font, ">", searchFrameX + 3, searchFrameY + 4,
                focused ? CodexTheme.TECH : CodexTheme.FAINT, true);

        // ⭐ 清除按钮：只在有搜索词时出现。
        //    改造前唯一的清空方式是按 ESC，而界面上没有任何地方提到这件事 ——
        //    而且大多数人的第一反应是 ESC 会直接关掉整个图鉴（搜索框为空时它确实会），
        //    所以根本不敢试。lang 里 kuvalich.codex.clear_search 早就写好了，只是没人接。
        clearBtnSize = 0;
        if (searchBox != null && !searchBox.getValue().isEmpty()) {
            clearBtnSize = 9;
            clearBtnX = searchFrameX + searchFrameW - clearBtnSize - 1;
            clearBtnY = searchFrameY + 3;
            boolean over = lastMouseX >= clearBtnX && lastMouseX < clearBtnX + clearBtnSize
                    && lastMouseY >= clearBtnY && lastMouseY < clearBtnY + clearBtnSize;
            int c = over ? CodexTheme.KUVA : CodexTheme.FAINT;
            // 一个叉：两条对角线，逐像素点出来（9px 见方，成本可以忽略）
            for (int i = 1; i < clearBtnSize - 1; i++) {
                g.fill(clearBtnX + i, clearBtnY + i, clearBtnX + i + 1, clearBtnY + i + 1, c);
                g.fill(clearBtnX + clearBtnSize - 1 - i, clearBtnY + i,
                        clearBtnX + clearBtnSize - i, clearBtnY + i + 1, c);
            }
        }

        CodexTheme.dataLine(g, hx, panelY + CodexTheme.HEADER_H, hw, CodexTheme.TECH, frameMillis);
    }

    private void drawContent(GuiGraphics g, int mx, int my) {
        if (filtered == null || filtered.isEmpty()) {
            String empty = Component.translatable("kuvalich.codex.empty").getString();
            g.drawString(this.font, empty, panelX + (panelW - this.font.width(empty)) / 2,
                    contentY + 30, CodexTheme.FAINT, false);
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
                    // 品质角标 + 标题 + 延伸到右边的细线
                    int rc = ModuleCodexData.getRarityColor(e.rarityOrder);
                    CodexTheme.cornerTab(g, contentX, drawY + 2, 9, rc);

                    // ⭐ 分组标题显示的是**收集进度**而不是条目数：条目数在底栏的搜索命中里
                    //    已经有了，而「这一档收集了多少」才是玩家翻图鉴时真正想知道的。
                    //    数字带阴影 —— 细体数字压在扫描线上很难读。
                    int ro = e.rarityOrder;
                    boolean roOk = ro >= 0 && ro < rarityTotal.length;
                    int gotN = roOk ? rarityFound[ro] : 0;
                    int totN = roOk ? rarityTotal[ro] : count;

                    // 名字用该品质自己的颜色画，与左边的角标一致。
                    // 改造前名字里内嵌 § 码，和角标的颜色对不上（青铜写成黄色、白银写成蓝色）。
                    String name = ModuleCodexData.getRarityName(ro);
                    int textX = contentX + 13;
                    g.drawString(this.font, name, textX, drawY + 4, rc, true);
                    int progX = textX + this.font.width(name) + 8;
                    String prog = gotN + " / " + totN;
                    int progColor = (totN > 0 && gotN >= totN) ? CodexTheme.EMBER : CodexTheme.ASH;
                    g.drawString(this.font, prog, progX, drawY + 4, progColor, true);

                    // 该档位的进度条：细细一条，长度跟着完成度走
                    int barX = progX + this.font.width(prog) + 8;
                    int barW = 48;
                    if (barX + barW < contentX + contentW) {
                        int barY = drawY + SECTION_H / 2;
                        g.fill(barX, barY, barX + barW, barY + 1,
                                CodexTheme.withAlpha(CodexTheme.EDGE, 0xC0));
                        int fillW = totN > 0 ? (int) ((long) barW * gotN / totN) : 0;
                        if (fillW > 0) {
                            g.fill(barX, barY - 1, barX + fillW, barY + 2, rc);
                        }
                        barX += barW;
                    }

                    int lineX = barX + 8;
                    if (lineX < contentX + contentW) {
                        g.fill(lineX, drawY + SECTION_H / 2, contentX + contentW,
                                drawY + SECTION_H / 2 + 1, CodexTheme.withAlpha(CodexTheme.EDGE, 0xC0));
                    }
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
     * <p>格子是「嵌进面板的凹槽」：底色比面板更暗，左上角一个斜角标记表示品质。
     * 未发现的不画问号而画几段加密噪线 —— 问号看起来像出错，噪线看起来像「还没解密」。</p>
     *
     * <p>悬停不做缩放也不换图标，只加一圈瞄准括号和一层极淡的底色：在这么小的尺寸上
     * 位移和缩放只会让整片网格看起来在抖。</p>
     */
    private void drawCell(GuiGraphics g, ModuleCodexData.CodexEntry e,
                          int x, int y, boolean found, boolean hover) {
        // 反馈表绝大多数帧是空的，空表时连 key 都不去查
        CodexFeedback.Entry fb = CodexFeedback.isEmpty()
                ? null : CodexFeedback.peek(e.discoveryKey, frameMillis);
        float fp = fb != null ? fb.progress(frameMillis) : 0f;

        // 「装不上」用横向抖动表达：衰减的正弦，三个来回之后归零。
        // 抖动必须在所有绘制之前改掉 x，否则格子的各层会互相错开。
        if (fb != null && fb.kind == CodexFeedback.Kind.REJECTED) {
            x += (int) (Math.sin(fp * Math.PI * 6.0) * 3.0 * (1f - fp));
        }

        int rarity = found ? ModuleCodexData.getRarityColor(e.rarityOrder)
                : ModuleCodexData.getRarityColorDim(e.rarityOrder);

        // 凹槽底
        g.fill(x, y, x + CELL_W, y + CELL_H,
                found ? CodexTheme.PLATE_DEEP : CodexTheme.withAlpha(CodexTheme.PLATE_DEEP, 0xB0));
        // 结构边只画左、下两条，制造「嵌进去」的方向感
        g.fill(x, y, x + 1, y + CELL_H, CodexTheme.withAlpha(CodexTheme.EDGE, found ? 0xFF : 0x80));
        g.fill(x, y + CELL_H - 1, x + CELL_W, y + CELL_H,
                CodexTheme.withAlpha(CodexTheme.EDGE, found ? 0xFF : 0x80));

        // 品质角标。5px 太小，三档品质色（青铜/白银/黄金）在网格里几乎分不出来；
        // 放到 8px 之后一眼可辨。它画在图标之前，被图标压住的只是左上角几个像素。
        CodexTheme.cornerTab(g, x + 1, y + 1, 8, rarity);

        // ⭐ 悬停底色必须画在图标**之前**：同样的 z 序原因，画在 renderItem 之后
        //    会落到图标背后，只能把格子边缘染上色，看起来像没生效。
        if (hover) {
            g.fill(x, y, x + CELL_W, y + CELL_H, CodexTheme.withAlpha(
                    found ? CodexTheme.KUVA : CodexTheme.TECH, creativeMode ? 0x22 : 0x12));
        }

        // 反馈的整格闪光同理，也必须在图标之前
        if (fb != null) {
            int flash = feedbackColor(fb.kind);
            int a = (int) (0x80 * (1f - fp) * (1f - fp));
            if (a > 2) {
                g.fill(x, y, x + CELL_W, y + CELL_H, CodexTheme.withAlpha(flash, a));
            }
        }

        if (found) {
            int iconX = x + (CELL_W - 16) / 2;
            int iconY = y + (CELL_H - 16) / 2 + 1;
            g.renderItem(e.displayStack, iconX, iconY);
        } else {
            // ⭐ 未发现的**根本不画图标**，而不是画完再盖一层遮罩。
            //
            //    盖遮罩这条路走不通：GuiGraphics.renderItem 把物品渲染在 z=150，
            //    而随后的 fill 落在 z=0，遮罩会跑到图标后面去 —— 表现就是
            //    「明明只发现了 15/429，每个格子的图标却都看得清清楚楚」，
            //    已发现/未发现的区分完全失效。（这个 z 序问题在改造前就存在，
            //    老代码的 OVERLAY_MISS 同样是在 renderItem 之后画的。）
            //
            //    加密噪线的位置由 discoveryKey 的哈希决定，所以同一个模组每帧长得一样、
            //    不会闪，不同模组之间又各不相同。
            int hash = e.discoveryKey.hashCode();
            for (int i = 0; i < 4; i++) {
                int bits = (hash >>> (i * 7)) & 0x7F;
                int ly = y + 5 + i * 4;
                if (ly >= y + CELL_H - 3) break;
                int lx = x + 3 + (bits % 6);
                int lw = 4 + (bits % 9);
                if (lx + lw > x + CELL_W - 3) lw = x + CELL_W - 3 - lx;
                if (lw <= 0) continue;
                g.fill(lx, ly, lx + lw, ly + 1, CodexTheme.withAlpha(CodexTheme.TECH, 0x55));
            }
        }

        if (hover) {
            // ⭐ 瞄准括号只在创造模式画。
            //
            //    括号是「这里能操作」的强视觉承诺，而点击拿取/装配只在创造模式生效。
            //    生存玩家看到括号亮起、点下去却毫无反应（没有闪光、没有声音、没有提示），
            //    和「压根没点中」是同一种观感，会反复点并怀疑功能坏了。
            //    生存模式只保留一圈很弱的描边，表示「这格可以看 tooltip」。
            if (creativeMode) {
                CodexTheme.brackets(g, x, y, CELL_W, CELL_H, 5,
                        found ? CodexTheme.KUVA : CodexTheme.TECH);
            } else {
                CodexTheme.border(g, x, y, CELL_W, CELL_H,
                        CodexTheme.withAlpha(CodexTheme.EDGE, 0xFF));
            }
        }

        if (fb != null) {
            drawFeedback(g, fb, fp, x, y);
        }
    }

    /**
     * 反馈动画的颜色
     *
     * @param kind 反馈类型
     * @return 颜色
     */
    private static int feedbackColor(CodexFeedback.Kind kind) {
        switch (kind) {
            case INSTALLED: return CodexTheme.TECH;
            case REJECTED: return CodexTheme.KUVA;
            case TAKEN: return CodexTheme.BONE;
            case DROPPED: return CodexTheme.EMBER;
            default: return CodexTheme.EMBER;
        }
    }

    /**
     * 画一次性反馈动画的外层部分（向外扩散的光环 / 解锁扫描线）
     *
     * <p>这部分画在图标之后没关系：光环在格子边框之外，扫描线是一条贯穿的横线，
     * 都不依赖盖住图标。真正需要盖住内容的整格闪光已经在图标之前画过了。</p>
     *
     * @param g  画布
     * @param fb 反馈
     * @param fp 进度 0~1
     * @param x  格子左（已含抖动偏移）
     * @param y  格子上
     */
    private void drawFeedback(GuiGraphics g, CodexFeedback.Entry fb, float fp, int x, int y) {
        int color = feedbackColor(fb.kind);

        // 向外扩散的括号环：越扩越淡，像一次脉冲
        int grow = (int) (fp * 7f);
        int ringAlpha = (int) (0xE0 * (1f - fp));
        if (ringAlpha > 4) {
            CodexTheme.brackets(g, x - grow, y - grow,
                    CELL_W + grow * 2, CELL_H + grow * 2, 4,
                    CodexTheme.withAlpha(color, ringAlpha));
        }

        if (fb.kind == CodexFeedback.Kind.UNLOCKED) {
            // 解锁：一条自上而下扫过格子的亮线，像刚刚被解密
            int sy = y + (int) (fp * CELL_H);
            if (sy >= y && sy < y + CELL_H) {
                g.fill(x, sy, x + CELL_W, sy + 1,
                        CodexTheme.withAlpha(color, (int) (0xE0 * (1f - fp))));
                g.fill(x, sy + 1, x + CELL_W, sy + 2,
                        CodexTheme.withAlpha(color, (int) (0x60 * (1f - fp))));
            }
        } else if (fb.kind == CodexFeedback.Kind.DROPPED) {
            // 背包满、掉在脚下：一道向**下**离开格子的短线，
            // 和「拿到手了」的向外脉冲区分开 —— 东西没进背包，是掉出去了
            int sy = y + CELL_H - 2 + (int) (fp * 8f);
            g.fill(x + CELL_W / 2 - 3, sy, x + CELL_W / 2 + 3, sy + 1,
                    CodexTheme.withAlpha(color, (int) (0xE0 * (1f - fp))));
            g.fill(x + CELL_W / 2 - 1, sy + 1, x + CELL_W / 2 + 1, sy + 3,
                    CodexTheme.withAlpha(color, (int) (0xA0 * (1f - fp))));
        }
    }


    /**
     * 底栏：筛选命中数（左）+ 操作提示（右）
     */
    private void drawFooter(GuiGraphics g) {
        int fy = panelY + panelH - CodexTheme.FOOTER_H;
        g.fill(panelX + 1, fy, panelX + panelW - 1, panelY + panelH - 1, CodexTheme.PLATE_HI);
        CodexTheme.holoSurface(g, panelX + 1, fy, panelW - 2, CodexTheme.FOOTER_H - 1, 10);
        g.fill(panelX + 1, fy, panelX + panelW - 1, fy + 1, CodexTheme.withAlpha(CodexTheme.EDGE, 0xE0));

        int textY = fy + (CodexTheme.FOOTER_H - this.font.lineHeight) / 2;

        if (filtered != null && !lastSearch.isEmpty()) {
            if (countSuffix == null) {
                countSuffix = Component.translatable("kuvalich.codex.count_suffix").getString();
            }
            if (filterPrefix == null) {
                filterPrefix = Component.translatable("kuvalich.codex.filter_result").getString() + " ";
            }
            // 只有数字每帧会变，前后缀都是缓存的
            String filterHint = filterPrefix + filtered.size() + countSuffix;
            g.drawString(this.font, filterHint, panelX + CodexTheme.PAD, textY, CodexTheme.EMBER, true);
        }

        // ⭐ 两种模式的提示各拼一次缓存起来。原先每帧都 new StringBuilder + 两次
        //    Component.getString()，而这两句话在一次界面生命周期里根本不会变。
        //
        //    生存模式也给一行说明：改造前这里是空的，生存玩家既看不到操作提示、
        //    点击又毫无反应，没有任何渠道弄清楚为什么。
        if (footerHintCreative == null) {
            String close = "[TAB] " + Component.translatable("kuvalich.codex.close_hint").getString();
            footerHintCreative = close + "    "
                    + Component.translatable("kuvalich.codex.creative_hint").getString();
            footerHintSurvival = close + "    "
                    + Component.translatable("kuvalich.codex.survival_hint").getString();
        }
        String hint = creativeMode ? footerHintCreative : footerHintSurvival;
        g.drawString(this.font, hint, panelX + panelW - CodexTheme.PAD - this.font.width(hint), textY,
                CodexTheme.FAINT, false);
    }

    // ==================== 工具 ====================

    private boolean inView(int y, int h) { return y + h > contentY - 10 && y < contentY + visibleH + 10; }

}
