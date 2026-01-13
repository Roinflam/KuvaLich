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
 * 白银级战甲模组（1.20.1版本）
 * Uncommon (Silver) tier warframe module (1.20.1 version)
 */
public class WarframeUncommonModule extends WarframeModuleBase {

    /**
     * 静态模组列表,用于随机获取
     * Static module list for random obtaining
     */
    public static List<ItemStack> itemStackList = new ArrayList<>();

    public WarframeUncommonModule(Properties properties) {
        super(properties);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get());
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

        // 冲刺
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.rush", "rush",
                new Object[]{"sprintSpeed", 0.2001f});

        // 增幅器协议
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.augur_accord", "augur_accord",
                new Object[]{"shield", 0.8001f, "armor", 0.4001f});

        // 快速充能
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.fast_deflection", "fast_deflection",
                new Object[]{"shieldRecoveryRate", 0.9001f, "shieldRecoveryDelay", -0.4501f});

        // 角斗士决心
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.gladiator_resolve", "gladiator_resolve",
                new Object[]{"health", 1.2001f, "armor", 0.9001f, "shield", -0.9001f});

        // 肉甲壳
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.carnis_carapace", "carnis_carapace",
                new Object[]{"health", 1.0001f, "shield", -0.6001f});

        // 石甲壳
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.saxum_carapace", "saxum_carapace",
                new Object[]{"armor", 1.0001f, "shield", -0.6001f});

        // 挖掘之手
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.digging_hand", "digging_hand",
                new Object[]{"reachDistance", 0.3001f},
                "reach_distance");

        // 碎岩者之力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.digging_power", "digging_power",
                new Object[]{"diggingSpeed", 0.6001f},
                "digging_speed");

        // 回春
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.rejuvenator", "rejuvenator",
                new Object[]{"responseRate", 1.2001f, "health", -0.6001f, "shield", -0.9001f});

        // 宝藏盗贼
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.treasure_thief", "treasure_thief",
                new Object[]{"itemDropMultiplier", 0.9001f, "health", -0.6001f, "shield", -0.9001f},
                "item_drop_multiplier");

        // 屹立不倒
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.stand_your_ground", "stand_your_ground",
                new Object[]{"health", 0.9001f, "armor", 0.6001f, "knockbackResistance", 0.4501f, "sprintSpeed", -0.3001f});

        // 雅典娜
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), items, itemStackList,
                "kuvaweapon.warframe_module.athena", "athena",
                new Object[]{"shield", 1.2001f, "shieldRecoveryRate", 0.9001f, "health", -0.9001f, "responseRate", -0.9001f});

        // ========== 自定义模组 / Custom Modules ==========
        CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, Rarity.UNCOMMON);

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
            CustomModuleManager.getInstance().addCustomWarframeModulesToRandomList(availableModules, Rarity.UNCOMMON);

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
        return Rarity.UNCOMMON;
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}