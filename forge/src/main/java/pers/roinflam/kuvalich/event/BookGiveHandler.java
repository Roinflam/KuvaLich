package pers.roinflam.kuvalich.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import vazkii.patchouli.api.PatchouliAPI;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.utils.LogUtil;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 教程书发放事件处理器
 * <p>
 * 当玩家首次进入服务器时，若安装了帕秋莉(Patchouli)模组且配置启用，
 * 自动向玩家背包中添加一本赤毒玄骸教程书。
 * </p>
 * <p>
 * 使用 Patchouli API 获取教程书物品栈，确保兼容性。
 * 使用玩家的 PersistentData 记录是否已发放，避免重复发放。
 * </p>
 *
 * @author RoinFlam
 */
public class BookGiveHandler {

    /** 持久化数据键：标记是否已发放教程书 / Persistent data key: whether guidebook has been given */
    private static final String GUIDEBOOK_GIVEN_TAG = Reference.MOD_ID + ":guidebook_given";

    /** 本模组的教程书 ResourceLocation / This mod's guidebook ResourceLocation */
    private static final ResourceLocation BOOK_ID = new ResourceLocation(Reference.MOD_ID, "kuvalich_guide");

    /**
     * 玩家登录事件处理
     * <p>
     * 仅在以下条件全部满足时发放教程书：
     * <ul>
     *     <li>配置项 enableGuidebook 为 true</li>
     *     <li>Patchouli 模组已加载</li>
     *     <li>该玩家尚未领取过教程书</li>
     * </ul>
     * </p>
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();

        // 仅服务端执行
        if (player.level().isClientSide()) {
            return;
        }

        // 检查配置是否启用
        if (!ModConfig.KUVA_LICH.enableGuidebook.get()) {
            return;
        }

        // 检查 Patchouli 是否已加载（双重保险，主类已经检查过一次）
        if (!ModList.get().isLoaded("patchouli")) {
            return;
        }

        // 检查是否已发放过
        CompoundTag persistentData = player.getPersistentData();
        CompoundTag forgeData = persistentData.getCompound(Player.PERSISTED_NBT_TAG);

        if (forgeData.getBoolean(GUIDEBOOK_GIVEN_TAG)) {
            return;
        }

        // 通过 Patchouli API 获取教程书物品栈
        ItemStack bookStack = PatchouliAPI.get().getBookStack(BOOK_ID);
        if (bookStack.isEmpty()) {
            LogUtil.error("无法通过 Patchouli API 创建教程书，书 ID: " + BOOK_ID);
            return;
        }

        // 发放到玩家背包
        if (!player.getInventory().add(bookStack)) {
            // 背包满时掉落在地上
            player.drop(bookStack, false);
        }

        // 标记已发放
        forgeData.putBoolean(GUIDEBOOK_GIVEN_TAG, true);
        persistentData.put(Player.PERSISTED_NBT_TAG, forgeData);

        LogUtil.debug("已向玩家 " + player.getName().getString() + " 发放赤毒玄骸教程书");
    }
}