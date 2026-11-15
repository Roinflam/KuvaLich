package pers.roinflam.kuvalich.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.PlayerFeedback;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 把收件箱里的东西搬进背包。只往背包里放、放不下就留在箱里，绝不扔地上。
 *
 * <p>不用 {@code Inventory.add}（创造模式下放不下的那部分会被抹掉），也不用 {@code placeItemBackInInventory}
 * （放不下扔脚下）：自己按格放，每放一格就把箱里那一条扣掉写回去，中途出什么事两边都对得上。</p>
 */
public final class Inbox {

    private static final int FULL_NOTICE_TICKS = 1200;

    private static final Map<UUID, Integer> LOGIN_AT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_FULL_NOTICE = new HashMap<>();
    /** 登录后还没到第一次自动投递的人。登录那一刻不提示：等那一次投完、还剩东西才说 */
    private static final Set<UUID> LOGIN_NOTICE = new HashSet<>();
    /** 上一次一件都没放进去时背包的样子。背包和收件箱都没动过，巡检就不再整箱解析 */
    private static final Map<UUID, Stuck> STUCK = new HashMap<>();
    /** 连着跳过这么多次巡检就照常试一次，兜住指纹没看出来的变化（比如格子里的物品原地改了 NBT） */
    private static final int MAX_SKIPS = 30;
    private static int scans;

    private Inbox() {
    }

    static void onLogin(ServerPlayer player) {
        LOGIN_AT.put(player.getUUID(), player.server.getTickCount());
        LOGIN_NOTICE.add(player.getUUID());
        STUCK.remove(player.getUUID());
    }

    static void onLogout(ServerPlayer player) {
        LOGIN_AT.remove(player.getUUID());
        LAST_FULL_NOTICE.remove(player.getUUID());
        LOGIN_NOTICE.remove(player.getUUID());
        STUCK.remove(player.getUUID());
    }

    /** 收件箱进了新东西：上次放不下的结论作废 */
    static void inboxGrew(Player player) {
        STUCK.remove(player.getUUID());
    }

    /** 测试用：真正解析收件箱的次数 */
    public static int scansForTest() {
        return scans;
    }

    /** 测试用：当作已经登录很久 */
    public static void skipLoginDelay(ServerPlayer player) {
        LOGIN_AT.remove(player.getUUID());
    }

    static void clearAll() {
        LOGIN_AT.clear();
        LAST_FULL_NOTICE.clear();
        LOGIN_NOTICE.clear();
        STUCK.clear();
    }

    /** 现在能不能往这个人背包里放：活着、不是旁观、没开着别的容器界面 */
    public static boolean canReceive(ServerPlayer player) {
        // 开着箱子之类的界面时先不放：有的界面关闭时会把自己持有的副本写回背包
        return !player.hasDisconnected() && !player.isRemoved() && player.isAlive() && !player.isSpectator()
                && player.containerMenu == player.inventoryMenu;
    }

    public static boolean inLoginDelay(ServerPlayer player) {
        Integer at = LOGIN_AT.get(player.getUUID());
        // 跨服同步背包的插件会在登录后整份覆盖背包，刚投进去的会被盖掉
        return at != null && player.server.getTickCount() - at < ModConfig.KUVA_LICH.inboxLoginDelayTicks.get();
    }

    /**
     * 巡检那一下。登录后第一次投递时箱里有东西又放不进（死着、开着界面）就提示一句；
     * 放进去一部分、背包满了的，由 {@link #deliver} 那句「背包已满」提示，不另说
     *
     * @return 发了登录那句提示
     */
    public static boolean patrol(ServerPlayer player) {
        if (inLoginDelay(player)) {
            return false;
        }
        boolean first = LOGIN_NOTICE.remove(player.getUUID());
        if (!PlayerLedger.hasInbox(player)) {
            return false;
        }
        if (first && !canReceive(player)) {
            PlayerFeedback.chat(player, KuvaPalette.INFO, "message.kuvalich.inbox.login", PlayerLedger.inboxCount(player));
            return true;
        }
        deliver(player, true, true);
        return false;
    }

