package pers.roinflam.kuvalich.network;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.inventory.container.ContainerRequiemEvolve;
import pers.roinflam.kuvalich.inventory.container.ContainerRequiemRecast;
import pers.roinflam.kuvalich.utils.Reference;

@SideOnly(Side.CLIENT)
public class GuiRequiemEvolve extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.MOD_ID + ":textures/gui/container/requiem_evolve.png");

    public GuiRequiemEvolve(@NotNull EntityPlayer entityPlayer, @NotNull World world, @NotNull BlockPos pos) {
        super(new ContainerRequiemEvolve(entityPlayer, world, pos));
        this.xSize = 176;
        this.ySize = 185;
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
    }


}
