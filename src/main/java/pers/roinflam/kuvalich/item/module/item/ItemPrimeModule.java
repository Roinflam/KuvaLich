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

import pers.roinflam.kuvalich.base.item.ItemModuleBase;
import pers.roinflam.kuvalich.base.item.ModuleBase;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemPrimeModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemPrimeModule(String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_PRIME_MODULE);
        itemStack.setTranslatableName("kuvaweapon.item_type_random.name");
        ModuleBase.setRandom(itemStack, true);
        return itemStack;
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            ItemStack itemStack = getRandomModule();
            items.add(itemStack);

            // ========== 原有MOD ==========

            // kuvaweapon.item_module.pressure_point_prime (压力点Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.pressure_point_prime");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.65001f);
            ItemModuleBase.setType(itemStack, "pressure_point");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.reach_prime (攻击范围Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.reach_prime");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 1.65001f);
            ItemModuleBase.setType(itemStack, "reach");
            ItemModuleBase.setConflictTags(itemStack, "attack_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.fury_prime (狂怒Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fury_prime");
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.55001f);
            ItemModuleBase.setType(itemStack, "fury");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.galvanized_chamber (镀层分裂膛室)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_chamber");
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.80001f);
            ItemModuleBase.addAttributes(itemStack, "killStackMultishot", 0.30001f);
            ItemModuleBase.setType(itemStack, "split_chamber");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.galvanized_steel (镀层真钢)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_steel");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 1.10001f);
            ItemModuleBase.addAttributes(itemStack, "killStackMeleeCriticalMultiplier", 0.30001f);
            ItemModuleBase.setType(itemStack, "true_steel");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.galvanized_reach (镀层攻击范围)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_reach");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 0.80001f);
            ItemModuleBase.addAttributes(itemStack, "killStackAttackRange", 0.30001f);
            ItemModuleBase.setType(itemStack, "reach");
            ItemModuleBase.setConflictTags(itemStack, "attack_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.galvanized_fury (镀层狂怒)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_fury");
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "killStackAttackSpeed", 0.10001f);
            ItemModuleBase.setType(itemStack, "fury");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.galvanized_aptitude (镀层武器资质)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_aptitude");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "killStackTriggerChance", 0.30001f);
            ItemModuleBase.setType(itemStack, "weapon_aptitude");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.galvanized_speed_trigger (镀层速度触发)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_speed_trigger");
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.40001f);
            ItemModuleBase.addAttributes(itemStack, "killStackFiringRate", 0.20001f);
            ItemModuleBase.setType(itemStack, "speed_trigger");
            ItemModuleBase.setConflictTags(itemStack, "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.galvanized_firestorm (镀层火焰风暴)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_firestorm");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.40001f);
            ItemModuleBase.addAttributes(itemStack, "killStackBurstingRadius", 0.20001f);
            ItemModuleBase.setType(itemStack, "firestorm");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // ========== 新增Prime MOD ==========

            // 59. kuvaweapon.item_module.predatory_instinct_prime (掠食本能Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.predatory_instinct_prime");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 2.00001f);
            ItemModuleBase.addAttributes(itemStack, "dashAttackRange", 5.00001f);
            ItemModuleBase.setType(itemStack, "predatory_instinct");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance", "dash_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 60. kuvaweapon.item_module.hunters_moment_prime (猎杀时刻Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.hunters_moment_prime");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 2.00001f);
            ItemModuleBase.addAttributes(itemStack, "dashTriggerChance", 2.40001f);
            ItemModuleBase.setType(itemStack, "hunters_moment");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance", "dash_trigger");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 61. kuvaweapon.item_module.violent_aesthetic_prime (非暴力美学Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.violent_aesthetic_prime");
            ItemModuleBase.addAttributes(itemStack, "baseDamageWhenNotCriticalStrike", 3.00001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.40001f);
            ItemModuleBase.setType(itemStack, "violent_aesthetic");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 62. kuvaweapon.item_module.infection_protocol_prime (感染协议Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.infection_protocol_prime");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 1.35001f);
            ItemModuleBase.setType(itemStack, "infection_protocol");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 63. kuvaweapon.item_module.dual_covenant_prime (双重契约Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.dual_covenant_prime");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 2.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 2.20001f);
            ItemModuleBase.setType(itemStack, "dual_covenant");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 64. kuvaweapon.item_module.mystic_archery_prime (秘法弓术Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.mystic_archery_prime");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 2.55001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.65001f);
            ItemModuleBase.setType(itemStack, "mystic_archery");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 65. kuvaweapon.item_module.arcane_ballistics_prime (奥能轨迹Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arcane_ballistics_prime");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 2.55001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.65001f);
            ItemModuleBase.setType(itemStack, "arcane_ballistics");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 66. kuvaweapon.item_module.reapers_scythe_prime (死神镰刀Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.reapers_scythe_prime");
            ItemModuleBase.addAttributes(itemStack, "multishot", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 1.60001f);
            ItemModuleBase.setType(itemStack, "reapers_scythe");
            ItemModuleBase.setConflictTags(itemStack, "multishot", "remote_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 67. kuvaweapon.item_module.hunters_code_prime (猎人法则Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.hunters_code_prime");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "slash", 1.65001f);
            ItemModuleBase.setType(itemStack, "hunters_code");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 68. kuvaweapon.item_module.corrosion_king_prime (剥皮者Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.corrosion_king_prime");
            ItemModuleBase.addAttributes(itemStack, "corrosion", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "slash", 1.25001f);
            ItemModuleBase.setType(itemStack, "corrosion_king");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 69. kuvaweapon.item_module.plague_herald_prime (瘟疫使者Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.plague_herald_prime");
            ItemModuleBase.addAttributes(itemStack, "gas", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 1.35001f);
            ItemModuleBase.setType(itemStack, "plague_herald");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 70. kuvaweapon.item_module.nuclear_storm_prime (核子风暴Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.nuclear_storm_prime");
            ItemModuleBase.addAttributes(itemStack, "radiation", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.90001f);
            ItemModuleBase.setType(itemStack, "nuclear_storm");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 71. kuvaweapon.item_module.magnetic_overlord_prime (磁暴领主Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magnetic_overlord_prime");
            ItemModuleBase.addAttributes(itemStack, "magnetic", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 1.35001f);
            ItemModuleBase.setType(itemStack, "magnetic_overlord");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 72. kuvaweapon.item_module.catastrophe_prime (灾厄降临Prime)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.catastrophe_prime");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.33001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 1.20001f);
            ItemModuleBase.setType(itemStack, "catastrophe");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // ========== 新增镀层MOD ==========

            // 73. kuvaweapon.item_module.galvanized_predatory_instinct (镀层掠食本能)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_predatory_instinct");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "dashAttackRange", 3.00001f);
            ItemModuleBase.addAttributes(itemStack, "killStackAttackRange", 0.50001f);
            ItemModuleBase.setType(itemStack, "predatory_instinct");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance", "dash_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 74. kuvaweapon.item_module.galvanized_hunters_moment (镀层猎杀时刻)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_hunters_moment");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "dashTriggerChance", 1.35001f);
            ItemModuleBase.addAttributes(itemStack, "killStackTriggerChance", 0.30001f);
            ItemModuleBase.setType(itemStack, "hunters_moment");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance", "dash_trigger");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 75. kuvaweapon.item_module.galvanized_violent_aesthetic (镀层暴力美学)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_violent_aesthetic");
            ItemModuleBase.addAttributes(itemStack, "baseDamageWhenNotCriticalStrike", 2.00001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "killStackAttackSpeed", 0.10001f);
            ItemModuleBase.setType(itemStack, "violent_aesthetic");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 76. kuvaweapon.item_module.galvanized_infection_protocol (镀层感染协议)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_infection_protocol");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "killStackTriggerChance", 0.30001f);
            ItemModuleBase.setType(itemStack, "infection_protocol");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 77. kuvaweapon.item_module.galvanized_dual_covenant (镀层双重契约)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_dual_covenant");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "killStackMeleeCriticalMultiplier", 0.50001f);
            ItemModuleBase.setType(itemStack, "dual_covenant");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 78. kuvaweapon.item_module.galvanized_reapers_scythe (镀层死神镰刀)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_reapers_scythe");
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 0.80001f);
            ItemModuleBase.addAttributes(itemStack, "killStackMultishot", 0.30001f);
            ItemModuleBase.setType(itemStack, "reapers_scythe");
            ItemModuleBase.setConflictTags(itemStack, "multishot", "remote_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 79. kuvaweapon.item_module.galvanized_shockwave_domain (镀层震荡领域)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_shockwave_domain");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.24001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "killStackBurstingRadius", 0.05001f);
            ItemModuleBase.setType(itemStack, "shockwave_domain");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 80. kuvaweapon.item_module.galvanized_death_barrage (镀层死亡弹幕)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.galvanized_death_barrage");
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.45001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "killStackMultishot", 0.25001f);
            ItemModuleBase.setType(itemStack, "death_barrage");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
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
        return EnumRarity.EPIC;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}