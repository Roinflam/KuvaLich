package pers.roinflam.kuvalich.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.PlayerFeedback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 把收件箱里的东西搬进背包。只往背包里放、放不下就留在箱里，绝不扔地上。
 *
 * <p>不用 {@code Inventory.add}（创造模式下放不下的那部分会被抹掉），也不用 {@code placeItemBackInInventory}
 * （放不下扔脚下）：自己按格放，每放一格就把箱里那一条扣掉写回去，中途出什么事两边都对得上。</p>
 */
public final class Inbox {

    /** 登录后多久才第一次投（tick）：跨服同步背包的插件会在登录后整份覆盖背包，刚投进去的会被盖掉 */
    static final int LOGIN_DELAY_TICKS = 200;
    private static final int FULL_NOTICE_TICKS = 1200;

    private static final Map<UUID, Integer> LOGIN_AT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_FULL_NOTICE = new HashMap<>();

    private Inbox() {
    }

    static void onLogin(ServerPlayer player) {
        LOGIN_AT.put(player.getUUID(), player.server.getTickCount());
    }

    static void onLogout(ServerPlayer player) {
        LOGIN_AT.remove(player.getUUID());
        LAST_FULL_NOTICE.remove(player.getUUID());
    }

    /** 测试用：当作已经登录很久 */
    public static void skipLoginDelay(ServerPlayer player) {
        LOGIN_AT.remove(player.getUUID());
    }

    static void clearAll() {
        LOGIN_AT.clear();
        LAST_FULL_NOTICE.clear();
    }

    /** 现在能不能往这个人背包里放：活着、不是旁观、没开着别的容器界面 */
    public static boolean canReceive(ServerPlayer player) {
        // 开着箱子之类的界面时先不放：有的界面关闭时会把自己持有的副本写回背包
        return !player.hasDisconnected() && !player.isRemoved() && player.isAlive() && !player.isSpectator()
                && player.containerMenu == player.inventoryMenu;
    }

    public static boolean inLoginDelay(ServerPlayer player) {
        Integer at = LOGIN_AT.get(player.getUUID());
        return at != null && player.server.getTickCount() - at < LOGIN_DELAY_TICKS;
    }

    /**
     * 试投一次。
     *
     * @param force  true = 不管登录后的等待（玩家自己用命令要的）
     * @param notify 放不下时提示一句（同一人 60 秒最多一次）
     * @return 这次放进背包的件数
     */
    public static int deliver(ServerPlayer player, boolean force, boolean notify) {
        if (!PlayerLedger.hasInbox(player) || !canReceive(player) || (!force && inLoginDelay(player))) {
            return 0;
        }
        Inventory inv = player.getInventory();
        ListTag list = PlayerLedger.inboxTag(player);
        int moved = 0;
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (stack.isEmpty()) {
                // 读不出来的原样留着，等所属模组装回来
                continue;
            }
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
                int n = Math.min(room, stack.getCount());
                if (n <= 0) {
                    break;
                }
                if (there.isEmpty()) {
                    ItemStack piece = stack.copyWithCount(n);
                    piece.setPopTime(5);
                    inv.setItem(slot, piece);
                } else {
                    there.grow(n);
                }
                stack.shrink(n);
                moved += n;
                if (stack.isEmpty()) {
                    list.remove(i);
                    i--;
                } else {
                    list.set(i, stack.save(new CompoundTag()));
                }
                PlayerLedger.putInboxTag(player, list);
            }
        }
        if (moved > 0) {
            inv.setChanged();
        }
        int left = notify ? PlayerLedger.inboxCount(player) : 0;
        if (left > 0) {
            int now = player.server.getTickCount();
            Integer last = LAST_FULL_NOTICE.get(player.getUUID());
            if (last == null || now - last >= FULL_NOTICE_TICKS) {
                LAST_FULL_NOTICE.put(player.getUUID(), now);
                PlayerFeedback.chat(player, KuvaPalette.WARN, "message.kuvalich.inbox.full", left);
            }
        }
        return moved;
    }
}
