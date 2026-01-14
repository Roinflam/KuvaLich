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
 * 青铜级武器模组（1.20.1版本）
 * Common (Bronze) tier weapon module (1.20.1 version)
 */
public class ItemCommonModule extends ItemModuleBase {

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

    public ItemCommonModule(Properties properties) {
        super(properties);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_COMMON_MODULE.get());
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

        // 压力点
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.pressure_point", "pressure_point",
                new Object[]{"meleeDamage", 1.2001f});

        // 攻击范围 - 移除attack_range冲突(数值只有110%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.reach", "reach",
                new Object[]{"attackRange", 1.1001f});

        // 真钢
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.true_steel", "true_steel",
                new Object[]{"meleeCriticalStrikeProbability", 1.2001f},
                "melee_crit_chance");

        // 器官粉碎 - 移除melee_crit_mult冲突(数值只有90%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.organ_shatter", "organ_shatter",
                new Object[]{"meleeCriticalStrikeMultiplier", 0.9001f});

        // 精准打击 - 移除remote_crit_chance冲突(数值只有90%)
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

        // 氩晶体射击 - 移除remote_crit_chance冲突(数值只有45%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.argon_crystal_shoot", "argon_crystal_shoot",
                new Object[]{"projectileDamage", 0.60001f, "remoteCriticalStrikeProbability", 0.45001f});

        // 氩晶体愤怒 - 移除remote_crit_mult冲突(数值只有45%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.argon_crystal_wrath", "argon_crystal_wrath",
                new Object[]{"projectileDamage", 0.60001f, "remoteCriticalStrikeMultiplier", 0.45001f});

        // 元素氩晶体愤怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.elemental_argon_crystal_wrath", "elemental_argon_crystal_wrath",
                new Object[]{"meleeDamage", 0.9001f, "triggerChance", 0.9001f, "triggerTime", 0.9001f});

        // 地狱电锯 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.chainsaw_from_hell", "chainsaw_from_hell",
                new Object[]{"meleeCriticalStrikeProbability", 0.6001f, "meleeCriticalStrikeMultiplier", 0.6001f, "attackSpeed", -0.3001f});

        // 泪痕 - 移除melee_crit_chance冲突(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.tear", "tear",
                new Object[]{"meleeCriticalStrikeProbability", 1.65001f, "meleeDamage", -0.60001f});

        // 灾难射击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.disaster_shoot", "disaster_shoot",
                new Object[]{"remoteDamage", 1.2001f, "triggerChance", 0.90001f});

        // 恶意
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.malicious", "malicious",
                new Object[]{"triggerChance", 1.2001f, "meleeCriticalStrikeProbability", -0.6001f, "remoteCriticalStrikeProbability", -0.6001f});

        // 单点突破
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.single_point_breakthrough", "single_point_breakthrough",
                new Object[]{"meleeDamage", 1.6501f, "attackRange", -0.7501f});

        // 病灶打击 - 移除dash_trigger冲突(数值只有150%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.lesion_blow", "lesion_blow",
                new Object[]{"dashTriggerChance", 1.50001f});

        // 劝说
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.persuasion", "persuasion",
                new Object[]{"baseDamageWhenNotCriticalStrike", 1.20001f});

        // 延伸之触 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.touches_of_extension", "touches_of_extension",
                new Object[]{"meleeCriticalStrikeProbability", 0.90001f, "meleeCriticalStrikeMultiplier", 0.60001f, "triggerChance", 0.60001f, "slash", -1.20001f});

        // 凶猛之弓 - 移除multishot冲突(数值只有60%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fierce_bow", "fierce_bow",
                new Object[]{"arrowDamage", 1.20001f, "multishot", 0.60001f});

        // 幻影之箭 - 移除multishot和firing_rate冲突(数值只有30%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.phantom_arrow", "phantom_arrow",
                new Object[]{"arrowDamage", 0.60001f, "multishot", 0.30001f, "firing_rate", 0.30001f});

        // 魔法增长 - 移除multishot冲突(数值只有60%)
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
        CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, Rarity.COMMON);

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