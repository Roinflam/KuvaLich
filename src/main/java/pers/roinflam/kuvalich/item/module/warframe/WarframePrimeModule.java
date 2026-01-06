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

public class WarframePrimeModule extends WarframeModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public WarframePrimeModule(String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_PRIME_MODULE);
        itemStack.setTranslatableName("kuvaweapon.warframe_type_random.name");
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

            // ========== 执刑官系列MOD（12个） ==========

            // 1. 执刑官 生命力 (Executioner Vitality)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_vitality");
            ItemModuleBase.addAttributes(itemStack, "health", 1.0001f);
            ItemModuleBase.addAttributes(itemStack, "killStackHealth", 0.08501f);
            ItemModuleBase.setType(itemStack, "vitality");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 2. 执刑官 蓄能重划 (Executioner Redirection)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_redirection");
            ItemModuleBase.addAttributes(itemStack, "shield", 1.0001f);
            ItemModuleBase.addAttributes(itemStack, "killStackShield", 0.04001f);
            ItemModuleBase.addAttributes(itemStack, "killStackShieldRecoveryRate", 0.02501f);
            ItemModuleBase.setType(itemStack, "redirection");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 3. 执刑官 钢铁纤维 (Executioner Steel Fiber)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_steel_fiber");
            ItemModuleBase.addAttributes(itemStack, "armor", 1.0001f);
            ItemModuleBase.addAttributes(itemStack, "killStackArmor", 0.08501f);
            ItemModuleBase.setType(itemStack, "steel_fiber");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 4. 执刑官 冲刺 (Executioner Rush)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_rush");
            ItemModuleBase.addAttributes(itemStack, "sprintSpeed", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "killStackSprintSpeed", 0.02501f);
            ItemModuleBase.setType(itemStack, "rush");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 5. 执刑官 活力 (Executioner Vigor)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_vigor");
            ItemModuleBase.addAttributes(itemStack, "shield", 0.5001f);
            ItemModuleBase.addAttributes(itemStack, "health", 0.5001f);
            ItemModuleBase.addAttributes(itemStack, "killStackShield", 0.03251f);
            ItemModuleBase.addAttributes(itemStack, "killStackHealth", 0.03251f);
            ItemModuleBase.setType(itemStack, "vigor");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 6. 执刑官 快速充能 (Executioner Fast Deflection)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_fast_deflection");
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryDelay", -0.4501f);
            ItemModuleBase.addAttributes(itemStack, "killStackShieldRecoveryRate", 0.02501f);
            ItemModuleBase.addAttributes(itemStack, "killStackShieldRecoveryDelay", -0.02001f);
            ItemModuleBase.setType(itemStack, "fast_deflection");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 7. 执刑官 火焰防护 (Executioner Flame Repellent)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_flame_repellent");
            ItemModuleBase.addAttributes(itemStack, "fireProtection", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "killStackFireProtection", 0.02501f);
            ItemModuleBase.setType(itemStack, "flame_repellent");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 8. 执刑官 避雷针 (Executioner Lightning Rod)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_lightning_rod");
            ItemModuleBase.addAttributes(itemStack, "electricProtection", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "killStackElectricProtection", 0.02501f);
            ItemModuleBase.setType(itemStack, "lightning_rod");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 9. 执刑官 情同手足 (Executioner Deep Friendship)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_deep_friendship");
            ItemModuleBase.addAttributes(itemStack, "homologousProtection", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "killStackHomologousProtection", 0.03001f);
            ItemModuleBase.setType(itemStack, "deep_friendship");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 10. 执刑官 密藏猎人 (Executioner Treasure Hunter)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_treasure_hunter");
            ItemModuleBase.addAttributes(itemStack, "itemDropMultiplier", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "killStackItemDropMultiplier", 0.03501f);
            ItemModuleBase.setType(itemStack, "treasure_hunter");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 11. 执刑官 碎岩者之力 (Executioner Digging Power)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_digging_power");
            ItemModuleBase.addAttributes(itemStack, "diggingSpeed", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "killStackDiggingSpeed", 0.02501f);
            ItemModuleBase.setType(itemStack, "digging_power");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 12. 执刑官 返老还童 (Executioner Rejuvenation)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.warframe_module.executioner_rejuvenation");
            ItemModuleBase.addAttributes(itemStack, "health", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "shield", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "responseRate", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "shieldRecoveryRate", 0.3001f);
            ItemModuleBase.addAttributes(itemStack, "killStackHealth", 0.02001f);
            ItemModuleBase.addAttributes(itemStack, "killStackShield", 0.02001f);
            ItemModuleBase.addAttributes(itemStack, "killStackResponseRate", 0.02001f);
            ItemModuleBase.addAttributes(itemStack, "killStackShieldRecoveryRate", 0.02001f);
            ItemModuleBase.setType(itemStack, "rejuvenation");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());
        }
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}