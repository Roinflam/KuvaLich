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

public class WarframeRareModule extends WarframeModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public WarframeRareModule(@NotNull String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_RARE_MODULE);
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
            itemStack.setTranslatableName("kuvaweapon.warframe_module.sure_footed");
            ItemModuleBase.addAttributes(itemStack, "knockbackResistance", 0.6001f);
            ItemModuleBase.setType(itemStack, "sure_footed");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.vigor");
            ItemModuleBase.addAttributes(itemStack, "shield", 0.5001f);
            ItemModuleBase.addAttributes(itemStack, "health", 0.5001f);
            ItemModuleBase.setType(itemStack, "vigor");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.armored_agility");
            ItemModuleBase.addAttributes(itemStack, "sprintSpeed", 0.15001f);
            ItemModuleBase.addAttributes(itemStack, "armor", 0.4001f);
            ItemModuleBase.setType(itemStack, "armored_agility");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.fortitude");
            ItemModuleBase.addAttributes(itemStack, "knockbackResistance", 0.4001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 1.0001f);
            ItemModuleBase.setType(itemStack, "fortitude");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.treasure_hunter");
            ItemModuleBase.addAttributes(itemStack, "itemDropMultiplier", 0.6001f);
            ItemModuleBase.setType(itemStack, "treasure_hunter");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.deep_friendship");
            ItemModuleBase.addAttributes(itemStack, "homologousProtection", 0.3001f);
            ItemModuleBase.setType(itemStack, "deep_friendship");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.pal");
            ItemModuleBase.addAttributes(itemStack, "reachDistance", 0.201f);
            ItemModuleBase.addAttributes(itemStack, "diggingSpeed", 0.401f);
            ItemModuleBase.setType(itemStack, "pal");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.overclock");
            ItemModuleBase.addAttributes(itemStack, "sprintSpeed", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "shield", -0.9001f);
            ItemModuleBase.setType(itemStack, "overclock");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.overextended");
            ItemModuleBase.addAttributes(itemStack, "reachDistance", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "diggingSpeed", -0.6001f);
            ItemModuleBase.setType(itemStack, "overextended");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.narrow_minded");
            ItemModuleBase.addAttributes(itemStack, "diggingSpeed", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "reachDistance", -0.6001f);
            ItemModuleBase.setType(itemStack, "overextended");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.rejuvenation");
            ItemModuleBase.addAttributes(itemStack, "health", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "shield", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "responseRate", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 0.3001f);
            ItemModuleBase.setType(itemStack, "rejuvenation");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.rapid_recovery");
            ItemModuleBase.addAttributes(itemStack, "responseRate", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "health", -0.3001f);
            ItemModuleBase.addAttributes(itemStack, "shield", -0.3001f);
            ItemModuleBase.setType(itemStack, "rapid_recovery");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.eden_6");
            ItemModuleBase.addAttributes(itemStack, "itemDropMultiplier", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "diggingSpeed", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "reachDistance", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "health", -0.9001f);
            ItemModuleBase.setType(itemStack, "treasure_thief");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.sacrificial_blaze");
            ItemModuleBase.addAttributes(itemStack, "fireProtection", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "armor", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "sprintSpeed", 0.4501f);
            ItemModuleBase.addAttributes(itemStack, "health", -0.6001f);
            ItemModuleBase.setType(itemStack, "flame_repellent");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());
        }
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}
