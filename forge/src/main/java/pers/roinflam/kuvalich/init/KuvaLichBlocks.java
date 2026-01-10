package pers.roinflam.kuvalich.init;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import pers.roinflam.kuvalich.blocks.ore.ExperienceOre;
import pers.roinflam.kuvalich.blocks.ore.RequiemOre;
import pers.roinflam.kuvalich.blocks.table.*;
import pers.roinflam.kuvalich.tabs.KuvaLichTab;

import java.util.ArrayList;
import java.util.List;

public class KuvaLichBlocks {
    public static final List<Block> BLOCKS = new ArrayList<Block>();

    public static final RequiemOre REQUIEM_ORE = new RequiemOre("requiem_ore", Material.ROCK, KuvaLichTab.getTab());
    public static final ExperienceOre EXPERIENCE_ORE = new ExperienceOre("experience_ore", Material.ROCK, KuvaLichTab.getTab());
    public static final RequiemGate REQUIEM_GATE = new RequiemGate("requiem_gate", Material.ROCK, KuvaLichTab.getTab());
    public static final RequiemRecast REQUIEM_RECAST = new RequiemRecast("requiem_recast", Material.ROCK, KuvaLichTab.getTab());
    public static final RequiemEvolve REQUIEM_EVOLVE = new RequiemEvolve("requiem_evolve", Material.ROCK, KuvaLichTab.getTab());
    public static final RequiemWeaponTable REQUIEM_WEAPON_TABLE = new RequiemWeaponTable("requiem_weapon_table", Material.ROCK, KuvaLichTab.getTab());
    public static final RequiemWarframeTable REQUIEM_WARFRAME_TABLE = new RequiemWarframeTable("requiem_warframe_table", Material.ROCK, KuvaLichTab.getTab());
}
