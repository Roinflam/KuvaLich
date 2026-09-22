package pers.roinflam.kuvalich.client.renderer;

import pers.roinflam.kuvalich.client.model.KuvaItemModel;
import pers.roinflam.kuvalich.item.Kuva;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 赤毒（Kuva）物品GeckoLib渲染器
 */
public class KuvaItemRenderer extends GeoItemRenderer<Kuva> {

    public KuvaItemRenderer() {
        super(new KuvaItemModel());
    }
}