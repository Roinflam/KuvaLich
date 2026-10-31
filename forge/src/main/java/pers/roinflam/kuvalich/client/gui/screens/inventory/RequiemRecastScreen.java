package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier.Op;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemRecastMenu;

/**
 * 安魂之铸GUI（1.20.1版本）
 * Requiem Recast Screen (1.20.1 version)
 *
 * <p>取走输出格时放三合一或分解的演出和声音，只在本人客户端。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemRecastScreen extends AbstractContainerScreen<RequiemRecastMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_recast.png"
    );

    private final GuiFx fx = new GuiFx();
    private boolean opened;

    public RequiemRecastScreen(RequiemRecastMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        if (!opened) {
            opened = true;
            GuiFx.playOpen();
        }
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int button, ClickType type) {
        Op op = Op.NONE;
        int rarity = -1;
        int source = -1;
        Slot out = menu.getSlot(ForgeOpClassifier.RECAST_OUTPUT);
        // 在 super 之前判：super 会在本地先把输出拿走
        if (slot == out && ForgeOpClassifier.takesOutput(out, menu.getCarried(), button, type)) {
            op = ForgeOpClassifier.classify(menu);
            if (op == Op.DECOMPOSE) {
                source = ForgeOpClassifier.singleModuleInput(menu);
                rarity = ForgeOpClassifier.rarity(menu.getSlot(source).getItem());
            } else if (op == Op.MERGE) {
                rarity = ForgeOpClassifier.rarity(out.getItem());
            }
        }
        super.slotClicked(slot, slotId, button, type);
        if (op == Op.DECOMPOSE && source >= 0) {
            fx.decompose(menu.getSlot(source), out, rarity);
        } else if (op == Op.MERGE) {
            fx.merge(menu.getSlot(0), menu.getSlot(1), menu.getSlot(2), out, rarity);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        fx.render(guiGraphics, this.leftPos, this.topPos, !this.menu.getCarried().isEmpty());
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
