package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
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
