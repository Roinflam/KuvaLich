package pers.roinflam.kuvalich;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.simple.SimpleChannel;

import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.config.ClothConfigScreen;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.*;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.tabs.KuvaLichCreativeTabs;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.Timer;
import java.util.TimerTask;

/**
 * 赤毒玄骸模组主类
 * Kuva Lich Mod Main Class
 *
 * @author RoinFlam
 */
@Mod(Reference.MOD_ID)
public class KuvaLich {

    /** 模组实例 / Mod instance */
    public static KuvaLich instance;

    /** 网络通道（通过NetworkRegistryHandler获取）/ Network channel (obtained via NetworkRegistryHandler) */
    public static SimpleChannel network;

    public KuvaLich() {
        instance = this;

        // 获取模组事件总线
        // Get mod event bus
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册方块 / Register blocks
        KuvaLichBlocks.BLOCKS.register(modEventBus);

        // 注册物品 / Register items
        KuvaLichItems.ITEMS.register(modEventBus);

        // 注册药水效果 / Register mob effects
        KuvaLichMobEffects.MOB_EFFECTS.register(modEventBus);

        // 注册附魔 / Register enchantments
        KuvaLichEnchantments.ENCHANTMENTS.register(modEventBus);

        // 注册实体类型 / Register entity types
        KuvaLichEntities.ENTITY_TYPES.register(modEventBus);

        // 注册MenuType / Register menu types
        KuvaLichMenuTypes.MENUS.register(modEventBus);

        // 注册创造模式标签页 / Register creative tabs
        KuvaLichCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // 注册设置事件
        // Register setup events
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // 注册配置
        // Register configurations
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                ModConfig.COMMON_CONFIG, "kuvalich-common.toml");
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                ModuleConfig.MODULE_CONFIG, "kuvalich-modules.toml");

        // 注册配置屏幕
        // Register config screen
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, screen) -> ClothConfigScreen.createConfigScreen(screen)
                )
        );

        LogUtil.info("赤毒玄骸模组构造函数执行完成");

        modEventBus.addListener(CapabilityRegistryHandler::registerCapabilities);

        LogUtil.info("Capability系统初始化完成");
    }

    /**
     * 通用设置阶段（客户端和服务端都执行）
     * Common setup phase (executed on both client and server)
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LogUtil.info("赤毒玄骸模组开始通用设置...");

        event.enqueueWork(() -> {
            try {
                // 注册网络处理器（已包含网络通道创建和消息包注册）
                // Register network handlers (includes network channel creation and packet registration)
                NetworkRegistryHandler.register();

                // 获取网络通道实例供其他地方使用
                // Get network channel instance for use in other places
                network = NetworkRegistryHandler.getChannel();

                LogUtil.debug("网络系统初始化成功");

                // ========== 初始化自定义模组系统 / Initialize Custom Module System ==========
                CustomModuleManager.getInstance().initialize();

                // 启动缓存清理定时器
                // Start cache cleanup timer
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
                }, 300000, 300000); // 5分钟清理一次 / Clean every 5 minutes

                LogUtil.info("赤毒玄骸模组通用设置完成");
            } catch (Exception e) {
                LogUtil.error("赤毒玄骸模组通用设置失败", e);
                throw e;
            }
        });
    }

    /**
     * 客户端设置阶段
     * Client setup phase
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LogUtil.debug("赤毒玄骸模组客户端设置完成");
    }

    /**
     * 注：1.20.1的世界生成已改为JSON配置方式
     * Note: World generation in 1.20.1 has changed to JSON configuration
     *
     * 矿石生成配置应移至：
     * Ore generation configuration should be moved to:
     * - data/kuvalich/worldgen/placed_feature/
     * - data/kuvalich/worldgen/configured_feature/
     *
     * 示例配置会在后续提供
     * Example configurations will be provided later
     */
}