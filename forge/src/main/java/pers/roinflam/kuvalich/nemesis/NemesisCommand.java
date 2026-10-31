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
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.fx.ServerFxQueue;
import pers.roinflam.kuvalich.mercy.MercyState;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 专属玄骸的管理 / 录像命令 {@code /kuvalich lich …}，权限 2，正式版也有
 *
 * <p>服主处理玩家的问题、录演示都用得上：查状态、补生、立刻召来、直接倒地、让它走、改怒气 / 冷却 / 暂停、
 * 指定一句台词（类名写 {@code random} 就随便挑一类）、往处决记录里塞一行、改名、作废这一轮。反馈是管理用的英文细节，套在
 * {@code commands.kuvalich.lich.ok} / {@code .fail} 里。</p>
 *
 * <p>录一段完整演示的顺序：{@code pause on} → {@code birth} → {@code attempt} 塞几行 → 开档案录 →
 * {@code summon front} 录登场 → {@code down} 录处决 → {@code taunt} 录通讯窗。</p>
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

        lich.then(Commands.literal("reset").then(target().executes(NemesisCommand::reset)));

        d.register(Commands.literal(Reference.MOD_ID).requires(s -> s.hasPermission(2)).then(lich));
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
                    .append(", affinity=0x").append(Integer.toHexString(LichProfile.affinities(card.getLichSeed()) & 0xFF))
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
        int levelBefore = card.getUnlockedCardStatus();
        if (levelBefore == 0) {
            // 走正常的进度流程补满第一阶段：解密 HUD、揭示、诞生通讯都照常放
            int threshold = card.getPointsRequired();
            int added = Math.max(1, threshold - card.getDecryptionProgress());
            card.addPotion(added);
            DecryptionHudPacket.sendProgress(p, card, added, levelBefore);
            LichDirector.onDecryptionProgress(p, card, added, levelBefore);
        } else {
            // 已经揭开过密语、却没有玄骸（上一只被作废了）：按「刚揭开第一条」通知导演，它照样诞生、发通讯
            LichDirector.onDecryptionProgress(p, card, 0, 0);
        }
        if (!card.hasLich()) {
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

    private static int ok(CommandContext<CommandSourceStack> ctx, String detail) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.kuvalich.lich.ok", detail), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String detail) {
        ctx.getSource().sendFailure(Component.translatable("commands.kuvalich.lich.fail", detail));
        return 0;
    }
}
