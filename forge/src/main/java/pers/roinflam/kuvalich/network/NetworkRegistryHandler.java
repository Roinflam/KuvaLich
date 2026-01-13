package pers.roinflam.kuvalich.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 网络注册处理器
 * Network registry handler
 *
 * 统一管理所有网络包的注册
 * Unified management of all packet registrations
 */
public class NetworkRegistryHandler {

    /** 网络通道 / Network channel */
    private static SimpleChannel INSTANCE;

    /** 网络协议版本 / Network protocol version */
    private static final String PROTOCOL_VERSION = "1";

    /** 消息ID计数器 / Message ID counter */
    private static int messageId = 0;

    /**
     * 注册网络通道和消息包
     * Register network channel and packets
     */
    public static void register() {
        LogUtil.info("开始注册网络通道...");

        // 创建网络通道
        // Create network channel
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Reference.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        // 注册消息包
        // Register packets
        registerMessages();

        LogUtil.info("网络通道注册成功，共注册 " + messageId + " 个消息包");
    }

    /**
     * 注册所有消息包
     * Register all packets
     */
    private static void registerMessages() {
        // 伤害显示包（服务器 → 客户端）
        // Damage display packet (Server → Client)
        INSTANCE.registerMessage(
                nextMessageId(),
                DamagePacket.class,
                DamagePacket::encode,
                DamagePacket::decode,
                DamagePacket::handle
        );

        // 挖掘速度包（服务器 → 客户端）
        // Digging speed packet (Server → Client)
        INSTANCE.registerMessage(
                nextMessageId(),
                DiggingSpeedPacket.class,
                DiggingSpeedPacket::encode,
                DiggingSpeedPacket::decode,
                DiggingSpeedPacket::handle
        );
    }

    /**
     * 获取下一个消息ID
     * Get next message ID
     */
    private static int nextMessageId() {
        return messageId++;
    }

    /**
     * 获取网络通道实例
     * Get network channel instance
     */
    public static SimpleChannel getChannel() {
        if (INSTANCE == null) {
            throw new IllegalStateException("网络通道未初始化！请先调用register()方法。");
        }
        return INSTANCE;
    }
}