package pers.roinflam.kuvalich.config;

import net.minecraftforge.common.config.Config;
import pers.roinflam.kuvalich.utils.Reference;

/**
 * 赤毒武器配置类
 *
 * 包含所有赤毒武器的基础属性配置
 * 配置文件位置：config/kuvalich.cfg
 *
 * @author RoinFlam
 */
@Config(modid = Reference.MOD_ID, category = "KuvaWeapon")
public final class ConfigKuvaWeapon {

    // ========== 武器系统配置 ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Weapon Attribute Multiplier",
            "     Controls variance in weapon stat generation",
            "     • 1.0 = Small variance - more consistent stats",
            "     • 1.5 = Normal variance (default)",
            "     • 2.0 = Large variance - more random stats",
            "     Higher values = greater difference between weapons",
            "",
            "[中文] 武器属性倍率",
            "     控制武器属性生成的差异度",
            "     • 1.0 = 小差异 - 属性更稳定",
            "     • 1.5 = 正常差异(默认)",
            "     • 2.0 = 大差异 - 属性更随机",
            "     数值越高 = 武器间差异越大",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attributeMultiplier")
    @Config.RangeDouble(min = 0)
    public static double attributeMultiplier = 1.5;

    // ========== Kuva Shildeg (赤毒希尔德) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Shildeg - Base Attack Damage",
            "     • 30 = Weak",
            "     • 44 = Normal (default)",
            "     • 60 = Strong",
            "     Heavy hammer with high damage, slow speed",
            "",
            "[中文] 赤毒希尔德 - 基础攻击伤害",
            "     • 30 = 较弱",
            "     • 44 = 正常(默认)",
            "     • 60 = 强力",
            "     重型锤类武器，高伤害低速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageKuvaShildeg")
    @Config.RangeDouble(min = 0)
    public static double attackDamageKuvaShildeg = 44;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Shildeg - Base Attack Speed",
            "     • 0.5 = Very slow",
            "     • 0.7 = Slow (default)",
            "     • 1.0 = Normal",
            "     Negative value = slower than normal",
            "",
            "[中文] 赤毒希尔德 - 基础攻击速度",
            "     • 0.5 = 非常慢",
            "     • 0.7 = 较慢(默认)",
            "     • 1.0 = 正常",
            "     低于1.0 = 低于正常速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedKuvaShildeg")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedKuvaShildeg = 0.7;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Kuva Shildeg - Movement Speed Modifier",
            "     • -0.3 = Very slow movement",
            "     • -0.2 = Slow movement (default)",
            "     • -0.1 = Slightly slow movement",
            "     Negative values reduce movement speed when held",
            "",
            "[中文] 赤毒希尔德 - 移动速度修正",
            "     • -0.3 = 移动非常慢",
            "     • -0.2 = 移动较慢(默认)",
            "     • -0.1 = 移动略慢",
            "     负值会降低手持时的移动速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedKuvaShildeg")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedKuvaShildeg = -0.2;

    // ========== Pennant (尖幡) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Pennant - Base Attack Damage",
            "     • 20 = Weak",
            "     • 28 = Normal (default)",
            "     • 36 = Strong",
            "     Balanced two-handed sword",
            "",
            "[中文] 尖幡 - 基础攻击伤害",
            "     • 20 = 较弱",
            "     • 28 = 正常(默认)",
            "     • 36 = 强力",
            "     平衡型双手剑",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamagePennant")
    @Config.RangeDouble(min = 0)
    public static double attackDamagePennant = 28;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Pennant - Base Attack Speed",
            "     • 0.9 = Slow",
            "     • 1.1 = Fast (default)",
            "     • 1.3 = Very fast",
            "     Above 1.0 = faster than normal",
            "",
            "[中文] 尖幡 - 基础攻击速度",
            "     • 0.9 = 较慢",
            "     • 1.1 = 较快(默认)",
            "     • 1.3 = 非常快",
            "     高于1.0 = 高于正常速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedPennant")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedPennant = 1.1;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Pennant - Movement Speed Modifier",
            "     • 0.0 = No change",
            "     • 0.025 = Slight boost (default)",
            "     • 0.05 = Noticeable boost",
            "     Positive values increase movement speed",
            "",
            "[中文] 尖幡 - 移动速度修正",
            "     • 0.0 = 无变化",
            "     • 0.025 = 轻微提升(默认)",
            "     • 0.05 = 明显提升",
            "     正值会提升移动速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedPennant")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedPennant = 0.025;

    // ========== Guandao Prime (关刀Prime) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Guandao Prime - Base Attack Damage",
            "     • 10 = Weak",
            "     • 16 = Normal (default)",
            "     • 24 = Strong",
            "     Fast polearm with lower damage",
            "",
            "[中文] 关刀Prime - 基础攻击伤害",
            "     • 10 = 较弱",
            "     • 16 = 正常(默认)",
            "     • 24 = 强力",
            "     快速长柄武器，伤害较低",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageGuandaoPrime")
    @Config.RangeDouble(min = 0)
    public static double attackDamageGuandaoPrime = 16;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Guandao Prime - Base Attack Speed",
            "     • 1.5 = Fast",
            "     • 2.0 = Very fast (default)",
            "     • 2.5 = Extremely fast",
            "     One of the fastest weapons",
            "",
            "[中文] 关刀Prime - 基础攻击速度",
            "     • 1.5 = 较快",
            "     • 2.0 = 非常快(默认)",
            "     • 2.5 = 极快",
            "     最快的武器之一",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedGuandaoPrime")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedGuandaoPrime = 2;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Guandao Prime - Movement Speed Modifier",
            "     • 0.05 = Slight boost",
            "     • 0.075 = Moderate boost (default)",
            "     • 0.1 = Large boost",
            "     Good mobility weapon",
            "",
            "[中文] 关刀Prime - 移动速度修正",
            "     • 0.05 = 轻微提升",
            "     • 0.075 = 中等提升(默认)",
            "     • 0.1 = 大幅提升",
            "     机动性良好的武器",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedGuandaoPrime")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedGuandaoPrime = 0.075;

    // ========== Paracesis (心智之殁) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Paracesis - Base Attack Damage",
            "     • 24 = Weak",
            "     • 34 = Normal (default)",
            "     • 44 = Strong",
            "     Heavy two-handed sword, moderate speed",
            "",
            "[中文] 心智之殁 - 基础攻击伤害",
            "     • 24 = 较弱",
            "     • 34 = 正常(默认)",
            "     • 44 = 强力",
            "     重型双手剑，中等速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageParacesis")
    @Config.RangeDouble(min = 0)
    public static double attackDamageParacesis = 34;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Paracesis - Base Attack Speed",
            "     • 0.7 = Slow",
            "     • 0.9 = Moderate (default)",
            "     • 1.1 = Fast",
            "     Balanced attack speed",
            "",
            "[中文] 心智之殁 - 基础攻击速度",
            "     • 0.7 = 较慢",
            "     • 0.9 = 中等(默认)",
            "     • 1.1 = 较快",
            "     平衡的攻击速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedParacesis")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedParacesis = 0.9;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Paracesis - Movement Speed Modifier",
            "     • -0.15 = Moderate penalty",
            "     • -0.1 = Small penalty (default)",
            "     • -0.05 = Minimal penalty",
            "     Heavy weapon with mobility cost",
            "",
            "[中文] 心智之殁 - 移动速度修正",
            "     • -0.15 = 中等惩罚",
            "     • -0.1 = 较小惩罚(默认)",
            "     • -0.05 = 轻微惩罚",
            "     重型武器有机动性代价",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedParacesis")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedParacesis = -0.1;

    // ========== Arca Titron (弧电振子锤) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Arca Titron - Base Attack Damage",
            "     • 28 = Weak",
            "     • 38 = Normal (default)",
            "     • 50 = Strong",
            "     Energy hammer with electrical damage",
            "",
            "[中文] 弧电振子锤 - 基础攻击伤害",
            "     • 28 = 较弱",
            "     • 38 = 正常(默认)",
            "     • 50 = 强力",
            "     能量锤，附带电击伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageArcaTitron")
    @Config.RangeDouble(min = 0)
    public static double attackDamageArcaTitron = 38;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Arca Titron - Base Attack Speed",
            "     • 0.6 = Very slow",
            "     • 0.75 = Slow (default)",
            "     • 0.9 = Moderate",
            "     Heavy weapon class",
            "",
            "[中文] 弧电振子锤 - 基础攻击速度",
            "     • 0.6 = 非常慢",
            "     • 0.75 = 较慢(默认)",
            "     • 0.9 = 中等",
            "     重型武器类别",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedArcaTitron")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedArcaTitron = 0.75;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Arca Titron - Movement Speed Modifier",
            "     • -0.2 = Large penalty",
            "     • -0.15 = Moderate penalty (default)",
            "     • -0.1 = Small penalty",
            "     Significant mobility reduction",
            "",
            "[中文] 弧电振子锤 - 移动速度修正",
            "     • -0.2 = 大幅惩罚",
            "     • -0.15 = 中等惩罚(默认)",
            "     • -0.1 = 较小惩罚",
            "     显著降低机动性",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedArcaTitron")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedArcaTitron = -0.15;

    // ========== Reaper Prime (收割者Prime) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Reaper Prime - Base Attack Damage",
            "     • 12 = Weak",
            "     • 18 = Normal (default)",
            "     • 26 = Strong",
            "     Fast scythe with good reach",
            "",
            "[中文] 收割者Prime - 基础攻击伤害",
            "     • 12 = 较弱",
            "     • 18 = 正常(默认)",
            "     • 26 = 强力",
            "     快速镰刀，攻击范围良好",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageReaperPrime")
    @Config.RangeDouble(min = 0)
    public static double attackDamageReaperPrime = 18;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Reaper Prime - Base Attack Speed",
            "     • 1.5 = Fast",
            "     • 1.75 = Very fast (default)",
            "     • 2.0 = Extremely fast",
            "     High attack speed weapon",
            "",
            "[中文] 收割者Prime - 基础攻击速度",
            "     • 1.5 = 较快",
            "     • 1.75 = 非常快(默认)",
            "     • 2.0 = 极快",
            "     高攻速武器",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedReaperPrime")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedReaperPrime = 1.75;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Reaper Prime - Movement Speed Modifier",
            "     • 0.025 = Minimal boost",
            "     • 0.05 = Small boost (default)",
            "     • 0.075 = Moderate boost",
            "     Mobile weapon with speed bonus",
            "",
            "[中文] 收割者Prime - 移动速度修正",
            "     • 0.025 = 轻微提升",
            "     • 0.05 = 较小提升(默认)",
            "     • 0.075 = 中等提升",
            "     机动性武器带速度加成",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedReaperPrime")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedReaperPrime = 0.05;

    // ========== Vitrica (金璃剑) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Vitrica - Base Attack Damage",
            "     • 36 = Weak",
            "     • 50 = Normal (default)",
            "     • 66 = Strong",
            "     Highest damage two-handed sword",
            "",
            "[中文] 金璃剑 - 基础攻击伤害",
            "     • 36 = 较弱",
            "     • 50 = 正常(默认)",
            "     • 66 = 强力",
            "     最高伤害的双手剑",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageVitrica")
    @Config.RangeDouble(min = 0)
    public static double attackDamageVitrica = 50;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Vitrica - Base Attack Speed",
            "     • 0.5 = Very slow",
            "     • 0.65 = Slow (default)",
            "     • 0.8 = Moderate",
            "     Slowest weapon class",
            "",
            "[中文] 金璃剑 - 基础攻击速度",
            "     • 0.5 = 非常慢",
            "     • 0.65 = 较慢(默认)",
            "     • 0.8 = 中等",
            "     最慢的武器类别",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedVitrica")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedVitrica = 0.65;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Vitrica - Movement Speed Modifier",
            "     • -0.25 = Very large penalty",
            "     • -0.2 = Large penalty (default)",
            "     • -0.15 = Moderate penalty",
            "     Heaviest weapon with maximum penalty",
            "",
            "[中文] 金璃剑 - 移动速度修正",
            "     • -0.25 = 非常大惩罚",
            "     • -0.2 = 大幅惩罚(默认)",
            "     • -0.15 = 中等惩罚",
            "     最重的武器，最大移速惩罚",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedVitrica")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedVitrica = -0.2;

    // ========== Gram Prime (格拉姆Prime) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Gram Prime - Base Attack Damage",
            "     • 32 = Weak",
            "     • 46 = Normal (default)",
            "     • 60 = Strong",
            "     Classic heavy greatsword",
            "",
            "[中文] 格拉姆Prime - 基础攻击伤害",
            "     • 32 = 较弱",
            "     • 46 = 正常(默认)",
            "     • 60 = 强力",
            "     经典重型大剑",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageGramPrime")
    @Config.RangeDouble(min = 0)
    public static double attackDamageGramPrime = 46;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Gram Prime - Base Attack Speed",
            "     • 0.6 = Very slow",
            "     • 0.7 = Slow (default)",
            "     • 0.85 = Moderate",
            "     Slow but powerful strikes",
            "",
            "[中文] 格拉姆Prime - 基础攻击速度",
            "     • 0.6 = 非常慢",
            "     • 0.7 = 较慢(默认)",
            "     • 0.85 = 中等",
            "     缓慢但强力的攻击",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedGramPrime")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedGramPrime = 0.7;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Gram Prime - Movement Speed Modifier",
            "     • -0.2 = Large penalty",
            "     • -0.15 = Moderate penalty (default)",
            "     • -0.1 = Small penalty",
            "     Heavy weapon mobility cost",
            "",
            "[中文] 格拉姆Prime - 移动速度修正",
            "     • -0.2 = 大幅惩罚",
            "     • -0.15 = 中等惩罚(默认)",
            "     • -0.1 = 较小惩罚",
            "     重型武器机动代价",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedGramPrime")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedGramPrime = -0.15;

    // ========== Sancti Magistar (圣洁执法者) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Sancti Magistar - Base Attack Damage",
            "     • 26 = Weak",
            "     • 36 = Normal (default)",
            "     • 48 = Strong",
            "     Holy warhammer with divine power",
            "",
            "[中文] 圣洁执法者 - 基础攻击伤害",
            "     • 26 = 较弱",
            "     • 36 = 正常(默认)",
            "     • 48 = 强力",
            "     神圣战锤，蕴含神力",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageSanctiMagistar")
    @Config.RangeDouble(min = 0)
    public static double attackDamageSanctiMagistar = 36;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Sancti Magistar - Base Attack Speed",
            "     • 0.7 = Slow",
            "     • 0.85 = Moderate (default)",
            "     • 1.0 = Normal",
            "     Balanced hammer speed",
            "",
            "[中文] 圣洁执法者 - 基础攻击速度",
            "     • 0.7 = 较慢",
            "     • 0.85 = 中等(默认)",
            "     • 1.0 = 正常",
            "     平衡的锤类速度",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedSanctiMagistar")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedSanctiMagistar = 0.85;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Sancti Magistar - Movement Speed Modifier",
            "     • -0.1 = Moderate penalty",
            "     • -0.075 = Small penalty (default)",
            "     • -0.05 = Minimal penalty",
            "     Light penalty for holy weapon",
            "",
            "[中文] 圣洁执法者 - 移动速度修正",
            "     • -0.1 = 中等惩罚",
            "     • -0.075 = 较小惩罚(默认)",
            "     • -0.05 = 轻微惩罚",
            "     神圣武器的轻微惩罚",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedSanctiMagistar")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedSanctiMagistar = -0.075;

    // ========== Destreza Prime (技巧之剑Prime) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Destreza Prime - Base Attack Damage",
            "     • 16 = Weak",
            "     • 24 = Normal (default)",
            "     • 34 = Strong",
            "     Precision rapier for skilled fighters",
            "",
            "[中文] 技巧之剑Prime - 基础攻击伤害",
            "     • 16 = 较弱",
            "     • 24 = 正常(默认)",
            "     • 34 = 强力",
            "     精准细剑，适合技术型战士",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageDestrezaPrime")
    @Config.RangeDouble(min = 0)
    public static double attackDamageDestrezaPrime = 24;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Destreza Prime - Base Attack Speed",
            "     • 1.2 = Fast",
            "     • 1.4 = Very fast (default)",
            "     • 1.6 = Extremely fast",
            "     Swift rapier attacks",
            "",
            "[中文] 技巧之剑Prime - 基础攻击速度",
            "     • 1.2 = 较快",
            "     • 1.4 = 非常快(默认)",
            "     • 1.6 = 极快",
            "     迅捷的刺剑攻击",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedDestrezaPrime")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedDestrezaPrime = 1.4;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Destreza Prime - Movement Speed Modifier",
            "     • 0.075 = Small boost",
            "     • 0.125 = Moderate boost (default)",
            "     • 0.175 = Large boost",
            "     Highest mobility weapon",
            "",
            "[中文] 技巧之剑Prime - 移动速度修正",
            "     • 0.075 = 较小提升",
            "     • 0.125 = 中等提升(默认)",
            "     • 0.175 = 大幅提升",
            "     机动性最高的武器",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedDestrezaPrime")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedDestrezaPrime = 0.125;

    // ========== Prisma Veritux (棱晶真理巨剑) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Prisma Veritux - Base Attack Damage",
            "     • 70 = Weak",
            "     • 100 = Normal (default)",
            "     • 140 = Strong",
            "     ⚠ MASSIVE DAMAGE ultra-heavy weapon!",
            "     Highest damage in the game",
            "",
            "[中文] 棱晶真理巨剑 - 基础攻击伤害",
            "     • 70 = 较弱",
            "     • 100 = 正常(默认)",
            "     • 140 = 强力",
            "     ⚠ 超重型武器，伤害巨大!",
            "     游戏中最高伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamagePrismaVeritux")
    @Config.RangeDouble(min = 0)
    public static double attackDamagePrismaVeritux = 100;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Prisma Veritux - Base Attack Speed",
            "     • 0.35 = Extremely slow",
            "     • 0.45 = Very slow (default)",
            "     • 0.55 = Slow",
            "     ⚠ Slowest weapon in the game!",
            "",
            "[中文] 棱晶真理巨剑 - 基础攻击速度",
            "     • 0.35 = 极慢",
            "     • 0.45 = 非常慢(默认)",
            "     • 0.55 = 较慢",
            "     ⚠ 游戏中最慢的武器!",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedPrismaVeritux")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedPrismaVeritux = 0.45;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Prisma Veritux - Movement Speed Modifier",
            "     • -0.8 = Extremely slow movement",
            "     • -0.6 = Very slow movement (default)",
            "     • -0.4 = Slow movement",
            "     ⚠ Massive mobility penalty!",
            "     Nearly immobile when wielded",
            "",
            "[中文] 棱晶真理巨剑 - 移动速度修正",
            "     • -0.8 = 移动极慢",
            "     • -0.6 = 移动非常慢(默认)",
            "     • -0.4 = 移动较慢",
            "     ⚠ 巨大的机动惩罚!",
            "     持握时几乎无法移动",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedPrismaVeritux")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedPrismaVeritux = -0.6;

    // ========== Machete (马谢特砍刀) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Machete - Base Attack Damage",
            "     • 24 = Weak",
            "     • 32 = Normal (default)",
            "     • 42 = Strong",
            "     Versatile machete with balanced stats",
            "",
            "[中文] 马谢特砍刀 - 基础攻击伤害",
            "     • 24 = 较弱",
            "     • 32 = 正常(默认)",
            "     • 42 = 强力",
            "     多用途砍刀，属性平衡",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageMachete")
    @Config.RangeDouble(min = 0)
    public static double attackDamageMachete = 32;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Machete - Base Attack Speed",
            "     • 1.3 = Fast",
            "     • 1.55 = Very fast (default)",
            "     • 1.8 = Extremely fast",
            "     Quick slashing attacks",
            "",
            "[中文] 马谢特砍刀 - 基础攻击速度",
            "     • 1.3 = 较快",
            "     • 1.55 = 非常快(默认)",
            "     • 1.8 = 极快",
            "     快速的砍击攻击",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedMachete")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedMachete = 1.55;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Machete - Movement Speed Modifier",
            "     • 0.0 = No change",
            "     • 0.025 = Slight boost (default)",
            "     • 0.05 = Moderate boost",
            "     Light weapon with mobility",
            "",
            "[中文] 马谢特砍刀 - 移动速度修正",
            "     • 0.0 = 无变化",
            "     • 0.025 = 轻微提升(默认)",
            "     • 0.05 = 中等提升",
            "     轻型武器，保持机动性",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedMachete")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedMachete = 0.025;

    // ========== Caustacyst (灼蚀变体镰) ==========

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Caustacyst - Base Attack Damage",
            "     • 20 = Weak",
            "     • 28 = Normal (default)",
            "     • 38 = Strong",
            "     Corrosive scythe with poison damage",
            "",
            "[中文] 灼蚀变体镰 - 基础攻击伤害",
            "     • 20 = 较弱",
            "     • 28 = 正常(默认)",
            "     • 38 = 强力",
            "     腐蚀镰刀，附带毒素伤害",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackDamageCaustacyst")
    @Config.RangeDouble(min = 0)
    public static double attackDamageCaustacyst = 28;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Caustacyst - Base Attack Speed",
            "     • 1.1 = Fast",
            "     • 1.3 = Very fast (default)",
            "     • 1.5 = Extremely fast",
            "     Swift scythe strikes",
            "",
            "[中文] 灼蚀变体镰 - 基础攻击速度",
            "     • 1.1 = 较快",
            "     • 1.3 = 非常快(默认)",
            "     • 1.5 = 极快",
            "     迅捷的镰刀攻击",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.attackSpeedCaustacyst")
    @Config.RangeDouble(min = 0)
    public static double attackSpeedCaustacyst = 1.3;

    @Config.Comment({
            "═══════════════════════════════════════════════════════════════",
            "[EN] Caustacyst - Movement Speed Modifier",
            "     • 0.075 = Small boost",
            "     • 0.125 = Moderate boost (default)",
            "     • 0.175 = Large boost",
            "     Excellent mobility with poison",
            "",
            "[中文] 灼蚀变体镰 - 移动速度修正",
            "     • 0.075 = 较小提升",
            "     • 0.125 = 中等提升(默认)",
            "     • 0.175 = 大幅提升",
            "     优秀的机动性与毒素结合",
            "═══════════════════════════════════════════════════════════════"
    })
    @Config.LangKey("config." + Reference.MOD_ID + ".kuvaweapon.movementSpeedCaustacyst")
    @Config.RangeDouble(min = 0)
    public static double movementSpeedCaustacyst = 0.125;
}