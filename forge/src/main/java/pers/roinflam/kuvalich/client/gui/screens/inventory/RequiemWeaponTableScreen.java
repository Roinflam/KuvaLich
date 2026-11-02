package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.world.inventory.RequiemWeaponTableMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 安魂之武器军械库界面，画成玄骸档案的另一页
 *
 * <ul>
 *   <li>左栏：武器的大投影（三倍大的物品图标轻轻浮着，背后两道反向转的圆弧，框里一层全息横纹、四角括号、底边刻度，
 *       隔几秒一道扫描光从上往下扫过），往下一根细线连到武器格，再下面是武器名和模组上限；
 *       武器放上去时投影从下往上扫出来、名字「解码」出来，拿走时投影往中线收拢；</li>
 *   <li>中栏：八张模组卡，没放武器时全部画叉，武器上台后叉一个个淡掉；电路从各卡流到武器格；</li>
 *   <li>右栏：已装模组清单和属性加成；底部「[TAB] 模组图鉴」（见 {@link ArmoryPageScreen}）。</li>
 * </ul>
 *
 * <p>电路（也是装入模组时 {@link GuiFx} 流光走的路）：模组卡在中栏、武器格在左栏，折线先落到卡下面的一条横道上，
 * 往左走到武器那一列，再从投影和武器格之间的缝里落到武器格顶上。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemWeaponTableScreen extends ArmoryPageScreen<RequiemWeaponTableMenu> {

    /** 菜单里 8 是武器格 */
    private static final int WEAPON_SLOT = 8;
    /** 投影框的半宽、半高，投影的倍数 */
    private static final int BOX_HW = 28;
    private static final int BOX_HH = 26;
    private static final int BIG = 3;
    /** 投影上那道扫描光隔多久扫一次、一趟多久 */
    private static final int SWEEP_PERIOD = 3400;
    private static final int SWEEP_MS = 1000;

    /** 版式：投影中心 y、武器格 y（图标左上角）、投影框四边 */
    private int wcy;
    private int wiy;
    private int bx0;
    private int by0;
    private int bx1;
    private int by1;

    public RequiemWeaponTableScreen(RequiemWeaponTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, WEAPON_SLOT, true);
    }

    /** 武器格在横道正下方，投影框底边在横道正上方：电路从横道一落就进武器格 */
    @Override
    protected void layoutLeft() {
        wiy = laneY + 4;
        by1 = laneY - 3;
        by0 = by1 - 2 * BOX_HH;
        wcy = (by0 + by1) / 2;
        bx0 = lcx - BOX_HW;
        bx1 = lcx + BOX_HW;
        place(menu.getSlot(WEAPON_SLOT), lcx - 8, wiy);
    }

    /**
     * 每张模组卡到武器格的折线：下面一排直接往下落到横道；上面一排正下方挡着下面一排，
     * 先从左边出去、在两列之间的空隙里往下走。横道往左走到武器那一列，再往下落到武器格顶上
     */
    @Override
    protected int[] circuitPath(int i) {
        int x = modX[i];
        int y = modY[i];
        int wTop = wiy - 2;
        if (i >= 4) {
            return new int[]{x, y + CELL_HH + 1, x, laneY, lcx, laneY, lcx, wTop};
        }
        int gap = x - 15;
        return new int[]{x - CELL - 1, y, gap, y, gap, laneY, lcx, laneY, lcx, wTop};
    }

    /** 光点到了武器格：格框亮一下 */
    @Override
    protected void drawCircuitEnd(GuiGraphics g, float x, float y, float flash, float eT) {
        int cx0 = lcx - 9;
        int cy0 = wiy - 1;
        float k = flash * eT;
        additive(g, () -> {
            GuiShapes.frame(g, cx0, cy0, cx0 + 18, cy0 + 18, lw(), GuiShapes.argb(FxPalette.KUVA_PALE, 0.45F * k));
            fill(g, lcx - 1, cy0 - 2, lcx + 1, cy0, FxPalette.KUVA_PALE, 0.8F * k);
        });
    }

    // ==================== 规则（和菜单一致） ====================

    @Override
    protected boolean hasHost() {
        return menu.getSlot(WEAPON_SLOT).hasItem();
    }

    @Override
    protected int moduleLimit() {
        return menu.getModuleLimit();
    }

    @Override
    protected boolean acceptsModule(ItemStack stack) {
        return stack.getItem() instanceof AbstractWeaponModule;
    }

    @Override
    protected Component hostMissing() {
        return Component.translatable("kuvalich.weapon_table.need_weapon");
    }

    @Override
    protected Component onlyModules() {
        return Component.translatable("kuvalich.weapon_table.only_modules");
    }

    @Override
    protected Component emptyDetail() {
        return Component.translatable("kuvalich.weapon_table.detail_empty");
    }

    /** 武器格：台上已经有一把，或者放的不是能装模组的武器 */
    @Override
    protected Component otherReject(Slot slot, ItemStack carried) {
        if (slot.index != WEAPON_SLOT) {
            return Component.empty();
        }
        return Component.translatable(slot.hasItem() ? "kuvalich.weapon_table.weapon_taken"
                : "kuvalich.weapon_table.only_weapons");
    }

    @Override
    protected List<Component> lockTip(int i) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("kuvalich.weapon_table.locked")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(FxPalette.ASH))));
        Component why = hasHost()
                ? Component.translatable("kuvalich.weapon_table.locked.limit", moduleLimit())
                : Component.translatable("kuvalich.weapon_table.locked.need_weapon");
        out.add(why.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(KuvaPalette.MUTED))));
        return out;
    }

    @Override
    protected void onEvent(Ev type, int id, ItemStack stack, long delayMs) {
        if (type == Ev.PLACE && id == WEAPON_SLOT) {
            Slot s = menu.getSlot(WEAPON_SLOT);
            burst(s.x + 8, s.y - 2, 6, -Mth.HALF_PI, 1.2F, 22.0F, 520.0F, 0.0F, FxPalette.KUVA, delayMs);
            // 投影上台：框中心再扩一圈
            ring(lcx, wcy, FxPalette.KUVA, delayMs + 120);
        }
    }

    // ==================== 左栏 ====================

    @Override
    protected void drawLeft(GuiGraphics g, int mx, int my, long now, long t, float eF, float eT) {
        drawTitle(g, this.title.getString(), lx0, lx1 - lx0, eF);
        hl(g, lx0, lx1, hairY, FxPalette.KUVA_PALE, 0.35F * eF);

        Slot ws = menu.getSlot(WEAPON_SLOT);
        ItemStack w = pending(Ev.PLACE, WEAPON_SLOT) ? ItemStack.EMPTY : ws.getItem();
        long wi = age(Ev.PLACE, WEAPON_SLOT);
        float lineW = GuiShapes.pixels(g, lw());
        if (!w.isEmpty()) {
            GuiShapes.begin(g);
            fill(g, bx0, by0, bx1, by1, INK, 0.85F * eT);
            frame(g, bx0, by0, bx1, by1, FxPalette.KUVA_PALE, 0.15F * eT);
            // 全息横纹、底边刻度
            for (int y = by0 + 2; y < by1 - 1; y += 3) {
                hl(g, bx0 + 1, bx1 - 1, y, FxPalette.KUVA_PALE, 0.035F * eT);
            }
            for (int x = bx0 + 4, k = 0; x < bx1 - 2; x += 4, k++) {
                vl(g, x, by1 - (k % 4 == 0 ? 3 : 2), by1, FxPalette.KUVA_PALE, 0.18F * eT);
            }
            brackets(g, 0.7F * eT);
            GuiShapes.end();
            // 背光上下各探出框外一点，下边止在武器格顶上面
            drawBacklight(g, lcx - 24, by0 - 8, 48, by1 - by0 + 12, 0.9F * eT);
            // 背后两道反向转的圆弧
            float rot = (now % 12000L) / 12000.0F * Mth.TWO_PI;
            GuiShapes.additive(g, true);
            GuiShapes.begin(g);
            arc(g, lcx, wcy, 21.0F, 21.0F, rot, 1.9F, lineW, GuiShapes.argb(FxPalette.KUVA_PALE, 0.3F * eT));
            arc(g, lcx, wcy, 21.0F, 21.0F, rot + Mth.PI, 0.6F, lineW, GuiShapes.argb(FxPalette.KUVA_PALE, 0.2F * eT));
            arc(g, lcx, wcy, 24.0F, 24.0F, -rot * 1.6F, 0.9F, lineW * 2.0F, GuiShapes.argb(FxPalette.KUVA, 0.3F * eT));
            GuiShapes.end();
            GuiShapes.additive(g, false);
            float bob = Mth.sin((now % 2600L) / 2600.0F * Mth.TWO_PI) * 1.2F;
            if (wi < 300) {
                // 放上去：投影从下往上扫出来（扫描线在 drawLeftOverlay 里，要压在图标上面）
                int cy = Math.round(by1 - (by1 - by0) * easeOut(wi / 300.0F));
                g.enableScissor(bx0, cy, bx1, by1);
                drawBig(g, w, bob);
                g.disableScissor();
            } else {
                drawBig(g, w, bob);
            }
            if (mx >= bx0 && mx < bx1 && my >= by0 && my < by1) {
                tipStack = w;
            }
        } else {
            float br = 0.75F + 0.25F * Mth.sin((now % 2400L) / 2400.0F * Mth.TWO_PI);
            GuiShapes.begin(g);
            GuiShapes.dashedFrame(g, bx0, by0, bx1, by1, lw(), GuiShapes.argb(FxPalette.KUVA_PALE, 0.25F * eT * br));
            brackets(g, 0.35F * eT * br);
            GuiShapes.end();
            textC(g, Component.translatable("kuvalich.weapon_table.place_weapon").getString(), lcx, wcy - 4,
                    FxPalette.ASH, 0.5F * eT);
            // 刚拿走：投影往中线收拢
            Fx taken = last(Ev.TAKE, WEAPON_SLOT);
            long rm = taken == null ? Long.MAX_VALUE : now - taken.t0();
            if (taken != null && rm < 180) {
                float e = 1.0F - rm / 180.0F;
                int hh = Math.round((by1 - by0) * 0.5F * e);
                if (hh > 0) {
                    g.enableScissor(bx0, wcy - hh, bx1, wcy + hh);
                    drawBig(g, taken.stack(), 0.0F);
                    g.disableScissor();
                }
                additive(g, () -> hl(g, bx0, bx1, wcy, FxPalette.KUVA_PALE, 0.8F * e));
            }
        }

        // 投影到武器格的细线、武器格；放不进去时格框抖、闪红，格子本身不动
        long rj = age(Ev.REJECT, WEAPON_SLOT);
        int dx = rj < 300 ? Math.round(Mth.sin(rj / 22.0F) * 3.0F * (1.0F - rj / 300.0F)) : 0;
        int cx0 = lcx - 9;
        int cy0 = wiy - 1;
        boolean hv = isHovering(ws.x, ws.y, 16, 16, mx, my);
        GuiShapes.begin(g);
        vl(g, lcx, by1, wiy - 1, FxPalette.KUVA_PALE, 0.3F * eT);
        fill(g, cx0, cy0, cx0 + 18, cy0 + 18, INK, 0.7F * eT);
        if (hv) {
            fill(g, cx0, cy0, cx0 + 18, cy0 + 18, FxPalette.KUVA_PALE, 0.1F);
        }
        frame(g, cx0 + dx, cy0, cx0 + dx + 18, cy0 + 18, FxPalette.KUVA_PALE, (hv ? 0.6F : 0.4F) * eT);
        GuiShapes.end();
        if (rj < 300) {
            float k = 0.5F * (1.0F - rj / 300.0F);
            additive(g, () -> fill(g, cx0, cy0, cx0 + 18, cy0 + 18, FxPalette.KUVA, k));
        }

        // 武器名（最多两行，放上去时「解码」出来）和模组上限
        int ty = wiy + 22;
        if (!w.isEmpty()) {
            float cw = lx1 - lx0;
            List<String> lines = wrapEven(plainName(w), cw);
            if (lines.size() > 2) {
                lines = List.of(lines.get(0), fit(lines.get(1) + lines.get(2), cw));
            }
            long age = wi < 100_000L ? wi : Long.MAX_VALUE / 4;
            for (String line : lines) {
                drawDecodeC(g, line, lcx, ty, FxPalette.BONE, 0.9F * eT, age, 40);
                age -= line.length() * 40L;
                ty += 10;
            }
            textC(g, Component.translatable("kuvalich.weapon_table.limit", moduleLimit()).getString(), lcx, ty + 1,
                    FxPalette.ASH, 0.7F * eT);
        }
    }

    /** 投影框四角的括号，长 6 */
    private void brackets(GuiGraphics g, float a) {
        int c = 6;
        hl(g, bx0, bx0 + c, by0, FxPalette.KUVA_PALE, a);
        vl(g, bx0, by0, by0 + c, FxPalette.KUVA_PALE, a);
        hl(g, bx1 - c, bx1, by0, FxPalette.KUVA_PALE, a);
        vl(g, bx1 - 1, by0, by0 + c, FxPalette.KUVA_PALE, a);
        hl(g, bx0, bx0 + c, by1 - 1, FxPalette.KUVA_PALE, a);
        vl(g, bx0, by1 - c, by1, FxPalette.KUVA_PALE, a);
        hl(g, bx1 - c, bx1, by1 - 1, FxPalette.KUVA_PALE, a);
        vl(g, bx1 - 1, by1 - c, by1, FxPalette.KUVA_PALE, a);
    }

    /** 压在图标上面：投影扫出来的扫描线、隔几秒扫一遍的扫描光、武器放上去那一下白闪、开场时投影和武器格的盖板 */
    @Override
    protected void drawLeftOverlay(GuiGraphics g, int mx, int my, long now, long t) {
        Slot ws = menu.getSlot(WEAPON_SLOT);
        int cx0 = lcx - 9;
        int cy0 = wiy - 1;
        if (pending(Ev.PLACE, WEAPON_SLOT)) {
            GuiShapes.begin(g);
            fill(g, cx0 + 1, cy0 + 1, cx0 + 17, cy0 + 17, INK, 1.0F);
            GuiShapes.end();
            return;
        }
        if (!ws.hasItem()) {
            return;
        }
        long wi = age(Ev.PLACE, WEAPON_SLOT);
        if (wi < 300) {
            int cy = Math.round(by1 - (by1 - by0) * easeOut(wi / 300.0F));
            hl(g, bx0 + 1, bx1 - 1, cy, FxPalette.KUVA_PALE, 0.9F);
            additive(g, () -> GuiShapes.hline(g, bx0, bx1, cy, lw() * 3, GuiShapes.argb(FxPalette.KUVA, 0.5F)));
        } else if (t > 1200 && wi > 1200) {
            long ph = now % SWEEP_PERIOD;
            if (ph < SWEEP_MS) {
                float u = ph / (float) SWEEP_MS;
                float y = by0 + 1 + (by1 - by0 - 2) * u;
                float k = Mth.sin(u * Mth.PI);
                additive(g, () -> {
                    GuiShapes.hline(g, bx0 + 1, bx1 - 1, y, lw() * 3, GuiShapes.argb(FxPalette.KUVA, 0.16F * k));
                    GuiShapes.hline(g, bx0 + 1, bx1 - 1, y, lw(), GuiShapes.argb(FxPalette.KUVA_PALE, 0.35F * k));
                });
            }
        }
        if (wi < 250) {
            float k = 0.5F * (1.0F - wi / 250.0F);
            additive(g, () -> fill(g, cx0, cy0, cx0 + 18, cy0 + 18, FxPalette.KUVA_PALE, k));
        }
        float cov = 1.0F - phase(t, T_TEXT_IN, T_TEXT_DONE);
        if (cov > 0.0F) {
            GuiShapes.begin(g);
            fill(g, bx0, by0, bx1, by1, INK, cov);
            fill(g, cx0 + 1, cy0 + 1, cx0 + 17, cy0 + 17, INK, cov);
            GuiShapes.end();
        }
    }

    /** 武器的大投影：物品图标放大三倍，中心对准投影框中心，上下浮 {@code bob} */
    private void drawBig(GuiGraphics g, ItemStack stack, float bob) {
        int half = 8 * BIG;
        g.pose().pushPose();
        g.pose().translate(lcx - half, wcy - half + bob, 0.0F);
        g.pose().scale(BIG, BIG, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }
}
