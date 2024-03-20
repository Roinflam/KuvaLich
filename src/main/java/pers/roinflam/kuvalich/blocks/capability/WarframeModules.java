package pers.roinflam.kuvalich.blocks.capability;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber
public class WarframeModules implements INBTSerializable<NBTTagCompound> {
    private ItemStack one;
    private ItemStack two;
    private ItemStack three;
    private ItemStack four;
    private ItemStack five;
    private ItemStack six;
    private ItemStack seven;
    private ItemStack eight;


    public WarframeModules() {
        this.one = ItemStack.EMPTY;
        this.two = ItemStack.EMPTY;
        this.three = ItemStack.EMPTY;
        this.four = ItemStack.EMPTY;
        this.five = ItemStack.EMPTY;
        this.six = ItemStack.EMPTY;
        this.seven = ItemStack.EMPTY;
        this.eight = ItemStack.EMPTY;
    }

    public ItemStack getOne() {
        return one;
    }

    public void setOne(ItemStack one) {
        this.one = one;
    }

    public ItemStack getTwo() {
        return two;
    }

    public void setTwo(ItemStack two) {
        this.two = two;
    }

    public ItemStack getThree() {
        return three;
    }

    public void setThree(ItemStack three) {
        this.three = three;
    }

    public ItemStack getFour() {
        return four;
    }

    public void setFour(ItemStack four) {
        this.four = four;
    }

    public ItemStack getFive() {
        return five;
    }

    public void setFive(ItemStack five) {
        this.five = five;
    }

    public ItemStack getSix() {
        return six;
    }

    public void setSix(ItemStack six) {
        this.six = six;
    }

    public ItemStack getSeven() {
        return seven;
    }

    public void setSeven(ItemStack seven) {
        this.seven = seven;
    }

    public ItemStack getEight() {
        return eight;
    }

    public void setEight(ItemStack eight) {
        this.eight = eight;
    }

    public void clone(@NotNull WarframeModules warframeModules) {
        this.setOne(warframeModules.getOne());
        this.setTwo(warframeModules.getTwo());
        this.setThree(warframeModules.getThree());
        this.setFour(warframeModules.getFour());
        this.setFive(warframeModules.getFive());
        this.setSix(warframeModules.getSix());
        this.setSeven(warframeModules.getSeven());
        this.setEight(warframeModules.getEight());
    }

    @Override
    public @NotNull NBTTagCompound serializeNBT() {
        @NotNull NBTTagCompound nbtTagCompound = new NBTTagCompound();
        nbtTagCompound.setTag("one", getOne().serializeNBT());
        nbtTagCompound.setTag("two", getTwo().serializeNBT());
        nbtTagCompound.setTag("three", getThree().serializeNBT());
        nbtTagCompound.setTag("four", getFour().serializeNBT());
        nbtTagCompound.setTag("five", getFive().serializeNBT());
        nbtTagCompound.setTag("six", getSix().serializeNBT());
        nbtTagCompound.setTag("seven", getSeven().serializeNBT());
        nbtTagCompound.setTag("eight", getEight().serializeNBT());
        return nbtTagCompound;
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.@NotNull Clone evt) {
        Entity entity = evt.getEntity();
        if (!entity.world.isRemote && entity instanceof EntityPlayer) {
            @NotNull EntityPlayer entityPlayer = (EntityPlayer) entity;
            @Nullable WarframeModules warframeModules = entityPlayer.getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);
            @Nullable WarframeModules originalWarframeModules = evt.getOriginal().getCapability(CapabilityRegistryHandler.WARFRAME_MODULES, null);
            warframeModules.clone(originalWarframeModules);
        }
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound nbt) {
        this.one = new ItemStack(nbt.getCompoundTag("one"));
        this.two = new ItemStack(nbt.getCompoundTag("two"));
        this.three = new ItemStack(nbt.getCompoundTag("three"));
        this.four = new ItemStack(nbt.getCompoundTag("four"));
        this.five = new ItemStack(nbt.getCompoundTag("five"));
        this.six = new ItemStack(nbt.getCompoundTag("six"));
        this.seven = new ItemStack(nbt.getCompoundTag("seven"));
        this.eight = new ItemStack(nbt.getCompoundTag("eight"));
    }

}
