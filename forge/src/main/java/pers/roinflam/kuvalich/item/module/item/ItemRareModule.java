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
     * 注册所有模组到创造标签页
     * Register all modules to creative tab
     *
     * @param output 创造标签页输出 / creative tab output
     */
    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        List<ItemStack> items = new ArrayList<>();

        // 随机模组始终显示
        items.add(getRandomModule());

        // ========== 原有MOD ==========

        // 分裂膛室
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.split_chamber", "split_chamber",
                new Object[]{"multishot", 0.90001f},
                "multishot");

        // 要害感知
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.vital_sense", "vital_sense",
                new Object[]{"remoteCriticalStrikeMultiplier", 1.2001f},
                "remote_crit_mult");

        // 腐蚀打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.spoiled_strike", "spoiled_strike",
                new Object[]{"meleeDamage", 1.00001f, "attackSpeed", -0.20001f});

        // 重口径
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.heavy_caliber", "heavy_caliber",
                new Object[]{"remoteDamage", 0.90001f, "remoteCriticalStrikeMultiplier", 0.90001f, "firing_rate", -0.60001f},
                "remote_crit_mult", "firing_rate");

        // 火焰风暴
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.firestorm", "firestorm",
                new Object[]{"bursting_radius", 0.60001f});

        // 暴击延迟
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.critical_delay", "critical_delay",
                new Object[]{"remoteCriticalStrikeProbability", 2.00001f, "firing_rate", -0.20001f},
                "remote_crit_chance", "firing_rate");

        // 邪恶加速
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.vile_acceleration", "vile_acceleration",
                new Object[]{"firing_rate", 0.90001f, "remoteDamage", -0.15001f});

        // 空尖弹
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.hollow_point", "hollow_point",
                new Object[]{"remoteCriticalStrikeProbability", 1.50001f, "remoteDamage", -0.15001f},
                "remote_crit_chance");

        // 榔头射击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.hammer_shot", "hammer_shot",
                new Object[]{"remoteCriticalStrikeMultiplier", 0.60001f, "triggerChance", 0.60001f},
                "remote_crit_mult");

        // 蔓延靶心
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.creeping_bullseye", "creeping_bullseye",
                new Object[]{"remoteCriticalStrikeProbability", 1.87501f, "firing_rate", -0.36001f},
                "remote_crit_chance", "firing_rate");

        // 武士刀能量
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.katana_energy", "katana_energy",
                new Object[]{"attackRange", 1.65001f, "meleeDamage", 0.60001f},
                "attack_range");

        // 反射线圈
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.reflex_coil", "reflex_coil",
                new Object[]{"attackSpeed", 0.60001f, "meleeCriticalStrikeProbability", 0.60001f},
                "melee_crit_chance");

        // 元素打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.elemental_strike", "elemental_strike",
                new Object[]{"fire", 0.60001f, "ice", 0.60001f, "poison", 0.60001f, "electricity", 0.60001f});

        // ========== 新增MOD（黄金级别）==========

        // 瘟疫使者
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.plague_herald", "plague_herald",
                new Object[]{"gas", 0.90001f, "triggerTime", 0.75001f});

        // 核子风暴
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.nuclear_storm", "nuclear_storm",
                new Object[]{"radiation", 0.90001f, "multishot", 0.60001f},
                "multishot");

        // 磁暴领主
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.magnetic_overlord", "magnetic_overlord",
                new Object[]{"magnetic", 0.90001f, "meleeCriticalStrikeProbability", 0.75001f},
                "melee_crit_chance");

        // 剥皮者
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.corrosion_king", "corrosion_king",
                new Object[]{"corrosion", 1.20001f, "slash", 0.75001f});

        // 末日箭矢
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.doomsday_arrow", "doomsday_arrow",
                new Object[]{"explosion", 0.90001f, "arrowDamage", 1.20001f});

        // 病入膏肓
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.viral_tyrant", "viral_tyrant",
                new Object[]{"virus", 1.20001f, "puncture", 0.75001f});

        // 熔毁协议
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.meltdown_protocol", "meltdown_protocol",
                new Object[]{"radiation", 0.90001f, "bursting_radius", 0.10001f, "triggerChance", 0.30001f});

        // 0号实验
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.biohazard", "biohazard",
                new Object[]{"gas", 0.90001f, "triggerChance", 0.30001f, "triggerTime", 0.40001f});

        // 电磁风暴
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.em_storm", "em_storm",
                new Object[]{"magnetic", 0.90001f, "remoteCriticalStrikeMultiplier", 0.90001f, "firing_rate", -0.30001f},
                "remote_crit_mult", "firing_rate");

        // 腐蚀熔蚀
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.corrosive_erosion", "corrosive_erosion",
                new Object[]{"corrosion", 0.90001f, "puncture", 0.90001f, "attackSpeed", -0.30001f});

        // 燃尽天际
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.scorched_earth", "scorched_earth",
                new Object[]{"explosion", 0.90001f, "multishot", 0.75001f, "remoteDamage", -0.25001f});

        // 病毒切
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.pandemic_outbreak", "pandemic_outbreak",
                new Object[]{"virus", 0.90001f, "slash", 0.60001f, "meleeCriticalStrikeProbability", 0.60001f},
                "melee_crit_chance");

        // 狂战士誓约
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.berserkers_oath", "berserkers_oath",
                new Object[]{"dashMeleeCriticalStrikeProbability", 3.00001f, "dashAttackRange", 5.00001f, "meleeDamage", -0.90001f},
                "dash_crit_chance", "dash_range");

        // 掠食者印记
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.predators_mark", "predators_mark",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.65001f, "puncture", 0.90001f, "triggerTime", -0.40001f},
                "dash_crit_chance");

        // 死神镰刀
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.reapers_scythe", "reapers_scythe",
                new Object[]{"multishot", 0.60001f, "remoteCriticalStrikeProbability", 0.80001f},
                "multishot", "remote_crit_chance");

        // 猎人法则
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.hunters_code", "hunters_code",
                new Object[]{"remoteCriticalStrikeMultiplier", 0.60001f, "slash", 0.90001f},
                "remote_crit_mult");

        // 角斗士荣耀
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.gladiators_glory", "gladiators_glory",
                new Object[]{"meleeCriticalStrikeMultiplier", 2.00001f, "meleeCriticalStrikeProbability", -0.45001f},
                "melee_crit_mult", "melee_crit_chance");

        // 钢铁誓言
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.iron_oath", "iron_oath",
                new Object[]{"baseDamageWhenNotCriticalStrike", 2.40001f, "puncture", 0.90001f, "meleeCriticalStrikeProbability", -0.45001f});

        // 暴力终末
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.ultimate_violence", "ultimate_violence",
                new Object[]{"baseDamageWhenNotCriticalStrike", 2.40001f, "impact", 0.90001f, "remoteCriticalStrikeProbability", -0.45001f});

        // 灾厄降临
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.catastrophe", "catastrophe",
                new Object[]{"bursting_radius", 0.15001f, "triggerChance", 0.60001f, "triggerTime", 0.60001f});

        // 奥术洪流
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.arcane_torrent", "arcane_torrent",
                new Object[]{"magicDamage", 2.40001f, "multishot", 0.75001f, "firing_rate", -0.35001f},
                "multishot", "firing_rate");

        // 秘法终末
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.mystic_apocalypse", "mystic_apocalypse",
                new Object[]{"arrowDamage", 2.40001f, "magicDamage", 1.35001f, "firing_rate", -0.45001f},
                "firing_rate");

        // 双刃传奇
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.dual_legend", "dual_legend",
                new Object[]{"meleeDamage", 2.20001f, "remoteDamage", 2.20001f, "attackSpeed", -0.25001f, "firing_rate", -0.25001f});

        // 双重灾难
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.dual_disaster", "dual_disaster",
                new Object[]{"meleeCriticalStrikeProbability", 2.00001f, "remoteCriticalStrikeProbability", 2.00001f, "meleeDamage", -0.35001f, "remoteDamage", -0.35001f});

        // 秘法主宰
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.arcane_domination", "arcane_domination",
                new Object[]{"magicDamage", 2.40001f, "triggerChance", 0.90001f, "meleeDamage", -0.45001f, "remoteDamage", -0.45001f});

        // 血之狂舞
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.blood_frenzy", "blood_frenzy",
                new Object[]{"meleeCriticalStrikeProbability", 2.40001f, "attackSpeed", 0.75001f, "meleeCriticalStrikeMultiplier", -0.60001f},
                "melee_crit_chance", "melee_crit_mult");

        // 裂口
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.crying_wounds", "crying_wounds",
                new Object[]{"slash", 1.65001f, "triggerChance", 1.35001f, "meleeCriticalStrikeProbability", -0.50001f},
                "melee_crit_chance");

        // 神射传说
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.legendary_shot", "legendary_shot",
                new Object[]{"arrowDamage", 2.40001f, "remoteCriticalStrikeProbability", 1.80001f, "firing_rate", -0.60001f},
                "remote_crit_chance", "firing_rate");

        // 弹道大师
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.ballistic_master", "ballistic_master",
                new Object[]{"projectileDamage", 2.40001f, "remoteCriticalStrikeMultiplier", 1.80001f, "firing_rate", -0.60001f},
                "remote_crit_mult", "firing_rate");

        // 异况超量
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.condition_outbreak", "condition_outbreak",
                new Object[]{"killStackBaseDamage", 0.08f});

        // 屠戮盛宴
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.slaughter_feast", "slaughter_feast",
                new Object[]{"bane_of_undefined", 0.25001f, "bane_of_undead", 0.25001f, "bane_of_arthropod", 0.25001f, "bane_of_illager", 0.25001f});

        // 连锁灾难
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.chain_catastrophe", "chain_catastrophe",
                new Object[]{"bursting_radius", 0.44001f, "triggerChance", 0.75001f, "multishot", -0.75001f});

        // 辐射君主
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.radiation_lord", "radiation_lord",
                new Object[]{"radiation", 1.20001f, "remoteCriticalStrikeMultiplier", 0.90001f},
                "remote_crit_mult");

        // 腐蚀狂潮
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.corrosion_tide", "corrosion_tide",
                new Object[]{"corrosion", 0.60001f, "meleeDamage", 0.60001f, "attackSpeed", 0.30001f});

        // 毒气扩散
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.toxic_cloud", "toxic_cloud",
                new Object[]{"gas", 1.20001f, "bursting_radius", 0.15001f});

        // 磁场崩溃
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.magnetic_collapse", "magnetic_collapse",
                new Object[]{"magnetic", 1.20001f, "triggerTime", 0.90001f});

        // 末日分裂
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.apocalypse_split", "apocalypse_split",
                new Object[]{"explosion", 0.90001f, "arrowDamage", 1.35001f, "multishot", 0.30001f},
                "multishot");

        // 病毒轨迹
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.viral_vector", "viral_vector",
                new Object[]{"virus", 1.20001f, "projectileDamage", 1.20001f});

        // 近战狂怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.melee_fury", "melee_fury",
                new Object[]{"meleeDamage", 2.40001f, "attackSpeed", 0.90001f, "meleeCriticalStrikeProbability", -0.60001f});

        // 远程狂怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.ranged_fury", "ranged_fury",
                new Object[]{"remoteDamage", 2.40001f, "firing_rate", 0.90001f, "remoteCriticalStrikeProbability", -0.60001f});

        // 掠食双修
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.predatory_hybrid", "predatory_hybrid",
                new Object[]{"remoteDamage", 1.05001f, "dashMeleeCriticalStrikeProbability", 1.20001f, "dashAttackRange", 3.00001f},
                "dash_crit_chance", "dash_range");

        // 狂轰滥炸
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.bombardment", "bombardment",
                new Object[]{"multishot", 1.20001f, "dashMeleeCriticalStrikeProbability", 1.20001f, "firing_rate", -0.25001f},
                "multishot", "dash_crit_chance", "firing_rate");

        // 元素融合
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.elemental_fusion", "elemental_fusion",
                new Object[]{"fire", 0.30001f, "poison", 0.30001f, "ice", 0.30001f, "electricity", 0.30001f});

        // 虐杀原形
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_RARE_MODULE.get(), items, itemStackList,
                "kuvaweapon.item_module.physical_triad", "physical_triad",
                new Object[]{"slash", 0.90001f, "puncture", 0.90001f, "impact", 0.90001f});

        // ========== 自定义模组 / Custom Modules ==========
        CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, Rarity.RARE);

        // 将所有物品添加到创造标签页
        items.forEach(output::accept);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide() && ItemModuleBase.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
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