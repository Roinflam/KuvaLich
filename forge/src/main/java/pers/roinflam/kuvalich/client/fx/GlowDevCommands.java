package pers.roinflam.kuvalich.client.fx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.client.gui.hud.LichEdgePulse;
import pers.roinflam.kuvalich.client.lich.LichRenderState;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Locale;

/**
 * 开发环境里的客户端调试命令（正式版不注册），对准星指着的实体（32 格内）调发光层、淡入淡出、描边闪：
 *
 * <ul>
 *   <li>{@code /kuvafxc glow boost <倍数> <毫秒>}：叠一段提亮；</li>
 *   <li>{@code /kuvafxc glow mode <normal|downed|dissolve>}：切换发光节奏；</li>
 *   <li>{@code /kuvafxc glow sustain <倍数>}：持续乘子（1 = 去掉）；</li>
 *   <li>{@code /kuvafxc glow info}：此刻的乘子、节奏、透明度；</li>
 *   <li>{@code /kuvafxc fade <in|out> <毫秒>}、{@code /kuvafxc fade hide}：本体淡入 / 淡出 / 立刻藏起；</li>
 *   <li>{@code /kuvafxc rim flash <毫秒>}：奴仆描边闪一下；</li>
 *   <li>{@code /kuvafxc edgepulse [near]}：屏幕边缘提示演一遍（不看选项开没开、附近有没有玄骸），带 {@code near} 按近距离的节奏。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class GlowDevCommands {

    private GlowDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> mode = Commands.literal("mode");
        for (KuvaGlow.Mode m : KuvaGlow.Mode.values()) {
            mode.then(Commands.literal(m.name().toLowerCase(Locale.ROOT)).executes(ctx -> mode(ctx.getSource(), m)));
        }
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("glow")
                        .then(Commands.literal("boost")
                                .then(Commands.argument("factor", FloatArgumentType.floatArg(1.0F, KuvaGlow.MAX))
                                        .then(Commands.argument("ms", IntegerArgumentType.integer(1, 60000))
                                                .executes(ctx -> boost(ctx.getSource(),
                                                        FloatArgumentType.getFloat(ctx, "factor"),
                                                        IntegerArgumentType.getInteger(ctx, "ms"))))))
                        .then(mode)
                        .then(Commands.literal("sustain")
                                .then(Commands.argument("factor", FloatArgumentType.floatArg(0.0F, KuvaGlow.MAX))
                                        .executes(ctx -> sustain(ctx.getSource(), FloatArgumentType.getFloat(ctx, "factor")))))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource()))))
                .then(Commands.literal("fade")
                        .then(Commands.literal("in")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(0, 60000))
                                        .executes(ctx -> fade(ctx.getSource(), true, IntegerArgumentType.getInteger(ctx, "ms")))))
                        .then(Commands.literal("out")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(0, 60000))
                                        .executes(ctx -> fade(ctx.getSource(), false, IntegerArgumentType.getInteger(ctx, "ms")))))
                        .then(Commands.literal("hide").executes(ctx -> hide(ctx.getSource()))))
                .then(Commands.literal("rim")
                        .then(Commands.literal("flash")
                                .then(Commands.argument("ms", IntegerArgumentType.integer(1, 10000))
                                        .executes(ctx -> rimFlash(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "ms"))))))
                .then(Commands.literal("edgepulse")
                        .executes(ctx -> edge(ctx.getSource(), false))
                        .then(Commands.literal("near").executes(ctx -> edge(ctx.getSource(), true)))));
    }

    private static int boost(CommandSourceStack src, float factor, int ms) {
        Entity e = target(src);
        if (e == null) {
            return 0;
        }
        KuvaGlow.boost(e.getId(), factor, ms);
        src.sendSuccess(() -> Component.literal("glow boost x" + factor + " for " + ms + " ms on " + e.getId()), false);
        return 1;
    }

    private static int mode(CommandSourceStack src, KuvaGlow.Mode m) {
        Entity e = target(src);
        if (e == null) {
            return 0;
        }
        KuvaGlow.mode(e.getId(), m);
        src.sendSuccess(() -> Component.literal("glow mode " + m + " on " + e.getId()), false);
        return 1;
    }

    private static int sustain(CommandSourceStack src, float factor) {
        Entity e = target(src);
        if (e == null) {
            return 0;
        }
        KuvaGlow.sustain(e.getId(), factor);
        src.sendSuccess(() -> Component.literal("glow sustain x" + factor + " on " + e.getId()), false);
        return 1;
    }

    private static int info(CommandSourceStack src) {
        Entity e = target(src);
        if (e == null) {
            return 0;
        }
        float pt = Minecraft.getInstance().getFrameTime();
        String s = String.format(Locale.ROOT, "id %d: glow x%.3f (%s), alpha %.3f%s", e.getId(),
                KuvaGlow.multiplier(e, pt), KuvaGlow.modeOf(e.getId()), LichRenderState.alpha(e, pt),
                LichRenderState.tracked(e.getId()) ? " (fading)" : "");
        src.sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    private static int fade(CommandSourceStack src, boolean in, int ms) {
        Entity e = target(src);
        if (e == null) {
            return 0;
        }
        if (in) {
            LichRenderState.fadeIn(e.getId(), ms);
        } else {
            LichRenderState.fadeOut(e.getId(), ms);
        }
        src.sendSuccess(() -> Component.literal("fade " + (in ? "in" : "out") + " " + ms + " ms on " + e.getId()), false);
        return 1;
    }

    private static int hide(CommandSourceStack src) {
        Entity e = target(src);
        if (e == null) {
            return 0;
        }
        LichRenderState.hide(e.getId());
        src.sendSuccess(() -> Component.literal("hide " + e.getId() + " (fade in to show again)"), false);
        return 1;
    }

    private static int rimFlash(CommandSourceStack src, int ms) {
        Entity e = target(src);
        if (e == null) {
            return 0;
        }
        KuvaThrallRim.flash(e.getId(), ms);
        src.sendSuccess(() -> Component.literal("rim flash " + ms + " ms on " + e.getId()), false);
        return 1;
    }

    private static int edge(CommandSourceStack src, boolean near) {
        LichEdgePulse.test(near);
        src.sendSuccess(() -> Component.literal("edge pulse" + (near ? " (near)" : "")), false);
        return 1;
    }

    /** 准星指着的实体（32 格内，被方块挡住的不算）；没有时回一句失败 */
    private static Entity target(CommandSourceStack src) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(32.0D));
        HitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDist = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceTo(block.getLocation());
        AABB box = player.getBoundingBox().expandTowards(look.scale(32.0D)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mc.level, player, eye, end, box,
                ent -> !ent.isSpectator() && ent.isPickable());
        if (hit == null || eye.distanceTo(hit.getLocation()) > blockDist) {
            src.sendFailure(Component.literal("look at an entity within 32 blocks"));
            return null;
        }
        return hit.getEntity();
    }
}
