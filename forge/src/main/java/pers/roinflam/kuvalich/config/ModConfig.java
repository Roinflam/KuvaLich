package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 赤毒玄骸模组配置类
 * Kuva Lich Mod Configuration Class
 *
 * 统一管理所有配置项
 * Unified management of all configuration items
 * 配置文件位置:config/kuvalich-common.toml
 * Config file location: config/kuvalich-common.toml
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModConfig {

    public static final ForgeConfigSpec COMMON_CONFIG;
    public static final KuvaLichConfig KUVA_LICH;
    public static final KuvaWeaponConfig KUVA_WEAPON;

    static {
        ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();

        KUVA_LICH = new KuvaLichConfig(COMMON_BUILDER);
        KUVA_WEAPON = new KuvaWeaponConfig(COMMON_BUILDER);

        COMMON_CONFIG = COMMON_BUILDER.build();
    }

    /**
     * 玄骸系统配置
     * Kuva Lich System Configuration
     */
    public static class KuvaLichConfig {

        // ========== 调试日志 / Debug Logging ==========

        public final ForgeConfigSpec.BooleanValue enableDetailedLogging;

        // ========== 护盾系统 / Shield System ==========

        public final ForgeConfigSpec.DoubleValue shieldCapMultiplier;

        // ========== 物品没收系统 / Item Confiscation System ==========

        public final ForgeConfigSpec.DoubleValue confiscationChance;
        public final ForgeConfigSpec.DoubleValue requiemUltimatumDropChance;

        // ========== 解密进度 / Decryption Progress ==========

        public final ForgeConfigSpec.IntValue minDecryptionProgress;
        public final ForgeConfigSpec.IntValue maxDecryptionProgress;
        public final ForgeConfigSpec.IntValue firstStage;
        public final ForgeConfigSpec.IntValue secondStage;
        public final ForgeConfigSpec.IntValue thirdStage;
        public final ForgeConfigSpec.DoubleValue masterPotionMultiplier;

        // ========== 伤害系统 / Damage System ==========

        public final ForgeConfigSpec.BooleanValue damageDisplay;
        public final ForgeConfigSpec.BooleanValue enableDamageNumbers;
        public final ForgeConfigSpec.BooleanValue enableTrueDamage;  // ✅ 新增:真伤系统开关
        public final ForgeConfigSpec.DoubleValue battleBoost;
        public final ForgeConfigSpec.DoubleValue reducedDamage;
        public final ForgeConfigSpec.DoubleValue increaseDamage;

        // ========== 武器等级系统 / Weapon Level System ==========

        public final ForgeConfigSpec.IntValue benchmarkLevel;
        public final ForgeConfigSpec.IntValue baseMinimumLevel;
        public final ForgeConfigSpec.IntValue baseMaximumLevel;
        public final ForgeConfigSpec.IntValue minimumLevelCapIncrease;
        public final ForgeConfigSpec.IntValue maximumLevelCapIncrease;
        public final ForgeConfigSpec.IntValue minimumLevel;
        public final ForgeConfigSpec.IntValue maximumLevel;
        public final ForgeConfigSpec.DoubleValue upgradeMultiplier;
        public final ForgeConfigSpec.IntValue upgradeLimit;

        // ========== 武器击杀叠层系统 / Weapon Kill Stack System ==========

        public final ForgeConfigSpec.IntValue weaponStackDecayTicks;
        public final ForgeConfigSpec.IntValue maxStacksBaseDamage;
        public final ForgeConfigSpec.IntValue maxStacksMultishot;
        public final ForgeConfigSpec.IntValue maxStacksMeleeCritMult;
        public final ForgeConfigSpec.IntValue maxStacksTriggerChance;
        public final ForgeConfigSpec.IntValue maxStacksAttackRange;
        public final ForgeConfigSpec.IntValue maxStacksAttackSpeed;
        public final ForgeConfigSpec.IntValue maxStacksBurstingRadius;
        public final ForgeConfigSpec.IntValue maxStacksFiringRate;

        // ========== 战甲击杀叠层系统 / Warframe Kill Stack System ==========

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

        // ========== 实体生成 / Entity Spawning ==========

        public final ForgeConfigSpec.IntValue kuvaLichSpawnWeight;
        public final ForgeConfigSpec.IntValue kuvaLichMinSpawnCount;
        public final ForgeConfigSpec.IntValue kuvaLichMaxSpawnCount;
        public final ForgeConfigSpec.IntValue kuvaSlaveSpawnWeight;
        public final ForgeConfigSpec.IntValue kuvaSlaveMinSpawnCount;
        public final ForgeConfigSpec.IntValue kuvaSlaveMaxSpawnCount;

        public KuvaLichConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("═══════════════════════════════════════════════════════════════")
                    .comment("Kuva Lich System Configuration")
                    .comment("赤毒玄骸系统配置")
                    .comment("═══════════════════════════════════════════════════════════════")
                    .push("kuva_lich");

            // ========== 调试日志 ==========
            builder.comment("")
                    .comment("═══ Debug Logging / 调试日志 ═══");

            enableDetailedLogging = builder
                    .comment("Enable detailed debug logging")
                    .comment("启用详细调试日志")
                    .define("enableDetailedLogging", false);

            // ========== 护盾系统 ==========
            builder.comment("")
                    .comment("═══ Shield System / 护盾系统 ═══");

            shieldCapMultiplier = builder
                    .comment("Shield cap multiplier (based on max health)")
                    .comment("护盾上限倍率(基于最大生命值)")
                    .comment("Formula: Shield Cap = Max Health × Shield Attribute × This Multiplier")
                    .comment("公式: 护盾上限 = 最大生命值 × 护盾属性 × 此倍率")
                    .comment("Example: 0.5 means shield cap is 50% of max health when shield attribute = 1.0")
                    .comment("示例: 0.5 表示当护盾属性=1.0时,护盾上限为最大生命值的50%")
                    .defineInRange("shieldCapMultiplier", 0.5, 0.0, 10.0);

            // ========== 物品没收系统 ==========
            builder.comment("")
                    .comment("═══ Item Confiscation System / 物品没收系统 ═══");

            confiscationChance = builder
                    .comment("Item confiscation chance (%)")
                    .comment("物品没收概率(%)")
                    .defineInRange("confiscationChance", 1.0, 0.0, 100.0);

            requiemUltimatumDropChance = builder
                    .comment("Requiem Ultimatum drop chance after successful decryption (%)")
                    .comment("成功破解后安魂通牒掉落几率(%)")
                    .defineInRange("requiemUltimatumDropChance", 25.0, 0.0, 100.0);

            // ========== 解密进度 ==========
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

            // ========== 伤害系统 ==========
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

            // ✅ 新增:真伤系统开关
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

            // ========== 武器等级系统 ==========
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

            // ========== 武器击杀叠层系统 ==========
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

            // ========== 战甲击杀叠层系统 ==========
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

            // ========== 实体生成 ==========
            builder.comment("")
                    .comment("═══ Entity Spawning / 实体生成 ═══");

            kuvaLichSpawnWeight = builder
                    .comment("Kuva Lich spawn weight")
                    .comment("赤毒玄骸生成权重")
                    .defineInRange("kuvaLichSpawnWeight", 5, 1, Integer.MAX_VALUE);

            kuvaLichMinSpawnCount = builder
                    .comment("Kuva Lich minimum spawn count")
                    .comment("赤毒玄骸最小生成数量")
                    .defineInRange("kuvaLichMinSpawnCount", 1, 1, Integer.MAX_VALUE);

            kuvaLichMaxSpawnCount = builder
                    .comment("Kuva Lich maximum spawn count")
                    .comment("赤毒玄骸最大生成数量")
                    .defineInRange("kuvaLichMaxSpawnCount", 1, 1, Integer.MAX_VALUE);

            kuvaSlaveSpawnWeight = builder
                    .comment("Kuva Slave spawn weight")
                    .comment("赤毒奴仆生成权重")
                    .defineInRange("kuvaSlaveSpawnWeight", 10, 1, Integer.MAX_VALUE);

            kuvaSlaveMinSpawnCount = builder
                    .comment("Kuva Slave minimum spawn count")
                    .comment("赤毒奴仆最小生成数量")
                    .defineInRange("kuvaSlaveMinSpawnCount", 1, 1, Integer.MAX_VALUE);

            kuvaSlaveMaxSpawnCount = builder
                    .comment("Kuva Slave maximum spawn count")
                    .comment("赤毒奴仆最大生成数量")
                    .defineInRange("kuvaSlaveMaxSpawnCount", 3, 1, Integer.MAX_VALUE);

            builder.pop();
        }
    }

    /**
     * 赤毒武器配置
     * Kuva Weapon Configuration
     */
    public static class KuvaWeaponConfig {

        public final ForgeConfigSpec.DoubleValue attributeMultiplier;

        // 赤毒希尔德
        public final ForgeConfigSpec.DoubleValue attackDamageKuvaShildeg;
        public final ForgeConfigSpec.DoubleValue attackSpeedKuvaShildeg;
        public final ForgeConfigSpec.DoubleValue movementSpeedKuvaShildeg;

        // 尖幡
        public final ForgeConfigSpec.DoubleValue attackDamagePennant;
        public final ForgeConfigSpec.DoubleValue attackSpeedPennant;
        public final ForgeConfigSpec.DoubleValue movementSpeedPennant;

        // 关刀Prime
        public final ForgeConfigSpec.DoubleValue attackDamageGuandaoPrime;
        public final ForgeConfigSpec.DoubleValue attackSpeedGuandaoPrime;
        public final ForgeConfigSpec.DoubleValue movementSpeedGuandaoPrime;

        // 心智之殁
        public final ForgeConfigSpec.DoubleValue attackDamageParacesis;
        public final ForgeConfigSpec.DoubleValue attackSpeedParacesis;
        public final ForgeConfigSpec.DoubleValue movementSpeedParacesis;

        // 弧电振子锤
        public final ForgeConfigSpec.DoubleValue attackDamageArcaTitron;
        public final ForgeConfigSpec.DoubleValue attackSpeedArcaTitron;
        public final ForgeConfigSpec.DoubleValue movementSpeedArcaTitron;

        // 收割者Prime
        public final ForgeConfigSpec.DoubleValue attackDamageReaperPrime;
        public final ForgeConfigSpec.DoubleValue attackSpeedReaperPrime;
        public final ForgeConfigSpec.DoubleValue movementSpeedReaperPrime;

        // 金璃剑
        public final ForgeConfigSpec.DoubleValue attackDamageVitrica;
        public final ForgeConfigSpec.DoubleValue attackSpeedVitrica;
        public final ForgeConfigSpec.DoubleValue movementSpeedVitrica;

        // 格拉姆Prime
        public final ForgeConfigSpec.DoubleValue attackDamageGramPrime;
        public final ForgeConfigSpec.DoubleValue attackSpeedGramPrime;
        public final ForgeConfigSpec.DoubleValue movementSpeedGramPrime;

        // 圣洁执法者
        public final ForgeConfigSpec.DoubleValue attackDamageSanctiMagistar;
        public final ForgeConfigSpec.DoubleValue attackSpeedSanctiMagistar;
        public final ForgeConfigSpec.DoubleValue movementSpeedSanctiMagistar;

        // 技巧之剑Prime
        public final ForgeConfigSpec.DoubleValue attackDamageDestrezaPrime;
        public final ForgeConfigSpec.DoubleValue attackSpeedDestrezaPrime;
        public final ForgeConfigSpec.DoubleValue movementSpeedDestrezaPrime;

        // 棱晶真理巨剑
        public final ForgeConfigSpec.DoubleValue attackDamagePrismaVeritux;
        public final ForgeConfigSpec.DoubleValue attackSpeedPrismaVeritux;
        public final ForgeConfigSpec.DoubleValue movementSpeedPrismaVeritux;

        // 马谢特砍刀
        public final ForgeConfigSpec.DoubleValue attackDamageMachete;
        public final ForgeConfigSpec.DoubleValue attackSpeedMachete;
        public final ForgeConfigSpec.DoubleValue movementSpeedMachete;

        // 灼蚀变体镰
        public final ForgeConfigSpec.DoubleValue attackDamageCaustacyst;
        public final ForgeConfigSpec.DoubleValue attackSpeedCaustacyst;
        public final ForgeConfigSpec.DoubleValue movementSpeedCaustacyst;

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

            // 赤毒希尔德
            builder.comment("").comment("Kuva Shildeg / 赤毒希尔德");
            attackDamageKuvaShildeg = builder.defineInRange("attackDamageKuvaShildeg", 44.0, 0.0, Double.MAX_VALUE);
            attackSpeedKuvaShildeg = builder.defineInRange("attackSpeedKuvaShildeg", 0.7, 0.0, Double.MAX_VALUE);
            movementSpeedKuvaShildeg = builder.defineInRange("movementSpeedKuvaShildeg", -0.2, -1.0, 1.0);

            // 尖幡
            builder.comment("").comment("Pennant / 尖幡");
            attackDamagePennant = builder.defineInRange("attackDamagePennant", 28.0, 0.0, Double.MAX_VALUE);
            attackSpeedPennant = builder.defineInRange("attackSpeedPennant", 1.1, 0.0, Double.MAX_VALUE);
            movementSpeedPennant = builder.defineInRange("movementSpeedPennant", 0.025, -1.0, 1.0);

            // 关刀Prime
            builder.comment("").comment("Guandao Prime / 关刀Prime");
            attackDamageGuandaoPrime = builder.defineInRange("attackDamageGuandaoPrime", 16.0, 0.0, Double.MAX_VALUE);
            attackSpeedGuandaoPrime = builder.defineInRange("attackSpeedGuandaoPrime", 2.0, 0.0, Double.MAX_VALUE);
            movementSpeedGuandaoPrime = builder.defineInRange("movementSpeedGuandaoPrime", 0.075, -1.0, 1.0);

            // 心智之殁
            builder.comment("").comment("Paracesis / 心智之殁");
            attackDamageParacesis = builder.defineInRange("attackDamageParacesis", 34.0, 0.0, Double.MAX_VALUE);
            attackSpeedParacesis = builder.defineInRange("attackSpeedParacesis", 0.9, 0.0, Double.MAX_VALUE);
            movementSpeedParacesis = builder.defineInRange("movementSpeedParacesis", -0.1, -1.0, 1.0);

            // 弧电振子锤
            builder.comment("").comment("Arca Titron / 弧电振子锤");
            attackDamageArcaTitron = builder.defineInRange("attackDamageArcaTitron", 38.0, 0.0, Double.MAX_VALUE);
            attackSpeedArcaTitron = builder.defineInRange("attackSpeedArcaTitron", 0.75, 0.0, Double.MAX_VALUE);
            movementSpeedArcaTitron = builder.defineInRange("movementSpeedArcaTitron", -0.15, -1.0, 1.0);

            // 收割者Prime
            builder.comment("").comment("Reaper Prime / 收割者Prime");
            attackDamageReaperPrime = builder.defineInRange("attackDamageReaperPrime", 18.0, 0.0, Double.MAX_VALUE);
            attackSpeedReaperPrime = builder.defineInRange("attackSpeedReaperPrime", 1.75, 0.0, Double.MAX_VALUE);
            movementSpeedReaperPrime = builder.defineInRange("movementSpeedReaperPrime", 0.05, -1.0, 1.0);

            // 金璃剑
            builder.comment("").comment("Vitrica / 金璃剑");
            attackDamageVitrica = builder.defineInRange("attackDamageVitrica", 50.0, 0.0, Double.MAX_VALUE);
            attackSpeedVitrica = builder.defineInRange("attackSpeedVitrica", 0.65, 0.0, Double.MAX_VALUE);
            movementSpeedVitrica = builder.defineInRange("movementSpeedVitrica", -0.2, -1.0, 1.0);

            // 格拉姆Prime
            builder.comment("").comment("Gram Prime / 格拉姆Prime");
            attackDamageGramPrime = builder.defineInRange("attackDamageGramPrime", 46.0, 0.0, Double.MAX_VALUE);
            attackSpeedGramPrime = builder.defineInRange("attackSpeedGramPrime", 0.7, 0.0, Double.MAX_VALUE);
            movementSpeedGramPrime = builder.defineInRange("movementSpeedGramPrime", -0.15, -1.0, 1.0);

            // 圣洁执法者
            builder.comment("").comment("Sancti Magistar / 圣洁执法者");
            attackDamageSanctiMagistar = builder.defineInRange("attackDamageSanctiMagistar", 36.0, 0.0, Double.MAX_VALUE);
            attackSpeedSanctiMagistar = builder.defineInRange("attackSpeedSanctiMagistar", 0.85, 0.0, Double.MAX_VALUE);
            movementSpeedSanctiMagistar = builder.defineInRange("movementSpeedSanctiMagistar", -0.075, -1.0, 1.0);

            // 技巧之剑Prime
            builder.comment("").comment("Destreza Prime / 技巧之剑Prime");
            attackDamageDestrezaPrime = builder.defineInRange("attackDamageDestrezaPrime", 24.0, 0.0, Double.MAX_VALUE);
            attackSpeedDestrezaPrime = builder.defineInRange("attackSpeedDestrezaPrime", 1.4, 0.0, Double.MAX_VALUE);
            movementSpeedDestrezaPrime = builder.defineInRange("movementSpeedDestrezaPrime", 0.125, -1.0, 1.0);

            // 棱晶真理巨剑
            builder.comment("").comment("Prisma Veritux / 棱晶真理巨剑");
            attackDamagePrismaVeritux = builder.defineInRange("attackDamagePrismaVeritux", 100.0, 0.0, Double.MAX_VALUE);
            attackSpeedPrismaVeritux = builder.defineInRange("attackSpeedPrismaVeritux", 0.45, 0.0, Double.MAX_VALUE);
            movementSpeedPrismaVeritux = builder.defineInRange("movementSpeedPrismaVeritux", -0.6, -1.0, 1.0);

            // 马谢特砍刀
            builder.comment("").comment("Machete / 马谢特砍刀");
            attackDamageMachete = builder.defineInRange("attackDamageMachete", 32.0, 0.0, Double.MAX_VALUE);
            attackSpeedMachete = builder.defineInRange("attackSpeedMachete", 1.55, 0.0, Double.MAX_VALUE);
            movementSpeedMachete = builder.defineInRange("movementSpeedMachete", 0.025, -1.0, 1.0);

            // 灼蚀变体镰
            builder.comment("").comment("Caustacyst / 灼蚀变体镰");
            attackDamageCaustacyst = builder.defineInRange("attackDamageCaustacyst", 28.0, 0.0, Double.MAX_VALUE);
            attackSpeedCaustacyst = builder.defineInRange("attackSpeedCaustacyst", 1.3, 0.0, Double.MAX_VALUE);
            movementSpeedCaustacyst = builder.defineInRange("movementSpeedCaustacyst", 0.125, -1.0, 1.0);

            builder.pop();
        }
    }

    /**
     * 配置重载事件处理
     * Config reload event handler
     */
    @SubscribeEvent
    public static void onConfigReload(final ModConfigEvent event) {
        // 配置重载时的逻辑（如果需要）
        // Logic when config is reloaded (if needed)
    }
}