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
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemUncommonModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemUncommonModule(String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_UNCOMMON_MODULE);
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

            // 切割
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.serration", "serration",
                    new Object[]{"remoteDamage", 1.65001f});

            // 速度触发
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.speed_trigger", "speed_trigger",
                    new Object[]{"firing_rate", 0.6001f},
                    "firing_rate");

            // 熔化冲击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.molten_impact", "molten_impact",
                    new Object[]{"fire", 0.9001f});

            // 电击之触
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.shocking_touch", "shocking_touch",
                    new Object[]{"electricity", 0.9001f});

            // 狂热打击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.fever_strike", "fever_strike",
                    new Object[]{"poison", 0.9001f});

            // 北风
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.north_wind", "north_wind",
                    new Object[]{"ice", 0.9001f});

            // 未定义生物克星
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.bane_of_undefined", "bane_of_undefined",
                    new Object[]{"bane_of_undefined", 0.3001f});

            // 不死生物克星
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.bane_of_undead", "bane_of_undead",
                    new Object[]{"bane_of_undead", 0.3001f});

            // 节肢生物克星
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.bane_of_arthropod", "bane_of_arthropod",
                    new Object[]{"bane_of_arthropod", 0.3001f});

            // 灾厄村民克星
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.bane_of_illager", "bane_of_illager",
                    new Object[]{"bane_of_illager", 0.3001f});

            // 武器资质
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.weapon_aptitude", "weapon_aptitude",
                    new Object[]{"triggerChance", 0.9001f});

            // 狂怒
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.fury", "fury",
                    new Object[]{"attackSpeed", 0.3001f});

            // 回旋砍
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.roundhouse_chop", "roundhouse_chop",
                    new Object[]{"attackRange", 1.20001f, "slash", 0.60001f},
                    "attack_range");

            // 魔法剑士
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.magic_swordsman", "magic_swordsman",
                    new Object[]{"meleeDamage", 1.20001f, "magicDamage", 1.20001f});

            // 三重打击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.triple_whammy", "triple_whammy",
                    new Object[]{"slash", 0.60001f, "puncture", 0.60001f, "impact", 0.60001f});

            // 小口径
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.small_caliber", "small_caliber",
                    new Object[]{"remoteCriticalStrikeProbability", 1.20001f, "remoteCriticalStrikeMultiplier", 1.20001f, "remoteDamage", -0.80001f},
                    "remote_crit_chance", "remote_crit_mult");

            // 流体
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.stream", "stream",
                    new Object[]{"dashAttackRange", 5.00001f},
                    "attack_range");

            // 精准射击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.sure_shot", "sure_shot",
                    new Object[]{"remoteDamage", 1.20001f, "triggerChance", 0.60001f});

            // 火焰之箭
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.arrow_fire", "arrow_fire",
                    new Object[]{"arrowDamage", 0.90001f, "fire", 0.90001f});

            // 冰霜之箭
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.arrow_ice", "arrow_ice",
                    new Object[]{"arrowDamage", 0.90001f, "ice", 0.90001f});

            // 毒素之箭
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.arrow_poison", "arrow_poison",
                    new Object[]{"arrowDamage", 0.90001f, "poison", 0.90001f});

            // 电击之箭
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.arrow_electricity", "arrow_electricity",
                    new Object[]{"arrowDamage", 0.90001f, "electricity", 0.90001f});

            // 低温弹药
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.cryo_rounds", "cryo_rounds",
                    new Object[]{"projectileDamage", 1.20001f, "ice", 0.60001f});

            // 弹簧弹舱
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.spring_loaded_chamber", "spring_loaded_chamber",
                    new Object[]{"projectileDamage", 1.55001f, "firing_rate", 0.75001f, "remoteCriticalStrikeProbability", -0.6001f},
                    "firing_rate");

            // 锯齿弹药
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.serrated_rounds", "serrated_rounds",
                    new Object[]{"projectileDamage", 1.20001f, "slash", 0.9001f, "triggerChance", 0.6001f});

            // 毁灭
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.devastated", "devastated",
                    new Object[]{"attackRange", 1.20001f, "puncture", 0.6001f, "triggerTime", 0.6001f},
                    "attack_range");

            // 裂变射击
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.fission_shot", "fission_shot",
                    new Object[]{"multishot", 1.20001f, "remoteDamage", -0.6001f},
                    "multishot");

            // ========== 新增MOD（白银级别）==========

            // 掠食本能
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.predatory_instinct", "predatory_instinct",
                    new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashAttackRange", 3.00001f},
                    "dash_crit_chance", "dash_range");

            // 猎杀时刻
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.hunters_moment", "hunters_moment",
                    new Object[]{"dashMeleeCriticalStrikeProbability", 1.20001f, "dashTriggerChance", 1.35001f},
                    "dash_crit_chance", "dash_trigger");

            // 非暴力美学
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.violent_aesthetic", "violent_aesthetic",
                    new Object[]{"baseDamageWhenNotCriticalStrike", 1.65001f, "attackSpeed", 0.20001f});

            // 感染协议
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.infection_protocol", "infection_protocol",
                    new Object[]{"triggerChance", 0.90001f, "triggerTime", 0.75001f});

            // 双重契约
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.dual_covenant", "dual_covenant",
                    new Object[]{"meleeDamage", 1.20001f, "remoteDamage", 1.20001f});

            // 秘法弓术
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.mystic_archery", "mystic_archery",
                    new Object[]{"arrowDamage", 1.35001f, "magicDamage", 1.05001f});

            // 奥能轨迹
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.arcane_ballistics", "arcane_ballistics",
                    new Object[]{"projectileDamage", 1.35001f, "magicDamage", 1.05001f});

            // 狂战士之怒
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.berserker_rage", "berserker_rage",
                    new Object[]{"meleeDamage", -0.15001f, "attackSpeed", 0.90001f});

            // 火力压制
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.suppressive_fire", "suppressive_fire",
                    new Object[]{"remoteDamage", 1.35001f, "firing_rate", 0.30001f},
                    "firing_rate");

            // 震荡领域
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.shockwave_domain", "shockwave_domain",
                    new Object[]{"bursting_radius", 0.15001f, "triggerTime", 0.90001f});

            // 死亡弹幕
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.item_module.death_barrage", "death_barrage",
                    new Object[]{"multishot", 0.60001f, "triggerChance", 0.90001f},
                    "multishot");
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        if (!worldIn.isRemote && ItemModuleBase.isRandom(itemstack) && handIn.equals(EnumHand.MAIN_HAND)) {
            // 过滤掉被禁用的模组
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);

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
        return EnumRarity.UNCOMMON;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}