package pers.roinflam.kuvalich;

import net.minecraft.world.DimensionType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;
import pers.roinflam.kuvalich.proxy.CommonProxy;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.world.WorldGenBase;
import software.bernie.geckolib3.GeckoLib;

/**
 * 赤毒玄骸模组主类
 *
 * 负责模组的初始化、网络注册、世界生成等核心功能
 *
 * @author RoinFlam
 */
@Mod(modid = Reference.MOD_ID, useMetadata = true)
public class KuvaLich {
    @Mod.Instance
    public static KuvaLich instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    /** 网络通信通道 */
    public static SimpleNetworkWrapper network;

    /**
     * 模组预初始化阶段
     *
     * 执行以下操作：
     * 1. 初始化GeckoLib动画库
     * 2. 注册网络处理器
     * 3. 注册Capability系统
     * 4. 注册网络消息包
     * 5. 注册世界生成器
     */
    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent evt) {
        LogUtil.info("赤毒玄骸模组开始预初始化...");

        try {
            // 初始化GeckoLib动画库
            GeckoLib.initialize();
            LogUtil.debug("GeckoLib动画库初始化成功");

            // 注册网络处理器和Capability
            NetworkRegistryHandler.register();
            LogUtil.debug("网络处理器注册成功");

            CapabilityRegistryHandler.register();
            LogUtil.debug("Capability系统注册成功");

            // 注册网络消息包
            network = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID);
            network.registerMessage(DamagePacket.Handler.class, DamagePacket.class, 0, Side.CLIENT);
            network.registerMessage(DiggingSpeedPacket.Handler.class, DiggingSpeedPacket.class, 1, Side.CLIENT);
            LogUtil.debug("网络消息包注册成功 - 伤害显示包(ID:0), 挖掘速度包(ID:1)");

            // 注册安魂矿石世界生成器
            // 参数: 维度(主世界), 方块状态, 最小Y(1), 最大Y(28), 最小矿脉大小(2), 最大矿脉大小(4), 区块最小生成次数(1), 区块最大生成次数(4)
            GameRegistry.registerWorldGenerator(
                    new WorldGenBase(DimensionType.OVERWORLD, KuvaLichBlocks.REQUIEM_ORE.getDefaultState(), 1, 28, 2, 4, 1, 4),
                    3
            );
            LogUtil.debug("安魂矿石世界生成器注册成功 - Y轴范围:1-28, 矿脉大小:2-4, 每区块生成:1-4次");

            // 注册经验矿石世界生成器
            // 参数: 维度(主世界), 方块状态, 最小Y(1), 最大Y(128), 最小矿脉大小(2), 最大矿脉大小(4), 区块最小生成次数(2), 区块最大生成次数(8)
            GameRegistry.registerWorldGenerator(
                    new WorldGenBase(DimensionType.OVERWORLD, KuvaLichBlocks.EXPERIENCE_ORE.getDefaultState(), 1, 128, 2, 4, 2, 8),
                    3
            );
            LogUtil.debug("经验矿石世界生成器注册成功 - Y轴范围:1-128, 矿脉大小:2-4, 每区块生成:2-8次");

            LogUtil.info("赤毒玄骸模组预初始化完成");
        } catch (Exception e) {
            LogUtil.error("赤毒玄骸模组预初始化失败", e);
            throw e;
        }
    }

    /**
     * 模组初始化阶段
     *
     * 目前暂无需要在此阶段执行的操作
     */
    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
        LogUtil.debug("赤毒玄骸模组初始化阶段");
    }

    /**
     * 模组后初始化阶段
     *
     * 目前暂无需要在此阶段执行的操作
     */
    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
        LogUtil.debug("赤毒玄骸模组后初始化阶段");
    }
}