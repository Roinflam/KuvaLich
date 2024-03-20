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

public class WarframeUncommonModule extends WarframeModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public WarframeUncommonModule(@NotNull String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_UNCOMMON_MODULE);
        itemStack.setTranslatableName("kuvaweapon.warframe_type_random.name");
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            ItemStack itemStack = getRandomModule();
            items.add(itemStack);

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.rush");
            ItemModuleBase.addAttributes(itemStack, "sprintSpeed", 0.3001f);
            ItemModuleBase.setType(itemStack, "rush");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.augur_accord");
            ItemModuleBase.addAttributes(itemStack, "shield", 0.7001f);
            ItemModuleBase.setType(itemStack, "augur_accord");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.fast_deflection");
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryDelay", -0.45001f);
            ItemModuleBase.setType(itemStack, "fast_deflection");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.gladiator_resolve");
            ItemModuleBase.addAttributes(itemStack, "health", 0.4001f);
            ItemModuleBase.setType(itemStack, "gladiator_resolve");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.carnis_carapace");
            ItemModuleBase.addAttributes(itemStack, "armor", 1.1001f);
            ItemModuleBase.addAttributes(itemStack, "health", 0.4001f);
            ItemModuleBase.setType(itemStack, "carnis_carapace");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.saxum_carapace");
            ItemModuleBase.addAttributes(itemStack, "armor", 1.1001f);
            ItemModuleBase.addAttributes(itemStack, "health", 0.4001f);
            ItemModuleBase.setType(itemStack, "saxum_carapace");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.digging_hand");
            ItemModuleBase.addAttributes(itemStack, "reachDistance", 0.3001f);
            ItemModuleBase.setType(itemStack, "digging_hand");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.digging_power");
            ItemModuleBase.addAttributes(itemStack, "diggingSpeed", 0.6001f);
            ItemModuleBase.setType(itemStack, "digging_power");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.rejuvenator");
            ItemModuleBase.addAttributes(itemStack, "responseRate", 1.0001f);
            ItemModuleBase.addAttributes(itemStack, "health", -0.5001f);
            ItemModuleBase.setType(itemStack, "rejuvenator");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.treasure_thief");
            ItemModuleBase.addAttributes(itemStack, "itemDropMultiplier", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "health", -0.6001f);
            ItemModuleBase.addAttributes(itemStack, "shield", -1.2001f);
            ItemModuleBase.setType(itemStack, "treasure_thief");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.stand_your_ground");
            ItemModuleBase.addAttributes(itemStack, "health", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "armor", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "knockbackResistance", 0.4501f);
            ItemModuleBase.addAttributes(itemStack, "sprintSpeed", -0.3001f);
            ItemModuleBase.setType(itemStack, "stand_your_ground");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.athena");
            ItemModuleBase.addAttributes(itemStack, "health", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "armor", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "responseRate", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", -0.9001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryDelay", -0.9001f);
            ItemModuleBase.setType(itemStack, "redirection");
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
        return EnumRarity.UNCOMMON;
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}
