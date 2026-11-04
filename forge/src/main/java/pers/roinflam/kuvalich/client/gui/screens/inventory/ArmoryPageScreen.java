package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.client.gui.codex.ModuleCodexScreen;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.tooltip.PanelPalette;
import pers.roinflam.kuvalich.client.tooltip.PanelStyle;
import pers.roinflam.kuvalich.client.tooltip.WeaponPanelComposer;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.weapon.panel.ValueFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 武器、战甲两个军械库共用的一页：中栏模组卡、右栏已装清单和属性加成、底部图鉴入口
 *
 * <ul>
 *   <li>中栏：八张模组卡排成 2×4（切角卡形，装着的按稀有度着色、边上有一道光绕着走，没解锁的画叉），
 *       卡底一排等级刻度，刚装上时一颗颗亮起、卡上方浮起「等级 N / M」；背后一圈慢慢转的全息椭圆环；
 *       卡下面一条八段的容量条，再下面一行是已装几个，手上拿着模组时换成它的名字和等级，放不进去时临时换成原因；</li>
 *   <li>电路：每张卡到装模组的那件东西（武器格 / 玩家）有一条折线，装着模组的那条亮着、光点一直往那边流；</li>
 *   <li>右栏：已装模组的清单（名字按稀有度着色，悬停一行显示说明、同时点亮它所在的卡），
 *       下面是各词条加起来的属性加成；装上 / 拿走时一道光从那张卡飞到属性栏，变了的词条再错开着滚到新值、
 *       扫一道增减色的光、行外留一个增减标（见 {@link #drawStats}）；</li>
 *   <li>左栏底下的小表（{@link #drawInfo}）由子类摆，共用的「装配概要」几项见 {@link #loadoutInfo}；</li>
 *   <li>底部「[TAB] 模组图鉴」，按 TAB 或者点它打开图鉴；</li>
 *   <li>装入、取出、武器上台的锻造演出照旧交给 {@link GuiFx}，每 tick 由 {@link ForgeOpClassifier.TableWatch}
 *       比快照认出来（点击、Shift 点击、图鉴一键装配都算）。</li>
 * </ul>
 *
 * <p>左栏归子类：武器军械库是武器的大投影和武器格，战甲军械库是玩家自己。</p>
 *
 * <p>画法上都是合批的细线和色块（{@link GuiShapes}），不逐格裁剪；加法混合的光集中在几段里画，切混合模式的次数固定。</p>
 */
@OnlyIn(Dist.CLIENT)
public abstract class ArmoryPageScreen<M extends AbstractContainerMenu> extends ArchivePageScreen<M> {

    /** 菜单里 0~7 是模组格 */
    protected static final int MODULES = 8;
    /** 模组卡的半宽、半高，横向格距、行距 */
    protected static final int CELL = 11;
    protected static final int CELL_HH = 14;
    private static final int GRID_SP = 30;
    private static final int ROW_SP = 34;
    /** 卡的左上、右下切角 */
    private static final int CHAMFER = 3;
    /** 清单里宽度够这么多才在名字后面带等级，窄了只剩名字 */
    private static final int LEVEL_MIN_WIDTH = 60;
    /** 卡边那道光绕一圈的周期、真正在走的那一段占比 */
    private static final int GLINT_PERIOD = 3600;
    private static final float GLINT_RUN = 0.45F;
    /** 电路光点：一趟的周期、在路上的那一段占比 */
    private static final int PULSE_PERIOD = 1900;
    private static final float PULSE_RUN = 0.7F;
    /**
     * 属性加成的演出：卡到属性栏的那道光走多久（属性等光到了才开始变）、数字滚到新值多久、几条一起变时每条错开多久、
     * 变化那一行的光扫多久、行右边的增减标留多久
     */
    private static final int STREAK_MS = 420;
    private static final int STAT_ROLL_MS = 1000;
    private static final int STAT_STAGGER_MS = 70;
    private static final int STAT_WIPE_MS = 1600;
    private static final int STAT_DELTA_MS = 2600;
    /** 卡底等级刻度：装上后第一颗什么时候亮、之后每颗隔多久；状态行「装配完成」留多久 */
    private static final int RANK_PIP_MS = 240;
    private static final int RANK_PIP_STEP = 45;
    private static final int RANK_POP_MS = 1800;
    /**
     * 模组托架框：比八张卡和容量条宽出的边距（横、竖）和切角。横向留得多些，上面一排最左边那张的电路在卡和框之间往下走，
     * 贴得近了两道竖线挤在一起
     */
    private static final int BAY_MX = 12;
    private static final int BAY_MY = 7;
    private static final int BAY_CHAMFER = 8;
    /** 属性加成一行多高；模组名折行时第二行比第一行低多少 */
    private static final int STAT_ROW = 10;
    private static final int WRAP_LINE = 9;
    /** 模组名折成两行以后，属性加成至少还要留几行，不够就不折、截断（悬停看全名） */
    private static final int MIN_STAT_ROWS = 4;

    /** 武器格的菜单格号，没有传 −1 */
    private final int weaponSlot;
    private final boolean weaponCodex;

    /**
     * 锻造演出。折线路径在 GuiFx 里登记了就清不掉、又只按登记时的坐标画，所以窗口大小变了（格子挪了）
     * 就换一份新的、按新坐标重登；TableWatch 要跨过打开图鉴再回来，一直用同一个，路径号数组原地改
     */
    private GuiFx fx = new GuiFx();
    private final int[] pathIds = new int[MODULES];
    private final ForgeOpClassifier.TableWatch watch;
    private int pathW = -1;
    private int pathH = -1;
    private boolean opened;
    private String codexHint = "";

    /** 版式：模组块的顶、八张卡的中心、卡下沿、电路横道、容量条、状态行 */
    protected int pTop;
    protected final int[] modX = new int[MODULES];
    protected final int[] modY = new int[MODULES];
    protected int gridBottom;
    protected int laneY;
    private int barY;
    private int statusY;

    /** 每张卡的电路折线（{x0, y0, x1, y1, …}）和各段累计长度；子类没给的是 null */
    private final int[][] circuit = new int[MODULES][];
    private final float[][] circuitLen = new float[MODULES][];

    /** 右栏这一帧列出的格号（按格号排，跳过还没轮到的） */
    private final int[] rows = new int[MODULES];
    private int rowCount;
    /** 清单各行的顶（多一个是最后一行的下沿）；名字折了行的那一行高一些，命中按这个算 */
    private final int[] rowTop = new int[MODULES + 1];
    /** 属性加成的滚动：从第几行开始看、画面上平滑跟着的偏移、最多能往下滚几行、滚动区范围 */
    private int statFirst;
    private float statShift;
    private int statMaxFirst;
    private int statAreaTop = -1;
    private int statAreaBottom = -1;
    /** 这一帧鼠标下的清单行对应的格号、鼠标下的模组卡；都没有是 −1 */
    private int listHover = -1;
    private int cellHover = -1;
    /** 这一帧鼠标下不是格子、但要显示物品说明的东西（清单行、武器投影） */
    protected ItemStack tipStack = ItemStack.EMPTY;

    /** 属性加成：上次算的时候各格是什么、各词条从哪个值滚到哪个值 */
    private final ItemStack[] statBasis = new ItemStack[MODULES];
    private final List<Stat> stats = new ArrayList<>();
    /** 第一次算属性不演（打开界面时本来就装着的） */
    private boolean statsPrimed;
    /** 从卡飞向属性栏的光；属性栏标题上一帧的 y（光的终点）；各行长短条的满格对应多大的数（平滑跟着变） */
    private final List<Streak> streaks = new ArrayList<>();
    private int statsTop = -1;
    private float statScale = Float.NaN;
    private long statClock = Long.MIN_VALUE;

    /** 左栏小表各项上一帧的值、变了的时刻 */
    private final Map<String, String> infoSeen = new HashMap<>();
    private final Map<String, Long> infoAt = new HashMap<>();

    /** 画卡形用的点缓冲；增减标的小三角；托架框的外圈、里圈和沿框取的点 */
    private final float[] pts = new float[2 * 8];
    private final float[] tri = new float[6];
    private final float[] bayOuter = new float[2 * 6];
    private final float[] bayInner = new float[2 * 6];
    private final float[] bayPt = new float[2];

    private static final class Stat {
        final String key;
        final String name;
        double from;
        double to;
        long t0;
        boolean leaving;
        /** 这次变了多少；新词条出现的时刻（老词条是 0）；画在哪一行（平滑挪到目标行） */
        double delta;
        long born;
        float y = Float.NaN;

        Stat(String key, String name) {
            this.key = key;
            this.name = name;
        }

        double value(long now) {
            float u = clamp01((now - t0) / (float) STAT_ROLL_MS);
            return from + (to - from) * easeOut(u);
        }
    }

    /** 一道从第 {@code card} 张卡飞向属性栏的光 */
    private record Streak(int card, int rgb, long t0) {
    }

    /** 左栏小表的一项：名称、数值、数值的颜色 */
    protected record Info(String key, String value, int rgb) {
    }

    protected ArmoryPageScreen(M menu, Inventory playerInventory, Component title, int weaponSlot, boolean weaponCodex) {
        super(menu, playerInventory, title);
        this.weaponSlot = weaponSlot;
        this.weaponCodex = weaponCodex;
        Arrays.fill(pathIds, -1);
        Arrays.fill(statBasis, ItemStack.EMPTY);
        this.watch = new ForgeOpClassifier.TableWatch(MODULES, weaponSlot, weaponSlot >= 0 ? pathIds : null);
    }

    // ==================== 子类填的 ====================

    /** 摆左栏的槽位、算左栏的版式（模组卡、横道已经算好） */
    protected abstract void layoutLeft();

    /** 画左栏（物品图标底下） */
    protected abstract void drawLeft(GuiGraphics g, int mx, int my, long now, long t, float eF, float eT);

    /** 左栏压在物品上面的东西（扫描线、盖板） */
    protected void drawLeftOverlay(GuiGraphics g, int mx, int my, long now, long t) {
    }

    /** 第 i 张卡到左栏的电路折线 {x0, y0, x1, y1, …}；没有返回 null。有武器格的，锻造演出的流光也走这条 */
    @Nullable
    protected int[] circuitPath(int i) {
        return null;
    }

    /** 电路的光点刚到终点（{@code flash} 从 1 淡到 0），子类在终点画个亮一下的节点之类 */
    protected void drawCircuitEnd(GuiGraphics g, float x, float y, float flash, float eT) {
    }

    /** 装模组的那件东西在不在：武器军械库看台上有没有武器，战甲军械库装在玩家自己身上，总在 */
    protected abstract boolean hasHost();

    /** 能用几格（菜单里的同一套规则） */
    protected abstract int moduleLimit();

    /** 这一种模组格收不收这件东西（只看种类，别的规则在 {@link #rejectMessage} 里一条条对） */
    protected abstract boolean acceptsModule(ItemStack stack);

    /** 没放武器时的提示 */
    protected Component hostMissing() {
        return Component.empty();
    }

    /** 放了别的东西时的提示 */
    protected abstract Component onlyModules();

    /** 右栏还没有模组时的说明 */
    protected abstract Component emptyDetail();

    /** 往模组格以外的、盯着的格子里放不进去时的提示 */
    protected Component otherReject(Slot slot, ItemStack carried) {
        return Component.empty();
    }

    /** 悬停没解锁的格子时的说明 */
    protected List<Component> lockTip(int i) {
        return List.of();
    }

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        super.init();
        codexHint = "[TAB] " + Component.translatable("kuvalich.codex.open_hint").getString();
        // 改窗口大小、从图鉴回来时 init 会再调一次，开机声只在第一次打开时放
        if (!opened) {
            opened = true;
            GuiFx.playOpen();
        }
    }

    @Override
    protected final void layout() {
        // 托架框顶在栏标题下划线下面留 5（框比卡顶高出 BAY_MY + 2）
        pTop = hairY + 10;
        for (int i = 0; i < MODULES; i++) {
            modX[i] = cx + Math.round((i % 4 - 1.5F) * GRID_SP);
            modY[i] = pTop + 16 + (i / 4) * ROW_SP;
            // 图标在卡的上部，下面留出画极性菱形的地方
            place(menu.getSlot(i), modX[i] - 8, modY[i] - 10);
        }
        gridBottom = modY[MODULES - 1] + CELL_HH;
        laneY = gridBottom + 5;
        barY = laneY + 6;
        // 状态行放在托架框下面，隔 5
        statusY = bayBottom() + 5;
        placeInventory();
        layoutLeft();
        for (int i = 0; i < MODULES; i++) {
            circuit[i] = circuitPath(i);
            circuitLen[i] = circuit[i] == null ? null : cumulative(circuit[i]);
        }
        // 武器排在最前：带着模组的武器放上台时，同一帧的几件按这个顺序错开出场，先上武器、再一张张亮卡
        int[] ids = new int[weaponSlot >= 0 ? MODULES + 1 : MODULES];
        int k = 0;
        if (weaponSlot >= 0) {
            ids[k++] = weaponSlot;
        }
        for (int i = 0; i < MODULES; i++) {
            ids[k++] = i;
        }
        track(ids);
        if (weaponSlot >= 0 && (width != pathW || height != pathH)) {
            pathW = width;
            pathH = height;
            fx = new GuiFx();
            Arrays.fill(pathIds, -1);
            for (int i = 0; i < MODULES; i++) {
                if (circuit[i] != null) {
                    pathIds[i] = fx.path(circuit[i]);
                }
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        watch.tick(menu, fx);
    }

    /**
     * 锻造演出画在格子物品之后、手上那件东西之前；这里的坐标已经平移过 leftPos / topPos（整屏界面两个都是 0），
     * 所以传 0
     */
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        fx.render(g, 0, 0, !menu.getCarried().isEmpty());
    }

    /** 原版只给有东西的格子出说明：没解锁的空格子、清单行、武器投影的说明在这里补 */
    @Override
    protected void renderPage(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderPage(g, mx, my, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        Slot hs = this.hoveredSlot;
        if (hs != null) {
            if (!hs.hasItem() && hs.index < MODULES && hs.index >= moduleLimit()) {
                List<Component> lines = lockTip(hs.index);
                if (!lines.isEmpty()) {
                    g.renderComponentTooltip(this.font, lines, mx, my);
                }
            }
            return;
        }
        if (!tipStack.isEmpty()) {
            g.renderTooltip(this.font, tipStack, mx, my);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            openCodex();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openCodex() {
        Minecraft.getInstance().setScreen(new ModuleCodexScreen(this, weaponCodex));
    }

    // ==================== 事件 ====================

    /**
     * 放不进去的原因，顺序和菜单的 mayPlace 一样：没放武器 → 没解锁 → 种类不对 → 未揭示 → 格子有东西 →
     * 裂罅只能一张 → 和已装的冲突
     */
    @Override
    protected Component rejectMessage(Slot slot, ItemStack carried) {
        int i = slot.index;
        if (i >= MODULES) {
            return otherReject(slot, carried);
        }
        if (!hasHost()) {
            return hostMissing();
        }
        if (i >= moduleLimit()) {
            return Component.translatable("kuvalich.armory.slot_locked");
        }
        if (!acceptsModule(carried)) {
            return onlyModules();
        }
        if (AbstractModule.isRandom(carried)) {
            return Component.translatable("kuvalich.armory.unrevealed");
        }
        if (slot.hasItem()) {
            return Component.translatable("kuvalich.armory.slot_taken");
        }
        boolean riven = ForgeOpClassifier.rarity(carried) == 4;
        for (int j = 0; j < MODULES; j++) {
            ItemStack ex = menu.getSlot(j).getItem();
            if (j == i || ex.isEmpty()) {
                continue;
            }
            if (riven && ForgeOpClassifier.rarity(ex) == 4) {
                return Component.translatable("kuvalich.armory.one_riven");
            }
            if (AbstractModule.hasConflict(ex, carried)) {
                return Component.translatable("kuvalich.armory.conflict", Component.literal(plainName(ex)));
            }
        }
        return Component.empty();
    }

    // ==================== 画 ====================

    @Override
    protected void drawPage(GuiGraphics g, int mx, int my, long now, long t, float eF) {
        float eT = phase(t, T_TEXT_IN, T_TEXT_DONE);
        tipStack = ItemStack.EMPTY;
        collectRows();
        refreshStats(now);
        listHover = rowAt(mx, my);
        cellHover = -1;
        for (int i = 0; i < MODULES; i++) {
            if (Math.abs(mx - modX[i]) <= CELL && Math.abs(my - modY[i]) <= CELL_HH) {
                cellHover = i;
            }
        }

        drawLeft(g, mx, my, now, t, eF, eT);

        // ---- 中栏：背光、托架、标题、电路、八张卡、容量条、状态行 ----
        drawBacklight(g, sepL, pTop - 30, sepR - sepL, 130, 0.85F * eT);
        drawBayFrame(g, now, eF);
        heading(g, Component.translatable("kuvalich.armory.mods").getString(), cx - 56, eF, 2.0F);
        hl(g, cx - 56, cx + 56, hairY, FxPalette.KUVA_PALE, 0.2F * eF);
        drawCircuits(g, now, eT);
        for (int i = 0; i < MODULES; i++) {
            drawCard(g, i, t);
        }
        drawGlints(g, now, t);
        drawBar(g, now, t);
        ItemStack carried = menu.getCarried();
        if (!drawMessage(g, cx, statusY, eF)) {
            if (!hasHost()) {
                textC(g, hostMissing().getString(), cx, statusY, FxPalette.ASH, 0.7F * eF);
            } else if (!carried.isEmpty() && acceptsModule(carried)) {
                drawCarrying(g, carried, eF);
            } else {
                float k = drawPlaced(g, eF);
                int used = installedCount();
                int lim = moduleLimit();
                boolean full = lim > 0 && used >= lim;
                if (k < 1.0F) {
                    textC(g, Component.translatable("kuvalich.armory.count", used, lim).getString(), cx, statusY,
                            full ? FxPalette.BONE : FxPalette.ASH, (full ? 0.85F : 0.7F) * (1.0F - k) * eF);
                }
            }
        }
        // 赤毒点往上飘最多 0.12 屏高：起点放在模组块下部，飘到头也不出卡顶
        int rise = Math.round(0.12F * height);
        int driftTop = pTop + 2 + rise;
        drawDrift(g, cx, driftTop, 2 * (modX[3] + CELL - cx), barY + 3 - driftTop, now, eF);

        // ---- 右栏：已装模组、属性加成 ----
        drawTitle(g, Component.translatable("kuvalich.armory.installed").getString(), rx0, rx1 - rx0, eF);
        hl(g, rx0, rx1, hairY, FxPalette.KUVA_PALE, 0.35F * eF);
        int y = drawList(g, eF);
        drawStats(g, y + 8, now, eF);
        drawStreaks(g, now, eF);

        // ---- 底部：图鉴入口 ----
        float w = textW(codexHint, 0.0F);
        float hx0 = Math.round(cx - w * 0.5F);
        boolean hv = mx >= hx0 && mx < hx0 + w && my >= botY - 1 && my < botY + 10;
        text(g, codexHint, hx0, botY, hv ? FxPalette.BONE : FxPalette.ASH, (hv ? 1.0F : 0.8F) * eF);
        hot(hx0, botY - 1, hx0 + w, botY + 10, null, this::openCodex);
    }

    // ---------- 电路 ----------

    /** 各卡到左栏的折线：装着的那条亮、光点往终点流；没装的只留一道很淡的底线 */
    private void drawCircuits(GuiGraphics g, long now, float eT) {
        if (eT <= 0.0F) {
            return;
        }
        float lineW = GuiShapes.pixels(g, lw());
        GuiShapes.begin(g);
        for (int i = 0; i < MODULES; i++) {
            int[] p = circuit[i];
            if (p == null) {
                continue;
            }
            ItemStack st = installed(i);
            int col = st.isEmpty() ? FxPalette.KUVA_PALE : rarityColor(st);
            int c = GuiShapes.argb(col, (st.isEmpty() ? 0.05F : 0.18F) * eT);
            for (int k = 0; k + 3 < p.length; k += 2) {
                GuiShapes.hairline(g, p[k], p[k + 1], p[k + 2], p[k + 3], lineW, c);
            }
        }
        GuiShapes.end();

        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        float endFlash = 0.0F;
        float ex = 0.0F;
        float ey = 0.0F;
        for (int i = 0; i < MODULES; i++) {
            int[] p = circuit[i];
            ItemStack st = installed(i);
            if (p == null || st.isEmpty()) {
                continue;
            }
            int col = rarityColor(st);
            float[] cum = circuitLen[i];
            long ph = (now + i * 237L) % PULSE_PERIOD;
            float u = ph / (PULSE_PERIOD * PULSE_RUN);
            ex = p[p.length - 2];
            ey = p[p.length - 1];
            if (u <= 1.0F) {
                pulseAt(g, p, cum, u, col, eT);
            } else {
                // 到站之后亮一下、淡掉
                float since = (ph - PULSE_PERIOD * PULSE_RUN) / 220.0F;
                endFlash = Math.max(endFlash, 1.0F - clamp01(since));
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
        if (endFlash > 0.0F) {
            drawCircuitEnd(g, ex, ey, endFlash, eT);
        }
    }

    // ---------- 模组卡 ----------

    /**
     * 一张模组卡：切角卡形，墨底；装着的按稀有度着色（淡色底、实线框、顶上一道色条、底部实心菱形），
     * 空的只有淡框和空心菱形，没解锁的画叉；放不进去时卡框左右抖、闪红（物品画在槽位的固定坐标上，不动）
     */
    private void drawCard(GuiGraphics g, int i, long t) {
        float e = phase(t, 600 + 50 * i, 900 + 50 * i);
        if (e <= 0.0F) {
            return;
        }
        ItemStack st = installed(i);
        boolean has = !st.isEmpty();
        boolean hv = cellHover == i || listHover == i;
        float lock = lockShown(i);
        long rj = age(Ev.REJECT, i);
        int dx = rj < 300 ? Math.round(Mth.sin(rj / 22.0F) * 3.0F * (1.0F - rj / 300.0F)) : 0;
        int col = has ? rarityColor(st) : FxPalette.KUVA_PALE;
        float open = 1.0F - lock;
        float lineW = GuiShapes.pixels(g, lw());
        int n = cardShape(i, dx);
        float x0 = modX[i] - CELL + dx;
        float x1 = modX[i] + CELL + dx;
        float y0 = modY[i] - CELL_HH;
        float y1 = modY[i] + CELL_HH;

        GuiShapes.begin(g);
        GuiShapes.convexFill(g, pts, n, GuiShapes.argb(INK, 0.78F * e));
        if (has) {
            GuiShapes.convexFill(g, pts, n, GuiShapes.argb(col, 0.1F * e * open));
        }
        if (hv && lock < 1.0F) {
            GuiShapes.convexFill(g, pts, n, GuiShapes.argb(FxPalette.KUVA_PALE, 0.08F * open));
        }
        if (lock > 0.0F) {
            GuiShapes.hairline(g, x0 + 4, y1 - 4, x1 - 4, y0 + 4, lineW, GuiShapes.argb(FxPalette.ASH, 0.2F * e * lock));
            GuiShapes.hairline(g, x0 + 4, y0 + 4, x1 - 4, y1 - 4, lineW, GuiShapes.argb(FxPalette.ASH, 0.12F * e * lock));
            GuiShapes.polyline(g, pts, n, true, lineW, GuiShapes.argb(FxPalette.ASH, 0.15F * e * lock));
        }
        if (open > 0.0F) {
            float fa = (has ? 0.6F : hv ? 0.5F : 0.2F) * e * open;
            GuiShapes.polyline(g, pts, n, true, lineW, GuiShapes.argb(col, fa));
            float dy = modY[i] + 10.0F;
            if (has) {
                fill(g, x0 + CHAMFER + 1, y0, x1 - 1, y0 + 1, col, 0.9F * e * open);
                if (ModuleLevelHelper.isLevelSystemEnabled()) {
                    drawRankPips(g, i, st, col, e * open, dx);
                } else {
                    GuiShapes.diamondFill(g, modX[i] + dx, dy, 2.0F, 2.5F, GuiShapes.argb(col, 0.85F * e * open));
                }
            } else {
                GuiShapes.diamondOutline(g, modX[i] + dx, dy, 2.0F, 2.5F, lineW,
                        GuiShapes.argb(FxPalette.KUVA_PALE, 0.25F * e * open));
            }
        }
        GuiShapes.end();
        if (has && open > 0.0F && ModuleLevelHelper.isLevelSystemEnabled()) {
            drawRankFlash(g, i, st, col, e * open);
        }
        if (rj < 300) {
            int c = GuiShapes.argb(FxPalette.KUVA, 0.5F * (1.0F - rj / 300.0F));
            additive(g, () -> GuiShapes.convexFill(g, pts, cardShape(i, 0), c));
        }
        if (hv && open > 0.0F) {
            // 悬停：外面再套一圈淡光
            int c = GuiShapes.argb(col, 0.18F * open);
            additive(g, () -> GuiShapes.frame(g, modX[i] - CELL - 1, modY[i] - CELL_HH - 1, modX[i] + CELL + 1,
                    modY[i] + CELL_HH + 1, lw(), c));
        }
    }

    /**
     * 卡底的等级刻度，一级一颗（满级不超过 5 级时每颗宽一倍）；满级超过 10 级的改成一根按比例填的条。
     * 刚装上时从左往右一颗颗亮起来；打开界面时本来就装着的直接是亮的
     */
    private void drawRankPips(GuiGraphics g, int i, ItemStack st, int col, float a, int dx) {
        int max = ModuleLevelHelper.getMaxLevel();
        int lv = ModuleLevelHelper.getModuleLevel(st);
        long ins = age(Ev.PLACE, i);
        float y = modY[i] + 9.0F;
        float mid = modX[i] + dx;
        if (max > 10) {
            float w = 2 * CELL - 6;
            float x0 = mid - w * 0.5F;
            long fillMs = RANK_PIP_MS + RANK_PIP_STEP * 6L;
            float u = ins < fillMs ? easeOut(seg(ins, RANK_PIP_MS, fillMs)) : 1.0F;
            fill(g, x0, y, x0 + w, y + 2, FxPalette.KUVA_PALE, 0.15F * a);
            fill(g, x0, y, x0 + w * lv / max * u, y + 2, col, 0.95F * a);
            return;
        }
        int pw = max <= 5 ? 2 : 1;
        int x = Math.round(mid - (max * pw + max - 1) * 0.5F);
        for (int k = 0; k < max; k++) {
            boolean lit = k < lv && ins >= RANK_PIP_MS + (long) RANK_PIP_STEP * k;
            fill(g, x, y, x + pw, y + 2, lit ? col : FxPalette.KUVA_PALE, (lit ? 0.95F : 0.18F) * a);
            x += pw + 1;
        }
    }

    /** 等级刻度一颗颗亮起时每颗白闪一下；满级的卡刻度底下一道光慢慢呼吸 */
    private void drawRankFlash(GuiGraphics g, int i, ItemStack st, int col, float a) {
        int max = ModuleLevelHelper.getMaxLevel();
        int lv = ModuleLevelHelper.getModuleLevel(st);
        long ins = age(Ev.PLACE, i);
        float y = modY[i] + 9.0F;
        boolean pips = max <= 10;
        int pw = max <= 5 ? 2 : 1;
        int x0 = Math.round(modX[i] - (max * pw + max - 1) * 0.5F);
        long now = KuvaFxDirector.clockMs();
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        if (pips && ins < RANK_PIP_MS + (long) RANK_PIP_STEP * lv + 220) {
            for (int k = 0; k < lv; k++) {
                long since = ins - RANK_PIP_MS - (long) RANK_PIP_STEP * k;
                if (since >= 0 && since < 220) {
                    int x = x0 + k * (pw + 1);
                    fill(g, x - 1, y - 1, x + pw + 1, y + 3, FxPalette.WHITE, 0.8F * (1.0F - since / 220.0F) * a);
                }
            }
        }
        if (lv >= max) {
            float br = 0.5F + 0.5F * Mth.sin((now % 2200L) / 2200.0F * Mth.TWO_PI);
            float w = pips ? max * pw + max - 1 : 2 * CELL - 6;
            float gx0 = modX[i] - w * 0.5F;
            fill(g, gx0 - 1, y + 3, gx0 + w + 1, y + 4, col, (0.25F + 0.35F * br) * a);
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    /** 卡形的 6 个点写进 {@link #pts}，返回点数 */
    private int cardShape(int i, int dx) {
        float x0 = modX[i] - CELL + dx;
        float x1 = modX[i] + CELL + dx;
        float y0 = modY[i] - CELL_HH;
        float y1 = modY[i] + CELL_HH;
        float[] p = pts;
        p[0] = x0 + CHAMFER;
        p[1] = y0;
        p[2] = x1;
        p[3] = y0;
        p[4] = x1;
        p[5] = y1 - CHAMFER;
        p[6] = x1 - CHAMFER;
        p[7] = y1;
        p[8] = x0;
        p[9] = y1;
        p[10] = x0;
        p[11] = y0 + CHAMFER;
        return 6;
    }

    /** 装着模组的卡，边上一道光绕着走（每张错开，走一圈歇一会儿） */
    private void drawGlints(GuiGraphics g, long now, long t) {
        if (t < 900) {
            return;
        }
        float lineW = GuiShapes.pixels(g, lw());
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (int i = 0; i < MODULES; i++) {
            ItemStack st = installed(i);
            if (st.isEmpty()) {
                continue;
            }
            float u = ((now + i * 431L) % GLINT_PERIOD) / (GLINT_PERIOD * GLINT_RUN);
            if (u > 1.0F) {
                continue;
            }
            int n = cardShape(i, 0);
            float per = 0.0F;
            for (int k = 0; k < n; k++) {
                int k2 = (k + 1) % n;
                per += Mth.sqrt(Mth.square(pts[k2 * 2] - pts[k * 2]) + Mth.square(pts[k2 * 2 + 1] - pts[k * 2 + 1]));
            }
            int col = rarityColor(st);
            float fade = Mth.sin(u * Mth.PI);
            float s = per * inOut(u);
            glint(g, n, s, 8.0F, lineW * 2.5F, GuiShapes.argb(col, 0.35F * fade));
            glint(g, n, s, 8.0F, lineW, GuiShapes.argb(FxPalette.KUVA_PALE, 0.9F * fade));
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    /** 沿着 {@link #pts} 里的闭合折线，从周长位置 s 起画一段长 len 的光（跨过起点就接着从头画） */
    private void glint(GuiGraphics g, int n, float s, float len, float lineW, int argb) {
        float pos = 0.0F;
        float end = s + len;
        for (int lap = 0; lap < 2; lap++) {
            for (int k = 0; k < n; k++) {
                int k2 = (k + 1) % n;
                float ax = pts[k * 2];
                float ay = pts[k * 2 + 1];
                float bx = pts[k2 * 2];
                float by = pts[k2 * 2 + 1];
                float seg = Mth.sqrt(Mth.square(bx - ax) + Mth.square(by - ay));
                float from = Math.max(s, pos);
                float to = Math.min(pos + seg, end);
                if (to > from && seg > 0.0F) {
                    float u0 = (from - pos) / seg;
                    float u1 = (to - pos) / seg;
                    GuiShapes.hairline(g, ax + (bx - ax) * u0, ay + (by - ay) * u0, ax + (bx - ax) * u1,
                            ay + (by - ay) * u1, lineW, argb);
                }
                pos += seg;
                if (pos >= end) {
                    return;
                }
            }
        }
    }

    /**
     * 叉画多少（0~1）。上限只在武器上台、拿走时变：上台后八个叉从左上往右下一个个淡掉，拿走时一个个回来
     */
    private float lockShown(int i) {
        boolean locked = i >= moduleLimit();
        if (weaponSlot < 0) {
            return locked ? 1.0F : 0.0F;
        }
        if (locked) {
            long tk = age(Ev.TAKE, weaponSlot);
            return tk < 600 ? seg(tk, 30 * i, 30 * i + 160) : 1.0F;
        }
        long pl = age(Ev.PLACE, weaponSlot);
        return pl < 800 ? 1.0F - seg(pl, 120 + 30 * i, 280 + 30 * i) : 0.0F;
    }

    // ---------- 托架框 ----------

    /** 托架框的下沿：容量条下面再留 BAY_MY */
    private int bayBottom() {
        return barY + 3 + BAY_MY;
    }

    /**
     * 模组块外面的托架框，和卡一样左上、右下切角，把八张卡和容量条整块框在里面：
     * 外圈一道虚线沿框慢慢走，里圈一道实线，三颗光点沿里圈绕
     */
    private void drawBayFrame(GuiGraphics g, long now, float a) {
        if (a <= 0.0F) {
            return;
        }
        int x0 = modX[0] - CELL - BAY_MX;
        int x1 = modX[3] + CELL + BAY_MX;
        int y0 = modY[0] - CELL_HH - BAY_MY;
        int y1 = bayBottom();
        float lineW = GuiShapes.pixels(g, lw());
        int n = chamferRect(bayOuter, x0, y0, x1, y1, BAY_CHAMFER);
        chamferRect(bayInner, x0 + 3, y0 + 3, x1 - 3, y1 - 3, BAY_CHAMFER - 1);
        // 虚线一节 3、周期按周长取整（约 6），绕一圈接缝处不断
        float per = perimeter(bayOuter, n);
        float period = per / Math.max(1, Math.round(per / 6.0F));
        float off = (now % 30000L) / 30000.0F * per;
        GuiShapes.begin(g);
        dashes(g, bayOuter, n, off, 3.0F, period, lineW, GuiShapes.argb(FxPalette.KUVA_PALE, 0.10F * a));
        GuiShapes.polyline(g, bayInner, n, true, lineW, GuiShapes.argb(FxPalette.KUVA, 0.08F * a));
        GuiShapes.end();
        float inPer = perimeter(bayInner, n);
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (int k = 0; k < 3; k++) {
            float s = ((now % 12000L) / 12000.0F + k / 3.0F) * inPer;
            for (int tail = 0; tail < 4; tail++) {
                float[] q = pointOn(bayInner, n, s - tail * 1.2F);
                fill(g, q[0] - 0.5F, q[1] - 0.5F, q[0] + 0.5F, q[1] + 0.5F, FxPalette.KUVA,
                        0.6F * (1.0F - tail * 0.25F) * a);
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    /** 左上、右下切角的框（和卡同一种切法）写进 p，返回点数 */
    private static int chamferRect(float[] p, float x0, float y0, float x1, float y1, float c) {
        p[0] = x0 + c;
        p[1] = y0;
        p[2] = x1;
        p[3] = y0;
        p[4] = x1;
        p[5] = y1 - c;
        p[6] = x1 - c;
        p[7] = y1;
        p[8] = x0;
        p[9] = y1;
        p[10] = x0;
        p[11] = y0 + c;
        return 6;
    }

    private static float perimeter(float[] p, int n) {
        float per = 0.0F;
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            per += Mth.sqrt(Mth.square(p[k2 * 2] - p[k * 2]) + Mth.square(p[k2 * 2 + 1] - p[k * 2 + 1]));
        }
        return per;
    }

    /** 闭合折线上周长位置 s 那一点（s 可以超出一圈或是负的），结果放在共用的数组里 */
    private float[] pointOn(float[] p, int n, float s) {
        float per = perimeter(p, n);
        s = ((s % per) + per) % per;
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            float ax = p[k * 2];
            float ay = p[k * 2 + 1];
            float seg = Mth.sqrt(Mth.square(p[k2 * 2] - ax) + Mth.square(p[k2 * 2 + 1] - ay));
            if (s <= seg || k == n - 1) {
                float u = seg > 0.0F ? Math.min(1.0F, s / seg) : 0.0F;
                bayPt[0] = ax + (p[k2 * 2] - ax) * u;
                bayPt[1] = ay + (p[k2 * 2 + 1] - ay) * u;
                return bayPt;
            }
            s -= seg;
        }
        return bayPt;
    }

    /** 沿闭合折线画虚线：从周长位置 offset 起，每 period 一节、一节长 dash，跨过拐角的一节折过去接着画 */
    private static void dashes(GuiGraphics g, float[] p, int n, float offset, float dash, float period, float lineW,
                               int argb) {
        float pos = 0.0F;
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            float ax = p[k * 2];
            float ay = p[k * 2 + 1];
            float bx = p[k2 * 2];
            float by = p[k2 * 2 + 1];
            float len = Mth.sqrt(Mth.square(bx - ax) + Mth.square(by - ay));
            if (len > 0.0F) {
                float s = offset + Mth.floor((pos - offset) / period) * period;
                for (; s < pos + len; s += period) {
                    float u0 = (Math.max(s, pos) - pos) / len;
                    float u1 = (Math.min(s + dash, pos + len) - pos) / len;
                    if (u1 > u0) {
                        GuiShapes.hairline(g, ax + (bx - ax) * u0, ay + (by - ay) * u0, ax + (bx - ax) * u1,
                                ay + (by - ay) * u1, lineW, argb);
                    }
                }
            }
            pos += len;
        }
    }

    // ---------- 容量条 ----------

    /**
     * 卡下面一条八段的条：一段对应一格，装着的填稀有度色，空的只有框，没解锁的压暗；
     * 放进去那段从左往右填满、闪一下，拿走时从右往左退掉
     */
    private void drawBar(GuiGraphics g, long now, long t) {
        float e = phase(t, 700, 1000);
        if (e <= 0.0F) {
            return;
        }
        float bx0 = modX[0] - CELL;
        float bx1 = modX[3] + CELL;
        float gap = 2.0F;
        float segW = (bx1 - bx0 - gap * (MODULES - 1)) / MODULES;
        GuiShapes.begin(g);
        for (int i = 0; i < MODULES; i++) {
            float x0 = bx0 + i * (segW + gap);
            float x1 = x0 + segW;
            if (i >= moduleLimit()) {
                fill(g, x0, barY, x1, barY + 3, FxPalette.ASH, 0.08F * e);
                continue;
            }
            fill(g, x0, barY, x1, barY + 3, INK, 0.6F * e);
            ItemStack st = installed(i);
            if (!st.isEmpty()) {
                long ins = age(Ev.PLACE, i);
                float u = ins < 220 ? easeOut(ins / 220.0F) : 1.0F;
                fill(g, x0, barY, x0 + segW * u, barY + 3, rarityColor(st), 0.85F * e);
            } else {
                Fx tk = last(Ev.TAKE, i);
                long rm = tk == null ? Long.MAX_VALUE : now - tk.t0();
                if (tk != null && rm < 200) {
                    float u = 1.0F - easeOut(rm / 200.0F);
                    fill(g, x0, barY, x0 + segW * u, barY + 3, rarityColor(tk.stack()), 0.85F * e);
                }
                frame(g, x0, barY, x1, barY + 3, FxPalette.KUVA_PALE, 0.2F * e);
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (int i = 0; i < MODULES; i++) {
            long ins = age(Ev.PLACE, i);
            if (ins < 360 && !installed(i).isEmpty()) {
                float x0 = bx0 + i * (segW + gap);
                fill(g, x0 - 1, barY - 1, x0 + segW + 1, barY + 4, FxPalette.KUVA_PALE, 0.6F * (1.0F - ins / 360.0F));
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    // ---------- 右栏 ----------

    /**
     * 右栏清单：一行一个，左边一道稀有度色条；刚放进去的名字「解码」出来、底下亮一道。名字和等级一行放不下时名字折成两行
     * （等级留在第一行右边）；折了以后属性加成剩不到 {@link #MIN_STAT_ROWS} 行就都不折、截断，悬停看全名。返回清单下沿
     */
    private int drawList(GuiGraphics g, float eF) {
        int x0 = rx0;
        float cw = rx1 - rx0;
        int y = hairY + 8;
        if (rowCount == 0) {
            for (String line : wrapEven(emptyDetail().getString(), cw)) {
                text(g, line, x0, y, FxPalette.ASH, 0.6F * eF);
                y += 10;
            }
            rowTop[0] = y;
            return y;
        }
        boolean showLevel = ModuleLevelHelper.isLevelSystemEnabled() && cw >= LEVEL_MIN_WIDTH;
        int rowH = rowHeight();
        String[][] lines = new String[rowCount][];
        String[] lvs = new String[rowCount];
        int wrapped = 0;
        for (int k = 0; k < rowCount; k++) {
            ItemStack st = menu.getSlot(rows[k]).getItem();
            lvs[k] = showLevel ? "Lv." + ModuleLevelHelper.getModuleLevel(st) : "";
            float room = cw - 5 - (showLevel ? textW(lvs[k], 0.0F) + 4 : 0);
            lines[k] = splitName(plainName(st), room, cw - 5);
            if (lines[k].length > 1) {
                wrapped++;
            }
        }
        // 折行吃掉的高度：属性加成（标题 14 + 至少几行）放不下就不折
        int statsNeed = 8 + 14 + MIN_STAT_ROWS * STAT_ROW;
        if (wrapped > 0 && y + rowCount * rowH + wrapped * WRAP_LINE + statsNeed > sepB) {
            for (int k = 0; k < rowCount; k++) {
                if (lines[k].length > 1) {
                    ItemStack st = menu.getSlot(rows[k]).getItem();
                    float room = cw - 5 - (showLevel ? textW(lvs[k], 0.0F) + 4 : 0);
                    lines[k] = new String[]{fit(plainName(st), room)};
                }
            }
        }
        for (int k = 0; k < rowCount; k++) {
            int i = rows[k];
            ItemStack st = menu.getSlot(i).getItem();
            int col = rarityColor(st);
            boolean hv = listHover == i || cellHover == i;
            long ins = age(Ev.PLACE, i);
            int extra = (lines[k].length - 1) * WRAP_LINE;
            int yy = y;
            rowTop[k] = yy;

            GuiShapes.begin(g);
            if (hv) {
                fill(g, x0, yy - 1, rx1, yy + 9 + extra, FxPalette.KUVA_PALE, 0.06F);
            }
            fill(g, x0, yy, x0 + 2, yy + 8 + extra, col, 0.9F * eF);
            GuiShapes.end();
            float ta = (hv ? 1.0F : 0.9F) * eF;
            drawDecode(g, lines[k][0], x0 + 5, yy, col, ta, ins, 35, 0.0F);
            if (lines[k].length > 1) {
                // 第二行接着第一行往下解码
                drawDecode(g, lines[k][1], x0 + 5, yy + WRAP_LINE, col, ta, ins - 35L * lines[k][0].length(), 35, 0.0F);
            }
            if (showLevel) {
                textR(g, lvs[k], rx1, yy, FxPalette.ASH, 0.7F * eF);
            }
            if (hv) {
                hlFade(g, x0 + 5, rx1, yy + 10 + extra, col, 0.5F * eF);
            }
            // 悬停卡时原版自己出说明，这里只管悬停清单行
            if (listHover == i) {
                tipStack = st;
            }
            if (ins < 500) {
                float k2 = 0.8F * (1.0F - ins / 500.0F);
                int ly = yy + 10 + extra;
                additive(g, () -> hlFade(g, x0, rx1, ly, col, k2));
            }
            y += rowH + extra;
        }
        rowTop[rowCount] = y;
        return y;
    }

    /**
     * 模组名分成一行或两行：第一行宽 first（右边要放等级），第二行宽 rest。优先在空格处断（「灭亡 节肢动物 Prime」
     * 断成「灭亡 节肢动物 / Prime」），没有合适的空格就按字断；两行还放不下，第二行截断
     */
    private String[] splitName(String name, float first, float rest) {
        if (textW(name, 0.0F) <= first) {
            return new String[]{name};
        }
        int cut = -1;
        for (int k = name.length() - 1; k > 0; k--) {
            if (name.charAt(k) == ' ' && textW(name.substring(0, k), 0.0F) <= first) {
                cut = k;
                break;
            }
        }
        if (cut < 0) {
            cut = name.length() - 1;
            while (cut > 1 && textW(name.substring(0, cut), 0.0F) > first) {
                cut--;
            }
        }
        String a = name.substring(0, cut).stripTrailing();
        String b = name.substring(cut).stripLeading();
        return new String[]{a, fit(b, rest)};
    }

    /**
     * 属性加成：所有已装模组的词条按面板的口径加起来（单张夹值 × 等级倍率，合计再夹一次），放不下的行收成「另有 N 项」。
     * 每行下面一根长短条，按这一页里最大的那个数折算。装上 / 取下模组时，一道光从那张卡飞到这里（{@link #drawStreaks}），
     * 到了标题下划线亮一下，变了的词条再一条条错开着变（{@link #drawStatRow}）；新词条等光到了才占一行，
     * 去掉的词条淡掉以后下面的行平滑地补上来
     */
    private void drawStats(GuiGraphics g, int top, long now, float eF) {
        statsTop = top;
        statMaxFirst = 0;
        float dt = statClock == Long.MIN_VALUE ? 1000.0F : Math.min(200.0F, now - statClock);
        statClock = now;
        float k = 1.0F - (float) Math.exp(-dt / 70.0F);
        if (stats.isEmpty() || top > sepB - 24) {
            return;
        }
        text(g, tr("kuvalich.armory.stats"), rx0, top, FxPalette.TEXT_MID, 0.9F * eF);
        hl(g, rx0, rx1, top + 10, FxPalette.KUVA_PALE, 0.25F * eF);
        float arrive = 0.0F;
        int arriveRgb = FxPalette.KUVA_PALE;
        for (Streak s : streaks) {
            long a = now - s.t0() - STREAK_MS;
            if (a >= 0 && a < 500 && 1.0F - a / 500.0F > arrive) {
                arrive = 1.0F - a / 500.0F;
                arriveRgb = s.rgb();
            }
        }
        if (arrive > 0.0F) {
            float ar = arrive;
            int ac = arriveRgb;
            additive(g, () -> {
                hl(g, rx0, rx1, top + 10, ac, 0.9F * ar);
                fill(g, rx0, top - 1, rx1, top + 10, ac, 0.08F * ar);
            });
        }
        int y0 = top + 14;
        int visible = 0;
        double maxAbs = 1.0e-6;
        for (Stat s : stats) {
            if (now >= s.born) {
                visible++;
                maxAbs = Math.max(maxAbs, Math.max(Math.abs(s.to), Math.abs(s.value(now))));
            }
        }
        statScale = Float.isNaN(statScale) ? (float) maxAbs : statScale + ((float) maxAbs - statScale) * k;
        int room = Math.max(0, (sepB - 2 - y0) / STAT_ROW);
        // 放不下时最后一行留给提示，其余的用滚轮翻
        int view = visible <= room ? visible : Math.max(0, room - 1);
        statMaxFirst = Math.max(0, visible - view);
        statFirst = Mth.clamp(statFirst, 0, statMaxFirst);
        float shiftTo = statFirst * STAT_ROW;
        statShift = Math.abs(shiftTo - statShift) < 0.05F ? shiftTo : statShift + (shiftTo - statShift) * k;
        statAreaTop = y0 - 1;
        statAreaBottom = y0 + Math.max(view, 1) * STAT_ROW;
        float cw = rx1 - rx0;
        int row = 0;
        // 裁到看得见的几行；右边留给栏外的增减标
        clip(g, rx0 - 2, y0 - 1, width, y0 + view * STAT_ROW);
        for (Stat s : stats) {
            if (now < s.born) {
                continue;
            }
            float target = y0 + row * STAT_ROW;
            s.y = Float.isNaN(s.y) ? target : s.y + (target - s.y) * k;
            int yy = Math.round(s.y - statShift);
            if (yy > y0 - STAT_ROW && yy < y0 + view * STAT_ROW) {
                drawStatRow(g, s, yy, cw, now, eF);
            }
            row++;
        }
        unclip(g);
        if (view > 0 && view < visible) {
            int below = visible - view - statFirst;
            String hint = below > 0 ? tr("kuvalich.armory.more", below) : tr("kuvalich.armory.more_above", statFirst);
            text(g, fit(hint, cw - 4), rx0, y0 + view * STAT_ROW, FxPalette.ASH, 0.5F * eF);
            // 滚动条：栏右边一道细轨，亮的一截是看得见的那几行
            float track = view * STAT_ROW - 2;
            float t0 = y0 + track * statShift / (visible * STAT_ROW);
            float t1 = t0 + track * view / visible;
            GuiShapes.begin(g);
            fill(g, rx1 + 2, y0, rx1 + 3, y0 + track, FxPalette.KUVA_PALE, 0.08F * eF);
            fill(g, rx1 + 2, t0, rx1 + 3, t1, FxPalette.KUVA_PALE, 0.45F * eF);
            GuiShapes.end();
        }
    }

    /** 鼠标在属性加成上滚轮：一格翻一行 */
    @Override
    protected boolean scrollPage(double mx, double my, double delta) {
        if (statMaxFirst <= 0 || mx < rx0 || mx >= rx1 + 4 || my < statAreaTop || my >= statAreaBottom + STAT_ROW) {
            return false;
        }
        int to = Mth.clamp(statFirst - (int) Math.signum(delta), 0, statMaxFirst);
        if (to == statFirst) {
            return false;
        }
        statFirst = to;
        return true;
    }

    /**
     * 属性加成的一行。开始变的那一刻起：数字一秒滚到新值，数值先发白再回本色、弹一下；增（绿）/ 减（红）色的光从左扫过整行、
     * 慢慢退掉；行右边（栏外的空白里）留一个带箭头的增减标约两秒半；长短条跟着数字伸缩、条头亮着。
     * 新词条从右边滑进来，去掉的词条名字上划一道删除线再淡掉
     */
    private void drawStatRow(GuiGraphics g, Stat s, int y, float cw, long now, float eF) {
        double v = s.value(now);
        long since = now - s.t0;
        boolean live = since >= 0;
        float alive = s.leaving ? 1.0F - clamp01((since - STAT_ROLL_MS * 0.4F) / (STAT_ROLL_MS * 0.6F)) : 1.0F;
        long age = now - s.born;
        float enter = s.born > 0 ? clamp01(age / 250.0F) : 1.0F;
        float slide = s.born > 0 && age < 320 ? 10.0F * (1.0F - easeOut(age / 320.0F)) : 0.0F;
        float a = eF * alive * enter;
        if (a <= 0.004F) {
            return;
        }
        String val = formatStat(s.key, v);
        float vw = textW(val, 0.0F);
        int base = v >= 0 ? PanelPalette.BONUS : PanelPalette.PENALTY;
        int dRgb = s.delta >= 0 ? PanelPalette.BONUS : PanelPalette.PENALTY;
        float wipe = live && since < STAT_WIPE_MS ? 1.0F - since / (float) STAT_WIPE_MS : 0.0F;
        float frac = (float) Math.min(1.0D, Math.abs(v) / Math.max(1.0e-6, statScale));

        GuiShapes.begin(g);
        hl(g, rx0, rx1, y + 9, FxPalette.KUVA_PALE, 0.05F * a);
        if (frac > 0.002F) {
            hl(g, rx0, rx0 + cw * frac, y + 9, base, 0.45F * a);
        }
        GuiShapes.end();

        String name = fit(s.name, cw - vw - 4);
        text(g, name, rx0 + slide, y, FxPalette.ASH, (0.75F + 0.25F * wipe) * a);
        float flash = live && since < 700 ? 1.0F - since / 700.0F : 0.0F;
        int vRgb = FxCurve.lerpColor(base, FxPalette.WHITE, 0.8F * flash);
        float va = (0.85F + 0.15F * wipe) * a;
        float pop = live && since < 350 ? 0.22F * Mth.square(1.0F - since / 350.0F) : 0.0F;
        if (pop > 0.0F) {
            g.pose().pushPose();
            g.pose().translate(rx1 + slide, y + 4, 0.0F);
            g.pose().scale(1.0F + pop, 1.0F + pop, 1.0F);
            textR(g, val, 0, -4, vRgb, va);
            g.pose().popPose();
        } else {
            textR(g, val, rx1 + slide, y, vRgb, va);
        }
        if (s.leaving && live) {
            float u = easeOut(clamp01(since / 400.0F));
            float nw = textW(name, 0.0F);
            GuiShapes.begin(g);
            hl(g, rx0, rx0 + nw * u, y + 4, PanelPalette.PENALTY, 0.85F * a);
            GuiShapes.end();
        }
        if (wipe > 0.0F) {
            float front = easeOut(clamp01(since / 380.0F));
            boolean rolling = since < STAT_ROLL_MS && frac > 0.002F;
            additive(g, () -> {
                fill(g, rx0, y - 1, rx0 + cw * front, y + 9, dRgb, 0.16F * wipe * a);
                if (front < 1.0F) {
                    fill(g, rx0 + cw * front - 1, y - 1, rx0 + cw * front, y + 9, FxPalette.WHITE, 0.7F * a);
                }
                if (rolling) {
                    float hx = rx0 + cw * frac;
                    fill(g, hx - 1, y + 8, hx + 1, y + 10, FxPalette.WHITE, 0.8F * a);
                }
            });
        }
        if (live && !s.leaving && since < STAT_DELTA_MS && Math.abs(s.delta) > 1.0e-4) {
            String d = formatStat(s.key, s.delta);
            if (s.delta > 0 && !d.startsWith("+")) {
                d = "+" + d;
            }
            if (width - rx1 - 4 >= textW(d, 0.0F) + 11) {
                float da = (since < 150 ? since / 150.0F
                        : since > STAT_DELTA_MS - 700 ? (STAT_DELTA_MS - since) / 700.0F : 1.0F) * eF;
                float dx = rx1 + 5 + 2.0F * easeOut(clamp01(since / 300.0F));
                triangle(g, dx + 2.5F, y + 4, s.delta > 0, dRgb, da);
                text(g, d, dx + 7, y, dRgb, da);
            }
        }
    }

    /** 增减标的小三角：增朝上、减朝下 */
    private void triangle(GuiGraphics g, float x, float y, boolean up, int rgb, float a) {
        if (a <= 0.004F) {
            return;
        }
        float h = 2.0F;
        tri[0] = x - 2.5F;
        tri[1] = up ? y + h : y - h;
        tri[2] = x + 2.5F;
        tri[3] = up ? y + h : y - h;
        tri[4] = x;
        tri[5] = up ? y - h : y + h;
        GuiShapes.begin(g);
        GuiShapes.convexFill(g, tri, 3, GuiShapes.argb(rgb, a));
        GuiShapes.end();
    }

    /**
     * 从卡飞向属性栏的光：从卡的右边出来，在两列卡之间的缝里往上走到卡的上方，横穿到右边的分隔线，
     * 沿分隔线落到属性栏标题的高度，再往右进栏；拖一截尾巴。到站时那里扩一圈环
     */
    private void drawStreaks(GuiGraphics g, long now, float eF) {
        if (streaks.isEmpty()) {
            return;
        }
        int ty = (statsTop >= 0 ? statsTop : hairY + 8) + 4;
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (Streak s : streaks) {
            long t = now - s.t0();
            if (t < 0 || t > STREAK_MS) {
                continue;
            }
            int i = s.card();
            int gapX = modX[i] + GRID_SP / 2;
            int[] p = {modX[i] + CELL + 1, modY[i], gapX, modY[i], gapX, pTop - 2, sepR, pTop - 2, sepR, ty, rx0 - 3, ty};
            float[] cum = cumulative(p);
            float d = cum[cum.length - 1] * inOut(t / (float) STREAK_MS);
            for (int tail = 0; tail < 12; tail++) {
                float dd = d - tail * 3.0F;
                if (dd < 0.0F) {
                    break;
                }
                float[] q = pointAt(p, cum, dd);
                float r = tail == 0 ? 1.5F : 0.6F;
                fill(g, q[0] - r, q[1] - r, q[0] + r, q[1] + r, tail == 0 ? FxPalette.WHITE : s.rgb(),
                        (tail == 0 ? 0.95F : 0.75F - tail * 0.055F) * eF);
            }
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);
    }

    /** 状态行：手上拿着这一种模组时，写它的名字和等级（未揭示的写装不了） */
    private void drawCarrying(GuiGraphics g, ItemStack carried, float eF) {
        float maxW = sepR - sepL - 16;
        if (AbstractModule.isRandom(carried)) {
            textC(g, fit(tr("kuvalich.armory.unrevealed"), maxW), cx, statusY, FxPalette.KUVA, 0.9F * eF);
            return;
        }
        String name = plainName(carried);
        String s = ModuleLevelHelper.isLevelSystemEnabled()
                ? tr("kuvalich.armory.carry", name, ModuleLevelHelper.getModuleLevel(carried), ModuleLevelHelper.getMaxLevel())
                : tr("kuvalich.armory.carry.plain", name);
        textC(g, fit(s, maxW), cx, statusY, rarityColor(carried), 0.9F * eF);
    }

    /**
     * 左栏底下的一块小表：标题、细线，一行一项「名称 … 数值」，数值右对齐；某一项的数值变了，那一行亮一下。
     * 放不下的行不画
     *
     * @return 表格下面的 y
     */
    protected int drawInfo(GuiGraphics g, String title, List<Info> rows, int y, float eT, long now) {
        if (rows.isEmpty() || y + 26 > sepB) {
            return y;
        }
        text(g, title, lx0, y, FxPalette.ASH, eT);
        hlFade(g, lx0, lx1, y + 11, FxPalette.KUVA_PALE, 0.3F * eT);
        int yy = y + 15;
        for (Info r : rows) {
            if (yy + 9 > sepB) {
                break;
            }
            String old = infoSeen.put(r.key(), r.value());
            if (old != null && !old.equals(r.value())) {
                infoAt.put(r.key(), now);
            }
            long ch = now - infoAt.getOrDefault(r.key(), Long.MIN_VALUE / 4);
            float fresh = ch < 900 ? 1.0F - ch / 900.0F : 0.0F;
            float vw = textR(g, r.value(), lx1, yy, r.rgb(), (0.9F + 0.1F * fresh) * eT);
            text(g, fit(r.key(), lx1 - lx0 - vw - 4), lx0, yy, FxPalette.ASH, (0.7F + 0.3F * fresh) * eT);
            if (fresh > 0.0F) {
                int ry = yy;
                additive(g, () -> fill(g, lx0, ry - 1, lx1, ry + 9, FxPalette.KUVA_PALE, 0.14F * fresh));
            }
            yy += 11;
        }
        return yy;
    }

    /** 装配概要的几项：已装配几个、模组等级合计、满级几张（后两项只在开了模组等级时有） */
    protected void loadoutInfo(List<Info> out) {
        int n = installedCount();
        out.add(new Info(tr("kuvalich.armory.info.installed"), n + " / " + moduleLimit(), FxPalette.BONE));
        if (!ModuleLevelHelper.isLevelSystemEnabled() || n == 0) {
            return;
        }
        int max = ModuleLevelHelper.getMaxLevel();
        int sum = 0;
        int maxed = 0;
        for (int i = 0; i < MODULES; i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (!st.isEmpty()) {
                int lv = ModuleLevelHelper.getModuleLevel(st);
                sum += lv;
                if (lv >= max) {
                    maxed++;
                }
            }
        }
        out.add(new Info(tr("kuvalich.armory.info.level"), sum + " / " + n * max,
                sum >= n * max ? FxPalette.ACCENT : FxPalette.BONE));
        out.add(new Info(tr("kuvalich.armory.info.maxed"), maxed + " / " + n,
                maxed == n ? FxPalette.ACCENT : FxPalette.BONE));
    }

    /**
     * 汇总里的数值：和提示框同一套格式，几处小数值多留一位——击杀叠层每层的加成常常只有零点几，
     * 取整会显示成「+0%」；低等级模组的爆炸半径只加零点零几米，一位小数是「+0.0m」。
     * 攻击速度那套格式本身不带正负号，汇总是「加了多少」，补上
     */
    private static String formatStat(String key, double v) {
        ValueFormat fmt = WeaponPanelComposer.sourceFormat(key);
        String sign = v < 0 ? "-" : "+";
        double pct = Math.abs(v * 100.0);
        if (fmt == ValueFormat.PERCENT_SIGNED && pct >= 0.05 && pct < 0.95) {
            return sign + String.format(Locale.ROOT, "%.1f%%", pct);
        }
        if (fmt == ValueFormat.PERCENT_1F) {
            return sign + String.format(Locale.ROOT, "%.1f%%", pct);
        }
        double m = Math.abs(v * 2.0);
        if (fmt == ValueFormat.METERS_DELTA && m >= 0.005 && m < 0.095) {
            return sign + String.format(Locale.ROOT, "%.2fm", m);
        }
        return fmt.format(v);
    }

    /** 汇总里的属性名：击杀叠层的词条是每杀一个叠一层，名字后面标出来，免得和同名的常驻属性看成一回事 */
    private static String statName(String key) {
        String name = PanelStyle.shortNameOf(key);
        return key.startsWith("killStack") ? name + Component.translatable("kuvalich.armory.per_kill").getString() : name;
    }

    /**
     * 各格的模组变了才重算属性；变了的词条从当前显示的值滚到新值，没了的滚到 0 再去掉。
     * 除了打开界面时第一次算，变了的那张卡各放一道光飞向属性栏，词条等光到了才开始变，几条一起变时错开
     */
    private void refreshStats(long now) {
        boolean animate = statsPrimed;
        statsPrimed = true;
        boolean changed = false;
        for (int i = 0; i < MODULES; i++) {
            ItemStack st = installed(i);
            if (!ItemStack.matches(st, statBasis[i])) {
                if (animate) {
                    streaks.add(new Streak(i, rarityColor(st.isEmpty() ? statBasis[i] : st), now));
                }
                statBasis[i] = st.copy();
                changed = true;
            }
        }
        stats.removeIf(s -> s.leaving && now - s.t0 > STAT_ROLL_MS);
        streaks.removeIf(s -> now - s.t0() > STREAK_MS + 600);
        if (!changed) {
            return;
        }
        long start = animate ? now + STREAK_MS : now - STAT_ROLL_MS - STAT_DELTA_MS;
        int order = 0;
        Map<String, Double> sum = new LinkedHashMap<>();
        for (ItemStack st : statBasis) {
            if (st.isEmpty()) {
                continue;
            }
            double mult = ModuleLevelHelper.getEffectiveMultiplier(st);
            for (Map.Entry<String, Double> e : AbstractModule.getAttributes(st)) {
                sum.merge(e.getKey(), ModuleConfig.clampAttributeValue(e.getKey(), e.getValue()) * mult, Double::sum);
            }
        }
        for (Stat s : stats) {
            Double raw = sum.remove(s.key);
            if (raw == null && s.leaving) {
                continue;
            }
            double to = raw == null ? 0.0D : ModuleConfig.clampAttributeTotal(s.key, raw);
            if (Math.abs(to - s.to) > 1.0e-4 || s.leaving != (raw == null)) {
                double cur = s.value(now);
                s.from = cur;
                s.delta = to - cur;
                s.to = to;
                s.t0 = start + (long) STAT_STAGGER_MS * order++;
                s.leaving = raw == null;
            }
        }
        for (Map.Entry<String, Double> e : sum.entrySet()) {
            double to = ModuleConfig.clampAttributeTotal(e.getKey(), e.getValue());
            if (Math.abs(to) < 1.0e-3) {
                continue;
            }
            Stat s = new Stat(e.getKey(), statName(e.getKey()));
            s.to = to;
            s.delta = to;
            s.t0 = start + (long) STAT_STAGGER_MS * order++;
            s.born = animate ? s.t0 : 0L;
            stats.add(s);
        }
    }

    /** 压在物品上面：还没轮到的卡盖住、放进去那一下白闪、开场时卡里模组的盖板 */
    @Override
    protected void drawOverlay(GuiGraphics g, int mx, int my, long now, long t) {
        for (int i = 0; i < MODULES; i++) {
            Slot slot = menu.getSlot(i);
            int x0 = modX[i] - 9;
            int y0 = modY[i] - 11;
            int x1 = modX[i] + 9;
            int y1 = modY[i] + 7;
            if (pending(Ev.PLACE, i)) {
                GuiShapes.begin(g);
                fill(g, x0, y0, x1, y1, INK, 1.0F);
                GuiShapes.end();
                continue;
            }
            long ins = age(Ev.PLACE, i);
            if (slot.hasItem() && ins < 260) {
                int n = cardShape(i, 0);
                int c = GuiShapes.argb(FxPalette.KUVA_PALE, 0.5F * (1.0F - ins / 260.0F));
                additive(g, () -> GuiShapes.convexFill(g, pts, n, c));
            }
            float cov = 1.0F - phase(t, 600 + 50 * i, 900 + 50 * i);
            if (cov > 0.0F && slot.hasItem() && dimmed(t)) {
                GuiShapes.begin(g);
                fill(g, x0, y0, x1, y1, INK, cov);
                GuiShapes.end();
            }
        }
        drawLeftOverlay(g, mx, my, now, t);
    }

    /**
     * 刚装上一张模组：状态行换成「装配完成：名字 · 等级 N / M」（稀有度色，满级的写满级），底下一道同色细线从中间展开，
     * 停一会儿再淡回「已装配」。卡上方没有地方（上面一排顶着栏标题、下面一排顶着上面一排），所以放在这里。
     * 跟着武器一起上台的模组不算（八张一齐上，说哪张都不对）
     *
     * @return 这一行现在的不透明度，0 表示没在显示
     */
    private float drawPlaced(GuiGraphics g, float eF) {
        int pick = -1;
        long best = RANK_POP_MS;
        for (int i = 0; i < MODULES; i++) {
            long ins = age(Ev.PLACE, i);
            boolean withWeapon = weaponSlot >= 0 && age(Ev.PLACE, weaponSlot) - ins < 1000L;
            if (ins < best && !withWeapon && !installed(i).isEmpty()) {
                best = ins;
                pick = i;
            }
        }
        if (pick < 0) {
            return 0.0F;
        }
        ItemStack st = installed(pick);
        String name = plainName(st);
        String s;
        boolean maxed = false;
        if (ModuleLevelHelper.isLevelSystemEnabled()) {
            int max = ModuleLevelHelper.getMaxLevel();
            int lv = ModuleLevelHelper.getModuleLevel(st);
            maxed = lv >= max;
            s = maxed ? tr("kuvalich.armory.placed.max", name, lv) : tr("kuvalich.armory.placed", name, lv, max);
        } else {
            s = tr("kuvalich.armory.placed.plain", name);
        }
        float a = best < 150 ? best / 150.0F : best > RANK_POP_MS - 500 ? (RANK_POP_MS - best) / 500.0F : 1.0F;
        int rgb = rarityColor(st);
        float w = textC(g, fit(s, sepR - sepL - 16), cx, statusY, maxed ? FxPalette.ACCENT : rgb, a * eF);
        float half = (w * 0.5F + 4.0F) * easeOut(clamp01(best / 300.0F));
        GuiShapes.begin(g);
        hl(g, cx - half, cx + half, statusY + 11, rgb, 0.8F * a * eF);
        GuiShapes.end();
        return a;
    }

    // ==================== 小工具 ====================

    /** 这一格现在算装着的模组（还没轮到揭示的当空的） */
    private ItemStack installed(int i) {
        return pending(Ev.PLACE, i) ? ItemStack.EMPTY : menu.getSlot(i).getItem();
    }

    /** 栏标题：字距 2 放不下就收成 1、0，再放不下截断 */
    protected void drawTitle(GuiGraphics g, String s, float x, float maxW, float a) {
        for (float sp = 2.0F; sp >= 0.0F; sp -= 1.0F) {
            if (textW(s, sp) <= maxW) {
                heading(g, s, x, a, sp);
                return;
            }
        }
        heading(g, fit(s, maxW), x, a, 0.0F);
    }

    /** 物品名去掉颜色代码（未揭示模组的名字里带着 §d 这类） */
    protected static String plainName(ItemStack st) {
        String s = ChatFormatting.stripFormatting(st.getHoverName().getString());
        return s == null ? "" : s;
    }

    protected static int rarityColor(ItemStack st) {
        int r = ForgeOpClassifier.rarity(st);
        return r >= 0 ? GuiFx.rarityColor(r) : FxPalette.BONE;
    }

    protected int installedCount() {
        int n = 0;
        for (int i = 0; i < MODULES; i++) {
            if (menu.getSlot(i).hasItem()) {
                n++;
            }
        }
        return n;
    }

    private int rowHeight() {
        return small ? 11 : 12;
    }

    private void collectRows() {
        rowCount = 0;
        for (int i = 0; i < MODULES; i++) {
            if (menu.getSlot(i).hasItem() && !pending(Ev.PLACE, i)) {
                rows[rowCount++] = i;
            }
        }
    }

    /** 鼠标下的清单行（行高不一样，按上一帧排好的各行的顶算） */
    private int rowAt(int mx, int my) {
        if (mx < rx0 || mx >= rx1) {
            return -1;
        }
        for (int k = 0; k < rowCount; k++) {
            if (my >= rowTop[k] - 1 && my < rowTop[k + 1] - 1) {
                return rows[k];
            }
        }
        return -1;
    }
}
