// 文件：GuiRequiemGate.java
// 路径：src/main/java/pers/roinflam/kuvalich/network/GuiRequiemGate.java
package pers.roinflam.kuvalich.network;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.inventory.container.ContainerRequiemGate;
import pers.roinflam.kuvalich.utils.Reference;

@SideOnly(Side.CLIENT)
public class GuiRequiemGate extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID + ":textures/gui/container/requiem_gate.png"
    );

    // GUI坐标常量
    private static final int PROGRESS_X_OFFSET = 12;
    private static final int PROGRESS_Y_OFFSET = 30;
    private static final int PROGRESS_WIDTH = 152;
    private static final int PROGRESS_HEIGHT = 18;

    private static final int RIDDLE_Y_OFFSET = 56;
    private static final int RIDDLE_SIZE = 16;
    private static final int FIRST_RIDDLE_X_OFFSET = 26;
    private static final int SECOND_RIDDLE_X_OFFSET = 80;
    private static final int THIRD_RIDDLE_X_OFFSET = 134;

    public GuiRequiemGate(EntityPlayer entityPlayer, World world, BlockPos pos) {
        super(new ContainerRequiemGate(entityPlayer, world, pos));
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = (this.width - this.xSize) / 2;
        int top = (this.height - this.ySize) / 2;

        // 绘制背景
        GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(left, top, 0, 0, this.xSize, this.ySize);

        // 绘制进度条
        drawProgressBar(left, top);

        // 绘制谜题卡片
        drawRiddleCards(left, top, mouseX, mouseY);
    }

    /**
     * 绘制进度条
     */
    private void drawProgressBar(int left, int top) {
        int progressX = left + PROGRESS_X_OFFSET;
        int progressY = top + PROGRESS_Y_OFFSET;

        // 绘制进度条背景
        this.drawTexturedModalRect(
                progressX, progressY,
                PROGRESS_HEIGHT, this.ySize,
                PROGRESS_WIDTH, PROGRESS_HEIGHT
        );

        ContainerRequiemGate container = (ContainerRequiemGate) this.inventorySlots;
        int progressWidth = calculateProgressWidth(container.level, container.schedule);

        // 绘制进度条前景
        if (progressWidth > 0) {
            this.drawTexturedModalRect(
                    progressX, progressY,
                    PROGRESS_HEIGHT, this.ySize + PROGRESS_HEIGHT,
                    progressWidth, PROGRESS_HEIGHT
            );
        }
    }

    /**
     * 计算进度条宽度
     */
    private int calculateProgressWidth(int level, double schedule) {
        if (level >= 3) {
            return PROGRESS_WIDTH;
        }

        int threshold = getThresholdForLevel(level);
        if (threshold <= 0) {
            return 0;
        }

        return (int) (PROGRESS_WIDTH * (schedule / threshold));
    }

    /**
     * 获取等级对应的阈值
     */
    private int getThresholdForLevel(int level) {
        switch (level) {
            case 0: return ModConfig.KUVA_LICH.firstStage;
            case 1: return ModConfig.KUVA_LICH.secondStage;
            case 2: return ModConfig.KUVA_LICH.thirdStage;
            default: return 0;
        }
    }

    /**
     * 绘制谜题卡片
     */
    private void drawRiddleCards(int left, int top, int mouseX, int mouseY) {
        ContainerRequiemGate container = (ContainerRequiemGate) this.inventorySlots;
        int riddleY = top + RIDDLE_Y_OFFSET;

        // 绘制三个卡片槽位
        drawRiddleSlot(left + FIRST_RIDDLE_X_OFFSET, riddleY, container.oneRiddle, mouseX, mouseY);
        drawRiddleSlot(left + SECOND_RIDDLE_X_OFFSET, riddleY, container.twoRiddle, mouseX, mouseY);
        drawRiddleSlot(left + THIRD_RIDDLE_X_OFFSET, riddleY, container.threeRiddle, mouseX, mouseY);
    }

    /**
     * 绘制单个谜题槽位
     */
    private void drawRiddleSlot(int x, int y, int riddleId, int mouseX, int mouseY) {
        if (riddleId == -1) {
            // 绘制空槽位
            this.drawTexturedModalRect(x, y, 0, this.ySize, RIDDLE_SIZE, RIDDLE_SIZE);
        } else {
            // 绘制卡片
            ItemStack card = new ItemStack(RequiemCardBase.getCard(riddleId));
            drawCard(card, x, y);

            // 检查鼠标悬停
            if (isMouseOver(x, y, RIDDLE_SIZE, RIDDLE_SIZE, mouseX, mouseY)) {
                renderToolTip(card, mouseX, mouseY);
            }
        }
    }

    /**
     * 检查鼠标是否悬停在区域上
     */
    private boolean isMouseOver(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    /**
     * 绘制卡片物品
     */
    private void drawCard(ItemStack itemStack, int x, int y) {
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        GlStateManager.translate(0.0F, 0.0F, 32.0F);
        this.zLevel = 200.0F;
        this.itemRender.zLevel = 200.0F;

        net.minecraft.client.gui.FontRenderer font = itemStack.getItem().getFontRenderer(itemStack);
        if (font == null) {
            font = fontRenderer;
        }

        this.itemRender.renderItemAndEffectIntoGUI(itemStack, x, y);
        this.itemRender.renderItemOverlayIntoGUI(
                font, itemStack, x, y - (itemStack.isEmpty() ? 0 : 8), ""
        );

        this.zLevel = 0.0F;
        this.itemRender.zLevel = 0.0F;
    }
}