// ModConfig.java
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
 * 赤毒玄骸模组配置类
 *
 * 统一管理所有配置项
 * 配置文件位置：config/kuvalich.cfg
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber
@Config(modid = Reference.MOD_ID, name = Reference.MOD_ID)
@Config.LangKey("config." + Reference.MOD_ID + ".title")
public final class ModConfig {

    @Config.Name("Kuva Lich System")
    @Config.Comment("赤毒玄骸系统相关配置 / Kuva Lich System Configuration")
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich")
    public static KuvaLichConfig KUVA_LICH = new KuvaLichConfig();

    @Config.Name("Kuva Weapon Attributes")
    @Config.Comment("赤毒武器基础属性配置 / Kuva Weapon Base Attributes")
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon")
    public static KuvaWeaponConfig KUVA_WEAPON = new KuvaWeaponConfig();

    public static class KuvaLichConfig {

        @Config.Comment({
                "═══════════════════════════════════════════════════════════════",
                "[EN] Enable Detailed Debug Logging",
                "     • true = Enabled - Logs all detailed information",
                "     • false = Disabled (default) - Only logs important events",
                "[中文] 启用详细调试日志",
                "     • true = 启用 - 记录所有详细信息",
                "     • false = 禁用(默认) - 仅记录重要事件",
                "═══════════════════════════════════════════════════════════════"
        })
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.enableDetailedLogging")
        public boolean enableDetailedLogging = false;

        @Config.Comment({
                "物品没收概率(%) / Item Confiscation Chance (%)",
                "玩家解密谜语后捡起物品被没收的概率",
                "设为0则完全禁用没收机制",
                "Chance of item being confiscated when picked up",
                "Set to 0 to disable confiscation completely"
        })
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.confiscationChance")
        @Config.RangeDouble(min = 0, max = 100)
        public double confiscationChance = 1.0;

        @Config.Comment({
                "安魂通牒掉落几率(%) / Requiem Ultimatum Drop Chance (%)",
                "成功破解赤毒玄骸后掉落安魂通牒的几率",
                "Drop chance of Requiem Ultimatum after successful decryption"
        })
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.requiemUltimatumDropChance")
        @Config.RangeDouble(min = 0, max = 100)
        public double requiemUltimatumDropChance = 25.0;

        @Config.Comment({
                "每次击杀获得的最小解密进度 / Minimum Decryption Progress Per Kill",
                "• 1 = 非常慢 / Very slow",
                "• 3 = 正常(默认) / Normal (default)",
                "• 5 = 快速 / Fast"
        })
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.minDecryptionProgress")
        @Config.RangeInt(min = 0)
        public int minDecryptionProgress = 3;

        @Config.Comment({
                "每次击杀获得的最大解密进度 / Maximum Decryption Progress Per Kill"
        })
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxDecryptionProgress")
        @Config.RangeInt(min = 0)
        public int maxDecryptionProgress = 5;

        @Config.Comment("第一阶段解密所需点数 / First Stage Requirement")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.firstStage")
        @Config.RangeInt(min = 1)
        public int firstStage = 60;

        @Config.Comment("第二阶段解密所需点数 / Second Stage Requirement")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.secondStage")
        @Config.RangeInt(min = 1)
        public int secondStage = 84;

        @Config.Comment("第三阶段解密所需点数 / Third Stage Requirement")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.thirdStage")
        @Config.RangeInt(min = 1)
        public int thirdStage = 120;

        @Config.Comment("击杀赤毒玄骸的额外点数倍率 / Master Kill Point Multiplier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.masterPotionMultiplier")
        @Config.RangeDouble(min = 1)
        public double masterPotionMultiplier = 5;

        @Config.Comment("显示所有伤害数字 / Display All Damage Numbers")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.damageDisplay")
        public boolean damageDisplay = false;

        @Config.Comment("伤害修正优先级 / Damage Modification Priority")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.damagePriority")
        public boolean damagePriority = true;

        @Config.Comment("战斗中每秒伤害提升 / Battle Damage Boost Per Second")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.battleBoost")
        @Config.RangeDouble(min = 0)
        public float battleBoost = 0.075f;

        @Config.Comment("每次解密失败的伤害削减 / Damage Reduction Per Failed Decryption")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.reducedDamage")
        @Config.RangeDouble(min = 0)
        public float reducedDamage = 0.1f;

