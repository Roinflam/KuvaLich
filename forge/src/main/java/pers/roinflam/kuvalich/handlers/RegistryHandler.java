package pers.roinflam.kuvalich.handlers;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;

import pers.roinflam.kuvalich.KuvaLich;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.init.KuvaLichEnchantments;
import pers.roinflam.kuvalich.init.KuvaLichEntities;
import pers.roinflam.kuvalich.init.KuvaLichItems;
import pers.roinflam.kuvalich.utils.IHasModel;

@Mod.EventBusSubscriber
public class RegistryHandler {

    @SubscribeEvent
    public static void onItemRegister(RegistryEvent.Register<Item> evt) {
        evt.getRegistry().registerAll(KuvaLichItems.ITEMS.toArray(new Item[0]));
    }

    @SubscribeEvent
    public static void onBlockRegister(RegistryEvent.Register<Block> evt) {
        evt.getRegistry().registerAll(KuvaLichBlocks.BLOCKS.toArray(new Block[0]));
    }

    @SubscribeEvent
    public static void onEnchantmentRegister(RegistryEvent.Register<Enchantment> evt) {
        evt.getRegistry().registerAll(KuvaLichEnchantments.ENCHANTMENTS.toArray(new Enchantment[0]));
    }

    @SubscribeEvent
    public static void onEntityRegister(RegistryEvent.Register<EntityEntry> evt) {
        evt.getRegistry().registerAll(KuvaLichEntities.ENTITIES.toArray(new EntityEntry[0]));
    }

    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent evt) {
        for (Item item : KuvaLichItems.ITEMS) {
            if (item instanceof IHasModel) {
                KuvaLich.proxy.registerItemRenderer(item, 0, "inventory");
            }
        }
        for (Block block : KuvaLichBlocks.BLOCKS) {
            if (block instanceof IHasModel) {
                KuvaLich.proxy.registerItemRenderer(Item.getItemFromBlock(block), 0, "inventory");
            }
        }
    }

    // ❌ 删除这个方法，避免重复注册
    // @SubscribeEvent
    // public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> evt) { ... }
}