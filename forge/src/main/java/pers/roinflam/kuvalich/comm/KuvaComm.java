package pers.roinflam.kuvalich.comm;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.network.packet.KuvaCommPacket;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.function.BiFunction;

/**
 * 通讯窗的服务端传输层：把一句台词发给一个玩家，由客户端按自己的语言解析并排进通讯窗
 *
 * <p>这里只管发送，不管说什么、什么时候说、怎么限流；那些在 {@code LichComms} 里。台词用
 * {@link Component#translatable} 组好再交过来，客户端按自己的语言解析，所以服务端不需要知道玩家用什么语言。</p>
 *
 * <p>通用代码，专用服也加载，不引用任何客户端类。</p>
 */
public final class KuvaComm {

    /** 插队：当前窗快速收起，再开新窗 */
    public static final int URGENT = 1;
    /** 到场：窗以青色故障显形 */
    public static final int ARRIVAL = 2;
    /** 随机嘲讽：窗忙或队列里有东西就直接丢掉 */
    public static final int AMBIENT = 4;
    /** 这句同时进聊天栏 */
    public static final int CHAT_COPY = 8;
    /** 玄骸这句不嘟囔 */
    public static final int NO_MUTTER = 16;

    private static final int FLAG_MASK = URGENT | ARRIVAL | AMBIENT | CHAT_COPY | NO_MUTTER;

    /** 玄骸的名字和外观由谁来定（专属玄骸那一套注册进来；没有注册时走兜底） */
    private static volatile BiFunction<ServerPlayer, LivingEntity, LichNameAndLook> lichResolver;

    private KuvaComm() {
    }

    /** 玄骸的显示名和外观 */
    public record LichNameAndLook(Component name, LichLook look) {
    }

    /**
     * 发一句台词。
     *
     * @param to     收话人
     * @param who    谁在说
     * @param name   名牌；{@code null} 时客户端按发言人取默认名（Lotus、Ordis）
     * @param line   台词
     * @param flags  {@link #URGENT} 等标志位的组合
     * @param look   发言人是玄骸时的外观；其他人忽略
     * @param holdMs 字幕至少停留多久；0 表示客户端按字数和音节自己算
     */
    public static void say(ServerPlayer to, CommSpeaker who, @Nullable Component name, Component line,
                           int flags, @Nullable LichLook look, int holdMs) {
        if (to == null || KuvaLich.network == null) {
            return;
        }
        LichLook sendLook = who == CommSpeaker.LICH ? (look != null ? look : LichLook.NONE) : null;
        KuvaCommPacket packet = new KuvaCommPacket(who, flags & FLAG_MASK, name, line, sendLook, Math.max(0, holdMs));
        KuvaLich.network.send(PacketDistributor.PLAYER.with(() -> to), packet);
    }

    /**
     * 玄骸说话：名字和外观交给解析器，没有注册解析器（或解析出错）时用实体的显示名，
     * 种子取实体 UUID 的哈希，不画武器。
     *
     * @param lich 世界里的那只玄骸；可以为空（比如它已经离场、台词是事后补发的）
     */
    public static void sayLich(ServerPlayer to, @Nullable LivingEntity lich, Component line, int flags, int holdMs) {
        LichNameAndLook resolved = null;
        BiFunction<ServerPlayer, LivingEntity, LichNameAndLook> resolver = lichResolver;
        if (resolver != null) {
            try {
                resolved = resolver.apply(to, lich);
            } catch (RuntimeException e) {
                LogUtil.error("解析玄骸通讯的名字和外观时出错", e);
            }
        }
        if (resolved == null) {
            resolved = fallback(lich);
        }
        say(to, CommSpeaker.LICH, resolved.name(), line, flags, resolved.look(), holdMs);
    }

    /** 注册玄骸名字和外观的解析器；传 {@code null} 恢复兜底做法 */
    public static void setLichResolver(@Nullable BiFunction<ServerPlayer, LivingEntity, LichNameAndLook> resolver) {
        lichResolver = resolver;
    }

    private static LichNameAndLook fallback(@Nullable LivingEntity lich) {
        if (lich == null) {
            return new LichNameAndLook(Component.translatable(CommSpeaker.LICH.nameKey()), LichLook.NONE);
        }
        return new LichNameAndLook(lich.getDisplayName(),
                new LichLook(lich.getUUID().hashCode(), -1, (byte) -1, lich.getId()));
    }
}
