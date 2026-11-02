package pers.roinflam.kuvalich.item.card;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import pers.roinflam.kuvalich.base.item.AbstractRequiemCard;
import javax.annotation.Nonnull;

/**
 * OULL 安魂卡片（ID: 8）：万能卡，放在哪一格都算这一格猜对；同时只能装一张。
 * 不进安魂之铸、不从挖矿掉，只在处决失败但猜对过至少一格时有几率得到
 * OULL Requiem Card (ID: 8): wildcard, counts as correct in any slot; only one can be placed at a time.
 */
public class OULL extends AbstractRequiemCard {

    /** Oull 的卡 id，排在八张普通卡后面 */
    public static final int ID = 8;

    public OULL(@Nonnull Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getID() {
        return ID;
    }

    /** 这一摞是不是 Oull */
    public static boolean is(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof OULL;
    }
}
