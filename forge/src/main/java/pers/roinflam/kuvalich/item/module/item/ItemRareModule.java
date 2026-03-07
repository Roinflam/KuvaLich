package pers.roinflam.kuvalich.item.module.item;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 黄金级武器模组（1.20.1版本）
 * Rare (Gold) tier weapon module (1.20.1 version)
 */
public class ItemRareModule extends ItemModuleBase {

    /**
     * 静态模组列表,用于随机获取
     * Static module list for random obtaining
     */
    public static List<ItemStack> itemStackList = new ArrayList<>();

    /**
     * 标记是否已初始化（用于服务器端懒加载）
     * Flag to check if initialized (for server-side lazy loading)
     */
    private static boolean isInitialized = false;

    public ItemRareModule(Properties properties) {
        super(properties);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RARE_MODULE.get());
        itemStack.setHoverName(net.minecraft.network.chat.Component.translatable("kuvaweapon.item_type_random.name"));
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    /**
     * 懒加载初始化方法（用于服务器端）
     * Lazy initialization for server-side
     */
    private static synchronized void ensureInitialized() {
        if (!isInitialized) {
            initializeModuleList();
            isInitialized = true;
        }
    }

    /**
     * 初始化模组列表
     * Initialize module list
     */
    private static void initializeModuleList() {
        // 清空列表，防止重复添加
        itemStackList.clear();

        // ========== 原有MOD ==========

        // 分裂膛室
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.split_chamber", "split_chamber",
                new Object[]{"multishot", 0.90001f},
                "multishot");

