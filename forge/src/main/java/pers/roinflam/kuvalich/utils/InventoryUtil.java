package pers.roinflam.kuvalich.utils;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 背包空间判断
 *
 * <h3>为什么不能直接信 {@code Inventory#add} 的返回值</h3>
 * <p>看起来它返回 false 就代表「塞不下」，实际上<b>创造模式下它永远返回 true</b>。
 * 1.20.1 的 {@code Inventory.add(int, ItemStack)} 字节码里有两处：</p>
 *
 * <pre>
 *  64: stack.getCount()
 *  68: if_icmpne 91                 // 一点都没塞进去
 *  72: player.getAbilities().instabuild
 *  81: ifeq 91
 *  85: stack.setCount(0)            // ⭐ 直接把物品抹掉
 *  89: iconst_1
 *  90: ireturn                      // ⭐ 仍然返回 true
 * </pre>
 *
 * <p>（另一处在受损物品分支，偏移 150~168，行为相同。）</p>
 *
 * <p>于是 {@code if (!inv.add(stack)) { 掉在脚下 }} 这个到处都在用的写法，
 * 对创造模式玩家是**完全失效**的：物品被原版静默销毁，else 分支一次都不会进。
 * 调用方接着照常 {@code setStackInSlot(slot, EMPTY)} 清空槽位 —— 东西就这么没了。
 * 军械库关闭时返还武器走的正是这条路径，背包满的创造玩家关一次菜单就丢一把武器。</p>
 *
 * <h3>判断依据</h3>
 * <p>{@link #hasRoomFor} 抄的是原版 {@code addResource(ItemStack)} 自己的判空逻辑：</p>
 *
 * <pre>
 *   i = getSlotWithRemainingSpace(stack);   // 能并进已有堆？
 *   if (i == -1) i = getFreeSlot();         // 有空格？
 *   if (i == -1) return stack.getCount();   // 两个都没有 == 塞不下
 * </pre>
 *
 * <p>所以「有空间」严格等价于这两个方法有一个返回非 -1。两者都是 public，
 * 不需要反射也不需要 mixin。</p>
 *
 * @author RoinFlam
 */
public final class InventoryUtil {

    private InventoryUtil() {
    }

    /**
     * 这个物品塞得进玩家背包吗
     *
     * <p>与原版 {@code Inventory#add} 的实际收纳能力一致，但<b>不受创造模式影响</b> ——
     * 问的是「真的有地方放」，而不是「add 会不会返回 true」。</p>
     *
     * <p>注意它判断的是**能不能放进去哪怕一个**，不保证整堆都放得下。
     * 目前所有调用点给的都是单个物品或整堆一起给，够用；
     * 将来若要按数量精确判断，得另写一个模拟收纳的版本。</p>
     *
     * @param player 玩家
     * @param stack  待放入的物品
     * @return 有空间则 true；物品为空时视为「放得下」（不用特殊处理）
     */
    public static boolean hasRoomFor(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        Inventory inv = player.getInventory();
        return inv.getSlotWithRemainingSpace(stack) != -1 || inv.getFreeSlot() != -1;
    }

    /**
     * 给玩家物品，塞不下就掉在脚下
     *
     * <p>替代 {@code if (!inv.add(stack)) player.drop(stack, false);} —— 那个写法
     * 在创造模式下会销毁物品，原因见类注释。</p>
     *
     * @param player 玩家
     * @param stack  物品（为空则什么都不做）
     * @return true 表示进了背包，false 表示掉在了地上
     */
    public static boolean giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (hasRoomFor(player, stack)) {
            player.getInventory().add(stack);
            return true;
        }
        player.drop(stack, false);
        return false;
    }
}
