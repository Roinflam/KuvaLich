package pers.roinflam.kuvalich.client.gui.hud;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.client.gui.screens.inventory.RequiemGateScreen;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 开发环境里的客户端调试命令（正式版不注册）：在本地直接驱动解密 HUD 和灭骸之扉的三角，截图、对时间轴用，不经过服务端
 *
 * <ul>
 *   <li>{@code /kuvafxc decrypt <进度 0~1> <已揭示 0~3>}：按收到一个普通进度包的样子弹一次（已揭示 3 是三枚并排）；</li>
 *   <li>{@code /kuvafxc decrypt reveal <卡 id> [阶段 1~3]}：揭示一枚（阶段 3 是揭示第三枚、换成三枚并排）；</li>
 *   <li>{@code /kuvafxc decrypt broken [名字]}：破解（三张卡扣完剩 2、1、0 次）；
 *       {@code /kuvafxc decrypt broken uses <剩几次> <剩几次> <剩几次>}：指定三枚各自那张卡扣完剩几次（−1 = 没有卡）；</li>
 *   <li>{@code /kuvafxc decrypt freeze <毫秒>|off}：把画面定在这一版收到包后的第几毫秒，逐帧截图对淡入曲线；</li>
 *   <li>{@code /kuvafxc decrypt hold on|off}：一直停着不淡出；</li>
 *   <li>{@code /kuvafxc decrypt boss <底 y>|off}：假装屏幕上方有 Boss 条、底在这里（GUI 坐标），看 HUD 往下让；</li>
 *   <li>{@code /kuvafxc decrypt gate <进度 0~1> <已揭示 0~3>|off}：灭骸之扉里的三角改画这组数据；</li>
 *   <li>{@code /kuvafxc decrypt clear}：收掉 HUD、取消以上所有覆盖。</li>
 * </ul>
 *
 * <p>演示用的三枚密语固定是 Xata、Fass、Netra，已揭示 n 条就亮前 n 枚。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class DecryptionDevCommands {

    private static final int[] DEMO = {6, 2, 4};
    private static final int THRESHOLD = 100;
    /** 破解演示的三张卡扣完剩几次：一张还剩 2、一张剩 1、一张这次用完 */
    private static final int[] DEMO_USES = {2, 1, 0};

    private DecryptionDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc").then(Commands.literal("decrypt")
                .then(Commands.argument("progress", FloatArgumentType.floatArg(0.0F, 1.0F))
                        .then(Commands.argument("revealed", IntegerArgumentType.integer(0, 3)).executes(ctx -> progress(
                                ctx.getSource(),
                                FloatArgumentType.getFloat(ctx, "progress"),
                                IntegerArgumentType.getInteger(ctx, "revealed")))))
                .then(Commands.literal("reveal")
                        .then(Commands.argument("card", IntegerArgumentType.integer(0, 15))
                                .executes(ctx -> reveal(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "card"), 1))
                                .then(Commands.argument("stage", IntegerArgumentType.integer(1, 3)).executes(ctx -> reveal(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "card"),
                                        IntegerArgumentType.getInteger(ctx, "stage"))))))
                .then(Commands.literal("broken")
                        .executes(ctx -> broken(ctx.getSource(), "Drokag Tuzz", DEMO_USES))
                        .then(Commands.literal("uses")
                                .then(Commands.argument("u1", IntegerArgumentType.integer(-1, 3))
                                        .then(Commands.argument("u2", IntegerArgumentType.integer(-1, 3))
                                                .then(Commands.argument("u3", IntegerArgumentType.integer(-1, 3))
                                                        .executes(ctx -> broken(ctx.getSource(), "Drokag Tuzz", new int[]{
                                                                IntegerArgumentType.getInteger(ctx, "u1"),
                                                                IntegerArgumentType.getInteger(ctx, "u2"),
                                                                IntegerArgumentType.getInteger(ctx, "u3")}))))))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> broken(ctx.getSource(), StringArgumentType.getString(ctx, "name"), DEMO_USES))))
                .then(Commands.literal("freeze")
                        .then(Commands.literal("off").executes(ctx -> freeze(ctx.getSource(), -1L)))
                        .then(Commands.argument("ms", IntegerArgumentType.integer(0, 60000))
                                .executes(ctx -> freeze(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "ms")))))
                .then(Commands.literal("hold")
                        .then(Commands.literal("on").executes(ctx -> hold(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> hold(ctx.getSource(), false))))
                .then(Commands.literal("boss")
                        .then(Commands.literal("off").executes(ctx -> boss(ctx.getSource(), -1)))
                        .then(Commands.argument("bottom", IntegerArgumentType.integer(0, 4000))
                                .executes(ctx -> boss(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "bottom")))))
                .then(Commands.literal("gate")
                        .then(Commands.literal("off").executes(ctx -> {
                            RequiemGateScreen.debugClear();
                            return ok(ctx.getSource(), "gate override off");
                        }))
                        .then(Commands.argument("progress", FloatArgumentType.floatArg(0.0F, 1.0F))
                                .then(Commands.argument("revealed", IntegerArgumentType.integer(0, 3)).executes(ctx -> gate(
                                        ctx.getSource(),
                                        FloatArgumentType.getFloat(ctx, "progress"),
                                        IntegerArgumentType.getInteger(ctx, "revealed"))))))
                .then(Commands.literal("clear").executes(ctx -> {
                    DecryptionHudOverlay.debugClear();
                    DecryptionHudOverlay.debugFreeze(-1L);
                    DecryptionHudOverlay.debugHold(false);
                    DecryptionHudOverlay.debugBossBottom(-1);
                    RequiemGateScreen.debugClear();
                    return ok(ctx.getSource(), "decrypt hud cleared");
                }))));
    }

    private static int[] demo(int revealed) {
        int[] r = {-1, -1, -1};
        for (int i = 0; i < revealed && i < 3; i++) {
            r[i] = DEMO[i];
        }
        return r;
    }

    private static int progress(CommandSourceStack src, float p, int revealed) {
        int amount = Math.round(p * THRESHOLD);
        DecryptionHudOverlay.show(DecryptionHudPacket.EVENT_PROGRESS, revealed, amount,
                revealed >= 3 ? -1 : THRESHOLD, 1, -1, demo(revealed), "", null);
        return ok(src, "decrypt progress " + p + " revealed " + revealed);
    }

    private static int reveal(CommandSourceStack src, int card, int stage) {
        int[] r = demo(stage);
        r[stage - 1] = card;
        DecryptionHudOverlay.show(DecryptionHudPacket.EVENT_REVEAL, stage, 0, stage >= 3 ? -1 : THRESHOLD, 1, card, r, "", null);
        return ok(src, "decrypt reveal card " + card + " stage " + stage);
    }

    private static int broken(CommandSourceStack src, String name, int[] uses) {
        DecryptionHudOverlay.show(DecryptionHudPacket.EVENT_COMPLETE, 0, 0, THRESHOLD, 0, -1, demo(3), name, uses);
        return ok(src, "decrypt broken " + name + " uses " + uses[0] + " " + uses[1] + " " + uses[2]);
    }

    private static int freeze(CommandSourceStack src, long ms) {
        DecryptionHudOverlay.debugFreeze(ms);
        return ok(src, ms < 0L ? "decrypt freeze off" : "decrypt freeze at " + ms + " ms");
    }

    private static int hold(CommandSourceStack src, boolean on) {
        DecryptionHudOverlay.debugHold(on);
        return ok(src, "decrypt hold " + (on ? "on" : "off"));
    }

    private static int boss(CommandSourceStack src, int bottom) {
        DecryptionHudOverlay.debugBossBottom(bottom);
        return ok(src, bottom < 0 ? "decrypt boss off" : "decrypt boss bottom " + bottom);
    }

    private static int gate(CommandSourceStack src, float p, int revealed) {
        RequiemGateScreen.debugOverride(demo(revealed), revealed >= 3 ? 1.0F : p);
        return ok(src, "gate override progress " + p + " revealed " + revealed);
    }

    private static int ok(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
