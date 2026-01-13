package pers.roinflam.kuvalich.item.card;

import net.minecraft.world.item.Item;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import javax.annotation.Nonnull;

public class VOME extends RequiemCardBase {

    public VOME(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getID() {
        return 5;
    }
}