package pers.roinflam.kuvalich.nemesis;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaLarvlingEntity;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.fx.ServerFxQueue;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 专属玄骸的管理 / 录像命令 {@code /kuvalich lich …}，权限 2，正式版也有
 *
 * <p>服主处理玩家的问题、录演示都用得上：查状态、补生、立刻召来、直接倒地、让它走、改怒气 / 冷却 / 暂停、
 * 指定一句台词（类名写 {@code random} 就随便挑一类）、往处决记录里塞一行、直接设等级、改名、作废这一轮。反馈是管理用的英文细节，套在
 * {@code commands.kuvalich.lich.ok} / {@code .fail} 里。</p>
 *
 * <p>录一段完整演示的顺序：{@code pause on} → {@code birth} → {@code attempt} 塞几行 → 开档案录 →
 * {@code summon front} 录登场 → {@code down} 录处决 → {@code taunt} 录通讯窗。</p>
 *
 * <p>幼体、奴仆小队和两边导演的状态另有几条（同样权限 2）：</p>
 * <ul>
 *   <li>{@code /kuvalich larvling spawn <玩家> [front|hidden]}：立刻在他身边放一只幼体（默认面前 5~9 格，
 *       hidden = 照导演的规矩放在看不见的地方），记成他的，原来那只收掉；</li>
 *   <li>{@code /kuvalich larvling due <玩家> [秒]}：把幼体计时拨到还剩几秒（默认 3），顺带去掉登录宽限；</li>
 *   <li>{@code /kuvalich larvling down <玩家>}：他那只幼体直接倒地，资格人照规矩定；</li>
 *   <li>{@code /kuvalich thrall squad <玩家> [只数]}：立刻在他身边放一队奴仆（只数默认按配置随机，不看在场上限）；</li>
 *   <li>{@code /kuvalich thrall due <玩家> [秒]}：把下一队拨到几秒后（默认 3）；</li>
 *   <li>{@code /kuvalich director <玩家>}：玄骸、幼体、奴仆小队三边导演眼下的状态。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class NemesisCommand {

    /** {@code down} 召出来的那只离玩家几格 */
    private static final double DOWN_DISTANCE = 4.0D;
    /** {@code down} 新召出来的要等显形演出放完再倒地（tick），和导演给的显形时长一样 */
    private static final int DOWN_AFTER_APPEAR = 12;

    private static final SuggestionProvider<CommandSourceStack> LINES = (ctx, b) -> SharedSuggestionProvider.suggest(
            Stream.concat(Stream.of("random"), Arrays.stream(LichLine.values()).filter(l -> l != LichLine.ARCHIVE)
                    .map(l -> l.name().toLowerCase(Locale.ROOT))), b);
    private static final SuggestionProvider<CommandSourceStack> RESULTS =
            (ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"correct", "wrong", "none"}, b);

    private static final SuggestionProvider<CommandSourceStack> EPHEMERA_TYPES = (ctx, b) ->
            SharedSuggestionProvider.suggest(pers.roinflam.kuvalich.ephemera.EphemeraType.all().stream()
                    .map(pers.roinflam.kuvalich.ephemera.EphemeraType::id), b);
    private static final SuggestionProvider<CommandSourceStack> EPHEMERA_ELEMENTS = (ctx, b) ->
            SharedSuggestionProvider.suggest(pers.roinflam.kuvalich.utils.KuvaWeaponUtil.LICH_ELEMENTS, b);

    private NemesisCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> lich = Commands.literal("lich");

        lich.then(Commands.literal("info").then(target().executes(NemesisCommand::info)));

        lich.then(Commands.literal("birth").then(target()
                .executes(ctx -> birth(ctx, null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> birth(ctx, StringArgumentType.getString(ctx, "name"))))));

        lich.then(Commands.literal("summon").then(target()
                .executes(ctx -> summon(ctx, SummonMode.FRONT))
                .then(Commands.literal("front").executes(ctx -> summon(ctx, SummonMode.FRONT)))
                .then(Commands.literal("hunt").executes(ctx -> summon(ctx, SummonMode.HUNT)))
                .then(Commands.literal("ultimatum").executes(ctx -> summon(ctx, SummonMode.ULTIMATUM)))));

        lich.then(Commands.literal("down").then(target().executes(NemesisCommand::down)));
        lich.then(Commands.literal("depart").then(target().executes(NemesisCommand::depart)));

        lich.then(Commands.literal("rage").then(target()
                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1.0F))
                        .executes(ctx -> rage(ctx, FloatArgumentType.getFloat(ctx, "value"))))));

        lich.then(Commands.literal("cooldown").then(target()
                .then(Commands.literal("clear").executes(NemesisCommand::cooldownClear))));

        lich.then(Commands.literal("pause").then(target()
                .then(Commands.literal("on").executes(ctx -> pause(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> pause(ctx, false)))));

        lich.then(Commands.literal("taunt").then(target()
                .then(Commands.argument("line", StringArgumentType.word()).suggests(LINES)
                        .executes(ctx -> taunt(ctx, 0))
                        .then(Commands.argument("index", IntegerArgumentType.integer(1))
                                .executes(ctx -> taunt(ctx, IntegerArgumentType.getInteger(ctx, "index")))))));

        lich.then(Commands.literal("attempt").then(target()
                .then(Commands.argument("r1", StringArgumentType.word()).suggests(RESULTS)
                        .then(Commands.argument("r2", StringArgumentType.word()).suggests(RESULTS)
                                .then(Commands.argument("r3", StringArgumentType.word()).suggests(RESULTS)
                                        .executes(NemesisCommand::attempt))))));

        lich.then(Commands.literal("rename").then(target()
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> rename(ctx, StringArgumentType.getString(ctx, "name"))))));

        lich.then(Commands.literal("rank").then(target()
                .then(Commands.argument("rank", IntegerArgumentType.integer(1, 99))
                        .executes(ctx -> rank(ctx, IntegerArgumentType.getInteger(ctx, "rank"))))));

        lich.then(Commands.literal("reset").then(target().executes(NemesisCommand::reset)));

        LiteralArgumentBuilder<CommandSourceStack> larvling = Commands.literal("larvling");
        larvling.then(Commands.literal("spawn").then(target()
                .executes(ctx -> larvlingSpawn(ctx, true))
                .then(Commands.literal("front").executes(ctx -> larvlingSpawn(ctx, true)))
                .then(Commands.literal("hidden").executes(ctx -> larvlingSpawn(ctx, false)))));
        larvling.then(Commands.literal("due").then(target()
                .executes(ctx -> larvlingDue(ctx, 3))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 36000))
                        .executes(ctx -> larvlingDue(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))));
        larvling.then(Commands.literal("down").then(target().executes(NemesisCommand::larvlingDown)));

        LiteralArgumentBuilder<CommandSourceStack> thrall = Commands.literal("thrall");
        thrall.then(Commands.literal("squad").then(target()
                .executes(ctx -> thrallSquad(ctx, 0))
                .then(Commands.argument("size", IntegerArgumentType.integer(1, 8))
                        .executes(ctx -> thrallSquad(ctx, IntegerArgumentType.getInteger(ctx, "size"))))));
        thrall.then(Commands.literal("due").then(target()
                .executes(ctx -> thrallDue(ctx, 3))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 36000))
                        .executes(ctx -> thrallDue(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))));

        LiteralArgumentBuilder<CommandSourceStack> ephemera = Commands.literal("ephemera");
        // give <玩家> <款式> [元素] [来历]：不写元素按默认元素
        ephemera.then(Commands.literal("give").then(target()
                .then(Commands.argument("type", StringArgumentType.word()).suggests(EPHEMERA_TYPES)
                        .executes(ctx -> ephemeraGive(ctx, null, ""))
                        .then(Commands.argument("element", StringArgumentType.word()).suggests(EPHEMERA_ELEMENTS)
                                .executes(ctx -> ephemeraGive(ctx, StringArgumentType.getString(ctx, "element"), ""))
                                .then(Commands.argument("origin", StringArgumentType.greedyString())
                                        .executes(ctx -> ephemeraGive(ctx, StringArgumentType.getString(ctx, "element"),
                                                StringArgumentType.getString(ctx, "origin"))))))));
        // roll：按种子和当前概率重掷现役玄骸的幻纹；带款式（或 none）就直接指定
        ephemera.then(Commands.literal("roll").then(target()
                .executes(ctx -> ephemeraRoll(ctx, null))
                .then(Commands.argument("type", StringArgumentType.word()).suggests(EPHEMERA_TYPES)
                        .executes(ctx -> ephemeraRoll(ctx, StringArgumentType.getString(ctx, "type"))))));

        LiteralArgumentBuilder<CommandSourceStack> director = Commands.literal("director");
        director.then(target().executes(NemesisCommand::director));

        // 权限挂在各个子命令上，不挂在根上：inbox 人人能用，而同名的根节点只认第一次注册时的权限
        d.register(Commands.literal(Reference.MOD_ID)
                .then(lich.requires(NemesisCommand::admin))
                .then(larvling.requires(NemesisCommand::admin))
                .then(thrall.requires(NemesisCommand::admin))
                .then(ephemera.requires(NemesisCommand::admin))
                .then(director.requires(NemesisCommand::admin))
                .then(Commands.literal("inbox").executes(NemesisCommand::inbox)));
    }

    public static boolean admin(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    /** 玩家自己查收件箱：报还有几件，并且马上试投一次。登录后的 10 秒等待照样要等，那是留给同步背包的插件的 */
    private static int inbox(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (PlayerLedger.inboxCount(p) == 0) {
            PlayerFeedback.chat(p, KuvaPalette.INFO, "message.kuvalich.inbox.empty");
            return 0;
        }
        if (!Inbox.canReceive(p)) {
            PlayerFeedback.chat(p, KuvaPalette.WARN, "message.kuvalich.inbox.waiting", PlayerLedger.inboxCount(p));
            return 0;
        }
        if (Inbox.inLoginDelay(p)) {
            PlayerFeedback.chat(p, KuvaPalette.WARN, "message.kuvalich.inbox.loginDelay", PlayerLedger.inboxCount(p));
            return 0;
        }
        int moved = Inbox.deliver(p, false);
        int left = PlayerLedger.inboxCount(p);
        if (left == 0) {
            PlayerFeedback.chat(p, KuvaPalette.SUCCESS, "message.kuvalich.inbox.done", moved);
        } else if (moved == 0) {
            PlayerFeedback.chat(p, KuvaPalette.WARN, "message.kuvalich.inbox.full", left);
        } else {
            PlayerFeedback.chat(p, KuvaPalette.WARN, "message.kuvalich.inbox.partial", moved, left);
        }
        return moved;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> target() {
        return Commands.argument("player", EntityArgument.player());
    }

    // ==================== 子命令 ====================

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null) {
            return fail(ctx, p.getGameProfile().getName() + " has no requiem data");
        }
        LichState state = LichDirector.stateOf(p);
        StringBuilder sb = new StringBuilder();
        sb.append(p.getGameProfile().getName()).append(": state=").append(state);
        LichDirector.HuntView view = LichDirector.view(p);
        if (view != null && view.waitReason() != WaitReason.NONE) {
            sb.append(" (waiting: ").append(view.waitReason()).append(')');
        }
        sb.append(", riddle stage=").append(card.getUnlockedCardStatus())
                .append(", progress=").append(card.getDecryptionProgress()).append('/').append(card.getPointsRequired());
        if (card.hasLich()) {
            long now = p.server.overworld().getGameTime();
            long cooldownLeft = Math.max(0L, card.getLichCooldownUntil() - now);
            sb.append(", name=").append(card.getLichName())
                    .append(", seed=").append(Long.toHexString(card.getLichSeed()))
                    .append(", traits=[").append(LichTraits.current(card.getLichSeed(), card.getLichRank()).describe()).append(']')
                    .append(", persona=").append(LichPersona.of((int) card.getLichSeed()).id)
                    .append(", generation=").append(card.getLichGeneration())
                    .append(", rank=").append(card.getLichRank())
                    .append(", rage=").append(String.format(Locale.ROOT, "%.3f", card.getLichRage()))
                    .append(" (").append(RageTier.of(card.getLichRage())).append(')')
                    .append(", cooldown=").append((cooldownLeft + 19) / 20).append("s")
                    .append(", encounters=").append(card.getLichEncounters())
                    .append(", weapon=").append(card.getLichWeapon())
                    .append(", paused=").append(card.isLichPaused());
            KuvaMasterEntity lich = view == null ? null : view.lich();
            if (lich != null) {
                sb.append(", entity #").append(lich.getId()).append(" at ")
                        .append(String.format(Locale.ROOT, "%.1f %.1f %.1f", lich.getX(), lich.getY(), lich.getZ()))
                        .append(" in ").append(lich.level().dimension().location())
                        .append(", hp=").append(String.format(Locale.ROOT, "%.0f/%.0f", lich.getHealth(), lich.getMaxHealth()))
                        .append(", mercy=").append(lich.getMercyState());
            } else if (view != null && view.spawnAt() >= 0) {
                long now2 = p.server.overworld().getGameTime();
                sb.append(", appears in ").append(Math.max(0L, view.spawnAt() - now2)).append(" ticks");
            }
        }
        RequiemCard.HistoryEntry last = card.getLastHistory();
        if (last != null) {
            sb.append(", last=").append(last.name()).append(" (fate ").append(last.fate()).append(')');
        }
        return ok(ctx, sb.toString());
    }

    private static int birth(CommandContext<CommandSourceStack> ctx, @Nullable String name) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null) {
            return fail(ctx, p.getGameProfile().getName() + " has no requiem data");
        }
        if (card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " already has a lich: " + card.getLichName());
        }
        String wanted = name == null ? null : name.strip();
        if (wanted != null && !LichNames.isAllowed(wanted)) {
            return fail(ctx, "name not allowed (max " + LichNames.MAX_LENGTH + " chars, no profanity): " + wanted);
        }
        // 不经过幼体直接诞生（随机种子），诞生通讯照常放；密语阶段、进度都不动
        if (!LichDirector.birthNow(p) || !card.hasLich()) {
            return fail(ctx, "birth did not happen (riddle stage " + card.getUnlockedCardStatus() + ")");
        }
        if (wanted != null) {
            // 诞生通讯排在 30 tick 之后才发，这时改名，台词里用的就是新名字
            card.setLichName(wanted);
        }
        return ok(ctx, p.getGameProfile().getName() + " now has " + card.getLichName()
                + " (generation " + card.getLichGeneration() + ")");
    }

    private static int summon(CommandContext<CommandSourceStack> ctx, SummonMode mode) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        if (!LichDirector.summonNow(p, mode)) {
            LichState state = LichDirector.stateOf(p);
            return fail(ctx, "cannot summon " + card.getLichName() + " (" + mode.name().toLowerCase(Locale.ROOT)
                    + "): state=" + state + (mode == SummonMode.FRONT ? ", or no room in front" : ""));
        }
        return ok(ctx, "summoning " + card.getLichName() + " (" + mode.name().toLowerCase(Locale.ROOT) + ")");
    }

    private static int down(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        if (!ModConfig.KUVA_LICH.mercyEnabled.get()) {
            return fail(ctx, "mercy is disabled in the config (kuva_lich.mercyEnabled = false)");
        }
        KuvaMasterEntity present = LichDirector.getLich(p).orElse(null);
        if (present != null) {
            if (present.isDeparting() || !present.isAlive()) {
                return fail(ctx, card.getLichName() + " is leaving");
            }
            return downNow(ctx.getSource(), p, present);
        }
        Vec3 look = p.getLookAngle();
        double len = Math.sqrt(look.x * look.x + look.z * look.z);
        double fx = len < 1.0E-4D ? -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD) : look.x / len;
        double fz = len < 1.0E-4D ? Mth.cos(p.getYRot() * Mth.DEG_TO_RAD) : look.z / len;
        BlockPos feet = BlockPos.containing(p.getX() + fx * DOWN_DISTANCE, p.getY(), p.getZ() + fz * DOWN_DISTANCE);
        if (!LichDirector.summonNow(p, SummonMode.FRONT, feet)) {
            return fail(ctx, "cannot summon " + card.getLichName() + " in front: state="
                    + LichDirector.stateOf(p) + ", or no room");
        }
        KuvaMasterEntity lich = LichDirector.getLich(p).orElse(null);
        if (lich == null) {
            return fail(ctx, "summon reported success but no lich is present");
        }
        CommandSourceStack source = ctx.getSource();
        ServerFxQueue.schedule(DOWN_AFTER_APPEAR, p, () -> {
            if (lich.isAlive() && !lich.isRemoved() && !lich.isDeparting() && lich.getEncounterEnd() == null
                    && !p.hasDisconnected()) {
                downNow(source, p, lich);
            }
        });
        source.sendSuccess(() -> Component.translatable("commands.kuvalich.lich.ok",
                "summoned " + card.getLichName() + ", downing in " + DOWN_AFTER_APPEAR + " ticks"), true);
        return 1;
    }

    /** 让它倒地；状态没变就是它这会儿不能倒地（怜悯之击关着，或者已经倒地 / 正在离场 / 已经死了），说一声 */
    private static int downNow(CommandSourceStack source, ServerPlayer p, KuvaMasterEntity lich) {
        lich.forceDowned(p);
        if (lich.getMercyState() == MercyState.NONE) {
            source.sendFailure(Component.translatable("commands.kuvalich.lich.down_refused", lich.getLichName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.kuvalich.lich.downed", lich.getLichName()), true);
        return 1;
    }

    private static int depart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        KuvaMasterEntity lich = LichDirector.getLich(p).orElse(null);
        if (lich == null) {
            return fail(ctx, p.getGameProfile().getName() + "'s lich is not present");
        }
        if (lich.getEncounterEnd() != null || lich.isDeparting()) {
            return fail(ctx, lich.getLichName() + " is already leaving");
        }
        LichDirector.endEncounter(p, lich, EncounterEnd.ADMIN, true);
        return ok(ctx, lich.getLichName() + " departs (admin, no cooldown)");
    }

    private static int rage(CommandContext<CommandSourceStack> ctx, float value) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        card.setLichRage(value);
        return ok(ctx, card.getLichName() + " rage = " + String.format(Locale.ROOT, "%.3f", card.getLichRage())
                + " (" + RageTier.of(card.getLichRage()) + ")");
    }

    private static int cooldownClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        card.setLichCooldownUntil(0L);
        return ok(ctx, card.getLichName() + " cooldown cleared");
    }

    private static int pause(CommandContext<CommandSourceStack> ctx, boolean paused) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null) {
            return fail(ctx, p.getGameProfile().getName() + " has no requiem data");
        }
        // 没有玄骸时也能先暂停：录像前先 pause on 再 birth，诞生后不会马上被来找
        card.setLichPaused(paused);
        return ok(ctx, p.getGameProfile().getName() + " lich " + (paused ? "paused" : "resumed"));
    }

    private static int taunt(CommandContext<CommandSourceStack> ctx, int index) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        String name = StringArgumentType.getString(ctx, "line");
        LichLine line;
        if ("random".equalsIgnoreCase(name)) {
            // 随便挑一类（档案那一类除外），录通讯窗时一条条换着看
            LichLine[] all = Arrays.stream(LichLine.values()).filter(l -> l != LichLine.ARCHIVE).toArray(LichLine[]::new);
            line = all[p.getRandom().nextInt(all.length)];
            if (index > 0) {
                return fail(ctx, "random takes no index");
            }
        } else {
            line = parseLine(name);
        }
        if (line == null) {
            return fail(ctx, "unknown line: " + name);
        }
        if (line == LichLine.ARCHIVE) {
            return fail(ctx, "archive lines are picked on the client when the archive opens");
        }
        if (index > line.count()) {
            return fail(ctx, name + " has only " + line.count() + " lines");
        }
        if (!LichComms.sayExact(p, line, index - 1, null)) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich to speak");
        }
        int sent = Math.max(0, LichComms.lastIndex(p, line));
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.kuvalich.lich.taunt_sent",
                line.name().toLowerCase(Locale.ROOT), sent + 1), true);
        return 1;
    }

    private static int attempt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        String[] raw = {StringArgumentType.getString(ctx, "r1"), StringArgumentType.getString(ctx, "r2"),
                StringArgumentType.getString(ctx, "r3")};
        int[] answers = {card.getOneAnswer(), card.getTwoAnswer(), card.getThreeAnswer()};
        int[] placed = {cardId(card.getOneCard()), cardId(card.getTwoCard()), cardId(card.getThreeCard())};
        int[] ids = new int[3];
        byte[] results = new byte[3];
        boolean judged = true;
        for (int i = 0; i < 3; i++) {
            String r = raw[i].toLowerCase(Locale.ROOT);
            int answer = answers[i] >= 0 && answers[i] <= 7 ? answers[i] : i;
            switch (r) {
                case "correct" -> {
                    ids[i] = answer;
                    results[i] = (byte) (judged ? RequiemCard.ATTEMPT_RIGHT : RequiemCard.ATTEMPT_UNJUDGED);
                }
                case "wrong" -> {
                    // 卡用已放的那张（它本来就不对的话）；否则随便换一张不对的
                    ids[i] = placed[i] >= 0 && placed[i] != answer ? placed[i] : (answer + 1) % 8;
                    results[i] = (byte) (judged ? RequiemCard.ATTEMPT_WRONG : RequiemCard.ATTEMPT_UNJUDGED);
                    judged = false;
                }
                case "none" -> {
                    ids[i] = -1;
                    results[i] = RequiemCard.ATTEMPT_UNJUDGED;
                    judged = false;
                }
                default -> {
                    return fail(ctx, "result must be correct, wrong or none: " + raw[i]);
                }
            }
        }
        int packed = RequiemCard.packAttempt(ids, results, ModConfig.KUVA_LICH.mercyEnabled.get());
        card.recordAttempt(packed);
        return ok(ctx, "attempt recorded for " + card.getLichName() + ": " + describe(packed)
                + " (" + card.getAttempts().length + " kept)");
    }

    /** 直接设玄骸等级（判错次数 + 1）；在场的那只按新等级重套面板 */
    private static int rank(CommandContext<CommandSourceStack> ctx, int rank) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        card.setKuvaLevel(rank - 1);
        LichDirector.HuntView view = LichDirector.view(p);
        KuvaMasterEntity lich = view == null ? null : view.lich();
        if (lich != null) {
            lich.refreshRank(card);
        }
        return ok(ctx, card.getLichName() + " is now rank " + card.getLichRank());
    }

    private static int rename(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        String wanted = name.strip();
        if (!LichNames.isAllowed(wanted)) {
            return fail(ctx, "name not allowed (max " + LichNames.MAX_LENGTH + " chars, no profanity): " + wanted);
        }
        String old = card.getLichName();
        card.setLichName(wanted);
        // 在场的那只跟着改：名牌、Boss 条都读实体上的名字
        LichDirector.getLich(p).ifPresent(lich -> lich.setDemoIdentity(wanted, lich.getLichRank()));
        return ok(ctx, old + " -> " + card.getLichName());
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        String name = card.getLichName();
        LichDirector.getLich(p).ifPresent(lich -> LichDirector.endEncounter(p, lich, EncounterEnd.ADMIN, true));
        // 只作废这一轮玄骸：谜语阶段、答案、手里的卡、被没收的东西都不动，也不给奖励
        card.endLichRound(RequiemCard.FATE_ADMIN);
        return ok(ctx, name + " is gone (reset, no rewards)");
    }

    // ==================== 幼体、奴仆小队、导演状态 ====================

    private static int larvlingSpawn(CommandContext<CommandSourceStack> ctx, boolean front) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        KuvaLarvlingEntity larvling = LarvlingDirector.spawn(p, front);
        if (larvling == null) {
            return fail(ctx, "no room for a larvling near " + p.getGameProfile().getName());
        }
        Item weapon = larvling.getWeaponItem();
        RequiemCard card = card(p);
        String warn = card != null && card.hasLich() ? " (warning: " + p.getGameProfile().getName()
                + " already has a lich and cannot execute it)" : "";
        return ok(ctx, "larvling #" + larvling.getId() + " at " + larvling.blockPosition().toShortString()
                + ", seed=" + Long.toHexString(larvling.getLichSeed()) + ", weapon=" + larvling.getWeaponIndex()
                + (weapon == null ? "" : " " + new ItemStack(weapon).getHoverName().getString()) + warn);
    }

    private static int larvlingDue(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        LarvlingDirector.setWait(p, seconds * 20);
        String blocker = LarvlingDirector.blocker(p);
        return ok(ctx, "next larvling for " + p.getGameProfile().getName() + " in " + seconds + "s of play"
                + (blocker.isEmpty() ? "" : " (blocked now: " + blocker + ")"));
    }

    private static int larvlingDown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        KuvaLarvlingEntity larvling = LarvlingDirector.larvlingOf(p);
        if (larvling == null) {
            return fail(ctx, p.getGameProfile().getName() + " has no larvling out");
        }
        larvling.forceDowned(p);
        if (larvling.getMercyState() == MercyState.NONE) {
            return fail(ctx, "larvling cannot go down right now (mercy disabled, leaving or dead)");
        }
        return ok(ctx, "larvling #" + larvling.getId() + " is down for " + larvling.downedSeconds()
                + "s, claimant=" + (larvling.getMercyQualified() == null ? "none" : larvling.getMercyQualified()));
    }

    private static int thrallSquad(CommandContext<CommandSourceStack> ctx, int size) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        if (size <= 0) {
            int min = ModConfig.KUVA_LICH.thrallSquadSizeMin.get();
            int max = Math.max(min, ModConfig.KUVA_LICH.thrallSquadSizeMax.get());
            size = min + p.getRandom().nextInt(max - min + 1);
        }
        int placed = ThrallDirector.spawnSquad(p, size).size();
        if (placed == 0) {
            return fail(ctx, "no room for thralls near " + p.getGameProfile().getName());
        }
        return ok(ctx, "sent " + placed + "/" + size + " thralls to " + p.getGameProfile().getName());
    }

    private static int thrallDue(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        ThrallDirector.setNext(p, seconds * 20);
        String blocker = card == null || !card.hasLich() ? "no lich" : ThrallDirector.blocker(p);
        return ok(ctx, "next thrall squad for " + p.getGameProfile().getName() + " in " + seconds + "s"
                + (blocker.isEmpty() ? "" : " (blocked now: " + blocker + ")"));
    }

    private static int director(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null) {
            return fail(ctx, p.getGameProfile().getName() + " has no requiem data");
        }
        StringBuilder sb = new StringBuilder(p.getGameProfile().getName());
        sb.append(": lich=").append(card.hasLich() ? card.getLichName() + " (" + LichDirector.stateOf(p) + ")" : "none");
        LarvlingDirector.View lv = LarvlingDirector.view(p);
        sb.append(" | larvling: ");
        if (!ModConfig.KUVA_LICH.larvlingEnabled.get()) {
            sb.append("disabled");
        } else if (lv.larvling() != null) {
            KuvaLarvlingEntity l = lv.larvling();
            sb.append("out #").append(l.getId()).append(" at ").append(l.blockPosition().toShortString())
                    .append(", ").append(Math.round(Math.sqrt(l.distanceToSqr(p)))).append(" blocks away, mercy=")
                    .append(l.getMercyState()).append(", weapon=").append(l.getWeaponIndex());
        } else if (card.hasLich()) {
            sb.append("not needed (has lich)");
        } else {
            sb.append("next in ").append(lv.waitTicks() < 0 ? "?" : String.valueOf((lv.waitTicks() + 19) / 20))
                    .append("s of play");
            if (!lv.blocker().isEmpty()) {
                sb.append(", blocked: ").append(lv.blocker());
            }
        }
        ThrallDirector.View tv = ThrallDirector.view(p);
        sb.append(" | thralls: ").append(tv.nearby()).append('/').append(ModConfig.KUVA_LICH.thrallSquadMaxNearby.get())
                .append(" within ").append(ModConfig.KUVA_LICH.thrallSquadRadius.get()).append(" blocks");
        if (!ModConfig.KUVA_LICH.thrallSquadEnabled.get()) {
            sb.append(", squads disabled");
        } else if (!card.hasLich()) {
            sb.append(", no squads (no lich)");
        } else {
            sb.append(", next squad in ").append(tv.nextInTicks() < 0 ? "?" : String.valueOf((tv.nextInTicks() + 19) / 20))
                    .append("s of unblocked time");
            if (!tv.blocker().isEmpty()) {
                sb.append(", blocked: ").append(tv.blocker());
            }
        }
        int perHour = ModConfig.KUVA_LICH.thrallSquadMaxPerHour.get();
        sb.append(", last hour ").append(tv.lastHour()).append('/').append(perHour <= 0 ? "unlimited" : String.valueOf(perHour));
        int safe = ModConfig.KUVA_LICH.thrallSquadSpawnSafeRadius.get();
        sb.append(", spawn safe ").append(safe <= 0 ? "off" : safe + " blocks").append(" (")
                .append(tv.spawnDistance()).append(" from spawn)");
        return ok(ctx, sb.toString());
    }

    // ==================== 工具 ====================

    @Nullable
    private static RequiemCard card(ServerPlayer p) {
        return p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
    }

    @Nullable
    private static LichLine parseLine(String name) {
        for (LichLine l : LichLine.values()) {
            if (l.name().equalsIgnoreCase(name)) {
                return l;
            }
        }
        return null;
    }

    private static int cardId(ItemStack stack) {
        if (stack != null && !stack.isEmpty()
                && stack.getItem() instanceof AbstractRequiemCard requiem) {
            return requiem.getID();
        }
        return -1;
    }

    private static String describe(int packed) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            int id = RequiemCard.attemptCardId(packed, i);
            int r = RequiemCard.attemptResult(packed, i);
            sb.append(id == RequiemCard.ATTEMPT_NO_CARD ? "-" : String.valueOf(id))
                    .append(r == RequiemCard.ATTEMPT_RIGHT ? "+" : r == RequiemCard.ATTEMPT_WRONG ? "x" : "?");
        }
        return sb.toString();
    }

    private static int ephemeraGive(CommandContext<CommandSourceStack> ctx, @Nullable String element, String origin)
            throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        String id = StringArgumentType.getString(ctx, "type");
        pers.roinflam.kuvalich.ephemera.EphemeraType type = pers.roinflam.kuvalich.ephemera.EphemeraType.byId(id);
        if (type == null) {
            return fail(ctx, "unknown ephemera: " + id);
        }
        if (element != null && !pers.roinflam.kuvalich.ephemera.EphemeraType.isElement(element)) {
            return fail(ctx, "unknown element: " + element);
        }
        String el = pers.roinflam.kuvalich.ephemera.EphemeraType.normalizeElement(element);
        pers.roinflam.kuvalich.utils.InventoryUtil.giveOrDrop(p,
                pers.roinflam.kuvalich.ephemera.EphemeraItem.create(type, el, origin.strip()));
        return ok(ctx, "gave ephemera " + id + " (" + el + ") to " + p.getGameProfile().getName());
    }

    private static int ephemeraRoll(CommandContext<CommandSourceStack> ctx, @Nullable String id)
            throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        RequiemCard card = card(p);
        if (card == null || !card.hasLich()) {
            return fail(ctx, p.getGameProfile().getName() + " has no lich");
        }
        if (id == null) {
            card.rollEphemera(ModConfig.KUVA_LICH.lichEphemeraChance.get());
        } else if ("none".equals(id)) {
            card.setLichEphemera("");
        } else if (pers.roinflam.kuvalich.ephemera.EphemeraType.byId(id) == null) {
            return fail(ctx, "unknown ephemera: " + id);
        } else {
            card.setLichEphemera(id);
        }
        String now = card.getLichEphemera();
        return ok(ctx, card.getLichName() + " ephemera: " + (now.isEmpty() ? "none" : now));
    }

    private static int ok(CommandContext<CommandSourceStack> ctx, String detail) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.kuvalich.lich.ok", detail), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String detail) {
        ctx.getSource().sendFailure(Component.translatable("commands.kuvalich.lich.fail", detail));
        return 0;
    }
}
