package pers.roinflam.kuvalich.client.gui.guide;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import pers.roinflam.kuvalich.config.GuideDisplayConfig;
import pers.roinflam.kuvalich.utils.Reference;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * 冒险指南自己的界面倍数：打开时临时改窗口的界面缩放，关掉时还原（2026-10-13 起；挑法见 {@link GuideScaleMath}）
 *
 * <h3>为什么改窗口、不在指南里整体缩放</h3>
 * <p>原版每帧都从窗口读倍数：界面投影按 {@code 帧缓冲宽 / getGuiScale()} 建，鼠标坐标按 {@code getGuiScaledWidth() / 屏幕宽} 换算，
 * {@code GuiGraphics.applyScissor} 用 {@code getGuiScale()} 乘回帧缓冲坐标，{@code guiWidth()} 也读窗口。改了窗口倍数，指南里的字、物品图标、
 * 提示框、裁剪、搜索框、实体预览全部自动一致，指南内部不用改；倍数总是整数，字不会糊。
 * 整体缩放 PoseStack 的做法要逐处换算裁剪（裁剪不吃 pose 矩阵）和鼠标，提示框会按真实坐标夹边、比指南的字大一号，不选。</p>
 *
 * <h3>改了全局状态，要处理的事</h3>
 * <ol>
 *   <li><b>生效</b>：指南受保护的 {@code init()} 第一行调 {@link #apply}（首次打开、改窗口大小、切全屏、F3+T 重载、调大小都走这里），
 *       设窗口倍数并把界面的 width / height 改成设完后的虚拟尺寸。<b>不调 {@code resizeDisplay()}</b>：它先按选项把倍数改回去，
 *       再 {@code screen.resize → init → 又设 → 再 resize}，无限递归。投影和鼠标映射每帧从窗口读，不用通知谁。</li>
 *   <li><b>还原</b>：{@code removed()} 里 {@link #restore}（按 {@code calculateScale(选项, 强制 Unicode)}，和 {@code resizeDisplay} 一样的算法；
 *       同样不调 {@code resizeDisplay}，那时它会落到正被换掉的指南身上）。换界面都经过 {@code setScreen → removed()}；
 *       兜底：每 tick 查一次「倍数是指南的，但当前界面已经不是指南」就还原。只改窗口，不碰 {@code options.guiScale}，不会写进 options.txt。</li>
 *   <li><b>背后的 HUD</b>：开着界面时原版照样画 HUD、和界面共用一个投影。倍数一改，快捷栏、聊天会在开书那一帧突然缩小。
 *       {@link RenderGuiEvent.Pre}（最高优先级）里 flush → 窗口换回游戏倍数 → 按游戏倍数重设投影 → 把 {@code Gui} 记下的屏幕宽高也改回来；
 *       {@link RenderGuiEvent.Post}（最低优先级）里换回指南的。{@code ForgeGui.render} 在发 Pre <b>之前</b>就把虚拟宽高读进了自己的字段，
 *       各浮层按那两个字段摆位置，不改的话快捷栏会按指南的宽度居中、画在游戏的投影里（偏到左边）。
 *       Pre 被别的模组取消时 Post 不会来：指南 {@code render()} 开头 {@link #ensure} 再核对一次。</li>
 *   <li><b>toast</b>（成就、配方提示）在界面之后画、没有事件可挂，开着指南时按指南倍数画（小一圈），不处理。</li>
 *   <li><b>别的模组</b>改写了 {@code Window#setGuiScale}：设完读回，不等于想要的就放弃这次（不改、不挂 HUD 切换），指南照游戏倍数画。</li>
 * </ol>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuideScale {

    /** 窗口倍数现在是指南的（和游戏的不同） */
    private static boolean active;
    /** 指南用的倍数（active 时有效） */
    private static int applied;
    /** 这一帧画 HUD 那一段换回了游戏倍数、还没换回来（Pre 来了、Post 还没来） */
    private static boolean hudSwitched;
    /** 投影矩阵的草稿（{@code RenderSystem.setProjectionMatrix} 会复制一份，可以复用） */
    private static final Matrix4f PROJECTION = new Matrix4f();
    /** {@code Gui.screenWidth / screenHeight}（protected）：正式环境 SRG 名 f_92977_ / f_92978_，开发环境是官方名（见 {@link #guiField}） */
    @Nullable
    private static Field screenWidthField;
    @Nullable
    private static Field screenHeightField;
    private static boolean fieldsLooked;

    private GuideScale() {
    }

    // ==================== 挑倍数 ====================

    /** 游戏实际用的倍数（和 {@code Minecraft#resizeDisplay} 同一个算法） */
    static int gameScale(Minecraft mc) {
        return mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
    }

    /** 指南想用的倍数：读配置 → 自动挑 / 跟游戏 / 指定值夹到 [最小, 游戏倍数] */
    static int want(Minecraft mc) {
        Window w = mc.getWindow();
        return GuideScaleMath.resolve(GuideDisplayConfig.guiScale(), w.getWidth(), w.getHeight(),
                mc.options.guiScale().get(), gameScale(mc), mc.isEnforceUnicode());
    }

    /** 指南现在用的倍数 */
    static int current(Minecraft mc) {
        return active ? applied : (int) Math.round(mc.getWindow().getGuiScale());
    }

    /** 可选的档不止一个（游戏倍数比最小档大）：「Aa」按钮画成可用 */
    static boolean adjustable(Minecraft mc) {
        int game = gameScale(mc);
        return GuideScaleMath.step(GuideScaleMath.min(game), 1, game, mc.isEnforceUnicode()) != GuideScaleMath.min(game);
    }

    // ==================== 生效 / 还原 ====================

    /**
     * 指南受保护的 {@code init()} 第一行调：设窗口倍数 → 读回校验 → 同步界面的 width / height
     */
    static void apply(Screen screen, Minecraft mc) {
        Window w = mc.getWindow();
        int game = gameScale(mc);
        int target = want(mc);
        hudSwitched = false;
        if ((int) Math.round(w.getGuiScale()) != target) {
            w.setGuiScale(target);
        }
        if ((int) Math.round(w.getGuiScale()) != target) {
            // 有模组强制自己的倍数：放弃这次，指南照游戏倍数画
            GuideLog.warnOnce("scale:override", "窗口的界面倍数设不成 " + target + "（读回 " + w.getGuiScale()
                    + "，可能有别的模组改写了 Window#setGuiScale），指南照游戏倍数画");
            w.setGuiScale(game);
            active = false;
        } else {
            active = target != game;
            applied = target;
        }
        screen.width = w.getGuiScaledWidth();
        screen.height = w.getGuiScaledHeight();
    }

    /** 指南 {@code removed()} 调：窗口倍数设回游戏的 */
    static void restore(Minecraft mc) {
        if (active) {
            mc.getWindow().setGuiScale(gameScale(mc));
        }
        active = false;
        hudSwitched = false;
    }

    /**
     * 调一档（「Aa」按钮、Ctrl + 滚轮、Ctrl − =）：写配置，调用方随后重建界面
     *
     * @param dir +1 字大一档（倍数 +1），−1 字小一档
     * @return 倍数变了（到头了返回 false）
     */
    static boolean step(Minecraft mc, int dir) {
        int now = current(mc);
        int next = GuideScaleMath.step(now, dir, gameScale(mc), mc.isEnforceUnicode());
        if (next == now) {
            return false;
        }
        GuideDisplayConfig.set(next);
        return true;
    }

    /**
     * 回到自动（Ctrl + 0）
     *
     * @return 倍数变了（配置照样改回自动，但倍数没变时不用重建）
     */
    static boolean reset(Minecraft mc) {
        int before = current(mc);
        GuideDisplayConfig.set(GuideScaleMath.AUTO);
        return want(mc) != before;
    }

    /** 「Aa」按钮悬停提示的第一行：界面大小：自动（×3）/ ×2（已指定）/ 跟游戏一样（×4） */
    static Component label(Minecraft mc) {
        int s = current(mc);
        int setting = GuideDisplayConfig.guiScale();
        String key = setting == GuideScaleMath.FOLLOW_GAME ? "kuvalich.guide.scale.game"
                : setting <= GuideScaleMath.AUTO ? "kuvalich.guide.scale.auto" : "kuvalich.guide.scale.fixed";
        return Component.translatable("kuvalich.guide.scale.title", Component.translatable(key, s));
    }

    // ==================== 画面 ====================

    /**
     * 指南 {@code render()} 开头调：倍数不是指南的（HUD 那一段换回游戏倍数后 Post 没来）就设回来
     */
    static void ensure(GuiGraphics g) {
        if (!active) {
            return;
        }
        Window w = Minecraft.getInstance().getWindow();
        if (!hudSwitched && (int) Math.round(w.getGuiScale()) == applied) {
            return;
        }
        g.flush();
        w.setGuiScale(applied);
        projection(w);
        hudSwitched = false;
    }

    /** 按窗口当前倍数重设界面投影（同 {@code GameRenderer.render} 里那一段） */
    private static void projection(Window w) {
        PROJECTION.setOrtho(0f, (float) (w.getWidth() / w.getGuiScale()), (float) (w.getHeight() / w.getGuiScale()), 0f,
                1000f, ForgeHooksClient.getGuiFarPlane());
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.ORTHOGRAPHIC_Z);
    }

    /** {@code ForgeGui} 在发 Pre 之前记下的屏幕宽高改成游戏倍数下的（各浮层按它摆位置） */
    private static void syncGuiSize(Minecraft mc, Window w) {
        if (!fieldsLooked) {
            fieldsLooked = true;
            screenWidthField = guiField("f_92977_", "screenWidth");
            screenHeightField = guiField("f_92978_", "screenHeight");
            if (screenWidthField == null || screenHeightField == null) {
                screenWidthField = null;
                screenHeightField = null;
            }
        }
        if (screenWidthField == null || screenHeightField == null) {
            return;
        }
        try {
            screenWidthField.setInt(mc.gui, w.getGuiScaledWidth());
            screenHeightField.setInt(mc.gui, w.getGuiScaledHeight());
        } catch (IllegalAccessException | RuntimeException e) {
            GuideLog.warnOnce("scale:gui_fields_set", "改不了 Gui 的屏幕宽高字段，开着指南时快捷栏可能偏位：" + e);
            screenWidthField = null;
            screenHeightField = null;
        }
    }

    /**
     * 取 {@code Gui} 的一个 protected 字段：正式环境（运行期是 SRG 名）按 SRG 名经 {@link ObfuscationReflectionHelper} 取；
     * 开发环境运行期是官方名，SRG 名找不到时再按官方名取一次（字符串常量不会被构建时的重映射改掉，所以两个名字都写上）
     *
     * @param srg      SRG 名（1.20.1：f_92977_ = screenWidth、f_92978_ = screenHeight）
     * @param official 官方名
     * @return 字段；都找不到时为 null（记一行日志，开着指南时快捷栏可能偏位，不崩）
     */
    @Nullable
    private static Field guiField(String srg, String official) {
        try {
            return ObfuscationReflectionHelper.findField(Gui.class, srg);
        } catch (RuntimeException e) {
            try {
                Field f = Gui.class.getDeclaredField(official);
                f.setAccessible(true);
                return f;
            } catch (ReflectiveOperationException | RuntimeException e2) {
                GuideLog.warnOnce("scale:gui_fields", "找不到 Gui 的屏幕宽高字段（" + srg + " / " + official
                        + "），开着指南时快捷栏可能偏位：" + e);
                return null;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGuideScaleHudPre(RenderGuiEvent.Pre event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        event.getGuiGraphics().flush();
        w.setGuiScale(gameScale(mc));
        projection(w);
        syncGuiSize(mc, w);
        hudSwitched = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGuideScaleHudPost(RenderGuiEvent.Post event) {
        if (!active || !hudSwitched) {
            return;
        }
        Window w = Minecraft.getInstance().getWindow();
        event.getGuiGraphics().flush();
        w.setGuiScale(applied);
        projection(w);
        hudSwitched = false;
    }

    /** 兜底：倍数是指南的、当前界面却已经不是指南（有模组直接改 {@code minecraft.screen} 绕开了 setScreen）就还原 */
    @SubscribeEvent
    public static void onGuideScaleClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof GuideScreen)) {
            restore(mc);
        }
    }
}
