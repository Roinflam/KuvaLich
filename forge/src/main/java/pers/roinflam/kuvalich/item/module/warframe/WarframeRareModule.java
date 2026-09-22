package pers.roinflam.kuvalich.item.module.warframe;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;

import pers.roinflam.kuvalich.base.item.AbstractWeaponModule;
import pers.roinflam.kuvalich.base.item.AbstractModule;
import pers.roinflam.kuvalich.base.item.AbstractWarframeModule;
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.RandomUtil;

import java.util.ArrayList;
import java.util.List;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.module.level.ModuleLevelHelper;

/**
 * 黄金级战甲模组（1.20.1版本）
 * Rare (Gold) tier warframe module (1.20.1 version)
 */
public class WarframeRareModule extends AbstractWarframeModule {

    public static volatile List<ItemStack> itemStackList = new ArrayList<>();
    private static boolean isInitialized = false;

    public WarframeRareModule(Properties properties) {
        super(properties);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_RARE_MODULE.get());
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

    /**
     * 配置热重载后丢掉已构建的原型池，下一次访问时按新配置重建。
     *
     * <p>模组原型里烧进了 {@code moduleAttributeMultiplier} /
     * {@code keyAttributeMultiplier} 两条配置以及 {@code disabledModuleTypes} 的取舍
     * （见 {@code ModuleRegistryHelper#register}），不重建的话改完配置要重启才生效。</p>
     *
     * <p>只翻标志位、不在这里重建：重建要跑完整张表（几十到上百个 register），
     * 而本方法是被 Forge 的配置监视线程调用的，不该在那上面做重活；
     * 等真正有人取模组时再走 {@code ensureInitialized()}。</p>
     *
     * <p>同时把静态表换成一张新的空表：图鉴（{@code ModuleCodexData})与
     * {@code registerCreativeTabItems} 都是用 {@code itemStackList.isEmpty()} 做守卫的，
     * 换成空表才能让那些守卫重新触发。</p>
     */
    public static synchronized void invalidateModuleList() {
        isInitialized = false;
        itemStackList = new ArrayList<>();
    }

    private static void initializeModuleList() {
        // ⭐ 这个局部变量刻意与静态字段同名：下面所有 register 调用填的都是它，
        //    填完之后在方法末尾一次性换掉静态字段的引用。
        //
        //    不能就地 clear() 再填：配置热重载后本方法会被再次调用，
        //    而图鉴界面与创造栏都是直接读这个 public 静态表的，
        //    就地清空再慢慢填会让它们看到半成品。
        //    换引用则让旧表对读者保持完整，字段已声明 volatile 保证可见性。
        List<ItemStack> itemStackList = new ArrayList<>();

        // 稳如泰山
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.sure_footed", "sure_footed",
                new Object[]{"knockbackResistance", 0.6001f});

        // 活力
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.vigor", "vigor",
                new Object[]{"shield", 0.5001f, "health", 0.5001f});

        // 装甲敏捷
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.armored_agility", "armored_agility",
                new Object[]{"sprintSpeed", 0.15001f, "armor", 0.4001f});

        // 刚毅
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.fortitude", "fortitude",
                new Object[]{"knockbackResistance", 0.4001f, "shieldRecoveryRate", 1.0001f});

        // 密藏猎人
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.treasure_hunter", "treasure_hunter",
                new Object[]{"itemDropMultiplier", 0.6001f},
                "item_drop_multiplier");

        // 矿脉嗅探 —— 金卡单词条标准值
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.vein_sense", "vein_sense",
                new Object[]{"oreDropMultiplier", 0.3001f},
                "ore_drop_multiplier");

        // 深掘者 —— 挖矿倍率 + 挖掘速度，代价是护甲
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.deep_delver", "deep_delver",
                new Object[]{"oreDropMultiplier", 0.4501f, "diggingSpeed", 0.3001f, "armor", -0.6001f},
                "ore_drop_multiplier");

        // 情同手足
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.deep_friendship", "deep_friendship",
                new Object[]{"homologousProtection", 0.3001f});

        // 朋友
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.pal", "pal",
                new Object[]{"reachDistance", 0.201f, "diggingSpeed", 0.401f});

        // 超频 ★ 冲刺速度 0.3001f → 0.2001f
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.overclock", "overclock",
                new Object[]{"sprintSpeed", 0.2001f, "shieldRecoveryRate", 0.6001f, "shield", -0.9001f});

        // 过度延伸 ★ 挖掘距离 0.9001f → 0.6001f
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.overextended", "overextended",
                new Object[]{"reachDistance", 0.6001f, "diggingSpeed", -0.6001f});

        // 心胸狭窄 ★ 挖掘速度 0.9001f → 0.6001f
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.narrow_minded", "overextended",
                new Object[]{"diggingSpeed", 0.6001f, "reachDistance", -0.6001f});

        // 返老还童
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.rejuvenation", "rejuvenation",
                new Object[]{"health", 0.3001f, "shield", 0.3001f, "responseRate", 0.3001f, "shieldRecoveryRate", 0.3001f});

        // 快速恢复
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.rapid_recovery", "rapid_recovery",
                new Object[]{"responseRate", 0.6001f, "shieldRecoveryRate", 0.6001f, "health", -0.3001f, "shield", -0.3001f});

        // 伊甸6号 ★ 挖掘速度 0.6001f → 0.3001f
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.eden_6", "treasure_thief",
                new Object[]{"itemDropMultiplier", 0.9001f, "diggingSpeed", 0.3001f, "reachDistance", 0.3001f, "health", -0.9001f},
                "item_drop_multiplier");

        // 献祭烈焰 ★ 火抗 0.9001f → 0.6001f, 护甲 0.9001f → 0.6001f, 冲刺 0.4501f → 0.2001f
        ModuleRegistryHelper.register(KuvaLichItems.WARFRAME_RARE_MODULE.get(), null, itemStackList,
                "kuvaweapon.warframe_module.sacrificial_blaze", "flame_repellent",
                new Object[]{"fireProtection", 0.6001f, "armor", 0.6001f, "sprintSpeed", 0.2001f, "health", -0.6001f});

        // 填完才发布：读者要么看到旧表，要么看到完整的新表。
        WarframeRareModule.itemStackList = itemStackList;
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, Rarity.RARE);

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
        if (!level.isClientSide() && AbstractWeaponModule.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            ensureInitialized();
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);
            CustomModuleManager.getInstance().addCustomWarframeModulesToRandomList(availableModules, Rarity.RARE);

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
        return Rarity.RARE;
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}
