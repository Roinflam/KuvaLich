package pers.roinflam.kuvalich.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.base.item.AbstractItemModule;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.network.message.ModuleDiscoveryPacket;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 模组发现记录事件处理器
 * Module Discovery Event Handler
 *
 * 四条发现路径，覆盖所有获取场景：
 *
 * 1. 玩家捡起模组时（EntityItemPickupEvent）
 * 2. 玩家登录时扫描背包补录 + 同步客户端
 * 3. 模组揭示时（ModuleLevelHelper.applyRevealLevel 手动调用）
 * 4. ⭐ 每5秒定期扫描背包（兜底）
 *
 * ⭐ 发现记录键格式从纯 type 升级为 type:rarityOrder，
 *    同一 type 不同品质的模组独立记录。
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class ModuleDiscoveryHandler {

    /** 定期扫描间隔（tick），100 = 5秒 / Periodic scan interval (ticks), 100 = 5 seconds */
    private static final int SCAN_INTERVAL = 100;

    /**
     * 路径1：玩家捡起物品时检测
     * Path 1: Detect on item pickup
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemEntity entityItem = event.getItem();
        if (entityItem == null) return;

        tryDiscoverSingle(player, entityItem.getItem());
    }

    /**
     * 路径2：玩家登录时扫描背包 + 同步
     * Path 2: Scan inventory on login + sync
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 延迟1tick确保就绪 / Delay 1 tick
        player.getServer().execute(() -> {
            scanInventory(player);
            ModuleDiscoveryPacket.syncToPlayer(player);
            LogUtil.debug("已向玩家 " + player.getName().getString() + " 同步模组发现记录");
        });
    }

    /**
     * ⭐ 路径4：定期背包扫描（兜底）
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % SCAN_INTERVAL != 0) return;

        boolean anyNew = scanInventory(player);
        if (anyNew) {
            ModuleDiscoveryPacket.syncToPlayer(player);
        }
    }

    /**
     * 玩家退出时清理客户端缓存
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide) {
            ModuleDiscoveryPacket.clearClientCache();
        }
    }

    // ==================== 内部方法 / Internal Methods ====================

    /**
     * 扫描玩家背包中所有模组并补录发现记录
     *
     * @param player 目标玩家
     * @return 是否有新发现
     */
    private static boolean scanInventory(ServerPlayer player) {
        boolean[] anyNew = {false};
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            Inventory inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;
                if (!(stack.getItem() instanceof AbstractItemModule) &&
                        !(stack.getItem() instanceof AbstractWarframeModule)) continue;
                if (AbstractModule.isRandom(stack)) continue;
                // ⭐ 使用组合键 / Use compound key
                String discoveryKey = buildDiscoveryKey(stack);
                if (discoveryKey.isEmpty()) continue;
                if (requiemCard.discoverModule(discoveryKey)) {
                    anyNew[0] = true;
                }
            }
        });
        return anyNew[0];
    }

    /**
     * 尝试记录单个物品的模组发现
     *
     * @param player 目标玩家
     * @param stack  物品栈
     */
    public static void tryDiscoverSingle(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (!(stack.getItem() instanceof AbstractItemModule) &&
                !(stack.getItem() instanceof AbstractWarframeModule)) return;
        if (AbstractModule.isRandom(stack)) return;
        // ⭐ 使用组合键 / Use compound key
        String discoveryKey = buildDiscoveryKey(stack);
        if (discoveryKey.isEmpty()) return;

        boolean isNew = ModuleDiscoveryPacket.discoverAndSync(player, discoveryKey);
        if (isNew) {
            LogUtil.debugEvent("模组发现", player.getName().getString(), "key: " + discoveryKey);
        }
    }

    // ==================== ⭐ 组合键工具方法 / Compound Key Utilities ====================

    /**
     * 根据物品栈构建发现记录组合键
     * Build discovery compound key from ItemStack
     *
     * 格式：type:rarityOrder（如 "fury:1"、"fury:3"）
     * Format: type:rarityOrder (e.g. "fury:1", "fury:3")
     *
     * @param stack 模组物品栈
     * @return 组合键，构建失败返回空字符串
     */
    public static String buildDiscoveryKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        String type = AbstractModule.getType(stack);
        if (type == null || type.isEmpty()) return "";
        int rarity = getRarityOrder(stack);
        if (rarity < 0) return type; // 未知品质兜底：使用纯type / Unknown rarity fallback
        return type + ":" + rarity;
    }

    /**
     * 根据 type 和 rarityOrder 构建发现记录组合键
     * Build discovery compound key from type and rarityOrder
     *
     * @param type        模组type标识
     * @param rarityOrder 品质序号（0~3）
     * @return 组合键
     */
    public static String buildDiscoveryKey(String type, int rarityOrder) {
        if (type == null || type.isEmpty()) return "";
        return type + ":" + rarityOrder;
    }

    /**
     * 根据物品类型判断品质序号
     * Determine rarity order from item class
     *
     * @param stack 模组物品栈
     * @return 品质序号（0=铜 1=银 2=金 3=Prime/裂罅），未知返回-1
     */
    public static int getRarityOrder(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        Item item = stack.getItem();
        // 武器模组 / Weapon modules
        if (item instanceof ItemRivenModule) return 3;
        if (item instanceof ItemPrimeModule) return 3;
        if (item instanceof ItemRareModule) return 2;
        if (item instanceof ItemUncommonModule) return 1;
        if (item instanceof ItemCommonModule) return 0;
        // 战甲模组 / Warframe modules
        if (item instanceof WarframeRivenModule) return 3;
        if (item instanceof WarframePrimeModule) return 3;
        if (item instanceof WarframeRareModule) return 2;
        if (item instanceof WarframeUncommonModule) return 1;
        if (item instanceof WarframeCommonModule) return 0;
        return -1;
    }
}
