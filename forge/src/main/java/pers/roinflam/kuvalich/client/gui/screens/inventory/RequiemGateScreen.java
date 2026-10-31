package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.client.KuvaKeyMappings;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.MurmurTriangle;
import pers.roinflam.kuvalich.client.lich.LichArchiveKeys;
import pers.roinflam.kuvalich.client.sound.KuvaSoundInstance;
import pers.roinflam.kuvalich.client.sound.KuvaSoundPlayer;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichSounds;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.packet.RequiemGateFillPacket;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.RequiemGateMenu;

/**
 * 灭骸之扉界面（放安魂卡的方块界面）
 *
 * <ul>
 *   <li>上排三个卡槽放安魂卡，每个槽下面三颗小红点是这张卡还能用几次（亮的带一圈暗红晕，用掉的压暗）；</li>
 *   <li>中间是三枚安魂密语排成的三角（和玄骸档案同一个部件）：揭示了的画这张卡的符号线描，
 *       正在解的那枚菱晶外圈和液面就是这一阶段的解密进度；三枚每 3 秒随机两枚换位，提示排列不代表顺序；</li>
 *   <li>右下角「[K] 玄骸档案」小字，点了（或者按档案键）关掉这个界面、打开档案；</li>
 *   <li>开界面放一声开门，再垫一层很轻的循环底噪，400 ms 淡入，关界面 300 ms 淡出；</li>
 *   <li>创造模式下按 [TAB] 一键补上正确答案卡（服务端会再核一次创造模式）。</li>
 * </ul>
 *
 * <p>数据走 {@link RequiemGateMenu} 同步的谜语三格、阶段和进度；阈值按同一套配置在客户端算。</p>
 */
