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
import pers.roinflam.kuvalich.client.fx.FxPalette;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 武器、战甲两个军械库共用的一页：中栏模组卡、右栏已装清单和属性加成、底部图鉴入口
 *
 * <ul>
 *   <li>中栏：八张模组卡排成 2×4（切角卡形，装着的按稀有度着色、边上有一道光绕着走，没解锁的画叉），
 *       背后一圈慢慢转的全息椭圆环；卡下面一条八段的容量条，再下面一行是已装几个，放不进去时临时换成原因；</li>
 *   <li>电路：每张卡到装模组的那件东西（武器格 / 玩家）有一条折线，装着模组的那条亮着、光点一直往那边流；</li>
 *   <li>右栏：已装模组的清单（名字按稀有度着色，悬停一行显示说明、同时点亮它所在的卡），
 *       下面是各词条加起来的属性加成，装上 / 拿走时数字滚到新值；</li>
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
    private static final int LEVEL_MIN_WIDTH = 100;
    private static final String ELLIPSIS = "…";
    /** 卡边那道光绕一圈的周期、真正在走的那一段占比 */
    private static final int GLINT_PERIOD = 3600;
    private static final float GLINT_RUN = 0.45F;
    /** 电路光点：一趟的周期、在路上的那一段占比 */
    private static final int PULSE_PERIOD = 1900;
    private static final float PULSE_RUN = 0.7F;
    /** 属性数字滚到新值用的时间 */
    private static final int STAT_ROLL_MS = 450;

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
    /** 这一帧鼠标下的清单行对应的格号、鼠标下的模组卡；都没有是 −1 */
    private int listHover = -1;
    private int cellHover = -1;
    /** 这一帧鼠标下不是格子、但要显示物品说明的东西（清单行、武器投影） */
    protected ItemStack tipStack = ItemStack.EMPTY;

    /** 属性加成：上次算的时候各格是什么、各词条从哪个值滚到哪个值 */
    private final ItemStack[] statBasis = new ItemStack[MODULES];
    private final List<Stat> stats = new ArrayList<>();

    /** 画卡形用的点缓冲 */
    private final float[] pts = new float[2 * 8];

    private static final class Stat {
        final String key;
        final String name;
        double from;
        double to;
        long t0;
        boolean leaving;

        Stat(String key, String name) {
            this.key = key;
            this.name = name;
        }

        double value(long now) {
            float u = clamp01((now - t0) / (float) STAT_ROLL_MS);
            return from + (to - from) * easeOut(u);
        }
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
        pTop = small ? hairY + 4 : hairY + 8;
        for (int i = 0; i < MODULES; i++) {
            modX[i] = cx + Math.round((i % 4 - 1.5F) * GRID_SP);
            modY[i] = pTop + 16 + (i / 4) * ROW_SP;
            // 图标在卡的上部，下面留出画极性菱形的地方
            place(menu.getSlot(i), modX[i] - 8, modY[i] - 10);
        }
        gridBottom = modY[MODULES - 1] + CELL_HH;
        laneY = gridBottom + 5;
        barY = laneY + 6;
        statusY = barY + 7;
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
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
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

        // ---- 中栏：背光、全息环、标题、电路、八张卡、容量条、状态行 ----
        int gcy = (modY[0] + modY[MODULES - 1]) / 2;
        drawBacklight(g, cx - 110, pTop - 30, 220, 130, 0.85F * eT);
        drawHoloRing(g, cx, gcy, 82.0F, small ? 34.0F : 38.0F, now, eF);
        heading(g, Component.translatable("kuvalich.armory.mods").getString(), cx - 56, eF, 2.0F);
        hl(g, cx - 56, cx + 56, hairY, FxPalette.KUVA_PALE, 0.2F * eF);
        drawCircuits(g, now, eT);
        for (int i = 0; i < MODULES; i++) {
            drawCard(g, i, t);
        }
        drawGlints(g, now, t);
        drawBar(g, now, t);
        if (!drawMessage(g, cx, statusY, eF)) {
            if (!hasHost()) {
                textC(g, hostMissing().getString(), cx, statusY, FxPalette.ASH, 0.7F * eF);
            } else {
                int used = installedCount();
                int lim = moduleLimit();
                boolean full = lim > 0 && used >= lim;
                textC(g, Component.translatable("kuvalich.armory.count", used, lim).getString(), cx, statusY,
                        full ? FxPalette.BONE : FxPalette.ASH, (full ? 0.85F : 0.7F) * eF);
            }
        }
        drawDrift(g, cx, pTop + 6, 150, 70, now, eF);

        // ---- 右栏：已装模组、属性加成 ----
        drawTitle(g, Component.translatable("kuvalich.armory.installed").getString(), rx0, rx1 - rx0, eF);
        hl(g, rx0, rx1, hairY, FxPalette.KUVA_PALE, 0.35F * eF);
        int y = drawList(g, eF);
        drawStats(g, y + 8, now, eF);

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
                GuiShapes.diamondFill(g, modX[i] + dx, dy, 2.0F, 2.5F, GuiShapes.argb(col, 0.85F * e * open));
            } else {
                GuiShapes.diamondOutline(g, modX[i] + dx, dy, 2.0F, 2.5F, lineW,
                        GuiShapes.argb(FxPalette.KUVA_PALE, 0.25F * e * open));
            }
        }
        GuiShapes.end();
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

    /** 右栏清单：一行一个，左边一道稀有度色条；刚放进去的名字「解码」出来、底下亮一道。返回清单下沿 */
    private int drawList(GuiGraphics g, float eF) {
        int x0 = rx0;
        float cw = rx1 - rx0;
        int y = hairY + 8;
        if (rowCount == 0) {
            for (String line : wrap(emptyDetail().getString(), cw)) {
                text(g, line, x0, y, FxPalette.ASH, 0.6F * eF);
                y += 10;
            }
            return y;
        }
        boolean showLevel = ModuleLevelHelper.isLevelSystemEnabled() && cw >= LEVEL_MIN_WIDTH;
        int rowH = rowHeight();
        for (int k = 0; k < rowCount; k++) {
            int i = rows[k];
            ItemStack st = menu.getSlot(i).getItem();
            int col = rarityColor(st);
            boolean hv = listHover == i || cellHover == i;
            long ins = age(Ev.PLACE, i);
            String lv = showLevel ? "Lv." + ModuleLevelHelper.getModuleLevel(st) : "";
            float room = cw - 5 - (showLevel ? textW(lv, 0.0F) + 4 : 0);
            String name = fit(plainName(st), room);
            int yy = y;

            GuiShapes.begin(g);
            if (hv) {
                fill(g, x0, yy - 1, rx1, yy + 9, FxPalette.KUVA_PALE, 0.06F);
            }
            fill(g, x0, yy, x0 + 2, yy + 8, col, 0.9F * eF);
            GuiShapes.end();
            drawDecode(g, name, x0 + 5, yy, col, (hv ? 1.0F : 0.9F) * eF, ins, 35, 0.0F);
            if (showLevel) {
                textR(g, lv, rx1, yy, FxPalette.ASH, 0.7F * eF);
            }
            if (hv) {
                hlFade(g, x0 + 5, rx1, yy + 10, col, 0.5F * eF);
            }
            // 悬停卡时原版自己出说明，这里只管悬停清单行
            if (listHover == i) {
                tipStack = st;
            }
            if (ins < 500) {
                float k2 = 0.8F * (1.0F - ins / 500.0F);
                additive(g, () -> hlFade(g, x0, rx1, yy + 10, col, k2));
            }
            y += rowH;
        }
        return y;
    }

    /**
     * 属性加成：所有已装模组的词条按面板的口径加起来（单张夹值 × 等级倍率，合计再夹一次），
     * 数值滚到新值的那一下整行亮一下。放不下的行收成「还有 N 项」
     */
    private void drawStats(GuiGraphics g, int top, long now, float eF) {
        if (stats.isEmpty() || top > sepB - 24) {
            return;
        }
        text(g, Component.translatable("kuvalich.armory.stats").getString(), rx0, top, FxPalette.TEXT_MID, 0.9F * eF);
        hl(g, rx0, rx1, top + 10, FxPalette.KUVA_PALE, 0.25F * eF);
        int y = top + 14;
        int room = Math.max(0, (sepB - 2 - y) / 10);
        int shown = stats.size() <= room ? stats.size() : Math.max(0, room - 1);
        float cw = rx1 - rx0;
        for (int k = 0; k < shown; k++) {
            Stat s = stats.get(k);
            double v = s.value(now);
            String val = formatStat(s.key, v);
            float vw = textW(val, 0.0F);
            long since = now - s.t0;
            float fresh = since < STAT_ROLL_MS + 250 ? 1.0F - since / (float) (STAT_ROLL_MS + 250) : 0.0F;
            float alive = s.leaving ? 1.0F - clamp01(since / (float) STAT_ROLL_MS) : 1.0F;
            text(g, fit(s.name, cw - vw - 4), rx0, y, FxPalette.ASH, (0.75F + 0.25F * fresh) * eF * alive);
            textR(g, val, rx1, y, v >= 0 ? PanelPalette.BONUS : PanelPalette.PENALTY,
                    (0.85F + 0.15F * fresh) * eF * alive);
            if (fresh > 0.0F) {
                int yy = y;
                additive(g, () -> fill(g, rx0, yy - 1, rx1, yy + 9, FxPalette.KUVA_PALE, 0.12F * fresh));
            }
            y += 10;
        }
        if (shown < stats.size()) {
            text(g, Component.translatable("kuvalich.armory.more", stats.size() - shown).getString(), rx0, y,
                    FxPalette.ASH, 0.5F * eF);
        }
    }

    /**
     * 汇总里的数值：和提示框同一套格式，只是不到 1% 的百分比留一位小数——击杀叠层每层的加成常常只有零点几，
     * 取整会显示成「+0%」
     */
    private static String formatStat(String key, double v) {
        ValueFormat fmt = WeaponPanelComposer.sourceFormat(key);
        double pct = Math.abs(v * 100.0);
        if (fmt == ValueFormat.PERCENT_SIGNED && pct >= 0.05 && pct < 0.95) {
            return (v > 0 ? "+" : "-") + String.format(Locale.ROOT, "%.1f%%", pct);
        }
        return fmt.format(v);
    }

    /** 汇总里的属性名：击杀叠层的词条是每杀一个叠一层，名字后面标出来，免得和同名的常驻属性看成一回事 */
    private static String statName(String key) {
        String name = PanelStyle.shortNameOf(key);
        return key.startsWith("killStack") ? name + Component.translatable("kuvalich.armory.per_kill").getString() : name;
    }

    /** 各格的模组变了才重算属性；变了的词条从当前显示的值滚到新值，没了的滚到 0 再去掉 */
    private void refreshStats(long now) {
        boolean changed = false;
        for (int i = 0; i < MODULES; i++) {
            ItemStack st = installed(i);
            if (!ItemStack.matches(st, statBasis[i])) {
                statBasis[i] = st.copy();
                changed = true;
            }
        }
        stats.removeIf(s -> s.leaving && now - s.t0 > STAT_ROLL_MS);
        if (!changed) {
            return;
        }
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
                s.from = s.value(now);
                s.to = to;
                s.t0 = now;
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
            s.t0 = now;
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

    private int rowAt(int mx, int my) {
        int top = hairY + 7;
        if (mx < rx0 || mx >= rx1 || my < top) {
            return -1;
        }
        int k = (my - top) / rowHeight();
        return k < rowCount ? rows[k] : -1;
    }
}
