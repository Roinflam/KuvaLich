// 文件：ItemPrimeModule.java
// 路径：forge/src/main/java/pers/roinflam/kuvalich/item/module/item/ItemPrimeModule.java
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
 * Prime级武器模组（1.20.1版本）
 * Prime (Epic) tier weapon module (1.20.1 version)
 *
 * ⭐ 第三批新增 · 单词条 Prime（银卡数值×1.5，与对应银卡同 type 互斥）：
 *    通用：收割之刻Prime（收集者阈值3%）、涤魂Prime（净化驱散15%）、斩首Prime（致命斩首0.015%）
 *    TACZ：洞穿弹头Prime（真实伤害30%）、战利搜集Prime（枪械战利品掉落45%）
 *
 * ⭐ 第六批新增 · 冷门 Prime（强度复合，部分与金卡同 type 互斥形成拉梯）：
 *    通用：神王之锤(冲击+暴伤,与金卡titan_hammer互斥)、终焉审判(基伤+处决)、影刃刺客(冲刺+处决)、
 *      奥术终结(魔法+处决)、时之囚笼(triggerTime专精)、屠魔圣典(四bane,与slaughter_feast互斥)、
 *      瘟疫领主、磁暴领主、病毒君主、大法师(与archmage_blade互斥)、元素法神(与arcane_elements互斥)、
 *      风暴突袭(冲刺+净化)、灭世箭神(与apocalypse_bow互斥)、暴君终结(基伤+秒杀)；
 *    TACZ：燃焰处决(与flame_execution互斥)、真伤狙神、腐蚀狂潮、辐射主宰、战利狙神、净化风暴(与purge_storm互斥)。
 */
public class ItemPrimeModule extends AbstractItemModule {

    public static List<ItemStack> itemStackList = new ArrayList<>();
    private static boolean isInitialized = false;

    public ItemPrimeModule(Properties properties) {
        super(properties);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_PRIME_MODULE.get());
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

        // ===== Prime 卡 =====

