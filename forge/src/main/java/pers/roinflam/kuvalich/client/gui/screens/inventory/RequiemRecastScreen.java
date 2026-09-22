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
import pers.roinflam.kuvalich.world.inventory.RequiemRecastMenu;

/**
 * 安魂之铸GUI（1.20.1版本）
 * Requiem Recast Screen (1.20.1 version)
 */
@OnlyIn(Dist.CLIENT)
public class RequiemRecastScreen extends AbstractContainerScreen<RequiemRecastMenu> {

    /** 输入槽个数；见 {@code RequiemRecastMenu} 的 addSlot 顺序：先三个输入，再输出 */
    private static final int INPUT_COUNT = 3;
    /** 输出槽下标 —— 在三个输入槽之后，不是 0 */
    private static final int OUTPUT_SLOT = INPUT_COUNT;

    public RequiemRecastScreen(RequiemRecastMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
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
     * <p>这个界面的布局是三者中唯一「产物在上、输入在下」的：输出槽单独居中在上方，
     * 三个输入槽横排在下方。原贴图靠一组三叉汇聚线把这个方向讲清楚，
     * 代码里零对应 —— 不补的话，三个输入槽和上面那个孤零零的槽之间就断了联系。</p>
     *
     * <p>原先那条「进度条」同样是静态装饰（Menu 里没有任何进度字段），不再画。</p>
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        ContainerChrome.panel(guiGraphics, left, top, this.imageWidth, this.imageHeight);

        var output = this.menu.slots.get(OUTPUT_SLOT);
        int[] ox = new int[INPUT_COUNT];
        int[] oy = new int[INPUT_COUNT];
        for (int i = 0; i < INPUT_COUNT; i++) {
            var in = this.menu.slots.get(i);
            ox[i] = left + in.x + 8;
            oy[i] = top + in.y - 1;
        }

        ContainerChrome.zone(guiGraphics, left + 8, top + 4, this.imageWidth - 16, 68);

        // 三个输入向上汇聚到输出槽
        ContainerChrome.busRoute(guiGraphics, ox, oy,
                top + 38, left + output.x + 8, top + output.y + ContainerChrome.SLOT,
                CodexTheme.EMBER, Util.getMillis());

        ContainerChrome.slots(guiGraphics, this.menu, left, top, (slot, index) -> {
            if (index == OUTPUT_SLOT) return ContainerChrome.SlotKind.OUTPUT;
            if (index < INPUT_COUNT) return ContainerChrome.SlotKind.INPUT;
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