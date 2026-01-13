package pers.roinflam.kuvalich.config.custom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义模组数据结构
 * Custom module data structure
 *
 * 用于JSON序列化/反序列化
 * Used for JSON serialization/deserialization
 */
public class CustomModuleData {

    // ========== 单个模组定义 / Single Module Definition ==========

    /**
     * 单个自定义模组的数据结构
     * Data structure for a single custom module
     */
    public static class ModuleEntry {

        /**
         * 模组显示名称（直接显示的文本，支持颜色代码）
         * Module display name (direct text, supports color codes)
         * 使用 & 符号作为颜色代码前缀 / Use & symbol as color code prefix
         * 示例 / Example: "&6&l血腥收割者", "&c炎&6魔&e之刃"
         */
        public String name = "";

        /**
         * 模组类别 / Module category
         * 可选值 / Options: "item"(武器模组), "warframe"(战甲模组)
         */
        public String category = "item";

        /**
         * 稀有度 / Rarity
         * 可选值 / Options: "common"(青铜), "uncommon"(白银), "rare"(黄金), "prime"(Prime)
         */
        public String rarity = "common";

        /**
         * 模组类型（用于同类型冲突检测）
         * Module type (for same-type conflict detection)
         * 同type的模组不能同时装备 / Modules with same type cannot be equipped together
         */
        public String type = "";

        /**
         * 冲突标签列表（用于跨类型冲突检测）
         * Conflict tags (for cross-type conflict detection)
         * 拥有相同标签的模组不能同时装备 / Modules with same tag cannot coexist
         */
        public List<String> conflictTags = new ArrayList<>();

        /**
         * 属性列表 / Attribute list
         * 键为属性名，值为属性数值（百分比形式，1.0 = 100%）
         * Key is attribute name, value is percentage (1.0 = 100%)
         */
        public Map<String, Double> attributes = new HashMap<>();

        /**
         * 是否启用 / Is enabled
         * 设为false可临时禁用而不删除配置 / Set false to disable without deleting config
         */
        public boolean enabled = true;
    }

    // ========== 配置文件根结构 / Config File Root Structure ==========

    /**
     * 配置文件版本（用于未来兼容性）
     * Config file version (for future compatibility)
     */
    public int configVersion = 1;

    /**
     * 自定义模组列表
     * Custom modules list
     */
    public List<ModuleEntry> modules = new ArrayList<>();

    // ========== 静态工厂方法 / Static Factory Methods ==========

    /**
     * 创建默认配置（包含示例模组）
     * Create default config with example modules
     */
    public static CustomModuleData createDefault() {
        CustomModuleData data = new CustomModuleData();

        // ========== 示例武器模组 / Example Item Module ==========
        ModuleEntry exampleItemModule = new ModuleEntry();
        exampleItemModule.name = "&6示例武器模组 Example Weapon Module";
        exampleItemModule.category = "item";
        exampleItemModule.rarity = "rare";
        exampleItemModule.type = "example_sword_type";
        exampleItemModule.conflictTags.add("melee_crit_chance");
        exampleItemModule.attributes.put("meleeDamage", 1.5);
        exampleItemModule.attributes.put("meleeCriticalStrikeProbability", 0.6);
        exampleItemModule.attributes.put("attackSpeed", -0.2);
        exampleItemModule.enabled = false; // 示例默认禁用 / Example disabled by default
        data.modules.add(exampleItemModule);

        // ========== 示例战甲模组 / Example Warframe Module ==========
        ModuleEntry exampleWarframeModule = new ModuleEntry();
        exampleWarframeModule.name = "&9示例战甲模组 Example Warframe Module";
        exampleWarframeModule.category = "warframe";
        exampleWarframeModule.rarity = "uncommon";
        exampleWarframeModule.type = "example_armor_type";
        exampleWarframeModule.attributes.put("health", 0.8);
        exampleWarframeModule.attributes.put("armor", 0.5);
        exampleWarframeModule.enabled = false; // 示例默认禁用 / Example disabled by default
        data.modules.add(exampleWarframeModule);

        return data;
    }
}