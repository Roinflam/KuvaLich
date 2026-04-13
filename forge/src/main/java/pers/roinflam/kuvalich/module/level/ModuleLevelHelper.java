package pers.roinflam.kuvalich.module.level;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.config.ModConfig;
import pers.roinflam.kuvalich.item.module.item.*;
import pers.roinflam.kuvalich.item.module.warframe.*;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 模组等级系统核心工具类
 * Module Level System Core Helper
 * <p>
 * 职责：等级NBT读写、属性缩放、升级费用、精通键生成、揭示等级赋予、系统开关
 * <p>
 * ⭐ 升级费用根据模组品质缩放：
 *    铜卡(Common) 25% | 银卡(Uncommon) 50% | 金卡(Rare) 75% | Prime/裂罅 100%
 *
 * @author RoinFlam
 */
public final class ModuleLevelHelper {

    private static final String LEVEL_KEY = "ModuleLevel";
    private static final String MODULE_TAG = Reference.MOD_ID + "_modules";

    // ==================== 系统开关 ====================

    /**
     * 检查模组等级系统是否启用
     */
    public static boolean isLevelSystemEnabled() {
        return ModConfig.KUVA_LICH.enableModuleLevelSystem.get();
    }

    // ==================== 等级NBT读写 ====================

    /**
     * 获取模组的当前等级
     * 系统关闭：返回满级 | 无NBT：返回1（旧存档兼容）
     */
    public static int getModuleLevel(ItemStack moduleStack) {
        if (!isLevelSystemEnabled()) { return getMaxLevel(); }
        if (moduleStack == null || moduleStack.isEmpty()) { return 1; }
        CompoundTag moduleTag = moduleStack.getTagElement(MODULE_TAG);
        if (moduleTag == null || !moduleTag.contains(LEVEL_KEY)) { return 1; }
        int level = moduleTag.getInt(LEVEL_KEY);
        return Math.max(1, Math.min(level, getMaxLevel()));
    }

    /**
     * 设置模组的等级
     */
    public static void setModuleLevel(ItemStack moduleStack, int level) {
        if (moduleStack == null || moduleStack.isEmpty()) { return; }
        int clampedLevel = Math.max(1, Math.min(level, getMaxLevel()));
        CompoundTag moduleTag = moduleStack.getOrCreateTagElement(MODULE_TAG);
        moduleTag.putInt(LEVEL_KEY, clampedLevel);
    }

    /**
     * 检查模组是否已满级
     */
    public static boolean isMaxLevel(ItemStack moduleStack) {
        return getModuleLevel(moduleStack) >= getMaxLevel();
    }

    /**
     * 检查模组是否有等级NBT标签
     */
    public static boolean hasLevelTag(ItemStack moduleStack) {
        if (moduleStack == null || moduleStack.isEmpty()) { return false; }
        CompoundTag moduleTag = moduleStack.getTagElement(MODULE_TAG);
        return moduleTag != null && moduleTag.contains(LEVEL_KEY);
    }

    // ==================== 配置读取 ====================

    /**
     * 获取模组最大等级（从配置文件读取，默认10）
     */
    public static int getMaxLevel() {
        return ModConfig.KUVA_LICH.moduleMaxLevel.get();
    }

    // ==================== 属性缩放 ====================

    /**
     * 获取模组属性的有效倍率
     * 系统关闭：1.0 | 1级/10满级：0.1 | 5级/10满级：0.5 | 10级/10满级：1.0
     */
    public static double getEffectiveMultiplier(ItemStack moduleStack) {
        if (!isLevelSystemEnabled()) { return 1.0; }
        int level = getModuleLevel(moduleStack);
        int maxLevel = getMaxLevel();
        if (maxLevel <= 1) { return 1.0; }
        return (double) level / (double) maxLevel;
    }

    // ==================== 升级费用 ====================

    /**
     * 计算从当前等级升级到下一级所需的内融核心数量（基础费用，不含品质缩放）
     * <p>
     * 公式：cost(n) = max(1, round(endoLastLevelCost × (n / (maxLevel - 1)) ^ exponent))
     * 已满级返回-1
     *
     * @param currentLevel 当前等级
     * @return 基础升级费用，已满级返回-1
     */
    public static int getUpgradeCost(int currentLevel) {
        int maxLevel = getMaxLevel();
        if (currentLevel >= maxLevel || maxLevel <= 1) { return -1; }
        int lastLevelCost = ModConfig.KUVA_LICH.endoLastLevelCost.get();
        double exponent = ModConfig.KUVA_LICH.endoCostExponent.get();
        double ratio = (double) currentLevel / (double) (maxLevel - 1);
        double rawCost = lastLevelCost * Math.pow(ratio, exponent);
        return Math.max(1, (int) Math.round(rawCost));
    }

