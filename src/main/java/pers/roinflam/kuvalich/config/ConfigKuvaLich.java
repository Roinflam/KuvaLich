package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 赤毒玄骸配置类
 *
 * 包含赤毒玄骸系统的所有配置项
 * 配置文件位置：config/kuvalich.cfg
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber
@Config(modid = Reference.MOD_ID, category = "KuvaLich")
public final class ConfigKuvaLich {

    // ========== 调试配置 ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Enable Detailed Debug Logging",
            "     Logs all calculations, events, and logic processes",
            "     • true = Enabled - Logs all detailed information",
            "     • false = Disabled (default) - Only logs important events",
            "     ⚠ WARNING: Generates extensive log output",
            "     Performance impact is minimal, but log files grow rapidly",
            "",
            "[中文] 启用详细调试日志",
            "     记录所有计算、事件和逻辑流程",
            "     • true = 启用 - 记录所有详细信息",
            "     • false = 禁用(默认) - 仅记录重要事件",
            "     ⚠ 警告: 产生大量日志输出",
            "     性能影响小，但日志文件会快速增长",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.enableDetailedLogging")
    public static boolean enableDetailedLogging = false;

    // ========== 解密进度配置 ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Minimum Decryption Progress Per Kill",
            "     Points gained when killing Kuva enemies",
            "     • 1 = Very slow progression",
            "     • 3 = Normal progression (default)",
            "     • 5 = Fast progression",
            "     Must be less than or equal to maximum progress",
            "",
            "[中文] 每次击杀获得的最小解密进度",
            "     击杀赤毒敌人时获得的进度点数",
            "     • 1 = 非常慢的进度",
            "     • 3 = 正常进度(默认)",
            "     • 5 = 快速进度",
            "     必须小于或等于最大进度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.minDecryptionProgress")
    @Config.RangeInt(min = 0)
    public static int minDecryptionProgress = 3;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Maximum Decryption Progress Per Kill",
            "     Maximum points that can be gained per kill",
            "     • 3 = Slow progression",
            "     • 5 = Normal progression (default)",
            "     • 10 = Fast progression",
            "     Must be greater than or equal to minimum progress",
            "",
            "[中文] 每次击杀获得的最大解密进度",
            "     每次击杀可获得的最大进度点数",
            "     • 3 = 慢速进度",
            "     • 5 = 正常进度(默认)",
            "     • 10 = 快速进度",
            "     必须大于或等于最小进度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxDecryptionProgress")
    @Config.RangeInt(min = 0)
    public static int maxDecryptionProgress = 5;

    // ========== 解密阶段配置 ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] First Stage Decryption Requirement",
            "     Total points needed to complete the first stage",
            "     • 30 = Easy difficulty",
            "     • 60 = Normal difficulty (default)",
            "     • 120 = Hard difficulty",
            "     Determines how quickly players can progress",
            "",
            "[中文] 第一阶段解密所需点数",
            "     完成第一阶段所需的总点数",
            "     • 30 = 简单难度",
            "     • 60 = 正常难度(默认)",
            "     • 120 = 困难难度",
            "     决定玩家推进速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.firstStage")
    @Config.RangeInt(min = 1)
    public static int firstStage = 60;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Second Stage Decryption Requirement",
            "     Total points needed to complete the second stage",
            "     • 42 = Easy difficulty",
            "     • 84 = Normal difficulty (default)",
            "     • 168 = Hard difficulty",
            "     Should be higher than first stage",
            "",
            "[中文] 第二阶段解密所需点数",
            "     完成第二阶段所需的总点数",
            "     • 42 = 简单难度",
            "     • 84 = 正常难度(默认)",
            "     • 168 = 困难难度",
            "     应该高于第一阶段",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.secondStage")
    @Config.RangeInt(min = 1)
    public static int secondStage = 84;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Third Stage Decryption Requirement",
            "     Total points needed to complete the third stage",
            "     • 60 = Easy difficulty",
            "     • 120 = Normal difficulty (default)",
            "     • 240 = Hard difficulty",
            "     Final stage requirement - highest difficulty",
            "",
            "[中文] 第三阶段解密所需点数",
            "     完成第三阶段所需的总点数",
            "     • 60 = 简单难度",
            "     • 120 = 正常难度(默认)",
            "     • 240 = 困难难度",
            "     最终阶段要求 - 最高难度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.thirdStage")
    @Config.RangeInt(min = 1)
    public static int thirdStage = 120;

    // ========== 战斗倍率配置 ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Master Kill Point Multiplier",
            "     Extra points multiplier when killing Kuva Master",
            "     • 1.0 = No bonus",
            "     • 5.0 = 5x points (default)",
            "     • 10.0 = 10x points",
            "     Rewards players for defeating stronger enemies",
            "",
            "[中文] 击杀赤毒玄骸的额外点数倍率",
            "     击杀赤毒玄骸时的额外点数倍率",
            "     • 1.0 = 无加成",
            "     • 5.0 = 5倍点数(默认)",
            "     • 10.0 = 10倍点数",
            "     奖励击败强敌的玩家",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.masterPotionMultiplier")
    @Config.RangeDouble(min = 1)
    public static double masterPotionMultiplier = 5;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Display All Damage Numbers",
            "     Shows damage numbers for all attacks",
            "     • true = Show all damage (can be cluttered)",
            "     • false = Hide damage numbers (default)",
            "     Useful for debugging and testing",
            "",
            "[中文] 显示所有伤害数字",
            "     显示所有攻击的伤害数字",
            "     • true = 显示所有伤害(可能杂乱)",
            "     • false = 隐藏伤害数字(默认)",
            "     用于调试和测试",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.damageDisplay")
    public static boolean damageDisplay = false;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Damage Modification Priority",
            "     Sets when damage modifications are applied",
            "     • true = Apply last (default) - Overrides other mods",
            "     • false = Apply first - Other mods can override",
            "     Affects compatibility with other damage-modifying mods",
            "",
            "[中文] 伤害修正优先级",
            "     设置伤害修正的应用时机",
            "     • true = 最后应用(默认) - 覆盖其他模组",
            "     • false = 优先应用 - 可被其他模组覆盖",
            "     影响与其他伤害修正模组的兼容性",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.damagePriority")
    public static boolean damagePriority = true;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Battle Damage Boost Per Second",
            "     Damage increase rate during combat",
            "     • 0.05 = 5% per second",
            "     • 0.075 = 7.5% per second (default)",
            "     • 0.10 = 10% per second",
            "     Applies to both Kuva Slave and Kuva Master",
            "     Makes longer fights progressively harder",
            "",
            "[中文] 战斗中每秒伤害提升",
            "     战斗期间的伤害增长速率",
            "     • 0.05 = 每秒5%",
            "     • 0.075 = 每秒7.5%(默认)",
            "     • 0.10 = 每秒10%",
            "     同时应用于赤毒奴仆和赤毒玄骸",
            "     使战斗时间越长难度越高",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.battleBoost")
    @Config.RangeDouble(min = 0)
    public static float battleBoost = 0.075f;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Damage Reduction Per Failed Decryption",
            "     Reduces player damage after each failed attempt",
            "     • 0.05 = 5% reduction",
            "     • 0.10 = 10% reduction (default)",
            "     • 0.20 = 20% reduction",
            "     Maximum reduction is 99% (10 failures)",
            "     Punishes repeated failures",
            "",
            "[中文] 每次解密失败的伤害削减",
            "     每次失败后玩家伤害降低",
            "     • 0.05 = 降低5%",
            "     • 0.10 = 降低10%(默认)",
            "     • 0.20 = 降低20%",
            "     最大削减99%(失败10次)",
            "     惩罚重复失败",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.reducedDamage")
    @Config.RangeDouble(min = 0)
    public static float reducedDamage = 0.1f;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Enemy Damage Increase Per Failed Decryption",
            "     Increases enemy damage after each failed attempt",
            "     • 0.10 = 10% increase",
            "     • 0.25 = 25% increase (default)",
            "     • 0.50 = 50% increase",
            "     Stacks infinitely - be careful!",
            "     Balances the damage reduction players receive",
            "",
            "[中文] 每次解密失败敌人伤害提升",
            "     每次失败后敌人伤害增加",
            "     • 0.10 = 增加10%",
            "     • 0.25 = 增加25%(默认)",
            "     • 0.50 = 增加50%",
            "     无限叠加 - 小心!",
            "     平衡玩家获得的伤害削减",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.increaseDamage")
    @Config.RangeDouble(min = 0)
    public static float increaseDamage = 0.25f;

    // ========== 武器等级配置 ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Weapon Level Benchmark",
            "     Base level for weapon stat calculations",
            "     • 30 = Lower baseline",
            "     • 45 = Normal baseline (default)",
            "     • 60 = Higher baseline",
            "     Below this = inferior equipment",
            "     Above this = superior equipment",
            "",
            "[中文] 武器等级基准",
            "     武器属性计算的基础等级",
            "     • 30 = 较低基准",
            "     • 45 = 正常基准(默认)",
            "     • 60 = 较高基准",
            "     低于此值 = 劣质装备",
            "     高于此值 = 优质装备",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.benchmarkLevel")
    @Config.RangeInt(min = 0)
    public static int benchmarkLevel = 45;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Base Minimum Weapon Level",
            "     Lowest level weapon obtainable without upgrades",
            "     • 1 = Very weak starting weapons",
            "     • 5 = Weak starting weapons (default)",
            "     • 10 = Average starting weapons",
            "     Players start here before any Requiem Gate upgrades",
            "",
            "[中文] 基础最低武器等级",
            "     未升级时可获得的最低武器等级",
            "     • 1 = 非常弱的初始武器",
            "     • 5 = 较弱初始武器(默认)",
            "     • 10 = 一般初始武器",
            "     玩家在灭骸之扉升级前的起始点",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.baseMinimumLevel")
    @Config.RangeInt(min = 0)
    public static int baseMinimumLevel = 5;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Base Maximum Weapon Level",
            "     Highest level weapon obtainable without upgrades",
            "     • 15 = Low cap",
            "     • 25 = Normal cap (default)",
            "     • 35 = High cap",
            "     Must be upgraded via Requiem Gate for higher levels",
            "",
            "[中文] 基础最高武器等级",
            "     未升级时可获得的最高武器等级",
            "     • 15 = 较低上限",
            "     • 25 = 正常上限(默认)",
            "     • 35 = 较高上限",
            "     需通过灭骸之扉升级获得更高等级",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.baseMaximumLevel")
    @Config.RangeInt(min = 0)
    public static int baseMaximumLevel = 25;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Minimum Level Cap Increase Per Upgrade",
            "     Minimum boost to level floor after successful decryption",
            "     • 1 = Slow progression",
            "     • 3 = Normal progression (default)",
            "     • 5 = Fast progression",
            "     Increases the minimum possible weapon level",
            "",
            "[中文] 每次升级的最小等级下限提升",
            "     成功解密后等级下限的最小提升量",
            "     • 1 = 慢速提升",
            "     • 3 = 正常提升(默认)",
            "     • 5 = 快速提升",
            "     提高可能的最低武器等级",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.minimumLevelCapIncrease")
    @Config.RangeInt(min = 0)
    public static int minimumLevelCapIncrease = 3;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Maximum Level Cap Increase Per Upgrade",
            "     Maximum boost to level ceiling after successful decryption",
            "     • 3 = Slow progression",
            "     • 6 = Normal progression (default)",
            "     • 10 = Fast progression",
            "     Increases the maximum possible weapon level",
            "",
            "[中文] 每次升级的最大等级上限提升",
            "     成功解密后等级上限的最大提升量",
            "     • 3 = 慢速提升",
            "     • 6 = 正常提升(默认)",
            "     • 10 = 快速提升",
            "     提高可能的最高武器等级",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maximumLevelCapIncrease")
    @Config.RangeInt(min = 0)
    public static int maximumLevelCapIncrease = 6;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Absolute Minimum Weapon Level",
            "     Lowest level achievable after maximum upgrades",
            "     • 15 = Lower bound",
            "     • 25 = Normal bound (default)",
            "     • 35 = Higher bound",
            "     Guarantees minimum quality after progression",
            "",
            "[中文] 绝对最低武器等级",
            "     达到升级上限后的最低等级",
            "     • 15 = 较低下限",
            "     • 25 = 正常下限(默认)",
            "     • 35 = 较高下限",
            "     保证推进后的最低品质",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.minimumLevel")
    @Config.RangeInt(min = 0)
    public static int minimumLevel = 25;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Absolute Maximum Weapon Level",
            "     Highest level achievable after maximum upgrades",
            "     • 45 = Lower ceiling",
            "     • 60 = Normal ceiling (default)",
            "     • 80 = Higher ceiling",
            "     End-game weapon level cap",
            "",
            "[中文] 绝对最高武器等级",
            "     达到升级上限后的最高等级",
            "     • 45 = 较低上限",
            "     • 60 = 正常上限(默认)",
            "     • 80 = 较高上限",
            "     游戏后期武器等级上限",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maximumLevel")
    @Config.RangeInt(min = 0)
    public static int maximumLevel = 60;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Weapon Upgrade Multiplier (Requiem Evolve)",
            "     Stat boost per upgrade using Requiem Evolve",
            "     • 0.05 = 5% per upgrade",
            "     • 0.10 = 10% per upgrade (default)",
            "     • 0.20 = 20% per upgrade",
            "     Stacks multiplicatively with each upgrade",
            "",
            "[中文] 武器升级倍率(安魂之融)",
            "     使用安魂之融升级时的属性提升",
            "     • 0.05 = 每次升级5%",
            "     • 0.10 = 每次升级10%(默认)",
            "     • 0.20 = 每次升级20%",
            "     每次升级乘法叠加",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.upgradeMultiplier")
    @Config.RangeDouble(min = 0)
    public static double upgradeMultiplier = 0.1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Weapon Upgrade Limit",
            "     Maximum number of times a weapon can be upgraded",
            "     • 5 = Limited upgrades",
            "     • 999 = Nearly unlimited (default)",
            "     • 9999 = Unlimited",
            "     Prevents infinite power scaling (if set low)",
            "",
            "[中文] 武器升级次数上限",
            "     武器可升级的最大次数",
            "     • 5 = 有限升级",
            "     • 999 = 近乎无限(默认)",
            "     • 9999 = 无限",
            "     防止无限强化(如果设置较低)",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.upgradeLimit")
    @Config.RangeInt(min = 0)
    public static int upgradeLimit = 999;

    // ========== 生成配置 ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Lich Spawn Weight",
            "     Relative spawn chance compared to other mobs",
            "     • 1 = Very rare",
            "     • 5 = Rare (default)",
            "     • 10 = Common",
            "     Higher values = more frequent spawns",
            "",
            "[中文] 赤毒玄骸生成权重",
            "     相对于其他生物的生成几率",
            "     • 1 = 非常稀有",
            "     • 5 = 稀有(默认)",
            "     • 10 = 常见",
            "     数值越高 = 生成越频繁",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaLichSpawnWeight")
    @Config.RangeInt(min = 1)
    public static int kuvaLichSpawnWeight = 5;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Lich Minimum Spawn Count",
            "     Minimum number spawned per spawn event",
            "     • 1 = Single spawn (default)",
            "     • 2 = Pair spawn",
            "     • 3 = Group spawn",
            "     ⚠ Kuva Lich is a boss mob - use with caution!",
            "",
            "[中文] 赤毒玄骸最小生成数量",
            "     每次生成事件的最小数量",
            "     • 1 = 单个生成(默认)",
            "     • 2 = 成对生成",
            "     • 3 = 成组生成",
            "     ⚠ 赤毒玄骸是Boss生物 - 谨慎设置!",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaLichMinSpawnCount")
    @Config.RangeInt(min = 1)
    public static int kuvaLichMinSpawnCount = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Lich Maximum Spawn Count",
            "     Maximum number spawned per spawn event",
            "     • 1 = Single spawn (default)",
            "     • 2 = Pair spawn",
            "     • 5 = Horde spawn",
            "     ⚠ Multiple bosses can be overwhelming!",
            "",
            "[中文] 赤毒玄骸最大生成数量",
            "     每次生成事件的最大数量",
            "     • 1 = 单个生成(默认)",
            "     • 2 = 成对生成",
            "     • 5 = 群体生成",
            "     ⚠ 多个Boss可能会难以应对!",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaLichMaxSpawnCount")
    @Config.RangeInt(min = 1)
    public static int kuvaLichMaxSpawnCount = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Slave Spawn Weight",
            "     Relative spawn chance compared to other mobs",
            "     • 5 = Rare",
            "     • 10 = Common (default)",
            "     • 20 = Very common",
            "     Higher values = more frequent spawns",
            "",
            "[中文] 赤毒奴仆生成权重",
            "     相对于其他生物的生成几率",
            "     • 5 = 稀有",
            "     • 10 = 常见(默认)",
            "     • 20 = 非常常见",
            "     数值越高 = 生成越频繁",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaSlaveSpawnWeight")
    @Config.RangeInt(min = 1)
    public static int kuvaSlaveSpawnWeight = 10;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Slave Minimum Spawn Count",
            "     Minimum number spawned per spawn event",
            "     • 1 = Solo spawn (default)",
            "     • 2 = Pair spawn",
            "     • 3 = Group spawn",
            "     Kuva Slaves often spawn in groups",
            "",
            "[中文] 赤毒奴仆最小生成数量",
            "     每次生成事件的最小数量",
            "     • 1 = 单独生成(默认)",
            "     • 2 = 成对生成",
            "     • 3 = 成组生成",
            "     赤毒奴仆通常成群出现",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaSlaveMinSpawnCount")
    @Config.RangeInt(min = 1)
    public static int kuvaSlaveMinSpawnCount = 1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Slave Maximum Spawn Count",
            "     Maximum number spawned per spawn event",
            "     • 2 = Small group",
            "     • 3 = Medium group (default)",
            "     • 5 = Large group",
            "     Controls maximum pack size",
            "",
            "[中文] 赤毒奴仆最大生成数量",
            "     每次生成事件的最大数量",
            "     • 2 = 小群",
            "     • 3 = 中等群体(默认)",
            "     • 5 = 大群",
            "     控制最大群体规模",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaSlaveMaxSpawnCount")
    @Config.RangeInt(min = 1)
    public static int kuvaSlaveMaxSpawnCount = 3;

    // ========== 配置同步 ==========

    /**
     * 配置变更监听器
     * 当配置在游戏内修改时自动同步
     */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent evt) {
        if (evt.getModID().equals(Reference.MOD_ID)) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        }
    }
}