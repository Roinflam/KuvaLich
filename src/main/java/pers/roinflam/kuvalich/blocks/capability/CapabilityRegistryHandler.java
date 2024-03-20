package pers.roinflam.kuvalich.blocks.capability;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class CapabilityRegistryHandler {
    @CapabilityInject(RequiemCard.class)
    public static Capability<RequiemCard> REQUIEM_CARD;
    @CapabilityInject(WarframeModules.class)
    public static Capability<WarframeModules> WARFRAME_MODULES;

    public static void register() {
        CapabilityManager.INSTANCE.register(RequiemCard.class, new Capability.IStorage<RequiemCard>() {
            @Nullable
            @Override
            public NBTBase writeNBT(Capability<RequiemCard> capability, @NotNull RequiemCard instance, EnumFacing side) {
                return instance.serializeNBT();
            }

            @Override
            public void readNBT(Capability<RequiemCard> capability, @NotNull RequiemCard instance, EnumFacing side, NBTBase nbt) {
                if (nbt instanceof NBTTagCompound) {
                    instance.deserializeNBT((NBTTagCompound) nbt);
                }
            }
        }, RequiemCard::new);
        CapabilityManager.INSTANCE.register(WarframeModules.class, new Capability.IStorage<WarframeModules>() {
            @Nullable
            @Override
            public NBTBase writeNBT(Capability<WarframeModules> capability, @NotNull WarframeModules instance, EnumFacing side) {
                return instance.serializeNBT();
            }

            @Override
            public void readNBT(Capability<WarframeModules> capability, @NotNull WarframeModules instance, EnumFacing side, NBTBase nbt) {
                if (nbt instanceof NBTTagCompound) {
                    instance.deserializeNBT((NBTTagCompound) nbt);
                }
            }
        }, WarframeModules::new);
    }

}
