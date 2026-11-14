package pers.roinflam.kuvalich.nemesis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 玩家身上的账、收件箱、盟友名册，放在 {@code PlayerPersisted/kuvalich} 下。
 *
 * <p>不放进安魂卡能力：回退旧 jar 时能力只写回自己认识的键，新字段连同里面的物品会被抹掉，
 * {@code PlayerPersisted} 旧版原样读写；重生、从末地回来也由 Forge 自己复制过去。</p>
 *
 * <p>每次都从玩家身上现取、不缓存：重生后旧玩家对象作废，拿着旧的去投递会出现一份投两次。</p>
 */
public final class PlayerLedger {

    public static final String ROOT = "kuvalich";
    private static final String FINALE = "finale";
    private static final String INBOX = "inbox";
    private static final String ALLIES = "allies";
    private static final String ACTIVE_ALLY = "activeAlly";
    private static final String ALLY_COOLDOWN_UNTIL = "allyCooldownUntil";

    private PlayerLedger() {
    }

    private static CompoundTag read(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT);
    }

    // getCompound 取不到时给的是一份没挂上去的新 tag，所以写完要一层层 put 回去
    private static void write(Player player, CompoundTag root) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(ROOT, root);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    // ==================== 账 ====================

    @Nullable
    public static Finale getFinale(Player player) {
        CompoundTag root = read(player);
        return root.contains(FINALE, Tag.TAG_COMPOUND) ? Finale.load(root.getCompound(FINALE)) : null;
    }

    public static boolean hasFinale(Player player) {
        return read(player).contains(FINALE, Tag.TAG_COMPOUND);
    }

    public static void setFinale(Player player, @Nullable Finale finale) {
        CompoundTag root = read(player);
        if (finale == null) {
            root.remove(FINALE);
        } else {
            root.put(FINALE, finale.save());
        }
        write(player, root);
    }

    /**
     * 选了感化：落定之前先在账上记一笔。落定出错留下的账，巡检和登录补落定都按这里记的选择来，
     * 不会把选了感化的按消灭补发。只写 NBT，代数对不上不记
     */
    public static void markFinaleConvert(Player player, int generation) {
        CompoundTag root = read(player);
        if (!root.contains(FINALE, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag f = root.getCompound(FINALE);
        if (f.getInt("gen") != generation) {
            return;
        }
        f.putBoolean("convert", true);
        root.put(FINALE, f);
        write(player, root);
    }

    public static boolean finaleMarkedConvert(Player player) {
        return read(player).getCompound(FINALE).getBoolean("convert");
    }

    /** 放进已经序列化好的账（{@link Finale#save} 的结果），只写 NBT，不会抛 */
    public static void putFinaleTag(Player player, CompoundTag finale) {
        CompoundTag root = read(player);
        root.put(FINALE, finale);
        write(player, root);
    }

    /**
     * 账里 drops / returns 中读不出物品的原始条目（副本），落定时原样接进收件箱；数量 ≤ 0 的本来就是空的，不要
     *
     * @param withDrops false = 只取 returns（感化不给掉落）
     */
    static ListTag unreadableFinaleItems(Player player, boolean withDrops) {
        CompoundTag f = read(player).getCompound(FINALE);
        ListTag out = new ListTag();
        for (String key : withDrops ? new String[]{"drops", "returns"} : new String[]{"returns"}) {
            ListTag list = f.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                if (tag.getByte("Count") > 0 && ItemStack.of(tag).isEmpty()) {
                    out.add(tag.copy());
                }
            }
        }
        return out;
    }

    // ==================== 收件箱 ====================

    public static List<ItemStack> getInbox(Player player) {
        return readItems(read(player).getList(INBOX, Tag.TAG_COMPOUND), player.getGameProfile().getName() + " 的收件箱");
    }

    public static void setInbox(Player player, Collection<ItemStack> items) {
        CompoundTag root = read(player);
        List<ItemStack> split = splitStacks(items);
        if (split.isEmpty()) {
            root.remove(INBOX);
        } else {
            root.put(INBOX, writeItems(split));
        }
        write(player, root);
    }

    // 原样接在后面，不把已有条目读出来再写回：读不出来的（比如所属模组暂时卸了）会在重写时丢掉
    public static void addToInbox(Player player, Collection<ItemStack> items) {
        ListTag add = writeItems(items);
        if (add.isEmpty()) {
            return;
        }
        CompoundTag root = read(player);
        ListTag inbox = root.getList(INBOX, Tag.TAG_COMPOUND).copy();
        inbox.addAll(add);
        root.put(INBOX, inbox);
        write(player, root);
    }

    public static boolean hasInbox(Player player) {
        return !read(player).getList(INBOX, Tag.TAG_COMPOUND).isEmpty();
    }

    /** 收件箱里还有几件（按数量加总，读不出来的条目不算） */
    public static int inboxCount(Player player) {
        int n = 0;
        ListTag list = read(player).getList(INBOX, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            n += stack.isEmpty() ? 0 : stack.getCount();
        }
        return n;
    }

    /** 收件箱原样的条目（副本），投递用 */
    static ListTag inboxTag(Player player) {
        return read(player).getList(INBOX, Tag.TAG_COMPOUND).copy();
    }

    static void putInboxTag(Player player, ListTag list) {
        CompoundTag root = read(player);
        if (list.isEmpty()) {
            root.remove(INBOX);
        } else {
            root.put(INBOX, list);
        }
        write(player, root);
    }

    /**
     * 清账：东西接进收件箱、账清掉，同一次写进去。物品先在外面序列化好，写的这一步不会半途抛出
     *
     * @param raw 账里读不出来的原始条目，原样接在后面
     */
    static void settleFinale(Player player, Collection<ItemStack> items, ListTag raw) {
        ListTag add = writeItems(items);
        CompoundTag root = read(player);
        ListTag inbox = root.getList(INBOX, Tag.TAG_COMPOUND).copy();
        inbox.addAll(add);
        inbox.addAll(raw);
        if (inbox.isEmpty()) {
            root.remove(INBOX);
        } else {
            root.put(INBOX, inbox);
        }
        root.remove(FINALE);
        write(player, root);
    }

    // ==================== 盟友名册（条目格式由盟友那一批定，这里只原样存取） ====================

    public static List<CompoundTag> getAllies(Player player) {
        ListTag list = read(player).getList(ALLIES, Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getCompound(i).copy());
        }
        return out;
    }

    public static void setAllies(Player player, List<CompoundTag> allies) {
        CompoundTag root = read(player);
        ListTag list = new ListTag();
        for (CompoundTag ally : allies) {
            list.add(ally.copy());
        }
        root.put(ALLIES, list);
        write(player, root);
    }

    /** 出战的那一条（代数），-1 = 没指定 */
    public static int getActiveAlly(Player player) {
        CompoundTag root = read(player);
        return root.contains(ACTIVE_ALLY, Tag.TAG_ANY_NUMERIC) ? root.getInt(ACTIVE_ALLY) : -1;
    }

    public static void setActiveAlly(Player player, int generation) {
        CompoundTag root = read(player);
        root.putInt(ACTIVE_ALLY, generation);
        write(player, root);
    }

    /** 盟友冷却截止，主世界 gameTime；0 = 没有冷却 */
    public static long getAllyCooldownUntil(Player player) {
        return read(player).getLong(ALLY_COOLDOWN_UNTIL);
    }

    public static void setAllyCooldownUntil(Player player, long until) {
        CompoundTag root = read(player);
        root.putLong(ALLY_COOLDOWN_UNTIL, until);
        write(player, root);
    }

    // ==================== 物品列表 ====================

    /**
     * 按堆叠上限拆开：1.20.1 的 {@code ItemStack.save} 把数量写成 byte，一堆超过 127 存盘读回就成了空气。
     * 空堆丢掉，返回的都是副本
     */
    public static List<ItemStack> splitStacks(Collection<ItemStack> items) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int max = Math.max(1, stack.getMaxStackSize());
            int left = stack.getCount();
            while (left > 0) {
                int n = Math.min(max, left);
                out.add(stack.copyWithCount(n));
                left -= n;
            }
        }
        return out;
    }

    public static ListTag writeItems(Collection<ItemStack> items) {
        ListTag list = new ListTag();
        for (ItemStack stack : splitStacks(items)) {
            list.add(stack.save(new CompoundTag()));
        }
        return list;
    }

    /** 读不出来的条目（数量 ≤ 0、物品已不存在）记 error，不静默跳过 */
    public static List<ItemStack> readItems(ListTag list, String what) {
        List<ItemStack> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ItemStack stack = ItemStack.of(tag);
            if (stack.isEmpty()) {
                LogUtil.error(what + " 第 " + i + " 条读不出物品，已跳过：" + tag);
                continue;
            }
            out.add(stack);
        }
        return out;
    }
}
