package pers.roinflam.kuvalich.blocks.capability.provider;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;


import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;
import pers.roinflam.kuvalich.blocks.capability.WarframeModules;

public class WarframeModuleProvider implements ICapabilitySerializable<NBTTagCompound> {
    private final WarframeModules instance;
    private final Capability<WarframeModules> capability;

    public WarframeModuleProvider() {
        this.instance = new WarframeModules();
        this.capability = CapabilityRegistryHandler.WARFRAME_MODULES;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return this.capability.equals(capability);
    }

    
    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        return this.capability.equals(capability) ? this.capability.cast(this.instance) : null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return this.instance.serializeNBT();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        this.instance.deserializeNBT(nbt);
    }
}
