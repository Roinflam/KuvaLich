package pers.roinflam.kuvalich.client.mercy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.mercy.KuvaStateKind;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Optional;

/**
 * 开发环境里的客户端调试命令（正式版不注册），对准星指着的玄骸 / 奴仆（32 格内）演状态特效，只在本地画，不经服务端、不改任何数值：
 *
 * <ul>
 *   <li>{@code /kuvafxc fury <档 0~4>}：把这只的怒意显示档位钉在这一档（发光乘子、纹路层、奴仆光点都跟着变）；
 *       {@code /kuvafxc fury clear} 回到同步的值；</li>
 *   <li>{@code /kuvafxc state adapt <层 1~6> [added|full]}：适应护壁，命中点按自己眼睛到它胸口的连线算；
 *       {@code added} = 这一下加了层，{@code full} = 第一次叠满；</li>
 *   <li>{@code /kuvafxc state guard <削掉的百分比 10~99>}：挡住了；</li>
 *   <li>{@code /kuvafxc state strike <吸血 × 10> [heavy]}：吸血（从它身边最近的别的生物身上飞回它，旁边没有就从自己身上）、
 *       {@code heavy} 再加击退明显；</li>
 *   <li>{@code /kuvafxc state weak}：弱点（裂纹落在命中点）；</li>
 *   <li>{@code /kuvafxc state regen}：自然回血的光点；</li>
 *   <li>{@code /kuvafxc state slow <倍数>}：护壁、偏转闪、裂纹放慢这么多倍（截图用，1 = 正常）。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class StateDevCommands {

    private StateDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("fury")
                        .then(Commands.literal("clear").executes(ctx -> fury(ctx.getSource(), -1)))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, 4))
                                .executes(ctx -> fury(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tier")))))
                .then(Commands.literal("state")
                        .then(Commands.literal("adapt")
                                .then(Commands.argument("layers", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> adapt(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "layers"), false, false))
                                        .then(Commands.literal("added").executes(ctx -> adapt(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "layers"), true, false)))
                                        .then(Commands.literal("full").executes(ctx -> adapt(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "layers"), true, true)))))
                        .then(Commands.literal("guard")
                                .then(Commands.argument("percent", IntegerArgumentType.integer(10, 99))
                                        .executes(ctx -> guard(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "percent")))))
                        .then(Commands.literal("strike")
                                .then(Commands.argument("drain", IntegerArgumentType.integer(0, 255))
                                        .executes(ctx -> strike(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "drain"), false))
                                        .then(Commands.literal("heavy").executes(ctx -> strike(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "drain"), true)))))
                        .then(Commands.literal("weak").executes(ctx -> weak(ctx.getSource())))
                        .then(Commands.literal("regen").executes(ctx -> regen(ctx.getSource())))
                        .then(Commands.literal("slow")
                                .then(Commands.argument("factor", IntegerArgumentType.integer(1, 50))
                                        .executes(ctx -> slow(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "factor")))))));
    }

    private static int fury(CommandSourceStack src, int tier) {
        AbstractKuva e = target(src);
        if (e == null) {
            return 0;
        }
        KuvaStateFx.setFuryOverride(e.getId(), tier);
        src.sendSuccess(() -> Component.literal(tier < 0 ? "fury override cleared on " + e.getId()
                : "fury tier " + tier + " on " + e.getId() + " (synced " + e.getFuryPermille() + "‰)"), false);
        return 1;
    }

    private static int adapt(CommandSourceStack src, int layers, boolean added, boolean full) {
        AbstractKuva e = target(src);
        if (e == null) {
            return 0;
        }
        int param = KuvaStateKind.adaptParam(full ? 6 : layers, AbstractKuva.DamageCategory.MELEE.ordinal(), added, full);
        send(e, hitPoint(e), KuvaStateKind.ADAPT, param, self() + 1);
        src.sendSuccess(() -> Component.literal("adapt L" + (full ? 6 : layers) + (added ? " added" : "")
                + (full ? " first-full" : "") + " on " + e.getId()), false);
        return 1;
    }

    private static int guard(CommandSourceStack src, int percent) {
        AbstractKuva e = target(src);
        if (e == null) {
            return 0;
        }
        send(e, hitPoint(e), KuvaStateKind.GUARD, KuvaStateKind.guardParam(percent, false), self() + 1);
        src.sendSuccess(() -> Component.literal("guard " + percent + "% on " + e.getId()), false);
        return 1;
    }

    private static int strike(CommandSourceStack src, int drain, boolean heavy) {
        AbstractKuva e = target(src);
        LocalPlayer me = Minecraft.getInstance().player;
        if (e == null || me == null) {
            return 0;
        }
        // 受击者取它身边 6 格内最近的别的生物（站在旁边看得清雾往哪飞），没有就是自己
        Entity victim = me;
        double best = 36.0D;
        for (LivingEntity o : e.level().getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(6.0D),
                o -> o != e && o != me && o.isAlive())) {
            double d2 = o.distanceToSqr(e);
            if (d2 < best) {
                best = d2;
                victim = o;
            }
        }
        Entity v = victim;
        send(e, v.position(), KuvaStateKind.STRIKE, KuvaStateKind.strikeParam(drain, heavy), v.getId() + 1);
        src.sendSuccess(() -> Component.literal("strike drain " + drain / 10.0F + (heavy ? " heavy" : "") + " from "
                + e.getId() + " to " + v.getId()), false);
        return 1;
    }

    private static int weak(CommandSourceStack src) {
        AbstractKuva e = target(src);
        if (e == null) {
            return 0;
        }
        KuvaStateFx.weakPoint(e, hitPoint(e));
        src.sendSuccess(() -> Component.literal("weak point on " + e.getId()), false);
        return 1;
    }

    private static int regen(CommandSourceStack src) {
        AbstractKuva e = target(src);
        if (e == null) {
            return 0;
        }
        KuvaStateFx.onRegen(e);
        src.sendSuccess(() -> Component.literal("regen motes on " + e.getId()), false);
        return 1;
    }

    private static int slow(CommandSourceStack src, int factor) {
        KuvaStateFx.setSlowMotion(factor);
        src.sendSuccess(() -> Component.literal("state fx slow-motion x" + factor), false);
        return 1;
    }

    private static void send(AbstractKuva e, Vec3 at, int kind, int param, int b) {
        KuvaStateFx.onPacket(new KuvaFxPacket(FxId.KUVA_STATE.id, e.getId() + 1, (float) at.x, (float) at.y,
                (float) at.z, KuvaStateKind.pack(kind, param), b));
    }

    private static int self() {
        LocalPlayer me = Minecraft.getInstance().player;
        return me == null ? -1 : me.getId();
    }

    /** 命中点：自己眼睛到它胸口的连线和包围盒的交点，往胸口收 0.05 格 */
    private static Vec3 hitPoint(AbstractKuva e) {
        Vec3 chest = new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.62D, e.getZ());
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) {
            return chest;
        }
        Optional<Vec3> hit = e.getBoundingBox().clip(me.getEyePosition(), chest);
        if (hit.isEmpty()) {
            return chest;
        }
        Vec3 p = hit.get();
        Vec3 dir = chest.subtract(p);
        double len = dir.length();
        return len <= 0.05D ? chest : p.add(dir.scale(0.05D / len));
    }

    /** 准星指着的玄骸 / 奴仆（32 格内，被方块挡住的不算）；没有时回一句失败 */
    private static AbstractKuva target(CommandSourceStack src) {
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
        Entity e = hit == null || eye.distanceTo(hit.getLocation()) > blockDist ? null : hit.getEntity();
        if (!(e instanceof AbstractKuva k)) {
            src.sendFailure(Component.literal("look at a Kuva Lich or Thrall within 32 blocks"));
            return null;
        }
        return k;
    }
}
