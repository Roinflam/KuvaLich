package pers.roinflam.kuvalich.handlers;

import net.minecraftforge.fml.client.registry.RenderingRegistry;
import pers.roinflam.kuvalich.entity.EntityKuvaMaster;
import pers.roinflam.kuvalich.entity.EntityKuvaSlave;
import pers.roinflam.kuvalich.entity.render.RenderKuvaMaster;
import pers.roinflam.kuvalich.entity.render.RenderKuvaSlave;

public class RenderHandler {

    public static void registerEntityRenders() {
        RenderingRegistry.registerEntityRenderingHandler(EntityKuvaSlave.class, RenderKuvaSlave::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityKuvaMaster.class, RenderKuvaMaster::new);
    }

}
