package pers.roinflam.kuvalich.module.weapon;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 额外装备槽位（副手 / 护甲 / Curios）加成的取数辅助（客户端专用）
 *
 * <p>⭐ 本类原先还负责渲染——{@code appendExtraSlotTooltip} 会把每一条额外加成
 * 单独列成一行（最多 48 行），而这些数值**同时**已经被 {@code mergeExtraSlotIntoAttributes}
 * 合进了主面板。也就是说同一份加成显示了两遍、每条占 2 行，
 * 这是「tooltip 整个屏幕都是属性」的主要来源之一。</p>
 *
 * <p>现在渲染统一交给 {@code client.tooltip.WeaponPanelComposer}：
 * 额外加成作为一个独立的 chip 组打包显示（通常 1~2 行），
 * 主面板数值依旧含这部分加成，但不再重复列一整块明细。</p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
public final class ExtraSlotTooltipHelper {

    // ========== 主手检测 / Main Hand Detection ==========

    /**
     * 判断当前查看的物品是否为主手武器
     * <p>
     * 引用比较优先（物品栏GUI中tooltip事件通常传入槽位原始引用）；
     * 内容比较兜底，但当主副手内容完全相同时仅引用匹配才通过。
     *
     * <p>⚠️ 这个判定只适用于**物品级**加成（额外槽位的属性确实只在拿着主手武器时生效）。
     * 击杀叠层是**玩家级**状态，不要套用这个判定 ——
     * 否则双持同款武器时（{@code weaponStack} 与主副手内容都匹配）叠层会莫名消失。</p>
     *
     * @param weaponStack 被查看的物品栈
     * @param player      玩家
     * @return 是否为主手武器
     */
    private static boolean isMainHandWeapon(ItemStack weaponStack, Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // 引用比较：直接是主手对象 → 一定是主手
        if (weaponStack == mainHand) {
            return true;
        }

        // 内容比较：匹配主手内容
        if (ItemStack.matches(weaponStack, mainHand)) {
            // 如果副手也匹配（主副手内容相同），无法区分 → 不显示
            return !ItemStack.matches(weaponStack, offHand);
        }

        return false;
    }

    // ========== 公开方法 / Public Methods ==========

    /**
     * 取额外槽位带来的属性加成（仅主手武器）
     *
     * <p>返回的是**独立的一份**，调用方可以既拿它单独显示来源、
     * 又把它合进主面板，不需要调用两次。</p>
     *
     * @param player      查看物品的玩家（可为 null）
     * @param weaponStack 被查看的物品栈
     * @return 额外槽位属性表；非主手武器或无玩家时返回空表（不会返回 null）
     */
    public static HashMap<String, Double> getExtraSlotAttributes(@Nullable Player player, ItemStack weaponStack) {
        if (player == null || !isMainHandWeapon(weaponStack, player)) {
            return new HashMap<>();
        }
        // ⭐ WeaponCombatHandler 里有按 tick 的缓存，同一帧多次调用不会重算
        return new HashMap<>(WeaponCombatHandler.getCachedExtraSlotAttributes(player));
    }

    /**
     * 将额外槽位属性合并到面板属性表（仅主手武器）
     *
     * <p>保留为公共 API：外部兼容代码（如 TACZ 桥）需要与面板口径一致的合并结果。</p>
     *
     * @param player      查看物品的玩家（可为null）
     * @param weaponStack 被查看的物品栈
     * @param attributes  面板属性表（会被直接修改）
     */
    public static void mergeExtraSlotIntoAttributes(@Nullable Player player, ItemStack weaponStack,
                                                    HashMap<String, Double> attributes) {
        for (Map.Entry<String, Double> entry : getExtraSlotAttributes(player, weaponStack).entrySet()) {
            if (Math.abs(entry.getValue()) >= 0.001) {
                attributes.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
    }

    /** 工具类禁止实例化 */
    private ExtraSlotTooltipHelper() {
    }
}
