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
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemPrimeModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemPrimeModule(String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_PRIME_MODULE);
        itemStack.setTranslatableName("kuvaweapon.item_type_random.name");
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            ItemStack itemStack = getRandomModule();
            items.add(itemStack);

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.pressure_point_prime");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.6501f);
            ItemModuleBase.setType(itemStack, "pressure_point");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.reach_prime");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 3.0001f);
            ItemModuleBase.setType(itemStack, "reach");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.heavy_trauma_prime");
            ItemModuleBase.addAttributes(itemStack, "impact", 1.65001f);
            ItemModuleBase.setType(itemStack, "heavy_trauma");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fever_strike_prime");
            ItemModuleBase.addAttributes(itemStack, "poison", 1.6501f);
            ItemModuleBase.setType(itemStack, "fever_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_undefined_prime");
            ItemModuleBase.addAttributes(itemStack, "bane_of_undefined", 0.5501f);
            ItemModuleBase.setType(itemStack, "bane_of_undefined");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_undead_prime");
            ItemModuleBase.addAttributes(itemStack, "bane_of_undead", 0.5501f);
            ItemModuleBase.setType(itemStack, "bane_of_undead");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_arthropod_prime");
            ItemModuleBase.addAttributes(itemStack, "bane_of_arthropod", 0.5501f);
            ItemModuleBase.setType(itemStack, "bane_of_arthropod");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_illager_prime");
            ItemModuleBase.addAttributes(itemStack, "bane_of_illager", 0.5501f);
            ItemModuleBase.setType(itemStack, "bane_of_illager");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magic_swordsman_prime");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.65001f);
            ItemModuleBase.setType(itemStack, "magic_swordsman");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.eldest_elemental_envoy_prime");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.65001f);
            ItemModuleBase.setType(itemStack, "eldest_elemental_envoy");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.malicious_prime");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 1.6501f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -0.6001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", -0.6001f);
            ItemModuleBase.setType(itemStack, "malicious");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.persuasion_prime");
            ItemModuleBase.addAttributes(itemStack, "baseDamageWhenNotCriticalStrike", 1.65001f);
            ItemModuleBase.setType(itemStack, "persuasion");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.firestorm_prime");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.44001f);
            ItemModuleBase.setType(itemStack, "firestorm");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.cryo_rounds_prime");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "ice", 0.90001f);
            ItemModuleBase.setType(itemStack, "cryo_rounds");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fierce_bow_prime");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.90001f);
            ItemModuleBase.setType(itemStack, "fierce_bow");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fission_shot_prime");
            ItemModuleBase.addAttributes(itemStack, "multishot", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.6001f);
            ItemModuleBase.setType(itemStack, "fission_shot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fury_prime");
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.5501f);
            ItemModuleBase.setType(itemStack, "fury");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());


            // 镀层分裂膛室 Galvanized Chamber
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_chamber");
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.80001f);              // +80%多重射击
            ItemModuleBase.addAttributes(itemStack, "killStackMultishot", 0.30001f);     // 击杀时：+30%多重射击
            ItemModuleBase.setType(itemStack, "galvanized_chamber");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 镀层武器才能 Galvanized Aptitude
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_aptitude");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.80001f);           // +80%触发几率
            ItemModuleBase.addAttributes(itemStack, "killStackBaseDamage", 0.04001f);     // 击杀时：目标身上每一种负面效果+4%基础伤害
            ItemModuleBase.setType(itemStack, "weapon_aptitude");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 镀层斩铁 Galvanized Steel
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_steel");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 1.10001f);      // +110%近战暴击几率
            ItemModuleBase.addAttributes(itemStack, "killStackMeleeCriticalMultiplier", 0.30001f);    // 击杀时：+30%近战暴击伤害
            ItemModuleBase.setType(itemStack, "true_steel");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 镀层剑风 Galvanized Reach
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_reach");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 1.10001f);            // +110%攻击范围
            ItemModuleBase.addAttributes(itemStack, "killStackAttackRange", 0.50001f);   // 击杀时：+50%攻击范围
            ItemModuleBase.setType(itemStack, "reach");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 镀层狂暴 Galvanized Fury
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_fury");
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.30001f);            // +30%攻击速度
            ItemModuleBase.addAttributes(itemStack, "killStackAttackSpeed", 0.08001f);   // 击杀时：+8%攻击速度
            ItemModuleBase.setType(itemStack, "fury");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 镀层烈焰风暴 Galvanized Firestorm
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_firestorm");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.24001f);              // +24%爆炸半径
            ItemModuleBase.addAttributes(itemStack, "killStackBurstingRadius", 0.08001f);      // 击杀时：+8%爆炸半径
            ItemModuleBase.setType(itemStack, "firestorm");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 镀层灵敏扳机 Galvanized Speed Trigger
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_speed_trigger");
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.60001f);              // +60%射速（弓类效果加倍）
            ItemModuleBase.addAttributes(itemStack, "killStackFiringRate", 0.08001f);      // 击杀时：+8%射速（弓类效果加倍）
            ItemModuleBase.setType(itemStack, "speed_trigger");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 镀层无尽苦难 Galvanized Continuous Misery
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_continuous_misery");
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 1.0001f);              // +100%触发时间
            ItemModuleBase.addAttributes(itemStack, "killStackTriggerChance", 0.30001f);  // 击杀时：+30%触发几率(4层满=120%)
            ItemModuleBase.setType(itemStack, "continuous_misery");  // 使用普通版type，产生冲突
            items.add(itemStack);
            itemStackList.add(itemStack.copy());
        }
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
        return EnumRarity.EPIC;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
