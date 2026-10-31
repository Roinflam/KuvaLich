package pers.roinflam.kuvalich.client.lich;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.fx.KuvaFxDirector;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 开发环境里的客户端调试命令 {@code /kuvafxc talk <毫秒> [shout]}：让准星指着的玄骸张嘴说这么久
 *
 * <p>直接设实体的 {@code talkUntilMs}（客户端演出钟），带 {@code shout} 时先来一下喊话动作再循环说话动作；
 * 不用等服务端真发一句通讯，就能看世界里那只的 {@code talk} / {@code talk_shout} 动画。
 * 0 毫秒让它马上闭嘴。准星 32 格内没指着就挑 16 格内最近的一只。正式版不注册。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class TalkDevCommand {

    private static final double PICK_RANGE = 32.0D;
    private static final double NEAREST_RANGE = 16.0D;

    private TalkDevCommand() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal("kuvafxc")
                .then(Commands.literal("talk")
                        .then(Commands.argument("ms", IntegerArgumentType.integer(0, 60000))
                                .executes(ctx -> talk(ctx, false))
                                .then(Commands.literal("shout").executes(ctx -> talk(ctx, true))))));
    }

    private static int talk(CommandContext<CommandSourceStack> ctx, boolean shout) {
        int ms = IntegerArgumentType.getInteger(ctx, "ms");
        AbstractKuva target = pick();
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("no kuva entity under the crosshair or within "
                    + (int) NEAREST_RANGE + " blocks"));
            return 0;
        }
        long now = KuvaFxDirector.clockMs();
        target.talkShout = shout;
        target.talkUntilMs = ms == 0 ? 0L : now + ms;
        ctx.getSource().sendSuccess(() -> Component.literal((ms == 0 ? "stop talking: #" : "talk " + ms + " ms"
                + (shout ? " (shout)" : "") + ": #") + target.getId() + " " + target.getName().getString()), false);
        return 1;
    }

    /** 准星 32 格内指着的玄骸 / 奴仆；没有就取 16 格内最近的一只 */
    private static AbstractKuva pick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        if (mc.crosshairPickEntity instanceof AbstractKuva kuva) {
            return kuva;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(PICK_RANGE));
        AABB box = player.getBoundingBox().expandTowards(look.scale(PICK_RANGE)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                e -> e instanceof AbstractKuva && !e.isSpectator(), PICK_RANGE * PICK_RANGE);
        if (hit != null && hit.getEntity() instanceof AbstractKuva kuva) {
            return kuva;
        }
        AbstractKuva nearest = null;
        double best = NEAREST_RANGE * NEAREST_RANGE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof AbstractKuva kuva && e.isAlive()) {
                double d = e.distanceToSqr(player);
                if (d < best) {
                    best = d;
                    nearest = kuva;
                }
            }
        }
        return nearest;
    }
}
