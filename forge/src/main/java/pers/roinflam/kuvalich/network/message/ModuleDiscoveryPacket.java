package pers.roinflam.kuvalich.network.message;

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
 * ⭐ 发现记录键格式升级为 type:rarityOrder（如 "fury:1"、"fury:3"）
 *    兼容旧存档：旧的纯 type 格式（如 "fury"）在查询时作为回退匹配。
 *    即旧玩家如果有 "fury" 记录，查询 "fury:1" 或 "fury:3" 均视为已发现。
 *
 * <p>⭐ 安全修复：{@code decode} 中的条目数量与单键长度加上了硬上限。
 * 修复前 {@code int size = buffer.readInt()} 完全不受限，
 * 且该包在 NetworkRegistryHandler 中未声明方向（Forge 默认双向），
 * 改装客户端只要发一个 {@code size = Integer.MAX_VALUE} 的包，
 * 服务端执行到 {@code new HashSet<>(size)} 时就会立刻尝试分配巨大的哈希表并 OOM 崩服。
 * 现在双管齐下：注册处锁定为 S2C，解码处再做长度校验兜底。</p>
 */
public class ModuleDiscoveryPacket {

    // ==================== 解码安全上限 / Decode Safety Limits ====================

    /**
     * 单个包允许携带的最大发现记录条目数。
     * <p>发现键格式为 type:rarityOrder，全模组（武器+战甲，4 品质）加上自定义模组
     * 数量级在数百，4096 是数量级以上的余量，正常玩家永远不会触碰。
     * 超过即判定为恶意包，抛出 {@link DecoderException} 由 Netty 断开该连接。</p>
     */
    private static final int MAX_DISCOVERY_ENTRIES = 4096;

    /**
     * 单个发现记录键允许的最大字符数。
     * <p>实际键形如 "heavy_caliber:2"，远小于 256。</p>
     */
    private static final int MAX_KEY_LENGTH = 256;

    /** 已发现的模组key集合 / Discovered module key set */
    private final Set<String> discoveredTypes;

    // ==================== 客户端缓存 / Client Cache ====================

    /** 客户端缓存的已发现模组集合（仅客户端使用）/ Client-side cache (client-only) */
    private static volatile Set<String> clientDiscoveredCache = new HashSet<>();

    /**
     * 构造同步包
     *
     * @param discoveredTypes 已发现的模组key集合
     */
    public ModuleDiscoveryPacket(Set<String> discoveredTypes) {
        this.discoveredTypes = discoveredTypes != null ? discoveredTypes : new HashSet<>();
    }

    // ==================== 编解码 / Encode/Decode ====================

    /**
     * 编码到字节缓冲
     *
     * <p>⭐ 与解码端对称截断：若集合规模异常超过 {@link #MAX_DISCOVERY_ENTRIES}，
     * 只写出前 N 条并打警告，避免服务端写出一个客户端必定拒收的包导致玩家掉线。
     * 正常情况下永远不会触发。</p>
     *
     * @param packet 待编码包
     * @param buffer 字节缓冲
     */
    public static void encode(ModuleDiscoveryPacket packet, FriendlyByteBuf buffer) {
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
     * <p>⭐ 对长度字段做严格校验：负数或超过 {@link #MAX_DISCOVERY_ENTRIES} 一律拒绝，
     * 防止恶意长度字段导致的内存耗尽。</p>
     *
     * @param buffer 字节缓冲
     * @return 解码得到的包
     * @throws DecoderException 长度字段非法时抛出，由 Netty 断开连接
     */
    public static ModuleDiscoveryPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        if (size < 0 || size > MAX_DISCOVERY_ENTRIES) {
            throw new DecoderException("ModuleDiscoveryPacket 条目数非法: " + size
                    + "（允许范围 0 ~ " + MAX_DISCOVERY_ENTRIES + "）");
        }

        Set<String> types = new HashSet<>(Math.max(16, size * 2));
        for (int i = 0; i < size; i++) {
            types.add(buffer.readUtf(MAX_KEY_LENGTH));
        }
        return new ModuleDiscoveryPacket(types);
    }

    public static void handle(ModuleDiscoveryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet));
        });
        context.setPacketHandled(true);
    }

    private static void handleClient(ModuleDiscoveryPacket packet) {
        clientDiscoveredCache = new HashSet<>(packet.discoveredTypes);
    }

    // ==================== 客户端查询接口 / Client Query API ====================

    /**
     * 查询模组是否已发现（客户端调用）
     * Check if module is discovered (client-side call)
     *
     * ⭐ 支持新旧两种格式的兼容查询：
     * 1. 先精确匹配 discoveryKey（如 "fury:3"）
     * 2. 若未命中，回退检查旧格式纯 type（如 "fury"）
     *    旧存档中不带冒号的记录会匹配所有品质
     *
     * @param discoveryKey 发现记录键（新格式 type:rarityOrder，或旧格式纯 type）
     * @return 是否已发现
     */
    public static boolean isDiscovered(String discoveryKey) {
        if (discoveryKey == null || discoveryKey.isEmpty()) {
            return false;
        }
        // 精确匹配 / Exact match
        if (clientDiscoveredCache.contains(discoveryKey)) {
            return true;
        }
        // ⭐ 旧存档兼容：新格式（type:rarity）未命中时，检查旧格式纯type是否存在
        // ⭐ Legacy compat: if new format (type:rarity) not found, check old pure type
        int colonIdx = discoveryKey.indexOf(':');
        if (colonIdx > 0) {
            String legacyType = discoveryKey.substring(0, colonIdx);
            return clientDiscoveredCache.contains(legacyType);
        }
        return false;
    }

    /**
     * 获取已发现模组数量（客户端调用）
     * Get discovered module count (client-side call)
     *
     * @return 已发现数量
     */
    public static int getDiscoveredCount() {
        return clientDiscoveredCache.size();
    }

    /**
     * 获取已发现模组集合的不可变视图（客户端调用）
     * Get immutable view of discovered modules (client-side call)
     *
     * @return 不可变Set
     */
    public static Set<String> getDiscoveredSet() {
        return Collections.unmodifiableSet(clientDiscoveredCache);
    }

    /**
     * 清理客户端缓存（退出世界时调用）
     * Clear client cache (call when leaving world)
     */
    public static void clearClientCache() {
        clientDiscoveredCache = new HashSet<>();
    }

    // ==================== 服务端发送接口 / Server Send API ====================

    /**
     * 向指定玩家发送完整的已发现模组同步包
     * Send full discovered modules sync packet to specified player
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
                    new ModuleDiscoveryPacket(discovered)
            );
        });
    }

    /**
     * 在服务端记录模组发现并同步到客户端
     * Record module discovery on server and sync to client
     *
     * ⭐ discoveryKey 应使用 type:rarityOrder 格式
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
                // 新发现 → 全量同步 / New discovery → full sync
                syncToPlayer(player);
            }
        });
        return isNew[0];
    }
}
