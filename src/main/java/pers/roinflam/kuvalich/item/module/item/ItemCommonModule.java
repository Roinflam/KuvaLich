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
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemCommonModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemCommonModule(String name) {
        super(name);
    }

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
            ItemStack module = itemStackList.get(RandomUtil.getInt(0, itemStackList.size() - 1));

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
            ItemStack itemStack = getRandomModule();
            items.add(itemStack);

            // ========== 原有MOD ==========

            // kuvaweapon.item_module.pressure_point (压力点)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.pressure_point");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.2001f);
            ItemModuleBase.setType(itemStack, "pressure_point");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.reach (攻击范围)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.reach");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 1.1001f);
            ItemModuleBase.setType(itemStack, "reach");
            ItemModuleBase.setConflictTags(itemStack, "attack_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.true_steel (真钢)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.true_steel");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 1.2001f);
            ItemModuleBase.setType(itemStack, "true_steel");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.organ_shatter (器官粉碎)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.organ_shatter");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", 0.9001f);
            ItemModuleBase.setType(itemStack, "organ_shatter");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.point_strike (精准打击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.point_strike");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 0.9001f);
            ItemModuleBase.setType(itemStack, "point_strike");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.continuous_misery (持续痛苦)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.continuous_misery");
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 1.0f);
            ItemModuleBase.setType(itemStack, "continuous_misery");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.sawtooth_clip (锯齿弹夹)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.sawtooth_clip");
            ItemModuleBase.addAttributes(itemStack, "slash", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 0.30001f);
            ItemModuleBase.setType(itemStack, "sawtooth_clip");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.argon_crystal_shoot (氩晶体射击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.argon_crystal_shoot");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 0.45001f);
            ItemModuleBase.setType(itemStack, "argon_crystal_shoot");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.argon_crystal_wrath (氩晶体愤怒)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.argon_crystal_wrath");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.45001f);
            ItemModuleBase.setType(itemStack, "argon_crystal_wrath");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.elemental_argon_crystal_wrath (元素氩晶体愤怒)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.elemental_argon_crystal_wrath");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.9001f);
            ItemModuleBase.setType(itemStack, "elemental_argon_crystal_wrath");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.chainsaw_from_hell (地狱电锯)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.chainsaw_from_hell");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", -0.3001f);
            ItemModuleBase.setType(itemStack, "chainsaw_from_hell");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance", "melee_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.tear (泪痕)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.tear");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", -0.60001f);
            ItemModuleBase.setType(itemStack, "tear");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.disaster_shoot (灾难射击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.disaster_shoot");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.90001f);
            ItemModuleBase.setType(itemStack, "disaster_shoot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.malicious (恶意)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.malicious");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -0.6001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", -0.6001f);
            ItemModuleBase.setType(itemStack, "malicious");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.single_point_breakthrough (单点突破)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.single_point_breakthrough");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.6501f);
            ItemModuleBase.addAttributes(itemStack, "attackRange", -0.7501f);
            ItemModuleBase.setType(itemStack, "single_point_breakthrough");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.lesion_blow (病灶打击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.lesion_blow");
            ItemModuleBase.addAttributes(itemStack, "dashTriggerChance", 1.50001f);
            ItemModuleBase.setType(itemStack, "lesion_blow");
            ItemModuleBase.setConflictTags(itemStack, "dash_trigger");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.persuasion (劝说)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.persuasion");
            ItemModuleBase.addAttributes(itemStack, "baseDamageWhenNotCriticalStrike", 1.20001f);
            ItemModuleBase.setType(itemStack, "persuasion");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.touches_of_extension (延伸之触)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.touches_of_extension");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "slash", -1.20001f);
            ItemModuleBase.setType(itemStack, "touches_of_extension");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance", "melee_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.fierce_bow (凶猛之弓)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fierce_bow");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.60001f);
            ItemModuleBase.setType(itemStack, "fierce_bow");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.phantom_arrow (幻影之箭)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.phantom_arrow");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.30001f);
            ItemModuleBase.setType(itemStack, "phantom_arrow");
            ItemModuleBase.setConflictTags(itemStack, "multishot", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.magical_growth (魔法增长)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magical_growth");
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.60001f);
            ItemModuleBase.setType(itemStack, "magical_growth");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.magic_missile (魔法飞弹)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magic_missile");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 0.60001f);
            ItemModuleBase.setType(itemStack, "magic_missile");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.pandora_star (潘多拉之星)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.pandora_star");
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 3.00001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", -1.50001f);
            ItemModuleBase.setType(itemStack, "pandora_star");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // ========== 新增MOD（青铜级别）==========

            // 1. kuvaweapon.item_module.arcane_potential (奥术潜能)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arcane_potential");
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.25001f);
            ItemModuleBase.setType(itemStack, "arcane_potential");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 2. kuvaweapon.item_module.marksmans_gift (神射天赋)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.marksmans_gift");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 1.25001f);
            ItemModuleBase.setType(itemStack, "marksmans_gift");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 3. kuvaweapon.item_module.ballistics_expert (弹道专家)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.ballistics_expert");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.25001f);
            ItemModuleBase.setType(itemStack, "ballistics_expert");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());
        }
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}