package pers.roinflam.kuvalich.client.larvling;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 开发环境里调幼体外观的客户端命令（正式版不注册）：
 *
 * <ul>
 *   <li>{@code /kuvafxc larvling style [a|b]}：倒地时头顶武器展示用哪一种（甲 浮刃 / 乙 全息牌），不带参数报当前的；</li>
 *   <li>{@code /kuvafxc larvling grip [x y z rx ry rz scale]}：现场调手里武器的握法（拳心偏移、绕 X/Y/Z 转角、缩放），
 *       不带参数报当前值。换了幼体的几何以后拿它对一遍，调好的数抄回 {@link LarvlingWeaponLayer}。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LarvlingDevCommands {

    private static final String[] GRIP_ARGS = {"x", "y", "z", "rx", "ry", "rz", "scale"};

    private LarvlingDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        // 七个数一个接一个，最后一个带执行
        RequiredArgumentBuilder<CommandSourceStack, Float> grip = null;
        for (int i = GRIP_ARGS.length - 1; i >= 0; i--) {
            RequiredArgumentBuilder<CommandSourceStack, Float> arg = Commands.argument(GRIP_ARGS[i],
                    i == GRIP_ARGS.length - 1 ? FloatArgumentType.floatArg(0.05F, 8.0F) : FloatArgumentType.floatArg());
            if (grip == null) {
                arg.executes(LarvlingDevCommands::tuneGrip);
            } else {
                arg.then(grip);
            }
            grip = arg;
        }
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("larvling")
                        .then(Commands.literal("style")
                                .executes(ctx -> style(ctx.getSource(), -1))
                                .then(Commands.literal("a").executes(ctx -> style(ctx.getSource(), LarvlingOverhead.STYLE_FLOAT)))
                                .then(Commands.literal("b").executes(ctx -> style(ctx.getSource(), LarvlingOverhead.STYLE_CARD))))
                        .then(Commands.literal("grip")
                                .executes(ctx -> showGrip(ctx.getSource()))
                                .then(grip))));
    }

    private static int style(CommandSourceStack src, int style) {
        if (style >= 0) {
            LarvlingOverhead.style = style;
        }
        String name = LarvlingOverhead.style == LarvlingOverhead.STYLE_CARD ? "b (holo card)" : "a (floating weapon)";
        src.sendSuccess(() -> Component.literal("larvling overhead style: " + name), false);
        return 1;
    }

    private static int tuneGrip(CommandContext<CommandSourceStack> ctx) {
        float[] v = new float[GRIP_ARGS.length];
        for (int i = 0; i < v.length; i++) {
            v[i] = FloatArgumentType.getFloat(ctx, GRIP_ARGS[i]);
        }
        LarvlingWeaponLayer.tune(v[0], v[1], v[2], v[3], v[4], v[5], v[6]);
        return showGrip(ctx.getSource());
    }

    private static int showGrip(CommandSourceStack src) {
        String s = LarvlingWeaponLayer.describe();
        src.sendSuccess(() -> Component.literal("larvling weapon " + s), false);
        return 1;
    }
}
