package pers.roinflam.kuvalich.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pers.roinflam.kuvalich.network.message.ElementDebuffPacket;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 元素特效专用网络通道
 * Element Effect Network Channel (self-contained)
 *
 * <p>独立于主 {@code NetworkRegistryHandler} 的 SimpleChannel，负责将元素 debuff
 * 的视觉标记从服务端实时推送到所有追踪该实体的客户端。</p>
 *
 * <p>为什么不复用 NetworkRegistryHandler：保持零侵入，用户仅需加入本 mod 包内
 * 的几个新文件即可启用，无需修改 mod 主类或其他网络注册代码。</p>
 *
 * <p>注册时机：在 {@link FMLCommonSetupEvent} 的 enqueueWork 中注册 packet，
 * 确保在网络初始化阶段之前完成。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ElementEffectNetwork {

    /** 网络协议版本 / Protocol version */
    private static final String VERSION = "1";

    /** 通道资源位置 / Channel resource location */
    private static final ResourceLocation CHANNEL_NAME =
            new ResourceLocation(Reference.MOD_ID, "element_effect");

    /** SimpleChannel 实例 / The SimpleChannel instance */
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(CHANNEL_NAME)
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    /** 已注册的 packet ID 计数器 */
    private static int nextPacketId = 0;

    /**
     * FMLCommonSetupEvent 触发时注册所有 packet
     *
     * @param event Forge 通用初始化事件
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ElementEffectNetwork::registerPackets);
    }

    /**
     * 注册所有元素特效相关的 packet
     */
    private static void registerPackets() {
        CHANNEL.registerMessage(
                nextPacketId++,
                ElementDebuffPacket.class,
                ElementDebuffPacket::encode,
                ElementDebuffPacket::decode,
                ElementDebuffPacket::handle
        );
    }

    /**
     * 将 packet 发送给所有正在追踪指定实体的玩家
     * <p>包括玩家自己（如果实体是玩家）+ 所有能看到该实体的其他玩家。
     * 这样可以保证宠物/女仆打出的元素特效，主人和周围玩家都能看到。</p>
     *
     * @param entity 被追踪的实体
     * @param packet 要发送的 packet
     */
    public static void sendToTrackers(Entity entity, ElementDebuffPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    /** 工具类禁止实例化 */
    private ElementEffectNetwork() {
    }
}
