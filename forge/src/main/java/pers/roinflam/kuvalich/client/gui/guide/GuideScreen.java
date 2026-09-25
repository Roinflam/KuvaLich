package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.client.gui.guide.GuideMarkup.Link;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.CatView;
import pers.roinflam.kuvalich.client.gui.guide.GuideView.EntryView;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 赤毒玄骸 冒险指南
 *
 * <p>两页：<b>首页</b>是分类大格子（照御炎指南的菜单），<b>阅读页</b>是「左目录 + 右正文」
 * （照卡利亚式附魔百科）。两页共用同一块面板和顶栏 —— 翻页时顶栏、搜索框一动不动，
 * 只有正文区在换，玩家不会觉得跳到了另一个界面。</p>
 *
 * <h3>视觉</h3>
 * <p>全部沿用模组图鉴的军械库全息终端语言（见 {@link GuideTheme}）：冷色近黑的斜切板材、
 * 科技青的结构线与扫描线、全屏唯一的暖色焦点赤毒猩红（当前条目、悬停中的格子 / 链接）。</p>
 *
 * <h3>动效：军械库终端开机（分镜与时间轴见 {@link GuideIntro}）</h3>
 * <ul>
 *   <li>开场（约 640ms）：屏幕正中一条科技青扫描线向两侧展开 → 面板以它为轴上下展开、斜切外框描线、
 *       四角括号卡入 → 扫描光束自上而下扫出内容（带全息闪烁与色偏干扰条）→ 顶栏书名乱码解密 →
 *       首页格子跟着光束逐个全息成形（描边先到、内容后到）。开场期间点一下 / 按任意键（Esc 除外）
 *       直接跳到终态，<b>这次输入照常处理</b>，不会被吞掉；</li>
 *   <li>收场（220ms）：内容被板材盖住 → 面板收成一条线 → 线向中心缩成一点消失，
 *       {@link #onClose()} 推迟到动画走完；</li>
 *   <li>首页 ↔ 阅读页：正文区 140ms 横向擦除；</li>
 *   <li>面板全程<b>不平移</b>，只裁剪、叠光效 —— 任何时刻命中区都和画出来的位置一致；</li>
 *   <li>常驻：首页面板里缓缓上浮的数据微粒、暗角、顶栏分隔线上一道往复的扫描亮点、四角一层淡青括号 —— 克制。</li>
 * </ul>
 *
 * <h3>数值来源</h3>
 * <p>正文里的 {@code {cfg:…}} 取自 {@link GuideClientConfig#snapshot()}：打开时取一次，
 * 服务端重发配置（{@link GuideClientConfig#revision()} 变了）、F3+T 重载资源、切换语言时整份重建并重新排版，
 * 保持当前条目与滚动位置不变。数值来源不在界面上显示：同步是后台的事，单人游戏里也无所谓「服务器」。</p>
 *
 * <h3>记忆</h3>
 * <p>上次停在哪一页、哪一篇、滚到哪、前进后退历史，都记在静态字段里 —— 关掉再开回到原处，
 * 跨存档也保留（找不到那一篇了就回首页）。</p>
 *
 * <p>不是暂停界面：单人游戏时世界照常运行（和附魔百科一致），边看书边等熔炉不耽误事。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public class GuideScreen extends Screen {

    private enum Page { HOME, READ }

    /** 一个历史位置：entryKey 为 null 表示首页 */
    private record Loc(@Nullable String entryKey, float scroll) {}

    // ==================== 跨开关保留的状态 ====================

    private static Page lastPage = Page.HOME;
    @Nullable
    private static String lastEntryKey;
    private static float lastScroll;
    private static final Deque<Loc> BACK = new ArrayDeque<>();
    private static final Deque<Loc> FORWARD = new ArrayDeque<>();
    private static final int HISTORY_MAX = 64;

    // ==================== 部件 ====================

    private GuideView view;
    private GuideSearch search;
    private GuideTextLayout text;
    private final GuideMedia media = new GuideMedia();
    private GuideReader reader;
    private final GuideSidebar sidebar = new GuideSidebar();
    private final GuideHome home = new GuideHome();
    private final GuideRender rc = new GuideRender();
    /** 开场 / 收场 / 翻页动效 */
    private final GuideIntro fx = new GuideIntro();
    private EditBox searchBox;

    // ==================== 状态 ====================

    private Page page = Page.HOME;
    @Nullable
    private EntryView current;
    private List<GuideSearch.Hit> hits = List.of();
    /** 键盘选中的搜索结果 */
    private int cursor = -1;
    private boolean restored;
    /** 见 {@link #repositionElements()}：重建控件前输入框是否聚焦着 */
    private boolean keepSearchFocus;

    private long openedAt;
    private long closingAt;
    private long lastFrame;
    /** 开场时间轴已经对齐到第一帧（或已被跳过），之后不再重设 openedAt */
    private boolean introAnchored;
    /** 上一帧画的是哪一页：和当前页不同就说明换页了，播一次横向擦除 */
    @Nullable
    private Page shownPage;
    /** 擦除中：旧页是哪一页、从什么时候开始擦（0 = 没在擦） */
    @Nullable
    private Page wipeFrom;
    private long wipeAt;
    private final float[] navHover = new float[3];

    // ==================== 几何 ====================

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int bodyY;
    private int bodyH;
    private int searchX;
    private int searchY;
    private int searchW;
    private final int[] navX = new int[3];
    private int navY;
    private static final int NAV_W = 16;
    private static final int NAV_H = 14;
    /** 顶栏左侧文字区的右边界（在它之后是导航按钮） */
    private int headerTextRight;
    private String titleText = "";
    private String subtitleText = "";
    private String searchHint = "";

    // 底栏文案缓存：只在页 / 条目 / 搜索模式 / 视图变化时重拼，不每帧 String.format
    private String footerStatus = "";
    private int footerStatusW;
    private String footerHint = "";
    @Nullable
    private Page footerPage;
    @Nullable
    private EntryView footerEntry;
    private boolean footerSearch;
    @Nullable
    private GuideView footerView;
    private int clearX;
    private int clearY;
    private boolean clearVisible;

    public GuideScreen() {
        super(Component.translatable("kuvalich.guide.title"));
    }

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        if (openedAt == 0L) {
            openedAt = Util.getMillis();
        }
        if (text == null) {
            text = new GuideTextLayout(this.font);
            reader = new GuideReader(text, media);
        }
        if (view == null) {
            view = GuideView.build();
            search = new GuideSearch(view, media);
        }
        computeGeometry();

        // resize / 切全屏会对同一个实例重新 init：先存下搜索词和光标，新建的空输入框不能把它清掉
        boolean reinit = searchBox != null;
        String previous = reinit ? searchBox.getValue() : "";
        int previousCursor = reinit ? searchBox.getCursorPosition() : 0;
        float sideScroll = sidebar.target;
        searchBox = new EditBox(this.font, searchX + 14, searchY + 4, searchW - 14 - 12, 10, Component.translatable("kuvalich.guide.search_hint"));
        searchBox.setBordered(false);
        searchBox.setMaxLength(48);
        searchBox.setTextColor(GuideTheme.BONE);
        if (!previous.isEmpty()) {
            // 先填词、再挂 responder：hits 和键盘选中的那一条都还是这次搜索的，不必重搜 ——
            // 重搜会把选中重置回第一条、把结果列表滚回顶部。setCursorPosition / setHighlightPos 不触发 responder
            searchBox.setValue(previous);
            searchBox.setCursorPosition(previousCursor);
            searchBox.setHighlightPos(previousCursor);
        }
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);
        if (keepSearchFocus) {
            focusSearch();
        }

        if (!restored) {
            restored = true;
            restore();
        }
        relayout();
        if (reinit && sidebar.isSearchMode()) {
            // relayout 里的 showResults 会把结果列表滚回顶部：尺寸变了不该丢掉读者滚到的位置。
            // 目录模式不用管：showTree 本来就保留滚动，还会把当前篇 reveal 进新的可视区
            sidebar.restoreScroll(sideScroll);
        }
    }

    /**
     * 改窗口大小 / 切全屏 / 从别的界面回来时，原版走 rebuildWidgets：先 clearWidgets()、再 clearFocus()、
     * 最后才调 {@link #init()} —— 到 init 里旧输入框已经被取消聚焦，在那里读 isFocused() 永远是 false。
     * 只能在这之前记下。丢了焦点的后果不只是光标不闪：接着按退格会走「后退」而不是删字，
     * 从首页开始的搜索会直接被退回首页、搜索词清空。
     */
    @Override
    protected void repositionElements() {
        keepSearchFocus = searchBox != null && searchBox.isFocused();
        try {
            super.repositionElements();
        } finally {
            keepSearchFocus = false;
        }
    }

    /**
     * 按屏幕尺寸算面板与顶栏控件的位置
     */
    private void computeGeometry() {
        int margin = this.width < 560 ? GuideTheme.MARGIN_SMALL : GuideTheme.MARGIN;
        int marginY = this.height < 320 ? 4 : 10;
        panelW = Math.min(this.width - margin * 2, GuideTheme.PANEL_MAX_W);
        panelH = this.height - marginY * 2;
        panelX = (this.width - panelW) / 2;
        panelY = marginY;
        bodyY = panelY + GuideTheme.HEADER_H + 1;
        bodyH = panelH - GuideTheme.HEADER_H - GuideTheme.FOOTER_H - 1;

        searchW = Math.max(96, Math.min(170, panelW * 26 / 100));
        searchX = panelX + panelW - GuideTheme.PAD - searchW;
        searchY = panelY + 9;
        navY = panelY + 10;
        int nx = searchX - 10 - (NAV_W * 3 + 3 * 2);
        for (int i = 0; i < 3; i++) {
            navX[i] = nx + i * (NAV_W + 3);
        }
        headerTextRight = nx - 8;

        titleText = I18n.get("kuvalich.guide.title");
        int textLeft = panelX + GuideTheme.PAD + 22 + 7;
        int room = headerTextRight - textLeft;
        titleText = GuideTheme.ellipsize(this.font, titleText, room);
        subtitleText = GuideTheme.ellipsize(this.font, I18n.get("kuvalich.guide.subtitle"), room);
        searchHint = GuideTheme.ellipsize(this.font, I18n.get("kuvalich.guide.search_hint"), searchW - 14 - 12);
        // 尺寸变了，底栏文案的截断宽度也变了
        footerView = null;
        // 动效：面板几何、书名逐字的位置与宽度（解密动画每帧不再测宽）、首页格子跟光束的换算
        fx.setPanel(panelX, panelY, panelW, panelH);
        fx.prepareTitle(this.font, titleText, subtitleText);
        home.setIntroBeam(panelY, panelH);
    }

    /**
     * 各部件按当前页重新排版（尺寸变了 / 视图换了 / 换页）
     */
    private void relayout() {
        int sideW = Math.max(112, Math.min(176, panelW * 27 / 100));
        sidebar.setBounds(panelX + 1, bodyY, sideW, bodyH);
        reader.setBounds(panelX + 2 + sideW, bodyY, panelW - sideW - 3, bodyH);
        int hp = GuideTheme.PAD + 4;
        // 矮屏（480×270）上下各省几像素，四行格子才能不滚动
        int top = bodyH < 240 ? 5 : 8;
        home.layout(view, current, text, panelX + hp, bodyY + top, panelW - hp * 2, bodyH - top * 2 + 2);
        if (sidebar.isSearchMode() && !query().isEmpty()) {
            sidebar.showResults(hits, text);
        } else {
            sidebar.showTree(view, current != null ? current.cat : null, this.font);
            sidebar.reveal(current);
        }
    }

    /**
     * 恢复上次关闭时的位置
     */
    private void restore() {
        EntryView ev = lastEntryKey != null ? view.entries.get(lastEntryKey) : null;
        current = ev;
        if (lastPage == Page.READ && ev != null) {
            page = Page.READ;
            reader.show(ev, lastScroll, false);
        } else {
            page = Page.HOME;
            reader.show(ev, ev != null ? lastScroll : 0f, false);
        }
        home.restartIntro(openedAt);
    }

    private void saveState() {
        lastPage = page;
        lastEntryKey = current != null ? current.key : null;
        lastScroll = reader != null && current != null ? reader.target : 0f;
    }

    @Override
    public void tick() {
        refreshIfStale();
        if (searchBox != null) {
            // 光标闪烁靠输入框自己的 tick 计数，界面不转发它就一直常亮
            searchBox.tick();
        }
        // 兜底：窗口最小化时不渲染，收场动画走不完，这里保证界面最终会关掉
        if (closingAt != 0L && Util.getMillis() - closingAt > GuideIntro.CLOSE_MS + 250f && minecraft != null && minecraft.screen == this) {
            super.onClose();
        }
    }

    /**
     * 服务端重发了配置 / 资源重载 / 切换了语言：重建视图，保持当前条目与滚动位置
     */
    private void refreshIfStale() {
        if (view == null || !view.isStale()) {
            return;
        }
        String key = current != null ? current.key : null;
        float scroll = reader.target;
        if (view.generation != GuideContentLoader.generation()) {
            // 内容换了，字体也可能跟着资源包换了：字宽缓存一并作废
            text = new GuideTextLayout(this.font);
            GuideReader fresh = new GuideReader(text, media);
            fresh.show(reader.entry, scroll, false);
            reader = fresh;
        }
        view = GuideView.build();
        search = new GuideSearch(view, media);
        reader.invalidate();
        EntryView ev = key != null ? view.entries.get(key) : null;
        if (ev == null && key != null) {
            // 这一篇在新配置下被条件藏掉了：退到同分类第一篇，分类也没了就回首页
            CatView cv = view.categoriesById.get(key.substring(0, key.indexOf('/')));
            ev = cv != null ? cv.entries.get(0) : null;
            scroll = 0f;
        }
        current = ev;
        String q = query();
        // 搜索中的阅读页本来就允许「左栏是结果、右栏还没选篇」，不能因为当前篇没了就把人踢回首页 ——
        // 首页和顶栏里还挂着的搜索词对不上，再打一个字又会被推回阅读页
        if (current == null && page == Page.READ && q.isEmpty()) {
            page = Page.HOME;
            home.restart(Util.getMillis());
        }
        reader.show(current, scroll, false);
        hits = q.isEmpty() ? List.of() : search.search(q);
        cursor = hits.isEmpty() ? -1 : Math.min(Math.max(cursor, 0), hits.size() - 1);
        relayout();
        saveState();
    }

    @Override
    public void removed() {
        saveState();
        if (reader != null) {
            reader.release();
        }
        sidebar.release();
        home.release();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 开场 / 收场 ====================

    /**
     * 开场期间（以及回首页后格子成形、翻页擦除期间）的点击 / 滚轮 / 按键：动画直接跳到终态
     * （开场时间轴拨到头、首页格子全部成形、擦除收尾），
     * 这次输入<b>接着照常处理</b>。面板全程不平移，终态下的命中区就是玩家看着的位置。
     * Esc 不走这里：开场中按 Esc 应该从当时的样子直接收场，而不是先闪到终态再收。
     */
    private void skipIntro() {
        if (closingAt != 0L) {
            return;
        }
        introAnchored = true;
        long now = Util.getMillis();
        if (now - openedAt < GuideIntro.OPEN_MS) {
            openedAt = now - (long) GuideIntro.OPEN_MS;
        }
        // 不只开场：回首页后格子也要重新成形（十个分类时最后一格 300 多毫秒后才可点），这期间点到一个
        // 已经画出外框、内容还没浮现过半的格子，tileAt 会当它不存在 —— 看得见却点不动。
        // 有输入就一并成形完毕；接下来这次输入若是换页，新一轮成形照常从头播
        home.finishAppear();
        wipeAt = 0L;
    }

    @Override
    public void onClose() {
        if (closingAt == 0L) {
            // 开场还没走完就关：开场时间轴冻结在这一刻（见 GuideIntro#update），收场从当时的样子接着走
            closingAt = Util.getMillis();
            saveState();
            return;
        }
        super.onClose();
    }

    // ==================== 导航 ====================

    private Loc here() {
        return page == Page.READ && current != null ? new Loc(current.key, reader.target) : new Loc(null, 0f);
    }

    private static void push(Deque<Loc> stack, Loc loc) {
        Loc top = stack.peek();
        if (top != null && java.util.Objects.equals(top.entryKey(), loc.entryKey())) {
            stack.pop();
        }
        stack.push(loc);
        while (stack.size() > HISTORY_MAX) {
            stack.removeLast();
        }
    }

    /**
     * 打开一篇（记历史）
     *
     * @param ev     条目
     * @param scroll 初始滚动位置
     */
    private void open(@Nullable EntryView ev, float scroll) {
        if (ev == null) {
            return;
        }
        if (page == Page.READ && ev == current) {
            reader.scrollTo(scroll);
            return;
        }
        push(BACK, here());
        FORWARD.clear();
        show(new Loc(ev.key, scroll));
    }

    private void goHome() {
        if (page == Page.HOME) {
            return;
        }
        push(BACK, here());
        FORWARD.clear();
        show(new Loc(null, 0f));
    }

    private void back() {
        travel(BACK, FORWARD);
    }

    private void forward() {
        travel(FORWARD, BACK);
    }

    private void travel(Deque<Loc> from, Deque<Loc> to) {
        while (!from.isEmpty()) {
            Loc loc = from.pop();
            // 历史跨开关、跨存档保留，指向的那篇可能已经被条件藏掉了：跳过
            if (loc.entryKey() != null && !view.entries.containsKey(loc.entryKey())) {
                continue;
            }
            if (loc.entryKey() == null && page == Page.HOME) {
                continue;
            }
            push(to, here());
            show(loc);
            return;
        }
    }

    /**
     * 不记历史地切到某个位置
     */
    private void show(Loc loc) {
        long now = Util.getMillis();
        if (loc.entryKey() == null) {
            page = Page.HOME;
            // 回到首页就是结束这次搜索：留着搜索词的话，首页的格子和顶栏里的关键词对不上
            if (!searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
            }
            home.layout(view, current, text, home.x, home.y, home.w, home.h);
            home.restart(now);
            click();
        } else {
            EntryView ev = view.entries.get(loc.entryKey());
            if (ev == null) {
                return;
            }
            boolean changed = ev != current || page != Page.READ;
            boolean catChanged = current == null || ev.cat != current.cat;
            page = Page.READ;
            current = ev;
            reader.show(ev, loc.scroll(), changed);
            if (!sidebar.isSearchMode()) {
                if (catChanged) {
                    sidebar.showTree(view, ev.cat, this.font);
                }
                sidebar.reveal(ev);
            }
            if (changed && minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
            }
        }
        saveState();
    }

    /** 回首页用按钮声；翻到某一篇用翻页声（在 {@link #show} 里）—— 同一次操作只响一个声音 */
    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private boolean navEnabled(int i) {
        return switch (i) {
            case 0 -> canTravel(BACK);
            case 1 -> canTravel(FORWARD);
            default -> page != Page.HOME;
        };
    }

    /**
     * 栈里有没有 {@link #travel} 不会跳过的位置。只看「栈不空」的话，栈里剩的全是会被跳过的
     * （被条件藏掉的篇、已经在首页时的首页位置）时，按钮亮着、点了没反应、点完才变灰。
     * 跳过规则必须和 travel 保持一致。每帧三个按钮 × 最多 {@value #HISTORY_MAX} 项，开销可以忽略
     */
    private boolean canTravel(Deque<Loc> stack) {
        for (Loc loc : stack) {
            if (loc.entryKey() == null ? page != Page.HOME : view.entries.containsKey(loc.entryKey())) {
                return true;
            }
        }
        return false;
    }

    // ==================== 搜索 ====================

    private String query() {
        return searchBox == null ? "" : GuideSearch.normalize(searchBox.getValue());
    }

    private void onSearchChanged(String value) {
        String q = GuideSearch.normalize(value);
        if (q.isEmpty()) {
            hits = List.of();
            cursor = -1;
            sidebar.showTree(view, current != null ? current.cat : null, this.font);
            sidebar.reveal(current);
            if (page == Page.READ && current == null) {
                // 在首页打字进了阅读页、一篇都没命中，又把词清空了（Esc / × / 退格删光）：
                // 搜索已经结束，右栏却没有可显示的篇 —— 回首页，别停在一个空白的阅读页上。
                // 进搜索时压进去的那格首页位置一并弹掉：回到首页后它只会让「后退」亮着却点不动
                Loc top = BACK.peek();
                if (top != null && top.entryKey() == null) {
                    BACK.pop();
                }
                show(new Loc(null, 0f));
            }
            return;
        }
        hits = search.search(q);
        cursor = hits.isEmpty() ? -1 : 0;
        sidebar.showResults(hits, text);
        if (page == Page.HOME) {
            // 在首页打字：直接进阅读页，左栏就是结果。首页留在历史里，后退能回来
            push(BACK, here());
            FORWARD.clear();
            page = Page.READ;
            if (current == null && !hits.isEmpty()) {
                current = hits.get(0).entry;
                reader.show(current, 0f, true);
            } else {
                reader.show(current, reader.target, true);
            }
            saveState();
        }
    }

    private void focusSearch() {
        searchBox.setFocused(true);
        setFocused(searchBox);
    }

    private void unfocusSearch() {
        searchBox.setFocused(false);
        setFocused(null);
    }

    private void moveCursor(int d) {
        int n = sidebar.resultCount();
        if (n == 0) {
            return;
        }
        cursor = cursor < 0 ? 0 : Math.floorMod(cursor + d, n);
        sidebar.revealResult(cursor);
    }

    private void openCursor() {
        EntryView ev = cursor >= 0 ? sidebar.resultAt(cursor) : null;
        if (ev != null) {
            open(ev, 0f);
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float delta = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (!introAnchored) {
            // 开场从第一帧算起：init 里建视图、排版可能要几十毫秒，按 init 的时刻算会把开场的头一段（亮点、中线）吃掉
            introAnchored = true;
            if (closingAt == 0L) {
                openedAt = now;
                home.restartIntro(now);
            }
        }
        refreshIfStale();

        fx.update(now, openedAt, closingAt);
        if (fx.closed()) {
            // 只在自己仍是当前界面时关：收场期间被别的界面压上来时本界面只是背景层，这时关会改动图层列表
            if (minecraft != null && minecraft.screen == this) {
                super.onClose();
            }
            return;
        }
        boolean settled = fx.settled();
        float time = GuideTheme.time();

        // 换页（不管是点格子、搜索、后退还是配置重载踢回首页）都在这里统一发现，播一次横向擦除
        if (shownPage != null && shownPage != page && closingAt == 0L) {
            wipeFrom = shownPage;
            wipeAt = now;
        }
        shownPage = page;
        if (wipeAt != 0L && now - wipeAt >= GuideIntro.WIPE_MS) {
            wipeAt = 0L;
        }

        rc.beginFrame();
        rc.g = g;
        rc.font = this.font;
        rc.media = media;
        rc.mouseX = mouseX;
        rc.mouseY = mouseY;
        rc.mouseActive = settled;
        rc.now = now;
        rc.time = time;
        rc.clipTop = 0;
        rc.clipBottom = this.height;
        rc.alpha = 1f;

        g.drawManaged(() -> {
            g.fill(0, 0, this.width, this.height, GuideTheme.fade(GuideTheme.SCRIM, fx.scrim));
            GuideTheme.vignette(g, this.width, this.height, fx.scrim);
            renderPanel(g, mouseX, mouseY, partialTick, settled, delta, now);
            fx.drawBrackets(g);
            fx.drawSeam(g);
        });

        // tooltip 不能放进 drawManaged：它内部自己也 drawManaged，嵌套会让外层后续绘制退化成逐次 flush
        if (settled) {
            renderTooltips(g, mouseX, mouseY);
        }
    }

    /**
     * 面板本体，按开场 / 收场进度裁剪（分镜见 {@link GuideIntro}）
     *
     * <ol>
     *   <li>展开带：以面板中线为轴的一条横带，高度随展开 / 收拢变化；板材只画在带里；</li>
     *   <li>光束之上：顶栏、正文、底栏、输入框只画在扫描光束已经扫过的部分；</li>
     *   <li>带里再叠全息闪烁、光束与干扰条、收场时盖住内容的板材；</li>
     *   <li>外框：描线没走完时画描线（不受展开带裁剪，跑在展开前面）；描完后画常态外框，
     *       收场时跟着展开带一起收（上下多留 3px 给辉光）。</li>
     * </ol>
     * 终态下两层裁剪都不开，和没有动画时的绘制完全一样。
     */
    private void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick, boolean settled, float delta, long now) {
        int bandH = fx.bandHeight();
        if (bandH <= 0) {
            fx.drawTrace(g, GuideTheme.CHAMFER);
            return;
        }
        int bandTop = fx.bandTop();
        boolean clipBand = bandH < panelH;
        if (clipBand) {
            g.enableScissor(panelX - 3, bandTop, panelX + panelW + 3, bandTop + bandH);
        }
        CodexTheme.chamferFill(g, panelX, panelY, panelW, panelH, GuideTheme.CHAMFER, GuideTheme.PLATE);
        CodexTheme.holoSurface(g, panelX + 2, panelY + 2, panelW - 4, panelH - 4, 10);

        int reveal = fx.revealBottom();
        if (reveal > panelY) {
            boolean clipBeam = reveal < panelY + panelH;
            if (clipBeam) {
                g.enableScissor(panelX, panelY, panelX + panelW, reveal);
            }
            renderHeader(g, mouseX, mouseY, settled, delta, 0);
            renderBody(g, delta, settled, now);
            renderFooter(g, 0);
            super.render(g, mouseX, mouseY, partialTick);
            if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
                // 占位提示自己画：EditBox 自带的 hint 颜色不可调，在这套暗色面板上太亮
                g.drawString(this.font, searchHint, searchBox.getX(), searchBox.getY(), GuideTheme.FAINT, false);
            }
            if (clipBeam) {
                g.disableScissor();
            }
        }
        fx.drawFlicker(g, GuideTheme.CHAMFER);
        fx.drawBeam(g, GuideTheme.CHAMFER);
        fx.drawCloseFade(g, GuideTheme.CHAMFER);
        if (clipBand) {
            g.disableScissor();
        }

        if (fx.traceDone()) {
            if (clipBand) {
                g.enableScissor(panelX - 3, bandTop - 3, panelX + panelW + 3, bandTop + bandH + 3);
            }
            CodexTheme.chamferGlow(g, panelX, panelY, panelW, panelH, GuideTheme.CHAMFER, fx.outlineColor(), fx.glowStrength());
            if (clipBand) {
                g.disableScissor();
            }
        } else {
            fx.drawTrace(g, GuideTheme.CHAMFER);
        }
    }

    /**
     * 正文区：当前页；换页后的 {@value GuideIntro#WIPE_MS}ms 里是横向擦除 ——
     * 擦除线一侧画新页、另一侧画旧页（两页的部件都常驻，旧页照原样再画一次就行）。
     * 去阅读页从左往右擦，回首页从右往左擦。旧页不响应鼠标。
     *
     * <p>各部件收到的「开合位移」恒为 0：面板不再平移，参数只是留着没删。</p>
     */
    private void renderBody(GuiGraphics g, float delta, boolean settled, long now) {
        if (wipeAt == 0L || wipeFrom == null || wipeFrom == page) {
            renderPage(g, page, delta, settled, now);
            return;
        }
        float e = GuideTheme.easeOutCubic((now - wipeAt) / GuideIntro.WIPE_MS);
        int left = panelX + 1;
        int right = panelX + panelW - 1;
        int span = right - left;
        boolean rightward = page == Page.READ;
        int front = rightward ? left + Math.round(span * e) : right - Math.round(span * e);

        g.enableScissor(rightward ? left : front, bodyY, rightward ? front : right, bodyY + bodyH);
        renderPage(g, page, delta, settled, now);
        g.disableScissor();

        boolean active = rc.mouseActive;
        rc.mouseActive = false;
        g.enableScissor(rightward ? front : left, bodyY, rightward ? right : front, bodyY + bodyH);
        renderPage(g, wipeFrom, delta, false, now);
        g.disableScissor();
        rc.mouseActive = active;

        GuideIntro.drawWipeFront(g, front, bodyY, bodyH, rightward, left, right);
    }

    private void renderPage(GuiGraphics g, Page p, float delta, boolean settled, long now) {
        if (p == Page.HOME) {
            CodexTheme.motes(g, panelX + 2, bodyY, panelW - 4, bodyH, now, 1f);
            home.render(rc, delta, 0, settled);
        } else {
            sidebar.render(rc, current, cursor, delta, 0);
            reader.render(rc, view, delta, 0);
        }
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (!rc.hoverStack.isEmpty()) {
            g.renderTooltip(this.font, rc.hoverStack, mouseX, mouseY);
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (GuideTheme.hit(mouseX, mouseY, navX[i], navY, NAV_W, NAV_H)) {
                String key = i == 0 ? "kuvalich.guide.nav.back" : i == 1 ? "kuvalich.guide.nav.forward" : "kuvalich.guide.nav.home";
                g.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderHeader(GuiGraphics g, int mx, int my, boolean settled, float delta, int shift) {
        int top = panelY;
        // 顶栏底色只画在顶栏范围内，但沿用面板的斜切轮廓：直接 fill 整条会把左右上角的切角填平。
        // 裁剪框是屏幕坐标，不受 pose 平移影响，要自己加上开合动画的位移
        g.enableScissor(panelX, top + shift, panelX + panelW, top + GuideTheme.HEADER_H + shift);
        CodexTheme.chamferFill(g, panelX + 1, top + 1, panelW - 2, panelH - 2, GuideTheme.CHAMFER - 1, GuideTheme.PLATE_HI);
        CodexTheme.holoSurface(g, panelX + 1, top + 1, panelW - 2, GuideTheme.HEADER_H - 1, 10);
        g.disableScissor();

        // 徽记：斜切徽框里嵌着书本身
        int ex = panelX + GuideTheme.PAD;
        int ey = top + 6;
        GuideTheme.emblem(g, ex, ey, 22, GuideTheme.PLATE_DEEP, GuideTheme.withAlpha(GuideTheme.TECH, 0xC0));
        CodexTheme.cornerTab(g, ex + 1, ey + 1, 5, GuideTheme.KUVA);
        g.renderItem(view.bookIcon(), ex + 3, ey + 3);

        int tx = ex + 22 + 7;
        // 书名 + 副标题：开场时书名做乱码解密、副标题打字式展开，之后就是两次普通的 drawString
        fx.drawTitle(g, this.font, tx, top + 7, top + 19);

        // 不显示「数值来自服务端 / 本地」之类的标签：同步是后台的事，单人游戏里本来也没有「服务器」可言

        // 导航按钮：后退 / 前进 / 首页
        for (int i = 0; i < 3; i++) {
            boolean on = navEnabled(i);
            boolean over = settled && on && GuideTheme.hit(mx, my, navX[i], navY, NAV_W, NAV_H);
            navHover[i] = GuideTheme.approach(navHover[i], over ? 1f : 0f, delta, 16f);
            float hv = navHover[i];
            int bx = navX[i];
            CodexTheme.chamferFill(g, bx, navY, NAV_W, NAV_H, 3,
                    GuideTheme.lerpColor(GuideTheme.PLATE_DEEP, 0xFF1A2530, hv));
            CodexTheme.chamferOutline(g, bx, navY, NAV_W, NAV_H, 3, on
                    ? GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF), GuideTheme.KUVA, hv)
                    : GuideTheme.withAlpha(GuideTheme.EDGE, 0x70));
            int glyph = !on ? GuideTheme.withAlpha(GuideTheme.FAINT, 0x90)
                    : GuideTheme.lerpColor(GuideTheme.ASH, GuideTheme.BONE, hv);
            int cx = bx + NAV_W / 2;
            int cy = navY + NAV_H / 2;
            if (i == 2) {
                GuideTheme.house(g, cx, cy - 1, glyph);
            } else {
                GuideTheme.chevron(g, cx, cy, i == 0, glyph);
            }
        }

        // 搜索框：前导放大镜 + 下划线（和图鉴的搜索框同一种写法，比整圈描边轻）
        boolean focused = searchBox != null && searchBox.isFocused();
        int line = focused ? GuideTheme.TECH : GuideTheme.EDGE;
        int underY = searchY + 15;
        g.fill(searchX, underY, searchX + searchW, underY + 1, line);
        if (focused) {
            g.fill(searchX, underY + 1, searchX + searchW, underY + 2, GuideTheme.withAlpha(line, 0x40));
        }
        g.fill(searchX, underY - 4, searchX + 1, underY + 1, line);
        g.fill(searchX + searchW - 1, underY - 4, searchX + searchW, underY + 1, line);
        GuideTheme.magnifier(g, searchX + 4, searchY + 4, focused ? GuideTheme.TECH : GuideTheme.FAINT);
        clearVisible = searchBox != null && !searchBox.getValue().isEmpty();
        if (clearVisible) {
            clearX = searchX + searchW - 10;
            clearY = searchY + 4;
            boolean over = GuideTheme.hit(mx, my, clearX - 1, clearY - 1, 9, 9);
            int c = over ? GuideTheme.KUVA : GuideTheme.FAINT;
            for (int i = 0; i < 7; i++) {
                g.fill(clearX + i, clearY + i, clearX + i + 1, clearY + i + 1, c);
                g.fill(clearX + 6 - i, clearY + i, clearX + 7 - i, clearY + i + 1, c);
            }
        }

        CodexTheme.dataLine(g, panelX + 1, top + GuideTheme.HEADER_H, panelW - 2, GuideTheme.TECH, rc.now);
    }

    private void renderFooter(GuiGraphics g, int shift) {
        int fy = panelY + panelH - GuideTheme.FOOTER_H;
        g.enableScissor(panelX, fy + shift, panelX + panelW, panelY + panelH + shift);
        CodexTheme.chamferFill(g, panelX + 1, panelY + 1, panelW - 2, panelH - 2, GuideTheme.CHAMFER - 1, GuideTheme.PLATE_HI);
        g.disableScissor();
        g.fill(panelX + 1, fy, panelX + panelW - 1, fy + 1, GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0));

        boolean searching = sidebar.isSearchMode();
        if (footerView != view || footerPage != page || footerEntry != current || footerSearch != searching) {
            footerView = view;
            footerPage = page;
            footerEntry = current;
            footerSearch = searching;
            if (page == Page.HOME) {
                footerStatus = I18n.get("kuvalich.guide.status.home", view.categories.size(), view.entryList.size());
            } else if (current != null) {
                footerStatus = I18n.get("kuvalich.guide.status.read", current.cat.title(), current.index + 1, current.cat.entries.size());
            } else {
                footerStatus = "";
            }
            footerStatusW = this.font.width(footerStatus);
            String hintKey = page == Page.HOME ? "kuvalich.guide.hint.home"
                    : searching ? "kuvalich.guide.hint.search" : "kuvalich.guide.hint.read";
            footerHint = GuideTheme.ellipsize(this.font, I18n.get(hintKey),
                    panelW - GuideTheme.PAD * 2 - footerStatusW - 16);
        }
        int ty = fy + (GuideTheme.FOOTER_H - 8) / 2;
        g.drawString(this.font, footerStatus, panelX + panelW - GuideTheme.PAD - footerStatusW, ty, GuideTheme.ASH, false);
        g.drawString(this.font, footerHint, panelX + GuideTheme.PAD, ty, GuideTheme.FAINT, false);
    }

    // ==================== 输入 ====================

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (closingAt != 0L) {
            return true;
        }
        boolean focused = searchBox.isFocused();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!searchBox.getValue().isEmpty()) {
                searchBox.setValue("");
                unfocusSearch();
                return true;
            }
            if (focused) {
                unfocusSearch();
                return true;
            }
            onClose();
            return true;
        }
        // 开场中按别的键：动画跳到终态，这个键照常处理
        skipIntro();
        if (hasAltDown() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            if (key == GLFW.GLFW_KEY_LEFT) {
                back();
            } else {
                forward();
            }
            return true;
        }
        if (focused) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                openCursor();
                return true;
            }
            if (sidebar.isSearchMode() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
                moveCursor(key == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_TAB) {
                unfocusSearch();
                return true;
            }
            searchBox.keyPressed(key, scan, modifiers);
            // 输入框开着时吞掉其余按键：别让字母键触发别的快捷操作
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            back();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB || (hasControlDown() && key == GLFW.GLFW_KEY_F)) {
            focusSearch();
            return true;
        }
        if (page == Page.READ) {
            if (sidebar.isSearchMode() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
                // 左栏是结果列表时 ↑↓ 始终在结果间移动，和底栏提示「↑↓ 选择」一致。
                // 用鼠标点过一条结果后输入框就失焦了，原先这时 ↑↓ 改去滚正文，提示却没变；
                // 正文照样能用 PgUp / PgDn / 空格 / Home / End 和滚轮滚
                moveCursor(key == GLFW.GLFW_KEY_UP ? -1 : 1);
                return true;
            }
            float pageStep = bodyH * 0.85f;
            switch (key) {
                case GLFW.GLFW_KEY_UP -> reader.scrollBy(-GuideTheme.LINE_H * 2);
                case GLFW.GLFW_KEY_DOWN -> reader.scrollBy(GuideTheme.LINE_H * 2);
                case GLFW.GLFW_KEY_PAGE_UP -> reader.scrollBy(-pageStep);
                case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> reader.scrollBy(pageStep);
                case GLFW.GLFW_KEY_HOME -> reader.scrollTo(0);
                case GLFW.GLFW_KEY_END -> reader.scrollTo(reader.maxScroll());
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> openCursor();
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            EntryView cont = home.continueTarget();
            if (cont != null) {
                open(cont, lastScroll);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (closingAt != 0L) {
            return true;
        }
        // 输入法直接上屏的字不经过 keyPressed：这里也要跳过开场
        skipIntro();
        if (!searchBox.isFocused()) {
            // 直接打字就是搜索：不用先去点搜索框
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return false;
            }
            focusSearch();
        }
        return searchBox.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (closingAt != 0L) {
            return true;
        }
        // 开场中点一下：动画跳到终态，这一下照常处理（不吞掉）。面板全程不平移，
        // 终态下的命中区就是玩家看着的位置；还没扫出来的格子此刻也一并成形完毕
        skipIntro();
        long now = Util.getMillis();
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            back();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_5) {
            forward();
            return true;
        }

        // 搜索框：命中范围按外框算，不按 EditBox 自己那条窄窄的文字区
        if (GuideTheme.hit(mx, my, searchX, searchY, searchW, 18)) {
            if (clearVisible && GuideTheme.hit(mx, my, clearX - 2, clearY - 2, 11, 11)) {
                searchBox.setValue("");
            } else if (mx >= searchBox.getX()) {
                // 先让输入框按点击位置放光标；它点在自己范围外会顺手取消聚焦，所以聚焦放在后面
                searchBox.mouseClicked(Math.min(mx, searchBox.getX() + searchBox.getWidth() - 1), searchBox.getY() + 2, button);
            }
            focusSearch();
            return true;
        }
        if (searchBox.isFocused()) {
            // 点别处就收起光标，否则那条闪动的竖线会一直挂在那里
            unfocusSearch();
        }

        if (button == 0) {
            for (int i = 0; i < 3; i++) {
                if (navEnabled(i) && GuideTheme.hit(mx, my, navX[i], navY, NAV_W, NAV_H)) {
                    if (i == 0) {
                        back();
                    } else if (i == 1) {
                        forward();
                    } else {
                        goHome();
                    }
                    return true;
                }
            }
        }

        if (page == Page.HOME) {
            if (home.mouseClicked(mx, my, button)) {
                return true;
            }
            if (button == 0) {
                int t = home.tileAt(mx, my, now);
                CatView cat = home.cat(t);
                if (cat != null) {
                    open(cat.entries.get(0), 0f);
                    return true;
                }
                if (home.overContinue(mx, my, now)) {
                    open(home.continueTarget(), lastScroll);
                    return true;
                }
            }
            return super.mouseClicked(mx, my, button);
        }

        if (reader.mouseClicked(mx, my, button) || sidebar.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0) {
            GuideSidebar.Row row = sidebar.rowAt(mx, my);
            if (row != null) {
                if (row.kind == GuideSidebar.Kind.CAT && row.cat != null) {
                    // 点分类名：一步跳到该分类第一篇（已经在这个分类里就回到它的第一篇）
                    open(row.cat.entries.get(0), 0f);
                } else if (row.entry != null) {
                    if (row.kind == GuideSidebar.Kind.RESULT) {
                        cursor = row.resultIndex;
                    }
                    open(row.entry, 0f);
                }
                return true;
            }
            Link link = rc.nextHoverLink;
            if (link != null && link.isLive() && reader.contains(mx, my)) {
                open(view.entries.get(link.entryKey), 0f);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (page == Page.READ && (reader.mouseDragged(my, button) || sidebar.mouseDragged(my, button))) {
            return true;
        }
        if (page == Page.HOME && home.mouseDragged(my, button)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (reader != null) {
            reader.release();
        }
        sidebar.release();
        home.release();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (closingAt != 0L) {
            return true;
        }
        skipIntro();
        if (page == Page.HOME) {
            return home.mouseScrolled(delta) || super.mouseScrolled(mx, my, delta);
        }
        if (sidebar.mouseScrolled(mx, my, delta) || reader.mouseScrolled(mx, my, delta)) {
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }
}
