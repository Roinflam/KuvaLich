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
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemPrimeModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemPrimeModule(@NotNull String name) {
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
        }
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
        return EnumRarity.EPIC;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
