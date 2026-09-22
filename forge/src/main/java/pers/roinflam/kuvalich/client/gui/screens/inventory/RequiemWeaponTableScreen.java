package pers.roinflam.kuvalich.client.gui.screens.inventory;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.client.gui.ContainerChrome;
import pers.roinflam.kuvalich.client.gui.codex.CodexTheme;
import pers.roinflam.kuvalich.client.gui.codex.ModuleCodexScreen;
import pers.roinflam.kuvalich.world.inventory.RequiemWeaponTableMenu;

/**
 * 武器军械库GUI（1.20.1版本）
 * Weapon Table Screen (1.20.1 version)
 *
 * ⭐ TAB键打开武器模组图鉴（showWeapon=true）
 * ⭐ 底部提示使用清晰的浅色文字 + 半透明底衬
 */
@OnlyIn(Dist.CLIENT)
public class RequiemWeaponTableScreen extends AbstractContainerScreen<RequiemWeaponTableMenu> {

    /** 模组槽的下标区间（见 {@code RequiemWeaponTableMenu} 的 addSlot 顺序）：0~7 */
    private static final int MODULE_SLOT_END = 8;
    /** 武器槽的下标 */
    private static final int WEAPON_SLOT = 8;

    public RequiemWeaponTableScreen(RequiemWeaponTableMenu menu, Inventory playerInventory, Component title) {
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
        // ⭐ 配色与阴影都跟着主题走：原先是硬编码 0xFFBBBBBB 且不带阴影，
        //    压在全息扫描线上会糊掉。定位公式不动 —— 它依赖 imageHeight，
        //    而 imageHeight 属于本次的改动禁区。
        String hint = "[TAB] " + Component.translatable("kuvalich.codex.open_hint").getString();
        int hw = this.font.width(hint);
        int hx = (this.width - hw) / 2;
        int hy = (this.height + this.imageHeight) / 2 + 4;
        guiGraphics.drawString(this.font, hint, hx, hy, CodexTheme.ASH, true);
    }

    /**
     * 全自绘背景，不再使用任何贴图
     *
     * <p>原先这里是两次 blit：整张背景 + 一条从 {@code (u=18, v=imageHeight)} 取的
     * 152×18 条带。第二条的 v 正好落在贴图可见区的下边界，再往下是透明像素 ——
     * 也就是说它什么都没画出来，是历史遗留的空转调用，这次一并去掉。</p>
     *
     * <p>槽位凹槽是<b>遍历</b> {@code menu.slots} 画出来的，一个坐标都不写死：
     * 那些坐标在 Menu 的 addSlot 里，动了会让拖拽和 Shift 快捷移动错位。</p>
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        long now = Util.getMillis();

        ContainerChrome.panel(guiGraphics, left, top, this.imageWidth, this.imageHeight);

        // 模组区与背包区分开：上半是改装台，下半是玩家自己的东西
        ContainerChrome.zone(guiGraphics, left + 8, top + 6, this.imageWidth - 16, 78);

        // ⭐ 8 个模组格汇聚到武器格的走线。
        //    原贴图上这是一组金色电路线，表达「模组给武器供能」——
        //    是这张贴图上唯一有信息量的图形，丢了这个界面就只剩一堆孤立的格子。
        //    这里用直角折线复刻并让它动起来：静态的线只是装饰，会流动的才读得出方向。
        int limit = this.menu.getModuleLimit();
        int[] ox = new int[MODULE_SLOT_END];
        int[] oy = new int[MODULE_SLOT_END];
        for (int i = 0; i < MODULE_SLOT_END; i++) {
            var slot = this.menu.slots.get(i);
            ox[i] = left + slot.x + 8;
            oy[i] = top + slot.y + ContainerChrome.SLOT;
        }
        var weaponSlot = this.menu.slots.get(WEAPON_SLOT);
        ContainerChrome.busRoute(guiGraphics, ox, oy,
                top + 60, left + weaponSlot.x + 8, top + weaponSlot.y - 1,
                CodexTheme.TECH, now);

        // 槽位凹槽：超出 moduleLimit 的模组格画成锁定态。
        // 改造前这些格子和正常空槽长得一模一样，玩家唯一的反馈是「放不进去」这个静默拒绝。
        ContainerChrome.slots(guiGraphics, this.menu, left, top, (slot, index) -> {
            if (index < MODULE_SLOT_END) {
                return index < limit ? ContainerChrome.SlotKind.INPUT : ContainerChrome.SlotKind.LOCKED;
            }
            if (index == WEAPON_SLOT) return ContainerChrome.SlotKind.SUBJECT;
            return ContainerChrome.SlotKind.PLAYER;
        });
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
