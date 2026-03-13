package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemGate;

@OnlyIn(Dist.CLIENT)
public class ScreenRequiemGate extends AbstractContainerScreen<MenuRequiemGate> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_gate.png"
    );

    private static final int PROGRESS_X_OFFSET = 12;
    private static final int PROGRESS_Y_OFFSET = 30;
    private static final int PROGRESS_WIDTH = 152;
    private static final int PROGRESS_HEIGHT = 18;

    private static final int RIDDLE_Y_OFFSET = 56;
    private static final int RIDDLE_SIZE = 16;
    private static final int FIRST_RIDDLE_X_OFFSET = 26;
    private static final int SECOND_RIDDLE_X_OFFSET = 80;
    private static final int THIRD_RIDDLE_X_OFFSET = 134;

    public ScreenRequiemGate(MenuRequiemGate menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
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

        guiGraphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);
        drawProgressBar(guiGraphics, left, top);
        drawRiddleCards(guiGraphics, left, top, mouseX, mouseY);
    }

    private void drawProgressBar(GuiGraphics guiGraphics, int left, int top) {
        int progressX = left + PROGRESS_X_OFFSET;
        int progressY = top + PROGRESS_Y_OFFSET;

        guiGraphics.blit(
                TEXTURE,
                progressX, progressY,
                PROGRESS_HEIGHT, this.imageHeight,
                PROGRESS_WIDTH, PROGRESS_HEIGHT
        );

        int progressWidth = calculateProgressWidth(menu.getLevel(), menu.getSchedule());

        if (progressWidth > 0) {
            guiGraphics.blit(
                    TEXTURE,
                    progressX, progressY,
                    PROGRESS_HEIGHT, this.imageHeight + PROGRESS_HEIGHT,
                    progressWidth, PROGRESS_HEIGHT
            );
        }
    }

    private int calculateProgressWidth(int level, int schedule) {
        if (level >= 3) {
            return PROGRESS_WIDTH;
        }

        int threshold = getThresholdForLevel(level);
        if (threshold <= 0) {
            return 0;
        }

        return (int) (PROGRESS_WIDTH * ((double) schedule / threshold));
    }

    private int getThresholdForLevel(int level) {
        switch (level) {
            case 0: return ModConfig.KUVA_LICH.firstStage.get();
            case 1: return ModConfig.KUVA_LICH.secondStage.get();
            case 2: return ModConfig.KUVA_LICH.thirdStage.get();
            default: return 0;
        }
    }

    private void drawRiddleCards(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        int riddleY = top + RIDDLE_Y_OFFSET;

        drawRiddleSlot(guiGraphics, left + FIRST_RIDDLE_X_OFFSET, riddleY,
                menu.getOneRiddle(), mouseX, mouseY);
        drawRiddleSlot(guiGraphics, left + SECOND_RIDDLE_X_OFFSET, riddleY,
                menu.getTwoRiddle(), mouseX, mouseY);
        drawRiddleSlot(guiGraphics, left + THIRD_RIDDLE_X_OFFSET, riddleY,
                menu.getThreeRiddle(), mouseX, mouseY);
    }

    private void drawRiddleSlot(GuiGraphics guiGraphics, int x, int y, int riddleId,
                                int mouseX, int mouseY) {
        if (riddleId == -1) {
            guiGraphics.blit(TEXTURE, x, y, 0, this.imageHeight, RIDDLE_SIZE, RIDDLE_SIZE);
        } else {
            ItemStack card = new ItemStack(AbstractRequiemCard.getCard(riddleId));
            drawCard(guiGraphics, card, x, y);

            if (isMouseOver(x, y, RIDDLE_SIZE, RIDDLE_SIZE, mouseX, mouseY)) {
                guiGraphics.renderTooltip(this.font, card, mouseX, mouseY);
            }
        }
    }

    private boolean isMouseOver(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private void drawCard(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y) {
        if (itemStack.isEmpty()) {
            return;
        }

        guiGraphics.renderItem(itemStack, x, y);
        guiGraphics.renderItemDecorations(this.font, itemStack, x, y - 8);
    }
}