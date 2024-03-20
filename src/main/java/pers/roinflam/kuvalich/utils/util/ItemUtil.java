package pers.roinflam.kuvalich.utils.util;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import pers.roinflam.kuvalich.KuvaLich;

import javax.annotation.Nonnull;

public class ItemUtil {

    @Nonnull
    public static Item registerItem(@Nonnull Item item, @Nonnull String name, @Nonnull CreativeTabs creativeTabs) {
        item.setUnlocalizedName(name);
        item.setRegistryName(name);
        item.setCreativeTab(creativeTabs);
        KuvaLich.proxy.registerItemRenderer(item, 0, "inventory");
        return item;
    }

}
