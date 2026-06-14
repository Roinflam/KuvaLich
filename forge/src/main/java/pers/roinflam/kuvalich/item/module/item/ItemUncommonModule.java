// 文件：ItemUncommonModule.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/item/module/item/ItemUncommonModule.java
package pers.roinflam.kuvalich.item.module.item;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

import pers.roinflam.kuvalich.base.item.AbstractItemModule;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;

/**
 * 白银级武器模组（1.20.1版本）
 * Uncommon (Sliver) tier weapon module (1.20.1 version)
 *
 * ⭐ 第三批新增 · 单词条银卡：
 *    通用：收割之刻（收集者阈值 2%）、涤魂（净化驱散 10%）、斩首（致命斩首 0.01%）
 *    TACZ：洞穿弹头（真实伤害 20%）、战利搜集（枪械战利品掉落 30%）
 *
 * ⭐ 第四批新增 · 轻量银卡搭配：
 *    通用：弱化打击（净化10%+近战60%）、猎魂者（阈值2%+远程60%）
 *    TACZ：实弹穿透（真伤10%+枪械伤害60%）、拾荒弹链（枪械战利品20%+弹夹30%）
 *
 * ⭐ 第五批新增 · 轻量银卡搭配（新词条 × 老词条）：
 *    通用：腐蚀猎手（阈值2%+腐蚀60%）、涤魂触击（抹除10%+远程60%）、
 *          毒刃斩首（秒杀0.01%+毒60%）、净化箭（抹除10%+箭矢90%）
 *    TACZ：真伤弹芯（真伤10%+爆头60%）、掠夺者之眼（枪械战利品20%+精准30%）
 *
 * ⭐ 第六批新增 · 冷门组合（银卡）：
 *    通用：基础元素×冷门载体（烈焰挥砍/冰封打击/剧毒穿刺/雷暴连射/炽炎之触/寒霜领域）、
 *          impact 控场进阶（碎骨重锤/震荡刃/霰弹冲击）、magicDamage×元素与散件、
 *          bane×暴击/×元素/×远程、基伤×元素、triggerTime 专精、dash×元素、横扫近战等。
 *    TACZ：枪伤×元素穿甲、真伤×精准、战利品×枪手、速瞄×元素、精准弹幕等冷门搭配。
 */
public class ItemUncommonModule extends AbstractItemModule {

    public static List<ItemStack> itemStackList = new ArrayList<>();
    private static boolean isInitialized = false;

