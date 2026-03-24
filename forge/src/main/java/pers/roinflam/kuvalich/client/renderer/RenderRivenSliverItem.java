package pers.roinflam.kuvalich.client.renderer;

import pers.roinflam.kuvalich.client.model.ModelRivenSliverItem;
import pers.roinflam.kuvalich.item.RivenSliver;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 裂罅碎块（RivenSliver）物品GeckoLib渲染器
 */
public class RenderRivenSliverItem extends GeoItemRenderer<RivenSliver> {

    public RenderRivenSliverItem() {
        super(new ModelRivenSliverItem());
    }
}