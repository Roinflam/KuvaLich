package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.module.ModulePlacementValidator;
import pers.roinflam.kuvalich.world.inventory.RequiemWarframeTableMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 安魂之战甲军械库界面，画成玄骸档案的另一页
 *
 * <ul>
 *   <li>左栏：玩家自己（战甲模组挂在玩家身上，不在哪件护甲上），垫一层背光、脚下一道细线，眼睛跟着鼠标；
 *       开场时人像从脚往头扫出来。下面是名字、一句说明和「装配概要」（已装配几个、模组等级合计、满级几张）；</li>
 *   <li>中栏：八张模组卡，没有上限、不画叉；电路从各卡流到人像旁边的节点；</li>
 *   <li>右栏：已装模组清单和属性加成；底部「[TAB] 模组图鉴」（见 {@link ArmoryPageScreen}）。</li>
 * </ul>
 *
 * <p>人像脚下一圈底座椭圆，一颗光点沿着绕；隔几秒一道扫描光从脚扫到头。
 * 没有武器格，装入时 {@code GuiFx} 只有格框亮起、没有流向武器的那道线；电路只是这边自己画的。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemWarframeTableScreen extends ArmoryPageScreen<RequiemWarframeTableMenu> {

    /** 人像上那道扫描光隔多久扫一次、一趟多久 */
    private static final int SWEEP_PERIOD = 4200;
    private static final int SWEEP_MS = 1300;

    /** 版式：人像的倍数、脚底 y、胸口 y、电路终点（人像右边的节点） */
    private int figScale;
    private int feetY;
    private int chestY;
    private int nodeX;
    /** 幻纹格的卡心：卡的左边贴左栏左边线（和栏标题对齐），和人像头部同高 */
    private int ephX;
    private int ephY;
    /** 人像渲染出过错就不再画了，免得每帧刷一遍异常 */
    private boolean figureFailed;

    public RequiemWarframeTableScreen(RequiemWarframeTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, -1, false);
    }

    /** 人像跟着窗口高度放大：427×240 时约 30 倍（身高 54），再大也不超过 64 倍 */
    @Override
    protected void layoutLeft() {
        figScale = Mth.clamp(Math.round(height * 0.13F), 28, 64);
        feetY = hairY + 12 + figScale * 2;
        chestY = feetY - Math.round(figScale * 1.3F);
        nodeX = lx1 - 4;
        ephX = lx0 + CELL;
        // 和头部同高：放在胸口高度会被人像手里拿的东西压住（缩放 4 时卡右边离手只剩一两格）。
        // 1.64 = 头部中心离脚底的格数（模型 28 像素 × 0.9375 / 16）
        ephY = feetY - Math.round(figScale * 1.64F);
        // 图标在卡里的位置和模组卡一样
        place(menu.getSlot(RequiemWarframeTableMenu.EPHEMERA_SLOT), ephX - 8, ephY - 10);
    }

    @Override
    protected int[] extraTracked() {
        return new int[]{RequiemWarframeTableMenu.EPHEMERA_SLOT};
    }

    @Override
    protected Component otherReject(Slot slot, ItemStack carried) {
        return slot.index == RequiemWarframeTableMenu.EPHEMERA_SLOT
                ? Component.translatable("kuvalich.warframe_table.only_ephemera") : Component.empty();
    }

    /** 空的幻纹格悬停时说明这一格是做什么的；有东西时原版照常出物品说明 */
    @Override
    protected void renderPage(GuiGraphics g, int mx, int my, float partialTick) {
        super.renderPage(g, mx, my, partialTick);
        Slot hs = this.hoveredSlot;
        if (hs != null && hs.index == RequiemWarframeTableMenu.EPHEMERA_SLOT && !hs.hasItem()
                && menu.getCarried().isEmpty()) {
            g.renderComponentTooltip(this.font, List.of(
                    Component.translatable("kuvalich.warframe_table.ephemera_slot"),
                    Component.translatable("kuvalich.warframe_table.ephemera_slot.desc").withStyle(ChatFormatting.GRAY)),
                    mx, my);
        }
    }

    /**
     * 每张模组卡到人像旁节点的折线：和武器军械库一样先落到卡下面的横道（上面一排从两列之间的空隙往下走），
     * 横道往左走到中栏左边，沿分隔线内侧上到胸口高度，再往左接到节点
     */
    @Override
    protected int[] circuitPath(int i) {
        int x = modX[i];
        int y = modY[i];
        int vx = sepL + 6;
        if (i >= 4) {
            return new int[]{x, y + CELL_HH + 1, x, laneY, vx, laneY, vx, chestY, nodeX, chestY};
        }
        int gap = x - 15;
        return new int[]{x - CELL - 1, y, gap, y, gap, laneY, vx, laneY, vx, chestY, nodeX, chestY};
    }

    /** 光点到了节点：菱形亮一下 */
    @Override
    protected void drawCircuitEnd(GuiGraphics g, float x, float y, float flash, float eT) {
        float k = flash * eT;
        additive(g, () -> {
            GuiShapes.diamondFill(g, nodeX, chestY, 3.0F, 3.5F, GuiShapes.argb(FxPalette.KUVA_PALE, 0.7F * k));
            GuiShapes.ring(g, nodeX, chestY, 4.0F + 4.0F * (1.0F - flash), GuiShapes.pixels(g, lw()),
                    GuiShapes.argb(FxPalette.KUVA, 0.5F * k), 1.0F);
        });
    }

    // ==================== 规则（和菜单一致） ====================

    @Override
    protected boolean hasHost() {
        return true;
    }

    @Override
    protected int moduleLimit() {
        return ModulePlacementValidator.SLOT_COUNT;
    }

    @Override
    protected boolean acceptsModule(ItemStack stack) {
        return stack.getItem() instanceof AbstractWarframeModule;
    }

    @Override
    protected Component onlyModules() {
        return Component.translatable("kuvalich.warframe_table.only_modules");
    }

    @Override
    protected Component emptyDetail() {
        return Component.translatable("kuvalich.warframe_table.detail_empty");
    }

    // ==================== 左栏 ====================

    @Override
    protected void drawLeft(GuiGraphics g, int mx, int my, long now, long t, float eF, float eT) {
        drawTitle(g, this.title.getString(), lx0, lx1 - lx0, eF);
        hl(g, lx0, lx1, hairY, FxPalette.KUVA_PALE, 0.35F * eF);

        LocalPlayer p = Minecraft.getInstance().player;
        int top = figureTop(p);
        drawBacklight(g, lcx - 26, top - 8, 52, feetY - top + 16, 0.8F * eT);
        float lineW = GuiShapes.pixels(g, lw());
        GuiShapes.begin(g);
        int c = GuiShapes.argb(FxPalette.KUVA_PALE, 0.35F * eT);
        GuiShapes.hlineFade(g, lcx - 30, lcx, feetY + 1, lw(), c & 0x00FFFFFF, c);
        GuiShapes.hlineFade(g, lcx, lcx + 30, feetY + 1, lw(), c, c & 0x00FFFFFF);
        // 底座椭圆、人像到节点的虚线、节点
        ellipse(g, lcx, feetY + 1, 24.0F, 5.0F, lineW, GuiShapes.argb(FxPalette.KUVA_PALE, 0.22F * eT));
        ellipse(g, lcx, feetY + 1, 18.0F, 3.5F, lineW, GuiShapes.argb(FxPalette.KUVA, 0.18F * eT));
        int dash = GuiShapes.argb(FxPalette.KUVA_PALE, 0.18F * eT);
        float beamX0 = lcx + figScale * 0.4F;
        for (float x = beamX0; x < nodeX - 4; x += 4.0F) {
            GuiShapes.hairline(g, x, chestY, Math.min(x + 2.0F, nodeX - 4), chestY, lineW, dash);
        }
        GuiShapes.diamondOutline(g, nodeX, chestY, 3.0F, 3.5F, lineW, GuiShapes.argb(FxPalette.KUVA_PALE, 0.5F * eT));
        // 幻纹格连到人像头部左边的虚线
        float beamX1 = lcx - figScale * 0.3F;
        for (float x = ephX + CELL + 3; x < beamX1; x += 4.0F) {
            GuiShapes.hairline(g, x, ephY, Math.min(x + 2.0F, beamX1), ephY, lineW, dash);
        }
        GuiShapes.end();
        drawEphemeraCard(g, mx, my, t);
        // 底座上一颗光点绕着走（拖一小截尾巴）
        float orbit = (now % 3200L) / 3200.0F * Mth.TWO_PI;
        GuiShapes.additive(g, true);
        GuiShapes.begin(g);
        for (int k = 0; k < 5; k++) {
            float a = orbit - k * 0.12F;
            float ox = lcx + 24.0F * Mth.cos(a);
            float oy = feetY + 1 + 5.0F * Mth.sin(a);
            float s = k == 0 ? 1.0F : 0.5F;
            fill(g, ox - s, oy - s, ox + s, oy + s, k == 0 ? FxPalette.KUVA_PALE : FxPalette.KUVA,
                    (k == 0 ? 0.9F : 0.5F - k * 0.09F) * eT);
        }
        GuiShapes.end();
        GuiShapes.additive(g, false);

        float eS = phase(t, T_TEXT_IN, T_TEXT_DONE);
        if (p != null && eS > 0.0F && !figureFailed) {
            // 开场：从脚往头扫出来（扫描线在 drawLeftOverlay 里，要压在人像上面）
            if (eS < 1.0F) {
                clip(g, lx0 - 8, scanY(top, eS), lx1 + 8, feetY + 3);
                drawFigure(g, p, mx, my);
                unclip(g);
            } else {
                drawFigure(g, p, mx, my);
            }
        }

        int ty = feetY + 6;
        if (p != null) {
            textC(g, fit(p.getName().getString(), lx1 - lx0), lcx, ty, FxPalette.BONE, 0.9F * eT);
        }
        ty += 14;
        for (String line : wrapEven(Component.translatable("kuvalich.warframe_table.on_body").getString(), lx1 - lx0)) {
            text(g, line, lx0, ty, FxPalette.ASH, 0.5F * eT);
            ty += 10;
        }
        List<Info> info = new ArrayList<>();
        loadoutInfo(info);
        drawInfo(g, tr("kuvalich.armory.info.loadout"), info, ty + 6, eT, now);
    }

    /** 开场扫描线；之后隔几秒一道扫描光从脚扫到头 */
    @Override
    protected void drawLeftOverlay(GuiGraphics g, int mx, int my, long now, long t) {
        float eS = phase(t, T_TEXT_IN, T_TEXT_DONE);
        if (eS >= 1.0F && t > 1500) {
            long ph = now % SWEEP_PERIOD;
            if (ph < SWEEP_MS) {
                float u = ph / (float) SWEEP_MS;
                int y = scanY(figureTop(Minecraft.getInstance().player), u);
                float k = Mth.sin(u * Mth.PI);
                additive(g, () -> {
                    GuiShapes.hline(g, lcx - 22, lcx + 22, y, lw() * 3, GuiShapes.argb(FxPalette.KUVA, 0.14F * k));
                    GuiShapes.hline(g, lcx - 18, lcx + 18, y, lw(), GuiShapes.argb(FxPalette.KUVA_PALE, 0.3F * k));
                });
            }
            return;
        }
        if (eS <= 0.0F) {
            return;
        }
        int cy = scanY(figureTop(Minecraft.getInstance().player), eS);
        float a = 1.0F - eS * 0.5F;
        hl(g, lcx - 22, lcx + 22, cy, FxPalette.KUVA_PALE, 0.9F * a);
        additive(g, () -> GuiShapes.hline(g, lcx - 26, lcx + 26, cy, lw() * 3, GuiShapes.argb(FxPalette.KUVA, 0.5F * a)));
    }

    private void drawEphemeraCard(GuiGraphics g, int mx, int my, long t) {
        ItemStack st = menu.getSlot(RequiemWarframeTableMenu.EPHEMERA_SLOT).getItem();
        int col = st.getItem() instanceof EphemeraItem ? EphemeraItem.getColor1(st) : FxPalette.KUVA_PALE;
        boolean hv = Math.abs(mx - ephX) <= CELL && Math.abs(my - ephY) <= CELL_HH;
        // 排在八张模组卡后面出场
        float e = phase(t, 600 + 50 * MODULES, 900 + 50 * MODULES);
        drawSingleCard(g, ephX, ephY, RequiemWarframeTableMenu.EPHEMERA_SLOT, st, col, hv, e);
    }

    private int figureTop(LocalPlayer p) {
        float h = p != null ? p.getBbHeight() : 1.8F;
        return feetY - Math.round(h * figScale) - 4;
    }

    private int scanY(int top, float e) {
        return Math.round(feetY + 2 - (feetY + 2 - top) * e);
    }

    /**
     * 人像：原版物品栏那个画法，眼睛跟着鼠标。渲染半途抛异常会留下没弹掉的矩阵和实体光照，这里接住还原，
     * 之后不再画
     */
    private void drawFigure(GuiGraphics g, LocalPlayer p, int mx, int my) {
        float eye = p.getEyeHeight() * figScale;
        PoseStack.Pose root = g.pose().last();
        try {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, lcx, feetY, figScale,
                    lcx - mx, feetY - eye - my, p);
        } catch (Throwable e) {
            while (!g.pose().clear() && g.pose().last() != root) {
                g.pose().popPose();
            }
            Minecraft.getInstance().getEntityRenderDispatcher().setRenderShadow(true);
            Lighting.setupFor3DItems();
            figureFailed = true;
        }
    }
}
