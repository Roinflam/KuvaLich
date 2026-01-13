package pers.roinflam.kuvalich.item.card;

import net.minecraft.world.item.Item;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import javax.annotation.Nonnull;

public class XATA extends RequiemCardBase {

    public XATA(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getID() {
        return 6;
    }
}