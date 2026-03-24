package pers.roinflam.kuvalich.client.renderer;

import pers.roinflam.kuvalich.client.model.ModelKuvaItem;
import pers.roinflam.kuvalich.item.Kuva;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 赤毒（Kuva）物品GeckoLib渲染器
 */
public class RenderKuvaItem extends GeoItemRenderer<Kuva> {

    public RenderKuvaItem() {
        super(new ModelKuvaItem());
    }
}