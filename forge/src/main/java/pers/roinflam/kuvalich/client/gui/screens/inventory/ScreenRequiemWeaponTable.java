package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.gui.codex.ModuleCodexScreen;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemWeaponTable;

/**
 * 武器军械库GUI（1.20.1版本）
 * Weapon Table Screen (1.20.1 version)
 *
 * ⭐ TAB键打开武器模组图鉴（showWeapon=true）
 * ⭐ 底部提示使用清晰的浅色文字 + 半透明底衬
 */
@OnlyIn(Dist.CLIENT)
public class ScreenRequiemWeaponTable extends AbstractContainerScreen<MenuRequiemWeaponTable> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_weapon_table.png"
    );

    public ScreenRequiemWeaponTable(MenuRequiemWeaponTable menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 175;
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
        guiGraphics.drawString(this.font, hint, hx, hy, 0xFFBBBBBB, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);
        guiGraphics.blit(TEXTURE, left + 12, top + 30, 18, this.imageHeight, 152, 18);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            Minecraft.getInstance().setScreen(new ModuleCodexScreen(this, true));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
