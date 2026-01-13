package pers.roinflam.kuvalich.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pers.roinflam.kuvalich.client.gui.screens.inventory.*;
import pers.roinflam.kuvalich.client.renderer.RenderKuvaMaster;
import pers.roinflam.kuvalich.client.renderer.RenderKuvaSlave;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichMenuTypes;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 客户端设置（1.20.1版本，包含实体渲染器注册）
 * Client Setup (1.20.1 version, including entity renderer registration)
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    /**
     * 客户端设置事件（1.20.1新API）
     * Client setup event (1.20.1 new API)
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            LogUtil.info("开始注册客户端内容...");

            // 注册所有Screen / Register all screens
            registerScreens();

            // 注册所有实体渲染器 / Register all entity renderers
            registerEntityRenderers();

            LogUtil.info("客户端内容注册完成");
        });
    }

    /**
     * 注册Screen（GUI界面）
     * Register screens (GUI interfaces)
     */
    private static void registerScreens() {
        MenuScreens.register(KuvaLichMenuTypes.REQUIEM_GATE.get(), ScreenRequiemGate::new);
        MenuScreens.register(KuvaLichMenuTypes.REQUIEM_RECAST.get(), ScreenRequiemRecast::new);
        MenuScreens.register(KuvaLichMenuTypes.REQUIEM_EVOLVE.get(), ScreenRequiemEvolve::new);
        MenuScreens.register(KuvaLichMenuTypes.REQUIEM_WEAPON_TABLE.get(), ScreenRequiemWeaponTable::new);
        MenuScreens.register(KuvaLichMenuTypes.REQUIEM_WARFRAME_TABLE.get(), ScreenRequiemWarframeTable::new);

        LogUtil.info("Screen注册完成");
    }

    /**
     * 注册实体渲染器（1.20.1新方法）
     * Register entity renderers (1.20.1 new method)
     */
    private static void registerEntityRenderers() {
        // 注册赤毒奴仆渲染器 / Register Kuva Slave renderer
        EntityRenderers.register(KuvaLichEntities.KUVA_SLAVE.get(), RenderKuvaSlave::new);

        // 注册赤毒玄骸渲染器 / Register Kuva Master renderer
        EntityRenderers.register(KuvaLichEntities.KUVA_MASTER.get(), RenderKuvaMaster::new);

        LogUtil.info("实体渲染器注册完成");
    }
}