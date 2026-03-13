// 文件：KuvaLich.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/KuvaLich.java
package pers.roinflam.kuvalich;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.simple.SimpleChannel;

import pers.roinflam.kuvalich.base.item.AbstractModule;
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
import pers.roinflam.kuvalich.worldgen.KuvaLichBiomeModifiers;

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

    /** 网络通道（通过 NetworkRegistryHandler 获取）/ Network channel */
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

        // 注册附魔 / Register enchantments
        KuvaLichEnchantments.ENCHANTMENTS.register(modEventBus);

        // 注册实体类型 / Register entity types
        KuvaLichEntities.ENTITY_TYPES.register(modEventBus);

        // 注册 MenuType / Register menu types
        KuvaLichMenuTypes.MENUS.register(modEventBus);

        // 注册创造模式标签页 / Register creative tabs
        KuvaLichCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // 注册自定义 BiomeModifier Codec（矿石/实体生成）
        // Register custom BiomeModifier Codec
        KuvaLichBiomeModifiers.register(modEventBus);

        // 注册生命周期事件
        // Register lifecycle events
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // 注册配置文件
        // Register config files
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

        modEventBus.addListener(CapabilityRegistryHandler::registerCapabilities);

        LogUtil.info("赤毒玄骸模组构造函数执行完成");
    }

    /**
     * 通用设置阶段（客户端和服务端均执行）
     * Common setup phase (executed on both sides)
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        LogUtil.info("赤毒玄骸模组开始通用设置...");

        event.enqueueWork(() -> {
            try {
                // 注册网络处理器
                // Register network handler
                NetworkRegistryHandler.register();
                network = NetworkRegistryHandler.getChannel();
                LogUtil.debug("网络系统初始化成功");

                // 初始化自定义模组系统
                // Initialize custom module system
                CustomModuleManager.getInstance().initialize();

                // ================================================================
                // TACZ 兼容层注册
                // TACZ compatibility layer registration
                //
                // 仅在 TACZ 已加载时注册兼容事件处理器，避免类加载异常。
                // Only register compat handler when TACZ is loaded to prevent
                // ClassNotFoundException at class loading time.
                //
                // 注意：Mixin（MixinGunShootInterval）通过独立的
                // kuvalich.tacz.mixins.json 配置，defaultRequire=0，
                // TACZ 不存在时会静默跳过，不会报错。
                // Note: The Mixin (MixinGunShootInterval) uses a separate
                // kuvalich.tacz.mixins.json with defaultRequire=0, so it
                // silently skips when TACZ is absent.
                // ================================================================
                if (ModList.get().isLoaded("tacz")) {
                    // 实例化兼容处理器并手动注册到 Forge 事件总线
                    // Instantiate compat handler and manually register to Forge event bus
                    MinecraftForge.EVENT_BUS.register(
                            new pers.roinflam.kuvalich.compat.tacz.TaczCompatEventHandler()
                    );
                    LogUtil.info("检测到 TACZ，Warframe 模组兼容层已启用");
                    LogUtil.info("  - 射速修正：MixinGunShootInterval（Mixin 注入）");
                    LogUtil.info("  - 正值多重射击：GunCachePropertyEvent（bullet_amount）");
                    LogUtil.info("  - 负值多重射击：GunFireEvent（概率取消）");
                } else {
                    LogUtil.debug("未检测到 TACZ，跳过兼容层注册");
                }

                // 启动缓存清理定时器（每 5 分钟清理一次）
                // Start cache cleanup timer (every 5 minutes)
                Timer cacheCleanerTimer = new Timer("KuvaLich-Cache-Cleaner", true);
                cacheCleanerTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            AbstractModule.cleanExpiredCache();
                            LogUtil.debug("缓存清理完成");
                        } catch (Exception e) {
                            LogUtil.error("缓存清理失败", e);
                        }
                    }
                }, 300000, 300000);

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
}