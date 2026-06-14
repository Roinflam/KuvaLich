package pers.roinflam.kuvalich.network;

import net.minecraft.resources.ResourceLocation;
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
                DamagePacket::handle
        );

        // 模组发现记录同步包（服务器 → 客户端）
        INSTANCE.registerMessage(
                nextMessageId(),
                ModuleDiscoveryPacket.class,
                ModuleDiscoveryPacket::encode,
                ModuleDiscoveryPacket::decode,
                ModuleDiscoveryPacket::handle
        );

        // 图鉴创造模式给予物品包（客户端 → 服务器）
        INSTANCE.registerMessage(
                nextMessageId(),
                CodexGiveItemPacket.class,
                CodexGiveItemPacket::encode,
                CodexGiveItemPacket::decode,
                CodexGiveItemPacket::handle
        );

        // ⭐ 战甲模组完整同步包（服务器 → 客户端）
        // 替代原 DiggingSpeedPacket，同步完整模组数据+击杀叠层
        INSTANCE.registerMessage(
                nextMessageId(),
                WarframeModuleSyncPacket.class,
                WarframeModuleSyncPacket::encode,
                WarframeModuleSyncPacket::decode,
                WarframeModuleSyncPacket::handle
        );

        // ⭐ 顶部解密进度HUD同步包（服务器 → 客户端）
        // 击杀奴仆/玄骸获得解密进度时下发，驱动屏幕顶部HUD的填充补间与揭示提示
        INSTANCE.registerMessage(
                nextMessageId(),
                DecryptionHudPacket.class,
                DecryptionHudPacket::encode,
                DecryptionHudPacket::decode,
                DecryptionHudPacket::handle
        );

        // ⭐ 灭骸之扉一键补全答案包（客户端 → 服务器，创造模式专用）
        // 创造玩家在灭骸之扉按 TAB 时请求服务端填入正确答案卡片
        INSTANCE.registerMessage(
                nextMessageId(),
                RequiemGateFillPacket.class,
                RequiemGateFillPacket::encode,
                RequiemGateFillPacket::decode,
                RequiemGateFillPacket::handle
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
