// ModuleConfig.java
package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import pers.roinflam.kuvalich.utils.Reference;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 模组配置类
 * 管理模组系统的各项配置，包括禁用模组等
 */
@Config(modid = Reference.MOD_ID, name = Reference.MOD_ID + "/modules")
@Config.LangKey("config." + Reference.MOD_ID + ".modules.title")
public class ModuleConfig {

    @Config.Comment({
            "禁用的模组Type列表",
            "填入模组的type值即可禁止该类型的所有模组（包括普通版、Prime版、执刑官版）",
            "被禁用的模组将：不出现在创造物品栏、无法通过随机模组获取、无法通过合成获取",
            "示例: [\"vitality\", \"split_chamber\", \"treasure_hunter\"]",
            "Disabled module type list",
            "Modules with these types will not appear in creative tab, cannot be obtained from random modules, and cannot be crafted"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".modules.disabledModuleTypes")
    public static String[] disabledModuleTypes = new String[]{};

    // ========== 缓存，避免每次都解析数组 ==========

    /** 禁用类型的缓存Set，提高查询效率 */
    private static Set<String> disabledTypesCache = null;

    /**
     * 检查指定type是否被禁用
     *
     * @param type 模组的type值
     * @return true表示被禁用，false表示可用
     */
    public static boolean isTypeDisabled(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        // 懒加载缓存
        if (disabledTypesCache == null) {
            rebuildCache();
        }
        return disabledTypesCache.contains(type);
    }

    /**
     * 重建禁用类型缓存
     * 在配置变更时调用
     */
    public static void rebuildCache() {
        disabledTypesCache = new HashSet<>(Arrays.asList(disabledModuleTypes));
    }

    /**
     * 配置变更监听器
     * 当玩家在游戏内修改配置时自动刷新缓存
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID)
    public static class ConfigChangeHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(Reference.MOD_ID)) {
                // 同步配置到字段
                ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
                // 重建缓存
                rebuildCache();
            }
        }
    }
}