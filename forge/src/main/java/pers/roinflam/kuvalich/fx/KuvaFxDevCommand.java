package pers.roinflam.kuvalich.fx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;

/**
 * 开发环境里的调试命令，正式版里没有（{@link FMLEnvironment#production} 为真时不注册）
 *
 * <ul>
 *   <li>{@code /kuvafx <效果名或编号> [a] [b]}：以准星指着的实体（16 格内）或方块为原点，
 *       走这个效果正式的发送方式发一个效果包；什么都没指着就以自己为原点。</li>
 *   <li>{@code /kuvafx lag <毫秒>}：服务端每个 tick 末尾多睡这么久，用来模拟服务器掉 TPS，0 关闭。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class KuvaFxDevCommand {

    /** 服务端每 tick 额外睡眠的毫秒数（只有调试命令能设，正式版里恒为 0） */
    private static volatile int lagMs = 0;

    private KuvaFxDevCommand() {
    }

    /** 发送方式：附近范围 / 追踪实体 / 只给一个玩家 */
    private enum Mode {
        NEAR, TRACKING, PLAYER
    }

    private record Route(Mode mode, double radius, boolean entity) {
        static Route near(double radius, boolean entity) {
            return new Route(Mode.NEAR, radius, entity);
        }

        static Route tracking() {
            return new Route(Mode.TRACKING, 0.0D, true);
        }

        static Route player() {
            return new Route(Mode.PLAYER, 0.0D, false);
        }
    }

    /** 每个效果正式的发送方式（范围按声音的衰减距离定） */
    private static Route routeOf(FxId id) {
        return switch (id) {
            case LICH_SUMMON, REQUIEM_SUCCESS, REQUIEM_FAIL -> Route.near(64.0D, true);
            case LICH_DEATH, LICH_DOWNED, MERCY_BEGIN, MERCY_STAB, LICH_EXPIRE, LICH_ARRIVE, LICH_DEPART -> Route.near(48.0D, true);
            case KUVA_STATE -> Route.near(32.0D, true);
            case CONFISCATE -> Route.near(16.0D, false);
            case GAS_CLOUD -> Route.near(32.0D, false);
            case MODULE_REVEAL -> Route.near(16.0D, true);
            case LICH_COMM, RIDDLE_SOLVED, RANK_UP -> Route.player();
            case DEV_SHOWCASE -> Route.near(64.0D, false);
            default -> Route.tracking();
        };
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        ArgumentBuilder<CommandSourceStack, ?> lag = Commands.literal("lag")
                .then(Commands.argument("ms", IntegerArgumentType.integer(0, 2000)).executes(ctx -> {
                    lagMs = IntegerArgumentType.getInteger(ctx, "ms");
                    ctx.getSource().sendSuccess(() -> Component.literal("server tick lag = " + lagMs + " ms"), false);
                    return lagMs;
                }));
        d.register(Commands.literal("kuvafx")
                .requires(s -> s.hasPermission(2))
                .then(lag)
                .then(Commands.argument("fx", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(FxId.values()).map(Enum::name), b))
                        .executes(ctx -> send(ctx, 0, 0))
                        .then(Commands.argument("a", IntegerArgumentType.integer())
                                .executes(ctx -> send(ctx, IntegerArgumentType.getInteger(ctx, "a"), 0))
                                .then(Commands.argument("b", IntegerArgumentType.integer())
                                        .executes(ctx -> send(ctx, IntegerArgumentType.getInteger(ctx, "a"),
                                                IntegerArgumentType.getInteger(ctx, "b")))))));
    }

    private static FxId parse(String s) {
        for (FxId f : FxId.values()) {
            if (f.name().equalsIgnoreCase(s)) {
                return f;
            }
        }
        try {
            return FxId.byId(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int send(CommandContext<CommandSourceStack> ctx, int a, int b) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer sp = src.getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "fx");
        FxId id = parse(name);
        if (id == null) {
            src.sendFailure(Component.literal("unknown fx: " + name));
            return 0;
        }
        // 找准星指着的东西：16 格内的实体优先，其次方块，都没有就以自己为原点
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(16.0D));
        ServerLevel level = sp.serverLevel();
        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, sp));
        double blockDist = blockHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceTo(blockHit.getLocation());
        AABB box = sp.getBoundingBox().expandTowards(look.scale(16.0D)).inflate(1.0D);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, sp, eye, end, box,
                e -> !e.isSpectator() && e.isPickable());
        Entity target = null;
        Vec3 origin = sp.position().add(0.0D, sp.getBbHeight() * 0.5D, 0.0D);
        if (eh != null && eye.distanceTo(eh.getLocation()) <= blockDist) {
            target = eh.getEntity();
            origin = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        } else if (blockDist != Double.MAX_VALUE) {
            origin = blockHit.getLocation();
        }

        Route route = routeOf(id);
        switch (route.mode()) {
            case TRACKING -> KuvaFx.tracking(target != null ? target : sp, id, a, b);
            case NEAR -> KuvaFx.near(level, origin, route.radius(), id,
                    route.entity() && target != null ? target.getId() : -1, a, b);
            case PLAYER -> KuvaFx.player(sp, id, origin, -1, a, b);
        }
        src.sendSuccess(() -> Component.literal("fx " + id + " (" + route.mode() + ") a=" + a + " b=" + b), false);
        return 1;
    }

    /** 模拟掉 TPS：每个 tick 末尾多睡一会儿 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        int ms = lagMs;
        if (ms > 0 && event.phase == TickEvent.Phase.END) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
