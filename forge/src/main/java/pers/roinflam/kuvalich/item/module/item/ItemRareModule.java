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
 * 黄金级武器模组（1.20.1版本）
 * Rare (Gold) tier weapon module (1.20.1 version)
 *
 * ⭐ 第三批新增 · 复合金卡：
 *    通用：净世审判（净化10%+收集者阈值2%）、处决之刃（收集者阈值2%+近战伤害100%+近战暴击60%）、
 *          斩首弹幕（致命斩首0.01%+多重射击30%+射速30%）、收割弹幕（收集者阈值2%+多重射击30%+远程伤害50%）
 *    TACZ：幻影弹（真伤15%+枪械伤害100%）、影袭（真伤15%+爆头60%+瞄准速度30%）、
 *          穿透处决（真伤15%+收集者阈值2%）、血色掠夺（枪械战利品35%+多重射击30%）、
 *          战利补给（枪械战利品30%+弹夹30%+装填20%）、净化弹幕（净化10%+精准20%+多重射击30%）
 *
 * ⭐ 第四批新增 · 复合金卡：
 *    通用：死神协奏（阈值2%+斩首0.01%）、净魂斩（净化15%+斩首0.01%+多重30%）、
 *          腐化收割（净化10%+阈值2%+远程50%）、狂乱斩首（斩首0.01%+攻速60%+近战暴击60%）、
 *          灵魂榨取（净化10%+近战100%+阈值2%）
 *    TACZ：收集者（阈值2.5%+枪械战利品35%）、穿魂弹（真伤15%+净化10%）、
 *          湮灭连射（真伤10%+射速30%+多重30%）、断罪者（真伤15%+斩首0.01%+爆头60%）、
 *          尸潮收割（枪械战利品30%+斩首0.01%+射速30%）、真理之触（真伤20%+精准30%）
 *
 * ⭐ 第五批新增 · 复合金卡（新词条 × 老词条交叉）：
 *    通用：腐蚀处决、瘟疫收割、雷暴斩首、湮灭爆轰、净化风暴、夺魂连斩、
 *          死亡箭雨、血祭审判、寒狱处决、灭杀领域、暴君终焉
 *    TACZ：穿甲真弹、影刃真伤、静默处决者、精准搜刮、真理弹幕
 */
public class ItemRareModule extends AbstractItemModule {

    public static List<ItemStack> itemStackList = new ArrayList<>();
    private static boolean isInitialized = false;

    public ItemRareModule(Properties properties) {
        super(properties);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RARE_MODULE.get());
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

