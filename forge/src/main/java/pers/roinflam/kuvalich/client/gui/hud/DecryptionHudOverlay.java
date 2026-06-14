package pers.roinflam.kuvalich.client.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.network.message.DecryptionHudPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.lang.ref.WeakReference;

/**
 * 安魂解密进度顶部HUD浮层（客户端专用）
 * Decryption progress top-HUD overlay (Client-only)
 *
 * <p>无需打开灭骸之扉界面，打怪获得解密进度时屏幕顶部弹出进度条：
 * <ul>
 *   <li>滑入 + 填充补间（指数平滑，帧率无关）+ 数秒后淡出。</li>
 *   <li>填充贴图复用灭骸之扉的 requiem_gate.png（UV 与 ScreenRequiemGate 完全一致）。</li>
 *   <li>无论一次加很多、加一点还是连续多次加，都用同一套平滑补间，从旧值平滑过渡到新值。</li>
 *   <li>揭示新线索时金色脉冲闪光 + 音效；最终解密成功时更强的庆祝闪光 + 音效，并将条归零。</li>
 * </ul></p>
 *
 * <p>所有文案均来自语言文件（翻译键见下方 KEY_* 常量），§颜色码在渲染时生效；
 * 淡入淡出的 alpha 由代码统一控制（字体渲染器对带 §颜色 的文本同样套用传入 alpha）。
 * 解密成功标题在语言文件中使用 §4 深红。</p>
 *
 * <p>数据来源全部由服务端 {@link DecryptionHudPacket} 下发；阈值随包传来，
 * 因此进度比例始终与服务端真实进度一致。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DecryptionHudOverlay {

    // ==================== 贴图（复用灭骸之扉进度条） ====================

    /** 进度条贴图（与 ScreenRequiemGate 同一张） */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Reference.MOD_ID, "textures/gui/container/requiem_gate.png");

    /** 进度条宽 / Bar width */
    private static final int BAR_W = 152;
    /** 进度条高 / Bar height */
    private static final int BAR_H = 18;
    /** 轨道贴图 U 偏移 / Track texture U（与 ScreenRequiemGate 一致：18） */
    private static final int TRACK_U = 18;
    /** 轨道贴图 V 偏移 / Track texture V（灭骸之扉 imageHeight = 166） */
    private static final int TRACK_V = 166;
    /** 填充贴图 U 偏移 / Fill texture U */
    private static final int FILL_U = 18;
    /** 填充贴图 V 偏移 / Fill texture V（166 + 18） */
    private static final int FILL_V = 166 + 18;

    // ==================== 布局 ====================

    /** 整个组件距屏幕顶部的边距（像素） */
    private static final int TOP_MARGIN = 6;
    /** 标题行与进度条之间的间距（像素） */
    private static final int TITLE_GAP = 11;
    /** 滑入时的起始上移距离（像素） */
    private static final int SLIDE_DISTANCE = 14;

    // ==================== 时间参数（毫秒） ====================

    /** 最后一次更新后保持显示的时长 */
    private static final long HOLD_MS = 4000L;
    /** 淡出时长 */
    private static final long FADE_MS = 600L;
    /** 滑入时长 */
    private static final long APPEAR_MS = 350L;
    /** 填充动画固定时长（毫秒）：无论增量多少都用此固定时长平滑过渡，模拟原版进度条的缓慢手感 */
    private static final long FILL_ANIM_DURATION_MS = 3000L;
    /** 揭示闪光时长 */
    private static final long REVEAL_FLASH_MS = 900L;
    /** 解密成功闪光时长 */
    private static final long COMPLETE_FLASH_MS = 1600L;

    // ==================== 闪光颜色与强度 ====================

    /** 揭示闪光颜色RGB（浅金） */
    private static final int REVEAL_RGB = 0xFFE8A0;
    /** 揭示色洗峰值透明度（柔和发光，非实心色块） */
    private static final int REVEAL_WASH_ALPHA_MAX = 90;
    /** 扩散光环最大外扩像素 */
    private static final int RING_MAX_OFFSET = 6;
    /** 扩散光环峰值透明度 */
    private static final int RING_ALPHA_MAX = 160;
    /** 揭示时整体缩放弹跳幅度 */
    private static final float REVEAL_POP_MAX = 0.045f;
    /** 标题底衬透明度（占整体alpha的比例） */
    private static final float TITLE_BG_ALPHA = 0.55f;
    /** 标题底衬水平内边距（像素） */
    private static final int TITLE_BG_PAD_X = 4;
    /** 标题状态切换时的交叉淡入淡出时长（毫秒） */
    private static final long TITLE_FADE_MS = 260L;

    // ==================== 进度条填充染色（奥罗金金色，可调；设为 1,1,1 即原色） ====================

    /** 填充染色 R 分量 */
    private static final float FILL_TINT_R = 1.00f;
    /** 填充染色 G 分量 */
    private static final float FILL_TINT_G = 0.82f;
    /** 填充染色 B 分量 */
    private static final float FILL_TINT_B = 0.40f;

    // ==================== 文本翻译键（文案与颜色均在语言文件维护） ====================

    /** 普通标题（带阶段占位符 %s）：语言文件值形如 "§6安魂密语解密 · 线索 %s/3"（奥罗金金色） */
    private static final String KEY_TITLE_NORMAL = "kuvalich.hud.decryption.title";
    /** 线索全部解锁、待挑战时的标题：建议 §e 亮金 */
    private static final String KEY_TITLE_READY = "kuvalich.hud.decryption.ready";
    /** 揭示新线索时的标题：建议 §e 亮金 */
    private static final String KEY_TITLE_REVEAL = "kuvalich.hud.decryption.reveal";
    /** 最终解密成功时的标题：使用 §4 深红（Kuva 红色终幕，亦可改 §6/§e 走纯金风格） */
    private static final String KEY_TITLE_COMPLETE = "kuvalich.hud.decryption.complete";
    /** 进度数字（两个占位符 %s / %s）：建议 §e 亮金 */
    private static final String KEY_NUMBERS_PROGRESS = "kuvalich.hud.decryption.progress";
    /** 线索全部揭示时进度条中央文案：建议 §e 亮金 */
    private static final String KEY_NUMBERS_ALL_REVEALED = "kuvalich.hud.decryption.all_revealed";

    // ==================== 运行时状态（仅客户端主线程访问，无需加锁） ====================

    private static int targetLevel;
    private static int targetProgress;
    private static int targetThreshold;
    private static float displayedFraction = 0.0f;
    private static long visibleUntilMs = 0L;
    private static long appearStartMs = 0L;
    private static long revealFlashStartMs = -1L;
    private static long completeFlashStartMs = -1L;
    private static boolean hasData = false;

    /** 填充动画起始比例 */
    private static float fillAnimStart = 0.0f;
    /** 填充动画目标比例 */
    private static float fillAnimTarget = 0.0f;
    /** 填充动画起始时间戳（毫秒，-1 表示未开始） */
    private static long fillAnimStartMs = -1L;

    /** 上一帧标题状态码（0普通/1就绪/2揭示/3成功，-1未初始化），用于检测状态切换触发交叉淡入 */
    private static int lastTitleState = -1;
    /** 上一帧绘制的标题组件，状态切换时作为淡出的旧标题 */
    private static Component lastTitleComponent = null;
    /** 交叉淡入中正在淡出的旧标题（null 表示当前无切换过渡） */
    private static Component titleFadeFrom = null;
    /** 标题交叉淡入起始时间戳（毫秒，-1 表示无过渡） */
    private static long titleFadeStartMs = -1L;

    /** 上次渲染所处世界（弱引用，避免阻止旧世界GC），用于检测切换世界后重置 */
    private static WeakReference<Level> lastLevelRef = null;

    private DecryptionHudOverlay() {
    }

    // ==================== 数据更新（由 DecryptionHudPacket 客户端处理调用） ====================

    /**
     * 接收服务端下发的解密进度更新。
     *
     * @param eventType 事件类型（见 DecryptionHudPacket 常量）
     * @param level     当前解密阶段
     * @param progress  当前阶段进度
     * @param threshold 当前阶段阈值（满级 -1）
     * @param added     本次增加量
     */
    public static void onPacket(int eventType, int level, int progress, int threshold, int added) {
        long now = Util.getMillis();
        boolean wasHidden = !hasData || now > visibleUntilMs + FADE_MS;

        targetLevel = level;
        targetProgress = progress;
        targetThreshold = threshold;
        hasData = true;
        visibleUntilMs = now + HOLD_MS;

        // 之前已隐藏 → 重新触发滑入
        if (wasHidden) {
            appearStartMs = now;
        }

        if (eventType == DecryptionHudPacket.EVENT_REVEAL) {
            revealFlashStartMs = now;
            playUiSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F);
        } else if (eventType == DecryptionHudPacket.EVENT_COMPLETE) {
            completeFlashStartMs = now;
            playUiSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F);
        }
    }

    // ==================== 注册 ====================

    /**
     * 注册为「最顶层」HUD 浮层
     *
     * @param event Forge HUD 浮层注册事件
     */
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("requiem_decryption_hud", DecryptionHudOverlay::render);
    }

    // ==================== 渲染 ====================

    /**
     * HUD 浮层渲染入口（每帧调用）
     *
     * @param gui         ForgeGui 实例
     * @param guiGraphics 图形上下文
     * @param partialTick 渲染插值
     * @param width       屏幕缩放宽
     * @param height      屏幕缩放高
     */
    public static void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();

        // 切换世界检测：进入新世界时重置，避免显示上一个世界的残留进度
        Level currentLevel = mc.level;
        if (lastLevelRef == null || lastLevelRef.get() != currentLevel) {
            lastLevelRef = new WeakReference<>(currentLevel);
            resetState();
        }

        if (currentLevel == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        if (!hasData) {
            return;
        }

        long now = Util.getMillis();
        // 完全隐藏后不渲染
        if (now > visibleUntilMs + FADE_MS) {
            return;
        }

        // 过期闪光及时清理，保证后续 pop / 闪光 / 标题状态一致
        if (revealFlashStartMs >= 0L && now - revealFlashStartMs >= REVEAL_FLASH_MS) {
            revealFlashStartMs = -1L;
        }
        if (completeFlashStartMs >= 0L && now - completeFlashStartMs >= COMPLETE_FLASH_MS) {
            completeFlashStartMs = -1L;
        }

        // 填充补间（固定时长动画）：目标变化时以当前显示值为起点，用固定 3 秒缓入缓出过渡到新目标，模拟原版进度条的缓慢手感
        double targetFraction = computeTargetFraction();
        if (fillAnimStartMs < 0L || Math.abs(targetFraction - fillAnimTarget) > 1.0e-4) {
            fillAnimStart = displayedFraction;
            fillAnimTarget = (float) targetFraction;
            fillAnimStartMs = now;
        }
        double animT = clamp01((now - fillAnimStartMs) / (double) FILL_ANIM_DURATION_MS);
        double animEased = animT * animT * (3.0 - 2.0 * animT);
        displayedFraction = (float) (fillAnimStart + (fillAnimTarget - fillAnimStart) * animEased);
        if (displayedFraction < 0.0f) {
            displayedFraction = 0.0f;
        } else if (displayedFraction > 1.0f) {
            displayedFraction = 1.0f;
        }

        // 滑入 + 淡出 → 整体 alpha 与 Y 偏移
        double appearT = clamp01((now - appearStartMs) / (double) APPEAR_MS);
        double appearEased = appearT * appearT * (3.0 - 2.0 * appearT);
        float alpha = (float) appearEased;
        int yOffset = (int) Math.round(-(1.0 - appearEased) * SLIDE_DISTANCE);
        if (now > visibleUntilMs) {
            double fadeT = clamp01((now - visibleUntilMs) / (double) FADE_MS);
            alpha *= (float) (1.0 - fadeT);
        }
        if (alpha <= 0.02f) {
            return;
        }

        int x = (width - BAR_W) / 2;
        int widgetTop = TOP_MARGIN + yOffset;
        int barY = widgetTop + TITLE_GAP;

        // 揭示/成功时整体轻微缩放弹跳（绕组件中心）
        float pop = computeFlashPop(now);
        guiGraphics.pose().pushPose();
        if (pop != 1.0f) {
            float cx = x + BAR_W / 2.0f;
            float cy = barY + BAR_H / 2.0f;
            guiGraphics.pose().translate(cx, cy, 0.0f);
            guiGraphics.pose().scale(pop, pop, 1.0f);
            guiGraphics.pose().translate(-cx, -cy, 0.0f);
        }

        renderBar(guiGraphics, x, barY, alpha);
        renderFlashes(guiGraphics, x, barY, alpha, now);
        renderText(mc.font, guiGraphics, x, widgetTop, barY, alpha, now);

        guiGraphics.pose().popPose();

        // 复原渲染状态，避免本浮层启用的 blend 泄漏到后续渲染
        RenderSystem.disableBlend();
    }

    /**
     * 计算当前帧整体缩放弹跳系数（揭示闪光前段快速弹起后回落到 1）。
     *
     * @param now 当前时间戳
     * @return 缩放系数（1.0 表示无缩放）
     */
    private static float computeFlashPop(long now) {
        // 仅揭示新线索做整体缩放弹跳；解密成功不做（此时进度增量为0，不应"爆"）
        return flashPop(revealFlashStartMs, REVEAL_FLASH_MS, REVEAL_POP_MAX, now);
    }

    /**
     * 单个闪光的缩放弹跳贡献：在前 1/3 时长内完成一次 0→峰值→0 的弹跳。
     *
     * @param start  闪光起始时间（&lt;0 表示未激活）
     * @param dur    闪光总时长
     * @param popMax 弹跳幅度峰值
     * @param now    当前时间戳
     * @return 缩放系数（1.0 表示该闪光无贡献）
     */
    private static float flashPop(long start, long dur, float popMax, long now) {
        if (start < 0L) {
            return 1.0f;
        }
        long elapsed = now - start;
        if (elapsed >= dur) {
            return 1.0f;
        }
        double t = (double) elapsed / dur;
        double bounce = Math.sin(Math.PI * Math.min(t * 3.0, 1.0));
        return (float) (1.0 + popMax * bounce * (1.0 - t));
    }

    /**
     * 绘制轨道 + 填充（通过 shader color 应用整体 alpha）
     */
    private static void renderBar(GuiGraphics guiGraphics, int x, int barY, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 轨道（原色）
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        guiGraphics.blit(TEXTURE, x, barY, TRACK_U, TRACK_V, BAR_W, BAR_H);

        // 填充（奥罗金金色染色）
        int fillWidth = Math.round(displayedFraction * BAR_W);
        if (fillWidth > BAR_W) {
            fillWidth = BAR_W;
        }
        if (fillWidth > 0) {
            RenderSystem.setShaderColor(FILL_TINT_R, FILL_TINT_G, FILL_TINT_B, alpha);
            guiGraphics.blit(TEXTURE, x, barY, FILL_U, FILL_V, fillWidth, BAR_H);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 绘制揭示新线索闪光（fill 自带 alpha，独立于 shader color）。
     * <p>柔和色洗 + 由进度条边缘向外扩散的光环，配合整体缩放弹跳，避免实心色块的廉价感。
     */
    private static void renderFlashes(GuiGraphics guiGraphics, int x, int barY, float overallAlpha, long now) {
        // 仅揭示新线索时做闪光；最终解密成功（COMPLETE）不再做进度条闪光（此时进度增量为0，不应"爆"）
        renderOneFlash(guiGraphics, x, barY, overallAlpha, now,
                revealFlashStartMs, REVEAL_FLASH_MS, REVEAL_RGB, REVEAL_WASH_ALPHA_MAX);
    }

    /**
     * 绘制单个闪光：柔和色洗（呼吸脉冲）+ 由进度条边缘向外扩散并淡出的光环。
     *
     * @param start        闪光起始时间（&lt;0 表示未激活）
     * @param dur          闪光总时长
     * @param rgb          颜色RGB（不含alpha）
     * @param washAlphaMax 色洗峰值透明度
     */
    private static void renderOneFlash(GuiGraphics guiGraphics, int x, int barY, float overallAlpha, long now,
                                       long start, long dur, int rgb, int washAlphaMax) {
        if (start < 0L) {
            return;
        }
        long elapsed = now - start;
        if (elapsed >= dur) {
            return;
        }
        double t = (double) elapsed / dur;
        double env = 1.0 - t;

        // 柔和色洗（盖在进度条上，低透明度发光感）
        double pulse = (Math.sin(t * Math.PI * 2.0 * 2.0) + 1.0) / 2.0;
        int washA = (int) (washAlphaMax * env * pulse * overallAlpha);
        if (washA > 1) {
            guiGraphics.fill(x, barY, x + BAR_W, barY + BAR_H, (washA << 24) | rgb);
        }

        // 扩散光环（向外扩张，env² 更快淡出）
        int off = (int) Math.round(t * RING_MAX_OFFSET) + 1;
        int ringA = (int) (RING_ALPHA_MAX * env * env * overallAlpha);
        if (ringA > 1) {
            int color = (ringA << 24) | rgb;
            int left = x - off;
            int right = x + BAR_W + off;
            int top = barY - off;
            int bottom = barY + BAR_H + off;
            guiGraphics.fill(left, top, right, top + 1, color);
            guiGraphics.fill(left, bottom - 1, right, bottom, color);
            guiGraphics.fill(left, top, left + 1, bottom, color);
            guiGraphics.fill(right - 1, top, right, bottom, color);
        }
    }

    /**
     * 绘制标题与进度数字。
     * <p>
     * 文案与颜色来自语言文件（§颜色码渲染时生效）；alpha 由 alphaColor 的高位字节控制，
     * 字体渲染器对带 §颜色 的文本同样套用该 alpha，从而保证淡入淡出一致。
     */
    private static void renderText(Font font, GuiGraphics guiGraphics, int x, int titleY, int barY, float alpha, long now) {
        // 计算当前标题状态码与对应翻译组件（3成功 / 2揭示 / 1就绪(满级) / 0普通）
        boolean completeActive = completeFlashStartMs >= 0L && (now - completeFlashStartMs) < COMPLETE_FLASH_MS;
        boolean revealActive = revealFlashStartMs >= 0L && (now - revealFlashStartMs) < REVEAL_FLASH_MS;
        int titleState;
        Component currentTitle;
        if (completeActive) {
            titleState = 3;
            currentTitle = Component.translatable(KEY_TITLE_COMPLETE);
        } else if (revealActive) {
            titleState = 2;
            currentTitle = Component.translatable(KEY_TITLE_REVEAL);
        } else if (targetLevel >= 3) {
            titleState = 1;
            currentTitle = Component.translatable(KEY_TITLE_READY);
        } else {
            titleState = 0;
            currentTitle = Component.translatable(KEY_TITLE_NORMAL, targetLevel);
        }

        // alpha 过低时跳过绘制（字体渲染器对极低 alpha 会强制不透明）；
        // 同步标题跟踪并清空过渡，避免 HUD 重新出现时误触发交叉淡入。
        if (alpha <= 0.15f) {
            lastTitleState = titleState;
            lastTitleComponent = currentTitle;
            titleFadeFrom = null;
            titleFadeStartMs = -1L;
            return;
        }

        // 检测标题状态切换 → 启动交叉淡入（旧标题淡出、新标题淡入），让文字切换不再生硬
        if (titleState != lastTitleState) {
            if (lastTitleState != -1 && lastTitleComponent != null) {
                titleFadeFrom = lastTitleComponent;
                titleFadeStartMs = now;
            }
            lastTitleState = titleState;
        }
        lastTitleComponent = currentTitle;

        // 标题底衬（按当前标题宽度，套用整体 alpha）
        drawTitleBg(font, guiGraphics, x, titleY, currentTitle, alpha);

        // 标题文字：过渡期内交叉淡入淡出，否则正常绘制
        if (titleFadeStartMs >= 0L && titleFadeFrom != null && (now - titleFadeStartMs) < TITLE_FADE_MS) {
            double tp = (double) (now - titleFadeStartMs) / TITLE_FADE_MS;
            drawTitleText(font, guiGraphics, x, titleY, titleFadeFrom, (float) (alpha * (1.0 - tp)));
            drawTitleText(font, guiGraphics, x, titleY, currentTitle, (float) (alpha * tp));
        } else {
            titleFadeStartMs = -1L;
            titleFadeFrom = null;
            drawTitleText(font, guiGraphics, x, titleY, currentTitle, alpha);
        }

        // 进度数字（进度条中央）：未满级时数字跟随进度条平滑计数上涨，避免数值瞬跳
        Component numbers;
        if (targetLevel >= 3) {
            numbers = Component.translatable(KEY_NUMBERS_ALL_REVEALED);
        } else {
            int shownProgress = (int) Math.round(displayedFraction * targetThreshold);
            if (shownProgress < 0) {
                shownProgress = 0;
            } else if (targetThreshold > 0 && shownProgress > targetThreshold) {
                shownProgress = targetThreshold;
            }
            numbers = Component.translatable(KEY_NUMBERS_PROGRESS, shownProgress, targetThreshold);
        }
        int alphaColor = (((int) (alpha * 255.0F) & 0xFF) << 24) | 0xFFFFFF;
        int numbersX = x + (BAR_W - font.width(numbers)) / 2;
        int numbersY = barY + (BAR_H - font.lineHeight) / 2 + 1;
        guiGraphics.drawString(font, numbers, numbersX, numbersY, alphaColor, true);
    }

    /**
     * 绘制标题底衬（半透明深色条，提升浮于世界上方文字的可读性，1px 圆角修饰）。
     *
     * @param title 用于计算宽度与居中的标题组件
     * @param alpha 整体淡入淡出 alpha
     */
    private static void drawTitleBg(Font font, GuiGraphics guiGraphics, int x, int titleY, Component title, float alpha) {
        int titleW = font.width(title);
        int titleX = x + (BAR_W - titleW) / 2;
        int bgAlpha = (int) (TITLE_BG_ALPHA * alpha * 255.0F) & 0xFF;
        if (bgAlpha <= 1) {
            return;
        }
        int bgColor = bgAlpha << 24;
        int bx0 = titleX - TITLE_BG_PAD_X;
        int bx1 = titleX + titleW + TITLE_BG_PAD_X;
        int by0 = titleY - 2;
        int by1 = titleY + font.lineHeight + 1;
        guiGraphics.fill(bx0 + 1, by0, bx1 - 1, by1, bgColor);
        guiGraphics.fill(bx0, by0 + 1, bx1, by1 - 1, bgColor);
    }

    /**
     * 居中绘制标题文字（§颜色码渲染时生效；传入 alpha 控制淡入淡出强度）。
     *
     * @param title     标题组件
     * @param textAlpha 文字 alpha（0~1）
     */
    private static void drawTitleText(Font font, GuiGraphics guiGraphics, int x, int titleY, Component title, float textAlpha) {
        if (textAlpha <= 0.0f) {
            return;
        }
        int a = (int) (textAlpha * 255.0F) & 0xFF;
        // alpha 过低时字体渲染器会强制不透明，跳过以避免突兀
        if (a < 4) {
            return;
        }
        int color = (a << 24) | 0xFFFFFF;
        int titleX = x + (BAR_W - font.width(title)) / 2;
        guiGraphics.drawString(font, title, titleX, titleY, color, true);
    }

    // ==================== 工具 ====================

    /**
     * 计算目标填充比例
     *
     * @return 0~1 的目标填充比例；满级（level&gt;=3）返回 1
     */
    private static double computeTargetFraction() {
        if (targetLevel >= 3) {
            return 1.0;
        }
        if (targetThreshold > 0) {
            double f = (double) targetProgress / targetThreshold;
            if (f < 0.0) {
                return 0.0;
            }
            if (f > 1.0) {
                return 1.0;
            }
            return f;
        }
        return 0.0;
    }

    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    /**
     * 重置全部运行时状态（切换世界时调用）
     */
    private static void resetState() {
        hasData = false;
        displayedFraction = 0.0f;
        visibleUntilMs = 0L;
        appearStartMs = 0L;
        revealFlashStartMs = -1L;
        completeFlashStartMs = -1L;
        fillAnimStart = 0.0f;
        fillAnimTarget = 0.0f;
        fillAnimStartMs = -1L;
        lastTitleState = -1;
        lastTitleComponent = null;
        titleFadeFrom = null;
        titleFadeStartMs = -1L;
    }

    /**
     * 播放 UI 音效
     *
     * @param sound 音效事件
     * @param pitch 音高
     */
    private static void playUiSound(SoundEvent sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }
}