@OnlyIn(Dist.CLIENT)
public class RequiemGateScreen extends AbstractContainerScreen<RequiemGateMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_gate.png"
    );

    /** 三角：三枚平均点相对界面左上角的位置、符号尺寸 */
    private static final float TRIANGLE_X = 88.0F;
    private static final float TRIANGLE_Y = 52.0F;
    private static final float SYMBOL_SIZE = 16.0F;

    /** 三个卡槽的左上角 x、y（和菜单里的槽位一致），次数点所在的那一行 */
    private static final int[] CARD_SLOT_X = {26, 80, 134};
    private static final int DOTS_Y = 29;
    private static final int DOT_STEP = 4;
    private static final int DOT_COUNT = 3;

    /** 「[K] 玄骸档案」：右上角对齐的位置、字号 */
    private static final int LINK_RIGHT = 164;
    private static final int LINK_TOP = 68;
    private static final float LINK_SCALE = 0.75F;

    /** 循环底噪的音量和淡入、淡出 */
    private static final float AMBIENCE_VOLUME = 0.6F;
    private static final int AMBIENCE_FADE_IN_MS = 400;
    private static final int AMBIENCE_FADE_OUT_MS = 300;

    private final MurmurTriangle triangle = new MurmurTriangle(SYMBOL_SIZE);
    private final int[] riddleBuffer = new int[3];
    /** 谜语卡的物品缓存（悬停时显示卡的说明），只在卡 id 变了时重建 */
    private final ItemStack[] riddleCards = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final int[] riddleCardIds = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

    private KuvaSoundInstance ambience;
    private boolean opened;

    private Component linkText = Component.empty();
    private float linkX0;
    private float linkY0;
    private float linkX1;
    private float linkY1;

    // ==================== 调试（开发环境的截图命令用） ====================

    private static boolean debugOverride;
    private static final int[] DEBUG_RIDDLES = {-1, -1, -1};
    private static float debugProgress;

    public RequiemGateScreen(RequiemGateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // 改窗口大小时 init 会再调一次，声音只在第一次打开时放
        if (!opened) {
            opened = true;
            KuvaSoundPlayer.play(KuvaSoundPlayer.ui(KuvaLichSounds.BLOCK_REQUIEM_GATE_OPEN.get(), 1.0F, 1.0F));
            ambience = KuvaSoundPlayer.play(KuvaSoundPlayer.uiLoop(KuvaLichSounds.BLOCK_REQUIEM_GATE_AMBIENCE.get(),
                    AMBIENCE_VOLUME, AMBIENCE_FADE_IN_MS));
        }
        linkText = Component.translatable("kuvalich.archive.open_hint", KuvaKeyMappings.ARCHIVE.getTranslatedKeyMessage());
    }

    @Override
    public void removed() {
        super.removed();
        if (ambience != null) {
            ambience.fadeOut(AMBIENCE_FADE_OUT_MS);
            ambience = null;
        }
    }

    /** 不画标题和「物品栏」字样 */
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 仅创造模式：底部显示 [TAB] 一键补全答案提示
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) {
            String hint = "[TAB] " + Component.translatable("kuvalich.gate.fill_hint").getString();
            int hintWidth = this.font.width(hint);
            int hintX = (this.width - hintWidth) / 2;
            int hintY = (this.height + this.imageHeight) / 2 + 4;
            guiGraphics.drawString(this.font, hint, hintX, hintY, 0xFFBBBBBB, false);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
        // 悬停在已揭示的密语上：显示那张卡的说明
        if (this.hoveredSlot == null && this.menu.getCarried().isEmpty()) {
            Integer slot = triangle.hoveredSlot(mouseX, mouseY);
            if (slot != null && riddleBuffer[slot] >= 0 && !riddleCards[slot].isEmpty()) {
                guiGraphics.renderTooltip(this.font, riddleCards[slot], mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        long now = KuvaFxDirector.clockMs();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, this.imageWidth, this.imageHeight);

        triangle.update(readRiddles(), stageProgress(), now);
        triangle.render(guiGraphics, leftPos + TRIANGLE_X, topPos + TRIANGLE_Y, now);
        drawUseDots(guiGraphics);
        drawArchiveLink(guiGraphics, mouseX, mouseY);
    }

    // ==================== 三角的数据 ====================

    private int[] readRiddles() {
        if (debugOverride) {
            System.arraycopy(DEBUG_RIDDLES, 0, riddleBuffer, 0, 3);
        } else {
            riddleBuffer[0] = menu.getOneRiddle();
            riddleBuffer[1] = menu.getTwoRiddle();
            riddleBuffer[2] = menu.getThreeRiddle();
        }
        for (int i = 0; i < 3; i++) {
            if (riddleBuffer[i] != riddleCardIds[i]) {
                riddleCardIds[i] = riddleBuffer[i];
                riddleCards[i] = riddleBuffer[i] < 0 ? ItemStack.EMPTY
                        : new ItemStack(AbstractRequiemCard.getCard(riddleBuffer[i]));
            }
        }
        return riddleBuffer;
    }

    /** 这一阶段的进度 0~1；三条都揭示了算满 */
    private float stageProgress() {
        if (debugOverride) {
            return debugProgress;
        }
        int level = menu.getLevel();
        if (level >= 3) {
            return 1.0F;
        }
        int threshold = getThresholdForLevel(level);
        if (threshold <= 0) {
            return 0.0F;
        }
        return Mth.clamp(menu.getSchedule() / (float) threshold, 0.0F, 1.0F);
    }

    /**
     * 指定阶段的解密阈值。读的是客户端本地的配置值，实际升阶在服务端判定；
     * 两边配置不一致时这里的比例会和服务端对不上
     *
     * @param level 当前阶段（0~2）
     * @return 该阶段所需总点数，越界返回 0
     */
    private int getThresholdForLevel(int level) {
        switch (level) {
            case 0:
                return ModConfig.KUVA_LICH.firstStage.get();
            case 1:
                return ModConfig.KUVA_LICH.secondStage.get();
            case 2:
                return ModConfig.KUVA_LICH.thirdStage.get();
            default:
                return 0;
        }
    }

    // ==================== 次数点 ====================

    /** 每个卡槽下面三颗 2×2 的点：还能用的亮红带一圈暗红晕，用掉的压暗；空槽不画 */
    private void drawUseDots(GuiGraphics g) {
        GuiShapes.begin(g);
        for (int i = 0; i < 3; i++) {
            ItemStack card = menu.getSlot(i).getItem();
            if (card.isEmpty()) {
                continue;
            }
            int left = usesLeft(card);
            float cx = leftPos + CARD_SLOT_X[i] + 8.0F;
            float y0 = topPos + DOTS_Y;
            for (int j = 0; j < DOT_COUNT; j++) {
                float x = cx + (j - 1) * DOT_STEP;
                if (j < left) {
                    GuiShapes.rect(g, x - 2.0F, y0 - 1.0F, x + 2.0F, y0 + 3.0F, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.6F));
                    GuiShapes.rect(g, x - 1.0F, y0, x + 1.0F, y0 + 2.0F, GuiShapes.argb(FxPalette.KUVA, 1.0F));
                } else {
                    GuiShapes.rect(g, x - 1.0F, y0, x + 1.0F, y0 + 2.0F, GuiShapes.argb(FxPalette.KUVA_DEEP, 0.55F));
                }
            }
        }
        GuiShapes.end();
    }

    /** 卡还能用几次，按三颗点折算 */
    private static int usesLeft(ItemStack card) {
        if (!card.isDamageableItem()) {
            return DOT_COUNT;
        }
        int max = card.getMaxDamage();
        int remain = max - card.getDamageValue();
        if (max == DOT_COUNT) {
            return Mth.clamp(remain, 0, DOT_COUNT);
        }
        return Mth.clamp(Mth.ceil(remain * DOT_COUNT / (float) max), 0, DOT_COUNT);
    }

    // ==================== 玄骸档案入口 ====================

    private void drawArchiveLink(GuiGraphics g, int mouseX, int mouseY) {
        if (KuvaKeyMappings.ARCHIVE.isUnbound()) {
            linkX1 = linkX0;
            return;
        }
        float w = this.font.width(linkText) * LINK_SCALE;
        linkX1 = leftPos + LINK_RIGHT;
        linkX0 = linkX1 - w;
        linkY0 = topPos + LINK_TOP;
        linkY1 = linkY0 + this.font.lineHeight * LINK_SCALE;
        boolean hover = overLink(mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(linkX0, linkY0, 0.0F);
        g.pose().scale(LINK_SCALE, LINK_SCALE, 1.0F);
        g.drawString(this.font, linkText, 0, 0, 0xFF000000 | (hover ? FxPalette.BONE : FxPalette.ASH), false);
        g.pose().popPose();
    }

    private boolean overLink(double mouseX, double mouseY) {
        return linkX1 > linkX0 && mouseX >= linkX0 && mouseX < linkX1 && mouseY >= linkY0 - 1.0F && mouseY < linkY1 + 1.0F;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && overLink(mouseX, mouseY)) {
            openArchive();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 关掉灭骸之扉、打开玄骸档案。先关界面（顺带通知服务端关容器），再走档案键同一个入口，
     * 能不能开（处决中不能开之类）的规矩只在 {@link LichArchiveKeys#open} 一处
     */
    private void openArchive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KuvaKeyMappings.ARCHIVE.isUnbound()) {
            return;
        }
        mc.player.closeContainer();
        LichArchiveKeys.open(mc);
    }

    /**
     * 创造模式下按 TAB 请求服务端一键补全正确答案卡（服务端收到 {@link RequiemGateFillPacket} 后会再核一次创造模式）；
     * 按档案键打开玄骸档案；其余交给父类（背包键关界面等）
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.isCreative()) {
                NetworkRegistryHandler.getChannel().sendToServer(new RequiemGateFillPacket());
                return true;
            }
        }
        if (KuvaKeyMappings.ARCHIVE.matches(keyCode, scanCode)) {
            openArchive();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== 调试入口 ====================

    /**
     * 让界面里的三角改画给定的数据（不碰服务端），截图用
     *
     * @param riddles  三格卡 id，−1 为未揭示
     * @param progress 这一阶段的进度 0~1
     */
    public static void debugOverride(int[] riddles, float progress) {
        for (int i = 0; i < 3; i++) {
            DEBUG_RIDDLES[i] = riddles != null && i < riddles.length ? riddles[i] : -1;
        }
        debugProgress = Mth.clamp(progress, 0.0F, 1.0F);
        debugOverride = true;
    }

    /** 恢复读菜单里的真实数据 */
    public static void debugClear() {
        debugOverride = false;
    }
}
