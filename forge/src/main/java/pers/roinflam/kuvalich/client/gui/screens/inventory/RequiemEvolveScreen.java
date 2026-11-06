package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier.Op;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.compat.tacz.TaczGunEnhanceUtil;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.item.Endo;
import pers.roinflam.kuvalich.item.Forma;
import pers.roinflam.kuvalich.item.Kuva;
import pers.roinflam.kuvalich.item.LichReliquary;
import pers.roinflam.kuvalich.item.RivenSliver;
import pers.roinflam.kuvalich.item.ShadowForma;
import pers.roinflam.kuvalich.item.module.warframe.WarframeRivenModule;
import pers.roinflam.kuvalich.item.module.weapon.WeaponRivenModule;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;
import pers.roinflam.kuvalich.module.level.ModuleLevelTooltipHelper;
import pers.roinflam.kuvalich.module.weapon.WeaponModuleHandler;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.KuvaWeaponUtil;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 安魂之融界面（融合、开光、塑形、裂罅循环、模组升级、枪械强化），画成玄骸档案的另一页
 *
 * <ul>
 *   <li>左栏：配方表，一行一种操作、图标是它的材料；当前这一步是哪种就点亮哪一行，
 *       手上拿着、或者鼠标停在背包里的东西能参与的几行半亮；悬停一行看物品槽、材料槽放什么，什么时候扣材料。
 *       下面「持有材料」列出背包里各种材料的数量；</li>
 *   <li>中栏：左格（物品）、中格（材料）、右边结果格的炉膛（主角）。左格的线从上面绕过去、中格的线从下面绕过去，
 *       都落到结果格，和融合演出走同一条路。结果出来时光沿两条线流进炉膛再揭示；
 *       预览类（融合、裂罅循环、模组升级，拿走才算数）的炉膛是虚线框，当场处理类是实线框。下部是背包；</li>
 *   <li>右栏「结果」：有结果时写这是哪种操作、等级怎么变、材料什么时候扣、有什么要当心的；
 *       只放了一边时列另一边能放什么；两边都放了却没有结果时写为什么。栏底是三步「流程」；</li>
 *   <li>取走结果时按这次是哪种操作放演出和声音（{@link GuiFx}），全部只在本人客户端；</li>
 *   <li>物品格里是已揭示的裂罅、材料是赤毒（或者正在挑多张洗的候选）时，状态行、右栏、流程换成裂罅循环的那一套，
 *       见 {@link RivenCycleView}。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemEvolveScreen extends ForgePageScreen<RequiemEvolveMenu> {

    private static final int IN = ForgeOpClassifier.EVOLVE_INPUT;
    private static final int MAT = ForgeOpClassifier.EVOLVE_MATERIAL;
    private static final int RES = ForgeOpClassifier.EVOLVE_RESULT;

    /** 两边都放了、结果还没出来时，等多久才说「凑不成」（服务端算结果要一个来回） */
    private static final long STUCK_GRACE_MS = 350L;
    /** 暗影塑形块的颜色（和它的演出同色） */
    private static final int ORDIS = 0xB9A0FF;

    /** 配方表的行 */
    private enum Row {
        FUSION("fusion"), IMBUE("imbue"), FORMA("forma"), RIVEN("riven"), RANK("rank"), GUN("gun");

        final String key;

        Row(String key) {
            this.key = key;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Recipe> recipes = new ArrayList<>();
    private final List<Stock> stocks = new ArrayList<>();

    /** 融合演出的两条线：左边输入格从上面绕过去，中间材料格从下面绕过去，都落到结果格 */
    private int pathFromInput = -1;
    private int pathFromMaterial = -1;
    private int[] topPath = new int[0];
    /** 常驻光点这一帧走哪几条 */
    private final int[][] pulsePaths = new int[2][];
    private static final int[] PULSE_RGBS = {FxPalette.KUVA, FxPalette.KUVA};
    private int[] bottomPath = new int[0];

    /** 版式：背光和飘点的参照 y、三格的中线 x、格子的中线 y、两条线的高度、状态行 */
    private int pTop;
    private int inX;
    private int matX;
    private int outX;
    private int ocy;
    private int above;
    private int below;
    private int statusY;

    /** 最后一次看到的非空材料 */
    @Nullable
    private Item seenMaterial;
    /** 当前结果出现那一刻的材料（当场处理类靠它判操作） */
    @Nullable
    private Item madeWith;
    private boolean hadResult;
    /** 当前结果是哪种操作（结果出现时判一次） */
    private Op resultOp = Op.NONE;

    /** 左格、中格上一帧的东西，和这个组合是从什么时候开始的 */
    private ItemStack prevIn = ItemStack.EMPTY;
    private ItemStack prevMat = ItemStack.EMPTY;
    private long comboAt;

    /** 裂罅循环（洗紫卡）的控件、候选和对照 */
    private final RivenCycleView riven;

    public RequiemEvolveScreen(RequiemEvolveMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.riven = new RivenCycleView(this, menu);
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();
        recipes.clear();
        addRow(Row.FUSION, KuvaLichItems.KUVA_SHILDEG.get());
        addRow(Row.IMBUE, KuvaLichItems.RIVEN_SLIVER.get());
        addRow(Row.FORMA, KuvaLichItems.FORMA.get());
        addRow(Row.RIVEN, KuvaLichItems.KUVA.get());
        if (ModuleLevelHelper.isLevelSystemEnabled()) {
            addRow(Row.RANK, KuvaLichItems.ENDO.get());
        }
        if (gunEnabled()) {
            addRow(Row.GUN, KuvaLichItems.LICH_RELIQUARY.get());
        }
        stocks.clear();
        addStock(KuvaLichItems.KUVA.get(), Row.RIVEN);
        if (ModuleLevelHelper.isLevelSystemEnabled()) {
            addStock(KuvaLichItems.ENDO.get(), Row.RANK);
        }
        addStock(KuvaLichItems.FORMA.get(), Row.FORMA);
        addStock(KuvaLichItems.SHADOW_FORMA.get(), Row.FORMA);
        addStock(KuvaLichItems.RIVEN_SLIVER.get(), Row.IMBUE);
        if (gunEnabled()) {
            addStock(KuvaLichItems.LICH_RELIQUARY.get(), Row.GUN);
        }
    }

    private void addStock(Item item, Row row) {
        stocks.add(new Stock(new ItemStack(item), s -> s.is(item), bit(row), item.getDescription()));
    }

    private void addRow(Row row, Item icon) {
        rows.add(row);
        recipes.add(new Recipe(new ItemStack(icon), Component.translatable("kuvalich.evolve.op." + row.key), null,
                () -> rowTip(row)));
    }

    /**
     * 炉膛半边和安魂之铸一样（窄屏 16、非窄屏 19），转弧半径按框角算。
     * 横向：物品格左沿到炉膛右沿整块在中栏居中（以前偏左 5 格多）；炉膛背后的全息环比炉膛往右多伸 1.1 个半边，
     * 居中以后环不能压到右分隔线和刻度，中栏只有 184 宽时就收紧材料格到炉膛这一段。
     * 竖向：上下两条线离炉膛 6 格，还要离「材料」两个字 3 格以上；整块在中栏里居中，上沿不高过分隔线顶
     */
    @Override
    protected void layout() {
        chHalf = small ? 16 : 19;
        int dIM = small ? 48 : 50;
        int dMO = small ? 64 : 72;
        float room = sepR - 4 - cx - chHalf * 1.1F;
        int over = Mth.ceil(dIM + dMO + 9 + chHalf - 2.0F * room);
        if (over > 0) {
            dMO -= over;
        }
        inX = cx - Math.round((dIM + dMO + chHalf - 9) * 0.5F);
        matX = inX + dIM;
        outX = matX + dMO;
        int arc = chamberArcOuter(chHalf);
        int lane = Math.max(chHalf + 6, 23);
        int statusOff = small ? 52 : 58;
        int top = middleTop(arc + 1 + statusOff + 11, sepT + 1, -1);
        ocy = top + arc + 1;
        pTop = ocy - (small ? 34 : 42);
        above = ocy - lane;
        below = ocy + lane;
        statusY = ocy + statusOff;
        Slot in = menu.getSlot(IN);
        Slot mat = menu.getSlot(MAT);
        resultSlot = menu.getSlot(RES);
        place(in, inX - 8, ocy - 8);
        place(mat, matX - 8, ocy - 8);
        place(resultSlot, outX - 8, ocy - 8);
        placeInventory();
        track(IN, MAT);
        // 格框占 y-1 ~ y+16，线从框外一格起步，落到结果格的框外一格
        topPath = new int[]{inX, in.y - 2, inX, above, outX, above, outX, resultSlot.y - 2};
        bottomPath = new int[]{matX, mat.y + 17, matX, below, outX, below, outX, resultSlot.y + 17};
        int[] ids = registerFxPaths(topPath, bottomPath);
        pathFromInput = ids[0];
        pathFromMaterial = ids[1];
        riven.layout(inX, matX, outX, ocy, statusY, arc);
    }

    // ==================== 锻造演出（取走结果时） ====================

    @Override
    protected void containerTick() {
        super.containerTick();
        observe();
    }

    /**
     * 记下材料和结果的变化：结果从无到有的那一刻，把当时的材料（已经用光的话就是之前最后看到的那个）记成 madeWith
     */
    private void observe() {
        ItemStack material = menu.getSlot(MAT).getItem();
        boolean hasResult = menu.getSlot(RES).hasItem();
        if (hasResult && !hadResult) {
            madeWith = material.isEmpty() ? seenMaterial : material.getItem();
        }
        hadResult = hasResult;
        if (!material.isEmpty()) {
            seenMaterial = material.getItem();
        }
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int button, ClickType type) {
        // 挑裂罅候选时物品格、材料格锁着：点了不发包，格子红一下
        if (riven.blocks(slot)) {
            return;
        }
        observe();
        Op op = Op.NONE;
        int level = 0;
        boolean locked = false;
        Slot out = menu.getSlot(RES);
        // 在 super 之前判：super 会在本地先把结果拿走
        if (slot == out && ForgeOpClassifier.takesOutput(out, menu.getCarried(), button, type)) {
            op = ForgeOpClassifier.classify(menu, madeWith);
            ItemStack result = out.getItem();
            if (op == Op.RANK_UP || op == Op.MAX_RANK) {
                level = ForgeOpClassifier.moduleLevel(result);
            } else if (op == Op.FORMA) {
                locked = ForgeOpClassifier.formaLocked(result);
            }
        }
        super.slotClicked(slot, slotId, button, type);
        observe();
        playOp(op, level, locked);
    }

    private void playOp(Op op, int level, boolean locked) {
        Slot in = menu.getSlot(IN);
        Slot mat = menu.getSlot(MAT);
        Slot out = menu.getSlot(RES);
        switch (op) {
            case FUSION -> fx.fusion(pathFromInput, pathFromMaterial, out);
            case RIVEN_CYCLE -> fx.rivenCycle(out);
            case FORMA -> fx.forma(out, locked);
            case SHADOW_FORMA -> fx.shadowForma(out);
            // 当场处理类：武器这时已经在结果格里，光点注入结果格
            case IMBUE, GUN_ENHANCE -> fx.imbue(mat, out);
            // 升级：内融核心注入左边的模组格
            case RANK_UP -> fx.rankUp(mat, in, level, false);
            case MAX_RANK -> fx.rankUp(mat, in, level, true);
            default -> {
            }
        }
    }

    /** 按「循环」洗出了一张预览：等光流进炉膛、结果揭示时紫框跳一下、放声音 */
    void rivenRolled() {
        fx.rivenCycle(menu.getSlot(RES), FLOW_MS);
    }

    @Override
    protected boolean scrollPage(double mx, double my, double delta) {
        return riven.scroll(mx, my, delta, KuvaFxDirector.clockMs());
    }

    @Override
    protected float inventoryAlpha(long now) {
        return riven.inventoryAlpha(now);
    }

    /** 赤毒是裂罅循环扣的：扣完从背包补满，材料格的数量变化不等于扣了多少，「−N」改由状态包里的实扣数画 */
    @Override
    @Nullable
    protected Fx lastSpend(int id) {
        Fx f = super.lastSpend(id);
        return f != null && id == MAT && f.stack().getItem() instanceof Kuva ? null : f;
    }

    // ==================== 结果出现 ====================

    @Override
    protected void afterScan(long now) {
        observe();
        super.afterScan(now);
        if (!resultSlot.hasItem()) {
            resultOp = Op.NONE;
        }
    }

    /** 结果出现：两条线一起流进炉膛；预览类是虚线炉膛。颜色按操作 */
    @Override
    protected Arrival arrival(ItemStack result) {
        resultOp = ForgeOpClassifier.classify(menu, madeWith);
        return new Arrival(opColor(resultOp), isPreview(resultOp), new int[][]{topPath, bottomPath});
    }

    private static boolean isPreview(Op op) {
        return op == Op.FUSION || op == Op.RIVEN_CYCLE || op == Op.RANK_UP || op == Op.MAX_RANK;
    }

    private static int opColor(Op op) {
        return switch (op) {
            case FUSION, MAX_RANK -> FxPalette.ACCENT;
            case RIVEN_CYCLE, IMBUE -> GuiFx.rarityColor(4);
            case FORMA, RANK_UP -> FxPalette.TECH;
            case SHADOW_FORMA -> ORDIS;
            default -> FxPalette.KUVA;
        };
    }

    @Override
    protected Component rejectMessage(Slot slot, ItemStack carried) {
        if (slot.index == IN) {
            return Component.translatable(carried.getCount() > 1
                    ? "kuvalich.evolve.reject.one" : "kuvalich.evolve.reject.item");
        }
        return Component.translatable("kuvalich.evolve.reject.material");
    }

    // ==================== 画 ====================

    @Override
    protected void drawPage(GuiGraphics g, int mx, int my, long now, long t, float eF) {
        riven.frame(now);
        boolean rv = riven.active(now);
        float eT = phase(t, T_TEXT_IN, T_TEXT_DONE);
        ItemStack in = menu.getSlot(IN).getItem();
        ItemStack mat = menu.getSlot(MAT).getItem();
        if (!ItemStack.matches(in, prevIn) || !ItemStack.matches(mat, prevMat)) {
            prevIn = in.copy();
            prevMat = mat.copy();
            comboAt = now;
        }
        boolean shown = resultShown();
        boolean stuck = !rv && !in.isEmpty() && !mat.isEmpty() && !resultSlot.hasItem() && now - comboAt > STUCK_GRACE_MS;

        // ---- 左栏：配方表、持有材料 ----
        int y = drawLeftHead(g, Component.translatable("kuvalich.evolve.recipes"), eF, eT);
        int hint = hintRows(mx, my) | (mat.isEmpty() ? leftRows(in) : 0) | (in.isEmpty() ? matRows(mat) : 0);
        int lit = litRows(in, mat);
        y = drawRecipes(g, recipes, y, lit, hint, eT, now);
        drawStock(g, stocks, y + 4, lit | hint, eT, now);

        // ---- 中栏：背光、两条线、三格、状态行 ----
        drawBacklight(g, cx - 110, pTop - 30, 220, 150, 0.85F * eT);
        // 两条线要穿过炉膛的墨底接到结果格上，所以炉膛先画
        drawChamber(g, mx, my, now, phase(t, 600, 950));
        drawCircuit(g, phase(t, 600, 950), in, mat, now);
        drawFlows(g);
        drawCell(g, menu.getSlot(IN), mx, my, phase(t, 650, 1050), now);
        drawCell(g, menu.getSlot(MAT), mx, my, phase(t, 770, 1170), now);
        drawCaptions(g, eT);
        if (rv) {
            riven.drawMiddle(g, mx, my, now, eF);
        } else {
            drawStatus(g, in, mat, shown, stuck, eF);
        }
        drawDrift(g, cx, pTop + 6, 150, 92, now, eF);

        // ---- 右栏：结果、流程 ----
        drawRightHead(g, Component.translatable("kuvalich.evolve.output"), eF);
        if (rv) {
            int stepTop = drawSteps(g, riven.steps(), eF, now);
            riven.drawRight(g, now, eF, stepTop - 6);
        } else {
            int stepTop = drawSteps(g, steps(in, mat, shown, stuck), eF, now);
            drawLines(g, detail(in, mat, shown, stuck), rx0, hairY + 8, rx1 - rx0, eF, stepTop - 6);
        }
    }

    /** 流程三步：放物品、放材料、取结果；两边都放了却凑不成时第三步是红叉 */
    private List<Step> steps(ItemStack in, ItemStack mat, boolean shown, boolean stuck) {
        boolean has = resultSlot.hasItem();
        boolean item = !in.isEmpty() || has;
        boolean material = !mat.isEmpty() || has;
        List<Step> out = new ArrayList<>(3);
        out.add(new Step(tr("kuvalich.evolve.step.item"), item ? STEP_DONE : STEP_ACTIVE));
        out.add(new Step(tr("kuvalich.evolve.step.material"), material ? STEP_DONE : item ? STEP_ACTIVE : STEP_PENDING));
        if (stuck) {
            out.add(new Step(tr("kuvalich.evolve.no_recipe"), STEP_FAILED));
        } else {
            out.add(new Step(tr("kuvalich.evolve.step.take"), shown ? STEP_ACTIVE : STEP_PENDING));
        }
        return out;
    }

    @Override
    protected void drawOverlay(GuiGraphics g, int mx, int my, long now, long t) {
        drawCellOverlay(g, menu.getSlot(IN), t, 800, 1100);
        drawCellOverlay(g, menu.getSlot(MAT), t, 920, 1220);
        drawChamberOverlay(g, t, 750, 1050);
        drawStockOverlay(g, now, t);
        riven.drawOverlay(g, now);
    }

    /** 两条线常亮一层淡底；格子里有东西的那条亮起来；左格和中格之间一个「+」，线进炉膛前各一个箭头 */
    private void drawCircuit(GuiGraphics g, float e, ItemStack in, ItemStack mat, long now) {
        if (e <= 0.0F) {
            return;
        }
        boolean inOn = !in.isEmpty() && !pending(Ev.PLACE, IN);
        boolean matOn = !mat.isEmpty() && !pending(Ev.PLACE, MAT);
        GuiShapes.begin(g);
        path(g, topPath, 1.0F, FxPalette.KUVA_PALE, 0.12F * e, null);
        path(g, bottomPath, 1.0F, FxPalette.KUVA_PALE, 0.12F * e, null);
        if (inOn) {
            path(g, topPath, 1.0F, FxPalette.KUVA, 0.5F * e, null);
        }
        if (matOn) {
            path(g, bottomPath, 1.0F, FxPalette.KUVA, 0.5F * e, null);
        }
        // 「+」
        float px = Math.round((inX + 8 + matX - 8) * 0.5F);
        hl(g, px - 3, px + 3, ocy, FxPalette.KUVA_PALE, 0.35F * e);
        vl(g, px, ocy - 3, ocy + 3, FxPalette.KUVA_PALE, 0.35F * e);
        GuiShapes.end();
        // 上下两段横线在中格和炉膛之间各放一个往右的小箭头：流向结果
        float ax = Math.round((matX + 8 + outX - chHalf) * 0.5F);
        chevronRight(g, ax, above, inOn ? FxPalette.KUVA : FxPalette.ASH, (inOn ? 0.85F : 0.35F) * e);
        chevronRight(g, ax, below, matOn ? FxPalette.KUVA : FxPalette.ASH, (matOn ? 0.85F : 0.35F) * e);
        // 有东西的那条线上光点一直往结果格流
        pulsePaths[0] = inOn ? topPath : null;
        pulsePaths[1] = matOn ? bottomPath : null;
        drawPulses(g, pulsePaths, PULSE_RGBS, 1700, now, e);
    }

    /**
     * 左格下面写「物品」、中格上面写「材料」：各自的线往另一边走，字放在没线的那一侧。
     * 两个字离各自的格框一样空 2 格（以前上 2 下 3）
     */
    private void drawCaptions(GuiGraphics g, float eT) {
        Slot in = menu.getSlot(IN);
        Slot mat = menu.getSlot(MAT);
        textC(g, tr("kuvalich.evolve.slot.item"), inX, in.y + 19, FxPalette.ASH, 0.6F * eT);
        textC(g, tr("kuvalich.evolve.slot.material"), matX, mat.y - 11, FxPalette.ASH, 0.6F * eT);
    }

    /**
     * 材料格上面写着「材料」，再上面还有一条线，扣材料的「−N」改到格子左下方（往下那条线的左边，和「物品」同一行），
     * 右对齐、只淡出不飘。格子右边、右下都挨着炉膛的转弧和往右的箭头，「−120」在默认字体下 24 宽，放那边会压上去
     */
    @Override
    protected void drawSpend(GuiGraphics g, Slot s, String text, float u, float a) {
        if (s.index != MAT) {
            super.drawSpend(g, s, text, u, a);
            return;
        }
        textR(g, text, s.x + 5, s.y + 19, FxPalette.KUVA, a);
    }

    /** 状态行：提示 → 结果 → 凑不成的原因 → 还差哪一边 */
    private void drawStatus(GuiGraphics g, ItemStack in, ItemStack mat, boolean shown, boolean stuck, float eF) {
        if (drawMessage(g, cx, statusY, eF)) {
            return;
        }
        if (resultSlot.hasItem()) {
            if (!shown) {
                return;
            }
            String op = opName(resultOp);
            boolean preview = isPreview(resultOp);
            textC(g, tr(preview ? "kuvalich.evolve.status.preview" : "kuvalich.evolve.status.done", op), cx, statusY,
                    FxPalette.BONE, 0.85F * eF);
            long r = resultAge();
            if (r < 600) {
                additive(g, () -> hl(g, cx - 70, cx + 70, statusY + 11, FxPalette.KUVA_PALE, 0.8F * (1.0F - r / 600.0F)));
            }
            return;
        }
        if (in.isEmpty() && mat.isEmpty()) {
            textC(g, tr("kuvalich.evolve.status.empty"), cx, statusY, FxPalette.ASH, 0.7F * eF);
        } else if (mat.isEmpty()) {
            textC(g, tr("kuvalich.evolve.status.need_mat"), cx, statusY, FxPalette.ASH, 0.7F * eF);
        } else if (in.isEmpty()) {
            textC(g, tr("kuvalich.evolve.status.need_item"), cx, statusY, FxPalette.ASH, 0.7F * eF);
        } else if (stuck) {
            textC(g, why(in, mat), cx, statusY, FxPalette.KUVA, 0.9F * eF);
        }
    }

    // ==================== 右栏 ====================

    private List<Line> detail(ItemStack in, ItemStack mat, boolean shown, boolean stuck) {
        List<Line> out = new ArrayList<>();
        if (resultSlot.hasItem()) {
            if (shown) {
                resultDetail(out, in);
            }
            return out;
        }
        if (in.isEmpty() && mat.isEmpty()) {
            out.add(new Line(tr("kuvalich.evolve.detail_empty"), FxPalette.ASH, 0.6F, 0));
            return out;
        }
        if (mat.isEmpty()) {
            // 只放了左格：列出中格能放什么
            out.add(new Line(tr("kuvalich.evolve.status.need_mat"), FxPalette.BONE, 1.0F, 4));
            int m = leftRows(in);
            if (m == 0) {
                out.add(new Line(tr("kuvalich.evolve.no_recipe.item"), KuvaPalette.MUTED, 0.85F, 0));
            }
            for (Row r : rows) {
                if ((m & bit(r)) != 0) {
                    out.add(new Line(opName(r) + " · " + materialName(r), KuvaPalette.MUTED, 0.85F, 2));
                }
            }
            return out;
        }
        if (in.isEmpty()) {
            // 只放了中格：列出左格能放什么
            out.add(new Line(tr("kuvalich.evolve.status.need_item"), FxPalette.BONE, 1.0F, 4));
            int m = matRows(mat);
            for (Row r : rows) {
                if ((m & bit(r)) != 0) {
                    out.add(new Line(opName(r), FxPalette.TEXT_MID, 0.9F, 0));
                    out.add(new Line(tr("kuvalich.evolve.recipe." + r.key + ".left"), KuvaPalette.MUTED, 0.8F, 4));
                }
            }
            return out;
        }
        if (stuck) {
            out.add(new Line(tr("kuvalich.evolve.no_recipe"), FxPalette.KUVA, 1.0F, 3));
            out.add(new Line(why(in, mat), KuvaPalette.MUTED, 0.85F, 0));
        }
        return out;
    }

    /** 有结果时：操作名、结果名、这一种操作要知道的、材料什么时候扣 */
    private void resultDetail(List<Line> out, ItemStack in) {
        ItemStack res = resultSlot.getItem();
        Op op = resultOp;
        boolean preview = isPreview(op);
        String head = op == Op.NONE ? tr("kuvalich.evolve.output") : opName(op);
        if (preview) {
            head += " · " + tr("kuvalich.evolve.preview");
        }
        out.add(new Line(head, resultRgb, 1.0F, 2));
        out.add(new Line(typed(res.getHoverName().getString(), resultAge()), FxPalette.BONE, 1.0F, 4));
        switch (op) {
            case FUSION -> {
                if (!in.isEmpty()) {
                    out.add(new Line(tr("kuvalich.evolve.level", KuvaWeaponUtil.getNumber(in), KuvaWeaponUtil.getNumber(res)),
                            FxPalette.TEXT_MID, 0.95F, 3));
                }
                out.add(new Line(tr("kuvalich.evolve.fusion.keep"), KuvaPalette.MUTED, 0.85F, 3));
                out.add(new Line(tr("kuvalich.evolve.fusion.warn"), FxPalette.KUVA, 0.9F, 4));
            }
            case RIVEN_CYCLE -> {
                out.add(new Line(tr("kuvalich.evolve.riven.paid"), KuvaPalette.MUTED, 0.85F, 3));
                if (!in.isEmpty()) {
                    out.add(new Line(tr("kuvalich.evolve.riven.cycles", rivenCycle(in)), FxPalette.TEXT_MID, 0.9F, 3));
                }
                out.add(new Line(tr("kuvalich.evolve.riven.warn"), FxPalette.KUVA, 0.9F, 4));
            }
            case RANK_UP, MAX_RANK -> {
                if (!in.isEmpty()) {
                    int a = ModuleLevelHelper.getModuleLevel(in);
                    out.add(new Line(tr("kuvalich.evolve.level", a, ModuleLevelHelper.getModuleLevel(res)),
                            FxPalette.TEXT_MID, 0.95F, 3));
                    int cost = ModuleLevelHelper.getUpgradeCost(a, in);
                    if (cost > 0) {
                        out.add(new Line(tr("kuvalich.evolve.rank.cost", cost), KuvaPalette.MUTED, 0.85F, 3));
                    }
                }
                if (op == Op.MAX_RANK) {
                    out.add(new Line(tr("kuvalich.evolve.rank.max"), FxPalette.ACCENT, 0.9F, 4));
                }
            }
            case FORMA -> {
                out.add(new Line(tr("kuvalich.evolve.forma.done"), KuvaPalette.MUTED, 0.85F, 3));
                if (WeaponModuleHandler.isFormaLocked(res)) {
                    out.add(new Line(tr("kuvalich.evolve.forma.locked"), FxPalette.KUVA, 0.9F, 4));
                }
            }
            case SHADOW_FORMA -> out.add(new Line(tr("kuvalich.evolve.shadow.done"), KuvaPalette.MUTED, 0.85F, 4));
            case IMBUE -> out.add(new Line(tr("kuvalich.evolve.imbue.done"), KuvaPalette.MUTED, 0.85F, 4));
            case GUN_ENHANCE -> out.add(new Line(tr("kuvalich.evolve.gun.done",
                    TaczGunEnhanceUtil.getEnhanceCount(res)), KuvaPalette.MUTED, 0.85F, 4));
            default -> {
            }
        }
        if (op != Op.NONE) {
            out.add(new Line(tr(preview ? "kuvalich.evolve.when.take" : "kuvalich.evolve.when.now"),
                    FxPalette.ASH, 0.6F, 0));
        }
    }

    // ==================== 配方表 ====================

    private int bit(Row r) {
        int i = rows.indexOf(r);
        return i < 0 ? 0 : 1 << i;
    }

    @Nullable
    private static Row rowOf(Op op) {
        return switch (op) {
            case FUSION -> Row.FUSION;
            case IMBUE -> Row.IMBUE;
            case FORMA, SHADOW_FORMA -> Row.FORMA;
            case RIVEN_CYCLE -> Row.RIVEN;
            case RANK_UP, MAX_RANK -> Row.RANK;
            case GUN_ENHANCE -> Row.GUN;
            default -> null;
        };
    }

    /**
     * 点亮哪一行：有结果看结果是哪种；两边都放了、还没结果时看中格的材料想走哪一种
     * （材料决定了是哪种操作，凑不成时这一行照样亮着，状态行写为什么）；只放了一边的不点亮，只半亮
     */
    private int litRows(ItemStack in, ItemStack mat) {
        if (resultSlot.hasItem()) {
            Row r = rowOf(resultOp);
            return r == null ? 0 : bit(r);
        }
        return !in.isEmpty() && !mat.isEmpty() ? matRows(mat) : 0;
    }

    /** 半亮哪几行：手上拿着的、或者鼠标停着的那件东西能参与的（放左格或放中格都算） */
    private int hintRows(int mx, int my) {
        ItemStack s = menu.getCarried();
        if (s.isEmpty()) {
            Slot over = slotAt(mx, my);
            s = over == null || over == resultSlot ? ItemStack.EMPTY : over.getItem();
        }
        return s.isEmpty() ? 0 : leftRows(s) | matRows(s);
    }

    /** 放在左格时能走哪几种 */
    private int leftRows(ItemStack s) {
        if (s.isEmpty()) {
            return 0;
        }
        Item it = s.getItem();
        int m = 0;
        if (KuvaWeaponUtil.hasType(s)) {
            m |= bit(Row.FUSION);
        }
        if (it instanceof AbstractModule) {
            if (AbstractModule.isRandom(s)) {
                return m;
            }
            if (it instanceof WeaponRivenModule || it instanceof WarframeRivenModule) {
                m |= bit(Row.RIVEN);
            }
            if (!ModuleLevelHelper.isMaxLevel(s)) {
                m |= bit(Row.RANK);
            }
            return m;
        }
        if (isMaterial(it) || s.getCount() != 1) {
            return m;
        }
        boolean base = WeaponModuleHandler.hasBase(s);
        if (!base) {
            m |= bit(Row.IMBUE);
        } else if (!KuvaWeaponUtil.hasType(s)) {
            m |= bit(Row.FORMA);
        }
        if (gunEnabled() && TaczGunEnhanceUtil.isTaczGun(s)) {
            m |= bit(Row.GUN);
        }
        return m;
    }

    /** 放在中格时能走哪几种 */
    private int matRows(ItemStack s) {
        if (s.isEmpty()) {
            return 0;
        }
        Item it = s.getItem();
        if (KuvaWeaponUtil.hasType(s)) {
            return bit(Row.FUSION);
        }
        if (it instanceof Kuva) {
            return bit(Row.RIVEN);
        }
        if (it instanceof Forma) {
            return bit(Row.FORMA);
        }
        if (it instanceof RivenSliver) {
            return bit(Row.IMBUE);
        }
        if (it instanceof LichReliquary) {
            return bit(Row.GUN);
        }
        if (it instanceof Endo) {
            return bit(Row.RANK);
        }
        return 0;
    }

    private static boolean isMaterial(Item it) {
        return it instanceof Kuva || it instanceof Endo || it instanceof RivenSliver || it instanceof Forma
                || it instanceof LichReliquary;
    }

    private List<Component> rowTip(Row row) {
        List<Component> out = new ArrayList<>();
        out.add(tip("kuvalich.evolve.op." + row.key, FxPalette.BONE));
        out.add(tip("kuvalich.evolve.tip.left", KuvaPalette.MUTED,
                Component.translatable("kuvalich.evolve.recipe." + row.key + ".left")));
        out.add(tip("kuvalich.evolve.tip.mid", KuvaPalette.MUTED, materialName(row)));
        out.add(tip("kuvalich.evolve.recipe." + row.key + ".when", FxPalette.KUVA));
        if (row == Row.FORMA) {
            out.add(tip("kuvalich.evolve.recipe.forma.shadow", KuvaPalette.MUTED));
        }
        return out;
    }

    private static MutableComponent tip(String key, int rgb, Object... args) {
        return Component.translatable(key, args).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    private static String opName(Row row) {
        return tr("kuvalich.evolve.op." + row.key);
    }

    private static String opName(Op op) {
        return switch (op) {
            case SHADOW_FORMA -> tr("kuvalich.evolve.op.shadow_forma");
            case MAX_RANK -> tr("kuvalich.evolve.op.max_rank");
            default -> {
                Row r = rowOf(op);
                yield r == null ? "" : opName(r);
            }
        };
    }

    /** 中格放什么（拿物品名，免得和物品本身的叫法对不上） */
    private static String materialName(Row row) {
        return switch (row) {
            case FUSION -> tr("kuvalich.evolve.mat.kuva_weapon");
            case IMBUE -> KuvaLichItems.RIVEN_SLIVER.get().getDescription().getString();
            case FORMA -> KuvaLichItems.FORMA.get().getDescription().getString() + " / "
                    + KuvaLichItems.SHADOW_FORMA.get().getDescription().getString();
            case RIVEN -> KuvaLichItems.KUVA.get().getDescription().getString();
            case RANK -> KuvaLichItems.ENDO.get().getDescription().getString();
            case GUN -> KuvaLichItems.LICH_RELIQUARY.get().getDescription().getString();
        };
    }

    // ==================== 凑不成的原因（照 RequiemEvolveMenu#processCrafting 的条件） ====================

    /** 两边都放了、却没有结果：为什么 */
    private String why(ItemStack in, ItemStack mat) {
        Item i = in.getItem();
        Item m = mat.getItem();
        boolean riven = i instanceof WeaponRivenModule || i instanceof WarframeRivenModule;
        if (riven && m instanceof Kuva) {
            if (AbstractModule.isRandom(in)) {
                return tr("kuvalich.evolve.why.riven_unrevealed");
            }
            int cost = i instanceof WarframeRivenModule
                    ? ModuleLevelTooltipHelper.getWarframeRivenCycleCost(WarframeRivenModule.getTrend(in), WarframeRivenModule.getCycle(in))
                    : ModuleLevelTooltipHelper.getWeaponRivenCycleCost(WeaponRivenModule.getTrend(in), WeaponRivenModule.getCycle(in));
            if (mat.getCount() < cost) {
                return tr("kuvalich.evolve.why.kuva_short", cost);
            }
        }
        if (KuvaWeaponUtil.hasType(mat) && !KuvaWeaponUtil.hasType(in)) {
            return tr("kuvalich.evolve.why.fusion_both");
        }
        if (m instanceof Kuva && !riven) {
            return tr("kuvalich.evolve.why.kuva_riven");
        }
        if (m instanceof Forma) {
            if (KuvaWeaponUtil.hasType(in)) {
                return tr("kuvalich.evolve.why.forma_kuva");
            }
            if (!WeaponModuleHandler.hasBase(in)) {
                return tr("kuvalich.evolve.why.forma_unimbued");
            }
            if (!(m instanceof ShadowForma) && WeaponModuleHandler.isFormaLocked(in)) {
                return tr("kuvalich.evolve.why.forma_locked");
            }
        }
        if (m instanceof RivenSliver && WeaponModuleHandler.hasBase(in)) {
            return tr("kuvalich.evolve.why.imbued");
        }
        if (m instanceof LichReliquary) {
            if (!gunEnabled()) {
                return tr("kuvalich.evolve.why.gun_off");
            }
            return tr(TaczGunEnhanceUtil.isTaczGun(in) ? "kuvalich.evolve.why.gun_max" : "kuvalich.evolve.why.gun_only");
        }
        if (m instanceof Endo) {
            if (!ModuleLevelHelper.isLevelSystemEnabled()) {
                return tr("kuvalich.evolve.why.level_off");
            }
            if (!(i instanceof AbstractModule)) {
                return tr("kuvalich.evolve.why.endo_module");
            }
            if (AbstractModule.isRandom(in)) {
                return tr("kuvalich.evolve.why.unrevealed");
            }
            int cost = ModuleLevelHelper.getUpgradeCost(ModuleLevelHelper.getModuleLevel(in), in);
            if (cost > 0 && mat.getCount() < cost) {
                return tr("kuvalich.evolve.why.endo_short", cost);
            }
        }
        return tr("kuvalich.evolve.why.generic");
    }

    private static int rivenCycle(ItemStack s) {
        return s.getItem() instanceof WarframeRivenModule ? WarframeRivenModule.getCycle(s) : WeaponRivenModule.getCycle(s);
    }

    /** 装了 TACZ、而且开了枪械强化 */
    private static boolean gunEnabled() {
        if (!TaczGunEnhanceUtil.isTaczLoaded()) {
            return false;
        }
        try {
            return ModConfig.KUVA_LICH.taczGunEnhanceEnable.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
