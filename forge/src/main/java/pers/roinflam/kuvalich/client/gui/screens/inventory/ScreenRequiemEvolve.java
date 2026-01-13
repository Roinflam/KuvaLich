package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemEvolve;

/**
 * 安魂之融GUI（1.20.1版本）
 * Requiem Evolve Screen (1.20.1 version)
 */
@OnlyIn(Dist.CLIENT)
public class ScreenRequiemEvolve extends AbstractContainerScreen<MenuRequiemEvolve> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_evolve.png"
    );

    public ScreenRequiemEvolve(MenuRequiemEvolve menu, Inventory playerInventory, Component title) {
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

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        // 绘制背景 / Draw background
        guiGraphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);

        // 绘制进度条背景 / Draw progress bar background
        guiGraphics.blit(TEXTURE, left + 12, top + 30, 18, this.imageHeight, 152, 18);
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