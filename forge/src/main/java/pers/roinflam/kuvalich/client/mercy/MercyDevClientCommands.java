package pers.roinflam.kuvalich.client.mercy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.mercy.MercyPlan;
import pers.roinflam.kuvalich.mercy.MercyVariant;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;
import java.util.Locale;

/**
 * 开发环境里核对处决演出用的客户端命令，正式版里没有（{@link FMLEnvironment#production} 为真时不注册）
 *
 * <ul>
 *   <li>{@code /kuvafxc mercyat <毫秒>}：把正在进行的处决冻在某一刻，镜头、姿态、刀模、界面都停在那一帧，
 *       逐个时刻截图核对用；{@code /kuvafxc mercyat off} 从冻住的那一刻接着走。</li>
 *   <li>{@code /kuvafxc mercytest <套路> <结果>}：不经服务端，本地直接对准星指着的（或 16 格内最近的）赤毒实体
 *       起一场处决，只演客户端这一半（服务端不锁目标、不结算）。结果是第三次处决的 {@code success / fail1 / fail2 / fail3}，
 *       第一、二次处决全对的 {@code adv1 / adv2}（挣开站起），第一、二次处决判错的 {@code k1fail1 / k2fail1 / k2fail2}；
 *       奴仆套路忽略结果。配合 {@code mercyat} 可以不等真打倒一只就看任意一刻。</li>
 *   <li>{@code /kuvafxc mercybeats}：把每套每个结果按 10 ms 导成文本（{@link MercyBeatsDump}），写到游戏目录的
 *       {@code mercy-beats.txt}，改节拍前后对比用。</li>
 *   <li>{@code /kuvafxc mercyorbit <角度> <距离> <高度>}：处决期间镜头换到绕目标的固定机位，平视（角度 0 在处决者那一侧，
 *       90 是侧面），要先切第三人称才画得出处决者；奴仆处决不切镜头、视角锁着，核对刀口靠它。{@code off} 关掉。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MercyDevClientCommands {

    private static final String[] OUTCOMES = {"success", "fail1", "fail2", "fail3", "adv1", "adv2", "k1fail1", "k2fail1",
            "k2fail2"};

    private MercyDevClientCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("mercyat")
                        .then(Commands.literal("off").executes(ctx -> {
                            boolean any = MercyClient.unfreeze();
                            reply(ctx, any ? "mercy timeline resumed" : "nothing frozen");
                            return any ? 1 : 0;
                        }))
                        .then(Commands.argument("ms", IntegerArgumentType.integer(0, 20000)).executes(ctx -> {
                            int ms = IntegerArgumentType.getInteger(ctx, "ms");
                            boolean ok = MercyClient.freeze(ms);
                            reply(ctx, ok ? "mercy timeline frozen at " + ms + " ms" : "no execution in progress");
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("mercybeats").executes(ctx -> {
                    java.nio.file.Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("mercy-beats.txt");
                    try {
                        MercyBeatsDump.write(file);
                    } catch (java.io.IOException ex) {
                        reply(ctx, "mercy beats: " + ex.getMessage());
                        return 0;
                    }
                    reply(ctx, "mercy beats -> " + file);
                    return 1;
                }))
                .then(Commands.literal("mercyorbit")
                        .then(Commands.literal("off").executes(ctx -> {
                            MercyCamera.devOrbitDist = 0.0F;
                            reply(ctx, "mercy orbit off");
                            return 1;
                        }))
                        .then(Commands.argument("angle", FloatArgumentType.floatArg(-360.0F, 360.0F))
                                .then(Commands.argument("dist", FloatArgumentType.floatArg(0.5F, 16.0F))
                                        .then(Commands.argument("height", FloatArgumentType.floatArg(0.0F, 6.0F))
                                                .executes(ctx -> {
                                                    MercyCamera.devOrbitAngle = FloatArgumentType.getFloat(ctx, "angle");
                                                    MercyCamera.devOrbitDist = FloatArgumentType.getFloat(ctx, "dist");
                                                    MercyCamera.devOrbitHeight = FloatArgumentType.getFloat(ctx, "height");
                                                    reply(ctx, "mercy orbit " + MercyCamera.devOrbitAngle + "° "
                                                            + MercyCamera.devOrbitDist + " m, h " + MercyCamera.devOrbitHeight);
                                                    return 1;
                                                })))))
                .then(Commands.literal("mercytest")
                        .then(Commands.argument("variant", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(MercyVariant.values()).map(v -> v.name().toLowerCase(Locale.ROOT)), b))
                                .executes(ctx -> test(ctx, "success"))
                                .then(Commands.argument("outcome", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(OUTCOMES, b))
                                        .executes(ctx -> test(ctx, StringArgumentType.getString(ctx, "outcome")))))));
    }

    private static int test(CommandContext<CommandSourceStack> ctx, String outcome) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "variant");
        MercyVariant variant = null;
        for (MercyVariant v : MercyVariant.values()) {
            if (v.name().equalsIgnoreCase(name)) {
                variant = v;
            }
        }
        if (variant == null) {
            reply(ctx, "unknown variant: " + name);
            return 0;
        }
        int stabs = 3;
        int prefix;
        switch (outcome.toLowerCase(Locale.ROOT)) {
            case "success" -> prefix = 3;
            case "fail1" -> prefix = 0;
            case "fail2" -> prefix = 1;
            case "fail3" -> prefix = 2;
            case "adv1" -> {
                stabs = 1;
                prefix = 1;
            }
            case "adv2" -> {
                stabs = 2;
                prefix = 2;
            }
            case "k1fail1" -> {
                stabs = 1;
                prefix = 0;
            }
            case "k2fail1" -> {
                stabs = 2;
                prefix = 0;
            }
            case "k2fail2" -> {
                stabs = 2;
                prefix = 1;
            }
            default -> {
                reply(ctx, "unknown outcome: " + outcome);
                return 0;
            }
        }
        if (variant.thrall) {
            stabs = 1;
            prefix = 0;
        }
        Entity target = pickTarget(mc, player);
        if (target == null) {
            reply(ctx, "no kuva entity in sight");
            return 0;
        }
        boolean success = !variant.thrall && stabs == 3 && prefix == 3;
        int[] cards = !variant.thrall ? new int[]{0, 1, 2} : new int[]{-1, -1, -1};
        int[] left = !variant.thrall ? new int[]{3, 2, 1} : new int[]{0, 0, 0};
        MercyPlan.Plan plan = new MercyPlan.Plan(stabs, prefix, variant, cards, left, success, success ? 1 : 0, true, false);
        // 站位传目标自己的位置，由 begin 按处决者现在站的方向和套路的站位距离算
        MercyClient.begin(target, player.getId(), MercyPlan.encode(plan), target.position());
        reply(ctx, "mercy test: " + variant.name().toLowerCase(Locale.ROOT) + " / " + outcome + " on #" + target.getId());
        return 1;
    }

    /** 准星指着的赤毒实体；没指着就取 16 格内最近的一只 */
    @Nullable
    private static Entity pickTarget(Minecraft mc, LocalPlayer player) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult eh && eh.getType() == HitResult.Type.ENTITY && eh.getEntity() instanceof AbstractKuva) {
            return eh.getEntity();
        }
        Entity best = null;
        double bestD = 16.0D * 16.0D;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof AbstractKuva && e.isAlive()) {
                double d = e.distanceToSqr(player);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }
}
