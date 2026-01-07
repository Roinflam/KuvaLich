// 文件：WarframeCommonModule.java
// 路径：src/main/java/pers/roinflam/kuvalich/item/module/warframe/WarframeCommonModule.java
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

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.base.item.WarframeModuleBase;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

public class WarframeCommonModule extends WarframeModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public WarframeCommonModule(String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_COMMON_MODULE);
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
            itemStack.setTranslatableName("kuvaweapon.warframe_module.vitality");
            ItemModuleBase.addAttributes(itemStack, "health", 1.0001f);
            ItemModuleBase.setType(itemStack, "vitality");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.redirection");
            ItemModuleBase.addAttributes(itemStack, "shield", 1.0001f);
            ItemModuleBase.setType(itemStack, "redirection");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.steel_fiber");
            ItemModuleBase.addAttributes(itemStack, "armor", 1.0001f);
            ItemModuleBase.setType(itemStack, "steel_fiber");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.flame_repellent");
            ItemModuleBase.addAttributes(itemStack, "fireProtection", 0.6001f);
            ItemModuleBase.setType(itemStack, "flame_repellent");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.lightning_rod");
            ItemModuleBase.addAttributes(itemStack, "electricProtection", 0.6001f);
            ItemModuleBase.setType(itemStack, "lightning_rod");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.vigilante_vigor");
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryDelay", -0.3001f);
            ItemModuleBase.setType(itemStack, "vigilante_vigor");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.gladiator_aegis");
            ItemModuleBase.addAttributes(itemStack, "armor", 0.4001f);
            ItemModuleBase.setType(itemStack, "gladiator_aegis");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.inspire");
            ItemModuleBase.addAttributes(itemStack, "responseRate", 0.6001f);
            ItemModuleBase.setType(itemStack, "inspire");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.wild");
            ItemModuleBase.addAttributes(itemStack, "health", 2.0001f);
            ItemModuleBase.addAttributes(itemStack, "shield", -2.0001f);
            ItemModuleBase.setType(itemStack, "wild");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.charged_armor");
            ItemModuleBase.addAttributes(itemStack, "shield", 1.0001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 1.0001f);
            ItemModuleBase.addAttributes(itemStack, "armor", -2.00001f);
            ItemModuleBase.setType(itemStack, "charged_armor");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.plated_armor");
            ItemModuleBase.addAttributes(itemStack, "armor", 0.8001f);
            ItemModuleBase.addAttributes(itemStack, "sprintSpeed", -0.2001f);
            ItemModuleBase.setType(itemStack, "plated_armor");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.beast_of_burden");
            ItemModuleBase.addAttributes(itemStack, "reachDistance", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "diggingSpeed", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "health", -0.6001f);
            ItemModuleBase.setType(itemStack, "beast_of_burden");
            // ✅ 不添加conflictTags（复合型模组，提供多个属性）
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.bones");
            ItemModuleBase.addAttributes(itemStack, "armor", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "health", -0.4001f);
            ItemModuleBase.setType(itemStack, "bones");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.maliwan");
            ItemModuleBase.addAttributes(itemStack, "itemDropMultiplier", 1.2001f);
            ItemModuleBase.addAttributes(itemStack, "reachDistance", -0.3001f);
            ItemModuleBase.addAttributes(itemStack, "diggingSpeed", -0.6001f);
            ItemModuleBase.setType(itemStack, "maliwan");
            // ✅ 添加掉落倍率冲突标签（虽然是复合型，但掉落是核心功能）
            ItemModuleBase.setConflictTags(itemStack, "item_drop_multiplier");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // ✅ 新增：跃动信号 - 200%跳跃高度
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.mobilize");
            ItemModuleBase.addAttributes(itemStack, "jumpBoost", 2.0001f);
            ItemModuleBase.setType(itemStack, "mobilize");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // ✅ 新增：飞行员 - 40%摔落抗性
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.aviator");
            ItemModuleBase.addAttributes(itemStack, "fallProtection", 0.4001f);
            ItemModuleBase.setType(itemStack, "aviator");
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
        return EnumRarity.COMMON;
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}