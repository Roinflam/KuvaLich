package pers.roinflam.kuvalich.item.module.warframe;

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
import pers.roinflam.kuvalich.base.item.WarframeModuleBase;
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 青铜级战甲模组（1.20.1版本）
 * Common (Bronze) tier warframe module (1.20.1 version)
 */
public class WarframeCommonModule extends WarframeModuleBase {

    /**
     * 静态模组列表,用于随机获取
     * Static module list for random obtaining
     */
    public static List<ItemStack> itemStackList = new ArrayList<>();

    public WarframeCommonModule(Properties properties) {
        super(properties);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_COMMON_MODULE.get());
        itemStack.setHoverName(net.minecraft.network.chat.Component.translatable("kuvaweapon.warframe_type_random.name"));
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

        // 生命力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.vitality", "vitality",
                new Object[]{"health", 1.0001f});

        // 蓄能重划
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.redirection", "redirection",
                new Object[]{"shield", 1.0001f});

        // 钢铁纤维
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.steel_fiber", "steel_fiber",
                new Object[]{"armor", 1.0001f});

        // 火焰抵抗
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.flame_repellent", "flame_repellent",
                new Object[]{"fireProtection", 0.6001f});

        // 避雷针
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.lightning_rod", "lightning_rod",
                new Object[]{"electricProtection", 0.6001f});

        // 义警活力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.vigilante_vigor", "vigilante_vigor",
                new Object[]{"shieldRecoveryRate", 0.6001f, "shieldRecoveryDelay", -0.3001f});

        // 角斗士圣盾
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.gladiator_aegis", "gladiator_aegis",
                new Object[]{"armor", 0.4001f});

        // 激励
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.inspire", "inspire",
                new Object[]{"responseRate", 0.6001f});

        // 狂野
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.wild", "wild",
                new Object[]{"health", 2.0001f, "shield", -2.0001f});

        // 充能护甲
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.charged_armor", "charged_armor",
                new Object[]{"shield", 1.0001f, "shieldRecoveryRate", 1.0001f, "armor", -2.00001f});

        // 重甲
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.plated_armor", "plated_armor",
                new Object[]{"armor", 0.8001f, "sprintSpeed", -0.2001f});

        // 负重兽
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.beast_of_burden", "beast_of_burden",
                new Object[]{"reachDistance", 0.3001f, "diggingSpeed", 0.6001f, "health", -0.6001f});

        // 白骨
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.bones", "bones",
                new Object[]{"armor", 1.2001f, "health", -0.4001f});

        // 马利万
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.maliwan", "maliwan",
                new Object[]{"itemDropMultiplier", 1.2001f, "reachDistance", -0.3001f, "diggingSpeed", -0.6001f},
                "item_drop_multiplier");

        // 跃动信号
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.mobilize", "mobilize",
                new Object[]{"jumpBoost", 2.0001f});

        // 飞行员
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.aviator", "aviator",
                new Object[]{"fallProtection", 0.4001f});

        // ========== 自定义模组 / Custom Modules ==========
        CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, Rarity.COMMON);

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
            CustomModuleManager.getInstance().addCustomWarframeModulesToRandomList(availableModules, Rarity.COMMON);

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
        return true;
    }
}