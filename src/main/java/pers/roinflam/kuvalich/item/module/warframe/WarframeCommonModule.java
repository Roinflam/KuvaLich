// 文件名：WarframeCommonModule.java
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
import pers.roinflam.kuvalich.config.custom.CustomModuleManager;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.ModuleRegistryHelper;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 青铜级战甲模组
 * Common (Bronze) tier warframe module
 */
public class WarframeCommonModule extends WarframeModuleBase {

    /**
     * 静态模组列表，用于随机获取
     * Static module list for random obtaining
     */
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public WarframeCommonModule(String name) {
        super(name);
    }

    /**
     * 获取随机模组物品
     * Get random module item
     */
    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.WARFRAME_COMMON_MODULE);
        itemStack.setTranslatableName("kuvaweapon.warframe_type_random.name");
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            // 随机模组始终显示
            items.add(getRandomModule());

            // 生命力
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.vitality", "vitality",
                    new Object[]{"health", 1.0001f});

            // 蓄能重划
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.redirection", "redirection",
                    new Object[]{"shield", 1.0001f});

            // 钢铁纤维
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.steel_fiber", "steel_fiber",
                    new Object[]{"armor", 1.0001f});

            // 火焰抵抗
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.flame_repellent", "flame_repellent",
                    new Object[]{"fireProtection", 0.6001f});

            // 避雷针
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.lightning_rod", "lightning_rod",
                    new Object[]{"electricProtection", 0.6001f});

            // 义警活力
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.vigilante_vigor", "vigilante_vigor",
                    new Object[]{"shieldRecoveryRate", 0.6001f, "shieldRecoveryDelay", -0.3001f});

            // 角斗士圣盾
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.gladiator_aegis", "gladiator_aegis",
                    new Object[]{"armor", 0.4001f});

            // 激励
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.inspire", "inspire",
                    new Object[]{"responseRate", 0.6001f});

            // 狂野
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.wild", "wild",
                    new Object[]{"health", 2.0001f, "shield", -2.0001f});

            // 充能护甲
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.charged_armor", "charged_armor",
                    new Object[]{"shield", 1.0001f, "shieldRecoveryRate", 1.0001f, "armor", -2.00001f});

            // 重甲
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.plated_armor", "plated_armor",
                    new Object[]{"armor", 0.8001f, "sprintSpeed", -0.2001f});

            // 负重兽
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.beast_of_burden", "beast_of_burden",
                    new Object[]{"reachDistance", 0.3001f, "diggingSpeed", 0.6001f, "health", -0.6001f});

            // 白骨
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.bones", "bones",
                    new Object[]{"armor", 1.2001f, "health", -0.4001f});

            // 马利万
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.maliwan", "maliwan",
                    new Object[]{"itemDropMultiplier", 1.2001f, "reachDistance", -0.3001f, "diggingSpeed", -0.6001f},
                    "item_drop_multiplier");

            // 跃动信号
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.mobilize", "mobilize",
                    new Object[]{"jumpBoost", 2.0001f});

            // 飞行员
            ModuleRegistryHelper.register(this, items, itemStackList,
                    "kuvaweapon.warframe_module.aviator", "aviator",
                    new Object[]{"fallProtection", 0.4001f});

            // ========== 自定义模组 / Custom Modules ==========
            CustomModuleManager.getInstance().addCustomWarframeModulesToCreativeTab(items, EnumRarity.COMMON);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        if (!worldIn.isRemote && ItemModuleBase.isRandom(itemstack) && handIn.equals(EnumHand.MAIN_HAND)) {
            // 过滤掉被禁用的模组
            List<ItemStack> availableModules = ModuleRegistryHelper.filterDisabled(itemStackList);

            // ========== 添加自定义模组到随机池 / Add custom modules to random pool ==========
            CustomModuleManager.getInstance().addCustomWarframeModulesToRandomList(availableModules, EnumRarity.COMMON);

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
        return EnumRarity.COMMON;
    }

    @Override
    public boolean isWarframe() {
        return true;
    }
}