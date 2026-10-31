package pers.roinflam.kuvalich.comm;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 通讯窗的调试命令，只在开发环境注册（{@link FMLEnvironment#production} 为真时不注册），挂在 {@code /kuvafx} 下面
 *
 * <ul>
 *   <li>{@code /kuvafx comm <lotus|ordis|lich> <语言键> [标志位]}：给自己发一句。语言值里的 %1$s 填玄骸名、%2$s 填自己的名字；
 *       玄骸那一句用准星指着的玄骸（有的话）的名字和外观；</li>
 *   <li>{@code /kuvafx comm <lotus|ordis|lich> text <整句>}：直接给文字（调排版、测超长句），{@code textf <标志位> <整句>} 带标志位；</li>
 *   <li>{@code /kuvafx comm demo}：依次 Lotus → 玄骸（到场）→ 玄骸接着说 → Ordis（带一段故障），看换人间隔和连说。</li>
 * </ul>
 * <p>标志位是 {@link KuvaComm} 里那几个数加起来：1 插队、2 到场、4 随机嘲讽、8 同时进聊天栏、16 不嘟囔。</p>
 *
 * <p>通用代码：命令在服务端执行，只经 {@link KuvaComm} 发包，不碰客户端类。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class CommDevCommand {

    /** 找准星指着的玄骸的距离 */
    private static final double PICK_RANGE = 64.0D;

    /** 演示的几句，按服务端 tick 排着发 */
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(long dueTick, UUID player, Runnable send) {
    }

    private CommDevCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        LiteralArgumentBuilder<CommandSourceStack> comm = Commands.literal("comm")
                .then(Commands.literal("demo").executes(CommDevCommand::demo));
        for (CommSpeaker who : CommSpeaker.values()) {
            String name = who.name().toLowerCase(Locale.ROOT);
            comm.then(Commands.literal(name)
                    .then(Commands.literal("text")
                            .then(Commands.argument("line", StringArgumentType.greedyString())
                                    .executes(ctx -> sendText(ctx, who, 0))))
                    .then(Commands.literal("textf")
                            .then(Commands.argument("flags", IntegerArgumentType.integer(0, 31))
                                    .then(Commands.argument("line", StringArgumentType.greedyString())
                                            .executes(ctx -> sendText(ctx, who, IntegerArgumentType.getInteger(ctx, "flags"))))))
                    .then(Commands.argument("key", StringArgumentType.word())
                            .executes(ctx -> sendKey(ctx, who, 0))
                            .then(Commands.argument("flags", IntegerArgumentType.integer(0, 31))
                                    .executes(ctx -> sendKey(ctx, who, IntegerArgumentType.getInteger(ctx, "flags"))))));
        }
        // 和现有的 /kuvafx 根节点合并（同名字面量节点 Brigadier 会并到一起）
        event.getDispatcher().register(Commands.literal("kuvafx")
                .requires(s -> s.hasPermission(2))
                .then(comm));
    }

    private static int sendKey(CommandContext<CommandSourceStack> ctx, CommSpeaker who, int flags)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(ctx, "key");
        LivingEntity lich = who == CommSpeaker.LICH ? pickLich(sp) : null;
        Component lichName = lich != null ? lich.getDisplayName() : Component.translatable(CommSpeaker.LICH.nameKey());
        send(sp, who, lich, Component.translatable(key, lichName, sp.getDisplayName()), flags);
        return 1;
    }

    private static int sendText(CommandContext<CommandSourceStack> ctx, CommSpeaker who, int flags)
            throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        String line = StringArgumentType.getString(ctx, "line");
        LivingEntity lich = who == CommSpeaker.LICH ? pickLich(sp) : null;
        send(sp, who, lich, Component.literal(line), flags);
        return 1;
    }

    private static void send(ServerPlayer sp, CommSpeaker who, @Nullable LivingEntity lich, Component line, int flags) {
        if (who == CommSpeaker.LICH) {
            KuvaComm.sayLich(sp, lich, line, flags, 0);
        } else {
            KuvaComm.say(sp, who, null, line, flags, null, 0);
        }
    }

    /**
     * 演示：四句的发送时刻错开，免得在客户端队列里等过 8 s 被丢。Lotus 这句约 3.6 s 说完，玄骸在它之后 1.8 s 开窗；
     * 玄骸第二句在第一句说完之前到，接着说；Ordis 在玄骸说完之前到，换人再开窗
     */
    private static int demo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        MinecraftServer server = sp.getServer();
        if (server == null) {
            return 0;
        }
        long now = server.getTickCount();
        UUID id = sp.getUUID();
        PENDING.removeIf(p -> p.player().equals(id));
        PENDING.add(new Pending(now, id, () -> withPlayer(server, id, p ->
                KuvaComm.say(p, CommSpeaker.LOTUS, null, Component.literal("Tenno，通讯测试：这是第一句。"), 0, null, 0))));
        PENDING.add(new Pending(now + 4, id, () -> withPlayer(server, id, p ->
                KuvaComm.sayLich(p, pickLich(p), Component.literal("闻到了！这是到场的那一句。"), KuvaComm.ARRIVAL, 0))));
        PENDING.add(new Pending(now + 100, id, () -> withPlayer(server, id, p ->
                KuvaComm.sayLich(p, pickLich(p), Component.literal("同一个人接着说，窗不收。"), 0, 0))));
        PENDING.add(new Pending(now + 180, id, () -> withPlayer(server, id, p ->
                KuvaComm.say(p, CommSpeaker.ORDIS, null, Component.literal("Ordis 收到，<x>测--出错--</x>测试完毕。"), 0, null, 0))));
        ctx.getSource().sendSuccess(() -> Component.literal("comm demo: 4 lines over 9 s"), false);
        return 1;
    }

    private static void withPlayer(MinecraftServer server, UUID id, java.util.function.Consumer<ServerPlayer> action) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            action.accept(p);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        Iterator<Pending> it = PENDING.iterator();
        List<Runnable> due = new ArrayList<>(2);
        while (it.hasNext()) {
            Pending p = it.next();
            if (now >= p.dueTick()) {
                it.remove();
                due.add(p.send());
            }
        }
        due.forEach(Runnable::run);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
    }

    /** 准星指着的玄骸（中间没被方块挡住）；没有返回 null */
    @Nullable
    private static LivingEntity pickLich(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(PICK_RANGE));
        HitResult blockHit = sp.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double blockDist = blockHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceTo(blockHit.getLocation());
        AABB box = sp.getBoundingBox().expandTowards(look.scale(PICK_RANGE)).inflate(1.0D);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(sp.level(), sp, eye, end, box,
                e -> e instanceof KuvaMasterEntity && e.isAlive());
        if (eh == null || eye.distanceTo(eh.getLocation()) > blockDist) {
            return null;
        }
        Entity e = eh.getEntity();
        return e instanceof LivingEntity le ? le : null;
    }
}