        @Config.Comment("每次解密失败敌人伤害提升 / Enemy Damage Increase Per Failed Decryption")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.increaseDamage")
        @Config.RangeDouble(min = 0)
        public float increaseDamage = 0.25f;

        @Config.Comment("武器等级基准 / Weapon Level Benchmark")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.benchmarkLevel")
        @Config.RangeInt(min = 0)
        public int benchmarkLevel = 45;

        @Config.Comment("基础最低武器等级 / Base Minimum Weapon Level")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.baseMinimumLevel")
        @Config.RangeInt(min = 0)
        public int baseMinimumLevel = 5;

        @Config.Comment("基础最高武器等级 / Base Maximum Weapon Level")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.baseMaximumLevel")
        @Config.RangeInt(min = 0)
        public int baseMaximumLevel = 25;

        @Config.Comment("每次升级的最小等级下限提升 / Minimum Level Cap Increase Per Upgrade")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.minimumLevelCapIncrease")
        @Config.RangeInt(min = 0)
        public int minimumLevelCapIncrease = 3;

        @Config.Comment("每次升级的最大等级上限提升 / Maximum Level Cap Increase Per Upgrade")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maximumLevelCapIncrease")
        @Config.RangeInt(min = 0)
        public int maximumLevelCapIncrease = 6;

        @Config.Comment("绝对最低武器等级 / Absolute Minimum Weapon Level")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.minimumLevel")
        @Config.RangeInt(min = 0)
        public int minimumLevel = 25;

        @Config.Comment("绝对最高武器等级 / Absolute Maximum Weapon Level")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maximumLevel")
        @Config.RangeInt(min = 0)
        public int maximumLevel = 60;

        @Config.Comment("武器升级倍率(安魂之融) / Weapon Upgrade Multiplier (Requiem Evolve)")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.upgradeMultiplier")
        @Config.RangeDouble(min = 0)
        public double upgradeMultiplier = 0.1;

        @Config.Comment("武器升级次数上限 / Weapon Upgrade Limit")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.upgradeLimit")
        @Config.RangeInt(min = 0)
        public int upgradeLimit = 60;

        @Config.Comment({
                "═══════════════════════════════════════════════════════════════",
                "武器击杀叠层系统配置 / Weapon Kill Stack System Configuration",
                "控制所有武器击杀叠加效果的最大层数 / Control max stacks for weapon kill stack effects",
                "═══════════════════════════════════════════════════════════════"
        })
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.weaponKillStackSettings")
        public String weaponKillStackSettings = "═══ Weapon Kill Stack Settings ═══";

        @Config.Comment("武器叠层持续时间(ticks) / Weapon Stack Decay Time (ticks)\n200 ticks = 10秒")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.weaponStackDecayTicks")
        @Config.RangeInt(min = 20, max = 6000)
        public int weaponStackDecayTicks = 200;

        @Config.Comment("基础伤害叠层最大层数 x 目标身上的每种异常状态 / Base Damage Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksBaseDamage")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksBaseDamage = 20;

        @Config.Comment("多重射击叠层最大层数 / Multishot Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksMultishot")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksMultishot = 5;

        @Config.Comment("近战暴击伤害叠层最大层数 / Melee Crit Mult Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksMeleeCritMult")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksMeleeCritMult = 4;

        @Config.Comment("触发几率叠层最大层数 / Trigger Chance Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksTriggerChance")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksTriggerChance = 4;

        @Config.Comment("攻击范围叠层最大层数 / Attack Range Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksAttackRange")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksAttackRange = 5;

        @Config.Comment("攻击速度叠层最大层数 / Attack Speed Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksAttackSpeed")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksAttackSpeed = 5;

        @Config.Comment("爆炸半径叠层最大层数 / Bursting Radius Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksBurstingRadius")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksBurstingRadius = 5;

        @Config.Comment("射速叠层最大层数 / Firing Rate Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksFiringRate")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksFiringRate = 10;

        @Config.Comment({
                "═══════════════════════════════════════════════════════════════",
                "战甲击杀叠层系统配置 / Warframe Kill Stack System Configuration",
                "控制所有战甲击杀叠加效果的最大层数 / Control max stacks for warframe kill stack effects",
                "战甲叠层特点：20层上限，20秒掉一层，适合持久战 / 20 max stacks, 20s decay per stack",
                "═══════════════════════════════════════════════════════════════"
        })
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.warframeKillStackSettings")
        public String warframeKillStackSettings = "═══ Warframe Kill Stack Settings ═══";

