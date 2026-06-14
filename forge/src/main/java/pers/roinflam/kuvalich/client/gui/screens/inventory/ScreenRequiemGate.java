package pers.roinflam.kuvalich.client.gui.screens.inventory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.message.RequiemGateFillPacket;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.world.inventory.MenuRequiemGate;

/**
 * 灭骸之扉GUI（1.20.1版本）
 * Requiem Gate Screen (1.20.1 version)
 *
 * <p>本次改动：
 * <ol>
 *   <li>进度条宽度钳制：修复升阶瞬间/同步延迟时 schedule&gt;=threshold 导致填充宽度溢出纹理区域的问题。</li>
 *   <li>进度条过渡动画：用指数平滑（帧率无关）让填充宽度平滑逼近目标值。开界面时从0填充，
 *       之后无论目标值小步增、一次性大涨还是跳很多，都用同一套平滑逻辑补间，不瞬跳。</li>
 *   <li>揭示成功提示：检测到 level（解密阶段）递增时，在进度条上叠加暖金色双脉冲衰减闪光。</li>
 *   <li>谜语槽位洗牌动画：已揭示卡片在其所占槽位间持续循环换位（2张交换、3张轮转），
 *       交叉时上下错开弧线避免重叠；每个已揭示槽位带呼吸脉冲边框。本次放慢了换位速度。</li>
 *   <li>⭐ 创造模式底部 [TAB] 一键补全提示 + 按键触发（发送 RequiemGateFillPacket，
 *       服务端校验创造模式后填入正确答案卡片）。</li>
 * </ol></p>
 */
