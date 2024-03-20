package pers.roinflam.kuvalich.proxy;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.kuvalich.handlers.RenderHandler;

public class ClientProxy extends CommonProxy {

    static {
        RenderHandler.registerEntityRenders();
    }

    @Override
    public void registerItemRenderer(@NotNull Item item, int meta, String id) {
        ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(item.getRegistryName(), id));
    }

}
