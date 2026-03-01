package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import pers.roinflam.kuvalich.utils.Reference;

import java.util.*;

/**
 * 模组配置类
 * Module configuration class
 *
 * 管理模组系统的各项配置，包括：
 *   - 禁用模组类型
 *   - 词条单值范围（min/max）
 *   - 词条总值上限（totalCap）
 *
 * Manages module system configurations, including:
 *   - Module type disabling
 *   - Attribute value range (min/max per single module)
 *   - Attribute total cap (sum of all equipped modules)
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModuleConfig {

    public static final ForgeConfigSpec MODULE_CONFIG;

    // ========== 禁用模组类型 / Disabled Module Types ==========

    /**
     * 禁用的模组类型列表
     * Disabled module types list
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_MODULE_TYPES;

    // ========== 词条单值范围 / Attribute Value Range ==========

    /**
     * 词条单值范围列表（限制单个模组上某词条的数值区间）
     * Attribute value range list (limits the value of an attribute on a single module)
     *
     * 格式 / Format: "attributeName:minValue:maxValue"
     *
     * 示例 / Examples:
     *   "meleeDamage:-0.5:3.0"
     *       → 单个模组的 meleeDamage 词条值必须在 [-0.5, 3.0] 范围内
     *       → meleeDamage value on a single module must be within [-0.5, 3.0]
     *
     *   "meleeCriticalStrikeProbability:0.0:1.0"
     *       → 单个模组的近战暴击率词条值限制在 [0.0, 1.0]，即 0%~100%
     *
     *   "triggerChance:0.0:1.5"
     *       → 单个模组的触发几率词条值限制在 [0.0, 1.5]，即 0%~150%
     *
     * 注意 / Note:
     *   - min 可以为负数（允许负词条存在但限制下限）
     *   - 超出范围的值会被 clamp 到边界，不会报错
     *   - 不在列表中的词条不受限制
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ATTRIBUTE_VALUE_RANGES;

    // ========== 词条总值上限 / Attribute Total Cap ==========

    /**
     * 词条总值上限列表（限制所有装备模组叠加后某词条的总和上限）
     * Attribute total cap list (limits the sum of an attribute across all equipped modules)
     *
     * 格式 / Format: "attributeName:maxTotal"
     *
     * 示例 / Examples:
     *   "meleeDamage:5.0"
     *       → 所有装备模组的 meleeDamage 叠加总值不超过 5.0（即 +500%）
     *       → Total meleeDamage across all equipped modules cannot exceed 5.0
     *
     *   "meleeCriticalStrikeProbability:3.0"
     *       → 近战暴击率叠加上限为 3.0（即 +300%，对应代码中三档暴击的最高档）
     *
     *   "triggerChance:5.0"
     *       → 触发几率叠加上限为 5.0（即 +500%，最多五次连触发）
     *
     *   "health:3.0"
     *       → 生命值叠加上限为 3.0（即 +300%）
     *
     * 注意 / Note:
     *   - 上限只限正向叠加，不影响负词条的叠加
     *   - 超出上限的总值会被 clamp 到上限值
     *   - 不在列表中的词条不受限制
     *   - 此限制发生在 hitKillStack 效果叠加之前（仅限基础模组叠加阶段）
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ATTRIBUTE_TOTAL_CAPS;

    // ========== 缓存 / Caches ==========

    /**
     * 词条单值范围缓存
     * Attribute value range cache
     * key=词条名, value=[min, max]
     */
    private static Map<String, double[]> attributeRangeCache = null;

    /**
     * 词条总值上限缓存
     * Attribute total cap cache
     * key=词条名, value=maxTotal
     */
    private static Map<String, Double> attributeTotalCapCache = null;

    /**
     * 禁用类型缓存
     * Disabled types cache
     */
    private static Set<String> disabledTypesCache = null;

    /**
     * 配置是否已加载
     * Whether config is loaded
     */
    private static volatile boolean configLoaded = false;

    // ========== 静态初始化 / Static Initialization ==========

    static {
        ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        BUILDER.comment("═══════════════════════════════════════════════════════════════")
                .comment("Module Management Configuration")
                .comment("模组管理配置")
                .comment("═══════════════════════════════════════════════════════════════")
                .push("modules");

        // ── 禁用模组类型 ──
        DISABLED_MODULE_TYPES = BUILDER
                .comment("Disabled module type list")
                .comment("禁用的模组Type列表")
                .comment("Fill in the module's type value to disable all modules of that type")
                .comment("填入模组的type值即可禁止该类型的所有模组(包括普通版、Prime版、执刑官版)")
                .comment("Disabled modules will: not appear in creative tab, cannot be obtained from random modules")
                .comment("被禁用的模组将: 不出现在创造物品栏、无法通过随机模组获取、无法通过合成获取")
                .comment("Example: [\"vitality\", \"split_chamber\", \"treasure_hunter\"]")
                .comment("示例: [\"vitality\", \"split_chamber\", \"treasure_hunter\"]")
                .defineList("disabledModuleTypes",
                        Arrays.asList(),
                        obj -> obj instanceof String);

        // ── 词条单值范围 ──
        ATTRIBUTE_VALUE_RANGES = BUILDER
                .comment("")
                .comment("Attribute value range for a single module")
                .comment("单个模组词条数值的最小/最大值范围限制")
                .comment("")
                .comment("Format / 格式: \"attributeName:minValue:maxValue\"")
                .comment("")
                .comment("Examples / 示例:")
                .comment("  \"meleeDamage:-0.5:3.0\"")
                .comment("      单个模组的 meleeDamage 词条值限制在 [-0.5, 3.0]")
                .comment("      Clamps meleeDamage on a single module to [-0.5, 3.0]")
                .comment("")
                .comment("  \"meleeCriticalStrikeProbability:0.0:1.0\"")
                .comment("      单个模组的近战暴击率限制在 [0%, 100%]")
                .comment("      Clamps melee crit chance on a single module to [0%, 100%]")
                .comment("")
                .comment("  \"triggerChance:0.0:1.5\"")
                .comment("      单个模组的触发几率限制在 [0%, 150%]")
                .comment("")
                .comment("Note / 注意:")
                .comment("  - min 可以为负数，允许负值词条存在但限制其下限")
                .comment("  - min can be negative to allow negative attributes but with a floor")
                .comment("  - Values outside range are clamped to boundary (no error thrown)")
                .comment("  - 超出范围的值会被 clamp 到边界，不报错")
                .comment("  - Attributes not listed here are unrestricted")
                .comment("  - 未列出的词条不受限制")
                .defineList("attributeValueRanges",
                        Arrays.asList(),
                        obj -> {
                            if (!(obj instanceof String)) return false;
                            String[] parts = ((String) obj).split(":");
                            if (parts.length != 3) return false;
                            try {
                                Double.parseDouble(parts[1]);
                                Double.parseDouble(parts[2]);
                                return true;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        });

        // ── 词条总值上限 ──
        ATTRIBUTE_TOTAL_CAPS = BUILDER
                .comment("")
                .comment("Attribute total cap across all equipped modules")
                .comment("所有装备模组叠加后，某词条总和的上限")
                .comment("")
                .comment("Format / 格式: \"attributeName:maxTotal\"")
                .comment("")
                .comment("Examples / 示例:")
                .comment("  \"meleeDamage:5.0\"")
                .comment("      所有模组的 meleeDamage 叠加总值上限为 5.0 (即 +500%)")
                .comment("      Total meleeDamage across all equipped modules capped at 5.0")
                .comment("")
                .comment("  \"meleeCriticalStrikeProbability:3.0\"")
                .comment("      近战暴击率叠加上限 3.0 (对应代码中三档暴击最高档 +300%)")
                .comment("      Melee crit chance total capped at 3.0 (matches 300%% tier in code)")
                .comment("")
                .comment("  \"triggerChance:5.0\"")
                .comment("      触发几率叠加上限 5.0 (最多5次连触发)")
                .comment("")
                .comment("  \"health:3.0\"")
                .comment("      生命值词条叠加上限 3.0 (+300%%)")
                .comment("")
                .comment("Note / 注意:")
                .comment("  - 上限仅约束正向叠加总值，不影响负值词条")
                .comment("  - Cap only applies to positive sums, negative totals are not clamped")
                .comment("  - 超出上限的总值会被 clamp 到上限")
                .comment("  - 此限制在 killStack 叠层生效之前计算（仅基础模组叠加阶段）")
                .comment("  - This cap is applied before kill stack effects")
                .comment("  - Attributes not listed here are unrestricted")
                .comment("  - 未列出的词条不受限制")
                .defineList("attributeTotalCaps",
                        Arrays.asList(),
                        obj -> {
                            if (!(obj instanceof String)) return false;
                            String[] parts = ((String) obj).split(":");
                            if (parts.length != 2) return false;
                            try {
                                Double.parseDouble(parts[1]);
                                return true;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        });

        BUILDER.pop();
        MODULE_CONFIG = BUILDER.build();
    }

    // ========== 工具方法 / Utility Methods ==========

    /**
     * 检查指定 type 是否被禁用
     * Check if specified type is disabled
     *
     * @param type 模组的 type 值 / module's type value
     * @return true 表示被禁用 / true if disabled
     */
    public static boolean isTypeDisabled(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        if (!configLoaded) {
            return false;
        }
        if (disabledTypesCache == null) {
            rebuildCache();
        }
        return disabledTypesCache != null && disabledTypesCache.contains(type);
    }

    /**
     * 将单个模组词条值 clamp 到配置的 [min, max] 范围内
     * Clamp a single module attribute value to the configured [min, max] range
     *
     * 若该词条未配置范围，则原值返回
     * If no range is configured for this attribute, the original value is returned
     *
     * @param attrName  词条名 / attribute name
     * @param value     原始值 / original value
     * @return          clamp 后的值 / clamped value
     */
    public static double clampAttributeValue(String attrName, double value) {
        if (!configLoaded) {
            return value;
        }
        if (attributeRangeCache == null) {
            rebuildCache();
        }
        double[] range = attributeRangeCache.get(attrName);
        if (range == null) {
            return value;
        }
        // range[0] = min, range[1] = max
        return Math.max(range[0], Math.min(range[1], value));
    }

    /**
     * 将叠加后的词条总值 clamp 到配置的 totalCap 上限
     * Clamp the total (summed) attribute value to the configured totalCap
     *
     * 若该词条未配置上限，则原值返回
     * If no cap is configured for this attribute, the original total is returned
     *
     * 只对正向总值生效，负向总值不受影响
     * Only positive totals are capped; negative totals pass through unchanged
     *
     * @param attrName  词条名 / attribute name
     * @param total     叠加总值 / summed total
     * @return          clamp 后的总值 / clamped total
     */
    public static double clampAttributeTotal(String attrName, double total) {
        if (!configLoaded) {
            return total;
        }
        if (attributeTotalCapCache == null) {
            rebuildCache();
        }
        Double cap = attributeTotalCapCache.get(attrName);
        if (cap == null) {
            return total;
        }
        // 只限制正向总值
        if (total > 0) {
            return Math.min(total, cap);
        }
        return total;
    }

    /**
     * 获取词条单值范围（若未配置返回 null）
     * Get attribute value range (returns null if not configured)
     *
     * @param attrName 词条名 / attribute name
     * @return [min, max] 或 null / [min, max] or null
     */
    public static double[] getAttributeRange(String attrName) {
        if (!configLoaded) {
            return null;
        }
        if (attributeRangeCache == null) {
            rebuildCache();
        }
        return attributeRangeCache.get(attrName);
    }

    /**
     * 获取词条总值上限（若未配置返回 null）
     * Get attribute total cap (returns null if not configured)
     *
     * @param attrName 词条名 / attribute name
     * @return 总值上限 或 null / total cap or null
     */
    public static Double getAttributeTotalCap(String attrName) {
        if (!configLoaded) {
            return null;
        }
        if (attributeTotalCapCache == null) {
            rebuildCache();
        }
        return attributeTotalCapCache.get(attrName);
    }

    /**
     * 检查某词条是否配置了单值范围
     * Check if an attribute has a configured value range
     */
    public static boolean hasAttributeRange(String attrName) {
        return getAttributeRange(attrName) != null;
    }

    /**
     * 检查某词条是否配置了总值上限
     * Check if an attribute has a configured total cap
     */
    public static boolean hasAttributeTotalCap(String attrName) {
        return getAttributeTotalCap(attrName) != null;
    }

    // ========== 缓存重建 / Cache Rebuilding ==========

    /**
     * 重建所有缓存（在配置加载或重载时调用）
     * Rebuild all caches (called on config load or reload)
     */
    public static void rebuildCache() {
        try {
            if (!configLoaded) {
                disabledTypesCache       = new HashSet<>();
                attributeRangeCache      = new HashMap<>();
                attributeTotalCapCache   = new HashMap<>();
                return;
            }

            // ── 重建禁用类型缓存 ──
            List<? extends String> disabledTypes = DISABLED_MODULE_TYPES.get();
            disabledTypesCache = new HashSet<>(disabledTypes);

            // ── 重建词条单值范围缓存 ──
            // Format: "attributeName:minValue:maxValue"
            Map<String, double[]> rangeMap = new HashMap<>();
            List<? extends String> rangeList = ATTRIBUTE_VALUE_RANGES.get();
            for (String entry : rangeList) {
                String[] parts = entry.split(":");
                if (parts.length != 3) {
                    System.err.println("[KuvaLich] Invalid attributeValueRanges entry (expected 3 parts): " + entry);
                    continue;
                }
                try {
                    String attrName = parts[0].trim();
                    double min      = Double.parseDouble(parts[1].trim());
                    double max      = Double.parseDouble(parts[2].trim());

                    if (min > max) {
                        System.err.println("[KuvaLich] attributeValueRanges: min > max for attribute '" + attrName + "', entry skipped: " + entry);
                        continue;
                    }

                    rangeMap.put(attrName, new double[]{min, max});
                } catch (NumberFormatException e) {
                    System.err.println("[KuvaLich] attributeValueRanges: failed to parse numbers in entry: " + entry);
                }
            }
            attributeRangeCache = rangeMap;

            // ── 重建词条总值上限缓存 ──
            // Format: "attributeName:maxTotal"
            Map<String, Double> capMap = new HashMap<>();
            List<? extends String> capList = ATTRIBUTE_TOTAL_CAPS.get();
            for (String entry : capList) {
                String[] parts = entry.split(":");
                if (parts.length != 2) {
                    System.err.println("[KuvaLich] Invalid attributeTotalCaps entry (expected 2 parts): " + entry);
                    continue;
                }
                try {
                    String attrName = parts[0].trim();
                    double maxTotal = Double.parseDouble(parts[1].trim());

                    if (maxTotal < 0) {
                        System.err.println("[KuvaLich] attributeTotalCaps: maxTotal < 0 for attribute '" + attrName + "', entry skipped: " + entry);
                        continue;
                    }

                    capMap.put(attrName, maxTotal);
                } catch (NumberFormatException e) {
                    System.err.println("[KuvaLich] attributeTotalCaps: failed to parse number in entry: " + entry);
                }
            }
            attributeTotalCapCache = capMap;

        } catch (IllegalStateException e) {
            // 配置尚未加载，使用空缓存
            // Config not loaded yet, use empty caches
            disabledTypesCache     = new HashSet<>();
            attributeRangeCache    = new HashMap<>();
            attributeTotalCapCache = new HashMap<>();
        } catch (Exception e) {
            System.err.println("[KuvaLich] Failed to rebuild ModuleConfig cache: " + e.getMessage());
            disabledTypesCache     = new HashSet<>();
            attributeRangeCache    = new HashMap<>();
            attributeTotalCapCache = new HashMap<>();
        }
    }

    // ========== 配置事件监听 / Config Event Listeners ==========

    /**
     * 配置首次加载监听器
     * Config first load listener
     */
    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            configLoaded = true;
            rebuildCache();
        }
    }

    /**
     * 配置热重载监听器（游戏内修改后自动刷新缓存）
     * Config hot-reload listener (auto-refresh cache when modified in-game)
     */
    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            configLoaded = true;
            rebuildCache();
        }
    }
}