@OnlyIn(Dist.CLIENT)
public class ScreenRequiemGate extends AbstractContainerScreen<MenuRequiemGate> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/container/requiem_gate.png"
    );

    private static final int PROGRESS_X_OFFSET = 12;
    private static final int PROGRESS_Y_OFFSET = 30;
    private static final int PROGRESS_WIDTH = 152;
    private static final int PROGRESS_HEIGHT = 18;

    private static final int RIDDLE_Y_OFFSET = 56;
    private static final int RIDDLE_SIZE = 16;
    private static final int FIRST_RIDDLE_X_OFFSET = 26;
    private static final int SECOND_RIDDLE_X_OFFSET = 80;
    private static final int THIRD_RIDDLE_X_OFFSET = 134;

    // ==================== 进度条过渡动画参数 ====================

    /** 进度条填充动画固定时长（毫秒）：开界面回放(0→当前)及界面内进度变化均用此固定时长，模拟原版进度条的缓慢手感 */
    private static final long PROGRESS_FILL_ANIM_DURATION_MS = 3000L;

    // ==================== 进度条填充染色（奥罗金金色，可调；设为 1,1,1 即原色） ====================

    /** 填充染色 R 分量 */
    private static final float FILL_TINT_R = 1.00f;
    /** 填充染色 G 分量 */
    private static final float FILL_TINT_G = 0.82f;
    /** 填充染色 B 分量 */
    private static final float FILL_TINT_B = 0.40f;

    // ==================== 揭示成功闪光参数 ====================

    /** 闪光总时长（毫秒），与顶部HUD统一 */
    private static final long REVEAL_FLASH_MS = 900L;

    /** 闪光颜色RGB（不含alpha），暖金 */
    private static final int REVEAL_FLASH_RGB = 0xFFE8A0;

    /** 闪光脉冲周期数（衰减包络内的完整正弦周期数） */
    private static final double REVEAL_FLASH_PULSE_CYCLES = 2.0;

    /** 色洗峰值透明度（柔和发光，非实心色块） */
    private static final int REVEAL_FLASH_WASH_ALPHA_MAX = 90;

    /** 扩散光环最大外扩像素 */
    private static final int REVEAL_FLASH_RING_OFFSET = 6;

    /** 扩散光环峰值透明度 */
    private static final int REVEAL_FLASH_RING_ALPHA_MAX = 150;

    // ==================== 谜语卡片洗牌动画参数 ====================

    /** 三个谜语槽位的X偏移（索引0/1/2对应一/二/三号槽） */
    private static final int[] RIDDLE_X_OFFSETS = {
            FIRST_RIDDLE_X_OFFSET, SECOND_RIDDLE_X_OFFSET, THIRD_RIDDLE_X_OFFSET
    };

    /** 单步洗牌循环时长（毫秒）：停留 + 平滑移动到下一槽位的总时长（已放慢） */
    private static final long SHUFFLE_CYCLE_MS = 3400L;

    /** 停留时长占比（0~1）：前段停在当前槽位，后段 smoothstep 平滑滑向下一槽位 */
    private static final double SHUFFLE_HOLD_FRAC = 0.6;

    /** 卡片换位时的垂直弧线高度（像素），避免两张卡片交叉时完全重叠 */
    private static final int SHUFFLE_ARC_H = 4;

    /** 槽位脉冲边框呼吸周期（毫秒），与洗牌循环独立 */
    private static final long FLASH_PERIOD_MS = 1300L;

    /** 脉冲边框最小透明度（0~255） */
    private static final int FLASH_ALPHA_MIN = 50;

    /** 脉冲边框最大透明度（0~255） */
    private static final int FLASH_ALPHA_MAX = 140;

    /** 脉冲颜色RGB（不含alpha），Kuva红 */
    private static final int FLASH_RGB = 0xFF5555;

    // ==================== 谜语卡片缓存（避免每帧new ItemStack） ====================

    /** 谜语卡片ItemStack缓存，仅在riddleId变化时重建 */
    private final ItemStack[] cachedRiddleCards = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    /** 上一次缓存对应的riddleId，用于判断是否需要重建 */
    private final int[] cachedRiddleIds = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

    /** 每帧复用的riddleId缓冲，避免重复分配 */
    private final int[] riddleIdBuffer = new int[3];

    /** 每帧复用的已揭示槽位索引缓冲 */
    private final int[] revealedSlotBuffer = new int[3];

    // ==================== 进度条/揭示动画运行时状态 ====================

    /** 当前渲染用填充宽度（像素），每帧由填充动画计算得出 */
    private float displayedProgressWidth = 0.0f;

    /** 上一帧观测到的解密阶段（level），用于检测阶段推进触发揭示闪光；<0 表示首帧 */
    private int lastLevel = -1;

    /** 揭示闪光起始时间戳（毫秒）；<0 表示当前无闪光 */
    private long revealFlashStartMs = -1L;

    /** 进度条填充动画起始宽度（像素） */
    private float fillAnimStart = 0.0f;

    /** 进度条填充动画目标宽度（像素） */
    private float fillAnimTarget = 0.0f;

    /** 进度条填充动画起始时间戳（毫秒，-1 表示未开始）；开界面为新实例，从此重新回放 0→当前 */
    private long fillAnimStartMs = -1L;

    public ScreenRequiemGate(MenuRequiemGate menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // ⭐ 仅创造模式：底部显示 [TAB] 一键补全答案提示（与军械库 TAB 提示同风格）
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) {
            String hint = "[TAB] " + Component.translatable("kuvalich.gate.fill_hint").getString();
            int hintWidth = this.font.width(hint);
            int hintX = (this.width - hintWidth) / 2;
            int hintY = (this.height + this.imageHeight) / 2 + 4;
            guiGraphics.drawString(this.font, hint, hintX, hintY, 0xFFBBBBBB, false);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * 监听按键：创造模式下按 TAB 请求服务端一键补全正确答案卡片。
     * <p>
     * 仅创造模式生效；非创造模式或非 TAB 键交还父类默认处理（如背包键关闭界面等）。
     * 服务端收到 {@link RequiemGateFillPacket} 后会再次校验创造模式，杜绝伪造包作弊。
     *
     * @param keyCode   按键码
     * @param scanCode  扫描码
     * @param modifiers 修饰键掩码
     * @return 是否已处理该按键
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
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 每帧推进动画状态（进度条补间 + 揭示检测）
        long now = Util.getMillis();
        updateProgressTween(now);
        updateRevealDetection(now);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);

        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, this.imageHeight);
        drawProgressBar(guiGraphics, left, top);
        drawRiddleCards(guiGraphics, left, top, mouseX, mouseY);
    }

    // ==================== 进度条动画状态更新 ====================

    /**
     * 推进进度条填充宽度的固定时长补间。
     * <p>
     * 帧率无关：以 {@link #PROGRESS_FILL_ANIM_DURATION_MS}（3 秒）为固定时长、缓入缓出地从起点过渡到目标。
     * 开界面为新实例（{@code fillAnimStartMs<0}），从 0 回放填充到当前进度（恢复"进度积累"开界面动画）；
     * 界面打开期间进度实时变化时，则以当前显示值为起点重新开始一段固定时长动画。
     *
     * @param now 当前绝对时间戳（毫秒）
     */
    private void updateProgressTween(long now) {
        int target = calculateProgressWidth(menu.getLevel(), menu.getSchedule());

        if (fillAnimStartMs < 0L) {
            // 开界面首帧：从 0 回放填充到当前进度
            fillAnimStart = 0.0f;
            fillAnimTarget = target;
            fillAnimStartMs = now;
        } else if (Math.abs(target - fillAnimTarget) > 0.5f) {
            // 界面打开期间进度实时变化 → 以当前显示值为起点，重新开始一段固定时长动画
            fillAnimStart = displayedProgressWidth;
            fillAnimTarget = target;
            fillAnimStartMs = now;
        }

        // 固定时长进度（钳制到 [0,1]）+ smoothstep 缓入缓出
        double raw = (now - fillAnimStartMs) / (double) PROGRESS_FILL_ANIM_DURATION_MS;
        double t = raw < 0.0 ? 0.0 : (raw > 1.0 ? 1.0 : raw);
        double eased = t * t * (3.0 - 2.0 * t);
        displayedProgressWidth = (float) (fillAnimStart + (fillAnimTarget - fillAnimStart) * eased);

        if (displayedProgressWidth < 0.0f) {
            displayedProgressWidth = 0.0f;
        } else if (displayedProgressWidth > PROGRESS_WIDTH) {
            displayedProgressWidth = PROGRESS_WIDTH;
        }
    }

    /**
     * 检测解密阶段（level）是否递增，递增即触发一次揭示闪光。
     * <p>
     * 首次观测仅记录基准、不触发，避免开界面时对已有进度误报。
     *
     * @param now 当前绝对时间戳（毫秒）
     */
    private void updateRevealDetection(long now) {
        int level = menu.getLevel();

        if (lastLevel < 0) {
            // 首帧仅记录基准，不触发提示
            lastLevel = level;
            return;
        }

        if (level > lastLevel) {
            // 阶段推进 = 揭示新线索
            revealFlashStartMs = now;
        }
        lastLevel = level;
    }

    // ==================== 进度条渲染 ====================

    private void drawProgressBar(GuiGraphics guiGraphics, int left, int top) {
        int progressX = left + PROGRESS_X_OFFSET;
        int progressY = top + PROGRESS_Y_OFFSET;

        // 进度条背景轨道
        guiGraphics.blit(
                TEXTURE,
                progressX, progressY,
                PROGRESS_HEIGHT, this.imageHeight,
                PROGRESS_WIDTH, PROGRESS_HEIGHT
        );

        // 使用平滑后的填充宽度（再次钳制确保安全）
        int fillWidth = Math.round(displayedProgressWidth);
        if (fillWidth < 0) {
            fillWidth = 0;
        } else if (fillWidth > PROGRESS_WIDTH) {
            fillWidth = PROGRESS_WIDTH;
        }

        if (fillWidth > 0) {
            // 填充用奥罗金金色染色（轨道保持原色）
            RenderSystem.setShaderColor(FILL_TINT_R, FILL_TINT_G, FILL_TINT_B, 1.0F);
            guiGraphics.blit(
                    TEXTURE,
                    progressX, progressY,
                    PROGRESS_HEIGHT, this.imageHeight + PROGRESS_HEIGHT,
                    fillWidth, PROGRESS_HEIGHT
            );
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        // 揭示成功闪光叠加
        drawRevealFlash(guiGraphics, progressX, progressY);
    }

    /**
     * 绘制揭示成功闪光：柔和色洗（暖金双脉冲 × 衰减包络）+ 由进度条边缘向外扩散并淡出的光环。
     * <p>与顶部HUD视觉统一；此处不加整体缩放弹跳，避免影响谜题卡洗牌动画与鼠标悬停判定。
     *
     * @param guiGraphics 图形上下文
     * @param progressX   进度条左上角 X
     * @param progressY   进度条左上角 Y
     */
    private void drawRevealFlash(GuiGraphics guiGraphics, int progressX, int progressY) {
        if (revealFlashStartMs < 0L) {
            return;
        }

        long elapsed = Util.getMillis() - revealFlashStartMs;
        if (elapsed >= REVEAL_FLASH_MS) {
            revealFlashStartMs = -1L;
            return;
        }

        double t = (double) elapsed / REVEAL_FLASH_MS;
        double env = 1.0 - t;

        // 柔和色洗（盖在进度条上，低透明度发光感）
        double pulse = (Math.sin(t * Math.PI * 2.0 * REVEAL_FLASH_PULSE_CYCLES) + 1.0) / 2.0;
        int washAlpha = (int) (REVEAL_FLASH_WASH_ALPHA_MAX * env * pulse);
        if (washAlpha > 1) {
            int washColor = (washAlpha << 24) | REVEAL_FLASH_RGB;
            guiGraphics.fill(progressX, progressY, progressX + PROGRESS_WIDTH, progressY + PROGRESS_HEIGHT, washColor);
        }

        // 扩散光环（由进度条边缘向外扩张，env² 更快淡出）
        int off = (int) Math.round(t * REVEAL_FLASH_RING_OFFSET) + 1;
        int ringAlpha = (int) (REVEAL_FLASH_RING_ALPHA_MAX * env * env);
        if (ringAlpha > 1) {
            int ringColor = (ringAlpha << 24) | REVEAL_FLASH_RGB;
            int left = progressX - off;
            int right = progressX + PROGRESS_WIDTH + off;
            int top = progressY - off;
            int bottom = progressY + PROGRESS_HEIGHT + off;
            guiGraphics.fill(left, top, right, top + 1, ringColor);
            guiGraphics.fill(left, bottom - 1, right, bottom, ringColor);
            guiGraphics.fill(left, top, left + 1, bottom, ringColor);
            guiGraphics.fill(right - 1, top, right, bottom, ringColor);
        }
    }

    /**
     * 计算进度条填充的目标宽度。
     * <p>
     * 对 schedule/threshold 比例做 [0,1] 钳制：正常升阶会扣减 schedule，
     * 但升阶瞬间或服务端→客户端同步延迟的那一帧可能出现 schedule>=threshold，
     * 不钳制会使填充宽度超过 {@link #PROGRESS_WIDTH}、blit 画到纹理区域外。
     *
     * @param level    当前解密阶段（0~3）
     * @param schedule 当前阶段已累积进度
     * @return 目标填充像素宽度，范围 [0, PROGRESS_WIDTH]
     */
    private int calculateProgressWidth(int level, int schedule) {
        if (level >= 3) {
            return PROGRESS_WIDTH;
        }

        int threshold = getThresholdForLevel(level);
        if (threshold <= 0) {
            return 0;
        }

        double ratio = (double) schedule / threshold;
        if (ratio < 0.0) {
            ratio = 0.0;
        } else if (ratio > 1.0) {
            ratio = 1.0;
        }

        return (int) (PROGRESS_WIDTH * ratio);
    }

    /**
     * 获取指定阶段的解密阈值。
     * <p>
     * 注意：此处读取的是客户端本地的 ModConfig 值，实际升阶判定在服务端。
     * 若 firstStage/secondStage/thirdStage 不是 SERVER 同步配置，
     * 两端数值不一致时进度条显示比例会与服务端真实进度对不上。
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

    // ==================== 谜语卡片 ====================

    /**
     * 绘制三个谜语槽位。
     * <p>
     * 未揭示槽位绘制静止的「神秘」覆盖；已揭示卡片在揭示≥2张时播放洗牌动画，
     * 仅1张时静止绘制。
     *
     * @param guiGraphics 图形上下文
     * @param left        GUI 左上角 X
     * @param top         GUI 左上角 Y
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     */
    private void drawRiddleCards(GuiGraphics guiGraphics, int left, int top, int mouseX, int mouseY) {
        int riddleY = top + RIDDLE_Y_OFFSET;

        // 读取三个谜语槽位卡片ID（-1=未揭示）到复用缓冲
        riddleIdBuffer[0] = menu.getOneRiddle();
        riddleIdBuffer[1] = menu.getTwoRiddle();
        riddleIdBuffer[2] = menu.getThreeRiddle();

        // 刷新卡片缓存（仅ID变化时重建ItemStack）
        refreshRiddleCardCache();

        // 收集已揭示的槽位索引
        int revealedCount = 0;
        for (int i = 0; i < 3; i++) {
            if (riddleIdBuffer[i] != -1) {
                revealedSlotBuffer[revealedCount++] = i;
            }
        }

        // 未揭示槽位绘制「神秘」覆盖（静止，不参与洗牌）
        for (int i = 0; i < 3; i++) {
            if (riddleIdBuffer[i] == -1) {
                int x = left + RIDDLE_X_OFFSETS[i];
                guiGraphics.blit(TEXTURE, x, riddleY, 0, this.imageHeight, RIDDLE_SIZE, RIDDLE_SIZE);
            }
        }

        if (revealedCount >= 2) {
            // 两张及以上：洗牌动画 + 槽位脉冲，提示顺序不固定
            drawShufflingRiddles(guiGraphics, left, riddleY, revealedCount, mouseX, mouseY);
        } else if (revealedCount == 1) {
            // 仅一张：静止绘制，无洗牌
            int idSlot = revealedSlotBuffer[0];
            int x = left + RIDDLE_X_OFFSETS[idSlot];
            drawCard(guiGraphics, cachedRiddleCards[idSlot], x, riddleY);
            if (isMouseOver(x, riddleY, RIDDLE_SIZE, RIDDLE_SIZE, mouseX, mouseY)) {
                guiGraphics.renderTooltip(this.font, cachedRiddleCards[idSlot], mouseX, mouseY);
            }
        }
    }

    /**
     * 刷新谜语卡片ItemStack缓存，仅在对应槽位ID变化时重建，避免每帧 new。
     */
    private void refreshRiddleCardCache() {
        for (int i = 0; i < 3; i++) {
            if (riddleIdBuffer[i] != cachedRiddleIds[i]) {
                cachedRiddleIds[i] = riddleIdBuffer[i];
                cachedRiddleCards[i] = (riddleIdBuffer[i] == -1)
                        ? ItemStack.EMPTY
                        : new ItemStack(AbstractRequiemCard.getCard(riddleIdBuffer[i]));
            }
        }
    }

    /**
     * 绘制已揭示卡片的洗牌动画。
     * <p>
     * 卡片身份固定，但在其所占的 k 个已揭示位置间循环换位：
     * 逻辑步 step 时，第 j 张卡片位于第 (j+step)%k 个已揭示位置，平滑移向 (j+step+1)%k。
     * 每个已揭示「家位置」绘制呼吸脉冲边框，卡片移走时仍发光，避免空洞。
     *
     * @param guiGraphics 图形上下文
     * @param left        GUI 左上角 X
     * @param riddleY     谜语行 Y
     * @param k           已揭示卡片数量（>=2）
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     */
    private void drawShufflingRiddles(GuiGraphics guiGraphics, int left, int riddleY,
                                      int k, int mouseX, int mouseY) {
        long now = Util.getMillis();

        // 槽位脉冲边框（连续呼吸）：在每个已揭示「家位置」绘制，实现「闪烁三个槽位」效果
        double flashPulse = (Math.sin(now * Math.PI * 2.0 / FLASH_PERIOD_MS) + 1.0) / 2.0;
        int borderAlpha = FLASH_ALPHA_MIN + (int) ((FLASH_ALPHA_MAX - FLASH_ALPHA_MIN) * flashPulse);
        for (int j = 0; j < k; j++) {
            int homeX = left + RIDDLE_X_OFFSETS[revealedSlotBuffer[j]];
            drawPulseBorder(guiGraphics, homeX, riddleY, borderAlpha);
        }

        // 洗牌进度：单步循环内前 HOLD_FRAC 停留，后段 smoothstep 平滑移动
        long step = now / SHUFFLE_CYCLE_MS;
        double phase = (now % SHUFFLE_CYCLE_MS) / (double) SHUFFLE_CYCLE_MS;
        double move = phase < SHUFFLE_HOLD_FRAC ? 0.0
                : (phase - SHUFFLE_HOLD_FRAC) / (1.0 - SHUFFLE_HOLD_FRAC);
        double eased = move * move * (3.0 - 2.0 * move);

        // 悬停命中记录：绘制完所有卡片后再渲染tooltip，避免被后绘制卡片遮挡
        int hoverIdSlot = -1;
        int hoverMouseX = 0;
        int hoverMouseY = 0;

        for (int j = 0; j < k; j++) {
            // 卡片 j（身份固定）当前所在位置与目标位置（均为已揭示位置）
            int fromPos = (int) ((j + step) % k);
            int toPos = (int) ((j + step + 1) % k);
            int fromX = left + RIDDLE_X_OFFSETS[revealedSlotBuffer[fromPos]];
            int toX = left + RIDDLE_X_OFFSETS[revealedSlotBuffer[toPos]];
            int drawX = (int) Math.round(fromX + (toX - fromX) * eased);

            // 垂直弧线：相邻卡片方向相反，交叉时上下错开避免重叠
            int arc = (int) Math.round(SHUFFLE_ARC_H * Math.sin(Math.PI * eased));
            int drawY = riddleY + (((j & 1) == 0) ? -arc : arc);

            // 卡片身份对应的逻辑槽（取卡片ItemStack），与其当前所在位置无关
            int idSlot = revealedSlotBuffer[j];
            drawCard(guiGraphics, cachedRiddleCards[idSlot], drawX, drawY);

            if (isMouseOver(drawX, drawY, RIDDLE_SIZE, RIDDLE_SIZE, mouseX, mouseY)) {
                hoverIdSlot = idSlot;
                hoverMouseX = mouseX;
                hoverMouseY = mouseY;
            }
        }

        if (hoverIdSlot != -1) {
            guiGraphics.renderTooltip(this.font, cachedRiddleCards[hoverIdSlot], hoverMouseX, hoverMouseY);
        }
    }

    /**
     * 在指定槽位绘制呼吸脉冲：1像素边框 + 柔和内填充。
     * <p>
     * 内填充透明度更低，卡片在位时基本被盖住，卡片移走时透出脉冲，
     * 边框始终可见，实现「持续闪烁」的槽位提示。
     *
     * @param guiGraphics 图形上下文
     * @param x           槽位左上角 X
     * @param y           槽位左上角 Y
     * @param alpha       当前脉冲透明度（0~255）
     */
    private void drawPulseBorder(GuiGraphics guiGraphics, int x, int y, int alpha) {
        int borderColor = (alpha << 24) | FLASH_RGB;
        // 1像素脉冲边框（上/下/左/右）
        guiGraphics.fill(x - 1, y - 1, x + RIDDLE_SIZE + 1, y, borderColor);
        guiGraphics.fill(x - 1, y + RIDDLE_SIZE, x + RIDDLE_SIZE + 1, y + RIDDLE_SIZE + 1, borderColor);
        guiGraphics.fill(x - 1, y, x, y + RIDDLE_SIZE, borderColor);
        guiGraphics.fill(x + RIDDLE_SIZE, y, x + RIDDLE_SIZE + 1, y + RIDDLE_SIZE, borderColor);
        // 柔和内填充（更低透明度）
        int fillAlpha = alpha / 4;
        int fillColor = (fillAlpha << 24) | FLASH_RGB;
        guiGraphics.fill(x, y, x + RIDDLE_SIZE, y + RIDDLE_SIZE, fillColor);
    }

    /**
     * 判断鼠标是否在指定矩形区域内。
     *
     * @param x      区域左上角 X
     * @param y      区域左上角 Y
     * @param width  区域宽
     * @param height 区域高
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @return 是否命中
     */
    private boolean isMouseOver(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    /**
     * 绘制单张卡片图标及其装饰。
     *
     * @param guiGraphics 图形上下文
     * @param itemStack   卡片物品栈
     * @param x           绘制 X
     * @param y           绘制 Y
     */
    private void drawCard(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y) {
        if (itemStack.isEmpty()) {
            return;
        }

        guiGraphics.renderItem(itemStack, x, y);
        guiGraphics.renderItemDecorations(this.font, itemStack, x, y - 8);
    }
}
