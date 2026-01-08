// 文件名：ItemCommonModule.java
// 路径：src/main/java/pers/roinflam/kuvalich/item/module/item/ItemCommonModule.java
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
 * 青铜级武器模组
 * Common (Bronze) tier weapon module
 */
public class ItemCommonModule extends ItemModuleBase {

    /**
     * 静态模组列表，用于随机获取
     * Static module list for random obtaining
     */
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemCommonModule(String name) {
        super(name);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_COMMON_MODULE);
        itemStack.setTranslatableName("kuvaweapon.item_type_random.name");
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        if (!worldIn.isRemote && ItemModuleBase.isRandom(itemstack) && handIn.equals(EnumHand.MAIN_HAND)) {
            // 过滤掉被禁用的模组
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);

            // ========== 添加自定义模组到随机池 / Add custom modules to random pool ==========
            CustomModuleManager.getInstance().addCustomItemModulesToRandomList(availableModules, EnumRarity.COMMON);

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
        return EnumRarity.COMMON;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            // 随机模组始终显示
            items.add(getRandomModule());

            // ========== 原有MOD ==========

            // 压力点
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.pressure_point", "pressure_point",
                    new Object[]{"meleeDamage", 1.2001f});

            // 攻击范围
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.reach", "reach",
                    new Object[]{"attackRange", 1.1001f},
                    "attack_range");

            // 真钢
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.true_steel", "true_steel",
                    new Object[]{"meleeCriticalStrikeProbability", 1.2001f},
                    "melee_crit_chance");

            // 器官粉碎
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.organ_shatter", "organ_shatter",
                    new Object[]{"meleeCriticalStrikeMultiplier", 0.9001f},
                    "melee_crit_mult");

            // 精准打击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.point_strike", "point_strike",
                    new Object[]{"remoteCriticalStrikeProbability", 0.9001f},
                    "remote_crit_chance");

            // 持续痛苦
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.continuous_misery", "continuous_misery",
                    new Object[]{"triggerTime", 1.0f});

            // 锯齿弹夹
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.sawtooth_clip", "sawtooth_clip",
                    new Object[]{"slash", 0.30001f, "remoteDamage", 0.30001f});

            // 氩晶体射击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.argon_crystal_shoot", "argon_crystal_shoot",
                    new Object[]{"projectileDamage", 0.60001f, "remoteCriticalStrikeProbability", 0.45001f},
                    "remote_crit_chance");

            // 氩晶体愤怒
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.argon_crystal_wrath", "argon_crystal_wrath",
                    new Object[]{"projectileDamage", 0.60001f, "remoteCriticalStrikeMultiplier", 0.45001f},
                    "remote_crit_mult");

            // 元素氩晶体愤怒
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.elemental_argon_crystal_wrath", "elemental_argon_crystal_wrath",
                    new Object[]{"meleeDamage", 0.9001f, "triggerChance", 0.9001f, "triggerTime", 0.9001f});

            // 地狱电锯
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.chainsaw_from_hell", "chainsaw_from_hell",
                    new Object[]{"meleeCriticalStrikeProbability", 0.6001f, "meleeCriticalStrikeMultiplier", 0.6001f, "attackSpeed", -0.3001f},
                    "melee_crit_chance", "melee_crit_mult");

            // 泪痕
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.tear", "tear",
                    new Object[]{"meleeCriticalStrikeProbability", 1.65001f, "meleeDamage", -0.60001f},
                    "melee_crit_chance");

            // 灾难射击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.disaster_shoot", "disaster_shoot",
                    new Object[]{"remoteDamage", 1.2001f, "triggerChance", 0.90001f});

            // 恶意
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.malicious", "malicious",
                    new Object[]{"triggerChance", 1.2001f, "meleeCriticalStrikeProbability", -0.6001f, "remoteCriticalStrikeProbability", -0.6001f});

            // 单点突破
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.single_point_breakthrough", "single_point_breakthrough",
                    new Object[]{"meleeDamage", 1.6501f, "attackRange", -0.7501f});

            // 病灶打击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.lesion_blow", "lesion_blow",
                    new Object[]{"dashTriggerChance", 1.50001f},
                    "dash_trigger");

            // 劝说
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.persuasion", "persuasion",
                    new Object[]{"baseDamageWhenNotCriticalStrike", 1.20001f});

            // 延伸之触
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.touches_of_extension", "touches_of_extension",
                    new Object[]{"meleeCriticalStrikeProbability", 0.90001f, "meleeCriticalStrikeMultiplier", 0.60001f, "triggerChance", 0.60001f, "slash", -1.20001f},
                    "melee_crit_chance", "melee_crit_mult");

            // 凶猛之弓
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.fierce_bow", "fierce_bow",
                    new Object[]{"arrowDamage", 1.20001f, "multishot", 0.60001f},
                    "multishot");

            // 幻影之箭
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.phantom_arrow", "phantom_arrow",
                    new Object[]{"arrowDamage", 0.60001f, "multishot", 0.30001f, "firing_rate", 0.30001f},
                    "multishot", "firing_rate");

            // 魔法增长
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.magical_growth", "magical_growth",
                    new Object[]{"magicDamage", 0.90001f, "multishot", 0.60001f},
                    "multishot");

            // 魔法飞弹
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.magic_missile", "magic_missile",
                    new Object[]{"remoteDamage", 0.60001f, "magicDamage", 0.60001f});

            // 潘多拉之星
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.pandora_star", "pandora_star",
                    new Object[]{"triggerTime", 3.00001f, "triggerChance", -1.50001f});

            // ========== 新增MOD（青铜级别）==========

            // 奥术潜能
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.arcane_potential", "arcane_potential",
                    new Object[]{"magicDamage", 1.25001f});

            // 神射天赋
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.marksmans_gift", "marksmans_gift",
                    new Object[]{"arrowDamage", 1.25001f});

            // 弹道专家
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.ballistics_expert", "ballistics_expert",
                    new Object[]{"projectileDamage", 1.25001f});

            // ========== 自定义模组 / Custom Modules ==========
            CustomModuleManager.getInstance().addCustomItemModulesToCreativeTab(items, EnumRarity.COMMON);
        }
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}