package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模组配置类
 * Module configuration class
 *
 * 管理模组系统的各项配置,包括禁用模组等
 * Manages module system configurations, including module disabling
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModuleConfig {

    public static final ForgeConfigSpec MODULE_CONFIG;

    /** 禁用的模组类型列表 / Disabled module types list */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DISABLED_MODULE_TYPES;

    /** 禁用类型的缓存Set,提高查询效率 / Cached set for disabled types */
    private static Set<String> disabledTypesCache = null;

    /** 配置是否已加载 / Whether config is loaded */
    private static volatile boolean configLoaded = false;

    static {
        ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

        BUILDER.comment("═══════════════════════════════════════════════════════════════")
                .comment("Module Management Configuration")
                .comment("模组管理配置")
                .comment("═══════════════════════════════════════════════════════════════")
                .push("modules");

        DISABLED_MODULE_TYPES = BUILDER
                .comment("Disabled module type list")
                .comment("禁用的模组Type列表")
                .comment("Fill in the module's type value to disable all modules of that type")
                .comment("填入模组的type值即可禁止该类型的所有模组(包括普通版、Prime版、执刑官版)")
                .comment("Disabled modules will: not appear in creative tab, cannot be obtained from random modules")
                .comment("被禁用的模组将:不出现在创造物品栏、无法通过随机模组获取、无法通过合成获取")
                .comment("Example: [\"vitality\", \"split_chamber\", \"treasure_hunter\"]")
                .comment("示例: [\"vitality\", \"split_chamber\", \"treasure_hunter\"]")
                .defineList("disabledModuleTypes",
                        Arrays.asList(),
                        obj -> obj instanceof String);

        BUILDER.pop();

        MODULE_CONFIG = BUILDER.build();
    }

    /**
     * 检查指定type是否被禁用
     * Check if specified type is disabled
     *
     * @param type 模组的type值 / module's type value
     * @return true表示被禁用,false表示可用 / true if disabled, false if available
     */
    public static boolean isTypeDisabled(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }

        // 如果配置还未加载,返回false(不禁用任何模组)
        // If config not loaded yet, return false (don't disable any modules)
        if (!configLoaded) {
            return false;
        }

        // 懒加载缓存
        // Lazy load cache
        if (disabledTypesCache == null) {
            rebuildCache();
        }

        return disabledTypesCache != null && disabledTypesCache.contains(type);
    }

    /**
     * 重建禁用类型缓存
     * Rebuild disabled types cache
     *
     * 在配置变更时调用
     * Called when config changes
     */
    public static void rebuildCache() {
        try {
            // 检查配置是否已加载
            // Check if config is loaded
            if (!configLoaded) {
                // 配置未加载,使用空缓存
                // Config not loaded, use empty cache
                disabledTypesCache = new HashSet<>();
                return;
            }

            List<? extends String> disabledTypes = DISABLED_MODULE_TYPES.get();
            disabledTypesCache = new HashSet<>(disabledTypes);
        } catch (IllegalStateException e) {
            // 配置还未加载,使用空缓存
            // Config not loaded yet, use empty cache
            disabledTypesCache = new HashSet<>();
        } catch (Exception e) {
            // 其他异常,记录日志并使用空缓存
            // Other exceptions, log and use empty cache
            System.err.println("[KuvaLich] Failed to rebuild module config cache: " + e.getMessage());
            disabledTypesCache = new HashSet<>();
        }
    }

    /**
     * 配置加载监听器
     * Config loading listener
     *
     * 当配置首次加载时设置标志并初始化缓存
     * Set flag and initialize cache when config is first loaded
     */
    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            configLoaded = true;
            rebuildCache();
        }
    }

    /**
     * 配置变更监听器
     * Config change listener
     *
     * 当玩家在游戏内修改配置时自动刷新缓存
     * Automatically refresh cache when player modifies config in-game
     */
    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(Reference.MOD_ID)) {
            configLoaded = true;
            rebuildCache();
        }
    }
}