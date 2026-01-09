// 文件名：ItemPrimeModule.java
// 路径：src/main/java/pers/roinflam/kuvalich/item/module/item/ItemPrimeModule.java
package pers.roinflam.kuvalich.item.module.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Prime级武器模组
 * Prime (Epic) tier weapon module
 */
public class ItemPrimeModule extends ItemModuleBase {

    /**
     * 静态模组列表，用于随机获取
     * Static module list for random obtaining
     */
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemPrimeModule(String name) {
        super(name);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_PRIME_MODULE);
        itemStack.setTranslatableName("kuvaweapon.item_type_random.name");
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            // 随机模组始终显示
            items.add(getRandomModule());

            // ========== 原有MOD ==========

            // 压力点Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.pressure_point_prime", "pressure_point",
                    new Object[]{"meleeDamage", 1.65001f});

            // 攻击范围Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.reach_prime", "reach",
                    new Object[]{"attackRange", 1.65001f},
                    "attack_range");

            // 狂怒Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.fury_prime", "fury",
                    new Object[]{"attackSpeed", 0.55001f});

            // 镀层分裂膛室
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_chamber", "split_chamber",
                    new Object[]{"multishot", 0.80001f, "killStackMultishot", 0.30001f},
                    "multishot");

            // 镀层真钢
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_steel", "true_steel",
                    new Object[]{"meleeCriticalStrikeProbability", 1.10001f, "killStackMeleeCriticalMultiplier", 0.30001f},
                    "melee_crit_chance");

            // 镀层攻击范围
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_reach", "reach",
                    new Object[]{"attackRange", 0.80001f, "killStackAttackRange", 0.30001f},
                    "attack_range");

            // 镀层狂怒
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_fury", "fury",
                    new Object[]{"attackSpeed", 0.30001f, "killStackAttackSpeed", 0.10001f});

            // 镀层武器资质
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_aptitude", "weapon_aptitude",
                    new Object[]{"triggerChance", 0.60001f, "killStackTriggerChance", 0.30001f});

            // 镀层速度触发
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_speed_trigger", "speed_trigger",
                    new Object[]{"firing_rate", 0.40001f, "killStackFiringRate", 0.20001f},
                    "firing_rate");

            // 镀层火焰风暴
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_firestorm", "firestorm",
                    new Object[]{"bursting_radius", 0.40001f, "killStackBurstingRadius", 0.20001f});

            // ========== 新增Prime MOD ==========

            // 掠食本能Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.predatory_instinct_prime", "predatory_instinct",
                    new Object[]{"dashMeleeCriticalStrikeProbability", 2.00001f, "dashAttackRange", 5.00001f},
                    "dash_crit_chance", "dash_range");

            // 猎杀时刻Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.hunters_moment_prime", "hunters_moment",
                    new Object[]{"dashMeleeCriticalStrikeProbability", 2.00001f, "dashTriggerChance", 2.40001f},
                    "dash_crit_chance", "dash_trigger");

            // 非暴力美学Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.violent_aesthetic_prime", "violent_aesthetic",
                    new Object[]{"baseDamageWhenNotCriticalStrike", 3.00001f, "attackSpeed", 0.40001f});

            // 感染协议Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.infection_protocol_prime", "infection_protocol",
                    new Object[]{"triggerChance", 1.65001f, "triggerTime", 1.35001f});

            // 双重契约Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.dual_covenant_prime", "dual_covenant",
                    new Object[]{"meleeDamage", 1.65001f, "remoteDamage", 1.65001f});

            // 秘法弓术Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.mystic_archery_prime", "mystic_archery",
                    new Object[]{"arrowDamage", 2.55001f, "magicDamage", 1.65001f});

            // 奥能轨迹Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.arcane_ballistics_prime", "arcane_ballistics",
                    new Object[]{"projectileDamage", 2.55001f, "magicDamage", 1.65001f});

            // 死神镰刀Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.reapers_scythe_prime", "reapers_scythe",
                    new Object[]{"multishot", 1.20001f, "remoteCriticalStrikeProbability", 1.60001f},
                    "multishot", "remote_crit_chance");

            // 猎人法则Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.hunters_code_prime", "hunters_code",
                    new Object[]{"remoteCriticalStrikeMultiplier", 1.20001f, "slash", 1.65001f},
                    "remote_crit_mult");

            // 剥皮者Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.corrosion_king_prime", "corrosion_king",
                    new Object[]{"corrosion", 1.65001f, "slash", 1.25001f});

            // 瘟疫使者Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.plague_herald_prime", "plague_herald",
                    new Object[]{"gas", 1.65001f, "triggerTime", 1.35001f});

            // 核子风暴Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.nuclear_storm_prime", "nuclear_storm",
                    new Object[]{"radiation", 1.65001f, "multishot", 0.90001f},
                    "multishot");

            // 磁暴领主Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.magnetic_overlord_prime", "magnetic_overlord",
                    new Object[]{"magnetic", 1.65001f, "remoteCriticalStrikeProbability", 1.35001f},
                    "remote_crit_chance");

            // 灾厄降临Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.catastrophe_prime", "catastrophe",
                    new Object[]{"bursting_radius", 0.33001f, "triggerChance", 1.20001f, "triggerTime", 1.20001f});

            // ========== 新增镀层MOD ==========

            // 镀层掠食本能
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_predatory_instinct", "predatory_instinct",
                    new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashAttackRange", 3.00001f, "killStackAttackRange", 0.50001f},
                    "dash_crit_chance", "dash_range");

            // 镀层猎杀时刻
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_hunters_moment", "hunters_moment",
                    new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashTriggerChance", 1.35001f, "killStackTriggerChance", 0.30001f},
                    "dash_crit_chance", "dash_trigger");

            // 镀层暴力美学
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_violent_aesthetic", "violent_aesthetic",
                    new Object[]{"baseDamageWhenNotCriticalStrike", 2.50001f, "attackSpeed", 0.25001f, "killStackAttackSpeed", 0.10001f});

            // 镀层感染协议
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_infection_protocol", "infection_protocol",
                    new Object[]{"triggerChance", 0.90001f, "triggerTime", 0.75001f, "killStackTriggerChance", 0.30001f});

            // 镀层双重契约
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_dual_covenant", "dual_covenant",
                    new Object[]{"meleeDamage", 1.20001f, "remoteDamage", 1.20001f, "killStackMeleeCriticalMultiplier", 0.50001f});

            // 镀层死神镰刀
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_reapers_scythe", "reapers_scythe",
                    new Object[]{"multishot", 0.60001f, "remoteCriticalStrikeProbability", 0.80001f, "killStackMultishot", 0.30001f},
                    "multishot", "remote_crit_chance");

            // 镀层震荡领域
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_shockwave_domain", "shockwave_domain",
                    new Object[]{"bursting_radius", 0.24001f, "triggerTime", 0.75001f, "killStackBurstingRadius", 0.05001f});

            // 镀层死亡弹幕
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.galvanized_death_barrage", "death_barrage",
                    new Object[]{"multishot", 0.45001f, "triggerChance", 0.75001f, "killStackMultishot", 0.25001f},
                    "multishot");

            // 毁灭损耗 Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.destruction_attrition_prime", "critical_rebuild",
                    new Object[]{"baseDamageWhenNotCriticalStrike", 20.0001f,
                            "meleeCriticalStrikeProbability", 1.4001f, "remoteCriticalStrikeProbability", 1.4001f,
                            "meleeCriticalStrikeMultiplier", -3.0001f, "remoteCriticalStrikeMultiplier", -3.0001f},
                    "critical_rebuild");

            // ========== 自定义模组 / Custom Modules ==========
            CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, EnumRarity.EPIC);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        if (!worldIn.isRemote && ItemModuleBase.isRandom(itemstack) && handIn.equals(EnumHand.MAIN_HAND)) {
            // 过滤掉被禁用的模组
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);

            // ========== 添加自定义模组到随机池 / Add custom modules to random pool ==========
            CustomModuleManager.getInstance().addCustomItemModulesToRandomList(availableModules, EnumRarity.EPIC);

            if (availableModules.isEmpty()) {
                return new ActionResult<ItemStack>(EnumActionResult.FAIL, itemstack);
            }

            ItemStack module = availableModules.get(RandomUtil.getInt(0, availableModules.size() - 1)).copy();

            EntityItem entityItem = new EntityItem(worldIn, playerIn.posX, playerIn.posY, playerIn.posZ, module);
            worldIn.spawnEntity(entityItem);

            playerIn.setHeldItem(handIn, ItemStack.EMPTY);
            return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, itemstack);
        }
        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}