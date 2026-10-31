package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.fx.gui.ForgeOpClassifier;
import pers.roinflam.kuvalich.client.fx.gui.GuiFx;
import pers.roinflam.kuvalich.client.gui.codex.ModuleCodexScreen;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemWeaponTableMenu;

/**
 * 武器军械库GUI（1.20.1版本）
 * Weapon Table Screen (1.20.1 version)
 *
 * TAB键打开武器模组图鉴（showWeapon=true）
 * 底部提示使用清晰的浅色文字 + 半透明底衬
 *
 * <p>装入、取出模组和武器上台的演出靠每 tick 比模组格快照认出来（{@link ForgeOpClassifier.TableWatch}），
 * 点击、Shift 点击、图鉴一键装配都覆盖到。装入时从那一格沿折线流一道光到武器格。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemWeaponTableScreen extends AbstractContainerScreen<RequiemWeaponTableMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_weapon_table.png"
    );

    /** 菜单里 0~7 是模组格，8 是武器格 */
    private static final int MODULE_SLOTS = 8;
    private static final int WEAPON_SLOT = 8;

    private final GuiFx fx = new GuiFx();
    private final ForgeOpClassifier.TableWatch watch;
    private boolean opened;

    private String hint = "";
    private int hintWidth;

    public RequiemWeaponTableScreen(RequiemWeaponTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 175;
        this.watch = new ForgeOpClassifier.TableWatch(MODULE_SLOTS, WEAPON_SLOT, buildPaths(menu));
    }

    /**
     * 每个模组格到武器格的折线：下面一排直接往下；上面一排正下方挡着下面一排的格子，
     * 先从朝武器那一侧出去、在两列之间的空隙里往下走，再横过去落到武器格顶上
     */
    private int[] buildPaths(RequiemWeaponTableMenu menu) {
        Slot weapon = menu.getSlot(WEAPON_SLOT);
        int wx = weapon.x + 8;
        int wTop = weapon.y - 2;
        int lowestBottom = 0;
        for (int i = 0; i < MODULE_SLOTS; i++) {
            lowestBottom = Math.max(lowestBottom, menu.getSlot(i).y + 17);
        }
        int lane = (lowestBottom + wTop) / 2;
        int[] ids = new int[MODULE_SLOTS];
        for (int i = 0; i < MODULE_SLOTS; i++) {
            Slot s = menu.getSlot(i);
            int cx = s.x + 8;
            if (!blockedBelow(menu, s)) {
                ids[i] = fx.path(cx, s.y + 17, cx, lane, wx, lane, wx, wTop);
                continue;
            }
            int cy = s.y + 8;
            boolean right = cx < wx;
            int exitX = right ? s.x + 17 : s.x - 2;
            // 两列之间空隙的中线（列距 41、格框 18），离武器中线不到 2 px 就直接对齐，免得最后拐一个 1 px 的小弯
            int gap = right ? s.x + 28 : s.x - 13;
            if (Math.abs(gap - wx) <= 2) {
                gap = wx;
            }
            ids[i] = fx.path(exitX, cy, gap, cy, gap, lane, wx, lane, wx, wTop);
        }
        return ids;
    }

    private static boolean blockedBelow(RequiemWeaponTableMenu menu, Slot s) {
        for (int i = 0; i < MODULE_SLOTS; i++) {
            Slot o = menu.getSlot(i);
            if (o != s && o.x == s.x && o.y > s.y) {
                return true;
            }
        }
        return false;
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

        // TAB提示（清晰可读）；先画提示再画物品说明，说明框盖得住它
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
            Minecraft.getInstance().setScreen(new ModuleCodexScreen(this, true));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
