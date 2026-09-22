package pers.roinflam.kuvalich.network.packet;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 模组发现记录同步包（服务器 → 客户端）
 * Module discovery sync packet (Server → Client)
 *
 * 将玩家已发现的模组key集合同步到客户端，供图鉴界面渲染使用。
 *
 * ⭐ 发现记录键格式为 type:rarityOrder（如 "fury:1"、"fury:3"）
 *    兼容旧存档：旧的纯 type 格式（如 "fury"）在查询时作为回退匹配。
 *
 * <p>⭐ 性能优化（本次）：新增<b>增量模式</b>。
 * 原实现里 {@link #discoverAndSync} 每发现一个新模组就全量重发一次
 * （全模组数百条 key），玩家早期几乎每分钟都在触发。
 * 现在 {@code full=false} 的包只携带新增的 key，客户端做并集合并；
 * 登录 / 背包扫描等场景仍走 {@code full=true} 全量替换，保证状态权威。</p>
 *
 * <p>⭐ 安全：{@code decode} 中的条目数量与单键长度有硬上限，
 * 且该包在 {@code NetworkRegistryHandler} 中已显式声明为 S2C，
 * 客户端无法反向发包触发服务端解码。</p>
 */
public class ModuleDiscoveryPacket {

    // ==================== 解码安全上限 / Decode Safety Limits ====================

    /**
     * 单个包允许携带的最大发现记录条目数。
     */
    private static final int MAX_DISCOVERY_ENTRIES = 4096;

    /**
     * 单个发现记录键允许的最大字符数。
     */
    private static final int MAX_KEY_LENGTH = 256;

    /** 已发现的模组key集合（全量模式=完整集合，增量模式=新增部分）*/
    private final Set<String> discoveredTypes;

    /**
     * ⭐ 是否为全量包
     * <p>true = 客户端整体替换缓存；false = 客户端与现有缓存做并集。</p>
     */
    private final boolean full;

    // ==================== 客户端缓存 / Client Cache ====================

    /** 客户端缓存的已发现模组集合（仅客户端使用）*/
    private static volatile Set<String> clientDiscoveredCache = new HashSet<>();

    /**
     * 构造同步包
     *
     * @param discoveredTypes 模组key集合
     * @param full            是否为全量包
     */
    public ModuleDiscoveryPacket(Set<String> discoveredTypes, boolean full) {
        this.discoveredTypes = discoveredTypes != null ? discoveredTypes : new HashSet<>();
        this.full = full;
    }

    /**
     * 构造全量同步包（兼容旧调用）
     *
     * @param discoveredTypes 已发现的模组key集合
     */
    public ModuleDiscoveryPacket(Set<String> discoveredTypes) {
        this(discoveredTypes, true);
    }

    // ==================== 编解码 / Encode/Decode ====================

    /**
     * 编码到字节缓冲
     *
     * @param packet 待编码包
     * @param buffer 字节缓冲
     */
    public static void encode(ModuleDiscoveryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.full);

        int total = packet.discoveredTypes.size();
        int size = Math.min(total, MAX_DISCOVERY_ENTRIES);
        if (total > MAX_DISCOVERY_ENTRIES) {
            LogUtil.warn("模组发现记录数量异常(" + total + ")，已截断为 " + MAX_DISCOVERY_ENTRIES + " 条发送");
        }

        buffer.writeInt(size);
        int written = 0;
        for (String type : packet.discoveredTypes) {
            if (written >= size) {
                break;
            }
            buffer.writeUtf(type, MAX_KEY_LENGTH);
            written++;
        }
    }

    /**
     * 从字节缓冲解码
     *
     * @param buffer 字节缓冲
     * @return 解码得到的包
     * @throws DecoderException 长度字段非法时抛出，由 Netty 断开连接
     */
    public static ModuleDiscoveryPacket decode(FriendlyByteBuf buffer) {
        boolean full = buffer.readBoolean();

        int size = buffer.readInt();
        if (size < 0 || size > MAX_DISCOVERY_ENTRIES) {
            throw new DecoderException("ModuleDiscoveryPacket 条目数非法: " + size
                    + "（允许范围 0 ~ " + MAX_DISCOVERY_ENTRIES + "）");
        }

        Set<String> types = new HashSet<>(Math.max(16, size * 2));
        for (int i = 0; i < size; i++) {
            types.add(buffer.readUtf(MAX_KEY_LENGTH));
        }
        return new ModuleDiscoveryPacket(types, full);
    }

    /**
     * 处理网络包
     *
     * @param packet          收到的包
     * @param contextSupplier 网络事件上下文
     */
    public static void handle(ModuleDiscoveryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });
        context.setPacketHandled(true);
    }

    /**
     * 客户端处理：全量包整体替换，增量包做并集合并
     *
     * @param packet 收到的包
     */
    private static void handleClient(ModuleDiscoveryPacket packet) {
        if (packet.full) {
            clientDiscoveredCache = new HashSet<>(packet.discoveredTypes);
        } else {
            if (packet.discoveredTypes.isEmpty()) {
                return;
            }
            // 写时复制：新建集合后整体替换引用，避免读线程看到中间状态
            Set<String> merged = new HashSet<>(clientDiscoveredCache);
            merged.addAll(packet.discoveredTypes);
            clientDiscoveredCache = merged;
        }
    }

    // ==================== 客户端查询接口 / Client Query API ====================

    /**
     * 查询模组是否已发现（客户端调用）
     *
     * <p>支持新旧两种格式的兼容查询：先精确匹配 discoveryKey，
     * 未命中则回退检查旧格式纯 type（旧存档中不带冒号的记录匹配所有品质）。</p>
     *
     * @param discoveryKey 发现记录键（新格式 type:rarityOrder，或旧格式纯 type）
     * @return 是否已发现
     */
    public static boolean isDiscovered(String discoveryKey) {
        return isDiscovered(discoveryKey, null);
    }

    /**
     * 查询发现状态（免分配版）
     *
     * <p>⭐ 为什么要这个重载：单参数版在「新格式未命中」时必然执行
     * {@code discoveryKey.substring(0, colonIdx)} 新建一个字符串再查旧格式缓存。
     * 而图鉴的标题计数会对<b>全量</b>条目（武器页 429 条）跑这个判定，
     * 未发现的条目越多分配越多 —— 发现率 12% 时每次全量扫描就是约 379 次
     * substring，图鉴只要开着就在持续制造纯属可避免的 GC 压力。</p>
     *
     * <p>调用方手上本来就有 type（{@code CodexEntry.moduleType}），
     * 直接传进来就不必从 key 里再切一次。</p>
     *
     * @param discoveryKey 发现记录键（新格式 {@code type:rarityOrder}）
     * @param legacyType   同一条目的纯 type；传 null 时退回从 key 里切
     * @return 是否已发现
     */
    public static boolean isDiscovered(String discoveryKey, String legacyType) {
        if (discoveryKey == null || discoveryKey.isEmpty()) {
            return false;
        }
        Set<String> cache = clientDiscoveredCache;
        // 精确匹配 / Exact match
        if (cache.contains(discoveryKey)) {
            return true;
        }
        // ⭐ 旧存档兼容：新格式未命中时，检查旧格式纯type是否存在
        if (legacyType != null) {
            return !legacyType.isEmpty() && cache.contains(legacyType);
        }
        int colonIdx = discoveryKey.indexOf(':');
        if (colonIdx > 0) {
            return cache.contains(discoveryKey.substring(0, colonIdx));
        }
        return false;
    }

    /**
     * 获取已发现模组数量（客户端调用）
     *
     * @return 已发现数量
     */
    public static int getDiscoveredCount() {
        return clientDiscoveredCache.size();
    }

    /**
     * 获取已发现模组集合的不可变视图（客户端调用）
     *
     * @return 不可变Set
     */
    public static Set<String> getDiscoveredSet() {
        return Collections.unmodifiableSet(clientDiscoveredCache);
    }

    /**
     * 清理客户端缓存（退出世界时调用）
     */
    public static void clearClientCache() {
        clientDiscoveredCache = new HashSet<>();
    }

    // ==================== 服务端发送接口 / Server Send API ====================

    /**
     * 向指定玩家发送<b>完整</b>的已发现模组同步包
     *
     * @param player 目标玩家
     */
    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            Set<String> discovered = requiemCard.getDiscoveredModules();
            KuvaLich.network.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ModuleDiscoveryPacket(discovered, true)
            );
        });
    }

    /**
     * 在服务端记录模组发现并同步到客户端
     *
     * <p>⭐ 只发送<b>增量</b>包（仅含本次新发现的一条 key），
     * 不再每次都把全部数百条记录重发一遍。</p>
     *
     * @param player       目标玩家
     * @param discoveryKey 发现记录键（type:rarityOrder 格式）
     * @return 是否为新发现（true=新的，false=已有记录）
     */
    public static boolean discoverAndSync(ServerPlayer player, String discoveryKey) {
        if (player == null || discoveryKey == null || discoveryKey.isEmpty()) {
            return false;
        }
        boolean[] isNew = {false};
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            if (requiemCard.discoverModule(discoveryKey)) {
                isNew[0] = true;
                // ⭐ 增量同步：只发这一条新 key
                Set<String> delta = new HashSet<>(2);
                delta.add(discoveryKey);
                KuvaLich.network.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new ModuleDiscoveryPacket(delta, false)
                );
            }
        });
        return isNew[0];
    }
}
