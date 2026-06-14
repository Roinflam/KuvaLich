// 文件：ItemCommonModule.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/item/module/item/ItemCommonModule.java
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
 * 青铜级武器模组（1.20.1版本）
 * Common (Bronze) tier weapon module (1.20.1 version)
 *
 * ⭐ 第六批新增 · 通用冷门组合（铜卡）：
 *    专门补现有体系的冷门/空白搭配，不与已有卡重复、不引入进阶机制（处决/真伤等留给金&Prime）。
 *    覆盖方向：
 *      - impact 控场（碎骨打击 / 震退之握 / 霰射冲击 / 双刃 / 钝击）
 *      - puncture 物理复合（破甲尖刺 / 贯穿箭 / 穿云箭 / 魔能穿刺）
 *      - triggerTime 的 DOT 延长专精（蔓延剧毒 / 余烬延烧 / 持久电弧 / 寒缚）
 *      - bane 克星组合（异类克星刃 / 灭尸之刃 / 除虫打击 / 讨伐之弓）
 *      - 暴击双词条（致命连击 / 精准连射 / 重击核心 / 弱点打击）
 *      - magicDamage 散件（法刃 / 咒术触发 / 魔能穿刺）
 *      - dash 入门（突进打击 / 疾刺）
 *      - 横扫沾染（横扫 / 巨刃）等
 *    TACZ 段新增「枪伤×元素 / 爆头×元素 / 精准×触发 / 续航×火力」等全空冷门搭配。
 */
public class ItemCommonModule extends AbstractItemModule {

    /**
     * 静态模组列表,用于随机获取
     */
    public static List<ItemStack> itemStackList = new ArrayList<>();

    /**
     * 标记是否已初始化（用于服务器端懒加载）
     */
    private static boolean isInitialized = false;

    public ItemCommonModule(Properties properties) {
        super(properties);
    }

    /**
     * 获取随机模组物品
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_COMMON_MODULE.get());
        itemStack.setHoverName(net.minecraft.network.chat.Component.translatable("kuvaweapon.item_type_random.name"));
        AbstractModule.setRandom(itemStack, true);
        return itemStack;
    }

    /**
     * 懒加载初始化方法（用于服务器端）
     */
    private static synchronized void ensureInitialized() {
        if (!isInitialized) {
            initializeModuleList();
            isInitialized = true;
        }
    }

    /**
     * 初始化模组列表
     */
    private static void initializeModuleList() {
        itemStackList.clear();

        // 压力点
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.pressure_point", "pressure_point",
                new Object[]{"meleeDamage", 1.2001f});

