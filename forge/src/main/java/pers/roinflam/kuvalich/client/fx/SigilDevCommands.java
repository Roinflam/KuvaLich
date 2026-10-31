package pers.roinflam.kuvalich.client.fx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;

import pers.roinflam.kuvalich.config.EffectsDisplayConfig;
import java.lang.reflect.Field;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.gui.requiem.RequiemSigils;
import pers.roinflam.kuvalich.client.sound.KuvaMixer;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 开发环境里的客户端调试命令（正式版不注册），给界面符号和混音器截图、试听用
 *
 * <ul>
 *   <li>{@code /kuvafxc sigils [on|off]}：屏幕上铺一张网格——八张卡各一行，列依次是只描线、卡面（深红填充 + 粉白线 + 红光）
 *       和处决界面的四种画法（暗线外框、点亮、回落、判错），倒数第二行是认不出的卡 id（中性占位），最下面一行是几种界面图元；
 *       {@code /kuvafxc sigils sizes <卡 id>}：同一张卡按实际用到的几种尺寸各画一枚（处决界面中央 0.216H、三格 0.07H、
 *       揭示 0.075H、档案 22 GUI 像素），看各档贴图在真实大小下的样子；</li>
 *   <li>{@code /kuvafxc duck <分贝> <毫秒>}：用调试 key 全局压混，接下来 30 个 tick 每 tick 记一条音量计算前后的值进日志；
 *       {@code /kuvafxc duck release <毫秒>} 放开；</li>
 *   <li>{@code /kuvafxc screenfx on|off}：只在这一局里开关「屏幕效果」选项（不写配置文件），看压混、暗角在关掉时是否不生效。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class SigilDevCommands {

    private static final String DUCK_KEY = "debug";
    private static final String[] LOOK_NAMES = {"draw", "card", "dark", "lit", "settled", "wrong"};
    private static final String[] CARD_NAMES = {"Khra", "Jahu", "Fass", "Lohk", "Netra", "Vome", "Xata", "Ris"};

    private static boolean showSigils;
    /** -1 = 网格；0~8 = 按实际尺寸画这张卡 */
    private static int sizesCard = -1;

    private SigilDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("sigils")
                        .executes(ctx -> toggleSigils(ctx.getSource(), !showSigils))
                        .then(Commands.literal("on").executes(ctx -> toggleSigils(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> toggleSigils(ctx.getSource(), false)))
                        .then(Commands.literal("sizes")
                                .then(Commands.argument("card", IntegerArgumentType.integer(0, 15)).executes(ctx -> {
                                    sizesCard = IntegerArgumentType.getInteger(ctx, "card");
                                    return toggleSigils(ctx.getSource(), true);
                                }))))
                .then(Commands.literal("duck")
                        .then(Commands.literal("release")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(0, 10000)).executes(ctx -> {
                                    int ms = IntegerArgumentType.getInteger(ctx, "ms");
                                    KuvaMixer.release(DUCK_KEY, ms);
                                    KuvaMixer.traceNext(30);
                                    ctx.getSource().sendSuccess(() -> Component.literal("mixer release " + ms + " ms"), false);
                                    return 1;
                                })))
                        .then(Commands.argument("db", FloatArgumentType.floatArg(-60.0F, 0.0F))
                                .then(Commands.argument("ms", IntegerArgumentType.integer(0, 10000)).executes(ctx -> {
                                    float db = FloatArgumentType.getFloat(ctx, "db");
                                    int ms = IntegerArgumentType.getInteger(ctx, "ms");
                                    KuvaMixer.duck(DUCK_KEY, db, ms);
                                    KuvaMixer.traceNext(30);
                                    ctx.getSource().sendSuccess(() -> Component.literal("mixer duck " + db + " dB / " + ms + " ms"), false);
                                    return 1;
                                }))))
                .then(Commands.literal("screenfx")
                        .then(Commands.literal("on").executes(ctx -> screenFx(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> screenFx(ctx.getSource(), false)))));
    }

    /**
     * 只改读值缓存（反射，调试用）：不写配置文件，立刻生效；配置文件被重载时会被文件里的值盖回去。
     * 走配置项的 set 会存盘，要等文件监视触发重载才生效，测的时候前后对不上。
     */
    private static int screenFx(CommandSourceStack src, boolean on) {
        try {
            Field f = EffectsDisplayConfig.class.getDeclaredField("cachedScreenEffects");
            f.setAccessible(true);
            f.setBoolean(null, on);
        } catch (ReflectiveOperationException | RuntimeException e) {
            src.sendFailure(Component.literal("screenfx: " + e));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("screen_effects = " + EffectsDisplayConfig.screenEffects()), false);
        return 1;
    }

    private static int toggleSigils(CommandSourceStack src, boolean on) {
        showSigils = on;
        if (!on) {
            sizesCard = -1;
        }
        src.sendSuccess(() -> Component.literal("sigil grid " + (on ? "on" : "off")), false);
        return 1;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!showSigils || FMLEnvironment.production) {
            return;
        }
        if (sizesCard >= 0) {
            drawSizes(event.getGuiGraphics(), sizesCard);
        } else {
            drawGrid(event.getGuiGraphics());
        }
    }

    /** 八行卡 + 一行占位 × 六列画法，外加一行图元 */
    private static void drawGrid(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float cell = h / 13.5F;
        float size = cell * 0.62F;
        float left = w * 0.5F - cell * 3.0F;
        float top = cell * 1.5F;
        g.fill(0, 0, w, h, 0xE6060A0C);
        int lw = GuiShapes.lineWidthPx();

        for (int col = 0; col < LOOK_NAMES.length; col++) {
            float x = left + cell * (col + 0.5F);
            // 列名比格子宽，上下错开两行写，窗口小的时候也不叠在一起
            int ty = (int) (top - cell * 0.5F) - (col % 2 == 0 ? 18 : 9);
            g.drawCenteredString(font, LOOK_NAMES[col], (int) x, ty, 0xFF8496A0);
        }
        for (int row = 0; row <= RequiemSigils.CARD_COUNT; row++) {
            float y = top + cell * (row + 0.5F);
            int card = row < RequiemSigils.CARD_COUNT ? row : 9;
            String name = row < RequiemSigils.CARD_COUNT ? CARD_NAMES[row] : "id 9";
            g.drawString(font, name, (int) (left - 40), (int) (y - 4), 0xFFB0BEC5);
            for (int col = 0; col < LOOK_NAMES.length; col++) {
                float x = left + cell * (col + 0.5F);
                if (col == 0) {
                    RequiemSigils.draw(g, card, x, y, size, GuiShapes.argb(FxPalette.BONE, 0.95F), 0);
                } else if (col == 1) {
                    RequiemSigils.drawCard(g, card, x, y, size, 1.0F);
                } else {
                    RequiemSigils.drawLook(g, card, x, y, size, RequiemSigils.SigilLook.values()[col - 2], 1.0F, lw);
                }
            }
        }
        // 最下面一行：几种界面图元
        float y = top + cell * (RequiemSigils.CARD_COUNT + 1.2F);
        float px1 = GuiShapes.pixels(g, lw);
        int pale = GuiShapes.argb(FxPalette.KUVA_PALE, 0.95F);
        float x0 = left - cell * 0.5F;
        GuiShapes.begin(g);
        GuiShapes.diamondFill(g, x0, y, size * 0.3F, size * 0.5F, GuiShapes.argb(FxPalette.KUVA_MIST, 0.45F));
        GuiShapes.diamondFillBelow(g, x0, y, size * 0.3F, size * 0.5F, y + size * 0.5F - size * 0.6F,
                GuiShapes.argb(FxPalette.KUVA_DEEP, 0.95F));
        GuiShapes.diamondOutline(g, x0, y, size * 0.3F, size * 0.5F, px1, GuiShapes.argb(FxPalette.KUVA_PALE, 0.2F));
        GuiShapes.diamondTrace(g, x0, y, size * 0.3F, size * 0.5F, px1, pale, 0.6F);
        GuiShapes.diamond(g, x0 + cell, y, size, px1, pale, 0.35F);
        GuiShapes.ring(g, x0 + cell * 2, y, size * 0.45F, px1, pale, 0.7F);
        GuiShapes.ring(g, x0 + cell * 2, y, size * 0.25F, px1, GuiShapes.argb(FxPalette.KUVA_PALE, 0.45F), 1.0F);
        GuiShapes.triangle(g, x0 + cell * 3, y, size, px1, pale, 0.5F);
        GuiShapes.hairline(g, x0 + cell * 3.6F, y + size * 0.4F, x0 + cell * 4.4F, y - size * 0.4F, px1, pale,
                GuiShapes.argb(FxPalette.KUVA_PALE, 0.0F));
        for (int i = 0; i < 3; i++) {
            GuiShapes.disc(g, x0 + cell * 5 + (i - 1) * size * 0.25F, y, size * 0.07F,
                    GuiShapes.argb(i == 2 ? FxPalette.ASH : FxPalette.KUVA_PALE, i == 2 ? 0.35F : 0.95F));
        }
        GuiShapes.end();
        g.drawString(font, "lw " + lw + " px, gui x" + (int) mc.getWindow().getGuiScale(), 4, 4, 0xFF8496A0);
    }

    /** 同一张卡按实际用到的尺寸各画一枚：左边中央大符文（点亮 / 判错），右边三格、揭示、档案 */
    private static void drawSizes(GuiGraphics g, int card) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0xE6060A0C);
        int lw = GuiShapes.lineWidthPx();
        float big = h * 0.216F;
        RequiemSigils.drawLook(g, card, w * 0.18F, h * 0.30F, big, RequiemSigils.SigilLook.LIT, 1.0F, lw * 1.5F);
        RequiemSigils.drawLook(g, card, w * 0.18F, h * 0.72F, big, RequiemSigils.SigilLook.WRONG, 1.0F, lw * 1.5F);
        float cell = h * 0.07F;
        RequiemSigils.SigilLook[] looks = RequiemSigils.SigilLook.values();
        for (int i = 0; i < looks.length; i++) {
            RequiemSigils.drawLook(g, card, w * 0.42F + i * h * 0.111F, h * 0.20F, cell, looks[i], 1.0F, lw);
        }
        RequiemSigils.drawCard(g, card, w * 0.42F, h * 0.45F, h * 0.075F, 1.0F);
        RequiemSigils.draw(g, card, w * 0.42F + h * 0.111F, h * 0.45F, h * 0.075F, GuiShapes.argb(FxPalette.KUVA_PALE, 0.95F),
                GuiShapes.argb(FxPalette.KUVA_DEEP, 0.95F));
        for (int i = 0; i < 3; i++) {
            float x = w * 0.42F + i * 34.0F;
            RequiemSigils.draw(g, (card + i) % (RequiemSigils.CARD_COUNT + 1), x, h * 0.70F, 22.0F,
                    GuiShapes.argb(FxPalette.WHITE, 0.95F), 0);
        }
        String id = card < RequiemSigils.CARD_COUNT ? CARD_NAMES[card] : "id " + card;
        g.drawString(font, id + "  center 0.216H / cells 0.07H / reveal 0.075H / archive 22 gui px, gui x"
                + (int) mc.getWindow().getGuiScale(), 4, 4, 0xFF8496A0);
    }
}
