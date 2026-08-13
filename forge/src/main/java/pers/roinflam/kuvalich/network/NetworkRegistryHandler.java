package pers.roinflam.kuvalich.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.kuvalich.network.message.CodexGiveItemPacket;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.network.message.DecryptionHudPacket;
import pers.roinflam.kuvalich.network.message.ModuleDiscoveryPacket;
import pers.roinflam.kuvalich.network.message.RequiemGateFillPacket;
import pers.roinflam.kuvalich.network.message.WarframeModuleSyncPacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Optional;

/**
 * 网络注册处理器
 * Network registry handler
 *
 * 统一管理所有网络包的注册
 * Unified management of all packet registrations
 *
 * ⭐ 变更：移除 DiggingSpeedPacket，新增 WarframeModuleSyncPacket
 * ⭐ 变更：新增 DecryptionHudPacket（顶部解密进度HUD同步包）
 * ⭐ 变更：新增 RequiemGateFillPacket（灭骸之扉一键补全答案包，创造模式专用）
 * ⭐ 协议版本升级为 "4"（包列表变更，不兼容旧版客户端）
 *
 * <p>⭐ 安全修复：所有包在注册时显式声明 {@link NetworkDirection}。
 * 此前使用的 5 参 {@code registerMessage} 重载不限制方向，Forge 会允许包双向收发，
 * 意味着改装客户端可以把任意 S2C 包发给服务端，服务端仍会执行 {@code decode}。
 * 配合 {@code ModuleDiscoveryPacket.decode} 中不受限的 {@code readInt()} 长度字段，
 * 可以构造出直接触发服务端 OOM 的崩服包。</p>
 *
 * <p>声明方向后，Forge 会在解码之前就拒绝方向不符的包，
 * 从源头切断"客户端伪造 S2C 包攻击服务端"这一路径。</p>
 *
 * <p>注意：声明方向不改变任何字节流格式，也不改变包列表，
 * 因此 PROTOCOL_VERSION 保持 "4" 不变，新旧客户端仍可互连。</p>
 */
public class NetworkRegistryHandler {

    /** 网络通道 / Network channel */
    private static SimpleChannel INSTANCE;

    /**
     * 网络协议版本
     * ⭐ 升级为 "4"：在 "3"（DecryptionHudPacket）基础上再新增 RequiemGateFillPacket，
     *    包列表变更，不兼容旧版客户端
     */
    private static final String PROTOCOL_VERSION = "4";

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
     *
     * <p>⭐ 每个包都显式声明收发方向，Forge 会拒绝方向不符的包。</p>
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

        // 模组发现记录同步包（服务器 → 客户端）
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

        // ⭐ 战甲模组完整同步包（服务器 → 客户端）
        // 替代原 DiggingSpeedPacket，同步完整模组数据+击杀叠层
        INSTANCE.registerMessage(
                nextMessageId(),
                WarframeModuleSyncPacket.class,
                WarframeModuleSyncPacket::encode,
                WarframeModuleSyncPacket::decode,
                WarframeModuleSyncPacket::handle,
                TO_CLIENT
        );

        // ⭐ 顶部解密进度HUD同步包（服务器 → 客户端）
        // 击杀奴仆/玄骸获得解密进度时下发，驱动屏幕顶部HUD的填充补间与揭示提示
        INSTANCE.registerMessage(
                nextMessageId(),
                DecryptionHudPacket.class,
                DecryptionHudPacket::encode,
                DecryptionHudPacket::decode,
                DecryptionHudPacket::handle,
                TO_CLIENT
        );

        // ⭐ 灭骸之扉一键补全答案包（客户端 → 服务器，创造模式专用）
        // 创造玩家在灭骸之扉按 TAB 时请求服务端填入正确答案卡片
        INSTANCE.registerMessage(
                nextMessageId(),
                RequiemGateFillPacket.class,
                RequiemGateFillPacket::encode,
                RequiemGateFillPacket::decode,
                RequiemGateFillPacket::handle,
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