        // ========== 原有MOD ==========

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.split_chamber", "split_chamber",
                new Object[]{"multishot", 0.90001f},
                "multishot");

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.vital_sense", "vital_sense",
                new Object[]{"remoteCriticalStrikeMultiplier", 1.2001f},
                "remote_crit_mult");

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.spoiled_strike", "spoiled_strike",
                new Object[]{"meleeDamage", 1.00001f, "attackSpeed", -0.20001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.heavy_caliber", "heavy_caliber",
                new Object[]{"remoteDamage", 0.90001f, "remoteCriticalStrikeMultiplier", 0.90001f, "firing_rate", -0.60001f},
                "remote_crit_mult", "firing_rate");

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.firestorm", "firestorm",
                new Object[]{"bursting_radius", 0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.critical_delay", "critical_delay",
                new Object[]{"remoteCriticalStrikeProbability", 2.00001f, "firing_rate", -0.20001f},
                "remote_crit_chance", "firing_rate");

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.vile_acceleration", "vile_acceleration",
                new Object[]{"firing_rate", 0.90001f, "remoteDamage", -0.15001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hollow_point", "hollow_point",
                new Object[]{"remoteCriticalStrikeProbability", 1.50001f, "remoteDamage", -0.15001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hammer_shot", "hammer_shot",
                new Object[]{"remoteCriticalStrikeMultiplier", 0.60001f, "triggerChance", 0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.creeping_bullseye", "creeping_bullseye",
                new Object[]{"remoteCriticalStrikeProbability", 1.87501f, "firing_rate", -0.36001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.katana_energy", "katana_energy",
                new Object[]{"attackRange", 1.2001f, "meleeDamage", 0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reflex_coil", "reflex_coil",
                new Object[]{"attackSpeed", 0.60001f, "meleeCriticalStrikeProbability", 0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.elemental_strike", "elemental_strike",
                new Object[]{"fire", 0.30001f, "ice", 0.30001f, "poison", 0.30001f, "electricity", 0.30001f});

        // ========== 新增MOD（黄金级别）==========

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.plague_herald", "plague_herald",
                new Object[]{"gas", 0.90001f, "triggerTime", 0.75001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.nuclear_storm", "nuclear_storm",
                new Object[]{"radiation", 0.90001f, "multishot", 0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magnetic_overlord", "magnetic_overlord",
                new Object[]{"magnetic", 0.90001f, "remoteCriticalStrikeProbability", 0.75001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosion_king", "corrosion_king",
                new Object[]{"corrosion", 1.20001f, "slash", 0.75001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.doomsday_arrow", "doomsday_arrow",
                new Object[]{"explosion", 0.90001f, "arrowDamage", 1.20001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.viral_tyrant", "viral_tyrant",
                new Object[]{"virus", 1.20001f, "puncture", 0.75001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.meltdown_protocol", "meltdown_protocol",
                new Object[]{"radiation", 0.90001f, "bursting_radius", 0.10001f, "triggerChance", 0.30001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.biohazard", "biohazard",
                new Object[]{"gas", 0.90001f, "triggerChance", 0.30001f, "triggerTime", 0.40001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.em_storm", "em_storm",
                new Object[]{"magnetic", 0.90001f, "remoteCriticalStrikeMultiplier", 0.90001f, "firing_rate", -0.30001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosive_erosion", "corrosive_erosion",
                new Object[]{"corrosion", 0.90001f, "puncture", 0.90001f, "attackSpeed", -0.30001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.scorched_earth", "scorched_earth",
                new Object[]{"explosion", 0.90001f, "multishot", 0.75001f, "remoteDamage", -0.25001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.pandemic_outbreak", "pandemic_outbreak",
                new Object[]{"virus", 0.90001f, "slash", 0.60001f, "meleeCriticalStrikeProbability", 0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.berserkers_oath", "berserkers_oath",
                new Object[]{"dashMeleeCriticalStrikeProbability", 2.25001f, "dashAttackRange", 5.00001f, "meleeDamage", -0.90001f},
                "dash_crit_chance", "dash_range");

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.predators_mark", "predators_mark",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.65001f, "puncture", 0.90001f, "triggerTime", -0.40001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reapers_scythe", "reapers_scythe",
                new Object[]{"multishot", 0.60001f, "remoteCriticalStrikeProbability", 0.80001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hunters_code", "hunters_code",
                new Object[]{"remoteCriticalStrikeMultiplier", 0.60001f, "slash", 0.90001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.gladiators_glory", "gladiators_glory",
                new Object[]{"meleeCriticalStrikeMultiplier", 1.20001f, "meleeCriticalStrikeProbability", -0.60001f},
                "melee_crit_mult", "melee_crit_chance");

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.iron_oath", "iron_oath",
                new Object[]{"baseDamageWhenNotCriticalStrike", 2.40001f, "puncture", 0.90001f, "meleeCriticalStrikeProbability", -0.45001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.ultimate_violence", "ultimate_violence",
                new Object[]{"baseDamageWhenNotCriticalStrike", 2.40001f, "impact", 0.90001f, "remoteCriticalStrikeProbability", -0.45001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.catastrophe", "catastrophe",
                new Object[]{"bursting_radius", 0.15001f, "triggerChance", 0.60001f, "triggerTime", 0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_torrent", "arcane_torrent",
                new Object[]{"magicDamage", 1.80001f, "multishot", 0.45001f, "firing_rate", -0.45001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.mystic_apocalypse", "mystic_apocalypse",
                new Object[]{"arrowDamage", 1.80001f, "magicDamage", 1.35001f, "firing_rate", -0.45001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.dual_legend", "dual_legend",
                new Object[]{"meleeDamage", 2.20001f, "remoteDamage", 2.20001f, "attackSpeed", -0.25001f, "firing_rate", -0.25001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.dual_disaster", "dual_disaster",
                new Object[]{"meleeCriticalStrikeProbability", 1.50001f, "remoteCriticalStrikeProbability", 1.20001f, "meleeDamage", -0.50001f, "remoteDamage", -0.50001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_domination", "arcane_domination",
                new Object[]{"magicDamage", 2.40001f, "triggerChance", 0.90001f, "meleeDamage", -0.45001f, "remoteDamage", -0.45001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.blood_frenzy", "blood_frenzy",
                new Object[]{"meleeCriticalStrikeProbability", 1.80001f, "attackSpeed", 0.45001f, "meleeCriticalStrikeMultiplier", -0.60001f},
                "melee_crit_chance", "melee_crit_mult");

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.crying_wounds", "crying_wounds",
                new Object[]{"slash", 1.65001f, "triggerChance", 1.35001f, "meleeCriticalStrikeProbability", -0.50001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.legendary_shot", "legendary_shot",
                new Object[]{"arrowDamage", 1.80001f, "remoteCriticalStrikeProbability", 1.20001f, "firing_rate", -0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.ballistic_master", "ballistic_master",
                new Object[]{"projectileDamage", 1.80001f, "remoteCriticalStrikeMultiplier", 1.20001f, "firing_rate", -0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.condition_outbreak", "condition_outbreak",
                new Object[]{"killStackBaseDamage", 0.08f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.slaughter_feast", "slaughter_feast",
                new Object[]{"bane_of_undefined", 0.25001f, "bane_of_undead", 0.25001f, "bane_of_arthropod", 0.25001f, "bane_of_illager", 0.25001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.chain_catastrophe", "chain_catastrophe",
                new Object[]{"bursting_radius", 0.44001f, "triggerChance", 0.75001f, "multishot", -0.75001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.radiation_lord", "radiation_lord",
                new Object[]{"radiation", 1.20001f, "remoteCriticalStrikeMultiplier", 0.90001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosion_tide", "corrosion_tide",
                new Object[]{"corrosion", 0.60001f, "meleeDamage", 0.60001f, "attackSpeed", 0.30001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.toxic_cloud", "toxic_cloud",
                new Object[]{"gas", 1.20001f, "bursting_radius", 0.15001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magnetic_collapse", "magnetic_collapse",
                new Object[]{"magnetic", 1.20001f, "triggerTime", 0.90001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.apocalypse_split", "apocalypse_split",
                new Object[]{"explosion", 0.90001f, "arrowDamage", 1.35001f, "multishot", 0.30001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.viral_vector", "viral_vector",
                new Object[]{"virus", 1.20001f, "projectileDamage", 1.20001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.melee_fury", "melee_fury",
                new Object[]{"meleeDamage", 2.00001f, "attackSpeed", 0.60001f, "meleeCriticalStrikeProbability", -0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.ranged_fury", "ranged_fury",
                new Object[]{"remoteDamage", 2.00001f, "firing_rate", 0.60001f, "remoteCriticalStrikeProbability", -0.60001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.predatory_hybrid", "predatory_hybrid",
                new Object[]{"remoteDamage", 1.05001f, "dashMeleeCriticalStrikeProbability", 1.20001f, "dashAttackRange", 3.00001f},
                "dash_crit_chance", "dash_range");

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bombardment", "bombardment",
                new Object[]{"multishot", 1.20001f, "dashMeleeCriticalStrikeProbability", 1.20001f, "firing_rate", -0.25001f});

        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.elemental_fusion", "elemental_fusion",
                new Object[]{"electricity", 0.30001f, "poison", 0.30001f, "fire", 0.30001f, "ice", 0.30001f});

        // ========== 新增通用词条 · 复合金卡 ==========

        // 净世审判 —— 净化驱散10% + 收集者阈值2%（先驱散不死增益再处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.purifying_judgment", "purifying_judgment",
                new Object[]{"purge_buff", 0.10001f, "execute_threshold", 0.02001f});

        // 处决之刃 —— 收集者阈值2% + 近战伤害100% + 近战暴击几率60%（近战处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.execution_blade", "execution_blade",
                new Object[]{"execute_threshold", 0.02001f, "meleeDamage", 1.00001f, "meleeCriticalStrikeProbability", 0.60001f});

        // 斩首弹幕 —— 致命斩首0.01% + 多重射击30% + 射速30%（弹幕放大斩首判定）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.decapitation_barrage", "decapitation_barrage",
                new Object[]{"execute_chance", 0.0001f, "multishot", 0.30001f, "firing_rate", 0.30001f});

        // 收割弹幕 —— 收集者阈值2% + 多重射击30% + 远程伤害50%（远程处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reaper_barrage", "reaper_barrage",
                new Object[]{"execute_threshold", 0.02001f, "multishot", 0.30001f, "remoteDamage", 0.50001f});

        // ========== 第四批新增通用词条 · 复合金卡 ==========

        // 死神协奏 —— 收集者阈值2% + 致命斩首0.01%（双处决：低血必杀 + 随机秒杀）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reapers_concerto", "reapers_concerto",
                new Object[]{"execute_threshold", 0.02001f, "execute_chance", 0.0001f});

        // 净魂斩 —— 净化驱散15% + 致命斩首0.01% + 多重射击30%（驱散增益后斩首弹幕）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.soul_reaver", "soul_reaver",
                new Object[]{"purge_buff", 0.15001f, "execute_chance", 0.0001f, "multishot", 0.30001f});

        // 腐化收割 —— 净化驱散10% + 收集者阈值2% + 远程伤害50%（远程净化处决流）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.blighted_harvest", "blighted_harvest",
                new Object[]{"purge_buff", 0.10001f, "execute_threshold", 0.02001f, "remoteDamage", 0.50001f});

        // 狂乱斩首 —— 致命斩首0.01% + 攻击速度60% + 近战暴击几率60%（高频近战斩首）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.frenzied_decapitation", "frenzied_decapitation",
                new Object[]{"execute_chance", 0.0001f, "attackSpeed", 0.60001f, "meleeCriticalStrikeProbability", 0.60001f});

        // 灵魂榨取 —— 净化驱散10% + 近战伤害100% + 收集者阈值2%（近战净化处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.soul_harvest", "soul_harvest",
                new Object[]{"purge_buff", 0.10001f, "meleeDamage", 1.00001f, "execute_threshold", 0.02001f});

        // ========== 第五批新增 · 通用复合金卡（新词条 × 老词条交叉搭配）==========

        // 腐蚀处决 —— 处决阈值2% + 腐蚀90% + 穿刺60%（破甲后压血处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosive_execution", "corrosive_execution",
                new Object[]{"execute_threshold", 0.02001f, "corrosion", 0.90001f, "puncture", 0.60001f});

        // 瘟疫收割 —— 处决阈值2% + 毒素90% + 触发时间60%（毒DOT压血触发处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.plague_reaping", "plague_reaping",
                new Object[]{"execute_threshold", 0.02001f, "poison", 0.90001f, "triggerTime", 0.60001f});

        // 雷暴斩首 —— 秒杀概率0.01% + 电击90% + 触发几率60%（高触发顺带秒杀）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.thunderous_decapitation", "thunderous_decapitation",
                new Object[]{"execute_chance", 0.0001f, "electricity", 0.90001f, "triggerChance", 0.60001f});

        // 湮灭爆轰 —— 处决阈值2% + 爆炸半径 + 爆炸元素60%（范围压血群体处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.annihilating_blast", "annihilating_blast",
                new Object[]{"execute_threshold", 0.02001f, "bursting_radius", 0.15001f, "explosion", 0.60001f});

        // 净化风暴 —— 抹除增益15% + 磁力90% + 触发几率30%（磁力抹盾 + 高概率抹增益）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.cleansing_storm", "cleansing_storm",
                new Object[]{"purge_buff", 0.15001f, "magnetic", 0.90001f, "triggerChance", 0.30001f});

        // 夺魂连斩 —— 抹除增益10% + 近战暴击伤害120% + 切割60%（近战暴击抹增益切割）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.soul_rend", "soul_rend",
                new Object[]{"purge_buff", 0.10001f, "meleeCriticalStrikeMultiplier", 1.20001f, "slash", 0.60001f});

        // 死亡箭雨 —— 秒杀概率0.01% + 箭矢伤害180% + 多重射击60%（弓流秒杀箭雨）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.deaths_arrow", "deaths_arrow",
                new Object[]{"execute_chance", 0.0001f, "arrowDamage", 1.80001f, "multishot", 0.60001f});

        // 血祭审判 —— 处决阈值2% + 魔法伤害150% + 触发几率60%（魔法压血处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.blood_judgment", "blood_judgment",
                new Object[]{"execute_threshold", 0.02001f, "magicDamage", 1.50001f, "triggerChance", 0.60001f});

        // 寒狱处决 —— 处决阈值2% + 冰冻90% + 攻击速度30%（冰减速 + 处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.frozen_execution", "frozen_execution",
                new Object[]{"execute_threshold", 0.02001f, "ice", 0.90001f, "attackSpeed", 0.30001f});

        // 灭杀领域 —— 秒杀概率0.01% + 爆炸半径 + 触发时间60%（范围命中放大秒杀判定）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.culling_field", "culling_field",
                new Object[]{"execute_chance", 0.0001f, "bursting_radius", 0.15001f, "triggerTime", 0.60001f});

        // 暴君终焉 —— 处决阈值2.5% + 未定义克星30% + 灾厄村民克星30%（克制怪强化处决）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.tyrants_end", "tyrants_end",
                new Object[]{"execute_threshold", 0.02501f, "bane_of_undefined", 0.30001f, "bane_of_illager", 0.30001f});

        // ========== TACZ 枪械专属模组（需要 TACZ 才会注册）==========
        if (ItemRivenModule.isTaczLoaded()) {

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.empowered_reload", "empowered_reload",
                    new Object[]{"reload_speed", 0.40001f, "radiation", 0.60001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.wildfire", "wildfire",
                    new Object[]{"magazine_size", 0.20001f, "fire", 0.60001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.tainted_magazine", "tainted_magazine",
                    new Object[]{"magazine_size", 0.66001f, "reload_speed", -0.33001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.stabilizer", "stabilizer",
                    new Object[]{"recoil_reduction", 0.60001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lethal_precision", "lethal_precision",
                    new Object[]{"recoil_reduction", 0.90001f, "remoteCriticalStrikeProbability", 0.75001f, "firing_rate", -0.30001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.bullet_storm", "bullet_storm",
                    new Object[]{"multishot", 0.60001f, "magazine_size", 0.40001f, "reload_speed", -0.30001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.overloaded_magazine", "overloaded_magazine",
                    new Object[]{"magazine_size", 0.90001f, "firing_rate", 0.45001f, "reload_speed", -0.60001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.rapid_reload", "rapid_reload",
                    new Object[]{"reload_speed", 0.60001f, "firing_rate", 0.30001f, "magazine_size", -0.30001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.cryo_chain", "cryo_chain",
                    new Object[]{"magazine_size", 0.20001f, "ice", 0.60001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.scorched_ammo", "scorched_ammo",
                    new Object[]{"reload_speed", 0.20001f, "fire", 0.60001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.toxin_injection", "toxin_injection",
                    new Object[]{"magazine_size", 0.20001f, "poison", 0.60001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.thunder_barrage", "thunder_barrage",
                    new Object[]{"multishot", 0.30001f, "electricity", 0.60001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.viral_payload", "viral_payload",
                    new Object[]{"projectile_speed", 0.40001f, "virus", 0.45001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.magnetic_pulse", "magnetic_pulse",
                    new Object[]{"recoil_reduction", 0.40001f, "magnetic", 0.45001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.corrosive_payload", "corrosive_payload",
                    new Object[]{"projectile_speed", 0.40001f, "corrosion", 0.45001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.explosive_round", "explosive_round",
                    new Object[]{"projectile_speed", 0.20001f, "explosion", 0.45001f, "bursting_radius", 0.10001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.radiation_penetration", "radiation_penetration",
                    new Object[]{"projectile_speed", 0.20001f, "radiation", 0.45001f, "puncture", 0.30001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.electromagnetic_overload", "electromagnetic_overload",
                    new Object[]{"reload_speed", 0.20001f, "magnetic", 0.45001f, "triggerChance", 0.20001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.gas_dispersal", "gas_dispersal",
                    new Object[]{"magazine_size", 0.20001f, "gas", 0.45001f, "triggerTime", 0.30001f});

            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.chambered_prime", "chambered",
                    new Object[]{"first_bullet_damage", 10.00001f});

            // ===== 第二批TACZ新属性黄金卡（第一批） =====

            // 致命弹道学 —— 300%枪械伤害 + 30%精准度 - 30%射速
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lethal_ballistics", "lethal_ballistics",
                    new Object[]{"gun_damage", 3.00001f, "accuracy", 0.30001f, "firing_rate", -0.30001f});

            // 处刑者之眼 —— 150%爆头伤害 + 90%远程暴伤 - 30%远程伤害
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.executioners_eye", "executioners_eye",
                    new Object[]{"headshot_damage", 1.50001f, "remoteCriticalStrikeMultiplier", 0.90001f, "remoteDamage", -0.30001f});

            // 弹道支配 —— 330%枪械伤害 + 60%爆头伤害 - 30%瞄准速度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ballistic_dominion", "ballistic_dominion",
                    new Object[]{"gun_damage", 3.30001f, "headshot_damage", 0.60001f, "aim_time", -0.30001f});

            // 零点校准 —— 45%精准度 + 45%瞄准速度 + 150%枪械伤害
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.zero_calibration", "zero_calibration",
                    new Object[]{"accuracy", 0.45001f, "aim_time", 0.45001f, "gun_damage", 1.50001f});

            // 毁灭弹幕 —— 250%枪械伤害 + 60%多重射击 - 30%精准度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.annihilation_barrage", "annihilation_barrage",
                    new Object[]{"gun_damage", 2.50001f, "multishot", 0.60001f, "accuracy", -0.30001f});

            // 狙击精要 —— 120%爆头伤害 + 45%精准度 - 45%射速
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.sniper_doctrine", "sniper_doctrine",
                    new Object[]{"headshot_damage", 1.20001f, "accuracy", 0.45001f, "firing_rate", -0.45001f});

            // 闪电瞄准 —— 60%瞄准速度 + 200%枪械伤害 - 30%后坐力降低
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lightning_aim", "lightning_aim",
                    new Object[]{"aim_time", 0.60001f, "gun_damage", 2.00001f, "recoil_reduction", -0.30001f});

            // ===== 第二批TACZ新属性黄金卡（第二批） =====

            // 处刑者 —— 120%爆头倍率 + 60%远程暴伤（处刑者Prime普通版）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.executioner", "executioner",
                    new Object[]{"headshot_damage", 1.20001f, "remoteCriticalStrikeMultiplier", 0.60001f},
                    "remote_crit_mult");

            // 弹道大师学 —— 250%枪械伤害 + 90%远程暴击几率 - 45%射速
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.master_ballistics", "master_ballistics",
                    new Object[]{"gun_damage", 2.50001f, "remoteCriticalStrikeProbability", 0.90001f, "firing_rate", -0.45001f});

            // 一击必杀 —— 150%爆头倍率 + 45%精准度 + 120%枪械伤害 - 45%射速
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.one_shot_kill", "one_shot_kill",
                    new Object[]{"headshot_damage", 1.50001f, "accuracy", 0.45001f, "gun_damage", 1.20001f, "firing_rate", -0.45001f});

            // 死亡凝视 —— 120%爆头倍率 + 180%枪械伤害 + 30%瞄准速度 - 30%远程伤害
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.death_stare", "death_stare",
                    new Object[]{"headshot_damage", 1.20001f, "gun_damage", 1.80001f, "aim_time", 0.30001f, "remoteDamage", -0.30001f});

            // 迅捷毁灭 —— 55%瞄准速度 + 200%枪械伤害 + 30%多重射击 - 30%精准度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.swift_annihilation", "swift_annihilation",
                    new Object[]{"aim_time", 0.55001f, "gun_damage", 2.00001f, "multishot", 0.30001f, "accuracy", -0.30001f});

            // ===== 新增 TACZ 专属词条 · 复合金卡 =====

            // 幻影弹 —— 真实伤害15% + 枪械伤害100%（枪伤抬高真伤基数）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.phantom_round", "phantom_round",
                    new Object[]{"true_bullet", 0.15001f, "gun_damage", 1.00001f});

            // 影袭 —— 真实伤害15% + 爆头伤害60% + 瞄准速度30%（狙击真伤）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.shadow_strike", "shadow_strike",
                    new Object[]{"true_bullet", 0.15001f, "headshot_damage", 0.60001f, "aim_time", 0.30001f});

            // 穿透处决 —— 真实伤害15% + 收集者阈值2%（真伤无视减伤压到处决线下）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.penetrating_execution", "penetrating_execution",
                    new Object[]{"true_bullet", 0.15001f, "execute_threshold", 0.02001f});

            // 血色掠夺 —— 枪械战利品掉落35% + 多重射击30%
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.bloody_plunder", "bloody_plunder",
                    new Object[]{"gun_loot_drop", 0.35001f, "multishot", 0.30001f});

            // 战利补给 —— 枪械战利品掉落30% + 弹夹容量30% + 装填速度20%（刷材料续航）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.war_supply", "war_supply",
                    new Object[]{"gun_loot_drop", 0.30001f, "magazine_size", 0.30001f, "reload_speed", 0.20001f});

            // 净化弹幕 —— 净化驱散10% + 精准度20% + 多重射击30%（泼水抹增益）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.purge_barrage", "purge_barrage",
                    new Object[]{"purge_buff", 0.10001f, "accuracy", 0.20001f, "multishot", 0.30001f});

            // ===== 第四批新增 TACZ 专属词条 · 复合金卡 =====

            // 收集者 —— 处决阈值2.5% + 枪械战利品掉落倍率35%（枪杀低血怪并爆量掉落）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.collector", "collector",
                    new Object[]{"execute_threshold", 0.02501f, "gun_loot_drop", 0.35001f});

            // 穿魂弹 —— 真实伤害15% + 净化驱散10%（先抹增益再补无视护甲真伤）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.spirit_piercer", "spirit_piercer",
                    new Object[]{"true_bullet", 0.15001f, "purge_buff", 0.10001f});

            // 湮灭连射 —— 真实伤害10% + 射速30% + 多重射击30%（每发都触发真伤的弹幕流）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.annihilation_volley", "annihilation_volley",
                    new Object[]{"true_bullet", 0.10001f, "firing_rate", 0.30001f, "multishot", 0.30001f});

            // 断罪者 —— 真实伤害15% + 致命斩首0.01% + 爆头伤害60%（爆头真伤 + 随机秒杀）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.condemner", "condemner",
                    new Object[]{"true_bullet", 0.15001f, "execute_chance", 0.0001f, "headshot_damage", 0.60001f});

            // 尸潮收割 —— 枪械战利品掉落30% + 致命斩首0.01% + 射速30%（高频斩首刷材料）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.carrion_swarm", "carrion_swarm",
                    new Object[]{"gun_loot_drop", 0.30001f, "execute_chance", 0.0001f, "firing_rate", 0.30001f});

            // 真理之触 —— 真实伤害20% + 精准度30%（纯真伤专精，无视护甲的稳定输出）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.truths_touch", "truths_touch",
                    new Object[]{"true_bullet", 0.20001f, "accuracy", 0.30001f});

            // ===== 第五批新增 TACZ 专属 · 复合金卡（真实子弹 × 老词条交叉）=====

            // 穿甲真弹 —— 真实子弹15% + 穿刺90% + 枪械伤害100%（真伤+穿刺双无视护甲，枪伤抬基数）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ap_true_round", "ap_true_round",
                    new Object[]{"true_bullet", 0.15001f, "puncture", 0.90001f, "gun_damage", 1.00001f});

            // 影刃真伤 —— 真实子弹15% + 切割60% + 爆头伤害60%（切割+真伤双无视护甲，爆头放大）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.phantom_edge", "phantom_edge",
                    new Object[]{"true_bullet", 0.15001f, "slash", 0.60001f, "headshot_damage", 0.60001f});

            // 静默处决者 —— 真实子弹15% + 秒杀概率0.01% + 精准度45%（精准真伤 + 随机秒杀）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.silent_executioner", "silent_executioner",
                    new Object[]{"true_bullet", 0.15001f, "execute_chance", 0.0001f, "accuracy", 0.45001f});

            // 精准搜刮 —— 枪械战利品掉落倍率35% + 精准度30% + 爆头伤害60%（精准爆头刷材料）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.precision_salvage", "precision_salvage",
                    new Object[]{"gun_loot_drop", 0.35001f, "accuracy", 0.30001f, "headshot_damage", 0.60001f});

            // 真理弹幕 —— 真实子弹10% + 枪械伤害150% + 多重射击30%（枪伤抬真伤基数的弹幕流）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.truth_barrage", "truth_barrage",
                    new Object[]{"true_bullet", 0.10001f, "gun_damage", 1.50001f, "multishot", 0.30001f});
        }
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, Rarity.RARE);

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
            CustomModuleManager.getInstance().addCustomItemModulesToRandomList(availableModules, Rarity.RARE);

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
        return Rarity.RARE;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