        @Config.Comment("战甲叠层持续时间(ticks) / Warframe Stack Decay Time (ticks)\n400 ticks = 20秒")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.warframeStackDecayTicks")
        @Config.RangeInt(min = 20, max = 6000)
        public int warframeStackDecayTicks = 400;

        @Config.Comment("生命值叠层最大层数 / Health Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksHealth")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksHealth = 20;

        @Config.Comment({
                "护盾基础值倍率 / Shield Base Multiplier",
                "护盾容量 = 生命值上限 × 护盾加成 × 此倍率",
                "Shield Capacity = Max Health × Shield Bonus × This Multiplier",
                "• 0.5 = 50% 生命值(默认) / 50% of health (default)",
                "• 1.0 = 100% 生命值 / 100% of health"
        })
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.shieldBaseMultiplier")
        @Config.RangeDouble(min = 0, max = 10)
        public double shieldBaseMultiplier = 0.5;

        @Config.Comment("护盾容量叠层最大层数 / Shield Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksShield")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksShield = 20;

        @Config.Comment("护甲叠层最大层数 / Armor Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksArmor")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksArmor = 20;

        @Config.Comment("冲刺速度叠层最大层数 / Sprint Speed Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksSprintSpeed")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksSprintSpeed = 20;

        @Config.Comment("护盾恢复速率叠层最大层数 / Shield Recovery Rate Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksShieldRecoveryRate")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksShieldRecoveryRate = 20;

        @Config.Comment("护盾恢复延迟叠层最大层数 / Shield Recovery Delay Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksShieldRecoveryDelay")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksShieldRecoveryDelay = 20;

        @Config.Comment("火焰抗性叠层最大层数 / Fire Protection Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksFireProtection")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksFireProtection = 20;

        @Config.Comment("电击抗性叠层最大层数 / Electric Protection Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksElectricProtection")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksElectricProtection = 20;

        @Config.Comment("同源抗性叠层最大层数 / Homologous Protection Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksHomologousProtection")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksHomologousProtection = 20;

        @Config.Comment("恢复生命值倍率叠层最大层数 / Response Rate Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksResponseRate")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksResponseRate = 20;

        @Config.Comment("战利品掉落倍率叠层最大层数 / Item Drop Multiplier Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksItemDropMultiplier")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksItemDropMultiplier = 20;

        @Config.Comment("挖掘速度叠层最大层数 / Digging Speed Max Stacks")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.maxStacksDiggingSpeed")
        @Config.RangeInt(min = 1, max = 100)
        public int maxStacksDiggingSpeed = 20;

        @Config.Comment("赤毒玄骸生成权重 / Kuva Lich Spawn Weight")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaLichSpawnWeight")
        @Config.RangeInt(min = 1)
        public int kuvaLichSpawnWeight = 5;

        @Config.Comment("赤毒玄骸最小生成数量 / Kuva Lich Minimum Spawn Count")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaLichMinSpawnCount")
        @Config.RangeInt(min = 1)
        public int kuvaLichMinSpawnCount = 1;

        @Config.Comment("赤毒玄骸最大生成数量 / Kuva Lich Maximum Spawn Count")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaLichMaxSpawnCount")
        @Config.RangeInt(min = 1)
        public int kuvaLichMaxSpawnCount = 1;

        @Config.Comment("赤毒奴仆生成权重 / Kuva Slave Spawn Weight")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaSlaveSpawnWeight")
        @Config.RangeInt(min = 1)
        public int kuvaSlaveSpawnWeight = 10;

        @Config.Comment("赤毒奴仆最小生成数量 / Kuva Slave Minimum Spawn Count")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaSlaveMinSpawnCount")
        @Config.RangeInt(min = 1)
        public int kuvaSlaveMinSpawnCount = 1;

        @Config.Comment("赤毒奴仆最大生成数量 / Kuva Slave Maximum Spawn Count")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvalich.kuvaSlaveMaxSpawnCount")
        @Config.RangeInt(min = 1)
        public int kuvaSlaveMaxSpawnCount = 3;
    }

    public static class KuvaWeaponConfig {

