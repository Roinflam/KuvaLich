package pers.roinflam.kuvalich.client.mercy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.client.model.KuvaMasterModel;
import pers.roinflam.kuvalich.mercy.MercyAnimSelector;
import pers.roinflam.kuvalich.utils.Reference;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.Collections;
import java.util.function.Function;

/**
 * 开发环境里核对处决动画用的客户端命令，正式版里没有（{@link FMLEnvironment#production} 为真时不注册）
 *
 * <ul>
 *   <li>{@code /kuvafxc mercyanim <片段>}：准星指着的（没指着就取 16 格内最近的）赤毒实体从头播某一段，
 *       不管它现在是不是倒地 / 处决状态；片段名照动画文件，按 Tab 有提示。</li>
 *   <li>{@code /kuvafxc mercyanim <片段> <毫秒>}：同上，但冻在这一刻，逐个关键帧截图用。</li>
 *   <li>{@code /kuvafxc mercyanim off}：全部放开，交还给原来的选片段逻辑。</li>
 * </ul>
 *
 * <p>冻帧的做法：GeckoLib 的片段时间 = 控制器速度 × 从这段开播起过了多少 tick，没有直接跳到某一刻的接口，
 * 所以每帧把速度设成「目标 tick ÷ 已过 tick」，乘出来始终是目标那一刻。放开时速度改回 1。</p>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class MercyAnimDevCommands {

    /** 实体 id → 强制播的片段 */
    private static final Int2ObjectMap<Forced> FORCED = new Int2ObjectOpenHashMap<>();

    private static final Function<AbstractKuva, Double> NORMAL_SPEED = e -> 1.0D;

    private MercyAnimDevCommands() {
    }

    private static final class Forced {
        final String clip;
        final RawAnimation animation;
        /** 冻在第几 tick；负数 = 不冻、照常播 */
        final double freezeTicks;
        /** 这段开播以后被调过几帧 */
        int frames;
        /** 控制器开始计时的那一刻（GeckoLib 的动画 tick） */
        double startTick = Double.NaN;
        /** 动画 tick 和 实体 tickCount + 帧内插值 之间的差，算速度时用来换算当前时刻 */
        double tickBase;
        @Nullable
        AnimationController<?> controller;

        Forced(String clip, double freezeTicks) {
            this.clip = clip;
            this.animation = RawAnimation.begin().thenPlay(clip);
            this.freezeTicks = freezeTicks;
        }
    }

    @SubscribeEvent
    public static void onRegister(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        MercyAnimSelector.devOverride = MercyAnimDevCommands::forced;
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("mercyanim")
                        .then(Commands.literal("off").executes(ctx -> {
                            int n = FORCED.size();
                            releaseAll();
                            reply(ctx, "mercy anim released (" + n + ")");
                            return n;
                        }))
                        .then(Commands.argument("clip", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(clipNames(), b))
                                .executes(ctx -> play(ctx, -1))
                                .then(Commands.argument("ms", IntegerArgumentType.integer(0, 20000))
                                        .executes(ctx -> play(ctx, IntegerArgumentType.getInteger(ctx, "ms")))))));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FORCED.clear();
    }

    private static Iterable<String> clipNames() {
        BakedAnimations baked = GeckoLibCache.getBakedAnimations().get(KuvaMasterModel.MERCY_ANIMATIONS);
        return baked == null ? Collections.emptyList() : baked.animations().keySet();
    }

    private static int play(CommandContext<CommandSourceStack> ctx, int ms) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return 0;
        }
        String clip = StringArgumentType.getString(ctx, "clip");
        if (!clip.startsWith("mercy.") && !"walk".equals(clip) && !"run".equals(clip) && !"stay".equals(clip)) {
            reply(ctx, "unknown clip: " + clip);
            return 0;
        }
        Entity target = pickTarget(mc, player);
        if (target == null) {
            reply(ctx, "no kuva entity in sight");
            return 0;
        }
        release(FORCED.remove(target.getId()));
        FORCED.put(target.getId(), new Forced(clip, ms < 0 ? -1.0D : ms / 50.0D));
        reply(ctx, "mercy anim: " + clip + (ms < 0 ? "" : " frozen at " + ms + " ms") + " on #" + target.getId());
        return 1;
    }

    /** 选片段时先问这里：有强制的片段就播它 */
    @Nullable
    private static RawAnimation forced(AbstractKuva entity, AnimationState<?> state) {
        if (FORCED.isEmpty()) {
            return null;
        }
        Forced f = FORCED.get(entity.getId());
        if (f == null) {
            return null;
        }
        if (f.frames == 0) {
            // 同一段可能正在播（例如倒地循环），强制从头来
            f.controller = state.getController();
            f.controller.forceAnimationReset();
            if (f.freezeTicks >= 0) {
                setSpeed(f.controller, e -> speedFor(f, e));
            }
        } else if (f.frames == 1) {
            // 过渡时长是 0：开播后的下一帧控制器才进入正式播放、从这一刻起计时（这一帧的选片段在计时之后才调）
            f.startTick = state.getAnimationTick();
            f.tickBase = f.startTick - (entity.tickCount + state.getPartialTick());
        }
        f.frames++;
        return f.animation;
    }

    private static Double speedFor(Forced f, AbstractKuva entity) {
        if (Double.isNaN(f.startTick)) {
            return 1.0D;
        }
        double now = f.tickBase + entity.tickCount + Minecraft.getInstance().getFrameTime();
        double elapsed = now - f.startTick;
        return elapsed <= 1.0E-4D ? 0.0D : f.freezeTicks / elapsed;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setSpeed(AnimationController<?> controller, Function<AbstractKuva, Double> speed) {
        ((AnimationController) controller).setAnimationSpeedHandler(speed);
    }

    private static void release(@Nullable Forced f) {
        if (f != null && f.controller != null) {
            setSpeed(f.controller, NORMAL_SPEED);
            f.controller.forceAnimationReset();
        }
    }

    private static void releaseAll() {
        for (Forced f : FORCED.values()) {
            release(f);
        }
        FORCED.clear();
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