    /**
     * 试投一次。登录后的等待期内什么都不做，命令领取也一样。
     *
     * @param notify 放不下时提示一句（同一人 60 秒最多一次）
     * @return 这次放进背包的件数
     */
    public static int deliver(ServerPlayer player, boolean notify) {
        return deliver(player, notify, false);
    }

    /** @param maySkip 上次一件没放进去、之后背包和收件箱都没变，就不再解析 */
    private static int deliver(ServerPlayer player, boolean notify, boolean maySkip) {
        if (!PlayerLedger.hasInbox(player) || !canReceive(player) || inLoginDelay(player)) {
            return 0;
        }
        Inventory inv = player.getInventory();
        Stuck stuck = STUCK.get(player.getUUID());
        if (maySkip && stuck != null && stuck.skipped < MAX_SKIPS && stuck.sameAs(inv)) {
            stuck.skipped++;
            if (notify) {
                noticeFull(player, stuck.left);
            }
            return 0;
        }
        scans++;
        ListTag list = PlayerLedger.inboxTag(player);
        int moved = 0;
        int remaining = 0;
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
                // 复制和序列化会跑到别的模组的物品代码，都在动背包之前做完；
                // 从放进背包到写回收件箱之间不能再有会抛的调用，不然背包多了、箱里没少，下次再发一份
                int left = stack.getCount() - n;
                ItemStack rest = left > 0 ? stack.copyWithCount(left) : ItemStack.EMPTY;
                CompoundTag restTag = left > 0 ? rest.save(new CompoundTag()) : null;
                ItemStack piece = there.isEmpty() ? stack.copyWithCount(n) : null;

                if (piece != null) {
                    piece.setPopTime(5);
                    inv.setItem(slot, piece);
                } else {
                    there.grow(n);
                }
                moved += n;
                if (restTag == null) {
                    list.remove(i);
                    i--;
                } else {
                    list.set(i, restTag);
                }
                PlayerLedger.putInboxTag(player, list);
                stack = rest;
            }
            remaining += stack.getCount();
        }
        if (moved > 0) {
            inv.setChanged();
            STUCK.remove(player.getUUID());
        } else {
            STUCK.put(player.getUUID(), new Stuck(inv, remaining));
        }
        if (notify) {
            noticeFull(player, remaining);
        }
        return moved;
    }

    private static void noticeFull(ServerPlayer player, int left) {
        if (left <= 0) {
            return;
        }
        int now = player.server.getTickCount();
        Integer last = LAST_FULL_NOTICE.get(player.getUUID());
        if (last == null || now - last >= FULL_NOTICE_TICKS) {
            LAST_FULL_NOTICE.put(player.getUUID(), now);
            PlayerFeedback.chat(player, KuvaPalette.WARN, "message.kuvalich.inbox.full", left);
        }
    }

    /**
     * 背包的指纹：主背包 36 格加副手（投递只往这些格子放）每格的物品和数量，再加 {@code getTimesChanged()}。
     * 光看计数不够：吃东西、Q 扔、放方块这些在服务端直接改堆叠，不走 {@code setChanged}
     */
    private static final class Stuck {
        final Inventory inv;
        final int changes;
        final Item[] items;
        final int[] counts;
        final int left;
        int skipped;

        Stuck(Inventory inv, int left) {
            this.inv = inv;
            this.changes = inv.getTimesChanged();
            this.left = left;
            int n = inv.items.size() + inv.offhand.size();
            items = new Item[n];
            counts = new int[n];
            for (int i = 0; i < n; i++) {
                ItemStack s = slot(inv, i);
                items[i] = s.isEmpty() ? null : s.getItem();
                counts[i] = s.isEmpty() ? 0 : s.getCount();
            }
        }

        boolean sameAs(Inventory now) {
            // 重生后背包是新对象
            if (now != inv || now.getTimesChanged() != changes || now.items.size() + now.offhand.size() != items.length) {
                return false;
            }
            for (int i = 0; i < items.length; i++) {
                ItemStack s = slot(now, i);
                if (s.isEmpty() ? items[i] != null : s.getItem() != items[i] || s.getCount() != counts[i]) {
                    return false;
                }
            }
            return true;
        }

        private static ItemStack slot(Inventory inv, int i) {
            return i < inv.items.size() ? inv.items.get(i) : inv.offhand.get(i - inv.items.size());
        }
    }
}
