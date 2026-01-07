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

public class ItemRareModule extends ItemModuleBase {
    public static List<ItemStack> itemStackList = new ArrayList<ItemStack>();

    public ItemRareModule(String name) {
        super(name);
    }

    public static ItemStack getRandomModule() {
        ItemStack itemStack = new ItemStack(KuvaLichItems.ITEM_RARE_MODULE);
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

            // kuvaweapon.item_module.split_chamber (分裂膛室)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.split_chamber");
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.90001f);
            ItemModuleBase.setType(itemStack, "split_chamber");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.vital_sense (要害感知)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.vital_sense");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 1.2001f);
            ItemModuleBase.setType(itemStack, "vital_sense");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.spoiled_strike (腐蚀打击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.spoiled_strike");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 1.00001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", -0.20001f);
            ItemModuleBase.setType(itemStack, "spoiled_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.heavy_caliber (重口径)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.heavy_caliber");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.60001f);
            ItemModuleBase.setType(itemStack, "heavy_caliber");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.firestorm (火焰风暴)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.firestorm");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.60001f);
            ItemModuleBase.setType(itemStack, "firestorm");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.critical_delay (暴击延迟)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.critical_delay");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 2.00001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.20001f);
            ItemModuleBase.setType(itemStack, "critical_delay");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_chance", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.vile_acceleration (邪恶加速)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.vile_acceleration");
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.15001f);
            ItemModuleBase.setType(itemStack, "vile_acceleration");
            ItemModuleBase.setConflictTags(itemStack, "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.hollow_point (空尖弹)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.hollow_point");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 1.50001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.15001f);
            ItemModuleBase.setType(itemStack, "hollow_point");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.hammer_shot (榔头射击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.hammer_shot");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.setType(itemStack, "hammer_shot");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.creeping_bullseye (蔓延靶心)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.creeping_bullseye");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 1.87501f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.36001f);
            ItemModuleBase.setType(itemStack, "creeping_bullseye");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_chance", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.katana_energy (武士刀能量)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.katana_energy");
            ItemModuleBase.addAttributes(itemStack, "attackRange", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 0.60001f);
            ItemModuleBase.setType(itemStack, "katana_energy");
            ItemModuleBase.setConflictTags(itemStack, "attack_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.reflex_coil (反射线圈)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.reflex_coil");
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 0.60001f);
            ItemModuleBase.setType(itemStack, "reflex_coil");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // kuvaweapon.item_module.elemental_strike (元素打击)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.elemental_strike");
            ItemModuleBase.addAttributes(itemStack, "fire", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "ice", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "poison", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "electricity", 0.60001f);
            ItemModuleBase.setType(itemStack, "elemental_strike");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // ========== 新增MOD（黄金级别）==========

            // 15. kuvaweapon.item_module.plague_herald (瘟疫使者)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.plague_herald");
            ItemModuleBase.addAttributes(itemStack, "gas", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.75001f);
            ItemModuleBase.setType(itemStack, "plague_herald");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 16. kuvaweapon.item_module.nuclear_storm (核子风暴)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.nuclear_storm");
            ItemModuleBase.addAttributes(itemStack, "radiation", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.60001f);
            ItemModuleBase.setType(itemStack, "nuclear_storm");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 17. kuvaweapon.item_module.magnetic_overlord (磁暴领主)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magnetic_overlord");
            ItemModuleBase.addAttributes(itemStack, "magnetic", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 0.75001f);
            ItemModuleBase.setType(itemStack, "magnetic_overlord");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 18. kuvaweapon.item_module.corrosion_king (剥皮者)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.corrosion_king");
            ItemModuleBase.addAttributes(itemStack, "corrosion", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "slash", 0.75001f);
            ItemModuleBase.setType(itemStack, "corrosion_king");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 19. kuvaweapon.item_module.doomsday_arrow (末日箭矢)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.doomsday_arrow");
            ItemModuleBase.addAttributes(itemStack, "explosion", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 1.20001f);
            ItemModuleBase.setType(itemStack, "doomsday_arrow");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 20. kuvaweapon.item_module.viral_tyrant (病入膏肓)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.viral_tyrant");
            ItemModuleBase.addAttributes(itemStack, "virus", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.75001f);
            ItemModuleBase.setType(itemStack, "viral_tyrant");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 21. kuvaweapon.item_module.meltdown_protocol (熔毁协议)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.meltdown_protocol");
            ItemModuleBase.addAttributes(itemStack, "radiation", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.10001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.30001f);
            ItemModuleBase.setType(itemStack, "meltdown_protocol");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 22. kuvaweapon.item_module.biohazard (0号实验)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.biohazard");
            ItemModuleBase.addAttributes(itemStack, "gas", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.40001f);
            ItemModuleBase.setType(itemStack, "biohazard");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 23. kuvaweapon.item_module.em_storm (电磁风暴)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.em_storm");
            ItemModuleBase.addAttributes(itemStack, "magnetic", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.30001f);
            ItemModuleBase.setType(itemStack, "em_storm");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 24. kuvaweapon.item_module.corrosive_erosion (腐蚀熔蚀)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.corrosive_erosion");
            ItemModuleBase.addAttributes(itemStack, "corrosion", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", -0.30001f);
            ItemModuleBase.setType(itemStack, "corrosive_erosion");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 25. kuvaweapon.item_module.scorched_earth (燃尽天际)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.scorched_earth");
            ItemModuleBase.addAttributes(itemStack, "explosion", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.25001f);
            ItemModuleBase.setType(itemStack, "scorched_earth");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 26. kuvaweapon.item_module.pandemic_outbreak (病毒切)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.pandemic_outbreak");
            ItemModuleBase.addAttributes(itemStack, "virus", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "slash", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 0.60001f);
            ItemModuleBase.setType(itemStack, "pandemic_outbreak");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 27. kuvaweapon.item_module.berserkers_oath (狂战士誓约)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.berserkers_oath");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 3.00001f);
            ItemModuleBase.addAttributes(itemStack, "dashAttackRange", 5.00001f);
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", -0.90001f);
            ItemModuleBase.setType(itemStack, "berserkers_oath");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance", "dash_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 28. kuvaweapon.item_module.predators_mark (掠食者印记)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.predators_mark");
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", -0.40001f);
            ItemModuleBase.setType(itemStack, "predators_mark");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 29. kuvaweapon.item_module.reapers_scythe (死神镰刀)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.reapers_scythe");
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 0.80001f);
            ItemModuleBase.setType(itemStack, "reapers_scythe");
            ItemModuleBase.setConflictTags(itemStack, "multishot", "remote_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 30. kuvaweapon.item_module.hunters_code (猎人法则)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.hunters_code");
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "slash", 0.90001f);
            ItemModuleBase.setType(itemStack, "hunters_code");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 31. kuvaweapon.item_module.gladiators_glory (角斗士荣耀)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.gladiators_glory");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", 2.00001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -0.45001f);
            ItemModuleBase.setType(itemStack, "gladiators_glory");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_mult", "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 32. kuvaweapon.item_module.iron_oath (钢铁誓言)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.iron_oath");
            ItemModuleBase.addAttributes(itemStack, "baseDamageWhenNotCriticalStrike", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -0.45001f);
            ItemModuleBase.setType(itemStack, "iron_oath");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 33. kuvaweapon.item_module.ultimate_violence (暴力终末)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.ultimate_violence");
            ItemModuleBase.addAttributes(itemStack, "baseDamageWhenNotCriticalStrike", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "impact", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", -0.45001f);
            ItemModuleBase.setType(itemStack, "ultimate_violence");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 34. kuvaweapon.item_module.catastrophe (灾厄降临)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.catastrophe");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.15001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.60001f);
            ItemModuleBase.setType(itemStack, "catastrophe");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 35. kuvaweapon.item_module.arcane_torrent (奥术洪流)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arcane_torrent");
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.35001f);
            ItemModuleBase.setType(itemStack, "arcane_torrent");
            ItemModuleBase.setConflictTags(itemStack, "multishot", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 36. kuvaweapon.item_module.mystic_apocalypse (秘法终末)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.mystic_apocalypse");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 1.35001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.45001f);
            ItemModuleBase.setType(itemStack, "mystic_apocalypse");
            ItemModuleBase.setConflictTags(itemStack, "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 37. kuvaweapon.item_module.dual_legend (双刃传奇)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.dual_legend");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 2.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 2.20001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", -0.25001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.25001f);
            ItemModuleBase.setType(itemStack, "dual_legend");
            ItemModuleBase.setConflictTags(itemStack, "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 38. kuvaweapon.item_module.dual_disaster (双重灾难)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.dual_disaster");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 2.00001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 2.00001f);
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", -0.35001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.35001f);
            ItemModuleBase.setType(itemStack, "dual_disaster");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance", "remote_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 39. kuvaweapon.item_module.arcane_domination (秘法主宰)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.arcane_domination");
            ItemModuleBase.addAttributes(itemStack, "magicDamage", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", -0.45001f);
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", -0.45001f);
            ItemModuleBase.setType(itemStack, "arcane_domination");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 40. kuvaweapon.item_module.blood_frenzy (血之狂舞)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.blood_frenzy");
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeMultiplier", -0.60001f);
            ItemModuleBase.setType(itemStack, "blood_frenzy");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance", "melee_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 41. kuvaweapon.item_module.crying_wounds (裂口)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.crying_wounds");
            ItemModuleBase.addAttributes(itemStack, "slash", 1.65001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 1.35001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -0.50001f);
            ItemModuleBase.setType(itemStack, "crying_wounds");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 42. kuvaweapon.item_module.legendary_shot (神射传说)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.legendary_shot");
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", 1.80001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.60001f);
            ItemModuleBase.setType(itemStack, "legendary_shot");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_chance", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 43. kuvaweapon.item_module.ballistic_master (弹道大师)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.ballistic_master");
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 1.80001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.60001f);
            ItemModuleBase.setType(itemStack, "ballistic_master");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 44. kuvaweapon.item_module.condition_outbreak (异况超量)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.condition_outbreak");
            ItemModuleBase.addAttributes(itemStack, "killStackBaseDamage", 0.08f);
            ItemModuleBase.setType(itemStack, "condition_outbreak");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 45. kuvaweapon.item_module.slaughter_feast (屠戮盛宴)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.slaughter_feast");
            ItemModuleBase.addAttributes(itemStack, "bane_of_undefined", 0.25001f);
            ItemModuleBase.addAttributes(itemStack, "bane_of_undead", 0.25001f);
            ItemModuleBase.addAttributes(itemStack, "bane_of_arthropod", 0.25001f);
            ItemModuleBase.addAttributes(itemStack, "bane_of_illager", 0.25001f);
            ItemModuleBase.setType(itemStack, "slaughter_feast");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 46. kuvaweapon.item_module.chain_catastrophe (连锁灾难)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.chain_catastrophe");
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.44001f);
            ItemModuleBase.addAttributes(itemStack, "triggerChance", 0.75001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", -0.75001f);
            ItemModuleBase.setType(itemStack, "chain_catastrophe");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 47. kuvaweapon.item_module.radiation_lord (辐射君主)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.radiation_lord");
            ItemModuleBase.addAttributes(itemStack, "radiation", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeMultiplier", 0.90001f);
            ItemModuleBase.setType(itemStack, "radiation_lord");
            ItemModuleBase.setConflictTags(itemStack, "remote_crit_mult");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 48. kuvaweapon.item_module.corrosion_tide (腐蚀狂潮)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.corrosion_tide");
            ItemModuleBase.addAttributes(itemStack, "corrosion", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 0.60001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.30001f);
            ItemModuleBase.setType(itemStack, "corrosion_tide");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 49. kuvaweapon.item_module.toxic_cloud (毒气扩散)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.toxic_cloud");
            ItemModuleBase.addAttributes(itemStack, "gas", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "bursting_radius", 0.15001f);
            ItemModuleBase.setType(itemStack, "toxic_cloud");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 50. kuvaweapon.item_module.magnetic_collapse (磁场崩溃)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.magnetic_collapse");
            ItemModuleBase.addAttributes(itemStack, "magnetic", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "triggerTime", 0.90001f);
            ItemModuleBase.setType(itemStack, "magnetic_collapse");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 51. kuvaweapon.item_module.apocalypse_split (末日分裂)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.apocalypse_split");
            ItemModuleBase.addAttributes(itemStack, "explosion", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "arrowDamage", 1.35001f);
            ItemModuleBase.addAttributes(itemStack, "multishot", 0.30001f);
            ItemModuleBase.setType(itemStack, "apocalypse_split");
            ItemModuleBase.setConflictTags(itemStack, "multishot");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 52. kuvaweapon.item_module.viral_vector (病毒轨迹)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.viral_vector");
            ItemModuleBase.addAttributes(itemStack, "virus", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "projectileDamage", 1.20001f);
            ItemModuleBase.setType(itemStack, "viral_vector");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 53. kuvaweapon.item_module.melee_fury (近战狂怒)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.melee_fury");
            ItemModuleBase.addAttributes(itemStack, "meleeDamage", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "attackSpeed", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "meleeCriticalStrikeProbability", -0.60001f);
            ItemModuleBase.setType(itemStack, "melee_fury");
            ItemModuleBase.setConflictTags(itemStack, "melee_crit_chance");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 54. kuvaweapon.item_module.ranged_fury (远程狂怒)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.ranged_fury");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 2.40001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "remoteCriticalStrikeProbability", -0.60001f);
            ItemModuleBase.setType(itemStack, "ranged_fury");
            ItemModuleBase.setConflictTags(itemStack, "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 55. kuvaweapon.item_module.predatory_hybrid (掠食双修)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.predatory_hybrid");
            ItemModuleBase.addAttributes(itemStack, "remoteDamage", 1.05001f);
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "dashAttackRange", 3.00001f);
            ItemModuleBase.setType(itemStack, "predatory_hybrid");
            ItemModuleBase.setConflictTags(itemStack, "dash_crit_chance", "dash_range");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 56. kuvaweapon.item_module.bombardment (狂轰滥炸)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.bombardment");
            ItemModuleBase.addAttributes(itemStack, "multishot", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "dashMeleeCriticalStrikeProbability", 1.20001f);
            ItemModuleBase.addAttributes(itemStack, "firing_rate", -0.25001f);
            ItemModuleBase.setType(itemStack, "bombardment");
            ItemModuleBase.setConflictTags(itemStack, "multishot", "dash_crit_chance", "firing_rate");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 57. kuvaweapon.item_module.elemental_fusion (元素融合)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.elemental_fusion");
            ItemModuleBase.addAttributes(itemStack, "fire", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "poison", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "ice", 0.30001f);
            ItemModuleBase.addAttributes(itemStack, "electricity", 0.30001f);
            ItemModuleBase.setType(itemStack, "elemental_fusion");
            items.add(itemStack);
            itemStackList.add(itemStack.copy());

            // 58. kuvaweapon.item_module.physical_triad (虐杀原形)
            itemStack = new ItemStack(this);
            itemStack.setTranslatableName("kuvaweapon.item_module.physical_triad");
            ItemModuleBase.addAttributes(itemStack, "slash", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "puncture", 0.90001f);
            ItemModuleBase.addAttributes(itemStack, "impact", 0.90001f);
            ItemModuleBase.setType(itemStack, "physical_triad");
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
        return EnumRarity.RARE;
    }

    @Override
    public boolean isWarframe() {
        return false;
    }
}