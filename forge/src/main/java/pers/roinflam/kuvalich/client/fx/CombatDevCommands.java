package pers.roinflam.kuvalich.client.fx;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pers.roinflam.kuvalich.client.fx.script.CombatFx;
import pers.roinflam.kuvalich.client.fx.script.ElementFx;
import pers.roinflam.kuvalich.client.fx.script.ElementStatusFx;
import pers.roinflam.kuvalich.client.renderer.ElementDebuffTracker;
import pers.roinflam.kuvalich.client.renderer.damage.DamageRenderer;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.network.packet.DamageInfo;
import pers.roinflam.kuvalich.network.packet.KuvaFxPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;

/**
 * 开发环境里的战斗画面调试命令（正式版不注册），给伤害数字、暴击、元素状态截图和数粒子用
 *
 * <ul>
 *   <li>{@code /kuvafxc hitnum <white|blue|purple|yellow|orange|red|dot> [数值] [个数]}：在准星对着的实体（没对着就取最近的）头上冒数字；</li>
 *   <li>{@code /kuvafxc popframes <yellow|orange|red>}：并排摆出 0 / 33 / 50 / 67 / 100 / 150 ms 六个时刻的同一个数字并停表（50、150 ms 即第 1、3 tick），
 *       日志里同时打出这几刻的放大倍数；{@code /kuvafxc freeze off} 放开；</li>
 *   <li>{@code /kuvafxc crit <1|2|3>}：假装自己对目标打出一次这档暴击；</li>
 *   <li>{@code /kuvafxc elem <元素> [层]}：假装目标刚触发了这种元素（爆发画面 + 声音 + 状态）；</li>
 *   <li>{@code /kuvafxc status <元素> <秒> [层]} / {@code statusall …}：只在本地给目标 / 32 格内全部生物挂上持续状态；</li>
 *   <li>{@code /kuvafxc gas [秒]}：在目标处放一团毒云；</li>
 *   <li>{@code /kuvafxc statusrow [on|off]}：准星上方画目标的元素状态行；</li>
 *   <li>{@code /kuvafxc prate [秒]}：数接下来几秒新生成的粒子，打出每秒平均和单 tick 峰值。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CombatDevCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger("KuvaLich/CombatDev");

    private static final String[] TIER_NAMES = {"white", "blue", "purple", "yellow", "orange", "red", "dot"};
    private static final String[] TIER_CODES = {"§f", "§b", "§5", "§e", "§6", "§c", "§f"};
    private static final long[] POP_FRAMES = {0L, 33L, 50L, 67L, 100L, 150L};

    private static final SuggestionProvider<CommandSourceStack> ELEMENTS = (ctx, b) -> {
        for (int i = 0; i < DamageInfo.ELEMENT_COUNT; i++) {
            b.suggest(DamageInfo.elementName(i));
        }
        return b.buildFuture();
    };
    private static final SuggestionProvider<CommandSourceStack> TIERS =
            (ctx, b) -> SharedSuggestionProvider.suggest(TIER_NAMES, b);

    private static boolean showStatusRow;
    private static int rateTicksLeft;
    private static int rateTicksTotal;
    private static long rateSum;
    private static int ratePeak;

    private CombatDevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("kuvafxc")
                .then(Commands.literal("hitnum")
                        .then(Commands.argument("tier", StringArgumentType.word()).suggests(TIERS)
                                .executes(ctx -> hitnum(ctx, 1234.0F, 1))
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                        .executes(ctx -> hitnum(ctx, FloatArgumentType.getFloat(ctx, "amount"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
                                                .executes(ctx -> hitnum(ctx, FloatArgumentType.getFloat(ctx, "amount"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("popframes")
                        .then(Commands.argument("tier", StringArgumentType.word()).suggests(TIERS)
                                .executes(CombatDevCommands::popFrames)))
                .then(Commands.literal("freeze")
                        .then(Commands.literal("off").executes(ctx -> {
                            DamageRenderer.devFreeze(-1L);
                            ctx.getSource().sendSuccess(() -> Component.literal("damage clock running"), false);
                            return 1;
                        })))
                .then(Commands.literal("crit")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                .executes(ctx -> crit(ctx, IntegerArgumentType.getInteger(ctx, "tier")))))
                .then(Commands.literal("elem")
                        .then(Commands.argument("element", StringArgumentType.word()).suggests(ELEMENTS)
                                .executes(ctx -> elem(ctx, 0))
                                .then(Commands.argument("amp", IntegerArgumentType.integer(0, 255))
                                        .executes(ctx -> elem(ctx, IntegerArgumentType.getInteger(ctx, "amp"))))))
                .then(Commands.literal("status")
                        .then(Commands.argument("element", StringArgumentType.word()).suggests(ELEMENTS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                        .executes(ctx -> status(ctx, false, 0))
                                        .then(Commands.argument("amp", IntegerArgumentType.integer(0, 255))
                                                .executes(ctx -> status(ctx, false, IntegerArgumentType.getInteger(ctx, "amp")))))))
                .then(Commands.literal("statusall")
                        .then(Commands.argument("element", StringArgumentType.word()).suggests(ELEMENTS)
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                                        .executes(ctx -> status(ctx, true, 0))
                                        .then(Commands.argument("amp", IntegerArgumentType.integer(0, 255))
                                                .executes(ctx -> status(ctx, true, IntegerArgumentType.getInteger(ctx, "amp")))))))
                .then(Commands.literal("gas")
                        .executes(ctx -> gas(ctx, 6))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                .executes(ctx -> gas(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("statusrow")
                        .executes(ctx -> statusRow(ctx.getSource(), !showStatusRow))
                        .then(Commands.literal("on").executes(ctx -> statusRow(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> statusRow(ctx.getSource(), false))))
                .then(Commands.literal("prate")
                        .executes(ctx -> prate(ctx.getSource(), 5))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                .executes(ctx -> prate(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    // ==================== 目标 ====================

    /** 准星对着的生物；没对着就取 16 格内离自己最近的 */
    private static LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        if (mc.crosshairPickEntity instanceof LivingEntity le) {
            return le;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(16.0D),
                e -> e != mc.player && e.isAlive())) {
            double d = e.distanceToSqr(mc.player);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static LivingEntity requireTarget(CommandSourceStack src) {
        LivingEntity t = target();
        if (t == null) {
            src.sendFailure(Component.literal("no target"));
        }
        return t;
    }

    private static int elementArg(CommandContext<CommandSourceStack> ctx) {
        int idx = DamageInfo.elementIndex(StringArgumentType.getString(ctx, "element"));
        if (idx < 0) {
            ctx.getSource().sendFailure(Component.literal("unknown element"));
        }
        return idx;
    }

    private static int tierArg(CommandContext<CommandSourceStack> ctx) {
        String s = StringArgumentType.getString(ctx, "tier");
        for (int i = 0; i < TIER_NAMES.length; i++) {
            if (TIER_NAMES[i].equals(s)) {
                return i;
            }
        }
        ctx.getSource().sendFailure(Component.literal("unknown tier"));
        return -1;
    }

    private static Vec3 head(LivingEntity e) {
        return e.position().add(0.0D, e.getBbHeight() + 0.3D, 0.0D);
    }

    private static KuvaFxPacket packet(FxId fx, LivingEntity e, int a, int b) {
        Vec3 c = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
        return new KuvaFxPacket(fx.id, e.getId() + 1, (float) c.x, (float) c.y, (float) c.z, a, b);
    }

    // ==================== 伤害数字 ====================

    private static int hitnum(CommandContext<CommandSourceStack> ctx, float amount, int count) {
        int tier = tierArg(ctx);
        LivingEntity t = tier < 0 ? null : requireTarget(ctx.getSource());
        if (t == null) {
            return 0;
        }
        boolean dot = "dot".equals(TIER_NAMES[tier]);
        Vec3 at = head(t);
        for (int i = 0; i < count; i++) {
            // 实体 id 给 -1：每个数字各自一条，不并进前一个
            DamageRenderer.getInstance().addDamage(-1, dot ? "fire" : "", amount, TIER_CODES[tier], "", "",
                    false, at, 0L);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("hitnum " + TIER_NAMES[tier] + " x" + count), false);
        return 1;
    }

    private static int popFrames(CommandContext<CommandSourceStack> ctx) {
        int tier = tierArg(ctx);
        LivingEntity t = tier < 0 ? null : requireTarget(ctx.getSource());
        if (t == null) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = KuvaFxDirector.clockMs();
        DamageRenderer.devFreeze(now);
        Vec3 base = head(t);
        // 沿相机左右方向一字排开
        var cam = KuvaFxDirector.camera();
        Vec3 left = cam == null ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(cam.getLeftVector().x(), cam.getLeftVector().y(), cam.getLeftVector().z());
        StringBuilder sb = new StringBuilder("popframes ").append(TIER_NAMES[tier]).append(':');
        int dtier = DamageInfo.tierOf(TIER_CODES[tier]);
        for (int i = 0; i < POP_FRAMES.length; i++) {
            long age = POP_FRAMES[i];
            Vec3 at = base.add(left.scale(((POP_FRAMES.length - 1) / 2.0D - i) * 1.6D));
            DamageInfo info = new DamageInfo(88.0F, TIER_CODES[tier], "", "", false, at, now - age,
                    null, "", 0L, true);
            DamageRenderer.getInstance().addDamageInfo(info);
            sb.append(' ').append(age).append("ms=").append(String.format("%.3f", DamageRenderer.popScaleAt(dtier, age)));
        }
        String line = sb.toString();
        LOGGER.info(line);
        ctx.getSource().sendSuccess(() -> Component.literal(line + " (frozen; /kuvafxc freeze off)"), false);
        if (mc.player != null) {
            LOGGER.info("popframes camera {} target {}", mc.player.position(), t.position());
        }
        return 1;
    }

    // ==================== 暴击 / 元素 ====================

    private static int crit(CommandContext<CommandSourceStack> ctx, int tier) {
        LivingEntity t = requireTarget(ctx.getSource());
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.player == null) {
            return 0;
        }
        CombatFx.crit(packet(FxId.CRIT, t, tier, mc.player.getId() + 1));
        LOGGER.info("crit tier {} at clock {} gameTime {}", tier, KuvaFxDirector.clockMs(), mc.level.getGameTime());
        ctx.getSource().sendSuccess(() -> Component.literal("crit " + tier), false);
        return 1;
    }

    private static int elem(CommandContext<CommandSourceStack> ctx, int amp) {
        int idx = elementArg(ctx);
        LivingEntity t = idx < 0 ? null : requireTarget(ctx.getSource());
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.player == null) {
            return 0;
        }
        // 电击带麻痹 tick、切割带流血轮数，其余不用
        int extra = idx == ElementFx.ELECTRICITY ? 40 : idx == ElementFx.SLASH ? 6 : 0;
        int a = idx | (Math.min(amp, 0xFF) << 8) | (extra << 16);
        ElementFx.proc(packet(FxId.ELEMENT_PROC, t, a, mc.player.getId() + 1));
        if (idx != ElementFx.ELECTRICITY && idx != ElementFx.SLASH) {
            ElementDebuffTracker.apply(t.getId(), DamageInfo.elementName(idx), 100, amp);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("elem " + DamageInfo.elementName(idx) + " amp " + amp), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, boolean all, int amp) {
        int idx = elementArg(ctx);
        if (idx < 0) {
            return 0;
        }
        int ticks = IntegerArgumentType.getInteger(ctx, "seconds") * 20;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        List<LivingEntity> list;
        if (all) {
            AABB box = mc.player.getBoundingBox().inflate(32.0D);
            list = mc.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != mc.player && e.isAlive());
        } else {
            LivingEntity t = requireTarget(ctx.getSource());
            if (t == null) {
                return 0;
            }
            list = List.of(t);
        }
        for (LivingEntity e : list) {
            if (idx == ElementFx.ELECTRICITY || idx == ElementFx.SLASH) {
                ElementStatusFx.devApply(e, idx, ticks);
            } else {
                ElementDebuffTracker.apply(e.getId(), DamageInfo.elementName(idx), ticks, amp);
                if (idx == ElementFx.VIRUS) {
                    ElementStatusFx.devApply(e, idx, ticks);
                }
            }
        }
        int n = list.size();
        ctx.getSource().sendSuccess(() -> Component.literal("status " + DamageInfo.elementName(idx) + " -> " + n), false);
        return 1;
    }

    private static int gas(CommandContext<CommandSourceStack> ctx, int seconds) {
        LivingEntity t = requireTarget(ctx.getSource());
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.player == null) {
            return 0;
        }
        Vec3 c = t.position().add(0.0D, 0.6D, 0.0D);
        ElementFx.gasCloud(new KuvaFxPacket(FxId.GAS_CLOUD.id, 0, (float) c.x, (float) c.y, (float) c.z,
                seconds * 20, mc.player.getId() + 1));
        ctx.getSource().sendSuccess(() -> Component.literal("gas " + seconds + " s"), false);
        return 1;
    }

    // ==================== 状态行 ====================

    private static int statusRow(CommandSourceStack src, boolean on) {
        showStatusRow = on;
        src.sendSuccess(() -> Component.literal("status row " + (on ? "on" : "off")), false);
        return 1;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!showStatusRow || FMLEnvironment.production) {
            return;
        }
        LivingEntity t = target();
        if (t == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() / 2 - 24;
        // 抬到聊天框之上，免得被它的底色挡住
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        ElementStatusRow.draw(g, t, cx, y, 1.0F);
        g.pose().popPose();
    }

    // ==================== 粒子计数 ====================

    private static int prate(CommandSourceStack src, int seconds) {
        rateTicksTotal = seconds * 20;
        rateTicksLeft = rateTicksTotal;
        rateSum = 0L;
        ratePeak = 0;
        src.sendSuccess(() -> Component.literal("counting particles for " + seconds + " s"), false);
        return 1;
    }

    /** tick 末尾读这一 tick 新生成的粒子数（tick 开头才清零，所以这时读到的是本 tick 的全部） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || rateTicksLeft <= 0) {
            return;
        }
        int n = FxBudget.newThisTick();
        rateSum += n;
        ratePeak = Math.max(ratePeak, n);
        if (--rateTicksLeft == 0) {
            float perSecond = rateSum * 20.0F / rateTicksTotal;
            String line = String.format("prate %d ticks: total %d, %.1f/s, peak %d/tick, live %d",
                    rateTicksTotal, rateSum, perSecond, ratePeak, FxBudget.liveParticles());
            LOGGER.info(line);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(line), false);
            }
        }
    }

}
