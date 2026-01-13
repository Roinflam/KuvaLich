package pers.roinflam.kuvalich.capability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * Capability注册处理器（1.20.1版本）
 * Capability Registry Handler (1.20.1 version)
 *
 * 重大变更：
 * Major changes:
 * 1. 使用CapabilityToken替代@CapabilityInject
 * 2. 使用RegisterCapabilitiesEvent注册
 * 3. 使用LazyOptional管理Capability实例
 * 4. Provider内置于数据类中
 *
 * 事件总线说明：
 * Event bus notes:
 * - registerCapabilities: 在MOD事件总线上，通过KuvaLich主类手动注册
 * - attachCapabilities/onPlayerClone: 在Forge事件总线上，通过@Mod.EventBusSubscriber自动注册
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CapabilityRegistryHandler {

    /**
     * 安魂卡片Capability
     * Requiem Card Capability
     *
     * 1.20.1中使用CapabilityToken替代@CapabilityInject
     * Use CapabilityToken instead of @CapabilityInject in 1.20.1
     */
    public static final Capability<RequiemCard> REQUIEM_CARD =
            CapabilityManager.get(new CapabilityToken<>(){});

    /**
     * 战甲模组Capability
     * Warframe Modules Capability
     */
    public static final Capability<WarframeModules> WARFRAME_MODULES =
            CapabilityManager.get(new CapabilityToken<>(){});

    // 预创建ResourceLocation，避免重复创建
    // Pre-create ResourceLocations to avoid repeated creation
    private static final ResourceLocation REQUIEM_CARD_ID =
            new ResourceLocation(Reference.MOD_ID, "requiem_card");
    private static final ResourceLocation WARFRAME_MODULES_ID =
            new ResourceLocation(Reference.MOD_ID, "warframe_modules");

    /**
     * 注册Capability（1.20.1新API）
     * Register Capabilities (1.20.1 new API)
     *
     * 注意：此方法通过KuvaLich主类在MOD事件总线上手动注册
     * Note: This method is manually registered on MOD event bus via KuvaLich main class
     *
     * ⚠️ 不要添加@SubscribeEvent注解，因为已经手动注册了
     * ⚠️ Do NOT add @SubscribeEvent annotation as it's already manually registered
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        LogUtil.info("开始注册Capabilities...");

        // 注册RequiemCard
        event.register(RequiemCard.class);
        LogUtil.info("已注册 RequiemCard Capability");  // ✅ 改为 info

        // 注册WarframeModules
        event.register(WarframeModules.class);
        LogUtil.info("已注册 WarframeModules Capability");  // ✅ 改为 info

        LogUtil.info("Capabilities注册完成");
    }

    /**
     * 玩家克隆事件（死亡重生）
     * Player clone event (death respawn)
     *
     * 用于在玩家死亡后保留Capability数据
     * Used to preserve Capability data after player death
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide || !(entity instanceof Player)) {
            return;
        }

        Player player = (Player) entity;
        Player original = event.getOriginal();

        // 克隆RequiemCard数据
        // Clone RequiemCard data
        player.getCapability(REQUIEM_CARD).ifPresent(newCap -> {
            original.getCapability(REQUIEM_CARD).ifPresent(oldCap -> {
                newCap.clone(oldCap);
                LogUtil.debug("已克隆玩家 " + player.getName().getString() + " 的RequiemCard数据");
                // ✅ 这里可以保持 debug，因为游戏运行时配置已加载
            });
        });

        // 克隆WarframeModules数据
        // Clone WarframeModules data
        player.getCapability(WARFRAME_MODULES).ifPresent(newCap -> {
            original.getCapability(WARFRAME_MODULES).ifPresent(oldCap -> {
                newCap.clone(oldCap);
                LogUtil.debug("已克隆玩家 " + player.getName().getString() + " 的WarframeModules数据");
                // ✅ 这里可以保持 debug，因为游戏运行时配置已加载
            });
        });
    }

    /**
     * 附加Capability到玩家实体
     * Attach Capabilities to player entity
     *
     * 注意：此方法在Forge事件总线上自动注册
     * Note: This method is automatically registered on FORGE event bus
     */
    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player)) {
            return;
        }

        // 附加RequiemCard
        // Attach RequiemCard
        event.addCapability(REQUIEM_CARD_ID, new RequiemCard.Provider());

        // 附加WarframeModules
        // Attach WarframeModules
        event.addCapability(WARFRAME_MODULES_ID, new WarframeModules.Provider());
    }
}