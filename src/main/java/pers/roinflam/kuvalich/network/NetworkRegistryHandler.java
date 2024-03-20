package pers.roinflam.kuvalich.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import pers.roinflam.kuvalich.utils.Reference;

public class NetworkRegistryHandler {

    public static void register() {
        NetworkRegistry.INSTANCE.registerGuiHandler(Reference.MOD_ID, new KuvaLichGuiHandler());
    }

}
