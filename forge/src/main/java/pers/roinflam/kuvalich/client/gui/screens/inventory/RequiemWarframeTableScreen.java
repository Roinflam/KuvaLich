package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.gui.ContainerChrome;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.client.gui.codex.ModuleCodexScreen;
import pers.roinflam.kuvalich.world.inventory.RequiemWarframeTableMenu;

/**
 * 战甲军械库GUI（1.20.1版本）
 * Warframe Table Screen (1.20.1 version)
 *
 * ⭐ TAB键打开战甲模组图鉴（showWeapon=false）
 * ⭐ 底部提示使用清晰的浅色文字 + 半透明底衬
 */
@OnlyIn(Dist.CLIENT)
public class RequiemWarframeTableScreen extends AbstractContainerScreen<RequiemWarframeTableMenu> {

    /** 模组槽的下标区间（见 {@code RequiemWarframeTableMenu} 的 addSlot 顺序）：0~7 */
    private static final int MODULE_SLOT_END = 8;

    public RequiemWarframeTableScreen(RequiemWarframeTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 146;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // ⭐ TAB提示（带底衬，清晰可读）/ TAB hint with bg pill
        String hint = "[TAB] " + Component.translatable("kuvalich.codex.open_hint").getString();
        int hw = this.font.width(hint);
        int hx = (this.width - hw) / 2;
        int hy = (this.height + this.imageHeight) / 2 + 4;
        guiGraphics.drawString(this.font, hint, hx, hy, CodexTheme.ASH, true);
    }

    /**
     * 全自绘背景，不再使用任何贴图
     *
     * <p>与武器军械库的区别只有一处：<b>没有汇聚走线</b>。战甲模组直接挂在玩家的
     * Capability 上，没有「输出槽」这个终点 —— 原贴图上也正是因此没有画汇聚线。
     * 硬加一条走向不存在的终点的线，只会误导玩家去找那个并不存在的槽。</p>
     *
     * <p>第二次 blit（从 {@code v=imageHeight} 取条带）落在贴图可见区之下的透明像素上，
     * 什么都没画出来，是历史遗留的空转调用，一并去掉。</p>
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        ContainerChrome.panel(guiGraphics, left, top, this.imageWidth, this.imageHeight);
        ContainerChrome.zone(guiGraphics, left + 8, top + 6, this.imageWidth - 16, 51);

        // 模组区下方一条横向刻度，代替原贴图上那些零散的装饰描线
        int barY = top + 60;
        for (int x = left + 14; x < left + this.imageWidth - 14; x += 6) {
            int len = ((x - left) / 6) % 4 == 0 ? 3 : 1;
            guiGraphics.fill(x, barY, x + 1, barY + len,
                    CodexTheme.withAlpha(CodexTheme.TECH, 0x70));
        }

        ContainerChrome.slots(guiGraphics, this.menu, left, top,
                (slot, index) -> index < MODULE_SLOT_END
                        ? ContainerChrome.SlotKind.INPUT
                        : ContainerChrome.SlotKind.PLAYER);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            Minecraft.getInstance().setScreen(new ModuleCodexScreen(this, false));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
