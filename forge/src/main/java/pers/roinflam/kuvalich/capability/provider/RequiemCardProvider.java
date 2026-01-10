package pers.roinflam.kuvalich.capability.provider;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;


import pers.roinflam.kuvalich.capability.RequiemCard;
import pers.roinflam.kuvalich.capability.CapabilityRegistryHandler;

public class RequiemCardProvider implements ICapabilitySerializable<NBTTagCompound> {
    private final RequiemCard instance;
    private final Capability<RequiemCard> capability;

    public RequiemCardProvider() {
        this.instance = new RequiemCard();
        this.capability = CapabilityRegistryHandler.REQUIEM_CARD;
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
