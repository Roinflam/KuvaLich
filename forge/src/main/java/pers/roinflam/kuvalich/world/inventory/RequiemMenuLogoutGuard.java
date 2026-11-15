package pers.roinflam.kuvalich.world.inventory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.nemesis.FinaleSettlement;
import pers.roinflam.kuvalich.nemesis.PlayerLedger;
import pers.roinflam.kuvalich.utils.InventoryUtil;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

import java.util.List;

/**
 * 下线时先关掉安魂系列的界面，再让原版存档
 *
 * <p>原版 {@code PlayerList#remove} 先 {@code save(player)}，之后移除实体时才 {@code doCloseContainer}。
 * 这几个界面关闭时把槽位里的东西交还玩家（军械库、铸、融退回背包，灭骸之扉写进安魂卡数据），
 * 落在存档之后就白写了：开着界面下线或退出单人世界，放在里面的武器、卡就没了。
 * {@code PlayerLoggedOutEvent} 在 remove 一开头、存档之前发，这里先关一次，关闭时交还的东西就进了存档；
 * 之后原版看到界面已经关了，不会再关第二次。</p>
 *
 * <p>手上拿着（光标上）的东西原版下线时扔在地上（它那时已经认为玩家断线了），这里先放回背包，
 * 放不下的进待领取、当场存盘，重登后再往背包里放。关界面时槽位里退回的东西（武器、模组卡等）同样处理。
 * 死亡、跨维度、平常关界面时人还在场，放不下照旧扔在脚下。</p>
 *
 * <p>下线这一步还要从 {@code PlayerList.remove} 一开头调一次（{@code MixinPlayerListRemove}）：
 * Tenet 在发登出事件之前就把开着的界面的光标放回背包、放不下的扔在脚下，等到事件里光标已经空了。</p>
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

    /** {@code PlayerList.remove} 一开头调（{@code MixinPlayerListRemove}），见类注释 */
    public static void beforeRemove(ServerPlayer player) {
        if (isRequiemMenu(player.containerMenu)) {
            returnCarriedAndClose(player);
        }
    }

    /**
     * 下线关界面期间的玩家。界面 removed() 里经 {@link InventoryUtil#giveOrDrop} 退回的槽位物品
     * 对这个人改走 {@link #stowOrInbox}，各界面自己决定退什么（预览格不退之类）的逻辑不用在这里重抄一遍。只在服务端主线程上用
     */
    @Nullable
    private static ServerPlayer closingOnLogout;
    private static boolean inboxedOnLogout;

    /** 正在下线关界面、东西已经按背包 + 待领取放好时返回 true，调用方不要再扔 */
    public static boolean stowIfClosingOnLogout(Player player, ItemStack stack) {
        if (closingOnLogout == null || player != closingOnLogout) {
            return false;
        }
        stowOrInbox(closingOnLogout, stack);
        return true;
    }

    private static void returnCarriedAndClose(ServerPlayer player) {
        boolean alive = player.isAlive();
        inboxedOnLogout = false;
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            if (alive) {
                stowOrInbox(player, carried);
            } else {
                InventoryUtil.giveOrDrop(player, carried);
            }
        }
        ServerPlayer outer = closingOnLogout;
        closingOnLogout = alive ? player : null;
        try {
            player.doCloseContainer();
        } finally {
            closingOnLogout = outer;
        }
        if (inboxedOnLogout) {
            inboxedOnLogout = false;
            FinaleSettlement.saveNow(player);
        }
    }

    /**
     * 先叠同种、再放空格，剩下的进待领取：人已经走了，扔在地上没人捡，过几分钟就消失。处理完 {@code stack} 是空的
     */
    private static void stowOrInbox(ServerPlayer player, ItemStack stack) {
        Inventory inv = player.getInventory();
        try {
            while (!stack.isEmpty()) {
                int slot = inv.getSlotWithRemainingSpace(stack);
                if (slot < 0) {
                    slot = inv.getFreeSlot();
                }
                if (slot < 0) {
                    break;
                }
                ItemStack there = inv.getItem(slot);
                int room = there.isEmpty() ? Math.min(stack.getMaxStackSize(), inv.getMaxStackSize())
                        : Math.min(there.getMaxStackSize(), inv.getMaxStackSize()) - there.getCount();
                if (room <= 0) {
                    break;
                }
                // 自己放，不走 Inventory.add：创造模式下它会把放不进的抹掉。放进去之后才从 stack 里扣
                int n = Math.min(room, stack.getCount());
                if (there.isEmpty()) {
                    inv.setItem(slot, stack.copyWithCount(n));
                } else {
                    there.grow(n);
                }
                stack.shrink(n);
            }
        } catch (RuntimeException e) {
            LogUtil.error("下线时把 " + player.getGameProfile().getName() + " 的 " + stack + " 放回背包出错，剩下的进待领取", e);
        }
        inv.setChanged();
        if (stack.isEmpty()) {
            return;
        }
        // 序列化会跑到物品自己的代码，可能抛；先做完再写待领取，写进去之后就不会再走扔地上那条，不会两边各一份
        ListTag tags;
        try {
            tags = PlayerLedger.writeItems(List.of(stack));
        } catch (RuntimeException e) {
            LogUtil.error("下线时 " + player.getGameProfile().getName() + " 的 " + stack + " 写不进待领取，改为扔在原地", e);
            player.drop(stack.copy(), false);
            stack.setCount(0);
            return;
        }
        PlayerLedger.appendToInbox(player, tags);
        stack.setCount(0);
        inboxedOnLogout = true;
    }

    static boolean isRequiemMenu(AbstractContainerMenu menu) {
        return menu instanceof RequiemGateMenu
                || menu instanceof RequiemWeaponTableMenu
                || menu instanceof RequiemWarframeTableMenu
                || menu instanceof RequiemRecastMenu
                || menu instanceof RequiemEvolveMenu;
    }
}
