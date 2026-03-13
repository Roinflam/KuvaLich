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
 * 白银级武器模组（1.20.1版本）
 * Uncommon (Silver) tier weapon module (1.20.1 version)
 */
public class ItemUncommonModule extends AbstractItemModule {

    public static List<ItemStack> itemStackList = new ArrayList<>();
    private static boolean isInitialized = false;

    public ItemUncommonModule(Properties properties) {
        super(properties);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_UNCOMMON_MODULE.get());
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

        // 切割
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.serration", "serration",
                new Object[]{"remoteDamage", 1.65001f});

        // 速度触发 - 移除firing_rate冲突(数值只有60%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.speed_trigger", "speed_trigger",
                new Object[]{"firing_rate", 0.6001f});

        // 熔化冲击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.molten_impact", "molten_impact",
                new Object[]{"fire", 0.9001f});

        // 电击之触
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.shocking_touch", "shocking_touch",
                new Object[]{"electricity", 0.9001f});

        // 狂热打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fever_strike", "fever_strike",
                new Object[]{"poison", 0.9001f});

        // 北风
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.north_wind", "north_wind",
                new Object[]{"ice", 0.9001f});

        // 未定义生物克星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bane_of_undefined", "bane_of_undefined",
                new Object[]{"bane_of_undefined", 0.3001f});

        // 不死生物克星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bane_of_undead", "bane_of_undead",
                new Object[]{"bane_of_undead", 0.3001f});

        // 节肢生物克星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bane_of_arthropod", "bane_of_arthropod",
                new Object[]{"bane_of_arthropod", 0.3001f});

        // 灾厄村民克星
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.bane_of_illager", "bane_of_illager",
                new Object[]{"bane_of_illager", 0.3001f});

        // 武器资质
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.weapon_aptitude", "weapon_aptitude",
                new Object[]{"triggerChance", 0.9001f});

        // 狂怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fury", "fury",
                new Object[]{"attackSpeed", 0.3001f});

        // 回旋砍 - 移除attack_range冲突(数值只有120%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.roundhouse_chop", "roundhouse_chop",
                new Object[]{"attackRange", 1.20001f, "slash", 0.60001f});

        // 魔法剑士
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.magic_swordsman", "magic_swordsman",
                new Object[]{"meleeDamage", 1.20001f, "magicDamage", 1.20001f});

        // 三重打击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.triple_whammy", "triple_whammy",
                new Object[]{"slash", 0.30001f, "puncture", 0.30001f, "impact", 0.30001f});

        // 小口径 - 移除冲突标签(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.small_caliber", "small_caliber",
                new Object[]{"remoteCriticalStrikeProbability", 1.20001f, "remoteCriticalStrikeMultiplier", 1.20001f, "remoteDamage", -0.80001f});

        // 流体 - 移除attack_range冲突(虽然是500%但属于特殊属性dash相关)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.stream", "stream",
                new Object[]{"dashAttackRange", 5.00001f});

        // 精准射击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.sure_shot", "sure_shot",
                new Object[]{"remoteDamage", 1.20001f, "triggerChance", 0.60001f});

        // 火焰之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arrow_fire", "arrow_fire",
                new Object[]{"arrowDamage", 0.90001f, "fire", 0.90001f});

        // 冰霜之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arrow_ice", "arrow_ice",
                new Object[]{"arrowDamage", 0.90001f, "ice", 0.90001f});

        // 毒素之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arrow_poison", "arrow_poison",
                new Object[]{"arrowDamage", 0.90001f, "poison", 0.90001f});

        // 电击之箭
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arrow_electricity", "arrow_electricity",
                new Object[]{"arrowDamage", 0.90001f, "electricity", 0.90001f});

        // 低温弹药
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.cryo_rounds", "cryo_rounds",
                new Object[]{"projectileDamage", 1.20001f, "ice", 0.60001f});

        // 弹簧弹舱 - 移除firing_rate冲突(有负面效果)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.spring_loaded_chamber", "spring_loaded_chamber",
                new Object[]{"projectileDamage", 1.55001f, "firing_rate", 0.75001f, "remoteCriticalStrikeProbability", -0.6001f});

        // 锯齿弹药
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.serrated_rounds", "serrated_rounds",
                new Object[]{"projectileDamage", 1.20001f, "slash", 0.9001f, "triggerChance", 0.6001f});

        // 毁灭 - 移除attack_range冲突(数值只有120%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.devastated", "devastated",
                new Object[]{"attackRange", 1.20001f, "puncture", 0.6001f, "triggerTime", 0.6001f});

        // 裂变射击
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.fission_shot", "fission_shot",
                new Object[]{"multishot", 1.20001f, "remoteDamage", -0.6001f});

        // 掠食本能
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.predatory_instinct", "predatory_instinct",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashAttackRange", 3.00001f},
                "dash_crit_chance", "dash_range");

        // 猎杀时刻
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.hunters_moment", "hunters_moment",
                new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashTriggerChance", 1.35001f},
                "dash_crit_chance", "dash_trigger");

        // 非暴力美学
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.violent_aesthetic", "violent_aesthetic",
                new Object[]{"baseDamageWhenNotCriticalStrike", 1.65001f, "attackSpeed", 0.20001f});

        // 感染协议
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.infection_protocol", "infection_protocol",
                new Object[]{"triggerChance", 0.90001f, "triggerTime", 0.75001f});

        // 双重契约
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.dual_covenant", "dual_covenant",
                new Object[]{"meleeDamage", 1.20001f, "remoteDamage", 1.20001f});

        // 秘法弓术
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.mystic_archery", "mystic_archery",
                new Object[]{"arrowDamage", 1.35001f, "magicDamage", 1.05001f});

        // 奥能轨迹
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.arcane_ballistics", "arcane_ballistics",
                new Object[]{"projectileDamage", 1.35001f, "magicDamage", 1.05001f});

        // 狂战士之怒
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.berserker_rage", "berserker_rage",
                new Object[]{"meleeDamage", -0.15001f, "attackSpeed", 0.90001f});

        // 火力压制 - 移除firing_rate冲突(只是辅助属性)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.suppressive_fire", "suppressive_fire",
                new Object[]{"remoteDamage", 1.35001f, "firing_rate", 0.30001f});

        // 震荡领域
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.shockwave_domain", "shockwave_domain",
                new Object[]{"bursting_radius", 0.15001f, "triggerTime", 0.90001f});

        // 死亡弹幕 - 移除multishot冲突(数值只有60%)
        ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                "kuvaweapon.item_module.death_barrage", "death_barrage",
                new Object[]{"multishot", 0.60001f, "triggerChance", 0.90001f});

        // ========== TACZ 枪械专属模组（需要 TACZ 才会注册）==========
        if (ItemRivenModule.isTaczLoaded()) {

            // 极限速度 —— 60%投射物速度
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.terminal_velocity", "terminal_velocity",
                    new Object[]{"projectile_speed", 0.60001f});

            // 致命弹道 —— 45%投射物速度 + 60%远程暴击几率（狙击入门向）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.lethal_trajectory", "lethal_trajectory",
                    new Object[]{"projectile_speed", 0.45001f, "remoteCriticalStrikeProbability", 0.60001f});

            // 弹幕倾泻 —— 30%射速 + 30%多重射击 - 30%远程伤害（弹幕代价型，移除冲突标签：有负面效果）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.barrage_protocol", "barrage_protocol",
                    new Object[]{"firing_rate", 0.30001f, "multishot", 0.30001f, "remoteDamage", -0.30001f});

            // 战术装填 —— 40%装填速度 + 40%后坐力降低（机动入门向）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.tactical_reload", "tactical_reload",
                    new Object[]{"reload_speed", 0.40001f, "recoil_reduction", 0.40001f});

            // 镇定射击 —— 45%后坐力降低 + 60%触发几率（稳定+触发）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.steady_aim", "steady_aim",
                    new Object[]{"recoil_reduction", 0.45001f, "triggerChance", 0.60001f});

            // 弹道校准 —— 45%投射物速度 + 60%远程暴击伤害（狙击暴伤向）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.ballistic_calibration", "ballistic_calibration",
                    new Object[]{"projectile_speed", 0.45001f, "remoteCriticalStrikeMultiplier", 0.60001f});

            // 超量供弹 —— 60%弹夹容量 - 20%装填速度（弹夹代价型，移除冲突标签：有负面效果）
            ModuleRegistryHelper.register(KuvaLichItems.ITEM_UNCOMMON_MODULE.get(), null, itemStackList,
                    "kuvaweapon.item_module.surplus_ammo", "surplus_ammo",
                    new Object[]{"magazine_size", 0.60001f, "reload_speed", -0.20001f});
        }
    }

    public static void registerCreativeTabItems(CreativeModeTab.Output output) {
        if (itemStackList.isEmpty()) {
            initializeModuleList();
        }

        List<ItemStack> items = new ArrayList<>();
        items.add(getRandomModule());
        items.addAll(itemStackList);
        CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, Rarity.UNCOMMON);
        items.forEach(output::accept);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (!level.isClientSide() && AbstractItemModule.isRandom(itemstack) && hand.equals(InteractionHand.MAIN_HAND)) {
            ensureInitialized();
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);
            CustomModuleManager.getInstance().addCustomItemModulesToRandomList(availableModules, Rarity.UNCOMMON);

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
        return false;
    }
}