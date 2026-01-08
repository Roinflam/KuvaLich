package pers.roinflam.kuvalich.event;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

/**
 * 赤毒玄骸没收物品事件处理器
 *
 * 当玩家解密了任意谜语后，捡起物品时有概率被没收
 * 被没收的物品会在解密成功后返还
 */
@Mod.EventBusSubscriber
public class ConfiscationHandler {

    // 没收时的嘲讽语录key
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
     * 监听玩家捡起物品事件
     *
     * @param event 物品拾取事件
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        EntityItem entityItem = event.getItem();

        // 仅在服务端处理
        if (player.world.isRemote) {
            return;
        }

        // 检查配置是否启用没收机制
        double confiscationChance = ModConfig.KUVA_LICH.confiscationChance;
        if (confiscationChance <= 0) {
            return;
        }

        // 获取玩家的RequiemCard能力
        RequiemCard requiemCard = player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD, null);
        if (requiemCard == null) {
            return;
        }

        // 检查是否有任意谜语被解开
        if (!requiemCard.hasAnyRiddleUnlocked()) {
            return;
        }

        // 概率判定
        if (!RandomUtil.percentageChance(confiscationChance)) {
            return;
        }

        // 获取要没收的物品
        ItemStack itemStack = entityItem.getItem();
        if (itemStack.isEmpty()) {
            return;
        }

        // 没收物品
        requiemCard.addConfiscatedItem(itemStack);

        // 取消拾取事件，移除掉落物实体
        event.setCanceled(true);
        entityItem.setDead();

        // 发送嘲讽消息
        sendConfiscationMessage(player);

        // 发送被没收物品的提示消息
        sendStolenItemMessage(player, itemStack);
    }

    /**
     * 发送没收时的嘲讽消息
     *
     * @param player 玩家
     */
    private static void sendConfiscationMessage(EntityPlayer player) {
        // 随机选择一条语录
        String messageKey = CONFISCATION_MESSAGES[RandomUtil.getInt(0, CONFISCATION_MESSAGES.length - 1)];
        TextComponentTranslation message = new TextComponentTranslation(messageKey);
        message.getStyle().setColor(TextFormatting.DARK_RED);
        player.sendMessage(message);
    }

    /**
     * 发送被没收物品的提示消息
     *
     * @param player    玩家
     * @param itemStack 被没收的物品
     */
    private static void sendStolenItemMessage(EntityPlayer player, ItemStack itemStack) {
        // 获取物品显示名称（优先自定义名称，否则使用翻译名）
        String itemName = itemStack.getDisplayName();
        int count = itemStack.getCount();

        // 构建物品名称字符串（数量大于1时添加数量后缀）
        String itemDisplay;
        if (count > 1) {
            itemDisplay = itemName + "x" + count;
        } else {
            itemDisplay = itemName;
        }

        // 发送提示消息
        TextComponentTranslation message = new TextComponentTranslation("message.kuvalich.confiscation.stolen", itemDisplay);
        message.getStyle().setColor(TextFormatting.RED);
        player.sendMessage(message);
    }

}