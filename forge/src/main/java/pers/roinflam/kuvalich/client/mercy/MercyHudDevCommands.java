package pers.roinflam.kuvalich.client.mercy;

import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.FxPalette;
import pers.roinflam.kuvalich.client.gui.requiem.GuiShapes;
import pers.roinflam.kuvalich.client.lich.ClientLichState;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.Reference;

import java.lang.reflect.Field;

/**
 * 开发环境里的客户端调试命令（正式版不注册），处决界面、升级横幅、倒地提示截图量位置用
 *
 * <ul>
 *   <li>{@code /kuvafxc mercygrid [on|off]}：铺一层量位置的网格——横线每 0.01H 一条、每 0.05H 加粗带刻度，竖线从屏幕中线
 *       往两边同样排；处决界面和横幅各元素该在的中心画成青色小十字，元素中心和十字对上就说明位置对；</li>
 *   <li>{@code /kuvafxc mercyhud <a|b|c> <结果> <毫秒|play> [卡1 卡2 卡3]}：本地摆一场假处决只给界面画
 *       （冻在这一毫秒，或者从 0 实时播一遍）；判错的同时排一条跟着这场走的升级横幅。结果：第三次处决
 *       {@code success / fail1 / fail2 / fail3}，第一、二次处决全对 {@code adv1 / adv2}，第一、二次处决判错
 *       {@code k1fail1 / k2fail1 / k2fail2}（k 后面是第几次处决，fail 后面是判错的第几刀）。{@code /kuvafxc mercyhud off} 撤掉；</li>
 *   <li>{@code /kuvafxc banner <等级> [direct|fled] [毫秒]}：直接排一条升级横幅（{@code fled} = 怜悯之击判错后逃走那种，
 *       按第一刀判错排时间），给了毫秒就冻在横幅的这一刻；{@code /kuvafxc banner off} 撤掉；</li>
 *   <li>{@code /kuvafxc mercydown [own|other|none] [秒]}：把准星指着的（或者最近的）赤毒实体在本客户端上摆成倒地
 *       （只改本地的同步数据，服务端不知道），{@code own} = 自己能处决、{@code other} = 资格人写成它自己、
 *       {@code none} = 没有资格人；{@code /kuvafxc mercydown clear} 恢复。看倒地提示、试处决键用；</li>
 *   <li>{@code /kuvafxc mercychoice <open|full|disabled|judging|off>}：摆一组假的感化 / 消灭选择牌（10 秒倒计时，
 *       {@code full} 带名册已满那一行，{@code judging} 直接是倒计时走完的样子），按住左右移动键看进度，不发包。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MercyHudDevCommands {

    /** 结果名和对应的（刀数，前缀对几张） */
    private static final String[] RESULTS = {"success", "fail1", "fail2", "fail3", "adv1", "adv2", "k1fail1", "k2fail1",
            "k2fail2"};
    private static final int[][] RESULT_PLAN = {{3, 3}, {3, 0}, {3, 1}, {3, 2}, {1, 1}, {2, 2}, {1, 0}, {2, 0}, {2, 1}};
    private static boolean grid;

    private MercyHudDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("mercygrid")
                        .executes(ctx -> grid(ctx.getSource(), !grid))
                        .then(Commands.literal("on").executes(ctx -> grid(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> grid(ctx.getSource(), false))))
                .then(Commands.literal("mercyhud")
                        .then(Commands.literal("off").executes(ctx -> {
                            MercyHud.clearDev();
                            RankUpBanner.clear();
                            ctx.getSource().sendSuccess(() -> Component.literal("mercyhud off"), false);
                            return 1;
                        }))
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .then(Commands.argument("result", StringArgumentType.word())
                                        .then(withCards(Commands.literal("play"), true))
                                        .then(withCards(Commands.argument("ms", IntegerArgumentType.integer(0, 20000)),
                                                false)))))
                .then(Commands.literal("mercychoice")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .executes(ctx -> choice(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("banner")
                        .then(Commands.literal("off").executes(ctx -> {
                            RankUpBanner.clear();
                            ctx.getSource().sendSuccess(() -> Component.literal("banner off"), false);
                            return 1;
                        }))
                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, 999))
                                .executes(ctx -> banner(ctx, false, -1))
                                .then(Commands.literal("direct")
                                        .executes(ctx -> banner(ctx, false, -1))
                                        .then(Commands.argument("ms", IntegerArgumentType.integer(0, 7000))
                                                .executes(ctx -> banner(ctx, false, IntegerArgumentType.getInteger(ctx, "ms")))))
                                .then(Commands.literal("fled")
                                        .executes(ctx -> banner(ctx, true, -1))
                                        .then(Commands.argument("ms", IntegerArgumentType.integer(0, 7000))
                                                .executes(ctx -> banner(ctx, true, IntegerArgumentType.getInteger(ctx, "ms")))))))
                .then(Commands.literal("mercydown")
                        .executes(ctx -> down(ctx.getSource(), "own", 60))
                        .then(Commands.literal("clear").executes(ctx -> down(ctx.getSource(), "clear", 0)))
                        .then(Commands.argument("who", StringArgumentType.word())
                                .executes(ctx -> down(ctx.getSource(), StringArgumentType.getString(ctx, "who"), 60))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                        .executes(ctx -> down(ctx.getSource(), StringArgumentType.getString(ctx, "who"),
                                                IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("mercyring")
                        .then(Commands.literal("kuva").executes(ctx -> ring(ctx.getSource(), MercyPrompt.RingStyle.KUVA)))
                        .then(Commands.literal("bone").executes(ctx -> ring(ctx.getSource(), MercyPrompt.RingStyle.BONE)))
                        .then(Commands.literal("gold").executes(ctx -> ring(ctx.getSource(), MercyPrompt.RingStyle.GOLD)))));
    }

    /** 倒地倒计时圈换颜色（三版给人挑） */
    private static int ring(CommandSourceStack src, MercyPrompt.RingStyle style) {
        MercyPrompt.ringStyle = style;
        src.sendSuccess(() -> Component.literal("mercy ring " + style.name().toLowerCase(java.util.Locale.ROOT)), false);
        return 1;
    }

    /** {@code play} / {@code <ms>} 后面可选再跟三张卡的 id */
    private static ArgumentBuilder<CommandSourceStack, ?> withCards(ArgumentBuilder<CommandSourceStack, ?> node,
                                                                    boolean play) {
        return node.executes(ctx -> hud(ctx, play, false))
                .then(Commands.argument("c1", IntegerArgumentType.integer(-1, 15))
                        .then(Commands.argument("c2", IntegerArgumentType.integer(-1, 15))
                                .then(Commands.argument("c3", IntegerArgumentType.integer(-1, 15))
                                        .executes(ctx -> hud(ctx, play, true)))));
    }

    /** {@code open / full / disabled / judging / off}：本地摆一组假的选择牌（不发包），量位置、试按住进度用 */
    private static int choice(CommandSourceStack src, String mode) {
        switch (mode) {
            case "off" -> MercyChoiceHud.clearDev();
            case "open" -> MercyChoiceHud.setDev(true, "", false);
            case "full" -> MercyChoiceHud.setDev(true, "Vor Tekalu", false);
            case "disabled" -> MercyChoiceHud.setDev(false, "", false);
            case "judging" -> MercyChoiceHud.setDev(true, "Vor Tekalu", true);
            default -> {
                src.sendFailure(Component.literal("mercychoice <open|full|disabled|judging|off>"));
                return 0;
            }
        }
        src.sendSuccess(() -> Component.literal("mercychoice " + mode), false);
        return 1;
    }

    private static int grid(CommandSourceStack src, boolean on) {
        grid = on;
        src.sendSuccess(() -> Component.literal("mercy grid " + (on ? "on" : "off")), false);
        return 1;
    }

    private static int hud(CommandContext<CommandSourceStack> ctx, boolean play, boolean cards) {
        String v = StringArgumentType.getString(ctx, "variant");
        String r = StringArgumentType.getString(ctx, "result");
        MercyVariant variant = switch (v) {
            case "a" -> MercyVariant.LICH_A;
            case "b" -> MercyVariant.LICH_B;
            case "c" -> MercyVariant.LICH_C;
            default -> null;
        };
        int stabs = -1, prefix = -1;
        for (int i = 0; i < RESULTS.length; i++) {
            if (RESULTS[i].equals(r)) {
                stabs = RESULT_PLAN[i][0];
                prefix = RESULT_PLAN[i][1];
            }
        }
        if (variant == null || prefix < 0) {
            ctx.getSource().sendFailure(Component.literal("mercyhud <a|b|c> <" + String.join("|", RESULTS)
                    + "> <ms|play> [c1 c2 c3]"));
            return 0;
        }
        int[] ids = cards
                ? new int[]{IntegerArgumentType.getInteger(ctx, "c1"), IntegerArgumentType.getInteger(ctx, "c2"),
                IntegerArgumentType.getInteger(ctx, "c3")}
                : new int[]{5, 0, 6};
        MercyPlan.Plan plan = new MercyPlan.Plan(stabs, prefix, variant, ids, new int[]{3, 2, 1}, false, 0, true, false);
        long ms = play ? -1L : IntegerArgumentType.getInteger(ctx, "ms");
        MercyHud.setDev(plan, ms);
        if (prefix < stabs) {
            int rank = ClientLichState.hasLich() ? ClientLichState.get().rank() + 1 : 4;
            RankUpBanner.show(rank, true, prefix + 1, MercyHud.DEV_TARGET, RankUpBanner.nameOf(-1), -1L);
        } else {
            RankUpBanner.clear();
        }
        String desc = "mercyhud " + v + " " + r + " " + (play ? "play" : ms + " ms");
        ctx.getSource().sendSuccess(() -> Component.literal(desc), false);
        return 1;
    }

    private static int banner(CommandContext<CommandSourceStack> ctx, boolean fled, int ms) {
        int rank = IntegerArgumentType.getInteger(ctx, "rank");
        Entity lich = aimedKuva();
        RankUpBanner.show(rank, fled, 1, -1, RankUpBanner.nameOf(lich == null ? -1 : lich.getId()), ms);
        ctx.getSource().sendSuccess(() -> Component.literal("banner " + rank + (fled ? " fled" : " direct")
                + (ms >= 0 ? " @" + ms : "")), false);
        return 1;
    }

    // ==================== 本地摆倒地 ====================

    private static int down(CommandSourceStack src, String who, int seconds) {
        AbstractKuva k = aimedKuva();
        Minecraft mc = Minecraft.getInstance();
        if (k == null || mc.player == null) {
            src.sendFailure(Component.literal("mercydown: no kuva entity nearby"));
            return 0;
        }
        int actor = switch (who) {
            case "own" -> mc.player.getId();
            case "other" -> k.getId();
            default -> -1;
        };
        boolean clear = "clear".equals(who);
        try {
            set(k, "DATA_MERCY_STATE", (byte) (clear ? MercyState.NONE.ordinal() : MercyState.DOWNED.ordinal()));
            set(k, "DATA_MERCY_ACTOR", clear ? -1 : actor);
            set(k, "DATA_MERCY_TIMER", clear ? 0 : AbstractKuva.packMercyTimer(seconds * 20, 3.0F, seconds));
        } catch (ReflectiveOperationException | RuntimeException e) {
            src.sendFailure(Component.literal("mercydown: " + e));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("mercydown " + who + " -> entity " + k.getId()), false);
        return 1;
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(AbstractKuva k, String field, T value) throws ReflectiveOperationException {
        Field f = AbstractKuva.class.getDeclaredField(field);
        f.setAccessible(true);
        k.getEntityData().set((EntityDataAccessor<T>) f.get(null), value);
    }

    /** 准星指着的赤毒实体；没有就取 24 格内最近的 */
    @Nullable
    private static AbstractKuva aimedKuva() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof AbstractKuva k) {
            return k;
        }
        if (mc.level == null || mc.player == null) {
            return null;
        }
        AbstractKuva best = null;
        double bestD = 24.0D * 24.0D;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof AbstractKuva k) {
                double d = k.distanceToSqr(mc.player);
                if (d < bestD) {
                    bestD = d;
                    best = k;
                }
            }
        }
        return best;
    }

    // ==================== 量位置的网格 ====================

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!grid || FMLEnvironment.production) {
            return;
        }
        drawGrid(event.getGuiGraphics());
    }

    private static void drawGrid(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        Font font = mc.font;
        float gs = (float) win.getGuiScale();
        float W = win.getWidth(), H = win.getHeight(), cx = W * 0.5F;
        g.pose().pushPose();
        g.pose().scale(1.0F / gs, 1.0F / gs, 1.0F);
        int faint = GuiShapes.argb(FxPalette.TECH, 0.16F);
        int strong = GuiShapes.argb(FxPalette.TECH, 0.45F);
        GuiShapes.begin(g);
        for (int i = 0; i <= 100; i++) {
            float y = H * i / 100.0F;
            GuiShapes.hairline(g, 0, y, W, y, 1.0F, i % 5 == 0 ? strong : faint);
        }
        int n = (int) Math.ceil(cx / (H * 0.01F));
        for (int i = -n; i <= n; i++) {
            float x = cx + H * 0.01F * i;
            GuiShapes.hairline(g, x, 0, x, H, 1.0F, i % 5 == 0 ? strong : faint);
        }
        int mark = GuiShapes.argb(0x40FFFF, 0.95F);
        cross(g, cx, MercyHud.CENTER_Y * H, H, mark);
        for (int i = 0; i < 3; i++) {
            float x = cx + (i - 1) * MercyHud.CELL_DX * H;
            cross(g, x, MercyHud.CELL_Y * H, H, mark);
            for (int j = 0; j < 3; j++) {
                cross(g, x + (j - 1) * MercyHud.DOT_DX * H, MercyHud.DOT_Y * H, H * 0.4F, mark);
            }
        }
        cross(g, cx, 0.700F * H, H, mark);
        cross(g, cx, 0.805F * H, H, mark);
        cross(g, cx, 0.845F * H, H, mark);
        cross(g, cx, 0.910F * H, H, mark);
        GuiShapes.end();
        int p = Math.max(1, Math.round(H / 540.0F));
        for (int i = 0; i <= 100; i += 5) {
            String s = String.format("%.2f", i / 100.0F);
            g.pose().pushPose();
            g.pose().translate(4.0F, H * i / 100.0F + 2.0F, 0.0F);
            g.pose().scale(p, p, 1.0F);
            g.drawString(font, s, 0, 0, 0xFF40FFFF, false);
            g.pose().popPose();
        }
        g.pose().popPose();
        g.drawString(font, (int) W + "x" + (int) H + " gui x" + (int) gs + " lw " + GuiShapes.lineWidthPx()
                + (RankUpBanner.debugState() != null ? "  " + RankUpBanner.debugState() : ""), 4, 4, 0xFF40FFFF, false);
    }

    /** 青色小十字，臂长 0.008H */
    private static void cross(GuiGraphics g, float x, float y, float H, int argb) {
        float a = H * 0.008F;
        GuiShapes.hairline(g, x - a, y, x + a, y, 1.0F, argb);
        GuiShapes.hairline(g, x, y - a, x, y + a, 1.0F, argb);
    }
}
