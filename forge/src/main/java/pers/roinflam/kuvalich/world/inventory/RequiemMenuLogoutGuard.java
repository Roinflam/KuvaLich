package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.utils.InventoryUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 下线时先关掉安魂系列的界面，再让原版存档
 *
 * <p>原版 {@code PlayerList#remove} 先 {@code save(player)}，之后移除实体时才 {@code doCloseContainer}。
 * 这几个界面关闭时把槽位里的东西交还玩家（军械库、铸、融退回背包，灭骸之扉写进安魂卡数据），
 * 落在存档之后就白写了：开着界面下线或退出单人世界，放在里面的武器、卡就没了。
 * {@code PlayerLoggedOutEvent} 在 remove 一开头、存档之前发，这里先关一次，关闭时交还的东西就进了存档；
 * 之后原版看到界面已经关了，不会再关第二次。</p>
 *
 * <p>手上拿着（光标上）的东西原版下线时扔在地上（它那时已经认为玩家断线了），这里先放回背包，放不下才扔。</p>
 *
 * <p>开着战甲军械库时死亡、跨维度同理：原版先把玩家标成已移除再关界面，关界面时看到玩家不在世界里就把光标上的扔出去，
 * 死在岩浆、虚空里幻纹就没了。这两条路在事件里先放回背包再关界面；死亡时放回背包以后跟着背包走
 * （死亡不掉落、墓碑类模组照常接手）。这时能力数据可能已经失效，关界面那次写回不一定生效，
 * 幻纹格不丢东西靠的是每次操作当场写回。</p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class RequiemMenuLogoutGuard {

    private RequiemMenuLogoutGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void closeRequiemMenuBeforeSave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isRequiemMenu(player.containerMenu)) {
            return;
        }
        returnCarriedAndClose(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void closeArmoryBeforeDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player
                && player.containerMenu instanceof RequiemWarframeTableMenu) {
            // 这时血量已经是 0，giveOrDrop 会当死人直接扔地上；背包还没掉落，直接放进去
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                player.getInventory().placeItemBackInInventory(carried);
            }
            player.closeContainer();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void closeArmoryBeforeTravel(EntityTravelToDimensionEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player
                && player.containerMenu instanceof RequiemWarframeTableMenu) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                InventoryUtil.giveOrDrop(player, carried);
            }
            player.closeContainer();
        }
    }

    private static void returnCarriedAndClose(ServerPlayer player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            InventoryUtil.giveOrDrop(player, carried);
        }
        player.doCloseContainer();
    }

    static boolean isRequiemMenu(AbstractContainerMenu menu) {
        return menu instanceof RequiemGateMenu
                || menu instanceof RequiemWeaponTableMenu
                || menu instanceof RequiemWarframeTableMenu
                || menu instanceof RequiemRecastMenu
                || menu instanceof RequiemEvolveMenu;
    }
}