    /**
     * ⭐ 计算指定模组从当前等级升级所需的内融核心数量（含品质缩放）
     * <p>
     * 在基础费用上乘以品质系数后向上取整，保证最低消耗1个。
     * <p>
     * 品质系数：铜卡 0.25 | 银卡 0.50 | 金卡 0.75 | Prime/裂罅 1.00
     *
     * @param currentLevel 当前等级
     * @param moduleStack  模组物品栈（用于判断品质）
     * @return 实际升级费用，已满级返回-1
     */
    public static int getUpgradeCost(int currentLevel, ItemStack moduleStack) {
        int baseCost = getUpgradeCost(currentLevel);
        if (baseCost <= 0) { return baseCost; }
        double multiplier = getRarityCostMultiplier(moduleStack);
        // 向上取整确保低品质低等级时不会出现0消耗
        return Math.max(1, (int) Math.ceil(baseCost * multiplier));
    }

    /**
     * ⭐ 获取模组品质对应的内融核心费用系数
     * <p>
     * 品质越低，升级越便宜：
     * <pre>
     * 品质        系数    说明
     * 铜卡        0.25    基础费用的 1/4
     * 银卡        0.50    基础费用的 2/4
     * 金卡        0.75    基础费用的 3/4
     * Prime/裂罅  1.00    全额
     * </pre>
     * <p>
     * 武器模组和战甲模组共用同一套系数。
     *
     * @param moduleStack 模组物品栈
     * @return 费用系数（0.25 ~ 1.0）
     */
    public static double getRarityCostMultiplier(ItemStack moduleStack) {
        if (moduleStack == null || moduleStack.isEmpty()) { return 1.0; }
        Item item = moduleStack.getItem();
        // 紫卡（裂罅）和 Prime：全额
        if (item instanceof ItemRivenModule || item instanceof WarframeRivenModule
                || item instanceof ItemPrimeModule || item instanceof WarframePrimeModule) {
            return 1.0;
        }
        // 金卡（稀有）：3/4
        if (item instanceof ItemRareModule || item instanceof WarframeRareModule) {
            return 0.75;
        }
        // 银卡（罕见）：2/4
        if (item instanceof ItemUncommonModule || item instanceof WarframeUncommonModule) {
            return 0.5;
        }
        // 铜卡（普通）：1/4
        if (item instanceof ItemCommonModule || item instanceof WarframeCommonModule) {
            return 0.25;
        }
        // 未知品质兜底：全额
        return 1.0;
    }

    /**
     * 计算从1级升到满级的总内融核心消耗（基础费用，不含品质缩放）
     */
    public static int getTotalUpgradeCost() {
        int maxLevel = getMaxLevel();
        int total = 0;
        for (int i = 1; i < maxLevel; i++) { total += getUpgradeCost(i); }
        return total;
    }

    // ==================== 精通键 ====================

    /**
     * 获取模组的精通键（mastery key）
     * 普通模组：type字符串 | 武器裂罅：按模式分3键 | 战甲裂罅：共享1键
     */
    public static String getMasteryKey(ItemStack moduleStack) {
        if (moduleStack == null || moduleStack.isEmpty()) { return ""; }
        String type = AbstractModule.getType(moduleStack);
        if (type.isEmpty()) { return ""; }
        if (type.equals(ItemRivenModule.RIVEN_TYPE) && moduleStack.getItem() instanceof ItemRivenModule) {
            int mode = ItemRivenModule.getRivenMode(moduleStack);
            switch (mode) {
                case ItemRivenModule.MODE_MELEE: return type + "_melee";
                case ItemRivenModule.MODE_REMOTE: return type + "_remote";
                case ItemRivenModule.MODE_UNIVERSAL: return type + "_universal";
                default: return type + "_melee";
            }
        }
        return type;
    }

    // ==================== ⭐ 揭示等级赋予 ====================

    /**
     * 模组揭示时赋予等级
     * <p>
     * 根据配置 enableMasteryOnReveal 决定是否读取精通记录：
     * - true：读取玩家精通记录，赋予已达到的最高等级（默认行为）
     * - false：每次揭示都是1级，忽略精通
     * <p>
     * 等级系统关闭时不做任何操作（所有模组按满级计算）
     *
     * @param module 揭示出的模组物品
     * @param player 揭示者
     */
    public static void applyRevealLevel(ItemStack module, Player player) {
        if (!isLevelSystemEnabled()) { return; }
        if (module == null || module.isEmpty() || player == null) { return; }

        // 配置：是否启用精通赋予
        boolean useMastery = ModConfig.KUVA_LICH.enableMasteryOnReveal.get();

        if (useMastery) {
            // 读取精通记录赋予等级
            String masteryKey = getMasteryKey(module);
            if (!masteryKey.isEmpty()) {
                player.getCapability(CapabilityRegistryHandler.REQUIEM_CARD).ifPresent(requiemCard -> {
                    int masteryLevel = requiemCard.getMasteryLevel(masteryKey);
                    if (masteryLevel > 0) {
                        setModuleLevel(module, masteryLevel);
                    } else {
                        setModuleLevel(module, 1);
                    }
                });
            } else {
                setModuleLevel(module, 1);
            }
        } else {
            // 每次揭示都是1级
            setModuleLevel(module, 1);
        }
    }

    private ModuleLevelHelper() {}
}
