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
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.client.gui.codex.ModuleCodexScreen;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemWarframeTableMenu;

/**
 * 战甲军械库GUI（1.20.1版本）
 * Warframe Table Screen (1.20.1 version)
 *
 * ⭐ TAB键打开战甲模组图鉴（showWeapon=false）
 * ⭐ 底部提示使用清晰的浅色文字 + 半透明底衬
 *
 * <p>装入、取出模组的演出靠每 tick 比模组格快照认出来（{@link ForgeOpClassifier.TableWatch}）。
 * 战甲军械库没有武器格，装入只有格框亮起、没有流向武器的那道线。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemWarframeTableScreen extends AbstractContainerScreen<RequiemWarframeTableMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_warframe_table.png"
    );

    /** 菜单里 0~7 是模组格 */
    private static final int MODULE_SLOTS = 8;

    private final GuiFx fx = new GuiFx();
    private final ForgeOpClassifier.TableWatch watch = new ForgeOpClassifier.TableWatch(MODULE_SLOTS, -1, null);
    private boolean opened;

    private String hint = "";
    private int hintWidth;

    public RequiemWarframeTableScreen(RequiemWarframeTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 146;
    }

    @Override
    protected void init() {
        super.init();
        this.hint = "[TAB] " + Component.translatable("kuvalich.codex.open_hint").getString();
        this.hintWidth = this.font.width(hint);
        if (!opened) {
            opened = true;
            GuiFx.playOpen();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        watch.tick(menu, fx);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        fx.render(guiGraphics, this.leftPos, this.topPos, !this.menu.getCarried().isEmpty());

        // ⭐ TAB提示（清晰可读）；先画提示再画物品说明，说明框盖得住它
        int hx = (this.width - hintWidth) / 2;
        int hy = (this.height + this.imageHeight) / 2 + 4;
        guiGraphics.drawString(this.font, hint, hx, hy, 0xFFBBBBBB, false);

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
        guiGraphics.blit(TEXTURE, left + 12, top + 30, 18, this.imageHeight, 152, 18);
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
