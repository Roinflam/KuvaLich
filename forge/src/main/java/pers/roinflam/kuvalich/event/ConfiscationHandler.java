package pers.roinflam.kuvalich.event;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

/**
 * 赤毒玄骸没收物品事件处理器（1.20.1版本）
 * Confiscation Handler (1.20.1 version)
 *
 * 当玩家解密了任意谜语后，捡起物品时有概率被没收
 * When player unlocked any riddle, items may be confiscated when picked up
 *
 * 没收上限由配置项 maxConfiscatedItems 控制，达到上限后不再没收
 * Confiscation limit controlled by maxConfiscatedItems config, stops when limit reached
 */
@Mod.EventBusSubscriber
public class ConfiscationHandler {

    // 没收时的嘲讽语录key（业务逻辑100%不变）
    // Confiscation message keys (business logic 100% unchanged)
    private static final String[] CONFISCATION_MESSAGES = {
            "message.kuvalich.confiscation.wallet",
            "message.kuvalich.confiscation.tax",
            "message.kuvalich.confiscation.something",
            "message.kuvalich.confiscation.closer",
            "message.kuvalich.confiscation.yoink",
            "message.kuvalich.confiscation.sector",
            "message.kuvalich.confiscation.tithe",
            "message.kuvalich.confiscation.coin",
            "message.kuvalich.confiscation.accepted"
    };

    /**
     * 监听玩家捡起物品事件（1.20.1事件API）
     * Listen to item pickup event (1.20.1 event API)
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        ItemEntity entityItem = event.getItem();

        // 仅在服务端处理 / Server-side only
        if (player.level().isClientSide) {
            return;
        }

        // 获取没收概率配置
        // Get confiscation chance config
        double confiscationChance = ModConfig.KUVA_LICH.confiscationChance.get();
        if (confiscationChance <= 0) {
            return;
        }

        // 获取玩家的RequiemCard能力 / Get player's RequiemCard capability
        player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
            // 检查是否有任意谜语被解开 / Check if any riddle unlocked
            if (!requiemCard.hasAnyRiddleUnlocked()) {
                return;
            }

            // ✅ 新增：检查是否已达到没收上限
            // Check if confiscation limit has been reached
            if (!requiemCard.canConfiscateMore()) {
                return;
            }

            // 概率判定 / Chance check
            if (!RandomUtil.percentageChance(confiscationChance)) {
                return;
            }

            // 获取要没收的物品 / Get item to confiscate
            ItemStack itemStack = entityItem.getItem();
            if (itemStack.isEmpty()) {
                return;
            }

            // 没收物品（addConfiscatedItem内部也会做上限检查，双重保险）
            // Confiscate item (addConfiscatedItem also checks limit internally, double safety)
            if (!requiemCard.addConfiscatedItem(itemStack)) {
                // 没收失败（已达上限），不取消拾取
                // Confiscation failed (limit reached), don't cancel pickup
                return;
            }

            // 取消拾取事件，移除掉落物实体
            // Cancel pickup, remove item entity
            event.setCanceled(true);
            entityItem.discard();

            // 发送嘲讽消息 / Send confiscation message
            sendConfiscationMessage(player);
        });
    }

    /**
     * 发送没收时的嘲讽消息（业务逻辑100%不变）
     * Send confiscation message (business logic 100% unchanged)
     */
    private static void sendConfiscationMessage(Player player) {
        String messageKey = CONFISCATION_MESSAGES[RandomUtil.getInt(0, CONFISCATION_MESSAGES.length - 1)];
        player.sendSystemMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_RED));
    }
}