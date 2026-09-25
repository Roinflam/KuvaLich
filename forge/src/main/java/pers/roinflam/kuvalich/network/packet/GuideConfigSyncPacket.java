package pers.roinflam.kuvalich.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.guide.GuideConfigSnapshot;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 服务端配置快照包（服务器 → 客户端）
 *
 * <p>冒险指南里的数值要跟服务端实际生效的配置走，但两份 COMMON 配置 Forge 不会同步，
 * 所以登录时和配置热重载时整份下发一次。内容见 {@link GuideConfigSnapshot}，约 170 项、十来 KB。</p>
 *
 * <p>字符串上限 {@link #MAX_VALUE_LEN}：列表类配置（禁用模组、词条范围）编码成 JSON 数组后可能较长，
 * 默认的 32767 足够，这里放宽到 2^18 留出余量。</p>
 *
 * <p><b>超限时失败在服务端编码，不在客户端解码</b>：{@code SimpleChannel.send} 当场同步调用 {@link #encode}，
 * 单个值超过 {@link #MAX_VALUE_LEN} 时 {@code writeUtf} 抛 {@code EncoderException}，整包超过 1 MiB 时
 * 原版自定义负载包的构造器抛 {@code IllegalArgumentException}。{@link #sendTo} 是在登录事件里调用的，
 * 异常一路穿出 {@code PlayerList.placeNewPlayer}，玩家会以「无效的玩家数据」被踢 —— 每个人都进不了服。
 * 所以两个发送接口都兜住异常只记日志：发不出去时客户端按缺值处理，指南显示默认值。</p>
 *
 * @author RoinFlam
 */
public class GuideConfigSyncPacket {

    private static final int MAX_KEY_LEN = 256;
    private static final int MAX_VALUE_LEN = 1 << 18;
    /** 条目数上限：防止畸形包让客户端分配巨型 Map */
    private static final int MAX_ENTRIES = 4096;

    private final Map<String, String> values;

    public GuideConfigSyncPacket(Map<String, String> values) {
        this.values = values;
    }

    public static void encode(GuideConfigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.values.size());
        for (Map.Entry<String, String> e : msg.values.entrySet()) {
            buf.writeUtf(e.getKey(), MAX_KEY_LEN);
            buf.writeUtf(e.getValue(), MAX_VALUE_LEN);
        }
    }

    public static GuideConfigSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("GuideConfigSyncPacket 条目数异常: " + size);
        }
        Map<String, String> values = new LinkedHashMap<>(size * 2);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(MAX_KEY_LEN);
            values.put(key, buf.readUtf(MAX_VALUE_LEN));
        }
        return new GuideConfigSyncPacket(values);
    }

    public static void handle(GuideConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                pers.roinflam.kuvalich.client.gui.guide.GuideClientConfig.accept(msg.values)));
        ctx.setPacketHandled(true);
    }

    // ==================== 服务端发送接口 ====================

    /**
     * 发给单个玩家（登录时）
     *
     * @param player 目标玩家
     */
    public static void sendTo(ServerPlayer player) {
        if (KuvaLich.network == null) {
            return;
        }
        try {
            KuvaLich.network.send(PacketDistributor.PLAYER.with(() -> player),
                    new GuideConfigSyncPacket(GuideConfigSnapshot.capture()));
        } catch (RuntimeException e) {
            // 在登录事件里抛出去会把玩家踢下线，见类注释
            LogUtil.error("冒险指南配置快照发送失败，指南将显示默认值", e);
        }
    }

    /**
     * 广播给全服（配置热重载后）。必须在服务端主线程调用。
     *
     * @param server 服务器
     */
    public static void broadcast(MinecraftServer server) {
        if (KuvaLich.network == null || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        try {
            KuvaLich.network.send(PacketDistributor.ALL.noArg(),
                    new GuideConfigSyncPacket(GuideConfigSnapshot.capture()));
        } catch (RuntimeException e) {
            LogUtil.error("冒险指南配置快照广播失败，指南将保留上一次的数值", e);
        }
    }
}
