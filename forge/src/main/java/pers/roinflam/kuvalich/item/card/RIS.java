package pers.roinflam.kuvalich.item.card;

import net.minecraft.world.item.Item;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import javax.annotation.Nonnull;

public class RIS extends AbstractRequiemCard {

    public RIS(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getID() {
        return 7;
    }
}