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

        // ========== TACZ 枪械专属模组（需要 TACZ 才会注册）==========
        if (ItemRivenModule.isTaczLoaded()) {

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

            // ===== 第二批TACZ新属性Prime卡（第一批） =====

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

            // ===== 第二批TACZ新属性镀层卡 =====

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

            // ===== 第二批TACZ新属性Prime升级版 =====

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