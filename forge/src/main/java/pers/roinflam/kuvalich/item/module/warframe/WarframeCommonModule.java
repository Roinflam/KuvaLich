package pers.roinflam.kuvalich.item.module.warframe;

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
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;

/**
 * 青铜级战甲模组（1.20.1版本）
 * Common (Bronze) tier warframe module (1.20.1 version)
 */
public class WarframeCommonModule extends AbstractWarframeModule {

    public static List<ItemStack> itemStackList = new ArrayList<>();
    private static boolean isInitialized = false;

    public WarframeCommonModule(Properties properties) {
        super(properties);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_COMMON_MODULE.get());
        itemStack.setHoverName(net.minecraft.network.chat.Component.translatable("kuvaweapon.warframe_type_random.name"));
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

        // 生命力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.vitality", "vitality",
                new Object[]{"health", 1.0001f});

        // 蓄能重划
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.redirection", "redirection",
                new Object[]{"shield", 1.0001f});

        // 钢铁纤维
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.steel_fiber", "steel_fiber",
                new Object[]{"armor", 1.0001f});

        // 火焰抵抗
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.flame_repellent", "flame_repellent",
                new Object[]{"fireProtection", 0.6001f});

        // 避雷针
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.lightning_rod", "lightning_rod",
                new Object[]{"electricProtection", 0.6001f});

        // 义警活力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.vigilante_vigor", "vigilante_vigor",
                new Object[]{"shieldRecoveryRate", 0.6001f, "shieldRecoveryDelay", -0.3001f});

        // 角斗士圣盾
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.gladiator_aegis", "gladiator_aegis",
                new Object[]{"armor", 0.4001f});

        // 激励
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.inspire", "inspire",
                new Object[]{"responseRate", 0.6001f});

        // 狂野
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.wild", "wild",
                new Object[]{"health", 2.0001f, "shield", -2.0001f});

        // 充能护甲
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.charged_armor", "charged_armor",
                new Object[]{"shield", 1.0001f, "shieldRecoveryRate", 1.0001f, "armor", -2.00001f});

        // 重甲
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.plated_armor", "plated_armor",
                new Object[]{"armor", 0.8001f, "sprintSpeed", -0.2001f});

        // 负重兽
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.beast_of_burden", "beast_of_burden",
                new Object[]{"reachDistance", 0.3001f, "diggingSpeed", 0.6001f, "health", -0.6001f});

        // 白骨
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.bones", "bones",
                new Object[]{"armor", 1.2001f, "health", -0.4001f});

        // 马利万 - 战利品掉落倍率削弱（平衡调整：青铜不应超过Prime品质的密藏猎人90%）
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.maliwan", "maliwan",
                new Object[]{"itemDropMultiplier", 0.6001f, "reachDistance", -0.3001f, "diggingSpeed", -0.6001f},
                "item_drop_multiplier");

        // 跃动信号
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.mobilize", "mobilize",
                new Object[]{"jumpBoost", 2.0001f});

        // 飞行员
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_COMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.aviator", "aviator",
                new Object[]{"fallProtection", 0.4001f});
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, Rarity.COMMON);

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
            CustomModuleManager.getInstance().addCustomWarframeModulesToRandomList(availableModules, Rarity.COMMON);

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
        return Rarity.COMMON;
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}