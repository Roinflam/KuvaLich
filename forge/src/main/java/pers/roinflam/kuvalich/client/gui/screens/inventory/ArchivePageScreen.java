package pers.roinflam.kuvalich.client.gui.screens.inventory;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 安魂系列方块界面的底子：照玄骸档案的样子铺满整屏、不画外框，像是档案的另一页
 *
 * <ul>
 *   <li>压暗 85% + 墨烟，两条竖分隔线把屏幕分成左、中、右三栏（和档案同一组比例）；背包在中栏下部，细线方格；</li>
 *   <li>开场沿用档案的时间轴：压暗 0~250 ms，框架 300~600 ms，文字 450~850 ms，背包一行一行淡入；
 *       同一种界面 10 秒内重开、或者关了档案开场选项时，150 ms 一起淡入；</li>
 *   <li>物品图标不能半透明，淡入用墨色盖板压在图标上面（z 300）；</li>
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

    private void computeColumns() {
        int w = this.width;
        int h = this.height;
        small = h < 260;
        sepL = Math.round(w * 0.29F);
        sepR = Math.round(w * 0.75F);
        lx0 = Math.round(w * 0.077F);
        lx1 = Math.round(w * 0.265F);
        rx0 = Math.round(w * 0.775F);
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

    /** 把一格摆到 (x, y)（物品图标左上角，屏幕坐标） */
    protected static void place(Slot slot, int x, int y) {
        SlotPositionAccessor a = (SlotPositionAccessor) slot;
        a.kuvalich$setX(x);
        a.kuvalich$setY(y);
    }

    /** 玩家背包摆成 3 行 + 隔一道缝的快捷栏，(gx, gy) 是方格外沿左上角 */
    protected void placeInventory() {
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) {
                continue;
            }
            int c = s.getContainerSlot();
            if (c < 9) {
                place(s, gx + 1 + 18 * c, gy + 59);
            } else if (c < 36) {
                place(s, gx + 1 + 18 * ((c - 9) % 9), gy + 1 + 18 * ((c - 9) / 9));
            }
        }
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
        drawInventoryCovers(g, now - openClock);
        drawRings(g, now);
        drawMotes(g, now);
        g.flush();
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
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
        drawInventory(g, mx, my, t);
        drawPage(g, mx, my, now, t, eFrame);
        drawOpenBeam(g, t);
        g.flush();
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

    /** 背包：墨底 + 细线方格，开场一行一行淡入；物品由原版画在上面，盖板在 {@link #drawInventoryCovers} */
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
            boolean hv = isHovering(s.x, s.y, 16, 16, mx, my);
            fill(g, s.x - 1, s.y - 1, s.x + 17, s.y + 17, INK, 0.6F * e);
            if (hv) {
                fill(g, s.x - 1, s.y - 1, s.x + 17, s.y + 17, FxPalette.KUVA_PALE, 0.1F);
            }
            GuiShapes.frame(g, s.x - 1, s.y - 1, s.x + 17, s.y + 17, lw(),
                    GuiShapes.argb(FxPalette.KUVA_PALE, (hv ? 0.55F : 0.1F) * e));
        }
        GuiShapes.end();
    }

    private float rowPhase(long t, int row) {
        int a = T_INV_IN + T_INV_STEP * row;
        return phase(t, a, a + T_INV_LEN);
    }

    private void drawInventoryCovers(GuiGraphics g, long t) {
        if (rowPhase(t, 3) >= 1.0F) {
            return;
        }
        GuiShapes.begin(g);
        for (Slot s : menu.slots) {
            int row = inventoryRow(s);
            if (row < 0 || !s.hasItem()) {
                continue;
            }
            float e = rowPhase(t, row);
            if (e < 1.0F) {
                fill(g, s.x - 1, s.y - 1, s.x + 17, s.y + 17, INK, 1.0F - e);
            }
        }
        GuiShapes.end();
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

    /**
     * 按宽度断行。纯西文交给原版（在空格处断）；带汉字的自己断：汉字逐字可断，西文单词、数字不拆开，
     * 行首不放逗号句号这类标点（宁可这一行超一点）。原版遇到「1 张模组换……」会在数字后的空格处断，
     * 剩下一长串汉字再被硬切，窄栏里常常只剩一个数字孤零零一行
     */
    protected List<String> wrap(String s, float maxW) {
        List<String> out = new ArrayList<>();
        if (!hasCjk(s)) {
            for (FormattedText line : this.font.getSplitter().splitLines(s, Math.max(1, (int) maxW), Style.EMPTY)) {
                out.add(line.getString());
            }
            return out;
        }
        StringBuilder line = new StringBuilder();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n') {
                out.add(line.toString().stripTrailing());
                line.setLength(0);
                i++;
                continue;
            }
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
        if (!line.isEmpty()) {
            out.add(line.toString().stripTrailing());
        }
        return out;
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
        List<String> lines = wrap(s, maxW);
        int n = lines.size();
        if (n < 2) {
            return lines;
        }
        for (float w = (float) Math.ceil(textW(s, 0.0F) / n); w < maxW; w += 3.0F) {
            List<String> even = wrap(s, w);
            if (even.size() == n) {
                return even;
            }
        }
        return lines;
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
