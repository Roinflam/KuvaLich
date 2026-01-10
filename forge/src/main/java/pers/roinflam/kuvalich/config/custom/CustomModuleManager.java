// CustomModuleManager.java

package pers.roinflam.kuvalich.config.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.config.ModuleConfig;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.LogUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义模组管理器
 * 负责加载、解析、注册自定义模组
 * <p>
 * Custom Module Manager
 * Responsible for loading, parsing, and registering custom modules
 */
public class CustomModuleManager {

    // ========== 单例模式 / Singleton Pattern ==========

    private static final CustomModuleManager INSTANCE = new CustomModuleManager();

    public static CustomModuleManager getInstance() {
        return INSTANCE;
    }

    private CustomModuleManager() {
    }

    // ========== 常量 / Constants ==========

    /**
     * 配置文件路径
     * Config file path
     */
    private static final String CONFIG_PATH = "config/kuvalich/custom_modules.json";

    /**
     * JSON解析器（带格式化输出）
     * JSON parser with pretty printing
     */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    // ========== 缓存数据 / Cached Data ==========

    /**
     * 已加载的配置数据
     * Loaded config data
     */
    private CustomModuleData configData = null;

    /**
     * 自定义武器模组缓存（按稀有度分类）
     * Custom item modules cache (grouped by rarity)
     */
    private final List<ItemStack> customItemCommonModules = new ArrayList<>();
    private final List<ItemStack> customItemUncommonModules = new ArrayList<>();
    private final List<ItemStack> customItemRareModules = new ArrayList<>();
    private final List<ItemStack> customItemPrimeModules = new ArrayList<>();

    /**
     * 自定义战甲模组缓存（按稀有度分类）
     * Custom warframe modules cache (grouped by rarity)
     */
    private final List<ItemStack> customWarframeCommonModules = new ArrayList<>();
    private final List<ItemStack> customWarframeUncommonModules = new ArrayList<>();
    private final List<ItemStack> customWarframeRareModules = new ArrayList<>();
    private final List<ItemStack> customWarframePrimeModules = new ArrayList<>();

    // ========== 初始化方法 / Initialization Methods ==========

    /**
     * 初始化自定义模组系统
     * 在FMLPreInitializationEvent中调用
     * <p>
     * Initialize custom module system
     * Call in FMLPreInitializationEvent
     */
    public void initialize() {
        LogUtil.info("[自定义模组] 开始加载自定义模组配置...");
        LogUtil.info("[CustomModule] Loading custom module config...");

        // 加载或创建配置文件
        loadOrCreateConfig();

        // 解析并缓存模组
        parseAndCacheModules();

        LogUtil.info("[自定义模组] 自定义模组加载完成，共 " + getTotalCustomModuleCount() + " 个");
        LogUtil.info("[CustomModule] Custom modules loaded, total: " + getTotalCustomModuleCount());
    }

    /**
     * 加载或创建配置文件
     * Load or create config file
     */
    private void loadOrCreateConfig() {
        File configFile = new File(CONFIG_PATH);

        // 确保父目录存在
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        if (configFile.exists()) {
            // 读取现有配置
            try {
                // 先读取文件内容，去掉注释后再解析
                String jsonContent = readAndStripComments(configFile);
                configData = GSON.fromJson(jsonContent, CustomModuleData.class);

                // 防止解析结果为null
                if (configData == null) {
                    LogUtil.warn("[自定义模组] 配置文件内容为空，使用默认配置");
                    LogUtil.warn("[CustomModule] Config file is empty, using default");
                    configData = CustomModuleData.createDefault();
                    saveConfig();
                } else {
                    LogUtil.info("[自定义模组] 成功读取配置文件");
                    LogUtil.info("[CustomModule] Config file loaded successfully");
                }
            } catch (Exception e) {
                LogUtil.error("[自定义模组] 读取配置文件失败，将使用默认配置", e);
                LogUtil.error("[CustomModule] Failed to load config, using default", e);
                configData = CustomModuleData.createDefault();
                saveConfig();
            }
        } else {
            // 创建默认配置
            LogUtil.info("[自定义模组] 配置文件不存在，正在创建默认配置...");
            LogUtil.info("[CustomModule] Config file not found, creating default...");
            configData = CustomModuleData.createDefault();
            saveConfig();
        }
    }

