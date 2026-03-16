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

/**
 * 青铜级武器模组（1.20.1版本）
 * Common (Bronze) tier weapon module (1.20.1 version)
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