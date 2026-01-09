// 文件名：WarframeRareModule.java
// 路径：src/main/java/pers/roinflam/kuvalich/item/module/warframe/WarframeRareModule.java
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
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 黄金级战甲模组
 * Rare (Gold) tier warframe module
 */
public class WarframeRareModule extends WarframeModuleBase {

    /**
     * 静态模组列表，用于随机获取
     * Static module list for random obtaining
     */
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public WarframeRareModule(String name) {
        super(name);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_RARE_MODULE);
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

            // ========== 添加自定义模组到随机池 / Add custom modules to random pool ==========
            CustomModuleManager.getInstance().addCustomWarframeModulesToRandomList(availableModules, EnumRarity.RARE);

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

            // 稳如泰山
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.sure_footed", "sure_footed",
                    new Object[]{"knockbackResistance", 0.6001f});

            // 活力
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.vigor", "vigor",
                    new Object[]{"shield", 0.5001f, "health", 0.5001f});

            // 装甲敏捷
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.armored_agility", "armored_agility",
                    new Object[]{"sprintSpeed", 0.15001f, "armor", 0.4001f});

            // 刚毅
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.fortitude", "fortitude",
                    new Object[]{"knockbackResistance", 0.4001f, "shieldRecoveryRate", 1.0001f});

            // 密藏猎人
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.treasure_hunter", "treasure_hunter",
                    new Object[]{"itemDropMultiplier", 0.6001f},
                    "item_drop_multiplier");

            // 情同手足
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.deep_friendship", "deep_friendship",
                    new Object[]{"homologousProtection", 0.3001f});

            // 朋友
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.pal", "pal",
                    new Object[]{"reachDistance", 0.201f, "diggingSpeed", 0.401f});

            // 超频
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.overclock", "overclock",
                    new Object[]{"sprintSpeed", 0.3001f, "shieldRecoveryRate", 0.6001f, "shield", -0.9001f});

            // 过度延伸
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.overextended", "overextended",
                    new Object[]{"reachDistance", 0.9001f, "diggingSpeed", -0.6001f});

            // 心胸狭窄
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.narrow_minded", "overextended",
                    new Object[]{"diggingSpeed", 0.9001f, "reachDistance", -0.6001f});

            // 返老还童
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.rejuvenation", "rejuvenation",
                    new Object[]{"health", 0.3001f, "shield", 0.3001f, "responseRate", 0.3001f, "shieldRecoveryRate", 0.3001f});

            // 快速恢复
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.rapid_recovery", "rapid_recovery",
                    new Object[]{"responseRate", 0.6001f, "shieldRecoveryRate", 0.6001f, "health", -0.3001f, "shield", -0.3001f});

            // 伊甸6号
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.eden_6", "treasure_thief",
                    new Object[]{"itemDropMultiplier", 0.9001f, "diggingSpeed", 0.6001f, "reachDistance", 0.3001f, "health", -0.9001f},
                    "item_drop_multiplier");

            // 献祭烈焰
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.sacrificial_blaze", "flame_repellent",
                    new Object[]{"fireProtection", 0.9001f, "armor", 0.9001f, "sprintSpeed", 0.4501f, "health", -0.6001f});

            // 协奏
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.concerto", "health_lock",
                    new Object[]{"shieldRecoveryDelay", -1.0001f, "shieldRecoveryRate", 1.0001f, "fixedHealth", 20.0001f, "health", -3.5001f},
                    "health_lock");

            // 库娃的优雅
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.kovas_elegance", "kovas_elegance",
                    new Object[]{"fallProtection", 1.0001f, "health", -0.9001f});

            // 力场护盾
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.force_field_shield", "armor_lock",
                    new Object[]{"fixedShield", 60.0001f, "shieldRecoveryRate", 2.0001f, "armor", -2.2001f},
                    "armor_lock");

            // ========== 自定义模组 / Custom Modules ==========
            CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, EnumRarity.RARE);
        }
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}