    public ItemUncommonModule(Properties properties) {
        super(properties);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_UNCOMMON_MODULE.get());
        itemStack.setHoverName(net.minecraft.network.chat.Component.translatable("kuvaweapon.item_type_random.name"));
        AbstractModule.setRandom(itemStack, true);
        return itemStack;
    }

    private static synchronized void ensureInitialized() {
        if (!isInitialized) {
            initializeModuleList();
            isInitialized = true;
        }
    }

    private static void initializeModuleList() {
        itemStackList.clear();

        // 切割
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.serration", "serration",
                new Object[]{"remoteDamage", 1.65001f});

        // 速度触发
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.speed_trigger", "speed_trigger",
                new Object[]{"firing_rate", 0.6001f});

        // 熔化冲击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.molten_impact", "molten_impact",
                new Object[]{"fire", 0.9001f});

        // 电击之触
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.shocking_touch", "shocking_touch",
                new Object[]{"electricity", 0.9001f});

        // 狂热打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fever_strike", "fever_strike",
                new Object[]{"poison", 0.9001f});

        // 北风
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.north_wind", "north_wind",
                new Object[]{"ice", 0.9001f});

        // 未定义生物克星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bane_of_undefined", "bane_of_undefined",
                new Object[]{"bane_of_undefined", 0.3001f});

        // 不死生物克星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bane_of_undead", "bane_of_undead",
                new Object[]{"bane_of_undead", 0.3001f});

        // 节肢生物克星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bane_of_arthropod", "bane_of_arthropod",
                new Object[]{"bane_of_arthropod", 0.3001f});

        // 灾厄村民克星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bane_of_illager", "bane_of_illager",
                new Object[]{"bane_of_illager", 0.3001f});

        // 武器资质
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.weapon_aptitude", "weapon_aptitude",
                new Object[]{"triggerChance", 0.9001f});

        // 狂怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fury", "fury",
                new Object[]{"attackSpeed", 0.3001f});

        // 回旋砍
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.roundhouse_chop", "roundhouse_chop",
                new Object[]{"attackRange", 1.20001f, "slash", 0.60001f});

        // 魔法剑士
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magic_swordsman", "magic_swordsman",
                new Object[]{"meleeDamage", 1.20001f, "magicDamage", 1.20001f});

        // 三重打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.triple_whammy", "triple_whammy",
                new Object[]{"slash", 0.30001f, "puncture", 0.30001f, "impact", 0.30001f});

        // 小口径
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.small_caliber", "small_caliber",
                new Object[]{"remoteCriticalStrikeProbability", 1.20001f, "remoteCriticalStrikeMultiplier", 1.20001f, "remoteDamage", -0.80001f});

        // 流体
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.stream", "stream",
                new Object[]{"dashAttackRange", 5.00001f});

        // 精准射击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.sure_shot", "sure_shot",
                new Object[]{"remoteDamage", 1.20001f, "triggerChance", 0.60001f});

        // 火焰之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arrow_fire", "arrow_fire",
                new Object[]{"arrowDamage", 0.90001f, "fire", 0.90001f});

        // 冰霜之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arrow_ice", "arrow_ice",
                new Object[]{"arrowDamage", 0.90001f, "ice", 0.90001f});

        // 毒素之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arrow_poison", "arrow_poison",
                new Object[]{"arrowDamage", 0.90001f, "poison", 0.90001f});

        // 电击之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arrow_electricity", "arrow_electricity",
                new Object[]{"arrowDamage", 0.90001f, "electricity", 0.90001f});

        // 低温弹药
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.cryo_rounds", "cryo_rounds",
                new Object[]{"projectileDamage", 1.20001f, "ice", 0.60001f});

        // 弹簧弹舱
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.spring_loaded_chamber", "spring_loaded_chamber",
                new Object[]{"projectileDamage", 1.55001f, "firing_rate", 0.75001f, "remoteCriticalStrikeProbability", -0.6001f});

        // 锯齿弹药
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.serrated_rounds", "serrated_rounds",
                new Object[]{"projectileDamage", 1.20001f, "slash", 0.9001f, "triggerChance", 0.6001f});

        // 毁灭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.devastated", "devastated",
                new Object[]{"attackRange", 1.20001f, "puncture", 0.6001f, "triggerTime", 0.6001f});

        // 裂变射击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fission_shot", "fission_shot",
                new Object[]{"multishot", 1.20001f, "remoteDamage", -0.6001f});

        // 掠食本能
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.predatory_instinct", "predatory_instinct",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashAttackRange", 3.00001f},
                "dash_crit_chance", "dash_range");

        // 猎杀时刻
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hunters_moment", "hunters_moment",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashTriggerChance", 1.35001f},
                "dash_crit_chance", "dash_trigger");

        // 非暴力美学
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.violent_aesthetic", "violent_aesthetic",
                new Object[]{"baseDamageWhenNotCriticalStrike", 1.65001f, "attackSpeed", 0.20001f});

        // 感染协议
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.infection_protocol", "infection_protocol",
                new Object[]{"triggerChance", 0.90001f, "triggerTime", 0.75001f});

        // 双重契约
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.dual_covenant", "dual_covenant",
                new Object[]{"meleeDamage", 1.20001f, "remoteDamage", 1.20001f});

        // 秘法弓术
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.mystic_archery", "mystic_archery",
                new Object[]{"arrowDamage", 1.35001f, "magicDamage", 1.05001f});

        // 奥能轨迹
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_ballistics", "arcane_ballistics",
                new Object[]{"projectileDamage", 1.35001f, "magicDamage", 1.05001f});

        // 狂战士之怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.berserker_rage", "berserker_rage",
                new Object[]{"meleeDamage", -0.15001f, "attackSpeed", 0.90001f});

        // 火力压制
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.suppressive_fire", "suppressive_fire",
                new Object[]{"remoteDamage", 1.35001f, "firing_rate", 0.30001f});

        // 震荡领域
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.shockwave_domain", "shockwave_domain",
                new Object[]{"bursting_radius", 0.15001f, "triggerTime", 0.90001f});

        // 死亡弹幕
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.death_barrage", "death_barrage",
                new Object[]{"multishot", 0.60001f, "triggerChance", 0.90001f});

        // ========== 新增通用词条 · 单词条银卡 ==========

        // 收割之刻 —— 收集者阈值 2%（目标生命低于上限2%直接处决，无上限，多卡叠加）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reapers_threshold", "execute_threshold",
                new Object[]{"execute_threshold", 0.02001f});

        // 涤魂 —— 净化驱散 10%（攻击命中概率移除目标增益）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.soul_cleanse", "purge_buff",
                new Object[]{"purge_buff", 0.10001f});

        // 斩首 —— 致命斩首 0.01%（攻击命中极低概率直接处决，可被多重射击放大）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.decapitate", "execute_chance",
                new Object[]{"execute_chance", 0.0001f});

        // ========== 第四批新增通用词条 · 轻量银卡搭配 ==========

        // 弱化打击 —— 净化驱散10% + 近战伤害60%（近战削增益入门卡）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.enfeebling_strike", "enfeebling_strike",
                new Object[]{"purge_buff", 0.10001f, "meleeDamage", 0.60001f});

        // 猎魂者 —— 收集者阈值2% + 远程伤害60%（远程处决入门卡）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.soul_hunter", "soul_hunter",
                new Object[]{"execute_threshold", 0.02001f, "remoteDamage", 0.60001f});

        // ========== 第五批新增通用词条 · 轻量银卡搭配（新词条 × 老词条）==========

        // 腐蚀猎手 —— 处决阈值2% + 腐蚀60%（破甲处决入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosive_hunter", "corrosive_hunter",
                new Object[]{"execute_threshold", 0.02001f, "corrosion", 0.60001f});

        // 涤魂触击 —— 抹除增益10% + 远程伤害60%（远程抹增益入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.cleansing_touch", "cleansing_touch",
                new Object[]{"purge_buff", 0.10001f, "remoteDamage", 0.60001f});

        // 毒刃斩首 —— 秒杀概率0.01% + 毒素60%（毒素 + 秒杀入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.venomous_decapitation", "venomous_decapitation",
                new Object[]{"execute_chance", 0.0001f, "poison", 0.60001f});

        // 净化箭 —— 抹除增益10% + 箭矢伤害90%（弓抹增益入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.purifying_arrow", "purifying_arrow",
                new Object[]{"purge_buff", 0.10001f, "arrowDamage", 0.90001f});

        // ========== ⭐ 第六批新增 · 通用冷门组合（银卡）==========
        // 基础元素值统一 0.9（与现有银卡单元素一致）；不引入处决/真伤等进阶机制以外的超模数值。

        // ----- 基础元素 × 冷门载体 -----

        // 烈焰挥砍 —— 火焰90% + 切割60%（火×切割，区别于纯近战）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.flame_slash", "flame_slash",
                new Object[]{"fire", 0.90001f, "slash", 0.60001f});

        // 冰封打击 —— 冰冻90% + 冲击60%（减速+击退控场）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.frost_strike", "frost_strike",
                new Object[]{"ice", 0.90001f, "impact", 0.60001f});

        // 剧毒穿刺 —— 毒素90% + 穿刺60%（破甲毒DOT）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.venom_pierce", "venom_pierce",
                new Object[]{"poison", 0.90001f, "puncture", 0.60001f});

        // 雷暴侵蚀 —— 电击90% + 触发几率60%（高频触发感电，避免与金卡雷霆弹幕倒挂）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.storm_volley", "storm_volley",
                new Object[]{"electricity", 0.90001f, "triggerChance", 0.60001f});

        // 炽炎之触 —— 火焰90% + 攻击速度30%（高频纵火）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.searing_touch", "searing_touch",
                new Object[]{"fire", 0.90001f, "attackSpeed", 0.30001f});

        // 寒霜领域 —— 冰冻90% + 攻击距离90%（横扫冰冻控场）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.frost_field", "frost_field",
                new Object[]{"ice", 0.90001f, "attackRange", 0.90001f});

        // ----- impact 控场进阶 -----

        // 碎骨重锤 —— 冲击60% + 近战伤害90%（近战击退主力）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bone_crusher", "bone_crusher",
                new Object[]{"impact", 0.60001f, "meleeDamage", 0.90001f});

        // 震荡刃 —— 冲击60% + 攻击距离90%（横扫击退）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.shockwave_blade", "shockwave_blade",
                new Object[]{"impact", 0.60001f, "attackRange", 0.90001f});

        // 霰弹冲击 —— 冲击45% + 多重射击60%（远程多发击退）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.scatter_blast", "scatter_blast",
                new Object[]{"impact", 0.45001f, "multishot", 0.60001f});

        // ----- magicDamage × 元素 / 散件 -----

        // 元素咒刃 —— 魔法伤害105% + 火焰60%（法术纵火）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.elemental_hex_blade", "elemental_hex_blade",
                new Object[]{"magicDamage", 1.05001f, "fire", 0.60001f});

        // 奥术风暴 —— 魔法伤害135% + 触发几率60% + 触发时间60%（法术异常爆发）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_storm", "arcane_storm",
                new Object[]{"magicDamage", 1.35001f, "triggerChance", 0.60001f, "triggerTime", 0.60001f});

        // 咒能箭雨 —— 魔法伤害105% + 箭矢伤害90% + 多重射击30%（法术弓流）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hex_arrow_rain", "hex_arrow_rain",
                new Object[]{"magicDamage", 1.05001f, "arrowDamage", 0.90001f, "multishot", 0.30001f});

        // ----- bane × 暴击 / 元素 / 远程 -----

        // 猎魔暴击 —— 不死克星30% + 近战暴击几率90%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.undead_crit", "undead_crit",
                new Object[]{"bane_of_undead", 0.30001f, "meleeCriticalStrikeProbability", 0.90001f},
                "melee_crit_chance");

        // 灭虫毒刃 —— 节肢克星30% + 毒素90%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arthropod_venom", "arthropod_venom",
                new Object[]{"bane_of_arthropod", 0.30001f, "poison", 0.90001f});

        // 讨逆箭 —— 灾厄村民克星30% + 箭矢伤害90%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.illager_arrow", "illager_arrow",
                new Object[]{"bane_of_illager", 0.30001f, "arrowDamage", 0.90001f});

        // 异界克星 —— 未定义克星30% + 远程伤害90%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.aberration_bane", "aberration_bane",
                new Object[]{"bane_of_undefined", 0.30001f, "remoteDamage", 0.90001f});

        // ----- 基伤 × 元素 / triggerTime 专精 / dash -----

        // 重拳 —— 非暴击基伤165% + 冲击60%（稳定击退）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.heavy_fist", "heavy_fist",
                new Object[]{"baseDamageWhenNotCriticalStrike", 1.65001f, "impact", 0.60001f});

        // 毒躯 —— 非暴击基伤165% + 毒素60%（无暴击毒流）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.toxic_husk", "toxic_husk",
                new Object[]{"baseDamageWhenNotCriticalStrike", 1.65001f, "poison", 0.60001f});

        // 持久毒云 —— 毒素90% + 触发时间90%（延长毒DOT专精）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lasting_toxin", "lasting_toxin",
                new Object[]{"poison", 0.90001f, "triggerTime", 0.90001f});

        // 永冻 —— 冰冻90% + 触发时间100% + 触发几率30%（长效减速）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.perma_frost", "perma_frost",
                new Object[]{"ice", 0.90001f, "triggerTime", 1.00001f, "triggerChance", 0.30001f});

        // 疾风毒刃 —— 冲刺触发几率135% + 毒素60%（冲刺触毒）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.gale_venom", "gale_venom",
                new Object[]{"dashTriggerChance", 1.35001f, "poison", 0.60001f});

        // 突袭重斩 —— 冲刺攻击距离 + 近战伤害60%（冲刺横扫强化）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lunge_heavy", "lunge_heavy",
                new Object[]{"dashAttackRange", 5.00001f, "meleeDamage", 0.60001f});

        // ----- 暴击 / 横扫 / 远程 -----

        // 狂乱挥砍 —— 攻击速度45% + 切割60%（高频切割，攻速压至 Prime 狂怒之下避免倒挂）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.frenzy_slash", "frenzy_slash",
                new Object[]{"attackSpeed", 0.45001f, "slash", 0.60001f});

        // 破绽横扫 —— 攻击距离120% + 近战暴击几率60%（大范围+暴击，避免与金卡居合能量同值）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.mighty_sweep", "mighty_sweep",
                new Object[]{"attackRange", 1.20001f, "meleeCriticalStrikeProbability", 0.60001f},
                "melee_crit_chance");

        // 致命精准 —— 近战暴击几率120% + 近战暴击伤害60%（双暴击）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lethal_precision_melee", "lethal_precision_melee",
                new Object[]{"meleeCriticalStrikeProbability", 1.20001f, "meleeCriticalStrikeMultiplier", 0.60001f},
                "melee_crit_chance", "melee_crit_mult");

        // 弹道精算 —— 远程暴击几率120% + 远程伤害60%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.ballistic_calc", "ballistic_calc",
                new Object[]{"remoteCriticalStrikeProbability", 1.20001f, "remoteDamage", 0.60001f},
                "remote_crit_chance");

        // 爆发箭 —— 箭矢伤害150% + 射速30%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.burst_arrow", "burst_arrow",
                new Object[]{"arrowDamage", 1.50001f, "firing_rate", 0.30001f});

        // 弹幕压制 —— 远程伤害135% + 多重射击60% - 射速30%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.barrage_suppress", "barrage_suppress",
                new Object[]{"remoteDamage", 1.35001f, "multishot", 0.60001f, "firing_rate", -0.30001f});

        // ========== TACZ 枪械专属模组（需要 TACZ 才会注册）==========
        if (ItemRivenModule.isTaczLoaded()) {

            // 极限速度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.terminal_velocity", "terminal_velocity",
                    new Object[]{"projectile_speed", 0.60001f});

            // 致命弹道
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lethal_trajectory", "lethal_trajectory",
                    new Object[]{"projectile_speed", 0.45001f, "remoteCriticalStrikeProbability", 0.60001f});

            // 弹幕倾泻
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.barrage_protocol", "barrage_protocol",
                    new Object[]{"firing_rate", 0.30001f, "multishot", 0.30001f, "remoteDamage", -0.30001f});

            // 战术装填
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.tactical_reload", "tactical_reload",
                    new Object[]{"reload_speed", 0.40001f, "recoil_reduction", 0.40001f});

            // 镇定射击
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.steady_aim", "steady_aim",
                    new Object[]{"recoil_reduction", 0.45001f, "triggerChance", 0.60001f});

            // 弹道校准
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ballistic_calibration", "ballistic_calibration",
                    new Object[]{"projectile_speed", 0.45001f, "remoteCriticalStrikeMultiplier", 0.60001f});

            // 超量供弹
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.surplus_ammo", "surplus_ammo",
                    new Object[]{"magazine_size", 0.60001f, "reload_speed", -0.20001f});

            // ===== 第二批TACZ新属性白银卡 =====

            // 弹道聚焦 —— 45%精准度 + 120%枪械伤害
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ballistic_focus", "ballistic_focus",
                    new Object[]{"accuracy", 0.45001f, "gun_damage", 1.20001f});

            // 猎首者 —— 90%爆头伤害 + 60%远程暴击几率
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.headhunter", "headhunter",
                    new Object[]{"headshot_damage", 0.90001f, "remoteCriticalStrikeProbability", 0.60001f});

            // 极速瞄具 —— 45%瞄准速度 + 30%后坐力降低
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.quickdraw_sight", "quickdraw_sight",
                    new Object[]{"aim_time", 0.45001f, "recoil_reduction", 0.30001f});

            // 穿甲弹芯 —— 200%枪械伤害 - 20%精准度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.armor_piercing_core", "armor_piercing_core",
                    new Object[]{"gun_damage", 2.00001f, "accuracy", -0.20001f});

            // 战术优势 —— 30%瞄准速度 + 30%精准度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.tactical_edge", "tactical_edge",
                    new Object[]{"aim_time", 0.30001f, "accuracy", 0.30001f});

            // 火力集中 —— 150%枪械伤害 + 60%爆头伤害
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.focused_firepower", "focused_firepower",
                    new Object[]{"gun_damage", 1.50001f, "headshot_damage", 0.60001f});

            // 弹道学 —— 247%枪械伤害（弹道学Prime普通版）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ballistics", "ballistics",
                    new Object[]{"gun_damage", 2.47001f});

            // 致命精准 —— 120%枪械伤害 + 60%远程暴击几率（致命精准Prime普通版）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lethal_accuracy", "lethal_accuracy",
                    new Object[]{"gun_damage", 1.20001f, "remoteCriticalStrikeProbability", 0.60001f});

            // 弱点锁定 —— 90%爆头倍率 + 45%精准度（弱点锁定Prime普通版）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.weak_point_lock", "weak_point_lock",
                    new Object[]{"headshot_damage", 0.90001f, "accuracy", 0.45001f});

            // 爆头专家 —— 120%爆头倍率 + 30%瞄准速度（爆头专家Prime普通版）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.headshot_expert", "headshot_expert",
                    new Object[]{"headshot_damage", 1.20001f, "aim_time", 0.30001f});

            // 快速反应 —— 45%瞄准速度 + 120%枪械伤害（快速反应Prime普通版）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.quick_response", "quick_response",
                    new Object[]{"aim_time", 0.45001f, "gun_damage", 1.20001f});

            // ===== 新增 TACZ 专属词条 · 单词条银卡 =====

            // 洞穿弹头 —— 真实伤害 20%（额外真伤，无视护甲）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.penetrator_round", "true_bullet",
                    new Object[]{"true_bullet", 0.20001f});

            // 战利搜集 —— 枪械战利品掉落 30%（仅 TACZ 子弹击杀生效）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.spoils_collector", "gun_loot_drop",
                    new Object[]{"gun_loot_drop", 0.30001f});

            // ===== 第四批新增 TACZ 专属词条 · 轻量银卡搭配 =====

            // 实弹穿透 —— 真实伤害10% + 枪械伤害60%（真伤入门卡，枪伤抬基数）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.solid_penetrator", "solid_penetrator",
                    new Object[]{"true_bullet", 0.10001f, "gun_damage", 0.60001f});

            // 拾荒弹链 —— 枪械战利品掉落20% + 弹夹容量30%（刷材料续航入门卡）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.scavenger_belt", "scavenger_belt",
                    new Object[]{"gun_loot_drop", 0.20001f, "magazine_size", 0.30001f});

            // ===== 第五批新增 TACZ 专属 · 轻量银卡搭配 =====

            // 真伤弹芯 —— 真实子弹10% + 爆头伤害60%（爆头真伤入门）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.true_core", "true_core",
                    new Object[]{"true_bullet", 0.10001f, "headshot_damage", 0.60001f});

            // 掠夺者之眼 —— 枪械战利品掉落20% + 精准度30%（精准刷材料入门）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.plunderers_eye", "plunderers_eye",
                    new Object[]{"gun_loot_drop", 0.20001f, "accuracy", 0.30001f});

            // ===== ⭐ 第六批新增 · TACZ 冷门组合（银卡）=====
            // gun_damage 独立乘区，配元素(0.6)不超模；真伤/战利品配差异化老词条避免与现有重复。

            // 烈焰穿甲 —— 枪械伤害150% + 火焰60% + 穿刺30%（破甲纵火弹）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.flame_ap", "flame_ap",
                    new Object[]{"gun_damage", 1.50001f, "fire", 0.60001f, "puncture", 0.30001f});

            // 毒液狙击 —— 爆头伤害90% + 毒素60% + 精准度30%（精准毒狙）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.venom_snipe", "venom_snipe",
                    new Object[]{"headshot_damage", 0.90001f, "poison", 0.60001f, "accuracy", 0.30001f});

            // 电磁狙杀 —— 爆头伤害90% + 电击60%（爆头触电）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.em_snipe", "em_snipe",
                    new Object[]{"headshot_damage", 0.90001f, "electricity", 0.60001f});

            // 真伤精准 —— 真实子弹10% + 精准度45%（精准真伤，区别于真伤弹芯的爆头向）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.true_precision", "true_precision",
                    new Object[]{"true_bullet", 0.10001f, "accuracy", 0.45001f});

            // 炽焰枪击 —— 枪械伤害150% + 火焰60%（火焰枪 DPS，去掉弹夹避免压制金卡野火）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.flame_belt", "flame_belt",
                    new Object[]{"gun_damage", 1.50001f, "fire", 0.60001f});

            // 重装狙击 —— 枪械伤害180% + 精准度30%（纯枪伤狙击强化）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.heavy_marksman", "heavy_marksman",
                    new Object[]{"gun_damage", 1.80001f, "accuracy", 0.30001f});

            // 战利枪手 —— 枪械战利品掉落30% + 枪械伤害60%（边刷材料边输出）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.loot_marksman", "loot_marksman",
                    new Object[]{"gun_loot_drop", 0.30001f, "gun_damage", 0.60001f});

            // 速瞄燃烧 —— 瞄准速度45% + 火焰60%（速瞄纵火）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.quick_burn", "quick_burn",
                    new Object[]{"aim_time", 0.45001f, "fire", 0.60001f});

            // 精准弹幕 —— 精准度45% + 多重射击60%（精准多发）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.precise_barrage", "precise_barrage",
                    new Object[]{"accuracy", 0.45001f, "multishot", 0.60001f});

            // 稳固连射 —— 后坐力降低45% + 射速30%（稳定压枪连射，后坐压至金卡稳定器之下避免倒挂）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.steady_volley", "steady_volley",
                    new Object[]{"recoil_reduction", 0.45001f, "firing_rate", 0.30001f});

            // 弹链续航 —— 弹夹容量40% + 装填速度20%（大弹夹快装填，弹夹压至 Prime 弹夹增幅之下避免倒挂）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ammo_endurance", "ammo_endurance",
                    new Object[]{"magazine_size", 0.40001f, "reload_speed", 0.20001f});

            // 穿甲弹幕 —— 枪械伤害150% + 穿刺45% + 多重射击30%（多发破甲）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ap_barrage", "ap_barrage",
                    new Object[]{"gun_damage", 1.50001f, "puncture", 0.45001f, "multishot", 0.30001f});
        }
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, Rarity.UNCOMMON);

        // ⭐ 创造模式模组默认满级
        if (ModuleLevelHelper.isLevelSystemEnabled()) {
            for (ItemStack stack : items) {
                if (!AbstractModule.isRandom(stack)) {
                    ModuleLevelHelper.setModuleLevel(stack, ModuleLevelHelper.getMaxLevel());
                }
            }
        }

        items.forEach(output::accept);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide() && AbstractItemModule.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            ensureInitialized();
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);
            CustomModuleManager.getInstance().addCustomItemModulesToRandomList(availableModules, Rarity.UNCOMMON);

            if (availableModules.isEmpty()) {
                return InteractionResultHolder.fail(itemstack);
            }

            ItemStack module = availableModules.get(RandomUtil.getInt(0, availableModules.size() - 1)).copy();

// ⭐ 模组等级系统：赋予揭示等级
            ModuleLevelHelper.applyRevealLevel(module, player);
            ItemEntity entityItem = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), module);
            level.addFreshEntity(entityItem);
            player.setItemInHand(hand, ItemStack.EMPTY);
            return InteractionResultHolder.success(itemstack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return Rarity.UNCOMMON;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