    /**
     * 读取文件并去除注释
     * 支持 // 单行注释和 /* * / 多行注释
     * 正确处理字符串内的特殊字符，不会误删字符串内容
     * <p>
     * Read file and strip comments
     * Supports // single-line and /* * / multi-line comments
     * Correctly handles special characters inside strings
     *
     * @param file 要读取的文件 / File to read
     * @return 去除注释后的JSON字符串 / JSON string with comments removed
     * @throws IOException 读取失败时抛出 / Thrown when read fails
     */
    private String readAndStripComments(File file) throws IOException {
        // 先读取整个文件内容
        StringBuilder fileContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
        }

        String content = fileContent.toString();
        StringBuilder result = new StringBuilder();

        boolean inString = false;           // 是否在字符串内
        boolean inSingleLineComment = false; // 是否在单行注释内
        boolean inMultiLineComment = false;  // 是否在多行注释内

        int i = 0;
        while (i < content.length()) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : '\0';

            // ========== 处理换行符 ==========
            if (c == '\n') {
                if (inSingleLineComment) {
                    // 单行注释结束
                    inSingleLineComment = false;
                }
                if (!inMultiLineComment) {
                    result.append(c);
                }
                i++;
                continue;
            }

            // ========== 在单行注释内，跳过直到换行 ==========
            if (inSingleLineComment) {
                i++;
                continue;
            }

            // ========== 在多行注释内，寻找结束标记 */ ==========
            if (inMultiLineComment) {
                if (c == '*' && next == '/') {
                    inMultiLineComment = false;
                    i += 2; // 跳过 */
                } else {
                    i++;
                }
                continue;
            }

            // ========== 在字符串内 ==========
            if (inString) {
                result.append(c);
                if (c == '\\' && next != '\0') {
                    // 转义字符，下一个字符直接追加，不做判断
                    result.append(next);
                    i += 2;
                } else if (c == '"') {
                    // 字符串结束（JSON只用双引号）
                    inString = false;
                    i++;
                } else {
                    i++;
                }
                continue;
            }

            // ========== 不在字符串、不在注释内 ==========

            // 检查字符串开始
            if (c == '"') {
                inString = true;
                result.append(c);
                i++;
                continue;
            }

            // 检查注释开始
            if (c == '/') {
                if (next == '/') {
                    // 单行注释开始
                    inSingleLineComment = true;
                    i += 2;
                    continue;
                } else if (next == '*') {
                    // 多行注释开始
                    inMultiLineComment = true;
                    i += 2;
                    continue;
                }
            }

