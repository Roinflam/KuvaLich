// 文件：KuvaLich.java
// 路径：src/main/java/pers/roinflam/kuvalich/KuvaLich.java
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
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;
import pers.roinflam.kuvalich.proxy.CommonProxy;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.world.WorldGenBase;
import software.bernie.geckolib3.GeckoLib;

import java.util.Timer;
import java.util.TimerTask;

@Mod(modid = Reference.MOD_ID, useMetadata = true)
public class KuvaLich {
    @Mod.Instance
    public static KuvaLich instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    public static SimpleNetworkWrapper network;

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent evt) {
        LogUtil.info("赤毒玄骸模组开始预初始化...");

        try {
            GeckoLib.initialize();
            LogUtil.debug("GeckoLib动画库初始化成功");

            NetworkRegistryHandler.register();
            LogUtil.debug("网络处理器注册成功");

            CapabilityRegistryHandler.register();
            LogUtil.debug("Capability系统注册成功");

            // 注册网络消息包（服务端->客户端）
            network = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID);
            network.registerMessage(DamagePacket.Handler.class, DamagePacket.class, 0, Side.CLIENT);
            network.registerMessage(DiggingSpeedPacket.Handler.class, DiggingSpeedPacket.class, 1, Side.CLIENT);
            LogUtil.debug("网络消息包注册成功");

            // 注册世界生成器
            GameRegistry.registerWorldGenerator(
                    new WorldGenBase(DimensionType.OVERWORLD, KuvaLichBlocks.REQUIEM_ORE.getDefaultState(), 1, 28, 2, 4, 1, 4),
                    3
            );
            LogUtil.debug("安魂矿石世界生成器注册成功");

            GameRegistry.registerWorldGenerator(
                    new WorldGenBase(DimensionType.OVERWORLD, KuvaLichBlocks.EXPERIENCE_ORE.getDefaultState(), 1, 128, 2, 4, 2, 8),
                    3
            );
            LogUtil.debug("经验矿石世界生成器注册成功");

            LogUtil.info("赤毒玄骸模组预初始化完成");
        } catch (Exception e) {
            LogUtil.error("赤毒玄骸模组预初始化失败", e);
            throw e;
        }
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {
        LogUtil.debug("赤毒玄骸模组初始化阶段");
    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {
        LogUtil.debug("赤毒玄骸模组后初始化阶段");

        // 启动缓存清理定时器（服务端和客户端都需要）
        Timer cacheCleanerTimer = new Timer("KuvaLich-Cache-Cleaner", true);
        cacheCleanerTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    ModuleBase.cleanExpiredCache();
                    LogUtil.debug("缓存清理完成");
                } catch (Exception e) {
                    LogUtil.error("缓存清理失败", e);
                }
            }
        }, 300000, 300000); // 5分钟清理一次

        LogUtil.info("赤毒玄骸模组完全加载完成");
    }
}