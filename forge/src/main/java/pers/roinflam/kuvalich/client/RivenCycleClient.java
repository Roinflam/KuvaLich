package pers.roinflam.kuvalich.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.kuvalich.world.inventory.RequiemEvolveMenu;

import java.util.List;

/** 收到裂罅循环状态包：只交给还开着的同一个安魂之融界面（窗口号对得上），关掉了就丢 */
@OnlyIn(Dist.CLIENT)
public final class RivenCycleClient {

    private RivenCycleClient() {
    }

    public static void accept(int containerId, int serial, List<ItemStack> candidates, int paid, int rolled,
                              byte reason) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof RequiemEvolveMenu menu
                && menu.containerId == containerId) {
            menu.acceptRivenState(serial, candidates, paid, rolled, reason);
        }
    }
}
