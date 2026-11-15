package pers.roinflam.kuvalich.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.ephemera.EphemeraItem;
import pers.roinflam.kuvalich.ephemera.EphemeraType;
import pers.roinflam.kuvalich.mercy.MercyServer;
import pers.roinflam.kuvalich.mixin.PlayerListSaveInvoker;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.RandomUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 终结的落定：把账（{@link PlayerLedger#getFinale}）上的东西搬进收件箱、清账、存盘。
 * 全模组只有这里能清 {@code finale}。
 *
 * <p>先算后改：会抛的（造物品、读卡）都在清账之前，抛了账原样留着，巡检或登录时再来；
 * 清账那一步只剩写 NBT。落定只动玩家数据和背包，不往世界里生成任何东西，所以盘上只可能是
 * 「有玄骸」「没玄骸、有账」「没账」三种之一，登录补落定不会重复发。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class FinaleSettlement {

    public enum Choice {
        VANQUISH, CONVERT
    }

    private static final int PATROL_PERIOD = 20;
    /** 账上有一条、这个人却没在处决，等这么久（tick）还没人处理就按消灭落定 */
    private static final int ORPHAN_GRACE_TICKS = 100;

    private static final Map<UUID, Integer> ORPHAN_SINCE = new HashMap<>();

    /** GameTest 用：不为 null 时在清账前调一下，在里面抛异常就是「落定算到一半出错」 */
    @Nullable
    public static volatile Runnable faultBeforeCommit;

    private FinaleSettlement() {
    }

    /**
     * 落定之后要告诉玩家的事
     *
     * @param newMinLevel 提过之后的武器等级下限，-1 = 这次没提
     * @param converted   感化进名册的玄骸名字，消灭时为空
     * @param released    名册满了被挤掉的那只，没有为空
     */
    public record Settled(int returned, int newMinLevel, int newMaxLevel, String converted, String released) {

        /** 发聊天、试投一次收件箱；各自出错只记日志 */
        public void announce(ServerPlayer player) {
            ServerPlayer p = current(player);
            if (!p.hasDisconnected()) {
                try {
                    if (returned > 0) {
                        PlayerFeedback.chat(p, KuvaPalette.SUCCESS, "message.kuvalich.confiscation.return", returned);
                    }
                    if (newMinLevel >= 0) {
                        PlayerFeedback.chat(p, KuvaPalette.SUCCESS, "message.kuvalich.minimumLevelWeapon", newMinLevel);
                    }
                    if (newMaxLevel >= 0) {
                        PlayerFeedback.chat(p, KuvaPalette.SUCCESS, "message.kuvalich.maximumLevelWeapon", newMaxLevel);
                    }
                    if (!converted.isEmpty()) {
                        PlayerFeedback.chat(p, KuvaPalette.SUCCESS, "message.kuvalich.convert.done", PlayerFeedback.allyName(converted));
                    }
                    if (!released.isEmpty()) {
                        PlayerFeedback.chat(p, KuvaPalette.ACCENT, "message.kuvalich.convert.released", PlayerFeedback.allyName(released));
                    }
                } catch (RuntimeException e) {
                    LogUtil.error("终结落定后发聊天出错", e);
                }
            }
            try {
                Inbox.deliver(p, true);
            } catch (RuntimeException e) {
                LogUtil.error("终结落定后投递收件箱出错，东西还在收件箱里", e);
            }
        }
    }

    /**
     * 落定这个玩家账上第 {@code generation} 代的那一条。账是空的、代数对不上都不处理（迟到的调用不会落定别的账）。
     * 不发聊天、不投递，调用方在合适的时候调 {@link Settled#announce}。
     *
     * @return 落定了返回要告诉玩家的事；什么都没做返回 null
     */
    @Nullable
    public static Settled resolve(ServerPlayer player, int generation, Choice choice) {
        ServerPlayer p = current(player);
        Finale f = PlayerLedger.getFinale(p);
        if (f == null || f.generation() != generation) {
            return null;
        }

        List<ItemStack> give = new ArrayList<>(f.returns());
        if (!f.ephemera().isEmpty()) {
            EphemeraType type = EphemeraType.byId(f.ephemera());
            if (type != null) {
                give.add(EphemeraItem.create(type, EphemeraType.elementFor(f.seed(), f.weaponType()), f.name()));
            } else {
                LogUtil.error("终结账里的幻纹款式 " + f.ephemera() + " 已不存在，这一件不发：" + p.getGameProfile().getName());
            }
        }
        boolean convert = choice == Choice.CONVERT;
        // 击杀奖励也走收件箱，不掉在地上。要改回掉地上只动这一行（登录补落定时不能补这部分，否则崩服会多一份）
        // 感化不给击杀奖励，只还被没收的东西和幻纹
        if (!convert) {
            give.addAll(f.drops());
        }
        // 所属模组暂时没装、读不出来的条目照收件箱的规矩原样留着，不随清账丢掉；感化时读不出来的掉落同样不给
        ListTag unreadable = PlayerLedger.unreadableFinaleItems(p, !convert);
        if (!unreadable.isEmpty()) {
            LogUtil.warn(p.getGameProfile().getName() + " 的终结账里有 " + unreadable.size() + " 条读不出来的物品，原样放进收件箱");
        }

        RequiemCard card = p.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
        int newMin = -1;
        int newMax = -1;
        if (card != null && !convert) {
            int inc = RandomUtil.getInt(ModConfig.KUVA_LICH.minimumLevelCapIncrease.get(),
                    ModConfig.KUVA_LICH.maximumLevelCapIncrease.get());
            int capMin = ModConfig.KUVA_LICH.minimumLevel.get();
            int capMax = ModConfig.KUVA_LICH.maximumLevel.get();
            if (card.getMinimumLevelWeapon() < capMin) {
                newMin = Math.min(capMin, card.getMinimumLevelWeapon() + inc);
            }
            if (card.getMaximumLevelWeapon() < capMax) {
                newMax = Math.min(capMax, card.getMaximumLevelWeapon() + inc);
            }
        } else if (card == null) {
            LogUtil.error("落定终结时 " + p.getGameProfile().getName() + " 没有安魂能力，武器等级上下限这次不提");
        }
        CompoundTag ally = convert ? AllyRoster.entryOf(f, p.server.overworld().getGameTime()) : null;

        if (faultBeforeCommit != null) {
            faultBeforeCommit.run();
        }
        // 清账：收件箱、清掉账、上下限一起写，重试碰到的已经是空账，不会提两次
        PlayerLedger.settleFinale(p, give, unreadable);
        if (newMin >= 0) {
            card.setMinimumLevelWeapon(newMin);
        }
        if (newMax >= 0) {
            card.setMaximumLevelWeapon(newMax);
        }
        ORPHAN_SINCE.remove(p.getUUID());
        String released = "";
        if (ally != null) {
            // 账已经清了，这两样出错只记日志：东西已经在收件箱里，不能再回头按消灭重来
            try {
                if (card != null && !card.markLastConverted(generation)) {
                    LogUtil.warn(p.getGameProfile().getName() + " 的历史首条不是第 " + generation + " 代，结局没改成感化");
                }
            } catch (RuntimeException e) {
                LogUtil.error("感化后改历史结局出错", e);
            }
            try {
                released = AllyRoster.add(p, ally);
            } catch (RuntimeException e) {
                LogUtil.error("感化后写盟友名册出错：" + p.getGameProfile().getName() + " 第 " + generation + " 代", e);
            }
        }
        LogUtil.debug("终结落定：" + p.getGameProfile().getName() + " 第 " + generation + " 代 " + f.name() + "，" + choice
                + "，进收件箱 " + give.size() + " 堆");

        saveNow(p);
        return new Settled(f.returns().size(), newMin, newMax, convert ? f.name() : "", released);
    }

    /** 落定并当场发聊天、试投。落定本身抛出的异常照样抛给调用方 */
    public static boolean resolveNow(ServerPlayer player, int generation, Choice choice) {
        Settled s = resolve(player, generation, choice);
        if (s == null) {
            return false;
        }
        s.announce(player);
        return true;
    }

    /** 账上有就落定（登录补落定、管理命令用）：账上记了感化按感化，否则按消灭。没有返回 false */
    public static boolean resolvePending(ServerPlayer player) {
        ServerPlayer p = current(player);
        Finale f = PlayerLedger.getFinale(p);
        return f != null && resolveNow(player, f.generation(), pendingChoice(p));
    }

    public static Choice pendingChoice(ServerPlayer player) {
        return PlayerLedger.finaleMarkedConvert(current(player)) ? Choice.CONVERT : Choice.VANQUISH;
    }

    /** 重生后旧玩家对象作废，账要记在玩家列表里现在那个对象上 */
    private static ServerPlayer current(ServerPlayer player) {
        ServerPlayer live = player.server.getPlayerList().getPlayer(player.getUUID());
        return live != null ? live : player;
    }

    public static void saveNow(ServerPlayer player) {
        try {
            ((PlayerListSaveInvoker) player.server.getPlayerList()).kuvalich$save(player);
        } catch (RuntimeException | LinkageError e) {
            LogUtil.error("单独保存 " + player.getGameProfile().getName() + " 的数据失败，改为保存全部玩家", e);
            try {
                player.server.getPlayerList().saveAll();
            } catch (RuntimeException e2) {
                LogUtil.error("保存全部玩家数据也失败了", e2);
            }
        }
    }

    // ==================== 巡检 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        if (now % PATROL_PERIOD != 0) {
            return;
        }
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            try {
                patrol(p, now);
            } catch (RuntimeException e) {
                LogUtil.error("终结账 / 收件箱巡检 " + p.getGameProfile().getName() + " 时出错", e);
            }
        }
    }

    /** 一个玩家的那一份：没人处理的账按消灭落定，收件箱试投一次。测试可以直接调 */
    public static void patrol(ServerPlayer p, int now) {
        UUID id = p.getUUID();
        Finale f = PlayerLedger.getFinale(p);
        if (f == null || MercyServer.hasActiveExec(id)) {
            ORPHAN_SINCE.remove(id);
        } else {
            int since = ORPHAN_SINCE.computeIfAbsent(id, k -> now);
            if (now - since >= ORPHAN_GRACE_TICKS) {
                ORPHAN_SINCE.remove(id);
                Choice c = pendingChoice(p);
                LogUtil.warn(p.getGameProfile().getName() + " 的终结账没人处理，按" + (c == Choice.CONVERT ? "感化" : "消灭") + "落定");
                resolveNow(p, f.generation(), c);
            }
        }
        Inbox.patrol(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            try {
                Inbox.deliver(p, true);
            } catch (RuntimeException e) {
                LogUtil.error("重生后投递收件箱出错", e);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ORPHAN_SINCE.remove(p.getUUID());
            Inbox.onLogout(p);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ORPHAN_SINCE.clear();
        Inbox.clearAll();
    }
}
