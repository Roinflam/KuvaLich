package pers.roinflam.kuvalich.client.ephemera;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.ephemera.EphemeraLook;
import pers.roinflam.kuvalich.ephemera.EphemeraStateTable;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Comparator;
import java.util.List;

/**
 * 开发环境里的客户端调试命令（正式版不注册），只改本地的状态表，不经服务端：
 *
 * <ul>
 *   <li>{@code /kuvafxc ephemera demo <个数> [款式] [元素]}：离自己最近的这么多只活物（不含自己）都挂上幻纹，量同屏耗时用；</li>
 *   <li>{@code /kuvafxc ephemera self [款式] [元素]}：自己挂上（服务端下一次同步会盖掉）；</li>
 *   <li>{@code /kuvafxc ephemera clear}：清空整张表；</li>
 *   <li>{@code /kuvafxc ephemera stats [reset]}：最近 120 帧的渲染耗时、画了几个、几个四边形；</li>
 *   <li>{@code /kuvafxc ephemera view first|back|front}：切视角（截图用）。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class EphemeraDevCommands {

    private EphemeraDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc").then(Commands.literal("ephemera")
                .then(Commands.literal("demo")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> demo(ctx, null, null))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(ctx -> demo(ctx, StringArgumentType.getString(ctx, "type"), null))
                                        .then(Commands.argument("element", StringArgumentType.word())
                                                .executes(ctx -> demo(ctx, StringArgumentType.getString(ctx, "type"),
                                                        StringArgumentType.getString(ctx, "element")))))))
                .then(Commands.literal("self")
                        .executes(ctx -> self(ctx, null, null))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> self(ctx, StringArgumentType.getString(ctx, "type"), null))
                                .then(Commands.argument("element", StringArgumentType.word())
                                        .executes(ctx -> self(ctx, StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "element"))))))
                .then(Commands.literal("clear").executes(ctx -> {
                    EphemeraRenderer.clearAll();
                    ctx.getSource().sendSuccess(() -> Component.literal("ephemera table cleared"), false);
                    return 1;
                }))
                .then(Commands.literal("view")
                        .then(Commands.literal("first").executes(ctx -> view(ctx, CameraType.FIRST_PERSON)))
                        .then(Commands.literal("back").executes(ctx -> view(ctx, CameraType.THIRD_PERSON_BACK)))
                        .then(Commands.literal("front").executes(ctx -> view(ctx, CameraType.THIRD_PERSON_FRONT))))
                .then(Commands.literal("stats")
                        .executes(ctx -> {
                            String s = EphemeraRenderer.stats();
                            ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                            return 1;
                        })
                        .then(Commands.literal("reset").executes(ctx -> {
                            EphemeraRenderer.resetStats();
                            ctx.getSource().sendSuccess(() -> Component.literal("ephemera stats reset"), false);
                            return 1;
                        })))));
    }

    private static int view(CommandContext<CommandSourceStack> ctx, CameraType type) {
        Minecraft.getInstance().options.setCameraType(type);
        ctx.getSource().sendSuccess(() -> Component.literal("view " + type), false);
        return 1;
    }

    private static EphemeraLook look(int i, String type, String element) {
        List<EphemeraType> all = EphemeraType.all();
        String t = type != null ? type : all.get(i % all.size()).id();
        String el = element != null ? element : EphemeraType.DEFAULT_ELEMENT;
        return EphemeraLook.ofDefault(t, el);
    }

    private static int demo(CommandContext<CommandSourceStack> ctx, String type, String element) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0;
        }
        if (type != null && EphemeraType.byId(type) == null) {
            ctx.getSource().sendFailure(Component.literal("unknown ephemera " + type));
            return 0;
        }
        int count = IntegerArgumentType.getInteger(ctx, "count");
        List<LivingEntity> near = mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(48.0D),
                e -> e != mc.player);
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        int n = Math.min(count, near.size());
        for (int i = 0; i < n; i++) {
            EphemeraStateTable.CLIENT.put(near.get(i).getId(), look(i, type, element));
        }
        ctx.getSource().sendSuccess(() -> Component.literal("ephemera on " + n + " entities"), false);
        return n;
    }

    private static int self(CommandContext<CommandSourceStack> ctx, String type, String element) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        if (type != null && EphemeraType.byId(type) == null) {
            ctx.getSource().sendFailure(Component.literal("unknown ephemera " + type));
            return 0;
        }
        EphemeraStateTable.CLIENT.put(mc.player.getId(), look(0, type, element));
        ctx.getSource().sendSuccess(() -> Component.literal("ephemera on self"), false);
        return 1;
    }
}