        // 要害感知
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.vital_sense", "vital_sense",
                new Object[]{"remoteCriticalStrikeMultiplier", 1.2001f},
                "remote_crit_mult");

        // 腐蚀打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.spoiled_strike", "spoiled_strike",
                new Object[]{"meleeDamage", 1.00001f, "attackSpeed", -0.20001f});

        // 重口径
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.heavy_caliber", "heavy_caliber",
                new Object[]{"remoteDamage", 0.90001f, "remoteCriticalStrikeMultiplier", 0.90001f, "firing_rate", -0.60001f},
                "remote_crit_mult", "firing_rate");

        // 火焰风暴
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.firestorm", "firestorm",
                new Object[]{"bursting_radius", 0.60001f});

        // 暴击延迟
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.critical_delay", "critical_delay",
                new Object[]{"remoteCriticalStrikeProbability", 2.00001f, "firing_rate", -0.20001f},
                "remote_crit_chance", "firing_rate");

        // 邪恶加速
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.vile_acceleration", "vile_acceleration",
                new Object[]{"firing_rate", 0.90001f, "remoteDamage", -0.15001f});

        // 空尖弹 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hollow_point", "hollow_point",
                new Object[]{"remoteCriticalStrikeProbability", 1.50001f, "remoteDamage", -0.15001f});

        // 榔头射击 - 移除remote_crit_mult冲突(数值只有60%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hammer_shot", "hammer_shot",
                new Object[]{"remoteCriticalStrikeMultiplier", 0.60001f, "triggerChance", 0.60001f});

        // 蔓延靶心 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.creeping_bullseye", "creeping_bullseye",
                new Object[]{"remoteCriticalStrikeProbability", 1.87501f, "firing_rate", -0.36001f});

        // 武士刀能量 - 移除attack_range冲突(攻击范围165%不算特别高)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.katana_energy", "katana_energy",
                new Object[]{"attackRange", 1.65001f, "meleeDamage", 0.60001f});

        // 反射线圈 - 移除melee_crit_chance冲突(数值只有60%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reflex_coil", "reflex_coil",
                new Object[]{"attackSpeed", 0.60001f, "meleeCriticalStrikeProbability", 0.60001f});

        // 元素打击（火焰/冰冻/毒素/电击 → 组合为腐蚀+爆炸）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.elemental_strike", "elemental_strike",
                new Object[]{"fire", 0.30001f, "ice", 0.30001f, "poison", 0.30001f, "electricity", 0.30001f});

        // ========== 新增MOD（黄金级别）==========

        // 瘟疫使者
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.plague_herald", "plague_herald",
                new Object[]{"gas", 0.90001f, "triggerTime", 0.75001f});

        // 核子风暴 - 元素修正为辐射（平衡调整：与Prime版保持一致）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.nuclear_storm", "nuclear_storm",
                new Object[]{"radiation", 0.90001f, "multishot", 0.60001f});

        // 磁暴领主 - 暴击修正为远程暴击几率（平衡调整：与Prime版保持一致）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magnetic_overlord", "magnetic_overlord",
                new Object[]{"magnetic", 0.90001f, "remoteCriticalStrikeProbability", 0.75001f});

        // 剥皮者
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosion_king", "corrosion_king",
                new Object[]{"corrosion", 1.20001f, "slash", 0.75001f});

        // 末日箭矢
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.doomsday_arrow", "doomsday_arrow",
                new Object[]{"explosion", 0.90001f, "arrowDamage", 1.20001f});

        // 病入膏肓
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.viral_tyrant", "viral_tyrant",
                new Object[]{"virus", 1.20001f, "puncture", 0.75001f});

        // 熔毁协议
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.meltdown_protocol", "meltdown_protocol",
                new Object[]{"radiation", 0.90001f, "bursting_radius", 0.10001f, "triggerChance", 0.30001f});

        // 0号实验
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.biohazard", "biohazard",
                new Object[]{"gas", 0.90001f, "triggerChance", 0.30001f, "triggerTime", 0.40001f});

        // 电磁风暴 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.em_storm", "em_storm",
                new Object[]{"magnetic", 0.90001f, "remoteCriticalStrikeMultiplier", 0.90001f, "firing_rate", -0.30001f});

        // 腐蚀熔蚀
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosive_erosion", "corrosive_erosion",
                new Object[]{"corrosion", 0.90001f, "puncture", 0.90001f, "attackSpeed", -0.30001f});

        // 燃尽天际
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.scorched_earth", "scorched_earth",
                new Object[]{"explosion", 0.90001f, "multishot", 0.75001f, "remoteDamage", -0.25001f});

        // 病毒切 - 移除melee_crit_chance冲突(数值只有60%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.pandemic_outbreak", "pandemic_outbreak",
                new Object[]{"virus", 0.90001f, "slash", 0.60001f, "meleeCriticalStrikeProbability", 0.60001f});

        // 狂战士誓约
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.berserkers_oath", "berserkers_oath",
                new Object[]{"dashMeleeCriticalStrikeProbability", 3.00001f, "dashAttackRange", 5.00001f, "meleeDamage", -0.90001f},
                "dash_crit_chance", "dash_range");

        // 掠食者印记 - 移除dash_crit_chance冲突(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.predators_mark", "predators_mark",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.65001f, "puncture", 0.90001f, "triggerTime", -0.40001f});

        // 死神镰刀 - 移除冲突标签(数值较低)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reapers_scythe", "reapers_scythe",
                new Object[]{"multishot", 0.60001f, "remoteCriticalStrikeProbability", 0.80001f});

        // 猎人法则 - 移除remote_crit_mult冲突(数值只有60%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hunters_code", "hunters_code",
                new Object[]{"remoteCriticalStrikeMultiplier", 0.60001f, "slash", 0.90001f});

        // 角斗士荣耀
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.gladiators_glory", "gladiators_glory",
                new Object[]{"meleeCriticalStrikeMultiplier", 2.00001f, "meleeCriticalStrikeProbability", -0.45001f},
                "melee_crit_mult", "melee_crit_chance");

        // 钢铁誓言
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.iron_oath", "iron_oath",
                new Object[]{"baseDamageWhenNotCriticalStrike", 2.40001f, "puncture", 0.90001f, "meleeCriticalStrikeProbability", -0.45001f});

        // 暴力终末
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.ultimate_violence", "ultimate_violence",
                new Object[]{"baseDamageWhenNotCriticalStrike", 2.40001f, "impact", 0.90001f, "remoteCriticalStrikeProbability", -0.45001f});

        // 灾厄降临
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.catastrophe", "catastrophe",
                new Object[]{"bursting_radius", 0.15001f, "triggerChance", 0.60001f, "triggerTime", 0.60001f});

        // 奥术洪流 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_torrent", "arcane_torrent",
                new Object[]{"magicDamage", 2.40001f, "multishot", 0.75001f, "firing_rate", -0.35001f});

        // 秘法终末 - 移除firing_rate冲突(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.mystic_apocalypse", "mystic_apocalypse",
                new Object[]{"arrowDamage", 2.40001f, "magicDamage", 1.35001f, "firing_rate", -0.45001f});

        // 双刃传奇
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.dual_legend", "dual_legend",
                new Object[]{"meleeDamage", 2.20001f, "remoteDamage", 2.20001f, "attackSpeed", -0.25001f, "firing_rate", -0.25001f});

        // 双重灾难
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.dual_disaster", "dual_disaster",
                new Object[]{"meleeCriticalStrikeProbability", 2.00001f, "remoteCriticalStrikeProbability", 2.00001f, "meleeDamage", -0.35001f, "remoteDamage", -0.35001f});

        // 秘法主宰
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_domination", "arcane_domination",
                new Object[]{"magicDamage", 2.40001f, "triggerChance", 0.90001f, "meleeDamage", -0.45001f, "remoteDamage", -0.45001f});

        // 血之狂舞
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.blood_frenzy", "blood_frenzy",
                new Object[]{"meleeCriticalStrikeProbability", 2.40001f, "attackSpeed", 0.75001f, "meleeCriticalStrikeMultiplier", -0.60001f},
                "melee_crit_chance", "melee_crit_mult");

        // 裂口 - 移除melee_crit_chance冲突(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.crying_wounds", "crying_wounds",
                new Object[]{"slash", 1.65001f, "triggerChance", 1.35001f, "meleeCriticalStrikeProbability", -0.50001f});

        // 神射传说 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.legendary_shot", "legendary_shot",
                new Object[]{"arrowDamage", 2.40001f, "remoteCriticalStrikeProbability", 1.80001f, "firing_rate", -0.60001f});

        // 弹道大师 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.ballistic_master", "ballistic_master",
                new Object[]{"projectileDamage", 2.40001f, "remoteCriticalStrikeMultiplier", 1.80001f, "firing_rate", -0.60001f});

        // 异况超量
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.condition_outbreak", "condition_outbreak",
                new Object[]{"killStackBaseDamage", 0.08f});

        // 屠戮盛宴
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.slaughter_feast", "slaughter_feast",
                new Object[]{"bane_of_undefined", 0.25001f, "bane_of_undead", 0.25001f, "bane_of_arthropod", 0.25001f, "bane_of_illager", 0.25001f});

        // 连锁灾难
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.chain_catastrophe", "chain_catastrophe",
                new Object[]{"bursting_radius", 0.44001f, "triggerChance", 0.75001f, "multishot", -0.75001f});

        // 辐射君主 - 移除remote_crit_mult冲突(数值只有90%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.radiation_lord", "radiation_lord",
                new Object[]{"radiation", 1.20001f, "remoteCriticalStrikeMultiplier", 0.90001f});

        // 腐蚀狂潮
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.corrosion_tide", "corrosion_tide",
                new Object[]{"corrosion", 0.60001f, "meleeDamage", 0.60001f, "attackSpeed", 0.30001f});

        // 毒气扩散
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.toxic_cloud", "toxic_cloud",
                new Object[]{"gas", 1.20001f, "bursting_radius", 0.15001f});

        // 磁场崩溃
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magnetic_collapse", "magnetic_collapse",
                new Object[]{"magnetic", 1.20001f, "triggerTime", 0.90001f});

        // 末日分裂 - 移除multishot冲突(数值只有30%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.apocalypse_split", "apocalypse_split",
                new Object[]{"explosion", 0.90001f, "arrowDamage", 1.35001f, "multishot", 0.30001f});

        // 病毒轨迹
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.viral_vector", "viral_vector",
                new Object[]{"virus", 1.20001f, "projectileDamage", 1.20001f});

        // 近战狂怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.melee_fury", "melee_fury",
                new Object[]{"meleeDamage", 2.40001f, "attackSpeed", 0.90001f, "meleeCriticalStrikeProbability", -0.60001f});

        // 远程狂怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.ranged_fury", "ranged_fury",
                new Object[]{"remoteDamage", 2.40001f, "firing_rate", 0.90001f, "remoteCriticalStrikeProbability", -0.60001f});

        // 掠食双修
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.predatory_hybrid", "predatory_hybrid",
                new Object[]{"remoteDamage", 1.05001f, "dashMeleeCriticalStrikeProbability", 1.20001f, "dashAttackRange", 3.00001f},
                "dash_crit_chance", "dash_range");

        // 狂轰滥炸 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bombardment", "bombardment",
                new Object[]{"multishot", 1.20001f, "dashMeleeCriticalStrikeProbability", 1.20001f, "firing_rate", -0.25001f});

        // 元素融合（电击/毒素/火焰/冰冻 → 组合为辐射+病毒，与元素打击的腐蚀+爆炸不同）
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.elemental_fusion", "elemental_fusion",
                new Object[]{"electricity", 0.30001f, "poison", 0.30001f, "fire", 0.30001f, "ice", 0.30001f});

        // ========== TACZ 枪械专属模组（需要 TACZ 才会注册）==========
        if (ItemRivenModule.isTaczLoaded()) {

            // 赋能装填 —— 40%装填速度 + 60%辐射伤害
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.empowered_reload", "empowered_reload",
                    new Object[]{"reload_speed", 0.40001f, "radiation", 0.60001f});

            // 野火 —— 20%弹夹容量 + 60%火焰伤害
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.wildfire", "wildfire",
                    new Object[]{"magazine_size", 0.20001f, "fire", 0.60001f});

            // 腐败弹夹 —— 66%弹夹容量 - 33%装填速度（移除冲突标签：有负面效果）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.tainted_magazine", "tainted_magazine",
                    new Object[]{"magazine_size", 0.66001f, "reload_speed", -0.33001f});

            // 稳定 —— 60%后坐力降低
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.stabilizer", "stabilizer",
                    new Object[]{"recoil_reduction", 0.60001f});

            // 致命精准 —— 90%后坐力降低 + 75%远程暴击几率 - 30%射速（精准狙击核心，移除冲突标签：有负面效果）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lethal_precision", "lethal_precision",
                    new Object[]{"recoil_reduction", 0.90001f, "remoteCriticalStrikeProbability", 0.75001f, "firing_rate", -0.30001f});

            // 弹雨风暴 —— 60%多重射击 + 40%弹夹容量 - 30%装填速度（弹幕流核心，移除冲突标签：有负面效果）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.bullet_storm", "bullet_storm",
                    new Object[]{"multishot", 0.60001f, "magazine_size", 0.40001f, "reload_speed", -0.30001f});

            // 过载弹匣 —— 90%弹夹容量 + 45%射速 - 60%装填速度（极端循环，移除冲突标签：有负面效果）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.overloaded_magazine", "overloaded_magazine",
                    new Object[]{"magazine_size", 0.90001f, "firing_rate", 0.45001f, "reload_speed", -0.60001f});

            // 急速换弹 —— 60%装填速度 + 30%射速 - 30%弹夹容量（快速循环流，移除冲突标签：有负面效果）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.rapid_reload", "rapid_reload",
                    new Object[]{"reload_speed", 0.60001f, "firing_rate", 0.30001f, "magazine_size", -0.30001f});

            // 冰封弹链 —— 20%弹夹容量 + 60%冰冻伤害（弹夹+冰冻，对标野火结构）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.cryo_chain", "cryo_chain",
                    new Object[]{"magazine_size", 0.20001f, "ice", 0.60001f});

            // 焦土弹药 —— 20%装填速度 + 60%火焰伤害（装填+火焰，对标野火结构）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.scorched_ammo", "scorched_ammo",
                    new Object[]{"reload_speed", 0.20001f, "fire", 0.60001f});

            // 毒素注射 —— 20%弹夹容量 + 60%毒素伤害（弹夹+毒素，对标野火结构）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.toxin_injection", "toxin_injection",
                    new Object[]{"magazine_size", 0.20001f, "poison", 0.60001f});

            // 雷暴弹幕 —— 30%多重射击 + 60%电击伤害（多重+电击，副词条为多重基准90%的1/3）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.thunder_barrage", "thunder_barrage",
                    new Object[]{"multishot", 0.30001f, "electricity", 0.60001f});

            // 病毒载荷 —— 40%投射物速度 + 45%病毒伤害（狙击+复合元素）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.viral_payload", "viral_payload",
                    new Object[]{"projectile_speed", 0.40001f, "virus", 0.45001f});

            // 磁力脉冲 —— 40%后坐力降低 + 45%磁力伤害（稳定+复合元素）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.magnetic_pulse", "magnetic_pulse",
                    new Object[]{"recoil_reduction", 0.40001f, "magnetic", 0.45001f});

            // 腐蚀弹头 —— 40%投射物速度 + 45%腐蚀伤害（速度+复合元素）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.corrosive_payload", "corrosive_payload",
                    new Object[]{"projectile_speed", 0.40001f, "corrosion", 0.45001f});

            // 爆裂弹头 —— 20%投射物速度 + 45%爆炸伤害 + 10%爆炸半径（三词条复合元素，副词条压缩）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.explosive_round", "explosive_round",
                    new Object[]{"projectile_speed", 0.20001f, "explosion", 0.45001f, "bursting_radius", 0.10001f});

            // 辐射穿透 —— 20%投射物速度 + 45%辐射伤害 + 30%穿刺伤害（三词条复合元素）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.radiation_penetration", "radiation_penetration",
                    new Object[]{"projectile_speed", 0.20001f, "radiation", 0.45001f, "puncture", 0.30001f});

            // 电磁过载 —— 20%装填速度 + 45%磁力伤害 + 20%触发几率（三词条复合元素）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.electromagnetic_overload", "electromagnetic_overload",
                    new Object[]{"reload_speed", 0.20001f, "magnetic", 0.45001f, "triggerChance", 0.20001f});

            // 毒气弥散 —— 20%弹夹容量 + 45%毒气伤害 + 30%触发时间（三词条复合元素）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.gas_dispersal", "gas_dispersal",
                    new Object[]{"magazine_size", 0.20001f, "gas", 0.45001f, "triggerTime", 0.30001f});

            // 膛室 Prime —— 1000%弹匣第一发子弹伤害（满弹夹射出的第一发直接加基伤）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.chambered_prime", "chambered",
                    new Object[]{"first_bullet_damage", 10.00001f});
        }
    }

    /**
     * 注册所有模组到创造标签页
     * Register all modules to creative tab
     *
     * @param output 创造标签页输出 / creative tab output
     */
    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        // 确保列表已初始化
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();

        // 随机模组始终显示
        items.add(getRandomModule());

        // 将所有已注册的模组添加到创造物品栏
        items.addAll(itemStackList);

        // ========== 自定义模组 / Custom Modules ==========
        CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, Rarity.RARE);

        // 将所有物品添加到创造标签页
        items.forEach(output::accept);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide() && ItemModuleBase.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            // 服务器端懒加载
            ensureInitialized();

            // 过滤掉被禁用的模组
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);

            // ========== 添加自定义模组到随机池 / Add custom modules to random pool ==========
            CustomModuleManager.getInstance().addCustomItemModulesToRandomList(availableModules, Rarity.RARE);

            if (availableModules.isEmpty()) {
                return InteractionResultHolder.fail(itemstack);
            }

            ItemStack module = availableModules.get(RandomUtil.getInt(0, availableModules.size() - 1)).copy();

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