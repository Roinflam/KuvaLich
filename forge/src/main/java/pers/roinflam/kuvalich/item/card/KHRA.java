package pers.roinflam.kuvalich.item.card;

import net.minecraft.world.item.Item;
import pers.roinflam.kuvalich.base.item.RequiemCardBase;
import javax.annotation.Nonnull;

/**
 * KHRA安魂卡片（ID: 0）
 * KHRA Requiem Card (ID: 0)
 */
public class KHRA extends RequiemCardBase {

    public KHRA(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getID() {
        return 0;
    }
}