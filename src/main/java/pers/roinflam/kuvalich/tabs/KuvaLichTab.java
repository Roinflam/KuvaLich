package pers.roinflam.kuvalich.tabs;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;

public class KuvaLichTab extends CreativeTabs {
    private static final KuvaLichTab KUVA_LICH_TAB = new KuvaLichTab();

    private KuvaLichTab() {
        super("kuvalich_tab");
    }

    public static CreativeTabs getTab() {
        return KUVA_LICH_TAB;
    }

    @Override
    public @NotNull ItemStack getTabIconItem() {
        return new ItemStack(KuvaLichBlocks.REQUIEM_GATE.getItemBlock());
    }
}
