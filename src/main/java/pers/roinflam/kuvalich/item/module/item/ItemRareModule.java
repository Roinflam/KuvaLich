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

public class ItemRareModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemRareModule(@NotNull String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RARE_MODULE);
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
            itemStack.setTranslatableName("kuvaweapon.item_module.split_chamber");
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.90001f);
            ItemModuleBase.setType(itemStack, "split_chamber");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.high_voltage");
            ItemModuleBase.addAttributes(itemStack, "electricity", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.setType(itemStack, "high_voltage");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.rime_rounds");
            ItemModuleBase.addAttributes(itemStack, "ice", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.setType(itemStack, "rime_rounds");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.vital_sense");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 1.20001f);
            ItemModuleBase.setType(itemStack, "vital_sense");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.thermite_rounds");
            ItemModuleBase.addAttributes(itemStack, "fire", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.setType(itemStack, "thermite_rounds");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.malignant_force");
            ItemModuleBase.addAttributes(itemStack, "poison", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.setType(itemStack, "malignant_force");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.hammer_shot");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.80001f);
            ItemModuleBase.setType(itemStack, "hammer_shot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.critical_delay");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 2.00001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.20001f);
            ItemModuleBase.setType(itemStack, "point_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.vile_acceleration");
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.15001f);
            ItemModuleBase.setType(itemStack, "vile_acceleration");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.spoiled_strike");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.00001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", -0.20001f);
            ItemModuleBase.setType(itemStack, "spoiled_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.hollow_point");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.150001f);
            ItemModuleBase.setType(itemStack, "hollow_point");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.jagged_edge");
            ItemModuleBase.addAttributes(itemStack, "slash", 0.90001f);
            ItemModuleBase.setType(itemStack, "jagged_edge");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.buzz_kill");
            ItemModuleBase.addAttributes(itemStack, "slash", 1.20001f);
            ItemModuleBase.setType(itemStack, "buzz_kill");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.rending_strike");
            ItemModuleBase.addAttributes(itemStack, "slash", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.80001f);
            ItemModuleBase.setType(itemStack, "rending_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.heavy_trauma");
            ItemModuleBase.addAttributes(itemStack, "impact", 0.90001f);
            ItemModuleBase.setType(itemStack, "heavy_trauma");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.collision_force");
            ItemModuleBase.addAttributes(itemStack, "impact", 1.20001f);
            ItemModuleBase.setType(itemStack, "collision_force");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.sundering_strike");
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.90001f);
            ItemModuleBase.setType(itemStack, "sundering_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.auger_strike");
            ItemModuleBase.addAttributes(itemStack, "puncture", 1.20001f);
            ItemModuleBase.setType(itemStack, "auger_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.accelerated_blast");
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.60001f);
            ItemModuleBase.setType(itemStack, "accelerated_blast");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.katana_energy");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 3.00001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -0.50001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", -0.50001f);
            ItemModuleBase.setType(itemStack, "katana_energy");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.heavy_caliber");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.60001f);
            ItemModuleBase.setType(itemStack, "small_caliber");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.eldest_elemental_envoy");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.20001f);
            ItemModuleBase.setType(itemStack, "eldest_elemental_envoy");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.infinity_blade");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", -1.20001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", -1.20001f);
            ItemModuleBase.setType(itemStack, "infinity_blade");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.residual_epidemic");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 3.00001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -1.20001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", -1.20001f);
            ItemModuleBase.setType(itemStack, "infinity_blade");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.maiming_strike");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 1.50001f);
            ItemModuleBase.setType(itemStack, "maiming_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.creeping_bullseye");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.20001f);
            ItemModuleBase.setType(itemStack, "creeping_bullseye");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.tainted_mag");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "fire", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "poison", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.20001f);
            ItemModuleBase.setType(itemStack, "tainted_mag");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.firestorm");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.24001f);
            ItemModuleBase.setType(itemStack, "firestorm");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.rain_of_arrows");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 1.6501f);
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.1001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.60001f);
            ItemModuleBase.setType(itemStack, "rain_of_arrows");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.joy_of_fire");
            ItemModuleBase.addAttributes(itemStack, "fire", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "attackRange", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", -0.3001f);
            ItemModuleBase.setType(itemStack, "joy_of_fire");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.frostmourne");
            ItemModuleBase.addAttributes(itemStack, "ice", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "attackRange", -0.3001f);
            ItemModuleBase.setType(itemStack, "joy_of_fire");
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
        return EnumRarity.RARE;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}