        @Config.Comment("武器属性倍率 / Weapon Attribute Multiplier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attributeMultiplier")
        @Config.RangeDouble(min = 0)
        public double attributeMultiplier = 1.5;

        @Config.Comment("赤毒希尔德 - 基础攻击伤害 / Kuva Shildeg - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageKuvaShildeg")
        @Config.RangeDouble(min = 0)
        public double attackDamageKuvaShildeg = 44;

        @Config.Comment("赤毒希尔德 - 基础攻击速度 / Kuva Shildeg - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedKuvaShildeg")
        @Config.RangeDouble(min = 0)
        public double attackSpeedKuvaShildeg = 0.7;

        @Config.Comment("赤毒希尔德 - 移动速度修正 / Kuva Shildeg - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedKuvaShildeg")
        @Config.RangeDouble(min = 0)
        public double movementSpeedKuvaShildeg = -0.2;

        @Config.Comment("尖幡 - 基础攻击伤害 / Pennant - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamagePennant")
        @Config.RangeDouble(min = 0)
        public double attackDamagePennant = 28;

        @Config.Comment("尖幡 - 基础攻击速度 / Pennant - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedPennant")
        @Config.RangeDouble(min = 0)
        public double attackSpeedPennant = 1.1;

        @Config.Comment("尖幡 - 移动速度修正 / Pennant - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedPennant")
        @Config.RangeDouble(min = 0)
        public double movementSpeedPennant = 0.025;

        @Config.Comment("关刀Prime - 基础攻击伤害 / Guandao Prime - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageGuandaoPrime")
        @Config.RangeDouble(min = 0)
        public double attackDamageGuandaoPrime = 16;

        @Config.Comment("关刀Prime - 基础攻击速度 / Guandao Prime - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedGuandaoPrime")
        @Config.RangeDouble(min = 0)
        public double attackSpeedGuandaoPrime = 2;

        @Config.Comment("关刀Prime - 移动速度修正 / Guandao Prime - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedGuandaoPrime")
        @Config.RangeDouble(min = 0)
        public double movementSpeedGuandaoPrime = 0.075;

        @Config.Comment("心智之殁 - 基础攻击伤害 / Paracesis - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageParacesis")
        @Config.RangeDouble(min = 0)
        public double attackDamageParacesis = 34;

        @Config.Comment("心智之殁 - 基础攻击速度 / Paracesis - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedParacesis")
        @Config.RangeDouble(min = 0)
        public double attackSpeedParacesis = 0.9;

        @Config.Comment("心智之殁 - 移动速度修正 / Paracesis - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedParacesis")
        @Config.RangeDouble(min = 0)
        public double movementSpeedParacesis = -0.1;

        @Config.Comment("弧电振子锤 - 基础攻击伤害 / Arca Titron - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageArcaTitron")
        @Config.RangeDouble(min = 0)
        public double attackDamageArcaTitron = 38;

        @Config.Comment("弧电振子锤 - 基础攻击速度 / Arca Titron - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedArcaTitron")
        @Config.RangeDouble(min = 0)
        public double attackSpeedArcaTitron = 0.75;

        @Config.Comment("弧电振子锤 - 移动速度修正 / Arca Titron - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedArcaTitron")
        @Config.RangeDouble(min = 0)
        public double movementSpeedArcaTitron = -0.15;

        @Config.Comment("收割者Prime - 基础攻击伤害 / Reaper Prime - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageReaperPrime")
        @Config.RangeDouble(min = 0)
        public double attackDamageReaperPrime = 18;

        @Config.Comment("收割者Prime - 基础攻击速度 / Reaper Prime - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedReaperPrime")
        @Config.RangeDouble(min = 0)
        public double attackSpeedReaperPrime = 1.75;

        @Config.Comment("收割者Prime - 移动速度修正 / Reaper Prime - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedReaperPrime")
        @Config.RangeDouble(min = 0)
        public double movementSpeedReaperPrime = 0.05;

        @Config.Comment("金璃剑 - 基础攻击伤害 / Vitrica - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageVitrica")
        @Config.RangeDouble(min = 0)
        public double attackDamageVitrica = 50;

        @Config.Comment("金璃剑 - 基础攻击速度 / Vitrica - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedVitrica")
        @Config.RangeDouble(min = 0)
        public double attackSpeedVitrica = 0.65;

