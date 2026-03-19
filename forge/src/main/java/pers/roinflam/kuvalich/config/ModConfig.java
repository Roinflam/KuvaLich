package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 赤毒玄骸模组配置类
 * <p>
 * 管理所有模组配置项，包括赤毒玄骸系统、赤毒武器属性和矿石生成。
 * 使用 Forge 配置规范（ForgeConfigSpec）实现持久化。
 * </p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModConfig {

    /** 通用配置规范 / Common config spec */
    public static final ForgeConfigSpec COMMON_CONFIG;

    /** 赤毒玄骸系统配置 / Kuva Lich system config */
    public static final KuvaLichConfig KUVA_LICH;

    /** 赤毒武器属性配置 / Kuva weapon attribute config */
    public static final KuvaWeaponConfig KUVA_WEAPON;

    /** 矿石生成配置 / Ore generation config */
    public static final OreGenConfig ORE_GEN;

    static {
        ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();

        KUVA_LICH = new KuvaLichConfig(COMMON_BUILDER);
        KUVA_WEAPON = new KuvaWeaponConfig(COMMON_BUILDER);
        ORE_GEN = new OreGenConfig(COMMON_BUILDER);

        COMMON_CONFIG = COMMON_BUILDER.build();
    }

    /**
     * 赤毒玄骸系统配置
     * <p>
     * 包含调试、护盾、物品没收、解密进度、伤害、武器等级、
     * 击杀叠层、实体生成、掉落概率、教程书等所有子系统配置。
     * </p>
     */
    public static class KuvaLichConfig {

        // ===== 调试 / Debug =====
        public final ForgeConfigSpec.BooleanValue enableDetailedLogging;

        // ===== 护盾系统 / Shield System =====
        public final ForgeConfigSpec.DoubleValue shieldCapMultiplier;

        // ===== 物品没收系统 / Confiscation System =====
        public final ForgeConfigSpec.DoubleValue confiscationChance;
        public final ForgeConfigSpec.IntValue maxConfiscatedItems;
        public final ForgeConfigSpec.DoubleValue requiemUltimatumDropChance;

        // ===== 解密进度系统 / Decryption Progress =====
        public final ForgeConfigSpec.IntValue minDecryptionProgress;
        public final ForgeConfigSpec.IntValue maxDecryptionProgress;
        public final ForgeConfigSpec.IntValue firstStage;
        public final ForgeConfigSpec.IntValue secondStage;
        public final ForgeConfigSpec.IntValue thirdStage;
        public final ForgeConfigSpec.DoubleValue masterPotionMultiplier;

        // ===== 伤害系统 / Damage System =====
        public final ForgeConfigSpec.BooleanValue damageDisplay;
        public final ForgeConfigSpec.BooleanValue enableDamageNumbers;
        public final ForgeConfigSpec.BooleanValue enableTrueDamage;
        public final ForgeConfigSpec.DoubleValue battleBoost;
        public final ForgeConfigSpec.DoubleValue reducedDamage;
        public final ForgeConfigSpec.DoubleValue increaseDamage;

        // ===== 元素伤害倍率 / Element Damage Multipliers =====
        public final ForgeConfigSpec.DoubleValue elementFireDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue elementPoisonDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue elementSlashDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue elementGasDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue elementElectricityDamageMultiplier;
        public final ForgeConfigSpec.DoubleValue elementExplosionDamageMultiplier;

        // ===== 武器等级系统 / Weapon Level System =====
        public final ForgeConfigSpec.IntValue benchmarkLevel;
        public final ForgeConfigSpec.IntValue baseMinimumLevel;
        public final ForgeConfigSpec.IntValue baseMaximumLevel;
        public final ForgeConfigSpec.IntValue minimumLevelCapIncrease;
        public final ForgeConfigSpec.IntValue maximumLevelCapIncrease;
        public final ForgeConfigSpec.IntValue minimumLevel;
        public final ForgeConfigSpec.IntValue maximumLevel;
        public final ForgeConfigSpec.DoubleValue upgradeMultiplier;
        public final ForgeConfigSpec.IntValue upgradeLimit;

        // ===== 模组属性倍率 / Module Attribute Multipliers =====
        public final ForgeConfigSpec.DoubleValue moduleAttributeMultiplier;
        public final ForgeConfigSpec.DoubleValue keyAttributeMultiplier;

        // ===== 塑形块锁定 / Forma Lock =====
        public final ForgeConfigSpec.BooleanValue formaLockEnabled;
        public final ForgeConfigSpec.DoubleValue formaLockChance;

        // ===== 武器击杀叠层 / Weapon Kill Stack =====
        public final ForgeConfigSpec.IntValue weaponStackDecayTicks;
        public final ForgeConfigSpec.IntValue maxStacksBaseDamage;
        public final ForgeConfigSpec.IntValue maxStacksMultishot;
        public final ForgeConfigSpec.IntValue maxStacksMeleeCritMult;
        public final ForgeConfigSpec.IntValue maxStacksTriggerChance;
        public final ForgeConfigSpec.IntValue maxStacksAttackRange;
        public final ForgeConfigSpec.IntValue maxStacksAttackSpeed;
        public final ForgeConfigSpec.IntValue maxStacksBurstingRadius;
        public final ForgeConfigSpec.IntValue maxStacksFiringRate;

        // ===== 战甲击杀叠层 / Warframe Kill Stack =====
        public final ForgeConfigSpec.IntValue warframeStackDecayTicks;
        public final ForgeConfigSpec.IntValue maxStacksHealth;
        public final ForgeConfigSpec.IntValue maxStacksShield;
        public final ForgeConfigSpec.IntValue maxStacksArmor;
        public final ForgeConfigSpec.IntValue maxStacksSprintSpeed;
        public final ForgeConfigSpec.IntValue maxStacksShieldRecoveryRate;
        public final ForgeConfigSpec.IntValue maxStacksShieldRecoveryDelay;
        public final ForgeConfigSpec.IntValue maxStacksFireProtection;
        public final ForgeConfigSpec.IntValue maxStacksElectricProtection;
        public final ForgeConfigSpec.IntValue maxStacksHomologousProtection;
        public final ForgeConfigSpec.IntValue maxStacksResponseRate;
        public final ForgeConfigSpec.IntValue maxStacksItemDropMultiplier;
        public final ForgeConfigSpec.IntValue maxStacksDiggingSpeed;

        // ===== 实体生成 / Entity Spawning =====
        public final ForgeConfigSpec.IntValue kuvaLichSpawnWeight;
        public final ForgeConfigSpec.IntValue kuvaLichMinSpawnCount;
        public final ForgeConfigSpec.IntValue kuvaLichMaxSpawnCount;
        public final ForgeConfigSpec.IntValue kuvaSlaveSpawnWeight;
        public final ForgeConfigSpec.IntValue kuvaSlaveMinSpawnCount;
        public final ForgeConfigSpec.IntValue kuvaSlaveMaxSpawnCount;

        // ===== 矿石掉落概率 / Ore Drop Chances =====
        public final ForgeConfigSpec.IntValue cardDropChanceWithEnchant;
        public final ForgeConfigSpec.IntValue baseCardDropChance;
        public final ForgeConfigSpec.DoubleValue fortuneReductionPerLevel;
        public final ForgeConfigSpec.IntValue minCardDropChance;
        public final ForgeConfigSpec.IntValue commonModuleDropChance;
        public final ForgeConfigSpec.IntValue uncommonModuleDropChance;
        public final ForgeConfigSpec.IntValue rareModuleDropChance;
        public final ForgeConfigSpec.IntValue expMultiplier;

        // ===== 赤毒奴仆掉落 / Kuva Slave Drops =====
        public final ForgeConfigSpec.IntValue slaveKuvaDropChance;
        public final ForgeConfigSpec.IntValue slaveKuvaMinAmount;
        public final ForgeConfigSpec.IntValue slaveKuvaMaxAmount;
        public final ForgeConfigSpec.IntValue slaveRivenSliverDropChance;
        public final ForgeConfigSpec.IntValue slaveRequiemGemBaseChance;
        public final ForgeConfigSpec.DoubleValue slaveRequiemGemLootingBonus;

        // ===== 赤毒玄骸掉落 / Kuva Master Drops =====
        public final ForgeConfigSpec.IntValue masterKuvaMinAmount;
        public final ForgeConfigSpec.IntValue masterKuvaMaxAmount;
        public final ForgeConfigSpec.IntValue masterRivenSliverMinAmount;
        public final ForgeConfigSpec.IntValue masterRivenSliverMaxAmount;
        public final ForgeConfigSpec.IntValue masterPrimeModuleChance;

        // ===== 通用掉落 / Common Drops =====
        public final ForgeConfigSpec.IntValue moduleWeaponRatio;

        // ===== 教程书 / Guidebook =====
        public final ForgeConfigSpec.BooleanValue enableGuidebook;

        /**
         * 构造赤毒玄骸系统配置
         *
         * @param builder Forge 配置规范构建器
         */
        public KuvaLichConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("═══════════════════════════════════════════════════════════════")
                    .comment("Kuva Lich System Configuration")
                    .comment("赤毒玄骸系统配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("kuva_lich");

            builder.comment("")
                    .comment("═══ Debug Logging / 调试日志 ═══");

            enableDetailedLogging = builder
                    .comment("Enable detailed debug logging")
                    .comment("启用详细调试日志")
                    .define("enableDetailedLogging", false);

            builder.comment("")
                    .comment("═══ Shield System / 护盾系统 ═══");

            shieldCapMultiplier = builder
                    .comment("Shield cap multiplier (based on max health)")
                    .comment("护盾上限倍率(基于最大生命值)")
                    .comment("Formula: Shield Cap = Max Health × Shield Attribute × This Multiplier")
                    .comment("公式: 护盾上限 = 最大生命值 × 护盾属性 × 此倍率")
                    .defineInRange("shieldCapMultiplier", 0.5, 0.0, 10.0);

            builder.comment("")
                    .comment("═══ Item Confiscation System / 物品没收系统 ═══");

            confiscationChance = builder
                    .comment("Item confiscation chance (%)")
                    .comment("物品没收概率(%)")
                    .defineInRange("confiscationChance", 1.0, 0.0, 100.0);

            maxConfiscatedItems = builder
                    .comment("Maximum number of items a Kuva Lich can confiscate per riddle cycle")
                    .comment("每个解密周期中赤毒玄骸最多没收的物品数量")
                    .comment("0 = disable confiscation regardless of chance")
                    .comment("0 = 无论概率如何都禁用没收")
                    .defineInRange("maxConfiscatedItems", 64, 0, 1024);

            requiemUltimatumDropChance = builder
                    .comment("Requiem Ultimatum drop chance after successful decryption (%)")
                    .comment("成功破解后安魂通牒掉落几率(%)")
                    .defineInRange("requiemUltimatumDropChance", 25.0, 0.0, 100.0);

            builder.comment("")
                    .comment("═══ Decryption Progress / 解密进度 ═══");

            minDecryptionProgress = builder
                    .comment("Minimum decryption progress per kill")
                    .comment("每次击杀获得的最小解密进度")
                    .defineInRange("minDecryptionProgress", 3, 0, Integer.MAX_VALUE);

            maxDecryptionProgress = builder
                    .comment("Maximum decryption progress per kill")
                    .comment("每次击杀获得的最大解密进度")
                    .defineInRange("maxDecryptionProgress", 5, 0, Integer.MAX_VALUE);

            firstStage = builder
                    .comment("First stage decryption requirement")
                    .comment("第一阶段解密所需点数")
                    .defineInRange("firstStage", 60, 1, Integer.MAX_VALUE);

            secondStage = builder
                    .comment("Second stage decryption requirement")
                    .comment("第二阶段解密所需点数")
                    .defineInRange("secondStage", 84, 1, Integer.MAX_VALUE);

            thirdStage = builder
                    .comment("Third stage decryption requirement")
                    .comment("第三阶段解密所需点数")
                    .defineInRange("thirdStage", 120, 1, Integer.MAX_VALUE);

            masterPotionMultiplier = builder
                    .comment("Master kill point multiplier")
                    .comment("击杀赤毒玄骸的额外点数倍率")
                    .defineInRange("masterPotionMultiplier", 5.0, 1.0, Double.MAX_VALUE);

            builder.comment("")
                    .comment("═══ Damage System / 伤害系统 ═══");

            damageDisplay = builder
                    .comment("Display all damage numbers")
                    .comment("显示所有伤害数字")
                    .define("damageDisplay", false);

            enableDamageNumbers = builder
                    .comment("Enable damage number display (floating damage text)")
                    .comment("启用伤害跳字显示(飘字效果)")
                    .comment("When disabled, no damage packets will be sent to client")
                    .comment("关闭时将不会向客户端发送伤害数据包")
                    .define("enableDamageNumbers", true);

            enableTrueDamage = builder
                    .comment("Enable true damage system (bypass setHealth and damage reduction)")
                    .comment("启用真伤系统(绕过setHealth和伤害减免)")
                    .comment("When enabled, will find and cache the real health field of entities")
                    .comment("启用时将查找并缓存实体真正的血量字段")
                    .comment("This bypasses custom health systems like Apollyon")
                    .comment("这会绕过自定义血量系统如Apollyon")
                    .comment("When disabled, will use standard setHealth method")
                    .comment("禁用时将使用标准的setHealth方法")
                    .define("enableTrueDamage", true);

            battleBoost = builder
                    .comment("Battle damage boost per second")
                    .comment("战斗中每秒伤害提升")
                    .defineInRange("battleBoost", 0.075, 0.0, Double.MAX_VALUE);

            reducedDamage = builder
                    .comment("Damage reduction per failed decryption")
                    .comment("每次解密失败的伤害削减")
                    .defineInRange("reducedDamage", 0.1, 0.0, Double.MAX_VALUE);

            increaseDamage = builder
                    .comment("Enemy damage increase per failed decryption")
                    .comment("每次解密失败敌人伤害提升")
                    .defineInRange("increaseDamage", 0.25, 0.0, Double.MAX_VALUE);

            builder.comment("")
                    .comment("═══ Element Damage Multipliers / 元素伤害倍率 ═══")
                    .comment("Independent multipliers for each damage-dealing element's triggered effect.")
                    .comment("各造成伤害的元素触发效果的独立倍率，仅影响元素触发时产生的额外伤害。")
                    .comment("Does NOT affect the element's contribution to total weapon damage calculation.")
                    .comment("不影响元素对武器总伤害计算的贡献，仅影响触发后的DOT/瞬发伤害。");

            elementFireDamageMultiplier = builder
                    .comment("Fire element triggered DOT damage multiplier")
                    .comment("火焰元素触发的持续伤害倍率")
                    .comment("1.0 = original damage (default)")
                    .comment("1.0 = 原始伤害(默认)")
                    .defineInRange("elementFireDamageMultiplier", 1.0, 0.0, 100.0);

            elementPoisonDamageMultiplier = builder
                    .comment("Poison element triggered DOT damage multiplier")
                    .comment("毒素元素触发的持续伤害倍率")
                    .comment("1.0 = original damage (default)")
                    .comment("1.0 = 原始伤害(默认)")
                    .defineInRange("elementPoisonDamageMultiplier", 1.0, 0.0, 100.0);

            elementSlashDamageMultiplier = builder
                    .comment("Slash element triggered DOT damage multiplier")
                    .comment("切割元素触发的持续伤害倍率")
                    .comment("1.0 = original damage (default)")
                    .comment("1.0 = 原始伤害(默认)")
                    .defineInRange("elementSlashDamageMultiplier", 1.0, 0.0, 100.0);

            elementGasDamageMultiplier = builder
                    .comment("Gas element triggered AOE DOT damage multiplier")
                    .comment("毒气元素触发的范围持续伤害倍率")
                    .comment("1.0 = original damage (default)")
                    .comment("1.0 = 原始伤害(默认)")
                    .defineInRange("elementGasDamageMultiplier", 1.0, 0.0, 100.0);

            elementElectricityDamageMultiplier = builder
                    .comment("Electricity element triggered instant damage multiplier")
                    .comment("电击元素触发的瞬发伤害倍率")
                    .comment("1.0 = original damage (default)")
                    .comment("1.0 = 原始伤害(默认)")
                    .defineInRange("elementElectricityDamageMultiplier", 1.0, 0.0, 100.0);

            elementExplosionDamageMultiplier = builder
                    .comment("Explosion element triggered AOE damage multiplier")
                    .comment("爆炸元素触发的范围伤害倍率")
                    .comment("1.0 = original damage (default)")
                    .comment("1.0 = 原始伤害(默认)")
                    .defineInRange("elementExplosionDamageMultiplier", 1.0, 0.0, 100.0);

            builder.comment("")
                    .comment("═══ Weapon Level System / 武器等级系统 ═══");

            benchmarkLevel = builder
                    .comment("Weapon level benchmark")
                    .comment("武器等级基准")
                    .defineInRange("benchmarkLevel", 45, 0, Integer.MAX_VALUE);

            baseMinimumLevel = builder
                    .comment("Base minimum weapon level")
                    .comment("基础最低武器等级")
                    .defineInRange("baseMinimumLevel", 5, 0, Integer.MAX_VALUE);

            baseMaximumLevel = builder
                    .comment("Base maximum weapon level")
                    .comment("基础最高武器等级")
                    .defineInRange("baseMaximumLevel", 25, 0, Integer.MAX_VALUE);

            minimumLevelCapIncrease = builder
                    .comment("Minimum level cap increase per upgrade")
                    .comment("每次升级的最小等级下限提升")
                    .defineInRange("minimumLevelCapIncrease", 3, 0, Integer.MAX_VALUE);

            maximumLevelCapIncrease = builder
                    .comment("Maximum level cap increase per upgrade")
                    .comment("每次升级的最大等级上限提升")
                    .defineInRange("maximumLevelCapIncrease", 6, 0, Integer.MAX_VALUE);

            minimumLevel = builder
                    .comment("Absolute minimum weapon level")
                    .comment("绝对最低武器等级")
                    .defineInRange("minimumLevel", 25, 0, Integer.MAX_VALUE);

            maximumLevel = builder
                    .comment("Absolute maximum weapon level")
                    .comment("绝对最高武器等级")
                    .defineInRange("maximumLevel", 60, 0, Integer.MAX_VALUE);

            upgradeMultiplier = builder
                    .comment("Weapon upgrade multiplier (Requiem Evolve)")
                    .comment("武器升级倍率(安魂之融)")
                    .defineInRange("upgradeMultiplier", 0.1, 0.0, Double.MAX_VALUE);

            upgradeLimit = builder
                    .comment("Weapon upgrade limit")
                    .comment("武器升级次数上限")
                    .defineInRange("upgradeLimit", 999, 0, Integer.MAX_VALUE);

            builder.comment("")
                    .comment("═══ Module Attribute Multiplier / 模组属性倍率 ═══")
                    .comment("Two independent multipliers for different attribute categories.")
                    .comment("两套独立倍率，分别控制不同类型词条，互不干扰，默认均为1.0。");

            moduleAttributeMultiplier = builder
                    .comment("General attribute multiplier — applies to all non-key attributes")
                    .comment("通用词条倍率 —— 应用于所有非关键词条")
                    .comment("Includes: all damage types, elements, faction bonus, health/shield/armor,")
                    .comment("attack speed, firing rate, sprint/digging speed, jump height,")
                    .comment("resistances, knockback resistance, multishot, response rate, item drop rate,")
                    .comment("and all corresponding kill-stack versions")
                    .comment("包含：各类伤害、元素、派系加成、生命/护盾/护甲、")
                    .comment("攻速、射速、冲刺/挖掘速度、跳跃高度、")
                    .comment("三种抗性、抗击倒、多重射击、恢复倍率、战利品倍率")
                    .comment("及以上所有属性的击杀叠层版本")
                    .comment("1.0 = use original values (default) / 1.0 = 原始数值(默认)")
                    .defineInRange("moduleAttributeMultiplier", 1.0, 0.0, 100.0);

            keyAttributeMultiplier = builder
                    .comment("Key attribute multiplier — applies independently to mechanical attributes")
                    .comment("关键词条倍率 —— 独立应用于机制性词条")
                    .comment("Includes: crit chance/multiplier (all types), trigger chance/time,")
                    .comment("shield recovery rate/delay, and their kill-stack versions")
                    .comment("包含：所有暴击几率/暴击伤害、触发几率/触发时间、")
                    .comment("护盾恢复速率/恢复延迟，以及对应的击杀叠层版本")
                    .comment("These are kept separate because their internal logic has special thresholds")
                    .comment("(e.g. crit tier at 100%/200%/300%, trigger multi-proc above 100%)")
                    .comment("这些词条的底层逻辑有特殊档位（暴击100%/200%/300%档位、触发超100%多次触发），")
                    .comment("单独暴露倍率以便精细控制，避免和通用倍率混用导致数值错乱")
                    .comment("1.0 = use original values (default) / 1.0 = 原始数值(默认)")
                    .defineInRange("keyAttributeMultiplier", 1.0, 0.0, 100.0);

            builder.comment("")
                    .comment("═══ Forma Lock System / 塑形块锁定系统 ═══");

            formaLockEnabled = builder
                    .comment("Enable Forma lock chance (each Forma re-roll may permanently lock the weapon panel)")
                    .comment("启用塑形块锁定概率(每次Forma洗面板可能永久锁定武器面板)")
                    .define("formaLockEnabled", false);

            formaLockChance = builder
                    .comment("Chance (%) to permanently lock weapon panel after Forma re-roll")
                    .comment("使用塑形块洗面板后永久锁定面板的概率(%)")
                    .comment("Only effective when formaLockEnabled is true")
                    .comment("仅在启用Forma锁定时生效")
                    .defineInRange("formaLockChance", 10.0, 0.0, 100.0);

            builder.comment("")
                    .comment("═══ Weapon Kill Stack System / 武器击杀叠层系统 ═══");

            weaponStackDecayTicks = builder
                    .comment("Weapon stack decay time (ticks), 200 ticks = 10 seconds")
                    .comment("武器叠层持续时间(ticks)，200 ticks = 10秒")
                    .defineInRange("weaponStackDecayTicks", 200, 20, 6000);

            maxStacksBaseDamage = builder
                    .comment("Base damage x status effects max stacks")
                    .comment("基础伤害 x 目标身上的每种异常状态最大层数")
                    .defineInRange("maxStacksBaseDamage", 20, 1, 100);

            maxStacksMultishot = builder
                    .comment("Multishot max stacks")
                    .comment("多重射击最大层数")
                    .defineInRange("maxStacksMultishot", 5, 1, 100);

            maxStacksMeleeCritMult = builder
                    .comment("Melee crit multiplier max stacks")
                    .comment("近战暴击伤害最大层数")
                    .defineInRange("maxStacksMeleeCritMult", 4, 1, 100);

            maxStacksTriggerChance = builder
                    .comment("Trigger chance max stacks")
                    .comment("触发几率最大层数")
                    .defineInRange("maxStacksTriggerChance", 4, 1, 100);

            maxStacksAttackRange = builder
                    .comment("Attack range max stacks")
                    .comment("攻击范围最大层数")
                    .defineInRange("maxStacksAttackRange", 5, 1, 100);

            maxStacksAttackSpeed = builder
                    .comment("Attack speed max stacks")
                    .comment("攻击速度最大层数")
                    .defineInRange("maxStacksAttackSpeed", 5, 1, 100);

            maxStacksBurstingRadius = builder
                    .comment("Bursting radius max stacks")
                    .comment("爆炸半径最大层数")
                    .defineInRange("maxStacksBurstingRadius", 5, 1, 100);

            maxStacksFiringRate = builder
                    .comment("Firing rate max stacks")
                    .comment("射速最大层数")
                    .defineInRange("maxStacksFiringRate", 10, 1, 100);

            builder.comment("")
                    .comment("═══ Warframe Kill Stack System / 战甲击杀叠层系统 ═══");

            warframeStackDecayTicks = builder
                    .comment("Warframe stack decay time (ticks), 400 ticks = 20 seconds")
                    .comment("战甲叠层持续时间(ticks)，400 ticks = 20秒")
                    .defineInRange("warframeStackDecayTicks", 400, 20, 6000);

            maxStacksHealth = builder
                    .comment("Health max stacks")
                    .comment("生命值最大层数")
                    .defineInRange("maxStacksHealth", 20, 1, 100);

            maxStacksShield = builder
                    .comment("Shield max stacks")
                    .comment("护盾容量最大层数")
                    .defineInRange("maxStacksShield", 20, 1, 100);

            maxStacksArmor = builder
                    .comment("Armor max stacks")
                    .comment("护甲最大层数")
                    .defineInRange("maxStacksArmor", 20, 1, 100);

            maxStacksSprintSpeed = builder
                    .comment("Sprint speed max stacks")
                    .comment("冲刺速度最大层数")
                    .defineInRange("maxStacksSprintSpeed", 20, 1, 100);

            maxStacksShieldRecoveryRate = builder
                    .comment("Shield recovery rate max stacks")
                    .comment("护盾恢复速率最大层数")
                    .defineInRange("maxStacksShieldRecoveryRate", 20, 1, 100);

            maxStacksShieldRecoveryDelay = builder
                    .comment("Shield recovery delay max stacks")
                    .comment("护盾恢复延迟最大层数")
                    .defineInRange("maxStacksShieldRecoveryDelay", 20, 1, 100);

            maxStacksFireProtection = builder
                    .comment("Fire protection max stacks")
                    .comment("火焰抗性最大层数")
                    .defineInRange("maxStacksFireProtection", 20, 1, 100);

            maxStacksElectricProtection = builder
                    .comment("Electric protection max stacks")
                    .comment("电击抗性最大层数")
                    .defineInRange("maxStacksElectricProtection", 20, 1, 100);

            maxStacksHomologousProtection = builder
                    .comment("Homologous protection max stacks")
                    .comment("同源抗性最大层数")
                    .defineInRange("maxStacksHomologousProtection", 20, 1, 100);

            maxStacksResponseRate = builder
                    .comment("Response rate max stacks")
                    .comment("恢复生命值倍率最大层数")
                    .defineInRange("maxStacksResponseRate", 20, 1, 100);

            maxStacksItemDropMultiplier = builder
                    .comment("Item drop multiplier max stacks")
                    .comment("战利品掉落倍率最大层数")
                    .defineInRange("maxStacksItemDropMultiplier", 20, 1, 100);

            maxStacksDiggingSpeed = builder
                    .comment("Digging speed max stacks")
                    .comment("挖掘速度最大层数")
                    .defineInRange("maxStacksDiggingSpeed", 20, 1, 100);

            builder.comment("")
                    .comment("═══ Entity Spawning / 实体生成 ═══")
                    .comment("Note: These values are read during world generation setup")
                    .comment("注意：这些值在世界生成初始化时读取，修改后重启游戏生效");

            kuvaLichSpawnWeight = builder
                    .comment("Kuva Lich spawn weight (set to 0 to disable)")
                    .comment("赤毒玄骸生成权重(设为0可禁用生成)")
                    .defineInRange("kuvaLichSpawnWeight", 10, 0, Integer.MAX_VALUE);

            kuvaLichMinSpawnCount = builder
                    .comment("Kuva Lich minimum spawn count")
                    .comment("赤毒玄骸最小生成数量")
                    .defineInRange("kuvaLichMinSpawnCount", 1, 1, Integer.MAX_VALUE);

            kuvaLichMaxSpawnCount = builder
                    .comment("Kuva Lich maximum spawn count")
                    .comment("赤毒玄骸最大生成数量")
                    .defineInRange("kuvaLichMaxSpawnCount", 1, 1, Integer.MAX_VALUE);

            kuvaSlaveSpawnWeight = builder
                    .comment("Kuva Slave spawn weight (set to 0 to disable)")
                    .comment("赤毒奴仆生成权重(设为0可禁用生成)")
                    .defineInRange("kuvaSlaveSpawnWeight", 25, 0, Integer.MAX_VALUE);

            kuvaSlaveMinSpawnCount = builder
                    .comment("Kuva Slave minimum spawn count")
                    .comment("赤毒奴仆最小生成数量")
                    .defineInRange("kuvaSlaveMinSpawnCount", 1, 1, Integer.MAX_VALUE);

            kuvaSlaveMaxSpawnCount = builder
                    .comment("Kuva Slave maximum spawn count")
                    .comment("赤毒奴仆最大生成数量")
                    .defineInRange("kuvaSlaveMaxSpawnCount", 2, 1, Integer.MAX_VALUE);

            builder.comment("")
                    .comment("═══ Requiem Destroyed Enchantment Drop Chances / 灭骸附魔掉落概率 ═══")
                    .comment("These control drop chances when mining Requiem Ore with the Requiem Destroyed enchantment")
                    .comment("这些控制使用灭骸附魔挖掘安魂矿石时的掉落概率");

            cardDropChanceWithEnchant = builder
                    .comment("Requiem Card drop chance (%) when tool has Requiem Destroyed enchantment")
                    .comment("持有灭骸附魔时安魂卡片掉落概率(%)")
                    .defineInRange("cardDropChanceWithEnchant", 100, 0, 100);

            baseCardDropChance = builder
                    .comment("Base Requiem Card drop chance (%) without the enchantment (affected by Fortune)")
                    .comment("无附魔时的基础安魂卡片掉落概率(%)(受时运影响)")
                    .defineInRange("baseCardDropChance", 25, 0, 100);

            fortuneReductionPerLevel = builder
                    .comment("Reduction to base card drop chance per Fortune level (%)")
                    .comment("每级时运对基础卡片掉落概率的削减量(%)")
                    .defineInRange("fortuneReductionPerLevel", 2.5, 0.0, 100.0);

            minCardDropChance = builder
                    .comment("Minimum card drop chance (%) even with high Fortune levels")
                    .comment("即使时运等级很高时的最低卡片掉落概率(%)")
                    .defineInRange("minCardDropChance", 5, 0, 100);

            commonModuleDropChance = builder
                    .comment("Common (Bronze) module drop chance (%) from Requiem Ore")
                    .comment("安魂矿石掉落青铜模组的概率(%)")
                    .defineInRange("commonModuleDropChance", 15, 0, 100);

            uncommonModuleDropChance = builder
                    .comment("Uncommon (Silver) module drop chance (%) from Requiem Ore")
                    .comment("安魂矿石掉落白银模组的概率(%)")
                    .defineInRange("uncommonModuleDropChance", 10, 0, 100);

            rareModuleDropChance = builder
                    .comment("Rare (Gold) module drop chance (%) from Requiem Ore")
                    .comment("安魂矿石掉落黄金模组的概率(%)")
                    .defineInRange("rareModuleDropChance", 5, 0, 100);

            expMultiplier = builder
                    .comment("Experience multiplier when mining Requiem Ore with Requiem Destroyed enchantment")
                    .comment("持有灭骸附魔挖掘安魂矿石时的经验倍数")
                    .defineInRange("expMultiplier", 2, 1, 100);

            builder.comment("")
                    .comment("═══ Kuva Slave Drop Loot / 赤毒奴仆掉落战利品 ═══");

            slaveKuvaDropChance = builder
                    .comment("Chance (%) to drop Kuva when killed by player")
                    .comment("被玩家击杀时掉落赤毒的概率(%)")
                    .defineInRange("slaveKuvaDropChance", 10, 0, 100);

            slaveKuvaMinAmount = builder
                    .comment("Minimum number of Kuva dropped")
                    .comment("掉落赤毒的最小数量")
                    .defineInRange("slaveKuvaMinAmount", 2, 1, 64);

            slaveKuvaMaxAmount = builder
                    .comment("Maximum number of Kuva dropped")
                    .comment("掉落赤毒的最大数量")
                    .defineInRange("slaveKuvaMaxAmount", 8, 1, 64);

            slaveRivenSliverDropChance = builder
                    .comment("Chance (%) to drop Riven Sliver when killed by player")
                    .comment("被玩家击杀时掉落裂罅碎块的概率(%)")
                    .defineInRange("slaveRivenSliverDropChance", 10, 0, 100);

            slaveRequiemGemBaseChance = builder
                    .comment("Base chance (%) to drop Requiem Gem when killed by player")
                    .comment("被玩家击杀时掉落安魂宝石的基础概率(%)")
                    .defineInRange("slaveRequiemGemBaseChance", 25, 0, 100);

            slaveRequiemGemLootingBonus = builder
                    .comment("Requiem Gem chance bonus per looting enchantment level (%)")
                    .comment("每级时运附魔对安魂宝石掉落概率的加成(%)")
                    .defineInRange("slaveRequiemGemLootingBonus", 2.5, 0.0, 100.0);

            builder.comment("")
                    .comment("═══ Kuva Master Drop Loot / 赤毒玄骸掉落战利品 ═══");

            masterKuvaMinAmount = builder
                    .comment("Minimum number of Kuva dropped on death")
                    .comment("死亡时掉落赤毒的最小数量")
                    .defineInRange("masterKuvaMinAmount", 32, 1, 256);

            masterKuvaMaxAmount = builder
                    .comment("Maximum number of Kuva dropped on death")
                    .comment("死亡时掉落赤毒的最大数量")
                    .defineInRange("masterKuvaMaxAmount", 64, 1, 256);

            masterRivenSliverMinAmount = builder
                    .comment("Minimum number of Riven Sliver dropped on death")
                    .comment("死亡时掉落裂罅碎块的最小数量")
                    .defineInRange("masterRivenSliverMinAmount", 4, 1, 64);

            masterRivenSliverMaxAmount = builder
                    .comment("Maximum number of Riven Sliver dropped on death")
                    .comment("死亡时掉落裂罅碎块的最大数量")
                    .defineInRange("masterRivenSliverMaxAmount", 8, 1, 64);

            masterPrimeModuleChance = builder
                    .comment("Chance (%) to drop a Prime module instead of Riven module on death")
                    .comment("死亡时掉落Prime模组而非裂罅模组的概率(%)")
                    .defineInRange("masterPrimeModuleChance", 50, 0, 100);

            builder.comment("")
                    .comment("═══ Common Drop Settings / 通用掉落设置 ═══");

            moduleWeaponRatio = builder
                    .comment("When dropping a module, chance (%) it is a weapon module")
                    .comment("掉落模组时，该模组为武器模组的概率(%)")
                    .defineInRange("moduleWeaponRatio", 75, 0, 100);

            builder.comment("")
                    .comment("═══ Guidebook / 教程书 ═══");

            enableGuidebook = builder
                    .comment("Give guidebook on first join (requires Patchouli)")
                    .comment("首次进服时发放教程书(需要安装帕秋莉)")
                    .define("enableGuidebook", true);

            builder.pop();
        }
    }

    /**
     * 矿石生成配置
     * <p>
     * 控制安魂矿石和经验矿石的生成参数。
     * 修改后需要重启游戏，仅对新生成区块有效。
     * </p>
     */
    public static class OreGenConfig {

        public final ForgeConfigSpec.IntValue requiemOreVeinCount;
        public final ForgeConfigSpec.IntValue requiemOreVeinSize;
        public final ForgeConfigSpec.IntValue requiemOreMinHeight;
        public final ForgeConfigSpec.IntValue requiemOreMaxHeight;
        public final ForgeConfigSpec.IntValue experienceOreVeinCount;
        public final ForgeConfigSpec.IntValue experienceOreVeinSize;
        public final ForgeConfigSpec.IntValue experienceOreMinHeight;
        public final ForgeConfigSpec.IntValue experienceOreMaxHeight;

        /**
         * 构造矿石生成配置
         *
         * @param builder Forge 配置规范构建器
         */
        public OreGenConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .comment("Ore Generation Configuration")
                    .comment("矿石生成配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .comment("⚠ Changes only take effect after restarting the game!")
                    .comment("⚠ 修改后需重启游戏才能生效！仅对新生成区块有效。")
                    .push("ore_gen");

            builder.comment("").comment("═══ Requiem Ore / 安魂矿石 ═══");

            requiemOreVeinCount = builder
                    .comment("Number of Requiem Ore veins per chunk (0 = disable)")
                    .comment("每个区块生成安魂矿石脉体数量(0=禁用)")
                    .defineInRange("requiemOreVeinCount", 8, 0, 256);

            requiemOreVeinSize = builder
                    .comment("Maximum blocks per Requiem Ore vein")
                    .comment("每个安魂矿石脉体最多包含的方块数")
                    .defineInRange("requiemOreVeinSize", 4, 1, 64);

            requiemOreMinHeight = builder
                    .comment("Minimum Y level for Requiem Ore generation")
                    .comment("安魂矿石生成的最低Y坐标")
                    .defineInRange("requiemOreMinHeight", -64, -64, 320);

            requiemOreMaxHeight = builder
                    .comment("Maximum Y level for Requiem Ore generation")
                    .comment("安魂矿石生成的最高Y坐标")
                    .defineInRange("requiemOreMaxHeight", 28, -64, 320);

            builder.comment("").comment("═══ Experience Ore / 经验矿石 ═══");

            experienceOreVeinCount = builder
                    .comment("Number of Experience Ore veins per chunk (0 = disable)")
                    .comment("每个区块生成经验矿石脉体数量(0=禁用)")
                    .defineInRange("experienceOreVeinCount", 16, 0, 256);

            experienceOreVeinSize = builder
                    .comment("Maximum blocks per Experience Ore vein")
                    .comment("每个经验矿石脉体最多包含的方块数")
                    .defineInRange("experienceOreVeinSize", 4, 1, 64);

            experienceOreMinHeight = builder
                    .comment("Minimum Y level for Experience Ore generation")
                    .comment("经验矿石生成的最低Y坐标")
                    .defineInRange("experienceOreMinHeight", -64, -64, 320);

            experienceOreMaxHeight = builder
                    .comment("Maximum Y level for Experience Ore generation")
                    .comment("经验矿石生成的最高Y坐标")
                    .defineInRange("experienceOreMaxHeight", 128, -64, 320);

            builder.pop();
        }
    }

    /**
     * 赤毒武器基础属性配置
     * <p>
     * 控制每种赤毒武器的攻击伤害、攻击速度和移动速度修正。
     * </p>
     */
    public static class KuvaWeaponConfig {

        /** 武器属性全局倍率 / Global weapon attribute multiplier */
        public final ForgeConfigSpec.DoubleValue attributeMultiplier;

        public final ForgeConfigSpec.DoubleValue attackDamageKuvaShildeg;
        public final ForgeConfigSpec.DoubleValue attackSpeedKuvaShildeg;
        public final ForgeConfigSpec.DoubleValue movementSpeedKuvaShildeg;

        public final ForgeConfigSpec.DoubleValue attackDamagePennant;
        public final ForgeConfigSpec.DoubleValue attackSpeedPennant;
        public final ForgeConfigSpec.DoubleValue movementSpeedPennant;

        public final ForgeConfigSpec.DoubleValue attackDamageGuandaoPrime;
        public final ForgeConfigSpec.DoubleValue attackSpeedGuandaoPrime;
        public final ForgeConfigSpec.DoubleValue movementSpeedGuandaoPrime;

        public final ForgeConfigSpec.DoubleValue attackDamageParacesis;
        public final ForgeConfigSpec.DoubleValue attackSpeedParacesis;
        public final ForgeConfigSpec.DoubleValue movementSpeedParacesis;

        public final ForgeConfigSpec.DoubleValue attackDamageArcaTitron;
        public final ForgeConfigSpec.DoubleValue attackSpeedArcaTitron;
        public final ForgeConfigSpec.DoubleValue movementSpeedArcaTitron;

        public final ForgeConfigSpec.DoubleValue attackDamageReaperPrime;
        public final ForgeConfigSpec.DoubleValue attackSpeedReaperPrime;
        public final ForgeConfigSpec.DoubleValue movementSpeedReaperPrime;

        public final ForgeConfigSpec.DoubleValue attackDamageVitrica;
        public final ForgeConfigSpec.DoubleValue attackSpeedVitrica;
        public final ForgeConfigSpec.DoubleValue movementSpeedVitrica;

        public final ForgeConfigSpec.DoubleValue attackDamageGramPrime;
        public final ForgeConfigSpec.DoubleValue attackSpeedGramPrime;
        public final ForgeConfigSpec.DoubleValue movementSpeedGramPrime;

        public final ForgeConfigSpec.DoubleValue attackDamageSanctiMagistar;
        public final ForgeConfigSpec.DoubleValue attackSpeedSanctiMagistar;
        public final ForgeConfigSpec.DoubleValue movementSpeedSanctiMagistar;

        public final ForgeConfigSpec.DoubleValue attackDamageDestrezaPrime;
        public final ForgeConfigSpec.DoubleValue attackSpeedDestrezaPrime;
        public final ForgeConfigSpec.DoubleValue movementSpeedDestrezaPrime;

        public final ForgeConfigSpec.DoubleValue attackDamagePrismaVeritux;
        public final ForgeConfigSpec.DoubleValue attackSpeedPrismaVeritux;
        public final ForgeConfigSpec.DoubleValue movementSpeedPrismaVeritux;

        public final ForgeConfigSpec.DoubleValue attackDamageMachete;
        public final ForgeConfigSpec.DoubleValue attackSpeedMachete;
        public final ForgeConfigSpec.DoubleValue movementSpeedMachete;

        public final ForgeConfigSpec.DoubleValue attackDamageCaustacyst;
        public final ForgeConfigSpec.DoubleValue attackSpeedCaustacyst;
        public final ForgeConfigSpec.DoubleValue movementSpeedCaustacyst;

        /**
         * 构造赤毒武器属性配置
         *
         * @param builder Forge 配置规范构建器
         */
        public KuvaWeaponConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .comment("Kuva Weapon Base Attributes Configuration")
                    .comment("赤毒武器基础属性配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("kuva_weapon");

            attributeMultiplier = builder
                    .comment("Weapon attribute multiplier")
                    .comment("武器属性倍率")
                    .defineInRange("attributeMultiplier", 1.5, 0.0, Double.MAX_VALUE);

            builder.comment("").comment("Kuva Shildeg / 赤毒希尔德");
            attackDamageKuvaShildeg = builder.defineInRange("attackDamageKuvaShildeg", 44.0, 0.0, Double.MAX_VALUE);
            attackSpeedKuvaShildeg = builder.defineInRange("attackSpeedKuvaShildeg", 0.7, 0.0, Double.MAX_VALUE);
            movementSpeedKuvaShildeg = builder.defineInRange("movementSpeedKuvaShildeg", -0.2, -1.0, 1.0);

            builder.comment("").comment("Pennant / 尖幡");
            attackDamagePennant = builder.defineInRange("attackDamagePennant", 28.0, 0.0, Double.MAX_VALUE);
            attackSpeedPennant = builder.defineInRange("attackSpeedPennant", 1.1, 0.0, Double.MAX_VALUE);
            movementSpeedPennant = builder.defineInRange("movementSpeedPennant", 0.025, -1.0, 1.0);

            builder.comment("").comment("Guandao Prime / 关刀Prime");
            attackDamageGuandaoPrime = builder.defineInRange("attackDamageGuandaoPrime", 16.0, 0.0, Double.MAX_VALUE);
            attackSpeedGuandaoPrime = builder.defineInRange("attackSpeedGuandaoPrime", 2.0, 0.0, Double.MAX_VALUE);
            movementSpeedGuandaoPrime = builder.defineInRange("movementSpeedGuandaoPrime", 0.075, -1.0, 1.0);

            builder.comment("").comment("Paracesis / 心智之殁");
            attackDamageParacesis = builder.defineInRange("attackDamageParacesis", 34.0, 0.0, Double.MAX_VALUE);
            attackSpeedParacesis = builder.defineInRange("attackSpeedParacesis", 0.9, 0.0, Double.MAX_VALUE);
            movementSpeedParacesis = builder.defineInRange("movementSpeedParacesis", -0.1, -1.0, 1.0);

            builder.comment("").comment("Arca Titron / 弧电振子锤");
            attackDamageArcaTitron = builder.defineInRange("attackDamageArcaTitron", 38.0, 0.0, Double.MAX_VALUE);
            attackSpeedArcaTitron = builder.defineInRange("attackSpeedArcaTitron", 0.75, 0.0, Double.MAX_VALUE);
            movementSpeedArcaTitron = builder.defineInRange("movementSpeedArcaTitron", -0.15, -1.0, 1.0);

            builder.comment("").comment("Reaper Prime / 收割者Prime");
            attackDamageReaperPrime = builder.defineInRange("attackDamageReaperPrime", 18.0, 0.0, Double.MAX_VALUE);
            attackSpeedReaperPrime = builder.defineInRange("attackSpeedReaperPrime", 1.75, 0.0, Double.MAX_VALUE);
            movementSpeedReaperPrime = builder.defineInRange("movementSpeedReaperPrime", 0.05, -1.0, 1.0);

            builder.comment("").comment("Vitrica / 金璃剑");
            attackDamageVitrica = builder.defineInRange("attackDamageVitrica", 50.0, 0.0, Double.MAX_VALUE);
            attackSpeedVitrica = builder.defineInRange("attackSpeedVitrica", 0.65, 0.0, Double.MAX_VALUE);
            movementSpeedVitrica = builder.defineInRange("movementSpeedVitrica", -0.2, -1.0, 1.0);

            builder.comment("").comment("Gram Prime / 格拉姆Prime");
            attackDamageGramPrime = builder.defineInRange("attackDamageGramPrime", 46.0, 0.0, Double.MAX_VALUE);
            attackSpeedGramPrime = builder.defineInRange("attackSpeedGramPrime", 0.7, 0.0, Double.MAX_VALUE);
            movementSpeedGramPrime = builder.defineInRange("movementSpeedGramPrime", -0.15, -1.0, 1.0);

            builder.comment("").comment("Sancti Magistar / 圣洁执法者");
            attackDamageSanctiMagistar = builder.defineInRange("attackDamageSanctiMagistar", 36.0, 0.0, Double.MAX_VALUE);
            attackSpeedSanctiMagistar = builder.defineInRange("attackSpeedSanctiMagistar", 0.85, 0.0, Double.MAX_VALUE);
            movementSpeedSanctiMagistar = builder.defineInRange("movementSpeedSanctiMagistar", -0.075, -1.0, 1.0);

            builder.comment("").comment("Destreza Prime / 技巧之剑Prime");
            attackDamageDestrezaPrime = builder.defineInRange("attackDamageDestrezaPrime", 24.0, 0.0, Double.MAX_VALUE);
            attackSpeedDestrezaPrime = builder.defineInRange("attackSpeedDestrezaPrime", 1.4, 0.0, Double.MAX_VALUE);
            movementSpeedDestrezaPrime = builder.defineInRange("movementSpeedDestrezaPrime", 0.125, -1.0, 1.0);

            builder.comment("").comment("Prisma Veritux / 棱晶真理巨剑");
            attackDamagePrismaVeritux = builder.defineInRange("attackDamagePrismaVeritux", 100.0, 0.0, Double.MAX_VALUE);
            attackSpeedPrismaVeritux = builder.defineInRange("attackSpeedPrismaVeritux", 0.45, 0.0, Double.MAX_VALUE);
            movementSpeedPrismaVeritux = builder.defineInRange("movementSpeedPrismaVeritux", -0.6, -1.0, 1.0);

            builder.comment("").comment("Machete / 马谢特砍刀");
            attackDamageMachete = builder.defineInRange("attackDamageMachete", 32.0, 0.0, Double.MAX_VALUE);
            attackSpeedMachete = builder.defineInRange("attackSpeedMachete", 1.55, 0.0, Double.MAX_VALUE);
            movementSpeedMachete = builder.defineInRange("movementSpeedMachete", 0.025, -1.0, 1.0);

            builder.comment("").comment("Caustacyst / 灼蚀变体镰");
            attackDamageCaustacyst = builder.defineInRange("attackDamageCaustacyst", 28.0, 0.0, Double.MAX_VALUE);
            attackSpeedCaustacyst = builder.defineInRange("attackSpeedCaustacyst", 1.3, 0.0, Double.MAX_VALUE);
            movementSpeedCaustacyst = builder.defineInRange("movementSpeedCaustacyst", 0.125, -1.0, 1.0);

            builder.pop();
        }
    }

    /**
     * 配置重新加载事件处理
     *
     * @param event 配置变更事件
     */
    @SubscribeEvent
    public static void onConfigReload(final ModConfigEvent event) {
    }
}