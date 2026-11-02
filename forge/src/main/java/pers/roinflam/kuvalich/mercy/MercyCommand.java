package pers.roinflam.kuvalich.mercy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.entity.AbstractKuva;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.entity.KuvaSlaveEntity;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.nemesis.LichNames;
import pers.roinflam.kuvalich.nemesis.LichProfile;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * 怜悯之击的管理 / 录像命令 {@code /kuvalich mercy …}，权限 2，正式版也有
 *
 * <p>录演示用：一条命令摆出倒地的演示目标、指定下一次用哪套动作和什么结果、立刻处决、改倒地倒计时、
 * 冻结所有倒计时、对一只赤毒实体播一次特殊状态特效、一键复位、中止自己的处决。服主处理「卡在倒地」之类的问题也用得上。</p>
 *
 * <p>演示目标（{@code spawn} 摆出来的）只在内存里：不存盘、不进专属玄骸的任何记录，处决它不扣卡、不记尝试、
 * 不给进度和掉落。真玄骸的结果只看卡，{@code outcome} 对它无效。</p>
 *
 * <p>「准星指着的」= 视线方向 16 格内第一只赤毒实体（被方块挡住的不算）；视线正好擦过去时，
 * 退回视线 25° 以内最近的那只，录像时不用对得很准。反馈是管理用的英文细节，套在
 * {@code commands.kuvalich.mercy.ok} / {@code .fail} 里。</p>
 *
 * <p>根节点 {@code /kuvalich} 和 {@code /kuvalich lich} 共用：Brigadier 合并同名节点时保留先注册那个的权限要求，
 * 两边都写权限 2，这里的 {@code mercy} 节点自己再要求一次。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class MercyCommand {

    /** 准星找目标的距离（格） */
    static final double AIM_RANGE = 16.0D;
    /** 射线没打中时，视线方向多少度以内的也算指着 */
    private static final double AIM_CONE_COS = Math.cos(Math.toRadians(25.0D));
    /** 演示目标摆在面前几格 */
    static final double SPAWN_DISTANCE = 3.0D;
    /** 面前那格站不下时依次试的距离 */
    private static final double[] SPAWN_TRIES = {3.0D, 2.5D, 3.5D, 2.0D, 4.0D, 1.5D};
    /** {@code reset} 的范围（格） */
    static final double RESET_RADIUS = 32.0D;
    /** {@code state fury} 临时改档的时长：10 秒 */
    static final int FURY_OVERRIDE_TICKS = 200;
    /** 交战增幅各档的下限（千分比）：0 档以下没有效果，4 档是交战 40 秒以上 */
    static final int[] FURY_TIER_FLOOR = {0, 250, 750, 1500, 3000};
    /** {@code state guard} 不给数时演示的削减比例（≥ 50% 画大的那道） */
    private static final int DEFAULT_GUARD_PERCENT = 60;
    /** {@code state strike} 演示的吸血量 × 10（2 点血）和「击退明显」 */
    private static final int DEMO_STRIKE_DRAIN_TENTHS = 20;
    /** 安魂卡的名字，下标就是卡 id（8 是 Oull） */
    static final String[] CARD_NAMES = {"khra", "jahu", "fass", "lohk", "netra", "vome", "xata", "ris", "oull"};

    private static final String[] OUTCOMES = {"success", "fail1", "fail2", "fail3", "real"};

    private static final SuggestionProvider<CommandSourceStack> ALL_VARIANTS = (ctx, b) ->
            SharedSuggestionProvider.suggest(variantNames(null, true), b);
    private static final SuggestionProvider<CommandSourceStack> LICH_SPAWN = (ctx, b) -> suggestFirstWord(b,
            variantNames(false, true));
    private static final SuggestionProvider<CommandSourceStack> THRALL_VARIANTS = (ctx, b) ->
            SharedSuggestionProvider.suggest(variantNames(true, true), b);
    private static final SuggestionProvider<CommandSourceStack> EXEC_FIRST = (ctx, b) -> {
        List<String> all = variantNames(null, true);
        all.addAll(List.of(OUTCOMES));
        return SharedSuggestionProvider.suggest(all, b);
    };
    private static final SuggestionProvider<CommandSourceStack> OUTCOME_NAMES = (ctx, b) ->
            SharedSuggestionProvider.suggest(OUTCOMES, b);
    private static final SuggestionProvider<CommandSourceStack> CARD_SPEC = (ctx, b) -> {
        // 每个空格后面都提示卡名，最后再提示 return
        String rest = b.getRemaining();
        int cut = rest.lastIndexOf(' ') + 1;
        SuggestionsBuilder sub = b.createOffset(b.getStart() + cut);
        List<String> options = new ArrayList<>(List.of(CARD_NAMES));
        options.add("return");
        if (cut == 0) {
            options.add("clear");
        }
        return SharedSuggestionProvider.suggest(options, sub);
    };

    private MercyCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> mercy = Commands.literal("mercy").requires(s -> s.hasPermission(2));

        mercy.then(Commands.literal("spawn")
                .then(Commands.literal("lich")
                        .executes(ctx -> spawnLich(ctx, ""))
                        .then(Commands.argument("options", StringArgumentType.greedyString()).suggests(LICH_SPAWN)
                                .executes(ctx -> spawnLich(ctx, StringArgumentType.getString(ctx, "options")))))
                .then(Commands.literal("thrall")
                        .executes(ctx -> spawnThrall(ctx, null))
                        .then(Commands.argument("variant", StringArgumentType.word()).suggests(THRALL_VARIANTS)
                                .executes(ctx -> spawnThrall(ctx, StringArgumentType.getString(ctx, "variant"))))));

        mercy.then(Commands.literal("down")
                .executes(MercyCommand::downAimed)
                .then(Commands.argument("targets", EntityArgument.entities()).executes(MercyCommand::downTargets)));

        mercy.then(Commands.literal("exec")
                .executes(ctx -> exec(ctx, null, null))
                .then(Commands.argument("first", StringArgumentType.word()).suggests(EXEC_FIRST)
                        .executes(ctx -> exec(ctx, StringArgumentType.getString(ctx, "first"), null))
                        .then(Commands.argument("outcome", StringArgumentType.word()).suggests(OUTCOME_NAMES)
                                .executes(ctx -> exec(ctx, StringArgumentType.getString(ctx, "first"),
                                        StringArgumentType.getString(ctx, "outcome"))))));

        mercy.then(Commands.literal("next")
                .then(Commands.argument("variant", StringArgumentType.word()).suggests(ALL_VARIANTS)
                        .executes(ctx -> next(ctx, StringArgumentType.getString(ctx, "variant")))));

        mercy.then(Commands.literal("outcome")
                .then(Commands.argument("outcome", StringArgumentType.word()).suggests(OUTCOME_NAMES)
                        .executes(ctx -> outcome(ctx, StringArgumentType.getString(ctx, "outcome")))));

        mercy.then(Commands.literal("cards")
                .then(Commands.argument("cards", StringArgumentType.greedyString()).suggests(CARD_SPEC)
                        .executes(ctx -> cards(ctx, StringArgumentType.getString(ctx, "cards")))));

        mercy.then(Commands.literal("timer")
                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                        .executes(ctx -> timer(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))));

        mercy.then(Commands.literal("state")
                .then(Commands.literal("adapt")
                        .executes(ctx -> stateAdapt(ctx, 1))
                        .then(Commands.argument("layers", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> stateAdapt(ctx, IntegerArgumentType.getInteger(ctx, "layers")))))
                .then(Commands.literal("full").executes(MercyCommand::stateFull))
                .then(Commands.literal("guard")
                        .executes(ctx -> stateGuard(ctx, DEFAULT_GUARD_PERCENT))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(10, 99))
                                .executes(ctx -> stateGuard(ctx, IntegerArgumentType.getInteger(ctx, "percent")))))
                .then(Commands.literal("strike").executes(MercyCommand::stateStrike))
                .then(Commands.literal("weak").executes(MercyCommand::stateWeak))
                .then(Commands.literal("fury")
                        .executes(ctx -> stateFury(ctx, FURY_TIER_FLOOR.length - 1))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(0, FURY_TIER_FLOOR.length - 1))
                                .executes(ctx -> stateFury(ctx, IntegerArgumentType.getInteger(ctx, "tier"))))));

        mercy.then(Commands.literal("freeze")
                .then(Commands.literal("on").executes(ctx -> freeze(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> freeze(ctx, false))));

        mercy.then(Commands.literal("reset").executes(MercyCommand::reset));
        mercy.then(Commands.literal("stop").executes(MercyCommand::stop));

        d.register(Commands.literal(Reference.MOD_ID).requires(s -> s.hasPermission(2)).then(mercy));
    }

    // ==================== spawn ====================

    /** {@code spawn lich [<套路>] [<名字>]}：套路写 random 或不写就随机，后面剩下的整段是名字 */
    private static int spawnLich(CommandContext<CommandSourceStack> ctx, String options) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!ModConfig.KUVA_LICH.mercyEnabled.get()) {
            return disabled(ctx);
        }
        String rest = options.strip();
        MercyVariant variant = null;
        boolean variantGiven = false;
        if (!rest.isEmpty()) {
            int space = rest.indexOf(' ');
            String first = space < 0 ? rest : rest.substring(0, space);
            if ("random".equalsIgnoreCase(first) || parseVariant(first) != null) {
                variantGiven = true;
                variant = parseVariant(first);
                rest = space < 0 ? "" : rest.substring(space + 1).strip();
            }
        }
        if (variant != null && variant.thrall) {
            return fail(ctx, variantName(variant) + " is a Thrall variant");
        }
        String name = rest;
        if (name.isEmpty()) {
            name = LichNames.generate(ThreadLocalRandom.current().nextLong());
        } else if (!LichNames.isAllowed(name)) {
            return fail(ctx, "name not allowed (max " + LichNames.MAX_LENGTH + " chars, no profanity): " + name);
        }
        KuvaMasterEntity lich = KuvaLichEntities.KUVA_MASTER.get().create(p.serverLevel());
        if (lich == null) {
            return fail(ctx, "could not create a Lich");
        }
        String err = place(p, lich);
        if (err != null) {
            return fail(ctx, err);
        }
        lich.setDemoIdentity(name, 1);
        // 演示玄骸不挂档案，武器照诞生时那样随机挑一把，不然倒地、处决时手里是空的
        lich.setDemoWeapon(LichProfile.weaponIndex(ThreadLocalRandom.current().nextLong(),
                KuvaLichItems.KUVA_WEAPONS.size()));
        lich.forceDowned(p);
        if (variantGiven) {
            MercyServer.setNextVariant(p, variant);
        }
        return ok(ctx, "demo Lich " + name + " #" + lich.getId() + " is down (" + lich.getMercyState() + ")"
                + (variantGiven ? ", next variant " + variantName(variant) : ""));
    }

    /** {@code spawn thrall [<套路>]} */
    private static int spawnThrall(CommandContext<CommandSourceStack> ctx, @Nullable String variantArg)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!ModConfig.KUVA_LICH.mercyEnabled.get()) {
            return disabled(ctx);
        }
        MercyVariant variant = null;
        boolean variantGiven = variantArg != null;
        if (variantGiven && !"random".equalsIgnoreCase(variantArg)) {
            variant = parseVariant(variantArg);
            if (variant == null) {
                return fail(ctx, "unknown variant: " + variantArg);
            }
            if (!variant.thrall) {
                return fail(ctx, variantName(variant) + " is a Lich variant");
            }
        }
        KuvaSlaveEntity thrall = KuvaLichEntities.KUVA_SLAVE.get().create(p.serverLevel());
        if (thrall == null) {
            return fail(ctx, "could not create a Thrall");
        }
        String err = place(p, thrall);
        if (err != null) {
            return fail(ctx, err);
        }
        thrall.forceDowned(p);
        if (variantGiven) {
            MercyServer.setNextVariant(p, variant);
        }
        return ok(ctx, "demo Thrall #" + thrall.getId() + " is down (" + thrall.getMercyState() + ")"
                + (variantGiven ? ", next variant " + variantName(variant) : ""));
    }

    /**
     * 把演示目标摆到面前、面朝玩家，标成演示用再加进世界。
     *
     * @return null 表示成功；否则是失败原因
     */
    @Nullable
    private static String place(ServerPlayer p, AbstractKuva e) {
        ServerLevel level = p.serverLevel();
        Vec3 at = spawnSpot(p, e);
        if (at == null) {
            return "no room in front of you";
        }
        float yaw = (float) (Mth.atan2(p.getZ() - at.z, p.getX() - at.x) * Mth.RAD_TO_DEG) - 90.0F;
        e.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        // 照命令召唤的样子配装备；不存盘之外再关掉随机消失，录像时走远一点也不会突然没了
        ForgeEventFactory.onFinalizeSpawn(e, level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.COMMAND,
                null, null);
        e.setPersistenceRequired();
        // 先标成演示用再加进世界：加入世界的检查和存盘都认这个标记
        e.markDemo();
        if (!level.addFreshEntity(e)) {
            return "the world refused the entity";
        }
        return null;
    }

    /** 面前 3 格附近找一处站得下、脚下有地的位置；都没有地就退回第一处站得下的 */
    @Nullable
    static Vec3 spawnSpot(ServerPlayer p, AbstractKuva e) {
        ServerLevel level = p.serverLevel();
        Vec3 f = horizontalLook(p);
        Vec3 floating = null;
        for (double d : SPAWN_TRIES) {
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos feet = BlockPos.containing(p.getX() + f.x * d, p.getY() + dy, p.getZ() + f.z * d);
                Vec3 at = new Vec3(p.getX() + f.x * d, feet.getY(), p.getZ() + f.z * d);
                AABB box = e.getType().getDimensions().makeBoundingBox(at);
                if (!level.noCollision(e, box)) {
                    continue;
                }
                BlockPos below = feet.below();
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    return at;
                }
                if (floating == null) {
                    floating = at;
                }
            }
        }
        return floating;
    }

    private static Vec3 horizontalLook(ServerPlayer p) {
        Vec3 look = p.getLookAngle();
        double len = Math.sqrt(look.x * look.x + look.z * look.z);
        if (len < 1.0E-4D) {
            float r = p.getYRot() * Mth.DEG_TO_RAD;
            return new Vec3(-Mth.sin(r), 0.0D, Mth.cos(r));
        }
        return new Vec3(look.x / len, 0.0D, look.z / len);
    }

    // ==================== down ====================

    private static int downAimed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        AbstractKuva target = aimed(p, e -> e.isAlive() && e.getMercyState() == MercyState.NONE);
        if (target == null) {
            return fail(ctx, "no Kuva Lich or Thrall in sight (" + (int) AIM_RANGE + " blocks)");
        }
        String err = downOne(target, p);
        return err == null ? ok(ctx, describe(target) + " is down") : fail(ctx, err);
    }

    private static int downTargets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(ctx, "targets");
        ServerPlayer p = ctx.getSource().getPlayer();
        int done = 0;
        String lastError = "no Kuva Lich or Thrall selected";
        for (Entity e : entities) {
            if (!(e instanceof AbstractKuva k)) {
                continue;
            }
            String err = downOne(k, p);
            if (err == null) {
                done++;
            } else {
                lastError = err;
            }
        }
        if (done == 0) {
            return fail(ctx, lastError);
        }
        int count = done;
        return ok(ctx, count + " downed");
    }

    /**
     * 直接打倒一只：奴仆和演示目标的资格人是下命令的人（控制台下的就没有资格人，到时消散），有主玄骸永远是主人。
     *
     * @return null 表示倒下了；否则是失败原因
     */
    @Nullable
    private static String downOne(AbstractKuva target, @Nullable ServerPlayer by) {
        if (!ModConfig.KUVA_LICH.mercyEnabled.get()) {
            return "mercy is disabled in the config (kuva_lich.mercyEnabled = false)";
        }
        if (!target.isAlive()) {
            return describe(target) + " is dead";
        }
        if (target.getMercyState() != MercyState.NONE) {
            return describe(target) + " is already " + target.getMercyState();
        }
        if (target instanceof KuvaMasterEntity lich) {
            if (lich.isDeparting()) {
                return describe(target) + " is leaving";
            }
            if (!lich.isOwned() && !lich.isDemo()) {
                return describe(target) + " is a wild Lich: wild Liches never go down";
            }
        }
        target.forceDowned(by);
        if (target.getMercyState() != MercyState.DOWNED) {
            return describe(target) + " did not go down";
        }
        return null;
    }

    // ==================== exec / next / outcome / cards ====================

    /**
     * {@code exec [<套路>] [<结果>]}：第一个参数可以是套路（或 random），也可以直接是结果
     */
    private static int exec(CommandContext<CommandSourceStack> ctx, @Nullable String first, @Nullable String second)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!ModConfig.KUVA_LICH.mercyEnabled.get()) {
            return disabled(ctx);
        }
        String variantArg = null;
        String outcomeArg = second;
        if (first != null) {
            if (second == null && parseOutcome(first) != null && parseVariant(first) == null) {
                outcomeArg = first;
            } else {
                variantArg = first;
            }
        }
        AbstractKuva target = aimed(p, e -> e.isAlive() && e.getMercyState() == MercyState.DOWNED);
        if (target == null) {
            return fail(ctx, "no downed target in sight (" + (int) AIM_RANGE + " blocks)");
        }
        boolean thrall = target instanceof KuvaSlaveEntity;
        MercyVariant variant = null;
        boolean setVariant = false;
        if (variantArg != null && !"random".equalsIgnoreCase(variantArg)) {
            variant = parseVariant(variantArg);
            if (variant == null) {
                return fail(ctx, "unknown variant: " + variantArg);
            }
            if (variant.thrall != thrall) {
                return fail(ctx, variantName(variant) + " does not fit " + describe(target));
            }
            setVariant = true;
        }
        MercyServer.Outcome outcome = null;
        if (outcomeArg != null) {
            outcome = parseOutcome(outcomeArg);
            if (outcome == null) {
                return fail(ctx, "outcome must be one of " + String.join(", ", OUTCOMES) + ": " + outcomeArg);
            }
            if (outcome != MercyServer.Outcome.REAL) {
                if (thrall) {
                    return fail(ctx, "a Thrall execution has no outcome to choose");
                }
                if (!target.isDemo()) {
                    return fail(ctx, "a real Lich is judged by the cards only");
                }
            }
        }
        if (setVariant) {
            MercyServer.setNextVariant(p, variant);
        }
        if (outcome != null) {
            MercyServer.setNextOutcome(p, outcome);
        }
        String err = MercyServer.executeNow(p, target);
        if (err != null) {
            // 没开始就别让这次指定的留到下一次
            if (setVariant) {
                MercyServer.setNextVariant(p, null);
            }
            if (outcome != null) {
                MercyServer.setNextOutcome(p, null);
            }
            return fail(ctx, err);
        }
        MercyPlan.Plan plan = MercyPlan.decode(target.getMercyPlan());
        return ok(ctx, "executing " + describe(target) + ": " + variantName(plan.variant()) + ", " + plan.stabs()
                + (plan.stabs() == 1 ? " stab" : " stabs, " + plan.prefix() + " right")
                + (plan.demo() ? " (demo)" : ""));
    }

    private static int next(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if ("random".equalsIgnoreCase(arg)) {
            MercyServer.setNextVariant(p, null);
            return ok(ctx, "next variant: random");
        }
        MercyVariant v = parseVariant(arg);
        if (v == null) {
            return fail(ctx, "unknown variant: " + arg);
        }
        MercyServer.setNextVariant(p, v);
        return ok(ctx, "next " + (v.thrall ? "Thrall" : "Lich") + " variant: " + variantName(v));
    }

    private static int outcome(CommandContext<CommandSourceStack> ctx, String arg) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        MercyServer.Outcome o = parseOutcome(arg);
        if (o == null) {
            return fail(ctx, "outcome must be one of " + String.join(", ", OUTCOMES) + ": " + arg);
        }
        if (o != MercyServer.Outcome.REAL) {
            AbstractKuva aimedAt = aimed(p, AbstractKuva::isAlive);
            if (aimedAt instanceof KuvaMasterEntity lich && !lich.isDemo()) {
                return fail(ctx, "a real Lich is judged by the cards only; outcome works on demo targets");
            }
        }
        MercyServer.setNextOutcome(p, o);
        return ok(ctx, o == MercyServer.Outcome.REAL ? "next demo execution: judged by your cards"
                : "next demo execution: " + o.name().toLowerCase(Locale.ROOT));
    }

    /**
     * {@code cards <c1> <c2> <c3> [<r1> <r2> <r3>] [return]}：卡名或 0~8（{@code -} = 空格子），处决前剩余次数 0~3，
     * 带 {@code return} 就演「归还物品」；{@code cards clear} 回到用身上的卡
     */
    private static int cards(CommandContext<CommandSourceStack> ctx, String spec) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String[] tokens = spec.strip().toLowerCase(Locale.ROOT).split("\\s+");
        if (tokens.length == 1 && ("clear".equals(tokens[0]) || "off".equals(tokens[0]))) {
            MercyServer.setDemoCards(p, null, null, false);
            return ok(ctx, "demo cards cleared: your own cards are shown");
        }
        boolean returns = false;
        List<String> rest = new ArrayList<>();
        for (String t : tokens) {
            if ("return".equals(t) || "returns".equals(t)) {
                returns = true;
            } else if (!t.isEmpty()) {
                rest.add(t);
            }
        }
        if (rest.size() != 3 && rest.size() != 6) {
            return fail(ctx, "give three cards, optionally followed by three counts (0~3) and \"return\"");
        }
        int[] ids = new int[3];
        for (int i = 0; i < 3; i++) {
            ids[i] = parseCard(rest.get(i));
            if (ids[i] == Integer.MIN_VALUE) {
                return fail(ctx, "unknown card: " + rest.get(i) + " (use " + String.join(", ", CARD_NAMES) + ", 0~8 or -)");
            }
        }
        int[] remaining = null;
        if (rest.size() == 6) {
            remaining = new int[3];
            for (int i = 0; i < 3; i++) {
                try {
                    remaining[i] = Integer.parseInt(rest.get(3 + i));
                } catch (NumberFormatException ex) {
                    return fail(ctx, "uses left must be 0~3: " + rest.get(3 + i));
                }
                if (remaining[i] < 0 || remaining[i] > 3) {
                    return fail(ctx, "uses left must be 0~3: " + remaining[i]);
                }
            }
        }
        MercyServer.setDemoCards(p, ids, remaining, returns);
        StringBuilder sb = new StringBuilder("demo cards:");
        for (int i = 0; i < 3; i++) {
            sb.append(' ').append(ids[i] < 0 ? "-" : CARD_NAMES[ids[i]]);
            if (remaining != null) {
                sb.append('(').append(remaining[i]).append(')');
            }
        }
        if (returns) {
            sb.append(", with returned items");
        }
        return ok(ctx, sb.toString());
    }

    // ==================== timer / state / freeze / reset / stop ====================

    private static int timer(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        AbstractKuva target = aimed(p, e -> e.isAlive() && e.getMercyState() == MercyState.DOWNED);
        if (target == null) {
            return fail(ctx, "no downed target in sight (" + (int) AIM_RANGE + " blocks)");
        }
        if (!MercyServer.setTimer(target, seconds)) {
            return fail(ctx, describe(target) + " is not down");
        }
        return ok(ctx, describe(target) + " stays down for " + seconds + " s"
                + (MercyServer.isFrozen(target.level()) ? " (timers are frozen here)" : ""));
    }

    private static int stateAdapt(CommandContext<CommandSourceStack> ctx, int layers) throws CommandSyntaxException {
        int param = KuvaStateKind.adaptParam(layers, AbstractKuva.DamageCategory.MELEE.ordinal(), true, false);
        return state(ctx, KuvaStateKind.ADAPT, param, "adapt " + layers);
    }

    private static int stateFull(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int param = KuvaStateKind.adaptParam(6, AbstractKuva.DamageCategory.MELEE.ordinal(), true, true);
        return state(ctx, KuvaStateKind.ADAPT, param, "adapt full");
    }

    private static int stateGuard(CommandContext<CommandSourceStack> ctx, int percent) throws CommandSyntaxException {
        return state(ctx, KuvaStateKind.GUARD, KuvaStateKind.guardParam(percent, true), "guard " + percent + "%");
    }

    private static int stateStrike(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        // 吸血那条线从下命令的人身上飞回目标，所以「受击者」就是下命令的人
        return state(ctx, KuvaStateKind.STRIKE, KuvaStateKind.strikeParam(DEMO_STRIKE_DRAIN_TENTHS, true), "strike");
    }

    private static int stateWeak(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return state(ctx, KuvaStateKind.WEAK, 0, "weak point");
    }

    /** 对准星指着的那只播一次特殊状态（只演画面，不改任何伤害数值） */
    private static int state(CommandContext<CommandSourceStack> ctx, int kind, int param, String label)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        AbstractKuva target = aimed(p, AbstractKuva::isAlive);
        if (target == null) {
            return fail(ctx, "no Kuva Lich or Thrall in sight (" + (int) AIM_RANGE + " blocks)");
        }
        KuvaStateServer.demo(target, kind, param, p);
        return ok(ctx, label + " on " + describe(target)
                + (target.getMercyState() != MercyState.NONE ? " (it is " + target.getMercyState() + ")" : ""));
    }

    private static int stateFury(CommandContext<CommandSourceStack> ctx, int tier) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        AbstractKuva target = aimed(p, AbstractKuva::isAlive);
        if (target == null) {
            return fail(ctx, "no Kuva Lich or Thrall in sight (" + (int) AIM_RANGE + " blocks)");
        }
        int permille = FURY_TIER_FLOOR[tier];
        target.setFuryOverride(permille, FURY_OVERRIDE_TICKS);
        return ok(ctx, describe(target) + " fury tier " + tier + " (" + permille + " permille) for "
                + FURY_OVERRIDE_TICKS / 20 + " s");
    }

    private static int freeze(CommandContext<CommandSourceStack> ctx, boolean frozen) {
        ServerLevel level = ctx.getSource().getLevel();
        MercyServer.setFrozen(level, frozen);
        return ok(ctx, "downed timers in " + level.dimension().location() + (frozen ? " frozen" : " running again"));
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = MercyServer.resetAround(level, ctx.getSource().getPosition(), RESET_RADIUS);
        return ok(ctx, n + " target(s) within " + (int) RESET_RADIUS + " blocks reset");
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String err = MercyServer.stop(p);
        return err == null ? ok(ctx, "execution stopped, the target is down again") : fail(ctx, err);
    }

    // ==================== 找目标 ====================

    /**
     * 准星指着的赤毒实体：先沿视线打一条 16 格的射线（被方块挡住就截在方块前），没打中就取视线 25° 以内、
     * 16 格内离视线最近的那只（同样要看得见）
     */
    @Nullable
    static AbstractKuva aimed(ServerPlayer p, Predicate<AbstractKuva> filter) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 far = eye.add(look.scale(AIM_RANGE));
        BlockHitResult block = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = block.getType() == HitResult.Type.MISS ? AIM_RANGE : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(reach));
        AABB sweep = p.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, sweep,
                e -> e instanceof AbstractKuva k && filter.test(k), reach * reach);
        if (hit != null && hit.getEntity() instanceof AbstractKuva k) {
            return k;
        }
        AbstractKuva best = null;
        double bestCos = AIM_CONE_COS;
        AABB around = p.getBoundingBox().inflate(AIM_RANGE);
        for (AbstractKuva k : p.level().getEntitiesOfClass(AbstractKuva.class, around, filter)) {
            Vec3 to = k.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 1.0E-3D || dist > AIM_RANGE) {
                continue;
            }
            double cos = to.dot(look) / dist;
            if (cos > bestCos && p.hasLineOfSight(k)) {
                bestCos = cos;
                best = k;
            }
        }
        return best;
    }

    // ==================== 解析与反馈 ====================

    @Nullable
    static MercyVariant parseVariant(String s) {
        String key = s.toLowerCase(Locale.ROOT);
        for (MercyVariant v : MercyVariant.values()) {
            if (variantName(v).equals(key)) {
                return v;
            }
        }
        return null;
    }

    static String variantName(@Nullable MercyVariant v) {
        return v == null ? "random" : v.name().toLowerCase(Locale.ROOT);
    }

    /** {@code thrall} 为 null 时两类都列 */
    private static List<String> variantNames(@Nullable Boolean thrall, boolean withRandom) {
        List<String> out = new ArrayList<>();
        if (withRandom) {
            out.add("random");
        }
        for (MercyVariant v : MercyVariant.values()) {
            if (thrall == null || v.thrall == thrall) {
                out.add(variantName(v));
            }
        }
        return out;
    }

    /** 只在第一个词的位置提示（后面是名字，不提示） */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFirstWord(
            SuggestionsBuilder b, List<String> options) {
        if (b.getRemaining().indexOf(' ') >= 0) {
            return b.buildFuture();
        }
        return SharedSuggestionProvider.suggest(options, b);
    }

    @Nullable
    static MercyServer.Outcome parseOutcome(String s) {
        String key = s.toLowerCase(Locale.ROOT);
        for (MercyServer.Outcome o : MercyServer.Outcome.values()) {
            if (o.name().toLowerCase(Locale.ROOT).equals(key)) {
                return o;
            }
        }
        return null;
    }

    /** 卡名、0~8 或 {@code -}（空格子，返回 -1）；认不出返回 {@link Integer#MIN_VALUE} */
    static int parseCard(String s) {
        if ("-".equals(s) || "none".equals(s) || "empty".equals(s)) {
            return -1;
        }
        for (int i = 0; i < CARD_NAMES.length; i++) {
            if (CARD_NAMES[i].equals(s)) {
                return i;
            }
        }
        try {
            int id = Integer.parseInt(s);
            return id >= 0 && id < CARD_NAMES.length ? id : Integer.MIN_VALUE;
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }

    private static String describe(AbstractKuva e) {
        StringBuilder sb = new StringBuilder();
        if (e instanceof KuvaMasterEntity lich) {
            sb.append(lich.isDemo() ? "demo Lich " : lich.isOwned() ? "Lich " : "wild Lich ");
            String name = lich.getLichName();
            if (!name.isEmpty()) {
                sb.append(name).append(' ');
            }
        } else {
            sb.append(e.isDemo() ? "demo Thrall " : "Thrall ");
        }
        return sb.append('#').append(e.getId()).toString();
    }

    private static int disabled(CommandContext<CommandSourceStack> ctx) {
        return fail(ctx, "mercy is disabled in the config (kuva_lich.mercyEnabled = false)");
    }

    private static int ok(CommandContext<CommandSourceStack> ctx, String detail) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.kuvalich.mercy.ok", detail), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String detail) {
        ctx.getSource().sendFailure(Component.translatable("commands.kuvalich.mercy.fail", detail));
        return 0;
    }
}
