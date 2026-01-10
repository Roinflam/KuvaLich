package pers.roinflam.kuvalich.entity.render;


import net.minecraft.client.renderer.entity.RenderManager;
import pers.roinflam.kuvalich.entity.EntityKuvaSlave;
import pers.roinflam.kuvalich.entity.model.ModelKuvaSlave;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

public class RenderKuvaSlave extends GeoEntityRenderer<EntityKuvaSlave> {

    public RenderKuvaSlave(RenderManager manager) {
        super(manager, new ModelKuvaSlave());
    }


}
