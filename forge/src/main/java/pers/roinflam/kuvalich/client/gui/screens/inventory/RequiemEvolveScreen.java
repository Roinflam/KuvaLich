package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.client.gui.ContainerChrome;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

/**
 * 安魂之融GUI（1.20.1版本）
 * Requiem Evolve Screen (1.20.1 version)
 */
@OnlyIn(Dist.CLIENT)
public class RequiemEvolveScreen extends AbstractContainerScreen<RequiemEvolveMenu> {

    /** 武器槽下标（见 {@code RequiemEvolveMenu} 的 addSlot 顺序） */
    private static final int WEAPON_SLOT = 0;
    /** 材料槽下标 */
    private static final int MATERIAL_SLOT = 1;
    /** 结果槽下标 */
    private static final int RESULT_SLOT = 2;

    public RequiemEvolveScreen(RequiemEvolveMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 185;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * 全自绘背景，不再使用任何贴图
     *
     * <p>原贴图在武器槽与材料槽之间画着指向结果槽的箭头和一圈法阵纹样 ——
     * 那是这个界面唯一告诉玩家「东西要往中间放」的东西，代码里零对应。
     * 这里用走线复刻：两个输入槽的线汇到一条母线，再拐进结果槽，
     * 沿线跑一颗流动光点表示方向。</p>
     *
     * <p>原先那条「进度条」是从贴图里 blit 的一张静态子图 —— 对应的 Menu 里
     * 根本没有 ContainerData 或进度字段，它永远不会变化，纯属装饰。
     * 既然是装饰，自绘版本就不再假装有进度条了。</p>
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        ContainerChrome.panel(guiGraphics, left, top, this.imageWidth, this.imageHeight);

        var weapon = this.menu.slots.get(WEAPON_SLOT);
        var material = this.menu.slots.get(MATERIAL_SLOT);
        var result = this.menu.slots.get(RESULT_SLOT);

        ContainerChrome.zone(guiGraphics, left + 8, top + 20, this.imageWidth - 16, 42);

        // 输入 → 结果 的方向指示
        ContainerChrome.busRoute(guiGraphics,
                new int[]{ left + weapon.x + 8, left + material.x + 8 },
                new int[]{ top + weapon.y - 1, top + material.y - 1 },
                top + 14,
                left + result.x + 8, top + result.y - 1,
                CodexTheme.EMBER, Util.getMillis());

        ContainerChrome.slots(guiGraphics, this.menu, left, top, (slot, index) -> {
            if (index == RESULT_SLOT) return ContainerChrome.SlotKind.OUTPUT;
            if (index == WEAPON_SLOT) return ContainerChrome.SlotKind.SUBJECT;
            if (index == MATERIAL_SLOT) return ContainerChrome.SlotKind.INPUT;
            return ContainerChrome.SlotKind.PLAYER;
        });
    }

    /**
     * 不渲染标签（标题和物品栏名称）
     * Do not render labels (title and inventory name)
     */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 留空 - 不渲染任何标签
        // Leave empty - do not render any labels
    }
}