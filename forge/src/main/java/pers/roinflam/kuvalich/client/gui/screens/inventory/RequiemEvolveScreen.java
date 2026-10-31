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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier.Op;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

/**
 * 安魂之融GUI（1.20.1版本）
 * Requiem Evolve Screen (1.20.1 version)
 *
 * <p>取走结果格时按这次是哪种操作（融合、塑形块、升级……）放演出和声音，全部只在本人客户端。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemEvolveScreen extends AbstractContainerScreen<RequiemEvolveMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_evolve.png"
    );

    private final GuiFx fx = new GuiFx();
    /** 融合时两条线：左边输入格从上面绕过去，中间材料格从下面绕过去，都落到结果格 */
    private final int pathFromInput;
    private final int pathFromMaterial;

    /** 最后一次看到的非空材料 */
    @Nullable
    private Item seenMaterial;
    /** 当前结果出现那一刻的材料（当场处理类靠它判操作） */
    @Nullable
    private Item madeWith;
    private boolean hadResult;
    private boolean opened;

    public RequiemEvolveScreen(RequiemEvolveMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 185;

        Slot in = menu.getSlot(ForgeOpClassifier.EVOLVE_INPUT);
        Slot mat = menu.getSlot(ForgeOpClassifier.EVOLVE_MATERIAL);
        Slot out = menu.getSlot(ForgeOpClassifier.EVOLVE_RESULT);
        int inX = in.x + 8;
        int matX = mat.x + 8;
        int outX = out.x + 8;
        // 格框占 y-1 ~ y+16，线从框外一格起步
        int above = in.y - 6;
        int below = mat.y + 21;
        this.pathFromInput = fx.path(inX, in.y - 2, inX, above, outX, above, outX, out.y - 2);
        this.pathFromMaterial = fx.path(matX, mat.y + 17, matX, below, outX, below, outX, out.y + 17);
    }

    @Override
    protected void init() {
        super.init();
        // 从别的界面回来、改窗口大小都会再走一次 init，开机音只在第一次打开时放
        if (!opened) {
            opened = true;
            GuiFx.playOpen();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        observe();
    }

    /**
     * 记下材料和结果的变化：结果从无到有的那一刻，把当时的材料（已经用光的话就是之前最后看到的那个）记成 madeWith
     */
    private void observe() {
        ItemStack material = menu.getSlot(ForgeOpClassifier.EVOLVE_MATERIAL).getItem();
        boolean hasResult = menu.getSlot(ForgeOpClassifier.EVOLVE_RESULT).hasItem();
        if (hasResult && !hadResult) {
            madeWith = material.isEmpty() ? seenMaterial : material.getItem();
        }
        hadResult = hasResult;
        if (!material.isEmpty()) {
            seenMaterial = material.getItem();
        }
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int button, ClickType type) {
        observe();
        Op op = Op.NONE;
        int level = 0;
        boolean locked = false;
        Slot out = menu.getSlot(ForgeOpClassifier.EVOLVE_RESULT);
        // 在 super 之前判：super 会在本地先把结果拿走
        if (slot == out && ForgeOpClassifier.takesOutput(out, menu.getCarried(), button, type)) {
            op = ForgeOpClassifier.classify(menu, madeWith);
            ItemStack result = out.getItem();
            if (op == Op.RANK_UP || op == Op.MAX_RANK) {
                level = ForgeOpClassifier.moduleLevel(result);
            } else if (op == Op.FORMA) {
                locked = ForgeOpClassifier.formaLocked(result);
            }
        }
        super.slotClicked(slot, slotId, button, type);
        observe();
        playOp(op, level, locked);
    }

    private void playOp(Op op, int level, boolean locked) {
        Slot in = menu.getSlot(ForgeOpClassifier.EVOLVE_INPUT);
        Slot mat = menu.getSlot(ForgeOpClassifier.EVOLVE_MATERIAL);
        Slot out = menu.getSlot(ForgeOpClassifier.EVOLVE_RESULT);
        switch (op) {
            case FUSION -> fx.fusion(pathFromInput, pathFromMaterial, out);
            case RIVEN_CYCLE -> fx.rivenCycle(out);
            case FORMA -> fx.forma(out, locked);
            case SHADOW_FORMA -> fx.shadowForma(out);
            // 当场处理类：武器这时已经在结果格里，光点注入结果格
            case IMBUE, GUN_ENHANCE -> fx.imbue(mat, out);
            // 升级：内融核心注入左边的模组格
            case RANK_UP -> fx.rankUp(mat, in, level, false);
            case MAX_RANK -> fx.rankUp(mat, in, level, true);
            default -> {
            }
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
