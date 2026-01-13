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
 * 赤毒玄骸没收物品事件处理器（1.20.1版本，业务逻辑100%不变）
 * Confiscation Handler (1.20.1 version, business logic 100% unchanged)
 *
 * 当玩家解密了任意谜语后，捡起物品时有概率被没收
 * When player unlocked any riddle, items may be confiscated when picked up
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
     *
     * 注意：1.20.1使用EntityItemPickupEvent.Pre
     * Note: 1.20.1 uses EntityItemPickupEvent.Pre
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        ItemEntity entityItem = event.getItem();

        // 仅在服务端处理 / Server-side only
        if (player.level().isClientSide) {
            return;
        }

        // ✅ 修正：调用.get()获取配置值
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

            // 概率判定 / Chance check
            if (!RandomUtil.percentageChance(confiscationChance)) {
                return;
            }

            // 获取要没收的物品 / Get item to confiscate
            ItemStack itemStack = entityItem.getItem();
            if (itemStack.isEmpty()) {
                return;
            }

            // 没收物品 / Confiscate item
            requiemCard.addConfiscatedItem(itemStack);

            // 取消拾取事件，移除掉落物实体（1.20.1新API）
            // Cancel pickup, remove item entity (1.20.1 new API)
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