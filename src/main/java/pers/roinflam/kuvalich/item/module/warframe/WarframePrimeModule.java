package pers.roinflam.kuvalich.item.module.warframe;

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
import pers.roinflam.kuvalich.base.item.WarframeModuleBase;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

public class WarframePrimeModule extends WarframeModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public WarframePrimeModule(@NotNull String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_PRIME_MODULE);
        itemStack.setTranslatableName("kuvaweapon.warframe_type_random.name");
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
        return EnumRarity.RARE;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            ItemStack itemStack = getRandomModule();
            items.add(itemStack);

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.vitality_prime");
            ItemModuleBase.addAttributes(itemStack, "health", 1.8001f);
            ItemModuleBase.setType(itemStack, "vitality");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.redirection_prime");
            ItemModuleBase.addAttributes(itemStack, "shield", 1.8001f);
            ItemModuleBase.setType(itemStack, "redirection");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.steel_fiber_prime");
            ItemModuleBase.addAttributes(itemStack, "armor", 1.8001f);
            ItemModuleBase.setType(itemStack, "steel_fiber");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.vigor_prime");
            ItemModuleBase.addAttributes(itemStack, "shield", 0.7501f);
            ItemModuleBase.addAttributes(itemStack, "health", 0.7501f);
            ItemModuleBase.setType(itemStack, "vigor");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.inspire_prime");
            ItemModuleBase.addAttributes(itemStack, "responseRate", 0.9001f);
            ItemModuleBase.setType(itemStack, "inspire");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.sure_footed_prime");
            ItemModuleBase.addAttributes(itemStack, "knockbackResistance", 1.0001f);
            ItemModuleBase.setType(itemStack, "sure_footed");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.treasure_hunter_prime");
            ItemModuleBase.addAttributes(itemStack, "itemDropMultiplier", 0.9001f);
            ItemModuleBase.setType(itemStack, "treasure_hunter");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.overclock_prime");
            ItemModuleBase.addAttributes(itemStack, "sprintSpeed", 0.4501f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "shield", -0.9001f);
            ItemModuleBase.setType(itemStack, "overclock");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.charged_armor_prime");
            ItemModuleBase.addAttributes(itemStack, "shield", 1.4001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 1.4001f);
            ItemModuleBase.addAttributes(itemStack, "armor", -2.0001f);
            ItemModuleBase.setType(itemStack, "charged_armor");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.treasure_thief_prime");
            ItemModuleBase.addAttributes(itemStack, "itemDropMultiplier", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "health", -0.6001f);
            ItemModuleBase.addAttributes(itemStack, "shield", -1.2001f);
            ItemModuleBase.setType(itemStack, "treasure_thief");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());
        }
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}