        // 攻击范围
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reach", "reach",
                new Object[]{"attackRange", 1.1001f});

        // 真钢
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.true_steel", "true_steel",
                new Object[]{"meleeCriticalStrikeProbability", 1.2001f},
                "melee_crit_chance");

        // 器官粉碎
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.organ_shatter", "organ_shatter",
                new Object[]{"meleeCriticalStrikeMultiplier", 0.9001f});

        // 精准打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.point_strike", "point_strike",
                new Object[]{"remoteCriticalStrikeProbability", 0.9001f});

        // 持续痛苦
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.continuous_misery", "continuous_misery",
                new Object[]{"triggerTime", 1.0f});

        // 锯齿弹夹
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.sawtooth_clip", "sawtooth_clip",
                new Object[]{"slash", 0.30001f, "remoteDamage", 0.30001f});

        // 氩晶体射击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.argon_crystal_shoot", "argon_crystal_shoot",
                new Object[]{"projectileDamage", 0.60001f, "remoteCriticalStrikeProbability", 0.45001f});

        // 氩晶体愤怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.argon_crystal_wrath", "argon_crystal_wrath",
                new Object[]{"projectileDamage", 0.60001f, "remoteCriticalStrikeMultiplier", 0.45001f});

        // 元素氩晶体愤怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.elemental_argon_crystal_wrath", "elemental_argon_crystal_wrath",
                new Object[]{"meleeDamage", 0.9001f, "triggerChance", 0.9001f, "triggerTime", 0.9001f});

        // 地狱电锯
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.chainsaw_from_hell", "chainsaw_from_hell",
                new Object[]{"meleeCriticalStrikeProbability", 0.6001f, "meleeCriticalStrikeMultiplier", 0.6001f, "attackSpeed", -0.3001f});

        // 泪痕
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.tear", "tear",
                new Object[]{"meleeCriticalStrikeProbability", 1.65001f, "meleeDamage", -0.60001f});

        // 灾难射击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.disaster_shoot", "disaster_shoot",
                new Object[]{"remoteDamage", 1.2001f, "triggerChance", 0.45001f});

        // 恶意
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.malicious", "malicious",
                new Object[]{"triggerChance", 1.2001f, "meleeCriticalStrikeProbability", -0.6001f, "remoteCriticalStrikeProbability", -0.6001f});

        // 单点突破
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.single_point_breakthrough", "single_point_breakthrough",
                new Object[]{"meleeDamage", 1.6501f, "attackRange", -0.7501f});

        // 病灶打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lesion_blow", "lesion_blow",
                new Object[]{"dashTriggerChance", 1.50001f});

        // 劝说
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.persuasion", "persuasion",
                new Object[]{"baseDamageWhenNotCriticalStrike", 1.20001f});

        // 延伸之触
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.touches_of_extension", "touches_of_extension",
                new Object[]{"meleeCriticalStrikeProbability", 0.90001f, "meleeCriticalStrikeMultiplier", 0.60001f, "triggerChance", 0.60001f, "slash", -1.20001f});

        // 凶猛之弓
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fierce_bow", "fierce_bow",
                new Object[]{"arrowDamage", 1.20001f, "multishot", 0.60001f});

        // 幻影之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.phantom_arrow", "phantom_arrow",
                new Object[]{"arrowDamage", 0.60001f, "multishot", 0.30001f, "firing_rate", 0.30001f});

        // 魔法增长
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magical_growth", "magical_growth",
                new Object[]{"magicDamage", 0.90001f, "multishot", 0.60001f});

        // 魔法飞弹
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magic_missile", "magic_missile",
                new Object[]{"remoteDamage", 0.60001f, "magicDamage", 0.60001f});

        // 潘多拉之星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.pandora_star", "pandora_star",
                new Object[]{"triggerTime", 3.00001f, "triggerChance", -1.50001f});

        // 奥术潜能
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_potential", "arcane_potential",
                new Object[]{"magicDamage", 1.25001f});

        // 神射天赋
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.marksmans_gift", "marksmans_gift",
                new Object[]{"arrowDamage", 1.25001f});

        // 弹道专家
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.ballistics_expert", "ballistics_expert",
                new Object[]{"projectileDamage", 1.25001f});

        // ========== ⭐ 第六批新增 · 通用冷门组合（铜卡）==========
        // 填补 impact 控场、puncture 物理、triggerTime DOT 延长、bane 克星组合、
        // 暴击双词条、magicDamage 散件、dash 入门、横扫沾染等现有体系的冷门空白。
        // 不引入处决/真伤/净化等进阶机制（留给金&Prime），数值保持铜卡入门档。

        // ----- impact 控场 / puncture 物理 -----

        // 碎骨打击 —— 冲击45% + 近战伤害60%（近战击退入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.heavy_impact", "heavy_impact",
                new Object[]{"impact", 0.45001f, "meleeDamage", 0.60001f});

        // 震退之握 —— 冲击45% + 攻击距离60%（远距离击退控场）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.knockback_grip", "knockback_grip",
                new Object[]{"impact", 0.45001f, "attackRange", 0.60001f});

        // 霰射冲击 —— 冲击30% + 多重射击45%（远程多发击退）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.scatter_impact", "scatter_impact",
                new Object[]{"impact", 0.30001f, "multishot", 0.45001f});

        // 双刃 —— 切割45% + 冲击45%（双物理：撕裂+击退）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.twin_edge", "twin_edge",
                new Object[]{"slash", 0.45001f, "impact", 0.45001f});

        // 钝击 —— 非暴击基伤120% + 冲击30%（稳定输出+击退）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.rock_blow", "rock_blow",
                new Object[]{"baseDamageWhenNotCriticalStrike", 1.20001f, "impact", 0.30001f});

        // 破甲尖刺 —— 穿刺60% + 近战伤害30%（近战破甲入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.armor_pierce", "armor_pierce",
                new Object[]{"puncture", 0.60001f, "meleeDamage", 0.30001f});

        // 贯穿箭 —— 穿刺45% + 箭矢伤害60%（弓系破甲入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.piercing_arrow", "piercing_arrow",
                new Object[]{"puncture", 0.45001f, "arrowDamage", 0.60001f});

        // 散射箭 —— 箭矢伤害60% + 多重射击45%（弓多发散射，区别于贯穿箭）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.cloud_piercer", "cloud_piercer",
                new Object[]{"arrowDamage", 0.60001f, "multishot", 0.45001f});

        // 裂伤之刃 —— 切割45% + 攻击速度30%（高频切割入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.rending_blade", "rending_blade",
                new Object[]{"slash", 0.45001f, "attackSpeed", 0.30001f});

        // ----- triggerTime 的 DOT 延长专精 -----

        // 蔓延剧毒 —— 毒素45% + 触发时间90%（延长毒DOT）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.spreading_venom", "spreading_venom",
                new Object[]{"poison", 0.45001f, "triggerTime", 0.90001f});

        // 余烬延烧 —— 火焰45% + 触发时间90%（延长火DOT）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lingering_burn", "lingering_burn",
                new Object[]{"fire", 0.45001f, "triggerTime", 0.90001f});

        // 持久电弧 —— 电击45% + 触发时间90%（延长麻痹时间）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lasting_arc", "lasting_arc",
                new Object[]{"electricity", 0.45001f, "triggerTime", 0.90001f});

        // 寒缚 —— 冰冻45% + 触发时间90%（延长减速）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.frost_bind", "frost_bind",
                new Object[]{"ice", 0.45001f, "triggerTime", 0.90001f});

        // ----- bane 克星组合 -----

        // 异类克星刃 —— 未定义克星30% + 近战伤害45%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.aberration_blade", "aberration_blade",
                new Object[]{"bane_of_undefined", 0.30001f, "meleeDamage", 0.45001f});

        // 灭尸之刃 —— 不死克星30% + 切割45%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.undead_bane_blade", "undead_bane_blade",
                new Object[]{"bane_of_undead", 0.30001f, "slash", 0.45001f});

        // 除虫打击 —— 节肢克星30% + 近战伤害45%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arthropod_purge", "arthropod_purge",
                new Object[]{"bane_of_arthropod", 0.30001f, "meleeDamage", 0.45001f});

        // 讨伐之弓 —— 灾厄村民克星30% + 远程伤害45%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.illager_hunt", "illager_hunt",
                new Object[]{"bane_of_illager", 0.30001f, "remoteDamage", 0.45001f});

        // ----- 暴击双词条（带互斥标签）-----

        // 致命连击 —— 近战暴击几率90% + 攻击速度30%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lethal_combo", "lethal_combo",
                new Object[]{"meleeCriticalStrikeProbability", 0.90001f, "attackSpeed", 0.30001f},
                "melee_crit_chance");

        // 精准连射 —— 远程暴击几率90% + 射速30%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.precise_volley", "precise_volley",
                new Object[]{"remoteCriticalStrikeProbability", 0.90001f, "firing_rate", 0.30001f},
                "remote_crit_chance");

        // 重击核心 —— 近战暴击伤害60% + 近战伤害45%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.heavy_crit_core", "heavy_crit_core",
                new Object[]{"meleeCriticalStrikeMultiplier", 0.60001f, "meleeDamage", 0.45001f},
                "melee_crit_mult");

        // 弱点打击 —— 远程暴击伤害60% + 远程伤害45%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.weak_spot_strike", "weak_spot_strike",
                new Object[]{"remoteCriticalStrikeMultiplier", 0.60001f, "remoteDamage", 0.45001f},
                "remote_crit_mult");

        // ----- magicDamage 散件 -----

        // 法刃 —— 魔法伤害90% + 攻击速度30%（近战法刃）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.spell_blade", "spell_blade",
                new Object[]{"magicDamage", 0.90001f, "attackSpeed", 0.30001f});

        // 咒术触发 —— 魔法伤害90% + 触发几率45%（法术异常流）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hex_trigger", "hex_trigger",
                new Object[]{"magicDamage", 0.90001f, "triggerChance", 0.45001f});

        // 魔能穿刺 —— 魔法伤害60% + 穿刺45%（破甲法术）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_pierce", "arcane_pierce",
                new Object[]{"magicDamage", 0.60001f, "puncture", 0.45001f});

        // ----- dash 冲刺入门 -----

        // 突进打击 —— 冲刺攻击距离 + 近战伤害30%（冲刺横扫入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lunge_strike", "lunge_strike",
                new Object[]{"dashAttackRange", 3.00001f, "meleeDamage", 0.30001f});

        // 疾刺 —— 冲刺触发几率135% + 切割45%（冲刺异常入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.rapid_thrust", "rapid_thrust",
                new Object[]{"dashTriggerChance", 1.35001f, "slash", 0.45001f});

        // ----- 横扫 / 稳定输出 / 远程散件 -----

        // 横扫 —— 攻击距离110% + 切割45%（横扫沾染入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.wide_sweep", "wide_sweep",
                new Object[]{"attackRange", 1.10001f, "slash", 0.45001f});

        // 巨刃 —— 攻击距离110% + 近战伤害45%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.great_blade", "great_blade",
                new Object[]{"attackRange", 1.10001f, "meleeDamage", 0.45001f});

        // 稳固打击 —— 非暴击基伤120% + 攻击速度30%（无暴击稳定流）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.steady_strike", "steady_strike",
                new Object[]{"baseDamageWhenNotCriticalStrike", 1.20001f, "attackSpeed", 0.30001f});

        // 疾斩 —— 攻击速度30% + 近战伤害45%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.swift_slash", "swift_slash",
                new Object[]{"attackSpeed", 0.30001f, "meleeDamage", 0.45001f});

        // 狂攻 —— 攻击速度45% - 近战伤害15%（高频换面板，狂战入门）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.frenzy_strike", "frenzy_strike",
                new Object[]{"attackSpeed", 0.45001f, "meleeDamage", -0.15001f});

        // 速射 —— 远程伤害90% + 射速30%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.rapid_fire", "rapid_fire",
                new Object[]{"remoteDamage", 0.90001f, "firing_rate", 0.30001f});

        // 连珠箭 —— 箭矢伤害90% + 射速30%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.chain_arrow", "chain_arrow",
                new Object[]{"arrowDamage", 0.90001f, "firing_rate", 0.30001f});

        // 飞弹齐射 —— 弹射物伤害90% + 多重射击30%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.missile_volley", "missile_volley",
                new Object[]{"projectileDamage", 0.90001f, "multishot", 0.30001f});

        // 散射 —— 多重射击45% + 远程伤害30%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.spread_shot", "spread_shot",
                new Object[]{"multishot", 0.45001f, "remoteDamage", 0.30001f});

        // ========== TACZ 枪械专属模组（需要 TACZ 才会注册）==========
        if (ItemRivenModule.isTaczLoaded()) {

            // 爆发装填
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.burst_reload", "burst_reload",
                    new Object[]{"reload_speed", 0.30001f});

            // 弹夹增幅
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.magazine_amplifier", "magazine_amplifier",
                    new Object[]{"magazine_size", 0.30001f});

            // 迅捷弹匣
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.swift_magazine", "swift_magazine",
                    new Object[]{"reload_speed", 0.20001f, "magazine_size", 0.20001f});

            // 穿甲弹头
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.piercing_round", "piercing_round",
                    new Object[]{"projectile_speed", 0.45001f, "puncture", 0.30001f});

            // 电磁加速弹
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.electromagnetic_accelerator", "electromagnetic_accelerator",
                    new Object[]{"remoteDamage", 0.60001f, "projectile_speed", 0.30001f});

            // 稳固射击
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.steady_shot", "steady_shot",
                    new Object[]{"remoteDamage", 0.60001f, "recoil_reduction", 0.30001f});

            // 轻量弹匣
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lightweight_magazine", "lightweight_magazine",
                    new Object[]{"magazine_size", 0.20001f, "reload_speed", 0.20001f, "recoil_reduction", 0.20001f});

            // ===== 第二批TACZ新属性青铜卡 =====

            // 精准射击 —— 30%精准度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.precise_shot", "precise_shot",
                    new Object[]{"accuracy", 0.30001f});

            // 迅捷举镜 —— 30%瞄准速度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.swift_aim", "swift_aim",
                    new Object[]{"aim_time", 0.30001f});

            // 致命要害 —— 60%爆头伤害倍率
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.vital_target", "vital_target",
                    new Object[]{"headshot_damage", 0.60001f});

            // 狙击直觉 —— 120%枪械伤害 + 20%精准度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.sniper_instinct", "sniper_instinct",
                    new Object[]{"gun_damage", 1.20001f, "accuracy", 0.20001f});

            // 重装弹头 —— 180%枪械伤害（纯枪伤入门）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.heavy_round", "heavy_round",
                    new Object[]{"gun_damage", 1.80001f});

            // 瞄准训练 —— 20%瞄准速度 + 20%精准度（双属性入门）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.aim_training", "aim_training",
                    new Object[]{"aim_time", 0.20001f, "accuracy", 0.20001f});

            // ===== ⭐ 第六批新增 · TACZ 冷门组合（铜卡）=====
            // 补「枪伤×元素 / 爆头×元素 / 精准×元素 / 速瞄×触发 / 续航×火力」等全空搭配。
            // gun_damage 走独立乘区，配低值元素(0.6)不超模；枪械补强符合整合包定位。

            // 燃烧弹 —— 枪械伤害120% + 火焰60%（枪伤×元素，火）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.incendiary_round", "incendiary_round",
                    new Object[]{"gun_damage", 1.20001f, "fire", 0.60001f});

            // 冰冻弹 —— 枪械伤害120% + 冰冻60%（枪伤×元素，冰）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.frost_round", "frost_round",
                    new Object[]{"gun_damage", 1.20001f, "ice", 0.60001f});

            // 毒气弹 —— 枪械伤害120% + 毒素60%（枪伤×元素，毒）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.toxic_round", "toxic_round",
                    new Object[]{"gun_damage", 1.20001f, "poison", 0.60001f});

            // 电磁弹 —— 枪械伤害120% + 电击60%（枪伤×元素，电）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.shock_round", "shock_round",
                    new Object[]{"gun_damage", 1.20001f, "electricity", 0.60001f});

            // 燃烧爆头 —— 爆头伤害60% + 火焰60%（爆头×元素，火）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.burning_headshot", "burning_headshot",
                    new Object[]{"headshot_damage", 0.60001f, "fire", 0.60001f});

            // 冰封爆头 —— 爆头伤害60% + 冰冻60%（爆头×元素，冰）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.freezing_headshot", "freezing_headshot",
                    new Object[]{"headshot_damage", 0.60001f, "ice", 0.60001f});

            // 雷霆爆头 —— 爆头伤害60% + 电击60%（爆头×元素，电）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.thunder_headshot", "thunder_headshot",
                    new Object[]{"headshot_damage", 0.60001f, "electricity", 0.60001f});

            // 精准燃烧 —— 精准度30% + 火焰60%（精准×元素）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.precise_burn", "precise_burn",
                    new Object[]{"accuracy", 0.30001f, "fire", 0.60001f});

            // 速瞄触发 —— 瞄准速度30% + 触发几率60%（速瞄×异常）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.quick_trigger", "quick_trigger",
                    new Object[]{"aim_time", 0.30001f, "triggerChance", 0.60001f});

            // 重火力弹 —— 枪械伤害150% + 后坐力降低30%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.heavy_firepower", "heavy_firepower",
                    new Object[]{"gun_damage", 1.50001f, "recoil_reduction", 0.30001f});

            // 稳定瞄准 —— 精准度30% + 后坐力降低30%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.stable_aim", "stable_aim",
                    new Object[]{"accuracy", 0.30001f, "recoil_reduction", 0.30001f});

            // 弹链供给 —— 弹夹容量30% + 射速30%（持续输出续航）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.belt_feed", "belt_feed",
                    new Object[]{"magazine_size", 0.30001f, "firing_rate", 0.30001f});

            // 快速换弹 —— 装填速度30% + 后坐力降低30%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.rapid_swap", "rapid_swap",
                    new Object[]{"reload_speed", 0.30001f, "recoil_reduction", 0.30001f});

            // 穿甲射击 —— 枪械伤害120% + 穿刺45%（枪械破甲）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ap_shot", "ap_shot",
                    new Object[]{"gun_damage", 1.20001f, "puncture", 0.45001f});

            // 爆头精准 —— 爆头伤害60% + 精准度30%（精准爆头入门）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.headshot_precision", "headshot_precision",
                    new Object[]{"headshot_damage", 0.60001f, "accuracy", 0.30001f});
        }
    }

    /**
     * 注册所有模组到创造标签页
     */
    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, Rarity.COMMON);

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
            CustomModuleManager.getInstance().addCustomItemModulesToRandomList(availableModules, Rarity.COMMON);

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
        return Rarity.COMMON;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
