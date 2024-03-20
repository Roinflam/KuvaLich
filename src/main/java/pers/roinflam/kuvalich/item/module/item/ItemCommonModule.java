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
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemCommonModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemCommonModule(@NotNull String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_COMMON_MODULE);
        itemStack.setTranslatableName("kuvaweapon.item_type_random.name");
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    @Override
    public @NotNull ActionResult<ItemStack> onItemRightClick(World worldIn, @NotNull EntityPlayer playerIn, EnumHand handIn) {
        @NotNull ItemStack itemstack = playerIn.getHeldItem(handIn);
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
    public @NotNull EnumRarity getRarity(@NotNull ItemStack stack) {
        return EnumRarity.COMMON;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            ItemStack itemStack = getRandomModule();
            items.add(itemStack);

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.pressure_point");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.2001f);
            ItemModuleBase.setType(itemStack, "pressure_point");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.reach");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 1.1001f);
            ItemModuleBase.setType(itemStack, "reach");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.true_steel");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 1.2001f);
            ItemModuleBase.setType(itemStack, "true_steel");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.organ_shatter");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", 0.9001f);
            ItemModuleBase.setType(itemStack, "organ_shatter");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.point_strike");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 0.9001f);
            ItemModuleBase.setType(itemStack, "point_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.continuous_misery");
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 1.0f);
            ItemModuleBase.setType(itemStack, "continuous_misery");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.sawtooth_clip");
            ItemModuleBase.addAttributes(itemStack, "slash", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 0.30001f);
            ItemModuleBase.setType(itemStack, "slash");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.argon_crystal_shoot");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 0.45001f);
            ItemModuleBase.setType(itemStack, "argon_crystal_shoot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.argon_crystal_wrath");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.45001f);
            ItemModuleBase.setType(itemStack, "argon_crystal_wrath");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.elemental_argon_crystal_wrath");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.9001f);
            ItemModuleBase.setType(itemStack, "pressure_point");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.chainsaw_from_hell");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", -0.3001f);
            ItemModuleBase.setType(itemStack, "chainsaw_from_hell");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.tear");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", -0.60001f);
            ItemModuleBase.setType(itemStack, "true_steel");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.disaster_shoot");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.90001f);
            ItemModuleBase.setType(itemStack, "disaster_shoot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.malicious");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -0.6001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", -0.6001f);
            ItemModuleBase.setType(itemStack, "malicious");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.single_point_breakthrough");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.6501f);
            ItemModuleBase.addAttributes(itemStack, "attackRange", -0.7501f);
            ItemModuleBase.setType(itemStack, "single_point_breakthrough");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.lesion_blow");
            ItemModuleBase.addAttributes(itemStack, "dashTriggerChance", 1.50001f);
            ItemModuleBase.setType(itemStack, "lesion_blow");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.persuasion");
            ItemModuleBase.addAttributes(itemStack, "baseDamageWhenNotCriticalStrike", 1.20001f);
            ItemModuleBase.setType(itemStack, "persuasion");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.touches_of_extension");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "slash", -1.20001f);
            ItemModuleBase.setType(itemStack, "touches_of_extension");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fierce_bow");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.60001f);
            ItemModuleBase.setType(itemStack, "fierce_bow");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.phantom_arrow");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.30001f);
            ItemModuleBase.setType(itemStack, "phantom_arrow");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magical_growth");
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.60001f);
            ItemModuleBase.setType(itemStack, "magical_growth");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magic_missile");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 0.60001f);
            ItemModuleBase.setType(itemStack, "magic_missile");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.pandora_star");
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 3.00001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", -1.50001f);
            ItemModuleBase.setType(itemStack, "pandora_star");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());
        }
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
