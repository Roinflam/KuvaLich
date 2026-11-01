package pers.roinflam.kuvalich.nemesis;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.comm.CommSpeaker;
import pers.roinflam.kuvalich.comm.KuvaComm;
import pers.roinflam.kuvalich.comm.LichLook;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.entity.KuvaMasterEntity;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玄骸和 Lotus 的台词层：挑哪一句、限流、要不要同时进聊天栏，最后交给 {@link KuvaComm} 发出去
 *
 * <ul>
 *   <li><b>挑句子</b>：同一类里随机一条，同一个玩家连着两次不抽到同一条。处决判错那一类有两句是同一句话的两个版本，
 *       「刀落偏了」那句只在怜悯之击开着时进池子，另一句只在关着时进（关着时没有落刀的场面）。</li>
 *   <li><b>限流</b>（每人，按主世界 gameTime 算）：同一个发言人两句之间至少隔 400 ms；Lotus 和玄骸分开算，
 *       这样诞生时 Lotus 那句和玄骸那句同一刻发，客户端排队、一前一后地放。低优先级的（随机嘲讽、没收）
 *       在 8 秒内有过任何通讯就丢掉；没收类每 30 秒至多一句，其余没收只有动作栏。</li>
 *   <li><b>聊天回显</b>：服务端配置 {@code lichCommChatEcho}，「重要」只给诞生、判错、遗言、被它打死、
 *       你死了这几类带上「同时进聊天栏」的标志，「全部」每句都带。</li>
 *   <li><b>名字和外观</b>：台词里 {@code %1$s} 是玄骸的名字（首字母大写）、{@code %2$s} 是主人的名字、
 *       {@code %3$s} 是它手里那把赤毒武器（诞生时 Lotus 那句靠它告诉玩家打倒它能拿到什么），三个都传，
 *       用不到的客户端会丢掉。给了说话的实体就用它的名字和外观（遗言时卡上的名字已经清掉了，
 *       演示用的玄骸也没有主人），没给就用收话人自己那只：在场的实体，或者卡上记的。</li>
 * </ul>
 *
 * <p>通用代码，专用服也加载，不引用任何客户端类。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LichComms {

    /** 同一个发言人两句之间的最短间隔（tick），400 ms */
    public static final int GAP_TICKS = 8;
    /** 低优先级的：这么久之内有过任何通讯就丢掉（tick），8 s */
    public static final int LOW_QUIET_TICKS = 160;
    /** 没收类：两句之间至少隔多久（tick），30 s */
    public static final int CONFISCATE_GAP_TICKS = 600;
    /** 台词参数的长度上限 */
    private static final int MAX_ARG_LENGTH = 32;
    /** 处决判错那一类里，只在怜悯之击开着 / 关着时用的两句（下标） */
    private static final int FAIL_MERCY_ONLY = 4;
    private static final int FAIL_NO_MERCY_ONLY = 5;

    /** 每个玩家的通讯记录，只在服务端主线程读写；下线删、关服清空 */
    private static final Map<UUID, Memory> MEMORY = new HashMap<>();

    private static final class Memory {
        long lastLich = Long.MIN_VALUE / 4;
        long lastLotus = Long.MIN_VALUE / 4;
        long lastAny = Long.MIN_VALUE / 4;
        long lastConfiscate = Long.MIN_VALUE / 4;
        final Map<LichLine, Integer> lastIndex = new EnumMap<>(LichLine.class);
    }

    /** 玄骸说话时用的名字、外观和台词里的两个参数 */
    private record Speaker(String lichName, String ownerName, LichLook look) {
    }

    private LichComms() {
    }

    /** 通讯窗传输层里「玄骸叫什么、长什么样」由这里回答（别处直接调 {@link KuvaComm#sayLich} 时也走它） */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        KuvaComm.setLichResolver(LichComms::resolveForTransport);
    }

    // ==================== 对外 ====================

    /**
     * 对主人说一句；说话的是他自己那只（在场的实体，或者卡上记的）。
     *
     * @return 真的发出去了返回 true；被限流丢掉、没有玄骸、这一类没有能用的句子返回 false
     */
    public static boolean say(ServerPlayer owner, LichLine line) {
        return say(owner, line, null);
    }

    /**
     * 对某个玩家说一句，说话的是指定的那只玄骸：给了实体就用它的名字和外观（遗言、演示用的玄骸），
     * 没给就和 {@link #say(ServerPlayer, LichLine)} 一样。
     *
     * @return 真的发出去了返回 true
     */
    public static boolean say(ServerPlayer to, LichLine line, @Nullable KuvaMasterEntity speaker) {
        return send(to, line, -1, speaker, false);
    }

    /**
     * 发指定的一句，不受限流（管理命令、录像用）。{@code index} 从 0 起，在当前开关下不该出现的照样发；
     * {@code index} 为 -1 时照常随机挑一句（同样不和上一句重复）。
     *
     * @return 发出去了返回 true；没有玄骸、下标越界返回 false
     */
    public static boolean sayExact(ServerPlayer to, LichLine line, int index, @Nullable KuvaMasterEntity speaker) {
        if (index < -1 || index >= line.count()) {
            return false;
        }
        return send(to, line, index, speaker, true);
    }

    /**
     * 这一类台词发出去时带的标志位：到场、通牒召来带青色故障显形；随机嘲讽标成「随口一句」
     * （客户端窗忙就直接丢）；按聊天回显配置决定带不带「同时进聊天栏」
     */
    public static int flagsFor(LichLine line) {
        int flags = 0;
        if (line == LichLine.ARRIVE || line == LichLine.ULTIMATUM) {
            flags |= KuvaComm.ARRIVAL;
        }
        if (line == LichLine.IDLE || line == LichLine.IDLE_FURIOUS) {
            flags |= KuvaComm.AMBIENT;
        }
        if (echoToChat(line)) {
            flags |= KuvaComm.CHAT_COPY;
        }
        return flags;
    }

    /** 这一类在当前配置下能抽的句子下标 */
    public static int[] candidates(LichLine line) {
        if (line != LichLine.FAIL || line.count() <= FAIL_NO_MERCY_ONLY) {
            int[] all = new int[line.count()];
            for (int i = 0; i < all.length; i++) {
                all[i] = i;
            }
            return all;
        }
        boolean mercy = ModConfig.KUVA_LICH.mercyEnabled.get();
        int skip = mercy ? FAIL_NO_MERCY_ONLY : FAIL_MERCY_ONLY;
        int[] out = new int[line.count() - 1];
        int k = 0;
        for (int i = 0; i < line.count(); i++) {
            if (i != skip) {
                out[k++] = i;
            }
        }
        return out;
    }

    /** 这个玩家上一次在这一类里抽到的下标，没有为 -1（管理命令、测试看） */
    public static int lastIndex(ServerPlayer player, LichLine line) {
        Memory m = MEMORY.get(player.getUUID());
        Integer i = m == null ? null : m.lastIndex.get(line);
        return i == null ? -1 : i;
    }

    // ==================== 发送 ====================

    private static boolean send(ServerPlayer to, LichLine line, int forcedIndex,
                                @Nullable KuvaMasterEntity speakerEntity, boolean bypassLimits) {
        if (to == null || line == LichLine.ARCHIVE || to.hasDisconnected()) {
            return false;
        }
        Speaker speaker = resolve(to, speakerEntity);
        if (speaker == null) {
            return false;
        }
        long now = to.server.overworld().getGameTime();
        Memory m = MEMORY.computeIfAbsent(to.getUUID(), id -> new Memory());
        if (!bypassLimits) {
            String drop = throttled(m, line, now);
            if (drop != null) {
                debug(to, line, "丢掉：" + drop);
                return false;
            }
        }
        int index = forcedIndex >= 0 ? forcedIndex : pick(to, m, line);
        if (index < 0) {
            return false;
        }

        Component text = Component.translatable(line.key(index), cut(speaker.lichName()), cut(speaker.ownerName()),
                weaponName(speaker.look()));
        int flags = flagsFor(line);
        if (line.lotus) {
            KuvaComm.say(to, CommSpeaker.LOTUS, null, text, flags, null, 0);
            m.lastLotus = now;
        } else {
            KuvaComm.say(to, CommSpeaker.LICH, Component.literal(speaker.lichName()), text, flags, speaker.look(), 0);
            m.lastLich = now;
        }
        m.lastAny = now;
        if (line == LichLine.CONFISCATE) {
            m.lastConfiscate = now;
        }
        m.lastIndex.put(line, index);
        debug(to, line, "发出 " + line.key(index) + "，标志 " + flags);
        return true;
    }

    /** 被限流时返回原因，放行返回 null */
    @Nullable
    private static String throttled(Memory m, LichLine line, long now) {
        long last = line.lotus ? m.lastLotus : m.lastLich;
        if (now - last < GAP_TICKS) {
            return "离上一句不到 400 ms";
        }
        if (line.priority == LichLine.Priority.LOW && now - m.lastAny < LOW_QUIET_TICKS) {
            return "低优先级，8 秒内有过通讯";
        }
        if (line == LichLine.CONFISCATE && now - m.lastConfiscate < CONFISCATE_GAP_TICKS) {
            return "没收类 30 秒内说过";
        }
        return null;
    }

    /** 随机挑一句，不和这个人上一次这一类的那句重复（池子里只有一句时除外） */
    private static int pick(ServerPlayer to, Memory m, LichLine line) {
        int[] pool = candidates(line);
        if (pool.length == 0) {
            return -1;
        }
        Integer last = m.lastIndex.get(line);
        if (pool.length == 1 || last == null) {
            return pool[to.getRandom().nextInt(pool.length)];
        }
        int lastPos = -1;
        for (int i = 0; i < pool.length; i++) {
            if (pool[i] == last) {
                lastPos = i;
                break;
            }
        }
        if (lastPos < 0) {
            return pool[to.getRandom().nextInt(pool.length)];
        }
        // 在除上一句之外的 n - 1 句里均匀抽
        int r = to.getRandom().nextInt(pool.length - 1);
        return pool[r >= lastPos ? r + 1 : r];
    }

    private static boolean echoToChat(LichLine line) {
        ModConfig.KuvaLichConfig.LichCommEcho mode;
        try {
            mode = ModConfig.KUVA_LICH.lichCommChatEcho.get();
        } catch (RuntimeException e) {
            mode = ModConfig.KuvaLichConfig.LichCommEcho.IMPORTANT;
        }
        return switch (mode) {
            case NONE -> false;
            case IMPORTANT -> line.chatEchoImportant;
            case ALL -> true;
        };
    }

    // ==================== 玄骸的名字和外观 ====================

    /**
     * 说话的是谁：给了有名字的实体就是它；否则是收话人自己那只（在场的实体优先，它的实体 id 让世界里那只也张嘴，
     * 不在场就用卡上记的）。都没有返回 null
     */
    @Nullable
    private static Speaker resolve(ServerPlayer to, @Nullable LivingEntity given) {
        if (given instanceof KuvaMasterEntity lich && !lich.getLichName().isEmpty()) {
            String owner = lich.getOwnerName();
            return new Speaker(lich.getLichName(), owner.isEmpty() ? to.getGameProfile().getName() : owner,
                    new LichLook(lich.getLichSeed(), lich.getLichWeaponIndex(), (byte) -1, lich.getId()));
        }
        RequiemCard card = to.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).orElse(null);
        if (card == null || !card.hasLich()) {
            return null;
        }
        KuvaMasterEntity present = LichDirector.getLich(to).orElse(null);
        int entityId = present != null ? present.getId() : given != null ? given.getId() : -1;
        return new Speaker(card.getLichName(), to.getGameProfile().getName(),
                new LichLook((int) card.getLichSeed(), card.getLichWeapon(), (byte) -1, entityId));
    }

    @Nullable
    private static KuvaComm.LichNameAndLook resolveForTransport(ServerPlayer to, @Nullable LivingEntity lich) {
        Speaker s = resolve(to, lich);
        return s == null ? null : new KuvaComm.LichNameAndLook(Component.literal(s.lichName()), s.look());
    }

    /** 台词里的 {@code %3$s}：这只玄骸拿的赤毒武器，传物品名的翻译键，客户端按自己的语言显示；下标无效时空着 */
    private static Component weaponName(LichLook look) {
        int i = look.weapon();
        return i >= 0 && i < KuvaLichItems.KUVA_WEAPONS.size() ? KuvaLichItems.KUVA_WEAPONS.get(i).getDescription()
                : Component.empty();
    }

    private static String cut(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > MAX_ARG_LENGTH ? s.substring(0, MAX_ARG_LENGTH) : s;
    }

    private static void debug(ServerPlayer to, LichLine line, String message) {
        if (LogUtil.isDebugEnabled()) {
            LogUtil.debug("[玄骸通讯] " + to.getGameProfile().getName() + " " + line + "：" + message);
        }
    }

    // ==================== 记录的清理 ====================

    /** 下线删记录、关服清空（FORGE 总线） */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID)
    public static final class Cleanup {

        private Cleanup() {
        }

        @SubscribeEvent
        public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            MEMORY.remove(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            MEMORY.clear();
        }
    }
}
