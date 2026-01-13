// 文件：CapabilityRegistryHandler.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/capability/CapabilityRegistryHandler.java
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
 * - registerCapabilities: 在MOD事件总线上,通过KuvaLich主类手动注册
 * - attachCapabilities/onPlayerClone: 在Forge事件总线上,通过@Mod.EventBusSubscriber自动注册
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
        LogUtil.info("已注册 RequiemCard Capability");

        // 注册WarframeModules
        event.register(WarframeModules.class);
        LogUtil.info("已注册 WarframeModules Capability");

        LogUtil.info("Capabilities注册完成");
    }

    /**
     * 玩家克隆事件（死亡重生）
     * Player clone event (death respawn)
     *
     * 用于在玩家死亡后保留Capability数据
     * Used to preserve Capability data after player death
     *
     * 修复说明（2025-01-14）：
     * Fix notes:
     * 1. 添加reviveCaps()调用以恢复死亡玩家的Capability访问权限
     * 2. 添加isWasDeath()检查,只在真正死亡时克隆数据
     * 3. 添加异常处理,确保克隆失败不会导致游戏崩溃
     * 4. 添加invalidateCaps()调用,确保旧实体的Capability被正确清理
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // 基础检查：确保是服务端的玩家实体
        // Basic checks: ensure it's a server-side player entity
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide || !(entity instanceof Player)) {
            return;
        }

        // ✅ 新增：只在死亡时克隆,避免从末地返回等情况误触发
        // Only clone on death, avoid triggering on end portal return etc.
        if (!event.isWasDeath()) {
            LogUtil.debug("玩家 " + entity.getName().getString() + " 触发克隆事件但非死亡,跳过Capability克隆");
            return;
        }

        Player player = (Player) entity;
        Player original = event.getOriginal();

        // ✅ 关键修复：恢复旧实体的Capability访问权限
        // Critical fix: Revive old entity's Capability access
        // 说明：玩家死亡后,Minecraft会自动调用invalidateCaps()使所有Capability失效
        // Note: After player death, Minecraft automatically calls invalidateCaps() to invalidate all Capabilities
        // 必须先调用reviveCaps()临时恢复访问权限,才能读取旧数据
        // Must call reviveCaps() first to temporarily restore access before reading old data
        original.reviveCaps();

        try {
            // 克隆RequiemCard数据（安魂卡片、谜语进度、没收物品等）
            // Clone RequiemCard data (requiem cards, riddle progress, confiscated items, etc.)
            player.getCapability(REQUIEM_CARD).ifPresent(newCap -> {
                original.getCapability(REQUIEM_CARD).ifPresent(oldCap -> {
                    newCap.clone(oldCap);
                    LogUtil.info("✓ 已克隆玩家 " + player.getName().getString() + " 的RequiemCard数据");
                });
            });

            // 克隆WarframeModules数据（战甲模组8个槽位）
            // Clone WarframeModules data (8 warframe module slots)
            player.getCapability(WARFRAME_MODULES).ifPresent(newCap -> {
                original.getCapability(WARFRAME_MODULES).ifPresent(oldCap -> {
                    newCap.clone(oldCap);
                    LogUtil.info("✓ 已克隆玩家 " + player.getName().getString() + " 的WarframeModules数据");
                });
            });

        } catch (Exception e) {
            // 捕获克隆过程中的任何异常,避免导致游戏崩溃
            // Catch any exceptions during cloning to avoid game crash
            LogUtil.error("克隆玩家Capability数据时出错: " + player.getName().getString(), e);
        } finally {
            // ✅ 新增：确保旧实体的Capability被正确失效
            // Ensure old entity's Capabilities are properly invalidated
            // 说明：克隆完成后必须再次失效,确保资源正确释放,防止内存泄漏
            // Note: Must invalidate again after cloning to ensure proper resource cleanup and prevent memory leaks
            original.invalidateCaps();
        }
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