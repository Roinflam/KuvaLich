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
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
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
            // 过滤掉被禁用的模组
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);

            if (availableModules.isEmpty()) {
                return new ActionResult<ItemStack>(EnumActionResult.FAIL, itemstack);
            }

            ItemStack module = availableModules.get(RandomUtil.getInt(0, availableModules.size() - 1)).copy();

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
            // 随机模组始终显示
            items.add(getRandomModule());

            // ========== Prime MOD ==========

            // 生命力Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.vitality_prime", "vitality",
                    new Object[]{"health", 1.8001f});

            // 蓄能重划Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.redirection_prime", "redirection",
                    new Object[]{"shield", 1.8001f});

            // 钢铁纤维Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.steel_fiber_prime", "steel_fiber",
                    new Object[]{"armor", 1.8001f});

            // 活力Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.vigor_prime", "vigor",
                    new Object[]{"shield", 0.7501f, "health", 0.7501f});

            // 激励Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.inspire_prime", "inspire",
                    new Object[]{"responseRate", 0.9001f});

            // 稳如泰山Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.sure_footed_prime", "sure_footed",
                    new Object[]{"knockbackResistance", 1.0001f});

            // 密藏猎人Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.treasure_hunter_prime", "treasure_hunter",
                    new Object[]{"itemDropMultiplier", 0.9001f},
                    "item_drop_multiplier");

            // 超频Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.overclock_prime", "overclock",
                    new Object[]{"sprintSpeed", 0.4501f, "shieldRecoveryRate", 0.9001f, "shield", -0.9001f});

            // 充能护甲Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.charged_armor_prime", "charged_armor",
                    new Object[]{"shield", 1.4001f, "shieldRecoveryRate", 1.4001f, "armor", -2.0001f});

            // 宝藏盗贼Prime
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.treasure_thief_prime", "treasure_thief",
                    new Object[]{"itemDropMultiplier", 1.2001f, "health", -0.6001f, "shield", -1.2001f},
                    "item_drop_multiplier");

            // ========== 执刑官系列MOD（12个） ==========

            // 执刑官 生命力
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_vitality", "vitality",
                    new Object[]{"health", 1.0001f, "killStackHealth", 0.08501f});

            // 执刑官 蓄能重划
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_redirection", "redirection",
                    new Object[]{"shield", 1.0001f, "killStackShield", 0.04001f, "killStackShieldRecoveryRate", 0.02501f});

            // 执刑官 钢铁纤维
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_steel_fiber", "steel_fiber",
                    new Object[]{"armor", 1.0001f, "killStackArmor", 0.08501f});

            // 执刑官 冲刺
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_rush", "rush",
                    new Object[]{"sprintSpeed", 0.3001f, "killStackSprintSpeed", 0.02501f});

            // 执刑官 活力
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_vigor", "vigor",
                    new Object[]{"shield", 0.5001f, "health", 0.5001f, "killStackShield", 0.03251f, "killStackHealth", 0.03251f});

            // 执刑官 快速充能
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_fast_deflection", "fast_deflection",
                    new Object[]{"shieldRecoveryRate", 0.9001f, "shieldRecoveryDelay", -0.4501f, "killStackShieldRecoveryRate", 0.02501f, "killStackShieldRecoveryDelay", -0.02001f});

            // 执刑官 火焰防护
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_flame_repellent", "flame_repellent",
                    new Object[]{"fireProtection", 0.6001f, "killStackFireProtection", 0.02501f});

            // 执刑官 避雷针
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_lightning_rod", "lightning_rod",
                    new Object[]{"electricProtection", 0.6001f, "killStackElectricProtection", 0.02501f});

            // 执刑官 情同手足
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_deep_friendship", "deep_friendship",
                    new Object[]{"homologousProtection", 0.3001f, "killStackHomologousProtection", 0.03001f});

            // 执刑官 密藏猎人
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_treasure_hunter", "treasure_hunter",
                    new Object[]{"itemDropMultiplier", 0.6001f, "killStackItemDropMultiplier", 0.03501f},
                    "item_drop_multiplier");

            // 执刑官 碎岩者之力
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_digging_power", "digging_power",
                    new Object[]{"diggingSpeed", 0.6001f, "killStackDiggingSpeed", 0.02501f},
                    "digging_speed");

            // 执刑官 返老还童
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.executioner_rejuvenation", "rejuvenation",
                    new Object[]{"health", 0.3001f, "shield", 0.3001f, "responseRate", 0.3001f, "shieldRecoveryRate", 0.3001f, "killStackHealth", 0.02001f, "killStackShield", 0.02001f, "killStackResponseRate", 0.02001f, "killStackShieldRecoveryRate", 0.02001f});
        }
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}