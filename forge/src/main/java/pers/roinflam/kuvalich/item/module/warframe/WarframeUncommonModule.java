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

    public static List<ItemStack> itemStackList = new ArrayList<>();
    private static boolean isInitialized = false;

    public WarframeUncommonModule(Properties properties) {
        super(properties);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get());
        itemStack.setHoverName(net.minecraft.network.chat.Component.translatable("kuvaweapon.warframe_type_random.name"));
        ModuleBase.setRandom(itemStack, true);
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

        // 冲刺
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.rush", "rush",
                new Object[]{"sprintSpeed", 0.3001f});

        // 占卜师契约
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.augur_accord", "augur_accord",
                new Object[]{"shield", 0.7001f});

        // 快速充能
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.fast_deflection", "fast_deflection",
                new Object[]{"shieldRecoveryRate", 0.9001f, "shieldRecoveryDelay", -0.45001f});

        // 角斗士决心
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.gladiator_resolve", "gladiator_resolve",
                new Object[]{"health", 0.4001f});

        // 肉食甲壳
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.carnis_carapace", "carnis_carapace",
                new Object[]{"armor", 1.1001f, "health", 0.4001f});

        // 巨岩甲壳
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.saxum_carapace", "saxum_carapace",
                new Object[]{"armor", 1.1001f, "health", 0.4001f});

        // 挖掘之手
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.digging_hand", "digging_hand",
                new Object[]{"reachDistance", 0.3001f});

        // 挖掘之力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.digging_power", "digging_power",
                new Object[]{"diggingSpeed", 0.6001f},
                "digging_speed");

        // 复苏者
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.rejuvenator", "rejuvenator",
                new Object[]{"responseRate", 1.0001f, "health", -0.5001f});

        // 宝藏盗贼
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.treasure_thief", "treasure_thief",
                new Object[]{"itemDropMultiplier", 0.9001f, "health", -0.6001f, "shield", -1.2001f},
                "item_drop_multiplier");

        // 坚守阵地
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.stand_your_ground", "stand_your_ground",
                new Object[]{"health", 0.9001f, "armor", 0.6001f, "knockbackResistance", 0.4501f, "sprintSpeed", -0.3001f});

        // 雅典娜
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.athena", "redirection",
                new Object[]{"health", 1.2001f, "armor", 0.9001f, "responseRate", 0.6001f, "shieldRecoveryRate", -0.9001f, "shieldRecoveryDelay", -0.9001f});
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, Rarity.UNCOMMON);
        items.forEach(output::accept);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide() && ItemModuleBase.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            ensureInitialized();
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);
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