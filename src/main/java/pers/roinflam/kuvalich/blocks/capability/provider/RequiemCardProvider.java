package pers.roinflam.kuvalich.blocks.capability.provider;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.kuvalich.blocks.capability.RequiemCard;
import pers.roinflam.kuvalich.blocks.capability.CapabilityRegistryHandler;

public class RequiemCardProvider implements ICapabilitySerializable<NBTTagCompound> {
    private final @NotNull RequiemCard instance;
    private final Capability<RequiemCard> capability;

    public RequiemCardProvider() {
        this.instance = new RequiemCard();
        this.capability = CapabilityRegistryHandler.REQUIEM_CARD;
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
        return this.capability.equals(capability);
    }

    @Nullable
    @Override
    public <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        return this.capability.equals(capability) ? this.capability.cast(this.instance) : null;
    }

    @Override
    public @NotNull NBTTagCompound serializeNBT() {
        return this.instance.serializeNBT();
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound nbt) {
        this.instance.deserializeNBT(nbt);
    }
}
