package pers.roinflam.kuvalich;

import net.minecraft.world.DimensionType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.init.KuvaLichBlocks;
import pers.roinflam.kuvalich.network.NetworkRegistryHandler;
import pers.roinflam.kuvalich.network.message.DamagePacket;
import pers.roinflam.kuvalich.network.message.DiggingSpeedPacket;
import pers.roinflam.kuvalich.proxy.CommonProxy;
import pers.roinflam.kuvalich.utils.Reference;
import pers.roinflam.kuvalich.utils.helper.world.WorldGenBase;
import software.bernie.geckolib3.GeckoLib;

@Mod(modid = Reference.MOD_ID, useMetadata = true)
public class KuvaLich {
    @Mod.Instance
    public static KuvaLich instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;
    public static SimpleNetworkWrapper network;

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent evt) {
        GeckoLib.initialize();
        NetworkRegistryHandler.register();
        CapabilityRegistryHandler.register();

        network = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID);
        network.registerMessage(DamagePacket.Handler.class, DamagePacket.class, 0, Side.CLIENT);
        network.registerMessage(DiggingSpeedPacket.Handler.class, DiggingSpeedPacket.class, 1, Side.CLIENT);

        GameRegistry.registerWorldGenerator(new WorldGenBase(DimensionType.OVERWORLD, KuvaLichBlocks.REQUIEM_ORE.getDefaultState(), 1, 28, 2, 4, 1, 4), 3);

        GameRegistry.registerWorldGenerator(new WorldGenBase(DimensionType.OVERWORLD, KuvaLichBlocks.EXPERIENCE_ORE.getDefaultState(), 1, 128, 2, 4, 2, 8), 3);
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {

    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {

    }
}
