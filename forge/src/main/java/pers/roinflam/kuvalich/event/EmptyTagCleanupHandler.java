package pers.roinflam.kuvalich.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 清除物品上无意义的空 {@code tag:{}} 标签
 *
 * <p><b>这修的是本模组自己造成的历史损坏。</b>
 * 1.20.1 的移植起点（{@code c7f88a1}，2026-01-13）把 1.12.2 里只读的
 * {@code itemStack.serializeNBT()} 换成了<b>原地写入</b>的 {@code itemStack.getOrCreateTag()}，
 * 而当时的 {@code onItemTooltip} 对鼠标划过的<b>每一个</b>物品都无条件调用它。
 * 于是泥土、圆石这些本来没有 NBT 的物品被塞进一个空标签。</p>
 *
 * <p><b>为什么空标签会让物品堆不起来</b>：{@code ItemStack.isSameItemSameTags}
 * 用 {@code Objects.equals(tagA, tagB)} 比较 NBT，而 {@code null} 与 {@code {}}
 * <b>并不相等</b>。于是「被鼠标划过的那一摞」和「没被划过的那一摞」从此永远合不到一起 ——
 * 现象是玩家莫名其妙多出一堆 1 个 1 个的泥土。</p>
 *
 * <p>成因已在 2.7.0（{@code 631ee70}）修掉，但<b>已经写进存档的空标签不会自己消失</b>，
 * 所以需要这一遍清理。清理只在服务端做，且只在标签**完全为空**时移除 ——
 * 任何带真实数据的物品都不会被动到。</p>
 *
 * <p>清理挂在 {@link TickEvent.PlayerTickEvent} 上而不是只在登录时跑一次：
 * 受损物品可能躺在箱子里，等玩家哪天把它拿进背包才需要被修。
 * 代价是每 {@value #SCAN_INTERVAL} tick 扫一次背包 —— 只读取 {@code getTag()}，
 * 命中才写，正常存档里长期是零写入。</p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class EmptyTagCleanupHandler {

    /** 扫描间隔（tick）。200 = 10 秒，比模组发现的扫描更稀疏，因为这是兜底而非功能 */
    private static final int SCAN_INTERVAL = 200;

    /** 工具类禁止实例化 */
    private EmptyTagCleanupHandler() {
    }

    /** 登录时先扫一遍：让玩家一进服就看到背包恢复正常 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int fixed = sweep(player);
        if (fixed > 0) {
            LogUtil.debug("已清理 " + player.getName().getString() + " 背包中 " + fixed + " 个空 NBT 标签");
        }
    }

    /** 定期兜底：受损物品可能是刚从箱子里拿出来的 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % SCAN_INTERVAL != 0) {
            return;
        }
        sweep(player);
    }

    /**
     * 扫一遍背包，移除完全为空的 NBT 标签
     *
     * @param player 目标玩家
     * @return 修复了几个物品
     */
    private static int sweep(ServerPlayer player) {
        if (!ModConfig.KUVA_LICH.cleanupEmptyItemTags.get()) {
            return 0;
        }

        int fixed = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag tag = stack.getTag();
            // ⭐ 只处理「有标签但标签里一个键都没有」：这种标签在原版语义下没有任何含义，
            //    唯一的作用就是让物品堆不起来。带任何数据的标签一律不碰。
            if (tag != null && tag.isEmpty()) {
                stack.setTag(null);
                fixed++;
            }
        }

        if (fixed > 0) {
            // 让客户端立刻看到合并后的结果，否则要等下一次容器同步
            player.containerMenu.broadcastChanges();
        }
        return fixed;
    }
}