            // 普通字符，直接追加
            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * 保存配置到文件
     * Save config to file
     */
    public void saveConfig() {
        File configFile = new File(CONFIG_PATH);

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(configFile), StandardCharsets.UTF_8)) {

            // 写入头部注释
            writer.write(generateConfigHeader());
            writer.write(GSON.toJson(configData));

            LogUtil.info("[自定义模组] 配置文件保存成功: " + configFile.getAbsolutePath());
            LogUtil.info("[CustomModule] Config saved: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            LogUtil.error("[自定义模组] 保存配置文件失败", e);
            LogUtil.error("[CustomModule] Failed to save config", e);
        }
    }

    /**
     * 生成配置文件头部注释
     * Generate config file header comments
     */
    private String generateConfigHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("// 赤毒玄骸 - 自定义模组配置文件\n");
        sb.append("// Kuva Lich - Custom Modules Configuration\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("//\n");
        sb.append("// 【配置说明 / Configuration Guide】\n");
        sb.append("//\n");
        sb.append("// name: 模组显示名称，支持颜色代码\n");
        sb.append("//       Module display name, supports color codes\n");
        sb.append("//       示例 / Example: \"&6血腥收割者\", \"&c&l炎魔之刃\"\n");
        sb.append("//\n");
        sb.append("// category: 模组类别 / Module category\n");
        sb.append("//   - \"item\": 武器模组 / Weapon module\n");
        sb.append("//   - \"warframe\": 战甲模组 / Warframe module\n");
        sb.append("//\n");
        sb.append("// rarity: 稀有度 / Rarity\n");
        sb.append("//   - \"common\": 青铜 / Bronze\n");
        sb.append("//   - \"uncommon\": 白银 / Silver\n");
        sb.append("//   - \"rare\": 黄金 / Gold\n");
        sb.append("//   - \"prime\": Prime\n");
        sb.append("//   - \"galvanized\": 镀层 / Galvanized\n");
        sb.append("//   - \"executioner\": 执行官 / Executioner\n");
        sb.append("//\n");
        sb.append("// type: 模组类型，同类型不能共存 / Module type, same type cannot coexist\n");
        sb.append("//\n");
        sb.append("// conflictTags: 冲突标签，拥有相同标签的模组不能共存\n");
        sb.append("//               Conflict tags, modules with same tag cannot coexist\n");
        sb.append("//\n");
        sb.append("// attributes: 属性列表，数值为百分比（1.0 = 100%）\n");
        sb.append("//             Attribute list, values are percentage (1.0 = 100%)\n");
        sb.append("//\n");
        sb.append("// enabled: 是否启用，设为false可临时禁用\n");
        sb.append("//          Is enabled, set false to disable\n");
        sb.append("//\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("// 【颜色代码 / Color Codes】\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("//\n");
        sb.append("// 使用 & 符号 + 代码，例如 &6 表示金色\n");
        sb.append("// Use & symbol + code, e.g. &6 for gold color\n");
        sb.append("//\n");
        sb.append("// --- 颜色 / Colors ---\n");
        sb.append("// &0 黑色/Black       &1 深蓝/Dark Blue   &2 深绿/Dark Green  &3 深青/Dark Aqua\n");
        sb.append("// &4 深红/Dark Red    &5 紫色/Purple      &6 金色/Gold        &7 灰色/Gray\n");
        sb.append("// &8 深灰/Dark Gray   &9 蓝色/Blue        &a 绿色/Green       &b 青色/Aqua\n");
        sb.append("// &c 红色/Red         &d 粉色/Pink        &e 黄色/Yellow      &f 白色/White\n");
        sb.append("//\n");
        sb.append("// --- 格式 / Formats ---\n");
        sb.append("// &l 粗体/Bold        &m 删除线/Strike    &n 下划线/Underline\n");
        sb.append("// &o 斜体/Italic      &r 重置/Reset\n");
        sb.append("//\n");
        sb.append("// 组合示例 / Combination Examples:\n");
        sb.append("// \"&6&l金色粗体\" = 金色粗体文字 / Gold bold text\n");
        sb.append("// \"&c炎&6魔&e之刃\" = 多色文字 / Multi-color text\n");
        sb.append("// \"&5&o神秘紫卡\" = 紫色斜体 / Purple italic text\n");
        sb.append("//\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("// 【可用武器属性 / Available Item Attributes】\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("//\n");
        sb.append("// --- 基础伤害 / Base Damage ---\n");
        sb.append("// meleeDamage: 近战伤害 / Melee damage\n");
        sb.append("// remoteDamage: 远程伤害 / Remote damage\n");
        sb.append("// arrowDamage: 箭矢伤害 / Arrow damage\n");
        sb.append("// projectileDamage: 弹射物伤害 / Projectile damage\n");
        sb.append("// magicDamage: 魔法伤害 / Magic damage\n");
        sb.append("//\n");
        sb.append("// --- 攻击属性 / Attack Attributes ---\n");
        sb.append("// multishot: 多重射击 / Multishot\n");
        sb.append("// attackSpeed: 攻击速度 / Attack speed\n");
        sb.append("// attackRange: 攻击范围 / Attack range\n");
        sb.append("// firing_rate: 射速 / Firing rate\n");
        sb.append("// bursting_radius: 爆炸半径 / Bursting radius\n");
        sb.append("//\n");
        sb.append("// --- 暴击属性 / Critical Attributes ---\n");
        sb.append("// meleeCriticalStrikeProbability: 近战暴击率 / Melee crit chance\n");
        sb.append("// meleeCriticalStrikeMultiplier: 近战暴击倍率 / Melee crit multiplier\n");
        sb.append("// remoteCriticalStrikeProbability: 远程暴击率 / Remote crit chance\n");
        sb.append("// remoteCriticalStrikeMultiplier: 远程暴击倍率 / Remote crit multiplier\n");
        sb.append("//\n");
        sb.append("// --- 冲刺攻击 / Dash Attack ---\n");
        sb.append("// dashMeleeCriticalStrikeProbability: 冲刺暴击率 / Dash crit chance\n");
        sb.append("// dashAttackRange: 冲刺攻击范围 / Dash attack range\n");
        sb.append("// dashTriggerChance: 冲刺触发几率 / Dash trigger chance\n");
        sb.append("//\n");
        sb.append("// --- 触发属性 / Trigger Attributes ---\n");
        sb.append("// triggerChance: 触发几率 / Trigger chance\n");
        sb.append("// triggerTime: 触发持续时间 / Trigger duration\n");
        sb.append("//\n");
        sb.append("// --- 元素伤害 / Elemental Damage ---\n");
        sb.append("// fire: 火焰 / Fire\n");
        sb.append("// ice: 冰冻 / Ice\n");
        sb.append("// poison: 毒素 / Poison\n");
        sb.append("// electricity: 电击 / Electricity\n");
        sb.append("//\n");
        sb.append("// --- 复合元素 / Combined Elements ---\n");
        sb.append("// gas: 毒气(火+毒) / Gas (Fire+Poison)\n");
        sb.append("// radiation: 辐射(火+电) / Radiation (Fire+Electricity)\n");
        sb.append("// magnetic: 磁力(冰+电) / Magnetic (Ice+Electricity)\n");
        sb.append("// corrosion: 腐蚀(毒+电) / Corrosion (Poison+Electricity)\n");
        sb.append("// explosion: 爆炸(火+冰) / Explosion (Fire+Ice)\n");
        sb.append("// virus: 病毒(冰+毒) / Virus (Ice+Poison)\n");
        sb.append("//\n");
        sb.append("// --- 物理伤害 / Physical Damage ---\n");
        sb.append("// slash: 切割 / Slash\n");
        sb.append("// puncture: 穿刺 / Puncture\n");
        sb.append("// impact: 冲击 / Impact\n");
        sb.append("//\n");
        sb.append("// --- 克制伤害 / Bane Damage ---\n");
        sb.append("// bane_of_undefined: 未定义生物克星 / Bane of undefined\n");
        sb.append("// bane_of_undead: 亡灵克星 / Bane of undead\n");
        sb.append("// bane_of_arthropod: 节肢动物克星 / Bane of arthropod\n");
        sb.append("// bane_of_illager: 灾厄村民克星 / Bane of illager\n");
        sb.append("//\n");
        sb.append("// --- 特殊属性 / Special Attributes ---\n");
        sb.append("// baseDamageWhenNotCriticalStrike: 非暴击时基础伤害 / Base damage when not crit\n");
        sb.append("//\n");
        sb.append("// --- 击杀叠层(武器) / Kill Stacks (Weapon) ---\n");
        sb.append("// killStackBaseDamage: 击杀叠加基础伤害 / Kill stack base damage\n");
        sb.append("// killStackMultishot: 击杀叠加多重射击 / Kill stack multishot\n");
        sb.append("// killStackMeleeCriticalMultiplier: 击杀叠加近战暴伤 / Kill stack melee crit mult\n");
        sb.append("// killStackTriggerChance: 击杀叠加触发几率 / Kill stack trigger chance\n");
        sb.append("// killStackAttackRange: 击杀叠加攻击范围 / Kill stack attack range\n");
        sb.append("// killStackAttackSpeed: 击杀叠加攻击速度 / Kill stack attack speed\n");
        sb.append("// killStackBurstingRadius: 击杀叠加爆炸半径 / Kill stack bursting radius\n");
        sb.append("// killStackFiringRate: 击杀叠加射速 / Kill stack firing rate\n");
        sb.append("//\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("// 【可用战甲属性 / Available Warframe Attributes】\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("//\n");
        sb.append("// --- 基础属性 / Base Attributes ---\n");
        sb.append("// health: 生命值 / Health\n");
        sb.append("// shield: 护盾 / Shield\n");
        sb.append("// armor: 护甲 / Armor\n");
        sb.append("// sprintSpeed: 冲刺速度 / Sprint speed\n");
        sb.append("//\n");
        sb.append("// --- 固定上限属性 / Fixed Cap Attributes ---\n");
        sb.append("// fixedHealth: 固定生命值上限 / Fixed health cap\n");
        sb.append("// fixedShield: 固定护盾上限 / Fixed shield cap\n");
        sb.append("// fixedArmor: 固定护甲上限 / Fixed armor cap\n");
        sb.append("//\n");
        sb.append("// --- 护盾属性 / Shield Attributes ---\n");
        sb.append("// shieldRecoveryRate: 护盾恢复速率 / Shield recovery rate\n");
        sb.append("// shieldRecoveryDelay: 护盾恢复延迟(负值=更快) / Shield recovery delay (negative=faster)\n");
        sb.append("//\n");
        sb.append("// --- 抗性属性 / Resistance Attributes ---\n");
        sb.append("// knockbackResistance: 击退抗性 / Knockback resistance\n");
        sb.append("// fireProtection: 火焰抗性 / Fire protection\n");
        sb.append("// electricProtection: 电击抗性 / Electric protection\n");
        sb.append("// homologousProtection: 同源抗性 / Homologous protection\n");
        sb.append("// fallProtection: 摔落保护 / Fall protection\n");
        sb.append("//\n");
        sb.append("// --- 工具属性 / Tool Attributes ---\n");
        sb.append("// reachDistance: 触及距离 / Reach distance\n");
        sb.append("// diggingSpeed: 挖掘速度 / Digging speed\n");
        sb.append("//\n");
        sb.append("// --- 其他属性 / Other Attributes ---\n");
        sb.append("// responseRate: 生命恢复倍率 / Health recovery rate\n");
        sb.append("// itemDropMultiplier: 物品掉落倍率 / Item drop multiplier\n");
        sb.append("// jumpBoost: 跳跃提升 / Jump boost\n");
        sb.append("//\n");
        sb.append("// --- 击杀叠层(战甲) / Kill Stacks (Warframe) ---\n");
        sb.append("// killStackHealth: 击杀叠加生命 / Kill stack health\n");
        sb.append("// killStackShield: 击杀叠加护盾 / Kill stack shield\n");
        sb.append("// killStackArmor: 击杀叠加护甲 / Kill stack armor\n");
        sb.append("// killStackSprintSpeed: 击杀叠加冲刺速度 / Kill stack sprint speed\n");
        sb.append("// killStackShieldRecoveryRate: 击杀叠加护盾恢复 / Kill stack shield recovery\n");
        sb.append("// killStackShieldRecoveryDelay: 击杀叠加护盾延迟 / Kill stack shield delay\n");
        sb.append("// killStackFireProtection: 击杀叠加火抗 / Kill stack fire protection\n");
        sb.append("// killStackElectricProtection: 击杀叠加电抗 / Kill stack electric protection\n");
        sb.append("// killStackHomologousProtection: 击杀叠加同源抗性 / Kill stack homologous protection\n");
        sb.append("// killStackResponseRate: 击杀叠加生命恢复 / Kill stack response rate\n");
        sb.append("// killStackItemDropMultiplier: 击杀叠加掉落 / Kill stack item drop\n");
        sb.append("// killStackDiggingSpeed: 击杀叠加挖掘速度 / Kill stack digging speed\n");
        sb.append("//\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("// 【常用冲突标签 / Common Conflict Tags】\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("//\n");
        sb.append("// melee_crit_chance: 近战暴击率类 / Melee crit chance category\n");
        sb.append("// melee_crit_mult: 近战暴伤类 / Melee crit mult category\n");
        sb.append("// remote_crit_chance: 远程暴击率类 / Remote crit chance category\n");
        sb.append("// remote_crit_mult: 远程暴伤类 / Remote crit mult category\n");
        sb.append("// multishot: 多重射击类 / Multishot category\n");
        sb.append("// attack_range: 攻击范围类 / Attack range category\n");
        sb.append("// firing_rate: 射速类 / Firing rate category\n");
        sb.append("// dash_crit_chance: 冲刺暴击类 / Dash crit category\n");
        sb.append("// dash_range: 冲刺范围类 / Dash range category\n");
        sb.append("// dash_trigger: 冲刺触发类 / Dash trigger category\n");
        sb.append("// item_drop_multiplier: 掉落倍率类 / Item drop category\n");
        sb.append("// digging_speed: 挖掘速度类 / Digging speed category\n");
        sb.append("//\n");
        sb.append("// ═══════════════════════════════════════════════════════════════\n");
        sb.append("\n");
        return sb.toString();
    }

    // ========== 解析方法 / Parsing Methods ==========

    /**
     * 解析配置并缓存模组
     * Parse config and cache modules
     */
    private void parseAndCacheModules() {
        // 清空缓存
        clearAllCaches();

        if (configData == null || configData.modules == null) {
            return;
        }

        for (CustomModuleData.ModuleEntry entry : configData.modules) {
            // 跳过禁用的模组
            if (!entry.enabled) {
                continue;
            }

            // 检查type是否被全局禁用
            if (entry.type != null && !entry.type.isEmpty() && ModuleConfig.isTypeDisabled(entry.type)) {
                LogUtil.debug("[自定义模组] 跳过被禁用的模组: " + entry.name);
                continue;
            }

            try {
                ItemStack moduleStack = createModuleStack(entry);
                if (moduleStack != null && !moduleStack.isEmpty()) {
                    cacheModule(entry, moduleStack);
                }
            } catch (Exception e) {
                LogUtil.error("[自定义模组] 解析模组失败: " + entry.name, e);
            }
        }
    }

    /**
     * 根据配置创建模组ItemStack
     * Create module ItemStack from config entry
     */
    private ItemStack createModuleStack(CustomModuleData.ModuleEntry entry) {
        // 获取对应的物品
        Item moduleItem = getModuleItem(entry.category, entry.rarity);
        if (moduleItem == null) {
            LogUtil.warn("[自定义模组] 无效的类别或稀有度: " + entry.category + "/" + entry.rarity);
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(moduleItem);

        // ========== 处理颜色代码并设置显示名称 / Process color codes and set display name ==========
        String displayName = translateColorCodes(entry.name);
        stack.setStackDisplayName(displayName);
        stack.setTranslatableName("item.custommodule." + entry.rarity.toLowerCase() + ".name");

        // 添加属性
        for (Map.Entry<String, Double> attr : entry.attributes.entrySet()) {
            ModuleBase.addAttributes(stack, attr.getKey(), attr.getValue());
        }

        // 设置type
        if (entry.type != null && !entry.type.isEmpty()) {
            ModuleBase.setType(stack, entry.type);
        }

        // 设置冲突标签
        if (entry.conflictTags != null && !entry.conflictTags.isEmpty()) {
            ModuleBase.setConflictTags(stack, entry.conflictTags);
        }

        return stack;
    }

    /**
     * 转换颜色代码
     * 支持 & 和 § 两种格式
     * Convert color codes
     * Supports both & and § formats
     *
     * @param text 原始文本 / Original text
     * @return 转换后的文本 / Converted text
     */
    private String translateColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 将 & 转换为 § （Minecraft颜色代码符号）
        // Convert & to § (Minecraft color code symbol)
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) > -1) {
                chars[i] = '§'; // § 符号
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * 根据类别和稀有度获取对应的物品
     * Get module item by category and rarity
     */
    private Item getModuleItem(String category, String rarity) {
        if ("item".equalsIgnoreCase(category)) {
            switch (rarity.toLowerCase()) {
                case "common":
                    return KuvaLichItems.ITEM_COMMON_MODULE;
                case "uncommon":
                    return KuvaLichItems.ITEM_UNCOMMON_MODULE;
                case "rare":
                    return KuvaLichItems.ITEM_RARE_MODULE;
                case "prime":
                case "galvanized":
                    return KuvaLichItems.ITEM_PRIME_MODULE;
                default:
                    return null;
            }
        } else if ("warframe".equalsIgnoreCase(category)) {
            switch (rarity.toLowerCase()) {
                case "common":
                    return KuvaLichItems.WARFRAME_COMMON_MODULE;
                case "uncommon":
                    return KuvaLichItems.WARFRAME_UNCOMMON_MODULE;
                case "rare":
                    return KuvaLichItems.WARFRAME_RARE_MODULE;
                case "prime":
                case "executioner":
                    return KuvaLichItems.WARFRAME_PRIME_MODULE;
                default:
                    return null;
            }
        }
        return null;
    }

    /**
     * 将模组缓存到对应列表
     * Cache module to corresponding list
     */
    private void cacheModule(CustomModuleData.ModuleEntry entry, ItemStack stack) {
        if ("item".equalsIgnoreCase(entry.category)) {
            switch (entry.rarity.toLowerCase()) {
                case "common":
                    customItemCommonModules.add(stack);
                    break;
                case "uncommon":
                    customItemUncommonModules.add(stack);
                    break;
                case "rare":
                    customItemRareModules.add(stack);
                    break;
                case "prime":
                case "galvanized":
                    customItemPrimeModules.add(stack);
                    break;
            }
        } else if ("warframe".equalsIgnoreCase(entry.category)) {
            switch (entry.rarity.toLowerCase()) {
                case "common":
                    customWarframeCommonModules.add(stack);
                    break;
                case "uncommon":
                    customWarframeUncommonModules.add(stack);
                    break;
                case "rare":
                    customWarframeRareModules.add(stack);
                    break;
                case "prime":
                case "executioner":
                    customWarframePrimeModules.add(stack);
                    break;
            }
        }

        LogUtil.debug("[自定义模组] 已注册: " + entry.name + " (" + entry.category + "/" + entry.rarity + ")");
    }

    /**
     * 清空所有缓存
     * Clear all caches
     */
    private void clearAllCaches() {
        customItemCommonModules.clear();
        customItemUncommonModules.clear();
        customItemRareModules.clear();
        customItemPrimeModules.clear();
        customWarframeCommonModules.clear();
        customWarframeUncommonModules.clear();
        customWarframeRareModules.clear();
        customWarframePrimeModules.clear();
    }

    // ========== 公共访问方法 / Public Access Methods ==========

    /**
     * 获取自定义武器模组（用于创造物品栏和随机获取）
     * Get custom item modules (for creative tab and random obtaining)
     */
    public List<ItemStack> getCustomItemModules(EnumRarity rarity) {
        switch (rarity) {
            case COMMON:
                return new ArrayList<>(customItemCommonModules);
            case UNCOMMON:
                return new ArrayList<>(customItemUncommonModules);
            case RARE:
                return new ArrayList<>(customItemRareModules);
            case EPIC:
                return new ArrayList<>(customItemPrimeModules);
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 获取自定义战甲模组（用于创造物品栏和随机获取）
     * Get custom warframe modules (for creative tab and random obtaining)
     */
    public List<ItemStack> getCustomWarframeModules(EnumRarity rarity) {
        switch (rarity) {
            case COMMON:
                return new ArrayList<>(customWarframeCommonModules);
            case UNCOMMON:
                return new ArrayList<>(customWarframeUncommonModules);
            case RARE:
                return new ArrayList<>(customWarframeRareModules);
            case EPIC:
                return new ArrayList<>(customWarframePrimeModules);
            default:
                return new ArrayList<>();
        }
    }

    /**
     * 添加自定义武器模组到创造物品栏
     * Add custom item modules to creative tab
     *
     * @param items  创造物品栏列表 / Creative tab item list
     * @param rarity 稀有度 / Rarity
     */
    public void addCustomItemModulesToCreativeTab(NonNullList<ItemStack> items, EnumRarity rarity) {
        for (ItemStack stack : getCustomItemModules(rarity)) {
            items.add(stack.copy());
        }
    }

    /**
     * 添加自定义战甲模组到创造物品栏
     * Add custom warframe modules to creative tab
     *
     * @param items  创造物品栏列表 / Creative tab item list
     * @param rarity 稀有度 / Rarity
     */
    public void addCustomWarframeModulesToCreativeTab(NonNullList<ItemStack> items, EnumRarity rarity) {
        for (ItemStack stack : getCustomWarframeModules(rarity)) {
            items.add(stack.copy());
        }
    }

    /**
     * 添加自定义武器模组到随机列表
     * Add custom item modules to random list (for random module drops)
     *
     * @param moduleList 模组列表 / Module list
     * @param rarity     稀有度 / Rarity
     */
    public void addCustomItemModulesToRandomList(List<ItemStack> moduleList, EnumRarity rarity) {
        for (ItemStack stack : getCustomItemModules(rarity)) {
            moduleList.add(stack.copy());
        }
    }

    /**
     * 添加自定义战甲模组到随机列表
     * Add custom warframe modules to random list
     *
     * @param moduleList 模组列表 / Module list
     * @param rarity     稀有度 / Rarity
     */
    public void addCustomWarframeModulesToRandomList(List<ItemStack> moduleList, EnumRarity rarity) {
        for (ItemStack stack : getCustomWarframeModules(rarity)) {
            moduleList.add(stack.copy());
        }
    }

    /**
     * 获取自定义模组总数
     * Get total custom module count
     */
    public int getTotalCustomModuleCount() {
        return customItemCommonModules.size()
                + customItemUncommonModules.size()
                + customItemRareModules.size()
                + customItemPrimeModules.size()
                + customWarframeCommonModules.size()
                + customWarframeUncommonModules.size()
                + customWarframeRareModules.size()
                + customWarframePrimeModules.size();
    }

    /**
     * 重新加载配置（用于热重载）
     * Reload config (for hot reload)
     */
    public void reload() {
        LogUtil.info("[自定义模组] 正在重新加载配置...");
        LogUtil.info("[CustomModule] Reloading config...");
        loadOrCreateConfig();
        parseAndCacheModules();
        LogUtil.info("[自定义模组] 重新加载完成，共 " + getTotalCustomModuleCount() + " 个模组");
        LogUtil.info("[CustomModule] Reload complete, total: " + getTotalCustomModuleCount() + " modules");
    }
}