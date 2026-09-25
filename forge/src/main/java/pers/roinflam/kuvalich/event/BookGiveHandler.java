package pers.roinflam.kuvalich.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.InventoryUtil;
import pers.roinflam.kuvalich.utils.KuvaPalette;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.PlayerFeedback;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 冒险指南发放事件处理器
 * <p>
 * 玩家首次进服时（配置 {@code enableGuidebook} 开启）往背包里放一本「赤毒玄骸 冒险指南」，
 * 右键打开本模组自带的指南界面，不再依赖帕秋莉（Patchouli）。
 * </p>
 *
 * <h3>为什么换了标记键</h3>
 * <p>
 * 旧版用 {@code kuvalich:guidebook_given} 记录发过帕秋莉书。换成自带指南后，
 * 老玩家手里那本帕秋莉书在整合包删掉帕秋莉后会变成无效物品，所以改用新键
 * {@link #GUIDEBOOK_GIVEN_TAG}，让每个人（包括老玩家）都补发一次新书。旧键不再读写。
 * </p>
 *
 * <h3>为什么「真放进背包」才写标记</h3>
 * <p>
 * 换键后所有老玩家都要补发一次，而冒险服老玩家的背包大多是满的。以前塞不下就
 * {@code drop} 在脚下再照常写标记：背包满着捡不回来，5 分钟后书消失，以后也不会再发，
 * 聊天栏还一句提示都没有。现在塞不下就<b>不写标记</b>、提示玩家腾格子，下次登录 / 重生自动重试。
 * </p>
 * <p>
 * 死亡界面同理：玩家停在死亡界面时退出，下次登录拿到的是一具「已死」的旧实体，
 * 背包在死亡那一刻已经清空（看着有空位）。书放进去后一点重生，{@code keepInventory}
 * 关闭时原版 {@code ServerPlayer.restoreFrom} 不复制背包，而标记在 {@code PERSISTED_NBT_TAG}
 * 里会被复制过去 —— 书没了、标记还在。所以死着的时候跳过，改在 {@link #onPlayerRespawn} 里发。
 * </p>
 * <p>
 * 书丢了之后的补领：无序配方「书 + 赤毒」（{@code data/kuvalich/recipes/kuvalich_guide.json}）。
 * </p>
 *
 * @author RoinFlam
 */
public class BookGiveHandler {

    /** 持久化数据键：标记是否已发放自带指南（v2） */
    private static final String GUIDEBOOK_GIVEN_TAG = Reference.MOD_ID + ":guidebook_v2_given";

    /**
     * 玩家登录事件处理
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        tryGive(event.getEntity());
    }

    /**
     * 玩家重生事件处理：补上「死亡界面登录」和「上次背包满」这两种没发成的情况。
     * <p>已经发过的玩家标记随 {@code PERSISTED_NBT_TAG} 复制到新实体，这里直接返回，不会重复发。</p>
     *
     * @param event 玩家重生事件
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        tryGive(event.getEntity());
    }

    /**
     * 没发过就发一本；放不进背包（或人还死着）就不写标记，留到下次登录 / 重生再试。
     *
     * @param player 玩家
     */
    private static void tryGive(Player player) {
        if (player.level().isClientSide()) {
            return;
        }

        if (!ModConfig.KUVA_LICH.enableGuidebook.get()) {
            return;
        }

        // 死亡界面：背包已清空、这具实体马上要被重生替换，放进来的书会跟着旧实体一起丢掉
        if (player.isDeadOrDying()) {
            return;
        }

        CompoundTag persistentData = player.getPersistentData();
        CompoundTag forgeData = persistentData.getCompound(Player.PERSISTED_NBT_TAG);

        if (forgeData.getBoolean(GUIDEBOOK_GIVEN_TAG)) {
            return;
        }

        ItemStack book = new ItemStack(KuvaLichItems.KUVALICH_GUIDE.get());
        // 不能用 add 的返回值判断，创造模式下它会抹掉物品还返回 true；hasRoomFor 问的是「真有地方放」
        if (!InventoryUtil.hasRoomFor(player, book)) {
            PlayerFeedback.chat(player, KuvaPalette.WARN, "kuvalich.guide.inventory_full");
            LogUtil.debug("玩家 " + player.getName().getString() + " 背包已满，冒险指南留到下次登录 / 重生再发");
            return;
        }
        InventoryUtil.giveOrDrop(player, book);

        forgeData.putBoolean(GUIDEBOOK_GIVEN_TAG, true);
        persistentData.put(Player.PERSISTED_NBT_TAG, forgeData);

        PlayerFeedback.chat(player, KuvaPalette.SUCCESS, "kuvalich.guide.given");
        LogUtil.debug("已向玩家 " + player.getName().getString() + " 发放赤毒玄骸冒险指南");
    }
}
