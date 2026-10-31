package pers.roinflam.kuvalich.client.comm;

import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.client.lich.LichVoice;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.kuvalich.utils.Reference;

import java.lang.reflect.Field;

/**
 * 通讯窗的客户端调试命令，只在开发环境注册（{@link FMLEnvironment#production} 为真时不注册）
 *
 * <ul>
 *   <li>{@code /kuvafxc cleanhud on|off}：除了通讯窗和 Boss 条，别的 HUD 全藏（录宣传片用，F1 会把通讯窗一起藏掉）；</li>
 *   <li>{@code /kuvafxc commat <毫秒>}：把当前通讯窗的时间轴冻在开窗后的某一刻，逐个时刻截图核对；现在没有窗的话，
 *       下一扇窗一开就冻住。{@code commat sub|name|close} 冻在字幕开始、名牌出满、收起到一半。
 *       冻住时屏幕上方叠一条时间轴：各个时刻的刻度、嘟囔的每个音节、当前位置；</li>
 *   <li>{@code /kuvafxc commat off}：解冻，从冻住的那一刻接着走；</li>
 *   <li>{@code /kuvafxc res <宽> <高>}：把渲染分辨率改成指定的帧缓冲尺寸（窗口大小不变，画面在离屏缓冲里按这个尺寸画，
 *       截图也是这个尺寸），核对不同分辨率下的版面；{@code res off} 回到窗口自己的尺寸。只在开发环境用，靠反射改字段。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CommDevClientCommands {

    private static boolean cleanHud;

    private CommDevClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("cleanhud")
                        .then(Commands.literal("on").executes(ctx -> setCleanHud(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setCleanHud(ctx, false))))
                .then(Commands.literal("commat")
                        .then(Commands.literal("off").executes(CommDevClientCommands::unfreeze))
                        .then(Commands.literal("sub").executes(ctx -> freezePhase(ctx, "sub")))
                        .then(Commands.literal("name").executes(ctx -> freezePhase(ctx, "name")))
                        .then(Commands.literal("close").executes(ctx -> freezePhase(ctx, "close")))
                        .then(Commands.argument("ms", IntegerArgumentType.integer(0, 60_000))
                                .executes(ctx -> freeze(ctx, IntegerArgumentType.getInteger(ctx, "ms")))))
                .then(Commands.literal("res")
                        .then(Commands.literal("off").executes(ctx -> resetResolution(ctx)))
                        .then(Commands.argument("w", IntegerArgumentType.integer(320, 7680))
                                .then(Commands.argument("h", IntegerArgumentType.integer(240, 4320))
                                        .executes(ctx -> setResolution(ctx, IntegerArgumentType.getInteger(ctx, "w"),
                                                IntegerArgumentType.getInteger(ctx, "h")))))));
    }

    private static int setResolution(CommandContext<CommandSourceStack> ctx, int w, int h) {
        if (!forceFramebuffer(w, h)) {
            ctx.getSource().sendFailure(Component.literal("cannot change framebuffer size here"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("framebuffer " + w + "x" + h), false);
        return 1;
    }

    private static int resetResolution(CommandContext<CommandSourceStack> ctx) {
        Window wnd = Minecraft.getInstance().getWindow();
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetFramebufferSize(wnd.getWindow(), w, h);
        return setResolution(ctx, Math.max(320, w[0]), Math.max(240, h[0]));
    }

    /** 直接改窗口记的帧缓冲尺寸，再让游戏按它重建渲染目标和界面缩放 */
    private static boolean forceFramebuffer(int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Field fw = Window.class.getDeclaredField("framebufferWidth");
            Field fh = Window.class.getDeclaredField("framebufferHeight");
            fw.setAccessible(true);
            fh.setAccessible(true);
            fw.setInt(mc.getWindow(), w);
            fh.setInt(mc.getWindow(), h);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        mc.resizeDisplay();
        return true;
    }

    private static int setCleanHud(CommandContext<CommandSourceStack> ctx, boolean on) {
        cleanHud = on;
        ctx.getSource().sendSuccess(() -> Component.literal("cleanhud " + (on ? "on" : "off")), false);
        return 1;
    }

    private static int freeze(CommandContext<CommandSourceStack> ctx, int ms) {
        CommHud.debugFreeze(ms);
        ctx.getSource().sendSuccess(() -> Component.literal("comm timeline frozen at " + ms + " ms"
                + (CommHud.isOpen() ? "" : " (next window)")), false);
        return 1;
    }

    private static int freezePhase(CommandContext<CommandSourceStack> ctx, String phase) {
        long ms = CommHud.debugPhaseMs(phase);
        if (ms < 0L) {
            ctx.getSource().sendFailure(Component.literal("no comm window in that phase"));
            return 0;
        }
        return freeze(ctx, (int) ms);
    }

    private static int unfreeze(CommandContext<CommandSourceStack> ctx) {
        CommHud.debugUnfreeze();
        ctx.getSource().sendSuccess(() -> Component.literal("comm timeline running"), false);
        return 1;
    }

    /** 录宣传片：只留通讯窗和 Boss 条（原版的和各模组自己画的，按 id 里带 boss 认） */
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!cleanHud) {
            return;
        }
        ResourceLocation id = event.getOverlay().id();
        if (id.equals(CommHud.OVERLAY_ID) || id.getPath().contains("boss")) {
            return;
        }
        event.setCanceled(true);
    }

    // ==================== 冻住时叠的时间轴 ====================

    private static final int BG = 0xC0000000;
    private static final int AXIS = 0xFF8496A0;
    private static final int MARK = 0xFFDCE6EA;
    private static final int SYLLABLE = 0xFFFF3B52;
    private static final int ENVELOPE = 0xFFFFC861;
    private static final int CURSOR = 0xFFF8FA2E;

    /** 冻住时在屏幕上方画一条时间轴（GUI 坐标）：各时刻的刻度、音节块和包络、当前位置，下面几行写数 */
    static void drawDebug(GuiGraphics g, Font font, CommHud.DebugView v) {
        int screenW = g.guiWidth();
        int left = 8;
        int right = screenW - 8;
        int top = 6;
        int axisY = top + 30;
        long span = Math.max(1000L, v.done() + 200L);
        g.fill(left - 4, top - 2, right + 4, axisY + 54, BG);

        g.fill(left, axisY, right, axisY + 1, AXIS);
        markRow = 0;
        mark(g, font, left, right, span, axisY, 0L, "0");
        mark(g, font, left, right, span, axisY, v.openDur(), "open");
        if (v.textStart() >= 0L) {
            mark(g, font, left, right, span, axisY, v.textStart(), "sub");
            mark(g, font, left, right, span, axisY, v.textStart() + v.wipeMs(), "+D");
        }
        if (v.nameIn() >= 0L) {
            mark(g, font, left, right, span, axisY, v.nameIn(), "name");
        }
        mark(g, font, left, right, span, axisY, v.textEnd(), "end");
        mark(g, font, left, right, span, axisY, v.close(), "close");
        mark(g, font, left, right, span, axisY, v.done(), "done");

        LichVoice.Schedule s = v.schedule();
        if (s != null && v.textStart() >= 0L) {
            int[] on = s.onsetMs();
            int[] du = s.durMs();
            for (int k = 0; k < on.length; k++) {
                int x0 = xOf(left, right, span, v.textStart() + on[k]);
                int x1 = Math.max(x0 + 1, xOf(left, right, span, v.textStart() + on[k] + du[k]));
                g.fill(x0, axisY + 4, x1, axisY + 10, SYLLABLE);
            }
            // 包络：每 2 GUI 像素采一次
            long origin = s.startMs() - v.textStart();
            for (int x = left; x < right; x += 2) {
                long ms = (long) ((x - left) / (double) (right - left) * span);
                float e = LichVoice.envelopeOf(s, Long.MAX_VALUE, origin + ms);
                if (e > 0.01F) {
                    int hgt = Math.round(e * 8.0F);
                    g.fill(x, axisY + 20 - hgt, x + 1, axisY + 20, ENVELOPE);
                }
            }
        }
        int cx = xOf(left, right, span, v.t());
        g.fill(cx, axisY - 10, cx + 1, axisY + 22, CURSOR);

        int ty = axisY + 24;
        g.drawString(font, "t=" + v.t() + "ms  D=" + v.wipeMs() + "  H=" + (v.textEnd() - v.textStart())
                + "  close=" + v.close() + "  done=" + v.done(), left, ty, 0xFFFFFFFF, false);
        String voice = v.lich()
                ? "han=" + v.han() + " latin=" + v.latin() + " syllables=" + v.syllables()
                + (s != null ? " mutter=" + s.lengthMs() + "ms" : "") + "  static=" + (v.staticActive() ? "playing" : "-")
                : "han=" + v.han() + " latin=" + v.latin();
        g.drawString(font, voice, left, ty + 10, 0xFFFFFFFF, false);
        if (s != null) {
            StringBuilder sb = new StringBuilder("onsets:");
            int[] on = s.onsetMs();
            for (int k = 0; k < on.length; k++) {
                sb.append(' ').append(on[k]);
                if (k + 1 < on.length) {
                    int gap = on[k + 1] - on[k];
                    if (gap > 230) {
                        sb.append(" |").append(gap);
                    }
                }
            }
            g.drawString(font, sb.toString(), left, ty + 20, 0xFFFFFFFF, false);
        }
    }

    private static int markRow;

    private static void mark(GuiGraphics g, Font font, int left, int right, long span, int axisY, long ms, String label) {
        int x = xOf(left, right, span, ms);
        g.fill(x, axisY - 4, x + 1, axisY + 2, MARK);
        // 刻度挨得近时标签上下错开两行
        int row = markRow++ % 2;
        g.drawString(font, label + " " + ms, x + 1, axisY - 13 - row * 9, MARK, false);
    }

    private static int xOf(int left, int right, long span, long ms) {
        return left + (int) Math.round(Math.max(0L, Math.min(span, ms)) / (double) span * (right - left));
    }
}
