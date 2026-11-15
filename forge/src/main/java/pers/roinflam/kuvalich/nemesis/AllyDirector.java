package pers.roinflam.kuvalich.nemesis;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.fx.FxId;
import pers.roinflam.kuvalich.fx.KuvaFx;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 感化过的玄骸在主人危急时作为盟友现身（感化稿 §3.3）。
 *
 * <p>触发不放在伤害事件里判：同为最低优先级的抗死附魔、减伤谁先谁后不定，事件里看不准「挨完还活着、真的跌破」。
 * 事件里只记一笔「这一 tick 挨了非玩家活物的打」，tick 末尾再看实际血量。冷却截止写进玩家数据，重登压不掉；
 * 在场、上膛这些临时状态只放内存。在场靠实体引用核：被 /kill、虚空、区块卸载、别的模组 discard 移走时
 * 实体回调 {@link #onAllyRemoved}，每秒巡检再把已移除的清一遍。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class AllyDirector {

    private static final int PERIOD = 20;
    /** 主人和盟友离开这么远（格）盟友就退去；身边这么远以内已有 2 只别人的盟友就不再来 */
    public static final double RANGE = 48.0D;
    private static final int CROWD_LIMIT = 2;
    public static final int LOGIN_DELAY_TICKS = 60 * 20;
    /** 跌破一次只掷一次：血量回到阈值以上、或过了这么久才重新上膛 */
    private static final int REARM_TICKS = 30 * 20;
    public static final int IDLE_LEAVE_TICKS = 15 * 20;
    public static final int STUCK_LEAVE_SECONDS = 10;
    /** 打死敌人后说一句的概率；台词本身是低优先级，刚有过通讯就被限流丢掉 */
    private static final float KILL_LINE_CHANCE = 0.3F;

    static final String NOTICE_ARRIVE = "message.kuvalich.ally.arrive";
    static final String NOTICE_WITHDRAW = "message.kuvalich.ally.withdraw";
    static final String NOTICE_DOWNED = "message.kuvalich.ally.downed";

    private static final Map<UUID, Present> PRESENT = new HashMap<>();
    private static final Map<UUID, Trigger> TRIGGERS = new HashMap<>();
    private static final Set<UUID> HIT_THIS_TICK = new HashSet<>();
    private static final Map<UUID, Long> LAST_COMBAT = new HashMap<>();
    private static final Map<UUID, Long> LOGIN_AT = new HashMap<>();

    private static final class Present {
        final KuvaMasterEntity entity;
        final int generation;
        final long since;
        Vec3 lastPos;
        int stuckSeconds;

        Present(KuvaMasterEntity entity, int generation, long since) {
            this.entity = entity;
            this.generation = generation;
            this.since = since;
            this.lastPos = entity.position();
        }

        boolean gone() {
            return entity.isRemoved() || !entity.isAlive();
        }
    }

    private static final class Trigger {
        boolean armed = true;
        long disarmedAt;
        long retryUntil;
    }

    private AllyDirector() {
    }

    // ==================== 查询 ====================

    /** 这个玩家的盟友此刻在场的实体；不在场返回 null */
    @Nullable
    public static KuvaMasterEntity presentOf(UUID owner) {
        Present pr = PRESENT.get(owner);
        if (pr == null) {
            return null;
        }
        if (pr.gone()) {
            PRESENT.remove(owner);
            return null;
        }
        return pr.entity;
    }

    /** 全服在场的盟友数；正在化雾退去的（最多再留 8 tick）不算 */
    public static int activeCount() {
        PRESENT.values().removeIf(Present::gone);
        int n = 0;
        for (Present pr : PRESENT.values()) {
            if (!pr.entity.isDeparting()) {
                n++;
            }
        }
        return n;
    }

    /** 下一次现身用名册里的哪一条：指定了出战的就是它，否则最新感化的那只 */
    @Nullable
    public static CompoundTag entryToSummon(Player player) {
        List<CompoundTag> allies = PlayerLedger.getAllies(player);
        if (allies.isEmpty()) {
            return null;
        }
        int active = PlayerLedger.getActiveAlly(player);
        for (CompoundTag a : allies) {
            if (a.getInt(AllyRoster.GENERATION) == active) {
                return a;
            }
        }
        return allies.get(allies.size() - 1);
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static boolean contains(Player player, int generation) {
        for (CompoundTag a : PlayerLedger.getAllies(player)) {
            if (a.getInt(AllyRoster.GENERATION) == generation) {
                return true;
            }
        }
        return false;
    }

    // ==================== 现身 ====================

    /**
     * 不能来的原因，空串 = 能来。{@code forced}（管理命令）不看冷却、登录宽限
     */
    public static String blocker(ServerPlayer p, long now, boolean forced) {
        if (!ModConfig.KUVA_LICH.allyEnabled.get()) {
            return "disabled";
        }
        if (PlayerLedger.getAllies(p).isEmpty()) {
            return "roster";
        }
        if (p.level().getDifficulty() == Difficulty.PEACEFUL) {
            return "peaceful";
        }
        if (p.isCreative() || p.isSpectator()) {
            return "creative";
        }
        if (!p.isAlive() || MercyServer.isBusy(p)) {
            return "busy";
        }
        if (!forced && PlayerLedger.getAllyCooldownUntil(p) > now) {
            return "cooldown";
        }
        String dim = p.level().dimension().location().toString();
        if (!ModConfig.KUVA_LICH.lichHuntDimensions.get().contains(dim)) {
            return "dimension";
        }
        if (presentOf(p.getUUID()) != null) {
            return "present";
        }
        int cap = ModConfig.KUVA_LICH.allyMaxActive.get();
        if (cap > 0 && activeCount() >= cap) {
            return "server_cap";
        }
        int near = 0;
        for (Present pr : PRESENT.values()) {
            if (!pr.gone() && !pr.entity.isDeparting() && pr.entity.level() == p.level()
                    && pr.entity.distanceToSqr(p) <= RANGE * RANGE) {
                near++;
            }
        }
        if (near >= CROWD_LIMIT) {
            return "crowded";
        }
        Long login = LOGIN_AT.get(p.getUUID());
        if (!forced && login != null && now - login < LOGIN_DELAY_TICKS) {
            return "login";
        }
        return "";
    }

    /**
     * 让盟友现身。成功返回空串，否则是原因（找不到落点、加不进世界也算没来）
     *
     * @param setCooldown 写冷却截止；管理命令召来的不写
     */
    public static String summon(ServerPlayer p, boolean forced, boolean setCooldown) {
        long now = now(p.server);
        String why = blocker(p, now, forced);
        if (!why.isEmpty()) {
            return why;
        }
        CompoundTag entry = entryToSummon(p);
        if (entry == null) {
            return "roster";
        }
        ServerLevel level = p.serverLevel();
        Vec3 at = LichSpawner.findFront(p, null, 12);
        if (at == null) {
            at = LichSpawner.findHidden(p, 3, 10, 16, true);
        }
        if (at == null) {
            return "no_spot";
        }
        KuvaMasterEntity ally = KuvaLichEntities.KUVA_MASTER.get().create(level);
        if (ally == null) {
            return "create";
        }
        float yaw = p.getYRot();
        ally.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        ally.setYHeadRot(yaw);
        ally.setYBodyRot(yaw);
        ally.setupAlly(p, entry, ModConfig.KUVA_LICH.allyHealthScale.get(), ModConfig.KUVA_LICH.allyDamageScale.get());
        ally.beginAppearGrace(KuvaMasterEntity.APPEAR_GRACE_TICKS);
        if (!level.addFreshEntity(ally)) {
            return "rejected";
        }
        PRESENT.put(p.getUUID(), new Present(ally, ally.getAllyGeneration(), now));
        LAST_COMBAT.put(p.getUUID(), now);
        if (setCooldown) {
            PlayerLedger.setAllyCooldownUntil(p, now + ModConfig.KUVA_LICH.allyCooldownMinutes.get() * 1200L);
        }
        KuvaFx.near(level, at, 64.0D, FxId.LICH_ARRIVE, ally, 0, 0);
        PlayerFeedback.chat(p, KuvaPalette.SUCCESS, NOTICE_ARRIVE, ally.getLichName());
        LichComms.say(p, LichLine.ALLY_ARRIVE, ally);
        LogUtil.debug("盟友现身：" + p.getGameProfile().getName() + " 的 " + ally.getLichName() + " @ " + at);
        return "";
    }

    /** 跌破阈值后掷一次。没被挡住才算掷过（会消耗这次上膛） */
    public static void roll(ServerPlayer p, long now) {
        Trigger t = TRIGGERS.computeIfAbsent(p.getUUID(), k -> new Trigger());
        if (!t.armed || now < t.retryUntil) {
            return;
        }
        if (!blocker(p, now, false).isEmpty()) {
            return;
        }
        t.armed = false;
        t.disarmedAt = now;
        boolean hit = p.getRandom().nextFloat() < ModConfig.KUVA_LICH.allyAssistChance.get();
        if (!hit || !summon(p, false, true).isEmpty()) {
            t.retryUntil = now + ModConfig.KUVA_LICH.allyRetrySeconds.get() * 20L;
        }
    }

    // ==================== 离场 ====================

    /** 化雾退去，不死、不掉东西。{@code line} 为 null 不说话；退去和被击倒的台词带一句给主人的提示 */
    public static void depart(KuvaMasterEntity ally, @Nullable LichLine line) {
        depart(ally, line, line == LichLine.ALLY_DOWNED ? NOTICE_DOWNED
                : line == LichLine.ALLY_LEAVE ? NOTICE_WITHDRAW : null);
    }

    private static void depart(KuvaMasterEntity ally, @Nullable LichLine line, @Nullable String notice) {
        if (ally.isRemoved() || !ally.isAlive() || ally.isDeparting()) {
            return;
        }
        ServerPlayer owner = ally.getAllyOwnerPlayer();
        if (owner != null && !owner.hasDisconnected()) {
            if (notice != null) {
                PlayerFeedback.chat(owner, NOTICE_DOWNED.equals(notice) ? KuvaPalette.WARN : KuvaPalette.INFO, notice,
                        ally.getLichName());
            }
            if (line != null) {
                LichComms.say(owner, line, ally);
            }
        }
        ally.departNow(KuvaMasterEntity.NATURAL_LEAVE_FX);
    }

    /** 血量到 0 的那一下（实体那边已经把血量留在 1） */
    public static void onAllyKnockedOut(KuvaMasterEntity ally) {
        depart(ally, LichLine.ALLY_DOWNED);
    }

    public static void onAllyRemoved(KuvaMasterEntity ally) {
        UUID owner = ally.getAllyOwnerUUID();
        if (owner != null) {
            Present pr = PRESENT.get(owner);
            if (pr != null && pr.entity == ally) {
                PRESENT.remove(owner);
            }
        }
    }

    /** 名册里删掉了这一代（遣散、被挤出）：正在场上就马上退去 */
    static void onReleased(UUID owner, int generation) {
        Present pr = PRESENT.get(owner);
        if (pr != null && pr.generation == generation) {
            depart(pr.entity, null);
        }
    }

    private static void departOf(UUID owner, @Nullable LichLine line) {
        KuvaMasterEntity ally = presentOf(owner);
        if (ally != null) {
            depart(ally, line);
        }
    }

    // ==================== 巡检 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = now(server);
        if (!HIT_THIS_TICK.isEmpty()) {
            double threshold = ModConfig.KUVA_LICH.allyTriggerHealthRatio.get();
            for (UUID id : new ArrayList<>(HIT_THIS_TICK)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || !p.isAlive() || p.getHealth() >= p.getMaxHealth() * threshold) {
                    continue;
                }
                try {
                    roll(p, now);
                } catch (RuntimeException e) {
                    LogUtil.error("盟友助战判定出错：" + p.getGameProfile().getName(), e);
                }
            }
            HIT_THIS_TICK.clear();
        }
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        try {
            patrol(server, now);
        } catch (RuntimeException e) {
            LogUtil.error("盟友巡检出错", e);
        }
    }

    public static void patrol(MinecraftServer server, long now) {
        double threshold = ModConfig.KUVA_LICH.allyTriggerHealthRatio.get();
        for (Map.Entry<UUID, Trigger> e : TRIGGERS.entrySet()) {
            Trigger t = e.getValue();
            if (t.armed) {
                continue;
            }
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (now - t.disarmedAt >= REARM_TICKS
                    || p != null && p.isAlive() && p.getHealth() >= p.getMaxHealth() * threshold) {
                t.armed = true;
            }
        }
        long maxStay = ModConfig.KUVA_LICH.allyMaxStaySeconds.get() * 20L;
        for (Map.Entry<UUID, Present> e : new ArrayList<>(PRESENT.entrySet())) {
            Present pr = e.getValue();
            if (pr.gone()) {
                PRESENT.remove(e.getKey());
                continue;
            }
            KuvaMasterEntity ally = pr.entity;
            if (ally.isDeparting()) {
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(e.getKey());
            if (owner == null || !owner.isAlive() || owner.level() != ally.level()
                    || owner.distanceToSqr(ally) > RANGE * RANGE || !contains(owner, pr.generation)) {
                depart(ally, null);
                continue;
            }
            if (now - pr.since >= maxStay) {
                depart(ally, LichLine.ALLY_LEAVE);
                continue;
            }
            boolean standby = MercyServer.isBusy(owner);
            long lastCombat = LAST_COMBAT.getOrDefault(e.getKey(), pr.since);
            if (!standby && ally.getTarget() == null && now - lastCombat >= IDLE_LEAVE_TICKS) {
                depart(ally, LichLine.ALLY_LEAVE);
                continue;
            }
            Vec3 pos = ally.position();
            boolean shouldMove = ally.getTarget() != null || owner.distanceToSqr(ally) > 36.0D;
            if (!standby && !ally.isAppearing() && shouldMove && pos.distanceToSqr(pr.lastPos) < 0.25D) {
                if (++pr.stuckSeconds >= STUCK_LEAVE_SECONDS) {
                    depart(ally, null, NOTICE_WITHDRAW);
                    continue;
                }
            } else {
                pr.stuckSeconds = 0;
            }
            pr.lastPos = pos;
        }
    }

    // ==================== 事件 ====================

    /** 只记一笔，判定放到 tick 末尾。主人自己出手也记成「交手中」 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (victim instanceof ServerPlayer p && attacker instanceof LivingEntity && !(attacker instanceof Player)
                && !(attacker instanceof KuvaMasterEntity lich && lich.isAlly())) {
            HIT_THIS_TICK.add(p.getUUID());
            LAST_COMBAT.put(p.getUUID(), now(p.server));
        }
        if (attacker instanceof ServerPlayer p && victim != p) {
            LAST_COMBAT.put(p.getUUID(), now(p.server));
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (victim instanceof ServerPlayer p) {
            departOf(p.getUUID(), null);
            return;
        }
        if (event.getSource().getEntity() instanceof KuvaMasterEntity ally && ally.isAlly() && ally != victim
                && victim.getRandom().nextFloat() < KILL_LINE_CHANCE) {
            ServerPlayer owner = ally.getAllyOwnerPlayer();
            if (owner != null) {
                LichComms.say(owner, LichLine.ALLY_KILL, ally);
            }
        }
    }

    /**
     * 盟友是 Monster，铁傀儡、守卫类模组、狼会按「敌对」去打它。不是敌对生物的、有主人的一律不许选它当目标；
     * 敌对生物照常可以回头打它
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof KuvaMasterEntity ally) || !ally.isAlly()) {
            return;
        }
        LivingEntity who = event.getEntity();
        if (!(who instanceof Enemy) || who instanceof OwnableEntity pet && pet.getOwnerUUID() != null
                || who instanceof KuvaMasterEntity other && other.isAlly()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            LOGIN_AT.put(p.getUUID(), now(p.server));
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        departOf(id, null);
        TRIGGERS.remove(id);
        LAST_COMBAT.remove(id);
        LOGIN_AT.remove(id);
        HIT_THIS_TICK.remove(id);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        departOf(event.getEntity().getUUID(), null);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PRESENT.clear();
        TRIGGERS.clear();
        HIT_THIS_TICK.clear();
        LAST_COMBAT.clear();
        LOGIN_AT.clear();
    }

    // ==================== 管理命令 /kuvalich ally ... ====================

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> ally = Commands.literal("ally").requires(s -> s.hasPermission(2));
        ally.then(Commands.literal("summon").then(Commands.argument("player", EntityArgument.player())
                .executes(AllyDirector::cmdSummon)));
        ally.then(Commands.literal("list").then(Commands.argument("player", EntityArgument.player())
                .executes(AllyDirector::cmdList)));
        ally.then(Commands.literal("leave").then(Commands.argument("player", EntityArgument.player())
                .executes(AllyDirector::cmdLeave)));
        ally.then(Commands.literal("clear").then(Commands.argument("player", EntityArgument.player())
                .executes(AllyDirector::cmdClear)));
        ally.then(Commands.literal("cooldown").then(Commands.literal("clear")
                .then(Commands.argument("player", EntityArgument.player()).executes(AllyDirector::cmdCooldownClear))));
        d.register(Commands.literal(Reference.MOD_ID).then(ally));
    }

    private static int cmdSummon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        String why = summon(p, true, false);
        if (!why.isEmpty()) {
            return fail(ctx, "ally did not appear for " + p.getGameProfile().getName() + ": " + why);
        }
        KuvaMasterEntity e = presentOf(p.getUUID());
        return ok(ctx, (e != null ? e.getLichName() : "ally") + " appeared for " + p.getGameProfile().getName());
    }

    private static int cmdList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        List<CompoundTag> allies = PlayerLedger.getAllies(p);
        int active = PlayerLedger.getActiveAlly(p);
        KuvaMasterEntity present = presentOf(p.getUUID());
        StringBuilder sb = new StringBuilder(p.getGameProfile().getName()).append(" roster ")
                .append(allies.size()).append('/').append(AllyRoster.capacity());
        for (CompoundTag a : allies) {
            int gen = a.getInt(AllyRoster.GENERATION);
            sb.append("\n  #").append(gen).append(' ').append(a.getString(AllyRoster.NAME))
                    .append(" rank ").append(a.getInt(AllyRoster.RANK));
            if (gen == active) {
                sb.append(" [active]");
            }
            if (present != null && present.getAllyGeneration() == gen) {
                sb.append(" [present]");
            }
        }
        long left = PlayerLedger.getAllyCooldownUntil(p) - now(p.server);
        sb.append("\n  cooldown ").append(Math.max(0L, left) / 20L).append(" s, server present ").append(activeCount());
        return ok(ctx, sb.toString());
    }

    private static int cmdLeave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        KuvaMasterEntity e = presentOf(p.getUUID());
        if (e == null) {
            return fail(ctx, p.getGameProfile().getName() + " has no ally present");
        }
        depart(e, null);
        return ok(ctx, e.getLichName() + " withdrew");
    }

    private static int cmdClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        int n = 0;
        for (CompoundTag a : PlayerLedger.getAllies(p)) {
            if (AllyRoster.release(p, a.getInt(AllyRoster.GENERATION))) {
                n++;
            }
        }
        return ok(ctx, "released " + n + " allies of " + p.getGameProfile().getName());
    }

    private static int cmdCooldownClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
        PlayerLedger.setAllyCooldownUntil(p, 0L);
        TRIGGERS.remove(p.getUUID());
        return ok(ctx, "ally cooldown cleared for " + p.getGameProfile().getName());
    }

    private static int ok(CommandContext<CommandSourceStack> ctx, String detail) {
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.kuvalich.lich.ok", detail), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String detail) {
        ctx.getSource().sendFailure(Component.translatable("commands.kuvalich.lich.fail", detail));
        return 0;
    }

    // ==================== 测试用 ====================

    /** 清掉某人的上膛 / 重试状态（测试里连着测几次触发用） */
    public static void resetTrigger(UUID player) {
        TRIGGERS.remove(player);
    }

    /** 模拟刚登录（测试登录宽限用） */
    public static void markLogin(ServerPlayer p) {
        LOGIN_AT.put(p.getUUID(), now(p.server));
    }

    /** 测试造的玩家走过一遍登录，跳过登录后 60 秒不来的那一道 */
    public static void skipLoginDelay(ServerPlayer p) {
        LOGIN_AT.remove(p.getUUID());
    }

    public static boolean isArmed(UUID player) {
        Trigger t = TRIGGERS.get(player);
        return t == null || t.armed;
    }
}
