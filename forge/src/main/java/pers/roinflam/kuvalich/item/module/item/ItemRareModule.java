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
                new Object[]{"attackRange", 1.65001f, "meleeDamage", 0.60001f});

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