        @Config.Comment("金璃剑 - 移动速度修正 / Vitrica - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedVitrica")
        @Config.RangeDouble(min = 0)
        public double movementSpeedVitrica = -0.2;

        @Config.Comment("格拉姆Prime - 基础攻击伤害 / Gram Prime - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageGramPrime")
        @Config.RangeDouble(min = 0)
        public double attackDamageGramPrime = 46;

        @Config.Comment("格拉姆Prime - 基础攻击速度 / Gram Prime - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedGramPrime")
        @Config.RangeDouble(min = 0)
        public double attackSpeedGramPrime = 0.7;

        @Config.Comment("格拉姆Prime - 移动速度修正 / Gram Prime - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedGramPrime")
        @Config.RangeDouble(min = 0)
        public double movementSpeedGramPrime = -0.15;

        @Config.Comment("圣洁执法者 - 基础攻击伤害 / Sancti Magistar - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageSanctiMagistar")
        @Config.RangeDouble(min = 0)
        public double attackDamageSanctiMagistar = 36;

        @Config.Comment("圣洁执法者 - 基础攻击速度 / Sancti Magistar - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedSanctiMagistar")
        @Config.RangeDouble(min = 0)
        public double attackSpeedSanctiMagistar = 0.85;

        @Config.Comment("圣洁执法者 - 移动速度修正 / Sancti Magistar - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedSanctiMagistar")
        @Config.RangeDouble(min = 0)
        public double movementSpeedSanctiMagistar = -0.075;

        @Config.Comment("技巧之剑Prime - 基础攻击伤害 / Destreza Prime - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageDestrezaPrime")
        @Config.RangeDouble(min = 0)
        public double attackDamageDestrezaPrime = 24;

        @Config.Comment("技巧之剑Prime - 基础攻击速度 / Destreza Prime - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedDestrezaPrime")
        @Config.RangeDouble(min = 0)
        public double attackSpeedDestrezaPrime = 1.4;

        @Config.Comment("技巧之剑Prime - 移动速度修正 / Destreza Prime - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedDestrezaPrime")
        @Config.RangeDouble(min = 0)
        public double movementSpeedDestrezaPrime = 0.125;

        @Config.Comment("棱晶真理巨剑 - 基础攻击伤害 / Prisma Veritux - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamagePrismaVeritux")
        @Config.RangeDouble(min = 0)
        public double attackDamagePrismaVeritux = 100;

        @Config.Comment("棱晶真理巨剑 - 基础攻击速度 / Prisma Veritux - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedPrismaVeritux")
        @Config.RangeDouble(min = 0)
        public double attackSpeedPrismaVeritux = 0.45;

        @Config.Comment("棱晶真理巨剑 - 移动速度修正 / Prisma Veritux - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedPrismaVeritux")
        @Config.RangeDouble(min = 0)
        public double movementSpeedPrismaVeritux = -0.6;

        @Config.Comment("马谢特砍刀 - 基础攻击伤害 / Machete - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageMachete")
        @Config.RangeDouble(min = 0)
        public double attackDamageMachete = 32;

        @Config.Comment("马谢特砍刀 - 基础攻击速度 / Machete - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedMachete")
        @Config.RangeDouble(min = 0)
        public double attackSpeedMachete = 1.55;

        @Config.Comment("马谢特砍刀 - 移动速度修正 / Machete - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedMachete")
        @Config.RangeDouble(min = 0)
        public double movementSpeedMachete = 0.025;

        @Config.Comment("灼蚀变体镰 - 基础攻击伤害 / Caustacyst - Base Attack Damage")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageCaustacyst")
        @Config.RangeDouble(min = 0)
        public double attackDamageCaustacyst = 28;

        @Config.Comment("灼蚀变体镰 - 基础攻击速度 / Caustacyst - Base Attack Speed")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedCaustacyst")
        @Config.RangeDouble(min = 0)
        public double attackSpeedCaustacyst = 1.3;

        @Config.Comment("灼蚀变体镰 - 移动速度修正 / Caustacyst - Movement Speed Modifier")
        @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedCaustacyst")
        @Config.RangeDouble(min = 0)
        public double movementSpeedCaustacyst = 0.125;
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent evt) {
        if (evt.getModID().equals(Reference.MOD_ID)) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        }
    }
}