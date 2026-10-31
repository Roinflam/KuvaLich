package pers.roinflam.kuvalich.client.gui.guide;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.config.GuideDisplayConfig;
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
 * <p>两页：<b>首页</b>是分类大格子，<b>阅读页</b>是「左目录 + 右正文」。
 * 两页共用同一块面板和顶栏 —— 翻页时顶栏、搜索框一动不动，
 * 只有正文区在换，玩家不会觉得跳到了另一个界面。</p>
 *
 * <h3>视觉</h3>
 * <p>全部沿用模组图鉴的军械库全息终端语言（见 {@link GuideTheme}）：冷色近黑的斜切板材、
 * 科技青的结构线与扫描线、全屏唯一的暖色焦点赤毒猩红（当前条目、悬停中的格子 / 链接）。</p>
 *
 * <h3>动效：军械库终端开机（分镜与时间轴见 {@link GuideIntro}）</h3>
 * <ul>
 *   <li>开场（三幕约 1600ms，1280ms 就可以操作）：中心亮点通电、四个瞄准括号逐个咬合锁定、自检条走到 100% →
 *       亮点拉成扫描线、面板以它为轴上下展开、外框描线，括号骑着展开带被带到面板四角落位 →
 *       扫描光束自上而下扫出内容（固定三次全息闪烁与色偏干扰条）→ 顶栏书名乱码解密 → 首页格子跟着光束逐个全息成形。
 *       每次打开都放完整开场；开场期间点一下 / 按任意键 / 滚轮（Esc 除外）直接跳到终态，<b>这次输入照常处理</b>；
 *       面板展开过半之前的点击和滚轮只跳过、不处理；F1~F12、单按修饰键不跳；</li>
 *   <li>从物品提示上按住 Ctrl 查阅打开的走<b>短开场</b>（从扫描线展开开始，约 280ms），直接翻到那一块并让它亮一下；</li>
 *   <li>收场（约 1000ms，和开场倒着呼应）：擦除光束自下而上盖住内容、书名重新加密 → 面板收拢、外框收线 →
 *       扫描线收回亮点、括号重新锁定后依次飞出、亮点断电。{@link #onClose()} 推迟到动画走完，然后回到打开前的界面
 *       （从物品栏查阅的回到物品栏）；收场期间按任意键 / 点一下直接关完（按的键交给游戏，Esc 除外；单按修饰键、F1~F12 不算）；</li>
 *   <li>首页 ↔ 阅读页：正文区 140ms 横向擦除；</li>
 *   <li>面板全程<b>不平移</b>，只裁剪、叠光效 —— 任何时刻命中区都和画出来的位置一致；</li>
 *   <li>常驻：暗角、顶栏分隔线上一道往复的扫描亮点、四角一层淡青括号。阅读栏、左栏、首页格子区里<b>没有常驻动画</b>
 *       （见 {@link GuideTheme} 类注释）；悬停时四个角括号收到格子 / 按钮的四角上（锁定括号），按住时底色转暗。</li>
 * </ul>
 *
 * <h3>界面大小（{@link GuideScale}）</h3>
 * <p>指南用自己的界面倍数：游戏选「自动」时取虚拟屏幕至少 560×320 的最大倍数（1080p 4 → 3、1440p 6 → 4），手动倍数跟游戏。
 * 顶栏第四个按钮「Aa」左键字小一档、右键字大一档；Ctrl + 滚轮、Ctrl − / = / 0 同样能调，写回 {@code kuvalich-tooltip.toml}
 * 的 {@code [guide] gui_scale}。换倍数、改窗口大小后阅读位置按块锚定（{@link GuideReader#anchor()}）。</p>
 *
 * <h3>数值来源</h3>
 * <p>正文里的 {@code {cfg:…}} 取自 {@link GuideClientConfig#snapshot()}：打开时取一次，
 * 服务端重发配置（{@link GuideClientConfig#revision()} 变了）、F3+T 重载资源、切换语言时整份重建并重新排版，
 * 保持当前条目与滚动位置不变。数值来源不在界面上显示：同步是后台的事，单人游戏里也无所谓「服务器」。</p>
 *
 * <h3>记忆与后退</h3>
 * <p>上次停在哪一页、哪一篇、滚到哪、前进后退历史，都记在静态字段里 —— 关掉再开回到原处，
 * 跨存档也保留（找不到那一篇了就回首页）。后退：顶栏按钮、Backspace、鼠标右键、鼠标侧键、Alt+←。
 * 查阅打开时原先的位置压进后退历史，读完按后退回得去。</p>
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

    /** 关掉指南后回到的界面（从物品栏查阅时就是物品栏）；null = 回到游戏 */
    @Nullable
    private final Screen parent;
    /** 查阅：打开时直接翻到的条目与块（块序号 -1 = 篇首）；null = 回到上次的位置 */
    @Nullable
    private final String targetKey;
    private final int targetBlock;
    /** 从生存物品栏查阅时 2×2 合成结果格的快照（见 {@link CraftResult}）；不是从那里打开的为 null，放回去之后也清成 null */
    @Nullable
    private CraftResult craftResult;
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
    /** 开场 / 收场的声音，挂在 {@link #fx} 的时间轴上 */
    private final GuideSfx sfx = new GuideSfx();
    /** 悬停锁定括号的限速（首页格子、继续阅读条、顶栏按钮共用） */
    private final GuideLockBudget lockBudget = new GuideLockBudget();
    private EditBox searchBox;

    // ==================== 状态 ====================

    private Page page = Page.HOME;
    @Nullable
    private EntryView current;
    private List<GuideSearch.Hit> hits = List.of();
    /** 键盘选中的搜索结果 */
    private int cursor = -1;
    private boolean restored;
    /**
     * 查阅打开时手上还按着的 Ctrl（查阅就是按住 Ctrl 或 Ctrl + Shift 打开的，打开那一刻多半还没松）：松开之前不算调大小的 Ctrl ——
     * 不然顺手滚一下滚轮读正文，界面就大一档 / 小一档，还写进了配置。看到松开一次就清掉（{@link #releaseCtrlLatch()}，每帧、每 tick 查）
     */
    private boolean ctrlLatched;
    /** 见 {@link #repositionElements()}：重建控件前输入框是否聚焦着 */
    private boolean keepSearchFocus;

    /**
     * 开场时钟（毫秒）：每帧加上本帧间隔、单帧最多记 {@value GuideIntro#MAX_FRAME_MS}ms（短开场是真实时间，{@link GuideIntro#update} 换算到开场时间轴）。
     * 打开指南的头几帧常常一帧一两百毫秒，按墙钟算会吃掉上电和锁定那一段；逐帧累加时低帧率下开场按比例变慢，最小化时暂停
     */
    private float introT;
    private long closingAt;
    private long lastFrame;
    /** 开场时钟已经从第一帧开始走了（或已被跳过） */
    private boolean introAnchored;
    /** 上一帧画的是哪一页：和当前页不同就说明换页了，播一次横向擦除 */
    @Nullable
    private Page shownPage;
    /** 擦除中：旧页是哪一页、从什么时候开始擦（0 = 没在擦） */
    @Nullable
    private Page wipeFrom;
    private long wipeAt;
    /** 顶栏按钮的悬停量：0~2 导航、3 是「Aa」、4 是开场动画开关 */
    private final float[] navHover = new float[5];
    /** 顶栏按钮开始悬停的时刻（锁定括号按它收拢）；0 = 没在悬停 */
    private final long[] navLockAt = new long[5];

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
    /** 「Aa」按钮（调界面大小）：在三个导航按钮右边，中间空 {@value #SCALE_GAP}px、画一道 1px 结构线 */
    private int scaleX;
    /** 开场动画开关：紧挨在「Aa」左边，中间空 3px */
    private int animX;
    private static final int SCALE_GAP = 5;
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

    /** 回到上次看的位置、完整开场、关掉后回到游戏（物品右键；测试工具按无参构造打开也走这里） */
    public GuideScreen() {
        this(null, null, -1, false);
    }

    /**
     * @param parent      关掉后回到的界面，可为 null
     * @param targetKey   直接翻到的条目（查阅），null = 回到上次的位置
     * @param targetBlock 那一篇里的第几块（{@link GuideView.EntryView#blocks()} 的下标），-1 = 篇首
     * @param quick       走短开场（查阅打开）
     */
    GuideScreen(@Nullable Screen parent, @Nullable String targetKey, int targetBlock, boolean quick) {
        super(Component.translatable("kuvalich.guide.title"));
        this.parent = parent;
        this.targetKey = targetKey;
        this.targetBlock = targetBlock;
        this.fx.quick = quick;
        this.ctrlLatched = quick && hasControlDown();
        // 在 setScreen 换掉物品栏之前拍下来：换的时候物品栏的 removed() 会把结果格清掉
        this.craftResult = CraftResult.capture(parent);
    }

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        // 第一行：换成指南自己的界面倍数，width / height 改成设完后的虚拟尺寸（首次打开、改窗口大小、切全屏、F3+T、调大小都走这里）
        if (minecraft != null) {
            GuideScale.apply(this, minecraft);
        }
        // 重新 init 时阅读栏宽度会变、整篇重排：先按块记下阅读位置，排完换算回去
        GuideReader.Anchor anchor = searchBox != null && reader != null && page == Page.READ ? reader.anchor() : null;
        if (text == null) {
            text = new GuideTextLayout(this.font);
            reader = new GuideReader(text, media);
        }
        if (view == null) {
            view = GuideView.current();
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
        reader.restoreAnchor(view, anchor);
    }

    /**
     * 换了界面大小（「Aa」按钮、Ctrl + 滚轮、Ctrl − = 0）：走原版的 resize 流程重建控件
     * （{@link #repositionElements()} 处理搜索框焦点，{@link #init()} 设新倍数、按块锚定阅读位置），首页滚回顶部，按钮声
     */
    private void rescale() {
        if (minecraft == null) {
            return;
        }
        home.scrollToTop();
        this.resize(minecraft, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        click();
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
        // 后退 / 前进 / 首页，再空 SCALE_GAP 是开场动画开关和「Aa」（调界面大小）
        scaleX = searchX - 10 - NAV_W;
        animX = scaleX - 3 - NAV_W;
        int nx = animX - SCALE_GAP - (NAV_W * 3 + 3 * 2);
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
        EntryView target = targetKey != null ? view.entries.get(targetKey) : null;
        if (target != null) {
            // 查阅：直接翻到那一篇的那一块。原先的位置压进后退历史，读完按后退回得去
            Loc before = lastPage == Page.READ && lastEntryKey != null ? new Loc(lastEntryKey, lastScroll) : new Loc(null, 0f);
            if (!java.util.Objects.equals(before.entryKey(), target.key)) {
                push(BACK, before);
                FORWARD.clear();
            }
            current = target;
            page = Page.READ;
            reader.show(target, 0f, false);
            reader.scrollToBlock(targetBlock);
            home.restartIntro();
            saveState();
            return;
        }
        EntryView ev = lastEntryKey != null ? view.entries.get(lastEntryKey) : null;
        current = ev;
        if (lastPage == Page.READ && ev != null) {
            page = Page.READ;
            reader.show(ev, lastScroll, false);
        } else {
            page = Page.HOME;
            reader.show(ev, ev != null ? lastScroll : 0f, false);
        }
        home.restartIntro();
    }

    private void saveState() {
        lastPage = page;
        lastEntryKey = current != null ? current.key : null;
        lastScroll = reader != null && current != null ? reader.target : 0f;
    }

    @Override
    public void tick() {
        releaseCtrlLatch();
        refreshIfStale();
        if (searchBox != null) {
            // 光标闪烁靠输入框自己的 tick 计数，界面不转发它就一直常亮
            searchBox.tick();
        }
        // 兜底：窗口最小化时不渲染，收场动画走不完，这里保证界面最终会关掉
        if (closingAt != 0L && Util.getMillis() - closingAt > GuideIntro.CLOSE_MS + 250f && minecraft != null && minecraft.screen == this) {
            finishClose();
        }
    }

    /**
     * 服务端重发了配置 / 资源重载 / 切换了语言：重建视图，保持当前条目与滚动位置
     */
    private void refreshIfStale() {
        if (view == null) {
            return;
        }
        GuideView fresh = GuideView.current();
        if (fresh == view) {
            return;
        }
        String key = current != null ? current.key : null;
        float scroll = reader.target;
        if (view.generation != GuideContentLoader.generation()) {
            // 内容换了，字体也可能跟着资源包换了：字宽缓存一并作废
            text = new GuideTextLayout(this.font);
            GuideReader freshReader = new GuideReader(text, media);
            freshReader.show(reader.entry, scroll, false);
            reader = freshReader;
        }
        view = fresh;
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
        // 关书、被别的界面顶掉都经过这里：打完的通讯记为看过、没打完的下次再打，写回本地文件
        CommMemory.close(Util.getMillis());
        if (reader != null) {
            reader.release();
        }
        sidebar.release();
        home.release();
        super.removed();
        // 换界面都经过 setScreen → removed()：窗口倍数设回游戏的，下一个界面（物品栏、死亡界面、断线界面……）init 时读到的就是还原后的尺寸
        if (minecraft != null) {
            GuideScale.restore(minecraft);
        }
        // 不管接下来是回物品栏还是被别的界面顶掉，物品栏的合成结果格都放回去（客户端这份本来就该和服务端一样）
        if (craftResult != null) {
            craftResult.restore();
            craftResult = null;
        }
    }

    /** 看到 Ctrl 松开过，查阅时带进来的那次 Ctrl 就作废（见 {@link #ctrlLatched}） */
    private void releaseCtrlLatch() {
        if (ctrlLatched && !hasControlDown()) {
            ctrlLatched = false;
        }
    }

    /** 调界面大小的 Ctrl 组合（Ctrl + 滚轮、Ctrl − = 0）此刻算不算按着 Ctrl：查阅时带进来、还没松开过的不算 */
    private boolean scaleCtrlDown() {
        releaseCtrlLatch();
        return !ctrlLatched && hasControlDown();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 开场 / 收场 ====================

    /**
     * 开场期间（以及回首页后格子成形、翻页擦除期间）的点击 / 滚轮 / 按键：动画直接跳到终态
     * （开场时钟拨到头、首页格子全部成形、擦除收尾），
     * 这次输入<b>接着照常处理</b>（面板展开过半之前的点击 / 滚轮例外，见 {@link #introEarly()}）。面板全程不平移，终态下的命中区就是玩家看着的位置。
     * Esc 不走这里：开场中按 Esc 应该从当时的样子直接收场，而不是先闪到终态再收。
     */
    private void skipIntro() {
        if (closingAt != 0L) {
            return;
        }
        introAnchored = true;
        boolean running = introT < fx.introEnd();
        introT = Math.max(introT, fx.introEnd());
        if (running) {
            sfx.skipped(false);
        }
        // 不只开场：回首页后格子也要重新成形（十个分类时最后一格 300 多毫秒后才可点），这期间点到一个
        // 已经画出外框、内容还没浮现过半的格子，tileAt 会当它不存在 —— 看得见却点不动。
        // 有输入就一并成形完毕；接下来这次输入若是换页，新一轮成形照常从头播
        home.finishAppear();
        wipeAt = 0L;
    }

    /**
     * 面板展开过半之前（{@value GuideIntro#HALF_UNFOLD}ms 前）：屏幕上还只有遮罩、亮点和锁定框，玩家看不见自己点在什么上，
     * 终态那个位置可能正好是一个分类格子 —— 这时的点击和滚轮只跳过开场、不当成点到了什么。按键照常处理（直接打字就是搜索，和位置无关）
     */
    private boolean introEarly() {
        return closingAt == 0L && introT < fx.introEnd() && fx.early();
    }

    /**
     * 开场期间不跳过、收场期间不打断的键：F1~F12（F2 截图、F11 切全屏不该把开场打断）和单按的修饰键
     * （Ctrl 是 Ctrl+F 的前半，后面那个 F 照样会跳；物品上按住 Ctrl 查阅后常常一直按着，按住不放的键会不停发重复事件）
     */
    private static boolean passiveKey(int key) {
        return (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F12)
                || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    @Override
    public void onClose() {
        if (closingAt == 0L) {
            // 开场还没走完就关：开场时间轴冻结在这一刻（见 GuideIntro#update），收场从当时的样子接着走
            // 动画关着：把起点拨到收场时长以前，下一帧（或下一 tick）就是走完的状态，其余流程不变
            long now = Util.getMillis();
            sfx.closing(introT < fx.introEnd());
            closingAt = GuideDisplayConfig.introAnimation() ? now : now - (long) GuideIntro.CLOSE_MS - 300L;
            saveState();
            return;
        }
        finishClose();
    }

    /** 收场走完：回到打开前的界面（从物品栏查阅的回到物品栏），没有就回到游戏 */
    private void finishClose() {
        if (minecraft == null) {
            return;
        }
        // 收场被按键 / 点击提前关完：断电声还没放就补上
        sfx.closeFinished();
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
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
            GuideSfx.page(true);
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
            if (changed) {
                GuideSfx.page(false);
            }
        }
        saveState();
    }

    /**
     * 按钮声（「Aa」、开场动画开关）。翻到某一篇、回首页各有自己的声音（在 {@link #show} 里），
     * 后退 / 前进 / 首页三个导航按钮也走 show —— 同一次操作只响一个声音
     */
    private void click() {
        GuideSfx.click();
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
        openHit(cursor);
    }

    /**
     * 打开第 index 条搜索结果。最早的命中落在一条通讯里时滚到那条通讯，并把它记为看过：
     * 直接显示全文，不演开框和擦出、不响
     */
    private void openHit(int index) {
        EntryView ev = index >= 0 ? sidebar.resultAt(index) : null;
        if (ev == null) {
            return;
        }
        open(ev, 0f);
        GuideSearch.Hit hit = index < hits.size() ? hits.get(index) : null;
        if (hit != null && hit.entry == ev && hit.commKey != null && page == Page.READ && current == ev) {
            CommMemory.markSeen(hit.commKey);
            reader.scrollToBlock(hit.commBlock);
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void render(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // HUD 那一段换回了游戏倍数、Post 却没来（Pre 被别的模组取消）：先设回指南的倍数和投影
        GuideScale.ensure(g);
        releaseCtrlLatch();
        long now = Util.getMillis();
        long frameMs = lastFrame == 0L ? 0L : now - lastFrame;
        float delta = Math.min(0.1f, frameMs / 1000f);
        lastFrame = now;
        if (!introAnchored) {
            // 开场从第一帧算起：init 里建视图、排版可能要几十毫秒，算进去会把开场的头一段（亮点、锁定）吃掉
            introAnchored = true;
            if (closingAt == 0L) {
                introT = 0f;
                home.restartIntro();
                if (!GuideDisplayConfig.introAnimation()) {
                    // 开场动画关着：第一帧就是终态，和开场中点一下跳过的效果一样；开书照样响一声快开
                    sfx.skipped(true);
                    skipIntro();
                }
            }
        } else if (closingAt == 0L && introT < fx.introEnd()) {
            // 逐帧累加：单帧最多记 MAX_FRAME_MS，卡顿的那几帧不会把开场跳掉；收场时停住（开场冻结在按下关闭的那一刻）
            introT = Math.min(fx.introEnd(), introT + Math.min(GuideIntro.MAX_FRAME_MS, (float) frameMs));
        }
        refreshIfStale();

        fx.update(introT, closingAt, now);
        sfx.frame(fx, now);
        // 首页格子的开场成形读开场时间轴：完整开场、查阅短开场共用一条映射
        home.setIntroTime(fx.openT);
        if (fx.closed()) {
            // 只在自己仍是当前界面时关：收场期间被别的界面压上来时本界面只是背景层，这时关会改动图层列表
            if (minecraft != null && minecraft.screen == this) {
                finishClose();
            }
            return;
        }
        // 就绪（1280ms）起悬停、提示框、按下态生效 —— 最后那一段只剩外框通电回落、书名收尾和最后几格成形，不该挡着玩家操作
        boolean settled = fx.ready();

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
        // 按下态：直接问 GLFW 左键现在是不是按着（点击在 mouseClicked 里就生效了，这只决定按住时画成什么样）
        rc.mouseDown = settled && minecraft != null
                && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        rc.lockBudget = lockBudget;
        rc.now = now;
        rc.partialTick = partialTick;
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
     *   <li>带里再叠全息闪烁、光束与干扰条、收场时自下而上盖住内容的擦除板材；</li>
     *   <li>外框：描线没走完时画描线（不受展开带裁剪，跑在展开前面）；描完后画常态外框（裁在展开带上下多留 3px 给辉光）；
     *       收场收线时又画回倒着走的描线。</li>
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
                g.drawString(this.font, searchHint, searchBox.getX(), searchBox.getY(), GuideTheme.FAINT_TEXT, false);
            }
            if (clipBeam) {
                g.disableScissor();
            }
        }
        fx.drawFlicker(g, GuideTheme.CHAMFER);
        fx.drawBeam(g, GuideTheme.CHAMFER);
        fx.drawErase(g, GuideTheme.CHAMFER);
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
            // 原先格子缝里还有十几粒数据微粒在往上飘：首页是找格子、读标题的地方，去掉了
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
        if (GuideTheme.hit(mouseX, mouseY, animX, navY, NAV_W, NAV_H)) {
            g.renderComponentTooltip(this.font, List.of(
                    Component.translatable(GuideDisplayConfig.introAnimation() ? "kuvalich.guide.anim.on" : "kuvalich.guide.anim.off"),
                    Component.translatable("kuvalich.guide.anim.hint").withStyle(st -> st.withColor(GuideTheme.ASH & 0xFFFFFF))),
                    mouseX, mouseY);
            return;
        }
        if (minecraft != null && GuideTheme.hit(mouseX, mouseY, scaleX, navY, NAV_W, NAV_H)) {
            // 第一行：界面大小：自动（×3）/ ×2（已指定）/ 跟游戏一样（×4）；第二行次要色写怎么调。
            // 只有一档可选时（游戏本身是 1 倍或 2 倍）按钮是灰的，第二行改说为什么调不了，不教一个不起作用的操作
            String hint = GuideScale.adjustable(minecraft) ? "kuvalich.guide.scale.hint" : "kuvalich.guide.scale.locked";
            g.renderComponentTooltip(this.font, List.of(GuideScale.label(minecraft),
                    Component.translatable(hint).withStyle(st -> st.withColor(GuideTheme.ASH & 0xFFFFFF))),
                    mouseX, mouseY);
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
        GuideItemIcon.render(g, view.bookIcon(), ex + 3, ey + 3);

        int tx = ex + 22 + 7;
        // 书名 + 副标题：开场时书名做乱码解密、副标题打字式展开，之后就是两次普通的 drawString
        fx.drawTitle(g, this.font, tx, top + 7, top + 19);

        // 不显示「数值来自服务端 / 本地」之类的标签：同步是后台的事，单人游戏里本来也没有「服务器」可言

        // 顶栏按钮：后退 / 前进 / 首页，隔一道结构线是开场动画开关和「Aa」（调界面大小，静止，只有悬停 / 按下态）
        g.fill(animX - 3, navY + 2, animX - 2, navY + NAV_H - 2, GuideTheme.withAlpha(GuideTheme.EDGE, 0xE0));
        boolean animOn = GuideDisplayConfig.introAnimation();
        for (int i = 0; i < 5; i++) {
            boolean scale = i == 3;
            boolean anim = i == 4;
            int bx = scale ? scaleX : anim ? animX : navX[i];
            boolean on = scale ? minecraft != null && GuideScale.adjustable(minecraft) : anim || navEnabled(i);
            boolean over = settled && on && GuideTheme.hit(mx, my, bx, navY, NAV_W, NAV_H);
            navHover[i] = GuideTheme.approach(navHover[i], over ? 1f : 0f, delta, 16f);
            if (!over) {
                navLockAt[i] = 0L;
            } else if (navLockAt[i] == 0L) {
                // 刚压上：四个角括号收到按钮四角上；一秒里已经收满 3 次就直接静止
                navLockAt[i] = lockBudget.tryUse(rc.now) ? rc.now : rc.now - (long) GuideTheme.LOCK_MS;
            }
            float hv = navHover[i];
            // 三态：常态深底 + 结构线描边；悬停偏亮 + 猩红描边；按住时赤毒暗面
            boolean press = over && rc.mouseDown;
            CodexTheme.chamferFill(g, bx, navY, NAV_W, NAV_H, 3,
                    press ? GuideTheme.PRESS_BG : GuideTheme.lerpColor(GuideTheme.PLATE_DEEP, 0xFF1A2530, hv));
            CodexTheme.chamferOutline(g, bx, navY, NAV_W, NAV_H, 3, on
                    ? GuideTheme.lerpColor(GuideTheme.withAlpha(GuideTheme.EDGE, 0xFF), GuideTheme.KUVA, hv)
                    : GuideTheme.withAlpha(GuideTheme.EDGE, 0x70));
            if (navLockAt[i] != 0L) {
                GuideTheme.lockBrackets(g, bx - 2, navY - 2, NAV_W + 4, NAV_H + 4,
                        (rc.now - navLockAt[i]) / GuideTheme.LOCK_MS, GuideTheme.KUVA);
            }
            int glyph = !on ? GuideTheme.withAlpha(GuideTheme.FAINT, 0x90)
                    : GuideTheme.lerpColor(GuideTheme.ASH, GuideTheme.BONE, hv);
            int cx = bx + NAV_W / 2;
            int cy = navY + NAV_H / 2;
            if (scale) {
                GuideTheme.scaleGlyph(g, cx, cy, glyph);
            } else if (anim) {
                // 关着的时候星是暗的、斜杠用赤毒色提示「已关」
                GuideTheme.animGlyph(g, cx, cy, animOn ? glyph : GuideTheme.withAlpha(glyph, 0x90), GuideTheme.KUVA, animOn);
            } else if (i == 2) {
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
        g.drawString(this.font, footerHint, panelX + GuideTheme.PAD, ty, GuideTheme.FAINT_TEXT, false);
    }

    // ==================== 输入 ====================

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (closingAt != 0L) {
            // 单按的修饰键、F1~F12 不算：物品上按住 Ctrl 查阅后常常一直按着，按住不放的键会不停发重复事件，一收场就会被立刻打断
            if (passiveKey(key)) {
                return false;
            }
            // 收场期间按任意键：直接关完，这个键交给游戏（关完回到游戏时 W 照样走、E 照样开物品栏）；
            // Esc 例外 —— 交出去会打开暂停菜单
            finishClose();
            return key == GLFW.GLFW_KEY_ESCAPE;
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
        // 开场中按别的键：动画跳到终态，这个键照常处理（F1~F12、单按修饰键不跳）；正在演开框和擦出的通讯也直接进静止态
        if (!passiveKey(key)) {
            skipIntro();
            CommMemory.finishAll();
        }
        // Ctrl − / 小键盘 −：字小一档；Ctrl = / 小键盘 +：字大一档；Ctrl 0：回到自动。搜索框聚焦时同样生效（Ctrl 组合不会上屏字）。
        // 查阅时带进来、还没松开过的 Ctrl 不算（scaleCtrlDown）
        if (scaleCtrlDown() && !hasAltDown() && minecraft != null) {
            if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
                if (GuideScale.step(minecraft, -1)) {
                    rescale();
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
                if (GuideScale.step(minecraft, 1)) {
                    rescale();
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_0 || key == GLFW.GLFW_KEY_KP_0) {
                if (GuideScale.reset(minecraft)) {
                    rescale();
                }
                return true;
            }
        }
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
            // 输入法直接上屏（不经过 keyPressed）也算按键：直接关完
            finishClose();
            return true;
        }
        // 输入法直接上屏的字不经过 keyPressed：这里也要跳过开场、让通讯直接显示全文
        skipIntro();
        CommMemory.finishAll();
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
            // 收场期间点一下：直接关完（这一下不交给游戏，免得关书的同时打了一下）
            finishClose();
            return true;
        }
        // 开场中点一下：动画跳到终态，这一下照常处理（不吞掉）。面板全程不平移，
        // 终态下的命中区就是玩家看着的位置；还没扫出来的格子此刻也一并成形完毕。
        // 面板展开过半之前例外：只跳过，不当成点到了什么
        boolean early = introEarly();
        skipIntro();
        if (page == Page.READ && reader.contains(mx, my)) {
            // 点在阅读栏里：正在演开框和擦出的通讯直接进静止态，这一下照常处理（通讯框里没有可点的东西）
            CommMemory.finishAll();
        }
        if (early) {
            return true;
        }
        long now = Util.getMillis();
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            back();
            return true;
        }
        // 开场动画开关：左键右键都是切换
        if ((button == 0 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) && GuideTheme.hit(mx, my, animX, navY, NAV_W, NAV_H)) {
            if (searchBox.isFocused()) {
                unfocusSearch();
            }
            GuideDisplayConfig.setIntroAnimation(!GuideDisplayConfig.introAnimation());
            click();
            return true;
        }
        // 「Aa」：左键字小一档、右键字大一档（到头了就不动）；右键在这里不是后退
        if ((button == 0 || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) && minecraft != null
                && GuideTheme.hit(mx, my, scaleX, navY, NAV_W, NAV_H)) {
            if (searchBox.isFocused()) {
                unfocusSearch();
            }
            if (GuideScale.step(minecraft, button == 0 ? -1 : 1)) {
                rescale();
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !GuideTheme.hit(mx, my, searchX, searchY, searchW, 18)) {
            // 右键也是后退（和退格、鼠标侧键、Alt+← 一样）；搜索框上的右键留给输入框
            if (searchBox.isFocused()) {
                unfocusSearch();
            }
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
                        openHit(row.resultIndex);
                    } else {
                        open(row.entry, 0f);
                    }
                }
                return true;
            }
            Link link = rc.nextHoverLink;
            // 只有条目链接能点；物品链接只出提示
            if (link != null && link.entryKey != null && reader.contains(mx, my)) {
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
            finishClose();
            return true;
        }
        boolean early = introEarly();
        skipIntro();
        if (early) {
            return true;
        }
        // Ctrl + 滚轮：向上字大一档、向下字小一档，这时不滚动正文。
        // 查阅打开时还按着的 Ctrl 不算（scaleCtrlDown）：松开之前滚轮照常滚正文
        if (scaleCtrlDown() && minecraft != null) {
            if (delta != 0 && GuideScale.step(minecraft, delta > 0 ? 1 : -1)) {
                rescale();
            }
            return true;
        }
        if (page == Page.HOME) {
            return home.mouseScrolled(delta) || super.mouseScrolled(mx, my, delta);
        }
        if (sidebar.mouseScrolled(mx, my, delta) || reader.mouseScrolled(mx, my, delta)) {
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    /**
     * 从生存物品栏查阅时，物品栏 2×2 合成结果格的快照。
     *
     * <p>setScreen(指南) 会调物品栏的 removed()，原版 {@code InventoryMenu#removed} 在客户端也会清空结果格
     * （把合成格的东西退回背包那一步才只在服务端做）。服务端那份结果没动、也不会重发，于是回到物品栏时结果格显示是空的 ——
     * 点它照样能取出来，但看上去像是配方没了。指南关掉时放回去。</p>
     *
     * <p>只在玩家还是同一个（没有重生、换维度）、结果格还空着、合成格和拍快照时一样时才放：
     * 指南开着的时候服务端改过合成格的话，新的格子和结果会同步过来，这时不该拿旧的盖掉。</p>
     */
    private record CraftResult(LocalPlayer player, ItemStack result, ItemStack[] grid) {

        @Nullable
        static CraftResult capture(@Nullable Screen parent) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (!(parent instanceof InventoryScreen) || player == null) {
                return null;
            }
            InventoryMenu menu = player.inventoryMenu;
            ItemStack result = menu.getSlot(InventoryMenu.RESULT_SLOT).getItem();
            if (result.isEmpty()) {
                return null;
            }
            ItemStack[] grid = new ItemStack[InventoryMenu.CRAFT_SLOT_END - InventoryMenu.CRAFT_SLOT_START];
            for (int i = 0; i < grid.length; i++) {
                grid[i] = menu.getSlot(InventoryMenu.CRAFT_SLOT_START + i).getItem().copy();
            }
            return new CraftResult(player, result.copy(), grid);
        }

        void restore() {
            if (Minecraft.getInstance().player != player) {
                return;
            }
            InventoryMenu menu = player.inventoryMenu;
            Slot slot = menu.getSlot(InventoryMenu.RESULT_SLOT);
            if (!slot.getItem().isEmpty()) {
                return;
            }
            for (int i = 0; i < grid.length; i++) {
                if (!ItemStack.matches(grid[i], menu.getSlot(InventoryMenu.CRAFT_SLOT_START + i).getItem())) {
                    return;
                }
            }
            slot.set(result.copy());
        }
    }
}
