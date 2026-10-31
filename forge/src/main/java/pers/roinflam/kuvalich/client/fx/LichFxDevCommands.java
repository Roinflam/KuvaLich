package pers.roinflam.kuvalich.client.fx;

import com.mojang.brigadier.CommandDispatcher;
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
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.script.KuvaAnimFx;
import pers.roinflam.kuvalich.client.fx.script.LichFx;
import pers.roinflam.kuvalich.client.fx.script.RequiemFx;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Locale;

/**
 * 开发环境里的客户端调试命令（正式版不注册），对准星指着的实体（32 格内）在本地演一遍，录像、逐段核对用
 *
 * <ul>
 *   <li>{@code /kuvafxc arrive}：重放一次无主玄骸的登场（胸口光 + 发光层提亮），不管它是不是已经演过；</li>
 *   <li>{@code /kuvafxc dissolve <death|expire|escape|requiem|thrall|depart> [keep]}：演一段消散。客户端删不掉实体，
 *       演完后模型保持消失 3 秒再复原；带 {@code keep} 时演完立刻复原（同一只反复拍）。同一只正在演时再发会被忽略，日志里有一行；</li>
 *   <li>{@code /kuvafxc escape}：判错后的暖色冲击 + 逃走消散；</li>
 *   <li>{@code /kuvafxc blade <1|2|3> [last]}：怜悯之击刀口的小火花；</li>
 *   <li>{@code /kuvafxc swing [hit]}：挥砍声和扫击弧，带 {@code hit} 时同一刻再报一次命中。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class LichFxDevCommands {

    /** 不带 keep 时，演完后保持消失多久再复原 */
    private static final int HIDDEN_AFTER_MS = 3000;

    private LichFxDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> dissolve = Commands.literal("dissolve");
        for (LichFx.Kind kind : LichFx.Kind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT);
            dissolve.then(Commands.literal(name)
                    .executes(ctx -> dissolve(ctx.getSource(), kind, false))
                    .then(Commands.literal("keep").executes(ctx -> dissolve(ctx.getSource(), kind, true))));
        }
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("arrive").executes(ctx -> arrive(ctx.getSource())))
                .then(dissolve)
                .then(Commands.literal("escape").executes(ctx -> escape(ctx.getSource())))
                .then(Commands.literal("blade")
                        .then(Commands.argument("stab", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> blade(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "stab"), false))
                                .then(Commands.literal("last").executes(ctx ->
                                        blade(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "stab"), true)))))
                .then(Commands.literal("swing")
                        .executes(ctx -> swing(ctx.getSource(), false))
                        .then(Commands.literal("hit").executes(ctx -> swing(ctx.getSource(), true)))));
    }

    private static int arrive(CommandSourceStack src) {
        Entity e = target();
        if (!(e instanceof AbstractKuva k)) {
            src.sendFailure(Component.literal("look at a Kuva Lich or thrall within 32 blocks"));
            return 0;
        }
        LichFx.lichAlert(k);
        src.sendSuccess(() -> Component.literal("arrive on " + e.getId()), false);
        return 1;
    }

    private static int dissolve(CommandSourceStack src, LichFx.Kind kind, boolean keep) {
        Entity e = target();
        if (e == null) {
            src.sendFailure(Component.literal("look at an entity within 32 blocks"));
            return 0;
        }
        int id = e.getId();
        int len = LichFx.lengthMs(kind, e);
        LichFx.dissolve(e, kind);
        int restoreAt = len + (keep ? 200 : HIDDEN_AFTER_MS);
        KuvaFxDirector.start("dev dissolve restore", e.position(), FxTier.A).until(restoreAt)
                .at(restoreAt, () -> LichFx.forgetDissolve(id));
        src.sendSuccess(() -> Component.literal("dissolve " + kind + " on " + id + " (" + len + " ms"
                + (keep ? ", keep)" : ", restore after " + HIDDEN_AFTER_MS + " ms)")), false);
        return 1;
    }

    private static int escape(CommandSourceStack src) {
        Entity e = target();
        if (e == null) {
            src.sendFailure(Component.literal("look at an entity within 32 blocks"));
            return 0;
        }
        int id = e.getId();
        RequiemFx.escape(e, FxPalette.KUVA_HOT);
        LichFx.dissolve(e, LichFx.Kind.ESCAPE);
        int restoreAt = LichFx.lengthMs(LichFx.Kind.ESCAPE, e) + HIDDEN_AFTER_MS;
        KuvaFxDirector.start("dev escape restore", e.position(), FxTier.A).until(restoreAt)
                .at(restoreAt, () -> LichFx.forgetDissolve(id));
        src.sendSuccess(() -> Component.literal("escape on " + id), false);
        return 1;
    }

    private static int blade(CommandSourceStack src, int stab, boolean last) {
        Entity e = target();
        if (e == null) {
            src.sendFailure(Component.literal("look at an entity within 32 blocks"));
            return 0;
        }
        RequiemFx.bladeSpark(e, stab, last);
        src.sendSuccess(() -> Component.literal("blade spark " + stab + (last ? " (last)" : "") + " on " + e.getId()), false);
        return 1;
    }

    private static int swing(CommandSourceStack src, boolean hit) {
        Entity e = target();
        if (!(e instanceof AbstractKuva k)) {
            src.sendFailure(Component.literal("look at a Kuva Lich or thrall within 32 blocks"));
            return 0;
        }
        KuvaAnimFx.onSwing(k);
        if (hit) {
            KuvaAnimFx.onHit(k);
        }
        src.sendSuccess(() -> Component.literal("swing" + (hit ? " + hit" : "") + " on " + e.getId()), false);
        return 1;
    }

    /** 准星指着的实体（32 格内，被方块挡住的不算） */
    private static Entity target() {
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
            return null;
        }
        return hit.getEntity();
    }
}
