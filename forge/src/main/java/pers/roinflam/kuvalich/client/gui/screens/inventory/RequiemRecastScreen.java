package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier.Op;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.client.gui.guide.GuideClientConfig;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.world.inventory.RequiemRecastMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 安魂之铸界面（三合一 / 分解），画成玄骸档案的另一页
 *
 * <ul>
 *   <li>左栏：配方表三行（安魂卡三合一、模组三合一、分解），这次放的东西对应哪一行就点亮哪一行，
 *       手上拿着、或者鼠标停在背包里的卡 / 模组时，它能走的那几行半亮；下面「持有材料」列出背包里的卡、
 *       两种模组和内融核心各有多少；</li>
 *   <li>中栏：上面是产出格的炉膛（主角），下面三个输入格，三条线汇到一根总线、往上接进炉膛。
 *       三合一做出来的那一刻三张输入被吃掉，光沿三条线流进炉膛，流到了产出从下往上扫出来；
 *       只放一张模组时是分解预览，炉膛换成虚线框、内融核心色。下部是背包；</li>
 *   <li>右栏「产出」：已经有产出时写它是什么、拿走会怎样；还没放齐时按已经放的几张预告会出什么、
 *       放第三张就会立刻合成；放的组合合不了时写为什么。栏底是三步「流程」；</li>
 *   <li>取走产出时的三合一 / 分解演出和声音照旧由 {@link GuiFx} 放。</li>
 * </ul>
 *
 * <p>三合一的几个开关读的是服务端同步下来的配置（{@link GuideClientConfig}），没同步时用本机配置。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemRecastScreen extends ForgePageScreen<RequiemRecastMenu> {

    private static final int OUT = ForgeOpClassifier.RECAST_OUTPUT;
    /** 配方表的三行 */
    private static final int ROW_CARDS = 0;
    private static final int ROW_MODULES = 1;
    private static final int ROW_DECOMPOSE = 2;

    /** 输入组合的种类 */
    private enum Kind {
        EMPTY, CARDS, WEAPON, WARFRAME, INVALID
    }

    /** 已经放进去的几张合起来看：什么种类、几张、各品质几张、合不了的原因 */
    private record Plan(Kind kind, int count, int[] tiers, @Nullable String why) {
        boolean valid() {
            return kind != Kind.INVALID && kind != Kind.EMPTY;
        }

        boolean modules() {
            return kind == Kind.WEAPON || kind == Kind.WARFRAME;
        }
    }

    /** 品质名直接借用武器模组的物品名（青铜模组、白银模组……） */
    private static final String[] TIER_KEYS = {
            "item.kuvalich.item_common_module", "item.kuvalich.item_uncommon_module",
            "item.kuvalich.item_rare_module", "item.kuvalich.item_prime_module",
            "item.kuvalich.item_riven_module"};

    private final List<Recipe> recipes = new ArrayList<>();
    private final List<Stock> stocks = new ArrayList<>();

    /** 版式：炉膛中心 y、输入格的中线 x 和格子 y、总线的 y、状态行的 y */
    private int pTop;
    private int ocy;
    private final int[] inX = new int[3];
    private int iy;
    private int busY;
    /** 各进料格到总线为止的那一截；常驻光点这一帧走哪条、什么颜色 */
    private final int[][] toBus = new int[3][];
    private final int[][] pulsePaths = new int[3][];
    private final int[] pulseRgbs = new int[3];
    private int statusY;
    /** 三个输入格到炉膛的连线 */
    private final int[][] feed = new int[3][];

    /** 服务端配置的快照，版本变了才重取 */
    private Map<String, String> cfg = Map.of();
    private int cfgRevision = Integer.MIN_VALUE;

    public RequiemRecastScreen(RequiemRecastMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        recipes.clear();
        recipes.add(new Recipe(new ItemStack(KuvaLichItems.KHRA_CARD.get()),
                Component.translatable("kuvalich.recast.row.cards"),
                Component.translatable("kuvalich.recast.row.cards.sub"), this::cardsTip));
        recipes.add(new Recipe(new ItemStack(KuvaLichItems.ITEM_RARE_MODULE.get()),
                Component.translatable("kuvalich.recast.row.modules"),
                Component.translatable("kuvalich.recast.row.modules.sub"), this::modulesTip));
        recipes.add(new Recipe(new ItemStack(KuvaLichItems.ENDO.get()),
                Component.translatable("kuvalich.recast.row.decompose"),
                Component.translatable("kuvalich.recast.row.decompose.sub"), this::decomposeTip));
        int modules = 1 << ROW_MODULES | 1 << ROW_DECOMPOSE;
        stocks.clear();
        stocks.add(new Stock(new ItemStack(KuvaLichItems.KHRA_CARD.get()), s -> s.getItem() instanceof AbstractRequiemCard,
                1 << ROW_CARDS, Component.translatable("kuvalich.recast.stock.cards")));
        stocks.add(new Stock(new ItemStack(KuvaLichItems.ITEM_RARE_MODULE.get()),
                s -> s.getItem() instanceof AbstractWeaponModule, modules,
                Component.translatable("kuvalich.recast.stock.weapon")));
        stocks.add(new Stock(new ItemStack(KuvaLichItems.WARFRAME_RARE_MODULE.get()),
                s -> s.getItem() instanceof AbstractWarframeModule, modules,
                Component.translatable("kuvalich.recast.stock.warframe")));
        stocks.add(new Stock(new ItemStack(KuvaLichItems.ENDO.get()), s -> s.is(KuvaLichItems.ENDO.get()),
                1 << ROW_DECOMPOSE, KuvaLichItems.ENDO.get().getDescription()));
    }

    @Override
    protected void layout() {
        pTop = small ? hairY + 1 : hairY + 5;
        chHalf = small ? 18 : 21;
        ocy = pTop + (small ? 22 : 27);
        int sp = small ? 54 : 60;
        iy = pTop + (small ? 62 : 74);
        busY = iy - (small ? 9 : 11);
        statusY = pTop + (small ? 88 : 104);
        for (int i = 0; i < 3; i++) {
            inX[i] = cx + (i - 1) * sp;
            place(menu.getSlot(i), inX[i] - 8, iy);
            // 格框占 y-1 ~ y+16，线从框外一格起步；中间那格直接往上，拐点重合不碍事
            feed[i] = new int[]{inX[i], iy - 2, inX[i], busY, cx, busY, cx, ocy + chHalf + 1};
            toBus[i] = new int[]{inX[i], iy - 2, inX[i], busY, cx, busY};
        }
        resultSlot = menu.getSlot(OUT);
        place(resultSlot, cx - 8, ocy - 8);
        placeInventory();
        track(0, 1, 2);
    }

    // ==================== 演出 ====================

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int button, ClickType type) {
        Op op = Op.NONE;
        int rarity = -1;
        int source = -1;
        Slot out = menu.getSlot(OUT);
        // 在 super 之前判：super 会在本地先把输出拿走
        if (slot == out && ForgeOpClassifier.takesOutput(out, menu.getCarried(), button, type)) {
            op = ForgeOpClassifier.classify(menu);
            if (op == Op.DECOMPOSE) {
                source = ForgeOpClassifier.singleModuleInput(menu);
                rarity = ForgeOpClassifier.rarity(menu.getSlot(source).getItem());
            } else if (op == Op.MERGE) {
                rarity = ForgeOpClassifier.rarity(out.getItem());
            }
        }
        super.slotClicked(slot, slotId, button, type);
        if (op == Op.DECOMPOSE && source >= 0) {
            fx.decompose(menu.getSlot(source), out, rarity);
        } else if (op == Op.MERGE) {
            fx.merge(menu.getSlot(0), menu.getSlot(1), menu.getSlot(2), out, rarity);
        }
    }

    /**
     * 产出出现：分解预览是那一张模组的线流过来、内融核心色、虚线炉膛；
     * 三合一是三条线一起流进来（三张这时已经被吃掉），炉膛按产出的稀有度上色
     */
    @Override
    protected Arrival arrival(ItemStack result) {
        if (ForgeOpClassifier.classify(menu) == Op.DECOMPOSE) {
            int src = ForgeOpClassifier.singleModuleInput(menu);
            return new Arrival(ENDO, true, src >= 0 ? new int[][]{feed[src]} : new int[0][]);
        }
        int r = ForgeOpClassifier.rarity(result);
        return new Arrival(r >= 0 ? GuiFx.rarityColor(r) : FxPalette.KUVA, false, feed);
    }

    @Override
    protected Component rejectMessage(Slot slot, ItemStack carried) {
        return Component.translatable("kuvalich.recast.reject");
    }

    // ==================== 画 ====================

    @Override
    protected void drawPage(GuiGraphics g, int mx, int my, long now, long t, float eF) {
        float eT = phase(t, T_TEXT_IN, T_TEXT_DONE);
        refreshConfig();
        Plan plan = plan();
        boolean hasOut = resultSlot.hasItem();
        boolean decompose = hasOut && ForgeOpClassifier.classify(menu) == Op.DECOMPOSE;

        // ---- 左栏：配方表、持有材料 ----
        int y = drawLeftHead(g, Component.translatable("kuvalich.recast.recipes"), eF, eT);
        int lit = litRows(plan, hasOut, decompose);
        int hint = hintRows(mx, my);
        y = drawRecipes(g, recipes, y, lit, hint, eT, now);
        drawStock(g, stocks, y + 4, lit | hint, eT, now);

        // ---- 中栏：背光、连线、炉膛、输入格 ----
        drawBacklight(g, cx - 110, pTop - 30, 220, 150, 0.85F * eT);
        drawCircuit(g, phase(t, 600, 950), hasOut, now);
        drawFlows(g);
        drawChamber(g, mx, my, now, phase(t, 550, 900));
        for (int i = 0; i < 3; i++) {
            drawCell(g, menu.getSlot(i), mx, my, phase(t, 650 + 120 * i, 1050 + 120 * i), now);
        }
        drawStatus(g, plan, hasOut, decompose, eF);
        drawDrift(g, cx, pTop + 6, 150, 92, now, eF);

        // ---- 右栏：产出、流程 ----
        drawRightHead(g, Component.translatable("kuvalich.recast.output"), eF);
        int stepTop = drawSteps(g, steps(plan, hasOut), eF, now);
        drawLines(g, detail(plan, hasOut, decompose), rx0, hairY + 8, rx1 - rx0, eF, stepTop - 6);
    }

    /** 流程三步：放卡、出产出、取走；放的组合合不了时第一步是红叉 */
    private List<Step> steps(Plan plan, boolean hasOut) {
        boolean shown = hasOut && resultShown();
        List<Step> out = new ArrayList<>(3);
        if (!hasOut && plan.kind() == Kind.INVALID) {
            out.add(new Step(tr("kuvalich.recast.invalid"), STEP_FAILED));
        } else {
            out.add(new Step(tr("kuvalich.recast.step.put"), hasOut ? STEP_DONE : STEP_ACTIVE,
                    hasOut ? null : plan.count() + "/3"));
        }
        out.add(new Step(tr("kuvalich.recast.step.make"), shown ? STEP_DONE : STEP_PENDING));
        out.add(new Step(tr("kuvalich.recast.step.take"), shown ? STEP_ACTIVE : STEP_PENDING));
        return out;
    }

    @Override
    protected void drawOverlay(GuiGraphics g, int mx, int my, long now, long t) {
        for (int i = 0; i < 3; i++) {
            drawCellOverlay(g, menu.getSlot(i), t, 800 + 120 * i, 1100 + 120 * i);
        }
        drawChamberOverlay(g, t, 700, 1000);
        drawStockOverlay(g, now, t);
    }

    /**
     * 三条进料线 + 总线 + 进炉膛的一截：常亮一层淡底，放了东西的那条亮起来、光点一直往总线流
     * （有产出时一路流进炉膛）；总线上一个往上的小箭头
     */
    private void drawCircuit(GuiGraphics g, float e, boolean hasOut, long now) {
        if (e <= 0.0F) {
            return;
        }
        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            path(g, feed[i], 1.0F, FxPalette.KUVA_PALE, 0.12F * e, null);
        }
        for (int i = 0; i < 3; i++) {
            if (menu.getSlot(i).hasItem() && !pending(Ev.PLACE, i)) {
                // 只亮到总线为止，进炉膛那一截等产出出来再亮
                path(g, new int[]{inX[i], iy - 2, inX[i], busY, cx, busY}, 1.0F, FxPalette.KUVA, 0.55F * e, null);
            }
        }
        if (hasOut && resultShown()) {
            vl(g, cx, busY, ocy + chHalf + 1, resultRgb, 0.6F * e);
        }
        GuiShapes.end();
        int mid = Math.round((busY + ocy + chHalf + 1) * 0.5F);
        chevronUp(g, cx, mid, hasOut ? resultRgb : FxPalette.ASH, (hasOut ? 0.85F : 0.35F) * e);
        boolean into = hasOut && resultShown();
        for (int i = 0; i < 3; i++) {
            boolean on = menu.getSlot(i).hasItem() && !pending(Ev.PLACE, i);
            pulsePaths[i] = !on ? null : into ? feed[i] : toBus[i];
            pulseRgbs[i] = into ? resultRgb : FxPalette.KUVA;
        }
        drawPulses(g, pulsePaths, pulseRgbs, 1700, now, e);
    }

    /** 中栏状态行：提示 → 产出 → 合不了的原因 → 放了几张 */
    private void drawStatus(GuiGraphics g, Plan plan, boolean hasOut, boolean decompose, float eF) {
        if (drawMessage(g, cx, statusY, eF)) {
            return;
        }
        if (hasOut) {
            if (!resultShown()) {
                return;
            }
            String s = decompose ? tr("kuvalich.recast.status.preview") : tr("kuvalich.recast.status.done");
            textC(g, s, cx, statusY, decompose ? ENDO : FxPalette.BONE, 0.85F * eF);
            long r = resultAge();
            if (!decompose && r < 600) {
                additive(g, () -> hl(g, cx - 70, cx + 70, statusY + 11, FxPalette.KUVA_PALE, 0.8F * (1.0F - r / 600.0F)));
            }
            return;
        }
        switch (plan.kind()) {
            case EMPTY -> textC(g, tr("kuvalich.recast.status.empty"), cx, statusY, FxPalette.ASH, 0.7F * eF);
            case INVALID -> textC(g, plan.why(), cx, statusY, FxPalette.KUVA, 0.9F * eF);
            default -> {
                if (plan.count() == 2) {
                    textC(g, tr("kuvalich.recast.status.last"), cx, statusY, FxPalette.BONE, 0.85F * eF);
                } else {
                    textC(g, tr("kuvalich.recast.status.count", plan.count()), cx, statusY, FxPalette.ASH, 0.7F * eF);
                }
            }
        }
    }

    /** 右栏：有产出写产出，没有就按已经放的几张预告 */
    private List<Line> detail(Plan plan, boolean hasOut, boolean decompose) {
        List<Line> out = new ArrayList<>();
        if (hasOut && !resultShown()) {
            // 流线还在路上：三张已经被吃掉，先空着，等揭示了再写产出
            return out;
        }
        if (hasOut) {
            ItemStack res = resultSlot.getItem();
            long r = resultAge();
            if (decompose) {
                int src = ForgeOpClassifier.singleModuleInput(menu);
                out.add(new Line(tr("kuvalich.recast.decompose"), FxPalette.TEXT_MID, 1.0F, 2));
                out.add(new Line(typed(res.getHoverName().getString() + " ×" + res.getCount(), r), ENDO, 1.0F, 4));
                if (src >= 0) {
                    out.add(new Line(tr("kuvalich.recast.decompose.from", menu.getSlot(src).getItem().getHoverName()),
                            KuvaPalette.MUTED, 0.85F, 4));
                }
                out.add(new Line(tr("kuvalich.recast.decompose.warn"), FxPalette.KUVA, 0.9F, 3));
                out.add(new Line(tr("kuvalich.recast.decompose.cancel"), FxPalette.ASH, 0.6F, 0));
            } else {
                out.add(new Line(tr("kuvalich.recast.done"), FxPalette.TEXT_MID, 1.0F, 2));
                out.add(new Line(typed(res.getHoverName().getString(), r), resultRgb, 1.0F, 4));
                if (res.getItem() instanceof AbstractRequiemCard) {
                    out.add(new Line(tr("kuvalich.recast.done.card"), KuvaPalette.MUTED, 0.85F, 3));
                } else {
                    out.add(new Line(tr("kuvalich.recast.done.module"), KuvaPalette.MUTED, 0.85F, 3));
                }
                out.add(new Line(tr("kuvalich.recast.done.take"), FxPalette.ASH, 0.6F, 0));
            }
            return out;
        }
        switch (plan.kind()) {
            case EMPTY -> out.add(new Line(tr("kuvalich.recast.detail_empty"), FxPalette.ASH, 0.6F, 0));
            case INVALID -> {
                out.add(new Line(tr("kuvalich.recast.invalid"), FxPalette.KUVA, 1.0F, 3));
                out.add(new Line(plan.why(), KuvaPalette.MUTED, 0.85F, 3));
                out.add(new Line(tr("kuvalich.recast.invalid.fix"), FxPalette.ASH, 0.6F, 0));
            }
            case CARDS -> {
                out.add(new Line(tr("kuvalich.recast.row.cards"), FxPalette.BONE, 1.0F, 4));
                out.add(new Line(tr("kuvalich.recast.cards.out"), KuvaPalette.MUTED, 0.85F, 4));
                if (plan.count() == 2) {
                    out.add(new Line(tr("kuvalich.recast.warn_third"), FxPalette.KUVA, 0.9F, 0));
                }
            }
            default -> {
                if (plan.count() == 1) {
                    // 单张模组：分解预览马上就到
                    out.add(new Line(tr("kuvalich.recast.row.decompose"), FxPalette.BONE, 1.0F, 4));
                    out.add(new Line(tr("kuvalich.recast.row.decompose.sub"), KuvaPalette.MUTED, 0.85F, 4));
                    out.add(new Line(tr("kuvalich.recast.single.merge"), FxPalette.ASH, 0.6F, 0));
                    return out;
                }
                out.add(new Line(tr("kuvalich.recast.row.modules"), FxPalette.BONE, 1.0F, 4));
                int single = singleTier(plan.tiers());
                boolean cross = on(plan.kind() == Kind.WARFRAME
                        ? ModConfig.KUVA_LICH.enableWarframeCrossTierRecast : ModConfig.KUVA_LICH.enableCrossTierRecast);
                if (single >= 0) {
                    String name = tierName(single);
                    out.add(new Line(tr("kuvalich.recast.modules.out", name, name), tierRgb(single), 0.95F, 3));
                    if (single < 3 && cross) {
                        out.add(new Line(tr("kuvalich.recast.modules.cross_hint"), KuvaPalette.MUTED, 0.75F, 4));
                    }
                } else {
                    out.add(new Line(tr("kuvalich.recast.modules.mixed"), KuvaPalette.MUTED, 0.85F, 2));
                    for (int k = 0; k < 3; k++) {
                        if (plan.tiers()[k] > 0) {
                            out.add(new Line(tierName(k) + " ×" + plan.tiers()[k], tierRgb(k), 0.9F, 0));
                        }
                    }
                    Line last = out.remove(out.size() - 1);
                    out.add(new Line(last.text(), last.rgb(), last.alpha(), 4));
                }
                if (plan.count() == 2) {
                    out.add(new Line(tr("kuvalich.recast.warn_third"), FxPalette.KUVA, 0.9F, 0));
                }
            }
        }
        return out;
    }

    // ==================== 配方表 ====================

    /** 点亮哪几行：有产出看产出，没有看已经放的几张 */
    private int litRows(Plan plan, boolean hasOut, boolean decompose) {
        if (hasOut) {
            if (decompose) {
                return 1 << ROW_DECOMPOSE;
            }
            return 1 << (resultSlot.getItem().getItem() instanceof AbstractRequiemCard ? ROW_CARDS : ROW_MODULES);
        }
        return switch (plan.kind()) {
            case CARDS -> 1 << ROW_CARDS;
            case WEAPON, WARFRAME -> plan.count() == 1 ? 1 << ROW_DECOMPOSE : 1 << ROW_MODULES;
            default -> 0;
        };
    }

    /** 半亮哪几行：手上拿着的、或者鼠标停着的那件东西能走的 */
    private int hintRows(int mx, int my) {
        ItemStack s = menu.getCarried();
        if (s.isEmpty()) {
            Slot over = slotAt(mx, my);
            s = over == null ? ItemStack.EMPTY : over.getItem();
        }
        if (s.getItem() instanceof AbstractRequiemCard) {
            return 1 << ROW_CARDS;
        }
        if (s.getItem() instanceof AbstractWeaponModule || s.getItem() instanceof AbstractWarframeModule) {
            return (1 << ROW_MODULES) | (1 << ROW_DECOMPOSE);
        }
        return 0;
    }

    private List<Component> cardsTip() {
        List<Component> out = new ArrayList<>();
        out.add(tip("kuvalich.recast.row.cards", FxPalette.BONE));
        out.add(tip("kuvalich.recast.tip.cards", KuvaPalette.MUTED));
        out.add(tip("kuvalich.recast.tip.instant", FxPalette.KUVA));
        return out;
    }

    /** 模组三合一的说明按服务端开关列：哪些品质能合、能不能混放、战甲模组行不行 */
    private List<Component> modulesTip() {
        refreshConfig();
        List<Component> out = new ArrayList<>();
        out.add(tip("kuvalich.recast.row.modules", FxPalette.BONE));
        out.add(tip("kuvalich.recast.tip.modules", KuvaPalette.MUTED));
        out.add(tip(on(ModConfig.KUVA_LICH.enableCrossTierRecast)
                ? "kuvalich.recast.tip.cross_on" : "kuvalich.recast.tip.cross_off", KuvaPalette.MUTED));
        if (on(ModConfig.KUVA_LICH.enablePrimeModuleRecast)) {
            out.add(tip("kuvalich.recast.tip.prime", KuvaPalette.MUTED));
        }
        if (on(ModConfig.KUVA_LICH.enableRivenModuleRecast)) {
            out.add(tip("kuvalich.recast.tip.riven", KuvaPalette.MUTED));
        }
        out.add(tip(on(ModConfig.KUVA_LICH.enableWarframeModuleRecast)
                ? "kuvalich.recast.tip.warframe_on" : "kuvalich.recast.tip.warframe_off", KuvaPalette.MUTED));
        out.add(tip("kuvalich.recast.tip.instant", FxPalette.KUVA));
        return out;
    }

    private List<Component> decomposeTip() {
        List<Component> out = new ArrayList<>();
        out.add(tip("kuvalich.recast.row.decompose", FxPalette.BONE));
        out.add(tip("kuvalich.recast.tip.decompose", KuvaPalette.MUTED));
        out.add(tip("kuvalich.recast.decompose.warn", FxPalette.KUVA));
        return out;
    }

    private static MutableComponent tip(String key, int rgb, Object... args) {
        return Component.translatable(key, args).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    // ==================== 预判（照抄 RequiemRecastMenu 的判断顺序） ====================

    /**
     * 已经放进去的几张合起来看。规则同 {@code RequiemRecastMenu#processCrafting}：
     * 全是安魂卡；或者全是武器模组 / 全是战甲模组（战甲还要总开关），裂罅只能三张裂罅、Prime 只能三张 Prime
     * 且各有开关，青铜 / 白银 / 黄金混放看跨品质开关。只放一张模组是分解，不看任何开关
     */
    private Plan plan() {
        int n = 0;
        int cards = 0;
        int weapon = 0;
        int warframe = 0;
        int[] tiers = new int[5];
        for (int i = 0; i < 3; i++) {
            ItemStack s = menu.getSlot(i).getItem();
            if (s.isEmpty()) {
                continue;
            }
            n++;
            if (s.getItem() instanceof AbstractRequiemCard) {
                cards++;
            } else if (s.getItem() instanceof AbstractWeaponModule) {
                weapon++;
            } else if (s.getItem() instanceof AbstractWarframeModule) {
                warframe++;
            }
            int r = ForgeOpClassifier.rarity(s);
            if (r >= 0) {
                tiers[r]++;
            }
        }
        if (n == 0) {
            return new Plan(Kind.EMPTY, 0, tiers, null);
        }
        if (cards > 0 && cards < n) {
            return invalid(n, tiers, "kuvalich.recast.why.mixed_card");
        }
        if (cards == n) {
            return new Plan(Kind.CARDS, n, tiers, null);
        }
        if (weapon > 0 && warframe > 0) {
            return invalid(n, tiers, "kuvalich.recast.why.mixed_kind");
        }
        Kind kind = weapon > 0 ? Kind.WEAPON : Kind.WARFRAME;
        if (n == 1) {
            return new Plan(kind, 1, tiers, null);
        }
        boolean wf = kind == Kind.WARFRAME;
        if (wf && !on(ModConfig.KUVA_LICH.enableWarframeModuleRecast)) {
            return invalid(n, tiers, "kuvalich.recast.why.warframe_off");
        }
        if (tiers[4] > 0) {
            if (tiers[4] < n) {
                return invalid(n, tiers, "kuvalich.recast.why.riven_mix");
            }
            if (!on(wf ? ModConfig.KUVA_LICH.enableWarframeRivenModuleRecast : ModConfig.KUVA_LICH.enableRivenModuleRecast)) {
                return invalid(n, tiers, "kuvalich.recast.why.riven_off");
            }
            return new Plan(kind, n, tiers, null);
        }
        if (tiers[3] > 0) {
            if (tiers[3] < n) {
                return invalid(n, tiers, "kuvalich.recast.why.prime_mix");
            }
            if (!on(wf ? ModConfig.KUVA_LICH.enableWarframePrimeModuleRecast : ModConfig.KUVA_LICH.enablePrimeModuleRecast)) {
                return invalid(n, tiers, "kuvalich.recast.why.prime_off");
            }
            return new Plan(kind, n, tiers, null);
        }
        if (singleTier(tiers) < 0
                && !on(wf ? ModConfig.KUVA_LICH.enableWarframeCrossTierRecast : ModConfig.KUVA_LICH.enableCrossTierRecast)) {
            return invalid(n, tiers, "kuvalich.recast.why.cross_off");
        }
        return new Plan(kind, n, tiers, null);
    }

    private static Plan invalid(int n, int[] tiers, String key) {
        return new Plan(Kind.INVALID, n, tiers, tr(key));
    }

    /** 只有一种品质时返回它，否则 −1 */
    private static int singleTier(int[] tiers) {
        int found = -1;
        for (int k = 0; k < tiers.length; k++) {
            if (tiers[k] > 0) {
                if (found >= 0) {
                    return -1;
                }
                found = k;
            }
        }
        return found;
    }

    private static String tierName(int tier) {
        return tr(TIER_KEYS[tier]);
    }

    private static int tierRgb(int tier) {
        return GuiFx.rarityColor(tier);
    }

    // ==================== 配置 ====================

    private void refreshConfig() {
        int rev = GuideClientConfig.revision();
        if (rev != cfgRevision) {
            cfgRevision = rev;
            cfg = GuideClientConfig.snapshot();
        }
    }

    /** 开关取同步下来的值；快照里没有这一项时退回本机配置 */
    private boolean on(ForgeConfigSpec.BooleanValue spec) {
        String v = cfg.get(String.join(".", spec.getPath()));
        if (v == null) {
            try {
                return spec.get();
            } catch (IllegalStateException e) {
                return false;
            }
        }
        return Boolean.parseBoolean(v);
    }
}
