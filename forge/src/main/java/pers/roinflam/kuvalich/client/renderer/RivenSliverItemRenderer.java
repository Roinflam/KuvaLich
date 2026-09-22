package pers.roinflam.kuvalich.client.renderer;

import pers.roinflam.kuvalich.client.model.RivenSliverItemModel;
import pers.roinflam.kuvalich.item.RivenSliver;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 裂罅碎块（RivenSliver）物品GeckoLib渲染器
 */
public class RivenSliverItemRenderer extends GeoItemRenderer<RivenSliver> {

    public RivenSliverItemRenderer() {
        super(new RivenSliverItemModel());
    }
}