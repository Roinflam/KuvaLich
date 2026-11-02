package pers.roinflam.kuvalich.client.gui.screens.inventory;

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
import pers.roinflam.kuvalich.client.fx.FxCurve;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * 安魂之铸、安魂之融两台工作台共用的一页：在档案页的底子上加结果格的「炉膛」、普通格子、格子之间的连线和左栏的配方表
 *
 * <ul>
 *   <li>普通格子：墨底细框，模组顶上一道稀有度色；放进去框亮一下、白闪，拿走时框往中心收，拒收时红一下；
 *       同一摞东西数量变少（材料被扣）时不当成放进去，飘一个「−N」；</li>
 *   <li>结果格套在大一圈的炉膛里，是这一页的主角：结果出来时四角从外面咬上来、炉膛亮一下、往外扩一圈框、
 *       物品从下往上扫出来、往上溅一把粒子；预览（拿走才算数）的炉膛是虚线框；结果被拿走时炉膛往中心收；</li>
 *   <li>格子之间的连线常亮一层很淡的底，放了东西的那条亮起来；结果出来时光先沿连线从输入格流进炉膛，
 *       流到了才揭示结果（流的这 300 ms 里结果格用墨色盖住）；</li>
 *   <li>左栏配方表：每行一个图标和名字，当前这一步对应的那行点亮，手上拿着、或者鼠标停在背包里的东西
 *       能参与的几行半亮；悬停一行看完整说明；</li>
 *   <li>取走结果时的演出和声音仍由 {@link GuiFx} 放，在 {@link #renderLabels} 里叠在物品上面画。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
abstract class ForgePageScreen<M extends AbstractContainerMenu> extends ArchivePageScreen<M> {

    /** 自己的事件：结果揭示、结果被拿走、流线 */
    protected static final int EV_RESULT = 300;
    protected static final int EV_RESULT_GONE = 301;
    protected static final int EV_FLOW = 302;

    /** 光沿连线流进炉膛要多久，流完才揭示结果 */
    protected static final int FLOW_MS = 300;
    private static final int FLOW_FADE_MS = 220;
    /** 结果从下往上扫出来要多久 */
    private static final int REVEAL_MS = 320;
    /** 内融核心的颜色（和锻造演出里内融核心的光点同色） */
    protected static final int ENDO = 0x9FE8FF;

    private static final int[][] NO_PATHS = new int[0][];

    /** 结果出来时怎么演：炉膛颜色、是不是预览、从哪几条连线流进来 */
    protected record Arrival(int rgb, boolean preview, int[][] flows) {
    }

    /** 左栏配方表的一行：图标、名字、名字下面的一行小字（可以没有）、悬停说明 */
    protected record Recipe(ItemStack icon, Component name, @Nullable Component sub,
                            Supplier<List<Component>> tip) {
    }

    /** 右栏的一段字：颜色、透明度、段后空几个单位 */
    protected record Line(String text, int rgb, float alpha, int gap) {
    }

    protected GuiFx fx = new GuiFx();
    /** 登记进 {@link #fx} 的路径；版式没变时不重建，免得正在播的演出被打断 */
    private int[][] fxPathsKey = NO_PATHS;
    @Nullable
    private int[] fxPathIds;
    private boolean opened;

    /** 结果格和炉膛半边长（子类在 layout 里定） */
    protected Slot resultSlot;
    protected int chHalf = 20;

    private ItemStack resultSeen = ItemStack.EMPTY;
    private boolean resultSeenInit;
    /** 当前结果的颜色、是不是预览；拿走时收拢用上一个结果的颜色 */
    protected int resultRgb = FxPalette.KUVA;
    protected boolean resultPreview;
    private int goneRgb = FxPalette.KUVA;

    private int[][] flowPaths = NO_PATHS;
    private int flowRgb = FxPalette.KUVA;
    private final float[] head = new float[2];

    /** 配方表每行当前的亮度（平滑过渡），以及上一帧的时刻 */
    private final float[] rowGlow = new float[8];
    private long rowClock = Long.MIN_VALUE;
    /** 这一帧配方表画了图标的位置和要压的暗度（x, y, 千分比），盖板在 drawOverlay 里画 */
    private final List<int[]> iconVeils = new ArrayList<>();

    protected ForgePageScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    // ==================== 子类填的 ====================

    /** 结果格里出现了新东西：这次怎么演 */
    protected abstract Arrival arrival(ItemStack result);

    // ==================== 生命周期 ====================

    @Override
    protected void init() {
        super.init();
        // 改窗口大小、从别的界面回来都会再走一次 init，开机音只在第一次打开时放
        if (!opened) {
            opened = true;
            GuiFx.playOpen();
        }
    }

    /**
     * 登记锻造演出要用的折线（相对界面左上角，现在就是屏幕坐标）。路径和上次一样时什么都不做；
     * 不一样（窗口大小变了）时换一份新的 {@link GuiFx}：它的路径只能登记、不能改
     *
     * @return 各条路径的路径号
     */
    protected int[] registerFxPaths(int[]... paths) {
        if (fxPathIds == null || !Arrays.deepEquals(paths, fxPathsKey)) {
            if (fxPathIds != null) {
                fx = new GuiFx();
            }
            fxPathsKey = new int[paths.length][];
            fxPathIds = new int[paths.length];
            for (int k = 0; k < paths.length; k++) {
                fxPathsKey[k] = paths[k].clone();
                fxPathIds[k] = fx.path(paths[k]);
            }
        }
        return fxPathIds;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        iconVeils.clear();
        super.renderBg(g, partialTick, mx, my);
    }

    /** 底子画完盖板、粒子之后，再叠锻造演出（和原来一样在格子物品之上、提示框之下） */
    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        super.renderLabels(g, mx, my);
        fx.render(g, this.leftPos, this.topPos, !this.menu.getCarried().isEmpty());
    }

    // ==================== 结果格 ====================

    @Override
    protected void afterScan(long now) {
        trackResult();
    }

    /** 每帧比一次结果格：空 → 有、换了一件 = 新结果；有 → 空 = 被拿走。同一件只是数量变了不算 */
    private void trackResult() {
        if (resultSlot == null) {
            return;
        }
        ItemStack cur = resultSlot.getItem();
        if (!resultSeenInit) {
            resultSeenInit = true;
            resultSeen = cur.copy();
            if (!cur.isEmpty()) {
                Arrival a = arrival(cur);
                resultRgb = a.rgb();
                resultPreview = a.preview();
            }
            return;
        }
        if (ItemStack.matches(cur, resultSeen)) {
            return;
        }
        if (cur.isEmpty()) {
            goneRgb = resultRgb;
            event(Ev.CUSTOM, EV_RESULT_GONE, resultSeen, 0L);
        } else if (resultSeen.isEmpty() || !ItemStack.isSameItemSameTags(cur, resultSeen)) {
            Arrival a = arrival(cur);
            resultRgb = a.rgb();
            resultPreview = a.preview();
            long delay = 0L;
            if (a.flows().length > 0) {
                flowPaths = a.flows();
                flowRgb = a.rgb();
                event(Ev.CUSTOM, EV_FLOW, ItemStack.EMPTY, 0L);
                delay = FLOW_MS;
            }
            event(Ev.CUSTOM, EV_RESULT, cur.copy(), delay);
        }
        resultSeen = cur.copy();
    }

    /** 结果出来了、而且已经揭示（流线还在路上时不算） */
    protected boolean resultShown() {
        return resultSlot != null && resultSlot.hasItem() && !pending(Ev.CUSTOM, EV_RESULT);
    }

    /** 结果揭示了多久；没有返回很大的数 */
    protected long resultAge() {
        return age(Ev.CUSTOM, EV_RESULT);
    }

    @Override
    protected void onEvent(Ev type, int id, ItemStack stack, long delayMs) {
        if (type == Ev.PLACE && id >= 0 && id < menu.slots.size()) {
            Slot s = menu.getSlot(id);
            burst(s.x + 8, s.y - 2, 6, -Mth.HALF_PI, 1.2F, 22.0F, 520.0F, 0.0F, FxPalette.KUVA, delayMs);
        } else if (type == Ev.CUSTOM && id == EV_RESULT && resultSlot != null) {
            // 结果出来那一下比放东西重：两侧斜着往上溅，带一点下坠
            float x = resultSlot.x + 8;
            float y = resultSlot.y + 8 - chHalf;
            burst(x, y, 9, -Mth.HALF_PI, 1.6F, 34.0F, 700.0F, 30.0F, resultRgb, delayMs);
            burst(x, y, 5, -Mth.HALF_PI, 0.6F, 48.0F, 520.0F, 0.0F, FxPalette.KUVA_PALE, delayMs);
        }
    }

    // ==================== 数量变少 ====================

    /** 这一格最近一次扣材料少了几个、过去多久；没有返回 null */
    @Nullable
    protected Fx lastSpend(int id) {
        return last(Ev.SPEND, id);
    }

    // ==================== 格子 ====================

    /** 一个普通格子：墨底、细框、模组的稀有度条，拒收的红闪、拿走时往中心收的框。物品由原版画在上面 */
    protected void drawCell(GuiGraphics g, Slot s, int mx, int my, float e, long now) {
        if (e <= 0.0F) {
            return;
        }
        int id = s.index;
        float x0 = s.x - 1;
        float y0 = s.y - 1;
        float x1 = s.x + 17;
        float y1 = s.y + 17;
        ItemStack st = pending(Ev.PLACE, id) ? ItemStack.EMPTY : s.getItem();
        boolean has = !st.isEmpty();
        boolean hv = isHovering(s.x, s.y, 16, 16, mx, my);
        long ins = age(Ev.PLACE, id);
        float boost = has ? 0.5F * (1.0F - seg(ins, 0, 600)) : 0.0F;
        int rar = has ? ForgeOpClassifier.rarity(st) : -1;
        GuiShapes.begin(g);
        fill(g, x0, y0, x1, y1, INK, 0.7F * e);
        if (hv) {
            fill(g, x0, y0, x1, y1, FxPalette.KUVA_PALE, 0.1F);
        }
        frame(g, x0, y0, x1, y1, FxPalette.KUVA_PALE, Math.min(1.0F, (hv ? 0.6F : has ? 0.4F : 0.18F) + boost) * e);
        if (rar >= 0) {
            fill(g, x0 + 1, y0, x1 - 1, y0 + 1, GuiFx.rarityColor(rar), 0.9F * e);
        }
        GuiShapes.end();
        long rj = age(Ev.REJECT, id);
        if (rj < 300) {
            additive(g, () -> fill(g, x0, y0, x1, y1, FxPalette.KUVA, 0.5F * (1.0F - rj / 300.0F)));
        }
        Fx tk = last(Ev.TAKE, id);
        long rm = tk == null ? Long.MAX_VALUE : now - tk.t0();
        if (!has && rm < 200) {
            // 拿走：一圈框往中心收掉
            float u = rm / 200.0F;
            float h = 9.0F * (1.0F - easeOut(u));
            float cx0 = s.x + 8;
            float cy0 = s.y + 8;
            additive(g, () -> frame(g, cx0 - h, cy0 - h, cx0 + h, cy0 + h, FxPalette.KUVA_PALE, 0.8F * (1.0F - u)));
        }
    }

    /**
     * 压在格子物品上面的：还没轮到的放进用墨色盖住、放进去那一下的白闪、开场时的盖板、数量变少时飘的「−N」
     *
     * @param introA 开场盖板开始退的时刻
     * @param introB 开场盖板退完的时刻
     */
    protected void drawCellOverlay(GuiGraphics g, Slot s, long t, int introA, int introB) {
        int id = s.index;
        float x0 = s.x - 1;
        float y0 = s.y - 1;
        if (pending(Ev.PLACE, id)) {
            GuiShapes.begin(g);
            fill(g, x0 + 1, y0 + 1, x0 + 17, y0 + 17, INK, 1.0F);
            GuiShapes.end();
            return;
        }
        long ins = age(Ev.PLACE, id);
        if (s.hasItem()) {
            if (ins < 250) {
                additive(g, () -> fill(g, x0, y0, x0 + 18, y0 + 18, FxPalette.KUVA_PALE, 0.5F * (1.0F - ins / 250.0F)));
            }
            float cov = 1.0F - phase(t, introA, introB);
            if (cov > 0.0F && dimmed(t)) {
                GuiShapes.begin(g);
                fill(g, x0 + 1, y0 + 1, x0 + 17, y0 + 17, INK, cov);
                GuiShapes.end();
            }
        }
        long sp = age(Ev.SPEND, id);
        Fx spend = lastSpend(id);
        if (spend != null && sp < 800) {
            float u = sp / 800.0F;
            float a = sp < 400 ? 1.0F : 1.0F - (sp - 400) / 400.0F;
            textC(g, "-" + spend.stack().getCount(), s.x + 8, s.y - 10 - 6.0F * easeOut(u), FxPalette.KUVA, a);
        }
    }

    // ==================== 炉膛 ====================

    /**
     * 结果格的炉膛：背后一圈全息环，外面两道反向转的弧；墨底、框（预览是虚线）、四个角，有产出时框里一层全息横纹；
     * 揭示时四角咬上来、亮一下、往外扩一圈。格子本身也在这里画
     */
    protected void drawChamber(GuiGraphics g, int mx, int my, long now, float e) {
        if (e <= 0.0F || resultSlot == null) {
            return;
        }
        Slot s = resultSlot;
        int ox = s.x + 8;
        int oy = s.y + 8;
        int h = chHalf;
        float x0 = ox - h;
        float y0 = oy - h;
        float x1 = ox + h;
        float y1 = oy + h;
        boolean shown = resultShown();
        long r = resultAge();
        int rgb = resultRgb;
        drawHoloRing(g, ox, oy, h * 2.1F, h * 0.8F, now, e);
        float rot = (now % 12000L) / 12000.0F * Mth.TWO_PI;
        float lineW = GuiShapes.pixels(g, lw());
        int arcRgb = shown ? rgb : FxPalette.KUVA_PALE;
        float ar = h + 6.0F;
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        arc(g, ox, oy, ar, ar, rot, 1.4F, lineW, GuiShapes.argb(arcRgb, (shown ? 0.4F : 0.18F) * e));
        arc(g, ox, oy, ar, ar, rot + Mth.PI, 0.5F, lineW, GuiShapes.argb(arcRgb, (shown ? 0.3F : 0.12F) * e));
        arc(g, ox, oy, ar + 3.0F, ar + 3.0F, -rot * 1.5F, 0.8F, lineW * 2.0F,
                GuiShapes.argb(FxPalette.KUVA, (shown ? 0.3F : 0.12F) * e));
        GuiShapes.end();
        GuiShapes.additive(g, false);
        GuiShapes.begin(g);
        fill(g, x0, y0, x1, y1, INK, (shown ? 0.85F : 0.5F) * e);
        if (shown) {
            for (float y = y0 + 2; y < y1 - 1; y += 3) {
                hl(g, x0 + 1, x1 - 1, y, rgb, 0.05F * e);
            }
        }
        if (shown) {
            float br = 0.85F + 0.15F * Mth.sin((now % 3000L) / 3000.0F * Mth.TWO_PI);
            if (resultPreview) {
                GuiShapes.dashedFrame(g, x0, y0, x1, y1, lw(), GuiShapes.argb(rgb, 0.5F * br * e));
            } else {
                frame(g, x0, y0, x1, y1, rgb, 0.45F * e);
            }
            // 四个角从外面 8 格咬上来，带一点回弹
            float in = r < 220 ? 8.0F * (1.0F - bite(r / 220.0F)) : 0.0F;
            corners(g, x0 - in, y0 - in, x1 + in, y1 + in, 6.0F, rgb, 0.9F * br * e);
        } else {
            float br = 0.75F + 0.25F * Mth.sin((now % 2400L) / 2400.0F * Mth.TWO_PI);
            GuiShapes.dashedFrame(g, x0, y0, x1, y1, lw(), GuiShapes.argb(FxPalette.KUVA_PALE, 0.22F * e * br));
            corners(g, x0, y0, x1, y1, 4.0F, FxPalette.KUVA_PALE, 0.35F * e);
        }
        GuiShapes.end();
        if (shown && r < 600) {
            float u = r / 600.0F;
            float grow = 12.0F * easeOut(u);
            additive(g, () -> {
                fill(g, x0, y0, x1, y1, rgb, 0.3F * (1.0F - u));
                frame(g, x0 - grow, y0 - grow, x1 + grow, y1 + grow, rgb, 0.7F * (1.0F - u));
            });
        }
        long gone = age(Ev.CUSTOM, EV_RESULT_GONE);
        if (!s.hasItem() && gone < 240) {
            // 拿走：炉膛的框往中心收
            float u = gone / 240.0F;
            float hh = h * (1.0F - easeOut(u));
            int c = goneRgb;
            additive(g, () -> frame(g, ox - hh, oy - hh, ox + hh, oy + hh, c, 0.8F * (1.0F - u)));
        }
        drawCell(g, s, mx, my, e, now);
    }

    /** 压在结果物品上面：流线还没到时整格盖住、揭示时从下往上扫出来、刚揭示的闪光、开场盖板 */
    protected void drawChamberOverlay(GuiGraphics g, long t, int introA, int introB) {
        if (resultSlot == null || !resultSlot.hasItem()) {
            return;
        }
        Slot s = resultSlot;
        float x0 = s.x;
        float y0 = s.y;
        if (pending(Ev.CUSTOM, EV_RESULT)) {
            GuiShapes.begin(g);
            fill(g, x0, y0, x0 + 16, y0 + 16, INK, 1.0F);
            GuiShapes.end();
            return;
        }
        long r = resultAge();
        int rgb = resultRgb;
        if (r > REVEAL_MS + 900) {
            // 揭示完之后隔几秒一道扫描光从上往下扫过结果
            long ph = KuvaFxDirector.clockMs() % 3000L;
            if (ph < 900L) {
                float u = ph / 900.0F;
                float sy = y0 - 2 + 20 * u;
                float k = Mth.sin(u * Mth.PI);
                additive(g, () -> {
                    GuiShapes.hline(g, x0 - 3, x0 + 19, sy, lw() * 3, GuiShapes.argb(rgb, 0.18F * k));
                    GuiShapes.hline(g, x0 - 2, x0 + 18, sy, lw(), GuiShapes.argb(FxPalette.KUVA_PALE, 0.4F * k));
                });
            }
        }
        if (r < REVEAL_MS) {
            float cy = Math.round(y0 + 16 - 16 * easeOut(r / (float) REVEAL_MS));
            GuiShapes.begin(g);
            fill(g, x0, y0, x0 + 16, cy, INK, 1.0F);
            hl(g, x0 - 1, x0 + 17, cy, FxPalette.KUVA_PALE, 0.9F);
            GuiShapes.end();
            additive(g, () -> GuiShapes.hline(g, x0 - 3, x0 + 19, cy, lw() * 3, GuiShapes.argb(rgb, 0.5F)));
        }
        if (r < 260) {
            additive(g, () -> fill(g, x0 - 1, y0 - 1, x0 + 17, y0 + 17, FxPalette.WHITE, 0.45F * (1.0F - r / 260.0F)));
        }
        float cov = 1.0F - phase(t, introA, introB);
        if (cov > 0.0F && dimmed(t)) {
            GuiShapes.begin(g);
            fill(g, x0, y0, x0 + 16, y0 + 16, INK, cov);
            GuiShapes.end();
        }
    }

    /** 四个 L 角 */
    protected static void corners(GuiGraphics g, float x0, float y0, float x1, float y1, float len, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        GuiShapes.begin(g);
        hl(g, x0, x0 + len, y0, rgb, a);
        vl(g, x0, y0, y0 + len, rgb, a);
        hl(g, x1 - len, x1, y0, rgb, a);
        vl(g, x1, y0, y0 + len, rgb, a);
        hl(g, x0, x0 + len, y1, rgb, a);
        vl(g, x0, y1 - len, y1, rgb, a);
        hl(g, x1 - len, x1, y1, rgb, a);
        vl(g, x1, y1 - len, y1, rgb, a);
        GuiShapes.end();
    }

    /** 咬合用的回弹（过冲约 13%，收 8 格时看得出「咬一下」） */
    private static float bite(float t) {
        float u = clamp01(t) - 1.0F;
        return 1.0F + 3.0F * u * u * u + 2.0F * u * u;
    }

    // ==================== 连线 ====================

    /** 横竖折线（x0, y0, x1, y1, …）画到总长的 {@code u}，线头坐标写进 {@code out} */
    protected static void path(GuiGraphics g, int[] xy, float u, int rgb, float a, @Nullable float[] out) {
        int total = 0;
        for (int p = 0; p + 3 < xy.length; p += 2) {
            total += Math.abs(xy[p + 2] - xy[p]) + Math.abs(xy[p + 3] - xy[p + 1]);
        }
        float left = total * clamp01(u);
        float hx = xy[0];
        float hy = xy[1];
        for (int p = 0; p + 3 < xy.length && left > 0.0F; p += 2) {
            int x0 = xy[p];
            int y0 = xy[p + 1];
            int x1 = xy[p + 2];
            int y1 = xy[p + 3];
            int seg = Math.abs(x1 - x0) + Math.abs(y1 - y0);
            if (seg == 0) {
                continue;
            }
            float take = Math.min(seg, left);
            float ex = x0 + Math.signum(x1 - x0) * take;
            float ey = y0 + Math.signum(y1 - y0) * take;
            if (y0 == y1) {
                hl(g, x0, ex, y0, rgb, a);
            } else {
                vl(g, x0, y0, ey, rgb, a);
            }
            hx = ex;
            hy = ey;
            left -= take;
        }
        if (out != null) {
            out[0] = hx;
            out[1] = hy;
        }
    }

    /** 结果出来前的流线：线头沿连线走 {@link #FLOW_MS}，身后留一条亮线，走到了整条退掉 */
    protected void drawFlows(GuiGraphics g) {
        long f = age(Ev.CUSTOM, EV_FLOW);
        if (f >= FLOW_MS + FLOW_FADE_MS || flowPaths.length == 0) {
            return;
        }
        boolean moving = f < FLOW_MS;
        float u = moving ? FxCurve.easeOutCubic(f / (float) FLOW_MS) : 1.0F;
        float a = moving ? 0.85F : 0.85F * (1.0F - (f - FLOW_MS) / (float) FLOW_FADE_MS);
        int rgb = flowRgb;
        additive(g, () -> {
            GuiShapes.begin(g);
            for (int[] p : flowPaths) {
                path(g, p, u, rgb, a, head);
                if (moving) {
                    float hx = Math.round(head[0]);
                    float hy = Math.round(head[1]);
                    fill(g, hx - 1, hy - 1, hx + 2, hy + 2, FxCurve.lerpColor(rgb, FxPalette.WHITE, 0.6F), a);
                }
            }
            GuiShapes.end();
        });
    }

    /** 向上的小箭头（两笔），画在竖线上表示流向 */
    protected static void chevronUp(GuiGraphics g, float x, float y, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        float w = GuiShapes.pixels(g, lw());
        int c = GuiShapes.argb(rgb, a);
        GuiShapes.hairline(g, x - 2.5F, y + 1.5F, x, y - 1.0F, w, c);
        GuiShapes.hairline(g, x, y - 1.0F, x + 2.5F, y + 1.5F, w, c);
    }

    /** 向右的小箭头 */
    protected static void chevronRight(GuiGraphics g, float x, float y, int rgb, float a) {
        if (a <= 0.002F) {
            return;
        }
        float w = GuiShapes.pixels(g, lw());
        int c = GuiShapes.argb(rgb, a);
        GuiShapes.hairline(g, x - 1.5F, y - 2.5F, x + 1.0F, y, w, c);
        GuiShapes.hairline(g, x + 1.0F, y, x - 1.5F, y + 2.5F, w, c);
    }

    // ==================== 左栏：标题、配方表 ====================

    /** 左栏标题和下面一个小节名，返回小节内容从哪个 y 开始 */
    protected int drawLeftHead(GuiGraphics g, Component section, float eF, float eT) {
        heading(g, this.title.getString(), lx0, eF, 2.0F);
        hl(g, lx0, lx1, hairY, FxPalette.KUVA_PALE, 0.35F * eF);
        int y0 = hairY + 8;
        text(g, section.getString(), lx0, y0, FxPalette.ASH, eT);
        hlFade(g, lx0, lx0 + 64, y0 + 11, FxPalette.KUVA_PALE, 0.35F * eT);
        return y0 + 17;
    }

    /**
     * 配方表：一行一个图标 + 名字（+ 一行小字）。{@code lit} 的那几行点亮（左边一道红杠、字变亮、图标不压暗），
     * {@code hint} 的那几行半亮；亮度按 90 ms 的时间常数平滑过渡
     *
     * @return 表格下面的 y
     */
    protected int drawRecipes(GuiGraphics g, List<Recipe> rows, int y, int lit, int hint, float eT, long now) {
        float dt = rowClock == Long.MIN_VALUE ? 1000.0F : Math.min(200.0F, now - rowClock);
        rowClock = now;
        float k = 1.0F - (float) Math.exp(-dt / 90.0F);
        float textX = lx0 + 21;
        float textW = lx1 - textX;
        for (int i = 0; i < rows.size() && i < rowGlow.length; i++) {
            Recipe r = rows.get(i);
            float target = (lit >> i & 1) != 0 ? 1.0F : (hint >> i & 1) != 0 ? 0.55F : 0.0F;
            rowGlow[i] += (target - rowGlow[i]) * k;
            float gl = rowGlow[i];
            List<String> sub = r.sub() == null ? List.of() : wrapEven(r.sub().getString(), textW);
            int lines = Math.min(2, sub.size());
            int rowH = (small ? 19 : 21) + (lines > 0 ? 10 * lines - (small ? 2 : 0) : 0);
            if (gl > 0.01F) {
                GuiShapes.begin(g);
                fill(g, lx0 - 3, y - 2, lx1, y + rowH - 3, FxPalette.KUVA, 0.07F * gl * eT);
                fill(g, lx0 - 5, y - 2, lx0 - 4, y + rowH - 3, FxPalette.KUVA, 0.9F * gl * eT);
                GuiShapes.end();
            }
            if (dimmed(now - openClock)) {
                g.renderItem(r.icon(), lx0, y);
                iconVeils.add(new int[]{lx0, y, Math.round(450.0F * (1.0F - gl))});
            }
            int nameRgb = FxCurve.lerpColor(FxPalette.ASH, FxPalette.BONE, Math.min(1.0F, gl * 1.6F));
            text(g, r.name().getString(), textX, lines > 0 ? y : y + 4, nameRgb, (0.65F + 0.35F * gl) * eT);
            for (int l = 0; l < lines; l++) {
                text(g, sub.get(l), textX, y + 10 + 10 * l, FxPalette.ASH, (0.45F + 0.25F * gl) * eT);
            }
            hot(lx0 - 3, y - 2, lx1, y + rowH - 3, r.tip(), null);
            y += rowH;
        }
        return y;
    }

    /** 配方表的图标：开场盖板 + 没点亮的那几行压暗一点（物品不能半透明，只能盖）；背景压暗前图标不画，这里也就没有要盖的 */
    protected void drawRecipeVeils(GuiGraphics g, long t) {
        float cov = 1.0F - phase(t, T_TEXT_IN, T_TEXT_DONE);
        GuiShapes.begin(g);
        for (int[] v : iconVeils) {
            float veil = v[2] / 1000.0F;
            float a = 1.0F - (1.0F - cov) * (1.0F - veil);
            fill(g, v[0], v[1], v[0] + 16, v[1] + 16, INK, a);
        }
        GuiShapes.end();
    }

    // ==================== 右栏 ====================

    /** 右栏标题和下划线 */
    protected void drawRightHead(GuiGraphics g, Component head, float eF) {
        heading(g, head.getString(), rx0, eF, 2.0F);
        hl(g, rx0, rx1, hairY, FxPalette.KUVA_PALE, 0.35F * eF);
    }

    /** 一段段字往下排（按栏宽断行），到 {@code maxY} 为止，返回排到的 y */
    protected int drawLines(GuiGraphics g, List<Line> lines, int x, int y, float w, float alpha, int maxY) {
        for (Line line : lines) {
            for (String s : wrapEven(line.text(), w)) {
                if (y + 9 > maxY) {
                    return y;
                }
                text(g, s, x, y, line.rgb(), line.alpha() * alpha);
                y += 10;
            }
            y += line.gap();
        }
        return y;
    }

    /** 名字「解码」出来：揭示后 {@code age} 毫秒时解到第几个字（前沿带几个乱码） */
    protected static String typed(String s, long age) {
        return scramble(s, age, 40);
    }

    protected static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
