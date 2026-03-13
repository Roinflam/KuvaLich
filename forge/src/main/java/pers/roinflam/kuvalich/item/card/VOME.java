package pers.roinflam.kuvalich.item.card;

import net.minecraft.world.item.Item;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import javax.annotation.Nonnull;

public class VOME extends AbstractRequiemCard {

    public VOME(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getID() {
        return 5;
    }
}