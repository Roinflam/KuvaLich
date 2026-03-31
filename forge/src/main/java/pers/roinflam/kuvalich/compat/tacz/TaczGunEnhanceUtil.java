package pers.roinflam.kuvalich.compat.tacz;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.item.LichReliquary;

import java.util.LinkedList;

/**
 * TACZ枪械永久强化工具类
 * 处理枪械基础伤害永久增幅的NBT读写、伤害乘数计算、合法性校验
 *
 * <p>强化数据直接存储在TACZ枪械的NBT中（跟着枪走），不依赖玩家Capability。</p>
 *
 * <p>伤害计算方式（线性叠加，非复利）：
 * <br>最终伤害 = 原始伤害 × (1 + 强化次数 × 每次增幅百分比)</p>
 *
 * TACZ Gun Permanent Enhancement Utility
 */
public final class TaczGunEnhanceUtil {

    // ==================== NBT常量 ====================

    /** NBT标签键：枪械强化次数 */
    public static final String TAG_ENHANCE_COUNT = "kuvalich_gun_enhance_count";

    /** 私有构造，纯工具类禁止实例化 */
    private TaczGunEnhanceUtil() {
    }

    // ==================== NBT读写 ====================

    /**
     * 获取枪械已强化次数
     *
     * @param gunStack 枪械物品堆
     * @return 强化次数，无标签时返回0
     */
    public static int getEnhanceCount(ItemStack gunStack) {
        if (gunStack.isEmpty() || !gunStack.hasTag()) {
            return 0;
        }
        return gunStack.getTag().getInt(TAG_ENHANCE_COUNT);
    }

    /**
     * 设置枪械强化次数
     *
     * @param gunStack 枪械物品堆
     * @param count    强化次数
     */
    public static void setEnhanceCount(ItemStack gunStack, int count) {
        gunStack.getOrCreateTag().putInt(TAG_ENHANCE_COUNT, Math.max(0, count));
    }

    // ==================== 伤害计算 ====================

    /**
     * 获取伤害乘数
     * <p>乘数 = 1.0 + 强化次数 × 每次增幅百分比</p>
     * <p>例：3次强化，每次25% → 1.0 + 3×0.25 = 1.75</p>
     *
     * @param gunStack 枪械物品堆
     * @return 伤害乘数，无强化时返回1.0
     */
    public static double getDamageMultiplier(ItemStack gunStack) {
        int count = getEnhanceCount(gunStack);
        if (count <= 0) {
            return 1.0;
        }
        double percentPerEnhance = ModConfig.KUVA_LICH.taczGunEnhancePercent.get();
        return 1.0 + count * percentPerEnhance;
    }

    /**
     * 对伤害缓存列表应用强化乘数（就地修改）
     * <p>用于在 AttachmentPropertyEvent 中修改战斗伤害缓存</p>
     *
     * @param damagePairs 伤害-距离对列表（TACZ的DamageModifier缓存）
     * @param multiplier  伤害乘数
     */
    public static void applyMultiplierToCache(LinkedList<ExtraDamage.DistanceDamagePair> damagePairs,
                                              double multiplier) {
        if (damagePairs == null || damagePairs.isEmpty() || multiplier == 1.0) {
            return;
        }
        // 构建新列表替换内容（DistanceDamagePair无setter，需新建对象）
        LinkedList<ExtraDamage.DistanceDamagePair> modified = new LinkedList<>();
        for (ExtraDamage.DistanceDamagePair pair : damagePairs) {
            modified.add(new ExtraDamage.DistanceDamagePair(
                    pair.getDistance(),
                    (float) (pair.getDamage() * multiplier)
            ));
        }
        damagePairs.clear();
        damagePairs.addAll(modified);
    }

    // ==================== 校验逻辑 ====================

    /**
     * 检查物品是否为TACZ枪械（不含配置和上限检查，仅类型判断）
     * <p>用于菜单槽位的 mayPlace 判断</p>
     *
     * @param stack 待检查物品堆
     * @return 是否为TACZ枪械
     */
    public static boolean isTaczGun(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        try {
            return IGun.getIGunOrNull(stack) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查物品是否为可强化的TACZ枪械
     * <p>条件：
     * <br>1. 功能已在配置中启用
     * <br>2. 物品是TACZ枪械（IGun接口）
     * <br>3. 未达到强化上限</p>
     *
     * @param stack 待检查物品堆
     * @return 是否可强化
     */
    public static boolean isEnhanceableTaczGun(ItemStack stack) {
        // 功能开关
        if (!ModConfig.KUVA_LICH.taczGunEnhanceEnable.get()) {
            return false;
        }
        // 必须是TACZ枪械
        if (!isTaczGun(stack)) {
            return false;
        }
        // 检查上限（-1 = 无限）
        int maxCount = ModConfig.KUVA_LICH.taczGunEnhanceMaxCount.get();
        if (maxCount > 0 && getEnhanceCount(stack) >= maxCount) {
            return false;
        }
        return true;
    }

    /**
     * 检查物品是否为玄骸之遗
     *
     * @param stack 待检查物品堆
     * @return 是否为玄骸之遗
     */
    public static boolean isLichReliquary(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LichReliquary;
    }

    /**
     * 对枪械执行一次强化
     * <p>强化次数+1，消耗1个玄骸之遗</p>
     *
     * @param gunStack       枪械物品堆
     * @param reliquaryStack 玄骸之遗物品堆
     * @return 强化是否成功
     */
    public static boolean performEnhance(ItemStack gunStack, ItemStack reliquaryStack) {
        if (!isEnhanceableTaczGun(gunStack) || !isLichReliquary(reliquaryStack)) {
            return false;
        }
        // 强化次数+1
        int newCount = getEnhanceCount(gunStack) + 1;
        setEnhanceCount(gunStack, newCount);
        // 消耗1个玄骸之遗
        reliquaryStack.shrink(1);
        return true;
    }

    /**
     * 获取当前强化百分比（用于Tooltip显示）
     * <p>例：3次强化，每次25% → 返回 0.75 (即75%)</p>
     *
     * @param gunStack 枪械物品堆
     * @return 总增幅百分比，无强化时返回0.0
     */
    public static double getTotalEnhancePercent(ItemStack gunStack) {
        int count = getEnhanceCount(gunStack);
        if (count <= 0) {
            return 0.0;
        }
        return count * ModConfig.KUVA_LICH.taczGunEnhancePercent.get();
    }
}
