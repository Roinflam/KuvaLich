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
     * 玩家克隆事件（死亡重生 / 维度传送等）
     * Player clone event (death respawn / dimension travel etc.)
     *
     * 用于在玩家实体重建后保留Capability数据
     * Used to preserve Capability data after player entity recreation
     *
     * 触发场景 / Triggered scenarios:
     * 1. 玩家死亡重生 (isWasDeath=true)
     * 2. 从末地返回主世界 (isWasDeath=false)
     * 3. 其他导致玩家实体重建的情况 (isWasDeath=false)
     *
     * ⚠️ 所有场景都必须克隆数据！
     * ⚠️ ALL scenarios must clone data!
     *
     * 修复说明（2025-01-14）：
     * Fix notes:
     * 1. 添加reviveCaps()调用以恢复死亡玩家的Capability访问权限
     * 2. 添加异常处理,确保克隆失败不会导致游戏崩溃
     * 3. 添加invalidateCaps()调用,确保旧实体的Capability被正确清理
     *
     * 修复说明（2025-xx-xx）：
     * Fix notes:
     * 4. 移除isWasDeath()检查，修复从末地返回时数据丢失的bug
     *    Removed isWasDeath() check to fix data loss when returning from End
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // 基础检查：确保是服务端的玩家实体
        // Basic checks: ensure it's a server-side player entity
        Entity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide || !(entity instanceof Player)) {
            return;
        }

        // ✅ 修复：不再检查isWasDeath()，所有Clone事件都必须克隆数据
        // Fix: No longer check isWasDeath(), ALL Clone events must clone data
        // 原因：从末地返回时isWasDeath()=false，但玩家实体同样会被重建
        // Reason: isWasDeath()=false when returning from End, but player entity is still recreated

        Player player = (Player) entity;
        Player original = event.getOriginal();

        LogUtil.debug("玩家 " + player.getName().getString() + " 触发克隆事件"
                + (event.isWasDeath() ? "（死亡重生）" : "（维度传送等）")
                + "，开始克隆Capability数据");

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
                });
            });

            // 克隆WarframeModules数据（战甲模组8个槽位）
            // Clone WarframeModules data (8 warframe module slots)
            player.getCapability(WARFRAME_MODULES).ifPresent(newCap -> {
                original.getCapability(WARFRAME_MODULES).ifPresent(oldCap -> {
                    newCap.clone(oldCap);
                });
            });

        } catch (Exception e) {
            // 捕获克隆过程中的任何异常,避免导致游戏崩溃
            // Catch any exceptions during cloning to avoid game crash
            LogUtil.error("克隆玩家Capability数据时出错: " + player.getName().getString(), e);
        } finally {
            // ✅ 确保旧实体的Capability被正确失效
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