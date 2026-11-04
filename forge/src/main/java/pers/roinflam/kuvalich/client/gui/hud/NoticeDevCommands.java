package pers.roinflam.kuvalich.client.gui.hud;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Locale;

/**
 * 开发环境里的客户端调试命令（正式版不注册）：在本地直接往提示条里推几条，截图、对时间轴用，不经过服务端
 *
 * <ul>
 *   <li>{@code /kuvafxc notice demo}：一次推十条样例，各个类别、普通和里程碑都有（同时最多三条，其余排队依次出）；</li>
 *   <li>{@code /kuvafxc notice long}：两条最长的（看折行、截断）；</li>
 *   <li>{@code /kuvafxc notice key <类别> <bar|chat> <语言键> [参数 a|b|c]}：推任意一条，类别是
 *       lich / success / danger / warn / progress / accent / muted，chat 是里程碑；</li>
 *   <li>{@code /kuvafxc notice hold on|off}：一直停着不退场；</li>
 *   <li>{@code /kuvafxc notice freeze on|off}、{@code step <毫秒>}：定住时间轴、往前拨，逐帧截图对动效；</li>
 *   <li>{@code /kuvafxc notice state}：现在几条在屏幕上、几条在排队；</li>
 *   <li>{@code /kuvafxc notice clear}：全部收掉，取消 hold / freeze。</li>
 * </ul>
 *
 * <p>命令是在聊天栏里打的，聊天栏开着时提示条不画、时间也停住，关掉聊天栏才开始出。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class NoticeDevCommands {

    private static final String LICH = "Drokag Tuzz";
    /** 演示用的三枚密语：Xata、Fass、Netra（和解密 HUD 的调试命令一样） */
    private static final int[] CARDS = {6, 2, 4};

    private NoticeDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc").then(Commands.literal("notice")
                .then(Commands.literal("demo").executes(ctx -> demo(ctx.getSource())))
                .then(Commands.literal("long").executes(ctx -> longOnes(ctx.getSource())))
                .then(Commands.literal("key")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .then(channel("bar", false))
                                .then(channel("chat", true))))
                .then(Commands.literal("hold")
                        .then(Commands.literal("on").executes(ctx -> hold(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> hold(ctx.getSource(), false))))
                .then(Commands.literal("freeze")
                        .then(Commands.literal("on").executes(ctx -> freeze(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> freeze(ctx.getSource(), false))))
                .then(Commands.literal("step")
                        .then(Commands.argument("ms", IntegerArgumentType.integer(1, 60000)).executes(ctx -> {
                            int ms = IntegerArgumentType.getInteger(ctx, "ms");
                            NoticeHud.debugStep(ms);
                            return ok(ctx.getSource(), "notice step " + ms + " ms, " + NoticeHud.debugState());
                        })))
                .then(Commands.literal("state").executes(ctx -> ok(ctx.getSource(), NoticeHud.debugState())))
                .then(Commands.literal("clear").executes(ctx -> {
                    NoticeHud.clear();
                    NoticeHud.debugHold(false);
                    NoticeHud.debugFreeze(false);
                    return ok(ctx.getSource(), "notice cleared");
                }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> channel(String name, boolean milestone) {
        return Commands.literal(name).then(Commands.argument("key", StringArgumentType.word())
                .executes(ctx -> key(ctx.getSource(), StringArgumentType.getString(ctx, "type"), milestone,
                        StringArgumentType.getString(ctx, "key"), ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> key(ctx.getSource(), StringArgumentType.getString(ctx, "type"), milestone,
                                StringArgumentType.getString(ctx, "key"), StringArgumentType.getString(ctx, "args")))));
    }

    private static int demo(CommandSourceStack src) {
        Component[] cards = new Component[CARDS.length];
        for (int i = 0; i < CARDS.length; i++) {
            cards[i] = Component.translatable(AbstractRequiemCard.getCard(CARDS[i]).getDescriptionId());
        }
        NoticeHud.push(KuvaPalette.LICH, false, "message.kuvalich.confiscation.stolen",
                new Object[]{LICH, Component.translatable("item.minecraft.diamond_sword")});
        NoticeHud.push(KuvaPalette.ACCENT, false, "message.kuvalich.deathResistance", new Object[]{120});
        NoticeHud.push(KuvaPalette.WARN, false, "kuvalich.mercy.deny.reach", new Object[0]);
        NoticeHud.push(KuvaPalette.SUCCESS, true, "message.kuvalich.confiscation.return", new Object[]{12});
        NoticeHud.push(KuvaPalette.SUCCESS, true, "message.kuvalich.minimumLevelWeapon", new Object[]{33});
        NoticeHud.push(KuvaPalette.SUCCESS, true, "message.kuvalich.maximumLevelWeapon", new Object[]{40});
        NoticeHud.push(KuvaPalette.ACCENT, true, "message.kuvalich.mercy.oull",
                new Object[]{Component.translatable(KuvaLichItems.OULL_CARD.get().getDescriptionId())});
        NoticeHud.push(KuvaPalette.ACCENT, true, "message.kuvalich.requiemRiddleAnswer", (Object[]) cards);
        NoticeHud.push(KuvaPalette.MUTED, false, "message.kuvalich.ultimatum.no_lich", new Object[0]);
        NoticeHud.push(KuvaPalette.LICH, false, "message.kuvalich.ultimatum.called", new Object[]{LICH});
        return ok(src, "notice demo: 10 pushed, " + NoticeHud.debugState());
    }

    private static int longOnes(CommandSourceStack src) {
        NoticeHud.push(KuvaPalette.WARN, true, "kuvalich.guide.inventory_full", new Object[0]);
        NoticeHud.push(KuvaPalette.LICH, false, "message.kuvalich.confiscation.stolen",
                new Object[]{"Vengeful Drokag Tuzz the Unbroken", Component.literal("Kuva Bramma of the Seventh Requiem")});
        return ok(src, "notice long: 2 pushed, " + NoticeHud.debugState());
    }

    private static int key(CommandSourceStack src, String type, boolean milestone, String key, String args) {
        int rgb = switch (type.toLowerCase(Locale.ROOT)) {
            case "lich" -> KuvaPalette.LICH;
            case "success" -> KuvaPalette.SUCCESS;
            case "danger" -> KuvaPalette.DANGER;
            case "warn" -> KuvaPalette.WARN;
            case "progress" -> KuvaPalette.PROGRESS;
            case "accent" -> KuvaPalette.ACCENT;
            case "muted" -> KuvaPalette.MUTED;
            default -> -1;
        };
        if (rgb < 0) {
            return ok(src, "notice: unknown type " + type + " (lich/success/danger/warn/progress/accent/muted)");
        }
        Object[] a = args.isEmpty() ? new Object[0] : (Object[]) args.split("\\|", -1);
        NoticeHud.push(rgb, milestone, key, a);
        return ok(src, "notice " + type + (milestone ? " chat " : " bar ") + key + ", " + NoticeHud.debugState());
    }

    private static int hold(CommandSourceStack src, boolean on) {
        NoticeHud.debugHold(on);
        return ok(src, "notice hold " + (on ? "on" : "off"));
    }

    private static int freeze(CommandSourceStack src, boolean on) {
        NoticeHud.debugFreeze(on);
        return ok(src, "notice freeze " + (on ? "on" : "off"));
    }

    private static int ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
