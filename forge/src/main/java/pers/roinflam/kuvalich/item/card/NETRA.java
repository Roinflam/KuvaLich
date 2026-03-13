package pers.roinflam.kuvalich.item.card;

import net.minecraft.world.item.Item;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import javax.annotation.Nonnull;

public class NETRA extends AbstractRequiemCard {

    public NETRA(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getID() {
        return 4;
    }
}