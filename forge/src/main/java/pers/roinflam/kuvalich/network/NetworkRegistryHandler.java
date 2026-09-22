package pers.roinflam.kuvalich.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.kuvalich.network.packet.CodexGiveItemPacket;
import pers.roinflam.kuvalich.network.packet.CodexInstallModulePacket;
import pers.roinflam.kuvalich.network.packet.DamagePacket;
import pers.roinflam.kuvalich.network.packet.DecryptionHudPacket;
import pers.roinflam.kuvalich.network.packet.ModuleDiscoveryPacket;
import pers.roinflam.kuvalich.network.packet.RequiemGateFillPacket;
import pers.roinflam.kuvalich.network.packet.WarframeModuleSyncPacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Optional;

/**
 * 网络注册处理器
 * Network registry handler
 *
 * 统一管理所有网络包的注册
 *
 * <p>⭐ 协议版本升级为 "7"：{@link DamagePacket} 改为传拆开的字段（伤害数字合并），
 * {@link WarframeModuleSyncPacket} 追加了武器类击杀叠层数组（客户端射速 / 多重射击与服务端对齐），
 * 字节流格式发生变化，与 "6" 及更早版本的客户端不兼容，必须提版本号强制握手拒绝，
 * 否则旧客户端会按老格式解码出错位数据。</p>
 *
 * <p>⭐ 安全：所有包在注册时都显式声明 {@link NetworkDirection}。
 * 不带方向的 5 参 {@code registerMessage} 重载会让 Forge 允许包双向收发，
 * 意味着改装客户端可以把任意 S2C 包发给服务端并触发服务端 {@code decode}。
 * 声明方向后，Forge 会在解码之前就拒绝方向不符的包。</p>
 */
public class NetworkRegistryHandler {

    /** 网络通道 / Network channel */
    private static SimpleChannel INSTANCE;

    /**
     * 网络协议版本
     * ⭐ "6"：DamagePacket 改为传拆开的字段（伤害数字合并），字节流格式变更
     * ⭐ "7"：WarframeModuleSyncPacket 追加武器类击杀叠层数组，字节流格式变更
     */
    private static final String PROTOCOL_VERSION = "7";

    /** 消息ID计数器 / Message ID counter */
    private static int messageId = 0;

    /** 服务端 → 客户端 方向常量（避免每次注册重复装箱） */
    private static final Optional<NetworkDirection> TO_CLIENT =
            Optional.of(NetworkDirection.PLAY_TO_CLIENT);

    /** 客户端 → 服务端 方向常量 */
    private static final Optional<NetworkDirection> TO_SERVER =
            Optional.of(NetworkDirection.PLAY_TO_SERVER);

    /**
     * 注册网络通道和消息包
     * Register network channel and packets
     */
    public static void register() {
        LogUtil.info("开始注册网络通道...");

        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Reference.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        registerMessages();

        LogUtil.info("网络通道注册成功,共注册 " + messageId + " 个消息包");
    }

    /**
     * 注册所有消息包
     * Register all packets
     */
    private static void registerMessages() {
        // 伤害显示包（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                DamagePacket.class,
                DamagePacket::encode,
                DamagePacket::decode,
                DamagePacket::handle,
                TO_CLIENT
        );

        // 模组发现记录同步包（服务器 → 客户端，支持全量/增量两种模式）
        INSTANCE.registerMessage(
                nextMessageId(),
                ModuleDiscoveryPacket.class,
                ModuleDiscoveryPacket::encode,
                ModuleDiscoveryPacket::decode,
                ModuleDiscoveryPacket::handle,
                TO_CLIENT
        );

        // 图鉴创造模式给予物品包（客户端 → 服务器）
        INSTANCE.registerMessage(
                nextMessageId(),
                CodexGiveItemPacket.class,
                CodexGiveItemPacket::encode,
                CodexGiveItemPacket::decode,
                CodexGiveItemPacket::handle,
                TO_SERVER
        );

        // 战甲模组完整同步包（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                WarframeModuleSyncPacket.class,
                WarframeModuleSyncPacket::encode,
                WarframeModuleSyncPacket::decode,
                WarframeModuleSyncPacket::handle,
                TO_CLIENT
        );

        // 顶部解密进度HUD同步包（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                DecryptionHudPacket.class,
                DecryptionHudPacket::encode,
                DecryptionHudPacket::decode,
                DecryptionHudPacket::handle,
                TO_CLIENT
        );

        // 灭骸之扉一键补全答案包（客户端 → 服务器，创造模式专用）
        INSTANCE.registerMessage(
                nextMessageId(),
                RequiemGateFillPacket.class,
                RequiemGateFillPacket::encode,
                RequiemGateFillPacket::decode,
                RequiemGateFillPacket::handle,
                TO_SERVER
        );

        // 图鉴创造模式一键装配包（客户端 → 服务器）
        // ⚠ 新包必须追加在最后：messageId 是顺序分配的，插在中间会让已有包的 id 全部后移，
        //    与旧版客户端/服务端连接时就会对不上号。
        INSTANCE.registerMessage(
                nextMessageId(),
                CodexInstallModulePacket.class,
                CodexInstallModulePacket::encode,
                CodexInstallModulePacket::decode,
                CodexInstallModulePacket::handle,
                TO_SERVER
        );
    }

    private static int nextMessageId() {
        return messageId++;
    }

    public static SimpleChannel getChannel() {
        if (INSTANCE == null) {
            throw new IllegalStateException("网络通道未初始化！请先调用register()方法。");
        }
        return INSTANCE;
    }
}
