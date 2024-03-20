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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import pers.roinflam.kuvalich.config.ConfigKuvaLich;
import pers.roinflam.kuvalich.inventory.container.ContainerRequiemGate;
import pers.roinflam.kuvalich.utils.Reference;

@SideOnly(Side.CLIENT)
public class GuiRequiemGate extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID + ":textures/gui/container/requiem_gate.png");

    public GuiRequiemGate(@NotNull EntityPlayer entityPlayer, @NotNull World world, @NotNull BlockPos pos) {
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
        GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(left, top, 0, 0, this.xSize, this.ySize);

        int progressX = left + 12;
        int progressY = top + 30;
        this.drawTexturedModalRect(left + 12, top + 30, 18, this.ySize, 152, 18);

        ContainerRequiemGate containerRequiemGate = (ContainerRequiemGate) this.inventorySlots;

        int level = containerRequiemGate.level;
        double schedule = containerRequiemGate.schedule;

        if (level == 0) {
            this.drawTexturedModalRect(progressX, progressY, 18, this.ySize + 18, (int) (152 * (schedule / ConfigKuvaLich.firstStage)), 18);
        } else if (level == 1) {
            this.drawTexturedModalRect(progressX, progressY, 18, this.ySize + 18, (int) (152 * (schedule / ConfigKuvaLich.secondStage)), 18);
        } else if (level == 2) {
            this.drawTexturedModalRect(progressX, progressY, 18, this.ySize + 18, (int) (152 * (schedule / ConfigKuvaLich.thirdStage)), 18);
        } else {
            this.drawTexturedModalRect(progressX, progressY, 18, this.ySize + 18, 152, 18);
        }

        int oneRiddleX = left + 26;
        int twoRiddleX = left + 80;
        int threeRiddleX = left + 134;
        int riddleY = top + 56;

        if (containerRequiemGate.threeRiddle == -1) {
            this.drawTexturedModalRect(threeRiddleX, riddleY, 0, this.ySize, 16, 16);
        }
        if (containerRequiemGate.twoRiddle == -1) {
            this.drawTexturedModalRect(twoRiddleX, riddleY, 0, this.ySize, 16, 16);
        }
        if (containerRequiemGate.oneRiddle == -1) {
            this.drawTexturedModalRect(oneRiddleX, riddleY, 0, this.ySize, 16, 16);
        }
        if (containerRequiemGate.threeRiddle != -1) {
            this.drawCard(new ItemStack(RequiemCardBase.getCard(containerRequiemGate.threeRiddle)), threeRiddleX, riddleY);
            if (mouseX >= threeRiddleX && mouseY >= riddleY && mouseX < threeRiddleX + 16 && mouseY < riddleY + 16) {
                renderToolTip(new ItemStack(RequiemCardBase.getCard(containerRequiemGate.threeRiddle)), mouseX, mouseY);
            }
        }
        if (containerRequiemGate.twoRiddle != -1) {
            this.drawCard(new ItemStack(RequiemCardBase.getCard(containerRequiemGate.twoRiddle)), twoRiddleX, riddleY);
            if (mouseX >= twoRiddleX && mouseY >= riddleY && mouseX < twoRiddleX + 16 && mouseY < riddleY + 16) {
                renderToolTip(new ItemStack(RequiemCardBase.getCard(containerRequiemGate.twoRiddle)), mouseX, mouseY);
            }
        }
        if (containerRequiemGate.oneRiddle != -1) {
            this.drawCard(new ItemStack(RequiemCardBase.getCard(containerRequiemGate.oneRiddle)), oneRiddleX, riddleY);
            if (mouseX >= oneRiddleX && mouseY >= riddleY && mouseX < oneRiddleX + 16 && mouseY < riddleY + 16) {
                renderToolTip(new ItemStack(RequiemCardBase.getCard(containerRequiemGate.oneRiddle)), mouseX, mouseY);
            }
        }
    }

    private void drawCard(@NotNull ItemStack itemStack, int x, int y) {
        GlStateManager.translate(0.0F, 0.0F, 32.0F);
        this.zLevel = 200.0F;
        this.itemRender.zLevel = 200.0F;
        net.minecraft.client.gui.@Nullable FontRenderer font = itemStack.getItem().getFontRenderer(itemStack);
        if (font == null) {
            font = fontRenderer;
        }
        this.itemRender.renderItemAndEffectIntoGUI(itemStack, x, y);
        this.itemRender.renderItemOverlayIntoGUI(font, itemStack, x, y - (itemStack.isEmpty() ? 0 : 8), "");
        this.zLevel = 0.0F;
        this.itemRender.zLevel = 0.0F;
    }

//    protected void renderToolTip(List<String> list, int x, int y) {
//        this.drawHoveringText(list, x, y, fontRenderer);
//    }

}