        // 压力点Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.pressure_point_prime", "pressure_point",
                new Object[]{"meleeDamage", 1.65001f});

        // 攻击范围Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reach_prime", "reach",
                new Object[]{"attackRange", 1.65001f});

        // 狂怒Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fury_prime", "fury",
                new Object[]{"attackSpeed", 0.55001f});

        // 掠食本能Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.predatory_instinct_prime", "predatory_instinct",
                new Object[]{"dashMeleeCriticalStrikeProbability", 2.00001f, "dashAttackRange", 5.00001f},
                "dash_crit_chance", "dash_range");

        // 猎杀时刻Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hunters_moment_prime", "hunters_moment",
                new Object[]{"dashMeleeCriticalStrikeProbability", 2.00001f, "dashTriggerChance", 2.40001f},
                "dash_crit_chance", "dash_trigger");

        // 非暴力美学Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.violent_aesthetic_prime", "violent_aesthetic",
                new Object[]{"baseDamageWhenNotCriticalStrike", 3.00001f, "attackSpeed", 0.40001f});

        // 感染协议Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.infection_protocol_prime", "infection_protocol",
                new Object[]{"triggerChance", 1.20001f, "triggerTime", 1.00001f});

        // 双重契约Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.dual_covenant_prime", "dual_covenant",
                new Object[]{"meleeDamage", 1.80001f, "remoteDamage", 1.80001f});

        // 秘法弓术Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.mystic_archery_prime", "mystic_archery",
                new Object[]{"arrowDamage", 2.00001f, "magicDamage", 1.35001f});

        // 奥能轨迹Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_ballistics_prime", "arcane_ballistics",
                new Object[]{"projectileDamage", 2.00001f, "magicDamage", 1.35001f});

        // 死神镰刀Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reapers_scythe_prime", "reapers_scythe",
                new Object[]{"multishot", 0.90001f, "remoteCriticalStrikeProbability", 1.20001f},
                "multishot", "remote_crit_chance");

        // 猎人法则Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hunters_code_prime", "hunters_code",
                new Object[]{"remoteCriticalStrikeMultiplier", 1.20001f, "slash", 1.65001f},
                "remote_crit_mult");

        // 剥皮者Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosion_king_prime", "corrosion_king",
                new Object[]{"corrosion", 1.65001f, "slash", 1.25001f});

        // 瘟疫使者Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.plague_herald_prime", "plague_herald",
                new Object[]{"gas", 1.65001f, "triggerTime", 1.35001f});

        // 核子风暴Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.nuclear_storm_prime", "nuclear_storm",
                new Object[]{"radiation", 1.65001f, "multishot", 0.90001f});

        // 磁暴领主Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magnetic_overlord_prime", "magnetic_overlord",
                new Object[]{"magnetic", 1.65001f, "remoteCriticalStrikeProbability", 1.35001f},
                "remote_crit_chance");

        // 灾厄降临Prime
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.catastrophe_prime", "catastrophe",
                new Object[]{"bursting_radius", 0.33001f, "triggerChance", 0.90001f, "triggerTime", 0.90001f});

        // 收割之刻 Prime —— 收集者阈值 3%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reapers_threshold_prime", "execute_threshold",
                new Object[]{"execute_threshold", 0.03001f});

        // 涤魂 Prime —— 净化驱散 15%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.soul_cleanse_prime", "purge_buff",
                new Object[]{"purge_buff", 0.15001f});

        // 斩首 Prime —— 致命斩首 0.015%
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.decapitate_prime", "execute_chance",
                new Object[]{"execute_chance", 0.00015f});

        // ===== 镀层（Galvanized）卡 =====

        // 镀层分裂膛室
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_chamber", "split_chamber",
                new Object[]{"multishot", 0.80001f, "killStackMultishot", 0.30001f},
                "multishot");

        // 镀层真钢
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_steel", "true_steel",
                new Object[]{"meleeCriticalStrikeProbability", 1.10001f, "killStackMeleeCriticalMultiplier", 0.30001f},
                "melee_crit_chance");

        // 镀层攻击范围
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_reach", "reach",
                new Object[]{"attackRange", 0.80001f, "killStackAttackRange", 0.30001f});

        // 镀层狂怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_fury", "fury",
                new Object[]{"attackSpeed", 0.30001f, "killStackAttackSpeed", 0.10001f});

        // 镀层武器资质
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_aptitude", "weapon_aptitude",
                new Object[]{"triggerChance", 0.60001f, "killStackTriggerChance", 0.30001f});

        // 镀层速度触发
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_speed_trigger", "speed_trigger",
                new Object[]{"firing_rate", 0.40001f, "killStackFiringRate", 0.20001f});

        // 镀层火焰风暴
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_firestorm", "firestorm",
                new Object[]{"bursting_radius", 0.40001f, "killStackBurstingRadius", 0.20001f});

        // 镀层掠食本能
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_predatory_instinct", "predatory_instinct",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashAttackRange", 3.00001f, "killStackAttackRange", 0.50001f},
                "dash_crit_chance", "dash_range");

        // 镀层猎杀时刻
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_hunters_moment", "hunters_moment",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashTriggerChance", 1.35001f, "killStackTriggerChance", 0.30001f},
                "dash_crit_chance", "dash_trigger");

        // 镀层暴力美学
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_violent_aesthetic", "violent_aesthetic",
                new Object[]{"baseDamageWhenNotCriticalStrike", 2.00001f, "attackSpeed", 0.75001f, "killStackAttackSpeed", 0.10001f});

        // 镀层感染协议
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_infection_protocol", "infection_protocol",
                new Object[]{"triggerChance", 0.90001f, "triggerTime", 0.75001f, "killStackTriggerChance", 0.30001f});

        // 镀层双重契约
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_dual_covenant", "dual_covenant",
                new Object[]{"meleeDamage", 1.20001f, "remoteDamage", 1.20001f, "killStackMeleeCriticalMultiplier", 0.50001f});

        // 镀层死神镰刀
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_reapers_scythe", "reapers_scythe",
                new Object[]{"multishot", 0.60001f, "remoteCriticalStrikeProbability", 0.80001f, "killStackMultishot", 0.30001f});

        // 镀层震荡领域
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_shockwave_domain", "shockwave_domain",
                new Object[]{"bursting_radius", 0.24001f, "triggerTime", 0.75001f, "killStackBurstingRadius", 0.05001f});

        // 镀层死亡弹幕
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.galvanized_death_barrage", "death_barrage",
                new Object[]{"multishot", 0.45001f, "triggerChance", 0.75001f, "killStackMultishot", 0.25001f});

        // ========== ⭐ 第六批新增 · 通用 Prime（冷门强度复合）==========
        // 处决数值用 Prime 档（阈值3% / 秒杀0.015%）；部分卡复用金卡 type 形成金⇄Prime 互斥拉梯。

        // 神王之锤Prime —— 冲击120% + 近战暴击伤害60%（与金卡 titan_hammer 互斥）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.titan_hammer_prime", "titan_hammer",
                new Object[]{"impact", 1.20001f, "meleeCriticalStrikeMultiplier", 0.60001f},
                "melee_crit_mult");

        // 终焉审判Prime —— 非暴击基伤300% + 处决阈值3%（基伤+处决Prime）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.doom_judgment_prime", "doom_judgment",
                new Object[]{"baseDamageWhenNotCriticalStrike", 3.00001f, "execute_threshold", 0.03001f});

        // 影刃刺客Prime —— 冲刺暴击200% + 处决阈值3% + 冲刺距离（冲刺+处决Prime）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.shadow_assassin_prime", "shadow_assassin",
                new Object[]{"dashMeleeCriticalStrikeProbability", 2.00001f, "execute_threshold", 0.03001f, "dashAttackRange", 5.00001f},
                "dash_crit_chance", "dash_range");

        // 奥术终结Prime —— 魔法伤害240% + 处决阈值3% + 触发几率60%（魔法+处决Prime）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_finisher_prime", "arcane_finisher",
                new Object[]{"magicDamage", 2.40001f, "execute_threshold", 0.03001f, "triggerChance", 0.60001f});

        // 时之囚笼Prime —— 触发时间200% + 触发几率90%（DOT 延长终极专精）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.time_prison_prime", "time_prison",
                new Object[]{"triggerTime", 2.00001f, "triggerChance", 0.90001f});

        // 屠魔圣典Prime —— 四系克星各40%（与金卡 slaughter_feast 互斥）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.demon_codex_prime", "slaughter_feast",
                new Object[]{"bane_of_undead", 0.40001f, "bane_of_arthropod", 0.40001f, "bane_of_illager", 0.40001f});

        // 瘟疫领主Prime —— 毒气165% + 攻击距离90% + 触发几率30%（横扫毒云Prime）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.plague_lord_prime", "plague_lord",
                new Object[]{"gas", 1.65001f, "attackRange", 0.90001f, "triggerChance", 0.30001f});

        // 磁暴领主Prime —— 磁力165% + 穿刺90%（破甲磁暴Prime）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magnetic_lord_prime", "magnetic_lord",
                new Object[]{"magnetic", 1.65001f, "puncture", 0.90001f});

        // 病毒君主Prime —— 病毒165% + 冲击90% + 近战伤害60%（近战病毒Prime）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.viral_sovereign_prime", "viral_sovereign",
                new Object[]{"virus", 1.65001f, "impact", 0.90001f, "meleeDamage", 0.60001f});

        // 大法师Prime —— 魔法伤害240% + 攻击速度60%（与金卡 archmage_blade 互斥）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.archmage_prime", "archmage_blade",
                new Object[]{"magicDamage", 2.40001f, "attackSpeed", 0.60001f});

        // 元素法神Prime —— 魔法伤害180% + 火焰90% + 冰冻90% + 触发几率30%（与金卡 arcane_elements 互斥）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.elemental_god_prime", "arcane_elements",
                new Object[]{"magicDamage", 1.80001f, "fire", 0.90001f, "ice", 0.90001f, "triggerChance", 0.30001f});

        // 风暴突袭Prime —— 冲刺暴击200% + 冲刺触发240% + 净化驱散10%（冲刺+净化Prime）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.storm_raid_prime", "storm_raid",
                new Object[]{"dashMeleeCriticalStrikeProbability", 2.00001f, "dashTriggerChance", 2.40001f, "purge_buff", 0.10001f},
                "dash_crit_chance", "dash_trigger");

        // 灭世箭神Prime —— 未定义克星45% + 箭矢伤害180% + 多重射击45%（与金卡 apocalypse_bow 互斥）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.apocalypse_archer_prime", "apocalypse_bow",
                new Object[]{"bane_of_undefined", 0.45001f, "arrowDamage", 1.80001f, "multishot", 0.45001f});

        // 暴君终结Prime —— 非暴击基伤300% + 秒杀概率0.015% - 近战伤害45%（基伤+秒杀Prime）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.tyrant_finisher_prime", "tyrant_finisher",
                new Object[]{"baseDamageWhenNotCriticalStrike", 3.00001f, "execute_chance", 0.00015f, "meleeDamage", -0.45001f});

        // ========== TACZ 枪械专属模组（需要 TACZ 才会注册）==========
        if (ItemRivenModule.isTaczLoaded()) {

            // ===== Prime 卡 =====

            // 爆发装填 Prime
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.burst_reload_prime", "burst_reload",
                    new Object[]{"reload_speed", 0.55001f});

            // 弹夹增幅 Prime
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.magazine_amplifier_prime", "magazine_amplifier",
                    new Object[]{"magazine_size", 0.55001f});

            // 致命弹道 Prime
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lethal_trajectory_prime", "lethal_trajectory",
                    new Object[]{"projectile_speed", 0.90001f, "remoteCriticalStrikeProbability", 0.75001f},
                    "remote_crit_chance");

            // 战术装填 Prime
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.tactical_reload_prime", "tactical_reload",
                    new Object[]{"reload_speed", 0.55001f, "recoil_reduction", 0.55001f});

            // 镇定射击 Prime
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.steady_aim_prime", "steady_aim",
                    new Object[]{"recoil_reduction", 0.60001f, "triggerChance", 0.75001f});

            // 弹道校准 Prime
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ballistic_calibration_prime", "ballistic_calibration",
                    new Object[]{"projectile_speed", 0.60001f, "remoteCriticalStrikeMultiplier", 0.75001f},
                    "remote_crit_mult");

            // 弹道学 Prime —— 375%枪械伤害 + 45%精准度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ballistics_prime", "ballistics",
                    new Object[]{"gun_damage", 3.75001f, "accuracy", 0.45001f});

            // 处刑者 Prime —— 180%爆头伤害 + 120%远程暴伤
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.executioner_prime", "executioner",
                    new Object[]{"headshot_damage", 1.80001f, "remoteCriticalStrikeMultiplier", 1.20001f},
                    "remote_crit_mult");

            // 极速瞄具 Prime —— 55%瞄准速度 + 45%精准度 + 30%后坐力降低
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.quickdraw_sight_prime", "quickdraw_sight",
                    new Object[]{"aim_time", 0.55001f, "accuracy", 0.45001f, "recoil_reduction", 0.30001f});

            // 弱点锁定 Prime —— 135%爆头倍率 + 45%精准度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.weak_point_lock_prime", "weak_point_lock",
                    new Object[]{"headshot_damage", 1.35001f, "accuracy", 0.45001f});

            // 致命精准 Prime —— 180%枪械伤害 + 90%远程暴击几率
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lethal_accuracy_prime", "lethal_accuracy",
                    new Object[]{"gun_damage", 1.80001f, "remoteCriticalStrikeProbability", 0.90001f},
                    "remote_crit_chance");

            // 快速反应 Prime —— 55%瞄准速度 + 180%枪械伤害
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.quick_response_prime", "quick_response",
                    new Object[]{"aim_time", 0.55001f, "gun_damage", 1.80001f});

            // 爆头专家 Prime —— 165%爆头倍率 + 45%瞄准速度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.headshot_expert_prime", "headshot_expert",
                    new Object[]{"headshot_damage", 1.65001f, "aim_time", 0.45001f});

            // 洞穿弹头 Prime —— 真实伤害 30%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.penetrator_round_prime", "true_bullet",
                    new Object[]{"true_bullet", 0.30001f});

            // 战利搜集 Prime —— 枪械战利品掉落 45%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.spoils_collector_prime", "gun_loot_drop",
                    new Object[]{"gun_loot_drop", 0.45001f});

            // ===== 镀层（Galvanized）卡 =====

            // 镀层 弹道学 —— 200%枪械伤害 + 击杀叠多重30%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.galvanized_ballistics", "ballistics",
                    new Object[]{"gun_damage", 2.00001f, "killStackMultishot", 0.30001f},
                    "multishot");

            // 镀层 猎首者 —— 60%爆头倍率 + 60%远程暴击几率 + 击杀叠触发30%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.galvanized_headhunter", "headhunter",
                    new Object[]{"headshot_damage", 0.60001f, "remoteCriticalStrikeProbability", 0.60001f, "killStackTriggerChance", 0.30001f});

            // 镀层 极速瞄具 —— 30%瞄准速度 + 30%后坐力降低 + 击杀叠射速20%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.galvanized_quickdraw_sight", "quickdraw_sight",
                    new Object[]{"aim_time", 0.30001f, "recoil_reduction", 0.30001f, "killStackFiringRate", 0.20001f});

            // 镀层 火力集中 —— 120%枪械伤害 + 45%爆头倍率 + 击杀叠多重25%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.galvanized_focused_firepower", "focused_firepower",
                    new Object[]{"gun_damage", 1.20001f, "headshot_damage", 0.45001f, "killStackMultishot", 0.25001f},
                    "multishot");

            // ===== ⭐ 第六批新增 · TACZ Prime（冷门强度复合）=====

            // 燃焰处决Prime —— 枪械伤害240% + 火焰90% + 处决阈值3%（与金卡 flame_execution 互斥）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.flame_execution_prime", "flame_execution",
                    new Object[]{"gun_damage", 2.40001f, "fire", 0.90001f, "execute_threshold", 0.03001f});

            // 真伤狙神Prime —— 真实子弹20% + 爆头伤害120% + 精准度45%（精准爆头真伤狙Prime）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.true_marksman_prime", "true_marksman",
                    new Object[]{"true_bullet", 0.20001f, "headshot_damage", 1.20001f, "accuracy", 0.45001f});

            // 腐蚀狂潮Prime —— 枪械伤害180% + 腐蚀90% + 多重射击30%（破甲弹幕Prime）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.corrosive_tide_prime", "corrosive_tide_gun",
                    new Object[]{"gun_damage", 1.80001f, "corrosion", 0.90001f, "multishot", 0.30001f});

            // 辐射主宰Prime —— 枪械伤害180% + 辐射90% + 精准度30%（精准辐射弹药Prime）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.radiation_master_prime", "radiation_master",
                    new Object[]{"gun_damage", 1.80001f, "radiation", 0.90001f, "accuracy", 0.30001f});

            // 战利狙神Prime —— 枪械战利品掉落45% + 爆头伤害120%（爆头刷材料狙Prime）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.loot_master_prime", "loot_master",
                    new Object[]{"gun_loot_drop", 0.45001f, "headshot_damage", 1.20001f});

            // 净化风暴Prime —— 枪械伤害180% + 净化驱散15% + 爆头伤害60%（与金卡 purge_storm 互斥）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_PRIME_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.purge_storm_prime", "purge_storm",
                    new Object[]{"gun_damage", 1.80001f, "purge_buff", 0.15001f, "headshot_damage", 0.60001f});
        }
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, Rarity.EPIC);

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
            CustomModuleManager.getInstance().addCustomItemModulesToRandomList(availableModules, Rarity.EPIC);

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
        return Rarity.EPIC;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
