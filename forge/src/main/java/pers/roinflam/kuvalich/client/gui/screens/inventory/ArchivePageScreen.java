package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.lich.SpacedText;
import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import pers.roinflam.kuvalich.mixin.client.SlotPositionAccessor;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 安魂系列方块界面的底子：照玄骸档案的样子铺满整屏、不画外框，像是档案的另一页
 *
 * <ul>
 *   <li>压暗 85% + 墨烟，两条竖分隔线把屏幕分成左、中、右三栏（和档案同一组比例），分隔线内侧带刻度，
 *       整页四角各一个裁切括号；背包在中栏下部，细线方格；</li>
 *   <li>开场沿用档案的时间轴：压暗 0~250 ms，框架 300~600 ms，文字 450~850 ms，背包一行一行淡入；
 *       同一种界面 10 秒内重开、或者关了档案开场选项时，150 ms 一起淡入；</li>
 *   <li>物品图标不能半透明：背景压暗之前格子挪到屏幕外、图标不画（子类的盖板和自己画的图标也要看 {@link #dimmed}），
 *       之后淡入用墨色盖板压在图标上面（z 300）；背包的图标在这一行淡入到一半时直接出来；</li>
 *   <li>槽位坐标每次 init 按窗口大小重排；整屏都算界面里面，点哪儿都不会把手上的东西扔出去；</li>
 *   <li>盯着子类指定的几格，放进、拿走、次数变少时各记一条演出事件；往不收的格子里放东西记一条「拒绝」。</li>
 * </ul>
 *
 * <p>子类在 {@link #layout} 里摆槽位，在 {@link #drawPage} 里画物品底下的东西，在 {@link #drawOverlay}
 * 里画压在物品上面的闪光、盖板。</p>
 */
@OnlyIn(Dist.CLIENT)
public abstract class ArchivePageScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    private static final ResourceLocation SMOKE = new ResourceLocation(Reference.MOD_ID, "textures/gui/archive/smoke.png");
    protected static final ResourceLocation BACKLIGHT = new ResourceLocation(Reference.MOD_ID,
            "textures/gui/archive/backlight.png");

    /** 墨色：格子的底、盖板 */
    protected static final int INK = 0x0C0204;

    /** 开场时间轴（毫秒） */
    protected static final int T_DIM = 250;
    protected static final int T_FRAME_IN = 300;
    protected static final int T_FRAME_DONE = 600;
    protected static final int T_TEXT_IN = 450;
    protected static final int T_TEXT_DONE = 850;
    private static final int T_QUICK = 150;
    private static final long QUICK_REOPEN_MS = 10_000L;
    /** 背包第一行开始淡入的时刻、每行往后错开多少、每行淡入多久 */
    private static final int T_INV_IN = 380;
    private static final int T_INV_STEP = 60;
    private static final int T_INV_LEN = 260;

    /** 压在物品图标上面的那一层的 z（物品画在 150 附近） */
    private static final float OVERLAY_Z = 300.0F;

    /** 每种界面上次关掉的时刻 */
    private static final Map<Class<?>, Long> LAST_CLOSE = new HashMap<>();

    /** 演出事件 */
    protected enum Ev {
        /** 格子里放进了东西（{@code stack} 是放进去的） */
        PLACE,
        /** 格子被拿空（{@code stack} 是拿走的） */
        TAKE,
        /** 同一件东西少了一次次数 */
        CONSUME,
        /** 同一摞东西少了几个（扣材料）；{@code stack} 的数量是少掉的个数 */
        SPEND,
        /** 往不收的格子里放东西 */
        REJECT,
        /** 子类自己的事件（三张放齐、揭示一枚之类），{@code id} 由子类定 */
        CUSTOM
    }

    protected record Fx(Ev type, int id, ItemStack stack, long t0) {
    }

    /** 悬停有提示 / 能点的一块 */
    protected record Hot(float x0, float y0, float x1, float y1, @Nullable Supplier<List<Component>> tip,
                         @Nullable Runnable click) {
        boolean contains(double mx, double my) {
            return mx >= x0 && mx < x1 && my >= y0 && my < y1;
        }
    }

    /** 放进东西时从格子中心扩出去的一圈 */
    private record Ring(float x, float y, long t0, int rgb) {
    }

    /** 冲击环扩完的时长 */
    private static final int RING_MS = 420;
    /** 「解码」出字时乱码的取字表：汉字换汉字、西文换西文，宽度不跳 */
    private static final String GLYPH_CJK = "赤毒骸魂安铸融械库模组扉灭档案玄卡锻";
    private static final String GLYPH_ASCII = "#%&*+=<>/?0123456789ABCDEFX";
    protected static final String ELLIPSIS = "…";

    private static final class Mote {
        float x;
        float y;
        float vx;
        float vy;
        float g;
        long t0;
        float life;
        int rgb;
    }

    // ==================== 版式（init 时按窗口算） ====================

    /** 版式至少要这么高；界面比它矮时整页换一个小一档的整数缩放来画 */
    private static final int MIN_CANVAS_H = 240;
    /**
     * 整页相对窗口界面缩放再乘的倍数，1 = 不缩。强制 Unicode 字体时原版把奇数档缩放往上凑成偶数，
     * 1280×720、1366×768 这类窗口会变成 4 档、界面只有 180~192 高，三栏和背包挤成一团；
     * 这时改按 {@code 物理高度 / 240} 取整那一档画（每个单位仍是整数个物理像素），鼠标坐标同样换算
     */
    private float canvas = 1.0F;
    /** 窗口高不到 260 个界面单位：间距收紧 */
    protected boolean small;
    /** 两条竖分隔线、它们的上下端 */
    protected int sepL;
    protected int sepR;
    protected int sepT;
    protected int sepB;
    /** 左栏、右栏的左右边，中栏和左栏的中线 */
    protected int lx0;
    protected int lx1;
    protected int rx0;
    protected int rx1;
    protected int cx;
    protected int lcx;
    /** 栏标题下划线、标题的 y；底部一行字的 y */
    protected int hairY;
    protected int titleY;
    protected int botY;
    /** 背包方格左上角（格子外沿） */
    protected int gx;
    protected int gy;

    // ==================== 状态 ====================

    protected long openClock;
    protected boolean quick;
    private boolean started;
    /** 开场时藏起来的格子都已放回（之后不再每帧摆格子）；藏起来之前各格的位置 */
    private boolean slotsShown;
    private int[] homeX = new int[0];
    private int[] homeY = new int[0];
    private static final int PARKED = -10000;

    private final List<Fx> fxs = new ArrayList<>();
    private final List<Hot> hots = new ArrayList<>();
    private final List<Mote> motes = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    /** 画弧、椭圆用的点缓冲，折线取点的结果 */
    private final float[] shapePts = new float[2 * 72];
    private final float[] at = new float[2];
    private final Random random = new Random();

    private int[] tracked = new int[0];
    @Nullable
    private ItemStack[] snapshot;

    private Component message = Component.empty();
    private int messageRgb;
    private long messageAt = Long.MIN_VALUE / 4;
    private int messageMs;

    protected ArchivePageScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    // ==================== 子类填的 ====================

    /** 摆槽位、算子类自己的版式（每次 init 都调） */
    protected abstract void layout();

    /** 画物品图标底下的一切（renderBg 时调，背景、分隔线、背包方格已经画好） */
    protected abstract void drawPage(GuiGraphics g, int mx, int my, long now, long t, float eFrame);

    /** 画压在物品图标上面的东西（闪光、盖板）；坐标同 drawPage */
    protected void drawOverlay(GuiGraphics g, int mx, int my, long now, long t) {
    }

    /** 一轮扫描找出了事件之后（三张放齐这类组合事件在这里判） */
    protected void afterScan(long now) {
    }

    /** 往格子里放了它不收的东西时提示什么 */
    protected Component rejectMessage(Slot slot, ItemStack carried) {
        return Component.empty();
    }

    /** 盯哪几格（菜单里的槽位下标） */
    protected void track(int... slots) {
        tracked = slots.clone();
        snapshot = null;
    }

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        fitCanvas();
        this.imageWidth = this.width;
        this.imageHeight = this.height;
        super.init();
        if (!started) {
            started = true;
            openClock = KuvaFxDirector.clockMs();
            long last = LAST_CLOSE.getOrDefault(getClass(), Long.MIN_VALUE / 4);
            quick = !EffectsDisplayConfig.archiveIntro() || Util.getMillis() - last < QUICK_REOPEN_MS;
        }
        computeColumns();
        layout();
    }

    @Override
    public void removed() {
        super.removed();
        LAST_CLOSE.put(getClass(), Util.getMillis());
    }

    /** 界面太矮时算出整页的缩放倍数，并把 width / height 换成缩放后的画布尺寸（每次 init 都从窗口尺寸重算） */
    private void fitCanvas() {
        canvas = 1.0F;
        Window win = this.minecraft.getWindow();
        double gui = win.getGuiScale();
        if (this.height >= MIN_CANVAS_H || gui <= 1.0) {
            return;
        }
        int k = Math.max(1, win.getHeight() / MIN_CANVAS_H);
        if (k >= gui) {
            return;
        }
        canvas = (float) (k / gui);
        this.width = Mth.ceil(win.getWidth() / (double) k);
        this.height = win.getHeight() / k;
    }

    /** 窗口界面坐标换成画布坐标 */
    private double toCanvas(double v) {
        return canvas == 1.0F ? v : v / canvas;
    }

    /**
     * 裁剪框（画布坐标）。原版 {@code enableScissor} 只乘窗口的界面缩放、不认姿态矩阵，画布缩小时会裁错地方，
     * 所以这里自己按物理像素算
     */
    protected void clip(GuiGraphics g, float x0, float y0, float x1, float y1) {
        g.flush();
        Window win = this.minecraft.getWindow();
        double f = win.getGuiScale() * canvas;
        int px0 = Mth.floor(x0 * f);
        int py0 = Mth.floor(y0 * f);
        int px1 = Mth.ceil(x1 * f);
        int py1 = Mth.ceil(y1 * f);
        RenderSystem.enableScissor(px0, win.getHeight() - py1, Math.max(0, px1 - px0), Math.max(0, py1 - py0));
    }

    protected void unclip(GuiGraphics g) {
        g.flush();
        RenderSystem.disableScissor();
    }

    /** 中栏要的宽度：背包方格 162 加两边各 11；五个界面中栏的东西都不比背包宽 */
    protected static final int MIDDLE_W = 184;

    /**
     * 右边那道竖分隔线占屏宽的比例，右栏从它右边 0.025 屏宽开始。中栏只要放下背包方格，多出来的宽度都让给右栏
     * （模组名、属性名、卡和配方的说明都长，右栏窄了只能截断或折得很碎）；宽屏下最多挪到 0.66，免得右栏的行拉得太长
     */
    protected float rightSeparator() {
        float want = (Math.round(width * 0.29F) + MIDDLE_W) / (float) width;
        return Mth.clamp(want, 0.66F, 0.75F);
    }

    private void computeColumns() {
        int w = this.width;
        int h = this.height;
        small = h < 260;
        sepL = Math.round(w * 0.29F);
        sepR = Math.round(w * rightSeparator());
        // 两条分隔线之间是奇数宽时中线落在半格上，背包只能偏一格；右线往左挪一格，背包两边离分隔线一样远
        if (((sepL + sepR) & 1) != 0) {
            sepR--;
        }
        lx0 = Math.round(w * 0.077F);
        lx1 = Math.round(w * 0.265F);
        rx0 = sepR + Math.round(w * 0.025F);
        rx1 = Math.round(w * 0.93F);
        cx = Math.round((sepL + sepR) * 0.5F);
        lcx = Math.round((lx0 + lx1) * 0.5F);
        sepT = Math.round(h * 0.08F);
        sepB = Math.round(h * 0.88F);
        hairY = Math.round(h * 0.087F);
        titleY = hairY - 11;
        gx = cx - 81;
        gy = sepB - 76;
        botY = sepB + 6;
    }

    /**
     * 中栏背包以上那一块（卡槽、炉膛、状态行）的竖直位置：在栏标题线和背包上面那道线之间居中，
     * 上沿不高过 {@code minTop}，最下面一道线离背包线至少空 3 格；居中的位置离 {@code snapY}（左右两栏第一行字）
     * 不到 6 格时干脆对齐过去，差几格看着像排歪了。以前一律贴着标题线排，大屏上中栏下面空出一百多格
     *
     * @param blockH 块顶到最下面一道线的距离
     * @param snapY  要对齐的 y，不对齐给负数
     * @return 块顶的 y
     */
    protected int middleTop(int blockH, int minTop, int snapY) {
        int maxTop = gy - 9 - blockH;
        int top = hairY + Math.round((gy - 5 - hairY - blockH - 1) * 0.5F);
        if (snapY >= 0 && Math.abs(top - snapY) <= 6) {
            top = snapY;
        }
        return Math.max(minTop, Math.min(top, maxTop));
    }

    /** 把一格摆到 (x, y)（物品图标左上角，屏幕坐标） */
    protected static void place(Slot slot, int x, int y) {
        SlotPositionAccessor a = (SlotPositionAccessor) slot;
        a.kuvalich$setX(x);
        a.kuvalich$setY(y);
    }

    /** 玩家背包摆成 3 行 + 隔一道缝的快捷栏，(gx, gy) 是方格外沿左上角 */
    protected void placeInventory() {
        for (Slot s : menu.slots) {
            if (inventoryRow(s) >= 0) {
                place(s, inventoryX(s), inventoryY(s));
            }
        }
    }

    /** 背包格在排版里的位置（物品图标左上角）；开场时格子本身可能被挪到屏幕外，画方格按这个 */
    private int inventoryX(Slot s) {
        int c = s.getContainerSlot();
        return gx + 1 + 18 * (c < 9 ? c : (c - 9) % 9);
    }

    private int inventoryY(Slot s) {
        int c = s.getContainerSlot();
        return c < 9 ? gy + 59 : gy + 1 + 18 * ((c - 9) / 9);
    }

    /**
     * 开场时物品图标先不画：格子挪到屏幕外，到点再放回 layout 摆好的位置。图标不能半透明，以前是压一块不透明的墨色盖板，
     * 可背景还没压暗时亮处看着就是几块黑斑。背包一行淡入到一半时图标出来；别的格子等背景压暗（{@link #dimmed}）以后才放回，
     * 之后各页自己的盖板再从实到虚（那时底下已经是暗的）
     */
    private void hideSlotsDuringIntro(long t) {
        if (slotsShown) {
            return;
        }
        int n = menu.slots.size();
        if (homeX.length != n) {
            homeX = new int[n];
            homeY = new int[n];
        }
        boolean all = true;
        for (int i = 0; i < n; i++) {
            Slot s = menu.slots.get(i);
            if (s.x != PARKED) {
                // layout 摆过的位置（重排过就是新位置）
                homeX[i] = s.x;
                homeY[i] = s.y;
            }
            int row = inventoryRow(s);
            boolean show = row >= 0 ? rowPhase(t, row) >= 0.5F : dimmed(t);
            if (show) {
                if (s.x == PARKED) {
                    place(s, homeX[i], homeY[i]);
                }
            } else {
                place(s, PARKED, PARKED);
                all = false;
            }
        }
        slotsShown = all;
    }

    /** 开场时背景已经压暗了：在这之前别画物品图标，也别画压在图标上的墨色盖板 */
    protected boolean dimmed(long t) {
        return quick ? lin(t) >= 1.0F : t >= T_DIM;
    }

    /** 背包格子在第几行（快捷栏算第 3 行），不是背包格返回 −1 */
    private static int inventoryRow(Slot s) {
        if (!(s.container instanceof Inventory)) {
            return -1;
        }
        int c = s.getContainerSlot();
        return c < 9 ? 3 : c < 36 ? (c - 9) / 9 : -1;
    }

    /** 整屏都算界面里面：不因为点在格子外面就把手上的东西扔掉（要扔用丢弃键） */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        return false;
    }

    /** 原版的白色悬停高亮不要，悬停由各页自己画在图标底下 */
    @Override
    public int getSlotColor(int index) {
        return 0;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        long now = KuvaFxDirector.clockMs();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, OVERLAY_Z);
        drawOverlay(g, mx, my, now, now - openClock);
        drawRings(g, now);
        drawMotes(g, now);
        g.flush();
        g.pose().popPose();
    }

    /**
     * 绘制入口：画布缩小时整页（连同物品、提示框）套一层缩放，鼠标按画布坐标重新取；子类要加东西覆写 {@link #renderPage}，
     * 这样加的东西也在缩放里
     */
    @Override
    public final void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (canvas == 1.0F) {
            renderPage(g, mx, my, partialTick);
            return;
        }
        Window win = this.minecraft.getWindow();
        int vx = (int) (this.minecraft.mouseHandler.xpos() * this.width / win.getScreenWidth());
        int vy = (int) (this.minecraft.mouseHandler.ypos() * this.height / win.getScreenHeight());
        g.pose().pushPose();
        g.pose().scale(canvas, canvas, 1.0F);
        renderPage(g, vx, vy, partialTick);
        g.flush();
        g.pose().popPose();
    }

    protected void renderPage(GuiGraphics g, int mx, int my, float partialTick) {
        hots.clear();
        super.render(g, mx, my, partialTick);
        this.renderTooltip(g, mx, my);
        if (this.hoveredSlot == null && this.menu.getCarried().isEmpty()) {
            Hot h = hotAt(mx, my);
            if (h != null && h.tip() != null) {
                List<Component> lines = h.tip().get();
                if (!lines.isEmpty()) {
                    g.renderComponentTooltip(this.font, lines, mx, my);
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        long now = KuvaFxDirector.clockMs();
        long t = now - openClock;
        scan(now);
        hideSlotsDuringIntro(t);
        float eDim = quick ? lin(t) : easeOut(seg(t, 0, T_DIM));
        float eFrame = phase(t, T_FRAME_IN, T_FRAME_DONE);
        g.fill(0, 0, width, height, GuiShapes.argb(0x000000, 0.85F * eDim));
        drawSmoke(g, width, height, now, 0.4F * eDim);
        // 两条分隔线从中间往上下长出来，长的时候端头亮一点
        float grow = quick ? 1.0F : easeOut(seg(t, T_FRAME_IN - 80, T_FRAME_DONE + 120));
        float mid = (sepT + sepB) * 0.5F;
        float half = (sepB - sepT) * 0.5F * grow;
        GuiShapes.begin(g);
        int sep = GuiShapes.argb(FxPalette.KUVA_PALE, 0.12F * eFrame);
        GuiShapes.vline(g, sepL, mid - half, mid + half, lw(), sep);
        GuiShapes.vline(g, sepR, mid - half, mid + half, lw(), sep);
        GuiShapes.end();
        if (grow > 0.0F && grow < 1.0F) {
            float tip = 0.8F * (1.0F - grow);
            additive(g, () -> {
                for (int x : new int[]{sepL, sepR}) {
                    fill(g, x - 1, mid - half - 1, x + 1, mid - half + 1, FxPalette.KUVA_PALE, tip);
                    fill(g, x - 1, mid + half - 1, x + 1, mid + half + 1, FxPalette.KUVA_PALE, tip);
                }
            });
        }
        drawRulers(g, now, eFrame);
        drawInventory(g, mx, my, t);
        drawPage(g, mx, my, now, t, eFrame);
        drawOpenBeam(g, t);
        g.flush();
    }

    /**
     * 分隔线内侧的刻度（每 8 格一短刻、每 32 格一长刻），一颗亮刻度沿两条分隔线慢慢来回走；
     * 整页四角各一个直角括号，像档案扫描件的裁切标
     */
    private void drawRulers(GuiGraphics g, long now, float e) {
        if (e <= 0.0F) {
            return;
        }
        GuiShapes.begin(g);
        for (int y = sepT + 8, k = 1; y < sepB - 2; y += 8, k++) {
            boolean major = k % 4 == 0;
            float len = major ? 3.0F : 1.5F;
            float a = (major ? 0.16F : 0.08F) * e;
            hl(g, sepL + 1, sepL + 1 + len, y, FxPalette.KUVA_PALE, a);
            hl(g, sepR - len, sepR, y, FxPalette.KUVA_PALE, a);
        }
        // 左右对称：右栏边（0.93 屏宽）到屏右比左栏边（0.077 屏宽）到屏左窄 3~5 格，按窄的那边定，左括号跟着往外挪
        int x1 = rx1 + 12;
        int x0 = width - x1;
        int y0 = Math.max(2, titleY - 7);
        int y1 = Math.min(height - 3, botY + 15);
        float c = 7.0F;
        float a = 0.2F * e;
        hl(g, x0, x0 + c, y0, FxPalette.KUVA_PALE, a);
        vl(g, x0, y0, y0 + c, FxPalette.KUVA_PALE, a);
        hl(g, x1 - c, x1, y0, FxPalette.KUVA_PALE, a);
        vl(g, x1, y0, y0 + c, FxPalette.KUVA_PALE, a);
        hl(g, x0, x0 + c, y1, FxPalette.KUVA_PALE, a);
        vl(g, x0, y1 - c, y1, FxPalette.KUVA_PALE, a);
        hl(g, x1 - c, x1, y1, FxPalette.KUVA_PALE, a);
        vl(g, x1, y1 - c, y1, FxPalette.KUVA_PALE, a);
        GuiShapes.end();
        // 亮刻度：9 秒一个来回，两条线错开半程
        float span = sepB - sepT - 12;
        additive(g, () -> {
            for (int side = 0; side < 2; side++) {
                float u = ((now + side * 4500L) % 9000L) / 9000.0F;
                float y = sepT + 6 + span * sine(u < 0.5F ? u * 2.0F : 2.0F - u * 2.0F);
                float x = side == 0 ? sepL + 1 : sepR - 4;
                fill(g, x, y, x + 4, y + 1, FxPalette.KUVA, 0.45F * e);
            }
        });
    }

    /** 开场时一道扫描光从上往下扫过整页（快速开场不扫） */
    private void drawOpenBeam(GuiGraphics g, long t) {
        if (quick || t < T_DIM - 60 || t > T_DIM + 520) {
            return;
        }
        float u = seg(t, T_DIM - 60, T_DIM + 520);
        float y = height * inOut(u);
        float a = (float) Math.sin(u * Math.PI);
        additive(g, () -> {
            GuiShapes.hline(g, 0, width, y, lw() * 4, GuiShapes.argb(FxPalette.KUVA, 0.22F * a));
            GuiShapes.hline(g, 0, width, y, lw(), GuiShapes.argb(FxPalette.KUVA_PALE, 0.55F * a));
        });
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx = toCanvas(mx);
        my = toCanvas(my);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.menu.getCarried().isEmpty()) {
            Hot h = hotAt(mx, my);
            if (h != null && h.click() != null && slotAt(mx, my) == null) {
                h.click().run();
                return true;
            }
        }
        Slot s = slotAt(mx, my);
        ItemStack carried = this.menu.getCarried();
        if (s != null && !carried.isEmpty() && isTracked(s.index) && !s.mayPlace(carried)) {
            event(Ev.REJECT, s.index, carried.copy(), 0L);
            message(rejectMessage(s, carried), FxPalette.KUVA, 1600);
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return super.mouseReleased(toCanvas(mx), toCanvas(my), button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return super.mouseDragged(toCanvas(mx), toCanvas(my), button, toCanvas(dx), toCanvas(dy));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (scrollPage(toCanvas(mx), toCanvas(my), delta)) {
            return true;
        }
        return super.mouseScrolled(toCanvas(mx), toCanvas(my), delta);
    }

    /** 页面自己的滚动区（画布坐标）；吃掉了返回 true */
    protected boolean scrollPage(double mx, double my, double delta) {
        return false;
    }

    @Override
    public void mouseMoved(double mx, double my) {
        super.mouseMoved(toCanvas(mx), toCanvas(my));
    }

    /** 鼠标下面的格子（按坐标现找，不靠上一帧的悬停） */
    @Nullable
    protected Slot slotAt(double mx, double my) {
        for (Slot s : menu.slots) {
            if (s.isActive() && isHovering(s.x, s.y, 16, 16, mx, my)) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    private Hot hotAt(double mx, double my) {
        for (int i = hots.size() - 1; i >= 0; i--) {
            if (hots.get(i).contains(mx, my)) {
                return hots.get(i);
            }
        }
        return null;
    }

    /** 加一块悬停提示 / 可点区域（每帧在 drawPage 里重新登记） */
    protected void hot(float x0, float y0, float x1, float y1, @Nullable Supplier<List<Component>> tip,
                       @Nullable Runnable click) {
        hots.add(new Hot(x0, y0, x1, y1, tip, click));
    }

    // ==================== 事件 ====================

    private boolean isTracked(int index) {
        for (int i : tracked) {
            if (i == index) {
                return true;
            }
        }
        return false;
    }

    /**
     * 每帧比一次盯着的格子：空 → 有是放进，有 → 空是拿走，同一件东西耐久掉了是用掉一次，换了东西算拿走 + 放进。
     * 同一帧里放进了好几格（创造模式一键补全）时依次错开 90 ms
     */
    private void scan(long now) {
        if (snapshot == null || snapshot.length != tracked.length) {
            snapshot = new ItemStack[tracked.length];
            for (int k = 0; k < tracked.length; k++) {
                snapshot[k] = menu.getSlot(tracked[k]).getItem().copy();
            }
            return;
        }
        int stagger = 0;
        for (int k = 0; k < tracked.length; k++) {
            ItemStack cur = menu.getSlot(tracked[k]).getItem();
            ItemStack old = snapshot[k];
            if (ItemStack.matches(cur, old)) {
                continue;
            }
            int id = tracked[k];
            if (old.isEmpty()) {
                event(Ev.PLACE, id, cur.copy(), 90L * stagger++);
            } else if (cur.isEmpty()) {
                event(Ev.TAKE, id, old, 0L);
            } else if (ItemStack.isSameItem(old, cur)) {
                // 同一件东西原地改了数据（模组写进台上的武器、卡用掉一次、扣材料）：不算拿走再放
                if (cur.getDamageValue() > old.getDamageValue()) {
                    event(Ev.CONSUME, id, cur.copy(), 0L);
                } else if (cur.getCount() < old.getCount()) {
                    event(Ev.SPEND, id, old.copyWithCount(old.getCount() - cur.getCount()), 0L);
                } else if (cur.getCount() > old.getCount()) {
                    // 往同一摞上又添了几个，照放进算
                    event(Ev.PLACE, id, cur.copy(), 90L * stagger++);
                }
            } else {
                event(Ev.TAKE, id, old, 0L);
                event(Ev.PLACE, id, cur.copy(), 90L * stagger++);
            }
            snapshot[k] = cur.copy();
        }
        afterScan(now);
    }

    protected void event(Ev type, int id, ItemStack stack, long delayMs) {
        fxs.add(new Fx(type, id, stack, KuvaFxDirector.clockMs() + delayMs));
        if (fxs.size() > 160) {
            fxs.subList(0, fxs.size() - 160).clear();
        }
        if (type == Ev.PLACE && id >= 0 && id < menu.slots.size()) {
            Slot s = menu.getSlot(id);
            int rarity = ForgeOpClassifier.rarity(stack);
            ring(s.x + 8, s.y + 8, rarity >= 0 ? GuiFx.rarityColor(rarity) : FxPalette.KUVA_PALE, delayMs);
        }
        onEvent(type, id, stack, delayMs);
    }

    /** 在 (x, y) 放一圈往外扩的冲击环（放进东西时底子自己放，子类的大事件也可以用） */
    protected void ring(float x, float y, int rgb, long delayMs) {
        rings.add(new Ring(x, y, KuvaFxDirector.clockMs() + delayMs, rgb));
    }

    /** 事件刚记下时（放粒子之类） */
    protected void onEvent(Ev type, int id, ItemStack stack, long delayMs) {
    }

    /** 最近一次这种事件（同一个 id）过去多久；没有、或者还没轮到返回很大的数 */
    protected long age(Ev type, int id) {
        long now = KuvaFxDirector.clockMs();
        for (int i = fxs.size() - 1; i >= 0; i--) {
            Fx f = fxs.get(i);
            if (f.type() == type && f.id() == id && now >= f.t0()) {
                return now - f.t0();
            }
        }
        return Long.MAX_VALUE / 4;
    }

    /** 有没有排在后面、还没开始的这种事件 */
    protected boolean pending(Ev type, int id) {
        long now = KuvaFxDirector.clockMs();
        for (int i = fxs.size() - 1; i >= 0; i--) {
            Fx f = fxs.get(i);
            if (f.type() == type && f.id() == id && f.t0() > now) {
                return true;
            }
        }
        return false;
    }

    /** 最近一次已经开始的这种事件 */
    @Nullable
    protected Fx last(Ev type, int id) {
        long now = KuvaFxDirector.clockMs();
        for (int i = fxs.size() - 1; i >= 0; i--) {
            Fx f = fxs.get(i);
            if (f.type() == type && f.id() == id && now >= f.t0()) {
                return f;
            }
        }
        return null;
    }

    /** 最近一次放进任何盯着的格子的事件（右栏没悬停时看它） */
    @Nullable
    protected Fx lastPlace() {
        long now = KuvaFxDirector.clockMs();
        for (int i = fxs.size() - 1; i >= 0; i--) {
            Fx f = fxs.get(i);
            if (f.type() == Ev.PLACE && now >= f.t0()) {
                return f;
            }
        }
        return null;
    }

    /** 中栏状态行临时换成一句提示 */
    protected void message(Component text, int rgb, int ms) {
        if (text.getString().isEmpty()) {
            return;
        }
        message = text;
        messageRgb = rgb;
        messageAt = KuvaFxDirector.clockMs();
        messageMs = ms;
    }

    /** 状态行的提示还在显示时画出来，返回画了没有 */
    protected boolean drawMessage(GuiGraphics g, float x, float y, float a) {
        long age = KuvaFxDirector.clockMs() - messageAt;
        if (age < 0 || age > messageMs) {
            return false;
        }
        textC(g, message.getString(), x, y, messageRgb, a * Math.min(1.0F, (messageMs - age) / 300.0F));
        return true;
    }

    // ==================== 背包 ====================

    /** 背包：墨底 + 细线方格，开场一行一行淡入；物品由原版画在上面，淡入到一半才放出来（{@link #hideSlotsDuringIntro}） */
    private void drawInventory(GuiGraphics g, int mx, int my, long t) {
        float eLine = phase(t, T_INV_IN, T_INV_IN + 320);
        GuiShapes.begin(g);
        int lineA = GuiShapes.argb(FxPalette.KUVA_PALE, 0.22F * eLine);
        int clear = lineA & 0x00FFFFFF;
        GuiShapes.hlineFade(g, gx, gx + 81, gy - 5, lw(), clear, lineA);
        GuiShapes.hlineFade(g, gx + 81, gx + 162, gy - 5, lw(), lineA, clear);
        for (Slot s : menu.slots) {
            int row = inventoryRow(s);
            if (row < 0) {
                continue;
            }
            float e = rowPhase(t, row);
            if (e <= 0.0F) {
                continue;
            }
            int x = inventoryX(s);
            int y = inventoryY(s);
            boolean hv = isHovering(x, y, 16, 16, mx, my);
            fill(g, x - 1, y - 1, x + 17, y + 17, INK, 0.6F * e);
            if (hv) {
                fill(g, x - 1, y - 1, x + 17, y + 17, FxPalette.KUVA_PALE, 0.1F);
            }
            GuiShapes.frame(g, x - 1, y - 1, x + 17, y + 17, lw(),
                    GuiShapes.argb(FxPalette.KUVA_PALE, (hv ? 0.55F : 0.1F) * e));
        }
        GuiShapes.end();
    }

    private float rowPhase(long t, int row) {
        int a = T_INV_IN + T_INV_STEP * row;
        return phase(t, a, a + T_INV_LEN);
    }

    // ==================== 微粒 ====================

    /** 往 (x, y) 放一把小方块粒子：{@code ang} 朝向（弧度，−π/2 朝上），{@code spread} 散开的角度 */
    protected void burst(float x, float y, int n, float ang, float spread, float speed, float life, float gravity,
                         int rgb, long delayMs) {
        long now = KuvaFxDirector.clockMs();
        for (int k = 0; k < n; k++) {
            Mote m = new Mote();
            float a = ang + (random.nextFloat() - 0.5F) * spread;
            float sp = speed * (0.5F + random.nextFloat());
            m.x = x;
            m.y = y;
            m.vx = (float) Math.cos(a) * sp;
            m.vy = (float) Math.sin(a) * sp;
            m.g = gravity;
            m.t0 = now + delayMs;
            m.life = life * (0.7F + random.nextFloat() * 0.6F);
            m.rgb = rgb;
            motes.add(m);
        }
    }

    private void drawRings(GuiGraphics g, long now) {
        if (rings.isEmpty()) {
            return;
        }
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        float px = GuiShapes.pixels(g, lw());
        for (int i = rings.size() - 1; i >= 0; i--) {
            Ring r = rings.get(i);
            long t = now - r.t0();
            if (t < 0) {
                continue;
            }
            if (t > RING_MS) {
                rings.remove(i);
                continue;
            }
            float u = t / (float) RING_MS;
            float fade = (1.0F - u) * (1.0F - u);
            // 外圈快、粗；内圈晚 60 ms、细
            GuiShapes.ring(g, r.x(), r.y(), 9.0F + 17.0F * easeOut(u), px * 2.0F,
                    GuiShapes.argb(r.rgb(), 0.55F * fade), 1.0F);
            float u2 = clamp01((t - 60) / (float) (RING_MS - 60));
            if (u2 > 0.0F) {
                GuiShapes.ring(g, r.x(), r.y(), 8.0F + 10.0F * easeOut(u2), px,
                        GuiShapes.argb(FxPalette.KUVA_PALE, 0.5F * (1.0F - u2)), 1.0F);
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    private void drawMotes(GuiGraphics g, long now) {
        if (motes.isEmpty()) {
            return;
        }
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (int i = motes.size() - 1; i >= 0; i--) {
            Mote m = motes.get(i);
            float t = now - m.t0;
            if (t < 0) {
                continue;
            }
            if (t > m.life) {
                motes.remove(i);
                continue;
            }
            float s = t / 1000.0F;
            int x = Math.round(m.x + m.vx * s);
            int y = Math.round(m.y + m.vy * s + 0.5F * m.g * s * s);
            fill(g, x, y, x + 1, y + 1, m.rgb, 1.0F - t / m.life);
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    /** 中栏飘着的 8 个赤毒点：在 (cx ± boxW/2, top ~ top + boxH) 里往上飘、淡入淡出 */
    protected void drawDrift(GuiGraphics g, float centerX, float top, float boxW, float boxH, long now, float a) {
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (int i = 0; i < 8; i++) {
            float life = 2000.0F + 2000.0F * hash(i, 7);
            float tt = now + hash(i, 3) * life;
            int cyc = (int) (tt / life);
            float u = (tt - cyc * life) / life;
            int x = Math.round(centerX + (hash(i, cyc * 2 + 11) - 0.5F) * boxW);
            int y = Math.round(top + boxH * (0.15F + 0.85F * hash(i, cyc * 2 + 12)) - u * (life / 1000.0F) * 0.03F * height);
            float alpha = (0.3F + 0.4F * hash(i, cyc + 5)) * (float) Math.sin(u * Math.PI) * a;
            if (alpha > 0.01F) {
                fill(g, x, y, x + 1, y + 1, FxPalette.KUVA, alpha);
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    // ==================== 画法小工具 ====================

    /** 界面细线的物理像素粗细 */
    protected static int lw() {
        return GuiShapes.lineWidthPx();
    }

    protected static void fill(GuiGraphics g, float x0, float y0, float x1, float y1, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        GuiShapes.block(g, x0, y0, x1, y1, GuiShapes.argb(rgb, a));
    }

    protected static void frame(GuiGraphics g, float x0, float y0, float x1, float y1, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        GuiShapes.frame(g, x0, y0, x1, y1, lw(), GuiShapes.argb(rgb, a));
    }

    protected static void hl(GuiGraphics g, float x0, float x1, float y, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        GuiShapes.hline(g, x0, x1, y, lw(), GuiShapes.argb(rgb, a));
    }

    protected static void vl(GuiGraphics g, float x, float y0, float y1, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        GuiShapes.vline(g, x, y0, y1, lw(), GuiShapes.argb(rgb, a));
    }

    /** 从 x0 往 x1 渐隐的下划线 */
    protected static void hlFade(GuiGraphics g, float x0, float x1, float y, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        int c = GuiShapes.argb(rgb, a);
        GuiShapes.hlineFade(g, x0, x1, y, lw(), c, c & 0x00FFFFFF);
    }

    /** 一段加法混合的画法 */
    protected static void additive(GuiGraphics g, Runnable draw) {
        GuiShapes.additive(g, true);
        draw.run();
        GuiShapes.additive(g, false);
    }

    /** 字只落在整界面像素上；α 低于 4/255 时原版会当成不透明，这种就不画 */
    protected float text(GuiGraphics g, String s, float x, float y, int rgb, float a, float spacing) {
        return SpacedText.draw(g, this.font, s, Math.round(x), Math.round(y), spacing, GuiShapes.argb(rgb, a), false);
    }

    protected float text(GuiGraphics g, String s, float x, float y, int rgb, float a) {
        return text(g, s, x, y, rgb, a, 0.0F);
    }

    protected float textW(String s, float spacing) {
        return spacing == 0.0F ? this.font.width(s) : SpacedText.width(this.font, s, spacing);
    }

    protected float textC(GuiGraphics g, String s, float centerX, float y, int rgb, float a) {
        float w = textW(s, 0.0F);
        text(g, s, Math.round(centerX - w * 0.5F), y, rgb, a);
        return w;
    }

    protected float textR(GuiGraphics g, String s, float right, float y, int rgb, float a) {
        float w = textW(s, 0.0F);
        text(g, s, Math.round(right - w), y, rgb, a);
        return w;
    }

    /** 放不下就从尾巴截掉、补一个省略号 */
    protected String fit(String s, float maxW) {
        if (textW(s, 0.0F) <= maxW) {
            return s;
        }
        int n = s.length();
        while (n > 0 && textW(s.substring(0, n) + ELLIPSIS, 0.0F) > maxW) {
            n--;
        }
        return s.substring(0, n) + ELLIPSIS;
    }

    protected static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** 右对齐、右下带一格墨色投影的字（叠在物品图标上的数量） */
    protected float textShadowR(GuiGraphics g, String s, float right, float y, int rgb, float a) {
        float w = textW(s, 0.0F);
        text(g, s, Math.round(right - w) + 1, y + 1, INK, a);
        text(g, s, Math.round(right - w), y, rgb, a);
        return w;
    }

    /**
     * 按宽度断行。纯西文交给原版（在空格处断）；带汉字的自己断：先按分句（逗号、句号这类标点之后，
     * 以及语言文件里预先标好的断点 U+200C）整段往行里排，一行最多比栏宽多出一个字，不从词中间切
     * （「在一切尚未 / 落定之前」那种断法）；一段长到怎么都放不下才另起一行逐字断、几行一样长：
     * 汉字逐字可断，西文单词、数字不拆开，行首不放逗号句号这类标点。
     * U+200C（零宽不连字）在原版 space 字体里宽 0、不画，物品提示里也看不见，没有标点的长句靠它标出词和词的分界。
     * 原版遇到「1 张模组换……」会在数字后的空格处断，剩下一长串汉字再被硬切，窄栏里常常只剩一个数字孤零零一行，
     * 所以空格不算分句
     */
    protected List<String> wrap(String s, float maxW) {
        return wrap(s, maxW, this.font.width("一"));
    }

    /** 同 {@link #wrap(String, float)}，{@code slack} 是一行最多能比栏宽多出多少（0 = 一格也不许多） */
    private List<String> wrap(String s, float maxW, float slack) {
        List<String> out = new ArrayList<>();
        if (!hasCjk(s)) {
            for (FormattedText line : this.font.getSplitter().splitLines(s, Math.max(1, (int) maxW), Style.EMPTY)) {
                out.add(line.getString());
            }
            return out;
        }
        String[] paras = s.split("\n", -1);
        int last = paras[paras.length - 1].isEmpty() && paras.length > 1 ? paras.length - 1 : paras.length;
        for (int p = 0; p < last; p++) {
            StringBuilder line = new StringBuilder();
            for (String clause : clauses(paras[p])) {
                if (this.font.width(clause) > maxW + slack) {
                    // 长到非拆不可：另起一行，拆成的几行一样长，不在下一行留「线，」这种一两个字的零头
                    if (!line.isEmpty()) {
                        out.add(line.toString().stripTrailing());
                        line.setLength(0);
                    }
                    List<String> parts = splitEven(clause.stripLeading(), maxW, slack);
                    out.addAll(parts.subList(0, parts.size() - 1));
                    line.append(parts.get(parts.size() - 1));
                    continue;
                }
                if (!line.isEmpty() && this.font.width(line + clause) > maxW + slack) {
                    out.add(line.toString().stripTrailing());
                    line.setLength(0);
                }
                line.append(line.isEmpty() ? clause.stripLeading() : clause);
            }
            out.add(line.toString().stripTrailing());
        }
        return out;
    }

    /** 分句结束的标点和预标的断点：分句连同它和后面的收尾引号、括号算一段 */
    private static final String CLAUSE_END = "，。、；：！？,;!?\u200C";
    private static final String CLAUSE_CLOSERS = "」』）》”’)";

    /** 一段（不含换行）切成分句，拼回去和原文一样 */
    private static List<String> clauses(String s) {
        List<String> out = new ArrayList<>();
        int start = 0;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            if (CLAUSE_END.indexOf(s.charAt(i)) < 0) {
                continue;
            }
            int j = i + 1;
            while (j < n && (CLAUSE_CLOSERS.indexOf(s.charAt(j)) >= 0 || CLAUSE_END.indexOf(s.charAt(j)) >= 0)) {
                j++;
            }
            out.add(s.substring(start, j));
            start = j;
            i = j - 1;
        }
        if (start < n) {
            out.add(s.substring(start));
        }
        return out;
    }

    /**
     * 逐字断成和按 {@code maxW} 断一样多的行；断在哪儿按代价挑：几行越一样长越好，断在词中间要罚
     * （断在虚词、连词、介词前面，或「的、时、为」这类字后面不罚：「产出槽将预览 / 可分解出的内融核心数量」，
     * 不要「可分解出的内 / 融核心数量」）。至少返回一行
     */
    private List<String> splitEven(String s, float maxW) {
        return splitEven(s, maxW, this.font.width("一"));
    }

    /** 同上；{@code slack} 和整段排行时一样，是一行最多能比栏宽多出多少 */
    private List<String> splitEven(String s, float maxW, float slack) {
        List<String> parts = splitChars(s, maxW);
        int n = parts.size();
        if (n <= 1) {
            return parts;
        }
        // 切成不可拆的小段：一个汉字、一个标点，或连着的西文 / 数字
        List<String> tok = new ArrayList<>();
        for (int i = 0; i < s.length(); ) {
            int j = i + 1;
            if (s.charAt(i) < 0x2E80 && !Character.isWhitespace(s.charAt(i))) {
                while (j < s.length() && s.charAt(j) < 0x2E80 && !Character.isWhitespace(s.charAt(j))) {
                    j++;
                }
            }
            tok.add(s.substring(i, j));
            i = j;
        }
        int m = tok.size();
        // 各段宽度的前缀和（原版字体没有字距调整，整行宽 = 各段宽之和）；行首行尾的空格不算
        float[] pre = new float[m + 1];
        for (int k = 0; k < m; k++) {
            pre[k + 1] = pre[k] + this.font.width(tok.get(k));
        }
        // cost[j][k]：前 k 段排成 j 行的最小代价；from 记上一行从哪儿开始
        double[][] cost = new double[n + 1][m + 1];
        int[][] from = new int[n + 1][m + 1];
        for (double[] row : cost) {
            Arrays.fill(row, Double.MAX_VALUE);
        }
        cost[0][0] = 0.0D;
        for (int j = 1; j <= n; j++) {
            for (int k = 1; k <= m; k++) {
                for (int a = j - 1; a < k; a++) {
                    if (cost[j - 1][a] == Double.MAX_VALUE || (a > 0 && !breakable(tok, a))) {
                        continue;
                    }
                    float lw = pre[k] - pre[a];
                    if (tok.get(a).isBlank()) {
                        lw -= pre[a + 1] - pre[a];
                    }
                    if (k - 1 > a && (tok.get(k - 1).isBlank() || NO_LINE_START.indexOf(tok.get(k - 1).charAt(0)) >= 0)) {
                        // 行尾的空格不算宽，逗号句号这类标点挂在行尾也不算
                        lw -= pre[k] - pre[k - 1];
                    }
                    if (lw > maxW + slack) {
                        continue;
                    }
                    double c = cost[j - 1][a] + (maxW - lw) * (maxW - lw) + (a > 0 ? breakPenalty(tok, a) : 0.0D);
                    if (c < cost[j][k]) {
                        cost[j][k] = c;
                        from[j][k] = a;
                    }
                }
            }
        }
        if (cost[n][m] == Double.MAX_VALUE) {
            return parts;
        }
        String[] lines = new String[n];
        for (int j = n, k = m; j > 0; j--) {
            int a = from[j][k];
            lines[j - 1] = String.join("", tok.subList(a, k)).strip();
            k = a;
        }
        return List.of(lines);
    }

    /** 能不能断在第 k 段前面：行首不放逗号句号这类标点 */
    private static boolean breakable(List<String> tok, int k) {
        return NO_LINE_START.indexOf(tok.get(k).charAt(0)) < 0;
    }

    /** 断在第 k 段前面的罚分（像素平方；几行长短差一个汉字约 80~160）：词和词的分界不罚，像是词中间的罚 1200 */
    private static double breakPenalty(List<String> tok, int k) {
        return breakPenalty(tok.get(k - 1).charAt(tok.get(k - 1).length() - 1), tok.get(k).charAt(0));
    }

    private static double breakPenalty(char before, char after) {
        if (Character.isWhitespace(before) || Character.isWhitespace(after) || before < 0x2E80 || after < 0x2E80) {
            return 0.0D;
        }
        if (BREAK_BEFORE.indexOf(after) >= 0 || BREAK_AFTER.indexOf(before) >= 0 || CLAUSE_END.indexOf(before) >= 0) {
            return 0.0D;
        }
        return 1200.0D;
    }

    /** 断在它前面像是新的一截（虚词、连词、介词、副词）、断在它后面像是一截结束的字 */
    private static final String BREAK_BEFORE = "可将于并即与或按及而但则再均需请被把在从向至以使让每各该此已仍亦也都就又还";
    private static final String BREAK_AFTER = "的了时后为着过";

    private List<String> splitChars(String s, float maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        wrapChars(s, maxW, out, line);
        out.add(line.toString().stripTrailing());
        return out;
    }

    /** 逐字断一段放不下的文字：接着 {@code line} 往下排，排满的行进 {@code out}，最后没排满的留在 {@code line} 里 */
    private void wrapChars(String s, float maxW, List<String> out, StringBuilder line) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            int j = i + 1;
            if (c < 0x2E80 && !Character.isWhitespace(c)) {
                while (j < n && s.charAt(j) < 0x2E80 && !Character.isWhitespace(s.charAt(j))) {
                    j++;
                }
            }
            String tok = s.substring(i, j);
            boolean blank = Character.isWhitespace(c);
            if (!line.isEmpty() && !blank && NO_LINE_START.indexOf(c) < 0
                    && this.font.width(line + tok) > maxW) {
                out.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            if (!(line.isEmpty() && blank)) {
                line.append(tok);
            }
            i = j;
        }
    }

    /** 不放在行首的标点 */
    private static final String NO_LINE_START = "，。、；：！？）」』》,.;:!?)%";

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x2E80) {
                return true;
            }
        }
        return false;
    }

    /** 断成和 {@link #wrap} 一样多的行，但每行尽量一样长（免得最后一行只剩一个字） */
    protected List<String> wrapEven(String s, float maxW) {
        return wrapEven(s, maxW, this.font.width("一"));
    }

    /**
     * 同 {@link #wrapEven}，但每行都不超过 {@code maxW}（行尾挂着的逗号句号除外，它们的笔画只占格子左边一小块）。
     * 贴着栏边、右边就是页边或别的东西的字用这个；wrapEven 允许多出一个字，窄栏里会越过栏边 9 格
     */
    protected List<String> wrapEvenWithin(String s, float maxW) {
        return wrapEven(s, maxW, 0.0F);
    }

    private List<String> wrapEven(String s, float maxW, float slack) {
        List<String> lines = wrap(s, maxW, slack);
        int n = lines.size();
        if (n < 2) {
            return lines;
        }
        // 收窄去凑等长时，断点不能比原来的差（「三张合成一 / 张未揭示模组」这种不要）
        int bad = badBreaks(lines);
        for (float w = (float) Math.ceil(textW(s, 0.0F) / n); w < maxW; w += 3.0F) {
            List<String> even = wrap(s, w, slack);
            if (even.size() == n && badBreaks(even) <= bad) {
                return even;
            }
        }
        return lines;
    }

    /** 几处换行像是断在词中间 */
    private static int badBreaks(List<String> lines) {
        int bad = 0;
        for (int i = 0; i + 1 < lines.size(); i++) {
            String a = lines.get(i);
            String b = lines.get(i + 1);
            if (!a.isEmpty() && !b.isEmpty() && breakPenalty(a.charAt(a.length() - 1), b.charAt(0)) > 0.0D) {
                bad++;
            }
        }
        return bad;
    }

    /**
     * 「解码」出字：解出来的前缀照原文画，前沿 3 个字是每 50 ms 换一次的乱码（赤毒色），后面还没轮到的不画。
     * {@code age} 是从开始出字算起过了多久，每 {@code perCharMs} 解出一个字
     *
     * @return 这串字完整画出来的宽度（居中时按它算，乱码阶段位置不跳）
     */
    protected float drawDecode(GuiGraphics g, String s, float x, float y, int rgb, float a, long age, int perCharMs,
                               float spacing) {
        int n = s.length();
        if (age < 0) {
            return textW(s, spacing);
        }
        int done = (int) Math.min(n, age / Math.max(1, perCharMs));
        if (done >= n) {
            return text(g, s, x, y, rgb, a, spacing);
        }
        String head = s.substring(0, done);
        if (done > 0) {
            text(g, head, x, y, rgb, a, spacing);
        }
        int end = Math.min(n, done + 3);
        StringBuilder b = new StringBuilder(end - done);
        int tick = (int) (age / 50L);
        for (int i = done; i < end; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                b.append(c);
                continue;
            }
            String pool = c >= 0x2E80 ? GLYPH_CJK : GLYPH_ASCII;
            b.append(pool.charAt(Math.min(pool.length() - 1, (int) (hash(i, tick) * pool.length()))));
        }
        float hx = done > 0 ? x + textW(head, spacing) + spacing : x;
        text(g, b.toString(), hx, y, FxPalette.KUVA, 0.85F * a, spacing);
        return textW(s, spacing);
    }

    /** 「解码」到一半的一串字（解出的前缀 + 前沿三个乱码），给只能整串画一种颜色的地方用 */
    protected static String scramble(String s, long age, int perCharMs) {
        int n = s.length();
        if (age < 0) {
            return "";
        }
        int done = (int) Math.min(n, age / Math.max(1, perCharMs));
        if (done >= n) {
            return s;
        }
        StringBuilder b = new StringBuilder(s.substring(0, done));
        int tick = (int) (age / 50L);
        for (int i = done; i < Math.min(n, done + 3); i++) {
            char c = s.charAt(i);
            String pool = c >= 0x2E80 ? GLYPH_CJK : GLYPH_ASCII;
            b.append(Character.isWhitespace(c) ? c
                    : pool.charAt(Math.min(pool.length() - 1, (int) (hash(i, tick) * pool.length()))));
        }
        return b.toString();
    }

    /** 居中的「解码」出字 */
    protected void drawDecodeC(GuiGraphics g, String s, float centerX, float y, int rgb, float a, long age,
                               int perCharMs) {
        float w = textW(s, 0.0F);
        drawDecode(g, s, Math.round(centerX - w * 0.5F), y, rgb, a, age, perCharMs, 0.0F);
    }

    /** 栏标题：开场时跟着框一起「解码」出来 */
    protected void heading(GuiGraphics g, String s, float x, float a, float spacing) {
        long age = quick ? Long.MAX_VALUE / 4 : KuvaFxDirector.clockMs() - openClock - T_FRAME_IN;
        drawDecode(g, s, x, titleY, FxPalette.TEXT_MID, a, age, 45, spacing);
    }

    // ==================== 全息环、弧、光点 ====================

    /** 以 (ox, oy) 为中心、从 a0 起扫过 sweep 弧度的一段弧（rx、ry 不同就是椭圆弧） */
    protected void arc(GuiGraphics g, float ox, float oy, float rx, float ry, float a0, float sweep, float lineW,
                       int argb) {
        int n = Mth.clamp((int) (Math.abs(sweep) / (Mth.PI / 20.0F)) + 1, 2, shapePts.length / 2 - 1);
        for (int k = 0; k <= n; k++) {
            float a = a0 + sweep * k / n;
            shapePts[k * 2] = ox + rx * Mth.cos(a);
            shapePts[k * 2 + 1] = oy + ry * Mth.sin(a);
        }
        GuiShapes.polyline(g, shapePts, n + 1, false, lineW, argb);
    }

    /** 整圈椭圆 */
    protected void ellipse(GuiGraphics g, float ox, float oy, float rx, float ry, float lineW, int argb) {
        int n = 48;
        for (int k = 0; k < n; k++) {
            float a = k * Mth.TWO_PI / n;
            shapePts[k * 2] = ox + rx * Mth.cos(a);
            shapePts[k * 2 + 1] = oy + ry * Mth.sin(a);
        }
        GuiShapes.polyline(g, shapePts, n, true, lineW, argb);
    }

    /**
     * 一圈倾斜的全息椭圆：外圈虚线慢慢转，里圈一道实线，三颗光点沿里圈绕（转到前面亮、后面暗）。
     * 画在格子底下当背景
     */
    protected void drawHoloRing(GuiGraphics g, float ox, float oy, float rx, float ry, long now, float a) {
        if (a <= 0.0F) {
            return;
        }
        float lineW = GuiShapes.pixels(g, lw());
        float rot = (now % 40000L) / 40000.0F * Mth.TWO_PI;
        GuiShapes.begin(g);
        int dash = GuiShapes.argb(FxPalette.KUVA_PALE, 0.08F * a);
        for (int k = 0; k < 40; k++) {
            float a0 = rot + k * Mth.TWO_PI / 40.0F;
            float a1 = a0 + Mth.TWO_PI / 40.0F * 0.55F;
            GuiShapes.hairline(g, ox + rx * Mth.cos(a0), oy + ry * Mth.sin(a0), ox + rx * Mth.cos(a1),
                    oy + ry * Mth.sin(a1), lineW, dash);
        }
        float ix = rx * 0.9F;
        float iy = ry * 0.9F;
        ellipse(g, ox, oy, ix, iy, lineW, GuiShapes.argb(FxPalette.KUVA, 0.07F * a));
        GuiShapes.end();
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (int k = 0; k < 3; k++) {
            float ang = -rot * 3.0F + k * Mth.TWO_PI / 3.0F;
            float depth = 0.5F + 0.5F * Mth.sin(ang);
            for (int tail = 0; tail < 4; tail++) {
                float a1 = ang - tail * 0.06F;
                float px = ox + ix * Mth.cos(a1);
                float py = oy + iy * Mth.sin(a1);
                fill(g, px - 0.5F, py - 0.5F, px + 0.5F, py + 0.5F, FxPalette.KUVA,
                        (0.15F + 0.55F * depth) * (1.0F - tail * 0.25F) * a);
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    /** 折线（x0, y0, x1, y1, …）各点的累计长度 */
    protected static float[] cumulative(int[] p) {
        float[] cum = new float[p.length / 2];
        for (int k = 1; k < cum.length; k++) {
            float dx = p[k * 2] - p[k * 2 - 2];
            float dy = p[k * 2 + 1] - p[k * 2 - 1];
            cum[k] = cum[k - 1] + Mth.sqrt(dx * dx + dy * dy);
        }
        return cum;
    }

    /** 折线上距起点 d 的那一点（结果放在一个共用的数组里，马上用掉） */
    protected float[] pointAt(int[] p, float[] cum, float d) {
        int k = 1;
        while (k < cum.length - 1 && cum[k] < d) {
            k++;
        }
        float seg = cum[k] - cum[k - 1];
        float u = seg <= 0.0F ? 0.0F : (d - cum[k - 1]) / seg;
        at[0] = p[k * 2 - 2] + (p[k * 2] - p[k * 2 - 2]) * u;
        at[1] = p[k * 2 - 1] + (p[k * 2 + 1] - p[k * 2 - 1]) * u;
        return at;
    }

    /**
     * 沿折线走的一颗光点，拖 4 格尾巴：{@code u} 是 0~1 的位置（缓入缓出）。
     * 调用方自己开加法混合、begin / end（一批光点一起画）
     */
    protected void pulseAt(GuiGraphics g, int[] p, float[] cum, float u, int rgb, float a) {
        float d = cum[cum.length - 1] * inOut(clamp01(u));
        for (int tail = 0; tail < 5; tail++) {
            float dd = d - tail * 2.5F;
            if (dd < 0.0F) {
                break;
            }
            float[] q = pointAt(p, cum, dd);
            float s = tail == 0 ? 1.0F : 0.5F;
            fill(g, q[0] - s, q[1] - s, q[0] + s, q[1] + s, tail == 0 ? FxPalette.KUVA_PALE : rgb,
                    (tail == 0 ? 0.9F : 0.6F - tail * 0.11F) * a);
        }
    }

    /** 一批常驻流动的光点：每条折线隔 {@code period} 跑一趟（占前 70%），条与条之间错开；null 的跳过 */
    protected void drawPulses(GuiGraphics g, int[][] paths, int[] rgbs, int period, long now, float a) {
        if (a <= 0.0F) {
            return;
        }
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (int i = 0; i < paths.length; i++) {
            if (paths[i] == null) {
                continue;
            }
            float u = ((now + i * 311L) % period) / (period * 0.7F);
            if (u <= 1.0F) {
                pulseAt(g, paths[i], cumulative(paths[i]), u, rgbs[i], a);
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    // ==================== 时间曲线 ====================

    /** 开场里 a~b 这一段的进度（缓入缓出）；快速开场时一律 150 ms 线性淡入 */
    protected float phase(long t, int a, int b) {
        return quick ? lin(t) : inOut(seg(t, a, b));
    }

    private static float lin(long t) {
        return clamp01(t / (float) T_QUICK);
    }

    protected static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    protected static float seg(long t, float a, float b) {
        return clamp01((t - a) / (b - a));
    }

    protected static float easeOut(float t) {
        float u = 1.0F - clamp01(t);
        return 1.0F - u * u * u;
    }

    protected static float inOut(float t) {
        t = clamp01(t);
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
    }

    protected static float sine(float t) {
        return 0.5F - 0.5F * (float) Math.cos(clamp01(t) * Math.PI);
    }

    /** 0~1 的伪随机数，只由两个整数决定 */
    protected static float hash(int a, int b) {
        int x = a * 0x9E3779B1 + b * 0x85EBCA77;
        x ^= x >>> 15;
        x *= 0x2C1B3C6D;
        x ^= x >>> 12;
        x *= 0x297A2D39;
        x ^= x >>> 15;
        return (x >>> 8) / 16777216.0F;
    }

    /** 档案的墨烟：铺满全屏，按高度缩放、每秒往左飘 0.01 屏宽；贴图四边无缝，靠重复铺开 */
    public static void drawSmoke(GuiGraphics g, int w, int h, long now, float alpha) {
        if (alpha <= 0.004F) {
            return;
        }
        float tileW = h * 2.0F;
        float drift = (now / 1000.0F) * 0.01F * w;
        float u = (drift / tileW) * 256.0F % 256.0F;
        int uw = Math.max(1, Math.round(256.0F * w / tileW));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(SMOKE, 0, 0, w, h, u, 0.0F, uw, 128, 256, 128);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * 画一个调暗了的物品图标：{@code lum} 压低颜色，{@code alpha} 管淡入（半透明只对用半透明材质的物品有效，
     * 方块物品只会跟着变暗）。以前是在图标上盖一块墨色方块，底下的页面是半透明的，盖上去就是一块方的黑底。
     * 先把前面排着没画的字和色块画掉，不然它们会一起被调暗
     */
    protected static void renderItemDim(GuiGraphics g, ItemStack stack, int x, int y, float lum, float alpha) {
        if (alpha <= 0.02F || lum <= 0.02F) {
            return;
        }
        if (lum >= 0.999F && alpha >= 0.999F) {
            g.renderItem(stack, x, y);
            return;
        }
        g.flush();
        RenderSystem.setShaderColor(lum, lum, lum, alpha);
        g.renderItem(stack, x, y);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 档案那张背光，拉到给定的框里 */
    protected static void drawBacklight(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        if (alpha <= 0.004F) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(BACKLIGHT, x, y, w, h, 0.0F, 0.0F, 128, 256, 128, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
