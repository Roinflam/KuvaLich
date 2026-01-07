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

public class ItemUncommonModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemUncommonModule(String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_UNCOMMON_MODULE);
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

            // kuvaweapon.item_module.serration (切割)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.serration");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.65001f);
            ItemModuleBase.setType(itemStack, "serration");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.speed_trigger (速度触发)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.speed_trigger");
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.6001f);
            ItemModuleBase.setType(itemStack, "speed_trigger");
            ItemModuleBase.setConflictTags(itemStack, "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.molten_impact (熔化冲击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.molten_impact");
            ItemModuleBase.addAttributes(itemStack, "fire", 0.9001f);
            ItemModuleBase.setType(itemStack, "molten_impact");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.shocking_touch (电击之触)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.shocking_touch");
            ItemModuleBase.addAttributes(itemStack, "electricity", 0.9001f);
            ItemModuleBase.setType(itemStack, "shocking_touch");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.fever_strike (狂热打击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fever_strike");
            ItemModuleBase.addAttributes(itemStack, "poison", 0.9001f);
            ItemModuleBase.setType(itemStack, "fever_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.north_wind (北风)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.north_wind");
            ItemModuleBase.addAttributes(itemStack, "ice", 0.9001f);
            ItemModuleBase.setType(itemStack, "north_wind");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.bane_of_undefined (未定义生物克星)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_undefined");
            ItemModuleBase.addAttributes(itemStack, "bane_of_undefined", 0.3001f);
            ItemModuleBase.setType(itemStack, "bane_of_undefined");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.bane_of_undead (不死生物克星)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_undead");
            ItemModuleBase.addAttributes(itemStack, "bane_of_undead", 0.3001f);
            ItemModuleBase.setType(itemStack, "bane_of_undead");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.bane_of_arthropod (节肢生物克星)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_arthropod");
            ItemModuleBase.addAttributes(itemStack, "bane_of_arthropod", 0.3001f);
            ItemModuleBase.setType(itemStack, "bane_of_arthropod");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.bane_of_illager (灾厄村民克星)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bane_of_illager");
            ItemModuleBase.addAttributes(itemStack, "bane_of_illager", 0.3001f);
            ItemModuleBase.setType(itemStack, "bane_of_illager");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.weapon_aptitude (武器资质)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.weapon_aptitude");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.9001f);
            ItemModuleBase.setType(itemStack, "weapon_aptitude");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.fury (狂怒)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fury");
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.3001f);
            ItemModuleBase.setType(itemStack, "fury");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.roundhouse_chop (回旋砍)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.roundhouse_chop");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "slash", 0.60001f);
            ItemModuleBase.setType(itemStack, "roundhouse_chop");
            ItemModuleBase.setConflictTags(itemStack, "attack_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.magic_swordsman (魔法剑士)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magic_swordsman");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.20001f);
            ItemModuleBase.setType(itemStack, "magic_swordsman");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.triple_whammy (三重打击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.triple_whammy");
            ItemModuleBase.addAttributes(itemStack, "slash", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "impact", 0.60001f);
            ItemModuleBase.setType(itemStack, "triple_whammy");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.small_caliber (小口径)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.small_caliber");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.80001f);
            ItemModuleBase.setType(itemStack, "small_caliber");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_chance", "remote_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.stream (流体)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.stream");
            ItemModuleBase.addAttributes(itemStack, "dashAttackRange", 5.00001f);
            ItemModuleBase.setType(itemStack, "stream");
            ItemModuleBase.setConflictTags(itemStack, "attack_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.sure_shot (精准射击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.sure_shot");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.setType(itemStack, "sure_shot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.arrow_fire (火焰之箭)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arrow_fire");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "fire", 0.90001f);
            ItemModuleBase.setType(itemStack, "arrow_fire");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.arrow_ice (冰霜之箭)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arrow_ice");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "ice", 0.90001f);
            ItemModuleBase.setType(itemStack, "arrow_ice");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.arrow_poison (毒素之箭)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arrow_poison");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "poison", 0.90001f);
            ItemModuleBase.setType(itemStack, "arrow_poison");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.arrow_electricity (电击之箭)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arrow_electricity");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "electricity", 0.90001f);
            ItemModuleBase.setType(itemStack, "arrow_electricity");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.cryo_rounds (低温弹药)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.cryo_rounds");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "ice", 0.60001f);
            ItemModuleBase.setType(itemStack, "cryo_rounds");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.spring_loaded_chamber (弹簧弹舱)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.spring_loaded_chamber");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.55001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", -0.6001f);
            ItemModuleBase.setType(itemStack, "spring_loaded_chamber");
            ItemModuleBase.setConflictTags(itemStack, "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.serrated_rounds (锯齿弹药)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.serrated_rounds");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "slash", 0.9001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.6001f);
            ItemModuleBase.setType(itemStack, "serrated_rounds");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.devastated (毁灭)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.devastated");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.6001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.6001f);
            ItemModuleBase.setType(itemStack, "devastated");
            ItemModuleBase.setConflictTags(itemStack, "attack_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.fission_shot (裂变射击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.fission_shot");
            ItemModuleBase.addAttributes(itemStack, "multishot", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.6001f);
            ItemModuleBase.setType(itemStack, "fission_shot");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // ========== 新增MOD（白银级别）==========

            // 4. kuvaweapon.item_module.predatory_instinct (掠食本能)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.predatory_instinct");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "dashAttackRange", 3.00001f);
            ItemModuleBase.setType(itemStack, "predatory_instinct");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance", "dash_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 5. kuvaweapon.item_module.hunters_moment (猎杀时刻)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.hunters_moment");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "dashTriggerChance", 1.35001f);
            ItemModuleBase.setType(itemStack, "hunters_moment");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance", "dash_trigger");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 6. kuvaweapon.item_module.violent_aesthetic (非暴力美学)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.violent_aesthetic");
            ItemModuleBase.addAttributes(itemStack, "baseDamageWhenNotCriticalStrike", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.20001f);
            ItemModuleBase.setType(itemStack, "violent_aesthetic");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 7. kuvaweapon.item_module.infection_protocol (感染协议)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.infection_protocol");
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.75001f);
            ItemModuleBase.setType(itemStack, "infection_protocol");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 8. kuvaweapon.item_module.dual_covenant (双重契约)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.dual_covenant");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.20001f);
            ItemModuleBase.setType(itemStack, "dual_covenant");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 9. kuvaweapon.item_module.mystic_archery (秘法弓术)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.mystic_archery");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 1.35001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.05001f);
            ItemModuleBase.setType(itemStack, "mystic_archery");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 10. kuvaweapon.item_module.arcane_ballistics (奥能轨迹)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arcane_ballistics");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.35001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.05001f);
            ItemModuleBase.setType(itemStack, "arcane_ballistics");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 11. kuvaweapon.item_module.berserker_rage (狂战士之怒)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.berserker_rage");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", -0.15001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.90001f);
            ItemModuleBase.setType(itemStack, "berserker_rage");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 12. kuvaweapon.item_module.suppressive_fire (火力压制)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.suppressive_fire");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.35001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.30001f);
            ItemModuleBase.setType(itemStack, "suppressive_fire");
            ItemModuleBase.setConflictTags(itemStack, "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 13. kuvaweapon.item_module.shockwave_domain (震荡领域)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.shockwave_domain");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.15001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.90001f);
            ItemModuleBase.setType(itemStack, "shockwave_domain");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 14. kuvaweapon.item_module.death_barrage (死亡弹幕)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.death_barrage");
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.90001f);
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
        return EnumRarity.UNCOMMON;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}