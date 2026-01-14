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
 * Prime级战甲模组（1.20.1版本）
 * Prime (Epic) tier warframe module (1.20.1 version)
 */
public class WarframePrimeModule extends WarframeModuleBase {

    public static List<ItemStack> itemStackList = new ArrayList<>();
    private static boolean isInitialized = false;

    public WarframePrimeModule(Properties properties) {
        super(properties);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_PRIME_MODULE.get());
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

        // 生命力Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.vitality_prime", "vitality",
                new Object[]{"health", 1.8001f});

        // 蓄能重划Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.redirection_prime", "redirection",
                new Object[]{"shield", 1.8001f});

        // 钢铁纤维Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.steel_fiber_prime", "steel_fiber",
                new Object[]{"armor", 1.8001f});

        // 活力Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.vigor_prime", "vigor",
                new Object[]{"shield", 0.7501f, "health", 0.7501f});

        // 激励Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.inspire_prime", "inspire",
                new Object[]{"responseRate", 0.9001f});

        // 稳如泰山Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.sure_footed_prime", "sure_footed",
                new Object[]{"knockbackResistance", 1.0001f});

        // 密藏猎人Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.treasure_hunter_prime", "treasure_hunter",
                new Object[]{"itemDropMultiplier", 0.9001f},
                "item_drop_multiplier");

        // 超频Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.overclock_prime", "overclock",
                new Object[]{"sprintSpeed", 0.4501f, "shieldRecoveryRate", 0.9001f, "shield", -0.9001f});

        // 充能护甲Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.charged_armor_prime", "charged_armor",
                new Object[]{"shield", 1.4001f, "shieldRecoveryRate", 1.4001f, "armor", -2.0001f});

        // 宝藏盗贼Prime
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.treasure_thief_prime", "treasure_thief",
                new Object[]{"itemDropMultiplier", 1.2001f, "health", -0.6001f, "shield", -1.2001f},
                "item_drop_multiplier");

        // 执刑官 生命力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_vitality", "vitality",
                new Object[]{"health", 1.0001f, "killStackHealth", 0.08501f});

        // 执刑官 蓄能重划
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_redirection", "redirection",
                new Object[]{"shield", 1.0001f, "killStackShield", 0.04001f, "killStackShieldRecoveryRate", 0.02501f});

        // 执刑官 钢铁纤维
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_steel_fiber", "steel_fiber",
                new Object[]{"armor", 1.0001f, "killStackArmor", 0.08501f});

        // 执刑官 冲刺
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_rush", "rush",
                new Object[]{"sprintSpeed", 0.3001f, "killStackSprintSpeed", 0.02501f});

        // 执刑官 活力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_vigor", "vigor",
                new Object[]{"shield", 0.5001f, "health", 0.5001f, "killStackShield", 0.03251f, "killStackHealth", 0.03251f});

        // 执刑官 快速充能
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_fast_deflection", "fast_deflection",
                new Object[]{"shieldRecoveryRate", 0.9001f, "shieldRecoveryDelay", -0.4501f, "killStackShieldRecoveryRate", 0.02501f, "killStackShieldRecoveryDelay", -0.02001f});

        // 执刑官 火焰防护
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_flame_repellent", "flame_repellent",
                new Object[]{"fireProtection", 0.6001f, "killStackFireProtection", 0.02501f});

        // 执刑官 避雷针
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_lightning_rod", "lightning_rod",
                new Object[]{"electricProtection", 0.6001f, "killStackElectricProtection", 0.02501f});

        // 执刑官 情同手足
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_deep_friendship", "deep_friendship",
                new Object[]{"homologousProtection", 0.3001f, "killStackHomologousProtection", 0.03001f});

        // 执刑官 密藏猎人
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_treasure_hunter", "treasure_hunter",
                new Object[]{"itemDropMultiplier", 0.6001f, "killStackItemDropMultiplier", 0.03501f},
                "item_drop_multiplier");

        // 执刑官 碎岩者之力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_digging_power", "digging_power",
                new Object[]{"diggingSpeed", 0.6001f, "killStackDiggingSpeed", 0.02501f},
                "digging_speed");

        // 执刑官 返老还童
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_PRIME_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.executioner_rejuvenation", "rejuvenation",
                new Object[]{"health", 0.3001f, "shield", 0.3001f, "responseRate", 0.3001f, "shieldRecoveryRate", 0.3001f, "killStackHealth", 0.02001f, "killStackShield", 0.02001f, "killStackResponseRate", 0.02001f, "killStackShieldRecoveryRate", 0.02001f});
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, Rarity.EPIC);
        items.forEach(output::accept);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide() && ItemModuleBase.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            ensureInitialized();
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);
            CustomModuleManager.getInstance().addCustomWarframeModulesToRandomList(availableModules, Rarity.EPIC);

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
        return Rarity.EPIC;
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}