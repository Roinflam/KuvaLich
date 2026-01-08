// 文件名：WarframeUncommonModule.java
// 路径：src/main/java/pers/roinflam/kuvalich/item/module/warframe/WarframeUncommonModule.java
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
 * 白银级战甲模组
 * Uncommon (Silver) tier warframe module
 */
public class WarframeUncommonModule extends WarframeModuleBase {

    /**
     * 静态模组列表，用于随机获取
     * Static module list for random obtaining
     */
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public WarframeUncommonModule(String name) {
        super(name);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_UNCOMMON_MODULE);
        itemStack.setTranslatableName("kuvaweapon.warframe_type_random.name");
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            // 随机模组始终显示
            items.add(getRandomModule());

            // 冲刺
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.rush", "rush",
                    new Object[]{"sprintSpeed", 0.3001f});

            // 占卜师契约
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.augur_accord", "augur_accord",
                    new Object[]{"shield", 0.7001f});

            // 快速充能
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.fast_deflection", "fast_deflection",
                    new Object[]{"shieldRecoveryRate", 0.9001f, "shieldRecoveryDelay", -0.45001f});

            // 角斗士决心
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.gladiator_resolve", "gladiator_resolve",
                    new Object[]{"health", 0.4001f});

            // 肉食甲壳
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.carnis_carapace", "carnis_carapace",
                    new Object[]{"armor", 1.1001f, "health", 0.4001f});

            // 巨岩甲壳
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.saxum_carapace", "saxum_carapace",
                    new Object[]{"armor", 1.1001f, "health", 0.4001f});

            // 挖掘之手
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.digging_hand", "digging_hand",
                    new Object[]{"reachDistance", 0.3001f});

            // 挖掘之力
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.digging_power", "digging_power",
                    new Object[]{"diggingSpeed", 0.6001f},
                    "digging_speed");

            // 复苏者
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.rejuvenator", "rejuvenator",
                    new Object[]{"responseRate", 1.0001f, "health", -0.5001f});

            // 宝藏盗贼
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.treasure_thief", "treasure_thief",
                    new Object[]{"itemDropMultiplier", 0.9001f, "health", -0.6001f, "shield", -1.2001f},
                    "item_drop_multiplier");

            // 坚守阵地
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.stand_your_ground", "stand_your_ground",
                    new Object[]{"health", 0.9001f, "armor", 0.6001f, "knockbackResistance", 0.4501f, "sprintSpeed", -0.3001f});

            // 雅典娜
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.athena", "redirection",
                    new Object[]{"health", 1.2001f, "armor", 0.9001f, "responseRate", 0.6001f, "shieldRecoveryRate", -0.9001f, "shieldRecoveryDelay", -0.9001f});

            // ========== 自定义模组 / Custom Modules ==========
            CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, EnumRarity.UNCOMMON);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        if (!worldIn.isRemote && ItemModuleBase.isRandom(itemstack) && handIn.equals(EnumHand.MAIN_HAND)) {
            // 过滤掉被禁用的模组
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);

            // ========== 添加自定义模组到随机池 / Add custom modules to random pool ==========
            CustomModuleManager.getInstance().addCustomWarframeModulesToRandomList(availableModules, EnumRarity.UNCOMMON);

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
        return EnumRarity.UNCOMMON;
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}