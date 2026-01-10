package pers.roinflam.kuvalich.entity.render;


import net.minecraft.client.renderer.entity.RenderManager;
import pers.roinflam.kuvalich.entity.EntityKuvaMaster;
import pers.roinflam.kuvalich.entity.model.ModelKuvaMaster;
import software.bernie.geckolib3.renderers.geo.GeoEntityRenderer;

public class RenderKuvaMaster extends GeoEntityRenderer<EntityKuvaMaster> {

    public RenderKuvaMaster(RenderManager manager) {
        super(manager, new ModelKuvaMaster());
    }


}
